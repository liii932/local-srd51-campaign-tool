package com.dndtool.offline.rules;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TicketStoreTest {
    @TempDir Path temp;
    @Test void durableTicketAndPendingSurviveProcessOwnerClosure()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        var ticket=OperationTicket.create(OfflineTestSupport.artifact(temp),0,evidence);
        try(var store=new TicketStore(state)) {
            store.bind(evidence);String name=store.save(ticket);assertEquals(ticket,store.load(name));store.begin(ticket);
            assertThrows(Exception.class,store::requireClear);assertThrows(Exception.class,()->new TicketStore(state));
            assertThrows(Exception.class,()->store.save(ticket));
        }
        try(var store=new TicketStore(state)) {assertEquals(ticket,store.pending());assertThrows(Exception.class,store::requireClear);store.resolved(ticket);store.requireClear();}
    }
    @Test void strictUuidAndCrossLineageTicketCannotAuthorize()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        var ticket=OperationTicket.create(OfflineTestSupport.artifact(temp),0,evidence);String original=new String(ticket.encode(),java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(Exception.class,()->OperationTicket.decode(original.replace(ticket.operationId().toString(),ticket.operationId().toString().toUpperCase(Locale.ROOT)).getBytes()));
        assertThrows(Exception.class,()->new OperationTicket(new UUID(0,0),ticket.fingerprint(),ticket.target(),ticket.lineage(),0,ticket.manifestSha256()));
        assertThrows(Exception.class,()->evidence.match(new OperationTicket(ticket.operationId(),ticket.fingerprint(),"second-source",ticket.lineage(),0,ticket.manifestSha256())));
        assertThrows(Exception.class,()->evidence.requireResolution(ticket));
    }
    @Test void unsavedTicketAndInsecureDirectoryCannotStartAttempt()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        var ticket=OperationTicket.create(OfflineTestSupport.artifact(temp),0,evidence);
        try(var store=new TicketStore(state)){assertThrows(Exception.class,()->store.begin(ticket));assertNull(store.pending());}
        Files.setPosixFilePermissions(state,java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
        assertThrows(Exception.class,()->new TicketStore(state));
    }
}
