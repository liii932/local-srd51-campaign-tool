package com.dndtool.offline.rules;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguageJdbcAcceptanceTest {
    @TempDir Path temp;

    @Test void disabledEntryDoesNotReadFilesOrInvokeAnyConnectorForAnyAccount() {
        var calls = new AtomicInteger();
        for (var role : LanguageJdbcAcceptance.Role.values()) {
            assertThrows(IllegalStateException.class, () -> LanguageJdbcAcceptance.openConfigured(false,
                    Map.of(LanguageJdbcAcceptance.DIRECTORY, "/missing/acceptance"), role,
                    (url, properties) -> { calls.incrementAndGet(); throw new AssertionError("Connection attempted"); }));
        }
        assertEquals(0, calls.get());
    }

    @Test void enabledButMissingOrRepositoryTargetNeverInvokesConnector() {
        for (Map<String, String> environment : List.of(Map.<String, String>of(),
                Map.of(LanguageJdbcAcceptance.DIRECTORY, "relative"),
                Map.of(LanguageJdbcAcceptance.DIRECTORY, Path.of("").toAbsolutePath().toString()))) {
            var calls = new AtomicInteger();
            assertThrows(Exception.class, () -> LanguageJdbcAcceptance.openConfigured(true, environment,
                    LanguageJdbcAcceptance.Role.RUNTIME,
                    (url, properties) -> { calls.incrementAndGet(); throw new AssertionError("Connection attempted"); }));
            assertEquals(0, calls.get());
        }
    }

    @Test void wrongPortsUuidUnknownTargetFieldsAndMissingEvidenceFailBeforeConnecting() throws Exception {
        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwx------"));
        String valid = "{\"acceptance_version\":1,\"port\":43307,\"server_uuid\":\"12345678-1234-1234-1234-123456789abc\"}";
        var inputs = new ArrayList<String>();
        for (String port : List.of("3306", "33060", "0", "65536", "\"43307\"")) inputs.add(valid.replace("43307", port));
        inputs.add(valid.replace("12345678-1234-1234-1234-123456789abc", "localhost"));
        inputs.add(valid.replace("{", "{\"jdbc_url\":\"jdbc:mysql://127.0.0.1:3306/dnd_tool_se\","));
        inputs.add(valid); // No external evidence/state: configuration alone grants no connection.
        for (String json : inputs) {
            Files.writeString(temp.resolve("acceptance.json"), json); var calls = new AtomicInteger();
            assertThrows(Exception.class, () -> LanguageJdbcAcceptance.openConfigured(true,
                    Map.of(LanguageJdbcAcceptance.DIRECTORY, temp.toString()), LanguageJdbcAcceptance.Role.INSTALLER,
                    (url, properties) -> { calls.incrementAndGet(); throw new AssertionError("Connection attempted"); }));
            assertEquals(0, calls.get());
        }
    }
}
