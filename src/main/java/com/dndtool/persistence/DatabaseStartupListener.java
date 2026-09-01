package com.dndtool.persistence;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Runs the read-only database check after Tomcat has created the application's JNDI context. */
@WebListener
public final class DatabaseStartupListener implements ServletContextListener {
    public static final String STATUS_ATTRIBUTE =
            "com.dndtool.persistence.databaseSchemaStatus";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        DatabaseSchemaStatus status = DatabaseDiagnostics.usingJndi().run();
        context.setAttribute(STATUS_ATTRIBUTE, status);

        // Log only the finite state category; never include SQL, credentials or exception text.
        context.log("Database readiness check finished with state " + status.state().name() + ".");
    }
}
