# 部署

本指南描述外部 Tomcat 10.1 根上下文的可回滚部署流程。部署会改变仓库外状态，应由明确授权的操作者执行；构建成功不等于已经部署。

## 1. 构建候选

在仓库根目录执行：

```powershell
mvn clean verify
```

确认最终退出码为 0、测试计数完整且出现 `BUILD SUCCESS`。候选默认为 `target/dnd-tool-se-0.1.0-SNAPSHOT.war`。
若全量运行中断或局部修复，按[测试指南](testing.md#2-必要时的完整验证)逐类补齐并复用有效报告，
记录分段结果；候选仍须来自无旧产物的构建目录，并具备完整回归覆盖。

记录候选的字节数、SHA-256 和 ZIP 条目数。检查必需的应用类、JSP、JavaScript、`web.xml` 和 V001—V042 资源存在；检查私钥、凭据、外部 Tomcat 配置、日志、备份、真实存档、结果捕获与本地文件名不存在。还应阅读生产资源/config 差异，文件名扫描不能代替内容审计。

## 2. 存储前提

Windows SQLite 路线采用本地 NTFS 私有根，按[配置合同](configuration.md#sqlite-当前应用存储配置)
提供 `DND_STORAGE_ROOT`，无需 Connector/J、MySQL 凭据或运行库迁移。先核对目标 Tomcat
启动身份、根所有者/ACL、目录边界及已有残留，保管真实数据后再切换配置。应用启动只取得
根锁，不自动创建战役或恢复残留；服务身份必须在启动、关闭、重开之间保持一致。

以下数据库前提仅用于明确保留的 MySQL 路线；数据库准备与 WAR 部署是两个独立步骤：

1. migrator 按顺序应用尚未执行的前向迁移；
2. 使用 [database/verify](../database/verify/) 中的只读脚本核对结构和 `schema_meta`；
3. 单独应用 [database/grants](../database/grants/) 中与当前应用职责匹配的最小运行授权；
4. 以只读诊断确认安装的最新迁移与应用预期一致。

V001—V018 已应用后不可修改、合并、重命名或重跑。SQL 文件存在、迁移成功、授权成功和应用启动是四种不同证据。不要把广泛 schema 权限授给运行账号。

当前候选的完整运行清单为 V001—V042；须按已安装账本，在独立迁移检查点完成尚缺的前向迁移、
核验与最小授权，再部署候选。来源链另为 V001—V012，两链共 54 份迁移源；
来源 SQL 留在 WAR 外，来源迁移与授权按[来源 schema 合同](rule-source-schema.md)另行执行。
内部快照、战斗及有限施法不会开放完整 base、Host 初始化或 archive format 2，也不改变默认发布版。

### SQLite 候选与数据边界

JSON 结构建库已用于[有限 SQLite 战役运行](storage-vitality-loop.md)，涵盖当前有限规则下的
角色/战斗/场景、参与和裁定事实及完整历史；当前范围见该运行合同。
MySQL 仍是未配置存储根时的默认模式，启用 SQLite 不会删除当前迁移或转换既有数据。
SQLite 候选须覆盖完整文件、同版恢复、锁和目标文件系统边界，WAR 必需/禁止项按
[测试指南](testing.md)核验。
部署仍执行本指南的停机、完整备份、候选审计、可回滚替换及验收，不另设自动切换程序。
SQLite 候选审计须包含结构 JSON、严格 codec/领域引擎、SQLite JDBC、Host 资源及
本机规则库服务/账本、机制支持校验以及 `storage/rules/` 五个导入候选；规则作者源保留在仓库 `rule-packages/srd51-runtime/`。
仅在满足[本地文件系统能力](configuration.md#sqlite-当前应用存储配置)的平台配置
`DND_STORAGE_ROOT`；Windows 使用 NTFS/ACL，进程中断与物理断电保证分开，不能以
Linux/WSL 或手工装配的测试容器代替原生 Windows 正式 WAR 验收。

切换前明确现有 MySQL 数据保管、残留和未决操作处置，备份外部配置及需要保留的数据。
当前可选模式按[配置合同](configuration.md#sqlite-装配与运行身份)独立装配，不要求新来源
JNDI 或两根变量；原 MySQL 仍是默认，旧装配退役另需完整替换和真实数据保管边界。
来源账本维护、授权与 WAR 部署分别执行。新运行库由产品新建/打开入口初始化，启动诊断不替
用户创建战役、补表或重建旧库。部署回退还原匹配的配置，保留新模型文件与残留，不用
旧 WAR 重新解释它们；无跨版本自动回退数据承诺。

数据库操作的原则与账号边界见[数据库文档](database.md)。当前无真实业务使用时，现有
`dnd_tool_se` 被采纳为本地开发部署运行库，但仍须先做只读盘点、停服、完整备份，并在物理隔离、
可销毁的 MySQL 实例内以原名 `dnd_tool_se` 原样重放完整迁移链；运行库不能作为
`MySqlIntegrationIT` 或可重复迁移测试目标。少量可丢弃测试数据不等于可以跳过备份、重跑已记录
迁移或绕过独立授权。

## 3. 外部配置

将无秘密示例按实际 `%CATALINA_BASE%` 复制到外部配置：

- [config/tomcat/ROOT.xml.example](../config/tomcat/ROOT.xml.example) → `%CATALINA_BASE%\conf\Catalina\localhost\ROOT.xml`
- [config/tomcat/setenv.bat.example](../config/tomcat/setenv.bat.example) → `%CATALINA_BASE%\bin\setenv.bat`

`ROOT.xml.example` 显式选择 SQLite。启动进程提供绝对 `DND_STORAGE_ROOT` 并启用
`EnvironmentPropertySource`，或直接在外部 context Parameter 中填写准确根。显式空值、
未解析占位符和无效根失败关闭；只有两处均没有配置才会进入原 MySQL 路线。
Windows 服务不读取 `setenv.bat`，必须配置实际服务身份和进程环境/JVM 选项。

默认 Tomcat Connector 只绑定 `127.0.0.1:8080`，shutdown listener 也限 loopback；不启用
AJP 或其他应用 context。可选玩家 HTTPS 另按下节启用。SQLite 的 16 MiB 导入限制由应用检查，示例验收容器的
表单 `maxPostSize` 为 `16778240`。保留 MySQL 时改用
[ROOT-mysql.xml.example](../config/tomcat/ROOT-mysql.xml.example)，并在外部提供原环境变量；
Connector/J 仍放在 `%CATALINA_BASE%\lib`，口令不进入 XML、批处理、JVM 参数或仓库。

### 可选玩家 HTTPS 入口

先完成本指南的候选构建、完整回归、WAR 审计及可回滚替换，再按明确授权配置网络。
玩家原型仅接入 `DND_STORAGE_ROOT` 的 runtime 战役；不开启此配置时保持原单 DM 部署。

1. 明确固定的局域网 IPv4 与 `8443`，配置[玩家请求边界](configuration.md#可选玩家-https-原型配置)。
   Host Connector 保持 `127.0.0.1:8080`；不要把它改绑 LAN 或添加转发 Host 的代理。
2. 准备设备受信任、SAN 精确包含所用 IPv4 的服务器证书，将 PKCS12 私钥库与口令保管在
   WAR/Git 外。使用[独立 Connector 示例](../config/tomcat/player-https-connector.xml.example)，
   通过外部环境提供私钥库位置和口令。现有 `EnvironmentPropertySource` 负责占位符；
   不在访问日志、批处理正文或 JVM 参数里填写秘密。
3. 在实际玩家设备安装所用 CA 的信任，并核对证书和地址。没有有效信任时停止接入，
   不提示忽略警告。防火墙只允许专用网络的选定 HTTPS 地址/端口，8080 仍不对外开放。
4. 用手机与桌面两类实际设备执行[玩家验收矩阵](design/v2/player-participation.md#验收与采用门)，
   特别检查 TLS 错误、错 Origin、访问 Host 路由、两玩家越权、撤回、接管与断线查证。
   loopback HTTPS 自动测试和手机尺寸模拟不替代此设备证据。
5. 关闭时撤销监听与防火墙规则，并清除两项玩家配置；保留匹配版本的存储根与完整文件。
   回退 WAR 仍按本指南，不用旧版解释新增参与事实，也不自动丢弃原运行。

示例和应用代码不会自动执行这些外部操作。证书更新/地址变化必须重新核对 SAN、应用
精确 origin 和设备信任，不能用备用 HTTP 地址掩盖失败。

### 自动化部署身份

交互式维护由明确授权的操作者执行，启动与关闭须使用约定的 Tomcat 运行账号；SQLite 根
归该运行账号所有。无人值守自动化采用独立的 Windows 本地部署身份 `dnd-tool-deployer`
作为目标合同。该名称记录的是待配置的
操作系统账号，不表示账号已经创建。它只应获得以下能力：

- 读取仓库和经审计的候选 WAR，写入仓库外候选/回滚目录；
- 停止和启动唯一指定的 Tomcat 服务；
- 备份、移除和替换该 Tomcat 实例的 `webapps\ROOT.war` 与 `webapps\ROOT`；
- 读取该实例最新日志和本机 loopback listener 状态。

该身份不得拥有通用本机管理员、任意服务控制、其他 Tomcat context、MySQL 账号管理或数据库
DDL 权限。它不保存 `dnd_tool_se_migrator` 凭据；SQLite Tomcat 使用具有该根私有权限的
运行身份，保留 MySQL 时继续使用最小权限 `dnd_tool_se_app`。Agent 可以自动完成构建、测试、WAR 审计和候选/活动哈希比较，但数据库
备份、正式迁移、授权、停启服务、活动 WAR 替换及真实业务写入仍是独立授权检查点。自动化只
消费操作者在当前维护进程中注入的秘密，不读取、回显或持久化完整环境。

## 4. 可回滚替换

1. 停止 Tomcat，并确认相关 Java 进程及 8005/8080、已启用的 8443 listener 均已退出。
2. 对当前 `webapps\ROOT.war`（若存在）、完整展开目录 `webapps\ROOT`、ROOT context、`server.xml` 和启动配置创建仓库外回滚副本；记录原本不存在的对象。SQLite 必须在停止后整体保管私有根，包括全部 run、journal、准备/清理材料及 saves，不选择性只复制 JSON。
3. 确认目标路径精确无误后，移除旧展开目录，复制经审计候选为 `webapps\ROOT.war`。
4. 比较候选和活动 `ROOT.war` 的 SHA-256；确保没有临时 WAR、旧并行 context 或部分展开目录。
5. 若启动需要交互输入数据库口令，保持 Tomcat 停止并交给操作者启动。

不要因两个独立 ZIP 构建的时间戳差异自动替换活动 WAR。只有已审计的明确候选才能进入部署流程。

## 5. 启动与验收

启动后只检查最新日志段，确认没有新的 `SEVERE`、`ERROR`、连接或 schema/hash 失败。系统层面应只有一个预期 Java/Tomcat 实例，8005/8080 只监听 loopback；默认 8443 不监听，显式启用玩家时仅监听已核准的精确地址并执行上节验收。

最小 HTTP 验收：

| 请求 | 预期 |
|---|---|
| `GET http://127.0.0.1:8080/health` | `200`、UTF-8 `text/plain`、正文 `OK` |
| `GET /` | `302`，相对定位到 `/host` |
| `GET /host` | MySQL 模式返回操作台；SQLite 模式进入 `/host/storage`，均有安全响应头和主机 Session |
| `GET /api/host/storage/state` | SQLite 模式返回当前运行/残留/文件状态；不执行恢复或业务写入 |
| `GET /api/host/storage/readiness` | `mode=SQLITE`；`200` 且 `ready=true` 的 IDLE/ACTIVE 才可新建/继续；需恢复、阻断或不可用返回 `503` 和稳定类别 |
| 主机数据库诊断 | 仅 MySQL 模式按其合同识别迁移/规则；SQLite 模式不据此判断就绪 |
| DM 入口的错误 Host、`localhost`、`::1` | 普通 `404` |
| DM 入口的 `/player`、`/api/player/*` | `404`；玩家只从显式配置的独立 HTTPS Connector 进入 |
| `/display`、`/api/public/*` | `404` |

只有 UI 或客户端 JavaScript 变化时才需要真实浏览器验收；应检查主要页面、表单和控制台错误。不要用 HTTP 探测执行真实业务写入，除非该写入验收被单独授权并有恢复方案。

就绪检查核验实际服务、可见保存文件及活动状态，不读取规则库或内置候选；新建/打开
另行核验准确原包与接受。`/health` 仅证明进程
存活。`STORAGE_RECOVERY_REQUIRED` 要求在页面显式恢复或精确放弃；`STORAGE_BLOCKED`
或 `STORAGE_UNAVAILABLE` 保留材料并按错误类别排查，不切换到另一个空根掩盖故障。
隔离正式 WAR 的可重复验收见[测试指南](testing.md#原生-windows-外部-tomcat-候选验收)。

### 日常打开与关闭

首次配置并验收后，可将 `http://127.0.0.1:8080` 保存为浏览器书签或 URL 快捷方式。
使用已配置的同一外部 Tomcat 实例。交互式独立实例的启动入口须显式设置
`CATALINA_HOME`、`CATALINA_BASE` 和 `JAVA_HOME`，再调用
`%CATALINA_HOME%\bin\startup.bat`；实例配置保存在 `%CATALINA_BASE%`。
服务安装由对应服务启动；就绪返回成功后打开书签。不得同时启动第二个相同端口或存储根的实例。

需要结束本次运行时先在页面执行 END，并确认保存完成及回到空闲；然后使用同一组
`CATALINA_HOME`、`CATALINA_BASE`、`JAVA_HOME` 调用 `%CATALINA_HOME%\bin\shutdown.bat`，
或停止对应服务，确认进程和 listener 退出。只关闭浏览器或 Tomcat
不会隐式 END；下次启动看到原 run 时先显式恢复，再继续原历史。异常退出同样遵循该路径，
不能删除 journal、手动重 seed 或以旧文件覆盖新状态。

## 6. 回滚

若启动或验收失败：停止 Tomcat，保留失败候选和最新日志证据，恢复整套旧 `ROOT.war` 与展开目录，再确认恢复后的活动哈希。数据库迁移是前向历史，不能通过替换 WAR 自动回滚；数据库恢复必须使用[备份与恢复](backup-and-restore.md)中的单独授权流程。

常见错误见[故障排查](troubleshooting.md)。
