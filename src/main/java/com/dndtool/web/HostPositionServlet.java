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

/** Moves one character on the frozen node map through the protected host boundary. */
@WebServlet(name = "HostPositionServlet", urlPatterns = "/api/host/maps/position")
public final class HostPositionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private transient PositionUpdater updater;

    public HostPositionServlet() {
    }

    HostPositionServlet(PositionUpdater updater) {
        this.updater = updater;
    }

    @Override
    public void init() throws ServletException {
        if (updater != null) return;
        try {
            Object resource = InitialContext.doLookup("java:comp/env/jdbc/DndToolSE");
            if (!(resource instanceof DataSource dataSource)) throw new NamingException();
            updater = new HostCommandService(dataSource)::position;
        } catch (NamingException exception) {
            throw new ServletException("Host command positioning is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        final HostCommandService.PositionResult result;
        try {
            result = updater.position(new HostCommandService.PositionRequest(
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE),
                    request.getParameter("characterKey"),
                    HostEventRequestSupport.rowVersion(request),
                    request.getParameter("nodeKey")));
        } catch (IllegalArgumentException exception) {
            error(response, 400, "INVALID_REQUEST");
            return;
        } catch (SQLException exception) {
            error(response, 503, "DATABASE_UNAVAILABLE");
            return;
        }
        if (result.status() == HostCommandService.Status.COMPLETED) {
            var value = result.result();
            write(response, 200, "{\"status\":\"COMPLETED\",\"replayed\":"
                    + value.replayed() + ",\"eventSequence\":" + value.eventSequence()
                    + ",\"nodeKey\":" + JsonSupport.quote(value.nodeKey())
                    + ",\"rowVersion\":" + value.rowVersion()
                    + ",\"changed\":" + value.changed() + "}");
            return;
        }
        int http = switch (result.status()) {
            case INVALID_REQUEST -> 400;
            case CAMPAIGN_NOT_FOUND, CHARACTER_NOT_FOUND -> 404;
            case MODULE_UNAVAILABLE -> 503;
            default -> 409;
        };
        var value = result.result();
        String detail = result.status() == HostCommandService.Status.VERSION_CONFLICT
                && value != null ? ",\"characterKey\":"
                        + JsonSupport.quote(value.rejectedCharacterKey())
                        + ",\"rowVersion\":" + value.currentRowVersion() : "";
        write(response, http, "{\"status\":\"ERROR\",\"code\":\""
                + result.status().name() + "\"" + detail + "}");
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
    interface PositionUpdater {
        HostCommandService.PositionResult position(
                HostCommandService.PositionRequest request) throws SQLException;
    }
}
