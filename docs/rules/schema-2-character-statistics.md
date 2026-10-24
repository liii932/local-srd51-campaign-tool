# Schema 2 角色成长与资源材料及状态存储

本合同定义可由 schema 2 作者包声明、组合并保存到来源账本的成长与资源材料。它覆盖完整身份、
稳定条目、类型化字段和原始作者事实，并描述运行镜像及角色状态/历史的结构持久化。
完整成长、资源和休息业务服务尚未接入；仓储不执行游戏规则，也没有新增事务 owner。
`CHARACTER_ADVANCEMENT` 是作者声明的能力范围，不能据此取得安装、运行或发布资格。
旧 `RELEASED` 规则、持久化摘要域、新战役默认规则和 archive format 2 发布门保持不变。

## 成长与资源声明

规则采用完整 `RuleIdentity(module_key, rule_type, rule_key)`，不能只用 `rule_key` 标识资源。
成长键以 `progression.` 开头，资源键以 `resource.` 开头，后续各段非空且符合稳定键字符规则。
两类共用 `identity`、`display_name`、`description`、`source_page` 和可选 `author_note`：展示名为
1—120 个 NFC 码点，描述和备注为 1—1000 个 NFC 码点，页码为整数 3—74；拒绝坏 Unicode 和 C0/C1。
作者不提交最终 `enabled`，启用状态由组合链建立；不接受自由属性包、任意算法或嵌套补丁。

| 类型 | 字段 | 作者约束 |
| --- | --- | --- |
| `character.class_progression` | `owner_class` | 真实 `character.class` 来源的完整身份 |
| 同上 | `hit_die_sides` | 仅整数 6、8、10、12 |
| 同上 | `proficiency_bonus_profile` | 完整覆盖 1—20 级，逐级等于 `2 + floor((level - 1) / 4)` |
| 同上 | `supported_maximum_level` | 整数 1—20 |
| 同上 | `advancement_scope` | `STATISTICS_AND_RESOURCES_ONLY` 或 `CHARACTER_ADVANCEMENT` |
| 同上 | `multiclass_prerequisite` | 完整成长声明必需；统计/资源声明可省略；使用下述完整前提原子 |
| `character.resource` | `owner_class` | 真实 `character.class` 来源的完整身份 |
| 同上 | `maximum_profile` | 共用 `AdvancementValueProfile`，最多 1000 个 ASCII 字符；已获得区间连续至 20，首段之前表示未取得 |
| 同上 | `recovery_profile` | 共用 `ResourceRecoveryProfile`，最多 200 个 ASCII 字符；连续完整覆盖 1—20，值为 `SHORT_REST`/`LONG_REST` |
| 同上 | `execution_mode` | `AUTOMATIC` 或 `BLOCKED`；保存该声明不执行恢复 |

profile 语法见[升级与生命骰](level-advancement-v2.md)及[职业特性与资源恢复](class-features-v2.md)。
严格读取与组合验证整个 profile，包括尚未到达的等级。禁用、阻断、无限或未取得都不能绕过作者校验。
同一职业最多有一个启用的成长声明；支持等级不替代逐级条目或其他领域的完整覆盖。

## 稳定等级、子职业、特性与专长

`CHARACTER_ADVANCEMENT` 的 `levels` 集合以稳定条目身份声明 1 至 `supported_maximum_level`
的每个启用等级。每项包含整数 `level`、布尔 `asi_available` 和可选备注；拒绝重复等级、空洞、
禁用等级、越界条目及一级 `asi_available=true`。条目遵循新增、单字段修改和禁用协议，不整表替换。

`character.subclass` 声明完整 `owner_class` 和 1—20 的 `selection_level`；同一职业启用候选的
选择等级必须一致。`character.feature` 声明 class/subclass 的完整 `owner`、1—20 的 `level`、
`feature_kind=FEATURE|OPTION`、`execution_mode` 和 `execution_algorithm`。
`character.feat` 声明完整 `prerequisite`、执行模式和算法。这三类共用展示、描述、来源页和备注字段，
键分别使用 `subclass.`、`feature.` 和 `feat.` 前缀。

| 特性模式 | 允许算法 |
| --- | --- |
| `AUTOMATIC` | `AUTOMATIC_RESOURCE_LIFECYCLE_V1` |
| `DM_ADJUDICATION` | `BOUNDED_SUBCLASS_SELECTION_V1`、`BOUNDED_FEATURE_SELECTION_V1`、`BOUNDED_DM_ADJUDICATION_V1` |
| `BLOCKED` | `BLOCKED_ISSUE_8_V1`、`BLOCKED_SPELL_SYSTEM_V1`、`BLOCKED_DOWNSTREAM_SYSTEM_V1` |

算法为不超过 128 个 ASCII 字符的稳定标识。专长声明仅接受 `BLOCKED` 与
`BLOCKED_DOWNSTREAM_SYSTEM_V1`。这些字段表达执行责任和拒绝边界，不激活效果；OPTION 目录的
存在也不证明已有选择消费者。实际角色成长、特性获得、选择、资源消费和休息仍须接入独立业务责任。

## 前提与有界熟练来源

属性前提是完整原子，例如：

```json
{"all_of":[{"any_of":[{"ability":"ability.strength","minimum":13}]}]}
```

最多六个 AND 组，每组一至两个 OR 条件；能力键固定为六个 `ability.*`，阈值为 1—20 的整数，
同一能力在整个前提中至多出现一次。空 `all_of` 明确表示没有属性前提；省略、空组、重复能力及
未知字段分别校验，不以文本表达式代替有限原子。

`character.proficiency` 声明 `category=SKILL|ARMOR|WEAPON`，仅 SKILL 必需冻结 `ability_key`；
工具使用已有 `character.tool` 身份。`catalog.proficiency_set` 声明
`category=SKILL|ARMOR|WEAPON|TOOL` 和 `minimum_enabled_members`，稳定 `members` 条目的
`proficiency` 引用同类别的真实启用候选。不同完整身份分别计数，跨模组同键不合并。

成长声明的 `starting_proficiencies`/`multiclass_proficiencies` 以稳定槽身份保存完整 `growth_grant`：
`FIXED` 引用完整熟练身份，`CHOICE` 引用完整候选集合并声明 1—16 的 `count`。
原四类来源的语言/工具 `proficiency_grants` 合同保持独立，不能混用两类原子。

## 组合与来源存储

外部引用必须来自直接依赖并在声明包或前序包中真实存在。应用阶段和最终组合均校验引用、前提和
profile；稍后覆盖或禁用不能使早先坏声明合法。所有字段保留完整覆盖链，显式同值修改也保留步骤。
使用 `set_field`、`set_enabled` 和仅清空备注的 `clear_field`；不支持 `maximum_profile.range`
之类嵌套修改。前提和 growth grant 使用独立类型化值，不编码成 JSON 文本字段。

[来源账本](schema-2-source-ledger.md)在准确原包下保存定义、条目、修改和前提有序子行，并与全部
原始 ZIP 逐列核对。作者序号、完整目标、前提 AND/OR 顺序及省略/空前提区别都属于准确事实。
[休息作者声明](schema-2-character-rest.md)沿用相同原包与字段链边界。

成功组合和来源读回只证明这组有限材料可被准确表达。安装资格门仍拒绝不完整基础包及非空领域
扩展；类型化运行快照和状态仓储不开放业务入口，完整初始化、保存/结束生命周期和发布门不变。

## 类型化运行投影

运行 V027 保存成长、资源、子职业、特性、专长、熟练目录/集合、稳定等级和来源槽，以及休息声明。
`JdbcSchema2GrowthSnapshotRows` 与 `JdbcSchema2RuntimeSnapshotRepository` 独立查询类型化行、
重建最终规则和完整覆盖链，比较生产 canonical 字节与既有摘要。规则前提与链步骤前后前提使用
有序子行，完整引用、空/省略、清空、同值覆盖和启停语义均保留；不存 JSON 规则袋。
已保留运行快照的读回不依赖作者源库，来源重建与绑定仍遵守[运行快照合同](schema-2-runtime-snapshot.md)。

## 角色、命令与历史持久化

`JdbcSchema2CharacterRuntimeRepository` 是调用方事务中的叶仓储，保存完整类型化事实：

| 表 | 结构责任 |
| --- | --- |
| `s2_character_runtime` | 角色身份、准确 run/snapshot、初始计划绑定及初始职业；同 run 仅一个角色根 |
| `s2_character_event` | 操作身份、版本、命令类型、请求摘要、完整命令输入和已保存结果 |
| `s2_character_event_state` | 完整 before/after 聚合：能力、等级、HP 及休息/时间事实 |
| `s2_character_event_resource` | 完整资源身份、当前/最大值和无限标记 |
| `s2_character_event_class` / `s2_character_event_hit_die` | 按完整身份的职业等级，以及分面数的可用/最大生命骰 |
| `s2_character_event_subclass` / `s2_character_event_feature` / `s2_character_event_feat` / `s2_character_event_proficiency` | 选择、获得和熟练的准确来源事实，不以文本或集合摘要代替 |
| `s2_character_command_ability` / `s2_character_command_proficiency` / `s2_character_command_hit_die` | 命令中的有界属性、熟练和生命骰输入 |

规则镜像、角色状态、前后历史和命令输入都保留完整身份及显式序号。仓储验证结构、绑定、排序、
数量、版本连续性及容量；父子行同事务保存，实际查询读回全部类型化事实。恢复仅保存既有结构，
不执行命令、不重掷、不授予、不治疗，也不恢复资源。它不能证明成长或休息的完整规则语义已经满足；
完整预验证、随机性顺序、业务幂等和提交查证必须由尚未接入的调用方服务承担。

集合界限为最多 20 个职业、4 个生命骰桶、64 个特性、20 个专长、64 条熟练来源和 32 个资源。
每角色最多 128 个事件和 32768 个历史子行；这些局部拒绝预算不表示完整战役或数据库物理容量。
状态、命令和 before/after 的存在性、序号与版本必须一致，缺失、多余、混合身份或断裂历史拒绝。

全部新状态事实仅追加；事件依附角色根，before/after 和命令子行依附事件，角色根引用准确初始
计划与运行快照。FK 使用 `RESTRICT`，应用只获 SELECT/INSERT。叶仓储不提交、回滚或清理，
所有者负责在[共同业务事务](business-write-transactions.md)保护下完整预验证和原子提交。
当前没有该领域的业务事务 owner、执行服务、Host 命令或完整战役恢复入口。
