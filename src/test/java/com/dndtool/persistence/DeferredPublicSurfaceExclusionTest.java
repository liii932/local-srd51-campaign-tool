package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Permanently excludes player/public surfaces while allowing Host-only rule growth. */
final class DeferredPublicSurfaceExclusionTest {
    private static final Pattern SERVLET_ROUTE =
            Pattern.compile("urlPatterns\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern DESCRIPTOR_ROUTE =
            Pattern.compile("<url-pattern>([^<]+)</url-pattern>");
    private static final Pattern FORBIDDEN_PUBLIC_IDENTIFIER = Pattern.compile(
            "(?i)(?:^|_)(?:public|publication|projection|viewer|audience|visibility)(?:_|$)");
    private static final Pattern FORBIDDEN_PUBLIC_SNAKE = Pattern.compile(
            "(?i)\\b(?:public|publication|viewer|audience|visibility)_[a-z0-9_]*\\b"
                    + "|\\b[a-z0-9_]+_(?:public|publication|viewer|audience|visibility)\\b");
    private static final Pattern FORBIDDEN_PUBLIC_CAMEL = Pattern.compile(
            "\\b(?:isPublic|public[A-Z][A-Za-z0-9]*|publication[A-Z][A-Za-z0-9]*|"
                    + "viewer[A-Z][A-Za-z0-9]*|audience[A-Z][A-Za-z0-9]*|"
                    + "visibility[A-Z][A-Za-z0-9]*)\\b");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("`([a-z0-9_]+)`");

    @Test
    void everyServletRouteStaysInsideHealthOrProtectedHostNamespaces() throws Exception {
        Set<String> routes = servletRoutes();
        assertTrue(routes.contains("/health"));
        assertTrue(routes.contains("/host"));
        for (String route : routes) {
            assertTrue(route.equals("/health") || route.equals("/host")
                            || route.startsWith("/host/") || route.startsWith("/api/host/"),
                    route);
            assertFalse(route.equals("/display") || route.startsWith("/display/")
                    || route.startsWith("/api/public/"), route);
        }
        assertEquals(Set.of("/host/*", "/api/host/*"), descriptorRoutes());
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

    private static Set<String> servletRoutes() throws IOException {
        Set<String> routes = new HashSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/dndtool/web"))) {
            for (Path file : files.filter(path -> path.toString().endsWith("Servlet.java"))
                    .toList()) {
                Matcher matcher = SERVLET_ROUTE.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) routes.add(matcher.group(1));
            }
        }
        return Set.copyOf(routes);
    }

    private static Set<String> descriptorRoutes() throws IOException {
        Set<String> routes = new HashSet<>();
        Matcher matcher = DESCRIPTOR_ROUTE.matcher(Files.readString(
                Path.of("src/main/webapp/WEB-INF/web.xml"), StandardCharsets.UTF_8));
        while (matcher.find()) routes.add(matcher.group(1));
        return Set.copyOf(routes);
    }

    private static boolean isProductionTextFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".jsp")
                || name.endsWith(".js") || name.endsWith(".xml");
    }

    private static void assertNoPublicIdentifier(String source, String content) {
        Matcher snake = FORBIDDEN_PUBLIC_SNAKE.matcher(content);
        assertFalse(snake.find(), () -> source + ": " + snake.group());
        Matcher camel = FORBIDDEN_PUBLIC_CAMEL.matcher(content);
        assertFalse(camel.find(), () -> source + ": " + camel.group());
    }
}
