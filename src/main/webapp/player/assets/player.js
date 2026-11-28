'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const fragment = new URLSearchParams(location.hash.slice(1));
  let invitation = fragment.get('invite') || '';
  if (location.hash) history.replaceState(null, '', location.pathname + location.search);
  const storage = {
    get(key) { try { return sessionStorage.getItem('dnd.player.' + key); } catch (_) { return null; } },
    set(key, value) { try { sessionStorage.setItem('dnd.player.' + key, value); return true; } catch (_) { return false; } },
    remove(key) { try { sessionStorage.removeItem('dnd.player.' + key); } catch (_) { /* No private projection is cached. */ } }
  };
  let session = null, view = null, connectionId = '', pendingRequest = null, lastReceipt = null;
  storage.remove('connection');
  let readPromise = null, timer = null, failures = 0, submitting = false, synchronized = false, receiptUnresolved = false;
  let candidates = [], routeDraft = null, pendingDetail = '';
  const directionSteps = [
    ['西北', -1, -1], ['北', 0, -1], ['东北', 1, -1], ['西', -1, 0],
    ['东', 1, 0], ['西南', -1, 1], ['南', 0, 1], ['东南', 1, 1]
  ];
  const directionButtons = directionSteps.map(([label], index) => {
    const button = element('button', 'secondary', label);
    button.type = 'button';
    button.setAttribute('aria-label', '向' + label);
    button.addEventListener('click', () => addRouteStep(index));
    $('player-route-directions').append(button);
    return button;
  });
  let cardsRenderKey = '', sharedRenderKey = '', proposalsRenderKey = '', actionsRenderKey = '', pendingRenderKey = '';
  try { pendingRequest = JSON.parse(storage.get('pending') || 'null'); } catch (_) { receiptUnresolved = true; }
  if (pendingRequest && (!pendingRequest.requestId || !pendingRequest.candidateId || !pendingRequest.precondition)) {
    pendingRequest = null; receiptUnresolved = true;
  }
  const errors = {
    PLAYER_INVALID_NICKNAME: '请使用 1 至 80 个字的昵称，并避免控制字符。', PLAYER_WRITE_CONNECTION_REQUIRED: '操作已由另一页面接管，请在需要时明确启用此处操作。',
    PLAYER_DEVICE_REAPPROVAL_REQUIRED: '此设备的恢复资格已失效，请 DM 重新核对已有成员。', PLAYER_SESSION_EXPIRED: '这个席位的会话已失效，请恢复设备席位或重新申请。',
    PLAYER_RUN_UNAVAILABLE: '当前战役已结束或正在恢复，请联系 DM 核对运行状态。', PLAYER_RATE_LIMITED: '请求较频繁，请稍候同步，行动不会自动重发。',
    PLAYER_SECURITY_UNAVAILABLE: '席位安全状态暂不可用，请 DM 核对恢复。', PLAYER_RESULT_UNVERIFIED: '本次结果仍待查证，请保持只读并请 DM 核对准确收据。',
    PLAYER_INVALID_REQUEST: '请核对输入，并使用当前允许的选项。', PLAYER_INVALID_PROPOSAL: '请使用 1 至 1000 个字描述尝试，并避免控制字符。',
    PLAYER_RECEIPT_CAPACITY: '本次运行的操作查证容量已满，请联系 DM。', PLAYER_PROPOSAL_CAPACITY: '当前提案容量已满，请联系 DM 清理已处理提案。',
    PLAYER_INVITATION_INVALID: '邀请码已失效，请向 DM 获取当前邀请。', PLAYER_NICKNAME_INVALID: '请缩短昵称并避免控制字符。',
    PLAYER_MEMBERSHIP_REVOKED: 'DM 已撤销这个席位，请重新核对身份。', PLAYER_SESSION_REQUIRED: '请重新核对你的席位。',
    PLAYER_OPERATIONS_PAUSED: 'DM 暂停了玩家操作，请等待恢复。', PLAYER_VIEW_EXPIRED: '现场已更新，请使用最新允许的行动。',
    PLAYER_WRITER_REVOKED: '操作已由另一设备接管，此处保留只读视图。', PLAYER_CONTROL_REQUIRED: '角色控制已经变化，请重新核对分配。',
    PLAYER_HOST_REOPEN_REQUIRED: '请等待 DM 完整恢复战役并明确重新开放席位。', PLAYER_DEVICE_INVALID: '此设备的恢复凭据不可用，请 DM 重新邀请并核对身份。',
    PLAYER_COMMAND_REJECTED: '当前规则或现场不接受这项行动，请重新选择允许的选项。',
    INVITATION_INVALID: '邀请码已失效，请向 DM 获取当前邀请。', INVALID_INVITATION: '邀请码已失效，请向 DM 获取当前邀请。',
    JOIN_CLOSED: '本次加入已关闭，请联系 DM。', INVALID_NICKNAME: '请使用有效昵称，避免控制字符，并缩短名称。',
    PLAYER_REVOKED: 'DM 已撤销这个席位，请重新核对身份。', SESSION_REQUIRED: '请重新核对你的席位。',
    PLAYER_PAUSED: 'DM 暂停了玩家操作。当前待处理行动会保留。', PLAYER_STALE: '现场已更新，请重新选择当前行动。',
    STALE_PRECONDITION: '现场已更新，本次操作未执行，请重新选择。', WRITE_CONNECTION_REQUIRED: '此设备当前只读，请明确启用操作。',
    PLAYER_WRITE_REVOKED: '操作已由另一设备接管。此处保留只读视图。', PLAYER_UNAVAILABLE: '当前席位暂不可用，请联系 DM。',
    UNSUPPORTED_ACTION: '此操作尚未支持，请向 DM 提出裁定。', INVALID_REQUEST: '请检查输入，再使用当前允许的选项。',
    RUN_BLOCKED: '战役正在核验恢复，请等待 DM 处理。'
  };
  function element(tag, className, text) {
    const node = document.createElement(tag); if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text); return node;
  }
  function status(title, detail, tone = '', retry = false) {
    if ($('player-status-title').textContent !== title) $('player-status-title').textContent = title;
    if ($('player-status-detail').textContent !== detail) $('player-status-detail').textContent = detail;
    $('player-status').className = 'status-banner' + (tone ? ' is-' + tone : ''); $('player-sync').hidden = !retry;
  }
  function connected(online) {
    const label = $('player-connection'); label.className = 'connection ' + (online ? 'is-live' : 'is-offline');
    label.replaceChildren(element('i'), document.createTextNode(online ? '已连接' : '连接中断'));
  }
  async function api(path, body) {
    const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const headers = {'Accept': 'application/json'};
      if (connectionId) headers['X-Player-Connection'] = connectionId;
      if (body !== undefined) { headers['Content-Type'] = 'application/json'; headers['X-CSRF-Token'] = session?.csrf || ''; }
      const response = await fetch('/api/player/' + path, {method: body === undefined ? 'GET' : 'POST',
        credentials: 'same-origin', cache: 'no-store', headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal});
      let data;
      try { data = await response.json(); } catch (_) { throw new Error('RESPONSE_UNKNOWN'); }
      if (!response.ok || data.status === 'ERROR') {
        const failure = new Error(data.code || 'PLAYER_UNAVAILABLE');
        failure.status = response.status; throw failure;
      }
      if (data.session) session = data.session;
      return data;
    } finally { clearTimeout(timeout); }
  }
  function clearProjection() {
    view = null; candidates = []; routeDraft = null; synchronized = false;
    cardsRenderKey = sharedRenderKey = proposalsRenderKey = actionsRenderKey = pendingRenderKey = '';
    for (const id of ['player-cards', 'player-actions', 'player-shared', 'player-proposals', 'player-pending', 'player-proposal-character']) $(id).replaceChildren();
    $('player-table').hidden = true;
    $('player-receipt').hidden = true; lastReceipt = null;
  }
  function canWrite() { return synchronized && !submitting && !receiptUnresolved && !pendingRequest && session?.writeEnabled === true && !session?.paused; }
  function renderActions() {
    const container = $('player-actions'), key = JSON.stringify(candidates);
    $('player-actions-count').textContent = `${candidates.length} 项`;
    if (key !== actionsRenderKey) {
      actionsRenderKey = key; container.replaceChildren();
      if (!candidates.length) container.append(element('p', 'empty-state', '此刻没有可直接提交的行动。等待当前机会，或向 DM 提出尝试。'));
      for (const candidate of candidates) {
      const button = element('button', 'action-button'); button.type = 'button'; button.disabled = !canWrite();
      const content = element('span'); content.append(element('strong', '', candidate.label || '当前行动'));
      const character = (view.cards || []).find(card => card.id === candidate.characterId);
      if (candidate.description || character) content.append(element('small', '', [character?.name, candidate.description].filter(Boolean).join(' · ')));
        const arrow = element('span', 'arrow', '→');
        arrow.setAttribute('aria-hidden', 'true');
        button.append(content, arrow); button.addEventListener('click', () => {
          const current = candidateInView(candidate);
          if (!current || !canWrite()) return;
          if (current.route) {
            routeDraft = {candidate: current, trigger: button, steps: []};
            renderRoute();
            directionButtons.find(option => !option.disabled)?.focus();
          } else sendCommand(current);
        }); container.append(button);
      }
    }
    for (const button of container.querySelectorAll('button')) button.disabled = !canWrite();
    renderRoute();
    $('player-receipt-recovery').hidden = !pendingRequest?.receiptKey || session?.status !== 'APPROVED';
    $('player-close-receipt').disabled = !synchronized || submitting;
    $('player-proposal-form').querySelector('button').disabled = !synchronized || submitting || session?.status !== 'APPROVED' || !view?.cards?.length;
    $('player-takeover').disabled = !synchronized || submitting || receiptUnresolved || Boolean(pendingRequest);
    $('player-join-form').querySelector('button').disabled = submitting || !session;
    $('player-resume').disabled = submitting || !session;
  }
  function candidateInView(candidate) {
    return candidates.find(value => value.id === candidate.id && value.precondition === candidate.precondition);
  }
  function routeStep(index) {
    const [, dx, dy] = directionSteps[index], route = routeDraft.candidate.route;
    const last = routeDraft.steps.at(-1) || route;
    const x = last.x + dx, y = last.y + dy;
    if (x < 0 || y < 0 || x >= (route.width ?? 201) || y >= (route.height ?? 201)) return null;
    return {x, y, direction: directionSteps[index][0]};
  }
  function renderRoute() {
    if (routeDraft && !candidateInView(routeDraft.candidate)) routeDraft = null;
    $('player-route').hidden = !routeDraft;
    if (!routeDraft) return;
    const {candidate, steps} = routeDraft, route = candidate.route;
    const available = canWrite(), tip = steps.at(-1) || route;
    const rule = "每格消耗 " + route.stepCostFeet + " 英尺移动额度。提交整段路线推进场景时间 6 秒。";
    const path = steps.length ? steps.map(step => step.direction).join(' → ') + '。' : '';
    const preview = path + '已选 ' + steps.length + ' / ' + route.maxSteps + ' 格。' +
      (steps.length ? '终点' : '起点') + '（' + tip.x + ', ' + tip.y + '）';
    if ($('player-route-rules').textContent !== rule) $('player-route-rules').textContent = rule;
    if ($('player-route-preview').textContent !== preview) $('player-route-preview').textContent = preview;
    directionButtons.forEach((button, index) => {
      button.disabled = !available || steps.length === route.maxSteps || !routeStep(index);
    });
    $('player-route-undo').disabled = !available || !steps.length;
    $('player-route-submit').disabled = !available || !steps.length;
    $('player-route-cancel').disabled = submitting;
  }
  function addRouteStep(index) {
    if (!routeDraft || !canWrite() || routeDraft.steps.length === routeDraft.candidate.route.maxSteps) return;
    const next = routeStep(index), focused = document.activeElement;
    if (!next) return;
    routeDraft.steps.push(next);
    renderRoute();
    if (focused?.disabled) (directionButtons.find(button => !button.disabled) || $('player-route-submit')).focus();
  }
  function stat(value, label) { const node = element('div', 'stat'); node.append(element('strong', '', value ?? '—'), element('span', '', label)); return node; }
  function listSection(parent, title, values, format) {
    if (!Array.isArray(values) || !values.length) return;
    const details = element('details'), summary = element('summary', '', title), list = element('ul', 'mini-list');
    for (const value of values) list.append(element('li', '', format(value))); details.append(summary, list); parent.append(details);
  }
  function renderCards() {
    const container = $('player-cards'); container.replaceChildren();
    if (!view.cards?.length) container.append(element('p', 'empty-state', '你已入席。请 DM 为你分配一个玩家角色。'));
    for (const card of view.cards || []) {
      const article = element('article', 'character-card'), top = element('div', 'character-top'), name = element('div');
      name.append(element('h3', '', card.name), element('p', '', [card.level ? `${card.level} 级` : '', card.className || '玩家角色'].filter(Boolean).join(' · ')));
      top.append(element('span', 'character-avatar', [...card.name || '角'][0]), name);
      const stats = element('div', 'character-stats'); stats.append(stat(card.currentHp === undefined ? '—' : `${card.currentHp} / ${card.maximumHp ?? '—'}`, '生命值'), stat(card.armorClass, '防护等级'), stat(card.speed, '速度'));
      const content = element('div', 'character-content');
      const abilityLabels = {'ability.strength':'力量','ability.dexterity':'敏捷','ability.constitution':'体质','ability.intelligence':'智力','ability.wisdom':'感知','ability.charisma':'魅力'};
      const abilities = Array.isArray(card.abilities) ? card.abilities : Object.entries(card.abilities || {}).map(([key, value]) => ({label: abilityLabels[key] || key, value}));
      if (abilities.length) { const grid = element('div', 'ability-grid'); for (const ability of abilities) grid.append(element('span', '', `${ability.label} ${ability.value}${ability.modifier === undefined ? '' : `（${ability.modifier >= 0 ? '+' : ''}${ability.modifier}）`}`)); content.append(grid); }
      if (card.proficiencyBonus !== undefined) content.append(element('p', '', `熟练加值 +${card.proficiencyBonus}`));
      if (card.health) content.append(element('p', '', [`临时生命值 ${card.health.temporaryHp || 0}`, card.health.dead ? '死亡' : card.health.stable ? '稳定' : '', card.currentHp === 0 ? `死亡豁免：成功 ${card.health.deathSuccesses || 0} / 失败 ${card.health.deathFailures || 0}` : ''].filter(Boolean).join(' · ')));
      listSection(content, '装备与物品', card.equipment, item => [item.name || '物品', item.quantity ? `×${item.quantity}` : '', item.location || ''].filter(Boolean).join(' '));
      listSection(content, '资源', card.resources, item => `${item.label || item.name || '资源'} ${item.unlimited ? '不限次数' : `${item.current ?? '—'} / ${item.maximum ?? '—'}`}`);
      listSection(content, '当前效果', card.effects, item => typeof item === 'string' ? item : item.label || item.name || '当前效果');
      listSection(content, '熟练', card.proficiencies, item => typeof item === 'string' ? item : item.label || item.name || '熟练');
      listSection(content, '特性与专长', card.features, item => String(item));
      listSection(content, '已准备法术', card.spellbook?.prepared, item => String(item));
      listSection(content, '法术书', card.spellbook?.learned, item => String(item));
      article.append(top, stats, content); container.append(article);
    }
    const select = $('player-proposal-character'), selected = select.value; select.replaceChildren();
    for (const card of view.cards || []) { const option = element('option', '', card.name); option.value = card.id; select.append(option); }
    if ((view.cards || []).some(card => card.id === selected)) select.value = selected;
  }
  function renderShared() {
    const container = $('player-shared'); container.replaceChildren();
    const shared = view.shared || {};
    const add = (kind, title, text) => { const article = element('article', 'shared-item'); article.append(element('p', 'eyebrow', kind), element('h3', '', title), element('p', '', text)); container.append(article); return article; };
    for (const card of shared.cards || []) add('队友概况', card.name || '已共享角色', card.description || '');
    for (const scene of shared.scenes || []) {
      const article = add(scene.focused ? '执行焦点' : '共享空间', scene.name || '已共享场景', scene.description || '');
      if (scene.turnLabel) article.append(element('p', '', scene.turnLabel));
      if (Array.isArray(scene.participants)) { const list = element('ul', 'mini-list'); for (const person of scene.participants) list.append(element('li', '', [person.name, person.position, person.current ? '当前行动者' : ''].filter(Boolean).join(' · '))); article.append(list); }
    }
    for (const log of shared.logs || []) add('行动记录', log.title || '桌上发生的事', typeof log === 'string' ? log : log.text || '');
    $('player-shared-section').hidden = !container.childElementCount;
  }
  function renderProposals() {
    const container = $('player-proposals'); container.replaceChildren();
    const labels = {WAITING_DM:'等待 DM 裁定',REJECTED:'未获批准',EXPIRED:'已过期，请重新提出',NARRATIVE:'DM 已回应叙事',EXECUTED:'裁定已执行'};
    for (const proposal of view.proposals || []) {
      const article = element('article', 'proposal-item'); article.append(element('span', 'tag', labels[proposal.state] || '等待 DM 核对'), element('p', '', proposal.text || ''));
      if (proposal.responseText) article.append(element('p', 'reply', proposal.responseText));
      if (proposal.adjudication?.state === 'ACCEPTED') { const result = proposal.adjudication; article.append(element('p', 'reply', `业务结果：${result.success ? '成功' : '未成功'} · 原骰 ${(result.dice || []).join('、')} · 总计 ${result.total}`)); if (result.reason) article.append(element('p', 'subtle', result.reason)); }
      container.append(article);
    }
    if (!container.childElementCount) container.append(element('p', 'subtle', '还没有提案。'));
  }
  function render() {
    const approved = session?.status === 'APPROVED', waiting = session?.status === 'WAITING';
    $('player-join').hidden = approved || waiting || session?.status === 'REJECTED';
    $('player-waiting').hidden = !(waiting || session?.status === 'REJECTED');
    $('player-table').hidden = !approved || !view;
    if (waiting || session?.status === 'REJECTED') {
      $('player-waiting-title').textContent = waiting ? '等待 DM 核对' : '这个席位暂不可用';
      $('player-waiting-description').textContent = waiting ? '申请已经送达。DM 批准后，这里会自动更新。' : '请联系 DM，重新核对本次加入与成员身份。';
      $('player-waiting-name').textContent = session.displayName || '';
      status(waiting ? '已进入等候区' : '需要 DM 核对', waiting ? '保持页面打开即可，不必重复申请。' : '角色与共享内容已清除。', waiting ? '' : 'warning');
    }
    if (!approved || !view) { renderActions(); return; }
    $('player-campaign-name').textContent = view.campaignName || '当前战役';
    $('player-greeting').textContent = `${session.displayName || '冒险者'}，${session.paused ? '你已入席，当前玩家操作暂停。' : session.writeEnabled ? '你已入席。' : '此设备以只读方式入席。启用操作会使其他设备立即只读。'}`;
    $('player-takeover').hidden = session.writeEnabled === true || session.paused === true;
    const cardsKey = JSON.stringify(view.cards), sharedKey = JSON.stringify(view.shared), proposalsKey = JSON.stringify(view.proposals), pendingKey = JSON.stringify(view.pending);
    if (cardsKey !== cardsRenderKey) { cardsRenderKey = cardsKey; renderCards(); }
    if (sharedKey !== sharedRenderKey) { sharedRenderKey = sharedKey; renderShared(); }
    if (proposalsKey !== proposalsRenderKey) { proposalsRenderKey = proposalsKey; renderProposals(); }
    const pending = $('player-pending');
    if (pendingKey !== pendingRenderKey) { pendingRenderKey = pendingKey; pending.replaceChildren(); pending.hidden = !view.pending?.active && !view.pending?.frozen;
      if (view.pending) pending.append(element('h2', '', view.pending.title || '等待当前行动继续'), element('p', '', view.pending.description || '请按当前允许的机会继续。'));
    }
    $('player-receipt').hidden = !lastReceipt;
    if (lastReceipt) {
      let note = '上次收据：行动已完成。';
      if (lastReceipt.state === 'NOT_EXECUTED') note = '上次收据：确认未执行，旧请求已作废。';
      else if (lastReceipt.attackResult) {
        const {hit, damage, dropped} = lastReceipt.attackResult;
        note = `上次攻击${hit ? '命中' : '未命中'}，造成 ${damage} 点伤害，${dropped ? '击倒了目标' : '未击倒目标'}。`;
        if (lastReceipt.outcome === 'WAITING') note += '当时仍在等待后续决定。';
      }
      else if (lastReceipt.outcome === 'WAITING') note = '上次收据：行动已接受，当时等待后续决定。当前现场请看上方进度。';
      if ($('player-receipt').textContent !== note) $('player-receipt').textContent = note;
    }
    candidates = Array.isArray(view.candidates) ? view.candidates : [];
    if (receiptUnresolved || pendingRequest) status('上一操作的结果待查证', pendingDetail ||
      '当前进度已读回。请核对上次操作的收据，必要时请 DM 协助。', 'warning', true);
    else if (!synchronized) status('正在同步当前进度', '操作暂时锁定，完整同步后再继续。', 'warning');
    else if (session.paused) status('DM 暂停了玩家操作', '你仍可查看角色与已共享内容。当前待处理行动保留，等待 DM 恢复。', 'warning');
    else if (!session.writeEnabled) status('此设备当前只读', '需要在这里行动时，请明确点击“在此设备启用操作”。', 'warning');
    else status('你的席位已就绪', '选择当前允许的行动，或把新的尝试交给 DM 裁定。');
    renderActions();
  }
  function schedule() {
    clearTimeout(timer); const foreground = failures ? Math.min(8000, 2000 * 2 ** (failures - 1)) : 2000, base = document.hidden ? Math.max(10000, foreground) : foreground;
    timer = setTimeout(() => synchronize(), base + (failures ? Math.floor(Math.random() * 500) : 0));
  }
  function forgetPending() { pendingRequest = null; receiptUnresolved = false; pendingDetail = ''; storage.remove('pending'); }
  function applyReceipt(receipt, allowClosed = false) {
    if (pendingRequest && receipt?.requestId === pendingRequest.requestId &&
        (receipt?.state === 'ACCEPTED' || allowClosed && receipt?.state === 'NOT_EXECUTED')) {
      lastReceipt = receipt; forgetPending();
    } else receiptUnresolved = true;
  }
  async function verifyPending(close = false) {
    if (!pendingRequest) return;
    const {requestId, candidateId, precondition, receiptKey} = pendingRequest;
    const identity = {requestId, candidateId, precondition};
    if (close) identity.receiptKey = receiptKey;
    const data = await api(close ? 'receipt-close' : 'receipt', identity);
    view = data.view;
    applyReceipt(data.receipt, close);
  }
  async function synchronize() {
    if (readPromise) return readPromise;
    if (submitting) { schedule(); return; }
    readPromise = (async () => {
      try {
        if (!session) await api('session');
        const data = await api('state'); connected(true); failures = 0;
        if (session?.status !== 'APPROVED') { clearProjection(); render(); if (session?.status === 'ANONYMOUS') status('加入一场新的冒险', '填写昵称和 DM 提供的邀请码，申请你的席位。'); return; }
        view = data.view; synchronized = true; candidates = [];
        if (pendingRequest) { receiptUnresolved = true; await verifyPending(); }
        render();
      } catch (failure) {
        synchronized = false; candidates = []; failures++; connected(false);
        if (failure.status === 401 || failure.status === 403) { clearProjection(); connectionId = ''; session = null; }
        renderActions(); status(failure.status === 401 || failure.status === 403 ? '请重新核对席位' : '连接中断，操作已锁定', errors[failure.message] || '恢复连接后会先同步现场并查证未确认的操作。不会自动重发行动。', 'error', true);
      } finally { readPromise = null; schedule(); }
    })(); return readPromise;
  }
  async function sendCommand(candidate, route) {
    if (!canWrite() || !candidateInView(candidate)) return;
    submitting = true;
    renderActions();
    if (readPromise) await readPromise;
    if (!synchronized || !session?.writeEnabled || !candidateInView(candidate)) {
      submitting = false;
      render();
      return;
    }
    synchronized = false; routeDraft = null; lastReceipt = null;
    pendingRequest = {requestId: crypto.randomUUID(), candidateId: candidate.id, precondition: candidate.precondition, receiptKey: candidate.receiptKey};
    if (!storage.set('pending', JSON.stringify(pendingRequest))) {
      forgetPending(); submitting = false; status('本次行动尚未提交', '浏览器无法保留操作查证凭据。请允许此站点的会话存储后重新同步。', 'warning', true); renderActions(); return;
    }
    renderActions(); status('正在提交你的行动', '等待本次结果，请勿重复操作。');
    const command = {requestId: pendingRequest.requestId, candidateId: candidate.id, precondition: candidate.precondition, connectionId};
    if (route) command.route = route;
    try {
      const data = await api('command', command);
      applyReceipt(data.receipt);
    } catch (failure) {
      receiptUnresolved = true; pendingDetail = errors[failure.message] || '';
      status('行动结果待查证', pendingDetail || '请求没有取得明确结果。正在读回现场并核对收据。', 'warning', true);
    } finally { submitting = false; synchronized = false; await synchronize(); }
  }
  $('player-route-undo').addEventListener('click', () => {
    if (!routeDraft || !canWrite()) return;
    routeDraft.steps.pop();
    renderRoute();
    if ($('player-route-undo').disabled) directionButtons.find(button => !button.disabled)?.focus();
  });
  $('player-route-cancel').addEventListener('click', () => {
    const trigger = routeDraft?.trigger;
    routeDraft = null;
    renderRoute();
    trigger?.focus();
  });
  $('player-route-submit').addEventListener('click', () => {
    if (!routeDraft?.steps.length) return;
    sendCommand(routeDraft.candidate, routeDraft.steps.map(({x, y}) => ({x, y})));
  });
  $('player-close-receipt').addEventListener('click', async () => {
    if (!synchronized || submitting || !pendingRequest?.receiptKey) return;
    submitting = true;
    renderActions();
    try {
      if (readPromise) await readPromise;
      if (synchronized && pendingRequest?.receiptKey) await verifyPending(true);
    } catch (failure) {
      pendingDetail = errors[failure.message] || '仍无法查证这次行动。请重新同步，必要时由 DM 核对。';
    } finally { submitting = false; synchronized = false; await synchronize(); }
  });
  $('player-invitation').value = invitation; invitation = ''; fragment.delete('invite');
  $('player-join-form').addEventListener('submit', async event => {
    event.preventDefault(); if (submitting || !session) return; submitting = true; renderActions();
    const form = event.currentTarget;
    try { await api('join', {invitation: form.elements.invitation.value, nickname: form.elements.nickname.value.normalize('NFC')}); form.elements.invitation.value = ''; clearProjection(); }
    catch (failure) { status('还未加入战役', errors[failure.message] || '申请没有完成，请核对邀请码并联系 DM。', 'warning'); }
    finally { submitting = false; render(); if (session?.status === 'WAITING') await synchronize(); }
  });
  $('player-takeover').addEventListener('click', async () => {
    if (!synchronized || submitting || receiptUnresolved || pendingRequest) return; submitting = true; renderActions();
    try { const data = await api('takeover', {}); connectionId = data.connectionId || ''; synchronized = false; }
    catch (failure) { status('暂时无法启用操作', errors[failure.message] || '请重新同步，确认当前席位和运行状态。', 'warning'); }
    finally { submitting = false; await synchronize(); }
  });
  $('player-resume').addEventListener('click', async () => {
    if (submitting || !session) return; submitting = true; renderActions();
    try { await api('resume', {}); synchronized = false; }
    catch (failure) { status('席位尚未恢复', errors[failure.message] || '此设备暂时无法恢复，请 DM 核对已有成员后重新邀请。', 'warning'); }
    finally { submitting = false; render(); if (session?.status === 'APPROVED') await synchronize(); }
  });
  $('player-proposal-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!synchronized || submitting) return; submitting = true; renderActions();
    const form = event.currentTarget;
    try { await api('proposal', {characterId: form.elements.characterId.value, text: form.elements.text.value.normalize('NFC')}); form.elements.text.value = ''; }
    catch (failure) { status('提案尚未确认送达', errors[failure.message] || '请先查看“我的提案”，确认后再决定是否重新提出。提案不会自动执行。', 'warning', true); }
    finally { submitting = false; await synchronize(); }
  });
  $('player-sync').addEventListener('click', () => { synchronized = false; renderActions(); synchronize(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { synchronized = false; renderActions(); synchronize(); } else schedule(); });
  window.addEventListener('offline', () => { synchronized = false; candidates = []; renderActions(); connected(false); status('连接中断，操作已锁定', '恢复连接后先同步现场并核对未确认的结果。没有离线行动队列。', 'error', true); });
  window.addEventListener('online', () => synchronize());
  window.addEventListener('pagehide', () => { clearTimeout(timer); synchronized = false; candidates = []; renderActions(); });
  window.addEventListener('pageshow', event => { if (event.persisted) { synchronized = false; renderActions(); synchronize(); } });
  synchronize();
})();
