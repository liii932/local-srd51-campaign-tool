package com.dndtool.web;

import com.dndtool.service.storage.*;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Finite JSON transport. Authentication cookies never use the Host HttpSession. */
@WebServlet(name = "PlayerServlet", urlPatterns = {"/player", "/player/", "/api/player/*"})
public final class PlayerServlet extends HttpServlet {
    public static final String SESSION_COOKIE = "DNDPLAYERSESSION", DEVICE_COOKIE = "DNDPLAYERDEVICE";
    private static final int MAX_BODY = 8192;
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            PlayerGatewayService service = PlayerApplicationAccess.service(request.getServletContext());
            if (request.getServletPath().equals("/player") || request.getServletPath().equals("/player/")) {
                response.setContentType("text/html; charset=UTF-8");
                try (var input = request.getServletContext().getResourceAsStream("/WEB-INF/player.html")) {
                    if (input == null) { error(response,503,"PLAYER_UNAVAILABLE"); return; }
                    input.transferTo(response.getOutputStream());
                }
                return;
            }
            String action = request.getPathInfo();
            if (!Set.of("/session","/state").contains(action == null ? "" : action)) { error(response,404,"PLAYER_REQUEST_DENIED"); return; }
            reply(response, service.read(cookie(request, SESSION_COOKIE), request.getHeader("X-Player-Connection"), action.equals("/session")));
        } catch (PlayerGatewayException failure) { error(response,failure.status(),failure.code()); }
        catch (RuntimeException failure) { error(response,503,"PLAYER_UNAVAILABLE"); }
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = request.getPathInfo();
            if (path == null || !Set.of("/join","/resume","/takeover","/command","/receipt","/proposal").contains(path)) { error(response,404,"PLAYER_REQUEST_DENIED"); return; }
            if (request.getQueryString() != null || request.getContentLengthLong() < 0 || request.getContentLengthLong() > MAX_BODY
                    || request.getContentType() == null || !request.getContentType().split(";",2)[0].equalsIgnoreCase("application/json")) { error(response,400,"PLAYER_INVALID_REQUEST"); return; }
            byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (body.length != request.getContentLengthLong() || body.length > MAX_BODY) { error(response,400,"PLAYER_INVALID_REQUEST"); return; }
            JsonObject input;
            try { input = CampaignJson.read(body); } catch (RuntimeException invalid) { error(response,400,"PLAYER_INVALID_REQUEST"); return; }
            PlayerGatewayService service = PlayerApplicationAccess.service(request.getServletContext());
            reply(response, service.post(cookie(request,SESSION_COOKIE), request.getHeader("X-CSRF-Token"), request.getHeader("X-Player-Connection"), path.substring(1), input, cookie(request,DEVICE_COOKIE)));
        } catch (PlayerGatewayException failure) { error(response,failure.status(),failure.code()); }
        catch (RuntimeException failure) { error(response,503,"PLAYER_UNAVAILABLE"); }
    }
    private static void reply(HttpServletResponse response, PlayerGatewayService.Reply reply) throws IOException {
        if (reply.sessionToken() != null) response.addHeader("Set-Cookie",SESSION_COOKIE + "=" + reply.sessionToken() + "; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=1800");
        if (reply.deviceToken() != null) response.addHeader("Set-Cookie",DEVICE_COOKIE + "=" + reply.deviceToken() + "; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=604800");
        response.setStatus(200); response.setContentType("application/json; charset=UTF-8"); response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(CampaignJson.canonical(reply.body()));
    }
    private static String cookie(HttpServletRequest request, String name) {
        String value = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) {
            if (value != null || !cookie.getValue().matches("[A-Za-z0-9_-]{43}")) throw new PlayerGatewayException(401,"PLAYER_SESSION_REQUIRED");
            value = cookie.getValue();
        }
        return value;
    }
    static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json; charset=UTF-8"); response.setHeader("Cache-Control","no-store");
        JsonObject body = new JsonObject(); body.addProperty("status","ERROR"); body.addProperty("code",code);
        response.getOutputStream().write(CampaignJson.canonical(body));
    }
}
