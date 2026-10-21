# Schema 2 离线安装、内容审查与本地接受

`tools/rule-packages/rule-package.sh` 的 `schema2-*` 命令显式维护独立规则源中的准确制品与
本地 DM 接受事实。它们调用 `Schema2SourceInstallation`，不由应用启动或运行请求触发；
全部 `offline.rules` 类继续排除在 WAR 外。制品协议见
[生产制品](schema-2-rule-artifacts.md)，存储、迁移及最小权限见
[来源账本](schema-2-source-ledger.md)。旧 schema 1 PARTITION 流程见
[角色目录安装](offline-language-installation.md)。

`schema2-*` 独立进程使用 256 MiB JVM 堆预算，以容纳写前/写后完整账本和有界解析临时对象；
它不是 RSS 上限。进程期限仍为 120 秒，超时先 TERM、5 秒后 KILL。旧 schema 1 命令继续使用
64 MiB 堆。直接嵌入 Java API 的调用方须保留同等资源和期限边界。

这里交付的是有界生产编码、原包保管、不可变安装/接受账本、显式审查及未知提交查证。
真实 MySQL 的授权、触发器、锁竞争、响应丢失、恢复连续性和物理介质耐久须另行授权验收。
源迁移、账号授权、运行库写入、发布、部署及 archive 2 激活均不是这些命令的动作。

## 当前安装资格

内核和内部快照支持有限语言、工具及[初始授予](schema-2-proficiency-grants.md)投影，
授予计划具备只读消费者；完整基础体系和业务初始化尚未接通。生产安装采用封闭门：

| 实际内容 | 离线结果 |
|---|---|
| 任意 `base` | `S2_BASE_INCOMPLETE`，在源 DML 前拒绝；语言验证基础不能冒充完整基础 |
| 有任何定义、条目或修改的扩展/兼容模组 | `S2_MECHANISM_UNSUPPORTED`，在源 DML 前拒绝；目录与参数模型不授予机制资格 |
| 作者结构自洽、内容操作为空的扩展/兼容模组 | 可保管并显式接受；依赖可尚未安装，实际组合仍须完整基础、准确依赖及运行消费者 |

最后一行只说明空机制集合的检查通过。它不表示内容型模组、可用组合、运行规则目录、
完整类型化来源与业务快照、初始化、恢复或 archive 2 已完成。作者自报资格、公开布尔参数、
成功解析、生产摘要或 DM 接受都不能绕过此门。安装协调层与仓储在新 DML 前共同执行此检查；
扩展支持范围须随真实消费者和对应合同、测试一起接入。
来源 V004 对初始授予的类型化作者字段与原包作等价检查；其内部链路测试不放宽上述资格门。

## 独立证据和能力

schema 2 单独读取 `evidence_version=2` 的 `maintenance.json`；schema 1 的 reader 继续只接受
版本 1。版本 2 保留旧流程的全部字段，并增加必需非负整数 `schema2_history_minimum_version`。
它与受控共享状态目录、三个非空报告及其原始 SHA-256 一起由独立维护方保管，不由作者、
制品构建器、安装器或 CLI 生成。报告摘要只能检测材料错配，不能证明管理员身份或历史真实性。

| 字段或材料 | 要求 |
|---|---|
| `target` / `lineage` | 永久绑定准确来源及独立连续历史；票据与共享目录同时核对 |
| `jdbc_url` / 实例 / `current_user` / grants | 沿用单 loopback TCP、固定源 schema、无活动角色、独立 schema/授权现场检查 |
| `permission` | 准备/安装为 `INSTALL`；准备审查/接受为 `ACCEPT`；查证为 `RESOLVE` |
| `isolated_operation` | 新操作为 `none`；查证为已完成独立执行者隔离的准确原 UUIDv4 |
| 两个 history minimum | 分别约束锁住的旧永久 S 版本和 schema 2 完整账本版本，不可用零点副本代替连续历史 |
| `state_directory` | 每条源谱系唯一、绝对、owner-only，全部 schema 1/2 安装、接受和查证共用 |
| `schema-audit.txt` | 实际批准 V001—V004 全定义及全部对象、触发器/DEFINER、入向引用、角色与最小权限，排除并发 DDL/其他写入口 |
| `history-audit.txt` | 两个账本检查点、永久非删除完整历史、独立接管链及没有回退/空克隆/删改/滞后副本的实际证据 |
| `isolation-audit.txt` | 准确目标/谱系、能力与操作、期限、共享状态目录及未决交接；查证还证明原进程/连接/重连/任务路径无法继续 SQL 或 commit |

所有报告须与本次维护配置中的 target、lineage、permission、原操作、检查点及状态目录一致。
工具核对报告原字节摘要和有效期，但不通过文本搜索或自报几个标志推导完整批准。
旧 S 检查点比较不声称以新窄账号重新读取 schema 1 业务内容；旧内容和历史的完整审计仍由独立报告负责。

安装与接受使用不同最小权限账号；`rule-source-installer.sql` 没有接受 INSERT，
`rule-source-accepter.sql` 没有制品 INSERT。`INSTALL` 证据在调用接受入口时直接失败；
`ACCEPT` 证据不能安装。每次另从无回显终端输入该能力自己的凭据，不把连接或秘密放进作者材料、
票据、审查文件或 Git。账号模板是待批准执行的 SQL 源，文件存在不表示已经应用。

## 验证、准备和安装

```bash
mvn -DskipTests compile
bash tools/rule-packages/rule-package.sh schema2-verify /controlled/module.zip

export DND_RULE_INSTALLER_JDBC_JAR=/controlled/mysql-connector-j.jar
bash tools/rule-packages/rule-package.sh schema2-prepare-install /controlled/install-evidence /controlled/module.zip
bash tools/rule-packages/rule-package.sh schema2-install /controlled/install-evidence ORIGINAL_UUID.json /controlled/module.zip
```

`schema2-verify` 仅验证准确传输制品，打印身份、角色、生产 profile 及 raw/逻辑双摘要。
准备安装先完整读取 ZIP 并执行封闭资格门，再以专用源连接完成 preflight、同 S 下完整观察；
结束只读事务后保存原预期计数票据。票据 CREATE_NEW、文件 force、目录 force 及实读全部成功
才算准备完成。安装重新读入原包，逐项匹配票据，先持久保存共享 `pending` 原意图，再进入源事务。

准确身份已安装且原 ZIP/逻辑/profile/角色完全一致时，准备直接返回原永久事实 UUID，不生成
新操作或新票据。相同准确版本的不同 ZIP 即使逻辑相同也拒绝；修改须换准确版本。
原票据重放优先查原永久操作，再检查新写预期，因此更高计数不抹去原已提交事实。

## 审查准确内容和显式接受

```bash
bash tools/rule-packages/rule-package.sh schema2-prepare-accept /controlled/accept-evidence MODULE_KEY EXACT_VERSION
# DM 阅读状态目录内打印出的 ORIGINAL_UUID.review.txt 及准确原包 ORIGINAL_UUID.review.zip。
bash tools/rule-packages/rule-package.sh schema2-accept /controlled/accept-evidence ORIGINAL_UUID.json EXPLICIT_REVIEW_SHA256
```

准备接受从实际来源重新有界读取、解析准确原包并核对完整账本，导出可读审查文本和保留准确字节
的伴随 ZIP。文本包含目标/谱系、当前完整预期计数、原安装 UUID/指纹/序号、准确身份/角色、
author/artifact/review 协议、生产 profile、raw/逻辑双摘要、完整规范逻辑声明，以及由已验证 ZIP
reader 提供的每个原始作者和许可/说明成员。原成员以 JSON 引号展示准确 Unicode 文本，控制
字符使用可见转义；原始 UTF-8 和 ZIP 字节完整保留在伴随原包中。没有第二条宽松 ZIP 解析路径。

审查文件、原包和原接受票据都先 force 并实读。DM 阅读实际内容后，必须在接受命令中显式传入
打印的审查摘要；工具不会把准备、显示摘要或作者声明自动转为接受。接受重新核对可靠票据、
审查文件摘要和伴随原包，并在锁内用实际已安装材料重建审查文本逐字节比较，匹配全部原安装
事实和预期计数后才登记独立永久接受。审查篡改、过期源计数或替换原包均不能部分写入。
已经接受的准确身份在准备时返回原接受 UUID，不重复登记事实。

票据版本 2、指纹版本 1 采用独立域 `DND_TOOL_SCHEMA2_SOURCE_OPERATION_V1` 加 LF，随后是
固定字段顺序的紧凑 UTF-8 JSON 意图。意图绑定 INSTALL/ACCEPT、target/lineage、原预期三个计数、
准确身份/角色、schema/protocol/profile、raw/逻辑双摘要，以及接受的原安装 UUID/指纹/序号、
review protocol 和审查摘要。原操作 UUID 不进入指纹；它与不可变意图由可靠票据一并保管。
测试使用独立 Python ordered-JSON/SHA-256 固定向量核对，不借生产 encoder 生成期望。

## 事务、共享阻断和查证

schema 1/2 共用同一 `executor.lock`、source/lineage 目录绑定及唯一 `pending`。任何版本的未决
标记，包括未知或损坏标记，均阻断新维护，不能换目录或删除文件绕过。版本 2 票据不覆盖版本 1。

专用连接先核对实际源环境、schema 历史和 grants，再设置 READ_COMMITTED、关闭 autoCommit。
首业务 SQL 锁原永久 S，然后锁 schema 2 控制行，完整读取全部安装/接受及计数。元数据先核对
长度和总预算，再有界读 BLOB；每个原包重新严格解析并重算双摘要。全量闭包和原预期校验完成
后才调用仓储一次。安装或接受各写事实与对应控制计数，全部后像从 JDBC 实读并逐字段比较，
最后只提交一次。仓储不提交、不借连接、不用 savepoint；任何写点失败完整回滚。

明确 commit 或同 S 下核实的原永久事实才报告 `COMMITTED`。commit 响应丢失、rollback 失败或
查证未完成均 `UNKNOWN`，保留原标记且不自动重试。明确 commit 后 close、标记收尾或输出失败
不降格提交结论；连接关闭不确定时继续阻断。`Result.acceptanceFact` 仅表示返回事实属于接受
账本，不是某身份当前接受状态查询。

```bash
# 维护方先独立完成原执行者隔离与完整历史证明，再提供 RESOLVE 证据及准确 isolated_operation。
bash tools/rule-packages/rule-package.sh schema2-resolve /controlled/resolve-evidence ORIGINAL_UUID.json
```

查证使用另一健康专用连接和原票据，按同 S 读取完整账本，仅 SELECT/SHOW、锁读和 rollback，
零 DML，不需要旧制品或审查文件。原 UUID/指纹与完整绑定一致返回原事实；UUID 冲突继续阻断。
原 UUID 缺席只有在独立原执行者隔离、S 已决、可信完整非删除历史及全部闭包成立时才返回
`NOT_COMMITTED`。查证事务自身 rollback 成功不证明原事务回滚。禁止更换操作标识、自动修正
expected、自动重装或把一次新连接成功当作历史证明。

## 验证边界

```bash
mvn '-Dtest=Schema2OperationTicketTest,Schema2SourceInstallationTest,Schema2RulePackageCliTest,JdbcSchema2RuleSourceTest,Schema2SourceLedgerContractTest,TicketStoreTest,SourceInstallationTest' test
```

无 DB 测试覆盖真实生产 reader→协调层→JDBC SQL 仓储→实际读回、独立证据和共享票据；另覆盖
双方能力、审查篡改、准确重导入/同版重包、陈旧计数、每个写点及影响数、提交响应丢失、回滚/
关闭故障和原意图零 DML 查证。CLI 进程测试核对稳定业务错误、资格门和无异常堆栈输出。
这些是内部与代理协议证据，不证明真实账号能力、触发器、并发锁、数据库恢复或容量。
