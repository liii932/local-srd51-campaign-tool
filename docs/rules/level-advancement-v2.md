# Canonical v2 升级与生命骰合同

本合同属于 `dnd5e2014_srd51_se` release `1`、canonical format `2`、archive format `2`。
该发布版仍为 `DRAFT`；V013—V017 的代码、目录或表存在不代表数据库已经迁移，也不允许创建战役、
执行规则或导入格式 2 存档。正式发布及 archive format 2 激活属于主要规则领域整合后的跨领域发布门。

## 冻结身份

- Host 路由：`POST /api/host/characters/level-up`；
- HP 选择：`FIXED_AVERAGE`、`SERVER_ROLL`；
- HP 算法：`HIT_DIE_OR_FIXED_PLUS_CONSTITUTION_MINIMUM_ONE_V1`；
- 随机算法：`SERVER_UNIFORM_HIT_DIE_V1`；
- 预览摘要域：`DND_TOOL_SE_LEVEL_ADVANCEMENT_PREVIEW_V1`；
- 确认摘要域：`DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V1`；
- 事件类型：`CHARACTER_LEVEL_ADVANCED`；
- 幂等操作类型：`ADVANCE_CHARACTER_LEVEL`；
- 生命骰资源：`resource.hit_dice.d6`、`resource.hit_dice.d8`、
  `resource.hit_dice.d10`、`resource.hit_dice.d12`；
- 目录属性：`class.proficiency_bonus_profile` 与 `resource.maximum_profile`。

字段变化使用 `character.level.total`、`character.class.level`、
`character.proficiency_bonus`、`advancement.hp_choice_algorithm`、
`advancement.hit_die_sides`、`advancement.hit_die_roll`、
`advancement.hit_point_increase`，以及每个稳定资源键的 `.current`、`.maximum`、
`.unlimited` 后缀。客户端不能指定骰点、HP 增量、熟练加值、资源上限或这些字段键。

## `advancement-value-profile-v1`

逐级 profile 是 ASCII、区分大小写的规范文本。逗号分隔严格递增且连续的等级区间；单级写作
`level:value`，多级写作 `minimum-maximum:value`。最后区间必须结束于 20；第一个区间之前表示
资源尚未获得。值只能是正整数或以下冻结表达式：

- `CLASS_LEVEL`；
- `FIVE_TIMES_CLASS_LEVEL`；
- `CHARISMA_MODIFIER_MINIMUM_ONE`；
- `ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE`；
- `UNLIMITED`。

职业熟练加值 profile 只能使用常量，并必须精确表示总等级 1–4 为 +2、5–8 为 +3、9–12
为 +4、13–16 为 +5、17–20 为 +6。未知表达式、重叠、空洞、乱序、超范围等级、重复属性或
错误类型全部 fail closed。

## 预览、确认和资源语义

V013 基线只允许已有单职业角色为该职业增加一级。V015 在不修改 V013 历史身份的前提下增加
[多职业、ASI 与专长 DRAFT 合同](multiclass-asi-feats-v2.md)：总等级仍恰好 `+1` 且不超过 20，
目标职业等级独立 `+1`，并由扩展摘要绑定多职业、子职业、职业特性、ASI 或专长选择。一级创建与
后续升级均通过同一 `ClassFeatureRules` 矩阵获得到期子职业及非 OPTION 特性；选择、特性状态、
根事件和审计记录原子提交。多职业施法合并继续阻断。

预览读取冻结目录和权威角色快照，计算熟练加值、生命骰增量、职业资源变化，以及 HP 固定值或
掷骰上下界。预览不写数据也不消耗随机性。固定 HP 为生命骰平均值向上取整加体质修正；服务端
掷骰为均匀 `1..hitDieSides` 加体质修正；两者最低均为 1。

有限资源扩容时保留已消耗量：`newCurrent = max(0, newMaximum - spent)`。新获得的资源以满值
开始。`UNLIMITED` 使用独立布尔状态，并以 current/maximum 均为 0 的规范存储表示，不能伪装
成任意大整数。升级增加一个对应面数的可用和最大生命骰；生命骰的休息消耗与恢复由独立任务实现。

确认事务依次锁定幂等键、角色与战役、冻结绑定、创建快照、职业等级、属性、专长、子职业、
已获得特性、熟练基线和完整资源目标集，复核
事件尾、row version、体质/魅力、目录身份及预览摘要后，才允许调用服务端随机源。职业等级、
资源、角色版本、事件尾、根事件、不可变升级快照、资源变化、字段变化和幂等结果必须共同提交或
整体回滚。相同 request id 只有在摘要及操作类型一致时回放首次保存的骰点、HP 增量和版本。

V013—V017 为未来 archive format 2 保存不依赖数据库 ID 的稳定状态。当前 DRAFT 角色投影使用
`eventSequence` 与 `eventTail` 建立当前状态所需的最小事件闭包，并具有严格 codec、预览、调用方
事务与 JDBC 恢复边界；format 1 的语义不变。该投影尚未组合进完整战役文件、released dispatcher、
Host 确认链或所有规则领域，因此跨领域发布候选前不激活 format 2，也不据此开放完整规则版。
