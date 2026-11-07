package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.CheckResult;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthResult;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRuleBindingIntegrationTest {
    private static final String BASE = "srd51_runtime";
    private static final String HOUSE = "dm_runtime_house_rules";
    private static final String STR = "ability.strength";
    private static final String WIS = "ability.wisdom";
    private static final RuleIdentity ATHLETICS = new RuleIdentity(BASE, "character.proficiency", "proficiency.skill.athletics");
    private static final RuleIdentity ARTISAN = new RuleIdentity(BASE, "character.background", "background.custom_artisan");
    private static final RuleIdentity CHECK = new RuleIdentity("srd51_characters", "adventure.check", "check.srd2014");
    private static final RuleIdentity DAMAGE = new RuleIdentity(HOUSE, "adventure.vital_effect", "vital_effect.dm_damage");
    private static final RuleIdentity HEALING = new RuleIdentity(HOUSE, "adventure.vital_effect", "vital_effect.dm_healing");

    @TempDir Path temporary;

    @Test void twoCampaignsBindCandidatesPlansOutcomesAndReopenedFiles() throws Exception {
        List<Integer> dice = new ArrayList<>();
        try (var service = new CampaignStorageService(temporary.resolve("two-bindings"), boundary -> { },
                sides -> { dice.add(sides); return 10; })) {
            var ordinary = start(service, BundledCampaignRules.STANDARD);
            var ordinaryBinding = assertBinding(service, ordinary, STR, "3", "language.dwarvish", 6);
            var ordinaryRequest = id();
            submit(service, ordinaryRequest, input(service, ordinary, STR, 14));
            assertResult(service, ordinary, ordinaryRequest, new CheckResult(10, null, 10, 2, 2, 14, true, false, false),
                    DAMAGE, 6, 6, 6, 5, "成功");
            var ordinaryView = service.current();
            String ordinaryFile = end(service);

            var variant = start(service, BundledCampaignRules.CHECK_VARIANT);
            var variantBinding = assertBinding(service, variant, WIS, "4", "language.gnomish", 3);
            assertEquals(ordinaryBinding.members().subList(0, 3), variantBinding.members().subList(0, 3));
            assertNotEquals(ordinaryBinding.members().getLast().rawZipSha256(), variantBinding.members().getLast().rawZipSha256());
            assertNotEquals(ordinaryBinding.members().getLast().logicalSha256(), variantBinding.members().getLast().logicalSha256());
            assertNotEquals(ordinaryBinding.compositionSha256(), variantBinding.compositionSha256());
            assertNotEquals(ordinaryBinding.finalRulesSha256(), variantBinding.finalRulesSha256());
            assertNotEquals(ordinaryBinding.chainSha256(), variantBinding.chainSha256());
            var variantBefore = service.accessSnapshot();
            assertCode("STALE_EPOCH", () -> service.domainCommand(id(), ordinaryView.epoch(), variantBefore.revision(),
                    "check-effects", input(service, variant, WIS, 14)));
            assertCode("DOMAIN_REJECTED", () -> submit(service, id(), input(service, variant, STR, 14)));
            assertEquals(variantBefore, service.accessSnapshot());
            assertEquals(List.of(20), dice);

            var failedCheck = id();
            submit(service, failedCheck, input(service, variant, WIS, 14));
            assertResult(service, variant, failedCheck, new CheckResult(10, null, 10, 1, 2, 13, false, false, false),
                    HEALING, 5, 0, 12, 2, "失败");
            var successfulCheck = id();
            submit(service, successfulCheck, input(service, variant, WIS, 13));
            assertResult(service, variant, successfulCheck, new CheckResult(10, null, 10, 1, 2, 13, true, false, false),
                    DAMAGE, 3, 3, 9, 4, "成功");
            var variantView = service.current();
            String variantFile = end(service);

            byte[] ordinaryBytes = Files.readAllBytes(service.savedFile(ordinaryFile));
            byte[] variantBytes = Files.readAllBytes(service.savedFile(variantFile));
            assertBadFilesRejected(service, ordinaryBytes, variantBytes);
            assertReopened(service, ordinaryFile, ordinaryView);
            assertReopened(service, variantFile, variantView);
            assertEquals(List.of(20, 20, 20), dice);
        }
    }

    @Test void variantRejectsBadUnselectedBranchAndStaleVersionsThenRecoversOneCommit() throws Exception {
        Path root = temporary.resolve("faults");
        List<Integer> dice = new ArrayList<>();
        AtomicReference<Boundary> fail = new AtomicReference<>();
        Scenario scenario;
        JsonObject intent;
        long epoch, revision;
        String run, request = id();
        try (var service = new CampaignStorageService(root, boundary -> {
            if (fail.compareAndSet(boundary, null)) throw new IllegalStateException("injected");
        }, sides -> { dice.add(sides); return 10; })) {
            scenario = start(service, BundledCampaignRules.CHECK_VARIANT);
            var before = service.accessSnapshot();
            run = before.runId(); epoch = before.epoch(); revision = before.revision();
            intent = input(service, scenario, WIS, 13);
            JsonObject badBranch = intent.deepCopy();
            badBranch.getAsJsonObject("failure").addProperty("itemDelta", "-4");
            assertCode("INVALID_FILE", () -> submit(service, id(), badBranch));
            JsonObject staleCharacter = intent.deepCopy();
            staleCharacter.addProperty("characterVersion", Long.toString(intent.get("characterVersion").getAsLong() + 1));
            assertCode("STALE_CHARACTER", () -> submit(service, id(), staleCharacter));
            assertCode("STALE_REVISION", () -> service.domainCommand(id(), epoch, revision - 1, "check-effects", intent));
            assertEquals(List.of(), dice);
            assertEquals(before, service.accessSnapshot());

            fail.set(Boundary.AFTER_CHARACTER_UPDATE);
            assertThrows(IllegalStateException.class, () -> submit(service, request, intent));
            assertEquals(before, service.accessSnapshot());
            assertEquals(List.of(20), dice);

            fail.set(Boundary.AFTER_BUSINESS_COMMIT);
            assertCode("COMMIT_UNKNOWN", () -> submit(service, request, intent));
            assertCode("RUN_BLOCKED", () -> service.domainCommand(id(), epoch, revision, "check-effects", intent));
            assertEquals(List.of(20, 20), dice);
        }

        try (var service = new CampaignStorageService(root, boundary -> { },
                sides -> { throw new AssertionError("Recovery or replay requested a new die"); })) {
            var restored = service.recover(run);
            assertNotEquals(epoch, restored.epoch());
            assertEquals(revision + 1, restored.revision());
            assertBinding(service, scenario, WIS, "4", "language.gnomish", 3);
            assertResult(service, scenario, request, new CheckResult(10, null, 10, 1, 2, 13, true, false, false),
                    DAMAGE, 3, 3, 9, 5, "成功");
            assertCode("STALE_EPOCH", () -> service.domainCommand(request, epoch, revision, "check-effects", intent));
            var replayed = service.domainCommand(request, restored.epoch(), revision, "check-effects", intent);
            assertTrue(replayed.lastCommand().replayed());
            assertEquals(revision + 1, replayed.lastCommand().acceptedRevision());
            assertEquals(restored.domain(), replayed.domain());
            assertEquals(restored.history(), replayed.history());
            JsonObject different = intent.deepCopy();
            different.getAsJsonObject("success").addProperty("message", "不同意图");
            assertCode("IDEMPOTENCY_CONFLICT", () -> service.domainCommand(request, restored.epoch(), revision, "check-effects", different));
            end(service);
        }
    }

    @Test void productionVariantMatchesAuthorBytesAndRejectsAnUnknownMechanism() throws Exception {
        var artifact = BundledCampaignRules.artifacts(BundledCampaignRules.CHECK_VARIANT).getLast();
        assertEquals(HOUSE, artifact.moduleKey());
        assertEquals("4", artifact.releaseVersion());
        Path source = Path.of("rule-packages", "srd51-runtime");
        Path variant = source.resolve("dm-runtime-check-variant");
        Map<String, byte[]> original = artifact.originalMembers();
        assertEquals(Set.of("author-package.json", "changes.json", "rules.json", "README.md", "NOTICE"), original.keySet());
        for (var entry : original.entrySet()) {
            Path path = switch (entry.getKey()) {
                case "NOTICE" -> source.resolve("notice.md");
                case "README.md" -> variant.resolve("package-guide.md");
                default -> variant.resolve(entry.getKey());
            };
            assertArrayEquals(Files.readAllBytes(path), entry.getValue(), entry.getKey());
        }
        var altered = new TreeMap<>(original);
        String rules = new String(original.get("rules.json"), StandardCharsets.UTF_8);
        altered.put("rules.json", rules.replace("DIRECT_VITAL_EFFECT_V1", "UNKNOWN_VITAL_EFFECT").getBytes(StandardCharsets.UTF_8));
        byte[] invalidZip = storedZip(altered);
        assertEquals("S2_TYPE", assertThrows(Schema2Exception.class,
                () -> ProductionRuleArtifact.read(invalidZip)).diagnostic().code());
    }

    @Test void externalAcceptedContentRunsAndRecoversWithoutLibraryButMovingFilesRequiresLocalAcceptance() throws Exception {
        var selected = new ArrayList<>(BundledCampaignRules.artifacts());
        var athletics = CampaignRuleSupportTest.authorRow(BASE, "rules.json", "rule_key", ATHLETICS.ruleKey());
        var mapping = CampaignRuleSupportTest.operation("set_field", athletics);
        mapping.addProperty("field", "ability_key"); mapping.addProperty("value", WIS);
        var damage = CampaignRuleSupportTest.operation("set_field",
                CampaignRuleSupportTest.authorRow(HOUSE, "rules.json", "rule_key", DAMAGE.ruleKey()));
        damage.addProperty("field", "modifier"); damage.addProperty("value", 2);
        selected.add(CampaignRuleSupportTest.extension("external_campaign_rules", selected,
                List.of(), List.of(), List.of(mapping, damage)));
        Path source = temporary.resolve("external-source");
        String run, request = id(); Scenario scenario; CampaignStorageService.CampaignView accepted;
        try (var service = new CampaignStorageService(source, boundary -> { }, sides -> 10)) {
            var members = selected.stream().map(artifact -> new com.dndtool.module.composition.ProductionSchema2Projection.StoredMember(
                    artifact.moduleKey(), artifact.releaseVersion(), artifact.role(), artifact.rawZipSha256(), artifact.logicalSha256())).toList();
            assertLibraryCode("RULE_PACKAGE_MISSING", () -> service.create("外部组合", members));
            assertTrue(service.status().residualRunIds().isEmpty());
            for (var artifact : selected) service.ruleLibrary().importArtifact(id(), artifact.originalZip());
            assertLibraryCode("RULE_PACKAGE_NOT_ACCEPTED", () -> service.previewRules(members));
            for (var member : members) service.ruleLibrary().accept(id(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
            assertEquals(5, service.previewRules(members).binding().members().size());
            service.create("外部组合", members); scenario = populate(service);
            Files.move(source.resolve("rule-library"), source.resolve("retained-library"));
            submit(service, request, input(service, scenario, WIS, 13));
            assertResult(service, scenario, request, new CheckResult(10, null, 10, 1, 2, 13, true, false, false),
                    DAMAGE, 2, 2, 10, 5, "成功");
            accepted = service.current(); run = service.status().runId();
            service.save(id(), accepted.epoch(), accepted.revision());
            assertFalse(Files.exists(source.resolve("rule-library")));
        }
        byte[] complete;
        try (var service = new CampaignStorageService(source, boundary -> { }, sides -> { throw new AssertionError("Recovery rolls"); })) {
            var restored = service.recover(run);
            assertEquals(accepted.domain(), restored.domain()); assertEquals(accepted.history(), restored.history());
            complete = Files.readAllBytes(service.savedFile(end(service)));
            assertFalse(Files.exists(source.resolve("rule-library")));
        }
        try (var service = new CampaignStorageService(temporary.resolve("new-machine"), boundary -> { },
                sides -> { throw new AssertionError("Open rolls"); })) {
            String file = service.importFile(complete).fileKey();
            assertLibraryCode("RULE_PACKAGE_MISSING", () -> service.open(file));
            assertEquals("IDLE", service.status().state());
            for (var artifact : selected) service.ruleLibrary().importArtifact(id(), artifact.originalZip());
            assertLibraryCode("RULE_PACKAGE_NOT_ACCEPTED", () -> service.open(file));
            for (var member : new CampaignCodec().decode(complete).snapshot().domains().material().binding().members())
                service.ruleLibrary().accept(id(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
            var opened = service.open(file);
            assertEquals(accepted.domain(), opened.domain()); assertEquals(accepted.history(), opened.history());
            assertEquals(0, opened.revision()); end(service);

            // A self-consistent frozen projection cannot claim different content for accepted original bytes.
            var sourceProjection = com.dndtool.module.composition.ProductionSchema2Projection.compose(selected);
            damage.addProperty("value", 3);
            var different = new ArrayList<>(selected.subList(0, 4));
            different.add(CampaignRuleSupportTest.extension("external_campaign_rules", different,
                    List.of(), List.of(), List.of(mapping, damage)));
            var changed = com.dndtool.module.composition.ProductionSchema2Projection.compose(different).projection();
            var claimedChains = changed.chains().stream().map(chain -> new FieldChain(chain.target(), chain.field(),
                    chain.steps().stream().map(step -> {
                        var location = step.authorLocation();
                        var claimed = sourceProjection.members().get(step.loadIndex());
                        return new ChainStep(step.op(), step.before(), step.after(), step.provider(), step.loadIndex(),
                                new Location(location.moduleKey(), location.releaseVersion(), claimed.rawZipSha256(),
                                        location.path(), location.pointer(), location.byteOffset()), step.origin());
                    }).toList())).toList();
            var forged = com.dndtool.module.composition.ProductionSchema2Projection.restore(sourceProjection.members(),
                    changed.rules(), changed.entries(), claimedChains);
            byte[] selfConsistent = new CampaignCodec().encode(new CampaignData.Document(id(),
                    new CampaignData.Snapshot(id(), "错绑内容", CampaignDomainData.State.initial(forged))));
            String wrongFile = service.importFile(selfConsistent).fileKey();
            assertCode("RULE_COMPOSITION_MISMATCH", () -> service.open(wrongFile));
            assertEquals("IDLE", service.status().state());
        }
    }

    private static void assertLibraryCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(LocalRuleLibraryException.class, action).code());
    }

    private static Schema2RulesBinding assertBinding(CampaignStorageService service, Scenario scenario, String ability,
            String release, String tradeLanguage, int damage) {
        var snapshot = service.accessSnapshot().snapshot();
        var state = snapshot.domains();
        var binding = state.material().binding();
        assertEquals(List.of(BASE + "@2", "srd51_characters@2", "srd51_magic@2", HOUSE + "@" + release),
                binding.members().stream().map(member -> member.moduleKey() + "@" + member.releaseVersion()).toList());
        assertEquals(binding, character(state, scenario.actor()).initial().character().binding());
        var trade = character(state, scenario.actor()).initial().character().grants().stream()
                .filter(grant -> grant.source().equals(ARTISAN) && grant.slot().entryKey().equals("trade_language"))
                .findFirst().orElseThrow();
        assertEquals(new RuleIdentity(BASE, "character.language", tradeLanguage), trade.target());
        assertFalse(trade.suppressed());

        JsonObject viewRules = service.current().domain().getAsJsonObject("rules");
        assertEquals(binding, new Gson().fromJson(viewRules.get("binding"), Schema2RulesBinding.class));
        JsonObject candidate = viewRules.getAsJsonArray("catalog").asList().stream().map(row -> row.getAsJsonObject())
                .filter(row -> row.get("key").getAsString().equals(ATHLETICS.ruleKey())).findFirst().orElseThrow();
        assertTrue(candidate.get("executable").getAsBoolean());
        assertEquals(ability, candidate.get("abilityKey").getAsString());
        List<Provider> expectedProviders = "3".equals(release) ? List.of(new Provider(BASE, "2"))
                : List.of(new Provider(BASE, "2"), new Provider(HOUSE, "4"));
        var mapping = chain(state, ATHLETICS, Field.ABILITY_KEY);
        assertEquals(expectedProviders, mapping.steps().stream().map(ChainStep::provider).toList());
        assertEquals(new TextValue(ability), mapping.steps().getLast().after().value());
        var modifier = chain(state, DAMAGE, Field.MODIFIER).steps().getFirst();
        assertEquals(new Provider(HOUSE, release), modifier.provider());
        assertEquals(new IntegerValue(damage), modifier.after().value());
        assertEquals(expectedProviders, chain(state, trade.slot(), Field.GRANT).steps().stream().map(ChainStep::provider).toList());
        if ("4".equals(release)) {
            JsonObject override = candidate.getAsJsonArray("overrides").get(0).getAsJsonObject();
            assertEquals("ability_key", override.get("field").getAsString());
            assertEquals(HOUSE, override.get("moduleKey").getAsString());
            assertEquals("4", override.get("releaseVersion").getAsString());
        } else {
            assertTrue(candidate.getAsJsonArray("overrides").isEmpty());
        }
        return binding;
    }

    private static void assertResult(CampaignStorageService service, Scenario scenario, String request, CheckResult check,
            RuleIdentity effect, int rolled, int applied, long hp, long quantity, String message) {
        var snapshot = service.accessSnapshot().snapshot();
        var state = snapshot.domains();
        var root = state.roots().getLast();
        assertEquals("check-effects", root.kind());
        assertEquals(UUID.fromString(request), root.operationId());
        assertEquals(scenario.actor(), root.subjectId());
        var audit = CampaignDomainCodec.decode(root.after().getBytes(StandardCharsets.UTF_8), CampaignCheckEffects.Audit.class);
        var actor = character(state, scenario.actor()).history();
        var target = character(state, scenario.target()).history();
        var checkEvent = actor.events().getLast();
        var healthEvent = target.events().getLast();
        assertEquals(2, root.characterOperations().size());
        assertEquals(Set.of(checkEvent.operationId(), healthEvent.operationId()), Set.copyOf(root.characterOperations()));
        assertEquals(checkEvent.operationId(), audit.checkOperationId());
        assertEquals(healthEvent.operationId(), audit.healthOperationId());
        assertEquals(check, checkEvent.checkResult());
        assertEquals(ATHLETICS, checkEvent.command().checkInput().proficiency());
        assertEquals(effect, healthEvent.command().healthInput().effect());
        assertEquals(new HealthResult(List.of(), null, null, rolled, applied), healthEvent.healthResult());
        assertEquals(audit.characterVersions().get(scenario.actor()) + 1, actor.current().version());
        assertEquals(audit.characterVersions().get(scenario.target()) + 1, target.current().version());
        assertEquals(hp, target.current().hitPoints());
        assertEquals(quantity, state.items().getFirst().quantity());
        assertEquals(2, state.positions().size());
        assertTrue(state.positions().stream().allMatch(p -> p.space().equals(CampaignSpaceData.Ref.world(scenario.camp()))));
        assertEquals(root.space().beforePositions(), root.space().afterPositions());
        assertEquals(check.success(), audit.success());
        assertEquals(message, audit.message());
    }

    private static void assertBadFilesRejected(CampaignStorageService service, byte[] ordinary, byte[] variant) {
        var status = service.status();
        var wrongBinding = CampaignJson.read(variant);
        wrongBinding.getAsJsonObject("domains").add("material",
                CampaignJson.read(ordinary).getAsJsonObject("domains").get("material"));
        assertCode("INVALID_FILE", () -> service.importFile(resign(wrongBinding)));

        var wrongMember = CampaignJson.read(variant);
        wrongMember.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonObject("binding")
                .getAsJsonArray("members").get(3).getAsJsonObject().addProperty("rawZipSha256", "0".repeat(64));
        assertCode("INVALID_FILE", () -> service.importFile(resign(wrongMember)));

        var missingChain = CampaignJson.read(variant);
        missingChain.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonArray("chains").remove(0);
        assertCode("INVALID_FILE", () -> service.importFile(resign(missingChain)));
        assertEquals(status, service.status());
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static byte[] storedZip(Map<String, byte[]> members) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var member : members.entrySet()) {
                byte[] bytes = member.getValue();
                var crc = new CRC32();
                crc.update(bytes);
                var entry = new ZipEntry(member.getKey());
                entry.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(bytes.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record Scenario(UUID actor, UUID target, UUID camp, UUID item) { }

    private static Scenario start(CampaignStorageService service, String profile) {
        CampaignRuleFixtures.create(service, profile, profile);
        return populate(service);
    }

    private static Scenario populate(CampaignStorageService service) {
        for (String name : List.of("检定者", "目标")) {
            command(service, id(), "character-create", object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "custom_artisan"));
        }
        UUID camp = CampaignSpaceFixtures.world(service, "营地");
        var state = service.accessSnapshot().snapshot().domains();
        UUID actor = state.characters().stream().filter(c -> c.name().equals("检定者")).findFirst().orElseThrow().id();
        UUID target = state.characters().stream().filter(c -> c.name().equals("目标")).findFirst().orElseThrow().id();
        CampaignSpaceFixtures.place(service, actor, camp);
        CampaignSpaceFixtures.place(service, target, camp);
        JsonObject item = object("characterId", target.toString(), "name", "绳索", "quantity", "3");
        item.add("itemId", JsonNull.INSTANCE);
        command(service, id(), "item", item);
        return new Scenario(actor, target, camp, service.accessSnapshot().snapshot().domains().items().getFirst().id());
    }

    private static JsonObject input(CampaignStorageService service, Scenario scenario, String ability, int dc) {
        var state = service.accessSnapshot().snapshot().domains();
        var actor = character(state, scenario.actor());
        JsonObject input = object("characterId", scenario.actor().toString(), "characterVersion", Long.toString(actor.history().current().version()));
        input.add("command", CampaignDomainCodec.toJson(new Command("CHECK_RESOLVED", null, 0, null, null, null,
                GrowthChoices.EMPTY, null, new CheckInput(CHECK, "SKILL", ability, ATHLETICS, dc, false, false, "DM确认运动检定和两支后果。"))));
        input.add("success", branch(scenario, DAMAGE, "2", "成功"));
        input.add("failure", branch(scenario, HEALING, "-1", "失败"));
        return CampaignSpaceFixtures.focused(state, input);
    }

    private static JsonObject branch(Scenario scenario, RuleIdentity effect, String delta, String message) {
        JsonObject branch = object("targetId", scenario.target().toString(), "itemId", scenario.item().toString(),
                "itemDelta", delta, "message", message);
        branch.add("healthEffect", CampaignDomainCodec.toJson(effect));
        return branch;
    }

    private static CampaignDomainData.Character character(CampaignDomainData.State state, UUID id) {
        return state.characters().stream().filter(character -> character.id().equals(id)).findFirst().orElseThrow();
    }

    private static FieldChain chain(CampaignDomainData.State state, Identity target, Field field) {
        return state.material().chains().stream().filter(chain -> chain.target().equals(target) && chain.field() == field)
                .findFirst().orElseThrow();
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static JsonObject object(String... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) result.addProperty(pairs[i], pairs[i + 1]);
        return result;
    }

    private static void submit(CampaignStorageService service, String request, JsonObject input) {
        command(service, request, "check-effects", input);
    }

    private static void command(CampaignStorageService service, String request, String action, JsonObject input) {
        CampaignSpaceFixtures.command(service, request, action, input);
    }

    private static String end(CampaignStorageService service) {
        var current = service.current();
        return service.end(id(), current.epoch(), current.revision()).fileKey();
    }

    private static void assertReopened(CampaignStorageService service, String file, CampaignStorageService.CampaignView expected) {
        var opened = service.open(file);
        assertEquals(0, opened.revision());
        assertNotEquals(expected.epoch(), opened.epoch());
        assertEquals(expected.domain(), opened.domain());
        assertEquals(expected.history(), opened.history());
        end(service);
    }

    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(StorageException.class, action).code());
    }
}
