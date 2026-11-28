# Agent 项目地图

只负责定位，不替代 [AGENTS.md](../AGENTS.md) 或下表的权威合同。按任务选择下表入口并按需读取，
不要递归加载全部链接。本地可选的 `dnd-project-start` skill 只提供导航便利；项目 skill 位于
不跟踪的 `.agents/skills/`，缺失时直接使用本地图。
当前进度以 Git、代码、测试和经授权的外部证据为准，文件存在不证明功能发布、迁移执行或部署完成。

## 最小项目识别

- DM 固定 `http://127.0.0.1:8080`；SQLite 有默认关闭的独立 HTTPS 玩家原型，配置不创建监听；边界见[安全说明](security.md)，公网与公开 API 不在范围内。
- Java 21 / Maven WAR / Tomcat 10.1 / Servlet / 原生 JavaScript / SQLite-JDBC（唯一 Web 运行库）/ MySQL 8（离线来源与旧库维护）/ Gson。
- 原型阶段只保证当前版本，不承诺跨版本战役延续或旧存档支持；设计修订按 [AGENTS.md](../AGENTS.md#prototype-design-and-compatibility) 与[产品说明](product.md)处理。
- SQLite 标准战役默认组合 WAR 内的四个准确原包，自定义战役绑定本机接受的有序成员。两者均使用 `RULE_BOUND_CAMPAIGN` format 3。成员与重开核验见[运行合同](storage-vitality-loop.md#有限-schema-2-规则材料)。
- `dnd5e2014_srd51_se` release 1、canonical/archive 2：仍为 `DRAFT`；可协调演进，跨领域发布门前不可绑定战役或激活存档。发布门见 [完整规则目标](rules/srd-5.1-complete.md)。
- [产品说明](product.md)与 [v2 设计导航](design/v2/README.md)记录当前可修订的目标，和实现分开阅读；理解项目不依赖本地 Agent 目录。
- 请求解析 → `web`；校验/事务 → `service/storage`；工作库与规则账本 → `persistence/storage`；冻结目录/规范编码 → `module`；入口安全 → `security`。随机结果、版本推进与审计由服务器拥有，事务不得部分提交。

## 任务路由（只选相关行）

下表 Java 入口相对 `src/main/java/com/dndtool/`。测试从 `src/test/java/com/dndtool/` 下的
同包同名 `*Test.java` 开始。没有同名测试时按表中测试族搜索。可运行模组按内容选择[独立行为场景](../rule-packages/srd51-runtime/scenarios/README.md)。DRAFT 语言与工具作者源用[独立目录向量](rules/language-author-package.md#独立验证)。
原 MySQL 资料按数据持有者的匹配版本保管，SQLite 聚合与事务直接调用纯规则，采用范围见[领域接合表](design/v2/domain-integration.md)。

| 任务关键词 | 先读合同的相关部分 | 生产入口 / 测试定位 |
|---|---|---|
| SRD 5.1 原文、规则条款、章节与页码 | 先用[本地主题索引与分页文本](references/srd-5.1/README.md)，按主题或搜索结果只读相关页；歧义核对[本地 PDF](references/srd-5.1.pdf) | 重建与官方回源条件见[参考说明](references/README.md)；原文不替代冻结合同或证明项目已实现 |
| SRD 5.1 English semantic definitions and source coverage | [Semantic specification](semantics/srd-5.1/README.md), [coverage and review](semantics/srd-5.1/coverage.md) | Source behavior and labeled interpretations; semantic completeness and source review are independent of application contracts and runtime support |
| 产品范围、v2 总体设计、规则版本与存储方向 | [产品说明](product.md)、[v2 设计导航](design/v2/README.md)；按主题选一篇 | 区分当前设计选择、待细化与实际实现；按需求证据修订，不从设计记录推断迁移、发布或部署已完成 |
| v2 模组分层、应用机制与接口采用 | [模组分层](design/v2/module-layering.md)、[模组接口](design/v2/module-interface-baseline.md)、[采用边界](design/v2/adoption-boundaries.md) | 核心/机制/内容/UI、战役绑定、单一事实 owner。外部内容按应用模板和现有机制闭包校验 |
| 第四阶段的规则行为接口 | [行为接口验证](design/v2/rule-behavior-interface-validation.md) | 分开阅读 16 个样例的原接口判定、修订合同的纸面复验和当前程序证据 |
| v2 本机规则库、准确接受与移机打开 | [本机规则库](design/v2/local-rule-library.md)、[规则存储](design/v2/rule-storage-and-versioning.md) | `LocalRuleLibrary`、`LocalRuleLibraryLedger`、`HostStorageServlet`。原包、本机独立接受、未知查证、新建和打开复核由 `LocalRuleLibraryTest`、`CampaignRuleBindingIntegrationTest` 定位 |
| 内置规则包身份清单与损坏处理 | [随附规则材料](storage-vitality-loop.md#有限-schema-2-规则材料)、[WAR 审计](testing.md#6-war-审计) | `BundledRuleManifestGenerator` 生成清单，`BundledCampaignRules` 核验候选和组合。建档、重开与写入拒绝见 `CampaignRuleBundleIsolationTest`、`StorageHttpIntegrationTest` |
| v2 世界节点、局部网格与多场景焦点 | [空间与焦点](design/v2/world-and-scene-spaces.md)、[运行与事务](design/v2/runtime-and-transactions.md) | `CampaignSpaceData/Commands`、`CampaignDomainEngine`；多场景单焦点、DM 旅行、独立时钟、唯一安置、全 pending 恢复。测试见 `CampaignSpacesIntegrationTest` |
| v2 三维体积、区域、可见与合法安置 | [空间与区域](design/v2/spatial-and-area-contract.md)、[跨空间因果作用域](design/v2/world-and-scene-spaces.md#causal-scope) | 目标 SpatialProof、区域裁切、联动占位、强制返回及同步被动因果。当前运行仍是二维网格 |
| v2 自然日、旅行食水、黎明与个人期限 | [时间与日历](design/v2/time-and-calendar.md) | 目标空间日历、PersonalTime、各类发生与转移。由实际时间和领域消费者各自验收 |
| v2 实物、魔法物件与符文 | [物件与魔法物品](design/v2/objects-and-magic-items.md)、[施法扩展](design/v2/spellcasting-extension-contract.md) | 目标实物位置与互动、同调与来源、Wand/Glyph 的实例资源、触发、施法和恢复 |
| v2 schema 2 作者包、最小组合链路、字段覆盖与失败向量 | [最小组合链路验证](design/v2/schema-2-composition-validation.md)，再读模组接口基线 | `module/composition/Schema2CompositionKernel.java`；测试在 `module/composition/`，独立向量在 `src/test/resources/schema2-composition/`；有限纯内核不授予安装或运行资格 |
| 规则 ZIP、组合及准确接受 | [制品](rules/schema-2-rule-artifacts.md)、[本机规则库](design/v2/local-rule-library.md) | `ProductionRuleArtifact`、`Schema2CompositionKernel`、`LocalRuleLibrary` 与 `CampaignRuleBindingIntegrationTest` |
| 角色成长、检定、装备、健康、休息与初始授予 | [角色基础](rules/schema-2-character-foundations.md)、[成长](rules/schema-2-character-statistics.md)、[初始计划](rules/schema-2-proficiency-grants.md) | `CampaignDomainEngine`、`CampaignRuleContext`、`Schema2InitialCharacterPlanService`；查看相应纯规则及 SQLite 集成用例 |
| 规则角色步速、重甲门槛及豁免 | [步行来源与作者字段](rules/schema-2-combat-authoring.md#步行来源)、[装备](rules/schema-2-foundation-authoring.md#装备字段与完整引用) | `Schema2CharacterEquipmentRules.walkingSpeed` 供战斗、探索和玩家卡片共用。见 `Schema2DwarfMovementCompositionTest` 和 `CampaignProneIntegrationTest` |
| 回合预算、角色和规则怪物移动、动作继续、法术与范围 | [战斗](rules/schema-2-combat.md)、[法术](rules/schema-2-spells.md)、[怪物吐息](rules/schema-2-monster-breath.md)、[怪物与借机攻击](rules/schema-2-monster-attacks.md) | `Schema2CombatRules`、`Schema2ActionFlowRules`、`CampaignDomainCommands`。怪物按冻结步速移动见作者场景 `monster-turn-movement`，个人反应及存档恢复见 `PlayerParticipationAcceptanceTest` 和 `browser-player-monsters.cjs --movement`。 |
| 事务、私有库和完整文件 | [结构与运行](storage-vitality-loop.md)、[映射](design/v2/storage-data-map-and-rollout.md)、[故障验证](testing.md) | `CampaignStorageService`、`CampaignDomainRepository`、`CampaignDomainCodec`；`CampaignStorageServiceTest`、`CampaignStorageCrashTest`、`CampaignFileVectorTest` |
| 山丘矮人亚种、一级和逐级 HP | [强韧与冻结参数](rules/schema-2-character-statistics.md#山丘矮人强韧) | `CampaignRuleContext`、`Schema2GrowthRules`、`CampaignHillDwarfIntegrationTest`，覆盖按亚种创建、升级和同版恢复 |
| 技能检定的可选属性 | [玩法合同](rules/schema-2-character-foundations.md#技能搭配不同属性)、[作者字段](rules/schema-2-foundation-authoring.md) | `Schema2CharacterCheckRules`、`CampaignSkillAbilityIntegrationTest`，核对两种组合的实际属性、条件后果与重开 |
| DM 规则卡被动检定 | [结算与适用范围](rules/schema-2-character-foundations.md#被动检定)、[作者字段](rules/schema-2-foundation-authoring.md)、[作者场景](../rule-packages/srd51-runtime/scenarios/README.md#验证被动检定) | `Schema2CharacterCheckRules`、`CampaignPassiveCheckIntegrationTest`，核对冻结分数、两分支预检和无检定骰的完整文件重开 |
| 双方对抗检定、保持现状与最小获胜分差 | [玩法](rules/schema-2-character-foundations.md#对抗检定)、[作者字段](rules/schema-2-foundation-authoring.md#对抗最小获胜分差)、[验证](testing.md#对抗检定验证) | `Schema2CharacterCheckRules`、`CampaignContestCheck`、`CampaignContestCheckIntegrationTest`，检查双方独立原骰、分差裁定和完整文件重开 |
| 团队属性检定、至少一半成功、家规门槛 | [玩法](rules/schema-2-character-foundations.md#团队属性检定)、[作者字段](rules/schema-2-foundation-authoring.md#团队成功比例)、[验证](testing.md#团队属性检定验证) | `CampaignGroupCheck`、`CampaignGroupCheckIntegrationTest`，核对成员版本、逐人原骰、共同成败和结束后重开 |
| 矮人石工知识与历史检定 | [题材与结算](rules/schema-2-character-foundations.md#石工知识)、[作者字段](rules/schema-2-foundation-authoring.md#石工知识) | `Schema2CharacterCheckRules`、`CampaignDomainEngine`、`CampaignStonecunningIntegrationTest` 核对单次熟练替代、双分支和完整文件重开 |
| 战士一级 Second Wind、附赠动作治疗与休息恢复 | [有限回气合同](rules/schema-2-second-wind.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2CombatRules/History`、`Schema2FeatureSupport`。`CampaignSecondWindIntegrationTest` 和 `tools/storage/browser-second-wind.cjs` 覆盖 SQLite 单职业战士 1–6 级的授予、附赠动作与次数分别扣费、原骰及治疗同根、保存及重开。 |
| 勇士 Improved Critical、武器重击与 Shield 后判定 | [有限精通重击合同](rules/schema-2-improved-critical.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2FeatureSupport`、`Schema2GrowthRules`、`Schema2ActionFlowRules`；`CampaignImprovedCriticalIntegrationTest`、`tools/storage/browser-improved-critical.cjs`；真实勇士授予及普通/OA/Ready 共用结算、未决反应完整恢复，限单职业战士 3–6 级，含普通短弓和有界近战武器 |
| 角色短弓、箭支与骷髅远程动作 | [远程攻击](rules/schema-2-ranged-attacks.md)、[作者对照](../rule-packages/srd51-runtime/scenarios/README.md#ranged-weapons) | `Schema2CombatRules`、`Schema2ActionFlowRules`、`PlayerCandidateFactory`，定向验证见 `Schema2RangedAttackRulesTest`、`PlayerParticipationAcceptanceTest` 和 `browser-player-monsters.cjs --ranged`。 |
| 擒抱、推撞、挣脱与拖带 | [近战控制](rules/schema-2-melee-control.md)、[完整文件](design/v2/save-and-recovery.md) | `Schema2MeleeControlRules`、`Schema2ActionFlowRules`、`CampaignMeleeControlIntegrationTest`，核对双包分差、攻击次数、逐步拖带和反应继续 |
| 基础倒地姿态、爬行与起身 | [有限倒地合同](rules/schema-2-prone.md)、[战斗合同](rules/schema-2-combat.md) | `Schema2CombatRules`、`Schema2ExplorationRules`、`Schema2ActionFlowRules`、`PlayerCandidateFactory`；`Schema2ProneRulesTest`、`Schema2ExplorationRulesTest`、`CampaignProneIntegrationTest`、`PlayerProneCandidateTest`、`tools/storage/browser-prone.cjs`；ACTIVE/Ready 与探索姿态链、六秒活动及离场门，保留旧战斗预算和原包 |
| SQLite 可选玩家入口、审批、控制/共享、逐人揭示与武器攻击目标、有限动作、裁定与重连 | [运行合同](storage-vitality-loop.md)、[安全](security.md)、[原型验证](testing.md#玩家参与原型验证) | `CampaignParticipationData/Commands`、`CampaignAdjudicationCommands`、`PlayerGatewayService`、`PlayerCampaignProjection/PlayerCandidateFactory`、`PlayerServlet/HostPlayerServlet`、`PlayerListenerConfiguration/PlayerBoundaryFilter`；`CampaignParticipationIntegrationTest`、`PlayerGatewayServiceTest`、`PlayerProjectionAndCandidatesTest`、`PlayerParticipationAcceptanceTest`、`PlayerBoundaryFilterTest`、`PlayerHttpIntegrationTest`、`PlayerProcessRecoveryIntegrationTest`、`tools/storage/browser-player-monsters.cjs`；默认关闭，真实 LAN 设备验收独立 |
| v2 多目标公共接口、体积空间、伤害骰后反应与完整保存 | [多目标豁免伤害](design/v2/multi-target-save-damage.md)，再读动作继续及持续效果协议 | SQLite 完整文件支持已接入的有限怪物吐息和 Burning Hands。原 PFE/Cutting Words golden、通用体积及其产品入口仍须各自消费者与验收 |
| SQLite 当前有限战役、角色/冒险/战斗、完整文件与同版恢复 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md)、[测试入口](testing.md#sqlite-结构完整文件与运行验收) | `HostStorageServlet` / `StorageApplicationListener`（配置与 readiness）；`CampaignStorageService` / `ManagedCampaignFiles`（准备前缀、Windows NTFS/ACL）、`CampaignDomainCommands`、`CampaignDomainEngine`、`CampaignDomainData/Codec/Validation`、`CampaignDomainRepository`；领域、生命周期、准备、Windows/进程故障测试。SQLite 是唯一 Web 运行库，`DND_STORAGE_ROOT` 覆盖默认存储根。 |
| SQLite 检定成功/失败分支、健康/物品/位置/消息后果 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md) | `service/storage/CampaignCheckEffects.java`、`CampaignDomainCommands.java`；`CampaignCheckEffectsTest`、`CampaignPassiveCheckIntegrationTest`。两分支全部预检后结算检定及选中后果，共用根事务和完整文件 |
| SQLite DM-managed 自由卡、基础字段、职业等级/熟练、MANUAL/SAVING_THROW 与共享网格 | [运行合同](storage-vitality-loop.md)、[领域接合表](design/v2/domain-integration.md) | `service/storage/CampaignManualData.java`、`CampaignManualCommands.java`、`CampaignDomainCommands.java`；Host UI 与 `CampaignManualIntegrationTest` 覆盖有序效果、回滚、完整文件与混合网格。DM 裁定与自动规则分别校验。[自由卡检定草稿浏览器回归](../tools/storage/browser-manual-check-draft.cjs) |
| SQLite 完整战役文件、运行空间与生命周期 | [规则存储](design/v2/rule-storage-and-versioning.md)、[完整文件](design/v2/save-and-recovery.md)、[生命周期](design/v2/runtime-and-transactions.md)、[领域接合表](design/v2/domain-integration.md) | `CampaignCodec`、`CampaignStorageService`、`CampaignDomainValidation`。本机 `RULE_BOUND_CAMPAIGN` format 3 保存完整冻结规则与业务聚合，接合 P0—P3、END 和显式恢复/放弃。周期 AUTO/轮换仍属后续范围 |
| 旧 MySQL 规则目录、canonical、hash、发布身份 | [冻结 v1](rules/srd-5.1.md) §2/§11、[v2 目录](rules/character-catalog-v2.md)、[注册表批准](rules/module-release-identity.md) | `module/ModuleCatalog.java`、`module/BuiltinModuleReleaseRegistry.java`、`module/ModuleCanonicalEncoderV1.java` / `module/ModuleCanonicalEncoderV2.java`。v1 默认仅指注册表，SQLite 标准组合见上方有限生产 schema 2 路由 |
| 应用启动、存储根与就绪 | [架构](architecture.md)与[配置](configuration.md) | `web/StorageApplicationListener.java`、`web/HostStorageServlet.java`；`StorageApplicationListenerTest`、`StorageReadinessTest` |
| DRAFT 语言/工具作者 JSON、严格读取、纯分区投影 | [语言作者入口](rules/language-author-package.md)、[工具熟练目录](rules/tool-catalog-partition.md) | `tools/rule-packages/catalog-vector.py --check rule-packages/srd51-complete` 核对作者源。`module/CharacterCatalogAuthorPackageReader.java`、`module/CharacterCatalogPartition.java` 的定向测试为 `CharacterCatalogAuthorPackageReaderTest`、`ToolAuthorPartitionTest`，使用固定应用夹具 |
| 检定、骰子、物品和条件效果 | [运行合同](storage-vitality-loop.md) | `service/storage/CampaignDomainCommands.java`、`CampaignCheckEffectsTest` |
| 世界节点、位置与场景 | [运行合同](storage-vitality-loop.md) | `service/storage/CampaignSpaceCommands.java`、`CampaignSpacesIntegrationTest` |
| 完整规则 archive format 2 DRAFT 角色片段 | [format 2 角色状态](rules/archive-format-2-character-state.md) | 历史内部片段由匹配版本保管。当前完整文件往返与不支持格式拒绝由 `CampaignCodecTest` 和 `StorageHttpIntegrationTest` 验证 |
| 工作台、玩家页面和 API | [架构](architecture.md)与[运行合同](storage-vitality-loop.md) | 路由见 `src/main/webapp/WEB-INF/web.xml`。工作台与玩家页面分别由 `HostStorageServlet`、`PlayerServlet` 读取 `storage.html`、`player.html`。用 `HostHomeServletTest` 核对退役入口，用 `StorageHttpIntegrationTest` 和 `PlayerHttpIntegrationTest` 验证两侧的实际请求。 |
| loopback、Session、CSRF、安全头 | [架构](architecture.md)；[安全](security.md) | `security/HostBoundaryFilter.java`、`security/HostRequestSecurityFilter.java`；API 注册见 `src/main/webapp/WEB-INF/web.xml`（仓库根） |
| 构建、测试、WAR、部署、备份 | [测试](testing.md)；实际部署才读 [部署](deployment.md) / [备份恢复](backup-and-restore.md) | 仓库根 `pom.xml`、`config/`、`tools/`；使用独立临时 SQLite 根与候选容器 |
| MySQL 退役准备与单路线实施 | [批次与关联责任](design/v2/mysql-retirement.md)、[依赖清单](design/v2/storage-data-map-and-rollout.md#mysql-dependency-inventory)、[能力映射](design/v2/domain-integration.md#sqlite-retained-capabilities)、[测试处置与验收](testing.md#mysql-free-acceptance) | 从 `pom.xml`、`CampaignDomainEngine` 和 `CampaignDomainCodec` 追到实际调用者与 WAR 条目；按批次验证默认构建、SQLite 业务和跨环境文件恢复 |
| 新增完整规则领域、覆盖差距、发布门 | [机制覆盖矩阵](rules/srd-5.1-coverage.md)、[完整规则目标](rules/srd-5.1-complete.md)；[架构](architecture.md) | 按机制族核对数据、真实执行和状态/恢复；只提升有实际消费者和证据的准确范围，不为未来领域编造类名 |
| Agent、skill、项目导航、文档 | [AGENTS.md](../AGENTS.md)、本文 | 先选本文相关入口；本地 `dnd-project-start` 可辅助导航；不预读业务源码 |

## 使用与维护

- 按 [AGENTS.md](../AGENTS.md) 恢复上下文并从本文选择相关入口；本地存在 `dnd-project-start` 时可辅助定位，缺失时直接使用本文。skill 不是自动执行的启动脚本。
- 入口变化时同步修正表中路径。规则正文、安全/事务不变量、账号职责、验证方法只在对应权威文档维护，不在 skill 中复制第二份。
- 产品和跨领域设计通过 [v2 设计导航](design/v2/README.md) 定位相关专题；历史提案、任务状态和测试报告不能覆盖正式设计或证明已实现。
- 分域 JSON 作者包与维护流程按任务查阅[规则制品](rules/schema-2-rule-artifacts.md)、[安装与接受](rules/schema-2-installation-and-acceptance.md)及[测试指南](testing.md)，不从本地 Agent 工作区补充合同或验证步骤。
- 动态 Issue/PR、分支、历史测试结果和部署状态不缓存于本文。
