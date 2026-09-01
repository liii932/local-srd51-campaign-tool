package com.dndtool.service;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.CampaignArchiveRepository;
import com.dndtool.persistence.CampaignArchiveRepository.CheckSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.EventSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.FieldValue;
import com.dndtool.persistence.CampaignArchiveRepository.Snapshot;
import com.dndtool.service.CampaignSaveFileService.ErrorCode;
import com.dndtool.service.CampaignSaveFileService.ExportException;
import com.dndtool.service.CampaignSaveFileService.ExportFile;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates and exports one server-owned campaign as the version-1 archive DTO. */
public final class CampaignArchiveExportService {
    public static final int FORMAT_VERSION = 1;
    private static final int MAX_RECENT_EVENTS = 50;
    private static final Pattern STABLE_KEY =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final CampaignArchiveRepository repository;
    private final CampaignSaveFileService fileService;
    private final BuiltinModuleReleaseRegistry releaseRegistry;
    private final CampaignArchiveFormatDispatcher archiveFormats;

    public CampaignArchiveExportService(CampaignArchiveRepository repository) {
        this(repository, new CampaignSaveFileService(), new BuiltinModuleReleaseRegistry());
    }

    CampaignArchiveExportService(
            CampaignArchiveRepository repository, CampaignSaveFileService fileService) {
        this(repository, fileService, new BuiltinModuleReleaseRegistry());
    }

    CampaignArchiveExportService(
            CampaignArchiveRepository repository,
            CampaignSaveFileService fileService,
            BuiltinModuleReleaseRegistry releaseRegistry) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry, "releaseRegistry");
        this.archiveFormats = new CampaignArchiveFormatDispatcher();
    }

    public Result export(String campaignKey) throws SQLException {
        if (!canonicalUuidV4(campaignKey)) return new Result(Status.INVALID_REQUEST, null);
        Optional<Snapshot> found = repository.findByCampaignKey(campaignKey);
        if (found.isEmpty()) return new Result(Status.NOT_FOUND, null);
        Snapshot snapshot = found.orElseThrow();
        BuiltinModuleReleaseRegistry.Descriptor descriptor = descriptor(snapshot);
        if (descriptor == null || !archiveFormats.supports(descriptor.archiveFormatVersion())
                || !valid(snapshot, campaignKey, descriptor)) {
            return new Result(Status.INVALID_STATE, null);
        }

        try {
            return new Result(
                    Status.READY,
                    fileService.encode(archiveFormats.write(
                            snapshot, descriptor.archiveFormatVersion())));
        } catch (ExportException exception) {
            return new Result(
                    exception.code() == ErrorCode.EXPORT_TOO_LARGE
                            ? Status.EXPORT_TOO_LARGE : Status.INVALID_STATE,
                    null);
        } catch (IllegalStateException exception) {
            return new Result(Status.INVALID_STATE, null);
        }
    }

    private BuiltinModuleReleaseRegistry.Descriptor descriptor(Snapshot snapshot) {
        if (snapshot == null || snapshot.module() == null) return null;
        BuiltinModuleReleaseRegistry.Resolution resolved = releaseRegistry.resolveReleased(
                snapshot.module().frozenModuleKey(),
                snapshot.module().frozenReleaseVersion());
        return resolved.status() == BuiltinModuleReleaseRegistry.ResolutionStatus.READY
                ? resolved.descriptor() : null;
    }

    private static boolean valid(
            Snapshot snapshot,
            String requestedKey,
            BuiltinModuleReleaseRegistry.Descriptor descriptor) {
        if (snapshot == null || snapshot.campaign() == null || snapshot.module() == null
                || !requestedKey.equals(snapshot.campaign().campaignKey())
                || !canonicalUuidV4(snapshot.campaign().campaignKey())
                || !normalizedCampaignName(snapshot.campaign().campaignName())
                || !Set.of("ACTIVE", "ARCHIVED").contains(
                        snapshot.campaign().campaignStatus())
                || !validModule(snapshot.module(), descriptor)) {
            return false;
        }

        Set<String> characterKeys = new HashSet<>();
        for (var character : snapshot.characters()) {
            if (character == null
                    || !canonicalUuidV4(character.characterKey())
                    || !characterKeys.add(character.characterKey())
                    || !Set.of("PC", "NPC").contains(character.characterType())
                    || !normalizedCharacterName(character.characterName())
                    || !Set.of("ACTIVE", "ARCHIVED").contains(character.characterStatus())
                    || !snapshot.module().frozenModuleKey().equals(
                            character.savedModuleKey())
                    || !snapshot.module().frozenReleaseVersion().equals(
                            character.savedReleaseVersion())
                    || !snapshot.module().frozenContentSha256().equals(
                            character.savedContentSha256())) {
                return false;
            }
        }

        Set<String> fields = new HashSet<>();
        for (FieldValue field : snapshot.fields()) {
            if (field == null || !characterKeys.contains(field.characterKey())
                    || !stable(field.fieldKey())
                    || !fields.add(field.characterKey() + "\u0000" + field.fieldKey())
                    || !validField(field)) {
                return false;
            }
        }

        Set<String> classes = new HashSet<>();
        for (var classLevel : snapshot.classLevels()) {
            if (classLevel == null || !characterKeys.contains(classLevel.characterKey())
                    || !stable(classLevel.classKey())
                    || classLevel.level() < 1 || classLevel.level() > 20
                    || !classes.add(classLevel.characterKey() + "\u0000"
                            + classLevel.classKey())) {
                return false;
            }
        }
        if (!validProficiencies(snapshot.skillProficiencies(), characterKeys)
                || !validProficiencies(snapshot.saveProficiencies(), characterKeys)) {
            return false;
        }

        for (var item : snapshot.items()) {
            boolean moduleItem = item != null && "MODULE".equals(item.sourceKind())
                    && stable(item.itemKey());
            boolean temporaryItem = item != null && "TEMPORARY".equals(item.sourceKind())
                    && item.itemKey() == null;
            if (item == null || !characterKeys.contains(item.characterKey())
                    || (!moduleItem && !temporaryItem)
                    || !validText(item.itemName(), 1, 80)
                    || !validText(item.itemDescription(), 0, 500)
                    || item.quantity() < 1 || item.quantity() > 999
                    || !Set.of("ACTIVE", "ARCHIVED").contains(item.itemStatus())) {
                return false;
            }
        }

        Set<String> maps = new HashSet<>();
        for (var map : snapshot.maps()) {
            if (map == null || !stable(map.mapKey()) || !maps.add(map.mapKey())
                    || !"NODE".equals(map.mapType()) || !stable(map.partyNodeKey())) {
                return false;
            }
            if (map.encounter() != null) {
                if (!"ACTIVE".equals(map.encounter().battleStatus())) return false;
                Set<String> participants = new HashSet<>();
                for (var participant : map.encounter().participants()) {
                    if (participant == null
                            || !characterKeys.contains(participant.characterKey())
                            || !participants.add(participant.characterKey())
                            || !Set.of("ALLY", "ENEMY", "NEUTRAL").contains(
                                    participant.faction())
                            || !stable(participant.nodeKey())) {
                        return false;
                    }
                }
            }
        }
        return validEvents(snapshot.recentEvents(), characterKeys);
    }

    private static boolean validModule(
            CampaignArchiveRepository.ModuleBinding module,
            BuiltinModuleReleaseRegistry.Descriptor descriptor) {
        return descriptor.moduleKey().equals(module.frozenModuleKey())
                && descriptor.releaseVersion().equals(module.frozenReleaseVersion())
                && descriptor.contentSha256().equals(module.frozenContentSha256())
                && module.frozenModuleKey().equals(module.releaseModuleKey())
                && module.frozenReleaseVersion().equals(module.releaseVersion())
                && module.frozenContentSha256().equals(module.releaseContentSha256())
                && module.canonicalFormatVersion() == descriptor.canonicalFormatVersion()
                && descriptor.hashAlgorithm().equals(module.hashAlgorithm())
                && "RELEASED".equals(module.releaseStatus());
    }

    private static boolean validField(FieldValue field) {
        int present = (field.textValue() == null ? 0 : 1)
                + (field.integerValue() == null ? 0 : 1)
                + (field.decimalValue() == null ? 0 : 1)
                + (field.booleanValue() == null ? 0 : 1);
        if (present != 1) return false;
        return switch (field.valueType()) {
            case "TEXT" -> validText(field.textValue(), 0, 2000);
            case "INTEGER" -> field.integerValue() != null;
            case "DECIMAL" -> validDecimal(field.decimalValue());
            case "BOOLEAN" -> field.booleanValue() != null;
            default -> false;
        };
    }

    private static boolean validDecimal(BigDecimal value) {
        return value != null && value.precision() <= 38 && Math.max(0, value.scale()) <= 18;
    }

    private static boolean validProficiencies(
            java.util.List<CampaignArchiveRepository.Proficiency> values,
            Set<String> characterKeys) {
        Set<String> targets = new HashSet<>();
        for (var value : values) {
            if (value == null || !characterKeys.contains(value.characterKey())
                    || !stable(value.targetKey()) || !stable(value.proficiencyKey())
                    || !targets.add(value.characterKey() + "\u0000" + value.targetKey())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validEvents(
            java.util.List<EventSnapshot> events, Set<String> characterKeys) {
        if (events.size() > MAX_RECENT_EVENTS) return false;
        long previous = 0;
        for (EventSnapshot event : events) {
            if (event == null || event.eventSequence() <= previous
                    || !validOptionalText(event.eventText(), 500)) {
                return false;
            }
            previous = event.eventSequence();
            if (event.check() == null) {
                if (!"NOTE".equals(event.eventType())
                        || event.subjectCharacterKey() != null
                        || event.eventText() == null) {
                    return false;
                }
            } else if (!"CHECK_EXECUTED".equals(event.eventType())
                    || !characterKeys.contains(event.subjectCharacterKey())
                    || !validCheck(event.check())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validCheck(CheckSnapshot check) {
        boolean ordinary = stable(check.modifierSourceKey()) && check.manualName() == null;
        boolean manual = check.modifierSourceKey() == null
                && validText(check.manualName(), 1, 80);
        return (check.eventKey() == null || stable(check.eventKey()))
                && stable(check.checkKey()) && stable(check.rollModeKey())
                && (ordinary || manual)
                && check.modifierValue() >= -99 && check.modifierValue() <= 99
                && check.totalValue() >= -98 && check.totalValue() <= 119
                && check.difficultyClass() >= 0 && check.difficultyClass() <= 60
                && Set.of("SUCCESS", "FAILURE").contains(check.checkResult())
                && ("SUCCESS".equals(check.checkResult())
                        == (check.totalValue() >= check.difficultyClass()));
    }

    private static boolean normalizedCampaignName(String value) {
        try {
            return CampaignCreationService.normalizeName(value).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean normalizedCharacterName(String value) {
        try {
            return CharacterNamePolicy.normalize(value).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validOptionalText(String value, int maximumCodePoints) {
        return value == null || validText(value, 0, maximumCodePoints);
    }

    private static boolean validText(String value, int minimumCodePoints, int maximumCodePoints) {
        if (value == null || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) return false;
        int codePoints = value.codePointCount(0, value.length());
        return codePoints >= minimumCodePoints && codePoints <= maximumCodePoints
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean stable(String value) {
        return value != null && STABLE_KEY.matcher(value).matches();
    }

    private static boolean canonicalUuidV4(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record Result(Status status, ExportFile file) {
        public Result {
            Objects.requireNonNull(status, "archive status");
            if ((status == Status.READY) != (file != null)) {
                throw new IllegalArgumentException("Archive file does not match status");
            }
        }
    }

    public enum Status {
        READY,
        INVALID_REQUEST,
        NOT_FOUND,
        INVALID_STATE,
        EXPORT_TOO_LARGE
    }
}
