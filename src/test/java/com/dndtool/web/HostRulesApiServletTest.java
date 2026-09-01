package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.HostRulesService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HostRulesApiServletTest {
    @Test
    void routeAndReadyJsonExposeOnlyTheVerifiedHostProjection() throws Exception {
        WebServlet route = HostRulesApiServlet.class.getAnnotation(WebServlet.class);
        assertEquals(List.of("/api/host/rules"), List.of(route.urlPatterns()));
        HostRulesService.CatalogView catalog = new HostRulesService.CatalogView(
                "dnd5e2014_srd51_se_v1", "1", 1, "力量",
                HostRulesService.RuleType.FIELD,
                List.of(new HostRulesService.RuleEntry(
                        HostRulesService.RuleType.FIELD,
                        "ability.strength", "力量", "基础属性")));
        HostRulesApiServlet servlet = new HostRulesApiServlet((query, type) -> {
            assertEquals("力量", query);
            assertEquals("FIELD", type);
            return new HostRulesService.Result(HostRulesService.Status.READY, catalog);
        });
        ResponseFixture response = new ResponseFixture();

        servlet.doGet(request(Map.of("q", "力量", "type", "FIELD")), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals("application/json", response.contentType);
        assertEquals(
                "{\"status\":\"READY\",\"module\":{"
                        + "\"moduleKey\":\"dnd5e2014_srd51_se_v1\","
                        + "\"releaseVersion\":\"1\",\"canonicalFormatVersion\":1},"
                        + "\"query\":\"力量\",\"type\":\"FIELD\",\"entries\":[{"
                        + "\"type\":\"FIELD\",\"key\":\"ability.strength\","
                        + "\"displayName\":\"力量\",\"summary\":\"基础属性\"}]}",
                response.body.toString());
    }

    @Test
    void serviceAndDatabaseFailuresUseStableHttpErrors() throws Exception {
        assertError(HostRulesService.Status.INVALID_REQUEST, 400, "INVALID_REQUEST");
        assertError(HostRulesService.Status.NO_ACTIVE_CAMPAIGN, 404, "NO_ACTIVE_CAMPAIGN");
        assertError(HostRulesService.Status.MODULE_UNAVAILABLE, 503, "MODULE_UNAVAILABLE");
        assertError(HostRulesService.Status.MODULE_HASH_MISMATCH, 409,
                "MODULE_HASH_MISMATCH");
        assertError(HostRulesService.Status.INVALID_STATE, 409, "RULE_CATALOG_INVALID");

        HostRulesApiServlet servlet = new HostRulesApiServlet((query, type) -> {
            throw new SQLException("secret database detail");
        });
        ResponseFixture response = new ResponseFixture();
        servlet.doGet(request(Map.of()), response.proxy());
        assertEquals(503, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}",
                response.body.toString());
    }

    @Test
    void jsonEscapesCatalogTextInsteadOfRenderingHtmlOrRawControlCharacters()
            throws Exception {
        HostRulesService.CatalogView catalog = new HostRulesService.CatalogView(
                "dnd5e2014_srd51_se_v1", "1", 1, "", null,
                List.of(new HostRulesService.RuleEntry(
                        HostRulesService.RuleType.ITEM, "item.test", "<script>",
                        "quote \" and\nline")));
        ResponseFixture response = new ResponseFixture();
        new HostRulesApiServlet((query, type) ->
                new HostRulesService.Result(HostRulesService.Status.READY, catalog))
                .doGet(request(Map.of()), response.proxy());

        assertTrue(response.body.toString().contains("\"displayName\":\"<script>\""));
        assertTrue(response.body.toString().contains("quote \\\" and\\nline"));
    }

    private static void assertError(
            HostRulesService.Status status, int expectedHttp, String expectedCode)
            throws Exception {
        HostRulesApiServlet servlet = new HostRulesApiServlet((query, type) ->
                new HostRulesService.Result(status, null));
        ResponseFixture response = new ResponseFixture();
        servlet.doGet(request(Map.of()), response.proxy());
        assertEquals(expectedHttp, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"" + expectedCode + "\"}",
                response.body.toString());
    }

    private static HttpServletRequest request(Map<String, String> parameters) {
        return proxy(HttpServletRequest.class, (ignored, method, arguments) ->
                "getParameter".equals(method.getName())
                        ? parameters.get((String) arguments[0])
                        : defaultValue(method));
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private String characterEncoding;

        private HttpServletResponse proxy() {
            return HostRulesApiServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "setContentType" -> { contentType = (String) arguments[0]; yield null; }
                case "setCharacterEncoding" -> {
                    characterEncoding = (String) arguments[0];
                    yield null;
                }
                case "getWriter" -> writer;
                default -> defaultValue(method);
            };
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
