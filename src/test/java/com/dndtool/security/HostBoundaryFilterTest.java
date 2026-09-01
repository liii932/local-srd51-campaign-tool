package com.dndtool.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests the boundary without a servlet container by using small dynamic proxies for the Servlet
 * API. This keeps the security contract executable without adding a mocking dependency.
 */
final class HostBoundaryFilterTest {
    private final HostBoundaryFilter filter = new HostBoundaryFilter();

    @Test
    void exactBoundaryIsAllowedAndReceivesSecurityHeaders() throws Exception {
        RequestFixture request = request("GET", "127.0.0.1", 8080, "http", "127.0.0.1:8080", Map.of());
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertTrue(chain.called);
        assertEquals(200, response.status);
        assertEquals("no-store", response.headers.get("cache-control"));
        assertEquals("nosniff", response.headers.get("x-content-type-options"));
        assertEquals("no-referrer", response.headers.get("referrer-policy"));
        assertEquals(
                "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'",
                response.headers.get("content-security-policy"));
    }

    @Test
    void nonLoopbackRemoteAddressIsRejectedEvenWithForwardedHeader() throws Exception {
        assertRejected(request(
                "GET",
                "192.168.0.102",
                8080,
                "http",
                "127.0.0.1:8080",
                Map.of("X-Forwarded-For", "127.0.0.1")));
    }

    @Test
    void ipv6LoopbackIsRejected() throws Exception {
        assertRejected(request("GET", "::1", 8080, "http", "127.0.0.1:8080", Map.of()));
    }

    @Test
    void wrongPortIsRejected() throws Exception {
        assertRejected(request("GET", "127.0.0.1", 8443, "http", "127.0.0.1:8080", Map.of()));
    }

    @Test
    void httpsSchemeIsRejected() throws Exception {
        assertRejected(request("GET", "127.0.0.1", 8080, "https", "127.0.0.1:8080", Map.of()));
    }

    @Test
    void localhostHostHeaderIsRejected() throws Exception {
        assertRejected(request("GET", "127.0.0.1", 8080, "http", "localhost:8080", Map.of()));
    }

    @Test
    void missingHostHeaderIsRejected() throws Exception {
        assertRejected(request("GET", "127.0.0.1", 8080, "http", null, Map.of()));
    }

    @Test
    void unsupportedMethodIsRejectedAfterBoundary() throws Exception {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(
                request("PUT", "127.0.0.1", 8080, "http", "127.0.0.1:8080", Map.of()).proxy(),
                response.proxy(),
                chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_METHOD_NOT_ALLOWED, response.status);
        assertEquals("GET, HEAD, POST", response.headers.get("allow"));
    }

    private void assertRejected(RequestFixture request) throws IOException, ServletException {
        ResponseFixture response = new ResponseFixture();
        ChainFixture chain = new ChainFixture();

        filter.doFilter(request.proxy(), response.proxy(), chain);

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.status);
        assertTrue(response.headers.isEmpty());
    }

    private static RequestFixture request(
            String method,
            String remoteAddress,
            int localPort,
            String scheme,
            String host,
            Map<String, String> additionalHeaders) {
        Map<String, String> headers = new HashMap<>();
        if (host != null) {
            headers.put("host", host);
        }
        additionalHeaders.forEach(
                (name, value) -> headers.put(name.toLowerCase(Locale.ROOT), value));
        return new RequestFixture(method, remoteAddress, localPort, scheme, headers);
    }

    private record RequestFixture(
            String method,
            String remoteAddress,
            int localPort,
            String scheme,
            Map<String, String> headers) {
        HttpServletRequest proxy() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getMethod" -> this.method;
                case "getRemoteAddr" -> remoteAddress;
                case "getLocalPort" -> localPort;
                case "getScheme" -> scheme;
                case "getHeader" -> headers.get(((String) args[0]).toLowerCase(Locale.ROOT));
                default -> defaultValue(method);
            };
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    handler);
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
                case "reset" -> {
                    status = HttpServletResponse.SC_OK;
                    headers.clear();
                    yield null;
                }
                case "sendError", "setStatus" -> {
                    status = (Integer) args[0];
                    yield null;
                }
                case "setHeader", "addHeader" -> {
                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT), (String) args[1]);
                    yield null;
                }
                case "getStatus" -> status;
                case "getHeader" -> headers.get(((String) args[0]).toLowerCase(Locale.ROOT));
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

    // Unused Servlet API methods return safe defaults so each test sets only boundary inputs.
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
