package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.dndtool.module.ToolCatalogOracle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source checks only; isolated MySQL must separately validate effective constraints and privileges. */
class ToolCatalogSchemaTest {
    @Test void bothForwardTablesPreserveEveryKeyAndTypedColumnAndHaveIndependentLedgers() throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V002__tool-catalog.sql"));
        String runtime=Files.readString(Path.of("src/main/resources/db/migration/V020__runtime-tool-snapshot.sql"));
        for(String sql:new String[]{source,runtime}) {
            for(var row:ToolCatalogOracle.rows())assertTrue(sql.contains("_binary'"+row.key()+"'"),row.key());
            for(String column:new String[]{"tool_key VARBINARY(128)","display_name VARCHAR(120)","description VARCHAR(1000)",
                    "category VARBINARY(18)","source_page SMALLINT","sort_order SMALLINT","BETWEEN 1 AND 37",
                    "ON UPDATE RESTRICT ON DELETE RESTRICT","ENGINE=InnoDB"})assertTrue(sql.contains(column),column);
            assertFalse(sql.contains("DROP TABLE"));assertFalse(sql.contains("FOREIGN_KEY_CHECKS"));
        }
        assertTrue(source.contains("SELECT release_status INTO root_status"));
        assertTrue(runtime.contains("PRIMARY KEY (snapshot_id, tool_key)"));
        assertTrue(runtime.contains("Immutable runtime tool"));
        assertEquals(20,SchemaMigrations.loadExpectations().size());
        assertEquals(RuleSchemaMigrations.expectations().get(1).scriptSha256(),
                RuleSchemaMigrations.canonicalPayloadSha256(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
