package com.dndtool.web;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.HostCommandService;
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

/** Initializes the frozen node map and encounter through the protected host boundary. */
@WebServlet(name = "HostEncounterServlet", urlPatterns = "/api/host/maps/encounter")
public final class HostEncounterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private transient EncounterInitializer initializer;

    public HostEncounterServlet() {
    }

    HostEncounterServlet(EncounterInitializer initializer) {
        this.initializer = initializer;
    }

    @Override
    public void init() throws ServletException {
        if (initializer != null) return;
        try {
            Object resource = InitialContext.doLookup("java:comp/env/jdbc/DndToolSE");
            if (!(resource instanceof DataSource dataSource)) throw new NamingException();
            initializer = new HostCommandService(dataSource)::initializeEncounter;
        } catch (NamingException exception) {
            throw new ServletException("Host command encounter initialization is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        final HostCommandService.EncounterResult result;
        try {
            result = initializer.initialize(new HostCommandService.EncounterRequest(
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE),
                    request.getParameter("partyNodeKey"),
                    HostEventRequestSupport.participants(request.getParameter("participants"))));
        } catch (IllegalArgumentException exception) {
            error(response, 400, "INVALID_REQUEST");
            return;
        } catch (SQLException exception) {
            error(response, 503, "DATABASE_UNAVAILABLE");
            return;
        }
        if (result.status() == HostCommandService.Status.COMPLETED) {
            var saved = result.result().savedEncounter();
            String participantCount = saved == null
                    ? "null" : Integer.toString(saved.participants().size());
            write(response, 200, "{\"status\":\"COMPLETED\",\"replayed\":"
                    + result.result().replayed() + ",\"eventSequence\":"
                    + result.result().eventSequence() + ",\"participantCount\":"
                    + participantCount + "}");
            return;
        }
        int http = result.status() == HostCommandService.Status.INVALID_REQUEST ? 400
                : result.status() == HostCommandService.Status.MODULE_UNAVAILABLE ? 503
                : result.status() == HostCommandService.Status.CAMPAIGN_NOT_FOUND ? 404 : 409;
        error(response, http, result.status().name());
    }

    private static String attribute(HttpServletRequest request, String name) {
        return HostEventRequestSupport.attribute(request, name);
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

    @FunctionalInterface
    interface EncounterInitializer {
        HostCommandService.EncounterResult initialize(
                HostCommandService.EncounterRequest request) throws SQLException;
    }
}
