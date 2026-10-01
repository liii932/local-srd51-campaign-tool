# 离线角色目录安装与提交查证

`tools/rule-packages/rule-package.sh` 是显式离线入口，复用 Maven 编译的
`com.dndtool.offline.rules`；这些类由 WAR 打包排除。它只写独立 `dnd_tool_rules`
中 `dnd5e2014_srd51_se` / 字符串 `"1"` 的 DRAFT 语言与工具熟练目录，profile 为
`srd51-character-catalog` / `1`。安装 18 项语言与 37 项工具不提供完整内容摘要、COMPLETE、发布批准、
运行快照或 archive 2 开放资格。运行库、默认 RELEASED v1、旧摘要域及迁移不变。

这里提供可执行组件与无 DB 故障测试。真实引擎的权限、触发器、并发、存储耐久、
网络响应丢失和资源容量仍须在测试连接保护通过后，单独授权隔离实例验收。
没有 schema 创建、迁移、授权、发布、自动重试、Tomcat/JNDI 或部署命令。

## 文件与制品

```bash
mvn -DskipTests compile
bash tools/rule-packages/rule-package.sh build rule-packages/srd51-complete /controlled/new-artifact
bash tools/rule-packages/rule-package.sh verify /controlled/new-artifact EXTERNAL_MANIFEST_SHA256
```

`build` 只接受新的输出目录；五个文件保留准确原始字节。输出清单含作者 schema、
manifest 版本、精确发布身份、canonical/archive 格式、算法、PARTITION、
`partition_keys` 和五项 `files`。每项恰为 path、role、byte_length、raw_sha256。
分区集合准确为 `character.language`、`character.tool`。
包展示名只来自作者头；PARTITION 清单禁止 `observed_content_sha256`，连 null 也拒绝。
清单自身不在 files 内；命令打印其原始 SHA-256，操作者另存于制品外的受控交付记录。
清单及哈希不是作者认证或发布批准，不随作者材料提供连接目标、凭据或 schema 升级。

当前封闭制品恰含 `author-package.json`、`character/languages.json`、`character/tools.json`、
`package-guide.md`、`notice.md` 和 `installation-manifest.json`；额外、遗漏、重复、
角色错误、未知路径、Windows 保留名、链接和目录逃逸均拒绝。清单版本/身份/格式
严格核对，五个文件包括文档和许可逐个有界实读、重算长度/raw hash，最后重新盘点。
作者头、语言和工具交给同一严格 reader，锁内不再读取目录。

| 输入预算 | 上界 |
|---|---:|
| 文件 / 目录 | 6 / 1（character） |
| 作者头 / 语言 / 工具 | 8192 / 262144 / 262144 字节 |
| 每个文档或许可 | 65536 字节 |
| manifest / 所有输入总和 | 16384 / 679936 字节 |
| manifest JSON | 深度 5、1024 token、每对象/数组 32 项、字符串 4096 UTF-16 单元 |
| 语言 / 工具 / 关系 | 18 行 / 37 行 / 0 |
| 每个源 SQL | 5 秒查询超时，驱动 socket 超时 15 秒 |
| CLI 进程 | 64 MiB 堆；120 秒 TERM，5 秒后 KILL |

解析整数只接受 ASCII 十进制 token，拒绝浮点、指数、强转、重复键和尾随内容。
输入分配受字节与节点预算约束；64 MiB 是测试/launcher 的 JVM 堆上限，不是 RSS。
容量测试同时填充全部作者文件与文档上界；此输入测试不替代实际磁盘/网络容量验收。

文件 IO 仅支持 Java `SecureDirectoryStream`，从绝对路径的文件系统根逐段
NOFOLLOW 打开目录并使用相对描述符读取，拒绝非普通文件；不支持此能力的原生 Windows
provider 失败关闭，使用 Linux/WSL 受控本地介质。构建输出父目录、证据/状态目录及其
父目录须由维护操作者控制，输入介质须在整个命令期间停止修改；不将 size/mtime
前后相等当恶意并发下的完整文件系统快照或硬链接隔离证明。不得允许其他用户替换
目录、改文件或创建硬链接/FIFO。launcher 的独立进程 deadline 是 IO 卡住时的终止边界；
终止不是原数据库事务回滚证明。直接嵌入 Java API 的调用方须保留同等进程期限。

## 独立维护证据与真实信任边界

每条源历史指定唯一、持久、所有执行者共用的 owner-only 状态目录，不因进程重启、
机器迁移或失败新建空目录绕过未决操作。独立维护目录也须 owner-only，包含
`maintenance.json`、`schema-audit.txt`、`history-audit.txt`、`isolation-audit.txt`。
工具不会生成这些批准报告。操作者配置文件选择目标；作者包和票据不能选择任意 URL。

维护方在停止其他维护写入、排除并发 DDL 后，人工审查完整实际定义与独立证据，
再填写以下字段。它们是由受控维护方担保的外部事实与授权输入，不是工具根据几个
布尔值推导的历史证明；报告摘要只防材料错配，不提供签名、认证或 PKI。

| maintenance.json 字段 | 要求 |
|---|---|
| evidence_version | 整数 1 |
| target / lineage | 小写 ASCII 受控标识，永久关联准确源与连续历史 |
| jdbc_url | 精确 `jdbc:mysql://127.0.0.1:PORT/dnd_tool_rules`，单 TCP 端点，无 URL 参数 |
| user / current_user | 窄 installer 账号；CURRENT_USER 精确 `账号@127.0.0.1` |
| server_uuid | 当前实例 UUID，仅为现场定位，不单独证明谱系 |
| grants_sha256 | 实际 SHOW GRANTS 每行原文按字符串序排序、LF 连接且末尾 LF 的 UTF-8 SHA-256 |
| schema_audit_sha256 / history_audit_sha256 / isolation_audit_sha256 | 对应三个报告原始字节 SHA-256；每份 1—65536 字节 |
| expires_at | UTC ISO-8601 时间；必须未过期 |
| permission | 准备/安装为 INSTALL，显式查证为 RESOLVE |
| isolated_operation | 新操作为 `none`；RESOLVE 为已完成独立隔离的准确原 UUIDv4 |
| history_minimum_version | 已独立保管历史检查点的非负安装计数下界 |
| state_directory | 固定、绝对、owner-only 的共享状态目录 |

`schema-audit.txt` 必须记录：批准规则链完整定义比较；七个准确 InnoDB 表及全部列、
索引、CHECK、21 个触发器和其 DEFINER；源内 RESTRICT FK 与**所有 schema 的入向引用**；
无额外表/视图/函数/例程/触发器副作用或运行库访问；所有授予和继承能力与
`rule-source-installer.sql` 的最小权限相符；实际列级 S/root FOR UPDATE 可用；
所有内容安装/发布入口共享 S 且永久事实不能旁路删除；迁移/发布/DDL 已隔离。
用有足够可见性的独立管理员/migrator 身份只读审计并记录实际材料与批准来源。
窄 installer 看不到的元数据不能以空结果视为安全，也不能为了审计扩大 installer 权限。

`history-audit.txt` 必须给出 target/lineage 的独立保管与接管链、原源与后继关联、
完整永久非删除接受历史、无备份回退/空克隆/人工删改/滞后副本、检查点版本及所有
维护入口受控的证据来源。自洽计数、schema 名、server UUID 或同种子不能代替这份证明。
`isolation-audit.txt` 必须给出授权操作与期限、未决交接盘点；查证时记录原进程/连接/
任务/重连路径已经无法继续发 SQL/commit 的实际证据。仅 close、进程消失或新文件锁
不等于完整隔离。未知、未审新对象、无完整历史、第二写入者或丢失原票据都保持阻断。

CLI 在同一专用连接核对实际默认库、实例 UUID、CURRENT_USER、无活动角色、非只读主库、
strict SQL mode、唯一批准 schema 账本和 SHOW GRANTS 摘要；它不会假称这些只读查询能
重新建立管理员不可见对象或历史真实性。证据过期、缺失、错配、权限/现场变化即拒绝。
连接禁用自动重连、多语句、本地文件导入，不借运行连接池；秘密仅从终端无回显输入。

## 准备、执行、查证

另行授权真实源写入之后，指定外部 Connector/J 文件；路径/口令不进入票据或 Git。

```bash
export DND_RULE_INSTALLER_JDBC_JAR=/controlled/driver/mysql-connector-j.jar
bash tools/rule-packages/rule-package.sh prepare /controlled/evidence /controlled/artifact MANIFEST_SHA256
bash tools/rule-packages/rule-package.sh install /controlled/evidence ORIGINAL_UUID.json /controlled/artifact MANIFEST_SHA256
```

prepare 完整验证输入和目标、只读取得预期代次后先形成版本 1 指纹，再以安全随机生成
UUIDv4。票据保存准确发布/profile、原 expected、指纹、manifest 与目标/谱系关联，不含
密码或作者全文。状态目录独占 file lock，票据 CREATE_NEW、文件 force、目录 force
和实际读回全部成功后，install 才能开始。发 SQL 前同样持久保存单个 `pending` 原意图；
崩溃或 UNKNOWN 留下它并阻断后续写入。不得删除、覆盖票据或复用 UUID 改意图。

安装单连接 READ_COMMITTED、autoCommit=false，首业务 SQL 按 PK 锁永久 S，再锁目标根，
有界读取全源 release→installation→partition→语言→工具，核对连续代次、唯一操作、父子闭包、
当前摘要/scope 与 S 精确行数/接受计数。每表最多 16385 行探测，元数据上限 16384；
本 profile 只允许一个非空受支持根，其他根只能是合法空头并计费。未知非空 profile
失败关闭；多发布完整 profile 与未来 COMPLETE 全域内容验证不是本组件的能力。

原接受先判，新写才检查 DRAFT、expected、格式和预算；精确删除原目录再插入 18 项语言与 37 项工具，
更新头、插入永久事实及两个分区、更新 S，各语句必须恰好影响 1 行。新根共 61 个 DML，完整整替换
115 个 DML。整套后像从 JDBC 实读并逐字段比较，包括其他根和历史不变，最后一次 commit。
任何写点、影响数或读回异常完整 rollback；仓储不自借连接、不提交、不用 savepoint。

COMMITTED 只来自明确 commit 或同 S 下核实的原永久接受；commit 响应丢失、rollback
失败或查证不完整均 UNKNOWN。明确 commit 后 close/输出/票据补记失败不会降格，但仍
可保留维护阻断。原票据重试必须重新全读实际输入；显式 resolve 无需旧制品：

```bash
# 维护方先更新独立隔离/历史证据：permission=RESOLVE，isolated_operation=原UUID。
bash tools/rule-packages/rule-package.sh resolve /controlled/evidence ORIGINAL_UUID.json
```

resolve 使用另一健康源连接，先同 S，再读取完整永久链，**仅 SELECT/SHOW 与 rollback，
零 DML/新 ID/自动重装**。原 ID/指纹一致返回原接受代次、scope、分区、manifest、展示名、
观测、installed_at，另报当前代次/状态。当前更高代或 RELEASED 不抹除原事实；历史 COMPLETE
观测不与后继 PARTITION 的 NULL 比较。报告未来 COMPLETE 事实不证明其全域内容健康。
原 ID 缺席只有在独立原执行者隔离、真实 S 已决、可信完整非删除历史及闭包均满足后才返回
NOT_COMMITTED。查证事务自己的 rollback 成功不能证明原事务回滚；任何查证失败仍 UNKNOWN。
ID/指纹冲突保留阻断。查证结束不自动重试；新尝试需要原输入仍完整、原 expected 仍匹配和
新维护授权，过期意图须先已决再重新准备，不能自动改 expected。

## 指纹和验证

`InstallationFingerprint` 使用独立域 `DND_TOOL_SE_SOURCE_INSTALLATION_REQUEST_V1`，
大端 LP(domain)+U32(1)+U16(17)，随后每字段 U16 序号/U8 tag/U32 payload 长度/payload。
17 字段依次为 INSTALL、module_key、release_version、expected、author schema、manifest
版本、canonical、archive、算法、包展示名、PARTITION、NULL 完整观测、manifest hash、
profile key、profile version、分区路径集合和五文件真实盘点。NULL 不是空串或零；
技术文本 ASCII、展示名 NFC UTF-8，整数 U64，集合按键/路径 ASCII 序编码。操作 ID、时间、
源本地 ID、连接、绝对路径和凭据不入指纹；目标/谱系另由可靠票据与维护边界绑定。

永久历史中的 `srd51-language` / `1` 原票据仍可显式 resolve，返回原事实自己的分区集合。
新安装仅接受角色目录 profile；旧语言 PARTITION 不被改写成新的安装事实或完整运行身份。

```bash
mvn '-Dtest=RuleArtifactTest,TicketStoreTest,SourceInstallationTest' test
mvn '-Dtest=RuleArtifactTest,TicketStoreTest,SourceInstallationTest' '-DargLine=-Xmx64m' test
```

固定 hex 向量由独立 Python 大端编码器生成并保存在 test resources；维护工具
`tools/rule-packages/fingerprint-vector.py` 不调用生产 encoder，普通构建不重写向量。
JDBC 代理故障测试执行真实 SQL 适配与协调代码，覆盖各写点异常/影响数、第九行读回损坏、
提交响应丢失、rollback/close 故障、永久闭包与元数据容量、无输入 resolve 与重放优先。
它们不证明真实 MySQL 授权、触发器、并发、恢复连续性或 fsync 物理介质耐久。
