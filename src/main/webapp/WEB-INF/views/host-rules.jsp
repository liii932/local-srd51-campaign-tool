<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.dndtool.service.HostRulesService" %>
<%@ page import="com.dndtool.web.HtmlSupport" %>
<%
    Object catalogValue = request.getAttribute("dndtool.hostRuleCatalog");
    HostRulesService.CatalogView catalog = catalogValue instanceof HostRulesService.CatalogView
            ? (HostRulesService.CatalogView) catalogValue : null;
    Object statusValue = request.getAttribute("dndtool.hostRuleCatalogStatus");
    String status = statusValue instanceof String ? (String) statusValue : "INVALID_STATE";
    String query = catalog == null ? "" : catalog.query();
    HostRulesService.RuleType selectedType = catalog == null ? null : catalog.selectedType();
%>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Local 5E Campaign Tool — 规则目录</title>
</head>
<body>
    <main>
        <p><a href="<%= request.getContextPath() %>/host">返回本机 DM 控制台</a></p>
        <h1>当前战役规则目录</h1>
        <p>这里只显示当前活动战役冻结并通过完整哈希校验的已发布内置规则。</p>
        <form method="get" action="<%= request.getContextPath() %>/host/rules">
            <label for="rule-query">关键词</label>
            <input id="rule-query" name="q" type="search"
                   value="<%= HtmlSupport.escape(query) %>">
            <label for="rule-type">类型</label>
            <select id="rule-type" name="type">
                <option value="">全部</option>
<% for (HostRulesService.RuleType type : HostRulesService.RuleType.values()) { %>
                <option value="<%= type.name() %>"<%=
                        type == selectedType ? " selected" : "" %>><%=
                        HtmlSupport.escape(type.name()) %></option>
<% } %>
            </select>
            <button type="submit">查询</button>
        </form>
<% if (catalog != null && "READY".equals(status)) { %>
        <p>
            发布版：<code><%= HtmlSupport.escape(catalog.moduleKey()) %></code>
            / <%= HtmlSupport.escape(catalog.releaseVersion()) %>
            · 规范格式 <%= catalog.canonicalFormatVersion() %>
            · 结果 <%= catalog.entries().size() %> 条
        </p>
<% if (catalog.entries().isEmpty()) { %>
        <p>没有匹配的规则目录项。</p>
<% } else { %>
        <table id="host-rule-results">
            <thead>
                <tr><th>类型</th><th>稳定键</th><th>名称</th><th>摘要</th></tr>
            </thead>
            <tbody>
<% for (HostRulesService.RuleEntry entry : catalog.entries()) { %>
                <tr>
                    <td><%= HtmlSupport.escape(entry.type().name()) %></td>
                    <td><code><%= HtmlSupport.escape(entry.key()) %></code></td>
                    <td><%= HtmlSupport.escape(entry.displayName()) %></td>
                    <td><%= HtmlSupport.escape(entry.summary()) %></td>
                </tr>
<% } %>
            </tbody>
        </table>
<% } %>
        <p><a href="<%= request.getContextPath() %>/api/host/rules">查看只读 JSON API</a></p>
<% } else if ("NO_ACTIVE_CAMPAIGN".equals(status)) { %>
        <p role="status">当前没有活动战役，因此没有可查询的冻结规则目录。</p>
<% } else if ("INVALID_REQUEST".equals(status)) { %>
        <p role="alert">查询条件无效。</p>
<% } else if ("MODULE_UNAVAILABLE".equals(status)) { %>
        <p role="alert">当前战役冻结的规则发布版不可用或尚未发布。</p>
<% } else if ("MODULE_HASH_MISMATCH".equals(status)) { %>
        <p role="alert">规则目录完整性校验失败。</p>
<% } else if ("DATABASE_UNAVAILABLE".equals(status)) { %>
        <p role="alert">数据库暂时不可用。</p>
<% } else { %>
        <p role="alert">规则目录状态无效。</p>
<% } %>
    </main>
</body>
</html>
