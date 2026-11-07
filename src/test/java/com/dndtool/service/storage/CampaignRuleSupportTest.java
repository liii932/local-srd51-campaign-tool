package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2PreparationIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.storage.CampaignDomainData.Material;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRuleSupportTest {
    private static final String LOCAL = "local_table_rules";
    private static final String CORE = BundledCampaignRules.CORE_MODULE;
    private static final String CHARACTERS = BundledCampaignRules.CHARACTER_MODULE;

    @Test void acceptsNewContentAndCoverageByActualOwnersAcrossFivePackages() throws Exception {
        var originalCheck = authorRow(CHARACTERS, "rules.json", "rule_key", "check.srd2014");
        var newCheck = declare(originalCheck, LOCAL, "check.local");
        var newLanguage = declare(authorRow(CORE, "rules.json", "rule_key", "language.common"), LOCAL, "language.common");
        newLanguage.addProperty("display_name", "Local Common");
        var originalSlot = authorRow(CORE, "entries.json", "entry_key", "extra_language");
        var newSlot = declareEntry(originalSlot, LOCAL, "extra_language");
        var localGrant = declareEntry(authorRow(CORE, "entries.json", "entry_key", "trade_language"), LOCAL, "local_language");
        localGrant.getAsJsonObject("grant").add("target", newLanguage.getAsJsonObject("identity").deepCopy());
        var athletics = authorRow(CORE, "rules.json", "rule_key", "proficiency.skill.athletics");
        var change = operation("set_field", athletics);
        change.addProperty("field", "ability_key");
        change.addProperty("value", "ability.wisdom");
        var selected = BundledCampaignRules.artifacts();
        var external = extension(LOCAL, selected, List.of(newCheck, newLanguage), List.of(newSlot, localGrant),
                List.of(disabled(originalCheck), disabled(originalSlot), change));
        var material = compose(selected, external);
        var context = Material.of(material).restore();
        assertEquals(5, context.binding().members().size());
        assertEquals(new RuleIdentity(LOCAL, "adventure.check", "check.local"), context.checkRule());
        assertEquals(context.binding(), CampaignRuleSupport.validate(material).binding());

        var plan = context.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(),
                "fighter", "custom_artisan");
        assertTrue(plan.character().grants().stream().anyMatch(grant -> grant.slot().moduleKey().equals(LOCAL)
                && grant.slot().entryKey().equals("extra_language") && grant.target().ruleKey().equals("language.elvish")));
        assertTrue(plan.character().grants().stream().anyMatch(grant -> grant.target().equals(
                new RuleIdentity(LOCAL, "character.language", "language.common"))));
        var history = new CampaignDomainEngine().initial(context.projection(), plan, UUID.randomUUID(),
                context.initialAbilities("fighter"), context.initialChoices("fighter"), UUID.randomUUID());
        var checks = new Schema2CharacterCheckRules();
        var command = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(context.checkRule(), "SKILL", "ability.wisdom",
                        context.rule("character.proficiency", "proficiency.skill.athletics"), 11, false, false, "Check local Athletics."));
        var result = checks.resolve(checks.prepare(context.projection(), history.current(), command), 8, null);
        assertEquals(11, result.total());
        assertTrue(result.success());
        var catalog = context.metadata().catalog();
        assertTrue(catalog.stream().anyMatch(row -> row.key().equals("proficiency.skill.athletics")
                && row.category().equals("SKILL") && row.abilityKey().equals("ability.wisdom")));
        assertTrue(catalog.stream().anyMatch(row -> row.key().equals("check.srd2014") && !row.enabled() && !row.executable()));
        assertEquals(2, catalog.stream().filter(row -> row.key().equals("language.common")).count());
    }

    @Test void compositionRejectsMissingMechanismAndWeaponAttackBeforeRuntime() throws Exception {
        var selected = BundledCampaignRules.artifacts();
        var health = authorRow(CHARACTERS, "rules.json", "rule_key", "health.srd2014");
        var noHealth = compose(selected, extension(LOCAL, selected, List.of(), List.of(), List.of(disabled(health))));
        assertRejected("STORAGE_RULE_SUPPORT_HEALTH", noHealth);

        var weapon = declare(authorRow(CHARACTERS, "rules.json", "rule_key", "item.longsword"), LOCAL, "item.new_sword");
        var noAttack = compose(selected, extension(LOCAL, selected, List.of(weapon), List.of(), List.of()));
        assertRejected("STORAGE_RULE_SUPPORT_COMBAT", noAttack);
    }

    @Test void eightMemberLocalCompositionRestoresAndOldJdbcRejectsItBeforeSql() throws Exception {
        var selected = new ArrayList<>(BundledCampaignRules.artifacts());
        var common = authorRow(CORE, "rules.json", "rule_key", "language.common");
        for (int index = 1; index <= 4; index++) {
            String module = "local_metadata_" + index;
            var language = declare(common, module, "language.common");
            selected.add(extension(module, List.of(selected.getFirst()), List.of(language), List.of(), List.of()));
        }
        var projection = ProductionSchema2Projection.compose(selected);
        var context = Material.of(projection).restore();
        assertEquals(8, context.binding().members().size());
        assertEquals("local_metadata_4", context.binding().members().getLast().moduleKey());

        Connection untouched = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (connection, method, args) -> {
                    throw new AssertionError("Unsupported snapshot touched JDBC: " + method.getName());
                });
        var rejected = assertThrows(SQLException.class, () -> new JdbcSchema2RuntimeSnapshotRepository()
                .store(untouched, Schema2PreparationIdentity.random(), projection));
        assertEquals("Invalid schema 2 runtime snapshot", rejected.getMessage());
    }

    @Test void unownedAutomaticFeatureMustBeDisabledToRemainMetadata() throws Exception {
        var selected = new ArrayList<>(BundledCampaignRules.artifacts());
        var feature = declare(authorRow(CHARACTERS, "rules.json", "rule_key", "feature.life_domain_spellcasting"),
                LOCAL, "feature.unowned_automatic");
        feature.addProperty("execution_algorithm", "AUTOMATIC_RESOURCE_LIFECYCLE_V1");
        selected.add(extension(LOCAL, selected, List.of(feature), List.of(), List.of()));
        assertRejected("STORAGE_RULE_SUPPORT_FEATURE_OWNER", ProductionSchema2Projection.compose(selected));

        var repaired = compose(selected, extension("local_compatibility", selected, List.of(), List.of(), List.of(disabled(feature))));
        var context = Material.of(repaired).restore();
        var row = context.metadata().catalog().stream().filter(item -> item.key().equals("feature.unowned_automatic"))
                .findFirst().orElseThrow();
        assertFalse(row.enabled());
        assertFalse(row.executable());
        var plan = context.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "cleric", "acolyte");
        var history = new CampaignDomainEngine().initial(context.projection(), plan, UUID.randomUUID(),
                context.initialAbilities("cleric"), context.initialChoices("cleric"), UUID.randomUUID());
        assertEquals(List.of("feature.life_domain_spellcasting"), history.current().features().stream()
                .map(acquired -> acquired.feature().ruleKey()).toList());
    }

    @Test void localAdvancementCannotAcquireBlockedFeatOrRollForIt() {
        var context = Material.of(BundledCampaignRules.projection()).restore();
        var plan = context.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "fighter", "acolyte");
        var engine = new CampaignDomainEngine();
        var history = engine.initial(context.projection(), plan, UUID.randomUUID(), context.initialAbilities("fighter"),
                context.initialChoices("fighter"), UUID.randomUUID());
        var fighter = context.rule("character.class", "class.fighter");
        var champion = new GrowthChoices(context.rule("character.subclass", "subclass.champion"), Map.of(), null, Map.of());
        for (var choices : List.of(GrowthChoices.EMPTY, champion)) {
            history = engine.executeCharacter(context.projection(), plan, history,
                    new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", fighter, choices), UUID.randomUUID(),
                    sides -> { throw new AssertionError("Fixed advancement requested a die"); });
        }
        var atThirdLevel = history;
        var feat = new GrowthChoices(null, Map.of(), context.rule("character.feat", "feat.grappler"), Map.of());
        var failure = assertThrows(IllegalArgumentException.class, () -> engine.executeCharacter(context.projection(), plan,
                atThirdLevel, new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", fighter, feat),
                UUID.randomUUID(), sides -> { throw new AssertionError("Blocked feat requested a die"); }));
        assertEquals("S2_GROWTH_FEAT_UNSUPPORTED", failure.getMessage());
        assertEquals(3, atThirdLevel.current().level());
        assertTrue(atThirdLevel.current().feats().isEmpty());
    }

    @Test void selfConsistentInitialFactsMustStillUseTheCampaignCreationTemplate() {
        var empty = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "Template");
        var input = new JsonObject();
        input.addProperty("name", "Fighter");
        input.addProperty("kind", "PC");
        input.addProperty("classKey", "fighter");
        input.addProperty("backgroundKey", "acolyte");
        UUID operation = UUID.randomUUID();
        var state = new CampaignDomainCommands().apply(empty, "character-create", input, operation, "0".repeat(64),
                sides -> { throw new AssertionError("Character initialization requested a die"); });
        assertDoesNotThrow(() -> CampaignData.validate(new CampaignData.Snapshot(empty.campaignId(), empty.name(), state)));

        var character = state.characters().getFirst();
        var context = state.material().restore();
        var alternate = new java.util.HashMap<>(context.initialAbilities("fighter"));
        alternate.put("ability.strength", 13);
        alternate.put("ability.dexterity", 15);
        var engine = new CampaignDomainEngine();
        var changedHistory = engine.initial(context.projection(), character.initial(),
                character.history().current().preparationOperationId(), alternate, context.initialChoices("fighter"), operation);
        assertDoesNotThrow(() -> engine.validateCharacter(context.projection(), character.initial(), changedHistory));
        var changed = new CampaignDomainData.Character(character.name(), character.kind(), character.active(),
                character.initial(), changedHistory);
        var changedState = new CampaignDomainData.State(state.material(), List.of(changed), state.combats(), state.nodes(),
                state.positions(), state.items(), state.roots(), state.manual(), state.participation(), state.space());
        var rejected = assertThrows(StorageException.class, () -> CampaignData.validate(
                new CampaignData.Snapshot(empty.campaignId(), empty.name(), changedState)));
        assertEquals("INVALID_FILE", rejected.code());
        assertEquals("Complete facts differ from retained root consequences", rejected.getMessage());
    }

    private static void assertRejected(String code, ProductionSchema2Projection projection) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, () -> CampaignRuleSupport.validate(projection)).getMessage());
        var rejected = assertThrows(StorageException.class, Material.of(projection)::restore);
        assertEquals("INVALID_FILE", rejected.code());
        assertEquals(code, rejected.getCause().getMessage());
    }

    private static ProductionSchema2Projection compose(List<ProductionRuleArtifact> selected, ProductionRuleArtifact added) {
        var all = new ArrayList<>(selected);
        all.add(added);
        return ProductionSchema2Projection.compose(all);
    }

    static JsonObject authorRow(String module, String file, String identityKey, String value) {
        var artifact = BundledCampaignRules.artifacts().stream().filter(member -> member.moduleKey().equals(module))
                .findFirst().orElseThrow();
        var rows = JsonParser.parseString(new String(artifact.originalMembers().get(file), StandardCharsets.UTF_8)).getAsJsonArray();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            if (row.getAsJsonObject("identity").get(identityKey).getAsString().equals(value)) return row;
        }
        throw new AssertionError(value);
    }

    private static JsonObject declare(JsonObject row, String module, String key) {
        var declaration = row.deepCopy();
        var identity = declaration.getAsJsonObject("identity");
        identity.addProperty("module_key", module);
        identity.addProperty("rule_key", key);
        return declaration;
    }

    private static JsonObject declareEntry(JsonObject row, String module, String key) {
        var declaration = row.deepCopy();
        var identity = declaration.getAsJsonObject("identity");
        identity.addProperty("module_key", module);
        identity.addProperty("entry_key", key);
        return declaration;
    }

    static JsonObject operation(String type, JsonObject row) {
        var operation = new JsonObject();
        operation.addProperty("op", type);
        operation.add("target", row.getAsJsonObject("identity").deepCopy());
        return operation;
    }

    static JsonObject disabled(JsonObject row) {
        var operation = operation("set_enabled", row);
        operation.addProperty("value", false);
        return operation;
    }

    static ProductionRuleArtifact extension(String module, List<ProductionRuleArtifact> dependencies,
            List<JsonObject> definitions, List<JsonObject> entries, List<JsonObject> changes) throws IOException {
        var files = new JsonArray();
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            addFile(zip, files, "definitions", "rules.json", definitions);
            addFile(zip, files, "entries", "entries.json", entries);
            addFile(zip, files, "changes", "changes.json", changes);
            var manifest = new JsonObject();
            manifest.addProperty("author_schema_version", 2);
            manifest.addProperty("module_key", module);
            manifest.addProperty("release_version", "1");
            manifest.addProperty("package_display_name", "Local table rules");
            manifest.addProperty("role", "extension");
            var declaredDependencies = new JsonArray();
            var tuple = new JsonObject();
            for (var member : dependencies) {
                var dependency = new JsonObject();
                dependency.addProperty("module_key", member.moduleKey());
                var versions = new JsonArray();
                versions.add(member.releaseVersion());
                dependency.add("supported_versions", versions);
                declaredDependencies.add(dependency);
                tuple.addProperty(member.moduleKey(), member.releaseVersion());
            }
            var combinations = new JsonArray();
            combinations.add(tuple);
            manifest.add("dependencies", declaredDependencies);
            manifest.add("compatible_combinations", combinations);
            manifest.add("load_order_constraints", new JsonArray());
            manifest.add("files", files);
            stored(zip, "author-package.json", manifest.toString());
        }
        return ProductionRuleArtifact.read(output.toByteArray());
    }

    private static void addFile(ZipOutputStream zip, JsonArray files, String kind, String path, List<JsonObject> rows)
            throws IOException {
        if (rows.isEmpty()) return;
        var file = new JsonObject();
        file.addProperty("kind", kind);
        file.addProperty("path", path);
        files.add(file);
        var body = new JsonArray();
        rows.forEach(body::add);
        stored(zip, path, body.toString());
    }

    private static void stored(ZipOutputStream zip, String path, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var crc = new CRC32();
        crc.update(bytes);
        var entry = new ZipEntry(path);
        entry.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
