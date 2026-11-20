# 数据库

生产应用使用 SQLite。MySQL 运行链与来源链用于独立 JDBC 验证、规则作者来源维护及
已有数据库的保管。生产部署见[部署指南](deployment.md)。

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

## 迁移合同

原 MySQL 运行链由 JDBC 核验工具及隔离测试使用，源文件位于 `src/main/resources/db/migration/`。V001—V018 是现有清单与数据库账本核对的历史；V011—V018
依次建立角色目录、一级创建、升级与生命骰、职业特性生命周期、多职业/ASI/专长、format 2
角色状态来源及多职业共享法术位计算目录基础的 DRAFT 前向状态：

- 保留现有数据库时，不修改、合并、重命名或重跑其中已应用的迁移，schema 变更使用下一个编号的前向迁移；
- 原型版本也可选择新的初始化结构并重建数据库，不要求未来设计继承现有表形状或完整迁移链。采用时同步初始化入口、应用清单、验证器、测试和文档；真实数据库清空、重建与迁移须明确授权，设计修改本身不执行这些操作；
- 迁移文件名、规范 SQL 载荷摘要与 `schema_meta` 必须一致；
- 仓库中的辅助 SQL 不是已执行证明。

迁移历史保护用于避免把现有数据库误认为另一套结构，不构成跨应用版本存档兼容承诺。
独立 MySQL 核验仍按下述清单检查结构和摘要。

只读验证脚本位于 [database/verify](../database/verify/)，最小授权脚本位于 [database/grants](../database/grants/)。移动和阅读这些文件不会执行 SQL。

V019 建立永久运行身份登记、不可变快照头和类型化语言镜像；V020 追加空工具镜像表。它们不复制旧
规则目录或创建战役，当前 Repository 只接受 DRAFT PARTITION。身份、调用方事务、授权
与验收合同见[运行语言快照分区](rules/runtime-language-snapshot.md)及[工具熟练目录](rules/tool-catalog-partition.md)。应用迁移清单预期
V001—V042；独立 schema 诊断按该清单校验。
V021 追加 schema 2 INTERNAL 类型化组合、最终规则、完整覆盖链、永久守卫与操作收据；
来源读取和原子转存合同见[来源到运行快照](rules/schema-2-runtime-snapshot.md)，增量授权见
[schema 2 运行权限](../database/grants/schema2-runtime-snapshot.sql)。迁移和授权文件不表示已执行。
V022 增加[角色语言／工具授予事实](rules/character-proficiency-grants.md)，V023 增加 schema 2 工具、工具集合和成员镜像。
V024 追加 schema 2 角色授予来源、稳定槽和类型化授予覆盖值；[初始授予计划](rules/schema-2-proficiency-grants.md)
从实际运行快照读取这些字段，仍不创建战役、角色工作集或业务历史。
V025 建立所有业务写入共用的不可替换单行守卫；V026 追加绑定 operation、run、snapshot、
campaign 和 character 的初始准备头、槽、选择及授予事实。准备事实与规则快照、控制和收据
同事务保存，但不授予游戏执行资格。见[共同业务事务](rules/business-write-transactions.md)、
[守卫权限](../database/grants/business-write-guard.sql)和[初始计划权限](../database/grants/schema2-initial-plans.sql)。
V027 增加成长、选择、熟练和休息规则镜像，以及追加式角色根、完整命令和各类状态历史；
它们属于[内部成长消费者](rules/schema-2-character-statistics.md)及[休息消费者](rules/schema-2-character-rest.md)。
V028/V030 追加四类检定/装备/健康规则镜像及完整字段链；V029/V031 保存检定输入与结果、装备
前后状态/命令、健康状态/结果和有序原骰。格式、预算及叶仓储追加/恢复见
[角色事实存储](rules/schema-2-character-fact-storage.md)；实际命令、规则语义与恢复验证见
[角色基础合同](rules/schema-2-character-foundations.md)。
V032 保存[战斗/近战武器/步行来源](rules/schema-2-combat-authoring.md)的准确类型化规则及完整链；
V033 保存[内部战斗](rules/schema-2-combat.md)的聚合、命令、前后状态、动作流、反应窗口和原骰，
以及指向真实角色的战斗子事件。V034 前向扩展唯一角色时钟的秒、长休/稳定恢复准确秒、
地面装备位置及倒地/半尺移动成本；不改写已批准迁移。战斗参与者继续各占独立 run/snapshot，
同战役、准确规则绑定和起点秒必须一致。权限和只读核验分别见
[战斗权限](../database/grants/schema2-combat-runtime.sql)、[战斗核验](../database/verify/schema2-combat-runtime.sql)
及[规则镜像核验](../database/verify/v032-schema2-combat-rules.sql)。SQL 源不证明已执行。
V035 保存[有限施法作者](rules/schema-2-spell-authoring.md)的两个法术、生命领域一级配置与
完整字段链，并以前向约束接入真实圣徽。V036 保存[施法与持续效果](rules/schema-2-spells.md)
的类型化命令／目标、施法结果、效果、贡献骰与豁免，并扩展原角色战斗因果子事件以记录
资源消费和治疗；HP、资源及秒时钟仍由原角色拥有。V036 同时前向修正战斗帧状态 CHECK，
既有迁移不改写。增量见[施法权限](../database/grants/schema2-spell-runtime.sql)、
[规则镜像核验](../database/verify/v035-schema2-spell-rules.sql)与[事实核验](../database/verify/schema2-spell-runtime.sql)；
V037 保存 Wizard profile、可学习名单和固定环境定义；V038 保存法术书事实、多层反应、Ready、Shield、
专注 holder、独立区域压制原因和战外场景状态，并扩展因果健康与时间输入。
V038 在任何持久 DDL 前要求 `s2_combat_runtime` 为空，包括已经 ENDED 的旧事实；旧 INTERNAL
动作图和请求格式不自动转换。存在真实旧 DRAFT 战斗历史时须另行审查迁移／恢复方案，不删除历史绕过此门。
V039 保存[有限怪物](rules/schema-2-monster-breath.md)的定义、动作条目、完整 stat block／
action profile 及字段链复合值的类型化子行。V040 保存真实怪物部署／健康／使用状态和
多目标批次的目标证明、共享原骰、checks、packets、专注义务，并接合场景命令／历史与
唯一 flow；角色 HP、装备、资源、时钟和 holder 继续由原 owner 保存。
旧 INTERNAL 场景格式和检查约束按该前向迁移明确预检，不猜测为已有历史补字段；真实
旧 DRAFT 数据必须先按具体迁移／恢复方案处理，不能删除数据绕过空库门。
对应[怪物／吐息只读核验](../database/verify/schema2-monster-breath.sql)检查完整类型化图的
计数、引用和算术异常；与场景核验组合运行，不改写数据或自动修复历史。

V041 为[奥术回想](rules/schema-2-arcane-recovery.md)追加有界恢复选择、事件计数与法术书每日事实；
V042 为[法术来源共享伤害](rules/schema-2-spell-save-damage.md)追加方向与真实 cast 来源明细，并前向扩展
既有法术/批次约束。新增子表继续使用准确父事件外键、不可变事实及调用方事务。
对应授权和只读核验分别见 `schema2-arcane-recovery.sql`、`schema2-spell-save-damage.sql`
在 `database/grants/` 与 `database/verify/` 下的同名文件，不由应用启动执行。

这些源文件不证明迁移、授权或真实数据库验收已经完成。
同 run 的唯一角色约束维持当前内部时钟所有权，不开放完整战役初始化或多角色生命周期。
[有限运行权限](../database/grants/schema2-character-runtime.sql)仅给应用 SELECT/INSERT，
[只读核验](../database/verify/schema2-character-runtime.sql)另行执行；没有隐含迁移或授权。
V022 不推测旧 DRAFT 的授予来源；若已经有一级创建快照，预检拒绝迁移，须另行决定真实数据的处理方式。
新增结构可用[只读核验 SQL](../database/verify/character-proficiency-grants.sql)盘点；
[授予权限](../database/grants/character-proficiency-grants.sql)与规则快照权限分别审核和应用。

独立 `RULES` 链位于 `database/rules/migration/`，目标为 `dnd_tool_rules.rule_schema_meta`，
本链包括 V001—V012，SQL 不进入 WAR；其固定批准元数据与只读 verifier 独立于运行链。
V005 保存成长、资源、休息和类型化条目/前提/修改材料，V006/V007 保存
[检定、装备与健康作者来源](rules/schema-2-foundation-authoring.md)，V008 保存三类战斗作者定义和原子修改，
V009/V010 保存法术、施法配置、Wizard 学习名单、固定环境与修改的类型化作者材料；
V011 增加怪物／动作、复合 stat block／action profile 的十一张类型化材料表，并前向
接入真实 `SAVING_THROW` 熟练类别及合法属性引用。既有历史保持不可变。
当前来源 schema 合计 53 张表、159 个触发器。schema 1 和 schema 2 共用原安装锁，各自核对所属历史与计数。
schema 2 原始制品、独立接受和控制账本绑定 43 张类型化作者材料表。成长定义、稳定等级条目、
起始/多职业来源槽、子职业、特性、专长、有界熟练集合与休息声明保留原始作者序号和完整引用。
能力前提按组/备选序号保存；存在标记区分省略和空前提。有限 profile、最高支持等级、采用范围与
执行模式均与全部原 ZIP 逐列核对，包含未选中包和全部子原子。
installer 仅获准确材料列 INSERT，accepter/app/agent 对作者材料只读；现有事实不能事后补写。
来源存储不放宽完整基础包与非空 add-on 的安装资格门，也不授予完整职业升级或发布资格。
结构、源账号权限及隔离验收边界见[离线规则源 schema](rule-source-schema.md)和
[schema 2 来源账本](rules/schema-2-source-ledger.md)。新增 SQL 与权限文件不表示已经在数据库执行。
运行 V001—V042 与来源 V001—V012 共 54 份迁移源。作者来源、运行镜像与真实角色命令/历史
分别按各自合同核验；该 INTERNAL 接入不开放完整战役，不建立第二个 JNDI，
也不执行外部迁移或授权。

## MySQL 工具与账号边界

| Schema | 身份 | 职责 |
| --- | --- | --- |
| 已有 `dnd_tool_se` | `dnd_tool_se_agent` | 只读核验、检查自身权限 |
| 已有 `dnd_tool_se` | `dnd_tool_se_migrator` | 由独立授权批准的前向迁移和所需备份读取 |
| 已有 `dnd_tool_se` | `dnd_tool_se_app` | 保留的最小运行权限合同，生产 SQLite WAR 使用外部文件权限 |
| `dnd_tool_se_it` | `dnd_tool_se_it` | JDBC 事务集成测试使用连接内临时表 |
| 隔离、可销毁实例内的 `dnd_tool_se` | 专用迁移与只读验收身份 | 原样重放 V001—V042，再只读核验 |
| `dnd_tool_rules` | 按来源 schema 定义的独立身份 | 安装、接受、维护与核验各自的授权 |

保留已有库时先盘点真实数据及迁移账本。迁移重放使用[隔离测试流程](testing.md#3-mysql-集成测试)，
保留原始版本名、SQL 和 schema 名称。已保留的 `dnd_tool_se` 不作为可写集成测试
或重复重放目标。完整触发器定义须由隔离重放中的有权限身份只读检查。
[语言分区 JDBC 验收](rules/language-partition-equivalence.md#隔离-jdbc-入口)
按其独立流程，在同一隔离实例内运行两条 schema 链。

账号管理员只负责获授权的账号和授权维护，工具按职责使用最小权限账号。旧库常规只读
核验使用 loopback 账号。现有数据库与源库的备份、恢复、正式迁移、授权及销毁是一组独立
外部操作，各自核对目标、作用边界和授权。保护已有数据库和存档，生产 WAR 切换只更换
Web 应用及配套配置。

## 稳定合同

`dnd5e2014_srd51_se_v1`、发布版本、规范哈希算法、字段键、算法键、事件类型、摘要域和存档稳定键属于持久化或协议合同。源码类名、文档路径和辅助脚本名的整理不得改变这些值。完整 SRD 5.1 目标不能通过扩写、重算或重新解释该发布版实现。

冻结规则详见 [rules/srd-5.1.md](rules/srd-5.1.md)。

原完整规则族 DRAFT 和 archive format 2 的身份与采用门见
[规则覆盖目标](rules/srd-5.1-complete.md)及[类型化目录合同](rules/character-catalog-v2.md)。
