package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.security.HostBoundaryFilter;
import com.dndtool.security.PlayerBoundaryFilter;
import com.dndtool.web.HealthServlet;
import com.dndtool.web.HostHomeServlet;
import com.dndtool.web.PlayerServlet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.jupiter.api.Test;

/** Excludes anonymous publication surfaces; the private player prototype has its own boundary. */
final class DeferredPublicSurfaceExclusionTest {
    private static final Set<String> PRIVATE_PLAYER_SERVLET_ROUTES =
            Set.of("/player", "/player/", "/api/player/*");
    private static final Pattern FORBIDDEN_PUBLIC_IDENTIFIER = Pattern.compile(
            "(?i)(?:^|_)(?:public|publication|projection|viewer|audience|visibility)(?:_|$)");
    private static final Pattern FORBIDDEN_PUBLIC_SNAKE = Pattern.compile(
            "(?i)\\b(?:public|publication|viewer)_[a-z0-9_]*\\b"
                    + "|\\b[a-z0-9_]+_(?:public|publication|viewer)\\b");
    private static final Pattern FORBIDDEN_PUBLIC_CAMEL = Pattern.compile(
            "\\b(?:isPublic|public[A-Z][A-Za-z0-9]*|publication[A-Z][A-Za-z0-9]*|"
                    + "viewer[A-Z][A-Za-z0-9]*)\\b");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("`([a-z0-9_]+)`");

    @Test
    void everyServletRouteStaysInsideHealthHostOrTheExactPrivatePlayerSurface() throws Exception {
        WebXml descriptor = new WebXml();
        assertTrue(new WebXmlParser(false, false, true).parseWebXml(
                Path.of("src/main/webapp/WEB-INF/web.xml").toUri().toURL(), descriptor, false));
        assertTrue(descriptor.isMetadataComplete());

        Map<String, String> routes = descriptor.getServletMappings();
        // The empty servlet pattern maps the context root. The "/" servlet pattern is the default mapping.
        assertTrue(routes.keySet().containsAll(Set.of("", "/health", "/host", "/host/")));
        assertTrue(routes.keySet().containsAll(PRIVATE_PLAYER_SERVLET_ROUTES));
        routes.forEach((route, servletName) -> {
            boolean playerRoute = PRIVATE_PLAYER_SERVLET_ROUTES.contains(route);
            assertTrue(route.isEmpty() || route.equals("/health") || route.equals("/host")
                            || route.startsWith("/host/") || route.startsWith("/api/host/")
                            || playerRoute, () -> "Unexpected servlet route: " + route);
            ServletDef servlet = descriptor.getServlets().get(servletName);
            assertNotNull(servlet, () -> "Missing servlet declaration: " + servletName);
            assertEquals(playerRoute, PlayerServlet.class.getName().equals(servlet.getServletClass()),
                    route);
        });
        for (String route : List.of("", "/host", "/host/")) {
            assertEquals(HostHomeServlet.class.getName(),
                    descriptor.getServlets().get(routes.get(route)).getServletClass(), route);
        }
        assertEquals(HealthServlet.class.getName(),
                descriptor.getServlets().get(routes.get("/health")).getServletClass());

        List<FilterMap> mappings = List.copyOf(descriptor.getFilterMappings());
        assertTrue(mappings.size() >= 2);
        FilterMap playerBoundary = mappings.getFirst();
        assertEquals(PlayerBoundaryFilter.class.getName(),
                descriptor.getFilters().get(playerBoundary.getFilterName()).getFilterClass());
        assertEquals(Set.of("/*"), Set.of(playerBoundary.getURLPatterns()));
        assertEquals(Set.of("REQUEST", "FORWARD", "INCLUDE", "ERROR"),
                Set.of(playerBoundary.getDispatcherNames()));

        FilterMap hostBoundary = mappings.get(1);
        assertEquals(HostBoundaryFilter.class.getName(),
                descriptor.getFilters().get(hostBoundary.getFilterName()).getFilterClass());
        assertEquals(Set.of("/", "/host/*", "/api/host/*"), Set.of(hostBoundary.getURLPatterns()));
        assertEquals(Set.of("REQUEST"), Set.of(hostBoundary.getDispatcherNames()));
    }

    @Test
    void productionCodeAndWebResourcesContainNoPublicProjectionIdentifiers()
            throws Exception {
        for (Path root : List.of(
                Path.of("src/main/java/com/dndtool"), Path.of("src/main/webapp"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(DeferredPublicSurfaceExclusionTest::isProductionTextFile)
                        .toList()) {
                    assertNoPublicIdentifier(file.toString(),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
    }

    @Test
    void migrationsContainNoPublicProjectionTablesOrFields() throws Exception {
        try (Stream<Path> files = Files.list(
                Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                Matcher identifiers = SQL_IDENTIFIER.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (identifiers.find()) {
                    String identifier = identifiers.group(1);
                    assertFalse(FORBIDDEN_PUBLIC_IDENTIFIER.matcher(identifier).find(),
                            () -> file + ": " + identifier);
                }
            }
        }
    }

    private static boolean isProductionTextFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".jsp")
                || name.endsWith(".html") || name.endsWith(".js") || name.endsWith(".xml");
    }

    private static void assertNoPublicIdentifier(String source, String content) {
        Matcher snake = FORBIDDEN_PUBLIC_SNAKE.matcher(content);
        assertFalse(snake.find(), () -> source + ": " + snake.group());
        // Java reflection access checks do not define a player/public product surface.
        String domainIdentifiers = content.replaceAll(
                "\\b(?:java\\.lang\\.reflect\\.)?Modifier\\.isPublic\\s*\\(", "modifierAccess(");
        // The unauthenticated HTTPS shell contains no game projection; it only starts approval.
        // Private member audiences/visibility are authorization facts, not publication flags.
        domainIdentifiers = domainIdentifiers.replace("publicShellNavigation", "unauthenticatedShellNavigation");
        Matcher camel = FORBIDDEN_PUBLIC_CAMEL.matcher(domainIdentifiers);
        assertFalse(camel.find(), () -> source + ": " + camel.group());
    }
}
