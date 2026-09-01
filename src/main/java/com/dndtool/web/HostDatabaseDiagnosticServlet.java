package com.dndtool.web;

import com.dndtool.persistence.DatabaseDiagnostics;
import com.dndtool.persistence.DatabaseSchemaStatus;
import com.dndtool.persistence.DatabaseStartupListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Exposes a fresh read-only database check only inside the existing host security boundary. */
@WebServlet(name = "HostDatabaseDiagnosticServlet", urlPatterns = "/api/host/diagnostics/database")
public final class HostDatabaseDiagnosticServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FAILURE_JSON =
            "{\"status\":\"ERROR\",\"code\":\"DATABASE_SCHEMA_UNAVAILABLE\"}";
    private static final String MODULE_FAILURE_JSON =
            "{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DatabaseSchemaStatus status = DatabaseDiagnostics.usingJndi().run();
        ServletContext context = getServletContext();
        context.setAttribute(DatabaseStartupListener.STATUS_ATTRIBUTE, status);

        writeStatus(response, status);
    }

    static void writeStatus(HttpServletResponse response, DatabaseSchemaStatus status)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        if (status.state() == DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH) {
            // Identify only the stable failure category; never disclose any of the four hashes.
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write(MODULE_FAILURE_JSON);
            return;
        }
        if (!status.isReady()) {
            // Clients receive one stable failure shape; internal state remains server-side only.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write(FAILURE_JSON);
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"status\":\"OK\",\"schemaVersion\":"
                + status.schemaVersion()
                + ",\"scriptName\":\"" + status.scriptName()
                + "\",\"scriptSha256\":\"" + status.scriptSha256()
                + "\"}");
    }
}
