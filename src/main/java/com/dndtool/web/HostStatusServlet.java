package com.dndtool.web;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Provides the first read-only JSON contract used to verify the host API boundary. */
@WebServlet(name = "HostStatusServlet", urlPatterns = "/api/host/status")
public final class HostStatusServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"status\":\"OK\"}");
    }
}
