package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2WizardSnapshotTest {
    private static final Identity ID=Identity.random();
    private static final ProductionSchema2Projection MATERIAL=Schema2WizardFixtures.projection();
    private static final JdbcSchema2RuntimeSnapshotRepository REPO=new JdbcSchema2RuntimeSnapshotRepository();
    private static final List<String> TABLES=List.of("s2_runtime_wizard_spellcasting","s2_runtime_environment","s2_runtime_spellbook_member");
    @Test void independentTypedWizardAndEnvironmentRestorePreservesCanonicalProjection()throws Exception {
        var f=stored();var p=REPO.read(f.connection,ID).projection();assertArrayEquals(MATERIAL.finalRulesJson(),p.finalRulesJson());assertArrayEquals(MATERIAL.chainsJson(),p.chainsJson());
    }
    @Test void everySnapshotColumnMissingAndExtraChildRowsFailClosed()throws Exception {
        for(String table:TABLES) {
            for(String column:stored().rows(table).getFirst().keySet()) {
                var f=stored();var row=f.rows(table).getFirst();Object v=row.get(column);row.put(column,v instanceof byte[] b?ascii(new String(b,StandardCharsets.US_ASCII)+"x"):v instanceof Integer n?n+1:"wrong");
                assertThrows(SQLException.class,()->REPO.read(f.connection,ID),table+"/"+column);
            }
            var missing=stored();missing.rows(table).removeFirst();assertThrows(SQLException.class,()->REPO.read(missing.connection,ID));
            var extra=stored();extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));assertThrows(SQLException.class,()->REPO.read(extra.connection,ID));
        }
    }
    @Test void newRuleAndMemberWriteFailureRollsBackCompleteCandidate()throws Exception {
        var writes=stored().sql.stream().filter(s->s.startsWith("INSERT INTO ")).toList();
        for(String table:TABLES) {
            int index=1+writes.indexOf(writes.stream().filter(s->s.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());var f=new Schema2RuntimeJdbcFixture();f.failWrite=index;
            try(var tx=BusinessWriteTestSupport.internal(f.connection)){assertThrows(SQLException.class,()->REPO.store(tx.connection(),ID,MATERIAL));tx.rollback();}assertTrue(f.tables.isEmpty());assertEquals(0,f.commits);
        }
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var f=new Schema2RuntimeJdbcFixture();try(var tx=BusinessWriteTestSupport.internal(f.connection)){REPO.store(tx.connection(),ID,MATERIAL);tx.commit();}return f;}
    private static byte[] ascii(String v){return v.getBytes(StandardCharsets.US_ASCII);}
}
