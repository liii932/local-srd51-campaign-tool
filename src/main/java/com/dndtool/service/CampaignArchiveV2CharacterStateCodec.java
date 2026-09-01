package com.dndtool.service;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit JSON codec for the unactivated archive-format-2 character-rules projection. */
final class CampaignArchiveV2CharacterStateCodec {
    private static final int MAX_BYTES = CampaignSaveFileService.MAX_BYTES;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_ARRAY_ELEMENTS = 100_000;
    private static final Pattern INTEGER = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "archiveFormatVersion", "eventTail", "stateEvents", "creationSnapshots",
            "creationSelections", "resources", "classLevels", "subclasses", "features",
            "featureChoices", "feats", "multiclassProficiencies");

    private CampaignArchiveV2CharacterStateCodec() {
    }

    static byte[] write(CampaignArchiveV2CharacterState input, Set<String> characterKeys) {
        CampaignArchiveV2CharacterState state =
                CampaignArchiveV2CharacterStateValidator.normalize(input, characterKeys);
        JsonObject root = new JsonObject();
        root.addProperty("archiveFormatVersion", state.archiveFormatVersion());
        root.addProperty("eventTail", state.eventTail());
        root.add("stateEvents", array(state.stateEvents(), value -> object(
                "eventSequence", value.eventSequence(),
                "eventType", value.eventType(),
                "subjectCharacterKey", value.subjectCharacterKey())));
        root.add("creationSnapshots", array(state.creationSnapshots(), value -> {
            JsonObject object = object(
                    "characterKey", value.characterKey(),
                    "createdEventSequence", value.createdEventSequence(),
                    "previewDigestSha256", value.previewDigestSha256(),
                    "requestDigestSha256", value.requestDigestSha256(),
                    "abilityMethodKey", value.abilityMethodKey(),
                    "raceKey", value.raceKey(),
                    "subraceKey", value.subraceKey(),
                    "backgroundKey", value.backgroundKey(),
                    "classKey", value.classKey());
            object.add("abilities", array(value.abilities(), ability -> object(
                    "abilityKey", ability.abilityKey(),
                    "baseScore", ability.baseScore(),
                    "finalScore", ability.finalScore())));
            object.addProperty("maximumHitPoints", value.maximumHitPoints());
            return object;
        }));
        root.add("creationSelections", array(state.creationSelections(), value -> object(
                "characterKey", value.characterKey(),
                "selectionKind", value.selectionKind(),
                "selectionOrder", value.selectionOrder(),
                "selectionKey", value.selectionKey())));
        root.add("resources", array(state.resources(), value -> object(
                "characterKey", value.characterKey(),
                "resourceKey", value.resourceKey(),
                "currentValue", value.currentValue(),
                "maximumValue", value.maximumValue(),
                "unlimited", value.unlimited())));
        root.add("classLevels", array(state.classLevels(), value -> object(
                "characterKey", value.characterKey(),
                "classKey", value.classKey(),
                "classLevel", value.classLevel())));
        root.add("subclasses", array(state.subclasses(), value -> object(
                "characterKey", value.characterKey(),
                "classKey", value.classKey(),
                "subclassKey", value.subclassKey(),
                "selectedAtClassLevel", value.selectedAtClassLevel(),
                "acquiredEventSequence", value.acquiredEventSequence())));
        root.add("features", array(state.features(), value -> object(
                "characterKey", value.characterKey(),
                "featureKey", value.featureKey(),
                "acquiredAtClassLevel", value.acquiredAtClassLevel(),
                "executionMode", value.executionMode(),
                "executionAlgorithm", value.executionAlgorithm(),
                "acquiredEventSequence", value.acquiredEventSequence())));
        root.add("featureChoices", array(state.featureChoices(), value -> object(
                "characterKey", value.characterKey(),
                "sourceFeatureKey", value.sourceFeatureKey(),
                "choiceOrder", value.choiceOrder(),
                "choiceType", value.choiceType(),
                "choiceKey", value.choiceKey(),
                "acquiredEventSequence", value.acquiredEventSequence())));
        root.add("feats", array(state.feats(), value -> object(
                "characterKey", value.characterKey(),
                "featKey", value.featKey(),
                "acquiredEventSequence", value.acquiredEventSequence())));
        root.add("multiclassProficiencies", array(
                state.multiclassProficiencies(), value -> object(
                        "characterKey", value.characterKey(),
                        "classKey", value.classKey(),
                        "proficiencyKey", value.proficiencyKey(),
                        "acquiredEventSequence", value.acquiredEventSequence())));
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Result read(byte[] content, Set<String> characterKeys) {
        if (content == null || content.length == 0) return Result.failure(Status.INVALID_DOCUMENT);
        if (content.length > MAX_BYTES) return Result.failure(Status.FILE_TOO_LARGE);
        final String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return Result.failure(Status.INVALID_UTF8);
        }
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
            return Result.failure(Status.INVALID_UTF8);
        }
        try {
            scan(source);
            JsonElement parsed = JsonParser.parseString(source);
            JsonObject root = requiredObject(parsed);
            requireFields(root, ROOT_FIELDS);
            CampaignArchiveV2CharacterState decoded = new CampaignArchiveV2CharacterState(
                    intValue(root, "archiveFormatVersion"),
                    longValue(root, "eventTail"),
                    values(root, "stateEvents", value -> new StateEvent(
                            longValue(value, "eventSequence"),
                            string(value, "eventType"),
                            string(value, "subjectCharacterKey")),
                            "eventSequence", "eventType", "subjectCharacterKey"),
                    values(root, "creationSnapshots", CampaignArchiveV2CharacterStateCodec::snapshot,
                            "characterKey", "createdEventSequence", "previewDigestSha256",
                            "requestDigestSha256", "abilityMethodKey", "raceKey", "subraceKey",
                            "backgroundKey", "classKey", "abilities", "maximumHitPoints"),
                    values(root, "creationSelections", value -> new CreationSelection(
                            string(value, "characterKey"), string(value, "selectionKind"),
                            intValue(value, "selectionOrder"), string(value, "selectionKey")),
                            "characterKey", "selectionKind", "selectionOrder", "selectionKey"),
                    values(root, "resources", value -> new ResourceState(
                            string(value, "characterKey"), string(value, "resourceKey"),
                            longValue(value, "currentValue"), longValue(value, "maximumValue"),
                            bool(value, "unlimited")),
                            "characterKey", "resourceKey", "currentValue", "maximumValue",
                            "unlimited"),
                    values(root, "classLevels", value -> new ClassLevel(
                            string(value, "characterKey"), string(value, "classKey"),
                            intValue(value, "classLevel")),
                            "characterKey", "classKey", "classLevel"),
                    values(root, "subclasses", value -> new SubclassState(
                            string(value, "characterKey"), string(value, "classKey"),
                            string(value, "subclassKey"), intValue(value, "selectedAtClassLevel"),
                            longValue(value, "acquiredEventSequence")),
                            "characterKey", "classKey", "subclassKey", "selectedAtClassLevel",
                            "acquiredEventSequence"),
                    values(root, "features", value -> new FeatureState(
                            string(value, "characterKey"), string(value, "featureKey"),
                            intValue(value, "acquiredAtClassLevel"), string(value, "executionMode"),
                            string(value, "executionAlgorithm"),
                            longValue(value, "acquiredEventSequence")),
                            "characterKey", "featureKey", "acquiredAtClassLevel", "executionMode",
                            "executionAlgorithm", "acquiredEventSequence"),
                    values(root, "featureChoices", value -> new FeatureChoice(
                            string(value, "characterKey"), string(value, "sourceFeatureKey"),
                            intValue(value, "choiceOrder"), string(value, "choiceType"),
                            string(value, "choiceKey"), longValue(value, "acquiredEventSequence")),
                            "characterKey", "sourceFeatureKey", "choiceOrder", "choiceType",
                            "choiceKey", "acquiredEventSequence"),
                    values(root, "feats", value -> new FeatState(
                            string(value, "characterKey"), string(value, "featKey"),
                            longValue(value, "acquiredEventSequence")),
                            "characterKey", "featKey", "acquiredEventSequence"),
                    values(root, "multiclassProficiencies", value -> new MulticlassProficiency(
                            string(value, "characterKey"), string(value, "classKey"),
                            string(value, "proficiencyKey"),
                            longValue(value, "acquiredEventSequence")),
                            "characterKey", "classKey", "proficiencyKey",
                            "acquiredEventSequence"));
            return Result.ready(CampaignArchiveV2CharacterStateValidator.normalize(
                    decoded, characterKeys));
        } catch (ReadFailure | IllegalArgumentException | IllegalStateException exception) {
            return Result.failure(exception instanceof ReadFailure failure
                    ? failure.status : Status.INVALID_VALUE);
        }
    }

    private static CreationSnapshot snapshot(JsonObject value) throws ReadFailure {
        JsonArray abilities = arrayValue(value, "abilities");
        List<AbilityScore> scores = new ArrayList<>();
        for (JsonElement element : abilities) {
            JsonObject ability = requiredObject(element);
            requireFields(ability, Set.of("abilityKey", "baseScore", "finalScore"));
            scores.add(new AbilityScore(
                    string(ability, "abilityKey"), intValue(ability, "baseScore"),
                    intValue(ability, "finalScore")));
        }
        return new CreationSnapshot(
                string(value, "characterKey"), longValue(value, "createdEventSequence"),
                string(value, "previewDigestSha256"), string(value, "requestDigestSha256"),
                string(value, "abilityMethodKey"), string(value, "raceKey"),
                nullableString(value, "subraceKey"), string(value, "backgroundKey"),
                string(value, "classKey"), scores, intValue(value, "maximumHitPoints"));
    }

    private static <T> List<T> values(
            JsonObject root, String name, Decoder<T> decoder, String... fields)
            throws ReadFailure {
        JsonArray array = arrayValue(root, name);
        List<T> result = new ArrayList<>();
        Set<String> required = Set.of(fields);
        for (JsonElement element : array) {
            JsonObject object = requiredObject(element);
            requireFields(object, required);
            result.add(decoder.decode(object));
        }
        return List.copyOf(result);
    }

    private static void scan(String source) throws ReadFailure {
        try (JsonReader json = new JsonReader(new StringReader(source))) {
            json.setStrictness(Strictness.STRICT);
            scanValue(json, 0);
            if (json.peek() != JsonToken.END_DOCUMENT) throw new ReadFailure(Status.INVALID_JSON);
        } catch (IOException | IllegalStateException exception) {
            throw new ReadFailure(Status.INVALID_JSON);
        }
    }

    private static void scanValue(JsonReader json, int depth) throws IOException, ReadFailure {
        switch (json.peek()) {
            case BEGIN_OBJECT -> {
                if (depth >= MAX_DEPTH) throw new ReadFailure(Status.INVALID_STRUCTURE);
                json.beginObject();
                Set<String> names = new HashSet<>();
                while (json.hasNext()) {
                    String name = json.nextName();
                    if (!unicodeScalars(name) || !names.add(name)) {
                        throw new ReadFailure(Status.DUPLICATE_KEY);
                    }
                    scanValue(json, depth + 1);
                }
                json.endObject();
            }
            case BEGIN_ARRAY -> {
                if (depth >= MAX_DEPTH) throw new ReadFailure(Status.INVALID_STRUCTURE);
                json.beginArray();
                int count = 0;
                while (json.hasNext()) {
                    if (++count > MAX_ARRAY_ELEMENTS) {
                        throw new ReadFailure(Status.INVALID_STRUCTURE);
                    }
                    scanValue(json, depth + 1);
                }
                json.endArray();
            }
            case STRING -> {
                if (!unicodeScalars(json.nextString())) throw new ReadFailure(Status.INVALID_VALUE);
            }
            case NUMBER -> json.nextString();
            case BOOLEAN -> json.nextBoolean();
            case NULL -> json.nextNull();
            default -> throw new ReadFailure(Status.INVALID_JSON);
        }
    }

    private static JsonObject object(Object... values) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < values.length; index += 2) {
            String name = (String) values[index];
            Object value = values[index + 1];
            if (value == null) result.add(name, JsonNull.INSTANCE);
            else if (value instanceof String text) result.addProperty(name, text);
            else if (value instanceof Number number) result.addProperty(name, number);
            else if (value instanceof Boolean bool) result.addProperty(name, bool);
            else throw new IllegalArgumentException("Unsupported explicit archive scalar");
        }
        return result;
    }

    private static <T> JsonArray array(List<T> values, Encoder<T> encoder) {
        JsonArray result = new JsonArray();
        for (T value : values) result.add(encoder.encode(value));
        return result;
    }

    private static JsonObject requiredObject(JsonElement value) throws ReadFailure {
        if (value == null || !value.isJsonObject()) throw new ReadFailure(Status.INVALID_STRUCTURE);
        return value.getAsJsonObject();
    }

    private static JsonArray arrayValue(JsonObject object, String name) throws ReadFailure {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) throw new ReadFailure(Status.INVALID_STRUCTURE);
        return value.getAsJsonArray();
    }

    private static void requireFields(JsonObject object, Set<String> fields) throws ReadFailure {
        if (!object.keySet().equals(fields)) throw new ReadFailure(Status.INVALID_STRUCTURE);
    }

    private static String string(JsonObject object, String name) throws ReadFailure {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new ReadFailure(Status.INVALID_STRUCTURE);
        }
        return value.getAsString();
    }

    private static String nullableString(JsonObject object, String name) throws ReadFailure {
        JsonElement value = object.get(name);
        if (value != null && value.isJsonNull()) return null;
        return string(object, name);
    }

    private static boolean bool(JsonObject object, String name) throws ReadFailure {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new ReadFailure(Status.INVALID_STRUCTURE);
        }
        return value.getAsBoolean();
    }

    private static int intValue(JsonObject object, String name) throws ReadFailure {
        long value = longValue(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }
        return (int) value;
    }

    private static long longValue(JsonObject object, String name) throws ReadFailure {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) throw new ReadFailure(Status.INVALID_STRUCTURE);
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        String token = primitive.getAsString();
        if (!primitive.isNumber() || !INTEGER.matcher(token).matches()) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException exception) {
            throw new ReadFailure(Status.INVALID_VALUE);
        }
    }

    private static boolean unicodeScalars(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(++index))) return false;
            } else if (Character.isLowSurrogate(current)) return false;
        }
        return true;
    }

    enum Status {
        READY,
        INVALID_DOCUMENT,
        FILE_TOO_LARGE,
        INVALID_UTF8,
        INVALID_JSON,
        DUPLICATE_KEY,
        INVALID_STRUCTURE,
        INVALID_VALUE
    }

    record Result(Status status, CampaignArchiveV2CharacterState state) {
        private static Result ready(CampaignArchiveV2CharacterState state) {
            return new Result(Status.READY, state);
        }

        private static Result failure(Status status) {
            return new Result(status, null);
        }
    }

    @FunctionalInterface
    private interface Encoder<T> {
        JsonObject encode(T value);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(JsonObject value) throws ReadFailure;
    }

    private static final class ReadFailure extends Exception {
        private final Status status;

        private ReadFailure(Status status) {
            this.status = status;
        }
    }
}
