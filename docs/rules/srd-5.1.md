# SRD 5.1 简化规则规范

> 状态：已发布简化 v1 的当前公开规则合同
>
> 版本：规则版本 `1`，规范格式版本 `canonical_format_version=1`
>
> 适用范围：Local 5E Campaign Tool 当前冻结的单机、单 DM v1

本文是内置发布版 `dnd5e2014_srd51_se_v1` 的规范性设计来源。它以 D&D 5e 2014/SRD 5.1 的基本属性、技能、豁免、等级与 d20 检定概念为参考，但只保留本工具真正实现的数据与计算；不试图复述或实现完整规则书。

本文不是内容哈希的直接输入。实际哈希来自与本文逐项一致的 `module_*` 关系数据，经 `canonical_format_version=1` 序列化后计算。Markdown 的排版、文件名、提交时间和文档换行不得进入哈希。

项目的长期目标已经扩展为完整覆盖 SRD 5.1，见[完整 SRD 5.1 覆盖目标](srd-5.1-complete.md)。
该目标不改变本文对应的已发布 v1：本文列出的简化、排除项、稳定键、算法、23 个规范分区、
内容摘要和旧战役解释继续保持不变。

## 1. 决策摘要

- 初版只有一个内置模组：`dnd5e2014_srd51_se_v1`，版本为 `1`；不提供上传、编辑、安装、选择或在线下载模组的页面/API。
- PC 与 NPC 使用同一角色数据模型；两者的区别只在 `character_type`，没有玩家所有权、批准状态或不同的可见字段。
- 规则仅覆盖角色卡基础数值、技能/豁免计算、简单物品，以及下一阶段的 d20 检定和有限类型化效果。
- 数值的权威输入是角色字段、职业等级和熟练关系；属性调整值、总等级、熟练加值、技能/豁免加值均在运行时计算，不保存可编辑副本。
- 初版不会自动处理攻击、伤害、法术、职业特性、种族、背景、专长、负重、货币、消耗次数、死亡豁免或状态效果。

## 2. 发布版身份与稳定键

| 项目 | 固定值 |
|---|---|
| 模组键 | `dnd5e2014_srd51_se_v1` |
| 发布版版本 | `1` |
| 规范格式版本 | `1` |
| 哈希算法 | `SHA-256` |
| 发布状态 | 完整校验前为 `DRAFT`；校验通过后仅可为不可变的 `RELEASED` |
| 可用发布版 | 初版恰好一个；不存在有效 `RELEASED` 时拒绝创建战役 |

所有模组稳定键使用小写 ASCII，格式为 `[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*`。稳定键是哈希、关系引用和导入比较的依据；页面显示名可以本地化，但不参与身份匹配。

## 3. 角色基础字段

角色创建时使用下表默认值。DM 可在主机页面修改基础字段；修改必须满足范围并递增角色聚合 `row_version`。PC/NPC 均适用。

| 稳定键 | 中文名 | 类型 | 默认值 | 有效范围 | 说明 |
|---|---|---:|---:|---:|---|
| `ability.strength` | 力量 | 整数 | 10 | 1—30 | 基础属性 |
| `ability.dexterity` | 敏捷 | 整数 | 10 | 1—30 | 基础属性 |
| `ability.constitution` | 体质 | 整数 | 10 | 1—30 | 基础属性 |
| `ability.intelligence` | 智力 | 整数 | 10 | 1—30 | 基础属性 |
| `ability.wisdom` | 感知 | 整数 | 10 | 1—30 | 基础属性 |
| `ability.charisma` | 魅力 | 整数 | 10 | 1—30 | 基础属性 |
| `hp.maximum` | 最大 HP | 整数 | 1 | 1—999 | 不由职业或体质自动推导 |
| `hp.current` | 当前 HP | 整数 | 1 | 0—`hp.maximum` | 0 仅是数值；初版没有昏迷、死亡或死亡豁免规则 |
| `armor_class` | AC | 整数 | 10 | 0—99 | 不由装备、属性或法术自动推导 |
| `speed.ground` | 地面速度 | 整数 | 30 | 0—999 | 单一速度，单位为英尺；不实现飞行、游泳、攀爬等速度 |

角色名称在保存前去除首尾空白并规范化为 Unicode NFC，长度为 1—80 个 Unicode 码点，不能包含控制字符。允许重名，名称不参与身份判断。

`character_key` 由服务端生成小写标准 UUID；改名、PC/NPC 类型变化、归档和恢复不得改变该键。复制角色必须生成新键。

## 4. 职业、等级和熟练

### 4.1 职业目录

职业在本初版仅记录名称和等级，不带职业特性、生命骰、装备、施法或前置条件。允许多职业；按职业稳定键排序后进入规范字节流。

| 稳定键 | 显示名 |
|---|---|
| `class.barbarian` | 野蛮人 |
| `class.bard` | 吟游诗人 |
| `class.cleric` | 牧师 |
| `class.druid` | 德鲁伊 |
| `class.fighter` | 战士 |
| `class.monk` | 武僧 |
| `class.paladin` | 圣武士 |
| `class.ranger` | 游侠 |
| `class.rogue` | 游荡者 |
| `class.sorcerer` | 术士 |
| `class.warlock` | 邪术师 |
| `class.wizard` | 法师 |

单个职业等级为 1—20；所有职业等级之和为 0—20。空白新角色和无职业 NPC 可以是总等级 0。每个职业在同一角色中最多一行。

### 4.2 熟练加值

总等级为 0 时使用 `+2`，便于空白角色立即用于基础检定；总等级 1—20 使用 2014 版熟练加值表。

| 总等级 | 熟练加值 |
|---:|---:|
| 0—4 | +2 |
| 5—8 | +3 |
| 9—12 | +4 |
| 13—16 | +5 |
| 17—20 | +6 |

技能和豁免各自保存一个熟练倍数枚举：`NONE=0`、`HALF=0.5`、`FULL=1`、`EXPERTISE=2`。不允许保存其他小数或负值。半熟练使用向下取整：`floor(proficiencyBonus / 2)`。

## 5. 技能与豁免

### 5.1 技能目录

| 稳定键 | 技能 | 对应属性 |
|---|---|---|
| `skill.acrobatics` | 体操 | 敏捷 |
| `skill.animal_handling` | 驯兽 | 感知 |
| `skill.arcana` | 奥秘 | 智力 |
| `skill.athletics` | 运动 | 力量 |
| `skill.deception` | 欺瞒 | 魅力 |
| `skill.history` | 历史 | 智力 |
| `skill.insight` | 洞悉 | 感知 |
| `skill.intimidation` | 威吓 | 魅力 |
| `skill.investigation` | 调查 | 智力 |
| `skill.medicine` | 医药 | 感知 |
| `skill.nature` | 自然 | 智力 |
| `skill.perception` | 察觉 | 感知 |
| `skill.performance` | 表演 | 魅力 |
| `skill.persuasion` | 游说 | 魅力 |
| `skill.religion` | 宗教 | 智力 |
| `skill.sleight_of_hand` | 巧手 | 敏捷 |
| `skill.stealth` | 隐匿 | 敏捷 |
| `skill.survival` | 求生 | 感知 |

### 5.2 豁免目录

六项豁免稳定键为 `save.strength`、`save.dexterity`、`save.constitution`、`save.intelligence`、`save.wisdom`、`save.charisma`。它们分别使用同名属性。

### 5.3 派生公式

令 `M(a) = floor((ability[a] - 10) / 2)`，令 `P` 为总等级对应的熟练加值，令 `Q(x)` 为技能或豁免 `x` 的熟练倍数，则：

```text
属性调整值          = M(属性值)
总等级              = 所有职业等级之和
熟练贡献            = 0 / floor(P / 2) / P / 2P
技能加值            = M(该技能对应属性) + 熟练贡献
豁免加值            = M(该豁免对应属性) + 熟练贡献
```

实现必须使用整数算法，特别是负属性调整值和半熟练都不能依赖浮点数。Java 实现以 `Math.floorDiv(score - 10, 2)` 计算属性调整值，以 `Math.floorDiv(proficiencyBonus, 2)` 计算半熟练。

## 6. 简单物品

物品实例只包含持有者、来源、名称、说明、数量和状态。没有重量、价格、货币、槽位、耐久度、充能、消耗次数、攻击数据或自动属性修改。

内置模组提供下列最小模板，用于验证模组物品授予和存档引用：

| 稳定键 | 显示名 | 说明 |
|---|---|---|
| `item.backpack` | 背包 | 用于携带物品的普通背包 |
| `item.rope_hempen_50ft` | 50 英尺麻绳 | 普通麻绳 |
| `item.torch` | 火把 | 普通照明用火把 |

每个物品实例的数量为 1—999。删除物品使用归档状态，不物理删除；多个相同模板可作为独立实例存在。临时物品只能有名称、说明和数量，同样不得产生自动规则效果。

## 7. 内置 NPC 模板

模板用于快速创建 NPC，不包含攻击、伤害、特性或自动行为。创建后得到普通角色实体，DM 可按角色规则继续修改。

| 稳定键 | 名称 | STR/DEX/CON/INT/WIS/CHA | HP | AC | 速度 |
|---|---|---|---:|---:|---:|
| `npc.commoner` | 平民 | 10/10/10/10/10/10 | 4 | 10 | 30 |
| `npc.guard` | 守卫 | 13/12/12/10/11/10 | 11 | 16 | 30 |
| `npc.wolf` | 狼 | 12/15/12/3/12/6 | 11 | 13 | 40 |

模板可带有预设技能/豁免熟练关系，但 v1 的这三个模板默认均为 `NONE`，职业等级为 0。模板内的每一个基础字段和关系值均参与发布版哈希。

## 8. 检定规则

### 8.1 可执行的检定

只有下列四种检定入口：

| 检定类型 | 修正值来源 |
|---|---|
| `ABILITY` | 指定一项属性的派生调整值 |
| `SKILL` | 指定一项技能的派生加值 |
| `SAVING_THROW` | 指定一项豁免的派生加值 |
| `MANUAL` | DM 输入有名称的固定整数修正 |

`MANUAL` 修正为 -99—+99，名称经 NFC 规范化后长度为 1—80 个码点且无控制字符。它是 DM 直接执行的检定入口，不依赖 `module_event_template` 或 `module_event_check`，但成功执行后仍创建关联内部事件。MANUAL 若携带效果，授权边界是同一冻结发布版中的 `module_effect_definition` 全局白名单和对应 `module_effect_parameter`，而不是不存在的事件模板关系；未知效果、未发布效果或当前数据库阶段尚未启用的效果必须拒绝。该解释沿用已发布 v1 的既有关系，不增加或修改模组数据。前端不能直接提交属性、技能或豁免的最终加值；服务端必须从当前角色状态重新计算。

所有四种检定都必须携带执行者的预期角色版本；包含角色目标的效果还必须携带每个可能目标的稳定角色键和预期版本。Service 在掷骰前锁定并核对执行者与全部目标，版本冲突不生成骰子或事件。

### 8.2 掷骰与比较

| 模式 | 候选骰 | 选中骰 |
|---|---:|---|
| `NORMAL` | 1 个 d20 | 唯一候选骰 |
| `ADVANTAGE` | 2 个 d20 | 较高值；相同则选择第一个 |
| `DISADVANTAGE` | 2 个 d20 | 较低值；相同则选择第一个 |

每个 d20 由服务端均匀生成 1—20。总计为 `selectedD20 + modifier`；DC 为 0—60 的整数；当且仅当总计大于等于 DC 时成功。自然 1 和自然 20 没有自动失败、自动成功、暴击或特殊效果。客户端既不能提交骰点，也不能覆盖已经保存的结果。

每次执行必须保存模式、全部候选骰及其顺序、选中骰、修正来源、修正值、总计、DC、成功/失败和关联内部事件。同一 `host_operation.request_id` 的重试通过该成功操作保存的唯一根事件返回原结果，不再次掷骰；这一根事件要求不追溯到既有的战役、角色或角色卡操作。

## 9. 事件模板、效果与地图

### 9.1 事件模板

内置事件模板只描述 DM 操作的类别，不含脚本：

| 稳定键 | 名称 | 允许的检定 |
|---|---|---|
| `event.note` | 记录说明 | 不掷骰 |
| `event.ability_check` | 属性检定 | `ABILITY` |
| `event.skill_check` | 技能检定 | `SKILL` |
| `event.saving_throw` | 豁免检定 | `SAVING_THROW` |

`event.note` 是特殊的事件模板路径，不是非模板路径。它必须精确匹配已发布关系 `event.note → effect.append_event_message`，只接受一条 1—500 码点的 NFC 消息并直接创建一个根内部事件；它不创建 `check_execution`、骰子、效果计划或效果参数值记录。其余三个模板分别只允许对应的属性、技能或豁免检定。

`MANUAL` 按第 8.1 节作为直接检定执行，不为已经发布的 v1 数据补造事件模板；它按冻结发布版的全局效果定义授权。ABILITY、SKILL 和 SAVING_THROW 仍必须同时通过事件模板的 `module_event_check` 与 `module_event_effect` 关系授权。DM 可以指定 DC、成功/失败分支以及相应边界批准的效果稳定键和类型化参数；客户端不能提交算法名、任意效果实现、JSON 参数包或脚本文本。

### 9.2 效果类型

| 稳定键 | 允许参数与约束 |
|---|---|
| `effect.adjust_current_hp` | 目标角色和 -999—+999 的整数变化；结果钳制在 0—最大 HP；只表示 DM 手工调整，不进行攻击或伤害结算 |
| `effect.grant_module_item` | 目标角色、有效内置物品键和数量 1—999 |
| `effect.grant_temporary_item` | 目标角色、NFC 名称 1—80 个码点、NFC 说明 0—500 个码点和数量 1—999；名称/说明不含控制字符且不附带规则效果 |
| `effect.set_entity_position` | 有效地图键、有效节点键和已存在的 PC/NPC |
| `effect.append_event_message` | NFC 文本，1—500 个码点，无控制字符 |

检定命令保存成功/失败两分支的类型化效果计划。每条效果保存分支、从 1 开始且连续的分支内顺序、冻结发布版和效果键；每个参数另存参数键、参数顺序、声明类型以及恰好一个匹配类型的值。参数集合必须与 `module_effect_parameter` 完全一致，不能缺少、重复或增加参数。实际分支由检定结果唯一决定，不保存独立可编辑的“已执行”标志；未选中分支只保存计划，不改变权威状态或生成字段变化。

效果按请求中明确的序号执行，且执行算法只能由冻结模组中的 `effect_key` 决定。`effect.append_event_message` 每个分支最多一条，实际分支的消息写入本次检定唯一根事件的文本，不另建事件。任一分支计划、参数、对象、预期版本、物品/地图/节点归属非法，或任一写入失败时，检定、骰子、效果计划、字段变化、根事件和幂等结果必须在同一事务中整体回滚。一次成功命令中，同一目标角色无论被多少个效果修改，其聚合 `row_version` 只递增一次。

V009 只启用 `effect.adjust_current_hp`、`effect.grant_module_item`、`effect.grant_temporary_item` 和 `effect.append_event_message`。`effect.set_entity_position` 虽已属于冻结发布版，仍须在 V010 运行表与校验全部就绪前以稳定“不支持”业务错误失败关闭。

### 9.3 最小地图

初版只提供节点地图 `map.tavern_cellar`，没有格距、移动力、遮挡、战争迷雾或自动寻路。

| 节点键 | 名称 |
|---|---|
| `node.street` | 街道 |
| `node.entry` | 酒馆入口 |
| `node.common_room` | 大厅 |
| `node.cellar` | 地窖 |

无向连接为：`street—entry`、`entry—common_room`、`common_room—cellar`。实体只能放在本表节点。DM 可以直接把实体放到任意合法节点；连接只用于显示地图拓扑，不限制移动路径。位置变化不自动消耗速度、不触发遭遇，也不执行距离、先攻、回合或行动规则。

### 9.4 最小遭遇状态

初版的遭遇状态只是 DM 的当前场景记录，不是战斗规则引擎：

- 每个战役最多一个 `ACTIVE` 遭遇，已结束遭遇标记为 `CLOSED`；
- 参战阵营只允许 `ALLY`、`ENEMY`、`NEUTRAL`；
- 同一角色在当前遭遇中只能出现一次；
- 遭遇必须引用本战役基于冻结模组创建的有效节点地图实例；
- `entity_position` 只属于当前活动遭遇中已经存在的参与者，并通过组合关系约束到该参与者、遭遇地图实例和合法节点；
- 未参战角色必须先显式加入遭遇，DM 直接定位和 `effect.set_entity_position` 都不能隐式加入；
- 队伍世界节点由独立的 `party_world_position` 保存，不由实体定位效果修改；
- 遭遇记录不包含先攻、轮次、行动、反应、移动力、距离、攻击或伤害结算。

数据库必须用只在 `ACTIVE` 状态生成战役 ID 的可空生成列及唯一索引，或等价的数据库级约束，防止并发请求为同一战役创建两个活动遭遇；只依赖 Service 先查询后插入不满足本规范。`battle_participant` 以遭遇和角色为唯一关系，同一角色可在不同的已关闭历史遭遇中再次出现。

## 10. 明确排除的规则

以下内容即使在相关原始规则中存在，也不是本发布版的功能：

- 种族、背景、职业特性、专长、经验、等级提升自动结算和角色构筑前置条件；
- 法术、法术位、已知法术、攻击、命中、伤害、抗性、易伤、重骰、爆骰和通用骰式；
- 死亡豁免、昏迷、休息、状态、先攻、回合、行动、距离、负重、货币、价格和消耗次数；
- 装备自动改变 AC、属性、速度、技能或豁免；
- 玩家账号、所有权、公开显示、多人同步、聊天、投票、脚本插件和任意 JSON 规则参数；
- DM 可见模组管理、模组编辑、模组选择、模组导入或在线下载。

若页面或导入数据要求上述能力，应用必须拒绝并返回稳定的通用业务错误，不得伪装成已结算的规则结果。

## 11. 规范哈希要求

为了使发布版可验证和战役可冻结，以下数据都必须进入 `canonical_format_version=1` 的规范字节流：

1. 发布版身份、版本、规则常量、数值范围和熟练加值表；
2. 所有字段、技能、豁免和职业定义；
3. NPC 模板及其每个基础字段、职业和熟练关系；
4. 物品、事件模板、效果定义、地图、节点和连接；
5. 所有稳定键、显示名和说明文本。

### 11.1 哈希边界

规范字节流是从一次一致性数据库读取中得到的逻辑投影，不是 SQL 文件、SQL 文本、Markdown、JSON、Java 对象序列化或数据库物理行的直接拼接。生成字节流前必须完成全部必填值、稳定键格式、唯一性、引用完整性、数值范围和已知算法编号校验；任一未知、缺失、重复、悬空或越界数据都使发布校验失败，不得通过跳过坏行继续计算。

发布版的 `module_key`、`release_version`、`canonical_format_version` 和 `hash_algorithm` 进入哈希。下列值不进入哈希：

- `module_release.id` 和所有其他数据库内部 ID；
- `content_sha256` 本身、`release_status`、`created_at`、`released_at`；
- 数据库插入顺序、自增值、索引、表名、列名和存储引擎信息；
- 模式迁移的 SHA-256、应用日志、行版本和运行时缓存；
- 战役、角色、物品实例、检定结果、事件、幂等记录和其他运行数据。

`content_sha256` 和发布状态被排除是必要的：它们是规范哈希的结果和发布流程状态，若参与输入将形成循环。数据库表名和列名也不定义发布版身份；实际 SQL 表必须无损映射到本节规定的逻辑分区、记录和字段。

### 11.2 基础编码

除非另有说明，所有长度和数量都是无符号 32 位大端整数 `U32`。实现必须拒绝负长度、超过 `2147483647` 字节的单值或无法放入 Java 数组的长度，不能发生截断或整数溢出。

`NAME(s)` 用于分区名和字段名，编码为 `U32(字节数) + US-ASCII 字节`。名称必须与本节清单完全相同，不能进行大小写转换或本地化。

每个字段值使用一个 1 字节类型标签。`NULL` 只有标签且没有长度或载荷；其他类型均编码为 `标签 + U32(载荷字节数) + 载荷`：

| 标签 | 名称 | 载荷规则 |
|---:|---|---|
| `0x00` | `NULL` | 无载荷；只允许用于投影清单中标有 `?` 的字段 |
| `0x01` | `TEXT` | 按下述文本规则规范化后的 UTF-8 |
| `0x02` | `IDENTIFIER` | 非空 US-ASCII；大小写敏感，不进行文本规范化 |
| `0x03` | `INTEGER` | 十进制 US-ASCII；无前导 `+`、无多余前导零，零只能是 `0`，负数形如 `-99` |
| `0x04` | `DECIMAL` | 十进制 US-ASCII；不用指数、无前导 `+`、去除无意义的尾随零，负零写作 `0` |
| `0x05` | `BOOLEAN` | 长度固定为 `1`；假为单字节 `0x00`，真为单字节 `0x01` |

`TEXT` 先把 `CRLF` 转为 `LF`，再把剩余 `CR` 转为 `LF`，最后执行 Unicode NFC，然后以严格 UTF-8 编码。不能替换非法 Unicode、未配对代理项或无法编码的字符；遇到它们必须失败。除业务字段自身明确要求的保存前 `trim` 外，不折叠空格、不删除尾随空格、不改变大小写，也不增加结尾换行。

`DECIMAL` 的数学值必须精确处理。Java 实现使用 `BigDecimal`，将零统一为 `0`，非零值使用 `stripTrailingZeros().toPlainString()`；禁止经过 `float` 或 `double`。例如 `0.5`、`0.50` 和 `5E-1` 都编码为 `0.5`。

以下是字段值的最小编码示例，空格只用于阅读，不属于字节流：

```text
NULL                 = 00
INTEGER 0            = 03 00000001 30
DECIMAL 0.5          = 04 00000003 302e35
TEXT “狼”            = 01 00000003 e78bbc
BOOLEAN true         = 05 00000001 01
IDENTIFIER “NORMAL”  = 02 00000006 4e4f524d414c
```

### 11.3 容器格式

完整字节流严格按以下顺序写入，不使用分隔符、平台换行、Java 原生序列化或数据库默认字符集转换：

```text
US-ASCII("DND_TOOL_SE_MODULE_CANONICAL")
U32(1)                                      // canonical_format_version
U32(分区数量)
对 11.4 中每个分区按表格顺序：
    NAME(分区名)
    U32(该分区记录数)
    对按规定排序后的每条记录：
        U32(字段数量)
        对该分区字段清单中的每个字段按清单顺序：
            NAME(字段名)
            编码后的字段值
```

所有 11.4 分区都必须出现一次，即使记录数为零；不能出现未知分区。每条记录必须输出清单中的全部字段，包括值为 `NULL` 的可空字段；不能省略字段或增加未知字段。分区数量、记录数量和字段数量也参与哈希。

同一排序键不得重复。字符串排序键比较规范化后 UTF-8 字节的无符号字典序：逐字节按 `0`—`255` 比较，公共前缀相同时较短者在前；不得使用数据库排序规则、Java 默认区域设置或中文拼音顺序。复合键按列从左到右比较；整数键按数学值比较。连接端点先按上述字节序规范化为 `endpoint_low_key < endpoint_high_key`，自连接和反向重复均非法。

### 11.4 固定逻辑投影

下表顺序就是固定分区顺序。字段在单元格中的顺序就是记录字段顺序；`I`、`T`、`Z`、`D`、`B` 分别表示 `IDENTIFIER`、`TEXT`、`INTEGER`、`DECIMAL`、`BOOLEAN`，`V` 表示由相邻 `value_type` 指定的对应标量类型，`?` 表示允许 `NULL`。未标 `?` 的字段一律非空。

| 分区名 | 固定字段顺序 | 分区内排序键 |
|---|---|---|
| `release` | `module_key:I`, `release_version:I`, `canonical_format_version:Z`, `hash_algorithm:I` | 恰好一条 |
| `rule_constant` | `constant_key:I`, `value_type:I`, `value:V` | `constant_key` |
| `field_definition` | `field_key:I`, `display_name:T`, `data_type:I`, `default_value:V`, `minimum_value:V?`, `maximum_value:V?`, `dependent_max_field_key:I?`, `unit:T?`, `description:T` | `field_key` |
| `class_definition` | `class_key:I`, `display_name:T` | `class_key` |
| `proficiency_tier` | `proficiency_key:I`, `enum_code:I`, `numerator:Z`, `denominator:Z`, `rounding_algorithm:I` | `proficiency_key` |
| `proficiency_bonus_band` | `minimum_total_level:Z`, `maximum_total_level:Z`, `bonus:Z` | 三个字段的数值顺序 |
| `skill_definition` | `skill_key:I`, `display_name:T`, `ability_field_key:I` | `skill_key` |
| `save_definition` | `save_key:I`, `ability_field_key:I` | `save_key` |
| `item_template` | `item_key:I`, `display_name:T`, `description:T` | `item_key` |
| `npc_template` | `template_key:I`, `display_name:T` | `template_key` |
| `npc_template_field_value` | `template_key:I`, `field_key:I`, `value:V` | `template_key`, `field_key` |
| `npc_template_class_level` | `template_key:I`, `class_key:I`, `level:Z` | `template_key`, `class_key` |
| `npc_template_proficiency` | `template_key:I`, `target_kind:I`, `target_key:I`, `proficiency_key:I` | `template_key`, `target_kind`, `target_key` |
| `check_definition` | `check_key:I`, `enum_code:I`, `modifier_algorithm:I` | `check_key` |
| `roll_mode` | `roll_mode_key:I`, `enum_code:I`, `candidate_count:Z`, `selection_algorithm:I` | `roll_mode_key` |
| `event_template` | `event_key:I`, `display_name:T` | `event_key` |
| `event_allowed_check` | `event_key:I`, `check_key:I` | `event_key`, `check_key` |
| `event_allowed_effect` | `event_key:I`, `effect_key:I` | `event_key`, `effect_key` |
| `effect_definition` | `effect_key:I`, `execution_algorithm:I` | `effect_key` |
| `effect_parameter` | `effect_key:I`, `parameter_key:I`, `data_type:I`, `reference_kind:I?`, `minimum_value:V?`, `maximum_value:V?`, `text_normalization:I?`, `reject_control_characters:B?`, `parameter_order:Z` | `effect_key`, `parameter_order`, `parameter_key` |
| `map_definition` | `map_key:I`, `map_type:I` | `map_key` |
| `map_node` | `map_key:I`, `node_key:I`, `display_name:T` | `map_key`, `node_key` |
| `map_connection` | `map_key:I`, `endpoint_low_key:I`, `endpoint_high_key:I` | 三个字段的字节顺序 |

`value_type` 和定义行中的 `data_type` 必须来自实现明确支持的封闭枚举。`V` 的实际类型标签必须与对应类型完全一致；例如整数定义不能用 `DECIMAL 10` 或 `TEXT "10"` 代替。引用字段写稳定键而不写数据库 ID。v1 的 `npc_template_class_level` 没有记录，但该零记录分区仍进入字节流；三个 NPC 的全部 18 项技能和六项豁免均应在 `npc_template_proficiency` 中显式保存为 `NONE`，不能依靠缺行表示默认值。

### 11.5 v1 稳定编号和关系

以下编号是关系数据使用的稳定值，数据库内部枚举或 Java 枚举名称不能替代它们：

- 熟练层级为 `proficiency.none/NONE/0/1`、`proficiency.half/HALF/1/2`、`proficiency.full/FULL/1/1`、`proficiency.expertise/EXPERTISE/2/1`；除半熟练使用 `FLOOR` 外，其余使用 `EXACT`。
- 检定定义为 `check.ability/ABILITY/ABILITY_MODIFIER_V1`、`check.skill/SKILL/SKILL_BONUS_V1`、`check.saving_throw/SAVING_THROW/SAVING_THROW_BONUS_V1`、`check.manual/MANUAL/MANUAL_MODIFIER_V1`。
- 掷骰模式为 `roll.normal/NORMAL/1/ONLY_CANDIDATE_V1`、`roll.advantage/ADVANTAGE/2/HIGHEST_FIRST_ON_TIE_V1`、`roll.disadvantage/DISADVANTAGE/2/LOWEST_FIRST_ON_TIE_V1`。
- `event.note` 不关联检定且只关联 `effect.append_event_message`；其余三个事件模板分别只关联同名检定定义，并可关联第 9.2 节全部五种效果。`check.manual` 没有 `module_event_check` 或事件模板效果关系是有意设计，它按第 8.1 节作为直接检定入口，并以冻结发布版的全局效果定义作为授权白名单。该运行解释不增加规范哈希分区或修改既有关系行。
- 五种效果的执行算法编号依次为 `ADJUST_CURRENT_HP_CLAMP_V1`、`GRANT_MODULE_ITEM_V1`、`GRANT_TEMPORARY_ITEM_V1`、`SET_ENTITY_NODE_POSITION_V1`、`APPEND_EVENT_MESSAGE_V1`。参数必须拆成 `effect_parameter` 关系行，不得把第 9.2 节的约束合并成 JSON 或任意脚本文本。
- 唯一地图的 `map_type` 为 `NODE`；连接行只保存按规范字节序排列后的两个端点。

`rule_constant` 至少必须包含下列跨实体常量；数值范围已经属于 `field_definition`、`proficiency_bonus_band` 或 `effect_parameter` 的，不再重复保存：

| `constant_key` | `value_type` | `value` |
|---|---|---|
| `stable_key.pattern` | `TEXT` | `[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*` |
| `character.key.format` | `IDENTIFIER` | `LOWERCASE_UUID_V1` |
| `character.name.normalization` | `IDENTIFIER` | `TRIM_THEN_NFC_V1` |
| `character.name.minimum_code_points` | `INTEGER` | `1` |
| `character.name.maximum_code_points` | `INTEGER` | `80` |
| `character.name.reject_control_characters` | `BOOLEAN` | `true` |
| `character.total_level.minimum` | `INTEGER` | `0` |
| `character.total_level.maximum` | `INTEGER` | `20` |
| `formula.ability_modifier` | `IDENTIFIER` | `FLOOR_DIV_SCORE_MINUS_10_BY_2_V1` |
| `formula.total_level` | `IDENTIFIER` | `SUM_CLASS_LEVELS_V1` |
| `formula.proficiency_contribution` | `IDENTIFIER` | `NONE_HALF_FLOOR_FULL_DOUBLE_V1` |
| `formula.skill_bonus` | `IDENTIFIER` | `ABILITY_MODIFIER_PLUS_PROFICIENCY_V1` |
| `formula.saving_throw_bonus` | `IDENTIFIER` | `ABILITY_MODIFIER_PLUS_PROFICIENCY_V1` |
| `item.removal_mode` | `IDENTIFIER` | `ARCHIVE_V1` |
| `check.manual.modifier.minimum` | `INTEGER` | `-99` |
| `check.manual.modifier.maximum` | `INTEGER` | `99` |
| `roll.d20.minimum` | `INTEGER` | `1` |
| `roll.d20.maximum` | `INTEGER` | `20` |
| `roll.random_source` | `IDENTIFIER` | `SERVER_UNIFORM_V1` |
| `check.dc.minimum` | `INTEGER` | `0` |
| `check.dc.maximum` | `INTEGER` | `60` |
| `check.success_comparison` | `IDENTIFIER` | `TOTAL_GREATER_THAN_OR_EQUAL_DC_V1` |
| `check.natural_1_has_special_result` | `BOOLEAN` | `false` |
| `check.natural_20_has_special_result` | `BOOLEAN` | `false` |
| `effect.transaction_mode` | `IDENTIFIER` | `ATOMIC_ALL_OR_NOTHING_V1` |

### 11.6 生成、校验和发布流程

实现顺序固定为：只增不改迁移建立定义表 → 后续只增不改迁移插入完整 `DRAFT` 定义 → Java 在同一只读一致性事务中加载并校验逻辑投影 → 按本节生成规范字节流 → 使用 `MessageDigest.getInstance("SHA-256")` 计算摘要 → 使用小写十六进制编码为 64 个 ASCII 字符 → 与应用随附的预期清单比较 → 仅在完全一致时由受控升级流程转为 `RELEASED` 并启用不可变保护。

预期哈希必须由可重复自动测试从已审查的完整定义产生后，再写入发布迁移和应用清单，不能手工猜测。测试必须先比较完整规范字节流或独立准备的字节测试向量，再固定摘要；不能只让同一份错误实现同时生成“期望值”和“实际值”。

完整 V003 定义的规范载荷固定为 29,915 字节，SHA-256 为 `8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e`。独立参考工具不调用 Java 编码器或应用清单，可用于只读复核这一稳定结果；清单存在本身不替代发布状态和不可变保护检查。

发布转换前必须再次在同一一致性读取中计算摘要并锁定目标 `DRAFT`。转换只能写 `module_release.content_sha256`、`release_status` 和 `released_at`，不得修改定义行。应用运行账号不能执行该转换。转换完成后，数据库保护必须拒绝对该发布版及其全部定义关系的 `INSERT`、`UPDATE` 和 `DELETE`。

应用启动、战役创建或激活、角色加载或修改、检定、事件和导入诊断使用相同的规范编码器重新计算摘要。创建战役时保存的冻结哈希必须同时等于重新计算值、`module_release.content_sha256` 和应用预期清单；任一不一致都进入 `MODULE_HASH_MISMATCH`，不能降级为警告。

## 12. 验证要求

- 任意 1—30 属性都按 `floor((score - 10) / 2)` 得到正确调整值，包括 9、10、11 和 1。
- 等级 0—20 的熟练加值、半熟练、全熟练和专精都按本规范计算。
- 18 技能和六项豁免始终使用表中绑定属性，客户端不能提交替代属性或最终加值。
- 不支持的规则输入被拒绝，且不会产生部分角色、骰子、物品或事件记录。
- `NORMAL`、`ADVANTAGE`、`DISADVANTAGE` 保存全部候选骰和选中骰；自然 1/20 没有额外结算。
- `MANUAL` 不依赖事件模板仍可执行并产生内部事件，且只能使用冻结发布版的全局效果白名单；`event.note` 只创建一个可幂等重放的根事件，不创建检定、骰子或效果计划记录。
- 两个效果分支和全部参数按类型与顺序完整保存，未选中分支不改变权威状态；定位效果仅在 V010 运行表与校验就绪时执行。
- 任一类型化效果失败时，同一事务中没有骰子、状态、字段变化或内部事件残留。
- 多目标检定在掷骰前核对并稳定顺序锁定全部角色版本，每个实际修改的角色聚合只递增一次版本。
- 数据库约束保证每个战役最多一个活动遭遇；只有已存在的活动参与者可定位到合法节点，定位不能隐式参战，且不存在先攻、回合、行动、距离或自动攻击/伤害字段。
- 模组哈希与数据库 ID、时间戳、插入顺序、CRLF/LF 差异无关；任何业务定义变更都改变哈希。
- 规范编码测试覆盖空分区、可空值、NFC、CRLF/LF、UTF-8 无符号排序、复合键、负整数、等价小数和非法类型标签。
- 使用独立准备的字节测试向量验证容器、字段名称、长度前缀和类型标签，并固定完整发布版的预期 SHA-256。
- `RELEASED` 模组定义不能新增、修改或删除；没有通过哈希验证的发布版时不能创建战役。

## 13. 后续扩展原则

完整规则目标通过新的不可变发布设计和只增不改的迁移实现，不修改本文件对应的 v1 数据语义，
也不覆盖已存在战役冻结的发布版和内容哈希。新规则必须先定义稳定键、输入范围、派生算法、
事务效果、规范哈希序列化和存档兼容策略，再进入页面或业务实现。完整规则族已批准为
`dnd5e2014_srd51_se`、release 1、canonical 2、archive 2，但仍为 `DRAFT`；该映射不得反向
改变本文 v1 的键、23 个规范分区、字节流或摘要，也不证明相应数据库目录已经安装。
