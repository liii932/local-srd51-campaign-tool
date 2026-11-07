# 架构

> 文档状态：当前实现说明与原型架构边界
> 规则目标：完整覆盖 D&D 5e 2014 / SRD 5.1；当前 Host 默认仍使用简化 v1。
> 当前运行边界：DM 管理面只供本机；SQLite 提供默认关闭的独立 HTTPS 玩家原型，LAN 设备启用仍须验收。

产品流程见[产品说明](product.md)。新 v2 的领域存储、完整快照、战役工作集和保存/结束
目标集中在 [v2 设计导航](design/v2/README.md)；其[采用边界](design/v2/adoption-boundaries.md)
明确与本文现行行为的差异。设计修订与版本支持范围按[产品原型政策](product.md#原型阶段的版本范围)
处理；目标或政策记录不表示代码、数据库和存档链路已经替换。

## 1. 产品边界

Local 5E Campaign Tool 由一台主机运行、一名本机 DM 管理。默认 MySQL 模式只提供
本机 Host；显式 SQLite 有限模式可配置独立玩家原型，分别执行以下边界：

1. 当前应用采用 Java 21、Tomcat 10.1、JSP、Servlet 和 JDBC；默认保留 MySQL 简化模式，
   `DND_STORAGE_ROOT` 显式启用私有 SQLite 当前有限运行模式。逐领域覆盖见
   [接合表](design/v2/domain-integration.md)，Java/Tomcat 与本机 Web 入口保持。
2. DM 只通过本机浏览器访问 `http://127.0.0.1:8080`。
3. Host 实现 `/host/*`、`/api/host/*` 和安全的 `/health`，根路径 `/` 转到 `/host`；可选玩家入口只有 `/player`、固定资源和 `/api/player/*` 白名单。
4. 玩家原型显式配置独立 HTTPS 8443、成员审批与逐请求授权；不自动创建监听。公网、匿名展示、`/display` 和 `/api/public/*` 不在范围内。
5. 数据库和存档不预建未使用的公开字段或 `public_*` 表；任何边界变化必须另立范围并明确结构、权限和真实数据处置。
6. 主机 loopback 边界、Session、CSRF、Origin/Referer、Fetch Metadata、对象版本、运行 epoch 和幂等键仍必须实现。恶意网页可以从主机浏览器尝试攻击 localhost，因此“只有本机使用”不等于可以取消 Web 安全边界。
7. 当前 Host 使用内置不可变发布，不提供脚本扩展；v2 自定义内容仅能使用已支持机制并经
   独立安装/接受，具体边界见模组公共合同。
8. MySQL Host 没有经过哈希验证的内置 `RELEASED` 发布版时不得创建战役；SQLite 有限模式
   只接受其准确内置目录，不使用完整 SRD 的 DRAFT 身份。各模型初始化都须通过该范围完整
   规则、业务与恢复资格，不能以临时空战役绕过。

完整规则支持本身不授予网络或玩家权限。玩家原型使用独立入口和授权，不能放宽
loopback Filter 或把主机接口绑定到局域网地址。

## 2. 当前实现与目标闭环

未启用 SQLite 时，原 Host 简化 v1 的闭环为：

```text
DM 访问 http://127.0.0.1:8080/host
  → 创建战役并自动绑定唯一内置 RELEASED 规则预设
  → 创建或导入 PC/NPC
  → 维护角色卡、职业、熟练、物品、节点地图位置和最小遭遇状态
  → 执行 1d20 检定和类型化事件效果
  → 在单一数据库事务中保存状态、骰子、字段变化和事件
  → 导出本地 campaign-save.json
  → Tomcat/MySQL 重启后恢复当前状态
  → 重新导入存档并使旧页面失效
```

当前基础闭环的验收条件为：

- Tomcat 以稳定根上下文部署，8080 只监听 `127.0.0.1`；
- `/host/*` 和 `/api/host/*` 只能通过精确的 loopback 主机入口访问；
- 根路径、健康检查、页面和接口不依赖 WLAN 地址、HTTPS 证书或外部设备；
- 主机读取不改变状态，所有修改只接受 `POST`；
- 主机写请求具有 CSRF、来源、Session、epoch、对象版本和幂等保护；
- 只有完整校验成功且不可变的内置规则发布版可以绑定战役；
- PC/NPC、角色字段、检定、事件、物品和地图状态都持久化；
- 事务失败不留下部分状态、骰子或事件；
- 存档可以在相同内置发布版哈希的系统中预览、导入和恢复；
- Tomcat 重启后能够恢复活动战役和当前 DM 工作状态；
- 日志、WAR、Git 和存档中没有数据库口令、Cookie、CSRF Token 或证书私钥。

完整 SRD 5.1 目标在相同本机边界中扩展这条链路：角色构筑、冒险资源、战斗、法术、装备、
怪物和状态都必须进入同一套服务器权威验证、事务、事件、幂等、冻结规则与存档机制。
局域网设备访问、HTTPS、证书信任、玩家身份与逐人视图另按[玩家参与](design/v2/player-participation.md)
接合和验收，不从完整规则支持推导网络权限。
新 v2 准备准确有序组合，由应用 JSON 表结构声明计算闭包并生成确定性 SQLite 建表计划。
同一初始化事务建立结构、静态种子、完整规则/链、业务、历史和控制；首份完整检查点接受
后才可执行，最终逻辑 JSON 文件发布并登记封存后才可清理。结构语言及职责见
[结构合同](design/v2/storage-schema-and-planning.md)，现行领域映射和替换入口见
[实施路线](design/v2/storage-data-map-and-rollout.md)。
重启残留经完整核验和查证后可显式继续、救援或精确放弃；见[完整文件](design/v2/save-and-recovery.md)
及[模组接口](design/v2/module-interface-baseline.md)。
当前以独立应用模型接合[有限 SQLite 战役运行](storage-vitality-loop.md)：
`BundledCampaignRules` 经生产 reader/compose 加载四个有限规则包；`CampaignDomainEngine`
在不借连接的前提下执行现有纯领域规则；`CampaignDomainData` 和严格
`CampaignDomainCodec` 保存完整类型化聚合。`CampaignDomainRepository` 写入聚合及关系
投影；`CampaignStorageService` 在一个 SQLite 事务中统一领域后果、根历史、revision 和
请求结果，提交后才向页面发布。独占根锁和一个 `BEGIN IMMEDIATE` 同时接纳并提交，
没有独立 C0 或 NOT_COMMITTED 裁定；未知 commit/rollback 阻断新写，经完整库/历史/收据
核验后显式恢复新 epoch，不重播未持久意图。根与子事件、原骰、初始/当前状态及 pending 共同进入
完整文件，打开和恢复直接核验事实而不重放。显式模式使用自己的服务与 epoch，关闭旧
MySQL 读写路由；未配置时仍使用原模式。它不等同完整 SRD 或通用完整 format 2 发布。
角色命令、角色状态、战斗历史与准备身份由 `Schema2CharacterRuntime`、`Schema2CombatHistory`
和 `Schema2PreparationIdentity` 定义。领域层校验事实形状、历史邻接和容量；JDBC 仓储
适配类型化行，SQLite 仓储保存严格聚合，两个存储入口复用同一事实合同。

下述 INTERNAL MySQL 来源/快照/历史实现说明的是原服务边界。SQLite 路线复用其
纯规则消费者，不调用它们另开 MySQL 事务。schema-2 prepared 身份与历史 version 是
保留的业务关系，本地 core run/epoch/HTTP 收据另建且不入文件。每命令 SQLite 耐久和
INITIAL/MANUAL/FINAL 承担当前保存责任，周期 AUTO 是后续容量测量目标。
检定分支入口 `CampaignCheckEffects` 与独立 DM-managed 自由卡入口 `CampaignManualCommands`
的实际覆盖/验收按[接合表](design/v2/domain-integration.md)核对，不能以自由卡绕过规则角色消费者。

[schema 2 内部快照链路](rules/schema-2-runtime-snapshot.md)已实现来源一致读取、类型化
规则与覆盖链的原子转存及独立回读；它仅提供有限规则材料，不接入 Host 游戏初始化，
也不提供完整业务工作集。它与全部现有业务写入口共用
[业务事务守卫](rules/business-write-transactions.md)；安装资格仍要求完整基础模组与机制支持。

[schema 2 初始授予](rules/schema-2-proficiency-grants.md)在这条材料链上增加类型化角色来源和
授予槽。`Schema2InitialCharacterPlanService` 从调用方只读事务中的实际运行快照生成计划，
保留来源、槽所属模组、准确目标与抑制事实，并按原规则绑定重建核验；它不借用旧 DRAFT
单发布创建事务，不持久化角色或发布执行视图。初始化准备把准确战役/角色身份、来源选择及
计划与 INTERNAL 快照、控制和结果同事务保存并独立读回；完整业务初始化仍需其余领域、
业务历史、容量和保存/结束生命周期闭包。

[角色成长与资源消费者](rules/schema-2-character-statistics.md)在已提交的准确初始计划上保存
独立类型化状态和完整局部历史。`Schema2CharacterRuntimeService` 复用共同事务守卫，在
规则、身份、版本、幂等和容量检查之后才消费随机数；状态、命令结果及前后事实原子提交。
它提供 INTERNAL 的有界成长选择、资源和[标准休息](rules/schema-2-character-rest.md)。
每个内部运行最多一个角色，使休息使用唯一时间历史；治疗、生命骰、资源和收益间隔同事务
提交。持续效果集合必须由封闭命令历史证明为空，扩展真实效果 producer 前须补齐时间闭包。
这不构成完整战役初始化或 Host 执行资格。直接恢复写入已验证事实，不重掷、不补授予或恢复收益。

[检定、装备与健康作者来源](rules/schema-2-foundation-authoring.md)把四类封闭定义及完整字段链
接入纯组合、生产编码与来源 V006/V007 的类型化原包核对。运行 V028/V030 现保存四类规则及完整字段链，
V029/V031 的[角色事实仓储](rules/schema-2-character-fact-storage.md)保存封闭输入、结果、装备/健康及完整前后状态，
支持受保护连接中的追加、读回和直接恢复。叶仓储验证结构与邻接，规则执行和历史语义重算由业务消费者负责。

[检定、装备与健康消费者](rules/schema-2-character-foundations.md)使用同一角色版本、根事件及事务。
模组保存类型化算法/参数，装备实例和健康是业务状态；服务端从准确熟练和穿戴事实派生检定贡献，
保存实际骰模式、骰点和结果。伤害、治疗、临时 HP、死亡豁免及稳定恢复沿唯一健康 owner 执行，
稳定期限接入该运行的唯一时间；没有平行 HP、装备目录即库存、独立检定事务或恢复时重掷。

[内部战斗](rules/schema-2-combat.md)由 `Schema2CombatRuntimeService` 接纳同战役的独立 prepared 角色，
要求冻结规则一致、准确 run/snapshot/准备绑定和同秒起点。`Schema2CombatRules` 消费
[战斗作者定义](rules/schema-2-combat-authoring.md)，以唯一聚合记录回合、动作、位置和活动继续链。
`JdbcSchema2CombatRuntimeRepository` 保存完整战斗历史；真实伤害、死亡豁免、掉落及六秒回合时间
仍写入原角色事件，与战斗根在共同事务中原子提交。保存/恢复保留已完成前缀与唯一未决叶子，
不复制 HP 或时钟、不重放已付成本。此 INTERNAL 接合不开放完整初始化、Host 或完整战役文件。

[有限施法](rules/schema-2-spells.md)复用这一 owner 与唯一动作流。`Schema2SpellRules` 从
[施法作者来源](rules/schema-2-spell-authoring.md)及实际角色事实验证生命领域一级的 Bless／Cure Wounds，
或单职业法师 1—5 级的 Shield／Counterspell；`Schema2WizardSpellbookRules` 独立拥有真实学习与准备，
作者配置不等于角色已知。成本和治疗通过准确根操作／施法帧的角色因果子事件提交，法术位、HP 和
秒时钟仍由原角色唯一拥有。`Schema2ActionFlowRules` 保存命中后伤害前、施放完成前及 Ready 触发后
的真实 checkpoint，反应可嵌套，暂停与恢复不重掷、不重付或隐式 PASS。

`Schema2OngoingEffectRules` 区分 Bless 同强来源的 SHADOWED 与固定 DM 抗魔区域的独立压制原因，
专注持有者准确关联 Bless 或 held Ready spell。`Schema2ExplorationRules` 在战斗与有限探索间交接同一
场景 owner，保留效果、区域与时钟，并在重入战斗时建立真实先攻；关闭场景必须满足效果与环境闭包。
V035/V037 保存类型化规则镜像，V036/V038 保存施法、学习/准备、反应、区域/压制及探索事实；
独立恢复重验完整历史与准确规则，不向最新来源补字段。完整 Host、base 和 archive format 2 保持关闭。

[有限怪物与火焰吐息](rules/schema-2-monster-breath.md)沿相同场景 owner 接入真实怪物部署、
回合动作及充能。来源 V011／镜像 V039 保存完整 stat block、actions 复合参数及来源链，
事实 V040 保存怪物唯一健康／使用状态、完整目标证明、共享原骰、逐目标 checks／packets
和专注义务。服务器从全场景点格重建 T；一次 release/raw 后由准确父帧继续，先完成全体
健康，再处理强制结束、共同 CON cut 和失败结束。原角色健康、装备、holder、效果及时间
继续由原 owner 保存，全部子历史与场景根共事务；不因机制共享而开放其他怪物动作或法术。
局部直接恢复保留原骰、成本和 pending，不代表完整文件、Host、技术 END／清理已接入。

## 3. 产品范围

### 3.1 当前冻结 v1 已实现

以下清单只描述默认 MySQL Host，不包含显式 SQLite 模式及其可选玩家入口。

- 单台主机运行 Tomcat 和 MySQL；
- 单个本机 DM 操作者，不建立玩家账号或角色所有权；
- 同一时间最多一个活动战役，其他战役可归档；
- 系统内置且不可在页面中编辑的规则预设 `dnd5e2014_srd51_se_v1`；
- 战役创建时自动绑定并冻结唯一有效内置发布版及其内容哈希；
- PC 与 NPC 共用 `character_record` 和类型化角色字段；
- 空白 PC/NPC、从内置模板创建 NPC、复制、归档和恢复角色；
- 六项属性、当前/最大 HP、AC、单一地面速度、职业等级、18 项技能、六项豁免和熟练倍数；
- 属性调整值、总等级、2014 版熟练加值以及技能/豁免加值计算；
- 简单物品的名称、说明、数量和持有者；
- 普通/优势/劣势的 `1d20±K` 检定及 DC 成功/失败；
- 保存全部候选 d20、选中骰、修正、合计、DC 和结果；
- 类型化事件效果：数值字段调整、模组物品授予、受限临时物品、实体定位和 DM 事件消息；
- 唯一内置节点地图、队伍世界位置，以及只记录成员、敌我阵营和节点位置的最小遭遇状态；
- 权威状态、内部事件和字段变化记录；
- UTF-8、最大 16 MiB、非 ZIP 的单战役 JSON 存档；
- 主机本地的导出、严格预览、整战役新建/替换导入和失败回滚；
- JUnit 单元测试和 MySQL 集成测试；
- 可重复构建、根上下文部署、启动、停止、日志和本机验收文档。

### 3.2 完整 SRD 5.1 目标

目标规则范围和自动化边界由[完整 SRD 5.1 覆盖目标](rules/srd-5.1-complete.md)定义；规则
原文从[本地主题索引](references/srd-5.1/README.md)定位相关分页与 PDF。目标至少包括：

- 角色构筑与成长：种族、背景、职业/子职、职业能力、生命骰、升级、多职业、专长、熟练项和语言；
- 冒险与资源：旅行、休息、恢复、环境、视野、光照、移动方式、负重和消耗资源；
- 战斗：先攻、轮次、回合、动作经济、移动、距离、范围、掩护、攻击、伤害、治疗、0 HP 和死亡豁免；
- 法术：目录、职业列表、已知/准备、法术位、成分、目标、区域、专注、仪式、升环及效果；
- 装备与经济：武器、护甲、工具、货币、价格、重量、装备状态、消耗品、魔法物品、充能和同调；
- 怪物与遭遇：完整属性块、速度、感官、能力、动作、反应、抗性/免疫、CR/XP 和遭遇支持；
- 状态与持续效果：全部 SRD 状态、持续时间、重复豁免、免疫、叠加、优先级和资源恢复；
- DM 裁定：不可机械决定的叙事结果使用封闭、类型化、可审计的裁定入口，不引入任意脚本。

这是目标范围，不是当前完成功能声明。每个领域只有在数据、规则、事务、存档、测试和 Host UI
共同完成后，才可在 README 中列为已实现。

### 3.3 可选局域网玩家原型

SQLite runtime 已接合成员审批、PC 控制、共享、探索许可、逐人视图和有限动作候选。
`CampaignParticipationCommands`、`CampaignAdjudicationCommands` 与 `PlayerGatewayService`
经同一个 `CampaignStorageService` 串行门处理授权和业务提交。玩家只能提交当前服务器
发出的候选，不能上传任意领域命令；自由卡仍由 DM 操作，当前玩家候选只覆盖有限规则卡动作。
DM 对玩家文字提议可拒绝、叙事回复或执行有理由的属性检定，尚无通用裁定执行器。

成员、控制、共享和已执行裁定属于完整业务事实；设备绑定与已发送请求关联只保存在
当前私有 run。同一残留 run 恢复后，DM 重新开放，原有效设备可重新认证并只读查证结果；
完整文件重开建立新 run，需要重新批准设备。两者均不恢复旧写连接或自动重发游戏命令。
入口边界见 §11，安全与恢复分别见[安全模型](security.md)和[运行合同](storage-vitality-loop.md)。
完整设计与剩余采用范围保留在[玩家参与](design/v2/player-participation.md)。

### 3.4 其他明确不做

- 全局玩家账号、多位独立 DM、匿名旁观、聊天和投票；
- 玩家管理存档、导入/导出完整战役、任意角色状态修改或绕过批准取得控制权；
- 页面中的模组上传、安装、规则正文编辑、内容更新和在线下载；
- 运行时 Java、JavaScript、SQL 或其他脚本插件；
- 运行时动态 DDL 和 MySQL JSON 列；
- WebSocket、SSE、推送、点对点通信和自动匹配；
- 图片地图编辑、战争迷雾、自动寻路和复杂视觉效果；
- 分支时间线、历史回放、快照回滚和撤销导入；
- 战役 ZIP 存档、自动/增量备份和角色级选择性合并；
- 云端多租户、公网开放和跨主机协作。

已安装模组的组合选择、预设与独立规则 ZIP 制品属于上述 v2 模组目标，尚未接入现行 Host；
它们不改变战役文件与规则制品分别保管的边界。

### 3.5 当前发布与原型演进边界

当前受支持的业务发布版只有 `dnd5e2014_srd51_se_v1`。应用使用显式只读注册表描述内置
身份，不查询数据库最大版本，也不从页面输入推断默认值。注册表当前包含：

| 用途 | module key | release | canonical | archive | 状态 |
|---|---|---:|---:|---:|---|
| 冻结简化版及当前新战役默认 | `dnd5e2014_srd51_se_v1` | 1 | 1 | 1 | `RELEASED` |
| 完整规则族的下一发布版 | `dnd5e2014_srd51_se` | 1 | 2 | 2 | `DRAFT` |

V011 在仓库中提供完整角色 DRAFT 目录及 canonical-v2 只读投影；迁移文件不证明目标数据库
已经执行。`DRAFT` 仍不允许创建战役、执行规则或导入存档，页面也不显示模组选择或管理入口。
V012 在同一 DRAFT 身份下增加一级创建 profile 和空运行时表。一级创建采用 preview/confirm：
Servlet 只解析有界选择，规则层从冻结 profile 派生最终属性和 HP，Repository 在一个
SERIALIZABLE 事务内共同写入角色根、快照、选择、资源、事件、字段变化和幂等结果。DRAFT
发布门仍在服务入口 fail closed，稳定合同见 `docs/rules/character-creation-v2.md`。
V013 增加冻结的逐级熟练/资源 profile、生命骰资源、canonical-v2 职业等级和不可变升级快照。
`/api/host/characters/level-up` 的预览不消耗随机性；确认 Repository 在 SERIALIZABLE 事务中锁定
幂等键、角色、战役、目录绑定、等级和完整资源目标集，复核事件尾与 row version 后才调用服务端
骰子，并共同提交状态、事件、字段变化、摘要和幂等结果。完整身份继续保持 DRAFT；稳定合同见
`docs/rules/level-advancement-v2.md`。
V014 为 236 个职业/子职业特性固定自动、有限 DM 裁决或阻断三态，为 12 个子职业固定选择
等级，并为 16 个职业资源固定执行与恢复 profile。类型化资源恢复、裁决键和逐职业矩阵见
`docs/rules/class-features-v2.md`。V015 增加多职业先决条件和熟练 profile、逐职业 ASI 等级、
可扩展专长状态（当前仅 Grappler）、不同生命骰组合及事件关联选择；V018 固定职业施法贡献并
提供共享法术位上限计算基础，但多职业施法权威状态/升级事务集成与 Grappler 战斗效果保持阻断，见
`docs/rules/multiclass-asi-feats-v2.md` 与 `docs/rules/multiclass-spell-slots-v2.md`。V017 和内部 DRAFT codec 使用
`eventSequence`/`eventTail` 为当前角色状态建立数据库 ID 无关的事件闭包，提供严格读取、规范写出、
预览和调用方事务内恢复基础；它不接入 Host 路由或 released dispatcher。完整身份、archive
format 2 和默认发布门继续保持关闭。

完整规则族当前不会在单个领域或少量 Issue 完成后转为 `RELEASED`。目录、运行状态、API、
canonical/archive 投影和测试可按新需求与证据协调修订；结构采用方式和真实数据边界见第 8 节。
现行跨领域发布门要求角色、装备/冒险、战斗/状态、法术和怪物/魔法物品完成整合后，才统一
验证摘要、激活 archive format 2、切换新战役默认值并启用对应不可变保护。
这些当前门槛与 `RELEASED` 标记不产生跨应用版本的旧发布版或旧存档支持义务。

内部 `module_release`、`module_*` 定义表、
`campaign_module`、稳定键、内容哈希和只读仓储仍是新增系统发布版的边界。

当前运行账号对 `RELEASED` 模组定义只有 `SELECT` 权限，内置数据由独立维护身份安装。
规则更新不得改变正在运行的战役解释；采用替代模型时同步调整支持身份、读写实现、测试和
文档，并按明确目标处理已有数据，不能用原型政策代替数据库或部署授权。

现有 v1 检定只支持 `1d20±K`、普通/优势/劣势和 DC 比较。该限制描述当前冻结发布版，
不再是最终产品目标。后续应用版本可替换或移除它；当前受支持运行内的规则身份、规范编码、
摘要与恢复必须一致，不能通过静默补字段、重算摘要或改绑默认值改变已保存事实的解释。

## 4. 技术结构与依赖

### 4.1 本机结构

```text
本机 DM 浏览器
  │ http://127.0.0.1:8080
  │ Session、CSRF、host_state_epoch、对象版本、request_id
  ▼
Tomcat 10.1 根上下文
  ├── /              → /host
  ├── /host/*        主机 JSP 页面
  ├── /api/host/*    主机命令与严格 DTO
  ├── /health        固定安全健康状态
  ├── Filter          loopback、方法、来源和响应头边界
  ├── Service         校验、事务、幂等和领域操作
  └── DAO             参数化 SQL 和确定性资源关闭
          ├── 未配置 DND_STORAGE_ROOT → MySQL 8.0 / 原 JNDI 简化模式
          └── 已配置 DND_STORAGE_ROOT → 私有 SQLite / 当前有限战役运行
```

Servlet 不直接执行 SQL，Repository 不决定业务权限，JSP 不保存权威状态。现有职责包为
`web`、`service`、`persistence`、`module` 和 `security`，不创建无内容占位类。
原 MySQL 路线的 `module.ModuleCatalog` 是其不可变规则投影；新 SQLite 有限规则使用
`ProductionSchema2Projection`，含准确成员、最终规则与完整字段链。两者不混用发布身份，
均不从浏览器接受算法、目录数值或派生结果。

`web.DatabaseDiagnosticsFactory` 负责原运行 JNDI 的延迟查找和诊断依赖装配，
`web.DatabaseStartupListener` 与 Host 诊断 Servlet 使用同一能力；
`service.DatabaseDiagnostics` 先校验打包迁移，再协调 schema 与规则完整性检查，
`persistence.DatabaseSchemaVerifier` 只负责参数化查询、账本比较和资源关闭。
失败时不继续下游规则读取，不执行迁移、安装或业务写入。
当前诊断仍在启动及每次 Host 诊断请求时重新检查原单库，不要求第二 JNDI。
这项分层不改变现有命令事务所有权，也不表示 v2 准备/执行上下文已接入。

### 4.2 根上下文和路由

应用以 `ROOT.war` 部署，正式 URL 不包含 Maven 版本号。根路径 `/` 使用相对重定向到 `/host`。产品不按来源分流到 `/display`，也不映射 `/api/public/*`。

所有 Host 业务页面和 API 都使用相同的正式 origin：

```text
http://127.0.0.1:8080
```

`localhost`、`::1`、局域网 IP、8443 和其他端口都不是 Host 入口别名。可选玩家的独立
HTTPS origin 与路径白名单见 §11，不经过 Host 页面或 Session。

### 4.3 直接依赖

| 组件 | 用途 | 约束 |
|---|---|---|
| Java 21 | 应用语言和标准库 | 当前不替换 |
| Tomcat 10.1 | Servlet/JSP、Session、JNDI 连接池 | Servlet/JSP API 不打入 WAR |
| MySQL 8.0 | 原简化模式及来源/运行内部服务 | 保留现有迁移/账号合同 |
| SQLite JDBC 3.50.3.0 | 当前有限战役工作库和共同事务 | 应用 JSON 结构、严格类型化聚合及独立关系投影核验 |
| MySQL Connector/J | JDBC 驱动 | 只通过 JDBC/DataSource 使用 |
| Gson | 主机 DTO 和存档 JSON | 不让领域对象依赖 Gson |
| Maven | 构建和依赖锁定 | 默认定向验证；发布/部署检查点按测试合同 |
| JUnit Jupiter | 单元与集成测试 | 仅测试依赖 |

初版继续使用 Tomcat JULI 和 `java.util.logging`。暂不引入 Spring、Flyway、HikariCP、Redis、WebSocket、Testcontainers、Playwright 或表达式引擎。

## 5. 本机 Web 安全边界

### 5.1 Loopback 入口

Host HTTP Connector 只绑定 `127.0.0.1:8080`。全局首个 `PlayerBoundaryFilter` 先隔离
Connector 和玩家路径；随后 `HostBoundaryFilter` 对 `/host/*` 和 `/api/host/*` 同时要求：

```text
实际 TCP 对端地址 = 127.0.0.1
实际本地端口 = 8080
request scheme = http
Host = 127.0.0.1:8080
```

任一条件不满足时返回相同的普通 `404`，不暴露路由、方法或管理能力。只有通过入口检查后，方法错误才返回 `405`。来源判断不读取或信任 `X-Forwarded-For`、`X-Forwarded-Host`、`X-Forwarded-Proto`。初版不配置反向代理、AJP、`RemoteIpValve` 或 `RemoteIpFilter`。

默认本机部署只启用 `127.0.0.1:8080`。显式 SQLite 模式可按 §11 配置独立玩家 HTTPS
8443；应用不会自行创建 Connector。玩家入口启用不改变这里的 Host 地址、端口和 Filter
条件，实际监听、证书信任及防火墙按[部署指南](deployment.md)单独核验。

### 5.2 方法、Session 和 CSRF

- 主机读取只接受 `GET`/`HEAD`，且不得改变状态；
- 所有修改只接受 `POST`；
- `PUT`、`PATCH`、`DELETE` 和 `TRACE` 在通过入口检查后返回 `405`；
- Session Cookie 固定名 `DNDHOSTSESSION`；
- Cookie 设置 `Path=/`、`HttpOnly`、`SameSite=Strict`，不设置 Domain、持久化 Max-Age 或 `Secure`；
- Session 空闲超时 30 分钟；
- 每个 Session 使用安全随机数生成至少 256 bit 的 CSRF Token；
- Token 只放在 HTML 隐藏字段或 `X-CSRF-Token`，不进入 URL、Cookie 或日志。

主机写请求按以下顺序校验：

```text
TCP 对端、端口、scheme、Host
  → POST
  → 精确 Origin；缺少时验证完整 Referer origin
  → Fetch Metadata（存在时拒绝 cross-site）
  → Session 和 CSRF Token
  → host_state_epoch
  → 对象 row_version
  → request_id 和请求摘要
  → 业务事务
```

允许 origin 固定为 `http://127.0.0.1:8080`。`Origin: null`、来源不匹配、Origin/Referer 都缺少或 `Sec-Fetch-Site: cross-site` 时拒绝写入。

MySQL 模式的主机 GET/HEAD 通过最小只读查询把当前活动战役的 `host_state_epoch` 刷新到
Session 和页面；没有活动战役时使用 `0`。SQLite 模式从存储服务读取自身运行 epoch，
不查询 MySQL。读取暂时失败时仍允许主机壳显示，但 POST 必须再次读取权威 epoch；
后端不可用返回 `503`，请求值只匹配旧 Session 而不匹配当前权威值时返回 `409`。
因此 Session 内缓存不是 epoch 的权威来源。MySQL 数据库诊断仍只适用于该模式。

### 5.3 响应与秘密

主机页面和 API 至少设置：

```text
Cache-Control: no-store
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
```

页面使用安全文本输出，不拼接未转义 HTML。SQL 全部参数化。错误响应不包含异常堆栈、本机路径、SQL、内部对象或秘密。

数据库口令位于 WAR 外的 Tomcat JNDI 配置。证书私钥虽然不再是初版运行依赖，仍不得进入 Git、WAR、页面、存档或日志。

## 6. 数据设计

### 6.1 原则

本节旧表清单及字段合同用于 MySQL 简化模式；SQLite 当前领域所有者、严格聚合文档与
关系投影见[运行合同](storage-vitality-loop.md)和[领域接合表](design/v2/domain-integration.md)。

- MySQL 不使用 JSON 列；
- 不根据模组字段动态执行 DDL；
- 当前权威状态保存在规范化业务表；
- 第一次正式角色创建或修改起就写入内部事件和字段变化；
- 主机写入使用对象版本、`host_state_epoch` 和幂等键；
- 日常对象不物理删除，只归档或恢复；
- 存档导入是整战役替换的受控例外；
- 产品不建立公开配置、公开投影、公开事件或公开身份字段。

### 6.2 核心表

| 表 | 主要内容 |
|---|---|
| `schema_meta` | 模式版本、应用时间、说明和脚本校验信息 |
| `campaign` | 内部 ID、不可变 `campaign_key`、名称、状态、`host_state_epoch`、内部事件末尾序号 |
| `module_release` | 模组键、版本、规范格式版本、哈希算法、SHA-256 和 `DRAFT`/`RELEASED` 状态 |
| `campaign_module` | 战役唯一冻结的模组发布版和内容哈希 |
| `module_field_definition` | 字段稳定键、名称、类型、范围、默认值和顺序 |
| `module_entity_template` / `module_entity_template_value` | NPC/敌人模板及类型化默认值 |
| `module_item_template` | 模组物品稳定键、名称和说明 |
| `module_event_template` / `module_event_check` / `module_event_effect` | 事件、检定和类型化效果定义 |
| `module_map_definition` / `module_map_node` / `module_map_connection` | 内置节点地图定义及显示用无向连接 |
| `character_record` | 战役、不可变 `character_key`、PC/NPC 类型、名称、状态、模组哈希和聚合 `row_version` |
| `character_field_value` | 文本、整数、精确小数或布尔角色字段值 |
| `character_class_level` 等关系表 | 职业等级、技能和豁免熟练倍数 |
| `item_instance` | 持有者、模组/临时物品、名称、说明、数量和状态 |
| `map_instance` / `party_world_position` | 战役中的节点地图实例和队伍世界节点 |
| `battle_state` / `battle_participant` / `entity_position` | 最小遭遇状态、封闭阵营和活动参战者的合法节点；不保存先攻、回合或行动 |
| `host_operation` | 唯一 `request_id`、请求摘要、操作类型、结果状态、根内部事件引用和时间 |
| `check_execution` | 执行者、模式、修正、合计、DC 和结果 |
| `dice_roll` | 候选 d20 顺序、点数和选中状态 |
| `check_effect` | 成功/失败分支、分支内顺序和批准效果键 |
| `check_effect_parameter_value` | 每个计划效果的有序类型化参数快照；不使用 JSON |
| `game_event` | 战役内部连续序号、事件类型、文字和关联对象 |
| `field_change` | 字段旧值、新值和关联事件 |

MySQL 模式不建立 `member`、`auth_token`、`action_request`、邀请码、设备表或任何 `public_*` 表。

### 6.3 战役身份

| 值 | 职责 | 可变性 | 是否写入存档 |
|---|---|---|---|
| `id BIGINT` | 数据库内部关联 | 不可变 | 否 |
| `campaign_key CHAR(36)` | 全局稳定业务身份和存档根身份 | 不可变 | 是 |

普通战役创建请求不能提交或覆盖 `campaign_key`。Service 在请求和冻结发布版完整校验后使用 `UUID.randomUUID()` 生成小写标准 UUID v4，JDBC 写入边界再次拒绝非规范、非 v4 或非 IETF variant 的键；数据库 `ascii_bin` 列、UUID 形状 CHECK 和全局唯一索引是格式及碰撞的最终防线。

运行账号只保留当前事件序号所需的 `campaign.internal_event_tail` 列级 UPDATE，不具有 `campaign_key` 的 UPDATE 权限；以后新增战役可变字段写入时逐列授权，不能恢复整表 UPDATE。数据库 `id` 只用于库内外键和事务锁定，不进入 HTTP 业务身份或存档；导入按存档中的 `campaign_key` 新建或替换战役，并重建本机数据库 ID，不修改或复用另一战役的键。

### 6.4 角色身份与并发

MySQL 模式的 PC 和 NPC 都来自 `character_record`，均由本机 DM 控制，不存在玩家所有权或批准状态。

| 值 | 职责 | 可变性 | 是否写入存档 |
|---|---|---|---|
| `id BIGINT` | 数据库内部关联 | 不可变 | 否 |
| `character_key CHAR(36)` | 稳定业务身份和存档关系 | 不可变 | 是 |
| `character_name` | DM 显示名称 | 可编辑 | 是 |
| `row_version` | 角色聚合乐观锁 | 每次聚合修改递增 | 否 |

`character_key` 由服务端使用 `UUID.randomUUID()` 生成并保存为小写标准 UUID，建立全局唯一约束。普通创建请求不能指定或覆盖它。复制角色生成新键；改名、PC/NPC 类型变化、归档和恢复不改变原键；键永不复用。

名称去除首尾空白、规范化为 Unicode NFC，长度限制为 1 至 80 个 Unicode 码点并拒绝控制字符。允许重名，名称不参与身份匹配。

修改基本字段、类型化字段、职业等级、熟练数据或角色持有物品时，都校验并递增 `character_record.row_version`。浏览器使用 `character_key + expected_row_version` 修改，版本不一致返回 `409 Conflict`。

一个检定可以读取执行者并修改零个或多个目标角色。请求必须携带执行者的 `expected_row_version`，以及每个可能被效果修改的目标角色键和预期版本；这些值都进入规范请求摘要。Service 先锁定战役，再把执行者和全部目标解析为数据库 ID、去重并按 ID 升序锁定，随后一次性核对活动状态、战役归属、冻结模组和版本。任一版本不一致在掷骰和写事件前返回 `409 Conflict`。成功事务只为实际发生权威变化的角色各递增一次 `row_version`；只被读取的执行者不递增，同一角色被同一分支中的多个效果修改也只递增一次。

一条类型化字段值只能使用与定义匹配的一个文本、整数、精确小数或布尔列。

### 6.4 内置发布版与内容哈希

已发布的 `dnd5e2014_srd51_se_v1` 使用 `canonical_format_version=1`。规范编码按战役冻结身份
解析注册表，再精确分派到对应编码器；未知、未发布或应用不支持的格式不会回退到 v1：

- 身份和稳定键使用精确 ASCII，不做 trim、NFC 或大小写转换；
- `TEXT` 按冻结合同将换行统一为 LF，再执行 Unicode NFC；
- 精确数值使用规范十进制表示；
- 实体和字段使用明确固定顺序；
- 无业务顺序的稳定键按无符号 UTF-8 字节排序；
- 可变长度值使用明确长度前缀；
- 数据库 ID、时间戳和插入顺序不进入规范字节流。

Java 使用 `MessageDigest` 计算 64 字符小写 SHA-256，并与应用随附的预期哈希比较。只有完整校验成功的 `DRAFT` 能转为不可变 `RELEASED`。数据库权限和保护逻辑阻止已发布定义新增、修改或删除。

身份词法、描述符和批准摘要以 [规则发布身份合同](rules/module-release-identity.md) 为准。
注册表是独立批准来源：其 DRAFT 描述符摘要严格为 null，完整源投影的 DRAFT 观测摘要不受
此批准值约束。正整数格式或合法摘要不证明已支持或已批准；manifest 返回 expected 值也不
证明源状态、源摘要或完整 canonical 已通过验证。

创建战役时从注册表的显式默认身份选择发布版，并且仅写入一行 `campaign_module`，冻结发布版
和哈希；不使用数据库最大版本。既有战役始终从 `campaign_module` 和角色保存身份解析目录、
规范编码器与存档格式，不因应用升级改绑默认值。应用启动、战役激活、角色加载/修改、检定、
事件、导入和诊断时重新比较实际、发布、冻结和保存哈希。未知、未发布或不支持的身份返回
稳定的不可用错误；已知身份的目录、格式或摘要漂移进入 `MODULE_HASH_MISMATCH`。

### 6.5 当前冻结的 D&D 5e 2014 简化 v1

[`docs/rules/srd-5.1.md`](rules/srd-5.1.md) 是当前内置发布版的规范性规则范围、稳定键、
数值边界、派生公式和哈希输入清单；本节只保留其总体摘要。完整规则目标由
[完整 SRD 5.1 覆盖目标](rules/srd-5.1-complete.md)定义，不能反向改变本节对应的数据语义。

- 六项属性：力量、敏捷、体质、智力、感知、魅力；
- 当前/最大 HP、AC 和单一地面速度；
- 2014 版 18 项技能及对应属性；
- 六项属性豁免；
- 单职业或多职业名称与等级；
- `0`、`0.5`、`1`、`2` 四种技能/豁免熟练倍数；
- 2014 版 1—20 级熟练加值表；
- 属性调整值、总等级、技能和豁免加值运行时计算，不保存可编辑重复副本；
- 简单物品只有名称、说明、数量和持有者。

新增模组字段只增加定义和值数据行，不执行运行时 DDL。

### 6.6 检定、事件、物品、节点地图和最小遭遇

服务端生成骰子结果。客户端只能提交执行者、冻结模组批准的检定/掷骰/效果稳定键、修正来源、DC 和对应的类型化目标参数；不能提交或覆盖 d20 点数、选中骰、属性/技能/豁免最终加值、算法名、JSON 参数或脚本文本。服务端必须从当前角色和冻结模组重新计算派生修正并选择执行算法。

`ABILITY`、`SKILL` 和 `SAVING_THROW` 使用各自的内置事件模板，并通过 `module_event_check` 与 `module_event_effect` 校验该模板允许的检定和效果。`MANUAL` 是不依赖事件模板的直接检定入口，由 DM 提供 NFC 名称和 -99—+99 固定整数修正；它仍创建关联内部事件。由于已经发布的 v1 有意不为 `check.manual` 建立事件模板关系，MANUAL 的效果授权直接使用同一冻结发布版的 `module_effect_definition` 全局白名单和 `module_effect_parameter` 参数定义，不能接受未发布、未知或当前模式版本尚未启用的效果。V009 阶段即使模组中已存在 `effect.set_entity_position`，Service 也必须以稳定的“不支持”业务错误拒绝它；V010 完成后才启用。

`event.note` 是一个不关联检定的特殊事件模板，而不是非模板路径。它必须精确匹配 `event.note → effect.append_event_message`，只接受一条 1—500 码点的 NFC 消息，直接创建一个根 `game_event`，不创建 `check_execution`、`dice_roll`、`check_effect` 或参数值行。

每次检定保存：

- 普通/优势/劣势模式；
- 全部候选 d20；
- 被选中的骰子；
- 修正值、合计、DC 和成功/失败；
- 执行者、时间和关联内部事件。

事件效果使用封闭的类型化关系数据。`check_effect` 为每条计划保存 `SUCCESS/FAILURE` 分支、从 1 开始的分支内顺序、冻结发布版和效果键；`check_effect_parameter_value` 按参数顺序保存参数键、声明类型及互斥的文本、整数、精确小数、布尔或稳定引用值列，并以 CHECK 保证恰好一个匹配类型的值列非空。每个分支的效果顺序唯一且连续，每个效果的参数集合和顺序必须与 `module_effect_parameter` 完全一致。实际分支由 `check_execution.result` 唯一决定，不另存可被篡改的“已执行”布尔副本；未选中分支只保留请求计划，不改变权威状态、不产生 `field_change`。

每个检定、消息和遭遇写命令恰好创建一个根 `game_event`，并在同一事务成功前把它写入 `host_operation.game_event_id`；该引用全局唯一，只允许在操作为 `SUCCEEDED` 时非空，并且只对这些命令类型要求成功时非空。既有战役创建、角色创建和角色卡操作可以继续为 NULL，不要求回填。检定的 `check_execution` 与根事件一对一，`event.note` 则只使用根事件。幂等重放先按 `request_id` 和摘要找到已成功操作，再通过根事件恢复原检定或消息结果，不重新掷骰或执行效果。相同 ID、不同请求摘要必须拒绝。

数值字段调整、模组物品授予和受限临时物品复用既有字段与 `item_instance` 写入规则，不建立第二套物品模型。`effect.append_event_message` 每个分支最多出现一次；实际分支的消息写入该检定的根事件文本，不另外分配事件序号。任一分支计划、参数、目标或预期版本非法，或者任一写入失败时，权威状态、骰子、效果计划、字段变化、根事件和幂等结果在同一事务中整体回滚。

初版只实例化冻结模组中的 `map.tavern_cellar` 节点地图，同一战役和地图键最多一个 `map_instance`。`battle_state` 通过 `ACTIVE/CLOSED` 状态和只在 ACTIVE 时生成战役 ID 的可空生成列建立唯一索引，由数据库保证每个战役最多一个活动遭遇；关闭后的历史遭遇可以并存。阵营只允许 `ALLY/ENEMY/NEUTRAL`，`battle_participant` 以遭遇和角色为唯一键。

`entity_position` 不是通用角色世界坐标，只属于活动遭遇参与者：它必须以组合外键引用同一战役的 `battle_participant`、该遭遇绑定的 `map_instance` 及其合法节点。`effect.set_entity_position` 和 DM 直接定位都只允许移动当前活动遭遇中已存在的活动参与者；未参战角色必须先加入遭遇，不能通过定位效果隐式加入。`party_world_position` 独立记录队伍所在节点，不由该效果修改。DM 可以把参与者直接放到任意合法节点；节点连接仅用于显示拓扑，不限制移动路径，也不产生距离、速度消耗、遭遇触发、先攻、回合或行动规则。

`effect.adjust_current_hp` 只执行 DM 指定的有符号 HP 调整并钳制到有效范围，不计算攻击、命中、伤害骰、暴击、抗性、死亡或状态，因此不构成攻击/伤害自动结算。这里的“遭遇”也只表示成员、阵营和位置快照，不是战斗规则引擎。

### 6.7 一致性与幂等

- 一个主机命令只有一个事务 owner；现有业务和内部材料入口统一通过
  `BusinessWriteTransactions` 先取得进程门，再借连接并锁永久数据库守卫；
- `host_operation.request_id` 全局唯一；
- 幂等记录保存操作类型和规范请求摘要，ID 相同而摘要不同必须拒绝；
- V009 为成功的检定、消息和遭遇操作保存唯一根 `game_event_id`；根事件是检定和无骰消息重放的共同结果入口；
- 业务提交成功后才记录成功结果；
- 失败事务不推进内部事件序号；
- `host_state_epoch` 在导入等整体替换操作后更新；
- 连接、语句和结果集使用确定性关闭；
- 仅在明确提交或回滚后恢复连接状态；提交未知或回滚失败时隔离连接并阻断全部写入口，
  不能恢复自动提交。明确提交后的连接清理失败不改变已提交结果。

## 7. 页面与接口

### 7.1 主机页面

SQLite 模式的 `/host` 进入 `/host/storage`，同一受保护操作台提供规则选择、角色卡、
已接合领域动作、保存/结束、文件列表/下载/导入与残留处置。具体表单及限制见
[运行合同](storage-vitality-loop.md)；以下旧页面只在未启用该模式时使用。

- `/host`：当前战役总览；
- `/host/rules`：按当前活动战役冻结发布版查询和搜索已发布规则目录；
- `/host/campaign`：创建、暂停、恢复和归档；
- `/host/characters`：PC/NPC 和角色卡；
- `/host/events`：检定、效果和内部事件；
- `/host/maps`：地图和实体位置；
- `/host/archive`：存档导出、导入和数据库诊断。

产品不建立 `/host/publication`。

`/host` 操作台采用只读战役总览、可折叠操作区和独立简化角色卡。总览展示现有节点与连接、
队伍位置、遭遇参与者、角色与物品摘要及检定消息；不提供未实现的战斗控制。
检定、地图、角色和战役操作沿用现有 API、版本与幂等校验。人物卡编号建议只辅助加载，
不改变检定执行者或其版本；角色构筑与升级入口明确保留 DRAFT 未发布状态。
布局使用同源样式表和原生折叠区，小屏幕按单列自然滚动。

完整规则将在相同 `/host/*` 边界内增加角色构筑、法术书、装备、战斗追踪、遭遇构建、
怪物目录和规则查询页面。页面名称和路由只有在对应领域设计与测试完成时才成为稳定接口；
完整规则支持不开放 `/display`；当前可选玩家页面使用 §11 的独立边界。

### 7.2 主机 API

SQLite 入口为 `GET /api/host/storage/state`、`download` 及同前缀受保护的
`POST create/open/recover/characters/heal/domain/save/end/import/delete/discard`。
`domain` 只接封闭领域命令，不能指定 SQL、物理路径或任意规则对象；准确请求摘要、
epoch、revision 与角色/场景版本仍分别校验。文件下载是读取，上传导入不自动激活。
下面的 rules/diagnostics 接口说明用于原 MySQL 模式。

`/api/host/*` 使用专用请求/响应 DTO，不直接序列化数据库实体。GET/HEAD 只能读取，POST 才能修改。导入、导出、模式诊断和数据库诊断都属于主机能力。

只读 `GET /api/host/rules` 使用可选的 `q` 关键词和封闭 `type` 分类筛选。Service 先从当前
活动战役解析冻结发布身份，再通过内置发布版注册表和完整目录哈希校验加载逻辑目录；未知、
畸形、未发布、摘要不一致或应用不支持的发布版失败关闭。响应只投影规则类型、稳定键、显示名
和摘要，不公开数据库 ID，不回退到新战役默认发布版，也不显示 `DRAFT` 注册项。

只读 `GET /api/host/diagnostics/database`：它必须复用完整主机边界，重新校验 JNDI 连接和 `schema_meta`；失败时只返回统一的不可用错误，不暴露异常、SQL、路径或凭据。

### 7.3 健康检查

`GET /health` 只返回固定 `OK`，不报告数据库、证书、路径、版本或内部状态。初版仅要求通过 loopback 访问。需要数据库诊断时使用通过完整主机边界保护的专用页面，而不是扩展公开健康响应。

## 8. MySQL、JNDI 与模式升级

初版使用 Tomcat JNDI JDBC 连接池：

| 参数 | 初始值 |
|---|---:|
| `initialSize` | 1 |
| `minIdle` | 1 |
| `maxIdle` | 5 |
| `maxTotal` | 10 |
| `maxWaitMillis` | 3000 |
| `validationQuery` | `SELECT 1` |
| `testWhileIdle` | true |

数据库账号分离：

- 模式维护账号只在经授权的维护窗口执行升级或重建，运行账号不代行这些操作；
- 运行账号对 `RELEASED` 模组定义只有 `SELECT`；
- 运行账号对可变业务表只拥有必要的 `SELECT`、`INSERT`、`UPDATE`、`DELETE`；
- 口令只存在于 WAR 外的 Tomcat JNDI 配置。

当前 `src/main/resources` 中的模式脚本由 `schema_meta` 检查版本。应用启动和主机数据库诊断
核对批准的模式版本、脚本名和规范 SQL 载荷 SHA-256；诊断使用预编译语句并关闭所有 JDBC
资源。V001 只建立最小空表骨架，不安装业务数据、不创建战役。保留现有数据库时，已应用迁移
及其校验和保持原样，后续结构变化使用前向迁移；整体替换原型 schema 也可采用新的初始化
路径及显式授权的重建。两种路径均须同步代码、清单、测试和文档，明确目标及真实数据处置，
不修改历史摘要来掩盖不一致。选择后一路径不表示相应工具已经实现或任何重建已经执行。

未配置 `DND_STORAGE_ROOT` 时，Host 通过 `jdbc/DndToolSE` 使用已迁移的运行结构。规则目录与运行状态的分离目标及
加载、外键、存档、权限和回滚边界见[静态规则目录与运行数据库分离设计](rule-database-separation.md)。
新目标按应用当前 JSON 结构在新私有库中初始化，同版残留核验已存模型、计划和真实结构，
不重跑 DDL/seed。同一运行固定规则解释，不引入规则热重载或跨库分布式事务。
当前可选 SQLite 由 `DND_STORAGE_ROOT` 显式选择，经 `StorageApplicationListener` 和
`CampaignStorageService` 装配。它不读取外部自定义规则来源，不连接来源 MySQL，也不使用
新的来源 JNDI。完整文件携带准确冻结 material/链，按内置四包固定投影逐项核验。
具体装配边界见[配置说明](configuration.md#sqlite-装配与运行身份)；启用 SQLite 不表示
已有 MySQL 数据已经迁移。

## 9. 本地存档、导入与恢复

### 9.1 文件边界

当前 SQLite 的完整文件和导入/打开/下载/删除/残留责任按
[运行合同](storage-vitality-loop.md#完整文件与直接恢复)执行；它包含有限运行的全部历史、
初始/当前事实与 pending，采用独立 `VITALITY_CAMPAIGN` 模型。下面的 released dispatcher、
旧 archive format 1 和 MySQL 确认事务不用于该路径；通用完整 format 2 的采用门仍独立。

主机导出单个 UTF-8 `campaign-save.json`，最大 16 MiB，不使用 ZIP。存档包含：

- 格式版本和不可变 `campaign_key`；
- 冻结内置发布版编号、版本和内容哈希；
- 当前完整权威状态；
- 使用 `character_key` 表达的角色和关系；
- 简单物品、节点地图、位置和最小遭遇快照；
- 为恢复当前 DM 上下文所需的最近最多 50 条内部可展示事件/检定快照。

存档不包含：

- 数据库 ID、`row_version`、`host_operation` 幂等记录或完整历史；
- 模组定义本体；
- Session、CSRF Token、数据库口令、证书私钥、Tomcat 配置或本机路径；
- 任何产品范围外的公开身份、公开配置、公开投影、公开事件或网络 epoch。

版本 1 的导出 DTO 使用固定字段顺序，但导入不得依赖字段顺序。根对象由
`formatVersion`、`campaign`、`module`、`characters`、`fields`、
`classLevels`、`skillProficiencies`、`saveProficiencies`、`items`、`maps` 和
`recentEvents` 组成：

- `campaign` 只保存不可变 `campaignKey`、规范化名称和当前状态；
- `module` 只保存冻结模组键、发布版本和规范内容 SHA-256，不复制模组定义；
- 角色字段按声明类型输出单一 JSON 标量；职业、技能、豁免和物品关系均以
  `characterKey` 关联；
- 节点地图只保存稳定地图键、类型、队伍节点，以及可选的活动遭遇状态、阵营和
  参与者节点；不把已关闭遭遇历史当作当前权威状态导出；
- `recentEvents` 先选取最新最多 50 条具有检定快照或类型为 `NOTE` 的 DM 可展示
  事件，再按内部事件序号升序写入。检定快照保存事件/检定/掷骰模式/修正来源稳定键、
  手工名称、修正、合计、DC 和结果，不复制候选骰、效果计划或完整事件历史。

JDBC 导出仓储在只读 `REPEATABLE_READ` 事务中读取一个一致快照，不锁行、不写库；
数据库 ID 只可作为仓储内部关联临时存在，不得越过稳定 DTO 边界。Service 必须在编码
前重新核对规范 UUID、冻结发布版身份、互斥类型值、关系目标、文本规则、封闭状态以及
事件/检定算术；任一漂移或不完整状态使整个导出失败，不生成部分文件。

上述根对象和每类子对象是当前 format 1 实现的精确字段白名单；修改文件协议须同步明确
当前支持格式、codec 和验证，不能由反射或数据库列自动扩散。角色本体与字段、职业、熟练、物品、遭遇
参与者和事件主体之间的引用只允许 `characterKey` 或 `subjectCharacterKey`，不得输出
任何 `id`/`*Id`。导出读取不得访问 `host_operation`、`field_change`、候选骰、效果计划
或模组定义表；只有受限的当前状态和最新 50 条 DM 可展示事件/检定快照属于载荷来源。

归档生产链只接收服务端稳定快照，不接收 Servlet 请求、Session、Cookie、CSRF Token、
环境变量、系统属性、JNDI 名称或文件路径，也不得自行访问本机文件。数据库口令、证书、
Tomcat 外部配置和秘密选项文件继续只存在于 WAR/Git 之外。此排除边界针对应用秘密和
环境元数据；战役名称、角色名称、物品说明和 DM 事件消息等已经通过各自业务规则保存的
权威文本必须原样导出，不能因为文本碰巧类似路径或配置片段而被静默删除。

format 2 的 DRAFT 角色投影与上述 format 1 白名单隔离。它当前覆盖创建快照/选择、资源、
职业等级、子职业、特性/选择、专长和多职业熟练，并携带 `eventTail` 及这些当前状态实际引用的
最小 `stateEvents` 闭包；闭包只使用 `eventSequence`、事件类型和 `characterKey`，不输出数据库
ID、row version 或完整历史。严格 codec 会拒绝未知/重复字段、非整数词法、悬空/跨角色事件、
未引用事件及非规范顺序，预览只返回计数和原始摘要。V017 以显式 `ARCHIVE_RESTORE` 来源允许
调用方事务重建专长和多职业熟练，同时触发器仍要求正常 `ADVANCEMENT` 写入存在 V015 父记录。
这些组件没有接入 released dispatcher、Host 路由或完整战役确认链；其余领域和全文件组合完成前，
format 2 仍不可用于正式导出或导入。精确字段、事件闭包和恢复来源见
[Archive format 2 DRAFT 角色状态投影](rules/archive-format-2-character-state.md)。

### 9.2 预览和确认

导入只允许本机主机页面执行。在接触数据库前，服务器先把上传内容交给无副作用的严格读取边界：按原始字节执行 16 MiB 硬上限，使用错误报告模式解码无 BOM UTF-8，并以 RFC 8259 严格模式解析单一 JSON 根值。版本 1 根必须是对象，最大容器嵌套深度为 16；每个数组另设 100,000 项资源防护上限，最近事件仍受业务上限 50 条约束。未知字段、缺失字段和任意层级的重复键全部拒绝，读取不依赖对象字段顺序，也不使用“后值覆盖前值”的对象映射。

根对象和每类子对象使用 9.1 的精确字段白名单。字符串、布尔值、数字和显式可空字段必须具有声明的 JSON 类型；整数只接受无小数点或指数的十进制整数词法并检查 Java/业务范围，小数继续限制精度 38、小数位 18。文本必须由合法 Unicode 标量组成；名称沿用 TRIM_THEN_NFC_V1，其他权威文本要求已是 NFC、按码点检查长度并拒绝 C0/C1 控制字符。读取后还要检查规范 UUID v4、稳定键形状、封闭状态、类型化字段互斥值、唯一关系、检定算术、事件顺序，以及字段、职业、熟练、物品、遭遇参与者和检定事件主体对文件内角色键的引用。

严格读取只证明文件结构和值在版本 1 边界内成立，不把文件中的模组引用当作已安装证明，也不访问数据库。模组键、发布版本和 SHA-256 在此处只做形状检查；后续目录校验必须确认目标系统存在编号、版本和内容哈希完全相同的已发布内置版本，并校验地图节点、字段、职业、熟练、物品及事件/检定稳定键确实属于该冻结目录，任一不符才以模组或目录错误拒绝。

预览不写数据库，显示新建/替换判断、对象数量、活动战役归档影响、不可撤销警告和原始文件 SHA-256。确认请求携带 CSRF、幂等键和相同文件摘要，并重新完成全部校验。若影响为 `OTHER_WILL_BE_ARCHIVED`，预览同时给出那一条活动战役的稳定 `campaign_key`；主机确认命令必须携带该精确键，其他影响则该值必须为空。幂等请求摘要使用域分离、长度前缀的规范编码同时绑定原始文件 SHA-256 和这个可空确认键，不能在相同 `request_id` 下改变归档决定。

`campaign_key` 不存在时整战役新建，已存在时整战役替换，不实现角色或字段级合并。只有预览明确提示且 DM 确认后才归档另一活动战役；导入事务重新锁定并要求当前另一活动战役仍精确匹配该确认键，缺失、过期或多余确认均在重建目标前拒绝。归档只更新这一条锁定的当前活动战役，其他已归档战役不在影响范围内。

任何归档、清空或插入前必须对已解析文档再次完成无副作用冲突预检：战役键和角色键为规范 UUID v4；角色、字段、职业、技能/豁免、地图和遭遇参与者的业务唯一关系不得重复；类型化字段必须恰有一个与声明类型匹配的值；简单物品必须满足来源互斥、冻结模板快照、持有者引用及 1—999 数量；整个战役最多一个活动遭遇，所有位置和事件主体必须引用文件内角色及同一冻结地图的合法节点。冻结规则允许多个相同模板作为不同物品实例存在，因此仅凭模板键、名称和数量相同不能判为重复物品。

仓储锁定目标和活动战役后，还要按数据库 ID 顺序锁定现有角色稳定身份并关闭插入范围：目标战役自身已有的角色键可随整战役替换重建；任何导入角色键若已经属于其他战役则返回稳定身份冲突，不得先归档另一战役或清空目标。数据库唯一键、外键和 CHECK 只是最后防线，不能替代上述预检；任一竞态或持久化失败仍由确认 Service 的单一事务整体回滚。

### 9.3 导入事务

```text
锁定目标战役和活动战役约束
  → 重验 campaign_key、character_key、模组哈希和全部引用
  → 按确认结果归档另一活动战役
  → 替换目标战役可变子状态
  → 写入当前权威状态并重建本机数据库 ID
  → 初始化聚合 row_version
  → 生成新的 host_state_epoch
  → 提交
```

存档不携带也不能控制聚合版本或主机 epoch。每次首次成功执行的导入在事务内完整重验并确认
幂等请求不是重放后，才由服务端 `SecureRandom` 生成一个 `1..Long.MAX_VALUE` 的新
`host_state_epoch`；相同幂等请求重放不得再次生成。仓储在锁内要求候选值与目标战役及当前
活动战役的旧 epoch 均不相同，碰撞时在任何归档、清空或插入前失败。新建或替换的战役根
`row_version` 统一初始化为 `0`，所有从存档重建的角色根 `row_version` 也统一初始化为 `0`；
epoch、版本初值、权威状态和成功幂等结果必须在同一事务提交或整体回滚。

确认页面只在同一原始文件仍匹配预览 SHA-256 时启用执行按钮；浏览器按与 Java 相同的域分离、长度前缀规则计算确认摘要，并只在预览影响为 `OTHER_WILL_BE_ARCHIVED` 时携带那一活动战役键。确认 POST 仍经过完整 loopback、来源、Session、CSRF、epoch、版本和幂等边界，服务器随后再次上传读取并执行 9.3 的单一事务。

只有导入事务已经提交并返回 `COMPLETED` 后，Servlet 才使当前主机 Session 失效，以 `Max-Age=0` 删除旧 `DNDHOSTSESSION` Cookie，并返回同源 `303` 到 `/host`。重定向 GET 创建带新 CSRF Token 和当前权威 epoch 的 Session；共享浏览器 Cookie 的其他旧标签页仍持有旧页面 Token/epoch，独立旧 Session 的 epoch 也会在 POST 时与当前活动战役重新比较，因而不能继续写。任何校验、业务或数据库失败都不失效 Session、不重定向，并保持原活动战役和目标战役不变。成功导入不可通过应用撤销；需要保留旧状态时必须在导入前手工导出。

### 9.4 进程重启后的状态恢复

本节描述未启用 SQLite 时的恢复。该模式中 MySQL InnoDB 表是战役权威状态；Tomcat Session、Servlet、Service 和 Repository 都不缓存
战役、角色、物品、地图、遭遇或事件上下文。每次 `/host` GET 都重新调用只读总览 Service，
由 Repository 取得新的连接并在单个 `REPEATABLE_READ` 事务中重建活动战役、冻结模组绑定、
角色摘要、简单物品、地图与队伍位置、当前活动遭遇及最近最多 20 条可展示检定/消息。因此
Tomcat 重启只会轮换 Session/CSRF；MySQL 正常重启并完成自身恢复后，同一持久化业务状态会由
下一次请求重新呈现，Session 中的 epoch 也从当前活动战役刷新。

数据库暂时不可用时页面只显示稳定的 `DATABASE_UNAVAILABLE` 状态，不保存失败快照；后续 GET
必须重新取连接并重读。应用启动和恢复路径不执行 DML、不自动导入存档、不创建隐藏备份，也
不依靠 `host_operation`、本地文件或 JVM 内存重建权威状态。MySQL 异常关闭时的数据恢复由
InnoDB 和既有运维备份边界负责，应用不得自行猜测或补写部分状态。

## 10. 验证重点

下列清单核对默认 MySQL Host 及其存档合同。SQLite 的完整历史、文件与恢复另按
[运行合同](storage-vitality-loop.md)核对，玩家入口另按[安全模型](security.md)核对，
不能将两种模式的版本、规则发布或文件字段互相套用。

- 只有精确 `http://127.0.0.1:8080` 能访问主机路由；
- `localhost`、`::1`、局域网 IP、错误端口、错误 Host/scheme 得到相同普通 `404`；
- 伪造 `X-Forwarded-*` 不能改变来源判断；
- 主机 GET/HEAD 不改变状态，写入只接受 POST；
- Origin/Referer、Fetch Metadata、Session、CSRF、epoch、版本、幂等键和摘要逐项失败时不能写入；
- Cookie 名称、属性、超时和主机安全响应头与设计一致；
- 同一 `request_id` 重试只执行一次，检定只掷一次骰；
- `event.note` 的幂等重放只返回同一根事件，不创建检定；MANUAL 不依赖事件模板且只使用冻结发布版全局效果白名单；
- PC/NPC 由 DM 控制，没有玩家所有权或批准状态；
- 角色改名、类型变化、归档/恢复不改变 `character_key`，复制生成新键；
- 名称 NFC、长度、控制字符和允许重名规则正确；
- 任一角色聚合子数据修改递增 `row_version`，旧版本返回 `409`；
- 多目标检定在掷骰前核对全部预期版本并稳定排序锁定，每个实际修改的角色在一次命令中只递增一次版本；
- 类型化字段一行只使用匹配的值列；
- 属性调整值、总等级、熟练加值、技能和豁免计算正确；
- 规范哈希与数据库 ID、插入顺序和时间戳无关；
- NFC、CRLF/LF 和等价小数产生相同规范哈希；
- 业务字段变化必然改变哈希，非法发布版不能进入 `RELEASED`；
- 已发布定义不可增删改，运行账号没有写权限；
- 没有有效内置发布版时不能创建战役；
- 创建战役恰好写入一行冻结 `campaign_module`；
- 模组哈希异常时拒绝角色、检定、事件和导入；
- 检定保存全部候选骰、选中骰、修正、合计、DC 和结果；
- 客户端不能提供骰子结果；
- 成功/失败两分支的效果与参数都按封闭类型和稳定顺序保存，未选中分支不产生权威变化；
- 任一效果非法时状态、骰子、效果、字段变化和事件整体回滚；
- 只有活动遭遇参与者能写入实体位置；每个战役最多一个活动遭遇，同一角色不能重复参战，定位不能隐式加入遭遇；
- 数据库资源确定性关闭，多次本机操作后连接数不持续增长；
- 存档不包含数据库 ID、行版本、幂等记录、模组定义、凭据、私钥或本机路径；
- 预览不写库，文件摘要变化或模组哈希不同不能确认；
- 重复/冲突业务键或非法关系使导入整体回滚；
- 成功导入生成新 `host_state_epoch` 并使旧页面失效；
- Tomcat/MySQL 重启后战役、角色、物品、地图和当前事件上下文恢复；
- WAR、Git、存档和日志不包含口令、Cookie、CSRF Token 或私钥；
- 产品不出现 `/display`、`/api/public/*`、`public_*` 表或未使用公开字段。

## 11. 可选玩家入口的运行边界

玩家入口仅在 `DND_STORAGE_ROOT` 和玩家配置同时存在时可用，默认 MySQL Host 不提供此能力。
`DND_PLAYER_ORIGIN` 与 `DND_PLAYER_BIND_ADDRESS` 必须指定同一个规范 IPv4 地址和
`https://IP:8443`；当前不接受 DNS 名称、IPv6、通配绑定、其他端口或明文 HTTP。
地址只允许 loopback、私有或链路本地 IPv4，应用不创建监听或更改证书、系统信任和防火墙。
配置与部署分别见[配置说明](configuration.md)和[部署指南](deployment.md)。

`PlayerBoundaryFilter` 在全部分派前执行。8080 拒绝玩家路径；玩家 Connector 只提供
`/player`、两份固定静态资源及有限 `/api/player/*` 白名单，拒绝根页、Host 页面/API、
完整文件和诊断。请求必须匹配实际 TLS、本地地址、端口和精确 Host，写入另核对精确
origin 与 Fetch Metadata，不信任代理转发头。

邀请只产生申请；DM 批准设备后，每次读取与写入仍重查当前成员资格及授权版本，动作还要
匹配 PC 控制、当前候选、版本和单一写连接。DM 仍独占完整文件管理与保存/结束。
安全凭据、限流和重连查证见[安全模型](security.md)，实际运行方法和隔离 HTTPS 测试见
[测试指南](testing.md)。隔离 loopback 测试不能代替真实局域网设备的证书、信任和网络验收。
公网、匿名展示、`/display`、`/api/public/*` 和全局账号仍不提供；未采用设计见
[玩家参与](design/v2/player-participation.md)。

## 12. 设计总结

1. **产品边界**：一台主机、一名本机 DM；默认 MySQL Host 与显式 SQLite 有限模式分别执行各自合同。
2. **网络边界**：默认只启用 `127.0.0.1:8080`；可选玩家使用独立 HTTPS/授权，公网及匿名展示不在范围内。
3. **接口边界**：Host 路由与玩家固定白名单分别受过滤器保护；玩家 Connector 不提供 Host、根页、诊断或文件。
4. **数据边界**：MySQL 保留冻结 v1；SQLite 的战役成员、控制、共享和历史随完整文件保存，运行安全凭据排除在外。
5. **安全边界**：loopback 仍保留严格 Filter、CSRF、来源、Session、版本和幂等保护。
6. **业务能力**：在既有规则哈希、事务、事件和存档机制上逐步完成角色、冒险、战斗、法术、装备、怪物和状态规则。
7. **演进原则**：按产品原型政策支持当前版本，设计、模型与协议可协调替换；同一受支持运行保持规则、摘要、事务和恢复一致，真实数据处置及已应用迁移仍遵守明确目标与独立授权。
