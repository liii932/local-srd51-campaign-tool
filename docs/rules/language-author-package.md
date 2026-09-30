# 语言作者分区

`rule-packages/srd51-complete/` 是 `dnd5e2014_srd51_se` / 字符串 `"1"` 的正式 DRAFT 作者源。
当前只含 `character.language` 的 18 项目录；包目录名不表示全部 SRD 已完成。
作者 schema 1、canonical 2、archive 2 是三个独立协议字段。只接受这个准确目标，合法但未支持
的其他身份/格式仍拒绝；沿用[发布身份合同](module-release-identity.md)，不改变注册表、默认
RELEASED v1、既有摘要或 V001—V018。

## 输入与资源边界

`module.LanguageAuthorPackageReader.read(byte[], byte[])` 只解析固定的两份作者输入：
`author-package.json` 和 `character/languages.json`。调用方按这个顺序提供原始字节；reader
不打开目录、不解析路径执行 IO、不提供命令行、Web 上传或安装能力。每次读取先检查两输入
的字节长度，再各复制一份快照；解析与 raw SHA-256 均使用同一快照。

| 限额 | 上限与执行位置 |
|---|---|
| 作者头 | 8192 原始字节，复制及 UTF-8 解码前检查 |
| 语言文件 | 262144 原始字节，复制及 UTF-8 解码前检查 |
| 合计 | 270336 原始字节，复制前检查；两个文件均不得为空 |
| JSON 深度 | 4 层，根值为第 1 层，进入下一值前检查 |
| JSON token | 每文件 512；每个值及对象键各计 1，读取前检查 |
| 对象/数组 | 最多 8 个字段/18 个元素，添加前检查；之后检查准确业务形状 |
| 解码字符串 | 4096 UTF-16 单元的解析分配预算，追加前检查；业务文本再按 NFC 后码点限额 |
| 整数 | 原 token 为 `[1-9][0-9]*`，最多 10 位且不超过 2147483647；再检查字段值域 |

这些是当前两作者文件的工程输入预算，允许 UTF-8 补充平面字符及其 JSON 代理对转义，
不等于未来所有规则正文/制品的发布容量。离线文件读取方必须先实施相同的有界读取，不能
先无限读入内存再调用纯 reader。目录枚举、普通文件/符号链接检查、漏/多文件、TOCTOU、
文档与许可盘点、installation-manifest 逐文件校验及安装前事务验证属于离线安装边界。
本 reader 的两个 raw 结果不声称验证整个目录、随包说明/许可或任何安装清单。

纯 reader 的文件数固定为 2，领域行数为 18，跨领域引用为 0。UTF-8/JSON/raw-hash 扫描随总字节
线性增长；NFC 的单字符串工作量受 4096 UTF-16 单元约束，规范排序仅处理 18 行。复制的输入
至多 270336 字节，解码字符载荷至多同数 UTF-16 单元；每文件最多 512 个值/键 token、每字符串
最多 4096 单元，对象/数组各有上述独立上界。模型仅保留 18 行及有界头/raw证据，不保留原始
字节或 JSON 树。这些是载荷/节点界，不能冒充 JVM 对象头、GC 或原生 RSS 的精确值。

工程容量验收采用独立 Surefire JVM 的 `-Xmx64m` 堆上限，分别把两原始输入填充到准确上限，
以及让全部显示名/说明达到补充平面字符码点上限并转成 JSON 代理对转义后填充至字节上限。
合法容量边界输入与深度/token/字符串攻击的纯读取均有 2 秒测试 deadline；不把文件 IO、JVM 启动、
离线清单/数据库安装包含在该时限，也不将该测试时限描述为 reader 的可中断运行超时 API。
后续离线入口须单独给出并实测其整体 deadline/峰值上界。

## 作者 schema 1

作者头是一个 JSON 对象，恰含：

- `author_schema_version`: 整数 `1`。
- `module_key`: `dnd5e2014_srd51_se`；`release_version`: 字符串 `"1"`。
- `package_display_name`: 1—120 个 NFC Unicode 码点。
- `canonical_format_version`: 整数 `2`；`archive_format_version`: 整数 `2`。
- `hash_algorithm`: 精确 `SHA-256`。
- `partitions`: 只有一个对象的数组，该对象恰含 `partition_key: character.language` 与
  `path: character/languages.json`。

固定路径白名单自然拒绝绝对路径、反斜杠、`.`/`..`、URI/百分号、大小写变体与 Windows
保留名；不提供接受其他路径的通用路径解析器。作者不提供 release_status、verification_scope、
content_sha256、observed_content_sha256、数据库身份或第二份安装清单。

语言文件是裸 JSON 数组，恰有 18 个对象。每对象恰含六字段：

| 字段 | 合同 |
|---|---|
| `language_key` | 下表精确稳定键，每个恰出现一次 |
| `display_name` | 1—120 NFC 码点 |
| `description` | 1—1000 NFC 码点，目录短说明 |
| `category` | 下表与键的准确配对；不是仅检查枚举 |
| `source_page` | 正整数原 token，值 3—74 |
| `sort_order` | 正整数原 token，整体恰为 1—18 的置换 |

所有对象拒绝未知、缺失、重复（包含转义后相同的字段名）和 null 字段。不接收注释、BOM、
非 JSON 空白、尾随文档、单引号、尾逗号或数值强转。UTF-8 解码拒绝畸形、截断、过长编码和
代理项编码；JSON 字符串必须为 Unicode 标量串，包括转义解码后的代理对检查。
作者显示文本先验证标量再 NFC，不 trim；拒绝 C0/C1、DEL、空串、全由 `isWhitespace` 或
`isSpaceChar` 组成的文本。LF/TAB/CR 也拒绝。技术身份不做 Unicode/大小写/空白修复。
`LanguagePartition.Language` 要求输入已经是 NFC，供后续存储读取复用，不能边读边修复源行。

## 基准与纯投影

正式作者源逐项承接既有语言目录基准：每项 `source_page=59`，说明为显示名加
` is an SRD 5.1 language catalog entry.`。这是目录定位，不声明两项秘密语言的完整正文已盘点。
基准显示文本和排序可随受审 DRAFT 内容演进；validator 不把它们冻结成唯一可接受值。

| 顺序 | 键后缀（统一 `language.` 前缀） | 显示名 | 分类 |
|---:|---|---|---|
| 1 | abyssal | Abyssal | EXOTIC |
| 2 | celestial | Celestial | EXOTIC |
| 3 | common | Common | STANDARD |
| 4 | deep_speech | Deep Speech | EXOTIC |
| 5 | draconic | Draconic | EXOTIC |
| 6 | druidic | Druidic | SECRET |
| 7 | dwarvish | Dwarvish | STANDARD |
| 8 | elvish | Elvish | STANDARD |
| 9 | giant | Giant | STANDARD |
| 10 | gnomish | Gnomish | STANDARD |
| 11 | goblin | Goblin | STANDARD |
| 12 | halfling | Halfling | STANDARD |
| 13 | infernal | Infernal | EXOTIC |
| 14 | orc | Orc | STANDARD |
| 15 | primordial | Primordial | EXOTIC |
| 16 | sylvan | Sylvan | EXOTIC |
| 17 | thieves_cant | Thieves Cant | SECRET |
| 18 | undercommon | Undercommon | EXOTIC |

`LanguagePartition` 是只依赖 JDK 的不可变完整语言分区，检查准确键集合、分类配对和顺序置换。
它输出 18 条窄类型 `Definition` 和 36 条 sealed `Attribute`，不创建通用属性袋或复制整份
`module.ModuleCatalog`。每项定义映射键、显示名、说明及显式顺序；类型固定
`character.language`。每项属性为 `(catalog.category,1,IDENTIFIER,Category)` 与
`(source.page,1,INTEGER,int)`；本分区没有关系。定义按 ASCII 键排序，属性按键再按 category/page
排序，等价于无符号 UTF-8 规范顺序。输入数组位置和显示顺序不能代替规范顺序。

包显示名、作者 schema、archive 格式、路径、原始长度/hash 不进入既有 canonical release
四字段或语言内容；语言显示名/说明/页/顺序进入对应规范字段，分类由键封闭约束。
只改变排版、分解/组合作者文本或包展示名可改变 raw 证据而不改变规范逻辑内容。

reader 返回 `PARTITION`、头、分区及两个作者文件的原始长度/SHA-256；没有完整内容摘要、
COMPLETE、发布批准或运行就绪能力。公开值类型可由调用方构造，因此类型存在本身不是可信
验证证明；后续安装入口仍须调用严格 reader/独立验证其实际输入。纯能力可以进入 WAR；作者
JSON、离线入口/写入适配器、生成安装载荷和测试夹具不得进入 WAR。现有生产运行路径不调用
这个 reader，也不从作者文件补齐规则。

## 独立验证

```bash
mvn '-Dtest=LanguageAuthorPackageReaderTest,ModuleCanonicalEncoderV1Test,ModuleCanonicalEncoderV2Test,BuiltinModuleReleaseRegistryTest,CampaignArchiveCapabilityBoundaryTest' test
mvn '-Dtest=LanguageAuthorPackageReaderTest' '-DargLine=-Xmx64m' test
```

测试从正式作者源读取真实字节，逐项对照独立手写矩阵；采用测试专用窄适配将纯分区送入
既有 canonical-v2 encoder，与 `src/test/resources/module-canonical-v2-languages.hex` 比较。
该 hex 由独立大端 u32/标量标签编码过程根据本页矩阵构造，未调用生产 reader/mapper/encoder，
覆盖 release 四字段、18 定义、36 属性、零关系。它是现有 DRAFT 编码的测试向量，不是新分区
摘要协议、完整内容批准值或可激活规则包。反例覆盖六字段、严格词法、资源预算、Unicode、
准确集合/分类、输入重排与显式顺序变化。完整验证与 WAR 正/负资产检查按[测试指南](../testing.md)。
