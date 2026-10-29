# Agent 项目地图

只负责定位，不替代 [AGENTS.md](../AGENTS.md) 或下表的权威合同。新会话通过
[dnd-project-start](../.agents/skills/dnd-project-start/SKILL.md) 按需读取；不要递归加载全部链接。
当前进度以 Git、代码、测试和经授权的外部证据为准，文件存在不证明功能发布、迁移执行或部署完成。

## 最小项目识别

- 本机、单 DM、唯一入口 `http://127.0.0.1:8080`；LAN/公网、HTTPS、玩家账号与公开 API 不在当前范围。
- Java 21 / Maven WAR / Tomcat 10.1 / Servlet-JSP / 原生 JavaScript / MySQL 8 / JDBC-JNDI / Gson。
- `dnd5e2014_srd51_se_v1` release 1、canonical/archive 1：`RELEASED`，新战役默认；既有持久化合同不可改写。
- `dnd5e2014_srd51_se` release 1、canonical/archive 2：仍为 `DRAFT`；可协调演进，跨领域发布门前不可绑定战役或激活存档。发布门见 [完整规则目标](rules/srd-5.1-complete.md)。
- [产品说明](product.md)与 [v2 设计基线](design/v2/README.md)记录目标，和当前实现分开阅读；理解项目不依赖本地 Agent 目录。
- 请求解析 → `web`；校验/事务 → `service`；JDBC → `persistence`；冻结目录/规范编码 → `module`；入口安全 → `security`。随机结果、版本推进与审计由服务器拥有，事务不得部分提交。

## 任务路由（只选相关行）

下表 Java 入口相对 `src/main/java/com/dndtool/`。测试从 `src/test/java/com/dndtool/` 下的
同包同名 `*Test.java` 开始；没有同名测试时按表中测试族搜索。路径是搜索起点，不是要全部读取的清单。

| 任务关键词 | 先读合同的相关部分 | 生产入口 / 测试定位 |
|---|---|---|
| SRD 5.1 原文、规则条款、章节与页码 | 先用[本地主题索引与分页文本](references/srd-5.1/README.md)，按主题或搜索结果只读相关页；歧义核对[本地 PDF](references/srd-5.1.pdf) | 重建与官方回源条件见[参考说明](references/README.md)；原文不替代冻结合同或证明项目已实现 |
| 产品范围、v2 总体设计、规则版本与存储方向 | [产品说明](product.md)、[v2 设计导航](design/v2/README.md)；按主题选一篇 | 先区分目标已定、待细化和当前实现；不从设计记录推断迁移、发布或部署已完成 |
| v2 模组接口、组合、依赖、覆盖与支持资格 | [模组接口基线](design/v2/module-interface-baseline.md)、[采用边界](design/v2/adoption-boundaries.md) | 公共合同为正式目标；现行作者 schema 1 与语言/工具 PARTITION 不代表通用组合平台已实现 |
| v2 schema 2 作者包、最小组合链路、字段覆盖与失败向量 | [最小组合链路验证](design/v2/schema-2-composition-validation.md)，再读模组接口基线 | `module/composition/Schema2CompositionKernel.java`；测试在 `module/composition/`，独立向量在 `src/test/resources/schema2-composition/`；有限纯内核不授予安装或运行资格 |
| schema 2 原始 ZIP、生产摘要、安装、独立接受与提交查证 | [制品合同](rules/schema-2-rule-artifacts.md)、[安装与接受](rules/schema-2-installation-and-acceptance.md)；存储见[来源账本](rules/schema-2-source-ledger.md) | `module/composition/ProductionRuleArtifact.java`、`ProductionSchema2Projection.java`；`offline/rules/Schema2SourceInstallation.java`、`JdbcSchema2RuleSource.java`；来源 V003—V011；资格门与生产摘要独立于验证内核 |
| schema 2 检定、装备与健康作者模型、纯组合及真实来源 | [作者来源合同](rules/schema-2-foundation-authoring.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CharacterSourceRepository.java`、`JdbcSchema2ExecutionSourceRows.java`；`Schema2CheckCompositionTest`、`Schema2EquipmentHealthCompositionTest`、对应来源/结构测试；来源 V006/V007；运行镜像、事实存储与角色基础消费者见下方入口 |
| schema 2 来源读取、组合快照、原子转存、收据和恢复绑定 | [来源到运行快照](rules/schema-2-runtime-snapshot.md) | `persistence/JdbcSourceSchema2Repository.java`、`JdbcSchema2RuntimeSnapshotRepository.java`、`JdbcSchema2SnapshotControlRepository.java`、`service/Schema2SnapshotTransferService.java`；运行 V021/V023/V024/V027/V028/V030/V032/V035/V037/V039；对应测试及 `Schema2ToolCompositionTest`、`JdbcSchema2ToolSnapshotTest`、 `ProductionSchema2StoredProjectionTest`；INTERNAL 材料不授予游戏执行资格 |
| schema 2 检定、装备与健康规则镜像及类型化角色事实 | [角色事实存储](rules/schema-2-character-fact-storage.md) | `persistence/JdbcSchema2EquipmentSnapshotRows.java`、`JdbcSchema2GrowthSnapshotRows.java`、`JdbcSchema2CharacterRuntimeRepository.java`；运行 V028—V031；对应快照、历史和 schema 测试；叶仓储负责结构/邻接及追加/直接恢复，规则语义由角色基础消费者验证 |
| schema 2 初始语言／工具授予、真实来源、跨模组槽和角色计划 | [初始授予合同](rules/schema-2-proficiency-grants.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CharacterSourceRepository.java`、`service/Schema2InitialProficiencyRules.java`、`Schema2InitialCharacterPlanService.java`；固定向量在 `src/test/resources/schema2-grants/`，对应组合、存储与计划测试；内部只读计划不等于业务初始化 |
| schema 2 角色成长、子职业/ASI/专长/多职业、资源、标准休息和局部历史恢复 | [成长与资源合同](rules/schema-2-character-statistics.md)、[休息合同](rules/schema-2-character-rest.md) | `service/Schema2CharacterProgressionRules.java`、`Schema2GrowthRules.java`、`Schema2CharacterRestRules.java`、`Schema2CharacterHealthRules.java`、`Schema2CharacterRuntimeService.java`、`persistence/JdbcSchema2CharacterRuntimeRepository.java`；来源 V005、运行 V027；单角色 INTERNAL，独立成长/休息入口不执行持续效果，完整战役/Host 仍关闭 |
| schema 2 检定、装备实例与护甲限制、伤害/治疗/死亡豁免 | [角色基础合同](rules/schema-2-character-foundations.md) | `service/Schema2CharacterCheckRules.java`、`Schema2CharacterEquipmentRules.java`、`Schema2CharacterHealthRules.java`、`Schema2CharacterRuntimeService.java`；类型化来源、镜像及局部历史；来源 V006/V007、运行 V028—V031，保持单角色 INTERNAL 边界 |
| 新旧业务共同写入、永久守卫、事务故障与初始化准备 | [共同业务事务](rules/business-write-transactions.md)、[运行设计](design/v2/runtime-and-transactions.md) | `persistence/BusinessWriteTransactions.java`、`JdbcSchema2InitialPlanRepository.java`、`service/Schema2InitializationPreparationService.java` 与 `Schema2SnapshotTransferService.java`；事务 owner 清单及对应测试见共同合同；准备事实不授予完整游戏执行资格 |
| v2 机制接入、责任清单、支持与验收判定 | [机制接入责任清单](design/v2/mechanism-integration-checklist.md)，再读其引用的领域合同 | 逐项定位定义、真实消费者、状态/历史/恢复及验证证据；清单不是已实现机制注册表 |
| schema 2 战斗作者、近战武器、步行来源与镜像 | [战斗作者合同](rules/schema-2-combat-authoring.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CombatSourceRows.java`、`JdbcSchema2CombatSnapshotRows.java`；来源 V008、运行 V032；`Schema2CombatCompositionTest` 和对应来源/快照/结构测试 |
| schema 2 内部战斗、先攻/回合、近战/移动/OA、倒地/掉落与秒时钟 | [战斗合同](rules/schema-2-combat.md) | `service/Schema2CombatRuntimeService.java`、`Schema2CombatRules.java`、`Schema2CombatState.java`、`Schema2CombatTimeRules.java`、`persistence/JdbcSchema2CombatRuntimeRepository.java`；运行 V033/V034；对应服务/规则/历史测试；同战役独立 prepared 角色、同规则/同秒，不开放 Host/完整基础包 |
| schema 2 施法作者、法术书/准备、Shield/Counterspell/Ready、抗魔区域与探索 | [施法作者合同](rules/schema-2-spell-authoring.md)、[有限施法合同](rules/schema-2-spells.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2SpellSourceRows.java`、`JdbcSchema2WizardSourceRows.java`、对应 SnapshotRows、`service/Schema2SpellRules.java`、`Schema2WizardSpellbookRules.java`、`Schema2ActionFlowRules.java`、`Schema2OngoingEffectRules.java`、`Schema2ExplorationRules.java`、`Schema2CombatRuntimeService.java`；来源 V009/V010、运行 V035—V038；Spell/Wizard 组合、来源/镜像、法术书、反应及运行恢复测试；生命领域一级和单职业法师 1—5 级保持 INTERNAL，未实现法术、完整 Host/base/archive 2 仍关闭 |
| v2 攻击、移动、施法、反应、暂停与继续 | [动作继续协议](design/v2/action-continuation-protocol.md)，再读所涉机制合同 | 单一动作流及反应/选择的恢复目标；具体 producer、消费者、存储和 Host 入口须逐项采用，不从设计推断已支持 |
| v2 施法授权、准备、升环/仪式、组件与后续机制扩展 | [施法扩展合同](design/v2/spellcasting-extension-contract.md)，再读有限施法及公共协议 | 目标接口与优先级；复用角色资源、唯一动作流、检定/伤害和持续效果 owner；按具体分支补足作者、消费者及恢复，不从元数据或设计取得执行资格 |
| v2 持续效果、叠加、压制、恢复贡献、到期与专注结束 | [持续效果协议](design/v2/ongoing-effect-protocol.md)，再读具体机制及动作继续合同 | 区分实例存续、有效贡献和永久结束；公共生命周期确定不代表各法术、毒病或物品效果已实现 |
| schema 2 有限怪物、吐息、充能、休息、完整目标、共享伤害与专注 | [有限怪物合同](rules/schema-2-monster-breath.md) | `module/composition/Schema2MonsterCodec.java`；`persistence/JdbcSchema2MonsterSourceRows.java`、`JdbcSchema2MonsterSnapshotRows.java`、`JdbcSchema2MonsterBreathRows.java`；`service/Schema2CombatRuntimeService.java`、`Schema2SaveDamageRules.java`、`Schema2MonsterRestRules.java`、`Schema2CombatState.java`；来源 V011、镜像 V039、事实 V040；`Schema2MonsterBreathRulesTest` 及 Monster 组合/来源/镜像/服务/历史测试；INTERNAL 点格机制，完整 Host/base/archive 2 仍关闭 |
| v2 多目标公共接口、体积空间、伤害骰后反应与完整保存 | [多目标豁免伤害](design/v2/multi-target-save-damage.md)，再读动作继续及持续效果协议 | 区分已采用的有限怪物合同与公共目标；原 PFE/Cutting Words golden、通用体积、完整文件和 Host 仍须各自消费者与验收 |
| v2 快照绑定、运行生命周期、保存/结束/恢复设计 | [v2 采用边界](design/v2/adoption-boundaries.md)，再读对应主题 | 全域设计入口在 `docs/design/v2/`；未采用能力不虚构生产入口，语言 PARTITION 不构成运行资格 |
| 规则目录、canonical、hash、发布身份 | [冻结 v1](rules/srd-5.1.md) §2/§11；[v2 目录](rules/character-catalog-v2.md) | `module/ModuleCatalog.java`、`module/BuiltinModuleReleaseRegistry.java`、`module/ModuleCanonicalEncoderV1.java` / `module/ModuleCanonicalEncoderV2.java` |
| 启动、JNDI、数据库就绪诊断 | [架构](architecture.md) §4/§8；[数据库](database.md) | `web/DatabaseDiagnosticsFactory.java`、`web/DatabaseStartupListener.java` → `service/DatabaseDiagnostics.java` → `persistence/DatabaseSchemaVerifier.java`；`DatabaseDiagnosticsTest`、`DatabaseDiagnosticsFactoryTest`、`HostDatabaseDiagnosticServletTest` |
| 语言/工具作者 JSON、严格读取、纯分区投影 | [语言作者入口](rules/language-author-package.md)、[工具熟练目录](rules/tool-catalog-partition.md) | `module/CharacterCatalogAuthorPackageReader.java`、`module/CharacterCatalogPartition.java`；`CharacterCatalogAuthorPackageReaderTest`、`ToolAuthorPartitionTest`；正式作者源在仓库根 `rule-packages/srd51-complete/`，不入 WAR |
| 源目录只读 JDBC、当前安装证据、连接归还 | [源目录读取](rules/source-language-reading.md) | `persistence/JdbcSourceLanguageRepository.java` 的 `loadCatalog()`；`JdbcSourceLanguageRepositoryTest`；返回语言与工具分区，不接生产完整目录或运行就绪 |
| 运行语言快照、run/snapshot 身份、调用方事务 | [运行语言快照分区](rules/runtime-language-snapshot.md) | `persistence/JdbcRuntimeLanguageSnapshotRepository.java`；`JdbcRuntimeLanguageSnapshotRepositoryTest`、`V019RuntimeLanguageSnapshotSchemaTest`；V019 仅建空表，语言 PARTITION 不提供执行资格 |
| 双目录快照、工具结构、逐字段及字节链路 | [工具熟练目录](rules/tool-catalog-partition.md) | `persistence/JdbcRuntimeCharacterCatalogRepository.java`；`CharacterCatalogChainTest`、`ToolCatalogSchemaTest`；运行 V020、来源 V002；PARTITION 不提供执行资格 |
| 离线语言安装、制品清单、操作票据、提交查证 | [离线语言安装](rules/offline-language-installation.md) | `offline/rules/RulePackageCli.java`、`offline/rules/SourceInstallation.java`、`offline/rules/JdbcRuleSource.java`；`RuleArtifactTest`、`TicketStoreTest`、`SourceInstallationTest`；离线类不入 WAR |
| 语言链路等价、独立字段/字节期望、跨快照故障 | [语言分区链路核验](rules/language-partition-equivalence.md) | 测试目录 `offline/rules/LanguagePartitionEquivalenceTest.java`；连接作者、离线安装、源只读及运行分区仓储；代理证据与真实 JDBC 分开报告 |
| 角色语言／工具授予、来源槽、卡片和内部存档 | [角色授予合同](rules/character-proficiency-grants.md) | `service/LevelOneCharacterRules.java`、`LevelOneCharacterCreationService.java`、`persistence/JdbcCharacterProficiencyGrantRepository.java`；运行 V022；`CharacterCardService` 与内部 format 2 读写；旧 DRAFT 路径不等于 schema 2 业务初始化 |
| v1 战役创建、角色创建、角色卡 | [冻结 v1](rules/srd-5.1.md) §3—5；[架构](architecture.md) §6 | `service/CampaignCreationService.java`、`service/CharacterCreationService.java`、`service/CharacterCardService.java` |
| v2 一级创建、种族、背景、初始选择 | [一级创建](rules/character-creation-v2.md) | `service/LevelOneCharacterCreationService.java`；`LevelOneCharacterRulesTest` |
| 升级、HP、生命骰 | [升级](rules/level-advancement-v2.md) | `service/LevelAdvancementService.java`；`LevelAdvancementRulesTest` |
| 旧 DRAFT 职业/子职业特性与资源恢复组件 | [职业特性](rules/class-features-v2.md) | `module/ResourceRecoveryProfile.java`、`service/ClassResourceRecoveryRules.java`、`service/ClassFeatureRules.java`；`ResourceRecoveryProfileTest`、`ClassResourceRecoveryRulesTest`；schema 2 的真实休息消费者见上方成长/休息入口 |
| 多职业、ASI、专长、熟练 | [多职业与专长](rules/multiclass-asi-feats-v2.md) | `service/CharacterAdvancementChoiceRules.java`；`LevelAdvancementServiceTest` |
| 多职业施法、共享法术位、Pact Magic | [共享法术位](rules/multiclass-spell-slots-v2.md)，含未完成边界 | `service/MulticlassSpellSlotRules.java`；`V018MulticlassSpellSlotFoundationSchemaTest` |
| 检定、骰子、简单物品、事件效果 | [冻结 v1](rules/srd-5.1.md) §6/§8—9 | `service/CheckTransactionService.java`、`service/CheckEffectExecutionService.java` |
| 节点地图、位置、最小遭遇 | [冻结 v1](rules/srd-5.1.md) §9 | `service/EntityPositionTransactionService.java`、`service/EncounterStateTransactionService.java` |
| 存档、导入导出、恢复 | [架构](architecture.md) §9；[format 2 角色状态](rules/archive-format-2-character-state.md) | `service/CampaignArchiveFormatDispatcher.java`；按 `CampaignArchive*Test` 定位，v2 先看 `CampaignArchiveV2CharacterStateTest` |
| JSP、JS、Host API、页面 | [架构](architecture.md) §7；对应领域合同 | `web/Host*Servlet.java`、`src/main/webapp/WEB-INF/views/`、`src/main/webapp/host/assets/`（后两项相对仓库根）；`web/Host*Test.java` |
| loopback、Session、CSRF、安全头 | [架构](architecture.md) §5；[安全](security.md) | `security/HostBoundaryFilter.java`、`security/HostRequestSecurityFilter.java`；API 注册见 `src/main/webapp/WEB-INF/web.xml`（仓库根） |
| schema、迁移、JDBC、权限、数据库诊断 | [数据库](database.md)；拆分设计仅需要时读 [规则/运行库分离](rule-database-separation.md) | `persistence/SchemaMigrations.java`、相关 `Jdbc*Repository.java`；`SchemaMigrationsTest` / `Jdbc*RepositoryTest`；SQL 在仓库根 `src/main/resources/db/migration/` 与 `database/` |
| 独立规则库、来源账本、语言结构、来源权限 | [规则来源 schema](rule-source-schema.md)；[数据库](database.md) | `persistence/RuleSchemaMigrations.java`、`persistence/RuleDatabaseSchemaVerifier.java`；`RuleSourceSchemaContractTest`；SQL 在仓库根 `database/rules/migration/`、`database/grants/rule-source-*.sql` 与 `database/verify/rule-source-schema.sql` |
| 构建、测试、WAR、部署、备份 | [测试](testing.md)；实际部署才读 [部署](deployment.md) / [备份恢复](backup-and-restore.md) | 仓库根 `pom.xml`、`config/`、`tools/`；MySQL 集成测试需独立授权与隔离目标 |
| MySQL 测试连接目标保护 | [测试](testing.md) §3 | 测试目录 `persistence/MySqlIntegrationTestSupport.java`；`MySqlIntegrationTestSupportTest`，两入口在连接前仅允许专用测试目标 |
| 新增完整规则领域、覆盖差距、发布门 | [完整规则目标](rules/srd-5.1-complete.md)；[架构](architecture.md) §2—3 | 先确认已有目录/运行模型/测试；没有入口不代表已实现，不为未来领域编造类名 |
| Agent、skill、项目导航、文档 | [AGENTS.md](../AGENTS.md)、本文 | `.agents/skills/dnd-project-start/SKILL.md`（仓库根）；不预读业务源码 |

## 使用与维护

- Pi 在项目受信任且未禁用 skills 时发现 `.agents/skills/`，默认只把名称与描述放入上下文；`AGENTS.md` 引导 Agent 在首个任务加载本 skill。它不是自动执行的启动脚本。
- 新增 skill 后可在已信任项目中 `/reload` 或重启；显式调用 `/skill:dnd-project-start 具体任务` 可确保加载。其他 Agent 也可按 `AGENTS.md` 的链接读取，无需 Pi 插件。
- 入口变化时同步修正表中路径。规则正文、安全/事务不变量、账号职责、验证方法只在对应权威文档维护，不在 skill 中复制第二份。
- 新 v2 产品和跨领域设计维护在 `docs/design/v2/`。本地 SD/CD 编号仅是来源线索；历史提案、任务状态和测试报告不能覆盖正式设计或证明已实现。
- 动态 Issue/PR、分支、历史测试结果和部署状态不缓存于本文。被忽略的 `.pi/harness/domain-json-rules/` 是本机分域 JSON harness，仅任务明确涉及它时读取，不进入 Git 或 WAR。
