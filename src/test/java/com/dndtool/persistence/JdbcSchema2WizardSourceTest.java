package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2WizardSourceTest {
    private static final List<ProductionRuleArtifact> ARTIFACTS=Schema2WizardFixtures.artifacts();
    private static final List<String> TABLES=List.of("rule_schema2_wizard_spellcasting","rule_schema2_environment","rule_schema2_spellbook_member","rule_schema2_wizard_change");
    @Test void originalWizardSlotsLearningMembersAndEnvironmentalSourcesRoundTrip()throws Exception {
        var fixture=stored();assertEquals(1,fixture.rows(TABLES.get(0)).size());assertEquals(1,fixture.rows(TABLES.get(1)).size());assertEquals(19,fixture.rows(TABLES.get(2)).size());assertEquals(1,fixture.rows(TABLES.get(3)).size());
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS);
        var independent=new Schema2SourceReadFixture(ARTIFACTS);assertEquals(19,independent.rows().get("rule_schema2_spellbook_member").size());
        assertEquals(3,independent.load(independent.selections()).artifacts().size());
    }
    @Test void everyTypedColumnMissingRowAndOrphanRowIsComparedToAllOriginalZips()throws Exception {
        for(String table:TABLES) {
            for(String column:stored().rows(table).getFirst().keySet()) {
                var fixture=stored();var row=fixture.rows(table).getFirst();Object value=row.get(column);row.put(column,value instanceof byte[] b?ascii(new String(b,StandardCharsets.US_ASCII)+"x"):value instanceof Integer n?n+1:"wrong");
                assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS),table+"/"+column);
            }
            var missing=stored();missing.rows(table).removeFirst();assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(missing.connection,ARTIFACTS));
            var extra=stored();extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(extra.connection,ARTIFACTS));
        }
    }
    @Test void eachNewDomainWriteFailureLeavesRollbackToCaller()throws Exception {
        var writes=stored().sql.stream().filter(s->s.startsWith("INSERT INTO ")).toList();
        for(String table:TABLES) {
            int index=1+writes.indexOf(writes.stream().filter(s->s.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());
            var fixture=new Schema2RuntimeJdbcFixture();fixture.failWrite=index;
            assertThrows(SQLException.class,()->{for(var a:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(fixture.connection,a);});
            assertEquals(0,fixture.commits);assertEquals(0,fixture.rollbacks);fixture.connection.rollback();assertTrue(fixture.tables.isEmpty());
        }
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var f=new Schema2RuntimeJdbcFixture();for(var a:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(f.connection,a);return f;}
    private static byte[] ascii(String v){return v.getBytes(StandardCharsets.US_ASCII);}
}
