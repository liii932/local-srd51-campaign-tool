/* Creates blank or template-backed characters through the protected host command contract. */
(() => {
    "use strict";

    const form = document.getElementById("create-character-form");
    if (!form) return;

    const campaignInput = document.getElementById("character-campaign-key");
    const typeInput = document.getElementById("character-type");
    const nameInput = document.getElementById("character-name");
    const templateInput = document.getElementById("character-template");
    const resultNode = document.getElementById("character-result");
    const submitButton = form.querySelector('button[type="submit"]');
    const encoder = new TextEncoder();
    const domain = "DND_TOOL_SE_CREATE_CHARACTER_V1";
    let pending = null;

    const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;

    function normalizedName() {
        return nameInput.value.replace(javaWhitespace, "").normalize("NFC");
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

    async function requestDigest(command) {
        const payload = digestPayload([
            domain,
            command.campaignKey,
            command.characterType,
            command.characterName,
            command.templateKey
        ]);
        const digest = await crypto.subtle.digest("SHA-256", payload);
        return Array.from(new Uint8Array(digest), byte =>
            byte.toString(16).padStart(2, "0")).join("");
    }

    async function commandFor(values) {
        const key = JSON.stringify(values);
        if (pending && pending.key === key) return pending;
        pending = {
            key,
            requestId: crypto.randomUUID(),
            digest: await requestDigest(values)
        };
        return pending;
    }

    function resetPending() {
        pending = null;
        resultNode.textContent = "";
    }
    for (const input of [campaignInput, typeInput, nameInput, templateInput]) {
        input.addEventListener("input", resetPending);
        input.addEventListener("change", resetPending);
    }
    typeInput.addEventListener("change", () => {
        if (typeInput.value === "PC") templateInput.value = "";
        templateInput.disabled = typeInput.value === "PC";
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const values = {
            campaignKey: campaignInput.value.trim().toLowerCase(),
            characterType: typeInput.value,
            characterName: normalizedName(),
            templateKey: templateInput.value
        };
        const codePoints = Array.from(values.characterName).length;
        const validCampaign = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(values.campaignKey);
        if (!validCampaign || codePoints < 1 || codePoints > 80
                || /[\u0000-\u001f\u007f-\u009f]/u.test(values.characterName)
                || (values.characterType === "PC" && values.templateKey !== "")) {
            resultNode.textContent = "请检查战役编号、角色类型、名称和模板。";
            return;
        }

        submitButton.disabled = true;
        resultNode.textContent = "正在验证规则并创建角色……";
        try {
            const command = await commandFor(values);
            const response = await fetch(form.dataset.endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "X-CSRF-Token": document.getElementById("csrf-token").value,
                    "X-Host-State-Epoch": document.getElementById("host-state-epoch").value,
                    "X-Object-Row-Version": document.getElementById("object-row-version").value,
                    "X-Request-Id": command.requestId,
                    "X-Request-Digest": command.digest
                },
                body: new URLSearchParams(values),
                credentials: "same-origin"
            });
            const payload = await response.json();
            if (response.ok) {
                resultNode.textContent = `角色创建成功：${payload.characterKey}（版本 ${payload.rowVersion}）`;
                nameInput.value = "";
                pending = null;
            } else {
                resultNode.textContent = `未创建角色（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            resultNode.textContent = "请求未完成；可以再次提交以安全重试。";
        } finally {
            submitButton.disabled = false;
        }
    });
})();
