# Agent 项目地图

只负责定位，不替代 [AGENTS.md](../AGENTS.md) 或下表的权威合同。按任务选择下表入口并按需读取，
不要递归加载全部链接。本地可选的 `dnd-project-start` skill 只提供导航便利；项目 skill 位于
不跟踪的 `.agents/skills/`，缺失时直接使用本地图。
当前进度以 Git、代码、测试和经授权的外部证据为准，文件存在不证明功能发布、迁移执行或部署完成。

## 最小项目识别

- DM 固定 `http://127.0.0.1:8080`；SQLite 有默认关闭的独立 HTTPS 玩家原型，配置不创建监听；边界见[安全说明](security.md)，公网与公开 API 不在范围内。
- Java 21 / Maven WAR / Tomcat 10.1 / Servlet-JSP / 原生 JavaScript / SQLite-JDBC（显式当前运行）/ MySQL 8-JNDI（默认简化与来源）/ Gson。
- 原型阶段只保证当前版本，不承诺跨版本战役延续或旧存档支持；设计修订按 [AGENTS.md](../AGENTS.md#prototype-design-and-compatibility) 与[产品说明](product.md)处理。
- 默认 MySQL 模式以 `dnd5e2014_srd51_se_v1` release 1、canonical/archive 1 为 `RELEASED` 新战役默认。显式 SQLite 新建选择本机已接受的有序准确规则成员，使用独立的 `RULE_BOUND_CAMPAIGN` format 3。
- `dnd5e2014_srd51_se` release 1、canonical/archive 2：仍为 `DRAFT`；可协调演进，跨领域发布门前不可绑定战役或激活存档。发布门见 [完整规则目标](rules/srd-5.1-complete.md)。
- [产品说明](product.md)与 [v2 设计导航](design/v2/README.md)记录当前可修订的目标，和实现分开阅读；理解项目不依赖本地 Agent 目录。
- 请求解析 → `web`；校验/事务 → `service`；JDBC → `persistence`；冻结目录/规范编码 → `module`；入口安全 → `security`。随机结果、版本推进与审计由服务器拥有，事务不得部分提交。

## 任务路由（只选相关行）

下表 Java 入口相对 `src/main/java/com/dndtool/`。测试从 `src/test/java/com/dndtool/` 下的
同包同名 `*Test.java` 开始；没有同名测试时按表中测试族搜索。路径是搜索起点，不是要全部读取的清单。
表中运行 V019—V042、来源迁移与 INTERNAL 说明指原 MySQL 服务；当前 SQLite 路线由
独立聚合/事务复用这些纯规则，不能把旧 Host 关闭门误套到新有限入口。

| 任务关键词 | 先读合同的相关部分 | 生产入口 / 测试定位 |
|---|---|---|
| SRD 5.1 原文、规则条款、章节与页码 | 先用[本地主题索引与分页文本](references/srd-5.1/README.md)，按主题或搜索结果只读相关页；歧义核对[本地 PDF](references/srd-5.1.pdf) | 重建与官方回源条件见[参考说明](references/README.md)；原文不替代冻结合同或证明项目已实现 |
| SRD 5.1 English semantic definitions and source coverage | [Semantic specification](semantics/srd-5.1/README.md), [coverage and review](semantics/srd-5.1/coverage.md) | Source behavior and labeled interpretations; semantic completeness and source review are independent of application contracts and runtime support |
| 产品范围、v2 总体设计、规则版本与存储方向 | [产品说明](product.md)、[v2 设计导航](design/v2/README.md)；按主题选一篇 | 区分当前设计选择、待细化与实际实现；按需求证据修订，不从设计记录推断迁移、发布或部署已完成 |
| v2 模组分层、应用机制与接口采用 | [模组分层](design/v2/module-layering.md)、[模组接口](design/v2/module-interface-baseline.md)、[采用边界](design/v2/adoption-boundaries.md) | 核心/机制/内容/UI、战役绑定、单一事实 owner。外部内容按应用模板和现有机制闭包校验 |
| 第四阶段的规则行为接口 | [行为接口验证](design/v2/rule-behavior-interface-validation.md) | 分开阅读 16 个样例的原接口判定、修订合同的纸面复验和当前程序证据 |
| v2 本机规则库、准确接受与移机打开 | [本机规则库](design/v2/local-rule-library.md)、[规则存储](design/v2/rule-storage-and-versioning.md) | `LocalRuleLibrary`、`LocalRuleLibraryLedger`、`HostStorageServlet`。原包、本机独立接受、未知查证、新建和打开复核由 `LocalRuleLibraryTest`、`CampaignRuleBindingIntegrationTest` 定位 |
| v2 世界节点、局部网格与多场景焦点 | [空间与焦点](design/v2/world-and-scene-spaces.md)、[运行与事务](design/v2/runtime-and-transactions.md) | `CampaignSpaceData/Commands`、`CampaignDomainEngine`；多场景单焦点、DM 旅行、独立时钟、唯一安置、全 pending 恢复。测试见 `CampaignSpacesIntegrationTest` |
| v2 三维体积、区域、可见与合法安置 | [空间与区域](design/v2/spatial-and-area-contract.md)、[跨空间因果作用域](design/v2/world-and-scene-spaces.md#causal-scope) | 目标 SpatialProof、区域裁切、联动占位、强制返回及同步被动因果。当前运行仍是二维网格 |
| v2 自然日、旅行食水、黎明与个人期限 | [时间与日历](design/v2/time-and-calendar.md) | 目标空间日历、PersonalTime、各类发生与转移。由实际时间和领域消费者各自验收 |
| v2 实物、魔法物件与符文 | [物件与魔法物品](design/v2/objects-and-magic-items.md)、[施法扩展](design/v2/spellcasting-extension-contract.md) | 目标实物位置与互动、同调与来源、Wand/Glyph 的实例资源、触发、施法和恢复 |
| v2 schema 2 作者包、最小组合链路、字段覆盖与失败向量 | [最小组合链路验证](design/v2/schema-2-composition-validation.md)，再读模组接口基线 | `module/composition/Schema2CompositionKernel.java`；测试在 `module/composition/`，独立向量在 `src/test/resources/schema2-composition/`；有限纯内核不授予安装或运行资格 |
| schema 2 原始 ZIP、生产摘要、安装、独立接受与提交查证 | [制品合同](rules/schema-2-rule-artifacts.md)、[安装与接受](rules/schema-2-installation-and-acceptance.md)；存储见[来源账本](rules/schema-2-source-ledger.md) | `module/composition/ProductionRuleArtifact.java`、`ProductionSchema2Projection.java`；`offline/rules/Schema2SourceInstallation.java`、`JdbcSchema2RuleSource.java`；来源 V003—V012；资格门与生产摘要独立于验证内核 |
| schema 2 检定、装备与健康作者模型、纯组合及真实来源 | [作者来源合同](rules/schema-2-foundation-authoring.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CharacterSourceRepository.java`、`JdbcSchema2ExecutionSourceRows.java`；`Schema2CheckCompositionTest`、`Schema2EquipmentHealthCompositionTest`、对应来源/结构测试；来源 V006/V007；运行镜像、事实存储与角色基础消费者见下方入口 |
| schema 2 来源读取、组合快照、原子转存、收据和恢复绑定 | [来源到运行快照](rules/schema-2-runtime-snapshot.md) | `persistence/JdbcSourceSchema2Repository.java`、`JdbcSchema2RuntimeSnapshotRepository.java`、`JdbcSchema2SnapshotControlRepository.java`、`service/Schema2SnapshotTransferService.java`；运行 V021/V023/V024/V027/V028/V030/V032/V035/V037/V039；对应测试及 `Schema2ToolCompositionTest`、`JdbcSchema2ToolSnapshotTest`、 `ProductionSchema2StoredProjectionTest`；INTERNAL 材料不授予游戏执行资格 |
| schema 2 检定、装备与健康规则镜像及类型化角色事实 | [角色事实存储](rules/schema-2-character-fact-storage.md) | `persistence/JdbcSchema2EquipmentSnapshotRows.java`、`JdbcSchema2GrowthSnapshotRows.java`、`JdbcSchema2CharacterRuntimeRepository.java`；运行 V028—V031；对应快照、历史和 schema 测试；`Schema2CharacterRuntime` 拥有类型/结构/邻接/容量，仓储适配追加/直接恢复，规则语义由领域消费者验证 |
| schema 2 初始语言／工具授予、真实来源、跨模组槽和角色计划 | [初始授予合同](rules/schema-2-proficiency-grants.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CharacterSourceRepository.java`、`service/Schema2InitialProficiencyRules.java`、`Schema2InitialCharacterPlanService.java`；固定向量在 `src/test/resources/schema2-grants/`，对应组合、存储与计划测试；内部只读计划不等于业务初始化 |
| schema 2 角色成长、子职业/ASI/专长/多职业、资源、标准休息和局部历史恢复 | [成长与资源合同](rules/schema-2-character-statistics.md)、[休息合同](rules/schema-2-character-rest.md) | `service/Schema2CharacterProgressionRules.java`、`Schema2GrowthRules.java`、`Schema2CharacterRestRules.java`、`Schema2CharacterHealthRules.java`、`Schema2CharacterRuntimeService.java`、`persistence/JdbcSchema2CharacterRuntimeRepository.java`；来源 V005、运行 V027；单角色 INTERNAL，独立成长/休息入口不执行持续效果，完整战役/Host 仍关闭 |
| schema 2 法师短休奥术回想与每日使用事实 | [奥术回想合同](rules/schema-2-arcane-recovery.md) | `service/Schema2ArcaneRecoveryRules.java`、角色休息/服务及角色仓储；运行 V041；对应规则、真实服务、恢复/故障及 schema 测试；仅明确 AUTOMATIC 声明可用，旧 BLOCKED 不自动升级 |
| schema 2 检定、装备实例与护甲限制、伤害/治疗/死亡豁免 | [角色基础合同](rules/schema-2-character-foundations.md) | `service/Schema2CharacterCheckRules.java`、`Schema2CharacterEquipmentRules.java`、`Schema2CharacterHealthRules.java`、`Schema2CharacterRuntimeService.java`；类型化来源、镜像及局部历史；来源 V006/V007、运行 V028—V031，保持单角色 INTERNAL 边界 |
| 新旧业务共同写入、永久守卫、事务故障与初始化准备 | [共同业务事务](rules/business-write-transactions.md)、[运行设计](design/v2/runtime-and-transactions.md) | `persistence/BusinessWriteTransactions.java`、`JdbcSchema2InitialPlanRepository.java`、`service/Schema2InitializationPreparationService.java` 与 `Schema2SnapshotTransferService.java`；事务 owner 清单及对应测试见共同合同；准备事实不授予完整游戏执行资格 |
| v2 机制接入、责任清单、支持与验收判定 | [机制接入责任清单](design/v2/mechanism-integration-checklist.md)，再读其引用的领域合同 | 逐项定位定义、真实消费者、状态/历史/恢复及验证证据；清单不是已实现机制注册表 |
| schema 2 角色事实、战斗历史与准备身份 | [角色事实存储](rules/schema-2-character-fact-storage.md)、[战斗合同](rules/schema-2-combat.md) | `service/Schema2CharacterRuntime.java`、`Schema2CombatHistory.java`、`Schema2PreparationIdentity.java`；领域类型拥有结构、邻接和容量校验，JDBC 仓储和 SQLite 聚合负责各自读写 |
| 战士 Extra Attack、共同特性支持、真实成长与多击恢复 | [有限额外攻击合同](rules/schema-2-extra-attack.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2FeatureSupport`、`Schema2GrowthRules`、`Schema2CombatRules/StateValidator`；`CampaignExtraAttackIntegrationTest`、`tools/storage/browser-extra-attack.cjs`，沿实际规则库导入/接受/组合、正常升级和完整文件链验收；不开放 V04 的擒抱相关机制 |
| 战士一级 Second Wind、附赠动作治疗与休息恢复 | [有限回气合同](rules/schema-2-second-wind.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2CombatRules/History`、`Schema2FeatureSupport`；`CampaignSecondWindIntegrationTest`、`tools/storage/browser-second-wind.cjs`，正常取得、原骰/资源/治疗同根、保存 END 重开；仅 SQLite 单职业战士 1–6 级 |
| 勇士 Improved Critical、武器重击与 Shield 后判定 | [有限精通重击合同](rules/schema-2-improved-critical.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2FeatureSupport`、`Schema2GrowthRules`、`Schema2ActionFlowRules`；`CampaignImprovedCriticalIntegrationTest`、`tools/storage/browser-improved-critical.cjs`；真实勇士授予及普通/OA/Ready 共用结算、未决反应完整恢复，限单职业战士 3–6 级近战武器 |
| 基础倒地姿态、爬行与起身 | [有限倒地合同](rules/schema-2-prone.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2CombatRules`、`Schema2ActionFlowRules`、`PlayerCandidateFactory`；`Schema2ProneRulesTest`、`CampaignProneIntegrationTest`、`PlayerProneCandidateTest`、`tools/storage/browser-prone.cjs`；ACTIVE 规则角色及 Ready 移动、探索起身与离场门，原包不变；探索爬行/推撞/擒抱未接入 |
| schema 2 战斗作者、近战武器、步行来源与镜像 | [战斗作者合同](rules/schema-2-combat-authoring.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2CombatSourceRows.java`、`JdbcSchema2CombatSnapshotRows.java`；来源 V008、运行 V032；`Schema2CombatCompositionTest` 和对应来源/快照/结构测试 |
| schema 2 内部战斗、先攻/回合、近战/移动/OA、倒地/掉落与秒时钟 | [战斗合同](rules/schema-2-combat.md) | `service/Schema2CombatRuntimeService.java`、`Schema2CombatRules.java`、`Schema2CombatState.java`、`Schema2CombatTimeRules.java`、`persistence/JdbcSchema2CombatRuntimeRepository.java`；运行 V033/V034；对应服务/规则/历史测试；同战役独立 prepared 角色、同规则/同秒，不开放 Host/完整基础包 |
| schema 2 施法作者、法术书/准备、Shield/Counterspell/Ready、抗魔区域与探索 | [施法作者合同](rules/schema-2-spell-authoring.md)、[有限施法合同](rules/schema-2-spells.md) | `module/composition/Schema2CompositionKernel.java`、`persistence/JdbcSchema2SpellSourceRows.java`、`JdbcSchema2WizardSourceRows.java`、对应 SnapshotRows、`service/Schema2SpellRules.java`、`Schema2WizardSpellbookRules.java`、`Schema2ActionFlowRules.java`、`Schema2OngoingEffectRules.java`、`Schema2ExplorationRules.java`、`Schema2CombatRuntimeService.java`；来源 V009/V010、运行 V035—V038；Spell/Wizard 组合、来源/镜像、法术书、反应及运行恢复测试；生命领域一级和单职业法师 1—5 级保持 INTERNAL，未实现法术、完整 Host/base/archive 2 仍关闭 |
| v2 攻击、移动、施法、反应、暂停与继续 | [动作继续协议](design/v2/action-continuation-protocol.md)、[因果作用域](design/v2/world-and-scene-spaces.md#causal-scope) | 每个规则场景至多一条未决流。现行焦点推进及目标受限因果继续按各自采用边界判断 |
| SQLite 可选玩家入口、审批、控制/共享、有限动作、裁定与重连 | [运行合同](storage-vitality-loop.md)、[安全](security.md)、[原型验证](testing.md#玩家参与原型验证) | `CampaignParticipationData/Commands`、`CampaignAdjudicationCommands`、`PlayerGatewayService`、`PlayerCampaignProjection/PlayerCandidateFactory`、`PlayerServlet/HostPlayerServlet`、`PlayerListenerConfiguration/PlayerBoundaryFilter`；`CampaignParticipationIntegrationTest`、`PlayerGatewayServiceTest`、`PlayerProjectionAndCandidatesTest`、`PlayerParticipationAcceptanceTest`、`PlayerBoundaryFilterTest`、`PlayerHttpIntegrationTest`、`PlayerProcessRecoveryIntegrationTest`；默认关闭，真实 LAN 设备验收独立 |
| v2 施法授权、准备、升环/仪式、组件与后续机制扩展 | [施法扩展合同](design/v2/spellcasting-extension-contract.md)，再读有限施法及公共协议 | 目标接口与优先级；复用角色资源、唯一动作流、检定/伤害和持续效果 owner；按具体分支补足作者、消费者及恢复，不从元数据或设计取得执行资格 |
| v2 持续效果、压制、形态、召唤与疾病 | [持续效果协议](design/v2/ongoing-effect-protocol.md)、[角色状态与历史](design/v2/character-state-and-history.md) | 区分原有限消费者与目标区域、真实抗魔、形态健康、群组先攻、疾病及跨空间伤害闭包 |
| schema 2 有限怪物、吐息、充能、休息、完整目标、共享伤害与专注 | [有限怪物合同](rules/schema-2-monster-breath.md) | `module/composition/Schema2MonsterCodec.java`；`persistence/JdbcSchema2MonsterSourceRows.java`、`JdbcSchema2MonsterSnapshotRows.java`、`JdbcSchema2MonsterBreathRows.java`；`service/Schema2CombatRuntimeService.java`、`Schema2SaveDamageRules.java`、`Schema2MonsterRestRules.java`、`Schema2CombatState.java`；来源 V011、镜像 V039、事实 V040；`Schema2MonsterBreathRulesTest` 及 Monster 组合/来源/镜像/服务/历史测试；INTERNAL 点格机制，完整 Host/base/archive 2 仍关闭 |
| schema 2 法术来源范围豁免伤害与燃烧之手 | [法术共享伤害合同](rules/schema-2-spell-save-damage.md) | `Schema2SpellRules`、`Schema2ActionFlowRules`、`Schema2SaveDamageRules` 与场景/批次仓储；来源 V012、运行 V042；对应组合、来源/镜像、真实服务及 schema 测试；真实施法来源与怪物动作互斥，有限范围不授予其他法术执行资格 |
| v2 多目标公共接口、体积空间、伤害骰后反应与完整保存 | [多目标豁免伤害](design/v2/multi-target-save-damage.md)，再读动作继续及持续效果协议 | 区分已采用的有限怪物合同与公共目标；原 PFE/Cutting Words golden、通用体积、完整文件和 Host 仍须各自消费者与验收 |
| SQLite 当前有限战役、角色/冒险/战斗、完整文件与同版恢复 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md)、[测试入口](testing.md#sqlite-结构完整文件与运行验收) | `HostStorageServlet` / `StorageApplicationListener`（配置与 readiness）；`CampaignStorageService` / `ManagedCampaignFiles`（准备前缀、Windows NTFS/ACL）、`CampaignDomainCommands`、`CampaignDomainEngine`、`CampaignDomainData/Codec/Validation`、`CampaignDomainRepository`；领域、生命周期、准备、Windows/进程故障测试；`DND_STORAGE_ROOT` 显式启用，不连接运行 MySQL |
| SQLite 检定成功/失败分支、健康/物品/消息后果 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md) | `service/storage/CampaignCheckEffects.java`、`CampaignDomainCommands.java`；`CampaignCheckEffectsTest`；两分支全部预检后再骰，同根事务和完整文件，不接受任意算法 |
| SQLite DM-managed 自由卡、基础字段、职业等级/熟练、MANUAL/SAVING_THROW 与共享网格 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md) | `service/storage/CampaignManualData.java`、`CampaignManualCommands.java`、`CampaignDomainCommands.java`；Host UI 与 `CampaignManualIntegrationTest` 覆盖有序效果、回滚、完整文件与混合网格。DM 裁定与自动规则分别校验 |
| 有限生产 schema 2 组合、初始化模板、目录与限制 | [规则包指南](../rule-packages/srd51-runtime/package-guide.md)、[领域接合表](design/v2/domain-integration.md) | `BundledCampaignRules` 提供交付原包候选，`CampaignRuleSupport` 检查应用执行范围，`CampaignRuleContext` 提供初始化与绑定投影，`Material` 保存解释合同、准确绑定与完整行链。测试见 `BundledCampaignRulesTest`、`CampaignRuleSupportTest`、`CampaignRuleContextTest` |
| v2 完整战役文件、运行空间与生命周期 | [规则存储](design/v2/rule-storage-and-versioning.md)、[完整文件](design/v2/save-and-recovery.md)、[生命周期](design/v2/runtime-and-transactions.md)、[领域接合表](design/v2/domain-integration.md) | `CampaignCodec`、`CampaignStorageService`、`CampaignDomainValidation`。本机 `RULE_BOUND_CAMPAIGN` format 3 保存完整冻结规则与业务聚合，接合 P0—P3、END 和显式恢复/放弃。周期 AUTO/轮换、通用 SRD MySQL archive format 2 独立验收 |
| v2 JSON 建表、存储数据覆盖与替换步骤 | [结构声明与计划](design/v2/storage-schema-and-planning.md)、[数据映射](design/v2/storage-data-map-and-rollout.md)、[领域接合表](design/v2/domain-integration.md) | `resources/storage/campaign-schema.json`、`StorageSchemaCatalog`、`StoragePlan`、`SqliteSchemaVerifier`、`CampaignDomainRepository`；结构/投影/聚合与未来写入足迹共同验证；旧模式保管和剩余采用不由声明存在推定 |
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
| 新增完整规则领域、覆盖差距、发布门 | [机制覆盖矩阵](rules/srd-5.1-coverage.md)、[完整规则目标](rules/srd-5.1-complete.md)；[架构](architecture.md) §2—3 | 按机制族核对数据、真实执行和状态/恢复；只提升有实际消费者和证据的准确范围，不为未来领域编造类名 |
| Agent、skill、项目导航、文档 | [AGENTS.md](../AGENTS.md)、本文 | 先选本文相关入口；本地 `dnd-project-start` 可辅助导航；不预读业务源码 |

## 使用与维护

- 按 [AGENTS.md](../AGENTS.md) 恢复上下文并从本文选择相关入口；本地存在 `dnd-project-start` 时可辅助定位，缺失时直接使用本文。skill 不是自动执行的启动脚本。
- 入口变化时同步修正表中路径。规则正文、安全/事务不变量、账号职责、验证方法只在对应权威文档维护，不在 skill 中复制第二份。
- 产品和跨领域设计通过 [v2 设计导航](design/v2/README.md) 定位相关专题；历史提案、任务状态和测试报告不能覆盖正式设计或证明已实现。
- 分域 JSON 作者包与维护流程按任务查阅[规则制品](rules/schema-2-rule-artifacts.md)、[安装与接受](rules/schema-2-installation-and-acceptance.md)及[测试指南](testing.md)，不从本地 Agent 工作区补充合同或验证步骤。
- 动态 Issue/PR、分支、历史测试结果和部署状态不缓存于本文。
