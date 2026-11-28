'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady, waitForHistoryCount, showCharacter} = require('./browser-action.cjs');
const {createNode, focusSpace, placeAtFocus, createScene, enterScene} = require('./browser-spaces.cjs');

const hostOrigin = 'http://127.0.0.1:8080';
const playerOrigin = 'https://127.0.0.1:8443';
const wolfName = '林地灰狼';

async function main() {
  const [flag, storageRoot, scenario] = process.argv.slice(2);
  assert.ok(flag === '--fresh-root' && path.isAbsolute(storageRoot), 'Expected --fresh-root /absolute/disposable/player-store');
  const scenarios = ['--movement', '--ranged', '--observation', '--surprise', '--cantrips', '--fighter', '--rogue', '--sneak-attack'];
  assert.ok(scenario === undefined || scenarios.includes(scenario), 'Expected a scenario from ' + scenarios.join(', '));
  const ranged = scenario === '--ranged';
  const observation = scenario === '--observation';
  const surprise = scenario === '--surprise';
  const cantrips = scenario === '--cantrips';
  const fighterFeatures = scenario === '--fighter';
  const rogueFeatures = scenario === '--rogue';
  const sneakAttack = scenario === '--sneak-attack';
  const monsterName = fighterFeatures || sneakAttack ? '林间地狱犬' : observation ? '林间哥布林' : ranged ? '林间骷髅' : wolfName;
  const root = await fs.realpath(storageRoot);
  const repo = await fs.realpath(path.join(__dirname, '../..'));
  assert.ok(root !== repo && !root.startsWith(repo + path.sep));
  assert.equal((await fs.lstat(storageRoot)).isSymbolicLink(), false);
  assert.deepEqual((await fs.readdir(root)).sort(), ['instance.lock', 'runs', 'saves']);
  for (const name of ['runs', 'saves']) assert.deepEqual(await fs.readdir(path.join(root, name)), []);

  const args = [];
  if (process.env.DND_PLAYER_TEST_SPKI) {
    assert.match(process.env.DND_PLAYER_TEST_SPKI, /^[A-Za-z0-9+/]{43}=$/);
    args.push('--ignore-certificate-errors-spki-list=' + process.env.DND_PLAYER_TEST_SPKI);
  }
  const browser = await chromium.launch({headless: true, args});
  try {
    const errors = [];
    async function newPage() {
      const page = await browser.newPage({locale: 'zh-CN'});
      page.on('pageerror', error => errors.push(error.message));
      page.setDefaultTimeout(30000);
      return page;
    }
    const host = await newPage(), attacker = await newPage(), observer = await newPage();
    const field = (form, name) => host.locator('#' + form).locator('[name="' + name + '"]');
    const submit = form => host.locator('#' + form + ' button').click();
    const hostAction = async (action, trigger) => {
      if (!cantrips && !fighterFeatures && !rogueFeatures && !sneakAttack) return (await observeStorageAction(host, action, trigger, {})).body;
      // Participation polling can refresh the Host while a form submission is finishing.
      const [response] = await Promise.all([
        host.waitForResponse(response => response.url() === hostOrigin + '/api/host/storage/' + action
          && response.request().method() === 'POST', {timeout: 120000}),
        trigger()
      ]);
      const body = await response.json();
      const actionKind = action === 'domain' ? new URLSearchParams(response.request().postData()).get('action') : null;
      const scope = actionKind ? `${action} / ${actionKind}` : action;
      const result = `Host ${scope} returned HTTP ${response.status()}${body.code ? ' ' + body.code : ''}`;
      assert.equal(response.status(), 200, result);
      assert.equal(body.status, 'OK', result);
      await waitForStorageReady(host);
      return body;
    };
    const domainAction = async form => {
      try { return await hostAction('domain', () => submit(form)); }
      catch (failure) { throw new Error(`Host form #${form}: ${failure.message}`, {cause: failure}); }
    };
    const readHost = () => host.evaluate(async () => (await fetch('/api/host/storage/state')).json());
    const readPlayer = page => page.evaluate(async () => (await fetch('/api/player/state')).json());
    async function governance(action, trigger) {
      const [reply] = await Promise.all([
        host.waitForResponse(r => r.url() === hostOrigin + '/api/host/storage/players/' + action && r.request().method() === 'POST'),
        trigger()
      ]);
      assert.equal(reply.status(), 200, `Host players/${action} returned HTTP ${reply.status()} ${(await reply.json()).code || ''}`);
      await host.locator('#host-players[aria-busy="false"]').waitFor();
    }
    async function selectRule(form, name, ruleKey) {
      const select = field(form, name);
      const value = await select.locator('option').evaluateAll((rows, key) => rows.find(r => r.value.endsWith('/' + key))?.value, ruleKey);
      assert.ok(value, ruleKey);
      await select.selectOption(value);
    }
    async function selectRules(form, name, keys) {
      const select = field(form, name);
      const options = await select.locator('option').evaluateAll(rows => rows.map(row => row.value));
      await select.selectOption(keys.map(key => {
        const option = options.find(value => value.endsWith('/' + key));
        assert.ok(option, key);
        return option;
      }));
    }
    async function share(kind, id, audience, memberId) {
      const form = 'host-player-share-form';
      await field(form, 'kind').selectOption(kind);
      await field(form, 'subjectId').selectOption(id);
      await field(form, 'audience').selectOption(audience);
      if (memberId) await host.locator('#host-player-share-members input[value="' + memberId + '"]').check();
      await governance('share', () => submit(form));
    }
    async function clickPlayerCommand(page, button, label) {
      const [response] = await Promise.all([
        page.waitForResponse(r => r.url() === playerOrigin + '/api/player/command' && r.request().method() === 'POST'),
        button.click()
      ]);
      const body = await response.json();
      assert.equal(response.status(), 200, `Player command ${label} returned HTTP ${response.status()} ${body.code || ''}`);
      return {request: response.request().postDataJSON(), receipt: body.receipt};
    }
    async function playerCommand(label, page = attacker) {
      return (await clickPlayerCommand(page, page.getByRole('button', {name: label}), label)).receipt;
    }
    async function postPlayer(page, action, input, connectionId) {
      const {session} = await readPlayer(page);
      return page.evaluate(async ({action, input, connectionId, csrf}) => {
        const response = await fetch('/api/player/' + action, {method: 'POST',
          headers: {'Content-Type': 'application/json', 'X-CSRF-Token': csrf, 'X-Player-Connection': connectionId},
          body: JSON.stringify(input)});
        const result = await response.json();
        return {status: response.status, code: result.code, receipt: result.receipt};
      }, {action, input, connectionId, csrf: session.csrf});
    }
    async function takeControl(page) {
      const [response] = await Promise.all([
        page.waitForResponse(reply => reply.url() === playerOrigin + '/api/player/takeover'),
        page.locator('#player-takeover').click()
      ]);
      assert.equal(response.status(), 200);
      return (await response.json()).connectionId;
    }

    await host.goto(hostOrigin + '/host/storage');
    await waitForStorageReady(host);
    assert.equal((await readHost()).storage.state, 'IDLE');
    const campaignName = sneakAttack ? '游荡者偷袭验收' : rogueFeatures ? '游荡者专精与灵巧动作验收'
      : fighterFeatures ? '战士防御与动作如潮验收' : cantrips ? '已知戏法验收' : surprise ? '受惊与分组先攻验收'
      : observation ? '玩家观测与掩护验收' : ranged ? '玩家远程验收' : '玩家近战验收';
    await field('storage-create', 'name').fill(campaignName);
    await hostAction('create', () => host.locator('#storage-create button[type="submit"]').click());
    const fighters = [];
    const characters = cantrips
      ? [
        {name: '巡林法师', owner: 'class.wizard', known: ['spell.fire_bolt', 'spell.mage_hand', 'spell.prestidigitation']},
        {name: '营地牧师', owner: 'class.cleric', known: ['spell.guidance', 'spell.light', 'spell.thaumaturgy']}
      ]
      : rogueFeatures || sneakAttack ? [
        {name: '巡林斥候', owner: 'class.rogue', expertise: ['proficiency.skill.stealth', 'tool.thieves_tools']},
        {name: '营地斥候', owner: 'class.rogue', expertise: ['proficiency.skill.acrobatics', 'proficiency.skill.perception']}
      ] : ['巡林战士', '营地战士'].map(name => ({name, owner: 'class.fighter'}));
    for (const {name, owner, known, expertise} of characters) {
      await host.locator('[data-pane="people"]').click();
      await field('domain-create', 'name').fill(name);
      await selectRule('domain-create', 'classKey', owner);
      if (owner === 'class.fighter') {
        const style = field('domain-create', 'fightingStyleKey');
        if (fighterFeatures) {
          assert.equal(await style.inputValue(), '');
          assert.equal(await style.evaluate(select => select.validity.valueMissing), true);
        }
        await selectRule('domain-create', 'fightingStyleKey', 'feature.fighter_defense');
      }
      if (known) {
        const select = field('domain-create', 'cantripKeys');
        await select.waitFor();
        const pool = await select.locator('option').evaluateAll(options => options.map(option => option.value));
        assert.ok(pool.length >= 4);
        await select.selectOption(known.map(key => {
          const option = pool.find(value => value.endsWith('/' + key));
          assert.ok(option, key);
          return option;
        }));
      }
      if (expertise) {
        await selectRule('domain-create', 'backgroundKey', 'background.acolyte');
        await selectRules('domain-create', 'skillKeys', ['acrobatics', 'athletics', 'perception', 'stealth']
          .map(skill => 'proficiency.skill.' + skill));
        await selectRules('domain-create', 'expertiseKeys', expertise);
      }
      const result = await domainAction('domain-create');
      const character = result.campaign.domain.characters.find(c => c.name === name);
      fighters.push(character.id);
      if (known) {
        assert.deepEqual(character.state.knownCantrips.map(entry => entry.spell.ruleKey).sort(), known.slice().sort());
        assert.ok(character.state.knownCantrips.every(entry => entry.characterClass.ruleKey === owner));
      }
      if (fighterFeatures) {
        const defense = character.state.features.find(feature => feature.feature.ruleKey === 'feature.fighter_defense');
        assert.equal(defense.acquiredAtClassLevel, 1);
        assert.equal(character.level, 1);
        assert.ok(!character.state.resources.some(resource => resource.identity.ruleKey === 'resource.fighter.action_surge'));
      }
      if (expertise) {
        assert.equal(character.level, 1);
        assert.equal(character.state.features.some(feature => feature.feature.ruleKey === 'feature.rogue_cunning_action'), false);
        const granted = character.state.features.find(feature => feature.feature.ruleKey === 'feature.rogue_expertise');
        assert.deepEqual(granted.expertise.map(target => target.ruleKey).sort(), expertise.slice().sort());
        assert.equal(character.expertise.length, 2);
        assert.ok(character.expertise.every(entry => entry.proficiencyContribution === 4));
      }
    }
    let weapon;
    if (!cantrips && !rogueFeatures) {
      await host.locator('[data-pane="actions"]').click();
      await field('domain-equipment', 'characterId').selectOption(fighters[0]);
      await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
      const weaponKey = sneakAttack ? 'item.dagger' : ranged ? 'item.shortbow' : 'item.longsword';
      await selectRule('domain-equipment', 'item', weaponKey);
      const acquired = await domainAction('domain-equipment');
      const fighter = acquired.campaign.domain.characters.find(c => c.id === fighters[0]);
      weapon = fighter.state.equipment.find(i => i.item.ruleKey === weaponKey).itemId;
      await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED');
      await field('domain-equipment', 'itemId').selectOption(weapon);
      await field('domain-equipment', 'location').selectOption(ranged ? 'BOTH_HANDS' : 'MAIN_HAND');
      await domainAction('domain-equipment');
      if (ranged) for (let i = 0; i < 2; i++) {
        await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
        await selectRule('domain-equipment', 'item', 'item.arrow');
        await domainAction('domain-equipment');
      }
    }
    const world = await createNode(host, hostAction, '林地');
    await focusSpace(host, hostAction, 'WORLD', world);
    await placeAtFocus(host, hostAction, fighters);
    const card = (domain, id) => domain.characters.find(character => character.id === id);
    const surgeResource = (domain, id) => card(domain, id).state.resources
      .find(resource => resource.identity.ruleKey === 'resource.fighter.action_surge');
    if (rogueFeatures) {
      const [rogue] = fighters;
      const initialExpertise = card((await readHost()).campaign.domain, rogue).expertise;
      await showCharacter(host, 'RULE', rogue);
      await host.locator('#character-browser-scroll [data-character-id="' + rogue + '"]')
        .getByText(/专精：.*熟练贡献 \+4/).waitFor();
      await host.locator('[data-pane="growth"]').click();
      await field('domain-growth', 'characterId').selectOption(rogue);
      await selectRule('domain-growth', 'class', 'class.rogue');
      const advanced = card((await domainAction('domain-growth')).campaign.domain, rogue);
      assert.equal(advanced.level, 2);
      assert.equal(advanced.state.features.find(feature => feature.feature.ruleKey === 'feature.rogue_cunning_action')
        .acquiredAtClassLevel, 2);
      assert.deepEqual(advanced.expertise, initialExpertise);
      await host.locator('[data-pane="world"]').click();
    }
    if (fighterFeatures) {
      const [fighter] = fighters;
      const form = 'domain-equipment';
      const baseArmorClass = card((await readHost()).campaign.domain, fighter).armorClass;
      await host.locator('[data-pane="actions"]').click();
      await field(form, 'characterId').selectOption(fighter);
      await field(form, 'kind').selectOption('ITEM_ACQUIRED');
      await selectRule(form, 'item', 'item.chain_mail');
      const carried = card((await domainAction(form)).campaign.domain, fighter);
      const armor = carried.state.equipment.find(item => item.item.ruleKey === 'item.chain_mail').itemId;
      assert.equal(carried.armorClass, baseArmorClass);
      for (const [kind, armorClass] of [['ITEM_EQUIPPED', 17], ['ITEM_UNEQUIPPED', baseArmorClass], ['ITEM_EQUIPPED', 17]]) {
        await field(form, 'kind').selectOption(kind);
        await field(form, 'itemId').selectOption(armor);
        if (kind === 'ITEM_EQUIPPED') await field(form, 'location').selectOption('WORN');
        const changed = card((await domainAction(form)).campaign.domain, fighter);
        assert.equal(changed.armorClass, armorClass);
        await showCharacter(host, 'RULE', fighter);
        await host.locator('#character-browser-scroll [data-character-id="' + fighter + '"]')
          .getByText(new RegExp('防护等级 ' + armorClass + ' ·')).waitFor();
      }
      await host.locator('[data-pane="growth"]').click();
      for (const id of fighters) {
        await field('domain-growth', 'characterId').selectOption(id);
        await selectRule('domain-growth', 'class', 'class.fighter');
        const advanced = (await domainAction('domain-growth')).campaign.domain;
        const granted = card(advanced, id).state.features.find(feature => feature.feature.ruleKey === 'feature.fighter_action_surge');
        assert.equal(granted.acquiredAtClassLevel, 2);
        assert.equal(card(advanced, id).level, 2);
        const uses = surgeResource(advanced, id);
        assert.equal(uses.current, 1);
        assert.equal(uses.maximum, 1);
      }
    }

    if (fighterFeatures) await host.locator('[data-pane="world"]').click();
    await field('domain-monster-create', 'name').fill(monsterName);
    const monsterKey = fighterFeatures || sneakAttack ? 'monster.hell_hound' : observation ? 'monster.goblin' : ranged ? 'monster.skeleton' : 'monster.wolf';
    await selectRule('domain-monster-create', 'monster', monsterKey);
    const created = await domainAction('domain-monster-create');
    const monster = created.campaign.domain.space.monsters.find(m => m.name === monsterName).id;
    const placements = sneakAttack ? [[fighters[0], 1, 1, 0], [monster, 2, 1, 1], [fighters[1], 2, 2, 0]]
      : cantrips ? [[fighters[0], 1, 1, 0], [monster, 4, 1, 1], [fighters[1], 1, 3, 0]]
      : observation ? [[fighters[0], 1, 1, 0], [monster, 3, 1, 1], [fighters[1], 1, 3, 0]]
      : ranged ? [[fighters[0], 1, 1, 0], [monster, 18, 1, 1], [fighters[1], 1, 2, 0]]
        : [[fighters[0], 1, 1, 0], [monster, 2, 1, 1], [fighters[1], 3, 3, 0]];
    const secondWolfName = '营地灰狼';
    let secondWolf;
    const wolfNames = [wolfName], wolves = [monster];
    if (scenario === '--movement' || surprise) {
      const additional = surprise ? [secondWolfName, '溪边灰狼', '岩地灰狼'] : [secondWolfName];
      for (const [index, name] of additional.entries()) {
        await field('domain-monster-create', 'name').fill(name);
        await selectRule('domain-monster-create', 'monster', 'monster.wolf');
        const result = await domainAction('domain-monster-create');
        const id = result.campaign.domain.space.monsters.find(m => m.name === name).id;
        wolfNames.push(name);
        wolves.push(id);
        placements.push([id, 1 + index, 2, 1]);
      }
      secondWolf = wolves[1];
    }
    if (ranged) await field('domain-scene-create', 'width').fill('20');
    const scene = await createScene(host, hostAction, '林间空地', 'RULES');
    await enterScene(host, hostAction, scene, placements);

    const seats = [attacker, observer], members = [];
    async function approvePlayers(existingMembers = []) {
      await governance('invite', () => host.locator('#host-players-invite').click());
      const invitation = await host.locator('#host-player-link').getAttribute('href');
      const nicknames = ['巡林玩家', '营地玩家'];
      for (const [index, page] of seats.entries()) {
        if (existingMembers.length) await page.goto('about:blank');
        await page.goto(invitation);
        await page.locator('#player-nickname').fill(nicknames[index]);
        await page.locator('#player-join-form button').click();
        await page.locator('#player-waiting').waitFor();
      }
      await host.getByRole('button', {name: '刷新席位', exact: true}).click();
      await host.locator('#host-player-applications .player-application').nth(1).waitFor();
      for (const [index, nickname] of nicknames.entries()) {
        const application = host.locator('#host-player-applications .player-application')
          .filter({has: host.getByRole('heading', {name: nickname, exact: true})});
        if (existingMembers.length) await application.getByRole('combobox').selectOption(existingMembers[index]);
        await governance('approve', () => application.getByRole('button', {name: '核对并批准', exact: true}).click());
      }
    }
    await approvePlayers();
    await host.getByText('角色控制与探索许可', {exact: true}).click();
    for (const [i, page] of seats.entries()) {
      await page.locator('#player-table').waitFor();
      members.push((await readPlayer(page)).session.playerKey);
      await field('host-player-control-form', 'memberId').selectOption(members[i]);
      await field('host-player-control-form', 'characterId').selectOption(fighters[i]);
      await governance('control', () => submit('host-player-control-form'));
    }
    assert.notEqual(members[0], members[1]);
    await takeControl(attacker);
    const observerConnection = sneakAttack ? await takeControl(observer) : null;
    if (surprise || cantrips || fighterFeatures) await observer.locator('#player-takeover').click();
    await host.getByText('共享给谁看', {exact: true}).click();
    await share('SCENE', scene, 'ALL');
    await share('MONSTER', monster, 'SELECTED', members[0]);
    if (secondWolf) await share('MONSTER', secondWolf, 'SELECTED', members[0]);
    await attacker.locator('#player-shared li').filter({hasText: monsterName}).waitFor();
    await observer.locator('#player-shared').getByRole('heading', {name: '林间空地'}).waitFor();
    async function assertMonsterHiddenFromObserver() {
      assert.ok(!(await observer.locator('#player-shared').innerText()).includes(monsterName));
      const view = JSON.stringify((await readPlayer(observer)).view);
      assert.ok(!view.includes(monster) && !view.includes(monsterName));
    }
    await assertMonsterHiddenFromObserver();

    await host.locator('[data-pane="combat"]').click();
    await field('domain-combat', 'kind').selectOption('REENTER_COMBAT');
    let state = await domainAction('domain-combat');
    const battle = () => state.campaign.domain.combats.find(c => c.current.combatId === scene).current;
    const active = () => battle().initiative[battle().activeOrdinal].characterId;
    const actor = id => battle().combatants.find(row => row.characterId === id);
    const endTurn = async () => {
      await field('domain-combat', 'kind').selectOption('END_TURN');
      await field('domain-combat', 'actorId').selectOption(active());
      state = await domainAction('domain-combat');
    };
    const advanceTo = async id => {
      for (let i = 0; i < battle().initiative.length && active() !== id; i++) await endTurn();
      assert.equal(active(), id);
    };
    const setCell = async (x, y, {cover = 'NONE', hideAllowed = false, blocksSight = false}) => {
      const form = 'domain-observation-cell';
      await field(form, 'combatId').selectOption(scene);
      await field(form, 'x').fill(String(x));
      await field(form, 'y').fill(String(y));
      await field(form, 'y').press('Tab');
      await field(form, 'hideAllowed').setChecked(hideAllowed);
      await field(form, 'blocksSight').setChecked(blocksSight);
      await field(form, 'cover').selectOption(cover);
      state = await domainAction(form);
      const cell = battle().observation.cells.find(cell => cell.position.x === x && cell.position.y === y);
      if (hideAllowed || blocksSight || cover !== 'NONE') assert.deepEqual(cell, {position: {x, y}, hideAllowed, blocksSight, cover});
      else assert.equal(cell, undefined);
    };
    if (sneakAttack) {
      const [rogue, ally] = fighters;
      const hp = () => battle().monsters.find(row => row.monsterId === monster).hitPoints;
      const history = () => state.campaign.domain.combats.find(row => row.current.combatId === scene);
      const attack = () => battle().flows.at(-1).attacks.find(row => row.actorId === rogue);
      const update = async () => { state = await readHost(); };
      const decisionLabels = ['使用偷袭', '放弃偷袭'];
      const pendingPanel = host.locator('#domain-pending');
      const decision = (page, label) => page.locator('#player-actions button:enabled').filter({hasText: label});
      const receiptFor = (page, request, connectionId) => {
        const {connectionId: ignored, ...identity} = request;
        return postPlayer(page, 'receipt', identity, connectionId);
      };
      function hitSummary(activeAttack) {
        return {attackTotal: activeAttack.selectedD20 + activeAttack.abilityModifier + activeAttack.proficiencyBonus
          + (activeAttack.blessingD4 ?? 0), critical: activeAttack.critical};
      }
      async function checkPending(expectedAttack) {
        await decision(attacker, decisionLabels[0]).waitFor();
        await decision(attacker, decisionLabels[1]).waitFor();
        await pendingPanel.getByRole('button', {name: decisionLabels[0], exact: true}).waitFor();
        await pendingPanel.getByRole('button', {name: decisionLabels[1], exact: true}).waitFor();
        assert.equal(await pendingPanel.getByRole('button', {name: /继续结算行动/}).count(), 0);
        const flow = battle().flows.at(-1);
        const leaf = flow.frames.find(row => row.frameKey === flow.leafFrameKey);
        assert.equal(leaf.frameKey, expectedAttack.frameKey);
        assert.deepEqual([leaf.kind, leaf.stage, leaf.status], ['ATTACK', 'SNEAK_ATTACK_CHOICE', 'WAITING_CHOICE']);
        assert.equal(expectedAttack.sneakAttack.status, 'PENDING');
        assert.equal(expectedAttack.sneakAttack.nearbyEnemyId, ally);
        assert.deepEqual(expectedAttack.damageDice, []);
        const attackerState = await readPlayer(attacker);
        assert.deepEqual(attackerState.view.pending.sneakAttack, hitSummary(expectedAttack));
        assert.equal(attackerState.view.pending.own, true);
        assert.deepEqual(attackerState.view.candidates.map(row => row.label), decisionLabels);
        assert.ok(!JSON.stringify(attackerState.view).includes(ally));
        assert.ok(!JSON.stringify(attackerState.view).includes(characters[1].name));
        const observerState = await readPlayer(observer);
        assert.equal(observerState.view.pending.own, false);
        assert.equal(observerState.view.pending.active, false);
        assert.equal(Object.hasOwn(observerState.view.pending, 'sneakAttack'), false);
        assert.deepEqual(observerState.view.candidates, []);
        await assertMonsterHiddenFromObserver();
        return attackerState.view.candidates[0];
      }
      let beforeHit;
      for (let attempt = 0; attempt < 24; attempt++) {
        await advanceTo(rogue);
        await playerCommand(/开始攻击动作/);
        beforeHit = hp();
        const originalStrike = await playerCommand(new RegExp('攻击 ' + monsterName));
        await update();
        if (attack().sneakAttack?.status === 'PENDING') {
          assert.equal(originalStrike.outcome, 'WAITING');
          assert.equal(Object.hasOwn(originalStrike, 'sneakAttackResult'), false);
          break;
        }
        assert.equal(attack().hit, false);
        await endTurn();
      }
      const waitingAttack = attack();
      assert.equal(hp(), beforeHit);
      const staleCandidate = await checkPending(waitingAttack);
      const oldRequest = {candidateId: staleCandidate.id, precondition: staleCandidate.precondition,
        requestId: await observer.evaluate(() => crypto.randomUUID()), connectionId: observerConnection};
      const denied = await postPlayer(observer, 'command', oldRequest, observerConnection);
      assert.deepEqual([denied.status, denied.code], [409, 'PLAYER_VIEW_EXPIRED']);

      const savedDomain = state.campaign.domain;
      await hostAction('save', () => host.locator('#storage-save').click());
      const ended = await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      await host.locator('#storage-files').selectOption(ended.savedFile);
      state = await hostAction('open', () => host.locator('#storage-open button[type="submit"]').click());
      assert.deepEqual(state.campaign.domain, savedDomain);
      await approvePlayers(members);
      for (const [index, page] of seats.entries()) {
        await page.locator('#player-table').waitFor();
        assert.equal((await readPlayer(page)).session.playerKey, members[index]);
      }
      const attackerWriter = await takeControl(attacker);
      const observerWriter = await takeControl(observer);
      await host.locator('[data-pane="combat"]').click();
      await checkPending(attack());
      const stale = await postPlayer(attacker, 'command', {...oldRequest, connectionId: attackerWriter}, attackerWriter);
      assert.deepEqual([stale.status, stale.code], [409, 'PLAYER_VIEW_EXPIRED']);
      assert.equal((await readHost()).campaign.domain.roots.length, savedDomain.roots.length);

      const chosen = await clickPlayerCommand(attacker, decision(attacker, decisionLabels[0]), decisionLabels[0]);
      await update();
      const resolved = attack();
      const used = {used: true, ...hitSummary(resolved), damage: resolved.damageAmount};
      assert.equal(resolved.firstD20, waitingAttack.firstD20);
      assert.equal(resolved.selectedD20, waitingAttack.selectedD20);
      assert.equal(resolved.sneakAttack.status, 'USED');
      const diceCount = resolved.critical ? 2 : 1;
      assert.equal(resolved.damageDice.length, diceCount);
      assert.equal(resolved.sneakAttack.damageDice.length, diceCount);
      assert.equal(resolved.damageAmount, Math.max(0, [...resolved.damageDice, ...resolved.sneakAttack.damageDice]
        .reduce((total, die) => total + die, resolved.abilityModifier)));
      assert.deepEqual(chosen.receipt.sneakAttackResult, used);
      assert.equal(hp(), beforeHit - used.damage);
      const usedEvent = history().events.at(-1);
      assert.equal(usedEvent.command.kind, 'USE_SNEAK_ATTACK');
      assert.ok(usedEvent.resolution.rolls.every(row => row.sides !== 20));
      assert.equal(usedEvent.resolution.rolls.filter(row => row.purpose === 'SNEAK_ATTACK_DAMAGE').length, diceCount);
      assert.equal(state.campaign.domain.roots.length, savedDomain.roots.length + 1);
      assert.equal(state.campaign.domain.roots.at(-1).actor.memberId, members[0]);
      const repeated = await postPlayer(attacker, 'command', chosen.request, attackerWriter);
      assert.deepEqual([repeated.status, repeated.code], [409, 'PLAYER_VIEW_EXPIRED']);
      assert.deepEqual((await receiptFor(attacker, chosen.request, attackerWriter)).receipt, chosen.receipt);
      assert.equal((await receiptFor(observer, chosen.request, observerWriter)).receipt.state, 'UNVERIFIED');
      assert.equal((await readHost()).campaign.domain.roots.length, savedDomain.roots.length + 1);
      await attacker.locator('#player-receipt').filter({hasText: `已使用偷袭 · 伤害合计 ${used.damage}`}).waitFor();
      await host.locator('.sneak-attack-result').filter({hasText: `伤害合计 ${used.damage}`}).waitFor();

      const moveMonster = async x => {
        await field('domain-combat', 'kind').selectOption('MOVE');
        await field('domain-combat', 'actorId').selectOption(monster);
        await field('domain-combat', 'path').fill(`${x},1`);
        state = await domainAction('domain-combat');
      };
      const finishMovement = async () => {
        const pendingContinue = pendingPanel.getByRole('button', {name: '继续结算行动', exact: true});
        while (battle().flows.at(-1).status === 'ACTIVE') {
          state = await hostAction('domain', () => pendingContinue.click());
        }
        assert.equal(actor(monster).position.x, 3);
      };
      for (let attempt = 0; attempt < 24; attempt++) {
        await advanceTo(monster);
        if (actor(monster).position.x === 3) await moveMonster(2);
        const hpBeforeOpportunity = hp();
        await moveMonster(3);
        await playerCommand(/借机攻击/);
        await update();
        assert.equal(attack().opportunityAttack, true);
        assert.equal(actor(rogue).reactionAvailable, false);
        assert.equal(active(), monster);
        if (attack().sneakAttack?.status === 'PENDING') {
          assert.notEqual(attack().sneakAttack.turnNumber, resolved.sneakAttack.turnNumber);
          await checkPending(attack());
          await share('MONSTER', monster, 'NONE');
          await update();
          await attacker.locator('#player-shared li').filter({hasText: monsterName}).waitFor({state: 'detached'});
          await checkPending(attack());
          const privateView = JSON.stringify((await readPlayer(attacker)).view);
          assert.ok(!privateView.includes(monster) && !privateView.includes(monsterName));
          const privateReceipt = await receiptFor(attacker, chosen.request, attackerWriter);
          assert.deepEqual(privateReceipt.receipt.sneakAttackResult, {used: true, ...hitSummary(resolved)});
          const declined = await clickPlayerCommand(attacker, decision(attacker, decisionLabels[1]), decisionLabels[1]);
          await update();
          const afterDecline = attack();
          assert.equal(afterDecline.sneakAttack.status, 'DECLINED');
          assert.deepEqual(afterDecline.sneakAttack.damageDice, []);
          assert.deepEqual(declined.receipt.sneakAttackResult, {used: false, ...hitSummary(afterDecline)});
          await attacker.locator('#player-receipt').filter({hasText: '已放弃偷袭'}).waitFor();
          assert.ok(!(await attacker.locator('#player-receipt').textContent()).includes('伤害合计'));
          assert.equal(hp(), hpBeforeOpportunity - afterDecline.damageAmount);
          await finishMovement();
          assert.equal(hp(), hpBeforeOpportunity - afterDecline.damageAmount);
          break;
        }
        assert.equal(attack().hit, false);
        await finishMovement();
        await advanceTo(rogue);
      }
      assert.equal(attack().sneakAttack.status, 'DECLINED');
      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, reopenedPending: true,
        deniedCrossSeat: true, idempotentReceipt: true, used, offTurnDeclinedAfterTargetRevocation: true}));
      return;
    }
    if (rogueFeatures) {
      const [rogue, novice] = fighters;
      const domain = () => state.campaign.domain;
      const history = () => domain().combats.find(row => row.current.combatId === scene);
      const lastEvent = () => history().events.at(-1);
      const budget = async () => (await readPlayer(attacker)).view.cards.find(row => row.id === rogue).turnBudget;
      const labels = async page => (await readPlayer(page)).view.candidates.map(candidate => candidate.label);
      const cunningKinds = ['CUNNING_DASH', 'CUNNING_DISENGAGE', 'CUNNING_HIDE'];
      async function rogueCommand(label) {
        const title = attacker.locator('strong').and(attacker.getByText(label, {exact: true}));
        const button = attacker.locator('#player-actions button:enabled').filter({has: title});
        await button.waitFor();
        return clickPlayerCommand(attacker, button, label);
      }
      const roleCard = attacker.locator('#player-cards .character-card').filter({
        has: attacker.getByRole('heading', {name: characters[0].name, exact: true})
      });
      const expertise = roleCard.locator('details').filter({has: attacker.locator('summary').filter({hasText: /^专精$/})});
      await expertise.locator('summary').click();
      const expertiseRows = expertise.locator('li');
      await expertiseRows.nth(1).waitFor();
      assert.deepEqual((await expertiseRows.allTextContents()).sort(), [
        'Stealth（熟练贡献 +4）', 'Thieves’ tools（熟练贡献 +4）'
      ]);
      await setCell(1, 1, {hideAllowed: true});
      await advanceTo(novice);
      assert.deepEqual(history().cunningActions, []);
      assert.ok(!(await labels(observer)).some(label => label.startsWith('灵巧动作')));
      assert.ok(!(await labels(attacker)).some(label => label.startsWith('灵巧动作')));
      const observerConnection = await takeControl(observer);
      await advanceTo(rogue);
      assert.deepEqual(history().cunningActions, cunningKinds);
      assert.ok((await labels(attacker)).includes('躲藏（动作）'));
      const firstTurn = await budget();
      const offeredHide = (await readPlayer(attacker)).view.candidates.find(candidate => candidate.label === '灵巧动作：躲藏');
      assert.ok(offeredHide);
      const stolen = {connectionId: observerConnection, candidateId: offeredHide.id,
        precondition: offeredHide.precondition, requestId: await observer.evaluate(() => crypto.randomUUID())};
      const denied = await postPlayer(observer, 'command', stolen, observerConnection);
      assert.deepEqual({status: denied.status, code: denied.code}, {status: 409, code: 'PLAYER_VIEW_EXPIRED'});
      assert.equal((await readHost()).campaign.domain.roots.length, domain().roots.length);
      const {request: exact, receipt: hideReceipt} = await rogueCommand('灵巧动作：躲藏');
      assert.equal(hideReceipt.state, 'ACCEPTED');
      assert.equal(hideReceipt.outcome, 'COMPLETED');
      const {connectionId, ...identity} = exact;
      const stale = await postPlayer(attacker, 'command', exact, connectionId);
      assert.deepEqual({status: stale.status, code: stale.code}, {status: 409, code: 'PLAYER_VIEW_EXPIRED'});
      const original = await postPlayer(attacker, 'receipt', identity, connectionId);
      assert.equal(original.status, 200);
      assert.deepEqual(original.receipt, hideReceipt);
      const otherReceipt = await postPlayer(observer, 'receipt', identity, observerConnection);
      assert.equal(otherReceipt.status, 200);
      assert.equal(otherReceipt.receipt.state, 'UNVERIFIED');
      state = await readHost();
      assert.equal(state.campaign.domain.roots.at(-1).actor.memberId, members[0]);
      const hideEvent = lastEvent();
      const hide = battle().observation.checks.at(-1);
      assert.equal(hide.kind, 'CUNNING_HIDE');
      assert.equal(hide.nimbleEscape, false);
      assert.equal(hide.roll.skillBonus, 4);
      assert.ok(Number.isInteger(hide.roll.firstD20) && hide.roll.firstD20 >= 1 && hide.roll.firstD20 <= 20);
      assert.equal(hide.roll.total, hide.roll.selectedD20 + hide.roll.abilityModifier + 4);
      assert.deepEqual(hideEvent.resolution.rolls, [
        {purpose: 'OBSERVATION_STEALTH', subjectId: rogue, sides: 20, value: hide.roll.firstD20}
      ]);
      assert.equal(hide.contests.length, 1);
      assert.equal(hide.contests[0].counterpartId, monster);
      assert.equal(hide.contests[0].hiddenAfter, hide.roll.total > hide.contests[0].opposingScore);
      assert.deepEqual(await budget(), {...firstTurn, bonusActionsRemaining: 0});
      assert.deepEqual(history().cunningActions, []);

      const beforeSave = domain();
      await hostAction('save', () => host.locator('#storage-save').click());
      const ended = await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      await host.locator('#storage-files').selectOption(ended.savedFile);
      state = await hostAction('open', () => host.locator('#storage-open button[type="submit"]').click());
      assert.deepEqual(domain(), beforeSave);
      assert.deepEqual(lastEvent(), hideEvent);
      await approvePlayers(members);
      for (const [index, page] of seats.entries()) {
        await page.locator('#player-table').waitFor();
        assert.equal((await readPlayer(page)).session.playerKey, members[index]);
      }
      await attacker.locator('#player-takeover').click();
      assert.deepEqual(await budget(), {...firstTurn, bonusActionsRemaining: 0});
      await rogueCommand('疾走');
      assert.deepEqual(await budget(), {...firstTurn, actionsRemaining: 0, bonusActionsRemaining: 0,
        movementRemainingHalfFeet: firstTurn.movementRemainingHalfFeet * 2});

      state = await readHost();
      await host.locator('[data-pane="combat"]').click();
      await endTurn();
      await advanceTo(rogue);
      const fresh = await budget();
      assert.deepEqual(fresh, firstTurn);
      await rogueCommand('灵巧动作：疾走');
      assert.deepEqual(await budget(), {...fresh, bonusActionsRemaining: 0,
        movementRemainingHalfFeet: fresh.movementRemainingHalfFeet * 2});
      await rogueCommand('疾走');
      assert.equal((await budget()).movementRemainingHalfFeet, fresh.movementRemainingHalfFeet * 3);

      state = await readHost();
      await endTurn();
      await advanceTo(rogue);
      await field('domain-combat', 'kind').selectOption('CUNNING_DISENGAGE');
      state = await domainAction('domain-combat');
      assert.equal(lastEvent().command.kind, 'CUNNING_DISENGAGE');
      assert.deepEqual(lastEvent().resolution.rolls, []);
      assert.equal(actor(rogue).disengaged, true);
      assert.deepEqual(await budget(), {...fresh, bonusActionsRemaining: 0});
      await field('domain-combat', 'kind').selectOption('MOVE');
      await field('domain-combat', 'path').fill('0,1');
      state = await domainAction('domain-combat');
      assert.deepEqual(actor(rogue).position, {x: 0, y: 1});
      assert.equal(battle().flows.at(-1).status, 'COMPLETED');
      assert.equal(actor(monster).reactionAvailable, true);
      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, expertise: 4,
        hideD20: hide.roll.firstD20, reopenedAndContinued: true, doubleDash: true, disengage: true}));
      return;
    }
    if (fighterFeatures) {
      const [fighter, otherFighter] = fighters;
      const domain = () => state.campaign.domain;
      const history = () => domain().combats.find(row => row.current.combatId === scene);
      const monsterState = () => domain().space.monsters.find(row => row.id === monster);
      const ownCard = async (page, id) => (await readPlayer(page)).view.cards.find(row => row.id === id);
      async function strike() {
        const before = monsterState();
        await playerCommand(/^开始攻击动作(?:\s|$)/);
        const {attackResult} = await playerCommand(new RegExp('^攻击 ' + monsterName));
        state = await readHost();
        const event = history().events.at(-1);
        const root = domain().roots.at(-1);
        const attempt = battle().flows.at(-1).attacks[0];
        assert.equal(event.command.kind, 'ATTACK');
        assert.equal(root.operationId, event.operationId);
        assert.deepEqual(root.actor, {kind: 'PLAYER', memberId: members[0], representedMemberId: null, characterId: fighter});
        assert.equal(attempt.actorId, fighter);
        assert.equal(attempt.targetId, monster);
        assert.equal(attempt.itemId, weapon);
        assert.equal(attempt.attackOrdinal, 0);
        assert.equal(attempt.hit, attackResult.hit);
        assert.ok(Number.isInteger(attempt.firstD20) && attempt.firstD20 >= 1 && attempt.firstD20 <= 20);
        assert.deepEqual(event.resolution.rolls.find(roll => roll.purpose === 'ATTACK_D20'),
          {purpose: 'ATTACK_D20', subjectId: fighter, sides: 20, value: attempt.firstD20});
        assert.equal(monsterState().hitPoints, Math.max(0, before.hitPoints - attackResult.damage));
        assert.equal(monsterState().dead, attackResult.dropped);
        await attacker.locator('#player-receipt').filter({hasText: `上次攻击${attackResult.hit ? '命中' : '未命中'}，造成 ${attackResult.damage} 点伤害`}).waitFor();
        await waitForHistoryCount(host, domain().roots.length);
        return attempt;
      }

      await advanceTo(fighter);
      assert.equal((await ownCard(attacker, fighter)).armorClass, 17);
      await attacker.locator('#player-cards .character-stats').filter({hasText: /17.*防护等级/}).waitFor();
      assert.equal(actor(fighter).actionsRemaining, 1);
      const first = await strike();
      assert.equal(actor(fighter).actionsRemaining, 0);
      assert.equal(surgeResource(domain(), fighter).current, 1);
      const beforeSurge = (await ownCard(attacker, fighter)).turnBudget;
      const receipt = await playerCommand(/^动作如潮(?:\s|$)/);
      assert.equal(receipt.state, 'ACCEPTED');
      assert.equal(receipt.outcome, 'COMPLETED');
      state = await readHost();
      const surge = history().events.at(-1);
      assert.equal(surge.command.kind, 'ACTION_SURGE');
      assert.deepEqual(surge.resolution.rolls, []);
      assert.equal(surgeResource(domain(), fighter).current, 0);
      assert.deepEqual((await ownCard(attacker, fighter)).turnBudget, {...beforeSurge, actionsRemaining: 1});
      await attacker.locator('#player-actions').getByRole('button', {name: /^动作如潮(?:\s|$)/}).waitFor({state: 'detached'});

      const retained = domain();
      await hostAction('save', () => host.locator('#storage-save').click());
      const ended = await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      await host.locator('#storage-files').selectOption(ended.savedFile);
      state = await hostAction('open', () => host.locator('#storage-open button[type="submit"]').click());
      assert.deepEqual(domain(), retained);
      assert.equal(active(), fighter);
      assert.deepEqual(history().events.find(event => event.operationId === surge.operationId), surge);
      await approvePlayers(members);
      for (const [index, page] of seats.entries()) {
        await page.locator('#player-table').waitFor();
        assert.equal((await readPlayer(page)).session.playerKey, members[index]);
        await page.locator('#player-takeover').click();
      }
      assert.equal((await ownCard(attacker, fighter)).armorClass, 17);
      assert.equal((await ownCard(attacker, fighter)).turnBudget.actionsRemaining, 1);
      await assertMonsterHiddenFromObserver();
      const second = await strike();
      assert.notEqual(second.sequenceKey, first.sequenceKey);
      assert.equal(actor(fighter).actionsRemaining, 0);
      assert.equal(surgeResource(domain(), fighter).current, 0);
      assert.ok(!(await readPlayer(attacker)).view.candidates.some(candidate => candidate.label === '开始攻击动作'));

      await host.locator('[data-pane="combat"]').click();
      await advanceTo(otherFighter);
      const starting = (await ownCard(observer, otherFighter)).turnBudget;
      await playerCommand(/^疾走(?:\s|$)/, observer);
      await playerCommand(/^动作如潮(?:\s|$)/, observer);
      await playerCommand(/^疾走(?:\s|$)/, observer);
      state = await readHost();
      const afterDashes = (await ownCard(observer, otherFighter)).turnBudget;
      assert.deepEqual(afterDashes, {...starting, actionsRemaining: 0,
        movementRemainingHalfFeet: 3 * starting.movementRemainingHalfFeet});
      assert.equal(surgeResource(domain(), otherFighter).current, 0);
      assert.ok(!(await readPlayer(observer)).view.candidates.some(candidate => candidate.label === '疾走'));

      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, armorClass: 17,
        attackRolls: [first.firstD20, second.firstD20], reopenedAndContinued: true, doubleDash: true}));
      return;
    }
    if (cantrips) {
      const [wizard, cleric] = fighters;
      const ownCard = page => page.locator('#player-cards .character-card');
      const wizardCard = ownCard(attacker), clericCard = ownCard(observer);
      const spellName = 'Fire Bolt', otherSpell = 'Sacred Flame', label = spellName + ' · ' + monsterName;
      await wizardCard.getByText('已知戏法', {exact: true}).click();
      await wizardCard.getByText(/Fire Bolt.*可施放/).waitFor();
      await wizardCard.getByText(/Mage Hand.*仅记录/).waitFor();
      await clericCard.getByText('已知戏法', {exact: true}).click();
      await clericCard.getByText(/Guidance.*仅记录/).waitFor();
      assert.equal(await clericCard.getByText(otherSpell, {exact: false}).count(), 0);

      await advanceTo(cleric);
      await observer.locator('#player-actions button:enabled').filter({hasText: '结束我的回合'}).waitFor();
      await host.locator('#domain-cantrip').waitFor({state: 'hidden'});
      assert.ok(!(await readPlayer(observer)).view.candidates.some(candidate => candidate.label.includes(otherSpell)));

      const character = (domain, id) => domain.characters.find(entry => entry.id === id);
      const monsterHp = domain => domain.space.monsters.find(entry => entry.id === monster).hitPoints;
      let outcome, attempts = 0;
      while (attempts < 12 && outcome?.outcome !== 'HIT') {
        attempts++;
        await advanceTo(wizard);
        await host.locator('#domain-cantrip').waitFor();
        await selectRule('domain-cantrip', 'spell', 'spell.fire_bolt');
        assert.deepEqual(await field('domain-cantrip', 'spell').locator('option').allTextContents(), [spellName]);
        await field('domain-cantrip', 'targetId').selectOption(monster);
        const before = state.campaign.domain;
        const slots = character(before, wizard).state.resources;
        assert.ok(slots.some(resource => resource.identity.ruleKey === 'resource.wizard.slot1' && resource.current > 0));
        outcome = (await playerCommand(label)).cantripResult;
        assert.deepEqual(Object.keys(outcome).sort(), ['damage', 'dropped', 'outcome', 'spell']);
        assert.ok(['HIT', 'MISS'].includes(outcome.outcome));
        assert.equal(outcome.spell, spellName);
        await attacker.locator('#player-receipt').filter({hasText: `${spellName} · ${outcome.outcome === 'HIT' ? '命中' : '未命中'} · 实际伤害 ${outcome.damage}`}).waitFor();
        await wizardCard.locator('.cantrip-result').filter({hasText: spellName}).waitFor();
        state = await readHost();
        const after = state.campaign.domain;
        const flow = battle().flows.at(-1), cast = flow.casts[0], attack = flow.attacks[0];
        assert.equal(cast.spell.ruleKey, 'spell.fire_bolt');
        assert.equal(cast.slotLevel, 0);
        assert.equal(cast.actorId, wizard);
        assert.deepEqual(cast.targets, [monster]);
        assert.equal(attack.hit, outcome.outcome === 'HIT');
        assert.equal(attack.damageAmount, outcome.damage);
        assert.equal(actor(wizard).actionsRemaining, 0);
        assert.equal(monsterHp(after), Math.max(0, monsterHp(before) - outcome.damage));
        assert.equal(outcome.dropped, monsterHp(after) === 0);
        assert.deepEqual(character(after, wizard).state.resources, slots);
        assert.equal(after.roots.length, before.roots.length + 1);
        assert.equal(after.roots.at(-1).actor.memberId, members[0]);
        await waitForHistoryCount(host, after.roots.length);
        const privateView = JSON.stringify((await readPlayer(observer)).view);
        assert.ok(!/Fire Bolt|cantripResult|lastCantrip|spellAttack|selectedD20|finalArmorClass/.test(privateView));
        await observer.locator('#player-receipt').waitFor({state: 'hidden'});
        await assertMonsterHiddenFromObserver();
        if (outcome.outcome === 'MISS') await endTurn();
      }
      assert.ok(outcome?.outcome === 'HIT' && outcome.damage > 0, 'Expected a server-rolled damaging Fire Bolt hit');
      await endTurn();
      await advanceTo(wizard);
      const dmTarget = outcome.dropped ? cleric : monster;
      const beforeDmCast = state.campaign.domain;
      assert.equal(actor(wizard).actionsRemaining, 1);
      await host.locator('#domain-cantrip').waitFor();
      await selectRule('domain-cantrip', 'spell', 'spell.fire_bolt');
      await field('domain-cantrip', 'targetId').selectOption(dmTarget);
      state = await domainAction('domain-cantrip');
      const afterDmCast = state.campaign.domain;
      assert.equal(afterDmCast.roots.length, beforeDmCast.roots.length + 1);
      const dmRoot = afterDmCast.roots.at(-1);
      assert.equal(dmRoot.kind, 'combat-command');
      assert.deepEqual(dmRoot.actor, {kind: 'DM', memberId: null, representedMemberId: members[0], characterId: wizard});
      const dmEvent = afterDmCast.combats.find(history => history.current.combatId === scene).events.at(-1);
      assert.equal(dmEvent.operationId, dmRoot.operationId);
      assert.equal(dmEvent.command.kind, 'CAST');
      assert.equal(dmEvent.command.castInput.slotLevel, 0);
      assert.equal(dmEvent.command.castInput.spell.ruleKey, 'spell.fire_bolt');
      assert.deepEqual(dmEvent.command.castInput.targets, [dmTarget]);
      assert.equal(actor(wizard).actionsRemaining, 0);
      assert.deepEqual(character(afterDmCast, wizard).state.resources, character(beforeDmCast, wizard).state.resources);

      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, attempts, cantripResult: outcome, dmCast: true}));
      return;
    }
    if (surprise) {
      const combat = 'domain-combat', kind = field(combat, 'kind');
      const openingOptions = host.locator('#domain-opening');
      const row = name => openingOptions.locator('.opening-participant').filter({has: host.getByText(name, {exact: true})});
      const history = () => state.campaign.domain.combats.find(c => c.current.combatId === scene);
      const openingEvent = () => history().events.findLast(event => event.command.kind === 'REENTER_COMBAT');
      const summary = host.locator('#domain-combat-summary');
      const ownCard = page => page.locator('#player-cards .character-card');
      const openingBudget = async page => (await readPlayer(page)).view.cards[0].turnBudget;
      assert.equal(openingEvent().command.opening, null);
      assert.ok(battle().initiative.every(entry => !entry.surprised && entry.rollSource === entry.characterId));
      assert.equal(openingEvent().resolution.rolls.filter(roll => roll.purpose === 'INITIATIVE').length, 6);

      let victim;
      let attempts = 0;
      // Use real dice. Retry a full setup if this contest leaves both players alert or only the last entrant surprised.
      while (!victim && attempts++ < 16) {
        await kind.selectOption('END_COMBAT');
        state = await domainAction(combat);
        await kind.selectOption('REENTER_COMBAT');
        await openingOptions.locator('summary').click();
        assert.deepEqual(errors, []);
        assert.deepEqual((await openingOptions.locator('.opening-participant > strong').allTextContents()).sort(),
          [...wolfNames, '巡林战士', '营地战士'].sort());
        const pick = (name, label, option) => row(name).getByLabel(label, {exact: true}).selectOption({label: option});
        await pick(wolfNames[0], '开场隐匿', '共同隐匿 · 组 1');
        if (attempts === 1) {
          await submit(combat);
          await host.locator('#storage-message').filter({hasText: '共同隐匿至少需要两名同组成员'}).waitFor();
          const untouched = (await readHost()).campaign.domain.combats.find(c => c.current.combatId === scene);
          assert.equal(untouched.current.status, 'EXPLORATION');
          assert.equal(untouched.events.length, history().events.length);
        }
        for (const name of wolfNames.slice(1)) await pick(name, '开场隐匿', '共同隐匿 · 组 1');
        await pick('营地战士', '开场隐匿', '独立隐匿');
        for (const [index, name] of wolfNames.entries())
          await pick(name, '先攻掷骰', `共同先攻 · 组 ${index < 2 ? 1 : 2}`);
        await pick(wolfNames[1], '同分顺位', '1');
        const original = await row(wolfNames[0]).getByLabel('同分顺位', {exact: true}).inputValue();
        assert.ok(Number(original) > 1);

        const [request, started] = await Promise.all([
          host.waitForRequest(r => r.url() === hostOrigin + '/api/host/storage/domain' && r.method() === 'POST'),
          domainAction(combat)
        ]);
        state = started;
        const sent = JSON.parse(new URLSearchParams(request.postData()).get('payload')).command.opening;
        assert.deepEqual(Object.keys(sent).sort(), ['initiativeGroups', 'stealthGroups', 'type']);
        for (const group of [...sent.stealthGroups, ...sent.initiativeGroups])
          assert.deepEqual(Object.keys(group).sort(), ['members', 'type']);
        const event = openingEvent(), {command, resolution} = event;
        const groupMembers = groups => groups.map(group => group.members.slice().sort()).sort();
        assert.deepEqual(groupMembers(command.opening.stealthGroups), [[...wolves].sort(), [fighters[1]]].sort());
        assert.deepEqual(groupMembers(command.opening.initiativeGroups), [wolves.slice(0, 2).sort(), wolves.slice(2).sort()].sort());
        assert.equal(command.tieBreakOrder[0], secondWolf);
        const initiative = id => battle().initiative.find(entry => entry.characterId === id);
        assert.equal(initiative(wolves[0]).rollSource, initiative(wolves[1]).rollSource);
        assert.equal(initiative(wolves[2]).rollSource, initiative(wolves[3]).rollSource);
        assert.notEqual(initiative(wolves[0]).rollSource, initiative(wolves[2]).rollSource);
        assert.equal(initiative(wolves[0]).d20, initiative(wolves[1]).d20);
        assert.ok(initiative(secondWolf).ordinal < initiative(monster).ordinal);
        assert.equal(resolution.rolls.filter(roll => roll.purpose === 'INITIATIVE').length, 4);
        assert.equal(resolution.rolls.filter(roll => roll.purpose === 'OPENING_STEALTH').length, 2);
        const checks = resolution.after.observation.checks.filter(check => check.openingOperationId === event.operationId);
        const wolfChecks = checks.filter(check => wolves.includes(check.actorId));
        assert.equal(wolfChecks.length, 4);
        assert.equal(new Set(wolfChecks.map(check => check.rollSource)).size, 1);
        assert.equal(new Set(wolfChecks.map(check => check.roll.firstD20)).size, 1);
        for (const [id, x, y] of placements) assert.deepEqual(actor(id).position, {x, y});
        victim = battle().initiative.find(entry => entry.surprised && fighters.includes(entry.characterId)
          && entry.ordinal < battle().initiative.length - 1);
      }
      assert.ok(victim, 'Expected a surprised PC to receive a first turn before the round ends');
      const victimPage = seats[fighters.indexOf(victim.characterId)];
      await summary.getByText('先攻顺序与开场状态', {exact: true}).click();
      assert.equal(await summary.locator('.opening-initiative tbody tr').filter({hasText: '共用'}).count(), 4);
      await summary.locator('.opening-initiative tbody tr').filter({hasText: '开场受惊'}).first().waitFor();
      await host.locator('#campaign-log-toggle').click();
      await host.locator('#storage-history > li').last().filter({hasText: '开场隐匿'}).filter({hasText: '共用'}).waitFor();
      await host.locator('#campaign-log-close').click();
      await ownCard(victimPage).locator('.turn-surprise').filter({hasText: '结束该回合后即可反应'}).waitFor();
      await advanceTo(victim.characterId);
      await victimPage.locator('#player-actions button:enabled').filter({hasText: '结束我的回合'}).waitFor();
      const playerBefore = await readPlayer(victimPage);
      assert.deepEqual(playerBefore.view.candidates.map(candidate => candidate.label), ['结束我的回合']);
      assert.deepEqual(await openingBudget(victimPage), {actionsRemaining: 0, bonusActionsRemaining: 0,
        reactionAvailable: false, movementRemainingHalfFeet: 0, surprised: true});
      const privateView = JSON.stringify((await readPlayer(victimPage)).view);
      assert.ok(!/openingOperationId|initiativeGroups|stealthGroups|rollSource|OPENING_STEALTH|opposingScore/.test(privateView));
      await playerCommand(/^结束我的回合/, victimPage);
      state = await readHost();
      await waitForHistoryCount(host, state.campaign.domain.roots.length);
      assert.equal(battle().round, 1);
      assert.equal(state.campaign.domain.roots.at(-1).actor.memberId, members[seats.indexOf(victimPage)]);
      assert.equal(history().turnBudgets[victim.characterId].reactionAvailable, true);
      await ownCard(victimPage).locator('.turn-surprise').waitFor({state: 'detached'});
      await ownCard(victimPage).locator('.turn-budget').filter({hasText: '反应可用'}).waitFor();
      assert.equal((await openingBudget(victimPage)).surprised, false);
      const firstGroupActors = battle().initiative.filter(entry => wolves.slice(0, 2).includes(entry.characterId));
      await advanceTo(firstGroupActors[0].characterId);
      const other = firstGroupActors[1].characterId;
      if (history().turnBudgets[active()].surprised) {
        await endTurn();
        await advanceTo(firstGroupActors[0].characterId);
      }
      const otherBefore = actor(other);
      await kind.selectOption('DASH');
      state = await domainAction(combat);
      assert.equal(actor(active()).actionsRemaining, 0);
      assert.deepEqual(actor(other), otherBefore);
      await endTurn();
      await advanceTo(other);
      if (history().turnBudgets[other].surprised) {
        await endTurn();
        await advanceTo(other);
      }
      assert.equal(actor(other).actionsRemaining, 1);
      assert.equal(openingEvent().resolution.after.monsters.length, wolves.length);
      await assertMonsterHiddenFromObserver();
      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, independentInitiative: true,
        initiativeGroups: 2, surpriseAttempts: attempts, firstTurnPassed: true, reactionRecoveredInRound: 1}));
      return;
    }
    if (observation) {
      const combatForm = 'domain-combat';
      const terrainForm = 'domain-observation-cell';
      const targetOption = (form, id) => field(form, 'targetId').locator(`option[value="${id}"]`);
      async function verifyTotalCover(form, action, actorId, targetId, x, y) {
        await field(form, 'actorId').selectOption(actorId);
        await field(form, 'kind').selectOption(action);
        const target = targetOption(form, targetId);
        await target.waitFor({state: 'attached'});
        await field(form, 'targetId').selectOption(targetId);
        await setCell(x, y, {cover: 'TOTAL'});
        await target.waitFor({state: 'detached'});
        assert.notEqual(await field(form, 'targetId').inputValue(), targetId);
        await setCell(x, y, {});
        await target.waitFor({state: 'attached'});
      }
      async function expectGoblin(page, position, lastKnown) {
        const row = page.locator('#player-shared li').filter({hasText: monsterName});
        await row.filter({hasText: monsterName + ' · ' + (lastKnown ? `最后已知 ${position}（当前不可见）` : position)}).waitFor();
        assert.equal((await row.innerText()).includes('最后已知'), lastKnown);
        const {view} = await readPlayer(page);
        const sharedScene = view.shared.scenes.find(entry => entry.name === '林间空地');
        const creature = sharedScene.participants.find(person => person.name === monsterName);
        assert.equal(creature.position, position);
        assert.equal(Boolean(creature.lastKnown), lastKnown);
        if (lastKnown) assert.equal(creature.current, false);
      }

      await share('MONSTER', monster, 'ALL');
      await expectGoblin(attacker, '3, 1', false);
      await expectGoblin(observer, '3, 1', false);
      await advanceTo(fighters[0]);
      await verifyTotalCover(combatForm, 'ATTACK', fighters[0], monster, 2, 1);
      await advanceTo(monster);
      const monsterForm = 'domain-monster';
      await verifyTotalCover(monsterForm, 'MONSTER_ATTACK', monster, fighters[1], 1, 3);
      await setCell(3, 1, {hideAllowed: true});
      await setCell(2, 1, {blocksSight: true});
      assert.match(await field(terrainForm, 'cover').locator('option[value="HALF"]').innerText(), /半掩护.*\+2/);
      await setCell(1, 3, {cover: 'HALF'});
      await expectGoblin(attacker, '3, 1', true);
      await expectGoblin(observer, '3, 1', false);

      await field(combatForm, 'actorId').selectOption(monster);
      const kind = field(combatForm, 'kind');
      assert.match(await kind.locator('option[value="NIMBLE_HIDE"]').innerText(), /躲藏|隐藏/);
      assert.match(await kind.locator('option[value="NIMBLE_DISENGAGE"]').innerText(), /撤离/);
      await kind.selectOption('NIMBLE_HIDE');
      await kind.selectOption('NIMBLE_DISENGAGE');
      state = await domainAction(combatForm);
      assert.equal(actor(monster).disengaged, true);
      assert.equal(actor(monster).actionsRemaining, 1);
      assert.equal(actor(monster).bonusActionsRemaining, 0);

      await field(combatForm, 'actorId').selectOption(monster);
      await kind.selectOption('MOVE');
      await field(combatForm, 'path').fill('4,1');
      state = await domainAction(combatForm);
      assert.deepEqual(actor(monster).position, {x: 4, y: 1});
      await expectGoblin(observer, '4, 1', false);
      await expectGoblin(attacker, '3, 1', true);

      await field(monsterForm, 'actorId').selectOption(monster);
      await field(monsterForm, 'kind').selectOption('MONSTER_ATTACK');
      const shortbow = field(monsterForm, 'attackAction').locator('option').filter({hasText: /Shortbow/});
      await field(monsterForm, 'attackAction').selectOption(await shortbow.getAttribute('value'));
      await field(monsterForm, 'targetId').selectOption(fighters[1]);
      const defender = state.campaign.domain.characters.find(character => character.id === fighters[1]);
      const baseAC = 10 + Math.floor((defender.state.abilities['ability.dexterity'] - 10) / 2);
      state = await domainAction(monsterForm);
      const shot = battle().flows.at(-1).attacks[0];
      assert.equal(shot.actorId, monster);
      assert.equal(shot.targetId, fighters[1]);
      assert.equal(shot.targetCover, 'HALF');
      assert.equal(shot.finalArmorClass, baseAC + 2);
      assert.equal(actor(monster).actionsRemaining, 0);
      await host.locator('#domain-combat-summary .monster-attack-result')
        .filter({hasText: /Shortbow/}).filter({hasText: `护甲等级 ${shot.finalArmorClass}`})
        .filter({hasText: '半掩护（AC +2）'}).waitFor();
      await expectGoblin(attacker, '4, 1', true);

      await advanceTo(fighters[0]);
      await field(combatForm, 'actorId').selectOption(fighters[0]);
      await kind.selectOption('ATTACK');
      await targetOption(combatForm, monster).waitFor({state: 'attached'});
      await playerCommand(/^搜索（动作）/);
      state = await readHost();
      const search = battle().observation.checks.at(-1);
      assert.equal(search.kind, 'SEARCH');
      assert.equal(search.actorId, fighters[0]);
      assert.ok(search.roll.firstD20 >= 1 && search.roll.firstD20 <= 20);
      assert.equal(actor(fighters[0]).actionsRemaining, 0);
      assert.equal(state.campaign.domain.roots.at(-1).actor.memberId, members[0]);
      await expectGoblin(attacker, '4, 1', true);

      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length,
        rememberedPosition: '3, 1', seenPosition: '4, 1', revealedPosition: '4, 1',
        weaponDropdownsBlocked: 2, nimbleDisengage: true, cover: shot.targetCover, armorClass: shot.finalArmorClass, playerSearch: true}));
      return;
    }
    if (ranged) {
      const character = (domain, id) => domain.characters.find(c => c.id === id);
      const monsterHp = domain => domain.space.monsters.find(m => m.id === monster).hitPoints;
      async function assertLongRange(attack, evidence, label) {
        const {distanceFeet, normalRangeFeet, longRangeFeet, closeEnemy} = attack.rangedAttack;
        assert.deepEqual({distanceFeet, normalRangeFeet, longRangeFeet, closeEnemy},
          {distanceFeet: 85, normalRangeFeet: 80, longRangeFeet: 320, closeEnemy: false});
        assert.equal(attack.status, 'RESOLVED');
        assert.equal(attack.advantage, false);
        assert.equal(attack.disadvantage, true);
        for (const roll of [attack.firstD20, attack.secondD20]) assert.ok(Number.isInteger(roll) && roll >= 1 && roll <= 20);
        assert.equal(attack.selectedD20, Math.min(attack.firstD20, attack.secondD20));
        await evidence.filter({hasText: label})
          .filter({hasText: `d20=${attack.firstD20} / ${attack.secondD20}，选用 ${attack.selectedD20}`}).waitFor();
      }
      await advanceTo(monster);
      const observerHp = character(state.campaign.domain, fighters[1]).state.hitPoints;
      const form = 'domain-monster';
      await field(form, 'actorId').selectOption(monster);
      await field(form, 'kind').selectOption('MONSTER_ATTACK');
      const bowOption = field(form, 'attackAction').locator('option').filter({hasText: /Shortbow.*80\/320 尺/});
      await field(form, 'attackAction').selectOption(await bowOption.getAttribute('value'));
      await field(form, 'targetId').selectOption(fighters[1]);
      state = await domainAction(form);
      const monsterShot = battle().flows.at(-1).attacks[0];
      assert.equal(monsterShot.actorId, monster);
      assert.equal(monsterShot.monsterAttack.action.entryKey, 'shortbow');
      assert.equal(monsterShot.targetId, fighters[1]);
      assert.equal(monsterShot.rangedAttack.ammunitionItemId, null);
      assert.equal(actor(monster).actionsRemaining, 0);
      assert.equal(character(state.campaign.domain, fighters[1]).state.hitPoints, Math.max(0, observerHp - monsterShot.damageAmount));
      const combatSummary = host.locator('#domain-combat-summary');
      await assertLongRange(monsterShot, combatSummary.locator('.monster-attack-result'), /远程攻击 · 85 尺（长射程） · Shortbow/);
      await assertMonsterHiddenFromObserver();

      await advanceTo(fighters[0]);
      await playerCommand(/开始攻击动作/);
      const before = (await readHost()).campaign.domain;
      const beforeEquipment = character(before, fighters[0]).state.equipment;
      assert.equal(beforeEquipment.find(item => item.itemId === weapon).location, 'BOTH_HANDS');
      const arrows = beforeEquipment.filter(item => item.item.ruleKey === 'item.arrow' && item.location === 'CARRIED')
        .map(item => item.itemId).sort();
      assert.equal(arrows.length, 2);
      const receipt = await playerCommand(new RegExp(`^射击 ${monsterName}（18, 1） · Shortbow · 85 尺（长射程）`));
      assert.deepEqual(Object.keys(receipt.attackResult).sort(), ['damage', 'dropped', 'hit']);
      const outcome = receipt.attackResult;
      await attacker.locator('#player-receipt').filter({hasText: `上次攻击${outcome.hit ? '命中' : '未命中'}，造成 ${outcome.damage} 点伤害，${outcome.dropped ? '击倒了目标' : '未击倒目标'}`}).waitFor();
      state = await readHost();
      const after = state.campaign.domain;
      const shot = battle().flows.at(-1).attacks[0];
      assert.equal(shot.actorId, fighters[0]);
      assert.equal(shot.itemId, weapon);
      assert.equal(shot.targetId, monster);
      assert.equal(shot.finalArmorClass, 13);
      assert.equal(shot.rangedAttack.ammunitionItemId, arrows[0]);
      assert.equal(actor(fighters[0]).sequence.performed, 1);
      assert.deepEqual(character(after, fighters[0]).state.equipment, beforeEquipment.filter(item => item.itemId !== arrows[0]));
      const hp = Math.max(0, monsterHp(before) - shot.damageAmount);
      assert.deepEqual(outcome, {damage: shot.damageAmount, dropped: hp === 0, hit: shot.hit});
      assert.equal(monsterHp(after), hp);
      assert.equal(after.roots.length, before.roots.length + 1);
      assert.equal(after.roots.at(-1).actor.memberId, members[0]);
      await waitForHistoryCount(host, after.roots.length);
      const shotEvidence = combatSummary.locator('.character-shot-result')
        .filter({hasText: `护甲等级 ${shot.finalArmorClass} ·`});
      await assertLongRange(shot, shotEvidence, /射击 · 85 尺（长射程） · 已用 1 支箭/);
      await assertMonsterHiddenFromObserver();
      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      const rolls = attack => [attack.firstD20, attack.secondD20, attack.selectedD20];
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, distanceFeet: 85,
        monsterRolls: rolls(monsterShot), playerRolls: rolls(shot), armorClass: shot.finalArmorClass,
        ammunitionSpent: 1, attackResult: outcome}));
      return;
    }
    const wolf = monster;
    if (scenario === '--movement') {
      const move = () => battle().flows.at(-1).movements[0];
      const playerBudget = async () => (await readPlayer(attacker)).view.cards[0].turnBudget;
      while (active() !== fighters[0]) await endTurn();
      assert.equal((await playerBudget()).actionsRemaining, 1);
      await playerCommand(/开始攻击动作/);
      await playerCommand(new RegExp('攻击 ' + secondWolfName));
      assert.equal((await playerBudget()).bonusActionsRemaining, 1);
      await playerCommand(/回气（附赠动作）/);
      assert.deepEqual(await playerBudget(), {actionsRemaining: 0, bonusActionsRemaining: 0, reactionAvailable: true, movementRemainingHalfFeet: 60, surprised: false});
      await attacker.locator('#player-cards .turn-budget').filter({hasText: '普通动作 0 · 附赠动作 0'}).waitFor();
      state = await readHost();
      while (active() !== wolf) await endTurn();

      await field('domain-combat', 'kind').selectOption('MOVE');
      await field('domain-combat', 'path').fill('2,0\n3,0\n4,0');
      state = await domainAction('domain-combat');
      assert.equal(move().cursor, 1);
      assert.deepEqual(actor(wolf).position, {x: 2, y: 0});
      assert.equal(actor(wolf).movementSpentHalfFeet, 10);
      await attacker.getByText(wolfName + '（2, 0）将离开你的触及范围。', {exact: false}).waitFor();
      assert.equal((await readPlayer(observer)).view.pending.own, false);
      assert.ok(!(await readPlayer(observer)).view.candidates.some(c => c.label.includes('借机攻击')));
      await hostAction('save', () => host.locator('#storage-save').click());
      const reaction = await playerCommand(/^借机攻击/);
      assert.deepEqual(Object.keys(reaction.attackResult).sort(), ['damage', 'dropped', 'hit']);
      assert.equal((await playerBudget()).reactionAvailable, false);
      await hostAction('domain', () => host.getByRole('button', {name: '继续结算行动', exact: true}).click());
      state = await readHost();
      const steps = reaction.attackResult.dropped ? 1 : 3;
      assert.equal(move().cursor, steps);
      assert.equal(actor(wolf).movementSpentHalfFeet, steps * 10);
      assert.deepEqual(actor(wolf).position, {x: steps === 1 ? 2 : 4, y: 0});
      assert.equal(battle().flows.at(-1).status, 'COMPLETED');
      await hostAction('end', () => host.locator('#storage-end').click());
      assert.equal((await readHost()).storage.state, 'IDLE');
      assert.deepEqual(errors, []);
      console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, steps, reaction: reaction.attackResult}));
      return;
    }
    let outcome;
    for (let attempt = 0; attempt < 24 && !outcome?.hit; attempt++) {
      while (battle().initiative[battle().activeOrdinal].characterId !== fighters[0]) await endTurn();
      await playerCommand(/开始攻击动作/);
      const before = (await readHost()).campaign.domain;
      const beforeHp = before.space.monsters.find(m => m.id === wolf).hitPoints;
      const receipt = await playerCommand(new RegExp('攻击 ' + wolfName));
      outcome = receipt.attackResult;
      assert.deepEqual(Object.keys(outcome).sort(), ['damage', 'dropped', 'hit']);
      await attacker.locator('#player-receipt').filter({hasText: `上次攻击${outcome.hit ? '命中' : '未命中'}，造成 ${outcome.damage} 点伤害，${outcome.dropped ? '击倒了目标' : '未击倒目标'}`}).waitFor();
      state = await readHost();
      const after = state.campaign.domain;
      assert.equal(after.roots.length, before.roots.length + 1);
      assert.equal(after.roots.at(-1).actor.memberId, members[0]);
      assert.equal(after.space.monsters.find(m => m.id === wolf).hitPoints, Math.max(0, beforeHp - outcome.damage));
      await waitForHistoryCount(host, after.roots.length);
      if (!outcome.hit) await endTurn();
    }
    assert.ok(outcome.hit && outcome.damage > 0, 'Expected a server-rolled damaging hit');
    await share('MONSTER', wolf, 'NONE');
    await attacker.locator('#player-shared li').filter({hasText: wolfName}).waitFor({state: 'detached'});
    assert.ok(!(await readPlayer(attacker)).view.candidates.some(c => c.label.includes(wolfName)));
    await hostAction('end', () => host.locator('#storage-end').click());
    assert.equal((await readHost()).storage.state, 'IDLE');
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({result: 'PASSED', playerSessions: seats.length, attackResult: outcome}));
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  const redact = value => String(value).replace(/([#?&]invite=)[^\s"'&]+/g, '$1[redacted]');
  console.error(redact(error.stack || error.message));
  if (error.cause) console.error(redact(error.cause.stack || error.cause.message));
  process.exitCode = 1;
});
