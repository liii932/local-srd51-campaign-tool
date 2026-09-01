package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.persistence.DatabaseSchemaStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Ensures diagnostic responses expose only stable categories, never hash comparison details. */
final class HostDatabaseDiagnosticServletTest {
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
        ResponseFixture response = new ResponseFixture();
        HostDatabaseDiagnosticServlet.writeStatus(
                response.proxy(),
                new DatabaseSchemaStatus(
                        DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE, 0, null, null));

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_SCHEMA_UNAVAILABLE\"}",
                response.body.toString());
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
