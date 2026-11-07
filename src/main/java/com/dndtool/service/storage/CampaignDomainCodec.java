package com.dndtool.service.storage;

import com.dndtool.service.Schema2PreparationIdentity;

import com.dndtool.service.Schema2CombatHistory;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialProficiencyRules;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Closed, typed record documents. No class names, SQL, arbitrary objects or executable types are accepted. */
public final class CampaignDomainCodec {
    private static final Map<String, Class<?>> TYPES = new TreeMap<>();
    private static final Map<Class<?>, String> TAGS = new HashMap<>();
    static {
        register("rule", Schema2CompositionKernel.class);
        register("material", ProductionSchema2Projection.class);
        register("character", Schema2CharacterRuntime.class);
        register("battle-history", Schema2CombatHistory.class);
        register("scene", Schema2CombatState.class);
        register("initial", Schema2InitialCharacterPlanService.class);
        register("grants", Schema2InitialProficiencyRules.class);
        type("rules-binding", Schema2RulesBinding.class);
        type("preparation-identity", Schema2PreparationIdentity.class);
        register("campaign", CampaignDomainData.class);
        register("space", CampaignSpaceData.class);
        register("participation", CampaignParticipationData.class);
        register("participation-command", CampaignParticipationCommands.class);
        register("adjudication", CampaignAdjudicationData.class);
        register("adjudication-command", CampaignAdjudicationCommands.class);
        register("check-effects", CampaignCheckEffects.class);
        register("manual", CampaignManualData.class);
        register("manual-command", CampaignManualCommands.class);
    }
    private CampaignDomainCodec() { }
    private static void register(String prefix, Class<?> container) {
        for (Class<?> nested : container.getDeclaredClasses())
            if (nested.isRecord() && Modifier.isPublic(nested.getModifiers())) type(prefix + "." + nested.getSimpleName(), nested);
    }
    private static void type(String tag, Class<?> type) { TYPES.put(tag, type); TAGS.put(type, tag); }

    public static byte[] encode(Object value) { return CampaignJson.canonical(toJson(value)); }
    public static String text(Object value) { return new String(encode(value), StandardCharsets.UTF_8); }
    public static <T> T decode(byte[] bytes, Class<T> expected) { return fromJson(CampaignJson.read(bytes), expected); }
    public static <T> T fromJson(JsonElement json, Class<T> expected) {
        try { return expected.cast(read(json, expected, 0)); }
        catch (ReflectiveOperationException | IllegalArgumentException failure) {
            throw new StorageException("INVALID_DOMAIN_DOCUMENT", "Typed campaign document does not match the supported model", failure);
        }
    }
    public static JsonElement toJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String s) { validText(s); return new JsonPrimitive(s); }
        if (value instanceof UUID id) return new JsonPrimitive(CampaignJson.uuid(id.toString()));
        if (value instanceof Integer || value instanceof Long) return new JsonPrimitive(value.toString());
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (value instanceof Collection<?> list) {
            JsonArray array = new JsonArray(); list.forEach(item -> array.add(toJson(item))); return array;
        }
        if (value instanceof Map<?, ?> map) {
            List<JsonArray> rows = new ArrayList<>();
            for (var e : map.entrySet()) { JsonArray row = new JsonArray(); row.add(toJson(e.getKey())); row.add(toJson(e.getValue())); rows.add(row); }
            rows.sort(Comparator.comparing(row -> new String(CampaignJson.canonical(row.get(0)), StandardCharsets.UTF_8)));
            JsonArray array = new JsonArray(); rows.forEach(array::add); return array;
        }
        String tag = TAGS.get(value.getClass());
        if (tag == null) throw CampaignJson.bad("Unsupported domain type");
        JsonObject object = new JsonObject(); object.addProperty("type", tag);
        try {
            for (RecordComponent field : value.getClass().getRecordComponents()) object.add(field.getName(), toJson(field.getAccessor().invoke(value)));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        return object;
    }
    private static Object read(JsonElement value, Type expected, int depth) throws ReflectiveOperationException {
        if (depth > 32 || value == null) throw CampaignJson.bad("Domain nesting budget or missing field");
        if (value.isJsonNull()) {
            if (expected instanceof Class<?> c && c.isPrimitive()) throw CampaignJson.bad("Null primitive");
            return null;
        }
        if (expected instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (!value.isJsonArray() || value.getAsJsonArray().size() > 100000) throw CampaignJson.bad("Expected bounded typed collection");
            if (raw == List.class || raw == Set.class) {
                List<Object> items = new ArrayList<>();
                for (JsonElement item : value.getAsJsonArray()) items.add(read(item, args[0], depth + 1));
                if (items.contains(null)) throw CampaignJson.bad("Null collection member");
                if (raw == Set.class) {
                    Set<Object> unique = new LinkedHashSet<>(items);
                    if (unique.size() != items.size()) throw CampaignJson.bad("Duplicate set member");
                    return Collections.unmodifiableSet(unique);
                }
                return List.copyOf(items);
            }
            if (raw == Map.class) {
                Map<Object, Object> map = new LinkedHashMap<>(); String previous = null;
                for (JsonElement item : value.getAsJsonArray()) {
                    if (!item.isJsonArray() || item.getAsJsonArray().size() != 2) throw CampaignJson.bad("Expected typed map pair");
                    JsonArray pair = item.getAsJsonArray();
                    String keyText = new String(CampaignJson.canonical(pair.get(0)), StandardCharsets.UTF_8);
                    if (previous != null && previous.compareTo(keyText) >= 0) throw CampaignJson.bad("Map keys must be unique and canonical");
                    previous = keyText;
                    Object key = read(pair.get(0), args[0], depth + 1), entry = read(pair.get(1), args[1], depth + 1);
                    if (key == null || entry == null || map.putIfAbsent(key, entry) != null) throw CampaignJson.bad("Invalid map member");
                }
                return Collections.unmodifiableMap(map);
            }
            throw CampaignJson.bad("Unsupported collection type");
        }
        if (!(expected instanceof Class<?> target)) throw CampaignJson.bad("Unsupported generic type");
        if (target == String.class) { String text = string(value); validText(text); return text; }
        if (target == UUID.class) return UUID.fromString(CampaignJson.uuid(string(value)));
        if (target == long.class || target == Long.class || target == int.class || target == Integer.class) {
            String text = string(value);
            if (!text.matches("0|-?[1-9][0-9]*")) throw CampaignJson.bad("Expected canonical signed integer string");
            long number = Long.parseLong(text);
            if (target == int.class || target == Integer.class) return Math.toIntExact(number);
            return number;
        }
        if (target == boolean.class || target == Boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw CampaignJson.bad("Expected boolean");
            return value.getAsBoolean();
        }
        if (target.isEnum()) {
            String name = string(value);
            return Arrays.stream(target.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals(name)).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown enum value"));
        }
        if (!value.isJsonObject()) throw CampaignJson.bad("Expected typed record");
        JsonObject object = value.getAsJsonObject(); Class<?> actual = TYPES.get(CampaignJson.string(object, "type"));
        if (actual == null || !target.isAssignableFrom(actual) || !actual.isRecord()) throw CampaignJson.bad("Unexpected record discriminator");
        RecordComponent[] components = actual.getRecordComponents();
        Set<String> names = new HashSet<>(Set.of("type")); for (var c : components) names.add(c.getName());
        if (!object.keySet().equals(names)) throw CampaignJson.bad("Unknown or missing record fields");
        Object[] arguments = new Object[components.length]; Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            arguments[i] = read(object.get(components[i].getName()), components[i].getGenericType(), depth + 1);
            parameterTypes[i] = components[i].getType();
        }
        return actual.getConstructor(parameterTypes).newInstance(arguments);
    }
    private static String string(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw CampaignJson.bad("Expected string");
        return value.getAsString();
    }
    private static void validText(String value) {
        CampaignJson.validString(value);
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) throw CampaignJson.bad("Domain text must be NFC");
    }
}
