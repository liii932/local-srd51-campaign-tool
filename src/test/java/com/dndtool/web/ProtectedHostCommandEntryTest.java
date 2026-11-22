package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.security.HostBoundaryFilter;
import com.dndtool.security.HostRequestSecurityFilter;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.jupiter.api.Test;

final class ProtectedHostCommandEntryTest {
    @Test
    void descriptorRoutesAllHostApisThroughTheStorageBoundaryAndWriteFilter() throws Exception {
        WebXml descriptor = new WebXml();
        assertTrue(new WebXmlParser(false, false, true).parseWebXml(
                Path.of("src/main/webapp/WEB-INF/web.xml").toUri().toURL(), descriptor, false));
        assertTrue(descriptor.isMetadataComplete());

        var routes = descriptor.getServletMappings();
        assertEquals(Set.of("/api/host/storage/*", "/api/host/storage/players/*"),
                routes.keySet().stream().filter(path -> path.startsWith("/api/host/"))
                        .collect(Collectors.toSet()));
        assertServlet(descriptor, "/host/storage", HostStorageServlet.class);
        assertServlet(descriptor, "/api/host/storage/*", HostStorageServlet.class);
        assertServlet(descriptor, "/api/host/storage/players/*", HostPlayerServlet.class);

        List<FilterMap> mappings = List.copyOf(descriptor.getFilterMappings());
        var filters = mappings.stream().map(FilterMap::getFilterName).toList();
        int boundary = filters.indexOf("HostBoundaryFilter");
        int security = filters.indexOf("HostRequestSecurityFilter");
        assertTrue(boundary >= 0 && security > boundary);
        assertEquals(HostBoundaryFilter.class.getName(),
                descriptor.getFilters().get(filters.get(boundary)).getFilterClass());
        assertEquals(HostRequestSecurityFilter.class.getName(),
                descriptor.getFilters().get(filters.get(security)).getFilterClass());
        assertTrue(Set.of(mappings.get(boundary).getURLPatterns()).contains("/api/host/*"));
        assertTrue(Set.of(mappings.get(security).getURLPatterns()).contains("/api/host/storage/*"));
        assertTrue(Set.of(mappings.get(security).getDispatcherNames()).contains("REQUEST"));
    }

    private static void assertServlet(WebXml descriptor, String route, Class<?> handler) {
        String name = descriptor.getServletMappings().get(route);
        var servlet = descriptor.getServlets().get(name);
        assertNotNull(servlet, route);
        assertEquals(handler.getName(), servlet.getServletClass(), route);
    }
}
