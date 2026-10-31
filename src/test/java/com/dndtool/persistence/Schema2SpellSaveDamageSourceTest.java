package com.dndtool.persistence;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellSaveDamageSourceTest {
    @Test void executableSourceAndSnapshotRetainTheExactAlgorithmAndRejectMetadataSubstitution()throws Exception {
        var artifacts=Schema2SpellSaveDamageFixtures.artifacts();var source=new Schema2RuntimeJdbcFixture();
        for(var artifact:artifacts)JdbcSchema2CharacterSourceRepository.store(source.connection,artifact);
        JdbcSchema2CharacterSourceRepository.verifyAll(source.connection,artifacts);
        var row=source.rows("rule_schema2_spell").stream().filter(r->Arrays.equals(ascii("SRD_2014_BURNING_HANDS_V1"),(byte[])r.get("algorithm"))).findFirst().orElseThrow();
        row.put("algorithm",ascii("SRD_2014_BURNING_HANDS_METADATA_V1"));assertThrows(SQLException.class,()->JdbcSchema2CharacterSourceRepository.verifyAll(source.connection,artifacts));
        var identity=Identity.random();var runtime=new Schema2RuntimeJdbcFixture();var repository=new JdbcSchema2RuntimeSnapshotRepository();var material=Schema2SpellSaveDamageFixtures.projection();
        try(var tx=BusinessWriteTestSupport.internal(runtime.connection)){repository.store(tx.connection(),identity,material);tx.commit();}
        assertArrayEquals(material.finalRulesJson(),repository.read(runtime.connection,identity).projection().finalRulesJson());
        var physical=runtime.rows("s2_runtime_spell").stream().filter(r->Arrays.equals(ascii("SRD_2014_BURNING_HANDS_V1"),(byte[])r.get("algorithm"))).findFirst().orElseThrow();
        physical.put("source_page",124);assertThrows(SQLException.class,()->repository.read(runtime.connection,identity));
    }
    private static byte[] ascii(String s){return s.getBytes(StandardCharsets.US_ASCII);}
}
