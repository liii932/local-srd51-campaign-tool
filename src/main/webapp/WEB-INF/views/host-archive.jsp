<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Local 5E Campaign Tool — 本机存档</title>
    <script src="<%= request.getContextPath() %>/host/assets/host-archive.js" defer></script>
</head>
<body>
    <main>
        <p><a href="<%= request.getContextPath() %>/host">返回本机 DM 控制台</a></p>
        <h1>本机存档导入</h1>
        <p>
            选择不超过 16 MiB 的单战役 JSON 存档。服务器会先执行严格格式、关系和
            内置发布版目录校验；只有预览成功并再次明确确认后才会执行导入。
        </p>
        <form id="archive-upload-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/archive/validate"
              data-import-endpoint="<%= request.getContextPath() %>/api/host/archive/import"
              data-redirect="<%= request.getContextPath() %>/host">
            <label for="archive-file">存档文件</label>
            <input id="archive-file" name="archive" type="file"
                   accept="application/json,.json" required>
            <input id="archive-csrf-token" type="hidden"
                   value="<%= request.getAttribute("dndtool.csrfToken") %>">
            <input id="archive-host-state-epoch" type="hidden"
                   value="<%= request.getAttribute("dndtool.hostStateEpoch") %>">
            <input id="archive-row-version" type="hidden"
                   value="<%= request.getAttribute("dndtool.rowVersion") %>">
            <button type="submit">上传并预览</button>
        </form>
        <p id="archive-upload-result" role="status" aria-live="polite"></p>

        <section id="archive-preview" aria-labelledby="archive-preview-title" hidden>
            <h2 id="archive-preview-title">导入预览</h2>
            <dl>
                <dt>预定方式</dt><dd id="archive-preview-mode"></dd>
                <dt>战役</dt><dd id="archive-preview-campaign"></dd>
                <dt>存档状态</dt><dd id="archive-preview-status"></dd>
                <dt>原始文件 SHA-256</dt><dd><code id="archive-preview-sha256"></code></dd>
                <dt>活动战役影响</dt><dd id="archive-preview-impact"></dd>
            </dl>
            <h3>对象数量</h3>
            <table>
                <thead><tr><th>对象</th><th>数量</th></tr></thead>
                <tbody id="archive-preview-counts"></tbody>
            </table>
            <p id="archive-preview-warning">
                <strong>不可撤销警告：</strong>
                成功导入后不能通过应用撤销；执行前请先手工导出需要保留的战役。
            </p>
            <p>确认会重新上传同一原始文件、重复全部校验并执行整战役导入。</p>
            <button id="archive-import-button" type="button" disabled>
                确认并执行导入
            </button>
            <p id="archive-import-result" role="status" aria-live="polite"></p>
        </section>
    </main>
</body>
</html>
