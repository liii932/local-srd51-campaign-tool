# 数据库

应用使用 MySQL 8.0、InnoDB、JDBC 与 Tomcat JNDI DataSource。数据库 schema 是权威持久化状态，应用不会从 Session、本地文件或 JVM 缓存重建业务数据。

当前实现仍把规则目录和运行状态放在 `dnd_tool_se` 单一 schema。目标设计允许在启动时从只读
规则存储加载并验证不可变目录快照；该快照不是战役业务状态的替代来源，角色、事件、版本和幂等
结果仍只以运行数据库为权威。物理拆分必须使用前向迁移并遵循
[静态规则目录与运行数据库分离设计](rule-database-separation.md)。

## 迁移合同

正式迁移位于 `src/main/resources/db/migration/`。V001—V018 均是不可变迁移历史；V011—V018
依次建立角色目录、一级创建、升级与生命骰、职业特性生命周期、多职业/ASI/专长、format 2
角色状态来源及多职业共享法术位计算目录基础的 DRAFT 前向状态：

- 不修改、合并、重命名或重跑已应用迁移；
- 新 schema 变更使用下一个编号的前向迁移；
- 迁移文件名、规范 SQL 载荷摘要与 `schema_meta` 必须一致；
- 仓库中的辅助 SQL 不是已执行证明。

只读验证脚本位于 [database/verify](../database/verify/)，最小授权脚本位于 [database/grants](../database/grants/)。移动和阅读这些文件不会执行 SQL。

## 自动化数据库拓扑与账号职责

当前本机运行实例只承载部署运行 schema，以及明确创建时的临时表集成测试 schema。完整迁移链
不再通过同一实例内改名 schema 来模拟，而是在物理隔离、可销毁的 MySQL 实例或容器内使用生产
原名 `dnd_tool_se` 重放。名称和权限是职责合同，不表示数据库、账号、迁移或授权已经实际创建或
执行：

| 边界 | 用途 | Schema | MySQL 用户 | 允许职责 |
|---|---|---|---|---|
| 本机运行实例 | 本地部署运行 | `dnd_tool_se` | `dnd_tool_se_app` | 仅已审核的逐表 `SELECT`/最小 DML；无 DDL、账号管理或 `GRANT OPTION` |
| 本机运行实例 | 运行库和 legacy 规则目录只读核验 | `dnd_tool_se` | `dnd_tool_se_agent` | schema 只读 `SELECT` 与自身授权检查；无任何写权限 |
| 本机运行实例 | 正式前向迁移和备份 | `dnd_tool_se` | `dnd_tool_se_migrator` | 仅该 schema 的迁移所需 DDL/DML、索引、外键和触发器权限，以及审核过的备份读取权限；无账号管理、全局权限或 `GRANT OPTION` |
| 本机实例（独立 schema） | JDBC 事务集成测试 | `dnd_tool_se_it` | `dnd_tool_se_it` | `SELECT`、`INSERT`、`UPDATE`、`CREATE TEMPORARY TABLES`，仅操作测试连接的临时表 |
| 独立 disposable 实例 | V001—V018 原样重放 | `dnd_tool_se` | 生命周期内的迁移身份 | 仅该隔离实例中迁移所需 DDL/DML、索引、外键和触发器权限 |
| 独立 disposable 实例 | 重放结果只读核验 | `dnd_tool_se` | `dnd_tool_se_validation_ro` | 仅 `SELECT`，随隔离实例销毁，不与运行库核验身份混用 |

运行、正式迁移和运行库核验连接固定从 loopback TCP 使用 `@127.0.0.1`，不创建 `%` 或 LAN 来源
账号。集成测试引导可同时创建 `@127.0.0.1` 与 `@localhost`，以兼容本机测试客户端的连接语义，
但权限只限 `dnd_tool_se_it`。迁移重放实例不挂载持久 volume，默认不发布网络端口；只有执行只读
JDBC 比较时才向 `127.0.0.1` 发布随机端口，并在验证完成或失败后销毁整个实例及其账号。MySQL 会
按 `TRIGGER` 权限过滤触发器元数据，因此普通只读账号不负责完整触发器定义验收；需要该元数据的
审核查询在迁移停止后由隔离实例内的迁移身份执行，且只能运行 `SELECT`/`SHOW`。

账号管理员只负责一次性创建/轮换运行实例账号及应用审核过的授权，不能作为 Agent、Tomcat、测试
或日常迁移连接。Agent 的常规运行库身份是 `dnd_tool_se_agent`；只有明确批准的迁移检查点才能向
`dnd_tool_se_migrator` 提供当前维护进程可用的凭据。Tomcat 始终使用
`dnd_tool_se_app`。密码只通过当前进程环境或经批准的外部秘密注入，不进入 Git、WAR、命令行、
日志或报告；自动化不得打印完整环境。

`database/test/create-integration-database.sql` 只引导 `dnd_tool_se_it` 及其临时表测试账号。
迁移重放不使用仓库内管理员 SQL 创建同实例替代 schema；它必须在经授权的外部检查点内创建全新
隔离实例，直接执行仓库原始迁移，并在结束时销毁。运行账号的逐表权限继续由
`database/grants/` 中的审核脚本累加；这些脚本不创建账号，也不授予全局权限。

## 现有运行库的阶段性使用

当前没有真实业务使用且只含少量测试数据时，采纳 `dnd_tool_se` 作为本地开发部署和业务验收的
运行库。它可以在完成只读盘点、停服、完整备份、迁移演练和明确授权后接收尚未安装的 V001—V018
前向迁移。已有测试数据即使可丢弃，也不能被自动清空、覆盖或当作已经备份。

`dnd_tool_se` 不得作为 `MySqlIntegrationIT` 的可写目标，测试保护会按数据库名拒绝它；事务、
回滚和故障注入测试只使用 `dnd_tool_se_it`。完整迁移链的反复安装或重建只在物理隔离的 disposable
MySQL 实例中使用原名 `dnd_tool_se`，不能靠重跑运行库迁移验证，也不能生成改名 SQL 副本。运行库
只读目录/Harness 比较可以使用 `dnd_tool_se_agent`，部署后的真实业务写入验收仍需单独授权和恢复
边界。

一旦运行库保存需要保留的战役数据，就不再视为 disposable：后续迁移和部署必须保留完整备份、
回滚候选及停服维护边界，不能因它曾用于开发而降低保护等级。

## Agent 自动化检查点

Agent 可以在既有授权范围内自动执行数据库无关测试、对 `dnd_tool_se_it` 的显式集成测试，以及
在已单独授权后创建并销毁物理隔离实例来原样重放迁移、用隔离实例内的只读账号验证。以下仍是彼此
独立的外部检查点：创建/删除 schema、实例或账号，改变授权，备份或恢复运行库，向运行实例的
`dnd_tool_se` 应用迁移，启动或停止 Tomcat，替换活动 WAR 以及真实业务写入。一次授权不能被推断
为后续所有检查点的永久授权。

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
  `ARCHIVE_RESTORE` 显式来源，并用触发器分别验证正常升级父记录或重建事件；V018 为十二职业
  增加多职业施法贡献属性，不建立法术运行状态。后续使用下一个可用编号，不修改 V001—V018；
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
恢复所需的显式来源；V018 只增加共享法术位计算所需的冻结职业贡献，核验脚本为
`database/verify/v018-multiclass-spell-slot-foundation.sql`，不表示迁移已执行。相关稳定边界见
[职业、子职业与职业特性合同](rules/class-features-v2.md)与
[多职业共享法术位合同](rules/multiclass-spell-slots-v2.md)。
