(() => {
    "use strict";

    const form = document.getElementById("archive-upload-form");
    if (!form) return;
    const fileInput = document.getElementById("archive-file");
    const resultNode = document.getElementById("archive-upload-result");
    const submitButton = form.querySelector("button[type='submit']");
    const importButton = document.getElementById("archive-import-button");
    const importResult = document.getElementById("archive-import-result");
    const previewPanel = document.getElementById("archive-preview");
    const maximumBytes = 16 * 1024 * 1024;
    const digestDomain = "DND_TOOL_SE_IMPORT_CAMPAIGN_ARCHIVE_V1";
    const encoder = new TextEncoder();
    let currentPreview = null;
    const countLabels = Object.freeze({
        characters: "角色",
        fields: "角色字段",
        classLevels: "职业等级",
        skillProficiencies: "技能熟练",
        saveProficiencies: "豁免熟练",
        items: "简单物品",
        maps: "地图",
        encounters: "活动遭遇",
        participants: "遭遇参与者",
        recentEvents: "最近事件",
        checks: "检定快照"
    });

    async function sha256(bytes) {
        const digest = await crypto.subtle.digest("SHA-256", bytes);
        return Array.from(new Uint8Array(digest), byte =>
            byte.toString(16).padStart(2, "0")).join("");
    }

    function int32(value) {
        const bytes = new Uint8Array(4);
        new DataView(bytes.buffer).setInt32(0, value, false);
        return bytes;
    }

    function field(value) {
        const bytes = encoder.encode(value);
        const result = new Uint8Array(4 + bytes.length);
        result.set(int32(bytes.length), 0);
        result.set(bytes, 4);
        return result;
    }

    async function confirmationDigest(rawFileSha256, confirmedCampaignKey) {
        const chunks = [field(digestDomain), field(rawFileSha256)];
        chunks.push(confirmedCampaignKey === null
            ? int32(-1) : field(confirmedCampaignKey));
        const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
        const canonical = new Uint8Array(length);
        let offset = 0;
        for (const chunk of chunks) {
            canonical.set(chunk, offset);
            offset += chunk.length;
        }
        return sha256(canonical);
    }

    function resetPreview() {
        currentPreview = null;
        resultNode.textContent = "";
        importResult.textContent = "";
        importButton.disabled = true;
        previewPanel.hidden = true;
    }

    fileInput.addEventListener("change", resetPreview);

    function impactText(preview) {
        const active = preview.activeCampaign;
        const identity = active
            ? `${active.campaignName}（${active.campaignKey}）`
            : "";
        switch (preview.activeCampaignImpact) {
            case "NONE": return "当前没有活动战役需要归档。";
            case "TARGET_REMAINS_ACTIVE": return "目标战役当前已活动，导入后仍保持活动。";
            case "TARGET_WILL_BE_ARCHIVED": return "目标战役当前活动，导入后将按存档状态归档。";
            case "OTHER_REMAINS_ACTIVE": return `另一活动战役 ${identity} 不受本次导入影响。`;
            case "OTHER_WILL_BE_ARCHIVED": return `另一活动战役 ${identity} 将在确认导入时归档。`;
            default: throw new Error("Unknown active campaign impact");
        }
    }

    function showPreview(preview, expectedDigest) {
        if (!preview || preview.rawFileSha256 !== expectedDigest
                || preview.irreversibleWarning !== true) {
            throw new Error("Preview binding mismatch");
        }
        document.getElementById("archive-preview-mode").textContent =
            preview.mode === "CREATE" ? "新建完整战役" : "替换完整战役";
        document.getElementById("archive-preview-campaign").textContent =
            `${preview.campaign.campaignName}（${preview.campaign.campaignKey}）`;
        document.getElementById("archive-preview-status").textContent =
            preview.campaign.campaignStatus;
        document.getElementById("archive-preview-sha256").textContent =
            preview.rawFileSha256;
        document.getElementById("archive-preview-impact").textContent = impactText(preview);

        const counts = document.getElementById("archive-preview-counts");
        counts.replaceChildren();
        for (const [key, label] of Object.entries(countLabels)) {
            const row = document.createElement("tr");
            const name = document.createElement("th");
            const value = document.createElement("td");
            name.scope = "row";
            name.textContent = label;
            value.textContent = String(preview.counts[key]);
            row.append(name, value);
            counts.append(row);
        }
        currentPreview = preview;
        previewPanel.hidden = false;
        importButton.disabled = false;
    }

    function commandHeaders(requestDigest) {
        return {
            "X-CSRF-Token": document.getElementById("archive-csrf-token").value,
            "X-Host-State-Epoch":
                document.getElementById("archive-host-state-epoch").value,
            "X-Object-Row-Version":
                document.getElementById("archive-row-version").value,
            "X-Request-Id": crypto.randomUUID(),
            "X-Request-Digest": requestDigest
        };
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const file = fileInput.files && fileInput.files[0];
        if (!file || file.size < 1 || file.size > maximumBytes) {
            resultNode.textContent = "请选择 1 字节至 16 MiB 的 JSON 存档。";
            return;
        }

        submitButton.disabled = true;
        resetPreview();
        resultNode.textContent = "正在上传并生成只读预览……";
        try {
            const bytes = await file.arrayBuffer();
            const digest = await sha256(bytes);
            const body = new FormData();
            body.append("archive", file, file.name);
            const response = await fetch(form.dataset.endpoint, {
                method: "POST",
                headers: commandHeaders(digest),
                body,
                credentials: "same-origin"
            });
            const payload = await response.json();
            if (response.ok && payload.status === "READY") {
                showPreview(payload.preview, digest);
                resultNode.textContent = "只读导入预览已生成；请核对后明确确认。";
            } else {
                resultNode.textContent =
                    `存档未通过预览（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            resultNode.textContent = "请求未完成；未执行任何导入写入。";
        } finally {
            submitButton.disabled = false;
        }
    });

    importButton.addEventListener("click", async () => {
        const file = fileInput.files && fileInput.files[0];
        if (!currentPreview || !file || file.size < 1 || file.size > maximumBytes) {
            resetPreview();
            resultNode.textContent = "存档选择已变化，请重新生成预览。";
            return;
        }

        submitButton.disabled = true;
        importButton.disabled = true;
        importResult.textContent = "正在重新校验并执行整战役导入……";
        try {
            const bytes = await file.arrayBuffer();
            const rawDigest = await sha256(bytes);
            if (rawDigest !== currentPreview.rawFileSha256) {
                throw new Error("Archive changed after preview");
            }
            const confirmedCampaignKey =
                currentPreview.activeCampaignImpact === "OTHER_WILL_BE_ARCHIVED"
                    ? currentPreview.activeCampaign.campaignKey : null;
            const requestDigest = await confirmationDigest(
                rawDigest, confirmedCampaignKey);
            const headers = commandHeaders(requestDigest);
            headers["X-Archive-Preview-SHA256"] = rawDigest;
            if (confirmedCampaignKey !== null) {
                headers["X-Confirmed-Archive-Campaign-Key"] = confirmedCampaignKey;
            }
            const body = new FormData();
            body.append("archive", file, file.name);
            const response = await fetch(form.dataset.importEndpoint, {
                method: "POST",
                headers,
                body,
                credentials: "same-origin",
                redirect: "follow"
            });
            const redirected = new URL(response.url);
            if (response.redirected
                    && redirected.origin === window.location.origin
                    && redirected.pathname === form.dataset.redirect) {
                window.location.replace(form.dataset.redirect);
                return;
            }
            const payload = await response.json();
            importResult.textContent =
                `导入未完成（${payload.code || "REQUEST_FAILED"}）；当前页面仍有效。`;
        } catch (error) {
            importResult.textContent = "导入请求未完成；请重新生成预览后再试。";
        } finally {
            submitButton.disabled = false;
            importButton.disabled = currentPreview === null;
        }
    });
})();
