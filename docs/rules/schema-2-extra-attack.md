# Schema 2 战士额外攻击

SRD 5.1 的战士从职业 5 级起，在自己回合执行 Attack 动作时可攻击两次。SRD 在职业
11、20 级还会增加次数。当前应用支持单职业战士 1–6 级，其中 5–6 级的额外攻击接入
现有近战武器流程。SRD 的更高等级、其他职业的额外攻击和多职业规则属于独立范围。
[SRD 第 25 页](../references/srd-5.1/pages/page-025.md) ·
[战士语义](../semantics/srd-5.1/classes-fighter-monk.md#srd51-class-fighter-extra-attack)

## 声明、组合与授予

内置角色包 `srd51_characters@3` 声明
`(srd51_characters, character.feature, feature.fighter_extra_attack)`。
它使用现有特性类型和字段链，必需 `feature_kind=FEATURE`、`execution_mode=AUTOMATIC`、
`execution_algorithm=SRD_2014_FIGHTER_EXTRA_ATTACK_V1`、`source_page=25`、`level=5`，
以及真实 `character.class` 的完整 Fighter owner 身份。该算法固定把一个 Attack 动作
的额度设为 2。描述文本只供阅读，次数无作者或客户端参数。

生产组合校验原始声明与最终行。作者沿既有字段操作覆盖，最终形状仍须满足上述约束。
启用的额外攻击需要启用的职业来源，同一组合至多启用一条。应用支持门要求它指向实际
战士建卡模板的完整职业身份，并具有可达职业 5 级的 `CHARACTER_ADVANCEMENT` 成长。
重复机制返回 `S2_EXTRA_ATTACK_AMBIGUOUS`，错误声明或引用按既有类型和引用错误拒绝。
允许组合省略或禁用该条规则。规则目录仍可展示被禁用的条目，正常 Attack 保有一次额度。

规则库按准确版本、原始 ZIP 和模组逻辑摘要导入及接受。组合配置、最终规则和完整字段
来源链一同冻结在战役材料中。正常 `CHARACTER_ADVANCED` 从战士职业 4 级升至 5 级时，
成长执行保存 `FeatureState` 的完整特性身份、完整职业 owner 和取得职业等级 5。
升至 6 级保留同一授予。成长预检拒绝带有该能力的多职业或超出 6 级的状态
（`S2_GROWTH_EXTRA_ATTACK_SCOPE`），在抽骰或写入历史前完成。
[角色成长](schema-2-character-statistics.md) · [本机规则库](../design/v2/local-rule-library.md)

## 执行与进度

`Schema2FeatureSupport` 验证角色已取得的事实与启用的冻结规则相符。取得错误身份、
所有者或等级、重复事实、未知特性或缺少执行归属时，使用相应消费者的稳定错误拒绝。
成长校验同时重算完整应得特性列表，拒绝缺失的授予。装备、检定、生命与施法预检可识别
此已取得特性。它只向普通 Attack 动作提供额度。

`BEGIN_ATTACK` 在当前角色自己的合法回合消费一个动作，由 `Schema2CombatRules`
从经验证的特性和冻结规则确定 `AttackSequence.limit`。后续每次 `ATTACK` 使用既有
单击输入、武器和目标校验及反应窗口；第二击可以独立选择合法目标。移动沿用当前回合的
移动额度，允许发生在两击之间。次数用完、主动放弃或回合结束时，既有战斗序列关闭。
失去行动资格时拒绝后续攻击。Ready 和借机攻击分别沿用各自的单击结算。
[战斗合同](schema-2-combat.md)

## 存储与恢复

SQLite 复用 `rules-runtime` 的 `rules_runtime_material` 保存完整声明及来源链，
`characters-runtime` 的 `characters_runtime_character` 保存授予和角色事件，
`combat` 的 `combat_scene` 保存序列身份、上限、已执行、已放弃及关闭状态。
每次攻击、动作流及未决反应使用现有战斗历史与 `actions_frame` 投影，归入
`history_runtime_root` 的业务根。额度由战斗序列拥有，无独立职业资源或存储分片。
原 schema 2 类型化来源、镜像及战斗事实使用既有特性和序列字段。

完整文件、活动 SQLite 与同版残留保留冻结材料、角色授予、序列进度、已用骰和未决动作。
恢复按历史版本的角色授予和对应冻结规则验证起始额度，核对完整根/子历史后装载状态。
已完成第一击的序列可在合法的后续命令中使用第二击。历史核验复用已保留骰点，运行随机
源只由新的合法攻击消费。打开文件仍要求本机准确原包及接受。
[存储与恢复](../storage-vitality-loop.md)

## 定向验证

`BundledCampaignRulesTest` 核对原包与冻结读回、正常 5/6 级授予及对装备、检定和生命的
中性。 `CampaignRuleSupportTest` 覆盖错误声明、错误覆盖、重复或禁用的特性及混合成长
在抽骰前拒绝。 `CampaignExtraAttackIntegrationTest` 通过规则库和真实战役写路径核对
攻击额度、移动与续击、拒绝和完整恢复。
