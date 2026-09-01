/* Creates the first local campaign through the protected host command contract. */
(() => {
    "use strict";

    const form = document.getElementById("create-campaign-form");
    if (!form) return;

    const nameInput = document.getElementById("campaign-name");
    const resultNode = document.getElementById("campaign-result");
    const submitButton = form.querySelector('button[type="submit"]');
    const encoder = new TextEncoder();
    const domain = encoder.encode("DND_TOOL_SE_CREATE_CAMPAIGN_V1");
    let pending = null;

    // This set matches Java Character.isWhitespace for code points relevant to trimming.
    const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;

    function normalizedName() {
        return nameInput.value.replace(javaWhitespace, "").normalize("NFC");
    }

    function lengthPrefixed(first, second) {
        const bytes = new Uint8Array(4 + first.length + 4 + second.length);
        const view = new DataView(bytes.buffer);
        view.setUint32(0, first.length, false);
        bytes.set(first, 4);
        view.setUint32(4 + first.length, second.length, false);
        bytes.set(second, 8 + first.length);
        return bytes;
    }

    async function requestDigest(name) {
        const payload = lengthPrefixed(domain, encoder.encode(name));
        const digest = await crypto.subtle.digest("SHA-256", payload);
        return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
    }

    async function commandFor(name) {
        if (pending && pending.name === name) return pending;
        pending = {
            name,
            requestId: crypto.randomUUID(),
            digest: await requestDigest(name)
        };
        return pending;
    }

    nameInput.addEventListener("input", () => {
        pending = null;
        resultNode.textContent = "";
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const name = normalizedName();
        const codePointCount = Array.from(name).length;
        if (codePointCount < 1 || codePointCount > 80 || /[\u0000-\u001f\u007f-\u009f]/u.test(name)) {
            resultNode.textContent = "战役名称须为 1 至 80 个字符，且不能包含控制字符。";
            return;
        }

        submitButton.disabled = true;
        resultNode.textContent = "正在验证规则并创建战役……";
        try {
            const command = await commandFor(name);
            const body = new URLSearchParams({campaignName: name});
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
                body,
                credentials: "same-origin"
            });
            const payload = await response.json();
            if (response.ok) {
                resultNode.textContent = payload.status === "ALREADY_SUCCEEDED"
                    ? `该请求已完成，战役编号：${payload.campaignKey}`
                    : `战役创建成功，编号：${payload.campaignKey}`;
                form.reset();
                pending = null;
            } else {
                // Keep the same idempotency key so a transient/unknown failure can be retried safely.
                resultNode.textContent = `未创建战役（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            // Do not expose network, database, or stack details in the page.
            resultNode.textContent = "请求未完成；可以再次提交以安全重试。";
        } finally {
            submitButton.disabled = false;
        }
    });
})();
