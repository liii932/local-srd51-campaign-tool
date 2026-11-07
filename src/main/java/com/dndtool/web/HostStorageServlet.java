package com.dndtool.web;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel;
import com.dndtool.module.composition.Schema2CompositionKernel.Diagnostic;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.storage.CampaignStorageService;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignJson;
import com.dndtool.service.storage.LocalRuleLibrary;
import com.dndtool.service.storage.LocalRuleLibraryException;
import com.dndtool.service.storage.StorageException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Protected Web interface for the rule-selected local campaign and its managed files. */
@WebServlet(name = "HostStorageServlet", urlPatterns = {"/host/storage", "/api/host/storage/*"})
public final class HostStorageServlet extends HttpServlet {
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final int MAX_COMMAND_BYTES = 16 * 1024;
    private static final int MAX_DOMAIN_BYTES = 256 * 1024;
    private static final Set<String> RULE_READS = Set.of("/rule-library", "/rule-review", "/rule-download", "/rule-operation");
    private static final Set<String> RULE_COMMANDS = Set.of("/rule-import", "/rule-import-bundled", "/rule-accept", "/rule-preview", "/rule-recover");
    private static final Set<String> CAMPAIGN_COMMANDS = Set.of("/create", "/open", "/recover", "/save", "/end", "/import", "/delete", "/discard", "/domain");
    private static final Set<String> IDENTITY_FIELDS = Set.of("moduleKey", "releaseVersion");

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!StorageApplicationListener.enabled(request.getServletContext())) { response.sendError(404); return; }
        String action = request.getPathInfo();
        if (action == null && request.getServletPath().equals("/host/storage")) {
            response.setContentType("text/html; charset=UTF-8");
            try (var input = getServletContext().getResourceAsStream("/WEB-INF/storage.html")) {
                if (input == null) { response.sendError(503); return; }
                input.transferTo(response.getOutputStream());
            }
            return;
        }
        if ("/readiness".equals(action)) { readiness(request, response); return; }
        if (action == null || !Set.of("/state", "/download").contains(action) && !RULE_READS.contains(action)) {
            response.sendError(404); return;
        }
        try {
            if ("/download".equals(action)) { download(request, response); return; }
            if (RULE_READS.contains(action)) ruleRead(request, response, service(request).ruleLibrary(), action);
            else writeState(request, response, service(request), null);
        }
        catch (LocalRuleLibraryException failure) { ruleError(response, failure); }
        catch (StorageException failure) { error(response, 409, failure.code()); }
        catch (Schema2Exception failure) { compositionError(response, failure); }
        catch (IllegalArgumentException failure) { error(response, 400, "INVALID_REQUEST"); }
        catch (RuntimeException | LinkageError failure) { error(response, 503, "STORAGE_UNAVAILABLE"); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!StorageApplicationListener.enabled(request.getServletContext())) { response.sendError(404); return; }
        try {
            String action = request.getPathInfo();
            if (action == null || !CAMPAIGN_COMMANDS.contains(action) && !RULE_COMMANDS.contains(action)) {
                response.sendError(404); return;
            }
            boolean ruleCommand = RULE_COMMANDS.contains(action);
            boolean importing = "/import".equals(action) || "/rule-import".equals(action);
            int maximum = switch (action) {
                case "/rule-import" -> ProductionRuleArtifact.MAX_ZIP_BYTES;
                case "/import" -> CampaignJson.MAX_BYTES;
                case "/domain" -> MAX_DOMAIN_BYTES * 6 + 1024;
                default -> MAX_COMMAND_BYTES;
            };
            String mediaType = importing ? "application/octet-stream" : "application/x-www-form-urlencoded";
            if (request.getQueryString() != null || request.getContentLengthLong() < 0 || request.getContentLengthLong() > maximum
                    || request.getContentType() == null
                    || !request.getContentType().split(";", 2)[0].equalsIgnoreCase(mediaType)) {
                error(response, 400, "INVALID_REQUEST"); return;
            }
            request.setCharacterEncoding("UTF-8");
            byte[] upload = importing ? request.getInputStream().readNBytes(maximum + 1) : null;
            if (importing && (upload.length == 0 || upload.length != request.getContentLengthLong())) throw new IllegalArgumentException("INVALID_REQUEST");
            Map<String, List<String>> fields = importing ? Map.of("fileSha256", List.of(CampaignJson.sha256(upload))) : fields(request, action);
            String epochText = request.getHeader("X-Host-State-Epoch");
            String revisionText = (String) request.getAttribute(HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE);
            String requestId = (String) request.getAttribute(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
            String suppliedDigest = (String) request.getAttribute(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE);
            String digest = StorageRequestDigest.sha256(action.substring(1), epochText, revisionText, fields);
            if (suppliedDigest == null || !MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII), suppliedDigest.getBytes(StandardCharsets.US_ASCII))) {
                error(response, 400, "INVALID_REQUEST_DIGEST"); return;
            }
            long epoch = number(epochText), revision = number(revisionText);
            if ((ruleCommand || Set.of("/create", "/open", "/recover", "/import", "/delete", "/discard").contains(action)) && revision != 0)
                throw new IllegalArgumentException("INVALID_REQUEST");
            CampaignStorageService service = service(request);
            if (service.status().epoch() != epoch) { error(response, 409, "STALE_EPOCH"); return; }
            if (ruleCommand) {
                writeRuleResult(response, ruleCommand(service, action, requestId, fields, upload));
                return;
            }
            Object result = switch (action) {
                case "/create" -> service.create(single(fields, "name"), members(fields));
                case "/open" -> service.open(single(fields, "fileKey"));
                case "/recover" -> service.recover(single(fields, "runId"));
                case "/import" -> service.importFile(upload);
                case "/delete" -> { service.deleteSavedFile(single(fields, "fileKey")); yield null; }
                case "/discard" -> { service.discardResidual(single(fields, "runId")); yield null; }
                case "/domain" -> service.domainCommand(requestId, epoch, revision, single(fields, "action"), domainPayload(single(fields, "payload")));
                case "/save" -> service.save(requestId, epoch, revision);
                case "/end" -> service.end(requestId, epoch, revision);
                default -> throw new IllegalArgumentException("INVALID_REQUEST");
            };
            writeState(request, response, service, result);
        } catch (LocalRuleLibraryException failure) {
            ruleError(response, failure);
        } catch (StorageException failure) {
            error(response, 409, failure.code());
        } catch (Schema2Exception failure) {
            compositionError(response, failure);
        } catch (IllegalArgumentException failure) {
            error(response, 400, "INVALID_REQUEST");
        } catch (Exception | LinkageError failure) {
            error(response, 503, "STORAGE_UNAVAILABLE");
        }
    }

    private static CampaignStorageService service(HttpServletRequest request) {
        return StorageApplicationListener.service(request.getServletContext());
    }

    /** Read-only deployment check; liveness alone does not establish a usable campaign store. */
    private static void readiness(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            CampaignStorageService service = service(request);
            JsonObject body = new JsonObject();
            boolean ready;
            synchronized (service) {
                var status = service.status();
                service.savedCampaigns();
                if ("ACTIVE".equals(status.state())) service.current();
                ready = Set.of("IDLE", "ACTIVE").contains(status.state());
                body.addProperty("status", ready ? "OK" : "ERROR");
                body.addProperty("mode", "SQLITE");
                body.addProperty("ready", ready);
                body.addProperty("state", status.state());
                if (!ready) body.addProperty("code", "BLOCKED".equals(status.state())
                        ? "STORAGE_BLOCKED" : "STORAGE_RECOVERY_REQUIRED");
            }
            response.setStatus(ready ? 200 : 503);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write(JSON.toJson(body));
        } catch (RuntimeException | LinkageError failure) {
            response.setStatus(503);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"status\":\"ERROR\",\"mode\":\"SQLITE\",\"ready\":false,"
                    + "\"state\":\"UNAVAILABLE\",\"code\":\"STORAGE_UNAVAILABLE\"}");
        }
    }

    private static Map<String, List<String>> fields(HttpServletRequest request, String action) {
        Set<String> expected = switch (action) {
            case "/create" -> Set.of("name", "members");
            case "/open", "/delete" -> Set.of("fileKey");
            case "/recover", "/discard" -> Set.of("runId");
            case "/domain" -> Set.of("action", "payload");
            case "/rule-import-bundled" -> Set.of("member");
            case "/rule-accept" -> Set.of("member", "reviewProtocol");
            case "/rule-preview" -> Set.of("members");
            case "/rule-recover" -> Set.of("operationId");
            default -> Set.of();
        };
        if (!request.getParameterMap().keySet().equals(expected)) throw new IllegalArgumentException("INVALID_REQUEST");
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String key : expected) {
            String[] submitted = request.getParameterValues(key);
            int maximum = "members".equals(key) ? Schema2CompositionKernel.MAX_PACKAGES : 1;
            if (submitted == null || submitted.length == 0 || submitted.length > maximum)
                throw new IllegalArgumentException("INVALID_REQUEST");
            values.put(key, List.copyOf(Arrays.asList(submitted)));
        }
        return values;
    }

    private static Object ruleCommand(CampaignStorageService service, String action, String requestId,
            Map<String, List<String>> fields, byte[] upload) {
        if ("/rule-preview".equals(action)) return service.previewRules(members(fields));
        LocalRuleLibrary library = service.ruleLibrary();
        return switch (action) {
            case "/rule-import" -> library.importArtifact(CampaignJson.uuid(requestId), upload);
            case "/rule-import-bundled" -> {
                StoredMember selected = member(single(fields, "member"));
                var artifact = BundledCampaignRules.artifactsAll().stream().filter(candidate -> bundledMember(candidate).equals(selected))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("INVALID_REQUEST"));
                yield library.importArtifact(CampaignJson.uuid(requestId), artifact.originalZip());
            }
            case "/rule-accept" -> library.accept(CampaignJson.uuid(requestId), member(single(fields, "member")),
                    single(fields, "reviewProtocol"), "local-dm");
            case "/rule-recover" -> library.recover(CampaignJson.uuid(single(fields, "operationId")));
            default -> throw new IllegalArgumentException("INVALID_REQUEST");
        };
    }

    private static void ruleRead(HttpServletRequest request, HttpServletResponse response, LocalRuleLibrary library, String action)
            throws IOException {
        if ("/rule-library".equals(action)) {
            requireQuery(request, Set.of());
            var candidates = BundledCampaignRules.artifactsAll().stream().map(artifact -> Map.of(
                    "member", bundledMember(artifact), "name", artifact.authorPackage().packageDisplayName())).toList();
            writeRuleResult(response, Map.of("inventory", library.inventory(), "bundled", candidates,
                    "maxZipBytes", ProductionRuleArtifact.MAX_ZIP_BYTES, "maxMembers", Schema2CompositionKernel.MAX_PACKAGES));
            return;
        }
        if ("/rule-operation".equals(action)) {
            requireQuery(request, Set.of("operationId"));
            writeRuleResult(response, library.inspect(CampaignJson.uuid(request.getParameter("operationId"))));
            return;
        }
        boolean downloading = "/rule-download".equals(action);
        requireQuery(request, downloading ? Set.of("moduleKey", "releaseVersion", "rawZipSha256") : IDENTITY_FIELDS);
        String moduleKey = request.getParameter("moduleKey"), releaseVersion = request.getParameter("releaseVersion");
        validateIdentity(moduleKey, releaseVersion);
        String raw = downloading ? request.getParameter("rawZipSha256") : null;
        if (downloading && !validDigest(raw)) throw new IllegalArgumentException("INVALID_REQUEST");
        var review = library.review(moduleKey, releaseVersion);
        if (downloading) {
            if (!raw.equals(review.imported().member().rawZipSha256())) throw new StorageException("RULE_MEMBER_MISMATCH", "Rule download identity differs");
            byte[] zip = review.artifact().originalZip();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"rule-" + raw + ".zip\"");
            response.setHeader("Cache-Control", "no-store");
            response.setContentLength(zip.length);
            response.getOutputStream().write(zip);
            return;
        }
        JsonObject result = new JsonObject();
        result.add("imported", JSON.toJsonTree(review.imported()));
        result.add("acceptance", JSON.toJsonTree(review.acceptance()));
        result.addProperty("reviewProtocol", LocalRuleLibrary.REVIEW_PROTOCOL);
        result.add("declaration", JsonParser.parseString(new String(review.artifact().logicalJson(), StandardCharsets.UTF_8)));
        writeRuleResult(response, result);
    }

    private static void requireQuery(HttpServletRequest request, Set<String> fields) {
        if (!request.getParameterMap().keySet().equals(fields)
                || fields.stream().anyMatch(field -> request.getParameterValues(field).length != 1))
            throw new IllegalArgumentException("INVALID_REQUEST");
    }

    private static StoredMember bundledMember(ProductionRuleArtifact artifact) {
        return new StoredMember(artifact.moduleKey(), artifact.releaseVersion(), artifact.role(), artifact.rawZipSha256(), artifact.logicalSha256());
    }

    private static List<StoredMember> members(Map<String, List<String>> fields) {
        return fields.get("members").stream().map(HostStorageServlet::member).toList();
    }

    private static StoredMember member(String value) {
        String[] parts = value.split("@", -1);
        if (parts.length != 5 || !validDigest(parts[2]) || !validDigest(parts[3])) throw new IllegalArgumentException("INVALID_REQUEST");
        validateIdentity(parts[0], parts[1]);
        return new StoredMember(parts[0], parts[1], Role.valueOf(parts[4]), parts[2], parts[3]);
    }

    private static void validateIdentity(String moduleKey, String releaseVersion) {
        if (!moduleKey.matches("[a-z][a-z0-9_]{0,63}") || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("INVALID_REQUEST");
    }

    private static boolean validDigest(String value) { return value.matches("[0-9a-f]{64}"); }

    private static void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!request.getParameterMap().keySet().equals(Set.of("fileKey")) || request.getParameterValues("fileKey").length != 1) {
            error(response, 400, "INVALID_REQUEST"); return;
        }
        String fileKey = CampaignJson.uuid(request.getParameter("fileKey"));
        CampaignStorageService managed = service(request);
        synchronized (managed) {
            var file = managed.savedFile(fileKey);
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"campaign-" + fileKey + ".json\"");
            response.setHeader("Cache-Control", "no-store");
            try (var input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                input.transferTo(response.getOutputStream());
            }
        }
    }

    private static String single(Map<String, List<String>> fields, String name) { return fields.get(name).getFirst(); }
    private static JsonObject domainPayload(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOMAIN_BYTES) throw new IllegalArgumentException("INVALID_REQUEST");
        return CampaignJson.read(bytes);
    }
    private static long number(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("INVALID_REQUEST");
        return Long.parseLong(value);
    }

    private static void writeState(HttpServletRequest request, HttpServletResponse response,
            CampaignStorageService service, Object result) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("status", "OK");
        synchronized (service) {
            var status = service.status();
            JsonObject state = JSON.toJsonTree(status).getAsJsonObject();
            state.addProperty("epoch", Long.toString(status.epoch()));
            body.add("storage", state);
            body.add("savedCampaigns", JSON.toJsonTree(service.savedCampaigns()));
            if ("ACTIVE".equals(status.state())) {
                var current = service.current();
                JsonObject campaign = JSON.toJsonTree(current).getAsJsonObject();
                campaign.addProperty("epoch", Long.toString(current.epoch()));
                body.add("campaign", campaign);
            } else body.add("campaign", JsonNull.INSTANCE);
        }
        Object csrf = request.getAttribute(HostRequestSecurityFilter.CSRF_TOKEN_REQUEST_ATTRIBUTE);
        if (csrf == null && request.getSession(false) != null)
            csrf = request.getSession(false).getAttribute(HostRequestSecurityFilter.CSRF_TOKEN_SESSION_ATTRIBUTE);
        body.addProperty("csrf", csrf instanceof String token ? token : null);
        if (result instanceof CampaignStorageService.SaveResult saved) body.addProperty("savedFile", saved.fileKey());
        if (result instanceof CampaignStorageService.ImportResult imported) body.addProperty("importedFile", imported.fileKey());
        if (result instanceof CampaignStorageService.CampaignView accepted && accepted.lastCommand() != null)
            body.add("commandResult", JSON.toJsonTree(accepted.lastCommand()));
        response.setContentType("application/json; charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write(JSON.toJson(body));
    }

    private static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"status\":\"ERROR\",\"code\":" + JsonSupport.quote(code) + "}");
    }

    private static void writeRuleResult(HttpServletResponse response, Object result) throws IOException {
        writeRuleJson(response, 200, Map.of("status", "OK", "result", result));
    }

    private static void ruleError(HttpServletResponse response, LocalRuleLibraryException failure) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("status", "ERROR");
        body.addProperty("code", failure.code());
        if (failure.operationId() != null) body.addProperty("operationId", failure.operationId());
        var issues = new JsonArray();
        if (failure.issue() != null) issues.add(JSON.toJsonTree(failure.issue()));
        if ("RULE_PACKAGE_INVALID".equals(failure.code()) && failure.getCause() instanceof Schema2Exception invalid)
            issues.addAll(diagnosticIssues(invalid.diagnostic()));
        body.add("issues", issues);
        writeRuleJson(response, 409, body);
    }

    private static void compositionError(HttpServletResponse response, Schema2Exception failure) throws IOException {
        var diagnostic = failure.diagnostic();
        writeRuleJson(response, 409, Map.of("status", "ERROR", "code", diagnostic.code(), "issues", diagnosticIssues(diagnostic)));
    }

    private static JsonArray diagnosticIssues(Diagnostic diagnostic) {
        var issues = new JsonArray();
        diagnostic.locations().stream().limit(Schema2CompositionKernel.MAX_PACKAGES).forEach(location -> {
            JsonObject issue = new JsonObject();
            issue.addProperty("diagnosticCode", diagnostic.code());
            issue.addProperty("stage", diagnostic.stage().name());
            issue.addProperty("moduleKey", location.moduleKey());
            issue.addProperty("releaseVersion", location.releaseVersion());
            issue.addProperty("expectedRawZipSha256", location.rawZipSha256());
            issue.addProperty("path", diagnosticPath(location.path()));
            String pointer = location.pointer();
            if (pointer != null && pointer.codePointCount(0, pointer.length()) <= 512 && pointer.codePoints().noneMatch(Character::isISOControl))
                issue.addProperty("pointer", pointer);
            issue.addProperty("byteOffset", location.byteOffset());
            issues.add(issue);
        });
        return issues;
    }

    private static String diagnosticPath(String path) {
        if (path == null) return null;
        // Unsafe entry names are reported at their container offset without echoing an absolute or traversal path.
        return path.length() <= 240 && path.matches("(?:\\$[a-z]+|[A-Za-z0-9][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*)")
                ? path : "$zip";
    }

    private static void writeRuleJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json; charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write(JSON.toJson(body));
    }
}
