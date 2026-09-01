package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterCreationService;
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

class HostCharacterCreationServletTest {
    private static final String REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String REQUEST_DIGEST = "a".repeat(64);
    private static final String CHARACTER_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void createdCharacterReturnsOnlyStableIdentityAndVersion() throws Exception {
        HostCharacterCreationServlet servlet = servlet(new CharacterCreationService.Result(
                CharacterCreationService.Status.CREATED, CHARACTER_KEY, 0L));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(), response.proxy());

        assertEquals(HttpServletResponse.SC_CREATED, response.status);
        assertEquals("application/json", response.contentType);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals("{\"status\":\"CREATED\",\"characterKey\":\""
                + CHARACTER_KEY + "\",\"rowVersion\":0}", response.body.toString());
    }

    @Test
    void mapsReplayAndFailuresToStableCodes() throws Exception {
        assertResponse(CharacterCreationService.Status.ALREADY_SUCCEEDED, 200,
                "ALREADY_SUCCEEDED");
        assertResponse(CharacterCreationService.Status.INVALID_REQUEST, 400, "INVALID_REQUEST");
        assertResponse(CharacterCreationService.Status.IDEMPOTENCY_CONFLICT, 409,
                "IDEMPOTENCY_CONFLICT");
        assertResponse(CharacterCreationService.Status.CAMPAIGN_UNAVAILABLE, 409,
                "CAMPAIGN_UNAVAILABLE");
        assertResponse(CharacterCreationService.Status.TEMPLATE_UNAVAILABLE, 400,
                "TEMPLATE_UNAVAILABLE");
        assertResponse(CharacterCreationService.Status.MODULE_UNAVAILABLE, 503,
                "MODULE_UNAVAILABLE");
        assertResponse(CharacterCreationService.Status.MODULE_HASH_MISMATCH, 409,
                "MODULE_HASH_MISMATCH");
    }

    @Test
    void databaseFailureIsGeneric() throws Exception {
        HostCharacterCreationServlet servlet = new HostCharacterCreationServlet(
                (a, b, c, d, e, f) -> { throw new SQLException("provider detail"); });
        ResponseFixture response = new ResponseFixture();
        servlet.doPost(request(), response.proxy());
        assertEquals(503, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}",
                response.body.toString());
    }

    private static HostCharacterCreationServlet servlet(CharacterCreationService.Result result) {
        return new HostCharacterCreationServlet((campaign, type, name, template, id, digest) -> {
            assertEquals("11111111-2222-4333-8444-555555555555", campaign);
            assertEquals("NPC", type);
            assertEquals("守卫甲", name);
            assertEquals("npc.guard", template);
            assertEquals(REQUEST_ID, id);
            assertEquals(REQUEST_DIGEST, digest);
            return result;
        });
    }

    private static void assertResponse(
            CharacterCreationService.Status status, int http, String code) throws Exception {
        String key = status == CharacterCreationService.Status.ALREADY_SUCCEEDED
                ? CHARACTER_KEY : null;
        Long version = status == CharacterCreationService.Status.ALREADY_SUCCEEDED ? 3L : null;
        ResponseFixture response = new ResponseFixture();
        servlet(new CharacterCreationService.Result(status, key, version))
                .doPost(request(), response.proxy());
        assertEquals(http, response.status);
        if (status == CharacterCreationService.Status.ALREADY_SUCCEEDED) {
            assertEquals("{\"status\":\"ALREADY_SUCCEEDED\",\"characterKey\":\""
                    + CHARACTER_KEY + "\",\"rowVersion\":3}", response.body.toString());
        } else {
            assertEquals("{\"status\":\"ERROR\",\"code\":\"" + code + "\"}",
                    response.body.toString());
        }
    }

    private static HttpServletRequest request() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
        attributes.put(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, REQUEST_DIGEST);
        Map<String, String> parameters = Map.of(
                "campaignKey", "11111111-2222-4333-8444-555555555555",
                "characterType", "NPC",
                "characterName", "守卫甲",
                "templateKey", "npc.guard");
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
        private String contentType;
        private String characterEncoding;

        private HttpServletResponse proxy() {
            return HostCharacterCreationServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "setContentType" -> { contentType = (String) arguments[0]; yield null; }
                case "setCharacterEncoding" -> {
                    characterEncoding = (String) arguments[0]; yield null;
                }
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
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
