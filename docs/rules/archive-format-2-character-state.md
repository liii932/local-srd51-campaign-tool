# Archive format 2 DRAFT 角色状态投影

本合同描述 `dnd5e2014_srd51_se` release `1` 的内部 archive format 2 角色切片。该发布仍为
`DRAFT`；本文不激活格式 2、不改变 format 1、不开放 Host 导入/导出，也不切换新战役默认版。

## 当前投影

`CampaignArchiveV2CharacterState` 只包含稳定业务值：

- `archiveFormatVersion=2` 与非负 `eventTail`；
- `stateEvents`：当前状态实际引用的最小根事件闭包；
- 一级创建快照中的初始能力值和有序选择；
- 当前资源及有限/无限状态；
- 各职业当前等级；
- 当前子职业、已获得特性及有序特性选择；
- 当前专长及多职业熟练增量。

当前切片尚未投影 ASI 后的六项当前能力值，因此不能用于恢复已经发生 ASI 的角色。补齐该状态
必须使用 V019 或合并时下一个可用前向迁移，并同时完成数据库读取、严格 codec 和恢复往返；不能
把一级创建 `finalScore` 误当成角色当前值。

投影不包含数据库 ID、`row_version`、host epoch、幂等记录、Session/CSRF、完整事件历史、
字段变化、升级/恢复/裁定历史或模组定义。历史表不是当前状态恢复的输入；未来领域若证明某段
历史本身影响当前规则，必须先增加稳定逻辑投影，不能导出数据库 ID 规避设计。

## 事件闭包

每个 `stateEvent` 只保存正数 `eventSequence`、封闭事件类型和 `subjectCharacterKey`。创建快照
引用 `LEVEL_ONE_CHARACTER_CREATED`；专长和多职业熟练只引用
`CHARACTER_LEVEL_ADVANCED`；子职业和特性可以引用创建或升级事件。所有序号必须不大于
`eventTail`，事件主体必须是同一文件中的角色，且每个事件必须至少被一个状态引用。文件不携带
未引用事件，也不为满足外键导出最近 50 条之外的完整历史。

`eventSequence` 是战役内稳定事件身份；数据库自增 ID 仅在导入事务中重建。完整 format 2 文件
后续还必须证明 `stateEvents` 与可展示 `recentEvents` 不重号，并以保存的 `eventTail` 防止导入后
复用旧序号。

## 严格 codec 与预览

内部 codec 显式写出字段，不使用反射。读取边界执行 16 MiB 上限、无 BOM UTF-8、RFC 8259
严格语法、最大深度/数组上限、重复键检测、精确字段白名单、整数词法、规范 UUID v4、稳定键、
SHA-256 形状、能力集合、资源范围、唯一关系及事件闭包校验。读取结果按稳定键规范排序，因此
输入数组顺序不改变再次写出的字节顺序。

DRAFT 预览无副作用，只返回 `eventTail`、各分区计数和原始载荷 SHA-256。它不把 DRAFT 模组视为
已发布，也不访问或修改战役状态。

## 恢复来源与事务

正常升级写入的专长和多职业熟练继续使用 `state_origin=ADVANCEMENT`，V017 触发器要求对应
V015 `character_advancement_choice_v2` 父记录存在。format 2 当前状态恢复使用
`state_origin=ARCHIVE_RESTORE`，触发器要求 `acquired_event_id` 指向同一战役、同一角色且类型为
`CHARACTER_LEVEL_ADVANCED` 的重建事件。两类状态均不可更新。

`JdbcCampaignArchiveV2CharacterStateImportRepository` 只在调用方事务中插入事件和当前状态，不得
commit 或 rollback。DRAFT 事务服务在借连接前完成第一次严格读取，在 `SERIALIZABLE` 事务内再次
读取并校验；任一 JDBC 或验证失败由事务所有者整体回滚。整战役替换清理顺序已覆盖 V012—V015
子表，避免旧 DRAFT 状态阻止角色根重建。

## 尚未激活的边界

当前角色片段尚未与 format 1 的战役、字段、物品、地图和最近事件投影组合成完整 format 2 文件；
也尚未接入 `CampaignArchiveFormatDispatcher`、公开存档 Service、Servlet/JSP 或 released 模组校验。
装备/冒险、战斗/状态、法术、怪物/魔法物品完成相同严格投影并通过全文件导出—读取—预览—
确认—数据库恢复往返前，format 2 必须继续失败关闭。
