package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2EquipmentHealthSnapshotTest {
    private static final Identity ID=Identity.random();
    private static final ProductionSchema2Projection MATERIAL=Schema2EquipmentHealthFixtures.projection();
    private static final JdbcSchema2RuntimeSnapshotRepository REPO=new JdbcSchema2RuntimeSnapshotRepository();
    private static final List<String> TABLES=List.of("s2_runtime_equipment_item","s2_runtime_health","s2_runtime_vital_effect");
    @Test void signedDiceMechanicsExactReferencesScopedClearsAndCompleteChainsRoundTrip()throws Exception {
        var fixture=stored();assertEquals(6,fixture.rows(TABLES.get(0)).size());assertEquals(1,fixture.rows(TABLES.get(1)).size());assertEquals(4,fixture.rows(TABLES.get(2)).size());
        assertTrue(fixture.rows("s2_runtime_vital_effect").stream().anyMatch(row->row.get("modifier").equals(-3)));
        var actual=REPO.read(fixture.connection,ID).projection();assertArrayEquals(MATERIAL.finalRulesJson(),actual.finalRulesJson());assertArrayEquals(MATERIAL.chainsJson(),actual.chainsJson());assertEquals(MATERIAL.finalRulesSha256(),actual.finalRulesSha256());assertEquals(MATERIAL.chainSha256(),actual.chainSha256());
    }
    @Test void everyTypedColumnAndInvalidOriginalSignedChainValueAreIndependentlyRejected()throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations=new ArrayList<>();
        for(String table:TABLES) {
            mutations.add(f->f.rows(table).removeFirst());mutations.add(f->f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst())));
            for(String column:stored().rows(table).getFirst().keySet())mutations.add(f->{var row=f.rows(table).getFirst();Object value=row.get(column);row.put(column,value instanceof byte[] bytes?ascii(new String(bytes,StandardCharsets.US_ASCII)+"x"):value instanceof Integer n?n+1:"wrong");});
        }
        mutations.add(f->{var chain=f.rows("s2_runtime_field_chain").stream().filter(row->Arrays.equals(ascii("modifier"),(byte[])row.get("field_key"))&&Arrays.equals(ascii("vital_effect.converted"),(byte[])row.get("rule_key"))).findFirst().orElseThrow();var steps=f.rows("s2_runtime_chain_step").stream().filter(row->row.get("chain_index").equals(chain.get("chain_index"))).toList();steps.getFirst().put("after_integer_value",-1001);steps.getLast().put("before_integer_value",-1001);});
        for(var mutation:mutations){var fixture=stored();mutation.accept(fixture);assertThrows(SQLException.class,()->REPO.read(fixture.connection,ID));}
    }
    @Test void failedNewDomainInsertRollsBackCompleteCandidate()throws Exception {
        var writes=stored().sql.stream().filter(sql->sql.startsWith("INSERT INTO ")).toList();
        for(String table:TABLES) {
            int index=1+writes.indexOf(writes.stream().filter(sql->sql.startsWith("INSERT INTO "+table+" (")).findFirst().orElseThrow());var fixture=new Schema2RuntimeJdbcFixture();fixture.failWrite=index;
            try(var tx=BusinessWriteTestSupport.internal(fixture.connection)){assertThrows(SQLException.class,()->REPO.store(tx.connection(),ID,MATERIAL));tx.rollback();}assertTrue(fixture.tables.isEmpty());assertEquals(0,fixture.commits);
        }
    }
    private static Schema2RuntimeJdbcFixture stored()throws Exception{var fixture=new Schema2RuntimeJdbcFixture();try(var tx=BusinessWriteTestSupport.internal(fixture.connection)){REPO.store(tx.connection(),ID,MATERIAL);tx.commit();}return fixture;}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
}
