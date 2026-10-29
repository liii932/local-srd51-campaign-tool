package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2MonsterSourceTest {
    private static final List<ProductionRuleArtifact> ARTIFACTS=Schema2MonsterFixtures.artifacts();
    @Test void everyOriginalMonsterActionAndAtomicOverrideMatchesAnIndependentOriginalJsonOracle()throws Exception {
        var fixture=stored();assertEquals(3,fixture.rows("rule_schema2_monster").size());assertEquals(6,fixture.rows("rule_schema2_monster_action").size());JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS);
        var independent=new Schema2SourceReadFixture(ARTIFACTS);assertEquals(4,independent.load(independent.selections()).artifacts().size());assertEquals(1,independent.commitCount());assertEquals(0,independent.rollbacks);independent.assertCompleted();
    }
    @Test void everyColumnAndMissingExtraOrUnselectedAtomRejectsWithoutRepairingTheSource()throws Exception {
        var fixture=stored();
        for(String table:Schema2MonsterSourceFixtureRows.TABLES){var rows=fixture.rows(table);assertFalse(rows.isEmpty(),table);var row=rows.getFirst();for(String column:List.copyOf(row.keySet())){Object old=row.get(column);row.put(column,corrupt(old));assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS),table+"/"+column);row.put(column,old);}var removed=rows.removeFirst();assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS));rows.addFirst(removed);rows.add(new LinkedHashMap<>(removed));assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection,ARTIFACTS));rows.removeLast();}
        var independent=new Schema2SourceReadFixture(ARTIFACTS);independent.rows().get("rule_schema2_monster_action_value").getFirst().put("mode","tampered");assertThrows(SQLException.class,()->independent.load(independent.selections().subList(0,2)));assertEquals(0,independent.commitCount());assertEquals(1,independent.rollbacks);independent.assertCompleted();
    }
    @Test void eachTypedTableWriteFailureLeavesRollbackWithTheCaller()throws Exception {
        var writes=stored().sql.stream().filter(sql->sql.startsWith("INSERT INTO ")).toList();
        for(String table:Schema2MonsterSourceFixtureRows.TABLES){int fail=1+writes.indexOf(writes.stream().filter(sql->sql.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());var fixture=new Schema2RuntimeJdbcFixture();fixture.failWrite=fail;assertThrows(SQLException.class,()->{for(var artifact:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(fixture.connection,artifact);});assertEquals(0,fixture.commits);assertEquals(0,fixture.rollbacks);fixture.connection.rollback();assertTrue(fixture.tables.isEmpty());}
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var f=new Schema2RuntimeJdbcFixture();for(var a:ARTIFACTS)JdbcSchema2CharacterSourceRepository.store(f.connection,a);return f;}
    static Object corrupt(Object old){return old instanceof byte[] b?(new String(b,StandardCharsets.US_ASCII)+"x").getBytes(StandardCharsets.US_ASCII):old instanceof Number n?n.intValue()+1:"invalid";}
}
