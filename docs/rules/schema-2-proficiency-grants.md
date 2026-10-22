# Schema 2 初始语言与工具授予

本合同定义 schema 2 内部组合材料中的初始授予来源、稳定来源槽和原子授予值。
它扩展有限组合投影及其生产编码，不授予完整种族、背景、职业、base、安装接受或游戏执行资格。
现有 schema 1、旧 DRAFT 角色规则及 RELEASED 规则包继续使用各自合同。
生产摘要的域和 canonical profile 沿用[制品合同](schema-2-rule-artifacts.md)，
来源读取、转存和恢复沿用[快照合同](schema-2-runtime-snapshot.md)。

## 本地 SRD 依据与采用范围

[语言条款（第 59 页）](../references/srd-5.1/pages/page-059.md)区分种族默认语言和背景追加语言；
[工具条款（第 70 页）](../references/srd-5.1/pages/page-070.md)说明工具熟练可由种族、职业、背景或专长提供。
因此本模型把“提供授予的来源”和“被授予的语言／工具目录目标”分别建模，并用来源槽连接两者。
当前仅采用前述四类初始来源元数据，未因这些引用开放专长或完整角色领域。

[背景授予条款（第 60 页）](../references/srd-5.1/pages/page-060.md)允许角色在不同来源重复取得同一种
技能或工具熟练时改选同类熟练。T1 的有限初始计划据此避免用已取得的完整目标身份填充 CHOICE：
固定授予先计入已得目标，后续选择逐槽去重。固定来源事实仍逐条保留，最终能力按完整目标身份聚合。
这一项目采用规则也用于语言选择；第 60 页的重复授予条款本身只明示技能和工具。
本合同不实现技能、任意替代目标或完整背景自定义；扩展行为须另有合同。

## 来源定义

规则身份仍为 `(module_key, rule_type, rule_key)`。新增类型严格限于
`character.race`、`character.subrace`、`character.background` 和 `character.class`。
它们提供初始授予所需的真实、可引用来源，不是任意来源字符串，也不是完整角色领域定义。

| 字段 | 合同 |
| --- | --- |
| `identity` | 完整原始所有者身份；新增声明由本包拥有 |
| `display_name` | NFC 文本，1—120 个 Unicode code point |
| `description` | NFC 文本，1—1000 个 Unicode code point |
| `source_page` | 整数 3—74 |
| `parent_race` | 仅 subrace 必填；完整 `character.race` 身份；其他来源禁止此字段 |
| `author_note` | 可省略的 NFC 文本，1—1000 个 Unicode code point |

文本拒绝 C0/C1 控制符和不成对 surrogate；空白、规范化和业务错误沿用现有严格作者读取器。
未知字段失败关闭。来源初始化为 `enabled=true`；已有来源可通过后续包覆盖上述原子领域字段，
其中只有 `author_note` 可清除。`parent_race` 可整体重定向至另一真实 race；身份本身不可覆盖。
禁止选择已禁用来源；选择 subrace 时，选中 race 必须与其最终 `parent_race` 完整身份一致。

## 来源槽和完整引用

来源规则下的集合为 `proficiency_grants`。每个槽使用完整条目身份：

```json
{"kind":"entry","rule":{"kind":"rule","module_key":"example_base","rule_type":"character.race","rule_key":"race.example"},"collection_key":"proficiency_grants","module_key":"example_extension","entry_key":"extra_language"}
```

`rule` 是真实来源身份；条目的 `module_key` 独立标识槽的原始所有者。
扩展包可以向已有来源添加由扩展包拥有的新槽。`entry_key` 是 1—64 位小写 ASCII 标识符，
遵守 `[a-z][a-z0-9_]{0,63}`。它不是旧 DRAFT 的 dotted grant key；不得通过拼接或缩短身份桥接两种合同。
同一来源允许多个槽，不同来源允许授予相同目标；每条来源关系均保留，不能因目标相同而删除事实。

条目只有 `identity`、必填 `grant` 和可省略 `author_note`，默认启用。
`grant` 是完整、封闭的 tagged union，类别严格限 `LANGUAGE` 或 `TOOL`：

```json
{"category":"LANGUAGE","kind":"FIXED","target":{"kind":"rule","module_key":"example_base","rule_type":"character.language","rule_key":"language.common"}}
```

```json
{"category":"TOOL","kind":"CHOICE","candidates":{"kind":"rule","module_key":"example_base","rule_type":"catalog.tool_set","rule_key":"tool_set.initial"},"count":1}
```

`FIXED` 只允许 `category`、`kind`、`target`；类别决定目标类型为
`character.language` 或 `character.tool`。
`CHOICE` 只允许 `category`、`kind`、`candidates`、`count`；类别决定引用为
`catalog.language_set` 或 `catalog.tool_set`，数量是 1—16 的 JSON 整数。
不接受混合分支、浮点数量、缺失参数或额外参数。

任何外部来源、槽所有者、父 race、固定目标或候选集都要求作者包声明直接依赖；
仅有间接依赖不足。引用必须指向本包或已加载包中的真实声明。
本包所有声明先可见，然后校验引用；不能通过将坏声明禁用、随后覆盖或隐藏槽来绕过校验。

## 原子覆盖和最终消费

`set_field` 对槽只接受领域字段 `grant`、`author_note`。
`grant` 的 Java 值为 `GrantValue(GrantSpec)`；`FixedGrant` 和 `ChoiceGrant` 分别承载完整分支。
允许整体进行 FIXED↔CHOICE、类别、目标、候选集和数量替换，替换值必须完整通过当前类型、引用和最终候选校验。
不接受 `grant.count` 等路径、属性袋、逐参数修改、`clear_field(grant)` 或身份变更。
`set_enabled` 独立切换整个来源或槽，已有一包一目标一字段操作限制保持不变。

组合结束时仍校验所有来源父关系、槽所有者及授予引用，包括已禁用对象。
已有语言和工具集合的 `minimum_enabled_members` 校验保持原合同。
对最终启用来源中的启用 CHOICE 槽，候选集本身必须启用，且启用成员所引用的启用目标按完整目标身份去重后不少于 `count`；
不满足时返回 `S2_CANDIDATES_INSUFFICIENT`。同名或同 `rule_key` 的不同模组目标仍是不同目标。
禁用来源或槽免除该槽的 CHOICE 消费义务，但不会放宽类型和引用有效性。

初始分配计划只消费已选来源的槽：

- FIXED 始终保留来源槽和目标事实；槽或目标禁用时标记 suppressed。
- 禁用 CHOICE 槽不要求输入，也不制造某个目标的授予事实；其禁用槽状态可供计划审查。
- 启用 CHOICE 槽的选择必须属于当前有效候选集，数量精确、完整身份去重，且不能重复已由 FIXED 或先前选择获得的目标。
- 同一目标由多个 FIXED 来源提供时，保留所有来源事实。最终语言/工具能力可按完整目标身份聚合。

本合同只覆盖初始语言与工具，不开放 skill、save、weapon、armor、升级选择或其他未实现机制。

## 来源链、摘要和恢复

每个新来源字段和槽字段沿用完整字段链：声明、默认启用、可选省略、显式设置、显式清除、
操作前后状态、提供包、加载序号、原始 ZIP 摘要、文件和 JSON Pointer 均保留。
授予链中的每个状态都是完整原子 `grant`，不会拆为若干不完整参数链。
每个新来源/槽的声明字段必须来自同一文件的同一数组元素；显式操作位置必须是操作数组元素。
这些位置不得指向 manifest；数组下标须处于对应每包定义、条目或修改预算内，
`parent_race` 和 `grant` 的引用子字段不能替代完整原子字段位置。

原始 ZIP、模组逻辑、组合配置、最终规则和完整链这五类生产摘要覆盖全部新增身份和字段；
不更换已有域，不在旧向量中追加空集合或改变已有编码。
从运行行恢复生产投影时，重新严格解析新来源与授予值，验证父身份、全部历史引用的声明时序、
链连续性、最终行一致性、提供包及来源位置，并重新计算生产编码。
仅比较收据字符串不足以代替这些检查。

数量和资源预算保持有限：最多 4 包，每包 64 定义、128 条目、256 修改，
现有文件、ZIP、JSON 和 8 MiB 运行 canonical 预算不变；每个选择槽最多 16 项，不存在开放参数表。

## 独立验证材料

`src/test/resources/schema2-grants/` 是独立 base、extension、compatibility 材料，
同时固定原始 ZIP、逻辑字段、最终字段、完整来源链及摘要期望。
它使用自定义初始来源元数据，不宣称提供完整 SRD race 或 class；目录中的语言页码为 59、工具为 70。
正例可选定 Human、North human、Sailor、Scholar，并选择 Elvish 和 Smith tools，
覆盖同名跨模组语言、多个来源槽、FIXED↔CHOICE、父身份覆盖恢复、候选去重、禁用来源、禁用槽和禁用目标。
期望数据由目录内 `oracle.py` 的独立 Python 标准库实现固定；其默认模式重新生成并逐字节比较
全部 10 份 ZIP hex 与期望 JSON，不写文件。测试不能使用被测组合器生成期望值。
