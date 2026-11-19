# 部署

这里描述外部 Tomcat 10.1 的 SQLite ROOT 应用部署。实际停启服务、替换 WAR、变更设备
信任和防火墙均由获授权的操作者执行。

## 1. 构建候选

在仓库根目录按[完整验证流程](testing.md#2-必要时的完整验证)执行构建。候选路径为
`target/dnd-tool-se-0.1.0-SNAPSHOT.war`。复用同一代码和配置的有效测试结果时，按测试
指南记录选取的范围和制品构建命令。

按 [WAR 审计方法](testing.md#6-war-审计)记录候选字节数、SHA-256 和 ZIP 条目数。核对
生产描述符、Servlet 和 Filter、SQLite JDBC、结构 JSON、五个规则原包和 Host/玩家
静态资源。审计源码与资源差量，确认 WAR 无退役 MySQL Web 入口、Connector/J、外部
Tomcat 配置、凭据、密钥、账号专用配置、日志、备份和真实战役数据。

## 2. 存储前提

确认 Tomcat 的实际运行账号、[私有根和文件系统](configuration.md#sqlite-当前应用存储配置)、
锁和已有残留。Windows 使用本地固定 NTFS 私有目录。按[原生 Windows 候选验收](testing.md#原生-windows-外部-tomcat-候选验收)
验证进程中断恢复。活动部署更换前先保管已有数据库、完整战役文件和外部配置。历史 MySQL
数据和工具按[数据库](database.md)及[备份恢复](backup-and-restore.md)独立管理。

## 3. 外部配置

Tomcat 的 Java `user.home` 下默认使用 `.dndtool/campaigns`。需要改根时给实际 Tomcat
进程设置绝对 `DND_STORAGE_ROOT`，或在
`%CATALINA_BASE%\conf\Catalina\localhost\ROOT.xml` 提供优先的
[外部 Context 参数](../config/tomcat/ROOT.xml.example)。检查无效覆盖时的就绪错误，
保留真实根和残留供处理。从旧配置切换时保管完整旧 Context 和启动配置，当前 Context
使用 SQLite 参数及所需的其他外部容器设置。

DM Connector 固定绑定 `127.0.0.1:8080`，shutdown listener 同样限 loopback。按
[配置合同](configuration.md#web-connector)设置容器、JVM 和表单解析限制。完整文件导入
另由应用强制限制为 16 MiB。[setenv 示例](../config/tomcat/setenv.bat.example)仅在
批处理启动方式使用。Windows 服务直接读取服务配置。

### 可选玩家 HTTPS 入口

默认只配置 DM Connector。确需启用玩家时，先完成候选构建、可回滚替换和 Host 基线验收，
再按批准的网络边界配置独立 HTTPS。

1. 选定私有、链路本地或用于隔离验收的 loopback IPv4 和固定端口 `8443`，按
   [玩家请求边界](configuration.md#可选玩家-https-原型配置)填写两项应用参数。
2. 使用[独立 Connector 示例](../config/tomcat/player-https-connector.xml.example)及
   SAN 准确包含该 IPv4 的证书。将 PKCS12 私钥库和口令保管在 Git/WAR 外，由受控启动
   环境提供占位符。实际玩家设备须信任该证书的颁发者。
3. 仅为选定私有网络上的 HTTPS 端口配置所需防火墙访问。DM Connector 继续只监听
   `127.0.0.1:8080`。
4. 用实际桌面和移动设备执行[玩家验收矩阵](design/v2/player-participation.md#验收与采用门)。
   核对证书信任、目标地址、两设备授权隔离、权限撤销和重连查证。
5. 关闭时移除相应应用参数、HTTPS Connector 和防火墙规则，保管运行及完整文件。

### 自动化部署身份

无人值守部署身份 `dnd-tool-deployer` 只需读候选、写仓库外回滚目录、控制指定的
Tomcat 服务、替换该服务的 ROOT WAR 与展开目录、读取最新日志和本地端口状态。
实际 Tomcat 运行身份拥有受管根所需的私有权限。MySQL 维护、账号和库的权限按数据库
合同单独授予。

## 4. 可回滚替换

1. 停止目标 Tomcat，确认 Java 进程和该实例的 8005、8080 以及已启用的 8443 退出。
2. 备份活动 `webapps\ROOT.war`、完整 `webapps\ROOT`、ROOT Context、`server.xml`
   和启动配置，记录原本不存在的对象。仓库外整体保管该实例实际使用的 SQLite 根，
   包括规则库原包及账本、全部运行库和 journal、保存文件、准备和清理材料。
3. 核对目标路径，移除旧展开目录，将审计过的候选复制到 `webapps\ROOT.war`。
   核对候选和活动 WAR 的 SHA-256，并检查目标 context 的配套配置。
4. 使用同一配置和运行账号启动目标实例，然后按下节验收。

## 5. 启动与验收

检查最新启动日志、目标 Java 进程和 listener。8080 及 shutdown listener 只监听
loopback。应用启用了玩家入口时核对精确 8443 地址，并执行设备验收。

| 请求 | 预期 |
| --- | --- |
| `GET /health` | `200`，UTF-8 纯文本 `OK` |
| `GET /`、`GET /host` | `303`，转到 `/host/storage` |
| `GET /host/storage` | `200`，战役桌与资源正常加载 |
| `GET /api/host/storage/state` | 读取活动运行、可见保存文件及残留状态 |
| `GET /api/host/storage/readiness` | `mode=SQLITE`。IDLE/ACTIVE 返回 `200` 和 `ready=true`，阻断、残留或存储根故障返回 `503` 和稳定代码 |
| 错误 Host、`localhost`、`::1` 访问 DM 路由 | `404` |
| DM Connector 上的 `/player`、`/api/player/*` | `404` |
| `GET /api/host/diagnostics/database`、`GET /display`、`GET /api/public/state` | `404` |

只读与拒绝路径由 `tools/storage/verify-war-http.cjs` 复验。涉及 UI 的候选验证桌面
和窄屏主要流程及控制台。业务写入使用经授权的隔离根和可恢复样本。

`STORAGE_RECOVERY_REQUIRED` 需在战役桌显式恢复或精确放弃相应 run。
就绪检查和故障排查保留原材料，按[配置](configuration.md#sqlite-当前应用存储配置)和
[运行合同](storage-vitality-loop.md)处理其他返回类别。

### 日常打开与关闭

将 `http://127.0.0.1:8080` 保存为书签。Windows 交互启动使用已配置的
`CATALINA_HOME`、`CATALINA_BASE`、`JAVA_HOME` 和对应的 `startup.bat`；
服务方式使用已配置的服务。需要结束运行时在战役桌执行 END，核对保存完成和空闲状态，
再操作 `shutdown.bat` 或停止服务。直接关浏览器或服务后，原 run 按残留流程处理。

## 6. 回滚

停止实例并保管失败候选及日志。在仓库外另外保管当前 SQLite 根，恢复相匹配的旧 WAR、
展开目录、外部配置和已有备份，按恢复版本的合同校验活动 WAR 哈希和数据库或存储根。
跨应用版本时使用该版本支持的数据，避免用旧程序解释新格式的文件和残留。
MySQL 和 SQLite 数据恢复均按[备份恢复](backup-and-restore.md)独立授权。
