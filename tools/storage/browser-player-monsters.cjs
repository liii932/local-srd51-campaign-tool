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
  assert.ok(scenario === undefined || scenario === '--movement');
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
    await field('storage-create', 'name').fill('玩家近战验收');
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
    await selectRule('domain-equipment', 'item', 'item.longsword');
    const acquired = await domainAction('domain-equipment');
    const fighter = acquired.campaign.domain.characters.find(c => c.id === fighters[0]);
    await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED');
    await field('domain-equipment', 'itemId').selectOption(fighter.state.equipment.find(i => i.item.ruleKey === 'item.longsword').itemId);
    await field('domain-equipment', 'location').selectOption('MAIN_HAND');
    await domainAction('domain-equipment');

    const world = await createNode(host, hostAction, '林地');
    await focusSpace(host, hostAction, 'WORLD', world);
    await placeAtFocus(host, hostAction, fighters);
    await field('domain-monster-create', 'name').fill(wolfName);
    await selectRule('domain-monster-create', 'monster', 'monster.wolf');
    const created = await domainAction('domain-monster-create');
    const wolf = created.campaign.domain.space.monsters.find(m => m.name === wolfName).id;
    const placements = [[fighters[0], 1, 1, 0], [wolf, 2, 1, 1], [fighters[1], 3, 3, 0]];
    const secondWolfName = '营地灰狼';
    let secondWolf;
    if (scenario === '--movement') {
      await field('domain-monster-create', 'name').fill(secondWolfName);
      await selectRule('domain-monster-create', 'monster', 'monster.wolf');
      const result = await domainAction('domain-monster-create');
      secondWolf = result.campaign.domain.space.monsters.find(m => m.name === secondWolfName).id;
      placements.push([secondWolf, 1, 2, 1]);
    }
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
    await share('MONSTER', wolf, 'SELECTED', members[0]);
    if (secondWolf) await share('MONSTER', secondWolf, 'SELECTED', members[0]);
    await attacker.locator('#player-shared li').filter({hasText: wolfName}).waitFor();
    await observer.locator('#player-shared').getByRole('heading', {name: '林间空地'}).waitFor();
    assert.ok(!(await observer.locator('#player-shared').innerText()).includes(wolfName));
    assert.ok(!JSON.stringify((await readPlayer(observer)).view).includes(wolf));

    await host.locator('[data-pane="combat"]').click();
    await field('domain-combat', 'kind').selectOption('REENTER_COMBAT');
    let state = await domainAction('domain-combat');
    const battle = () => state.campaign.domain.combats.find(c => c.current.combatId === scene).current;
    const endTurn = async () => {
      await field('domain-combat', 'kind').selectOption('END_TURN');
      await field('domain-combat', 'actorId').selectOption(battle().initiative[battle().activeOrdinal].characterId);
      state = await domainAction('domain-combat');
    };
    if (scenario === '--movement') {
      const active = () => battle().initiative[battle().activeOrdinal].characterId;
      const move = () => battle().flows.at(-1).movements[0];
      const actor = id => battle().combatants.find(row => row.characterId === id);
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
