# v2 采用边界

本页区分当前有限 SQLite 路线、原 MySQL 保管/独立服务与未来完整目标。详细生产入口和
逐域验收状态见[领域接合表](domain-integration.md)；不在本文保存易失效测试计数。
合同与源码的协调修改不代表真实迁移、安装接受、部署或完整 SRD 发布。

## 模组分层的采用顺序

当前接合战役规则上下文、领域类型、本机规则库、有限外部内容与分空间执行。
后续按真实消费者逐机制扩展。
完整 SRD、默认切换和部署各自验收。[四层设计](module-layering.md)

| 目标 | 当前差距 | 完成门 |
|---|---|---|
| 核心保障与应用语义分开 | 角色和战斗历史由领域拥有。`CampaignStorageService` 仍集中视图、事务和文件协调 | 继续把通用保障和领域适配分开，维持真实入口、原子结果和完整恢复 |
| 本机规则库与独立接受 | 内置与外部原包经相同导入和独立接受。新建与文件打开重读准确原包，活动与同版残留使用冻结材料。[本机库](local-rule-library.md) | 可重建索引与组合预设为可选扩展，继续维持准确原包、接受与操作结果的一致性 |
| 多场景单焦点、后台冻结 | SQLite 采用唯一安置、焦点代次、空间钟、DM 旅行及各场景 pending 文件。MySQL INTERNAL 沿用原单场景 | [空间合同](world-and-scene-spaces.md)区分现行二维场景与目标跨空间被动因果。真实受限叶子、地形和分阶段旅行分别采用 |

各步以实现和验收证据确定采用范围。旧模型可直接替换，仍有用户价值的待接合责任继续在
领域接合表追踪。[领域接合表](domain-integration.md)

## 当前运行选择

| 边界 | 当前选择及生产落点 | 剩余采用/验收责任 |
|---|---|---|
| 配置与默认 | `DND_STORAGE_ROOT` 显式启用 `HostStorageServlet`/`CampaignStorageService`；未配置默认 MySQL。当前 SQLite 不查 JNDI、不双写、失败不回退 | 默认切换和旧路由退役须完整用户能力/恢复/运维证据及独立授权；不能自动处置真实旧库 |
| 准确规则 | `LocalRuleLibrary` 提供有序准确成员及独立接受。严格生产 reader/compose 和 `CampaignRuleSupport` 校验完整机制，`CampaignRuleContext` 向候选、初始计划、目录、结算与历史验算提供同一战役绑定 | 完整 SRD、新建卡模型与新机制各自采用 |
| 规则文件 | `RULE_BOUND_CAMPAIGN` format 3 携 `Material(interpretation,binding,rules,entries,chains)` 和完整业务聚合。文件打开核对本机重新组合的材料，活动与残留从自身冻结材料重建 | 错解释、错绑、缺包、未接受、坏链或超出支持范围均拒绝 |
| 物理结构 | 应用 JSON + `StorageSchemaCatalog/StoragePlan/SqliteSchemaVerifier`，一次初始化完整写入足迹 | 真实引擎、外键、锁、编码、进程/文件故障及容量分别验证；恢复不补表/seed |
| 复杂状态 | 严格 typed TEXT 聚合保存全部 current/history，关系列与索引逐行核验。角色与战斗的类型和结构校验归领域，JDBC 与文件是适配 | 核对字段、图闭包和独立期望，坏投影拒绝 |
| 身份/初始化 | core run、epoch 与 HTTP 收据重新建立。`Schema2PreparationIdentity`、原事件键和版本作为稳定业务事实保留。INITIAL 完整保存后才开放写入，玩家名单和控制关系归独立参与聚合 | 任意加入等级或完整角色编辑器等未来输入另设计，保留初始及当前事实 |
| 当前规则角色 | 标准数组 fighter≤6、Life cleric=1、Wizard≤5；初始计划、授予/成长/ASI/资源/装备/健康/休息及书籍等复用纯规则；单职业战士正常升至 5 级取得 [Extra Attack](../../rules/schema-2-extra-attack.md)，本人 Attack 序列额度为 2，6 级保持 | 其他未实现职业特性仅 OPTION 目录不自动授予；擒抱、挣脱、拖带和释放仍无消费者，Extra Attack 不代表 V04 整条轨迹通过 |
| 原正式自由卡/Manual | 独立 typed DM-managed 领域接合基础字段/职业等级/熟练、MANUAL/SAVING_THROW 和有序效果；共用空间安置与 DM 裁定战斗 | 当前实际实现/测试/界面与精确差距以接合表核实；未获本次证据前不能宣称全 v1 闭合或以文档排除 |
| 检定分支后果 | `check-effects` 使用战役绑定的检定和两分支预检，提交同根健康、物品与消息后果。标准与家规组合沿同一入口结算 | `CampaignRuleBindingIntegrationTest` 核对组合差异与恢复。`CampaignCheckEffectsTest` 核对预检、原子后果和失败边界 |
| 场景/动作/效果/怪物 | 角色、场景与空间聚合及完整历史；每场景一个未决 flow、焦点叶子、原骰、成本、压制和怪物充能 | 每个真实机制的正常/坏规则/版本/回滚及保存 pending 直接恢复；不从协议推导未知动作支持 |
| 共同事务 | 独占根锁/串行门 + `BEGIN IMMEDIATE`，聚合/投影/根子历史/revision/接受收据一次提交 | commit/rollback/关闭未知阻断；显式真实库全验后新 epoch；无持久接受意图不自动重试 |
| 接纳协议选择 | 不采用独立 C0/NOT_COMMITTED 裁定事务；游戏请求不成为恢复重放队列 | 该方案替换为当前单实例合同，并非“缺 C0 仍冒充完成”；将来异步/多实例须重设计 |
| 文件生命周期 | INITIAL/MANUAL/FINAL P0—P3，严格上传/下载/打开，显式恢复/放弃及准确删除/清理 | 各窗口真实故障、不可执行半初始化、收据与字节一致及未知文件保护。缺当前解释和绑定的旧 runtime 材料、原 MySQL archive 与内部片段均拒绝 |
| 周期 AUTO/玩家步骤 | 当前每命令 SQLite 耐久，手动和 FINAL 全文件；不把周期 AUTO 或玩家步骤作为运行必需 | 自动便携检查点先测量容量/阈值/增长/保留再单独采用；玩家步骤当前仍是业务目标，见专章 |
| 局域网玩家参与 | 当前 SQLite 已有成员/控制/共享与历史、独立会话/设备恢复、服务器候选/裁剪、单写/暂停和有界裁定原型；玩家 HTTPS 默认关闭 | [玩家参与](player-participation.md#当前原型与剩余采用)列出已接消费者及自由卡直接操作/完整交互/确证未提交工具等缺口；可信 LAN 手机/桌面实测和部署仍须独立验收，Host Filter 保持原边界 |
| 运维与平台 | 原 MySQL 迁移/账号/真实数据保管；SQLite 仅使用满足当前锁/权限/硬链接/目录 force 的受管根 | 原生 Windows 文件系统适配、真实断电/低磁盘等具体平台能力仍须独立证据；不删除旧账本/checksum |

## 统一完成门

1. 每个现行字段、原骰、初始/当前值、所有根子历史、消耗对象与 pending 有唯一保存/
   推导/排除映射；不能以空数组、只迁 ACTIVE 或近期截断掩盖缺口。
2. strict typed codec、JSON 结构、闭包/计划和真实仓储共同实现，精确规则/业务引用可独立
   核验。关系投影损坏使读取拒绝，恢复不重建“正确索引”后放行。
3. 初始化 DDL/seed/材料/业务/控制在同事务，commit 后完整读回，INITIAL P0—P3 接受
   后才执行；文件打开是新本地运行，残留是保留原库的显式恢复，二者分别验证。
4. 真实命令的全部目标/两分支/版本/容量/继续义务先于 RNG，状态、原骰、根子历史和
   接受结果共同回滚/提交；响应未知不能重做或通过新 request 绕过。
5. 手动保存支持所有已提交中途状态；END 不结算 pending。上传/下载/打开/删除/放弃
   分别识别准确对象，坏文件和未知目录不覆盖、不递归删除。
6. 当前有限模型和已有正式能力的差异有实际接合/验收记录；测试存在、自洽往返、设计
   名称、安装成功或“无冲突”均不代表本次通过、已部署或完整发布。

<a id="phase-four-adoption"></a>

## 第四阶段目标的采用门

[规则行为接口验证](rule-behavior-interface-validation.md)区分原接口判定、修订合同的
纸面复验与当前运行证据。应用采用必须沿实际 producer、共享计划、唯一领域 consumer、
私有库事务、完整文件和用户入口闭合。按上面的统一门核对正常、边界、错误输入、重复请求、
过期版本、中途恢复及提交故障，各项证据随实际接入记录。

| 目标族 | 采用所需的实际证据 |
|---|---|
| 检定、攻击替代、反应和共享伤害 | 真实机会与公开时点、唯一攻击额度、完整目标和分量防御、同一伤害阶段及失能/专注后继。[动作继续](action-continuation-protocol.md) · [共享伤害](multi-target-save-damage.md) |
| 三维空间与被动跨空间因果 | 实际区域、视线和位置证明，联动安置及不丢失的回归叶子。同阶段共同提交前后台健康，真实受限选择与后继随原因保存，后台时钟和普通动作冻结。[空间与区域](spatial-and-area-contract.md) · [因果作用域](world-and-scene-spaces.md#causal-scope) |
| 持续效果、形态、召唤和疾病 | 区域进入/START、抗魔局部原因、回转溢出、群组退场和感染的实际 producer 与 consumer。恢复验证旧骰、原健康、真实位置、持续来源、发生游标及完整 pending。[持续效果](ongoing-effect-protocol.md) · [角色状态与历史](character-state-and-history.md) |
| 日历、期限、魔法物件与储存施法 | 真实空间推进、自然日/黎明的唯一消费、迁移余量、实物与授权、魔杖状态和符文触发，连同中途应用与直接恢复验收。[时间与日历](time-and-calendar.md) · [物件与魔法物品](objects-and-magic-items.md) · [施法扩展](spellcasting-extension-contract.md) |

有限 Divine Intervention 与 Glyph 的确切类型化分支按各自合同采用。任意神祇援助的
自动执行与开放符文谓词仍属开放范围。现行二维场景、规则库和 format 3 的实际证据仍以
[领域接合表](domain-integration.md)为准。

## 保留的未完成目标

完整 SRD 和未登记机制、跨空间因果与新增空间交接的运行采用、
任意加入等级/角色编辑器、玩家步骤、
玩家参与原型的剩余交互与真实 LAN 设备验收、周期 AUTO 的容量/保留策略、更广平台及最坏规模的耐久/恢复验证继续保留。
原正式自由卡、Manual 与效果等差距在接合表按实现与本次证据收敛，不能宣称已永久排除。
本机规则库承担新建和文件打开的准确原包与接受检查。独占工作库事务承担游戏接纳，
耐久 SQLite 和显式检查点承担当前文件生命周期。

## 验证与外部检查点

纯文档更新不运行 Maven。实现以相关定向规则/共享 SQLite/完整文件/HTTP/进程故障测试
及必要 WAR 审计取得证据，见[测试指南](../../testing.md)。当前测试定位包括
`LocalRuleLibraryTest`、`BundledCampaignRulesTest`、`CampaignRuleSupportTest`、`CampaignRuleContextTest`、`CampaignRuleBindingIntegrationTest`、
`CampaignDomainEngineTest`、`CampaignCheckEffectsTest`、
`CampaignDomainRecoveryTest`、`CampaignDomainsIntegrationTest`、
`CampaignDomainsCombatIntegrationTest`、`CampaignStorageLifecycleTest` 及结构/文件/故障/
HTTP 族；DM-managed 的最终验证入口随真实实现登记。

原 MySQL/来源的真实约束、权限、维护和保管仍按自身合同。来源安装/接受、迁移授权、
真实数据导出/恢复/删除、应用部署与默认切换各自证明；保留数据库用前向迁移，重建须
明确授权/备份，不改已应用 checksum。无跨版本兼容要求不授权删除真实资料。
