# v2 存储职责图与替换路线

本页维护当前 SQLite 结构/领域/文件的 owner 映射，以及原 MySQL 能力与真实数据保管的
核对入口。结构格式见[结构合同](storage-schema-and-planning.md)，事务与完整文件见
[运行](runtime-and-transactions.md)和[保存](save-and-recovery.md)。[领域接合表](domain-integration.md)
列出当前生产入口、剩余差异和退役候选待验证的断言。

只支持当前应用声明模型，不承诺跨版本工作库升级/存档转换。SQLite 是当前应用的唯一运行库，`DND_STORAGE_ROOT` 只控制其位置。
代码接合、数据库迁移、真实数据处置、部署和默认切换分别判断。

## 当前实现与权威

| 边界 | 实际入口与当前选择 |
|---|---|
| 准确规则材料 | `LocalRuleLibrary` 保管原包、导入及本机接受。`CampaignRuleSupport` 校验组合的应用资格，`CampaignDomainData.Material` 保存解释、准确绑定、全部规则行、条目和来源链 |
| 应用结构 | `storage/campaign-schema.json` → `StorageSchemaCatalog` → `StoragePlan`/`SqliteSchemaCompiler`；`SqliteSchemaVerifier` 核现有结构和 seed |
| 业务执行 | `CampaignDomainCommands` 与无连接 `CampaignDomainEngine`，复用现有纯领域规则；自由卡用独立 DM-managed typed 领域 |
| 共同事务 | `CampaignStorageService` 持根/目录锁、串行接纳，`BEGIN IMMEDIATE` 一次提交全部聚合/关系投影、根子历史、revision 和接受 |
| 文档/关系存储 | `CampaignDomainRepository` 写 closed typed TEXT 和关系投影，调用方拥有事务；读取逐行核验，不修复坏索引 |
| 文件 | `CampaignCodec`、`CampaignDomainCodec/Validation` 保存 `RULE_BOUND_CAMPAIGN` format 3，包含全部空间、唯一安置、焦点和各 pending。打开按文件绑定重建准确组合，活动和同版残留使用冻结规则 |
| 原 MySQL/来源服务 | 独立维护原 SQL、批准清单、账号、来源安装与接受和内部快照。旧档随匹配的发行物保管。当前代码和交付边界见[依赖清单](#mysql-dependency-inventory) |

## 当前物理分片与唯一状态 owner

下表按应用 JSON 的实际分片命名。聚合里的资源、装备、动作等 typed 状态是其唯一事实；
同名关系表是独立核验的投影，不是另一份可写 owner。物理分片粒度不等于文件分区或
Java 责任类型，也不要求为每个叶字段保留旧 MySQL 子表。

| 分片/表 | 内容、owner 与文件落点 |
|---|---|
| `core`：core_control/core_storage_module/core_command/core_save/core_player_control | 本地 run、模型、计划、模块、epoch、revision、业务接受、保存和玩家安全控制。Service 与 Repository 协调，技术控制留在工作库 |
| `rules-runtime`：rules_runtime_material | 完整 production Material；文件 domains.material 同时保留准确成员、最终规则、entries 和完整链 |
| `characters-runtime`：characters_runtime_character | Character 的名称/类型/active、InitialPlan、完整 History/current；角色 typed 聚合拥有全部成长/HP/生命骰/资源/装备/书籍/休息事实 |
| `scenes`：scenes_world/scenes_space/scenes_clock/scenes_placement | 严格世界聚合及场景、时钟和唯一安置的核验投影。文件保存节点、位置、简单物品、焦点及各空间事实 |
| `combat`：combat_scene | 完整场景/战斗 History/current；含原骰/先攻/轮回合、部署、唯一动作树/窗口、施法/效果/区域、怪物及全部历史依赖 |
| `history-runtime`：history_runtime_root | 连续战役根、原操作与意图、subject、typed 前后事实、角色子操作和场景因果。文件 `domains.roots` 保存全部根，`campaign.eventTail` 核对数量 |
| `resources`：resources_character | 角色 current 资源准确身份/余额/上限/无限标记的关系投影；生命骰/休息及完整资源历史仍在所属角色聚合 |
| `equipment`：equipment_instance | 角色装备实例/持有/穿戴/地面 typed 投影，与角色 current/历史逐行核对 |
| `spellcasting`：spellcasting_book | 角色真实已学/准备/恢复事实投影，不从法术目录补人物状态 |
| `actions`：actions_frame | 场景的完整 flow 帧关系投影；父子/返回点/窗口/已消费成本/唯一继续点保留在场景聚合与全历史 |
| `effects`：effects_instance | 场景持续效果投影，完整来源/目标/压制/专注/终结历史由场景聚合拥有 |
| `monsters`：monsters_campaign/monsters_instance | 战役怪物当前事实及场景实例的核验投影。动作、休息及共享伤害历史随场景完整保存 |
| `manual`：manual_character | 独立 DM-managed 自由卡的核验投影。自由卡与规则角色共享空间安置，DM 裁定场景的模式和占位归空间领域 |
| `runtime`：runtime_profile | 当前有限运行范围的根/静态支持标记，依赖上述完整未来写入足迹，不随当前有无角色/施法者裁剪 |

所有列/键/约束/索引由应用 JSON 完整声明。正文 codec 登记闭合 record/enum，拒绝动态类、
未知/缺失字段、错误标量、重复映射和坏数值/Unicode；无自由属性袋。每次实际读取同时核
全部投影和整场根/子引用，不能损坏后重建索引再继续。新增聚合字段/投影必须同时更新
声明、codec、仓储、验证、完整文件与相关测试。

## 字段、历史与文件映射

| 数据类别 | 当前 owner/保留范围 | 直接恢复要求 |
|---|---|---|
| 稳定战役/角色/对象键 | Snapshot、InitialPlan、角色/场景/世界/manual 聚合 | 保留原值，不按名字或插入顺序生成替代 |
| schema-2 prepared Identity/事件 operation/version | 初始/角色/场景历史中被引用的稳定业务事实 | 文件保留原值，不重建为实际 core run 或旧 HTTP 执行权限 |
| 实际 run/epoch/revision/接受与保存收据 | core 本地控制 | 文件排除，新打开建立新控制，残留完整读验后新 epoch；不从文件取得旧请求资格 |
| 初始计划与初始卡 | 初始准确来源/槽/选择/授予、标准数组 INITIALIZED 事件 | 初始与 current 分开；不重新创建/授予/掷骰解释当前值 |
| 属性/成长/ASI/子职业/特性/熟练 | 角色 current 与全部命令/前后历史 | 保留选择与原骰/来源；未实现 OPTION 元数据不自动获得，不靠导入重算丢失事实 |
| HP/临时HP/死亡/生命骰/资源/休息/每日事实 | 所属角色聚合；资源表仅投影 | 不补治疗、休息、死亡豁免、充能或位数；原剩余/消耗及准确日界时间保留 |
| 装备实例/法术书 | 所属角色聚合及全部历史 | 取得/消耗/掉落/穿戴和书籍学习/准备恢复直接写既有事实，不从目录发物品/法术 |
| 全部检定骰、选择/DC/修正、两分支计划与实际效果 | 角色/检定 typed 结果、根历史及受影响世界/健康/manual 聚合 | 两分支完整预检先于真 RNG；所选效果按原序同根提交，恢复不重应用 |
| 场景/先攻/轮回合/时钟 | 完整 combat History/current，角色时间由 engine 同根协调 | 不用多个可漂移时钟拼出“最新”；原部署/单位/阵营/原骰全部核验 |
| 动作/反应/Ready/施法/共享伤害 | 场景所有 flow/帧/窗口/候选/决定/目标证明/原骰/成本/packet 与全历史 | 已关闭窗口仍可能有必需后缀；保留唯一叶子与已消费前缀，恢复不 PASS/继续/重复扣费 |
| 效果/专注/压制/怪物 | 场景完整实例、当前值/期限/全部原因与历史 | 无贡献、被压制、终结但被引用的实例仍保存，不补 tick 或结束回调 |
| 节点/位置/简单物品/profile/消息 | 世界字段、根 typed 前后事实和 manual 独立模型 | 精确邻接/归属/数量，检定消息归原根，不用 NOTE 代替未知类型 |
| 全部根子历史 | roots 与角色/场景/manual 已记录业务事实 | 连续根、唯一子归属、完整 before/after/原骰；不按 UI 近期列表截断 |

当前文件保存完整冻结材料、解释和来源链。`Material.restore` 从冻结行重建投影并核对准确指纹及
机制闭包。打开文件时，准确匹配标准绑定的成员使用随附四包，其余绑定使用本机已接受的原包。重建的组合须与冻结材料逐项一致。DDL、结构计划与本地
执行收据由私有工作库独立保管。

<a id="mysql-dependency-inventory"></a>

## MySQL 依赖与保管清单

| 交付对象 | 责任与入口 |
|---|---|
| Web 与默认构建 | `StorageApplicationListener` 从 Context 参数、`DND_STORAGE_ROOT` 或运行身份解析私有根。`CampaignStorageService` 与 `LocalRuleLibraryLedger` 通过 SQLite JDBC 连接；默认 Maven 解析 SQLite 驱动及 HTTP 测试容器。旧 Host 路由返回 404。 |
| 战役与规则 | SQLite 在私有工作库持有冻结规则、角色与场景事实、投影、根和幂等收据。本机规则库负责原包、审阅与接受。构建生成的规则身份清单和准确原包随 WAR 交付。 |
| 作者输入 | `RulePackageCli` 的 build、verify 和 schema2-verify 读取本地制品。DRAFT 语言/工具的独立作者向量、严格读取与可玩组合的资格门独立核验。 |
| 旧运行库与来源库 | JDBC 适配器、迁移/权限脚本、诊断、安装/接受票据及内部快照工具由持有者随匹配旧版保管。两个迁移账本及对应原件、备份、旧文件和最小权限核验见[数据库合同](../../database.md#迁移合同)。 |

### SQLite 仍使用的领域依赖

| 当前消费者与共享事实 | 拆分时保护的边界 |
|---|---|
| `BundledCampaignRules`、`LocalRuleLibrary`、`CampaignRuleSupport` 和 `CampaignDomainData.Material` 使用 `ProductionRuleArtifact`、`Schema2CompositionKernel`、`ProductionSchema2Projection`、`Schema2RulesBinding` 及其规则/条目/来源链类型。`Schema2CompositionKernel` 还使用 `AdvancementValueProfile` 和 `ResourceRecoveryProfile` | 保留与 JDBC 无关的原包读取、规范编码、按顺序组合、冻结材料重建和成员/最终规则/完整链摘要。SQLite 本机接受由 `LocalRuleLibrary` 的独立账本承担 |
| `CampaignDomainCommands` 和 `CampaignSpaceCommands` 调用 `CampaignDomainEngine`。`CampaignDomainValidation` 将保留事实投影为 `CharacterRecord`，通过领域命令核验历史。引擎使用独立的纯 `Schema2CharacterMaterial`、`Schema2CharacterRequest` 和 `Schema2CombatRequest`，执行 `Schema2*Rules` 并生成和重验请求摘要 | 保留已分离的纯引擎、材料、请求和规则。保持事件命令、历史邻接、前后状态、原骰、类型化事实和请求摘要。MySQL 专属连接、SQL、事务守卫及操作收据随对应适配器退出产品交付 |
| `CampaignRuleContext.initialPlan` 调用纯 `Schema2InitialCharacterPlanService.prepareMaterial`。独立作者场景入口 `tools/rule-packages/ModuleScenario.java` 也引用 `InitialPlan`。`CampaignDomainCodec` 登记 `initial.Plan`、`initial.InitialPlan`、`preparation-identity`、熟练授予、角色事实和战斗状态/历史记录 | 核对静态调用、独立场景及 codec 的反射登记。类型如需移动，同步当前 format 3 的标签、记录字段、事件与摘要。准备身份继续保留为领域事实，SQLite 的运行身份、epoch 与接受收据由自身控制表管理 |
| `CampaignDomainEngine`、`CampaignManualCommands` 与 `PlayerGatewayService` 等共用 `CharacterNamePolicy` | 保留名称校验的 Unicode、NFC 与控制字符边界 |

制品审计须从源码与构建输出复核领域类型、内部类、codec 登记和独立作者场景的需求。运行验证覆盖标准/自定义组合、历史、同版恢复及完整文件。

## 原 MySQL 清单的用途与接合差异

原表、迁移清单与校验 SQL 由对应版本保管。按业务事实核对旧数据的读取责任及当前能力，并按实际账本判断已应用的迁移。[数据库保管](../../database.md#迁移合同)

| 原责任 | 当前接合/保管方向 |
|---|---|
| 规则目录/语言工具/PARTITION/内部快照 | SQLite 冻结完整组合与链。本机规则库保管准确原包和接受，原 MySQL 来源、迁移和接受服务独立保管 |
| 原角色自由字段/职业等级/熟练/Manual/普通save/简易遭遇名单/阵营 | 由独立 DM-managed typed 领域逐项接合。当前入口、剩余差异和待验证断言见接合表 |
| schema-2 角色/资源/装备/健康/书籍/休息叶表 | 原纯规则/完整 typed History 复用到角色聚合，同根/事务/文件，不复制第二条 MySQL 服务事务 |
| schema-2 战斗/动作/效果/怪物叶表 | 完整场景 History 聚合及关系投影，同一角色健康/资源 owner；保留全部已完成/未决事实 |
| v1 检定/成功失败效果/物品/位置/消息 | `check-effects` 和 `manual-check` 对已支持的生命、物品、位置及消息后果完整预检，按序与根历史同事务提交，并随完整文件保存。旧 MySQL 直接定位和 `effect.set_entity_position` 的业务写入路径已移除。SQLite 的遭遇内直放与检定分支原子定位见[领域接合表](domain-integration.md#支持边界和设计修订) |
| 永久业务守卫/内部操作 | 当前独占根锁+BEGIN IMMEDIATE，状态/根/接受一次提交；未知显式恢复核库/全部收据，独立 C0/NOT_COMMITTED 方案已替换 |
| 旧 format 1/内部 format 2 片段 | 按原发行物和离线合同保管。当前 SQLite 工作台仅打开本版 format 3 完整文件 |

## 按当前合同交付与验收

1. **字段/owner/支持范围。** 将所有现行能力、稳定业务键、初始/current、原骰/审计/pending
   映射到严格聚合/投影/文件或有依据排除项。metadata 不执行，缺消费者不能用空表放行。
   自由卡/Manual 现行能力差距须在接合表逐项收敛，不因有限规则模板而默认降 scope。
2. **声明与真实引擎。** 纯 StoragePlan 无分配/连接/RNG，严格声明拒绝坏类型/键/依赖。
   真 SQLite 核 DDL/seed/事实/控制共同提交/回滚、DELETE/EXTRA/外键/UTF-8、锁与句柄。
3. **共同业务接受。** 单独 C0/未提交裁定不再作为门；当前所有真实写入口必须同锁/同
   BEGIN IMMEDIATE 一次写聚合/投影/根子历史/revision/接受。两分支/完整目标先于随机，
   响应未知不重试，recover 核真实库/全部事实与收据后新 epoch。
4. **完整创建/文件/残留。** 初始化结构/seed/规则/全事实同事务，commit 后实际读回，
   INITIAL 完整保存后执行。文件直接恢复与原库显式恢复分开；不重建 prepared 历史键、
   不补表/seed、不重播命令。独立字段/字节期望覆盖坏输入、坏投影和复杂 pending。
5. **本轮完整生命周期。** 角色/场景后果同事务，INITIAL/MANUAL/FINAL P0—P3、导入/
   下载/打开/删除/discard/恢复/准确清理必须逐窗口验证。每命令工作库耐久不是省略完整
   文件的理由；当前无需周期 AUTO 才运行，也不能因此推迟领域 END/故障闭包。
6. **后续自动检查点/玩家步骤。** 先测量完整规则/历史/pending、库/journal、各档案/临时/
   墓碑和内存/时延峰值，再确定阈值/增长/保留并实现。玩家步骤、新机制和完整 SRD
   另有产品/机制门，不从当前存储完成推导。
7. **应用装配。** WAR 显式注册 SQLite 的 Host 与玩家入口。验证默认根启动、无 JNDI、旧业务接口关闭；保留的 MySQL 资料单独维护。

## 配置、测试、打包与实际数据

`DND_STORAGE_ROOT` 选择 SQLite 私有根，应用管理 `runs/`、`saves/` 和独立 `rule-library/`。
本机规则库与运行工作库使用各自的存储和事务。

当前测试入口包括结构/引擎、BundledCampaignRules、CampaignDomainEngine、共享 SQLite
域/战斗/文件/生命周期、HTTP/安全和进程故障族。领域接合表列出自由卡和检定效果的当前入口、
剩余差异及待验证断言。退役候选应分别记录 SQLite 应用与平台锁、刷盘、低磁盘的实际验证结果。
可重复命令见[测试指南](../../testing.md)。

WAR 必含应用结构、严格聚合与领域代码、SQLite JDBC、Host 页面与脚本及 `storage/rules/` 的五份原包。
其他作者源/来源SQL/离线写入类、真实库/journal/存档、秘密/日志/机器路径仍排除。生产
资源变化须审计 WAR 字节/SHA-256/条目和内容；实际部署按[唯一部署流程](../../deployment.md)。

默认保管原 MySQL 运行/来源库、准确原包/接受账本和必要可读应用。保留数据库用前向
迁移，不改已应用摘要；重建/重置、真实导出恢复删除、来源安装接受、Tomcat启停/WAR
替换和业务写入分别授权。无跨版本兼容只免除格式延续义务，不减少真实数据责任。
