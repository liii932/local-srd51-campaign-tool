package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Shared access to independent, fixed proficiency-source vectors. */
public final class Schema2GrantFixtures {
    public static final String BASE = "grants_base", EXT = "grants_extension", COMPAT = "grants_compatibility";
    public static final List<String> MODULES = List.of(BASE, EXT, COMPAT);
    private Schema2GrantFixtures() { }

    public static byte[] resource(String path) {
        try (var input = Schema2GrantFixtures.class.getResourceAsStream("/schema2-grants/" + path)) {
            if (input == null) throw new AssertionError("Missing fixed grants fixture " + path);
            return input.readAllBytes();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
    public static List<ProductionRuleArtifact> fixedArtifacts() {
        return MODULES.stream().map(module -> ProductionRuleArtifact.read(HexFormat.of().parseHex(
                new String(resource(module + ".zip.hex"), StandardCharsets.US_ASCII).strip()))).toList();
    }
    public static List<Map<String, byte[]>> packages() {
        var result = new ArrayList<Map<String, byte[]>>();
        for (String module : MODULES) {
            var files = new LinkedHashMap<String, byte[]>();
            files.put("author-package.json", resource(module + "/author-package.json"));
            for (var declaration : json(files.get("author-package.json")).getAsJsonObject().getAsJsonArray("files")) {
                String path = declaration.getAsJsonObject().get("path").getAsString();
                files.put(path, resource(module + "/" + path));
            }
            result.add(files);
        }
        return result;
    }
    public static List<ProductionRuleArtifact> artifacts(List<Map<String, byte[]>> files) {
        return files.stream().map(f -> ProductionRuleArtifact.read(Schema2ValidationCodec.rawZip(f))).toList();
    }
    public static JsonElement json(byte[] value) {
        return JsonParser.parseString(new String(value, StandardCharsets.UTF_8));
    }
    public static JsonObject row(JsonElement root, int index) { return root.getAsJsonArray().get(index).getAsJsonObject(); }
    public static void edit(List<Map<String, byte[]>> files, int index, String path, Consumer<JsonElement> mutation) {
        var parsed = json(files.get(index).get(path));
        mutation.accept(parsed);
        files.get(index).put(path, parsed.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static JsonObject rule(String module, String type, String key) {
        var result = new JsonObject(); result.addProperty("kind", "rule"); result.addProperty("module_key", module);
        result.addProperty("rule_type", type); result.addProperty("rule_key", key); return result;
    }
}
