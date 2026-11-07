package com.dndtool.web;

import com.dndtool.security.*;
import com.dndtool.service.storage.CampaignStorageService;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

/** Disposable two-connector prototype. Never edits installed Tomcat, firewall or device trust. */
public final class PlayerLoopLauncher implements AutoCloseable {
    private static final List<String> RUNTIME_RESOURCES = List.of("/storage/campaign-schema.json",
            "/storage/rules/srd51-runtime.zip", "/storage/rules/srd51-characters.zip",
            "/storage/rules/srd51-magic.zip", "/storage/rules/dm-runtime-house-rules.zip",
            "/storage/rules/dm-runtime-check-variant.zip");
    private final Tomcat tomcat;
    private final Context context;
    private final Path base;
    private final SSLContext clientTrust;
    private final String certificateSpki;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PlayerLoopLauncher(Tomcat tomcat, Context context, Path base, SSLContext trust, String spki) {
        this.tomcat = tomcat; this.context = context; this.base = base; this.clientTrust = trust; this.certificateSpki = spki;
    }
    public SSLContext clientTrust() { return clientTrust; }
    public String certificateSpki() { return certificateSpki; }
    public CampaignStorageService storage() { return StorageApplicationListener.service(context.getServletContext()); }

    public static PlayerLoopLauncher start(Path root) throws Exception {
        validateStorageRoot(root);
        requireRuntimeResources(CampaignStorageService.class::getResourceAsStream);
        Path base;
        try { base = Files.createTempDirectory("dnd-player-https-"); }
        catch (IOException | SecurityException unavailable) {
            throw problem("ISOLATED_TEMP_UNAVAILABLE", "A private temporary test directory could not be created. Check the authorized test environment before launching.");
        }
        Tomcat tomcat = new Tomcat();
        try {
            String password = UUID.randomUUID().toString(); Path keyStore = base.resolve("test-server.p12");
            String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
            ProcessBuilder generate = new ProcessBuilder(executable, "-genkeypair", "-alias", "player-test", "-keyalg", "RSA", "-keysize", "2048",
                    "-sigalg", "SHA256withRSA", "-validity", "2", "-dname", "CN=Local player acceptance", "-ext", "SAN=ip:127.0.0.1",
                    "-ext", "EKU=serverAuth", "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass:env", "DND_PLAYER_TEST_PASSWORD", "-noprompt");
            generate.environment().put("DND_PLAYER_TEST_PASSWORD", password);
            generate.redirectErrorStream(true).redirectOutput(base.resolve("certificate-generation.log").toFile());
            Process process = generate.start();
            try {
                if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS))
                    throw problem("ISOLATED_CERTIFICATE_TIMEOUT", "Temporary certificate generation timed out. Check the selected Java installation before another launch.");
                if (process.exitValue() != 0)
                    throw problem("ISOLATED_CERTIFICATE_FAILED", "Temporary certificate generation failed. Check that the selected JDK supports keytool and PKCS12; no device trust was changed.");
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw problem("ISOLATED_CERTIFICATE_PROCESS_ACTIVE", "The test certificate process did not stop. Inspect this test invocation before another launch.");
                }
            }
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(keyStore)) { keys.load(input, password.toCharArray()); }
            KeyStore trusted = KeyStore.getInstance("PKCS12"); trusted.load(null, null);
            var certificate = keys.getCertificate("player-test"); trusted.setCertificateEntry("player-test", certificate);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(trusted);
            SSLContext ssl = SSLContext.getInstance("TLS"); ssl.init(null, trust.getTrustManagers(), null);
            String spki = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded()));
            Files.writeString(base.resolve("test-certificate.pem"), "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded()) + "\n-----END CERTIFICATE-----\n");

            tomcat.setBaseDir(base.resolve("tomcat").toString()); tomcat.setHostname("127.0.0.1");
            Connector host = new Connector(); host.setPort(8080); host.setProperty("address", "127.0.0.1"); host.setProperty("throwOnFailure", "true"); tomcat.setConnector(host);
            Connector player = new Connector(); player.setPort(8443); player.setProperty("address", "127.0.0.1"); player.setProperty("throwOnFailure", "true");
            player.setScheme("https"); player.setSecure(true); player.setProperty("SSLEnabled", "true");
            SSLHostConfig tls = new SSLHostConfig(); tls.setProtocols("TLSv1.2,TLSv1.3");
            SSLHostConfigCertificate cert = new SSLHostConfigCertificate(tls, SSLHostConfigCertificate.Type.RSA);
            cert.setCertificateKeystoreFile(keyStore.toString()); cert.setCertificateKeystorePassword(password); cert.setCertificateKeystoreType("PKCS12"); cert.setCertificateKeyAlias("player-test");
            tls.addCertificate(cert); player.addSslHostConfig(tls); tomcat.getService().addConnector(player);
            Context context = tomcat.addContext("", Path.of("src/main/webapp").toAbsolutePath().toString());
            context.addParameter(StorageApplicationListener.ROOT_PARAMETER, root.toString());
            context.addParameter(PlayerListenerConfiguration.ORIGIN_PARAMETER, "https://127.0.0.1:8443");
            context.addParameter(PlayerListenerConfiguration.ADDRESS_PARAMETER, "127.0.0.1");
            context.addMimeMapping("js", "text/javascript"); context.addMimeMapping("css", "text/css");
            context.setSessionCookieName(HostRequestSecurityFilter.SESSION_COOKIE_NAME); context.setSessionCookiePath("/"); context.setUseHttpOnly(true);
            context.addApplicationListener(StorageApplicationListener.class.getName());
            addFilter(context, "PlayerBoundaryFilter", PlayerBoundaryFilter.class.getName(), "/*");
            addFilter(context, "HostBoundaryFilter", HostBoundaryFilter.class.getName(), "/host/*", "/api/host/*");
            addFilter(context, "HostRequestSecurityFilter", HostRequestSecurityFilter.class.getName(), "/host/*", "/api/host/*");
            servlet(context, "storage", HostStorageServlet.class.getName(), "/host/storage", "/api/host/storage/*");
            servlet(context, "hostPlayers", HostPlayerServlet.class.getName(), "/api/host/storage/players/*");
            servlet(context, "player", PlayerServlet.class.getName(), "/player", "/player/", "/api/player/*");
            servlet(context, "home", HostHomeServlet.class.getName(), "/host");
            servlet(context, "assets", DefaultServlet.class.getName(), "/host/assets/*", "/player/assets/*");
            PlayerLoopLauncher launcher = new PlayerLoopLauncher(tomcat, context, base, ssl, spki);
            tomcat.start();
            if (!host.getState().isAvailable() || host.getLocalPort() != 8080 || !player.getState().isAvailable() || player.getLocalPort() != 8443 || !context.getState().isAvailable())
                throw problem("ISOLATED_PLAYER_SERVER_UNAVAILABLE", "The isolated web application or its fixed connectors did not start. Check the test classpath and whether another service owns a required port.");
            requireStorageReady(context.getServletContext());
            return launcher;
        } catch (Exception | LinkageError failure) {
            StartupFailure diagnostic = startupFailure(failure);
            try { cleanupCreatedResources(tomcat, base); }
            catch (Exception cleanup) { diagnostic.addSuppressed(problem("ISOLATED_CLEANUP_INCOMPLETE", "Temporary test resources could not be completely removed. Check this test process before another launch; the supplied campaign root was retained.")); }
            throw diagnostic;
        }
    }

    /** Read-only checks mirror the managed-path boundary; a missing dedicated root is supported. */
    static void validateStorageRoot(Path root) {
        if (root == null || !root.isAbsolute()) throw problem("ISOLATED_STORAGE_ROOT_INVALID", "Choose an absolute disposable storage root outside the repository and deployed application.");
        try {
            for (Path ancestor = root.normalize(); ancestor != null; ancestor = ancestor.getParent()) {
                if (Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS) || Files.exists(ancestor.resolve("WEB-INF"), LinkOption.NOFOLLOW_LINKS))
                    throw problem("ISOLATED_STORAGE_LOCATION_REJECTED", "The storage root is beneath a Git or deployed-application marker. Sandbox-injected markers also count. Use an authorized isolated location outside those markers; do not remove markers or relax storage protection.");
            }
            for (Path ancestor = root; ancestor != null; ancestor = ancestor.getParent()) {
                if (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) continue;
                BasicFileAttributes attributes = Files.readAttributes(ancestor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther())
                    throw problem("ISOLATED_STORAGE_PATH_UNSAFE", "Use a dedicated directory without symbolic links, reparse points or non-directory ancestors.");
            }
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && root.getFileSystem().supportedFileAttributeViews().contains("posix")
                    && !Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
                throw problem("ISOLATED_STORAGE_PERMISSIONS", "An existing disposable root needs owner-only rwx permissions. The launcher does not change permissions on supplied directories.");
        } catch (IOException | SecurityException unreadable) {
            throw problem("ISOLATED_STORAGE_PATH_UNREADABLE", "The storage path could not be checked. Verify directory access in the authorized test environment.");
        }
    }

    @FunctionalInterface interface ResourceSource { InputStream open(String name) throws IOException; }
    static void requireRuntimeResources(ResourceSource resources) {
        for (String name : RUNTIME_RESOURCES) try (InputStream input = resources.open(name)) {
            if (input == null || input.read() == -1) throw problem("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE", "A required campaign runtime resource is missing or empty. Prepare the documented test classpath and resources before launching.");
        } catch (IOException | SecurityException | LinkageError unreadable) {
            throw problem("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE", "A required campaign runtime resource is unreadable. Prepare the documented test classpath and resources before launching.");
        }
    }

    static void requireStorageReady(ServletContext context) {
        if (!Boolean.TRUE.equals(context.getAttribute(StorageApplicationListener.ENABLED_ATTRIBUTE))
                || context.getAttribute(StorageApplicationListener.ERROR_ATTRIBUTE) != null
                || !(context.getAttribute(StorageApplicationListener.SERVICE_ATTRIBUTE) instanceof CampaignStorageService))
            throw problem("ISOLATED_STORAGE_UNAVAILABLE", "The campaign service did not initialize. Check private directory access, exclusive root ownership and the prepared application resources. No player-ready address will be published.");
        try {
            CampaignStorageService storage = StorageApplicationListener.service(context);
            synchronized (storage) {
                // Recovery-required stores still need the Host UI; business write guards remain
                // authoritative for RESIDUAL, BLOCKED and SEALED runs.
                storage.status();
            }
        } catch (RuntimeException | LinkageError unavailable) {
            throw problem("ISOLATED_STORAGE_UNAVAILABLE", "The campaign service is unavailable. Check exclusive root ownership and prepared runtime resources; retain the supplied store for diagnosis.");
        }
    }

    static final class StartupFailure extends IllegalStateException {
        private final String code;
        private StartupFailure(String code, String instruction) { super(code + ": " + instruction); this.code = code; }
        String code() { return code; }
    }
    private static StartupFailure problem(String code, String instruction) { return new StartupFailure(code, instruction); }
    private static StartupFailure startupFailure(Throwable failure) {
        if (failure instanceof StartupFailure diagnostic) return diagnostic;
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return problem("ISOLATED_START_INTERRUPTED", "Isolated startup was interrupted. Its temporary resources are being closed; no ready addresses were published.");
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) if (cause instanceof BindException)
            return problem("ISOLATED_PORT_UNAVAILABLE", "A required loopback port is occupied. Identify the owning process; stop only an authorized test instance before another launch.");
        return problem("ISOLATED_START_FAILED", "The disposable server did not become ready. Check Java, test classpath and runtime resources; supplied campaign files are retained.");
    }
    private static void servlet(Context context, String name, String type, String... paths) {
        Tomcat.addServlet(context, name, type); for (String path : paths) context.addServletMappingDecoded(path, name);
    }
    private static void addFilter(Context context, String name, String type, String... paths) {
        FilterDef definition = new FilterDef(); definition.setFilterName(name); definition.setFilterClass(type); context.addFilterDef(definition);
        FilterMap mapping = new FilterMap(); mapping.setFilterName(name); for (String path : paths) mapping.addURLPattern(path); mapping.setDispatcher("REQUEST"); context.addFilterMap(mapping);
    }
    @Override public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        cleanupCreatedResources(tomcat, base);
    }
    /** Only the directory allocated by this launch is deleted; supplied campaign storage is retained. */
    static void cleanupCreatedResources(Tomcat tomcat, Path createdBase) throws Exception {
        boolean incomplete = false;
        try {
            LifecycleState state = tomcat.getServer().getState();
            if (!Set.of(LifecycleState.NEW, LifecycleState.INITIALIZED, LifecycleState.STOPPED, LifecycleState.DESTROYED).contains(state)) tomcat.stop();
        } catch (Exception failure) { incomplete = true; }
        try { tomcat.destroy(); } catch (Exception failure) { incomplete = true; }
        try {
            if (Files.exists(createdBase, LinkOption.NOFOLLOW_LINKS)) try (var paths = Files.walk(createdBase)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException failure) { incomplete = true; }
        if (incomplete) throw problem("ISOLATED_CLEANUP_INCOMPLETE", "The isolated container or its temporary test files did not close completely. Inspect this test invocation; supplied campaign storage was retained.");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Path.of(args[0]).isAbsolute()) throw new IllegalArgumentException("Expected an absolute disposable storage root");
        try (PlayerLoopLauncher launcher = start(Path.of(args[0]))) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { launcher.close(); } catch (Exception ignored) { } }, "player-prototype-close"));
            System.out.println("DM: http://127.0.0.1:8080/host/storage\nPlayer: https://127.0.0.1:8443/player");
            System.out.println("Temporary test certificate: " + launcher.base.resolve("test-certificate.pem"));
            System.out.println("Test-only certificate SPKI: " + launcher.certificateSpki);
            launcher.tomcat.getServer().await();
        }
    }
}
