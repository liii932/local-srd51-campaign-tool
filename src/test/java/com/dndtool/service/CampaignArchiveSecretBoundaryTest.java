package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.CharacterProficiencyGrant;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignCodec;
import com.dndtool.service.storage.CampaignData;
import com.dndtool.service.storage.CampaignDomainData;
import com.dndtool.service.storage.CampaignJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Protects the internal character projection and the portable file from request and machine data. */
final class CampaignArchiveSecretBoundaryTest {
    private static final List<Path> CODEC_SOURCES = List.of(
            Path.of("src/main/java/com/dndtool/service/CampaignArchiveV2CharacterStateCodec.java"),
            Path.of("src/main/java/com/dndtool/service/CampaignArchiveV2CharacterStateValidator.java"),
            Path.of("src/main/java/com/dndtool/service/CampaignArchiveV2DraftPreview.java"),
            Path.of("src/main/java/com/dndtool/service/storage/CampaignCodec.java"),
            Path.of("src/main/java/com/dndtool/service/storage/CampaignDomainCodec.java"));

    @Test
    void draftCharacterRecordsHaveNoRequestSecretMachineOrDeferredPublicComponents() {
        Set<String> forbiddenFragments = Set.of(
                "session", "cookie", "csrf", "password", "credential", "secret",
                "privatekey", "certificate", "tomcat", "localpath", "database",
                "jdbc", "public", "player", "owner", "approval", "networkepoch",
                "hoststateepoch");

        var records = Stream.concat(
                Stream.<Class<?>>of(CampaignArchiveV2CharacterState.class, CharacterProficiencyGrant.class),
                Arrays.stream(CampaignArchiveV2CharacterState.class.getDeclaredClasses()).filter(Class::isRecord))
                .toList();
        for (Class<?> type : records) {
            assertTrue(type.isRecord(), type.getName());
            for (var component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertFalse(forbiddenFragments.stream().anyMatch(name::contains),
                        type.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void portableFileProjectsOnlyStableCampaignAndParticipationFields() {
        var snapshot = new CampaignData.Snapshot("11111111-1111-4111-8111-111111111111", "便携战役",
                CampaignDomainData.State.initial(BundledCampaignRules.projection()));
        var document = new CampaignData.Document("22222222-2222-4222-8222-222222222222", snapshot);
        var file = CampaignJson.read(new CampaignCodec().encode(document));
        assertEquals(Set.of("applicationModel", "campaign", "domains", "fileKey", "fileKind",
                "formatVersion", "integrity"), file.keySet());
        assertEquals(Set.of("campaignKey", "eventTail", "name"), file.getAsJsonObject("campaign").keySet());
        var domains = file.getAsJsonObject("domains");
        assertEquals(Set.of("type", "material", "characters", "combats", "nodes", "positions", "items",
                "roots", "manual", "participation", "space"), domains.keySet());
        assertEquals(Set.of("type", "members", "controls", "shares", "exploration"),
                domains.getAsJsonObject("participation").keySet());
    }

    @Test
    void codecsCannotReadRequestsEnvironmentJndiOrLocalFiles() throws Exception {
        for (Path path : CODEC_SOURCES) {
            String source = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String forbiddenCode : List.of(
                    "jakarta.servlet", "javax.servlet", "httpsession", ".getsession(",
                    ".getheader(", ".getparameter(", "system.getenv(",
                    "system.getproperty(", "java.nio.file", "java.io.file",
                    "fileinputstream", "filereader", "initialcontext", "javax.naming",
                    "jakarta.naming", ".lookup(", "dnd_db_password", "catalina_",
                    "server.xml", "context.xml", "setenv", "defaults-extra-file",
                    "begin private key", "begin rsa private key", "d:\\")) {
                assertFalse(source.contains(forbiddenCode), path + " contains " + forbiddenCode);
            }
        }
    }
}
