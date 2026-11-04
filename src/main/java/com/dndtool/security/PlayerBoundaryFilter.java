package com.dndtool.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/** Global first boundary: the TLS connector can serve only the finite player surface. */
public final class PlayerBoundaryFilter implements Filter {
    private PlayerListenerConfiguration configuration;
    private static final Set<String> READS = Set.of("/player", "/player/", "/player/assets/player.js", "/player/assets/player.css",
            "/api/player/session", "/api/player/state");
    private static final Set<String> WRITES = Set.of("/api/player/join", "/api/player/resume", "/api/player/takeover",
            "/api/player/command", "/api/player/receipt", "/api/player/proposal");

    public PlayerBoundaryFilter() { }
    public PlayerBoundaryFilter(PlayerListenerConfiguration configuration) { this.configuration = configuration; }
    @Override public void init(FilterConfig config) {
        if (configuration == null) {
            try { configuration = PlayerListenerConfiguration.from(config.getServletContext()); }
            catch (IllegalArgumentException invalid) { configuration = null; config.getServletContext().log("Player HTTPS configuration is invalid; the player surface is disabled."); }
        }
    }

    @Override public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain) throws IOException, ServletException {
        if (!(input instanceof HttpServletRequest request) || !(output instanceof HttpServletResponse response)) throw new ServletException("HTTP required");
        String uri = request.getRequestURI(), context = request.getContextPath();
        String path = uri == null || context == null || !uri.startsWith(context) ? "" : uri.substring(context.length());
        boolean playerPath = path.equals("/player") || path.startsWith("/player/") || path.startsWith("/api/player/");
        boolean hostConnector = request.getLocalPort() == 8080 && "http".equals(request.getScheme());
        if (hostConnector) {
            if (playerPath) deny(response, 404); else chain.doFilter(input, output);
            return;
        }
        if (configuration == null || !allowedBoundary(request, configuration) || !playerPath || request.getQueryString() != null) { deny(response, 404); return; }
        headers(response);
        String method = request.getMethod();
        if (("GET".equals(method) || "HEAD".equals(method)) && READS.contains(path)) {
            String origin = request.getHeader("Origin"), site = request.getHeader("Sec-Fetch-Site");
            boolean publicShellNavigation = origin == null && Set.of("/player", "/player/").contains(path)
                    && "navigate".equals(request.getHeader("Sec-Fetch-Mode"))
                    && "document".equals(request.getHeader("Sec-Fetch-Dest"));
            if (origin != null && !configuration.origin().equals(origin)
                    || !publicShellNavigation && site != null && !Set.of("same-origin", "none").contains(site)) { deny(response, 403); return; }
        } else if ("POST".equals(method) && WRITES.contains(path)) {
            if (!configuration.origin().equals(request.getHeader("Origin"))
                    || !"same-origin".equals(request.getHeader("Sec-Fetch-Site"))
                    || !Set.of("cors", "same-origin").contains(Objects.toString(request.getHeader("Sec-Fetch-Mode"), ""))
                    || !"empty".equals(request.getHeader("Sec-Fetch-Dest"))) { deny(response, 403); return; }
        } else { deny(response, 404); return; }
        chain.doFilter(input, output);
    }

    static boolean allowedBoundary(HttpServletRequest request, PlayerListenerConfiguration config) {
        if (!"https".equals(request.getScheme()) || !request.isSecure() || request.getLocalPort() != 8443
                || !config.address().equals(request.getLocalAddr()) || !config.authority().equals(request.getHeader("Host"))) return false;
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return false;
        while (names.hasMoreElements()) {
            String name = names.nextElement().toLowerCase(Locale.ROOT);
            if (name.equals("forwarded") || name.startsWith("x-forwarded-") || name.equals("x-real-ip")) return false;
        }
        Enumeration<String> hosts = request.getHeaders("Host");
        if (hosts == null || !hosts.hasMoreElements()) return false;
        hosts.nextElement(); return !hosts.hasMoreElements();
    }

    private static void headers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'; object-src 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    private static void deny(HttpServletResponse response, int status) throws IOException {
        headers(response);
        response.setStatus(status); response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"status\":\"ERROR\",\"code\":\"PLAYER_REQUEST_DENIED\"}");
    }
}
