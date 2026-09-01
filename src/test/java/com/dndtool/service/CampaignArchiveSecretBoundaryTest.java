package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CampaignArchiveRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Prevents request secrets, machine configuration, and deferred public data entering archives. */
final class CampaignArchiveSecretBoundaryTest {
    private static final List<Class<?>> ARCHIVE_RECORDS = List.of(
            CampaignArchiveRepository.Snapshot.class,
            CampaignArchiveRepository.Campaign.class,
            CampaignArchiveRepository.ModuleBinding.class,
            CampaignArchiveRepository.CharacterState.class,
            CampaignArchiveRepository.FieldValue.class,
            CampaignArchiveRepository.ClassLevel.class,
            CampaignArchiveRepository.Proficiency.class,
            CampaignArchiveRepository.ItemState.class,
            CampaignArchiveRepository.MapState.class,
            CampaignArchiveRepository.Encounter.class,
            CampaignArchiveRepository.Participant.class,
            CampaignArchiveRepository.EventSnapshot.class,
            CampaignArchiveRepository.CheckSnapshot.class);
    private static final List<Path> PRODUCTION_PATH = List.of(
            Path.of("src/main/java/com/dndtool/persistence/CampaignArchiveRepository.java"),
            Path.of("src/main/java/com/dndtool/persistence/JdbcCampaignArchiveRepository.java"),
            Path.of("src/main/java/com/dndtool/service/CampaignArchiveExportService.java"),
            Path.of("src/main/java/com/dndtool/service/CampaignArchiveJsonWriter.java"),
            Path.of("src/main/java/com/dndtool/service/CampaignSaveFileService.java"));

    @Test
    void dtoModelHasNoRequestSecretMachineOrDeferredPublicComponents() {
        Set<String> forbiddenFragments = Set.of(
                "session", "cookie", "csrf", "password", "credential", "secret",
                "privatekey", "certificate", "tomcat", "localpath", "database",
                "jdbc", "public", "player", "owner", "approval", "networkepoch",
                "hoststateepoch");

        for (Class<?> type : ARCHIVE_RECORDS) {
            assertTrue(type.isRecord(), type.getName());
            for (var component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                assertFalse(forbiddenFragments.stream().anyMatch(name::contains),
                        type.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void productionArchivePathCannotReadRequestsEnvironmentJndiOrLocalFiles()
            throws Exception {
        String source = readProductionPath().toLowerCase();
        for (String forbiddenCode : List.of(
                "jakarta.servlet", "javax.servlet", "httpsession", ".getsession(",
                ".getheader(", ".getparameter(", "system.getenv(",
                "system.getproperty(", "java.nio.file", "java.io.file",
                "fileinputstream", "filereader", "initialcontext", "javax.naming",
                "jakarta.naming", ".lookup(", "dnd_db_password", "catalina_",
                "server.xml", "context.xml", "setenv", "defaults-extra-file",
                "begin private key", "begin rsa private key", "d:\\")) {
            assertFalse(source.contains(forbiddenCode), forbiddenCode);
        }
    }

    private static String readProductionPath() throws Exception {
        StringBuilder source = new StringBuilder();
        for (Path path : PRODUCTION_PATH) {
            source.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
        }
        return source.toString();
    }
}
