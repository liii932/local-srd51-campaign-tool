package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CheckFixtures;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CheckSnapshotTest {
    private static final Identity ID = Identity.random();
    private static final JdbcSchema2RuntimeSnapshotRepository REPO = new JdbcSchema2RuntimeSnapshotRepository();
    @Test void typedCheckAndCompleteSourceChainRestoreIdenticalProductionBytes() throws Exception {
        var fixture = stored(); var row = fixture.rows("s2_runtime_check").getFirst();
        assertEquals(77, row.get("source_page")); assertEquals("Reviewed ability check", row.get("display_name"));
        assertArrayEquals(ascii("SRD_2014_ABILITY_CHECK_V1"), (byte[]) row.get("algorithm"));
        var original = Schema2CheckFixtures.projection(); var actual = REPO.read(fixture.connection, ID).projection();
        assertArrayEquals(original.finalRulesJson(), actual.finalRulesJson()); assertArrayEquals(original.chainsJson(), actual.chainsJson());
        assertEquals(original.finalRulesSha256(), actual.finalRulesSha256()); assertEquals(original.chainSha256(), actual.chainSha256());
    }
    @Test void everyTypedRuleFieldAndOriginalChainAtomAreValidated() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = new ArrayList<>();
        mutations.add(f -> f.rows("s2_runtime_check").removeFirst());
        mutations.add(f -> f.rows("s2_runtime_check").add(new LinkedHashMap<>(f.rows("s2_runtime_check").getFirst())));
        for (String column : stored().rows("s2_runtime_check").getFirst().keySet()) mutations.add(f -> {
            var row = f.rows("s2_runtime_check").getFirst(); Object value = row.get(column);
            row.put(column, value instanceof byte[] bytes ? ascii(new String(bytes, StandardCharsets.US_ASCII) + "x") : value instanceof Integer n ? n + 1 : "wrong");
        });
        mutations.add(f -> {
            var chain = f.rows("s2_runtime_field_chain").stream().filter(r -> Arrays.equals(ascii("algorithm"), (byte[]) r.get("field_key"))).findFirst().orElseThrow();
            var steps = f.rows("s2_runtime_chain_step").stream().filter(r -> r.get("chain_index").equals(chain.get("chain_index"))).toList();
            steps.getFirst().put("after_text_value", "UNKNOWN_ORIGINAL_ALGORITHM");
            steps.getLast().put("before_text_value", "UNKNOWN_ORIGINAL_ALGORITHM");
        });
        for (var mutation : mutations) { var fixture = stored(); mutation.accept(fixture); assertThrows(SQLException.class, () -> REPO.read(fixture.connection, ID)); }
    }
    @Test void failedTypedCheckInsertRollsBackTheWholeCandidate() throws Exception {
        var fixture = stored(); var writes = fixture.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList();
        int failure = 1 + writes.indexOf(writes.stream().filter(sql -> sql.startsWith("INSERT INTO s2_runtime_check (")).findFirst().orElseThrow());
        var failed = new Schema2RuntimeJdbcFixture(); failed.failWrite = failure;
        try (var tx = BusinessWriteTestSupport.internal(failed.connection)) { assertThrows(SQLException.class, () -> REPO.store(tx.connection(), ID, Schema2CheckFixtures.projection())); tx.rollback(); }
        assertTrue(failed.tables.isEmpty()); assertEquals(0, failed.commits);
    }
    private static Schema2RuntimeJdbcFixture stored() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture();
        try (var tx = BusinessWriteTestSupport.internal(fixture.connection)) { REPO.store(tx.connection(), ID, Schema2CheckFixtures.projection()); tx.commit(); }
        return fixture;
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
