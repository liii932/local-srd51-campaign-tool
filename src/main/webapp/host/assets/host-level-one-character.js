/* Server-authoritative preview/confirm client for canonical-v2 level-one PCs. */
(() => {
    "use strict";
    const form = document.getElementById("level-one-character-form");
    if (!form) return;
    const result = document.getElementById("level-one-result");
    const confirm = document.getElementById("level-one-confirm");
    const encoder = new TextEncoder();
    let preview = null;

    function normalizedName() {
        return form.elements.characterName.value.trim().normalize("NFC");
    }

    function parameters(action) {
        const values = new URLSearchParams();
        values.set("action", action);
        for (const name of ["campaignKey", "raceKey", "subraceKey", "backgroundKey", "classKey",
                "classSubclassKey", "ability.strength", "ability.dexterity", "ability.constitution",
                "ability.intelligence", "ability.wisdom", "ability.charisma"]) {
            values.set(name, form.elements[name].value.trim());
        }
        values.set("characterName", normalizedName());
        for (const name of ["abilityBonusChoices", "skillChoices", "languageChoices",
                "toolChoices", "startingOptionChoices"]) {
            form.elements[name].value.split(",").map(value => value.trim()).filter(Boolean)
                    .forEach(value => values.append(name, value));
        }
        return values;
    }

    async function sha256(text) {
        const bytes = await crypto.subtle.digest("SHA-256", encoder.encode(text));
        return Array.from(new Uint8Array(bytes), value => value.toString(16).padStart(2, "0")).join("");
    }

    function framed(value) {
        const size = encoder.encode(value).length;
        return `${size}:${value}\n`;
    }

    async function confirmationDigest(values) {
        return sha256("DND_TOOL_SE_LEVEL_ONE_CONFIRM_V1\n"
                + framed(values.get("campaignKey")) + framed(values.get("characterName"))
                + framed(preview.previewDigest));
    }

    function securityHeaders(requestId, digest) {
        return {
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            "X-CSRF-Token": document.getElementById("csrf-token").value,
            "X-Host-State-Epoch": document.getElementById("host-state-epoch").value,
            "X-Object-Row-Version": document.getElementById("object-row-version").value,
            "X-Request-Id": requestId,
            "X-Request-Digest": digest
        };
    }

    form.addEventListener("input", () => { preview = null; confirm.disabled = true; });
    form.addEventListener("change", () => { preview = null; confirm.disabled = true; });
    form.addEventListener("submit", async event => {
        event.preventDefault();
        const values = parameters("PREVIEW");
        const requestId = crypto.randomUUID();
        const bodyDigest = await sha256(values.toString());
        result.textContent = "服务器正在验证冻结目录与全部选择……";
        try {
            const response = await fetch(form.dataset.endpoint, {method: "POST",
                headers: securityHeaders(requestId, bodyDigest), body: values,
                credentials: "same-origin"});
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.code || "REQUEST_FAILED");
            preview = payload;
            confirm.disabled = false;
            result.textContent = `预览有效：HP ${payload.maximumHitPoints}；确认前任何战役事件都会使其失效。`;
        } catch (error) {
            preview = null;
            confirm.disabled = true;
            result.textContent = `预览被拒绝（${error.message}）。`;
        }
    });

    confirm.addEventListener("click", async () => {
        if (!preview) return;
        const values = parameters("CONFIRM");
        values.set("previewDigest", preview.previewDigest);
        values.set("expectedEventTail", String(preview.expectedEventTail));
        const requestId = crypto.randomUUID();
        const digest = await confirmationDigest(values);
        confirm.disabled = true;
        try {
            const response = await fetch(form.dataset.endpoint, {method: "POST",
                headers: securityHeaders(requestId, digest), body: values,
                credentials: "same-origin"});
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.code || "REQUEST_FAILED");
            result.textContent = `一级角色创建成功：${payload.characterKey}（版本 ${payload.rowVersion}）。`;
            preview = null;
        } catch (error) {
            result.textContent = `确认被拒绝（${error.message}）；请重新预览。`;
            preview = null;
        }
    });
})();
