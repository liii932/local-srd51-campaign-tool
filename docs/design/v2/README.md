# v2 设计导航

本目录记录当前应用的存储/文件/事务选择及完整产品的后续目标。设计属于项目正式文档，
不由 Agent 工作区、历史完成记录或文档状态授予实现/发布资格。原型只支持当前应用声明
的模型；同版准确规则、状态/历史、事务和恢复必须一致，真实数据处置仍需独立授权。

“v2”是设计简称，不是所有版本字段都取数字 2。完整 SRD 族
`dnd5e2014_srd51_se` release `"1"` 的 canonical/archive 目标仍为 `2/2`，状态仍 DRAFT。
当前显式 SQLite 入口运行的是随版本交付的有限四包 profile，不声称该完整规则族已发布，
也不使用旧 MySQL archive dispatcher 激活完整 format 2。

## 当前选择与实现边界

`DND_STORAGE_ROOT` 启用当前有限 SQLite 路线；未配置仍使用原 MySQL 简化模式。当前选择是：

- 每次新建/文件打开一个独占私有 SQLite；全领域后果、根子历史、版本与接受收据一次业务事务提交。
- 复杂状态用严格 typed TEXT 聚合，关系投影逐行独立核验；不要求继续复制旧 MySQL 叶表形状，也不接自由 JSON 属性袋。
- 完整 JSON 携准确 frozen material/完整字段链、初始/当前事实、全部历史及 pending；核对本版四包的 composition/final/chain 指纹，不要求外部来源 MySQL 在线。
- schema-2 prepared Identity 是保留初始/历史的稳定业务标识；物理 core run、epoch、HTTP/保存收据新建并排除于文件。
- 独占根锁、串行门和 `BEGIN IMMEDIATE` 接纳；不另建独立 C0/NOT_COMMITTED 裁定。未知提交/回滚阻断，显式 recover 核真实库/全部收据与历史后更新 epoch；不重播未持久请求。
- INITIAL/MANUAL/FINAL 共用 P0—P3；周期 AUTO 与玩家步骤不是当前运行必需，后续自动便携检查点先完成容量/保留测量及独立实现。

实际生产入口、旧能力接合差异和本次验证状态统一见[领域接合表](domain-integration.md)，
用户操作/平台要求见[运行合同](../../storage-vitality-loop.md)。本页不代替逐领域 owner 和测试证据。

## 如何阅读

| 主题 | 文档 | 职责 |
|---|---|---|
| 产品、原型与网络范围 | [产品说明](../../product.md)、[架构](../../architecture.md) | 当前 SQLite/默认 MySQL、支持范围、loopback 与真实数据责任 |
| 当前领域能力与验收 | [领域接合表](domain-integration.md)、[采用边界](adoption-boundaries.md) | 实际入口、初始/当前/历史覆盖、仍需接合的旧能力与后续门 |
| 规则身份、作者输入与组合 | [模组接口基线](module-interface-baseline.md) | 严格 schema-2、依赖/字段操作/完整链、有限内置与未来自定义接受的区别 |
| 规则材料、存储与版本 | [规则存储](rule-storage-and-versioning.md) | 当前四包、完整 material/链、严格聚合/关系投影、准确版本与保管 |
| 结构声明与纯计划 | [结构合同](storage-schema-and-planning.md) | 应用 JSON、有限 SQLite 编译、依赖、DDL/seed、首次初始化与既有结构验证 |
| 数据 owner 与替换 | [数据映射](storage-data-map-and-rollout.md) | 当前物理表/正文/投影映射、原 MySQL 清单及配置/测试/运维边界 |
| 角色与完整因果历史 | [角色状态与历史](character-state-and-history.md) | 业务/物理身份、不可变初始与当前、资源/装备/场景 owner、根子归属 |
| 单写事务与故障恢复 | [运行与事务](runtime-and-transactions.md) | 独占接纳、预检/RNG、共同提交、未知查证、新 epoch、初始化和清理 |
| 完整文件与生命周期 | [保存与恢复](save-and-recovery.md) | frozen 规则/全事实、严格编码、P0—P3、导入/下载/删除/END/残留 |
| 后续玩家步骤/自动文件检查点 | [步骤与自动保存](player-steps-and-auto-save.md) | 明确未采用部分、未来计步/阈值/增长/轮换及验收，不能代替当前耐久事务 |
| 每机制责任 | [责任清单](mechanism-integration-checklist.md) | 内容、真实消费者、状态/历史、保存恢复、清理和独立证据的 17 项责任 |
| 动作与效果 | [动作继续](action-continuation-protocol.md)、[持续效果](ongoing-effect-protocol.md) | 唯一待继续叶子、反应/选择、来源/压制/到期及具体消费者的适用范围 |
| 多目标与施法扩展 | [共享伤害](multi-target-save-damage.md)、[施法扩展](spellcasting-extension-contract.md) | 既有有限机制与未来对象/体积/新法术的独立接合门 |
| 规则来源与原文 | [本地 SRD 索引](../../references/srd-5.1/README.md)、[完整规则目标](../../rules/srd-5.1-complete.md)、[覆盖矩阵](../../rules/srd-5.1-coverage.md) | 原文、有限支持、完整 SRD 跨领域发布门 |
| 原 MySQL 内部作者/来源/快照 | [制品](../../rules/schema-2-rule-artifacts.md)、[安装接受](../../rules/schema-2-installation-and-acceptance.md)、[快照](../../rules/schema-2-runtime-snapshot.md) | 独立原包/接受和旧服务门，不成为当前内置 SQLite 路线的来源连接前提 |

## 未来完整目标与变更规则

通用自定义包的独立接受/发布、完整 SRD 的其余机制、任意加入等级/角色编辑器、玩家
步骤、自动便携检查点以及更广平台耐久能力仍需各自合同和验收。已存在旧正式能力的接合
缺口必须在接合表追踪，不能通过改写“当前范围”把它们当作永久不做。

各设计可以按当前需求/证据协调替换。修改时同步代码、测试、正式合同与采用门，删除
已经失效的选择，不以追加一段解释保留互相冲突的规则。来源安装/接受、迁移/授权、真实
数据恢复/删除、发布、部署与默认切换分别授权和证明；代码存在或一次测试不替代它们。
未完成公共事项须明确为继续实施、当前设计替换后已过时、按产品范围暂缓，或有真实外部
Issue/Milestone 证据的转移；不得因文档整理而宣称关闭或已转交。
