# Canonical v2 一级角色创建合同

本合同属于 `dnd5e2014_srd51_se` release `1`、canonical format `2`、archive format `2`。
该发布版仍为 `DRAFT`；实现存在不代表它可绑定战役、执行规则或导入存档。只有主要规则领域完成
整合后的跨领域发布门才能发布该身份并改变新战役默认值。

## 冻结身份

- 属性方法键：`ability.standard_array_v1`。
- 预览摘要域：`DND_TOOL_SE_LEVEL_ONE_PREVIEW_V1`。
- 确认请求摘要域：`DND_TOOL_SE_LEVEL_ONE_CONFIRM_V1`。
- 事件类型：`LEVEL_ONE_CHARACTER_CREATED`。
- 幂等操作类型：`CREATE_LEVEL_ONE_CHARACTER`。
- 核心资源键：`resource.hit_points`。
- 目录属性键：`creation.level_one_profile` 与 `creation.ability_method`。

`ability.standard_array_v1` 要求客户端把 `[15, 14, 13, 12, 10, 8]` 恰好各分配一次。客户端
不能提交最终属性、属性修正、HP、固定熟练或规范身份。

## `level-one-profile-v1` 编码

`creation.level_one_profile` 是 ASCII、区分大小写的规范文本。段由 `|` 分隔；段内使用
`name=value`。固定集合使用逗号分隔的严格升序本地键；选择集合使用 `count:key,...`。支持段为：

- `bonus=ability+value,...`：固定种族属性加值；
- `bonus_choice=count:ability,...`：必须互异的自由属性加值；
- `language`、`skill`、`tool`：固定集合或选择集合；
- `save=key,...`：固定豁免熟练；
- `start=count:stable-key,...`：起始选项；
- `subrace=required`：亚种必须存在且父种族必须匹配；
- `hp=6|8|10|12`：一级职业 HP 基数。

未知段、重复段、非规范顺序、重复候选、控制字符和不支持的数值全部 fail closed。V012 中的
profile 是 DRAFT 目录的一部分并进入 canonical-v2 内容摘要，不能在发布后原地修改。

## 预览和确认

预览只读取活动战役的冻结绑定、事件尾和不可变目录，不写角色、事件、资源或幂等行。服务器验证
全部键和集合，合并固定/选择熟练，应用种族修正，并从职业 HP 基数和最终体质修正派生最大 HP。
职业在一级到达子职业选择等级时必须同时提交服务器目录中的子职业键；不可提前或跨职业选择。

确认必须重新得到相同规范请求、目录摘要和事件尾。V013 以后，同一规则计算还初始化
`character_class_level_v2`、对应面数的一枚生命骰和一级已获得的职业资源。事务先锁定幂等键与
活动战役，再验证冻结绑定和预览事件尾，最后共同写入角色根、不可变创建快照、规范选择、职业
等级、HP/生命骰/职业资源、一级到期子职业与非 OPTION 职业特性、事件尾、创建事件、字段变化及
幂等结果。任一失败回滚全部写入。重复
request id 仅在摘要及操作类型完全一致时回放原结果。
