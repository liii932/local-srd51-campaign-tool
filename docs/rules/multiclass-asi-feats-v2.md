# Canonical v2 多职业、ASI 与专长 DRAFT 合同

本合同属于 `dnd5e2014_srd51_se` release `1` 当前 DRAFT、canonical format `2`、archive format `2`
设计。V015—V018 不发布该身份、不激活 archive format 2，也不改变新战役默认发布版。

## 目录与选择

V015 为十二职业增加三个类型化目录属性：

- `class.multiclass_prerequisite`：进入新职业时必须同时满足当前每个职业与目标职业的 SRD
  属性先决条件；战士的力量或敏捷使用显式析取，其余复合条件使用显式合取；
- `class.multiclass_proficiency_profile`：固定熟练增量和有界技能/工具候选；客户端只能选择
  服务端冻结候选，已有熟练不会重复写入；V016 的
  `class.starting_proficiency_profile` 与一级创建的技能/工具选择共同形成可重建的初始熟练基线；
- `class.asi_levels`：按目标职业等级冻结 ASI 可用等级，包含战士和游荡者的额外等级。

ASI 必须选择一个属性 +2 或两个不同属性各 +1，最终属性不得超过 20；非 ASI 等级不得提交
ASI 或专长。专长使用可扩展的 `character.feat` 定义、类型化先决条件与执行状态。本切片只内置
SRD `feat.grappler`，要求力量 13；同一角色不能重复获得同一专长。Grappler 的获得可保存，依赖
战斗系统的实际效果仍为 `BLOCKED_DOWNSTREAM_SYSTEM_V1`。

## 多职业升级与阻断边界

总等级决定熟练加值；目标职业等级决定该职业的资源、特性、ASI 与生命骰。相同面数的生命骰按
所有职业等级合并，不同面数分别保存。目录、职业等级、属性、已有专长、已有熟练和完整资源集合
必须在确认事务内重新锁定和比较。到期子职业选择及非 OPTION 特性也由相同的冻结矩阵计算，
与升级事件共同提交。

多职业施法位、Pact Magic 组合及其他法术状态明确保存为
`BLOCKED_PENDING_SPELL_SYSTEM`；V015 不推断或伪造施法资源合并。V018 只固定职业贡献并提供共享
法术位上限计算基础，尚未建立权威法术位状态或接入升级事务，因此不替换该持久化阻断状态。待法术
状态与事务闭环完成后，必须通过新的前向迁移和协调规则替换该 DRAFT 阻断状态。

## 摘要、状态与事务

扩展升级使用 `DND_TOOL_SE_LEVEL_ADVANCEMENT_PREVIEW_V2` 与
`DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V2`。规范摘要按稳定键排序职业等级、ASI、专长、熟练和
资源变化，因此客户端选择顺序不影响结果。

`character_advancement_choice_v2`、`character_ability_score_change_v2`、
`character_feat_state_v2` 与 `character_multiclass_proficiency_v2` 保存事件关联状态。职业等级、
属性、专长、熟练、HP、生命骰、职业资源、根事件、字段变化、row version、事件尾和幂等结果
共同提交或整体回滚。体质 ASI 同时按新修正值增加本级 HP，并为既有总等级补足每级差值。

archive format 2 的运行时投影仍未激活。当前 DRAFT 角色切片以 `eventSequence`/`eventTail` 表达
创建、升级来源的最小事件闭包，并规范投影创建快照/选择、资源、职业等级、子职业、特性/选择、
专长和多职业熟练。V017 为专长与多职业熟练增加 `ADVANCEMENT`/`ARCHIVE_RESTORE` 显式来源：
正常升级仍必须存在 V015 父选择，恢复写入必须指向同角色的重建升级事件。内部 codec、预览和
调用方事务测试不接入 released dispatcher、Host 路由或完整战役确认链；后续领域与全文件往返
完成后，最终仍只由跨领域发布门统一冻结和激活。
