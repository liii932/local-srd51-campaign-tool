package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CampaignSaveDownloadResponseTest {
    @Test
    void writesFixedUtf8JsonAttachmentAndPreservesHostSecurityHeaders() throws Exception {
        String json = "{\"name\":\"本机战役\"}";
        CampaignSaveFileService.ExportFile file = new CampaignSaveFileService().encode(json);
        ResponseFixture response = new ResponseFixture();
        response.headers.put("cache-control", "no-store");
        response.headers.put("content-security-policy", "frame-ancestors 'none'");

        CampaignSaveDownloadResponse.write(response.proxy(), file);

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals("UTF-8", response.characterEncoding);
        assertEquals("application/json", response.contentType);
        assertEquals("attachment; filename=\"campaign-save.json\"",
                response.headers.get("content-disposition"));
        assertEquals("no-store", response.headers.get("cache-control"));
        assertEquals("frame-ancestors 'none'",
                response.headers.get("content-security-policy"));
        assertFalse(response.headers.containsKey("content-encoding"));
        assertEquals(file.byteLength(), response.contentLength);
        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), response.body.toByteArray());
    }

    private static final class ResponseFixture implements InvocationHandler {
        private final Map<String, String> headers = new HashMap<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final ServletOutputStream output = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // Synchronous unit-test response; asynchronous writes are intentionally unused.
            }

            @Override
            public void write(int value) {
                body.write(value);
            }
        };
        private int status = HttpServletResponse.SC_OK;
        private String characterEncoding;
        private String contentType;
        private long contentLength = -1;

        private HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
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
                case "setContentLengthLong" -> {
                    contentLength = (long) arguments[0];
                    yield null;
                }
                case "setHeader", "addHeader" -> {
                    headers.put(((String) arguments[0]).toLowerCase(Locale.ROOT),
                            (String) arguments[1]);
                    yield null;
                }
                case "getOutputStream" -> output;
                default -> defaultValue(method);
            };
        }
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
