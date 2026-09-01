package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignArchiveConfirmationRequestDigest;
import com.dndtool.service.CampaignArchiveConfirmationService;
import com.dndtool.service.CampaignArchiveDigest;
import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HostArchiveImportServletTest {
    private static final String REQUEST_ID = "33333333-3333-4333-8333-333333333333";
    private static final String CONFIRMED = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final byte[] CONTENT = "{\"formatVersion\":1}"
            .getBytes(StandardCharsets.UTF_8);
    private static final String FILE_DIGEST = CampaignArchiveDigest.sha256(CONTENT);
    private static final String REQUEST_DIGEST =
            CampaignArchiveConfirmationRequestDigest.sha256(FILE_DIGEST, CONFIRMED);

    @Test
    void annotationKeepsConfirmationInsideTheProtectedBoundedNamespace() {
        WebServlet servlet = HostArchiveImportServlet.class.getAnnotation(WebServlet.class);
        MultipartConfig multipart =
                HostArchiveImportServlet.class.getAnnotation(MultipartConfig.class);

        assertArrayEquals(new String[] {"/api/host/archive/import"}, servlet.urlPatterns());
        assertEquals(CampaignSaveFileService.MAX_BYTES, multipart.maxFileSize());
        assertEquals(CampaignSaveFileService.MAX_BYTES + 65_536L, multipart.maxRequestSize());
        assertEquals(0, multipart.fileSizeThreshold());
    }

    @Test
    void completedImportInvalidatesSessionExpiresCookieAndRedirectsToHost()
            throws Exception {
        SessionFixture session = new SessionFixture();
        ResponseFixture response = new ResponseFixture();
        HostArchiveImportServlet servlet = new HostArchiveImportServlet(command -> {
            assertEquals(REQUEST_ID, command.requestId());
            assertEquals(REQUEST_DIGEST, command.requestDigestSha256());
            assertEquals(FILE_DIGEST, command.previewFileSha256());
            assertEquals(CONFIRMED, command.confirmedArchiveCampaignKey());
            assertArrayEquals(CONTENT, command.content());
            return new CampaignArchiveConfirmationService.Result(
                    CampaignArchiveConfirmationService.Status.COMPLETED, 7L, false);
        });

        servlet.doPost(request(session, CONTENT).proxy(), response.proxy());

        assertTrue(session.invalidated);
        assertEquals(HttpServletResponse.SC_SEE_OTHER, response.status);
        assertEquals("/host", response.headers.get("location"));
        assertEquals("DNDHOSTSESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0",
                response.headers.get("set-cookie"));
        assertEquals("", response.body.toString());
    }

    @Test
    void rejectedImportPreservesSessionAndReturnsStableErrorWithoutRedirect()
            throws Exception {
        for (CampaignArchiveConfirmationService.Status status : List.of(
                CampaignArchiveConfirmationService.Status.DIGEST_MISMATCH,
                CampaignArchiveConfirmationService.Status.MODULE_UNAVAILABLE,
                CampaignArchiveConfirmationService.Status.MODULE_HASH_MISMATCH,
                CampaignArchiveConfirmationService.Status.PREVIEW_STATE_CHANGED,
                CampaignArchiveConfirmationService.Status.STABLE_IDENTITY_CONFLICT)) {
            SessionFixture session = new SessionFixture();
            ResponseFixture response = new ResponseFixture();
            HostArchiveImportServlet servlet = new HostArchiveImportServlet(command ->
                    new CampaignArchiveConfirmationService.Result(status, null, false));

            servlet.doPost(request(session, CONTENT).proxy(), response.proxy());

            int expected = status == CampaignArchiveConfirmationService.Status.DIGEST_MISMATCH
                    ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_CONFLICT;
            assertEquals(expected, response.status, status.name());
            assertFalse(session.invalidated, status.name());
            assertFalse(response.headers.containsKey("location"), status.name());
            assertEquals("{\"status\":\"ERROR\",\"code\":\""
                    + status.name() + "\"}", response.body.toString(), status.name());
        }
    }

    @Test
    void malformedUploadMissingSessionAndDatabaseFailureNeverRedirect() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HostArchiveImportServlet counting = new HostArchiveImportServlet(command -> {
            calls.incrementAndGet();
            throw new SQLException("synthetic database detail");
        });

        ResponseFixture noSession = new ResponseFixture();
        counting.doPost(request(null, CONTENT).proxy(), noSession.proxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, noSession.status);

        SessionFixture session = new SessionFixture();
        ResponseFixture badUpload = new ResponseFixture();
        counting.doPost(request(session, new byte[0]).proxy(), badUpload.proxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, badUpload.status);
        assertEquals(0, calls.get());

        ResponseFixture database = new ResponseFixture();
        counting.doPost(request(session, CONTENT).proxy(), database.proxy());
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, database.status);
        assertEquals(1, calls.get());
        assertFalse(session.invalidated);
        assertFalse(database.headers.containsKey("location"));
        assertTrue(database.body.toString().contains("DATABASE_UNAVAILABLE"));
    }

    private static RequestFixture request(SessionFixture session, byte[] content) {
        Map<String, String> headers = Map.of(
                HostArchiveImportServlet.PREVIEW_DIGEST_HEADER.toLowerCase(Locale.ROOT),
                FILE_DIGEST,
                HostArchiveImportServlet.CONFIRMED_CAMPAIGN_HEADER.toLowerCase(Locale.ROOT),
                CONFIRMED);
        Map<String, Object> attributes = Map.of(
                HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID,
                HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE, REQUEST_DIGEST);
        return new RequestFixture(session, content, headers, attributes);
    }

    private record RequestFixture(
            SessionFixture session,
            byte[] content,
            Map<String, String> headers,
            Map<String, Object> attributes) {
        private HttpServletRequest proxy() {
            Part part = HostArchiveImportServletTest.proxy(
                    Part.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "getName" -> ArchiveMultipartSupport.ARCHIVE_PART_NAME;
                        case "getSubmittedFileName" -> "campaign-save.json";
                        case "getSize" -> (long) content.length;
                        case "getInputStream" -> new ByteArrayInputStream(content);
                        default -> defaultValue(method);
                    });
            return HostArchiveImportServletTest.proxy(
                    HttpServletRequest.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "getSession" -> session == null ? null : session.proxy();
                        case "getContentType" -> "multipart/form-data; boundary=x";
                        case "getParts" -> List.of(part);
                        case "getHeader" -> headers.get(
                                arguments[0].toString().toLowerCase(Locale.ROOT));
                        case "getAttribute" -> attributes.get(arguments[0]);
                        case "getContextPath" -> "";
                        default -> defaultValue(method);
                    });
        }
    }

    private static final class SessionFixture implements InvocationHandler {
        private boolean invalidated;

        private HttpSession proxy() {
            return HostArchiveImportServletTest.proxy(
                    HttpSession.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            if ("invalidate".equals(method.getName())) {
                invalidated = true;
                return null;
            }
            return defaultValue(method);
        }
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final Map<String, String> headers = new HashMap<>();
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;

        private HttpServletResponse proxy() {
            return HostArchiveImportServletTest.proxy(
                    HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "setStatus" -> { status = (int) arguments[0]; yield null; }
                case "setHeader" -> {
                    headers.put(arguments[0].toString().toLowerCase(Locale.ROOT),
                            arguments[1].toString());
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
