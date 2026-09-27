# 离线规则源 schema

规则源链位于 `database/rules/migration/`，角色为 `RULES`，显式默认库为 `dnd_tool_rules`。
它与运行链 `src/main/resources/db/migration/`、`dnd_tool_se.schema_meta` 分开编号和批准。
规则链从 `V001__rule-source-schema.sql` 开始；已应用文件不可改写，后续变更追加本链下一个
连续编号。运行 V001—V018、RELEASED v1、旧默认发布及现有 JNDI 合同保持不变。

本合同提供离线 schema 源、批准元数据、窄权限模板和独立只读账本核验器。它不接入 Web、
JNDI、health、规则安装或加载入口，不表示已创建数据库或已通过真实 MySQL 验收。
完整规则族保持 DRAFT，语言片段不能批准完整内容、激活 archive format 2 或改变新战役默认。

## 两条独立批准链

`RuleSchemaMigrations` 编入 WAR 的只有角色、版本、准确脚本名和固定批准载荷摘要；
不从数据库或作者制品学习期望值。规则 SQL、grants、核验 SQL 均不进入 WAR。
规则链文件名为 `V` 加至少三位补零十进制版本、`__`、小写 kebab-case 责任名和 `.sql`。
`rule_schema_meta` 保存 `schema_version INT` 正整数主键、唯一 `script_name VARBINARY(255)`、
严格小写 64 位 hex `script_sha256 VARBINARY(64)` 和数据库生成的 UTC `applied_at DATETIME(6)`。
安装器与应用没有账本 DML；既有记录禁止 UPDATE/DELETE。

载荷算法：严格 UTF-8 解码，CRLF/CR 转 LF，要求唯一且顺序正确的
`CHECKSUM-SCOPE-BEGIN/END` 标记，排除紧邻标记的边界换行，对中间字节计算 SHA-256。
全部实质 schema 操作与控制行种子在作用域内；携带摘要的最后账本 INSERT 在作用域外。
文件原始字节摘要是运输证据，与规范载荷摘要分别保管。构建测试独立比较源文件与固定清单，
不自动重写批准摘要。旧运行链算法和历史摘要不变。

`RuleDatabaseSchemaVerifier` 独立读取 `SELECT CAST(DATABASE() AS BINARY)`，逐字节确认默认库，
再有序比较整个 `rule_schema_meta`。每个语句设超时，账本读取上限为期望行数加一；缺行、
多行、重复、NULL、版本/脚本/摘要不符均失败关闭。核验器关闭自身借用的连接与语句/结果，
不切库、不执行 DML/DDL、不改变连接状态、不 commit/rollback；它不证明表、权限或内容健康。

## 表与列

六表均为 InnoDB。源本地 ID、安装代次与计数使用有符号 BIGINT；只有发布根 `id` 自增。
FK 只指向源内同一发布，全部 UPDATE/DELETE RESTRICT，不级联，不引用运行 schema。
技术键、版本、枚举和摘要使用 VARBINARY，保留全部字节的大小写与尾字符区别。
SQL 将二进制输入无损映射到 latin1 字符进行大小写敏感的 ASCII 白名单检查；不用 binary
直接调用 ICU 正则。语法正则另配非法字符拒绝，避免行尾锚点放过 LF。

| 表 | 列与不变量 |
|---|---|
| `rule_release` | 正 `id`；唯一 `(module_key, release_version)`；键 1—128 ASCII，`[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*`；版本 1—64 ASCII，首字符字母或数字，其余字母/数字/点/下划线/连字符；`canonical_format_version`、`archive_format_version` 正 INT；`hash_algorithm` 精确 `SHA-256`；可 NULL `content_sha256`；`release_status` 为 DRAFT/RELEASED；`installation_revision` 为 0..Long.MAX_VALUE；UTC `created_at`、可 NULL `released_at` |
| `rule_language` | PK `(release_id, language_key)`；`display_name VARCHAR(120)`、`description VARCHAR(1000)`；`category VARBINARY(8)`；`source_page SMALLINT` 3—74；`sort_order SMALLINT` 1—18，按发布唯一；release FK |
| `rule_package_installation` | PK `(release_id, installation_revision)`，二者正数；全表唯一 `source_operation_id VARBINARY(16)` 精确 UUIDv4 网络序及 variant；`operation_fingerprint_version INT=1`；`operation_digest_sha256`；正 INT `author_schema_version`、`installation_manifest_version`；`installation_manifest_sha256`；`package_display_name VARCHAR(120)`；`verification_scope` 为 PARTITION/COMPLETE；可 NULL `observed_content_sha256`；UTC `installed_at`；release FK |
| `rule_package_installation_partition` | PK `(release_id, installation_revision, partition_key)`；前两列共同 FK 指向安装事实；首片键精确 `character.language`；不保存数组、JSON 或旧目录 |
| `rule_installation_control` | 永久 PK `control_id TINYINT=1`、`protocol_version INT=1`；`metadata_row_count BIGINT` 1—16384；`row_version BIGINT` 0..Long.MAX_VALUE；无 FK |

所有摘要列都是 VARBINARY(64)，非 NULL 时必须恰好为 64 个小写 ASCII hex。
所有文本使用 utf8mb4/utf8mb4_0900_bin，按 Unicode 码点限长，非空、非全空白且拒绝 C0/C1，
包括 NUL、TAB、LF、CR、DEL、NEL。迁移/写入连接必须使用严格 SQL mode，拒绝超长截断。
SQL 的 ICU control/space 检查须通过实际支持版本上的 Unicode 反例验收；NFC 和完整 Unicode
输入/读回校验属于安装器与 reader，CHECK 不会把分解串自动正规化。

语言键/分类是封闭矩阵，不仅比较分类总数：

| 分类 | `language.` 后缀 |
|---|---|
| STANDARD | common, dwarvish, elvish, giant, gnomish, goblin, halfling, orc |
| EXOTIC | abyssal, celestial, deep_speech, draconic, infernal, primordial, sylvan, undercommon |
| SECRET | druidic, thieves_cant |

每个完整语言分区必须恰好包含这 18 项及 1—18 连续排序；数据库约束只拒绝单行非法键/分类、
越界与重复，完整集合由 validator 核验。显示名、说明、合法页码和排序不是永久固定展示值。

## 状态、不可变与事务责任

新头只能 DRAFT、代次 0、摘要/发布时间 NULL。DRAFT 发布时间恒 NULL，可保存当前 COMPLETE
的观测摘要；RELEASED 必有摘要和数据库生成的发布时间。格式正整数是结构约束，不表示本体
支持任意格式。RELEASED 转换仍须独立批准、当前 COMPLETE、实际完整内容重算及全域发布门。
普通 installer 不获状态/发布时间权限。

PARTITION 事实的 observed 摘要恒 NULL，COMPLETE 恒非 NULL。当前事实必须与头代次和摘要
一致；历史 COMPLETE 事实永久保留原摘要，不与后来 PARTITION 头比较。每个正代次恰有一条
操作事实和完整非空分区集合，各根历史从 1 到当前代次连续；这些跨表关系由受控事务/reader
核验，CHECK 不查询其他表，不把 FK 当作闭包或内容证明。

根 UPDATE 自身持有排他行锁，触发器用 OLD/NEW 拒绝 RELEASED 改动和 id/身份/created_at
重绑，根 DELETE 一律拒绝。自增列不能用于 MySQL CHECK，因此 AFTER INSERT 在实际 ID
已分配后拒绝非正数。语言变更、事实/分区新增在触发器中 `SELECT ... FOR UPDATE` 锁所属根并
确认 DRAFT；语言 UPDATE 先拒绝跨根/键重绑。永久事实/分区 UPDATE/DELETE 一律拒绝。
这些防线也阻断受保护根/事实的 REPLACE 删除再插入路径；不使用客户端可伪造的 session 标志。
控制行身份/协议不可改，不能删除；UTC 时间由触发器生成，不能靠客户端指定时间制造事实。

唯一空源种子为 S `(control_id, protocol_version, metadata_row_count, row_version)=(1,1,1,0)`。
不种规则头、语言或安装事实。16384 是工程验证基线，未完成容量验收不能称为发布容量。
受控安装须先锁 S，再锁已有根；新根也必须在 S 下建立。u 等于 S、根、事实、分区的全源
行数之和；v 等于成功安装事实数。新安装按新增根 b 与分区数 p 收费 `u+b+1+p`、`v+1`；
重放/查证/发布不收费。安装器必须把领域行、头代次/摘要、事实、分区与计数同事务提交。
这组 schema 不自动维护计数，不实现安装服务、提交不明查证、配额闭包或发布入口。

## 账号与验收

管理员分别审核 `database/grants/rule-source-{app,agent,installer,migrator}.sql`；文件不创建
账号或设置密码。app/agent 只对六个准确源表 SELECT，没有 schema 通配或写权限；普通只读
核验不能借此声明取得源提交查证屏障。installer 只获明确列级根 INSERT/UPDATE、语言内容
写权、事实/分区 INSERT、S 两计数列 UPDATE，不能写账本、状态/发布时间、根身份、永久事实
或 S 身份。独立 migrator 仅源 schema 所需 CREATE/ALTER/INDEX/REFERENCES/TRIGGER/DML，
没有全局权限、账号管理或 GRANT OPTION；不把其能力传给 Web/installer。

库级 GRANT 中的下划线在 `partial_revokes=OFF` 时是通配符，反引号本身不能消除该语义。
migrator 模板使用 `dnd\_tool\_rules`；管理员先读取 `@@GLOBAL.partial_revokes`，若为 ON，
只将该标识符改为逐字解释的 `dnd_tool_rules`，不同时应用两种形式，也不修改全局设置。
表级授权中的库名按字面解释。实际授权验收应确认相似名称的其他 schema 仍不可访问。

触发器使用创建者 DEFINER，迁移身份必须保留触发器执行所需权限。管理员应检查有效角色、
继承权限及既有宽授权，不能只看到新 GRANT 就认定最小权限。列级 UPDATE 账号的根/S 锁读、
实际 DEFINER 以及发布竞争必须在真实 MySQL 验收；不要给永久事实补 UPDATE 来方便锁读。
发布维护的授权属于另一个检查点，不由 installer 模板授予。

执行步骤彼此独立：确认隔离目标与授权；管理员建立窄账号；规则 migrator 在明确默认库下
创建空 schema 并预检；原样执行本链获批 SQL；核对对象和空源后记最后账本；独立只读核验；
另行账号授权验收。迁移前必须确认默认库精确为 `dnd_tool_rules`、无既有对象且前序历史一致，
不能以默认库未知或可改名目标试跑。DDL 可能隐式提交，中途失败立即维护阻断，不 IF NOT EXISTS
跳过、不盲重跑、不补造账本。最后账本 INSERT 自检准确默认库、六表/十八触发器及空源种子，
不能替代完整定义核验或使多条 DDL 获得整体回滚。

`database/verify/rule-source-schema.sql` 只有 SELECT/SHOW，输出默认库、账本、种子、列、索引、
FK、CHECK、触发器/视图/例程及权限材料。触发器元数据被 MySQL 权限过滤，须由 migrator 在
停止写入后只读核验，不扩大 app/agent 权限。其空源计数只用于迁移检查点，不作为安装后准入。

无 DB 测试检查源摘要、独立清单、结构和 grant 防回归以及 JDBC 只读/资源关闭边界。
真实 MySQL 验收还必须覆盖非法高字节/尾空格/NUL、版本逐字节不同、18 项关系、补充平面文本
边界、C0/C1/全空白、UUID 长度/version/variant、正 ID 与 FK、权限反例、REPLACE 和发布并发。
连接保护必须先在 `open()`/`pooledDataSource()` 两入口实现并通过无 DB 回归，再单独授权
disposable 验收；现有仅拒绝运行库名的配置检查不足以开放这一步。不得复用部署库或临时表 IT
库安装规则。未执行真实引擎验证时只能报告本地交付通过，不能报告 schema/权限验收完成。

MySQL 约束依据：[CHECK 限制](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)、
[正则字符集](https://dev.mysql.com/doc/refman/8.0/en/regexp.html)、
[触发器语义](https://dev.mysql.com/doc/refman/8.0/en/trigger-syntax.html)、
[GRANT 的数据库名解释](https://dev.mysql.com/doc/refman/8.0/en/grant.html)。
