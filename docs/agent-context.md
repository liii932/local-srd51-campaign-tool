# Agent 项目地图

只负责定位，不替代 [AGENTS.md](../AGENTS.md) 或下表的权威合同。新会话通过
[dnd-project-start](../.agents/skills/dnd-project-start/SKILL.md) 按需读取；不要递归加载全部链接。
当前进度以 Git、代码、测试和经授权的外部证据为准，文件存在不证明功能发布、迁移执行或部署完成。

## 最小项目识别

- 本机、单 DM、唯一入口 `http://127.0.0.1:8080`；LAN/公网、HTTPS、玩家账号与公开 API 不在当前范围。
- Java 21 / Maven WAR / Tomcat 10.1 / Servlet-JSP / 原生 JavaScript / MySQL 8 / JDBC-JNDI / Gson。
- `dnd5e2014_srd51_se_v1` release 1、canonical/archive 1：`RELEASED`，新战役默认；既有持久化合同不可改写。
- `dnd5e2014_srd51_se` release 1、canonical/archive 2：仍为 `DRAFT`；可协调演进，跨领域发布门前不可绑定战役或激活存档。发布门见 [完整规则目标](rules/srd-5.1-complete.md)。
- 请求解析 → `web`；校验/事务 → `service`；JDBC → `persistence`；冻结目录/规范编码 → `module`；入口安全 → `security`。随机结果、版本推进与审计由服务器拥有，事务不得部分提交。

## 任务路由（只选相关行）

下表 Java 入口相对 `src/main/java/com/dndtool/`。测试从 `src/test/java/com/dndtool/` 下的
同包同名 `*Test.java` 开始；没有同名测试时按表中测试族搜索。路径是搜索起点，不是要全部读取的清单。

| 任务关键词 | 先读合同的相关部分 | 生产入口 / 测试定位 |
|---|---|---|
| 规则目录、canonical、hash、发布身份 | [冻结 v1](rules/srd-5.1.md) §2/§11；[v2 目录](rules/character-catalog-v2.md) | `module/BuiltinModuleReleaseRegistry.java`、`module/ModuleCanonicalEncoderV1.java` / `module/ModuleCanonicalEncoderV2.java`；再找 `persistence/ModuleCatalog.java` |
| 语言作者 JSON、严格读取、纯分区投影 | [语言作者分区](rules/language-author-package.md) | `module/LanguageAuthorPackageReader.java`、`module/LanguagePartition.java`；`LanguageAuthorPackageReaderTest`；正式作者源在仓库根 `rule-packages/srd51-complete/`，不入 WAR |
| 源语言只读 JDBC、当前安装证据、连接归还 | [源语言分区读取](rules/source-language-reading.md) | `persistence/JdbcSourceLanguageRepository.java`；`JdbcSourceLanguageRepositoryTest`；仅返回语言分区，不接生产完整目录或运行就绪 |
| 运行语言快照、run/snapshot 身份、调用方事务 | [运行语言快照分区](rules/runtime-language-snapshot.md) | `persistence/JdbcRuntimeLanguageSnapshotRepository.java`；`JdbcRuntimeLanguageSnapshotRepositoryTest`、`V019RuntimeLanguageSnapshotSchemaTest`；V019 仅建空表，语言 PARTITION 不提供执行资格 |
| 离线语言安装、制品清单、操作票据、提交查证 | [离线语言安装](rules/offline-language-installation.md) | `offline/rules/RulePackageCli.java`、`offline/rules/SourceInstallation.java`、`offline/rules/JdbcRuleSource.java`；`RuleArtifactTest`、`TicketStoreTest`、`SourceInstallationTest`；离线类不入 WAR |
| 语言链路等价、独立字段/字节期望、跨快照故障 | [语言分区链路核验](rules/language-partition-equivalence.md) | 测试目录 `offline/rules/LanguagePartitionEquivalenceTest.java`；连接作者、离线安装、源只读及运行分区仓储；代理证据与真实 JDBC 分开报告 |
| v1 战役创建、角色创建、角色卡 | [冻结 v1](rules/srd-5.1.md) §3—5；[架构](architecture.md) §6 | `service/CampaignCreationService.java`、`service/CharacterCreationService.java`、`service/CharacterCardService.java` |
| v2 一级创建、种族、背景、初始选择 | [一级创建](rules/character-creation-v2.md) | `service/LevelOneCharacterCreationService.java`；`LevelOneCharacterRulesTest` |
| 升级、HP、生命骰 | [升级](rules/level-advancement-v2.md) | `service/LevelAdvancementService.java`；`LevelAdvancementRulesTest` |
| 职业/子职业特性、休息、资源恢复 | [职业特性](rules/class-features-v2.md) | `service/ClassFeatureRules.java`、`service/CharacterLifecycleCommandService.java`；`ClassResourceRecoveryRulesTest` |
| 多职业、ASI、专长、熟练 | [多职业与专长](rules/multiclass-asi-feats-v2.md) | `service/CharacterAdvancementChoiceRules.java`；`LevelAdvancementServiceTest` |
| 多职业施法、共享法术位、Pact Magic | [共享法术位](rules/multiclass-spell-slots-v2.md)，含未完成边界 | `service/MulticlassSpellSlotRules.java`；`V018MulticlassSpellSlotFoundationSchemaTest` |
| 检定、骰子、简单物品、事件效果 | [冻结 v1](rules/srd-5.1.md) §6/§8—9 | `service/CheckTransactionService.java`、`service/CheckEffectExecutionService.java` |
| 节点地图、位置、最小遭遇 | [冻结 v1](rules/srd-5.1.md) §9 | `service/EntityPositionTransactionService.java`、`service/EncounterStateTransactionService.java` |
| 存档、导入导出、恢复 | [架构](architecture.md) §9；[format 2 角色状态](rules/archive-format-2-character-state.md) | `service/CampaignArchiveFormatDispatcher.java`；按 `CampaignArchive*Test` 定位，v2 先看 `CampaignArchiveV2CharacterStateTest` |
| JSP、JS、Host API、页面 | [架构](architecture.md) §7；对应领域合同 | `web/Host*Servlet.java`、`src/main/webapp/WEB-INF/views/`、`src/main/webapp/host/assets/`（后两项相对仓库根）；`web/Host*Test.java` |
| loopback、Session、CSRF、安全头 | [架构](architecture.md) §5；[安全](security.md) | `security/HostBoundaryFilter.java`、`security/HostRequestSecurityFilter.java`；API 注册见 `src/main/webapp/WEB-INF/web.xml`（仓库根） |
| schema、迁移、JDBC、权限、数据库诊断 | [数据库](database.md)；拆分设计仅需要时读 [规则/运行库分离](rule-database-separation.md) | `persistence/SchemaMigrations.java`、相关 `Jdbc*Repository.java`；`SchemaMigrationsTest` / `Jdbc*RepositoryTest`；SQL 在仓库根 `src/main/resources/db/migration/` 与 `database/` |
| 独立规则库、来源账本、语言结构、来源权限 | [规则来源 schema](rule-source-schema.md)；[数据库](database.md) | `persistence/RuleSchemaMigrations.java`、`persistence/RuleDatabaseSchemaVerifier.java`；`RuleSourceSchemaContractTest`；SQL 在仓库根 `database/rules/migration/`、`database/grants/rule-source-*.sql` 与 `database/verify/rule-source-schema.sql` |
| 构建、测试、WAR、部署、备份 | [测试](testing.md)；实际部署才读 [部署](deployment.md) / [备份恢复](backup-and-restore.md) | 仓库根 `pom.xml`、`config/`、`tools/`；MySQL 集成测试需独立授权与隔离目标 |
| MySQL 测试连接目标保护 | [测试](testing.md) §3 | 测试目录 `persistence/MySqlIntegrationTestSupport.java`；`MySqlIntegrationTestSupportTest`，两入口在连接前仅允许专用测试目标 |
| 新增完整规则领域、覆盖差距、发布门 | [完整规则目标](rules/srd-5.1-complete.md)；[架构](architecture.md) §2—3 | 先确认已有目录/运行模型/测试；没有入口不代表已实现，不为未来领域编造类名 |
| Agent、skill、项目导航、文档 | [AGENTS.md](../AGENTS.md)、本文 | `.agents/skills/dnd-project-start/SKILL.md`（仓库根）；不预读业务源码 |

## 使用与维护

- Pi 在项目受信任且未禁用 skills 时发现 `.agents/skills/`，默认只把名称与描述放入上下文；`AGENTS.md` 引导 Agent 在首个任务加载本 skill。它不是自动执行的启动脚本。
- 新增 skill 后可在已信任项目中 `/reload` 或重启；显式调用 `/skill:dnd-project-start 具体任务` 可确保加载。其他 Agent 也可按 `AGENTS.md` 的链接读取，无需 Pi 插件。
- 入口变化时同步修正表中路径。规则正文、安全/事务不变量、账号职责、验证方法只在对应权威文档维护，不在 skill 中复制第二份。
- 动态 Issue/PR、分支、历史测试结果和部署状态不缓存于本文。被忽略的 `.pi/harness/domain-json-rules/` 是本机分域 JSON harness，仅任务明确涉及它时读取，不进入 Git 或 WAR。
