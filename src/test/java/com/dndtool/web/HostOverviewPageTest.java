package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HostOverviewPageTest {
    private static final Path HOST_VIEW = Path.of("src/main/webapp/WEB-INF/views/host.jsp");

    @Test
    void overviewPresentsEveryApprovedHostCommandReadModel() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);
        String overview = overview(view);

        for (String id : List.of(
                "host-overview",
                "host-character-summary",
                "host-check-message-summary",
                "host-item-summary",
                "host-map-nodes",
                "host-map-connections",
                "host-encounter-summary")) {
            assertTrue(overview.contains("id=\"" + id + "\""), id);
        }
        for (String heading : List.of(
                "角色摘要", "检定与消息", "简单物品总览", "节点地图", "当前遭遇")) {
            assertTrue(overview.contains(heading), heading);
        }
        assertTrue(overview.contains("HtmlSupport.escape("));
    }

    @Test
    void overviewIsReadOnlyAndContainsNoAutomatedCombatControlVocabulary() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);
        String overview = overview(view);

        for (String control : List.of("<form", "<button", "<input", "<select", "<textarea")) {
            assertFalse(overview.contains(control), control);
        }
        String lower = overview.toLowerCase(java.util.Locale.ROOT);
        for (String forbidden : List.of(
                "attack", "damage", "initiative", "turn_order", "movement_speed",
                "攻击", "伤害", "先攻", "回合控制")) {
            assertFalse(lower.contains(forbidden), forbidden);
        }
    }

    @Test
    void jspScriptletAvoidsPatternVariablesUnsupportedByTomcatJasperSourceLevel() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(view.contains("instanceof HostOverviewRepository.Snapshot\n"));
        assertTrue(view.contains("instanceof String\n"));
        assertFalse(view.contains("instanceof HostOverviewRepository.Snapshot snapshot"));
        assertFalse(view.contains("instanceof String status"));
    }

    @Test
    void moduleMismatchShowsOnlyGenericFailureWithoutDigestDetails() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);

        assertTrue(view.contains("\"MODULE_HASH_MISMATCH\".equals(overviewStatus)"));
        assertTrue(view.contains("模组完整性校验未通过，相关操作已拒绝。"));
        assertFalse(view.contains("frozenContentSha256()"));
        assertFalse(view.contains("releaseContentSha256()"));
    }

    private static String overview(String view) {
        int start = view.indexOf("<section id=\"host-overview\"");
        int end = view.indexOf("</section>", start);
        assertTrue(start >= 0 && end > start);
        return view.substring(start, end);
    }
}
