package com.dndtool.web;

import com.dndtool.service.storage.CampaignStorageService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.GenericServlet;
import jakarta.servlet.annotation.WebListener;
import java.nio.file.Path;

/** Owns the explicitly configured current campaign application and its private storage. */
@WebListener
public final class StorageApplicationListener implements ServletContextListener {
    public static final String ROOT_PARAMETER = "DND_STORAGE_ROOT";
    public static final String ENABLED_ATTRIBUTE = "com.dndtool.storage.enabled";
    public static final String SERVICE_ATTRIBUTE = "com.dndtool.storage.service";
    public static final String ERROR_ATTRIBUTE = "com.dndtool.storage.error";

    public static boolean enabled(ServletContext context) {
        if (context == null) return false;
        Object enabled = context.getAttribute(ENABLED_ATTRIBUTE);
        if (enabled instanceof Boolean value) return value;
        String root = configuredRoot(context);
        return root != null && !root.isEmpty();
    }

    public static CampaignStorageService service(ServletContext context) {
        Object value = context == null ? null : context.getAttribute(SERVICE_ATTRIBUTE);
        if (value instanceof CampaignStorageService service) return service;
        throw new IllegalStateException("STORAGE_UNAVAILABLE");
    }

    public static boolean skipLegacyInitialization(GenericServlet servlet) {
        return servlet.getServletConfig() != null && enabled(servlet.getServletContext());
    }

    private static String configuredRoot(ServletContext context) {
        String parameter = context.getInitParameter(ROOT_PARAMETER);
        return parameter != null ? parameter : System.getenv(ROOT_PARAMETER);
    }

    @Override public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        boolean enabled = enabled(context);
        context.setAttribute(ENABLED_ATTRIBUTE, enabled);
        if (!enabled) return;
        try {
            Path root = Path.of(configuredRoot(context));
            String deployedRoot = context.getRealPath("/");
            if (!root.isAbsolute() || (deployedRoot != null
                    && root.normalize().startsWith(Path.of(deployedRoot).toAbsolutePath().normalize())))
                throw new IllegalArgumentException("INVALID_STORAGE_ROOT");
            context.setAttribute(SERVICE_ATTRIBUTE,
                    new CampaignStorageService(root));
        } catch (Exception failure) {
            context.setAttribute(ERROR_ATTRIBUTE, "STORAGE_UNAVAILABLE");
            context.log("Current campaign storage is unavailable.");
        }
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        Object value = event.getServletContext().getAttribute(SERVICE_ATTRIBUTE);
        if (value instanceof CampaignStorageService service) {
            try { service.close(); }
            catch (Exception failure) {
                event.getServletContext().log("Current campaign storage did not close normally.");
            }
        }
        event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
    }
}
