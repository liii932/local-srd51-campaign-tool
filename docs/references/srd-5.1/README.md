# SRD 5.1 分页参考

本目录是[本地官方英文 PDF](../srd-5.1.pdf) 的检索用派生文本，按原文 403 页拆分。
每页保留页码并链接回 PDF；第 1 页是许可说明，第 2 页是原文的空白页说明。
文本不翻译、不摘要，不替代项目的规则实现合同或可执行作者 JSON。

## 主题入口

下表按原文起始页定位常用主题；具体条目也可通过正文搜索或机器索引查找。

| 主题 | 原文入口 |
|---|---|
| 种族 | [Races · 3](pages/page-003.md) |
| 职业 | [Barbarian 起 · 8](pages/page-008.md) |
| 升级与多职业 | [Beyond 1st Level / Multiclassing · 56](pages/page-056.md) |
| 角色细节与背景 | [Alignment · 58](pages/page-058.md)、[Backgrounds · 60](pages/page-060.md) |
| 装备与专长 | [Equipment · 62](pages/page-062.md)、[Feats · 75](pages/page-075.md) |
| 属性、技能、检定与豁免 | [Using Ability Scores · 76](pages/page-076.md) |
| 冒险、移动、环境与休息 | [Time / Movement · 84](pages/page-084.md)、[Resting · 87](pages/page-087.md) |
| 战斗 | [The Order of Combat · 90](pages/page-090.md) |
| 施法规则、法术列表与条目 | [Spellcasting · 100](pages/page-100.md)、[Spell Lists · 105](pages/page-105.md)、[Spell Descriptions · 114](pages/page-114.md) |
| 陷阱、疾病、疯狂、物体与毒药 | [Traps · 195](pages/page-195.md)、[Diseases · 199](pages/page-199.md)、[Madness · 201](pages/page-201.md)、[Objects · 203](pages/page-203.md)、[Poisons · 204](pages/page-204.md) |
| 魔法物品 | [Magic Items · 206](pages/page-206.md)、[Sentient Magic Items · 251](pages/page-251.md)、[Artifacts · 252](pages/page-252.md) |
| 怪物规则与属性块 | [Monsters · 254](pages/page-254.md) |
| 状态 | [Conditions · 358](pages/page-358.md) |
| 神祇与位面 | [Pantheons · 360](pages/page-360.md)、[The Planes of Existence · 363](pages/page-363.md) |
| 其他生物与 NPC | [Miscellaneous Creatures · 366](pages/page-366.md)、[Nonplayer Characters · 395](pages/page-395.md) |

## 按需读取

优先在本地按主题定位或搜索，再读取命中页。不要默认把全部原文加载进上下文。
日常查询无需联网；本地文件重建和官方回源条件见[参考说明](../README.md)。
文件名 `pages/page-056.md` 对应 PDF 第 56 页。

在仓库根目录执行：

```bash
rg --no-config --no-pre -n -i -- 'multiclassing|concentration|short rest' docs/references/srd-5.1/pages
cat docs/references/srd-5.1/pages/page-056.md
```

原文的段落和表格可能跨页；命中页从半句话或表格续行开始时，连同前后页读取。
引用使用原文页码和章节名。表格数值、脚注或排版含义需要核对时，使用页内的 PDF 链接。

[index.json](index.json) 提供页码、文件路径和从字体识别的标题线索；标题列表不是完整的
规则目录。查找法术、物品或怪物时，仍可搜索分页正文。

## 文本处理与重建

正文按版面区域输出：双栏区域先左后右，全宽表格在相应位置单独保留；页脚另列。
等宽代码块保留原文物理行、缩进及表格行列关联。只清理 PDF 字符映射带入的空白和
重复连字符等编码伪影，不重写规则句子，不合并跨页内容。

使用安装了 `pdfminer.six` 的 Python 环境，在仓库根目录运行：

```bash
python tools/reference-docs/extract-srd-text.py
python tools/reference-docs/extract-srd-text.py --check
```

提取器核对 403 页覆盖、每个非空源字形恰好输出一次、规范化非空字符计数及控制字符。
`--check` 从 PDF 重新提取并与已保存的分页文本、索引比较，不修改文件。
这些检查验证文字覆盖和可重复性；视觉行列关系仍需对照 PDF 抽查。

## 来源与许可

来源是 Wizards of the Coast 的
[System Reference Document 5.1](https://media.dndbeyond.com/compendium-images/srd/5.1/SRD_CC_v5.1.pdf)，
对应 D&D 5e 2014。分页和排版整理属于格式改编；原始 PDF 保持不变。
原文与派生文本按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 提供，
署名与完整许可引用见仓库 [NOTICE](../../../NOTICE)。
