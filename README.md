# Local 5E Campaign Tool

Local 5E Campaign Tool 帮助一名 DM 在本机主持 D&D 5e 2014 战役。打开 `http://127.0.0.1:8080`，输入战役名称，使用应用随附的标准规则开团。角色、行动、骰点和历史由本机 SQLite 保存。结束战役后可用完整文件继续游戏。可选的玩家 HTTPS 入口默认关闭。

本项目是原型，支持当前版本的战役文件。已实现的职业、法术和动作范围见[产品说明](./docs/product.md)。

## 当前已实现

DM 入口的 `/` 和 `/host` 均返回 `303`，转到 `/host/storage`。操作见 [SQLite 战役工作台](./docs/storage-vitality-loop.md)。
首次体验可按[短冒险流程](./docs/storage-vitality-loop.md#跑一段短冒险)完成建队、探索、战斗和保存后继续；
隔离本地启动与浏览器验收见[测试指南](./docs/testing.md#sqlite-结构完整文件与运行验收)。
使用标准规则即可开始。要使用外部包或家规组合，在“自定义规则组合”中逐包审阅、接受并预览。工作台可以：

- 创建标准数组的人类或矮人 fighter、Life-domain cleric 或 Wizard PC/NPC，保存初始卡、当前卡和完整角色历史。标准包矮人穿重甲时保有 25 尺步速，家规可分别调整[基础步速、重甲策略、减速量和力量门槛](docs/rules/schema-2-combat-authoring.md#步行来源)；
- 矮人可选山丘矮人亚种，标准[强韧](docs/rules/schema-2-character-statistics.md#山丘矮人强韧)在一级和以后每个角色总等级各增加 1 点最大 HP。
- DM 可用[被动检定](docs/rules/schema-2-character-foundations.md#被动检定)判断反复搜寻等任务是否成功。标准包采用 10 加适用修正，优势加 5、劣势减 5，可预设成败后果并在保存重开后继续。
- 两名角色争抢同一件物品时，DM 可发起[对抗检定](docs/rules/schema-2-character-foundations.md#对抗检定)。双方分别掷骰，标准包领先 1 分即胜，检定变体需领先 2 分。
- 角色结伴过沼泽等共同任务可用[团队属性检定](docs/rules/schema-2-character-foundations.md#团队属性检定)。每人各掷一次检定，标准包至少一半成功即全队成功，检定变体要求三分之二。
- DM 可在智力（历史）检定中选择“辨认石工作品来历”。矮人的[石工知识](docs/rules/schema-2-character-foundations.md#石工知识)在标准包下提供双倍熟练加值。
- 单职业战士 1 级正常取得[回气](docs/rules/schema-2-second-wind.md)，在本人战斗回合用附赠动作恢复 `1d10＋战士等级` HP，次数随标准短休或长休恢复；
- 五级战士可在同一回合抓住对手、用第二击推倒，再拖带移动。目标可选择防守技能并在自己的回合挣脱。[近战控制玩法](docs/rules/schema-2-melee-control.md)
- 单职业战士正常升到 5 级取得[额外攻击](docs/rules/schema-2-extra-attack.md)，自己回合的每个 Attack 动作可攻击两次，6 级保持；首击后的余数随完整文件保存恢复；
- 单职业战士 3 级选择勇士后取得[精通重击](docs/rules/schema-2-improved-critical.md)，3–6 级的普通近战、短弓和近战借机或 Ready 攻击选中 19 或 20 时重击。
- 由现有纯规则消费者执行已接入的成长/ASI/多职业、熟练、检定、装备、健康、休息与资源命令；
- 使用有限普通战斗、移动、机会攻击、反应/继续、Bless、Cure Wounds、Shield、Counterspell、Burning Hands、法师书/准备和奥术回想；
- 在工作台按规则怪物的步速逐格移动，玩家可对向本人揭示的怪物借机攻击或放弃。角色卡和主持端可核对普通动作、附赠动作、反应和剩余移动。[回合与移动](docs/rules/schema-2-combat.md)
- 开战时，DM 可指定谁尝试隐匿，并让同类怪物共用先攻骰。每名参战者分别判断是否受惊，首个本人回合结束后即可反应。[遭遇开场](docs/rules/schema-2-combat.md#encounter-opening)
- 规则角色可在探索中或本人战斗回合[主动卧倒、双倍成本爬行和起身](docs/rules/schema-2-prone.md)；探索卧倒不耗时，移动与起身各推进六秒，战斗起身花半速；复用倒地攻防、OA 与 Ready 移动，姿态和未决路径随完整文件恢复；
- 建立世界节点、路线及有界网格场景，显式入退场、旅行与切换唯一执行焦点；后台时钟、角色、效果与未决动作冻结；
- 在[战役工作台](./docs/storage-vitality-loop.md#战役工作台)左栏管理规则库与玩家，中栏主持当前场景，右栏翻阅角色卡。
- 角色可双手持短弓，逐支用箭。骷髅可在自己的回合射击。射程、近身敌人和护盾术都实际参与命中判定。[短弓与远程攻击](docs/rules/schema-2-ranged-attacks.md)
- 狼与地狱犬都可在本人回合或借机时啮咬。狼命中后目标做 DC 11 力量豁免，失败倒地。主持人可在借机或护盾术窗口保存重开，在战役记录查看原骰和姿态。[怪物近战与借机攻击](docs/rules/schema-2-monster-attacks.md)
- 保存有限怪物吐息/充能、持续效果、DM 抗魔区域、唯一安置、简单物品、角色状态与消息根历史；
- 管理独立的 DM-managed 自由卡、基础字段、职业等级和熟练，为 MANUAL/SAVING_THROW 检定设置有序后果，
  在同一运行内复用检定草稿，并在共享网格上使用 DM 裁定战斗和阵营。
- 在一个 SQLite 事务提交完整领域后果、历史、版本与幂等结果；保存初始及当前完整 JSON，下载/上传导入、同版重开，显式恢复或精确放弃残留。

标准规则使用 WAR 随附身份清单中的四个准确原包。若某个原包损坏，仅依赖它的内置组合不可用。
自定义组合可选用本机规则库里已导入、接受的内置或外部原包。组合最多八包，
经过严格生产 reader、依赖/覆盖组合及当前机制与创建模板的完整支持校验。随附家规让运动默认使用感知，DM 也可为长途游泳选用体质（运动）。该组合的固定直接伤害为 3，工匠初始语言为侏儒语。[标准包指南](./rule-packages/srd51-runtime/package-guide.md) · [家规说明](./rule-packages/srd51-runtime/dm-runtime-check-variant/package-guide.md)
创建、候选、结算、来源展示和恢复使用同一战役
冻结上下文；不能在活动战役中切换。它们不是完整 SRD `RELEASED` 发布，
不借用旧完整规则族的 `DRAFT` 身份。Fighter 支持到职业 6 级、Life-domain cleric 到 1 级、
Wizard 到 5 级；未接入的职业特性保留目录元数据，不自动授予。
准确领域覆盖、现行能力差异及剩余采用门见[领域接合表](./docs/design/v2/domain-integration.md)。

战役默认存放在运行账号的 `${user.home}/.dndtool/campaigns`。通过 `DND_STORAGE_ROOT` 可指定其他私有目录。

本机规则库负责准确原包和接受账本，作者工具单独校验 DRAFT 目录与规则制品。
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
记录[四层职责与执行](./docs/design/v2/module-layering.md)、[模组接口与组合](./docs/design/v2/module-interface-baseline.md)、
[本机规则库](./docs/design/v2/local-rule-library.md)、[私有运行存储](./docs/design/v2/rule-storage-and-versioning.md)、
[完整战役文件](./docs/design/v2/save-and-recovery.md)及[生命周期](./docs/design/v2/runtime-and-transactions.md)。
当前 SQLite 运行按[世界与场景](./docs/design/v2/world-and-scene-spaces.md)维护每个空间的
逻辑时钟及唯一安置。DM 裁定本次旅行耗时，角色恢复资格按累计实际经历时间计算。
完整文件 `RULE_BOUND_CAMPAIGN` format 3 保存各场景时钟、焦点及全部待继续事项。
新建和文件打开均核对所绑定的完整规则，自定义组合还要核验本机接受记录。活动运行、保存及本机残留恢复使用冻结材料。
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
PC 控制分配、明确共享、有限服务端候选动作、DM 裁定和操作收据查证。玩家可在共享场景中
按本人角色的视角查看规则怪物的当前或最后已知格位，用近战武器或短弓攻击，并看到命中、实际伤害和击倒结果。DM 可布置掩护，地精可用附赠动作躲藏或撤离。[躲藏、观察与掩护](./docs/rules/schema-2-observation.md)

Host 接口与完整战役文件
仅对 DM 开放。监听需要显式配置，应用不会自行创建 Connector。
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
| Web API/UI | Jakarta Servlet 6.0、HTML、原生 JavaScript |
| 数据库 | 进程私有 SQLite，随 WAR 交付 JDBC 驱动 |
| JSON | Gson 2.14 |
| 构建 | Maven 3.9 |
| 测试 | JUnit 5、真实临时 SQLite、独立规则向量与成品场景 |

## 架构概览

```text
本机浏览器
  └─ http://127.0.0.1:8080/host/storage
       └─ Servlet / HTML / JavaScript
            └─ Service：验证、事务、幂等与规则边界
                 └─ JDBC Repository
                      └─ 私有 SQLite 战役工作库
```

应用按职责分为 `web`、`security`、`service`、`persistence` 和 `module` 包。Servlet
不直接执行 SQL，Repository 不决定业务权限，浏览器展示服务端状态；跨表业务操作由 Service
拥有事务边界。

## 环境要求

- JDK 21；
- 从源码构建时需要 Maven 3.9 或兼容版本；
- Apache Tomcat 10.1；
- SQLite 存储使用原生 Windows 本地 NTFS/私有 ACL，或 Linux/WSL 的 POSIX 私有文件系统；文件锁、硬链接及平台落盘边界见[配置](./docs/configuration.md#sqlite-当前应用存储配置)；
- 支持现代 JavaScript 的本机浏览器。

可直接使用发行版 `dnd-tool-se-0.2.1.war`。安装和首次启动见[快速开始](./docs/getting-started.md)。

## 构建与测试

从源码生成应用 WAR 可在仓库根目录运行 `mvn -DskipTests package`，产物为
`target/dnd-tool-se-0.10.0.war`。这条命令只打包。改动应用机制、API 或存储时，按变更选择
JUnit 用例，例如启动逻辑用 `mvn '-Dtest=StorageApplicationListenerTest' test`。

可运行规则模组用已有应用 WAR 运行作者场景。以下命令核对该 WAR 随附标准组合的巨剑攻击、伤害及目标剩余 HP。

```sh
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.10.0.war \
  --case weapon-damage rule-packages/srd51-runtime/scenarios/standard.json
```

开发中的模组可单独构建候选 ZIP 并传给同一入口，见[模组场景指南](./rule-packages/srd51-runtime/scenarios/README.md)。SQLite 测试、无 MySQL 环境验收和 WAR 审计见[测试指南](./docs/testing.md)。

离线 DRAFT 语言与工具目录使用 `python3 -B tools/rule-packages/catalog-vector.py --check rule-packages/srd51-complete` 核对正式作者内容。应用读取器与安装测试另用固定夹具。见[独立作者验证](./docs/rules/language-author-package.md#独立验证)。

## 数据库与部署

在外部 Tomcat 中将发行 WAR 部署为 `ROOT.war`。WAR 自带 SQLite JDBC，启动无需配置 JNDI 或 MySQL。
私有存储根、可回滚替换与验收见[部署指南](./docs/deployment.md)。

当前应用、默认构建与测试只需要 SQLite。本机规则库保管已接受原包，历史 MySQL 库、账号、迁移账本和工具按匹配版本单独保管。[数据库合同](./docs/database.md#迁移合同) · [候选验收](./docs/testing.md#mysql-free-acceptance)

## 安全与数据约束

- Host 入口只接受精确的 `http://127.0.0.1:8080` origin，不信任 `X-Forwarded-*`；
- 可选玩家入口要求准确配置的 HTTPS 地址、端口和 origin，只开放玩家页面、资产及有限 API；
- 主机写请求通过 `X-CSRF-Token` 请求头提交会话令牌，并校验 Origin/Referer、Fetch Metadata、状态 epoch、对象版本和幂等键。
- 客户端不能指定骰点、选中骰、派生修正、最终结果、效果算法或规范业务身份；
- 未知、重复、未发布、摘要不一致或不受支持的冻结规则必须失败关闭；
- 权威状态、事件、骰子、效果、字段变化、版本和幂等结果在同一事务提交或回滚；
- SQLite 完整 JSON 保留当前有限模型的状态、历史与规则材料，排除玩家凭据、私钥和本机控制材料；
- 数据库账号按迁移、运行和只读验收职责分离。

## 项目结构

```text
src/main/java/com/dndtool/
  module/          不可变规则模型、内置目录、规范编码与哈希校验
  persistence/     SQLite 结构、规则账本、战役事实仓储与 Host 状态代次接口
  security/        loopback 主机边界和请求安全过滤器
  service/         业务验证、诊断编排、事务、幂等和存档服务
  web/             SQLite 启动与就绪检查、Servlet、HTTP DTO 与页面支持
src/main/resources/storage/
                    战役结构声明与内置规则原包
src/main/webapp/    WAR 的 Host / Player HTML、web.xml、JavaScript 和 CSS
src/test/           单元、边界、结构和集成测试
docs/               架构、规则、配置、安全、测试和运维指南
config/tomcat/      不含秘密的外部 Tomcat 配置示例
tools/              规则制品与摘要校验、SQLite 隔离启动和验收工具
```

## 文档索引

The [English SRD 5.1 semantic specification](./docs/semantics/srd-5.1/README.md)
defines source behavior independently of runtime support. Its coverage ledger
tracks semantic drafting and source review separately from implementation.

| 文档 | 内容 |
|---|---|
| [快速开始](./docs/getting-started.md) | 环境前提、构建、配置和最小启动验收 |
| [产品说明](./docs/product.md) | 当前行为、v2 使用流程与暂缓范围 |
| [SQLite 当前有限战役](./docs/storage-vitality-loop.md) | 规则选择、领域运行、严格完整文件与显式恢复 |
| [领域接合表](./docs/design/v2/domain-integration.md) | 当前实际覆盖、旧能力差异和剩余接合门 |
| [v2 设计基线](./docs/design/v2/README.md) | 已确定的目标设计、实现差异和采用前提 |
| [模组分层与执行架构](./docs/design/v2/module-layering.md) | 核心/机制/规则内容/UI、接口、事实 owner 与分步端到端验收 |
| [规则行为接口验证](./docs/design/v2/rule-behavior-interface-validation.md) | 16 个 SRD 样例、原接口判定、修订合同的纸面复验与当前程序支持 |
| [本机规则库](./docs/design/v2/local-rule-library.md) | 准确原包、独立接受、组合准备及移机打开 |
| [世界与场景焦点](./docs/design/v2/world-and-scene-spaces.md) | 当前节点与二维网格、单焦点和全状态恢复，及跨空间被动因果目标 |
| [时间与日历](./docs/design/v2/time-and-calendar.md) | 目标空间钟、自然日和黎明、个人经历与期限迁移 |
| [空间与区域](./docs/design/v2/spatial-and-area-contract.md) | 目标三维体积、范围证明、联动移动和生成/返回安置 |
| [物件与魔法物品](./docs/design/v2/objects-and-magic-items.md) | 目标物件身份、同调、魔杖充能、符文条件与直接恢复 |
| [局域网玩家参与](./docs/design/v2/player-participation.md) | 玩家参与设计与原型采用边界 |
| [JSON 结构声明与战役建库](./docs/design/v2/storage-schema-and-planning.md) | 有限声明格式、依赖闭包、确定性计划、示例、初始化与结构恢复 |
| [存储数据覆盖与替换路线](./docs/design/v2/storage-data-map-and-rollout.md) | 当前表组/领域/所有者映射、公共接口、配置切换、实施步骤与验收 |
| [Agent 项目上下文](./docs/agent-context.md) | 新会话使用的精简项目地图和权威资料索引 |
| [架构](./docs/architecture.md) | 产品范围、组件、事务与信任边界 |
| [规则数据库分离设计](./docs/rule-database-separation.md) | 当前存储、双 schema 职责及 v2 采用边界 |
| [schema 2 制品与安装接受](./docs/rules/schema-2-installation-and-acceptance.md) | 原始 ZIP、生产摘要、审阅绑定、独立接受和提交查证 |
| [schema 2 来源到运行快照](./docs/rules/schema-2-runtime-snapshot.md) | 历史来源与运行快照保管、当前冻结组合与直接恢复入口 |
| [schema 2 初始语言／工具授予](./docs/rules/schema-2-proficiency-grants.md) | 真实来源与稳定槽、原子授予字段、跨模组选择和固定快照计划 |
| [schema 2 检定、装备与健康作者来源](./docs/rules/schema-2-foundation-authoring.md) | 四类类型化定义、字段和引用、负修正、清空、纯组合与真实来源边界 |
| [schema 2 角色事实存储](./docs/rules/schema-2-character-fact-storage.md) | 准确镜像、检定/装备/健康输入与结果、前后状态、历史预算及仓储直接恢复 |
| [schema 2 检定、装备与健康基础](./docs/rules/schema-2-character-foundations.md) | 真实检定、装备实例、健康变化、命令幂等、完整局部历史与直接恢复 |
| [schema 2 战斗作者来源](./docs/rules/schema-2-combat-authoring.md) | 战斗算法、近战与远程武器及步行的类型化来源 |
| [schema 2 内部战斗与动作继续](./docs/rules/schema-2-combat.md) | 真实攻击/移动/机会攻击、回合、倒地/掉落、共享秒时钟、完整历史与恢复 |
| [schema 2 施法作者来源](./docs/rules/schema-2-spell-authoring.md) | 生命领域与法师来源、学习名单、固定抗魔环境 |
| [schema 2 有限施法与持续效果](./docs/rules/schema-2-spells.md) | 真实成本/法术书、Shield/Counterspell/Ready、独立压制、专注/到期及探索转移 |
| [schema 2 有限怪物与火焰吐息](./docs/rules/schema-2-monster-breath.md) | 类型化怪物/动作、完整点格目标、共享伤害/豁免、充能、专注共同切面与局部恢复 |
| [共同业务写入与初始化准备](./docs/rules/business-write-transactions.md) | SQLite 共同事务与历史运行守卫的保管 |
| [SRD 5.1 本地原文索引](./docs/references/srd-5.1/README.md) | 按主题定位 403 页检索文本，核对本地 PDF 与官方出处 |
| [完整规则目标](./docs/rules/srd-5.1-complete.md) | SRD 5.1 功能覆盖、自动化边界和兼容要求 |
| [冻结 v1 规则规范](./docs/rules/srd-5.1.md) | 现有稳定键、算法、范围、规范化和模组关系 |
| [职业特性 v2](./docs/rules/class-features-v2.md) | 12 职业/子职业特性矩阵、资源恢复与阻断边界 |
| [多职业共享法术位 v2](./docs/rules/multiclass-spell-slots-v2.md) | DRAFT 施法贡献与共享位采用范围 |
| [Archive format 2 角色投影](./docs/rules/archive-format-2-character-state.md) | 历史 DRAFT 片段的保管和当前格式接受边界 |
| [配置](./docs/configuration.md) | SQLite 存储根、Tomcat 运行身份与可选玩家 HTTPS Connector |
| [数据库](./docs/database.md) | SQLite 工作库与原 MySQL 数据保管 |
| [安全](./docs/security.md) | 请求边界、权威输入、最小权限与秘密处理 |
| [测试](./docs/testing.md) | 应用定向测试、独立模组场景、集成测试和 WAR 审计 |
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
- 按上方验证入口核对模组内容，应用改动验证受影响的机制及调用方；
- 生产代码或资源变化时审计生成的 WAR；
- 不提交构建产物、数据库秘密、Tomcat 外部配置、日志、备份或真实存档。
