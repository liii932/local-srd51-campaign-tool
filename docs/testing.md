# 测试指南

默认选择覆盖变更的最小充分验证。模组内容和可见行为使用独立作者工具，应用代码按受影响边界选择 Maven 定向测试。
SQLite WAR 用于外部 Tomcat 和独立作者场景。每次运行的计数、制品摘要和实际结果记录在验证或发布证据中。
[WAR 审计](#6-war-审计) · [无 MySQL 采用验收](#mysql-free-acceptance)

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

`-Dtest` 仅约束 Surefire。使用 `verify`、profile 或多模块 reactor 前检查 POM 及插件绑定，不能据此假定 Failsafe、其他插件或所有模块均已缩小范围。

相关测试失败时先修复并重跑相关范围，不自动扩大为全量。复用本次对相同代码、资源、依赖、配置及相关环境取得的有效结果；后续改动影响证据时才重跑对应范围。不得同时运行会写同一 `target` 的 Maven 命令。

当前工作台的启动、规则绑定、完整文件与就绪测试见[SQLite 结构、完整文件与运行验收](#sqlite-结构完整文件与运行验收)。

验证 SQLite Web 入口时，由 `HostHomeServletTest` 核对首页和退役 URL，由 `StorageHttpIntegrationTest` 核对就绪、文件下载、规则绑定及拒绝写入后的状态。

### Tomcat 测试依赖

`pom.xml` 的 `tomcat.version` 管理测试范围的 `tomcat-embed-core`。
修改它、Servlet 依赖或 Web 请求边界时，可选实际 HTTP 测试：

```bash
mvn '-Dtest=StorageHttpIntegrationTest,PlayerHttpIntegrationTest' test
```

用例以实际 SQLite 临时根和容器执行。启动前确认 `127.0.0.1:8080` 和 `8443` 空闲，
玩家测试还使用 JDK `keytool`。嵌入容器和容器提供的 Servlet API 留在 WAR 外。
外部 Tomcat 按[部署指南](deployment.md)维护。

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

DRAFT 语言与工具内容运行 `python3 -B tools/rule-packages/catalog-vector.py --check rule-packages/srd51-complete`，按独立字段矩阵逐项核对作者记录，并复算规范字节摘要。纯读取与投影测试从 `src/test/resources/character-catalog-author/` 读取固定应用夹具。范围与字段见[DRAFT 作者验证](rules/language-author-package.md#独立验证)。

修改目录校验脚本时，运行 `python3 -B tools/rule-packages/tests/rule-vectors-test.py -v`。测试在临时副本中检查行重排、非法作者字段及错误的独立期望表。

本地作者 CLI 的文件核验在支持 JDK `SecureDirectoryStream` 的 Linux/WSL 本地文件系统上执行。
在缺少该能力的原生 Windows 文件系统，文件核验按 `S2_LOCAL_STATE` 拒绝，通用解析和路径用例
仍执行。报告分别列出通过、按文件系统能力跳过的用例及 Linux/WSL 的文件核验证据。
[作者制品入口](rules/schema-2-rule-artifacts.md)

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

普通构建的数据库测试使用隔离临时根及实际 SQLite 文件。外部容器、浏览器和真实平台各按下文的入口执行。

<a id="mysql-free-acceptance"></a>

## 3. 无 MySQL 采用验收

默认产品构建和采用验收在完全无 MySQL 的清洁环境完成。下表列出旧测试责任的去向，
便于核对保留业务的实际证据。验证报告分别记录默认构建、真实外部 WAR、完整文件和同版恢复。
[退役与既有资料保管](design/v2/mysql-retirement.md)

### 测试处置矩阵

| 当前测试责任 | 处置 | 目标验收证据 |
| --- | --- | --- |
| 纯规则、声明解释、ZIP 安全和冻结身份，例如 `ProductionRuleArtifactTest`、`Schema2CompositionGoldenTest`、`Schema2*RulesTest` | 保留；从混合测试拆出仍有消费方的断言与固定夹具 | 以现行消费者核对严格解析、Unicode、预算、算法、候选/字段覆盖链、unsupported/DRAFT 拒绝、当前摘要和原包身份。 |
| 原包接受、来源分区和运行快照，例如 `Schema2SourceInstallationTest`、`LanguagePartitionEquivalenceTest`、`CharacterCatalogChainTest`、`Schema2SnapshotTransferServiceTest`、`JdbcSchema2*Snapshot*Test` | 迁移产品判据；旧 PARTITION、双库转存及离线票据专属断言随入口删除 | `LocalRuleLibraryTest`、`CampaignRuleBindingIntegrationTest`、`CampaignRuleBundleIsolationTest` 核对原字节与身份、导入/接受分离、缺包/未接受、顺序/覆盖、篡改、故障回滚及提交不明后的原操作查证。保留语言/工具的独立字段与字节判据，按 DRAFT 作者目录、当前规则组合和文件的各自协议核验。[来源责任处置](design/v2/mysql-retirement.md) |
| JDBC 角色、检定、装备、休息、场景、战斗、持续效果、怪物和施法，例如 `JdbcSchema2*HistoryTest`、`Schema2CombatRuntimeServiceTest`、`Schema2SpellSaveDamageRuntimeServiceTest` | 合并已有等价断言；将缺失的业务判据迁到对应 SQLite 测试 | 由 `CampaignDomains*IntegrationTest` 等生产命令、真实 SQLite 和读回用例承担。保留手算/固定期望、原骰次序、全目标预检、子事实、精确继续点和晚写失败；未支持机制失败关闭。[领域验收索引](design/v2/domain-integration.md#可重复验收索引) |
| 遭遇直接定位与检定分支定位 | 使用现行 SQLite 安置和根历史 | `CampaignScenePlacementIntegrationTest`、`CampaignCheckEffectsTest`、`CampaignManualIntegrationTest` 和 `StorageHttpIntegrationTest#dmPlacesEnteredCharactersAndAppliesConditionalPlacementInOneHttpCommand` 检查非相邻定位、两个检定分支、无效未选分支、晚写回滚、原收据及完整文件重开。工作台路径由 `browser-smoke.cjs` 验证。[定位合同](design/v2/world-and-scene-spaces.md#直接定位与分支定位) |
| `BusinessWriteTransactionsTest`、`BusinessWriteGuardSchemaTest`、`MySqlIntegrationIT` 的事务责任 | 迁移可观察的事务断言；首条 SQL 守卫、DBCP 复位及连接目标保护随 MySQL 实现删除 | 用 `CampaignCheckEffectsTest`、`CampaignDomainRecoveryTest`、`CampaignStorageServiceTest`、`CampaignStorageLifecycleTest`、`CampaignStorageCrashTest` 等验证串行、过期版本、同键重放/异意图、零 RNG/写入的预检拒绝、晚失败整笔回滚及 UNKNOWN 查证。逐项读回事实、根/子历史、效果、版本和收据。 |
| SQLite 结构、文件、备份恢复、Host/玩家及安全 | 保留 | 保留 `StorageSchemaCatalogTest`、`SqliteSchemaTest`、`CampaignFileVectorTest`、`CampaignCodecTest`、故障/平台用例、HTTP/浏览器与手工入口。核对 format 3 的独立字节向量、旧/坏文件拒绝、退役路由 `404`、会话和存档秘密边界。[SQLite 验收](#sqlite-结构完整文件与运行验收) · [玩家测试](#玩家参与原型验证) |
| MySQL 静态迁移、schema/授权、诊断、真实 JDBC 验收、旧存档与 Host 专属实现 | 随对应实现删除，包括 `SchemaMigrationsTest`、`RuleSchemaMigrationsTest`、`DatabaseDiagnosticsTest`、`MySqlIntegrationTestSupportTest`、`LanguagePartitionJdbcIT`、`verify-language-jdbc.py` 及其脚本测试 | format 1 和内部 format 2 的专属读写与 JDBC 恢复测试随入口退出；现行 format 3 继续独立验证旧格式拒绝、文件完整性和敏感字段拒绝。发布资格、规范编码及作者向量按各自消费方保留。明确选择的旧版维护使用匹配旧版的隔离入口。 |
| 作者源与独立向量 | 保留 SQLite 成品 Python/JDK 场景、ZIP 一致性检查、DRAFT `catalog-vector.py` 和 `rule-vectors-test.py` 中保留格式的断言 | 固定应用夹具在 Surefire，交付原包用候选 WAR。独立手写期望覆盖可玩组合及 DRAFT 目录各自承诺的内容。移除专属路径前逐项核对自带 SQL、旧数据库快照行、JDBC 代理或 golden 向量的剩余消费方。 |

### 两个清洁环境

从同一候选修订的干净检出开始。A 使用清洁 Linux；B 可使用另一个清洁 Linux 实例或清洁的
原生 Windows 虚拟机。两套 Linux 必须各有隔离网络、Tomcat base、Maven 本地库、私有 SQLite
根和规则接受账本。WSL 使用发行版本地 POSIX 文件系统，并在应用运行期间隔离宿主数据库。
A/B 只转移候选 WAR、完整 JSON 和所需准确规则 ZIP。原生 Windows NTFS、ACL、进程中断和外部
Tomcat 验证另行取得。B 为清洁 Windows 时可共用该实例，否则在独立原生候选实例取证。
安装有 MySQL 的宿主可验证原生平台边界，不能计入 A/B。

各实例准备仓库外证据目录，记录系统镜像、JDK、Maven、Tomcat 与安装清单，以及实际使用的
Python、Node.js、Playwright/Chromium 等工具版本。固定干净的 Maven 设置和制品源，构建完成后
断开隔离实例外部网络，保留环回和测试工具。串行使用各测试端口，进程和浏览器写入限获授权的
隔离实例及一次性样本。

| 环境核对 | Linux/WSL | 原生 Windows |
| --- | --- | --- |
| 安装、服务与进程 | 对照镜像安装清单与系统包清单（如 `dpkg-query -W`），查 `ps -eo pid,comm`、`command -v mysql mysqladmin mysqld mariadb mariadb-admin mariadbd`。有 systemd 时查 `systemctl list-unit-files --type=service`。 | 对照镜像安装清单与卸载注册表（含 32 位项），查 `Get-Service`、`Get-Process`、`Get-Command mysql,mysqladmin,mysqld,mariadb,mariadb-admin,mariadbd -All`。 |
| 本地监听、外部连接条件 | 启动前后查 `ss -ltnp`，记录 `ip -br link`、`ip route` 和隔离方式。 | 启动前后查 `Get-NetTCPConnection -State Listen`，记录 `Get-NetAdapter`、`Get-NetRoute` 和隔离方式。 |
| 有效配置与可见依赖 | 检查有效 Maven 设置、环境变量名、JVM 实际 classpath、Tomcat home/base 的 `lib` 或自定义加载路径，以及 `server.xml`、`context.xml`、ROOT Context、`web.xml`；确认无 Connector/J 或 MariaDB JDBC、MySQL DataSource/JNDI、连接串和账号凭据。 | 同 A，使用 Windows 上实际生效的路径与启动参数。 |

核对镜像及工具安装目录中的便携程序，确认有效环境中也无手动安装的 MySQL/MariaDB 服务、
客户端、运行或来源实例。核对全部监听，
避免只检查默认的 3306/33060。保留命中的路径或类别、检查范围和结论，证据不写入环境变量值或密码。

1. **空依赖库解析及产品打包。** Linux/WSL 在仓库根执行以下命令。Windows 用
   `New-Item -ItemType Directory` 在 VM 的测试区域分别为 `$acceptance_maven` 和
   `$acceptance_evidence` 创建空目录，在 PowerShell 用原生 `mvn` 执行相同目标及选项，
   每条命令写在一行。两个环境各自执行，使用正常产品 POM 和默认构建 profile。依赖树覆盖全部
   scope，包括 test 和 provided。

   ```bash
   acceptance_maven="$(mktemp -d)"
   acceptance_evidence="$(mktemp -d)"
   mvn -B -ntp "-Dmaven.repo.local=$acceptance_maven" -DfailIfNoTests=true \
     '-Dtest=StorageApplicationListenerTest,StorageHttpIntegrationTest,LocalRuleLibraryTest,CampaignRuleBindingIntegrationTest,CampaignCheckEffectsTest,CampaignStorageCrashTest,PlayerHttpIntegrationTest,PlayerProcessRecoveryIntegrationTest' \
     clean package
   mvn -B -ntp "-Dmaven.repo.local=$acceptance_maven" dependency:tree \
     "-DoutputFile=$acceptance_evidence/dependency-tree.txt"
   mvn -B -ntp "-Dmaven.repo.local=$acceptance_maven" help:effective-settings \
     -DshowPasswords=false "-Doutput=$acceptance_evidence/effective-settings.xml"
   ```

   保存构建、解析和各组测试的输出、退出码和 Surefire XML；补跑前保留前一组报告。逐项核对
   所选测试类与实际执行数。核对依赖树、解析日志和新库条目中没有
   `com.mysql:mysql-connector-j`、`mysql:mysql-connector-java`、MariaDB 等 MySQL 驱动或
   MySQL 专用产品依赖。按 [WAR 审计](#6-war-审计)检查两侧候选的入口、资源、SQLite JDBC、规则原包、生产类和引用闭包。
   另检查 `target/classes` 及容器实际加载路径无已退役的专属适配器。使用保留功能的测试确认
   共用 JDBC API 及纯领域消费者可用。
   两侧各自构建和审计，后续运行及移机都使用 A 的同一份 WAR，并对照 A 的摘要核对 B 收到的
   制品及两侧实际加载的 `webapps/ROOT.war`。默认不要求两次独立 Maven 构建逐字节相同。
   应用补跑从[领域验收索引](design/v2/domain-integration.md#可重复验收索引)按每项保留能力选取
   实际受影响的结构/规则、业务、文件和权限测试，并执行下面列出的故障组及原生组。所有补跑使用
   本次的 `-Dmaven.repo.local`。在应用运行的断网阶段执行补跑时加 `-o`，使用此前构建解析的
   依赖。完整 Maven 套件仍按第 2 节选择。

2. **真实 WAR 启动。** 按[双平台外部 Tomcat 候选流程](deployment.md#isolated-war-candidate)启动
   隔离实例。每次启动均检查旧 JNDI 资源、驱动、凭据和可用 MySQL 连接的缺席。每个分支使用独立的
   一次性 `user.home`，在启动该实例的 JVM 上设置 `-Duser.home=<私有绝对路径>`（直接启动 Java 的候选工具可用
   `JDK_JAVA_OPTIONS` 临时传入）。Windows `Prepare` 会写入显式根，只在停止的一次性实例上修改其
   `ROOT.xml` 中的 `DND_STORAGE_ROOT` 参数。逐次停止再更换配置，检查以下启动分支：

   | 配置 | 就绪与本地文件断言 |
   | --- | --- |
   | 显式有效私有根 | `node tools/storage/verify-war-http.cjs IDLE` 通过，`instance.lock` 在准确显式根。 |
   | Context 参数和进程环境变量均未设，`user.home` 指向有效私有目录 | 相同 HTTP 验证通过，`${user.home}/.dndtool/campaigns/instance.lock` 出现在该默认根。 |
   | Context 参数和进程环境变量均未设，测试 JVM 显式使用 `-Duser.home=` | 就绪 `503`，`mode=SQLITE`、`ready=false`、`state=UNAVAILABLE`、`code=STORAGE_ROOT_INVALID`，无新受管目录或活动运行。 |
   | Context 显式给相对路径，进程环境和默认根均可用 | 就绪 `503`，`mode=SQLITE`、`ready=false`、`state=UNAVAILABLE`、`code=STORAGE_ROOT_INVALID`，有效备选根不出现受管文件。 |
   | 两处覆盖均未设，在一次性 `user.home` 预先建立名为 `.dndtool` 的普通文件 | 相同 `503` 和状态，`code=STORAGE_UNAVAILABLE`，保留该文件且未创建活动运行。 |

   失败分支直接读取 `GET /api/host/storage/readiness`，例如运行
   `curl -i http://127.0.0.1:8080/api/host/storage/readiness`（PowerShell 用 `curl.exe`），确认响应不披露路径。
   `StorageApplicationListenerTest#missingOverridesAndUnusableHomeFailWithoutStartingTheService`
   验证无 Context 和环境参数、且 JVM home 缺失、为空或为非绝对路径时没有服务属性且只报告
   `STORAGE_ROOT_INVALID`。用 `StorageHttpIntegrationTest` 验证无效配置下仍拒绝旧路由且不披露路径。
   检查生产启动调用链、描述符和实际加载类，核对启动未查找 `jdbc/DndToolSE` 或任何旧 DataSource。
   对健康和失败分支均保存[连接观察](deployment.md#isolated-war-candidate)。

3. **真实 WAR 规则和业务。** 在两侧的健康候选实例上按 [SQLite 验收](#sqlite-结构完整文件与运行验收)
   直接运行浏览器用例。为每个命令准备独立的空白证据目录，冒险脚本另用全新受管根。

   ```text
   node tools/storage/browser-smoke.cjs <主验收证据目录>
   node tools/storage/browser-combat-smoke.cjs <战斗验收证据目录>
   node tools/storage/browser-adventure-smoke.cjs <冒险验收证据目录> --fresh-root <冒险样本私有根>
   ```

   主流程完成后在原测试根接跑战斗脚本，另启空白根跑冒险脚本。核对规则 PC/NPC、自由卡、装备、
   两分支检定及有序后果、健康、战斗/继续、资源与休息和有限施法。旅行和场景时钟用工作台创建
   两个地点与路线，安置主体后记录 DM 明确输入的耗时，核对源时钟推进、目标时钟保持及主体个人
   经历。有限怪物用 `CampaignDomainsCombatIntegrationTest#realBlessCostAndSharedMonsterDamageSurviveTheRolledPendingCheckpoint`
   核对真实共享吐息原骰与所有目标，并用 `CampaignMonsterSpaceIntegrationTest` 核对跨场景额度和 HP。
   工作台上再对 Hell Hound 吐息记录目标、共享原骰、受损结果及用量。按[能力表](design/v2/domain-integration.md#sqlite-retained-capabilities)
   对应各用例与事实。工作台验证 [#89](https://github.com/liii932/local-srd51-campaign-tool/issues/89)
   时，在裁定场景将已入场主体直接放到合法的非相邻空格，核对位置/版本、原空间时钟和普通移动
   额度。分别用规则卡与自由卡提交成功/失败定位并混合其他分支后果；用低、高 DC 覆盖成功与失败，
   以同一次动作原骰和历史核对选中分支。将未选分支设为越界或已占用，验证候选 HTTP 拒绝；
   定向测试再证明零取骰、零部分写入、过期目标拒绝、晚写整笔回滚和同一操作查证。

   用成品 WAR 运行两份独立规则场景，留存组合顺序、原包摘要和实际用例结果。另核对 ZIP 与作者
   源的一致性及 DRAFT 作者向量。以下为 Bash 示例，Windows 使用对应候选路径及本机 Python。

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
   前三包。缺包时预览报 `RULE_PACKAGE_MISSING`，全部导入但接受前预览及建档报
   `RULE_PACKAGE_NOT_ACCEPTED`。逐包审阅和明确接受（`GET .../rule-review`、
   `POST .../rule-accept`）后，把依赖基础包的扩展移到基础包之前，应使预览返回 `409`。按正确顺序预览
   （`POST .../rule-preview`）并创建战役，比对接受记录、预览和运行的实际成员、双摘要和来源链。
   用真实角色验证运动使用感知、固定伤害为 `2`。用独立隔离根上传坏 ZIP，将固定伤害的 `algorithm`
   改为 `UNKNOWN_VITAL_EFFECT`，导入预期 `409/RULE_PACKAGE_INVALID` 及 `S2_TYPE`。另将选定成员的
   `rawZipSha256` 换成另一个有效包的摘要，核对错绑预览/建档拒绝；在
   `CampaignRuleBindingIntegrationTest` 验证同版号错原包或伪造冻结投影的重开拒绝。对照规则库
   inventory、`runs/`、`saves/` 和状态，确认拒绝未登记无效材料、误接受成员或建立活动运行。
   [本机规则库合同](design/v2/local-rule-library.md)

4. **玩家与失败边界。** 先证实默认无玩家监听且 DM 8080 只绑定 loopback，再在独立验收配置按
   [玩家 HTTPS 流程](deployment.md#可选玩家-https-入口)启用准确地址的 8443。执行
   [玩家浏览器脚本](#玩家参与原型验证)可直接连接该 WAR，使用新的私有根及证据目录。核对批准、
   设备/席位隔离、受限投影、候选动作与裁定，比较 Host 和玩家响应，确认同一 SQLite 提交只新增
   一条对应根历史并原子记录所需的子历史和一个原请求收据。另用 `PlayerHttpIntegrationTest`、
   `PlayerProcessRecoveryIntegrationTest` 覆盖服务端权限撤销、重启后的重连及只读查证，
   核对旧会话、撤权和未决请求均不能重放写入。记录坏 Host、伪造转发头、CSRF/Origin、对象版本、
   跨 Connector、公开和旧 MySQL URL 的拒绝。涉及真实设备、证书信任或
   防火墙时使用已批准的目标，按[设备采用门](design/v2/player-participation.md#验收与采用门)单独留证。

5. **两机完整文件。** 使用两侧专用的空白根。在 A 先以标准组合建档、保存、END 并下载，而后执行
   第 3 步的自定义组合另建一档。两档均创建并安置规则角色，至少执行一项已记录原骰和后果的命令。
   在其中一档保留借机反应
   窗口。可照 `browser-combat-smoke.cjs` 的临近战斗布置，在规则场景把持匕首的战士置于 `(1,0)`、
   持钉头锤的牧师置于 `(2,0)`。按实际先攻推进到战士回合，令战士移向 `(0,0)`。显示「放弃反应」
   后暂停，不点 PASS 或继续结算。记录原先攻骰、移动额度、位置、动作流和当前反应叶子。
   玩家控制、共享及重开重新批准可以使用另一档。

   用工作台「保存」与 END 发布 MANUAL/FINAL，经保存文件列表下载对应 `RULE_BOUND_CAMPAIGN`
   format 3 JSON。记录精确 `fileKey`、SHA-256、绑定及全部领域事实和历史。向 B 转移 A 的 WAR、
   导出的 JSON 和自定义所需的准确原包，逐份核对字节数及 SHA-256。B 通过工作台上传完整文件，
   逐份确认只登记并保持 IDLE。先不导入规则原包，打开标准档并核对规则库仍无接受记录，END 后打开
   自定义档应报 `RULE_PACKAGE_MISSING`。导入全部准确成员但不接受，重试应报
   `RULE_PACKAGE_NOT_ACCEPTED`。按第 3 步接受后再打开。另用独立根提交坏文件，或用同版本
   重新打包得到的不同原字节建立本机接受记录后打开自定义档。核对两种拒绝均不激活或覆盖已有
   文件；用 `CampaignStorageLifecycleTest#fileIdentityConflictNeverOverwritesExistingBytes`
   验证内容有效但身份冲突，用 `StorageHttpIntegrationTest#ruleLibraryPreservesOriginalZipAndFileOpenRequiresExactLocalAcceptance`
   验证即使完整性正确也拒绝不支持的格式。

   B 建立新的 run，比较冻结绑定与字段链、初始/当前事实、角色/场景/时钟、原骰、根/子历史、资源、
   未决动作及玩家控制/共享。可保存一份未继续的 B 副本，比较 JSON 中除 `fileKey` 和 `integrity`
   外的载荷。选择原反应窗口的 PASS，再点击「继续结算行动」，核对只完成原移动后缀，随后保存并
   END。原文件保持原字节，旧 run、epoch、请求收据和设备会话无继承；玩家设备须重新批准。涉及
   [#89](https://github.com/liii932/local-srd51-campaign-tool/issues/89) 的样本另比较定位后的
   格位、版本、原骰和全部因果历史。

6. **原工作库残留。** 独立于移机测试，各环境在合成活动战役上正常停机和强制中断，分别重启同一
   实例及原私有根。参照[原生候选的恢复用例](#原生-windows-外部-tomcat-候选验收)运行
   `node tools/storage/browser-recovery-smoke.cjs prepare <空证据目录>`，重启后用
   `recover <同一证据目录>`；另用新证据目录做 `prepare-discard` / `discard`。
   `node tools/storage/verify-war-http.cjs RESIDUAL` 预期就绪
   `503/STORAGE_RECOVERY_REQUIRED`，显式恢复后为 `ACTIVE`。核对保留原 run、库/journal、最新事实、
   历史和收据，epoch 更新，旧 epoch 拒绝，未重建、补 seed 或重放。另在两侧按第 5 步的
   借机反应步骤建立相同未决流程，在活动且保留原库时终止进程，重启后从战役桌用准确 run 恢复，
   核对未决叶子、原骰和已付成本，再继续原移动。用双场景用例验证继续焦点场景时其他待继续场景冻结。

   停止候选后用定向 Maven 测试真实 SQLite 与文件系统故障，沿用本次的 `-Dmaven.repo.local`。

   ```bash
   mvn -o "-Dmaven.repo.local=$acceptance_maven" -DfailIfNoTests=true \
     '-Dtest=CampaignStorageCrashTest,CampaignPreparationTest,CampaignStorageLifecycleTest,CampaignStorageServiceTest,CampaignDomainRecoveryTest,CampaignDomainRootValidationTest,CampaignCheckEffectsTest,StorageReadinessTest' test
   ```

   按故障边界逐项留证。初始化、保存发布、写入后提交前和已提交响应丢失由进程级中断及精确续作
   验证。应用定向测试核对晚写回滚、坏库/坏投影、过期 epoch/revision、同键异意图与未知文件保护。
   `CampaignStorageServiceTest` 在真实临时库中用延迟外键约束触发 `COMMIT` 异常，
   用晚写触发器提前回滚后触发 `ROLLBACK` 不能确认。两种情况分别检查
   `COMMIT_UNKNOWN`、就绪阻断及显式恢复后的事实和原收据。对照进程强制退出、正常回滚和提交后
   响应丢失各自的结果。观察故障前后的权威事实、根/子历史、效果、版本、文件可见性和收据，
   查证前拒绝新写入，查证后禁止自动重放原命令。

7. **原生文件系统。** Linux 实例用 `findmnt -T "$storage_root"` 确认 POSIX 底层文件系统，活动样本用
   `stat -c '%a %u %h %d:%i %n'` 核对根、运行和保存目录的 `700`、受管文件无 group/other
   权限及发布测试中硬链接的设备和 inode。故障组再验证符号链接拒绝、同卷无覆盖发布、退出释放
   及未知内容保护。原生 Windows 实例用 `Get-Volume` 和驱动器类型核对本地固定 NTFS，用 `Get-Acl` 检查实际
   运行账号的 ACL；运行 `mvn '-Dtest=CampaignWindowsFilesystemTest' test`（附上本次的
   `-Dmaven.repo.local`），核对已有宽 ACL、UNC、junction/reparse、跨进程锁和退出释放。
   JUnit 临时根也放在各自目标文件系统上，必要时按 [SQLite 测试临时目录](#sqlite-结构完整文件与运行验收)
   同时设置 Maven 与测试 JVM。Windows 生命周期测试中的符号链接夹具需要该隔离账号能够创建链接；
   若不可用则单独列出受限方法，保留 A 的对应断言并在 B 执行原生 junction/reparse，未获得的
   Windows 执行结果标明原因。各平台验证根争锁、坏库、无效根、损坏保存点及发布冲突。
   进程中断证据只覆盖进程级恢复。[存储配置](configuration.md#sqlite-当前应用存储配置)

记录候选修订、WAR 字节数/摘要/条目数、环境与隔离方式，每个检查项用以下字段单独记录。

| 环境与检查项 | 实际命令或操作及输入身份 | 预期状态或错误 | 实际状态与退出码 | 证据位置 |
| --- | --- | --- | --- | --- |
| A 或 B、步骤编号 | 命令或表单步骤、相关候选/文件/原 run 身份 | 数值、就绪状态、拒绝代码或不变量 | 对照结果、适用时的退出码 | 测试报告、诊断、状态对比或文件审计 |

JUnit 记测试数、失败、错误和跳过原因。缺证、跳过或失败的采用门须在对应环境补验。
证据意外含敏感材料时留在受控目录并在报告中脱敏。测试资料、日志、信任材料和备份均在 Git/WAR 外。
既有 MySQL 库、历史及数据保管单独按[数据库说明](database.md)处理，清洁环境的验收不对其执行连接或写入。

<a id="4-迁移与规则测试"></a>

## 4. 规则声明与采用边界

完整 SRD 的 DRAFT 目录与本版有限可运行组合各有自己的入口。产品加载使用严格作者读取、
ZIP 原始身份和 SQLite 冻结投影。纯规则、应用消费者和独立作者期望按差量分别验证。

| 变更范围 | 定向入口与验收 |
| --- | --- |
| DRAFT 语言与工具作者目录 | `catalog-vector.py --check rule-packages/srd51-complete`，配合 `CharacterCatalogAuthorPackageReaderTest`、`ToolAuthorPartitionTest` 核对合法字段、重复/错误内容、规范字节和纯分区投影。DRAFT 目录遵守其原有发布门。 |
| 可运行 ZIP、组合和字段覆盖 | `ProductionRuleArtifactTest`、`ProductionSchema2StoredProjectionTest`、`Schema2CompositionGoldenTest`、`Schema2CompositionParsingTest`、`Schema2CompositionRejectionTest`。按主题另选 `Schema2*CompositionTest` 测机制和拒绝边界。 |
| 冻结规则的业务接合 | `CampaignRuleSupportTest`、`CampaignRuleBundleIsolationTest`、`CampaignRuleBindingIntegrationTest`、`LocalRuleLibraryTest`。用真实 SQLite 核对原包接受、缺损、篡改、准确绑定、使用和文件重开。 |
| 完整文件支持范围 | `CampaignFileVectorTest`、`CampaignCodecTest`、`StorageHttpIntegrationTest` 验证 `RULE_BOUND_CAMPAIGN` format 3，按声明拒绝旧格式、坏投影和敏感字段。继续点及同版残留按对应业务方验证。 |

固定规则身份、摘要向量及本版支持/拒绝范围见[模组作者向量与成品行为](#模组作者向量与成品行为)
和[领域验收索引](design/v2/domain-integration.md#可重复验收索引)。独立通过的 DRAFT 作者向量给出
目录证据。可玩资格仍由应用支持的组合、接受和实际消费路径决定。

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

## 5. Web 与安全测试

覆盖精确 loopback/Host/scheme/port、伪造 `X-Forwarded-*`、方法限制、Session Cookie、CSRF、Origin/Referer、Fetch Metadata、安全响应头、epoch、对象版本、幂等键和摘要。错误入口应返回一致的普通错误且不能到达业务写入。

验证 Host 写请求时，先取得会话和令牌。仅以 `_csrf` 参数提交正确令牌应返回 `403` 且无业务写入。
以 `X-CSRF-Token` 请求头提交时，分别检查合法命令成功，以及额外的业务参数仍按入口合同被拒绝。

客户端权威边界测试应确认浏览器不能指定骰点、选中骰、派生修正、最终结果、效果算法、
数据库身份或版本推进。相应领域实现后，还必须确认浏览器不能指定服务器候选集合之外的
职业/专长/法术/目标，不能覆盖先攻顺序、伤害调整、资源消耗、状态持续时间或 DM 裁定类型。

## 6. WAR 审计

生产代码或打包资源变化后，从候选对应源码的根目录运行。使用 Python 3.11+ 和环境中的 JDK 21+（`java`、`javac`、`jdeps`）。

```bash
python3 -B tools/storage/audit-war.py target/dnd-tool-se-0.2.1.war
```

保存命令、退出码及 JSON 输出，记录字节数、SHA-256、全部 ZIP 条目数、应用类和嵌套 JAR 数。
工具检查生产描述符及 Host/玩家静态资源、SQLite JDBC 驱动服务和数据库结构、运行类和引用闭包、
五个准确随附 ZIP 及 `bundled-members.tsv`。规则清单使用候选内实际依赖重新核算，并与发布包逐字节比较。
生产类须能回查当前源码，JDK 引用检查按外部 Tomcat 提供 Servlet API 的配置运行。

禁止内容包括 MySQL 专属驱动、两条迁移链、专属离线工具和仓储、退役 Web 入口、测试启动器
与夹具、凭据、证书或私钥、外部 Tomcat 配置、日志、备份、真实数据库及完整战役文件。
嵌套 JAR 名称、驱动服务和类条目共同检查。审核源码、资源和示例配置差量中可能内嵌的敏感值，
将人工审计结果与工具输出一起保留。环境配置、连接行为与实际运行仍按[采用验收](#mysql-free-acceptance)取证。

用 `CampaignRuleBundleIsolationTest` 验证原包损坏时的建档、重开和原文件保护。
用 `StorageHttpIntegrationTest#damagedBundledCandidateIsListedButRejectedBeforeAnyRuleLibraryWrite`
验证坏候选列出但导入前拒绝。标准组合及家规变体使用各自的[独立作者场景](#模组作者向量与成品行为)。

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
测试只写临时目录。HTTP 测试独占本机 8080，不能与人工验证实例同时运行。
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

玩家原型测试使用独立 Tomcat 入口和一次性私有 SQLite 根。
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

## 有限战斗、施法与怪物流程

按[领域验收索引](design/v2/domain-integration.md#可重复验收索引)选取实际应用入口。

| 机制 | 现行定向测试 |
| --- | --- |
| 战斗、反应、继续、时间和空间 | `CampaignDomainsCombatIntegrationTest`、`CampaignSpacesIntegrationTest`、`CampaignSpaceTimeBoundaryTest`。对应纯规则选用 `Schema2CombatRulesTest`、`Schema2CombatTimeRulesTest`、`Schema2ReadyRulesTest` 及 `Schema2ReactionSpellRulesTest`。 |
| 有限施法与持续效果 | `CampaignDomainsCombatIntegrationTest`、`CampaignImprovedCriticalIntegrationTest`。纯规则选用 `Schema2SpellRulesTest`、`Schema2ReadySpellRulesTest`、`Schema2OngoingEffectRulesTest`。 |
| 吐息及共享伤害 | `CampaignDomainsCombatIntegrationTest`、`CampaignMonsterSpaceIntegrationTest`、`Schema2MonsterBreathRulesTest`。 |
| 法师奥术回想 | `CampaignArcaneRecoveryIntegrationTest`、`Schema2ArcaneRecoveryRulesTest`。 |

真实应用测试验证 HP、费用、原骰、已执行前缀、完整文件和继续点。纯规则测试各自覆盖其判定，
成品模组用独立场景核对实际规则及可见效果。实现范围和待采用机制见[领域接合](design/v2/domain-integration.md)。
