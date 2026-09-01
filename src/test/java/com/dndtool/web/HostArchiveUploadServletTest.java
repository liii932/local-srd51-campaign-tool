package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.persistence.CampaignArchivePreviewRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignArchiveDigest;
import com.dndtool.service.CampaignArchivePreviewService;
import com.dndtool.service.CampaignArchiveUploadValidationService;
import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HostArchiveUploadServletTest {
    private static final byte[] CONTENT = "{\"formatVersion\":1}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void annotationsKeepUploadInsideProtectedHostNamespaceAndBoundItsSize() {
        WebServlet servlet = HostArchiveUploadServlet.class.getAnnotation(WebServlet.class);
        MultipartConfig multipart =
                HostArchiveUploadServlet.class.getAnnotation(MultipartConfig.class);

        assertArrayEquals(new String[] {"/api/host/archive/validate"}, servlet.urlPatterns());
        assertEquals(CampaignSaveFileService.MAX_BYTES, multipart.maxFileSize());
        assertEquals(CampaignSaveFileService.MAX_BYTES + 65_536L, multipart.maxRequestSize());
        assertEquals(0, multipart.fileSizeThreshold());
    }

    @Test
    void exactMultipartFileAndRawDigestReachReadOnlyValidator() throws Exception {
        ResponseFixture response = new ResponseFixture();
        HostArchiveUploadServlet servlet = new HostArchiveUploadServlet(content -> {
            assertArrayEquals(CONTENT, content);
            return readyResult();
        });

        servlet.doPost(request(CONTENT, digest(CONTENT)), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals("application/json", response.contentType);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals("{\"status\":\"READY\",\"preview\":{"
                + "\"mode\":\"CREATE\","
                + "\"campaign\":{\"campaignKey\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\","
                + "\"campaignName\":\"测试\\\"战役\",\"campaignStatus\":\"ACTIVE\"},"
                + "\"counts\":{\"characters\":1,\"fields\":4,\"classLevels\":1,"
                + "\"skillProficiencies\":1,\"saveProficiencies\":1,\"items\":2,"
                + "\"maps\":1,\"encounters\":1,\"participants\":1,"
                + "\"recentEvents\":2,\"checks\":1},"
                + "\"activeCampaignImpact\":\"OTHER_WILL_BE_ARCHIVED\","
                + "\"activeCampaign\":{\"campaignKey\":"
                + "\"ffffffff-eeee-4ddd-8ccc-bbbbbbbbbbbb\","
                + "\"campaignName\":\"当前战役\"},"
                + "\"rawFileSha256\":\"" + digest(CONTENT) + "\","
                + "\"irreversibleWarning\":true}}", response.body.toString());
    }

    @Test
    void validationFailuresMapToStableHttpErrors() throws Exception {
        assertStatus(CampaignArchiveUploadValidationService.Status.INVALID_ARCHIVE,
                HttpServletResponse.SC_BAD_REQUEST, "INVALID_ARCHIVE");
        assertStatus(CampaignArchiveUploadValidationService.Status.FILE_TOO_LARGE,
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "FILE_TOO_LARGE");
        assertStatus(CampaignArchiveUploadValidationService.Status.INVALID_CATALOG_REFERENCE,
                HttpServletResponse.SC_BAD_REQUEST, "INVALID_CATALOG_REFERENCE");
        assertStatus(CampaignArchiveUploadValidationService.Status.MODULE_UNAVAILABLE,
                HttpServletResponse.SC_CONFLICT, "MODULE_UNAVAILABLE");
        assertStatus(CampaignArchiveUploadValidationService.Status.MODULE_HASH_MISMATCH,
                HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
    }

    @Test
    void rejectsWrongMediaShapeExtraPartsOversizeAndDigestMismatchBeforeValidation()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HostArchiveUploadServlet servlet = new HostArchiveUploadServlet(content -> {
            calls.incrementAndGet();
            return readyResult();
        });

        ResponseFixture wrongMedia = new ResponseFixture();
        servlet.doPost(request("application/json", List.of(part(CONTENT)), digest(CONTENT)),
                wrongMedia.proxy());
        assertError(wrongMedia, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                "MULTIPART_REQUIRED");

        ResponseFixture extraPart = new ResponseFixture();
        servlet.doPost(request("multipart/form-data; boundary=x",
                List.of(part(CONTENT), part(CONTENT)), digest(CONTENT)), extraPart.proxy());
        assertError(extraPart, HttpServletResponse.SC_BAD_REQUEST, "INVALID_UPLOAD");

        ResponseFixture oversize = new ResponseFixture();
        servlet.doPost(request("multipart/form-data; boundary=x",
                List.of(part(CONTENT, CampaignSaveFileService.MAX_BYTES + 1L)), digest(CONTENT)),
                oversize.proxy());
        assertError(oversize, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "FILE_TOO_LARGE");

        ResponseFixture badDigest = new ResponseFixture();
        servlet.doPost(request(CONTENT, "0".repeat(64)), badDigest.proxy());
        assertError(badDigest, HttpServletResponse.SC_BAD_REQUEST, "REQUEST_DIGEST_MISMATCH");
        assertEquals(0, calls.get());
    }

    @Test
    void databaseFailureReturnsGenericUnavailableError() throws Exception {
        HostArchiveUploadServlet servlet = new HostArchiveUploadServlet(content -> {
            throw new SQLException("synthetic secret provider detail");
        });
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(CONTENT, digest(CONTENT)), response.proxy());

        assertError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE");
    }

    private static void assertStatus(
            CampaignArchiveUploadValidationService.Status status,
            int expectedHttpStatus,
            String code) throws Exception {
        HostArchiveUploadServlet servlet =
                new HostArchiveUploadServlet(content -> failureResult(status));
        ResponseFixture response = new ResponseFixture();

        servlet.doPost(request(CONTENT, digest(CONTENT)), response.proxy());

        assertError(response, expectedHttpStatus, code);
    }

    private static void assertError(ResponseFixture response, int status, String code) {
        assertEquals(status, response.status);
        assertEquals("{\"status\":\"ERROR\",\"code\":\"" + code + "\"}",
                response.body.toString());
    }

    private static CampaignArchivePreviewService.Result failureResult(
            CampaignArchiveUploadValidationService.Status status) {
        return new CampaignArchivePreviewService.Result(status, null);
    }

    private static CampaignArchivePreviewService.Result readyResult() {
        CampaignArchivePreviewService.ObjectCounts counts =
                new CampaignArchivePreviewService.ObjectCounts(1, 4, 1, 1, 1, 2, 1, 1, 1, 2, 1);
        CampaignArchivePreviewRepository.CampaignState active =
                new CampaignArchivePreviewRepository.CampaignState(
                        "ffffffff-eeee-4ddd-8ccc-bbbbbbbbbbbb", "当前战役", "ACTIVE");
        CampaignArchivePreviewService.Preview preview = new CampaignArchivePreviewService.Preview(
                CampaignArchivePreviewService.PreviewMode.CREATE,
                new CampaignArchivePreviewService.CampaignSummary(
                        "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "测试\"战役", "ACTIVE"),
                counts,
                CampaignArchivePreviewService.ActiveCampaignImpact.OTHER_WILL_BE_ARCHIVED,
                active,
                digest(CONTENT),
                true);
        return new CampaignArchivePreviewService.Result(
                CampaignArchiveUploadValidationService.Status.READY, preview);
    }

    private static HttpServletRequest request(byte[] content, String digest) {
        return request("multipart/form-data; boundary=x", List.of(part(content)), digest);
    }

    private static HttpServletRequest request(
            String contentType, Collection<Part> parts, String digest) {
        InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
            case "getContentType" -> contentType;
            case "getParts" -> parts;
            case "getAttribute" -> HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE
                    .equals(arguments[0]) ? digest : null;
            default -> defaultValue(method);
        };
        return proxy(HttpServletRequest.class, handler);
    }

    private static Part part(byte[] content) {
        return part(content, content.length);
    }

    private static Part part(byte[] content, long declaredSize) {
        InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
            case "getName" -> HostArchiveUploadServlet.ARCHIVE_PART_NAME;
            case "getSubmittedFileName" -> "campaign-save.json";
            case "getSize" -> declaredSize;
            case "getInputStream" -> new ByteArrayInputStream(content);
            default -> defaultValue(method);
        };
        return proxy(Part.class, handler);
    }

    private static String digest(byte[] content) {
        return CampaignArchiveDigest.sha256(content);
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private String characterEncoding;

        private HttpServletResponse proxy() {
            return HostArchiveUploadServletTest.proxy(HttpServletResponse.class, this);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
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
