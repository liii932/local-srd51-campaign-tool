# v2 动作暂停与继续协议

状态：统一控制协议、领域责任和验收条件已确定；[有限 INTERNAL 战斗消费者](../../rules/schema-2-combat.md)
已采用普通近战、移动、机会攻击的真实 owner、类型化存储及直接恢复。
[有限 INTERNAL 施法与场景合同](../../rules/schema-2-spells.md)另以同一 flow 接通
Bless/Cure Wounds、Shield/Counterspell 真实反应链及封闭触发的 Ready attack／move／spell；
真实成本、原骰、父子帧、专注与领域后果随类型化场景历史直接恢复。施法资格限该合同的
生命领域一级角色与单职业 Wizard 1—5 级学习／准备模型。
其他执行分支、完整文件映射和 Host 入口仍须各自采用，本文不证明所有分支已经可以运行。
它遵循[模组接口基线](module-interface-baseline.md)、[机制接入责任清单](mechanism-integration-checklist.md)、
[运行与事务](runtime-and-transactions.md)、[保存与恢复](save-and-recovery.md)及
[玩家步骤](player-steps-and-auto-save.md)。legacy RELEASED v1、DRAFT 状态和 archive format 2
未激活的边界保持不变；本文不授权迁移、规则接受、数据库业务写入或部署。

已接窗口包括命中后伤害前、施法完成前和 Ready 的有限完成触发；OPEN/CLOSED 窗口仍阻断
新的根动作与主动结束专注，须先明确决策并继续同一父动作。当前 Counterspell 仅采用
当前真实法术位范围内的自动反制；四环以上能力检定的公共检查接入、Shield 的 Magic Missile
伤害 producer、长施法等分支仍须独立闭环，不能从类型登记或局部计算逻辑推断支持。

## 范围与共同不变量

每个活动运行中的战役至多有一个未终结的 `ActionFlowV1`，统一承载攻击、移动、施法、
反应以及这些动作内必须完成的有限选择。施法专用 `SpellActionFlow` 的候选职责由它吸收，
不能并存第二个可独立推进的栈或写门，不能用虚构 cast 承载武器攻击或移动。
这里的 flow 是业务继续图，不是数据库事务；每次命令仍是一个短事务，等待用户期间不持锁。

- 完整预检先于 RNG、业务写入与成本消费。未知但可能适用的反应、效果或后续消费者阻断接纳，
  不能被过滤成“无候选”，也不能先扣成本再等待实现。
- 每个已接受成本、原骰、决定、已执行路径段和领域结果都有唯一业务身份。后继命令只执行
  尚未发生的准确后缀；重放、子反应返回、保存和恢复均不再次消费或补算。
- 同时只有一个可推进的叶子。父帧停在已登记 checkpoint，子帧真实终结后才能返回父帧；
  关闭窗口不等于解决父动作，也不开放普通游戏写入。
- 通用层控制顺序、引用、窗口和等待；攻击、位置、伤害、施法、条件、专注、时间与计步
  保持各自唯一 owner。控制帧不建立第二份 HP、动作池、movement pool 或施法资源。
- 无反应候选和必需选择、且所有后继计划均已证明时，同一事务可贯穿多个确定阶段直接完成。
  不要求每个内部阶段一次请求或一次点击。

本协议登记的是有限、类型化的应用能力。它不提供脚本、表达式解释器、任意 JSON payload、
动态 handler 名称或模组自定义执行阶段。持续效果生命周期按[持续效果协议](ongoing-effect-protocol.md)
接合；多目标豁免伤害按[具体结算合同](multi-target-save-damage.md)登记，完整区域几何、作者安装
与编辑器仍按各自采用边界完成。登记一个窗口不补足具体效果消费者。

## 时序依据

| 原文 | 本协议必须保留的时点 |
|---|---|
| [反应与分段移动，SRD p.91](../../references/srd-5.1/pages/page-091.md) | 反应结束后原角色继续其回合；使用反应后直到本人下一回合开始才能再次使用，不能在全局 round 边界统一补回；移动可在动作前后、已完成的多次攻击之间拆开。 |
| [Ready，p.93–94](../../references/srd-5.1/pages/page-093.md)、[持有法术能量，p.94](../../references/srd-5.1/pages/page-094.md) | 先登记可感知触发和回应 action 或 move；触发完整结束后才可使用反应，也可忽略本次触发。预备法术在准备时正常施放并以专注持有，释放不再施放一次。 |
| [攻击结构，p.94](../../references/srd-5.1/pages/page-094.md) | 先目标与修正，再攻击骰，命中后才伤害；已有攻击骰不能因反应而重掷。 |
| [机会攻击，p.95](../../references/srd-5.1/pages/page-095.md) | 可见敌人越出触及时、离开之前，以反应作一次近战攻击；Disengage、传送及不使用本人 movement/action/reaction 的被动移动不触发。 |
| [Shield，p.179](../../references/srd-5.1/pages/page-179.md) | 已被攻击命中或被 magic missile 指定时可反应；触发攻击也适用 AC +5，故攻击窗口必须位于命中已知、伤害尚未结算之间。 |
| [Counterspell，p.131](../../references/srd-5.1/pages/page-131.md) | 对可见、60 feet 内正在施法的生物使用，在其施法完成与效果发生之前结算；符合环级时直接反制，否则用施法能力作 DC 为 10 + 目标法术环级的能力检定。 |
| [Protection/GWF，p.24](../../references/srd-5.1/pages/page-024.md)、[施法时间，p.101](../../references/srd-5.1/pages/page-101.md) | Protection 影响攻击骰，须在该骰之前；GWF 是伤害骰选择，不消费反应。同一 turn 的 bonus action 法术限制须与其他施法双向核验，不能把本人回合中的全部反应一概禁止。 |
| [Cutting Words，p.13](../../references/srd-5.1/pages/page-013.md) | 允许攻击/能力检定骰之后、判定成败之前反应，也允许伤害骰之后、实际伤害之前反应；不能把所有攻击修正都限于掷骰前，或伤害原骰后立即扣 HP。 |

下述同一触发内的确定排序和容量上限是工程合同，不是 SRD 补充规则，也不改变上表时序。
开放式可感知触发由 Ready 领域提供已验证的有限 DM 事实和判定，不接收自然语言脚本自行执行。

## 状态、身份与必要字段

以下是语义必要集，不是最终 SQL、文件字段名或 HTTP DTO。每种字段的实际编码、约束、
独立读回及文件落点仍须由实现切片交付，不能因本表存在就声明存储完成。

| 对象 | 必要信息与不变量 |
|---|---|
| Flow | 可移植 `flow_key`、协议版本、根帧键、当前叶帧键、`ACTIVE/COMPLETED`、创建/最后变化事件；运行中另绑定准确 run/snapshot/campaign、聚合 CAS 和预留。ACTIVE 恰有一条有效的活动祖先链；COMPLETED 没有未决子帧、窗口或 choice。 |
| Typed frame | `frame_key`、父帧键或根、`kind`、领域 `owner_ref`、调用原因 `ROOT/REACTION/CONTINUATION`、状态、准确阶段、已完成阶段前缀、创建事件及待返回 checkpoint；反应调用另引用原窗口/机会/选择。所有引用同一 flow，不得成环、跨运行或悬空。 |
| Checkpoint | 所属帧、连续 ordinal、应用登记的类型/版本、真实触发事实引用、`BEFORE/AFTER`、领域 continuation 类型、已保存意图和不可重做结果的引用、当前依赖闭包。每个 checkpoint 只消费一次，不靠自由字符串定位代码。 |
| Reaction window | `window_key`、父 owner 帧/checkpoint、确切触发事实、`OPEN/CLOSED/CONSUMED`、完整机会集合、当前机会位置、开启/关闭/消费事件；CLOSED 仍属于父等待状态，CONSUMED 才表示父已消费本次继续。 |
| Reaction opportunity | 窗口内唯一 actor、连续 priority、类型化选项集合及来源、触发时资格依据、`PENDING/PASSED/DECLARED/COMPLETED/INELIGIBLE`、决定事实、可空子帧；DECLARED 恰有一个真实反应子帧，未终结前不得处理下一个机会。 |
| Reaction selection frame | 确切窗口/机会、允许的有限 `reaction_kind` 和参数、`PENDING/DECIDED`、决定事件及可空被选子帧。选择一个反应或 PASS；不能以任意 action 名称把普通操作变为反应。 |
| Domain choice frame | `choice_key`、领域 owner、准确 choice 类型/版本、原结果与候选引用、尚未决定集合、已决定前缀、`PENDING/COMPLETE`；例如 GWF 原骰 ordinal 的 KEEP/REROLL。领域值只在 owner 保存，帧引用它们。 |

帧 `kind` 封闭为 `ATTACK`、`MOVEMENT`、`SPELL_CAST`、`READY_RELEASE`、`REACTION_EFFECT`、
`REACTION_SELECTION`、`DOMAIN_CHOICE`、`TURN_CLOSING`、`ONGOING_EFFECT_TRANSITION` 及 `SAVE_DAMAGE`。每种 owner_ref 分别指准确攻击
attempt、移动计划、cast、已准备 action/move/held spell、类型化反应效果、窗口机会、领域
choice、turn closing、持续效果的准确转换批次/待结义务或准确 `SaveDamageBatch`。新增 kind/continuation
必须随应用建立完整合同和消费者，不能由内容包登记。READY_RELEASE 使用已准备结果的准确
owner，再调用其真实 ATTACK/MOVEMENT 子计划或已登记效果 consumer，不创建假施法。
REACTION_EFFECT 仅承载应用已登记的非施法/非攻击/非移动反应，例如 Protection 对准确父
攻击贡献一份 disadvantage；必须保存真实来源、目标 CheckPlan、reaction spend、贡献及
结果引用，不能以通用 effect 名称/参数袋接收任意效果。

`ONGOING_EFFECT_TRANSITION` 由持续效果协议登记真实转换与后继责任；在已有动作内以
`CONTINUATION` 子帧接合，独立效果命令才按其资格建立根。它引用唯一领域状态和准确时间/
空间/专注来源，不另建效果栈。普通转换不消费反应或增加反应深度；只有真实反应子动作
沿既定规则消费。稳定存续的实例不持有该帧，只有当前尚待处理的转换占用动作流。

`SAVE_DAMAGE` 由[多目标豁免伤害合同](multi-target-save-damage.md)登记批次、固定阶段和返回责任。
地狱猎犬吐息根使用真实 `MONSTER_ACTION` 来源及 action/充能消费；它没有攻击骰，也不创建
假 cast。帧只引用批次，不复制检定、共享伤害骰或 HP。其他机制须先完成自己的 producer
与消费合同，才能以真实父 owner 的普通 CONTINUATION 调用，不因此自动开放法术或其他怪物动作。

执行帧状态为 `RUNNABLE`、`WAITING_WINDOW`、`WAITING_CHILD`、`WAITING_CHOICE`、`TERMINAL`。
选择帧按表内专用状态；当前视图中的等待类别由真实叶子推导，不另存可漂移的第二状态。
TERMINAL 必须引用领域真实结果；在同一事务消费返回并移除活动链引用，历史继续保留。
不能只给帧标 COMPLETE 而缺少领域结果、成本、审计或版本变化。

普通领域子动作使用 `CONTINUATION` 边，例如 SPELL_CAST 的具体效果真实产生一个法术攻击
ATTACK；它复用原施法来源，不消费第二个 action/reaction，也不增加反应深度。只有绑定
真实触发、窗口机会和一次 reaction spend 的 `REACTION` 边才增加深度和消费反应。
同一反应内部的普通子计划继承现有深度；不能把普通孩子一概视为反应，也不能借普通边
绕过实际反应成本。产生子帧与父 WAITING_CHILD 同事务保存，孩子终态后消费唯一返回点。

`flow_key/frame_key/window_key/choice_key` 是战役内稳定业务键，按[身份合同](character-state-and-history.md)
产生和保留，可独立恢复；它们不等于数据库行 ID。可移植引用使用战役键、稳定角色/生物/
物品/过程键及 `(event_sequence, ordinal)` 等真实事实身份。run/snapshot、本地行 ID、
row_version、请求键/收据和预算/CAS 控制不进入战役文件；恢复映射重建它们。

## 封闭登记与完整预检

每个可执行阶段由应用提供一份类型化登记，至少包含：

1. 准确阶段类型/版本、owner kind、输入/候选/结果结构、引用和数量边界。
2. 触发事实 producer、精确 BEFORE/AFTER 时点、完整候选 reader、排序和可选反应类型。
3. 所有成本的唯一接受点、骰子 producer/审计、领域结果 consumer、父级重验和合法失效结果。
4. 可变化依赖与冻结结果的划分、真实版本闭包、历史、完整保存/直接恢复和清理归属。
5. 从接纳到所有允许终态的有限计划及费用上界，包括子反应、选择、失败、停止和 CLOSING。

登记里的 plan 是现有领域类型的组合，不是递归携带任意 success/failure JSON 的通用程序。
`CheckPlan` 仍负责确定检定输入、贡献和分支；共享检定核唯一产生 audit。
`DamagePacket` 仍负责有来源的伤害分量、减免、HP 与伤害后订阅者；flow 不另算一份伤害。
条件实例、专注 holder/伤害豁免、时间窗和持续施法工作仍由真实 owner 消费，不因开窗口
重复掷专注骰、重复结束 holder、增加六秒或创造空效果成功。

新根接纳前完整读取固定规则、全部目标与候选、角色和怪物、位置/感官/敌意、动作及资源、
适用效果/条件、历史来源、乐观版本、幂等和容量。所有当前和可达分支的对象/消费者必须
可验证；后继可能变化通过已登记的有限 frontier 和终态证明，不能假设当前没有来源便永远为零。
缺真实 monster 反应、移动危险触发、伤害后订阅者或具体法术效果时拒绝相关根，不能静默漏项。

已接受根的后继不重做根操作，但仍在首 RNG/写入前重建当前阶段的完整计划、版本和余额。
意图已固定的目标、路径或法术不得自动换成其他目标/路线/资源来通过重验；仅未执行部分
且领域显式允许的新计划可以经独立、带版本的命令替换，保留原计划及替代历史。

## 统一转换与窗口推进

| 转换 | 前置条件与原子结果 |
|---|---|
| 接纳根 | 完整预检通过；保存 owner 的已接受意图、真实成本/原骰或其准确未发生状态、flow/根帧、历史、预留和请求结果。无等待时可同事务终结。 |
| 到达 checkpoint | 确切前缀已经完成，触发事实真实且仅一次；完整派生机会。无候选则直接消费 checkpoint；有候选则父转 WAITING_WINDOW，保存窗口和第一个 reaction selection。 |
| PASS | 仅当前 PENDING 机会；记录本次忽略与原因，选择帧 DECIDED、机会 PASSED；不消费反应、法术位或骰子。随后处理下一个机会或关闭窗口。 |
| 接纳反应 | 完整复验当前机会、具体反应源/目标/材料/资源、版本、预算和深度；消费一次真实反应及该具体动作的应付成本，保存选择、DECLARED 机会和真实子帧，父转 WAITING_CHILD。 |
| 子帧终结 | 已结清自身所有孩子、窗口、choice 与领域订阅者；写真实结果并消费唯一返回点。反应孩子另把机会转 COMPLETED，返回原窗口的准确后继；普通孩子返回父领域阶段，不改无关机会。不先执行父再回放子。 |
| 机会失资格 | 当前机会的触发时资格依据仍可核验，但现时反应已用、源终止或其他已登记条件不再满足；由明确推进命令在无 RNG 事务记录 INELIGIBLE 和原因，GET 不写。 |
| 关闭窗口 | 全部机会为 PASSED/COMPLETED/INELIGIBLE，无未终结子帧；保存 CLOSED。窗口留在活动链，普通写门仍关闭。 |
| 父级继续 | 准确 CLOSED 窗口、当前叶子/父 checkpoint、当前全部依赖和版本通过；消费一次 continuation，窗口 CONSUMED，推进父阶段或写已登记停止/失败终态。可在关闭的同事务完成，也可由下一明确命令继续。 |
| 领域 choice | 仅当前 choice 的合法尚未决定项；按 owner 语义 KEEP/选择或抽一次新骰，保存决定和结果。未完成则继续等待；完成时同事务消费父后继，不能让普通写入插入 choice 与父消费之间。 |
| 终结 flow | 根领域结果及全部子义务真实终态，所有引用/费用结清；转 COMPLETED 并释放活动写门。保留历史与业务结果，不删除过去的接受事实。 |

显式推进命令可以在完整复验后连续记录已失资格机会并到达下一个有效选择，不能按超时
自动 PASS。若提供 DM 关闭窗口，必须是精确 top 窗口的有审计命令，把剩余 PENDING 分别
记 PASSED/DM_CLOSED；不能跨过正在执行的孩子、清掉必需领域 choice、退款或替父解决。

## 候选、优先级和深度

一个触发中一个 actor 至多一个机会；多个合法反应作为该机会中的有限选项，使用其中一个
不会再取得同窗口的第二次选择。候选与触发资格在 checkpoint 完整确定；后续重验只可
记录已有机会失资格，不能因子动作新获得能力而追溯加入原触发。子动作产生的新事实可
按自己的已登记时点产生新窗口。未入选、PASS 和 INELIGIBLE 都不消费反应。

战斗按真实 initiative ordinal（已解决同分）排序；非战斗按生物类型 `CHARACTER` 先于
`MONSTER`，同类型再按稳定生物键的 UUID 网络序排序，priority 从 0 连续。
仅最小未决定 priority 可被选择。
不按浏览器请求先后、不按数据库返回顺序抢反应；SRD 明确的不同 BEFORE/AFTER 时点
优先于此排序。同一事实多个登记阶段按应用固定阶段序，不能由模组加载顺序改时间线。

| 工程上限 | 处理 |
|---|---|
| 活动反应调用深度 8，根为 0 | 只有接受真实反应子动作才加一；待选 selection、普通攻击/移动阶段、checkpoint、GWF choice、长施法工作或普通子计划不增加。其孩子全部终结返回才减一。 |
| 每窗口最多 128 actor 机会 | 完整集合超限就拒绝产生窗口的接纳/继续命令，不取前 128 个，不把超出者自动 PASS。 |
| 每 owner 执行帧最多 64 checkpoint | ordinal/阶段总量在接纳前可证明；不能用重新建立同一 owner 帧规避。 |
| 每 flow 最多 1024 个已创建执行/选择帧、1024 个窗口，活动祖先链最多 32 帧 | 同时限制累计触发与活跃引用；数量按真实图计费，不能靠 pop 清零累计额度。 |

这些值是待工程验证的支持上限，不是压测结论或游戏规则。达到深度 8 时尝试第 9 层反应
返回 `REACTION_DEPTH_LIMIT`，没有第 9 次成本、骰子或事件；原机会保持待选，可显式 PASS。
其他上限也在消费前拒绝并保留原 pending，不能截断合法候选或自动结算。
反应终结图不能通过“新 flow”或把 reaction 标成普通 continuation 清零深度。
所有上限同时服从保存结构/字节、单命令 RNG、目标/guard 与真实领域更小边界。

数量也是必须预留的结构额度：接纳根及每个可选反应/新选择前，须证明“已创建数量 +
本次增量 + 父子全部必需后缀最坏数量”不超对应上限。未来必需窗口的完整机会数也在
frontier 中证明，不能先接受子动作，再于强制返回时才发现第 65 个 checkpoint 或窗口
超 128 actor。可选孩子会超限就在它被接受/付费前拒绝，原机会仍可 PASS；父既有强制
后缀的额度不被可选孩子借走。已接受的必需继续不得以事后超界为正常路径而永久卡住；
重建发现此不变量破损属于完整性故障，保留原状态并按运行故障/救援保存处理，不截断结算。

## 活动链与调用边界

owner 帧 O 到达窗口 W 时，O 为 WAITING_WINDOW，新建 `parent=O` 的 selection S，S 是
唯一可推进叶子。接受反应后 S=DECIDED，真实孩子 C 的 `parent=S`，O=WAITING_CHILD；
S 留作不可推进的祖先，当前叶子变为 C 或其后代。C 的全部义务终结时，同事务把机会
置 COMPLETED、从活动链摘除 C/S、令 O 回 WAITING_WINDOW，并产生下一 PENDING 机会的
新 selection；没有下一机会则 W=CLOSED，叶子准确回到 O。历史帧和决定均保留。

PASS 或明确记录 INELIGIBLE 同样在一个事务决定并摘除 S，再换下一个 selection 或关闭 W。
CLOSED 时没有 selection 叶子，O 仍为 WAITING_WINDOW 且只允许消费 W 的准确 continuation；
不能因为 O 成为叶子便执行普通动作。消费后 W=CONSUMED，O 才进入真实后继状态。
普通领域孩子直接以 O 为父、O=WAITING_CHILD；其返回不经过机会状态。领域 choice 同样
直接以 O 为父、O=WAITING_CHOICE，最后决定与摘链/父消费同事务。不得留下 DECIDED
selection 作为无合法操作的叶子，或同时保留两个 PENDING selection。

| 调用/结果 | 有界语义必要集 |
|---|---|
| `ContinueIntent` | 准确本地 run/snapshot/campaign 和 `flow_key`；请求幂等键；应用登记的 `operation_key={producer, subject, phase, ordinal}`；预期叶帧键和按操作必需的 window/opportunity/choice 键；登记的完整预期版本集合；封闭操作 `PASS/DECLARE_REACTION/ADVANCE_WINDOW/CONTINUE_OWNER/DECIDE_CHOICE/CONTINUE_CLOSING` 及该操作专属类型化决定。无关分支字段不得出现，未知/重复/过量字段拒绝，不接收自由阶段或结果。 |
| Accepted | 原操作身份、接受根事件、当时 flow/owner/阶段、当时成本/骰子/决定与真实结果引用；只描述本次不可变接受事实。该结果的 schema/字节随具体操作定义，不混入可随时间变化的“现在可操作”令牌。 |
| `PendingView` | 当前运行资格/阻断原因；准确 flow/叶子/owner kind/阶段及当前版本；当前 window/opportunity/choice、actor 与 priority；服务端有限选项及来源、允许观察的原结果/余集、当前允许操作；终结或已清理时明确现状。它是只读观察，提交时仍完整重验，不产生资格、保存或自动推进。 |

外层 DTO 与字节界使用实际登记的更小边界并服从运行请求总界；语义中显式无值与缺失
分别校验，不用 null 接收任意分支。查询和 accepted 之间没有“自动跟随最新 flow”的
重定向关系；旧视图必须重新取得当前身份/版本后提交新的准确意图。

## 各领域 owner 的继续责任

| Owner | 必须保存与只执行一次的事实 | 返回时必须核验及处理 |
|---|---|---|
| 攻击与 AttackSequence | 动作来源、sequence/turn、唯一 attempt/attack ordinal、所选目标/武器、真实攻击 audit、命中/暴击依据、弹药等应付成本、伤害/GWF 引用与唯一完成结果。BEGIN 消费 GENERAL action 一次，后继攻击/choice 不重新发放动作。 | 未掷阶段可按注册规则重算当前修正；已掷阶段保原 audit，掷后贡献及 AC/防御只在各自真实时点进入最终结果。真实失能、目标失效或反制必须有该阶段的类型化终态及 sequence 收束语义；没有则原分支不可接纳。不能借关闭 sequence 丢弃已掷未完成一击。 |
| 移动 | 原路径/模式、派生边界、已执行前缀、当前位置、下一段/段内位置、累计 spent、剩余 allowance、触发消费标记及时间进度。普通 turn MOVE 使用原 movement pool；Ready move 使用其真实反应许可和至多当前 speed 的单次 allowance，不创造普通 turn 或额外行动。 | 从当前权威位置核验原后缀、速度/地形/阻挡、健康、敌意/感官和新触发。不能走则写合法 STOPPED 结果和原因，保前缀及成本；不回到旧位置，不自动换路/换模式/重走。 |
| 施法 | cast/真实授权、action/work 来源、目标/资源选择、材料绑定、CASTING/DECLARED/RESOLVED/INTERRUPTED 等领域状态和具体效果结果。START/工作不提前扣只在 DECLARE 应付的 slot/消耗材料；DECLARE 一次接受后反制/失败不退款、不二次扣除。 | 子反应结果先应用，再消费准确 cast continuation；COUNTERED/FAILED 是真实结果，不能空效果冒充 RESOLVED。已 DECLARED 的成本保留；未到接受点的资源不因保存/超时提前消费。 |
| 反应可用性与 Ready | 最近使用事实和本人下一回合开始的恢复；Ready 的来源、可感知触发、action 或 move、到期点、已准备法术/专注与释放状态。 | PASS 仅忽略这次 trigger，未到期 Ready 不因此自动取消；真正使用或到期由 Ready owner 唯一消费。持有法术被打断不再释放；释放无第二个 CAST/slot/material/Counterspell 声明。 |
| 类型化反应效果 | 准确来源/窗口机会、一次 reaction spend、父目标/阶段、效果贡献和 APPLIED/失效等登记结果；Protection 的贡献指向原攻击的 CheckPlan。 | 真实贡献保存后才能终结子帧；父核按准确引用消费一次，不重新施加、不造 attack/cast、不重复消耗反应。具体效果缺完整结果/存储/恢复仍拒绝。 |
| 持续效果转换 | 唯一来源/目标实例、转换原因、已完成状态变化和准确待结义务；字段及生效/压制/恢复/结束语义见持续效果协议。 | 以当前权威状态重验未完成后继，保留原期限、已消费事实和终结事实；不重放原施法或已执行回调。父必须等本次必需义务结清再推进，不能恢复已结束实例。 |
| 多目标豁免伤害 | 准确 SaveDamageBatch、真实动作/充能成本、完整目标、逐目标检定及共享伤害 audit、已完成阶段前缀、统一伤害时点与健康/专注结果。 | 按具体合同重验尚未执行的阶段与完整版本；共享骰不重掷，各目标 packet 不重复应用；全体健康计划原子应用，先前已提交的反应/骰子前缀保留，全部必需后继结清才终结。 |
| 伤害、条件和专注 | 同一个 DamagePacket/伤害 audit、真实 Vitals/Health 变化、条件来源、holder 与集中豁免 audit。 | 父/子所有变化在其真实时间点生效；伤害后集中检定沿共享 CheckPlan 唯一掷骰，不能由 flow 再掷一份或靠 pop 解除条件。 |
| 非反应 choice | 真实领域 attempt、原骰/已决定前缀、允许的有限候选、当前未决集合与最终 consumer。 | 冻结依赖仍完整；不得重新开原命中窗口或更换原攻击目标。没有统一“默认选择”，技术 END、断线、forfeit 和 GET 不作决定。 |
| TURN_CLOSING/时间/计步 | 准确 turn/round、固定 closing 队列、真实时间/work 信用、未完成义务和唯一玩家步骤 credit。 | 只消费当前队头及其 flow 子帧；全部必需义务终结后才完成 turn/round 和 PC 步骤。长期合法跨步状态本身不阻断完成。 |

未掷攻击因子反应变得不能执行时，保存 `ATTACK_STOPPED_BEFORE_ROLL`，保留已接受成本、
释放本次未掷 attempt 的独占而不增加 performed；actor 仍可行动时原 sequence 的其他攻击
额度保留。actor 已失能则由健康/sequence owner 以 ACTOR_INACTIVE 关闭并记录真实 forfeited，
不能制造 HIT/MISS 或攻击骰。已经掷出的攻击始终保留原 audit；若目标被孩子改变为不可
结算，登记 `ATTACK_TARGET_UNAVAILABLE` 无伤害终态，真实已掷一击仅完成计数一次，不对
失效目标套用旧健康/位置或制造 packet。具体 owner 缺少这些结果/历史的 reader/consumer
时仍阻断原分支；不能临时删 attempt、退款或把合法失效当数据损坏永久卡住。
sequence 的 performed 只统计真实完成攻击；pending 一击不计 performed，但独占下一 ordinal。
末次完成/失能关闭的优先级和终结引用仍由 sequence owner 一次处理，不由通用层重计。
此计数只适用于真实 sequence 来源。法术攻击引用原施法，OA 引用其一次 reaction spend
并作一次近战攻击，不调用 BEGIN、不扣 GENERAL action，也不借普通 Extra Attack 额度；
Ready action 的攻击数与成本由其原准备来源和当前合法条件决定，不能套用普通序列放大。

## 典型时序与父级重验

### 攻击、Shield 和骰子选择

`ATTACK_BEFORE_ROLL` 先结清影响本次攻击骰的合法反应，再用真实 `CheckPlan` 掷一次攻击。
Protection 用 REACTION_EFFECT 子帧一次消费反应并登记该攻击的 disadvantage，返回后由父
CheckPlan 合成真实 adv/disadv；不能先掷 d20 再补 Protection，也不需要假 cast/attack。
随后先经过 `ATTACK_ROLLED_BEFORE_OUTCOME`：原 d20/selected/原 total 审计不改，尚未判定
命中；Cutting Words 类掷后反应以真实来源、自己的资源/骰 audit 和类型化贡献单独记录，
原攻击 owner 只消费一次这些贡献形成此阶段有效 check total/初步判定，不能改写原骰、重掷或
接收临时任意 modifier。此类反应全部结清后才判 HIT/MISS/critical；具体 reader/consumer
不完整的适用分支仍在原接纳门阻断。能力检定有同等时点需求时，也须登记其真实 owner
和 `CHECK_ROLLED_BEFORE_OUTCOME`，本阶段名不自动开放所有检定效果。
不可变原骰/当时依据、后续反应贡献、最终判定是分别可审核的事实；不能覆盖已经终结的
audit.total/hit。领域采用须建立准确 typed pending-roll/outcome 投影及引用，旧 final audit
不能冒充尚未判定的 roll；本协议不回填既有审计。

命中才进入 `ATTACK_HIT_BEFORE_DAMAGE`；此时 Shield 候选看见的是命中事实，伤害骰尚未产生。
Shield 子 cast 可以有自己的 Counterspell 窗口；整个孩子结束后，父用原 selected d20/total、
已合法合成的掷后贡献、自然 1/20 及当前合法防御重新确定该次命中，不能再掷 d20 或重复
消费同一贡献。自然 20 的命中仍按攻击规则处理。
若已不命中，保存真实防御改变后的 MISS，不产生伤害骰或 GWF choice。
原先已知命中保留为 Shield 的真实触发事实，反应后另存最终 outcome；不能回写覆盖原
触发或已终结 audit 的 hit，也不能把 Shield 之后的判定重新当成同一命中机会开启窗口。

所有命中前及此命中后反应结清后，才按最终命中产生伤害原骰；若有合法 GWF 低骰，建立
`DOMAIN_CHOICE/GWF_DIE_CHOICE`。玩家可逐个选择任一尚未决定原 ordinal 的 KEEP/REROLL，
REROLL 仅抽同 faces 一次且必须保留新值；KEEP_REST 明确保留余集。原攻击 audit 跨根引用，
opening/中间决定不增加 performed；最后决定先固定最终保留骰，再进入后续伤害阶段。
不得在 GWF 看见伤害骰后重新提供该次命中的 Shield，或把 GWF 当消耗反应的能力。

伤害原骰和合法骰子选择确定后必须经过 `DAMAGE_ROLLED_BEFORE_APPLY`。Cutting Words 类
伤害骰后反应另有真实资源/骰 audit/来源与贡献；damage owner 一次消费贡献，按其已登记
算法形成最终 damage audit/packet、健康与伤害后义务，最后才完成该攻击/计数。已有骰和
GWF 决定不改写、不重掷；无后置候选时最后 GWF 决定仍可在同事务完成全部后继。
有后置窗口则先提交真实 retained dice/窗口和未完成 attempt，performed 仍不前进；返回时
使用当前合法健康/减免闭包，不能套用 GWF opening 的旧 HP。具体减伤/后置反应没有完整
reader、贡献结构或消费者时，此适用分支保持关闭，不以本协议代替伤害算法设计。

GWF 等 choice 阶段按 owner 冻结其真实业务依据。包括 `END_CONCENTRATION` 在内的并行命令，
只有该阶段明确登记、证明不破坏冻结依据且具备完整重新验证/终态时才可接纳；不能照搬
施法等待阶段的例外。查询和技术保存不改变 basis；需要改变 basis 却没有处理合同就拒绝该写入。
其他反应/伤害选择不因 GWF 示例自动获得支持。

Shield 的 magic missile 分支另登记 `SPELL_TARGETED_BEFORE_DAMAGE`，无需虚构 ATTACK audit；
其真实法术和免伤 consumer 未完成时，此分支保持关闭。

### 移动、机会攻击和 Ready move

原路径 1—64 输入段及最多 256 个派生几何子段须完整预检；原始路径/资源/消费者不合法时
整根拒绝、零前缀写入。合法接纳后按真实触发边界推进，不能先到终点再让反应看旧位置。
在 `MOVEMENT_LEAVE_REACH_BEFORE`，保存到“仍在触及内的离开前边界”的已走前缀及其真实
spent，待离开后缀尚未执行；完整候选据此产生 OA。多个敌人的 OA 按窗口顺序逐个结算。
不适用 OA 的离开仍必须由完整 reader 证明，不能接受客户端的“不触发”布尔。

子 OA 若使移动者失能、速度归零或使原后缀失去资格，父写 `MOVEMENT_STOPPED` 的类型化原因，
保留边界位置、已用 movement 与真实伤害；合法停止是结果，不是格式错误。即使仍可移动，
也只重验原未走后缀。被子效果搬离原路径时，从真实新位置记录停止，不瞬移回 checkpoint；
要走新路须在原 flow 终结后接受新的合法移动意图。

Ready 的 `READY_TRIGGER_AFTER` 位于触发事件或对应可感知移动边界已完成之后；它不能把
同一 OA 的“离开之前”改成“离开之后”。真实 Ready move 消费本人反应来移动，故其路径
也可能触发其他生物的 OA：父 MOVEMENT → Ready selection → READY_RELEASE → 子 MOVEMENT
→ OA selection → 子 ATTACK。孩子逐层完成后才返回父原边界；父已走前缀不回滚，剩余路径不自动重规划。
父移动与子 Ready move 各用自身唯一 allowance/spent，不能复制或借用对方 movement pool。

普通 turn 内移动不另推进时间；探索 MOVE 原定一次六秒只由时间 owner 按登记计划记一次，
不因每段、暂停、反应、恢复或最终继续重复增加。需要中途时间分配或危险区域消费者却
缺真实计划时阻断该分支，本协议不补造沿路径的均匀时间表。

### Counterspell 与准备法术

`CAST_BEFORE_COMPLETION` 保存真实 cast 及其已接受的声明成本，效果尚未执行。Counterspell
子 cast 完整校验可见性、60 feet、真实法术位与成分；可再被合法 Counterspell，且按真实
反应调用计深度。内层先解，外层再解；成功反制把目标 cast 置真实 COUNTERED 结果，阻止
其效果，保留已付 action/slot/材料。反制能力检定沿共享核，不因它是法术便自动添加 PB，
也不改成法术攻击；真实适用的能力检定贡献仍按其准确来源合成。

预备一 action 法术在“准备时的正常施放”可有此窗口；成功后由 Ready/专注 owner 持有，
释放只是已有准备结果的真实消费，没有第二次施法声明。这是依据原文“准备时 cast、随后
release”的协议解释，不是新的 SRD 原文表述。长施法过程中的特殊反制、尚未
DECLARE 的中断成本与 work 时点必须由施法领域另外闭合；不能套用“失去专注不扣 slot”
决定所有 Counterspell 资源。未闭合的该分支保持阻断。
长期 CASTING 或尚待未来触发的合法 Ready 不持续占住活动 flow；它们保留在领域状态中，
真正的声明/触发才重新经过共同守卫建立一个 flow。不能由 GET 或定时器自行继续。

反应施法同样使用真实 turn 的完整施法历史：本人 turn 已施放 bonus action 法术时，不能
再用反应施放另一个不符合该限制的法术；先有此类反应施法，也不能随后用 bonus action
施法绕过同一 turn 限制。其他生物 turn 的反应按那个 turn 核验，不能按 round 泛禁。

## 接纳、写门、版本与幂等

沿[运行事务](runtime-and-transactions.md)先取进程门、永久数据库守卫，再按固定锁层次取得
完整领域闭包；Repository 使用调用方连接。新命令先核准确意图和幂等事实，再验证本次
运行资格、准确 flow/叶子/窗口/choice、全部当前版本、领域条件、预算与随机性上限，之后
才能 RNG 或写入。客户端预期版本只是待验证输入，不能指定阶段、骰点、派生修正或版本推进。

每个新根/继续命令以一个全局根事件记录真实状态、成本、原骰/决定、领域正文、活动链、
所影响聚合各一次版本推进、预留变化及唯一请求接受结果，原子提交。无变化的聚合不加版，
回滚不推进事件尾或 CAS。阶段内操作先后顺序不产生多个可漂移的版本源。
父先前 CAS 不能授权新后继；子帧返回后必须使用当前 flow/owner/所有受影响对象版本。

ACTIVE flow 的新游戏写入仅允许当前叶子上的已登记选择/反应/推进，以及该阶段明确允许
的有限领域操作；普通移动、下一击、装备、学习、休息、时间推进、步骤完成或模式交接
不能绕过等待。子效果的健康/条件变化由其真实事务内计划消费，不以另一个普通入口插队。
读取、精确重放、受守卫允许的保存/技术 END 按各自资格执行；前端“这是反应”不授予权限。
没有 close→resolve 普通写入空档，也没有从关闭窗口新建第二 bottom 的入口。

相同请求键、相同规范意图只返回原接受事实；不同意图冲突。旧 opening 结果为 WAITING，
后继完成后仍返回那份 WAITING，不改写成 COMPLETE，不凭旧结果授予当前选择资格。
当前视图另读真实 flow 和领域状态，可显示现时 pending、终态或工作集已清理不可用，
不能与不可变 accepted 结果混成一份。精确重放不先以当前易变版本/配额重新执行原命令。

完整 rollback 明确成功时不留部分状态；已提交但响应失败保留已接受事实。commit 未知或
rollback 失败使执行资格失效，按原操作在共同守卫下只读查证；不换键重试、不补结果、
不倒退 RNG、不过期自动 PASS，也不因查证成功自动执行父后继。

## CLOSING、玩家步骤和终结预留

完成/放弃 turn 先计算结束后会产生的完整义务，不能仅看命令开始时 flow 为空。可能产生
反应、choice 或最后 round 长施法声明时，先原子接受 `TURN_CLOSING` 根、真实时间/work
信用、固定 finish 队列及预留；不创建第二个并行 bottom。一次只处理准确队头 cast/回调，
其反应与 choice 都是同一 flow 的孩子。

队列前项的反应改变后项资格/版本时，用独立无 RNG 事务记录 `REPLAN_REQUIRED`；后续明确
重计划命令只替换尚未发生后缀，保留原队列位置、work/action 来源和 superseded 计划。
已 DECLARED 的 cast 不重扣或退款，已执行时钟/work、既有骰子和历史不改写。
无合法替代时由已登记放弃/中断终态收束，不能自动换目标或靠旧计划继续。

全部必需结束回调、finish 项、窗口及 choice 终结后，才写 turn 完成、必要 round 关闭和
PC 的唯一 step completion/N+1；NPC 回合与回合外反应不计玩家步。
例如 N=49/W=0，结束回合产生长施法反应时先 CLOSING、N 仍为 49，全部义务收束才 N=50，
之后触发既定自动保存屏障。不得先计 N=50 再用屏障阻断尚未完成的反应。
合法跨步的专注、Ready、持续效果或长施法状态本身不要求自动结束；完整 owner 完成判据
仍是前提；持续效果的到期、压制与结束义务按[持续效果协议](ongoing-effect-protocol.md)接合。

每次产生 pending 前，预算 owner 必须证明完整变化后可导出，并持久保留从当前状态到
所有允许终态的有限收束额度：真实子反应/选择、已掷未完成一击、窗口关闭、父重验失效、
停止/失败、历史/版本/结果及 CLOSING 全部纳入。只给 top window 或每帧固定若干字节不够。
上限只证明有限，不代替准确结构/字节/节点/数组与 RNG 的计算。

CASTING、FLOW、TURN_FINISH 等义务按同一语义节点去重并在真实状态迁移时原子移交；
不能分别给父子计入同一健康变化，也不能释放父预留后才尝试为必需孩子申请空间。
后继在首 RNG 前重建完整计划并核实际费用不超剩余额度；当前免费空间不足不能丢弃已接受
收束义务。失败/未知提交不释放额度，只有真实终态和读回闭包匹配才结算。
最终保存/首次修复预留与 AUTO 峰值仍遵循[保存预算](save-and-recovery.md#容量与增长)，
不得借用最终保存预留维持普通 flow，也不预留无限未来回合或未决定的可选新攻击。
真实 SQL、物理峰值和容量实证未完成前保持相应采用门关闭。

## 保存、直接恢复与技术 END

| 数据 | 文件与恢复责任 |
|---|---|
| 活动 flow、帧、checkpoint、窗口、机会和 choice | 保存全部可移植身份、类型/版本、状态、父子/返回关系、准确候选顺序、决定/消费事实及活动祖先链；不能仅保存 top 或 PENDING 标志。 |
| 领域意图、原骰和进度 | owner 保存原攻击 audit/最终防御、尚未消费的伤害与 choice、已走路径/剩余后缀/spent/触发标记、cast 声明成本与效果、Ready 持有状态、CLOSING 队列/原计划和全部业务历史。 |
| 本地控制 | run/snapshot、物理外键、row_version/CAS、进程资格、请求收据、N/W 和本地预算事实明确排除；按已验证业务图重建新本地身份/版本和收束预留，不把它们伪装成业务字段携带。 |

已终结的帧、窗口、机会和决定凡为完整历史或现有因果引用所需，也必须随文件保存并
直接恢复，不能只留活动链而使原骰/反应来源悬空；恢复这些历史不重新激活或执行它们。

严格 reader 独立验证：同战役归属、完整规则引用、类型/状态合法组合、父子无环和唯一叶子、
机会顺序与子反应来源、反应使用恢复点、成本消费与真实事件、骰子和决定余集、路径前缀与
当前位置/spent、窗口消费次数、领域终态/历史和 CLOSING 计步一致性。已接受前缀保留，
未执行后缀按类型解析；不能重新运行事件来判断/补做已执行部分。缺分区或 reader 不支持
直接拒绝，空数组不代替尚未实现的状态。

writer/reader 往返自洽不足以验收，须独立逐字段比较源覆盖和恢复投影。独立恢复原样写入
已验证业务状态/历史，重建本地映射和当前依赖版本；不重掷、不再扣资源、不代选、不关闭
窗口、不走剩余路径、不补回合/时间/计步。新运行明确提交并发布执行视图后，才可用新的
受保护意图与新 CAS 继续原业务叶子；旧请求键和旧版本不获得新执行权利。

技术 END 只排空在途数据库事务，按最新完整源保存 OPEN/CLOSING/选择中状态；不隐含
PASS、KEEP_REST、resolve、退款、退出战斗或停止专注。按 A 保存 FINAL、独立 B 验证清理：
flow 的窗口/机会/选择、领域 pending、业务引用、历史和本地预算均须纳入完整清理图，
FINAL 实际读回并验证等价后才原子清理。失败保留完整工作集，不能先删 flow 或保留悬空 owner。
永久身份/请求结果/结束事实与档案按保存合同保留，不属于 flow 结束可删除的数据。

重启残留不自动重建 ACTIVE 执行资格，也不能直接 resume；仅经核验救援保存 END，再从
合格文件按新运行完整恢复。GET、诊断、下载、停机、提交查证及精确重放均不触发业务继续。

## 稳定错误与诊断

以下是本协议的正式语义错误；HTTP 状态与 DTO 在真实入口采用时映射，不借此变更 v1。
错误包含阶段、准确 flow/frame/window/choice（可取得时）、类型化原因及真实规则/事实位置；
不以异常文本或虚构来源充当错误码。坏冻结数据与保存损坏不能当成正常 INELIGIBLE。

| 错误 | 发生条件与保证 |
|---|---|
| `ACTION_FLOW_BUSY` | 新游戏写入不属于当前允许叶子/阶段；拒绝写入和 RNG，不隐式关闭旧 flow。 |
| `ACTION_FLOW_TARGET_MISMATCH` | 跨运行/战役、错帧/窗口/choice、非当前叶子或错 continuation；不跟随“最新”对象。 |
| `ACTION_FLOW_VERSION_CONFLICT` | 新请求的任一必要 CAS/领域版本过期；无 RNG、成本、事件或部分版本推进。 |
| `ACTION_FLOW_STAGE_INVALID` | 违反转换、重复消费 checkpoint/已决定项、窗口未关闭或孩子未终结；不重做既有结果。 |
| `ACTION_FLOW_RULE_UNSUPPORTED` | 可能适用的机制、具体结果、parent disposition 或完整 reader 未登记；在接纳/继续消费前失败关闭。 |
| `ACTION_FLOW_FROZEN_DATA_INVALID` | 坏规则、来源/审计不一致、悬空或循环引用、非法状态图；使相应能力失效，不能自动修复或跳过。 |
| `REACTION_NOT_CURRENT` | 指定机会不是最小 PENDING 或其选择已决定；不允许后到请求抢顺序。 |
| `REACTION_OPTION_INVALID` | 不是该机会允许的具体选项，或当前条件不满足；不消费反应；合法失资格由明确推进记录。 |
| `REACTION_DEPTH_LIMIT` | 接受将超过活动反应深度；保留父窗口与原机会，不产生超限孩子。 |
| `ACTION_FLOW_LIMIT_EXCEEDED` | 新根/可选孩子的完整机会/阶段/帧/结构将超工程界；本次零 RNG/写入，保留既有前缀，不截断或默认 PASS；已接受必需后缀依预留完成。 |
| `ACTION_FLOW_RESERVATION_INSUFFICIENT` | 无法证明完整保存或必需收束额度；新义务产生前拒绝，既有 pending/额度不被删除。 |
| `ACTION_FLOW_IDEMPOTENCY_CONFLICT` | 同键不同规范意图；保持原结果，不重执行。 |

字段结构/候选选择错误先拒绝；合法子反应导致的父失能、目标消失、移动停止或被反制是
登记的业务结果，不用以上技术错误无限重试。提交未知、自动保存暂停与执行资格失效
继续使用运行生命周期的共同守卫，不另建 flow 专用自动恢复通道。

## 可审核验收矩阵与采用门

以下均为后续实现必须观测的独立预期，**不是本次已运行测试的结果**。每项须记录真实
producer → owner plan → consumer、状态/历史/文件映射、实际验证和仍关闭的分支，按
[责任清单](mechanism-integration-checklist.md)区分设计完整、内部可测、端到端和正式发布。

| 场景 | 独立预期 |
|---|---|
| 无候选原子路径 | 完整 reader 覆盖角色和怪物；同根攻击/移动/施法真实完成，不强造 pending 或额外成本。 |
| 普通领域子动作 | SPELL_CAST → 法术攻击 ATTACK 只有真实父施法成本，普通边不扣 reaction、不加深度；反应 cast 内的同类子攻击继承现有深度，不额外加一。 |
| 非动作型反应 | Protection 有真实 REACTION_EFFECT owner/结果和一次 reaction spend；父只消费该攻击的贡献一次，先决定再掷 d20；不造 cast、武器攻击或额外 action。 |
| 掷后攻击修正 | 原攻击骰审计固定、尚未判成败；ATTACK_ROLLED_BEFORE_OUTCOME 的反应自有资源/骰/贡献，父仅合成一次最终 total，再判命中并提供真正 Shield 窗口。 |
| 掷后伤害修正 | 原伤害骰/GWF 决定固定；DAMAGE_ROLLED_BEFORE_APPLY 的反应结清前无 HP/performed，贡献由 damage owner 一次消费；无候选则可与最后选择同事务完成。 |
| 坏规则/未知适用机制/末尾坏目标 | 首 RNG 前拒绝；零业务写入、零动作/资源/事件/版本变化，不能先执行合法目标或路径前半。 |
| 同一窗口多 actor、多选项 | 排序与连续 priority 独立一致；只有当前 actor 可选一个选项；后到 actor 请求被拒；PASS 不扣反应。 |
| 失资格与 Ready PASS | GET 零写；明确推进保存 INELIGIBLE 原因；PASS 不取消未到期 Ready，之后另一真实触发仍可产生机会。 |
| 反应恢复点 | 本人回合开始才恢复；跨 round、反应发生在本人回合及多个窗口均不额外送反应。 |
| OA 边界 | OA 看见离开前位置；反应伤害/失能后原移动合法停止，已走前缀/成本保留、后缀不重走。 |
| Ready move 嵌套 OA | trigger 已完成才 Ready move；子移动可再引出 OA，逐层返回同一父边界；双方 allowance、spent、反应与时间各只消费一次。 |
| Shield | 原 d20/total 不变；AC +5 适用于触发攻击；改变为 MISS 无伤害骰/packet；自然 20 不靠 AC 变 MISS。 |
| Counterspell 链 | 内层先结算；每个真实 cast 的 reaction/slot/材料按接受点一次消费；被反制父无效果、不退款或重掷；能力检定不自动加 PB；未闭合长施法分支明确拒绝。 |
| 本人 turn 反应施法 | 在真实同一 turn 双向核验 bonus action 施法限制；不按 round 封锁其他 turn，也不一概禁止本人 turn 的合法反应。 |
| Ready 法术准备/释放 | 准备阶段正常施放/反制并持有；释放不二次 cast 或消费 slot/material；专注已断无释放效果。 |
| GWF choice | 所有伤害骰前反应先结束；保全部原骰，可任意选未决定低骰，只重掷一次且使用新值；opening 不计 performed，最后决定先固定 retained dice，后置伤害反应完毕才一次最终伤害/完成。 |
| 普通多次攻击与移动 | 一击完成、flow 释放后可合法移动再攻击；sequence 原 limit/action 来源不重发；不能在某击 pending 时用下一击/forfeit 绕门。 |
| 深度/数量边界 | 根/普通阶段/choice 不计反应深度；第 8 层合法，第 9 层零成本稳定拒绝；超 128 机会或累计界不截断，原 pending 可准确查阅/保存。 |
| 关闭后继续与父重验 | CLOSED 窗口仍挡普通写入；变化的当前版本全重验，注册停止/失败可终结；不自动换目标或回放已执行前缀。 |
| 选择帧活动链 | 接纳后 DECIDED selection 仅为祖先；孩子终结、PASS/INELIGIBLE 都原子归还 owner 并换下一选择；CLOSED 的唯一叶子为 owner，不残留两个选择或不可推进叶子。 |
| 必需后缀结构额度 | 在上限前一项接纳可选孩子时计入全部父子必需窗口/帧；无法容纳就付费前拒绝，既有强制后继仍可完成；不得于已消费后才超界。 |
| CLOSING 与 N49 | clock/work/队列+CLOSING 原子提交且 N49；队列后项变更记 REPLAN_REQUIRED；必需 flow/choice 完成后只计 N50 一次，再触发保存屏障。 |
| 幂等与当前视图 | 原 WAITING accepted 在后续完成/清理后仍精确重放；当前视图另示现态/不可用；同键异意图、迟到 CAS 零 RNG。 |
| 故障注入 | 成本/原骰/帧/窗口/健康/版本/结果任一落库点失败无部分提交；已提交响应失败返回原事实；commit 未知只读查证并阻断新写。 |
| 中途 FINAL 与直接恢复 | 独立固定 fixture 覆盖嵌套反应、已走前缀、GWF 部分决定、CLOSED 未消费窗口与 CLOSING；逐字段源覆盖，恢复没有骰子/成本/时间/选择副作用。 |
| 坏文件与重启 | 改一父键/ordinal/成本/骰/顺序/spent/计步来源即拒绝；重启不 resume、不清 pending，仅按救援 END 和新运行恢复。 |
| 容量/清理闭包 | 最坏合法子链和全部终态实际费用有界且不超预留；FINAL/B 任一失败保完整工作集；真实入向引用和 owner 叶子同一清理事务，无半套删除。 |

公共协议已确定为正式设计输入。Shield、Counterspell 与 Ready 的有限内部采用以
[施法合同](../../rules/schema-2-spells.md)为准，场景类型化存储与直接恢复不代替完整文件及 Host 验收。
其余具体反应/选择仍须完成类型字段、
规范字节/独立向量、真实调用、SQL/锁/权限、持久预留、文件完整覆盖、故障及 Host 验收；
这些实施责任、持续效果的具体消费者及多目标结算合同的采用缺口不能因公共协议或目录模型存在而标为已支持。
发布仍须通过[完整 SRD 跨领域门](../../rules/srd-5.1-complete.md#6-完成判定)。
