# Schema 2 战斗、武器与步行作者来源

本合同约束战斗、近战与远程武器攻击、步行的严格作者输入、组合与战役冻结。沿用[制品合同](schema-2-rule-artifacts.md)及[检定、装备与健康作者合同](schema-2-foundation-authoring.md)。

## 共同字段与身份

每个定义必需完整 `identity={kind:rule,module_key,rule_type,rule_key}`、`display_name`、
`description`、`source_page`、`algorithm`，可选 `author_note`。文本、ASCII 身份、NFC、Unicode
码点长度、C0/C1 控制符、未知字段和精确 JSON 整数词法继承作者基础合同。引用必须包含完整身份，
只能指向本包或先加载包；外部原始所有者必须是直接依赖。不从名称、说明或规则键猜测数值与能力。

| 类型 | 键前缀 | 支持的算法 | 原文页 |
| --- | --- | --- | --- |
| `adventure.combat` | `combat.*` | `SRD_2014_COMBAT_V1` | 90—95 |
| `equipment.weapon_attack` | `weapon_attack.*` | `SRD_2014_MELEE_WEAPON_ATTACK_V1` | 62—73 |
| `equipment.weapon_attack` | `weapon_attack.*` | `SRD_2014_RANGED_WEAPON_ATTACK_V1` | 62—73 |
| `character.movement` | `movement.*` | `SRD_2014_WALKING_SPEED_V1` | 3—74 |
| `equipment.armor_movement` | `armor_movement.heavy_armor`（准确键） | `SRD_2014_HEAVY_ARMOR_MOVEMENT_V1` | 63 |

`adventure.combat` 没有自由动作参数。最终组合至多有一个启用定义；执行资格另要求准确启用规则。
算法负责当前采用范围的一般动作、单击 Attack、回合和机会攻击；内容包不能登记 handler、阶段、
反应种类、脚本或客户端可写动作池。具体采用范围和运行责任由[战斗合同](schema-2-combat.md)维护。

原文依据包括[先攻与回合 p.90](../references/srd-5.1/pages/page-090.md)、
[反应与移动 p.91](../references/srd-5.1/pages/page-091.md)、
[攻击结构 p.94](../references/srd-5.1/pages/page-094.md)和
[近战与机会攻击 p.95](../references/srd-5.1/pages/page-095.md)。出处页不代替实现覆盖证据。

## 擒抱与推撞

本人 Attack 的一击可以用于擒抱、推倒或推开。当前战斗算法固定单格中型角色的身体触及、体型差上限、推开距离和拖带减速。选骰及最小获胜分差来自冻结的检定定义，速度来自冻结的步行与护甲材料。字段范围和数值见[近战控制](schema-2-melee-control.md#数值与支持范围)。

## 近战武器定义

现有 `equipment.item` 保留库存、手位、护甲和熟练合同，其原格式不含伤害、触及或攻击能力。
`equipment.weapon_attack` 是对真实物品的独立类型化规则，不是物品实例或已经发生的攻击。

| 字段 | 封闭约束 |
| --- | --- |
| `item` | 完整 `equipment.item` 引用；目标必须为 `kind=WEAPON` |
| `ability_policy` | `STRENGTH` 或 `FINESSE`；后者由服务器取 STR/DEX 修正中较高者 |
| `dice_count` | 整数 1—4 |
| `dice_sides` | 4、6、8、10 或 12 |
| `damage_type` | `BLUDGEONING`、`PIERCING` 或 `SLASHING`，必需且不能清空 |
| `reach_feet` | 5 或 10 |

一个物品至多一个启用的近战定义。禁用定义仍须引用存在且类别正确的真实物品；启用定义还要求
目标物品启用。原始声明和最终组合分别核验，后续覆盖不能掩盖原始类别或引用错误。
武器熟练来自该物品准确 `proficiency` 和角色实际授予；命中与伤害的属性修正、熟练加值、AC、
骰点和结果均由服务器派生，不是作者自由数值。装备实例必须真实存在并处于合法手位。

单职业战士 5–6 级的攻击额度由独立的
[Extra Attack 特性声明](schema-2-extra-attack.md)及真实授予提供，不是武器 profile 的数值。
勇士 3–6 级的 [Improved Critical](schema-2-improved-critical.md)同样来自实际特性授予，
不在武器定义上保存第二份重击阈值；普通攻击、OA 与 Ready 共用该被动贡献。
投掷、双持、versatile、重型武器特殊影响、其他额外攻击来源、
重掷和魔法附加伤害须有自己的完整字段与消费者后才能开放，不能通过说明文字或环境健康效果冒充。

## 远程武器定义

`SRD_2014_RANGED_WEAPON_ATTACK_V1` 使用真实的双手 `RANGED_WEAPON` 物品、`DEXTERITY`、伤害骰及类型。`ammunition` 必须准确引用本组合的 `AMMUNITION` 物品。`normal_range_feet` 与 `long_range_feet` 取 5 至 600 之间的五尺倍数，长射程必须大于正常射程。远程格式使用这两个数字，近战格式使用 `reach_feet`。源定义和覆盖后的组合都按各自格式验证。

标准 `weapon_attack.shortbow` 使用 80/320 尺、1d6 穿刺及 `item.arrow`。作者可用 `set_field` 单独修改正常射程。可运行对照和玩家行动证据见[独立作者场景](../../rule-packages/srd51-runtime/scenarios/README.md#ranged-weapons)。怪物通过自己的[动作条目](schema-2-monster-attacks.md#模组作者字段)给出射程、命中和完整伤害分量。

## 步行来源

步行算法为每个种族或亚种声明基础速度和重甲策略。通用重甲规则为整个组合声明一次减速量，各件重甲声明自己的力量门槛。

| 冻结字段 | 有效值 | 标准包 |
| --- | --- | --- |
| `character.movement.walk_speed_feet` | 5–120 尺，5 的倍数 | 人类 30，矮人 25 |
| `character.movement.heavy_armor_speed_policy` | `APPLY` 或 `IGNORE` | 人类 `APPLY`，矮人 `IGNORE` |
| `equipment.armor_movement.heavy_armor_speed_penalty_feet` | 0–120 尺，5 的倍数 | 10 |
| `equipment.item.strength_minimum` | 重甲取整数 0–30，0 表示无门槛；其余装备取 0 | 链甲 13，板甲 15 |

`character.movement.owner` 必须引用完整的 `character.race` 或 `character.subrace` 身份。
每个来源至多有一项启用的步行定义，启用定义要求来源也已启用。按角色的实际种族和亚种须能匹配唯一一项。
组合至多启用一项 `equipment.armor_movement`。可建团组合必须恰有一项，完整文件恢复时也会复核。步速、通用减速量和策略均可用
`set_field` 独立覆盖；力量门槛在对应装备上覆盖。所有数值必须使用精确 JSON 整数。

当角色穿着重甲、实际力量严格低于该甲门槛且其策略为 `APPLY` 时，有效步速为
`max(0, 基础步速 − 通用减速量)`。其余情形使用基础步速。护甲熟练、隐匿劣势和施法各按自身规则结算。
战斗步行、疾走、Ready 移动、爬行、起身、探索移动与玩家本人角色卡共用有效步速。
例如只把矮人基础步速改为 30 尺，穿板甲的低力量矮人即可步行 30 尺，疾走增加 30 尺，起身消耗 15 尺。
同等条件的人类仍为 20 尺。角色可否创建取决于战役已通过校验的种族模板。
规则依据见[矮人特性](../references/srd-5.1/pages/page-003.md)和[重甲力量要求](../references/srd-5.1/pages/page-063.md)。

## 覆盖、冻结与验证

受支持的覆盖继续按制品合同保留完整原子、完整引用、原包和位置。战役工作库冻结最终规则及
完整链，战斗命令、准备与焦点、装备和角色健康事实由各自业务聚合拥有。

`Schema2CombatCompositionTest` 和 `Schema2DwarfMovementCompositionTest` 核对作者、覆盖与冻结材料。
成品组合的 [`dwarf-heavy-armor-speed` 场景](../../rule-packages/srd51-runtime/scenarios/README.md)
使用相同板甲和力量值验证标准策略。作者变体分别验证单改步速，以及交换策略、调整通用减速和板甲门槛。战斗命令见[战斗合同](schema-2-combat.md)。

[旧版来源与镜像](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/schema-2-combat-authoring.md)
