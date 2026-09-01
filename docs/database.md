# 数据库

应用使用 MySQL 8.0、InnoDB、JDBC 与 Tomcat JNDI DataSource。数据库 schema 是权威持久化状态，应用不会从 Session、本地文件或 JVM 缓存重建业务数据。

当前实现仍把规则目录和运行状态放在 `dnd_tool_se` 单一 schema。目标设计允许在启动时从只读
规则存储加载并验证不可变目录快照；该快照不是战役业务状态的替代来源，角色、事件、版本和幂等
结果仍只以运行数据库为权威。物理拆分必须使用前向迁移并遵循
[静态规则目录与运行数据库分离设计](rule-database-separation.md)。

## 迁移合同

正式迁移位于 `src/main/resources/db/migration/`。V001—V017 均是不可变迁移历史；V011—V017
依次建立角色目录、一级创建、升级与生命骰、职业特性生命周期、多职业/ASI/专长及 format 2
角色状态来源的 DRAFT 前向状态：

- 不修改、合并、重命名或重跑已应用迁移；
- 新 schema 变更使用下一个编号的前向迁移；
- 迁移文件名、规范 SQL 载荷摘要与 `schema_meta` 必须一致；
- 仓库中的辅助 SQL 不是已执行证明。

只读验证脚本位于 [database/verify](../database/verify/)，最小授权脚本位于 [database/grants](../database/grants/)。移动和阅读这些文件不会执行 SQL。

## 账号职责

| 角色 | 允许职责 |
|---|---|
| migrator | 按审核顺序执行 DDL/迁移；必要时执行独立备份/恢复流程 |
| application | 可变业务表所需的最小 DML；不可变 `RELEASED` 模组目录只读 |
| read-only verifier | 最小 `SELECT`、`SHOW`、`DESCRIBE` 与普通 `EXPLAIN SELECT` |
| integration test | 仅在独立测试库中创建临时表并写入测试数据 |

生产库与集成测试库必须分离。测试保护会拒绝把生产数据库 `dnd_tool_se` 当作可写测试目标。

## 稳定合同

`dnd5e2014_srd51_se_v1`、发布版本、规范哈希算法、字段键、算法键、事件类型、摘要域和存档稳定键属于持久化或协议合同。源码类名、文档路径和辅助脚本名的整理不得改变这些值。完整 SRD 5.1 目标不能通过扩写、重算或重新解释该发布版实现。

冻结规则详见 [rules/srd-5.1.md](rules/srd-5.1.md)。

## 完整规则版的演进边界

完整规则版需要新的规则定义表或关系、运行状态和存档字段时：

- V011 建立 canonical-v2 类型化目录表并插入完整角色 DRAFT；V012 追加一级创建 profile、
  空创建快照/选择/资源表；V013 追加逐级 profile、生命骰、职业等级和升级快照表；V014
  追加职业特性执行矩阵、子职业选择等级、固定执行/恢复 profile 和五个空生命周期表；V015
  增加多职业/ASI/专长目录 profile、扩展升级约束及四个事件关联状态表；V016 追加可重建的
  一级职业武器、护甲、盾牌与工具熟练基线；V017 为专长和多职业熟练增加 `ADVANCEMENT` 与
  `ARCHIVE_RESTORE` 显式来源，并用触发器分别验证正常升级父记录或重建事件。后续使用下一个
  可用编号，不修改 V001—V017；
- 先批准新的发布身份、稳定键、封闭算法和规范逻辑投影，再插入 `DRAFT` 数据；
- 使用新的规范格式表达新增分区，不能向 `canonical_format_version=1` 的 23 个分区隐式追加字段；
- DRAFT 定义和运行模型可通过后续前向迁移及协调的代码、测试和文档继续演进；迁移文件不可变不代表未发布业务语义需要兼容，除非存在明确要求保留的真实数据；
- 只有角色、装备/冒险、战斗/状态、法术和怪物/魔法物品完成跨领域整合，并通过独立字节向量、内容摘要、archive 严格往返和不可变保护验证后，才能转为 `RELEASED`；
- 旧战役继续冻结到原发布版，不能因应用升级自动改变规则解释；
- 新的存档格式必须保留对版本 1 的明确识别，升级或导入失败不得留下部分状态；archive format 2 可在 DRAFT 中渐进开发，但在统一发布门前不得激活。

完整规则族映射为 `dnd5e2014_srd51_se`、release 1、`canonical_format_version=2`、archive
format 2。V011 文件包含该身份和角色目录的 `DRAFT` 种子，但仓库文件不是数据库执行证明，
该身份仍不允许应用绑定或导入。规范投影和稳定键见
[角色目录与 canonical format 2](rules/character-catalog-v2.md)，目标能力和完成标准见
[完整 SRD 5.1 覆盖目标](rules/srd-5.1-complete.md)。

V012 的 `character_creation_snapshot_v2`、`character_creation_selection_v2` 和
`character_resource_state_v2` 是空权威运行时表。迁移本身不创建业务行。只读核验脚本是
`database/verify/v012-level-one-character-creation.sql`；应用账户增量授权模板是
`database/grants/level-one-character-runtime.sql`。迁移、核验和授权仍是三个独立检查点。

V013 的 `character_class_level_v2`、`character_level_advancement_v2` 和
`character_level_resource_change_v2` 保存职业等级、不可变升级结果及资源变化，并为
`character_resource_state_v2` 增加显式无限资源状态。只读核验脚本是
`database/verify/v013-level-advancement-hit-dice.sql`；应用账户所需的最小增量仍集中在
`database/grants/level-one-character-runtime.sql`。这些文件存在不表示已执行迁移、核验或授权。

V014 的五个 canonical-v2 生命周期表保存子职业、特性、选择、DM 裁决和资源恢复审计边界；
V015 的 `character_advancement_choice_v2`、`character_ability_score_change_v2`、
`character_feat_state_v2` 与 `character_multiclass_proficiency_v2` 保存多职业、ASI、专长和熟练增量；
迁移本身保持这些表为空，并保持完整规则版为 `DRAFT`。V017 不激活 format 2，只建立当前状态
恢复所需的显式来源；核验脚本分别为 `database/verify/v014-class-feature-lifecycle.sql` 与
`database/verify/v017-character-archive-v2-origin.sql`。稳定属性、矩阵计数与阻断边界见
[职业、子职业与职业特性合同](rules/class-features-v2.md)。
