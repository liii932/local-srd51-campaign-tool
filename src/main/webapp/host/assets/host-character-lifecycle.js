/* Sends audited character lifecycle commands with optimistic locking and idempotency. */
(() => {
    "use strict";

    const form = document.getElementById("character-lifecycle-form");
    if (!form) return;

    const keyInput = document.getElementById("lifecycle-character-key");
    const versionInput = document.getElementById("lifecycle-row-version");
    const actionInput = document.getElementById("lifecycle-action");
    const valueInput = document.getElementById("lifecycle-value");
    const resultNode = document.getElementById("lifecycle-result");
    const button = form.querySelector('button[type="submit"]');
    const encoder = new TextEncoder();
    const domain = "DND_TOOL_SE_MUTATE_CHARACTER_LIFECYCLE_V1";
    let pending = null;

    const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;

    function normalizedValue(action) {
        if (action === "RENAME") {
            return valueInput.value.replace(javaWhitespace, "").normalize("NFC");
        }
        if (action === "CHANGE_TYPE") return valueInput.value.trim().toUpperCase();
        return "";
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
            domain, command.characterKey, command.rowVersion,
            command.action, command.value
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

    function resetPending() {
        pending = null;
        resultNode.textContent = "";
    }
    for (const input of [keyInput, versionInput, actionInput, valueInput]) {
        input.addEventListener("input", resetPending);
        input.addEventListener("change", resetPending);
    }
    actionInput.addEventListener("change", () => {
        const needsValue = actionInput.value === "RENAME"
                || actionInput.value === "CHANGE_TYPE";
        valueInput.disabled = !needsValue;
        valueInput.required = needsValue;
        if (!needsValue) valueInput.value = "";
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const command = {
            characterKey: keyInput.value.trim().toLowerCase(),
            rowVersion: versionInput.value,
            action: actionInput.value,
            value: normalizedValue(actionInput.value)
        };
        const validKey = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(command.characterKey);
        const validVersion = /^(?:0|[1-9][0-9]*)$/.test(command.rowVersion);
        const validValue = command.action === "CHANGE_TYPE"
            ? /^(?:PC|NPC)$/.test(command.value)
            : command.action !== "RENAME"
                || (Array.from(command.value).length >= 1
                    && Array.from(command.value).length <= 80
                    && !/[\u0000-\u001f\u007f-\u009f]/u.test(command.value));
        if (!validKey || !validVersion || !validValue) {
            resultNode.textContent = "请检查角色编号、版本、操作和新值。";
            return;
        }

        button.disabled = true;
        resultNode.textContent = "正在验证规则并修改角色……";
        try {
            const token = await idempotency(command);
            const response = await fetch(form.dataset.endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "X-CSRF-Token": document.getElementById("csrf-token").value,
                    "X-Host-State-Epoch": document.getElementById("host-state-epoch").value,
                    "X-Object-Row-Version": command.rowVersion,
                    "X-Request-Id": token.requestId,
                    "X-Request-Digest": token.digest
                },
                body: new URLSearchParams({
                    characterKey: command.characterKey,
                    action: command.action,
                    value: command.value
                }),
                credentials: "same-origin"
            });
            const payload = await response.json();
            if (response.ok) {
                versionInput.value = String(payload.rowVersion);
                resultNode.textContent = `角色修改成功（版本 ${payload.rowVersion}）。`;
                pending = null;
            } else {
                if (payload.rowVersion !== undefined && payload.rowVersion !== null) {
                    versionInput.value = String(payload.rowVersion);
                }
                resultNode.textContent = `未修改角色（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            resultNode.textContent = "请求未完成；可以再次提交以安全重试。";
        } finally {
            button.disabled = false;
        }
    });
})();
