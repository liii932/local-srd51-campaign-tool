# Schema 2 有限怪物与火焰吐息

## 采用范围

本合同连接 `adventure.monster` 的准确作者／来源／镜像与
`Schema2CombatRuntimeService`、`Schema2SaveDamageRules` 和 `Schema2MonsterRules` 的 INTERNAL
怪物动作消费者。唯一可执行怪物动作是
`SAVE_DAMAGE_BATCH_V1` / `HELL_HOUND_FIRE_BREATH_V1`；普通 Mummy 的火易伤和
Hell Hound 的火免疫作为真实目标防御消费。怪物能力说明、Bite、Multiattack、Rotting Fist、
Dreadful Glare 等元数据不取得执行资格，也不能由通用健康输入代替其成本、伤害或后果。

共同事务、角色健康及唯一动作流分别沿用[业务事务](business-write-transactions.md)、
[角色基础](schema-2-character-foundations.md)、[内部战斗](schema-2-combat.md)和
[有限施法](schema-2-spells.md)。[多目标公共合同](../design/v2/multi-target-save-damage.md)
保留更广的目标接口；本文记录已经接入的封闭分支及其适用差异。
这一 INTERNAL 接合不开放完整基础包、非空内容包安装资格、Host、完整战役文件或
archive format 2；`dnd5e2014_srd51_se` 继续为 DRAFT，旧 RELEASED 默认不变。

原文先读本地 SRD：Hell Hound [p.321](../references/srd-5.1/pages/page-321.md)／
[p.322](../references/srd-5.1/pages/page-322.md)、Mummy [p.333](../references/srd-5.1/pages/page-333.md)、
怪物动作及 Recharge [p.259](../references/srd-5.1/pages/page-259.md)、豁免
[p.83](../references/srd-5.1/pages/page-083.md)、共享伤害与掩护
[p.96](../references/srd-5.1/pages/page-096.md)、抗性／易伤
[p.97](../references/srd-5.1/pages/page-097.md)、临时 HP／零 HP
[p.98](../references/srd-5.1/pages/page-098.md)、专注
[p.102](../references/srd-5.1/pages/page-102.md)、锥形
[p.103](../references/srd-5.1/pages/page-103.md)。规则出处不证明安装、数据库迁移或部署完成。

## 作者、准确身份与支持资格

怪物定义必需完整规则身份、`display_name`、`description`、`source_page` 和 `stat_block`，
可选 `author_note`。`rule_type=adventure.monster`，规则键使用 `monster.*`，出处页界为
258—403。Unicode 码点、NFC、控制符、整数词法、未知／重复字段和直接依赖检查沿用
[作者基础合同](schema-2-foundation-authoring.md)，不得从英文名称寻找对象。
`stat_block` 的 25 个直接字段使用严格 JSON 每对象最多 32 字段的结构预算；深度 16、
token 32768、单字符串 4096 UTF-16 单元、数组 256 项及既定制品字节预算保持，不开放任意嵌套数据。

`stat_block` 是一个完整复合原子，包含体型／生物类别／阵营、AC 及护甲说明、HP 与生命骰、
步行速度、六项属性、实际豁免及技能加值、伤害防御与条件免疫、四类感官和被动察觉、
语言、精确 CR 分数、XP 及有限特性说明。HP 平均值须与生命骰、CON 修正一致；显式怪物
save bonus 是该豁免的最终基础加值，不能再重复加属性或 PB。仅有感官、语言或 trait
文本不表示相应执行器存在；需要这些机制的可达动作仍须独立接入。
每个 trait 另必需类型化 `scope`：`PERCEPTION_HEARING_SMELL_ONLY`、
`ATTACK_ROLL_PACK_TACTICS_ONLY` 或 `UNSUPPORTED`。前两者的完整语义分别只适用指定感官
Perception 和 Pack Tactics 攻击骰，与本次 OTHER_DELIVERY 吐息／DEX／CON 无关；
`UNSUPPORTED` 在真实根预检中拒绝，不能将未知被动能力当零贡献继续。
`damage_defenses` 的每项保留 `kind`、`damage_type` 与 `qualifier`：`ALL` 表示对应伤害
类型的无条件防御，`NONMAGICAL_ATTACKS` 只限 B/P/S 抗性。Mummy 的非魔法攻击 B/P/S
抗性不会作用于吐息；Hell Hound 的 FIRE/ALL 免疫及 Mummy 的 FIRE/ALL 易伤会作用。
本切片不提供角色防御来源，不能把怪物防御表当角色抗性输入。

`actions` 集合的条目使用完整 `EntryIdentity`：原怪物规则身份、`collection_key=actions`、
条目所有者模组和稳定 `entry_key`。条目必需 `action_profile`，可选 `author_note`。
定义和条目各自有启用事实与字段来源链；禁用或来源错误不能退回同名其他动作。

| `action_profile` 分支 | 完整字段与含义 |
|---|---|
| `BLOCKED_METADATA` | 仅 `display_name`、`description`、`source_page`、`mode`；禁止可执行参数，不生成 action、roll 或健康结果。 |
| `SAVE_DAMAGE_BATCH_V1` | 上述字段加准确 `mechanism`、`save_policy`、`damage_profile`、`area`、`recharge`；每组均完整，不接受不同机制的字段拼接。 |

唯一已登记参数为：

| 原子 | 准确值 |
|---|---|
| `mechanism` | `HELL_HOUND_FIRE_BREATH_V1` |
| `save_policy` | `ability_key=ability.dexterity`，`dc_mode=FIXED_DC`，`dc=12`，`fail_outcome=FULL`，`success_outcome=HALF` |
| `damage_profile` | `dice_count=6`，`dice_sides=6`，`damage_type=FIRE`，`delivery=OTHER_DELIVERY`，`ability_modifier=NO_ABILITY_MODIFIER` |
| `area` | `shape=CONE`，`length_feet=15`，`origin_policy=EXCLUDE_ORIGIN` |
| `recharge` | `dice_sides=6`，`minimum_roll=5`，`turn_start=true`，`short_rest=true`，`long_rest=true` |

`stat_block` 和 `action_profile` 只允许整体替换，不能只换 DC 而遗留另一种 profile 的字段。
`author_note` 沿可清空字段白名单；启用状态使用独立 `set_enabled`。纯组合、生产规范编码和
完整字段链保留原包、原始所有者及条目身份，来源与镜像须逐列重建全部原包，包含未选中包。
接受事实、类型表与运行快照不互相替代；来源与快照的读回不授予完整游戏运行资格。

## 实际命令与唯一继续点

`START` 通过封闭 `MonsterDeployment` 绑定怪物业务 UUID、准确最终规则身份、格坐标及阵营。
角色保留原 prepared run/snapshot，怪物引用该场景相同的规则组合；不得跨战役或用角色
身份冒充怪物。位置、先攻、回合及动作／反应仍归场景 owner，怪物 HP 和 action-use 归
场景中的唯一 `MonsterInstance`，不写入角色健康表或复制第二份怪物当前状态。
真实服务仍要求 2—16 个 prepared 角色，加入怪物后场景总数不超过 64；不开放纯怪物或
零／单角色服务入口。当前只接纳 SMALL／MEDIUM 点格怪物，其他体型在部署前拒绝。
作者 HP 字段的词法上界为100000，仍须满足生命骰平均值等约束；当前运行额外要求
HP≤65535。显式 monster save bonus
减相应属性修正必须恰等于其 CR 推导 PB：CR0—4 为2、5—8 为3、9—12 为4、13—16 为5、
17—20 为6；超界、额外修正或双倍熟练不进入这个有限消费者。定义可存储不等于运行可接纳。

`MONSTER_BREATH` 的 `BreathInput` 只接受准确 action 条目身份和服务端八个方向之一。
客户端不能提交目标列表、DC、骰点、伤害、修正、HP、防御、阶段或下一版本。完整依赖、
规则、位置、资源、当前真实回合及容量须在首 RNG 前通过。

| 接受阶段 | 已发生事实和唯一后续 |
|---|---|
| `MONSTER_BREATH` / `SAVE_DAMAGE_ROLLED` | 消费本回合 action 及一次可用吐息，固定完整目标证明；非空目标只掷一次 6d6，保存原骰与共享总值。`SAVE_DAMAGE` 父帧保留为 PENDING，尚无 DEX、packet、HP 或 CON。 |
| `CONTINUE_FLOW` | 绑定原 flow、leaf 及当前乐观版本，只消费未发生后缀：DEX → 共同 APPLY → 强制专注结束 → 共同 CON → 失败结束 → 完成后触发。原 action、原六骰和 release 不再消费。 |
| `SAVE_DAMAGE_COMPLETED` | 所有健康、效果及专注义务已接受；产生准确 `MONSTER_ACTION_COMPLETED` trigger，以真实怪物为 subject、原 batch frame 为 source。若匹配已准备的 Ready，沿同一 flow 保存 OPEN／CLOSED／CONSUMED 和父返回点，继续不能再次结算吐息。 |

没有 Cutting Words 生产消费者时不生成空的伤害骰后反应窗口，也不把上述 PENDING 父帧
称为一个 Cutting Words 机会。GET、保存、恢复和重复旧请求不推进它。吐息不是施法或攻击，
不生成 Shield、Counterspell、攻击命中、暴击或 nonlethal 选择。
完整点格证明为 T 空时接受 `NO_TARGETS`，仍消费一次 action／吐息，但不掷共享骰、不作
DEX／CON、不创建 packet；不能用空结果掩盖未知规则、缺 reader 或不完整目标。
INTERNAL 输入／支持错误使用 `S2_BREATH_COMMAND`、`S2_BREATH_AREA`、
`S2_MONSTER_ACTION_UNSUPPORTED`、`S2_MONSTER_RECHARGE_UNAVAILABLE`、
`S2_MONSTER_SPACE_UNSUPPORTED` 和 `S2_MONSTER_TRAIT_UNSUPPORTED`；版本、当前回合、
容量、冻结事实与唯一继续点沿既有 `S2_COMBAT_*`／`ACTION_FLOW_*`。公共目标合同的
`SAVE_DAMAGE_*` HTTP／文件错误映射尚未据此开放。

只有在本人后续真实 turn START 才为已耗用吐息抽 recharge d6；5／6 恢复，1—4 保持耗用。
读操作和恢复不补 recharge、不提前开始新回合，不因已释放怪物后来失能而取消原伤害。

`Schema2MonsterRestRules` 在 EXPLORATION 接纳 `BEGIN_MONSTER_REST`／
`COMPLETE_MONSTER_REST`。BEGIN 保存真实开始秒、SHORT 一小时／LONG 八小时的到期秒；
COMPLETE 由服务端计算尚余秒数，一次推进全部角色唯一时钟并结清效果到期，然后恢复
该怪物的充能。此前已经走过的时长不重复计时；不是由客户端填写完成后时间。
它不发放角色休息／治疗／资源收益，也不凭空给怪物治疗。存在未稳定零 HP、活动角色
休息、未决 flow、未知效果闭包或容量不足时，完整预检先拒绝；重入战斗中断活动怪物休息。
恢复 ACTIVE／COMPLETED／INTERRUPTED 原事实不发放新恢复，独立角色休息不能绕过场景 owner。

## 完整空间与有限防御

目标 reader 从场景真实角色与怪物位置重建全 roster，逐一保存 `AreaCandidate` 的身份、
位置、纳入结论及原因；敌友、火免疫、零 HP 或未被 UI 选中都不能删去 candidate。
`TargetSetProof` 保存政策、场景 roster 版本、源格／方向、完整 candidates 和稳定 T。
目标顺序为 CHARACTER 在前、MONSTER 在后，同类按 UUID 的无符号网络字节排序；ordinal 连续。
完整证明与所选方向分开，不能由调用方用一个目标子集替代它。

当前空间政策为 `S2_POINT_CONE_CHEBYSHEV_V1`：每格五尺，把真实格坐标作为点，八个方向
按 EAST、SE、SOUTH、SW、WEST、NW、NORTH、NE 对应 `(1,0)`、`(1,1)`、`(0,1)`、
`(-1,1)`、`(-1,0)`、`(-1,-1)`、`(0,-1)`、`(1,-1)`。设相对坐标为 p，方向为 d，
`dot=p·d`，`cross=p.x*d.y-p.y*d.x`；纳入恰为
`dot>0 && dot<=3*(d.x*d.x+d.y*d.y) && 2*abs(cross)<=dot`。原点排除，边界按该点格政策纳入。
全 roster 是 START 接纳并加入先攻的所有真实角色与怪物；当前没有另一份战外／隐藏
non-initiative creature roster，不能据此宣称读取了尚未登记的全冒险场景。

这一政策继承内部战斗正常可见平面格、无障碍与无掩护的前提，是明确的项目测量选择，
不声称是通用三维体积／遮挡引擎或原设计 golden 的占位交集政策。缺位置、未知体型／
空间政策、未支持的遮挡或掩护不能按“无贡献”忽略；相关分支在首 RNG 前拒绝。

DEX 使用真实目标修正、实际 save 熟练和当前有效 Bless；不把普通 save 的自然 1／20
当必败／必成。自动失败不掷假 d20；优势／劣势按当前真实贡献合并，并保留全部原骰。
角色 save 熟练来自 `ProficiencyCategory.SAVING_THROW` 的准确属性定义和真实初始
FIXED 成长授予；相同属性按完整来源去重后只加一次 PB，不能从职业显示名硬编码。
只有已经接入且可证明的条件与贡献进入本切片，Evasion、Legendary Resistance、任意
反伤、特殊减伤或其他未知消费者仍阻断。
闪避只在当前速度大于零且其余真实条件满足时为 DEX 提供优势。当前没有怪物 OA 的可执行
攻击／reach producer；场景中有存活、敌对且 reaction 可用的怪物时，PC MOVE 及 Ready
move 的声明／释放在首 RNG 前以 `S2_MONSTER_REACTION_UNSUPPORTED` 保守拒绝，不能省略
可能的怪物机会攻击。友方或已死怪物不构成该威胁；怪物自己的移动、攻击与 Ready 根仍以
`S2_MONSTER_ACTION_UNSUPPORTED` 拒绝，Bite 元数据不能补出这些机制。

共享六骰总值 R 对每目标只引用一次。失败取 R，成功先 `floor(R/2)`，再其他已支持修正、
抗性向下取半、易伤乘二；免疫得到零。同种防御重复不重复计算，抗性与易伤并存按顺序执行。
保留准确来源证明，不能仅存一个无来源布尔值。物理／魔法等适用 gate 不同的防御不能
因为同一个 damage type 就混为同一无条件贡献。

## 共同伤害、健康与专注

所有 DEX 结束后才取得全体目标的同一 APPLY cut：先确定全部 packet 和健康计划，再
应用全部健康变化。任何一个目标失能引起的效果结束，都不能倒改另一个目标本次减免。
角色 temp HP、HP、死亡／稳定、倒地和掉物由原健康／装备 owner 产生真实因果子事件；
怪物采用本切片明确的零 HP 死亡政策，不伪造 PC 死亡豁免。

packet 保存 target ordinal、共享 roll、唯一 save、pre HP/temp/max HP、防御证明、实际
damage、temp 吸收及 post HP/temp/dead；实际伤害不等于 HP delta。只消耗 temp HP
仍是真实健康变化；免疫且无其他变化不虚增健康版本。

全体健康后果成立后先合并处理所有失能／死亡导致的 holder 强制结束及完整效果闭包。
其余受正伤害 holders 才需要 CON，DC 为 `max(10,floor(actual damage/2))`，temp HP
完全吸收也不免除。建立统一 CON cut 后按目标顺序使用同一份贡献；一个 CON 失败不能
在其他本批 CON 中途撤掉该 cut 内的 Bless。所有结果确定后再批量结束失败 holders。
Bless、Ready 持能量、独立压制及同名覆盖继续由真实效果 owner 接合；没有额外平行 holder。

## 存储、版本与直接恢复

来源 V011 与镜像 V039 保存类型化怪物、动作复合原子及完整覆盖链；运行 V040 保存怪物
部署、初始／当前／历史状态、action-use、完整目标证明、共享骰、checks、packets 和
concentration obligations，并扩展准确命令与 flow 类型。既有迁移和持久化标识不改写。
安装／授权与[只读事实核验](../../database/verify/schema2-monster-breath.sql)是分开的检查点；
核验只返回完整图的可见结构和异常行，不填补缺失数据或继续 pending。
`JdbcSchema2MonsterBreathRows` 沿原场景事件／phase 保存并重建这些类型化行：

| V040 物理行 | 责任 |
|---|---|
| `s2_combat_monster_deployment`、`s2_combat_breath_input` | 完整 START 部署与封闭吐息意图，不接收客户端结果。 |
| `s2_combat_monster`、`s2_combat_monster_action` | 前后怪物健康及准确 action-use，关联原场景历史。 |
| `s2_combat_save_damage_batch`、`s2_combat_breath_candidate`、`s2_combat_breath_target` | 原 release／frame、阶段、完整 roster/membership 和连续 T。 |
| `s2_combat_breath_die`、`s2_combat_breath_check`、`s2_combat_breath_packet`、`s2_combat_breath_defense`、`s2_combat_breath_concentration` | 原共享骰、save／CON 依据与结果、每目标健康计划／防御来源和 holder 义务；缺少任一被引用节点均拒绝。 |
| `s2_combat_monster_rest_input`、`s2_combat_monster_rest` | 准确休息意图、开始／到期／完成或中断事实及时间因果引用。 |

| 事实 | 唯一 owner 与保存／恢复要求 |
|---|---|
| 怪物身份与 stat block 来源 | 保存准确 rule/action 身份及同一组合绑定；恢复从原镜像核验，不向最新来源补字段。 |
| 位置、先攻、回合、action/reaction、frame/window | 原场景与唯一 flow；保存当前回合及所有祖先／终结引用，不只留顶层 WAITING 标记。 |
| 怪物 HP/dead/action-use | `MonsterInstance`；保留原初始及每个前后状态，恢复不回满、不补休息或 recharge。 |
| 怪物休息 | `MonsterRest` 的准确 key、monster、SHORT／LONG、开始／到期秒、状态及结束操作引用；恢复不重计时间、重发充能或抹去中断事实。 |
| batch/release/T/shared roll/checks/packets | `SaveDamageBatch` 的不可变因果事实；连续 ordinal、完整 candidate、原六骰、各自依据及准确已完成前缀必须齐全。 |
| 角色 HP/temp/死亡／装备／时间 | 原角色历史；根引用其全部有序因果子事件，共事务提交，不从 packet 另造当前角色。 |
| holder/source/application/suppression/Ready | 原持续效果 owner；强制结束、失败结束、原状态与所有 T 外目标引用一并保留，ENDED 不复活。 |
| 请求与 accepted/UNKNOWN | 原操作身份、完整意图和不可变接受结果；新阶段必须新意图与当前版本，UNKNOWN 只读查证，不自动继续。 |

Service 在共同事务内读取准确历史、校验规则与全部版本、预留强制后缀、消费骰子、写
角色因果子事件与场景根并实际读回；叶仓储不提交或回滚。任一后缀写失败只能回滚该次
事务，之前已经接受的 release/raw/action-use 保留。新根写失败则本命令的全部成本、
骰审计、版本及事件一起回滚；骰源本身无需倒退。

内部 `Save` 及 `restore` 核对完整业务图、规则和保存骰点后直接写回当前状态及完整历史，
不调用随机源、不重施法、不复付成本、不重伤害、不 PASS、不恢复效果、不推进时钟。
PENDING raw 和 after-batch Ready 机会必须由真实生产命令产生后保存，手工拼接 paused
对象或仅 writer/reader 自洽往返不能充当完整恢复证据。

完整保存/恢复须覆盖的引用闭包包含怪物部署／current/history/action-use → batch/source/action →
candidate/target/check/shared roll/packet/obligation → flow/window/Ready/effect → 角色因果子事件
与规则快照，并包含 monster rest／完成操作／时间子事件引用；死亡、源结束或 batch COMPLETE 均不授权删除历史。完整 FINAL 文件、
发布/接受后的私有运行目录清理、全域恢复映射与容量验收仍按[保存与恢复目标](../design/v2/save-and-recovery.md)
单独采用，不能把局部仓储恢复写成完整战役存档或授权删除工作集。

## 独立验证边界

[测试指南](../testing.md#schema-2-有限怪物与多目标豁免伤害验证)按作者／来源／镜像、真实命令、
共同 cut、事务与恢复分别选择测试。固定 oracle 从本地 SRD 和明确初始状态手算，禁止
调用被测 planner、encoder 或 reducer 生成期望。

公共设计原 golden 使用 Protection from Energy 与 Cutting Words，当前未接入这些消费者；
其原期望继续作为目标设计，不能直接投喂当前入口或声称已有相关反应窗口。实际服务验收
须用真实 Bless、Ready 持能量、怪物防御、合法来源和完整空间构造同类边界；保存原始
输入、骰流和独立结果，并从实际仓储读回所有领域，而不是只比较行数或头摘要。

[独立服务 oracle](../../src/test/resources/schema2-monster/breath-runtime-golden.json)使用真实
一级生命领域角色 A／B：各 HP9、DEX／CON +1、无 DEX／CON 熟练，B 先由真实环境
效果取得 temp HP5；A 实际施放 Bless 到 A／B，B 实际 Ready 持有 Cure Wounds，
触发为 H 的怪物动作完成。H、A、B、另一 Hell Hound I、Mummy M 的格坐标为
`(0,0)`、`(1,0)`、`(2,0)`、`(2,1)`、`(3,0)`，EAST 得 T=A／B／I／M。
六骰 `1,2,1,2,2,3` 合为 11；DEX 原骰为 9／10／1／2，A／B 各真实 Bless d4=1。

| 目标 | save total／结果 | 真实伤害 | post HP／temp | 伤害后事实 |
|---|---|---:|---|---|
| A | 11／失败 | 11 | 0／0 | overflow2 < max9，不即死；倒地掉物并强制结束 Bless，无 CON。 |
| B | 12／成功 | 5 | 9／0 | temp 吸收全部仍需 DC10 CON；A 的 Bless 已结束，原 d20=9 加 CON1 恰成功，无旧 d4。 |
| I | 2／失败 | 0 | 45／0 | 完整 save 保留，fire immunity 生效，不推进无变化健康。 |
| M | 1／失败 | 22 | 36／0 | fire vulnerability 只应用一次；非魔法攻击 B/P/S 抗性不适用。 |

至 after-batch Ready 窗口共 13 个逻辑骰，四包都引用同一六骰；实际伤害和 38，HP 总损失
31，差额恰为 temp 吸收5与 A overflow2。B 随后真实释放 Ready Cure，d8=4，加 WIS−1
和生命门徒3，治疗 A 到 HP6；A 保持倒地／掉物，原 ENDED Bless 不复活。释放只新增
一个逻辑骰和一次 reaction，不重复法术位或 action；含释放合计 14 个逻辑骰。

必须观测全体预检零 RNG／零写入、末尾坏对象、stale／同键异意图、合法 NO_TARGETS、
自然 1／20、奇数半伤、免疫仍 save、易伤、temp HP 全吸收、零 HP／即死／掉物、
强制结束先于共同 CON、失败批量结束、after-batch Ready、recharge、pending 直接恢复、
缺行／孤儿／坏引用拒绝、末尾角色／效果／accepted 写失败回滚及 UNKNOWN 原操作查证。
JDBC 代理、静态 SQL、测试内容包和局部保存不代替真实 MySQL、安装接受、Host、
完整文件、技术 END／运行目录清理、部署或跨域发布门。
