# 有限 schema 2 规则制品与生产编码

本合同定义 `ProductionRuleArtifact` 的真实原始 ZIP 入口及
`ProductionSchema2Projection` 的生产摘要。作者语义沿用
[schema 2 最小组合链路](../design/v2/schema-2-composition-validation.md)的封闭类型模型，
支持 `character.language`、`catalog.language_set`、`character.tool`、`catalog.tool_set`
及两种集合的 `members` 条目，并按[初始授予合同](schema-2-proficiency-grants.md)支持
四类角色授予来源与 `proficiency_grants` 条目。角色为
`base / extension / compatibility`，定义、条目、修改、直接依赖、联合版本、顺序约束、
字段覆盖与最终候选检查均由同一个严格读取和组合内核完成。

[成长与资源](schema-2-character-statistics.md)另定义其有限类型，
[检定、装备与健康作者来源](schema-2-foundation-authoring.md)另定义 `adventure.check`、
`equipment.item`、`adventure.health`、`adventure.vital_effect` 的字段、条件引用及严格清空。
这四类定义已接通作者、纯组合、真实来源和[准确镜像/事实存储](schema-2-character-fact-storage.md)；
新业务消费者尚未采用。纯材料还原与角色仓储恢复各自验证不同事实，不能据此取得执行资格。

“生产编码”指可用于真实制品保管和准确身份绑定的字节合同，不表示完整基础、机制支持、
安装成功、DM 接受或运行资格。有限基础始终缺少完整跨领域证据；自洽原包、逻辑、配置、
最终规则及覆盖链不能替代资格门。`requireProductionQualification` 对有限投影仍返回
`S2_BASE_INCOMPLETE`。作者不能在清单中自报批准、完整性或摘要。

## 封闭目录类型

| 类型 | 必需领域字段 | 可选领域字段 |
|---|---|---|
| `character.language` | `display_name,description,category,source_page`；category 为 STANDARD/EXOTIC/SECRET | `author_note` |
| `character.tool` | `display_name,description,category,source_page`；category 为 ARTISAN/KIT/GAMING_SET/MUSICAL_INSTRUMENT/NAVIGATION/VEHICLE | `author_note` |
| `catalog.language_set`、`catalog.tool_set` | `display_name,minimum_enabled_members` | `author_note` |
| language_set 的 `members` 条目 | `identity,language`；language 为完整 `character.language` 身份 | `author_note` |
| tool_set 的 `members` 条目 | `identity,tool`；tool 为完整 `character.tool` 身份 | `author_note` |
| `character.race/background/class` 授予来源 | `display_name,description,source_page` | `author_note` |
| `character.subrace` 授予来源 | 上述来源字段和完整 `parent_race` 身份 | `author_note` |
| 角色来源的 `proficiency_grants` 条目 | `identity,grant`；grant 为封闭 FIXED/CHOICE 完整原子 | `author_note` |

定义另含完整 `identity`。显示名为 1—120 码点，说明和备注为 1—1000 码点，来源页为
3—74 的精确整数；沿用严格标量、NFC、非空白和 C0/C1 控制字符拒绝策略。
`minimum_enabled_members` 是 1—128 的精确整数。工具分类与现有
[工具熟练目录分区](tool-catalog-partition.md)的六值分类同源，类型模型为 `ToolCategory`；
说明、分类与来源页均是窄类型字段，没有通用属性袋。schema 2 不复制 schema 1 分区的
`sort_order` 或固定 37 项全量约束；扩展包可以声明自己原始拥有的新工具身份。

两种集合都仅有 `members` 集合。工具成员只接受 `tool` 字段并引用工具，语言成员只接受
`language` 字段并引用语言；修改时也不能跨域使用这些字段或身份。两种引用字段均为完整
身份的原子修改单元。上述语言、工具和授予类型清空仅允许 `author_note`；
装备与健康效果的条件字段清空按[作者来源合同](schema-2-foundation-authoring.md#覆盖清空与来源链)限定；
启停仅通过 `set_enabled`。

角色来源仅表达初始授予领域，不能替代完整种族、背景或职业规则。授予的类别、固定目标或
候选集合与数量整体写入 `grant`，覆盖不能只更换其子字段；`parent_race` 是亚种的完整引用
原子。所有声明、修改和覆盖步骤进入既有生产投影及摘要域，现有语言／工具向量保持原字节。
最终候选由“成员启用且所指目录项启用”共同决定，按完整目标身份去重，重复成员不增加
候选数；每个集合都必须满足声明的下限，禁用集合也不能规避完整局部投影检查。
外部集合、目标项和修改目标的原始所属模组均须有直接依赖，引用只能指向本包或先前包
已声明的对象。工具目录与集合不声明制作、使用算法或任何机制执行支持。

## 真实 ZIP 输入

`ProductionRuleArtifact.read(byte[])` 在内存内读取唯一原包，先检查原始长度，再复制输入，
对实际复制的完整原始字节计算 SHA-256。它不调用文件系统解压器，不创建文件，也不接受
调用方自报摘要或“原包与文件集对应”的声明。只有 ZIP reader 的内部封闭能力对象能够把
已校验原包及提取文件交给严格作者读取器。所有解析、字段来源及覆盖链位置均绑定真实
原包 SHA-256；无法取得完整允许长度原包的长度错误不伪造摘要。

接受的运输 profile 是有限 ZIP32：

- 单磁盘，非空，使用 STORED（0）或原始 DEFLATE（8）；DEFLATE 的 version-needed 为 20，
  STORED 为 10—20；local 与 central 的 version-needed 必须相同。
- 文件名为 1—240 个可打印 ASCII 字节，无空格。路径段符合
  `[A-Za-z0-9][A-Za-z0-9._-]*`，不得以点结尾；不得有空段、`.`、`..`、反斜杠、冒号、
  绝对路径或目录项。精确重复及 ASCII 大小写冲突都拒绝。
- flags 只允许 UTF-8 标记 `0x0800`；不支持加密、数据描述符或 DEFLATE 可选 flags。
  made-by 系统只接受 DOS（0）或 Unix（3）；Unix 文件类型只能为空或普通文件，
  拒绝 symlink 及其他特殊类型。external attrs 低 16 位和 internal attrs 均为零。
- 拒绝所有 local/central extra、entry comment、archive comment、ZIP64 标记、分卷。
  不支持自解压前缀、数字签名、未知记录、空洞、重叠及任何尾随字节。
- EOCD 恰为最后 22 字节。目录精确计数并连续结束于 EOCD；按物理偏移排序后的
  local records 连续覆盖 `[0, central_offset)`。central 顺序与物理文件顺序不赋予语义。
- local/central 的文件名、flags、method、version-needed、时间日期、CRC32、压缩和展开长度
  必须一致。实际展开长度与 CRC32 必须匹配；DEFLATE 必须结束且消费准确压缩区间，
  不允许第二个流或压缩区间尾随垃圾。

在分配任何展开文件数组前，检查**所有**目录和 local records、每文件预算及整个包预算：

| 项目 | 上限 |
|---|---|
| 原始 ZIP | 2,097,152 字节 |
| ZIP 文件总数 | 20 |
| 作者清单 | 16,384 字节 |
| 清单登记的数据文件 | 16 个，每个 65,536 字节 |
| 作者文件展开总计（含清单） | 1,048,576 字节 |
| 元数据文件 | 根目录精确名称 `LICENSE`、`NOTICE`、`README.md`，各至多一个 |
| 每个元数据文件 | 32,768 字节 |
| 全部文件展开总计 | 1,146,880 字节 |
| 每文件展开比例 | `expanded <= compressed * 100 + 1024` |
| 一次组合 | 至多 4 个准确包 |

元数据必须是无 BOM 的严格 UTF-8；允许 TAB/LF/CR，拒绝其他 C0 和全部 C1 控制字符。
元数据只保留在原包中，不是作者 JSON，不进入逻辑投影。移除这些封闭元数据名称后，
实际作者文件必须恰等于 `author-package.json` 与其 `files` 声明；不允许未列出的文件，
也不允许把元数据登记为作者数据。JSON 的字节、深度、token、字符串、数组及领域行数预算
继续使用最小组合链路合同。作者文本按 Unicode 码点计数、NFC 规范化并拒绝控制字符。

## 生产规范字节与五类摘要

profile 固定为 `schema2-production-json-1`，组合算法固定为
`schema2-production-projection-1`。J 是 UTF-8、无空格、无末尾换行的 JSON：对象键按 ASCII
升序，字符串直接输出合法非 ASCII 标量，转义双引号、反斜杠和 JSON 必须转义字符；
整数为十进制，boolean 为小写；可选无值字段省略，不写 null。

与内部验证编码共享 JSON 转义和类型化投影实现，但**不得复用验证摘要**。令
`H(t,x) = SHA-256(UTF8("local-dnd/schema2-production/1/") || ASCII(t) || 0x00 || J(x))`，
hex 为小写 64 字符。四个封闭 tag 为：

| 摘要 | 定义 |
|---|---|
| 原始 ZIP R | `SHA-256(原始 ZIP 全字节)`，无前缀 |
| 模组逻辑 L | `H("module-logic", P)` |
| 组合配置 K | `H("composition-config", Q)` |
| 最终规则 F | `H("final-rules", {rules,entries})` |
| 完整覆盖链 T | `H("full-chain", {composition_sha256:K,chains})` |

P 沿用严格作者类型投影：清单删除 `files,package_display_name`，加入
`definitions,entries,changes`。定义按 `(module_key,rule_type,rule_key)` 排序；条目按
`(rule 的排序键,collection_key,module_key,entry_key)` 排序。目标先按 kind 的 ASCII 顺序
（entry、rule），再按对应身份排序。changes 按 `(目标,field)` 排序，set_enabled 的 field
视为 enabled；不改写作者 op，也不删除合法同值修改。dependencies 按 module_key，
supported_versions 按 ASCII，compatible_combinations 按 J 的无符号字节，约束按
`(before,after)` 排序。所有领域字段及 author_note 都进入 P；不注入默认 enabled。

Q 恰含 `author_schema_version=2,canonical_profile,composition_algorithm,members`；members
严格保持选择顺序，绝不自动排序、去重或挑选版本。每项恰含
`module_key,release_version,role,raw_zip_sha256=R,logical_sha256=L`，使用本生产域的 L。
Q 不包含 F 或 T。最终规则只包含完整类型化规则与条目（包括禁用对象），按上述身份排序，
不包含加载顺序、发布版本、来源或覆盖过程。

chains 是内核重建的完整字段链，按 `(目标,field)` 排序、步骤按实际发生顺序。每个字段
包含初始步骤及所有显式操作，包括同值覆盖；before/after 分别区分 uninitialized、absent、
present。来源包含准确 provider、load_index、真实原包摘要、包内路径、JSON Pointer 和 origin。
覆盖链 envelope 使用生产 K，不能绑定验证配置。`verifyChains` 从原始已读取作者包重新组合，
按完整规范字节核对，不接受只比较摘要或只重算链内局部值。

仅改变排版、ZIP 压缩/顺序/时间、包展示名、元数据或文件拆分，L/F 不变而 R/K/T 改变。
移除合法同值操作时 F 可不变，但 L/K/T 改变。验证专用的 `validation-json-1`、
`local-dnd/schema2-validation/1/` 及现有 RELEASED canonical/archive digest 域保持不变。
本 profile 不激活 `dnd5e2014_srd51_se`，不改变 RELEASED 默认，也不授予 archive 2 或运行支持。

## 调用与证据边界

`ProductionRuleArtifact` 公开准确身份、角色、`canonicalProfile()`、双摘要、`logicalJson()`、
`originalZip()`、包含全部原始作者和元数据字节的 `originalMembers()` 及不可伪造的
`authorPackage()`；`expandedByteLength()` 返回全部已验证成员的展开字节总和，供来源整体预算核对。
数组均为防御性副本，成员映射不可修改且每次深拷贝。安装与持久化使用
**artifact.logicalSha256()**；嵌套 `AuthorPackage.logicalSha256()` 保留内部验证域，不能写作
生产摘要。

`ProductionSchema2Projection.compose(List<ProductionRuleArtifact>)` 公开生产配置、最终规则、
完整链字节及各自摘要；它复用内核依赖/顺序/字段算法。`projection()` 可供资格门检查类型化
结果；其中既有内核 JSON/digest 仍为验证专用。生产读写方只使用外层生产访问器。

类型出口保持封闭：`Definition` 包含语言、语言集合、工具、工具集合、角色授予来源、
成长/资源/熟练/休息，以及本合同链接的检定、装备和健康作者定义；
`FinalRule.definition()` 返回相应类型化定义。语言/工具/初始授予的条目为
`LanguageEntry`、`ToolEntry` 和 `ProficiencyGrantEntry`，成长条目另按成长合同限定；共用
`identity()/authorNote()/target()`，分别保留 `language()`、`tool()` 和 `grant()`。
授予条目的 `grant()` 为 `FixedGrant / ChoiceGrant` 完整原子，`target()` 返回固定目标或候选集合。
`FinalEntry` 持有该封闭条目。存储还原入口 `restore` 从这些完整类型行与来源链重建规范字节，重新检查
字段形状、已规范化文本、引用类型、候选下限、逐字段链完整性、来源连续性和声明先后；
不依赖原始来源 ZIP 可用，也不把坏存储文本自动正规化为合法值。

独立固定字节和摘要在
[`schema2-production` 测试资源](../../src/test/resources/schema2-production/README.md)，
恶意 ZIP、展开预算、Unicode、原包来源、不可变副本与摘要关系由
`ProductionRuleArtifactTest` 验证。生产资格、安装/接受持久化及运行接入各有自己的门与事务，
不能从本页的局部制品通过结论推导完成状态。

混合语言／工具的独立固定资源位于
[`schema2-tools`](../../src/test/resources/schema2-tools/README.md)：真实 37 项工具、两种集合、
三份原始 ZIP、生产逻辑／配置／最终字段／完整链字节及摘要均有固定期望。
`Schema2ToolCompositionTest` 核对全部原始工具字段、生产编码与存储还原，并拒绝混域、
通用字段、坏文本、间接或缺失引用、重复候选计数、启停后的候选不足和不完整来源链。
既有语言验证及生产向量的字节与摘要合同保持原样。
