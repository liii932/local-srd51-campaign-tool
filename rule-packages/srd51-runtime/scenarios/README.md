# 验证候选规则模组

从仓库根目录运行，需 Python 3、JDK 21+（`java`、`javac`）和一份兼容的现有应用 WAR。入口直接用 WAR 的制品读取器、组合器和 Java 规则引擎，在内存中结算。整个流程无需 Maven、Tomcat、浏览器或数据库。若同时修改了应用机制，先单独验证并构建应用，再把下面的 `war` 指向更新后的 WAR。

## 验证本次角色包改动

编辑 `rule-packages/srd51-runtime/srd51-characters/` 后，从作者源构建候选 ZIP。在 Bash 中执行以下整段即可验证一击巨剑伤害。其余三个成员取自 WAR，保留组合顺序。

```bash
war=target/dnd-tool-se-0.2.1.war
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

核对 `MODULE srd51_characters@5` 一行的外部 ZIP 绝对路径和 SHA-256。入口将实际读入的原包摘要与运行时组合中相同位置的成员摘要逐一比对。同一版本的两份候选也可区分。只传场景清单时，所有成员都取自 WAR。

多项装备声明同时改动时，在同一个命令中重复 `--case`。这些用例共用一次解包、驱动编译、引擎 JVM 启动和规则组合。每项自行准备角色、场景和骰流，按传入的次序执行。省略 `--case` 时按清单次序执行全部用例。任何失败都使整批退出非零。输出分别给出准备、组合与通过用例的耗时，可用 shell 的 `time` 测整条命令。

```bash
verify_character_package "$candidates/srd51-characters.zip" \
  --case weapon-damage --case plate-armor --case equipment-profiles
```

## 核验家规覆盖

以下命令可独立复制。家规包占第四个位置。两项用例分别从新建的合法一级战士直接执行定额伤害和运动检定。

```bash
candidates="$(mktemp -d)"
python3 rule-packages/srd51-runtime/build-bundles.py --output-dir "$candidates" dm-runtime-check-variant
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.2.1.war \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/dm-runtime-check-variant.zip" --case fixed-damage --case skill-check \
  rule-packages/srd51-runtime/scenarios/check-variant.json
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
python3 -B tools/rule-packages/verify-module-scenario.py --war target/dnd-tool-se-0.2.1.war \
  --module @srd51-runtime --module @srd51-characters --module @srd51-magic \
  --module "$candidates/wrong-damage.zip" --case fixed-damage \
  rule-packages/srd51-runtime/scenarios/check-variant.json
```

## 选择和新增场景

| 清单 | 可选择的行为 |
|---|---|
| [standard.json](standard.json)、[check-variant.json](check-variant.json) | `initial-character` 验证初始 HP、语言与工具；`skill-check` 验证检定属性、熟练、成败与错误属性拒绝；`fixed-damage` 验证固定伤害和剩余 HP。 |
| 标准组合的角色与装备 | `fighter-growth`、`fighter-extra-attack` 实际升级以验证授予等级。`cleric-growth-limit` 验证牧师的升级上限。`plate-armor` 使用一级战士穿甲后派生 AC。`weapon-damage` 使用真实持用、攻击与健康结算，固定普通命中及两颗伤害骰，验证应用量和目标 HP。 |
| 标准组合的其他声明 | `class-templates`、`wizard-spellbook`、`wizard-evocation`、`burning-hands`（施法预检）、`equipment-profiles`、`monster-breath-profile`、`rule-origins`。 |

在清单中以 `bundles` 声明默认 ZIP 顺序，以 `members` 写明预期的 `module_key@release_version`，以 `cases` 列出要运行的 ID。通用用例从 `character`、`skillCheck`、`damage` 读取输入和手写期望。新增专有行为可在同目录的可信 Java 驱动中注册 ID 和生产消费者调用，再将 ID 加入清单。`driver` 指定驱动类名，标准组合的例子见 [BundledSrd51Scenarios.java](BundledSrd51Scenarios.java)。规则包只提供规则数据。

作者源和待打包 ZIP 的逐字节一致性可用 `python3 rule-packages/srd51-runtime/build-bundles.py --check` 另查。应用的机制、事务、存储和恢复使用[应用测试夹具](../../../docs/testing.md#1-聚焦测试)。离线 DRAFT 目录使用[作者向量](../../../docs/rules/language-author-package.md#独立验证)。
