package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2MonsterSnapshotTest {
    private static final Identity ID=Identity.random();
    private static final ProductionSchema2Projection MATERIAL=Schema2MonsterFixtures.projection();
    private static final JdbcSchema2RuntimeSnapshotRepository REPO=new JdbcSchema2RuntimeSnapshotRepository();
    private static final List<String> TABLES=List.of("s2_runtime_monster","s2_runtime_monster_action","s2_runtime_monster_stat","s2_runtime_monster_save","s2_runtime_monster_skill","s2_runtime_monster_defense","s2_runtime_monster_condition","s2_runtime_monster_language","s2_runtime_monster_trait","s2_runtime_monster_action_value");
    @Test void typedWholeAtomsAndEveryBeforeAfterOrdinalRestoreWithoutLatestSourceReads()throws Exception {
        var f=stored();var actual=REPO.read(f.connection,ID).projection();assertEquals(3,f.rows("s2_runtime_monster").size());assertEquals(6,f.rows("s2_runtime_monster_action").size());assertArrayEquals(MATERIAL.finalRulesJson(),actual.finalRulesJson());assertArrayEquals(MATERIAL.chainsJson(),actual.chainsJson());assertEquals(MATERIAL.finalRulesSha256(),actual.finalRulesSha256());assertEquals(MATERIAL.chainSha256(),actual.chainSha256());assertTrue(f.sql.stream().noneMatch(q->q.contains(" FROM rule_schema2_")));
    }
    @Test void everyPhysicalColumnAndMissingExtraCrossSnapshotOrOrphanRowsFailClosed()throws Exception {
        var f=stored();
        for(String table:TABLES){var rows=f.rows(table);assertFalse(rows.isEmpty(),table);var row=rows.getFirst();for(String column:List.copyOf(row.keySet())){Object old=row.get(column);row.put(column,JdbcSchema2MonsterSourceTest.corrupt(old));assertThrows(SQLException.class,()->REPO.read(f.connection,ID),table+"/"+column);row.put(column,old);}var removed=rows.removeFirst();assertThrows(SQLException.class,()->REPO.read(f.connection,ID),table+" missing");rows.addFirst(removed);rows.add(new LinkedHashMap<>(removed));assertThrows(SQLException.class,()->REPO.read(f.connection,ID),table+" duplicate");rows.removeLast();}
        var atom=f.rows("s2_runtime_monster_stat").getFirst();Object old=atom.get("owner_index");atom.put("owner_index",255);assertThrows(SQLException.class,()->REPO.read(f.connection,ID));atom.put("owner_index",old);
    }
    @Test void eachNewTableFailureRollsBackTheWholeCandidateAndDoesNotCommitInsideTheRepository()throws Exception {
        var writes=stored().sql.stream().filter(sql->sql.startsWith("INSERT INTO ")).toList();for(String table:TABLES){int fail=1+writes.indexOf(writes.stream().filter(sql->sql.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());var f=new Schema2RuntimeJdbcFixture();f.failWrite=fail;try(var tx=BusinessWriteTestSupport.internal(f.connection)){assertThrows(SQLException.class,()->REPO.store(tx.connection(),ID,MATERIAL));tx.rollback();}assertTrue(f.tables.isEmpty());assertEquals(0,f.commits);}
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var f=new Schema2RuntimeJdbcFixture();try(var tx=BusinessWriteTestSupport.internal(f.connection)){REPO.store(tx.connection(),ID,MATERIAL);tx.commit();}return f;}
}
