# 角色目录与 canonical format 2

本文记录 `dnd5e2014_srd51_se` release 1 当前 DRAFT 角色目录的逻辑投影。V011 迁移及其中
已经使用的稳定身份保持不可修改，但未发布目录可通过后续前向迁移和协调实现继续演进。该发布版
仍为 `DRAFT`；目录可被只读仓储加载和离线校验，但不能绑定战役、执行规则或导入存档。

## 目录范围

V011 的种子覆盖 SRD 5.1 角色章节中的以下定义族：

| 定义类型 | 稳定键前缀 | 数量 | 范围 |
|---|---|---:|---|
| `character.race` | `race.` | 9 | 全部 SRD 种族 |
| `character.subrace` | `subrace.` | 4 | Hill Dwarf、High Elf、Lightfoot、Rock Gnome |
| `character.background` | `background.` | 1 | SRD 示例背景 Acolyte |
| `character.language` | `language.` | 18 | 标准、异域及 Druidic、Thieves Cant |
| `character.tool` | `tool.` | 37 | 工匠工具、工具包、游戏、乐器、导航和载具熟练项 |
| `character.class` | `class.` | 12 | 全部 SRD 职业 |
| `character.subclass` | `subclass.` | 12 | 每个职业的 SRD 子职 |
| `character.feature` | `feature.` | 274 | 种族、背景、职业、子职及封闭选项特性 |
| `character.resource` | `resource.` | 16 | 具有短休、长休或特殊恢复方式的职业资源 |
| `character.feat` | `feat.` | 1 | Grappler |

名称和覆盖边界以官方 [SRD 5.1](https://media.dndbeyond.com/compendium-images/srd/5.1/SRD_CC_v5.1.pdf)
为准。目录保存稳定身份、显示名、来源页、最早等级、分类、拥有关系、父级关系和当前可表达的
前置关系；规则正文的自动执行由后续领域任务实现，不能从简短目录说明推断算法。

## 类型化投影

canonical format 2 只编码四个逻辑分区：

1. `release`：module key、release version、canonical format version 和 hash algorithm；
2. `catalog_definition`：定义类型、稳定键、显示名、说明和类型内顺序；
3. `catalog_attribute`：定义身份、属性键、属性内顺序、值类型及类型化标量；
4. `catalog_relation`：来源身份、关系类型、目标身份和关系内顺序。

数据库 ID、时间戳、`release_status` 和已经保存的 `content_sha256` 不进入摘要域。V2 不读取或
编码 V1 的 22 个定义列表；两种投影不能混合。将来的目录领域只能向这三个类型化目录分区
增加经过批准的定义、属性或关系。既有迁移和已使用身份不能原地改写；DRAFT 投影若被取代，
必须通过新的前向迁移、显式格式设计及协调的代码/测试更新完成，而不是伪装成已发布兼容合同。

### 字节格式

- 文件头为 ASCII `DND_TOOL_SE_MODULE_CANONICAL`，随后是大端无符号 32 位格式版本 `2`
  和分区数 `4`；
- 名称是大端无符号 32 位字节长度加非空 ASCII；分区随后编码行数；
- 每行先编码字段数，再按文档定义的字段顺序编码字段名和值；
- 标量标签保持为 `TEXT=0x01`、`IDENTIFIER=0x02`、`INTEGER=0x03`、
  `DECIMAL=0x04`、`BOOLEAN=0x05`；整数和规范小数使用 ASCII；
- 文本先把 CRLF/CR 归一为 LF，再转为 NFC 并严格编码为 UTF-8；非法代理项拒绝；
- 行按规范化 UTF-8 的无符号字节序排序，不依赖数据库排序规则、JVM locale 或输入顺序；
- `module-canonical-v2-character.hex` 是独立生成的最小角色目录黄金向量。

## 封闭验证

目录加载后、编码和计算摘要前必须整体通过验证：

- 只接受上述定义类型、已批准属性键和关系类型；稳定键必须为小写 ASCII 并匹配所属前缀；
- 类型内 `sort_order`、同属性的 `attribute_order` 和同关系的 `relation_order` 从 1 连续；
- 每个定义有一个 `source.page`；职业有合法生命骰面数，特性有 1—20 的最早等级，资源有
  封闭恢复标识，Grappler 的力量前置值为 13；
- 亚种和子职各有一个父级；特性和资源各有一个合法拥有者；所有关系目标必须存在；
- 值类型与实际标量一致；显示文本按 Unicode code point 限长，拒绝 C0/C1 控制字符和畸形
  UTF-16；重复身份、顺序空洞、未知键或悬空引用全部失败关闭；
- 完整发布版保持 `DRAFT` 时，发布注册表仍返回 `UNPUBLISHED_RELEASE`，因此业务服务不能
  使用这些目录行建立或改变权威状态。

V011 是仓库中唯一新增的迁移。文件存在和测试通过都不证明任何数据库已执行该迁移。
