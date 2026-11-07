# v2 采用边界

本页区分当前有限 SQLite 路线、原 MySQL 保管/独立服务与未来完整目标。详细生产入口和
逐域验收状态见[领域接合表](domain-integration.md)；不在本文保存易失效测试计数。
合同与源码的协调修改不代表真实迁移、安装接受、部署或完整 SRD 发布。

## 当前运行选择

| 边界 | 当前选择及生产落点 | 剩余采用/验收责任 |
|---|---|---|
| 配置与默认 | `DND_STORAGE_ROOT` 显式启用 `HostStorageServlet`/`CampaignStorageService`；未配置默认 MySQL。当前 SQLite 不查 JNDI、不双写、失败不回退 | 默认切换和旧路由退役须完整用户能力/恢复/运维证据及独立授权；不能自动处置真实旧库 |
| 准确规则 | `BundledCampaignRules` 生产 reader/compose 四个准确有限包；应用支持范围与旧完整 SRD DRAFT/RELEASED 注册分开 | 完整 SRD、任意外部组合/安装接受和新机制独立；metadata 不取得执行资格 |
| 规则文件 | SQLite 与 JSON 均携 `Material(members,rules,entries,chains)`，四集合逐项精确比对本版已验证固定投影及其 composition/final/chain | 错版本、链/摘要/身份或未知机制整份拒绝；当前打开无需外部来源 MySQL |
| 物理结构 | 应用 JSON + `StorageSchemaCatalog/StoragePlan/SqliteSchemaVerifier`，一次初始化完整写入足迹 | 真实引擎、外键、锁、编码、进程/文件故障及容量分别验证；恢复不补表/seed |
| 复杂状态 | 严格 typed TEXT 聚合保存全部 current/history；关系列/索引逐行核验 | 字段/图闭包、独立期望、坏投影拒绝；不因未拆成叶表推定不合法，也不接任意 JSON |
| 身份/初始化 | 新 core run/epoch/HTTP 收据；prepared Identity 和原事件键/version 作为稳定业务事实保留；INITIAL 完整保存前不开写；玩家名单和控制关系由独立参与聚合拥有 | 任意加入等级/完整角色编辑器等未来输入另设计；不能重置历史或从模板补当前值 |
| 当前规则角色 | 标准数组 fighter≤6、Life cleric=1、Wizard≤5；初始计划、授予/成长/ASI/资源/装备/健康/休息及书籍等复用纯规则 | 未实现职业特性仅 OPTION 目录不自动授予；种族属性、完整背景及完整职业不是已交付能力 |
| 原正式自由卡/Manual | 独立 typed DM-managed 领域接合基础字段/职业等级/熟练、MANUAL/SAVING_THROW、有序效果与简易遭遇名单/阵营；不得写入 schema-2 规则角色 | 当前实际实现/测试/界面与精确差距以接合表核实；未获本次证据前不能宣称全 v1 闭合或以文档排除 |
| 检定分支后果 | `check-effects` 使用完整两分支预检、实际检定与同根健康/物品/节点/消息后果 | `CampaignCheckEffectsTest` 与共享 SQLite 域测试核对顺序、晚写失败及文件；仅列入口不等于测试通过 |
| 场景/动作/效果/怪物 | 同一角色/场景聚合及完整历史、唯一 flow/待继续叶子、原骰/成本/压制/怪物充能 | 每个真实机制的正常/坏规则/版本/回滚及保存 pending 直接恢复；不从协议推导未知动作支持 |
| 共同事务 | 独占根锁/串行门 + `BEGIN IMMEDIATE`，聚合/投影/根子历史/revision/接受收据一次提交 | commit/rollback/关闭未知阻断；显式真实库全验后新 epoch；无持久接受意图不自动重试 |
| 接纳协议选择 | 不采用独立 C0/NOT_COMMITTED 裁定事务；游戏请求不成为恢复重放队列 | 该方案替换为当前单实例合同，并非“缺 C0 仍冒充完成”；将来异步/多实例须重设计 |
| 文件生命周期 | INITIAL/MANUAL/FINAL P0—P3，严格上传/下载/打开，显式恢复/放弃及准确删除/清理 | 各窗口真实故障、不可执行半初始化、收据/字节一致及未知文件保护；旧 format 1/内部片段不自动转换 |
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
自动执行与开放符文谓词仍属开放范围。当前运行证据见[领域接合表](domain-integration.md)。

## 保留的未完成目标

完整 SRD 和未登记机制、任意自定义包接受/独立发布、任意加入等级/角色编辑器、玩家步骤、
玩家参与原型的剩余交互与真实 LAN 设备验收、周期 AUTO 的容量/保留策略、更广平台及最坏规模的耐久/恢复验证继续保留。
原正式自由卡、Manual 与效果等差距在接合表按实现与本次证据收敛，不能宣称已永久排除。
当前内置包已替代“每次打开必需来源 MySQL”的前提；当前单事务接纳已替代 C0/裁定方案；
当前耐久库+显式检查点已替代“必须周期 AUTO 才能运行”的前提。它们是明确的设计替换，
不是仍未完成却被隐藏的公共待办。

## 验证与外部检查点

纯文档更新不运行 Maven。实现以相关定向规则/共享 SQLite/完整文件/HTTP/进程故障测试
及必要 WAR 审计取得证据，见[测试指南](../../testing.md)。当前测试定位包括
`BundledCampaignRulesTest`、`CampaignDomainEngineTest`、`CampaignCheckEffectsTest`、
`CampaignDomainRecoveryTest`、`CampaignDomainsIntegrationTest`、
`CampaignDomainsCombatIntegrationTest`、`CampaignStorageLifecycleTest` 及结构/文件/故障/
HTTP 族；DM-managed 的最终验证入口随真实实现登记。

原 MySQL/来源的真实约束、权限、维护和保管仍按自身合同。来源安装/接受、迁移授权、
真实数据导出/恢复/删除、应用部署与默认切换各自证明；保留数据库用前向迁移，重建须
明确授权/备份，不改已应用 checksum。无跨版本兼容要求不授权删除真实资料。
