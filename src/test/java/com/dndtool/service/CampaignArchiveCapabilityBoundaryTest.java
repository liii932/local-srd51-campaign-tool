package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.service.storage.CampaignCodec;
import com.dndtool.service.storage.StorageException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Protects the format, source and SQL boundaries of the retained archive services. */
final class CampaignArchiveCapabilityBoundaryTest {
    private static final Pattern RECOVERY_NAME =
            Pattern.compile("(?i)(?:undo|revert|backup|branch|timeline|snapshot|history)");
    private static final Pattern RECOVERY_TABLE = Pattern.compile(
            "(?i)\\b(?:campaign_(?:backup|branch|history|snapshot)|"
                    + "(?:archive|import)_(?:backup|history|snapshot))\\b");

    @Test
    void formatTwoDraftSliceStaysInternalAndCannotOpenAsACompleteCampaign() {
        assertFalse(Modifier.isPublic(CampaignArchiveV2CharacterStateCodec.class.getModifiers()));
        assertFalse(Modifier.isPublic(CampaignArchiveV2DraftPreview.class.getModifiers()));
        assertFalse(Modifier.isPublic(CampaignArchiveV2DraftExportService.class.getModifiers()));
        assertFalse(Modifier.isPublic(CampaignArchiveV2DraftImportService.class.getModifiers()));

        var draft = new CampaignArchiveV2CharacterState(2, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        byte[] fragment = CampaignArchiveV2CharacterStateCodec.write(draft, Set.of());
        assertEquals(CampaignArchiveV2CharacterStateCodec.Status.READY,
                CampaignArchiveV2CharacterStateCodec.read(fragment, Set.of()).status());
        assertEquals("INVALID_FILE", assertThrows(StorageException.class,
                () -> new CampaignCodec().decode(fragment)).code());
    }

    @Test
    void archiveSourcesCreateNoFilesystemBackupOrRecoveryTables() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/dndtool"))) {
            var archiveFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("CampaignArchive"))
                    .toList();
            assertFalse(archiveFiles.isEmpty());
            for (Path file : archiveFiles) {
                assertFalse(RECOVERY_NAME.matcher(file.getFileName().toString()).find(), file.toString());
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.matches("(?s).*\\b(?:Files\\.(?:copy|move|createTempFile)|"
                        + "FileOutputStream|RandomAccessFile)\\b.*"), file.toString());
            }
        }

        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations.filter(Files::isRegularFile).toList()) {
                // Rule snapshots and immutable execution histories are not campaign backup/branch commands.
                if (!Set.of("V019__runtime-language-snapshot.sql", "V020__runtime-tool-snapshot.sql",
                        "V021__schema2-runtime-snapshot.sql", "V029__schema2-character-check-history.sql",
                        "V031__schema2-equipment-health-history.sql")
                        .contains(migration.getFileName().toString())) {
                    assertFalse(RECOVERY_NAME.matcher(migration.getFileName().toString()).find(),
                            migration.toString());
                }
                assertFalse(RECOVERY_TABLE.matcher(
                        Files.readString(migration, StandardCharsets.UTF_8)).find(),
                        migration.toString());
            }
        }
    }
}
