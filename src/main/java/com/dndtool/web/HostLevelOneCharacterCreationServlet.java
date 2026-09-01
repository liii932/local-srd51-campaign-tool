package com.dndtool.web;

import com.dndtool.persistence.JdbcLevelOneCharacterCreationRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.LevelOneCharacterCreationService;
import com.dndtool.service.LevelOneCharacterRules;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Protected host preview/confirm endpoint for canonical-v2 level-one PCs. */
@WebServlet(name = "HostLevelOneCharacterCreationServlet",
        urlPatterns = "/api/host/characters/level-one")
public final class HostLevelOneCharacterCreationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";
    private transient LevelOneCreator creator;

    public HostLevelOneCharacterCreationServlet() {
    }

    HostLevelOneCharacterCreationServlet(LevelOneCreator creator) {
        this.creator = creator;
    }

    @Override
    public void init() throws ServletException {
        if (creator != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            JdbcLevelOneCharacterCreationRepository repository =
                    new JdbcLevelOneCharacterCreationRepository(dataSource);
            LevelOneCharacterCreationService service = new LevelOneCharacterCreationService(
                    new JdbcModuleCatalogRepository(dataSource), repository);
            creator = new LevelOneCreator() {
                @Override
                public LevelOneCharacterCreationService.PreviewResult preview(
                        LevelOneCharacterRules.Request request) throws SQLException {
                    return service.preview(request);
                }

                @Override
                public LevelOneCharacterCreationService.ConfirmResult confirm(
                        LevelOneCharacterRules.Request request, long expectedEventTail,
                        String previewDigest, String requestId, String requestDigest)
                        throws SQLException {
                    return service.confirm(request, expectedEventTail, previewDigest,
                            requestId, requestDigest);
                }
            };
        } catch (NamingException exception) {
            throw new ServletException("Level-one character creation is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        final LevelOneCharacterRules.Request rulesRequest;
        try {
            rulesRequest = rulesRequest(request);
        } catch (IllegalArgumentException exception) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            return;
        }
        try {
            if ("PREVIEW".equals(request.getParameter("action"))) {
                writePreview(response, creator.preview(rulesRequest));
                return;
            }
            if (!"CONFIRM".equals(request.getParameter("action"))) {
                error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
                return;
            }
            long expectedTail = Long.parseLong(request.getParameter("expectedEventTail"));
            writeConfirm(response, creator.confirm(rulesRequest, expectedTail,
                    request.getParameter("previewDigest"),
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE)));
        } catch (NumberFormatException exception) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
        }
    }

    private static LevelOneCharacterRules.Request rulesRequest(HttpServletRequest request) {
        Map<String, Integer> abilities = new LinkedHashMap<>();
        for (String ability : List.of(
                "strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")) {
            abilities.put("ability." + ability,
                    Integer.valueOf(request.getParameter("ability." + ability)));
        }
        return new LevelOneCharacterRules.Request(
                request.getParameter("campaignKey"), request.getParameter("characterName"),
                request.getParameter("raceKey"), blankToNull(request.getParameter("subraceKey")),
                request.getParameter("backgroundKey"), request.getParameter("classKey"),
                blankToNull(request.getParameter("classSubclassKey")),
                abilities, values(request, "abilityBonusChoices"),
                values(request, "skillChoices"), values(request, "languageChoices"),
                values(request, "toolChoices"), values(request, "startingOptionChoices"));
    }

    private static List<String> values(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? List.of() : Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void writePreview(HttpServletResponse response,
            LevelOneCharacterCreationService.PreviewResult result) throws IOException {
        if (result.status() == LevelOneCharacterCreationService.Status.PREVIEW_READY) {
            LevelOneCharacterRules.Prepared value = result.prepared();
            StringBuilder scores = new StringBuilder("{");
            value.finalAbilityScores().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> scores.append(JsonSupport.quote(entry.getKey()))
                            .append(':').append(entry.getValue()).append(','));
            if (scores.charAt(scores.length() - 1) == ',') scores.setLength(scores.length() - 1);
            scores.append('}');
            write(response, HttpServletResponse.SC_OK,
                    "{\"status\":\"PREVIEW_READY\",\"previewDigest\":"
                            + JsonSupport.quote(value.previewDigestSha256())
                            + ",\"expectedEventTail\":" + value.expectedEventTail()
                            + ",\"maximumHitPoints\":" + value.maximumHitPoints()
                            + ",\"finalAbilityScores\":" + scores + "}");
        } else {
            writeStatusError(response, result.status(), result.errorCode());
        }
    }

    private static void writeConfirm(HttpServletResponse response,
            LevelOneCharacterCreationService.ConfirmResult result) throws IOException {
        if (result.status() == LevelOneCharacterCreationService.Status.CREATED
                || result.status() == LevelOneCharacterCreationService.Status.ALREADY_SUCCEEDED) {
            int status = result.status() == LevelOneCharacterCreationService.Status.CREATED
                    ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK;
            write(response, status, "{\"status\":" + JsonSupport.quote(result.status().name())
                    + ",\"characterKey\":" + JsonSupport.quote(result.characterKey())
                    + ",\"rowVersion\":" + result.rowVersion() + "}");
        } else {
            writeStatusError(response, result.status(), result.status().name());
        }
    }

    private static void writeStatusError(HttpServletResponse response,
            LevelOneCharacterCreationService.Status status, String code) throws IOException {
        int http = switch (status) {
            case INVALID_REQUEST -> HttpServletResponse.SC_BAD_REQUEST;
            case STALE_PREVIEW, IDEMPOTENCY_CONFLICT, CAMPAIGN_UNAVAILABLE,
                    MODULE_UNAVAILABLE, MODULE_HASH_MISMATCH -> HttpServletResponse.SC_CONFLICT;
            default -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
        error(response, http, code);
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

    interface LevelOneCreator {
        LevelOneCharacterCreationService.PreviewResult preview(LevelOneCharacterRules.Request request)
                throws SQLException;
        LevelOneCharacterCreationService.ConfirmResult confirm(
                LevelOneCharacterRules.Request request, long expectedEventTail,
                String previewDigest, String requestId, String requestDigest) throws SQLException;
    }
}
