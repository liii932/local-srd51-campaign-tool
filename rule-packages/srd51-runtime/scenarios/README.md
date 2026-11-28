# 验证候选规则模组

从仓库根目录运行，需 Python 3、JDK 21+（`java`、`javac`）和一份兼容的现有应用 WAR。入口直接用 WAR 的制品读取器、组合器和 Java 规则引擎，在内存中结算。整个流程无需 Maven、Tomcat、浏览器或数据库。若同时修改了应用机制，先单独验证并构建应用，再把下面的 `war` 指向更新后的 WAR。

## 验证本次角色包改动

编辑 `rule-packages/srd51-runtime/srd51-characters/` 后，从作者源构建候选 ZIP。在 Bash 中执行以下整段即可验证一击巨剑伤害。其余三个成员取自 WAR，保留组合顺序。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" srd51-characters

verify_character_package() {
  local package="$1"
  shift
  python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
    --module @srd51-runtime --module "$package" --module @srd51-magic --module @dm-runtime-house-rules \
    "$@" "$scenarios/standard.json"
}
verify_character_package "$candidates/srd51-characters.zip" --case weapon-damage
```

核对 `MODULE srd51_characters@15` 一行的外部 ZIP 绝对路径和 SHA-256。入口将实际读入的原包摘要与运行时组合中相同位置的成员摘要逐一比对。同一版本的两份候选也可区分。只传场景清单时，所有成员都取自 WAR。

多项装备声明同时改动时，在同一个命令中重复 `--case`。这些用例共用一次解包、驱动编译、引擎 JVM 启动和规则组合。每项自行准备角色、场景和骰流，按传入的次序执行。省略 `--case` 时按清单次序执行全部用例。任何失败都使整批退出非零。输出分别给出准备、组合与通过用例的耗时，可用 shell 的 `time` 测整条命令。

```bash
verify_character_package "$candidates/srd51-characters.zip" \
  --case weapon-damage --case plate-armor --case equipment-profiles
```

## 验证战士的防御与动作如潮

使用已支持这两个机制的候选 WAR。防御场景核对一级真实选择、仅持盾、穿脱板甲，以及同为 18 的攻击总值打中 AC 18、经过防御加值后对 AC 19 未命中。动作如潮场景经过真实成长，在二级执行一击加一击，在五级分别完成两组两击，并验证两次疾走让步速 30 尺的战士可走 90 尺。每次发动核对准确的资源消耗、零骰、普通动作增加和其余回合预算保持。

```bash
war=target/dnd-tool-se-0.12.0.war
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" srd51-characters srd51-magic
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module "$candidates/srd51-characters.zip" \
  --module "$candidates/srd51-magic.zip" --module @dm-runtime-house-rules \
  --case class-templates --case fighter-growth --case plate-armor \
  --case fighter-defense --case fighter-action-surge --case fighter-extra-attack \
  rule-packages/srd51-runtime/scenarios/standard.json
```

## 验证矮人石工知识

标准组合验证智力（历史）检定辨认石工来历时的双倍熟练贡献。第四成员换为检定变体后，相同题材按三倍熟练结算。体质（历史）按普通检定结算，指定石工来历题材时则在输入校验中拒绝。通过 `--module` 指定外部候选，核对输出的实际加载路径与成员身份。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" srd51-characters dm-runtime-check-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module "$candidates/srd51-characters.zip" --module @srd51-magic --module @dm-runtime-house-rules \
  --case stonecunning "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module "$candidates/srd51-characters.zip" --module @srd51-magic --module "$candidates/dm-runtime-check-variant.zip" \
  --case stonecunning-variant "$scenarios/check-variant.json"
```

## 验证矮人的重甲移动例外

用兼容 WAR 的标准组合运行 `dwarf-heavy-armor-speed`。场景创建力量均为 8 的一级矮人和人类法师，穿同种板甲后验证矮人步行 25 尺、人类步行 20 尺，以及对应的移动上限、疾走增量和起身成本。两人仍承担板甲的隐匿劣势，以及未受训穿甲造成的施法限制和力量、敏捷检定劣势。

```bash
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.12.0.war \
  --case dwarf-heavy-armor-speed rule-packages/srd51-runtime/scenarios/standard.json
```

## 验证独立修改移动参数

两条用例共用同一个兼容 WAR。第一个候选只把矮人的 `walk_speed_feet` 从 25 改为 30，验证穿板甲后仍可移动 30 尺，疾走额外获得 30 尺，起身花费 15 尺，人类对照仍为 20 尺。第二组候选交换种族策略，将共享减速改为 5 尺，并将板甲力量要求改为 14。低力量矮人降至 20 尺，低力量人类保持 30 尺，力量正好为 14 的矮人保持 25 尺。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" srd51-runtime srd51-characters
python3 - "$candidates" <<'PYTHON'
import json
import sys
from pathlib import Path
from zipfile import ZipFile

directory = Path(sys.argv[1])

def edit(package, candidate, updates):
    with ZipFile(directory / f"{package}.zip") as source, ZipFile(directory / f"{candidate}.zip", "w") as target:
        for member in source.infolist():
            data = source.read(member)
            if member.filename == "rules.json":
                rows = json.loads(data)
                for key, changes in updates.items():
                    row = next(row for row in rows if row["identity"]["rule_key"] == key)
                    for field, (before, after) in changes.items():
                        assert row[field] == before
                        row[field] = after
                data = (json.dumps(rows, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
            target.writestr(member, data)

edit("srd51-runtime", "dwarf-speed", {"movement.dwarf": {"walk_speed_feet": (25, 30)}})
edit("srd51-runtime", "armor-policy", {
    "movement.dwarf": {"heavy_armor_speed_policy": ("IGNORE", "APPLY")},
    "movement.human": {"heavy_armor_speed_policy": ("APPLY", "IGNORE")},
})
edit("srd51-characters", "armor-penalty", {
    "armor_movement.heavy_armor": {"heavy_armor_speed_penalty_feet": (10, 5)},
    "item.plate": {"strength_minimum": (15, 14)},
})
PYTHON

verify_movement_candidate() {
  python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
    --module "$1" --module "$2" --module @srd51-magic --module @dm-runtime-house-rules \
    "$scenarios/$3.json"
}
verify_movement_candidate "$candidates/dwarf-speed.zip" @srd51-characters dwarf-speed-author-edit
verify_movement_candidate "$candidates/armor-policy.zip" "$candidates/armor-penalty.zip" heavy-armor-policy-author-edit
```

这些异字节候选沿用作者验证版本号，供独立入口核验。若要在本机规则库登记候选，为变更包及其声明了准确依赖的包分配新发行版本，再按[规则包合同](../../../docs/rules/schema-2-rule-artifacts.md)构建和接受。

## 验证山丘矮人强韧

`hill-dwarf-toughness` 在标准组合中创建同属性的两名矮人战士。只有选择山丘矮人的角色在一级和每次升级多得 1 点 HP。场景在受伤后依次执行固定值和掷骰升级，检查当前和最大 HP 的增量、生命骰和属性。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --case hill-dwarf-toughness "$scenarios/standard.json"
```

以下命令使用同一 WAR，另造一份只覆盖强韧两个参数的临时候选。一级额外增加 2 点 HP，后续升级额外增加 0 点。场景核对两个覆盖的准确来源和真实成长结果。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" dm-runtime-house-rules
python3 - "$candidates" <<'PYTHON'
import json
import sys
from pathlib import Path
from zipfile import ZipFile

directory = Path(sys.argv[1])
owner = {
    "kind": "rule",
    "module_key": "srd51_characters",
    "rule_type": "character.subrace_hit_points",
    "rule_key": "subrace_hit_points.hill_dwarf",
}
changes = [
    {"op": "set_field", "target": owner, "field": "first_level_bonus", "value": 2},
    {"op": "set_field", "target": owner, "field": "per_additional_level_bonus", "value": 0},
]
with ZipFile(directory / "dm-runtime-house-rules.zip") as source, ZipFile(directory / "hill-dwarf-house.zip", "w") as target:
    for member in source.infolist():
        data = source.read(member)
        if member.filename == "author-package.json":
            manifest = json.loads(data)
            manifest["dependencies"].append({"module_key": "srd51_characters", "supported_versions": ["15"]})
            manifest["compatible_combinations"][0]["srd51_characters"] = "15"
            manifest["files"].append({"kind": "changes", "path": "changes.json"})
            data = json.dumps(manifest, ensure_ascii=False).encode("utf-8")
        target.writestr(member, data)
    target.writestr("changes.json", json.dumps(changes))
PYTHON
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/hill-dwarf-house.zip" "$scenarios/hill-dwarf-toughness-author-edit.json"
```

候选沿用作者验证版本号。登记到本机规则库时，为变更包及其声明了准确依赖的包分配新发行版本，并按[规则包合同](../../../docs/rules/schema-2-rule-artifacts.md)构建和接受。

## 核验家规覆盖

家规包占第四个位置。两种组合均验证默认属性的运动检定，标准组合使用力量，检定变体使用感知。标准组合在预检中拒绝体质（运动）。检定变体接受体质（运动），用一级战士和牧师对照熟练与未熟练时的加值、总值和成败。两种组合均核对冻结开关及其来源，检定变体另验证定额伤害。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" dm-runtime-check-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --case skill-check --case alternate-skill-disabled "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/dm-runtime-check-variant.zip" --case fixed-damage --case skill-check \
  --case alternate-skill-enabled "$scenarios/check-variant.json"
```

可用一个临时副本证明 `fixed-damage` 会抓到回归。保留上面的 `candidates`，从正确包复制所有条目，只改动伤害声明的 `modifier`。以下命令预期退出码为 1，并显示 `fixed damage rolled expected 3, got 4`。换回原 ZIP 即可验证通过。

```bash
python3 - "$candidates" <<'PYTHON'
import json
import sys
from pathlib import Path
from zipfile import ZipFile

directory = Path(sys.argv[1])
with ZipFile(directory / "dm-runtime-check-variant.zip") as good, ZipFile(directory / "wrong-damage.zip", "w") as bad:
    for entry in good.infolist():
        data = good.read(entry)
        if entry.filename == "rules.json":
            rules = json.loads(data)
            effect = next(r for r in rules if r["identity"]["rule_key"] == "vital_effect.dm_damage")
            assert effect["modifier"] == 3
            effect["modifier"] = 4
            data = json.dumps(rules).encode()
        bad.writestr(entry, data)
PYTHON
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.12.0.war \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/wrong-damage.zip" --case fixed-damage \
  rule-packages/srd51-runtime/scenarios/check-variant.json
```

## 验证被动检定

两种组合均使用一级战士的感知（察觉）检定，属性修正 +1，熟练 +2，DC 14。场景核对冻结参数来源、实际优势和劣势、被动分数和成败，确认三个 d20 结果字段均为空。

| 组合 | 基础分 | 无优势劣势 | 仅优势 | 仅劣势 | 优势劣势抵消 |
|---|---:|---:|---:|---:|---:|
| 标准 | 10 | 13 | 18 | 8 | 13 |
| 检定变体 | 12 | 15 | 18 | 12 | 15 |

主动检定对照在两种组合中都掷得 4 和 11，取高后合计 14，达到 DC。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" dm-runtime-check-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --case passive-check "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/dm-runtime-check-variant.zip" --case passive-check-variant "$scenarios/check-variant.json"
```

## 验证对抗检定

两个场景都通过 DM 领域命令为两名规则角色执行对抗，核对最小获胜分差的包来源、两方原骰、修正、总值与共同裁定。16 对 15 时标准包判领先方胜，家规保持现状。平分时都保持现状，应对方领先 2 分时都判应对方胜。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case contest-check "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case contest-check-variant "$scenarios/check-variant.json"
```

## 验证团队属性检定

标准和家规使用四名角色、同一 DC、同一组原骰，恰好两人成功。标准包要求两人，变体要求三人。第五人加入后，三人成功仍通过标准门槛，家规需四人。场景分别核对成员原骰、个人成败、冻结字段来源、团队成败和选中的叙事。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case group-check "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case group-check-variant "$scenarios/check-variant.json"
```

## 验证已知战斗戏法

`known-combat-cantrips` 使用一级法师与一级生命领域牧师各自选择的三个戏法。
法师对恰好相距 120 尺的目标施放 Fire Bolt，以自然 20 命中，掷出两个 d10，造成 5 点火焰伤害。
牧师对恰好相距 60 尺、处于四分之三掩护后且仍可见的法师施放 Sacred Flame。
敏捷豁免原骰 9 加修正 2，未达到 DC 12，随后一个 d8 造成 4 点光耀伤害。
两次施法均消耗一个动作，施法记录的环位均为 0，没有可扣减的法术位资源。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" srd51-magic
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters \
  --module "$candidates/srd51-magic.zip" --module @dm-runtime-house-rules \
  --case known-combat-cantrips "$scenarios/standard.json"
```

核对 `MODULE srd51_magic@19` 一行的实际候选 ZIP 路径和 SHA-256。

## 选择和新增场景

| 清单 | 可选择的行为 |
|---|---|
| [standard.json](standard.json)、[check-variant.json](check-variant.json) | `initial-character` 验证初始 HP、语言与工具。`skill-check` 验证默认检定属性、熟练与成败。`alternate-skill-disabled` 和 `alternate-skill-enabled` 验证开关来源及体质（运动）的预检和结算。`fixed-damage` 验证固定伤害和剩余 HP。 |
| 被动检定 | `passive-check` 和 `passive-check-variant` 验证标准声明及家规覆盖的分数、优势和劣势、无骰结果与主动检定对照。 |
| 矮人石工知识 | `stonecunning` 验证标准包的双倍熟练及普通历史对照。`stonecunning-variant` 验证五级矮人的三倍熟练贡献 +9，以及其他属性或其他技能不能使用石工来历题材。 |
| [战士防御与动作如潮](#验证战士的防御与动作如潮) | `fighter-defense`、`fighter-action-surge` |
| 标准组合的角色与装备 | `fighter-growth`、`fighter-extra-attack` 实际升级以验证授予等级。`hill-dwarf-toughness` 验证一级 HP 和后续两种升级方式。`cleric-growth-limit` 验证牧师的升级上限。`plate-armor` 使用一级战士穿甲后派生 AC。`dwarf-heavy-armor-speed` 对照穿板甲的矮人与人类实际移动。`weapon-damage` 使用真实持用、攻击与健康结算，固定普通命中及两颗伤害骰，验证应用量和目标 HP。 |
| 已知战斗戏法 | `known-combat-cantrips` 验证真实选择、Fire Bolt 最大射程与重击、Sacred Flame 忽略可见目标的部分掩护，以及两者的动作和零环记录。 |
| 标准组合的其他声明 | `class-templates`、`wizard-spellbook`、`wizard-evocation`、`burning-hands`（施法预检）、`equipment-profiles`、`monster-breath-profile`、`rule-origins`。 |

在清单中以 `bundles` 声明默认 ZIP 顺序，以 `members` 写明预期的 `module_key@release_version`，以 `cases` 列出要运行的 ID。通用用例从 `character`、`skillCheck`、`damage` 读取输入和手写期望。新增专有行为可在同目录的可信 Java 驱动中注册 ID 和生产消费者调用，再将 ID 加入清单。`driver` 指定驱动类名，标准组合的例子见 [BundledSrd51Scenarios.java](BundledSrd51Scenarios.java)。规则包只提供规则数据。

作者源和待打包 ZIP 的逐字节一致性可用 `python3 rule-packages/srd51-runtime/build-bundles.py --check` 另查。应用的机制、事务、存储和恢复使用[应用测试夹具](../../../docs/testing.md#1-聚焦测试)。离线 DRAFT 目录使用[作者向量](../../../docs/rules/language-author-package.md#独立验证)。

<a id="melee-control"></a>
## 五级战士的近战控制

用同一 WAR 运行两个场景。两组使用正常升级的五级战士，首击以原骰 10、14 得到 16 对 15。标准组合须建立擒抱，检定变体须保持原状。双方都保留第二击，再实际推倒目标。场景核对每击序号、原骰、总值、冻结门槛、结论及当前关系。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case melee-control "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case melee-control-variant "$scenarios/check-variant.json"
```

<a id="surprise-initiative"></a>
## 验证受惊、共同隐匿与分组先攻

`surprise-initiative` 用标准包中的两名战士和地精运行两场遭遇。第一场中，地精掷出 8，加隐匿 6 得 14。被动察觉 13 的战士受惊，察觉 14 的战士发现地精。受惊战士的首回合结束后，其反应在同一轮恢复。

第二场加入另一只地精和两只骷髅。两只地精共用隐匿原骰，地精组和骷髅组分别再掷先攻。固定两组先攻原骰均为 9，加上敏捷修正各得 11，同分的四名怪物按指定顺序各自入列。即使看不见地精，战士也因察觉到公开的骷髅而照常行动。

```bash
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.12.0.war \
  --case surprise-initiative rule-packages/srd51-runtime/scenarios/standard.json
```

<a id="observer-hiding"></a>
## 验证地精躲藏与观察者视角

标准组合使用地精自己的隐匿 +6。掷出 5，总值 11，被动察觉 12 的战士看见地精，被动察觉 10 的法师只知道它的原格。灵巧逃脱花掉本回合的一个附赠动作，地精仍可用普通动作从隐蔽处向法师射箭。优势骰 7、9 取 9，加 4 得 13，带半掩护的目标 AC 为 14，因此失手并暴露位置。场景还验证全掩护阻止攻击，以及法师主动搜索以 12 超过原隐匿总值 11。

```bash
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.12.0.war \
  --case observer-hiding rule-packages/srd51-runtime/scenarios/standard.json
```

<a id="ranged-weapons"></a>
## 短弓的候选、箭支和射程

在同一 WAR 上让持弓战士从 85 尺外射击骷髅。标准短弓正常射程 80 尺，玩家候选显示长射程，原骰 15、6 取 6，攻击未命中。第五个成员只把角色短弓的正常射程改为 100 尺，候选改为正常射程，原骰 15 命中并造成 5 点伤害。两组均消费一支实际箭。骷髅的短弓保有自己的 80/320 尺，两组均在长射程回击并造成 5 点伤害。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidate_modules="$(mktemp -d)"
python3 -B rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidate_modules" dm-runtime-range-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case ranged-weapons "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic --module @dm-runtime-house-rules \
  --module "$candidate_modules/dm-runtime-range-variant.zip" "$scenarios/ranged-weapons-variant.json"
```

<a id="monster-turn-movement"></a>
## 规则怪物的回合移动

标准组合的 `monster-turn-movement` 让持长剑的战士守在一只步速 40 尺的狼旁。狼尝试走 45 尺时整条路线被拒绝。合法路线先付一格的费用，在离开战士触及前暂停。固定原骰使借机攻击未命中，主持端继续同一条路线，狼走满八格并保留普通动作。再次申请走一格时余额已经用尽。

```bash
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.12.0.war \
  --case monster-turn-movement rule-packages/srd51-runtime/scenarios/standard.json
```

<a id="monster-melee"></a>
## 怪物近战的标准和作者变体

在同一份候选 WAR 上对 AC 12 的规则角色实际发动啮咬。原 d20 为 6 时，标准 +5 未命中，变体 +7 命中。另以原 d20 为 12 使两者均命中，核对两类伤害原骰及真实 HP。扩展 ZIP 只需放入私有临时目录。

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidate_modules="$(mktemp -d)"
python3 -B rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidate_modules" dm-runtime-monster-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case monster-melee "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic --module @dm-runtime-house-rules \
  --module "$candidate_modules/dm-runtime-monster-variant.zip" "$scenarios/monster-melee-variant.json"
```

<a id="wolf-bite"></a>
## 验证狼啮咬的力量豁免

在同一份候选 WAR 上运行标准组合和只改豁免 DC 的作者变体。狼在盟友靠近目标时以优势啮咬另一只狼，穿刺伤害原骰为 2 和 3，固定修正为 +2，目标 HP 从 11 降到 4。目标力量豁免掷出 10，加值 +1 后总计 11，标准 DC 11 下保持站立，作者 DC 12 下倒地。

两份组合都从目标狼的力量 12 得到 +1。自定义目标若在 `saving_throws` 显式声明力量豁免，所填整数是包含属性和熟练的总加值。冻结规则支持检查会在启战取骰前核对它与属性、挑战等级的关系。[怪物近战作者字段](../../../docs/rules/schema-2-monster-attacks.md#模组作者字段)

```bash
war=target/dnd-tool-se-0.12.0.war
scenarios=rule-packages/srd51-runtime/scenarios
candidate_modules="$(mktemp -d)"
python3 -B rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidate_modules" dm-runtime-wolf-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" --case wolf-bite "$scenarios/standard.json"
python3 -B tools/rule-packages/verify-module-scenario.py --war "$war" \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic --module @dm-runtime-house-rules \
  --module "$candidate_modules/dm-runtime-wolf-variant.zip" "$scenarios/wolf-bite-variant.json"
```
