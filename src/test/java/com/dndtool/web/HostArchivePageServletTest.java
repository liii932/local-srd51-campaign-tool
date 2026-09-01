package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HostArchivePageServletTest {
    @Test
    void forwardsProtectedArchiveViewWithNoStoreResponse() throws Exception {
        Fixture fixture = new Fixture();

        new HostArchivePageServlet().doGet(fixture.request(), fixture.response());

        assertEquals("no-store", fixture.headers.get("Cache-Control"));
        assertEquals("/WEB-INF/views/host-archive.jsp", fixture.dispatchPath);
        assertTrue(fixture.forwarded);
    }

    private static final class Fixture {
        private final Map<String, String> headers = new HashMap<>();
        private String dispatchPath;
        private boolean forwarded;

        private HttpServletRequest request() {
            return proxy(HttpServletRequest.class, (ignored, method, arguments) -> {
                if ("getRequestDispatcher".equals(method.getName())) {
                    dispatchPath = (String) arguments[0];
                    return dispatcher();
                }
                return defaultValue(method);
            });
        }

        private HttpServletResponse response() {
            return proxy(HttpServletResponse.class, (ignored, method, arguments) -> {
                if ("setHeader".equals(method.getName())) {
                    headers.put((String) arguments[0], (String) arguments[1]);
                }
                return defaultValue(method);
            });
        }

        private RequestDispatcher dispatcher() {
            return proxy(RequestDispatcher.class, (ignored, method, arguments) -> {
                if ("forward".equals(method.getName())) forwarded = true;
                return defaultValue(method);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
