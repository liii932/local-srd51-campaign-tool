package com.dndtool.web;

import com.dndtool.persistence.JdbcCampaignModuleBindingRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.service.HostRulesService;
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

/** Host-only JSON projection of the active campaign's verified released rule catalog. */
@WebServlet(name = "HostRulesApiServlet", urlPatterns = "/api/host/rules")
public final class HostRulesApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient RulesOperations operations;

    public HostRulesApiServlet() {
    }

    HostRulesApiServlet(RulesOperations operations) {
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
            HostRulesService service = new HostRulesService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCampaignModuleBindingRepository(dataSource));
            operations = service::load;
        } catch (NamingException exception) {
            throw new ServletException("Host rules are unavailable");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepare(response);
        final HostRulesService.Result result;
        try {
            result = operations.load(request.getParameter("q"), request.getParameter("type"));
        } catch (SQLException exception) {
            error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE");
            return;
        }
        switch (result.status()) {
            case READY -> write(response, HttpServletResponse.SC_OK, json(result.catalog()));
            case INVALID_REQUEST ->
                    error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            case NO_ACTIVE_CAMPAIGN ->
                    error(response, HttpServletResponse.SC_NOT_FOUND, "NO_ACTIVE_CAMPAIGN");
            case MODULE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> error(
                    response, HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
            case INVALID_STATE -> error(
                    response, HttpServletResponse.SC_CONFLICT, "RULE_CATALOG_INVALID");
        }
    }

    private static void prepare(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
    }

    private static String json(HostRulesService.CatalogView catalog) {
        StringBuilder json = new StringBuilder(8192);
        json.append("{\"status\":\"READY\",\"module\":{")
                .append("\"moduleKey\":").append(JsonSupport.quote(catalog.moduleKey()))
                .append(",\"releaseVersion\":")
                .append(JsonSupport.quote(catalog.releaseVersion()))
                .append(",\"canonicalFormatVersion\":")
                .append(catalog.canonicalFormatVersion())
                .append("},\"query\":").append(JsonSupport.quote(catalog.query()))
                .append(",\"type\":").append(JsonSupport.quote(
                        catalog.selectedType() == null ? null : catalog.selectedType().name()))
                .append(",\"entries\":[");
        for (int index = 0; index < catalog.entries().size(); index++) {
            if (index > 0) json.append(',');
            HostRulesService.RuleEntry entry = catalog.entries().get(index);
            json.append("{\"type\":").append(JsonSupport.quote(entry.type().name()))
                    .append(",\"key\":").append(JsonSupport.quote(entry.key()))
                    .append(",\"displayName\":")
                    .append(JsonSupport.quote(entry.displayName()))
                    .append(",\"summary\":").append(JsonSupport.quote(entry.summary()))
                    .append('}');
        }
        return json.append("]}").toString();
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

    @FunctionalInterface
    interface RulesOperations {
        HostRulesService.Result load(String query, String type) throws SQLException;
    }
}
