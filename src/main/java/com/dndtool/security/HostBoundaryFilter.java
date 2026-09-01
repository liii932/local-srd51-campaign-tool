package com.dndtool.security;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Keeps the host-only surface reachable only through the fixed loopback HTTP origin.
 *
 * <p>Every value is taken from the servlet request that Tomcat received. Forwarded headers are
 * deliberately ignored because they can describe an upstream proxy rather than the TCP peer.
 */
public final class HostBoundaryFilter implements Filter {
    static final String ALLOWED_REMOTE_ADDRESS = "127.0.0.1";
    static final int ALLOWED_LOCAL_PORT = 8080;
    static final String ALLOWED_SCHEME = "http";
    static final String ALLOWED_HOST = "127.0.0.1:8080";

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            throw new ServletException("Host boundary requires HTTP");
        }

        if (!isAllowed(httpRequest)) {
            httpResponse.reset();
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        applySecurityHeaders(httpResponse);
        if (!isAllowedMethod(httpRequest.getMethod())) {
            httpResponse.setHeader("Allow", "GET, HEAD, POST");
            httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Returns whether the request matches the complete, non-negotiable local boundary. */
    static boolean isAllowed(HttpServletRequest request) {
        return ALLOWED_REMOTE_ADDRESS.equals(request.getRemoteAddr())
                && ALLOWED_LOCAL_PORT == request.getLocalPort()
                && ALLOWED_SCHEME.equals(request.getScheme())
                && ALLOWED_HOST.equals(request.getHeader("Host"));
    }

    /** Host routes are read-only for GET/HEAD and reserve POST for state-changing commands. */
    static boolean isAllowedMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "POST".equals(method);
    }

    /** Applies headers required for host pages and APIs after the boundary has passed. */
    private static void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
