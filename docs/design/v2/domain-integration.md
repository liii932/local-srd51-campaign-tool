# 当前版本全领域接合与验收清单

本清单依据生产入口、SQLite 领域实现及定向测试入口，核对当前保留的业务能力及其验收责任。
已退役路线的交付边界和既有资料保管见 [MySQL 退役](mysql-retirement.md)。

## 领域与依赖

下表描述领域职责与目标验收。具体已接入的路径、替代和差距见 [SQLite 保留能力](#sqlite-retained-capabilities)。

| 领域 | 用户能力与入口 | 权威与关联 | 接合要求与验收 |
|---|---|---|---|
| 规则选择 | 选择当前准确规则组合、查看规则 | 完整规则、成员与覆盖链；所有领域依赖 | 声明闭包、准确材料、不可执行规则拒绝；普通命令不回来源库 |
| 战役与角色 | 新建、PC/NPC、改名、离场恢复、角色卡 | 战役根、初始卡与当前角色；历史 | 应用入口创建和再次读取，名字Unicode与版本边界 |
| 授予与成长 | 语言工具、职业、子职业、ASI/专长、多职业 | 初始计划、成长事实、规则 | 合法选择、非法引用与等级/资格；完整保留初始和当前事实 |
| 资源与休息 | 消耗、生命骰、短/长休、奥术回想 | 角色资源、累计经历时间和休息资格由角色拥有 | 核准确时间域、休息资格与间隔，失败无消耗 |
| 物品与装备 | 简单物品数量、实例装备、穿脱 | 角色物品、规则、护甲/组件 | 数量与资格边界，战斗/施法读取同一装备 |
| 检定与健康 | 主动掷骰、被动分数、团队属性检定、对抗检定、伤害/治疗/临时 HP、死亡豁免 | 服务器原骰或确定性分数、角色 HP、历史 | 先校验请求与各分支，同请求复用结果，旧版本不写入 |
| 场景与位置 | 节点图、局部网格、安置交接、DM 切焦点 | 世界节点、场景、唯一安置、焦点和各空间逻辑时钟 | 完整验证位置与焦点代次，后台冻结，全部空间直接恢复。见[世界与场景](world-and-scene-spaces.md) |
| 战斗与动作 | 先攻回合、攻击、移动、OA、Ready、反应继续 | 场景、角色、规则和所属空间的动作流 | 焦点空间推进，受影响角色共同提交，全部未决窗口直接恢复 |
| 施法 | 书籍、准备、位数及已支持法术 | 角色资源/装备、动作、规则 | Bless/Cure Wounds/Shield/Counterspell/Burning Hands 的实际支持边界 |
| 持续效果 | 专注、到期、叠加与抗魔压制 | 效果实例、时钟、场景和动作 | 无贡献与结束但被引用实例也保存，不重触发回调 |
| 怪物 | 实例、普通与借机攻击、吐息、充能、怪物休息 | 战役怪物事实、场景安置、攻击、反应与共享伤害 | 单目标各分量原骰、离开前暂停、多目标豁免、共享原骰、充能恢复与完整历史 |
| 历史与消息 | 审计、便笺、输入结果与因果查询 | 单一战役根序号与领域子历史 | 连续序号、唯一因果、坏文件拒绝；不只导出当前值 |
| 文件生命周期 | 创建/打开/保存/结束/重开/导入/导出/恢复/删除 | 私有库、完整文件、运行收据 | P0—P3查证、初始化中断、精确清理、未知文件保护 |
| 玩家参与 | 受邀加入、审批、分配、共享、有限动作和重连 | 参与聚合、根归因、运行私有设备控制 | 逐人裁剪、单写、撤权、恢复及新运行审批。见[玩家参与](player-participation.md) |

## 存储取舍

保留 JSON 声明的严格关系表、键、索引、静态种子及计划核验。角色与场景是事务聚合，复杂状态及历史正文使用明确的封闭类型文档；其全部字段由代码注册的 record 类型与枚举定义，读取拒绝未知类型、未知/缺失字段、重复映射键和非规范数值。输入不能指定 Java 类、表、SQL 或文件路径。关系列承担稳定身份、归属、顺序、版本和索引；正文在独立领域验证后才能成为权威。不能以二进制字段隐藏任意 JSON。

文档不直接赋予旧运行身份执行权。原 prepared 标识只作保留规则/初始事实及因果关联的稳定业务标识；本地运行UUID、安全代次、CAS和HTTP幂等收据由当前私有库重新建立。完整文件保留所有领域当前状态和全部已记录历史，恢复不重掷、重付或重放业务命令。

<a id="sqlite-retained-capabilities"></a>

## 当前路径、结构和验收责任

DM 从 `/host` 进入 `/host/storage`。工作台经 `GET /api/host/storage/state` 读取战役状态与冻结规则目录，
业务表单经受保护的 `POST /api/host/storage/domain` 由 `action` 选择封闭命令。文件、规则库和 DM 参与管理使用各自路由。
玩家提交服务端候选，经 `PlayerGatewayService` 进入同一个 `CampaignStorageService` 事务门。
`CampaignDomainCommands` 协调领域结算，`CampaignDomainRepository` 保存全部聚合和投影。

角色和战斗共享 `Schema2CharacterRuntime`、`Schema2CombatState`、`Schema2CombatHistory`
与 `Schema2PreparationIdentity` 的类型化事实。`CampaignDomainEngine` 使用已独立的
`Schema2CharacterMaterial`、`Schema2CharacterRequest`、`Schema2CombatRequest` 和纯规划服务的 `InitialPlan`。
SQLite 适配器按封闭类型编码，由领域模型校验结构、相邻前后状态和容量。
同一工作库事务保留业务事实、接纳结果、根历史和关系投影。

| 领域 | 操作入口及运行实现 | 权威数据、关联及当前私有库落点 | 初始化、读写和文件接合 | 关键边界与验收 |
|---|---|---|---|---|
| 规则与模块 | 标准四包直接开团。`/api/host/storage/rule-*` 提供原包导入、审阅、下载、准确接受、有序组合预览及操作查证/恢复。`LocalRuleLibrary`、`CampaignRuleSupport`、`CampaignRuleContext` | 本机原包和 SQLite 接受账本。`rules_runtime_material` 保存解释合同、准确有序绑定、规则、条目与字段链 | 新建和完整文件打开按原包重新组合。标准组合核对随附原包，自定义组合核对本机接受。冻结材料供业务、保存与同版残留恢复使用 | 准确内置/外部组合及实际家规差异，缺包、未接受、错误顺序、错绑、坏规则及来源链拒绝。完整规则 DRAFT 发布资格另管 |
| 战役与生命周期 | `/api/host/storage` 下的 `create`、`open`、`recover`、`save`、`end`、`import`、`download`、`delete`、`discard` | `core_*`、分配/输入/清理材料、受管保存文件，由 `CampaignStorageService` 和 `ManagedCampaignFiles` 协调 | INITIAL/MANUAL/FINAL 共用 Snapshot、严格 codec 和 P0—P3。导入登记完整文件的原 fileKey，打开才建立新运行并核对本机规则。同版残留显式恢复原库 | 初始化与发布中断、提交响应丢失、锁与旧 epoch、旧格式与坏文件、精确放弃和删除，周期 AUTO 待采用 |
| 规则角色与初始授予 | `character-create`、`character-profile`；`CampaignDomainEngine`、初始授予规则 | `characters_runtime_character` 的 InitialPlan、initial event/current/history；名字、PC/NPC 与活动状态进入根历史 | 标准数组、语言/工具及有限职业授予。创建默认人类来源，标准组合可选择矮人及其山丘亚种，文件保存准确种族、亚种和初始授予，强韧由初始计划与冻结规则派生 | 准确引用、初始来源、活动状态、Unicode、文件初始前缀。种族属性提升、完整背景技能、起始装备整包和任意加入等级待采用 |
| 自由角色卡 | `manual-create`、`manual-profile`、`manual-field`、`manual-class`、`manual-proficiency`；`CampaignManualData/Commands` | `scenes_world` 的自由卡聚合及 `manual_character` 核验投影 | 六属性、HP/AC/速度、十二职业等级、技能/豁免熟练；空白卡及平民、守卫、狼 NPC 模板，保存初始卡、当前卡和每次根历史 | 自由卡承担简易建卡，独立生命值/定额治疗建档入口已退出。自由卡 HP 为 DM 记录值，检定和字段校验由自由卡领域负责 |
| 成长与职业 | `character-command` 的成长分支、角色成长与法术书规则 | 所属规则角色的职业、子职业、ASI、授予及成长事件 | 所选机制的未来写入结构预建，按真实消费者写初始及前后事实 | 战士 1–6、生命牧师 1、法师 1–5 的有限模板，含有限兼职前提及熟练选择。[Extra Attack](../../rules/schema-2-extra-attack.md)由单职业战士 5 级正常成长授予，6 级保持；勇士 3–6 级取得[精通重击](../../rules/schema-2-improved-critical.md)，普通/OA/Ready 近战共用 19/20 重击判定。无消费者专长拒绝取得，多施法职业的共享法术位仍待采用 |
| 回气附赠动作 | SQLite `combat-command/SECOND_WIND`；战斗及健康/资源 owner | 一级实际授予；角色资源/HP、战斗命令原骰及两条角色子历史，同根一次提交 | 保存和直接恢复已付成本、实际治疗与原攻击序列；不用新余额或表 | 本人 ACTIVE 回合、单职业 1–6 级、满 HP/耗尽/过期/回滚/准确授予 |
| 资源与休息 | character-command；成长与休息网页的资源、生命骰、时间及奥术回想位数选择 | 当前资源、生命骰及休息由角色聚合拥有；`resources_character` 为核验投影 | 同一空间的推进量同根提交，角色累计秒可以不同；原骰、每日使用及消耗直接恢复 | 准确空间、累计日界、资格/预算/位数、失败无部分收益、保存/END/重开 |
| 物品与装备 | `item`、`manual-item`；`character-command` 的装备分支 | 简单物品归 `scenes_world`，保留来源、说明、数量和状态；装备实例归角色，`equipment_instance` 核验 | 自由卡保留背包、麻绳和火把模板、临时物品与归档。规则装备保留取得、穿脱、掉落和移除事实 | 核对所有者、版本、数量和护甲限制，战斗与施法组件读取同一装备。恢复直接装载实例及历史 |
| 检定与健康 | `character-command`、`check-effects`、`manual-check`。角色基础规则、`CampaignCheckEffects`、`CampaignManualCommands` | 原骰与选骰或被动分数依据、DC、修正、成功状态、效果计划、HP/临时 HP/死亡及版本进入角色与根历史 | 自由卡支持属性、技能、豁免及 MANUAL，分支可按序调整自由卡 HP、授予模板或临时物品、定位并记录消息。DM 按冻结规则执行规则卡的主动或被动属性、技能及工具检定，每个分支可对一个合格规则角色使用冻结健康效果、变更一个已有简单物品的数量、定位并记录消息 | 两分支、目标和版本核对先于检定与任何取骰，选中分支后果和根/子历史共同提交。精确能力差异见下方[支持边界](#支持边界和设计修订) |
| 地图、场景与焦点 | `node-create`、`node-link`、`position`、`focus-set`、`scene-*`、`space-move`、`travel`；`CampaignSpaceCommands` | `scenes_world` 的世界节点、唯一安置、场景、焦点和各空间钟 | 自建节点与路线取代固定酒馆地图。首次安置、沿路线旅行、逐格移动及进出场使用各自命令。DM 裁定场景允许直接定位已入场的规则角色或自由卡 | 错锚点、占位、版本与焦点代次、后台冻结、源空间计时和跨文件恢复。三维区域及跨空间被动因果待采用 |
| 战斗与动作 | combat-command；`CampaignDomainEngine`、`Schema2CombatRules` | `combat_scene` 完整当前/历史，`actions_frame` 为核验投影；角色 HP/资源仍属角色 | 先攻、回合、攻击、移动、OA、Ready、窗口/机会/父子帧与继续点完整保存 | OPEN/CLOSED 窗口保存恢复；支付/原骰只一次；跨角色后果共同提交 |
| 倒地姿态与爬行 | `combat-command/DROP_PRONE/MOVE/EXPLORATION_MOVE/STAND_UP/READY_MOVE`；战斗、探索与动作继续 owner | 场景以 `Combatant.prone` 计算起身和爬行，按半尺记录战斗花费，并保留逐格路径和探索速度预检。怪物姿态与战役空间聚合在同一事务内同步 | 完整文件保留姿态、已走前缀、反应支付及未决继续点。主动卧倒、起身与爬行为 B 类应用能力 | [有限倒地合同](../../rules/schema-2-prone.md)；ACTIVE/Ready 爬行及失能停止保持；探索卧倒零耗时，爬行按当前速度限额，移动/起身各六秒并保留旧战斗预算，逐格区域与末尾到期同事务；战斗交接/END 重开不清姿态，活着的倒地规则角色起身前不能离场。怪物姿态随实例转移，可在规则场景起身。战斗推倒由[近战控制](../../rules/schema-2-melee-control.md)产生 |
| 施法与法术书 | 角色 `character-command` 法术书/准备/恢复；场景 `combat-command` 施法/反应 | 法术书归角色、`spellcasting_book` 投影；施法尝试、成本、目标和原骰归场景动作历史 | 五个已支持法术机制使用同一规则、装备、资源与继续流 | 生命领域 Bless/Cure Wounds，单职业法师 Shield/Counterspell/Burning Hands。其余法师目录法术限学习/准备元数据，施放请求拒绝 |
| 持续效果与环境 | 场景专注、环境、探索/战斗交接 | 场景效果、专注持有、压制、期限及历史；`effects_instance` 核验投影 | 保存 SHADOWED、已结束但仍被引用的实例及继续义务 | 专注、到期、独立压制原因、恢复后无重复贡献或结束回调。抗魔区域是 DM 家规，Antimagic Field 法术待采用 |
| 怪物与共享伤害 | `monster-create`、空间转移、场景吐息/充能/休息 | 战役怪物身份、HP、姿态与动作额度由空间聚合保管，场景保存结算前后事实与共享伤害批次 | 共享原骰、目标证明、逐目标豁免和伤害包、已消费动作及休息保留 | 已掷未 APPLY 保存恢复；多目标 HP/专注同根提交。地狱犬的[啮咬](../../rules/schema-2-monster-attacks.md)共用普通/反应攻击流程，分别保留穿刺和火焰原骰，吐息保留共享火焰骰。狼啮咬保留真实力量豁免、命中后裁定及怪物转场姿态 |
| 历史、消息与重试 | 所有命令及 `note`；`CampaignDomainValidation` 和各领域历史验证 | `history_runtime_root` 连续根、角色和场景完整子事件；`core_command` 保存运行本地接受收据 | 按原请求与保留骰序列纯核对各历史前缀；文件保存全部根和子事实，直接装载现状 | `note` 承接 v1 记录说明用途，同键同意图返回原事实。错版本、异意图、坏邻接、孤儿和重排拒绝 |
| 玩家参与 | `/api/host/storage/players/*`、可选 HTTPS `/player` 和 `/api/player/*`。`PlayerServlet`、`HostPlayerServlet`、`PlayerBoundaryFilter` | `participation_state` 和参与关系投影，`Root.actor` 归因，`CampaignAdjudicationCommands` 保存规则卡属性检定的有界裁定，`core_player_control` 保存私有授权 | 完整文件保留成员、控制、共享、许可和业务归因。原库显式恢复并重新开放后可凭有效设备绑定换新会话，显式领用写连接。文件重开须再批准设备 | 逐人裁剪/候选、实际操作者、暂停/撤权、单写、原收据查证和 TLS 隔离。自由卡可分配和查看，直接操作待采用。HTTPS 默认关闭，真实 LAN 设备信任单独验收 |

所有实际表、约束、索引和静态 seed 位于
[`campaign-schema.json`](../../../src/main/resources/storage/campaign-schema.json)。模块闭包不按
当前角色职业、当前非空表或当前效果状态裁剪。当前唯一 `runtime` 结构计划预建上述能力的全部
未来写入足迹。建局使用完整战役模型 `campaign-spaces-3` 与 `sqlite-campaign-3`，便携文件使用
`RULE_BOUND_CAMPAIGN` format 3。

应用交付五个规则 ZIP 作为导入候选，标准与家规组合各含四个准确成员。两者按序共用
`srd51_runtime@13`、`srd51_characters@14`、`srd51_magic@16`，第四个成员分别为
`dm_runtime_house_rules@25` 与 `dm_runtime_house_rules@27`。绑定保留成员顺序、准确版本与
原始 ZIP 和逻辑摘要，以及组合、最终规则和字段链摘要。DM 可选择经本机接受并满足当前支持
范围的外部组合。角色创建、候选、展示、实际结算和
历史校验共同使用所选战役的冻结规则上下文。标准被动基数为 10，优劣势调整幅度为 5。检定变体分别使用 12 和 3，运动默认以感知结算，允许 DM 为本次技能检定选择其他属性。变体的石工知识采用三倍熟练，自定义工匠获得侏儒语，DM 定额钝击伤害为 3 HP。作者和消费者的对应关系见[规则包指南](../../../rule-packages/srd51-runtime/package-guide.md)。

规则角色、自由卡和怪物共用网格与占位。自动战斗按场景内具备规则事实的参与者结算。
进出场更新当前名册，已完成动作按各事件当时的名册和版本核验。

## 可重复验收索引

| 检查 | 测试或实际入口 | 必须证明的结果 |
|---|---|---|
| 声明与组合 | `StorageSchemaCatalogTest`、`SqliteSchemaTest`、`CampaignSelectedSchemaTest` | 严格 JSON、直接依赖、确定性完整 runtime 计划、实际 SQLite 约束、索引及种子 |
| 准确规则 | `LocalRuleLibraryTest`、`BundledCampaignRulesTest`、`CampaignRuleBundleIsolationTest`、`CampaignRuleSupportTest`、`CampaignRuleBindingIntegrationTest`。模组行为用独立场景，随附原包与作者源的一致性用包构建器 `--check` | 准确导入与本机接受、内置包损坏隔离、内置及外部覆盖、候选与初始化、同骰的不同结算和条件后果、缺包或未接受阻断打开、健康残留的直接恢复 |
| 规则角色、成长与装备 | `CampaignDomainEngineTest`、`CampaignDomainsIntegrationTest`；工作台角色/成长/装备/检定表单 | 完整初始/当前及历史、成长和装备共用同一卡、原骰与版本保留；晚写回滚，保存/END 重开后逐项比较 |
| 自由卡与有序后果 | `CampaignManualIntegrationTest`、`StorageHttpIntegrationTest` 的自由卡旅程、`tools/storage/browser-manual-check-draft.cjs` | 自由字段/职业/熟练、混合网格、两分支事前验证、服务端骰、顺序与全部可能目标版本、共同提交及回滚。准确重开保留已执行后果，未提交草稿按运行隔离 |
| 规则卡分支后果 | `CampaignCheckEffectsTest`、`CampaignRuleBindingIntegrationTest`、工作台规则卡检定表单 | 两分支的准确冻结效果与焦点/目标，含非法未选分支零取骰。同一角色的检定与健康子事件保留连续版本，事实、根历史和收据共同提交并按原文件校验 |
| 规则卡被动检定 | `Schema2CheckCompositionTest`、`CampaignPassiveCheckIntegrationTest`、[独立作者场景](../../../rule-packages/srd51-runtime/scenarios/README.md#验证被动检定) | 核对标准及变体参数，实际分数、无检定骰、选中后果独立取骰、原子提交与同请求查证。完整文件保存、END 和重开时按原属性、装备、熟练及冻结材料重验分数和后果 |
| 对抗检定 | `CampaignContestCheck`、`CampaignContestCheckIntegrationTest`、[独立作者场景](../../../rule-packages/srd51-runtime/scenarios/README.md#验证对抗检定) | 同一世界节点的双方各自检定，按冻结分差确定领先方或保持现状。战役根保存双方原骰与共同裁定，完整文件重开后可继续对抗。 |
| 团队属性检定 | `CampaignGroupCheck`、`CampaignGroupCheckIntegrationTest`、[作者场景](../../../rule-packages/srd51-runtime/scenarios/README.md#验证团队属性检定) | 焦点世界节点的一份根记录关联每名参与者的检定。保存重开后按原骰重验共同成败与命中消息，下一次检定沿用冻结比例。 |
| 根历史与因果 | `CampaignDomainRootValidationTest` | 节点/物品语义、历史前缀、准确子事件归属及逐根空间时钟，拒绝最终值自洽但过程伪造的文件 |
| 直接定位与分支定位 | `CampaignScenePlacementIntegrationTest`、`CampaignCheckEffectsTest`、`CampaignManualIntegrationTest`、`StorageHttpIntegrationTest`、`tools/storage/browser-smoke.cjs` | 焦点裁定场景的非相邻空格、双分支都预检、分支内逐步校验位置版本和占位、拒绝零取骰、晚写回滚、原请求查证、保存和重开。审计核对原格和目标格、版本、原骰、其他后果及历史。 |
| 空间闭环 | `CampaignSpacesIntegrationTest`、`CampaignSpaceTimeBoundaryTest`；`tools/storage/browser-spaces.cjs` | DM 旅行、混合占位、两个待继续场景、旧焦点零 RNG、FINAL 重开及只继续原后缀 |
| 战斗与姿态 | `CampaignDomainsCombatIntegrationTest`、`CampaignExtraAttackIntegrationTest`、`CampaignSecondWindIntegrationTest`、`CampaignImprovedCriticalIntegrationTest`、`CampaignProneIntegrationTest` | 真实成长授予、成本及原骰、未决 OA/Shield/爬行续作及跨角色回滚。保存后每段已付成本只出现一次 |
| 施法、效果与怪物 | `CampaignDomainsCombatIntegrationTest`、`CampaignMonsterSpaceIntegrationTest`、`CampaignImprovedCriticalIntegrationTest`；Cure Wounds 见 `tools/storage/browser-combat-smoke.cjs`。反应/Ready 法术和压制的共享规则另见 `Schema2ReactionSpellRulesTest`、`Schema2ReadySpellRulesTest`、`Schema2OngoingEffectRulesTest` | SQLite 路径核对 Bless 位数与专注、吐息共享原骰及未结算跨文件恢复、HP 与动作额度跨场景、Shield 防御及 Burning Hands 实际后果。共享规则夹具只证明各自规则行为 |
| 怪物近战与借机攻击 | `CampaignMonsterAttackIntegrationTest`、`Schema2MonsterAttackRulesTest` 和 `tools/storage/browser-monster-attack.cjs` | 普通攻击消耗动作，宣告借机攻击消耗反应，放弃则保留反应。重开保留双位置、移动游标、攻击原骰及两类伤害，抓者失能时解除擒抱并停止剩余拖带。 |
| 完整文件和恢复 | `CampaignCodecTest`、`CampaignFileVectorTest`、`CampaignDomainRecoveryTest` | 严格字节/字段向量、初始化中断、已提交响应丢失、导入初始事实不可改写 |
| 文件系统生命周期 | `CampaignStorageServiceTest`、`CampaignStorageLifecycleTest`、`CampaignPreparationTest`、`CampaignStorageCrashTest`、`CampaignWindowsFilesystemTest` | P0—P3、准备前缀、原意图续作、未知材料保护、根/运行独占、NTFS ACL/reparse、导入/下载/删除/精确放弃；Windows 进程中断与断电保证分开 |
| 资源与休息 | `CampaignArcaneRecoveryIntegrationTest`、`CampaignSecondWindIntegrationTest`、`CampaignSpaceTimeBoundaryTest`；共享规则见 `Schema2ArcaneRecoveryRulesTest` | 真实短休或长休的资格、位数/预算/法术书、每日使用、个人累计经历时间、幂等与故障回滚。完整文件保留资源事实、根/子历史与使用日 |
| 应用入口 | `StorageHttpIntegrationTest`、`StorageApplicationListenerTest`、`StorageReadinessTest`；Windows 候选脚本、`tools/storage/verify-war-http.cjs`、冒险和恢复浏览器 smoke | 无 JNDI 的 SQLite 就绪、旧路由拒绝、真实 WAR 描述符、Filter、CSRF/摘要/epoch、完整文件重开、残留恢复和导入导出 |
| 玩家参与 | `CampaignParticipationIntegrationTest`、`PlayerGatewayServiceTest`、`PlayerParticipationAcceptanceTest`、`PlayerProjectionAndCandidatesTest`、`PlayerBoundaryFilterTest`、`PlayerHttpIntegrationTest`、`PlayerProcessRecoveryIntegrationTest`；`tools/storage/browser-player-smoke.cjs` | 参与历史与裁定随文件，私有授权隔离；旧控制权提交在 RNG 前拒绝。核对 loopback HTTPS、暂停时保留未决叶子、显式恢复后原收据查证、END 和文件重开后重新批准 |

表中的源码入口和测试断言用于核对当前接合。退役候选须另记录实际执行的结果，按受影响能力
核对正常运行、严格文件读回、同版残留恢复及异常分支。对未决流程核对原骰、成本、当前叶子和可继续后缀，
对坏规则、过期版本、晚写故障和未知提交核对零部分写入或准确查证。规则作者行为仍使用各包的独立场景，
应用事务和文件使用对应的 SQLite 定向测试，真实入口使用隔离浏览器及候选 WAR 验证。
[测试指南](../../testing.md#sqlite-结构完整文件与运行验收) · [本地运行说明](../../storage-vitality-loop.md)

## 支持边界和设计修订

| 旧 MySQL Web 能力 | SQLite 的接合与去向 |
|---|---|
| 简化角色、NPC、熟练、普通检定与事件记录 | 自由卡承接可编辑 PC/NPC、平民/守卫/狼、十二职业等级和技能/豁免熟练，保留普通、优势、劣势的服务端 d20 语义。`manual-check` 承接属性、技能、豁免和 MANUAL，`note` 承接不掷骰的消息记录。应用封闭命令负责授权和校验，旧模组事件模板关系随其执行器退出。 |
| 检定分支的健康调整、物品授予、消息 | 自由卡分支承接这三类功能。HP 整数调整限自由卡，物品可授予符合焦点、场景归属和版本条件的自由卡或规则卡。规则卡使用 `check-effects` 的冻结健康效果、已有物品数量和消息。规则卡的豁免由实际机制执行，固定修正的 MANUAL 及分支新建物品目前只在自由卡检定提供。两种检定各自验证两分支并提交完整因果记录。 |
| 固定酒馆节点、简易遭遇、直接定位与 `effect.set_entity_position` | DM 裁定场景承接简易遭遇，用户创建的节点与连边取代固定酒馆预设。现有 `position` 只安置尚未安置的角色到焦点世界节点，旅行、进出场和步进各有独立命令。遭遇内直接定位和两类检定分支定位由同一个 SQLite 安置领域负责。 |
| 按旧 MySQL 存档创建和恢复战役 | 当前由 format 3 完整文件与同版工作库残留承担。旧 MySQL 存档入口和旧文件格式退出本版产品，真实历史资料按[数据库说明](../../database.md)保管。 |

规则战斗沿各自动作流和未决反应约束更新位置。DM 的直接定位和检定分支定位均要求焦点裁定场景，
保留位置前后值及版本，不推进空间时钟或消耗普通移动。玩家按服务端候选授权。
[当前空间合同](world-and-scene-spaces.md#直接定位与分支定位) · [#89](https://github.com/liii932/local-srd51-campaign-tool/issues/89)

外部包须经本机准确接受并通过应用的职业、特性、法术与机制支持检查。BLOCKED 与元数据执行请求
按对应机制拒绝。其余 SRD 机制和真实 LAN 设备信任单独跟踪，均独立于退役验收。
[#47 其余 SRD 机制](https://github.com/liii932/local-srd51-campaign-tool/issues/47) · [#62 真实 LAN 设备验收](https://github.com/liii932/local-srd51-campaign-tool/issues/62)

DM 当前可以声明 Ready。玩家可通过候选释放已声明的 Ready，玩家声明 Ready 仍待接合。
玩家编辑或检定自由卡、更多施法参数、战斗多格路径及裁定场景共享视图也待接合。
全局玩家账号在产品范围之外。玩家步骤和周期 AUTO 各有独立采用条件。
[当前玩家候选与采用范围](player-participation.md#当前原型与剩余采用) · [步骤与自动保存](player-steps-and-auto-save.md)

当前采用聚合文档与关系投影共同校验，并把固定规则材料纳入完整文件。独占根锁和一个
SQLite 业务/根/接受事务承担当前单实例接纳；不另建 C0 重放队列。每条命令都耐久提交，
INITIAL、MANUAL、FINAL 共用完整文件发布；周期 AUTO/淘汰须先完成容量和保留策略测量，
不代替本版保存、恢复和全部领域验收。具体取舍见[运行事务](runtime-and-transactions.md)
和[文件合同](save-and-recovery.md)。
