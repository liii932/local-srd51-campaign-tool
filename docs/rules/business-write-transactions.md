# 共同业务写入

本合同连接既有业务事务与 [v2 运行设计](../design/v2/runtime-and-transactions.md)。
共同接纳不改变 RELEASED v1 的规则、事件、幂等摘要和存档编码，也不赋予不完整 schema 2
基础模组游戏执行资格。完整角色、业务历史及保存/结束生命周期仍按各领域采用条件接入。

## 唯一写入边界

`BusinessWriteTransactions` 拥有共同进程门、连接借还、永久数据库守卫和事务结果。
业务事务先取得进程门，再借连接、开始事务并以第一条 SQL 锁定 `business_write_guard`。
规则、目标、版本和幂等校验及随机性消费均在此边界内进行；不能只在 Servlet 设置门。
旧事务保留原隔离级别：角色聚合修改使用 `READ_COMMITTED`，其余入口使用 `SERIALIZABLE`；
新 schema 2 材料采用 `READ_COMMITTED`。

事务 owner 通过 `Session.connection()` 把受约束连接交给叶仓储，只有 Session 可以决定
提交、回滚及连接归还。叶仓储不能自行提交、回滚、关闭或另开写连接；写入方法拒绝未经
共同接纳的连接。只读预览、导出和 GET 不因读取数据而获得业务写入或修复资格。

实际事务入口如下；新增入口必须同时更新清单和行为测试：

| 事务 owner | 写入责任 |
|---|---|
| `JdbcCampaignCreationRepository.create` | legacy 战役创建 |
| `JdbcCharacterCreationRepository.create` | legacy 角色创建 |
| `JdbcCharacterCardMutationRepository.mutate` | 角色卡修改 |
| `JdbcCharacterLifecycleMutationRepository.mutate` | 角色生命周期命令 |
| `JdbcCharacterAggregateMutationRepository.mutate` | 角色聚合修改 |
| `JdbcLevelOneCharacterCreationRepository.confirm` | DRAFT 一级角色创建 |
| `JdbcLevelAdvancementRepository.confirm` | DRAFT 角色成长 |
| `CheckTransactionService.execute` | 检定、效果、版本及幂等结果 |
| `EncounterCommandTransactionService.initialize` | 带命令身份的遭遇初始化 |
| `EncounterStateTransactionService.initialize` | 内部遭遇初始化 |
| `EntityPositionTransactionService.position` | 实体位置修改 |
| `CampaignArchiveConfirmationService.confirm` | 完整 legacy 战役导入 |
| `CampaignArchiveV2DraftImportService.importState` | 内部 DRAFT 角色状态导入 |
| `Schema2SnapshotTransferService` | INTERNAL 快照的原子转存 |

旧 PARTITION 快照仓储也要求受共同守卫约束的连接；离线验收调用方使用 `openPartition`，
只能在没有活动旧战役和 schema 2 残留时写 PARTITION 材料，不获得业务执行资格。

活动旧战役阻止新 schema 2 初始化；内部快照、未决事务、运行残留和未完成清理不能当作
健康空闲。材料守卫仍核验其局部收据及计数，但它位于共同守卫之后，不能代替共同接纳。
相同命令重放沿用原业务的准确摘要和接受事实，不重复掷骰、授予或写历史。

## 事务结果与故障

| 观察到的结果 | 连接及后续行为 |
|---|---|
| 未调用 commit，rollback 明确成功 | 保留回滚结果，安全恢复并归还连接 |
| commit 明确成功 | 保留提交结果；之后归还连接失败也不改报回滚，不再次执行业务 |
| commit 响应不明或 rollback 失败 | 保持 UNKNOWN，阻断后续写入；隔离原连接，不恢复自动提交 |
| 无法确认连接已隔离 | 保持阻断，不把可疑连接作为健康连接归还 |

故障处理覆盖异常和严重错误。`close()`、普通查询无结果以及 commit 后再次 rollback
均不能单独证明未提交。查证必须绑定原操作，在原执行者已隔离后用新连接锁同一个永久
守卫，核验完整结果及相关材料；查证不补写、不重试命令、不自动发布或恢复执行视图。

## 后续初始化采用边界

初始语言/工具计划遵循 [schema 2 初始授予合同](schema-2-proficiency-grants.md)。
当前 `Schema2InitialCharacterPlanService` 从已存储的 INTERNAL 快照生成和核验只读初始计划，
不保存计划。将准确计划与快照、控制及操作收据同事务持久化和独立读回，仍须后续采用。

完整业务初始化当前不可用，即使运行库为空也不授予游戏执行资格。它还需要真实角色及全部
必需领域、完整业务/历史写入与恢复、容量及生命周期闭包；不能以空战役、空历史或只读计划
替代。新战役入口、完整文件和 archive format 2 的正式激活继续受采用与发布门约束。

## 验证与安装

数据库无关测试应覆盖所有真实事务入口和调用方叶仓储、第一条 SQL 的锁序、并发互斥、
旧活动战役及残留阻断、事务失败、明确提交后的连接故障、UNKNOWN 隔离和原操作查证。

前向迁移、最小授权和只读核验脚本必须分别审核。新增迁移进入候选 WAR 及完整 schema
诊断清单；文件存在、静态 SQL 测试和 JDBC 代理测试都不表示真实 MySQL 已迁移或已通过
锁、触发器、权限及连接池失效验收。数据库安装、授权、真实数据库验收和部署仍为独立
检查点，普通应用启动不执行这些操作。

`V025__business-write-guard.sql` 建立不可替换的单行守卫。
[守卫权限](../../database/grants/business-write-guard.sql)仅增加守卫及接纳状态读取；
只读核验见[守卫核验](../../database/verify/business-write-guard.sql)。
