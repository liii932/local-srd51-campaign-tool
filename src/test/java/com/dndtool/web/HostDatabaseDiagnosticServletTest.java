package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.persistence.DatabaseSchemaStatus;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Ensures diagnostic responses expose only stable categories, never hash comparison details. */
final class HostDatabaseDiagnosticServletTest {
    @Test
    void eachGetRefreshesTheDiagnosticAndPublishedContextStatus() throws Exception {
        assertEquals(List.of("/api/host/diagnostics/database"), List.of(
                HostDatabaseDiagnosticServlet.class.getAnnotation(WebServlet.class).urlPatterns()));
        List<DatabaseSchemaStatus> results = List.of(
                new DatabaseSchemaStatus(DatabaseSchemaStatus.State.READY, 19, "approved.sql", "abc"),
                DatabaseSchemaStatus.failure(DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE));
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> attributes = new HashMap<>();
        ServletContext context = (ServletContext) Proxy.newProxyInstance(
                ServletContext.class.getClassLoader(), new Class<?>[] {ServletContext.class},
                (ignored, method, arguments) -> {
                    if (method.getName().equals("setAttribute")) {
                        attributes.put((String) arguments[0], arguments[1]);
                        return null;
                    }
                    throw new AssertionError("Unexpected context call: " + method.getName());
                });
        ServletConfig config = (ServletConfig) Proxy.newProxyInstance(
                ServletConfig.class.getClassLoader(), new Class<?>[] {ServletConfig.class},
                (ignored, method, arguments) -> method.getName().equals("getServletContext") ? context : null);
        HostDatabaseDiagnosticServlet servlet = new HostDatabaseDiagnosticServlet(
                () -> results.get(calls.getAndIncrement()));
        servlet.init(config);
        assertEquals(0, calls.get());
        ResponseFixture first = new ResponseFixture();
        ResponseFixture second = new ResponseFixture();

        servlet.doGet(null, first.proxy());
        assertEquals(results.getFirst(), attributes.get("com.dndtool.persistence.databaseSchemaStatus"));
        servlet.doGet(null, second.proxy());

        assertEquals(2, calls.get());
        assertEquals(results.getLast(), attributes.get("com.dndtool.persistence.databaseSchemaStatus"));
        assertEquals(HttpServletResponse.SC_OK, first.status);
        assertEquals("{\"status\":\"OK\",\"schemaVersion\":19,\"scriptName\":\"approved.sql\",\"scriptSha256\":\"abc\"}",
                first.body.toString());
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, second.status);
    }

    @Test
    void moduleMismatchUsesGenericConflictResponse() throws Exception {
        ResponseFixture response = new ResponseFixture();

        HostDatabaseDiagnosticServlet.writeStatus(
                response.proxy(),
                new DatabaseSchemaStatus(
                        DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH, 0, null, null));

        assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}",
                response.body.toString());
    }

    @Test
    void otherFailuresRemainIndistinguishable() throws Exception {
        for (DatabaseSchemaStatus.State state : DatabaseSchemaStatus.State.values()) {
            if (state == DatabaseSchemaStatus.State.READY
                    || state == DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH) continue;
            ResponseFixture response = new ResponseFixture();
            HostDatabaseDiagnosticServlet.writeStatus(response.proxy(),
                    new DatabaseSchemaStatus(state, 0, "private script", "private hash"));

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.status);
            assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_SCHEMA_UNAVAILABLE\"}",
                    response.body.toString());
        }
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;

        private HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "getWriter" -> writer;
                default -> defaultValue(method);
            };
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
}
