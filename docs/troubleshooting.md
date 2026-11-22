# 故障排查

## 战役存储未就绪

从 DM 入口读取 `/api/host/storage/readiness`。`/health` 只检查 Web 应用能否响应。启动账号默认使用其 home 下的 `.dndtool/campaigns`，显式 Context 参数优先于同名环境变量。

| 返回码 | 检查或操作 |
|---|---|
| `STORAGE_ROOT_INVALID` | 核对根为绝对私有目录，位于仓库和展开应用之外。检查空值及未解析的占位符。 |
| `STORAGE_ROOT_IN_USE` | 找到已持有该根的实例，使用它继续战役。 |
| `STORAGE_UNAVAILABLE` | 核对启动账号、目录所有者、可写权限和文件系统。Windows 使用本机 NTFS 和私有 ACL。 |
| `STORAGE_RECOVERY_REQUIRED` | 从工作台显式恢复对应残留。 |
| `STORAGE_BLOCKED`、`STORAGE_INTEGRITY`、`COMMIT_UNKNOWN` | 保管工作库、journal、准备/清理材料和保存点，按[恢复流程](backup-and-restore.md#sqlite-完整文件与同版恢复)核对原运行。 |

单份保存文件提示不可用时，仍可操作当前战役或打开其他有效保存点。可从保管副本重新取得该文件。

## 标准规则或内置候选不可用

标准新建或打开标准存档返回 `BUNDLED_RULES_UNAVAILABLE` 时，核对活动 WAR 中的标准组合及
`bundled-members.tsv`。如果某个内置候选单独标为不可用，核对该包及其身份清单项。检查方法见
[WAR 审计](testing.md#6-war-审计)。自定义战役返回 `RULE_PACKAGE_NOT_ACCEPTED` 时，按
[运行合同](storage-vitality-loop.md#有限-schema-2-规则材料)在本机接受战役绑定的准确原包。

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

重新加载 `/host` 获取当前 Session、CSRF 与 SQLite 运行代次。旧页面可能因对象版本变化、
导入成功、打开/恢复或 Session 轮换而失效。`STALE_EPOCH`、`STALE_REVISION`、`STALE_CHARACTER` 均须重新读取当前状态，不重放旧表单。

SQLite 的 `SAVE_PENDING` 应完成准确待保存请求；`COMMIT_UNKNOWN` 或 `RUN_BLOCKED`
须先显式恢复，不能换请求号重复提交。导入返回 `FILE_CONFLICT` 表示原 `fileKey` 已存在，
不应修改文件身份、覆盖文件或将导入成功等同于打开运行。

玩家遇到 `PLAYER_HOST_REOPEN_REQUIRED` 或 `PLAYER_OPERATIONS_PAUSED`，先由 Host 核对
残留恢复和重新开放状态。原设备恢复身份后仍需显式接管写连接；
`PLAYER_DEVICE_REAPPROVAL_REQUIRED` 需重新审批。`PLAYER_VIEW_EXPIRED` 要刷新合法候选，
响应丢失或 `PLAYER_RESULT_UNVERIFIED` 先查证原收据；仍待查证时可关闭原提交，取得已接受或确定未执行的结果。

## 离线 MySQL 集成测试未运行

需要离线 JDBC 验收时，用 `-Dtest=MySqlIntegrationIT` 显式选择测试，配置获授权的独立测试库、
启用和确认写入开关，并向运行 Maven 的同一 shell 会话提供口令变量。选中后仍被跳过时，检查
Surefire 报告中的跳过原因及最终测试计数。命令与目标限制见 [MySQL 集成测试](testing.md#3-mysql-集成测试)。

## WAR 或部署不一致

比较已审计候选与活动 `ROOT.war` 的 SHA-256。ZIP 时间戳可能使两次独立构建的 WAR 字节不同；只有明确部署步骤才可替换活动文件。部署和回滚见[部署指南](deployment.md)。
