# Local 5E Campaign Tool

Local 5E Campaign Tool 是一个面向单机游戏主持人的本地 Web 工具，使用 Java、Tomcat
和 JDBC 管理战役并执行 D&D 5e 2014 规则；显式 SQLite 运行模式承接当前有限领域，
未配置时仍使用 MySQL 简化模式。项目目标是完整覆盖 SRD 5.1 中可公开实现的
角色、冒险、战斗、法术、装备、怪物和状态规则，同时保留可持久化、可审计、可恢复的单 DM
操作闭环。

项目处于原型阶段，只保证当前应用版本的运行，不承诺跨版本延续战役或读取旧存档。
设计可按当前需求和验证结果修订，现有实现不构成永久兼容基线；范围见[产品说明](./docs/product.md)。

> 当前实现包含可选 SQLite 有限运行规则与默认 MySQL 简化模式，尚未完成全部 SRD 5.1。DM 入口固定为
> `http://127.0.0.1:8080`，由本机 DM 操作。SQLite 模式另有默认关闭的独立 HTTPS 玩家原型；
> 实际局域网启用、设备信任和验收须另行完成。公网、匿名展示和公开 API 仍不在范围内。

## 当前已实现

显式设置 `DND_STORAGE_ROOT` 后，`/host` 进入 [SQLite 战役操作台](./docs/storage-vitality-loop.md)。
首次体验可按[短冒险流程](./docs/storage-vitality-loop.md#跑一段短冒险)完成建队、探索、战斗和保存后继续；
隔离本地启动与浏览器验收见[测试指南](./docs/testing.md#sqlite-结构完整文件与运行验收)。
选择 `srd51-vitality@1`、`srd51-healing@1`、`srd51-runtime@1` 的准确有序组合后，可以：

- 创建标准数组的有限 fighter、Life-domain cleric 或 Wizard PC/NPC，保存初始卡、当前卡和完整角色历史；
- 由现有纯规则消费者执行已接入的成长/ASI/多职业、熟练、检定、装备、健康、休息与资源命令；
- 使用有限普通战斗、移动、机会攻击、反应/继续、Bless、Cure Wounds、Shield、Counterspell、Burning Hands、法师书/准备和奥术回想；
- 保存有限怪物吐息/充能、持续效果、DM 抗魔区域、节点位置、简单物品、角色状态与消息根历史；
- 管理独立的 DM-managed 自由卡、基础字段、职业等级和熟练，执行 MANUAL/SAVING_THROW、
  有序检定效果与简易遭遇名单/阵营；
- 在一个 SQLite 事务提交完整领域后果、历史、版本与幂等结果；保存初始及当前完整 JSON，下载/上传导入、同版重开，显式恢复或精确放弃残留。

有限规则角色能力使用应用随版本交付的[四个 schema-2 包](./rule-packages/srd51-runtime/package-guide.md)，
经过生产 ZIP reader、组合及完整类型化投影核验。它们不是完整 SRD `RELEASED` 发布，
不借用旧完整规则族的 `DRAFT` 身份。Fighter 支持到职业 6 级、Life-domain cleric 到 1 级、
Wizard 到 5 级；未接入的职业特性保留目录元数据，不自动授予。
准确领域覆盖、现行能力差异及剩余采用门见[领域接合表](./docs/design/v2/domain-integration.md)。

未设置 `DND_STORAGE_ROOT` 时，原 MySQL 简化模式仍是启动默认，保留冻结规则
`dnd5e2014_srd51_se_v1`、既有 Host 入口和格式 1 存档。SQLite 模式启动后关闭这些旧业务路由，
不会双写、读取旧库补状态或在失败时回退。现有 MySQL 数据、迁移账本和外部账号必须独立保管；
本地代码接合不表示已迁移、部署或删除真实数据。

来源 MySQL 的作者包安装/接受、内部快照与规则算法测试继续具有各自责任。
通用外部规则安装、完整 SRD 发布、完整通用 archive format 2 和跨版本存档兼容
不由此有限应用模型自动完成。当前业务/根/接受在一个 SQLite 事务提交，未知结果显式
恢复；每命令耐久和 INITIAL/MANUAL/FINAL 承担保存责任，周期 AUTO/轮换仍为后续目标。
DM-managed 自由卡与有限规则角色各自校验，不自动互相转换；具体操作和限制见
[SQLite 战役操作台](./docs/storage-vitality-loop.md)。

## 产品目标

完整规则目标以 D&D 5e 2014 的 SRD 5.1 为规则与许可边界，包括：

- 完整角色构筑、升级、多职业、种族、背景、职业能力、熟练项和专长；
- 通用检定、豁免、攻击、伤害、治疗、死亡豁免、休息和资源恢复；
- 先攻、轮次、回合、动作、附赠动作、反应、移动、距离、范围和状态；
- 法术目录、已知/准备法术、法术位、成分、目标、区域、专注、仪式和升环；
- 武器、护甲、冒险装备、货币、价格、负重、消耗品、魔法物品和同调；
- 完整 SRD 怪物属性块、能力、动作、反应、抗性、免疫、CR/XP 和遭遇支持；
- 可确定规则由服务端自动计算；需要叙事判断的规则通过类型化 DM 裁定记录处理。

完整范围、自动化边界和兼容原则见[完整 SRD 5.1 覆盖目标](./docs/rules/srd-5.1-complete.md)。
本文所列目标不是对当前版本已完成功能的声明。

[产品说明](./docs/product.md)区分当前行为和 v2 使用流程；[v2 设计导航](./docs/design/v2/README.md)
记录[模组接口与组合](./docs/design/v2/module-interface-baseline.md)、[私有运行存储](./docs/design/v2/rule-storage-and-versioning.md)、
[完整战役文件](./docs/design/v2/save-and-recovery.md)及[生命周期](./docs/design/v2/runtime-and-transactions.md)。
目标以应用随版本交付的[JSON 表结构声明](./docs/design/v2/storage-schema-and-planning.md)，
按规则、机制、历史与未决状态闭包建立每次打开独占的 SQLite 运行空间，使用完整 JSON
保存与同版残留显式恢复。[数据覆盖与替换路线](./docs/design/v2/storage-data-map-and-rollout.md)
列出全部现行表组、公共接口和分步验收。当前 SQLite 已接合有限规则下的角色、冒险与战斗
聚合及同版完整文件；逐领域采用和剩余差异以[接合表](./docs/design/v2/domain-integration.md)为准，
完整 SRD 与通用完整 v2 发布门仍未完成。
具体机制的设计与采用按[机制接入责任清单](./docs/design/v2/mechanism-integration-checklist.md)逐项核对。
设计进入项目文档不表示功能已实现或完整规则已发布。

## 产品边界

DM 通过本机 loopback 操作。SQLite 的可选玩家原型使用独立 HTTPS 入口，支持邀请审批、
PC 控制分配、明确共享、有限服务端候选动作、DM 裁定和操作收据查证；玩家只能接收获准
投影，不能访问 Host 接口或完整战役文件。监听需要显式配置，应用不会自行创建 Connector。
使用范围见[运行合同](./docs/storage-vitality-loop.md)，边界见[安全说明](./docs/security.md)，
隔离验证见[测试指南](./docs/testing.md#玩家参与原型验证)。真实局域网部署、证书信任和外部设备验收
仍需完成。

以下仍不属于目标范围：

- 公网、端口转发、云端中继、全局玩家账号、多 DM、匿名旁观、聊天和投票；
- `/display`、`/api/public/*` 和无需认证的战役投影；
- WebSocket、推送、脚本插件、模组/用户提供的任意 DDL 和 JSON 数据库属性袋。
  目标运行空间由应用自有 JSON 声明表、列、键、约束与依赖，编译为 SQLite DDL；
  模组作者提供规则内容，应用负责结构和执行能力。

完整范围与边界见[架构文档](./docs/architecture.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 运行时 | Java 21 |
| Web 容器 | Apache Tomcat 10.1，外部 WAR 部署 |
| Web API/UI | Jakarta Servlet 6.0、JSP、原生 JavaScript |
| 数据库 | 可选私有 SQLite/JDBC；默认 MySQL 8.0/JNDI 简化模式 |
| JSON | Gson 2.14 |
| 构建 | Maven 3.9 |
| 测试 | JUnit 5、可选独立 MySQL 集成测试 |

## 架构概览

```text
本机浏览器
  └─ http://127.0.0.1:8080/host
       └─ Servlet / JSP / JavaScript
            └─ Service：验证、事务、幂等与规则边界
                 └─ JDBC Repository
                      ├─ 显式 DND_STORAGE_ROOT → 私有 SQLite 战役
                      └─ 未配置 → Tomcat JNDI / MySQL 简化模式
```

应用按职责分为 `web`、`security`、`service`、`persistence` 和 `module` 包。Servlet
不直接执行 SQL，Repository 不决定业务权限，JSP 不保存权威状态；跨表业务操作由 Service
拥有事务边界。

## 环境要求

- JDK 21；
- Maven 3.9 或兼容版本；
- Apache Tomcat 10.1；
- SQLite 模式使用原生 Windows 本地 NTFS/私有 ACL，或 Linux/WSL 的 POSIX 私有文件系统；文件锁、硬链接及平台落盘边界见[配置](./docs/configuration.md#sqlite-当前应用存储配置)；原 MySQL 模式需要 MySQL 8.0；
- 支持现代 JavaScript 的本机浏览器。

应用不提供内嵌服务器，也不能通过 `java -jar` 独立启动。Windows 正式路线为外部
Tomcat 10.1 + WAR + 主机本地 SQLite；配置真实启动身份和私有根后，通过
`/api/host/storage/readiness` 确认就绪，打开 `http://127.0.0.1:8080`。
原 MySQL 模式的 Connector/J 和数据库凭据由外部 Tomcat 提供，不得打包进 WAR。
隔离测试和真实 WAR 验收见[测试指南](./docs/testing.md#原生-windows-外部-tomcat-候选验收)，
正式切换及日常启停遵循[部署流程](./docs/deployment.md)。

## 构建与测试

在仓库根目录执行：

```powershell
mvn clean verify
```

成功后生成：

```text
target/dnd-tool-se-0.1.0-SNAPSHOT.war
```

构建必须以 Maven 最终退出码、测试总数以及 `BUILD SUCCESS` 为准。定向测试、完整验证、
WAR 审计与手工验收方法见[测试指南](./docs/testing.md)。

需要运行真实 JDBC 集成测试时，必须使用独立、一次性的可写测试库；普通 `mvn clean verify`
不会连接本机业务库。准备和显式启用方法也在[测试指南](./docs/testing.md)中。

## 数据库与部署

本项目使用外部 Tomcat 根上下文部署；SQLite 运行模式按[配置](./docs/configuration.md#sqlite-当前应用存储配置)
设置 `DND_STORAGE_ROOT`，由应用建立私有工作库，无需运行 MySQL 迁移。以下步骤用于原
MySQL 模式及其独立账号/历史保管，数据库维护和 WAR 部署分别授权：

1. 使用专用 migrator 账号按顺序应用尚未执行的迁移；
2. 使用只读验收脚本和专用只读账号核对结构；
3. 单独授予运行账号所需的最小权限；
4. 在 Tomcat 外配置 `java:comp/env/jdbc/DndToolSE`；
5. 将已构建并审计的 WAR 部署为 `ROOT.war`；
6. 启动 MySQL 和 Tomcat 后访问 `http://127.0.0.1:8080/host`。

当前自动化使用 `dnd_tool_se` 作为本地部署运行库，使用 `dnd_tool_se_it` 执行临时表集成测试；
完整 V001—V042 迁移链只在物理隔离、可销毁的 MySQL 实例中以原名 `dnd_tool_se` 原样重放，不在
运行实例内创建改名 schema，也不改写迁移 SQL。运行、只读核验、正式迁移、集成测试和隔离重放
使用不同账号。现有少量测试数据的运行库可继续作为开发部署目标，但不能作为集成测试或迁移重放
目标，使用前仍需盘点和备份。精确账号权限与 Agent 检查点见[数据库说明](./docs/database.md)。

现有 V001—V018 迁移一旦应用即不可修改或重跑。不要把数据库口令放入命令行、Git、WAR、
日志或 Java 启动参数。先阅读[数据库说明](./docs/database.md)与[配置说明](./docs/configuration.md)，
再按[部署指南](./docs/deployment.md)执行可回滚部署。可参考以下无秘密示例配置：

- [Tomcat ROOT Context](./config/tomcat/ROOT.xml.example)
- [Tomcat setenv](./config/tomcat/setenv.bat.example)

## 安全与数据约束

- Host 入口只接受精确的 `http://127.0.0.1:8080` origin，不信任 `X-Forwarded-*`；
- 可选玩家入口要求准确配置的 HTTPS 地址、端口和 origin，只开放玩家页面、资产及有限 API；
- 主机写请求使用 Session、CSRF、Origin/Referer、Fetch Metadata、状态 epoch、对象版本和幂等键；
- 客户端不能指定骰点、选中骰、派生修正、最终结果、效果算法或规范业务身份；
- 未知、重复、未发布、摘要不一致或不受支持的冻结规则必须失败关闭；
- 权威状态、事件、骰子、效果、字段变化、版本和幂等结果在同一事务提交或回滚；
- MySQL 格式 1 存档使用稳定业务键，不包含数据库 ID、行版本或完整内部历史；SQLite 完整 JSON
  保留当前有限模型的状态、历史与规则材料，排除玩家凭据、私钥和本机控制材料；
- 数据库账号按迁移、运行和只读验收职责分离。

## 项目结构

```text
src/main/java/com/dndtool/
  module/          不可变规则模型、内置目录、规范编码与哈希校验
  persistence/     JDBC Repository、模式查询与持久化边界
  security/        loopback 主机边界和请求安全过滤器
  service/         业务验证、诊断编排、事务、幂等和存档服务
  web/             JNDI/启动装配、Servlet、HTTP DTO 与页面支持
src/main/resources/db/migration/
                    V001—V042 只增不改运行数据库迁移
src/main/webapp/    JSP、web.xml 和主机端 JavaScript
src/test/           单元、边界、结构和集成测试
docs/               架构、规则、配置、安全、测试和运维指南
database/           只读验证、最小授权与独立测试库辅助 SQL
config/tomcat/      不含秘密的外部 Tomcat 配置示例
tools/              数据库备份和独立规则哈希验证工具
```

## 文档索引

| 文档 | 内容 |
|---|---|
| [快速开始](./docs/getting-started.md) | 环境前提、构建、配置和最小启动验收 |
| [产品说明](./docs/product.md) | 当前行为、v2 使用流程与暂缓范围 |
| [SQLite 当前有限战役](./docs/storage-vitality-loop.md) | 规则选择、领域运行、严格完整文件与显式恢复 |
| [领域接合表](./docs/design/v2/domain-integration.md) | 当前实际覆盖、旧能力差异和剩余接合门 |
| [v2 设计基线](./docs/design/v2/README.md) | 已确定的目标设计、实现差异和采用前提 |
| [局域网玩家参与](./docs/design/v2/player-participation.md) | 玩家参与设计与原型采用边界 |
| [JSON 结构声明与战役建库](./docs/design/v2/storage-schema-and-planning.md) | 有限声明格式、依赖闭包、确定性计划、示例、初始化与结构恢复 |
| [存储数据覆盖与替换路线](./docs/design/v2/storage-data-map-and-rollout.md) | 当前表组/领域/所有者映射、公共接口、配置切换、实施步骤与验收 |
| [Agent 项目上下文](./docs/agent-context.md) | 新会话使用的精简项目地图和权威资料索引 |
| [架构](./docs/architecture.md) | 产品范围、组件、事务与信任边界 |
| [规则数据库分离设计](./docs/rule-database-separation.md) | 当前存储、双 schema 职责及 v2 采用边界 |
| [schema 2 制品与安装接受](./docs/rules/schema-2-installation-and-acceptance.md) | 原始 ZIP、生产摘要、审阅绑定、独立接受和提交查证 |
| [schema 2 来源到运行快照](./docs/rules/schema-2-runtime-snapshot.md) | 来源只读复核、类型化快照和完整链、原子转存、查证与准确恢复绑定 |
| [schema 2 初始语言／工具授予](./docs/rules/schema-2-proficiency-grants.md) | 真实来源与稳定槽、原子授予字段、跨模组选择和固定快照计划 |
| [schema 2 检定、装备与健康作者来源](./docs/rules/schema-2-foundation-authoring.md) | 四类类型化定义、字段和引用、负修正、清空、纯组合与真实来源边界 |
| [schema 2 角色事实存储](./docs/rules/schema-2-character-fact-storage.md) | 准确镜像、检定/装备/健康输入与结果、前后状态、历史预算及仓储直接恢复 |
| [schema 2 检定、装备与健康基础](./docs/rules/schema-2-character-foundations.md) | 真实检定、装备实例、健康变化、命令幂等、完整局部历史与直接恢复 |
| [schema 2 战斗作者来源](./docs/rules/schema-2-combat-authoring.md) | 战斗算法、真实近战武器及步行来源、来源 V008 和运行 V032 镜像 |
| [schema 2 内部战斗与动作继续](./docs/rules/schema-2-combat.md) | 真实攻击/移动/机会攻击、回合、倒地/掉落、共享秒时钟、完整历史与恢复 |
| [schema 2 施法作者来源](./docs/rules/schema-2-spell-authoring.md) | 生命领域/法师准确来源、学习名单、固定抗魔环境与 V009/V010、V035/V037 镜像 |
| [schema 2 有限施法与持续效果](./docs/rules/schema-2-spells.md) | 真实成本/法术书、Shield/Counterspell/Ready、独立压制、专注/到期及探索转移 |
| [schema 2 有限怪物与火焰吐息](./docs/rules/schema-2-monster-breath.md) | 类型化怪物/动作、完整点格目标、共享伤害/豁免、充能、专注共同切面与局部恢复 |
| [共同业务写入与初始化准备](./docs/rules/business-write-transactions.md) | 全部写入口、永久守卫、事务故障隔离及绑定初始计划的原子准备 |
| [SRD 5.1 本地原文索引](./docs/references/srd-5.1/README.md) | 按主题定位 403 页检索文本，核对本地 PDF 与官方出处 |
| [完整规则目标](./docs/rules/srd-5.1-complete.md) | SRD 5.1 功能覆盖、自动化边界和兼容要求 |
| [冻结 v1 规则规范](./docs/rules/srd-5.1.md) | 现有稳定键、算法、范围、规范化和模组关系 |
| [职业特性 v2](./docs/rules/class-features-v2.md) | 12 职业/子职业特性矩阵、资源恢复与阻断边界 |
| [多职业共享法术位 v2](./docs/rules/multiclass-spell-slots-v2.md) | DRAFT 职业贡献、共享位上限与未完成边界 |
| [Archive format 2 角色投影](./docs/rules/archive-format-2-character-state.md) | DRAFT 稳定事件闭包、严格 codec 与恢复来源 |
| [配置](./docs/configuration.md) | JNDI、数据库凭据与 loopback Connector |
| [数据库](./docs/database.md) | 当前迁移链、保留数据库或重建的边界与账号职责 |
| [安全](./docs/security.md) | 请求边界、权威输入、最小权限与秘密处理 |
| [测试](./docs/testing.md) | 定向测试、完整验证、集成测试和 WAR 审计 |
| [公开仓库发布检查清单](./docs/public-release-checklist.md) | 私有迁移仓库公开前的内容审计与 GitHub 设置确认 |
| [部署](./docs/deployment.md) | 可回滚的 Tomcat 根上下文部署与验收 |
| [备份与恢复](./docs/backup-and-restore.md) | 应用存档和数据库级恢复边界 |
| [故障排查](./docs/troubleshooting.md) | 启动、数据库、请求和测试常见故障 |

## 许可证

本项目的原创软件代码采用 [Apache License 2.0](./LICENSE) 授权。内置规则文档和种子数据中
源自 SRD 5.1 的部分采用 [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/)
授权。适用范围、法定署名和修改说明见 [NOTICE](./NOTICE)。

## 参与开发

提交修改前请先阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)；自动化代理还应遵守 [AGENTS.md](./AGENTS.md)。

- 按完整 SRD 5.1 目标做最小领域切片，并准确区分当前行为与目标行为；
- 不修改已经应用的编号迁移，应新增下一条前向迁移；
- 先运行相关定向测试，再按变更风险运行完整验证；
- 生产代码或资源变化时审计生成的 WAR；
- 不提交构建产物、数据库秘密、Tomcat 外部配置、日志、备份或真实存档。
