# schema 2 作者包到组合结果的最小链路验证

状态：有限 schema 2 严格读取、纯组合与验证专用编码已有内部可测实现；本页仅规定验证入口，
生产编码、安装/接受及内部运行材料沿各自正式合同采用，不由本页的局部投影授予资格。
实现范围与调用方式见[内部组合内核](#9-内部组合内核)。
本页细化[模组接口基线](module-interface-baseline.md)的有限输入和期望输出，不新增运行资格。
**正向结论为局部投影通过；完整组合资格必须拒绝。** 三个语言和一个测试集合不构成完整 `base`。
这与现有[语言作者分区](../../rules/language-author-package.md)的 schema 1 / PARTITION 是不同材料，
也不改变[规则存储与版本](rule-storage-and-versioning.md)的完整快照、独立接受与发布门。

## 1. 验证上下文与边界

完整包应沿公共基线通过原包、离线安装、实际组合、开始/恢复和运行各门。本向量只验证
作者字节到局部类型化结果、完整字段链及摘要绑定的确定性；不把局部门命名为安装成功。
`validation_base@1` 的 `role=base` 是用于检查角色/顺序的作者声明，**不是基础完整性证明**。
投影计算可在独立验证上下文中查看；生产离线基础完整性门必须拒绝此包，不能等到运行才拒绝。
没有真实完整基础、机制接入证据和本地准确接受事实，因此不产生 COMPLETE、RELEASED、
可安装/可执行资格、业务快照、存档或数据库写入。本页不引入跳过这些门的生产选项。

Common、Dwarvish、Elvish 的 STANDARD 分类及页码来自 [SRD 5.1 p59](../../references/srd-5.1/pages/page-059.md)。
`catalog.language_set`、集合名字、说明文字、备注及覆盖值都是自定义验收材料，不是 SRD 更正，
不宣称已实现语言选择或授予。这里的有限检查器只计算“启用的关系且目标语言启用”的候选数，
按目标语言的完整身份去重，多条关系指向同一种语言不能增加候选数。
真实选择、授予抑制、完整角色与开始/恢复仍须各自采用合同及[机制接入验收](mechanism-integration-checklist.md)。

三个准确包依次为 `validation_base@1`、`validation_extension@1`、`validation_compatibility@1`。
后文 B/E/C 仅是表格缩写；真实 JSON 始终使用完整身份。集合 S 是 B 的
`catalog.language_set / language_set.standard`；L1/L2/L3 分别是 B 的 Common、B 的 Dwarvish、E 的 Elvish；
M1/M2/M3 是 S 下分别由 B/B/E 原始拥有的 `common`、`dwarvish`、`elvish` 条目。

## 2. 有限 schema 与严格读取

本页规定 schema 2 的这组有限类型合同；不代表其他领域字段或完整生产编码已经定稿。
清单恰含下面输入中的九个字段；role 仅 base/extension/compatibility，没有 canonical/archive、摘要、资格或数据库字段。
`package_display_name` 为 1—120 NFC Unicode 码点，沿用下文显示文本的标量、控制字符和全空白拒绝策略。
`files` 每项恰含 `kind,path`，kind 仅 `definitions/entries/changes`，各文件根为数组；
清单与实际作者文件一一对应，无多/漏/重复路径。本向量只采用下列 ASCII 文件名。
通用 ZIP 路径安全、许可文件、安装容量和 SQL 不是本投影的已验收能力。

| 对象 | 准确形状与限制 |
|---|---|
| rule 身份 | 恰含 `kind="rule", module_key, rule_type, rule_key` |
| entry 身份 | 恰含 `kind="entry", rule`（完整 rule 身份）, `collection_key, module_key, entry_key` |
| 模组键 | `[a-z][a-z0-9_]{0,63}`；正例选定三键，不以它们作语法白名单；不作大小写/空白/Unicode 修复 |
| 版本 | 1—64 ASCII 字符，`[A-Za-z0-9][A-Za-z0-9._-]*`；准确比较，不解析数字 |
| 定义 | `identity` 加所选类型的全部必需字段，可有本表明确的可选字段；不接受 `enabled` |
| 条目 | `identity,language` 必需，`author_note` 可选；不接受 `enabled` |
| set_field | 恰含 `op="set_field",target,field,value`；值匹配该原子字段完整类型 |
| clear_field | 恰含 `op="clear_field",target,field`；仅能清空 `author_note` |
| set_enabled | 恰含 `op="set_enabled",target,value`；value 必须是 JSON boolean |

`character.language` 恰有必需的 `display_name,description,category,source_page` 和可选 `author_note`；
`category` 是 STANDARD/EXOTIC/SECRET，本向量三项均 STANDARD；`source_page` 是 3—74 整数。
`catalog.language_set` 恰有必需的 `display_name,minimum_enabled_members` 和可选 `author_note`；
后者是 1—128 整数。它只有 `members` 集合；每个条目的 `language` 必须引用 `character.language`。
规则键匹配 `[a-z][a-z0-9_.]{0,127}`，条目键匹配 `[a-z][a-z0-9_]{0,63}`；集合键固定 members。
它们都是准确 ASCII 字符串；规则身份始终是 `(原始所属 module_key,rule_type,rule_key)`。
这里只允许这两种规则类型、这一个集合和上述具名字段，不接收任意属性袋或脚本机制。

每个具名字段是原子修改单元；`language` 是完整身份，不能只修改其 `rule_key` 子字段。
`enabled` 是独立系统状态，只能用 `set_enabled` 修改。`display_name` 为 1—120 NFC 码点，
`description` 和 `author_note` 为 1—1000 NFC 码点。`author_note` 是明确新增的编辑备注字段，
不影响候选计数，但进入本向量逻辑摘要、完整最终规则与覆盖链。省略它得到“无值”，
清空得到同样终态并保留显式步骤；`description` 沿用非空要求，不能用 clear 或 null 绕过。

读取顺序：先检查所有原始长度并复制快照，再严格 UTF-8/JSON，验证闭合形状和类型，最后构造模型。
拒绝 BOM、畸形 UTF-8、孤立代理项、注释、尾逗号、尾随文档、重复解码键、未知/缺失必需字段和 null。
文本先检查 Unicode 标量再 NFC；不 trim；拒绝 C0/C1、DEL、空串和全空白。身份保持原字节语义。
整数 token 仅 `0|[1-9][0-9]*`，最多十位且不超过 2147483647，再查字段范围；不接受浮点/指数/字符串强转。
本演算预算：最多 4 包、每包 16 作者文件；头 16384 字节、其他单文件 65536、每包总计 1048576；
每文件深度 16、32768 个值/键 token、单字符串 4096 UTF-16 单元，数组最多 256 元素、对象最多 16 字段；
每包最多 64 定义、128 条目、256 修改。均在相应分配/追加前检查，所有约束同时生效。
这些只是有限验证预算，不是通用制品容量承诺。现有 CharacterCatalogAuthorPackageReader 只接受 schema 1，JSON 只支持正整数/字符串等
窄形状，深度 4、对象 8 字段；不能仅将其 schema 判断改为 2 来处理 boolean、嵌套身份和新清单。

## 3. 完整正向作者输入

各标题给出包身份及准确包内路径。代码块内部 UTF-8 字节、原有空格及每行 LF 均保留，
最后一行后恰有一个 LF；围栏及标题不入文件。每包只含其清单和登记的文件。

### validation_base@1 / author-package.json

```json
{
  "author_schema_version": 2,
  "module_key": "validation_base",
  "release_version": "1",
  "package_display_name": "Validation base projection",
  "role": "base",
  "dependencies": [],
  "compatible_combinations": [{}],
  "load_order_constraints": [],
  "files": [{"kind":"definitions","path":"rules.json"},{"kind":"entries","path":"members.json"}]
}
```

### validation_base@1 / rules.json

```json
[
  {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"display_name":"Common","description":"Common is a Standard language.","category":"STANDARD","source_page":59,"author_note":"Base editorial note."},
  {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"},"display_name":"Dwarvish","description":"Dwarvish is a Standard language.","category":"STANDARD","source_page":59},
  {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"display_name":"Standard languages","minimum_enabled_members":1}
]
```

### validation_base@1 / members.json

```json
[
  {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"common"},"language":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"author_note":"Base common relation."},
  {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"dwarvish"},"language":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"}}
]
```

### validation_extension@1 / author-package.json

```json
{
  "author_schema_version": 2,
  "module_key": "validation_extension",
  "release_version": "1",
  "package_display_name": "Validation extension",
  "role": "extension",
  "dependencies": [{"module_key":"validation_base","supported_versions":["1"]}],
  "compatible_combinations": [{"validation_base":"1"}],
  "load_order_constraints": [{"before":"validation_base","after":"validation_extension"}],
  "files": [{"kind":"definitions","path":"rules.json"},{"kind":"entries","path":"members.json"},{"kind":"changes","path":"changes.json"}]
}
```

### validation_extension@1 / rules.json

```json
[
  {"identity":{"kind":"rule","module_key":"validation_extension","rule_type":"character.language","rule_key":"language.elvish"},"display_name":"Elvish","description":"Elvish is a Standard language.","category":"STANDARD","source_page":59}
]
```

### validation_extension@1 / members.json

```json
[
  {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_extension","entry_key":"elvish"},"language":{"kind":"rule","module_key":"validation_extension","rule_type":"character.language","rule_key":"language.elvish"},"author_note":"Extension editorial note."}
]
```

### validation_extension@1 / changes.json

```json
[
  {"op":"set_field","target":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"field":"description","value":"Common is available in this validation set."},
  {"op":"clear_field","target":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"field":"author_note"},
  {"op":"set_enabled","target":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"},"value":false},
  {"op":"set_enabled","target":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"common"},"value":false},
  {"op":"set_field","target":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"field":"minimum_enabled_members","value":3}
]
```

### validation_compatibility@1 / author-package.json

```json
{
  "author_schema_version": 2,
  "module_key": "validation_compatibility",
  "release_version": "1",
  "package_display_name": "Validation compatibility",
  "role": "compatibility",
  "dependencies": [{"module_key":"validation_base","supported_versions":["1"]},{"module_key":"validation_extension","supported_versions":["1"]}],
  "compatible_combinations": [{"validation_base":"1","validation_extension":"1"}],
  "load_order_constraints": [{"before":"validation_extension","after":"validation_compatibility"}],
  "files": [{"kind":"changes","path":"changes.json"}]
}
```

### validation_compatibility@1 / changes.json

```json
[
  {"op":"set_field","target":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"field":"description","value":"Common is available in this validation set."},
  {"op":"set_enabled","target":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"},"value":true},
  {"op":"set_enabled","target":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"common"},"value":true},
  {"op":"clear_field","target":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_extension","entry_key":"elvish"},"field":"author_note"},
  {"op":"set_field","target":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"field":"author_note","value":"Validation context only."},
  {"op":"set_field","target":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"common"},"field":"language","value":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"}}
]
```

## 4. 组合算法与逐步期望

先验证每包全部文件，建立完整身份索引；每个新增对象原始所属模组必须等于当前包。
新增规则与条目分别收齐再解引用，文件/数组顺序不产生优先级。新增外部规则下的条目检查
规则所属与条目所属；引用值也检查其所属模组。当前包自身之外，每个被用到的模组都须直接依赖。
同模组所有文件按 `(完整目标身份,领域字段或 enabled)` 排重；同值重复也拒绝。
新增对象只能在声明中确定最终内容，不能在本包 changes 再改；已有身份不能再次新增。

随后检查选定清单：恰一个完整基础是生产资格门；局部演算只保留角色结构检查并显式记录该门失败。
同模组不许重复出现；版本准确匹配支持集合与**完整联合配对**，不取笛卡尔积。
本向量有依赖边 B→E、B→C、E→C；`before,after` 分别是有向边起点/终点。
全部约束端点限声明者及直接依赖；拒绝自环、重复边、相反边和任何循环。
依赖边与显式边的合法重合不是重复声明；系统只检查 B,E,C 的用户顺序，不重排。

每包收齐新增定义/条目后，逐一检查其引用只能指向本包或前序包中真实存在的正确类型对象，
引用缺失立即拒绝；再应用它对前序对象的 changes，引用字段的新值也在当时完成同样检查。
修改目标必须由前序包创建，不能让后序新增或修正挽救本包无效引用。
引用先检查直接依赖，再检查当前可见范围、存在性与目标类型；允许范围内不存在的键报 MISSING。
GRAPH 不用引用推导或补写 dependency；不能为了可见性重排用户清单。
每条输入指令都先独立验证身份、直接依赖、字段和值；较后包不能掩盖较早包的错误。
禁用内容仍做完整类型/引用检查。合法包间的暂时候选不足可由兼容包修复，前缀从不具备运行资格。
全部合法操作完成后，重新检查条目归属、引用目标类型、引用存在性及集合的最终候选数。
关系禁用只抑制关系，语言禁用抑制引用它的候选；均不删除记录或改写另一方 enabled。

| 检查点 | 对象/修改计数 | 有效成员与判定 |
|---|---|---|
| B 读完 | 3 规则、2 条目、0 修改 | M1、M2；2≥1，只有局部一致性 |
| E 加入并执行 | 4 规则、3 条目、5 修改 | M3；1<3，暂时不足，等待剩余合法包 |
| C 执行 | 4 规则、3 条目、累计 11 修改 | M1、M2、M3；3≥3，局部投影通过 |
| 生产完整资格 | 不生成安装/接受/业务结果 | `S2_BASE_INCOMPLETE`；机制/接受亦无证明，拒绝 |

## 5. 完整最终类型化投影

以下是完整输出，不省略禁用记录（本正例终态全部重新启用）；无值 `author_note` 省略。
它仅用于本页验算，不传给现有 ModuleCatalog 或 canonical-v2 encoder 冒充完整发布。

### expected / final-rules.json

```json
{
  "rules": [
    {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"display_name":"Standard languages","minimum_enabled_members":3,"author_note":"Validation context only.","enabled":true},
    {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"display_name":"Common","description":"Common is available in this validation set.","category":"STANDARD","source_page":59,"enabled":true},
    {"identity":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"},"display_name":"Dwarvish","description":"Dwarvish is a Standard language.","category":"STANDARD","source_page":59,"enabled":true},
    {"identity":{"kind":"rule","module_key":"validation_extension","rule_type":"character.language","rule_key":"language.elvish"},"display_name":"Elvish","description":"Elvish is a Standard language.","category":"STANDARD","source_page":59,"enabled":true}
  ],
  "entries": [
    {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"common"},"language":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.common"},"author_note":"Base common relation.","enabled":true},
    {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_base","entry_key":"dwarvish"},"language":{"kind":"rule","module_key":"validation_base","rule_type":"character.language","rule_key":"language.dwarvish"},"enabled":true},
    {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"validation_base","rule_type":"catalog.language_set","rule_key":"language_set.standard"},"collection_key":"members","module_key":"validation_extension","entry_key":"elvish"},"language":{"kind":"rule","module_key":"validation_extension","rule_type":"character.language","rule_key":"language.elvish"},"enabled":true}
  ]
}
```

## 6. 完整字段覆盖链

每个目标的每个领域字段及 enabled 都有一条链，包括初始无值；共 31 链、31 初始步骤、11 修改步骤。
状态对象仅有 `{"state":"uninitialized"}`、`{"state":"absent"}`、`{"state":"present","value":V}`；
初始 before 为 uninitialized，不与 absent 混淆。每步恰含 `op,before,after,provider,load_index,author_location,origin`。
`provider={module_key,release_version}`；load_index 为 B=0、E=1、C=2；author_location 恰含
`raw_zip_sha256,path,pointer`，其摘要取第 7 节对应包原始 ZIP。准确版本由 provider 绑定。
initial 的 origin 为 `declared/default_enabled/omitted_optional`；修改的 origin 固定 `explicit`。
initial op 固定 `initial`，其余准确为作者 op；链对象恰含 `target,field,steps`。

下表列出全部步骤；“—”表示无步骤，不能为它造一条继承记录。值列即每步 after，before 必须等于
上一步 after。`∅`=absent，其余为 present；D0/D1 是上文 Common 的原始/修改 description，均可直接取完整文本。
位置写法 `B:r0/x`=B 的 `rules.json#/0/x`，`E:m0/x`=E 的 `members.json#/0/x`，
`C:c0`=C 的 `changes.json#/0`；r/m/c 分别对应 rules/members/changes。
缺省 enabled/省略备注的位置指向所在对象，如 `B:r0`，origin 明确说明未伪造作者字段。

| 目标.字段 | initial（值；位置） | E（操作、值；位置） | C（操作、值；位置） |
|---|---|---|---|
| L1.display_name | Common；B:r0/display_name | — | — |
| L1.description | D0；B:r0/description | set_field D1；E:c0 | set_field D1；C:c0 |
| L1.category | STANDARD；B:r0/category | — | — |
| L1.source_page | 59；B:r0/source_page | — | — |
| L1.author_note | Base editorial note.；B:r0/author_note | clear_field ∅；E:c1 | — |
| L1.enabled | true；B:r0 | — | — |
| L2.display_name | Dwarvish；B:r1/display_name | — | — |
| L2.description | Dwarvish is a Standard language.；B:r1/description | — | — |
| L2.category | STANDARD；B:r1/category | — | — |
| L2.source_page | 59；B:r1/source_page | — | — |
| L2.author_note | ∅；B:r1 | — | — |
| L2.enabled | true；B:r1 | set_enabled false；E:c2 | set_enabled true；C:c1 |
| L3.display_name | Elvish；E:r0/display_name | — | — |
| L3.description | Elvish is a Standard language.；E:r0/description | — | — |
| L3.category | STANDARD；E:r0/category | — | — |
| L3.source_page | 59；E:r0/source_page | — | — |
| L3.author_note | ∅；E:r0 | — | — |
| L3.enabled | true；E:r0 | — | — |
| S.display_name | Standard languages；B:r2/display_name | — | — |
| S.minimum_enabled_members | 1；B:r2/minimum_enabled_members | set_field 3；E:c4 | — |
| S.author_note | ∅；B:r2 | — | set_field Validation context only.；C:c4 |
| S.enabled | true；B:r2 | — | — |
| M1.language | L1 完整身份；B:m0/language | — | set_field L1 完整身份；C:c5 |
| M1.author_note | Base common relation.；B:m0/author_note | — | — |
| M1.enabled | true；B:m0 | set_enabled false；E:c3 | set_enabled true；C:c2 |
| M2.language | L2 完整身份；B:m1/language | — | — |
| M2.author_note | ∅；B:m1 | — | — |
| M2.enabled | true；B:m1 | — | — |
| M3.language | L3 完整身份；E:m0/language | — | — |
| M3.author_note | Extension editorial note.；E:m0/author_note | — | clear_field ∅；C:c3 |
| M3.enabled | true；E:m0 | — | — |

重算逐字段终值并与第 5 节比较，任意漏链、漏同值步骤、错误 before、提供者、位置或 load_index 均失败。
Common.description 的 D1→D1 及 M1.language 的同身份设置都必须留链；clear 不恢复低层备注。

## 7. 可复核制品与摘要绑定

这是**验证专用**编码 `validation-json-1` / 组合算法 `validation-schema2-projection-1`，不写入任何现行
content_sha256、批准注册表、canonical/archive 2 或已持久化 digest domain；生产字节合同仍待采用。
令 J 为递归按 ASCII 对象键排序、UTF-8、无空格、无结尾 LF 的 JSON，字符串直接输出非 ASCII 标量，
仅转义双引号、反斜杠及 JSON 必须转义字符；文本已 NFC。整数十进制、boolean 小写；数组顺序由下文指定。
rule 排序键为 `(module_key,rule_type,rule_key)`；entry 为 `(rule 的排序键,collection_key,module_key,entry_key)`；
目标排序为 kind 的 ASCII 顺序再上述身份键，链再按 field ASCII 排序，步骤按发生顺序。

原包向量使用第 3 节准确文件字节构造无目录项的 ZIP32 STORED：文件名按 ASCII 升序，
local header 和 central header 均不含 extra/comment；不使用描述符、ZIP64、前后附加字节或 archive comment。
每项 filename 为 ASCII，flags=0、method=0、DOS time=0、DOS date=33（1980-01-01）；CRC32 与两种 size
来自文件原始字节。local version-needed=20；central version-made-by=0x0314、version-needed=20、
internal attrs=0、external attrs=`0100644 << 16`、disk-start=0。central offset 指实际 local header；
标准 local/central/EOCD 字段均 little-endian，EOCD 两盘号=0、两计数相同、目录 size/offset 按实际字节。
这只是可重建原始 ZIP 摘要的夹具配方，不要求生产一般原包使用这种排版或压缩。

逻辑投影 P：取清单删除 `files,package_display_name`，添加 `definitions,entries,changes` 三数组（缺用途为空）。
定义/条目采用严格解析、NFC 后的完整作者行，分别按身份排序；不注入初始 enabled 或缺省备注。
changes 按 `(目标排序键,field)` 排序，set_enabled 的排序 field 视为 enabled，但不改写原操作对象。
dependencies 按 module_key、各 supported_versions 按 ASCII 排序；配对按其 J 字节、约束按 `(before,after)` 排序。
只有文件拆分、排版、文件次序及包展示名不进入 P；所有规则字段及 author_note、显式同值操作都进入。

定义 H(t,x)=SHA-256(`UTF8("local-dnd/schema2-validation/1/")+UTF8(t)+0x00+J(x)`)；hex 为小写 64 字符。
R_m=SHA-256(原始 ZIP 全字节)，L_m=H("logic",P_m)。组合配置 Q 恰含
`author_schema_version=2,canonical_profile="validation-json-1",composition_algorithm="validation-schema2-projection-1",members`；
members 按 B,E,C 顺序，每项恰含 `module_key,release_version,role,raw_zip_sha256=R_m,logical_sha256=L_m`。
K=H("config",Q)；F=H("rules",第 5 节 JSON)；T=H("chain",{"composition_sha256":K,"chains":第 6 节完整链数组})。
链中 R_m 使用各自真实原包摘要，不能用逻辑摘要替换；F 不含版本、顺序或链；K 不包含 F/T，避免循环。

下列验证专用金向量由完整 JSON 与链算法计算；原包另以手写 ZIP32 little-endian 头/目录构造，
与明确 ZipInfo 属性的 STORED ZIP 逐字节比较一致。数值不是外部批准摘要，不能授予发布资格。

| 摘要 | 小写 SHA-256 |
|---|---|
| R_B | `a518b56f9a429b401a23a85b308028c1c39e9a79c3d1852dc77d0ea13fb88bf1` |
| R_E | `b18809465d939c0ebabb17e256a837843b455eece6abc2a76b180bf04a8da40c` |
| R_C | `0477aa284478335d56ccfd004d8fc26b2cd58c5d5621cbc73fba390e135bed58` |
| L_B | `6fec35d4e41cb1a107c4c55e20411e68608cb085f628b67da8fe15fb4026e3f1` |
| L_E | `bfb7e5c57ef09f787e75c7aaee39c6c4eeafebc5595128a9ea10912134b77dcf` |
| L_C | `3faa351ae33bdab99d4c18c5a3a565790cdd2b2b18f5ee819e362988e10bae0c` |
| K | `8b3f39da62b8f893ee5832d0d4c01ab06655f945de6419fd51bd79d9e47adea3` |
| F | `47fc74fd9e1865666de088fc98aff41e6baa8a077f71a3a2f791ee1e2f07407b` |
| T | `45c3f8242fbe9c23c660c70fc6885e86606350f8b706edce804d240e0edad94a` |

本页的摘要关系验收还要求：只改排版则 R、K、T 变，L/F 不变；只改文件拆分则 L/F 不变、R/K/T 变；
移除 C:c0 的合法同值覆盖则最终值/F 不变，L_C/K/T 必变；篡改 before 或作者位置则 T 必变并且链核验失败。
即便五类摘要全部自洽，完整基础/机制/接受门仍须失败，不得用它们生成运行资格。

## 8. 完整反例与诊断合同

每行从第 3 节全部正向输入独立变换；未指明部分保持原字节。替换后按配方重建原包，
避免先触发不相关运输错误。删除/追加/替换的完整值由表指定；JSON Pointer 按 RFC 6901。
新文件必须同时登记，否则先报清单不匹配。以下错误码由本页的有限 schema 2 内核提供，
不改变现有 schema 1 reader、安装器或 HTTP 错误合同。
阶段为 PARSE、AUTHOR、GRAPH、APPLY、FINAL、QUALIFICATION；GRAPH 检查成员/依赖/顺序，
APPLY 在逐包收齐后检查引用和应用指令；出现早期错误即停止发布局部结果。
诊断恰含 `code,stage,message,locations`；位置含准确包/版本、R_m、path、pointer，未解码时改用 byte_offset。
重复项提供双方；组合清单错误无作者位置时，提供选择清单位置，不虚构包内来源。

| 编号 | 独立变换 | 期望错误/阶段及证据 |
|---|---|---|
| N01 | B 头 author_schema_version 改为 1 | S2_SCHEMA_UNSUPPORTED / AUTHOR；不落入旧 reader 当作有效新包 |
| N02 | B 头追加 unknown=1，或删除 dependencies，或 dependencies=null | S2_SHAPE / AUTHOR；各自独立，指准确字段 |
| N03 | 在 B 头 module_key 前插入 `"module_\u006bey":"validation_base",` | S2_DUPLICATE_KEY / PARSE；转义后重复，报告两处键位置 |
| N04 | B 头前置字节 EF BB BF；另例为 C0 AF | S2_JSON / PARSE 或 S2_UTF8 / PARSE；byte_offset=0 |
| N05 | E:c2/value 改为 `"false"`；另例 source_page 改为 59.0 | S2_TYPE / AUTHOR；不强转 |
| N06 | B:r0/display_name 改为空串或 `"\ud800"` | S2_TEXT / AUTHOR 或 S2_JSON / PARSE；无部分对象输出 |
| N07 | B:r0/description 清空为 `""`；另例 E:c1/field 改 description | S2_TEXT 或 S2_FIELD_NOT_CLEARABLE / AUTHOR；备注规则不能放宽说明 |
| N08 | E:c0/field 改为 `identity` 或 `language.rule_key` | S2_FIELD_UNKNOWN / AUTHOR；身份与原子字段不可局部改 |
| N09 | B 的 rules.json 根替换为 `{}`；另例漏传 members.json | S2_SHAPE / AUTHOR 或 S2_FILE_SET / AUTHOR |
| N10 | 同一份 B:r0 完整追加到 B:rules.json | S2_DUPLICATE_IDENTITY / AUTHOR；指 /0 与 /3 |
| N11 | E:r0/identity/module_key 改 validation_base | S2_ORIGINAL_OWNER / AUTHOR；新增不能冒用其他包原始身份 |
| N12 | E 新增 changes 文件 second-changes.json，内容为 `[E:c0 的完整对象]`，并在 files 追加该 path/kind | S2_DUPLICATE_FIELD / AUTHOR；changes.json#/0 与 second-changes.json#/0，即使值相同 |
| N13 | E:c0/target 改为 E:r0 的完整身份 | S2_TARGET_NOT_PREVIOUS / APPLY；本包新增对象不能再改 |
| N14 | E:c0/target/rule_key 改 language.missing | S2_TARGET_MISSING / APPLY；C 后续正确覆盖不能掩盖它 |
| N15 | C 去掉对 B 的 dependency，配对改为 `[{"validation_extension":"1"}]`，changes 仅保留原 C:c3 | S2_DIRECT_DEPENDENCY / APPLY；新 changes.json#/0 的 entry.rule 属 B，即便条目属已直接依赖的 E、E 传递依赖 B 也不够 |
| N16 | C 去掉对 E 的 dependency，配对改为 `[{"validation_base":"1"}]`，约束改为 `[]`，changes 仅保留原 C:c3 | S2_DIRECT_DEPENDENCY / APPLY；新 changes.json#/0 的条目原始归属 E 必须单独检查，rule 属已直接依赖的 B 不够 |
| N17 | C 的配对仅删除 validation_extension，其他不变 | S2_COMBINATION_SHAPE / AUTHOR；配对须完整覆盖直接依赖 |
| N18 | C 两个 supported_versions 均改 `["1","2"]`，配对改 `[{"validation_base":"1","validation_extension":"1"},{"validation_base":"2","validation_extension":"2"}]`；E release_version 改 `"2"`，选择 B1/E2/C1 | S2_COMBINATION_UNSUPPORTED / GRAPH；各单项支持，联合 (1,2) 不支持 |
| N19 | C 对 E 的 supported_versions 改 `["1","01"]`，配对不变 | S2_VERSION_SET_MISMATCH / AUTHOR；支持集合必须等于配对投影 |
| N20 | 选择 B1/E1/C1/E1；另例选择 B1/C1/E1 | S2_DUPLICATE_MODULE 或 S2_LOAD_ORDER / GRAPH；不自动去重/重排 |
| N21 | C 约束追加 `{"before":"validation_compatibility","after":"validation_base"}` | S2_ORDER_CYCLE / GRAPH；与依赖形成环 |
| N22 | E 约束追加 `{"before":"validation_base","after":"validation_extension"}`；另例 after 改 unknown | S2_DUPLICATE_CONSTRAINT 或 S2_ORDER_ENDPOINT / AUTHOR |
| N23 | B:m1/language/rule_key 改 language.missing；E changes 追加 `op="set_enabled",value=false,target=B:m1 的完整 identity` | S2_REFERENCE_MISSING / APPLY；B 的无效引用先失败，后序合法禁用不能掩盖 |
| N24 | C:c1/value 改 false（合法修改），其他不变 | S2_CANDIDATES_INSUFFICIENT / FINAL；有效 M1、M3，2<3，不能输出可用组合 |
| N25 | E:c0/value 改为整数 7，C:c0 仍给合法文本 | S2_TYPE / AUTHOR；较后合法同字段值不能掩盖前序错误 |
| N26 | E 头追加 `verification_scope:"COMPLETE"` 或 `accepted:true` | S2_SHAPE / AUTHOR；作者不能自授资格 |
| N27 | 原始正例不作修改，请求完整生产资格 | S2_BASE_INCOMPLETE / QUALIFICATION；缺完整领域/机制/接受，projection pass 不代替资格 |
| N28 | 读取超过头字节/深度/token/字符串预算任一项；例如 B 头补空格到 16385 字节 | S2_BUDGET / PARSE；在相应分配前拒绝，不能返回部分模型 |
| N29 | B:m1/language 替换为 E:r0 的完整 identity | S2_DIRECT_DEPENDENCY / APPLY；首先违反 B 无 E 直接依赖；也不得等待后包补目标或替 base 增加外依赖 |
| N30 | C:c5/value 替换为 B:r1 的完整 identity | S2_CANDIDATES_INSUFFICIENT / FINAL；M1/M2 同指 L2，distinct 候选只有 L2、L3，2<3 |

N12 的“完整对象”是第 3 节 E changes.json 的 /0 原样复制，不是身份/字段省略语法；
N13 等替换身份也取本文完整 JSON 对象。反例变换定义了全部差异，不要求另造 30 套重复包。
无资格接受、真实 ZIP 入口防护、关系表/事务/读回、真实 Host 与保存恢复均仍待实施；本页结束时的
结论仍是**局部 schema 2 语义链通过、完整生产组合拒绝**，不是端到端安装或运行验收完成。

## 9. 内部组合内核

[Schema2CompositionKernel](../../../src/main/java/com/dndtool/module/composition/Schema2CompositionKernel.java)
提供独立于现行 schema 1 和 ModuleCatalog 的纯内存入口，采用层级为**内部可测**：

- `read` 从包内路径到原始文件字节的映射读取一个包，完整预算检查后复制输入；支持本文
  语言向量的两种类型，以及同一封闭模型中的 `character.tool`、`catalog.tool_set` 和
  `members.tool`。字段与混域拒绝合同见[有限目录类型](../../rules/schema-2-rule-artifacts.md#封闭目录类型)。
  返回不可由调用方伪造的不可变作者包，仍不接受任意属性袋或脚本机制。
- `compose` 按调用方给出的作者包顺序检查依赖与联合版本，逐包验证引用并应用修改，最后
  检查完整局部投影；`composeFiles` 连接读取和组合。结果包含类型化规则、条目、完整字段链
  与验证专用规范字节及摘要，不产生安装、接受、业务状态或执行视图。
- `verifyChains` 根据原作者包重新计算完整链，核对传入的 `validation-json-1` 规范链字节，
  拒绝遗漏、同值步骤丢失、before/after、来源或作者位置被篡改；只验证链摘要自洽不足以通过。
  核验以重建结果的字节长度为界，不把作者文件的数组/token 上限施加于派生链。
- `requireProductionQualification` 始终拒绝这一有限投影，返回
  `S2_BASE_INCOMPLETE / QUALIFICATION`；没有允许不完整基础包投入运行的开关。

输入映射不接受外部自报原包摘要。内核按第 7 节固定 ZIP32 STORED 配方，从已复制的文件
构造**验证原包**并计算摘要；它不是任意 ZIP 的上传、解压或安装入口，也不声称保留一个未输入
的真实发行 ZIP。真实制品读取与生产编码使用独立的
[生产制品入口](../../rules/schema-2-rule-artifacts.md)，不把上述验证摘要写作生产摘要。

独立作者输入、最终字段、完整覆盖链和固定摘要位于
[测试资源](../../../src/test/resources/schema2-composition/)，测试不从被测实现生成这些期望，
也不在执行时读取设计文档生成期望。这组语言原始输入、31 条链、42 个步骤及全部字节／
摘要保持原样；工具扩展使用独立的
[混合目录固定资源](../../../src/test/resources/schema2-tools/README.md)，包含真实 37 项工具、
两类集合、字段与成员覆盖、启停和按完整目标身份去重后的候选检查。工具完整类型投影、
生产字节与来源链也能由存储行还原，未来声明引用、跨域引用或缺链均拒绝。
这些能力没有扩充本页语言向量的正向结论或完整基础／机制资格。
可重复的定向命令及 WAR 边界见[测试指南](../../testing.md)。
既有 schema 1 语言/工具安装、运行镜像、RELEASED v1 和 canonical/archive 协议均不由此入口改变。
