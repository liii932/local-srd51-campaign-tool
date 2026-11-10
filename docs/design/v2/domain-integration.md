# 当前版本全领域接合与验收清单

本清单以产品、生产入口、现有纯规则消费者及其测试确定范围。完整 SRD 尚未实现的机制不因存储替换自动变为可执行规则；已经存在的有限消费者必须接入共同战役运行。原有 MySQL 真实数据保持原处，不自动迁移或删除。

## 领域与依赖

下表描述领域职责与目标验收。具体已接入的路径和测试见[当前路径、结构和验收责任](#当前路径结构和验收责任)。

| 领域 | 用户能力与入口 | 权威与关联 | 接合要求与验收 |
|---|---|---|---|
| 规则选择 | 选择当前准确规则组合、查看规则 | 完整规则、成员与覆盖链；所有领域依赖 | 声明闭包、准确材料、不可执行规则拒绝；普通命令不回来源库 |
| 战役与角色 | 新建、PC/NPC、改名、离场恢复、角色卡 | 战役根、初始卡与当前角色；历史 | 应用入口创建和再次读取，名字Unicode与版本边界 |
| 授予与成长 | 语言工具、职业、子职业、ASI/专长、多职业 | 初始计划、成长事实、规则 | 合法选择、非法引用与等级/资格；完整保留初始和当前事实 |
| 资源与休息 | 消耗、生命骰、短/长休、奥术回想 | 角色资源、累计经历时间和休息资格由角色拥有 | 核准确时间域、休息资格与间隔，失败无消耗 |
| 物品与装备 | 简单物品数量、实例装备、穿脱 | 角色物品、规则、护甲/组件 | 数量与资格边界，战斗/施法读取同一装备 |
| 检定与健康 | d20、优势劣势、伤害/治疗/临时HP、死亡豁免 | 服务器原骰、角色HP、历史 | 校验先于RNG，同请求不重掷，旧版本不写入 |
| 场景与位置 | 节点图、局部网格、安置交接、DM 切焦点 | 世界节点、场景、唯一安置、焦点和各空间逻辑时钟 | 完整验证位置与焦点代次，后台冻结，全部空间直接恢复。见[世界与场景](world-and-scene-spaces.md) |
| 战斗与动作 | 先攻回合、攻击、移动、OA、Ready、反应继续 | 场景、角色、规则和所属空间的动作流 | 焦点空间推进，受影响角色共同提交，全部未决窗口直接恢复 |
| 施法 | 书籍、准备、位数及已支持法术 | 角色资源/装备、动作、规则 | Bless/Cure Wounds/Shield/Counterspell/Burning Hands 的实际支持边界 |
| 持续效果 | 专注、到期、叠加与抗魔压制 | 效果实例、时钟、场景和动作 | 无贡献与结束但被引用实例也保存，不重触发回调 |
| 怪物 | 实例、吐息、充能、怪物休息 | 战役怪物事实、场景安置、规则与共享伤害 | 多目标豁免、共享原骰、充能恢复与完整历史 |
| 历史与消息 | 审计、便笺、输入结果与因果查询 | 单一战役根序号与领域子历史 | 连续序号、唯一因果、坏文件拒绝；不只导出当前值 |
| 文件生命周期 | 创建/打开/保存/结束/重开/导入/导出/恢复/删除 | 私有库、完整文件、运行收据 | P0—P3查证、初始化中断、精确清理、未知文件保护 |

## 存储取舍

保留 JSON 声明的严格关系表、键、索引、静态种子及计划核验。角色与场景是事务聚合，复杂状态及历史正文使用明确的封闭类型文档；其全部字段由代码注册的 record 类型与枚举定义，读取拒绝未知类型、未知/缺失字段、重复映射键和非规范数值。输入不能指定 Java 类、表、SQL 或文件路径。关系列承担稳定身份、归属、顺序、版本和索引；正文在独立领域验证后才能成为权威。不能以二进制字段隐藏任意 JSON。

文档不直接赋予旧运行身份执行权。原 prepared 标识只作保留规则/初始事实及因果关联的稳定业务标识；本地运行UUID、安全代次、CAS和HTTP幂等收据由当前私有库重新建立。完整文件保留所有领域当前状态和全部已记录历史，恢复不重掷、重付或重放业务命令。

## 当前路径、结构和验收责任

统一用户入口为 `/host` → `/host/storage`。所有业务表单经受保护的
`POST /api/host/storage/domain`，由 `action` 选择下面的封闭命令；客户端不指定 SQL、
Java 类型名、骰子或派生结果。`CampaignStorageService` 拥有事务，
`CampaignDomainCommands` 协调领域，`CampaignDomainRepository` 保存全部聚合和投影。

角色和战斗共享 `Schema2CharacterRuntime`、`Schema2CombatState`、`Schema2CombatHistory`
与 `Schema2PreparationIdentity` 的类型化事实。领域模型校验结构、相邻前后状态和容量，
SQLite 适配器按封闭类型编码；内部 MySQL JDBC 适配器按类型化行读写，转换领域历史校验拒绝为
稳定的 `SQLException`，由调用方拥有事务。检定算法、原骰和后果的归属沿用各领域合同。

| 领域 | 操作入口及运行实现 | 权威数据、关联及当前私有库落点 | 初始化、读写和文件接合 | 关键验收 |
|---|---|---|---|---|
| 规则与模块 | 规则库导入、审阅、接受、有序组合预览及创建。`LocalRuleLibrary`、`CampaignRuleSupport`、`CampaignRuleContext` | 本机原包和 SQLite 接受账本。`rules_runtime_material` 保存战役解释合同、完整绑定、规则、条目与字段链 | 新建和打开复核本机准确原包及接受。冻结材料供业务、保存与残留恢复消费 | 内置与外部准确组合、缺包、未接受、错误顺序、错绑、坏规则及来源链 |
| 战役与生命周期 | create/open/recover/save/end/import/download/delete/discard | `core_*`、分配/输入/清理材料、受管保存文件；生命周期服务是唯一 owner | 全部领域由同一 Snapshot 初始化、保存、读回、恢复、封存和清理 | 中断与响应丢失、锁、旧 epoch、未接受文件、准确清理 |
| 规则角色与初始授予 | character-create/profile；`CampaignDomainEngine`、初始授予规则 | `characters_runtime_character` 的 InitialPlan、initial event/current/history；名字/PC-NPC/离场由根历史保存 | 标准数组、语言/工具及有限职业授予；完整当前与初始分别保存 | 准确引用、初始来源、活动状态、Unicode、文件初始前缀 |
| 自由角色卡 | manual-create/profile/field/class/proficiency；`CampaignManualData/Commands` | `scenes_world` 中独立 manual 聚合；`manual_character` 核验身份、版本及资料投影 | 六属性、HP/AC/速度、十二职业等级、技能/豁免倍数；初始卡、当前卡和每次 typed 根历史 | 范围、总等级、NFC、版本冲突、归档、与规则角色隔离 |
| 成长与职业 | character-command 的成长分支、角色成长与法术书规则 | 所属规则角色的职业、子职业、ASI、授予及成长事件 | 所选机制的未来写入结构预建，按真实消费者写初始及前后事实 | fighter 1–6、Life cleric 1、wizard 1–5 的有限模板；[战士 Extra Attack](../../rules/schema-2-extra-attack.md)由单职业战士 5 级正常成长授予，6 级保持；勇士 3–6 级实际取得[精通重击](../../rules/schema-2-improved-critical.md)，普通/OA/Ready 武器攻击共用 19/20 重击判定。无消费者专长的获取请求拒绝 |
| 回气附赠动作 | SQLite `combat-command/SECOND_WIND`；战斗及健康/资源 owner | 一级实际授予；角色资源/HP、战斗命令原骰及两条角色子历史，同根一次提交 | 保存和直接恢复已付成本、实际治疗与原攻击序列；不用新余额或表 | 本人 ACTIVE 回合、单职业 1–6 级、满 HP/耗尽/过期/回滚/准确授予；MySQL INTERNAL 拒绝该命令 |
| 资源与休息 | character-command；成长与休息网页的资源、生命骰、时间及奥术回想位数选择 | 当前资源、生命骰及休息由角色聚合拥有；`resources_character` 为核验投影 | 同一空间的推进量同根提交，角色累计秒可以不同；原骰、每日使用及消耗直接恢复 | 准确空间、累计日界、资格/预算/位数、失败无部分收益、保存/END/重开 |
| 物品与装备 | item/manual-item；character-command 的装备分支 | 简单物品归 `scenes_world`，保留来源/说明/数量/状态；装备实例归角色，`equipment_instance` 核验 | 共同角色引用与全部取得/移动/消耗事实；不按模板重新发放 | 所有者、归档、数量、护甲与施法组件读取同一装备 |
| 检定与健康 | character-command/check-effects/manual-check；角色基础规则及手动检定算法 | 原骰、候选/选骰、DC、派生修正、HP/临时HP/死亡、效果计划与前后值在角色/根历史 | 两分支预检，实际分支后果和根/子历史一起提交，完整恢复不重掷 | 手动/属性/技能/豁免、优势劣势、HP/物品/消息联动、晚写回滚 |
| 地图、场景与焦点 | node-create/link、position、focus-set、scene-*、space-move、travel；`CampaignSpaceCommands` | `scenes_world` 的世界节点、唯一安置、场景、焦点和各空间钟 | 网格与名册从安置和场景事实投影。自由卡和规则角色使用同一位置合同，DM 裁定战斗与自动规则各有入口 | 错锚点、占位、版本与焦点代次、后台冻结、源空间计时和跨文件恢复 |
| 战斗与动作 | combat-command；`CampaignDomainEngine`、`Schema2CombatRules` | `combat_scene` 完整当前/历史，`actions_frame` 为核验投影；角色 HP/资源仍属角色 | 先攻、回合、攻击、移动、OA、Ready、窗口/机会/父子帧与继续点完整保存 | OPEN/CLOSED 窗口保存恢复；支付/原骰只一次；跨角色后果共同提交 |
| 施法与法术书 | 角色法术书/准备/恢复；场景施法/反应 | 法术书归角色、`spellcasting_book` 投影；施法 attempt/成本/目标/原骰归场景动作历史 | 五个已支持法术机制使用同一规则、装备、资源与继续流 | Bless、Cure Wounds、Shield、Counterspell、Burning Hands 的各有限合同；学习/准备/位阶边界 |
| 持续效果与环境 | 场景专注、环境、探索/战斗交接 | 场景效果/holder/压制/期限/历史；`effects_instance` 核验投影 | 保留 SHADOWED、已结束但仍被引用实例及继续义务 | 专注、到期、独立压制原因、恢复不重复贡献或结束回调 |
| 怪物与共享伤害 | monster-create、空间转移、场景吐息/充能/休息 | 战役怪物身份、HP 与动作额度由空间聚合保管，场景保存结算前后事实与共享伤害批次 | 共享原骰、目标证明、逐目标豁免/packet、已消费动作及休息保留 | 已掷未 APPLY 保存恢复；多目标 HP/专注一起提交，不二次扣费 |
| 历史、消息与重试 | 所有命令及 note；统一根、各领域 validator | `history_runtime_root` 连续根，角色/场景完整子事件；`core_command` 仅本地接受收据 | 按根原请求与保留骰序列纯核对各前缀及其子事件；完整文件排除本地执行权 | 不截断近期历史、坏邻接/孤儿/重排拒绝；同键同意图返回原事实 |

所有实际表、约束、索引和静态 seed 位于
[`campaign-schema.json`](../../../src/main/resources/storage/campaign-schema.json)。模块闭包不按
当前角色职业、当前非空表或当前效果状态裁剪。当前唯一 `runtime` 结构计划预建上述能力的全部
未来写入足迹。建局使用完整战役模型 `campaign-spaces-3` 与 `sqlite-campaign-3`，便携文件使用
`RULE_BOUND_CAMPAIGN` format 3。

应用交付五个规则 ZIP 作为导入候选，标准与家规组合各含四个准确成员。两者按序共用
`srd51_runtime@5`、`srd51_characters@5`、`srd51_magic@5`，第四个成员分别为
`dm_runtime_house_rules@9` 与 `dm_runtime_house_rules@10`。绑定保留成员顺序、准确版本与
原始 ZIP 和逻辑摘要，以及组合、最终规则和字段链摘要。DM 可选择经本机接受并满足当前支持
范围的外部组合。角色创建、候选、展示、实际结算和
历史校验共同使用所选战役的冻结规则上下文。变体以感知结算运动检定，自定义工匠获得侏儒语，
DM 定额钝击伤害为 3 HP。作者和消费者的对应关系见[规则包指南](../../../rule-packages/srd51-runtime/package-guide.md)。

自由卡的 HP=0 仍是 DM 记录值。规则角色继续按成长、装备和法术合同派生状态。场景可使用
DM 裁定模式，规则角色、自由卡和怪物共用网格与占位；自动战斗以场景中实际具备规则事实的
参与者结算。入退场更改现役名册，已完成动作仍按各事件当时的名册和版本核验。

## 可重复验收索引

| 检查 | 测试或实际入口 | 必须证明的结果 |
|---|---|---|
| 声明与组合 | `StorageSchemaCatalogTest`、`SqliteSchemaTest`、`CampaignSelectedSchemaTest` | 严格 JSON、直接依赖、确定性完整 runtime 计划、实际 SQLite 约束、索引及种子 |
| 准确规则 | `LocalRuleLibraryTest`、`BundledCampaignRulesTest`、`CampaignRuleSupportTest`、`CampaignRuleBindingIntegrationTest`。包构建器 `--check` | 准确导入与本机接受、内置及外部覆盖、候选与初始化、同骰的不同结算和条件后果、缺包或未接受阻断打开、健康残留的直接恢复 |
| 规则角色与共享机制 | `CampaignDomainEngineTest`、`CampaignDomainsIntegrationTest`、现有 schema-2 机制测试族 | 完整初始/当前及历史、独立空间秒和角色累计秒、成长/资源/装备/健康、引用和版本边界 |
| 自由卡与条件后果 | `CampaignManualIntegrationTest`、`CampaignCheckEffectsTest` | 自由字段/职业/熟练、混合网格、两分支事前验证、服务端骰、明确顺序、目标版本、一次共同提交与回滚 |
| 根历史与因果 | `CampaignDomainRootValidationTest` | 节点/物品语义、历史前缀、准确子事件归属及逐根空间时钟，拒绝最终值自洽但过程伪造的文件 |
| 复杂跨领域流程 | `CampaignDomainsCombatIntegrationTest` | OA 窗口恢复后继续移动/攻击；Bless 位数与专注、怪物共享原骰、多目标 HP 一致 |
| 空间闭环 | `CampaignSpacesIntegrationTest` | DM 旅行、混合占位、两个待继续规则场景、旧焦点零 RNG、FINAL 重开与继续原后缀 |
| 完整文件和恢复 | `CampaignCodecTest`、`CampaignFileVectorTest`、`CampaignDomainRecoveryTest` | 严格字节/字段向量、初始化中断、已提交响应丢失、导入初始事实不可改写 |
| 文件系统生命周期 | `CampaignStorageServiceTest`、`CampaignStorageLifecycleTest`、`CampaignPreparationTest`、`CampaignStorageCrashTest`、`CampaignWindowsFilesystemTest` | P0—P3、准备前缀、原意图续作、未知材料保护、根/运行独占、NTFS ACL/reparse、导入/下载/删除/精确放弃；Windows 进程中断与断电保证分开 |
| 奥术回想 | `CampaignArcaneRecoveryIntegrationTest`、`Schema2ArcaneRecoveryRulesTest` 与浏览器主用例 | 真实短休、位数/预算/法术书、每日使用、幂等与故障回滚，完整文件保留根/子历史与使用日 |
| 应用入口 | `StorageHttpIntegrationTest`、`StorageApplicationListenerTest`、`StorageReadinessTest`；Windows 候选脚本、`verify-war-http.cjs` 与两个浏览器 smoke | 配置失败关闭、SQLite 实际就绪、真实 WAR 注解/JSP/Filter、CSRF/摘要/epoch、用户表单、装备/攻击/继续/生命法术结果、重开与导入导出 |

本表记录当前实际接合路径和验收责任；所有列出的领域都进入同一运行、完整文件及恢复路径。
测试类存在不等于执行结果，每次候选须由对应源码的测试、浏览器和 WAR 审计共同确认。
执行方法见[测试指南](../../testing.md)及[本地运行说明](../../storage-vitality-loop.md)。

## 支持边界和设计修订

这里的“全领域”指当前正式简化能力与当前已实现的有限规则消费者全部接到新路线。
有限外部内容通过本机导入和准确接受进入组合，执行资格仍按应用支持的职业模板、特性、法术与
机制逐项判定。完整 SRD、任意等级角色加入、跨空间持续影响、全局玩家账号与玩家步骤各自采用。
BLOCKED 与元数据按对应机制拒绝执行。
[玩家参与](player-participation.md#当前原型与剩余采用)已有 SQLite 原型：`CampaignParticipationCommands`
接合成员/控制/共享/探索许可、`Root.actor` 与 `CampaignAdjudicationCommands` 接合归因和有界
裁定，`PlayerGatewayService` 经共同门提交，`PlayerCampaignProjection/PlayerCandidateFactory`
产生逐人投影及服务器候选。`PlayerServlet/HostPlayerServlet` 和全局 `PlayerBoundaryFilter`
提供独立可选 HTTPS surface；默认不开监听。自由卡玩家直接检定、全部动作参数编辑、
DM 确证未提交工具及真实手机/桌面 LAN 信任验收仍有缺口，不能据此宣布整份计划完成。

当前采用聚合文档与关系投影共同校验，并把固定规则材料纳入完整文件。独占根锁和一个
SQLite 业务/根/接受事务承担当前单实例接纳；不另建 C0 重放队列。每条命令都耐久提交，
INITIAL、MANUAL、FINAL 共用完整文件发布；周期 AUTO/淘汰须先完成容量和保留策略测量，
不代替本版保存、恢复和全部领域验收。具体取舍见[运行事务](runtime-and-transactions.md)
和[文件合同](save-and-recovery.md)。
