# Schema 2 角色成长、HP、生命骰与职业资源


本合同定义 schema 2 成长、生命骰、HP 和职业资源的作者模型和规则消费。
`CHARACTER_ADVANCEMENT` 声明完整成长计算，`STATISTICS_AND_RESOURCES_ONLY` 声明有限统计能力。
当前 SQLite 工作台接入的角色模板和机制见[领域接合表](../design/v2/domain-integration.md)；
实际休息和恢复见[休息合同](schema-2-character-rest.md)。

## 山丘矮人强韧

标准包在 `character.subrace / subrace.hill_dwarf` 上声明强韧。选择矮人时可选此亚种。
建卡保存种族和亚种的完整规则身份。普通矮人只取得其种族来源，山丘矮人另外取得亚种来源。
强韧增加生命值上限，山丘矮人的感知提升和矮人的体质提升各有独立来源，当前模板继续使用标准数组。[SRD 第 4 页](../references/srd-5.1/pages/page-004.md)

| `character.subrace_hit_points` 的必需字段 | 作者合同 |
| --- | --- |
| `identity`、`display_name`、`description`、`source_page`、`algorithm` | 键前缀 `subrace_hit_points.`，第 4 页，算法 `SRD_2014_SUBRACE_HIT_POINTS_V1`。可选 `author_note` |
| `owner` | 指向完整 `character.subrace` 身份。启用贡献要求启用的亚种，同一亚种最多一项启用贡献 |
| `first_level_bonus` | 一级生命值加值，精确整数 0–5，标准值 1 |
| `per_additional_level_bonus` | 每获得一个后续角色总等级的加值，精确整数 0–5，标准值 1 |

两个数值可独立 `set_field` 覆盖，通过同一条字段来源链冻结。选择的亚种没有启用贡献时，加值为 0。
角色总等级为 L 时，累计贡献是 `first_level_bonus + (L - 1) × per_additional_level_bonus`。
一级先算职业初始 HP，再加一级贡献。此后先按固定值或实际生命骰及体质算本级增量并取最低 1，
另计适用的体质追溯调整，再加一份后续等级贡献。进入另一个职业也只取后续等级加值。
升级时当前和最大 HP 增加同样的数值，保留原先损失的 HP。实际掷骰和生命骰数量由职业规则决定。
完整文件按原初始计划、冻结规则和已记录的骰点重验。纯统计范围 `STATISTICS_AND_RESOURCES_ONLY` 对选中带启用 HP 贡献的亚种明确拒绝。

体质 14 的两名战士采用固定值成长时，普通矮人的最大 HP 从 12 升至 20，标准山丘矮人从 13 升至 22。
家规将两个字段分别设为 2 和 0 时，山丘矮人从 14 升至 22，一级的 2 点在以后各级保留。

## 类型化作者模型

以下两个规则类型使用完整 `RuleIdentity(module_key, rule_type, rule_key)`，不能只用 `rule_key`
标识资源。它们不拥有 `proficiency_grants` 集合，也不接受自由属性包、任意算法或嵌套补丁。
资源键必须以 `resource.` 开头，成长键必须以 `progression.` 开头，后续各段非空且遵守稳定键
字符规则；不接受裸键、空段或末尾句点。
除 `author_note` 外，下列字段均必需；作者不能提交最终 `enabled`，它由组合链建立。

| 规则类型 | 字段 | 约束 |
| --- | --- | --- |
| 两类共用 | `identity`、`display_name`、`description`、`source_page`、`author_note` | 身份遵守 schema 2 规则身份合同；展示名 1—120 个 NFC 码点；描述／可选备注 1—1000 个 NFC 码点；页码整数 3—74；拒绝 C0/C1 控制字符和坏 Unicode |
| `character.class_progression` | `owner_class` | 指向真实 `character.class` 元数据来源的完整规则身份 |
| `character.class_progression` | `hit_die_sides` | 仅整数 6、8、10、12 |
| `character.class_progression` | `proficiency_bonus_profile` | `AdvancementValueProfile.parseProficiencyBonus` 验证完整 1—20 级；状态按总等级保存并校验熟练加值，本合同不开放具体检定消费者 |
| `character.class_progression` | `supported_maximum_level` | 整数 1—20；相应声明范围内的当前和目标职业等级均不得超过该边界 |
| `character.class_progression` | `advancement_scope` | `STATISTICS_AND_RESOURCES_ONLY` 或 `CHARACTER_ADVANCEMENT`；后者必须提供完整逐级条目、前提和对应选择事实 |
| `character.class_progression` | `multiclass_prerequisite` | 完整成长必需；有限统计可省略；使用下述有限类型化属性前提原子 |
| `character.resource` | `owner_class` | 指向真实 `character.class` 的完整规则身份 |
| `character.resource` | `maximum_profile` | 复用 `AdvancementValueProfile`；最多 1000 个 ASCII 字符，已获得资源的连续区间覆盖至 20，首段之前表示未取得 |
| `character.resource` | `recovery_profile` | 复用 `ResourceRecoveryProfile`；最多 200 个 ASCII 字符，区间连续完整覆盖 1—20，值仅 `SHORT_REST`／`LONG_REST` |
| `character.resource` | `execution_mode` | 仅 `AUTOMATIC`／`BLOCKED` |

容量、熟练加值和恢复 profile 的具体语法分别见[升级与生命骰](level-advancement-v2.md)及
[职业特性与资源恢复](class-features-v2.md)。共享解析器验证整个 profile，包括当前等级之后的
区间。已满、无限、未取得、禁用或阻断的资源都不能绕过畸形作者声明校验。

同一职业最多具有一个启用的成长声明。缺成长声明、成长声明被禁用、职业来源被禁用或职业
等级越过支持边界时，对应消费者明确拒绝。`supported_maximum_level` 约束相应声明范围内的
目标职业等级，不代替稳定逐级条目、到期子职业、ASI、专长及职业特性选择的完整闭包。

### 完整成长声明、逐级条目及熟练来源

`character.subclass` 必需声明 `owner_class` 和整数 `selection_level`（1—20）。
同一职业全部启用子职业候选必须具有相同选择等级；不支持在不同时点竞争同一首次选择。
`character.feature` 必需声明 class／subclass 完整 `owner`、职业等级 `level`（1—20）、
`feature_kind=FEATURE|OPTION`、`execution_mode` 和 `execution_algorithm`。
`character.feat` 必需声明 `prerequisite`、执行状态和算法。以上各类保留共用展示名、描述、
页码和备注字段；子职业、特性、专长键分别使用 `subclass.`、`feature.`、`feat.`。

特性执行状态为 `AUTOMATIC`、`CHOICE`、`DM_ADJUDICATION` 或 `BLOCKED`。基础算法白名单见
[职业特性合同](class-features-v2.md)，当前 `CHOICE` 专属一级战士防御。特性消费者按各自的攻击、法术或叙事规则使用已取得的事实。
schema 2 的法师 `SRD_2014_WIZARD_ARCANE_RECOVERY_V1` 另按[奥术回想合同](schema-2-arcane-recovery.md)
支持准确的 AUTOMATIC 声明及真实短休消费者；同算法 BLOCKED 仍不执行，两种声明不自动转换。
战士 `SRD_2014_FIGHTER_EXTRA_ATTACK_V1` 另按[额外攻击合同](schema-2-extra-attack.md)校验
真实职业 5 级授予。当前能力限单职业战士 5–6 级，成长预检和恢复共同核验该范围。
勇士 `SRD_2014_CHAMPION_IMPROVED_CRITICAL_V1` 按[精通重击合同](schema-2-improved-critical.md)
校验 3 级实际子职业授予，限单职业战士 3–6 级；不会仅凭等级产生能力。
战士 `SRD_2014_FIGHTER_SECOND_WIND_V1` 按[回气合同](schema-2-second-wind.md)校验一级
实际授予和准确一次/休息资源，当前限单职业战士 1–6 级；使用由 SQLite 战斗入口拥有。
[防御与动作如潮](schema-2-fighter-actions.md)分别使用 `SRD_2014_FIGHTER_DEFENSE_V1` 的一级显式选择，以及
`SRD_2014_FIGHTER_ACTION_SURGE_V1` 的二级自动授予。选择绑定初始化命令、摘要和完整角色历史；
动作如潮绑定准确职业资源，只有战斗入口可扣减。两者都要求单职业战士，最高支持 6 级。
专长当前仅允许 `BLOCKED`／`BLOCKED_DOWNSTREAM_SYSTEM_V1`：可验证前提并记录获得，不能
执行未接入的效果。到期的非 `OPTION` 特性按其算法取得，`CHOICE` 要求准确选择。
遇到到期非 OPTION 特性的 `BOUNDED_FEATURE_SELECTION_V1` 时，在 RNG 前拒绝整次成长。
当前有界选择包括子职业、ASI／专长、多职业熟练与来自冻结规则的一级战士防御。

完整成长的 `levels` 集合必须以稳定条目身份声明 1 至 `supported_maximum_level` 的每个启用
等级，每项恰有整数 `level` 和布尔 `asi_available`。拒绝重复等级、空洞、禁用等级或越界条目；
当前一级初始化不接受 ASI／专长义务，故一级 `asi_available=true` 声明直接拒绝。
逐级条目遵循新增／单字段修改／禁用协议，不整表替换成长表。

属性前提是一个完整原子：`{"all_of":[{"any_of":[{"ability":"ability.strength","minimum":13}]}]}`。
最多 6 个 AND 组，每组 1—2 个 OR 条件；属性键固定为六个 `ability.*`，阈值整数 1—20，
同一属性在整个前提至多出现一次。空 `all_of` 明确表示没有属性前提；空组、未知字段、重复
属性或越界阈值拒绝。该有界结构只表达已有成长／专长的属性门槛，不提供任意条件执行语言。

`character.proficiency` 声明 `category=SKILL|SAVING_THROW|ARMOR|WEAPON`，
SKILL 与 SAVING_THROW 必需六属性之一的准确 `ability_key`，其余类别禁止该字段；
工具直接使用已有 `character.tool` 身份。`catalog.proficiency_set` 声明
`category=SKILL|SAVING_THROW|ARMOR|WEAPON|TOOL` 和 `minimum_enabled_members`，其 `members` 条目以
`proficiency` 引用同类别的真实启用候选。集合按不同完整身份计数，跨模组同键不合并。

成长声明的 `starting_proficiencies`／`multiclass_proficiencies` 集合以稳定槽身份保存
`growth_grant` 原子：`{"kind":"FIXED","target":完整熟练身份}` 或
`{"kind":"CHOICE","candidates":完整集合身份,"count":1}`，数量为 1—16。
初始四类来源的语言／工具 `proficiency_grants` 合同保持独立。新授予原子和属性前提分别使用
类型化值及有序子行存储，不能把对象编码成文本字段。
`SAVING_THROW` 使用同一真实成长授予／来源事实；当前角色内容由初始职业 FIXED 槽授予，
不因多职业自动取得新职业的完整起始 save 熟练。实际普通豁免消费者由
[有限怪物合同](schema-2-monster-breath.md)接入，按准确属性只加一次 PB；
单角色基础检定入口仍不因作者类别新增而开放普通 save 命令。

## 组合、来源与运行投影

职业 owner 引用必须在声明包或前序包中真实存在；外部原始所有者必须是直接依赖。同一个
`rule_key` 可以存在于不同模组，状态、消费目标及历史始终保留完整身份。引用与作者 profile
在应用阶段及最终投影中均验证，稍后禁用或覆盖不能使早先的坏声明合法。

所有新增字段分别具有完整字段链。`owner_class` 使用类型化身份值，面数／等级使用整数值，
profile、scope 和 execution 使用已验证文本值；不把复合对象压成 JSON 文本。字段操作遵循
现有 `set_field`、`set_enabled` 和在本成长领域仅允许清空备注的 `clear_field` 合同，不允许
`maximum_profile.range` 等嵌套修改。显式同值修改仍保留来源步骤。


战役冻结成长与资源的最终字段及完整来源链。读取按准确规则原包及其绑定校验生产字节和摘要；
同版残留使用战役内冻结材料。组合阶段单独验证声明，业务执行再核对当前角色的资格和状态。

## 领域命令与状态

领域命令为封闭的 `INITIALIZED`、`CHARACTER_ADVANCED`、`STATISTICS_ADVANCED`、`RESOURCE_SPENT` 和
`RESOURCES_RECOVERED`。各命令只接受其对应字段，不能夹带客户端骰点、资源上限、HP 增量、
职业特性结果或任意规则字段。工作台仅接纳当前保护范围内的角色创建及成长命令。
[角色事实存储](schema-2-character-fact-storage.md)另支持检定、装备和健康的封闭记录格式；
成长/休息规则在复制状态时保留装备与健康事实，不生成其他领域的效果；
同一个 RuntimeService 通过[角色基础消费者](schema-2-character-foundations.md)执行其检定、装备和健康命令。

`INITIALIZED` 必须绑定准确的运行快照、已保留的初始计划和计划中的职业来源。服务端重新
核对初始计划的来源、槽、选择和授予，而后只接受标准数组 `8,10,12,13,14,15` 在六个冻结
`ability.*` 键中的精确排列。没有种族能力提升、购点、掷属性或任意角色作者输入。
能力状态验证范围为 1—30，但这不扩大初始化入口。一级 HP 为
`max(1, hitDieSides + floor((CON - 10) / 2))`，可用／最大生命骰均为 1，版本为 0；
已取得自动资源以完整容量开始。

完整成长 scope 的初始化还要核对职业起始熟练、到期的一级子职业及冻结规则要求的一级战士防御选择，
并保存实际取得的特性。初始语言／工具事实仍从准确的初始计划重建。

`STATISTICS_ADVANCED` 只为同一个职业增加一个统计等级。它执行 HP／生命骰和已声明职业
资源的容量变化，明确不处理经验前提、到期职业特性、子职业、ASI、专长、多职业或施法。
调用方不得把这个结果报告为完成了普通 SRD 职业升级。HP 选择仅 `FIXED_AVERAGE` 或
`SERVER_ROLL`；固定值为 `max(1, hitDieSides / 2 + 1 + CON modifier)`，掷骰值为
`max(1, boundedServerRoll + CON modifier)`。HP current／maximum 同增，最大 HP 不超过
65535；可用／最大生命骰各增一。

`CHARACTER_ADVANCED` 沿现有 DRAFT 合同由 DM 发起，只允许总等级和目标职业等级各增加
一级，总等级不超过 20；没有新增 XP 或里程碑授权系统，也不声称自动判定经验条件。
目标职业使用完整身份。进入新职业时，必须同时满足全部当前职业和新职业的多职业属性前提；
职业等级决定子职业、特性、ASI、资源及生命骰，总等级决定熟练加值。

ASI 等级必须选择一个属性 +2、两个不同属性各 +1，或取得一个满足前提且未取得的专长；
非 ASI 等级拒绝这些输入。属性不得超过 20。体质修正变化同时作用于本级 HP，并按既有总等级
追溯补足；魅力变化重算所有已取得职业的相关资源上限，仍保留各资源已消费量。

到期子职业必须从准确启用候选中选择，并且不能重选；目标等级的非 OPTION 特性与职业等级、
选择、专长和熟练事实一起原子提交。进入第二职业时只取得其 `multiclass_proficiencies`，不能
再次取得完整起始熟练或起始装备。已知熟练不能再次作为选择收益；同一固定熟练的不同真实
来源分别保留，实际有效熟练按完整目标身份去重。

状态保存每个职业的完整身份和等级，以及按 d6／d8／d10／d12 分桶的可用／最大生命骰。
各桶最大值必须等于对应职业等级之和，总最大生命骰等于总等级，不能把混合生命骰折成一个
面数。初始职业、已选子职业、特性／专长来源及熟练来源槽均可直接恢复。

`Schema2CharacterProgressionRules.PreparedAdvance` 只能由完整预检生成，构造器不对调用方
开放。职业、等级边界、完整资源集合、HP 上限、历史容量及所有目录验证在随机性前完成；
确认只接受固定选择的空骰点，或 `1..hitDieSides` 的服务端结果。

有限资源扩容保留已经消费的数量：`newCurrent = max(0, newMaximum - spent)`。新取得资源
以满容量开始；无限资源规范存储为 `unlimited=true,current=0,maximum=0`，之后变为有限
资源时以新容量开始。`RESOURCE_SPENT` 只针对已经取得的完整资源身份，消费量为
1—1000000，有限资源不得透支；无限资源消费保持规范零值。

`RESOURCES_RECOVERED` 的 `SHORT_REST`／`LONG_REST` 只表示冻结资源 profile 的恢复触发。
短触发恢复短休资源；长触发恢复短休与长休资源。恢复只把符合条件的有限资源 current 设置为
maximum，不改变等级、HP、生命骰、能力、初始授予或职业特性。没有可恢复的差额时拒绝，
不能通过重复恢复重新得到升级收益。

完整成长 scope 禁止直接调用这个恢复命令，以免绕过真实休息条件或长休收益限制；恢复由
[真实休息消费者](schema-2-character-rest.md)在完成休息时处理。活动休息期间禁止成长及独立
职业资源消费，不能一边推进休息窗口一边完成不相容命令。
记录 BLOCKED 内容的获得事实不证明其恢复义务已经履行；完整休息不得跳过已启用且当前职业
等级已取得的 BLOCKED 资源，具体预检与拒绝边界见休息合同。

状态最多保留 32 个已取得自动资源；每个状态必须精确包含当时规则要求的资源集合。缺失、
重复、多余、禁用、阻断、未取得或容量／无限标记不符的资源均拒绝。HP 与生命骰范围、
HP 最大值的可达范围、六能力集合和完整运行身份也必须成立。单职业统计范围内，最大生命骰
等于该职业等级；完整成长按职业对应面数分桶，各桶最大值等于对应职业等级之和，合计等于总等级。

完整成长另外限制职业数不超过 20、生命骰桶不超过 4、已获特性不超过 64、专长不超过 20、
熟练来源事实不超过 64。全历史类型化子行预算为 32768，包含 before／after、命令中的有界
选择及其来源；确认必须在 RNG 前检查候选完整集合，不能只检查事件数量。

## 事务、幂等与结果查证


`CampaignDomainEngine` 以 `Schema2CharacterRequest` 复核绑定、初始计划和完整历史。
事务与收据由 SQLite 工作库负责，在首次 RNG 前检查当前版本和可达事实预算。

摘要使用独立域 `local-dnd/schema2-character-command/1` 后接零字节，按顺序编码
操作、run、snapshot、campaign、character、准备操作六个 UUID，完整 `Schema2RulesBinding`，
预期版本、命令 union 字段及按键排序的初始能力。字符串为 UTF-8 字节长前缀，整数按
`DataOutputStream` 大端编码。摘要不替代完整状态或历史；同一操作的不同请求拒绝，准确
重放返回原事件，不再次抽骰、消费或写事件。


提交、回滚和原请求查证按 [SQLite 事务合同](../design/v2/runtime-and-transactions.md)执行。

## 不可变历史与直接恢复

一个角色最多 128 个事件，含版本 0 的初始化；后续每个成功命令版本恰增一。历史保留
操作身份、请求摘要、封闭命令输入、服务端已保存骰点、完整 before／after 及所有资源身份。
版本连续、事件首尾、同角色身份、固定能力和相邻状态必须一致；重复 operation id 拒绝。

直接恢复保存的状态与历史，不执行命令、不消费随机性、不再次初始授予、升级或恢复资源。
恢复必须验证完整运行规则绑定、保留初始计划、标准数组起点及历史中每次状态变化的合法性；
任何缺失资源、伪造容量、断裂历史、重复操作或无效随机结果都拒绝。状态、历史、幂等结果和
乐观版本由同一个受保护业务事务提交或回滚。成功读回或恢复不授权安装、完整战役文件导入
或 archive format 2 激活。

[数据映射](../design/v2/storage-data-map-and-rollout.md)列出角色聚合、资源投影及完整文件的关系。

## SRD 依据与剩余能力

本地 [SRD 第 56 页](../references/srd-5.1/pages/page-056.md)规定升级得到额外生命骰、HP、
职业特性及适用的能力提升，并规定多职业属性前提；[第 57 页](../references/srd-5.1/pages/page-057.md)
区分总等级与职业等级、混合生命骰和多职业熟练／特性。完整成长 scope 消费这里的成长前提、
有界选择和状态变化；有限统计 scope 仍不能替代它。多职业施法和 Pact Magic 的权威法术状态、
资源转换、施法动作及依赖战斗／法术的特性激活仍保持明确阻断，不因记录“已获得”而解除。

本地 [SRD 第 87 页](../references/srd-5.1/pages/page-087.md)的短休包含至少一小时的活动
限制和逐次生命骰选择／掷骰；长休包含至少八小时、活动／中断条件、开始时 HP 前提、
24 小时收益限制、全部 HP 恢复和有界生命骰恢复。真实休息的时间、健康和效果闭包责任按
[休息合同](schema-2-character-rest.md)验证；本合同中的资源触发本身不证明这些责任已经履行。
