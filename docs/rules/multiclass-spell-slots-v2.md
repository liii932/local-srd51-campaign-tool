# Canonical v2 多职业共享法术位 DRAFT 基础

本合同属于 `dnd5e2014_srd51_se` release `1` 当前 DRAFT、canonical format `2`、archive
format `2` 设计。V018 不发布该身份、不激活 archive format 2，也不改变新战役默认发布版。

## 冻结职业贡献

V018 为全部十二职业增加唯一的
`class.multiclass_spellcasting_progression` 标识：

| 贡献类型 | 职业 | 共享施法者等级贡献 |
|---|---|---|
| `FULL` | bard、cleric、druid、sorcerer、wizard | 职业等级全额相加 |
| `HALF_DOWN` | paladin、ranger | 已在 2 级取得 Spellcasting 的职业等级先合计，再除以 2 并向下取整 |
| `PACT_MAGIC` | warlock | 不计入共享施法者等级；Pact Magic 保持独立 |
| `NONE` | barbarian、fighter、monk、rogue | 不贡献共享施法者等级 |

当前 DRAFT 只收录 SRD 的 Champion 与 Thief，不存在 Eldritch Knight 或 Arcane Trickster，
因此没有三分之一施法者贡献。后续若目录范围变化，必须以前向迁移和协调的规范校验明确加入，
不能从客户端或名称猜测。

## 服务端计算

`MulticlassSpellSlotRules` 只接受至少两个不同职业的权威等级集合。它在计算前拒绝重复职业、
1—20 之外的职业等级、总等级超过 20、缺失或重复的职业定义/贡献属性，以及未知贡献标识。

只有至少两个职业已经取得 Spellcasting 特性时才应用共享表；一级 paladin/ranger 不计为已取得
该特性。否则结果明确标记为不适用且不伪造单职业法术位。适用时，共享施法者等级按上述冻结贡献
计算，并映射到 SRD 5.1 Multiclass Spellcaster 表，产生当前有效的 1—9 环法术位上限。Warlock
只返回存在 Pact Magic 的独立标志，不推断其法术位等级、当前可用次数或恢复结果。客户端不能
提交贡献类型、有效施法者等级或法术位上限。

## 明确未完成的边界

本切片不建立法术目录、已知/准备法术、法术书、当前法术位状态、Pact Magic 位阶、Mystic
Arcanum、施法动作、升环、专注、目标、效果或资源消耗。它也不接入升级事务、Host API、正式
存档 dispatcher 或 archive format 2 当前状态投影。

因此 V015 的 `BLOCKED_PENDING_SPELL_SYSTEM`、V014 的 `BLOCKED_SPELL_SYSTEM_V1` 以及现有法术
资源阻断保持不变。后续切片必须通过新的前向迁移建立权威法术状态，并在完整请求、冻结目录、
职业等级、资源目标集、事件尾和 row version 全部锁定重验后，才可协调替换持久化阻断状态。
共享法术位、Pact Magic、事件、字段/资源变化、幂等结果和存档状态必须原子提交或整体回滚。
