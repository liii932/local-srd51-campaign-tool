package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignCreationService;
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

/** Verifies the public HTTP contract without starting Tomcat or connecting to MySQL. */
final class HostCampaignCreationServletTest {
    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String REQUEST_DIGEST = "a".repeat(64);
    private static final String CAMPAIGN_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void createdCampaignReturnsOnlyStablePublicFields() throws Exception {
        HostCampaignCreationServlet servlet = servlet(
                new CampaignCreationService.Result(
                        CampaignCreationService.Status.CREATED, CAMPAIGN_KEY));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request("本机战役"), response.proxy());

        assertEquals(HttpServletResponse.SC_CREATED, response.status);
        assertEquals("application/json", response.contentType);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals(
                "{\"status\":\"CREATED\",\"campaignKey\":\"" + CAMPAIGN_KEY + "\"}",
                response.body.toString());
    }

    @Test
    void replayAndBusinessFailuresMapToStableStatuses() throws Exception {
        assertResponse(CampaignCreationService.Status.ALREADY_SUCCEEDED,
                HttpServletResponse.SC_OK, "ALREADY_SUCCEEDED");
        assertResponse(CampaignCreationService.Status.INVALID_REQUEST,
                HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
        assertResponse(CampaignCreationService.Status.IDEMPOTENCY_CONFLICT,
                HttpServletResponse.SC_CONFLICT, "IDEMPOTENCY_CONFLICT");
        assertResponse(CampaignCreationService.Status.ACTIVE_CAMPAIGN_EXISTS,
                HttpServletResponse.SC_CONFLICT, "ACTIVE_CAMPAIGN_EXISTS");
        assertResponse(CampaignCreationService.Status.RELEASE_UNAVAILABLE,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
        assertResponse(CampaignCreationService.Status.MODULE_HASH_MISMATCH,
                HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
    }

    @Test
    void databaseFailureReturnsGenericErrorWithoutExceptionDetails() throws Exception {
        HostCampaignCreationServlet servlet = new HostCampaignCreationServlet(
                (name, requestId, digest) -> {
                    throw new SQLException("secret provider detail");
                });
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request("本机战役"), response.proxy());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}",
                response.body.toString());
    }

    private static HostCampaignCreationServlet servlet(CampaignCreationService.Result result) {
        return new HostCampaignCreationServlet((name, requestId, digest) -> {
            assertEquals("本机战役", name);
            assertEquals(REQUEST_ID, requestId);
            assertEquals(REQUEST_DIGEST, digest);
            return result;
        });
    }

    private static void assertResponse(
            CampaignCreationService.Status status, int expectedHttpStatus, String expectedCode)
            throws Exception {
        String campaignKey = status == CampaignCreationService.Status.ALREADY_SUCCEEDED
                ? CAMPAIGN_KEY : null;
        ResponseFixture response = new ResponseFixture();
        servlet(new CampaignCreationService.Result(status, campaignKey))
                .doPost(request("本机战役"), response.proxy());

        assertEquals(expectedHttpStatus, response.status);
        if (status == CampaignCreationService.Status.ALREADY_SUCCEEDED) {
            assertEquals("{\"status\":\"ALREADY_SUCCEEDED\",\"campaignKey\":\""
                    + CAMPAIGN_KEY + "\"}", response.body.toString());
        } else {
            assertEquals("{\"status\":\"ERROR\",\"code\":\"" + expectedCode + "\"}",
                    response.body.toString());
        }
    }

    private static HttpServletRequest request(String campaignName) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
        attributes.put(HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, REQUEST_DIGEST);
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
            case "getAttribute" -> attributes.get(arguments[0]);
            case "getParameter" -> "campaignName".equals(arguments[0]) ? campaignName : null;
            default -> defaultValue(method);
        };
        return proxy(HttpServletRequest.class, handler);
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private String characterEncoding;

        private HttpServletResponse proxy() {
            return HostCampaignCreationServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> {
                    status = (int) arguments[0];
                    yield null;
                }
                case "setContentType" -> {
                    contentType = (String) arguments[0];
                    yield null;
                }
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
