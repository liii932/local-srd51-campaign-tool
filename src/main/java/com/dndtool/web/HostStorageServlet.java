package com.dndtool.web;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.storage.CampaignStorageService;
import com.dndtool.service.storage.CampaignJson;
import com.dndtool.service.storage.StorageException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
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
        if (action == null || !Set.of("/state", "/download").contains(action)) { response.sendError(404); return; }
        try {
            if ("/download".equals(action)) { download(request, response); return; }
            writeState(request, response, service(request), null);
        }
        catch (StorageException failure) { error(response, 409, failure.code()); }
        catch (RuntimeException failure) { error(response, 503, "STORAGE_UNAVAILABLE"); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!StorageApplicationListener.enabled(request.getServletContext())) { response.sendError(404); return; }
        try {
            String action = request.getPathInfo();
            if (action == null || !Set.of("/create", "/open", "/recover", "/characters", "/heal", "/save", "/end", "/import", "/delete", "/discard", "/domain").contains(action)) {
                response.sendError(404); return;
            }
            boolean importing = "/import".equals(action);
            int maximum = importing ? CampaignJson.MAX_BYTES : "/domain".equals(action) ? MAX_DOMAIN_BYTES * 6 + 1024 : MAX_COMMAND_BYTES;
            String mediaType = importing ? "application/octet-stream" : "application/x-www-form-urlencoded";
            if (request.getQueryString() != null || request.getContentLengthLong() < 0 || request.getContentLengthLong() > maximum
                    || request.getContentType() == null
                    || !request.getContentType().split(";", 2)[0].equalsIgnoreCase(mediaType)) {
                error(response, 400, "INVALID_REQUEST"); return;
            }
            request.setCharacterEncoding("UTF-8");
            byte[] upload = importing ? request.getInputStream().readNBytes(CampaignJson.MAX_BYTES + 1) : null;
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
            if (Set.of("/create", "/open", "/recover", "/import", "/delete", "/discard").contains(action) && revision != 0)
                throw new IllegalArgumentException("INVALID_REQUEST");
            CampaignStorageService service = service(request);
            if (service.status().epoch() != epoch) { error(response, 409, "STALE_EPOCH"); return; }
            Object result = switch (action) {
                case "/create" -> service.create(single(fields, "name"), fields.get("modules"));
                case "/open" -> service.open(single(fields, "fileKey"));
                case "/recover" -> service.recover(single(fields, "runId"));
                case "/import" -> service.importFile(upload);
                case "/delete" -> { service.deleteSavedFile(single(fields, "fileKey")); yield null; }
                case "/discard" -> { service.discardResidual(single(fields, "runId")); yield null; }
                case "/domain" -> service.domainCommand(requestId, epoch, revision, single(fields, "action"), domainPayload(single(fields, "payload")));
                case "/characters" -> service.addCharacter(requestId, epoch, revision, single(fields, "name"), number(single(fields, "maximumHp")), number(single(fields, "currentHp")));
                case "/heal" -> service.heal(requestId, epoch, revision, single(fields, "characterId"), number(single(fields, "characterVersion")), number(single(fields, "amount")));
                case "/save" -> service.save(requestId, epoch, revision);
                case "/end" -> service.end(requestId, epoch, revision);
                default -> throw new IllegalArgumentException("INVALID_REQUEST");
            };
            writeState(request, response, service, result);
        } catch (StorageException failure) {
            error(response, 409, failure.code());
        } catch (IllegalArgumentException failure) {
            error(response, 400, "INVALID_REQUEST");
        } catch (Exception failure) {
            error(response, 503, "STORAGE_UNAVAILABLE");
        }
    }

    private static CampaignStorageService service(HttpServletRequest request) {
        return StorageApplicationListener.service(request.getServletContext());
    }

    private static Map<String, List<String>> fields(HttpServletRequest request, String action) {
        Set<String> expected = switch (action) {
            case "/create" -> Set.of("name", "modules");
            case "/open", "/delete" -> Set.of("fileKey");
            case "/recover", "/discard" -> Set.of("runId");
            case "/characters" -> Set.of("name", "maximumHp", "currentHp");
            case "/heal" -> Set.of("characterId", "characterVersion", "amount");
            case "/domain" -> Set.of("action", "payload");
            default -> Set.of();
        };
        if (!request.getParameterMap().keySet().equals(expected)) throw new IllegalArgumentException("INVALID_REQUEST");
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String key : expected) {
            String[] submitted = request.getParameterValues(key);
            if (submitted == null || submitted.length == 0 || submitted.length > 8 || (!key.equals("modules") && submitted.length != 1))
                throw new IllegalArgumentException("INVALID_REQUEST");
            values.put(key, List.copyOf(Arrays.asList(submitted)));
        }
        return values;
    }

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
            body.add("modules", JSON.toJsonTree(service.catalog()));
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
}
