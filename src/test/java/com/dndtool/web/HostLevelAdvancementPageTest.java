package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostLevelAdvancementPageTest {
    @Test
    void hostPageExposesBoundedPreviewAndConfirmControls() throws Exception {
        String view = Files.readString(
                Path.of("src/main/webapp/WEB-INF/views/host.jsp"), StandardCharsets.UTF_8);
        String script = Files.readString(
                Path.of("src/main/webapp/host/assets/host-level-advancement.js"),
                StandardCharsets.UTF_8);

        for (String id : List.of("level-advancement-form", "level-advancement-character-key",
                "level-advancement-target", "level-advancement-class",
                "level-advancement-hp-choice", "level-advancement-preview",
                "level-advancement-confirm",
                "level-advancement-result")) {
            assertTrue(view.contains("id=\"" + id + "\""), id);
        }
        assertTrue(view.contains("name=\"classSubclassKey\""));
        assertTrue(view.contains("name=\"subclassKey\""));
        assertTrue(view.contains("/api/host/characters/level-up"));
        assertTrue(script.contains("DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V2"));
        assertTrue(script.contains("expectedEventTail"));
        assertTrue(script.contains("expectedRowVersion"));
        assertTrue(script.contains("X-Request-Digest"));
    }
}
