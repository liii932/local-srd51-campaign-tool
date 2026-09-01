package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterLifecycleCommandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HostCharacterLifecycleServletTest {
    private static final String REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String DIGEST = "a".repeat(64);

    @Test
    void updatedCommandReturnsTheNewRowVersion() throws Exception {
        HostCharacterLifecycleServlet servlet = servlet(
                new CharacterLifecycleCommandService.Result(
                        CharacterLifecycleCommandService.Status.UPDATED, 8L));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(200, response.status);
        assertEquals("{\"status\":\"UPDATED\",\"rowVersion\":8}",
                response.body.toString());
    }

    @Test
    void versionConflictReturnsTheCurrentServerVersion() throws Exception {
        HostCharacterLifecycleServlet servlet = servlet(
                new CharacterLifecycleCommandService.Result(
                        CharacterLifecycleCommandService.Status.VERSION_CONFLICT, 9L));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(409, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"VERSION_CONFLICT\","
                + "\"rowVersion\":9}", response.body.toString());
    }

    @Test
    void moduleMismatchReturnsOnlyStableFailureCategory() throws Exception {
        HostCharacterLifecycleServlet servlet = servlet(
                new CharacterLifecycleCommandService.Result(
                        CharacterLifecycleCommandService.Status.MODULE_HASH_MISMATCH, null));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(409, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}",
                response.body.toString());
    }

    @Test
    void databaseFailureDoesNotExposeProviderDetails() throws Exception {
        HostCharacterLifecycleServlet servlet = new HostCharacterLifecycleServlet(
                (a, b, c, d, e, f) -> { throw new SQLException("provider detail"); });
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(503, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}",
                response.body.toString());
    }

    private static HostCharacterLifecycleServlet servlet(
            CharacterLifecycleCommandService.Result result) {
        return new HostCharacterLifecycleServlet((key, version, action, value, id, digest) -> {
            assertEquals("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", key);
            assertEquals("7", version);
            assertEquals("RENAME", action);
            assertEquals("Aria", value);
            assertEquals(REQUEST_ID, id);
            assertEquals(DIGEST, digest);
            return result;
        });
    }

    private static HttpServletRequest request() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
        attributes.put(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, DIGEST);
        attributes.put(HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE, "7");
        Map<String, String> parameters = Map.of(
                "characterKey", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                "action", "RENAME",
                "value", "Aria");
        return proxy(HttpServletRequest.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "getAttribute" -> attributes.get(arguments[0]);
                    case "getParameter" -> parameters.get(arguments[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = 200;

        private HttpServletResponse proxy() {
            return HostCharacterLifecycleServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "getWriter" -> writer;
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
