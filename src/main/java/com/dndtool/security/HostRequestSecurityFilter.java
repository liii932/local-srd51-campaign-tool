package com.dndtool.security;

import com.dndtool.persistence.HostStateEpochRepository;
import com.dndtool.persistence.JdbcHostStateEpochRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;

/**
 * Enforces request-level protections after {@link HostBoundaryFilter} has accepted the origin.
 *
 * <p>GET and HEAD bootstrap a short-lived host session so the page can render a CSRF token into a
 * future form. POST validates the source, Fetch Metadata, session token, state versions, request
 * identifier, and digest syntax in the same order as the design. Durable idempotency and the
 * digest of a canonical business payload are revalidated by the reached Service/DAO transaction.
 */
public final class HostRequestSecurityFilter implements Filter {
    public static final String SESSION_COOKIE_NAME = "DNDHOSTSESSION";
    public static final String CSRF_TOKEN_REQUEST_ATTRIBUTE = "dndtool.csrfToken";
    public static final String HOST_STATE_EPOCH_REQUEST_ATTRIBUTE = "dndtool.hostStateEpoch";
    public static final String ROW_VERSION_REQUEST_ATTRIBUTE = "dndtool.rowVersion";
    public static final String CSRF_TOKEN_SESSION_ATTRIBUTE = "dndtool.csrfToken";
    public static final String HOST_STATE_EPOCH_SESSION_ATTRIBUTE = "dndtool.hostStateEpoch";
    public static final String ROW_VERSION_SESSION_ATTRIBUTE = "dndtool.rowVersion";
    public static final String REQUEST_ID_ATTRIBUTE = "dndtool.requestId";
    public static final String REQUEST_DIGEST_ATTRIBUTE = "dndtool.requestDigest";

    static final String EXPECTED_ORIGIN = "http://127.0.0.1:8080";
    static final String CSRF_HEADER = "X-CSRF-Token";
    static final String CSRF_PARAMETER = "_csrf";
    static final String EPOCH_HEADER = "X-Host-State-Epoch";
    static final String ROW_VERSION_HEADER = "X-Object-Row-Version";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_DIGEST_HEADER = "X-Request-Digest";
    static final String INITIAL_VERSION = "0";

    private static final int SESSION_TIMEOUT_SECONDS = 30 * 60;
    private static final int CSRF_TOKEN_BYTES = 32;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";
    private static final SecureRandom RANDOM = new SecureRandom();

    private transient HostStateEpochRepository hostStateEpochRepository;

    public HostRequestSecurityFilter() {
        // Required by the Servlet container.
    }

    HostRequestSecurityFilter(HostStateEpochRepository hostStateEpochRepository) {
        this.hostStateEpochRepository = hostStateEpochRepository;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (hostStateEpochRepository != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            hostStateEpochRepository = new JdbcHostStateEpochRepository(dataSource);
        } catch (NamingException exception) {
            throw new ServletException("Host state epoch validation is unavailable");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            throw new ServletException("Host request security requires HTTP");
        }
        if (hostStateEpochRepository == null) {
            throw new ServletException("Host state epoch validation is not initialized");
        }

        SameSiteResponseWrapper responseWrapper = new SameSiteResponseWrapper(httpResponse);
        if ("GET".equals(httpRequest.getMethod()) || "HEAD".equals(httpRequest.getMethod())) {
            HttpSession session = httpRequest.getSession(true);
            initializeSession(session);
            refreshEpochForRead(session);
            responseWrapper.ensureSessionCookie(httpRequest, session);
            httpRequest.setAttribute(
                    CSRF_TOKEN_REQUEST_ATTRIBUTE,
                    session.getAttribute(CSRF_TOKEN_SESSION_ATTRIBUTE));
            httpRequest.setAttribute(
                    HOST_STATE_EPOCH_REQUEST_ATTRIBUTE,
                    session.getAttribute(HOST_STATE_EPOCH_SESSION_ATTRIBUTE));
            httpRequest.setAttribute(
                    ROW_VERSION_REQUEST_ATTRIBUTE,
                    session.getAttribute(ROW_VERSION_SESSION_ATTRIBUTE));
            chain.doFilter(httpRequest, responseWrapper);
            return;
        }

        // HostBoundaryFilter handles unsupported methods before this filter is reached.
        if (!hasAllowedWriteSource(httpRequest)) {
            responseWrapper.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (isCrossSiteFetch(httpRequest)) {
            responseWrapper.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            responseWrapper.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        initializeSession(session);
        if (!hasMatchingCsrfToken(httpRequest, session)) {
            responseWrapper.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!hasMatchingVersion(httpRequest, EPOCH_HEADER, HOST_STATE_EPOCH_SESSION_ATTRIBUTE)) {
            responseWrapper.sendError(HttpServletResponse.SC_CONFLICT);
            return;
        }
        if (!matchesAuthoritativeEpoch(session, responseWrapper)) {
            return;
        }

        // The host epoch is session-wide, while a row version belongs to the target aggregate.
        // Its actual optimistic-lock comparison therefore happens inside the write transaction.
        String rowVersion = httpRequest.getHeader(ROW_VERSION_HEADER);
        if (!isNonNegativeDecimal(rowVersion)) {
            responseWrapper.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        String requestDigest = httpRequest.getHeader(REQUEST_DIGEST_HEADER);
        if (!isCanonicalUuid(requestId) || !isSha256Hex(requestDigest)) {
            responseWrapper.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Business services recompute the digest and persist both values in their transaction.
        httpRequest.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        httpRequest.setAttribute(REQUEST_DIGEST_ATTRIBUTE, requestDigest);
        httpRequest.setAttribute(ROW_VERSION_REQUEST_ATTRIBUTE, rowVersion);
        chain.doFilter(httpRequest, responseWrapper);
    }

    private void refreshEpochForRead(HttpSession session) {
        try {
            session.setAttribute(
                    HOST_STATE_EPOCH_SESSION_ATTRIBUTE,
                    Long.toString(currentActiveEpoch()));
        } catch (java.sql.SQLException ignored) {
            // Keep the host shell and database diagnostics reachable. POST still fails closed.
        }
    }

    private boolean matchesAuthoritativeEpoch(
            HttpSession session, HttpServletResponse response) throws IOException {
        final String current;
        try {
            current = Long.toString(currentActiveEpoch());
        } catch (java.sql.SQLException exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return false;
        }
        if (!current.equals(session.getAttribute(HOST_STATE_EPOCH_SESSION_ATTRIBUTE))) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return false;
        }
        return true;
    }

    private long currentActiveEpoch() throws java.sql.SQLException {
        long epoch = hostStateEpochRepository.currentActiveEpoch();
        if (epoch < 0) throw new java.sql.SQLException("Host state epoch is negative");
        return epoch;
    }

    /** Accepts Origin exactly, or falls back to a Referer with the exact expected origin. */
    static boolean hasAllowedWriteSource(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return EXPECTED_ORIGIN.equals(origin);
        }
        return hasExpectedRefererOrigin(request.getHeader("Referer"));
    }

    /** Rejects only an explicitly cross-site Fetch Metadata signal; other checks remain required. */
    static boolean isCrossSiteFetch(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        return fetchSite != null && "cross-site".equalsIgnoreCase(fetchSite.trim());
    }

    private static boolean hasExpectedRefererOrigin(String referer) {
        if (referer == null) {
            return false;
        }
        try {
            URI uri = new URI(referer);
            return uri.getUserInfo() == null
                    && "http".equalsIgnoreCase(uri.getScheme())
                    && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() == 8080;
        } catch (URISyntaxException | NullPointerException ignored) {
            return false;
        }
    }

    private static boolean hasMatchingCsrfToken(HttpServletRequest request, HttpSession session) {
        Object stored = session.getAttribute(CSRF_TOKEN_SESSION_ATTRIBUTE);
        String supplied = request.getHeader(CSRF_HEADER);
        if (supplied == null) {
            supplied = request.getParameter(CSRF_PARAMETER);
        }
        if (!(stored instanceof String expected) || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                supplied.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static boolean hasMatchingVersion(
            HttpServletRequest request, String headerName, String sessionAttribute) {
        String supplied = request.getHeader(headerName);
        Object current = request.getSession(false).getAttribute(sessionAttribute);
        return current instanceof String expected
                && isNonNegativeDecimal(supplied)
                && expected.equals(supplied);
    }

    private static void initializeSession(HttpSession session) {
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        if (session.getAttribute(CSRF_TOKEN_SESSION_ATTRIBUTE) == null) {
            byte[] token = new byte[CSRF_TOKEN_BYTES];
            RANDOM.nextBytes(token);
            session.setAttribute(
                    CSRF_TOKEN_SESSION_ATTRIBUTE,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(token));
        }
        if (session.getAttribute(HOST_STATE_EPOCH_SESSION_ATTRIBUTE) == null) {
            session.setAttribute(HOST_STATE_EPOCH_SESSION_ATTRIBUTE, INITIAL_VERSION);
        }
        if (session.getAttribute(ROW_VERSION_SESSION_ATTRIBUTE) == null) {
            session.setAttribute(ROW_VERSION_SESSION_ATTRIBUTE, INITIAL_VERSION);
        }
    }

    private static boolean isNonNegativeDecimal(String value) {
        return value != null && value.matches("(?:0|[1-9][0-9]*)");
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isSha256Hex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    /** Adds SameSite without putting a CSRF token into a cookie. */
    private static final class SameSiteResponseWrapper extends HttpServletResponseWrapper {
        private SameSiteResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        private void ensureSessionCookie(HttpServletRequest request, HttpSession session) {
            if (hasCurrentSessionCookie(request, session.getId())) {
                return;
            }
            // getSession() can make Tomcat add its cookie through the original response object,
            // before a filter wrapper sees it. Replacing the header here guarantees SameSite.
            setHeader(
                    "Set-Cookie",
                    SESSION_COOKIE_NAME + "=" + session.getId()
                            + "; Path=/; HttpOnly; SameSite=Strict");
        }

        private static boolean hasCurrentSessionCookie(
                HttpServletRequest request, String sessionId) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return false;
            }
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE_NAME.equals(cookie.getName())
                        && sessionId != null && cookie.getValue() != null
                        && MessageDigest.isEqual(
                                sessionId.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                                cookie.getValue().getBytes(
                                        java.nio.charset.StandardCharsets.US_ASCII))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void addCookie(Cookie cookie) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                // Tomcat's Cookie processor does not serialize the non-standard SameSite
                // attribute from Cookie#setAttribute, so write this session cookie explicitly.
                StringBuilder header = new StringBuilder(cookie.getName())
                        .append('=').append(cookie.getValue() == null ? "" : cookie.getValue())
                        .append("; Path=/; HttpOnly; SameSite=Strict");
                if (cookie.getMaxAge() >= 0) {
                    header.append("; Max-Age=").append(cookie.getMaxAge());
                }
                super.addHeader("Set-Cookie", header.toString());
                return;
            }
            super.addCookie(cookie);
        }
    }
}
