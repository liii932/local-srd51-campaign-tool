# Schema 2 施法、反应与持续效果

## 采用范围与真实资格

`Schema2CombatRuntimeService` 在同一 INTERNAL 场景中执行 Bless、Cure Wounds、Shield、Counterspell、
Ready attack／move／spell、固定环境抗魔区域及战外持续效果。规则来自[施法作者合同](schema-2-spell-authoring.md)
定义的准确组合与镜像，角色资格来自实际成长、装备、学习和准备记录。共同事务、版本及正常可见
平面方格的前提见[战斗合同](schema-2-combat.md)。这些内部能力不开放完整 base、Host 或 archive format 2。

生命领域 profile 限一级单职业角色：实际取得指定子职业与自动特性后，Bless／Cure Wounds 始终准备。
Wizard profile 限单职业 1—5 级，法术书初始化实际学习六个一级法术，每次升级实际学习两个当前法术位
允许等级的法术。模组的 `spells` 集合只声明可学习名单，不是角色已学或已准备事实。
`SPELLBOOK_INITIALIZED`、`SPELLBOOK_ADVANCED`、`SPELLBOOK_PREPARED` 由角色服务接受；漏掉上一等级
学习步骤不能继续升级，也不能以未完成学习状态施法。升级学习不自动改准备列表。

Wizard 准备列表须来自已学法术，数量不超过 `max(1, INT 调整值 + Wizard 等级)`；初始准备后，仅新一次
实际完成的长休允许改变列表。准备耗时按所选法术每环至少一分钟计入角色时钟，记录已使用的长休标记。
法术位复用 `character.resource`，按等级分别取得一至三环位并由长休恢复。不会创建可独立修改的 slot 副本。
知识名单中的其他法术只提供封闭元数据，执行仍明确拒绝；名单存在不等于法术能力已实现。
Arcane Recovery、Evocation Savant、Sculpt Spells 保留真实获得事实，但当前不能执行其恢复、抄录或塑形能力。

## 施法与反应

| 操作 | 真实前提与后果 |
|---|---|
| `CAST` Bless | 一次 action、一个一级位；V/S/M，实际持有 profile 指定圣徽；选择 1—3 个三十尺内角色，建立一个专注来源及其全部目标应用。 |
| `CAST` Cure Wounds | 一次 action、一个一级位；V/S，至少一只空手；五尺内一个角色；服务器掷 1d8，加 WIS 调整值及生命门徒的三点，受目标 HP 上限约束。 |
| `DECLARE_SHIELD` | 在命中已确定、伤害尚未掷骰的窗口内，由受击且已准备该法术的 Wizard 消费 reaction 和合法法术位；AC 加五，持续至本人下回合开始。保留原命中事实，使用原攻击骰重新判断最终命中；不能重掷攻击。 |
| `DECLARE_COUNTERSPELL` | 对六十尺内可见施法的真实 `CAST_BEFORE_COMPLETION` 窗口，消费 reaction 和三环位。被反制的声明仍保留已支付成本；反制本身可产生嵌套反制窗口。当前真实法术位范围内按 SRD 自动反制，不宣称已接入四环以上施法。 |
| `END_CONCENTRATION` | 在没有待继续动作流的边界主动结束本人唯一专注，不消费 action，可在他人回合执行。 |

不熟练护甲的禁止施法由装备事实决定。当前要求正常发声和可用手部，不支持沉默、特殊组件、组件袋、
抄录、仪式、多职业或其他未知施法机制。Shield 的 Magic Missile 免伤分支仍没有真实伤害 producer；
Magic Missile 元数据不能触发该分支。目标目前为真实角色，怪物目标仍须相应消费者。

完整规则、目标、组件、资源、乐观版本和强制继续容量先于 RNG 与写入验证。每次施法声明只支付一次，
反制或被打断不退款。暂停窗口使用同一 frame graph：选择帧、反应子帧与父帧关系明确保存；CLOSED 窗口
须显式继续才成为 CONSUMED，不以“最后一个窗口”猜测当前 owner。读取和恢复不会隐式 PASS。

## Ready

Ready 使用真实 action 建立 `ReadyPreparation`，响应分别为一次武器攻击、独立速度额度的移动或
已实际施放并保持专注的法术。触发只接纳三种服务器产生的完成事实：指定生物进入指定格后、攻击完成后、
施法完成后；自然语言不被解释为可执行条件。触发发生后在响应前重新检查可用性，释放消费 reaction。

Ready attack 只进行一次攻击，Ready move 使用独立移动额度，仍可触发机会攻击并逐层返回原动作。
Ready spell 在准备时检查组件、消费法术位并开放反制窗口；保持法术即占用唯一专注 holder。
释放不会第二次消费法术位、重新要求组件或制造新的施法声明／反制窗口。PASS 只放弃本次触发，
保留尚未过期的准备。本人下回合开始、失能或保持法术的专注结束会使对应准备失效；原消费不撤销。

## 持续实例、贡献与独立压制

Bless 保存唯一 effect key、完整法术身份、施法者、固定目标和原开始／到期秒数，目标应用身份为
`(effectKey, targetId)`。Shield 使用独立类型化实例与回合期限。每人最多一个显式专注 holder，
其来源为 Bless 实例或 Ready spell；没有第二个可独立修改的专注布尔值。

攻击、机会攻击、CON 专注豁免及死亡豁免按当前有效 Bless 加一个 d4，保存原骰、来源和结果。
自然 1／20 特例保留。相同法术同强实例按真实创建顺序选择最早者，落选者为 `SHADOWED`，
仍保留原期限；胜出者结束后重新选择，不重复施法、不刷新期限。

`adventure.environment` 的 `DM_ANTIMAGIC_ZONE_V1` 是模组声明、DM 放置的固定环境机制，
不是八环 Antimagic Field 法术。区域中心固定，半径十尺，使用当前方格距离；放置、结束均为场景命令。
区域内不能声明施法；区域覆盖的 Bless／Shield 目标应用各自建立独立 `SuppressionCause`。
多个区域的原因全部保留，解除一个仍可能受另一个压制。离开区域、结束区域或永久结束效果时，
只解除准确对应的原因；压制不结束实例、不暂停期限、不解除专注、不退款。
`SUPPRESSED` 与同名覆盖的 `SHADOWED` 分开求值，读路径不能补造或修复缺失关系。

每个实际伤害包先结算健康后果：零 HP／失能／死亡立即结束专注；否则进行 DC
`max(10, floor(damage / 2))` 的 CON 豁免。临时 HP 吸收的伤害仍需检查。效果在该次豁免是否贡献
按当时压制与覆盖状态确定；失败后才结束。主动结束、替换、失能、失败和自然到期保留首次结束事实。

## 战外场景与唯一时钟

`END_COMBAT` 收束当前轮的六秒并切换为 `EXPLORATION`，保留同一 scene/combat ID、角色、位置、
效果、原因和历史。尚未结束的反应流以及 Shield／Ready 的回合期限须先处理，不能把回合期限猜成秒数。
`REENTER_COMBAT` 在同一场景重新掷先攻，保留原效果与其原期限；最终 `CLOSE_SCENE` 要求所有存续
来源和区域已关闭。结束战斗和关闭场景是两个不同操作。

探索沿用角色唯一秒时钟：`EXPLORATION_ADVANCE_TIME` 显式推进 1—60 秒，`EXPLORATION_MOVE`
在角色速度额度内逐个五尺格执行合法步行并推进六秒。探索根 CAST 终结时计一次六秒，反应子施法不另计时；新效果从真实
完成时刻起计持续时间。`EXPLORATION_HEALTH` 只接受冻结 `adventure.vital_effect` 的真实伤害、
治疗或临时 HP，不能提交任意金额；伤害仍触发同一专注 owner。

零 HP、未稳定且未死亡的角色存在时，不能用探索推进时间／移动／施法绕过死亡豁免；须先实际治疗、
稳定或重入战斗处理真实回合。死亡豁免不会在探索中每六秒凭空生成。存续场景继续阻断独立角色写入口，
防止装备、休息或健康操作绕过效果与版本责任。GET、重复请求和恢复都不推进时钟。

## 类型化存储与验证

来源 V009/V010、镜像 V035/V037、事实 V036/V038 保存准确作者、学习名单、角色法术书、命令、
多层 frame/window、原骰、成本、Ready、holder、Shield、区域及全部独立原因。每类子行有精确计数与
引用，缺行、多行、孤儿、未知类型或独立重建不一致均拒绝。既有迁移保持不变。

V038 在任何持久 DDL 前要求 `s2_combat_runtime` 为空，包括已经 ENDED 的旧事实；旧 INTERNAL
动作图和请求格式不自动转换。存在真实旧 DRAFT 战斗历史时须另行审查迁移／恢复方案，不删除历史绕过此门。

资源、健康和时间仍为角色历史的因果子事件；根场景操作与全部子结果共用
[业务事务](business-write-transactions.md)，仓储不得自行提交。`COMBAT_ENVIRONMENT_HEALTH`
保留真实 effect 输入与骰子。直接恢复保留完整当前状态和历史，不重掷、不重施法、不重复学习或付款；
`UNKNOWN` 只查证原操作。

规则优先读取本地 SRD：Wizard [p.52](../references/srd-5.1/pages/page-052.md)／
[p.53](../references/srd-5.1/pages/page-053.md)，Ready [p.93](../references/srd-5.1/pages/page-093.md)／
[p.94](../references/srd-5.1/pages/page-094.md)，组件／专注 [p.102](../references/srd-5.1/pages/page-102.md)，
同法术叠加 [p.104](../references/srd-5.1/pages/page-104.md)，Bless [p.122](../references/srd-5.1/pages/page-122.md)，
Counterspell [p.131](../references/srd-5.1/pages/page-131.md)，Cure Wounds [p.132](../references/srd-5.1/pages/page-132.md)，
Shield [p.179](../references/srd-5.1/pages/page-179.md)。固定区域、同强最早优先和内部容量是项目合同。

[定向验证](../testing.md#schema-2-有限施法与持续效果验证)分开覆盖纯规则、实际角色成长、来源／镜像、
事务、历史与恢复。代理 JDBC 和静态 SQL 不能替代另行授权的真实 MySQL 迁移、权限及并发验收。
