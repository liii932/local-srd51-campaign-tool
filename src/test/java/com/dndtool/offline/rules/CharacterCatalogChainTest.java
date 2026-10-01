package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assertions.*;
import com.dndtool.module.*;
import com.dndtool.persistence.JdbcRuntimeCharacterCatalogRepository;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSourceLanguageRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterCatalogChainTest {
    @TempDir Path temp;
    private final JdbcRuntimeCharacterCatalogRepository runtime=new JdbcRuntimeCharacterCatalogRepository();

    private LanguagePartitionJdbcFixture installed() throws Exception {
        var artifact=OfflineTestSupport.artifact(temp);
        Path state=OfflineTestSupport.privateDirectory(temp,"state");
        var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        var db=new SourceInstallationTest.Database();
        try(var store=new TicketStore(state)) {
            var ticket=OperationTicket.create(artifact,0,evidence);store.save(ticket);
            var result=new SourceInstallation(evidence,store,db::open).install(ticket,artifact);
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());
            assertEquals(List.of("character.language","character.tool"),result.acceptance().partitions());
        }
        return new LanguagePartitionJdbcFixture(db);
    }

    private static void assertIndependent(CharacterCatalogPartition actual) throws Exception {
        LanguagePartitionOracle.assertPartition(LanguagePartitionOracle.baseline(),actual.languages());
        assertEquals(37,actual.tools().tools().size());
        for(int i=0;i<37;i++) {
            var e=ToolCatalogOracle.rows().get(i);var a=actual.tools().tools().get(i);
            assertEquals(new ToolCatalogOracle.Row(a.toolKey(),a.displayName(),a.description(),a.category().name(),a.sourcePage(),a.sortOrder()),e);
        }
        assertArrayEquals(HexFormat.of().parseHex(Files.readString(Path.of("src/test/resources/module-canonical-v2-language-tools.hex")).strip()),
                new ModuleCanonicalEncoderV2().encode(actual.projection()));
    }

    @Test void authorInstalledSourceAndActualRuntimeRowsMatchIndependentOracleWithSourceOffline() throws Exception {
        var f=installed();var source=new JdbcSourceLanguageRepository(f.source()).loadCatalog();assertIndependent(source);
        assertEquals(37,f.installed.tools.size());
        var first=Identity.random();var second=Identity.random();
        try(var tx=f.runtime()) {
            assertIndependent(runtime.append(tx,first,source).partition());
            assertEquals(0,f.commits);assertEquals(0,f.rollbacks);tx.commit();
        }
        f.sourceOffline=true;
        try(var tx=f.runtime()) {
            assertIndependent(runtime.append(tx,second,source).partition());tx.commit();
        }
        int sourceQueries=f.sourceQueries;
        try(var tx=f.runtime()) {
            assertIndependent(runtime.read(tx,first).partition());assertIndependent(runtime.read(tx,second).partition());
            assertThrows(Exception.class,()->runtime.read(tx,new Identity(first.runId(),second.snapshotId())));tx.rollback();
        }
        assertEquals(sourceQueries,f.sourceQueries);
        assertEquals(74,f.tools.size());assertEquals(2,f.registrations.size());
    }

    @Test void everyToolWriteAndCallerFailureRollBackBothDomainsAndPermanentRegistration() throws Exception {
        var f=installed();var source=new JdbcSourceLanguageRepository(f.source()).loadCatalog();
        for(int point=1;point<=37;point++) {
            f.failToolWrite=point;f.toolWrites=0;
            try(var tx=f.runtime()) {
                assertThrows(Exception.class,()->runtime.append(tx,Identity.random(),source));tx.rollback();
            }
            assertTrue(f.tools.isEmpty());assertTrue(f.languages.isEmpty());assertTrue(f.heads.isEmpty());assertTrue(f.registrations.isEmpty());
        }
        f.failToolWrite=0;
        try(var tx=f.runtime()) {
            runtime.append(tx,Identity.random(),source);tx.rollback(); // Later caller work fails.
        }
        assertTrue(f.tools.isEmpty());assertTrue(f.languages.isEmpty());assertTrue(f.registrations.isEmpty());
    }

    @Test void corruptStoredToolFieldsAndCrossSnapshotRowsRejectWithoutReturningAView() throws Exception {
        var f=installed();var source=new JdbcSourceLanguageRepository(f.source()).loadCatalog();var id=Identity.random();
        try(var tx=f.runtime()){runtime.append(tx,id,source);tx.commit();}
        for(String field:List.of("tool_key","display_name","description","category","source_page","sort_order","snapshot_id")) {
            f.toolFault=rows->rows.get(0).put(field,switch(field) {
                case "source_page"->75;case "sort_order"->2;case "snapshot_id"->Identity.random().snapshotBytes();
                case "tool_key","category"->LanguagePartitionJdbcFixture.b("unknown");default->"\u0085";
            });
            try(var tx=f.runtime()){assertThrows(Exception.class,()->runtime.read(tx,id),field);tx.rollback();}
        }
        f.toolFault=rows->rows.remove(0);
        try(var tx=f.runtime()){assertThrows(Exception.class,()->runtime.read(tx,id));tx.rollback();}
        f.toolFault=rows->rows.add(rows.get(0));
        try(var tx=f.runtime()){assertThrows(Exception.class,()->runtime.read(tx,id));tx.rollback();}
    }

    @Test void corruptToolSourceRejectsWholeReadAndRuntimeReadbackRollsBack() throws Exception {
        var f=installed();var source=new JdbcSourceLanguageRepository(f.source()).loadCatalog();
        Object page=f.installed.tools.get(0)[5];f.installed.tools.get(0)[5]="70";
        assertThrows(Exception.class,()->new JdbcSourceLanguageRepository(f.source()).loadCatalog());
        f.installed.tools.get(0)[5]=page;
        f.toolFault=rows->rows.get(0).put("display_name","Changed but valid");
        try(var tx=f.runtime()) {
            assertThrows(Exception.class,()->runtime.append(tx,Identity.random(),source));tx.rollback();
        }
        assertTrue(f.registrations.isEmpty());assertTrue(f.heads.isEmpty());assertTrue(f.languages.isEmpty());assertTrue(f.tools.isEmpty());
    }
}
