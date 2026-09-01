<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.dndtool.persistence.HostOverviewRepository" %>
<%@ page import="com.dndtool.web.HtmlSupport" %>
<%--
    Dynamic values are stored in hidden fields so the external script remains compatible with
    the self-only CSP. The token is never placed in a cookie, URL, or persistent browser storage.
--%>
<%
    Object overviewValue = request.getAttribute("dndtool.hostOverview");
    HostOverviewRepository.Snapshot overview =
            overviewValue instanceof HostOverviewRepository.Snapshot
                    ? (HostOverviewRepository.Snapshot) overviewValue : null;
    Object overviewStatusValue = request.getAttribute("dndtool.hostOverviewStatus");
    String overviewStatus = overviewStatusValue instanceof String
            ? (String) overviewStatusValue : "EMPTY";
%>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Local 5E Campaign Tool — 本机 DM 控制台</title>
    <script src="<%= request.getContextPath() %>/host/assets/host-campaign.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-character.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-level-one-character.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-level-advancement.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-character-lifecycle.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-character-card.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-event.js" defer></script>
    <script src="<%= request.getContextPath() %>/host/assets/host-map.js" defer></script>
</head>
<body>
    <main>
        <h1>Local 5E Campaign Tool</h1>
        <p>
            <a href="<%= request.getContextPath() %>/host/rules">规则目录</a>
            · <a href="<%= request.getContextPath() %>/host/archive">本机存档导入</a>
            · <a id="active-campaign-export"
                 href="<%= request.getContextPath() %>/api/host/archive/export">导出当前活动战役</a>
        </p>
        <input id="active-campaign-key" type="hidden"
               value="<%= overview == null ? "" : HtmlSupport.escape(overview.campaign().campaignKey()) %>">
        <section id="host-overview" aria-labelledby="host-overview-title">
            <h2 id="host-overview-title">当前战役总览</h2>
<% if (overview != null && "READY".equals(overviewStatus)) { %>
            <p>
                <strong><%= HtmlSupport.escape(overview.campaign().campaignName()) %></strong>
                （<%= HtmlSupport.escape(overview.campaign().campaignKey()) %>）
                · 状态 <%= HtmlSupport.escape(overview.campaign().campaignStatus()) %>
                · 规则 <%= HtmlSupport.escape(overview.binding().frozenModuleKey()) %>
                / <%= HtmlSupport.escape(overview.binding().frozenReleaseVersion()) %>
            </p>

            <h3>角色摘要</h3>
<% if (overview.characters().isEmpty()) { %>
            <p>当前战役还没有角色。</p>
<% } else { %>
            <table id="host-character-summary">
                <thead>
                    <tr><th>名称</th><th>类型</th><th>状态</th><th>HP</th><th>AC</th><th>物品</th><th>版本</th></tr>
                </thead>
                <tbody>
<% for (HostOverviewRepository.CharacterSummary character : overview.characters()) { %>
                    <tr>
                        <td><%= HtmlSupport.escape(character.characterName()) %></td>
                        <td><%= HtmlSupport.escape(character.characterType()) %></td>
                        <td><%= HtmlSupport.escape(character.characterStatus()) %></td>
                        <td><%= character.currentHp() == null || character.maximumHp() == null
                                ? "—" : character.currentHp() + " / " + character.maximumHp() %></td>
                        <td><%= character.armorClass() == null ? "—" : character.armorClass() %></td>
                        <td><%= character.itemCount() %></td>
                        <td><%= character.rowVersion() %></td>
                    </tr>
<% } %>
                </tbody>
            </table>
<% } %>

            <h3>检定与消息</h3>
<% if (overview.events().isEmpty()) { %>
            <p>尚无检定或消息记录。</p>
<% } else { %>
            <ol id="host-check-message-summary">
<% for (HostOverviewRepository.EventSummary event : overview.events()) { %>
                <li>
                    #<%= event.eventSequence() %>
<% if (event.checkKey() != null) { %>
                    <%= HtmlSupport.escape(event.manualName() == null
                            ? event.modifierSourceKey() : event.manualName()) %>
                    · <%= HtmlSupport.escape(event.rollModeKey()) %>
                    · 修正 <%= event.modifierValue() %>
                    · 合计 <%= event.totalValue() %>
                    / DC <%= event.difficultyClass() %>
                    · <%= HtmlSupport.escape(event.checkResult()) %>
<% } else { %>
                    消息
<% } %>
<% if (event.subjectName() != null) { %>
                    · <%= HtmlSupport.escape(event.subjectName()) %>
<% } %>
<% if (event.eventText() != null) { %>
                    — <%= HtmlSupport.escape(event.eventText()) %>
<% } %>
                </li>
<% } %>
            </ol>
<% } %>

            <h3>简单物品总览</h3>
<% if (overview.items().isEmpty()) { %>
            <p>当前战役还没有简单物品。</p>
<% } else { %>
            <table id="host-item-summary">
                <thead>
                    <tr><th>持有者</th><th>名称</th><th>来源</th><th>数量</th><th>状态</th><th>说明</th></tr>
                </thead>
                <tbody>
<% for (HostOverviewRepository.ItemSummary item : overview.items()) { %>
                    <tr>
                        <td><%= HtmlSupport.escape(item.characterName()) %></td>
                        <td><%= HtmlSupport.escape(item.itemName()) %></td>
                        <td><%= HtmlSupport.escape(item.sourceKind()) %></td>
                        <td><%= item.quantity() %></td>
                        <td><%= HtmlSupport.escape(item.itemStatus()) %></td>
                        <td><%= HtmlSupport.escape(item.itemDescription()) %></td>
                    </tr>
<% } %>
                </tbody>
            </table>
<% } %>

            <h3>节点地图</h3>
            <p>
                <%= HtmlSupport.escape(overview.map().mapKey()) %>
<% if (overview.map().partyNodeKey() != null) { %>
                · 队伍节点 <%= HtmlSupport.escape(overview.map().partyNodeKey()) %>
<% } else { %>
                · 尚未保存队伍节点
<% } %>
            </p>
            <ul id="host-map-nodes">
<% for (HostOverviewRepository.MapNode node : overview.map().nodes()) { %>
                <li>
                    <%= HtmlSupport.escape(node.displayName()) %>
                    （<%= HtmlSupport.escape(node.nodeKey()) %>）
<% if (node.nodeKey().equals(overview.map().partyNodeKey())) { %>
                    · 队伍所在
<% } %>
                </li>
<% } %>
            </ul>
            <p>拓扑连接：</p>
            <ul id="host-map-connections">
<% for (HostOverviewRepository.MapConnection connection : overview.map().connections()) { %>
                <li><%= HtmlSupport.escape(connection.endpointLowName()) %>
                    — <%= HtmlSupport.escape(connection.endpointHighName()) %></li>
<% } %>
            </ul>

            <h3>当前遭遇</h3>
<% if (overview.encounter().battleStatus() == null) { %>
            <p>当前没有活动遭遇。</p>
<% } else if (overview.encounter().participants().isEmpty()) { %>
            <p>活动遭遇尚无参与者。</p>
<% } else { %>
            <table id="host-encounter-summary">
                <thead><tr><th>角色</th><th>类型</th><th>阵营</th><th>节点</th></tr></thead>
                <tbody>
<% for (HostOverviewRepository.Participant participant
        : overview.encounter().participants()) { %>
                    <tr>
                        <td><%= HtmlSupport.escape(participant.characterName()) %></td>
                        <td><%= HtmlSupport.escape(participant.characterType()) %></td>
                        <td><%= HtmlSupport.escape(participant.faction()) %></td>
                        <td><%= HtmlSupport.escape(participant.nodeName()) %></td>
                    </tr>
<% } %>
                </tbody>
            </table>
<% } %>
<% } else if ("DATABASE_UNAVAILABLE".equals(overviewStatus)) { %>
            <p role="status">战役总览暂不可用；数据库连接恢复后刷新页面。</p>
<% } else if ("MODULE_HASH_MISMATCH".equals(overviewStatus)) { %>
            <p role="status">模组完整性校验未通过，相关操作已拒绝。</p>
<% } else if ("INVALID_STATE".equals(overviewStatus)) { %>
            <p role="status">战役总览状态不一致，请先运行数据库诊断。</p>
<% } else { %>
            <p>当前没有活动战役。</p>
<% } %>
        </section>

        <hr>
        <h2>创建战役</h2>
        <p>创建首个本机战役。提交前会验证并冻结唯一内置规则发布版。</p>
        <form id="create-campaign-form" data-endpoint="<%= request.getContextPath() %>/api/host/campaigns">
            <label for="campaign-name">战役名称</label>
            <input id="campaign-name" name="campaignName" type="text" required>
            <input id="csrf-token" type="hidden" value="<%= request.getAttribute("dndtool.csrfToken") %>">
            <input id="host-state-epoch" type="hidden" value="<%= request.getAttribute("dndtool.hostStateEpoch") %>">
            <input id="object-row-version" type="hidden" value="<%= request.getAttribute("dndtool.rowVersion") %>">
            <button type="submit">创建战役</button>
        </form>
        <p id="campaign-result" role="status" aria-live="polite"></p>

        <hr>
        <h2>创建角色</h2>
        <p>可创建空白 PC/NPC，或使用审核过的内置 NPC 模板初始化角色。</p>
        <form id="create-character-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/characters">
            <label for="character-campaign-key">战役编号</label>
            <input id="character-campaign-key" name="campaignKey" type="text" required>

            <label for="character-type">角色类型</label>
            <select id="character-type" name="characterType">
                <option value="PC">PC</option>
                <option value="NPC">NPC</option>
            </select>

            <label for="character-name">角色名称</label>
            <input id="character-name" name="characterName" type="text" required>

            <label for="character-template">NPC 模板</label>
            <select id="character-template" name="templateKey">
                <option value="">空白角色</option>
                <option value="npc.commoner">平民</option>
                <option value="npc.guard">守卫</option>
                <option value="npc.wolf">狼</option>
            </select>

            <button type="submit">创建角色</button>
        </form>
        <p id="character-result" role="status" aria-live="polite"></p>

        <h3>一级 PC 创建器（canonical v2）</h3>
        <p>先由服务器预览全部派生值，再用同一预览摘要原子确认。DRAFT 规则版在发布前会拒绝业务执行。</p>
        <form id="level-one-character-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/characters/level-one">
            <label for="level-one-campaign-key">战役编号</label>
            <input id="level-one-campaign-key" name="campaignKey" type="text" required>
            <label for="level-one-name">角色名称</label>
            <input id="level-one-name" name="characterName" type="text" required>
            <label for="level-one-race">种族</label>
            <select id="level-one-race" name="raceKey">
                <option value="race.dwarf">Dwarf</option><option value="race.elf">Elf</option>
                <option value="race.halfling">Halfling</option><option value="race.human">Human</option>
                <option value="race.dragonborn">Dragonborn</option><option value="race.gnome">Gnome</option>
                <option value="race.half_elf">Half-Elf</option><option value="race.half_orc">Half-Orc</option>
                <option value="race.tiefling">Tiefling</option>
            </select>
            <label for="level-one-subrace">亚种（不适用时留空）</label>
            <select id="level-one-subrace" name="subraceKey">
                <option value="">不适用</option><option value="subrace.hill_dwarf">Hill Dwarf</option>
                <option value="subrace.high_elf">High Elf</option><option value="subrace.lightfoot">Lightfoot</option>
                <option value="subrace.rock_gnome">Rock Gnome</option>
            </select>
            <input name="backgroundKey" type="hidden" value="background.acolyte">
            <label for="level-one-class">职业</label>
            <select id="level-one-class" name="classKey">
                <option value="class.barbarian">Barbarian</option><option value="class.bard">Bard</option>
                <option value="class.cleric">Cleric</option><option value="class.druid">Druid</option>
                <option value="class.fighter">Fighter</option><option value="class.monk">Monk</option>
                <option value="class.paladin">Paladin</option><option value="class.ranger">Ranger</option>
                <option value="class.rogue">Rogue</option><option value="class.sorcerer">Sorcerer</option>
                <option value="class.warlock">Warlock</option><option value="class.wizard">Wizard</option>
            </select>
            <label>一级职业子职业键（职业在一级选择时填写）
                <input name="classSubclassKey" type="text"
                       placeholder="subclass.life / subclass.draconic / subclass.fiend">
            </label>
            <fieldset><legend>标准数组分配</legend>
                <label>STR <input name="ability.strength" type="number" value="15" required></label>
                <label>DEX <input name="ability.dexterity" type="number" value="12" required></label>
                <label>CON <input name="ability.constitution" type="number" value="14" required></label>
                <label>INT <input name="ability.intelligence" type="number" value="10" required></label>
                <label>WIS <input name="ability.wisdom" type="number" value="13" required></label>
                <label>CHA <input name="ability.charisma" type="number" value="8" required></label>
            </fieldset>
            <label>自由属性加值键（逗号分隔）<input name="abilityBonusChoices" type="text"></label>
            <label>技能选择键（逗号分隔）<input name="skillChoices" type="text" value="skill.athletics"></label>
            <label>语言选择键（逗号分隔）<input name="languageChoices" type="text" value="language.celestial,language.draconic"></label>
            <label>工具选择键（逗号分隔）<input name="toolChoices" type="text" value="tool.smith_tools"></label>
            <label>起始选项键（逗号分隔）<input name="startingOptionChoices" type="text" value="starting.background.acolyte.equipment,starting.class.fighter.a"></label>
            <button id="level-one-preview" type="submit">服务器预览</button>
            <button id="level-one-confirm" type="button" disabled>确认创建</button>
        </form>
        <p id="level-one-result" role="status" aria-live="polite"></p>

        <h3>升级与生命骰（canonical v2）</h3>
        <p>预览不会掷骰；确认时会在版本、冻结目录和完整资源集合验证后由服务器结算。</p>
        <form id="level-advancement-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/characters/level-up">
            <label for="level-advancement-character-key">角色编号</label>
            <input id="level-advancement-character-key" name="characterKey" type="text" required>
            <label for="level-advancement-target">目标总等级</label>
            <input id="level-advancement-target" name="targetLevel"
                   type="number" min="2" max="20" step="1" required>
            <label for="level-advancement-class">本级职业键</label>
            <input id="level-advancement-class" name="targetClassKey" type="text"
                   placeholder="class.fighter" required>
            <label>子职业键（仅在目标职业选择等级填写）
                <input name="subclassKey" type="text" placeholder="subclass.champion">
            </label>
            <label for="level-advancement-hp-choice">生命值增加方式</label>
            <select id="level-advancement-hp-choice" name="hpChoiceAlgorithm">
                <option value="FIXED_AVERAGE">固定平均值</option>
                <option value="SERVER_ROLL">服务器掷生命骰</option>
            </select>
            <fieldset>
                <legend>ASI（只在本职业 ASI 等级填写；总增量必须为 2）</legend>
                <label>力量 <input name="asi.strength" type="number" min="1" max="2"></label>
                <label>敏捷 <input name="asi.dexterity" type="number" min="1" max="2"></label>
                <label>体质 <input name="asi.constitution" type="number" min="1" max="2"></label>
                <label>智力 <input name="asi.intelligence" type="number" min="1" max="2"></label>
                <label>感知 <input name="asi.wisdom" type="number" min="1" max="2"></label>
                <label>魅力 <input name="asi.charisma" type="number" min="1" max="2"></label>
            </fieldset>
            <label>专长键（替代 ASI；当前仅 feat.grappler）
                <input name="featKey" type="text" placeholder="feat.grappler">
            </label>
            <label>多职业熟练选择键（逗号分隔）
                <input name="proficiencyChoices" type="text">
            </label>
            <button id="level-advancement-preview" type="submit">服务器预览</button>
            <button id="level-advancement-confirm" type="button" disabled>确认升级</button>
        </form>
        <p id="level-advancement-result" role="status" aria-live="polite"></p>

        <hr>
        <h2>修改角色</h2>
        <p>按当前版本执行改名、类型切换、归档或恢复；成功后会生成内部审计事件。</p>
        <form id="character-lifecycle-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/characters/lifecycle">
            <label for="lifecycle-character-key">角色编号</label>
            <input id="lifecycle-character-key" name="characterKey" type="text" required>

            <label for="lifecycle-row-version">当前版本</label>
            <input id="lifecycle-row-version" name="rowVersion" type="number" min="0" required>

            <label for="lifecycle-action">操作</label>
            <select id="lifecycle-action" name="action">
                <option value="RENAME">改名</option>
                <option value="CHANGE_TYPE">切换 PC/NPC</option>
                <option value="ARCHIVE">归档</option>
                <option value="RESTORE">恢复</option>
            </select>

            <label for="lifecycle-value">新名称或类型</label>
            <input id="lifecycle-value" name="value" type="text">

            <button type="submit">修改角色</button>
        </form>
        <p id="lifecycle-result" role="status" aria-live="polite"></p>

        <hr>
        <h2>简化角色卡与物品</h2>
        <p>加载角色后可修改权威基础字段、职业等级、技能/豁免熟练和简单物品；派生值只读。</p>
        <form id="character-card-load-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/characters/card">
            <label for="card-character-key">角色编号</label>
            <input id="card-character-key" name="characterKey" type="text" required>
            <button type="submit">加载角色卡</button>
        </form>
        <p id="character-card-result" role="status" aria-live="polite"></p>

        <section id="character-card-panel" hidden>
            <h3 id="character-card-title"></h3>
            <p id="character-card-summary"></p>

            <h3>基础字段</h3>
            <div id="character-card-fields"></div>

            <h3>职业等级</h3>
            <div id="character-card-classes"></div>

            <h3>技能</h3>
            <div id="character-card-skills"></div>

            <h3>豁免</h3>
            <div id="character-card-saves"></div>

            <h3>简单物品</h3>
            <div id="character-card-items"></div>

            <form id="add-module-item-form">
                <label for="module-item-key">内置物品</label>
                <select id="module-item-key" name="itemKey"></select>
                <label for="module-item-quantity">数量</label>
                <input id="module-item-quantity" name="quantity"
                       type="number" min="1" max="999" value="1" required>
                <button type="submit">添加内置物品</button>
            </form>

            <form id="add-temporary-item-form">
                <label for="temporary-item-name">临时物品名称</label>
                <input id="temporary-item-name" name="itemName" type="text" required>
                <label for="temporary-item-description">说明</label>
                <input id="temporary-item-description" name="itemDescription" type="text">
                <label for="temporary-item-quantity">数量</label>
                <input id="temporary-item-quantity" name="quantity"
                       type="number" min="1" max="999" value="1" required>
                <button type="submit">添加临时物品</button>
            </form>
        </section>

        <hr>
        <h2>检定事件</h2>
        <form id="host-event-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/events/check">
            <label for="host-executor">执行者角色键</label>
            <input id="host-executor" name="executorCharacterKey" type="text"
                   pattern="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                   autocomplete="off" required>

            <label for="host-executor-version">执行者当前版本</label>
            <input id="host-executor-version" name="executorExpectedRowVersion"
                   type="number" min="0" step="1" required>

            <label for="host-check-type">检定类型</label>
            <select id="host-check-type" name="checkType">
                <option value="ABILITY">属性</option>
                <option value="SKILL">技能</option>
                <option value="SAVING_THROW">豁免</option>
                <option value="MANUAL">手动</option>
            </select>

            <label id="host-source-label" for="host-source">检定来源</label>
            <select id="host-source" name="modifierSourceKey"></select>

            <div id="host-manual-fields" hidden>
                <label for="host-manual-name">手动检定名称</label>
                <input id="host-manual-name" name="manualName" type="text"
                       maxlength="80" autocomplete="off">
                <label for="host-manual-modifier">手动修正</label>
                <input id="host-manual-modifier" name="manualModifier" type="number"
                       min="-99" max="99" step="1">
            </div>

            <label for="host-roll-mode">掷骰模式</label>
            <select id="host-roll-mode" name="rollModeKey">
                <option value="roll.normal">普通</option>
                <option value="roll.advantage">优势</option>
                <option value="roll.disadvantage">劣势</option>
            </select>

            <label for="host-dc">难度等级 DC</label>
            <input id="host-dc" name="difficultyClass" type="number"
                   min="0" max="60" step="1" value="10" required>

            <fieldset>
                <legend>成功分支批准效果</legend>
                <label><input type="checkbox" name="successEffects"
                              value="effect.adjust_current_hp">调整当前 HP</label>
                <label><input type="checkbox" name="successEffects"
                              value="effect.grant_module_item">授予内置物品</label>
                <label><input type="checkbox" name="successEffects"
                              value="effect.grant_temporary_item">授予临时物品</label>
                <label><input type="checkbox" name="successEffects"
                              value="effect.set_entity_position">设置实体节点</label>
                <label><input type="checkbox" name="successEffects"
                              value="effect.append_event_message">追加事件消息</label>
                <div id="host-success-effect-inputs"></div>
            </fieldset>

            <fieldset>
                <legend>失败分支批准效果</legend>
                <label><input type="checkbox" name="failureEffects"
                              value="effect.adjust_current_hp">调整当前 HP</label>
                <label><input type="checkbox" name="failureEffects"
                              value="effect.grant_module_item">授予内置物品</label>
                <label><input type="checkbox" name="failureEffects"
                              value="effect.grant_temporary_item">授予临时物品</label>
                <label><input type="checkbox" name="failureEffects"
                              value="effect.set_entity_position">设置实体节点</label>
                <label><input type="checkbox" name="failureEffects"
                              value="effect.append_event_message">追加事件消息</label>
                <div id="host-failure-effect-inputs"></div>
            </fieldset>

            <label for="host-targets">可能目标角色键及当前版本</label>
            <textarea id="host-targets" name="targetCharacterVersions" rows="4"
                      placeholder="每行：character_key,expected_row_version"></textarea>

            <button type="submit">执行检定事件</button>
        </form>
        <p id="host-event-result" role="status" aria-live="polite"></p>

        <hr>
        <h2>节点地图与遭遇</h2>
        <p>初始化内置酒馆—地窖节点图，或按角色当前版本直接移动一个角色。</p>
        <form id="host-encounter-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/maps/encounter">
            <label for="host-party-node">队伍节点</label>
            <select id="host-party-node" name="partyNodeKey">
<% if (overview != null) { for (HostOverviewRepository.MapNode node : overview.map().nodes()) { %>
                <option value="<%= HtmlSupport.escape(node.nodeKey()) %>"><%= HtmlSupport.escape(node.displayName()) %></option>
<% }} %>
            </select>
            <label for="host-participants">参与者</label>
            <textarea id="host-participants" name="participants" rows="5"
                      placeholder="每行：character_key,ALLY|ENEMY|NEUTRAL,node_key"></textarea>
            <button type="submit">初始化地图与遭遇</button>
        </form>
        <p id="host-encounter-result" role="status" aria-live="polite"></p>

        <form id="host-position-form"
              data-endpoint="<%= request.getContextPath() %>/api/host/maps/position">
            <label for="host-position-character">角色键</label>
            <input id="host-position-character" name="characterKey" type="text" required>
            <label for="host-position-version">角色当前版本</label>
            <input id="host-position-version" name="rowVersion" type="number"
                   min="0" step="1" required>
            <label for="host-position-node">目标节点</label>
            <select id="host-position-node" name="nodeKey">
<% if (overview != null) { for (HostOverviewRepository.MapNode node : overview.map().nodes()) { %>
                <option value="<%= HtmlSupport.escape(node.nodeKey()) %>"><%= HtmlSupport.escape(node.displayName()) %></option>
<% }} %>
            </select>
            <button type="submit">移动角色</button>
        </form>
        <p id="host-position-result" role="status" aria-live="polite"></p>
    </main>
</body>
</html>
