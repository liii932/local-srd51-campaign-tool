package com.dndtool.web;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.storage.*;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Uses the unchanged Host boundary, CSRF, epoch and canonical HTTP request digest. */
@WebServlet(name = "HostPlayerServlet", urlPatterns = "/api/host/storage/players/*")
public final class HostPlayerServlet extends HttpServlet {
    private static final Map<String,Set<String>> FIELDS = Map.ofEntries(
            Map.entry("invite",Set.of()), Map.entry("close",Set.of()), Map.entry("approve",Set.of("applicationId","memberId")),
            Map.entry("reject",Set.of("applicationId")), Map.entry("pause",Set.of("paused")), Map.entry("proposal-reject",Set.of("proposalId")),
            Map.entry("proposal-narrative",Set.of("proposalId","text")),Map.entry("proposal-execute",Set.of("proposalId","ability","dc","reason")),
            Map.entry("control",Set.of("payload")), Map.entry("member",Set.of("payload")), Map.entry("share",Set.of("payload")), Map.entry("exploration",Set.of("payload")));
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/state".equals(request.getPathInfo())) { PlayerServlet.error(response,404,"PLAYER_REQUEST_DENIED"); return; }
        try {
            if (com.dndtool.security.PlayerListenerConfiguration.from(request.getServletContext()) == null) {
                JsonObject disabled = new JsonObject(); disabled.addProperty("status","OK"); disabled.addProperty("configured",false);
                disabled.addProperty("csrf",Objects.toString(request.getAttribute(HostRequestSecurityFilter.CSRF_TOKEN_REQUEST_ATTRIBUTE),"")); write(response,disabled); return;
            }
            JsonObject body = PlayerApplicationAccess.service(request.getServletContext()).hostState();
            body.addProperty("configured",true);
            body.addProperty("csrf",Objects.toString(request.getAttribute(HostRequestSecurityFilter.CSRF_TOKEN_REQUEST_ATTRIBUTE),""));
            body.addProperty("playerOrigin",com.dndtool.security.PlayerListenerConfiguration.from(request.getServletContext()).origin()); write(response,body);
        } catch (PlayerGatewayException failure) { PlayerServlet.error(response,failure.status(),failure.code()); }
        catch (RuntimeException failure) { PlayerServlet.error(response,503,"PLAYER_UNAVAILABLE"); }
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = request.getPathInfo(), action = path == null ? "" : path.substring(1);
            Set<String> expected = FIELDS.get(action);
            if (expected == null) { PlayerServlet.error(response,404,"PLAYER_REQUEST_DENIED"); return; }
            if (request.getQueryString() != null || request.getContentLengthLong() < 0 || request.getContentLengthLong() > 32*1024
                    || request.getContentType() == null || !request.getContentType().split(";",2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) throw new IllegalArgumentException();
            request.setCharacterEncoding("UTF-8");
            if (!request.getParameterMap().keySet().equals(expected)) throw new IllegalArgumentException();
            Map<String,List<String>> values = new LinkedHashMap<>(); JsonObject input = new JsonObject();
            for (String key : expected) {
                String[] field = request.getParameterValues(key); if (field == null || field.length != 1 || field[0] == null) throw new IllegalArgumentException();
                values.put(key,List.of(field[0])); input.addProperty(key,field[0]);
            }
            String epoch = request.getHeader("X-Host-State-Epoch"), revision = (String) request.getAttribute(HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE);
            String supplied = (String) request.getAttribute(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE);
            String digest = StorageRequestDigest.sha256("players/"+action,epoch,revision,values);
            if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            JsonObject body = PlayerApplicationAccess.service(request.getServletContext()).hostCommand(action,input,
                    (String) request.getAttribute(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),Long.parseLong(epoch),Long.parseLong(revision));
            body.addProperty("csrf",Objects.toString(request.getAttribute(HostRequestSecurityFilter.CSRF_TOKEN_REQUEST_ATTRIBUTE),""));
            body.addProperty("playerOrigin",com.dndtool.security.PlayerListenerConfiguration.from(request.getServletContext()).origin()); write(response,body);
        } catch (PlayerGatewayException failure) { PlayerServlet.error(response,failure.status(),failure.code()); }
        catch (StorageException failure) { PlayerServlet.error(response,409,failure.code()); }
        catch (IllegalArgumentException failure) { PlayerServlet.error(response,400,"INVALID_REQUEST"); }
        catch (RuntimeException failure) { PlayerServlet.error(response,503,"PLAYER_UNAVAILABLE"); }
    }
    private static void write(HttpServletResponse response, JsonObject body) throws IOException {
        response.setStatus(200); response.setContentType("application/json; charset=UTF-8"); response.getOutputStream().write(CampaignJson.canonical(body));
    }
}
