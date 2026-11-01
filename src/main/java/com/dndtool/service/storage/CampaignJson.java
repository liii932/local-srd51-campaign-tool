package com.dndtool.service.storage;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.text.Normalizer;
import java.util.*;

/** Strict portable JSON, shared by the finite codec and allocation markers. */
public final class CampaignJson {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private CampaignJson() { }

    public static JsonObject read(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw bad("JSON size is outside the supported budget");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.charAt(0) == '\ufeff') throw bad("UTF-8 BOM is forbidden");
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = readValue(reader, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT || !result.isJsonObject()) throw bad("Expected one JSON object");
            return result.getAsJsonObject();
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            throw new StorageException("INVALID_FILE", "Malformed strict UTF-8 JSON", e);
        }
    }

    private static JsonElement readValue(JsonReader r, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > 1_000_000) throw bad("JSON nesting or node budget exceeded");
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                r.beginObject(); JsonObject o = new JsonObject();
                while (r.hasNext()) {
                    String key = r.nextName(); validString(key);
                    if (o.has(key)) throw bad("Duplicate JSON field");
                    o.add(key, readValue(r, depth + 1, nodes));
                }
                r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> {
                r.beginArray(); JsonArray a = new JsonArray();
                while (r.hasNext()) a.add(readValue(r, depth + 1, nodes));
                r.endArray(); yield a;
            }
            case STRING -> { String s = r.nextString(); validString(s); yield new JsonPrimitive(s); }
            case NUMBER -> {
                String s = r.nextString();
                if (!s.matches("0|[1-9][0-9]*")) throw bad("Only bounded nonnegative protocol integers may be JSON numbers");
                try { yield new JsonPrimitive(Integer.parseInt(s)); }
                catch (NumberFormatException e) { throw bad("Protocol integer overflow"); }
            }
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw bad("Unexpected JSON token");
        };
    }

    public static byte[] canonical(JsonElement value) {
        StringBuilder b = new StringBuilder(); append(b, value);
        byte[] bytes = b.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw bad("JSON size exceeds budget");
        return bytes;
    }

    private static void append(StringBuilder b, JsonElement value) {
        if (value.isJsonNull()) { b.append("null"); return; }
        if (value.isJsonObject()) {
            b.append('{'); boolean first = true;
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) {
                if (!first) b.append(','); first = false; string(b, key); b.append(':'); append(b, value.getAsJsonObject().get(key));
            }
            b.append('}');
        } else if (value.isJsonArray()) {
            b.append('['); boolean first = true;
            for (JsonElement element : value.getAsJsonArray()) {
                if (!first) b.append(','); first = false; append(b, element);
            }
            b.append(']');
        } else {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isString()) string(b, p.getAsString()); else b.append(p);
        }
    }

    private static void string(StringBuilder b, String s) {
        validString(s); b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i); if (c == '"' || c == '\\') b.append('\\'); b.append(c);
        }
        b.append('"');
    }

    public static void fields(JsonObject o, String... names) {
        if (!o.keySet().equals(Set.of(names))) throw bad("Unknown or missing fields");
    }
    public static JsonObject object(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonObject()) throw bad("Expected object: " + key);
        return o.getAsJsonObject(key);
    }
    public static JsonArray array(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) throw bad("Expected array: " + key);
        return o.getAsJsonArray(key);
    }
    public static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString()) throw bad("Expected string: " + key);
        return o.get(key).getAsString();
    }
    public static long integer(JsonObject o, String key) {
        String s = string(o, key);
        if (!s.matches("0|[1-9][0-9]*")) throw bad("Expected canonical nonnegative integer: " + key);
        try { return Long.parseLong(s); } catch (NumberFormatException e) { throw bad("Integer overflow: " + key); }
    }
    public static void protocol(JsonObject o, String key, int expected) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.toString().equals(Integer.toString(expected))) throw bad("Unsupported protocol: " + key);
    }
    public static String uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw bad("Expected canonical UUIDv4");
        return value;
    }
    public static String name(String value) {
        if (value == null) throw bad("Name is required");
        validString(value);
        int n = value.codePointCount(0, value.length());
        if (n < 1 || n > 80 || value.isBlank() || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) throw bad("Name must be NFC and contain 1–80 nonblank code points");
        return value;
    }
    public static void validString(String value) {
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i); int cp = value.codePointAt(i);
            if (Character.isSurrogate(c) && (cp < 0x10000 || !Character.isHighSurrogate(c))) throw bad("Unpaired surrogate");
            if (cp < 32 || (cp >= 127 && cp <= 159)) throw bad("Control code points are forbidden");
            i += Character.charCount(cp);
        }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String digest(String domain, byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((domain + "\0" + "1" + "\0").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static StorageException bad(String message) { return new StorageException("INVALID_FILE", message); }
}
