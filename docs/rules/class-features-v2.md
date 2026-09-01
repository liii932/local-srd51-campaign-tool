# Canonical v2 职业、子职业与职业特性合同

本合同属于 `dnd5e2014_srd51_se` release `1` 当前 DRAFT、canonical format `2`、archive format `2`
设计。该发布版保持 `DRAFT`；目录、规则类、V014 或空运行时表的存在都不发布该身份，不改变
新战役默认，也不启用 archive format 2。DRAFT 设计可以通过后续前向迁移和协调实现继续演进。

## 冻结目录属性与状态

V014 为 236 个职业或子职业拥有的特性固定且只固定一种执行状态：

- `AUTOMATIC`：13 个仅由类型化资源容量与休息恢复表达的能力；算法为
  `AUTOMATIC_RESOURCE_LIFECYCLE_V1`；
- `DM_ADJUDICATION`：24 个子职业/特性选择点或确需 DM 判断的能力；算法为
  `BOUNDED_SUBCLASS_SELECTION_V1`、`BOUNDED_FEATURE_SELECTION_V1` 或
  `BOUNDED_DM_ADJUDICATION_V1`；
- `BLOCKED`：199 个依赖多职业/ASI/专长、法术、战斗或其他未交付系统的能力；算法为
  `BLOCKED_ISSUE_8_V1`、`BLOCKED_SPELL_SYSTEM_V1` 或
  `BLOCKED_DOWNSTREAM_SYSTEM_V1`。其中第一个名称是 V014 已持久化的算法身份，不表示发布计划继续绑定某个动态 Issue。

状态和算法分别存入 `feature.execution_mode` 与 `feature.execution_algorithm`。同一特性缺失、
重复、未知状态、状态/算法不匹配或错误归属时，canonical-v2 校验和职业特性规则都 fail closed。
`OPTION` 行属于覆盖矩阵，不会被当成角色自动获得的全部选项。

13 个自动特性是：野蛮人的 Rage；吟游诗人的 Bardic Inspiration、Font of Inspiration；
牧师的 Channel Divinity；德鲁伊的 Wild Shape；战士的 Second Wind、Action Surge、
Indomitable；武僧的 Ki；圣武士的 Divine Sense、Lay on Hands；游荡者的 Stroke of Luck；
术士的 Font of Magic。这里只自动处理对应资源的获得、上限和休息恢复，不宣称自动处理攻击、
伤害、目标、动作经济、法术或叙事效果。Superior Inspiration、Perfect Self、Cleansing Touch、
Sorcerous Restoration 等特殊触发未被休息引擎覆盖，因而保持明确阻断。

## 子职业与逐级矩阵

每个 SRD 职业有一个对应 SRD 子职业。`subclass.selection_level` 固定选择等级：Life Domain、
Draconic Bloodline、The Fiend 为 1；Circle of the Land、School of Evocation 为 2；其余为 3。
规则层从冻结父子关系、选择等级和 `feature.level` 生成逐级解锁；客户端不能提交派生等级、
执行状态或算法。

| 职业 | 子职业 | 自动 | DM 裁决 | 阻断 | 合计 |
|---|---|---:|---:|---:|---:|
| Barbarian | Path of the Berserker | 1 | 1 | 16 | 18 |
| Bard | College of Lore | 2 | 2 | 11 | 15 |
| Cleric | Life Domain | 1 | 2 | 9 | 12 |
| Druid | Circle of the Land | 1 | 1 | 12 | 14 |
| Fighter | Champion | 3 | 2 | 7 | 12 |
| Monk | Way of the Open Hand | 1 | 1 | 21 | 23 |
| Paladin | Oath of Devotion | 2 | 2 | 15 | 19 |
| Ranger | Hunter | 0 | 5 | 23 | 28 |
| Rogue | Thief | 1 | 2 | 15 | 18 |
| Sorcerer | Draconic Bloodline | 1 | 1 | 17 | 19 |
| Warlock | The Fiend | 0 | 4 | 43 | 47 |
| Wizard | School of Evocation | 0 | 1 | 10 | 11 |

参数化测试从不可变 V011 行独立重建该矩阵，检查 236 个键唯一、等级为 1—20 且三种状态之和
精确等于每个职业的目录总数。V014 的 schema 记录还要求全局计数精确为 13/24/199。

## 资源恢复与裁决边界

16 个职业资源具有 `resource.execution_mode` 和完整覆盖 1—20 的
`resource.recovery_profile`。Pact Magic、Mystic Arcanum 与 Arcane Recovery 的资源状态明确
阻断；若权威状态中出现这些资源，规则层以 `AUTHORITATIVE_STATE_MISMATCH` 拒绝。其余资源使用
`RESOURCE_CURRENT_SET_TO_MAXIMUM` 类型化效果；短休只恢复短休资源，长休同时恢复短休和长休资源。
吟游激励在 1—4 级为长休恢复，5—20 级为短休恢复。

DM 裁决只接受 `SUCCESS`、`FAILURE`、`NO_EFFECT`，并按冻结算法绑定到一个稳定裁决键。
自动能力不能改走 DM 裁决，阻断能力不能被裁决伪装成可执行。权威写入必须先证明角色已经获得
该特性，再在同一事务保存根事件、裁决行、字段/资源变化、row version、事件尾和幂等结果。

## V014 状态与发布门

V014 新增 `character_subclass_state_v2`、`character_feature_state_v2`、
`character_feature_choice_v2`、`character_feature_adjudication_v2` 和
`character_resource_recovery_v2`。迁移不创建角色、选择、事件或业务状态；只读核验脚本要求五表
为空。稳定事件名为 `CHARACTER_FEATURE_ADJUDICATED` 与 `CHARACTER_RESOURCES_RECOVERED`。

V014 本身不实现多职业、ASI、专长、法术状态或完整战斗，也不修改
`CHARACTER_LEVEL_ADVANCED`、`ADVANCE_CHARACTER_LEVEL` 或已有升级摘要域。V015 通过独立的
[多职业、ASI 与专长 DRAFT 合同](multiclass-asi-feats-v2.md)完成角色成长选择框架；既有算法标识
`BLOCKED_ISSUE_8_V1` 仍是 V014 的持久化身份，不因实现推进而重命名或重写。多职业施法及
Grappler 战斗效果仍阻断。角色领域完成后也不发布 canonical/archive format 2 或切换默认
发布版；这些动作推迟到主要规则领域完成整合后的跨领域发布门。
