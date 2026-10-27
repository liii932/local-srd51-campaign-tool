# Schema 2 施法与固定抗魔环境作者来源

本文定义 `adventure.spell`、`character.spellcasting`、`character.wizard_spellcasting`、
`adventure.environment` 和真实圣徽物件的作者输入、纯组合、
准确来源与 INTERNAL 运行镜像。沿用[作者基础](schema-2-foundation-authoring.md)、
[生产制品](schema-2-rule-artifacts.md)、[来源账本](schema-2-source-ledger.md)和
[战斗作者合同](schema-2-combat-authoring.md)。运行消费者与持久化生命周期见
[有限施法与持续效果](schema-2-spells.md)。类型登记不改变完整基础包、非空 add-on、
Host、完整战役、archive format 2 或跨域发布资格。

## 准确身份与法术算法

定义必需完整 `identity={kind:rule,module_key,rule_type,rule_key}`、`display_name`、
`description`、`source_page` 和 `algorithm`，可选 `author_note`。引用必须是完整规则身份，
不能以显示名称、裸规则键或客户端给定的派生值代替。Unicode 码点、NFC、控制符、
精确 JSON 整数词法、未知字段和直接依赖规则继承作者基础合同。

| 类型 | 规则键前缀 | 算法 | 原文页 |
| --- | --- | --- | --- |
| `adventure.spell` | `spell.*` | `SRD_2014_BLESS_V1` | 122 |
| `adventure.spell` | `spell.*` | `SRD_2014_CURE_WOUNDS_V1` | 132 |
| `character.spellcasting` | `spellcasting.*` | `LIFE_DOMAIN_LEVEL1_V1` | 16—17 |
| `character.wizard_spellcasting` | `spellcasting.*` | `SRD_2014_WIZARD_PREPARED_V1` | 52—53 |
| `adventure.spell` | `spell.*` | `SRD_2014_SHIELD_V1` | 179 |
| `adventure.spell` | `spell.*` | `SRD_2014_COUNTERSPELL_V1` | 131 |
| `adventure.environment` | `environment.*` | `DM_ANTIMAGIC_ZONE_V1` | 0，明确为 DM 环境家规 |

`adventure.spell` 没有自由机制参数。算法完整拥有法术环位、施法时间、射程、组件、目标、
骰子、持续时间和专注要求，作者不能额外附加自由伤害、治疗、脚本或 handler。Bless 是 1 环、
一个动作、30 feet、V/S/M、最多三个不同生物、专注至多一分钟的攻击检定与豁免 d4；
Cure Wounds 是 1 环、一个动作、触碰、V/S、即时 `1d8 + Wisdom 修正` 治疗，
生命领域门徒再增加 `2 + 法术环位`，本切片恰为 +3。高环施放、额外法术及额外目标扩展
均须有完整消费者后才能开放，不能借通用环境健康效果绕过施法校验。

原文依据为 [Cleric 与准备规则 p.16](../references/srd-5.1/pages/page-016.md)、
[Life Domain p.17](../references/srd-5.1/pages/page-017.md)、
[Bless p.122](../references/srd-5.1/pages/page-122.md)和
[Cure Wounds p.132](../references/srd-5.1/pages/page-132.md)。出处不代替运行支持证据。

## 生命领域 1 级施法来源

`character.spellcasting` 另外必需下列原子字段。最终组合至多有一个启用配置，
且配置的所有引用必须存在、类别准确，启用配置还要求全部目标启用。
原始声明和最终组合分别校验；后包覆盖不能掩盖原始错误。

| 字段 | 封闭约束 |
| --- | --- |
| `owner_class` | 完整 `character.class` 引用 |
| `required_subclass` | 完整 `character.subclass` 引用；归属同一职业且 `selection_level=1` |
| `required_feature` | 完整 `character.feature` 引用；归属该子职业、`level=1`、`feature_kind=FEATURE`、`execution_mode=AUTOMATIC`、`execution_algorithm=LIFE_DOMAIN_LEVEL1_V1` |
| `slot_resource` | 完整 `character.resource` 引用；归属同一职业、`maximum_profile=1-20:2`、`recovery_profile=1-20:LONG_REST`、`execution_mode=AUTOMATIC` |
| `bless` | 完整 `adventure.spell` 引用，算法必须为 `SRD_2014_BLESS_V1` |
| `cure_wounds` | 完整 `adventure.spell` 引用，算法必须为 `SRD_2014_CURE_WOUNDS_V1` |
| `material_item` | 完整 `equipment.item` 引用，类别必须为 `HOLY_SYMBOL` |
| `ability_key` | 恰为 `ability.wisdom` |

配置表达生命领域 1 级始终准备的两个域法术及生命门徒治疗贡献，不表达通用准备清单、
已知法术或法术书。实际角色必须通过现有初始/成长链获得被引用的职业、子职业、特性和资源；
加载配置本身不授予角色施法资格。运行仅接纳总等级 1、单职业且该职业等级 1，
拒绝缺失特性、额外未支持特性、多职业、更高等级、Pact Magic 和高环施法。

`slot_resource` 是既有角色资源事实中的 1 环法术位池。每次合法施法消耗一个，
两法术共享当前值，沿既有标准长休恢复。配置中的 1—20 范围是现有资源编码要求，
运行等级门始终限定 1 级；它不声明更高等级牧师的法术位表，也不创建第二份可写法术位。

## 法师 1—5 级的学习目录

`character.wizard_spellcasting` 是独立于生命领域的准确职业能力来源，固定使用 INT。
它必需 `owner_class`、`required_feature`、`slot_resource`、`rank2_slot_resource`、
`rank3_slot_resource` 和 `ability_key`；后三个法术位引用必须不同，均归属该职业，
`execution_mode=AUTOMATIC`、`recovery_profile=1-20:LONG_REST`。一级、二级、三级池的
`maximum_profile` 分别恰为 `1:2,2:3,3-20:4`、`3:2,4-20:3` 和 `5:2,6-20:3`。
未列出的低等级没有对应资源；运行采用门限单职业法师 1—5 级，不从后三段推断更高等级能力。

`required_feature` 必须归属该准确职业，`level=1`、`feature_kind=FEATURE`、
`execution_mode=AUTOMATIC`、`execution_algorithm=SRD_2014_WIZARD_SPELLCASTING_V1`。
每个职业至多一个启用法师配置。与生命领域配置并存不授予跨职业法术，且不取消其单职业边界。

配置拥有 `spells` 集合。每个成员为独立完整 entry 身份，必需 `spell` 完整规则引用，
可选 `author_note`；启用目录至少包含六个不同、启用的一环法师法术。它只声明可学习名单，
不表示角色已经学习或准备。角色必须沿真实初始化、逐级成长、法术书和长休准备事实取得资格；
初始六个、每级新增两个及准备上限由角色消费者验证。原始声明和最终名单均校验准确类别，
生命领域的 Bless／Cure Wounds 不能进入法师名单。

Shield、Counterspell 分别为一环和三环，出处与机制算法不可自由覆盖。目录另允许以下
只读元数据算法：`SRD_2014_<NAME>_METADATA_V1`，其中 `<NAME>` 限于
`ALARM`、`BURNING_HANDS`、`CHARM_PERSON`、`COLOR_SPRAY`、`COMPREHEND_LANGUAGES`、
`DETECT_MAGIC`、`DISGUISE_SELF`、`EXPEDITIOUS_RETREAT`、`FALSE_LIFE`、`FEATHER_FALL`、
`FIND_FAMILIAR`、`FOG_CLOUD`、`GREASE`、`MAGE_ARMOR`、`MAGIC_MISSILE`、`SLEEP`、
`THUNDERWAVE`。这些都是一环法师法术，其准确原文页由封闭算法映射保存。元数据允许真实
学习和准备，不提供施放消费者，尝试执行仍以稳定业务错误拒绝；不能从名称、自由参数或
已准备状态推断伤害、豁免、仪式、魔法飞弹或其他未实现机制。

法师的 Arcane Recovery 及 Evocation 的 Savant／Sculpt Spells 不被省略或伪装成已执行：
三项分别使用封闭的 `SRD_2014_WIZARD_ARCANE_RECOVERY_V1`、
`SRD_2014_EVOCATION_SAVANT_V1`、`SRD_2014_SCULPT_SPELLS_V1`，当前执行模式为
`BLOCKED`。前者必须归属法师职业且一级取得，后两者归属二级选择的真实子职业且二级取得。
运行对 Shield／Counterspell 的无影响判定必须核对实际目录、owner 和角色获得事实；
不能按任意 BLOCKED、文本键或相似名称放行，也不开放短休恢复、抄书折扣或塑能塑形。

规则依据本地 [Wizard 表与法术书 p.52](../references/srd-5.1/pages/page-052.md)、
[准备与成长 p.53](../references/srd-5.1/pages/page-053.md)、
[Wizard 名单 p.111](../references/srd-5.1/pages/page-111.md)及
[续页 p.112](../references/srd-5.1/pages/page-112.md)。

## 固定抗魔环境

`adventure.environment` 只允许 `DM_ANTIMAGIC_ZONE_V1`，必需基本准确身份、名称、说明、
`source_page=0` 和算法，不接受自由半径、脚本或法术参数。算法固定十尺区域，由真实 DM 放置
事实创建，区域位置、原因身份、创建操作及解除由环境 producer 保存。它明确是所选家规场景，
不表示八环 Antimagic Field 已施放，不提供其完整 SRD 规则。每个独立区域保持各自的压制原因，
不替代同法术落选实例的 SHADOWED，也不重置法术原期限或返还成本。

## 真实圣徽物件

`equipment.item.kind=HOLY_SYMBOL` 是既有库存/手位事实中的真实物件定义，使用
`SRD_2014_EQUIPMENT_V1`，出处仍为装备范围 62—73。它要求 `armor_class=0`、
`strength_minimum=0`、`stealth_disadvantage=false`、`hands=1`，省略 `proficiency`
与 `stealth_skill`。其他装备类别继续要求其准确熟练引用，不能借该可选字段放宽。
`proficiency` 仍不允许用 `clear_field` 清空。

该切片支持持在 `MAIN_HAND` 或 `OFF_HAND` 的圣徽作为 Bless 材料替代；物件实例必须属于
施法者，准确引用配置中的圣徽定义。仅携带、落地、穿戴、错误定义或伪造实例不满足。
Cure Wounds 的 S 组件要求真实空手；未受训护甲仍禁止施法。物件与手位的状态校验复用
现有装备消费者，客户端不能用自由布尔值声称材料、空手、准备或剩余法术位已经满足。

## 覆盖、来源与镜像

新类型允许 `set_field` 一次替换目标拥有的完整原子字段，引用一次替换完整身份；
`set_enabled` 保存启停，只有 `author_note` 可清空。必要引用、属性、算法、出处与必需文本
均不可清空。同值覆盖也保留 provider、加载序号、原包 SHA、位置、操作和 before/after。
生产规范格式与五类摘要域不变。

来源 V010 追加 `rule_schema2_wizard_spellcasting`、`rule_schema2_spellbook_member`、
`rule_schema2_environment` 和 `rule_schema2_wizard_change`；镜像 V037 对应保存准确配置、
学习名单和环境规则，并以前向 ALTER 扩展法术、特性与字段链的封闭约束。完整引用使用
同快照 RESTRICT 外键；来源与运行镜像均独立逐列恢复，不借最新来源补齐。

来源 V009 增加 `rule_schema2_spell`、`rule_schema2_spellcasting` 和
`rule_schema2_spell_change`。原始作者序号、文本、整数、布尔与七组完整引用分别存列；
共享 reader 对全部包逐列比较原始 ZIP，包含未选择包，缺失、多余、跨包及任意列差异均拒绝。
材料只追加，installer 仅获准确列 INSERT，其他来源角色只读。来源仓储不提交或回滚。

运行 V035 增加 `s2_runtime_spell` 与 `s2_runtime_spellcasting`，以前向 ALTER 扩展
圣徽的装备约束及字段链引用白名单。施法的职业、子职业、特性、资源、两个法术与圣徽
通过同快照 RESTRICT 外键和语义检查绑定。独立读回从保存的类型化行及完整字段链恢复
生产投影，不查询最新来源补齐机制字段。V009/V035 不改写既有迁移、RELEASED v1 或
已发布 digest 合同，也不保存运行施法结果或持续效果实例。

法师与环境的针对性入口为 `Schema2WizardCompositionTest`、`JdbcSchema2WizardSourceTest`、
`JdbcSchema2WizardSnapshotTest`；
验证入口还包括 `Schema2SpellCompositionTest`、`JdbcSchema2SpellSourceTest`、
`JdbcSchema2SpellSnapshotTest` 与 `Schema2SpellSchemaTest`，运行消费者另验证真实初始化、
共享资源消耗、组件、目标、持续效果及恢复。代理和静态 SQL 测试不证明 MySQL 的触发器、
权限和外键已经执行；迁移、授权、实际数据库验收与部署仍是独立检查点。
