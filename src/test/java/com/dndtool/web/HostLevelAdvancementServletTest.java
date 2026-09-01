package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.LevelAdvancementRules;
import com.dndtool.service.LevelAdvancementService;
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

class HostLevelAdvancementServletTest {
    private static final String CHARACTER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void confirmedServerRollReturnsOnlyStableOutcome() throws Exception {
        HostLevelAdvancementServlet servlet = new HostLevelAdvancementServlet(
                new HostLevelAdvancementServlet.Advancer() {
                    @Override
                    public LevelAdvancementService.PreviewResult preview(
                            LevelAdvancementRules.Request request) {
                        throw new AssertionError();
                    }

                    @Override
                    public LevelAdvancementService.ConfirmResult confirm(
                            LevelAdvancementRules.Request request, long tail, long version,
                            String preview, String requestId, String requestDigest) {
                        assertEquals(2, request.targetLevel());
                        assertEquals("SERVER_ROLL", request.hpChoiceAlgorithm());
                        assertEquals(9, tail);
                        assertEquals(4, version);
                        return new LevelAdvancementService.ConfirmResult(
                                LevelAdvancementService.Status.ADVANCED,
                                CHARACTER, 5L, 7, 9);
                    }
                });
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(201, response.status);
        assertEquals("{\"status\":\"ADVANCED\",\"characterKey\":\"" + CHARACTER
                + "\",\"rowVersion\":5,\"hitDieRoll\":7,\"hitPointIncrease\":9}",
                response.body.toString());
    }

    private static HttpServletRequest request() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE,
                "123e4567-e89b-42d3-a456-426614174000");
        attributes.put(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, "b".repeat(64));
        Map<String, String> parameters = Map.of(
                "action", "CONFIRM", "characterKey", CHARACTER,
                "targetLevel", "2", "hpChoiceAlgorithm", "SERVER_ROLL",
                "expectedEventTail", "9", "expectedRowVersion", "4",
                "previewDigest", "c".repeat(64));
        return proxy(HttpServletRequest.class, (ignored, method, arguments) -> switch (
                method.getName()) {
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
            return HostLevelAdvancementServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "getWriter" -> writer;
                case "setContentType", "setCharacterEncoding" -> null;
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
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
