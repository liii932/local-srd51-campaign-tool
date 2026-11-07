# v2 存储职责图与替换路线

本页维护当前 SQLite 结构/领域/文件的 owner 映射，以及原 MySQL 能力与真实数据保管的
核对入口。结构格式见[结构合同](storage-schema-and-planning.md)，事务与完整文件见
[运行](runtime-and-transactions.md)和[保存](save-and-recovery.md)。当前生产入口、剩余
差异和本次执行证据统一由[领域接合表](domain-integration.md)记录，不由旧表数量推断。

只支持当前应用声明模型，不承诺跨版本工作库升级/存档转换。未配置 `DND_STORAGE_ROOT`
保留默认 MySQL 简化模式；配置后独立 SQLite 入口关闭旧读写路由，不双写、不查旧库补值。
代码接合、数据库迁移、真实数据处置、部署和默认切换分别判断。

## 当前实现与权威

| 边界 | 实际入口与当前选择 |
|---|---|
| 准确规则材料 | `LocalRuleLibrary` 保管原包、导入及本机接受。`CampaignRuleSupport` 校验组合的应用资格，`CampaignDomainData.Material` 保存解释、准确绑定、全部规则行、条目和来源链 |
| 应用结构 | `storage/campaign-schema.json` → `StorageSchemaCatalog` → `StoragePlan`/`SqliteSchemaCompiler`；`SqliteSchemaVerifier` 核现有结构和 seed |
| 业务执行 | `CampaignDomainCommands` 与无连接 `CampaignDomainEngine`，复用现有纯领域规则；自由卡用独立 DM-managed typed 领域 |
| 共同事务 | `CampaignStorageService` 持根/目录锁、串行接纳，`BEGIN IMMEDIATE` 一次提交全部聚合/关系投影、根子历史、revision 和接受 |
| 文档/关系存储 | `CampaignDomainRepository` 写 closed typed TEXT 和关系投影，调用方拥有事务；读取逐行核验，不修复坏索引 |
| 文件 | `CampaignCodec`、`CampaignDomainCodec/Validation` 保存 `RULE_BOUND_CAMPAIGN` format 3，包含全部空间、唯一安置、焦点和各 pending。打开复核本机原包及接受，活动和同版残留使用冻结规则 |
| 原 MySQL/来源服务 | 原 SQL/迁移/读写/账号职责保留；来源安装接受、内部快照与旧格式入口有独立范围，不成为当前有限 SQLite 调用路径 |

## 当前物理分片与唯一状态 owner

下表按应用 JSON 的实际分片命名。聚合里的资源、装备、动作等 typed 状态是其唯一事实；
同名关系表是独立核验的投影，不是另一份可写 owner。物理分片粒度不等于文件分区或
Java 责任类型，也不要求为每个叶字段保留旧 MySQL 子表。

| 分片/表 | 内容、owner 与文件落点 |
|---|---|
| `core`：core_control/core_storage_module/core_command/core_save/core_player_control | 本地 run、模型、计划、模块、epoch、revision、业务接受、保存和玩家安全控制。Service 与 Repository 协调，技术控制留在工作库 |
| `rules-runtime`：rules_runtime_material | 完整 production Material；文件 domains.material 同时保留准确成员、最终规则、entries 和完整链 |
| `characters-runtime`：characters_runtime_character | Character 的名称/类型/active、InitialPlan、完整 History/current；角色 typed 聚合拥有全部成长/HP/生命骰/资源/装备/书籍/休息事实 |
| `scenes`：scenes_world/scenes_space/scenes_clock/scenes_placement | 严格世界聚合及场景、时钟和唯一安置的核验投影。文件保存节点、位置、简单物品、焦点及各空间事实 |
| `combat`：combat_scene | 完整场景/战斗 History/current；含原骰/先攻/轮回合、部署、唯一动作树/窗口、施法/效果/区域、怪物及全部历史依赖 |
| `history-runtime`：history_runtime_root | 连续战役根、原操作与意图、subject、typed 前后事实、角色子操作和场景因果。文件 `domains.roots` 保存全部根，`campaign.eventTail` 核对数量 |
| `resources`：resources_character | 角色 current 资源准确身份/余额/上限/无限标记的关系投影；生命骰/休息及完整资源历史仍在所属角色聚合 |
| `equipment`：equipment_instance | 角色装备实例/持有/穿戴/地面 typed 投影，与角色 current/历史逐行核对 |
| `spellcasting`：spellcasting_book | 角色真实已学/准备/恢复事实投影，不从法术目录补人物状态 |
| `actions`：actions_frame | 场景的完整 flow 帧关系投影；父子/返回点/窗口/已消费成本/唯一继续点保留在场景聚合与全历史 |
| `effects`：effects_instance | 场景持续效果投影，完整来源/目标/压制/专注/终结历史由场景聚合拥有 |
| `monsters`：monsters_campaign/monsters_instance | 战役怪物当前事实及场景实例的核验投影。动作、休息及共享伤害历史随场景完整保存 |
| `manual`：manual_character | 独立 DM-managed 自由卡的核验投影。自由卡与规则角色共享空间安置，DM 裁定场景的模式和占位归空间领域 |
| `runtime`：runtime_profile | 当前有限运行范围的根/静态支持标记，依赖上述完整未来写入足迹，不随当前有无角色/施法者裁剪 |

所有列/键/约束/索引由应用 JSON 完整声明。正文 codec 登记闭合 record/enum，拒绝动态类、
未知/缺失字段、错误标量、重复映射和坏数值/Unicode；无自由属性袋。每次实际读取同时核
全部投影和整场根/子引用，不能损坏后重建索引再继续。新增聚合字段/投影必须同时更新
声明、codec、仓储、验证、完整文件与相关测试。

## 字段、历史与文件映射

| 数据类别 | 当前 owner/保留范围 | 直接恢复要求 |
|---|---|---|
| 稳定战役/角色/对象键 | Snapshot、InitialPlan、角色/场景/世界/manual 聚合 | 保留原值，不按名字或插入顺序生成替代 |
| schema-2 prepared Identity/事件 operation/version | 初始/角色/场景历史中被引用的稳定业务事实 | 文件保留原值，不重建为实际 core run 或旧 HTTP 执行权限 |
| 实际 run/epoch/revision/接受与保存收据 | core 本地控制 | 文件排除，新打开建立新控制，残留完整读验后新 epoch；不从文件取得旧请求资格 |
| 初始计划与初始卡 | 初始准确来源/槽/选择/授予、标准数组 INITIALIZED 事件 | 初始与 current 分开；不重新创建/授予/掷骰解释当前值 |
| 属性/成长/ASI/子职业/特性/熟练 | 角色 current 与全部命令/前后历史 | 保留选择与原骰/来源；未实现 OPTION 元数据不自动获得，不靠导入重算丢失事实 |
| HP/临时HP/死亡/生命骰/资源/休息/每日事实 | 所属角色聚合；资源表仅投影 | 不补治疗、休息、死亡豁免、充能或位数；原剩余/消耗及准确日界时间保留 |
| 装备实例/法术书 | 所属角色聚合及全部历史 | 取得/消耗/掉落/穿戴和书籍学习/准备恢复直接写既有事实，不从目录发物品/法术 |
| 全部检定骰、选择/DC/修正、两分支计划与实际效果 | 角色/检定 typed 结果、根历史及受影响世界/健康/manual 聚合 | 两分支完整预检先于真 RNG；所选效果按原序同根提交，恢复不重应用 |
| 场景/先攻/轮回合/时钟 | 完整 combat History/current，角色时间由 engine 同根协调 | 不用多个可漂移时钟拼出“最新”；原部署/单位/阵营/原骰全部核验 |
| 动作/反应/Ready/施法/共享伤害 | 场景所有 flow/帧/窗口/候选/决定/目标证明/原骰/成本/packet 与全历史 | 已关闭窗口仍可能有必需后缀；保留唯一叶子与已消费前缀，恢复不 PASS/继续/重复扣费 |
| 效果/专注/压制/怪物 | 场景完整实例、当前值/期限/全部原因与历史 | 无贡献、被压制、终结但被引用的实例仍保存，不补 tick 或结束回调 |
| 节点/位置/简单物品/profile/消息 | 世界字段、根 typed 前后事实和 manual 独立模型 | 精确邻接/归属/数量，检定消息归原根，不用 NOTE 代替未知类型 |
| 全部根子历史 | roots 与角色/场景/manual 已记录业务事实 | 连续根、唯一子归属、完整 before/after/原骰；不按 UI 近期列表截断 |

当前文件保存完整冻结材料、解释和来源链。`Material.restore` 从冻结行重建投影并核对准确指纹及
机制闭包。新打开时再用本机原包重组合，逐项核对文件冻结材料和本机接受。DDL、结构计划与本地
执行收据由私有工作库独立保管。

## 原 MySQL 清单的用途与接合差异

附录仍列原运行 V001—V042、来源 V001—V012 的实际持久表，用于能力/字段审查和保管，
不是新模型要照抄的 259 张表，也不是外部数据库已经安装或迁移的证据。

| 原责任 | 当前接合/保管方向 |
|---|---|
| 规则目录/语言工具/PARTITION/内部快照 | SQLite 冻结完整组合与链。本机规则库保管准确原包和接受，原 MySQL 来源、迁移和接受服务独立保管 |
| 原角色自由字段/职业等级/熟练/Manual/普通save/简易遭遇名单/阵营 | 独立 DM-managed typed 领域逐项接合，不能文档排除或伪装成 schema-2 构筑；最终差异/本次证据见接合表 |
| schema-2 角色/资源/装备/健康/书籍/休息叶表 | 原纯规则/完整 typed History 复用到角色聚合，同根/事务/文件，不复制第二条 MySQL 服务事务 |
| schema-2 战斗/动作/效果/怪物叶表 | 完整场景 History 聚合及关系投影，同一角色健康/资源 owner；保留全部已完成/未决事实 |
| v1 检定/成功失败效果/物品/位置/消息 | check-effects 和 manual 对应入口完整预检、有序后果、根历史/共享事务/文件；不能用 note/即时UI结果降级 |
| 永久业务守卫/内部操作 | 当前独占根锁+BEGIN IMMEDIATE，状态/根/接受一次提交；未知显式恢复核库/全部收据，独立 C0/NOT_COMMITTED 方案已替换 |
| 旧 format 1/内部 format 2 片段 | 保留原模式职责，不是新有限文件的 reader；无跨版本兼容或自动业务数据转换承诺 |

## 按当前合同交付与验收

1. **字段/owner/支持范围。** 将所有现行能力、稳定业务键、初始/current、原骰/审计/pending
   映射到严格聚合/投影/文件或有依据排除项。metadata 不执行，缺消费者不能用空表放行。
   自由卡/Manual 现行能力差距须在接合表逐项收敛，不因有限规则模板而默认降 scope。
2. **声明与真实引擎。** 纯 StoragePlan 无分配/连接/RNG，严格声明拒绝坏类型/键/依赖。
   真 SQLite 核 DDL/seed/事实/控制共同提交/回滚、DELETE/EXTRA/外键/UTF-8、锁与句柄。
3. **共同业务接受。** 单独 C0/未提交裁定不再作为门；当前所有真实写入口必须同锁/同
   BEGIN IMMEDIATE 一次写聚合/投影/根子历史/revision/接受。两分支/完整目标先于随机，
   响应未知不重试，recover 核真实库/全部事实与收据后新 epoch。
4. **完整创建/文件/残留。** 初始化结构/seed/规则/全事实同事务，commit 后实际读回，
   INITIAL 完整保存后执行。文件直接恢复与原库显式恢复分开；不重建 prepared 历史键、
   不补表/seed、不重播命令。独立字段/字节期望覆盖坏输入、坏投影和复杂 pending。
5. **本轮完整生命周期。** 角色/场景后果同事务，INITIAL/MANUAL/FINAL P0—P3、导入/
   下载/打开/删除/discard/恢复/准确清理必须逐窗口验证。每命令工作库耐久不是省略完整
   文件的理由；当前无需周期 AUTO 才运行，也不能因此推迟领域 END/故障闭包。
6. **后续自动检查点/玩家步骤。** 先测量完整规则/历史/pending、库/journal、各档案/临时/
   墓碑和内存/时延峰值，再确定阈值/增长/保留并实现。玩家步骤、新机制和完整 SRD
   另有产品/机制门，不从当前存储完成推导。
7. **默认切换与退役。** 只有完整当前用户能力和两类恢复/文件/运维证据满足才单独决定
   默认切换/旧装配退役；当前 MySQL 默认保留，不双写、不变更外部账号/数据或历史。

## 配置、测试、打包与实际数据

`DND_STORAGE_ROOT` 选择 SQLite 私有根，应用管理 `runs/`、`saves/` 和独立 `rule-library/`。
本机规则库与运行工作库使用各自的存储和事务。

当前测试入口包括结构/引擎、BundledCampaignRules、CampaignDomainEngine、共享 SQLite
域/战斗/文件/生命周期、HTTP/安全和进程故障族；manual/check-effects 的精确新增类及
本次观察结果登记在接合表。类存在不证明通过；来源/旧 MySQL 测试不代替 SQLite 或平台
锁/刷盘/低磁盘验证。可重复命令见[测试指南](../../testing.md)。

WAR 必含应用结构、严格聚合与领域代码、SQLite JDBC、Host 页面与脚本及 `storage/rules/` 的五份原包。
其他作者源/来源SQL/离线写入类、真实库/journal/存档、秘密/日志/机器路径仍排除。生产
资源变化须审计 WAR 字节/SHA-256/条目和内容；实际部署按[唯一部署流程](../../deployment.md)。

默认保管原 MySQL 运行/来源库、准确原包/接受账本和必要可读应用。保留数据库用前向
迁移，不改已应用摘要；重建/重置、真实导出恢复删除、来源安装接受、Tomcat启停/WAR
替换和业务写入分别授权。无跨版本兼容只免除格式延续义务，不减少真实数据责任。

## 附录：现行持久表完整清单

表名仅用于核对替换覆盖，不把现有 259 张表固定为目标物理模型。未列建表的迁移仍可能
修改约束、seed 或现有列，不能因没有新增表就在替换审查中跳过。

### 运行库：206 张

| 建表迁移 | 表名 |
|---|---|
| [V001__stage1_schema.sql](../../../src/main/resources/db/migration/V001__stage1_schema.sql) | `schema_meta`, `module_release`, `campaign`, `campaign_module`, `host_operation` |
| [V002__stage2_module_schema.sql](../../../src/main/resources/db/migration/V002__stage2_module_schema.sql) | `module_rule_constant`, `module_field_definition`, `module_class_definition`, `module_proficiency_tier`, `module_proficiency_bonus_band`, `module_skill_definition`, `module_save_definition`, `module_item_template`, `module_entity_template`, `module_entity_template_value`, `module_entity_template_class_level`, `module_entity_template_proficiency`, `module_check_definition`, `module_roll_mode`, `module_event_template`, `module_effect_definition`, `module_event_check`, `module_event_effect`, `module_effect_parameter`, `module_map_definition`, `module_map_node`, `module_map_connection` |
| [V005__stage2_character_event_schema.sql](../../../src/main/resources/db/migration/V005__stage2_character_event_schema.sql) | `character_record`, `character_class_level`, `character_skill_proficiency`, `character_save_proficiency`, `game_event`, `field_change` |
| [V006__stage2_character_field_value_schema.sql](../../../src/main/resources/db/migration/V006__stage2_character_field_value_schema.sql) | `character_field_value` |
| [V008__stage2_simple_item_schema.sql](../../../src/main/resources/db/migration/V008__stage2_simple_item_schema.sql) | `item_instance` |
| [V009__stage3_check_execution_schema.sql](../../../src/main/resources/db/migration/V009__stage3_check_execution_schema.sql) | `check_execution`, `dice_roll`, `check_effect`, `check_effect_parameter_value` |
| [V010__stage3_node_encounter_schema.sql](../../../src/main/resources/db/migration/V010__stage3_node_encounter_schema.sql) | `map_instance`, `party_world_position`, `battle_state`, `battle_participant`, `entity_position` |
| [V011__complete_character_catalog_draft.sql](../../../src/main/resources/db/migration/V011__complete_character_catalog_draft.sql) | `module_catalog_definition_v2`, `module_catalog_attribute_v2`, `module_catalog_relation_v2` |
| [V012__level_one_character_creation.sql](../../../src/main/resources/db/migration/V012__level_one_character_creation.sql) | `character_creation_snapshot_v2`, `character_creation_selection_v2`, `character_resource_state_v2` |
| [V013__level_advancement_hit_dice.sql](../../../src/main/resources/db/migration/V013__level_advancement_hit_dice.sql) | `character_class_level_v2`, `character_level_advancement_v2`, `character_level_resource_change_v2` |
| [V014__class_feature_lifecycle.sql](../../../src/main/resources/db/migration/V014__class_feature_lifecycle.sql) | `character_subclass_state_v2`, `character_feature_state_v2`, `character_feature_choice_v2`, `character_feature_adjudication_v2`, `character_resource_recovery_v2` |
| [V015__multiclass_asi_feat_draft.sql](../../../src/main/resources/db/migration/V015__multiclass_asi_feat_draft.sql) | `character_advancement_choice_v2`, `character_ability_score_change_v2`, `character_feat_state_v2`, `character_multiclass_proficiency_v2` |
| [V019__runtime-language-snapshot.sql](../../../src/main/resources/db/migration/V019__runtime-language-snapshot.sql) | `runtime_run_identity`, `runtime_rule_snapshot`, `runtime_rule_language` |
| [V020__runtime-tool-snapshot.sql](../../../src/main/resources/db/migration/V020__runtime-tool-snapshot.sql) | `runtime_rule_tool` |
| [V021__schema2-runtime-snapshot.sql](../../../src/main/resources/db/migration/V021__schema2-runtime-snapshot.sql) | `s2_runtime_identity`, `s2_runtime_snapshot`, `s2_runtime_member`, `s2_runtime_language`, `s2_runtime_language_set`, `s2_runtime_language_entry`, `s2_runtime_field_chain`, `s2_runtime_chain_step`, `s2_runtime_control`, `s2_runtime_operation` |
| [V022__character-proficiency-grants.sql](../../../src/main/resources/db/migration/V022__character-proficiency-grants.sql) | `character_proficiency_grant_v2` |
| [V023__schema2-runtime-tools.sql](../../../src/main/resources/db/migration/V023__schema2-runtime-tools.sql) | `s2_runtime_tool`, `s2_runtime_tool_set`, `s2_runtime_tool_entry` |
| [V024__schema2-character-grants.sql](../../../src/main/resources/db/migration/V024__schema2-character-grants.sql) | `s2_runtime_character_source`, `s2_runtime_proficiency_grant` |
| [V025__business-write-guard.sql](../../../src/main/resources/db/migration/V025__business-write-guard.sql) | `business_write_guard` |
| [V026__schema2-initial-plans.sql](../../../src/main/resources/db/migration/V026__schema2-initial-plans.sql) | `s2_initial_plan`, `s2_initial_plan_slot`, `s2_initial_plan_choice`, `s2_initial_plan_grant` |
| [V027__schema2-character-runtime.sql](../../../src/main/resources/db/migration/V027__schema2-character-runtime.sql) | `s2_runtime_class_progression`, `s2_runtime_resource`, `s2_runtime_subclass`, `s2_runtime_feature`, `s2_runtime_feat`, `s2_runtime_proficiency`, `s2_runtime_proficiency_set`, `s2_runtime_rest`, `s2_runtime_class_level_entry`, `s2_runtime_proficiency_entry`, `s2_runtime_growth_grant`, `s2_runtime_rule_prerequisite`, `s2_runtime_chain_prerequisite`, `s2_character_runtime`, `s2_character_event`, `s2_character_event_state`, `s2_character_event_resource`, `s2_character_event_class`, `s2_character_event_hit_die`, `s2_character_event_subclass`, `s2_character_event_feature`, `s2_character_event_feat`, `s2_character_event_proficiency`, `s2_character_command_ability`, `s2_character_command_proficiency`, `s2_character_command_hit_die` |
| [V028__schema2-check-rules.sql](../../../src/main/resources/db/migration/V028__schema2-check-rules.sql) | `s2_runtime_check` |
| [V029__schema2-character-check-history.sql](../../../src/main/resources/db/migration/V029__schema2-character-check-history.sql) | `s2_character_check` |
| [V030__schema2-equipment-health-rules.sql](../../../src/main/resources/db/migration/V030__schema2-equipment-health-rules.sql) | `s2_runtime_equipment_item`, `s2_runtime_health`, `s2_runtime_vital_effect` |
| [V031__schema2-equipment-health-history.sql](../../../src/main/resources/db/migration/V031__schema2-equipment-health-history.sql) | `s2_character_event_equipment`, `s2_character_equipment_command`, `s2_character_health_event`, `s2_character_health_die` |
| [V032__schema2-combat-rules.sql](../../../src/main/resources/db/migration/V032__schema2-combat-rules.sql) | `s2_runtime_combat`, `s2_runtime_weapon_attack`, `s2_runtime_movement` |
| [V033__schema2-combat-runtime.sql](../../../src/main/resources/db/migration/V033__schema2-combat-runtime.sql) | `s2_combat_runtime`, `s2_combat_member`, `s2_combat_event`, `s2_combat_dependency`, `s2_combat_character_result`, `s2_combat_command_point`, `s2_combat_command_deployment`, `s2_combat_command_tie`, `s2_combat_roll`, `s2_combat_fact`, `s2_combat_state`, `s2_combat_actor`, `s2_combat_initiative`, `s2_combat_flow`, `s2_combat_frame`, `s2_combat_window`, `s2_combat_opportunity`, `s2_combat_opportunity_item`, `s2_combat_movement`, `s2_combat_movement_point`, `s2_combat_attack`, `s2_combat_attack_die`, `s2_character_combat_input` |
| [V035__schema2-spell-rules.sql](../../../src/main/resources/db/migration/V035__schema2-spell-rules.sql) | `s2_runtime_spell`, `s2_runtime_spellcasting` |
| [V036__schema2-spell-runtime.sql](../../../src/main/resources/db/migration/V036__schema2-spell-runtime.sql) | `s2_combat_cast_command`, `s2_combat_cast_command_target`, `s2_combat_ongoing_effect`, `s2_combat_ongoing_target`, `s2_combat_cast`, `s2_combat_cast_target`, `s2_combat_cast_die`, `s2_combat_saving_throw` |
| [V037__schema2-wizard-environment-rules.sql](../../../src/main/resources/db/migration/V037__schema2-wizard-environment-rules.sql) | `s2_runtime_wizard_spellcasting`, `s2_runtime_environment`, `s2_runtime_spellbook_member` |
| [V038__schema2-reactions-and-ongoing-scenes.sql](../../../src/main/resources/db/migration/V038__schema2-reactions-and-ongoing-scenes.sql) | `s2_character_event_learned_spell`, `s2_character_event_prepared_spell`, `s2_character_spellbook_command`, `s2_character_spellbook_choice`, `s2_combat_ready_input`, `s2_combat_ready_point`, `s2_combat_ready_target`, `s2_combat_zone_command`, `s2_combat_health_command`, `s2_combat_reaction_option`, `s2_combat_window_trigger`, `s2_combat_ready_release`, `s2_combat_ability_check`, `s2_combat_ready`, `s2_combat_trigger`, `s2_combat_shield`, `s2_combat_concentration`, `s2_combat_environment`, `s2_combat_suppression` |
| [V039__schema2-monster-rules.sql](../../../src/main/resources/db/migration/V039__schema2-monster-rules.sql) | `s2_runtime_monster`, `s2_runtime_monster_action`, `s2_runtime_monster_stat`, `s2_runtime_monster_save`, `s2_runtime_monster_skill`, `s2_runtime_monster_defense`, `s2_runtime_monster_condition`, `s2_runtime_monster_language`, `s2_runtime_monster_trait`, `s2_runtime_monster_action_value` |
| [V040__schema2-monster-breath-runtime.sql](../../../src/main/resources/db/migration/V040__schema2-monster-breath-runtime.sql) | `s2_combat_monster_deployment`, `s2_combat_breath_input`, `s2_combat_monster`, `s2_combat_monster_action`, `s2_combat_save_damage_batch`, `s2_combat_breath_candidate`, `s2_combat_breath_target`, `s2_combat_breath_die`, `s2_combat_breath_check`, `s2_combat_breath_packet`, `s2_combat_breath_defense`, `s2_combat_breath_concentration`, `s2_combat_monster_rest_input`, `s2_combat_monster_rest` |
| [V041__schema2-arcane-recovery.sql](../../../src/main/resources/db/migration/V041__schema2-arcane-recovery.sql) | `s2_character_arcane_recovery_choice` |
| [V042__schema2-spell-save-damage.sql](../../../src/main/resources/db/migration/V042__schema2-spell-save-damage.sql) | `s2_combat_spell_damage_source` |

### 来源库：53 张

| 建表迁移 | 表名 |
|---|---|
| [V001__rule-source-schema.sql](../../../database/rules/migration/V001__rule-source-schema.sql) | `rule_schema_meta`, `rule_release`, `rule_language`, `rule_package_installation`, `rule_package_installation_partition`, `rule_installation_control` |
| [V002__tool-catalog.sql](../../../database/rules/migration/V002__tool-catalog.sql) | `rule_tool` |
| [V003__schema2-source-ledger.sql](../../../database/rules/migration/V003__schema2-source-ledger.sql) | `rule_schema2_artifact`, `rule_schema2_acceptance`, `rule_schema2_control` |
| [V004__schema2-character-grant-source.sql](../../../database/rules/migration/V004__schema2-character-grant-source.sql) | `rule_schema2_character_source`, `rule_schema2_proficiency_grant`, `rule_schema2_character_change` |
| [V005__schema2-advancement-source.sql](../../../database/rules/migration/V005__schema2-advancement-source.sql) | `rule_schema2_class_progression`, `rule_schema2_resource`, `rule_schema2_subclass`, `rule_schema2_feature`, `rule_schema2_feat`, `rule_schema2_proficiency`, `rule_schema2_proficiency_set`, `rule_schema2_class_level`, `rule_schema2_proficiency_member`, `rule_schema2_growth_grant`, `rule_schema2_ability_requirement`, `rule_schema2_rest`, `rule_schema2_advancement_change` |
| [V006__schema2-check-source.sql](../../../database/rules/migration/V006__schema2-check-source.sql) | `rule_schema2_check` |
| [V007__schema2-equipment-health-source.sql](../../../database/rules/migration/V007__schema2-equipment-health-source.sql) | `rule_schema2_equipment_item`, `rule_schema2_health`, `rule_schema2_vital_effect`, `rule_schema2_execution_change` |
| [V008__schema2-combat-source.sql](../../../database/rules/migration/V008__schema2-combat-source.sql) | `rule_schema2_combat`, `rule_schema2_weapon_attack`, `rule_schema2_movement`, `rule_schema2_combat_change` |
| [V009__schema2-spell-source.sql](../../../database/rules/migration/V009__schema2-spell-source.sql) | `rule_schema2_spell`, `rule_schema2_spellcasting`, `rule_schema2_spell_change` |
| [V010__schema2-wizard-environment-source.sql](../../../database/rules/migration/V010__schema2-wizard-environment-source.sql) | `rule_schema2_wizard_spellcasting`, `rule_schema2_environment`, `rule_schema2_spellbook_member`, `rule_schema2_wizard_change` |
| [V011__schema2-monster-source.sql](../../../database/rules/migration/V011__schema2-monster-source.sql) | `rule_schema2_monster`, `rule_schema2_monster_action`, `rule_schema2_monster_change`, `rule_schema2_monster_stat`, `rule_schema2_monster_save`, `rule_schema2_monster_skill`, `rule_schema2_monster_defense`, `rule_schema2_monster_condition`, `rule_schema2_monster_language`, `rule_schema2_monster_trait`, `rule_schema2_monster_action_value` |
