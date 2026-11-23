# 测试指南

默认选择覆盖本次差量的最小充分验证。模组内容和可见行为使用独立工具，应用代码按受影响边界选择 Maven 定向测试。WAR 审计、真实平台和显式 MySQL 验收有各自的证据。仓库文档记录可重复方法，每次运行的计数和哈希留在验证或发布记录。

SQLite WAR 供外部 Tomcat 部署和独立作者场景使用。离线 MySQL 工具使用同一源码构建的
`target/classes`。迁移核对使用仓库中的原始 SQL，实际 MySQL schema、权限和数据由另行
授权的现场验收确认。打包与离线交付的核查范围见 [WAR 审计](#6-war-审计)。

## 1. 聚焦测试

先检查本次 diff、调用方、共享边界和资源引用，再选择相关证据：

| 改动 | 验证 |
|---|---|
| 模组作者声明、目录、数值、授予、覆盖或模组行为用例 | [模组作者向量与成品行为](#模组作者向量与成品行为)。用非 Maven 工具校验实际交付内容和受影响的可见行为。 |
| 应用机制、加载/解释、命令、权威状态、事务、持久化或 Web 边界 | 选择受影响的 JUnit 机制、调用方和失败边界，使用 `-Dtest` 定向运行。 |
| 新增或修改应用机制，同时交付模组内容或改变其可见行为 | 应用机制及接入使用定向 JUnit，作者声明和成品表现使用独立模组验证。两边各自保留证据。 |
| 纯文档或 skill | 核对内容、链接和格式。 |

模组相关的应用 JUnit 用例通过 [Surefire 专用夹具](../src/test/fixtures/application-rules/storage/rules)
验证解释、冻结、事务和恢复。Maven 为这组夹具单独生成身份清单，Surefire 优先读取夹具及对应清单。
作者发布新 ZIP 时，用交付包和相应场景验证内容。应用输入合同改变时再更新固定夹具。WAR 和本地浏览器
验收启动器使用 `src/main/resources/storage/rules/` 中的交付包及其构建生成的身份清单。

验证当前战役的检定、条件效果和事务回滚时，可以运行

```bash
mvn '-Dtest=CampaignCheckEffectsTest' test
```

根据变更覆盖正常行为、边界值、Unicode 码点/NFC、非法冻结数据、过期 `row_version`、幂等冲突、事务失败与禁止部分写入。重命名批次还应覆盖受影响的 Servlet 注册、反射字符串、页面资源和脚本引用。

原型验收只针对当前应用版本：验证当前格式的完整往返及不支持格式的明确拒绝，不要求旧存档
仍可导入。设计替换时同步改写或移除只固定旧模型的测试，保留当前有效的安全、事务和失败路径
覆盖；既有测试通过或历史批准不能单独证明该设计应继续保留。

只确认生产代码可编译时使用 `mvn compile`。定向 `test` 已含前置编译阶段，不需要机械重复 `compile`。保留无匹配测试的失败行为，不用 `failIfNoTests=false` 或 `surefire.failIfNoSpecifiedTests=false` 隐藏选错测试。

需要制品且应用测试待执行时，可用 `mvn '-Dtest=实际相关测试类' package`。已有同输入的应用测试证据，或本次仅改模组内容时，使用 `mvn -DskipTests package` 并记录为跳过测试的打包。交付可运行模组时用候选 WAR 验证受影响的作者场景。生产代码或打包资源变化按第 6 节审计 WAR。部署的干净构建按[部署指南](deployment.md#1-构建候选)处理。

`-Dtest` 仅约束 Surefire。使用 `verify`、profile 或多模块 reactor 前检查 POM 及插件绑定，不能据此假定 Failsafe、其他插件或所有模块均已缩小范围。MySQL 集成测试仍按第 3 节单独授权和选择目标。

相关测试失败时先修复并重跑相关范围，不自动扩大为全量。复用本次对相同代码、资源、依赖、配置及相关环境取得的有效结果；后续改动影响证据时才重跑对应范围。不得同时运行会写同一 `target` 的 Maven 命令。

当前工作台的启动、规则绑定、完整文件与就绪测试见[SQLite 结构、完整文件与运行验收](#sqlite-结构完整文件与运行验收)。

验证 SQLite Web 入口时，由 `HostHomeServletTest` 核对首页和退役 URL，由 `StorageHttpIntegrationTest` 核对就绪、文件下载、规则绑定及拒绝写入后的状态。
维护离线 MySQL 目录诊断时，用下列服务测试验证先核对 schema、再读取模组，以及连接或校验失败时停止后续查询。

```bash
mvn '-Dtest=DatabaseDiagnosticsTest' test
```

真实 MySQL 验收按第 3 节独立执行。当前离线链路的处置与退役后的发布验证见
[无 MySQL 采用验收](#mysql-free-acceptance)。

### Tomcat 测试依赖

`pom.xml` 的 `tomcat.version` 统一管理 `tomcat-dbcp` 与 `tomcat-embed-core`。
两者均保持 `test` scope，升级时使用同一 Tomcat 10.1 补丁版本，并运行：

```bash
mvn '-Dtest=MySqlIntegrationTestSupportTest,StorageHttpIntegrationTest' test
```

前者使用无网络工厂验证连接入口与连接池配置；后者在临时 SQLite 存储上验证真实 HTTP
链路，运行前须确认 `127.0.0.1:8080` 空闲。两者均不连接真实 MySQL。Tomcat 测试依赖
及容器提供的 Servlet API 不得进入生产 WAR；更新这些依赖不会升级外部 Tomcat 实例，
外部容器仍按[部署指南](deployment.md)独立维护。

### 模组作者向量与成品行为

作者检查以实际声明、原 ZIP、组合和独立期望为输入。受影响的用例应记录准确模组版本和组合顺序、来源与覆盖、正负例及预期。以下命令逐字节比较作者源生成的 ZIP 和仓库中待打包的 ZIP：

```bash
python3 rule-packages/srd51-runtime/build-bundles.py --check
```

可运行模组使用一份兼容的现有 WAR、Python 3 和 JDK 21+。从仓库根构建本次家规候选，运行定额伤害和运动检定两个用例：

```bash
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" dm-runtime-check-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.2.1.war \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/dm-runtime-check-variant.zip" --case fixed-damage --case skill-check \
  rule-packages/srd51-runtime/scenarios/check-variant.json
```

按断言选 `--case`，同一组合的多个 ID 可在一个命令中重复传入，省略时执行整张清单。一批只解包、编译驱动、启动引擎 JVM 和组合一次。每项使用独立的角色或战斗状态。核对输出的有序成员、候选绝对路径和原 ZIP SHA-256。`@名称` 从 WAR 读取依赖。整个模组验证链路只调用 Python 和 JDK。若同时改了应用机制，先定向验证应用并单独构建新 WAR，再将该 WAR 传给模组入口。单项巨剑结算、新增用例及同版本错误候选的复现命令见[场景指南](../rule-packages/srd51-runtime/scenarios/README.md)。

定位慢操作时，先读已有 `target/surefire-reports/TEST-*.xml` 中各 `testcase` 的 `time`，再对照该方法和共享夹具。定额伤害从合法初始状态结算，职业授予经过相应升级。应用战斗事务测试组装请求可读取夹具里的当前版本，命令提交、故障回滚、损坏拒绝及保存恢复仍走实际服务并显式读回。历史耗时只用于选择用例；测量整条命令可用 `time`，单项通过耗时另看场景输出或 Surefire 报告。

DRAFT 语言与工具内容运行 `python3 -B tools/rule-packages/catalog-vector.py --check rule-packages/srd51-complete`，按独立字段矩阵逐项核对作者记录，并复算规范字节摘要。离线读取器和安装测试从 `src/test/resources/character-catalog-author/` 读取固定应用夹具。范围与字段见[DRAFT 作者验证](rules/language-author-package.md#独立验证)。

修改目录校验或安装指纹脚本时，运行 `python3 -B tools/rule-packages/tests/rule-vectors-test.py -v`。该测试在临时副本中检查行重排、非法作者字段和错误的期望表，并验证安装指纹保留原文件换行、包显示名按 NFC 编码。

需要核对真实表单、玩家候选或完整文件恢复时，在已启动的隔离应用上选用下文的浏览器脚本或可重复的人工场景。记录实际组合、步骤、断言和结果。模组验证使用独立工具，应用编译和 WAR 打包是单独步骤。

## 2. 必要时的完整验证

只有用户明确要求全量应用回归时才执行完整 Maven 套件。共用机制或跨域契约变化时，列出受影响的具体应用责任和覆盖缺口，补选对应测试组。发布、部署、合并、候选打包及任务收尾都按改动选择相关验证。

用户要求全量时使用下列命令。`clean` 独立判断，有旧产物残留风险或需要干净候选时才使用。

```bash
mvn verify
```

Maven 验证记录实际命令、最终退出码、测试总数、失败、错误与跳过数。模组验证记录候选包、使用的 WAR（如适用）、实际运行的场景和结果。不要从截断或仍在运行的输出宣称成功，不将定向通过、零测试或跳过测试表述成全量通过。

全量运行中断或修正局部失败后，可保留本次已完成、输入未受影响的通过报告，只补跑失败、
未完成及修改影响的测试。逐类核对合并覆盖与跳过原因，分别报告原命令和补跑结果；不能把
它描述成一次全量命令通过。需要更换构建目录时复制当前源码和资源，保持相对目录结构，
在无旧产物的目录构建候选；不要复制秘密、真实数据或旧 `target`。

普通 Maven 构建的数据库相关测试使用 mock、临时结构或静态 SQL 检查。真实数据库按第 3 节独立授权。

## 3. MySQL 集成测试

本节的 MySQL 连接用于离线旧库维护和独立 JDBC 验收。`MySqlIntegrationIT` 默认不参加普通构建。它只允许连接独立、一次性的可写测试库，并在连接中创建临时表，用于验证：

- 角色聚合 DAO 在版本匹配时提交、版本过期时拒绝、子写失败时回滚；
- Tomcat DBCP 连接借出、归还及连接状态恢复。

当前战役的检定原子性由 `CampaignCheckEffectsTest` 验证，完整文件的接纳、打开与拒绝由 `StorageHttpIntegrationTest` 验证。

运行临时表套件时，可在数据库管理工具中审核并执行
[database/test/create-integration-database.sql](../database/test/create-integration-database.sql)，建立专用
`dnd_tool_se_it` schema 和临时表测试账号。只在未保存的编辑器缓冲区或仓库外临时副本替换密码
占位符；该引导脚本不执行应用迁移。完整迁移链验证不复用这个 schema 或账号，必须
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

Windows 专属操作可在同一安全边界内使用等价 PowerShell 环境变量语法。测试代码仅允许明确的
专用目标 `dnd_tool_se_it`；`dnd_tool_se`、`dnd_tool_rules`、其他库及无法明确解析的目标均在
连接前拒绝。不得修改该保护来复用少量测试数据。全部集成测试被跳过不构成验收；
不要打印完整环境，也不要把测试口令或完整业务数据写入日志。

允许的 URL 是普通单主机 `jdbc:mysql://HOST[:PORT]/dnd_tool_se_it`，可选且唯一的查询参数为
`connectionTimeZone=UTC`。拒绝多主机、负载均衡/复制、用户信息、百分号编码、片段、额外路径及
其他参数，避免驱动扩展属性覆盖连接目标或执行额外会话 SQL。

连接保护本身使用无数据库回归：

```bash
mvn '-Dtest=MySqlIntegrationTestSupportTest' test
```

这组测试在显式启用、配置齐备且确认可写的条件下，分别经过 `open` 与 `pooledDataSource`
的配置及目标检查路径，注入连接/池工厂核对非法目标的实际连接调用计数为零；正例证明专用
目标能通过检查。它不连接真实数据库，也不扩大临时表账号或独立迁移验收路径的权限。

### 3.1 自动化数据库验证顺序

1. 普通 Maven 构建不连接任何业务数据库。
2. `MySqlIntegrationIT` 只连接 `dnd_tool_se_it`，由 `dnd_tool_se_it` 用户创建连接级临时表；测试结束后不得留下持久业务表或数据。
3. 旧 MySQL 运行链 V001—V042 只在物理隔离且可销毁的 MySQL 实例或容器中演练；该实例内部创建原名 `dnd_tool_se`，使用独立 schema、账号和存储卷。
4. 自动化按批准清单从仓库读取原始迁移文件并核对载荷摘要，授权执行时原样提交 SQL。每个迁移使用新的客户端连接并显式把默认数据库设为 `dnd_tool_se`，使没有 `USE` 的 V007 也定位到正确的库。
5. 迁移完成后，使用该隔离实例内的 `dnd_tool_se_validation_ro` 核对完整 `schema_meta`、可见结构和目录计数。MySQL 按 `TRIGGER` 权限过滤的触发器定义由隔离实例内的迁移身份在停止迁移写入后核对，但验证步骤只能执行审核过的 `SELECT`/`SHOW`。
6. 对保留的实际 `dnd_tool_se`，Agent 使用 `dnd_tool_se_agent` 做只读盘点或 Harness 比较。迁移、授权和写入由各自的授权检查点处理。

账号/schema 合同及现有运行库的阶段性使用决策见[数据库说明](database.md)。

<a id="mysql-free-acceptance"></a>

## 无 MySQL 采用验收

[MySQL 退役设计](design/v2/mysql-retirement.md)采用后，默认产品构建和验收须在完全没有
MySQL 的环境完成。当前 Web WAR 已使用 SQLite，但同一 Maven 项目仍解析 `provided` 的
Connector/J，默认测试也含离线链路的 SQL 合同和代理用例。第 3 节及下文的 JDBC 命令仍描述
现有实现。下表用于退役实施时核对覆盖转移，本节的启动或场景预检只证明实际执行的环节。

### 测试处置矩阵

| 当前测试责任 | 退役时的处置与验收证据 |
| --- | --- |
| 纯规则、声明解释、ZIP 安全和冻结身份，包括 `ProductionRuleArtifactTest`、`Schema2CompositionGoldenTest` 和 `Schema2*RulesTest` 中仍有消费者的用例 | 保留严格解析、Unicode、预算、算法、候选/字段覆盖链、unsupported/DRAFT 拒绝，以及当前摘要和原包身份的独立向量。混合用例拆出纯断言，按当前支持的合同维护期望。 |
| 规则来源安装、接受和运行快照，包括 `Schema2SourceInstallationTest`、`LanguagePartitionEquivalenceTest`、`Schema2SnapshotTransferServiceTest`、`JdbcSchema2*Snapshot*Test` | 生产规则库和战役绑定由真实 SQLite 的 `LocalRuleLibraryTest`、`CampaignRuleBindingIntegrationTest`、`CampaignRuleBundleIsolationTest` 承接，缺口补在对应 owner。覆盖原包身份、导入与接受分离、缺包/未接受、顺序/覆盖、篡改、故障回滚及提交不明后的原操作查证。旧 PARTITION、双库转存或离线票据的专属断言随相应入口退出默认验收。 |
| JDBC 角色、检定、装备、休息、场景、战斗、持续效果、怪物和施法服务/仓储，包括 `JdbcSchema2*HistoryTest`、`Schema2CombatRuntimeServiceTest` 和 `Schema2SpellSaveDamageRuntimeServiceTest` | 对齐[已接合领域](design/v2/domain-integration.md#可重复验收索引)，迁到走生产命令、真实 SQLite 事务及读回的 `CampaignDomains*IntegrationTest` 等对应 owner。已有等价用例合并覆盖，保留手算/固定期望、原骰次序、全目标预检、子事实、精确继续点及晚写失败。未支持机制保持失败关闭。 |
| `BusinessWriteTransactionsTest`、`BusinessWriteGuardSchemaTest`、`MySqlIntegrationIT` 的事务责任 | 将仍适用的并发串行、stale 版本、同键重放/异意图、提交前失败与 UNKNOWN 隔离查证落到 `CampaignCheckEffectsTest`、`CampaignDomainRecoveryTest`、`CampaignStorageLifecycleTest`、`CampaignStorageCrashTest` 等真实 SQLite 用例。观察预检拒绝零 RNG/写入，晚失败后无部分状态、事件、效果、版本或收据，已提交结果可查证且不重放。MySQL 首条 SQL 守卫、DBCP 会话复位和专用连接目标保护随实现移出产品验收。 |
| SQLite 结构、文件、备份恢复、Host/玩家及安全 | 保留 `StorageSchemaCatalogTest`、`SqliteSchemaTest`、`CampaignFileVectorTest`、`CampaignCodecTest`，以及文件生命周期、原生平台、HTTP 和[玩家测试](#玩家参与原型验证)。保留 format 3 正常往返、旧/坏文件拒绝、退役路由 `404`、会话和存档秘密边界。 |
| MySQL 两条静态迁移链、schema/授权、诊断、真实 JDBC 验收、format 1 和内部 format 2 旧档/片段及旧 Host 专属语义 | `SchemaMigrationsTest`、`RuleSchemaMigrationsTest`、`DatabaseDiagnosticsTest`、`MySqlIntegrationTestSupportTest`、`LanguagePartitionJdbcIT`、`verify-language-jdbc.py` 及旧存档/Host 专属断言跟随所检验的实现退出默认产品验收。有明确保留范围的旧版维护证据在其隔离入口单独执行。仍有产品意义的独立期望转到现行消费者。 |
| 与数据库无关的规则作者源和独立向量 | 保留 Python/JDK 成品场景、ZIP 一致性检查、DRAFT `catalog-vector.py` 和 `rule-vectors-test.py` 中作用于保留格式的断言。作者源与手写期望可独立于实现执行；用例通过维持其原有资格边界。 |

### 两个清洁环境

在两个相互独立的环境 A/B，从同一退役候选修订的干净检出开始。分别使用 Linux/WSL 本地
POSIX 文件系统及原生 Windows 本地固定 NTFS，准备独立 Maven 本地库、Tomcat 实例、运行账号的
私有 SQLite 根和仓库外证据目录。运行环境只安装相应平台的 JDK、Maven、Tomcat 和实际需要的
验收工具。记录 MySQL/MariaDB 服务、进程、监听、客户端和 JDBC 驱动的缺席；核对有效的
Maven 设置、应用环境、JVM classpath、Tomcat `lib`、ROOT Context 及 JNDI，确认无旧库
配置、凭据、共享驱动或继承的数据。构建时只接入需要的 Maven 制品源，应用运行阶段隔离外部数据库网络。
各端口在本机串行使用，进程操作和浏览器业务写入只针对获授权的隔离实例及一次性样本。

1. **空依赖库解析及产品打包。** Linux/WSL 在仓库根执行以下命令。Windows 原生 Maven 使用
   相同目标及选项，将两个 `mktemp` 目录换成由操作者新建的空白本地目录。两个环境各自执行，
   使用正常产品 POM 和默认构建 profile。依赖树覆盖全部 scope，包括 test 和 provided。

   ```bash
   acceptance_maven="$(mktemp -d)"
   acceptance_evidence="$(mktemp -d)"
   mvn -B -ntp "-Dmaven.repo.local=$acceptance_maven" \
     '-Dtest=StorageApplicationListenerTest,StorageHttpIntegrationTest,LocalRuleLibraryTest,CampaignRuleBindingIntegrationTest,CampaignCheckEffectsTest,CampaignStorageCrashTest,PlayerHttpIntegrationTest,PlayerProcessRecoveryIntegrationTest' \
     clean package
   mvn -B -ntp "-Dmaven.repo.local=$acceptance_maven" dependency:tree \
     "-DoutputFile=$acceptance_evidence/dependency-tree.txt"
   ```

   保存两条命令的输出、退出码和 Surefire 结果。核对依赖树、解析日志和新库条目中没有
   `com.mysql:mysql-connector-j`、`mysql:mysql-connector-java`、MariaDB 等 MySQL 驱动或
   MySQL 专用产品依赖。当前 POM 在这一项会发现 Connector/J。按 [WAR 审计](#6-war-审计)
   检查两个候选的必需入口、资源、SQLite JDBC、规则原包和清单。退役候选再检查 `target/classes`
   及 WAR 无 MySQL 专属驱动、迁移、CLI、仓储、启动/连接配置和遗留入口，纯领域类仍可用。
   按上表补跑改动涉及的应用测试。所有补跑通过 `-Dmaven.repo.local` 使用本次的独立库，
   完整 Maven 套件仍按第 2 节选择。

2. **真实 WAR 启动。** 两个环境均按[外部 Tomcat 流程](deployment.md)启动隔离实例，Windows 复用
   [原生候选工具](#原生-windows-外部-tomcat-候选验收)。移机使用同一份审计过的 WAR，两侧核对其
   SHA-256。每次启动均保持无旧 JNDI 资源、驱动、凭据和可用 MySQL 连接。每个分支使用独立的
   一次性 `user.home`，在启动该实例的 JVM 上设置 `-Duser.home=<私有绝对路径>`（直接启动 Java 的候选工具可用
   `JDK_JAVA_OPTIONS` 临时传入）。Windows `Prepare` 会写入显式根，只在停止的一次性实例上修改其
   `ROOT.xml` 中的 `DND_STORAGE_ROOT` 参数。逐次停止再更换配置，检查以下启动分支：

   | 配置 | 就绪与本地文件断言 |
   | --- | --- |
   | 显式有效私有根 | `node tools/storage/verify-war-http.cjs IDLE` 通过，`instance.lock` 在准确显式根。 |
   | Context 参数和进程环境变量均未设，`user.home` 指向有效私有目录 | 相同 HTTP 验证通过，`${user.home}/.dndtool/campaigns/instance.lock` 出现在该默认根。 |
   | Context 显式给相对路径，进程环境和默认根均可用 | 就绪 `503`，`mode=SQLITE`、`ready=false`、`state=UNAVAILABLE`、`code=STORAGE_ROOT_INVALID`，有效备选根不出现受管文件。 |
   | 两处覆盖均未设，在一次性 `user.home` 预先建立名为 `.dndtool` 的普通文件 | 相同 `503` 和状态，`code=STORAGE_UNAVAILABLE`，保留该文件且未创建活动运行。 |

   失败分支直接读取 `GET /api/host/storage/readiness`，例如运行
   `curl -i http://127.0.0.1:8080/api/host/storage/readiness`，同时确认响应不披露路径。

3. **真实 WAR 规则和业务。** 按 [SQLite 验收](#sqlite-结构完整文件与运行验收)把浏览器用例直接跑在健康
   候选实例上，验证规则 PC/NPC、自由卡、装备、检定两分支及后果、健康、旅行及场景时钟、战斗/继续、
   资源、可用施法和有限怪物（例如吐息共享伤害及充能），记录初始和最终事实。
   用成品 WAR 运行两份独立规则场景，留存组合顺序、原包摘要和实际用例结果。另核对 ZIP 与作者
   源的一致性及 DRAFT 作者向量。

   ```bash
   war=target/dnd-tool-se-0.2.1.war
   scenarios=rule-packages/srd51-runtime/scenarios
   python3 rule-packages/srd51-runtime/build-bundles.py --check
   python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" "$scenarios/standard.json"
   python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" "$scenarios/check-variant.json"
   python3 -B tools/rule-packages/catalog-vector.py --check rule-packages/srd51-complete
   ```

   自定义规则另从 WAR 外提供一份受支持的准确候选 ZIP。可在仓库外复制随附 `check-variant` 原包的
   条目，将 `author-package.json` 的 `release_version` 改为 `"11"`，将 `rules.json` 中
   `vital_effect.dm_damage` 的 `modifier` 改为 `2`，更新包内 `README.md` 后将这些文件打包在 ZIP
   根目录。包内条目名与清单声明的路径保持一致。保管重新打包后的 `dm_runtime_house_rules@11`
   原字节及摘要。随附 `check-variant` 仍按 `@10`、固定伤害 `3` 的独立场景验证。自定义组合前三包
   使用标准包，第四包使用这份外部候选。

   通过工作台的本机规则库执行外部原包上传（`POST /api/host/storage/rule-import`），从随附列表导入
   前三包。接受前预览应报 `RULE_PACKAGE_NOT_ACCEPTED`。逐包审阅和明确接受（`GET .../rule-review`、
   `POST .../rule-accept`）后，交换基础包与扩展的顺序应使预览返回 `409`。按正确顺序预览
   （`POST .../rule-preview`）并创建战役，比对接受记录、预览和运行的实际成员、双摘要和来源链。
   用真实角色验证运动使用感知、固定伤害为 `2`。用独立隔离根上传坏 ZIP，将固定伤害的 `algorithm`
   改为 `UNKNOWN_VITAL_EFFECT`，导入预期 `409/RULE_PACKAGE_INVALID` 及 `S2_TYPE`。用错误摘要或
   错绑冻结投影验证组合拒绝，复用 `CampaignRuleBindingIntegrationTest` 的错绑用例。核对这些拒绝未登记
   无效材料或建立活动运行。[本机规则库合同](design/v2/local-rule-library.md)

4. **玩家与失败边界。** 先证实默认无玩家监听且 DM 8080 只绑定 loopback，再在独立验收配置按
   [玩家 HTTPS 流程](deployment.md#可选玩家-https-入口)启用准确地址的 8443。执行
   [玩家参与验证](#玩家参与原型验证)的批准、设备/席位隔离、受限投影、候选动作、权限撤销、
   重启后的重连及只读查证，核对旧会话、撤权和未决请求均不能重放写入。记录坏 Host、伪造转发头、
   CSRF/Origin、对象版本、跨 Connector、公开和旧 MySQL URL 的拒绝。涉及真实设备、证书信任或
   防火墙时使用已批准的目标，按[设备采用门](design/v2/player-participation.md#验收与采用门)单独留证。

5. **两机完整文件。** 在 A 分别用标准与本机接受的自定义组合建档。对 `RULE_BOUND_CAMPAIGN`
   format 3 执行保存、END 和下载，保留有原骰、事件/效果及复杂未决继续点的样本，例如
   `CampaignSpacesIntegrationTest` 中的双场景借机反应，或 `CampaignDomainsCombatIntegrationTest`
   中已掷骰但待结算的共享吐息。玩家控制、共享及重开重新批准可以使用独立样本。记录 `fileKey`、
   SHA-256、绑定及事实摘要。向 B 的独立空白实例转移 WAR、导出的 JSON 和必要原包，逐份核对原字节。
   确认导入只登记、同 `fileKey` 冲突无覆盖。标准无需接受记录可打开。自定义先验缺包与已导入未接受
   都不激活，再接受准确包并打开。确认错包/损坏/不支持格式拒绝，无部分写入。核对 B 建立新的 run，
   全部冻结材料与字段链、初始/当前事实、角色/场景/时钟、原骰、根/子历史、资源、未决动作及玩家
   控制/共享与 A 一致。从准确等待点只执行剩余后缀，再保存并结束。原文件继续保持原字节，旧 run/epoch、
   请求收据和设备会话无继承，玩家设备须重新批准。

6. **原工作库残留。** 独立于移机测试，各环境在合成活动战役上正常停机和强制中断，分别重启同一
   实例及原私有根。参照[原生候选的恢复用例](#原生-windows-外部-tomcat-候选验收)运行
   `browser-recovery-smoke.cjs prepare` / `recover` 及 `prepare-discard` / `discard`。
   `node tools/storage/verify-war-http.cjs RESIDUAL` 预期就绪
   `503/STORAGE_RECOVERY_REQUIRED`，显式恢复后为 `ACTIVE`。核对保留原 run、库/journal、最新事实、
   历史和收据，epoch 更新，旧 epoch 拒绝，未重建、补 seed 或重放。另用代表性的复杂未决夹具执行
   原库恢复，检查未决后缀、原骰和资源一致。双场景夹具还检查继续一个场景时其他场景保持冻结。
   另留故障 JUnit 证据，
   用 `CampaignStorageCrashTest`、`CampaignPreparationTest`、`CampaignStorageLifecycleTest`、
   `CampaignDomainRecoveryTest` 及受影响领域用例核对初始化/保存中断、晚写失败、响应丢失、
   提交/回滚结果不明、过期 epoch/revision、同键冲突、坏投影及未知文件保护。分别核对工作库及文件
   前后像、可见性、历史、版本和收据，区分确证回滚、原意图续作和隔离待查证。

7. **原生文件系统。** 在 Linux/WSL 本地 POSIX 环境核对 owner-only 权限、符号链接拒绝、锁和
   同卷无覆盖硬链接。在原生 Windows 本地固定 NTFS 运行
   `mvn '-Dtest=CampaignWindowsFilesystemTest' test`（附上本次的 `-Dmaven.repo.local`），核对 ACL
   继承、已有宽 ACL、junction/reparse、跨进程锁与退出释放，并执行原生候选进程恢复。各自核对
   根争锁、坏库、无效根、损坏保存点和发布冲突的拒绝边界。记录实际执行数、平台受限项目和
   补测结果。进程中断证据只覆盖进程级恢复。[存储配置](configuration.md#sqlite-当前应用存储配置)

记录候选修订、WAR 字节数/摘要/条目数、环境与隔离方式，每个检查项用以下字段单独记录。

| 环境与检查项 | 实际命令或操作及输入身份 | 预期状态或错误 | 实际状态与退出码 | 证据位置 |
| --- | --- | --- | --- | --- |
| A 或 B、步骤编号 | 命令或表单步骤、相关候选/文件/原 run 身份 | 数值、就绪状态、拒绝代码或不变量 | 对照结果、适用时的退出码 | 测试报告、诊断、状态对比或文件审计 |

JUnit 记测试数、失败、错误和跳过原因。缺证、跳过或失败的采用门须在对应环境补验。
证据意外含敏感材料时留在受控目录并在报告中脱敏。测试资料、日志、信任材料和备份均在 Git/WAR 外。
既有 MySQL 库、历史及数据保管单独按[数据库说明](database.md)处理，清洁环境的验收不对其执行连接或写入。

## 4. 迁移与规则测试

本节的 JUnit 类用于应用读写器、组合器、规则机制和离线 MySQL 工具的回归。V001—V042 运行迁移及 JDBC 快照场景指旧 MySQL 模型；当前 SQLite 战役见[结构、完整文件与运行验收](#sqlite-结构完整文件与运行验收)。按实际改动选择相关类，用固定作者包和向量作为测试输入。改动交付模组时，按[模组作者向量与成品行为](#模组作者向量与成品行为)选择独立检查。

改变 [schema 2 有限组合内核](design/v2/schema-2-composition-validation.md#9-内部组合内核)的严格解析或覆盖解释时，可选这些无数据库测试：

```bash
mvn '-Dtest=Schema2CompositionGoldenTest,Schema2CompositionRejectionTest,Schema2CompositionParsingTest' test
```

新内核测试从固定作者字节核对独立最终字段、31 条覆盖链及验证专用摘要，覆盖 N01—N30、
严格解析与预算、依赖/联合版本/顺序、启停与清空、同值覆盖、不可变性及链篡改；相邻测试
保护既有 schema 1、canonical 和发布门。期望在 `src/test/resources/schema2-composition/`，
固定验证 ZIP 以十六进制文本保存，不依赖运行时读取设计文档或被测实现生成期望。
通过只证明这组有限类型的内部纯计算；生产资格仍拒绝，不涉及安装、SQL、HTTP 或业务执行。
WAR 须包含 `module/composition` 的内核类，并排除全部作者、验证 ZIP 和测试向量。

[schema 2 生产制品](rules/schema-2-rule-artifacts.md)与
[安装、独立接受链路](rules/schema-2-installation-and-acceptance.md)的应用证据按改变的边界选择。调整 ZIP 读取器或安装协调器时分别可用：

```bash
mvn '-Dtest=ProductionRuleArtifactTest' test
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2SourceInstallationTest,Schema2OperationTicketTest' test
```

第一组核对有界 ZIP、安全路径、原始字节、生产编码及独立摘要向量，并保护既有组合语义。
第二组覆盖原包与接受事实分别不可改写、来源双摘要回读、独立操作指纹、实际协调器与 JDBC
仓储衔接、共享维护锁/未决标记、显式审阅、旧票据、故障回滚及提交结果未知后的原操作查证。
CLI 改动选 `Schema2RulePackageCliTest` 核对真实命令的稳定诊断。共享来源账本、连接目标、schema 和旧安装入口改变时，选对应的仓储、清单、验证器或 schema 1 测试。
安装、维护证据和票据测试依赖 POSIX owner-only 权限与 `SecureDirectoryStream`，须在支持这些
能力的 Linux/WSL 文件系统运行；纯 ZIP/组合测试另按自身前提执行。原生 Windows provider
不支持这些目录能力，相关安装测试可能在建立夹具或打开安全目录时失败，不能据此宣称
安装链路通过，也不能把省略相关测试后的结果称为完整回归；应记录平台限制并在支持环境重跑。
来源容量用例在 256 MiB 测试堆中保留前像，安装接近 raw/expanded 各 8 MiB 总限额的最后一个包，
核对完整后像，并证明下一包在 DML 前拒绝；这不替代真实驱动的容量与时限验收。
来源 schema/授权的静态测试和 JDBC 协议代理不替代真实 MySQL 的权限、约束、并发及持久化验收。
当前仅无内容扩展/兼容包可安装和接受；不完整基础包与尚未集成的机制仍失败关闭，接受事实
不等于可运行组合或完整规则发布资格。生产纯制品类进入 WAR，离线 CLI、来源 SQL、原包、
审阅导出、维护证据和全部测试向量必须留在 WAR 外。

[schema 2 来源到运行快照](rules/schema-2-runtime-snapshot.md)通过应用协调器、仓储及结构测试验证。仅改转存协调器及回读时可选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2SnapshotTransferServiceTest,JdbcSchema2RuntimeSnapshotRepositoryTest,ProductionSchema2StoredProjectionTest' test
```

测试使用真实协调器和 JDBC 仓储，以及具有事务前像的 SQL 代理；独立固定向量核对实际类型化
回读的组合、最终规则与完整覆盖链。覆盖来源事务关闭后才获取运行连接、守卫为首条运行 SQL、
精确恢复绑定、逐表写入失败、回读篡改、幂等冲突、提交结果未知和隔离后的无来源查证。
有限基础包仅作为账本完整性测试材料；测试不通过离线安装资格门，也不授予游戏执行资格。
V021/V023 静态结构与授权测试不能代替真实 MySQL 迁移、权限、锁、连接池或持久化验收。
WAR 保留运行仓储、协调器和生产组合类。离线核验通过仓库迁移源和 `target/classes/db/migration/` 中的
V021/V023 资源进行。来源 SQL、运行迁移、外部原包及测试向量均在 WAR 外。

[schema 2 工具组合与运行材料](rules/schema-2-runtime-snapshot.md)的应用组合/读写改动可选择：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2ToolCompositionTest,JdbcSchema2ToolSnapshotTest,Schema2ToolSnapshotSchemaTest' test
```

固定三包向量在 `src/test/resources/schema2-tools/`，由独立作者字节、最终字段/链/摘要组成。
测试覆盖 37 项工具字段、跨模组条目、类型不匹配、候选去重、混合目录序号、真实查询行重建与
各新增表写入失败回滚。有限组合仍受安装资格门约束，不能将向量通过作为基础模组可用证据。

[schema 2 初始语言／工具授予](rules/schema-2-proficiency-grants.md)的完整来源链与结果有独立作者向量。使用标准库 oracle 针对固定样例复算原始 ZIP、逻辑、组合、最终字段、完整链及摘要：

```bash
python3 src/test/resources/schema2-grants/oracle.py
```

它默认只做逐字节核对，适用输入由该脚本的固定向量范围决定。应用解释和计划服务改变时，可选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2GrantCompositionTest,Schema2InitialProficiencyRulesTest,Schema2InitialCharacterPlanServiceTest,Schema2InitialCharacterPlanIntegrationTest' test
```

来源、快照或结构改变时，再选 `JdbcSchema2CharacterSourceRepositoryTest`、`JdbcSchema2GrantSnapshotTest` 或 `Schema2CharacterGrantSchemaTest`。应用测试覆盖真实来源/父关系、完整槽身份、跨模组目标、原子授予覆盖、
固定与选择授予、禁用与重复选择、源类型化行和原包不一致、运行行/历史损坏以及事务失败。
完整链路测试通过实际源仓储、转存协调器、运行仓储和 `JdbcSchema2InitialCharacterPlanner`，核对手写的八条授予事实，
并证明源连接关闭或不可用后只读计划不访问来源、不提交调用方事务、不写业务数据。
改动共用 ledger、快照或迁移清单时，补选上面的原包安装、转存、工具及 schema 测试；
V004/V024 静态与 JDBC 代理证据不替代真实 MySQL 约束、触发器和权限验收。
WAR 保留初始计划服务和类型化仓储。来源 V004 与运行 V024 由离线迁移核验读取，作者包、
oracle 和全部测试向量也留在 WAR 外。

[语言分区链路等价核验](rules/language-partition-equivalence.md) 的应用安装/读写改动可选：

```bash
mvn '-Dtest=LanguagePartitionEquivalenceTest,CharacterCatalogChainTest' test
```

它从应用的固定作者夹具经过生产离线安装、源只读适配、运行镜像写入和指定身份读取，在每个边界
对照独立六字段矩阵、类型化投影和受审 canonical 字节向量，避免源与镜像共同遗漏仍让往返
相等通过。覆盖内容/排序变化、同键不同快照、读取损坏和调用方事务回滚；源断开后的镜像
读取只证明本分区不访问源库。事务 JDBC 代理不连接真实数据库，不能替代
另行授权的隔离实例中原样迁移、安装、驱动、权限与持久化验收，也不提供完整规则就绪资格。

真实语言/工具目录 JDBC 验收入口为 `tools/rule-packages/verify-language-jdbc.py --execute-disposable`，
须先获得隔离实例、迁移和测试写入授权。它先重验两组无数据库连接保护，再创建 tmpfs MySQL，
原样迁移并等待独立现场结构/权限审计；实际用例为 `LanguagePartitionJdbcIT`，测试跳过不算
成功。运行 `python3 -B tools/rule-packages/verify-language-jdbc.py` 可离线核对仓库内两条 Java
清单及对应 SQL 的文件集合、载荷摘要、结构和触发器范围。已审范围为运行 V001—V042 与来源 V001—V012。
增加迁移时需要先复核结构、触发器和权限，再更新验收范围。前置条件、账号、清理及故障注入边界见
[隔离 JDBC 入口](rules/language-partition-equivalence.md#隔离-jdbc-入口)。

[语言作者分区 reader](rules/language-author-package.md) 或应用编码变更可选择：

```bash
mvn '-Dtest=CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest' test
```

应用 reader 用固定作者夹具及独立字段矩阵核对严格 JSON/UTF-8/整数、Unicode 码点/NFC、资源上限、准确键与分类集合和排序。编码器变更另选受影响版本的 `ModuleCanonicalEncoderV1Test` 或 `ModuleCanonicalEncoderV2Test` 核对独立字节向量。证据范围为 18 项语言和 37 项工具分区，不产生完整规则摘要或
发布资格；目录/安装清单与源库事务验收见下面的离线安装边界。WAR 审计须确认纯模型/reader
类存在，作者 JSON、说明/许可包与测试向量均不在 WAR 中。

[离线语言安装](rules/offline-language-installation.md) 的无数据库定向命令（Linux/WSL）：

```bash
mvn '-Dtest=RuleArtifactTest,TicketStoreTest,SourceInstallationTest' test
```

它覆盖完整制品和独立指纹向量、持久票据与未决标记、单连接锁序、各写入点异常及影响行数
错误、实际查询结果被篡改后的整笔回滚，以及提交/回滚/查证失败的分类。JDBC 代理测试不证明
真实 MySQL 权限、触发器、并发屏障、响应丢失或介质断电耐久性；这些按操作说明在另行授权的
隔离实例上验收。安装仍只产生 DRAFT 语言/工具 PARTITION，不提供完整规则就绪或发布资格。
离线命令 `tools/rule-packages/rule-package.sh` 使用同一源码构建的 `target/classes`。连接 MySQL
的子命令另需提供外部 Connector/J。
WAR 必须排除 `com/dndtool/offline/` 全部类、安装工具、作者包、安装清单和测试向量。

[源语言分区读取](rules/source-language-reading.md) 的应用 JDBC/诊断改动可选：

```bash
mvn '-Dtest=JdbcSourceLanguageRepositoryTest,RuleDatabaseSchemaVerifierTest' test
```

它核对同一只读事务中的准确源库/账本、当前 PARTITION 证据、55 项独立字段矩阵、严格 JDBC
类型与 Unicode、有界固定 SELECT，以及失败时资源关闭和连接状态恢复。历史 COMPLETE 不得
替代当前证据；超量或坏行失败关闭，不从旧目录或作者文件补齐。连接池、真实驱动类型、快照
一致性和实际 SELECT-only 权限须另行授权在隔离 MySQL 实例验收。离线 DRAFT 分区读取和
独立账本核验使用同一源码构建的 `target/classes`。`JdbcSourceLanguageRepository` 继续随 WAR
打包。SQLite 规则加载由工作台的本地规则库处理。

[运行语言快照分区](rules/runtime-language-snapshot.md) 的应用仓储和 DDL 变更示例：

```bash
mvn '-Dtest=JdbcRuntimeLanguageSnapshotRepositoryTest,V019RuntimeLanguageSnapshotSchemaTest' test
```

它核对 UUIDv4 网络字节、永久身份冲突、跨快照拒绝、18 项语言分区的严格读取、实际 SELECT
回读与调用方事务所有权，并用事务代理验证插入、回读及后续调用方失败后的整笔回滚。V019
在仓库中保留原批准载荷，构建后也可从 `target/classes/db/migration/` 读取。结构和授权文件
另有静态合同测试。代理与静态测试不证明真实 MySQL
的 CHECK、触发器、FK、权限或持久化回滚；这些需在独立授权的隔离实例上完成。只有语言的
PARTITION 不接入新建、导入或执行视图，也不获得完整规则摘要或发布资格。

[工具熟练目录](rules/tool-catalog-partition.md)的组合快照使用 `JdbcRuntimeCharacterCatalogRepository`。
相关命令为 `mvn '-Dtest=CharacterCatalogChainTest,ToolCatalogSchemaTest' test`，核对 55 项双领域
内容、独立 canonical 字节、每个工具写点和后续调用方失败的整笔回滚、来源断开、错误类型及
跨快照拒绝。运行 V020 来自仓库及 `target/classes/db/migration/`，来源 V002 来自
`database/rules/migration/`。实际约束与授权仍须在隔离实例验收。

[离线规则源 schema](rule-source-schema.md) 的无数据库定向命令：

```bash
mvn '-Dtest=RuleSchemaMigrationsTest,RuleSourceSchemaContractTest,RuleDatabaseSchemaVerifierTest' test
```

它检查独立源清单/严格 UTF-8/载荷摘要、表与授权结构防回归，以及完整只读账本比较、准确默认库、
有界查询和 JDBC 资源关闭。SQL 结构测试不能替代真实引擎对 CHECK、触发器、FK、Unicode 和
权限/并发的验证。来源迁移和运行迁移均留在 WAR 外。改变共同迁移清单时，以各链的中央清单测试保护既有批准摘要，并核对运行 V001—V042 和来源 V001—V012 的完整清单（两链共 54 份迁移源）。
规则源 disposable 验收须先通过测试连接两入口保护的无 DB 回归，再单独授权隔离实例；
不得借临时表 IT 入口对部署源库试写，也不得扩大该 IT 库及账号用途。步骤见该 schema 合同。

[规则发布身份合同](rules/module-release-identity.md) 的纯边界定向命令：

```bash
mvn '-Dtest=BuiltinModuleReleaseRegistryTest,BuiltinModuleHashManifestTest' test
```

这组测试分别验证精确 ASCII 身份与长度、四种解析状态、描述符格式/算法/状态/摘要组合、
默认及重复拒绝、独立批准摘要查询。正整数格式结构不等于支持；expected 查询不等于来源
状态、摘要相等或完整内容验证。Java `int` 测试不证明 JSON 原 token 的类型和词法已被验证。
编码、存档门或相邻消费者行为受影响时，按差量选择 `ModuleCanonicalEncoderV1Test`、`ModuleCanonicalEncoderV2Test`、
`CampaignArchiveCapabilityBoundaryTest` 或具体消费者的拒绝回归；
生产代码变化还须按第 6 节审计 WAR。这些纯测试无需启用 MySQL 集成测试。

完整运行链的数量、顺序、脚本名和批准摘要由 `SchemaMigrationsTest` 集中核验；独立来源链由
`RuleSchemaMigrationsTest` 集中核验。领域测试检查自身迁移及其前置合同，不复制其他领域的整链
总数，也不假定自己的迁移永远位于链尾。普通去重仍需保留当前有效行为和独立失败路径的覆盖；
采用新模型时则按新支持范围协调替换测试，不为保留历史断言而强制保留旧代码。

- 当前迁移链中 V001—V018 的文件与账本必须匹配；V011 的角色 DRAFT 目录、V012 的一级创建 schema/profile、
  V013 的升级/生命骰 schema/profile、V014 的职业特性/恢复矩阵、V015 的多职业/ASI/专长框架、
  V016 的初始熟练基线、V017 的 format 2 状态来源及 V018 的多职业施法贡献由清单摘要、精确计数、
  关系、profile 语法和 canonical-v2 负例测试保护。
- 当前 `ImmutableLegacyMigrationContractTest` 固定 V001—V010 的现有清单；采用新的初始化链时须连同清单和验证器一起调整，不把测试类名视为永久产品要求；
  `DeferredPublicSurfaceExclusionTest` 只保护永久延期的公开访问面，不再禁止新增 Host 规则领域。
- 规则测试覆盖未知/重复/未发布数据、稳定键引用、范围、NFC、UTF-8 无符号排序、精确小数、规范字节流与内容摘要。
- 当前支持的模组，其战役冻结值、实际目录摘要、发布摘要和应用清单必须一致，否则失败关闭；这不要求后续版本保留同一模组或读取器。
- 目录、运行模型、canonical/archive 投影和对应测试可协调替换，不要求为旧 RELEASED 或 DRAFT 行为保留兼容层；保留数据库或重建的外部边界见[数据库合同](database.md#迁移合同)。
- 内部 DRAFT archive format 2 的投影按各领域合同验证；当前 SQLite 的完整战役使用 `RULE_BOUND_CAMPAIGN` format 3。发布规则内容或改变支持范围时，核对[完整规则目标的发布条件](rules/srd-5.1-complete.md#6-完成判定)中受影响的契约和证据。

一级创建的定向验证包括 `LevelOneRuleProfileTest`、`LevelOneCharacterRulesTest`、
`LevelOneCharacterCreationServiceTest`、`JdbcLevelOneCharacterCreationRepositoryTest`、
`V012LevelOneCharacterCreationSchemaTest` 和 `LevelOneCharacterRuntimeGrantsTest`。它们证明预览
无权威写入、非法选择被拒绝、事件尾变化导致 stale preview、事务故障不留下部分角色，并且
DRAFT 发布门保持关闭。

[角色语言／工具授予](rules/character-proficiency-grants.md)的应用写入示例：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=LevelOneCharacterCreationServiceTest,JdbcCharacterProficiencyGrantRepositoryTest' test
```

写入测试覆盖固定和选择授予的准确父来源/槽、完整候选检查、幂等与写入失败回滚。卡片、内部存档、直接恢复和结构改变时，按影响加上其对应测试。V022 静态前置检查要求旧 DRAFT 创建快照为空；
这些测试不执行迁移，不证明真实 MySQL 触发器或权限，也不将该旧目录链路视作 schema 2 业务运行。
WAR 保留授予模型与消费者。离线结构核验使用运行 V022，账号增量见[授予权限](../database/grants/character-proficiency-grants.sql)。

升级的定向验证包括 `AdvancementValueProfileTest`、`LevelAdvancementRulesTest`、
`LevelAdvancementServiceTest`、`JdbcLevelAdvancementRepositoryTest` 和
`V013LevelAdvancementSchemaTest`。它们覆盖逐级 profile
畸形、跳级、资源状态不一致、固定值/服务端掷骰、stale row version、事务失败和禁止部分写入，
并证明过期状态或幂等重放不会消耗随机性。

成长与恢复区间的共用模型使用 `AdvancementValueProfileTest`、`ResourceRecoveryProfileTest` 和
`ModuleCanonicalEncoderV2Test` 验证结构化区间与文本共用校验、集合不可变、熟练加值逐级拒绝
坏值、恢复方式组合及区间完整性。涉及这些纯模型时保留 canonical v1 独立向量，并按受影响的
调用方选择一级创建和升级 Service 测试。标准休息的执行和恢复按下文 schema 2 休息用例核验。

职业特性的定向验证包括 `ClassFeatureRulesTest`、`V014ClassFeatureCoverageMatrixTest`、
`V014ClassFeatureLifecycleSchemaTest`、`CharacterAdvancementChoiceRulesTest`、
`V015MulticlassAsiFeatSchemaTest`、`V016StartingProficiencyBaselineSchemaTest` 与
`V017CharacterArchiveV2OriginSchemaTest`。V018 的十二职业贡献与批准载荷由
`V018MulticlassSpellSlotFoundationSchemaTest` 核对。旧 DRAFT 目录拒绝未知贡献标识由
`ModuleCanonicalEncoderV2Test` 验证。format 2 的角色投影另由
`CampaignArchiveV2CharacterStateTest` 和 `JdbcCampaignArchiveV2CharacterStateImportRepositoryTest`
覆盖规范写出/严格读取、稳定事件闭包、预览、调用方事务、失败回滚及无 commit/rollback 的
Repository 合同。参数化矩阵逐职业覆盖全部 236 个职业/子职业特性。目录和成长测试验证
自动、裁决、阻断三态、子职业选择边界和畸形冻结目录。

辅助验证 SQL 只读，位于 [database/verify](../database/verify/)；静态测试不会把这些文件的存在当作数据库已验收证据。

### 4.1 完整 SRD 5.1 目标的测试矩阵

下列维度用于定位新增机制实际影响的交互。应用机制及事务按受影响领域选择 JUnit；交付模组的声明和可见表现另用作者向量和成品场景验证：

- 角色构筑、升级、多职业、专长和派生值重算；
- 通用骰子、优势/劣势抵消、攻击、暴击、伤害分量、抗性、易伤和免疫；
- 先攻、轮次、回合、动作经济、反应窗口、移动、距离、范围和掩护；
- 0 HP、昏迷、稳定、死亡豁免、治疗、临时生命值和即死；
- 状态、持续时间、重复豁免、专注、叠加、免疫、到期和解除；
- 已知/准备法术、法术位、多职业施法、目标、区域、升环、资源消耗和开放式 DM 裁定；
- 武器、护甲、负重、货币、消耗品、充能、同调和休息恢复；
- 怪物动作、反应、恢复能力、施法、抗性/免疫、CR/XP 和遭遇关系；
- 受支持的完整文件状态往返、不支持格式的明确拒绝和失败时禁止部分导入。

随机规则使用可注入且可审计的随机源测试边界和候选选择，但生产结果仍由服务端安全随机源
生成。时间、回合和触发测试必须固定明确时钟或顺序，不能依赖测试执行速度。新增机制的目录、解释、状态、事务、页面入口和恢复证据按实际采用范围核对，并提供可以独立执行的模组行为用例。

## Schema 2 检定、装备与健康作者来源验证

[作者来源合同](rules/schema-2-foundation-authoring.md)的应用组合与读取器分别选择。只修改组合器时可选：

```bash
mvn '-Dtest=Schema2CheckCompositionTest,Schema2EquipmentHealthCompositionTest' test
```

组合测试覆盖四类封闭定义、算法/页码/引用类别、完整来源链、启停、有符号修正和严格清空，
包括负整数原词法及 `-0`/小数/指数/强制转换拒绝、合法最终值掩盖非法原始声明的反例。
来源映射改动选 `JdbcSchema2CheckSourceRepositoryTest` 或 `JdbcSchema2EquipmentHealthSourceTest`，从真实作者 ZIP 核对 typed 行，覆盖未选中包、完整引用与操作、缺失/多余/损坏数据和
各写点失败；仓储保留调用方事务所有权。结构测试保护来源 V006/V007、最小授权和独立批准链。
这些是作者、组合与来源证据，不包含新运行镜像、检定/装备/健康业务、骰子或角色历史验收。
运行镜像与事实存储使用后面的[持久化验证](#schema-2-检定装备与健康镜像和事实存储验证)，
真实命令与恢复语义使用[角色基础验证](#schema-2-检定装备与健康验证)；
已实现的成长/休息验证继续使用下节入口。

WAR 包含严格作者/组合和真实来源读取类；来源 SQL、离线安装器、作者包及测试向量排除。
来源 V001—V012 的 53 张表、159 个触发器及 43 张类型化材料表须在另行授权的隔离 MySQL
实例验收；代理和静态结构测试不能替代真实 CHECK、FK、权限、锁或持久化事实。

## Schema 2 角色成长、资源与休息验证

[内部成长合同](rules/schema-2-character-statistics.md)和[标准休息](rules/schema-2-character-rest.md)的服务与规则消费者改动可选：

```bash
mvn '-Dtest=Schema2CharacterProgressionRulesTest,Schema2GrowthRulesTest,Schema2CharacterAdvancementAndRestServiceTest' test
```

独立固定规则字段及完整覆盖链在 `src/test/resources/schema2-progression/`。组合器、来源读取或成长/休息仓储改动分别查 `Schema2ProgressionCompositionTest`、`JdbcSchema2GrowthSourceRepositoryTest`、`JdbcSchema2CompleteGrowthStateTest` 或 `JdbcSchema2RestStateTest`，按共享差量补相邻应用消费者。迁移清单
变化沿用下节 schema 与诊断测试。服务测试使用实际来源、计划、快照和状态仓储的 JDBC
代理，覆盖失效前拒绝、随机性顺序、幂等、写入失败回滚、源库断开和直接恢复，不替代真实
MySQL 外键、触发器、权限或部署证据。成长/休息集成测试还覆盖子职业、ASI、专长、多职业、
逐次生命骰、活动中断、长休收益间隔及完整历史恢复；不声称通用效果或多角色生命周期已完成。
WAR 保留成长/休息服务、规则和类型化仓储。离线结构核验使用运行 V027 与来源 V005。
迁移、测试 oracle 和向量均留在 WAR 外。

## Schema 2 检定、装备与健康镜像和事实存储验证

[运行快照](rules/schema-2-runtime-snapshot.md)和[角色事实存储](rules/schema-2-character-fact-storage.md)按快照、历史和结构边界选择。检定读回及历史改动示例：

```bash
mvn '-Dtest=JdbcSchema2CheckSnapshotTest,JdbcSchema2CheckHistoryTest,Schema2CheckHistorySchemaTest' test
```

快照测试按独立字段和来源期望核对四类完整规则、混合序号、字段链、负修正/清空、实际读回、
缺失/多余/坏类型、跨快照以及各写点故障。历史测试使用真实叶仓储及事务 JDBC 代理，
核对封闭输入/结果、有效骰模式、完整装备健康前后状态、有序原骰、追加读回、直接恢复、
孤儿与类型化行缺失、结构/邻接/预算限制及整笔回滚；这些测试保存已经准备的事实，不执行新业务。

运行链为 V001—V042，来源为 V001—V012，共 54 份迁移源。WAR 保留镜像/事实仓储，排除两条
迁移链、离线写入工具、测试向量和真实存档。Check/Equipment/Health
消费者、请求幂等和 RuntimeService 命令使用下节验证；仓储测试本身只作为存储事实的证据。
现有成长/休息测试保护新增字段的中性保留，DRAFT、Host、archive 2 和安装门保持。
静态 SQL/JDBC 代理不代替真实 MySQL 的 CHECK、生成列、外键、触发器、权限与持久化验收。

## Schema 2 检定、装备与健康验证

[角色基础合同](rules/schema-2-character-foundations.md)使用真实准备、来源、快照、角色事件和直接恢复。
应用新增作者类型或改变读写映射时，按影响选择 `Schema2CheckCompositionTest`、`Schema2EquipmentHealthCompositionTest`、
对应 `JdbcSchema2Check*` / `JdbcSchema2EquipmentHealth*` 来源、快照、历史和 schema 测试；
共享组合器改动同时覆盖原语言/工具/授予/成长向量及 `ProductionSchema2StoredProjectionTest`。
独立向量位于 `src/test/resources/schema2-checks/` 和 `schema2-equipment-health/`。

检定消费者变化的定向命令：

```bash
mvn '-Dtest=Schema2CharacterCheckRulesTest,Schema2CharacterCheckServiceTest' test
```

装备、生命或共享运行服务改动选择同领域的规则和服务测试，覆盖真实授予去重、装备产生的实际骰模式、手位/AC、穿脱时间、环境效果原骰、临时 HP、零 HP/即死、
死亡豁免、恰抵达稳定恢复期限、长休清临时 HP、跨命令状态保持，以及缺失/损坏子表、幂等、随机性前拒绝、
故障整体回滚与直接恢复不重掷。未知运行时故障遵循共同守卫冻结，不能把回滚视作允许盲目重试。
WAR 保留基础消费者。运行 V028—V031 供离线结构核验使用，来源和运行迁移、测试向量与
离线写入工具均留在 WAR 外。
这些测试不授予真实 MySQL、部署、Host、战斗回合或完整初始化验收。

## 共同业务事务与初始化准备验证

[共同事务合同](rules/business-write-transactions.md)改变共享接纳边界时，从清单选择受影响的事务 owner 及调用方叶仓储测试，并保留现有版本、幂等、随机性和失败回滚断言。永久守卫和连接所有权变化可选：

```bash
mvn '-Dtest=BusinessWriteTransactionsTest,BusinessWriteGuardSchemaTest' test
```

故障测试通过独立子 JVM 验证永久失效门，不提供重置生产阻断状态的测试后门。覆盖第一条 SQL
锁序、并发互斥、裸连接拒绝、活动 legacy 和材料残留阻断、commit/rollback/连接归还故障、
明确提交与 UNKNOWN 的区别，以及隔离后绑定原操作的只读查证。改变材料准备或待查证事实时，加 `Schema2InitializationPreparationServiceTest` 或对应初始计划仓储与集成测试。准备测试从真实来源重读并
对照固定计划期望，覆盖同键不同计划、孤立/损坏事实和每个写点失败后无部分快照、计划及收据。
完整初始化缺少领域、业务历史、容量及生命周期时必须继续拒绝。
计划指纹的独立固定期望通过 `python3 -B src/test/resources/schema2-grants/expected/initial-plan-fingerprint.py` 复算。该脚本使用标准库、既有独立规则摘要及手写的来源、槽、选择和授予事实，其固定摘要由准备测试直接断言。

迁移清单变化选择两条链的中央清单测试及相关 schema 诊断测试；涉及工具分区或角色目录合同
时，再加入 `ToolCatalogSchemaTest` 或 `V011CompleteCharacterCatalogDraftTest`。
PARTITION 调用方采用共同 Session 后，还应覆盖语言、工具镜像及链路测试。
`python3 -B tools/rule-packages/tests/verify-language-jdbc-test.py` 核对已审迁移链，
验证未知迁移在外部操作前被拒绝。模拟回放检查原样 SQL、执行身份、默认数据库和授权编排。
实际驱动、现场权限和持久化行为需要在另行授权的隔离实例中验收。
候选 WAR 保留共同 Session、初始准备服务与仓储。离线迁移核验检查 V025/V026，测试工具和两条迁移链均留在 WAR 外。

## 5. Web 与安全测试

覆盖精确 loopback/Host/scheme/port、伪造 `X-Forwarded-*`、方法限制、Session Cookie、CSRF、Origin/Referer、Fetch Metadata、安全响应头、epoch、对象版本、幂等键和摘要。错误入口应返回一致的普通错误且不能到达业务写入。

验证 Host 写请求时，先取得会话和令牌。仅以 `_csrf` 参数提交正确令牌应返回 `403` 且无业务写入。
以 `X-CSRF-Token` 请求头提交时，分别检查合法命令成功，以及额外的业务参数仍按入口合同被拒绝。

客户端权威边界测试应确认浏览器不能指定骰点、选中骰、派生修正、最终结果、效果算法、
数据库身份或版本推进。相应领域实现后，还必须确认浏览器不能指定服务器候选集合之外的
职业/专长/法术/目标，不能覆盖先攻顺序、伤害调整、资源消耗、状态持续时间或 DM 裁定类型。

## 6. WAR 审计

生产代码或打包资源变化后，记录 SQLite 候选 WAR 的检查结果：

- 字节数、SHA-256 与 ZIP 条目数；
- 必需的运行类、Host 与玩家 HTML/JavaScript，以及 `WEB-INF/web.xml` 的 SQLite 路由、监听器和过滤器；
- SQLite 结构声明、领域引擎、严格 codec、仓储和 SQLite JDBC，以及 `WEB-INF/classes/storage/rules/` 下五个准确内置 ZIP 和 `bundled-members.tsv`；
- 禁止项包括两条 MySQL 迁移链的 SQL、Connector/J、退役 MySQL Web 入口及资源、测试启动器和测试夹具；
- `WEB-INF/classes/com/dndtool/persistence/` 下没有 `SchemaMigrations.class`、`RuleDatabaseSchemaVerifier.class` 及两类各自的全部内类；
- 私钥/证书秘密、凭据、Tomcat 外部配置、日志、备份、真实存档、结果捕获与本地路径文件名的匹配数；
- 生产资源和示例配置差异中的秘密扫描结果。

同一源码的 Maven 构建通过 `target/classes` 交付离线工具、上述迁移及核验类，并在
`target/classes/db/migration/` 保留全部 42 份运行迁移。运行迁移源保存在
`src/main/resources/db/migration/`，来源迁移源保存在 `database/rules/migration/`。
`SchemaMigrationsTest` 检查 classpath 中的运行迁移及清单，`RuleSchemaMigrationsTest` 核对
仓库中的来源迁移，`RuleDatabaseSchemaVerifierTest` 覆盖独立的只读账本核验。

从候选 WAR 的 `WEB-INF/classes/storage/rules/` 取出
`{srd51-runtime,srd51-characters,srd51-magic,dm-runtime-house-rules,dm-runtime-check-variant}.zip`，逐个与当前交付 ZIP 比对原字节及许可。工作台的标准预设依次使用 `srd51_runtime@5`、`srd51_characters@5`、`srd51_magic@5` 和 `dm_runtime_house_rules@9`。`dm_runtime_house_rules@10` 随 WAR 提供为自定义导入候选，作者场景可用它占用第四个位置。运行合同见[随附规则材料](storage-vitality-loop.md#有限-schema-2-规则材料)。

用同一构建 `target/classes` 中的 `com.dndtool.service.storage.BundledRuleManifestGenerator` 对取出的五个 ZIP 重算清单。运行时类路径包括 `target/classes` 和从候选 WAR 取出的 `WEB-INF/lib/*`，参数依次为 ZIP 目录和新清单路径。将新清单与 WAR 中的 `bundled-members.tsv` 逐字节比对。
用 `CampaignRuleBundleIsolationTest` 检查专属包或共享包损坏时的建档、重开和原文件保留；
用 `StorageHttpIntegrationTest#damagedBundledCandidateIsListedButRejectedBeforeAnyRuleLibraryWrite` 检查候选状态和导入写入边界。
变体使用感知进行运动检定，将定额直接伤害设为 3，并将自定义工匠背景的固定语言改为侏儒语。
这些数值、属性映射和授予属于项目家规。见[家规作者包](../rule-packages/srd51-runtime/dm-runtime-check-variant/package-guide.md)。

核对 WAR 内 `WEB-INF/classes/com/dndtool/` 下 `service/` 中的 `Schema2CharacterRuntime`、
`Schema2CombatHistory`、`Schema2CharacterRequest`、`Schema2CombatRequest`、`Schema2PreparationIdentity`、`Schema2FeatureSupport`、`storage/CampaignRuleContext`、
`storage/CampaignRuleSupport`、`storage/LocalRuleLibrary` 和 `persistence/storage/` 中的 `LocalRuleLibraryLedger`。
旧 `storage/CampaignRules.class` 及独立 HP 表/创建入口不应保留。
核对迁出的 JDBC 仓储嵌套领域类型均已排除，包括 `JdbcSchema2CharacterRuntimeRepository$Command.class`、
`JdbcSchema2CharacterRuntimeRepository$State.class`、`JdbcSchema2CharacterRuntimeRepository$History.class`、
`JdbcSchema2CombatRuntimeRepository$History.class` 和 `JdbcSchema2RuntimeSnapshotRepository$Identity.class`。
仍在用的 JDBC 仓储类是持久化适配入口。
作者场景通过 `CampaignRuleContext.initialPlan` 取得角色计划，WAR 须保留
`Schema2InitialCharacterPlanService$InitialPlan` 和 `Schema2InitialCharacterPlanService$Plan`。
检查包内没有 `Schema2CharacterRuntimeService$Request`、`Schema2CombatRuntimeService$Request`、
`Schema2CharacterRuntimeService$Material` 或 `Schema2InitialCharacterPlanService$SnapshotReader`。
`rule-packages/` 作者源、外部导入原包及离线安装写入工具仍不得进入 WAR。

WAR 审计通过不代表已经部署。

### SQLite 结构、完整文件与运行验收

`StorageApplicationListenerTest` 和 `StorageHttpIntegrationTest` 覆盖默认根、无 JNDI 的生产入口及显式无效根。用 `CampaignRuleBindingIntegrationTest` 验证标准战役在新根直接重开、自定义绑定仍需本机接受。`CampaignStorageServiceTest` 验证单份损坏存档隔离、未改动行保持原位及提交故障恢复。

当前有限 SQLite 模型见[运行合同](storage-vitality-loop.md)。标准新建测试只提交战役名称，
并核对标准存档可在空规则库的存储根打开且无接受写入。自定义组合测试覆盖移机时缺少原包、
已导入但未接受、准确接受后的打开。结构与真实 SQLite 的定向入口是
`StorageSchemaCatalogTest`、`SqliteSchemaTest`；业务/生命周期见 `CampaignStorageServiceTest`、
`CampaignStorageLifecycleTest`。标准和家规的组合、候选属性、角色初始授予和检定分支使用[独立成品场景](#模组作者向量与成品行为)。改动运行绑定或恢复时，`CampaignRuleBindingIntegrationTest` 经完整文件重开及残留恢复核对原骰、状态和根子历史，并覆盖未知机制、错绑材料、坏未选分支、版本拒绝、
晚写回滚和提交未知后的幂等重放；外部第五包改变真实检定与伤害，并覆盖缺库活动/恢复、
移机打开缺原包/未接受拒绝与准确接受后的重开。`LocalRuleLibraryTest` 使用临时真实 SQLite
核验导入/接受分离、原字节身份、幂等冲突、发布中断、提交未知、重启查证和坏材料阻断。
`CampaignRuleSupportTest` 验证外部内容/覆盖/槽 owner、完整机制闭包和不可获得的 BLOCKED 专长。
这些测试仅使用隔离临时根，不触碰真实战役或来源数据库。`CampaignDomainEngineTest` 验证无连接纯领域后果；
`CampaignCheckEffectsTest` 验证两分支全部预检、类型化有序后果和共享事务；
`CampaignManualIntegrationTest` 覆盖自由卡字段/职业/熟练、MANUAL/SAVING_THROW、有序后果、
混合网格、DM 裁定模式、共同回滚，以及完整文件中初始/当前事实的分别保留；
`CampaignSpaceTimeBoundaryTest` 另核个人稳定恢复期限、重新安置版本、空间投影损坏和准确请求收据；
`CampaignMonsterSpaceIntegrationTest` 核受伤且已消耗动作额度的怪物跨场景保留与完整文件往返。
`CampaignSpacesIntegrationTest` 负责双场景各有 pending、切换焦点冻结、过期焦点零 RNG/写入、
FINAL 重开后只继续原后缀，以及裁定旅行、源时钟、目标安置和个人经历时间；
`CampaignDomainRootValidationTest` 拒绝根历史重组、未来节点引用、物品来源/归属伪造及
中间时钟分歧；`CampaignDomainRecoveryTest` 覆盖初始化已提交、业务提交响应未知时的
显式恢复，以及对保留导入输入的完整核验，断言不重初始化或重播；
`CampaignDomainsIntegrationTest`、`CampaignDomainsCombatIntegrationTest` 验证共享 SQLite 的
完整聚合、原骰/历史/未决动作与文件重开。独立基础文件字节向量和严格读取见
`CampaignFileVectorTest`、`CampaignCodecTest`。`CampaignStorageCrashTest` 在独立 JVM 中于 DDL、初始化提交、
业务更新/提交和 P2 后强制中止，再由新服务实例查证恢复。真实 loopback HTTP、模块选择、
治疗、保存结束、文件重开和重启恢复见 `StorageHttpIntegrationTest`。
测试只写临时目录，不连接 MySQL；HTTP 测试独占本机 8080，不能与人工验证实例同时运行。
`CampaignPreparationTest` 保护准备目录归属、部分输入/分配、完整身份不符、未知材料和
恢复再次中断；`CampaignStorageCrashTest` 还在独立进程中覆盖创建目录、锁、零字节暂存、
半写、硬链接发布和正式运行发布等前缀。`CampaignWindowsFilesystemTest` 只在原生
Windows JVM 执行，核验 NTFS ACL、junction 拒绝及跨进程运行锁；Linux 跳过此类不构成
Windows 通过。`CampaignArcaneRecoveryIntegrationTest` 覆盖短休资格、真实位数与预算、
每日限制、法术书、幂等、晚写回滚和保存/END/重开。
`StorageApplicationListenerTest`、`StorageReadinessTest` 验证配置优先级、显式空根失败关闭、
运行就绪、残留需恢复及活动库损坏阻断。独立保存文件损坏时只将该文件标为不可用。

系统临时目录也必须在 Git/WAR 树外；若环境在临时根放置 `.git` 或 `WEB-INF` 标记，
应用会拒绝其下的运行。此时为 Maven 和测试 JVM 同时指定合格的
`-Djava.io.tmpdir=/absolute/test-temp` 与
`'-DargLine=-Djava.io.tmpdir=/absolute/test-temp'`（目录须已存在），保留目录保护，
不删除未知标记或改动真实仓库。已有 `argLine` 选项须合并保留。

例如，仅改规则库接受和冻结绑定时可选：

```bash
mvn '-Dtest=LocalRuleLibraryTest,CampaignRuleBindingIntegrationTest' test
```

需要已启动的隔离应用供人工或浏览器验收时，可以准备本地实例（Linux/WSL、Java 21+，8080 必须空闲）。这个准备命令用 Maven 构建启动 classpath；已有候选实例可直接运行浏览器工具：

```bash
storage_test_root="$(mktemp -d /var/tmp/dnd-storage-XXXXXX)"
bash tools/storage/run-local.sh "$storage_test_root"
```

脚本依次显示环境检查、编译与依赖准备、应用启动。首次编译可能需要几分钟，Maven 阶段
和原始错误保持可见；出现应用就绪地址后再打开浏览器。启动预备不等于运行测试。

打开 `http://127.0.0.1:8080/host/storage`，输入名称直接用标准规则开团。验证自定义组合时，再导入、接受并选择所需原包。
建立世界节点和路线，安置规则 PC/NPC，在焦点节点验证装备、检定和健康；记录 DM 本次旅行
耗时并转移选中的主体，核对源节点推进、目标节点原钟与角色累计经历。创建规则场景，从
锚点入场后执行所选战斗或施法动作。创建独立自由卡，编辑基础字段、职业等级/熟练与物品，
执行 MANUAL/SAVING_THROW 和两分支有序后果，并核对 DM 场景内的混合占位和探索/战斗模式。
分别打开常规与家规组合的战役，核对运动检定候选属性和工匠背景的固定语言随战役切换。
让两个场景各保留一个待继续动作，再保存、结束并重开，核对焦点、全部时钟、初始/当前卡、
原骰/资源、根/子历史和两个待继续点。验证下载后上传不会自动激活，
同 fileKey 不覆盖，受引用保存点不能删除；重启必须显式恢复，坏文件/投影不得部分激活。
Ctrl+C 仅停止这个隔离验证实例；未 END 的运行下次启动必须显式恢复。先 END，再停止
进程，保管需要的 `saves/*.json`，最后只删除本次明确生成的测试根。脚本不部署 WAR，
不修改现有 Tomcat；它用测试 classpath 装配同一生产 Servlet/Filter。

可选浏览器自动验收使用独立安装的 Playwright/Chromium，浏览器环境需有中文字库；
不向 Maven 或 WAR 加浏览器依赖。

验证自由卡检定草稿时，用 `tools/storage/run-local.sh` 启动全新隔离存储根，再向浏览器脚本传入同一目录。

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-manual-check-draft.cjs --fresh-root /absolute/disposable/storage-root
```

脚本在同一标签页保存并结束战役 A，再进入战役 B，核对 B 发出的检定请求中成功、失败两分支及目标版本。它也验证同一运行内同步状态、刷新页面、保存和连续检定会保留草稿，以及目标失效后必须重新选择才能提交。

验证 DM 三栏工作台时，使用[玩家参与测试启动器](#玩家参与原型验证)和全新一次性私有根，以便左栏从真实玩家接口读取席位。待启动器就绪后，在另一终端使用同一根运行：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-workspace.cjs /absolute/empty/workspace-evidence \
  --fresh-root /absolute/disposable/player-store
```

脚本通过实际表单创建角色和自由卡，在已有战斗行动者与攻击目标的情况下翻页，核对选择值和服务器刷新后的卡片身份。桌面及 390px 截图和滚动、焦点、请求检查的结果写入证据目录。详情见[工作台使用说明](storage-vitality-loop.md#战役工作台)。

聚焦短冒险体验时，先用 `tools/storage/run-local.sh` 启用全新临时根，再执行：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-adventure-smoke.cjs /absolute/empty/adventure-evidence \
  --fresh-root /absolute/private/test-root
```

`--fresh-root` 必须是本次启动器实际使用的可丢弃空白根，不能指向真实战役。脚本通过真实
表单建立 PC/NPC、取得并装备武器、创建相邻地点、检定及分支后果、处理随机先攻和借机
反应、攻击、保存结束，关闭浏览器会话后精确打开 FINAL，再继续一个回合。核对完整初始/
当前事实、原骰、历史与旧文件字节；INITIAL、手动保存、最终保存均按返回的准确身份保留。
同时断言每次提交的忙碌反馈、普通成功操作不重复读取整份状态、epoch 改变后刷新 Session，
并输出动作耗时、宽屏/390px 截图和 `adventure-browser-report.json`。它只关闭浏览器会话，
应用进程重启及残留恢复使用下方独立恢复验收；两种关闭不能混称。

较广的领域覆盖使用主浏览器验收：
先用 `tools/storage/run-local.sh` 启动**另一个全新一次性根**，再在另一终端运行：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-smoke.cjs /absolute/empty/screenshots
```

默认使用与当前操作系统匹配的 Playwright Chromium。要使用已安装的 Chromium/Edge，
可将 `DND_BROWSER_EXECUTABLE` 设为该系统下浏览器可执行文件的绝对路径；脚本先验证
文件存在。Windows 原生 Node 必须使用 Windows 浏览器，不能指定 WSL Linux executable。

脚本拒绝已有运行、残留或保存点的根。当前表单验收核对规则卡和自由卡、地点及明确安置、
服务器检定和成功/失败分支的 HP/物品/消息后果、优势豁免、物品归档/恢复、局部网格与
规则战斗继续。完整文件重开应保留角色、物品、全部场景和历史，下载、删除后可按原 fileKey 导入。
脚本输出宽屏/390px 截图、横向溢出和 JavaScript/CSP 检查结果 `browser-report.json`；
成功时所有验证运行均已结束。奥术回想场景用真实法术书、资源与休息表单选择恢复，验证
未满短休、超预算、同日重复拒绝且无状态/历史变化，空选择可放弃，合法收益和使用日显示
正确，保存、END 与重开后全部根/子历史及位数保持一致。
失败保留报告与测试根供检查，不递归删除未知文件。浏览器构建必须适合所用操作系统。

在同一隔离应用上继续执行装备、战斗继续与生命法术验收：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-combat-smoke.cjs /absolute/empty/combat-screenshots
```

补充脚本只接受主验收留下的已结束夹具，另建规则卡战役；通过真实表单取得/装备武器、
触发借机攻击窗口并 PASS/继续、攻击、使用 Life Cleric 的 Cure Wounds。核对服务器原骰、
HP 和法术位共同变化，以及 FINAL 重开后事实不变。随机先攻和命中按实际结果处理，
不通过注入固定骰子绕过生产路径。报告、截图和完整示例存档属于测试输出，不进入 WAR。

WAR 须包含 `/storage/campaign-schema.json`、第 6 节所列准确规则 ZIP 和领域类型、严格聚合/领域引擎、
结构/运行服务、SQLite JDBC、Host 入口和对应静态资源；不得包含测试 launcher、真实
db/journal、保存文件或本地根。浏览器、真实 SQLite/HTTP、纯规则及故障测试分别承担
其实际场景责任，不能互相代替。自由卡、MANUAL/SAVING_THROW、有序检定效果、焦点和共享
网格由上述领域测试及浏览器路径共同验收，同时核对其与 schema-2 规则卡的隔离。
周期 AUTO/轮换是容量测量后的后续能力，当前测试按每命令耐久与显式完整保存验收。
下列是[全域实施路线](design/v2/storage-data-map-and-rollout.md)的当前完整验收责任，
各候选以实际执行结果判断，不从测试类或本文列表推定通过、完整 SRD 发布或平台断电耐久。

- 纯计划：独立字段/结构期望覆盖 JSON 严格解析、类型/默认值/键/约束、模块和外键依赖、
  完整未来写入足迹、确定性顺序及计划摘要。拒绝材料不得分配目录、执行 DDL 或消费 RNG。
- 引擎：使用最终选定 SQLite JDBC 在测试专有临时目录创建真实库，验证 DDL、索引、seed、
  规则、业务、历史和收据同事务；测试外键、STRICT、精确数值/字节/Unicode、事务所有权
  及锁争用。使用专有的临时运行目录，记录 SQLite 驱动的实际结果。
- 同版恢复：分别覆盖完整回滚空库、已提交初始化、首份检查点未完成、未知结果、错误
  模型/计划与部分结构；断言无补表、重 seed、重掷或重复授予。独占锁与一个业务/根/接受
  事务、未知 commit/rollback 的阻断及显式新 epoch、P0—P3、END 和封存/墓碑均按 SQLite
  [生命周期](design/v2/runtime-and-transactions.md)做真实中断验证。
- 领域接合：每个现行 owner 覆盖正常/边界/坏冻结数据/版本/幂等/共同回滚，以及
  完整文件字段和直接恢复的独立期望。不能仅对编码器与读取器的自洽往返宣称数据无遗漏。
- 打包与模式：当前必需项包括应用结构 JSON、编译/验证类、领域消费者、五个受控内置
  原包、构建生成的身份清单和 SQLite JDBC。验证生产描述符仅注册 SQLite 入口，排除旧 MySQL 网页、Servlet、Connector/J、
  两条迁移链和第 6 节列出的离线核验类。其他作者原包、离线写入类、真实 DB/journal/档案、
  配置秘密和本地材料均留在 WAR 外。审计字节数、SHA-256、条目数、必需/禁止文件及资源内容。

默认按改动及共享依赖运行定向测试。Host 切换按实际改变的持久化与事务路径选择测试。具体跨域覆盖缺口按第 2 节补选受影响的应用组。真实文件系统耐久/进程中断验收独立执行。
离线 MySQL 验收按第 3、4 节及其独立授权边界执行。

### 原生 Windows 外部 Tomcat 候选验收

先完成当前候选的相关测试，再按第 6 节审计要加载的 SQLite WAR。使用已安装的 Tomcat 10.1、Java 21+，指定全新
一次性 `CATALINA_BASE` 和独立空白 NTFS 存储根，两者均在 Git、WAR、现有 Tomcat 与
真实数据之外。以下变量由操作者填入准确绝对路径，不使用真实根作为夹具：

浏览器用例使用原生 Windows Node.js 和 Playwright。默认使用 Playwright 配套浏览器；
也可将 `DND_BROWSER_EXECUTABLE` 设为已安装浏览器的准确绝对路径，并记录实际浏览器版本。
该变量只选择测试浏览器，不改变应用配置。

```powershell
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Prepare `
  -TomcatHome $tomcatHome -JavaHome $javaHome -War $candidateWar `
  -CatalinaBase $candidateBase -StorageRoot $testStorage
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Start -CatalinaBase $candidateBase
node tools/storage/verify-war-http.cjs IDLE
node tools/storage/browser-smoke.cjs $screenshots
node tools/storage/browser-combat-smoke.cjs $combatScreenshots
pwsh -File tools/storage/windows-tomcat-candidate.ps1 -Action Stop -CatalinaBase $candidateBase
```

脚本使用原生外部 Tomcat Bootstrap、真实 `ROOT.war` 和正常 context 扫描；不手动注册
Servlet/Listener，也不读取或修改已安装实例的 setenv、ROOT、应用或数据。它复制容器
公共配置到独立 base，生成仅当前账号可访问的配置/存储目录，固定 loopback 8080/8005；
端口占用时拒绝，不终止其他服务或改端口。Prepare 检查空目录和路径隔离；Start/Status/Stop
绑定准确 base、PID、进程创建时间及 executable，不以模糊进程名控制服务。

Start 返回的 `STARTED` 只表示进程已启动，必须另查 `httpStatus=200` 且
`readiness.ready=true`。HTTP 脚本验证根入口与 `/host` 的 `303` 跳转、`web.xml` 显式注册的
Servlet/Listener、Filter/Session、静态资源、SQLite 就绪、CSRF/epoch/摘要以及
Host/Origin/方法边界和旧路由关闭；只发送
必定拒绝的写请求。浏览器脚本在一次性夹具上执行被授权的实际表单业务。失败保留进程身份、
日志与材料，用同一 base 的 Status/Stop 处置；不递归清理未知内容。

主浏览器、战斗和恢复用例为 HTTP 响应与配对的状态刷新保留 120 秒等待，页面控件仍使用
30 秒等待。报告分别记录 POST、状态刷新和整步耗时、请求身份及失败阶段；不自动重试
可能已接受的命令。这些是验收工具的等待上限，不是应用响应时间承诺。

若主浏览器在奥术回想重开阶段已留下准确的未结束运行，可在正常停止并重新启动同一
隔离实例后接续。服务必须明确返回 `RESIDUAL`，且原 `input.json`、`allocation.json` 和
保存文件全部保留；使用新的空证据目录：

```powershell
node tools/storage/browser-smoke.cjs $resumeEvidence --resume-arcane $runId $retainedInputFile
```

该模式只接受原主用例的准确奥术回想夹具，先核对运行身份、分配摘要、原保存载荷及
预期历史/法术位。残留状态按合同隐藏保存列表，显式恢复后才核对 API 文件身份。
接续会比较新保存的全部载荷（仅排除文件自身身份与完整性字段）、END/重开后的完整
领域与历史，并完成基础模块尾段；原保存文件身份和字节必须保持一致。此前通过的主流程
结果与新接续报告分别保留，再继续战斗用例。其他中断位置须先定位，不能借此模式跳过
失败断言、覆盖报告或删除未知运行材料。


正常停机与强制终止后分别重启同一 base/root，核对 `RESIDUAL` 的就绪 `503`、显式恢复后
原事实/历史不变；可用 `node tools/storage/verify-war-http.cjs RESIDUAL` 检查恢复前边界。
`browser-recovery-smoke.cjs prepare $evidence` 用真实表单建立 `4/12 → 9/12` 并保存准确
run/epoch、事实/历史及所有保存文件身份/字节摘要；外部停止再启动后，用
`browser-recovery-smoke.cjs recover $evidence` 核对显式恢复、新 epoch、旧 epoch 拒绝及
END。正常关闭与强制终止各使用新的空证据目录，进程操作仅针对候选脚本记录的准确 PID。
精确放弃使用 `prepare-discard` / `discard` 两段，准备后保留一次尚未生成完整保存点的治疗，重启后
经准确 runId 确认放弃，核对已存文件完全不变。脚本只接受已知隔离夹具，拒绝证据覆盖，
失败保留原现场；它不控制或终止进程。

原生文件测试需同时覆盖
第二实例争锁、退出后释放、准备前缀、发布冲突和未知材料；没有取得 Windows symlink
创建权限的用例单独说明，不以跳过替代 junction/reparse 验收。离线 POSIX 工具测试仍在
Linux/WSL 执行，不要求迁移到 Windows。

记录实际 OS/JDK/Tomcat/NTFS、候选身份、用例结果和跳过原因。进程中断结果不证明物理
断电时目录项持久；Windows 边界按[配置合同](configuration.md#sqlite-当前应用存储配置)。
隔离验收成功仍不代表正式入口已切换；正式部署、备份和回滚只按[部署指南](deployment.md)。

### 玩家参与原型验证

本原型只使用一次性私有 SQLite 根；不连接或重置真实 MySQL，不部署现有 Tomcat。
身份/同名申请/审批轮换/单写接管/暂停/撤权、玩家裁剪与当前叶子候选、完整参与历史和
类型化裁定、严格私有控制及可信 HTTPS 可选用以下定向集合：

```bash
mvn '-Dtest=CampaignParticipationIntegrationTest,PlayerGatewayServiceTest,PlayerParticipationAcceptanceTest,PlayerProjectionAndCandidatesTest,PlayerBoundaryFilterTest,PlayerHttpIntegrationTest' test
```

修改领域根或结构时，另外选择受影响的领域、codec、存储恢复及 schema 测试。正式候选按[部署指南](deployment.md#1-构建候选)构建并审计。
`PlayerHttpIntegrationTest` 创建一次性带 loopback IP SAN 的证书，
测试客户端仅信任该证书，并验证默认客户端拒绝它；需要 JDK `keytool` 及空闲 8080/8443。
该用例从玩家管理 GET 取得 Host 令牌，检查各次管理 POST 的成功响应均省略 `csrf`。关闭邀请后再读取管理状态，并用刷新所得令牌提交暂停，核对玩家此时的写权限。
已通过集合不为收尾重复执行；打包复用本次有效证据并审计 WAR。
`PlayerParticipationAcceptanceTest` 使用可控时钟和独立 SQLite，补查邀请轮换及准确到期、
END 后新运行的身份/收据隔离、后来成员的共享范围、控制转交与排队命令的串行边界，
以及真实反应窗口暂停时不代选、不追加业务事实。

独立进程恢复使用 `mvn '-Dtest=PlayerProcessRecoveryIntegrationTest' test`。它在两个
独立 JVM 间重启同一私有根，分别只信任对应临时证书，核对 RESIDUAL、Host 显式恢复与
重新开放、原设备换取新会话和原收据只读查证；不得自动恢复写连接或重放命令。
该用例同样占用 8080/8443，须与真实 HTTP 测试、浏览器启动器串行运行。
若默认临时目录位于 Git/WAR 标记之下，先创建另一个私有测试目录，通过
`-DargLine="-Djava.io.tmpdir=/absolute/private/test-temp"` 指定测试 JVM 的临时根；
子进程继承其隔离范围，不删除标记或放宽存储路径保护。

仅检查启动准备时运行 `mvn '-Dtest=PlayerLoopLauncherReadinessTest' test`。该集合不启动
HTTP/TLS 监听、证书进程或浏览器，检查存储路径、必要资源、服务可用性及失败清理。
临时根仍须满足前文的 Git/WAR 隔离与私有权限要求，不能因测试环境的合成标记而放宽保护。

可操作原型通过下面入口启动。脚本先编译测试启动器并准备 classpath，不执行测试。
它只绑定 loopback，自动产生临时测试证书，退出时清理
测试容器和证书；业务根保留，须在页面 END 或下次显式恢复。
启动器先检查路径和必要资源，确认容器及存储服务可用后才输出地址；初始化失败不报告
启动成功，并尝试清理本次容器与临时证书，不删除传入的业务根。已有残留运行仍可进入
Host 恢复页面，启动检查本身不恢复运行或赋予玩家写权限。

```bash
bash tools/storage/run-player-local.sh /absolute/disposable/player-store
```

DM 为 `http://127.0.0.1:8080/host/storage`，玩家为 `https://127.0.0.1:8443/player`。
人工浏览器必须正确信任测试证书，不忽略 TLS 警告。真实 LAN 启用只按[部署指南](deployment.md#可选玩家-https-入口)。
独立 Playwright 安装的浏览器验收在另一个终端执行，`--fresh-root` 必须准确指向本次
启动器使用的空白可丢弃根，证据目录须为空并与该根分离：

```bash
NODE_PATH=/absolute/browser-tools/node_modules \
PLAYWRIGHT_BROWSERS_PATH=/absolute/browser-tools/browsers \
node tools/storage/browser-player-smoke.cjs /absolute/empty/player-evidence \
  --fresh-root /absolute/disposable/player-store
```

浏览器默认使用标准证书信任。隔离自动测试可将 `DND_PLAYER_TEST_SPKI` 设置为本次启动器
输出的准确测试证书 SPKI，只对该测试公钥例外；不得借此跳过真实设备 CA 安装。
QR 像素核验还需独立 Python 环境中的
`Pillow` 和 `zxing-cpp`；用 `DND_QR_DECODER_PYTHON` 指定解释器，或以 `DND_QR_DECODER_PATH`
指定包含这两个包的临时目录。解码实际页面截图并比对本次邀请链接，不向报告输出邀请值。
脚本不使用通用 `ignoreHTTPSErrors`；此证据只覆盖隔离原型交互。
其单次旅程覆盖申请/批准、本人卡、合法候选、响应丢失后只读查证、明确裁定、共享撤回、
暂停、双标签接管及结束。截图/证书/日志和临时库不入 Git/WAR。
若前段已成功而邀请阶段中止，可用 `--resume-after-roster` 续跑；脚本先核对固定角色、
装备、版本和空参与状态，任何检查点差异都拒绝继续，不重放已完成的建队操作。

WAR 须额外包含玩家与主持参与界面、局部 QR 编码器、玩家边界/会话/投影/候选类、
参与/裁定领域及结构；禁止临时证书、测试启动器和任何运行凭据进入 WAR。

## 7. 手工验收

可达路由、UI 或客户端 JavaScript 变化，以及需要通过真实表单观察的模组表现，选择相关浏览器用例。按[部署指南](deployment.md#5-启动与验收)
检查 `/health`、`/` 与 `/host` 转到 `/host/storage`、SQLite 就绪、安全头和请求边界。
真实业务写入必须单独授权并具备可验证恢复方案。

按[工作台验证流程](#sqlite-结构完整文件与运行验收)覆盖桌面和手机宽度、键盘焦点、
场景操作与角色卡翻页、空闲与活动状态、加载及错误反馈。核对 CSP 下同源资源正常加载，
角色卡翻页保持场景中的表单选择，窄屏能够访问当前操作区与角色卡。

<a id="extra-attack-verification"></a>

## 战士 Extra Attack 的真实链路验证

标准组合的 `--case fighter-extra-attack` 实际推进到职业 4、5、6 级，检查授予、来源和 Attack 动作额度。应用侧的 `Schema2ExtraAttackRulesTest` 使用固定 ZIP 检查缺失、提前、重复、错误等级或来源的特性状态。另需验收 Host 表单与首击检查点时，对已启动的全新隔离候选执行：

```bash
node tools/storage/browser-extra-attack.cjs
```

浏览器走真实 Host 表单和 API，核对正常升级、额度展示、一次动作费用、两击及首击检查点恢复。
应用修改攻击序列或状态持久化时，选 `CampaignExtraAttackIntegrationTest` 覆盖第三击、击间移动/OA/换目标、放弃、Ready 单击、非法/过期/重复请求、坏文件和晚写回滚。修改组合器或规则库时，按[机制合同](rules/schema-2-extra-attack.md)及上文 SQLite 绑定边界选择相关应用测试。平台和 WAR 各按其实际影响验收。

<a id="improved-critical-verification"></a>

## 勇士 Improved Critical 的真实链路验证

标准组合的 `--case fighter-growth` 检查勇士特性的来源、授予等级和执行算法。若模组改动影响近战重击的可见结果，另补对应的独立命中与伤害场景。需要验收真实表单时，对已启动的全新隔离候选执行：

```bash
node tools/storage/browser-improved-critical.cjs
```

浏览器使用实际导入/接受/新建/成长/装备/战斗/保存/END/打开表单，按服务端实际骰值检查结果。固定 19、20、劣势及 Shield 边界由可复现的独立行为场景或应用机制夹具验证，骰点只能在服务端随机源边界注入。
应用改动按受影响的组合、攻击判定和命令/事务/恢复分别选 `Schema2ImprovedCriticalCompositionTest`、`Schema2ImprovedCriticalRulesTest` 或 `CampaignImprovedCriticalIntegrationTest`。规则库及完整冻结绑定的共同路径按其实际差量补测。

<a id="second-wind-verification"></a>

## 战士一级 Second Wind 的真实链路验证

标准组合的 `--case class-templates` 检查一级战士实际获得的回气特性、执行算法和初始资源。附赠动作、治疗骰或休息恢复声明改变时，按影响补对应的独立场景。需要验收 Host 的回气操作与完整文件重开时，对已启动的全新隔离候选执行：

```bash
node tools/storage/browser-second-wind.cjs
```

浏览器从实际导入、接受、新建和角色表单进入，使用服务器随机骰，核验回气结果、资源、普通动作保留及保存/END/重开后继续。满 HP、资源耗尽、真实短休与长休恢复按本次模组变化补独立场景。
应用组合/解释变化可选 `Schema2SecondWindCompositionTest` 或 `Schema2SecondWindRulesTest`；事务与恢复选 `CampaignSecondWindIntegrationTest` 检查边界、幂等、晚写回滚和坏文件。玩家候选、命令和收据路径变化时，`PlayerSecondWindCandidateTest` 覆盖邀请、审批、控制、接管及过期候选。

<a id="prone-verification"></a>

## 倒地姿态、爬行与起身的组内验证

本组使用既有战斗与速度声明。需要观察成品姿态与成本时，对已启动的全新隔离候选执行：

```bash
node tools/storage/browser-prone.cjs
```

浏览器通过 Host 原包导入/接受/新建和角色表单执行战斗及探索的卧倒、爬行、起身，检查姿态、成本、探索路径上限、旧战斗预算保留、倒地保存后 END/重开继续，以及宽窄屏布局和 JavaScript/CSP。

应用机制或共享状态变化时按差量选择以下测试。

`Schema2ProneRulesTest` 检查不花动作或时间的主动卧倒、探索姿态链及旧战斗预算保留、
双倍移动成本、Dash、25 尺速度的半尺边界、Ready 独立移动预算、OA 与失能停止；
`Schema2ExplorationRulesTest` 检查六秒活动和整路径预检。相关既有战斗、Ready、反应和回气测试
保护共享消费者。`CampaignProneIntegrationTest` 从应用夹具 ZIP 的独立审阅接受、有序
预览和新建开始，正常创建角色及取得装备，经真实生产命令和临时 SQLite 验证姿态、
移动、起身、未决路径保存/END/重开、探索卧倒/爬行/起身、战斗交接和离场门、拒绝边界、幂等、过期和事务回滚。
探索链应同时断言：卧倒不推进时钟；每段爬行最多按当前速度支付双倍成本，不能借旧 Dash；
移动与起身各推进六秒，旧移动/动作/反应事实保持；完整文件直接恢复姿态，重试不重复推进时间。
25 尺速度来自定向规则向量，不声称当前人类创建模板具有该速度。
`PlayerProneCandidateTest` 经过真实邀请、审批、控制和接管验证服务端候选，
包括探索许可、撤权及旧候选拒绝；客户端不能指定移动成本或替他人修改姿态。

共同加载、冻结和 HTTP 边界改变时，选择对应的 `CampaignRuleSupportTest`、`CampaignRuleBindingIntegrationTest`、`LocalRuleLibraryTest` 或 `StorageHttpIntegrationTest`。已通过且输入未变的结果复用。
固定骰值仅在服务随机源边界输入；构造的坏文件和纯规则状态只提供负例或补充边界证据。

## Schema 2 战斗与动作继续验证

[内部战斗合同](rules/schema-2-combat.md)通过实际来源、准备、冻结镜像、角色和战斗仓储验证。应用动作继续与时钟变化可选：

```bash
mvn '-Dtest=Schema2CombatRulesTest,Schema2CombatTimeRulesTest,Schema2CombatRuntimeServiceTest' test
```

战斗声明的应用解释与来源/快照映射改动按影响选 `Schema2CombatCompositionTest`、`JdbcSchema2CombatSourceTest` 或 `JdbcSchema2CombatSnapshotTest`。共享装备、健康、休息、成长或 schema 变化时补对应消费者。服务用例验证真实近战、机会攻击在离开前
提交暂停、准确父继续、过期版本、一次消费、掉物与倒地、秒余数、死亡豁免、因果子历史、
故障整笔回滚、UNKNOWN 原操作查证，以及 OPEN/CLOSED 窗口和健康／计时完整直接恢复。
原始骰、预算和未实现来源拒绝在纯规则与存储测试中分别验证；不以过往测试计数代替本次结果。

WAR 保留战斗消费者与仓储。运行 V032—V034 与来源 V008 供离线迁移核验使用，离线写入工具与测试向量也留在 WAR 外。
JDBC 代理及静态 SQL 检查不代替真实 MySQL、迁移执行、授权、部署、Host 或 archive format 2 验收。

## Schema 2 有限施法与持续效果验证

[有限施法](rules/schema-2-spells.md)的应用基本施法与反应分别有定向测试。下列为改动施法消费和改动反应窗口的示例：

```bash
mvn '-Dtest=Schema2SpellRulesTest,Schema2SpellRuntimeServiceTest' test
mvn '-Dtest=Schema2ReactionSpellRulesTest,Schema2ReadySpellRulesTest' test
```

[施法作者合同](rules/schema-2-spell-authoring.md)列出应用施法及法师声明的组合、来源和快照测试，按读取或写入边界选择。模组法术的学习名单、目标和区域声明使用独立期望，取得及执行使用成品场景。
真实角色测试经过初始化、逐级成长、每级学习和长休准备，验证重复／越级学习、非法准备与元数据法术执行拒绝。
反应测试覆盖命中后伤害前的 Shield、Counterspell 嵌套、Ready attack/move/spell 的准备／触发／释放、
一次成本、专注占用、PASS 与到期。持续效果测试分别验证同名 SHADOWED、多个独立 SUPPRESSED 原因、
解除与重新求值、战斗结束／重入、探索时间和健康后果。
事务与历史测试经过真实准备、角色、场景及历史仓储，核对重复请求／过期版本、paused leaf 直接恢复、
缺失／多余／孤儿行拒绝、故障回滚和 UNKNOWN 原操作查证。共享角色与战斗消费者改变时，补选其对应
定向测试，验证法术书、成本和时间的中性保留。代理 JDBC 与静态 SQL 不替代真实 MySQL 约束、权限、
并发和持久化验收。

当前批准链为运行 V001—V042 与来源 V001—V012，共 54 份迁移源；来源为 53 张表、159 个触发器，
其中 schema 2 为三个账本表与 43 张类型化作者材料表。WAR 保留施法与场景消费者、类型化仓储。
运行 V035—V038 和来源 V009/V010 供离线核验使用，离线写入工具、作者包与测试向量也留在 WAR 外。
这些命令描述可重复验证方法，不表示真实数据库或完整发布门已验收。

## Schema 2 有限怪物与多目标豁免伤害验证

[有限怪物合同](rules/schema-2-monster-breath.md)的应用组合和来源读取改动可选：

```bash
mvn '-Dtest=Schema2MonsterCompositionTest,JdbcSchema2MonsterSourceTest' test
```

批次解释及应用服务变化可选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2MonsterBreathRulesTest,Schema2MonsterBreathRuntimeServiceTest' test
```

快照或批次持久化改动另选受影响的仓储与结构测试。应用组合和存储测试核对 `stat_block`／`action_profile` 完整复合原子、准确 action 条目、启用、
覆盖前后值、save 熟练真实来源、防御适用 gate、原 ZIP 与全部类型化行。旧字段或声明
没有默认执行语义；BLOCKED_METADATA 和其他未支持动作必须拒绝消费。

规则和真实服务测试使用明确初始状态及独立手算结果，经过实际准备／快照、角色、场景、
怪物部署和真实命令到仓储读回，覆盖八方向完整点格 roster、敌友／免疫目标、一次共享
6d6、稳定 target ordinal、逐目标真实 DEX／Bless、自然 1／20、奇数半伤、易伤／免疫、
temp HP 全吸收、零 HP／即死／倒地掉物、全部 APPLY 后强制结束及统一 CON cut。
公共设计原 PFE／Cutting Words golden 保留目标地位；它们未有消费者时，不能用假贡献
冒充本轮可执行验收。after-batch Ready 必须来自准确怪物动作完成事实，不能伪造攻击或施法。
[独立 JSON oracle](../src/test/resources/schema2-monster/breath-runtime-golden.json)固定真实一级
角色／Bless／Ready 和 Hell Hound／Mummy 的输入、骰流、四包期望与恢复不变量；测试读取
这些手算期望，不能用生产算法输出更新它。先攻／前置动作的随机消费与吐息的 13／14 骰分开计量。

分别保存真实根命令产生的 raw PENDING 和真实 Ready 产生的 OPEN／CLOSED 窗口，再在
来源不可用、骰源零调用的目标中直接恢复，核全部怪物、角色子历史、原骰、source／holder、
完整 T、checks／packets／obligations 与唯一父继续点。恢复后仅执行准确剩余后缀，
不能重付 action、重掷 raw、再次伤害、复活 ENDED Bless 或隐式 PASS。

事务用例在完整预检、末尾目标／角色子事件、末尾效果、batch 子行和 accepted 写入各边界
注入故障，区分新根全回滚与已提交 raw 前缀保留；同键重放、同键异意图、stale 继续、
容量不足、缺失／多余／孤儿行和 UNKNOWN 原操作查证分别观察。recharge 必须来自后续
本人真实 turn START，短／长休恢复须由真实有限怪物休息 owner 产生，GET／恢复均不抽骰或补时间。

全链为运行 V001—V042、来源 V001—V012，共 54 份迁移源；来源 53 表、159 触发器，
其中 schema 2 有三个账本表和 43 张类型化作者材料表。WAR 保留怪物与批次消费者及类型化仓储。
运行 V039/V040 和来源 V011 供离线核验使用，离线写入工具、作者包、独立 oracle 与测试向量也留在 WAR 外。
JDBC 代理和静态 SQL 不替代真实 MySQL、权限／锁并发、完整文件、Host、技术 END／清理、
部署或跨域发布门；本节是验证方法，不缓存历史测试计数或宣称迁移已执行。

另行授权并安装后，在准确运行 schema 以只读身份执行
[怪物／吐息核验](../database/verify/schema2-monster-breath.sql)，并与
[场景核验](../database/verify/schema2-combat-runtime.sql)组合使用。inventory 查询列出表、
触发器和 FK 可见性；异常查询核完整头计数、连续 ordinal、release 时空间证明、共享骰和、
save／packet／防御／专注引用与算术、休息及其结束引用，正常应无异常行。脚本只读且不修复。
`Schema2MonsterBreathRuntimeSchemaTest` 对批准链、持久 DDL 前空场景门、精确复合 FK、
不可变触发器、闭合枚举、NULL 联合分支、无窗口 raw 继续及窄授权作源码合同检查；
它不执行上述 SQL，也不证明真实 MySQL 已通过。

## Schema 2 法师特殊恢复与法术共享伤害验证

[奥术回想](rules/schema-2-arcane-recovery.md)的应用机制与服务变化可选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2ArcaneRecoveryRulesTest,Schema2ArcaneRecoveryRuntimeServiceTest' test
```

验证真实短休完成、每日时钟边界、各环缺额与合计额度、拒绝旧 BLOCKED、幂等/版本、
准备/学习保留每日事实、类型化读回/直接恢复，以及晚写失败与 UNKNOWN 查证。

[法术共享伤害](rules/schema-2-spell-save-damage.md)的应用请求和批次服务改动可选：

```bash
mvn '-DargLine=-Xmx256m' '-Dtest=Schema2SpellSaveDamageRequestDigestTest,Schema2SpellSaveDamageRuntimeServiceTest' test
```

独立作者向量与手算结果覆盖真实 Wizard 授权/位数/组件、混合生物完整目标、共享原骰、逐目标
豁免/防御、健康与专注、反制不退款、伤害后 Ready、已付父施法失能、直接恢复、坏物理来源、
事务回滚与未知提交。旧元数据、Sculpt、地面物品、活动抗魔和未支持的嵌套对象风险保持明确拒绝。
状态域与旧非范围请求摘要使用独立固定期望，不能仅验证 writer/reader 自洽。

应用声明解释、来源和结构变化选对应组合、Source 或 Schema 测试。共用动作、场景、角色与批次消费变化时，按具体差量加入既有施法、反应/Ready、怪物吐息、
角色仓储及迁移/发布边界测试；已通过且输入未变化的结果按本文规则复用。真实 MySQL 的
CHECK/FK/触发器、权限和锁语义须另行授权验收，JDBC 代理与静态 SQL 检查不能代替。
WAR 保留规则、消费者和类型化仓储。运行 V041/V042 和来源 V012 供离线核验使用。测试向量、
离线工具、参考文件与本地 Agent 工作区均留在 WAR 外。完整规则仍为 DRAFT；这些内部测试不单独证明
Host 或完整文件接合，当前 SQLite 采用范围与额外验收见[领域接合表](design/v2/domain-integration.md)。
