# 测试指南

测试分为聚焦单元/边界测试、完整 Maven 回归、显式 MySQL 集成测试、WAR 审计和必要的手工 HTTP/UI 验收。仓库文档记录可重复方法；某次运行的动态计数和哈希应留在 CI 或发布记录中。

## 1. 聚焦测试

修改生产代码前后先运行最相关的测试类，例如：

```bash
mvn '-Dtest=CheckTransactionServiceTest,JdbcCheckExecutionRepositoryTest' test
```

根据变更覆盖正常行为、边界值、Unicode 码点/NFC、非法冻结数据、旧版本、幂等冲突、事务失败与禁止部分写入。重命名批次还应覆盖受影响的 Servlet 注册、反射字符串、页面资源和脚本引用。

## 2. 完整验证

代码、资源、迁移、构建或公开文档整理完成后运行：

```bash
mvn clean verify
```

报告必须来自完整结束的进程，并包含退出码、测试总数、失败、错误、跳过和最终 `BUILD SUCCESS`/失败。不要从截断或仍在运行的输出宣称成功。

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
3. V001—V019 完整迁移链只在物理隔离且可销毁的 MySQL 实例或容器中演练；该实例内部创建原名 `dnd_tool_se`，不与运行实例共享 schema、账号或持久 volume。
4. 自动化直接按生产清单读取仓库中的原始迁移文件，不生成 SQL 副本、不改写 `USE`、不重算摘要。每个迁移使用新的客户端连接并显式把默认数据库设为 `dnd_tool_se`；这是 V007 本身没有 `USE` 时仍能正确定位的必要条件。
5. 迁移完成后，使用该隔离实例内的 `dnd_tool_se_validation_ro` 核对完整 `schema_meta`、可见结构和目录计数。MySQL 按 `TRIGGER` 权限过滤的触发器定义由隔离实例内的迁移身份在停止迁移写入后核对，但验证步骤只能执行审核过的 `SELECT`/`SHOW`。
6. 运行实例的 `dnd_tool_se` 只允许 `dnd_tool_se_agent` 做只读盘点/Harness 比较。向它应用迁移、授权或测试写入不是测试套件的一部分，必须转入独立部署检查点。

账号/schema 合同及现有运行库的阶段性使用决策见[数据库说明](database.md)。

## 4. 迁移与规则测试

[语言分区链路等价核验](rules/language-partition-equivalence.md) 的无数据库定向命令：

```bash
mvn '-Dtest=LanguagePartitionEquivalenceTest,MySqlIntegrationTestSupportTest' test
```

它从正式作者源经过生产离线安装、源只读适配、运行镜像写入和指定身份读取，在每个边界
对照独立六字段矩阵、类型化投影和受审 canonical 字节向量，避免源与镜像共同遗漏仍让往返
相等通过。覆盖内容/排序变化、同键不同快照、读取损坏和调用方事务回滚；源断开后的镜像
读取只证明本分区不访问源库。事务 JDBC 代理及测试目标保护均不连接真实数据库，不能替代
另行授权的隔离实例中原样迁移、安装、驱动、权限与持久化验收，也不提供完整规则就绪资格。

真实语言 JDBC 验收入口为 `tools/rule-packages/verify-language-jdbc.py --execute-disposable`，
须先获得隔离实例、迁移和测试写入授权。它先重验两组无数据库连接保护，再创建 tmpfs MySQL，
原样迁移并等待独立现场结构/权限审计；实际用例为 `LanguagePartitionJdbcIT`，测试跳过不算
成功。默认不带执行参数时只检查迁移清单。前置条件、账号、清理及故障注入边界见
[隔离 JDBC 入口](rules/language-partition-equivalence.md#隔离-jdbc-入口)。

[语言作者分区](rules/language-author-package.md) 的无数据库定向命令：

```bash
mvn '-Dtest=LanguageAuthorPackageReaderTest,ModuleCanonicalEncoderV1Test,ModuleCanonicalEncoderV2Test,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

它核对正式作者源的 18 项独立字段矩阵、严格 JSON/UTF-8/整数、Unicode 码点/NFC、资源上限、
准确键与分类集合、排序及独立 canonical 字节向量。只证明语言分区，不产生完整规则摘要或
发布资格；目录/安装清单与源库事务验收见下面的离线安装边界。WAR 审计须确认纯模型/reader
类存在，作者 JSON、说明/许可包与测试向量均不在 WAR 中。

[离线语言安装](rules/offline-language-installation.md) 的无数据库定向命令（Linux/WSL）：

```bash
mvn '-Dtest=RuleArtifactTest,TicketStoreTest,SourceInstallationTest' test
```

它覆盖完整制品和独立指纹向量、持久票据与未决标记、单连接锁序、各写入点异常及影响行数
错误、实际查询结果被篡改后的整笔回滚，以及提交/回滚/查证失败的分类。JDBC 代理测试不证明
真实 MySQL 权限、触发器、并发屏障、响应丢失或介质断电耐久性；这些按操作说明在另行授权的
隔离实例上验收。安装仍只产生 DRAFT 语言 PARTITION，不提供完整规则就绪或发布资格。
WAR 必须排除 `com/dndtool/offline/` 全部类、安装工具、作者包、安装清单和测试向量。

[源语言分区读取](rules/source-language-reading.md) 的无数据库定向命令：

```bash
mvn '-Dtest=JdbcSourceLanguageRepositoryTest,RuleDatabaseSchemaVerifierTest,LanguageAuthorPackageReaderTest,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

它核对同一只读事务中的准确源库/账本、当前 PARTITION 证据、18 项独立字段矩阵、严格 JDBC
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

[离线规则源 schema](rule-source-schema.md) 的无数据库定向命令：

```bash
mvn '-Dtest=RuleSchemaMigrationsTest,RuleSourceSchemaContractTest,RuleDatabaseSchemaVerifierTest' test
```

它检查独立源清单/严格 UTF-8/载荷摘要、表与授权结构防回归，以及完整只读账本比较、准确默认库、
有界查询和 JDBC 资源关闭。SQL 结构测试不能替代真实引擎对 CHECK、触发器、FK、Unicode 和
权限/并发的验证。规则 SQL 必须留在 WAR 外；完整构建同时保护运行 V001—V018 原批准摘要，
并核对追加 V019 后的完整运行清单。
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
`CampaignArchiveCapabilityBoundaryTest` 及受影响消费者的拒绝回归，再按第 2 节完整验证；
生产代码变化还须按第 6 节审计 WAR。这些纯测试无需启用 MySQL 集成测试。

- V001—V018 是不可修改的迁移历史；V011 的角色 DRAFT 目录、V012 的一级创建 schema/profile、
  V013 的升级/生命骰 schema/profile、V014 的职业特性/恢复矩阵、V015 的多职业/ASI/专长框架、
  V016 的初始熟练基线、V017 的 format 2 状态来源及 V018 的多职业施法贡献由清单摘要、精确计数、
  关系、profile 语法和 canonical-v2 负例测试保护。
- `ImmutableLegacyMigrationContractTest` 固定 V001—V010 历史；
  `DeferredPublicSurfaceExclusionTest` 只保护永久延期的公开访问面，不再禁止新增 Host 规则领域。
- 规则测试覆盖未知/重复/未发布数据、稳定键引用、范围、NFC、UTF-8 无符号排序、精确小数、规范字节流与内容摘要。
- `RELEASED` 模组不可变，战役冻结值、实际目录摘要、发布摘要和应用清单必须一致，否则失败关闭。
- V011—V018 文件及其中已持久化身份受历史测试保护，但完整规则族仍为 DRAFT：后续前向迁移可以协调改变未发布目录、运行模型、canonical/archive 投影和对应测试，不要求为被替代的 DRAFT 行为保留兼容层。
- archive format 2 的严格往返测试应随各领域渐进建立；只有跨领域发布候选才固定摘要、激活格式、切换默认发布版并运行完整发布门矩阵。

一级创建的定向验证包括 `LevelOneRuleProfileTest`、`LevelOneCharacterRulesTest`、
`LevelOneCharacterCreationServiceTest`、`JdbcLevelOneCharacterCreationRepositoryTest`、
`V012LevelOneCharacterCreationSchemaTest` 和 `LevelOneCharacterRuntimeGrantsTest`。它们证明预览
无权威写入、非法选择被拒绝、事件尾变化导致 stale preview、事务故障不留下部分角色，并且
DRAFT 发布门保持关闭。

升级的定向验证包括 `AdvancementValueProfileTest`、`LevelAdvancementRulesTest`、
`LevelAdvancementServiceTest`、`JdbcLevelAdvancementRepositoryTest`、
`HostLevelAdvancementServletTest` 和 `V013LevelAdvancementSchemaTest`。它们覆盖逐级 profile
畸形、跳级、资源状态不一致、固定值/服务端掷骰、stale row version、事务失败和禁止部分写入，
并证明过期状态或幂等重放不会消耗随机性。

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

## 5. Web 与安全测试

覆盖精确 loopback/Host/scheme/port、伪造 `X-Forwarded-*`、方法限制、Session Cookie、CSRF、Origin/Referer、Fetch Metadata、安全响应头、epoch、对象版本、幂等键和摘要。错误入口应返回一致的普通错误且不能到达业务写入。

客户端权威边界测试应确认浏览器不能指定骰点、选中骰、派生修正、最终结果、效果算法、
数据库身份或版本推进。相应领域实现后，还必须确认浏览器不能指定服务器候选集合之外的
职业/专长/法术/目标，不能覆盖先攻顺序、伤害调整、资源消耗、状态持续时间或 DM 裁定类型。

## 6. WAR 审计

生产代码或打包资源变化后，记录候选 WAR：

- 字节数、SHA-256 与 ZIP 条目数；
- 必需的新类、JSP、JavaScript、`web.xml` 与 V001—V019 资源；
- 私钥/证书秘密、凭据、Tomcat 外部配置、日志、备份、真实存档、结果捕获与本地路径文件名的匹配数；
- 生产资源和示例配置差异中的秘密扫描结果。

WAR 审计通过不代表已经部署。

## 7. 手工验收

只有可达路由、UI 或客户端 JavaScript 变化时才需要浏览器验收。按[部署指南](deployment.md)检查 `/health`、根重定向、`/host`、数据库诊断、安全头、错误 Host/origin 与明确排除的路由。真实业务写入必须单独授权并具备可验证恢复方案。
