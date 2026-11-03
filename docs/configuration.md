# 配置

运行时配置属于外部 Tomcat，不进入 WAR。仓库只提供不含秘密的示例。

## JNDI DataSource

未配置 `DND_STORAGE_ROOT` 的 MySQL 模式查找固定资源 `java:comp/env/jdbc/DndToolSE`。该名称是运行时合同，不应在普通整理中更改。示例 Context 位于 [config/tomcat/ROOT-mysql.xml.example](../config/tomcat/ROOT-mysql.xml.example)。

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

## SQLite 当前应用存储配置

当前[SQLite 战役模式](storage-vitality-loop.md)使用 `DND_STORAGE_ROOT`，可由启动进程环境
或同名 Servlet context parameter 提供（context parameter 优先，包括空值）。必须是
Git/WAR/展开应用之外的绝对私有目录。应用管理根内互不包含的 `runs` 和 `saves`，持
整个根的实例锁；运行目录另持独占锁。根、子目录和文件均拒绝符号链接及 Windows
reparse point，祖先路径也不得经由此类重定向。

| 平台 | 私有权限与本地文件系统 | 落盘边界 |
|---|---|---|
| 原生 Windows | 本地 NTFS；根及受管目录/文件归实际 Tomcat 运行账号所有，ACL 只允许该账号，目录向文件和子目录继承 FullControl | 文件 force、SQLite DELETE/EXTRA 和同卷无覆盖硬链接；Java NIO 不提供 Windows 目录同步，本版验证进程中断恢复，不承诺物理断电后的目录项持久性 |
| Linux/WSL | 支持 POSIX owner-only `0700` 目录、文件锁、SQLite 及硬链接的本地文件系统 | 文件 force、SQLite DELETE/EXTRA 与目录 force；仍须独立核验目标存储设备的断电语义 |

新受管目录及显式写入文件按私有权限创建并回读。Windows SQLite 数据库和 journal 从
私有运行目录继承同一 ACL；已有宽泛 ACL 不由应用自动收紧或接管。不支持共享根、UNC、
FAT/exFAT、可重定向路径或无法取得的锁，不切换根、不降级为覆盖复制。服务换运行账号
属于停机维护，必须保管全部文件并明确重设权限；Windows 管理员的系统接管能力不由应用
ACL 撤销，本版不额外为 SYSTEM/Administrators 添加读取授权。
Windows 部署须先用 `DriveInfo.DriveType` 核对根所在盘为本机固定盘，并核对 `DriveFormat`
为 NTFS。运行时 Java 能拒绝 UNC、非 NTFS 与 reparse 路径，但公开文件系统 API 不能可靠
辨别伪装为盘符的远端 NTFS 映射；盘符本地性是受信任启动配置的前提，不能仅由 `NTFS`
名称推断。隔离候选 Prepare 会在创建目标前检查本地固定盘，不访问网络共享来试探能力。

显式提供该配置时，`/host` 进入 `/host/storage`，安全 epoch 从 SQLite 服务读取，旧 MySQL
读写路由关闭，启动不查 JNDI；空值、空白、控制字符、相对路径或其他无效根均保持此模式
不可用，不能回退到 MySQL。环境与 context parameter 均未配置
时使用原 MySQL 功能。当前 SQLite 运行规则由 WAR 内的四个有限 schema-2 原包交付，
新建/重开通过生产 reader 与准确投影核验，无需数据库凭据或来源 MySQL 在线。目录包含
生命值基础、治疗和依赖二者的 `srd51-runtime@1`；选择 runtime 后仍沿同一受控根保存全部
领域事实。准确内容/限制见[包指南](../rule-packages/srd51-runtime/package-guide.md)。
切换启动模式前须保管两侧真实数据与残留，不自动搬移、转换或删除它们。

## v2 存储配置目标

当前 SQLite 由 `StorageApplicationListener` 装配 `CampaignStorageService`；
`DND_STORAGE_ROOT` 是此模式唯一的存储根输入，应用在根内分开管理运行与保存目录，
不需要另配来源 JNDI、运行连接池或两套根变量。[ROOT 示例](../config/tomcat/ROOT.xml.example)
显式选择 SQLite，未解析的环境占位符也会失败关闭。
[MySQL ROOT 示例](../config/tomcat/ROOT-mysql.xml.example)、`web.xml` 的 `jdbc/DndToolSE`
及既有秘密变量仅服务明确保留的原 MySQL 模式；不要把两个示例同时装入同一 ROOT。

交互启动的 `startup.bat` / `catalina.bat` 读取
[setenv 示例](../config/tomcat/setenv.bat.example)，并从启动进程继承 `DND_STORAGE_ROOT`。
Windows 服务不执行该批处理，须在服务的真实运行身份、环境和 JVM 配置中提供根及
`EnvironmentPropertySource`；也可在外部 ROOT context 写入绝对根，优先于环境。
根路径不是秘密，但不得把数据库口令加入 JVM 参数。

SQLite 驱动和受审 JSON 声明随应用打包；本地连接核验 DELETE/EXTRA、外键和事务配置。
四个内置原包由生产 reader/compose 建立固定投影，完整文件携带准确 material/链并逐字段
比对此投影。外部来源离线不影响当前新建、打开、运行、保存、结束或同版恢复。

未来通用自定义包的来源安装/接受和只读配置须另行设计，同步代码、示例、权限与测试；
本文不预留未实现的 JNDI 或环境变量作为当前启动合同。若后续彻底退役原 MySQL 路线，
须先完成[领域接合](design/v2/domain-integration.md)、核实真实数据保管，再协调移除旧配置，
不能因当前可选 SQLite 已存在就删除真实迁移、账号或数据。

迁移存储根是独立停机维护：共同保管全部残留、分配/清理材料、数据库/journal 和保存文件，
不能改根后忽略原残留而宣称空闲；部署更新不会自动移动、转换或删除它们。

## Web Connector

正式入口固定为 `http://127.0.0.1:8080`。Connector 应只绑定 `127.0.0.1`；不要为 8080 配置局域网防火墙入站规则，不要启用 AJP、反向代理、`RemoteIpValve` 或 `RemoteIpFilter`，应用也不信任 `X-Forwarded-*`。
SQLite 导入使用 `application/octet-stream`，应用限制完整文件为 16 MiB；Tomcat 的
`maxPostSize` 是表单解析限制，不替代应用的文件字节限制。

LAN/公网、HTTPS、CA、动态地址、玩家账号、`/display`、`/api/public/*` 和公开投影不属于当前产品目标，也不属于当前配置。
