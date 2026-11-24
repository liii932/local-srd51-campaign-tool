# SQLite 单路线交付与 MySQL 资料保管

本版运行、构建、测试和候选 WAR 使用 SQLite。原运行/来源 MySQL 的代码、SQL 和独立维护入口随匹配的历史版本保管。退役范围源于[议题 #81](https://github.com/liii932/local-srd51-campaign-tool/issues/81)，双平台候选、生产替换和真实资料处置各有独立证据。[构建配置](../../../pom.xml)

## 实施依据

| 需要判断什么 | 以何处为准 |
| --- | --- |
| 哪些装配已退出、哪些类和资源仍被编译或交付，哪些数据须保管 | [运行库、来源库及共享代码的依赖清单](storage-data-map-and-rollout.md#mysql-dependency-inventory)、[数据库与账号](../../database.md) |
| 旧服务的哪项业务由 SQLite 承担，哪里只有有限接合 | [保留能力、调用者及缺口](domain-integration.md#sqlite-retained-capabilities) |
| 哪些用例迁移断言、哪些随适配器退出，怎样取得候选证据 | [测试处置与无 MySQL 验收](../../testing.md#mysql-free-acceptance)、[部署与回滚边界](../../deployment.md) |

每个活动战役持有自己的 SQLite 权威事实、冻结材料和完整历史。本机规则库存准确原包与接受账本。业务请求须先预检，再由服务端取随机结果，同一事务写入事实、效果、版本、历史及幂等结果。文件打开创建新运行，同版残留恢复沿用原工作库及未决事实。DM loopback、默认关闭的玩家 HTTPS、独立会话及逐人裁剪随这条路径保留。具体接口与文件合同见[产品说明](../../product.md)和[存储运行合同](../../storage-vitality-loop.md)。

规则验证分三处承担。SQLite 默认使用 WAR 的四包有序标准组合，也支持明确选择的本机准确已接受组合。format 3 文件打开重新核对规则，准确标准组合可由 WAR 提供，否则须有匹配的本机已接受原包。同版残留恢复核验战役内冻结材料。完整 SRD 作者目录仍是 DRAFT，原 MySQL 的内部 archive format 2 有独立资格门。当前应用消费可玩组合和 DRAFT 作者协议；历史片段由原制品解释。见[规则存储与版本](rule-storage-and-versioning.md)和[领域接合](domain-integration.md)。

## 交付范围与验收

| 范围 | 当前责任与验证 |
|---|---|
| Maven 与 WAR | 默认 POM 使用 SQLite JDBC 与测试用嵌入式 Tomcat。使用空库解析全部 scope，核对实际下载、编译输出和候选 WAR 的必要及禁止条目。 |
| 领域协议 | 纯角色/战斗请求、初始计划、引擎及 codec 保留准确规则身份、类型标签、原骰与请求摘要。使用组合、文件、恢复和纯领域测试核验。 |
| 已退役的专属交付 | 两条 MySQL 迁移、权限/测试脚本、JDBC 仓储、诊断、来源安装和内部恢复均由匹配版本保管。DRAFT 目录使用独立作者矩阵，schema 2 原包使用严格读取。 |
| [DM 直接及分支定位](https://github.com/liii932/local-srd51-campaign-tool/issues/89) | 在已入场角色所属的 DM 裁定焦点场景内直接安置；两种检定的成功和失败分支均先预检，再随选中后果、历史、位置版本及收据共同提交。见[空间](world-and-scene-spaces.md)与[领域接合](domain-integration.md#sqlite-retained-capabilities)。 |
| 可发布候选 | 用同一 WAR 在独立环境核对外部 Tomcat、两种平台的文件和进程边界、成员授权、移机打开与原库恢复；候选报告须区分平台专属验证、环境清洁度和缺证项。[可重复验收](../../testing.md#mysql-free-acceptance) |

SQLite 默认装配已经切换。本版未设置环境变量和 Context 参数时，从 Tomcat 运行身份的 `user.home` 解析私有根。验收覆盖默认根有效、无可解析的根、所选根无法初始化及根已占用时的就绪结果。真实数据导出、迁移、账号调整、生产部署和删除各有独立授权与恢复边界。见[配置](../../configuration.md)和[部署](../../deployment.md)。

## 语言与工具双分区的责任去向

[议题 #10](https://github.com/liii932/local-srd51-campaign-tool/issues/10) 的交付基线已可核实。[PR #11](https://github.com/liii932/local-srd51-campaign-tool/pull/11) 的合并提交 `630a61f` 直接接在 #9 的 `3411fe8` 之后。下表按该议题的验收对象区分已有交付与单路线仍需证明的内容。

| #10 的验收对象 | 结论与依据 |
| --- | --- |
| 独立差异与合同同步 | **已完成。** #11 在包含成长/资源修正的基线上交付双分区代码、迁移、核验 SQL、权限模板、目录/安装/运行合同和测试入口。当前[工具目录合同](../../rules/tool-catalog-partition.md)仍限定为熟练引用目录。 |
| 正式作者材料及严格读取 | **目录已交付，内容校验继续需要。** 18 项语言、37 项工具逐项保留稳定键、显示名、说明、分类、来源页和显式排序。作者侧严格拒绝坏形状、整数、分类及 Unicode，存储读取拒绝坏行。正式 DRAFT 内容使用[独立字段矩阵及规范向量](../../rules/language-author-package.md#独立验证)，应用读取器另用固定夹具。不能将这份 DRAFT 目录直接当作可玩 ZIP。 |
| 双分区安装、来源 V002/运行 V020、只读来源读取 | **原链路已交付，产品责任由当前路线替代。** #11 包含发布代次、永久安装事实、原接受重放、完整后像和事务失败判据，以及 `rule_tool`、`runtime_rule_tool` 和只读适配器。后续产品以本机规则库准确原包、导入/接受账本及冻结组合承担安装与读取；新写入无须经过跨库表。既有账本与所对应的读取材料独立保管。见[双分区链路](../../rules/language-partition-equivalence.md)。 |
| UUIDv4 身份对、调用方事务和断源后的运行镜像 | **原链路已交付，产品责任由当前路线替代。** `JdbcRuntimeCharacterCatalogRepository` 写入后实际 SELECT 读回，调用方拥有事务。SQLite 改由本次运行的冻结材料和工作库承担执行；必须单独验证准确绑定、错配拒绝、晚写回滚、脱离来源后的当前运行及同版恢复。见[领域接合](domain-integration.md#sqlite-retained-capabilities)。 |
| 跨边界的独立六字段与规范字节 | **原链路已交付，独立判据继续需要。** `CharacterCatalogChainTest` 及独立向量固定 55 条定义、110 条属性、零关系；行序、技术 ID/代次及两端同时漏字段分别有判据。DRAFT 作者向量与 SQLite 可玩包、组合和 format 3 文件的向量各按自己的协议保留。见[工具目录核验](../../rules/tool-catalog-partition.md)。 |
| 脚本、候选 WAR、真实隔离 JDBC 重放和最小权限 | **#10 范围已有验收记录。** #11 分列静态检查、脚本测试、应用测试与 WAR 审计，并记录真实临时 MySQL 的 22 个迁移、有实际执行的双分区 JDBC 场景、权限拒绝及经新连接确认的整笔回滚。该记录对应 #11 的候选；SQLite 候选须按双环境流程取证。今后针对旧库的新实机核验仅在明确选择独立维护交付时安排。见[原始验收记录](https://github.com/liii932/local-srd51-campaign-tool/pull/11)。 |

完整 SRD 的其余机制由 [#47](https://github.com/liii932/local-srd51-campaign-tool/issues/47) 跟踪，真实 LAN 设备接入由 [#62](https://github.com/liii932/local-srd51-campaign-tool/issues/62) 跟踪。周期 AUTO、全部未来 v2 机制和完整玩家交互均不作为当前退役的前置。保留业务中仍缺的证明按能力表和验收表补齐。议题状态的变更与本计划、候选验收及实际部署分别记录。
