# 故障排查

## 应用无法启动或数据库诊断失败

先核对实际启动模式：未配置 `DND_STORAGE_ROOT` 时才使用 MySQL；Servlet context parameter
优先于进程环境，包括空值。显式空值、未解析占位符或无效目录会让 SQLite 不可用，
不会回退到 MySQL。不要通过删除配置或更换空根掩盖原运行的残留。

SQLite 模式检查：

1. 用 DM 入口读取 `/api/host/storage/readiness`；`/health` 的 `OK` 只证明进程存活。
2. `STORAGE_UNAVAILABLE` 时检查实际运行账号、绝对私有根、Git/WAR/展开应用边界、
   符号链接/reparse point、文件系统和独占锁；Windows 还须核对本地 NTFS 与 owner-only ACL。
3. `STORAGE_RECOVERY_REQUIRED` 时在 Host 页面显式恢复准确残留或精确放弃；
   `STORAGE_BLOCKED`、`STORAGE_INTEGRITY` 或 `COMMIT_UNKNOWN` 时保留数据库、journal、
   准备/清理材料和保存文件，按[恢复边界](backup-and-restore.md#sqlite-完整文件与同版恢复)处理。
4. 检查最新日志首个根因；不要删 journal、手动补表/re-seed、删锁或打开旧保存点代替恢复。

MySQL 模式检查：

1. 确认 MySQL 正在本机监听预期端口。
2. 确认 Connector/J 位于 `%CATALINA_BASE%\lib`。
3. 确认 `ROOT.xml` 定义 `jdbc/DndToolSE`，且环境变量属性源已启用。
4. 确认启动 Tomcat 的进程获得数据库用户名与口令，但不要输出口令。
5. 检查最新 Catalina 与 localhost 日志段中的首个根因；分享日志前先脱敏。

`SCHEMA_MISMATCH` 表示已安装迁移身份、脚本名或摘要与应用预期不一致。不要编辑旧迁移或授予广泛权限来绕过它。`MODULE_HASH_MISMATCH` 表示实际、发布、冻结或应用清单摘要不一致，应停止业务写入并调查目录数据和版本来源。

## 页面返回 404

DM 精确使用 `http://127.0.0.1:8080`。该入口上的 `localhost`、`::1`、局域网 IP、HTTPS、
错误端口或错误 Host 被拒绝是预期安全行为，`/player` 与 `/api/player/*` 也不从这里提供。
不要通过反向代理或转发头绕过检查。

显式启用的玩家入口使用准确配置的 `https://<IPv4>:8443/player`。两项玩家配置和独立 TLS
Connector 缺一不可；应用配置本身不创建监听，无效配置关闭玩家请求面。错误本地地址、
Host、路径、查询串、转发头返回 `404`；错误 Origin 或 Fetch Metadata 返回 `403`。
玩家 HTTPS Connector 不提供 Host 路由。按[配置说明](configuration.md#可选玩家-https-原型配置)
核对当前值；证书/SAN/设备信任错误按[部署步骤](deployment.md#可选玩家-https-入口)修正，
不要忽略证书警告或改用 HTTP。

## 页面可读但写请求失败

重新加载 `/host` 获取当前 Session、CSRF 与 `host_state_epoch`。旧页面可能因对象版本变化、
导入成功、打开/恢复或 Session 轮换而失效。MySQL 的 `VERSION_CONFLICT`，以及 SQLite 的
`STALE_EPOCH`、`STALE_REVISION`、`STALE_CHARACTER` 均须重新读取当前状态，不重放旧表单。

SQLite 的 `SAVE_PENDING` 应完成准确待保存请求；`COMMIT_UNKNOWN` 或 `RUN_BLOCKED`
须先显式恢复，不能换请求号重复提交。导入返回 `FILE_CONFLICT` 表示原 `fileKey` 已存在，
不应修改文件身份、覆盖文件或将导入成功等同于打开运行。

玩家遇到 `PLAYER_HOST_REOPEN_REQUIRED` 或 `PLAYER_OPERATIONS_PAUSED`，先由 Host 核对
残留恢复和重新开放状态。原设备恢复身份后仍需显式接管写连接；
`PLAYER_DEVICE_REAPPROVAL_REQUIRED` 需重新审批。`PLAYER_VIEW_EXPIRED` 要刷新合法候选，
响应丢失或 `PLAYER_RESULT_UNVERIFIED` 只查询原请求收据，不重发业务命令。

## 集成测试被跳过

普通 `clean verify` 不连接真实 MySQL。显式集成测试需要独立测试库、确认写入开关和同一 PowerShell 会话中的口令变量。全部测试被跳过不构成验收，详见[测试指南](testing.md)。

## WAR 或部署不一致

比较已审计候选与活动 `ROOT.war` 的 SHA-256。ZIP 时间戳可能使两次独立构建的 WAR 字节不同；只有明确部署步骤才可替换活动文件。部署和回滚见[部署指南](deployment.md)。
