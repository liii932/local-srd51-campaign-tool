package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterCardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HostCharacterCardServletTest {
    private static final String KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String DIGEST = "a".repeat(64);

    @Test
    void getReturnsDedicatedEscapedCardDto() throws Exception {
        HostCharacterCardServlet servlet = new HostCharacterCardServlet(
                operations(new CharacterCardService.LoadResult(
                        CharacterCardService.LoadStatus.READY, card()), null));
        ResponseFixture response = new ResponseFixture();

        servlet.doGet(request("GET"), response.proxy());

        assertEquals(200, response.status);
        String body = response.body.toString();
        assertTrue(body.startsWith("{\"status\":\"READY\",\"card\":{"));
        assertTrue(body.contains("\"characterName\":\"A\\\"ria\""));
        assertTrue(body.contains("\"totalLevel\":5"));
        assertFalse(body.contains("<script>"));
        assertTrue(body.contains("\\u2028"));
    }

    @Test
    void postMapsVersionConflictAndForwardsSecurityAttributes() throws Exception {
        CharacterCardService.MutationResult conflict = new CharacterCardService.MutationResult(
                CharacterCardService.MutationStatus.VERSION_CONFLICT, 9L);
        HostCharacterCardServlet servlet = new HostCharacterCardServlet(
                operations(null, conflict));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request("POST"), response.proxy());

        assertEquals(409, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"VERSION_CONFLICT\","
                + "\"rowVersion\":9}", response.body.toString());
    }

    @Test
    void loadAndMutationReturnOnlyStableModuleMismatchError() throws Exception {
        HostCharacterCardServlet servlet = new HostCharacterCardServlet(operations(
                new CharacterCardService.LoadResult(
                        CharacterCardService.LoadStatus.MODULE_HASH_MISMATCH, null),
                new CharacterCardService.MutationResult(
                        CharacterCardService.MutationStatus.MODULE_HASH_MISMATCH, null)));
        ResponseFixture load = new ResponseFixture();
        ResponseFixture mutation = new ResponseFixture();

        servlet.doGet(request("GET"), load.proxy());
        servlet.doPost(request("POST"), mutation.proxy());

        String expected = "{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}";
        assertEquals(409, load.status);
        assertEquals(expected, load.body.toString());
        assertEquals(409, mutation.status);
        assertEquals(expected, mutation.body.toString());
    }

    @Test
    void databaseFailureDoesNotExposeProviderDetails() throws Exception {
        HostCharacterCardServlet servlet = new HostCharacterCardServlet(
                new HostCharacterCardServlet.CardOperations() {
                    @Override
                    public CharacterCardService.LoadResult load(String key) throws SQLException {
                        throw new SQLException("provider detail and SQL");
                    }

                    @Override
                    public CharacterCardService.MutationResult mutate(
                            String a, String b, String c, String d, String e,
                            String f, String g, String h, String i) throws SQLException {
                        throw new SQLException("provider detail and SQL");
                    }
                });
        ResponseFixture response = new ResponseFixture();

        servlet.doGet(request("GET"), response.proxy());

        assertEquals(503, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}",
                response.body.toString());
    }

    private static HostCharacterCardServlet.CardOperations operations(
            CharacterCardService.LoadResult load,
            CharacterCardService.MutationResult mutation) {
        return new HostCharacterCardServlet.CardOperations() {
            @Override
            public CharacterCardService.LoadResult load(String key) {
                assertEquals(KEY, key);
                return load;
            }

            @Override
            public CharacterCardService.MutationResult mutate(
                    String key,
                    String version,
                    String action,
                    String target,
                    String value,
                    String description,
                    String quantity,
                    String requestId,
                    String digest) {
                assertEquals(KEY, key);
                assertEquals("7", version);
                assertEquals("SET_FIELD", action);
                assertEquals("ability.strength", target);
                assertEquals("15", value);
                assertEquals(REQUEST_ID, requestId);
                assertEquals(DIGEST, digest);
                return mutation;
            }
        };
    }

    private static CharacterCardService.Card card() {
        return new CharacterCardService.Card(
                KEY, "PC", "A\"ria", "ACTIVE", 7, 5, 3,
                List.of(new CharacterCardService.FieldView(
                        "ability.strength", "力\u2028量", 15, 1L, 30L, 2, null)),
                List.of(new CharacterCardService.ClassView("class.fighter", "战士", 5)),
                List.of(), List.of(),
                List.of(new CharacterCardService.TierView("proficiency.none", "NONE")),
                List.of(), List.of());
    }

    private static HttpServletRequest request(String method) {
        Map<String, String> parameters = Map.of(
                "characterKey", KEY,
                "action", "SET_FIELD",
                "targetKey", "ability.strength",
                "value", "15",
                "description", "",
                "quantity", "");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE, "7");
        attributes.put(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
        attributes.put(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, DIGEST);
        return proxy(HttpServletRequest.class, (ignored, invoked, arguments) ->
                switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getParameter" -> parameters.get(arguments[0]);
                    case "getAttribute" -> attributes.get(arguments[0]);
                    default -> defaultValue(invoked.getReturnType());
                });
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = 200;

        HttpServletResponse proxy() {
            return HostCharacterCardServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> {
                    status = (int) arguments[0];
                    yield null;
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
        return 0;
    }
}
