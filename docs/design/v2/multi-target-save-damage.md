# v2 多目标豁免伤害公共结算合同

状态：以一个真实机制完成公共接口、时序和独立验收推演；这是设计级验证，尚无本合同的
生产执行器、SQL、文件 codec 或 Host 入口。它不把验收 fixture 算作真实消费者，不授予
完整怪物、法术、模组作者 schema 2 或 archive format 2 的运行资格。
本文遵循[模组接口基线](module-interface-baseline.md)、[机制接入责任清单](mechanism-integration-checklist.md)、
[动作继续协议](action-continuation-protocol.md)、[持续效果协议](ongoing-effect-protocol.md)、
[运行与事务](runtime-and-transactions.md)及[保存与恢复](save-and-recovery.md)。

## 唯一实例、原文和支持范围

实例为 **Hell Hound 的 Fire Breath**。本合同的应用机制标识为 `SAVE_DAMAGE_BATCH_V1`，
具体登记为 `HELL_HOUND_FIRE_BREATH_V1`；这些是新 v2 目标合同标识，不是现有持久化键。
真实内容仍使用模组基线的完整规则身份和 actions 集合条目身份；不由英文显示名寻找内容，
不在本文为尚未登记的目录臆造稳定规则键或作者 JSON 路径。

| 本地 SRD 5.1 | 本实例的承诺 |
|---|---|
| [Hell Hound，p.321–322](../../references/srd-5.1/pages/page-322.md) | Fire Breath 是怪物 action：15-foot cone，区域内每个 creature 作 DC 12 DEX save；失败承受 6d6 fire，成功承受一半；Recharge 5–6。21 是展示平均值，本实例执行选用骰子，不把平均值和骰子结果混用。没有点燃物体、持续火焰、后续 save 或施法成分。 |
| [Actions / Limited Usage，p.259](../../references/srd-5.1/pages/page-259.md) | 使用怪物真实动作与一次可用吐息；使用后不可再用。在之后本人 turn START 掷 d6，5/6 恢复；short/long rest 也恢复。恢复归 turn/rest owner，不在吐息结算或 GET 中补掷。 |
| [Saving Throws，p.83](../../references/srd-5.1/pages/page-083.md) | 用目标自己的 DEX 修正、实际 save 熟练及适用贡献；DC 来自吐息，不套施法 DC；普通 save 没有攻击的自然 1/20 必败/必成或暴击。 |
| [Damage Rolls / Cover，p.96](../../references/srd-5.1/pages/page-096.md) | 同时多目标只掷一次伤害；half/three-quarters cover 的 DEX save 修正为 +2/+5，只取最强，必须由空间与检定 owner 提供。 |
| [Resistance / Vulnerability，p.97](../../references/srd-5.1/pages/page-097.md) | 先其他伤害修正，再抗性，再易伤；同类重复抗性/易伤各只应用一次。免疫必须有真实来源。 |
| [0 HP / Temporary Hit Points，p.98–99](../../references/srd-5.1/pages/page-098.md) | 伤害先消耗 temp HP；剩余影响 HP，0 HP、即死、稳定及承伤后果由健康 owner 处理；不能把 HP delta 当承受伤害。 |
| [Concentration，p.102](../../references/srd-5.1/pages/page-102.md) | 每个独立伤害来源分别检查；CON DC 为 10 或所受伤害的一半取较大者；真实失能或死亡直接结束。 |
| [Cone，p.103](../../references/srd-5.1/pages/page-103.md) | 沿所选方向扩展，宽度等于距原点距离，长度 15 feet；原点是否纳入须明确。本实例选择排除原点，不能顺便假定施加者免疫或友方不受影响。 |
| [Cutting Words，p.13](../../references/srd-5.1/pages/page-013.md) | 真实可适用的伤害骰后反应必须在造成伤害前处理；它可修正吐息的共享伤害 roll，不能错误地用来减 DEX/CON saving throw。 |

本实例的 `save_policy={DEX, FIXED_DC:12, FAIL:FULL, SUCCESS:HALF}`、
`damage_profile={ONE_COMPONENT, FIRE, OTHER_DELIVERY, 6d6, NO_ABILITY_MODIFIER}`、
`area={CONE, LENGTH_FEET:15, EXCLUDE_ORIGIN}`、`use={MONSTER_ACTION, RECHARGE_5_6}`
分别是完整复合原子参数。伤害不是 spell/attack，weapon tags 为空；不能仅因同为 fire
就与其他 delivery、材质/魔法或准确适用 gate 不同的分量乱合并。本实例没有多分量分组。
不能只覆盖 DC 模式而遗留另一种 DC 的字段，或把一半 profile
拼成未登记机制。未知 mode、多个伤害分量、对物体结算、Evasion 等替代 save 结果、
Legendary Resistance、反伤、额外伤害及特殊减伤，只有建立其准确合同与完整消费者后才
可进入支持范围；本实例不默认支持它们，也不能把实际适用者当零贡献过滤。

执行分工为：固定参数、完整目标、骰子、减免、健康和已登记专注结果由服务端自动结算；
DM 仅在服务端合法锥向候选中有限选择。边界体积、遮挡或特殊空间关系确需裁定时，必须
先由空间领域接受有准确场景/对象/事实版本的类型化裁定；本命令不接收“最终目标列表”、
自由文本判定或任意伤害。空间 reader 不能证明完整目标时，本分支关闭。

## 公共类型与唯一消费者

以下是必须交付的语义接口及严格值域，不是已经存在的 Java 类型、HTTP DTO 或文件字节。
每个 union 分支只允许其字段；未知、重复、缺失、null、坏类型和超界字段严格拒绝。
客户端只提供下一节的 `BreathIntent`。下表中的 plan、cut、贡献、骰点和 outcome 都由
服务端生成，不能经请求直接提交，也不提供通用 JSON 效果解释器。

| 类型 / owner | 必要字段与封闭语义 |
|---|---|
| `CreatureRef` / 生物身份 owner | 战役稳定键、`CHARACTER/MONSTER`、该种稳定 UUIDv4 业务键；运行引用另核同一 run/snapshot。归档、离场、0 HP 和死亡不得混为“查不到记录”。 |
| `SaveDamageBatch` / 豁免伤害协调者 | 稳定 `batch_key`、准确协议/机制版本、真实来源、release 事实、完整 `TargetSetProof`、连续 target ordinal、当前固定阶段、完成前缀和未结义务、共享 roll/各 check/packet/健康/专注结果引用、flow/frame/continuation 引用。只协调这些事实，不复制 HP、holder 或动作池。 |
| `BatchSource` / 怪物动作 owner | 本实例恰为 `MONSTER_ACTION`：准确怪物身份、原 stat-block action 条目身份及当前授予依据、combat/turn、一次 action spend、一次 recharge-use spend、release 事件。真实执行使用同一 snapshot 的最终启用条目及来源链。未来具体法术必须另登记真实 cast 来源，不能把本实例冒充施法。 |
| `TargetSetProof` / 空间 owner | 场景及完整 creature roster 版本、源姿态/原点、15-foot cone 的准确方向候选与排除原点选择、全部空间/遮挡/覆盖/裁定事实、逐 candidate 的纳入或排除证明、完整 T 及排序。选中区域与实际目标是不同对象；不能以用户提交的子集代替 T。 |
| `CheckPlan` / 共享检定 planner | 稳定 check 身份、`SAVE`、purpose=`BREATH_DEX/CONCENTRATION_CON`、准确 batch/target/source/阶段、ability、server-owned DC、当前 `CheckBasis`、完整类型化贡献及来源、`ROLL/AUTO_FAILURE`。前者生成有限 roll manifest；后者必须有真实自动失败 condition 证明，不生成虚假 d20。 |
| `CheckBasis` / 检定与贡献 owner | 采样点与依赖事实版本、实际属性修正、适用 save 熟练贡献、固定加减、bonus-die 项、advantage/disadvantage 全集及聚合证明。没有熟练来源不能自动加 PB；多份 advantage/disadvantage 按其核算法合并，二者皆有时互抵为一个 d20。 |
| `CheckRoll` / 共享骰核 | 不可变原 d20、各骰 ordinal/faces/value、selected ordinal、原修正与贡献骰、roll manifest 身份及实际消费顺序。普通模式一个 d20，advantage/disadvantage 两个并选高/低；同点选较小 ordinal。不能事后重写原骰。 |
| `CheckOutcome` / 共享检定 consumer | 恰引用一个真实 roll 或 AUTO_FAILURE 证明，以及已登记掷后贡献/选择，记录最终有效 total 和 `SUCCESS/FAILURE`；普通判定 `total >= DC`。原 roll、后置贡献和最终 outcome 分开保存；没有可适用后置机制时可同事务完成。 |
| `SharedDamageRoll` / 共享伤害骰核 | 准确 batch/release/source、一个 6d6 原 manifest 和六个不可变原骰、raw total，及另存的已消费共享 roll 修正；一次 roll 供所有目标引用，不复制成五次独立伤害骰。 |
| `DamagePacket` / 伤害 owner | 稳定 packet 身份=`(batch_key,target_ordinal)`、准确 shared roll/source、target 的唯一 save outcome、fire 分量、成功/失败比例、共享和本目标修正、完整免疫/抗性/易伤证明、`DamageApplyCut`、实际伤害及真实健康/伤害后义务引用。不能由 HP 减少量反造 packet 或用目标数拆分伤害。 |
| `DamageApplyCut` / 批次与真实领域 owner | 全体目标在最终 APPLY 边界的权威 vitals/health、temp HP、减免/贡献/condition、holder、引用闭包及依赖版本。先形成完整不可变计划，再一次消费所有目标；它是本次事实的依据，不是第二份可写当前状态。 |
| `HealthPlan` / 唯一健康 owner | 完整 pre/post temp HP/HP/max HP/生命状态/稳定与死亡计数、实际吸收和 overflow、condition/onset/掉落及真实伤害触发。计划无 RNG、无写入，consumer 核对同一 cut 后一次应用。 |
| `ConcentrationDamageObligation` / 专注 owner | 身份=`(holder acquisition fact, packet identity)`，实际伤害、固定 DC、`NOT_REQUIRED/FORCED_ENDED/CHECK_REQUIRED` 及证明，check/result 或准确强制结束引用；本批一个来源对每个受伤 holder 至多一次，不能按骰数、temp/HP 两段或目标数重复检查。 |

`prepareChecks`、`prepareDamageBatch`、`prepareHealth`、`prepareConcentration` 都是只读、
确定性的纯计划：只消费显式固定规则、当前权威事实和已经接受的结果，不抽 RNG、不查
主机时间、不写库、不扣资源。共享骰核在整体预检通过后按 manifest 唯一抽骰；真实
命令协调者调用各 owner 的 consumer，在调用方事务中应用计划并保存 typed 历史。
“有 CheckPlan/DamagePacket 对象”或只有测试直接调用 planner 不能证明这条真实链已实现。

真实链路的采用目标为：怪物动作来源/有限 area 选择 → 完整目标 reader → batch planner →
共享伤害骰核及共享检定核 → damage/health consumer → concentration/effect consumer →
batch/flow 终结 → 可读接受结果。每条箭头都需实际生产调用和读回证据；目前均不得据本文
声明已完成。公共接口可以复用于以后已登记的机制；不因此开放新的来源或参数含义。

## 输入、完整目标与数值边界

`BreathIntent` 必需且只含：准确 run/snapshot/campaign、请求幂等键、服务端可规范比较的
操作身份、怪物业务键、当前 combat/turn、准确 action 条目身份、服务端有限
`area_choice_key`，以及操作登记的完整预期版本集合。它不能提交 DC、damage dice、
target ordinal、最终 T、HP、modifier、effect winner、RNG seed、阶段或新版本值。
选择的 area 候选自身绑定源姿态及场景事实版本；旧候选不能跟随当前源位置重新解释。

空间 owner 枚举本次场景的**全部** creature，包含敌友、PC/NPC、怪物、0 HP 与不在战斗
initiative 中但实际位于场景的 creature；排除只依据真实空间或主体资格，不因未选中、
火免疫、没有 token、隐藏于某个 UI 列表或非敌人而漏掉。每个 candidate 恰有一个完整
membership 结论，cover 单独输出供 save 使用。源原点排除由 area 规则证明，源本身
是否因其他占据空间纳入仍归空间合同，不能用“自我免疫”代替几何。

本实例不建立完整网格/体积引擎：空间 reader 必须声明正在使用的测量、体积交集和遮挡
政策，绑定它的准确应用版本；缺位置、未知边界或未登记体积策略在首 RNG 前拒绝。
有限 DM 裁定若被空间合同允许，必须覆盖所有有歧义 candidate、引用真实有限对象并先
成为权威事实；不能在吐息请求里放一个 `include=true` 绕过 reader。golden trace 使用
无边界/遮挡歧义且已完整核验的空间事实，不把这些输入称为已实现的几何服务。

release 时固定 area 意图、完整 T、身份/ordinal 和当时 membership 证明；不在每目标
循环中重新扫描或换人。后续反应不能自动扩充 T。SAVE/APPLY 的可变贡献仍在各自真实
阶段重读；不能在 DECLARE 永久冻结 HP、cover、抗性或 holder。原目标仍是 creature
但已经 0 HP 时必须走真实健康分支；反应使它确证不再是 creature 时，本范围只允许登记的
`TARGET_CEASED_TO_BE_CREATURE` 无 packet 终态，保留原目标和原因、不另找替代。
物理记录丢失或来源不一致是完整性故障；缺上述失效 consumer 的可达分支在根接纳前阻断。

| 工程支持界 | 规则 |
|---|---|
| T 为 0—64 个不同 creature | 超限拒绝完整命令，不截前 64 个。T 为空可接受 `NO_TARGETS`：真实 action/recharge-use 各消费一次，不产生 shared dice、check 或 packet；不是“机制未实现”的空成功。 |
| target 顺序 | `CHARACTER` 先于 `MONSTER`，同种按稳定 UUID 的无符号网络 16 字节升序；ordinal 从 0 连续。和 SQL 锁序、initiative 反应 priority 各司其职，不依 DB/请求顺序。 |
| check 贡献 | 每 check 最多 32 个类型化贡献，其中最多 8 个 bonus dice；每 die 的 faces/数量/参数须来自已支持规则，不能因在数量界内接受任意 dice 表达式。 |
| damage 贡献 | 本实例一个 fire 分量；每目标最多 32 个有来源的贡献。共享修正只占一次真实语义节点，不复制成 N 个反应成本。 |
| 单命令骰请求 | 最多 4096 个逻辑骰结果；精确 manifest 及所有可达阶段上界须在首 RNG 前证明，反应/choice 另服从 flow 更小上限及父后缀预留。这不是底层随机源调用次数，拒绝采样等由骰核另行保证有界执行。 |
| 整数 | 计划用精确有符号 long，禁止浮点及溢出；所有输入/中间结果范围在 RNG 前证明。本文伤害除法每步向下取整是项目采用的整数合同，不能冒称 p.97 明文规定了通用舍入。 |

这些是选定的工程验证边界，不是 SRD 目标上限或已完成压测。合法值的准确编码、字段
字节界和容量实测仍是采用材料；本表不能替代保存的 64 MiB/结构/预算证明。

## 伤害数学、共同切面与专注

本实例固定以下计算顺序。设共享原骰和为 R，共享伤害 roll 的已登记减项之和为 K。
共享有效值 `S=max(0,R-K)`；Cutting Words 反应减的是这一个 roll，因此所有目标引用 S，
不能只给一个目标减、对每目标再骰一次，或同时减共享值和 packet。

对目标 i：失败为 `B_i=S`，成功为 `B_i=floor(S/2)`；再按具体类型登记的顺序应用本目标
其他修正并钳为非负 `M_i`。本范围未登记的修正顺序阻断，不按数据库行先后来减。
fire 免疫时实际伤害 `D_i=0`；否则先在有适用抗性时 `floor(M_i/2)`，再在有适用易伤时
乘 2，得到 `D_i`。重复免疫/抗性/易伤保留全部证明，各种语义只应用一次；抵抗和易伤
并存也不先抵消为“没有二者”，必须按上述顺序执行。

健康 owner 用同一 APPLY cut 中的 `temp_i`、`hp_i`、`max_i` 计算：
`absorbed_i=min(temp_i,D_i)`，`remaining_i=D_i-absorbed_i`，
`temp'_i=temp_i-absorbed_i`，`hp'_i=max(0,hp_i-remaining_i)`；真实 overflow、即死、
0 HP 既有状态、死亡失败/稳定变化及不同生物的死亡政策继续由健康 owner 完整规划。
这里只是伤害，不作治疗，不重发 temp HP，不把所有 0 HP 怪物通用改成 PC 死亡豁免。
原文的近战击倒选择不适用于吐息，不能开放 nonlethal 开关。

**共同切面是对同时生效作出的工程时序选择，不是 SRD 额外规定的遍历顺序。**

1. 所有 APPLY 前的真实反应和必需选择先结清。每个 DEX check 在自己的合法掷前点采样
   当前贡献；已产生 roll/outcome 保留。未发生的 save 不能套 release 时冻结的旧贡献。
2. 全部 save 终结后，在唯一 `DAMAGE_APPLY` 边界重读完整 T 的当前减免/健康/holder/效果
   闭包，形成一份 `DamageApplyCut`。先算出**全体** packets 与 HealthPlans，再应用全体
   健康变化；不能处理 A 后先结束 A 的专注，再用变化后的抗性给 B 算本次伤害。
3. 全体伤害健康事实成立后，健康/专注/effect owner 先归并并消费本批全部确定的失能/死亡
   及其强制 holder 结束；同阶段结束一并发生，再重求持续贡献和有限后继。保留 source、
   application、原因及 callback 的真实状态；用持续效果协议的完整闭包，不只清 holder。
4. 强制结束及其当前必需后继结清后，确定仍在集中且 `D_i>0` 的 holder。DC 为
   `max(10,floor(D_i/2))`；用 D 而非 HP delta、overflow、R、S 或 B。temp HP 全吸收仍需
   检查，D=0 不产生无意义 CON check，已强制结束者不再抽 CON d20。
5. 以此时的统一 `CONCENTRATION_CHECK_CUT` 形成全部所需 CON CheckPlans，再按 target
   ordinal 执行。共同 cut 已不包含第 3 步结束的来源；本批一个 CON 失败不在另一个本批
   CON check 之间临时撤掉其 cut 内贡献。全部这些 outcomes 确定后，再批量结束失败
   holders 及其完整效果子图。新产生的独立伤害是新 packet/source/义务，不能折回本批 D。
6. 全部真实后继终结才终结 batch/flow。存在 callback 反应或选择时可保存已完成的全体
   健康/结束前缀和准确余集，沿唯一 flow 继续；不能回到目标循环重伤害一次。

本实例只接纳已登记并能保持该切面语义的 check 贡献。适用的骰后 save/CON 选择或反应，
必须有准确 CheckPlan、时点、choice/子动作、原结果保留和对剩余 cut 的政策；本合同
没有开放任意插队修改。缺这类消费者就阻断相关根，不能先接受再默认不使用。
持续效果带来的 HP/max HP、条件/onset、掉持物、结束回调、压制原因解除及 winner 变化
均归真实 owner。未知订阅者或无法证明有限的后果在根完整预检中拒绝。

## 固定阶段、release 与唯一动作流

本机制的唯一新增执行 frame kind 为 **`SAVE_DAMAGE`**，owner_ref 恰指该
`SaveDamageBatch`。怪物吐息以 `ROOT` 建立它，来源为真实 `MONSTER_ACTION`；没有攻击骰、
cast、slot 或第二个 action。未来已登记的施法效果可将准确 batch 作 `CONTINUATION`
孩子，复用原施法成本，但本合同不自动支持任何法术，也不开放任意 producer。

| 固定领域阶段 / checkpoint | 准确已发生事实与继续责任 |
|---|---|
| `ADMIT_RELEASE` | 完整预检全部规则、目标、依赖/frontier、成本、版本、预算后，接受 action/recharge-use 各一次并记录 release。release 前源必须有真实动作和可用能力、非失能并满足自身限制；release 后的单纯失能/死亡不取消已经释放的吐息。改变已释放伤害须有准确机制，不能因 source 当前不能行动而静默取消。 |
| `ROLL_SHARED_DAMAGE` | T 非空时一次抽 6d6，保存原 manifest/roll。到动作协议既有 `DAMAGE_ROLLED_BEFORE_APPLY`，绑定准确 shared roll；尚无本批 HP/CON 结果。 |
| `RESOLVE_SHARED_ROLL_WINDOW` | 完整 reader 按动作协议产生所有真实 Cutting Words 等候选；有候选就 WAITING_WINDOW，并保存 raw/窗口/预留。真实反应孩子消费自己的 reaction/资源/骰，贡献引用 shared roll。关闭窗口后准确 continuation 合成 S 一次；不重掷 R、不提前套各目标减免。 |
| `TARGET_SAVE` | 按 ordinal 完成每个 DEX CheckPlan/roll/outcome，保存准确已完成前缀。需要已登记 choice 时用同一 flow 的 `DOMAIN_CHOICE`；没有则同事务连续处理，不强制每目标一个请求。原骰不可修改，当前未掷 check 用当前合法依据。 |
| `DAMAGE_APPLY` | 所有 pre-apply 义务完成且完整重验通过；产生共同 cut、所有 packet/HealthPlan 并一起应用。此阶段内禁止逐目标提交或等待用户。 |
| `FORCED_CONCENTRATION_END` | 批量处理本批失能/死亡与强制 holder/effect 结束。若真实后继需暂停，普通 `ONGOING_EFFECT_TRANSITION` 子帧引用准确 transition，并返回本阶段的准确未决 obligation；健康前缀已真实成立。 |
| `CONCENTRATION_CHECKS` | 所有强制闭包结清后生成统一 CON cut 和准确 check 余集；沿共享核一次执行，结果固定后批量消费失败结束及其后继。只允许本阶段完整登记的操作，不把“可随时结束专注”当作破坏 cut 的并行入口。 |
| `AFTER_BATCH` → `COMPLETE` | 全部 packets、健康/专注及当前必需效果义务终态后，才提供准确 `READY_TRIGGER_AFTER` 等已登记 after 触发；真实孩子终结后收束 batch/flow。原吐息不是施法，不能产生 Counterspell 或 Shield 的攻击命中窗口。 |

continuation 必须指向准确 batch/frame/checkpoint、上述阶段及当前 target/obligation
ordinal；这是封闭的应用枚举，不由客户端拼字符串定位。共享 roll 窗口使用动作协议既有
`ContinueIntent`；窗口 CLOSED 尚未 CONSUMED 时仍禁止普通写入。父当前仅能推进尚未发生
后缀，先消费孩子的真实贡献再重验。反应 opportunity 的 initiative priority 沿公共协议，
并不改本批 target/RNG 顺序。普通 transition/choice 孩子不增加反应深度。

没有候选和 choice 且所有后继可证明时，一次短事务可以从 release 贯穿 COMPLETE。
有等待则提交真实前缀、唯一叶子和有限收束预留；等待期间不持事务/锁，也不能另开伤害流。
尚待未来 turn START 的 recharge、稳定持续效果或原有专注本身不占住 flow；吐息不推进
游戏时钟、不完成怪物 turn、不产生玩家步骤 N+1，也不替 turn/rest owner 恢复能力。

## RNG、完整预检与事务故障

无交互正常路径的骰消费序为：共享 6d6 按 die ordinal → 按 target ordinal 的 DEX checks
（每 check 的 d20 ordinal 在前，bonus dice 按其稳定条目身份/来源事实排序）→ 同序且真正
需要的 CON checks。AUTO_FAILURE 仅记录证明，不为保持固定长度而浪费 d20。
有反应/choice 时，当前真实孩子在其到达点消费自己 manifest；返回只消费剩余后缀。
“确定性”指同一规则/权威事实/决定及骰流得到同一结果；不承诺跨回滚复用一个 seed，
不让客户端决定 RNG，也不在保存/恢复/幂等重放时抽骰。

接纳与每次继续按以下次序完成，不能边读目标边写伤害：

1. 严格解析有限输入；进程门 → 运行连接/永久数据库守卫 → 既定层次完整锁闭包。
2. 核准确操作身份与幂等事实；精确旧请求直接返回原不可变接受结果，同键异意图冲突。
3. 核当前执行资格、准确 snapshot/flow/叶子/阶段、全部依赖版本；新根核 action/recharge
   来源、启用状态和完整 area/T，继续核真实已接受前缀及剩余 target/义务。
4. 完整读取固定规则、全部对象和实际/可达反应、check、减免、健康、holder/效果/历史
   闭包，验证每分支 consumer、合法失效结果和有限 frontier；最后一个对象也必须通过。
5. 构造完整纯计划及最坏 RNG/结构/字节/版本/历史增长，证明变化后可完整保存，且所有
   已接受必需后缀仍有持久收束额度；不能借 FINAL/首次修复预留给普通增长。
6. 此后才 RNG/成本/业务写入；在同一调用方事务中保存本命令全部状态、typed 正文、根
   事件、flow 前缀、各真实变化聚合各一次版本推进、预算/预留及唯一接受结果，实际读回。
   Repository 不提交、回滚或另开写连接；提交成功后才返回本次接受事实。

锁闭包包含未受伤但其抵抗/专注/抑制关系或历史被引用/改变的对象；不同锁层内按准确主键
顺序取得，不能把 target/RNG 顺序当锁序或以 SQL ORDER BY 证明已锁完。
每次继续重新核当前权威版本；父 opening 的 CAS 不授权后继。存储投影不能省略固定
规则引用或在请求中临时从来源库选新版本。

| 可观测失败位置 | 必须保留的事实 |
|---|---|
| 完整预检失败，包括末尾目标坏版本/未知 effect/预算不足 | 本命令零游戏 RNG、零成本、零业务写入/事件/版本推进；新根没有合法目标前缀。 |
| 无等待一次事务中，抽骰后任意 packet/HP/历史/版本/结果写入失败且 rollback 明确成功 | 全体本次目标、action/recharge-use、audit、效果、flow、事件尾与接受结果一起回滚；不要求倒退随机源。 |
| release/raw/窗口已在先前事务提交，后继 APPLY 事务失败且 rollback 明确成功 | 只回滚本次尚未提交的后缀；先前 release、已付成本、raw、已接受反应/决定及原窗口状态原样保留。本次不能留下部分目标 HP，也不能把整条动作流说成已回滚。 |
| 全体 HP 已提交，当前效果义务尚待继续 | 它们是合法已提交前缀；后续失败不撤销伤害、不重新应用 packet，只保留准确待结余集和预留。 |
| commit 成功后响应失败 | 保留全部接受事实；同键同意图只重放原结果，不重掷/扣资源/继续。 |
| commit 未知或 rollback 失败 | 隔离连接并使执行资格失效；按原操作身份在共同守卫下只读查证完整闭包。不换键重试，不把空查询当回滚，不自动继续或释放额度。 |

每个接受结果包含原操作、根事件、batch/flow、该次阶段、成本/roll/check/packet/真实
结果引用和当时 `WAITING/COMPLETE/NO_TARGETS`；它永远不改为后来状态。当前只读 view
另报当前叶子、允许操作、全部目标可读结果/来源、当前版本与运行阻断原因。重放旧 WAITING
不重新打开窗口，GET 不掷 recharge、不补伤害或结束效果。

## 状态、历史、版本、保存与清理

以下“文件落点”是格式 2 的类型化分区责任，不宣称最终 JSON 字段字节已激活；实施须
给每个字段实际路径和独立向量，不能借用旧内部 DRAFT 投影或忽略目录作为正式依赖。

| 业务事实 / 唯一状态与版本 owner | 历史及文件/直接恢复落点 |
|---|---|
| batch、完整 T/ordinal、release、阶段/前缀、所有结果/义务引用 / batch 聚合 | batch 分区及完整 typed 命令正文；保留原稳定键、原来源/空间证明和真实阶段，不恢复为“重新开始吐息”。 |
| action spend、recharge 可用性/最近消费与恢复事实 / 怪物动作及 turn/rest 资源聚合 | 怪物当前资源分区及全历史；恢复原可用/已用状态，不在恢复时补 turn START 掷骰或短休。 |
| 每 check 的 basis、roll/自动失败证明、选择/贡献与 outcome / 共享检定事实，引用父 batch 或专注 owner | 检定及其真实父正文；原始骰、最终判定、准确 cause/贡献必须齐全，不能从新能力值反算或只存 total。 |
| shared roll、修正及 packet / 伤害事实，引用 batch | 完整伤害分区/历史；所有目标引用同一原 roll，保留各自 save、cut、减免证明、实际伤害和应用身份，不能从 HP delta 重建。 |
| temp HP、HP/max HP、健康、0 HP/死亡/稳定、condition/onset 和掉落 / 各真实健康、条件、物品聚合 | 初始/当前状态仍分别保存，事件正文引用完整前后事实与主体；恢复直接写当前状态，不能从初始卡或事件重放重建。只吸收 temp HP 也真实推进其 owner；火免疫且无其他状态变化的目标不虚增 vitals 版本。 |
| holder、伤害集中义务/CON cut、强制或失败结束、来源/application/原因/回调 / 专注及具体效果聚合 | 专注/持续效果分区及完整历史；源结束与待 callback 可同时成立，恢复 ENDED 不复活。未受本批直接伤害的效果目标也要保存完整引用。 |
| frame、checkpoint、窗口、机会、choice、transition/返回点 / 唯一 flow 与领域 owner | 动作流/持续效果分区；保存整个活动祖先链和因历史引用需保留的终结对象，不只保存 top 或 WAITING 标志。 |
| 根事件与全部 typed 正文 / 战役事件尾 | 每命令一个全局根，子事实使用稳定 `(event_sequence, ordinal)`；恢复原 sequence/尾，不重编号、不补造 NOTE，不删历史只保 UI 最近记录。 |
| run/snapshot、物理行 ID、row_version/CAS、请求键/接受收据、进程资格、N/W、预算/预留控制 / 本地运行协调者 | 明确排除于可移植业务文件。新运行重建映射及从 0 开始的本地聚合版本、当前完整收束预留；不恢复旧请求的执行权利。准确规则组合绑定按公共 rules 外壳重核。 |

一个命令内同一真实聚合只推进一次版本；读依赖可以必须有 CAS 而不加版。CHECK/DAMAGE
不可变事实和 batch 的阶段指针不另造与真实健康/效果状态漂移的版本源。怪物/角色身份、
已结束过程及原 release 保留历史引用；普通业务不能按“死亡/离场”物理删除它们。

CheckBasis、TargetSetProof、APPLY/CON cut 在文件中保存的是当时真实事实/数值和可移植
事件引用；其中用于本地校验的 run/snapshot/row_version 等 token 仍排除，新本地验证
映射重新建立。不能从恢复后的当前 HP/效果反算旧 cut，或携带旧 CAS 授权继续。

严格恢复 reader 独立核验：同战役完整身份/准确规则组合、真实 source/action/recharge
消费、T 的唯一连续顺序、shared roll 的六个骰及修正、每个 check 的 basis/合法结果、
packet/健康数学、共同 cut、holder acquisition 与每来源一次义务、效果结束闭包、阶段
前缀与唯一叶子、所有 typed 历史/来源引用，以及已消费/未消费余集。保存证明必须逐字段
覆盖源→文件、文件→恢复投影和真实数据库读回；writer/reader 自洽往返不够。
缺分区、未知类型、无健康结果却标已应用或缺一个未受伤的效果目标都拒绝，不能填空数组。

直接恢复原样写入业务状态、完整历史与 pending，不抽骰、不补 check/HP、不开新窗口、
不 PASS、不结束 holder、不执行 end callback、不推进时间/turn/步骤。恢复的新运行完整
初始化提交并发布后，后续新意图使用新本地身份/版本才可继续原准确叶子；重启残留须先
经救援 END，不直接激活旧执行器。

技术 END 保存任一真实阶段，包括“已 release、骰已掷而未伤害”和“全体 HP 已应用、
效果/反应尚待继续”；不把它们自动完成或退款。A 提交 FINAL 后，B 独立重读实际字节并
验证恢复等价，再按完整引用图原子清理：flow/window/choice/transition 与义务引用 →
领域当前/历史中的 check/packet/batch/source 交叉引用 → 其真实依赖叶子与运行聚合 →
工作集/快照和本地预留。实际 SQL 顺序必须由全部真实入向引用决定，本表不是删表脚本；
未盘点闭包则 B 关闭。任一点失败回滚整个 B，保留完整工作集；永久身份/收据/结束事实/
档案继续按保存合同保留。清理不构成业务专注结束或吐息伤害。

容量按真实图计费：T=64 时最多一 shared roll、64 个 DEX 结果、64 个 packet/健康结果、
64 个本来源集中义务；CON 只给实际需要者，但必须预留其最大可达数量及完整效果子图、
反应/choice、typed 历史和终结后缀。来源结束可能影响 T 之外的 applications，不能用
64 代替效果闭包界。共享 roll、健康和专注节点按真实身份 union，不能父子重复计费或先
释放父预留再申请孩子；未来 recharge/新 turn/新吐息另准入，不预留无限未来历史。

## 独立 golden trace

下列数值是手工推导的实施 oracle，**不是已运行测试或生产可用证明**。它分别提供原始
权威输入、骰流及逐项期望；不得调用待测 planner/encoder 生成期望再与自身比较。

空间输入：源 H 为一只可行动的 Hell Hound，其 turn action 与吐息各可用一次；选择
服务端锥向候选 Q，原点为 H，长 15 feet、排除原点。场景完整 roster 为 H、A、D、E、B、C、F。
A/D/E 是角色，B 是另一只 Hell Hound，C 是普通 Mummy，均使用当前真实 Medium 体型；
F 明确位于 cone 之外。空间证明将 A/D/E/B/C 纳入，H/F 排除，无 cover、遮挡、边界或
体积歧义。采用 feet 坐标，锥向为 +X，原点 (0,0,2.5)；本空间 fixture 的 Medium 占位
为 5×5 feet，Z 区间为 0—5，H 的占位中心 (-2.5,0)，A/D/E/B/C 的中心依次为
(2.5,0)、(7.5,-2.5)、(7.5,2.5)、(12.5,-5)、(12.5,5)，F 为 (22.5,0)。空间 owner
已接受“占位与锥体有非零体积交集即纳入”的本次类型化空间政策和完整 membership 证明；
源占位在 X≤0，F 在 X≥20，均不纳入。这个占位/交集政策是明确的工程 fixture 前提，
不是声称 SRD 规定真实 Medium 身体都是 5-foot cube。不能因 B 火免疫或 C 是敌方而漏掉。
这里验证完整空间结论的消费；真实测量/几何政策及其 reader 另须独立验收，不能用该输入
倒证已经实现通用几何引擎。

各角色稳定 UUID 的末段依次为 A=`000000000001`、D=`000000000002`、E=`000000000003`，
其余 UUID 字节相同且满足 UUIDv4；B/C 同种内按键升序。因此 T 固定为 **A、D、E、B、C**，
ordinal 为 0—4。以下基础修正包含实际适用的熟练贡献，没有 bonus dice、advantage、
disadvantage、反应候选或 check 选择；所有后继 consumer 在此 oracle 的假定输入中齐全。

| 目标 | 当前 HP/max HP；temp HP | DEX save 修正 | 防御和真实专注 |
|---|---|---|---|
| A | 20/20；0 | +2 | E 正在集中维持对 A 的 Protection from Energy，选 fire；A 自己无 holder。 |
| D | 18/18；30 | +0 | D 自己集中维持 Protection from Energy，选 cold；对 fire 无减免。CON save 修正 +0。 |
| E | 10/30；0 | +0 | 集中维持上述对 A 的 fire 保护；对自身 fire 无减免。 |
| B | 45/45；0 | +1 | Hell Hound 的 fire immunity；无 holder。 |
| C | 58/58；0 | −1 | [Mummy，p.333](../../references/srd-5.1/pages/page-333.md) 的 fire vulnerability；无 holder。 |

两份 Protection from Energy 按 [p.173](../../references/srd-5.1/pages/page-173.md)为不同
真实 source/application/holder，原期限仍有效，没有压制/重叠或其他结束义务。
这只借用准确既有法术语义构造依赖实例，不声明本项已经实现其施法、效果或保存消费者。

无交互骰流按上述阶段为：`6d6=[6,5,4,3,3,2]`，然后 DEX d20 依次
`A=10,D=9,E=10,B=1,C=2`，最后只有 `D.CON=11`。合计 **12 个逻辑骰结果**。
R=S=23，DC=12；A total=12 成功，其余依次 9/10/2/1 均失败。B 的免疫不删除其真实 save。

| ordinal / 目标 | save 后 B | 免疫/抗性/易伤后 D | temp HP 后值 | HP 后值 | 伤害后义务 |
|---|---:|---:|---:|---:|---|
| 0 / A | floor(23/2)=11 | floor(11/2)=5 | 0 | 15 | 无 holder，无 CON check。 |
| 1 / D | 23 | 23 | 7 | 18 | temp HP 吸收全部 23，仍产生 DC=max(10,floor(23/2))=11 的 CON check。 |
| 2 / E | 23 | 23 | 0 | 0 | 原 HP10，余伤13 < max30，不即死；形成 HEALTH_ZERO 昏迷/失能及真实 PRONE/掉落计划，直接失去 holder，不抽 CON。 |
| 3 / B | 23 | 0 | 0 | 45 | 免疫证明保留，无伤害集中义务或 HP 版本推进。 |
| 4 / C | 23 | 46 | 0 | 12 | 火易伤一次，无 holder。 |

先得到并应用这五个共同 cut 计划，再结束 E 的 holder/fire 保护及 A 的 application。
A 本次实际伤害仍为 **5**，随后已经没有该 fire 抗性；不得因 E 排在 A 前后改变结果。
强制结束闭包后只有 D 需要 CON；11+0=11 成功，保留 cold holder/source。H 的 action 与
recharge-use 各消费一次，能力当前不可用；没有 recharge d6、额外动作、时间/turn 完成或
玩家步。全体 packet 均引用同一个六骰 audit；实际伤害和为 97，HP 总减少仅为 61，
两者差来自 D 的 temp HP23 和 E 超出当前 HP的13，不能用后者推导前者。

版本期望：正常无等待一个命令仅一个根事件；H 资源、batch/flow、A/D/E/C 的真实变化
聚合、E 的 holder/source/application 结束和相关健康/条件/掉落 owner 各按其实际变化
推进一次；D 即使 HP 不变也有 temp HP 变化。读依赖无变化不加版，B 不凭接受一次 save
虚增 vitals 版。完整 typed audits/history 与唯一 accepted 结果同事务存在。

反应停点的独立变体新增角色 Z：一个 level 3 College of Lore bard，位于锥外的
(-10,0,2.5)，使用 d6 Bardic Inspiration。完整场景 roster 因而改为 H、A、D、E、B、C、F、Z；
空间 owner 重新生成并核验新的 roster/空间版本和 Q 的完整证明，Z 被准确排除，T 仍为
A、D、E、B、C，其他输入不变。源 H 在 Z 可见的 60 feet 内、能听到 Z 且无 charmed
immunity，Z 有未消费 reaction/一次 Bardic Inspiration；不能凭空增加一个 proof 之外的 actor。
raw 23 后在 `DAMAGE_ROLLED_BEFORE_APPLY` 暂停，尚无本批 HP；该 bard 明确选择 Cutting Words，
其真实 inspiration die 掷出 4，消费 reaction/一次资源，贡献引用这一 shared roll。
S=19；相同 save 骰下 A 为 floor(19/2)=9 再抗性为4，D/E 为19，B为0，C为38。
于是 A HP16，D temp11/HP18，E HP0且余9<30，B HP45，C HP20；D 的 CON DC变为10。
只新增该真实反应的一个逻辑骰结果，共13个；不对五个目标各消费一次 bard 资源。
源 H 若在已释放后的合法孩子中失能，原 shared roll 与 release 保留；单纯失能不取消这19。
未实现该真实反应 reader/贡献/消费/恢复时，这个变体及其可能适用根保持关闭。

## 负例、故障与独立恢复验收

以下均是实施必须实际观测的预期；当前文档核对不代替测试、真实 SQL/权限或文件读回。

| 场景 | 独立验收要求 |
|---|---|
| 客户端指定目标子集、骰点/DC/修正、重复或跨 run 目标、旧 area 版本 | 严格拒绝；服务端完整 T 仍含 B/C，零游戏 RNG/成本/写入。 |
| 第 64 个目标坏规则/来源/版本，或第 65 个真实目标 | 首 RNG 前整根拒绝，不留前 63 个目标/资源前缀，不截断。 |
| 未知/禁用 action、错误 profile/字段组合、缺 health/effect/反应 reader | 失败关闭；不能空结果或默认无候选。合法 NO_TARGETS 必须由完整空间证明区分。 |
| 重复抗性、抗性与易伤并存、奇数除法、共享减项大于R | 例：23成功→11→抗性5→易伤10；两份抗性仍5；R23−K24钳0，所有D为0且无CON。免疫目标仍保完整来源和save。 |
| 自动失败、adv/disadv、自然1/20 | Paralyzed/Petrified/Stunned/Unconscious有真实DEX自动失败证明，零d20；单独Incapacitated没有这条规则。Restrained为DEX劣势，Prone不据此改save。adv/disadv抵消只一d20；总值到DC才成功，不能套攻击critical规则；未支持Evasion等实际分支阻断。 |
| 全体伤害同时、E排序更换、初始生命边界 | 只要同一cut，A仍5；不能因实现for循环先结束E而改成11。零HP承伤、即死、monster死亡政策、held-drop/condition消费必须由真实health计划验证。 |
| temp吸尽、免疫、多个集中者 | D仍DC11；B D0不查；E失能零CON。先全部强制结束，再统一CON cut；两个CON失败结果批量结束，后一个不因前一个失败临时丢失本cut贡献。 |
| SAVE前反应改变贡献、APPLY前减免变化 | 已掷结果保留；未掷save采样当前依据，最终减免重读APPLY cut。原release不能冻结旧抗性/HP，也不能把后来的修正倒改原骰。 |
| Cutting Words / Ready / 非施法 | 原raw不改，shared贡献只消费一次传播全T；不能减save total；Ready仅在准确after触发，不能抢在吐息伤害前；不产生Shield/Counterspell假窗口。 |
| 新根无等待事务在最后目标HP、最后效果结束、最后历史或accepted写入失败 | rollback明确成功时整个本命令回滚，全部资源/目标/版本/事件尾/结果不变；骰源无需倒退。每个边界分别注入故障。 |
| raw窗口已提交后APPLY失败 | 原raw/资源/窗口/已接受反应保持；本次全部目标零新HP变化、无新packet/结果，原请求状态可只读查看/保存；不能重开raw或退款。 |
| stale继续、同键同意图、同键异意图、响应失败/提交未知 | stale零RNG/写入；原accepted精确重放；异意图冲突；提交成功事实保留；未知阻断并只读查证，不换键、自动重试或自动继续。 |
| 中途保存 S0 | 使用上述Cutting Words变体的release/raw23/OPEN窗口：action/recharge已用，全体原HP/temp/holder不变，无save/packet；恢复相同图与原骰，无新RNG/成本/PASS。无候选正常路径不制造这个停点。 |
| 中途保存 S1 | 同一反应变体中，孩子真实结果4已保存、窗口CLOSED但未CONSUMED，父尚未合成S或掷save；保留原raw/子骰/反应成本与准确父返回点，不重执行孩子，也不根据19反写原raw23。 |
| 中途保存 S2 | 独立 golden 变体中，原完整 roster 内的角色 F 仍在锥外 (22.5,0)，无新增目标。F 的真实既有 Ready source 引用先前接受根/action spend、尚未到期且 reaction 可用；已验证可感知触发为 H 的吐息效果完成，回应为已登记的 5-foot move，速度及路径资格完整。AFTER_BATCH 产生该真实 Ready 的 OPEN 窗口时，全体 HP、E 的 ENDED source/application、D 的成功 CON 已提交，F 尚未选择或消费 reaction。恢复只保留准确余集，不补伤害/CON 或复活 E 的保护；Protection from Energy 本身没有虚构 end callback。 |
| 文件损坏 | 独立改一个target ordinal、source/roll引用、save total/骰、temp吸收、holder来源、cut、callback消费或已完成前缀即拒绝；不能reader自动修复或重演事件。 |
| END/清理/容量 | 任一pending可原样FINAL；A失败不删，B中间失败全回滚；实际64目标+T外效果闭包+反应最坏费用在保存结构/字节内。超额度在新增义务前拒绝，既有必需后缀预留仍完整，不借FINAL额度。 |

恢复验收须为S0/S1/S2提供独立字段清单/期望文件与数据库读回投影；writer/reader往返、
只比行数/头摘要或用测试中的假consumer不能提升“端到端接入”。真实Host应展示完整T、
每目标save/共享roll/减免/temp与HP、集中/效果结果及当前唯一继续选项，保持loopback、
同源/CSRF等既有边界；本文不开放新HTTP路由或浏览器写入。

稳定业务错误登记为 `SAVE_DAMAGE_TARGET_MISMATCH`、`SAVE_DAMAGE_VERSION_CONFLICT`、
`SAVE_DAMAGE_RULE_UNSUPPORTED`、`SAVE_DAMAGE_FROZEN_DATA_INVALID`、
`SAVE_DAMAGE_LIMIT_EXCEEDED`、`SAVE_DAMAGE_RESERVATION_INSUFFICIENT`、
`SAVE_DAMAGE_IDEMPOTENCY_CONFLICT`；状态/当前叶子错误沿动作协议，生命周期/未决沿运行守卫。
诊断含真实阶段、可取得的batch/target/check/packet及规则/事实位置，不虚构作者路径。
合法豁免成功、免疫、NO_TARGETS、目标失去creature资格和集中结束是有证据的业务结果，
坏冻结数据不能冒充这些结果。

## 17项责任落点与采用判定

本表的负责者为实施责任角色；“未实现”明确包括真实生产调用和存储证据，不能把本文
设计记录、pure fixture 或计划类型当已完成交付。设计之外的实际采用逐项遵循四层门。

| 项 | 负责者 | 本文合同与独立证据；实施边界 |
|---|---|---|
| 1 原文/范围 | 怪物领域设计者、内容作者 | 唯一实例及本地页码；golden使用真实fire抗性/免疫/易伤。三种执行方式与阻断范围明确；原文核对已完成，运行未实现。 |
| 2 身份/支持映射 | 领域设计者、应用维护者 | 两个准确新目标标识、有限profile、sealed frame/source；真实校验/consumer未实现，不映射为已有生产能力。 |
| 3 类型化作者字段/修改 | 领域模型及作者实现者 | 四组原子参数、完整身份/启用/来源链约束；本项只固定运行输入语义，不实现完整怪物作者schema2、字段编码/修改向量，作者/组合采用仍阻断。 |
| 4 离线/来源 | 来源reader/离线工具实现者 | 必须读回完整action/profile/引用/启用事实；未知字段负例。实际类型表、作者位置和真实数据库独立读回未实现，不以fixture替代。 |
| 5 组合/快照 | 组合及初始化实现者 | BatchSource绑定同一准确组合/snapshot；五类摘要/完整链沿模组基线，不重定义域。该条目的规范字节/独立向量及实际来源/快照读回未实现。 |
| 6 输入/目标 | 空间、命令及HTTP实现者 | BreathIntent、完整roster/membership/排序、0—64界；golden全T与伪造子集负例。真实空间reader/有限候选及入口未实现。 |
| 7 时机/资源/进度 | 怪物动作、turn/rest、flow owner | release/共享raw/各阶段、一次action/recharge、后续恢复责任、after触发和源失能语义；停点与RNG trace。真实continuation/资源消费者未实现。 |
| 8 纯计划/真实消费 | 规则作者、命令协调者 | 公共Check/Damage/Health/Concentration计划和唯一链；逐目标手算期望。纯计划与生产消费均未据本文实现，不用测试调用声称接入。 |
| 9 接纳/事务 | Service、JDBC实现者 | 完整6步预检、锁闭包、共同cut、多目标一次应用及故障表；真实SQL/连接/锁和注入证据未实现。 |
| 10 幂等/提交 | 结果持久化及故障实现者 | 不可变accepted与当前view、同键比较、前缀/后缀及未知查证；真实结果编码/数据库故障验收未实现。 |
| 11 状态/历史/版本 | batch、健康、资源、检定、专注/effect owner | 唯一owner/全局根/业务引用及版本表，golden版本期望；typed正文/真实读回未实现。没有新增第二份HP或holder。 |
| 12 保存/恢复 | 各owner、codec、恢复协调者 | 字段归属/逻辑落点/排除理由，S0/S1/S2独立投影；实际严格字节、逐字段独立向量及直接落库未实现，空分区不得冒充完成。 |
| 13 END/清理 | 保存结束、各领域及清理实现者 | pending原样FINAL、A/B分离、完整入向引用图和失败不删；实际物理图/清理SQL及独立B证据未实现。 |
| 14 容量/增长 | 预算、batch/effect及保存owner | 64目标结构、T外效果闭包、RNG及必需后缀union/预留；实际字节/节点/物理峰值尚未实证，不宣称容量通过。 |
| 15 独立验收 | 各实现作者、主审者 | 12-draw golden、13-draw反应变体、负例/事务/恢复矩阵构成独立的内容与算术期望；业务测试及真实数据库演练仍须按实现范围验收。 |
| 16 真实Host/可读结果 | 命令及Host实现者 | 明确有限输入、完整T与结果/来源、稳定错误和唯一继续view；生产页面/API及Host入口仍须实施验收。 |
| 17 同步/采用 | 领域维护者、集成者、主审者 | 本文、v2入口、采用边界及必要SAVE_DAMAGE登记一起维护；当前采用停留在设计层，真实安装、接受、机制链、跨域/发布与独立外部操作仍须分别完成。 |

选定实例确定了可审阅的公共结算接口设计和独立验收输入；当前采用
仍停留在设计层。补齐各行实际字段/规范字节、生产来源/快照/消费者、短事务、完整文件与
恢复清理、预留实证和Host证据后，才可分别判断内部可测、端到端和发布。它不重开已确定
的模组/动作/持续效果协议，不实现全域怪物或法术，不激活作者schema2或archive format2。
legacy `dnd5e2014_srd51_se_v1` 继续RELEASED与默认，完整SRD继续DRAFT；设计采用不授权
迁移、规则安装/接受、数据库业务写入、部署或Git外部检查点。
