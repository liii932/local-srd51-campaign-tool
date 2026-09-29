package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class HostConsolePageTest {
    private static final Path VIEW = Path.of("src/main/webapp/WEB-INF/views/host.jsp");

    @Test
    void consoleKeepsEveryExistingCommandReachableThroughNativeDisclosure() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        for (String id : List.of("check-workspace", "map-workspace", "characters-workspace",
                "campaign-workspace")) {
            assertTrue(view.contains("<details id=\"" + id + "\""), id);
        }
        assertTrue(view.contains("href=\"#host-workspace\""));
        assertTrue(view.contains("href=\"#character-desk\""));
        assertTrue(view.contains("id=\"host-workspace\" class=\"console\""));
        for (String id : List.of("create-campaign-form", "create-character-form",
                "level-one-character-form", "level-advancement-form", "character-lifecycle-form",
                "character-card-load-form", "add-module-item-form", "add-temporary-item-form",
                "host-event-form", "host-encounter-form", "host-position-form")) {
            assertEquals(1, Pattern.compile("<form id=\"" + id + "\"")
                    .matcher(view).results().count(), id);
        }
        assertTrue(view.contains("角色构筑与升级 · DRAFT 未发布"));
        assertTrue(view.contains("DRAFT 规则版在发布前会拒绝业务执行。"));
    }

    @Test
    void defaultDisclosureDependsOnAuthoritativeOverviewStatus() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        assertTrue(view.contains("id=\"check-workspace\" class=\"command-group\""
                + "<%= \"READY\".equals(overviewStatus) ? \" open\" : \"\" %>"));
        assertTrue(view.contains("id=\"campaign-workspace\" class=\"command-group\""
                + "<%= \"EMPTY\".equals(overviewStatus) ? \" open\" : \"\" %>"));
    }

    @Test
    void characterSuggestionsStayOutsideReadonlyOverviewAndExecutorInputs() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        int aside = view.indexOf("<aside id=\"character-desk\"");
        int options = view.indexOf("<datalist id=\"card-character-options\"");
        assertTrue(aside > view.indexOf("id=\"host-event-form\""));
        assertTrue(options > aside);
        assertTrue(view.contains("id=\"card-character-key\" name=\"characterKey\" type=\"text\""
                + " list=\"card-character-options\" required"));
        assertEquals(1, Pattern.compile("list=\"card-character-options\"")
                .matcher(view).results().count());
        String suggestions = view.substring(options, view.indexOf("</datalist>", options));
        assertTrue(suggestions.contains("HtmlSupport.escape(character.characterKey())"));
        assertTrue(suggestions.contains("HtmlSupport.escape(character.characterName())"));
        assertFalse(suggestions.contains("rowVersion"));
        assertFalse(suggestions.contains("executor"));
    }

    @Test
    void presentationUsesSelfHostedStylesWithoutInlineOrScriptedLayout() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        String css = Files.readString(Path.of("src/main/webapp/host/assets/host-console.css"),
                StandardCharsets.UTF_8);
        assertTrue(view.contains("/host/assets/host-console.css\">"));
        assertFalse(view.contains("<style"));
        assertFalse(view.contains("style=\""));
        assertFalse(view.contains("<script>"));
        assertFalse(css.contains("@import"));
        assertFalse(css.contains("url("));
        assertTrue(css.contains("[hidden] { display: none !important; }"));
        assertTrue(css.contains("@media (max-width: 560px)"));
    }
}
