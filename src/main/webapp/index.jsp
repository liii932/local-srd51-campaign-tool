<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%--
    The root application is only a dispatcher. Use the context path so the redirect remains
    correct if the WAR is temporarily deployed under a non-root context during diagnostics.
--%>
<%
    response.setStatus(302);
    response.setHeader("Location", request.getContextPath() + "/host");
    response.setHeader("Cache-Control", "no-store");
%>
