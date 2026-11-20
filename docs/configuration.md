# 配置

生产应用使用 SQLite。运行配置属于外部 Tomcat。仓库提供无凭据的示例。

## SQLite 当前应用存储配置

存储根按以下顺序选择。

1. Servlet context 的 `DND_STORAGE_ROOT`。需要覆盖时填写
   [ROOT Context 示例](../config/tomcat/ROOT.xml.example)中的绝对路径。
2. Tomcat 进程环境中的 `DND_STORAGE_ROOT`。
3. 两处均无配置时使用 Java 的 `${user.home}/.dndtool/campaigns`，即实际 Tomcat
   运行身份的 home。

显式空值、空白、控制字符、相对路径和仓库或应用树内路径均失败关闭。启动结果可从
`/api/host/storage/readiness` 获取。保留所选路径及现有文件，按状态核对配置和权限。

| 启动失败类别 | 核对项 |
| --- | --- |
| `STORAGE_ROOT_INVALID` | 路径语法、绝对本地目录和仓库/应用树边界 |
| `STORAGE_UNAVAILABLE` | 实际运行身份、权限、可用文件系统及应用资源 |
| `STORAGE_ROOT_IN_USE` | 私有根已有独占所有者 |

受管根须在 Git、WAR 和展开应用外。应用分开管理 `runs/`、`saves/` 和
`rule-library/`，持有整个根的实例锁，运行目录另持独占锁。受管路径及祖先须避开
符号链接和 Windows reparse point。

| 平台 | 私有权限与本地文件系统 | 落盘边界 |
|---|---|---|
| 原生 Windows | 本地 NTFS；根及受管目录/文件归实际 Tomcat 运行账号所有，ACL 只允许该账号，目录向文件和子目录继承 FullControl | 文件 force、SQLite DELETE/EXTRA 和同卷无覆盖硬链接；Java NIO 不提供 Windows 目录同步，本版验证进程中断恢复，不承诺物理断电后的目录项持久性 |
| Linux/WSL | 支持 POSIX owner-only `0700` 目录、文件锁、SQLite 及硬链接的本地文件系统 | 文件 force、SQLite DELETE/EXTRA 与目录 force；仍须独立核验目标存储设备的断电语义 |

新受管目录及显式写入文件按私有权限创建并回读。Windows SQLite 数据库和 journal 从
私有运行目录继承同一 ACL；已有宽泛 ACL 不由应用自动收紧或接管。不支持共享根、UNC、
FAT/exFAT 或可重定向路径。根锁和无覆盖发布条件必须成立。服务换运行账号
属于停机维护，必须保管全部文件并明确重设权限；Windows 管理员的系统接管能力不由应用
ACL 撤销，本版不额外为 SYSTEM/Administrators 添加读取授权。
Windows 部署须先用 `DriveInfo.DriveType` 核对根所在盘为本机固定盘，并核对 `DriveFormat`
为 NTFS。运行时 Java 能拒绝 UNC、非 NTFS 与 reparse 路径，但公开文件系统 API 不能可靠
辨别伪装为盘符的远端 NTFS 映射；盘符本地性是受信任启动配置的前提，不能仅由 `NTFS`
名称推断。隔离候选 Prepare 会在创建目标前检查本地固定盘，不访问网络共享来试探能力。

## SQLite 装配与运行身份

`StorageApplicationListener` 装配 `CampaignStorageService`。SQLite 驱动和受审 JSON
结构声明随 WAR 打包。生产 Tomcat 只需应用运行身份及私有根的访问权限。

交互启动的 `startup.bat` / `catalina.bat` 从启动进程继承环境，读取
[setenv 示例](../config/tomcat/setenv.bat.example)中的 JVM 设置。Windows 服务的进程环境、
身份和 JVM 选项由服务配置提供。`EnvironmentPropertySource` 用于解析可选 HTTPS
Connector 的环境占位符。普通存储配置直接读取进程环境或外部 Context 参数。

规则库在 `rule-library/originals/` 按原包摘要保管 ZIP，`ledger.db` 记录导入、接受和
操作结果。标准组合由应用随附，自定义组合从准确已接受的原包重组。活动运行及同版恢复
始终核验冻结材料。移动存储根须先停止应用，共同保管原包与账本、残留、数据库/journal、
文件发布和清理材料及全部保存文件。见[备份恢复](backup-and-restore.md)。

## Web Connector

DM 入口固定为 `http://127.0.0.1:8080`。该 Connector 只绑定 `127.0.0.1`；不要为 8080 配置局域网防火墙入站规则，不要启用 AJP、反向代理、`RemoteIpValve` 或 `RemoteIpFilter`，应用也不信任 `X-Forwarded-*`。
SQLite 导入使用 `application/octet-stream`，应用限制完整文件为 16 MiB；Tomcat 的
`maxPostSize` 是表单解析限制，不替代应用的文件字节限制。

### 可选玩家 HTTPS 原型配置

玩家入口可显式设置 `DND_PLAYER_ORIGIN=https://<选定IPv4>:8443` 和
`DND_PLAYER_BIND_ADDRESS=<同一IPv4>`。两项必须准确匹配，缺项、空值、公开/通配地址或
错误端口失败关闭；都未设置时玩家入口关闭。当前仅支持规范私有、链路本地或 loopback
IPv4，loopback 用于隔离验证。配置可通过 ServletContext 参数或进程环境提供，参数优先。
无效配置会关闭玩家请求面并记录有限错误，不会让 DM 入口改绑或自动创建 Connector。

这两项只配置应用请求边界，不创建网络监听。外部 Tomcat 须另外配置独立 HTTPS Connector，
证书 SAN 与所用地址一致，玩家设备须信任颁发者。证书私钥/口令和设备信任材料保留在 WAR/Git
外；没有忽略证书错误或 HTTP 降级入口。DM Connector 仍只绑定 `127.0.0.1:8080`。
玩家 Connector 只接受 `/player`、限定静态资源及 `/api/player/*` 白名单；Host 路由、
错误本地地址/Host、查询串或转发头被拒绝，写请求还必须满足准确 Origin 与 Fetch Metadata。
按[部署指南](deployment.md#可选玩家-https-入口)启用、回退和验收。
