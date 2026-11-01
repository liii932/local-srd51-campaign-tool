# 当前版本 JSON 结构声明与战役建库

状态：当前应用结构与有限 SQLite 建库合同。`StorageSchemaCatalog` / `StoragePlan`、
编译/verifier 和领域仓储支撑当前有限角色/冒险/战斗聚合及完整文件；实际覆盖和本次
证据见[领域接合表](domain-integration.md)。完整 SRD/任意外部包/未来机制仍须独立采用。
本页定义应用结构 JSON 到单战役运行库的完整边界；现状、数据覆盖和替换批次见
[数据映射与实施路线](storage-data-map-and-rollout.md)，权威分工见
[规则存储](rule-storage-and-versioning.md)，发布与恢复见[运行合同](runtime-and-transactions.md)。

## 结构源、作用域与支持范围

应用随当前版本交付**实际表结构 JSON**，包含表、列、类型、键、约束、索引、分片依赖和
静态种子。它取代运行结构的手写 SQL 资源作为唯一声明源；生成的 SQL 是方言编译结果，
不再维护另一份可独立修改的生产 DDL。目标资源位于 `src/main/resources/storage/`，
声明读取、计划和 SQLite 实现位于 `com.dndtool.persistence` 的存储职责下。
当前路径承载生命值记录与选配 runtime 的全部已接合领域；新增领域仍须同步真实字段、
严格 codec/仓储/验证和文件，不能凭 schema 新表声称已支持。

当前编译器拒绝可空复合外键（包括显式全空 CHECK 的形式）、含立即边的外键循环、
二进制或布尔值的大小比较；这些高级形式不能因下方语法示例存在而使用。
循环只在每条相关边均为延迟外键时接受。领域执行资格由有限规则目录和服务入口显式限定，
通用结构编译成功不授予新业务能力。现有六种标量及所用谓词的边界以对应结构测试为准。

| 输入 | 谁拥有 | 可以决定什么 |
|---|---|---|
| 应用结构 JSON | 应用开发者；同批修改仓储、校验、文件投影和测试 | 当前版本可用结构及其有限依赖、类型和约束 |
| 规则作者 JSON / 原始 ZIP | 当前四包随应用生产验证交付；未来外部内容经独立安装/接受/组合门 | 已支持规则类型、内容、机制参数和准确规则依赖 |
| 战役创建计划 / 完整战役 JSON | 创建消费者 / 严格文件 reader | 业务事实、准确规则绑定及必须恢复的历史、继续义务 |
| 应用 profile/机制到结构的有限映射 | `CampaignRules` / `StoragePlan` 及真实消费者 | 选定 profile 所需分片、全部未来写入/历史/pending 足迹；不要求尚不存在的通用注册框架 |

作者规则包和战役文件都不能指定表名、列、SQL、驱动、路径或动态类名；不能用同名 JSON
文件替换应用资源。新机制需要扩展应用声明和消费者，并重新作为当前应用版本交付。
应用内部编译器只支持本页列出的关系结构子集，不承担通用 ORM、查询语言、用户插件或
多数据库迁移框架。业务查询仍由类型化 Repository 明确实现。

物理隔离单位是一次打开的战役：每次新建或从完整文件打开，分配独占目录和一个 SQLite
主库；全部选定分片进入这个库，不按规则包、角色或机制再拆库，不用 `ATTACH` 拼事务。
共享的是应用 JSON/实现和当前内置四包，不共享可变战役表。当前新建/文件打开不需来源
MySQL；原外部来源的维护仍用独立 SQL/账号，不在战役打开时创建。未来来源索引职责
改变须协调 reader/installer/权限，不能由当前结构选择跳过旧维护合同。

只支持本应用声明的准确 `applicationModel`、`storageModel` 和声明协议。它们用于同版
识别和一致性校验，不是版本比较、增量升级或跨版本存档转换指令。新应用可以更换结构，
但不静默修改旧库、重写旧账本或删除真实数据。

## 有限声明格式

声明根恰含 `declarationVersion`、`applicationModel`、`storageModel`、`dialect`、`modules`。
当前声明协议为整数 `1`，方言为 `sqlite-strict-1`；其余两个模型标识为应用注册的准确 ASCII
标识。一个发布版本只含一套受支持声明，不按“最高版本”选择。`modules` 是分片数组：

| 对象 | 必需字段与语义 |
|---|---|
| 分片 | `id`、`dependsOn`、`tables`、`seeds`；依赖是同一 storageModel 中的准确分片 ID |
| 表 | `name`、`columns`、`primaryKey`、`uniqueKeys`、`foreignKeys`、`checks`、`indexes`；允许无条目的集合必须显式为空 |
| 列 | `name`、`type`、`nullable`；只允许下表与该类型相符的额外字段，`default` 可省略 |
| 主键 | 非空有序列名数组；每表恰一个，所有成员 `nullable=false`，由应用分配键，不使用自增 |
| 唯一键 | `{name, columns}`；columns 为非空有序列名数组；键名唯一 |
| 外键 | `{name, columns, references:{table,columns}, onDelete, onUpdate, deferred}`；两侧等长、逐列类型一致，引用本库主键或声明的唯一键 |
| CHECK | `{name, expression}`；expression 为下面的封闭谓词树，不能放 SQL 字符串 |
| 索引 | `{name, columns, unique}`；列序有意义，统一 ASC/BINARY，不允许表达式、部分索引或输入定义排序规则 |
| 静态种子 | `{table, rows}`；rows 是该表列名到类型值的对象数组，只写所属表，按完整主键规范排序 |

分片 ID 用小写 ASCII kebab-case；物理表/列/键/索引名用 `[a-z][a-z0-9_]{0,62}`。
表、显式索引和约束名在整个计划内唯一，表名前缀表达分片所有者。每张表只有一个声明
owner；其他分片可依赖、引用，不能追加列或覆盖原定义。重复到达同一分片只装配一次。
原型不开放视图、触发器、生成列、虚拟表、任意 PRAGMA、SQL 片段或外部 include。

reader 严格读取 UTF-8：拒绝 BOM、重复对象键、未知/缺失字段、尾随内容、注释、非有限数
和孤立 surrogate。声明与选定闭包均有预算；协议 1 的单份资源上限为 16 MiB、128 个分片、
512 张表、每表 256 列/128 个显式约束/128 个索引、每个谓词深度 16/节点数 1024，静态
种子总行数不超过 10000。该上限是编译器输入边界，业务规模另由容量合同控制；超界使
应用声明不可用，不能截断或省略分片。静态资源仍须在构建和加载时验证，不能因来自 WAR
就假定结构正确。

### 列类型与校验责任

| 逻辑类型 | JSON 附加字段 / 值表示 | SQLite 编译与运行责任 |
|---|---|---|
| `int64` | 可选 `minimum` / `maximum`；值和边界为有符号规范十进制字符串，缺省为有符号 64 位全范围 | `INTEGER`，范围 CHECK；JDBC `long`，拒绝溢出、浮点、指数和隐式截断 |
| `boolean` | JSON boolean | `INTEGER CHECK (列 IN (0,1))`；仓储显式绑定 0/1 |
| `uuid` | 小写规范 UUIDv4 字符串 | `BLOB` 加长度 16 CHECK；规范字节顺序固定，UUID 版本/variant 由应用校验 |
| `digest256` | 64 位小写十六进制字符串 | `BLOB` 加长度 32 CHECK；只表达已定义摘要，不能用作规则接受证明 |
| `text` | 必需 `minCodePoints`、`maxCodePoints`、`ascii`；长度界为有界 JSON 整数 | `TEXT COLLATE BINARY` 加长度 CHECK；所有文本由应用按码点验证 NFC、范围和 C0/C1/DEL 拒绝；ascii=true 另要求全 ASCII |
| `bytes` | 必需 `maxBytes`；JSON 值为规范 base64 | `BLOB` 加字节上限 CHECK；仅供明确二进制事实，不能藏任意业务 JSON |

`nullable` 必须明确，null 不能充当缺失字段。`default` 若存在，必须是该列允许的标量
或显式合法 null；不支持时间函数、UUID 函数、随机数、表达式及隐式 NOW。已验证导入
必须显式提供全部必存事实，不能用 DDL 默认值修补缺字段。精确数量/金额/距离使用
声明单位的整数；比值拆为分子分母，分母约束为正，不引入 REAL/浮点规则计算。

所有表编译为 `STRICT, WITHOUT ROWID`；只使用 INTEGER/TEXT/BLOB，避免隐式 rowid 或
宽松类型成为业务身份。STRICT 仍允许无损类型转换，因此它不能代替 Java 输入类型验证。
该引擎能力的要求依据 [SQLite STRICT tables](https://www.sqlite.org/stricttables.html)，
当前 JDBC 固定为 3.50.3.0 并由真实引擎测试核对；不以文档能力代替实际执行证据。

谓词树只允许 `and` / `or`（非空 `args`）、`not`（`arg`）、`isNull` / `notNull`（`column`）、
`eq/ne/lt/le/gt/ge`（`left/right`）、`in`（`column/values`）。比较操作数恰为
`{column:列名}` 或 `{literal:类型值,type:逻辑类型}`；不支持函数、正则、算术、子查询或跨表表达式。
枚举用 `in`；同列范围用类型界；跨列如 `hp_current <= hp_max` 用比较节点。校验器检查类型、
可空性和递归/节点预算；编译结果用 `CHECK ((谓词) IS TRUE)`，不能让 SQL NULL 悄悄通过。
允许 null 的业务必须明确写出 `isNull OR ...`。跨行邻接、NFC、历史语义和图闭包由类型化
validator 负责，不借触发器或动态表达式引擎实现。应用每次从 DB 读回仍执行领域校验。

外键当前只允许 `onDelete=NO_ACTION`、`onUpdate=NO_ACTION`；不隐式级联删除被历史
引用的事实。`deferred=true` 编译为 `DEFERRABLE INITIALLY DEFERRED`，否则立即检查。
当前复合外键必须全部非空，不支持可空复合外键或用全空 CHECK 模拟该形式。
关系列按真实 owner 声明准确外键；typed 聚合内的完整规则/业务/历史引用由独立领域图
验证，不能因为物理键存在就接受跨战役或错准备身份。模块内循环只有所有边均延迟才
接受，模块依赖仍无环。
子外键列必须有以同序列为前缀的主键/索引；声明校验检查这项，不依赖运行时猜索引。
父键使用声明 PK/UNIQUE，不能依赖可独立删除的唯一索引。

每个连接在事务外启用并读回 `foreign_keys=ON`；在事务中设置它不会生效。
延迟外键的存在也不替代最终 `foreign_key_check` 和领域图验证。
依据 [SQLite foreign keys](https://www.sqlite.org/foreignkeys.html)。

### 结构有效不等于机制可用

应用按当前受支持 profile 明确分片根及契约：全部表、真实读写 owner、结构/数据 verifier、
规则材料、严格文件 codec、无副作用恢复及未来写入依赖。当前由 CampaignRules/StoragePlan
和实际消费者落实，不把尚无实现的 StorageModuleContract 类型当作已存在的注册框架。
未实现项不能通过空表或空集合占位。表声明不决定类加载路径，也不自动生成业务服务。
初始化专用连接能力允许 DDL、静态种子和固定规则写入；发布后业务事务仅允许所属可变
表的类型化写操作，`rules` 与静态种子只读，不能把原始可写连接交给规则消费者。该边界
替代当前 MySQL 触发器/账号对这些表的不可变保护，必须有拒绝运行期规则修改的测试；
普通文件恢复也经初始化入口，不能绕开固定材料验证。

每张物理列必须有“规则固定材料、当前事实、历史事实、本地控制、可重建”之一的责任；
可移植列再映射到唯一文件字段或明确的确定性推导，技术列明确排除。历史前后快照虽然
重复值，仍是不可删的历史事实。不得把有 operation UUID 的列一律当技术收据删除；
当前物品、动作和事件的业务因果键需要独立保留或建立稳定映射。

## 从选定范围计算确定性闭包

有两种不同的图与顺序：

1. **规则图**：严格校验当前 profile 的准确有序组合、恰一个 base、依赖版本、联合兼容、
   冲突及覆盖顺序。递归计算准确依赖闭包并与有序清单核对；缺成员就返回缺项，不能
   自动挑同名/最新版本、接受原包或重排用户组合。重新组合得到完整最终规则及覆盖链。
2. **存储图**：应用根据已验证材料确定分片根，沿应用 JSON 的 `dependsOn` 取最小不动点。
   它可以自动纳入所需应用结构，不改变任何规则成员、加载顺序或支持资格。

根集合是以下并集；不能按“现在没有行”或文件自报分区来缩小：

- 必需 `core`、`rules`、`history`、`characters`：运行控制、规则绑定/固定材料、统一历史和
  公共完整战役输入。必需表即使合法为空也创建。
- 全部最终规则和完整覆盖链，包括禁用、覆盖掉的字段值、METADATA/BLOCKED 内容的
  结构类型与引用。它们需要保存、解释、检查，不因此获得执行资格。
- 当前选择中可执行机制的**全部未来写入足迹**：成本、资源、健康、场景、动作继续、
  反应、效果、订阅和后缀，即使本次没有对应角色或法术实例也必须具备。
- 新建计划或文件的全部初始/当前状态、全历史、已结束且仍被引用的实例，以及 pending
  动作、窗口、选择、准备材料和待回调义务。按类型化材料提取，未知类型拒绝。

例如施法消费者要求 `spellcasting/resources/equipment/actions/effects/characters/scenes/history`；
战斗使用同一健康、资源与动作接口。一个规则包可要求多个分片，多个包可共用同一分片，
不为每个法术、怪物或包复制表。双向领域调用不等于双向结构依赖；将共同键/根事实放在
下层，跨域关联由上层所属分片引用两侧，真实不可拆循环则合入一个声明分片。

当前 rules-runtime 用严格 typed TEXT 完整保存 members/最终 rules/entries/chains，不按
法术/怪物再复制规则叶表；characters-runtime/combat 等保存完整 typed 聚合，resources/
equipment/spellcasting/actions/effects/monsters 等保存独立核验的关系投影。其全部列由
应用 JSON 声明，正文由封闭 record/enum codec 和领域/整场验证约束；无任意属性或类名。
保存 metadata 不授予机制执行资格。当前 runtime 根纳入全部后续写入和历史/pending
结构，空队伍/无施法者不裁剪。未来可调整有限分片，不能逐内容动态建表。

计划算法是纯函数，不分配目录、不连接数据库、不消费 RNG：

```text
校验应用声明与当前 profile/真实消费者的有限结构契约一致
校验准确规则图 + 输入事实/历史/继续图
roots := mandatory ∪ rule-storage ∪ supported-write-footprints ∪ input-references
closure := roots 沿 dependsOn 展开到不再增加
校验全部引用、owner、codec/restore/consumer 和支持资格
modules := 稳定拓扑排序（同层以 ASCII id 升序）
tables := 按模块顺序；模块内外键强连通组拓扑排序，同组按表名升序
plan := 全部 CREATE TABLE → 全部显式索引 → 静态种子任务 → 数据装配/验证任务
输出有序结构、schemaPlanDigest、各分片的纳入原因路径
```

每个跨分片外键都要求所属分片显式依赖目标 owner；检查未声明依赖，不靠外键静默补图。
模块内先建全表再插数据，循环键声明延迟；立即外键按父行先于子行插入。空库创建不用
`IF NOT EXISTS`，已存在任何未登记用户对象都不是“可继续初始化”。不存在 `ALTER`、
追加迁移、启动补表或运行中拓展闭包；同版残留走独立验证路径。

规范计划按固定协议展开所有默认值：对象键 UTF-8 字节序，分片/表按上述稳定顺序；
`dependsOn` 拒绝重复后按 ID 排序，命名约束/索引按 name 排序，种子任务按表顺序、行按
类型化完整主键排序。列、键成员和其余有业务意义的数组保留声明顺序；整数值以规范字符串，
文本按本页规则。规范 UTF-8 无空白、BOM 和尾随换行，字符串仅转义引号和反斜线。
摘要输入为 ASCII `DND-STORAGE-PLAN`、零字节、ASCII `1`、零字节，接完整规范计划。
计划包含声明协议、应用/存储模型、方言、所选完整结构、索引、静态种子和对应实现合同
标识；不包含 run ID、目录、动态业务值或诊断原因路径。相同闭包可用于不同准确规则组合，
规则绑定仍须单独核验。摘要用于持久计划绑定，不能代替结构检查、字段比较或规则摘要。

## 最小完整声明示例

以下是可独立编译的三表声明，用两个分片演示类型、主外键、唯一键、CHECK、索引、依赖
与静态 seed；全部引用都在片段内。`storage-example-1` 是示例模型，不是可执行战役模型。
生产 `core/rules/history` 的完整控制、规则链及各领域表须按数据映射交付，不能把本例
作为完整战役建库的占位替身。

```json
{
  "declarationVersion": 1,
  "applicationModel": "storage-example-1",
  "storageModel": "storage-example-1",
  "dialect": "sqlite-strict-1",
  "modules": [
    {
      "id": "core",
      "dependsOn": [],
      "tables": [
        {
          "name": "core_actor_kind",
          "columns": [
            {"name": "kind_key", "type": "text", "nullable": false, "minCodePoints": 2, "maxCodePoints": 3, "ascii": true}
          ],
          "primaryKey": ["kind_key"],
          "uniqueKeys": [], "foreignKeys": [], "checks": [], "indexes": []
        },
        {
          "name": "core_campaign",
          "columns": [
            {"name": "campaign_key", "type": "uuid", "nullable": false},
            {"name": "name", "type": "text", "nullable": false, "minCodePoints": 1, "maxCodePoints": 80, "ascii": false}
          ],
          "primaryKey": ["campaign_key"],
          "uniqueKeys": [], "foreignKeys": [], "checks": [], "indexes": []
        }
      ],
      "seeds": [{"table": "core_actor_kind", "rows": [{"kind_key": "NPC"}, {"kind_key": "PC"}]}]
    },
    {
      "id": "characters",
      "dependsOn": ["core"],
      "tables": [
        {
          "name": "characters_character",
          "columns": [
            {"name": "character_key", "type": "uuid", "nullable": false},
            {"name": "campaign_key", "type": "uuid", "nullable": false},
            {"name": "kind_key", "type": "text", "nullable": false, "minCodePoints": 2, "maxCodePoints": 3, "ascii": true},
            {"name": "name", "type": "text", "nullable": false, "minCodePoints": 1, "maxCodePoints": 80, "ascii": false},
            {"name": "hp_max", "type": "int64", "nullable": false, "minimum": "1"},
            {"name": "hp_current", "type": "int64", "nullable": false, "minimum": "0"},
            {"name": "archived", "type": "boolean", "nullable": false, "default": false}
          ],
          "primaryKey": ["character_key"],
          "uniqueKeys": [{"name": "uq_characters_campaign_key", "columns": ["campaign_key", "character_key"]}],
          "foreignKeys": [
            {"name": "fk_characters_campaign", "columns": ["campaign_key"], "references": {"table": "core_campaign", "columns": ["campaign_key"]}, "onDelete": "NO_ACTION", "onUpdate": "NO_ACTION", "deferred": false},
            {"name": "fk_characters_kind", "columns": ["kind_key"], "references": {"table": "core_actor_kind", "columns": ["kind_key"]}, "onDelete": "NO_ACTION", "onUpdate": "NO_ACTION", "deferred": false}
          ],
          "checks": [
            {"name": "ck_characters_hp", "expression": {"op": "le", "left": {"column": "hp_current"}, "right": {"column": "hp_max"}}}
          ],
          "indexes": [
            {"name": "idx_characters_kind", "columns": ["kind_key"], "unique": false},
            {"name": "idx_characters_campaign_name", "columns": ["campaign_key", "name"], "unique": false}
          ]
        }
      ],
      "seeds": []
    }
  ]
}
```

示例选择 `characters`，结构闭包和执行顺序为 `core → characters`，生成三条 CREATE
TABLE、两条 CREATE INDEX 和两个参数化种子 INSERT。例如角色表中的逻辑声明编译为：

```sql
CREATE TABLE "characters_character" (
  "character_key" BLOB NOT NULL CHECK (length("character_key") = 16),
  "campaign_key" BLOB NOT NULL CHECK (length("campaign_key") = 16),
  "kind_key" TEXT COLLATE BINARY NOT NULL CHECK (length("kind_key") BETWEEN 2 AND 3),
  "name" TEXT COLLATE BINARY NOT NULL CHECK (length("name") BETWEEN 1 AND 80),
  "hp_max" INTEGER NOT NULL CHECK ("hp_max" >= 1),
  "hp_current" INTEGER NOT NULL CHECK ("hp_current" >= 0),
  "archived" INTEGER NOT NULL DEFAULT 0 CHECK ("archived" IN (0,1)),
  PRIMARY KEY ("character_key"),
  CONSTRAINT "uq_characters_campaign_key" UNIQUE ("campaign_key", "character_key"),
  CONSTRAINT "fk_characters_campaign" FOREIGN KEY ("campaign_key")
    REFERENCES "core_campaign" ("campaign_key") ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT "fk_characters_kind" FOREIGN KEY ("kind_key")
    REFERENCES "core_actor_kind" ("kind_key") ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT "ck_characters_hp" CHECK (("hp_current" <= "hp_max") IS TRUE)
) STRICT, WITHOUT ROWID;
CREATE INDEX "idx_characters_campaign_name" ON "characters_character" ("campaign_key", "name");
CREATE INDEX "idx_characters_kind" ON "characters_character" ("kind_key");
```

标识符来自已校验声明并统一引用，文字默认值由编译器按固定转义生成；所有数据行使用
PreparedStatement。示例的 PC/NPC 是应用静态分类；战役 UUID/名称和角色 HP 是创建计划
的动态值，不能写入应用 seed，也不能从 schema 默认值推测已有角色的消耗。

## 公共接口与建库事务

以下为责任接口，具体 Java 类型名可随实现细化，但事务和输入边界必须保持：

| 接口 | 输入 / 输出 | 禁止的副作用 |
|---|---|---|
| `StorageSchemaCatalog` | 打包 JSON + 代码注册合同 → 已验证不可变声明 | 不读上传目录、不访问战役库 |
| `StoragePlan` 与应用范围映射 | 当前准确 profile、严格声明及完整输入/引用 → 闭包、计划、摘要与纳入原因 | 不改变规则选择、不分配资源、不消费随机数 |
| `SqliteSchemaCompiler` | 计划 → 类型化 DDL/绑定 seed 任务 | 不执行 SQL、不接受任意 SQL 或字段路径 |
| `CampaignStorageService` 初始化协调 + `CampaignRepository`/域叶仓储 | 独占新空间、计划、固定 material/链和完整业务 → 单事务准备结果、commit 后读回和首份检查点 | 不以半初始化开放 Host、不用普通命令重演历史 |
| `SqliteSchemaVerifier` + 完整聚合/投影/历史验证 | 当前计划与独占已有库 → 结构/材料/事实/控制证据 | 不补表、不迁移、不重 seed、不重建损坏投影 |
| `CampaignStorageService` 事务 owner | 当前运行资格/独占根 → 受控 SQLite 连接与 BEGIN IMMEDIATE | 叶仓储不提交/回滚/另开写连接；Servlet 不决定数据库路径 |

新建或从 JSON 打开的建库顺序：

1. 在健康空闲边界完成有界输入验证，取得经生产 reader/compose 验证的内置四包固定投影；
   文件将完整 members/rules/entries/chains 逐项精确比对此投影，不重新组合或要求来源 MySQL。
   生成全部静态结构和无副作用初始化计划，先核容量及每个字段/历史/继续消费者。
2. 获取工具实例和新目录独占权，持久记录分配意图：准确 operation、输入指纹、规则绑定、
   实际 core run、应用/存储模型、声明协议、方言、闭包清单及 `schemaPlanDigest`。
   文件中的 schema-2 prepared Identity 作为稳定业务事实保留，不重建为物理运行权限。
   新目录和空数据库只属于 PREPARING；文件中的业务键不决定路径。
3. 打开唯一初始化写连接，在事务外配置并读回 `journal_mode=DELETE`、`synchronous=EXTRA`、
   `foreign_keys=ON` 和有限 busy timeout。所选 JDBC 的事务适配统一发起 `BEGIN IMMEDIATE`，
   不混用自动开启的 JDBC 事务与第二个 BEGIN。
4. **一个事务**内执行所有表和索引 DDL，插入应用静态 seed，再写固定组合/规则/完整链、
   业务根/初始与当前状态、全历史和 pending、必要本地控制。动态初始数据由领域计划
   产生；来源规则不是 schema seed。写入 core 的结构清单和初始化收据，与全部事实共同提交。
5. 提交前实际逐域读回并核独立期望、结构、引用、约束和历史图。跨域输入失败则整体回滚；
   创建表成功或 seed 数量相同不证明初始化成功。明确 commit 后用健康连接再次核登记
   和完整读回，仍保持 PREPARING；该提交后验证可与首份 P0 的同一完整一致读取复用，
   首次完整检查点 P0—P3 接受后才发布 ACTIVE/新 epoch。

新建若包含受支持的随机角色生成，纯计划保存已验证的生成请求及预算，不预掷骰。
初始化器在全部输入、规则、结构和领域前置验证后，才在上述受保护初始化事务内生成
本次结果，并与初始/当前卡和收据共同保存；不能放入 schema 默认值或静态 seed。
文件恢复永不调用 RNG。已明确回滚不要求倒退随机源，但不会有已接受初始结果；提交
未知须查清原初始化，不能重新掷骰或换操作重做。详见[公共初始化输入](character-state-and-history.md#新建与文件恢复的公共初始化输入)。

SQLite 的 CREATE 属于写事务；一个写连接保证结构与初始化数据共同提交的目标仍需用
真实驱动验证，见 [SQLite transactions](https://www.sqlite.org/lang_transaction.html)。
DELETE journal 下选择 EXTRA，使 journal 删除后的目录同步纳入提交耐久设置；FULL
在该模式下不能一概保证断电后保留最近提交，依据
[SQLite synchronous](https://www.sqlite.org/pragma.html#pragma_synchronous)。该设置不替代
目标文件系统实测，也不证明外部战役 JSON 的 P2 发布耐久性。

### 已有库、错误与重入

| 情况 | 处理 |
|---|---|
| 声明坏字段、重复名、非法类型/引用/依赖循环、消费者缺失 | 计划阶段拒绝；没有目录、DDL、seed 或 RNG 副作用；诊断给稳定分类和声明位置 |
| 空间不足、创建/锁/配置失败 | 不进入初始化；保留可准确识别的准备材料，解除未持久操作或走原意图取消 |
| DDL、seed、数据或读回失败且 rollback 明确成功 | 整次初始化未提交；核验空结构及原分配意图后可按同一计划重试，静态 seed 不用 upsert 掩盖残留 |
| 初始化 commit/rollback/关闭结果未知 | BLOCKED；隔离原执行者并让引擎恢复，核原分配标记、初始化收据、结构和完整事实；不换 operation/目录重做 |
| 完整初始化已提交但首份检查点未接受 | 只继续原 P0—P3 或精确放弃；不重建表、不重写业务、不发布执行资格 |
| 已证明原初始化未提交，且库恢复后无用户结构 | 原准确输入/计划仍可得时才重试；拿不到就保持准备或精确取消，不凭空构造新输入 |
| 有部分结构、额外表/触发器、不同模型/闭包/计划或缺数据 | 完整性故障，保留并隔离；不运行 CREATE IF NOT EXISTS、ALTER 或 seed 来“修好” |
| 已有完整同版运行 | 核当前计划/实际结构、聚合/全部关系投影/历史/接受及保存收据，再显式新 epoch；无独立 C0/未持久请求重放或结构写入 |

结构核验同时读 `sqlite_schema`、表列/主键、外键、索引、STRICT/WITHOUT ROWID 属性；
CHECK/默认值等 PRAGMA 未完整表达的项目与编译器固定 DDL 规范结果核对，禁止只比较
头摘要。拒绝未知用户对象，允许的 SQLite 内部对象由方言明确白名单。再运行
`integrity_check` 与独立 `foreign_key_check`，并执行应用全域验证；前者不覆盖外键错误。
不读数据库自报结构来生成一个“相符”的新计划。残留的闭包根据原规则、完整历史和
pending 重新计算且必须相等，不在恢复时缩小或扩张。

静态 seed 在完整初始化中只插入一次。重复打开正常 JSON 会建立新库并有自己的第一次
初始化；恢复同一个残留不会再 seed。规则、业务初始化、业务恢复与 schema 初始化是
不同责任，禁止在每次启动/打开时统一调用“幂等建表并补默认数据”。

## 采用验收

- 独立结构向量断言确切表/列/类型/键/索引/CHECK和计划顺序；输入 JSON 对象/无序集合
  的排列变化不改变规范计划，规则加载顺序变化仍影响规则绑定；模块循环、FK类型/父键、
  默认值、表达式和预算错误在执行前拒绝。
- 在实际 SQLite JDBC 临时目录验证三表例、全部生产分片及代表性组合；外键、约束、
  Unicode/二进制/数值和延迟引用真正生效，不用代理 Connection 替代。
- 注入每个 DDL、索引、seed、规则、业务、收据及 commit 边界故障；证明全无或全有，
  同版重入不补结构、不重复授予，首份保存前不执行，未知不换键重做。
- 检测同版被删除/添加/改列的结构、错误计划/规则绑定和损坏控制；真实进程中断与恢复、
  锁/句柄释放、磁盘满和 P0—P3 按生命周期验收。
- 数据映射中每个当前领域都有真实新仓储、字段覆盖、完整历史/文件和无副作用恢复证据；
  未支持机制保持原资格门。引擎基础可以定向先交付，完整 Host 切换须等全域门通过。

文档里的 JSON/SQL 示例可做格式和内部引用检查，不代表生产编译器、迁移、WAR 或数据库
已验证。实施批次和旧数据库保管按[替换路线](storage-data-map-and-rollout.md)执行。
