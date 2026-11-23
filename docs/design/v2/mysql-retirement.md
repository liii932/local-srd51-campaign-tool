# MySQL 退役准备与 SQLite 单路线实施计划

SQLite 已是唯一 Web 后端。目标是让产品的安装、默认构建、测试、交付和恢复也都独立于运行 MySQL 和来源 MySQL。当前 [POM](../../../pom.xml) 仍直接声明 `provided` 的 Connector/J，默认编译和测试仍包含两套旧库的代码；WAR 的排除清单只覆盖其中一部分。启动时无需 MySQL 和在干净环境完成退役验收是两个不同的检查点。

## 实施依据

| 需要判断什么 | 以何处为准 |
| --- | --- |
| 哪些装配已退出、哪些类和资源仍被编译或交付，哪些数据须保管 | [运行库、来源库及共享代码的依赖清单](storage-data-map-and-rollout.md#mysql-dependency-inventory)、[数据库与账号](../../database.md) |
| 旧服务的哪项业务由 SQLite 承担，哪里只有有限接合 | [保留能力、调用者及缺口](domain-integration.md#sqlite-retained-capabilities) |
| 哪些用例迁移断言、哪些随适配器退出，怎样取得候选证据 | [测试处置与无 MySQL 验收](../../testing.md#mysql-free-acceptance)、[部署与回滚边界](../../deployment.md) |

每个活动战役持有自己的 SQLite 权威事实、冻结材料和完整历史。本机规则库存准确原包与接受账本。业务请求须先预检，再由服务端取随机结果，同一事务写入事实、效果、版本、历史及幂等结果。文件打开创建新运行，同版残留恢复沿用原工作库及未决事实。DM loopback、默认关闭的玩家 HTTPS、独立会话及逐人裁剪随这条路径保留。具体接口与文件合同见[产品说明](../../product.md)和[存储运行合同](../../storage-vitality-loop.md)。

规则验证分三处承担。SQLite 默认使用 WAR 的四包有序标准组合，也支持明确选择的本机准确已接受组合。format 3 文件打开重新核对规则，准确标准组合可由 WAR 提供，否则须有匹配的本机已接受原包。同版残留恢复核验战役内冻结材料。完整 SRD 作者目录仍是 DRAFT，原 MySQL 的内部 archive format 2 有独立资格门。上述身份和协议各有调用者，不能按格式编号或 `Schema2` 前缀一起清除。见[规则存储与版本](rule-storage-and-versioning.md)和[领域接合](domain-integration.md)。

## 后续实施批次

按表自上而下实施。每批只触及所列责任及直接调用者。凡改生产类或打包资源，均重建并审计候选 WAR 的字节数、SHA-256、条目数、必要及禁止条目；跨批复用测试须说明候选、受影响行为及复用依据。各批最小验收如下。

| 批次与依赖 | 入口、允许改动与受保护行为 | 验证、同步文档与完成条件 |
| --- | --- | --- |
| 1 · 默认构建去除 MySQL 驱动依赖。可先行 | 从根 `pom.xml`、`MySqlIntegrationTestSupport`、`MySqlIntegrationIT` 和维护脚本入手。移出默认 Connector/J；测试用 Tomcat DBCP 由这组 MySQL 测试引用，须一起核对测试编译路径。确需旧驱动的隔离 JDBC 入口改为显式选用。保留 SQLite JDBC、嵌入式 HTTP 测试容器、规则夹具和随附清单生成步骤。 | 在空本地 Maven 仓库检查默认依赖解析、测试编译、有实际用例的定向测试及打包，核对下载记录、依赖树及 WAR。默认链路不得解析 Connector/J，显式工具只归独立入口。同步[配置](../../configuration.md)、[测试](../../testing.md)及[数据库](../../database.md)。此批解决构建依赖，剩余 WAR 类归下两批。 |
| 2 · 提取 SQLite 仍消费的领域协议。可与批次 1 独立开发，须先于删除适配器 | 从 `CampaignDomainEngine`、`CampaignDomainCodec` 及其读写端追溯共用的 Material、Request、Plan、身份与完整历史校验。移动类型时同步调用者、反射类型标签和夹具，保留当前模型的规范字节、请求摘要和因果历史。将业务计算、事实、作者读取及规范编码与持有 MySQL 连接或事务的类分开。 | 定向覆盖规则装入、聚合编解码、请求摘要重算、初始及当前状态、完整历史、损坏投影拒绝和未决动作恢复；以独立字段/字节期望及真实 SQLite 文件检验读写。对照[能力映射](domain-integration.md#sqlite-retained-capabilities)和[数据映射](storage-data-map-and-rollout.md)更新实际调用关系，核实每处 SQLite 引用都有归属。 |
| 3 · 移除运行库与来源库的专属交付。依赖 1、2 | 运行侧沿旧 Host/API、事务服务、DAO、format 1/内部 format 2、运行迁移与诊断清理；来源侧沿离线安装、接受、分区镜像、来源迁移、grants、脚本与专属测试清理。按清单逐个处置共享调用者。旧库维护如有独立需求，单列交付物、文档、账号与验收入口；产品构建、WAR、安装及验收不得依赖它。 | 先把保留行为及失败断言接到 SQLite/文件/HTTP，再移除专属测试。比对源码、依赖树、编译输出、WAR 实际条目和容器入口，核实两侧均已退出，SQLite 配置错误仍可诊断。同步根导航、架构、数据库、配置和测试入口。真实库、账号和原件按[数据库说明](../../database.md)保管。 |
| 4 · 验收无 MySQL 候选。依赖前三批形成的同一候选 | 在两套可重建、独立的环境执行[验收方案](../../testing.md#mysql-free-acceptance)。核对服务、客户端、驱动、JNDI、凭据和可用连接的缺席。覆盖默认启动、错误配置、内置与外部规则、新建、业务/玩家、全文件跨环境打开、原库残留恢复及安全故障。 | 按能力记录实际进程退出码、执行/失败/错误/跳过数、平台、故障观察与 WAR 审计。平台专属检查在相应系统留证，无法证明的一项保持待验。同步测试与部署指南。全部目标有对应的有效结果后才能认定产品退役完成。 |

SQLite 默认装配已经切换。本版未设置环境变量和 Context 参数时，使用 Tomcat 运行身份 home 下的私有根；所选根无效、不能初始化或已占用时，readiness 返回相应 SQLite 错误，禁止回退 JNDI。验收须同时测无显式配置的正常启动和根不可用的失败路径。候选验收不执行生产替换；真实数据导出、迁移、账号调整、部署和删除各有独立授权与恢复边界。见[配置](../../configuration.md)和[部署](../../deployment.md)。

遭遇内直接定位和检定分支原子定位的 MySQL 执行入口、事务服务和 JDBC 写入路径已随 [PR #87](https://github.com/liii932/local-srd51-campaign-tool/pull/87) 删除。冻结的 v1 效果键和已应用库的迁移记录继续按原发布身份保管。两项业务能力将在 SQLite 场景与检定事务中实现，当前行为和验收责任见[领域接合表](domain-integration.md#支持边界和设计修订)。后续交付由 [#89](https://github.com/liii932/local-srd51-campaign-tool/issues/89) 跟踪。

## 语言与工具双分区的责任去向

[议题 #10](https://github.com/liii932/local-srd51-campaign-tool/issues/10) 的交付基线已可核实。[PR #11](https://github.com/liii932/local-srd51-campaign-tool/pull/11) 的合并提交 `630a61f` 直接接在 #9 的 `3411fe8` 之后。下表按该议题的验收对象区分已有交付与单路线仍需证明的内容。

| #10 的验收对象 | 结论与依据 |
| --- | --- |
| 独立差异与合同同步 | **已完成。** #11 在包含成长/资源修正的基线上交付双分区代码、迁移、核验 SQL、权限模板、目录/安装/运行合同和测试入口。当前[工具目录合同](../../rules/tool-catalog-partition.md)仍限定为熟练引用目录。 |
| 正式作者材料及严格读取 | **目录已交付，内容校验继续需要。** 18 项语言、37 项工具逐项保留稳定键、显示名、说明、分类、来源页和显式排序。作者侧严格拒绝坏形状、整数、分类及 Unicode，存储读取拒绝坏行。正式 DRAFT 内容使用[独立字段矩阵及规范向量](../../rules/language-author-package.md#独立验证)，应用读取器另用固定夹具。不能将这份 DRAFT 目录直接当作可玩 ZIP。 |
| 双分区安装、来源 V002/运行 V020、只读来源读取 | **原链路已交付，产品责任由当前路线替代。** #11 包含发布代次、永久安装事实、原接受重放、完整后像和事务失败判据，以及 `rule_tool`、`runtime_rule_tool` 和只读适配器。后续产品以本机规则库准确原包、导入/接受账本及冻结组合承担安装与读取；新写入无须经过跨库表。既有账本与所对应的读取材料独立保管。见[双分区链路](../../rules/language-partition-equivalence.md)。 |
| UUIDv4 身份对、调用方事务和断源后的运行镜像 | **原链路已交付，产品责任由当前路线替代。** `JdbcRuntimeCharacterCatalogRepository` 写入后实际 SELECT 读回，调用方拥有事务。SQLite 改由本次运行的冻结材料和工作库承担执行；必须单独验证准确绑定、错配拒绝、晚写回滚、脱离来源后的当前运行及同版恢复。见[领域接合](domain-integration.md#sqlite-retained-capabilities)。 |
| 跨边界的独立六字段与规范字节 | **原链路已交付，独立判据继续需要。** `CharacterCatalogChainTest` 及独立向量固定 55 条定义、110 条属性、零关系；行序、技术 ID/代次及两端同时漏字段分别有判据。DRAFT 作者向量与 SQLite 可玩包、组合和 format 3 文件的向量各按自己的协议保留。见[工具目录核验](../../rules/tool-catalog-partition.md)。 |
| 脚本、候选 WAR、真实隔离 JDBC 重放和最小权限 | **#10 范围已有验收记录。** #11 分列静态检查、脚本测试、应用测试与 WAR 审计，并记录真实临时 MySQL 的 22 个迁移、有实际执行的双分区 JDBC 场景、权限拒绝及经新连接确认的整笔回滚。该记录对应 #11 的候选；本计划的 SQLite 候选须按批次 4 取证。今后针对旧库的新实机核验仅在明确选择独立维护交付时安排。见[原始验收记录](https://github.com/liii932/local-srd51-campaign-tool/pull/11)。 |

完整 SRD 的其余机制由 [#47](https://github.com/liii932/local-srd51-campaign-tool/issues/47) 跟踪，真实 LAN 设备接入由 [#62](https://github.com/liii932/local-srd51-campaign-tool/issues/62) 跟踪。周期 AUTO、全部未来 v2 机制和完整玩家交互均不作为当前退役的前置。保留业务中仍缺的证明按能力表和验收表补齐。议题状态的变更与本计划、候选验收及实际部署分别记录。
