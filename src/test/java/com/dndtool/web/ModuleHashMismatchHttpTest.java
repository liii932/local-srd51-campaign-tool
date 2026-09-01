package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.HostCommandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Locks the protected host-command HTTP boundary to one non-diagnostic module failure response. */
final class ModuleHashMismatchHttpTest {
    private static final String ERROR =
            "{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}";

    @Test
    void checkPositionEncounterAndExportReturnOnlyStableModuleMismatch() throws Exception {
        assertMismatch(response -> new HostCheckServlet(request ->
                new HostCommandService.CheckResult(
                        HostCommandService.Status.MODULE_HASH_MISMATCH, null))
                .doPost(request(), response));
        assertMismatch(response -> new HostPositionServlet(request ->
                new HostCommandService.PositionResult(
                        HostCommandService.Status.MODULE_HASH_MISMATCH, null))
                .doPost(request(), response));
        assertMismatch(response -> new HostEncounterServlet(request ->
                new HostCommandService.EncounterResult(
                        HostCommandService.Status.MODULE_HASH_MISMATCH, null))
                .doPost(request(), response));
        assertMismatch(response -> new HostCampaignExportServlet(() ->
                new HostCommandService.ExportResult(
                        HostCommandService.Status.MODULE_HASH_MISMATCH, null))
                .doGet(request(), response));
    }

    private static void assertMismatch(Action action) throws Exception {
        ResponseFixture response = new ResponseFixture();

        action.run(response.proxy());

        assertEquals(HttpServletResponse.SC_CONFLICT, response.status);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals("application/json", response.contentType);
        assertEquals(ERROR, response.body.toString());
    }

    private static HttpServletRequest request() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("checkType", "MANUAL");
        parameters.put("manualModifier", "0");
        parameters.put("manualName", "manual");
        parameters.put("difficultyClass", "10");
        parameters.put("targetCharacterVersions", "");
        parameters.put("partyNodeKey", "node.entry");
        parameters.put("participants", "");
        parameters.put("characterKey", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        parameters.put("nodeKey", "node.entry");
        Map<String, Object> attributes = Map.of(
                HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE,
                "123e4567-e89b-42d3-a456-426614174000",
                HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE,
                "a".repeat(64),
                HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE,
                "0");
        return proxy(HttpServletRequest.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "getParameter" -> parameters.get(arguments[0]);
                    case "getParameterValues" -> null;
                    case "getAttribute" -> attributes.get(arguments[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    @FunctionalInterface
    private interface Action {
        void run(HttpServletResponse response) throws Exception;
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;
        private String characterEncoding;
        private String contentType;

        private HttpServletResponse proxy() {
            return ModuleHashMismatchHttpTest.proxy(
                    HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> {
                    status = (int) arguments[0];
                    yield null;
                }
                case "setCharacterEncoding" -> {
                    characterEncoding = (String) arguments[0];
                    yield null;
                }
                case "setContentType" -> {
                    contentType = (String) arguments[0];
                    yield null;
                }
                case "getWriter" -> writer;
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
