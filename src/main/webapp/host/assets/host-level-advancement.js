/* Server-authoritative preview/confirm client for canonical-v2 level advancement. */
(() => {
    "use strict";
    const form = document.getElementById("level-advancement-form");
    if (!form) return;
    const result = document.getElementById("level-advancement-result");
    const confirm = document.getElementById("level-advancement-confirm");
    const encoder = new TextEncoder();
    let preview = null;

    function parameters(action) {
        const values = new URLSearchParams();
        values.set("action", action);
        values.set("characterKey", form.elements.characterKey.value.trim());
        values.set("targetLevel", form.elements.targetLevel.value.trim());
        values.set("hpChoiceAlgorithm", form.elements.hpChoiceAlgorithm.value);
        values.set("targetClassKey", form.elements.targetClassKey.value.trim());
        const subclass = form.elements.subclassKey.value.trim();
        if (subclass) values.set("subclassKey", subclass);
        for (const ability of ["strength", "dexterity", "constitution",
                "intelligence", "wisdom", "charisma"]) {
            const value = form.elements[`asi.${ability}`].value.trim();
            if (value) values.set(`asi.${ability}`, value);
        }
        const feat = form.elements.featKey.value.trim();
        if (feat) values.set("featKey", feat);
        const proficiencies = form.elements.proficiencyChoices.value.trim();
        if (proficiencies) values.set("proficiencyChoices", proficiencies);
        return values;
    }

    async function sha256(text) {
        const bytes = await crypto.subtle.digest("SHA-256", encoder.encode(text));
        return Array.from(new Uint8Array(bytes), value =>
            value.toString(16).padStart(2, "0")).join("");
    }

    function framed(value) {
        return `${encoder.encode(value).length}:${value}\n`;
    }

    async function confirmationDigest(values) {
        let canonical = "DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V2\n"
                + framed(values.get("characterKey"))
                + `${values.get("targetLevel")}\n`
                + framed(values.get("hpChoiceAlgorithm"))
                + framed(values.get("targetClassKey"))
                + (values.has("subclassKey") ? framed(values.get("subclassKey")) : "-1:\n");
        for (const ability of ["charisma", "constitution", "dexterity",
                "intelligence", "strength", "wisdom"]) {
            const value = values.get(`asi.${ability}`);
            if (value !== null) canonical += framed(`ability.${ability}`) + `${value}\n`;
        }
        canonical += values.has("featKey") ? framed(values.get("featKey")) : "-1:\n";
        const proficiencies = values.has("proficiencyChoices")
                ? values.get("proficiencyChoices").split(",").map(value => value.trim()).sort()
                : [];
        for (const value of proficiencies) canonical += framed(value);
        canonical += framed(preview.previewDigest);
        return sha256(canonical);
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

    function invalidate() {
        preview = null;
        confirm.disabled = true;
    }

    form.addEventListener("input", invalidate);
    form.addEventListener("change", invalidate);
    form.addEventListener("submit", async event => {
        event.preventDefault();
        const values = parameters("PREVIEW");
        const requestId = crypto.randomUUID();
        result.textContent = "服务器正在验证等级、冻结目录和资源……";
        try {
            const response = await fetch(form.dataset.endpoint, {
                method: "POST", headers: securityHeaders(
                    requestId, await sha256(values.toString())),
                body: values, credentials: "same-origin"
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.code || "REQUEST_FAILED");
            preview = payload;
            confirm.disabled = false;
            const hp = payload.minimumHitPointIncrease === payload.maximumHitPointIncrease
                ? String(payload.minimumHitPointIncrease)
                : `${payload.minimumHitPointIncrease}–${payload.maximumHitPointIncrease}`;
            const choice = payload.featKey ? `；专长 ${payload.featKey}` : "";
            const multiclass = payload.multiclass ? "；新增职业" : "";
            result.textContent = `等级 ${payload.previousLevel} → ${payload.targetLevel}；`
                    + `HP +${hp}；熟练 +${payload.previousProficiencyBonus}`
                    + ` → +${payload.newProficiencyBonus}；d${payload.hitDieSides} 生命骰 +1`
                    + `${multiclass}${choice}。`;
        } catch (error) {
            invalidate();
            result.textContent = `预览被拒绝（${error.message}）。`;
        }
    });

    confirm.addEventListener("click", async () => {
        if (!preview) return;
        const values = parameters("CONFIRM");
        values.set("previewDigest", preview.previewDigest);
        values.set("expectedEventTail", String(preview.expectedEventTail));
        values.set("expectedRowVersion", String(preview.expectedRowVersion));
        const requestId = crypto.randomUUID();
        confirm.disabled = true;
        try {
            const response = await fetch(form.dataset.endpoint, {
                method: "POST", headers: securityHeaders(
                    requestId, await confirmationDigest(values)),
                body: values, credentials: "same-origin"
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.code || "REQUEST_FAILED");
            const roll = payload.hitDieRoll === null ? "固定值" : `掷骰 ${payload.hitDieRoll}`;
            result.textContent = `升级成功（${roll}，HP +${payload.hitPointIncrease}，`
                    + `版本 ${payload.rowVersion}）。`;
            preview = null;
        } catch (error) {
            result.textContent = `确认被拒绝（${error.message}）；请重新预览。`;
            preview = null;
        }
    });
})();
