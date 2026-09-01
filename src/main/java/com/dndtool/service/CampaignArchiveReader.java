package com.dndtool.service;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Strict, side-effect-free boundary for untrusted campaign save bytes. Parsing and all
 * document-local validation finish before a later import service may query or mutate state.
 */
public final class CampaignArchiveReader {
    public static final int MAX_DEPTH = 16;
    private static final int MAX_ARRAY_ELEMENTS = 100_000;
    private static final int MAX_RECENT_EVENTS = 50;
    private static final Pattern INTEGER = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Pattern STABLE_KEY =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private final BuiltinModuleReleaseRegistry releaseRegistry;
    private final CampaignArchiveFormatDispatcher archiveFormats;

    public CampaignArchiveReader() {
        this(new BuiltinModuleReleaseRegistry());
    }

    CampaignArchiveReader(BuiltinModuleReleaseRegistry releaseRegistry) {
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry, "releaseRegistry");
        this.archiveFormats = new CampaignArchiveFormatDispatcher();
    }

    public Result read(byte[] content) {
        if (content == null || content.length == 0) {
            return failure(Status.INVALID_DOCUMENT);
        }
        if (content.length > CampaignSaveFileService.MAX_BYTES) {
            return failure(Status.FILE_TOO_LARGE);
        }

        final String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            return failure(Status.INVALID_UTF8);
        }
        // The version-1 writer emits UTF-8 without a BOM; accepting one creates two encodings.
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
            return failure(Status.INVALID_UTF8);
        }

        try {
            validateSyntaxAndDepth(source);
            CampaignArchiveDocument document = new Parser(source).readDocument();
            validateDocument(document);
            return new Result(Status.READY, document);
        } catch (ReadFailure failure) {
            return failure(failure.status);
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            // Parser details and user-controlled content never cross this stable error boundary.
            return failure(Status.INVALID_JSON);
        }
    }

    /** Reuses the strict reader's complete business validation for an already parsed document. */
    public Status validate(CampaignArchiveDocument document) {
        if (document == null) return Status.INVALID_DOCUMENT;
        try {
            validateDocument(document);
            return Status.READY;
        } catch (ReadFailure failure) {
            return failure.status;
        } catch (RuntimeException malformedObjectGraph) {
            return Status.INVALID_DOCUMENT;
        }
    }

    private static Result failure(Status status) {
        return new Result(status, null);
    }

    private static void validateSyntaxAndDepth(String source) throws IOException, ReadFailure {
        try (JsonReader json = strictReader(source)) {
            if (json.peek() == JsonToken.END_DOCUMENT) {
                throw new ReadFailure(Status.INVALID_DOCUMENT);
            }
            scanValue(json, 0);
            if (json.peek() != JsonToken.END_DOCUMENT) {
                throw new ReadFailure(Status.INVALID_JSON);
            }
        }
    }

    /** Traverses even unknown values so their nesting cannot evade the schema reader. */
    private static void scanValue(JsonReader json, int depth) throws IOException, ReadFailure {
        JsonToken token = json.peek();
        switch (token) {
            case BEGIN_OBJECT -> {
                int childDepth = checkedDepth(depth);
                json.beginObject();
                while (json.hasNext()) {
                    requireUnicodeScalars(json.nextName());
                    scanValue(json, childDepth);
                }
                json.endObject();
            }
            case BEGIN_ARRAY -> {
                int childDepth = checkedDepth(depth);
                json.beginArray();
                int count = 0;
                while (json.hasNext()) {
                    if (++count > MAX_ARRAY_ELEMENTS) {
                        throw new ReadFailure(Status.INVALID_STRUCTURE);
                    }
                    scanValue(json, childDepth);
                }
                json.endArray();
            }
            case STRING -> requireUnicodeScalars(json.nextString());
            case NUMBER -> json.nextString();
            case BOOLEAN -> json.nextBoolean();
            case NULL -> json.nextNull();
            default -> throw new ReadFailure(Status.INVALID_JSON);
        }
    }

    private static int checkedDepth(int depth) throws ReadFailure {
        if (depth >= MAX_DEPTH) {
            throw new ReadFailure(Status.DEPTH_EXCEEDED);
        }
        return depth + 1;
    }

    private static JsonReader strictReader(String source) {
        JsonReader json = new JsonReader(new StringReader(source));
        json.setStrictness(Strictness.STRICT);
        return json;
    }

    private void validateDocument(CampaignArchiveDocument document) throws ReadFailure {
        if (document.campaign() == null || document.module() == null) {
            throw new ReadFailure(Status.INVALID_STRUCTURE);
        }
        BuiltinModuleReleaseRegistry.Resolution resolved = releaseRegistry.resolveReleased(
                document.module().moduleKey(), document.module().releaseVersion());
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            throw new ReadFailure(Status.UNSUPPORTED_RELEASE);
        }
        if (resolved.descriptor().archiveFormatVersion() != document.formatVersion()
                || !archiveFormats.supports(document.formatVersion())) {
            throw new ReadFailure(Status.UNSUPPORTED_FORMAT);
        }
        if (!canonicalUuidV4(document.campaign().campaignKey())
                || !normalizedCampaignName(document.campaign().campaignName())
                || !Set.of("ACTIVE", "ARCHIVED").contains(
                        document.campaign().campaignStatus())
                || !stable(document.module().moduleKey())
                || !validReleaseVersion(document.module().releaseVersion())
                || !SHA256.matcher(document.module().contentSha256()).matches()) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }

        Set<String> characterKeys = new HashSet<>();
        for (var character : document.characters()) {
            if (!canonicalUuidV4(character.characterKey())
                    || !characterKeys.add(character.characterKey())
                    || !Set.of("PC", "NPC").contains(character.characterType())
                    || !normalizedCharacterName(character.characterName())
                    || !Set.of("ACTIVE", "ARCHIVED").contains(character.characterStatus())) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }

        Set<String> fields = new HashSet<>();
        for (var field : document.fields()) {
            requireCharacter(characterKeys, field.characterKey());
            if (!stable(field.fieldKey())
                    || !fields.add(relation(field.characterKey(), field.fieldKey()))
                    || !validField(field)) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }

        Set<String> classes = new HashSet<>();
        for (var classLevel : document.classLevels()) {
            requireCharacter(characterKeys, classLevel.characterKey());
            if (!stable(classLevel.classKey()) || classLevel.level() < 1
                    || classLevel.level() > 20
                    || !classes.add(relation(
                            classLevel.characterKey(), classLevel.classKey()))) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }
        validateProficiencies(document.skillProficiencies(), characterKeys);
        validateProficiencies(document.saveProficiencies(), characterKeys);

        for (var item : document.items()) {
            requireCharacter(characterKeys, item.characterKey());
            boolean moduleItem = "MODULE".equals(item.sourceKind()) && stable(item.itemKey());
            boolean temporaryItem = "TEMPORARY".equals(item.sourceKind())
                    && item.itemKey() == null;
            if ((!moduleItem && !temporaryItem)
                    || !validText(item.itemName(), 1, 80)
                    || !validText(item.itemDescription(), 0, 500)
                    || item.quantity() < 1 || item.quantity() > 999
                    || !Set.of("ACTIVE", "ARCHIVED").contains(item.itemStatus())) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }

        Set<String> maps = new HashSet<>();
        int activeEncounters = 0;
        for (var map : document.maps()) {
            if (!stable(map.mapKey()) || !maps.add(map.mapKey())
                    || !"NODE".equals(map.mapType()) || !stable(map.partyNodeKey())) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
            if (map.encounter() != null) {
                if (++activeEncounters > 1
                        || !"ACTIVE".equals(map.encounter().battleStatus())) {
                    throw new ReadFailure(Status.INVALID_VALUE);
                }
                Set<String> participants = new HashSet<>();
                for (var participant : map.encounter().participants()) {
                    requireCharacter(characterKeys, participant.characterKey());
                    if (!participants.add(participant.characterKey())
                            || !Set.of("ALLY", "ENEMY", "NEUTRAL").contains(
                                    participant.faction())
                            || !stable(participant.nodeKey())) {
                        throw new ReadFailure(Status.INVALID_VALUE);
                    }
                }
            }
        }
        validateEvents(document.recentEvents(), characterKeys);
    }

    private static boolean validField(CampaignArchiveDocument.FieldValue field) {
        int present = (field.textValue() == null ? 0 : 1)
                + (field.integerValue() == null ? 0 : 1)
                + (field.decimalValue() == null ? 0 : 1)
                + (field.booleanValue() == null ? 0 : 1);
        if (present != 1) return false;
        return switch (field.valueType()) {
            case "TEXT" -> validText(field.textValue(), 0, 2_000);
            case "INTEGER" -> field.integerValue() != null;
            case "DECIMAL" -> field.decimalValue() != null
                    && field.decimalValue().precision() <= 38
                    && Math.max(0, field.decimalValue().scale()) <= 18;
            case "BOOLEAN" -> field.booleanValue() != null;
            default -> false;
        };
    }

    private static void validateProficiencies(
            List<CampaignArchiveDocument.Proficiency> values, Set<String> characterKeys)
            throws ReadFailure {
        Set<String> targets = new HashSet<>();
        for (var value : values) {
            requireCharacter(characterKeys, value.characterKey());
            if (!stable(value.targetKey()) || !stable(value.proficiencyKey())
                    || !targets.add(relation(value.characterKey(), value.targetKey()))) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }
    }

    private static void validateEvents(
            List<CampaignArchiveDocument.EventSnapshot> events, Set<String> characterKeys)
            throws ReadFailure {
        if (events.size() > MAX_RECENT_EVENTS) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }
        long previous = 0;
        for (var event : events) {
            if (event.eventSequence() <= previous || !validOptionalText(event.eventText(), 500)) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
            previous = event.eventSequence();
            if (event.check() == null) {
                if (!"NOTE".equals(event.eventType())
                        || event.subjectCharacterKey() != null || event.eventText() == null) {
                    throw new ReadFailure(Status.INVALID_VALUE);
                }
            } else {
                requireCharacter(characterKeys, event.subjectCharacterKey());
                if (!"CHECK_EXECUTED".equals(event.eventType()) || !validCheck(event.check())) {
                    throw new ReadFailure(Status.INVALID_VALUE);
                }
            }
        }
    }

    private static boolean validCheck(CampaignArchiveDocument.CheckSnapshot check) {
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

    private static void requireCharacter(Set<String> characterKeys, String characterKey)
            throws ReadFailure {
        if (characterKey == null || !characterKeys.contains(characterKey)) {
            throw new ReadFailure(Status.INVALID_RELATIONSHIP);
        }
    }

    private static String relation(String left, String right) {
        return left + "\u0000" + right;
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
        if (value == null || !hasOnlyUnicodeScalars(value)
                || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            return false;
        }
        int codePoints = value.codePointCount(0, value.length());
        return codePoints >= minimumCodePoints && codePoints <= maximumCodePoints
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean stable(String value) {
        return value != null && value.length() <= 128 && STABLE_KEY.matcher(value).matches();
    }

    private static boolean validReleaseVersion(String value) {
        return value != null && value.length() <= 64 && RELEASE_VERSION.matcher(value).matches();
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

    private static void requireUnicodeScalars(String value) throws ReadFailure {
        if (!hasOnlyUnicodeScalars(value)) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }
    }

    private static boolean hasOnlyUnicodeScalars(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(++index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    public record Result(Status status, CampaignArchiveDocument document) {
        public Result {
            Objects.requireNonNull(status, "archive read status");
            if ((status == Status.READY) != (document != null)) {
                throw new IllegalArgumentException("Archive document does not match status");
            }
        }
    }

    public enum Status {
        READY,
        INVALID_DOCUMENT,
        FILE_TOO_LARGE,
        INVALID_UTF8,
        INVALID_JSON,
        DEPTH_EXCEEDED,
        DUPLICATE_KEY,
        INVALID_STRUCTURE,
        INVALID_VALUE,
        INVALID_RELATIONSHIP,
        UNSUPPORTED_RELEASE,
        UNSUPPORTED_FORMAT
    }

    private static final class ReadFailure extends Exception {
        private final Status status;

        private ReadFailure(Status status) {
            this.status = status;
        }
    }

    private static final class Parser {
        private final JsonReader json;

        private Parser(String source) {
            json = strictReader(source);
        }

        private CampaignArchiveDocument readDocument() throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            Integer formatVersion = null;
            CampaignArchiveDocument.Campaign campaign = null;
            CampaignArchiveDocument.ModuleReference module = null;
            List<CampaignArchiveDocument.CharacterState> characters = null;
            List<CampaignArchiveDocument.FieldValue> fields = null;
            List<CampaignArchiveDocument.ClassLevel> classLevels = null;
            List<CampaignArchiveDocument.Proficiency> skillProficiencies = null;
            List<CampaignArchiveDocument.Proficiency> saveProficiencies = null;
            List<CampaignArchiveDocument.ItemState> items = null;
            List<CampaignArchiveDocument.MapState> maps = null;
            List<CampaignArchiveDocument.EventSnapshot> recentEvents = null;
            while (json.hasNext()) {
                String name = nextUniqueName(seen);
                switch (name) {
                    case "formatVersion" -> formatVersion = readInt();
                    case "campaign" -> campaign = readCampaign();
                    case "module" -> module = readModule();
                    case "characters" -> characters = readArray(this::readCharacter);
                    case "fields" -> fields = readArray(this::readField);
                    case "classLevels" -> classLevels = readArray(this::readClassLevel);
                    case "skillProficiencies" ->
                            skillProficiencies = readArray(this::readProficiency);
                    case "saveProficiencies" ->
                            saveProficiencies = readArray(this::readProficiency);
                    case "items" -> items = readArray(this::readItem);
                    case "maps" -> maps = readArray(this::readMap);
                    case "recentEvents" -> recentEvents = readArray(this::readEvent);
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "formatVersion", "campaign", "module", "characters", "fields",
                    "classLevels", "skillProficiencies", "saveProficiencies", "items", "maps",
                    "recentEvents");
            if (json.peek() != JsonToken.END_DOCUMENT) {
                throw new ReadFailure(Status.INVALID_JSON);
            }
            return new CampaignArchiveDocument(
                    formatVersion, campaign, module, characters, fields, classLevels,
                    skillProficiencies, saveProficiencies, items, maps, recentEvents);
        }

        private CampaignArchiveDocument.Campaign readCampaign()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String key = null;
            String name = null;
            String status = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "campaignKey" -> key = readString();
                    case "campaignName" -> name = readString();
                    case "campaignStatus" -> status = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "campaignKey", "campaignName", "campaignStatus");
            return new CampaignArchiveDocument.Campaign(key, name, status);
        }

        private CampaignArchiveDocument.ModuleReference readModule()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String key = null;
            String version = null;
            String hash = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "moduleKey" -> key = readString();
                    case "releaseVersion" -> version = readString();
                    case "contentSha256" -> hash = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "moduleKey", "releaseVersion", "contentSha256");
            return new CampaignArchiveDocument.ModuleReference(key, version, hash);
        }

        private CampaignArchiveDocument.CharacterState readCharacter()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String key = null;
            String type = null;
            String name = null;
            String status = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> key = readString();
                    case "characterType" -> type = readString();
                    case "characterName" -> name = readString();
                    case "characterStatus" -> status = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "characterType", "characterName",
                    "characterStatus");
            return new CampaignArchiveDocument.CharacterState(key, type, name, status);
        }

        private CampaignArchiveDocument.FieldValue readField()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String characterKey = null;
            String fieldKey = null;
            String valueType = null;
            Scalar value = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> characterKey = readString();
                    case "fieldKey" -> fieldKey = readString();
                    case "valueType" -> valueType = readString();
                    case "value" -> value = readScalar();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "fieldKey", "valueType", "value");
            return typedField(characterKey, fieldKey, valueType, value);
        }

        private CampaignArchiveDocument.FieldValue typedField(
                String characterKey, String fieldKey, String valueType, Scalar value)
                throws ReadFailure {
            if (value == null) throw new ReadFailure(Status.INVALID_STRUCTURE);
            return switch (valueType) {
                case "TEXT" -> {
                    if (value.kind != ScalarKind.STRING) invalidValue();
                    yield new CampaignArchiveDocument.FieldValue(
                            characterKey, fieldKey, valueType, value.lexical, null, null, null);
                }
                case "INTEGER" -> {
                    if (value.kind != ScalarKind.NUMBER) invalidValue();
                    yield new CampaignArchiveDocument.FieldValue(
                            characterKey, fieldKey, valueType, null,
                            parseLong(value.lexical), null, null);
                }
                case "DECIMAL" -> {
                    if (value.kind != ScalarKind.NUMBER) invalidValue();
                    BigDecimal decimal;
                    try {
                        decimal = new BigDecimal(value.lexical);
                    } catch (NumberFormatException exception) {
                        throw new ReadFailure(Status.INVALID_VALUE);
                    }
                    yield new CampaignArchiveDocument.FieldValue(
                            characterKey, fieldKey, valueType, null, null, decimal, null);
                }
                case "BOOLEAN" -> {
                    if (value.kind != ScalarKind.BOOLEAN) invalidValue();
                    yield new CampaignArchiveDocument.FieldValue(
                            characterKey, fieldKey, valueType, null, null, null, value.bool);
                }
                default -> throw new ReadFailure(Status.INVALID_VALUE);
            };
        }

        private CampaignArchiveDocument.ClassLevel readClassLevel()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String characterKey = null;
            String classKey = null;
            Integer level = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> characterKey = readString();
                    case "classKey" -> classKey = readString();
                    case "level" -> level = readInt();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "classKey", "level");
            return new CampaignArchiveDocument.ClassLevel(characterKey, classKey, level);
        }

        private CampaignArchiveDocument.Proficiency readProficiency()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String characterKey = null;
            String targetKey = null;
            String proficiencyKey = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> characterKey = readString();
                    case "targetKey" -> targetKey = readString();
                    case "proficiencyKey" -> proficiencyKey = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "targetKey", "proficiencyKey");
            return new CampaignArchiveDocument.Proficiency(
                    characterKey, targetKey, proficiencyKey);
        }

        private CampaignArchiveDocument.ItemState readItem()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String characterKey = null;
            String sourceKind = null;
            String itemKey = null;
            String itemName = null;
            String description = null;
            Integer quantity = null;
            String status = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> characterKey = readString();
                    case "sourceKind" -> sourceKind = readString();
                    case "itemKey" -> itemKey = readNullableString();
                    case "itemName" -> itemName = readString();
                    case "itemDescription" -> description = readString();
                    case "quantity" -> quantity = readInt();
                    case "itemStatus" -> status = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "sourceKind", "itemKey", "itemName",
                    "itemDescription", "quantity", "itemStatus");
            return new CampaignArchiveDocument.ItemState(
                    characterKey, sourceKind, itemKey, itemName, description, quantity, status);
        }

        private CampaignArchiveDocument.MapState readMap() throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String mapKey = null;
            String mapType = null;
            String partyNodeKey = null;
            CampaignArchiveDocument.Encounter encounter = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "mapKey" -> mapKey = readString();
                    case "mapType" -> mapType = readString();
                    case "partyNodeKey" -> partyNodeKey = readString();
                    case "encounter" -> encounter = readNullableEncounter();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "mapKey", "mapType", "partyNodeKey", "encounter");
            return new CampaignArchiveDocument.MapState(
                    mapKey, mapType, partyNodeKey, encounter);
        }

        private CampaignArchiveDocument.Encounter readNullableEncounter()
                throws IOException, ReadFailure {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return null;
            }
            beginObject();
            Set<String> seen = new HashSet<>();
            String status = null;
            List<CampaignArchiveDocument.Participant> participants = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "battleStatus" -> status = readString();
                    case "participants" -> participants = readArray(this::readParticipant);
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "battleStatus", "participants");
            return new CampaignArchiveDocument.Encounter(status, participants);
        }

        private CampaignArchiveDocument.Participant readParticipant()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            String characterKey = null;
            String faction = null;
            String nodeKey = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "characterKey" -> characterKey = readString();
                    case "faction" -> faction = readString();
                    case "nodeKey" -> nodeKey = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "characterKey", "faction", "nodeKey");
            return new CampaignArchiveDocument.Participant(characterKey, faction, nodeKey);
        }

        private CampaignArchiveDocument.EventSnapshot readEvent()
                throws IOException, ReadFailure {
            beginObject();
            Set<String> seen = new HashSet<>();
            Long sequence = null;
            String type = null;
            String subject = null;
            String text = null;
            CampaignArchiveDocument.CheckSnapshot check = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "eventSequence" -> sequence = readLong();
                    case "eventType" -> type = readString();
                    case "subjectCharacterKey" -> subject = readNullableString();
                    case "eventText" -> text = readNullableString();
                    case "check" -> check = readNullableCheck();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "eventSequence", "eventType", "subjectCharacterKey",
                    "eventText", "check");
            return new CampaignArchiveDocument.EventSnapshot(sequence, type, subject, text, check);
        }

        private CampaignArchiveDocument.CheckSnapshot readNullableCheck()
                throws IOException, ReadFailure {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return null;
            }
            beginObject();
            Set<String> seen = new HashSet<>();
            String eventKey = null;
            String checkKey = null;
            String rollModeKey = null;
            String modifierSourceKey = null;
            String manualName = null;
            Integer modifierValue = null;
            Integer totalValue = null;
            Integer difficultyClass = null;
            String result = null;
            while (json.hasNext()) {
                switch (nextUniqueName(seen)) {
                    case "eventKey" -> eventKey = readNullableString();
                    case "checkKey" -> checkKey = readString();
                    case "rollModeKey" -> rollModeKey = readString();
                    case "modifierSourceKey" -> modifierSourceKey = readNullableString();
                    case "manualName" -> manualName = readNullableString();
                    case "modifierValue" -> modifierValue = readInt();
                    case "totalValue" -> totalValue = readInt();
                    case "difficultyClass" -> difficultyClass = readInt();
                    case "checkResult" -> result = readString();
                    default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
            }
            json.endObject();
            requireFields(seen, "eventKey", "checkKey", "rollModeKey", "modifierSourceKey",
                    "manualName", "modifierValue", "totalValue", "difficultyClass",
                    "checkResult");
            return new CampaignArchiveDocument.CheckSnapshot(
                    eventKey, checkKey, rollModeKey, modifierSourceKey, manualName,
                    modifierValue, totalValue, difficultyClass, result);
        }

        private <T> List<T> readArray(ItemReader<T> itemReader)
                throws IOException, ReadFailure {
            if (json.peek() != JsonToken.BEGIN_ARRAY) {
                throw new ReadFailure(Status.INVALID_STRUCTURE);
            }
            json.beginArray();
            List<T> values = new ArrayList<>();
            while (json.hasNext()) {
                if (values.size() >= MAX_ARRAY_ELEMENTS) {
                    throw new ReadFailure(Status.INVALID_STRUCTURE);
                }
                values.add(itemReader.read());
            }
            json.endArray();
            return List.copyOf(values);
        }

        private Scalar readScalar() throws IOException, ReadFailure {
            return switch (json.peek()) {
                case STRING -> new Scalar(ScalarKind.STRING, readString(), null);
                case NUMBER -> new Scalar(ScalarKind.NUMBER, json.nextString(), null);
                case BOOLEAN -> new Scalar(ScalarKind.BOOLEAN, null, json.nextBoolean());
                default -> throw new ReadFailure(Status.INVALID_STRUCTURE);
            };
        }

        private String readString() throws IOException, ReadFailure {
            if (json.peek() != JsonToken.STRING) {
                throw new ReadFailure(Status.INVALID_STRUCTURE);
            }
            String value = json.nextString();
            requireUnicodeScalars(value);
            return value;
        }

        private String readNullableString() throws IOException, ReadFailure {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return null;
            }
            return readString();
        }

        private int readInt() throws IOException, ReadFailure {
            long value = readLong();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
            return (int) value;
        }

        private long readLong() throws IOException, ReadFailure {
            if (json.peek() != JsonToken.NUMBER) {
                throw new ReadFailure(Status.INVALID_STRUCTURE);
            }
            return parseLong(json.nextString());
        }

        private static long parseLong(String lexical) throws ReadFailure {
            if (!INTEGER.matcher(lexical).matches()) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
            try {
                return Long.parseLong(lexical);
            } catch (NumberFormatException exception) {
                throw new ReadFailure(Status.INVALID_VALUE);
            }
        }

        private void beginObject() throws IOException, ReadFailure {
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                throw new ReadFailure(Status.INVALID_STRUCTURE);
            }
            json.beginObject();
        }

        private String nextUniqueName(Set<String> seen) throws IOException, ReadFailure {
            String name = json.nextName();
            requireUnicodeScalars(name);
            if (!seen.add(name)) {
                throw new ReadFailure(Status.DUPLICATE_KEY);
            }
            return name;
        }

        private static void requireFields(Set<String> seen, String... expected)
                throws ReadFailure {
            if (!seen.equals(Set.of(expected))) {
                throw new ReadFailure(Status.INVALID_STRUCTURE);
            }
        }

        private static void invalidValue() throws ReadFailure {
            throw new ReadFailure(Status.INVALID_VALUE);
        }

        @FunctionalInterface
        private interface ItemReader<T> {
            T read() throws IOException, ReadFailure;
        }
    }

    private enum ScalarKind {
        STRING,
        NUMBER,
        BOOLEAN
    }

    private record Scalar(ScalarKind kind, String lexical, Boolean bool) {
    }
}
