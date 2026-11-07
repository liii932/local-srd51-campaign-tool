# 规则行为的接口承载验证

原评估固定接口后核对 16 条 SRD 5.1 轨迹，得到 **9 P、6 U、1 F**。第四阶段保留原判、
原输入和原预期，再按修订后的权威合同复验。16 条原轨迹在各自列明的条件下均为设计 P；
原先未证明的开放式铭文条件与任意神祇机械后果仍为 U。逐项范围见[缺口账本](#gap-ledger)。

“原设计”引用的是原评估所读的边界；链接指向现在维护的权威合同。修订后的判定单列，
当前程序采用仍以源码和现有测试所覆盖的准确分支为准。表中的骰值只供纸面复验和未来
验收，不是客户端输入或应用测试结果。

## 验证对象与判定方法

行为从 [SRD 5.1 语义索引](../../semantics/srd-5.1/README.md)选取；原文通过各条语义链接的
本地 SRD 页核对。`srd51.*` 是语义标识，不自动成为规则包键、执行算法或持久化类型。
标注为 **Project interpretation** 的内容仍是项目解释，不能改称 SRD 明文。

以下是原评估固定的合同边界。该轮可以引用已有扩展点，但不会用临时补写的接口提升原判。
第四阶段可修订权威合同，并单独对修订后的同一轨迹判定。

| 合同入口 | 原评估使用的边界 |
|---|---|
| [模组分层](module-layering.md#模组到机制的接口)、[应用内契约](module-layering.md#应用内契约) | 规则只声明有限类型和参数；应用机制解释规则；候选、预检、求解、领域应用、共同提交、直接恢复分别负责 |
| [模组接口](module-interface-baseline.md#阶段校验与执行资格) | 新行为先由应用提供明确 consumer 和完整支持合同；作者不能注册处理器、脚本、任意表达式或写状态指令 |
| [动作登记](action-continuation-protocol.md#封闭登记与完整预检)、[活动链](action-continuation-protocol.md#活动链与调用边界) | 应用登记有限阶段、生产者/消费点、成本、原骰、返回点和恢复；`DOMAIN_CHOICE` 与反应分开；继续只消费准确叶子的未执行后缀 |
| [持续效果登记](ongoing-effect-protocol.md#封闭登记重叠与条件聚合)、[时间与发生身份](ongoing-effect-protocol.md#时钟资格变化与重新求值) | source/application/contribution/cause 分开；登记真实消费者、进出、到期、进展与结束义务；保存发生游标及已消费前缀 |
| [施法分派](spellcasting-extension-contract.md#效果分派与公共领域-owner)、[共享伤害](multi-target-save-damage.md#伤害数学共同切面与专注) | 有限类型化子计划、完整目标 reader、共享原骰、逐目标修正、分伤害类型及唯一健康 owner |
| [空间与焦点](world-and-scene-spaces.md#执行焦点与冻结)、[角色经历时间](world-and-scene-spaces.md#角色累计经历时间) | 一处执行焦点；后台时间和游戏事实冻结；恢复资格随角色经历时间携带；缺少合同的跨主体/跨空间依赖拒绝交接 |
| [类型化 DM 裁定](player-participation.md#dm-裁定代操作与暂停) | 有界裁定必须接真实领域消费者；叙事备注、任意状态覆盖不构成规则支持 |

判定记号如下，P 只对逐例写明的前提及分支有效。

| 记号 | 含义 | 可以据此做什么 |
|---|---|---|
| **P** | 被评估的合同明确给出输入、阶段、唯一 owner、后果与恢复 | 可以据此设计并新增应用机制；程序采用需独立证据 |
| **U** | 缺少必要事实、阶段或跨 owner 交接语义，当前无法证明轨迹闭合 | 先补设计决定，再单独验证修订后的接口；不能记为原设计通过 |
| **F** | 指定轨迹要求违反原合同的一条明确不变量 | 必须调整行为范围或修订冲突边界；保留拒绝也不等于支持该轨迹 |

“以后可以新增机制”本身不构成 P。每例须找到已写明的扩展位置，并说明谁提供权威输入、
谁决定结果、谁应用一次。若需要凭空增加跨领域交接，则记 U。下文的“声明”只是应由
有限规则数据提供的内容清单，不是新定稿的 JSON schema；公式由应用机制计算，不接作者表达式。

所有样例共同保留：完整预检先于 RNG/业务写入；已接受成本、原骰、状态、历史、版本与
待继续事项在同一调用方事务内提交；保存和打开不重做命令、不补骰、不扣第二次费用。
未来选择只保存已发生前缀，继续时复验当前后缀。用户选择、空间切换、保存和恢复不自动
消耗游戏时间。未知结果不能换请求键重试，后台空间不能为完成某条规则而偷偷推进。

## 样例矩阵

| 样例 | 被验证的具体轨迹 | 原设计 | 修订后原轨迹 | 当前程序 |
|---|---|---|---|---|
| [V01](#v01) Bardic Inspiration | 攻击 d20 后、判定前选择消费灵感 | P，限攻击分支 | P | 未接入掷后选择 |
| [V02](#v02) Sneak Attack | 命中后的额外骰、暴击与每 turn 用量 | P | P | 未接入 |
| [V03](#v03) Divine Smite | 最终近战命中后选法术位，追加异类型伤害 | P | P | 未接入 |
| [V04](#v04) Grapple + Extra Attack | 一击改为技能对抗，拖带与无动作释放 | U | P | 未接入；当前攻击序列额度为 1 |
| [V05](#v05) Counterspell | 已知瞬时法术的嵌套反制及原前缀恢复 | P | P | 有限子集已接入 |
| [V06](#v06) Fireball + Evasion | 完整目标已确定后的共享伤害、逐目标减免与专注 | P，限伤害分派片段 | P | 未接入 Fireball/Evasion；已有 Burning Hands 相邻链路 |
| [V07](#v07) Spirit Guardians | 明确进入、同 turn 重进、以后 turn 的 START | P，限明确触发片段 | P | 未接入 |
| [V08](#v08) Polymorph | 形态 HP 耗尽、回转及本体溢出伤害 | U | P | 未接入 |
| [V09](#v09) Antimagic Field | 已知覆盖下两个独立原因压制 Bless，期限继续 | P，限贡献压制片段 | P | 固定 DM 区域有相邻机制；真实法术未接入 |
| [V10](#v10) Conjure Animals | 战斗中生成多个主体、群组先攻、消失退场 | U | P | 未接入 |
| [V11](#v11) Forced March / Food and Water | 按自然日归集旅行及生存消耗、逐次累积力竭 | U | P | 仅有 DM 旅行耗时及时间推进 |
| [V12](#v12) Sewer Plague | 同空间潜伏期、症状、休息治疗及治愈顺序 | P，限所列进展 | P | 未接入 |
| [V13](#v13) Wand of Fireballs | 同调、充能施法、最后一充能及黎明恢复 | U | P | 未接入 |
| [V14](#v14) Glyph of Warding | 物件条件触发、一次结算与储存施法释放 | U | P，限列明的条件与释放 | 未接入 |
| [V15](#v15) Divine Intervention | 百分骰／自动成功、有限 DM 后果与恢复资格 | P，限有限后果 | P，限有限后果 | 未接入；现有裁定入口只处理已支持的检定后果 |
| [V16](#v16) Warding Bond | 前台受伤引发后台主体同步受伤 | F | P，真实距离与完整因果域 | 未接入；现行交接拒绝该依赖 |

这些样例覆盖不同接口压力点，未穷举角色构筑、多职业、升级、感官/遮蔽、社交、三维
空间、位面和全部怪物能力。P 的数量不表示完整规则覆盖率，U/F 也不证明整个分层方向不可用。

## 原轨迹、原判依据与当前程序证据

<a id="v01"></a>
### V01：Bardic Inspiration 的攻击掷后选择

语义：[Bardic Inspiration](../../semantics/srd-5.1/classes-barbarian-bard.md#srd51-class-bard-bardic-inspiration)，SRD pp. 12–13。
吟游诗人在自己回合以附赠动作授予 60 尺内能听到自己的另一生物一颗灵感骰，同一受领者
至多持有一颗；10 分钟内可在 d20 后、宣布成败前选择使用，掷出即消费，不消耗反应。
声明内容为距离、期限、骰阶、适用检定类型及次数/恢复档；等级到骰阶的规则由应用解释。

**轨迹。** 5 级、CHA +3 的 Bard 在场景秒 100 授予 d8，次数 `3→2`，期限 700。
同伴攻击 AC 15，原骰 `10+4=14`；保存于选择点，直接恢复仍保留原骰且尚未宣布命中。
选择使用，d8=3，总值 17 命中，实例消费一次；选择放弃则保留尚未到期的实例。

**P 的依据。** [攻击掷后阶段](action-continuation-protocol.md#攻击shield-和骰子选择)明确留出
`ATTACK_ROLLED_BEFORE_OUTCOME`；[非反应选择](action-continuation-protocol.md#活动链与调用边界)
已有 `DOMAIN_CHOICE`。动作 owner 消费附赠动作，资源 owner 扣次数，效果 owner 保管
授予/期限，CheckPlan 保留 d20 并消费独立贡献。重复消费不得再抽 d8，非法受领者须在授予前拒绝。
普通属性检定和所有种类豁免的掷后接合不由本例证明。

**程序。** [攻击实现](../../../src/main/java/com/dndtool/service/Schema2ActionFlowRules.java)的
`beginAttack` 当前直接从 d20 和既有修正判命中；[实际帧枚举](../../../src/main/java/com/dndtool/service/Schema2CombatState.java)
也没有该选择阶段。[独立检定](../../../src/main/java/com/dndtool/service/Schema2CharacterCheckRules.java)、
[有限豁免](../../../src/main/java/com/dndtool/service/Schema2SavingThrowRules.java)与
[`CampaignDomainEngine` 的死亡骰](../../../src/main/java/com/dndtool/service/CampaignDomainEngine.java)
也没有原骰提交后等贡献、再公布及消费的续接。

<a id="v02"></a>
### V02：Sneak Attack、暴击和每 turn 用量

语义：[Sneak Attack](../../semantics/srd-5.1/classes-rogue-sorcerer.md#srd51-class-rogue-sneak-attack)、
[暴击](../../semantics/srd-5.1/combat.md#srd51-core-critical-hits)，SRD pp. 39、96–97。
灵巧或远程武器命中生物，满足优势，或无劣势且目标 5 尺内有另一名未失能敌人时可选用；
每 turn 一次，额外 `ceil(rogueLevel/2)d6`。声明为武器条件、邻接范围、骰阶及 turn 限额。

**轨迹。** 5 级 Rogue 用刺剑优势掷 `[6,20]`，选择追加伤害。暴击武器骰 `2d8=[4,5]`，
固定修正 +3，Sneak Attack `6d6=[1,2,3,4,5,6]`，减免前共 33 穿刺伤害。
同一 turn 第二次命中不能再用；另一个生物的 turn 上，若反应仍可用且借机攻击合格，可再用。

**P 的依据。** [有限阶段与 DamagePacket](action-continuation-protocol.md#封闭登记与完整预检)
允许在最终命中与原伤害骰之间消费带来源的附加分量。攻击 owner 提供武器、优势/劣势及
邻接证明，特性机制消费准确 `(主体, 特性, combat/turn)` 用量，伤害 owner 处理暴击与伤害。
放弃选择不占用次数；已消费实例保存来源、turn、原骰和伤害结果，重开不把额度改成每 round
或仅自己 turn。已用额度或非法武器不产生新增特性骰。

**程序。** `Schema2ActionFlowRules.settleAttack` 只处理当前单一武器 profile，未接此选择、
附加骰来源和每 turn 限额。[实现](../../../src/main/java/com/dndtool/service/Schema2ActionFlowRules.java)

<a id="v03"></a>
### V03：Divine Smite 的命中后资源消费

语义：[Divine Smite](../../semantics/srd-5.1/classes-paladin-ranger.md#srd51-class-paladin-divine-smite)，SRD pp. 31–32。
近战武器命中后可选法术位，L 环位追加 `min(L+1,5)d8` 光耀，对不死生物/邪魔再加 `1d8`。
这次资源消费不是施法。声明为资格、位阶伤害表、伤害类型及目标类型加骰。

**轨迹。** 对不死生物的最终命中造成武器 `5+3=8` 挥砍，选择 2 环位，余额 `1→0`，
光耀 `4d8=[2,4,6,8]=20`，无减免合计 28。若只有武器分量受抗性，结果为 `4+20=24`。
攻击总值 15 原可命中 AC 14，若 Shield 将 AC 提至 19，则最终 MISS，不开放 Smite 消费。

**P 的依据。** 复用[命中后阶段](action-continuation-protocol.md#攻击shield-和骰子选择)、
`DOMAIN_CHOICE` 和带来源/类型的 DamagePacket。资源 owner 一次扣合法位，伤害 owner
保持分量类型；不生成第二次动作、反应或 Counterspell 窗口。保存于选位点保留命中及候选
依据，继续时复验余额；拒绝失效选位不扣费/掷骰。Shield 后再选是既有协议下的机制结算
策略，不是声称 SRD 为所有命中特性规定了普遍先后序。

**程序。** 当前单一武器伤害分支未接此组合；不能借真正的 Wizard 施法入口冒充 Smite。
[攻击](../../../src/main/java/com/dndtool/service/Schema2ActionFlowRules.java) · [施法](../../../src/main/java/com/dndtool/service/Schema2SpellRules.java)

<a id="v04"></a>
### V04：Grapple 替代 Extra Attack 中的一击

语义：[擒抱](../../semantics/srd-5.1/combat.md#srd51-core-grappling)、
[Extra Attack](../../semantics/srd-5.1/classes-fighter-monk.md#srd51-class-fighter-extra-attack)、
[Grappled](../../semantics/srd-5.1/conditions.md#srd51-condition-grappled)，SRD pp. 25、77、95–96、358。
需空手、触及内、目标至多大一级；以 STR(Athletics) 对目标选择的 STR(Athletics) 或
DEX(Acrobatics) 对抗，替代 Attack 动作中的一击。拖带通常使速度减半；释放无需动作。

**轨迹。** 5 级 Fighter 有两击。第一击擒抱 `11+5=16` 对 `10+4=14` 成功，第一 ordinal
已消费，第二击仍可用。目标后来花动作挣脱，双方各 14，平局保持擒抱。
反例：初始对抗各 17 时维持未擒抱，但该一击仍已使用。拖带时两者位置须同事务变化。

**U 的原因。** [攻击序列 owner](action-continuation-protocol.md#各领域-owner-的继续责任)
已有额度/ordinal，但其 ATTACK 合同以武器、攻击 audit、hit/critical 为基准，未规定
“无攻击掷骰的对抗 attempt”如何消费同一序列。成对移动和“随时释放”进入当前等待写门的
许可也未闭合。目标选技能时的保存须保留原 ordinal 与选择权，不能另建普通检定而丢掉序列。
新增替代 attempt 或联动移动接口后只能验证新设计。

**程序。** [BEGIN_ATTACK](../../../src/main/java/com/dndtool/service/Schema2CombatRules.java)
当前额度为 1；[角色包](../../../rule-packages/srd51-runtime/srd51-characters/rules.json)
的 Extra Attack 执行标记为 `BLOCKED`。

<a id="v05"></a>
### V05：嵌套 Counterspell

语义：[Counterspell](../../semantics/srd-5.1/spells-a-c.md#srd51-spell-counterspell)，SRD p. 131。
看到 60 尺内生物施法时用反应；目标有效环级不高于反制位则自动成功，否则以施法属性作
`DC=10+目标有效环级` 的属性检定。声明为触发、范围、组件、环级与检定规则。

**轨迹。** A 声明 1 环 Bless，B 用 3 环位反制 A，C 用 3 环位反制 B。C 自动成功，
B 被反制，A 继续产生 Bless；三个真实声明各保留自身动作/反应和位成本，反制链零检定骰。
保存于最内层后直接恢复准确叶子；错误指向父 A 的继续请求拒绝，不能重新扣 B 的成本。

**P 的依据。** [Counterspell 与准备法术](action-continuation-protocol.md#counterspell-与准备法术)
已经定义真实 cast、`CAST_BEFORE_COMPLETION`、子反应、内层优先、返回父帧及原成本保留。
动作 owner 管反应，资源 owner 管位，施法 owner 管真实 cast 终态。高阶检定、额外贡献、
复杂视线和长施法的分支不由这个自动成功轨迹证明。

**程序。** 当前规定所有参战者彼此可见、无遮挡的网格内，有限法师/3 环位反制链已接入。
[场景前提](../../rules/schema-2-combat.md) · [资格实现](../../../src/main/java/com/dndtool/service/Schema2SpellRules.java)
存在[嵌套测试](../../../src/test/java/com/dndtool/service/Schema2ReactionSpellRulesTest.java)和
[恢复测试](../../../src/test/java/com/dndtool/service/Schema2SpellSaveDamageRuntimeServiceTest.java)源码。
[`completeCast` 中的局部算式](../../../src/main/java/com/dndtool/service/Schema2ActionFlowRules.java)
直接取 INT、无额外贡献；不代表高环或通用见证分支具有真实输入链。本轮未运行这些测试。

<a id="v06"></a>
### V06：Fireball、Evasion、抗性与专注

语义：[Fireball](../../semantics/srd-5.1/spells-e-g.md#srd51-spell-fireball)、
[Evasion](../../semantics/srd-5.1/classes-rogue-sorcerer.md#srd51-class-rogue-evasion)，SRD pp. 144、40、97、102。
Fireball 的完整规则含 150 尺施法距离、20 尺半径球体、绕角传播和点燃未穿戴/携带的可燃物。
**本例只验证空间 owner 已证明完整目标集合后的伤害分派**，不假定客户端名单就是完整区域。

**轨迹。** 三环、DC 15，共享 `8d6=[6,6,5,4,3,3,2,2]=31`。A 普通失败，temp 7、HP 45
变为 temp 0、HP 21；原专注按 31 伤害作 DC 15 检定，CON `11+3=14` 失败。
B 有 Evasion 与火抗且豁免失败，`31→15→7`；C 有 Evasion 且成功，伤害 0。
D 普通成功，有两个火抗来源，仍是 `31→15→7`，不会再次减半为 3。

**P 的依据。** [施法分派](spellcasting-extension-contract.md#效果分派与公共领域-owner)
连接[逐目标修正及共同 APPLY](multi-target-save-damage.md#伤害数学共同切面与专注)。规则数据
给出骰阶、DC 来源和有限修正类型；应用先解释 Evasion，再处理抗性，健康 owner 一次应用
临时 HP/HP，专注 owner 消费对应实际伤害。保存保留共享原骰和每目标进度；最后一个目标
版本失效也须在新骰和部分写入前拒绝。合法成功无伤害与非法目标不同。

**原未证明部分。** 球体/绕角/可燃物、没有生物但有对象时的效果原为 U。
当前[共享链](../../../src/main/java/com/dndtool/service/Schema2SaveDamageRules.java)接的是
Burning Hands 的有限锥体；未知区域/特定落地装备分支关闭，角色抗性 reader 尚无本例消费者。

<a id="v07"></a>
### V07：Spirit Guardians 的进入与回合触发

语义：[Spirit Guardians](../../semantics/srd-5.1/spells-n-s.md#srd51-spell-spirit-guardians)，SRD p. 182。
自身 15 尺范围、专注至多 10 分钟，施放时选可见豁免者；受影响生物速度减半。
首次在某个 turn 进入，或自己 turn 开始时在区内，作 WIS 豁免；基础 3d8，升环每环 +1d8。

**轨迹。** 四环、善良施法者，场景秒 100 建源，期限 700，豁免可见 A。B 从 20 尺外进入
15 尺内，速度 `30→15`；首次进入失败，`4d8=[6,5,4,2]=17`，HP `40→23`。
B 同 turn 离开再进入不再次产生进入伤害。到 B 以后一次 START 仍在区域，成功豁免
`4d8=[8,7,5,3]=23`，减半 11，HP `23→12`。

**P 的依据。** [真实区域/回合发生身份](ongoing-effect-protocol.md#时钟资格变化与重新求值)
与[移动前缀/后缀](action-continuation-protocol.md#移动机会攻击和-ready-move)已有接点。
空间 owner 提供真实 entry，效果 owner 保管源、豁免集合、holder、期限、逐触发游标；
检定/健康 owner 消费本次伤害。保存不重置 entry/turn 游标，不能给不可见目标补豁免身份。

**原未证明部分。** 施放时已有生物在区内、区域扫过静止者、同 turn 的 START 后首次进入、
压制解除及探索模式触发仍待具体语义和边界决定；不能用一个“本 turn 已处理”布尔值合并
不同触发。当前[持续效果实现](../../../src/main/java/com/dndtool/service/Schema2OngoingEffectRules.java)
没有该真实移动区域或周期消费者。

<a id="v08"></a>
### V08：Polymorph 的形态健康交接

语义：[Polymorph](../../semantics/srd-5.1/spells-n-s.md#srd51-spell-polymorph)，SRD p. 169；狼见 p. 393。
自愿、非 shapechanger、非 0 HP 的合法目标可变成 CR 不高于目标 CR/等级的野兽。
形态统计替代本体统计，保留人格/阵营，装备融合且不提供贡献。形态归零/死亡/效果结束时回转。

**轨迹。** X 本体 HP 24，变成 HP 11 的狼，稳定主体 ID 不变。一次减免后伤害 17 耗尽
形态，回转后将溢出 6 应用于本体，HP 18，装备贡献恢复。中间形态归零不能先产生本体
倒地/掉落装备事件。中途保存必须同时保留本体和当前形态健康、装备实例及来源。

**U 的原因。** [唯一健康 owner](module-layering.md#权威事实与身份)、
[专注及结束义务](ongoing-effect-protocol.md#专注结束范围与一次性回调)并未规定上述
“形态耗尽→回转→溢出→最终健康观察”交接。普通 HealthPlan 或结束回调的存在不能证明
下游不会提前看到死亡。尺寸/占位、抗魔压制后形态恢复也缺具体合同。非自愿目标成功豁免
是合法付费无效果，不能与非法形态/CR 的前置拒绝混为一谈。

**程序。** [角色事实](../../../src/main/java/com/dndtool/service/Schema2CharacterRuntime.java)
与[健康规则](../../../src/main/java/com/dndtool/service/Schema2CharacterHealthRules.java)
维护一组普通 HP/统计，未接形态层次。

<a id="v09"></a>
### V09：两个 Antimagic Field 原因与 Bless 到期

语义：[Antimagic Field](../../semantics/srd-5.1/spells-a-c.md#srd51-spell-antimagic-field)、
[Bless](../../semantics/srd-5.1/spells-a-c.md#srd51-spell-bless)，SRD pp. 117、122。
完整 Antimagic Field 是自身 10 尺球体、专注至多一小时，含物件、区域裁切、召唤、传送与
神器/神祇例外。本例前提是同场景的覆盖及可压制资格已经被证明，只核对目标贡献。

**轨迹。** Bless 在场景秒 100 生效，160 到期。T1 同时受独立 F1/F2 压制，T2 在外仍有 d4。
120 时 F1 移开，T1 仍被 F2 压制；140 时 F2 移开，T1 恢复贡献；160 时来源结束。
若 F2 到 160 后才移开，则不恢复。已付位、已掷骰和既有伤害不因压制变化退还或重算。

**P 的依据。** [source/application/cause 身份](ongoing-effect-protocol.md#对象身份与唯一所有者)
及[重新求值](ongoing-effect-protocol.md#时钟资格变化与重新求值)明确区分独立原因、暂时
无贡献与永久结束。效果 owner 管两份 cause/episode，时间 owner 推进原期限，检定 owner
只消费当前合法贡献。保存保留两个原因；解除一个不能清空全部原因，到期不能被抗魔暂停。

**原未证明部分。** 原轨迹不含完整球体、魔法武器使用情境、例外来源、召唤暂隐后占位
和传送门。当前[固定 DM 区域](../../../src/main/java/com/dndtool/service/Schema2OngoingEffectRules.java)
能影响有限 Bless/Shield 消费者，[测试源码](../../../src/test/java/com/dndtool/service/Schema2OngoingEffectRulesTest.java)
包含多个独立原因与期限结束；这条运行链没有真实 8 环法术。

<a id="v10"></a>
### V10：Conjure Animals 的生成、群组先攻与退场

语义：[Conjure Animals](../../semantics/srd-5.1/spells-a-c.md#srd51-spell-conjure-animals)、
[召唤选择解释](../../semantics/srd-5.1/spells-a-c.md#srd51-spell-conjuration-choices)，SRD pp. 127、90、393。
三环、专注至多一小时，选择数量/CR 档；具体属性块由 DM 选择是语义文档的项目解释。
召唤群组共用先攻、有自己的 turn；口令无需动作，无口令仅自卫；归零或法术结束即消失。

**轨迹。** 选 2 只 CR≤1，由 DM 选两狼：两个可见空格、两个稳定主体 ID、各 HP 11，
组先攻只掷一次 `12+2=14`。W1 受 11 伤害后消失，释放占位；W2 保留，专注结束才退场。
保存后 W1 不重生、先攻不重掷。友善与口令关系不自动授予玩家账号控制权。

**U 的原因。** [有限施法子计划](spellcasting-extension-contract.md#效果分派与公共领域-owner)
与来源结束义务可描述上层原因，但[空间命令](world-and-scene-spaces.md#空间和模式命令)
尚未闭合战斗中原子生成/退场、群组插入轮次、当前成员消失及 pending 重验的交接。
不能先在世界创建，再伪装成战斗内合法生成。任一初始格非法应使整次声明在付费/RNG 前拒绝。

**程序。** [monster-create/applyScene](../../../src/main/java/com/dndtool/service/storage/CampaignSpaceCommands.java)
只允许在世界节点创建，场景回写要求主体已经安置；[初始先攻](../../../src/main/java/com/dndtool/service/Schema2CombatRules.java)
按每只怪物独立结算，不提供本例的群组生命周期。

<a id="v11"></a>
### V11：强行军、食水与自然日

语义：[Forced March](../../semantics/srd-5.1/exploration.md#srd51-core-forced-march)、
[Food and Water](../../semantics/srd-5.1/exploration.md#srd51-core-food-and-water)、
[Day boundary](../../semantics/srd-5.1/exploration.md#srd51-core-day-boundary)，SRD pp. 84、86–87、358。
每天超过 8 小时的每小时末逐角色作 CON 豁免，DC 从 11 起递增，失败各加一级力竭。
食水不足产生的力竭有独立恢复限制，不能只存一个丢失来源的总数。

**轨迹。** 同一天已有 8 小时旅行。第九小时 A 原力竭 2、CON 总值 10，失败变 3；
B 总值 11 成功。第十小时 DC 12，A 已受豁免劣势，原骰 `[15,9]`、修正 0，取 9 失败变 4；
B 总值 12 成功。两次检定须消费各自时点的当前力竭，不能按出发时状态一次算完。
另一个反例：食水不足来源的力竭在未吃喝足量前不能借普通休息直接清除。

**U 的原因。** 语义文档对未指定重置的 per-day 采用 DM 建立的世界自然日，这是明确标注
的项目解释。[当前角色时钟](world-and-scene-spaces.md#角色累计经历时间)则携带实际经历秒，
奥术回想当前用 `floor(个人经历秒/86400)`；两者并不等价。接口还缺每日活动归集、跨空间
日界和食水欠量的事实边界。时间服务可推进秒，不代表已有“旅行了多久/哪一天”的 reader。
保存须保留活动日、逐小时已消费前缀和各类力竭来源，否则重开会补骰或错误恢复。

**程序。** [旅行入口](../../../src/main/java/com/dndtool/service/storage/CampaignSpaceCommands.java)
按 DM 裁定分钟推进源域并执行空间交接；[时钟测试源码](../../../src/test/java/com/dndtool/service/storage/CampaignSpaceTimeBoundaryTest.java)
覆盖跨快慢时钟的恢复与空间写回滚。二者无强行军、速度、地形或食水消耗消费者。

<a id="v12"></a>
### V12：Sewer Plague 的潜伏期、症状和休息结算

语义：[Sewer Plague](../../semantics/srd-5.1/hazards-health.md#srd51-hazard-sewer-plague)，SRD pp. 199、87、358。
合格 humanoid 暴露后 CON DC 11，失败感染，1d4 天后出现症状：加一级力竭、生命骰治疗
减半、长休不恢复 HP。症状后的长休末必须作 CON DC 11，失败加一级，成功减一级，降至 1 以下治愈。

**轨迹。** 同一焦点空间，潜伏骰 d4=2，保留 172800 秒期限；到期只加一次症状/力竭。
生命骰普通治疗值 7 变为 3。长休开始 HP 8、力竭 1，完成时仍 HP 8；疾病豁免总值 11，
力竭降至 0 并治愈，这次长休不会事后补 HP，下次合法长休按健康规则恢复。
先确定本次治疗、再作疾病豁免、再处理独立食水减级，是语义文档已标注的项目解释。

**P 的依据。** [疾病进展与截止边界登记](ongoing-effect-protocol.md#时钟资格变化与重新求值)
允许具体 deadline/进展消费者；[力竭来源](ongoing-effect-protocol.md#封闭登记重叠与条件聚合)
保留逐实例实际等级。[时间与休息 owner](world-and-scene-spaces.md#角色累计经历时间)提供
真实完成的休息及其截止边界，健康 owner 在该时点读取治疗限制，疾病机制随后提交豁免/
减级/治愈。休息边界须带准确完成事实和 episode，不能把任意经过八小时当长休完成。
保存感染 ID、原潜伏骰、期限、症状消费及休息进度；重开不重掷/重加。

**程序与限制。** 当前[健康规则](../../../src/main/java/com/dndtool/service/Schema2CharacterHealthRules.java)
与[持续效果](../../../src/main/java/com/dndtool/service/Schema2OngoingEffectRules.java)未接疾病消费者。
本例不证明迁移感染者、石化暂停、多疾病同时触发或所有疾病治愈归属。

<a id="v13"></a>
### V13：Wand of Fireballs 的实例资源与黎明

语义：[Wand of Fireballs](../../semantics/srd-5.1/magic-items-s-w.md#srd51-item-wand-of-fireballs)，SRD pp. 248–249、206–207、144。
需施法者同调，持有时花动作和充能施放 DC 15 的 Fireball；1 充能三环，每多 1 充能升一环。
最大 7，黎明恢复 `1d6+1`；花掉最后一充能后 d20=1 则化灰毁坏。

**轨迹。** 已同调的同一物件花 3 充能，`7→4`，得到五环 Fireball 的 10d6，不扣角色位。
若合法黎明发生，恢复骰 d6=5，受上限限制只回 3，余额 7。末次充能后 d20=1 的已毁物件
不能到黎明再恢复。保存必须保留物件 ID、持有/同调、余额、毁坏和已消费黎明身份。

**U 的原因。** [物品施法授权](spellcasting-extension-contract.md#类型化授权与真实资格)
已给出机制方向，但完整物件生命周期、黎明事件与场景暂停没有闭合的时间合同。
与同调者相距超过 100 尺持续 24 小时的边界还需要跨空间距离/计时定义。V06 的完整 Fireball 几何
缺口也传递到这里。不能拿角色每日恢复或墙钟替代黎明，也不能把转交物件实现为复制新资源。

**程序。** 当前[施法实现](../../../src/main/java/com/dndtool/service/Schema2SpellRules.java)
和[应用支持门](../../../src/main/java/com/dndtool/service/storage/CampaignRuleSupport.java)
没有本例的物品授权/充能/黎明消费者。

<a id="v14"></a>
### V14：Glyph of Warding 的物件触发与储存施法

语义：[Glyph of Warding](../../semantics/srd-5.1/spells-e-g.md#srd51-spell-glyph-of-warding)，SRD pp. 149–150。
一小时施放，消费至少 200 gp 材料；刻在指定表面或可封闭物件内。容器离原施放位置超过
10 尺则结束而不触发；施放时定义触发/豁免条件。可选爆炸符文或实际施放并储存另一个法术。

**轨迹。** 盒内设“打开时未说出指定口令”触发。口令正确不开火；移动 10 尺不因距离结束，
15 尺结束且不能移回复活。合法触发的三环爆炸共享 `5d8=23`，失败 23、成功 11，只触发一次。
储存法术分支须保留铭刻时的真实授权和成本，释放不再收同一成本；原需专注的效果按全时长
存在，无人维持专注。存档须保留原位置、物件、条件、储存声明及一次性触发事实。

**U 的原因。** 现有[效果发生/结束](ongoing-effect-protocol.md#时钟资格变化与重新求值)
可保管已定义来源，但缺少真实表面/物件身份、开合/阅读/口令等事件事实与有限条件语义。
[封闭施法授权](spellcasting-extension-contract.md#类型化授权与真实资格)也没有储存符文
释放这种授权，不能伪装成角色重新施法或 Ready。稳定等待未来触发不应永久占用活动 flow；
只有实际触发后尚未完成的义务进入当前流。爆炸球体/绕角还受 V06 同类缺口约束。

**程序。** [空间命令](../../../src/main/java/com/dndtool/service/storage/CampaignSpaceCommands.java)
提供主体网格，[施法规则](../../../src/main/java/com/dndtool/service/Schema2SpellRules.java)
只承接有限法术；未见真实铭文宿主、条件 reader 或储存释放授权。

<a id="v15"></a>
### V15：Divine Intervention 的有限 DM 后果

语义：[Divine Intervention](../../semantics/srd-5.1/classes-cleric-druid.md#srd51-class-cleric-divine-intervention)，SRD p. 17。
10–19 级 Cleric 花动作，请求帮助，d100≤职业等级成功；20 级自动成功不掷骰。
DM 决定帮助内容。成功后等 7 天再用，失败后须完成长休。

**轨迹。** 10 级 Cleric 的 d100=10 成功，11 失败。成功分支由 DM 选择预先限定且已有
真实健康消费者的治疗后果：对已预检目标直接治疗 5，HP `8→13`，未超过上限 20，不另付
施法动作或位。该分支在同一逻辑秒执行；随后等待 604800 秒的实际经历时长。
失败分支保留已用动作与原骰，直到一次合法长休完成才恢复资格。20 级分支零随机样本。
若等待 DM，保存原百分骰、动作成本和准确选择叶子，重开不替 DM 选、不重新尝试成功率。

**P 的依据。** [有限阶段和选择](action-continuation-protocol.md#封闭登记与完整预检)、
[类型化 DM 裁定](player-participation.md#dm-裁定代操作与暂停)及
[个人恢复资格](world-and-scene-spaces.md#角色累计经历时间)能连接本例。特性机制解释成功率，
DM 只选择已预检的有限后果，健康 owner 应用治疗，资源 owner 保管恢复条件。未知后果拒绝，
不能提交任意 JSON 修改世界；不是每种神祇干预都由此通过。百分骰后另起无关联的裁定根
不能取代该未决特性的继续责任。

**程序。** [现有 DM 裁定](../../../src/main/java/com/dndtool/service/storage/CampaignAdjudicationCommands.java)
只调用受支持的检定后果；[职业支持](../../../src/main/java/com/dndtool/service/storage/CampaignRuleSupport.java)
也没有该高等级特性。因此这是设计可承载的有限轨迹，不是当前自由卡/备注的自动能力。

<a id="v16"></a>
### V16：Warding Bond 跨空间传递伤害

语义：[Warding Bond](../../semantics/srd-5.1/spells-t-z.md#srd51-spell-warding-bond)，SRD pp. 191、97。
双方持续佩戴各值至少 50 gp 的戒指，60 尺内目标有 +1 AC/豁免及全伤抗性，每次目标受伤
施法者也受同量伤害；超过 60 尺、施法者归零、重施、动作解除或一小时到期则结束。
按语义文档的项目解释，传递目标减免后的伤害并保留类型，施法者自己的防御再适用。

**轨迹。** 同空间的 14 火伤先被目标抗性变为 7；施法者若也有火抗，收到 7 后变为 3。
现在要求目标在前台受伤、施法者在后台且羁绊仍有效：必须同步更新后台施法者 HP，可能还
引发其归零、专注或其他后果。把后果排到切回时补算不能实现同一次伤害的原子因果。

**F 的原因。** 原[后台冻结](world-and-scene-spaces.md#执行焦点与冻结)禁止该写入，
原[空间交接](world-and-scene-spaces.md#空间和模式命令)关闭未接合的跨主体影响。
两个场景锚定同一世界节点不提供物理距离，不能自动推定“超过 60 尺所以结束”。
拒绝携带该依赖转移是合法现行守卫，但不等于支持本例的跨空间羁绊行为。

**程序。** 当前没有 Warding Bond 消费者。[空间入口](../../../src/main/java/com/dndtool/service/storage/CampaignSpaceCommands.java)
对后台主体返回 `SPACE_FROZEN`，并拒绝携带活动场景依赖的交接；
[共享伤害实现](../../../src/main/java/com/dndtool/service/Schema2SaveDamageRules.java)
仍按所属场景内的有限目标处理。跨空间作用域只存在于修订后的设计。

<a id="gap-ledger"></a>

## 缺口账本

原判 P 只覆盖前表指定的片段；P 样例的额外分支在本表用原 U 登记。每个编号独立追踪，
结论只适用于表中列明的生产者与消费条件。程序列链接到对应样例的源码证据，所有 P
仍需按[接入责任清单](mechanism-integration-checklist.md)取得运行采用资格。

### 检定、攻击和施法

| 缺口 | 原→修订 | 合同接点与分支复验 | 当前程序证据 |
|---|---|---|---|
| G01.1 普通能力检定、技能对抗 | U→P | d20=10、+4 与 d8=3 得 17；普通 DC 15 成功，对抗胜 total 16，双方结清前无胜负。保存的是 14 和可选骰，错 choice 零新骰。[检定阶段](action-continuation-protocol.md#check-stages) | [V01](#v01)；有限独立检定无该 choice |
| G01.2 普通属性及专注豁免 | U→P | DEX DC 15 或实伤 30 的 CON DC 15，同一 14→17 通过；自动失败仅保存真实条件，零 d20。专注结束消费准确 holder 义务，重复不得重扣灵感。[检定阶段](action-continuation-protocol.md#check-stages)、[专注 cut](multi-target-save-damage.md#typed-damage-application) | [V01](#v01)；有限 save 无该续接 |
| G01.3 死亡豁免 | U→P | 0 HP 且未稳定时 8+3=11 记一次成功；自然 1 仍两次失败，20 走恢复 1 HP 分支。坏当前生命状态或过期叶子不产生新骰或计数。[死亡向量](action-continuation-protocol.md#check-and-attack-vectors) | [V01](#v01)；当前死亡骰无该选择 |
| G04.1 攻击替代及强制技能选择 | U→P | 5 级两击，首 ordinal 先保留唯一擒抱 attempt。选择技能点无攻击骰/伤害；无手、过大、越距拒绝整次新 attempt。[擒抱替代](action-continuation-protocol.md#grapple-attempt) | [V04](#v04)；额度 1，Extra Attack 阻断 |
| G04.2 对抗、平局与挣脱 | U→P | 16>14 建关系；初次 17 平局消费首 ordinal、无 Grappled。后续花动作挣脱平 14，状态不变；恢复不补另一笔 action。[擒抱替代](action-continuation-protocol.md#grapple-attempt) | [V04](#v04)；无此对抗 owner |
| G04.3 拖带 | U→P | 尺寸差不足两级时主速度 30→15，否则维持 30。真实 OA 在离开前，被拖者不耗移动且无自身 OA；配对第二端失败时两端位置与 spent 同回滚。[联动步段](action-continuation-protocol.md#coupled-move) | [V04](#v04)；现行移动为单主体 |
| G04.4 无动作释放及失效 | U→P | 当前等待期间释放只结束准确关系与手位。重复释放不多消条件；联动等待恢复后只停后缀，已走位置、原骰不倒改。[释放写门](action-continuation-protocol.md#grapple-attempt) | [V04](#v04)；无关系或例外写门 |
| G05.1 高阶、升环、非 INT | U→P | 3 环对真实 5 环 DC 15，Sorcerer CHA +3 与 d20=8 得 11；用 5 环位自动成功、零骰。原环级不允许客户端替换。[反制检定](action-continuation-protocol.md#counterspell-check) | [V05](#v05)；有限实际位，局部 INT 分支 |
| G05.2 能力检定贡献 | U→P | 上例合法灵感 d6=4 后 15 成功，无默认 PB，Bless 不加；有真实 Cutting Words 时可在公布前消费其反应。恢复原 11 后只抽待选贡献骰。[反制向量](spellcasting-extension-contract.md#spell-vectors) | [V05](#v05)；无通用选择及贡献 |
| G05.3 真实观察及清晰路径 | U→P | 60 尺、有视线和实际施法见证才进入候选；61 尺、实墙、无施法见证均不获此机会。错 cast 或旧见证请求零成本，已接受事实不因后移墙面重写。[见证](spellcasting-extension-contract.md#counterspell-witness)、[视线](spatial-and-area-contract.md#visibility) | [V05](#v05)；现行场景假定彼此可见 |
| G05.4 长施法中断 | U→P | 第十个工作 action 已付、此前 54 秒时成功反制，保留十次 action 与 54 秒，末段信用、位、耗材和效果未发生。保存第十段窗口不重开 START；错 ordinal 拒绝。[工作边](spellcasting-extension-contract.md#long-work) | [V05](#v05)；无长施法 |
| G06.1 球体、绕角及完整集合 | U→P | 区内可绕障碍的目标纳入，完整阻断或超 20 尺排除。150 尺内指向被遮挡点时，在首交面近侧按精确距离及坐标序确定合法格点；无点返回 `NO_LEGAL_NEAR_SIDE_ORIGIN`，付款和掷骰均未发生。漏掉最后一个 roster 成员同样拒绝。[近侧取样](spatial-and-area-contract.md#visibility)、[空间区域](spatial-and-area-contract.md#areas)、[快照](spellcasting-extension-contract.md#spell-target-snapshots) | [V06](#v06)；已有有限锥体 |
| G06.2 对象点燃及只有对象 | U→P | 触达的裸可燃对象点燃一次；已燃、封闭壳体阻隔、穿戴/携带或被抗魔裁切无新点燃。只有对象也点燃，零伤害骰及 save；最后对象写失败使本阶段 HP、燃烧共同回滚。[点燃](multi-target-save-damage.md#fireball-damage) | [V06](#v06)；无点燃消费者 |

### 区域、形态和召唤

| 缺口 | 原→修订 | 合同接点与分支复验 | 当前程序证据 |
|---|---|---|---|
| G07.1 初建区域与豁免 | U→P | 创区时区内未豁免者有减速，无进入伤害；原可见 A 保持豁免。非法或不可见豁免目标在建源前拒绝。[Spirit Guardians](ongoing-effect-protocol.md#spirit-guardians) | [V07](#v07)；无真实区域 |
| G07.2 扫过与实际进入 | U→P | 源走过静止者只重求覆盖；目标实际被推动入界可形成 `GUARDIANS_FIRST_ENTRY`。写段失败无半个位置前缀，重放同一边不再产生进入。[联动边界](spatial-and-area-contract.md#coupled-move)、[触发](ongoing-effect-protocol.md#spirit-guardians) | [V07](#v07)；无该移动归因 |
| G07.3 同 turn 的 START 和 ENTRY | U→P | 区内 START 后离开又第一次进入仍可再触发；再次重进不再触发。分别保留发生键，恢复入口不把两类事件合并。[触发](ongoing-effect-protocol.md#spirit-guardians) | [V07](#v07)；无发生账本 |
| G07.4 压制恢复及重叠 | U→P | 被压制或落选的首次进入记录 SKIPPED，解除不补发；两个同名区只用 winner 的伤害/减速，不乘成 30→7.5，已发生的 START/ENTRY 不重抽。[重叠与触发](ongoing-effect-protocol.md#spirit-guardians) | [V07](#v07)；无该区域 winner |
| G07.5 探索触发 | U→P | 明确探索 epoch 的六秒切片及真实主体顺序，不因同切片拆命令多得 START。保存于 START 余集不补时间、不推进后台；错 epoch 拒绝。[探索时序](ongoing-effect-protocol.md#探索中的-temporalturncontext) | [V07](#v07)；无探索上下文 |
| G08.1 资格、统计和装备 | U→P | 合法野兽替代统计、稳定 ID 和人格保留，装备融合不贡献。非法 CR 或损坏 reader 前置拒绝；合法目标为 0 HP 或 shapechanger 时保留已付成本并记 `NO_EFFECT`，不掷豁免；其他不自愿合法目标 WIS 成功也为已付无效果。[形态资格](character-state-and-history.md#form-health) | [V08](#v08)；普通健康/统计 |
| G08.2 耗尽、回转、溢出 | U→P | 狼形态 11 接受 17 后同包溢出 6，本体 24→18。无临时本体倒地或两份 CON，回转后当前 CON 用实伤 17 的 DC 10。伤害/回转中任一写失败无半回转。[形态健康](character-state-and-history.md#form-health) | [V08](#v08)；无形态交接 |
| G08.3 尺寸、压制和恢复 | U→P | 回转变大且原场景/时间域无合法格时停在 `WAITING_PLACEMENT`；仅占位变化可重算原 episode，压制或结束改变应安置身体时原子关闭旧计划，建立当前身体的计划。已结的 17 伤害、6 溢出、本体 HP18 及相关消费不重做。保存两层 HP、原锚及新叶子。[形态压制](character-state-and-history.md#form-suppression)、[安置](spatial-and-area-contract.md#placement-transition) | [V08](#v08)；无两层状态 |
| G09.1 完整球体、裁切与目标 | U→P | 随身 10 尺球体独立 cause；与 Fireball 仅部分重叠只裁切受覆盖部分，外部目标仍正常。场内未声明施法零成本，已合法声明后受压按实际阶段保成本。[抗魔使用范围](ongoing-effect-protocol.md#antimagic-field) | [V09](#v09)；固定 DM 区域 |
| G09.2 魔法物件、武器和弹丸 | U→P | 普通物件魔法属性停用但保物理属性和充能；弓在场内仍压制，箭完全出全部场恢复、重入再压制。保存实际路径与分量证明；伪造出界或缺最后路径读集拒绝。[抗魔武器](ongoing-effect-protocol.md#antimagic-field) | [V09](#v09)；无武器路径消费者 |
| G09.3 神器及直接神祇来源 | U→P | 冻结神器本身、由它实际创建的效果及直接神祇因果可例外；普通信仰、名称或客户端标记不通过。恢复核对来源链，缺证不默认放行。[来源分类](ongoing-effect-protocol.md#antimagic-field) | [V09](#v09)；无完整来源分类 |
| G09.4 召唤暂隐、复现与终结 | U→P | 解除最后原因才在原场景/时间域找安置；无合法格保存 `WAITING_PLACEMENT`。获准的因果 scope 中再次压制使原返回为 `CANCELLED_RESUPPRESSED`，仍暂隐且保同一 ID、HP 和先攻；再次解除最后原因才开新 episode。源终止优先 `CANCELLED_SOURCE_ENDED` 永久退场。恢复只装载取消事实及准确新叶子，不等待已取消的 RETURN。[抗魔暂隐](ongoing-effect-protocol.md#antimagic-field)、[安置](spatial-and-area-contract.md#placement-transition) | [V09](#v09)；无主体暂隐 |
| G09.5 传送、位面旅行与门户 | U→P | 传送任一端覆盖，无转移/焦点变化；门户实际开口交叠关闭通路，物理门仍可开。移场解除时不补传；过期端点版本或错继续点不改两端。[抗魔旅行](ongoing-effect-protocol.md#antimagic-field) | [V09](#v09)；无该消费者 |
| G10.1 原子生成及环级 | U→P | 两狼全体预检和生成；选 2 档在 5/7/9 环为 4/6/8 只，6/8 环仍 2。声明任一非法格零成本；已付后被占为 NO_SPAWN，真实抗魔阻断单格另记，不以失败回写冒充已出生。[生成](character-state-and-history.md#summon-turn) | [V10](#v10)；仅世界创建 |
| G10.2 群组先攻与游标 | U→P | 只掷 12+2=14，中途按稳定项及本轮关闭前缀插入；已消费轮次不补发。保存组骰/当前成员，错游标拒绝，恢复不重掷。[群组回合](character-state-and-history.md#summon-turn) | [V10](#v10)；单怪先攻 |
| G10.3 指令、控制与退场 | U→P | 可听口令零 action，友善无账号控制；W1 归零消失、W2 留场，源结束连同暂隐成员收束。若恰为当前成员，保存原骰并结一次关闭余集，迟到旧候选拒绝；成员写失败不留孤立安置。[召唤生命周期](character-state-and-history.md#summon-lifecycle) | [V10](#v10)；无群组生命周期 |

### 日历、疾病和真实物件

| 缺口 | 原→修订 | 合同接点与分支复验 | 当前程序证据 |
|---|---|---|---|
| G11.1 独立自然日、迁移和个人资格 | U→P | 已旅行 8 小时、摄入半份，交接保留账本和游标，只绑定目的严格未来日界；后台不补日或个人秒。空间存续期固定定义及 episode，活动空间改制、移动黎明或重新绑定的请求不能制造同日恢复。日界不改变长休间隔或 Arcane Recovery 个人日档。[日历发生](time-and-calendar.md#calendar-events)、[个人资格](time-and-calendar.md#personal-eligibility) | [V11](#v11)；有独立秒与恢复相邻测试 |
| G11.2 逐小时旅行 | U→P | 真实参加者 9/10 小时末各结一次；A 2→3 后下一次按劣势，不提前冻结。正逢日界先结额外小时；恢复原小时游标及余集，虚报旅时或旧发生不能补骰。[旅行账本](time-and-calendar.md#daily-activity) | [V11](#v11)；仅 DM 计时 |
| G11.3 食水与力竭归因 | U→P | CON+0，六个半份日欠粮刚好 3 日不加、第七日超过才加；热日恰 1 加仑走 DC 15，低于半量零骰，已有力竭再加 2。足量解除恢复限制，未足量长休不清该来源；重复日界不扣第二份库存。[食水账本](time-and-calendar.md#daily-activity) | [V11](#v11)；无摄入、欠量消费者 |
| G12.1 感染迁移与真实暴露 | U→P | d4=2 的 172800 个人秒跨快慢时钟不变，已过 3600 则余 169200。真实暴露免疫/已感染零新潜伏骰，未知接触不冒充免疫；未结豁免不迁叶子。[暴露与迁移](ongoing-effect-protocol.md#疾病进展石化与休息) | [V12](#v12)；无感染 owner |
| G12.2 石化独立原因 | U→P | 两个有效原因保同一暂停余量，解除首个仍冻结，末因解除后同 episode 继续；余量零仅一次 onset。暂停中无补长休疾病骰，已有力竭保留，治愈后解除不恢复。[石化](ongoing-effect-protocol.md#disease-progression) | [V12](#v12)；无疾病暂停 |
| G12.3 同一长休的多病及归因 | U→P | 先固定 HP 恢复资格，再逐感染义务，最后食水减级。Sewer Plague 成功按真实可减 credit 降一级，主体总力竭仍≥1 不治愈；第三种来源不能借“治愈”一并删。恢复已结清治疗与休息队列，不补 HP。[长休与归因](ongoing-effect-protocol.md#多疾病与力竭归因) | [V12](#v12)；无多病后继 |
| G12.4 Sight Rot 的治愈 | U→P | 真实污染饮水 DC 15，失败后 86400 个人秒到 −1，真实长休逐级到 −5 并带自己 BLINDED。合法 Eyebright 剂量防一次恶化，第三剂终结自己的感染/失明；错误剂量、别人的 infection 或同键重放无第二剂效果。[疾病义务](ongoing-effect-protocol.md#多疾病与力竭归因) | [V12](#v12)；无此症状及剂量 |
| G12.5 Cackle Fever 的独立后果 | U→P | 真实发作的豁免失败才有 d10 和发作；长休可选恢复，成功 d6 同减两 DC，例如 13→8，归零治愈。第三次真实恢复失败独立一次 d100 缺陷；治病不结束该缺陷，伪造休息/压力或重复第三次不掷骰。保存 fit、感染和独立缺陷各自余集。[发作与疯狂](ongoing-effect-protocol.md#cackle-fever-发作及独立疯狂) | [V12](#v12)；无发作或独立缺陷 |
| G13.1 实例同调、持有及授予 | U→P | 同调手持且花 3 充能，7→4，五环 10d6、DC 15，位零。短休接触中断、第四件、同种重复、包内或不足充能在相应付款前拒绝；转手保同一余额。[同调](objects-and-magic-items.md#attunement)、[物件授予](spellcasting-extension-contract.md#item-and-stored-grants) | [V13](#v13)；无物件授权 |
| G13.2 末次、毁坏与黎明 | U→P | 1→0 d20=1 化灰，已接受 cast 仍走后缀且未来黎明无恢复；非 1 保零。余额 4 的真实黎明 d6=5 只得 3。保存准确两种发生及消费，后台、转交或重复同日黎明不补骰。[充能与黎明](objects-and-magic-items.md#wand-charges) | [V13](#v13)；无黎明/末次消费者 |
| G13.3 距离和 24 小时 | U→P | 恰 100 尺不计；101 尺 86399 个人秒仍有效，86400 结束；提前回到 100 尺清零，未知距离拒交接。即使物件前台过黎明，冻结同调者不累加离身时长。[分离时间](time-and-calendar.md#item-boundaries) | [V13](#v13)；无同调距离计时 |
| G14.1 宿主、真实互动和有限谓词 | U→P | 真实同次开箱前说对口令则箱开、符文留存；随后另一次开箱没有口令可触发。伪造发声或缺候选真值拒绝，不把未知当 false。[互动](objects-and-magic-items.md#interaction-facts)、[谓词](objects-and-magic-items.md#glyph-predicate) | [V14](#v14)；无物件事件 reader |
| G14.2 铭刻、原点与移动失效 | U→P | 一小时和真实耗材付款，封闭宿主离原点 10 尺有效、15 尺结束，移回不活；中途越界再返回也结束。表面宿主依自己边界，距离证明不完整拒绝移动，销毁/失效先于触发。[铭刻生命周期](objects-and-magic-items.md#glyph-lifecycle) | [V14](#v14)；无真实宿主锚点 |
| G14.3 爆炸与一次触发 | U→P | 20 尺绕角、完整生物集合，共享 5d8=23，失败 23／成功 11；火符文不因此点燃对象。触发后保存唯一收据和未释放余集，重复开箱不生成第二份义务。[爆炸符文](objects-and-magic-items.md#glyph-lifecycle)、[区域](spatial-and-area-contract.md#areas) | [V14](#v14)；无触发/释放链 |
| G14.4 储存授权与集中改写 | U→P | 三环 Glyph 与真实准备的一环 Bless 分别付款，储存合法单目标档；释放使触发者取得 d4 贡献，完整 60 秒，无 holder 或第二次付款。储存 Fireball 以触发者为中心重建完整范围。其他储存效果须有各自准确消费者；坏授权拒绝，真实目标失效不改找替代。回滚保护触发收据和未应用后缀。[储存施法](spellcasting-extension-contract.md#item-and-stored-grants)、[实物向量](objects-and-magic-items.md#object-recovery) | [V14](#v14)；无此授权 |
| G14.5 任意开放条件 | U→U | 仅通过已登记事实和有界词汇；例如任意历史叙述或未登记条件在铭刻接受前拒绝，保存/恢复不把它改写成可执行谓词。[有限词汇边界](objects-and-magic-items.md#glyph-predicate) | [V14](#v14)；无通用执行 |
| G15.1 任意机械神祇援助 | U→U | 已预检治疗/效果仍为 P；有界 NARRATIVE 保存一次真实叙事、可见性和成功冷却，不改变 HP、位或世界状态。脚本、字段补丁或临时未预检机械后果拒绝，等待原叶子保留。[有限裁定边界](ongoing-effect-protocol.md#divine-intervention) | [V15](#v15)；仅现有检定裁定 |
| G16.1 跨空间距离与存续 | F→P | 可比较物理框架证明戒指、实际距离 ≤60 尺才继续，61 尺正常结束；同世界锚点但无变换拒交接，不推断超距。后台个人期限不补算，旧距离或缺闭包零新骰。[空间证明](spatial-and-area-contract.md#spatial-proof)、[羁绊](ongoing-effect-protocol.md#warding-bond) | [V16](#v16)；现行冻结拒绝 |
| G16.2 前后台原子伤害及后继 | F→P | 原 14 火→7 与 relay 7→3 的健康同阶段；前置后台选择时两端未伤，后置专注等待时两端已伤。后台归零或再伤害照真实 owner 及 scope 叶子结算，不开放普通流；第二端写失败共同回滚。[共同提交](world-and-scene-spaces.md#cross-space-apply)、[伤害接点](multi-target-save-damage.md#causal-damage-apply) | [V16](#v16)；无同阶段跨空间写入 |
| G16.3 调度、递归与直接恢复 | F→P | 完整无环图、唯一边和受限继续。已登记的关系重验、区域覆盖、实物触发可分别消费真实同调离身、覆盖变化、Glyph 互动或位移；切焦点、普通后台 MOVE/开箱与旧后台叶子不产生这些事实。环或未知后果首骰前拒绝。恢复仅保当前 scope 叶子及原冻结流，不自动 PASS、不重传 7；之后切回重验原后缀。[受限入口](world-and-scene-spaces.md#causal-scope)、[恢复](world-and-scene-spaces.md#scope-recovery) | [V16](#v16)；无因果域/恢复 |

共 49 项，原缺口为 46 U、3 F。修订后 47 项在所列范围为 P，G14.5 与 G15.1 仍为 U。
这与 16 条原始轨迹的 9 P、6 U、1 F 是不同计数。原始的每条轨迹均保留，未用有限子集
替代原 F；新的跨空间合同仍结算原例要求的后台伤害及真实后果。

<a id="retest-vectors"></a>

## 修订合同的纸面复验

逐行把原轨迹和账本里的变体代入现行合同。保存/故障列区分已提交前缀与失败事务本身；
每个向量都应在对应真实运行切片重复验证，当前结果只是预期和读码证据。

| 样例 | 正常与关键例外的预期 | 非法、重复及过期 | 保存位置与失败边界 |
|---|---|---|---|
| [V01](#v01) | 5 级在场景秒 100 授予，3→2，期限等效于 700；AC 15，10+4=14 等选择，d8=3 得 17 命中。放弃保留灵感；能力、对抗、属性、专注、死亡豁免见 G01.1–3。 | 自授、听不到、已有一骰在授予前拒绝；迟到版本或错授予 ID 不能使用；同键重放无第二颗 d8。 | 保存原 14、唯一叶子及未公布状态。选择事务失败保原灵感及 14，无新公布、附加骰或后果。 |
| [V02](#v02) | 刺剑优势 [6,20]，暴击武器 9+固定 3+特性 21=33 穿刺；同 turn 第二击无特性选项，其他 turn 的合法 OA 可用。 | 非合格武器、已有用量或无证明的 5 尺邻接不能获取特性；伪造/迟到选项无骰；重发只读原用量事实。 | 最终命中已定但未选时保存 attack/turn；失败的接受事务一起回滚本次用量、附加骰及 HP，之前已付成本不变。 |
| [V03](#v03) | 2 环位 1→0，对不死武器 8、光耀 20，合计 28；挥砍抗性时 24。总值 15 被 Shield 提到 AC 19 后改 MISS，零位、零伤害骰。 | 错法术位池、已空/过期余额、非最终命中拒绝；同键不再扣位，不产生 cast/反制窗。 | 选位前恢复原最终命中；已付后只剩伤害后缀。未提交的 APPLY 回滚当次全部分量，若位在同事务接受也回滚。 |
| [V04](#v04) | 16>14 用首 ordinal，第二击仍可用；起始 17 平不建立，挣脱 14 平保留。拖带两位同段；等待时释放不耗动作。 | 无空手、越触及或过期位置拒绝；重复技能决定不能再占 ordinal，释放旧关系不能清其他来源。 | 保存于必选技能或联动 OA；分别恢复原 ordinal 或成对前缀。后端第二主体写失败，两位置和该段 spent 同回滚。 |
| [V05](#v05) | C 先自动反制 B，B 无效果，A 仍有 Bless，三份真实成本各一次，零检定骰。高环、贡献、遮挡、工作中断见 G05.1–4。 | 指向父 A、无真实在施 cast、过期施法见证或被挡路径拒绝；重复响应不扣 B/C 第二次资源。 | 最内层保存原真实叶子；高阶可保存 d20=8、未公布 11；长工作保存第十段窗口。反制结果写失败保已付前缀，不留下仅标记父却未记录子结果的半状态。 |
| [V06](#v06) | 31 火伤：A temp7→0、HP45→21，CON 14<15，专注结束；B Evasion+抗性 7，C 0，D 重复抗性仍 7。对象与空生物集合见 G06.1–2。 | 缺最后一个生物/对象证据或旧版本，首个新骰前拒绝；重复 APPLY 不再扣 HP、点燃或掷 CON。 | 保存共享 31、逐个豁免或点燃前准确余集；最后 HP/点燃写失败回滚全部该 APPLY，先前提交的骰、法术成本保持。 |
| [V07](#v07) | 秒 100 建源至 700，A 可见豁免。B 首次进入速度 30→15，失败 17、HP40→23；同 turn 再进无伤；以后 START 成功 23→11，HP23→12。变体见 G07.1–5。 | 假边界、错 turn 或豁免名单失真拒绝；同一进入重放不伤第二次，错 epoch 不补 START。 | 保存已走边、原触发和伤害后待续，不再进入。故障回滚本事务的发生消费、原骰、伤害与位置/时间变更；已接受边前缀保持。 |
| [V08](#v08) | 狼 11 承 17 回转，同包溢出 6、本体 24→18，装备恢复。形态耗尽不先产生倒地/掉落；真实溢出使本体归零时才处理本体后果。 | 非法形态/CR 零费；合法 0 HP、shapechanger 或不自愿目标豁免成功为已付无效果。错层、版本或重放 packet 不重复扣本体。 | 保存两层健康、融合装备和来源；安置待决后若身体因新边界改变，恢复只装载新计划和已结算包。最后健康/来源写失败无半回转。 |
| [V09](#v09) | Bless 秒 100–160；T1 被 F1/F2 压制、T2 仍有 d4；120 去 F1 不恢复，140 去 F2 恢复，160 结束。F2 晚于 160 移开不复活。完整类别见 G09.1–5。 | 未知来源或覆盖拒绝，不能用名号声明神器；重复移开同一原因不清另一个，旧端点不开放门户。被取消的安置叶子不能提交旧 RETURN。 | 保存两个原因、期限与暂隐安置；再压制或源结束使待返回按 G09.4 收束，恢复无复活或旧计划重放。取消写失败回滚本次 cause、叶子与位置状态，原骰/成本保留。 |
| [V10](#v10) | 同组两狼各 HP11、组先攻 14。W1 受 11 消失，无尸体；W2 留至专注结束。环级、插入、命令见 G10.1–3。 | 最后一格非法付费前拒绝；过期成员命令或无听觉证据不写；重复生成/退场不再掷先攻或关闭两次。 | 保存 W1 已结束、W2 仍在及真实组游标；回读不复活。任一成员、占位或组项写失败无半组。 |
| [V11](#v11) | 第九小时 DC11，A 10 失败 2→3，B 11 成功；第十小时 A 取 [15,9] 中 9，DC12 失败至 4，B 12 成功。食水不足不因普通休息清除。 | 伪造参与/食水、旧账本或旧日界拒绝；重复额外小时不重骰、不重复加级，转移不清旧游标。 | 第十小时与日界同刻时，到点时钟、个人时间前缀、完整 occurrence 和未决义务同事务落库。恢复先结待办强行军，再日界；发生集写失败不留独立时钟，后续失败保先前已提交的时钟与准确余集。[同刻边界](time-and-calendar.md#boundary-order) |
| [V12](#v12) | 潜伏 d4=2=172800 秒；症状一次。生命骰 7→3；HP8 的长休仍为 8，CON11 成功，力竭 1→0 治愈；同次长休不补 HP。其他病/迁移/暂停见 G12.1–5。 | 假暴露、等待八小时冒充真实休息、错感染或过期叶子不结算；重复 rest/onset 不补骰或加级。 | 保存原潜伏、暂停原因、HP 固定阶段及疾病休息余集；当前疾病写失败回滚本次治愈/力竭变化，已真实提交治疗不重算。 |
| [V13](#v13) | 同一已同调实物 7→4，五环 10d6 无角色位；黎明 d6=5 余额到 7；另案末次 d20=1 销毁，未来黎明不能恢复。 | 非同调/未持有、非法花费或错物件版本零骰；改日历定义、黎明秒或 episode 不产生新恢复，重放原 DAWN/LAST_CHARGE 不再骰。 | 到黎明的时钟与完整 occurrence 及待结余集共同保存。若前序边界尚在等待，恢复仅接该后缀和原 DAWN，不补第二次黎明；付款或余额写失败不留单独原骰、余额或已消费标记。[同刻边界](time-and-calendar.md#boundary-order) |
| [V14](#v14) | 对口令箱开不触发；移动 10 尺不终止，15 尺永久结束。爆炸共享 23/11；储存释放不重复支付并使用全持续时长。开放谓词仍 U。 | 假口令、未登记条件、坏宿主/储存收据或过期事件拒绝；已消费铭文不因重开再次触发。 | 保存已结束来源、触发收据和未执行的释放叶子；触发接受事务失败不留单独收据，后续 APPLY 失败只回滚本次释放后果，已接受前缀保留。 |
| [V15](#v15) | Cleric 10，d100=10 成功、11 失败；有限治疗 5，HP8→13（上限20），同逻辑秒无位。成功个人 604800 秒，失败真长休恢复；20 级零骰。 | 非 DM、过期叶子、临时未预检后果拒绝；重复治疗不加 5 第二次。NARRATIVE 可记叙事、消耗真实成功资格，任意机械援助仍 U。 | 保存百分骰、已付动作、WAITING_DM；决定/效果/冷却写失败共同回滚，恢复不重新尝试或代选。 |
| [V16](#v16) | 同空间与有距离证明的双空间均 14→7→3。前后台该 APPLY 同时提交；后台归零、专注、反应和后继走真实因果域，不推进后台时间。 | 无 frame 证据、循环或未经授权的后台 MOVE/旧 flow 入口拒绝；重复边不再传伤，旧参与权限/焦点/CAS 零新骰。 | APPLY 前保存原骰/计划、两端未扣血；APPLY 后保存两端 HP 和精确后置叶子，背景原动作冻结。后台最后写失败两端和同阶段历史一起回滚。 |

数值向量分别由[动作](action-continuation-protocol.md#check-and-attack-vectors)、
[施法](spellcasting-extension-contract.md#spell-vectors)、[日历](time-and-calendar.md#复验向量)、
[实物](objects-and-magic-items.md#object-recovery)及[跨空间](world-and-scene-spaces.md#纸面验证向量)
的权威接口解释。纸面算式中的假定仅用于该向量，运行时须由相应事实 reader 证明。

## 证据与复验边界

本页结论来自逐例语义、相关 SRD 原文、现有设计条款和当前源码的纸面核对。源码中的测试
只用于辨别现有分支，不把其存在或过去结果当作本轮运行证据。未执行 Maven、应用命令、
真实数据库或部署；文档检查只能证明内容/链接/格式，不授予运行或规则发布资格。

程序列区分有限 INTERNAL 场景消费者和当前 SQLite 可移植 `RULE_BOUND_CAMPAIGN`
format 3。后者的原有运行、检查点与直接打开证据仍按[采用边界](adoption-boundaries.md)
判定，不能将本文新设计的业务图算作已经由该文件格式读写。

复验时先固定待验证的合同内容和样例范围，再逐项核对声明、权威输入、阶段/成本、随机、
后果、时间及恢复。内容变化须重审相关结论；不以文档哈希代替实际阅读。实现之后另行增加
定向正常、拒绝、重复/过期、保存直接恢复及事务失败测试，程序支持列只有取得对应证据后才更新。
