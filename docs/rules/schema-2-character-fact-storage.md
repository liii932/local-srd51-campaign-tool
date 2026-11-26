# Schema 2 角色事实存储


规则角色的检定、装备与健康由 `Schema2CharacterRuntime` 表达类型化命令、原骰、结果、
前后状态与完整历史。`Schema2PreparationIdentity` 是角色初始计划及历史引用的稳定领域事实。
`CampaignDomainEngine` 按战役冻结规则核验历史，SQLite 工作库负责当前运行的控制身份和事务。
[角色基础](schema-2-character-foundations.md) · [数据映射](../design/v2/storage-data-map-and-rollout.md)

初始化版本为 0，后续每个接受事件恰增一次，操作身份唯一；每个 before 必须等于上一 after，
当前状态必须匹配最后 after。规则定义存入冻结材料，装备实例、当前 HP 和真实原骰由业务聚合保存。

## 类型化命令与结果

`Command` 是封闭 union。每类命令仅允许自身字段，不能夹带资源、成长、休息或其他类型输入。
说明字段 `adjudication` 必须已是 NFC、非全空白、1—500 个 Unicode 码点，拒绝 C0/C1
控制字符和孤立 surrogate。完整规则身份、UUIDv4、字段范围和可空值分别验证。

| 事实类型 | 保存内容与形状 |
| --- | --- |
| `CHECK_RESOLVED` / `CheckInput` | 完整 `adventure.check` 身份、`ABILITY/SKILL/TOOL`、六属性之一、适用的完整熟练身份、题材 `subject`（`GENERAL` 或 `STONEWORK_ORIGIN`）、DC 0—30、原始优势/劣势布尔裁定及说明；ABILITY 不携带熟练，SKILL 仅 `character.proficiency`，TOOL 仅 `character.tool` |
| `CheckResult` | 第一个 d20、可空第二个 d20、选中骰、属性修正、基础熟练加值 `baseProficiencyBonus`、实际熟练贡献 `proficiencyBonus`、可空的石工知识来源 `stonecunningRule`、总值、成败，以及独立保存的 `effectiveAdvantage/effectiveDisadvantage`；不能覆盖原输入的裁定 |
| `ITEM_ACQUIRED` / `EquipmentInput` | UUIDv4 实例、完整 `equipment.item` 身份、`CARRIED` 及说明；实例 UUID 等于本次操作 UUID，历史不能重复取得同一实例身份 |
| `ITEM_EQUIPPED` | 实例、`WORN/MAIN_HAND/OFF_HAND/BOTH_HANDS` 之一及说明；输入不再次指定物品定义 |
| `ITEM_REMOVED`、`ITEM_UNEQUIPPED` | 实例及说明；物品定义与目标位置输入均为空 |
| `HEALTH_EFFECT_RESOLVED` / `HealthInput` | 完整 `adventure.health` 与 `adventure.vital_effect` 身份、可空 `KEEP/REPLACE` 选择及说明；该选择的适用语义需由实际消费者验证 |
| `DEATH_SAVE_RESOLVED` | 完整健康规则及说明；效果身份和临时 HP 选择均为空 |
| `HealthResult` | 有序效果骰列表、可空死亡 d20、可空稳定恢复 d4、非负 `rolledAmount/appliedAmount`；效果骰最多 20 个且每个 1—12，死亡骰 1—20，恢复骰 1—4 |

检定结果按保存的有效模式验证：优势/劣势皆有或皆无时只有一个 d20，仅有其中之一时两个骰
取高或低；每个 d20 为 1—20。总值等于选中骰加属性/熟练贡献，成败等于 `total >= DC`。
检定命令保留实际属性和完整技能身份，两者进入请求摘要。重演时先依据冻结检定开关核验组合，再按检定前的角色状态求实际属性修正和基础熟练加值。普通熟练按完整目标身份匹配。石工知识只允许在智力（历史）的石工来历题材使用，实际贡献取当时基础值乘以冻结倍率。题材进入请求摘要。历史读回时，`Schema2CharacterCheckRules` 用当时的初始计划、规则组合、角色状态和原骰核验来源、数值、有效模式与成败。

检定事件不能夹带生命骰结果；只有检定事件保存 `checkResult`。
基础健康效果结果不得携带死亡 d20 或恢复 d4，基础死亡豁免结果不得携带效果骰；
这两类基础事件保存 `healthResult`。下述战斗健康子事件另按准确因果保存结果，
原骰与派生值分别保留，仓储不通过重新执行命令生成缺失结果。

## 完整装备与健康状态

`State.equipment` 最多 32 个 `EquipmentItem(itemId,item,location)`，按 UUID 文本顺序严格排列，
每个实例身份唯一并保留完整物品规则身份。位置仅为
`CARRIED/WORN/MAIN_HAND/OFF_HAND/BOTH_HANDS`；至多一个 WORN，双手占位与任一单手占位冲突。
物品类别、熟练、AC、工具用途及穿脱时间由 `Schema2CharacterEquipmentRules` 结合准确规则镜像判断。

`State.health` 保存临时 HP、死亡豁免成功/失败次数、稳定、死亡及可空稳定恢复分钟和秒。
当前 HP 继续只使用 `State.hitPoints`，不建立第二份权威值。初始健康为
`(0,0,0,false,false,null)`；临时 HP 为 0—65535，成功/失败次数各为 0—2。

- HP 大于 0 时不能死亡或稳定，计数为 0，恢复期限为空。
- 死亡时 HP 为 0、计数为 0、稳定为 false、恢复期限为空。
- 稳定时 HP 为 0、计数为 0、未死亡，恢复期限的分钟／秒必须严格晚于当前时刻，分钟不超过 1,000,000,000。
- 其余零 HP 状态的恢复期限为空；这些形状不授权任何健康状态变化或自动时间收益。

领域历史校验约束每类事件允许改变的字段。检定只能改变角色版本；装备命令保留 HP、健康、成长和
资源，仅允许装备及适用的时间字段变化；健康命令保留装备、时间、成长和资源。
状态结构及邻接检查不替代伤害、治疗、死亡、穿脱或到期恢复的规则语义验证。


## 聚合、投影与直接恢复

`CampaignDomainRepository` 在调用方事务中保存完整角色聚合及关系核验投影。
`CampaignDomainCodec` 的闭合记录登记保护字段形状及类型，一次读回严格核对全历史、参照和投影。
读回失败拒绝继续，不重建遗失的事实。完整文件保留准确冻结材料、完整根子历史和现有业务事实，
打开只恢复和验证既有原骰、装备和状态。

## 历史预算与采用边界

每个角色最多 128 个事件、32768 个类型化历史子行。每个前后状态的装备实例、命令/结果头
及每个效果骰都计入预算。`Schema2CharacterRuntime.checkCandidateCapacity` 对尚无实际骰子的健康候选保守预留
一个结果头加最多 20 个原骰行；已有事件按实际保存的事实数量计数。
应用在完整命令预检后、首次随机性前检查容量，使用类型化事实即可计算。


真实业务消费者按[共同事务](../design/v2/runtime-and-transactions.md)执行预检、结算、追加、实际读回和历史核验。
只有实际接合的当前模型进入 format 3。验证入口见[领域接合](../design/v2/domain-integration.md#可重复验收索引)。

## 战斗因果事实与精确时钟

角色事件引用准确 combat、根操作、frame、
actor、伤害／暴击及秒推进事实。`COMBAT_DAMAGE_APPLIED`、`COMBAT_DEATH_SAVE_RESOLVED`、
`COMBAT_TIME_ADVANCED` 只由[战斗 owner](schema-2-combat.md)同事务产生，基础请求入口拒绝。
原始攻击骰由战斗保存；角色健康行保存准确健康规则和实际 HP／死亡结果，双向核验因果引用。
同一根可按连续子序号先推进角色时间再结算其死亡豁免，各角色版本连续且不重用子操作键。

[有限施法](schema-2-spells.md)使用 `COMBAT_RESOURCE_SPENT` 与
`COMBAT_HEALING_APPLIED`，分别保存原角色一级位资源消费及 Cure Wounds 治疗的准确结果。
二者引用真实战斗根与施法 frame，资源事件不夹带健康结果；自我治疗先扣资源、再写健康，
两个子事件的角色版本连续。Bless 死亡豁免的一个贡献 d4 随健康结果保存，准确效果来源及
豁免审计由战斗保存并交叉核验；不把它混成基础健康效果或客户端角色命令。

`RestState` 保存分钟、秒余数、上次长休收益分钟／秒和活动休息；`HealthState` 的稳定期限
同样保存分钟／秒。非零秒在成长、检定、装备和休息复制中保留。地面装备仍属于原实例，`GROUND` 必须伴随准确战斗身份和 x/y 格坐标；
其他位置禁止这些字段。战斗伤害导致的掉落与 HP 变化写在同一角色事件中。

完整战役恢复同时核对场景及其角色历史，拒绝缺少根归属的角色事件。
