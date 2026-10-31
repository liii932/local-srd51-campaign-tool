# v2 持续效果生效、压制、恢复贡献与结束协议

状态：公共生命周期、责任、时序和验收合同已确定；[有限 INTERNAL 施法与场景](../../rules/schema-2-spells.md)
已采用 Bless 的真实来源、固定目标、攻击/CON/死亡豁免贡献、专注/到期与同强 SHADOWED 恢复，
Shield 的个人回合期限、Bless／Ready spell 的唯一专注 holder，以及固定 DM 抗魔区域对
Bless／Shield 目标应用的独立多原因 SUPPRESSED。场景 owner 保留战外效果、原因和类型化历史／直接恢复。
其他效果族、完整文件及 Host 仍待采用；
公共协议与这一封闭消费者不代表完整持续效果引擎已经实现。
它沿用[模组接口基线](module-interface-baseline.md)、[机制接入责任清单](mechanism-integration-checklist.md)、
[动作继续协议](action-continuation-protocol.md)、[运行与事务](runtime-and-transactions.md)、
[保存与恢复](save-and-recovery.md)及[玩家步骤](player-steps-and-auto-save.md)。
legacy RELEASED v1、完整 SRD DRAFT 和 archive format 2 未激活的边界不变。

当前 Bless 以唯一角色秒时钟到期，落选来源保留原期限和专注，不因恢复贡献重复施法或退款。
`END_COMBAT` 可将同一场景转为 `EXPLORATION` 并保留尚存效果；未结动作流和 Shield／Ready
回合期限仍须先处理，不能猜成秒数。当前 owner 仅覆盖封闭场景内的时间、移动、施法、
健康与 Bless 到期，不代表跨场景统一战役时间或完整周期义务／结束回调已经接通。
固定十尺 `DM_ANTIMAGIC_ZONE_V1` 是环境机制；真实八环 Antimagic Field、Dispel Magic
及其他压制来源仍须各自闭环，不能由这个切片推断支持。

## 范围与原文边界

本协议统一持续来源、逐目标应用及类型化贡献的身份、存续、压制、重新求值、结束、待结
义务和恢复责任。瞬时伤害、治疗、资源消耗等事实仍归原领域，不因存在一个持续来源而
变成可撤销状态。本文不扩展多目标伤害分配、完整区域几何、全部效果族或作者执行平台。

| 本地 SRD 5.1 | 必须保留的语义 |
|---|---|
| [Duration / Concentration，p.102](../../references/srd-5.1/pages/page-102.md) | duration 表示法术存续时间；瞬时改变不因之后解除魔法而撤销；失去专注结束所维持法术，不能同时专注两个法术；伤害各来源分别检查，失能或死亡直接失去专注。 |
| [Combining Magical Effects，p.104](../../references/srd-5.1/pages/page-104.md) | 不同法术在持续重叠时共同作用；同法术多次施放只取最强效果，不合并这些施放；同强如何选取在本页没有规定。 |
| [Conditions / Exhaustion，p.358](../../references/srd-5.1/pages/page-358.md) | 同条件的独立来源有独立期限，普通条件不因重复而增强；exhaustion 是累计等级的特例；grappled 因真实失能或越出触及而结束。 |
| [Petrified / Unconscious，p.359](../../references/srd-5.1/pages/page-359.md) | 石化暂存既有毒病，未中和它们；昏迷包含掉持物和倒地，结束昏迷不自动撤销已发生的掉落或倒地。 |
| [Antimagic Field，p.117](../../references/srd-5.1/pages/page-117.md) | 按目标、物品使用情境或覆盖区域压制；被压制时不发挥作用，duration 继续消耗，已耗 slot 不退还；神器/神祇例外和抗魔场彼此不抵消须由具体规则识别。 |
| [Calm Emotions，p.123](../../references/srd-5.1/pages/page-123.md) | 可压制 charmed/frightened；压制结束时，原期限仍有效才恢复原状态。 |
| [Dispel Magic，p.136](../../references/srd-5.1/pages/page-136.md) | 成功解除是结束，不是临时压制；以生物、物体或魔法效果为目标时，由真实效果合同确定结束范围，不能靠一个通用 clear-all。 |
| [Bless，p.122](../../references/srd-5.1/pages/page-122.md)、[Aid，p.114](../../references/srd-5.1/pages/page-114.md)、[Haste，p.153](../../references/srd-5.1/pages/page-153.md) | Bless 重复仍只一个 d4，高环只扩目标；Aid 同时涉及 current/max HP，需真实健康消费者；Haste 的真实结束产生后续限制，压制本身不等于结束。 |
| [Protection from Poison，p.173](../../references/srd-5.1/pages/page-173.md) | 中和一种既有毒和随后持续的优势/抗性是不同事实；持续部分结束不能使已中和毒复活。 |
| [Acid Arrow，p.114](../../references/srd-5.1/pages/page-114.md)、[Heroes’ Feast，p.154](../../references/srd-5.1/pages/page-154.md) | 法术头标 Instantaneous 仍可能产生下一回合伤害或 24 小时收益；法术 duration 不足以决定有无持续过程及后继的魔法/解除资格。 |

下文的稳定同强选择、排序和闭包限制是工程合同，不是引入 DMG 或 2024 规则。
“抗魔期间仍需维持专注，受伤照常检查”由 p.102 的专注义务和 p.117 的压制规则合取而来；
压制没有新增免除专注的规则。石化只规定毒病暂存，不能据此把抗魔的 duration 策略套给
所有毒病进展；疾病阶段、发作时钟和石化解除后的进展必须由其类型明确登记。
持续过程是否存在、后继是否仍是可解除/可抗魔压制的魔法，由真实机制登记，不以
`duration != INSTANTANEOUS` 筛选，也不因来源是 cast 就一律准许 Dispel/Antimagic；
未知类别保持阻断，不能把瞬时事实的后续全部当持续魔法抹除。

## 对象、身份与唯一所有者

下表是语义必要集，不是最终 SQL、DTO 或序列化字段名。所有可移植键遵循
[业务身份合同](character-state-and-history.md)，准确规则身份遵循模组基线，均不得用显示名、
数据库行 ID、自由文本原因或单个布尔值代替。

| 对象及唯一 owner | 必要信息与约束 |
|---|---|
| `OngoingSource`，来源领域 | 战役内稳定 `source_key`，协议/具体类型及版本，准确规则/条目身份，产生它的 cast、feature、物品或其他已登记真实事实，施加者/实际主体关系，创建事件及 ordinal，固定施加参数和已接受成本引用，`ACTIVE/ENDED`，原始期限/当前进展，结束事件和原因。运行时另绑定 run/snapshot/campaign、聚合 CAS 与预算。一次真实来源只有一个 root，不为每个目标复制施法/专注。 |
| `TargetApplication`，该来源的目标应用 owner | 稳定 `application_key`、准确 `source_key`、类型化目标引用、施加结果与产生事件、同源目标内连续 acquisition ordinal、`ACTIVE/ENDED`、该目标专有期限/后续豁免进度及结束原因。固定目标同一 acquisition 不能重复建立；区域重新进入若允许新应用，必须有新的真实 entry episode 和新 ordinal。 |
| `ContributionBundle`，具体效果类型 | 准确 application、稳定机制条目/贡献组键、已登记种类和有限参数、接收者/受影响字段或规则查询情境、叠加组/比较合同、依赖/期限引用。一个 bundle 可含 AC、速度、检定、权限等多份类型化贡献；同法术 winner 一起选择完整 bundle，不分别挑字段拼装。贡献身份为 application 与稳定条目/组身份的组合。 |
| `SuppressionCause`，真实原因的来源领域 | 稳定原因 episode 键及类型、真实 producer/事件、所引用来源或区域/条件/关系，准确作用范围、恰一个建立事实和至多一个解除事实及证据；未解除才是当前有效原因。范围可以是来源的已登记子集、某 application、某 bundle 或某类上下文贡献；拒绝一个来源级布尔替代完整目标/几何。多个独立原因并存且分别解除，真实重新进入建立新 episode，不改写旧解除事实。 |
| `ConcentrationHolder`，施法/专注领域 | 每施法者唯一 holder：`NONE` 或准确 `CASTING/DECLARED/EFFECT` 对象及取得事实；EFFECT 指真实 source。持续效果只登记对这个 holder 的依赖和订阅关系，不保存第二个 concentration boolean。 |
| `EffectTransition`，本次转换协调者 | 稳定批次键、真实根事件/原因、确定的转换前缀、待结义务及顺序、已完成结果引用；需要暂停时关联准确 flow/frame/checkpoint。它协调已有 owner，不能复制 HP、位置、时钟或专注状态。 |
| `EndObligation` / 其他转换义务，具体 consumer | 稳定义务键、所属 source/application、准确触发边和 callback 类型/版本、输入事实、`PENDING/WAITING/COMPLETED/NOT_APPLICABLE`、消费事件/真实结果或有限不适用证明。全范围结束与目标结束共享同一目标义务身份，不能各执行一遍。 |

引用必须双向可核验：完整来源事实确实产生该实例、该 application 确实属于该来源/目标，
贡献确实由相应冻结机制产生。仅外键存在、同名法术或某个 cast 行存在都不够。
condition、poison、disease、区域、物品等现有领域继续拥有自己的业务状态；适配本协议时
以准确引用接合，不另建一份同形实例或第二份权威期限。
每种实体/原因边/义务明确归属一个领域聚合版本，版本和历史由真实变化的 owner 推进；
通用层不新增第二个可独立漂移的 effect version。

依赖图区分两类边：`REQUIRES_LIVE_SOURCE` 表示来源结束须同时结束的真实子对象；
`PRODUCED_FACT` 仅表示历史因果，不表示随源清除。例如专注所维持的 application 是前者，
已造成伤害、独立 CONDITION_ONSET PRONE、掉到地面的物品和真实 end callback 产生的后继
通常是后者，实际分类由类型登记。不得沿所有来源引用递归删除或终止。

## 存续、申请结果和当前贡献

来源及 application 的最小持久生命周期均为 `ACTIVE → ENDED`。ACTIVE 表示尚未永久
结束，可能没有任何当前贡献；ENDED 是吸收终态，不能通过移除压制、恢复、再装备或后续
RESOLVE 回到 ACTIVE。尚未接受的计划不是实例；畸形冻结数据是错误，不新增 INVALID
业务阶段把它伪装成合法终态。
ACTIVE 的结束字段必须无值，ENDED 恰引用一个真实首次结束事件及类型化原因；创建、原始
来源/目标、期限依据及既有事件不可改写。已完成义务不再转 PENDING；WAITING 必须引用
真实未终结 flow/帧，COMPLETED 必须有消费事件/结果，NOT_APPLICABLE 必须有准确证明。

新申请结果与已有实例压制严格分开。条件施加结果 `APPLIED` 指真实新 condition instance；
原条件申请合同中的 `SUPPRESSED` 表示因当时完整免疫证明未产生实例，instance 为无值，
只保留原申请及拒绝施加事实。免疫后来消失也不能从该旧失败申请生出实例；重新施加须有
新的合法 producer、完整校验和新结果。它与本文已有 ACTIVE 实例的可恢复压制不是同一状态。

当前贡献是依据真实源、application、原因及查询情境重求的结果，不是第二个可写 phase：

| 投影结果 | 语义 |
|---|---|
| `EFFECTIVE` | 源和 application 存续、所有必要资格成立、无适用压制，且相应叠加组选择它。 |
| `SUPPRESSED` | 源/application 仍存续；列出全部当前适用的独立压制原因和范围，不丢失较晚解除的原因。 |
| `SHADOWED` | 本身可参与但同组另一个来源胜出；保存/显示准确 winner 和比较证明，不结束失败者或改写其期限。 |
| `INAPPLICABLE` | 该种贡献在当前已登记情境不适用，如 frightened 的劣势要求恐惧来源在视线内；这不自动取消其“不能主动靠近”贡献。 |
| `ENDED` | 来源或该 application 已终结，保留准确结束事实；不能参与 winner 或恢复。 |

投影同时返回全部原因、候选和比较依据；显示标签按 ENDED、SUPPRESSED、INAPPLICABLE、
SHADOWED、EFFECTIVE 判定，不能因第一个原因匹配就停止完整读取。源/应用级原因作用于其
明确范围，贡献情境的原因只限制该贡献；因而同源可以一目标生效、另一目标被压制，或同
target 仅部分贡献不适用。合法无贡献也不等于失效数据或永久结束。
独立 blocking cause 与派生的 SHADOWED 严格分开：先按存续、当前资格及独立压制形成
候选，再选 winner。上次落选不能成为本次排除候选的原因；否则强者结束后弱者会被自身
旧落选标志永久挡住。持久化的选择证明只是结果/历史，不反向充当独立压制事实。

| 转换 | 前置条件、业务变化和禁止事项 |
|---|---|
| 创建来源/施加 application | 完整预检通过并到达具体效果的接受点；一次保存来源/目标申请结果、合法实例、贡献依据、期限/holder 订阅及真实 onset 义务。不得用空效果标成功。 |
| 增加压制原因 | 真实原因及其作用范围已成立；保存原因边和转换事实，重新计算受影响贡献/派生值及真实转换后果。实例保持 ACTIVE，不跑 end callback、不退款。 |
| 解除一个压制原因 | 原因确实解除；只移除该原因的有效作用，保留其他原因、重叠选择和历史。无其他障碍才重新贡献，不强制设 EFFECTIVE。 |
| winner/资格变化 | 根据当前权威状态重新比较/求值；保留来源、原期限和已发生事实。新的 winner 不重新施法、不重新执行一次性施加。 |
| 结束 application | 真实目标级触发已成立；原子标 ENDED，撤去本目标存续依赖与贡献，安排该 application 尚未消费的结束义务；其他目标保持原状。 |
| 结束 source | 真实来源级触发已成立；原子标 ENDED，结束其全部 `REQUIRES_LIVE_SOURCE` 子图并撤去贡献，清理准确 holder/订阅关系，建立去重的来源及目标结束义务；不沿历史因果撤销既成事实。 |
| 继续义务 | 只执行尚未发生的准确后缀，保存真实结果/不适用证明；原 ENDED/原因变化仍成立。完成回调不把来源复活。 |
| 重复结束 | 同一已结束对象不新造结束事件、结束原因或 callback；新请求返回类型化 ALREADY_ENDED 当前结论，原请求仍精确重放原接受事实。 |

结束抑制来源，或具体类型判定其抑制资格失去时，还必须解除以该存续/资格为必要条件、
指向**其他存续来源**的全部出向 suppression cause，并重求接收者的贡献；这与结束本源
的存续子图是不同责任。原因拥有者、接收者、全部入向/出向边都纳入同一闭包；仅有历史
因果关系的独立后继按自身期限处理，不能删除抑制源后留下永远无法解除的原因。

所有 target 已结束是否使 source 结束、是否释放 holder，以及无目标的区域根是否继续存续，
必须由具体类型登记；不能以“当前有效目标数为零”通用清空专注。新目标获取也不能复用
一个已结束 application；允许持续区域再次获取时，新 episode 是新事实，旧终态仍保留。

## 封闭登记、重叠与条件聚合

应用为每个支持的持续类型登记完整合同；模组只能使用应用已支持的类型和有限参数，不能
提交 callback 名称、任意表达式、自由 JSON、执行脚本或自报的强度。登记至少包括：

1. 源/目标/贡献身份、允许参数和值域、完整来源及目标 reader、创建和成本接受点。
2. 具体贡献与其真实 consumer、采样时点、数值/上下文/随机性责任、叠加组与比较算法。
3. 可抑制范围、原因及完整证明 reader、每个 eligibility gate、同原因去重与解除条件。
4. 来源/目标期限、专注依赖、区域进入/离开、装备/权限变化、死亡/失能/解除等准确转换。
5. onset、失去贡献、恢复贡献和结束各边的义务；作用范围、shadowed/suppressed 时的适用
   政策、只执行一次的身份、产生后继的独立存续规则及无变化的合法结果。
6. 状态/历史/版本、flow 继续点、有限闭包/终结额度、文件与直接恢复、END/清理及独立验收。

同法术多来源以完整规则身份确定同法术域，按每个 target 在各次施放间选择完整效果 bundle。
施加者、显示名、模组加载顺序和 slot level 均不直接代替同法术身份或最强比较。比较器须
由具体机制从冻结规则和真实施放参数定义为可复核的完整排序/等强关系；Bless 高环增加
目标数，不因此给已有 target 更强的 d4。需要不同强弱维度却尚未定义比较的分支拒绝采用，
不能逐字段取不同施放的最大值或让客户端报 potency。
整个 bundle 的候选资格与选定之后各贡献的上下文门分别登记；某份已选贡献局部受压制或
不适用时，不从落选施放借一个字段填补。若该机制要求整体改选，须对完整 bundle 重选，
不能混成多来源的合成法术。

本版对比较器确认的等强候选采用**最早者优先**：依次按 source 创建事件序号 ASC、同事件
创建 ordinal ASC、稳定 source 业务键的无符号网络字节序 ASC、application acquisition
ordinal ASC 选择第一项。这是工程选择；不声称 SRD 5.1 规定取最早或最新。选取在掷其
贡献骰之前完成，不用 Bless 实掷的 d4 大小反选来源。持久记录来源和比较证明，独立恢复
保留排序依据，数据库返回顺序或新本地行 ID 不能改变 winner。

不同叠加组分别求值，再由实际派生/检定消费者按已登记顺序合成。不同法术不因同显示名、
同目标字段或相同数值而互斥；明确的跨规则互斥须有独立合同和组身份。此规则也不表示
“所有 bonus 一律相加”：advantage/disadvantage、AC 替代算法、抗性、临时 HP、速度与
资源各自保留其真实聚合合同，未知聚合不能默认相加或取最大。

普通 condition instance 各保留 ACTIVE/ENDED、来源关系和独立期限；同 kind 的多个有效
来源产生一份不增强的语义集合，同时保留所有 source-sensitive 关系。charmed 必须知道
各 charmer，frightened 必须分别求视线和接近限制；不能选一个“最强 condition”丢失其余
约束。结束一个睡眠来源不会消除 HEALTH_ZERO 等另一昏迷来源，也不清除独立 PRONE。

exhaustion 不使用 winner：逐 instance 保存 original/remaining，remaining 按真实已获等级
计入当前总级（上限 6），不存可在减级后自动补出的超额休眠等级；减级计划精确分配本次
实际减少量，remaining 为零才结束该实例。达到 6 的死亡是健康事实，减级不复活。
具体减级归属/选择由其类型登记，不能靠任意先删一个 source 实现。

PETRIFIED 对既有毒病的暂存使用独立的原因集合，保留毒病真实实例/进展；最后一个相关
原因消失才按其登记策略恢复。普通免疫不能默认压制所有既有条件，只有具体规则明确
要求时才建立压制边。原型未知、进展策略或暂停/恢复 consumer 缺失时拒绝相关转换。

## 时钟、资格变化与重新求值

战役只有一个权威游戏时钟，秒数只由已登记时间 owner 推进；主机时间、HTTP 超时、保存、
GET、重启和恢复都不推进 duration。按秒到期保存绝对业务截止秒和原始期限依据；按回合
边界保存准确 combat/turn/START 或 END 及其产生依据，不把个人回合期限乘六替代真实边界。
“until removed”、未来个人回合计数、按次触发或分期进展都必须有具体 deadline planner。

一个来源可同时依赖最长期限和专注，先发生的合法结束边终结来源；application 可有自己的
更早期限。期限 owner 只能有一个；condition 等现有期限由原对象拥有，通用效果层引用
其边界而不复制第二个倒计时。抗魔或同法术 shadowed 不延后期限、不冻结时钟；毒病等特殊
进展暂停与法术 duration 分别登记，不从一个统一 pause 布尔推断。

| 真实变化 | 必须由类型明确的结果 |
|---|---|
| 离开区域 | `END_APPLICATION`、保持 ACTIVE 而暂不贡献，或仅不再触发区域 tick；重新进入是原应用恢复还是新 acquisition，均须明确。不能用一个“离开即停”覆盖所有区域。 |
| 失去/脱下装备、调谐或授予关系失效 | 来源本身结束、只是装备贡献失资格，或已施放效果继续；重新装备是否恢复原贡献须明确，不能重发已耗资源/充能。 |
| 施加者移动、离开初始 range、失能或死亡 | 只按真实规则消费。固定持续且不依赖专注/特定范围的法术，不因这些变化通用结束；专注持有者死亡/失能则按专注规则结束。 |
| 解除魔法、成功后续 save、主动解除 | 明确是源还是目标范围；多目标法术对一个 target 的成功解除/豁免通常只结束该 application，不能影响其他 target；准确范围由效果合同和真实意图证明。 |
| 抗魔覆盖或移开 | 依据真实 target、物品使用情境和局部几何改变对应贡献；不能因 caster/holder 在场内就一律撤去远处所有 target。神器/神祇例外、抗魔场不互消及未知魔法类别须完整识别。 |

恢复贡献及胜出来源变化时，从**当前**权威基础与完整当前有效集合重求派生值，包括当前
装备、属性、其他效果、健康与位置；禁止按旧值“减回旧 bonus/加回旧 bonus”。缓存投影
必须能从这些权威输入独立核验，不能当第二份业务真相。

可逆派生值和一次性事实分开：恢复不重施法、不重掷已产生的骰子、不再伤害/治疗、不补发
temp HP、充能、动作资源或已花 slot，不撤销已经发生的 HP 伤害、消费、掉落或倒地。
真实周期触发仍可在下一合法边界产生新事实，按该边界身份去重，不能把重新贡献当补领
被压制期间全部 tick。

每次周期 occurrence 的业务身份绑定准确 source、application（来源级触发使用明确的
SOURCE 范围）、稳定触发条目完整身份、真实边界引用及 occurrence ordinal。边界引用按
已登记类型恰为 combat/turn/START 或 END、业务截止秒及其 schedule episode，或真实
区域/目标 entry episode；ordinal 在该边界同条目内稳定有序，不以请求键、本地行 ID 或
新建立的 flow 代替。owner 持久保存连续已检查前缀 cursor、准确待处理余集，以及每次
`PENDING/SKIPPED/CONSUMED` 决定的检查事件、原因/资格证明和消费结果。SKIPPED 和
CONSUMED 为终态；重复请求、重新贡献、winner 切换和直接恢复均不重置 cursor、不重复
消费，也不把已跳过 occurrence 改回待处理或之后补发。当前必需的 PENDING 仍按唯一 flow
结清，不能只推进 cursor 后丢失未完成义务；继续时仍复验当前依赖，但不重做已记录的
检查骰、决定或消费。

每个触发条目分别登记效果贡献/伤害、期限、后续解除 save 和疾病等进展的 gating/暂停
策略及实际 consumer；SUPPRESSED 或 SHADOWED 不一律停止全部周期义务。法术 duration
继续按已定规则消耗，其他 save/进展是否继续、暂存或合法跳过由各自具体类型证明；暂停
进展须保留其真实 cursor/余量和准确恢复策略。缺策略或消费者拒绝采用，不默认 SKIP；
合法 SKIPPED 只记录本次未执行原因，解除压制不补领它。

Haste 类每 turn 的行动授予由唯一 action owner 管理：额度身份绑定准确受领者、真实
combat/turn 和稳定用途组，保存本 turn 原额度、已消费量、消费事实及各来源支持证明。
同组多个 source/winner 共享该真实 turn 的额度账本，不能各发一份；压制后恢复、同强
winner 切换或来源更替不能重置已消费量、创建新额度身份或再次发放 action。贡献失去或
恢复时，合法可用余量/额度变化只能由 action owner 的具体 transition plan 决定，并
保留既有消费和用途限制；未明确该计划与来源归并规则的类型保持不支持。

这不禁止真实转换后果。Aid 的 current/max HP、max HP 降低后的合法 clamp、身体/重量
变化等必须由唯一健康/身体 owner 提供专门 transition plan 和完整前后事实；不能把 current
HP 当普通可逆 bonus，也不能把撤销/恢复做成一次新的治疗。具体数学和抑制互动未闭合时，
相应效果族保持不支持，而非由本协议补造统一 HP 公式。

UNCONSCIOUS 的有效语义无→有时，真实 onset consumer 按准确转换边产生独立 PRONE 与
HeldDropPlan；多个同时有效来源不重复 onset。若压制后恢复也形成新的无→有边，是否有
新的真实 onset 由该具体机制的恢复政策登记并按边身份去重；不能机械一律回放旧 onset，
也不能对全部机制一律禁止新 onset。结束/压制昏迷不把物品移回手中或自动站起。

## 专注、结束范围与一次性回调

新合法集中 START/DECLARE 的全部预检成功后，才结束旧 holder 支持的来源并取得新
`CASTING/DECLARED`；非法请求保留旧 holder。新施法被反制不会复活旧专注。合法转换为
EFFECT 只引用该次真实新来源；已 DECLARED 后失去专注的后续 RESOLVE 不得重新取得 holder
或建立依赖已失去专注的持续效果，已发生瞬时部分和其他阶段由其准确类型处理。

压制或 shadowed 不清除 holder，不免伤害后的专注检查，不在解除压制时重新取得 holder。
同一伤害来源由唯一 DamagePacket/健康 owner 产生一次实际伤害事实，专注 owner 按它消费
共享检定 audit；真实失能/死亡优先直接结束，不额外掷一个无意义的集中骰。

结束 holder 时，完整枚举并在同一事务终结它支持的 source 及全部存续依赖子图，包括被
压制或 shadowed 的 application、condition、定时边和贡献。未知 subscriber、缺目标或
缺结束 consumer 阻断父操作，不能先清 holder 再留下漂浮效果。目标级结束不通用清除
整个 holder；若类型要求“最后一目标结束则释放”，必须有真实该类型的 source 结束规则。

来源或目标进入 ENDED 即刻撤去其贡献，结束事件和首个真实原因不可改写。结束 callback
是新的待履行义务，其等待不推迟已经发生的结束，也不使 ENDED 可逆。每个登记的来源级
callback 每 source 最多一次，目标级 callback 每 application 最多一次；义务键包含所属
对象、准确转换边及稳定 callback 条目身份。先目标结束、后源结束也只消费一次该目标义务。

Haste 的“法术结束”可产生独立的迟缓后继，压制/落选不触发这个 end callback；真实结束
时，被压制/落选目标如何处理该后继必须由 Haste 的具体 owner 政策闭合，不能从通用规则
猜定。callback 产生的独立新效果不作为旧源的存续孩子被同一次结束 sweep 清掉。
合法无后果也须记录 `NOT_APPLICABLE` 及类型化证明，不能静默漏执行；不适用和未知不混用。

## 有限转换闭包与动作继续

任何上游变化可能影响条件、专注、装备、位置、派生值或期限时，真实 producer 交出一个
完整类型化 transition plan；通用协调只排序/去重，实际值与后果仍由各 owner 一次消费。
协议不建立可独立运行的第二个效果栈、后台 scheduler 或“读取时修复”入口。

闭包按以下顺序构造和执行：

1. 在 RNG、资源消费和业务写入前，枚举全部目标、原因、重叠组、订阅者、当前及所有可达
   后继/失败分支；核对规则、版本、完整 reader、类型化结果、保存及终结容量。
2. 以真实事实边界为批次，按具体规则的 BEFORE/AFTER 阶段先后处理；同边界登记阶段相同
   时，按固定阶段序、source 创建事件/ordinal、目标完整业务键、application ordinal、
   义务条目完整身份排序。类型/键的比较使用明确无符号规范字节，不依 DB/请求到达顺序。
3. 同一事实边界先消费该阶段全部确定的源/目标结束和原因变化，保留全部真实前缀；再按
   当前活源及完整独立原因重算 eligible 集合、winner 和派生结果，消费真实 onset/失去/
   恢复/结束义务及其后继。强弱两来源同秒或同 turn 边界到期时，不得在两次结束之间
   临时恢复也即将结束的弱来源并误触 onset；具体规则明确另有先后时按登记阶段处理。
4. 新事实仅进入已证明的有限 frontier，按真实义务身份去重；稳定后才返回父继续点或
   完成根命令。一个义务不能同时由时间、健康、flow 和来源 owner 各执行一次。

类型登记必须给出有界依赖图、阶段拓扑序、每节点最大生成量、RNG/结构/字节及版本推进
上界。结束只走终态，原因按真实事实建立/解除；同一个事实版本和义务身份不重复求解。
会形成互相反转的 A 抑制 B/B 又恢复 A、callback 再激活已结束祖先等循环，若无已审核的
专用有限算法则预检拒绝；不能“迭代到稳定”、截断队列或靠最大循环次数丢弃真实义务。
Antimagic Field 的彼此不抵消是具体规则边界，不以通用魔法布尔形成互消循环。

需要 reaction/choice 或跨命令继续的真实转换，使用唯一 flow 中的
`ONGOING_EFFECT_TRANSITION` 帧，owner 是准确 EffectTransition 批次/义务。它既可作真实
效果根，也可作为 ATTACK/MOVEMENT/SPELL_CAST/TURN_CLOSING 等父帧的普通 CONTINUATION
孩子；普通边不增加反应深度，不产生额外 action/reaction 成本。实际反应仍按动作协议
绑定真实窗口、机会和一次 reaction spend。不能伪造 cast 或把普通 callback 标成反应。

帧只引用领域已发生前缀、待执行余集、准确返回 checkpoint 和当前依赖；不复制效果值。
具体阶段必须登记 `RUNNABLE/WAITING_WINDOW/WAITING_CHILD/WAITING_CHOICE/TERMINAL`
的合法转换及结果。等待时，来源已经结束或原因已经变化的事实同事务成立；pending 仅指
尚未执行的后继。后继和子反应先完成，父才重验其当前完整依赖并继续，不能返回旧基础值。
无等待且全部闭包已证明时同事务直接结清，无须为每次投影变化强造帧或额外请求。

| 接合 owner | 真实责任 |
|---|---|
| CheckPlan / 攻击 | 明确每份贡献在掷前、掷后或防御时点的采样规则；已记录骰子、已消费 Bless 等原贡献及 audit 不因随后压制而倒改。尚未发生阶段按当前状态重验；不得因为 winner 改变而重掷原骰，或把旧 bonus 加第二次。 |
| DamagePacket / 健康 | 在伤害所属时点使用完整适用减免及 contribution，实际伤害、condition/onset、专注和结束闭包同根协调；效果层不重算第二份伤害、不由 HP delta 反造伤害来源。掷后未应用的伤害保留原骰和准确待结进度。 |
| 移动/空间 | 按真实边界消费进出/覆盖变化，再决定原移动后缀。因速度归零/失能合法停止，保已走前缀和真实位置；不先移动到终点再倒放效果。抓抱等明确“离开即结束”的关系不改成可恢复压制。 |
| 时间 | 由唯一时间 owner 保存真实 clock、已消费时间前缀及未走时间后缀，在截止边界先处理效果/后继，再推进后续窗。若需等待，转换帧引用准确时间 owner 和 continuation；不能先把 clock 推到最终时刻再处理过去 callback。普通 turn 移动不加时间，探索 MOVE 原六秒也不因暂停/恢复重复增加。 |
| TURN_CLOSING / 玩家步骤 | 将真实到期、onset/end 和其全部必需后继纳入固定 finish 队列与同一 flow；仍为 CLOSING 时不写 turn/round 最终完成或 N+1。全部必需义务结清才完成一次，再按水位触发保存。 |

未来合法 duration、持续专注、区域根和待未来 trigger 本身不占活动 flow，也不持续阻止
步骤完成。当前已经到边界、已接受但尚未履行的转换/choice/回调则必须结清；不能把它们
标成“合法持续状态”绕过写门。无来源/阶段登记的特殊 timer 不因通用时间接口存在就获准。

## 类型化调用、事务和结果

以下为语义调用边界，真实接口仍须给出严格 DTO 和字节上限；不据此创建任意效果修改 API。

| 调用/结果 | 有界必要信息 |
|---|---|
| `EffectTransitionIntent` | 准确本地 run/snapshot/campaign、请求键、操作身份 `{producer, subject, phase, ordinal}`、预期完整版本集合；操作为已登记 `END_SOURCE/END_APPLICATION/CONTINUE_TRANSITION` 或父命令内部 `RECONCILE`，附准确 source/application/transition 和需要的 flow/frame/checkpoint。普通用户不能提交派生贡献、cause 集合、winner、最终期限、骰点或任意内部 RECONCILE。 |
| `TransitionCause` | 封闭的真实 cast/holder、时钟/回合边界、位置/区域关系、装备关系、健康/condition、解除或有限 DM 裁定事实引用；各分支必需字段、无值和不适用严格区分，不接自由 cause 字符串。 |
| `ContributionEvaluation` | 查询所用准确规则/源/应用和当前权威事实版本、完整候选及逐贡献结果、全部压制原因、比较器/同强选择证明、最终 typed contribution；它是无 RNG、无写入计划，真实消费者核验并一次消费。 |
| Accepted | 原请求/操作身份、接受根事件、真实转换与已消费成本/骰/结果引用、当时 COMPLETE 或 WAITING；不可变，不因后继完成改写原 WAITING。 |
| `OngoingEffectView` | 当前运行资格、来源/目标存续与期限、holder 引用、逐贡献原因和 winner、当前 transition/flow 叶子/允许操作及版本、已结束或已清理事实；GET 只读，不解除原因、不跑 callback、不推进时间。 |

所有操作使用共同独占门及私有库单写事务，按完整主键的确定顺序读取和验证全部
来源/目标/健康/位置/holder/时间/flow 及其引用闭包。Repository 复用调用方连接，不自行
提交。新根完整预检所有目标及全部可能后果后才 RNG/写入；继续只重建准确未执行后缀，
仍核当前全部依赖和剩余额度，不能凭父先前 CAS 继续使用旧目标/资格。

一个命令的真实权威变化、根事件/typed 历史、原因/选择/义务、flow 前缀、各受影响聚合
各一次版本推进、预算/预留和接受结果原子提交。无业务变化的聚合不因“更新事件字段”
自行加版；多个目标/回调不能各自提交。ACTIVE flow 仅允许准确当前叶子的登记操作，
END_CONCENTRATION 等有限操作也须有该阶段允许的合同，不能以“无需 action”绕过等待。

相同键、相同规范意图返回原接受事实；同键异意图冲突，不能再跑一次结束或恢复。
新请求结束已结束对象可返回 ALREADY_ENDED，但它不改写旧结束事实，也不授权推进其
未决 callback；继续须指向当前准确 transition/叶子和新 CAS。已提交响应失败保留原事实。
commit 未知或 rollback 失败按运行协议隔离执行资格、在同一操作身份下只读查证，
不换键重试、不补义务、不释放预留、不倒退 RNG，也不在查证成功后自动继续。

## 保存、直接恢复、容量与技术 END

| 内容 | 保存及直接恢复责任 |
|---|---|
| 源和 application | 完整稳定身份、真实来源/target、ACTIVE/ENDED、原始参数/期限、当前进展、结束原因/事实、各 episode 及全部历史；ENDED 和失败申请仍有因果引用，不能只保存当前有效来源。 |
| 贡献和原因 | 真实贡献定义的准确规则/条目引用、固定施加数据、独立 suppression cause/作用范围/解除事实、重叠组和比较依据；不把可推导缓存当新权威。完整恢复后用当前已验证基础独立核验投影，但不把求值当新施加。 |
| 订阅和依赖 | holder 与其准确 source/condition/区域/时间等全部存续依赖、历史因果边的区别、各对象唯一期限/进展和实际业务时钟/回合。缺一 subscriber 或使用错误 owner 均拒绝。 |
| 周期和 turn 额度 | occurrence 身份、已检查 cursor/待处理余集、检查/跳过/消费事实与原因，以及 action owner 的 turn/用途组额度和既有消费；直接恢复原账本，不按当前 winner 重新授予。 |
| 转换中途 | EffectTransition、已发生前缀/未决余集、义务状态/真实结果/不适用证明、flow/checkpoint、时间/移动前缀和准确未消费后缀；结束后待回调的 ENDED 不能被恢复成 ACTIVE。 |
| 本地控制 | run/snapshot、物理行 ID/外键、row_version/CAS、请求收据、进程资格、保存技术水位和本地预算事实排除于业务文件；按完整已验证业务图重建新本地绑定、版本和收束预留。 |

严格 reader 核验战役/目标归属、准确规则与来源、状态与结束字段、全部原因、关系范围、
期限/时钟/回合一致性、独立 acquisition、唯一 holder、无悬空/禁止循环、callback 消费
次数、真实事实与历史，以及 flow/时间/closing 的唯一继续点。已到期却仍 ACTIVE 且没有
合法边界中途状态，或已有结束事实却仍贡献，均是损坏；不能读取时自动到期修复。
缺分区/未知类型拒绝，空数组不能代替尚未实现的领域。

恢复直接写入已验证的当前状态、历史和未执行余集；不重放施加/伤害/治疗/onset/end，
不重掷、不补 tick/资源、不延长期限、不停止专注、不走剩余时间/路径，也不自动选择或
完成步骤。writer/reader 自洽往返不能证明覆盖；须独立逐字段核源→文件及文件→恢复投影，
再核实际数据库读回。恢复沿原准确组合，不借重建快照改规则或 winner。

每次新来源、目标获取、压制边、后继及扩大 frontier 前，先证明完整变化后可导出，且从
当前状态到全部已接受义务合法终态的历史、节点、结构、原骰/选择、结果、版本和字节都有
有限额度。创建持续或无限期实例时，预留可确定的结束/清理与当下必需收尾，包括其真实
onset/end、专注全子图和 CLOSING 责任；不承诺无限未来回合、可选新施法或无穷 tick 历史。
不能只按当前活跃贡献数或最早一个 timer 计费，也不能无界递归预留。

每次新的时间推进或周期触发请求在推进前证明本次有限窗口的全部真实后果、所有已承诺
时间后缀和终结余量；普通容量不足则在推进前拒绝，保留原 source/pending、既有预留和
技术 END 能力，不先推进半段再发现后缀无额度。若边界产生持久 pending，本命令已接受
的全部必需后缀沿动作协议预留，不能以“下一 tick 下次检查”为由漏掉已承诺窗口。

容量沿用[保存预算](save-and-recovery.md#容量与增长)，每类源/目标/原因/义务还须有实际
字段、数量、逻辑字节、guard/RNG 及物理峰值界。共享健康/时间/flow/来源终结节点按真实
语义身份 union 计费并在同事务移交，不能父子重复计费，也不能释放父预留后才申请必需
后继。新可选义务容量不足在成本前拒绝；已接受必需义务使用持久预留完成，完整性损坏
转故障/救援，不丢弃 pending。完整保存/修复所需容量不能借给普通效果增长。

技术 END 只排空在途数据库事务，按最新完整源保存稳定持续状态和转换中途；不主动结束
来源/专注、解除压制、执行 end callback、退款或推进时间。FINAL 按[保存协议](save-and-recovery.md)
P0—P3 发布、实际读验并登记封存，覆盖实例/原因/订阅/义务/flow/历史整个图；随后关闭
连接并隔离清理整个私有运行目录。封存前失败保源，目录部分删除失败保墓碑而非承诺回滚。
正式档案独立保留，本地收据随运行清理后旧 run 失效，不因领域效果结束删除。
重启不自动 resume 或运行到期任务；同版完整查证/校验后可显式继续原运行，也可救援保存
或精确放弃；从文件则新开运行并保留所有未决后缀。

## 稳定错误与诊断

以下为正式语义错误，HTTP/DTO 映射随真实入口采用，不改 v1。诊断包含阶段、准确可取得
的 source/application/contribution/cause/transition、规则/事实位置及类型化原因；不虚构
来源。合法期限到达、目标豁免成功和已结束是业务结果，坏数据不能混成“不适用”。

| 错误 | 保证 |
|---|---|
| `ONGOING_EFFECT_TARGET_MISMATCH` | 错战役/run/snapshot/来源/目标/义务或非当前叶子，零 RNG/写入；不自动跟随最新对象。 |
| `ONGOING_EFFECT_VERSION_CONFLICT` | 任一必要当前 CAS/依赖过期，本次零 RNG、成本、事件和版本推进。 |
| `ONGOING_EFFECT_TRANSITION_INVALID` | 非法转换、试图恢复 ENDED、重复消费义务、错边界/返回点；保留真实前缀和原结果。 |
| `ONGOING_EFFECT_RULE_UNSUPPORTED` | 可能适用的类型、比较、原因/特殊 timer、后继或完整 reader 未登记；接纳前失败关闭，不视为零贡献。 |
| `ONGOING_EFFECT_FROZEN_DATA_INVALID` | 来源/状态/规则/历史不一致、悬空、禁止循环、重复 holder 或贡献；不跳过、不自动修复，并使相关能力失效。 |
| `ONGOING_EFFECT_LIMIT_EXCEEDED` | 本次新增会超过登记数量、结构、数值或 RNG 界，付费前拒绝，不截断目标/原因/义务。 |
| `ONGOING_EFFECT_RESERVATION_INSUFFICIENT` | 无法证明完整保存和必需后继/终结额度，新义务产生前拒绝，保留既有 pending/预留。 |
| `ONGOING_EFFECT_IDEMPOTENCY_CONFLICT` | 同键不同规范意图；保持原接受事实，不重新执行。 |

动作流忙、自动保存暂停、提交未决及执行资格失效继续使用共同守卫与动作协议的对应
错误，不建立效果专用绕行资格。字段、类型、未知/重复/过量输入在业务计划前严格拒绝。

## 独立验收矩阵与采用门

以下是实施必须观测的独立预期，**不是已运行测试结果**。每项须指向真实 producer →
plan → consumer、状态/历史/版本、文件/恢复/清理映射及实际证据，按责任清单分别判断
设计责任完整、内部可测、端到端和正式发布，不能以一张效果表或纯函数代替完整接入。

| 场景 | 独立预期 |
|---|---|
| 两个不同法术/叠加组 | 各来源和期限保留，按各自真实聚合合同共同作用；同名不同完整身份不被显示名合并。 |
| 同法术强弱和同强 | 每 target 完整 bundle 选择最强；弱来源不结束，强者结束后弱者仅在仍存续时贡献；同强按创建事件/ordinal/稳定键 ASC，恢复不改变 winner。 |
| 同边界共同到期 | 先结束该阶段全部确定到期源，再求 winner；没有短暂恢复/额外 onset，旧 SHADOWED 不回灌为独立 blocking cause。 |
| Bless 高环/随机性 | 高环多目标不使已有 target 的 d4 更强；两个来源只选一个 d4，先选择再掷，压制/恢复/幂等不重掷旧检定。 |
| 多目标局部压制/解除 | A target 在抗魔中无贡献，B 正常；移开只重求 A。对 A 的 Dispel/后续成功 save 只结束登记范围，不清 B 或通用清 holder。 |
| 多压制原因 | 抗魔与另一已登记原因并存，解除一个仍不贡献；最后原因消失且原期限有效才恢复，原因全集和解除历史可独立读回。 |
| 抑制来源结束 | 完整解除本源对其他来源的出向原因并重求接收者；不残留悬空压制边，不结束其他来源，旧解除 episode 不被复用改写。 |
| 压制期间结束 | deadline/专注失败照常终结源与全存续子图；之后离开抗魔或解除另一个原因仍 ENDED，不重建 holder/应用。 |
| 申请被免疫拒绝 | `SUPPRESSED` 申请无 instance、无 onset/drop；免疫消失只改变现态，旧请求重放不产生实例。 |
| 同条件多来源/关系 | 多个睡眠/HEALTH_ZERO 昏迷各保期限；解除一个不清另一/PRONE；多个 charmer/fear 来源的特定关系和分贡献条件均保留。 |
| exhaustion | 累计与减级精确落到真实 remaining，不套 max winner、不储存等待自动回补的超额；达到死亡后减级不复活。 |
| 石化毒病 | 既有对象暂存、有独立原因/进展，非 ENDED；多源石化只解除一个仍暂停；不借抗魔通则猜进展，缺 reader 整体拒绝。 |
| 中和与结束 | Protection from Poison 的已中和毒不在保护结束后恢复；源结束不撤销已发生伤害、治疗或消耗。 |
| 瞬时来源的持续后继 | Acid Arrow 下一回合义务、Heroes’ Feast 延后收益/24h 状态有真实 owner；不按 duration 头漏存，不因 cast 因果关系一概允许 dispel/抗魔压制。 |
| 当前基础/转换后果 | 被压制期间装备/基础改变，恢复使用当前基础；Aid/身体/maxHP 后果走真实 consumer，既不按旧 bonus 增减，也不重发治疗/temp HP/资源。 |
| onset 与 end callback | 同时多来源不重复掉落，独立 PRONE 不随昏迷撤销；恢复形成的新边按登记政策去重；先 target END 后 source END 只消费一次目标 callback。 |
| Haste | 压制/落选不触发 lethargy；真实结束产生准确已登记后继，后继不会被旧源 sweep 结束；未闭合抑制互动不宣称支持。 |
| occurrence 与独立 gating | 同 source/application/条目/真实边界/ordinal 只形成一份 occurrence 事实并至多消费一次；压制可使已登记伤害跳过而期限/save 按各自策略处理，cursor/原因完整保存恢复，解除压制不补发 SKIPPED。 |
| turn 行动额度 | 同 turn/用途组已消费 Haste 类行动后，压制→恢复、同强 winner 切换或多来源均不重发额度；保存/恢复保原消费，新可用余量只由真实 action plan 决定。 |
| 专注 holder | 非法新集中保旧；合法新集中结束旧，新 cast 被反制旧也不复活；DECLARED 期间失去 holder 后 RESOLVE 不重建依赖旧专注的 effect。 |
| 专注与伤害 | 被压制时仍按真实独立 damage source 消费共享检查；失能/死亡直接结束；effect/flow 不额外掷一份集中骰。 |
| 区域/装备/初始 range | 按登记模式结束、失资格或新 acquisition；旧 ENDED 不恢复；普通不依赖范围/专注的持续法术不因 caster 移动/死亡通用结束。 |
| Check/Damage/移动继续 | 原 audit/已花贡献骰/伤害与已走前缀保留；未执行阶段用当前资格，因真实状态合法停止；没有旧 bonus 重加、第二份 packet 或瞬移回 checkpoint。 |
| 时间中断/CLOSING | 到真实边界先结闭包；等待保存 clock 前缀与余集，不先走最终秒；探索六秒只一次；最后回合所有必需义务结束后才 N+1 和保存屏障。 |
| 有限闭包/重复触发 | 同一真实义务跨时间/健康/holder 只消费一次；禁止循环/未知后继在首 RNG 前拒绝；稳定长期实例不占 flow。 |
| 完整预检/末尾坏目标 | 坏冻结参数、未知 reader、最后 target 的坏版本均使整新根零 RNG/写入/成本；不能先处理前几个目标。 |
| CAS/幂等/提交故障 | 迟到请求零副作用；原 WAITING 不被后继改写；任一落库点失败整体回滚，响应失败保已提交事实，未知只查证且不自动继续/释放额度。 |
| 中途保存/独立恢复 | fixture 覆盖多原因、shadowed、ENDED 待 callback、时间边界等待、部分目标终结、CLOSING；逐字段独立期望一致，无重施加/重掷/补 tick/清 holder。 |
| 坏文件/清理/容量 | 修改一 source/期限/原因/义务次数/holder/episode 即拒绝；最坏合法闭包实际费用有界；FINAL/B 失败保完整源，无半套删除，不借结束预留接受普通增长。 |
| 重复 tick 的有限准入 | 无限期来源只预留已确定终结/当前收尾；每次有限时间窗口首写前计全后果及后缀，超额度零推进，持久 pending 的必需后缀仍有额度且可保存/END。 |

本文确定公共协议；各具体类型采用时仍须交付准确 typed 字段、原子作者参数、规范字节及
独立向量、真实来源/快照/贡献与转换消费者、SQL/锁/权限、完整存档分区/恢复映射、持久
预留/物理峰值、故障注入和真实 Host 验收。字段义务不能由空集合或 N/A 掩盖。
Bless、Shield、固定 DM 区域的独立压制及战外保留的有限内部采用见
[施法合同](../../rules/schema-2-spells.md)；Aid/Haste、毒病、真实 Antimagic Field／Dispel Magic、
其余区域与压制来源、周期义务和结束回调仍须各自闭环。有限场景 owner 不代替统一战役时间
及跨场景效果责任，也不改变
[采用边界](adoption-boundaries.md)和[完整 SRD 跨领域发布门](../../rules/srd-5.1-complete.md#6-完成判定)。
