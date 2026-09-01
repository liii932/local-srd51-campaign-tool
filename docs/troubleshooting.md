# 故障排查

## 应用无法启动或数据库诊断失败

1. 确认 MySQL 正在本机监听预期端口。
2. 确认 Connector/J 位于 `%CATALINA_BASE%\lib`。
3. 确认 `ROOT.xml` 定义 `jdbc/DndToolSE`，且环境变量属性源已启用。
4. 确认启动 Tomcat 的进程获得数据库用户名与口令，但不要输出口令。
5. 检查最新 Catalina 与 localhost 日志段中的首个根因；分享日志前先脱敏。

`SCHEMA_MISMATCH` 表示已安装迁移身份、脚本名或摘要与应用预期不一致。不要编辑旧迁移或授予广泛权限来绕过它。`MODULE_HASH_MISMATCH` 表示实际、发布、冻结或应用清单摘要不一致，应停止业务写入并调查目录数据和版本来源。

## 页面返回 404

精确使用 `http://127.0.0.1:8080`。`localhost`、`::1`、局域网 IP、HTTPS、错误端口或错误 Host 被拒绝是预期安全行为。不要通过反向代理或转发头绕过检查。

## 页面可读但写请求失败

重新加载 `/host` 获取当前 Session、CSRF 与 `host_state_epoch`。旧页面可能因对象版本变化、导入成功或 Session 轮换而失效。`VERSION_CONFLICT` 应通过重新读取当前状态解决，不应重放旧表单数据。

## 集成测试被跳过

普通 `clean verify` 不连接真实 MySQL。显式集成测试需要独立测试库、确认写入开关和同一 PowerShell 会话中的口令变量。全部测试被跳过不构成验收，详见[测试指南](testing.md)。

## WAR 或部署不一致

比较已审计候选与活动 `ROOT.war` 的 SHA-256。ZIP 时间戳可能使两次独立构建的 WAR 字节不同；只有明确部署步骤才可替换活动文件。部署和回滚见[部署指南](deployment.md)。
