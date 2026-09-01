package com.dndtool.web;

import com.dndtool.persistence.JdbcLevelAdvancementRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.LevelAdvancementRules;
import com.dndtool.service.LevelAdvancementService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Protected host preview/confirm endpoint for one canonical-v2 level advancement. */
@WebServlet(name = "HostLevelAdvancementServlet",
        urlPatterns = "/api/host/characters/level-up")
public final class HostLevelAdvancementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";
    private transient Advancer advancer;

    public HostLevelAdvancementServlet() {
    }

    HostLevelAdvancementServlet(Advancer advancer) {
        this.advancer = advancer;
    }

    @Override
    public void init() throws ServletException {
        if (advancer != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            LevelAdvancementService service = new LevelAdvancementService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcLevelAdvancementRepository(dataSource));
            advancer = new Advancer() {
                @Override
                public LevelAdvancementService.PreviewResult preview(
                        LevelAdvancementRules.Request request) throws SQLException {
                    return service.preview(request);
                }

                @Override
                public LevelAdvancementService.ConfirmResult confirm(
                        LevelAdvancementRules.Request request, long expectedEventTail,
                        long expectedRowVersion, String previewDigest, String requestId,
                        String requestDigest) throws SQLException {
                    return service.confirm(request, expectedEventTail, expectedRowVersion,
                            previewDigest, requestId, requestDigest);
                }
            };
        } catch (NamingException exception) {
            throw new ServletException("Level advancement is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        try {
            LevelAdvancementRules.Request rulesRequest = new LevelAdvancementRules.Request(
                    request.getParameter("characterKey"),
                    Integer.parseInt(request.getParameter("targetLevel")),
                    request.getParameter("hpChoiceAlgorithm"),
                    blankToNull(request.getParameter("targetClassKey")),
                    blankToNull(request.getParameter("subclassKey")),
                    abilityIncreases(request), blankToNull(request.getParameter("featKey")),
                    commaSeparated(request.getParameter("proficiencyChoices")));
            if ("PREVIEW".equals(request.getParameter("action"))) {
                writePreview(response, advancer.preview(rulesRequest));
                return;
            }
            if (!"CONFIRM".equals(request.getParameter("action"))) {
                error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
                return;
            }
            writeConfirm(response, advancer.confirm(rulesRequest,
                    Long.parseLong(request.getParameter("expectedEventTail")),
                    Long.parseLong(request.getParameter("expectedRowVersion")),
                    request.getParameter("previewDigest"),
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE)));
        } catch (NumberFormatException exception) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
        }
    }

    private static void writePreview(HttpServletResponse response,
            LevelAdvancementService.PreviewResult result) throws IOException {
        if (result.status() != LevelAdvancementService.Status.PREVIEW_READY) {
            writeStatusError(response, result.status(), result.errorCode());
            return;
        }
        LevelAdvancementRules.Prepared value = result.prepared();
        write(response, HttpServletResponse.SC_OK,
                "{\"status\":\"PREVIEW_READY\",\"previewDigest\":"
                        + JsonSupport.quote(value.previewDigestSha256())
                        + ",\"expectedEventTail\":" + value.context().expectedEventTail()
                        + ",\"expectedRowVersion\":" + value.context().expectedRowVersion()
                        + ",\"previousLevel\":" + value.context().totalLevel()
                        + ",\"targetLevel\":" + value.targetLevel()
                        + ",\"hitDieSides\":" + value.hitDieSides()
                        + ",\"minimumHitPointIncrease\":"
                        + value.minimumHitPointIncrease()
                        + ",\"maximumHitPointIncrease\":"
                        + value.maximumHitPointIncrease()
                        + ",\"previousProficiencyBonus\":"
                        + value.previousProficiencyBonus()
                        + ",\"newProficiencyBonus\":"
                        + value.newProficiencyBonus()
                        + ",\"multiclass\":" + (value.advancementChoice() != null
                                && value.advancementChoice().multiclass())
                        + ",\"featKey\":" + JsonSupport.quote(value.advancementChoice() == null
                                ? null : value.advancementChoice().featKey()) + "}");
    }

    private static void writeConfirm(HttpServletResponse response,
            LevelAdvancementService.ConfirmResult result) throws IOException {
        if (result.status() == LevelAdvancementService.Status.ADVANCED
                || result.status() == LevelAdvancementService.Status.ALREADY_SUCCEEDED) {
            int status = result.status() == LevelAdvancementService.Status.ADVANCED
                    ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK;
            String roll = result.hitDieRoll() == null ? "null" : result.hitDieRoll().toString();
            write(response, status, "{\"status\":" + JsonSupport.quote(result.status().name())
                    + ",\"characterKey\":" + JsonSupport.quote(result.characterKey())
                    + ",\"rowVersion\":" + result.rowVersion()
                    + ",\"hitDieRoll\":" + roll
                    + ",\"hitPointIncrease\":" + result.hitPointIncrease() + "}");
            return;
        }
        writeStatusError(response, result.status(), result.status().name());
    }

    private static void writeStatusError(HttpServletResponse response,
            LevelAdvancementService.Status status, String code) throws IOException {
        int http = status == LevelAdvancementService.Status.INVALID_REQUEST
                ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_CONFLICT;
        error(response, http, code);
    }

    private static Map<String, Integer> abilityIncreases(HttpServletRequest request) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String ability : List.of("strength", "dexterity", "constitution",
                "intelligence", "wisdom", "charisma")) {
            String value = blankToNull(request.getParameter("asi." + ability));
            if (value != null) result.put("ability." + ability, Integer.parseInt(value));
        }
        return Map.copyOf(result);
    }

    private static List<String> commaSeparated(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : raw.split(",", -1)) result.add(value.trim());
        return List.copyOf(result);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        write(response, status, "{\"status\":\"REJECTED\",\"code\":"
                + JsonSupport.quote(code) + "}");
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    interface Advancer {
        LevelAdvancementService.PreviewResult preview(LevelAdvancementRules.Request request)
                throws SQLException;

        LevelAdvancementService.ConfirmResult confirm(
                LevelAdvancementRules.Request request, long expectedEventTail,
                long expectedRowVersion, String previewDigest, String requestId,
                String requestDigest) throws SQLException;
    }
}
