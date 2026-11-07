# v2 动作暂停与继续协议

当前[有限 INTERNAL 战斗消费者](../../rules/schema-2-combat.md)
已采用普通近战、移动、机会攻击的真实 owner、类型化存储及直接恢复。
[有限 INTERNAL 施法与场景合同](../../rules/schema-2-spells.md)另以同一 flow 接通
Bless/Cure Wounds、Shield/Counterspell 真实反应链及封闭触发的 Ready attack／move／spell；
真实成本、原骰、父子帧、专注与领域后果随类型化场景历史直接恢复。施法资格限该合同的
生命领域一级角色与单职业 Wizard 1—5 级学习／准备模型。
本协议属于应用规则机制层，为各领域共享动作继续、窗口和结算时机。运行核心提供绑定、
权限、事务、幂等、预算与恢复保障。下文的目标协议以[焦点空间](world-and-scene-spaces.md#执行焦点与冻结)
的当前叶子推进。跨空间因果后继由同一流的 [CausalScope](world-and-scene-spaces.md#causal-scope)
当前叶子承载。各新增分支按[采用边界](adoption-boundaries.md)独立验收。
它遵循[模组接口基线](module-interface-baseline.md)、[机制接入责任清单](mechanism-integration-checklist.md)、
[运行与事务](runtime-and-transactions.md)、[保存与恢复](save-and-recovery.md)及
[玩家步骤](player-steps-and-auto-save.md)。legacy RELEASED v1、DRAFT 状态和 archive format 2
未激活的边界保持不变；本文不授权迁移、规则接受、数据库业务写入或部署。

已接窗口包括命中后伤害前、施法完成前和 Ready 的有限完成触发；OPEN/CLOSED 窗口仍阻断
新的根动作与主动结束专注，须先明确决策并继续同一父动作。当前 Counterspell 仅采用
当前真实法术位范围内的自动反制；四环以上能力检定的公共检查接入、Shield 的 Magic Missile
伤害 producer、长施法等分支仍须独立闭环，不能从类型登记或局部计算逻辑推断支持。

## 范围与共同不变量

每个空间至多有一个未终结的 `ActionFlowV1`，统一承载该空间内的攻击、移动、施法、反应
及必需选择。焦点空间的当前叶子具有继续资格。其他空间的原流、已付成本、决定与进度在
焦点切换时冻结；切回时以新焦点代次重验原叶子、权限和当前依赖。跨空间的被动后果使用
焦点流内的因果作用域及明确许可的决定入口；后台原流保持冻结，也不会另开可独立执行的根。
这里的 flow 是业务继续图，不是数据库事务；每次命令仍是一个短事务，等待用户期间不持锁。

- 完整预检先于 RNG、业务写入与成本消费。未知但可能适用的反应、效果或后续消费者阻断接纳，
  不能被过滤成“无候选”，也不能先扣成本再等待实现。
- 每个已接受成本、原骰、决定、已执行路径段和领域结果都有唯一业务身份。后继命令只执行
  尚未发生的准确后缀；重放、子反应返回、保存和恢复均不再次消费或补算。
- 每条流有一条活动祖先链，全战役同时至多一个可推进叶子。父帧停在已登记 checkpoint，
  子帧真实终结后才能返回父帧；已关闭但未消费的窗口继续阻断所在空间的普通游戏写入。
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
| Flow | 可移植 `flow_key`、所属战役与空间、准确规则绑定、协议版本、根帧键、当前叶帧键、`ACTIVE/COMPLETED`、创建与最后变化事件。运行中另校验物理运行、焦点代次、聚合 CAS 和预留。ACTIVE 恰有一条有效的活动祖先链；COMPLETED 没有未决子帧、窗口或 choice。 |
| Typed frame | `frame_key`、父帧键或根、`kind`、领域 `owner_ref`、调用原因 `ROOT/REACTION/CONTINUATION`、状态、准确阶段、已完成阶段前缀、创建事件及待返回 checkpoint；反应调用另引用原窗口/机会/选择。所有引用同一 flow，不得成环、跨运行或悬空。 |
| Checkpoint | 所属帧、连续 ordinal、应用登记的类型/版本、真实触发事实引用、`BEFORE/AFTER`、领域 continuation 类型、已保存意图和不可重做结果的引用、当前依赖闭包。每个 checkpoint 只消费一次，不靠自由字符串定位代码。 |
| Reaction window | `window_key`、父 owner 帧/checkpoint、确切触发事实、`OPEN/CLOSED/CONSUMED`、完整机会集合、当前机会位置、开启/关闭/消费事件；CLOSED 仍属于父等待状态，CONSUMED 才表示父已消费本次继续。 |
| Reaction opportunity | 窗口内唯一 actor、连续 priority、类型化选项集合及来源、触发时资格依据、`PENDING/PASSED/DECLARED/COMPLETED/INELIGIBLE`、决定事实、可空子帧；DECLARED 恰有一个真实反应子帧，未终结前不得处理下一个机会。 |
| Reaction selection frame | 确切窗口/机会、允许的有限 `reaction_kind` 和参数、`PENDING/DECIDED`、决定事件及可空被选子帧。选择一个反应或 PASS；不能以任意 action 名称把普通操作变为反应。 |
| Domain choice frame | `choice_key`、领域 owner、准确 choice 类型/版本和阶段 ordinal、决定主体与控制依据、`REQUIRED/OPTIONAL`、有序有限选项键及资格事实、原结果与候选引用、尚未决定集合、已决定前缀、`PENDING/COMPLETE`。领域值只在 owner 保存，帧引用它们。 |

帧 `kind` 封闭为 `ATTACK`、`CHECK`、`GRAPPLE_CONTEST`、`MOVEMENT`、`SPELL_CAST`、`READY_RELEASE`、`REACTION_EFFECT`、
`REACTION_SELECTION`、`DOMAIN_CHOICE`、`TURN_CLOSING`、`ONGOING_EFFECT_TRANSITION` 及 `SAVE_DAMAGE`。每种 owner_ref 分别指准确攻击
attempt、检定实例、擒抱或挣脱的对抗实例、移动计划、cast、已准备 action/move/held spell、类型化反应效果、窗口机会、领域
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
产生和保留，可独立恢复。可移植引用使用战役和空间键、稳定角色、生物、物品、过程键及
`(event_sequence, ordinal)` 等真实事实身份。物理运行标识、本地行 ID、请求收据和本地
CAS 控制由恢复映射重建。

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
| 领域 choice | 仅当前 choice 的获授权主体能提交有限选项键。复验后按该选项接受成本与有限骰，保存决定和结果。未完成则继续等待；完成时同事务消费父后继，不能让普通写入插入 choice 与父消费之间。 |
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
若同一因果窗有不同空间的合法机会，先按稳定空间键排序，各空间内部沿该空间的上述顺序；
不同战斗的 initiative ordinal 不互相比较。
仅最小未决定 priority 可被选择。
不按浏览器请求先后、不按数据库返回顺序抢反应；SRD 明确的不同 BEFORE/AFTER 时点
优先于此排序。同一事实多个登记阶段按应用固定阶段序，不能由模组加载顺序改时间线。

| 工程上限 | 处理 |
|---|---|
| 活动反应调用深度 8，根为 0 | 只有接受真实反应子动作才加一；待选 selection、普通攻击/移动阶段、checkpoint、GWF choice、长施法工作或普通子计划不增加。其孩子全部终结返回才减一。 |
| 每窗口最多 128 actor 机会 | 完整集合超限就拒绝产生窗口的接纳/继续命令，不取前 128 个，不把超出者自动 PASS。 |
| 每 owner 执行帧最多 64 checkpoint | ordinal/阶段总量在接纳前可证明；不能用重新建立同一 owner 帧规避。 |
| 每个 choice 最多 64 个有限选项、最多 64 项未决 ordinal | 必选时先证明每个可达停点至少有一个合法选项或具体领域终态；可选时必须预留明确放弃的终结路径。 |
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

焦点空间的 owner 帧 O 到达窗口 W 时，O 为 WAITING_WINDOW，新建 `parent=O` 的 selection S，
S 成为当前可推进叶子。接受反应后 S=DECIDED，真实孩子 C 的 `parent=S`，O=WAITING_CHILD；
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
| `ContinueIntent` | 准确运行、战役、焦点空间及焦点代次和 `flow_key`；因果叶子另含 `scopeId` 与准确目标主体/空间；请求幂等键；应用登记的 `operation_key={producer, subject, phase, ordinal}`；预期叶帧键和按操作必需的 window/opportunity/choice 键；登记的完整预期版本集合；封闭操作 `PASS/DECLARE_REACTION/ADVANCE_WINDOW/CONTINUE_OWNER/DECIDE_CHOICE/ADVANCE_CHOICE/RELEASE_GRAPPLE/CONTINUE_CLOSING` 及该操作专属类型化决定。无关分支字段不得出现，未知/重复/过量字段拒绝，不接收自由阶段或结果。 |
| Accepted | 原操作身份、接受根事件、当时 flow/owner/阶段、当时成本/骰子/决定与真实结果引用；只描述本次不可变接受事实。该结果的 schema/字节随具体操作定义，不混入可随时间变化的“现在可操作”令牌。 |
| `PendingView` | 当前运行资格/阻断原因；所属空间及焦点或冻结状态；准确 flow/叶子/owner kind/阶段及当前版本；当前 window/opportunity/choice、actor 与 priority；允许观察的原结果与余集。执行候选由焦点流按权限生成并关联当前焦点代次；因果叶子另给所属作用域、后台 actor 的准确受限选项和继续凭据，后台原流保持无候选。 |

外层 DTO 与字节界使用实际登记的更小边界并服从运行请求总界；语义中显式无值与缺失
分别校验，不用 null 接收任意分支。查询和 accepted 之间没有“自动跟随最新 flow”的
重定向关系；旧视图必须重新取得当前身份/版本后提交新的准确意图。

<a id="finite-domain-choice"></a>
### 有限领域选择

`DECIDE_CHOICE` 含 `choice_key/option_key`，分项选择另含原 `item_ordinal`。引擎由
当前叶子、原候选及当前权威事实确定选项语义。必选项只接受明确的领域值，例如擒抱
防守者在两项技能中选择；可选项有 `DECLINE`，按骰逐项决定时可有 `KEEP_REST`，
均保留截至选择点的原事实。放弃、保持、选技能均无骰及额外成本。选择使用骰子或资源时，
先验证来源、余额、时限、权限及父后缀，才接受已登记消费和本选项骰 manifest。反应另用
反应机会及真实反应成本，不能从 choice 入口取代。

已列选项失效时，旧选择请求以 `ACTION_CHOICE_OPTION_INVALID` 拒绝。经明确
`ADVANCE_CHOICE` 重验并写入失效原因，原可选机会已无使用选项时记录
`INELIGIBLE` 后继续；必选仍有合法原选项则保持等待，全部失效时只允许所属机制
规定的准确终态，例如对抗主体确证离场导致 attempt 失效。角色由谁控制变化只影响当前
提交权，不能代替其决定；DM 可凭真实权限提交同一类型化选项并留痕。未知或损坏事实
走完整性故障路径。超时、END、关闭反应窗、失去客户端连接均不能自动替选。

## 各领域 owner 的继续责任

| Owner | 必须保存与只执行一次的事实 | 返回时必须核验及处理 |
|---|---|---|
| 攻击与 AttackSequence | 动作来源、sequence/turn、唯一 attempt/attack ordinal、武器一击或擒抱替代分支及其真实审计和结果。BEGIN 消费 GENERAL action 一次，后继攻击/choice 不重新发放动作。 | 已掷阶段保原 audit，掷后贡献及防御按各自时点采样。真实失能或目标失效须有类型化终态及 sequence 收束；不能借关闭 sequence 丢弃已接受的替代或已掷一击。 |
| 检定与对抗 | 权威调用来源、`CheckStage` 阶段和 checkpoint、原骰、原依据、贡献/选择、公布结果及准确领域返回。对抗 owner 另存双方、技能选择、先行方、现状与结果。 | 消费原检定结果一次；两个对抗 total 结清前不公布胜负或写领域后果。暂停不产生第二次检定根。 |
| 移动 | 原路径/模式、派生边界、已执行前缀、当前位置、下一段/段内位置、累计 spent、剩余 allowance、触发消费标记及时间进度。联动时另存关系与双方位置证明。普通 turn MOVE 使用原 movement pool；Ready move 使用其真实反应许可和至多当前 speed 的单次 allowance。 | 从当前权威位置核验原后缀、速度/地形/阻挡、健康、敌意/感官和新触发。不能走则写合法 STOPPED 结果和原因，保前缀及成本；不回到旧位置，不自动换路/换模式/重走。 |
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

<a id="check-stages"></a>
### d20 检定与结果公布

执行 owner 为每次检定登记 `CheckPlan` 并以 `CheckStage` 连接掷骰、有限选择和真实后果。
稳定 `check_key=(parent_owner_ref,purpose,ordinal)` 绑定生物、调用原因、当前可执行
空间/turn 或探索 occurrence、目标或对手（若适用）、完整规则来源和真实后果 consumer。
攻击在原 ATTACK 内执行同一阶段；独立能力检定可为 CHECK 根；对抗、豁免、反制和
专注以原 owner 内联阶段或 CHECK 普通孩子继续。子结果类型与父返回点均由应用固定。

| 检定类型 | 权威输入与判定 |
|---|---|
| `ATTACK` | 攻击 owner 给出攻击来源、攻击加值、目标和 AC 来源；选中自然 1 必败，选中自然 20 命中并暴击，其他按合成 total 与 AC 判定。另有真实的扩展暴击规则时依该攻击规则处理。 |
| `ABILITY` | 活动 owner 给出属性及适用技能/工具或明确无技能、DC 及真实成功/失败后果；`total >= DC` 成功。反制是无技能的 `ABILITY`，用真实施法属性。 |
| `CONTEST_SIDE` | 对抗 owner 绑定同一对抗的两侧、各自属性与技能；一侧只形成已定 total，双方完成后比较，高者获胜，平局保现状。无虚构 DC 或自然 1/20 特例。 |
| `ABILITY_SAVE` | 效果 owner 给出属性、DC 和结果分支；健康/专注给出来自独立伤害的 CON save，环境、疾病、职业、装备和法术触发也提供各自真实输入。普通 save `total >= DC` 成功；特定条件可提供 `AUTO_FAILURE` 证明。 |
| `DEATH_SAVE` | 回合 START 且生命、0 HP、未稳定等条件通过，DC 10，无属性及默认 PB。选中自然 1 记两次失败，选中自然 20 走恢复 1 HP 的真实健康计划与恢复限制；其他按 total 一次成功/失败。第三次成败、恢复及重置由健康 owner 消费。 |

死亡豁免可提供真实的非动作贡献选择；仅禁止行动或反应的状态不会单独关闭该选择。

1. `D20_BEFORE_ROLL`：确认准确主体和依赖，完成掷前可选贡献、反应与必需选择后，由
   检定 owner 冻结掷骰依据。依据记录属性/熟练及其来源、固定贡献、已接受的掷前贡献骰、
   优势和劣势全集及其抵消证明、自动贡献骰 manifest。不能按同一来源重复增加熟练。
   真实自动失败跳过所有骰阶段并记录条件证明。
2. `D20_ROLL`：骰核按 ordinal 抽一颗 d20，只有不抵消的优势/劣势抽两颗并取高/低，
   同点取较早 ordinal。随后按稳定规则身份、来源事实与 die ordinal 顺序抽已接纳的
   自动贡献骰。保存每颗原值、被选 d20、原 basis 和原 total；此时没有已公布的结果。
3. `D20_ROLLED_BEFORE_OUTCOME`：原骰不可变。先按用途、来源事实和稳定选项顺序处理
   掷骰者的可选贡献，再处理外部掷后反应窗口。一次贡献决定保存准确 check、来源、时机、
   消耗与独立骰；同一机会不会因子动作返回再次提供。原授权结果可以按权限展示，成功/
   失败、最终命中及对抗输赢均在此阶段后公布，客户端无输入可宣判。原生效性和原骰
   不由后来的位移、失效来源或新属性反算；后缀只重读对应阶段允许变化的依赖。
4. `D20_PUBLISH_OUTCOME` → `CONSUME_OUTCOME`：一次合成最终 total 及明确结果类型，
   以不可变发布事实进入原领域 consumer。攻击先发布能触发 Shield 的初步命中事实，
   最终命中见[攻击段](#攻击shield-和骰子选择)；对抗双方 total 按发起方再应对方处理，
   胜负由对抗 owner 一次公布。已真实接受结果无法结算时仅按预定终态记录真实原因，
   保留此前原骰和成本。恢复从已保存 phase/ordinal 继续，不补做发布或后果。

掷前窗和掷后窗只对实际适用类型开设。同一攻击的 `ATTACK_BEFORE_ROLL`、
`ATTACK_ROLLED_BEFORE_OUTCOME` 分别实现上述两个通用阶段；能力检定与各种豁免使用
同样时点。单 check 最多 32 个来源贡献，其中最多 8 颗贡献骰；需要证明所有已接纳孩子、
骰 manifest 和领域余集均在更小的实际规则及总预算以内。主张一种贡献时须有真实来源。

| 贡献 | 时点、资格与真实消费 |
|---|---|
| Bardic Inspiration | 对攻击、能力检定（含对抗/反制）或 saving throw（含专注和死亡）在掷后提供 `USE_INSPIRATION/DECLINE`。若 owner 在原意图已有明确掷前使用选择，也可在真实掷前点接受；未作掷前选择时直接到 d20。一次 USE 抽授予的骰并消耗效果，无反应或第二次 Bard 次数。放弃保留至原期限；到期或终止已消失时不补抽。死亡自然骰特例不被加值改写。 |
| Bless | 有效来源对攻击与 saving throw 的自动 d4，随原 roll manifest 保存。普通能力检定和 Counterspell 不适用。 |
| Guidance / Resistance | 分别仅作用于能力检定／save。掷前放弃可保留原有效实例供掷后选择。接受使用即在选择点掷独立 d4、消费其应用并结束来源；若在掷前接受，则 d4 事实先于 d20，保存为原 basis 的已接受贡献。已接受的贡献不因自身结束而撤销。 |
| Protection / Cutting Words | 前者在合法攻击掷前施加劣势。后者只对攻击、能力检定或伤害有真实反应窗口，按准确视距、听觉、charmed 免疫、反应及 Bard 次数验证；掷后选用一次扣各自费用并掷一个来源骰。不能从普通 save、专注或死亡 save 减值。 |
| Jack of All Trades / Peerless Skill | 前者以实际 Bard 特性为依据，只在能力检定尚未含该角色 PB 时提供掷前 `APPLY/DECLINE`，采用则加入向下取整的半 PB，无骰或额外成本。后者为符合等级的 Lore Bard 本人能力检定提供掷后选择，一次消费自己的 Bardic Inspiration 次数与真实骰，无反应。两者均可适用于合资格的反制检定。 |

授予灵感的真实动作在 Bard 自己的 turn，验证 60 尺内另一名能听见自己的生物、准确听觉
证据、空的持有位置、现有次数与 bonus action。接受后一次扣该 bonus action 和一次特性
用量，零骰；来源保存 Bard、受领者、授予事件、固定骰阶（d6，5/10/15 级变为 d8/d10/d12）
及受领者的 `PERSONAL_ELAPSED(start+600)`。最大用量为 `max(1,CHA修正)`，恢复由实际
休息与 Font of Inspiration 特性决定（5 级起短休或长休，之前长休）。两个空间不补时。
使用意图绑定准确授予实例，消费后历史仍保留。已有灵感或非法距离／听觉时授予零成本。

阶段顺序是项目选择。语义依据见 [Bardic Inspiration 与 Cutting Words](../../semantics/srd-5.1/classes-barbarian-bard.md)、
[检定与豁免](../../semantics/srd-5.1/abilities.md)和 [死亡豁免](../../semantics/srd-5.1/combat.md#srd51-core-death-saving-throws)。
消费点、暂存结果的权限与同一次判定的公开边界不因手工裁定入口或使用同一骰型而省略。

### 攻击、Shield 和骰子选择

`ATTACK_BEFORE_ROLL` 先结清影响本次攻击骰的合法反应，再用真实 `CheckPlan` 掷一次攻击。
Protection 用 REACTION_EFFECT 子帧一次消费反应并登记该攻击的 disadvantage，返回后由父
CheckPlan 合成真实 adv/disadv；不能先掷 d20 再补 Protection，也不需要假 cast/attack。
随后先经过 `ATTACK_ROLLED_BEFORE_OUTCOME`：原 d20/selected/原 total 审计不改，尚未判定
命中；Cutting Words 类掷后反应以真实来源、自己的资源/骰 audit 和类型化贡献单独记录，
原攻击 owner 只消费一次这些贡献形成此阶段有效 check total/初步判定，不能改写原骰、重掷或
接收临时任意 modifier。主体的掷后选择及外部反应全部结清后才公布能触发命中后窗口的
判定；同类时点由上节 `CheckStage` 统一控制。具体 reader/consumer 不完整的适用分支
仍在原接纳门阻断。
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

所有命中前及此命中后反应结清，且得到最终命中后，进入命中附加选择。固定顺序为
Sneak Attack → Divine Smite → 完整伤害骰 → GWF → 骰后减项 → 实际伤害。
这是项目采用的结算顺序，尤其不让 Smite 在 Shield 改判前消费。

<a id="hit-riders"></a>
#### 命中附加伤害

| 附加 / 真实 owner | 固定资格与决定 | 成本及伤害 |
|---|---|---|
| Sneak Attack / 特性用量 | 最终命中 creature，武器确为 finesse 或 ranged。采用已掷攻击的净优势，或其无净劣势且空间证明目标 5 尺内有另一名未失能的目标敌人。读取准确 turn identity 和既有用量；选择 `ADD_SNEAK_ATTACK/DECLINE`。 | 接受 ADD 写入唯一 `(actor,feature,combat,turn)` 用量和附加骰规格 `ceil(rogueLevel/2)d6`，引用原武器主伤害分量类型；放弃不占用额度。反应发生在哪个 turn 就使用其额度，不按 round 或本人 turn 归并。 |
| Divine Smite / 特性与法术位 | 至少 Paladin 2 级的有效特性，对 creature 的最终近战武器命中。以当前真实法术位池、可支用类型及 rank 生成有限 `SPEND_SLOT(poolKey,rank)/DECLINE`；以最终命中时的目标 creature type 证明不死／邪魔加骰。 | 接受准确池和环级一次支出；光耀 `min(rank+1,5)d8`，不死或邪魔再加 `1d8`。这是特性支出，不产生 cast、action、reaction、组件或 Counterspell。放弃或 Shield 改判时不消费位。 |

选择帧引用最终命中事实、attack ordinal、武器、turn、空间或目标类型依据及尚未接受的
消费。接受时复验当前资格和后继预留；任何迟到、伪造或余额失效的选项都不扣资源或抽骰。
各附加分量保留其独立来源；暴击分别加倍该次攻击各分量的骰数，固定修正只计一次。
伤害 manifest 依武器、Sneak Attack、Divine Smite 顺序，分量内骰 ordinal 递增；同一
最终命中只有一份伤害应用及伤害后义务。物理与光耀分别按真实防御范围处理，同类型且
适用标签相同的分量先合计再按其抗性取整。Health owner 接受同一应用的合计实伤与分量证明，
按[伤害交接](multi-target-save-damage.md#typed-damage-application)提交，不以骰数或分量数重复扣 HP 或检定专注。

若有合法 GWF 低骰，建立
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

<a id="grapple-attempt"></a>
### 擒抱替代、挣脱与释放

`AttackSequence` 的有效额度由同一 Attack action 的真实来源和 Extra Attack 决定；
该 Fighter 5 级序列额度为 2。owner 保留 `sequence_key,combat,turn,action_spend_ref,
granted,performed,pending_attempt_ref` 和各已消费 ordinal。真实序列的下一 ordinal
只接受 `WEAPON_ATTACK` 或 `GRAPPLE_REPLACEMENT` 之一。机会攻击、Ready 的单击许可、
怪物 Multiattack 或法术攻击不因拥有序列字段而得到此替代额度。

1. 擒抱在当前序列、合法 actor/target、同空间、触及、目标至多大一级、准确空手位和全部
   对抗/条件/移动/释放消费者通过预检后，以 `GRAPPLE_CONTEST` 接受下一 ordinal 的
   唯一 attempt。只有一个可执行替代占有该 ordinal；已接受替代必以成功、平局、失败或
   主体失效结果消费该 ordinal。pending 尚未计入 performed，终结才计一次。无额外
   action、武器成本、攻击 d20、AC、命中、暴击、Shield 或伤害包。
2. 保存 `contest_key,mode=ESTABLISH,sequence/ordinal,actor,target,hand_slot,
   reach_and_size_proof`。目标必须用 `DOMAIN_CHOICE` 选择
   `STR_ATHLETICS/DEX_ACROBATICS`，发起方固定 STR(Athletics)；选技能无额外骰或成本。
   双方按先发起方后应对方执行各自的 `CONTEST_SIDE`，待双方完成才公布比较。
3. 发起方 total 严格大于对方才写关系 `grapple_key,grappler,target,hand_slot,
   originating_contest,reach_basis,status,ended_reason`，持续效果/条件 owner 产生唯一
   来源的 Grappled 及 `UNTIL_GRAPPLE_END` 终止依据。它占用原手位，效果使受擒者
   speed 为零且不能受速度加值。平局或失败维持原关系，零新条件。已有同一来源/目标的
   活跃关系禁止重复创建；不同有效擒抱来源独立，结束一条不会清除其他来源。
4. `ESCAPE_GRAPPLE` 为受擒者自己合法 action，需绑定准确活动关系并接受一次 action
   spend；受擒者选同样的两项技能，对方固定 STR(Athletics)。比较高者胜，只有挣脱方
   获胜才结束准确关系。平局/失败保持关系并保留已用 action；逃脱前被规则结束时记
   `RELATION_ALREADY_ENDED`，既有成本保留、未做检定不补骰。

主动 `RELEASE_GRAPPLE` 仅由擒抱者获授权控制者发出，指定准确关系、版本、焦点，若有
活动流则另给准确当前叶子。在焦点场景的任意已提交停点开放，包括对方回合和窗口、choice 等待；
同一命令内无可插入的中间写点。接纳不消费 action、reaction、movement、时间或骰，不代
当前叶子作决定。关系结束、手位释放、条件源结束及真实派生事实由同一事务应用。原窗口
机会只会依原触发资格失效；已冻结的骰依据、原骰、已付成本及选择不倒改。后续未执行
阶段以新当前事实重验，若原联动关系被释放，移动仅保留已走前缀并停止联动后缀；不自动
转为单人新路线。此写门例外只允许该真实释放及其确定后果，预检必须覆盖后继失效和
收束，未知后果在原分支接纳门关闭。擒抱者失能/死亡、有效主体移除或权威位置使对象超出
触及均由对应领域边界在同一事务结束该关系，不收第二笔释放成本。原文依据见
[擒抱](../../semantics/srd-5.1/combat.md#srd51-core-grappling)和
[Grappled](../../semantics/srd-5.1/conditions.md#srd51-condition-grappled)。

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

<a id="coupled-move"></a>
#### 擒抱拖带的联动步段

移动 owner 按[联动几何](spatial-and-area-contract.md#coupled-move)向空间 owner 提交
`CoupledMoveRequest`，含原移动与准确 segment ordinal、活动关系、grappler/carried
稳定主体及当前占位、原完整路径与各段有限拖带意图、空间和关系依赖、体型、运动方式与
携带资格。空间 owner 返回 `CoupledMoveResult`，以 `SpatialProof` 证明双方原位、
两者扫过空间、终位、路径中的实际触及和 BEFORE/AFTER 边界；不得一方已移动才检另一方。
移动 owner 单独派生本段主移动者可用速度（被拖者至少小两级则不减，否则减半）、地形
成本与累计 spent。被拖者不消费自己的 movement、action 或 reaction。

每段先结算主移动者在原位的真实离开前 OA，再复验同一关系、两方占位与完整后缀。合法
时同事务写入双方位置和本段 spent，并产生双方的实际位置变化事实；由区域 owner 处理
真实 after-enter 等后果。被拖者没有用自己的 movement/action/reaction，故不因被拖过
触及边界产生针对自己的 OA；真实进入触发仍读取它的位置事实。子反应使一方失能、离位、
断开关系或阻挡一方，且令配对后缀失效时，记录 `MOVEMENT_STOPPED` 和准确原因，保留
真实前缀；一个成员的段写入失败时整段回滚。待续保存双方原始意图、空间证明、已提交
双位置前缀、spent、待消费触发及准确剩余段。恢复只写这些事实，不自动补走一方。

普通 turn 内移动不另推进时间；探索 MOVE 原定一次六秒只由时间 owner 按登记计划记一次，
不因每段、暂停、反应、恢复或最终继续重复增加。需要中途时间分配或危险区域消费者却
缺真实计划时阻断该分支，本协议不补造沿路径的均匀时间表。

### Counterspell 与准备法术

<a id="counterspell-check"></a>
`CAST_BEFORE_COMPLETION` 保存准确目标 cast、阶段/工作 ordinal、已接受成本和真实施法
可观察事实，效果尚未执行。候选 reader 用[空间证明](spatial-and-area-contract.md#visibility)
读取双方权威位置、从反应者到正在施法者的可见性、clear path 和 60 feet 内距离，并核
真正可见的正在施法依据。普通窗口核同一空间；因果域中的跨空间见证还须有明确 scope
参与资格与同物理框架证明。光看见生物或看见已产生的效果不等于目击施法。
这条可观察事实政策为项目解释；有效环级、选择的法术位及其他未公开规则事实由服务端
计算，不作为默认的观察者提示内容。反应接受时重验同一在施 cast、阶段、空间、组件、
真实施法资格、可用 reaction 和 slot；空间不明、过期、超距或遮蔽等不得冒充肯定证明。

反制以真实反应 cast 执行，自己也可被合法反制，按真实反应边计深度。孩子先解并返回；
子反制自身被反制时，不针对原目标再判环级或掷骰。自己的窗口结清且目标仍处于准确
可反制阶段时，比对真实有效环级（升环用实际位环，仪式用基础环，物品等其他授权用
其准确有效环级）。反制环级达到目标环级时自动成功，零检定骰。否则走
[`CheckStage`](#check-stages) 的 `ABILITY`，用该次反制的真实施法授权的能力，
DC=`10+目标有效环级`。无关联技能、无默认 PB。适用时可有 Bardic Inspiration、
Guidance、半熟练等真实能力检定贡献及 Cutting Words；Bless 不在该检定中消费。
检定未公布时可保存其原骰、后续选项及原反应调用链。成功时标记准确目标 cast 的该工作
或声明已被反制；父 owner 仍在原窗口结清后一次消费准确返回点。若失败，父按真实剩余
候选与未执行后缀继续。已被其他孩子结束或真实离开本 checkpoint 时保存该次反制的
`TARGET_CAST_UNAVAILABLE` 结果，保留已付成本，不改指其他 cast。

普通瞬时施法在真实 DECLARE 已付成本后开放该窗口。反制成功使目标 `COUNTERED`，
不产生法术效果，原 action、slot、材料均按已接受事实保留。长施法由
[工作 owner](spellcasting-extension-contract.md#long-work) 发出同一类型窗口，作用点为
每个实际可观察工作段，身份 `(cast_key,work_ordinal)`。START 是第一段。开放时已有本段
应付 action 的接受事实，本段工作时长与信用尚未推进，最终 DECLARE 应付 slot/材料尚未
消费。反制成功记录 `INTERRUPTED_BEFORE_DECLARE`、释放本次 CASTING holder，保留之前
工作和本段已付 action；不执行本段待推进的工作时间/信用或 DECLARE。若无人成功反制，
只消费本段准确 continuation；最后一段工作完成后进入 DECLARE，按施法 owner 重验并
消费尚未支付成本，不为同一最后工作段重开第二个窗口。保存保留该工作段的准确已付和
未付边界，既不因长施法反制退还已付成本，也不因它有施法意图而提前扣费。

预备一 action 法术在“准备时的正常施放”可有此窗口；成功后由 Ready/专注 owner 持有，
释放只是已有准备结果的真实消费，没有第二次施法声明。这是依据原文“准备时 cast、随后
release”的协议解释。见[反制原文](../../references/srd-5.1/pages/page-131.md)与
[施法时间原文](../../references/srd-5.1/pages/page-101.md)。
长期 CASTING 或尚待未来触发的合法 Ready 不持续占住活动 flow；它们保留在领域状态中，
真正的工作/声明/触发才重新经过共同守卫建立 flow 或成为当前 flow 的领域孩子。GET 或
定时器不授予继续资格。

反应施法同样使用真实 turn 的完整施法历史：本人 turn 已施放 bonus action 法术时，不能
再用反应施放另一个不符合该限制的法术；先有此类反应施法，也不能随后用 bonus action
施法绕过同一 turn 限制。其他生物 turn 的反应按那个 turn 核验，不能按 round 泛禁。

## 接纳、写门、版本与幂等

沿[运行事务](runtime-and-transactions.md)先取应用/运行独占门，再取得私有库单写事务并读取
完整领域闭包；Repository 使用调用方连接。新命令先核准确意图和幂等事实，再验证本次
运行资格、焦点与代次、准确 flow/叶子/窗口/choice、全部当前版本、领域条件、预算与随机性上限，之后
才能 RNG 或写入。客户端预期版本只是待验证输入，不能指定阶段、骰点、派生修正或版本推进。

每个新根/继续命令以一个全局根事件记录真实状态、成本、原骰/决定、领域正文、活动链、
所影响聚合各一次版本推进、预留变化及唯一请求接受结果，原子提交。无变化的聚合不加版，
回滚不推进事件尾或 CAS。阶段内操作先后顺序不产生多个可漂移的版本源。
父先前 CAS 不能授权新后继；子帧返回后必须使用当前 flow/owner/所有受影响对象版本。

焦点空间有 ACTIVE flow 时，游戏写入仅允许当前叶子上的已登记选择/反应/推进，以及该阶段明确允许
的有限领域操作；普通移动、下一击、装备、学习、休息、时间推进、步骤完成或模式交接
不能绕过等待。子效果的健康/条件变化由其真实事务内计划消费，不以另一个普通入口插队。
因果域当前叶子可按准确权限提供后台参与者的选择或反应。该入口只消费本因果域的后继，
不推进后台原 flow、时间或 turn；不能以普通后台动作或自动 PASS 代替。
读取、精确重放、保存、技术 END 和显式焦点切换按各自权限及守卫执行。切换以原子提交
改变焦点代次，未终结动作保持冻结原态；切回后旧候选和迟到命令按过期代次拒绝。
正在执行的跨空间 scope 须先完成当前因果义务，才允许切换焦点。
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
已完成前缀、固定 finish 队列及预留；不创建第二个并行 bottom。尚须完成前窗口的工作段
仍属于队头待结后缀，不能预记本段工作时间/信用。一次只处理准确队头 cast/回调，其反应
与 choice 都是同一 flow 的孩子。

队列前项的反应改变后项资格/版本时，用独立无 RNG 事务记录 `REPLAN_REQUIRED`；后续明确
重计划命令只替换尚未发生后缀，保留原队列位置、work/action 来源和 superseded 计划。
已 DECLARED 的 cast 不重扣或退款，已执行时钟/work、既有骰子和历史不改写。
无合法替代时由已登记放弃/中断终态收束，不能自动换目标或靠旧计划继续。

全部必需结束回调、finish 项、窗口及 choice 终结后，才写 turn 完成、必要 round 关闭和
PC 的唯一 step completion；NPC 回合与回合外反应不计玩家步。
业务计步与保存水位分离。任何已提交的 CLOSING/反应后缀都可完整保存；若命令 revision
到达自动保存阈值，先保存该准确 pending，再允许后续受保护继续，不强行完成回合来保存。
合法跨步的专注、Ready、持续效果或长施法状态本身不要求自动结束；完整 owner 完成判据
仍是前提；持续效果的到期、压制与结束义务按[持续效果协议](ongoing-effect-protocol.md)接合。

每次产生 pending 前，预算 owner 必须证明完整变化后可导出，并持久保留从当前状态到
所有允许终态的有限收束额度：真实子反应/选择、已掷未完成一击、窗口关闭、父重验失效、
停止/失败、历史/版本/结果及 CLOSING 全部纳入。只给 top window 或每帧固定若干字节不够。
上限只证明有限，不代替准确结构/字节/节点/数组与 RNG 的计算。战役预算计入焦点和后台
各空间已接受的收束额度，切换焦点保留冻结流的预留，恢复时按完整业务图重建。

CASTING、FLOW、TURN_FINISH 等义务按同一语义节点去重并在真实状态迁移时原子移交；
不能分别给父子计入同一健康变化，也不能释放父预留后才尝试为必需孩子申请空间。
后继在首 RNG 前重建完整计划并核实际费用不超剩余额度；当前免费空间不足不能丢弃已接受
收束义务。失败/未知提交不释放额度，只有真实终态和读回闭包匹配才结算。
完整保存及后续自动便携检查点的峰值遵循[保存预算](save-and-recovery.md#容量验证与灾备)，
不得借用最终保存预留维持普通 flow，也不预留无限未来回合或未决定的可选新攻击。
真实 SQL、物理峰值和容量实证未完成前保持相应采用门关闭。

## 保存、直接恢复与技术 END

| 数据 | 文件与恢复责任 |
|---|---|
| 空间焦点、因果域与全部活动流 | 保存焦点及其历史，各空间全部活动或冻结的 flow、帧、checkpoint、窗口、机会和 choice，以及未决因果域闭包、调度叶子、真实前缀和冻结原流的关联。保留可移植身份、类型与版本、状态、父子和返回关系、准确候选顺序、决定与消费事实及各自活动祖先链。 |
| 领域意图、原骰和进度 | owner 保存原 d20/依据、逐来源决定与消费、未公布或已公布结果和领域应用点。包括原攻击 audit/最终防御、命中附加及用量、准确序列 ordinal/技能/双方对抗/活动擒抱、双方已走前缀/剩余段/spent/触发、cast 已付或待付成本和工作 ordinal、Ready、CLOSING 原计划及全部业务历史。 |
| 本地控制 | 物理运行标识、物理外键、本地 CAS、进程资格、请求收据、保存技术水位和本地预算事实排除于文件。新运行按已验证业务图重建本地身份、当前执行前置和全部收束预留。 |

已终结的帧、窗口、机会和决定凡为完整历史或现有因果引用所需，也必须随文件保存并
直接恢复，不能只留活动链而使原骰/反应来源悬空；恢复这些历史不重新激活或执行它们。

严格 reader 独立验证：战役与空间归属、完整规则引用、类型/状态合法组合、父子无环、
每空间未终结原流与全局可推进叶子的唯一性、因果域继续权限与后台原流冻结、
机会顺序与子反应来源、反应使用恢复点、成本消费与真实事件、骰子和决定余集、路径前缀与
当前位置/spent、窗口消费次数、领域终态/历史和 CLOSING 计步一致性。已接受前缀保留，
未执行后缀按类型解析；不能重新运行事件来判断/补做已执行部分。缺分区或 reader 不支持
直接拒绝，空数组不代替尚未实现的状态。

writer/reader 往返自洽不足以验收，须独立逐字段比较源覆盖和恢复投影。独立恢复原样写入
已验证业务状态/历史，重建本地映射和当前依赖版本；不重掷、不再扣资源、不代选、不关闭
窗口、不走剩余路径、不补回合/时间/计步。新运行明确提交、首份检查点成功并发布执行视图
后，才可用新的受保护意图、焦点代次与 CAS 继续焦点空间的原业务叶子。

技术 END 只排空在途数据库事务，按最新完整源保存 OPEN/CLOSING/选择中状态；不隐含
PASS、KEEP_REST、resolve、退款、退出战斗或停止专注。按[保存 P0—P3](save-and-recovery.md)
发布/接受 FINAL 并封存；完整图用于证明文件覆盖与恢复等价。关闭后以整个私有目录隔离、
可重入清理，部分文件删除不承诺事务回滚；未封存不得删源，也不逐领域删除 flow。
请求收据随本地运行保留，清理后旧 run 失效；正式文件不随运行删除。

重启不自动执行 flow；同版残留在独占查证/完整校验后可显式继续原 run 并更新安全代次，
也可救援保存或精确放弃。文件恢复另建新运行。GET、诊断、下载、停机、查证和重放不推进业务。

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
| `ACTION_CHOICE_OPTION_INVALID` | 决定者、选项、分项 ordinal、来源或当前资格与该 choice 不符；不扣费、掷骰或默认代选。 |
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

<a id="check-and-attack-vectors"></a>
### 检定、攻击替代与附加的纸面向量

| 向量 | 正常、例外及可恢复事实 |
|---|---|
| V01 原攻击及其他检定 | 5 级 Bard 在场景秒 100 授予 d8，次数 3→2，持续的对应截止点为场景秒 700。攻击 AC 15，d20=10 与 +4，或 DC 15 的普通能力、DEX、实际承伤 30 的专注，均可在 d8 选择前保存原 14；恢复仍未发布，d8=3 后 17 命中或成功。对抗方 total 16 时原 14 会败而 17 获胜，双方完成才公布输赢。Decline 零新骰并保留有效灵感；越权、无来源或过期版本零新骰与写入。 |
| V01 死亡豁免 | 0 HP 仍活且未稳定，START 掷 8；有有效 d8 灵感，选择 3 后 total=11，写一次成功。原骰若为自然 1，贡献不能改变两次失败；自然 20 仍走恢复 1 HP 的健康分支。保存于选择前仍无死亡次数变化，恢复后仅公布一次。 |
| V02 Sneak Attack | 5 级 Rogue 刺剑优势选 20；武器暴击 `2d8=4+5`，固定 +3，加倍特性骰 `6d6=1+2+3+4+5+6`，共 33 穿刺。一 turn 第二次命中无选项，另一个 turn 的合法机会攻击重新检查。保存于命中后保武器/turn/原骰及选项，不另发额度；同键重发不再消费用量，失效候选不追加骰。 |
| V03 Divine Smite | 最终命中不死生物，2 环位从 1 到 0，武器挥砍 8 加 `4d8=[2,4,6,8]` 光耀 20，合计 28，只有挥砍抗性则 24；原总值 15、AC 14，Shield 提为 19 后 MISS，无选位或附加骰。选位前恢复保最终命中，不扣位；选位后同键只重放。过期位版本/非法池零骰扣费，接受事务内伤害写失败回滚同命令的扣位、骰事实和 HP。 |
| V04 擒抱与拖带 | Fighter 5 级的第一 ordinal 上 `11+5=16 > 10+4=14`，建立关系且第二击仍在；初次 17 平局仍消费该 ordinal 而无 Grappled。后来挣脱 14 平局消耗其 action 保持关系。保存于必选技能点只恢复原 ordinal 及选项；无空手、超触及或过期版本在新接纳前拒绝。拖带两位置同段提交，注入第二端写失败两端及 spent 同回滚；等待中无动作释放后停原联动后缀，不重复结算旧对抗。 |

以下继续列出共享流的独立采用预期。

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
| Counterspell 链及工作中断 | 内层先结算；每个真实 cast 的反应及其他成本按接受点一次消费；同或低环自动零骰，高环用真实施法能力走共享检定；未观察施法或超距无机会；长施法工作窗保留已付 action 和历史信用，中断不消费该段尚未发生的信用和 DECLARE 成本。 |
| 擒抱替代、挣脱和释放 | 必选技能前可保存原 ordinal；初次平局不建立条件，挣脱平局不结束；双方都可应用合法检定贡献。拖带成对提交，被拖者不耗 movement，不触发针对自己的 OA；等待时无动作释放只改准确关系，已发生前缀保留。 |
| 掷后保存与命中附加 | d20 已保存且结果尚未公布时恢复原选择和骰；Sneak 在同一 turn 只追加一次，另一个 turn 可再次追加；Smite 只在最终命中后选位，暴击加倍附加骰、固定修正不重复，Shield 改判不出现附加选择。 |
| 本人 turn 反应施法 | 在真实同一 turn 双向核验 bonus action 施法限制；不按 round 封锁其他 turn，也不一概禁止本人 turn 的合法反应。 |
| Ready 法术准备/释放 | 准备阶段正常施放/反制并持有；释放不二次 cast 或消费 slot/material；专注已断无释放效果。 |
| GWF choice | 所有伤害骰前反应先结束；保全部原骰，可任意选未决定低骰，只重掷一次且使用新值；opening 不计 performed，最后决定先固定 retained dice，后置伤害反应完毕才一次最终伤害/完成。 |
| 普通多次攻击与移动 | 一击完成、flow 释放后可合法移动再攻击；sequence 原 limit/action 来源不重发；不能在某击 pending 时用下一击/forfeit 绕门。 |
| 深度/数量边界 | 根/普通阶段/choice 不计反应深度；第 8 层合法，第 9 层零成本稳定拒绝；超 128 机会或累计界不截断，原 pending 可准确查阅/保存。 |
| 关闭后继续与父重验 | CLOSED 窗口仍挡普通写入；变化的当前版本全重验，注册停止/失败可终结；不自动换目标或回放已执行前缀。 |
| 选择帧活动链 | 接纳后 DECIDED selection 仅为祖先；孩子终结、PASS/INELIGIBLE 都原子归还 owner 并换下一选择；CLOSED 的唯一叶子为 owner，不残留两个选择或不可推进叶子。 |
| 必需后缀结构额度 | 在上限前一项接纳可选孩子时计入全部父子必需窗口/帧；无法容纳就付费前拒绝，既有强制后继仍可完成；不得于已消费后才超界。 |
| CLOSING 与保存阈值 | clock/work/队列+CLOSING 原子提交；到 revision 阈值可原样保存 pending；后项变更记 REPLAN_REQUIRED，必需 flow/choice 完成后才计业务步一次。 |
| 幂等与当前视图 | 运行存续期原 WAITING accepted 在后续完成后仍精确重放；目录清理后旧 run 不可用；当前视图另示现态，同键异意图、迟到 CAS 零 RNG。 |
| 故障注入 | 成本/原骰/帧/窗口/健康/版本/结果任一落库点失败无部分提交；已提交响应失败返回原事实；commit 未知只读查证并阻断新写。 |
| 焦点切换、冻结与恢复 | A 有未决叶子时切到 B，B 可接受并保留自己的未决流。核 A 时钟和已付成本/原骰/继续点保持冻结、过期焦点请求零 RNG。中途 FINAL 保存两空间，直接打开并切回 A 后只继续 A 的原后缀。 |
| 中途 FINAL 与直接恢复 | 独立固定 fixture 覆盖嵌套反应、已走前缀、GWF 部分决定、CLOSED 未消费窗口与 CLOSING；逐字段源覆盖，恢复没有骰子/成本/时间/选择副作用。 |
| 坏文件与重启 | 改一父键/ordinal/成本/骰/顺序/spent/计步来源即拒绝；重启不自动 resume、不清 pending；完整查证/校验后显式恢复本地运行或从文件另开。 |
| 容量/清理闭包 | 最坏合法子链和全部终态实际费用有界且不超预留；封存前失败保完整工作集；封存后目录清理可重入；真实入向引用和 owner 叶子进入同一完整投影；部分目录残留不得激活。 |

公共协议已确定为正式设计输入。Shield、Counterspell 与 Ready 的有限内部采用以
[施法合同](../../rules/schema-2-spells.md)为准，场景类型化存储与直接恢复不代替完整文件及 Host 验收。
其余具体反应/选择仍须完成类型字段、
规范字节/独立向量、真实调用、SQL/锁/权限、持久预留、文件完整覆盖、故障及 Host 验收；
这些实施责任、持续效果的具体消费者及多目标结算合同的采用缺口不能因公共协议或目录模型存在而标为已支持。
发布仍须通过[完整 SRD 跨领域门](../../rules/srd-5.1-complete.md#6-完成判定)。
