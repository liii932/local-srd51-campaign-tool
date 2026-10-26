# Schema 2 战斗、近战武器与步行作者来源

本文限定 `adventure.combat`、`equipment.weapon_attack` 与 `character.movement` 的作者输入、
纯组合、准确来源和 INTERNAL 运行镜像。沿用[作者基础](schema-2-foundation-authoring.md)、
[生产制品](schema-2-rule-artifacts.md)、[来源账本](schema-2-source-ledger.md)及
[动作继续协议](../design/v2/action-continuation-protocol.md)。类型和算法的登记不授予完整基础包、
非空 add-on、完整战役、Host、archive format 2 或发布资格。

## 共同字段与身份

每个定义必需完整 `identity={kind:rule,module_key,rule_type,rule_key}`、`display_name`、
`description`、`source_page`、`algorithm`，可选 `author_note`。文本、ASCII 身份、NFC、Unicode
码点长度、C0/C1 控制符、未知字段和精确 JSON 整数词法继承作者基础合同。引用必须包含完整身份，
只能指向本包或先加载包；外部原始所有者必须是直接依赖。不从名称、说明或规则键猜测数值与能力。

| 类型 | 键前缀 | 唯一算法 | 原文页 |
| --- | --- | --- | --- |
| `adventure.combat` | `combat.*` | `SRD_2014_COMBAT_V1` | 90—95 |
| `equipment.weapon_attack` | `weapon_attack.*` | `SRD_2014_MELEE_WEAPON_ATTACK_V1` | 62—73 |
| `character.movement` | `movement.*` | `SRD_2014_WALKING_SPEED_V1` | 3—74 |

`adventure.combat` 没有自由动作参数。最终组合至多有一个启用定义；执行资格另要求准确启用规则。
算法负责当前采用范围的一般动作、单击 Attack、回合和机会攻击；内容包不能登记 handler、阶段、
反应种类、脚本或客户端可写动作池。具体采用范围和运行责任由[战斗合同](schema-2-combat.md)维护。

原文依据包括[先攻与回合 p.90](../references/srd-5.1/pages/page-090.md)、
[反应与移动 p.91](../references/srd-5.1/pages/page-091.md)、
[攻击结构 p.94](../references/srd-5.1/pages/page-094.md)和
[近战与机会攻击 p.95](../references/srd-5.1/pages/page-095.md)。出处页不代替实现覆盖证据。

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

本模型只登记普通近战攻击。远程、弹药、投掷、双持、versatile、重型武器特殊影响、额外攻击、
重掷和魔法附加伤害须有自己的完整字段与消费者后才能开放，不能通过说明文字或环境健康效果冒充。

## 步行来源

`character.movement` 另必需 `owner` 和 `walk_speed_feet`。`owner` 是准确的
`character.race` 或 `character.subrace`；速度为 5—120 feet 的整数且必须为 5 的倍数。
一个种族或亚种族原始 owner 至多一个启用的步行来源。禁用定义也保留真实 owner，启用定义要求
owner 启用。实际角色必须按其冻结种族和亚种族找到唯一适用来源；多个适用来源拒绝歧义，
不猜默认 30 feet，也不以最新来源补齐缺失事实。

速度是定义，已走距离、剩余 movement、位置和路径是业务事实。护甲力量不足的速度减值沿
真实装备派生；本定义不提供飞行、游泳、攀爬或自由移动模式，也不创建第二份 movement pool。

## 覆盖、来源与运行镜像

`set_field` 仅替换目标类型拥有的完整原子字段；完整引用一次替换，`set_enabled` 保存启停。
这三个类型只有 `author_note` 可 `clear_field`。所有算法、数值、引用、伤害类型和必需文本均不可清空。
同值覆盖也保留 provider、加载序号、原包 SHA、位置、before/after 和操作身份。

来源 V008 增加 `rule_schema2_combat`、`rule_schema2_weapon_attack`、`rule_schema2_movement` 和
`rule_schema2_combat_change`。作者序号、文本、整数和完整引用逐列保存；原 ZIP 中全部包，包括
未选择包，均由共享 reader 独立核对。缺失、多余、跨包和任何列不一致都拒绝。材料只追加，
installer 只获准确列 INSERT，其他来源角色只读；仓储不拥有提交和回滚权限。

运行 V032 增加三个对应 `s2_runtime_*` 类型化表，并前向扩展既有字段链目标白名单。
快照保留完整定义、原始和覆盖字段链、准确成员身份以及既有五类生产摘要；生产编码和摘要域不变。
独立读回使用保存的类型化行重建完整投影，不依赖最新来源。武器和步行引用通过同快照 RESTRICT
外键与语义检查共同绑定，不能保存只有规则头、缺失机制字段的快照。

验证入口为 `Schema2CombatCompositionTest`、`JdbcSchema2CombatSourceTest`、
`JdbcSchema2CombatSnapshotTest` 与 `Schema2CombatSchemaTest`；固定独立向量位于
`src/test/resources/schema2-combat/`。逐列篡改、漏行/多行、非法原始链和写入失败均应拒绝或整体回滚。
代理和结构测试不证明真实 MySQL 的触发器、权限和外键已执行；迁移、授权与部署各自需要独立检查点。
