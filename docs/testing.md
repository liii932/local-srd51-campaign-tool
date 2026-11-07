# 测试指南

测试分为聚焦单元/边界测试、完整 Maven 回归、显式 MySQL 集成测试、WAR 审计和必要的手工 HTTP/UI 验收。仓库文档记录可重复方法；某次运行的动态计数和哈希应留在 CI 或发布记录中。

## 1. 聚焦测试

默认只运行覆盖本次变化的最小充分测试集合。先检查本次任务的 diff、调用方、共用规则与资源引用，再选择相关测试类，不只按同名类匹配。执行前简述范围和理由，例如：

```bash
mvn '-Dtest=CheckTransactionServiceTest,JdbcCheckExecutionRepositoryTest' test
```

根据变更覆盖正常行为、边界值、Unicode 码点/NFC、非法冻结数据、过期 `row_version`、幂等冲突、事务失败与禁止部分写入。重命名批次还应覆盖受影响的 Servlet 注册、反射字符串、页面资源和脚本引用。

原型验收只针对当前应用版本：验证当前格式的完整往返及不支持格式的明确拒绝，不要求旧存档
仍可导入。设计替换时同步改写或移除只固定旧模型的测试，保留当前有效的安全、事务和失败路径
覆盖；既有测试通过或历史批准不能单独证明该设计应继续保留。

纯文档或 skill 变化只检查内容、链接和格式，不运行 Maven；只确认生产代码可编译时使用 `mvn compile`。定向 `test` 已含前置编译阶段，不需要机械重复 `compile`。保留无匹配测试的失败行为，不用 `failIfNoTests=false` 或 `surefire.failIfNoSpecifiedTests=false` 隐藏选错测试。

只有需要制品时才运行 `package`：相关测试尚未运行时用 `mvn '-Dtest=实际相关测试类' package`；相同代码、资源、依赖和配置已有本次定向通过证据时，可用 `mvn -DskipTests package`，并明确这是跳过测试的打包。生产代码或打包资源变化仍按第 6 节审计 WAR，不能把打包当作全量通过。

`-Dtest` 仅约束 Surefire。使用 `verify`、profile 或多模块 reactor 前检查 POM 及插件绑定，不能据此假定 Failsafe、其他插件或所有模块均已缩小范围。MySQL 集成测试仍按第 3 节单独授权和选择目标。

相关测试失败时先修复并重跑相关范围，不自动扩大为全量。复用本次对相同代码、资源、依赖、配置及相关环境取得的有效结果；后续改动影响证据时才重跑对应范围。不得同时运行会写同一 `target` 的 Maven 命令。

规则模型归属或诊断装配变化时，先覆盖固定 canonical/hash 向量、目录 JDBC、发布门和旧单池诊断：

```bash
mvn '-Dtest=ModuleCanonicalEncoderV1Test,ModuleCanonicalEncoderV2Test,ModuleContentHasherTest,BuiltinModuleHashManifestTest,BuiltinModuleReleaseRegistryTest,JdbcModuleCatalogRepositoryTest,ModuleIntegrityServiceTest,SchemaMigrationsTest,DatabaseSchemaVerifierTest,DatabaseDiagnosticsTest,DatabaseDiagnosticsFactoryTest,DatabaseStartupListenerTest,HostDatabaseDiagnosticServletTest,CampaignArchiveCapabilityBoundaryTest' test
```

再按真实引用闭包加入受影响的角色、检定、存档、规则浏览和语言投影消费者。诊断测试须证明
打包迁移/schema 在规则之前校验、失败不继续查询、只查原 `jdbc/DndToolSE`、查询资源关闭、
无数据写入、每次 GET 更新结果及有限 HTTP/日志类别。包迁移打包时排除旧路径残留类，
特别是旧 listener，避免重复启动诊断；这些无数据库测试不构成真实 MySQL 或部署验收。

### Tomcat 测试依赖

`pom.xml` 的 `tomcat.version` 统一管理 `tomcat-dbcp` 与 `tomcat-embed-core`。
两者均保持 `test` scope，升级时使用同一 Tomcat 10.1 补丁版本，并运行：

```bash
mvn '-Dtest=MySqlIntegrationTestSupportTest,StorageHttpIntegrationTest' test
```

前者使用无网络工厂验证连接入口与连接池配置；后者在临时 SQLite 存储上验证真实 HTTP
链路，运行前须确认 `127.0.0.1:8080` 空闲。两者均不连接真实 MySQL。Tomcat 测试依赖
及容器提供的 Servlet API 不得进入生产 WAR；更新这些依赖不会升级外部 Tomcat 实例，
外部容器仍按[部署指南](deployment.md)独立维护。

## 2. 必要时的完整验证

不默认执行裸 `mvn test`、`mvn verify` 或 `mvn clean verify`。只有用户明确要求，当前发布/部署/合并检查点明确要求，或已确认跨模块/共用核心影响且定向覆盖不足时，才运行全量。执行前说明具体影响和覆盖缺口；文件数量、路径、JSP/CSS/资源变化或任务收尾本身不是触发理由。

全量范围和 `clean` 分开判断。通常全量使用 `mvn verify`；有残留产物证据、删除/重命名可能遗留旧产物，或需要可重复的干净候选时才加 `clean`。发布/部署仍遵守[部署指南](deployment.md)要求的干净构建：

```bash
mvn clean verify
```

所有验证报告必须来自已结束的进程，列出实际命令、退出码、测试总数、失败、错误、跳过和最终 `BUILD SUCCESS`/失败，以及未运行范围。不要从截断或仍在运行的输出宣称成功，不将定向通过、零测试或跳过测试表述成全量通过。

全量运行中断或修正局部失败后，可保留本次已完成、输入未受影响的通过报告，只补跑失败、
未完成及修改影响的测试。逐类核对合并覆盖与跳过原因，分别报告原命令和补跑结果；不能把
它描述成一次全量命令通过。需要更换构建目录时复制当前源码和资源，保持相对目录结构，
在无旧产物的目录构建候选；不要复制秘密、真实数据或旧 `target`。

普通 `clean verify` 不应写入业务数据库。数据库相关单元测试使用 mock、临时结构或静态 SQL 检查。

## 3. MySQL 集成测试

`MySqlIntegrationIT` 默认不参加普通构建。它只允许连接独立、一次性的可写测试库，并在连接中创建临时表，用于验证：

- JDBC 提交、回滚、`row_version` 与幂等重放；
- 调用方拥有的 `SERIALIZABLE` 事务；
- 整战役导入失败时无半成品；
- Tomcat DBCP 连接借出/归还及连接状态恢复；
- 权威检定、骰子、效果、事件、字段变化、版本和幂等结果的原子提交/回滚。

运行临时表套件时，可在数据库管理工具中审核并执行
[database/test/create-integration-database.sql](../database/test/create-integration-database.sql)，建立专用
`dnd_tool_se_it` schema 和临时表测试账号。只在未保存的编辑器缓冲区或仓库外临时副本替换密码
占位符；该引导脚本不执行应用迁移。完整迁移链验证不复用这个 schema 或账号，必须
使用下面定义的物理隔离实例。

Linux/WSL Agent 使用已验证的原生 Maven，并在同一 Bash 会话中安全读取测试口令：

```bash
(
  read -r -s -p 'MySQL integration password: ' DND_MYSQL_INTEGRATION_PASSWORD; printf '\n'
  export DND_MYSQL_INTEGRATION_PASSWORD
  mvn \
    '-Ddnd.mysql.integration=true' \
    '-Ddnd.mysql.integration.confirmWritable=true' \
    '-Ddnd.mysql.integration.url=jdbc:mysql://127.0.0.1:3306/dnd_tool_se_it?connectionTimeZone=UTC' \
    '-Ddnd.mysql.integration.user=dnd_tool_se_it' \
    '-Dtest=MySqlIntegrationIT' test
)
```

Windows 专属操作可在同一安全边界内使用等价 PowerShell 环境变量语法。测试代码仅允许明确的
专用目标 `dnd_tool_se_it`；`dnd_tool_se`、`dnd_tool_rules`、其他库及无法明确解析的目标均在
连接前拒绝。不得修改该保护来复用少量测试数据。全部集成测试被跳过不构成验收；
不要打印完整环境，也不要把测试口令或完整业务数据写入日志。

允许的 URL 是普通单主机 `jdbc:mysql://HOST[:PORT]/dnd_tool_se_it`，可选且唯一的查询参数为
`connectionTimeZone=UTC`。拒绝多主机、负载均衡/复制、用户信息、百分号编码、片段、额外路径及
其他参数，避免驱动扩展属性覆盖连接目标或执行额外会话 SQL。

连接保护本身使用无数据库回归：

```bash
mvn '-Dtest=MySqlIntegrationTestSupportTest' test
```

这组测试在显式启用、配置齐备且确认可写的条件下，分别经过 `open` 与 `pooledDataSource`
的配置及目标检查路径，注入连接/池工厂核对非法目标的实际连接调用计数为零；正例证明专用
目标能通过检查。它不连接真实数据库，也不扩大临时表账号或独立迁移验收路径的权限。

### 3.1 自动化数据库验证顺序

1. 普通 `mvn clean verify` 不连接任何业务数据库。
2. `MySqlIntegrationIT` 只连接 `dnd_tool_se_it`，由 `dnd_tool_se_it` 用户创建连接级临时表；测试结束后不得留下持久业务表或数据。
3. V001—V042 完整迁移链只在物理隔离且可销毁的 MySQL 实例或容器中演练；该实例内部创建原名 `dnd_tool_se`，不与运行实例共享 schema、账号或持久 volume。
4. 自动化直接按生产清单读取仓库中的原始迁移文件，不生成 SQL 副本、不改写 `USE`、不重算摘要。每个迁移使用新的客户端连接并显式把默认数据库设为 `dnd_tool_se`；这是 V007 本身没有 `USE` 时仍能正确定位的必要条件。
5. 迁移完成后，使用该隔离实例内的 `dnd_tool_se_validation_ro` 核对完整 `schema_meta`、可见结构和目录计数。MySQL 按 `TRIGGER` 权限过滤的触发器定义由隔离实例内的迁移身份在停止迁移写入后核对，但验证步骤只能执行审核过的 `SELECT`/`SHOW`。
6. 运行实例的 `dnd_tool_se` 只允许 `dnd_tool_se_agent` 做只读盘点/Harness 比较。向它应用迁移、授权或测试写入不是测试套件的一部分，必须转入独立部署检查点。

账号/schema 合同及现有运行库的阶段性使用决策见[数据库说明](database.md)。

## 4. 迁移与规则测试

[schema 2 有限组合内核](design/v2/schema-2-composition-validation.md#9-内部组合内核)的无数据库定向命令：

```bash
mvn '-Dtest=Schema2CompositionGoldenTest,Schema2CompositionRejectionTest,Schema2CompositionParsingTest,CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest,ModuleCanonicalEncoderV1Test,ModuleCanonicalEncoderV2Test,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

新内核测试从固定作者字节核对独立最终字段、31 条覆盖链及验证专用摘要，覆盖 N01—N30、
严格解析与预算、依赖/联合版本/顺序、启停与清空、同值覆盖、不可变性及链篡改；相邻测试
保护既有 schema 1、canonical 和发布门。期望在 `src/test/resources/schema2-composition/`，
固定验证 ZIP 以十六进制文本保存，不依赖运行时读取设计文档或被测实现生成期望。
通过只证明这组有限类型的内部纯计算；生产资格仍拒绝，不涉及安装、SQL、HTTP 或业务执行。
WAR 须包含 `module/composition` 的内核类，并排除全部作者、验证 ZIP 和测试向量。

[schema 2 生产制品](rules/schema-2-rule-artifacts.md)与
[安装、独立接受链路](rules/schema-2-installation-and-acceptance.md)的无数据库定向命令：

```bash
mvn '-Dtest=ProductionRuleArtifactTest,Schema2CompositionGoldenTest,Schema2CompositionRejectionTest,Schema2CompositionParsingTest' test
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2SourceInstallationTest,Schema2OperationTicketTest,Schema2RulePackageCliTest,JdbcSchema2RuleSourceTest,Schema2SourceLedgerContractTest,TicketStoreTest,SourceInstallationTest,RuleSchemaMigrationsTest,RuleSourceSchemaContractTest,RuleDatabaseSchemaVerifierTest' test
```

第一组核对有界 ZIP、安全路径、原始字节、生产编码及独立摘要向量，并保护既有组合语义。
第二组覆盖原包与接受事实分别不可改写、来源双摘要回读、独立操作指纹、实际协调器与 JDBC
仓储衔接、共享维护锁/未决标记、显式审阅、旧票据、故障回滚及提交结果未知后的原操作查证。
CLI 子进程测试核对真实命令的稳定诊断；共用存储和旧离线安装测试保护 schema 1 路径。
安装、维护证据和票据测试依赖 POSIX owner-only 权限与 `SecureDirectoryStream`，须在支持这些
能力的 Linux/WSL 文件系统运行；纯 ZIP/组合测试另按自身前提执行。原生 Windows provider
不支持这些目录能力，相关安装测试可能在建立夹具或打开安全目录时失败，不能据此宣称
安装链路通过，也不能把省略相关测试后的结果称为完整回归；应记录平台限制并在支持环境重跑。
来源容量用例在 256 MiB 测试堆中保留前像，安装接近 raw/expanded 各 8 MiB 总限额的最后一个包，
核对完整后像，并证明下一包在 DML 前拒绝；这不替代真实驱动的容量与时限验收。
来源 schema/授权的静态测试和 JDBC 协议代理不替代真实 MySQL 的权限、约束、并发及持久化验收。
当前仅无内容扩展/兼容包可安装和接受；不完整基础包与尚未集成的机制仍失败关闭，接受事实
不等于可运行组合或完整规则发布资格。生产纯制品类进入 WAR，离线 CLI、来源 SQL、原包、
审阅导出、维护证据和全部测试向量必须留在 WAR 外。

[schema 2 来源到运行快照](rules/schema-2-runtime-snapshot.md)的无数据库定向命令：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=JdbcSourceSchema2RepositoryTest,JdbcSchema2RuntimeSnapshotRepositoryTest,Schema2SnapshotTransferServiceTest,ProductionSchema2StoredProjectionTest,Schema2RuntimeSnapshotSchemaTest,ProductionRuleArtifactTest,Schema2CompositionGoldenTest,Schema2CompositionRejectionTest,Schema2CompositionParsingTest,JdbcSchema2RuleSourceTest,Schema2SourceInstallationTest,SchemaMigrationsTest,ToolCatalogSchemaTest,RuleSchemaMigrationsTest,V011CompleteCharacterCatalogDraftTest,DatabaseDiagnosticsTest,DatabaseSchemaVerifierTest,CampaignArchiveCapabilityBoundaryTest' test
```

测试使用真实协调器和 JDBC 仓储，以及具有事务前像的 SQL 代理；独立固定向量核对实际类型化
回读的组合、最终规则与完整覆盖链。覆盖来源事务关闭后才获取运行连接、守卫为首条运行 SQL、
精确恢复绑定、逐表写入失败、回读篡改、幂等冲突、提交结果未知和隔离后的无来源查证。
有限基础包仅作为账本完整性测试材料；测试不通过离线安装资格门，也不授予游戏执行资格。
V021/V023 静态结构与授权测试不能代替真实 MySQL 迁移、权限、锁、连接池或持久化验收。
WAR 包含运行仓储、协调器、生产组合类和 V021/V023；来源 SQL、原包及测试向量仍排除。

[schema 2 工具组合与运行材料](rules/schema-2-runtime-snapshot.md)的定向命令：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2ToolCompositionTest,JdbcSchema2ToolSnapshotTest,Schema2ToolSnapshotSchemaTest,Schema2CompositionGoldenTest,ProductionSchema2StoredProjectionTest,SchemaMigrationsTest' test
```

固定三包向量在 `src/test/resources/schema2-tools/`，由独立作者字节、最终字段/链/摘要组成。
测试覆盖 37 项工具字段、跨模组条目、类型不匹配、候选去重、混合目录序号、真实查询行重建与
各新增表写入失败回滚。有限组合仍受安装资格门约束，不能将向量通过作为基础模组可用证据。

[schema 2 初始语言／工具授予](rules/schema-2-proficiency-grants.md)的无数据库定向命令：

```bash
python3 src/test/resources/schema2-grants/oracle.py
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2GrantCompositionTest,Schema2InitialProficiencyRulesTest,Schema2InitialCharacterPlanServiceTest,Schema2InitialCharacterPlanIntegrationTest,JdbcSchema2CharacterSourceRepositoryTest,JdbcSchema2GrantSnapshotTest,Schema2CharacterGrantSchemaTest' test
```

标准库 oracle 从固定作者 JSON 独立复算原始 ZIP、逻辑、组合、最终字段、完整链及摘要，
默认只做逐字节核对。测试覆盖真实来源/父关系、完整槽身份、跨模组目标、原子授予覆盖、
固定与选择授予、禁用与重复选择、源类型化行和原包不一致、运行行/历史损坏以及事务失败。
完整链路测试通过实际源仓储、转存协调器、运行仓储和默认初始计划服务，核对手写的八条授予事实，
并证明源连接关闭或不可用后只读计划不访问来源、不提交调用方事务、不写业务数据。
改动共用 ledger、快照或迁移清单时，补选上面的原包安装、转存、工具及 schema 测试；
V004/V024 静态与 JDBC 代理证据不替代真实 MySQL 约束、触发器和权限验收。
WAR 包含初始计划服务、类型化仓储和运行 V024；来源 V004、作者包、oracle 及全部测试向量均排除。

[语言分区链路等价核验](rules/language-partition-equivalence.md) 的无数据库定向命令：

```bash
mvn '-Dtest=LanguagePartitionEquivalenceTest,CharacterCatalogChainTest,MySqlIntegrationTestSupportTest' test
```

它从正式作者源经过生产离线安装、源只读适配、运行镜像写入和指定身份读取，在每个边界
对照独立六字段矩阵、类型化投影和受审 canonical 字节向量，避免源与镜像共同遗漏仍让往返
相等通过。覆盖内容/排序变化、同键不同快照、读取损坏和调用方事务回滚；源断开后的镜像
读取只证明本分区不访问源库。事务 JDBC 代理及测试目标保护均不连接真实数据库，不能替代
另行授权的隔离实例中原样迁移、安装、驱动、权限与持久化验收，也不提供完整规则就绪资格。

真实语言/工具目录 JDBC 验收入口为 `tools/rule-packages/verify-language-jdbc.py --execute-disposable`，
须先获得隔离实例、迁移和测试写入授权。它先重验两组无数据库连接保护，再创建 tmpfs MySQL，
原样迁移并等待独立现场结构/权限审计；实际用例为 `LanguagePartitionJdbcIT`，测试跳过不算
成功。默认不带执行参数时只检查迁移清单。前置条件、账号、清理及故障注入边界见
[隔离 JDBC 入口](rules/language-partition-equivalence.md#隔离-jdbc-入口)。

[语言作者分区](rules/language-author-package.md) 的无数据库定向命令：

```bash
mvn '-Dtest=CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest,ModuleCanonicalEncoderV1Test,ModuleCanonicalEncoderV2Test,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

它核对正式作者源的 18 项语言与 37 项工具独立字段矩阵、严格 JSON/UTF-8/整数、Unicode 码点/NFC、资源上限、
准确键与分类集合、排序及独立 canonical 字节向量。只证明这两个分区，不产生完整规则摘要或
发布资格；目录/安装清单与源库事务验收见下面的离线安装边界。WAR 审计须确认纯模型/reader
类存在，作者 JSON、说明/许可包与测试向量均不在 WAR 中。

[离线语言安装](rules/offline-language-installation.md) 的无数据库定向命令（Linux/WSL）：

```bash
mvn '-Dtest=RuleArtifactTest,TicketStoreTest,SourceInstallationTest' test
```

它覆盖完整制品和独立指纹向量、持久票据与未决标记、单连接锁序、各写入点异常及影响行数
错误、实际查询结果被篡改后的整笔回滚，以及提交/回滚/查证失败的分类。JDBC 代理测试不证明
真实 MySQL 权限、触发器、并发屏障、响应丢失或介质断电耐久性；这些按操作说明在另行授权的
隔离实例上验收。安装仍只产生 DRAFT 语言/工具 PARTITION，不提供完整规则就绪或发布资格。
WAR 必须排除 `com/dndtool/offline/` 全部类、安装工具、作者包、安装清单和测试向量。

[源语言分区读取](rules/source-language-reading.md) 的无数据库定向命令：

```bash
mvn '-Dtest=JdbcSourceLanguageRepositoryTest,RuleDatabaseSchemaVerifierTest,CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

它核对同一只读事务中的准确源库/账本、当前 PARTITION 证据、55 项独立字段矩阵、严格 JDBC
类型与 Unicode、有界固定 SELECT，以及失败时资源关闭和连接状态恢复。历史 COMPLETE 不得
替代当前证据；超量或坏行失败关闭，不从旧目录或作者文件补齐。连接池、真实驱动类型、快照
一致性和实际 SELECT-only 权限需另行授权隔离 MySQL 验收，不能用代理测试替代。新适配器
进入 WAR，但不自动装配为生产规则加载入口，也不改变 DRAFT 发布门。

[运行语言快照分区](rules/runtime-language-snapshot.md) 的无数据库定向命令：

```bash
mvn '-Dtest=JdbcRuntimeLanguageSnapshotRepositoryTest,V019RuntimeLanguageSnapshotSchemaTest,SchemaMigrationsTest,DatabaseDiagnosticsTest,V011CompleteCharacterCatalogDraftTest,RuleSchemaMigrationsTest,CampaignArchiveCapabilityBoundaryTest' test
```

它核对 UUIDv4 网络字节、永久身份冲突、跨快照拒绝、18 项语言分区的严格读取、实际 SELECT
回读与调用方事务所有权，并用事务代理验证插入、回读及后续调用方失败后的整笔回滚。V019
的结构和授权文件另有静态合同测试，旧迁移摘要保持不变。代理与静态测试不证明真实 MySQL
的 CHECK、触发器、FK、权限或持久化回滚；这些需在独立授权的隔离实例上完成。只有语言的
PARTITION 不接入新建、导入或执行视图，也不获得完整规则摘要或发布资格。

[工具熟练目录](rules/tool-catalog-partition.md)的组合快照使用 `JdbcRuntimeCharacterCatalogRepository`。
相关命令为 `mvn '-Dtest=CharacterCatalogChainTest,ToolCatalogSchemaTest' test`，核对 55 项双领域
内容、独立 canonical 字节、每个工具写点和后续调用方失败的整笔回滚、来源断开、错误类型及
跨快照拒绝。运行 V020 和来源 V002 的实际约束与授权仍须隔离实例验收。

[离线规则源 schema](rule-source-schema.md) 的无数据库定向命令：

```bash
mvn '-Dtest=RuleSchemaMigrationsTest,RuleSourceSchemaContractTest,RuleDatabaseSchemaVerifierTest' test
```

它检查独立源清单/严格 UTF-8/载荷摘要、表与授权结构防回归，以及完整只读账本比较、准确默认库、
有界查询和 JDBC 资源关闭。SQL 结构测试不能替代真实引擎对 CHECK、触发器、FK、Unicode 和
权限/并发的验证。规则 SQL 必须留在 WAR 外；完整构建同时保护运行 V001—V018 原批准摘要，
并核对 V001—V042 完整运行清单及来源 V001—V012（两链共 54 份迁移源）。
规则源 disposable 验收须先通过测试连接两入口保护的无 DB 回归，再单独授权隔离实例；
不得借临时表 IT 入口对部署源库试写，也不得扩大该 IT 库及账号用途。步骤见该 schema 合同。

[规则发布身份合同](rules/module-release-identity.md) 的纯边界定向命令：

```bash
mvn '-Dtest=BuiltinModuleReleaseRegistryTest,BuiltinModuleHashManifestTest' test
```

这组测试分别验证精确 ASCII 身份与长度、四种解析状态、描述符格式/算法/状态/摘要组合、
默认及重复拒绝、独立批准摘要查询。正整数格式结构不等于支持；expected 查询不等于来源
状态、摘要相等或完整内容验证。Java `int` 测试不证明 JSON 原 token 的类型和词法已被验证。
涉及该边界时继续运行 `ModuleCanonicalEncoderV1Test`、`ModuleCanonicalEncoderV2Test`、
`CampaignArchiveCapabilityBoundaryTest` 及受影响消费者的拒绝回归，再按第 2 节判断是否需要全量验证；
生产代码变化还须按第 6 节审计 WAR。这些纯测试无需启用 MySQL 集成测试。

完整运行链的数量、顺序、脚本名和批准摘要由 `SchemaMigrationsTest` 集中核验；独立来源链由
`RuleSchemaMigrationsTest` 集中核验。领域测试检查自身迁移及其前置合同，不复制其他领域的整链
总数，也不假定自己的迁移永远位于链尾。普通去重仍需保留当前有效行为和独立失败路径的覆盖；
采用新模型时则按新支持范围协调替换测试，不为保留历史断言而强制保留旧代码。

- 当前迁移链中 V001—V018 的文件与账本必须匹配；V011 的角色 DRAFT 目录、V012 的一级创建 schema/profile、
  V013 的升级/生命骰 schema/profile、V014 的职业特性/恢复矩阵、V015 的多职业/ASI/专长框架、
  V016 的初始熟练基线、V017 的 format 2 状态来源及 V018 的多职业施法贡献由清单摘要、精确计数、
  关系、profile 语法和 canonical-v2 负例测试保护。
- 当前 `ImmutableLegacyMigrationContractTest` 固定 V001—V010 的现有清单；采用新的初始化链时须连同清单和验证器一起调整，不把测试类名视为永久产品要求；
  `DeferredPublicSurfaceExclusionTest` 只保护永久延期的公开访问面，不再禁止新增 Host 规则领域。
- 规则测试覆盖未知/重复/未发布数据、稳定键引用、范围、NFC、UTF-8 无符号排序、精确小数、规范字节流与内容摘要。
- 当前支持的模组，其战役冻结值、实际目录摘要、发布摘要和应用清单必须一致，否则失败关闭；这不要求后续版本保留同一模组或读取器。
- 目录、运行模型、canonical/archive 投影和对应测试可协调替换，不要求为旧 RELEASED 或 DRAFT 行为保留兼容层；保留数据库或重建的外部边界见[数据库合同](database.md#迁移合同)。
- archive format 2 的严格往返测试应随各领域渐进建立；只有跨领域发布候选才固定摘要、激活格式、切换默认发布版并运行完整发布门矩阵。

一级创建的定向验证包括 `LevelOneRuleProfileTest`、`LevelOneCharacterRulesTest`、
`LevelOneCharacterCreationServiceTest`、`JdbcLevelOneCharacterCreationRepositoryTest`、
`V012LevelOneCharacterCreationSchemaTest` 和 `LevelOneCharacterRuntimeGrantsTest`。它们证明预览
无权威写入、非法选择被拒绝、事件尾变化导致 stale preview、事务故障不留下部分角色，并且
DRAFT 发布门保持关闭。

[角色语言／工具授予](rules/character-proficiency-grants.md)的无数据库定向命令：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=CharacterProficiencyGrantTest,CharacterProficiencyGrantSchemaTest,LevelOneCharacterRulesTest,LevelOneCharacterCreationServiceTest,JdbcLevelOneCharacterCreationRepositoryTest,JdbcCharacterProficiencyGrantRepositoryTest,CharacterCardServiceTest,JdbcCharacterCardRepositoryTest,HostCharacterCardServletTest,CampaignArchiveV2CharacterStateTest,JdbcCampaignArchiveV2CharacterStateRepositoryTest,JdbcCampaignArchiveV2CharacterStateImportRepositoryTest,JdbcCampaignArchiveImportRepositoryTest,CampaignArchiveCapabilityBoundaryTest' test
```

覆盖固定和选择授予的准确父来源/槽、完整候选检查、幂等与写入失败回滚、角色卡真实读取、
严格内部文件、来源缺失/伪造拒绝及直接恢复。V022 静态前置检查要求旧 DRAFT 创建快照为空；
这些测试不执行迁移，不证明真实 MySQL 触发器或权限，也不将该旧目录链路视作 schema 2 业务运行。
WAR 须包含授予模型、消费者和 V022；账号增量见[授予权限](../database/grants/character-proficiency-grants.sql)。

升级的定向验证包括 `AdvancementValueProfileTest`、`LevelAdvancementRulesTest`、
`LevelAdvancementServiceTest`、`JdbcLevelAdvancementRepositoryTest`、
`HostLevelAdvancementServletTest` 和 `V013LevelAdvancementSchemaTest`。它们覆盖逐级 profile
畸形、跳级、资源状态不一致、固定值/服务端掷骰、stale row version、事务失败和禁止部分写入，
并证明过期状态或幂等重放不会消耗随机性。

成长与恢复区间的共用模型使用 `AdvancementValueProfileTest`、`ResourceRecoveryProfileTest`、
`ModuleCanonicalEncoderV2Test`、`LevelAdvancementRulesTest` 和 `ClassResourceRecoveryRulesTest`
验证：结构化区间与文本共用校验、集合不可变、逐个等级破坏熟练加值、恢复方式组合、未来区间
错误及资源已满/不可用/无限时仍拒绝坏规则；恢复组件另验证权威魅力值、最低一次公式及容量
不匹配拒绝。涉及这些纯模型时保留 canonical v1 独立向量，
并按真实调用闭包选择一级创建和升级 Service 测试；不以纯规则测试替代领域存储或 MySQL 验收。

职业特性的定向验证包括 `ClassFeatureRulesTest`、`ClassFeatureAdjudicationRulesTest`、
`ClassResourceRecoveryRulesTest`、`V014ClassFeatureCoverageMatrixTest`、
`V014ClassFeatureLifecycleSchemaTest`、`CharacterAdvancementChoiceRulesTest` 和
`V015MulticlassAsiFeatSchemaTest`、`V016StartingProficiencyBaselineSchemaTest` 与
`V017CharacterArchiveV2OriginSchemaTest`。V018 的职业贡献、共享施法者等级、半施法职业合并取整、
Pact Magic 隔离、1—9 环上限及畸形目录由 `V018MulticlassSpellSlotFoundationSchemaTest` 与
`MulticlassSpellSlotRulesTest` 覆盖。format 2 的角色投影另由
`CampaignArchiveV2CharacterStateTest` 和 `JdbcCampaignArchiveV2CharacterStateImportRepositoryTest`
覆盖规范写出/严格读取、稳定事件闭包、预览、调用方事务、失败回滚及无 commit/rollback 的
Repository 合同。参数化矩阵逐职业覆盖全部 236 个职业/子职业特性；规则
测试验证自动、裁决、阻断三态、子职业选择边界、动态恢复等级、阻断法术资源和畸形冻结目录。

辅助验证 SQL 只读，位于 [database/verify](../database/verify/)；静态测试不会把这些文件的存在当作数据库已验收证据。

### 4.1 完整 SRD 5.1 目标的测试矩阵

新增规则领域除通用边界外，还必须覆盖与其他领域的组合行为：

- 角色构筑、升级、多职业、专长和派生值重算；
- 通用骰子、优势/劣势抵消、攻击、暴击、伤害分量、抗性、易伤和免疫；
- 先攻、轮次、回合、动作经济、反应窗口、移动、距离、范围和掩护；
- 0 HP、昏迷、稳定、死亡豁免、治疗、临时生命值和即死；
- 状态、持续时间、重复豁免、专注、叠加、免疫、到期和解除；
- 已知/准备法术、法术位、多职业施法、目标、区域、升环、资源消耗和开放式 DM 裁定；
- 武器、护甲、负重、货币、消耗品、充能、同调和休息恢复；
- 怪物动作、反应、恢复能力、施法、抗性/免疫、CR/XP 和遭遇关系；
- 存档格式演进、旧 v1 识别、完整状态往返和失败时禁止部分导入。

随机规则使用可注入且可审计的随机源测试边界和候选选择，但生产结果仍由服务端安全随机源
生成。时间、回合和触发测试必须固定明确时钟或顺序，不能依赖测试执行速度。任何规则领域
只有在目录、服务、仓储、事务、Web 边界和存档测试共同完成后，才可标为已实现。

## Schema 2 检定、装备与健康作者来源验证

[作者来源合同](rules/schema-2-foundation-authoring.md)的无数据库定向命令：

```bash
mvn '-Dtest=Schema2CheckCompositionTest,Schema2EquipmentHealthCompositionTest,JdbcSchema2CheckSourceRepositoryTest,JdbcSchema2EquipmentHealthSourceTest,Schema2CheckSourceSchemaTest,Schema2EquipmentHealthSchemaTest,Schema2SourceLedgerContractTest,RuleSchemaMigrationsTest,RuleSourceSchemaContractTest,ImmutableLegacyMigrationContractTest' test
```

组合测试覆盖四类封闭定义、算法/页码/引用类别、完整来源链、启停、有符号修正和严格清空，
包括负整数原词法及 `-0`/小数/指数/强制转换拒绝、合法最终值掩盖非法原始声明的反例。
来源测试从真实作者 ZIP 核对 typed 行，包含未选中包、完整引用与操作、缺失/多余/损坏数据和
各写点失败；仓储保留调用方事务所有权。结构测试保护来源 V006/V007、最小授权和独立批准链。
这些是作者、组合与来源证据，不包含新运行镜像、检定/装备/健康业务、骰子或角色历史验收。
运行镜像与事实存储使用后面的[持久化验证](#schema-2-检定装备与健康镜像和事实存储验证)，
真实命令与恢复语义使用[角色基础验证](#schema-2-检定装备与健康验证)；
已实现的成长/休息验证继续使用下节入口。

WAR 包含严格作者/组合和真实来源读取类；来源 SQL、离线安装器、作者包及测试向量排除。
来源 V001—V012 的 53 张表、159 个触发器及 43 张类型化材料表须在另行授权的隔离 MySQL
实例验收；代理和静态结构测试不能替代真实 CHECK、FK、权限、锁或持久化事实。

## Schema 2 角色成长、资源与休息验证

[内部成长合同](rules/schema-2-character-statistics.md)和[标准休息](rules/schema-2-character-rest.md)的数据库无关定向命令：

```bash
mvn '-Dtest=Schema2ProgressionCompositionTest,Schema2CharacterProgressionRulesTest,Schema2GrowthRulesTest,Schema2CharacterRuntimeServiceTest,Schema2CharacterAdvancementAndRestServiceTest,JdbcSchema2AdvancementSourceRepositoryTest,JdbcSchema2GrowthSourceRepositoryTest,Schema2AdvancementSourceSchemaTest,JdbcSchema2ProgressionSnapshotTest,JdbcSchema2CharacterRuntimeRepositoryTest,JdbcSchema2CompleteGrowthStateTest,JdbcSchema2RestStateTest,Schema2CharacterRuntimeSchemaTest' test
```

独立固定规则字段及完整覆盖链在 `src/test/resources/schema2-progression/`。共享组合器或
来源读取变化还覆盖原语言/工具/授予向量、stored projection 和来源账本测试。迁移清单
变化沿用下节 schema 与诊断测试。服务测试使用实际来源、计划、快照和状态仓储的 JDBC
代理，覆盖失效前拒绝、随机性顺序、幂等、写入失败回滚、源库断开和直接恢复，不替代真实
MySQL 外键、触发器、权限或部署证据。成长/休息集成测试还覆盖子职业、ASI、专长、多职业、
逐次生命骰、活动中断、长休收益间隔及完整历史恢复；不声称通用效果或多角色生命周期已完成。
WAR 必须包含成长/休息服务、规则、类型化仓储及运行 V027；来源 V005、测试 oracle 和向量排除。

## Schema 2 检定、装备与健康镜像和事实存储验证

[运行快照](rules/schema-2-runtime-snapshot.md)和[角色事实存储](rules/schema-2-character-fact-storage.md)的数据库无关定向命令：

```bash
mvn '-Dtest=JdbcSchema2CheckSnapshotTest,JdbcSchema2EquipmentHealthSnapshotTest,JdbcSchema2CheckHistoryTest,JdbcSchema2EquipmentHealthHistoryTest,Schema2CheckHistorySchemaTest,Schema2EquipmentHealthHistorySchemaTest,Schema2CheckSourceSchemaTest,Schema2EquipmentHealthSchemaTest,SchemaMigrationsTest,JdbcSchema2CharacterRuntimeRepositoryTest,JdbcSchema2RestStateTest' test
```

快照测试按独立字段和来源期望核对四类完整规则、混合序号、字段链、负修正/清空、实际读回、
缺失/多余/坏类型、跨快照以及各写点故障。历史测试使用真实叶仓储及事务 JDBC 代理，
核对封闭输入/结果、有效骰模式、完整装备健康前后状态、有序原骰、追加读回、直接恢复、
孤儿与类型化行缺失、结构/邻接/预算限制及整笔回滚；这些测试保存已经准备的事实，不执行新业务。

运行链为 V001—V042，来源为 V001—V012，共 54 份迁移源。WAR 须包含镜像/事实仓储及
全部运行迁移，排除来源 SQL、离线写入工具、测试向量和真实存档。Check/Equipment/Health
消费者、请求幂等和 RuntimeService 命令使用下节验证；仓储测试本身只作为存储事实的证据。
现有成长/休息测试保护新增字段的中性保留，DRAFT、Host、archive 2 和安装门保持。
静态 SQL/JDBC 代理不代替真实 MySQL 的 CHECK、生成列、外键、触发器、权限与持久化验收。

## Schema 2 检定、装备与健康验证

[角色基础合同](rules/schema-2-character-foundations.md)使用真实准备、来源、快照、角色事件和直接恢复。
新增作者类型或读写映射时，覆盖 `Schema2CheckCompositionTest`、`Schema2EquipmentHealthCompositionTest`、
对应 `JdbcSchema2Check*` / `JdbcSchema2EquipmentHealth*` 来源、快照、历史和 schema 测试；
共享组合器改动同时覆盖原语言/工具/授予/成长向量及 `ProductionSchema2StoredProjectionTest`。
独立向量位于 `src/test/resources/schema2-checks/` 和 `schema2-equipment-health/`。

消费者变化的定向命令：

```bash
mvn '-Dtest=Schema2CharacterCheckRulesTest,Schema2CharacterCheckServiceTest,Schema2CharacterEquipmentRulesTest,Schema2CharacterHealthRulesTest,Schema2CharacterEquipmentHealthServiceTest,Schema2CharacterRuntimeServiceTest,Schema2CharacterAdvancementAndRestServiceTest,Schema2CharacterProgressionRulesTest,Schema2GrowthRulesTest' test
```

覆盖真实授予去重、装备产生的实际骰模式、手位/AC、穿脱时间、环境效果原骰、临时 HP、零 HP/即死、
死亡豁免、恰抵达稳定恢复期限、长休清临时 HP、跨命令状态保持，以及缺失/损坏子表、幂等、随机性前拒绝、
故障整体回滚与直接恢复不重掷。未知运行时故障遵循共同守卫冻结，不能把回滚视作允许盲目重试。
WAR 包含基础消费者与运行 V028—V031；来源 SQL、测试向量和离线写入工具不得入包。
这些测试不授予真实 MySQL、部署、Host、战斗回合或完整初始化验收。

## 共同业务事务与初始化准备验证

[共同事务合同](rules/business-write-transactions.md)改变共享接纳边界时，应覆盖清单中的全部
事务 owner 及调用方叶仓储测试，并保留现有版本、幂等、随机性和失败回滚断言。对永久守卫、
连接所有权、材料准备和未决查证的数据库无关定向命令为：

```bash
mvn '-Dtest=BusinessWriteTransactionsTest,BusinessWriteGuardSchemaTest,Schema2SnapshotTransferServiceTest,Schema2InitializationPreparationServiceTest,Schema2InitialPlanSchemaTest,Schema2InitialCharacterPlanServiceTest,Schema2InitialCharacterPlanIntegrationTest' test
python3 -B src/test/resources/schema2-grants/expected/initial-plan-fingerprint.py
```

故障测试通过独立子 JVM 验证永久失效门，不提供重置生产阻断状态的测试后门。覆盖第一条 SQL
锁序、并发互斥、裸连接拒绝、活动 legacy 和材料残留阻断、commit/rollback/连接归还故障、
明确提交与 UNKNOWN 的区别，以及隔离后绑定原操作的只读查证。准备测试从真实来源重读并
对照固定计划期望，覆盖同键不同计划、孤立/损坏事实和每个写点失败后无部分快照、计划及收据。
完整初始化缺少领域、业务历史、容量及生命周期时必须继续拒绝。
计划指纹 oracle 仅使用标准库、既有独立规则摘要及手写的来源、槽、选择和授予事实；
不调用生产计划器或指纹实现，其固定摘要由准备测试直接断言。

迁移清单变化选择两条链的中央清单测试及相关 schema 诊断测试；涉及工具分区或角色目录合同
时，再加入 `ToolCatalogSchemaTest` 或 `V011CompleteCharacterCatalogDraftTest`。
PARTITION 调用方采用共同 Session 后，还应覆盖语言、工具镜像及链路测试。
`python3 -B tools/rule-packages/tests/verify-language-jdbc-test.py` 使用明确有界的代表材料测试
隔离验收脚本的原样 SQL、身份、默认数据库和授权编排，并独立验证未审查迁移链的入口拒绝；
它不启动 MySQL，也不替代另行授权的真实数据库验收。代表材料不是完整可执行迁移链，测试
通过不表示固定验收 profile 已接受当前全部迁移；profile 和结构／权限审计范围须一并复核。
候选 WAR 必须包含共同 Session、初始准备服务/仓储及 V025/V026，排除测试工具和来源 SQL。

## 5. Web 与安全测试

覆盖精确 loopback/Host/scheme/port、伪造 `X-Forwarded-*`、方法限制、Session Cookie、CSRF、Origin/Referer、Fetch Metadata、安全响应头、epoch、对象版本、幂等键和摘要。错误入口应返回一致的普通错误且不能到达业务写入。

客户端权威边界测试应确认浏览器不能指定骰点、选中骰、派生修正、最终结果、效果算法、
数据库身份或版本推进。相应领域实现后，还必须确认浏览器不能指定服务器候选集合之外的
职业/专长/法术/目标，不能覆盖先攻顺序、伤害调整、资源消耗、状态持续时间或 DM 裁定类型。

## 6. WAR 审计

生产代码或打包资源变化后，记录候选 WAR：

- 字节数、SHA-256 与 ZIP 条目数；
- 必需的新类、JSP、JavaScript、`web.xml` 与仍保留的 V001—V042 资源；
- SQLite 结构声明、领域引擎/严格 codec/仓储、SQLite JDBC、Host 资源，以及 `storage/rules/` 下四个准确内置 ZIP；
- 私钥/证书秘密、凭据、Tomcat 外部配置、日志、备份、真实存档、结果捕获与本地路径文件名的匹配数；
- 生产资源和示例配置差异中的秘密扫描结果。

检查 `WEB-INF/classes/com/dndtool/service/` 下的 `Schema2CharacterRuntime`、
`Schema2CombatHistory` 和 `Schema2PreparationIdentity`。核对迁出的 JDBC 嵌套领域类型
均已排除，例如 `JdbcSchema2CharacterRuntimeRepository$State.class`、
`JdbcSchema2CombatRuntimeRepository$History.class` 和
`JdbcSchema2RuntimeSnapshotRepository$Identity.class`。

本文前述原包排除项针对离线来源包、测试向量和作者工作目录；
`storage/rules/{srd51-runtime,srd51-characters,srd51-magic,dm-runtime-house-rules}.zip`
是当前显式交付的有限生产资源，必须包含且逐个验证生产 reader、许可和准确内容。
`rule-packages/` 作者源、外部导入原包及离线安装写入工具仍不得进入 WAR。

WAR 审计通过不代表已经部署。

### SQLite 结构、完整文件与运行验收

当前有限 SQLite 模型见[运行合同](storage-vitality-loop.md)。结构与真实 SQLite 的定向入口是
`StorageSchemaCatalogTest`、`SqliteSchemaTest`；业务/生命周期见 `CampaignStorageServiceTest`、
`CampaignStorageLifecycleTest`。`BundledCampaignRulesTest` 核验真实四包、精确数字、可执行初始化
与基础/施法预检；`CampaignDomainEngineTest` 验证无连接纯领域后果；
`CampaignCheckEffectsTest` 验证两分支全部预检、类型化有序后果和共享事务；
`CampaignManualIntegrationTest` 覆盖自由卡字段/职业/熟练、MANUAL/SAVING_THROW、有序后果、
混合简易遭遇、共同回滚，以及完整文件中初始/当前事实的分别保留；
`CampaignDomainRootValidationTest` 拒绝根历史重组、未来节点引用、物品来源/归属伪造及
中间时钟分歧；`CampaignDomainRecoveryTest` 覆盖初始化已提交、业务提交响应未知时的
显式恢复，以及对保留导入输入的完整核验，断言不重初始化或重播；
`CampaignDomainsIntegrationTest`、`CampaignDomainsCombatIntegrationTest` 验证共享 SQLite 的
完整聚合、原骰/历史/未决动作与文件重开。独立基础文件字节向量和严格读取见
`CampaignFileVectorTest`、`CampaignCodecTest`。`CampaignStorageCrashTest` 在独立 JVM 中于 DDL、初始化提交、
业务更新/提交和 P2 后强制中止，再由新服务实例查证恢复。真实 loopback HTTP、模块选择、
治疗、保存结束、文件重开和重启恢复见 `StorageHttpIntegrationTest`。
测试只写临时目录，不连接 MySQL；HTTP 测试独占本机 8080，不能与人工验证实例同时运行。
`CampaignPreparationTest` 保护准备目录归属、部分输入/分配、完整身份不符、未知材料和
恢复再次中断；`CampaignStorageCrashTest` 还在独立进程中覆盖创建目录、锁、零字节暂存、
半写、硬链接发布和正式运行发布等前缀。`CampaignWindowsFilesystemTest` 只在原生
Windows JVM 执行，核验 NTFS ACL、junction 拒绝及跨进程运行锁；Linux 跳过此类不构成
Windows 通过。`CampaignArcaneRecoveryIntegrationTest` 覆盖短休资格、真实位数与预算、
每日限制、法术书、幂等、晚写回滚和保存/END/重开。
`StorageApplicationListenerTest`、`StorageReadinessTest` 验证配置优先级、显式空根失败关闭、
实际规则/文件/运行就绪、需恢复和损坏阻断。

系统临时目录也必须在 Git/WAR 树外；若环境在临时根放置 `.git` 或 `WEB-INF` 标记，
应用会拒绝其下的运行。此时为 Maven 和测试 JVM 同时指定合格的
`-Djava.io.tmpdir=/absolute/test-temp` 与
`'-DargLine=-Djava.io.tmpdir=/absolute/test-temp'`（目录须已存在），保留目录保护，
不删除未知标记或改动真实仓库。已有 `argLine` 选项须合并保留。

```bash
python3 rule-packages/srd51-runtime/build-bundles.py --check
mvn '-Dtest=BundledCampaignRulesTest,CampaignDomainEngineTest,CampaignCheckEffectsTest,CampaignManualIntegrationTest,CampaignDomainRootValidationTest,CampaignDomainRecoveryTest,CampaignDomainsIntegrationTest,CampaignDomainsCombatIntegrationTest,CampaignStorageLifecycleTest,StorageSchemaCatalogTest,SqliteSchemaTest,CampaignStorageServiceTest,CampaignCodecTest,CampaignFileVectorTest,CampaignStorageCrashTest,StorageHttpIntegrationTest,StorageRequestDigestTest,HostBoundaryFilterTest,HostRequestSecurityFilterTest,HostHomeServletTest,DatabaseStartupListenerTest,ProtectedHostCommandEntryTest' test
```

人工执行同一入口（Linux/WSL、Java 21+、Maven，8080 必须空闲）：

```bash
storage_test_root="$(mktemp -d /var/tmp/dnd-storage-XXXXXX)"
bash tools/storage/run-local.sh "$storage_test_root"
```

脚本依次显示环境检查、编译与依赖准备、应用启动。首次编译可能需要几分钟，Maven 阶段
和原始错误保持可见；出现应用就绪地址后再打开浏览器。启动预备不等于运行测试。

打开 `http://127.0.0.1:8080/host/storage`。基础验收可创建 `4/12` 角色、治疗 `5`，
预期 `9/12`，保存结束再开后状态与历史相同；只选基础模块时治疗不可用。
runtime 验收选择全部三个应用模块，在时间零点创建规则卡 PC/NPC 队伍，执行装备/检定/
健康及所选施法/战斗动作；创建独立自由卡，编辑基础字段、职业等级/熟练、物品，执行
MANUAL/SAVING_THROW 和两分支有序后果，管理混合名单/阵营、队伍/实体位置及简易遭遇。
时间零点创建限制只适用于规则卡。分别在普通提交、反应等待与效果存续时保存，结束并重开，
核对初始/当前卡、原骰/资源、根/子历史和唯一 pending。验证下载后上传不会自动激活，
同 fileKey 不覆盖，受引用保存点不能删除；重启必须显式恢复，坏文件/投影不得部分激活。
Ctrl+C 仅停止这个隔离验证实例；未 END 的运行下次启动必须显式恢复。先 END，再停止
进程，保管需要的 `saves/*.json`，最后只删除本次明确生成的测试根。脚本不部署 WAR，
不修改现有 Tomcat；它用测试 classpath 装配同一生产 Servlet/Filter。

可选浏览器自动验收使用独立安装的 Playwright/Chromium，浏览器环境需有中文字库；
不向 Maven 或 WAR 加浏览器依赖。
聚焦短冒险体验时，先以上述启动器启用全新临时根，再执行：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-adventure-smoke.cjs /absolute/empty/adventure-evidence \
  --fresh-root /absolute/private/test-root
```

`--fresh-root` 必须是本次启动器实际使用的可丢弃空白根，不能指向真实战役。脚本通过真实
表单建立 PC/NPC、取得并装备武器、创建相邻地点、检定及分支后果、处理随机先攻和借机
反应、攻击、保存结束，关闭浏览器会话后精确打开 FINAL，再继续一个回合。核对完整初始/
当前事实、原骰、历史与旧文件字节；INITIAL、手动保存、最终保存均按返回的准确身份保留。
同时断言每次提交的忙碌反馈、普通成功操作不重复读取整份状态、epoch 改变后刷新 Session，
并输出动作耗时、宽屏/390px 截图和 `adventure-browser-report.json`。它只关闭浏览器会话，
应用进程重启及残留恢复使用下方独立恢复验收；两种关闭不能混称。

较广的领域覆盖使用主浏览器验收：
先用 `tools/storage/run-local.sh` 启动**另一个全新一次性根**，再在另一终端运行：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-smoke.cjs /absolute/empty/screenshots
```

默认使用与当前操作系统匹配的 Playwright Chromium。要使用已安装的 Chromium/Edge，
可将 `DND_BROWSER_EXECUTABLE` 设为该系统下浏览器可执行文件的绝对路径；脚本先验证
文件存在。Windows 原生 Node 必须使用 Windows 浏览器，不能指定 WSL Linux executable。

脚本拒绝已有运行、残留或保存点的根。实际表单验收场景包括基础 `4/12 → 9/12`、
保存结束重开与只选基础时关闭治疗；runtime 规则卡 PC/NPC、地点/位置/物品/消息、
服务器检定及成功/失败分支的 HP/物品/位置/消息后果；自由卡字段/职业等级/豁免熟练、
MANUAL 和优势豁免、有序后果、物品归档/恢复、混合简易遭遇及队伍/实体位置；规则战斗
回合、完整文件重开后两种卡/物品/历史/战斗一致，以及下载、删除后保留原 fileKey 导入。
脚本输出宽屏/390px 截图、横向溢出和 JavaScript/CSP 检查结果 `browser-report.json`；
成功时所有验证运行均已结束。奥术回想场景用真实法术书、资源与休息表单选择恢复，验证
未满短休、超预算、同日重复拒绝且无状态/历史变化，空选择可放弃，合法收益和使用日显示
正确，保存、END 与重开后全部根/子历史及位数保持一致。
失败保留报告与测试根供检查，不递归删除未知文件。浏览器构建必须适合所用操作系统。

在同一隔离应用上继续执行装备、战斗继续与生命法术验收：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-combat-smoke.cjs /absolute/empty/combat-screenshots
```

补充脚本只接受主验收留下的已结束夹具，另建规则卡战役；通过真实表单取得/装备武器、
触发借机攻击窗口并 PASS/继续、攻击、使用 Life Cleric 的 Cure Wounds。核对服务器原骰、
HP 和法术位共同变化，以及 FINAL 重开后事实不变。随机先攻和命中按实际结果处理，
不通过注入固定骰子绕过生产路径。报告、截图和完整示例存档属于测试输出，不进入 WAR。

WAR 另须包含 `/storage/campaign-schema.json`、四个准确规则 ZIP、严格聚合/领域引擎、
结构/运行服务、SQLite JDBC、Host 入口和对应静态资源；不得包含测试 launcher、真实
db/journal、保存文件或本地根。浏览器、真实 SQLite/HTTP、纯规则及故障测试分别承担
其实际场景责任，不能互相代替。自由卡、MANUAL/SAVING_THROW、有序检定效果和简易
遭遇由上述当前领域测试及浏览器路径共同验收，同时核对其与 schema-2 规则卡的隔离。
周期 AUTO/轮换是容量测量后的后续能力，当前测试按每命令耐久与显式完整保存验收。
下列是[全域实施路线](design/v2/storage-data-map-and-rollout.md)的当前完整验收责任，
各候选以实际执行结果判断，不从测试类或本文列表推定通过、完整 SRD 发布或平台断电耐久。

- 纯计划：独立字段/结构期望覆盖 JSON 严格解析、类型/默认值/键/约束、模块和外键依赖、
  完整未来写入足迹、确定性顺序及计划摘要。拒绝材料不得分配目录、执行 DDL 或消费 RNG。
- 引擎：使用最终选定 SQLite JDBC 在测试专有临时目录创建真实库，验证 DDL、索引、seed、
  规则、业务、历史和收据同事务；测试外键、STRICT、精确数值/字节/Unicode、事务所有权
  及锁争用。不得连接真实运行目录，也不用 MySQL 成功或代理 JDBC 代替引擎证据。
- 同版恢复：分别覆盖完整回滚空库、已提交初始化、首份检查点未完成、未知结果、错误
  模型/计划与部分结构；断言无补表、重 seed、重掷或重复授予。独占锁与一个业务/根/接受
  事务、未知 commit/rollback 的阻断及显式新 epoch、P0—P3、END、封存/墓碑和无来源
  MySQL 的运行按[生命周期](design/v2/runtime-and-transactions.md)作真实中断验证；不要求独立 C0。
- 领域接合：每个现行 owner 覆盖正常/边界/坏冻结数据/版本/幂等/共同回滚，以及
  完整文件字段和直接恢复的独立期望。不能仅对编码器与读取器的自洽往返宣称数据无遗漏。
- 打包与模式：当前必需项包括应用结构 JSON、编译/验证类、领域消费者、四个受控内置
  原包和 SQLite JDBC；默认 MySQL 及旧资源仍保留，只有明确退役后才移除旧适配。拒绝其他作者原包、来源维护 SQL、
  离线写入类、真实 DB/journal/档案、配置秘密和本地材料进入 WAR；审计字节数、SHA-256、
  条目数、必需/禁止文件及资源内容。

默认按改动及共享依赖运行定向测试。完整 Host 切换改变全部持久化和事务路径，是需要
完整回归的检查点；真实文件系统耐久/进程中断验收独立执行，不能以单元测试通过替代。
现行 MySQL 数据库验收仍按第 3、4 节及其独立授权边界执行。

### 原生 Windows 外部 Tomcat 候选验收

先完成当前候选的相关测试和 WAR 审计。使用已安装的 Tomcat 10.1、Java 21+，指定全新
一次性 `CATALINA_BASE` 和独立空白 NTFS 存储根，两者均在 Git、WAR、现有 Tomcat 与
真实数据之外。以下变量由操作者填入准确绝对路径，不使用真实根作为夹具：

浏览器用例使用原生 Windows Node.js 和 Playwright。默认使用 Playwright 配套浏览器；
也可将 `DND_BROWSER_EXECUTABLE` 设为已安装浏览器的准确绝对路径，并记录实际浏览器版本。
该变量只选择测试浏览器，不改变应用配置。

```powershell
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Prepare `
  -TomcatHome $tomcatHome -JavaHome $javaHome -War $candidateWar `
  -CatalinaBase $candidateBase -StorageRoot $testStorage
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Start -CatalinaBase $candidateBase
node tools/storage/verify-war-http.cjs IDLE
node tools/storage/browser-smoke.cjs $screenshots
node tools/storage/browser-combat-smoke.cjs $combatScreenshots
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Stop -CatalinaBase $candidateBase
```

脚本使用原生外部 Tomcat Bootstrap、真实 `ROOT.war` 和正常 context 扫描；不手动注册
Servlet/Listener，也不读取或修改已安装实例的 setenv、ROOT、应用或数据。它复制容器
公共配置到独立 base，生成仅当前账号可访问的配置/存储目录，固定 loopback 8080/8005；
端口占用时拒绝，不终止其他服务或改端口。Prepare 检查空目录和路径隔离；Start/Status/Stop
绑定准确 base、PID、进程创建时间及 executable，不以模糊进程名控制服务。

Start 返回的 `STARTED` 只表示进程已启动，必须另查 `httpStatus=200` 且
`readiness.ready=true`。HTTP 脚本验证 welcome JSP、注解发现、web.xml Filter/Session、
静态资源、真实 SQLite 就绪、CSRF/epoch/摘要及 Host/Origin/方法边界和旧路由关闭；只发送
必定拒绝的写请求。浏览器脚本在一次性夹具上执行被授权的实际表单业务。失败保留进程身份、
日志与材料，用同一 base 的 Status/Stop 处置；不递归清理未知内容。

主浏览器、战斗和恢复用例为 HTTP 响应与配对的状态刷新保留 120 秒等待，页面控件仍使用
30 秒等待。报告分别记录 POST、状态刷新和整步耗时、请求身份及失败阶段；不自动重试
可能已接受的命令。这些是验收工具的等待上限，不是应用响应时间承诺。

若主浏览器在奥术回想重开阶段已留下准确的未结束运行，可在正常停止并重新启动同一
隔离实例后接续。服务必须明确返回 `RESIDUAL`，且原 `input.json`、`allocation.json` 和
保存文件全部保留；使用新的空证据目录：

```powershell
node tools/storage/browser-smoke.cjs $resumeEvidence --resume-arcane $runId $retainedInputFile
```

该模式只接受原主用例的准确奥术回想夹具，先核对运行身份、分配摘要、原保存载荷及
预期历史/法术位。残留状态按合同隐藏保存列表，显式恢复后才核对 API 文件身份。
接续会比较新保存的全部载荷（仅排除文件自身身份与完整性字段）、END/重开后的完整
领域与历史，并完成基础模块尾段；原保存文件身份和字节必须保持一致。此前通过的主流程
结果与新接续报告分别保留，再继续战斗用例。其他中断位置须先定位，不能借此模式跳过
失败断言、覆盖报告或删除未知运行材料。


正常停机与强制终止后分别重启同一 base/root，核对 `RESIDUAL` 的就绪 `503`、显式恢复后
原事实/历史不变；可用 `node tools/storage/verify-war-http.cjs RESIDUAL` 检查恢复前边界。
`browser-recovery-smoke.cjs prepare $evidence` 用真实表单建立 `4/12 → 9/12` 并保存准确
run/epoch、事实/历史及所有保存文件身份/字节摘要；外部停止再启动后，用
`browser-recovery-smoke.cjs recover $evidence` 核对显式恢复、新 epoch、旧 epoch 拒绝及
END。正常关闭与强制终止各使用新的空证据目录，进程操作仅针对候选脚本记录的准确 PID。
精确放弃使用 `prepare-discard` / `discard` 两段，准备后保留一次尚未生成完整保存点的治疗，重启后
经准确 runId 确认放弃，核对已存文件完全不变。脚本只接受已知隔离夹具，拒绝证据覆盖，
失败保留原现场；它不控制或终止进程。

原生文件测试需同时覆盖
第二实例争锁、退出后释放、准备前缀、发布冲突和未知材料；没有取得 Windows symlink
创建权限的用例单独说明，不以跳过替代 junction/reparse 验收。离线 POSIX 工具测试仍在
Linux/WSL 执行，不要求迁移到 Windows。

记录实际 OS/JDK/Tomcat/NTFS、候选身份、用例结果和跳过原因。进程中断结果不证明物理
断电时目录项持久；Windows 边界按[配置合同](configuration.md#sqlite-当前应用存储配置)。
隔离验收成功仍不代表正式入口已切换；正式部署、备份和回滚只按[部署指南](deployment.md)。

### 玩家参与原型验证

本原型只使用一次性私有 SQLite 根；不连接或重置真实 MySQL，不部署现有 Tomcat。
身份/同名申请/审批轮换/单写接管/暂停/撤权、玩家裁剪与当前叶子候选、完整参与历史和
类型化裁定、严格私有控制及可信 HTTPS 可选用以下定向集合：

```bash
mvn '-Dtest=CampaignParticipationIntegrationTest,PlayerGatewayServiceTest,PlayerParticipationAcceptanceTest,PlayerProjectionAndCandidatesTest,PlayerBoundaryFilterTest,PlayerHttpIntegrationTest' test
```

修改领域根或结构时，另外选择受影响的现有领域、codec、存储恢复及 schema 测试；不因
有玩家页面而默认全量；正式候选仍须满足[部署完整回归门](deployment.md#1-构建候选)。
`PlayerHttpIntegrationTest` 创建一次性带 loopback IP SAN 的证书，
测试客户端仅信任该证书，并验证默认客户端拒绝它；需要 JDK `keytool` 及空闲 8080/8443。
已通过集合不为收尾重复执行；打包复用本次有效证据并审计 WAR。
`PlayerParticipationAcceptanceTest` 使用可控时钟和独立 SQLite，补查邀请轮换及准确到期、
END 后新运行的身份/收据隔离、后来成员的共享范围、控制转交与排队命令的串行边界，
以及真实反应窗口暂停时不代选、不追加业务事实。

独立进程恢复使用 `mvn '-Dtest=PlayerProcessRecoveryIntegrationTest' test`。它在两个
独立 JVM 间重启同一私有根，分别只信任对应临时证书，核对 RESIDUAL、Host 显式恢复与
重新开放、原设备换取新会话和原收据只读查证；不得自动恢复写连接或重放命令。
该用例同样占用 8080/8443，须与真实 HTTP 测试、浏览器启动器串行运行。
若默认临时目录位于 Git/WAR 标记之下，先创建另一个私有测试目录，通过
`-DargLine="-Djava.io.tmpdir=/absolute/private/test-temp"` 指定测试 JVM 的临时根；
子进程继承其隔离范围，不删除标记或放宽存储路径保护。

仅检查启动准备时运行 `mvn '-Dtest=PlayerLoopLauncherReadinessTest' test`。该集合不启动
HTTP/TLS 监听、证书进程或浏览器，检查存储路径、必要资源、服务可用性及失败清理。
临时根仍须满足前文的 Git/WAR 隔离与私有权限要求，不能因测试环境的合成标记而放宽保护。

可操作原型通过下面入口启动。脚本先编译测试启动器并准备 classpath，不执行测试。
它只绑定 loopback，自动产生临时测试证书，退出时清理
测试容器和证书；业务根保留，须在页面 END 或下次显式恢复。
启动器先检查路径和必要资源，确认容器及存储服务可用后才输出地址；初始化失败不报告
启动成功，并尝试清理本次容器与临时证书，不删除传入的业务根。已有残留运行仍可进入
Host 恢复页面，启动检查本身不恢复运行或赋予玩家写权限。

```bash
bash tools/storage/run-player-local.sh /absolute/disposable/player-store
```

DM 为 `http://127.0.0.1:8080/host/storage`，玩家为 `https://127.0.0.1:8443/player`。
人工浏览器必须正确信任测试证书，不忽略 TLS 警告。真实 LAN 启用只按[部署指南](deployment.md#可选玩家-https-入口)。
独立 Playwright 安装的浏览器验收在另一个终端执行，`--fresh-root` 必须准确指向本次
启动器使用的空白可丢弃根，证据目录须为空并与该根分离：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-player-smoke.cjs /absolute/empty/player-evidence \
  --fresh-root /absolute/disposable/player-store
```

浏览器默认使用标准证书信任。隔离自动测试可将 `DND_PLAYER_TEST_SPKI` 设置为本次启动器
输出的准确测试证书 SPKI，只对该测试公钥例外；不得借此跳过真实设备 CA 安装。
QR 像素核验还需独立 Python 环境中的
`Pillow` 和 `zxing-cpp`；用 `DND_QR_DECODER_PYTHON` 指定解释器，或以 `DND_QR_DECODER_PATH`
指定包含这两个包的临时目录。解码实际页面截图并比对本次邀请链接，不向报告输出邀请值。
脚本不使用通用 `ignoreHTTPSErrors`；此证据只覆盖隔离原型交互。
其单次旅程覆盖申请/批准、本人卡、合法候选、响应丢失后只读查证、明确裁定、共享撤回、
暂停、双标签接管及结束。截图/证书/日志和临时库不入 Git/WAR。
若前段已成功而邀请阶段中止，可用 `--resume-after-roster` 续跑；脚本先核对固定角色、
装备、版本和空参与状态，任何检查点差异都拒绝继续，不重放已完成的建队操作。

WAR 须额外包含玩家与主持参与界面、局部 QR 编码器、玩家边界/会话/投影/候选类、
参与/裁定领域及结构；禁止临时证书、测试启动器和任何运行凭据进入 WAR。

## 7. 手工验收

只有可达路由、UI 或客户端 JavaScript 变化时才需要浏览器验收。按[部署指南](deployment.md)检查 `/health`、根重定向、`/host`、数据库诊断、安全头、错误 Host/origin 与明确排除的路由。真实业务写入必须单独授权并具备可验证恢复方案。

操作台布局验收应覆盖宽屏与手机宽度、键盘焦点和跳转操作区链接、原生折叠区展开、长名称与
空战役/数据库不可用/模组校验失败状态。确认节点与遭遇呈现现有读取模型提供的摘要，全部原有表单
可到达，角色卡编号建议不会改变检定执行者或版本。加载角色卡使用只读请求；保存按钮、
检定确认和其他业务提交仍须按上述授权边界验收。核对 CSP 下同源样式和脚本正常加载，
窄屏不截断操作区或角色卡内容；自动化页面合同由 `HostOverviewPageTest`、
`HostConsolePageTest` 和对应表单测试覆盖。

## Schema 2 战斗与动作继续验证

[内部战斗合同](rules/schema-2-combat.md)通过实际来源、准备、冻结镜像、角色和战斗仓储验证：

```bash
mvn '-Dtest=Schema2CombatCompositionTest,JdbcSchema2CombatSourceTest,JdbcSchema2CombatSnapshotTest,Schema2CombatSchemaTest,Schema2CombatRulesTest,Schema2CombatTimeRulesTest,Schema2CombatRuntimeServiceTest,JdbcSchema2CombatRuntimeRepositoryTest,Schema2CombatRuntimeSchemaTest' test
```

共享修改还须覆盖原组合 golden、stored projection、来源账本、
角色装备／健康／休息／成长和迁移 schema 诊断。服务用例验证真实近战、机会攻击在离开前
提交暂停、准确父继续、过期版本、一次消费、掉物与倒地、秒余数、死亡豁免、因果子历史、
故障整笔回滚、UNKNOWN 原操作查证，以及 OPEN/CLOSED 窗口和健康／计时完整直接恢复。
原始骰、预算和未实现来源拒绝在纯规则与存储测试中分别验证；不以过往测试计数代替本次结果。

WAR 包含战斗消费者／仓储和运行 V032—V034，排除来源 V008、离线写入工具与测试向量。
JDBC 代理及静态 SQL 检查不代替真实 MySQL、迁移执行、授权、部署、Host 或 archive format 2 验收。

## Schema 2 有限施法与持续效果验证

[施法作者](rules/schema-2-spell-authoring.md)与[有限施法](rules/schema-2-spells.md)分层选择聚焦测试：

```bash
mvn '-Dtest=Schema2SpellCompositionTest,JdbcSchema2SpellSourceTest,JdbcSchema2SpellSnapshotTest,Schema2SpellSchemaTest' test
mvn '-Dtest=Schema2SpellRulesTest,Schema2SpellRuntimeServiceTest,Schema2SpellRuntimeSchemaTest' test
```

新增 Wizard／环境与反应切片时，按改动选择：

```bash
mvn '-Dtest=Schema2WizardCompositionTest,JdbcSchema2WizardSourceTest,JdbcSchema2WizardSnapshotTest,Schema2WizardSpellbookRulesTest' test
mvn '-Dtest=Schema2ReactionSpellRulesTest,Schema2ReadyRulesTest,Schema2ReadySpellRulesTest,Schema2OngoingEffectRulesTest,Schema2ExplorationRulesTest' test
mvn '-Dtest=Schema2WizardRuntimeServiceTest,Schema2ExplorationRuntimeServiceTest,JdbcSchema2CombatRuntimeRepositoryTest,Schema2SceneRuntimeSchemaTest' test
```

作者与存储测试核对完整身份、学习名单、固定区域、全部原包与类型化行，不以可学习声明代替角色知识。
真实角色测试经过初始化、逐级成长、每级学习和长休准备，验证重复／越级学习、非法准备与元数据法术执行拒绝。
反应测试覆盖命中后伤害前的 Shield、Counterspell 嵌套、Ready attack/move/spell 的准备／触发／释放、
一次成本、专注占用、PASS 与到期。持续效果测试分别验证同名 SHADOWED、多个独立 SUPPRESSED 原因、
解除与重新求值、战斗结束／重入、探索时间和健康后果。
事务与历史测试经过真实准备、角色、场景及历史仓储，核对重复请求／过期版本、paused leaf 直接恢复、
缺失／多余／孤儿行拒绝、故障回滚和 UNKNOWN 原操作查证。共享角色与战斗消费者改变时，补选其对应
定向测试，验证法术书、成本和时间的中性保留。代理 JDBC 与静态 SQL 不替代真实 MySQL 约束、权限、
并发和持久化验收。

当前批准链为运行 V001—V042 与来源 V001—V012，共 54 份迁移源；来源为 53 张表、159 个触发器，
其中 schema 2 为三个账本表与 43 张类型化作者材料表。WAR 须包含施法／场景消费者、类型化仓储和
运行 V035—V038，排除来源 V009/V010、离线写入工具、作者包与测试向量。
这些命令描述可重复验证方法，不表示真实数据库或完整发布门已验收。

## Schema 2 有限怪物与多目标豁免伤害验证

[有限怪物合同](rules/schema-2-monster-breath.md)的作者、来源与镜像先选：

```bash
mvn '-Dtest=Schema2MonsterCompositionTest,JdbcSchema2MonsterSourceTest,JdbcSchema2MonsterSnapshotTest,Schema2MonsterSchemaTest' test
```

真实批次服务和共同 owner 按改动选取怪物规则／运行测试，并补选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2MonsterBreathRulesTest,Schema2MonsterBreathRuntimeServiceTest,Schema2MonsterBreathRuntimeSchemaTest,SchemaMigrationsTest,RuleSchemaMigrationsTest' test
mvn '-Dtest=Schema2CombatRuntimeServiceTest,Schema2SpellRuntimeServiceTest,Schema2ExplorationRuntimeServiceTest,Schema2ReadySpellRulesTest,Schema2ReactionSpellRulesTest,JdbcSchema2CombatRuntimeRepositoryTest,Schema2SceneRuntimeSchemaTest' test
```

作者和存储测试核对 `stat_block`／`action_profile` 完整复合原子、准确 action 条目、启用、
覆盖前后值、save 熟练真实来源、防御适用 gate、原 ZIP 与全部类型化行。旧字段或声明
没有默认执行语义；BLOCKED_METADATA 和其他未支持动作必须拒绝消费。

规则和真实服务测试使用明确初始状态及独立手算结果，经过实际准备／快照、角色、场景、
怪物部署和真实命令到仓储读回，覆盖八方向完整点格 roster、敌友／免疫目标、一次共享
6d6、稳定 target ordinal、逐目标真实 DEX／Bless、自然 1／20、奇数半伤、易伤／免疫、
temp HP 全吸收、零 HP／即死／倒地掉物、全部 APPLY 后强制结束及统一 CON cut。
公共设计原 PFE／Cutting Words golden 保留目标地位；它们未有消费者时，不能用假贡献
冒充本轮可执行验收。after-batch Ready 必须来自准确怪物动作完成事实，不能伪造攻击或施法。
[独立 JSON oracle](../src/test/resources/schema2-monster/breath-runtime-golden.json)固定真实一级
角色／Bless／Ready 和 Hell Hound／Mummy 的输入、骰流、四包期望与恢复不变量；测试读取
这些手算期望，不能用生产算法输出更新它。先攻／前置动作的随机消费与吐息的 13／14 骰分开计量。

分别保存真实根命令产生的 raw PENDING 和真实 Ready 产生的 OPEN／CLOSED 窗口，再在
来源不可用、骰源零调用的目标中直接恢复，核全部怪物、角色子历史、原骰、source／holder、
完整 T、checks／packets／obligations 与唯一父继续点。恢复后仅执行准确剩余后缀，
不能重付 action、重掷 raw、再次伤害、复活 ENDED Bless 或隐式 PASS。

事务用例在完整预检、末尾目标／角色子事件、末尾效果、batch 子行和 accepted 写入各边界
注入故障，区分新根全回滚与已提交 raw 前缀保留；同键重放、同键异意图、stale 继续、
容量不足、缺失／多余／孤儿行和 UNKNOWN 原操作查证分别观察。recharge 必须来自后续
本人真实 turn START，短／长休恢复须由真实有限怪物休息 owner 产生，GET／恢复均不抽骰或补时间。

全链为运行 V001—V042、来源 V001—V012，共 54 份迁移源；来源 53 表、159 触发器，
其中 schema 2 有三个账本表和 43 张类型化作者材料表。WAR 包含怪物／批次消费者、
类型化仓储及运行 V039/V040；排除来源 V011、离线写入能力、作者包、独立 oracle 与测试向量。
JDBC 代理和静态 SQL 不替代真实 MySQL、权限／锁并发、完整文件、Host、技术 END／清理、
部署或跨域发布门；本节是验证方法，不缓存历史测试计数或宣称迁移已执行。

另行授权并安装后，在准确运行 schema 以只读身份执行
[怪物／吐息核验](../database/verify/schema2-monster-breath.sql)，并与
[场景核验](../database/verify/schema2-combat-runtime.sql)组合使用。inventory 查询列出表、
触发器和 FK 可见性；异常查询核完整头计数、连续 ordinal、release 时空间证明、共享骰和、
save／packet／防御／专注引用与算术、休息及其结束引用，正常应无异常行。脚本只读且不修复。
`Schema2MonsterBreathRuntimeSchemaTest` 对批准链、持久 DDL 前空场景门、精确复合 FK、
不可变触发器、闭合枚举、NULL 联合分支、无窗口 raw 继续及窄授权作源码合同检查；
它不执行上述 SQL，也不证明真实 MySQL 已通过。

## Schema 2 法师特殊恢复与法术共享伤害验证

[奥术回想](rules/schema-2-arcane-recovery.md)的无数据库定向命令：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2ArcaneRecoveryRulesTest,Schema2ArcaneRecoveryRuntimeServiceTest,Schema2ArcaneRecoverySchemaTest' test
```

验证真实短休完成、每日时钟边界、各环缺额与合计额度、拒绝旧 BLOCKED、幂等/版本、
准备/学习保留每日事实、类型化读回/直接恢复，以及晚写失败与 UNKNOWN 查证。

[法术共享伤害](rules/schema-2-spell-save-damage.md)的无数据库定向命令：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2SpellSaveDamageCompositionTest,Schema2SpellSaveDamageSourceTest,Schema2SpellSaveDamageSchemaTest,Schema2SpellSaveDamageRequestDigestTest,Schema2SpellSaveDamageRuntimeServiceTest' test
```

独立作者向量与手算结果覆盖真实 Wizard 授权/位数/组件、混合生物完整目标、共享原骰、逐目标
豁免/防御、健康与专注、反制不退款、伤害后 Ready、已付父施法失能、直接恢复、坏物理来源、
事务回滚与未知提交。旧元数据、Sculpt、地面物品、活动抗魔和未支持的嵌套对象风险保持明确拒绝。
状态域与旧非范围请求摘要使用独立固定期望，不能仅验证 writer/reader 自洽。

共用动作、场景、角色与批次消费变化时，按具体差量加入既有施法、反应/Ready、怪物吐息、
角色仓储及迁移/发布边界测试；已通过且输入未变化的结果按本文规则复用。真实 MySQL 的
CHECK/FK/触发器、权限和锁语义须另行授权验收，JDBC 代理与静态 SQL 检查不能代替。
WAR 须包含新增规则/消费者、类型化仓储及运行 V041/V042；来源 V012、测试向量、离线工具、
参考文件与本地 Agent 工作区必须排除。完整规则仍为 DRAFT；这些内部测试不单独证明
Host 或完整文件接合，当前 SQLite 采用范围与额外验收见[领域接合表](design/v2/domain-integration.md)。
