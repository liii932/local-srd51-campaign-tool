package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellSaveDamageSchemaTest {
    @Test void castStatusDomainIsExactAndBothForwardPayloadsMatchTheirApprovedMetadata()throws Exception {
        String runtime=Files.readString(Path.of("src/main/resources/db/migration/V042__schema2-spell-save-damage.sql"));
        String marker="cast_status IN (";int start=runtime.indexOf(marker);assertTrue(start>=0);assertEquals(start,runtime.lastIndexOf(marker));
        String domain=runtime.substring(start+marker.length(),runtime.indexOf(')',start));
        assertEquals("_binary'PENDING', _binary'HELD', _binary'RESOLVED', _binary'COUNTERED', _binary'INTERRUPTED'",domain);
        assertFalse(hasHexInDomain(runtime));
        String polluted=runtime.replace("cast_status IN (_binary'PENDING'","cast_status IN (_binary'"+"a".repeat(64)+"'");assertNotEquals(runtime,polluted);assertTrue(hasHexInDomain(polluted));
        var approved=SchemaMigrations.loadExpectations().stream().filter(e->e.version()==42).findFirst().orElseThrow();
        assertEquals(approved.scriptSha256(),SchemaMigrations.canonicalPayloadSha256(runtime));
        String source=Files.readString(Path.of("database/rules/migration/V012__schema2-spell-save-damage-source.sql"));
        var expected=RuleSchemaMigrations.expectations().stream().filter(e->e.version()==12).findFirst().orElseThrow();
        assertEquals(expected.scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));
        assertTrue(runtime.contains("AND (SELECT COUNT(*) FROM schema_meta)=41"));assertTrue(runtime.contains("column_name='arcane_recovery_day')=1"));
        assertTrue(source.contains("AND (SELECT COUNT(*) FROM rule_schema_meta)=11"));
    }
    private static boolean hasHexInDomain(String sql) {
        int at=0;while((at=sql.indexOf(" IN (",at))>=0) {int end=sql.indexOf(')',at);if(end<0)throw new AssertionError("Unclosed SQL value domain");
            if(Pattern.compile("'[0-9a-f]{64}'").matcher(sql.substring(at,end)).find())return true;at=end+1;
        }return false;
    }
}
