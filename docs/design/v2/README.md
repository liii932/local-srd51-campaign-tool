# v2 设计导航

本目录记录当前应用的存储/文件/事务选择及完整产品的后续目标。设计属于项目正式文档，
不由 Agent 工作区、历史完成记录或文档状态授予实现/发布资格。原型只支持当前应用声明
的模型；同版准确规则、状态/历史、事务和恢复必须一致，真实数据处置仍需独立授权。

“v2”是设计简称，不是所有版本字段都取数字 2。完整 SRD 族
`dnd5e2014_srd51_se` release `"1"` 的 canonical/archive 目标仍为 `2/2`，状态仍 DRAFT。
当前显式 SQLite 入口使用本机已接受的准确组合，并提供标准与家规所需的原包作为导入候选。
SQLite 完整文件 `RULE_BOUND_CAMPAIGN` format 3 与 MySQL archive dispatcher 分别管理。

## 模组分层目标

[模组分层与应用执行架构](module-layering.md)规定运行核心、应用规则机制、规则模组和界面的
职责及端到端采用顺序。当前准确组合、战役上下文、领域类型及本机规则库已接合。
单 DM 同时运行一个活动战役，每次打开建立独占私有 SQLite。新建及移机打开须在本机导入并
接受准确原包，活动和同版残留恢复使用自身冻结材料。[本机规则库](local-rule-library.md)

当前 SQLite [世界与场景](world-and-scene-spaces.md)允许不同主体处于多个世界节点或局部
二维网格场景。当前只有焦点可执行，后台时钟和游戏事实冻结。DM 显式交接与裁定旅行耗时，角色
恢复资格按累计经历时间计算。完整文件保留所有场景、焦点、安置、时钟与待继续事项。

第四阶段的[规则行为接口验证](rule-behavior-interface-validation.md)保存 16 个 SRD
样例的原接口判定，并记录修订合同的纸面复验及当前程序证据。新合同规定焦点和后台
可以怎样在受限因果作用域内结算同一次伤害。后台时钟、个人经历和普通操作仍冻结。
纸面轨迹用于核对设计，实际采用按[采用边界](adoption-boundaries.md#phase-four-adoption)
逐机制验证。[跨空间因果](world-and-scene-spaces.md#causal-scope)

## 当前选择与实现边界

`DND_STORAGE_ROOT` 启用当前有限 SQLite 路线；未配置仍使用原 MySQL 简化模式。当前选择是：

- 每次新建/文件打开一个独占私有 SQLite；全领域后果、根子历史、版本与接受收据一次业务事务提交。
- DM 明确选择本机已接受的 1–8 个有序准确成员，组合须满足现有建卡模板与机制闭包。角色创建、候选、目录与来源、结算和历史验算使用同一战役规则上下文。
- 复杂状态用严格 typed TEXT 聚合，关系投影逐行独立核验；不要求继续复制旧 MySQL 叶表形状，也不接自由 JSON 属性袋。
- 完整 JSON 携冻结规则、准确解释与绑定、完整字段链、初始及当前事实、历史与 pending。文件打开对照本机重新组合的材料，工作库恢复重建自身规则并核对绑定及摘要。
- `Schema2PreparationIdentity` 是保留初始与历史的稳定业务标识。物理 core run、epoch、HTTP 与保存收据在本机重新建立。
- 独占根锁、串行门和 `BEGIN IMMEDIATE` 接纳；不另建独立 C0/NOT_COMMITTED 裁定。未知提交/回滚阻断，显式 recover 核真实库/全部收据与历史后更新 epoch；不重播未持久请求。
- INITIAL/MANUAL/FINAL 共用 P0—P3；周期 AUTO 与玩家步骤不是当前运行必需，后续自动便携检查点先完成容量/保留测量及独立实现。

实际生产入口、旧能力接合差异和本次验证状态统一见[领域接合表](domain-integration.md)，
用户操作/平台要求见[运行合同](../../storage-vitality-loop.md)。本页不代替逐领域 owner 和测试证据。

## 如何阅读

| 主题 | 文档 | 职责 |
|---|---|---|
| 四层职责与执行接口 | [模组分层](module-layering.md) | 核心/机制/内容/UI、唯一 owner、规则绑定、真实调用链和分步验收 |
| 第四阶段行为承载验证 | [规则行为接口验证](rule-behavior-interface-validation.md) | 16 个样例的原接口判定、修订目标的纸面复验、开放范围和程序证据分列 |
| 本机准确规则与接受 | [本机规则库](local-rule-library.md) | 原包保管、独立接受、新建/文件打开复核；活动与残留不回源 |
| 世界、局部场景与跨空间作用 | [空间与焦点](world-and-scene-spaces.md) | 唯一位置、多场景单焦点、后台冻结、受限被动因果及完整恢复 |
| 几何、区域与安置 | [空间与区域](spatial-and-area-contract.md) | 目标三维体积、视线与完整区域证明、联动移动、生成与强制回归 |
| 时间、期限与日历 | [时间与日历](time-and-calendar.md) | 目标空间钟、个人经历、自然日与黎明、期限交接及消费去重 |
| 物件、魔法物品与符文 | [物件与魔法物品](objects-and-magic-items.md) | 实物身份和互动、同调、魔杖充能、符文触发与保存 |
| 产品、原型与网络范围 | [产品说明](../../product.md)、[架构](../../architecture.md) | 当前 SQLite/默认 MySQL、支持范围、loopback 与真实数据责任 |
| 当前领域能力与验收 | [领域接合表](domain-integration.md)、[采用边界](adoption-boundaries.md) | 实际入口、初始/当前/历史覆盖、仍需接合的旧能力与后续门 |
| 规则身份、作者输入与组合 | [模组接口基线](module-interface-baseline.md) | 严格 schema-2、依赖、字段操作、完整链和有限机制的执行资格 |
| 规则材料、存储与版本 | [规则存储](rule-storage-and-versioning.md) | 战役冻结材料、独立本机规则库、准确绑定与文件模型 |
| 结构声明与纯计划 | [结构合同](storage-schema-and-planning.md) | 应用 JSON、有限 SQLite 编译、依赖、DDL/seed、首次初始化与既有结构验证 |
| 数据 owner 与替换 | [数据映射](storage-data-map-and-rollout.md) | 当前物理表/正文/投影映射、原 MySQL 清单及配置/测试/运维边界 |
| 角色与完整因果历史 | [角色状态与历史](character-state-and-history.md) | 业务和运行身份、状态及因果历史、形态健康、召唤与群组回合 |
| 局域网玩家参与原型 | [玩家参与](player-participation.md) | 当前 SQLite 成员/控制/共享、有限动作/裁定、独立 HTTPS 边界与重连原型；真实设备信任和剩余交互另验收 |
| 单写事务与故障恢复 | [运行与事务](runtime-and-transactions.md) | 独占接纳、预检/RNG、共同提交、未知查证、新 epoch、初始化和清理 |
| 完整文件与生命周期 | [保存与恢复](save-and-recovery.md) | frozen 规则/全事实、严格编码、P0—P3、导入/下载/删除/END/残留 |
| 后续玩家步骤/自动文件检查点 | [步骤与自动保存](player-steps-and-auto-save.md) | 明确未采用部分、未来计步/阈值/增长/轮换及验收，不能代替当前耐久事务 |
| 每机制责任 | [责任清单](mechanism-integration-checklist.md) | 内容、真实消费者、状态/历史、保存恢复、清理和独立证据的 17 项责任 |
| 动作与效果 | [动作继续](action-continuation-protocol.md)、[持续效果](ongoing-effect-protocol.md) | 检定、动作替代、反应和选择、移动区域、真实抗魔、疾病及持续关系 |
| 多目标与施法扩展 | [共享伤害](multi-target-save-damage.md)、[施法扩展](spellcasting-extension-contract.md) | 当前有限机制及目标的对象、多目标阶段、魔法物品授权和储存释放 |
| 规则来源与原文 | [本地 SRD 索引](../../references/srd-5.1/README.md)、[完整规则目标](../../rules/srd-5.1-complete.md)、[覆盖矩阵](../../rules/srd-5.1-coverage.md) | 原文、有限支持、完整 SRD 跨领域发布门 |
| 原 MySQL 内部作者/来源/快照 | [制品](../../rules/schema-2-rule-artifacts.md)、[安装接受](../../rules/schema-2-installation-and-acceptance.md)、[快照](../../rules/schema-2-runtime-snapshot.md) | 原 MySQL 路线的独立保管、接受和服务门 |

## 未来完整目标与变更规则

完整 SRD 的其余机制、任意加入等级/角色编辑器、跨空间因果与地形目标的运行采用、局域网
玩家参与与步骤、自动便携检查点以及更广平台耐久能力仍需各自实现和验收。已存在旧正式能力的接合
缺口必须在接合表追踪，不能通过改写“当前范围”把它们当作永久不做。

各设计可以按当前需求/证据协调替换。修改时同步代码、测试、正式合同与采用门，删除
已经失效的选择，不以追加一段解释保留互相冲突的规则。来源安装/接受、迁移/授权、真实
数据恢复/删除、发布、部署与默认切换分别授权和证明；代码存在或一次测试不替代它们。
未完成公共事项须明确为继续实施、当前设计替换后已过时、按产品范围暂缓，或有真实外部
Issue/Milestone 证据的转移；不得因文档整理而宣称关闭或已转交。
