package com.dndtool.web;

import com.dndtool.security.HostBoundaryFilter;
import com.dndtool.security.HostRequestSecurityFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

/** Isolated development/acceptance launcher; never installs or replaces a deployed application. */
public final class StorageLoopLauncher implements AutoCloseable {
    private final Tomcat tomcat;
    private final Path baseDirectory;
    private final AtomicBoolean closed = new AtomicBoolean();

    private StorageLoopLauncher(Tomcat tomcat, Path baseDirectory) { this.tomcat = tomcat; this.baseDirectory = baseDirectory; }

    public static StorageLoopLauncher start(Path storageRoot) throws Exception {
        Tomcat tomcat = new Tomcat();
        Path baseDirectory = Files.createTempDirectory("dnd-storage-http-");
        tomcat.setBaseDir(baseDirectory.toString());
        tomcat.setHostname("127.0.0.1");
        Connector connector = new Connector();
        connector.setPort(8080);
        connector.setProperty("address", "127.0.0.1");
        connector.setProperty("throwOnFailure", "true");
        connector.setProperty("maxPostSize", "2097152");
        tomcat.setConnector(connector);
        Context context = tomcat.addContext("", Path.of("src/main/webapp").toAbsolutePath().toString());
        context.addMimeMapping("js", "text/javascript");
        context.addMimeMapping("css", "text/css");
        context.addParameter(StorageApplicationListener.ROOT_PARAMETER, storageRoot.toString());
        context.setSessionCookieName(HostRequestSecurityFilter.SESSION_COOKIE_NAME);
        context.setSessionCookiePath("/");
        context.setUseHttpOnly(true);
        context.addApplicationListener(StorageApplicationListener.class.getName());
        context.addApplicationListener(DatabaseStartupListener.class.getName());
        addFilter(context, "HostBoundaryFilter", HostBoundaryFilter.class.getName());
        addFilter(context, "HostRequestSecurityFilter", HostRequestSecurityFilter.class.getName());
        Tomcat.addServlet(context, "storage", new HostStorageServlet());
        context.addServletMappingDecoded("/host/storage", "storage");
        context.addServletMappingDecoded("/api/host/storage/*", "storage");
        Tomcat.addServlet(context, "home", new HostHomeServlet());
        context.addServletMappingDecoded("/host", "home");
        Tomcat.addServlet(context, "legacyCampaign", new HostCampaignCreationServlet());
        context.addServletMappingDecoded("/api/host/campaigns", "legacyCampaign");
        Tomcat.addServlet(context, "legacyRules", new HostRulesApiServlet());
        context.addServletMappingDecoded("/api/host/rules", "legacyRules");
        Tomcat.addServlet(context, "assets", new DefaultServlet());
        context.addServletMappingDecoded("/host/assets/*", "assets");
        StorageLoopLauncher launcher = new StorageLoopLauncher(tomcat, baseDirectory);
        try {
            tomcat.start();
            if (!connector.getState().isAvailable() || connector.getLocalPort() != 8080)
                throw new IllegalStateException("LOOPBACK_PORT_UNAVAILABLE");
            if (!context.getState().isAvailable()) throw new IllegalStateException("STORAGE_WEBAPP_UNAVAILABLE");
            return launcher;
        } catch (Exception failure) {
            try { launcher.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void addFilter(Context context, String name, String type) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilterClass(type);
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/host/*");
        mapping.addURLPattern("/api/host/*");
        mapping.setDispatcher("REQUEST");
        context.addFilterMap(mapping);
    }

    @Override public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        try { tomcat.stop(); }
        finally {
            try { tomcat.destroy(); }
            finally {
                try (var files = Files.walk(baseDirectory)) {
                    for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Expected one private storage root");
        StorageLoopLauncher launcher = start(Path.of(arguments[0]));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { launcher.close(); } catch (Exception ignored) { }
        }, "storage-http-close"));
        System.out.println("当前战役应用已启动：http://127.0.0.1:8080/host/storage");
        launcher.tomcat.getServer().await();
    }
}
