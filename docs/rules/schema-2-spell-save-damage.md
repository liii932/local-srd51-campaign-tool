# Schema 2 法术来源的范围豁免伤害

## 采用范围与准确资格

`SRD_2014_BURNING_HANDS_V1` 是独立于
`SRD_2014_BURNING_HANDS_METADATA_V1` 的真实消费者。两种作者算法均保留；旧元数据包不会
自动取得执行资格。新算法的固定来源页为本地 SRD [p.123](../references/srd-5.1/pages/page-123.md)，
作者/准确来源及镜像沿用[施法作者合同](schema-2-spell-authoring.md)，真实获得与准备沿用
[有限施法合同](schema-2-spells.md)。它仅采用本文列出的有限分支，不代表完整范围法术或完整 SRD 覆盖。

施法者须实际取得单职业 Wizard 1—5 级 profile，真实法术书已学且已准备本准确法术，并已
完成当前等级的学习。profile 的能力必须为 Intelligence；DC 保存真实 INT 调整值和 PB，
结果为 `8 + INT 调整值 + PB`。实际施放消费一次 action 和所选真实 `character.resource`
法术位。只接纳当前等级已取得的一至三环位：分别为 3d6、4d6、5d6；其他位阶明确拒绝。
不能从元数据名单、客户端 DC、等级或骰式取得资格。

本消费者限定正常发声、双手空闲、无材料的活动战斗场景。双手限制是依据 p.123 双手动作
描述选择的有限接纳边界，不修改其他 S 法术的一只自由手规则。已获得 Sculpt Spells 的角色
拒绝此 evocation 分支，直到真实豁免目标选择消费者接入；不会把 BLOCKED 特性视为零贡献。
独立内容包可以定义没有该获得事实的有限 profile，仍须通过正常来源、成长、学习和准备链。

当前不接纳探索施放或 Ready 持有此范围法术；Ready 在准备及付款前返回
`S2_SPELL_READY_AREA_UNSUPPORTED`。其他角色在法术效果全部完成后响应
`CAST_COMPLETED` 的 Ready 沿既有唯一动作流执行。

## 完整目标、对象与反应边界

`CastInput` 使用空的 creature target 列表和唯一 `coneDirection`（0—7）表达方向。
不能提交自选目标子集。方向依次为东、东北、北、西北、西、西南、南、东南；使用
[有限怪物合同](schema-2-monster-breath.md)相同的正常可见平面方格、Small/Medium 点格与
`S2_POINT_CONE_CHEBYSHEV_V1` 完整空间政策。十五尺锥形排除原点，完整枚举已登记的角色
及怪物、位置、纳入原因和确定顺序；敌友、免疫和零 HP 均不允许删去 candidate。
方向在声明时固定，位置和实际目标在真实法术完成、开始共享伤害时采样。

SRD 还要求点燃区域内未穿戴或携带的可燃物。当前场景没有通用对象/燃烧 owner；范围内
存在任何登记地面物品时，在付款及 RNG 前以 `S2_SPELL_OBJECT_EFFECT_UNSUPPORTED` 拒绝。
支持场景沿有限场景合同，不包含未登记环境物体。不能把未知物品猜成不可燃，不能把点燃
交给通用 HP 输入。存在任何活动抗魔环境区域的场景同样在接受前拒绝
`S2_SPELL_AREA_ANTIMAGIC_UNSUPPORTED`，不让法术伤害穿过尚未采用的区域魔法语义。

真实 Counterspell 在原 `CAST_BEFORE_COMPLETION` 窗口执行；付款只发生一次，被反制不
退款、不创建伤害批次、不掷伤害或豁免。嵌套 Counterspell 继续引用准确原 cast 和窗口。
为保证原声明不会在付款后才遇到缺失的对象 owner，存在可用 Counterspell 时，声明先
以所有可能嵌套反制者的 `CAST_COMPLETED` 为种子，遍历已武装 Ready 的可达完成事件。
Ready attack 继续产生攻击者的 `ATTACK_COMPLETED`；受击者实际已准备 Shield 且有可用
一环位时，其 `CAST_COMPLETED` 也在原法术效果之前可达。原施法者只有同时具备嵌套
Counterspell 或上述 Shield 资格时，其完成事件才进入此提前发生的集合；不能把原法术
效果完成后的合法 Ready move 一并拒绝。当前 held Ready spell 释放不另产生完成触发。
遍历以事件种类和角色身份去重，有限闭合所有可达后继；遇到移动，或可能打倒非原点
持物目标的攻击，就在首次 RNG/付款前返回 `S2_SPELL_NESTED_OBJECT_EFFECT_UNSUPPORTED`。
原点施法者被真实嵌套 Ready 攻击打倒或杀死时，父施法在继续时保存 `INTERRUPTED` 与
`CASTER_INCAPACITATED`，保留已付 action/slot；不会掷伤害、创建 batch 或再次付款。
该终态不用于掩盖缺失的点燃消费者。

## 同一共享伤害与后继 owner

`SaveDamageBatch` 的来源为互斥的真实 monster action 或 `SpellDamageSource`。法术分支保存
原 cast frame、完整 spell identity、所选 slot、INT/PB 贡献；action 必须为空。不会伪造
`adventure.monster/actions` 身份或把施法当作怪物充能动作。

完整请求、所有版本、目标、健康、DEX/CON、已支持防御、强制后继容量及对象边界均先于
成本或 RNG 检查。声明接受后先处理真实反制；成功完成且目标非空才产生一次共享 d6 序列。
每个目标保存完整 DEX 豁免；免疫仍执行豁免。成功先向下取半，再用实际火抗性/免疫/易伤。
角色防御当前仍为空；怪物使用原 stat block，未接入 Protection from Energy 或其他角色防御。

原始共享骰之后的 `SAVE_DAMAGE_ROLLED` 是准确待继续叶子，属于原 cast 的 child。
`CONTINUE_FLOW` 只消费未完成后缀：共同 DEX cut → 全部 packet 与健康计划 → 强制专注结束
及贡献协调 → 共同 CON cut → 失败专注批量结束 → 原 cast 完成及真实 Ready。
共享伤害、临时 HP、零 HP、死亡/掉物和专注复用同一健康与持续效果 owner。
空目标仍消费 action/slot，但没有骰、save 或 packet。

## 类型化保存、恢复与验证

运行迁移 V042 为 cast command/attempt 增加方向，并建立
`s2_combat_spell_damage_source`。共享 batch 的 action 身份允许完整为空；精确 source 明细
只能指向同一 phase 的真实 cast/child frame。既有 `s2_combat_breath_*` 表名保留为物理历史，
其完整 candidate、骰、check、packet、防御和专注明细由共享消费者复用。RULES V012 仅新增
准确算法接纳，原算法和迁移不改写。

读取和恢复检查完整原 source、cast/window/frame、INT/PB、目标、原骰、健康/角色因果
历史、强制余集和接受结果；未知、缺行、多行、孤儿或字段改变均拒绝。声明窗口、原始骰
暂停、伤害后 Ready 以及施法者死亡后的父继续点均可直接恢复，不重施法、重选资源、
重掷、隐式 PASS、重伤害或退款。根、子事件、版本和接受结果共同提交或回滚；调用方拥有事务。

`Schema2SpellSaveDamageRuntimeServiceTest` 的手算向量使用真实 Wizard A（INT10、PB2、DC10）、
生命领域 B（HP9、temp5、DEX/CON +1）、Hell Hound 和 Mummy。B 实际施放 Bless 到 A/B，
再以 Ready move 等待 A 完成施法；A 的东向锥形包含 B/Hell Hound/Mummy。
共享三骰 3/4/4 得 11；B 原 DEX7、Bless1 得 9，失败；Hell Hound 的 DEX12/+1
见本地 [p.322](../references/srd-5.1/pages/page-322.md)，无 DEX 豁免熟练，原1加1得2，失败但免疫；
Mummy 原2减1得1，失败且易伤。三包实际伤害为 11/0/22，HP 为 3/45/36，B temp 归零。
B CON 原8加1和 Bless1 恰成功；此后才出现 A 的 `CAST_COMPLETED` Ready 窗口。
升环、奇数半伤、零 HP/强制结束、反制、失能、旧版本、幂等、坏物理行、事务故障和 UNKNOWN
另有聚焦向量。测试实现存在不等于本轮已执行或真实 MySQL 已验收。

[专用授权](../../database/grants/schema2-spell-save-damage.sql)与
[只读验证](../../database/verify/schema2-spell-save-damage.sql)分别使用。SQL 文件存在不证明
迁移或 grant 已执行。该采用保持 INTERNAL，完整 Host/base/archive format 2、安装接受、
真实 MySQL、部署与跨域发布门仍须各自验收；旧 RELEASED 默认和 v1 合同不变。
