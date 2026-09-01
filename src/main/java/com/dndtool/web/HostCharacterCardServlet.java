package com.dndtool.web;

import com.dndtool.persistence.JdbcCharacterCardMutationRepository;
import com.dndtool.persistence.JdbcCharacterCardRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterCardService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Host-only verified card projection and audited card/item mutation endpoint. */
@WebServlet(name = "HostCharacterCardServlet", urlPatterns = "/api/host/characters/card")
public final class HostCharacterCardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient CardOperations operations;

    public HostCharacterCardServlet() {
        // Required by the Servlet container.
    }

    HostCharacterCardServlet(CardOperations operations) {
        this.operations = operations;
    }

    @Override
    public void init() throws ServletException {
        if (operations != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CharacterCardService service = new CharacterCardService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCharacterCardRepository(dataSource),
                    new JdbcCharacterCardMutationRepository(dataSource));
            operations = new CardOperations() {
                @Override
                public CharacterCardService.LoadResult load(String characterKey)
                        throws SQLException {
                    return service.load(characterKey);
                }

                @Override
                public CharacterCardService.MutationResult mutate(
                        String characterKey,
                        String rowVersion,
                        String action,
                        String targetKey,
                        String value,
                        String description,
                        String quantity,
                        String requestId,
                        String requestDigest) throws SQLException {
                    return service.mutate(
                            characterKey, rowVersion, action, targetKey, value,
                            description, quantity, requestId, requestDigest);
                }
            };
        } catch (NamingException exception) {
            throw new ServletException("Character cards are unavailable");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepare(response);
        final CharacterCardService.LoadResult result;
        try {
            result = operations.load(request.getParameter("characterKey"));
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
            return;
        }
        switch (result.status()) {
            case READY -> write(response, HttpServletResponse.SC_OK, cardJson(result.card()));
            case INVALID_REQUEST ->
                    error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            case NOT_FOUND ->
                    error(response, HttpServletResponse.SC_NOT_FOUND, "CHARACTER_NOT_FOUND");
            case MODULE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> error(
                    response, HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
            case INVALID_STATE ->
                    error(response, HttpServletResponse.SC_CONFLICT, "CHARACTER_STATE_INVALID");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepare(response);
        final CharacterCardService.MutationResult result;
        try {
            result = operations.mutate(
                    request.getParameter("characterKey"),
                    attribute(request, HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE),
                    request.getParameter("action"),
                    request.getParameter("targetKey"),
                    request.getParameter("value"),
                    request.getParameter("description"),
                    request.getParameter("quantity"),
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE));
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
            return;
        }
        switch (result.status()) {
            case UPDATED -> success(response, "UPDATED", result.rowVersion());
            case ALREADY_SUCCEEDED ->
                    success(response, "ALREADY_SUCCEEDED", result.rowVersion());
            case INVALID_REQUEST ->
                    error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            case NOT_FOUND ->
                    error(response, HttpServletResponse.SC_NOT_FOUND, "TARGET_NOT_FOUND");
            case VERSION_CONFLICT ->
                    conflict(response, "VERSION_CONFLICT", result.rowVersion());
            case IDEMPOTENCY_CONFLICT -> error(
                    response, HttpServletResponse.SC_CONFLICT, "IDEMPOTENCY_CONFLICT");
            case NO_CHANGE -> conflict(response, "NO_CHANGE", result.rowVersion());
            case MODULE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> error(
                    response, HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
        }
    }

    private static void prepare(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static String cardJson(CharacterCardService.Card card) {
        StringBuilder json = new StringBuilder(8192);
        json.append("{\"status\":\"READY\",\"card\":{")
                .append("\"characterKey\":").append(JsonSupport.quote(card.characterKey()))
                .append(",\"characterType\":").append(JsonSupport.quote(card.characterType()))
                .append(",\"characterName\":").append(JsonSupport.quote(card.characterName()))
                .append(",\"characterStatus\":").append(JsonSupport.quote(card.characterStatus()))
                .append(",\"rowVersion\":").append(card.rowVersion())
                .append(",\"totalLevel\":").append(card.totalLevel())
                .append(",\"proficiencyBonus\":").append(card.proficiencyBonus());
        appendFields(json, card.fields());
        appendClasses(json, card.classes());
        appendProficiencies(json, "skills", card.skills());
        appendProficiencies(json, "saves", card.saves());
        appendTiers(json, card.tiers());
        appendTemplates(json, card.itemTemplates());
        appendItems(json, card.items());
        return json.append("}}").toString();
    }

    private static void appendFields(
            StringBuilder json, List<CharacterCardService.FieldView> values) {
        json.append(",\"fields\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.FieldView value = values.get(index);
            json.append("{\"fieldKey\":").append(JsonSupport.quote(value.fieldKey()))
                    .append(",\"displayName\":").append(JsonSupport.quote(value.displayName()))
                    .append(",\"value\":").append(value.value())
                    .append(",\"minimum\":").append(value.minimum())
                    .append(",\"maximum\":").append(value.maximum())
                    .append(",\"modifier\":").append(value.modifier())
                    .append(",\"unit\":").append(JsonSupport.quote(value.unit()))
                    .append('}');
        }
        json.append(']');
    }

    private static void appendClasses(
            StringBuilder json, List<CharacterCardService.ClassView> values) {
        json.append(",\"classes\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.ClassView value = values.get(index);
            json.append("{\"classKey\":").append(JsonSupport.quote(value.classKey()))
                    .append(",\"displayName\":").append(JsonSupport.quote(value.displayName()))
                    .append(",\"level\":").append(value.level()).append('}');
        }
        json.append(']');
    }

    private static void appendProficiencies(
            StringBuilder json,
            String name,
            List<CharacterCardService.ProficiencyView> values) {
        json.append(',').append(JsonSupport.quote(name)).append(":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.ProficiencyView value = values.get(index);
            json.append("{\"targetKey\":").append(JsonSupport.quote(value.targetKey()))
                    .append(",\"displayName\":").append(JsonSupport.quote(value.displayName()))
                    .append(",\"abilityFieldKey\":")
                    .append(JsonSupport.quote(value.abilityFieldKey()))
                    .append(",\"proficiencyKey\":")
                    .append(JsonSupport.quote(value.proficiencyKey()))
                    .append(",\"bonus\":").append(value.bonus()).append('}');
        }
        json.append(']');
    }

    private static void appendTiers(
            StringBuilder json, List<CharacterCardService.TierView> values) {
        json.append(",\"tiers\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.TierView value = values.get(index);
            json.append("{\"proficiencyKey\":")
                    .append(JsonSupport.quote(value.proficiencyKey()))
                    .append(",\"enumCode\":").append(JsonSupport.quote(value.enumCode()))
                    .append('}');
        }
        json.append(']');
    }

    private static void appendTemplates(
            StringBuilder json, List<CharacterCardService.ItemTemplateView> values) {
        json.append(",\"itemTemplates\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.ItemTemplateView value = values.get(index);
            json.append("{\"itemKey\":").append(JsonSupport.quote(value.itemKey()))
                    .append(",\"displayName\":").append(JsonSupport.quote(value.displayName()))
                    .append(",\"description\":").append(JsonSupport.quote(value.description()))
                    .append('}');
        }
        json.append(']');
    }

    private static void appendItems(
            StringBuilder json, List<CharacterCardService.ItemView> values) {
        json.append(",\"items\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            CharacterCardService.ItemView value = values.get(index);
            json.append("{\"itemToken\":").append(JsonSupport.quote(value.itemToken()))
                    .append(",\"sourceKind\":").append(JsonSupport.quote(value.sourceKind()))
                    .append(",\"itemKey\":").append(JsonSupport.quote(value.itemKey()))
                    .append(",\"itemName\":").append(JsonSupport.quote(value.itemName()))
                    .append(",\"itemDescription\":")
                    .append(JsonSupport.quote(value.itemDescription()))
                    .append(",\"quantity\":").append(value.quantity())
                    .append(",\"itemStatus\":").append(JsonSupport.quote(value.itemStatus()))
                    .append('}');
        }
        json.append(']');
    }

    private static void success(
            HttpServletResponse response, String status, Long rowVersion) throws IOException {
        write(response, HttpServletResponse.SC_OK,
                "{\"status\":" + JsonSupport.quote(status)
                        + ",\"rowVersion\":" + rowVersion + "}");
    }

    private static void conflict(
            HttpServletResponse response, String code, Long rowVersion) throws IOException {
        write(response, HttpServletResponse.SC_CONFLICT,
                "{\"status\":\"ERROR\",\"code\":" + JsonSupport.quote(code)
                        + ",\"rowVersion\":" + rowVersion + "}");
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        write(response, status,
                "{\"status\":\"ERROR\",\"code\":" + JsonSupport.quote(code) + "}");
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    /** Test seam keeping HTTP response tests independent of JNDI and MySQL. */
    interface CardOperations {
        CharacterCardService.LoadResult load(String characterKey) throws SQLException;

        CharacterCardService.MutationResult mutate(
                String characterKey,
                String rowVersion,
                String action,
                String targetKey,
                String value,
                String description,
                String quantity,
                String requestId,
                String requestDigest) throws SQLException;
    }
}
