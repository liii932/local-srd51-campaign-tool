package com.dndtool.web;

import com.dndtool.persistence.JdbcCharacterLifecycleMutationRepository;
import com.dndtool.persistence.JdbcCharacterModuleBindingRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterLifecycleCommandService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Handles audited rename, type-change, archive and restore host commands. */
@WebServlet(
        name = "HostCharacterLifecycleServlet",
        urlPatterns = "/api/host/characters/lifecycle")
public final class HostCharacterLifecycleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient LifecycleMutator lifecycleMutator;

    public HostCharacterLifecycleServlet() {
        // Required by the Servlet container.
    }

    HostCharacterLifecycleServlet(LifecycleMutator lifecycleMutator) {
        this.lifecycleMutator = lifecycleMutator;
    }

    @Override
    public void init() throws ServletException {
        if (lifecycleMutator != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CharacterLifecycleCommandService service = new CharacterLifecycleCommandService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCharacterLifecycleMutationRepository(dataSource),
                    new JdbcCharacterModuleBindingRepository(dataSource));
            lifecycleMutator = service::mutate;
        } catch (NamingException exception) {
            throw new ServletException("Character lifecycle commands are unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        final CharacterLifecycleCommandService.Result result;
        try {
            result = lifecycleMutator.mutate(
                    request.getParameter("characterKey"),
                    attribute(request, HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE),
                    request.getParameter("action"),
                    request.getParameter("value"),
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE));
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
            return;
        }

        switch (result.status()) {
            case UPDATED -> success(response, "UPDATED", result.rowVersion());
            case ALREADY_SUCCEEDED -> success(
                    response, "ALREADY_SUCCEEDED", result.rowVersion());
            case INVALID_REQUEST -> error(
                    response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            case NOT_FOUND -> error(
                    response, HttpServletResponse.SC_NOT_FOUND, "CHARACTER_NOT_FOUND");
            case VERSION_CONFLICT -> conflict(
                    response, "VERSION_CONFLICT", result.rowVersion());
            case IDEMPOTENCY_CONFLICT -> error(
                    response, HttpServletResponse.SC_CONFLICT, "IDEMPOTENCY_CONFLICT");
            case NO_CHANGE -> conflict(response, "NO_CHANGE", result.rowVersion());
            case MODULE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> error(
                    response, HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
        }
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static void success(
            HttpServletResponse response, String status, Long rowVersion) throws IOException {
        write(response, HttpServletResponse.SC_OK,
                "{\"status\":\"" + status + "\",\"rowVersion\":" + rowVersion + "}");
    }

    private static void conflict(
            HttpServletResponse response, String code, Long rowVersion) throws IOException {
        write(response, HttpServletResponse.SC_CONFLICT,
                "{\"status\":\"ERROR\",\"code\":\"" + code
                        + "\",\"rowVersion\":" + rowVersion + "}");
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        write(response, status, "{\"status\":\"ERROR\",\"code\":\"" + code + "\"}");
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    /** Test seam that keeps HTTP mapping tests independent from JNDI and MySQL. */
    @FunctionalInterface
    interface LifecycleMutator {
        CharacterLifecycleCommandService.Result mutate(
                String characterKey,
                String rowVersion,
                String action,
                String value,
                String requestId,
                String requestDigestSha256) throws SQLException;
    }
}
