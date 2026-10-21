# 角色语言与工具授予事实

本合同覆盖现有单模组 DRAFT 一级创建及 schema 2 的内部初始授予计划。旧创建 profile
产生语言和工具授予事实，创建命令在同一个事务保存事实、角色、选择、资源、根事件和幂等
结果；角色卡与内部 archive format 2 角色片段读取并验证同一事实。schema 2 从真实运行
快照读取类型化来源与槽，产生绑定准确规则的只读计划，不提交角色业务状态。它不发布 DRAFT、不激活 archive 2，
不改变 RELEASED v1 或新战役默认值。

采用层级为**内部可测**。旧真实创建、JDBC、卡片和内部文件链，以及 schema 2
作者来源/槽、来源转存、完整运行读回与初始计划可以验证。schema 2 的角色业务提交、
共同业务守卫及完整初始化尚未接合；内部计划中的禁用事实不开放游戏执行或工具检定。完整采用仍遵循
[机制责任清单](../design/v2/mechanism-integration-checklist.md)及
[采用边界](../design/v2/adoption-boundaries.md)。

## 原文与范围

[SRD 本地第 59 页](../references/srd-5.1/pages/page-059.md)的 Languages 说明种族的
默认语言、背景的额外语言选择及角色卡记录；标准、异域与秘密语言的适用许可仍由冻结的
创建候选界定，不接受任意 DM 文本扩张候选。
[第 70 页](../references/srd-5.1/pages/page-070.md)的 Tools 说明种族、职业、背景或
专长提供工具熟练，不同工匠工具各自具有熟练身份，并说明工具检定可以使用不同属性。

旧创建保存既有[一级创建 profile](character-creation-v2.md)产生的语言/工具授予；
schema 2 只读消费者另行推导本合同限定的初始计划。
源类型封闭为 `character.race`、`character.subrace`、`character.background`、
`character.class`；专长授予、多职业增量、后续学习和实际工具检定修正不并入本切片。
语言交流的叙事结果也不由本切片自动裁定。profile 的既有固定与有限选择语义保持，
不引入新的通用机制注册表、表达式或 DM 任意输入。

## 完整事实与身份

`module.CharacterProficiencyGrant` 是不可变的单条事实，字段如下：

| 字段 | 含义与边界 |
|---|---|
| `category` | `LANGUAGE` 或 `TOOL`，对应 `character.language` 或 `character.tool` |
| `sourceModuleKey` | 授予来源的完整模组归属，ASCII 模组键，最多 64 字符 |
| `sourceType` / `sourceKey` | 封闭源类型及对应 `race.`、`subrace.`、`background.`、`class.` 键 |
| `grantSlotKey` | 来源内的稳定授予槽；不能用候选目标或显示名称替代 |
| `grantKind` | `FIXED` 或 `CHOICE` |
| `targetModuleKey` | 被授予语言/工具的完整模组归属，不从来源模组隐式推断 |
| `proficiencyKey` | 类别对应的 `language.` 或 `tool.` 稳定键 |
| `suppressed` | 事实保留但不提供有效能力；模型允许已获得的 CHOICE 以后受抑制 |

规则键和槽键为区分大小写的 ASCII 分段键，最多 128 字符。来源、目标模组分别保留；
两模组中相同本地键不是同一规则。`ORDER` 按类别、来源模组/类型/键、槽、授予类型、
目标模组/键及抑制值产生确定性顺序，排序不改变事实身份。

旧 DRAFT profile 没有启用字段；本创建消费者只产生 `suppressed=false`，源/目标模组
都等于所绑定的单模组。这些单模组及固定槽约束仅属于旧 profile。schema 2 的完整来源与
槽身份由下述独立计划事实表示，不改变旧模型、卡片或内部角色 codec。

目标禁用的既定语义见[模组接口基线](../design/v2/module-interface-baseline.md)：禁止新
选择；固定授予保留事实与来源并抑制有效能力。已经取得的选择也不能因后来禁用而丢失
事实。schema 2 新建计划据此抑制固定事实并排除禁用选择；已有选择以后被禁用的业务
状态迁移仍不属于本计划消费者。

## 真实产生与选择分配

`LevelOneCharacterRules.prepare` 使用已经验证的种族、亚种、背景和职业 profile。
所有固定目标和所有选择候选，包括未选候选，都必须存在于冻结目录；缺失引用使用
`MALFORMED_FROZEN_CATALOG` 拒绝。请求仍只提交既有有限键列表，不能指定派生的授予
来源、槽、事实数量或抑制状态。

现有 profile 每个来源的每个类别至多有一组固定项和一组选择，稳定槽为：

| 类别 | 固定槽 | 选择槽 |
|---|---|---|
| 语言 | `creation.language.fixed` | `creation.language.choice` |
| 工具 | `creation.tool.fixed` | `creation.tool.choice` |

旧请求的选择列表按键规范排序，profile 组按种族、亚种、背景、职业的顺序处理。服务器
按此顺序寻找同时满足每组数量和候选限制的确定性分配；前一组的贪心分配不可行时回退，
直到全部组同时满足。每个选择仅分配给一个真实父组，事实保留该组及其稳定槽。相同请求
的输入顺序不改变分配、原有预览摘要或事实顺序。这是旧请求的服务器分配规则，不是新
schema 2 任意多组选择的作者合同。

不同来源固定授予同一语言或工具时，每个来源事实都保留，规范目标集合只包含该目标一次；
这与客户端重复提交同一选择不同。客户端重复输入使用 `DUPLICATE_OR_MALFORMED_SELECTION`
拒绝；选择项与任何固定项重叠使用 `DUPLICATE_SELECTION` 拒绝。固定项不需要提交空的伪选择。
未知键、重复输入、错误数量、不可同时满足的组以及错误亚种父关系沿用稳定创建错误。
`reconstructProficiencyGrants` 从冻结父身份和完整 LANGUAGE/TOOL 创建选择重新计算
相同事实，用于卡片与内部恢复校验，不能信任文件提交的来源描述。

原 `languages`、`tools` 和 `character_creation_selection_v2` 继续作为原创建合同中的
规范目标投影；它们不能替代来源事实。预览和确认摘要域及其原编码保持不变，事实是相同
冻结规则与规范选择的确定性结果。客户端没有新授予输入字段。

## Schema 2 来源、槽与只读初始计划

作者来源为完整 `RuleIdentity(moduleKey, ruleType, ruleKey)`，源类型仍封闭为上述四类。
每个来源保存显示名、描述、来源页和可选作者备注；只有亚种必须具有真实种族类型的
`parent_race`，其余三类禁止该字段。来源、父种族和授予目标均取自最终类型化规则。
授予槽为 `EntryIdentity(source, proficiency_grants, slotOwner, slotKey)`；来源所属模组和
槽所属模组分别保留。槽键采用作者条目的有界简单标识符，不复用旧 profile 的带点槽名。
不同扩展可在同一来源上各自拥有同名槽，完整身份仍互异。

槽的 `grant` 是原子封闭联合：`FIXED(category, target)` 或
`CHOICE(category, candidates, count)`，类别仅为 LANGUAGE/TOOL，目标分别为对应类型的
语言/工具；选择引用对应类别的候选集合，数量为 1—16。覆盖必须替换整个 grant，不能
只改类别、种类或引用而留下另一种授予的残余字段。来源、槽、集合、成员和目标的存在及
类型在最终模型中校验，禁用不能掩盖损坏引用，包括未选候选。

`Schema2InitialCharacterPlanService.prepare` 默认通过
`JdbcSchema2RuntimeSnapshotRepository.read` 读取调用方指定的同一 run/snapshot，
独立读回全部类型字段和字段链，并比较准确 `Schema2RulesBinding`。绑定包括 profile、
算法、成员顺序、版本、原包/逻辑双摘要及组合、最终规则、完整链摘要。请求不提交
projection，也不能用相同最终规则替换不同来源链。该服务只使用调用方已有运行连接，
不变更事务状态，不借来源连接、不提交、回滚或写入业务行。

请求为三个必选来源（种族、背景、职业）、一个可选亚种，以及按完整槽身份提供的有限
目标列表。全部来源必须真实存在并启用，亚种的最终父种族必须与所选种族准确一致。
`Schema2InitialProficiencyRules` 先校验所有所选来源的槽与全部候选，再解释选择。启用
CHOICE 必须按其真实数量提供可用目标；禁止向固定槽、禁用槽、未选来源或错误槽所属
模组提交选择。每个槽内及全部槽之间的 CHOICE 目标不能重复，也不能与任何有效固定
目标重复。请求已准确指定每个槽，不进行贪心重分配；所有槽必须同时满足。

重复固定来源的事实全部保留。禁用固定槽或固定目标产生 `suppressed=true` 的固定事实；
禁用 CHOICE 槽保留 `SlotFact(enabled=false)`，没有新选择义务，也不制造目标事实。
禁用集合、成员或目标不可用于新选择。来源、数量、授予种类、类别和抑制状态均由服务器
推导。独立 `GrantFact` 保存完整槽、类别、种类、完整目标和抑制值，来源只能由
`slot.rule` 派生；不会丢弃槽所属模组，也不把简单槽键拼成旧模型的虚构身份。

不可变 `Plan` 保留本地 run/snapshot、准确 binding、真实来源选择、完整槽选择、全部槽和
全部授予事实；它绑定原运行快照，不能把本地身份声明为可移植存档身份。请求最多 512 个槽、
每槽最多 16 个目标、合计最多 256 个选择；集合深拷贝、
确定性排序并拒绝空引用与重复目标。计划最多 512 个槽和 768 条事实；运行规则、条目、
字段链及字节预算继续采用[运行快照合同](schema-2-runtime-snapshot.md)。

`validate` 从同一准确运行快照及计划中的来源/选择独立重建并全量比较。缺失、多余、
伪造来源或槽、目标、种类、类别、禁用状态及绑定均拒绝；不再次授予或写库。这是内部
计划投影与恢复校验，不是完整角色状态、外部作者文件或已启用的 archive format 2。

## 创建事务与存储

`LevelOneCharacterCreationService.confirm` 把真实规则产生的完整事实放入
`LevelOneCharacterCreationRepository.Command.proficiencyGrants`。命令使用不可变列表。
`JdbcLevelOneCharacterCreationRepository` 在借连接前校验每条事实的父身份、单模组归属、
槽和无抑制状态，并核对事实的目标集合与原创建选择一致。

新表 `character_proficiency_grant_v2` 由前向 V022 建立。表保存 `character_id`、
`module_release_id` 和模型全部字段；`proficiency_type` 由类别确定，用于目标目录外键。
主键包括角色、类别、完整来源模组/类型/键、槽、目标模组和目标键。外键连接创建快照、
同发布的角色根、来源定义及目标定义；插入触发器核对真实创建父身份和模组归属，更新
触发器禁止改写事实。允许由已有完整清理事务删除事实，不能因不可变而阻断工作集清理。

创建顺序为根、快照、原选择、授予事实、等级/资源、事件及审计、幂等结果。授予写入通过
`JdbcCharacterProficiencyGrantRepository.append` 使用父事务连接；该仓储不提交、回滚、
关闭或另借连接。任何授予行写入失败均由父创建事务整体回滚，不能留下角色或平面选择。
现有创建没有 RNG；重放和 stale preview 沿用父命令的校验及事务边界。

V022 不推测回填旧 DRAFT 平面数据，前置检查要求创建快照为空；存在旧 DRAFT 创建数据时
迁移拒绝，须另行审查来源重建方案。迁移不创建角色或授予、不发布规则、不授权账号，
不能把 SQL 文件存在当成已经应用。

## 卡片、内部文件与恢复

`JdbcCharacterProficiencyGrantRepository.read` 从实际查询行重建完整模型，校验类别与
目标类型、布尔值及重复身份；角色卡同时读取创建父身份和原规范选择。`CharacterCardService`
用冻结目录重新求出完整事实并比对后，返回事实及目录显示名称；可读投影不能反过来产生
权威授予。v1 角色没有创建快照时不得夹带此类事实。

内部 `CampaignArchiveV2CharacterState.ProficiencyGrant` 保存稳定角色键与完整模型，
排除数据库 ID。严格 codec 保留每个字段；带准确目录的 `validated` 从创建快照和规范
选择重算全部事实，拒绝来源伪造、缺行、多行、错误槽或目标。恢复仓储在调用方事务中
直接写入已验证事实，不再次授予、选择或执行创建命令。清理必须先删除授予行，再删除
创建快照和角色根。

这是内部角色片段，未接入 released archive dispatcher 或完整战役恢复。完整业务历史、
准确 schema 2 `rules` 绑定、全部领域当前状态、FINAL 保存、技术 END、清理和容量闭包
继续按[保存与恢复](../design/v2/save-and-recovery.md)采用。不可用空其他分区把角色
片段声明为完整文件，也不开放首次新建战役或外部角色作者文件入口。

## 逐项责任与证据

| 责任项 | 当前落点与证据；未采用边界 |
|---|---|
| 1 原文与范围 | 本地 SRD 第 59/70 页；本合同限定初始语言/工具授予，工具检定和后续授予不在此消费范围 |
| 2 身份与支持 | 旧 profile 使用 `CharacterProficiencyGrant`；schema 2 使用完整来源与 `EntryIdentity` 槽及独立 `GrantFact`，不授予完整模组运行资格 |
| 3 作者字段与修改 | schema 2 四类来源、亚种父种族、原子 FIXED/CHOICE grant、完整跨模组引用及启停；旧 profile 稳定槽合同保持 |
| 4 安装与来源 | 旧业务仍读 `ModuleCatalog`；schema 2 原始包→来源账本→转存→完整类型化运行读回→计划，不绕过基础包完整性及安装资格门 |
| 5 快照与执行身份 | schema 2 计划绑定真实 run/snapshot 及准确规则成员和摘要；共同 campaign 初始化和业务提交尚未接合 |
| 6 输入与选择 | 旧请求由服务器有界回退分配；schema 2 按完整槽提供选择，重验全部候选、数量和跨槽重复，禁用候选不可新选 |
| 7 时机与资源 | 随初始创建一次产生，不另消费动作、时间、骰子或职业资源；无跨命令 pending 选择，其他授予时机不在本切片 |
| 8 计划与消费者 | 旧 profile → 创建 Command → JDBC append；schema 2 真实运行快照 → `Schema2InitialCharacterPlanService` → 不可变初始计划，尚不执行角色业务提交 |
| 9 校验与事务 | 事实在借连接前有界校验，同父创建事务提交；JDBC 写入故障测试核对整体回滚，尚不替代新 v2 共同守卫 |
| 10 幂等与提交结果 | 沿用父创建操作和摘要，不新增独立授予请求；完整 v2 提交未知与共同查证仍待采用 |
| 11 状态与历史 | 创建快照是父身份，授予表保存不可变来源事实，原 selection 是规范目标投影；创建根事件及审计同事务，未宣称完整 v2 历史 |
| 12 保存与直接恢复 | 旧内部角色 codec 与导入保留事实；schema 2 计划按准确快照及 binding 独立重建、全量比较，不再次授予；全战役文件和全部历史仍有阻断 |
| 13 END 与清理 | 既有替换清理先删授予再删快照/角色；完整 FINAL/END/救援与引用图尚未采用，不由此开放新运行 |
| 14 容量与增长 | 旧 profile 有界创建一次；schema 2 计划采用槽/选择/事实预算与运行快照预算；尚未证明全局 FINAL 预留和完整战役容量 |
| 15 独立与失败验证 | 真实四来源正例、跨模组同键/同槽键、坏未选候选、错误父关系、禁用与重复、绑定防串及恢复伪造拒绝；JDBC 代理不证明真实 MySQL 触发器或权限 |
| 16 Host 与结果 | 既有创建 Host 的发布门保持，卡片读取真实事实；schema 2 开始入口、授予编辑或工具检定入口未开放 |
| 17 同步与采用 | 本合同与角色创建、内部文件、数据库及采用边界共同维护；定向验证命令和制品审计见测试指南，不记录动态结果计数 |

相关测试入口包括 `CharacterProficiencyGrantTest`、`LevelOneCharacterRulesTest`、
`LevelOneCharacterCreationServiceTest`、`JdbcLevelOneCharacterCreationRepositoryTest`、
`CharacterProficiencyGrantSchemaTest` 及卡片/内部 format 2 的对应测试。实际测试结果、真实
数据库验收、授权、发布和部署各自报告，不从源码或本合同推断通过。
