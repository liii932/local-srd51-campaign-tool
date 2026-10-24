package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2ProgressionFixtures;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2ProgressionSnapshotTest {
    private static final JdbcSchema2RuntimeSnapshotRepository REPOSITORY = new JdbcSchema2RuntimeSnapshotRepository();
    private static final Identity IDENTITY = Identity.random();
    @Test void actualTypedRulesAndCompleteOverrideChainsRoundTripAcrossModules() throws Exception {
        var material = Schema2ProgressionFixtures.projection(); var fixture = stored();
        var progression = fixture.rows("s2_runtime_class_progression").getFirst();
        assertArrayEquals(ascii("growth_base"), (byte[]) progression.get("owner_class_module"));
        assertArrayEquals(ascii("class.guard"), (byte[]) progression.get("owner_class_key"));
        assertEquals(10, progression.get("hit_die_sides")); assertEquals(3, progression.get("supported_maximum_level"));
        assertEquals("1-4:2,5-8:3,9-12:4,13-16:5,17-20:6", progression.get("proficiency_bonus_profile"));
        assertArrayEquals(ascii("STATISTICS_AND_RESOURCES_ONLY"), (byte[]) progression.get("advancement_scope"));
        var resources = fixture.rows("s2_runtime_resource"); assertEquals(4, resources.size());
        var base = resources.stream().filter(row -> Arrays.equals(ascii("growth_base"), (byte[]) row.get("module_key")) && Arrays.equals(ascii("resource.energy"), (byte[]) row.get("rule_key"))).findFirst().orElseThrow();
        assertEquals("1-2:3,3-20:4", base.get("maximum_profile")); assertEquals("1-20:LONG_REST", base.get("recovery_profile"));
        var chain = fixture.rows("s2_runtime_field_chain").stream().filter(row -> Arrays.equals(ascii("growth_base"), (byte[]) row.get("rule_module_key")) && Arrays.equals(ascii("resource.energy"), (byte[]) row.get("rule_key")) && Arrays.equals(ascii("maximum_profile"), (byte[]) row.get("field_key"))).findFirst().orElseThrow();
        var steps = fixture.rows("s2_runtime_chain_step").stream().filter(row -> row.get("chain_index").equals(chain.get("chain_index"))).toList();
        assertEquals(List.of("1-2:2,3-20:3", "1-2:3,3-20:4"), steps.stream().map(row -> row.get("after_text_value")).toList());
        assertEquals("1-2:2,3-20:3", steps.getLast().get("before_text_value"));
        var actual = REPOSITORY.read(fixture.connection, IDENTITY).projection();
        assertArrayEquals(material.finalRulesJson(), actual.finalRulesJson()); assertArrayEquals(material.chainsJson(), actual.chainsJson());
        assertEquals(material.compositionSha256(), actual.compositionSha256()); assertEquals(1, fixture.commits);
    }
    @Test void missingExtraWrongTypedOwnerProfileScopeOrChainCannotBeRead() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = new ArrayList<>();
        for (String table : List.of("s2_runtime_class_progression", "s2_runtime_resource")) {
            mutations.add(f -> f.rows(table).removeFirst());
            mutations.add(f -> f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst())));
            mutations.add(f -> f.rows(table).getFirst().put("snapshot_id", Identity.random().snapshotBytes()));
            mutations.add(f -> f.rows(table).getFirst().put("owner_class_module", ascii("missing")));
            mutations.add(f -> f.rows(table).getFirst().put("owner_class_type", ascii("character.race")));
        }
        mutations.add(f -> f.rows("s2_runtime_class_progression").getFirst().put("supported_maximum_level", 21));
        mutations.add(f -> f.rows("s2_runtime_class_progression").getFirst().put("hit_die_sides", 20));
        mutations.add(f -> f.rows("s2_runtime_class_progression").getFirst().put("advancement_scope", ascii("COMPLETE_CLASS")));
        mutations.add(f -> f.rows("s2_runtime_resource").getFirst().put("maximum_profile", "1-20:arbitrary"));
        mutations.add(f -> f.rows("s2_runtime_resource").getFirst().put("execution_mode", ascii("CLIENT")));
        mutations.add(f -> f.rows("s2_runtime_resource").getFirst().put("recovery_profile", "x".repeat(201)));
        mutations.add(f -> {
            var ownerChain = f.rows("s2_runtime_field_chain").stream().filter(row -> Arrays.equals(ascii("owner_class"), (byte[]) row.get("field_key"))).findFirst().orElseThrow();
            f.rows("s2_runtime_chain_step").stream().filter(row -> row.get("chain_index").equals(ownerChain.get("chain_index"))).findFirst().orElseThrow().put("after_reference_rule_key", ascii("class.missing"));
        });
        for (var mutation : mutations) { var fixture = stored(); mutation.accept(fixture); assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, IDENTITY)); }
    }
    @Test void newDomainWriteFailureRequiresCallerRollbackAndLeavesNoPartialMaterial() throws Exception {
        var material = Schema2ProgressionFixtures.projection(); var good = stored();
        var inserts = good.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList();
        for (int i = 0; i < inserts.size(); i++) if (inserts.get(i).startsWith("INSERT INTO s2_runtime_class_progression (") || inserts.get(i).startsWith("INSERT INTO s2_runtime_resource (")) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = i + 1;
            try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) { assertThrows(SQLException.class, () -> REPOSITORY.store(transaction.connection(), IDENTITY, material)); transaction.rollback(); }
            assertTrue(fixture.tables.isEmpty()); assertEquals(0, fixture.commits);
        }
    }
    @Test void fullGrowthAndRestRulesEntriesAndAtomicPrerequisitesRoundTripAsTypedRows() throws Exception {
        var material = Schema2ProgressionFixtures.fullProjection(); var fixture = storedFull();
        assertEquals(2, fixture.rows("s2_runtime_class_progression").size());
        assertEquals(2, fixture.rows("s2_runtime_subclass").size());
        assertEquals(5, fixture.rows("s2_runtime_feature").size());
        assertEquals(1, fixture.rows("s2_runtime_feat").size());
        assertEquals(6, fixture.rows("s2_runtime_proficiency").size());
        assertEquals(1, fixture.rows("s2_runtime_proficiency_set").size());
        assertEquals(10, fixture.rows("s2_runtime_class_level_entry").size());
        assertEquals(3, fixture.rows("s2_runtime_proficiency_entry").size());
        assertEquals(6, fixture.rows("s2_runtime_growth_grant").size());
        assertEquals(1, fixture.rows("s2_runtime_rest").size());
        assertEquals(87, fixture.rows("s2_runtime_rest").getFirst().get("source_page"));
        assertEquals(4, fixture.rows("s2_runtime_rule_prerequisite").size());
        assertEquals(4, fixture.rows("s2_runtime_chain_prerequisite").size());
        assertTrue(fixture.rows("s2_runtime_chain_step").stream().anyMatch(row -> Arrays.equals(ascii("character.subclass"), (byte[]) row.get("after_reference_rule_type"))));
        var restored = REPOSITORY.read(fixture.connection, IDENTITY).projection();
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertEquals(material.finalRulesSha256(), restored.finalRulesSha256());
    }
    @Test void everyNewGrowthDomainAndBothAtomsAreIndependentlyReadAndRejectDamagedRows() throws Exception {
        List<String> tables = List.of("s2_runtime_subclass", "s2_runtime_feature", "s2_runtime_feat", "s2_runtime_proficiency", "s2_runtime_proficiency_set", "s2_runtime_rest", "s2_runtime_class_level_entry", "s2_runtime_proficiency_entry", "s2_runtime_growth_grant", "s2_runtime_rule_prerequisite", "s2_runtime_chain_prerequisite");
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = new ArrayList<>();
        for (String table : tables) {
            mutations.add(f -> f.rows(table).removeFirst());
            mutations.add(f -> f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst())));
            mutations.add(f -> f.rows(table).getFirst().put("snapshot_id", Identity.random().snapshotBytes()));
        }
        mutations.add(f -> f.rows("s2_runtime_rule_prerequisite").getFirst().put("group_index", 4));
        mutations.add(f -> f.rows("s2_runtime_chain_prerequisite").getFirst().put("phase", ascii("BEFORE")));
        mutations.add(f -> f.rows("s2_runtime_growth_grant").getFirst().put("growth_target_type", ascii("character.language")));
        mutations.add(f -> f.rows("s2_runtime_rest").getFirst().put("algorithm", ascii("CUSTOM_REST")));
        for (var mutation : mutations) { var fixture = storedFull(); mutation.accept(fixture); assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, IDENTITY)); }
    }
    @Test void eachNewGrowthDomainWriteFailureRollsBackTheCompleteCandidate() throws Exception {
        var material = Schema2ProgressionFixtures.fullProjection(); var good = storedFull();
        var writes = good.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList(); Set<String> seen = new HashSet<>();
        for (int index = 0; index < writes.size(); index++) {
            String table = writes.get(index).substring("INSERT INTO ".length(), writes.get(index).indexOf(" ("));
            if (!Set.of("s2_runtime_subclass", "s2_runtime_feature", "s2_runtime_feat", "s2_runtime_proficiency", "s2_runtime_proficiency_set", "s2_runtime_rest", "s2_runtime_class_level_entry", "s2_runtime_proficiency_entry", "s2_runtime_growth_grant", "s2_runtime_rule_prerequisite", "s2_runtime_chain_prerequisite").contains(table) || !seen.add(table)) continue;
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = index + 1;
            try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) { assertThrows(SQLException.class, () -> REPOSITORY.store(transaction.connection(), IDENTITY, material)); transaction.rollback(); }
            assertTrue(fixture.tables.isEmpty());
        }
        assertEquals(11, seen.size());
    }
    private static Schema2RuntimeJdbcFixture storedFull() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture();
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) { REPOSITORY.store(transaction.connection(), IDENTITY, Schema2ProgressionFixtures.fullProjection()); transaction.commit(); }
        return fixture;
    }
    private static Schema2RuntimeJdbcFixture stored() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture();
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) { REPOSITORY.store(transaction.connection(), IDENTITY, Schema2ProgressionFixtures.projection()); transaction.commit(); }
        return fixture;
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
