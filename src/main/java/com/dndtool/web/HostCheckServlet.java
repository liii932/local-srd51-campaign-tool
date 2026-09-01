package com.dndtool.web;

import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.D20CheckCalculator;
import com.dndtool.service.CheckTransactionService;
import com.dndtool.service.HostCommandService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Collectors;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Executes one frozen host-command check through the protected host command boundary. */
@WebServlet(name = "HostCheckServlet", urlPatterns = "/api/host/events/check")
public final class HostCheckServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private transient CheckExecutor executor;

    public HostCheckServlet() {
    }

    HostCheckServlet(CheckExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void init() throws ServletException {
        if (executor != null) return;
        try {
            Object resource = InitialContext.doLookup("java:comp/env/jdbc/DndToolSE");
            if (!(resource instanceof DataSource dataSource)) throw new NamingException();
            executor = new HostCommandService(dataSource)::executeCheck;
        } catch (NamingException exception) {
            throw new ServletException("Host command checks are unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        json(response);
        final HostCommandService.CheckResult result;
        try {
            String checkType = request.getParameter("checkType");
            boolean manual = "MANUAL".equals(checkType);
            result = executor.execute(new HostCommandService.CheckRequest(
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE),
                    request.getParameter("executorCharacterKey"),
                    HostEventRequestSupport.rowVersion(request),
                    switch (checkType == null ? "" : checkType) {
                        case "ABILITY" -> "check.ability";
                        case "SKILL" -> "check.skill";
                        case "SAVING_THROW" -> "check.saving_throw";
                        case "MANUAL" -> "check.manual";
                        default -> throw new IllegalArgumentException("invalid check type");
                    },
                    request.getParameter("rollModeKey"),
                    manual ? null : request.getParameter("modifierSourceKey"),
                    manual ? HostEventRequestSupport.integer(
                            request.getParameter("manualModifier")) : null,
                    manual ? request.getParameter("manualName") : null,
                    HostEventRequestSupport.integer(request.getParameter("difficultyClass")),
                    HostEventRequestSupport.targetVersions(
                            request.getParameter("targetCharacterVersions")),
                    HostEventRequestSupport.effects(request, "success"),
                    HostEventRequestSupport.effects(request, "failure")));
        } catch (IllegalArgumentException exception) {
            error(response, 400, "INVALID_REQUEST");
            return;
        } catch (SQLException exception) {
            error(response, 503, "DATABASE_UNAVAILABLE");
            return;
        }
        if (result.status() == HostCommandService.Status.COMPLETED) {
            success(response, result.result());
        } else {
            failure(response, result.status(), result.result());
        }
    }

    private static void success(
            HttpServletResponse response, CheckTransactionService.Result result)
            throws IOException {
        D20CheckCalculator.Result calculation = result.calculation();
        String candidates = calculation.candidates().stream()
                .map(value -> "{\"order\":" + value.order() + ",\"rolledValue\":"
                        + value.rolledValue() + ",\"selected\":" + value.selected() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        write(response, 200, "{\"status\":\"COMPLETED\",\"replayed\":"
                + result.replayed() + ",\"eventSequence\":"
                + result.savedCheck().eventSequence() + ",\"candidates\":" + candidates
                + ",\"selectedValue\":" + calculation.selectedValue()
                + ",\"modifierValue\":" + calculation.modifierValue()
                + ",\"totalValue\":" + calculation.totalValue()
                + ",\"difficultyClass\":" + calculation.difficultyClass()
                + ",\"outcome\":\"" + calculation.outcome().name() + "\"}");
    }

    private static void failure(
            HttpServletResponse response, HostCommandService.Status status,
            CheckTransactionService.Result result) throws IOException {
        int http = switch (status) {
            case INVALID_REQUEST -> 400;
            case CAMPAIGN_NOT_FOUND, CHARACTER_NOT_FOUND -> 404;
            case MODULE_UNAVAILABLE -> 503;
            default -> 409;
        };
        String detail = status == HostCommandService.Status.VERSION_CONFLICT
                && result != null
                ? ",\"characterKey\":" + JsonSupport.quote(result.rejectedCharacterKey())
                        + ",\"rowVersion\":" + result.currentRowVersion() : "";
        write(response, http, "{\"status\":\"ERROR\",\"code\":\""
                + status.name() + "\"" + detail + "}");
    }

    private static String attribute(HttpServletRequest request, String name) {
        return HostEventRequestSupport.attribute(request, name);
    }

    private static void json(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
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
    interface CheckExecutor {
        HostCommandService.CheckResult execute(HostCommandService.CheckRequest request)
                throws SQLException;
    }
}
