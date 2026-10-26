# Schema 2 制品安装与独立验收账本

本合同定义 schema 2 制品的来源存储边界：保存经过封闭安装资格检查的准确原始 ZIP、
生产逻辑身份和不可变操作事实，并由另一个显式操作验收准确已安装材料。
入口与票据见[安装与验收流程](schema-2-installation-and-acceptance.md)，生产 ZIP 和规范编码见
[模组接口基线](../design/v2/module-interface-baseline.md)。纯验证向量不能取得安装或验收资格。

这里保存可重新解析的完整制品，以及与原 ZIP 精确一致的类型化初始熟练、职业成长、资源、休息及
[检定、装备与健康作者材料](schema-2-foundation-authoring.md)，
不把通用规则袋当作业务来源模型。语言、工具等既有
schema 1 来源表不被 schema 2 安装改写。[来源到运行快照](schema-2-runtime-snapshot.md)从准确
原包重建有限类型材料及完整字段链；尚未采用的其他领域来源、完整运行目录、战役创建和 archive 2
开放仍属于后续采用边界。来源账本只确认原始事实与接受证据；成长、休息、检定、装备和健康由运行快照的显式消费者执行。
完整基础包未过全域完整性门时 `S2_BASE_INCOMPLETE`；没有现行业务算法支持的机制不能靠安装或人工验收取得运行资格。

## 表与身份

`database/rules/migration/V003__schema2-source-ledger.sql` 建立独立 RULES 账本，
既有历史保持不可变；V005 保存成长、资源和休息材料，V006/V007 保存检定、装备与健康材料。
28 个 schema 2 表均为 InnoDB，其中三个账本/控制表与 25 张类型化作者材料表分别承担事实职责。
技术身份、版本、枚举和摘要使用 VARBINARY，
因此大小写及尾字符按字节比较。没有跨 schema FK 或运行表写入。

| 表 | 当前合同 |
|---|---|
| `rule_schema2_artifact` | PK 为准确 module/release；module 为 1—64 ASCII `[a-z][a-z0-9_]*`，version 为 1—64 ASCII 且首字符字母或数字；唯一正安装序号和 UUIDv4；保存原 ZIP BLOB、准确压缩/展开长度、raw ZIP SHA-256、生产 logical SHA-256、角色、schema/protocol/profile、原安装操作指纹和数据库生成 UTC 时间 |
| `rule_schema2_acceptance` | 每个准确 module/release 最多一条；独立 UUIDv4、接受序号、操作指纹、审查材料 raw SHA-256 和数据库 UTC 时间；完整绑定原安装操作 ID/指纹/序号、schema/protocol/profile、角色、raw ZIP 与 logical SHA；源内 RESTRICT FK 指向准确安装身份 |
| `rule_schema2_control` | 唯一永久行 `(1,1,1,0,0,0,0)`；依次为 control ID、protocol、元数据行数、安装数、验收数、ZIP 字节总和、行版本 |
| `rule_schema2_character_source` | 原始 race/subrace/background/class 声明：原包、版本、definitions 作者序号、稳定规则身份、完整元数据、parent race 引用及可空作者备注 |
| `rule_schema2_proficiency_grant` | 原始稳定授予槽声明：原包、版本、entries 作者序号、来源规则和槽身份、完整 LANGUAGE/TOOL × FIXED/CHOICE 原子、可空作者备注 |
| `rule_schema2_character_change` | 原始来源/槽操作：原包、版本、changes 作者序号、准确目标、操作/字段与单个类型化值；保存清空、启停、文本、页码、parent race 和完整 grant 原子 |
| `rule_schema2_class_progression` | 原始 `character.class_progression` 声明：原包、版本、definitions 作者序号、完整规则身份、元数据、完整 owner class 引用、生命骰面数、熟练加值 profile、最高支持等级、准确 `STATISTICS_AND_RESOURCES_ONLY`/`CHARACTER_ADVANCEMENT` 范围、多职业前提存在标记及可空作者备注 |
| `rule_schema2_resource` | 原始 `character.resource` 声明：同一作者身份与元数据、完整 owner class 引用、资源上限及恢复 profile、准确 `AUTOMATIC`/`BLOCKED` 执行模式及可空作者备注 |
| `rule_schema2_advancement_change` | 原始成长、资源和休息操作：原包、版本、changes 作者序号、完整规则或条目目标、操作/字段；文本、整数、布尔、完整规则引用、growth grant 原子与前提存在标记分别保存，支持 author note 清空和启停 |
| `rule_schema2_subclass` | 原始子职业元数据、完整 owner class 引用和选择等级 |
| `rule_schema2_feature` | 原始特性/选项元数据、完整 class/subclass owner、等级、种类、执行模式和白名单算法 |
| `rule_schema2_feat` | 原始专长元数据、前提子原子与明确 BLOCKED 执行模式/算法；声明不授予激活能力 |
| `rule_schema2_proficiency` | 原始 SKILL/ARMOR/WEAPON 目录；技能保存冻结能力键，其他分类该列为空 |
| `rule_schema2_proficiency_set` | 原始有界熟练候选集合、分类和最低启用成员数 |
| `rule_schema2_class_level` | 稳定 `levels` 条目完整身份、等级和 ASI 可用事实，不根据整数等级补造缺失条目 |
| `rule_schema2_proficiency_member` | 稳定成员条目及完整 proficiency/tool 引用 |
| `rule_schema2_growth_grant` | 稳定 starting/multiclass 来源槽；保存 FIXED/CHOICE、完整 target/candidates 引用和有界选择数 |
| `rule_schema2_ability_requirement` | definitions/changes 作者身份下的组序号、备选序号、冻结能力键和最低值；保留 AND 组与 OR 备选的准确原始结构 |
| `rule_schema2_rest` | 原始 `adventure.rest` 声明、页码 87 和准确 `SRD_2014_REST_V1` 算法 |
| `rule_schema2_check` | 原始 `adventure.check` 定义、页码 77、准确 `SRD_2014_ABILITY_CHECK_V1` 算法；操作复用 `rule_schema2_advancement_change` 的类型化列 |
| `rule_schema2_equipment_item` | 原始 `equipment.item` 定义、种类、AC、力量要求、隐匿劣势、手数、完整熟练和可空隐匿技能引用、白名单算法 |
| `rule_schema2_health` | 原始 `adventure.health` 定义、页码 98 和准确 `SRD_2014_HEALTH_V1` 算法 |
| `rule_schema2_vital_effect` | 原始 `adventure.vital_effect` 定义、种类、骰数/骰面、有符号修正、适用伤害类型和准确 `DIRECT_VITAL_EFFECT_V1` 算法 |
| `rule_schema2_execution_change` | 装备、健康与直接效果的原始字段/启停/清空操作；文本、有符号整数、布尔与完整引用列互斥，保留目标和作者序号 |

类型化作者材料按准确原包和作者序号标识；前提子原子另加 `author_kind/group_index/alternative_index`。作者序号保留
同类文件按 ASCII 路径排序、各文件内按原始数组位置累积的零基位置；包括其他领域，
不能采用规范身份排序后的序号，也不能按过滤后的新领域重新编号。清单的文件声明顺序
不改变该序号，准确文件路径和数组位置仍由原 ZIP 保留。作者协议每包最多 64 个定义、128 个条目和
256 个操作；外键仅绑定同一原包身份。owner class 使用完整 module/type/key，
同名资源不能跨模组合并。外部来源、目标与集合引用由组合器校验直接依赖和加载位置，
不强制被依赖包先写入来源物理表。

职业进阶必须明确支持等级和采用范围；`CHARACTER_ADVANCEMENT` 必须提供多职业前提，稳定等级、
起始与多职业来源槽、子职业和特性均通过完整身份接合。统计/资源范围可省略多职业前提；存在标记
区分省略和明确空 `all_of`。前提最多六组、每组一至两个备选，六个冻结能力键全局不重复，
最低值为 1—20；子表按两级原始数组位置保存，每包最多 1920 个前提子原子。
资源、特性与专长保留准确执行模式和白名单算法，`BLOCKED` 不能靠存储或人工验收激活。
profile 文本保存作者的准确有限协议值，恢复 profile 上限为 200 字符；作者校验和读回重解析
继续拒绝未知、畸形或不受支持值。所有新域以类型化列和子行保存，不保存 JSON 或最终组合值。
SQL CHECK 提供列形状、引用类型和基础范围约束，不能替代组合器的引用闭包与 profile 语义校验。

前向材料迁移只增加新表与约束，不要求既有 artifact/acceptance 为空，也不猜测回填 ZIP 内容。
不含相应领域的旧包投影为空，仍可读；含相应声明或操作却缺少等价物理行时，
完整 reader 拒绝该账本。此规则不改变旧表、不允许补写不可变事实或授权迁移执行。

持久协议准确为 author schema 2、artifact protocol 1、operation fingerprint version 1、
canonical profile `schema2-production-json-1`。logical SHA 取自
`ProductionRuleArtifact.logicalSha256()` 的生产 `module-logic` 域；不能保存嵌套
`AuthorPackage.logicalSha256()` 的验证域摘要来代替它。原 ZIP 包括受支持的说明/许可材料、
文件排列和压缩形式，不能由展开文件重新打包代替原字节。

单 ZIP 上限 2 MiB；本源最多 16 个不可变版本，总 ZIP 与总展开字节各自上限 8 MiB，每版至多一条验收。
这是一组当前有界工程预算，不表示完成真实引擎容量验收。展开字节和 ZIP 结构另受生产
reader 的有限协议约束，不能用压缩体积替代展开预算。

## 共享屏障、计数和事务

schema 1 安装、schema 2 安装和独立验收首先按 PK 锁原永久
`rule_installation_control` 控制行 S。schema 2 随后锁 `rule_schema2_control`；
制品/验收 INSERT 和 control UPDATE 触发器也取得同一个 S，不使用可伪造 session 标志。
类型化材料 INSERT 触发器锁 schema 2 控制行，并核对当前安装序号对应的原包。
原 S 的 `metadata_row_count/row_version` 保留 schema 1 元数据域语义，不把新表计入旧计数。
schema 2 维护证据中的旧历史下界与锁住的 S 版本比较；这一步不声称重新审计旧内容闭包。

新域按 `I` 个安装、`A` 个接受事实独立计数：
`metadata_row_count=1+I+A`、`row_version=I+A`、`0<=A<=I<=16`，
ZIP 字节计数等于全部安装的实际原始 ZIP 长度之和。安装与验收序号各自从 1 连续增长。
新事实、完整原 ZIP、类型化作者材料和对应计数在调用方同一事务提交；重放与查证不增加计数。

`JdbcSchema2RuleSource` 只使用调用方传入的单连接，不借连接、不改变连接状态、
不 commit/rollback、不使用 savepoint。协调层承担 READ_COMMITTED、事务状态、
回滚和提交不明处理。类型化写入由 `JdbcSchema2CharacterSourceRepository.store` 在原包 INSERT
之后、控制行 UPDATE 之前使用同一调用方连接完成；该入口要求可写显式事务，任一失败交由
调用方回滚。触发器只允许当前安装序号对应包的材料插入，旧事实不能事后补写。
所有新 DML 必须恰好影响一行；仓储重新从 JDBC 读取后像并比较已有
安装、验收及计数。异常不被解释为成功，也不由仓储自动补写或重试。

读回先有界读取所有元数据，检查每个 `OCTET_LENGTH(original_zip)`、声明长度、总 ZIP 与总展开预算，
再通过有界 binary stream 读取 ZIP。完整重新解析、重算 raw/生产 logical SHA、核对
准确身份/角色/profile/资格、实际展开成员字节和，并检查独立连续序号、UUID 唯一、验收绑定和计数闭包。
共享 SELECT-only 校验由 `Schema2SourceLedger` 承担，离线仓储在其结果上继续检查安装资格；
`JdbcSourceSchema2Repository` 在独立只读事务中复用它并要求选定项具备准确接受事实。
账本完整性不授予机制或运行资格；运行材料由独立快照协调器建立，不由此 reader 写入。
运行 V028/V030 保存四类规则的准确镜像及完整链，V029/V031 提供
[类型化角色事实存储](schema-2-character-fact-storage.md)。来源读回、规则镜像和仓储直接恢复
各自验证所属事实；[角色基础消费者](schema-2-character-foundations.md)再验证实际命令与历史语义，
来源读回或仓储恢复不能代替完整战役资格。

共享 reader 同时有界读取全部 21 张作者材料表，与每个准确 ZIP 的原始类型化声明和操作逐列比较。
比较包含未选中包、可空字段、完整引用、稳定等级/来源槽、前提两级顺序、有限 profile、支持范围、执行模式、两类复合 grant，以及新定义的种类/算法/条件引用、有符号修正与清空事实；
缺失、多余、错误包/版本/序号和任一值不一致均拒绝。不能用组合后的最终值代替作者声明或历史操作，也不能只比较制品头摘要。
作者读取和纯材料字段链还原均校验完整原声明及历史原子的目标类型约束，后包覆盖不能掩盖非法作者事实。
非初始清空仅限目标拥有的 `author_note`、`stealth_skill`、`damage_type`；具体条件见作者来源合同。
新增存储能力不改变 `Schema2InstallationEligibility`：不完整 BASE 仍拒绝，非空 add-on
仍未获安装资格。类型化夹具直接构造有限材料，不能据此声称安装领域已验收。

## 不可变与独立批准

安装不会插入验收行。相同 module/release 的相同原 ZIP 重导入返回原安装事实；
不会为了新尝试再造一条安装事实。不同原 ZIP 即使逻辑摘要相同也返回
`S2_VERSION_CONFLICT`，验收前后都如此。原操作 UUID 重放要求指纹和完整意图一致，
否则为 `S2_OPERATION_CONFLICT`。

验收必须选择已本地安装的准确材料，并单独绑定审查材料原始字节摘要。相同准确材料与
审查摘要重放返回原验收；不同审查摘要不能覆盖旧验收。原安装 UUID 和新验收 UUID
各自保留，验收永远不能重绑新 ZIP、其他版本或另一次安装。新 UUID 若仅取得既有事实，
不产生新 UUID 的永久操作事实；查证只按原事实实际记录的 UUID 识别接受结果。

安装、验收的 UPDATE/DELETE 触发器一律拒绝；永久控制行禁止删除和重置，REPLACE
不能借删除路径改写历史。数据库生成事实时间，安装触发器核对 ZIP SHA 与总展开预算，
验收触发器核对完整安装绑定。受控事务和读回另校验数据库 CHECK/FK 不能表达的闭包。
人工验收是本地明确批准，不是作者认证、数字签名、发布批准或完整运行资格。

## 账号与独立引擎验收

`rule-source-installer.sql` 对新制品与 21 张作者材料表只有准确列级 INSERT，并获安装计数列 UPDATE；没有新验收 INSERT。
`rule-source-accepter.sql` 是独立账号模板，只能 INSERT 新验收及更新验收计数；
它对旧 S 仅获 `UPDATE(control_id)` 以支持锁读，执行路径不更新旧 S。
二者都不能改写已有制品/作者材料/验收或写 schema 账本。accepter 对作者材料只有 SELECT，
app/agent 对 35 个准确表只有 SELECT。
迁移身份仍独立，没有新增账号创建、密码或 schema 通配能力。

SQL 文件只表示可审阅迁移/权限源，不证明真实数据库执行。部署前分别授权并核对准确
V001—V008 历史、35 张表、105 个触发器、所有列/约束/索引、DEFINER、跨库入向 FK、
继承角色和既有宽权限。只读核验结合 `rule-source-schema.sql` 与
`rule-schema2-source-ledger.sql`；后者不输出 ZIP 正文。

独立 MySQL 验收还须覆盖精确二进制身份、非法 UUID/摘要/长度、同版碰撞、不可变/REPLACE、
installer 不能验收、accepter 不能安装、app 不能写、两个协议共享 S 的竞争、
真实列级锁读、回滚/提交响应丢失和关闭后恢复。代理测试仅验证 SQL 适配、闭包、
故障处理及调用方事务责任，不能替代这些引擎事实。

