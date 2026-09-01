/* Executes a frozen host-command check without accepting rolls, algorithms or derived modifiers. */
(() => {
    "use strict";
    const form = document.getElementById("host-event-form");
    if (!form) return;
    const byId = id => document.getElementById(id);
    const executorInput = byId("host-executor");
    const executorVersionInput = byId("host-executor-version");
    const checkTypeInput = byId("host-check-type");
    const sourceLabel = byId("host-source-label");
    const sourceInput = byId("host-source");
    const manualFields = byId("host-manual-fields");
    const manualNameInput = byId("host-manual-name");
    const manualModifierInput = byId("host-manual-modifier");
    const rollModeInput = byId("host-roll-mode");
    const dcInput = byId("host-dc");
    const targetsInput = byId("host-targets");
    const resultNode = byId("host-event-result");
    const button = form.querySelector('button[type="submit"]');
    const encoder = new TextEncoder();
    let pending = null;

    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    const versionPattern = /^(0|[1-9][0-9]*)$/;
    const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;
    const checkKeys = Object.freeze({
        ABILITY: "check.ability", SKILL: "check.skill",
        SAVING_THROW: "check.saving_throw", MANUAL: "check.manual"
    });
    const sourceOptions = Object.freeze({
        ABILITY: [["ability.strength", "力量"], ["ability.dexterity", "敏捷"],
            ["ability.constitution", "体质"], ["ability.intelligence", "智力"],
            ["ability.wisdom", "感知"], ["ability.charisma", "魅力"]],
        SKILL: [["skill.acrobatics", "体操"], ["skill.animal_handling", "驯兽"],
            ["skill.arcana", "奥秘"], ["skill.athletics", "运动"],
            ["skill.deception", "欺瞒"], ["skill.history", "历史"],
            ["skill.insight", "洞悉"], ["skill.intimidation", "威吓"],
            ["skill.investigation", "调查"], ["skill.medicine", "医药"],
            ["skill.nature", "自然"], ["skill.perception", "察觉"],
            ["skill.performance", "表演"], ["skill.persuasion", "游说"],
            ["skill.religion", "宗教"], ["skill.sleight_of_hand", "巧手"],
            ["skill.stealth", "隐匿"], ["skill.survival", "求生"]],
        SAVING_THROW: [["save.strength", "力量"], ["save.dexterity", "敏捷"],
            ["save.constitution", "体质"], ["save.intelligence", "智力"],
            ["save.wisdom", "感知"], ["save.charisma", "魅力"]]
    });
    const nodes = [["node.street", "街道"], ["node.entry", "酒馆入口"],
        ["node.common_room", "大厅"], ["node.cellar", "地窖"]];
    const items = [["item.backpack", "背包"], ["item.rope_hempen_50ft", "50 英尺麻绳"],
        ["item.torch", "火把"]];
    const effectFields = Object.freeze({
        "effect.adjust_current_hp": [["target", "目标角色键", "REFERENCE"],
            ["amount", "HP 调整量", "INTEGER"]],
        "effect.grant_module_item": [["target", "目标角色键", "REFERENCE"],
            ["item", "内置物品", "REFERENCE", items], ["quantity", "数量", "INTEGER"]],
        "effect.grant_temporary_item": [["target", "目标角色键", "REFERENCE"],
            ["name", "临时物品名称", "TEXT"], ["description", "说明", "TEXT"],
            ["quantity", "数量", "INTEGER"]],
        "effect.set_entity_position": [["target", "目标角色键", "REFERENCE"],
            ["node", "目标节点", "REFERENCE", nodes]],
        "effect.append_event_message": [["message", "消息", "TEXT"]]
    });
    const canonicalKeys = Object.freeze({
        target: "target_character", amount: "amount", item: "item_template",
        quantity: "quantity", name: "name", description: "description",
        node: "node", message: "message"
    });

    function digestPayload(values) {
        const fields = values.map(value => encoder.encode(String(value)));
        const bytes = new Uint8Array(fields.reduce((sum, field) => sum + 4 + field.length, 0));
        const view = new DataView(bytes.buffer);
        let offset = 0;
        for (const field of fields) {
            view.setUint32(offset, field.length, false); offset += 4;
            bytes.set(field, offset); offset += field.length;
        }
        return bytes;
    }

    async function sha256(fields) {
        const result = await crypto.subtle.digest("SHA-256", digestPayload(fields));
        return Array.from(new Uint8Array(result), byte =>
            byte.toString(16).padStart(2, "0")).join("");
    }

    function setManualMode(manual) {
        sourceLabel.hidden = manual; sourceInput.hidden = manual; sourceInput.disabled = manual;
        manualFields.hidden = !manual; manualNameInput.required = manual;
        manualModifierInput.required = manual;
    }

    function populateSources() {
        const type = checkTypeInput.value;
        sourceInput.replaceChildren(); setManualMode(type === "MANUAL");
        for (const [value, label] of sourceOptions[type] || []) {
            const option = document.createElement("option");
            option.value = value; option.textContent = label; sourceInput.append(option);
        }
    }

    function renderEffectInputs(branch) {
        const container = byId(`host-${branch}-effect-inputs`);
        container.replaceChildren();
        for (const input of form.querySelectorAll(`input[name="${branch}Effects"]:checked`)) {
            const group = document.createElement("fieldset");
            const legend = document.createElement("legend");
            legend.textContent = input.parentElement.textContent.trim(); group.append(legend);
            for (const [key, labelText, type, choices] of effectFields[input.value]) {
                const label = document.createElement("label"); label.textContent = labelText;
                let control;
                if (choices) {
                    control = document.createElement("select");
                    for (const [value, text] of choices) {
                        const option = document.createElement("option");
                        option.value = value; option.textContent = text; control.append(option);
                    }
                } else {
                    control = document.createElement("input");
                    control.type = type === "INTEGER" ? "number" : "text";
                }
                control.name = `${branch}.${input.value.slice(7)}.${key}`;
                control.required = key !== "description";
                label.append(control); group.append(label);
            }
            container.append(group);
        }
    }

    function targetVersions() {
        const targets = targetsInput.value.split(/\r?\n/u).map(line => line.trim())
                .filter(Boolean).map(line => {
                    const parts = line.split(",");
                    if (parts.length !== 2) throw new Error("INVALID_TARGETS");
                    return {characterKey: parts[0].trim().toLowerCase(),
                        expectedRowVersion: parts[1].trim()};
                });
        if (targets.some(value => !uuidPattern.test(value.characterKey)
                || !versionPattern.test(value.expectedRowVersion))
                || new Set(targets.map(value => value.characterKey)).size !== targets.length) {
            throw new Error("INVALID_TARGETS");
        }
        return targets.sort((a, b) => a.characterKey.localeCompare(b.characterKey)
                || Number(a.expectedRowVersion) - Number(b.expectedRowVersion));
    }

    function effectValues(branch) {
        return Array.from(form.querySelectorAll(`input[name="${branch}Effects"]:checked`), input => {
            const prefix = `${branch}.${input.value.slice(7)}.`;
            const parameters = [];
            for (const [key, ignored, type] of effectFields[input.value]) {
                const control = form.elements.namedItem(prefix + key);
                let value = control.value;
                if (type === "TEXT") value = value.normalize("NFC");
                if (type === "INTEGER") {
                    if (!/^-?(0|[1-9][0-9]*)$/.test(value)) throw new Error("INVALID_INTEGER");
                    value = String(Number(value));
                }
                parameters.push([canonicalKeys[key], type, value]);
            }
            if (input.value === "effect.set_entity_position") {
                parameters.splice(1, 0, ["map", "REFERENCE", "map.tavern_cellar"]);
            }
            return {effectKey: input.value, parameters};
        });
    }

    async function commandDigest(command) {
        const payload = ["DND_TOOL_SE_STAGE3_CHECK_PAYLOAD_V1", command.checkKey,
            command.rollModeKey, command.source, command.manualName, command.manualModifier,
            command.dc];
        for (const [branch, effects] of [["SUCCESS", command.success], ["FAILURE", command.failure]]) {
            payload.push(branch, String(effects.length));
            for (const effect of effects) {
                payload.push(effect.effectKey, String(effect.parameters.length));
                for (const parameter of effect.parameters) payload.push(...parameter);
            }
        }
        const payloadHash = await sha256(payload);
        const requestFields = ["DND_TOOL_SE_EXECUTE_STAGE3_CHECK_V1", payloadHash,
            command.executor, command.executorVersion, String(command.targets.length)];
        for (const target of command.targets) {
            requestFields.push(target.characterKey, target.expectedRowVersion);
        }
        return sha256(requestFields);
    }

    function reset() { pending = null; resultNode.textContent = ""; }
    form.addEventListener("input", reset);
    form.addEventListener("change", event => {
        reset();
        if (event.target === checkTypeInput) populateSources();
        if (event.target.name === "successEffects") renderEffectInputs("success");
        if (event.target.name === "failureEffects") renderEffectInputs("failure");
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const manual = checkTypeInput.value === "MANUAL";
            const command = {
                checkKey: checkKeys[checkTypeInput.value], rollModeKey: rollModeInput.value,
                source: manual ? "" : sourceInput.value,
                manualName: manual
                    ? manualNameInput.value.replace(javaWhitespace, "").normalize("NFC") : "",
                manualModifier: manual ? manualModifierInput.value : "", dc: dcInput.value,
                executor: executorInput.value.trim().toLowerCase(),
                executorVersion: executorVersionInput.value, targets: targetVersions(),
                success: effectValues("success"), failure: effectValues("failure")
            };
            if (!command.checkKey || !uuidPattern.test(command.executor)
                    || !versionPattern.test(command.executorVersion)
                    || !/^(?:[0-9]|[1-5][0-9]|60)$/.test(command.dc)) {
                throw new Error("INVALID_REQUEST");
            }
            const identity = JSON.stringify(command);
            if (!pending || pending.identity !== identity) {
                pending = {identity, requestId: crypto.randomUUID(),
                    digest: await commandDigest(command)};
            }
            button.disabled = true; resultNode.textContent = "正在掷骰并原子提交事件……";
            const response = await fetch(form.dataset.endpoint, {method: "POST",
                body: new URLSearchParams(new FormData(form)), credentials: "same-origin",
                headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "X-CSRF-Token": byId("csrf-token").value,
                    "X-Host-State-Epoch": byId("host-state-epoch").value,
                    "X-Object-Row-Version": command.executorVersion,
                    "X-Request-Id": pending.requestId, "X-Request-Digest": pending.digest}});
            const payload = await response.json();
            if (response.ok) {
                resultNode.textContent = `事件 #${payload.eventSequence}：${payload.selectedValue}`
                    + ` + ${payload.modifierValue} = ${payload.totalValue}，${payload.outcome}。`
                    + "角色版本可能已推进，请刷新总览后继续。";
                pending = null;
            } else {
                resultNode.textContent = `检定未提交（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            resultNode.textContent = "请检查执行者、版本、检定参数、效果参数和全部可能目标。";
        } finally {
            button.disabled = false;
        }
    });
    populateSources(); renderEffectInputs("success"); renderEffectInputs("failure");
})();
