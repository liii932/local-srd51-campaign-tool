package com.dndtool.web;

import com.dndtool.persistence.JdbcHostOverviewRepository;
import com.dndtool.persistence.HostOverviewRepository;
import com.dndtool.service.HostOverviewService;
import com.dndtool.service.ModuleIntegrityService;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Serves the host console shell while keeping the JSP implementation under {@code WEB-INF}. */
@WebServlet(name = "HostHomeServlet", urlPatterns = "/host")
public final class HostHomeServlet extends HttpServlet {
    static final String OVERVIEW_ATTRIBUTE = "dndtool.hostOverview";
    static final String OVERVIEW_STATUS_ATTRIBUTE = "dndtool.hostOverviewStatus";
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient OverviewOperations operations;

    public HostHomeServlet() {
        // Required by the Servlet container.
    }

    HostHomeServlet(OverviewOperations operations) {
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
            HostOverviewService service = new HostOverviewService(
                    new JdbcHostOverviewRepository(dataSource),
                    ModuleIntegrityService.using(dataSource));
            operations = service::load;
        } catch (NamingException exception) {
            throw new ServletException("Host overview is unavailable");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            HostOverviewService.Result result = operations.load();
            request.setAttribute(OVERVIEW_STATUS_ATTRIBUTE, result.status().name());
            if (result.status() == HostOverviewService.Status.READY) {
                request.setAttribute(OVERVIEW_ATTRIBUTE, result.snapshot());
            }
        } catch (SQLException exception) {
            // Keep the local control shell reachable while exposing only a stable failure category.
            request.setAttribute(OVERVIEW_STATUS_ATTRIBUTE, "DATABASE_UNAVAILABLE");
        }
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/host.jsp").forward(request, response);
    }

    /** Test seam keeping servlet behavior tests independent of JNDI and MySQL. */
    interface OverviewOperations {
        HostOverviewService.Result load() throws SQLException;
    }
}
