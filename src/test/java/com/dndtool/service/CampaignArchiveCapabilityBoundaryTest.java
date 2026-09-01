package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CampaignArchiveImportRepository;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Locks the deliberately one-way archive boundary for the local MVP. */
final class CampaignArchiveCapabilityBoundaryTest {
    private static final Pattern SERVLET_ROUTE =
            Pattern.compile("urlPatterns\\s*=\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RECOVERY_NAME =
            Pattern.compile("(?i)(?:undo|revert|backup|branch|timeline|snapshot|history)");
    private static final Pattern RECOVERY_TABLE = Pattern.compile(
            "(?i)\\b(?:campaign_(?:backup|branch|history|snapshot)|"
                    + "(?:archive|import)_(?:backup|history|snapshot))\\b");

    @Test
    void archiveRoutesOnlyExposePreviewAndOneWayImport() throws Exception {
        Set<String> archiveRoutes = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/dndtool/web"))) {
            for (Path file : files.filter(path -> path.toString().endsWith("Servlet.java")).toList()) {
                Matcher matcher = SERVLET_ROUTE.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String route = matcher.group(1);
                    if (route.contains("/archive")) archiveRoutes.add(route);
                }
            }
        }
        assertEquals(Set.of(
                "/api/host/archive/export",
                "/api/host/archive/import",
                "/api/host/archive/validate",
                "/host/archive"), archiveRoutes);
        assertTrue(archiveRoutes.stream().noneMatch(route -> RECOVERY_NAME.matcher(route).find()));
    }

    @Test
    void publicArchiveServicesHaveNoRecoveryOrBranchCommand() {
        Set<String> confirmationMethods = publicMethodNames(CampaignArchiveConfirmationService.class);
        Set<String> importMethods = publicMethodNames(CampaignArchiveImportRepository.class);

        assertEquals(Set.of("confirm"), confirmationMethods);
        assertEquals(Set.of("importArchive"), importMethods);
        for (String method : confirmationMethods) {
            assertFalse(RECOVERY_NAME.matcher(method).find(), method);
        }
        for (String method : importMethods) {
            assertFalse(RECOVERY_NAME.matcher(method).find(), method);
        }
    }

    @Test
    void formatTwoDraftComponentsCannotActivateReleasedArchiveDispatch() {
        CampaignArchiveFormatDispatcher dispatcher = new CampaignArchiveFormatDispatcher();

        assertTrue(dispatcher.supports(1));
        assertFalse(dispatcher.supports(2));
        assertFalse(Modifier.isPublic(CampaignArchiveV2CharacterStateCodec.class.getModifiers()));
        assertFalse(Modifier.isPublic(CampaignArchiveV2DraftPreview.class.getModifiers()));
        assertFalse(Modifier.isPublic(CampaignArchiveV2DraftImportService.class.getModifiers()));
    }

    @Test
    void archiveSourcesCreateNoFilesystemBackupOrRecoveryTables() throws Exception {
        List<Path> productionFiles = new java.util.ArrayList<>();
        for (Path root : List.of(
                Path.of("src/main/java/com/dndtool"),
                Path.of("src/main/webapp/host"),
                Path.of("src/main/webapp/WEB-INF/views"))) {
            try (Stream<Path> files = Files.walk(root)) {
                productionFiles.addAll(files.filter(Files::isRegularFile)
                        .filter(CampaignArchiveCapabilityBoundaryTest::isArchiveFile)
                        .toList());
            }
        }
        assertFalse(productionFiles.isEmpty());
        for (Path file : productionFiles) {
            String normalizedName = file.getFileName().toString().toLowerCase();
            assertFalse(RECOVERY_NAME.matcher(normalizedName).find(), file.toString());
            String source = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(source.matches("(?s).*\\b(?:Files\\.(?:copy|move|createTempFile)|"
                    + "FileOutputStream|RandomAccessFile)\\b.*"), file.toString());
        }

        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations.filter(Files::isRegularFile).toList()) {
                assertFalse(RECOVERY_NAME.matcher(migration.getFileName().toString()).find(),
                        migration.toString());
                assertFalse(RECOVERY_TABLE.matcher(
                        Files.readString(migration, StandardCharsets.UTF_8)).find(),
                        migration.toString());
            }
        }
    }

    @Test
    void archivePageStatesManualExportBeforeIrreversibleImport() throws Exception {
        String view = Files.readString(
                Path.of("src/main/webapp/WEB-INF/views/host-archive.jsp"),
                StandardCharsets.UTF_8);
        assertTrue(view.contains("不可撤销警告"));
        assertTrue(view.contains("成功导入后不能通过应用撤销"));
        assertTrue(view.contains("执行前请先手工导出需要保留的战役"));
    }

    private static Set<String> publicMethodNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) names.add(method.getName());
        }
        return Set.copyOf(names);
    }

    private static boolean isArchiveFile(Path path) {
        String normalized = path.toString().toLowerCase();
        return normalized.contains("campaignarchive")
                || normalized.contains("hostarchive")
                || normalized.endsWith("host-archive.jsp")
                || normalized.endsWith("host-archive.js");
    }
}
