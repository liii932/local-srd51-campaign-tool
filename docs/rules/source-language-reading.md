# 规则源语言分区读取

`persistence.JdbcSourceLanguageRepository` 从独立规则 `DataSource` 读取
`dnd5e2014_srd51_se` / 字符串 `1` 的当前语言与工具熟练目录。`loadCatalog()` 返回不可变
`CharacterCatalogPartition`；`load()` 是其语言投影，仍完整验证同次安装的两个分区。
缺失、未安装、损坏或超出支持范围均失败，不返回部分集合。
它不产生完整内容摘要、发布批准或运行就绪证明，也不连接 Host、JNDI、启动加载和业务调用方。
完整规则族保持 DRAFT，旧 RELEASED v1、新战役默认值及既有迁移不变。

结构依据是[规则源 schema](../rule-source-schema.md)，字段、精确语言集合及纯投影沿用
[语言作者分区](language-author-package.md)与[工具熟练目录](tool-catalog-partition.md)。读取不依赖作者文件、离线安装类或旧目录三表。

## 固定读取与当前证据

一次调用只借用一个源连接，在同一个只读 `REPEATABLE_READ` 事务中依次读取：

1. `SELECT CAST(DATABASE() AS BINARY)`，逐字节确认默认库为 `dnd_tool_rules`。
2. `rule_schema_meta` 的完整有序账本，与 `RuleSchemaMigrations` 的独立批准清单比较。
3. 精确身份的 `rule_release` 头，核对正源 ID、格式 2/2、`SHA-256`、DRAFT、正安装代次、
   NULL 内容摘要及 NULL 发布时间。
4. 同一源 ID 与当前代次的安装事实，核对准确归属、支持的作者/清单/指纹版本、UUIDv4 网络序、
   小写摘要及包展示文本；当前范围只能是 PARTITION，observed 摘要必须 NULL。
5. 同一当前事实的分区声明，准确且唯一为 `character.language`、`character.tool`。
6. 同一发布根的全部语言行，独立验证准确 18 键、键/分类矩阵及 1—18 排序置换后构造纯分区。
7. 同一发布根的全部工具行，独立验证准确 37 键、键/分类矩阵及 1—37 排序置换。

所有 SQL 固定、参数绑定，不切库、不执行 DML、DDL 或锁读。每个查询设置 5 秒 JDBC 查询超时；
头与当前事实允许观察第 2 行，分区声明允许第 3 行，账本允许期望数加一，语言允许第 19 行、工具允许第 38 行后拒绝，
不会截断多余行制造合格集合。该超时不等于连接借用或整次操作的可中断 deadline。

仅当前代次的证据参与本分区读取。旧 COMPLETE 事实可以保留原非 NULL 摘要，而当前 PARTITION
头和事实的摘要均为 NULL；旧摘要既不与当前头比较，也不补作当前完整性证明。本入口不扫描全源
安装历史、不核验安装控制行预算，不提供安装结果查证或提交已决屏障；这些职责仍属于
[离线安装协议](offline-language-installation.md)。
创建/安装时间不参与语言内容或当前代次的判定，本入口不读取这些诊断列。

## 类型与失败边界

技术身份、枚举和摘要按二进制 ASCII 精确读取，不做 trim、大小写转换或编码修复。整数只接受
JDBC 的真实整数值，在转换和使用前检查范围；字符串、浮点数、小数或 NULL 不冒充合法整数。
源 ID、安装代次及关联字段只在源侧校验中使用，不进入返回值、逻辑内容、运行快照身份或存档。

显示文本必须已经是合法 Unicode 标量和 NFC，按码点检查长度；拒绝 C0/C1、DEL、空串及全空白。
源行中的分解串直接拒绝，不在读取时正规化。SQL 返回顺序不决定逻辑顺序；纯分区按稳定键排序，
显式 `sort_order` 保持其原值。源自报 RELEASED、当前 COMPLETE、未知格式、错误账本、缺失/重复/
跨发布证据以及任意坏行均失败，不授予完整规则资格。

## 连接所有权

本入口拥有自己借用的连接和只读事务，不接收调用方事务。借出连接必须处于 autoCommit；若已在
事务中，不执行查询，也不提交或回滚那个未知事务。建立本次事务前保存原状态，正常读取结束后
提交只读事务，再恢复 autoCommit、readOnly、isolation 并关闭资源，全部成功后才返回分区。

本次事务内失败则回滚；回滚失败时不调用 `setAutoCommit(true)`，避免隐式提交未决事务。
无法安全恢复的连接先调用 JDBC `abort`，终止成功才关闭借出句柄。若终止也失败，则保留借出
句柄隔离、不再调用可能重置 autoCommit 的池 `close`；连接池所有者负责失效处理和恢复，不得
将该异常视为可直接重试的成功归还。恢复、关闭和处置异常不会变成成功结果，也不会覆盖原始
失败。结果集和语句由此入口关闭，连接在上述安全边界内关闭。`setReadOnly(true)` 只是防误用
标志，真实 SELECT-only 权限必须由数据库账号及独立验收保证。

## 验证

```bash
mvn '-Dtest=JdbcSourceLanguageRepositoryTest,CharacterCatalogChainTest,RuleDatabaseSchemaVerifierTest,CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
```

无数据库测试使用独立手写的 18 项语言与 37 项工具字段矩阵和 JDBC 故障代理，核对固定读取、投影、类型、当前
证据、超量/损坏拒绝及连接资源与失败处置。WAR 审计确认新只读适配器和纯模型存在，规则 SQL、
作者源、离线安装类和测试夹具不入包。

真实 Connector/J 类型、MySQL 一致快照、连接池归还/失效及实际最小权限仍须在单独授权的隔离
实例中验收。先通过专用测试目标保护的无 DB 回归；不复用部署库或临时表测试库来安装源 schema。
代理测试及普通构建不构成这些真实数据库证据，具体外部边界沿用[测试指南](../testing.md)。
