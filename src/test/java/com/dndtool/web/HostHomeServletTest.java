package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.HostOverviewService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HostHomeServletTest {
    @Test
    void forwardsEmptyOverviewWithNoStoreResponse() throws Exception {
        Fixture fixture = new Fixture();
        HostHomeServlet servlet = new HostHomeServlet(() ->
                new HostOverviewService.Result(
                        HostOverviewService.Status.EMPTY, null));

        servlet.doGet(fixture.request(), fixture.response());

        assertEquals("EMPTY", fixture.attributes.get(HostHomeServlet.OVERVIEW_STATUS_ATTRIBUTE));
        assertEquals("no-store", fixture.headers.get("Cache-Control"));
        assertEquals("/WEB-INF/views/host.jsp", fixture.dispatchPath);
        assertTrue(fixture.forwarded);
    }

    @Test
    void keepsHostShellReachableWithStableDatabaseFailureCategory() throws Exception {
        Fixture fixture = new Fixture();
        HostHomeServlet servlet = new HostHomeServlet(() -> {
            throw new SQLException("synthetic overview read failure");
        });

        servlet.doGet(fixture.request(), fixture.response());

        assertEquals("DATABASE_UNAVAILABLE",
                fixture.attributes.get(HostHomeServlet.OVERVIEW_STATUS_ATTRIBUTE));
        assertTrue(fixture.forwarded);
    }

    @Test
    void keepsHostShellReachableWithStableModuleMismatchCategory() throws Exception {
        Fixture fixture = new Fixture();
        HostHomeServlet servlet = new HostHomeServlet(() ->
                new HostOverviewService.Result(
                        HostOverviewService.Status.MODULE_HASH_MISMATCH, null));

        servlet.doGet(fixture.request(), fixture.response());

        assertEquals("MODULE_HASH_MISMATCH",
                fixture.attributes.get(HostHomeServlet.OVERVIEW_STATUS_ATTRIBUTE));
        assertEquals(false, fixture.attributes.containsKey(HostHomeServlet.OVERVIEW_ATTRIBUTE));
        assertEquals("no-store", fixture.headers.get("Cache-Control"));
        assertTrue(fixture.forwarded);
    }

    @Test
    void reloadsAfterTransientDatabaseFailureWithoutCachingTheFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        HostHomeServlet servlet = new HostHomeServlet(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new SQLException("synthetic database restart window");
            }
            return new HostOverviewService.Result(
                    HostOverviewService.Status.EMPTY, null);
        });
        Fixture unavailable = new Fixture();
        Fixture recovered = new Fixture();

        servlet.doGet(unavailable.request(), unavailable.response());
        servlet.doGet(recovered.request(), recovered.response());

        assertEquals("DATABASE_UNAVAILABLE",
                unavailable.attributes.get(HostHomeServlet.OVERVIEW_STATUS_ATTRIBUTE));
        assertEquals("EMPTY",
                recovered.attributes.get(HostHomeServlet.OVERVIEW_STATUS_ATTRIBUTE));
        assertEquals(2, attempts.get());
        assertTrue(unavailable.forwarded);
        assertTrue(recovered.forwarded);
    }

    private static final class Fixture {
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private String dispatchPath;
        private boolean forwarded;

        private HttpServletRequest request() {
            return proxy(HttpServletRequest.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setAttribute" -> {
                            attributes.put((String) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "getRequestDispatcher" -> {
                            dispatchPath = (String) arguments[0];
                            yield dispatcher();
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private HttpServletResponse response() {
            return proxy(HttpServletResponse.class, (ignored, method, arguments) -> {
                if ("setHeader".equals(method.getName())) {
                    headers.put((String) arguments[0], (String) arguments[1]);
                }
                return defaultValue(method.getReturnType());
            });
        }

        private RequestDispatcher dispatcher() {
            return proxy(RequestDispatcher.class, (ignored, method, arguments) -> {
                if ("forward".equals(method.getName())) forwarded = true;
                return defaultValue(method.getReturnType());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
