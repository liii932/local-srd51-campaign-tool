package com.dndtool.module.composition;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed oracles are transcribed from the design contract, never computed by the production kernel. */
final class Schema2Fixtures {
    static final String BASE = "validation_base", EXT = "validation_extension", COMPAT = "validation_compatibility";
    static final List<String> MODULES = List.of(BASE, EXT, COMPAT);
    static final Schema2CompositionKernel KERNEL = new Schema2CompositionKernel();
    private Schema2Fixtures() {}
    static byte[] resource(String path) {
        try (var input = Schema2Fixtures.class.getResourceAsStream("/schema2-composition/" + path)) {
            if (input == null) throw new AssertionError("Missing fixed fixture " + path);
            return input.readAllBytes();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
    static List<Map<String, byte[]>> packages() {
        var all = new ArrayList<Map<String, byte[]>>();
        for (String module : MODULES) {
            var files = new LinkedHashMap<String, byte[]>();
            files.put("author-package.json", resource(module + "/author-package.json"));
            for (var declaration : json(files.get("author-package.json")).getAsJsonObject().getAsJsonArray("files")) {
                String path = declaration.getAsJsonObject().get("path").getAsString();
                files.put(path, resource(module + "/" + path));
            }
            all.add(files);
        }
        return all;
    }
    static JsonElement json(byte[] value) { return JsonParser.parseString(new String(value, StandardCharsets.UTF_8)); }
    static JsonElement json(List<Map<String, byte[]>> p, int index, String path) { return json(p.get(index).get(path)); }
    static void edit(List<Map<String, byte[]>> p, int index, String path, Consumer<JsonElement> change) {
        var value = json(p, index, path); change.accept(value); put(p, index, path, value);
    }
    static void put(List<Map<String, byte[]>> p, int index, String path, JsonElement value) {
        p.get(index).put(path, value.toString().getBytes(StandardCharsets.UTF_8));
    }
    static void raw(List<Map<String, byte[]>> p, int index, String path, String value) {
        p.get(index).put(path, value.getBytes(StandardCharsets.UTF_8));
    }
    static JsonObject row(JsonElement root, int index) { return root.getAsJsonArray().get(index).getAsJsonObject(); }
    static JsonObject header(List<Map<String, byte[]>> p, int index) { return json(p,index,"author-package.json").getAsJsonObject(); }
    static JsonObject identity(List<Map<String, byte[]>> p, int index, String path, int row) {
        return row(json(p,index,path),row).getAsJsonObject("identity").deepCopy();
    }
    static void register(List<Map<String, byte[]>> p, int index, String path, String kind, JsonArray values) {
        edit(p,index,"author-package.json", h -> {
            var declaration = new JsonObject(); declaration.addProperty("kind",kind); declaration.addProperty("path",path);
            h.getAsJsonObject().getAsJsonArray("files").add(declaration);
        });
        put(p,index,path,values);
    }
    static Schema2CompositionKernel.Projection compose(List<Map<String, byte[]>> p) { return KERNEL.composeFiles(p); }
    static void passes(List<Map<String, byte[]>> p) { assertDoesNotThrow(() -> compose(p)); }
    static void reads(List<Map<String, byte[]>> p) { assertDoesNotThrow(() -> KERNEL.read(p.getFirst())); }
    static void fails(List<Map<String, byte[]>> p) { assertThrows(Schema2CompositionKernel.Schema2Exception.class, () -> compose(p)); }
    static List<Schema2CompositionKernel.AuthorPackage> read(List<Map<String, byte[]>> p) {
        return p.stream().map(KERNEL::read).toList();
    }
    static Schema2CompositionKernel.Diagnostic rejected(String code, String stage, List<Map<String, byte[]>> p) {
        var failure = assertThrows(Schema2CompositionKernel.Schema2Exception.class, () -> compose(p));
        var d = failure.diagnostic(); assertEquals(code,d.code()); assertEquals(stage,d.stage().name());
        assertNotNull(d.message()); assertFalse(d.message().isBlank()); assertNotNull(d.locations());
        return d;
    }
    static void at(Schema2CompositionKernel.Diagnostic d, int index, String path, String pointer) {
        var matches=d.locations().stream().filter(l -> path.equals(l.path()) && pointer.equals(l.pointer())).toList();
        assertFalse(matches.isEmpty(), () -> "Missing " + MODULES.get(index) + ":" + path + "#" + pointer + " in " + d.locations());
        var location=matches.getFirst(); assertEquals(MODULES.get(index),location.moduleKey()); assertEquals("1",location.releaseVersion());
        assertNotNull(location.rawZipSha256()); assertTrue(location.rawZipSha256().matches("[0-9a-f]{64}")); assertNull(location.byteOffset());
    }
    static void atOrBelow(Schema2CompositionKernel.Diagnostic d, int index, String path, String pointer) {
        assertTrue(d.locations().stream().anyMatch(l -> MODULES.get(index).equals(l.moduleKey()) && path.equals(l.path())
                && l.pointer()!=null && (l.pointer().equals(pointer) || l.pointer().startsWith(pointer+"/"))),
                () -> "Missing affected location " + path + "#" + pointer + " in " + d.locations());
    }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static byte[] canonical(JsonElement value) {
        StringBuilder b=new StringBuilder(); write(value,b); return b.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void write(JsonElement value,StringBuilder b) {
        if (value.isJsonObject()) {
            b.append('{'); boolean comma=false;
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) {
                if (comma) b.append(','); comma=true; string(key,b); b.append(':'); write(value.getAsJsonObject().get(key),b);
            }
            b.append('}');
        } else if (value.isJsonArray()) {
            b.append('['); boolean comma=false;
            for (var item:value.getAsJsonArray()) { if(comma)b.append(',');comma=true;write(item,b); } b.append(']');
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) string(value.getAsString(),b);
        else b.append(value);
    }
    private static void string(String text,StringBuilder b) {
        b.append('"');
        text.codePoints().forEach(cp -> {
            if(cp=='"'||cp=='\\') { b.append('\\');b.appendCodePoint(cp); }
            else if(cp==8)b.append("\\b");else if(cp==9)b.append("\\t");else if(cp==10)b.append("\\n");
            else if(cp==12)b.append("\\f");else if(cp==13)b.append("\\r");
            else if(cp<32)b.append(String.format("\\u%04x",cp));else b.appendCodePoint(cp);
        }); b.append('"');
    }
    static String digest(String domain, JsonElement value) {
        var prefix=("local-dnd/schema2-validation/1/"+domain+"\0").getBytes(StandardCharsets.UTF_8);
        var payload=canonical(value); var bytes=Arrays.copyOf(prefix,prefix.length+payload.length);
        System.arraycopy(payload,0,bytes,prefix.length,payload.length);return sha(bytes);
    }
}
