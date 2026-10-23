package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real adapter with row-based JDBC test storage; no claim of MySQL engine verification. */
class JdbcSchema2ToolSnapshotTest {
    private final JdbcSchema2RuntimeSnapshotRepository repository = new JdbcSchema2RuntimeSnapshotRepository();

    @Test void mixedLanguagesAndToolsReadBackEveryTypedFieldAndFullChain() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture();
        var identity = Identity.random();
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
            repository.store(transaction.connection(), identity, projection());
            assertEquals(37, fixture.rows("s2_runtime_tool").size());
            assertEquals(1, fixture.rows("s2_runtime_tool_set").size());
            assertFalse(fixture.rows("s2_runtime_language").isEmpty());
            assertFalse(fixture.rows("s2_runtime_language_entry").isEmpty());
            assertFalse(fixture.rows("s2_runtime_tool_entry").isEmpty());
            assertEquals(0, fixture.commits);
            assertEquals(0, fixture.rollbacks);
            transaction.commit();
        }
        var actual = repository.read(fixture.connection, identity).projection();
        assertArrayEquals(resource("expected/configuration.json"), actual.configurationJson());
        assertArrayEquals(resource("expected/final-rules.json"), actual.finalRulesJson());
        assertArrayEquals(resource("expected/chains.json"), actual.chainsJson());
    }

    @Test void toolRowsCannotBeOmittedCoercedOrSubstitutedWithLanguageReferences() throws Exception {
        for (Consumer<Schema2RuntimeJdbcFixture> mutation : List.<Consumer<Schema2RuntimeJdbcFixture>>of(
                f -> f.rows("s2_runtime_tool").removeLast(),
                f -> f.rows("s2_runtime_tool_set").clear(),
                f -> f.rows("s2_runtime_tool_entry").removeLast(),
                f -> f.rows("s2_runtime_tool").getFirst().put("category", ascii("STANDARD")),
                f -> f.rows("s2_runtime_tool").getFirst().put("enabled", 2),
                f -> f.rows("s2_runtime_tool").getFirst().put("source_page", "70"),
                f -> f.rows("s2_runtime_tool_entry").getFirst().put("tool_rule_key", ascii("language.common")),
                f -> f.rows("s2_runtime_tool_entry").getFirst().put("entry_index",
                        f.rows("s2_runtime_language_entry").getFirst().get("entry_index")),
                f -> f.rows("s2_runtime_tool").getFirst().put("rule_index",
                        f.rows("s2_runtime_language").getFirst().get("rule_index")))) {
            var fixture = new Schema2RuntimeJdbcFixture();
            var identity = Identity.random();
            storeAndCommit(fixture, identity);
            mutation.accept(fixture);
            assertThrows(SQLException.class, () -> repository.read(fixture.connection, identity));
        }
    }

    @Test void eachNewTypedTableFailureRollsBackWithTheCaller() throws Exception {
        var good = new Schema2RuntimeJdbcFixture();
        storeAndCommit(good, Identity.random());
        var inserts = good.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList();
        for (String table : List.of("s2_runtime_tool", "s2_runtime_tool_set", "s2_runtime_tool_entry")) {
            var fixture = new Schema2RuntimeJdbcFixture();
            fixture.failWrite = java.util.stream.IntStream.range(0, inserts.size())
                    .filter(i -> inserts.get(i).startsWith("INSERT INTO " + table + " ("))
                    .findFirst().orElseThrow() + 1;
            try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
                assertThrows(SQLException.class, () -> repository.store(transaction.connection(), Identity.random(), projection()));
                assertEquals(0, fixture.commits);
                assertEquals(0, fixture.rollbacks);
                transaction.rollback();
            }
            assertTrue(fixture.tables.isEmpty());
        }
    }

    private void storeAndCommit(Schema2RuntimeJdbcFixture fixture, Identity identity) throws SQLException {
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
            repository.store(transaction.connection(), identity, projection()); transaction.commit();
        }
    }

    private static ProductionSchema2Projection projection() {
        return ProductionSchema2Projection.compose(List.of("tools_base", "tools_extension", "tools_compatibility")
                .stream().map(module -> ProductionRuleArtifact.read(HexFormat.of().parseHex(
                        new String(resource(module + ".zip.hex"), StandardCharsets.US_ASCII).strip()))).toList());
    }

    private static byte[] resource(String path) {
        try (var input = JdbcSchema2ToolSnapshotTest.class.getResourceAsStream("/schema2-tools/" + path)) {
            return Objects.requireNonNull(input, path).readAllBytes();
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
