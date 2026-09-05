# 配置

运行时配置属于外部 Tomcat，不进入 WAR。仓库只提供不含秘密的示例。

## JNDI DataSource

应用查找固定资源 `java:comp/env/jdbc/DndToolSE`。该名称是运行时合同，不应在普通整理中更改。示例 Context 位于 [config/tomcat/ROOT.xml.example](../config/tomcat/ROOT.xml.example)。

连接 URL 应指向本机 MySQL、数据库 `dnd_tool_se`，并显式设置 UTC 会话时区、UTF-8 和有限连接/套接字超时。连接池基线为：

| 参数 | 值 |
|---|---:|
| `initialSize` | 1 |
| `minIdle` | 1 |
| `maxIdle` | 5 |
| `maxTotal` | 10 |
| `maxWaitMillis` | 3000 |
| `validationQuery` | `SELECT 1` |
| `testWhileIdle` | `true` |

Connector/J 必须由 Tomcat 加载，因此应安装在 `%CATALINA_BASE%\lib`，不要把驱动或凭据复制进 Web 应用目录。

## 数据库凭据

示例 Context 使用 `DND_DB_USERNAME` 与 `DND_DB_PASSWORD` 占位符。部署运行时
`DND_DB_USERNAME` 固定为最小权限 `dnd_tool_se_app`，不能改用 `dnd_tool_se_migrator`、
`dnd_tool_se_agent` 或测试账号。口令由启动 Tomcat 的已授权进程临时注入；交互式 Windows
部署可在当前 PowerShell 会话安全读取，自动化部署只消费操作者预先注入的环境变量，不得读取、
回显或持久化完整环境。当前没有批准把无人值守秘密存入仓库、XML、批处理或 JVM 参数。

不要把口令放入：

- Git 追踪文件；
- `JAVA_OPTS`、`CATALINA_OPTS` 或 Java argument file；
- 命令行、构建日志或问题报告；
- WAR、Tomcat 展开目录或浏览器存档。

Tomcat 必须启用 `EnvironmentPropertySource` 才能解析 XML 中的环境变量占位符。示例见 [setenv.bat.example](../config/tomcat/setenv.bat.example)。缺少口令时应用应失败关闭。测试、迁移、只读核验和操作系统部署身份的完整矩阵见[数据库说明](database.md)和[部署指南](deployment.md)。

## Web Connector

正式入口固定为 `http://127.0.0.1:8080`。Connector 应只绑定 `127.0.0.1`；不要为 8080 配置局域网防火墙入站规则，不要启用 AJP、反向代理、`RemoteIpValve` 或 `RemoteIpFilter`，应用也不信任 `X-Forwarded-*`。

LAN/公网、HTTPS、CA、动态地址、玩家账号、`/display`、`/api/public/*` 和公开投影不属于当前产品目标，也不属于当前配置。
