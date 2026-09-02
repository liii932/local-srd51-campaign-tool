# 公开仓库发布检查清单

本清单用于把从旧私有仓库迁移来的当前仓库转为公开仓库前的最后确认。它是可重复流程，
不是一次性验收记录；不要在本文记录具体执行时间、个人路径、私有 URL、日志片段或真实数据。

## 边界

- 本清单只覆盖 Git 仓库内容和 GitHub 仓库设置准备。
- 切换 GitHub 仓库可见性、fetch、push、创建 Issue/PR、部署、数据库迁移、授权或真实业务写入都是单独外部操作，需要明确授权。
- 如果发现凭据、私钥、真实战役数据、数据库备份或其他需要从历史中移除的材料，立即停止公开化流程，并制定单独的历史重写和令牌轮换计划。

## 1. 工作树和历史边界

1. 确认当前分支、上游和远端指向预期的新仓库。
2. 检查 staged、unstaged、untracked 文件和完整 diff；不要用 reset、checkout 或 clean 掩盖未解释的变更。
3. 确认 `.gitignore` 覆盖构建产物、IDE 配置、本地 Tomcat 配置、日志、备份、真实存档、证书、私钥、client option file 和本地环境文件。
4. 如需发布公开仓库，优先从当前树生成一个干净提交；不要把旧私有仓库的备份分支、临时 refs 或本地恢复目录推到公开远端。

## 2. 内容审计

至少检查以下类别的已跟踪文件和准备提交的 diff：

- 数据库口令、API token、cookie、session、client option file、`.env`、本地 properties；
- 私钥、证书私钥、keystore、PFX/P12、临时 CA 文件；
- Tomcat 外部真实配置、JVM 参数文件、Connector/J 私有路径、真实 `%CATALINA_BASE%`；
- 数据库 dump、备份、恢复脚本副本、表导出、真实迁移运行结果；
- 真实战役存档、玩家/角色私人内容、日志、结果捕获、终端转录；
- 个人机器路径、内网地址、旧私有仓库 URL、私有 issue/PR 编号；
- 未经许可的第三方素材或非 SRD 5.1 内容。

可用关键词抽查作为辅助，但不能替代人工审阅：

```powershell
rg -n --hidden --glob '!.git/**' --glob '!target/**' \
  -i 'password|passwd|secret|token|api[_-]?key|private key|BEGIN .*PRIVATE|jdbc:|TODO|FIXME|proprietary|internal only'
```

对 `127.0.0.1`、`localhost`、示例数据库 URL 等命中，应确认它们只是产品 loopback 合同或无秘密示例。

## 3. 公开入口文件

公开前确认根目录至少包含并保持一致：

- `README.md`：准确区分当前已实现功能、DRAFT 范围和产品边界；
- `LICENSE` 与 `NOTICE`：覆盖原创代码和 SRD 5.1 派生内容的授权及署名；
- `SECURITY.md`：说明私密漏洞报告和不公开披露细节；
- `CONTRIBUTING.md`：说明开发流程、测试和不提交私有数据；
- `.gitignore`：阻止常见本地/敏感产物；
- `.github/ISSUE_TEMPLATE/*` 与 `.github/pull_request_template.md`：引导公开协作避免泄露私人数据。

## 4. 构建与打包审计

代码、资源、构建配置或迁移变更后，公开前执行：

```powershell
mvn clean verify
```

如果生成 WAR，还应按 `docs/testing.md` 和 `docs/deployment.md` 记录本地观察到的字节数、SHA-256、条目数，并检查 WAR 中不存在凭据、私钥、外部 Tomcat 配置、日志、备份、真实存档或本地文件名。文档或 GitHub 模板的纯文本变更通常不需要重新打包。

## 5. GitHub 仓库设置

切换可见性前，在 GitHub 设置中确认：

- 默认分支、分支保护和必需检查符合当前维护策略；
- private vulnerability reporting 已启用或有等效私密报告入口；
- Issues、Discussions、Wiki、Projects、Actions、Packages 等功能只按需要启用；
- 仓库描述、topics、主页链接和许可证识别准确；
- 没有公开旧私有远端、备份分支、临时分支或机器特定资料。

## 6. 最终公开前确认

1. `git diff --check` 和 `git diff --cached --check` 无空白错误。
2. 列出准备公开的 staged 文件名，并确认没有用户无关变更。
3. 明确说明本次没有执行数据库、部署或 GitHub 可见性切换，除非这些操作已被单独授权并完成。
4. 可见性切换后，立即从公开视角浏览 README、LICENSE、NOTICE、SECURITY、CONTRIBUTING、Issue 模板和默认分支文件列表。
