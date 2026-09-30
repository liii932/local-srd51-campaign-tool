package com.dndtool.web;

import com.dndtool.persistence.DatabaseSchemaStatus;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.Objects;
import java.util.function.Supplier;

/** Runs the read-only database check after Tomcat has created the application's JNDI context. */
@WebListener
public final class DatabaseStartupListener implements ServletContextListener {
    public static final String STATUS_ATTRIBUTE =
            "com.dndtool.persistence.databaseSchemaStatus";

    private final Supplier<DatabaseSchemaStatus> diagnostics;

    public DatabaseStartupListener() {
        this(DatabaseDiagnosticsFactory.usingJndi()::run);
    }

    DatabaseStartupListener(Supplier<DatabaseSchemaStatus> diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        DatabaseSchemaStatus status = diagnostics.get();
        context.setAttribute(STATUS_ATTRIBUTE, status);

        // Log only the finite state category; never include SQL, credentials or exception text.
        context.log("Database readiness check finished with state " + status.state().name() + ".");
    }
}
