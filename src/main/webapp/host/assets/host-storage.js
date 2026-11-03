'use strict';
(() => {
  const endpoint = '/api/host/storage/';
  const byId = id => document.getElementById(id);
  let view = null;
  let busy = false;
  let lastSaved = null;
  let manualSelected = null;
  const messages = {
    STORAGE_UNAVAILABLE: '战役存储暂不可用，请检查应用配置。',
    RUN_EXISTS: '已有战役，请先保存并结束当前战役。',
    RESIDUAL_RUN_EXISTS: '有未结束的战役，请先恢复。',
    RESIDUAL_RUN: '有未结束的战役，请先恢复。',
    PREPARATION_INCOMPLETE: '本次准备尚未保存完整输入，无法恢复；请精确放弃所选残留后重新创建或打开。',
    STORAGE_INTEGRITY: '战役材料未通过一致性核验，现有文件和运行已保留。请检查存储材料。',
    FILE_CONFLICT: '这个文件身份已经存在，或暂存材料不一致。现有文件已保留。',
    INVALID_FILE: '文件或字段未通过完整校验，请检查所选文件和输入。',
    INVALID_DOMAIN_DOCUMENT: '操作字段与当前支持的规则不符，请检查选择。',
    DOMAIN_INVALID: '此操作不满足当前规则或状态，请检查角色、装备与待处理行动。',
    DOMAIN_REJECTED: '此操作不满足当前规则或状态，请检查角色、装备、轮次与待处理行动。',
    SCENE_ACTIVE: '请通过当前战斗或探索场景处理这项操作，关闭场景后再修改队伍与位置。',
    STALE_COMBAT: '战斗已更新，已读取当前轮次与待处理行动。',
    INACTIVE_ROSTER: '请先恢复所有离队角色，再开始共享场景。',
    ROSTER_ALREADY_STARTED: '时间已经开始推进，当前模型需要先创建完整队伍。',
    CLEANUP_BLOCKED: '清理尚未完成。未知文件或链接已保留，请检查存储目录。',
    RUN_DISCARDED: '这个运行已经开始放弃，请继续“放弃未保存进度”。',
    RUN_NOT_FOUND: '所选未结束战役已经不存在，请重新读取。',
    RUN_LOCKED: '这个未结束战役仍有其他进程使用，请先关闭对应进程。',
    STALE_EPOCH: '页面已经过期，已重新读取战役。',
    STALE_VERSION: '战役已更新，已重新读取最新进度。',
    STALE_REVISION: '战役已更新，已重新读取最新进度。',
    STALE_CHARACTER: '角色已更新，已重新读取最新生命值。',
    HEALING_RULE_REQUIRED: '创建战役时需要选择治疗模块。',
    RULE_NOT_SELECTED: '创建战役时需要选择治疗模块。',
    RUN_BLOCKED: '当前战役需要恢复，请使用恢复战役继续。',
    SAVE_PENDING: '上次保存尚未完成，请恢复当前战役。',
    INVALID_HP: '当前生命值应在 1 与上限之间，上限最多为 65535。',
    INVALID_AMOUNT: '治疗量应为 0 至 65535 的整数。',
    IDEMPOTENCY_CONFLICT: '本次操作与已接受的操作不一致，请刷新后重试。',
    INVALID_REQUEST: '请检查输入和操作字段，必要时刷新后重试。'
  };
  const message = text => { byId('storage-message').textContent = text; };
  function options(select, items, label) {
    const selected = new Set(Array.from(select.selectedOptions, option => option.value));
    select.replaceChildren();
    if (select.hasAttribute('data-optional') && !select.multiple) {
      const empty = document.createElement('option'); empty.value = ''; empty.textContent = '未选择'; select.append(empty);
    }
    for (const [index, item] of items.entries()) {
      const option = document.createElement('option'); option.value = item;
      option.textContent = label(item, index); option.selected = selected.has(item); select.append(option);
    }
  }
  function downloadLink(link, key) {
    link.hidden = !key;
    if (key) link.href = endpoint + 'download?fileKey=' + encodeURIComponent(key);
    else link.removeAttribute('href');
  }
  function render() {
    const active = view.campaign !== null;
    byId('storage-idle').hidden = active;
    byId('storage-active').hidden = !active;
    const modules = byId('storage-modules');
    if (modules.childElementCount === 0) {
      for (const module of view.modules) {
        const label = document.createElement('label'), input = document.createElement('input');
        input.type = 'checkbox'; input.name = 'modules'; input.value = module.id; input.checked = true;
        if (module.requires.length === 0) { input.required = true; input.disabled = true; }
        label.append(input, document.createTextNode(module.name)); modules.append(label);
      }
    }
    const recoverable = [...view.storage.residualRunIds];
    if (!active && view.storage.runId && !recoverable.includes(view.storage.runId)) recoverable.push(view.storage.runId);
    const saved = new Map((view.savedCampaigns || []).map(file => [file.fileKey, file]));
    options(byId('storage-files'), view.storage.saveFiles, (id, i) => {
      const file = saved.get(id); return file ? `${file.name} · ${file.eventTail} 条记录` : `已存战役 ${i + 1}`;
    });
    options(byId('storage-residuals'), recoverable, (_, i) => `未结束战役 ${i + 1}`);
    byId('storage-open').querySelector('button').disabled = view.storage.state !== 'IDLE' || view.storage.saveFiles.length === 0;
    byId('storage-recover').querySelector('button').disabled = recoverable.length === 0;
    byId('storage-create').querySelector('button').disabled = view.storage.state !== 'IDLE';
    byId('storage-import').querySelector('button').disabled = view.storage.state !== 'IDLE';
    byId('storage-delete').disabled = view.storage.state !== 'IDLE' || view.storage.saveFiles.length === 0;
    byId('storage-discard').disabled = recoverable.length === 0;
    downloadLink(byId('storage-download'), view.storage.state === 'IDLE' ? byId('storage-files').value : null);
    downloadLink(byId('storage-last-download'), lastSaved && view.storage.saveFiles.includes(lastSaved) ? lastSaved : null);
    if (!active) return;
    const campaign = view.campaign;
    byId('storage-campaign-name').textContent = campaign.name;
    byId('storage-vitality').hidden = Boolean(campaign.domain);
    byId('storage-domain').hidden = !campaign.domain;
    if (campaign.domain) { renderDomain(campaign.domain); return; }
    const characters = byId('storage-characters'); characters.replaceChildren();
    for (const character of campaign.characters) {
      const card = document.createElement('article'); card.className = 'card';
      const name = document.createElement('strong'); name.textContent = character.name;
      const hp = document.createElement('span'); hp.className = 'hp'; hp.textContent = `${character.currentHp} / ${character.maximumHp}`;
      card.append(name, hp); characters.append(card);
    }
    options(byId('storage-heal-character'), campaign.characters.map(c => c.id), id => campaign.characters.find(c => c.id === id).name);
    const healing = campaign.rules.some(rule => (typeof rule === 'string' ? rule : rule.id) === 'srd51-healing@1');
    byId('storage-heal').querySelector('button').disabled = !healing || campaign.characters.length === 0;
    byId('storage-healing-note').textContent = healing ? '治疗后的生命值最多达到上限。' : '此战役未启用治疗模块。';
    const history = byId('storage-history'); history.replaceChildren();
    for (const event of campaign.history) {
      const item = document.createElement('li');
      const character = campaign.characters.find(c => c.id === event.characterId);
      item.textContent = event.type === 'CHARACTER_CREATED'
        ? `${event.name || character?.name || '角色'} 加入战役，生命值 ${event.afterHp} / ${event.maximumHp}。`
        : `${character?.name || '角色'} 接受治疗：${event.beforeHp} → ${event.afterHp}。`;
      history.append(item);
    }
  }
  const abilities = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma'];
  const abilityNames = ['力量', '敏捷', '体质', '智力', '感知', '魅力'];
  const battleStatus = value => ({ACTIVE: '战斗中', EXPLORATION: '探索', ENDED: '已关闭'})[value] || '场景';
  const locationName = value => ({CARRIED: '携带', WORN: '穿戴', MAIN_HAND: '主手', OFF_HAND: '副手', BOTH_HANDS: '双手', GROUND: '地面'})[value] || value;
  const ruleCategory = value => ({'character.class': '职业', 'character.class_progression': '职业成长', 'character.race': '种族', 'character.background': '背景',
    'character.subclass': '子职', 'character.feature': '职业特性', 'character.feat': '专长', 'character.resource': '资源', 'character.proficiency': '技能熟练',
    'character.language': '语言', 'character.tool': '工具', 'character.rest': '休息', 'equipment.item': '装备', 'adventure.check': '检定', 'adventure.health': '健康',
    'adventure.vital_effect': 'DM 生命效果', 'adventure.combat': '战斗', 'adventure.weapon_attack': '攻击', 'adventure.movement': '移动', 'adventure.spell': '法术',
    'adventure.spellcasting': '施法', 'character.wizard_spellcasting': '法师施法', 'adventure.environment': '环境', 'adventure.monster': '怪物'})[value] || '规则资料';
  const domain = () => view.campaign?.domain;
  const roster = () => domain()?.characters || [];
  const manualData = () => domain()?.manual;
  const manualCards = () => (manualData()?.characters || []).map(history => history.current);
  const allPeople = () => [...roster(), ...manualCards()];
  const selectedCharacter = form => roster().find(character => character.id === form.elements.characterId.value);
  const catalog = () => domain()?.rules?.catalog || [];
  const ruleValue = rule => `${rule.moduleKey}/${rule.type || rule.ruleType}/${rule.key || rule.ruleKey}`;
  const ruleFromValue = value => catalog().find(rule => ruleValue(rule) === value);
  function typedRule(value) {
    const rule = typeof value === 'string' ? ruleFromValue(value) : value;
    return rule ? {type: 'rule.RuleIdentity', moduleKey: rule.moduleKey, ruleType: rule.ruleType || rule.type, ruleKey: rule.ruleKey || rule.key} : null;
  }
  function ruleName(identity) {
    if (!identity) return '无';
    const found = catalog().find(rule => rule.moduleKey === identity.moduleKey && (rule.type || rule.ruleType) === identity.ruleType && (rule.key || rule.ruleKey) === identity.ruleKey);
    return found?.name || identity.ruleKey;
  }
  function personName(id) {
    const character = allPeople().find(character => character.id === id);
    if (character) return character.name;
    for (const history of domain()?.combats || []) {
      const monster = history.current.monsters?.find(monster => monster.monsterId === id);
      if (monster) return ruleName(monster.monster);
    }
    return '生物';
  }
  function text(parent, tag, value, className) {
    const element = document.createElement(tag); element.textContent = value;
    if (className) element.className = className;
    parent.append(element); return element;
  }
  function table(parent, headings, rows) {
    const wrapper = document.createElement('div'); wrapper.className = 'table-scroll';
    const element = document.createElement('table'), head = document.createElement('thead'), heading = document.createElement('tr');
    headings.forEach(value => text(heading, 'th', value)); head.append(heading); element.append(head);
    const body = document.createElement('tbody');
    rows.forEach(values => { const row = document.createElement('tr'); values.forEach(value => text(row, 'td', String(value ?? '—'))); body.append(row); });
    element.append(body); wrapper.append(element); parent.append(wrapper);
  }
  function refreshRuleChoices() {
    for (const select of document.querySelectorAll('[data-rules]')) {
      const types = select.dataset.rules.split(',');
      const entries = catalog().filter(rule => types.includes(rule.type || rule.ruleType)
        && (['character.class', 'character.background', 'equipment.item', 'adventure.monster'].includes(rule.type || rule.ruleType)
          || select.closest('#domain-spellbook') || rule.executable !== false));
      options(select, entries.map(ruleValue), value => ruleFromValue(value).name || ruleFromValue(value).key);
    }
    for (const select of document.querySelectorAll('.ability-choice')) options(select, abilities.map(key => 'ability.' + key), key => abilityNames[abilities.indexOf(key.substring(8))]);
    for (const select of document.querySelectorAll('.domain-character')) {
      const shared = ['domain-position', 'domain-item'].includes(select.form?.id);
      options(select, (shared ? allPeople() : roster()).map(character => character.id), personName);
    }
    for (const select of document.querySelectorAll('.node-choice')) options(select, (domain().nodes || []).map(node => node.id), id => domain().nodes.find(node => node.id === id).name);
    refreshCharacterChoices();
  }
  function refreshCharacterChoices() {
    for (const form of document.querySelectorAll('#storage-domain form')) {
      const character = form.elements.characterId ? allPeople().find(character => character.id === form.elements.characterId.value) : roster().find(item => item.id === form.elements.actorId?.value);
      const equipment = character?.state?.equipment || [];
      for (const select of form.querySelectorAll('.equipment-choice')) options(select, equipment.map(item => item.itemId), id => {
        const item = equipment.find(item => item.itemId === id); return `${ruleName(item.item)} · ${locationName(item.location)}`;
      });
      const items = (domain()?.items || []).filter(item => item.characterId === character?.id);
      for (const select of form.querySelectorAll('.world-item-choice')) options(select, items.map(item => item.id), id => {
        const item = items.find(item => item.id === id); return `${item.name} × ${item.quantity}`;
      });
      if (form.id === 'domain-profile' && character) { form.elements.name.value = character.name; form.elements.kind.value = character.kind; form.elements.active.checked = character.active; }
      if (form.elements.combatId) {
        const battle = battleFor(form.elements.combatId.value), monster = battle?.monsters?.find(monster => monster.monsterId === form.elements.actorId?.value);
        for (const select of form.querySelectorAll('.breath-choice')) options(select, (monster?.actionUses || []).map((_, index) => String(index)), value => {
          const action = monster.actionUses[Number(value)]; return `${action.action.entryKey}${action.available ? ' · 可用' : ' · 已用'}`;
        });
      }
    }
    refreshCheckBranches();
    refreshManualEffects();
    refreshArcaneRecovery();
  }
  function refreshArcaneRecovery() {
    const form = byId('domain-rest'), character = selectedCharacter(form), current = character?.state;
    const active = current?.rest?.active, fields = byId('arcane-recovery-fields');
    const enabled = form.elements.kind.value === 'REST_COMPLETED' && active?.kind === 'SHORT_REST' && active.phase === 'IN_PROGRESS';
    fields.hidden = !enabled; fields.disabled = !enabled;
    let recoverable = false;
    for (const input of fields.querySelectorAll('[data-recover-slot]')) {
      const resource = current?.resources?.find(resource => resource.identity.ruleKey === `resource.wizard.slot${input.dataset.recoverSlot}`);
      const missing = resource && !resource.unlimited ? Math.max(0, Number(resource.maximum) - Number(resource.current)) : 0;
      input.disabled = !enabled || missing === 0; input.closest('label').hidden = missing === 0;
      input.max = String(Math.min(4, missing));
      if (input.disabled) input.value = '0';
      if (missing > 0) recoverable = true;
    }
    const day = Math.floor((Number(current?.rest?.minute || 0) * 60 + Number(current?.rest?.second || 0)) / 86400);
    const used = current?.spellbook?.lastArcaneRecoveryDay;
    const wizardLevel = Number(current?.classes?.find(entry => entry.characterClass.ruleKey === 'class.wizard')?.level || 0);
    byId('arcane-recovery-status').textContent = !current?.spellbook ? '尚未建立法术书；可放弃本次收益并完成短休。'
      : `当前为战役第 ${day} 日；${used !== null && used !== undefined && Number(used) === day ? '本日已使用奥术回想，可放弃本次收益并完成短休。'
        : recoverable ? `本次恢复预算最多 ${Math.ceil(wizardLevel / 2)} 环级。` : '没有已消耗的法术位。'}`;
  }
  function arcaneRecoveryResult(event) {
    if (!Object.keys(event?.command?.restInput?.recoveredSpellSlots || {}).length) return '';
    const changes = (event.after.resources || []).flatMap(resource => {
      const before = event.before.resources.find(previous => ruleValue(previous.identity) === ruleValue(resource.identity));
      const recovered = Number(resource.current) - Number(before?.current);
      return recovered > 0 ? [`${ruleName(resource.identity)} ${before.current} → ${resource.current}（恢复 ${recovered}）`] : [];
    });
    return `奥术回想：${changes.join('；')}；使用日：第 ${event.after.spellbook.lastArcaneRecoveryDay} 日`;
  }
  function refreshCheckBranches() {
    if (!domain()) return;
    const form = byId('domain-check'), executor = selectedCharacter(form), enabled = form.elements.withEffects.checked;
    byId('check-branches-note').hidden = !enabled;
    const active = roster().filter(character => character.active);
    for (const branch of form.querySelectorAll('[data-check-branch]')) {
      branch.hidden = !enabled; branch.disabled = !enabled;
      const select = branch.querySelector('.check-branch-character'), previous = select.value;
      options(select, active.map(character => character.id), personName);
      if (!active.some(character => character.id === previous) && executor?.active) select.value = executor.id;
      const items = (domain().items || []).filter(item => item.characterId === select.value);
      options(branch.querySelector('.check-branch-item'), items.map(item => item.id), id => {
        const item = items.find(item => item.id === id); return `${item.name} · 当前 ${item.quantity}`;
      });
    }
  }
  function renderDomain(state) {
    refreshRuleChoices();
    const cards = byId('storage-characters'); cards.replaceChildren();
    for (const character of state.characters) {
      const card = document.createElement('article'); card.className = 'card character-card'; const current = character.state;
      text(card, 'strong', `${character.name} · ${character.kind === 'NPC' ? 'NPC' : '角色'}${character.active ? '' : ' · 离队'}`);
      text(card, 'span', `${character.hitPoints} / ${character.maximumHitPoints}`, 'hp');
      text(card, 'p', `${character.level} 级 · 熟练 +${current.proficiencyBonus} · ${current.health?.dead ? '已死亡' : current.health?.stable ? '稳定' : '存活'} · 临时生命值 ${current.health?.temporaryHitPoints || 0}`);
      table(card, abilityNames, [abilities.map(key => current.abilities?.['ability.' + key] ?? '—')]);
      text(card, 'p', `职业：${(current.classes || []).map(entry => `${ruleName(entry.characterClass)} ${entry.level}`).join('、')}`);
      text(card, 'p', `资源：${(current.resources || []).map(resource => `${ruleName(resource.identity)} ${resource.unlimited ? '不限' : `${resource.current}/${resource.maximum}`}`).join('；') || '无'}`);
      text(card, 'p', `生命骰：${(current.hitDicePools || []).map(pool => `${pool.current}/${pool.maximum} d${pool.sides}`).join('；') || '无'}`);
      text(card, 'p', `装备：${(current.equipment || []).map(item => `${ruleName(item.item)}（${locationName(item.location)}）`).join('；') || '无'}`);
      text(card, 'p', `特性：${(current.features || []).map(feature => ruleName(feature.feature)).join('；') || '无'}`);
      text(card, 'p', `熟练：${(current.proficiencies || []).map(proficiency => ruleName(proficiency.target)).join('；') || '无'}`);
      if (current.rest?.active) text(card, 'p', `休息中：${current.rest.active.kind || current.rest.active.restKind} · ${current.rest.active.phase}`);
      if (current.spellbook) {
        text(card, 'p', `已准备法术：${current.spellbook.prepared.map(ruleName).join('、') || '无'}`);
        const day = current.spellbook.lastArcaneRecoveryDay;
        text(card, 'p', day === null || day === undefined ? '奥术回想：尚未使用' : `奥术回想使用日：第 ${day} 日`);
      }
      const recentRecovery = character.history?.events?.findLast(event => Object.keys(event.command?.restInput?.recoveredSpellSlots || {}).length);
      if (recentRecovery) text(card, 'p', `最近${arcaneRecoveryResult(recentRecovery)}`);
      const recentCheck = character.history?.events?.findLast(event => event.checkResult);
      if (recentCheck) { const result = recentCheck.checkResult; text(card, 'p', `最近检定：d20 ${result.selectedD20} + 属性 ${result.abilityModifier} + 熟练 ${result.proficiencyBonus} = ${result.total}，${result.success ? '成功' : '失败'}`); }
      const recentHealth = character.history?.events?.findLast(event => event.healthResult);
      if (recentHealth) { const result = recentHealth.healthResult; text(card, 'p', `最近健康操作：${recentHealth.before?.hitPoints ?? '—'} → ${recentHealth.after.hitPoints}；掷骰 ${[...(result.dice || []), result.deathD20, result.recoveryD4].filter(value => value !== null && value !== undefined).join('、') || '无'}`); }
      cards.append(card);
    }
    const history = byId('storage-history'); history.replaceChildren();
    for (const root of state.roots || []) {
      if (root.kind === 'manual-check') {
        const audit = JSON.parse(root.after), result = audit.result, details = [`${root.sequence}. 自由卡检定 · ${personName(root.subjectId)} · ${result.success ? '成功' : '失败'}`];
        details.push(`d20 ${result.dice.join(' / ')}，选用 ${result.dice[Number(result.selectedIndex)]}；调整值 ${result.modifier}；总值 ${result.total}`);
        for (const change of audit.cards || []) {
          const before = change.before?.current, after = change.after?.current;
          if (before && after && before.fields['hp.current'] !== after.fields['hp.current']) details.push(`${after.name} 生命值 ${before.fields['hp.current']} → ${after.fields['hp.current']}`);
        }
        for (const change of audit.items || []) if (change.after) details.push(`${change.after.name} 数量 ${change.before?.quantity || 0} → ${change.after.quantity}`);
        for (const change of audit.positions || []) if (change.after) details.push(`${personName(change.after.characterId)} 到达 ${state.nodes.find(node => node.id === change.after.nodeId)?.name || '地点'}`);
        const request = result.request, chosen = result.success ? request.success || request.successEffects : request.failure || request.failureEffects;
        if (chosen?.length) details.push(`应用顺序：${chosen.map(effect => effect.amount !== undefined ? `生命值 ${effect.amount}` : effect.nodeId ? '设置位置' : effect.itemKey || effect.quantity ? '给予物品' : '记录消息').join(' → ')}`);
        for (const effect of chosen || []) if (typeof effect.text === 'string' && effect.text) details.push(effect.text);
        text(history, 'li', details.join('；'));
      } else if (root.kind === 'check-effects') {
        const audit = JSON.parse(root.after), details = [`${root.sequence}. 检定及后果 · ${personName(root.subjectId)} · ${audit.success ? '成功' : '失败'}`];
        if (audit.healthOperationId) {
          for (const character of roster()) {
            const event = character.history.events.find(event => event.operationId === audit.healthOperationId);
            if (event) details.push(`${character.name} 生命值 ${event.before.hitPoints} → ${event.after.hitPoints}`);
          }
        }
        if (audit.itemAfter) details.push(`${audit.itemAfter.name} 数量 ${audit.itemBefore.quantity} → ${audit.itemAfter.quantity}`);
        if (audit.positionAfter) {
          const nodeName = id => state.nodes.find(node => node.id === id)?.name || '未标记';
          details.push(`${personName(audit.positionAfter.characterId)} 位置 ${nodeName(audit.positionBefore?.nodeId)} → ${nodeName(audit.positionAfter.nodeId)}`);
        }
        if (audit.message) details.push(audit.message);
        text(history, 'li', details.join('；'));
      } else {
        const event = roster().find(character => character.id === root.subjectId)?.history?.events?.find(event => event.operationId === root.operationId);
        const recovery = arcaneRecoveryResult(event);
        text(history, 'li', `${root.sequence}. ${operationName(root.kind)}${allPeople().some(character => character.id === root.subjectId) ? ` · ${personName(root.subjectId)}` : ''}${root.kind === 'note' ? ` · ${root.after}` : ''}${recovery ? ` · ${recovery}` : ''}`);
      }
    }
    const world = byId('domain-world-summary'); world.replaceChildren();
    text(world, 'h3', '场景概览');
    table(world, ['地点', '相邻地点', '当前角色'], (state.nodes || []).map(node => [node.name,
      (node.adjacent || []).map(id => state.nodes.find(other => other.id === id)?.name || '未知地点').join('、'),
      (state.positions || []).filter(position => position.nodeId === node.id).map(position => personName(position.characterId)).join('、')]));
    table(world, ['物品记录', '持有人', '数量'], (state.items || []).map(item => [item.name, personName(item.characterId), item.quantity]));
    const rules = byId('domain-rule-catalog'); rules.replaceChildren();
    table(rules, ['规则', '类别', '当前用途'], catalog().map(rule => [rule.name, ruleCategory(rule.type), rule.executable ? '支持自动处理' : rule.description || '资料说明']));
    const limitations = state.rules?.limitations;
    byId('domain-limitations').textContent = Array.isArray(limitations) ? limitations.join('；') : limitations || '';
    renderCombat(state);
    renderManual();
  }
  function operationName(kind) {
    return ({'character-create': '创建角色', 'character-profile': '调整角色资料', 'character-command': '角色操作',
      'combat-command': '战斗行动', 'node-create': '建立地点', 'node-link': '连接地点', position: '调整位置', item: '物品记录', note: '便笺',
      'manual-create': '创建自由卡', 'manual-profile': '更新自由卡资料', 'manual-field': '更新自由卡数值', 'manual-class': '更新自由卡职业等级',
      'manual-proficiency': '更新自由卡熟练', 'manual-item': '更新自由卡物品', 'manual-encounter-start': '开始简易遭遇', 'manual-encounter-members': '更新遭遇名单',
      'manual-encounter-close': '结束简易遭遇', 'manual-party-position': '移动队伍',
      CHARACTER_CREATED: '创建角色', CHARACTER_PROFILE: '调整角色资料', CHARACTER_ADVANCED: '角色升级',
      CHECK_RESOLVED: '检定', HEALTH_EFFECT_RESOLVED: '健康效果', ITEM_ACQUIRED: '取得装备', ITEM_EQUIPPED: '装备物品',
      NOTE: '便笺', NODE_CREATED: '建立地点', NODE_LINKED: '连接地点', POSITION: '调整位置', ITEM: '物品记录', COMBAT: '战斗行动'})[kind] || kind;
  }
  const battleFor = id => domain()?.combats?.find(history => history.current.combatId === id)?.current;
  function baseCombat(kind, actorId = null) {
    return {type: 'scene.Command', kind, actorId, targetId: null, itemId: null, path: [], deployments: [], tieBreakOrder: [],
      flowKey: null, leafKey: null, windowKey: null, opportunityKey: null, castInput: null, readyInput: null, readyKey: null,
      zoneInput: null, zoneKey: null, healthInput: null, seconds: '0', monsterDeployments: [], breathInput: null, monsterRestInput: null};
  }
  function sendCombat(battle, command) { return domainCommand('combat-command', {combatId: battle?.combatId || null, combatVersion: String(battle?.version ?? -1), command}); }
  function pendingButton(parent, label, battle, command) {
    const button = text(parent, 'button', label); button.type = 'button'; button.addEventListener('click', () => sendCombat(battle, command));
  }
  function renderCombat(state) {
    const combats = state.combats || [], summary = byId('domain-combat-summary'), pending = byId('domain-pending'); summary.replaceChildren(); pending.replaceChildren();
    const deployments = byId('domain-deployments');
    const previous = new Map(Array.from(deployments.querySelectorAll('[data-character]'), row => [row.dataset.character, {
      checked: row.querySelector('[name="join"]').checked, x: row.querySelector('[name="x"]').value, y: row.querySelector('[name="y"]').value, side: row.querySelector('[name="side"]').value}]));
    deployments.replaceChildren();
    for (const [index, character] of state.characters.filter(character => character.active).entries()) {
      const row = document.createElement('div'); row.className = 'deployment'; row.dataset.character = character.id;
      const old = previous.get(character.id);
      const label = document.createElement('label'), join = document.createElement('input'); join.name = 'join'; join.type = 'checkbox'; join.checked = old?.checked ?? true;
      label.append(join, document.createTextNode(character.name)); row.append(label);
      for (const [name, title, value] of [['side', '阵营', old?.side ?? (index === 0 ? 0 : 1)], ['x', 'X', old?.x ?? index * 2], ['y', 'Y', old?.y ?? 0]]) {
        const field = text(row, 'label', title), input = document.createElement('input'); input.name = name; input.type = 'number'; input.value = value;
        input.min = name === 'side' ? '0' : '-10000'; input.max = name === 'side' ? '15' : '10000'; input.required = true; field.append(input);
      }
      deployments.append(row);
    }
    for (const select of document.querySelectorAll('.combat-choice')) options(select, combats.map(history => history.current.combatId), id => {
      const battle = battleFor(id); return `第 ${combats.findIndex(history => history.current.combatId === id) + 1} 场 · ${battleStatus(battle.status)} · 第 ${battle.round} 轮`;
    });
    refreshCombatActors();
    for (const history of combats) {
      const battle = history.current, box = document.createElement('article'); box.className = 'card';
      const active = battle.initiative?.[battle.activeOrdinal]?.characterId;
      text(box, 'h3', `第 ${battle.round} 轮 · ${battleStatus(battle.status)} · 当前 ${personName(active)}`);
      table(box, ['参与者', '格位', '阵营', '动作', '反应'], battle.combatants.map(actor => [personName(actor.characterId), `${actor.position.x}, ${actor.position.y}`, actor.side,
        actor.actionAvailable ? '可用' : '已用', actor.reactionAvailable ? '可用' : '已用']));
      for (const effect of battle.ongoingEffects || []) text(box, 'p', `效果：${ruleName(effect.spell || effect.rule)} · ${effect.status === 'ACTIVE' ? '生效' : '已结束'}`);
      for (const zone of battle.environmentZones || []) if (!zone.endedOperationId) text(box, 'p', `环境区域：${ruleName(zone.rule)} · 中心 ${zone.center.x}, ${zone.center.y}`);
      for (const monster of battle.monsters || []) text(box, 'p', `${ruleName(monster.monster)} · 生命值 ${monster.hitPoints}${monster.dead ? ' · 已死亡' : ''}`);
      const recentCast = battle.flows.flatMap(flow => flow.casts).findLast(cast => cast.status === 'RESOLVED');
      if (recentCast) {
        const caster = roster().find(character => character.id === recentCast.actorId);
        const slot = caster?.state.resources.find(resource => ruleValue(resource.identity) === ruleValue(recentCast.slotResource));
        const targets = recentCast.targets.map(id => roster().find(character => character.id === id)).filter(Boolean);
        const healing = recentCast.healingDice.length ? `原骰 d8=${recentCast.healingDice.join('、')}，加值 ${recentCast.healingModifier}，实际恢复 ${recentCast.appliedHealing}；` : '';
        text(box, 'p', `${personName(recentCast.actorId)} · ${ruleName(recentCast.spell)}：${healing}${targets.map(character => `${character.name} 当前 ${character.hitPoints}/${character.maximumHitPoints}`).join('；')}；${recentCast.slotLevel} 环法术位当前 ${slot ? `${slot.current}/${slot.maximum}` : '已记录'}`, 'cast-result');
      }
      const latest = history.events?.at(-1);
      if (latest?.resolution?.rolls) text(box, 'p', `本次骰子：${latest.resolution.rolls.map(roll => `${roll.purpose} d${roll.sides}=${roll.value}`).join('；')}`);
      summary.append(box);
      for (const flow of (battle.flows || []).filter(flow => flow.status === 'ACTIVE')) {
        const panel = document.createElement('article'); panel.className = 'card';
        const leaf = flow.frames.find(frame => frame.frameKey === flow.leafFrameKey);
        const actorId = flow.attacks?.find(attack => attack.frameKey === leaf?.frameKey)?.actorId
          || flow.casts?.find(cast => cast.frameKey === leaf?.frameKey)?.actorId
          || flow.movements?.find(movement => movement.movementKey === leaf?.ownerKey)?.actorId
          || battle.saveDamageBatches?.find(batch => batch.frameKey === leaf?.frameKey)?.sourceId || active;
        text(panel, 'h4', `${leaf?.stage || '行动处理中'} · ${leaf?.status || flow.status}`);
        for (const window of flow.windows.filter(window => window.status === 'OPEN')) {
          for (const opportunity of window.opportunities.filter(opportunity => opportunity.status === 'PENDING' && opportunity.selectionFrameKey === leaf?.frameKey)) {
            const row = document.createElement('div'); row.className = 'actions'; text(row, 'span', `${personName(opportunity.actorId)} 的反应：`);
            const base = {...baseCombat('PASS_REACTION', opportunity.actorId), flowKey: flow.flowKey, leafKey: leaf.frameKey, windowKey: window.windowKey, opportunityKey: opportunity.opportunityKey};
            pendingButton(row, '放弃反应', battle, base);
            for (const choice of opportunity.options || []) {
              if (choice.kind === 'OPPORTUNITY_ATTACK') pendingButton(row, `借机攻击 · ${ruleName(roster().find(character => character.id === opportunity.actorId)?.state.equipment.find(item => item.itemId === choice.itemId)?.item)}`, battle, {...base, kind: 'DECLARE_OPPORTUNITY_ATTACK', itemId: choice.itemId});
              else if (choice.kind === 'READY_RELEASE') pendingButton(row, '释放预备动作', battle, {...base, kind: 'RELEASE_READY', readyKey: choice.readyKey});
              else if (['SHIELD', 'COUNTERSPELL'].includes(choice.kind)) pendingButton(row, ruleName(choice.spell), battle, {...base,
                kind: choice.kind === 'SHIELD' ? 'DECLARE_SHIELD' : 'DECLARE_COUNTERSPELL', castInput: {type: 'scene.CastInput', castingRule: typedRule(choice.castingRule), spell: typedRule(choice.spell),
                  targets: [choice.kind === 'SHIELD' ? opportunity.actorId : window.trigger.subjectId], materialItemId: null, slotLevel: String(choice.slotLevel),
                  targetCastFrameKey: choice.kind === 'COUNTERSPELL' ? window.trigger.sourceFrameKey : null, coneDirection: null}});
            }
            panel.append(row);
          }
        }
        const closedLeafWindow = leaf && flow.windows.some(window => window.windowKey === leaf.windowKey && window.ownerFrameKey === leaf.frameKey && window.status === 'CLOSED');
        if (leaf && leaf.kind !== 'REACTION_SELECTION' && (closedLeafWindow || leaf.status !== 'WAITING_WINDOW')) pendingButton(panel, '继续结算行动', battle,
          {...baseCombat(leaf.kind === 'MOVEMENT' ? 'CONTINUE_MOVEMENT' : 'CONTINUE_FLOW', actorId), flowKey: flow.flowKey, leafKey: leaf.frameKey, windowKey: leaf.windowKey});
        pending.append(panel);
      }
    }
    if (!pending.childElementCount) text(pending, 'p', '当前没有待处理行动。');
  }
  function refreshCombatActors() {
    for (const form of document.querySelectorAll('#storage-domain form')) {
      if (!form.elements.combatId) continue;
      const battle = battleFor(form.elements.combatId.value);
      const ids = battle?.combatants.map(actor => actor.characterId) || [];
      for (const select of form.querySelectorAll('.combat-actor-choice')) {
        options(select, ids, personName);
        if (select.name === 'actorId' && battle?.initiative?.[battle.activeOrdinal]) select.value = battle.initiative[battle.activeOrdinal].characterId;
      }
      for (const select of form.querySelectorAll('.monster-choice')) options(select, (battle?.monsters || []).map(monster => monster.monsterId), personName);
      for (const select of form.querySelectorAll('.zone-choice')) options(select, (battle?.environmentZones || []).filter(zone => !zone.endedOperationId).map(zone => zone.zoneKey), id => {
        const zone = battle.environmentZones.find(zone => zone.zoneKey === id); return `${ruleName(zone.rule)} · ${zone.center.x}, ${zone.center.y}`;
      });
    }
    refreshCharacterChoices();
  }
  const tierNames = {NONE: '无', HALF: '半熟练', FULL: '熟练', EXPERTISE: '专精'};
  const effectNames = {HP: '调整当前生命值', GRANT_MODULE_ITEM: '给予目录物品', GRANT_TEMPORARY_ITEM: '给予临时物品', POSITION: '设置角色位置', MESSAGE: '记录结果消息'};
  const manualCatalog = () => manualData()?.catalog;
  const manualEncounter = id => manualData()?.encounters.find(encounter => encounter.id === id);
  function manualPayload(card, extra = {}) {
    if (!card) throw new Error('请先创建或选择自由卡。');
    return {characterId: card.id, characterVersion: String(card.version), ...extra};
  }
  function control(parent, title, name, type, value = '') {
    const label = text(parent, 'label', title), input = document.createElement(type === 'select' ? 'select' : 'input');
    input.name = name;
    if (type !== 'select') { input.type = type; input.value = value; }
    label.append(input); return input;
  }
  function renderManual() {
    const data = manualData(), meta = manualCatalog();
    if (!data || !meta) return;
    refreshManualTemplates();
    for (const select of document.querySelectorAll('.manual-character-choice')) {
      const shared = select.form?.id === 'manual-participant';
      const people = (shared ? allPeople() : manualCards()).filter(card => !['manual-check', 'manual-participant'].includes(select.form?.id) || card.active);
      options(select, people.map(card => card.id), personName);
      if (select.form?.id === 'manual-card-load' && manualSelected && people.some(card => card.id === manualSelected)) select.value = manualSelected;
    }
    const cards = byId('manual-cards'); cards.replaceChildren();
    for (const card of manualCards()) {
      const box = document.createElement('article'); box.className = 'card';
      const totalLevel = Object.values(card.classes).reduce((sum, value) => sum + Number(value), 0), proficiency = Math.max(2, 2 + Math.floor((totalLevel - 1) / 4));
      text(box, 'strong', `${card.name} · ${card.kind === 'PC' ? '自由角色' : '自由 NPC'}${card.active ? '' : ' · 离队'}`);
      text(box, 'span', `${card.fields['hp.current']} / ${card.fields['hp.maximum']}`, 'hp');
      table(box, abilityNames, [abilities.map(key => card.fields['ability.' + key])]);
      text(box, 'p', `防护等级 ${card.fields.armor_class} · 速度 ${card.fields['speed.ground']} 英尺 · 等级 ${totalLevel} · 熟练 +${proficiency}`);
      const items = domain().items.filter(item => item.characterId === card.id);
      text(box, 'p', `物品：${items.map(item => `${item.name} × ${item.quantity}${item.active === false ? '（归档）' : ''}`).join('；') || '无'}`);
      cards.append(box);
    }
    renderManualEditor(); refreshManualCheck(); refreshManualInventory(); renderManualEncounters(); refreshManualEffects();
  }
  function renderManualEditor() {
    const parent = byId('manual-card-editor'); parent.replaceChildren();
    const card = manualCards().find(card => card.id === manualSelected), meta = manualCatalog();
    if (!card || !meta) return;
    text(parent, 'h3', `编辑 ${card.name}`);
    const profile = document.createElement('form'); profile.className = 'manual-profile';
    control(profile, '名称', 'name', 'text', card.name).required = true;
    const kind = control(profile, '类型', 'kind', 'select'); options(kind, ['PC', 'NPC'], key => key === 'PC' ? '玩家角色' : 'NPC'); kind.value = card.kind;
    const active = control(profile, '加入当前队伍', 'active', 'checkbox'); active.checked = card.active;
    const submit = text(profile, 'button', '保存自由卡资料'); submit.type = 'submit';
    profile.addEventListener('submit', event => { event.preventDefault(); domainCommand('manual-profile', manualPayload(card, {name: profile.elements.name.value, kind: profile.elements.kind.value, active: active.checked})); });
    parent.append(profile);
    function editable(container, title, value, action, extra, minimum, maximum, choices) {
      const row = document.createElement('div'); row.className = 'manual-edit-row'; row.dataset.manualTarget = extra.fieldKey || extra.classKey || extra.targetKey;
      const input = control(row, title, 'value', choices ? 'select' : 'number', String(value));
      if (choices) { options(input, choices, key => tierNames[key] || key); input.value = value; }
      else { input.min = String(minimum); input.max = String(maximum); input.required = true; }
      const button = text(row, 'button', '保存'); button.type = 'button';
      button.addEventListener('click', () => {
        if (!input.reportValidity()) return;
        const valueKey = action === 'manual-field' ? 'value' : action === 'manual-class' ? 'level' : 'tier';
        domainCommand(action, manualPayload(card, {...extra, [valueKey]: input.value}));
      });
      container.append(row);
    }
    const fields = document.createElement('div'); fields.className = 'manual-field-grid';
    for (const field of meta.fields) editable(fields, field.name, card.fields[field.key], 'manual-field', {fieldKey: field.key}, field.minimum, field.maximum);
    parent.append(fields);
    for (const [title, definitions, values, action, category] of [['职业等级', meta.classes, card.classes, 'manual-class', null],
      ['技能熟练', meta.skills, card.skills, 'manual-proficiency', 'SKILL'], ['豁免熟练', meta.saves, card.saves, 'manual-proficiency', 'SAVE']]) {
      const details = document.createElement('details'); text(details, 'summary', title); const list = document.createElement('div'); list.className = 'manual-field-grid';
      for (const definition of definitions) editable(list, definition.name, values[definition.key] ?? (category ? 'NONE' : 0), action,
        category ? {category, targetKey: definition.key} : {classKey: definition.key}, 0, 20, category ? meta.tiers : null);
      details.append(list); parent.append(details);
    }
  }
  function refreshManualCheck() {
    const meta = manualCatalog(); if (!meta) return;
    const form = byId('manual-check'), kind = form.elements.checkType.value;
    const entries = kind === 'ABILITY' ? meta.fields.filter(field => field.key.startsWith('ability.')) : kind === 'SKILL' ? meta.skills : kind === 'SAVING_THROW' ? meta.saves : [];
    options(byId('manual-check-source'), entries.map(entry => entry.key), key => entries.find(entry => entry.key === key).name);
    byId('manual-check-source').disabled = kind === 'MANUAL';
    form.elements.manualModifier.min = '-99'; form.elements.manualModifier.max = '99';
    form.elements.manualModifier.disabled = kind !== 'MANUAL'; form.elements.manualName.disabled = kind !== 'MANUAL';
  }
  function refreshManualTemplates() {
    const meta = manualCatalog(); if (!meta) return;
    const templates = meta.templates.filter(template => byId('manual-create').elements.kind.value === 'NPC' || template.key === 'blank');
    options(byId('manual-template'), templates.map(template => template.key), key => templates.find(template => template.key === key).name);
  }
  function refreshManualInventory() {
    const form = byId('manual-inventory'), meta = manualCatalog(); if (!meta) return;
    const items = domain().items.filter(item => item.characterId === form.elements.characterId.value);
    options(byId('manual-inventory-item'), items.map(item => item.id), id => {
      const item = items.find(item => item.id === id); return `${item.name} × ${item.quantity}${item.active === false ? ' · 归档' : ''}`;
    });
    options(byId('manual-item-source'), meta.items.map(item => item.key), key => meta.items.find(item => item.key === key).name);
  }
  function renderManualEncounters() {
    const data = manualData(), meta = manualCatalog(), summary = byId('manual-encounter-summary'); if (!data || !meta) return;
    const active = data.encounters.filter(encounter => encounter.status === 'ACTIVE');
    for (const select of document.querySelectorAll('.manual-encounter-choice')) {
      options(select, active.map(encounter => encounter.id), id => `简易遭遇 ${data.encounters.findIndex(encounter => encounter.id === id) + 1}`);
      if (!select.value && active.length === 1) select.value = active[0].id;
    }
    const richScene = domain().combats.some(history => history.current.status !== 'ENDED');
    byId('manual-encounter').querySelector('button[type="submit"],button:not([type])').disabled = richScene || active.length > 0;
    byId('manual-encounter-end').disabled = active.length === 0;
    byId('domain-combat-start').querySelector('button').disabled = active.length > 0;
    const rosterNode = byId('manual-encounter-roster'), previous = new Map(Array.from(rosterNode.children, row => [row.dataset.character, {
      checked: row.querySelector('[name="join"]').checked, side: row.querySelector('[name="side"]').value, node: row.querySelector('[name="participantNodeId"]').value}])); rosterNode.replaceChildren();
    for (const card of allPeople().filter(card => card.active)) {
      const old = previous.get(card.id), row = document.createElement('div'); row.className = 'manual-participant-row'; row.dataset.character = card.id;
      const join = control(row, card.name, 'join', 'checkbox'); join.checked = old?.checked ?? true;
      const side = control(row, '阵营', 'side', 'select'); options(side, meta.sides, key => ({ALLY: '友方', ENEMY: '敌方', NEUTRAL: '中立'})[key]); side.value = old?.side || (card.kind === 'NPC' ? 'ENEMY' : 'ALLY');
      const node = control(row, '位置', 'participantNodeId', 'select'); options(node, domain().nodes.map(node => node.id), id => domain().nodes.find(node => node.id === id).name);
      if (domain().nodes.some(item => item.id === old?.node)) node.value = old.node;
      else if (data.partyNodeId) node.value = data.partyNodeId;
      rosterNode.append(row);
    }
    summary.replaceChildren();
    text(summary, 'p', `队伍地点：${domain().nodes.find(node => node.id === data.partyNodeId)?.name || '未标记'}`);
    for (const [index, encounter] of data.encounters.entries()) {
      text(summary, 'h3', `简易遭遇 ${index + 1} · ${encounter.status === 'ACTIVE' ? '进行中' : '已结束'}`);
      table(summary, ['角色', '阵营', '当前位置'], encounter.participants.map(person => [personName(person.characterId), ({ALLY: '友方', ENEMY: '敌方', NEUTRAL: '中立'})[person.side],
        domain().nodes.find(node => node.id === domain().positions.find(position => position.characterId === person.characterId)?.nodeId)?.name || '未标记']));
    }
  }
  function addManualEffect(branch) {
    const meta = manualCatalog(); if (!meta) return;
    const parent = byId(`manual-${branch}-effects`);
    if (parent.children.length >= 64) { message('每个检定分支最多设置 64 项后果。'); return; }
    const row = document.createElement('fieldset'); row.className = 'manual-effect';
    const kind = control(row, '后果', 'kind', 'select'); options(kind, meta.effectKinds, key => effectNames[key] || key);
    const body = document.createElement('div'); body.className = 'manual-effect-fields'; row.append(body);
    const actions = document.createElement('div'); actions.className = 'actions';
    for (const [label, operation] of [['上移', () => { if (row.previousElementSibling) parent.insertBefore(row, row.previousElementSibling); }],
      ['下移', () => { if (row.nextElementSibling) parent.insertBefore(row.nextElementSibling, row); }], ['删除后果', () => row.remove()]]) {
      const button = text(actions, 'button', label); button.type = 'button'; button.addEventListener('click', operation);
    }
    row.append(actions); parent.append(row);
    const rebuild = () => {
      body.replaceChildren();
      if (kind.value !== 'MESSAGE') { const target = control(body, '目标', 'targetId', 'select'); target.className = 'manual-effect-target'; target.required = true; }
      if (kind.value === 'HP') { const amount = control(body, '生命值变化（负数为伤害）', 'amount', 'number', '1'); amount.min = '-999'; amount.max = '999'; amount.required = true; }
      if (kind.value === 'GRANT_MODULE_ITEM') { const item = control(body, '目录物品', 'itemKey', 'select'); options(item, meta.items.map(item => item.key), key => meta.items.find(item => item.key === key).name); }
      if (kind.value === 'GRANT_TEMPORARY_ITEM') { control(body, '物品名称', 'name', 'text').required = true; control(body, '物品描述', 'description', 'text'); }
      if (['GRANT_MODULE_ITEM', 'GRANT_TEMPORARY_ITEM'].includes(kind.value)) { const quantity = control(body, '数量', 'quantity', 'number', '1'); quantity.min = '1'; quantity.max = '999'; quantity.required = true; }
      if (kind.value === 'POSITION') { const node = control(body, '地点', 'nodeId', 'select'); node.className = 'node-choice'; node.required = true; options(node, domain().nodes.map(node => node.id), id => domain().nodes.find(node => node.id === id).name); text(body, 'p', '位置后果的目标须已加入当前简易遭遇。'); }
      if (kind.value === 'MESSAGE') control(body, '消息', 'text', 'text').required = true;
      refreshManualEffects();
    };
    kind.addEventListener('change', rebuild); rebuild();
  }
  function refreshManualEffects() {
    if (!manualData()) return;
    for (const row of document.querySelectorAll('.manual-effect')) {
      const kind = row.querySelector('[name="kind"]').value, target = row.querySelector('[name="targetId"]'); if (!target) continue;
      let people = (kind === 'HP' ? manualCards() : allPeople()).filter(card => card.active);
      if (kind === 'POSITION') { const participants = manualData().encounters.find(encounter => encounter.status === 'ACTIVE')?.participants || []; people = people.filter(card => participants.some(person => person.characterId === card.id)); }
      options(target, people.map(card => card.id), personName);
    }
  }
  function readManualEffects(branch) {
    const effects = Array.from(byId(`manual-${branch}-effects`).children, row => {
      const value = name => row.querySelector(`[name="${name}"]`).value, kind = value('kind');
      if (kind === 'HP') return {kind, targetId: value('targetId'), amount: value('amount')};
      if (kind === 'GRANT_MODULE_ITEM') return {kind, targetId: value('targetId'), itemKey: value('itemKey'), quantity: value('quantity')};
      if (kind === 'GRANT_TEMPORARY_ITEM') return {kind, targetId: value('targetId'), name: value('name'), description: value('description'), quantity: value('quantity')};
      if (kind === 'POSITION') return {kind, targetId: value('targetId'), nodeId: value('nodeId')};
      return {kind: 'MESSAGE', text: value('text')};
    });
    if (effects.filter(effect => effect.kind === 'MESSAGE').length > 1) throw new Error('每个分支最多设置一条结果消息。');
    return effects;
  }
  async function load() {
    const response = await fetch(endpoint + 'state', {credentials: 'same-origin', cache: 'no-store'});
    const body = await response.json();
    if (!response.ok) throw new Error(body.code || 'STORAGE_UNAVAILABLE');
    view = body; render();
  }
  async function digest(action, epoch, revision, fields) {
    const bytes = [];
    const integer = value => { bytes.push((value >>> 24) & 255, (value >>> 16) & 255, (value >>> 8) & 255, value & 255); };
    const string = value => { const encoded = new TextEncoder().encode(value); integer(encoded.length); bytes.push(...encoded); };
    string('DND_STORAGE_HTTP_V1'); string(action); string(epoch); string(revision);
    const keys = Object.keys(fields).sort(); integer(keys.length);
    for (const key of keys) { string(key); integer(fields[key].length); for (const value of fields[key]) string(value); }
    const hash = await crypto.subtle.digest('SHA-256', new Uint8Array(bytes));
    return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('');
  }
  async function command(action, fields, upload = null) {
    if (busy || !view) return;
    busy = true;
    const epoch = view.storage.epoch, revision = String(view.campaign?.revision ?? 0);
    try {
      const payload = new URLSearchParams();
      for (const [key, values] of Object.entries(fields)) for (const value of values) payload.append(key, value);
      const response = await fetch(endpoint + action, {method: 'POST', credentials: 'same-origin', headers: {
        'Content-Type': upload ? 'application/octet-stream' : 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': view.csrf,
        'X-Host-State-Epoch': epoch, 'X-Object-Row-Version': revision,
        'X-Request-Id': crypto.randomUUID(), 'X-Request-Digest': await digest(action, epoch, revision, fields)
      }, body: upload || payload.toString()});
      const body = await response.json();
      if (!response.ok) throw new Error(body.code || 'STORAGE_UNAVAILABLE');
      if (body.savedFile) lastSaved = body.savedFile;
      if (action === 'domain' && fields.action?.[0] === 'manual-create' && body.commandResult?.characterId) manualSelected = body.commandResult.characterId;
      await load();
      message(body.commandResult?.replayed ? '已确认先前操作，战役记录没有重复。'
        : action === 'end' ? '战役已保存并结束，可以再次打开。' : action === 'save' ? '战役已保存。'
        : action === 'import' ? '文件已核验并导入，请选择打开。' : action === 'delete' ? '所选文件已删除。'
        : action === 'discard' ? '所选运行已放弃，已存战役文件保留。' : '战役已更新。');
    } catch (failure) {
      try { await load(); } catch (_) { /* Keep the last visible state; no command is retried. */ }
      message(messages[failure.message] || '操作未完成，请检查输入及当前战役状态。');
    } finally { busy = false; }
  }
  function form(id, action, extra = () => ({})) {
    byId(id).addEventListener('submit', event => {
      event.preventDefault();
      const fields = {};
      for (const [key, value] of new FormData(event.currentTarget).entries()) (fields[key] ||= []).push(value);
      Object.assign(fields, extra()); command(action, fields);
    });
  }
  function domainCommand(action, payload) { return command('domain', {action: [action], payload: [JSON.stringify(payload)]}); }
  function baseCharacter(kind) {
    return {type: 'character.Command', kind, resource: null, amount: '0', recoveryTrigger: null, hpChoice: null, targetClass: null,
      growthChoices: {type: 'character.GrowthChoices', subclass: null, abilityIncreases: [], feat: null, proficiencyChoices: []},
      restInput: null, checkInput: null, equipmentInput: null, healthInput: null, combatInput: null, spellbookInput: null};
  }
  function domainForm(id, action, build) {
    byId(id).addEventListener('submit', event => {
      event.preventDefault();
      try {
        const values = Object.fromEntries(new FormData(event.currentTarget));
        domainCommand(typeof action === 'function' ? action(event.currentTarget, values) : action, build(event.currentTarget, values));
      }
      catch (failure) { message(failure.message || '请检查操作输入。'); }
    });
  }
  function characterForm(id, build) {
    domainForm(id, 'character-command', (form, values) => {
      const character = selectedCharacter(form);
      if (!character) throw new Error('请先创建或选择角色。');
      return {characterId: character.id, characterVersion: String(character.version), command: build(form, values, character)};
    });
  }
  const multipleRules = select => Array.from(select.selectedOptions, option => typedRule(option.value));
  domainForm('domain-create', 'character-create', (_, values) => ({name: values.name, kind: values.kind,
    classKey: ruleFromValue(values.classKey).key, backgroundKey: ruleFromValue(values.backgroundKey).key}));
  domainForm('domain-profile', 'character-profile', (_, values) => ({characterId: values.characterId, name: values.name, kind: values.kind, active: values.active === 'on'}));
  characterForm('domain-growth', (_, values) => ({...baseCharacter('CHARACTER_ADVANCED'), hpChoice: values.hpChoice, targetClass: typedRule(values.class),
    growthChoices: {type: 'character.GrowthChoices', subclass: typedRule(values.subclass), feat: typedRule(values.feat),
      abilityIncreases: values.ability ? [[values.ability, '2']] : [], proficiencyChoices: []}}));
  characterForm('domain-resource', (_, values) => ({...baseCharacter(values.kind),
    resource: values.kind === 'RESOURCE_SPENT' ? typedRule(values.resource) : null,
    amount: values.kind === 'RESOURCE_SPENT' ? values.amount : '0', recoveryTrigger: values.kind === 'RESOURCES_RECOVERED' ? values.recoveryTrigger : null}));
  characterForm('domain-rest', (_, values) => ({...baseCharacter(values.kind), restInput: {type: 'character.RestInput',
    restKind: values.kind === 'REST_STARTED' ? values.restKind : null, activity: values.kind === 'TIME_ADVANCED' ? values.activity : null,
    minutes: values.kind === 'TIME_ADVANCED' ? values.minutes : '0', hitDieSides: values.kind === 'HIT_DIE_SPENT' ? values.hitDieSides : '0',
    recoveredHitDice: values.kind === 'REST_COMPLETED' && Number(values.recoverDice) > 0 ? [[values.hitDieSides, values.recoverDice]] : [],
    recoveredSpellSlots: values.kind === 'REST_COMPLETED' ? [1, 2, 3, 4, 5].filter(rank => Number(values[`recoverSlot${rank}`]) > 0)
      .map(rank => [String(rank), values[`recoverSlot${rank}`]]) : []}}));
  characterForm('domain-equipment', (_, values) => {
    const acquire = values.kind === 'ITEM_ACQUIRED';
    if (!acquire && !values.itemId) throw new Error('请选择角色已有的物品。');
    return {...baseCharacter(values.kind), equipmentInput: {type: 'character.EquipmentInput', itemId: acquire ? crypto.randomUUID() : values.itemId,
      item: acquire ? typedRule(values.item) : null, location: acquire ? 'CARRIED' : values.kind === 'ITEM_EQUIPPED' ? values.location : null, adjudication: values.adjudication}};
  });
  function conditionalBranch(prefix, values) {
    const itemId = values[prefix + 'Item'] || null, delta = values[prefix + 'Delta'];
    if (!/^(0|-?[1-9][0-9]*)$/.test(delta) || (itemId === null) !== (delta === '0')) throw new Error(`${prefix === 'success' ? '成功' : '失败'}分支：物品与非零数量变化需要同时选择；没有物品变化时请留空并设为 0。`);
    return {targetId: values[prefix + 'Target'], healthEffect: typedRule(values[prefix + 'Effect']), itemId, itemDelta: delta,
      nodeId: values[prefix + 'Node'] || null, message: values[prefix + 'Message'] || ''};
  }
  domainForm('domain-check', (_, values) => values.withEffects === 'on' ? 'check-effects' : 'character-command', (form, values) => {
    const character = selectedCharacter(form);
    if (!character) throw new Error('请先创建或选择检定角色。');
    const input = {characterId: character.id, characterVersion: String(character.version), command: {...baseCharacter('CHECK_RESOLVED'),
      checkInput: {type: 'character.CheckInput', rule: typedRule(values.rule), kind: values.kind, abilityKey: values.ability,
        proficiency: values.kind === 'ABILITY' ? null : typedRule(values.proficiency), difficultyClass: values.difficultyClass,
        advantage: values.advantage === 'on', disadvantage: values.disadvantage === 'on', adjudication: values.adjudication}}};
    if (values.withEffects === 'on') { input.success = conditionalBranch('success', values); input.failure = conditionalBranch('failure', values); }
    return input;
  });
  characterForm('domain-health', (_, values) => ({...baseCharacter(values.kind), healthInput: {type: 'character.HealthInput', rule: typedRule(values.rule),
    effect: values.kind === 'HEALTH_EFFECT_RESOLVED' ? typedRule(values.effect) : null, temporaryChoice: values.kind === 'HEALTH_EFFECT_RESOLVED' ? values.temporaryChoice || null : null, adjudication: values.adjudication}}));
  characterForm('domain-spellbook', (form, values, character) => ({...baseCharacter(values.kind), spellbookInput: {type: 'character.SpellbookInput', castingRule: typedRule(values.castingRule),
    learned: values.kind === 'SPELLBOOK_PREPARED' ? [] : multipleRules(form.elements.learned),
    prepared: values.kind === 'SPELLBOOK_ADVANCED' ? (character.state.spellbook?.prepared || []).map(typedRule) : multipleRules(form.elements.prepared)}}));
  domainForm('domain-node', 'node-create', (_, values) => ({name: values.name}));
  domainForm('domain-link', 'node-link', (_, values) => ({from: values.from, to: values.to}));
  domainForm('domain-position', 'position', (_, values) => ({characterId: values.characterId, nodeId: values.nodeId}));
  domainForm('domain-item', 'item', (_, values) => ({itemId: values.itemId || null, characterId: values.characterId, name: values.name, quantity: values.quantity}));
  domainForm('domain-note', 'note', (_, values) => ({text: values.text}));
  domainForm('manual-create', 'manual-create', (_, values) => ({name: values.name, kind: values.kind, template: values.template}));
  byId('manual-card-load').addEventListener('submit', event => {
    event.preventDefault(); manualSelected = event.currentTarget.elements.characterId.value; renderManualEditor();
  });
  byId('manual-create').elements.kind.addEventListener('change', refreshManualTemplates);
  domainForm('manual-inventory', 'manual-item', (form, values) => manualPayload(manualCards().find(card => card.id === values.characterId), {
    itemId: values.itemId || null, sourceKey: form.elements.sourceKey.value || null, name: values.name, description: values.description || '', quantity: values.quantity, active: values.active === 'on'}));
  byId('manual-inventory').elements.characterId.addEventListener('change', () => {
    refreshManualInventory(); byId('manual-inventory-item').dispatchEvent(new Event('change'));
  });
  byId('manual-inventory-item').addEventListener('change', event => {
    const item = domain().items.find(item => item.id === event.currentTarget.value), form = event.currentTarget.form;
    if (item) { form.elements.name.value = item.name; form.elements.description.value = item.description || ''; form.elements.sourceKey.value = item.sourceKey || ''; form.elements.quantity.value = item.quantity; form.elements.active.checked = item.active !== false; }
    else { form.elements.name.value = ''; form.elements.description.value = ''; form.elements.quantity.value = '1'; form.elements.active.checked = true; form.elements.sourceKey.value = ''; }
    form.elements.name.readOnly = Boolean(item); form.elements.description.readOnly = Boolean(item); form.elements.sourceKey.disabled = Boolean(item);
  });
  byId('manual-item-source').addEventListener('change', event => {
    const item = manualCatalog()?.items.find(item => item.key === event.currentTarget.value), form = event.currentTarget.form;
    if (item) { form.elements.name.value = item.name; form.elements.description.value = item.description; }
    form.elements.name.readOnly = Boolean(item); form.elements.description.readOnly = Boolean(item);
  });
  byId('manual-check').elements.checkType.addEventListener('change', refreshManualCheck);
  for (const button of document.querySelectorAll('[data-add-manual-effect]')) button.addEventListener('click', () => addManualEffect(button.dataset.addManualEffect));
  domainForm('manual-check', 'manual-check', (_, values) => {
    const card = manualCards().find(card => card.id === values.characterId), success = readManualEffects('success'), failure = readManualEffects('failure');
    const ids = [...new Set([...success, ...failure].filter(effect => effect.kind !== 'MESSAGE').map(effect => effect.targetId))].sort();
    const targets = ids.map(id => {
      const person = allPeople().find(card => card.id === id); if (!person) throw new Error('后果目标已经改变，请重新选择。');
      return {characterId: id, version: String(person.version)};
    });
    return {...manualPayload(card), checkKind: values.checkType, key: values.checkType === 'MANUAL' ? null : values.modifierSource,
      manualName: values.checkType === 'MANUAL' ? values.manualName : null, manualModifier: values.checkType === 'MANUAL' ? values.manualModifier : null,
      rollMode: values.rollMode, dc: values.difficultyClass, targets, success, failure};
  });
  domainForm('manual-party', 'manual-party-position', (_, values) => ({nodeId: values.nodeId}));
  domainForm('manual-encounter', 'manual-encounter-start', (_, values) => ({nodeId: values.nodeId,
    participants: Array.from(byId('manual-encounter-roster').children).filter(row => row.querySelector('[name="join"]').checked).map(row => ({
      characterId: row.dataset.character, side: row.querySelector('[name="side"]').value, nodeId: row.querySelector('[name="participantNodeId"]').value}))}));
  byId('manual-encounter-end').addEventListener('click', () => {
    const id = byId('manual-encounter').elements.encounterId.value;
    if (id) domainCommand('manual-encounter-close', {encounterId: id}); else message('请选择要结束的简易遭遇。');
  });
  domainForm('manual-participant', 'manual-encounter-members', (_, values) => {
    const encounter = manualEncounter(values.encounterId); if (!encounter) throw new Error('请选择进行中的简易遭遇。');
    const participants = encounter.participants.filter(person => person.characterId !== values.characterId).map(person => ({characterId: person.characterId, side: person.side,
      nodeId: domain().positions.find(position => position.characterId === person.characterId)?.nodeId}));
    if (values.operation === 'add') participants.push({characterId: values.characterId, side: values.side, nodeId: values.nodeId});
    return {encounterId: encounter.id, participants};
  });
  byId('domain-combat-start').addEventListener('submit', event => {
    event.preventDefault();
    const deployments = Array.from(byId('domain-deployments').querySelectorAll('[data-character]')).filter(row => row.querySelector('[name="join"]').checked).map(row => ({
      type: 'scene.Deployment', characterId: row.dataset.character, position: {type: 'scene.Position', x: row.querySelector('[name="x"]').value, y: row.querySelector('[name="y"]').value}, side: row.querySelector('[name="side"]').value}));
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const monsterDeployments = values.monster ? [{type: 'scene.MonsterDeployment', monsterId: crypto.randomUUID(), monster: typedRule(values.monster),
      position: {type: 'scene.Position', x: values.monsterX, y: values.monsterY}, side: values.monsterSide}] : [];
    sendCombat(null, {...baseCombat('START'), deployments, monsterDeployments, tieBreakOrder: [...deployments.map(deployment => deployment.characterId), ...monsterDeployments.map(monster => monster.monsterId)]});
  });
  byId('domain-combat').addEventListener('submit', event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)), battle = battleFor(values.combatId);
    if (!battle) { message('请先开始战斗。'); return; }
    const value = baseCombat(values.kind, values.actorId);
    if (values.kind === 'ATTACK') { value.targetId = values.targetId || null; value.itemId = values.itemId || null; }
    if (['MOVE', 'EXPLORATION_MOVE'].includes(values.kind)) value.path = [{type: 'scene.Position', x: values.x, y: values.y}];
    if (values.kind === 'EXPLORATION_ADVANCE_TIME') value.seconds = values.seconds;
    if (values.kind === 'REENTER_COMBAT') value.tieBreakOrder = battle.combatants.map(actor => actor.characterId);
    sendCombat(battle, value);
  });
  byId('domain-cast').addEventListener('submit', event => {
    event.preventDefault(); const form = event.currentTarget, values = Object.fromEntries(new FormData(form)), battle = battleFor(values.combatId);
    if (!battle) { message('请先开始战斗。'); return; }
    const castInput = {type: 'scene.CastInput', castingRule: typedRule(values.castingRule), spell: typedRule(values.spell),
      targets: values.coneDirection === '' ? Array.from(form.elements.targets.selectedOptions, option => option.value) : [],
      materialItemId: values.materialItemId || null, slotLevel: values.slotLevel, targetCastFrameKey: null, coneDirection: values.coneDirection || null};
    sendCombat(battle, {...baseCombat('CAST', values.actorId), castInput});
  });
  byId('domain-monster').addEventListener('submit', event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)), battle = battleFor(values.combatId);
    const monster = battle?.monsters.find(monster => monster.monsterId === values.actorId);
    if (!battle || !monster) { message('请选择当前场景中的怪物。'); return; }
    const value = baseCombat(values.kind, values.actorId);
    if (values.kind === 'MONSTER_BREATH') {
      const entry = monster.actionUses[Number(values.breathAction)]?.action;
      if (!entry) { message('所选怪物没有可执行吐息。'); return; }
      value.breathInput = {type: 'scene.BreathInput', direction: values.direction, action: {type: 'rule.EntryIdentity', rule: typedRule(entry.rule),
        moduleKey: entry.moduleKey, collectionKey: entry.collectionKey, entryKey: entry.entryKey}};
    } else {
      const rest = battle.monsterRests.find(rest => rest.monsterId === values.actorId && rest.status === 'ACTIVE');
      value.monsterRestInput = {type: 'scene.MonsterRestInput', restKey: values.kind === 'COMPLETE_MONSTER_REST' ? rest?.restKey || null : null,
        kind: values.kind === 'COMPLETE_MONSTER_REST' ? rest?.kind || values.restKind : values.restKind};
    }
    sendCombat(battle, value);
  });
  byId('domain-environment').addEventListener('submit', event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)), battle = battleFor(values.combatId);
    if (!battle) { message('请先开始场景。'); return; }
    const value = baseCombat(values.kind, values.actorId);
    if (values.kind === 'CREATE_ENVIRONMENT_ZONE') value.zoneInput = {type: 'scene.ZoneInput', rule: typedRule(values.rule), center: {type: 'scene.Position', x: values.x, y: values.y}};
    if (values.kind === 'END_ENVIRONMENT_ZONE') value.zoneKey = values.zoneKey || null;
    if (values.kind === 'EXPLORATION_HEALTH') { value.targetId = values.targetId; value.healthInput = {type: 'character.HealthInput', rule: typedRule(values.healthRule),
      effect: typedRule(values.effect), temporaryChoice: values.temporaryChoice || null, adjudication: values.adjudication}; }
    sendCombat(battle, value);
  });
  byId('domain-ready').addEventListener('submit', event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)), battle = battleFor(values.combatId);
    if (!battle) { message('请先开始战斗。'); return; }
    const position = {type: 'scene.Position', x: values.x, y: values.y};
    const readyInput = {type: 'scene.ReadyInput', responseKind: values.responseKind,
      trigger: {type: 'scene.ReadyTrigger', kind: values.triggerKind, subjectId: values.subjectId, position: values.triggerKind === 'CREATURE_ENTER_CELL_AFTER' ? position : null},
      targetId: values.responseKind === 'ATTACK' ? values.targetId || null : null,
      itemId: values.responseKind === 'ATTACK' ? values.itemId || null : null, path: values.responseKind === 'MOVE' ? [position] : [],
      spell: values.responseKind === 'SPELL' ? {type: 'scene.CastInput', castingRule: typedRule(values.castingRule), spell: typedRule(values.spell), targets: [values.targetId],
        materialItemId: values.itemId || null, slotLevel: values.slotLevel, targetCastFrameKey: null, coneDirection: null} : null};
    sendCombat(battle, {...baseCombat('READY_' + values.responseKind, values.actorId), readyInput});
  });
  for (const button of document.querySelectorAll('[data-pane]')) button.addEventListener('click', () => {
    for (const panel of document.querySelectorAll('[data-panel]')) panel.hidden = panel.dataset.panel !== button.dataset.pane;
    for (const tab of document.querySelectorAll('[data-pane]')) tab.setAttribute('aria-pressed', String(tab === button));
  });
  for (const select of document.querySelectorAll('.domain-character,.combat-actor-choice,.monster-choice')) select.addEventListener('change', refreshCharacterChoices);
  byId('check-with-effects').addEventListener('change', refreshCheckBranches);
  byId('domain-rest').elements.kind.addEventListener('change', refreshArcaneRecovery);
  for (const select of document.querySelectorAll('.check-branch-character')) select.addEventListener('change', refreshCheckBranches);
  for (const select of document.querySelectorAll('.world-item-choice')) select.addEventListener('change', event => {
    const item = domain().items.find(item => item.id === event.currentTarget.value), form = event.currentTarget.form;
    if (item) { form.elements.name.value = item.name; form.elements.quantity.value = item.quantity; }
  });
  for (const select of document.querySelectorAll('.combat-choice')) select.addEventListener('change', refreshCombatActors);
  form('storage-create', 'create', () => ({modules: Array.from(byId('storage-modules').querySelectorAll('input:checked'), input => input.value)}));
  form('storage-open', 'open'); form('storage-recover', 'recover'); form('storage-character', 'characters');
  form('storage-heal', 'heal', () => ({characterVersion: [String(view.campaign.characters.find(c => c.id === byId('storage-heal-character').value).version)]}));
  byId('storage-save').addEventListener('click', () => command('save', {}));
  byId('storage-end').addEventListener('click', () => command('end', {}));
  byId('storage-files').addEventListener('change', () => downloadLink(byId('storage-download'), byId('storage-files').value));
  byId('storage-delete').addEventListener('click', () => {
    const selected = byId('storage-files').selectedOptions[0];
    if (selected && window.confirm(`永久删除“${selected.textContent}”的这个本地保存文件？请先下载需要保留的副本。`)) command('delete', {fileKey: [selected.value]});
  });
  byId('storage-discard').addEventListener('click', () => {
    const selected = byId('storage-residuals').selectedOptions[0];
    if (selected && window.confirm(`永久放弃所选运行 ${selected.value} 的未保存进度？已存战役文件会保留。`)) command('discard', {runId: [selected.value]});
  });
  byId('storage-import').addEventListener('submit', async event => {
    event.preventDefault(); const file = event.currentTarget.elements.file.files[0];
    if (!file || file.size === 0 || file.size > 16 * 1024 * 1024) { message('请选择不超过 16 MiB 的完整战役 JSON 文件。'); return; }
    const bytes = await file.arrayBuffer();
    const hash = await crypto.subtle.digest('SHA-256', bytes);
    const fileSha256 = Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('');
    await command('import', {fileSha256: [fileSha256]}, bytes);
  });
  load().then(() => message(view.campaign ? '战役已就绪。' : '创建、打开或恢复一个战役。')).catch(failure => message(messages[failure.message] || '无法读取战役。'));
})();
