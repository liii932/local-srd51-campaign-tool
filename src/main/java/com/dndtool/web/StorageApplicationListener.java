package com.dndtool.web;

import com.dndtool.service.storage.CampaignStorageService;
import com.dndtool.service.storage.CampaignStorageService.InvalidRootException;
import com.dndtool.service.storage.StorageException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.io.IOException;
import java.nio.file.Path;

/** Owns the SQLite campaign application and its private storage root. */
public final class StorageApplicationListener implements ServletContextListener {
    public static final String ROOT_PARAMETER = "DND_STORAGE_ROOT";
    public static final String SERVICE_ATTRIBUTE = "com.dndtool.storage.service";
    public static final String ERROR_ATTRIBUTE = "com.dndtool.storage.error";
    private static final String INVALID = "STORAGE_ROOT_INVALID";
    private static final String UNAVAILABLE = "STORAGE_UNAVAILABLE";
    private static final String IN_USE = "STORAGE_ROOT_IN_USE";
    private final String environmentRoot;
    private final String userHome;

    public StorageApplicationListener() {
        this(System.getenv(ROOT_PARAMETER), System.getProperty("user.home"));
    }

    StorageApplicationListener(String environmentRoot, String userHome) {
        this.environmentRoot = environmentRoot;
        this.userHome = userHome;
    }

    public static CampaignStorageService service(ServletContext context) {
        Object value = context == null ? null : context.getAttribute(SERVICE_ATTRIBUTE);
        if (value instanceof CampaignStorageService service) return service;
        throw new IllegalStateException(startupError(context));
    }

    public static String startupError(ServletContext context) {
        Object value = context == null ? null : context.getAttribute(ERROR_ATTRIBUTE);
        if (INVALID.equals(value)) return INVALID;
        if (IN_USE.equals(value)) return IN_USE;
        return UNAVAILABLE;
    }

    @Override public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        try {
            Path root;
            try {
                root = configuredRoot(context);
            } catch (IllegalArgumentException failure) {
                recordFailure(context, INVALID);
                return;
            }
            context.setAttribute(SERVICE_ATTRIBUTE, new CampaignStorageService(root));
        } catch (InvalidRootException failure) {
            recordFailure(context, INVALID);
        } catch (StorageException failure) {
            recordFailure(context, "INSTANCE_LOCKED".equals(failure.code()) ? IN_USE : UNAVAILABLE);
        } catch (IOException | RuntimeException | LinkageError failure) {
            recordFailure(context, UNAVAILABLE);
        }
    }

    private Path configuredRoot(ServletContext context) {
        String parameter = context.getInitParameter(ROOT_PARAMETER);
        String supplied = parameter == null ? environmentRoot : parameter;
        Path root = supplied == null ? absolutePath(userHome).resolve(".dndtool").resolve("campaigns")
                : absolutePath(supplied);
        String deployedRoot = context.getRealPath("/");
        if (root.getParent() == null || deployedRoot != null
                && root.startsWith(Path.of(deployedRoot).toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(INVALID);
        }
        return root;
    }

    private static Path absolutePath(String value) {
        if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(INVALID);
        Path path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException(INVALID);
        return path.normalize();
    }

    private static void recordFailure(ServletContext context, String code) {
        context.setAttribute(ERROR_ATTRIBUTE, code);
        context.log("Current campaign storage failed to initialize: " + code);
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        Object value = context.getAttribute(SERVICE_ATTRIBUTE);
        context.removeAttribute(SERVICE_ATTRIBUTE);
        if (value instanceof CampaignStorageService service) {
            try { service.close(); }
            catch (Exception failure) { context.log("Current campaign storage did not close normally."); }
        }
    }
}
