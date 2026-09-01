package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.HostRulesService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HostRulesPageServletTest {
    @Test
    void routeLoadsTheSameServiceProjectionAndForwardsUnderWebInf() throws Exception {
        WebServlet route = HostRulesPageServlet.class.getAnnotation(WebServlet.class);
        assertEquals(List.of("/host/rules"), List.of(route.urlPatterns()));
        HostRulesService.CatalogView catalog = new HostRulesService.CatalogView(
                "dnd5e2014_srd51_se_v1", "1", 1, "力量",
                HostRulesService.RuleType.FIELD, List.of());
        HostRulesPageServlet servlet = new HostRulesPageServlet((query, type) -> {
            assertEquals("力量", query);
            assertEquals("FIELD", type);
            return new HostRulesService.Result(HostRulesService.Status.READY, catalog);
        });
        Fixture fixture = new Fixture(Map.of("q", "力量", "type", "FIELD"));

        servlet.doGet(fixture.request(), fixture.response());

        assertEquals("READY", fixture.attributes.get(HostRulesPageServlet.STATUS_ATTRIBUTE));
        assertEquals(catalog, fixture.attributes.get(HostRulesPageServlet.CATALOG_ATTRIBUTE));
        assertEquals("no-store", fixture.headers.get("Cache-Control"));
        assertEquals("/WEB-INF/views/host-rules.jsp", fixture.dispatchPath);
        assertTrue(fixture.forwarded);
    }

    @Test
    void databaseFailureKeepsTheHostPageReachableWithAStableCategory() throws Exception {
        HostRulesPageServlet servlet = new HostRulesPageServlet((query, type) -> {
            throw new SQLException("secret provider detail");
        });
        Fixture fixture = new Fixture(Map.of());

        servlet.doGet(fixture.request(), fixture.response());

        assertEquals("DATABASE_UNAVAILABLE",
                fixture.attributes.get(HostRulesPageServlet.STATUS_ATTRIBUTE));
        assertTrue(fixture.forwarded);
    }

    private static final class Fixture {
        private final Map<String, String> parameters;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private String dispatchPath;
        private boolean forwarded;

        private Fixture(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        private HttpServletRequest request() {
            return proxy(HttpServletRequest.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "getParameter" -> parameters.get((String) arguments[0]);
                        case "setAttribute" -> {
                            attributes.put((String) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "getRequestDispatcher" -> {
                            dispatchPath = (String) arguments[0];
                            yield dispatcher();
                        }
                        default -> defaultValue(method);
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
