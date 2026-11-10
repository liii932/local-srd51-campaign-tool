# Schema 2 有限勇士精通重击

## 语义与应用范围

[Improved Critical](../semantics/srd-5.1/classes-fighter-monk.md#srd51-class-fighter-improved-critical)
规定勇士从战士 3 级起，武器攻击掷出 19 或 20 为重击；伤害按
[重击规则](../semantics/srd-5.1/combat.md#srd51-core-critical-hits)将攻击伤害骰加倍，
固定修正只加一次。规则来源为 SRD 第 25、94、96–97 页。语义标识不作为模组键或持久化身份。

**项目解释：** 合法武器攻击的最终选中 d20 为 19 时，精通重击直接构成命中，
不另以 AC 否决，Shield 提高 AC 后也一样。另一种读法仅让自然 20 自动命中、
19 仍先比较 AC；本项目采用前者，因为特性直接授予的是“重击”，不是“命中时追加效果”。
目标和武器资格仍先验证；自然 1 仍未命中，优势或劣势中未被选中的 19 无效。

**应用支持：** 单职业战士 3–6 级，正常选择 Champion 并实际获得本特性，
在已支持的手持近战武器普通攻击、借机攻击和 Ready 单击中使用。
本切片不开放远程、投掷、徒手、天然武器、法术攻击、多职业或 Superior Critical。
这些限制是当前消费者范围，不是 SRD 对精通重击的限制。

## 声明、组合与取得

作者使用既有 `character.feature` 格式：

| 字段 | 有限声明 |
|---|---|
| `feature_kind` / `execution_mode` | `FEATURE` / `AUTOMATIC` |
| `execution_algorithm` | `SRD_2014_CHAMPION_IMPROVED_CRITICAL_V1` |
| `owner` | 完整 `character.subclass` 身份，规则键为 `subclass.champion` |
| `level` / `source_page` | `3` / `25` |

子职业必须启用，选择等级为 3，真实所有者是启用的 `class.fighter`。
严格读取与最终组合都校验约束；修改字段不能绕过校验。组合至多启用一个此能力，
未知算法、错误模式、等级、来源、所有者或重复声明拒绝。能力不绑定特定包名。
内置 `srd51_characters@5` 使用稳定键 `feature.champion_improved_critical`，
外部包仍须遵守准确依赖、完整身份和同一支持合同。

`Schema2GrowthRules` 在正常 `CHARACTER_ADVANCED` 选择子职业后派生授予，
角色历史保存完整特性身份、owner 和取得职业等级。服务端根据冻结规则核验应得授予；
名字、客户端等级或单独的子职业名称不能代替真实取得。
`Schema2FeatureSupport` 供攻击及相邻消费者统一验证已取得特性，成长拒绝超范围组合。

## 消费者、状态与继续

`Schema2ActionFlowRules` 的共享武器攻击路径在首次判定及反应后的最终判定消费此能力。
它使用原始选骰和实际授予，不接受客户端重击阈值，也不改写原骰。
普通攻击、OA 和 Ready 保持各自行动成本；被动精通重击不额外消费动作、反应或资源。
它不增加攻击次数，也不改变属性检定、豁免、死亡豁免或伤害法术。

Shield 仍由真实命中开启候选窗口，反应只支付原有成本一次。
已取得能力的自然 19 在窗口后仍重击；其他普通命中仍按当前 AC 重验。
伤害使用既有武器 profile，骰子数加倍而属性修正一次，健康、失能、掉落及专注后继
仍由各自 owner 处理。五尺内失去意识目标的命中重击是独立规则，不借用本特性。

冻结材料归 `rules_runtime_material`，实际授予和成长事件归
`characters_runtime_character`；选骰、初步命中、最终 AC/命中/重击、伤害骰及
未决窗口归 `combat_scene` 与 `actions_frame` 投影。根历史和这些后果在同一事务提交。
无需新增用量、持久化字段、表或兼容 reader。各项成本、版本、结果和健康继续由原 owner 唯一持有。

完整文件保留准确规则、授予、已付动作/反应/法术位、原攻击骰和当前叶子。
保存、END、直接打开不补伤害、不掷骰、不重付成本；后续合法继续仅执行尚未完成的后缀。
历史验算消费已保留骰序列，坏授予、冻结规则、结果或历史不静默修复。

## 制品与验证边界

首次加入执行能力需要更新应用。此后现有能力范围内的外部内容更新可直接导入新版本，
不要求重新构建 WAR。原包字节改变必须使用新版本；准确依赖和嵌入指南变化也计入制品。
规则库更新不修改活动战役。结束后可选择新组合新建，原文件仍要求其准确原组合和本机接受。
不为旧应用版本添加兼容层，也不执行真实数据重置或迁移。

`CampaignImprovedCriticalIntegrationTest` 通过原包、本机独立接受、有序预览、新建、
正常成长与生产命令核验行为、失败边界和完整文件；隔离浏览器使用
`tools/storage/browser-improved-critical.cjs`。共同攻击、反应、成长和规则绑定回归按
[验证入口](../testing.md#improved-critical-verification)选择。
该切片不改变行为设计报告 16 条原轨迹的完成状态。
