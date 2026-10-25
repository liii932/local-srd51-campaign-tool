# Schema 2 角色事实存储

本合同约束检定、装备与健康的类型化运行事实格式、追加读回和直接恢复。
规则定义与完整字段链沿用[作者来源](schema-2-foundation-authoring.md)及
[运行快照](schema-2-runtime-snapshot.md)；角色根、成长和休息事实沿用
[成长与资源](schema-2-character-statistics.md)、[标准休息](schema-2-character-rest.md)及
[共同业务事务](business-write-transactions.md)。

当前已接通四类规则的准确运行镜像，以及下述输入、结果、状态和历史的仓储层。
`JdbcSchema2CharacterRuntimeRepository` 能校验、追加、读回和直接恢复这些封闭事实；
叶仓储接收已准备的事实，不抽骰、不推导完整规则效果。
`Schema2CharacterRuntimeService` 复用该仓储执行既有成长/资源/休息以及真实检定、装备和健康命令；
[角色基础合同](schema-2-character-foundations.md)限定 Check/Equipment/Health 规则消费者、共同前提和历史语义重算。
结构与算术校验不能替代这些业务校验，仓储可写或恢复本身不授予执行或发布资格。

## 规则镜像与事实的区别

运行 V028/V030 分别保存 `adventure.check`、`equipment.item`、`adventure.health`、
`adventure.vital_effect` 的完整最终字段、启停和覆盖链，准确读回重建生产字节与摘要。
运行 V029/V031 则保存角色检定、装备和健康的命令/结果及 before/after，
不把物品定义当成库存，不把健康算法定义当成角色当前 HP。
运行 V001—V031 与来源 V001—V007 共 38 份迁移源；来源字段与安装资格门保持。

根身份继续绑定准确 run、snapshot、campaign、character 和 preparation operation。
一个 run 最多一个角色根，初始事件版本为 0，后续事件恰好递增一次；所有操作 UUID 唯一，
相邻 before/after 必须相等，当前状态必须等于最后 after。初始化装备为空，健康为规范初始值。
现有命令的状态拷贝保留新增字段，不借成长或休息合成新装备、检定或健康命令。

## 类型化命令与结果

`Command` 是封闭 union。每类命令仅允许自身字段，不能夹带资源、成长、休息或其他类型输入。
说明字段 `adjudication` 必须已是 NFC、非全空白、1—500 个 Unicode 码点，拒绝 C0/C1
控制字符和孤立 surrogate。完整规则身份、UUIDv4、字段范围和可空值分别验证。

| 事实类型 | 保存内容与形状 |
| --- | --- |
| `CHECK_RESOLVED` / `CheckInput` | 完整 `adventure.check` 身份、`ABILITY/SKILL/TOOL`、六属性之一、适用的完整熟练身份、DC 0—30、原始优势/劣势布尔裁定及说明；ABILITY 不携带熟练，SKILL 仅 `character.proficiency`，TOOL 仅 `character.tool` |
| `CheckResult` | 第一个 d20、可空第二个 d20、选中骰、属性修正、熟练贡献、总值、成败，以及独立保存的 `effectiveAdvantage/effectiveDisadvantage`；不能覆盖原输入的裁定 |
| `ITEM_ACQUIRED` / `EquipmentInput` | UUIDv4 实例、完整 `equipment.item` 身份、`CARRIED` 及说明；实例 UUID 等于本次操作 UUID，历史不能重复取得同一实例身份 |
| `ITEM_EQUIPPED` | 实例、`WORN/MAIN_HAND/OFF_HAND/BOTH_HANDS` 之一及说明；输入不再次指定物品定义 |
| `ITEM_REMOVED`、`ITEM_UNEQUIPPED` | 实例及说明；物品定义与目标位置输入均为空 |
| `HEALTH_EFFECT_RESOLVED` / `HealthInput` | 完整 `adventure.health` 与 `adventure.vital_effect` 身份、可空 `KEEP/REPLACE` 选择及说明；该选择的适用语义需由实际消费者验证 |
| `DEATH_SAVE_RESOLVED` | 完整健康规则及说明；效果身份和临时 HP 选择均为空 |
| `HealthResult` | 有序效果骰列表、可空死亡 d20、可空稳定恢复 d4、非负 `rolledAmount/appliedAmount`；效果骰最多 20 个且每个 1—12，死亡骰 1—20，恢复骰 1—4 |

检定结果按保存的有效模式验证：优势/劣势皆有或皆无时只有一个 d20，仅有其中之一时两个骰
取高或低；每个 d20 为 1—20。总值等于选中骰加属性/熟练贡献，成败等于 `total >= DC`。
属性贡献核对 before 中的六属性，熟练按完整目标身份匹配且只加一次；这些算术/结构校验
不独立证明技能默认属性、实际工具持有或装备产生的有效模式；`Schema2CharacterCheckRules`
结合 `Schema2CharacterEquipmentRules` 从准确镜像及 before 状态重新验证这些事实。

检定事件不能夹带生命骰结果；只有检定事件保存 `checkResult`。
健康效果结果不得携带死亡 d20 或恢复 d4，死亡豁免结果不得携带效果骰；只有两类健康事件
保存 `healthResult`。原骰与派生值分别保留，仓储不通过重新执行命令生成缺失结果。

## 完整装备与健康状态

`State.equipment` 最多 32 个 `EquipmentItem(itemId,item,location)`，按 UUID 文本顺序严格排列，
每个实例身份唯一并保留完整物品规则身份。位置仅为
`CARRIED/WORN/MAIN_HAND/OFF_HAND/BOTH_HANDS`；至多一个 WORN，双手占位与任一单手占位冲突。
物品类别、熟练、AC、工具用途及穿脱时间由 `Schema2CharacterEquipmentRules` 结合准确规则镜像判断。

`State.health` 保存临时 HP、死亡豁免成功/失败次数、稳定、死亡及可空稳定恢复分钟。
当前 HP 继续只使用 `State.hitPoints`，不建立第二份权威值。初始健康为
`(0,0,0,false,false,null)`；临时 HP 为 0—65535，成功/失败次数各为 0—2。

- HP 大于 0 时不能死亡或稳定，计数为 0，恢复期限为空。
- 死亡时 HP 为 0、计数为 0、稳定为 false、恢复期限为空。
- 稳定时 HP 为 0、计数为 0、未死亡，恢复期限必须大于当前分钟且不超过 1,000,000,000。
- 其余零 HP 状态的恢复期限为空；这些形状不授权任何健康状态变化或自动时间收益。

仓储约束每类事件允许改变的字段。检定只能改变角色版本；装备命令保留 HP、健康、成长和
资源，仅允许装备及适用的时间字段变化；健康命令保留装备、时间、成长和资源。
状态结构及邻接检查不替代伤害、治疗、死亡、穿脱或到期恢复的规则语义验证。

## 表、追加与直接恢复

| 表 | 责任 |
| --- | --- |
| `s2_character_check` | 每条检定事件恰好一行完整输入、原骰、有效模式及结果；其他事件不得有该行 |
| `s2_character_event_equipment` | 每个 before/after 的规范装备实例列表；数量与状态头一致，唯一约束保护实例及穿戴/手位 |
| `s2_character_equipment_command` | 每条装备事件恰好一行完整输入 |
| `s2_character_event_state` | 原状态标量及 equipment_count、完整健康字段；原有资源、成长、熟练、生命骰子表仍完整保存 |
| `s2_character_health_event` | 每条健康事件恰好一行完整输入与结果头，明确效果骰数量及可空 d20/d4 |
| `s2_character_health_die` | 以连续零基索引保存每个效果原骰，数量与健康结果头一致 |

新表依附准确事件或前后状态，外键使用 RESTRICT，UPDATE/DELETE 触发器拒绝改写。
应用增量权限仅 SELECT/INSERT；核验和授权分别见
[角色事实核验](../../database/verify/schema2-character-runtime.sql)与
[运行权限](../../database/grants/schema2-character-runtime.sql)。完整运行清理必须纳入所有子行，
当前仓储不提供隐式删除。

`append`、`restore` 要求调用方提供受共同守卫保护的连接；叶仓储不提交、回滚、关闭连接或
另开写事务。追加先读取现有完整历史，验证 before、全局操作身份和候选完整结构，写后实际
读回并比较。`restore` 直接写入已有完整事实和原骰，不重放命令、掷骰或再次发放收益；
已存在角色、身份冲突或不连续历史拒绝，任一失败由事务 owner 回滚整笔候选。

读回独立查询状态、各类命令和原骰；缺行、多余行、重复/断裂索引、孤儿、跨角色与错误类型
均拒绝，无角色根时也检查全部子表。请求摘要仍作为调用方提供的准确操作事实保存，
仓储只校验摘要格式与唯一性；`Schema2CharacterRuntimeService` 的请求指纹绑定全部有界输入、准确规则、
版本和身份，负责同操作回放、正文冲突拒绝及从保存事件重新核对请求摘要。

## 历史预算与采用边界

每个角色最多 128 个事件、32768 个类型化历史子行。每个前后状态的装备实例、命令/结果头
及每个效果骰都计入预算。`checkCandidateCapacity` 对尚无实际骰子的健康候选保守预留
一个结果头加最多 20 个原骰行；已有事件按实际行数计数，不能只检查事件总数。
容量检查本身不抽骰、不执行效果；RuntimeService 在完整命令预检后、首次随机性前调用它。

真实业务消费者沿共同事务完成规则预检、随机结果生成、追加、实际读回和历史重算，
提交未知时隔离并只读查证原操作；其语义和验证见[角色基础合同](schema-2-character-foundations.md)。
成长/休息规则的状态拷贝保留装备与健康字段，稳定恢复与长休清临时 HP 由唯一健康 owner 协调。
完整初始化、Host、全部特性、持续效果、跨角色协同及 archive format 2 继续按
[采用边界](../design/v2/adoption-boundaries.md)接入，默认 RELEASED 不变。

定向入口见[镜像与事实存储验证](../testing.md#schema-2-检定装备与健康镜像和事实存储验证)。
JDBC 代理与静态结构检查不代替真实 MySQL 的 CHECK、生成列、外键、触发器、权限或持久化验收。
