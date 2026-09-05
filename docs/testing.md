# 测试指南

测试分为聚焦单元/边界测试、完整 Maven 回归、显式 MySQL 集成测试、WAR 审计和必要的手工 HTTP/UI 验收。仓库文档记录可重复方法；某次运行的动态计数和哈希应留在 CI 或发布记录中。

## 1. 聚焦测试

修改生产代码前后先运行最相关的测试类，例如：

```bash
mvn '-Dtest=CheckTransactionServiceTest,JdbcCheckExecutionRepositoryTest' test
```

根据变更覆盖正常行为、边界值、Unicode 码点/NFC、非法冻结数据、旧版本、幂等冲突、事务失败与禁止部分写入。重命名批次还应覆盖受影响的 Servlet 注册、反射字符串、页面资源和脚本引用。

## 2. 完整验证

代码、资源、迁移、构建或公开文档整理完成后运行：

```bash
mvn clean verify
```

报告必须来自完整结束的进程，并包含退出码、测试总数、失败、错误、跳过和最终 `BUILD SUCCESS`/失败。不要从截断或仍在运行的输出宣称成功。

普通 `clean verify` 不应写入业务数据库。数据库相关单元测试使用 mock、临时结构或静态 SQL 检查。

## 3. MySQL 集成测试

`MySqlIntegrationIT` 默认不参加普通构建。它只允许连接独立、一次性的可写测试库，并在连接中创建临时表，用于验证：

- JDBC 提交、回滚、`row_version` 与幂等重放；
- 调用方拥有的 `SERIALIZABLE` 事务；
- 整战役导入失败时无半成品；
- Tomcat DBCP 连接借出/归还及连接状态恢复；
- 权威检定、骰子、效果、事件、字段变化、版本和幂等结果的原子提交/回滚。

运行临时表套件时，可在数据库管理工具中审核并执行
[database/test/create-integration-database.sql](../database/test/create-integration-database.sql)，建立专用
`dnd_tool_se_it` schema 和临时表测试账号。只在未保存的编辑器缓冲区或仓库外临时副本替换密码
占位符；该引导脚本不执行 V001—V018 应用迁移。完整迁移链验证不复用这个 schema 或账号，必须
使用下面定义的物理隔离实例。

Linux/WSL Agent 使用已验证的原生 Maven，并在同一 Bash 会话中安全读取测试口令：

```bash
(
  read -r -s -p 'MySQL integration password: ' DND_MYSQL_INTEGRATION_PASSWORD; printf '\n'
  export DND_MYSQL_INTEGRATION_PASSWORD
  mvn \
    '-Ddnd.mysql.integration=true' \
    '-Ddnd.mysql.integration.confirmWritable=true' \
    '-Ddnd.mysql.integration.url=jdbc:mysql://127.0.0.1:3306/dnd_tool_se_it?connectionTimeZone=UTC' \
    '-Ddnd.mysql.integration.user=dnd_tool_se_it' \
    '-Dtest=MySqlIntegrationIT' test
)
```

Windows 专属操作可在同一安全边界内使用等价 PowerShell 环境变量语法。测试代码必须再次拒绝
运行库名 `dnd_tool_se`；不得修改该保护来复用少量测试数据。全部集成测试被跳过不构成验收；
不要打印完整环境，也不要把测试口令或完整业务数据写入日志。

### 3.1 自动化数据库验证顺序

1. 普通 `mvn clean verify` 不连接任何业务数据库。
2. `MySqlIntegrationIT` 只连接 `dnd_tool_se_it`，由 `dnd_tool_se_it` 用户创建连接级临时表；测试结束后不得留下持久业务表或数据。
3. V001—V018 完整迁移链只在物理隔离且可销毁的 MySQL 实例或容器中演练；该实例内部创建原名 `dnd_tool_se`，不与运行实例共享 schema、账号或持久 volume。
4. 自动化直接按生产清单读取仓库中的原始迁移文件，不生成 SQL 副本、不改写 `USE`、不重算摘要。每个迁移使用新的客户端连接并显式把默认数据库设为 `dnd_tool_se`；这是 V007 本身没有 `USE` 时仍能正确定位的必要条件。
5. 迁移完成后，使用该隔离实例内的 `dnd_tool_se_validation_ro` 核对完整 `schema_meta`、可见结构和目录计数。MySQL 按 `TRIGGER` 权限过滤的触发器定义由隔离实例内的迁移身份在停止迁移写入后核对，但验证步骤只能执行审核过的 `SELECT`/`SHOW`。
6. 运行实例的 `dnd_tool_se` 只允许 `dnd_tool_se_agent` 做只读盘点/Harness 比较。向它应用迁移、授权或测试写入不是测试套件的一部分，必须转入独立部署检查点。

账号/schema 合同及现有运行库的阶段性使用决策见[数据库说明](database.md)。

## 4. 迁移与规则测试

- V001—V018 是不可修改的迁移历史；V011 的角色 DRAFT 目录、V012 的一级创建 schema/profile、
  V013 的升级/生命骰 schema/profile、V014 的职业特性/恢复矩阵、V015 的多职业/ASI/专长框架、
  V016 的初始熟练基线、V017 的 format 2 状态来源及 V018 的多职业施法贡献由清单摘要、精确计数、
  关系、profile 语法和 canonical-v2 负例测试保护。
- `ImmutableLegacyMigrationContractTest` 固定 V001—V010 历史；
  `DeferredPublicSurfaceExclusionTest` 只保护永久延期的公开访问面，不再禁止新增 Host 规则领域。
- 规则测试覆盖未知/重复/未发布数据、稳定键引用、范围、NFC、UTF-8 无符号排序、精确小数、规范字节流与内容摘要。
- `RELEASED` 模组不可变，战役冻结值、实际目录摘要、发布摘要和应用清单必须一致，否则失败关闭。
- V011—V018 文件及其中已持久化身份受历史测试保护，但完整规则族仍为 DRAFT：后续前向迁移可以协调改变未发布目录、运行模型、canonical/archive 投影和对应测试，不要求为被替代的 DRAFT 行为保留兼容层。
- archive format 2 的严格往返测试应随各领域渐进建立；只有跨领域发布候选才固定摘要、激活格式、切换默认发布版并运行完整发布门矩阵。

一级创建的定向验证包括 `LevelOneRuleProfileTest`、`LevelOneCharacterRulesTest`、
`LevelOneCharacterCreationServiceTest`、`JdbcLevelOneCharacterCreationRepositoryTest`、
`V012LevelOneCharacterCreationSchemaTest` 和 `LevelOneCharacterRuntimeGrantsTest`。它们证明预览
无权威写入、非法选择被拒绝、事件尾变化导致 stale preview、事务故障不留下部分角色，并且
DRAFT 发布门保持关闭。

升级的定向验证包括 `AdvancementValueProfileTest`、`LevelAdvancementRulesTest`、
`LevelAdvancementServiceTest`、`JdbcLevelAdvancementRepositoryTest`、
`HostLevelAdvancementServletTest` 和 `V013LevelAdvancementSchemaTest`。它们覆盖逐级 profile
畸形、跳级、资源状态不一致、固定值/服务端掷骰、stale row version、事务失败和禁止部分写入，
并证明过期状态或幂等重放不会消耗随机性。

职业特性的定向验证包括 `ClassFeatureRulesTest`、`ClassFeatureAdjudicationRulesTest`、
`ClassResourceRecoveryRulesTest`、`V014ClassFeatureCoverageMatrixTest`、
`V014ClassFeatureLifecycleSchemaTest`、`CharacterAdvancementChoiceRulesTest` 和
`V015MulticlassAsiFeatSchemaTest`、`V016StartingProficiencyBaselineSchemaTest` 与
`V017CharacterArchiveV2OriginSchemaTest`。V018 的职业贡献、共享施法者等级、半施法职业合并取整、
Pact Magic 隔离、1—9 环上限及畸形目录由 `V018MulticlassSpellSlotFoundationSchemaTest` 与
`MulticlassSpellSlotRulesTest` 覆盖。format 2 的角色投影另由
`CampaignArchiveV2CharacterStateTest` 和 `JdbcCampaignArchiveV2CharacterStateImportRepositoryTest`
覆盖规范写出/严格读取、稳定事件闭包、预览、调用方事务、失败回滚及无 commit/rollback 的
Repository 合同。参数化矩阵逐职业覆盖全部 236 个职业/子职业特性；规则
测试验证自动、裁决、阻断三态、子职业选择边界、动态恢复等级、阻断法术资源和畸形冻结目录。

辅助验证 SQL 只读，位于 [database/verify](../database/verify/)；静态测试不会把这些文件的存在当作数据库已验收证据。

### 4.1 完整 SRD 5.1 目标的测试矩阵

新增规则领域除通用边界外，还必须覆盖与其他领域的组合行为：

- 角色构筑、升级、多职业、专长和派生值重算；
- 通用骰子、优势/劣势抵消、攻击、暴击、伤害分量、抗性、易伤和免疫；
- 先攻、轮次、回合、动作经济、反应窗口、移动、距离、范围和掩护；
- 0 HP、昏迷、稳定、死亡豁免、治疗、临时生命值和即死；
- 状态、持续时间、重复豁免、专注、叠加、免疫、到期和解除；
- 已知/准备法术、法术位、多职业施法、目标、区域、升环、资源消耗和开放式 DM 裁定；
- 武器、护甲、负重、货币、消耗品、充能、同调和休息恢复；
- 怪物动作、反应、恢复能力、施法、抗性/免疫、CR/XP 和遭遇关系；
- 存档格式演进、旧 v1 识别、完整状态往返和失败时禁止部分导入。

随机规则使用可注入且可审计的随机源测试边界和候选选择，但生产结果仍由服务端安全随机源
生成。时间、回合和触发测试必须固定明确时钟或顺序，不能依赖测试执行速度。任何规则领域
只有在目录、服务、仓储、事务、Web 边界和存档测试共同完成后，才可标为已实现。

## 5. Web 与安全测试

覆盖精确 loopback/Host/scheme/port、伪造 `X-Forwarded-*`、方法限制、Session Cookie、CSRF、Origin/Referer、Fetch Metadata、安全响应头、epoch、对象版本、幂等键和摘要。错误入口应返回一致的普通错误且不能到达业务写入。

客户端权威边界测试应确认浏览器不能指定骰点、选中骰、派生修正、最终结果、效果算法、
数据库身份或版本推进。相应领域实现后，还必须确认浏览器不能指定服务器候选集合之外的
职业/专长/法术/目标，不能覆盖先攻顺序、伤害调整、资源消耗、状态持续时间或 DM 裁定类型。

## 6. WAR 审计

生产代码或打包资源变化后，记录候选 WAR：

- 字节数、SHA-256 与 ZIP 条目数；
- 必需的新类、JSP、JavaScript、`web.xml` 与 V001—V018 资源；
- 私钥/证书秘密、凭据、Tomcat 外部配置、日志、备份、真实存档、结果捕获与本地路径文件名的匹配数；
- 生产资源和示例配置差异中的秘密扫描结果。

WAR 审计通过不代表已经部署。

## 7. 手工验收

只有可达路由、UI 或客户端 JavaScript 变化时才需要浏览器验收。按[部署指南](deployment.md)检查 `/health`、根重定向、`/host`、数据库诊断、安全头、错误 Host/origin 与明确排除的路由。真实业务写入必须单独授权并具备可验证恢复方案。
