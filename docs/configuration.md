# 配置

运行时配置属于外部 Tomcat，不进入 WAR。仓库只提供不含秘密的示例。
未配置 `DND_STORAGE_ROOT` 时使用 MySQL；显式提供该项时使用 SQLite，配置无效也不会
回退到 MySQL。两种模式的配置和数据分别保管。

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
时使用原 MySQL 功能。SQLite 规则库接受外部作者 ZIP，WAR 内五个原包只是导入候选。
两类包都须明确导入、审阅和接受。新建/文件打开核验准确原字节、接受记录及重组投影，
无需数据库凭据或来源 MySQL。所有新战役使用完整领域模型，简单角色由自由卡承担。准确内容/限制见[包指南](../rule-packages/srd51-runtime/package-guide.md)。
切换启动模式前须保管两侧真实数据与残留，不自动搬移、转换或删除它们。

## SQLite 装配与运行身份

当前 SQLite 由 `StorageApplicationListener` 装配 `CampaignStorageService`；
`DND_STORAGE_ROOT` 是此模式唯一的存储根输入，应用在根内分开管理 `runs/`、`saves/` 和 `rule-library/`，
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
规则库在 `rule-library/originals/` 按真实原包摘要保管 ZIP，在 `ledger.db` 登记导入、接受及
原操作结果。文件与账本沿用受控根的本机权限、禁止符号链接和 SQLite 耐久要求。备份规则库
须共同保管原包与账本。移机打开文件须在新机重新导入并接受准确原包。
完整文件携带解释合同、绑定、规则与完整链；活动运行、保存、结束及同版残留恢复从冻结
材料核验，规则库不可用不改变这些运行资格。未决规则库操作需按原 UUID 查证，不能换键跳过。

装配不使用来源 JNDI。MySQL 模式的迁移、账号及真实数据仍独立
保管，不因启用 SQLite 而自动退役或删除。

迁移存储根是独立停机维护：共同保管规则库原包与账本、全部残留、分配/清理材料、数据库/journal 和保存文件，
不能改根后忽略原残留而宣称空闲；部署更新不会自动移动、转换或删除它们。

## Web Connector

DM 入口固定为 `http://127.0.0.1:8080`。该 Connector 只绑定 `127.0.0.1`；不要为 8080 配置局域网防火墙入站规则，不要启用 AJP、反向代理、`RemoteIpValve` 或 `RemoteIpFilter`，应用也不信任 `X-Forwarded-*`。
SQLite 导入使用 `application/octet-stream`，应用限制完整文件为 16 MiB；Tomcat 的
`maxPostSize` 是表单解析限制，不替代应用的文件字节限制。

### 可选玩家 HTTPS 原型配置

SQLite runtime 可显式设置 `DND_PLAYER_ORIGIN=https://<选定IPv4>:8443` 和
`DND_PLAYER_BIND_ADDRESS=<同一IPv4>`。两项必须准确匹配，缺项、空值、公开/通配地址或
错误端口失败关闭；都未设置时玩家入口关闭。当前仅支持规范私有、链路本地或 loopback
IPv4，loopback 用于隔离验证。配置可通过 ServletContext 参数或进程环境提供，参数优先。
无效配置会关闭玩家请求面并记录有限错误，不会让 DM 入口改绑或自动创建 Connector。

这两项只配置应用请求边界，不创建网络监听。外部 Tomcat 须另外配置独立 HTTPS Connector，
证书 SAN 与所用地址一致，玩家设备须信任颁发者。证书私钥/口令和设备信任材料保留在 WAR/Git
外；没有忽略证书错误或 HTTP 降级入口。DM Connector 仍只绑定 `127.0.0.1:8080`。
玩家 Connector 只接受 `/player`、限定静态资源及 `/api/player/*` 白名单；Host 路由、
错误本地地址/Host、查询串或转发头被拒绝，写请求还必须满足准确 Origin 与 Fetch Metadata。
启用、回退与验收按[部署](deployment.md#可选玩家-https-入口)执行；公网、`/display`、
`/api/public/*` 和匿名投影仍不在范围内。
