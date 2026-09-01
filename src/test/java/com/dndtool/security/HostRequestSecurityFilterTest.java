package com.dndtool.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Verifies the ordered request checks that run after the loopback boundary. */
final class HostRequestSecurityFilterTest {
    private static final String TOKEN = "test-csrf-token";
    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private long currentEpoch;
    private final HostRequestSecurityFilter filter =
            new HostRequestSecurityFilter(() -> currentEpoch);

    @Test
    void getBootstrapsSessionAndExposesCsrfTokenOnlyAsRequestAttribute() throws Exception {
        SessionFixture session = new SessionFixture();
        RequestFixture request = request("GET", session, Map.of());
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
        String token = (String) request.attributes.get(HostRequestSecurityFilter.CSRF_TOKEN_REQUEST_ATTRIBUTE);
        assertNotNull(token);
        assertEquals(43, token.length());
        assertEquals(HostRequestSecurityFilter.INITIAL_VERSION,
                session.attributes.get(HostRequestSecurityFilter.HOST_STATE_EPOCH_SESSION_ATTRIBUTE));
        assertEquals("0", request.attributes.get(
                HostRequestSecurityFilter.HOST_STATE_EPOCH_REQUEST_ATTRIBUTE));
        assertEquals("0", request.attributes.get(
                HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE));
        assertEquals(30 * 60, session.maxInactiveInterval);
        assertTrue(response.headers.get("set-cookie").contains("SameSite=Strict"));
        assertFalse(response.headers.get("set-cookie").contains(TOKEN));
    }

    @Test
    void postPassesWhenAllRequestContractValuesMatch() throws Exception {
        SessionFixture session = sessionWithToken();
        RequestFixture request = request("POST", session, Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                "Sec-Fetch-Site", "same-origin",
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST));
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
        assertEquals(REQUEST_ID, request.attributes.get(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
        assertEquals(DIGEST, request.attributes.get(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE));
        assertEquals("0", request.attributes.get(
                HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE));
    }

    @Test
    void getRefreshesEpochAndReplacesAStaleSessionCookie() throws Exception {
        currentEpoch = 73L;
        SessionFixture session = new SessionFixture("new-session");
        RequestFixture request = request(
                "GET", session, Map.of(),
                new Cookie[] {new Cookie(
                        HostRequestSecurityFilter.SESSION_COOKIE_NAME, "old-session")});
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
        assertEquals("73", session.attributes.get(
                HostRequestSecurityFilter.HOST_STATE_EPOCH_SESSION_ATTRIBUTE));
        assertEquals("73", request.attributes.get(
                HostRequestSecurityFilter.HOST_STATE_EPOCH_REQUEST_ATTRIBUTE));
        assertTrue(response.headers.get("set-cookie").contains("new-session"));
        assertTrue(response.headers.get("set-cookie").contains("SameSite=Strict"));
    }

    @Test
    void refererIsUsedOnlyWhenOriginIsAbsent() throws Exception {
        SessionFixture session = sessionWithToken();
        RequestFixture request = request("POST", session, Map.of(
                "Referer", "http://127.0.0.1:8080/host/form",
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST));
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
    }

    @Test
    void crossSiteOrNullOriginIsRejected() throws Exception {
        assertRejected(postWithHeaders(Map.of(
                "Origin", "https://evil.example",
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST)));
        assertRejected(postWithHeaders(Map.of(
                "Origin", "null",
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST)));
    }

    @Test
    void crossSiteFetchMetadataIsRejected() throws Exception {
        assertRejected(postWithHeaders(Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                "Sec-Fetch-Site", "cross-site",
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST)));
    }

    @Test
    void missingSessionIsRejectedBeforeCsrfValidation() throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", null, Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN));

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
    }

    @Test
    void badCsrfTokenIsRejected() throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, "wrong",
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0"));

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    @Test
    void staleHostEpochIsRejectedBeforeRequestIdIsAccepted() throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "1",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0"));

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
    }

    @Test
    void epochThatMatchesOnlyTheOldSessionIsRejectedAgainstCurrentHostState()
            throws Exception {
        currentEpoch = 91L;
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST));

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        assertFalse(request.attributes.containsKey(
                HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }

    @Test
    void epochDatabaseFailureRejectsPostWithoutDispatching() throws Exception {
        HostRequestSecurityFilter unavailable = new HostRequestSecurityFilter(() -> {
            throw new SQLException("synthetic database detail");
        });
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0"));

        unavailable.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.status);
    }

    @Test
    void aggregateRowVersionIsForwardedInsteadOfComparedWithSessionPlaceholder()
            throws Exception {
        SessionFixture session = sessionWithToken();
        RequestFixture request = request("POST", session, Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "27",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, DIGEST));
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
        assertEquals("27", request.attributes.get(
                HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE));
    }

    @Test
    void malformedAggregateRowVersionIsRejected() throws Exception {
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "01"));
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    @Test
    void malformedRequestIdOrDigestIsRejected() throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();
        RequestFixture request = request("POST", sessionWithToken(), Map.of(
                "Origin", HostRequestSecurityFilter.EXPECTED_ORIGIN,
                HostRequestSecurityFilter.CSRF_HEADER, TOKEN,
                HostRequestSecurityFilter.EPOCH_HEADER, "0",
                HostRequestSecurityFilter.ROW_VERSION_HEADER, "0",
                HostRequestSecurityFilter.REQUEST_ID_HEADER, "not-a-uuid",
                HostRequestSecurityFilter.REQUEST_DIGEST_HEADER, "not-a-sha256"));

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    }

    private static RequestFixture postWithHeaders(Map<String, String> headers) {
        return request("POST", sessionWithToken(), headers);
    }

    private static SessionFixture sessionWithToken() {
        SessionFixture session = new SessionFixture();
        session.attributes.put(HostRequestSecurityFilter.CSRF_TOKEN_SESSION_ATTRIBUTE, TOKEN);
        session.attributes.put(HostRequestSecurityFilter.HOST_STATE_EPOCH_SESSION_ATTRIBUTE, "0");
        session.attributes.put(HostRequestSecurityFilter.ROW_VERSION_SESSION_ATTRIBUTE, "0");
        return session;
    }

    private static RequestFixture request(
            String method, SessionFixture session, Map<String, String> additionalHeaders) {
        return request(method, session, additionalHeaders, null);
    }

    private static RequestFixture request(
            String method,
            SessionFixture session,
            Map<String, String> additionalHeaders,
            Cookie[] cookies) {
        Map<String, String> headers = new HashMap<>();
        additionalHeaders.forEach((name, value) -> headers.put(name.toLowerCase(Locale.ROOT), value));
        return new RequestFixture(method, session, headers, new HashMap<>(), cookies);
    }

    private static void assertRejected(RequestFixture request) throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        new HostRequestSecurityFilter(() -> 0L)
                .doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status);
    }

    private record RequestFixture(
            String method, SessionFixture session, Map<String, String> headers,
            Map<String, Object> attributes, Cookie[] cookies) {
        private RequestFixture(String method, SessionFixture session, Map<String, String> headers) {
            this(method, session, headers, new HashMap<>(), null);
        }

        HttpServletRequest proxy() {
            InvocationHandler handler = (proxy, invoked, args) -> switch (invoked.getName()) {
                case "getMethod" -> method;
                case "getHeader" -> headers.get(((String) args[0]).toLowerCase(Locale.ROOT));
                case "getSession" -> session == null ? null : session.proxy();
                case "getCookies" -> cookies;
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> attributes.get(args[0]);
                default -> defaultValue(invoked);
            };
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    handler);
        }
    }

    private static final class SessionFixture implements InvocationHandler {
        private final Map<String, Object> attributes = new HashMap<>();
        private final String id;
        private int maxInactiveInterval;

        private SessionFixture() {
            this("test-session");
        }

        private SessionFixture(String id) {
            this.id = id;
        }

        HttpSession proxy() {
            return (HttpSession) Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class<?>[] {HttpSession.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAttribute" -> attributes.get(args[0]);
                case "getId" -> id;
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "setMaxInactiveInterval" -> {
                    maxInactiveInterval = (Integer) args[0];
                    yield null;
                }
                case "getMaxInactiveInterval" -> maxInactiveInterval;
                default -> defaultValue(method);
            };
        }
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final Map<String, String> headers = new HashMap<>();
        private int status = HttpServletResponse.SC_OK;

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "sendError", "setStatus" -> {
                    status = (Integer) args[0];
                    yield null;
                }
                case "setHeader", "addHeader" -> {
                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT), (String) args[1]);
                    yield null;
                }
                default -> defaultValue(method);
            };
        }
    }

    private static final class ChainFixture implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }
}
