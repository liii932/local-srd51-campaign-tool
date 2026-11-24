# 数据库

应用及本机规则库使用 SQLite。原 MySQL 资料由持有者与其匹配版本共同保管。生产部署见[部署指南](deployment.md)。

## SQLite 生产工作库

新建或打开战役时实例化私有工作库。受审 JSON 声明的表、键、约束和索引由
`StorageSchemaCatalog`、`StoragePlan` 与 `SqliteSchemaVerifier` 编译并核验。
结构、seed 与完整战役事实在受控生命周期内初始化。同版残留继续使用原库，验证
现有结构和数据。SQLite 初始化使用独立声明。

`CampaignStorageService` 通过独占门与单个 `BEGIN IMMEDIATE` 事务推进规则状态。
`CampaignDomainRepository` 写入聚合、投影和关联业务事实，由调用方提交事件、revision 和
收据。读取独立核对准确投影及绑定。完整文件保留冻结材料和历史，重开建立新 run；
同版残留恢复保留原 run 与收据，并建立新 epoch。文件过程、耐久和备份见
[运行合同](storage-vitality-loop.md)和[备份恢复](backup-and-restore.md)。

## MySQL 维护与退役边界

| 保管对象 | 需要共同保留的材料 |
|---|---|
| 运行库 `dnd_tool_se` | 数据库、已应用的 `schema_meta`、匹配版本及批准清单、V001—V042 原 SQL、对应角色历史与存档。 |
| 来源库 `dnd_tool_rules` | 数据库、`rule_schema_meta`、V001—V012 原 SQL、规则 ZIP、安装及接受账本、尚未裁定的原票据和审阅材料。 |
| 维护与读取 | 匹配的源码、制品、外部配置、驱动、权限模板、只读核验与备份。format 1 整档和内部 format 2 片段由对应版本解释。 |

当持有的库需要保持可读时，先按其实际应用版本核对备份、迁移历史及原包与接受记录的对应关系。迁移、应用与只读核验各用独立账号，真实数据的备份、恢复、销毁及账号调整分别明确目标与授权。资料、凭据和备份保管在 Git 与 WAR 之外。[备份与恢复](backup-and-restore.md#原-mysql-存档)

## 迁移合同

SQLite 以当前应用结构声明初始化工作库，启动与恢复核对结构及事实。保留旧 MySQL 实例时，按其匹配版本核对 SQL 原件与已应用摘要；需要演进时使用前向迁移。已应用的文件、名称和校验和须保留。源码归档提供参考版本，现场保管者应按实例账本选择准确版本。[原运行 SQL](https://github.com/liii932/local-srd51-campaign-tool/tree/4f485b3158c3bb3148463c68f9ada53839edaa1e/src/main/resources/db/migration) · [原来源 SQL、权限与核验](https://github.com/liii932/local-srd51-campaign-tool/tree/4f485b3158c3bb3148463c68f9ada53839edaa1e/database)

## 稳定合同

本版 format 3 以战役内冻结材料、规范身份、完整历史及投影为权威。本机规则库保管准确原包和独立接受账本。完整文件打开重新绑定准确组合，同版残留恢复读取工作库及原收据。真实资料处置与程序交付分别授权。[单路线交付边界](design/v2/mysql-retirement.md)
