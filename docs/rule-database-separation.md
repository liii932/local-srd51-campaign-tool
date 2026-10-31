# 静态规则目录与运行数据库分离

本文说明当前存储与私有运行库替代边界。新 v2 的权威目标分别位于
[规则存储与版本](design/v2/rule-storage-and-versioning.md)、[运行与事务](design/v2/runtime-and-transactions.md)
及[采用边界](design/v2/adoption-boundaries.md)，这里不维护另一套启动或快照方案。

## 当前边界

当前 Host 仍通过 `java:comp/env/jdbc/DndToolSE` 使用 `dnd_tool_se`。`module_release`、
`module_*`、`module_catalog_*_v2` 与战役、角色、事件、幂等表共处该 schema，运行表仍有
本地规则外键及业务流程中的目录读取。不能只更换 Servlet loader 就宣称已完成拆分。

独立规则来源的语言与工具熟练目录表、安装事实及读取已见[来源 schema](rule-source-schema.md)、
[离线安装](rules/offline-language-installation.md)和[来源读取](rules/source-language-reading.md)。
来源 V001/V002 保存语言与工具目录；运行 V019 提供[语言快照分区](rules/runtime-language-snapshot.md)，
V020 增加[工具镜像并支持双分区读写](rules/tool-catalog-partition.md)。这些是分区实现，不代表
第二 JNDI、完整准备/执行上下文、战役快照绑定或部署已完成。

来源 V003 增加 [schema 2 制品与接受账本](rules/schema-2-source-ledger.md)，以独立离线流程保存
准确原包和 DM 明确接受事实，安装与接受共用维护屏障但分别登记。它不改写已有语言/工具表，
完整基础和尚未集成的机制仍由资格门拒绝。

[schema 2 内部转存链路](rules/schema-2-runtime-snapshot.md)读取来源 V003 的准确接受事实，
关闭来源事务后，在运行 V021/V023 中原子保存类型化组合、最终规则、完整覆盖链与操作收据。
实际回读及后续查证只使用运行库。当前全部写入口已接入[共同业务守卫](rules/business-write-transactions.md)，
但该有限实现尚未接入 Host 完整初始化、完整工作集或第二 JNDI，不授予完整运行资格。

## 选定目标与当前接入

| 边界 | 选定职责 | 当前差异 |
|---|---|---|
| 规则来源 MySQL | 准确原始 ZIP、安装/接受事实及独立迁移账本；类型化索引可重建 | 已有逐域来源表仍按当前合同严格核验；简化来源写入/读取尚未实施 |
| 每次打开的私有 SQLite | 完整最终规则/覆盖链、全部业务/历史/幂等和生命周期控制，同一事务 | 当前仍为共享 MySQL 运行库，现有 SQL/触发器/FOR UPDATE 不能直接搬用 |
| 逻辑 JSON 战役文件 | 保存点的全部业务投影及准确规则绑定 | 当前 format 1/内部 format 2 片段未采用完整闭包；不再设计 DB 档案 blob 双份存储 |
| 应用内存 | 受控准备和实际读回后的执行视图，可重建 | 无权补缺失事实，执行资格仍受生命周期与提交证据限制 |

拓扑、结构依赖与应用固定 SQL 建表的唯一目标合同在[规则存储](design/v2/rule-storage-and-versioning.md)。
本页不另外定义 JSON 表结构框架、JNDI 新名称或第二套生命周期。当前 `jdbc/DndToolSE`、
环境变量、账号和 WAR 外配置保持现行合同；具体替换接入及验证列入采用计划，不能只换驱动。

## 引用、加载与释放

- 来源 ID 不传播到业务或文件；整个战役共用新 run/snapshot 与稳定规则身份，不保留单角色
  prepared run 作为目标结构。来源连接完整读取/验证后关闭，再进入私有运行事务，无跨库提交。
- 规则镜像与完整链是本次固定材料；普通命令和保存不访问来源。当前 RELEASED/DRAFT 与
  INTERNAL 资格继续按实际门核验，来源接受不能补全机制或业务恢复能力。
- 文件直接恢复重建本地身份与规则材料，不重放历史；本地同版残留则经独占查证/全量校验
  后显式恢复原运行与新安全代次，来源离线不破坏已经固定的完整材料。
- 保存按[文件合同](design/v2/save-and-recovery.md)分开文件发布点和工作库接受点；END 封存后
  关闭/隔离整个运行目录再清理，不再把跨域 SQL DELETE 图作为目标释放机制。

## 迁移、验证与回退边界

当前 MySQL 两条迁移链各有编号、批准和诊断，见[数据库](database.md)。保留的库不改写已应用
文件或账本。新的私有 SQLite 使用应用当前存储模块与独立结构验证，不沿用旧 MySQL 编号，
不为旧应用存档建立迁移链；规则源账本演进仍是独立维护操作。

替代须同时完成事务/JDBC适配、类型/约束/排序、全域根与历史重映射、来源只读接合、文件
发布/清理、配置诊断和 Host 入口。保留当前运行期间不得混用两套写入口；切换要明确真实
数据处置，不能为通过检查自动删除旧库。目标 SQLite 的真实故障证据不能由 MySQL/代理
测试替代，设计任务也不授权迁移、数据重建、权限调整或部署。

本版只支持准确声明的模型/规则/文件；应用回退遇到不支持材料时保留并拒绝，不修改账本
伪装兼容。采用顺序及验收统一见[采用边界](design/v2/adoption-boundaries.md)，现行备份与
部署仍执行[备份恢复](backup-and-restore.md)和[部署指南](deployment.md)的独立检查点。
