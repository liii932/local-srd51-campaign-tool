# 工具熟练目录分区

`character.tool` 为 DRAFT `dnd5e2014_srd51_se` / 字符串 `"1"` 的 37 项工具熟练引用目录。
它与 18 项语言共享作者包和 DRAFT 身份。这里的完整集合只指本目录，
不包含装备价格、重量、制作、使用算法或角色选择规则，也不是全部角色领域或 COMPLETE。
本目录沿用完整规则 DRAFT 的分区合同。旧 MySQL 批准摘要与归档映射见[发布身份合同](module-release-identity.md)。

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
作者文本可正规化到 NFC；类型化模型要求已规范化的值。

`ToolPartition` 是不可变类型模型，产生 37 条 `character.tool` 定义及 74 条窄类型属性。
每项的 `catalog.category` 是 IDENTIFIER，`source.page` 是 INTEGER；没有关系。
`CharacterCatalogPartition` 汇合语言与工具，通过同一 `ModuleCatalog` 和 canonical-v2
encoder 产生 55 条定义、110 条属性、零关系。数组位置不替代显式排序，规范顺序按稳定键。

## 核验与采用门

正式作者内容用[独立作者核对](language-author-package.md#独立验证)。应用 reader 使用固定夹具。
独立手写 `ToolCatalogOracle` 的六字段矩阵和语言矩阵分别检查字段；
`tools/rule-packages/catalog-vector.py` 独立编码并核对规范字节。
`ToolAuthorPartitionTest` 核对闭合字段、稳定排序、未知值拒绝和固定 canonical-v2 字节。

本分区仅用于 DRAFT 目录校验，不授予游戏执行或存档资格。可玩的工具熟练由战役冻结的
schema 2 规则提供，见[初始授予](schema-2-proficiency-grants.md)和[领域接合表](../design/v2/domain-integration.md)。
先前的双库安装和镜像证据随[原版合同](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/tool-catalog-partition.md)保管。
