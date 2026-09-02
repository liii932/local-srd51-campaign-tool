# Local 5E Campaign Tool

Local 5E Campaign Tool 是一个面向单机游戏主持人的本地 Web 工具，使用 Java、Tomcat
和 MySQL 管理战役并执行 D&D 5e 2014 规则。项目目标是完整覆盖 SRD 5.1 中可公开实现的
角色、冒险、战斗、法术、装备、怪物和状态规则，同时保留可持久化、可审计、可恢复的单 DM
操作闭环。

> 当前实现仍是纯本机的冻结简化 v1，并未完成全部 SRD 5.1 功能。正式入口仅为
> `http://127.0.0.1:8080`；局域网/公网、HTTPS、玩家账号、`/display`、
> `/api/public/*` 和公开数据投影不属于当前产品目标。

## 当前已实现

- 创建、激活、归档和恢复单一活动战役；
- 使用显式内置发布版注册表；当前默认仍是经过哈希校验的
  `dnd5e2014_srd51_se_v1` release 1 / canonical 1 / archive 1；
- 已为完整规则族 `dnd5e2014_srd51_se` 固定 release 1 / canonical 2 / archive 2
  映射，但该身份仍为 `DRAFT`，不能创建战役、读取目录或导入存档；
- 已在该 DRAFT 身份下建立 12 职业/子职业的 236 项特性三态矩阵，以及职业资源的类型化
  容量/休息恢复边界，并建立多职业先决条件/熟练增量、ASI 和可扩展专长状态（当前仅
  Grappler）的 DRAFT 角色切片；多职业施法和 Grappler 战斗效果仍阻断。该内部规则基础不
  等于发布或开放可达业务入口，完整规则族会保持 DRAFT，直到主要规则领域完成跨领域发布候选验收；
- 创建 PC/NPC，维护角色字段、职业等级、技能/豁免熟练和简单物品；
- 执行普通、优势和劣势 d20 检定，并保存候选骰、修正、DC、结果和内部事件；
- 通过封闭的类型化效果调整字段、授予物品、记录消息或更新合法实体位置；
- 使用内置节点地图管理队伍位置、最小遭遇状态、参与者阵营和实体位置；
- 导出、严格预览并整战役导入 UTF-8 JSON 存档；
- 在 Tomcat/MySQL 重启后恢复权威状态和当前 DM 工作上下文。

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

## 产品边界

产品保持单机、单 DM，仅通过 loopback 提供服务。以下能力不属于当前产品目标；若要引入，
必须另立范围并完成独立的架构与安全评审：

- 局域网、公网或移动设备访问；
- HTTPS Connector、证书、CA 和防火墙入站配置；
- 玩家账号、角色所有权、邀请码、聊天、投票和外部写入；
- `/display`、`/api/public/*`、公开身份、公开配置、公开投影和公开事件；
- WebSocket、推送、脚本插件、动态 DDL 和 JSON 数据库列。

完整范围与边界见[架构文档](./docs/architecture.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 运行时 | Java 21 |
| Web 容器 | Apache Tomcat 10.1，外部 WAR 部署 |
| Web API/UI | Jakarta Servlet 6.0、JSP、原生 JavaScript |
| 数据库 | MySQL 8.0、JDBC、Tomcat JNDI/DBCP 连接池 |
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
                      └─ Tomcat JNDI DataSource
                           └─ MySQL 8.0
```

应用按职责分为 `web`、`security`、`service`、`persistence` 和 `module` 包。Servlet
不直接执行 SQL，Repository 不决定业务权限，JSP 不保存权威状态；跨表业务操作由 Service
拥有事务边界。

## 环境要求

- JDK 21；
- Maven 3.9 或兼容版本；
- Apache Tomcat 10.1；
- MySQL 8.0；
- 支持现代 JavaScript 的本机浏览器。

应用不提供内嵌服务器，也不能通过 `java -jar` 独立启动。Connector/J 和数据库凭据由外部
Tomcat 配置提供，不得打包进 WAR。

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

本项目使用外部 Tomcat 根上下文部署，数据库迁移和运行权限相互分离：

1. 使用专用 migrator 账号按顺序应用尚未执行的迁移；
2. 使用只读验收脚本和专用只读账号核对结构；
3. 单独授予运行账号所需的最小权限；
4. 在 Tomcat 外配置 `java:comp/env/jdbc/DndToolSE`；
5. 将已构建并审计的 WAR 部署为 `ROOT.war`；
6. 启动 MySQL 和 Tomcat 后访问 `http://127.0.0.1:8080/host`。

现有 V001—V017 迁移一旦应用即不可修改或重跑。不要把数据库口令放入命令行、Git、WAR、
日志或 Java 启动参数。先阅读[数据库说明](./docs/database.md)与[配置说明](./docs/configuration.md)，
再按[部署指南](./docs/deployment.md)执行可回滚部署。可参考以下无秘密示例配置：

- [Tomcat ROOT Context](./config/tomcat/ROOT.xml.example)
- [Tomcat setenv](./config/tomcat/setenv.bat.example)

## 安全与数据约束

- 业务入口只接受精确的 `127.0.0.1:8080` origin，不信任 `X-Forwarded-*`；
- 主机写请求使用 Session、CSRF、Origin/Referer、Fetch Metadata、状态 epoch、对象版本和幂等键；
- 客户端不能指定骰点、选中骰、派生修正、最终结果、效果算法或规范业务身份；
- 未知、重复、未发布、摘要不一致或不受支持的冻结规则必须失败关闭；
- 权威状态、事件、骰子、效果、字段变化、版本和幂等结果在同一事务提交或回滚；
- 存档使用稳定业务键，不包含数据库 ID、行版本、凭据、私钥或完整内部历史；
- 数据库账号按迁移、运行和只读验收职责分离。

## 项目结构

```text
src/main/java/com/dndtool/
  module/          内置规则目录、规范编码与哈希校验
  persistence/     JDBC Repository、模式诊断与持久化边界
  security/        loopback 主机边界和请求安全过滤器
  service/         业务验证、事务、幂等和存档服务
  web/             Servlet、HTTP DTO 与页面支持
src/main/resources/db/migration/
                    V001—V017 只增不改数据库迁移
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
| [Agent 项目上下文](./docs/agent-context.md) | 新会话使用的精简项目地图和权威资料索引 |
| [架构](./docs/architecture.md) | 产品范围、组件、事务与信任边界 |
| [规则数据库分离设计](./docs/rule-database-separation.md) | 静态规则目录启动加载、运行数据库及分阶段迁移边界 |
| [完整规则目标](./docs/rules/srd-5.1-complete.md) | SRD 5.1 功能覆盖、自动化边界和兼容要求 |
| [冻结 v1 规则规范](./docs/rules/srd-5.1.md) | 现有稳定键、算法、范围、规范化和模组关系 |
| [职业特性 v2](./docs/rules/class-features-v2.md) | 12 职业/子职业特性矩阵、资源恢复与阻断边界 |
| [Archive format 2 角色投影](./docs/rules/archive-format-2-character-state.md) | DRAFT 稳定事件闭包、严格 codec 与恢复来源 |
| [配置](./docs/configuration.md) | JNDI、数据库凭据与 loopback Connector |
| [数据库](./docs/database.md) | 迁移不可变性、账号职责与稳定合同 |
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
