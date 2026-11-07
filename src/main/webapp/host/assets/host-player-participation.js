'use strict';
(() => {
  const $ = id => document.getElementById(id), base = '/api/host/storage/players/';
  let state = null, hostView = null, busy = false, readPromise = null, timer = null, invitationUrl = '', proposalRenderKey = '';
  const panel = $('host-players');
  const messages = {PLAYER_DISABLED:'玩家 HTTPS 入口尚未配置。配置正确地址与受信任证书后，再开放加入。',PLAYER_UNAVAILABLE:'玩家参与暂不可用，请确认当前活动战役与入口配置。',PLAYER_RUN_UNAVAILABLE:'请先创建、打开或恢复一个战役。',PLAYER_RULES_UNAVAILABLE:'玩家参与需要含规则角色模块的战役。',
    STALE_VERSION:'现场已变化，本次设置未提交。已读取最新席位，请核对后明确操作。',STALE_REVISION:'现场已变化，本次设置未提交。已读取最新席位。',
    PLAYER_PENDING:'仍有需要处理的机会，不能在此时转交角色。请先处理当前待决事项。',DOMAIN_REJECTED:'当前状态不允许这项设置，请核对成员、角色和待处理行动。',
    INVALID_REQUEST:'请核对必填字段与当前支持的设置。',STORAGE_UNAVAILABLE:'请先创建、打开或恢复一个战役。'};
  const text = value => String(value ?? '');
  function element(tag, className, value) { const node = document.createElement(tag); if (className) node.className = className; if (value !== undefined) node.textContent = text(value); return node; }
  function message(value) { $('host-players-message').textContent = value; }
  const participation = () => state?.participation || {members:[],controls:[],shares:[],exploration:[]};
  const members = () => (participation().members || []).filter(member => member.active);
  const memberLabel = member => `${member.name} · 席位 ${member.id.slice(0,8)}`;
  const cards = () => [
    ...(hostView?.campaign?.domain?.characters || []).map(card => ({id:card.id,name:card.name,kind:card.kind})),
    ...(hostView?.campaign?.domain?.manual?.characters || []).map(history => ({id:history.current.id,name:history.current.name,kind:history.current.kind}))
  ];
  const domain = () => hostView?.campaign?.domain;
  const focusedSpace = () => domain()?.space.focus.space;
  const focusedScene = () => {
    const focus = focusedSpace();
    return focus?.kind === 'SCENE' ? domain().space.scenes.find(scene => scene.id === focus.id) : null;
  };
  const isFocused = id => {
    const focus = focusedSpace(), place = domain()?.positions.find(p => p.characterId === id)?.space;
    return Boolean(focus && place && focus.kind === place.kind && focus.id === place.id);
  };
  const pcCards = () => cards().filter(card => card.kind === 'PC');
  const currentView = () => Boolean(state && hostView?.campaign
    && String(state.epoch) === String(hostView.storage.epoch)
    && String(state.revision) === String(hostView.campaign.revision));
  function selectOptions(select, values, label, blank) {
    const selected = select.value; select.replaceChildren();
    if (blank) { const option = element('option', '', blank); option.value = ''; select.append(option); }
    for (const value of values) { const option = element('option', '', label(value)); option.value = value.id; select.append(option); }
    if (Array.from(select.options).some(option => option.value === selected)) select.value = selected;
  }
  function setBusy(value) { busy = value; panel.setAttribute('aria-busy', String(value)); for (const button of panel.querySelectorAll('button')) button.disabled = value; if (!value && state) { renderControls(); renderExplorationForm(); } }
  async function digest(action, epoch, revision, fields) {
    const bytes = [], integer = value => bytes.push(value >>> 24 & 255, value >>> 16 & 255, value >>> 8 & 255, value & 255);
    const string = value => { const encoded = new TextEncoder().encode(String(value)); integer(encoded.length); bytes.push(...encoded); };
    string('DND_STORAGE_HTTP_V1'); string(action); string(epoch); string(revision); const keys = Object.keys(fields).sort(); integer(keys.length);
    for (const key of keys) { string(key); integer(fields[key].length); for (const value of fields[key]) string(value); }
    const result = await crypto.subtle.digest('SHA-256', new Uint8Array(bytes)); return Array.from(new Uint8Array(result), byte => byte.toString(16).padStart(2, '0')).join('');
  }
  async function command(action, fields = {}) {
    if (busy || !state) return;
    if (!currentView()) { message('正在同步最新玩家席位，请核对后再操作。'); refresh(false); return; }
    const context = {epoch:state.epoch,revision:state.revision,csrf:state.csrf};
    setBusy(true); message('正在核对并更新玩家席位…');
    let sent = false;
    try {
      const values = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, [String(value)]]));
      const requestDigest = await digest('players/' + action, context.epoch, context.revision, values), body = new URLSearchParams();
      for (const [key, entries] of Object.entries(values)) for (const value of entries) body.append(key, value);
      sent = true;
      const response = await fetch(base + action, {method:'POST',credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8',
        'X-CSRF-Token':context.csrf,'X-Host-State-Epoch':context.epoch,'X-Object-Row-Version':context.revision,'X-Request-Id':crypto.randomUUID(),'X-Request-Digest':requestDigest},body:body.toString()});
      const data = await response.json();
      if (!response.ok || data.status === 'ERROR') { const failure = new Error(data.code || 'PLAYER_UNAVAILABLE'); failure.definitive = response.status < 500; throw failure; }
      state = {...state,...data,csrf:data.csrf || state.csrf}; render(); document.dispatchEvent(new Event('dnd:storage-refresh')); message(action === 'approve' ? '该申请已逐人核对并批准。接下来可分配玩家角色。' : action === 'proposal-execute' ? '裁定已明确执行，请核对实际业务结果。' : '玩家设置已更新。');
    } catch (failure) {
      message(sent && !failure.definitive ? '本次设置的结果尚未确认。请刷新并核对实际席位；不会自动重复提交。' : messages[failure.message] || '设置未完成，请核对当前战役与玩家状态。');
      await refresh(false);
    } finally { setBusy(false); }
  }
  function governance(action, payload) { return command(action, {payload:JSON.stringify(payload)}); }
  function renderControls() {
    const enabled = state?.opened === true, locked = busy || !currentView();
    for (const button of panel.querySelectorAll('button')) button.disabled = locked;
    $('host-players-refresh').disabled = busy;
    for (const form of panel.querySelectorAll('form')) for (const button of form.querySelectorAll('button')) button.disabled = locked || !enabled;
    $('host-players-invite').disabled = !hostView?.campaign || !state?.configured || locked; $('host-players-close').disabled = !state?.invitationOpen || locked; $('host-players-pause').disabled = !enabled || locked;
    $('host-players-pause').textContent = state?.paused ? '恢复玩家操作' : '暂停玩家操作';
    $('host-players-state').textContent = !enabled ? '玩家操作尚未开放' : state.paused ? '玩家操作已暂停' : '玩家操作已开放';
    $('host-players-state-note').textContent = !enabled ? '在活动战役中显式开放加入。' : state.paused ? '玩家仍可查看与查证；当前待决行动原样保留。' : '已批准玩家按合法机会行动；关闭这个浏览器不会自动暂停。';
  }
  function renderInvitation() {
    const show = state.invitationOpen && state.invitation && state.playerOrigin;
    $('host-player-invitation').hidden = !show;
    if (!show) { invitationUrl = ''; $('host-player-qr').replaceChildren(); $('host-player-link').removeAttribute('href'); $('host-player-link').textContent = ''; return; }
    const url = state.playerOrigin + '/player#invite=' + encodeURIComponent(state.invitation);
    if (url !== invitationUrl) {
      invitationUrl = url; $('host-player-link').href = url; $('host-player-link').textContent = url;
      try { window.LocalInviteQr.render($('host-player-qr'), url); } catch (_) { $('host-player-qr').replaceChildren(element('p', 'player-small', '邀请较长，请复制完整链接。')); }
    }
    const expiry = state.invitationExpires ? new Date(Number(state.invitationExpires)) : null;
    $('host-player-invitation-expiry').textContent = expiry && !Number.isNaN(expiry.getTime()) ? `有效至 ${expiry.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}，到期后请重新开放。` : '本次邀请有时效；失效后请重新开放。';
  }
  function renderApplications() {
    const container = $('host-player-applications'), selected = new Map(Array.from(container.querySelectorAll('[data-application]'),node=>[node.dataset.application,node.querySelector('select').value])); container.replaceChildren(); const applications = state.applications || [];
    $('host-player-application-count').textContent = applications.length;
    if (!applications.length) container.append(element('p', 'player-empty', '暂无待核对申请。'));
    for (const application of applications) {
      const article = element('article', 'player-application'); article.dataset.application=application.applicationId; article.append(element('h4', '', application.nickname),element('p','player-small','先与在场玩家确认，再选择新成员或明确绑定已有成员。'));
      const label = element('label','','成员身份'), select = element('select'); select.setAttribute('aria-label', `${application.nickname} 的成员身份`);
      selectOptions(select,members(),member => `绑定已有成员：${memberLabel(member)}`,'建立独立新成员'); if(selected.has(application.applicationId)&&Array.from(select.options).some(option=>option.value===selected.get(application.applicationId)))select.value=selected.get(application.applicationId); label.append(select); article.append(label);
      const actions = element('div','actions'), approve = element('button','','核对并批准'), reject = element('button','player-secondary','拒绝申请'); approve.type = reject.type = 'button';
      approve.addEventListener('click',()=>command('approve',{applicationId:application.applicationId,memberId:select.value})); reject.addEventListener('click',()=>command('reject',{applicationId:application.applicationId})); actions.append(approve,reject); article.append(actions); container.append(article);
    }
  }
  function renderMembers() {
    const container = $('host-player-members'); container.replaceChildren();
    if (!members().length) container.append(element('p','player-empty','还没有已批准成员。'));
    for (const member of members()) {
      const article = element('article','player-member'), assignments = (participation().controls || []).filter(control => control.memberId === member.id);
      article.append(element('h4','',memberLabel(member)),element('p','player-small',assignments.length ? `控制：${assignments.map(control => cards().find(card => card.id === control.characterId)?.name || '玩家角色').join('、')}` : '尚未分配玩家角色'));
      const revoke = element('button','player-secondary','撤销这个成员的授权'); revoke.type='button'; revoke.addEventListener('click',()=>governance('member',{memberId:member.id,memberVersion:String(member.version),name:member.name,active:false})); article.append(revoke); container.append(article);
    }
    for (const select of panel.querySelectorAll('.player-member-choice')) selectOptions(select,members(),memberLabel);
    for (const select of panel.querySelectorAll('.player-pc-choice')) selectOptions(select,pcCards(),card=>card.name);
    const receivers = $('host-player-share-members'), checked = new Set(Array.from(receivers.querySelectorAll('input:checked'),input=>input.value));
    receivers.replaceChildren(element('legend','','指定接收成员'));
    for (const member of members()) { const label=element('label'),input=element('input'); input.type='checkbox';input.value=member.id;input.checked=checked.has(member.id);label.append(input,document.createTextNode(memberLabel(member)));receivers.append(label); }
    renderShareSubjects();
    renderExplorationForm();
  }
  function renderShareSubjects() {
    const form=$('host-player-share-form'), kind=form.elements.kind.value;
    const choices=kind==='CHARACTER' ? cards() : kind==='SCENE' ? [...(domain()?.nodes || []), ...(domain()?.space.scenes || [])] : hostView?.campaign ? [{id:hostView.campaign.campaignId,name:'持续共享获准的行动记录'}] : [];
    selectOptions(form.elements.subjectId,choices,value=>value.name);
    renderSharePolicy();
  }
  function renderSharePolicy() {
    const form=$('host-player-share-form'),share=(participation().shares || []).find(share=>share.kind===form.elements.kind.value&&share.subjectId===form.elements.subjectId.value),key=[form.elements.kind.value,form.elements.subjectId.value,share?.version,share?.active].join(':');
    let current=$('host-player-share-current');if(!current){current=element('p','player-small');current.id='host-player-share-current';form.elements.subjectId.closest('label').after(current);}
    current.textContent=!share?.active?'当前未共享此内容。':share.audience.kind==='ALL'?'当前持续共享给全体已批准成员。':`当前共享给：${(share.audience.memberIds || []).map(id=>members().find(member=>member.id===id)?.name||'已停用成员').join('、')}`;
    if(form.dataset.policyKey===key)return;form.dataset.policyKey=key;
    form.elements.audience.value=!share?'ALL':!share.active?'NONE':share.audience.kind==='SPECIFIC'?'SELECTED':'ALL';
    for(const input of $('host-player-share-members').querySelectorAll('input'))input.checked=Boolean(share?.audience?.memberIds?.includes(input.value));
    $('host-player-share-members').hidden=form.elements.audience.value!=='SELECTED';
  }
  function renderProposals() {
    const key=JSON.stringify({proposals:state.proposals,members:members().map(member=>[member.id,member.name]),
      eligibility:(domain()?.characters || []).map(card=>[card.id,card.active,isFocused(card.id)]),focus:focusedSpace(),resolution:focusedScene()?.resolution});
    if(key===proposalRenderKey)return;proposalRenderKey=key;
    const container=$('host-player-proposals');container.replaceChildren(); const proposals=(state.proposals || []).filter(proposal=>proposal.state==='WAITING_DM');
    if (!proposals.length) container.append(element('p','player-empty','暂无等待裁定的提案。'));
    for (const proposal of proposals) {
      const article=element('article','player-proposal'), member=members().find(member=>member.id===(proposal.memberId || proposal.playerKey)), card=cards().find(card=>card.id===proposal.characterId);
      article.append(element('h4','',`${member?.name || '玩家'} · ${card?.name || '玩家角色'}`),element('span','player-label','提案 · 尚未执行'),element('p','',proposal.text));
      const reject=element('button','player-secondary','拒绝本次提案');reject.type='button';reject.addEventListener('click',()=>command('proposal-reject',{proposalId:proposal.id}));article.append(reject);
      const ruleCard=domain()?.characters.find(card=>card.id===proposal.characterId && card.active);
      const canAdjudicate=ruleCard && isFocused(ruleCard.id) && focusedScene()?.resolution!=='RULES';
      if(canAdjudicate){
        const form=element('form'),title=element('h4','','属性检定裁定'),grid=element('div','player-adjudicate-grid');
        const abilityLabel=element('label','','使用属性'),ability=element('select');ability.name='ability';
        for(const [key,name] of [['strength','力量'],['dexterity','敏捷'],['constitution','体质'],['intelligence','智力'],['wisdom','感知'],['charisma','魅力']]){const option=element('option','',name);option.value='ability.'+key;ability.append(option);}abilityLabel.append(ability);
        const dcLabel=element('label','','DC（仅主持人可见）'),dc=element('input');dc.type='number';dc.name='dc';dc.min='0';dc.max='30';dc.step='1';dc.value='10';dc.required=true;dcLabel.append(dc);grid.append(abilityLabel,dcLabel);
        const reasonLabel=element('label','','裁定理由（最多 500 个字符）'),reason=element('textarea');reason.name='reason';reason.maxLength=1000;reason.rows=2;reason.required=true;reason.value=[...proposal.text].slice(0,500).join('');reasonLabel.append(reason);
        const note=element('p','player-small','此类型只执行一次属性检定，不附加任意状态或结果。选择 DC 不保证成功。'),execute=element('button','','裁定并执行检定');execute.type='submit';
        form.append(title,grid,reasonLabel,note,execute);form.addEventListener('submit',event=>{event.preventDefault();const normalizedReason=reason.value.normalize('NFC');if([...normalizedReason].length>500){message('裁定理由最多 500 个字符，请缩短后再执行。');reason.focus();return;}command('proposal-execute',{proposalId:proposal.id,ability:ability.value,dc:dc.value,reason:normalizedReason});});article.append(form);
      }else article.append(element('p','player-small',ruleCard?'请将焦点切到该角色所在的世界节点或 DM 裁定场景，再执行此检定。':'这个角色可通过叙事回应或由 DM 在自由卡中裁定。'));
      const narrative=element('form'),narrativeLabel=element('label','','仅回应叙事'),reply=element('textarea');reply.name='text';reply.maxLength=1000;reply.rows=2;reply.required=true;reply.placeholder='说明现场发生了什么，或补充需要玩家确认的情境。';narrativeLabel.append(reply);const respond=element('button','player-secondary','回应叙事，不执行检定');respond.type='submit';narrative.append(narrativeLabel,respond);narrative.addEventListener('submit',event=>{event.preventDefault();command('proposal-narrative',{proposalId:proposal.id,text:reply.value.normalize('NFC')});});article.append(narrative);container.append(article);
    }
  }
  function render() {
    if (!state) return;
    if (!state.configured) { $('host-players-content').hidden=true; message('玩家 HTTPS 入口尚未配置。请先按部署说明设置准确的局域网地址与受信任证书，再开放加入。'); return; }
    if (!hostView?.campaign) { $('host-players-content').hidden=true; invitationUrl='';$('host-player-qr').replaceChildren();$('host-player-link').removeAttribute('href');$('host-player-link').textContent='';message('请先创建、打开或恢复一个战役，再邀请玩家入席。');return; }
    $('host-players-content').hidden=false;renderInvitation();renderApplications();renderMembers();renderProposals();renderControls();
  }
  async function refresh(showMessage=true) {
    if(readPromise) return readPromise;
    readPromise=(async()=>{try {const response=await fetch(base+'state',{credentials:'same-origin',cache:'no-store'}),data=await response.json();
      if(!response.ok || data.status==='ERROR') throw new Error(data.code || 'PLAYER_UNAVAILABLE');
      const previous=state;state=data;
      if(!busy && (!previous || JSON.stringify(previous)!==JSON.stringify(state))) render();
      if(state.configured && hostView?.campaign && String(hostView.campaign.revision)!==String(state.revision)) document.dispatchEvent(new Event('dnd:storage-refresh'));
      if(showMessage && state.configured && hostView?.campaign) message('逐人核对加入、分配角色，并选择明确的共享受众。');
    }catch(failure){if(showMessage) message(messages[failure.message] || '暂时无法读取玩家席位。');}
    finally{readPromise=null;clearTimeout(timer);timer=setTimeout(()=>{if(!document.hidden && !busy) refresh(false);else schedule();},document.hidden?15000:4000);}})();return readPromise;
  }
  function schedule(){clearTimeout(timer);timer=setTimeout(()=>refresh(false),document.hidden?15000:4000);}
  document.addEventListener('dnd:storage-view',event=>{hostView=event.detail;if(state&&!busy){render();if(state.configured&&hostView?.campaign&&!currentView())refresh(false);}});
  $('host-players-refresh').addEventListener('click',()=>refresh());
  $('host-players-invite').addEventListener('click',()=>command('invite'));
  $('host-players-close').addEventListener('click',()=>command('close'));
  $('host-players-pause').addEventListener('click',()=>command('pause',{paused:String(!state.paused)}));
  $('host-player-copy').addEventListener('click',async()=>{if(!invitationUrl)return;try{await navigator.clipboard.writeText(invitationUrl);message('完整邀请已复制。请只交给本次希望邀请的玩家。');}catch(_){message('浏览器未允许复制，请选择上方完整邀请链接手动复制。');}});
  $('host-player-control-form').addEventListener('submit',event=>{event.preventDefault();const values=new FormData(event.currentTarget),control=(participation().controls || []).find(control=>control.characterId===values.get('characterId'));governance('control',{characterId:values.get('characterId'),controlVersion:String(control?.version??0),memberId:values.get('operation')==='revoke'?null:values.get('memberId')});});
  const explorationForm = $('host-player-exploration-form');
  const permissionFor = id => (participation().exploration || []).find(permission => permission.characterId === id);
  function renderExplorationForm() {
    const values = explorationForm.elements, id = values.characterId.value;
    const focus = focusedSpace(), scene = focusedScene();
    const revoke = values.enabled.value === 'false';
    const supported = domain()?.characters.some(card => card.id === id && card.active) && isFocused(id)
      && (focus?.kind === 'WORLD' || scene?.resolution === 'RULES' && scene.mode === 'EXPLORATION');
    for (const action of explorationForm.querySelectorAll('[name="allowedAction"]'))
      action.disabled = revoke || !supported || (focus.kind === 'WORLD' && action.value !== 'CHARACTER_COMMAND');
    explorationForm.querySelector('button').disabled = busy || (revoke ? !permissionFor(id)?.active : !supported);
    $('host-player-exploration-note').textContent = revoke ? '撤回该角色的现有探索许可。'
      : supported ? focus.kind === 'WORLD' ? '许可绑定角色所在的焦点世界节点。' : '许可绑定角色所在的焦点规则探索场景。'
      : '请先将规则 PC 安置在焦点世界节点或规则探索场景。';
  }
  explorationForm.addEventListener('change', renderExplorationForm);
  explorationForm.addEventListener('submit', event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget), characterId = values.get('characterId');
    const permission = permissionFor(characterId), active = values.get('enabled') === 'true';
    const actions = active ? values.getAll('allowedAction').sort() : permission?.actions;
    if (!actions?.length) { message('请至少选择一个有限操作范围。'); return; }
    const sceneId = active ? focusedSpace()?.id : permission?.sceneId;
    if (!sceneId || active && !isFocused(characterId)) { message('请核对角色的准确探索范围。'); return; }
    governance('exploration', {characterId, permissionVersion:String(permission?.version ?? 0), sceneId, actions, active});
  });
  const shareForm=$('host-player-share-form');shareForm.elements.kind.querySelector('[value="CARD"]').value='CHARACTER';shareForm.elements.kind.addEventListener('change',renderShareSubjects);
  shareForm.elements.subjectId.addEventListener('change',renderSharePolicy);
  shareForm.elements.audience.addEventListener('change',()=>$('host-player-share-members').hidden=shareForm.elements.audience.value!=='SELECTED');
  shareForm.addEventListener('submit',event=>{event.preventDefault();const values=new FormData(event.currentTarget),share=(participation().shares || []).find(share=>share.kind===values.get('kind')&&share.subjectId===values.get('subjectId'));const selected=Array.from($('host-player-share-members').querySelectorAll('input:checked'),input=>input.value).sort();if(values.get('audience')==='SELECTED'&&!selected.length){message('请至少选择一位接收成员。');return;}governance('share',{shareId:share?.id||null,shareVersion:String(share?.version??0),kind:values.get('kind'),subjectId:values.get('subjectId'),audience:{type:'participation.Audience',kind:values.get('audience')==='SELECTED'?'SPECIFIC':'ALL',memberIds:values.get('audience')==='SELECTED'?selected:[]},active:values.get('audience')!=='NONE'});});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh(false);});
  refresh();
})();
