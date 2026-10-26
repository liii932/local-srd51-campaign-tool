package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.persistence.Schema2SourceLedger.Identity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;

/** SELECT-only JDBC protocol fixture. Directly seeded finite BASE grants no install/run eligibility or MySQL evidence. */
public class Schema2SourceReadFixture {
    private static final List<ProductionRuleArtifact> DEFAULT_ARTIFACTS = List.of(artifact("validation_base"), artifact("validation_extension"), artifact("validation_compatibility"));
    private static UUID installationOperation(int index) { return new UUID(0x4000L, 0x8000000000000000L + index); }
    private static UUID acceptanceOperation(int index) { return new UUID(0x1000000000004000L, 0x8000000000000000L + index); }
    private static byte[] operationBytes(UUID operation) { return ByteBuffer.allocate(16).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits()).array(); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static ProductionRuleArtifact artifact(String name) {
        try (InputStream input = Schema2SourceReadFixture.class.getResourceAsStream("/schema2-production/" + name + ".zip.hex")) {
            return ProductionRuleArtifact.read(HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", "")));
        } catch (IOException failure) { throw new AssertionError(failure); }
    }

    final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    final List<String> queries = new ArrayList<>(), events = new ArrayList<>();
    final List<Resource> resources = new ArrayList<>();
    final Set<String> failures = new HashSet<>();
    final Map<String, Integer> maximumValueRow = new HashMap<>();
    boolean autoCommit = true, readOnly;
    int isolation = Connection.TRANSACTION_READ_COMMITTED;
    int acquisitions, commits, rollbacks, aborts, closes, blobReads, streamCloses, streamBytes;
    byte[] streamOverride;
    final Connection connection = proxy(Connection.class, this::connectionCall);
    private final List<ProductionRuleArtifact> artifacts;
    private boolean unavailable;
    public Consumer<String> observer = event -> { };

    public Schema2SourceReadFixture() { this(DEFAULT_ARTIFACTS); }
    public Schema2SourceReadFixture(List<ProductionRuleArtifact> artifacts) {
        this.artifacts = List.copyOf(artifacts);
        seed();
    }
    public DataSource dataSource() {
        return proxy(DataSource.class, (object, method, arguments) -> {
            assertEquals("getConnection", method.getName());
            acquisitions++; hit("acquire");
            if (unavailable) throw new SQLException("Source disconnected");
            return connection;
        });
    }
    public Connection connection() { return connection; }
    public List<Identity> selections() { return artifacts.stream().map(a -> new Identity(a.moduleKey(), a.releaseVersion())).toList(); }
    public List<ProductionRuleArtifact> artifacts() { return artifacts; }
    public int closeCount() { return closes; }
    public int commitCount() { return commits; }
    public int acquisitionCount() { return acquisitions; }
    public List<String> events() { return List.copyOf(events); }
    public void fail(String operation) { failures.add(operation); }
    public void unavailable(boolean value) { unavailable = value; }
    public Map<String, List<Map<String, Object>>> rows() { return rows; }

    private void seed() {
        rows.put("schema", list(row("schema_name", bytes("dnd_tool_rules"))));
        rows.put("ledger", new ArrayList<>(RuleSchemaMigrations.expectations().stream().map(expected -> row("schema_version", expected.version(), "script_name", bytes(expected.scriptName()), "script_sha256", bytes(expected.scriptSha256()))).toList()));
        rows.put("artifact", new ArrayList<>());
        rows.put("acceptance", new ArrayList<>());
        for (String table : List.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_execution_change", "rule_schema2_combat_change", "rule_schema2_health", "rule_schema2_vital_effect", "rule_schema2_combat", "rule_schema2_weapon_attack", "rule_schema2_movement")) rows.put(table, new ArrayList<>());
        long total = 0;
        for (int index = 0; index < artifacts.size(); index++) {
            var artifact = artifacts.get(index);
            seedCharacterMaterial(artifact);
            total += artifact.originalZip().length;
            rows.get("artifact").add(row("module_key", bytes(artifact.moduleKey()), "release_version", bytes(artifact.releaseVersion()),
                    "installation_sequence", (long) index + 1, "source_operation_id", operationBytes(installationOperation(index)),
                    "operation_fingerprint_version", 1, "operation_digest_sha256", bytes("a".repeat(64)), "author_schema_version", 2,
                    "artifact_protocol_version", 1, "canonical_profile", bytes(artifact.canonicalProfile()), "package_role", bytes(artifact.role().name()),
                    "raw_zip_sha256", bytes(artifact.rawZipSha256()), "logical_sha256", bytes(artifact.logicalSha256()),
                    "raw_zip_byte_length", (long) artifact.originalZip().length, "expanded_byte_length", artifact.expandedByteLength(),
                    "original_zip", artifact.originalZip(), "installed_at", LocalDateTime.of(2026, 1, 1, 0, index)));
            rows.get("acceptance").add(row("module_key", bytes(artifact.moduleKey()), "release_version", bytes(artifact.releaseVersion()),
                    "acceptance_sequence", (long) index + 1, "acceptance_operation_id", operationBytes(acceptanceOperation(index)),
                    "operation_fingerprint_version", 1, "operation_digest_sha256", bytes("b".repeat(64)),
                    "installed_operation_id", operationBytes(installationOperation(index)), "installed_operation_digest_sha256", bytes("a".repeat(64)),
                    "installation_sequence", (long) index + 1, "author_schema_version", 2, "artifact_protocol_version", 1,
                    "canonical_profile", bytes(artifact.canonicalProfile()), "package_role", bytes(artifact.role().name()),
                    "raw_zip_sha256", bytes(artifact.rawZipSha256()), "logical_sha256", bytes(artifact.logicalSha256()),
                    "review_sha256", bytes("c".repeat(64)), "accepted_at", LocalDateTime.of(2026, 2, 1, 0, index)));
        }
        rows.put("control", list(row("control_id", 1, "protocol_version", 1, "metadata_row_count", 1L + 2 * artifacts.size(),
                "installation_count", (long) artifacts.size(), "acceptance_count", (long) artifacts.size(), "raw_zip_byte_count", total, "row_version", 2L * artifacts.size())));
    }

    /** Independent fixture projection from original JSON; never calls the storage mapper or compositor. */
    private void seedCharacterMaterial(ProductionRuleArtifact artifact) {
        Map<String, byte[]> files = artifact.originalMembers();
        JsonObject header = JsonParser.parseString(new String(files.get("author-package.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Integer> indexes = new HashMap<>();
        var declarations = new ArrayList<JsonObject>();
        header.getAsJsonArray("files").forEach(file -> declarations.add(file.getAsJsonObject()));
        declarations.sort(Comparator.comparing(file -> file.get("path").getAsString()));
        for (var file : declarations) {
            String kind = file.get("kind").getAsString();
            for (var element : JsonParser.parseString(new String(files.get(file.get("path").getAsString()), StandardCharsets.UTF_8)).getAsJsonArray()) {
                int index = indexes.getOrDefault(kind, 0); indexes.put(kind, index + 1);
                JsonObject value = element.getAsJsonObject();
                JsonObject target = value.getAsJsonObject(kind.equals("changes") ? "target" : "identity");
                boolean entry = target.get("kind").getAsString().equals("entry");
                JsonObject owner = entry ? target.getAsJsonObject("rule") : target;
                String ruleType = owner.get("rule_type").getAsString();
                if (Set.of("equipment.item", "adventure.health", "adventure.vital_effect", "adventure.combat", "equipment.weapon_attack", "character.movement").contains(ruleType)) {
                    seedExecutionMaterial(artifact, kind, index, value, owner, ruleType); continue;
                }
                if (Set.of("character.class_progression", "character.resource", "character.subclass", "character.feature", "character.feat", "character.proficiency", "catalog.proficiency_set", "adventure.rest", "adventure.check").contains(ruleType)) {
                    seedAdvancementMaterial(artifact, kind, index, value, owner, ruleType);
                    continue;
                }
                if (!Set.of("character.race", "character.subrace", "character.background", "character.class").contains(owner.get("rule_type").getAsString())) continue;
                Map<String, Object> row = row("artifact_module_key", bytes(artifact.moduleKey()), "release_version", bytes(artifact.releaseVersion()), "author_index", index);
                String table;
                if (kind.equals("definitions")) {
                    table = "rule_schema2_character_source";
                    identity(row, owner, "module_key", "rule_type", "rule_key");
                    for (String field : List.of("display_name", "description", "author_note")) row.put(field, optionalText(value, field));
                    row.put("source_page", value.get("source_page").getAsInt());
                    JsonObject parent = value.getAsJsonObject("parent_race");
                    row.put("parent_race_module_key", parent == null ? null : bytes(parent.get("module_key").getAsString()));
                    row.put("parent_race_rule_key", parent == null ? null : bytes(parent.get("rule_key").getAsString()));
                } else {
                    identity(row, owner, "rule_module_key", "rule_type", "rule_key");
                    row.put("collection_key", entry ? bytes(target.get("collection_key").getAsString()) : null);
                    row.put("module_key", entry ? bytes(target.get("module_key").getAsString()) : null);
                    row.put("entry_key", entry ? bytes(target.get("entry_key").getAsString()) : null);
                    if (kind.equals("entries")) {
                        table = "rule_schema2_proficiency_grant";
                        grant(row, value.getAsJsonObject("grant")); row.put("author_note", optionalText(value, "author_note"));
                    } else {
                        table = "rule_schema2_character_change";
                        row.put("target_kind", bytes(entry ? "entry" : "rule"));
                        String operation = value.get("op").getAsString();
                        String field = operation.equals("set_enabled") ? "enabled" : value.get("field").getAsString();
                        row.put("operation", bytes(operation)); row.put("field_key", bytes(field));
                        for (String column : List.of("text_value", "integer_value", "boolean_value", "reference_module_key", "reference_rule_type", "reference_rule_key")) row.put(column, null);
                        grant(row, null);
                        if (!operation.equals("clear_field")) {
                            var atom = value.get("value");
                            if (field.equals("grant")) grant(row, atom.getAsJsonObject());
                            else if (field.equals("parent_race")) identity(row, atom.getAsJsonObject(), "reference_module_key", "reference_rule_type", "reference_rule_key");
                            else if (field.equals("enabled")) row.put("boolean_value", atom.getAsBoolean() ? 1 : 0);
                            else if (field.equals("source_page")) row.put("integer_value", atom.getAsInt());
                            else row.put("text_value", atom.getAsString());
                        }
                    }
                }
                rows.get(table).add(row);
            }
        }
    }
    private void seedExecutionMaterial(ProductionRuleArtifact artifact, String kind, int index, JsonObject value, JsonObject owner, String ruleType) {
        Map<String,Object> row = row("artifact_module_key",bytes(artifact.moduleKey()),"release_version",bytes(artifact.releaseVersion()),"author_index",index);
        String table;
        if (kind.equals("definitions")) {
            table = "rule_schema2_" + (ruleType.equals("equipment.item") ? "equipment_item" : ruleType.substring(ruleType.indexOf('.')+1));
            identity(row,owner,"module_key","rule_type","rule_key");
            for(String field:List.of("display_name","description","author_note"))row.put(field,optionalText(value,field));
            row.put("source_page",value.get("source_page").getAsInt());row.put("algorithm",bytes(value.get("algorithm").getAsString()));
            if(ruleType.equals("equipment.weapon_attack")) {
                identity(row,value.getAsJsonObject("item"),"item_module_key","item_rule_type","item_rule_key");
                for(String field:List.of("ability_policy","damage_type"))row.put(field,bytes(value.get(field).getAsString()));
                for(String field:List.of("dice_count","dice_sides","reach_feet"))row.put(field,value.get(field).getAsInt());
            } else if(ruleType.equals("character.movement")) {
                identity(row,value.getAsJsonObject("owner"),"owner_module_key","owner_rule_type","owner_rule_key");
                row.put("walk_speed_feet",value.get("walk_speed_feet").getAsInt());
            } else if(ruleType.equals("equipment.item")) {
                row.put("kind",bytes(value.get("kind").getAsString()));
                for(String field:List.of("armor_class","strength_minimum","hands"))row.put(field,value.get(field).getAsInt());
                row.put("stealth_disadvantage",value.get("stealth_disadvantage").getAsBoolean()?1:0);
                identity(row,value.getAsJsonObject("proficiency"),"proficiency_module_key","proficiency_rule_type","proficiency_rule_key");
                for(String column:List.of("stealth_skill_module_key","stealth_skill_rule_type","stealth_skill_rule_key"))row.put(column,null);
                if(value.has("stealth_skill"))identity(row,value.getAsJsonObject("stealth_skill"),"stealth_skill_module_key","stealth_skill_rule_type","stealth_skill_rule_key");
            } else if(ruleType.equals("adventure.vital_effect")) {
                row.put("kind",bytes(value.get("kind").getAsString()));
                for(String field:List.of("dice_count","dice_sides","modifier"))row.put(field,value.get(field).getAsInt());
                row.put("damage_type",value.has("damage_type")?bytes(value.get("damage_type").getAsString()):null);
            }
        } else {
            table=Set.of("adventure.combat","equipment.weapon_attack","character.movement").contains(ruleType)?"rule_schema2_combat_change":"rule_schema2_execution_change";identity(row,owner,"rule_module_key","rule_type","rule_key");
            String op=value.get("op").getAsString(),field=op.equals("set_enabled")?"enabled":value.get("field").getAsString();
            row.put("operation",bytes(op));row.put("field_key",bytes(field));
            for(String column:List.of("text_value","integer_value","boolean_value","reference_module_key","reference_rule_type","reference_rule_key"))row.put(column,null);
            if(!op.equals("clear_field")) {
                var atom=value.get("value");
                if(Set.of("proficiency","stealth_skill","item","owner").contains(field))identity(row,atom.getAsJsonObject(),"reference_module_key","reference_rule_type","reference_rule_key");
                else if(Set.of("enabled","stealth_disadvantage").contains(field))row.put("boolean_value",atom.getAsBoolean()?1:0);
                else if(Set.of("source_page","armor_class","strength_minimum","hands","dice_count","dice_sides","modifier","reach_feet","walk_speed_feet").contains(field))row.put("integer_value",atom.getAsInt());
                else row.put("text_value",atom.getAsString());
            }
        }
        rows.get(table).add(row);
    }
    private void seedAdvancementMaterial(ProductionRuleArtifact artifact, String kind, int index,
            JsonObject value, JsonObject owner, String ruleType) {
        Map<String, Object> row = row("artifact_module_key", bytes(artifact.moduleKey()), "release_version", bytes(artifact.releaseVersion()), "author_index", index);
        String table;
        if (kind.equals("definitions")) {
            table = "rule_schema2_" + (ruleType.equals("character.class_progression") ? "class_progression" : ruleType.substring(ruleType.indexOf('.') + 1));
            identity(row, owner, "module_key", "rule_type", "rule_key");
            for (String field : List.of("display_name", "author_note")) row.put(field, optionalText(value, field));
            if (!ruleType.equals("catalog.proficiency_set")) {
                row.put("description", value.get("description").getAsString()); row.put("source_page", value.get("source_page").getAsInt());
            }
            if (value.has("owner_class")) identity(row, value.getAsJsonObject("owner_class"), "owner_class_module_key", "owner_class_rule_type", "owner_class_rule_key");
            if (value.has("owner")) identity(row, value.getAsJsonObject("owner"), "owner_module_key", "owner_rule_type", "owner_rule_key");
            for (String field : List.of("hit_die_sides", "supported_maximum_level", "selection_level", "level", "minimum_enabled_members"))
                if (value.has(field)) row.put(field, value.get(field).getAsInt());
            for (String field : List.of("proficiency_bonus_profile", "maximum_profile", "recovery_profile"))
                if (value.has(field)) row.put(field, value.get(field).getAsString());
            for (String field : List.of("advancement_scope", "execution_mode", "execution_algorithm", "feature_kind", "category", "algorithm"))
                if (value.has(field)) row.put(field, bytes(value.get(field).getAsString()));
            if (ruleType.equals("character.proficiency")) row.put("ability_key", value.has("ability_key") ? bytes(value.get("ability_key").getAsString()) : null);
            if (ruleType.equals("character.class_progression")) {
                row.put("multiclass_prerequisite_present", value.has("multiclass_prerequisite") ? 1 : 0);
                seedRequirements(artifact, index, kind, value.getAsJsonObject("multiclass_prerequisite"));
            }
            if (ruleType.equals("character.feat")) seedRequirements(artifact, index, kind, value.getAsJsonObject("prerequisite"));
        } else {
            JsonObject target = value.getAsJsonObject(kind.equals("changes") ? "target" : "identity");
            boolean entry = target.get("kind").getAsString().equals("entry");
            identity(row, owner, "rule_module_key", "rule_type", "rule_key");
            row.put("collection_key", entry ? bytes(target.get("collection_key").getAsString()) : null);
            row.put("module_key", entry ? bytes(target.get("module_key").getAsString()) : null);
            row.put("entry_key", entry ? bytes(target.get("entry_key").getAsString()) : null);
            if (kind.equals("entries")) {
                String collection = target.get("collection_key").getAsString();
                if (collection.equals("levels")) {
                    table = "rule_schema2_class_level"; row.put("level", value.get("level").getAsInt());
                    row.put("asi_available", value.get("asi_available").getAsBoolean() ? 1 : 0);
                } else if (collection.equals("members")) {
                    table = "rule_schema2_proficiency_member";
                    identity(row, value.getAsJsonObject("proficiency"), "proficiency_module_key", "proficiency_rule_type", "proficiency_rule_key");
                } else {
                    table = "rule_schema2_growth_grant"; growthGrant(row, value.getAsJsonObject("growth_grant"));
                }
                row.put("author_note", optionalText(value, "author_note"));
            } else {
                table = "rule_schema2_advancement_change"; row.put("target_kind", bytes(entry ? "entry" : "rule"));
                String operation = value.get("op").getAsString();
                String field = operation.equals("set_enabled") ? "enabled" : value.get("field").getAsString();
                row.put("operation", bytes(operation)); row.put("field_key", bytes(field));
                for (String column : List.of("text_value", "integer_value", "boolean_value", "reference_module_key", "reference_rule_type", "reference_rule_key", "prerequisite_value")) row.put(column, null);
                growthGrant(row, null);
                if (!operation.equals("clear_field")) {
                    var atom = value.get("value");
                    if (Set.of("owner_class", "owner", "proficiency").contains(field)) identity(row, atom.getAsJsonObject(), "reference_module_key", "reference_rule_type", "reference_rule_key");
                    else if (field.equals("growth_grant")) growthGrant(row, atom.getAsJsonObject());
                    else if (Set.of("multiclass_prerequisite", "prerequisite").contains(field)) {
                        row.put("prerequisite_value", 1); seedRequirements(artifact, index, kind, atom.getAsJsonObject());
                    } else if (Set.of("enabled", "asi_available").contains(field)) row.put("boolean_value", atom.getAsBoolean() ? 1 : 0);
                    else if (Set.of("source_page", "hit_die_sides", "supported_maximum_level", "selection_level", "level", "minimum_enabled_members").contains(field)) row.put("integer_value", atom.getAsInt());
                    else row.put("text_value", atom.getAsString());
                }
            }
        }
        rows.get(table).add(row);
    }
    private void seedRequirements(ProductionRuleArtifact artifact, int index, String kind, JsonObject prerequisite) {
        if (prerequisite == null) return;
        var groups = prerequisite.getAsJsonArray("all_of");
        for (int group = 0; group < groups.size(); group++) {
            var alternatives = groups.get(group).getAsJsonObject().getAsJsonArray("any_of");
            for (int alternative = 0; alternative < alternatives.size(); alternative++) {
                var minimum = alternatives.get(alternative).getAsJsonObject();
                rows.get("rule_schema2_ability_requirement").add(row("artifact_module_key", bytes(artifact.moduleKey()),
                        "release_version", bytes(artifact.releaseVersion()), "author_index", index, "author_kind", bytes(kind),
                        "group_index", group, "alternative_index", alternative, "ability_key", bytes(minimum.get("ability").getAsString()),
                        "minimum_value", minimum.get("minimum").getAsInt()));
            }
        }
    }
    private static void growthGrant(Map<String, Object> row, JsonObject grant) {
        for (String column : List.of("growth_grant_kind", "growth_grant_target_module_key", "growth_grant_target_rule_type", "growth_grant_target_rule_key", "growth_grant_count")) row.put(column, null);
        if (grant == null) return;
        String kind = grant.get("kind").getAsString();
        row.put("growth_grant_kind", bytes(kind));
        identity(row, grant.getAsJsonObject(kind.equals("FIXED") ? "target" : "candidates"), "growth_grant_target_module_key", "growth_grant_target_rule_type", "growth_grant_target_rule_key");
        if (kind.equals("CHOICE")) row.put("growth_grant_count", grant.get("count").getAsInt());
    }
    private static void identity(Map<String, Object> row, JsonObject identity, String module, String type, String key) {
        row.put(module, bytes(identity.get("module_key").getAsString())); row.put(type, bytes(identity.get("rule_type").getAsString())); row.put(key, bytes(identity.get("rule_key").getAsString()));
    }
    private static void grant(Map<String, Object> row, JsonObject grant) {
        for (String column : List.of("grant_category", "grant_kind", "grant_target_module_key", "grant_target_rule_key", "grant_count")) row.put(column, null);
        if (grant == null) return;
        String kind = grant.get("kind").getAsString(); JsonObject target = grant.getAsJsonObject(kind.equals("FIXED") ? "target" : "candidates");
        row.put("grant_category", bytes(grant.get("category").getAsString())); row.put("grant_kind", bytes(kind));
        row.put("grant_target_module_key", bytes(target.get("module_key").getAsString())); row.put("grant_target_rule_key", bytes(target.get("rule_key").getAsString()));
        if (kind.equals("CHOICE")) row.put("grant_count", grant.get("count").getAsInt());
    }
    private static String optionalText(JsonObject value, String name) { return value.has(name) ? value.get(name).getAsString() : null; }
    Map<String, Object> control() { return rows.get("control").getFirst(); }
    Map<String, Object> installed(int index) { return rows.get("artifact").get(index); }
    Map<String, Object> accepted(int index) { return rows.get("acceptance").get(index); }
    JdbcSourceSchema2Repository.Snapshot load(List<Identity> selected) throws SQLException {
        return new JdbcSourceSchema2Repository(dataSource()).load(selected);
    }
    Object connectionCall(Object object, java.lang.reflect.Method method, Object[] args) throws SQLException {
        return switch (method.getName()) {
            case "getAutoCommit" -> autoCommit;
            case "isReadOnly" -> readOnly;
            case "getTransactionIsolation" -> isolation;
            case "setReadOnly" -> { hit(readOnly ? "restoreReadOnly" : "setReadOnly"); readOnly = (boolean) args[0]; yield null; }
            case "setTransactionIsolation" -> { hit(isolation == Connection.TRANSACTION_REPEATABLE_READ ? "restoreIsolation" : "setIsolation"); isolation = (int) args[0]; yield null; }
            case "setAutoCommit" -> { hit((boolean) args[0] ? "restoreAuto" : "begin"); autoCommit = (boolean) args[0]; yield null; }
            case "prepareStatement" -> {
                assertFalse(autoCommit); assertTrue(readOnly); assertEquals(Connection.TRANSACTION_REPEATABLE_READ, isolation);
                yield statement((String) args[0]);
            }
            case "commit" -> { commits++; assertResourcesClosed(); hit("commit"); yield null; }
            case "rollback" -> { rollbacks++; assertResourcesClosed(); hit("rollback"); yield null; }
            case "abort" -> { aborts++; hit("abort"); yield null; }
            case "close" -> { closes++; hit("close"); yield null; }
            default -> throw new AssertionError("Unexpected connection method " + method.getName());
        };
    }
    PreparedStatement statement(String sql) {
        String table = table(sql);
        Resource statement = new Resource(); resources.add(statement);
        Map<Integer, byte[]> parameters = new HashMap<>();
        int[] maximum = {0};
        boolean[] timeout = {false};
        return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
            case "setQueryTimeout" -> { assertEquals(5, args[0]); timeout[0] = true; yield null; }
            case "setMaxRows" -> {
                maximum[0] = (int) args[0];
                assertEquals(table.equals("ledger") ? RuleSchemaMigrations.expectations().size() + 1 : table.equals("artifact") || table.equals("acceptance") ? 17
                        : Set.of("rule_schema2_character_source", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_health", "rule_schema2_vital_effect", "rule_schema2_combat", "rule_schema2_weapon_attack", "rule_schema2_movement").contains(table) ? 4097 : Set.of("rule_schema2_proficiency_grant", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant").contains(table) ? 8193
                        : Set.of("rule_schema2_character_change", "rule_schema2_advancement_change", "rule_schema2_execution_change", "rule_schema2_combat_change").contains(table) ? 65537 : table.equals("rule_schema2_ability_requirement") ? 30721 : 2, maximum[0]);
                yield null;
            }
            case "setBytes" -> { parameters.put((int) args[0], ((byte[]) args[1]).clone()); yield null; }
            case "executeQuery" -> {
                assertTrue(timeout[0]); assertTrue(maximum[0] > 0); hit("execute:" + table); queries.add(table);
                List<Map<String, Object>> selected = rows.get(table);
                if (table.equals("zip")) {
                    assertEquals(Set.of(1, 2), parameters.keySet());
                    selected = rows.get("artifact").stream().filter(row -> Arrays.equals((byte[]) row.get("module_key"), parameters.get(1))
                            && Arrays.equals((byte[]) row.get("release_version"), parameters.get(2))).toList();
                } else assertTrue(parameters.isEmpty());
                yield result(sql, table, selected.subList(0, Math.min(selected.size(), maximum[0])), statement);
            }
            case "close" -> { statement.closed = true; hit("stmtClose:" + table); yield null; }
            default -> throw new AssertionError("Unexpected statement method " + method.getName());
        });
    }
    ResultSet result(String sql, String table, List<Map<String, Object>> selected, Resource statement) {
        Resource result = new Resource(); resources.add(result);
        String[] columns = table.equals("schema") ? new String[] {"schema_name"} : sql.substring(7, sql.indexOf(" FROM ")).split(", ");
        int[] cursor = {-1};
        return proxy(ResultSet.class, (object, method, args) -> {
            assertFalse(statement.closed); assertFalse(result.closed);
            return switch (method.getName()) {
                case "next" -> ++cursor[0] < selected.size();
                case "getObject" -> {
                    maximumValueRow.merge(table, cursor[0] + 1, Math::max);
                    String column = columns[(int) args[0] - 1];
                    Map<String, Object> row = selected.get(cursor[0]);
                    if (column.equals("OCTET_LENGTH(original_zip)")) yield (long) ((byte[]) row.get("original_zip")).length;
                    assertTrue(row.containsKey(column), column);
                    yield row.get(column);
                }
                case "getBinaryStream" -> {
                    assertEquals(1, args[0]); blobReads++;
                    byte[] body = streamOverride == null ? (byte[]) selected.get(cursor[0]).get("original_zip") : streamOverride;
                    yield new ByteArrayInputStream(body) {
                        @Override public synchronized byte[] readNBytes(int maximum) {
                            byte[] value = new byte[Math.min(maximum, available())];
                            read(value, 0, value.length); streamBytes += value.length; return value;
                        }
                        @Override public void close() { streamCloses++; }
                    };
                }
                case "close" -> { result.closed = true; hit("rsClose:" + table); yield null; }
                default -> throw new AssertionError("Unexpected result method " + method.getName());
            };
        });
    }
    void hit(String event) throws SQLException { events.add(event); observer.accept(event); if (failures.contains(event)) throw new SQLException(event); }
    void assertCompleted() { assertTrue(autoCommit); assertFalse(readOnly); assertEquals(Connection.TRANSACTION_READ_COMMITTED, isolation); assertEquals(1, closes); assertResourcesClosed(); }
    void assertResourcesClosed() { resources.forEach(resource -> assertTrue(resource.closed)); }

    private static String table(String sql) {
        for (String table : List.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_execution_change", "rule_schema2_combat_change", "rule_schema2_health", "rule_schema2_vital_effect", "rule_schema2_combat", "rule_schema2_weapon_attack", "rule_schema2_movement"))
            if (sql.contains(" FROM " + table + " ORDER BY artifact_module_key, release_version, author_index")) return table;
        return switch (sql.replaceAll("\\s+", " ").trim()) {
            case "SELECT CAST(DATABASE() AS BINARY) AS schema_name" -> "schema";
            case "SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version ASC" -> "ledger";
            case "SELECT control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version FROM rule_schema2_control ORDER BY control_id" -> "control";
            case "SELECT module_key, release_version, installation_sequence, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, OCTET_LENGTH(original_zip), installed_at FROM rule_schema2_artifact ORDER BY installation_sequence" -> "artifact";
            case "SELECT module_key, release_version, acceptance_sequence, acceptance_operation_id, operation_fingerprint_version, operation_digest_sha256, installed_operation_id, installed_operation_digest_sha256, installation_sequence, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, review_sha256, accepted_at FROM rule_schema2_acceptance ORDER BY acceptance_sequence" -> "acceptance";
            case "SELECT original_zip FROM rule_schema2_artifact WHERE module_key = ? AND release_version = ?" -> "zip";
            default -> throw new AssertionError("Forbidden source SQL: " + sql);
        };
    }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) row.put((String) pairs[index], pairs[index + 1]);
        return row;
    }
    private static List<Map<String, Object>> list(Map<String, Object> row) { return new ArrayList<>(List.of(row)); }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler)); }
    private static final class Resource { boolean closed; }
}
