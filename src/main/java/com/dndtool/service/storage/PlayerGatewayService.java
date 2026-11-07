package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.CharacterNamePolicy;
import com.dndtool.service.storage.CampaignParticipationData.*;
import com.dndtool.service.storage.CampaignStorageService.AccessSnapshot;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/** Run-scoped transport authority. Every access and Host mutation shares the storage monitor. */
public final class PlayerGatewayService {
    public record Reply(JsonObject body, String sessionToken, String deviceToken) { }
    private static final long SESSION_MILLIS = 30 * 60_000L, DEVICE_MILLIS = 7 * 24 * 60 * 60_000L;
    private static final int MAX_SESSIONS = 256, MAX_ATTEMPTS = 512;
    private final CampaignStorageService storage;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Budget bootstrapBudget = new Budget(128,2);
    private final Map<String, Session> sessions = new HashMap<>();
    private record Writer(String sessionToken, String connection) { }
    private final Map<UUID, Writer> writers = new HashMap<>();
    private final List<Proposal> proposals = new ArrayList<>();
    private final Map<String,JsonObject> adjudications = new HashMap<>();
    private final Map<String,String> narrativeResponses = new HashMap<>();
    private String run = "", invitation;
    private long epoch, invitationExpires;
    private boolean opened;

    private static final class Session {
        String token, csrf, status = "ANONYMOUS", nickname, run, bindingId, deviceToken;
        UUID member;
        long epoch, expires, candidateRevision = -1;
        String precondition;
        Map<String, PlayerCandidateFactory.Candidate> candidates = Map.of();
        final Budget reads = new Budget(8,1), posts = new Budget(12,1), identityAttempts = new Budget(5,1.0/60), receipts = new Budget(8,0.5);
        Session(String token, String csrf, long expires) { this.token = token; this.csrf = csrf; this.expires = expires; }
    }
    private record Proposal(String id, UUID memberId, UUID characterId, String text, String state) { }
    /** Bounded memory and no background timer: refill is measured only on the current request. */
    private static final class Budget {
        final double capacity,perSecond;double tokens;long last=-1;
        Budget(double capacity,double perSecond){this.capacity=capacity;this.perSecond=perSecond;tokens=capacity;}
        void take(long now){if(last>=0)tokens=Math.min(capacity,tokens+Math.max(0,now-last)*perSecond/1000);last=now;if(tokens<1)throw denied(429,"PLAYER_RATE_LIMITED");tokens-=1;}
    }

    public PlayerGatewayService(CampaignStorageService storage) { this(storage, Clock.systemUTC()); }
    public PlayerGatewayService(CampaignStorageService storage, Clock clock) { this.storage = Objects.requireNonNull(storage); this.clock = Objects.requireNonNull(clock); }

    public Reply read(String credential, String connection, boolean bootstrap) {
        return locked(() -> {
            refreshRun(); expire();
            Session session = credential == null ? null : sessions.get(credential);
            if (session == null) {
                if (!bootstrap) throw denied(401, "PLAYER_SESSION_REQUIRED");
                bootstrapBudget.take(now());
                session = newSession();
            }
            session.reads.take(now());
            String newToken = null, device = null;
            if (session.status.equals("APPROVED_READY")) {
                session.status="APPROVED";
                try { authorize(session, false); } catch(RuntimeException invalid) { session.status="APPROVED_READY";throw invalid; }
                sessions.remove(session.token);
                session.token = token(); session.csrf = token(); session.status = "APPROVED";
                sessions.put(session.token, session); newToken = session.token;
                device = session.deviceToken; session.deviceToken = null;
            }
            JsonObject body = envelope(session, connection);
            if (newToken == null && (credential == null || !session.token.equals(credential))) newToken = session.token;
            return new Reply(body, newToken, device);
        });
    }

    public Reply post(String credential, String csrf, String connection, String action, JsonObject input, String deviceCredential) {
        return locked(() -> {
            refreshRun(); expire(); Session session = requireSession(credential);
            session.posts.take(now());
            if(Set.of("join","resume").contains(action))session.identityAttempts.take(now());
            if(action.equals("receipt"))session.receipts.take(now());
            if (!same(session.csrf, csrf)) throw denied(403, "PLAYER_CSRF_REQUIRED");
            String device = null;
            switch (action) {
                case "join" -> {
                    fields(input, "invitation", "nickname");
                    if (!session.status.equals("ANONYMOUS") && !session.status.equals("REJECTED")) throw denied(409, "PLAYER_JOIN_UNAVAILABLE");
                    String supplied = text(input, "invitation"), nickname;
                    try { nickname = CharacterNamePolicy.normalize(text(input, "nickname")); }
                    catch (RuntimeException invalid) { throw denied(400, "PLAYER_INVALID_NICKNAME"); }
                    if (!opened || invitation == null || now() >= invitationExpires || !same(invitation, supplied)) throw denied(409, "PLAYER_INVITATION_INVALID");
                    access(); session.nickname = nickname; session.status = "WAITING"; session.run = run; session.epoch = epoch;
                }
                case "resume" -> {
                    fields(input); if (!opened) throw denied(409, "PLAYER_HOST_REOPEN_REQUIRED");
                    AccessSnapshot access = access(); JsonObject binding = findBinding(deviceCredential, access.localControl());
                    UUID member = UUID.fromString(text(binding, "memberId")); requireMember(access, member);
                    requireBindingVersion(access,binding,member);
                    session.member = member; session.bindingId = text(binding, "id"); session.run = run; session.epoch = epoch;
                    session.nickname = access.snapshot().domains().participation().member(member).name();
                    session.status = "APPROVED"; session.csrf = token();
                    sessions.remove(session.token); session.token = token(); sessions.put(session.token, session);
                    // Restoring identity never assigns the active write connection.
                    return new Reply(envelope(session, null), session.token, null);
                }
                case "takeover" -> {
                    fields(input); authorize(session, true);
                    String writer = token(); writers.put(session.member, new Writer(session.token,writer));
                    JsonObject body = envelope(session, writer); body.addProperty("connectionId", writer);
                    return new Reply(body, null, null);
                }
                case "command" -> {
                    fields(input, "connectionId", "candidateId", "precondition", "requestId");
                    AccessSnapshot access = authorize(session, true);
                    String writer = text(input, "connectionId"), candidateId = text(input, "candidateId"), precondition = text(input, "precondition"), requestId = uuid(input, "requestId");
                    requireWriter(session, writer);
                    if (!same(session.precondition, precondition) || session.candidateRevision != access.revision()) throw denied(409, "PLAYER_VIEW_EXPIRED");
                    PlayerCandidateFactory.Candidate candidate = session.candidates.get(candidateId);
                    if (candidate == null) throw denied(409, "PLAYER_VIEW_EXPIRED");
                    UUID character = UUID.fromString(candidate.characterId());
                    if (!session.member.equals(access.snapshot().domains().participation().controller(character))) throw denied(403, "PLAYER_CONTROL_REQUIRED");
                    JsonObject local = access.localControl().deepCopy(); JsonArray attempts = array(local, "attempts");
                    if (attempts.size() >= MAX_ATTEMPTS) throw denied(409, "PLAYER_RECEIPT_CAPACITY");
                    for (JsonElement element : attempts) if (text(element.getAsJsonObject(), "requestId").equals(requestId)) throw denied(409, "PLAYER_REQUEST_ALREADY_SENT");
                    JsonObject attempt = new JsonObject(); attempt.addProperty("bindingId", session.bindingId); attempt.addProperty("requestId", requestId);
                    attempt.addProperty("candidateId", candidateId); attempt.addProperty("precondition", precondition); attempt.addProperty("revision", Long.toString(access.revision()));
                    attempt.addProperty("action", candidate.action()); attempt.add("input", candidate.input().deepCopy()); attempt.addProperty("characterId", candidate.characterId());
                    attempts.add(attempt); local.add("attempts", attempts); storage.updateLocalPlayerControl(local);
                    try {
                        storage.domainCommand(requestId, access.epoch(), access.revision(), candidate.action(), candidate.input(), Actor.player(session.member, character),
                                current -> { validateCurrent(session, writer, precondition, current); });
                    } catch (StorageException rejected) {
                        if (Set.of("COMMIT_UNKNOWN", "RUN_BLOCKED").contains(rejected.code())) throw denied(503, "PLAYER_RESULT_UNVERIFIED");
                        throw denied(409, "PLAYER_COMMAND_REJECTED");
                    }
                    return receipt(session, attempt, writer);
                }
                case "receipt" -> {
                    fields(input, "requestId", "candidateId", "precondition"); authorize(session, false);
                    String requestId = uuid(input, "requestId");
                    JsonObject attempt = null;
                    for (JsonElement element : array(access().localControl(), "attempts")) {
                        JsonObject row = element.getAsJsonObject();
                        if (text(row, "bindingId").equals(session.bindingId) && text(row, "requestId").equals(requestId)
                                && same(text(row, "candidateId"), text(input, "candidateId")) && same(text(row, "precondition"), text(input, "precondition"))) attempt = row;
                    }
                    if (attempt == null) return unverified(session, connection);
                    return receipt(session, attempt, connection);
                }
                case "proposal" -> {
                    fields(input, "characterId", "text"); AccessSnapshot access = authorize(session, false);
                    UUID character = UUID.fromString(uuid(input, "characterId"));
                    if (!session.member.equals(access.snapshot().domains().participation().controller(character))) throw denied(403, "PLAYER_CONTROL_REQUIRED");
                    String message = text(input, "text");
                    try { CampaignManualData.text(message, 1, 1000); } catch (RuntimeException invalid) { throw denied(400, "PLAYER_INVALID_PROPOSAL"); }
                    if (proposals.size() >= 100) throw denied(409, "PLAYER_PROPOSAL_CAPACITY");
                    proposals.add(new Proposal(UUID.randomUUID().toString(), session.member, character, message, "WAITING_DM"));
                }
                default -> throw denied(404, "PLAYER_REQUEST_DENIED");
            }
            return new Reply(envelope(session, connection), null, device);
        });
    }

    private Reply receipt(Session session, JsonObject attempt, String connection) {
        AccessSnapshot current = authorize(session, false);
        UUID character = UUID.fromString(text(attempt, "characterId"));
        var found = storage.exactDomainReceipt(text(attempt, "requestId"), run, Long.parseLong(text(attempt, "revision")), text(attempt, "action"),
                attempt.getAsJsonObject("input"), Actor.player(session.member, character), next -> authorize(session, false));
        if (found.isEmpty()) return unverified(session, connection);
        JsonObject body = envelope(session, connection);
        JsonObject result = PlayerCampaignProjection.receipt(current, session.member, found.get());
        result.addProperty("requestId", text(attempt, "requestId")); body.add("receipt", result);
        return new Reply(body, null, null);
    }
    private Reply unverified(Session session, String connection) {
        JsonObject body = envelope(session, connection), receipt = new JsonObject(); receipt.addProperty("state", "UNVERIFIED");
        body.add("receipt", receipt); return new Reply(body, null, null);
    }

    private JsonObject envelope(Session session, String connection) {
        JsonObject body = new JsonObject(), identity = new JsonObject(); body.addProperty("status", "OK");
        identity.addProperty("status", session.status); identity.addProperty("csrf", session.csrf);
        if (session.nickname != null) identity.addProperty("displayName", session.nickname);
        if (session.status.equals("APPROVED")) {
            AccessSnapshot access = authorize(session, false);
            identity.addProperty("playerKey", session.member.toString());
            Writer writer = writers.get(session.member);
            identity.addProperty("writeEnabled", opened && !paused(access.localControl()) && writer != null && same(writer.sessionToken(),session.token) && same(writer.connection(),connection));
            identity.addProperty("paused", paused(access.localControl()) || !opened);
            JsonObject view = PlayerCampaignProjection.project(access, session.member);
            if (session.candidateRevision != access.revision() || session.precondition == null) {
                session.precondition = token(); session.candidateRevision = access.revision();
                Map<String, PlayerCandidateFactory.Candidate> choices = new LinkedHashMap<>();
                for (var candidate : PlayerCandidateFactory.candidates(access, session.member)) {
                    if (choices.size() >= 1024) break;
                    choices.put(token(), candidate);
                }
                session.candidates = Collections.unmodifiableMap(new LinkedHashMap<>(choices));
            }
            JsonArray candidates = new JsonArray();
            for (var issued : session.candidates.entrySet()) {
                var candidate = issued.getValue();
                JsonObject choice = new JsonObject(); choice.addProperty("id", issued.getKey()); choice.addProperty("label", candidate.label());
                choice.addProperty("characterId", candidate.characterId()); choice.addProperty("precondition", session.precondition); candidates.add(choice);
            }
            view.add("candidates", candidates); JsonArray ownProposals = new JsonArray();
            for (Proposal proposal : proposals) if (proposal.memberId.equals(session.member)) ownProposals.add(proposalJson(proposal));
            view.add("proposals", ownProposals); body.add("view", view);
        }
        body.add("session", identity); session.expires = now() + SESSION_MILLIS; return body;
    }

    /** Host-only status; its DTO never enters a player reply. */
    public JsonObject hostState() {
        return locked(() -> {
            refreshRun(); AccessSnapshot access = access(); JsonObject body = new JsonObject(); body.addProperty("status", "OK");
            body.addProperty("revision", Long.toString(access.revision())); body.addProperty("epoch", Long.toString(access.epoch()));
            body.addProperty("paused", paused(access.localControl())); body.addProperty("opened", opened);
            body.addProperty("invitationOpen", invitation != null && now() < invitationExpires);
            if (invitation != null && now() < invitationExpires) { body.addProperty("invitation", invitation); body.addProperty("invitationExpires", Long.toString(invitationExpires)); }
            JsonArray pending = new JsonArray();
            for (Session s : sessions.values()) if (s.status.equals("WAITING") && s.run.equals(run) && s.epoch == epoch && s.expires > now()) {
                JsonObject row = new JsonObject(); row.addProperty("applicationId", s.csrf); row.addProperty("nickname", s.nickname); pending.add(row);
            }
            body.add("applications", pending); JsonArray communications = new JsonArray(); for (Proposal p : proposals) communications.add(proposalJson(p)); body.add("proposals", communications);
            // Portable business facts are safe on the existing protected Host surface.
            body.add("participation", new Gson().toJsonTree(access.snapshot().domains().participation()));
            return body;
        });
    }

    public JsonObject hostCommand(String action, JsonObject input, String requestId, long expectedEpoch, long revision) {
        return locked(() -> {
            refreshRun(); AccessSnapshot access = access();
            if (access.epoch() != expectedEpoch || access.revision() != revision) throw new StorageException("STALE_REVISION", "Host participation view expired");
            switch (action) {
                case "invite" -> { fields(input); opened = true; invitation = token(); invitationExpires = now() + 10 * 60_000L; }
                case "close" -> { fields(input); invitation = null; }
                case "approve" -> {
                    fields(input, "applicationId", "memberId");
                    Session pending = sessions.values().stream().filter(s -> s.status.equals("WAITING") && s.csrf.equals(text(input, "applicationId")) && s.expires > now()).findFirst().orElseThrow(() -> denied(409, "PLAYER_APPLICATION_EXPIRED"));
                    JsonObject local = access.localControl().deepCopy(); JsonArray bindings = array(local, "bindings");
                    if (bindings.size() >= 128) throw denied(409, "PLAYER_DEVICE_CAPACITY");
                    String selected = text(input, "memberId"); UUID member;
                    if (selected.isEmpty()) {
                        member = UUID.fromString(requestId); JsonObject command = new JsonObject(); command.addProperty("name", pending.nickname);
                        storage.domainCommand(requestId, expectedEpoch, revision, "member-add", command); access = access();
                    } else { member = UUID.fromString(CampaignJson.uuid(selected)); requireMember(access, member); }
                    String recovery = token(), binding = UUID.randomUUID().toString();
                    JsonObject row = new JsonObject(); row.addProperty("id", binding); row.addProperty("memberId", member.toString()); row.addProperty("memberVersion",Long.toString(access.snapshot().domains().participation().member(member).version())); row.addProperty("hash", hash(recovery)); row.addProperty("expires", Long.toString(now() + DEVICE_MILLIS)); bindings.add(row); local.add("bindings", bindings); storage.updateLocalPlayerControl(local);
                    pending.member = member; pending.bindingId = binding; pending.deviceToken = recovery; pending.status = "APPROVED_READY"; opened = true;
                }
                case "reject" -> {
                    fields(input, "applicationId"); Session pending = sessions.values().stream().filter(s -> s.status.equals("WAITING") && s.csrf.equals(text(input, "applicationId"))).findFirst().orElseThrow(() -> denied(409, "PLAYER_APPLICATION_EXPIRED")); pending.status = "REJECTED";
                }
                case "pause" -> { fields(input, "paused"); boolean paused = bool(input, "paused"); JsonObject local = access.localControl().deepCopy(); local.addProperty("paused", paused); storage.updateLocalPlayerControl(local); if (!paused) opened = true; }
                case "proposal-reject" -> { fields(input, "proposalId"); replaceProposal(text(input, "proposalId"), "REJECTED"); }
                case "proposal-narrative" -> {
                    fields(input,"proposalId","text");String id=text(input,"proposalId"),response=CampaignManualData.text(text(input,"text"),1,1000);
                    pendingProposal(id);replaceProposal(id,"NARRATIVE");narrativeResponses.put(id,response);
                }
                case "proposal-execute" -> {
                    fields(input,"proposalId","ability","dc","reason");Proposal proposal=pendingProposal(text(input,"proposalId"));
                    String ability=text(input,"ability"),dcText=text(input,"dc"),reason=CampaignManualData.text(text(input,"reason"),1,500);
                    if(!Schema2CharacterRuntime.ABILITIES.contains(ability) || !dcText.matches("0|[1-9][0-9]?"))throw denied(400,"PLAYER_INVALID_ADJUDICATION");
                    int dc=Integer.parseInt(dcText);if(dc>30)throw denied(400,"PLAYER_INVALID_ADJUDICATION");
                    if(!proposal.memberId.equals(access.snapshot().domains().participation().controller(proposal.characterId)))throw denied(409,"PLAYER_PROPOSAL_EXPIRED");
                    requireMember(access,proposal.memberId);
                    var character=access.snapshot().domains().characters().stream().filter(c->c.id().equals(proposal.characterId)&&c.active()&&c.kind().equals("PC")).findFirst().orElseThrow(()->denied(409,"PLAYER_ADJUDICATION_UNSUPPORTED"));
                    var command=new Schema2CharacterRuntime.Command("CHECK_RESOLVED",null,0,null,null,null,Schema2CharacterRuntime.GrowthChoices.EMPTY,null,
                            new Schema2CharacterRuntime.CheckInput(BundledCampaignRules.rule("adventure.check","check.srd2014"),"ABILITY",ability,null,dc,false,false,reason));
                    JsonObject check=new JsonObject();check.addProperty("characterId",proposal.characterId.toString());check.addProperty("characterVersion",Long.toString(character.history().current().version()));check.add("command",CampaignDomainCodec.toJson(command));
                    for(String branch:List.of("success","failure")) {
                        JsonObject effect=new JsonObject();effect.addProperty("targetId",proposal.characterId.toString());effect.add("healthEffect",JsonNull.INSTANCE);effect.add("itemId",JsonNull.INSTANCE);effect.addProperty("itemDelta","0");effect.add("nodeId",JsonNull.INSTANCE);effect.addProperty("message",branch.equals("success")?"检定成功":"检定失败");check.add(branch,effect);
                    }
                    JsonObject decision=new JsonObject();decision.addProperty("proposalId",proposal.id);decision.addProperty("memberId",proposal.memberId.toString());decision.addProperty("proposalText",proposal.text);decision.addProperty("reason",reason);decision.addProperty("type","ABILITY_CHECK");decision.add("check",check);
                    storage.domainCommand(requestId,expectedEpoch,revision,"adjudicated-check",decision);
                    var accepted=access().snapshot().domains();var root=accepted.roots().stream().filter(r->r.operationId().toString().equals(requestId)).findFirst().orElseThrow();
                    var result=accepted.characters().stream().flatMap(c->c.history().events().stream()).filter(e->root.characterOperations().contains(e.operationId())&&e.checkResult()!=null).findFirst().orElseThrow().checkResult();
                    JsonObject receipt=new JsonObject();receipt.addProperty("requestId",requestId);receipt.addProperty("state","ACCEPTED");receipt.addProperty("type","ABILITY_CHECK");receipt.addProperty("reason",reason);receipt.addProperty("total",result.total());receipt.addProperty("success",result.success());JsonArray dice=new JsonArray();dice.add(result.firstD20());if(result.secondD20()!=null)dice.add(result.secondD20());receipt.add("dice",dice);
                    adjudications.put(proposal.id,receipt);replaceProposal(proposal.id,"EXECUTED");
                }
                case "control", "member", "share", "exploration" -> {
                    fields(input, "payload"); JsonObject payload = CampaignJson.read(text(input, "payload").getBytes(StandardCharsets.UTF_8));
                    String command = switch(action) { case "control" -> "control-set"; case "member" -> "member-update"; case "share" -> "share-set"; default -> "exploration-set"; };
                    storage.domainCommand(requestId, expectedEpoch, revision, command, payload);
                    if (action.equals("member") && !payload.get("active").getAsBoolean()) revokeBindings(UUID.fromString(payload.get("memberId").getAsString()));
                }
                default -> throw denied(404, "PLAYER_REQUEST_DENIED");
            }
            return hostState();
        });
    }

    private void replaceProposal(String id, String state) {
        for (int i = 0; i < proposals.size(); i++) { Proposal p = proposals.get(i); if (p.id.equals(id)) { proposals.set(i, new Proposal(p.id,p.memberId,p.characterId,p.text,state)); return; } }
        throw denied(409, "PLAYER_PROPOSAL_EXPIRED");
    }
    private Proposal pendingProposal(String id) { return proposals.stream().filter(p->p.id.equals(id)&&p.state.equals("WAITING_DM")).findFirst().orElseThrow(()->denied(409,"PLAYER_PROPOSAL_EXPIRED")); }
    private void revokeBindings(UUID member) {
        JsonObject local=access().localControl(); Set<String> revoked=new HashSet<>(); JsonArray bindings=new JsonArray();
        for(JsonElement element:array(local,"bindings")) {
            JsonObject row=element.getAsJsonObject(); if(text(row,"memberId").equals(member.toString()))revoked.add(text(row,"id"));else bindings.add(row);
        }
        JsonArray attempts=new JsonArray();for(JsonElement element:array(local,"attempts"))if(!revoked.contains(text(element.getAsJsonObject(),"bindingId")))attempts.add(element);
        local.add("bindings",bindings);local.add("attempts",attempts);storage.updateLocalPlayerControl(local);writers.remove(member);
        sessions.values().removeIf(s->member.equals(s.member));
    }
    private JsonObject proposalJson(Proposal p) {
        JsonObject row = new JsonObject(); row.addProperty("id",p.id); row.addProperty("memberId",p.memberId.toString()); row.addProperty("characterId",p.characterId.toString()); row.addProperty("text",p.text); row.addProperty("state",p.state);
        if(adjudications.containsKey(p.id))row.add("adjudication",adjudications.get(p.id).deepCopy());if(narrativeResponses.containsKey(p.id))row.addProperty("responseText",narrativeResponses.get(p.id));return row;
    }
    private void validateCurrent(Session session, String writer, String precondition, AccessSnapshot current) {
        authorize(session, true); requireWriter(session, writer);
        if (!same(session.precondition, precondition) || session.candidateRevision != current.revision()) throw denied(409, "PLAYER_VIEW_EXPIRED");
    }
    private void requireWriter(Session session, String connection) {
        Writer writer = writers.get(session.member);
        if (writer == null || !same(writer.sessionToken(),session.token) || !same(writer.connection(), connection)) throw denied(409, "PLAYER_WRITE_CONNECTION_REQUIRED");
    }
    private AccessSnapshot authorize(Session session, boolean writing) {
        AccessSnapshot access = access();
        if (session.member == null || !run.equals(session.run) || session.epoch != epoch || !session.status.equals("APPROVED")) throw denied(401, "PLAYER_SESSION_EXPIRED");
        requireMember(access, session.member);
        boolean binding = false;
        for (JsonElement e : array(access.localControl(), "bindings")) if (text(e.getAsJsonObject(), "id").equals(session.bindingId) && text(e.getAsJsonObject(),"memberId").equals(session.member.toString()) && Long.parseLong(text(e.getAsJsonObject(), "expires")) > now()) {
            requireBindingVersion(access,e.getAsJsonObject(),session.member); binding = true;
        }
        if (!binding) throw denied(401, "PLAYER_SESSION_EXPIRED");
        if (writing && (!opened || paused(access.localControl()))) throw denied(409, "PLAYER_OPERATIONS_PAUSED");
        return access;
    }
    private static void requireMember(AccessSnapshot access, UUID member) {
        if (access.snapshot().domains() == null || access.snapshot().domains().participation().members().stream().noneMatch(m -> m.id().equals(member) && m.active())) throw denied(403, "PLAYER_MEMBERSHIP_REVOKED");
    }
    private static void requireBindingVersion(AccessSnapshot access,JsonObject binding,UUID member) {
        if (Long.parseLong(text(binding,"memberVersion")) != access.snapshot().domains().participation().member(member).version()) throw denied(401,"PLAYER_DEVICE_REAPPROVAL_REQUIRED");
    }
    private JsonObject findBinding(String credential, JsonObject local) {
        if (credential != null && credential.matches("[A-Za-z0-9_-]{43}")) for (JsonElement element : array(local, "bindings")) {
            JsonObject row = element.getAsJsonObject(); if (same(text(row, "hash"), hash(credential)) && Long.parseLong(text(row, "expires")) > now()) return row;
        }
        throw denied(401, "PLAYER_DEVICE_REAPPROVAL_REQUIRED");
    }
    private Session newSession() { if (sessions.size() >= MAX_SESSIONS) throw denied(429, "PLAYER_SESSION_CAPACITY"); Session s = new Session(token(), token(), now() + SESSION_MILLIS); sessions.put(s.token, s); return s; }
    private Session requireSession(String token) { Session s = token == null ? null : sessions.get(token); if (s == null) throw denied(401, "PLAYER_SESSION_REQUIRED"); return s; }
    private void expire() { sessions.values().removeIf(s -> s.expires <= now()); }
    private void refreshRun() {
        var status = storage.status();
        if (!run.equals(status.runId()) || epoch != status.epoch()) { sessions.clear(); writers.clear(); proposals.clear(); adjudications.clear();narrativeResponses.clear();invitation = null; opened = false; run = status.runId(); epoch = status.epoch(); }
    }
    private AccessSnapshot access() {
        if (!"ACTIVE".equals(storage.status().state())) throw denied(409, "PLAYER_RUN_UNAVAILABLE");
        AccessSnapshot access = storage.accessSnapshot();
        if (access.snapshot().domains() == null) throw denied(409, "PLAYER_RULES_UNAVAILABLE");
        JsonObject local=access.localControl();
        if(local.size()==0) {
            local.addProperty("schemaVersion",1);local.addProperty("runId",access.runId());local.addProperty("paused",false);local.add("bindings",new JsonArray());local.add("attempts",new JsonArray());
            storage.updateLocalPlayerControl(local);access=storage.accessSnapshot();
        }
        validateLocal(access);return access;
    }
    private static void validateLocal(AccessSnapshot access) {
        try {
            JsonObject local=access.localControl();CampaignJson.fields(local,"schemaVersion","runId","paused","bindings","attempts");CampaignJson.protocol(local,"schemaVersion",1);
            if(!CampaignJson.string(local,"runId").equals(access.runId()) || !local.get("paused").isJsonPrimitive() || !local.getAsJsonPrimitive("paused").isBoolean())throw new IllegalArgumentException();
            JsonArray bindings=CampaignJson.array(local,"bindings"),attempts=CampaignJson.array(local,"attempts");
            if(bindings.size()>128 || attempts.size()>MAX_ATTEMPTS)throw new IllegalArgumentException();
            Set<String> ids=new HashSet<>(),hashes=new HashSet<>(),requests=new HashSet<>();
            for(JsonElement element:bindings) {
                JsonObject row=element.getAsJsonObject();CampaignJson.fields(row,"id","memberId","memberVersion","hash","expires");
                String id=CampaignJson.uuid(CampaignJson.string(row,"id")),member=CampaignJson.uuid(CampaignJson.string(row,"memberId"));
                long version=CampaignJson.integer(row,"memberVersion");
                if(!ids.add(id) || version<1 || version>access.snapshot().domains().participation().member(UUID.fromString(member)).version()
                        || !CampaignJson.string(row,"hash").matches("[0-9a-f]{64}") || !hashes.add(CampaignJson.string(row,"hash")) || CampaignJson.integer(row,"expires")<1)throw new IllegalArgumentException();
            }
            for(JsonElement element:attempts) {
                JsonObject row=element.getAsJsonObject();CampaignJson.fields(row,"bindingId","requestId","candidateId","precondition","revision","action","input","characterId");
                if(!ids.contains(CampaignJson.uuid(CampaignJson.string(row,"bindingId"))) || !requests.add(CampaignJson.uuid(CampaignJson.string(row,"requestId")))
                        || !CampaignJson.string(row,"candidateId").matches("[A-Za-z0-9_-]{43}") || !CampaignJson.string(row,"precondition").matches("[A-Za-z0-9_-]{43}")
                        || CampaignJson.integer(row,"revision")>access.revision() || !Set.of("character-command","combat-command").contains(CampaignJson.string(row,"action")))throw new IllegalArgumentException();
                UUID character=UUID.fromString(CampaignJson.uuid(CampaignJson.string(row,"characterId")));
                if(!character.equals(CampaignParticipationCommands.characterFor(CampaignJson.string(row,"action"),CampaignJson.object(row,"input"))))throw new IllegalArgumentException();
            }
        } catch(RuntimeException malformed) { throw denied(503,"PLAYER_SECURITY_UNAVAILABLE"); }
    }
    private <T> T locked(Supplier<T> action) { synchronized (storage) { return action.get(); } }
    private long now() { return clock.millis(); }
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) { return CampaignJson.sha256(value.getBytes(StandardCharsets.US_ASCII)); }
    private static boolean same(String first, String second) { return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8)); }
    private static boolean paused(JsonObject local) { return local.has("paused") && local.get("paused").getAsBoolean(); }
    private static JsonArray array(JsonObject object, String key) { return object.has(key) ? object.getAsJsonArray(key).deepCopy() : new JsonArray(); }
    private static String text(JsonObject object, String key) { try { return CampaignJson.string(object, key); } catch (RuntimeException invalid) { throw denied(400, "PLAYER_INVALID_REQUEST"); } }
    private static String uuid(JsonObject object, String key) { try { return CampaignJson.uuid(text(object, key)); } catch (RuntimeException invalid) { throw denied(400, "PLAYER_INVALID_REQUEST"); } }
    private static boolean bool(JsonObject object, String key) { String value = text(object,key); if (!Set.of("true","false").contains(value)) throw denied(400,"PLAYER_INVALID_REQUEST"); return Boolean.parseBoolean(value); }
    private static void fields(JsonObject object, String... keys) { if (!object.keySet().equals(Set.of(keys))) throw denied(400,"PLAYER_INVALID_REQUEST"); }
    private static PlayerGatewayException denied(int status, String code) { return new PlayerGatewayException(status, code); }
}
