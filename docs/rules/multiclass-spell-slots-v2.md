# Canonical v2 多职业共享法术位 DRAFT 基础

本页记录历史 DRAFT 目录及其规则语义。当前 SQLite 实现见[领域接合表](../design/v2/domain-integration.md)。历史读写和校验使用[匹配版本](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/multiclass-spell-slots-v2.md)。

本合同属于 `dnd5e2014_srd51_se` release `1` 当前 DRAFT、canonical format `2`、archive
format `2` 设计。V018 冻结旧 MySQL DRAFT 目录，archive format 2 限内部角色片段。

## 冻结职业贡献

V018 为全部十二职业增加唯一的
`class.multiclass_spellcasting_progression` 标识：

| 贡献类型 | 职业 |
|---|---|
| `FULL` | bard、cleric、druid、sorcerer、wizard |
| `HALF_DOWN` | paladin、ranger |
| `PACT_MAGIC` | warlock |
| `NONE` | barbarian、fighter、monk、rogue |

当前 DRAFT 只收录 SRD 的 Champion 与 Thief，不存在 Eldritch Knight 或 Arcane Trickster，
因此没有三分之一施法者贡献。后续若目录范围变化，必须以前向迁移和协调的规范校验明确加入，
不能从客户端或名称猜测。

## 明确未完成的边界

本切片不建立法术目录、已知/准备法术、法术书、当前法术位状态、Pact Magic 位阶、Mystic
Arcanum、施法动作、升环、专注、目标、效果或资源消耗。它也不接入升级事务、Host API、正式
存档 dispatcher 或 archive format 2 当前状态投影。

V015 的 `BLOCKED_PENDING_SPELL_SYSTEM`、V014 的 `BLOCKED_SPELL_SYSTEM_V1` 及现有法术
资源阻断继续适用。共享法术位与 Pact Magic 的采用目标见[施法扩展合同](../design/v2/spellcasting-extension-contract.md)。
