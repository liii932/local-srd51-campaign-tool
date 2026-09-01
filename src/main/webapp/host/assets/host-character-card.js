/* Loads and mutates a verified simplified character card through host-only DTOs. */
(() => {
    "use strict";

    const loadForm = document.getElementById("character-card-load-form");
    if (!loadForm) return;

    const keyInput = document.getElementById("card-character-key");
    const resultNode = document.getElementById("character-card-result");
    const panel = document.getElementById("character-card-panel");
    const title = document.getElementById("character-card-title");
    const summary = document.getElementById("character-card-summary");
    const fieldsNode = document.getElementById("character-card-fields");
    const classesNode = document.getElementById("character-card-classes");
    const skillsNode = document.getElementById("character-card-skills");
    const savesNode = document.getElementById("character-card-saves");
    const itemsNode = document.getElementById("character-card-items");
    const moduleItemForm = document.getElementById("add-module-item-form");
    const temporaryItemForm = document.getElementById("add-temporary-item-form");
    const moduleItemSelect = document.getElementById("module-item-key");
    const encoder = new TextEncoder();
    const domain = "DND_TOOL_SE_MUTATE_CHARACTER_CARD_V1";
    const tierNames = new Map([
        ["NONE", "无"], ["HALF", "半熟练"], ["FULL", "熟练"], ["EXPERTISE", "专精"]
    ]);
    const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;
    let card = null;
    let pending = null;

    function clear(node) {
        while (node.firstChild) node.removeChild(node.firstChild);
    }

    function element(name, text) {
        const node = document.createElement(name);
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function signed(value) {
        return value >= 0 ? `+${value}` : String(value);
    }

    function digestPayload(values) {
        const fields = values.map(value => encoder.encode(value));
        const size = fields.reduce((sum, field) => sum + 4 + field.length, 0);
        const bytes = new Uint8Array(size);
        const view = new DataView(bytes.buffer);
        let offset = 0;
        for (const field of fields) {
            view.setUint32(offset, field.length, false);
            offset += 4;
            bytes.set(field, offset);
            offset += field.length;
        }
        return bytes;
    }

    async function digest(command) {
        const bytes = digestPayload([
            domain, command.characterKey, command.rowVersion, command.action,
            command.targetKey, command.value, command.description, command.quantity
        ]);
        const result = await crypto.subtle.digest("SHA-256", bytes);
        return Array.from(new Uint8Array(result), byte =>
            byte.toString(16).padStart(2, "0")).join("");
    }

    async function idempotency(command) {
        const identity = JSON.stringify(command);
        if (pending && pending.identity === identity) return pending;
        pending = {
            identity,
            requestId: crypto.randomUUID(),
            digest: await digest(command)
        };
        return pending;
    }

    async function loadCard() {
        const characterKey = keyInput.value.trim().toLowerCase();
        if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(characterKey)) {
            resultNode.textContent = "请输入规范的角色编号。";
            panel.hidden = true;
            return;
        }
        resultNode.textContent = "正在加载并校验角色卡……";
        try {
            const url = new URL(loadForm.dataset.endpoint, window.location.href);
            url.searchParams.set("characterKey", characterKey);
            const response = await fetch(url, {credentials: "same-origin"});
            const payload = await response.json();
            if (!response.ok) {
                card = null;
                panel.hidden = true;
                resultNode.textContent = `无法加载角色卡（${payload.code || "REQUEST_FAILED"}）。`;
                return;
            }
            card = payload.card;
            pending = null;
            render();
            resultNode.textContent = "角色卡已加载。";
        } catch (error) {
            resultNode.textContent = "角色卡请求未完成。";
        }
    }

    async function mutate(action, targetKey, value = "", description = "", quantity = "") {
        if (!card) return;
        const command = {
            characterKey: card.characterKey,
            rowVersion: String(card.rowVersion),
            action,
            targetKey,
            value,
            description,
            quantity
        };
        resultNode.textContent = "正在保存并写入审计事件……";
        try {
            const token = await idempotency(command);
            const response = await fetch(loadForm.dataset.endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "X-CSRF-Token": document.getElementById("csrf-token").value,
                    "X-Host-State-Epoch": document.getElementById("host-state-epoch").value,
                    "X-Object-Row-Version": command.rowVersion,
                    "X-Request-Id": token.requestId,
                    "X-Request-Digest": token.digest
                },
                body: new URLSearchParams(command),
                credentials: "same-origin"
            });
            const payload = await response.json();
            if (response.ok) {
                pending = null;
                resultNode.textContent = `保存成功（版本 ${payload.rowVersion}）。`;
                await loadCard();
                return true;
            }
            if (payload.rowVersion !== undefined && payload.rowVersion !== null) {
                card.rowVersion = payload.rowVersion;
            }
            resultNode.textContent = `未保存（${payload.code || "REQUEST_FAILED"}）。`;
            return false;
        } catch (error) {
            resultNode.textContent = "请求未完成；可原样重试以复用幂等编号。";
            return false;
        }
    }

    function saveRow(labelText, input, action, targetKey, displaySuffix = "") {
        const row = element("p");
        const label = element("label", labelText);
        label.append(" ", input);
        row.append(label);
        if (displaySuffix) row.append(` ${displaySuffix}`);
        const button = element("button", "保存");
        button.type = "button";
        button.addEventListener("click", () => mutate(action, targetKey, input.value));
        row.append(" ", button);
        return row;
    }

    function renderFields() {
        clear(fieldsNode);
        for (const field of card.fields) {
            const input = document.createElement("input");
            input.type = "number";
            input.value = String(field.value);
            if (field.minimum !== null) input.min = String(field.minimum);
            if (field.maximum !== null) input.max = String(field.maximum);
            input.required = true;
            const suffix = [
                field.modifier === null ? "" : `调整值 ${signed(field.modifier)}`,
                field.unit || ""
            ].filter(Boolean).join("；");
            fieldsNode.append(saveRow(
                field.displayName, input, "SET_FIELD", field.fieldKey, suffix));
        }
    }

    function renderClasses() {
        clear(classesNode);
        for (const characterClass of card.classes) {
            const input = document.createElement("input");
            input.type = "number";
            input.min = "0";
            input.max = "20";
            input.value = String(characterClass.level);
            input.required = true;
            classesNode.append(saveRow(
                characterClass.displayName, input,
                "SET_CLASS_LEVEL", characterClass.classKey));
        }
    }

    function proficiencySelect(current) {
        const select = document.createElement("select");
        for (const tier of card.tiers) {
            const option = element("option", tierNames.get(tier.enumCode) || tier.enumCode);
            option.value = tier.proficiencyKey;
            option.selected = tier.proficiencyKey === current;
            select.append(option);
        }
        return select;
    }

    function renderProficiencies(node, values, action) {
        clear(node);
        for (const value of values) {
            const select = proficiencySelect(value.proficiencyKey);
            node.append(saveRow(
                `${value.displayName}（${signed(value.bonus)}）`,
                select, action, value.targetKey));
        }
    }

    function renderItems() {
        clear(itemsNode);
        if (card.items.length === 0) itemsNode.append(element("p", "暂无物品。"));
        for (const item of card.items) {
            const row = element("p");
            row.append(`${item.itemName} — ${item.itemDescription}；`);
            const quantity = document.createElement("input");
            quantity.type = "number";
            quantity.min = "1";
            quantity.max = "999";
            quantity.value = String(item.quantity);
            const save = element("button", "保存数量");
            save.type = "button";
            save.addEventListener("click", () => mutate(
                "SET_ITEM_QUANTITY", item.itemToken, quantity.value));
            const status = element("button", item.itemStatus === "ACTIVE" ? "归档" : "恢复");
            status.type = "button";
            status.addEventListener("click", () => mutate(
                item.itemStatus === "ACTIVE" ? "ARCHIVE_ITEM" : "RESTORE_ITEM",
                item.itemToken));
            row.append("数量 ", quantity, " ", save, " ", status);
            itemsNode.append(row);
        }

        clear(moduleItemSelect);
        for (const template of card.itemTemplates) {
            const option = element("option", `${template.displayName} — ${template.description}`);
            option.value = template.itemKey;
            moduleItemSelect.append(option);
        }
    }

    function render() {
        title.textContent = `${card.characterName}（${card.characterType}）`;
        summary.textContent = `状态 ${card.characterStatus}；版本 ${card.rowVersion}；`
            + `总等级 ${card.totalLevel}；熟练加值 ${signed(card.proficiencyBonus)}`;
        renderFields();
        renderClasses();
        renderProficiencies(
            skillsNode, card.skills, "SET_SKILL_PROFICIENCY");
        renderProficiencies(
            savesNode, card.saves, "SET_SAVE_PROFICIENCY");
        renderItems();
        panel.hidden = false;
    }

    loadForm.addEventListener("submit", event => {
        event.preventDefault();
        loadCard();
    });

    moduleItemForm.addEventListener("submit", async event => {
        event.preventDefault();
        const quantity = document.getElementById("module-item-quantity").value;
        await mutate("ADD_MODULE_ITEM", moduleItemSelect.value, "", "", quantity);
    });

    temporaryItemForm.addEventListener("submit", async event => {
        event.preventDefault();
        const nameInput = document.getElementById("temporary-item-name");
        const descriptionInput = document.getElementById("temporary-item-description");
        const quantityInput = document.getElementById("temporary-item-quantity");
        const name = nameInput.value.replace(javaWhitespace, "").normalize("NFC");
        const description = descriptionInput.value.normalize("NFC");
        const saved = await mutate(
            "ADD_TEMPORARY_ITEM", "", name, description, quantityInput.value);
        if (saved) {
            nameInput.value = "";
            descriptionInput.value = "";
            quantityInput.value = "1";
        }
    });
})();
