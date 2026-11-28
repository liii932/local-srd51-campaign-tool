'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady, waitForHistoryCount} = require('./browser-action.cjs');
const {createNode, focusSpace, placeAtFocus, createScene, enterScene} = require('./browser-spaces.cjs');

const hostOrigin = 'http://127.0.0.1:8080';
const playerOrigin = 'https://127.0.0.1:8443';
const wolfName = '林地灰狼';

async function main() {
  const [flag, storageRoot, scenario] = process.argv.slice(2);
  assert.ok(flag === '--fresh-root' && path.isAbsolute(storageRoot), 'Expected --fresh-root /absolute/disposable/player-store');
  assert.ok(scenario === undefined || ['--movement', '--ranged', '--observation'].includes(scenario),
    'Expected --movement, --ranged, or --observation');
  const ranged = scenario === '--ranged';
  const observation = scenario === '--observation';
  const monsterName = observation ? '林间哥布林' : ranged ? '林间骷髅' : wolfName;
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
    const hostAction = async (action, trigger) => (await observeStorageAction(host, action, trigger, {})).body;
    const domainAction = form => hostAction('domain', () => submit(form));
    const readHost = () => host.evaluate(async () => (await fetch('/api/host/storage/state')).json());
    const readPlayer = page => page.evaluate(async () => (await fetch('/api/player/state')).json());
    async function governance(action, trigger) {
      const [reply] = await Promise.all([
        host.waitForResponse(r => r.url() === hostOrigin + '/api/host/storage/players/' + action && r.request().method() === 'POST'),
        trigger()
      ]);
      assert.equal(reply.status(), 200, (await reply.json()).code);
      await host.locator('#host-players[aria-busy="false"]').waitFor();
    }
    async function selectRule(form, name, ruleKey) {
      const select = field(form, name);
      const value = await select.locator('option').evaluateAll((rows, key) => rows.find(r => r.value.endsWith('/' + key))?.value, ruleKey);
      assert.ok(value, ruleKey);
      await select.selectOption(value);
    }
    async function share(kind, id, audience, memberId) {
      const form = 'host-player-share-form';
      await field(form, 'kind').selectOption(kind);
      await field(form, 'subjectId').selectOption(id);
      await field(form, 'audience').selectOption(audience);
      if (memberId) await host.locator('#host-player-share-members input[value="' + memberId + '"]').check();
      await governance('share', () => submit(form));
    }
    async function playerCommand(label) {
      const [response] = await Promise.all([
        attacker.waitForResponse(r => r.url() === playerOrigin + '/api/player/command'),
        attacker.getByRole('button', {name: label}).click()
      ]);
      assert.equal(response.status(), 200);
      return (await response.json()).receipt;
    }

    await host.goto(hostOrigin + '/host/storage');
    await waitForStorageReady(host);
    assert.equal((await readHost()).storage.state, 'IDLE');
    await field('storage-create', 'name').fill(observation ? '玩家观测与掩护验收' : ranged ? '玩家远程验收' : '玩家近战验收');
    await hostAction('create', () => host.locator('#storage-create button[type="submit"]').click());
    const fighters = [];
    for (const name of ['巡林战士', '营地战士']) {
      await host.locator('[data-pane="people"]').click();
      await field('domain-create', 'name').fill(name);
      await selectRule('domain-create', 'classKey', 'class.fighter');
      const result = await domainAction('domain-create');
      fighters.push(result.campaign.domain.characters.find(c => c.name === name).id);
    }
    await host.locator('[data-pane="actions"]').click();
    await field('domain-equipment', 'characterId').selectOption(fighters[0]);
    await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
    const weaponKey = ranged ? 'item.shortbow' : 'item.longsword';
    await selectRule('domain-equipment', 'item', weaponKey);
    const acquired = await domainAction('domain-equipment');
    const fighter = acquired.campaign.domain.characters.find(c => c.id === fighters[0]);
    const weapon = fighter.state.equipment.find(i => i.item.ruleKey === weaponKey).itemId;
    await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED');
    await field('domain-equipment', 'itemId').selectOption(weapon);
    await field('domain-equipment', 'location').selectOption(ranged ? 'BOTH_HANDS' : 'MAIN_HAND');
    await domainAction('domain-equipment');
    if (ranged) for (let i = 0; i < 2; i++) {
      await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
      await selectRule('domain-equipment', 'item', 'item.arrow');
      await domainAction('domain-equipment');
    }

    const world = await createNode(host, hostAction, '林地');
    await focusSpace(host, hostAction, 'WORLD', world);
    await placeAtFocus(host, hostAction, fighters);
    await field('domain-monster-create', 'name').fill(monsterName);
    await selectRule('domain-monster-create', 'monster', observation ? 'monster.goblin' : ranged ? 'monster.skeleton' : 'monster.wolf');
    const created = await domainAction('domain-monster-create');
    const monster = created.campaign.domain.space.monsters.find(m => m.name === monsterName).id;
    const placements = observation ? [[fighters[0], 1, 1, 0], [monster, 3, 1, 1], [fighters[1], 1, 3, 0]]
      : ranged ? [[fighters[0], 1, 1, 0], [monster, 18, 1, 1], [fighters[1], 1, 2, 0]]
        : [[fighters[0], 1, 1, 0], [monster, 2, 1, 1], [fighters[1], 3, 3, 0]];
    const secondWolfName = '营地灰狼';
    let secondWolf;
    if (scenario === '--movement') {
      await field('domain-monster-create', 'name').fill(secondWolfName);
      await selectRule('domain-monster-create', 'monster', 'monster.wolf');
      const result = await domainAction('domain-monster-create');
      secondWolf = result.campaign.domain.space.monsters.find(m => m.name === secondWolfName).id;
      placements.push([secondWolf, 1, 2, 1]);
    }
    if (ranged) await field('domain-scene-create', 'width').fill('20');
    const scene = await createScene(host, hostAction, '林间空地', 'RULES');
    await enterScene(host, hostAction, scene, placements);

    await governance('invite', () => host.locator('#host-players-invite').click());
    const invitation = await host.locator('#host-player-link').getAttribute('href');
    const seats = [attacker, observer], members = [];
    for (const [index, page] of seats.entries()) {
      await page.goto(invitation);
      await page.locator('#player-nickname').fill(['巡林玩家', '营地玩家'][index]);
      await page.locator('#player-join-form button').click();
      await page.locator('#player-waiting').waitFor();
    }
    await host.getByRole('button', {name: '刷新席位', exact: true}).click();
    await host.locator('#host-player-applications .player-application').nth(1).waitFor();
    for (let i = 0; i < seats.length; i++) {
      await governance('approve', () => host.getByRole('button', {name: '核对并批准', exact: true}).first().click());
    }
    await host.getByText('角色控制与探索许可', {exact: true}).click();
    for (const [i, page] of seats.entries()) {
      await page.locator('#player-table').waitFor();
      members.push((await readPlayer(page)).session.playerKey);
      await field('host-player-control-form', 'memberId').selectOption(members[i]);
      await field('host-player-control-form', 'characterId').selectOption(fighters[i]);
      await governance('control', () => submit('host-player-control-form'));
    }
    assert.notEqual(members[0], members[1]);
    await attacker.locator('#player-takeover').click();
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
    if (observation) {
      const combatForm = 'domain-combat';
      const terrainForm = 'domain-observation-cell';
      const setCell = async (x, y, {cover = 'NONE', hideAllowed = false, blocksSight = false}) => {
        await field(terrainForm, 'combatId').selectOption(scene);
        await field(terrainForm, 'x').fill(String(x));
        await field(terrainForm, 'y').fill(String(y));
        await field(terrainForm, 'y').press('Tab');
        await field(terrainForm, 'hideAllowed').setChecked(hideAllowed);
        await field(terrainForm, 'blocksSight').setChecked(blocksSight);
        await field(terrainForm, 'cover').selectOption(cover);
        state = await domainAction(terrainForm);
        const cell = battle().observation.cells.find(cell => cell.position.x === x && cell.position.y === y);
        if (hideAllowed || blocksSight || cover !== 'NONE') assert.deepEqual(cell, {position: {x, y}, hideAllowed, blocksSight, cover});
        else assert.equal(cell, undefined);
      };
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
      assert.deepEqual(await playerBudget(), {actionsRemaining: 0, bonusActionsRemaining: 0, reactionAvailable: true, movementRemainingHalfFeet: 60});
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

main().catch(error => { console.error(error.message); process.exitCode = 1; });
