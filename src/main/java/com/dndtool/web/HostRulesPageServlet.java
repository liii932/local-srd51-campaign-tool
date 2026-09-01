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

/** Renders the host-only rule browser from the same verified service projection as the API. */
@WebServlet(name = "HostRulesPageServlet", urlPatterns = "/host/rules")
public final class HostRulesPageServlet extends HttpServlet {
    static final String CATALOG_ATTRIBUTE = "dndtool.hostRuleCatalog";
    static final String STATUS_ATTRIBUTE = "dndtool.hostRuleCatalogStatus";
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient RulesOperations operations;

    public HostRulesPageServlet() {
    }

    HostRulesPageServlet(RulesOperations operations) {
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
            throws ServletException, IOException {
        try {
            HostRulesService.Result result = operations.load(
                    request.getParameter("q"), request.getParameter("type"));
            request.setAttribute(STATUS_ATTRIBUTE, result.status().name());
            if (result.status() == HostRulesService.Status.READY) {
                request.setAttribute(CATALOG_ATTRIBUTE, result.catalog());
            }
        } catch (SQLException exception) {
            request.setAttribute(STATUS_ATTRIBUTE, "DATABASE_UNAVAILABLE");
        }
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/host-rules.jsp")
                .forward(request, response);
    }

    @FunctionalInterface
    interface RulesOperations {
        HostRulesService.Result load(String query, String type) throws SQLException;
    }
}
