package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HostRulesPageTest {
    private static final Path VIEW =
            Path.of("src/main/webapp/WEB-INF/views/host-rules.jsp");
    private static final Path HOST_VIEW =
            Path.of("src/main/webapp/WEB-INF/views/host.jsp");

    @Test
    void ruleBrowserUsesOnlyProtectedReadOnlyHostRoutes() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        String host = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);

        assertTrue(view.contains("method=\"get\""));
        assertTrue(view.contains("/host/rules"));
        assertTrue(view.contains("/api/host/rules"));
        assertTrue(view.contains("HtmlSupport.escape"));
        assertTrue(view.contains("id=\"host-rule-results\""));
        assertTrue(host.contains("/host/rules"));
        for (String forbidden : List.of(
                "method=\"post\"", "/display", "/api/public", "/api/player")) {
            assertFalse(view.contains(forbidden), forbidden);
        }
    }

    @Test
    void pageStatesThatOnlyTheFrozenVerifiedReleasedCatalogIsVisible() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);

        assertTrue(view.contains("当前活动战役冻结并通过完整哈希校验的已发布内置规则"));
        assertTrue(view.contains("当前战役冻结的规则发布版不可用或尚未发布"));
        assertTrue(view.contains("规则目录完整性校验失败"));
    }
}
