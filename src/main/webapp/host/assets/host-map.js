/* Sends idempotent protected commands for the frozen node map and encounter. */
(() => {
    "use strict";
    const encounterForm = document.getElementById("host-encounter-form");
    const positionForm = document.getElementById("host-position-form");
    if (!encounterForm || !positionForm) return;
    const byId = id => document.getElementById(id);
    const encoder = new TextEncoder();
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    const versionPattern = /^(0|[1-9][0-9]*)$/;
    let encounterPending = null;
    let positionPending = null;

    function bytes(fields) {
        const encoded = fields.map(value => encoder.encode(String(value)));
        const result = new Uint8Array(encoded.reduce((sum, field) => sum + 4 + field.length, 0));
        const view = new DataView(result.buffer);
        let offset = 0;
        for (const field of encoded) {
            view.setUint32(offset, field.length, false); offset += 4;
            result.set(field, offset); offset += field.length;
        }
        return result;
    }

    async function sha256(fields) {
        const digest = await crypto.subtle.digest("SHA-256", bytes(fields));
        return Array.from(new Uint8Array(digest), value =>
            value.toString(16).padStart(2, "0")).join("");
    }

    function participants() {
        const result = byId("host-participants").value.split(/\r?\n/u)
                .map(line => line.trim()).filter(Boolean).map(line => {
                    const values = line.split(",");
                    if (values.length !== 3) throw new Error("INVALID_PARTICIPANT");
                    return {characterKey: values[0].trim().toLowerCase(),
                        faction: values[1].trim().toUpperCase(), nodeKey: values[2].trim()};
                });
        if (result.some(value => !uuidPattern.test(value.characterKey)
                || !["ALLY", "ENEMY", "NEUTRAL"].includes(value.faction)
                || !/^node\.[a-z0-9_]+$/.test(value.nodeKey))
                || new Set(result.map(value => value.characterKey)).size !== result.length) {
            throw new Error("INVALID_PARTICIPANT");
        }
        return result.sort((a, b) => a.characterKey.localeCompare(b.characterKey));
    }

    function headers(rowVersion, token) {
        return {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            "X-CSRF-Token": byId("csrf-token").value,
            "X-Host-State-Epoch": byId("host-state-epoch").value,
            "X-Object-Row-Version": rowVersion,
            "X-Request-Id": token.requestId, "X-Request-Digest": token.digest};
    }

    encounterForm.addEventListener("input", () => { encounterPending = null; });
    encounterForm.addEventListener("change", () => { encounterPending = null; });
    encounterForm.addEventListener("submit", async event => {
        event.preventDefault();
        const resultNode = byId("host-encounter-result");
        try {
            const command = {campaignKey: byId("active-campaign-key").value,
                partyNodeKey: byId("host-party-node").value, participants: participants()};
            if (!uuidPattern.test(command.campaignKey)) throw new Error("NO_CAMPAIGN");
            const fields = ["DND_TOOL_SE_INITIALIZE_STAGE3_ENCOUNTER_V1",
                command.campaignKey, command.partyNodeKey, String(command.participants.length)];
            for (const participant of command.participants) {
                fields.push(participant.characterKey, participant.faction, participant.nodeKey);
            }
            const identity = JSON.stringify(command);
            if (!encounterPending || encounterPending.identity !== identity) {
                encounterPending = {identity, requestId: crypto.randomUUID(),
                    digest: await sha256(fields)};
            }
            resultNode.textContent = "正在初始化地图、遭遇和审计事件……";
            const response = await fetch(encounterForm.dataset.endpoint, {method: "POST",
                credentials: "same-origin", headers: headers("0", encounterPending),
                body: new URLSearchParams({partyNodeKey: command.partyNodeKey,
                    participants: byId("host-participants").value})});
            const payload = await response.json();
            if (response.ok) {
                resultNode.textContent = payload.participantCount === null
                    ? `地图与遭遇请求已幂等重放（事件 #${payload.eventSequence}）。`
                    : `地图与遭遇已初始化（事件 #${payload.eventSequence}，`
                        + `${payload.participantCount} 名参与者）。`;
                encounterPending = null;
            } else resultNode.textContent = `未初始化（${payload.code || "REQUEST_FAILED"}）。`;
        } catch (error) {
            resultNode.textContent = "请先创建活动战役，并检查队伍节点及参与者三列格式。";
        }
    });

    positionForm.addEventListener("input", () => { positionPending = null; });
    positionForm.addEventListener("change", () => { positionPending = null; });
    positionForm.addEventListener("submit", async event => {
        event.preventDefault();
        const resultNode = byId("host-position-result");
        try {
            const command = {campaignKey: byId("active-campaign-key").value,
                characterKey: byId("host-position-character").value.trim().toLowerCase(),
                rowVersion: byId("host-position-version").value,
                nodeKey: byId("host-position-node").value};
            if (!uuidPattern.test(command.campaignKey) || !uuidPattern.test(command.characterKey)
                    || !versionPattern.test(command.rowVersion)) throw new Error("INVALID_MOVE");
            const payloadHash = await sha256(["SET_STAGE3_ENTITY_POSITION_V2",
                command.campaignKey, command.characterKey, "map.tavern_cellar", command.nodeKey]);
            const digest = await sha256(["DND_TOOL_SE_EXECUTE_STAGE3_CHECK_V1", payloadHash,
                command.characterKey, command.rowVersion, "0"]);
            const identity = JSON.stringify(command);
            if (!positionPending || positionPending.identity !== identity) {
                positionPending = {identity, requestId: crypto.randomUUID(), digest};
            }
            resultNode.textContent = "正在移动角色并写入审计事件……";
            const response = await fetch(positionForm.dataset.endpoint, {method: "POST",
                credentials: "same-origin", headers: headers(command.rowVersion, positionPending),
                body: new URLSearchParams({characterKey: command.characterKey,
                    nodeKey: command.nodeKey})});
            const payload = await response.json();
            if (response.ok) {
                if (payload.rowVersion !== null) byId("host-position-version").value = payload.rowVersion;
                resultNode.textContent = `角色已在 ${payload.nodeKey}（事件 #${payload.eventSequence}）。`;
                positionPending = null;
            } else {
                if (payload.rowVersion !== undefined && payload.rowVersion !== null) {
                    byId("host-position-version").value = payload.rowVersion;
                }
                resultNode.textContent = `未移动（${payload.code || "REQUEST_FAILED"}）。`;
            }
        } catch (error) {
            resultNode.textContent = "请检查活动战役、角色键、当前版本和目标节点。";
        }
    });
})();
