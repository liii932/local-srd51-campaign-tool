package com.dndtool.offline.rules;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2OperationTicketTest {
    @TempDir Path temp;
    private Schema2OperationTicket vector() {
        return new Schema2OperationTicket(UUID.fromString("12345678-1234-4234-8234-123456789abc"),Schema2OperationTicket.Kind.INSTALL,
                "isolated-source","source-history-1",0,0,0,"empty_extension","01","EXTENSION","schema2-production-json-1",
                "a".repeat(64),"b".repeat(64),"none","none",0,"none");
    }
    @Test void independentPythonOrderedJsonVectorAndStrictRoundTrip() {
        // hashlib.sha256(domain + json.dumps(ordered explicit fields,separators=(',',':'))), independently calculated.
        assertEquals("1691789f6686bfeee4f3e51adb73b82f4e62216e71da8a8b5b426a2f8f874baa",vector().fingerprint());
        assertEquals(vector(),Schema2OperationTicket.decode(vector().encode()));
        String encoded=new String(vector().encode(),StandardCharsets.UTF_8);
        for(String changed:List.of(encoded.replace("\"expected_version\":0","\"expected_version\":1"),
                encoded.replace("\"01\"","\"1\""),encoded.replace("EXTENSION","BASE"),
                encoded.replace("\"author_schema_version\":2","\"author_schema_version\":1"),
                encoded.replace("schema2-production-json-1","validation-json-1"),encoded.replace("\"ticket_version\":2","\"ticket_version\":1")))
            assertThrows(IllegalArgumentException.class,()->Schema2OperationTicket.decode(changed.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void independentAcceptVectorBindsOriginalInstallationAndExplicitReview() {
        var ticket=new Schema2OperationTicket(UUID.fromString("12345678-1234-4234-8234-123456789abc"),Schema2OperationTicket.Kind.ACCEPT,
                "isolated-source","source-history-1",3,2,1,"empty_extension","01","EXTENSION","schema2-production-json-1",
                "a".repeat(64),"b".repeat(64),"87654321-4321-4321-8321-cba987654321","c".repeat(64),2,"d".repeat(64));
        // Independent Python ordered JSON + hashlib vector; ACCEPT-specific fields are explicit inputs.
        assertEquals("1c9d1cff248d19cc30883a2c5fe88c394451f3835cdce78913a949519a4b6802",ticket.fingerprint());
        assertEquals(ticket,Schema2OperationTicket.decode(ticket.encode()));
        String encoded=new String(ticket.encode(),StandardCharsets.UTF_8);
        for(String changed:List.of(encoded.replace(ticket.installedOperationId(),"87654321-4321-4321-8321-cba987654322"),
                encoded.replace(ticket.installedOperationDigest(),"e".repeat(64)),
                encoded.replace("\"installation_sequence\":2","\"installation_sequence\":1"),
                encoded.replace(ticket.reviewSha256(),"f".repeat(64)),
                encoded.replace("\"review_protocol_version\":1","\"review_protocol_version\":2")))
            assertThrows(IllegalArgumentException.class,()->Schema2OperationTicket.decode(changed.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void impossibleCountersMalformedIdentityAndUuidRefuse() {
        for(long[] counts:List.of(new long[]{17,17,0},new long[]{2,0,2},new long[]{2,1,0}))
            assertThrows(IllegalArgumentException.class,()->new Schema2OperationTicket(vector().operationId(),vector().kind(),vector().target(),vector().lineage(),counts[0],counts[1],counts[2],
                    vector().moduleKey(),vector().releaseVersion(),vector().role(),vector().canonicalProfile(),vector().rawZipSha256(),vector().logicalSha256(),"none","none",0,"none"));
        String encoded=new String(vector().encode(),StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->Schema2OperationTicket.decode(encoded.replace("empty_extension","invalid.module").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->Schema2OperationTicket.decode(encoded.replace(vector().operationId().toString(),"00000000-0000-0000-0000-000000000000").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void sameDurablePendingBarrierBlocksBothProtocolsAndRetainsVersionTwoAcrossOwners()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");
        try(var store=new TicketStore(state)) {
            store.save(vector());store.begin(vector());assertEquals(vector(),store.pendingSchema2());
            assertThrows(Exception.class,store::requireClear);assertThrows(Exception.class,()->new TicketStore(state));
            assertThrows(Exception.class,()->store.begin(new OperationTicket(UUID.randomUUID(),"a".repeat(64),"isolated-source","source-history-1",0,"b".repeat(64))));
        }
        try(var store=new TicketStore(state)) {
            assertEquals(vector(),store.pendingSchema2());assertThrows(Exception.class,store::requireClear);store.resolved(vector());store.requireClear();
            var old=new OperationTicket(UUID.randomUUID(),"a".repeat(64),"isolated-source","source-history-1",0,"b".repeat(64));
            store.save(old);store.begin(old);assertThrows(Exception.class,()->store.begin(vector()));assertThrows(Exception.class,store::requireClear);
        }
    }
    @Test void versionTwoEvidenceHasSeparatePermissionAndCheckpointWhileVersionOneRemainsStrict()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");
        Path directory=Schema2OfflineTestSupport.evidenceDirectory(temp,state,"ACCEPT",null);
        var acceptance=MaintenanceEvidence.readSchema2(directory);acceptance.requireAccept();
        assertThrows(Exception.class,acceptance::requireSchema2Install);assertThrows(Exception.class,()->MaintenanceEvidence.read(directory));
        var installer=Schema2OfflineTestSupport.evidence(temp,state,"INSTALL",null);assertThrows(Exception.class,installer::requireAccept);
        var old=OfflineTestSupport.evidence(temp,state,"INSTALL",null);assertThrows(Exception.class,old::requireAccept);assertThrows(Exception.class,old::requireSchema2Install);
        String material=Files.readString(directory.resolve("maintenance.json"));Files.writeString(directory.resolve("maintenance.json"),material.replace(",\"schema2_history_minimum_version\":0",""));
        assertThrows(Exception.class,()->MaintenanceEvidence.readSchema2(directory));
    }
}
