# 工具熟练目录分区

`character.tool` 为 DRAFT `dnd5e2014_srd51_se` / 字符串 `"1"` 的 37 项工具熟练引用目录。
它与 18 项语言共享作者包、发布根、安装事实和运行快照身份。这里的完整集合只指本目录，
不包含装备价格、重量、制作、使用算法或角色选择规则，也不是全部角色领域或 COMPLETE。
旧 RELEASED v1、默认发布、规范字节和存档格式保持不变。

## 字段与封闭集合

正式材料为 `rule-packages/srd51-complete/character/tools.json`。数组准确包含 37 个对象，
每个对象恰含 `tool_key`、`display_name`、`description`、`category`、`source_page`、`sort_order`。
未知、缺失、重复和 null 字段失败关闭。共同严格 JSON、UTF-8、精确整数、输入预算和 NFC
规则见[作者入口](language-author-package.md)。显示名为 1—120 码点，说明为 1—1000 码点，
禁止控制字符和全空白。来源页为 3—74，排序整体为 1—37 的置换。

| 分类 | `tool.` 键后缀 |
|---|---|
| ARTISAN | alchemist_supplies, brewer_supplies, calligrapher_supplies, carpenter_tools, cartographer_tools, cobbler_tools, cook_utensils, glassblower_tools, jeweler_tools, leatherworker_tools, mason_tools, painter_supplies, potter_tools, smith_tools, tinker_tools, weaver_tools, woodcarver_tools |
| KIT | disguise_kit, forgery_kit, herbalism_kit, poisoner_kit, thieves_tools |
| GAMING_SET | dice_set, playing_card_set |
| MUSICAL_INSTRUMENT | bagpipes, drum, dulcimer, flute, lute, lyre, horn, pan_flute, shawm, viol |
| NAVIGATION | navigator_tools |
| VEHICLE | vehicles_land, vehicles_water |

键与分类必须准确配对。正式材料承接既有 V011 目录的英文显示名和短说明，来源页为 70，
显式排序按 ASCII 键排列。显示文本、合法页码和排序可随受审 DRAFT 修改，并未冻结为唯一值。
作者文本可正规化到 NFC；存储读取只接受已经规范化的值，不修复坏行。

`ToolPartition` 是不可变类型模型，产生 37 条 `character.tool` 定义及 74 条窄类型属性。
每项的 `catalog.category` 是 IDENTIFIER，`source.page` 是 INTEGER；没有关系。
`CharacterCatalogPartition` 汇合语言与工具，通过同一 `ModuleCatalog` 和 canonical-v2
encoder 产生 55 条定义、110 条属性、零关系。数组位置不替代显式排序，规范顺序按稳定键。

## 安装、来源与运行

来源 V002 在独立规则链追加 `rule_tool` 及分区键约束。主键 `(release_id, tool_key)`，排序
按发布唯一，外键只引用来源发布根且 RESTRICT。三种内容写入触发器锁根并要求 DRAFT，
UPDATE 不允许重绑键或发布。已有 V001 和永久语言安装事实保持原样。

新安装的 profile 为 `srd51-character-catalog` / `1`，同一事务保存两领域、根代次、永久事实、
两项分区与控制行计数，提交前实际读取并比较全部后像。旧语言票据只保留其原事实查证能力。
完整文件盘点、指纹和失败处理见[离线安装](offline-language-installation.md)。

`JdbcSourceLanguageRepository.loadCatalog()` 在一个只读一致事务中验证两项当前分区及两张
领域表，返回纯 `CharacterCatalogPartition`。工具查询最多观察第 38 行；缺行、多行、错误
JDBC 类型或非法字段拒绝整次读取。来源 ID、安装代次及连接能力不进入模型或规范字节。

运行 V020 在 V019 登记/头/语言结构上追加 `runtime_rule_tool`。主键为
`(snapshot_id, tool_key)`，本地外键 RESTRICT，排序按快照唯一，UPDATE 触发器禁止改写。
运行表不引用来源表或来源自增 ID。新表初始为空，不升级任何已有快照。

`JdbcRuntimeCharacterCatalogRepository.append/read` 使用调用方提供的运行连接和准确
run/snapshot UUIDv4 身份对。append 一次写入登记、头、语言和工具，并实际 SELECT 比较完整
内容；read 只读本地快照，来源断开后仍有效。任何写入、读回或后续业务失败均由调用方回滚
整笔事务。Repository 不提交、回滚、关闭连接或创建 savepoint。已提交身份不可复用。
头仍是 DRAFT/PARTITION、NULL 内容摘要，不能直接用于游戏运行或存档格式 2。

## 核验与采用门

独立手写 `ToolCatalogOracle` 的六字段矩阵与语言矩阵组成期望，
`tools/rule-packages/catalog-vector.py` 使用独立大端编码生成
`src/test/resources/module-canonical-v2-language-tools.hex`，不调用生产模型、reader 或 encoder。
各链路边界逐字段及逐字节比较；该字节向量不是可保存为完整内容摘要的发布证明。

```bash
mvn '-Dtest=CharacterCatalogAuthorPackageReaderTest,ToolAuthorPartitionTest,RuleArtifactTest,SourceInstallationTest,JdbcSourceLanguageRepositoryTest,CharacterCatalogChainTest,ToolCatalogSchemaTest,RuleSchemaMigrationsTest,SchemaMigrationsTest' test
python3 -B tools/rule-packages/verify-language-jdbc.py
python3 -B tools/rule-packages/tests/verify-language-jdbc-test.py -v
```

代理链路覆盖正式材料、真实安装 SQL 适配、来源读取、运行实际读回与独立字节，逐工具写点
回滚、调用方后续失败、坏字段/跨快照拒绝、来源不可用及多身份隔离。源安装测试覆盖所有
DML 写点、代次冲突、原接受重放、提交未知、永久历史和预算。SQL 静态检查不能代替引擎约束。

真实验证沿用[隔离 JDBC 入口](language-partition-equivalence.md#隔离-jdbc-入口)，另行授权后
在可销毁实例原样重放运行 V001—V020、来源 V001—V002，核对实际结构和窄账号，再验证
双领域安装、读取、约束、禁止权限及回滚。安装分别在语言和工具写入时触发真实 CHECK 拒绝，
用新连接确认七张来源表完整保留原状态；工具运行镜像另测 NULL、未知键、分类错配、重复
排序与无父快照外键，失败后登记、头和两个领域均无残留。核验 SQL 和授权模板分别为
`rule-tool-catalog.sql`、`v020-runtime-tool-snapshot.sql` 与 `runtime-tool-snapshot.sql`。
未执行时必须保留未验收状态，普通构建不操作数据库或部署。

WAR 只包含纯模型、只读/运行适配器、批准清单和运行迁移；作者材料、离线安装类、来源 SQL、
grants、核验脚本和测试向量不进入 WAR。其他角色、装备/冒险、战斗/状态、法术、怪物/魔法物品
的完整链路与跨领域 COMPLETE 门仍需分别实现和验收。
