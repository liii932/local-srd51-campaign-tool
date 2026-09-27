package com.dndtool.offline.rules;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Explicit disposable-instance gate. This does not relax MySqlIntegrationTestSupport. */
final class LanguageJdbcAcceptance {
    static final String ENABLE = "dnd.language.jdbc.acceptance";
    static final String DIRECTORY = "DND_LANGUAGE_ACCEPTANCE_DIRECTORY";
    enum Role {
        INSTALLER("dnd_tool_rules", "dnd_tool_rules_installer", "DND_LANGUAGE_INSTALLER_PASSWORD"),
        SOURCE("dnd_tool_rules", "dnd_tool_rules_app", "DND_LANGUAGE_SOURCE_PASSWORD"),
        RUNTIME("dnd_tool_se", "dnd_tool_se_app", "DND_LANGUAGE_RUNTIME_PASSWORD"),
        VERIFIER("dnd_tool_se", "dnd_tool_se_validation_ro", "DND_LANGUAGE_VERIFIER_PASSWORD");
        final String schema, user, passwordVariable;
        Role(String schema, String user, String passwordVariable) {
            this.schema = schema; this.user = user; this.passwordVariable = passwordVariable;
        }
    }
    @FunctionalInterface interface Connector { Connection connect(String url, Properties properties) throws SQLException; }
    private final Path directory;
    private final int port;
    private final String server;
    private final Map<String, String> environment;
    final MaintenanceEvidence evidence;

    private LanguageJdbcAcceptance(Path directory, int port, String server,
                                   Map<String, String> environment, MaintenanceEvidence evidence) {
        this.directory = directory; this.port = port; this.server = server;
        this.environment = environment; this.evidence = evidence;
    }

    static LanguageJdbcAcceptance load(boolean enabled, Map<String, String> environment) throws IOException {
        if (!enabled) throw new IllegalStateException("Disposable language JDBC acceptance is not explicitly enabled");
        String configured = environment.get(DIRECTORY);
        if (configured == null || configured.isBlank()) throw new IOException("Missing acceptance directory");
        Path requested = Path.of(configured);
        if (!requested.isAbsolute()) throw new IOException("Acceptance directory must be absolute");
        Path directory = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (directory.startsWith(Path.of("").toRealPath())) throw new IOException("Acceptance material must remain outside the repository");
        privateDirectory(directory);
        Map<String, Object> config;
        try (var root = SecureFiles.directory(directory)) {
            config = OfflineJson.object(OfflineJson.parse(SecureFiles.read(root, "acceptance.json", 4096)),
                    "acceptance_version", "port", "server_uuid");
        }
        if (OfflineJson.number(config, "acceptance_version") != 1) throw new IOException("Unsupported acceptance configuration");
        long port = OfflineJson.number(config, "port");
        if (port < 1024 || port > 65535 || port == 3306 || port == 33060) throw new IOException("Not an isolated acceptance port");
        String server = OfflineJson.string(config, "server_uuid");
        if (!server.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !UUID.fromString(server).toString().equals(server) || server.equals("00000000-0000-0000-0000-000000000000")) {
            throw new IOException("Invalid expected server UUID");
        }
        privateDirectory(directory.resolve("evidence")); privateDirectory(directory.resolve("state"));
        var evidence = MaintenanceEvidence.read(directory.resolve("evidence"));
        if (!evidence.url().equals(url((int) port, Role.INSTALLER))
                || !evidence.user().equals(Role.INSTALLER.user)
                || !evidence.text("current_user").equals(Role.INSTALLER.user + "@127.0.0.1")
                || !evidence.text("server_uuid").equals(server)
                || !evidence.stateDirectory().equals(directory.resolve("state"))) {
            throw new IOException("Acceptance target and independent maintenance evidence differ");
        }
        evidence.requireInstall();
        for (Role role : Role.values()) {
            String password = environment.get(role.passwordVariable);
            if (password == null || password.isEmpty() || password.length() > 4096) throw new IOException("Missing bounded acceptance credential");
        }
        return new LanguageJdbcAcceptance(directory, (int) port, server, Map.copyOf(environment), evidence);
    }

    static Connection openConfigured(boolean enabled, Map<String, String> environment, Role role, Connector connector)
            throws IOException, SQLException {
        return load(enabled, environment).open(role, connector);
    }

    Connection open(Role role) throws SQLException { return open(role, DriverManager::getConnection); }

    private Connection open(Role role, Connector connector) throws SQLException {
        evidence.fresh();
        Properties p = new Properties(); p.setProperty("user", role.user);
        p.setProperty("password", environment.get(role.passwordVariable));
        p.setProperty("autoReconnect", "false"); p.setProperty("allowMultiQueries", "false");
        p.setProperty("allowLoadLocalInfile", "false"); p.setProperty("allowUrlInLocalInfile", "false");
        p.setProperty("connectionTimeZone", "UTC"); p.setProperty("forceConnectionTimeZoneToSession", "true");
        p.setProperty("characterEncoding", "UTF-8"); p.setProperty("connectTimeout", "5000");
        p.setProperty("socketTimeout", "15000"); p.setProperty("useAffectedRows", "true");
        Connection c = connector.connect(url(port, role), p);
        try {
            verifyTarget(c, server, role);
            return c;
        } catch (SQLException | RuntimeException | Error failure) {
            try { c.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    static void verifyTarget(Connection c, String server, Role role) throws SQLException {
        try (var s = c.prepareStatement("SELECT @@server_uuid, CAST(DATABASE() AS BINARY), CURRENT_USER()")) {
            s.setQueryTimeout(5); s.setMaxRows(2);
            try (var r = s.executeQuery()) {
                if (!r.next() || !server.equals(r.getString(1))
                        || !Arrays.equals(role.schema.getBytes(java.nio.charset.StandardCharsets.US_ASCII), r.getBytes(2))
                        || !(role.user + "@127.0.0.1").equals(r.getString(3)) || r.next()) {
                    throw new SQLException("Disposable acceptance server/schema/account mismatch");
                }
            }
        }
    }

    Path state() { return directory.resolve("state"); }
    private static String url(int port, Role role) { return "jdbc:mysql://127.0.0.1:" + port + "/" + role.schema; }
    private static void privateDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).stream()
                    .anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) {
            throw new IOException("Acceptance directories must be real owner-only directories");
        }
    }
}
