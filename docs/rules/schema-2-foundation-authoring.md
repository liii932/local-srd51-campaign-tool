# Schema 2 检定、装备与健康作者来源

本合同约束检定、装备、健康和环境效果的严格作者输入、纯组合与生产投影。准确身份、原始 ZIP、直接依赖、完整字段链和生产摘要域见[制品合同](schema-2-rule-artifacts.md)。当前业务消费者见[角色基础合同](schema-2-character-foundations.md)。

## 身份、文本与出处

定义使用完整 `identity={module_key,rule_type,rule_key}`；引用也必须携带全部三段身份。
模组键为 1—64 个 ASCII 字符，遵守 `[a-z][a-z0-9_]*`；规则键最多 128 个 ASCII 字符，
须使用下表前缀。前缀后首段匹配 `[a-z][a-z0-9_]*`，后续段匹配 `[a-z0-9_]+`，
段间以点分隔；不能用裸前缀、空段或尾句点。

每个定义必需 `identity`、`display_name`、`description`、`source_page`、`algorithm`，
可选 `author_note`。显示名为 1—120 个 Unicode 码点，说明和备注为 1—1000 个码点；
作者读取规范化为 NFC，拒绝全空白、C0/C1 控制字符及坏 Unicode。存储读回要求文本已经规范，
不把损坏材料自动正规化。未知字段、显式 null、脚本和自由参数对象均拒绝。
初始定义不接受 `enabled`；启停仅用 `set_enabled`。

| 类型 | 键前缀 | 唯一允许的算法 | `source_page` |
| --- | --- | --- | --- |
| `adventure.check` | `check.*` | `SRD_2014_ABILITY_CHECK_V1` | 整数 77 |
| `equipment.item` | `item.*` | `SRD_2014_EQUIPMENT_V1` | 整数 62—73 |
| `adventure.health` | `health.*` | `SRD_2014_HEALTH_V1` | 整数 98 |
| `adventure.vital_effect` | `vital_effect.*` | `DIRECT_VITAL_EFFECT_V1` | 整数 96—99 |

检定与健康类型没有其他领域字段；一个最终组合分别至多包含一个启用的检定定义和一个启用的
健康定义。零个启用定义可作为有限材料组合，但不能据此获得相应业务执行资格。

出处对应本地 SRD 的[属性与优势 p.76](../references/srd-5.1/pages/page-076.md)、
[熟练与检定 p.77](../references/srd-5.1/pages/page-077.md)、
[技能 p.78](../references/srd-5.1/pages/page-078.md)、
[护甲 p.63](../references/srd-5.1/pages/page-063.md)、
[穿脱与盾牌 p.64](../references/srd-5.1/pages/page-064.md)、
[工具 p.70](../references/srd-5.1/pages/page-070.md)及
[伤害 p.96](../references/srd-5.1/pages/page-096.md)、
[治疗 p.97](../references/srd-5.1/pages/page-097.md)、
[零 HP 与临时 HP p.98](../references/srd-5.1/pages/page-098.md)、
[临时 HP 期限 p.99](../references/srd-5.1/pages/page-099.md)。
页码记录声明所依据的原文，不能代替机制支持或实际执行证据。

## 装备字段与完整引用

`equipment.item` 另外必需 `kind`、`armor_class`、`strength_minimum`、
`stealth_disadvantage`、`hands`；`proficiency` 按物品类别要求，`stealth_skill` 按隐匿劣势要求。
整数初步范围分别为 AC 0—20、力量要求 0—15、手数 0—2；完整声明还必须满足下表。
`stealth_disadvantage` 必须是 JSON 布尔值，不能用文本或整数替代。

| `kind` | AC | 力量要求 | 手数 | `proficiency` |
| --- | --- | --- | --- | --- |
| `LIGHT_ARMOR`、`MEDIUM_ARMOR` | 10—20 | 0 | 0 | `character.proficiency`，类别 `ARMOR` |
| `HEAVY_ARMOR` | 10—20 | 0、13 或 15 | 0 | `character.proficiency`，类别 `ARMOR` |
| `SHIELD` | 2 | 0 | 1 | `character.proficiency`，类别 `ARMOR` |
| `TOOL` | 0 | 0 | 1 或 2 | `character.tool` |
| `WEAPON` | 0 | 0 | 1 或 2 | `character.proficiency`，类别 `WEAPON` |
| `HOLY_SYMBOL` | 0 | 0 | 1 | 必须省略 |

仅护甲可声明隐匿劣势。`stealth_disadvantage=true` 时必须提供完整 `stealth_skill`，
其目标必须是 `category=SKILL` 且 `ability_key=ability.dexterity` 的真实熟练定义；
为 false 时必须省略该字段。盾、工具、武器和圣徽均为 false 且不携带 `stealth_skill`。
应用不根据显示名称或自由规则键猜测技能身份。
圣徽的真实持有与施法组件资格见[施法作者合同](schema-2-spell-authoring.md)；
其余类别继续要求准确熟练引用，`proficiency` 仍不能用 `clear_field` 清空。

引用只能指向本包或加载在前的真实定义；外部原始所有者必须是本包的直接依赖。
禁用装备仍须保留其类别要求的真实引用；启用装备的适用熟练与隐匿技能目标还必须启用。
原始声明和最终投影均校验这些条件，后续覆盖不能掩盖原始类别或引用错误。
工具熟练目录、装备定义与角色已经持有或穿戴某实例是不同事实；本合同只保存定义。

## 环境健康效果与有符号整数

`adventure.vital_effect` 另外必需 `kind`、`dice_count`、`dice_sides`、`modifier`，
仅 `damage_type` 是可选领域字段。

| 字段 | 封闭约束 |
| --- | --- |
| `kind` | `DAMAGE`、`HEALING` 或 `TEMP_HP` |
| `dice_count` | 整数 0—20 |
| `dice_sides` | 骰数为 0 时恰为 0；骰数为 1—20 时仅 4、6、8、10、12 |
| `modifier` | 有符号整数 -1000—1000，定义与 `set_field` 使用同一校验 |
| `damage_type` | `DAMAGE` 必需；`HEALING`、`TEMP_HP` 必须省略 |

伤害类型只允许 `ACID`、`BLUDGEONING`、`COLD`、`FIRE`、`FORCE`、`LIGHTNING`、
`NECROTIC`、`PIERCING`、`POISON`、`PSYCHIC`、`RADIANT`、`SLASHING`、`THUNDER`。
骰数和骰面均为 0 表达固定修正量；定义不携带客户端骰点或已经发生的伤害、治疗与临时 HP。
直接健康效果的声明不代表攻击、施法、豁免、物品消耗、持续效果或反应流程已经接入。

所有整数必须使用精确 JSON 十进制整数词法。负数保留符号；拒绝 `-0`、小数、指数、
字符串/布尔/null 强制转换、前导 `+`、前导零及越界值。负数不能先取绝对值再解析；
生产规范字节、字段链和来源整数列必须保留同一有符号数值。

## 覆盖、清空与来源链

`set_field` 只修改目标类型拥有的完整原子字段；不能写嵌套路径、未知字段或复合参数袋。
引用字段一次替换完整身份，`set_enabled` 单独保存布尔启停。同一包不能两次修改同一目标字段。
同值覆盖仍保留 provider、加载序号、原包 SHA、路径、JSON Pointer、before/after 和操作种类。

`clear_field` 仅允许目标实际拥有的 `author_note`、`stealth_skill`、`damage_type`。
可在同一包中把护甲的隐匿劣势设为 false 并清空 `stealth_skill`，或把伤害种类改为
`HEALING/TEMP_HP` 并清空 `damage_type`；最终完整组合仍必须满足条件字段约束。
`ability_key` 和 `multiclass_prerequisite` 即使在某些定义中可省略，也不因此允许清空；
必需字段、算法、其他引用、整数和 enabled 均不能用 `clear_field` 删除。
读取作者操作及还原字段链都执行同一清空白名单，拒绝伪造的非初始 ABSENT 步骤。
初始可选字段缺席和后续显式清空是不同的来源事实。

生产编码仍用 `schema2-production-json-1` / `schema2-production-projection-1` 和既有五类摘要。
新增字段及每步操作进入各自类型化投影；原始非法算法、数值、引用或清空不能被后包的合法最终值隐藏。
旧 RELEASED 与不含新类型的既有固定字节合同保持。

## 当前验证与运行

`Schema2CheckCompositionTest` 和 `Schema2EquipmentHealthCompositionTest` 使用固定作者与覆盖材料。
当前战役保留完整的生产投影与来源链；实际命令的闭合预检、原骰、状态和恢复用
`CampaignDomainsIntegrationTest`、`CampaignCheckEffectsTest` 和[存储运行合同](../storage-vitality-loop.md)验证。

[旧版来源与镜像](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/schema-2-foundation-authoring.md)
