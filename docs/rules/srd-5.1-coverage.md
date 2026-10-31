# SRD 5.1 机制覆盖与接入责任

本文把[完整覆盖目标](srd-5.1-complete.md)拆到可以审查的机制族。规则基线为本地 SRD 5.1；
[主题索引](../references/srd-5.1/README.md)和分页文本提供出处，原文存在不证明规则数据、执行器或产品入口存在。
当前采用边界以[采用边界](../design/v2/adoption-boundaries.md)及下列领域合同为准。
完整规则身份保持 DRAFT，旧 RELEASED 规则保持默认；本表不授予安装、战役绑定、Host、完整文件或发布资格。

## 覆盖记录方法

一个记录单元是具有共同前提、成本、结算和后继责任的机制族。新增具体种族、特性、法术、物品或怪物时，
必须把它映射到相应机制族及准确完整规则身份；一个实例通过不能使同族所有内容自动取得支持。
同族中参数、目标政策、动作时机或后果不同的内容必须继续拆行，并记录实际支持的范围。

每行分别审核以下三层，不以最高的一层替代另外两层：

| 层 | 本文用语 | 含义 |
|---|---|---|
| 数据 | 元数据 | 有名称、出处、关系或说明；未证明全部规则参数、原包、类型化来源、组合链和运行镜像完整。 |
| 数据 | 有限类型化 | 指定内容具有正式作者协议、完整引用、来源和运行镜像；只覆盖合同明确允许的参数和内容。 |
| 执行 | 自动 | 真实服务从冻结规则及权威状态计算结果；不是孤立纯函数。 |
| 执行 | 有限选择 | 服务生成合法候选，DM 选择目标、选项或顺序，服务重验并执行。 |
| 执行 | 类型化裁定 | 存在真实命令及有界裁定类型、对象、成本、后果、事件和恢复；标签、备注或任意成功/失败记录不足以证明机制完成。 |
| 执行 | 未接入／阻断 | 没有闭合消费者，或当前入口明确拒绝；不能经通用伤害、说明文字或任意修正绕过。 |
| 状态 | 局部闭环 | 当前 INTERNAL 或单发布 DRAFT 切片保存并严格重验其状态、原骰、历史、幂等及直接恢复；不等于完整文件。 |
| 状态 | 未建闭环 | 对该机制的权威事实、因果历史或恢复责任尚未建立。 |

下表“局部”始终受对应合同的起点、容量、对象和场景限制；“未接入”不推断数据库已经执行任何迁移。
执行状态为 `DM_ADJUDICATION` 的目录行须检查真实消费者，不能直接计为“类型化裁定”。
旧 DRAFT 的种族修正、成长目录和内部角色文件与 schema 2 运行材料分别记账，不能拼接成完整游戏路径。

每次扩展按[机制接入责任清单](../design/v2/mechanism-integration-checklist.md)验证：原文及例外 →
准确作者身份/参数/引用 → 来源与覆盖链 → 运行镜像 → 前提/成本/目标/空间 → 动作与时间 owner →
效果与贡献 → 状态及因果历史 → 幂等/版本/事务 → 严格读回及直接恢复 → 产品接入与发布门。
缺任一必需环节即保留具体缺口，不用“通用支持”或单个领域状态遮蔽。

## 实际合同、代码与验证入口

这些入口用于复核后续矩阵，不是本次运行测试的声明。完整测试选择见[测试指南](../testing.md)。

| 证据组 | 正式合同 | 当前生产入口与最近验证 |
|---|---|---|
| <a id="catalog"></a>角色目录 | [角色目录](character-catalog-v2.md)、[一级创建](character-creation-v2.md)、[职业特性](class-features-v2.md)、[多职业/ASI/专长](multiclass-asi-feats-v2.md) | [LevelOneCharacterRules](../../src/main/java/com/dndtool/service/LevelOneCharacterRules.java)、[ClassFeatureAdjudicationRules](../../src/main/java/com/dndtool/service/ClassFeatureAdjudicationRules.java)；[LevelOneCharacterRulesTest](../../src/test/java/com/dndtool/service/LevelOneCharacterRulesTest.java)、[ClassFeatureAdjudicationRulesTest](../../src/test/java/com/dndtool/service/ClassFeatureAdjudicationRulesTest.java)。后者是有界纯规则校验，不是任意职业能力的业务执行入口。 |
| <a id="growth"></a>授予与成长 | [初始授予](schema-2-proficiency-grants.md)、[成长与资源](schema-2-character-statistics.md) | [Schema2InitialProficiencyRules](../../src/main/java/com/dndtool/service/Schema2InitialProficiencyRules.java)、[Schema2GrowthRules](../../src/main/java/com/dndtool/service/Schema2GrowthRules.java)、[Schema2CharacterProgressionRules](../../src/main/java/com/dndtool/service/Schema2CharacterProgressionRules.java)、[Schema2CharacterRuntimeService](../../src/main/java/com/dndtool/service/Schema2CharacterRuntimeService.java)；[Schema2InitialProficiencyRulesTest](../../src/test/java/com/dndtool/service/Schema2InitialProficiencyRulesTest.java)、[Schema2GrowthRulesTest](../../src/test/java/com/dndtool/service/Schema2GrowthRulesTest.java)、[Schema2CharacterAdvancementAndRestServiceTest](../../src/test/java/com/dndtool/service/Schema2CharacterAdvancementAndRestServiceTest.java)。 |
| <a id="foundation"></a>检定/装备/健康 | [作者来源](schema-2-foundation-authoring.md)、[基础消费者](schema-2-character-foundations.md)、[事实存储](schema-2-character-fact-storage.md) | [Schema2CharacterCheckRules](../../src/main/java/com/dndtool/service/Schema2CharacterCheckRules.java)、[Schema2CharacterEquipmentRules](../../src/main/java/com/dndtool/service/Schema2CharacterEquipmentRules.java)、[Schema2CharacterHealthRules](../../src/main/java/com/dndtool/service/Schema2CharacterHealthRules.java)；[Schema2CharacterCheckServiceTest](../../src/test/java/com/dndtool/service/Schema2CharacterCheckServiceTest.java)、[Schema2CharacterEquipmentHealthServiceTest](../../src/test/java/com/dndtool/service/Schema2CharacterEquipmentHealthServiceTest.java)、[JdbcSchema2CharacterRuntimeRepository](../../src/main/java/com/dndtool/persistence/JdbcSchema2CharacterRuntimeRepository.java)。 |
| <a id="rest"></a>休息/时间 | [标准休息](schema-2-character-rest.md) | [Schema2CharacterRestRules](../../src/main/java/com/dndtool/service/Schema2CharacterRestRules.java)、原角色服务；[Schema2CharacterAdvancementAndRestServiceTest](../../src/test/java/com/dndtool/service/Schema2CharacterAdvancementAndRestServiceTest.java)。场景时间另见战斗和施法组。 |
| <a id="combat"></a>战斗/动作 | [战斗作者](schema-2-combat-authoring.md)、[内部战斗](schema-2-combat.md)、[动作继续协议](../design/v2/action-continuation-protocol.md) | [Schema2CombatRuntimeService](../../src/main/java/com/dndtool/service/Schema2CombatRuntimeService.java)、[Schema2CombatRules](../../src/main/java/com/dndtool/service/Schema2CombatRules.java)、[Schema2ActionFlowRules](../../src/main/java/com/dndtool/service/Schema2ActionFlowRules.java)、[Schema2CombatTimeRules](../../src/main/java/com/dndtool/service/Schema2CombatTimeRules.java)；[Schema2CombatRuntimeServiceTest](../../src/test/java/com/dndtool/service/Schema2CombatRuntimeServiceTest.java)、[Schema2CombatRulesTest](../../src/test/java/com/dndtool/service/Schema2CombatRulesTest.java)、[JdbcSchema2CombatRuntimeRepository](../../src/main/java/com/dndtool/persistence/JdbcSchema2CombatRuntimeRepository.java)。 |
| <a id="spells"></a>施法/效果/探索 | [施法作者](schema-2-spell-authoring.md)、[有限施法](schema-2-spells.md)、[施法扩展目标](../design/v2/spellcasting-extension-contract.md)、[持续效果协议](../design/v2/ongoing-effect-protocol.md) | [Schema2SpellRules](../../src/main/java/com/dndtool/service/Schema2SpellRules.java)、[Schema2WizardSpellbookRules](../../src/main/java/com/dndtool/service/Schema2WizardSpellbookRules.java)、[Schema2OngoingEffectRules](../../src/main/java/com/dndtool/service/Schema2OngoingEffectRules.java)、[Schema2ExplorationRules](../../src/main/java/com/dndtool/service/Schema2ExplorationRules.java)、原战斗服务；[Schema2SpellRuntimeServiceTest](../../src/test/java/com/dndtool/service/Schema2SpellRuntimeServiceTest.java)、[Schema2WizardRuntimeServiceTest](../../src/test/java/com/dndtool/service/Schema2WizardRuntimeServiceTest.java)、[Schema2ExplorationRuntimeServiceTest](../../src/test/java/com/dndtool/service/Schema2ExplorationRuntimeServiceTest.java)。 |
| <a id="monsters"></a>怪物/共享伤害 | [有限怪物](schema-2-monster-breath.md)、[多目标伤害合同](../design/v2/multi-target-save-damage.md) | [Schema2MonsterRules](../../src/main/java/com/dndtool/service/Schema2MonsterRules.java)、[Schema2MonsterRestRules](../../src/main/java/com/dndtool/service/Schema2MonsterRestRules.java)、[Schema2SaveDamageRules](../../src/main/java/com/dndtool/service/Schema2SaveDamageRules.java)、原战斗服务；[Schema2MonsterBreathRulesTest](../../src/test/java/com/dndtool/service/Schema2MonsterBreathRulesTest.java)、[Schema2MonsterBreathRuntimeServiceTest](../../src/test/java/com/dndtool/service/Schema2MonsterBreathRuntimeServiceTest.java)、[JdbcSchema2MonsterBreathRows](../../src/main/java/com/dndtool/persistence/JdbcSchema2MonsterBreathRows.java)。 |

## 角色构筑、种族、背景与成长

SRD 页码均指 PDF 原文页；相邻页可能含续表。数据列中的“旧目录”只指[角色目录](#catalog)，不表示 schema 2 已有相同可运行实体。

| 机制族与原文 | 当前数据 | 当前执行 | 状态/恢复 | 依据与具体尚缺义务 |
|---|---|---|---|---|
| 种族/亚种身份、体型、速度、语言（[3—7](../references/srd-5.1/pages/page-003.md)） | 旧目录含 SRD 种族/亚种与关系；schema 2 有限来源元数据与授予 | 语言/工具来源有限选择；不执行完整种族 | 授予局部闭环 | [授予](#growth)；须把体型、速度、感官、所有种族特性和能力调整接到相同准确来源及消费者。 |
| 矮人：抗毒、战斗训练、石工知识、山丘矮人强韧（[3—4](../references/srd-5.1/pages/page-003.md)） | 旧特性元数据；部分熟练可表达 | 具体效果未接入 | 特性效果未建闭环 | [目录/成长](#catalog)；缺毒豁免/抗性来源、专门检定熟练倍率、逐级 HP 与重甲速度例外。 |
| 精灵/高等精灵：妖精血统、Trance、感官、戏法（[4](../references/srd-5.1/pages/page-004.md)） | 旧特性元数据 | 具体效果未接入 | 未建闭环 | [目录](#catalog)；缺魅惑优势/魔法睡眠免疫、特殊休息、黑暗视觉、种族施法授权及真实戏法。 |
| 半身人/轻足：Lucky、Brave、Nimbleness、Naturally Stealthy（[4—5](../references/srd-5.1/pages/page-004.md)） | 旧特性元数据 | 未接入 | 未建闭环 | [目录](#catalog)；缺自然 1 重掷及原骰关系、恐惧贡献、穿越体积、隐蔽合法候选。 |
| 人类能力提升（[5](../references/srd-5.1/pages/page-005.md)） | 旧创建 profile 有固定加值 | 旧单发布创建可派生；schema 2 起点未采用种族修正 | 旧角色切片；schema 2 缺项 | [创建](#catalog)、[成长](#growth)；不能用旧 profile 结果补填 schema 2 权威属性。 |
| 龙裔：血统、吐息、伤害抗性（[5—6](../references/srd-5.1/pages/page-005.md)） | 旧特性元数据 | 未接入 | 未建闭环 | [目录](#catalog)；缺血统选择→伤害/区域/DC、等级骰数、休息恢复与角色防御来源；怪物吐息不是种族能力。 |
| 侏儒/岩侏儒：Gnome Cunning、Artificer's Lore、Tinker（[6](../references/srd-5.1/pages/page-006.md)） | 旧特性元数据 | 未接入 | 未建闭环 | [目录](#catalog)；缺对魔法限定豁免优势、知识专门贡献、制作成本/时间/对象与寿命。 |
| 半精灵：属性选择、Skill Versatility、妖精血统（[6—7](../references/srd-5.1/pages/page-006.md)） | 旧创建选择/profile 与特性 | 旧创建有限选择；schema 2 未闭合种族 | 旧角色局部 | [创建/授予](#catalog)；缺统一种族属性与技能授予、魅惑/睡眠和感官消费者。 |
| 半兽人：Relentless Endurance、Savage Attacks、Menacing（[7](../references/srd-5.1/pages/page-007.md)） | 旧特性元数据 | 未接入 | 未建闭环 | [目录](#catalog)；缺零 HP 替代、每日资源、暴击额外武器骰及恐吓授予的完整来源。 |
| 提夫林：Hellish Resistance、Infernal Legacy（[7](../references/srd-5.1/pages/page-007.md)） | 旧特性元数据 | 未接入 | 未建闭环 | [目录](#catalog)；缺角色火抗来源、种族法术/能力/等级与每日成本、黑暗视觉。 |
| 背景 Acolyte、语言/技能/工具、起始装备与自定义（[59—61](../references/srd-5.1/pages/page-060.md)） | Acolyte 旧目录；schema 2 有限初始来源 | 语言/工具槽有限选择；不实现完整自定义背景 | 来源授予局部 | [授予](#growth)；缺全背景技能/装备选择、重复熟练替代规则、Shelter of the Faithful 的真实有界叙事流程。 |
| 属性生成、一级创建、描述/阵营、起始财富（[3](../references/srd-5.1/pages/page-003.md)、[56](../references/srd-5.1/pages/page-056.md)、[58—62](../references/srd-5.1/pages/page-058.md)） | 旧创建 profile；schema 2 标准数组 | 标准数组有限排列；不提供掷属性/购点或完整财富创建 | 各自局部；不互通补全 | [创建/成长](#growth)；缺统一完整初始角色、种族修正、财富/装备候选及相关来源快照。 |
| 升级、职业/子职业、ASI、专长获得、多职业前提（[56—58](../references/srd-5.1/pages/page-056.md)、[75](../references/srd-5.1/pages/page-075.md)） | 有限类型化逐级声明、前提、选项 | 有限选择并自动算 HP/PB/混合生命骰；专长效果仍阻断 | 成长局部闭环 | [成长](#growth)；不得跳过到期 OPTION 义务；缺所有职业效果消费者、XP 奖励/升级工作流与完整战役初始化。 |
| 多职业施法、Pact Magic、同名职业能力合并（[57—58](../references/srd-5.1/pages/page-057.md)） | 旧目录有贡献/法术位计算；schema 2 非通用施法 | 纯计算不能授予施法；现行 profile 排除多职业 | 施法合并未闭合 | [成长](#growth)、[施法](#spells)；缺逐来源已知/准备、共享位与 Pact 分池、仪式/焦点、Extra Attack/Unarmored Defense 等合并语义。 |
| Grappler 专长效果（[75](../references/srd-5.1/pages/page-075.md)） | 前提与获得可记录，效果 BLOCKED | 未接入 | 获得局部；效果未建 | [成长](#growth)；须接擒抱关系、对被擒目标攻击优势、pin 动作与双方 restrained，不能按一般 ASI 获得计为完成。 |

职业表逐行区分“资源获得/容量/恢复”与“使用能力”。下列“资源协议”指旧目录已有相应资源声明、
schema 2 有真实通用容量/消费/标准休息消费者；没有证明该准确 SRD 内容的作者包、来源、镜像与业务路径已逐项采用。
旧 `ClassResourceRecoveryRules` 和 `ClassFeatureAdjudicationRules` 是规则组件，缺少其业务调用方，
不能把目录 `AUTOMATIC` 或 `DM_ADJUDICATION` 标签当作已执行的 SRD 职业能力。

| 职业机制族与原文 | 当前数据与真实能力 | 尚缺执行及状态责任 | 现有 owner/验证依据 |
|---|---|---|---|
| Barbarian / Berserker（[8—10](../references/srd-5.1/pages/page-008.md)） | 旧目录；成长可记录解锁；Rage 资源协议 | Rage 启动/维持/伤害与抗性、无甲防御、Reckless/Danger Sense、Extra Attack、Feral Instinct、Brutal Critical、Relentless Rage；Frenzy/耗竭、Mindless Rage、恐吓与 Retaliation 均缺真实消费者 | [成长](#growth)管理取得/资源；[战斗](#combat)及[效果](#spells)须消费。 |
| Bard / Lore（[11—14](../references/srd-5.1/pages/page-011.md)） | Bardic Inspiration/Font of Inspiration 资源协议 | 激励授予及骰后使用、Jack of All Trades/Expertise、Song of Rest、Countercharm、Magical Secrets/已知法术、Superior Inspiration、Cutting Words/Peerless Skill 的真实反应/原骰修正未接入 | [成长](#growth)；[检定](#foundation)、[施法/动作流](#spells)尚缺对应贡献。 |
| Cleric / Life（[15—18](../references/srd-5.1/pages/page-015.md)） | 一级单职业 Life、Bless/Cure Wounds、Disciple of Life 的有限类型化消费者；Channel Divinity 资源协议 | 完整准备/仪式、Domain Spells 各级、Turn/Destroy Undead、Preserve Life、Blessed Healer、Divine Strike、Supreme Healing、Divine Intervention 均缺；一级治疗分支不代表完整 Life | [施法](#spells)、[成长](#growth)局部闭环。 |
| Druid / Land（[19—23](../references/srd-5.1/pages/page-019.md)） | Wild Shape 资源协议；其余旧元数据/获得 | 野兽目录选择、形态/装备/HP 替代、游泳飞行门限、施法与形态冲突；准备/仪式、Natural Recovery、Circle Spells、Land's Stride/Nature's Ward/Sanctuary 未闭合 | [成长](#growth)；变形、施法、环境 owner 尚缺实际接合。 |
| Fighter / Champion（[24—25](../references/srd-5.1/pages/page-024.md)） | Second Wind/Action Surge/Indomitable 资源协议 | Fighting Style 选择/贡献、治疗 bonus action、额外 action、重掷及保存原骰、Extra Attack、Improved/Superior Critical、Remarkable Athlete、Survivor 未执行 | [成长](#growth)、[战斗](#combat)；当前普通 Attack 一次不承担 Extra Attack。 |
| Monk / Open Hand（[26—29](../references/srd-5.1/pages/page-026.md)） | Ki 资源协议 | Martial Arts/Flurry、无甲/移动、Patient Defense/Step、Deflect Missiles、Slow Fall、Stunning Strike、Ki 武器、Evasion/Diamond Soul、Empty Body/Perfect Self、Open Hand Technique/Wholeness/Tranquility/Quivering Palm 未闭合 | [成长](#growth)；动作、条件、豁免、防御和持续/延迟效果待接。 |
| Paladin / Devotion（[30—34](../references/srd-5.1/pages/page-030.md)） | Divine Sense/Lay on Hands 资源协议 | 感知/分配治疗/祛病毒、Fighting Style、施法/Divine Smite、Divine Health、Channel Divinity、光环、Cleansing Touch、Sacred Weapon/Turn/圣光状态等未执行 | [成长](#growth)；[施法/效果](#spells)尚缺光环及目标类型 owner。 |
| Ranger / Hunter（[35—38](../references/srd-5.1/pages/page-035.md)） | 旧元数据、解锁与有限成长选择 | Favored Enemy/Natural Explorer、施法、Fighting Style、Hunter's Prey/Defensive Tactics/Multiattack/Superior Defense 的具体选项、Primeval Awareness、地形/隐藏/感官和 Foe Slayer 未闭合 | [成长](#growth)遇未接选择须拒绝；战斗/冒险能力分别接入。 |
| Rogue / Thief（[39—41](../references/srd-5.1/pages/page-039.md)） | Stroke of Luck 资源协议；其余元数据/取得 | Expertise/Sneak Attack、Cunning Action、Uncanny Dodge、Evasion、Reliable Talent、Blindsense/Elusive、Fast Hands/攀爬/Use Magic Device/Thief's Reflexes 及 Stroke 使用时机未接入 | [检定](#foundation)、[战斗](#combat)、物品 owner 待消费；资源不是掷后改结果授权。 |
| Sorcerer / Draconic（[42—45](../references/srd-5.1/pages/page-042.md)） | Font of Magic 资源协议 | 已知施法/戏法、sorcery point↔slot 转换、全部 Metamagic 的授权/成本/变换、Sorcerous Restoration、Draconic Resilience/Elemental Affinity/飞行/Draconic Presence 未闭合 | [成长](#growth)、[施法](#spells)；不得把任意参数当 Metamagic。 |
| Warlock / Fiend（[46—51](../references/srd-5.1/pages/page-046.md)） | 旧元数据/获得；Pact Magic/Mystic Arcanum 特殊资源阻断 | Pact 分池/短休/已知、Pact Boon 的真实实体、各 Invocation、Eldritch Master、临时 HP/骰后加值/Fiendish Resilience/Hurl Through Hell 均缺执行与恢复 | [成长](#growth)保守阻断未接恢复义务；施法/宠物/武器/效果须分别消费。 |
| Wizard / Evocation（[52—54](../references/srd-5.1/pages/page-052.md)） | 单职业 1—5 级法术书学习/准备、Shield/Counterspell 局部；其他学习条目有元数据 | Arcane Recovery 已接入[有限内部恢复](schema-2-arcane-recovery.md)；Evocation Savant 抄录、Sculpt Spells、Potent Cantrip/Empowered/Overchannel、仪式、戏法、Spell Mastery/Signature Spells、六级以上成长/施法仍缺 | [施法](#spells)、[休息](#rest)；恢复须证明实际短休、每日标记、slot 选择及原事实恢复，不能将资源填满作为替代。 |

## 检定与骰子

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 属性/技能/工具检定（[76—82](../references/srd-5.1/pages/page-076.md)） | 有限类型化；DC、适用属性、DM 优劣势及说明有界输入，服务器派生与掷骰 | 局部闭环；工具须实际持有。缺替代技能属性、全部特性贡献及完整场景条件；裁定 DC/优劣势不代表开放式效果裁定 | [基础](#foundation) |
| 优势/劣势与自然骰（[76—77](../references/srd-5.1/pages/page-076.md)、[94](../references/srd-5.1/pages/page-094.md)） | 普通检定、有限攻击/先攻/豁免按实际范围自动合并；原骰保存 | 局部闭环；自然 1/20 只适用于对应类型，新增贡献须证明来源与时机 | [基础](#foundation)、[战斗](#combat)、[怪物](#monsters) |
| 普通豁免（[83](../references/srd-5.1/pages/page-083.md)） | 当前真实 save 入口限吐息 DEX、伤害后专注 CON、死亡豁免及既有特定分支 | 未有通用六属性 save 命令；缺各效果 DC/属性/自动失败/完整贡献与对应事件，不能拿能力检定替代 | [怪物](#monsters)、[施法](#spells) |
| 对抗/共同工作/团队检定（[77—79](../references/srd-5.1/pages/page-077.md)） | 原文；未接通机制 | 缺双方原骰/平手保持现状、帮助资格和目标、团队过半判定与原子历史；当前 CHECK 不能模拟多方根事件 | [基础](#foundation)为可扩展边界，无现成消费者 |
| 被动检定、感知/隐藏关系（[78—79](../references/srd-5.1/pages/page-078.md)、[94](../references/srd-5.1/pages/page-094.md)） | 怪物被动察觉可存元数据；未接真实察觉流程 | 缺 10+修正/优劣势±5、隐蔽/发现事实、观察者/被观察者与感官适用条件 | [基础](#foundation)、[怪物](#monsters) |
| 专精、半熟练、最低值、重掷/骰后替换（[12—14](../references/srd-5.1/pages/page-012.md)、[39—40](../references/srd-5.1/pages/page-039.md)） | 旧特性元数据；未接通 | 缺贡献去重/取整、原骰→重掷/替换关系、触发窗口、资源一次消费；Lucky/Reliable Talent/Indomitable 不因有 d20 工具而完成 | [成长](#growth)、[动作流](#combat) |
| 奖励/惩罚骰、伤害骰与百分骰（[76](../references/srd-5.1/pages/page-076.md)、[96](../references/srd-5.1/pages/page-096.md)及具体条目） | 真实 Bless d4、健康声明 d4—d12、近战伤害与有限共享 d6；通用组合/百分骰机制未接入 | 已接分支局部闭环；缺每种规则批准的骰式、修正时点与独立审计，不能接受客户端任意骰式或结果 | [基础](#foundation)、[施法](#spells)、[怪物](#monsters) |
| 灵感（[59—60](../references/srd-5.1/pages/page-059.md)） | 原文/旧元数据；未接入 | 缺授予/持有上限/转交、明确消耗时点与优势来源、并发和恢复；DM 优势布尔输入不是灵感资源 | [基础](#foundation)当前仅裁定检定情境 |

## 装备、经济与魔法物品

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 普通护甲/盾、工具、武器实例（[62—70](../references/srd-5.1/pages/page-062.md)） | 有限类型化；获得/移除/手位/穿脱、护甲 AC/熟练/力量/隐匿影响自动 | 局部闭环；不证明全部 SRD 物品目录已逐条收录。缺数量堆叠、容器、库存跨角色转移及战斗拾取 | [基础](#foundation) |
| 武器属性、远程、投掷、弹药、装填、临时/银质/特殊武器（[64—65](../references/srd-5.1/pages/page-064.md)） | 普通近战 profile 有限类型化，真实手持/属性/熟练和伤害；其余未接入 | 局部近战闭环；缺逐属性适用、近远距离、弹药扣除/回收、投掷实例位置、loading、improvised、net/lance、银质防御 gate | [战斗](#combat)、[基础](#foundation) |
| 货币换算、获得/买卖、财宝/服务/生活费用（[62](../references/srd-5.1/pages/page-062.md)、[72—74](../references/srd-5.1/pages/page-072.md)） | 原文；无经济消费者 | 缺精确金额、数量/售价规则、交易双方与库存同事务、费用/时间/服务有界裁定及历史恢复 | 未建经济 owner；不能用 ITEM_ACQUIRED 当作已付款 |
| 重量、携带/推拉/举起、负重变体（[69](../references/srd-5.1/pages/page-069.md)、[79—80](../references/srd-5.1/pages/page-079.md)） | 当前库存位置不是负重模型 | 缺重量/数量/容器容量、STR 与体型倍率、速度/检定贡献、变体选择、丢弃/转移的真实后果 | [基础](#foundation)仅实例 owner；负重消费者未建 |
| 冒险装备、装备包、容器、消耗品（[66—69](../references/srd-5.1/pages/page-066.md)） | 工具和少量材料物件协议；无完整 gear 使用目录 | 缺装备包展开、容量/装填、点燃/光源/绳索/攀爬/治疗包等各族的使用动作、消耗及生成对象 | [基础](#foundation)、[战斗](#combat)须分别接成本与效果 |
| 坐骑/载具/挽具、水运（[71—72](../references/srd-5.1/pages/page-071.md)） | 原文；未接入 | 缺生物/载具实体、价格/载重/速度、乘员与操纵、拉车/运输、位置与恢复 | 未建旅行/载具消费者 |
| 同调、穿戴/持用限制、同类/成对物品（[206](../references/srd-5.1/pages/page-206.md)） | 无真实同调/魔法装备消费者 | 缺前提、三件上限、短休绑定/解除、死亡/距离等结束条件、双向角色↔物品引用与恢复 | [休息](#rest)、[基础](#foundation)尚不承担同调 |
| 魔法物品激活、指令、充能/恢复、物品施法（[206—207](../references/srd-5.1/pages/page-206.md)） | 原文；普通实例不提供这些执行参数 | 缺准确物品目录、动作/成分例外、消耗与随机恢复、耗尽销毁、法术授权及物品来源贡献 | 物品使用 owner 未建；不得经 CAST 或健康效果绕过 |
| 魔法武器/护甲、属性设定、防御/感官、持续物品效果（[207—251](../references/srd-5.1/pages/page-207.md)） | 原文；没有完整可运行 item 数据 | 须按加值/替代值/抗性/免疫/感官/移动/光环等真实机制拆分；缺叠加/同调 gate、来源及摘下后撤销 | [效果](#spells)、[基础](#foundation)仅提供接合边界 |
| 药水/卷轴/召唤物品、诅咒、智慧物品与神器（[207—253](../references/srd-5.1/pages/page-207.md)、[251—252](../references/srd-5.1/pages/page-251.md)） | 原文；未接入 | 缺消耗/鉴定/随机后果、卷轴职业/能力 gate、召唤实体、不可自愿解除与 Remove Curse、智慧属性/冲突/神器各自的类型化裁定与历史 | 未建具体消费者；普通 DM 说明不能声明完成 |

## 冒险、环境、时间与休息

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 时间、旅行速度、行军与特殊速度（[84—85](../references/srd-5.1/pages/page-084.md)） | 单角色分钟/秒、平面步行探索与战斗六秒局部自动 | 缺完整战役共享时间、队伍 travel pace/强行军/导航、骑乘/特殊速度、所有可达后继；不从墙钟推断 | [休息](#rest)、[战斗](#combat)、[探索](#spells) |
| 困难地形、跳跃、攀爬、游泳、爬行（[85](../references/srd-5.1/pages/page-085.md)） | 当前普通步行不接纳 | 缺逐段成本/起跳助跑/距离/高度、STR 派生、失败后果与类型化裁定；倒地起身不代表爬行 | [战斗](#combat)空间边界 |
| 坠落、窒息（[86](../references/srd-5.1/pages/page-086.md)） | 直接环境健康效果可声明；两机制未实现 | 缺高度/坠落骰上限/落地 prone、屏息与窒息轮数/零 HP/禁止恢复等真实状态及计时；不能降为任意伤害 | [健康](#foundation)只是后果 owner |
| 光照/遮蔽、黑暗视觉/盲视/真实视觉、隐藏与察觉（[86](../references/srd-5.1/pages/page-086.md)） | 固定正常可见空间；怪物 senses 有限数据 | 缺光源/范围/障碍、观察者感官与空间资格、被动/主动发现、隐形位置和攻击/法术限制 | [战斗](#combat)、[怪物](#monsters)明确不授予感官执行 |
| 食物/水、饥渴、耗竭与恢复（[86—87](../references/srd-5.1/pages/page-086.md)、[358](../references/srd-5.1/pages/page-358.md)） | 原文；标准休息不自动结算 | 缺摄取量/天数/CON、耗竭累计与全部派生贡献、长休且进食饮水后的恢复；当前长休不能清空未建状态 | [休息](#rest)封闭范围 |
| 物体交互/破坏与临时行动（[87](../references/srd-5.1/pages/page-087.md)、[91](../references/srd-5.1/pages/page-091.md)、[203](../references/srd-5.1/pages/page-203.md)） | 装备实例与掉地可存；无通用物体战斗实体 | 缺免费交互额度、Use an Object、材质/AC/HP/伤害阈值/免疫、可破坏空间与开放行动的真实类型化裁定 | [基础](#foundation)、[战斗](#combat)；不能把生物 HP 表当物体 |
| 标准短休、生命骰、标准长休（[87](../references/srd-5.1/pages/page-087.md)） | 有限类型化；活动段、期限/中断、逐骰治疗、资源和生命骰恢复有限选择+自动 | 单角色局部闭环；缺场景持续效果下的角色休息、多角色共同时间、种族/职业替代与特殊额外义务 | [休息](#rest) |
| Arcane Recovery、Natural Recovery 及特殊恢复（[21](../references/srd-5.1/pages/page-021.md)、[53](../references/srd-5.1/pages/page-053.md)及各职业） | Arcane Recovery 已接入[有限内部恢复](schema-2-arcane-recovery.md)；其余不因 recovery_profile 存在而可执行 | 须验证真实休息、使用频率/日界、恢复位等级总额与上限、选项、每日标记和直接恢复；Natural Recovery 等未接入 | [休息](#rest)、[施法](#spells)共同 owner |
| 休整：制作/工作/疗养/研究/训练（[88—89](../references/srd-5.1/pages/page-088.md)） | 原文；未接入 | 缺活动预算、金钱/材料/时间/熟练、进度与结果、疗养疾病毒素后果、研究信息和训练的有界裁定 | 未建 downtime 消费者 |
| 社交、背景叙事与开放式能力（[60—61](../references/srd-5.1/pages/page-060.md)、[82](../references/srd-5.1/pages/page-082.md)） | 有界检定可记录情境说明；旧目录有 DM 标签 | 缺具体裁定类型、对象/限制/成本/获益、事件及恢复；不把 SUCCESS/FAILURE/NO_EFFECT 纯函数视为完整业务 | [检定](#foundation)、[目录](#catalog) |
| 陷阱：侦测/解除/触发、简单/复杂及各示例（[195—198](../references/srd-5.1/pages/page-195.md)） | 原文；直接健康效果不是完整陷阱数据 | 缺陷阱实例/位置、被动/主动检测、触发条件、攻击/豁免/伤害/重置、复杂回合与全部目标事实 | 检定、空间、共享伤害可复用；尚无陷阱 owner |
| 疾病、毒药、疯狂（[199—205](../references/srd-5.1/pages/page-199.md)） | 原文；未接入 | 须分别建暴露/传染/给药、豁免/潜伏/周期/重复豁免、毒伤与 poisoned、病程/治愈、疯狂随机表及类型化行为裁定，不能压成一个状态备注 | 健康/效果/时间接合待建 |
| 神祇、位面与位面旅行（[360—365](../references/srd-5.1/pages/page-360.md)） | 本地参考文本，不是运行规则目录 | 叙事资料可查与机制支持分别判定；若法术/能力涉及位面、神祇或旅行，须有身份、合法目的地/限制、耗时与真实结果裁定 | 未建位面运行消费者；不要求把纯背景叙述虚构成算法 |

## 战斗动作、空间、伤害与条件

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 遭遇/先攻/同值/回合/轮次（[90—91](../references/srd-5.1/pages/page-090.md)） | 有限类型化；警觉可见角色及有限怪物，服务器先攻、同值有限排列 | 局部闭环；缺突袭、完整遭遇 roster、阵营变化、非参战生物与 XP 工作流 | [战斗](#combat)、[怪物](#monsters) |
| action/bonus action/reaction、回合重置（[90—91](../references/srd-5.1/pages/page-090.md)） | 普通 action、reaction、移动预算和已接反应自动；未提供通用 bonus action 消费者 | 局部闭环；缺每个 bonus producer、额外 action/attack、每回合施法限制及其时序，不可仅添加额度字段 | [战斗](#combat)、[施法](#spells) |
| 普通近战 Attack、命中/暴击/伤害（[93—97](../references/srd-5.1/pages/page-093.md)） | 普通手持近战 profile、一次 Attack；真实属性/熟练/AC、原骰及健康 | 局部闭环；缺非致命击倒、徒手/天然武器及其他属性/多个伤害分量/特性修正；每种新 profile 须完整来源 | [战斗](#combat) |
| 远程/投掷/双持、Extra Attack（[93—95](../references/srd-5.1/pages/page-093.md)） | 未接入 | 缺射程与贴身劣势、遮蔽/弹药/投掷、附赠攻击手位和伤害规则、多攻击序列与限制；不能循环调用普通 ATTACK | [战斗](#combat)当前明确边界 |
| Dash/Disengage/Dodge（[93](../references/srd-5.1/pages/page-093.md)） | 已接 action 成本及速度/本回合 OA 免除/攻击和 DEX save 贡献 | 局部闭环；新增失能、不可见、速度为零或特殊动作来源须消费真实贡献 | [战斗](#combat)、[怪物](#monsters) |
| Help（[93](../references/srd-5.1/pages/page-093.md)） | 未接入 | 缺援助对象/任务/五尺敌人资格、下一次适用检定或攻击的单次优势、本人下回合期限及消费事实 | [战斗](#combat)、[检定](#foundation)待接 |
| Hide/Search（[93—94](../references/srd-5.1/pages/page-093.md)） | 未接入；普通技能检定不足 | 缺隐藏关系/可被感知资格、主动搜索目标与被动值、观察者专属结果、失去隐藏和攻击影响 | 空间/感知/检定 owner 须共同接合 |
| Ready（[93—94](../references/srd-5.1/pages/page-093.md)） | 有限 attack/move/spell 及封闭完成事实触发、reaction、持能量专注 | 局部闭环；不解释任意自然语言触发，不开放其他物体/能力响应；新触发须有真实 producer 与恢复闭包 | [施法](#spells)、[动作流](#combat) |
| Use an Object、拾取/重新装备、临时行动（[91](../references/srd-5.1/pages/page-091.md)、[94](../references/srd-5.1/pages/page-094.md)） | 战斗掉地已保存；使用/拾取未接入 | 缺免费交互与 action 区分、目标距离/手位、物品使用成本/效果/归属和类型化临时行动结果 | [基础](#foundation)、[战斗](#combat) |
| Grapple/Shove/escape（[95—96](../references/srd-5.1/pages/page-095.md)） | 未接入 | 缺 Attack 内替代额度、手位/体型/触及、Athletics 对抗候选、平手、抓取者关系、拖拽/脱离/失能终止、push/prone 后果 | 对抗/位置/条件须同根事件，不借普通 damage 模拟 |
| 移动/OA/起身（[91—95](../references/srd-5.1/pages/page-091.md)） | 平面相邻未占格步行、精确半速起身、PC 普通武器 OA 与逐段暂停 | 局部闭环；缺怪物 OA、穿越/挤压/爬行/地形、teleport/强制移动的触发例外；敌对可反应怪物使相关 PC 移动保守拒绝 | [战斗](#combat)、[怪物](#monsters) |
| 体型空间、触及、掩护/遮挡、区域（[92](../references/srd-5.1/pages/page-092.md)、[96](../references/srd-5.1/pages/page-096.md)、[102—103](../references/srd-5.1/pages/page-102.md)） | 单点格、正常可见、无遮挡；有限锥形和固定半径区域 | 局部目标证明；缺大体积/三维/障碍、半/四分之三/全掩护、所有区域形状与完整战外 roster；点格政策不代表通用 SRD 空间引擎 | [战斗](#combat)、[怪物](#monsters)、[施法](#spells) |
| 伤害类型、抗性/易伤/免疫、共享伤害（[96—97](../references/srd-5.1/pages/page-096.md)） | 有限吐息共享骰及准确怪物防御/适用 gate；PC 来源不通用 | 局部闭环；缺角色防御 producer、多分量及一般物理/魔法/攻击门限、伤害后反应如 Uncanny Dodge、Evasion/Legendary Resistance | [怪物](#monsters)；共享核不能授予所有伤害机制 |
| 治疗/temp HP/零 HP/稳定/死亡/即死（[97—99](../references/srd-5.1/pages/page-097.md)） | 真实有限健康、回合死亡豁免、稳定期限、掉物/倒地；怪物有限零 HP 死亡政策 | 局部闭环；缺医疗动作稳定、非致命击倒、复活、最大 HP 变化和特性替代后果；治疗不自动起身/捡物 | [健康](#foundation)、[战斗](#combat)、[怪物](#monsters) |
| 坐骑战斗、水下战斗（[99](../references/srd-5.1/pages/page-099.md)） | 未接入 | 缺骑乘/下马与独立或受控坐骑先攻/action/位置、跌落/OA、游泳速度与武器/火抗规则 | 未建对应战斗消费者 |

状态必须逐种审核 producer、多个来源的独立期限、免疫、贡献及解除。已有零 HP 后果不能证明下列状态的外部施加均可执行。

| 状态与原文 | 当前实际范围 | 仍缺的具体合同/执行/恢复责任 |
|---|---|---|
| Prone（[359](../references/srd-5.1/pages/page-359.md)） | 零 HP 引起倒地、攻击优劣势、起身；[战斗](#combat)局部 | 其他 producer、爬行、完整来源/期限/解除关系尚未接入。 |
| Unconscious / Incapacitated（[358—359](../references/srd-5.1/pages/page-358.md)） | 零 HP 健康及战斗动作阻断/掉物/近距暴击/适用自动失败、专注强制结束局部 | 法术/条件外部施加、非 HP 昏迷/失能来源、重叠/到期、感知与所有 STR/DEX save 消费者未完整建立。 |
| Blinded（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺视觉检定自动失败、可视施法/发现资格、双向攻击优势劣势、来源/期限/免疫。 |
| Deafened（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺听觉检定自动失败、感知触发与来源/期限/免疫。 |
| Charmed（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺准确 charmer 身份、有害目标禁止、社交优势、来源/解除与怪物免疫消费。 |
| Frightened（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺恐惧来源/视线、攻击与检定劣势、靠近禁止、重复豁免与解除。 |
| Grappled（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺抓取关系、速度为零、reach/强制移动/抓取者失能解除及移动因果。 |
| Invisible（[358—359](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺特殊感官与重遮蔽、仍可定位/被发现、双向攻击贡献、隐藏及施法可见性。 |
| Paralyzed（[358—359](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺失能、不可移动/说话、STR/DEX 自动失败、受击优势及五尺暴击、来源/期限。 |
| Petrified（[359](../references/srd-5.1/pages/page-359.md)） | 未接入 | 缺形态/重量/携物、感知/行动限制、STR/DEX 自动失败、全伤抗性、毒病免疫及暂停而非治愈。 |
| Poisoned（[359](../references/srd-5.1/pages/page-359.md)） | 未接入 | 缺攻击/能力检定劣势、毒药/怪物 producer、持续/重复豁免、免疫及解除。 |
| Restrained（[359](../references/srd-5.1/pages/page-359.md)） | 未接入 | 缺速度为零、双向攻击贡献、DEX 劣势、束缚来源/挣脱/多重实例。 |
| Stunned（[359](../references/srd-5.1/pages/page-359.md)） | 未接入 | 缺失能/移动/说话、STR/DEX 自动失败、受击优势及各 producer/解除。 |
| Exhaustion（[358](../references/srd-5.1/pages/page-358.md)） | 未接入 | 缺层级累计/上限死亡、检定→速度→攻击/豁免→最大 HP 的全部贡献，以及食水/长休恢复条件。 |

持续伤害/治疗、回合开始/结束、重复豁免、受击结束、光环/进出区域、同名不同强度和状态免疫等
仍须各自 producer 和完整后继闭包；当前 Bless/Shield/Ready/固定抗魔实例的[局部效果 owner](#spells)
只证明其合同明确覆盖的贡献、同强最早优先、压制与到期，不是通用状态引擎。

## 施法授权、成本与法术效果族

法术目录、施法授权和效果执行分开计账；学会/准备元数据法术只形成知识事实，不形成施放能力。

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 法术目录/职业列表（[100—114](../references/srd-5.1/pages/page-100.md)） | 有限封闭算法及 Wizard 可学元数据名单；不覆盖全部列表/条目 | 缺完整 SRD 条目的类型化参数、全部职业关系、来源/组合链/镜像与逐条机制映射；全文存在不计目录完成 | [施法](#spells) |
| 准备/已知/法术书/领域授权（[100](../references/srd-5.1/pages/page-100.md)、[15—17](../references/srd-5.1/pages/page-015.md)、[52—54](../references/srd-5.1/pages/page-052.md)） | 一级 Life 始终准备两法术；单职业 Wizard 1—5 级真实学习/升级/准备与长休标记 | 局部闭环；缺其余职业/种族/专长/怪物/物品授权、戏法、全部始终准备例外、抄录与仪式名单 | [施法](#spells) |
| 法术位/升环/多职业/Pact（[100—101](../references/srd-5.1/pages/page-100.md)、[57—58](../references/srd-5.1/pages/page-057.md)） | 有限一至三环资源、已接法术的固定允许位；真实成本一次消费 | 局部闭环；缺通用升环效果、四至九环、多职业合并/分来源已知、Pact 分池及转换/特殊恢复 | [施法](#spells)、[成长](#growth) |
| 施法时间/组件/法器/材料（[101—102](../references/srd-5.1/pages/page-101.md)） | action、reaction 与 Ready；正常发声/空手、真实圣徽、护甲禁施法 | 局部闭环；缺 bonus action 限制、长时施法/中断、沉默/绑缚、组件袋/各法器、定价/消耗材料、免组件例外 | [施法](#spells) |
| 目标/区域/可见与路径（[102—103](../references/srd-5.1/pages/page-102.md)） | 当前角色定向法术与正常可见点格；怪物锥形另有完整内部 roster | 缺全生物/物体/位置目标、敌友/类别/感官、全部形状/体积/掩护及更广场景 reader；怪物部署不自动授权其成为每种法术目标 | [施法](#spells)、[怪物](#monsters) |
| Bless（[122](../references/srd-5.1/pages/page-122.md)） | 有限类型化、真实一级 Life 授权、有限目标选择、成本/专注/d4 贡献 | 局部闭环；新增 save/attack 消费者必须显式消费有效贡献；不证明所有职业/升环/目标范围已支持 | [施法](#spells) |
| Cure Wounds（[132](../references/srd-5.1/pages/page-132.md)） | 有限类型化、一级 Life、触及/组件/slot/d8+WIS+生命门徒 | 局部闭环；缺其他授权/升环、怪物类型例外及其他治疗/复活效果；不能把普通环境治疗当作施法 | [施法](#spells)、[健康](#foundation) |
| Shield / Counterspell（[179](../references/srd-5.1/pages/page-179.md)、[131](../references/srd-5.1/pages/page-131.md)） | Wizard 已准备来源、准确反应窗口、AC 重判/有限自动反制、嵌套与一次成本 | 局部闭环；Magic Missile producer、四环以上 counter ability check/升环、未知施法来源与其他反应尚缺 | [施法](#spells)、[动作流](#combat) |
| Burning Hands（[123](../references/srd-5.1/pages/page-123.md)） | **有限类型化／自动＋有限选择／局部闭环**：[真实法术来源消费者](schema-2-spell-save-damage.md)接入 Wizard 授权、一次成本、反制、完整锥形目标、共享 3d6/有限升环、DEX 半伤/防御、健康/专注、伤害后 Ready 与暂停直接恢复；旧元数据不升级 | 仅正常发声、双手空闲、未获得 Sculpt 的合同分支；点燃对象、活动抗魔、Ready 持有范围法术、探索施放和其他位阶仍阻断；未支持的嵌套对象风险在付款前拒绝 | [施法](#spells)、[共享伤害](#monsters) |
| 其他瞬时伤害：法术攻击/单体 save/自动命中/多束（[114—194](../references/srd-5.1/pages/page-114.md)） | 元数据名单中的 Magic Missile 等仍拒绝执行；没有通用执行授权 | 须分别建攻击/命中/多束目标分配、每束或共享骰、伤害类型/升环、Shield 免伤、反应与完整后果；共享吐息不能替代 | [施法](#spells)扩展边界 |
| 持续伤害/治疗/区域、光环、重复豁免（[114—194](../references/srd-5.1/pages/page-114.md)） | 无这些法术的闭合消费者 | 缺进入/离开/开始/结束触发、同回合频率、来源移动/失能、批量伤害及共同贡献 cut、结束/压制/到期/恢复 | [效果协议](#spells)为目标，不能按 Bless 实例结构推断完成 |
| 控制/魅惑/恐惧/失明/睡眠/麻痹等（[114—194](../references/srd-5.1/pages/page-114.md)） | 部分名称可学习为元数据；具体效果未接入 | 缺各法术目标 gate/HP 池/重复豁免/受击解除、完整状态贡献/免疫、专注和跨场景期限；逐内容映射到上方状态族 | [施法](#spells)、状态表 |
| 增益/减益、护甲/防御、能力变换（[114—194](../references/srd-5.1/pages/page-114.md)） | Bless/Shield 是有限实例；其他未接入 | 缺准确加值或替代值、应用对象/资格、同名不同强度、抗性 gate、伤害前后反应与撤销；Protection from Energy 不在当前真实消费者内 | [施法](#spells)、[共享伤害](#monsters) |
| 移动/传送/飞行/墙体/地形（[114—194](../references/srd-5.1/pages/page-114.md)） | 未接入对应法术 | 缺目的地/路径/阻挡、体积/高度、强制移动/OA 例外、地形与区域实体、持续和失能后果 | [战斗](#combat)、[效果](#spells)须共同接合 |
| 召唤/创造生物、熟悉魔宠、死灵与支配（[114—194](../references/srd-5.1/pages/page-114.md)） | 未接入 | 缺新实体/控制者/阵营/先攻/命令额度、准确属性块/动作、共享 HP 与消失/死亡/专注后果、完整保存引用 | 角色/怪物/场景 owner 必须统一，不能生成为临时 PC |
| 变形/形态替代（[114—194](../references/srd-5.1/pages/page-114.md)） | 未接入 | 缺原形/现形的 HP/属性/资源/装备与权限、溢出伤害、种类与 CR gate、回退/死亡及恢复；不能只改显示名 | 形态消费者未建 |
| 幻象/侦知/预言/交流/旅行/愿望等开放效果（[114—194](../references/srd-5.1/pages/page-114.md)） | 原文及少量元数据；无真实类型化裁定消费者 | 每族须明确可裁定结果、对象/信息权限/空间、成本、抵抗/交互检定、生成事实与事件；自由说明或纯成功/失败不是完整支持 | [施法扩展目标](#spells) |
| 解除/反魔法/复活/永久改造（[114—194](../references/srd-5.1/pages/page-114.md)） | Counterspell 与 DM 固定抗魔区域局部；区域不是 Antimagic Field 法术 | 缺 Dispel/Remove Curse 等目标与等级检定、复活时间/魂/尸体/材料 gate、永久事实变更与恢复；固定压制不能替代任意驱散 | [施法](#spells) |

## 怪物、NPC、被动与遭遇

| 机制族与原文 | 当前数据/执行 | 状态/恢复与具体缺口 | 依据 |
|---|---|---|---|
| 属性块/CR/XP/类型/感官/语言（[254—258](../references/srd-5.1/pages/page-254.md)、[261—357](../references/srd-5.1/pages/page-261.md)、[366—403](../references/srd-5.1/pages/page-366.md)） | 有限类型化 stat_block 协议、Hell Hound/Mummy 验证实例；不代表所有 SRD 属性块已收录 | 有限实例局部闭环；缺全目录逐项映射、更多体型/运动、感官/语言/telepathy 执行；显式 save bonus 不能再叠 PB/属性 | [怪物](#monsters) |
| 部署/位置/先攻/HP/死亡（[254—259](../references/srd-5.1/pages/page-254.md)） | SMALL/MEDIUM 点格、有限真实 roster、怪物回合和零 HP 死亡 | 局部闭环；缺大体型、纯怪物场景、完整战外生物 roster、击败/俘获/战利品与 XP 奖励 | [怪物](#monsters)、[战斗](#combat) |
| 普通近战/远程、天然武器、Multiattack（[259](../references/srd-5.1/pages/page-259.md)） | Bite/Rotting Fist/Multiattack 等 BLOCKED_METADATA | 未建执行闭环；须接准确攻击加值/触及/目标/骰分量、序列限制、命中附加效果、OA/reaction producer；PC 武器攻击不能自动覆盖怪物 | [怪物](#monsters) |
| Hell Hound Fire Breath / Recharge（[321—322](../references/srd-5.1/pages/page-321.md)） | 唯一有限可执行怪物动作，15 尺点锥、DEX12、共享 6d6、半伤、recharge5—6 | 完整目标证明/共享骰/分阶段健康与专注、真实回合 recharge 和怪物休息局部闭环；不泛化任意吐息或区域 | [怪物](#monsters) |
| 其他 save 动作：凝视/恐惧/疾病毒素/死亡（[259](../references/srd-5.1/pages/page-259.md)及各属性块） | 元数据或原文；例如 Dreadful Glare/Rotting Fist 未执行 | 缺可见/距离/目标类型、豁免与重复、免疫期限、状态/最大 HP/疾病/诅咒及死亡后继，不能只登记 damage_profile | [怪物](#monsters)、条件/健康 owner 待接 |
| 怪物防御/被动：抗性、易伤、免疫、条件免疫（[257](../references/srd-5.1/pages/page-257.md)） | Hell Hound FIRE 免疫/Mummy FIRE 易伤、NONMAGICAL_ATTACKS gate 真实消费 | 局部闭环；缺所有防御来源/适用属性/魔法武器判定、Legendary Resistance、Magic Resistance、Evasion/特殊减伤、条件免疫的真实 producer | [怪物](#monsters) |
| 感知、Pack Tactics 与其他 traits（[257—259](../references/srd-5.1/pages/page-257.md)及各属性块） | 有限 scope 能证明 hearing/smell 感知或 Pack Tactics 对当前吐息无贡献；不执行其本族 | 未建其执行闭环；缺感知/相邻盟友攻击贡献、再生/回避/反伤/死亡爆发等每个被动；UNSUPPORTED 必须在根预检拒绝 | [怪物](#monsters)；“与本动作无关”不是被动已经实现 |
| Innate Spellcasting/Spellcasting/Psionics（[258—259](../references/srd-5.1/pages/page-258.md)） | 属性块说明不是施法授权 | 缺已知/每日次数/slot、能力/DC/组件例外、怪物动作经济、法术效果及跨场景恢复；PC Wizard profile 不能用于怪物 | [怪物](#monsters)、[施法](#spells) |
| 附赠/反应、Parry/OA/反制等（[259](../references/srd-5.1/pages/page-259.md)及各属性块） | 怪物 reaction 状态可存；无怪物攻击/OA 可执行 producer | 缺各触发/候选/代价/伤害前后窗口、继续与恢复；遇可能 OA 的敌对怪物相关移动须拒绝，不能静默跳过 | [怪物](#monsters)、[动作流](#combat) |
| 使用次数/短长休/充能恢复（[259](../references/srd-5.1/pages/page-259.md)） | 吐息 turn-start recharge、显式怪物短/长休恢复局部 | 缺其他每日/次数/随机/受条件恢复、怪物治疗与生命骰、不同资源来源；怪物休息不授予 PC 休息收益 | [怪物](#monsters) |
| 传奇动作/传奇抗性（[260](../references/srd-5.1/pages/page-260.md)及各传奇属性块） | 原文；未接入 | 缺他人回合结束触发、逐选项成本、本人回合重置/突袭失能 gate；传奇抗性须在失败后由真实有限选择改结果并扣次数 | [动作流](#combat)须新增真实 owner，不复用普通 reaction 余额 |
| 巢穴动作/区域效应（[260](../references/srd-5.1/pages/page-260.md)及实际具有该段落的内容） | 原文通用结构；无消费者 | 只按 SRD 实际收录条目建内容，不补商业书选项；缺 initiative20 败同值、失能/突袭 gate、区域生命周期/死亡后消散及恢复 | 场景/时间/效果 owner 待接 |
| 召唤/变形/吞咽/擒抱/附体及其他特殊动作（各属性块 [261—403](../references/srd-5.1/pages/page-261.md)） | 原文；未接入 | 须按实体/形态/容纳关系/控制权/脱离、伤害共享/内部位置、来源死亡的后继分别建族；不能以 monster stat_block 文本代替 | [怪物](#monsters)只提供有限部署和伤害 owner |
| NPC/其他生物/遭遇 XP（[258](../references/srd-5.1/pages/page-258.md)、[366—403](../references/srd-5.1/pages/page-366.md)） | 同有限 stat_block 协议可表达部分事实；无完整 NPC/遭遇业务 | 缺全 SRD 属性块、实际动作/特性、XP 汇总/分配与持久化；难度功能限 SRD 实际定义，不能引入未收录的 DMG 算法冒充 SRD | [怪物](#monsters)、[完整目标](srd-5.1-complete.md#28-怪物npc-与遭遇支持) |

## 全域状态、产品与验收责任

| 责任 | 当前实际边界 | 进入完整支持前必须补齐 |
|---|---|---|
| 来源、安装、接受、组合与冻结 | 有限作者协议和类型化来源/镜像可测；非空领域与完整 base 的资格仍按采用门拒绝 | 准确完整基础材料、全部机制支持检查、合法组合、安装/接受与业务初始化分别完成；来源事实不等于业务授权。 |
| 共同事务、版本、幂等 | [共同事务](business-write-transactions.md)与当前内部 owner 接合 | 每个新机制保持全请求/规则/目标/版本/容量先于 RNG；根事件、子状态、原骰、效果后继和接受结果同事务；UNKNOWN 仅查证原操作。 |
| 历史、严格读取、直接恢复 | 角色/场景/怪物各自局部恢复；保留原规则绑定，不重新掷骰/收费/触发 | 全域引用图、所有暂停阶段、结束/清理/重新开始、完整文件字段及容量、独立读回与故障回滚；局部 writer/reader 自洽不足。 |
| Host、规则查询、DM 工作流 | 现有 Host 不能视为完整 schema 2 游戏入口 | 完整角色/遭遇/施法/物品/休息有界 API 与 UI、合法候选查询、准确诊断及真实类型化裁定；受支持入口继续限 loopback 单 DM。 |
| 发布与 archive format 2 | 完整身份 DRAFT、legacy 默认及 archive 门保持 | [完整目标完成门](srd-5.1-complete.md#6-完成判定)中的跨领域目录/规范字节/摘要、完整文件往返、数据库语义、测试和 WAR 验收分别完成；无单族自动发布。 |

## 下一批闭合单元与 owner

这些是可审查的后续接合边界，表示依赖和缺口，不是交付或发布承诺。每批只把实际通过责任清单的准确内容改为支持。

| 闭合单元 | 现有实际 owner | 必须一起交付的边界 |
|---|---|---|
| 其余法术位特殊恢复 | 角色服务、休息规则、法术书规则及角色仓储 | 以已接入的有限 Arcane Recovery 为基础，扩展其余准确恢复来源；Natural Recovery 等仍须自己的资格、休息/日界、位阶选择和原事实恢复，不能借同类算法自动开放。 |
| 范围法术的其余完整语义 | 战斗服务、施法规则、共享伤害规则、场景仓储 | 以有限 Burning Hands 为基础，分别接通点燃对象、Sculpt 选择、抗魔区域、Ready 持有及探索施放的真实 owner；每个新法术仍须自己的授权、成本、目标/伤害参数、反应与恢复闭环。 |
| 普通生物攻击与怪物 OA | 战斗规则/服务、怪物规则、动作流及装备/健康事实 | 建怪物攻击 profile、Multiattack 边界和真实 reach/reaction，PC MOVE/Ready move 不再因缺失 OA producer 被迫拒绝；逐动作保留伤害与后继。 |
| 通用 save/对抗与 Grapple/Shove | 检定规则、战斗规则/服务、角色/场景仓储 | 完整 save/双方检定事实、平手与目标候选、Attack 替代额度、抓取关系/体型/手位/移动/失能解除；专长只在其真实关系具备后采用。 |
| 条件 producer 与贡献 | 持续效果、健康、战斗/检定、施法 owner | 先按逐状态表闭合一个真实 producer、免疫、叠加/期限及所有受影响消费者，再扩展内容；不能用通用条件名称集合代替语义。 |
| 角色防御与伤害后反应 | 成长/来源、效果 owner、共享伤害与动作流 | 明确来源与 gate、同一 APPLY/CON cut、失败后有界选择、资源一次消费；Protection from Energy、Cutting Words、Evasion 等分别接入。 |
| 普通经济/库存后再接魔法物品 | 当前装备实例 owner；经济/同调/使用 owner 尚须建立 | 数量/重量/金钱/容器事务与战斗交互先闭合；同调/充能/物品施法/诅咒分别接休息、授权、效果及恢复。 |
| 角色休息接场景与环境周期 | 休息规则、探索/战斗唯一时间、健康/效果 owner | 多角色共同时间、存续效果/疾病/饥渴/耗竭的到期与后继闭包；不能取消单角色约束后沿用独立时钟。 |
| 全域文件与产品入口 | 准备/快照/共同事务、角色与场景仓储、完整文件/Host 尚未采用 | 统一完整基础、全域当前状态/不可变历史/暂停/恢复/清理与 UI/API；完成后再独立进入跨领域发布门。 |

维护本表时同步更新具体领域合同、[采用边界](../design/v2/adoption-boundaries.md)及必要的
[任务地图](../agent-context.md)。只替换已有证据支持的当前边界，不把计划、旧测试结果、迁移文件或原文收录写成运行事实。
