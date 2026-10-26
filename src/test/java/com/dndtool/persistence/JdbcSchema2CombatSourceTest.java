package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CombatSourceTest {
    private static final List<ProductionRuleArtifact> ARTIFACTS=Schema2CombatFixtures.artifacts();
    private static final List<String> TABLES=List.of("rule_schema2_combat","rule_schema2_weapon_attack","rule_schema2_movement","rule_schema2_combat_change");
    @Test void originalTypedMechanicsReferencesAndOperationsExactlyMatchTheOriginalZip() throws Exception {
        var fixture=stored();assertEquals(1,fixture.rows(TABLES.get(0)).size());assertEquals(1,fixture.rows(TABLES.get(1)).size());assertEquals(1,fixture.rows(TABLES.get(2)).size());assertEquals(4,fixture.rows(TABLES.get(3)).size());
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS);
        var source=new Schema2SourceReadFixture(ARTIFACTS);assertEquals(2,source.load(source.selections()).artifacts().size());
    }
    @Test void everyColumnMissingExtraAndUnselectedMechanismTamperingFailsClosed() throws Exception {
        for(String table:TABLES) {
            for(String column:stored().rows(table).getFirst().keySet()) {
                var fixture=stored();var row=fixture.rows(table).getFirst();Object value=row.get(column);
                row.put(column,value instanceof byte[] bytes?ascii(new String(bytes,StandardCharsets.US_ASCII)+"x"):value instanceof Integer n?n+1:"wrong");
                assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS),table+"/"+column);
            }
            var missing=stored();missing.rows(table).removeFirst();assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(missing.connection,ARTIFACTS));
            var extra=stored();extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(extra.connection,ARTIFACTS));
        }
        var source=new Schema2SourceReadFixture(ARTIFACTS);source.rows().get("rule_schema2_combat_change").getFirst().put("text_value","unselected changed");
        assertThrows(SQLException.class,()->source.load(source.selections().subList(0,1)));assertEquals(1,source.rollbacks);
    }
    @Test void eachNewTableWriteFailureRemainsCallerOwnedAndRollsBackEveryEarlierRow() throws Exception {
        var writes=stored().sql.stream().filter(sql->sql.startsWith("INSERT INTO ")).toList();
        for(String table:TABLES) {
            int index=1+writes.indexOf(writes.stream().filter(sql->sql.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());
            var fixture=new Schema2RuntimeJdbcFixture();fixture.failWrite=index;
            assertThrows(SQLException.class,()->{for(var artifact:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(fixture.connection,artifact);});
            assertEquals(0,fixture.commits);assertEquals(0,fixture.rollbacks);fixture.connection.rollback();assertTrue(fixture.tables.isEmpty());
        }
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var fixture=new Schema2RuntimeJdbcFixture();for(var artifact:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(fixture.connection,artifact);return fixture;}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
}
