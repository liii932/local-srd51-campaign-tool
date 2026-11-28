#!/usr/bin/env node
'use strict';

// Run against a fresh disposable run-local.sh instance on the protected loopback entry.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady, readHistory} = require('./browser-action.cjs');
const {selectStandardRules} = require('./browser-rule-library.cjs');
const {createNode, focusSpace, placeAtFocus, beginBattle} = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '擒抱推撞浏览器验收';
const fighterName = '进阶战士';
const guardName = '练习守卫';
const watcherName = '持刃守卫';

async function main() {
  const [flag, storageRoot] = process.argv.slice(2);
  assert.ok(process.argv.length === 4 && flag === '--fresh-root' && path.isAbsolute(storageRoot),
    'Usage: node tools/storage/browser-grapple-shove.cjs --fresh-root /absolute/private/test-root');
  const root = await fs.realpath(storageRoot);
  const repository = await fs.realpath(path.join(__dirname, '../..'));
  assert.ok(root !== repository && !root.startsWith(repository + path.sep));
  assert.equal((await fs.lstat(storageRoot)).isSymbolicLink(), false);
  assert.deepEqual((await fs.readdir(root)).sort(), ['instance.lock', 'runs', 'saves']);
  for (const name of ['runs', 'saves']) {
    const directory = path.join(root, name);
    assert.ok((await fs.lstat(directory)).isDirectory());
    assert.deepEqual(await fs.readdir(directory), []);
  }

  const browser = await chromium.launch({headless: true});
  const page = await browser.newPage({locale: 'zh-CN', viewport: {width: 1440, height: 1050}});
  page.setDefaultTimeout(30000);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', event => {
    if (/Content Security Policy|violates.*directive/i.test(event.text())) pageErrors.push(event.text());
  });
  const form = id => page.locator('#' + id);
  const button = id => form(id).locator('button[type="submit"], button:not([type])');
  const field = (id, name) => form(id).locator(`[name="${name}"]`);
  const combatField = name => field('domain-combat', name);
  const summary = form('domain-combat-summary');
  const pending = form('domain-pending');
  const releaseButton = summary.getByRole('button', {name: '释放擒抱（无需动作）', exact: true});
  const skills = {ATHLETICS: '力量（运动）', ACROBATICS: '敏捷（杂技）'};
  let current;
  let owned = false;
  const domain = () => current.campaign.domain;
  const card = name => domain().characters.find(character => character.name === name);
  const cardName = id => domain().characters.find(character => character.id === id).name;
  const history = () => domain().combats[0];
  const battle = () => history().current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const actor = id => battle().combatants.find(value => value.characterId === id);
  const contest = () => battle().meleeControl.contests.at(-1);
  const grapple = () => battle().meleeControl.grapples.find(relation => !relation.endReason);
  const movement = () => battle().flows.flatMap(flow => flow.movements).findLast(move => move.drag);
  const lastEvent = () => history().events.at(-1);

  async function action(name, trigger) {
    const observed = await observeStorageAction(page, name, trigger, {});
    current = observed.body;
    return current;
  }
  async function selectRule(id, name, key) {
    const select = field(id, name);
    const value = (await select.locator('option').evaluateAll(options => options.map(option => option.value)))
      .find(option => option.endsWith('/' + key));
    assert.ok(value, `Missing selectable rule ${key}`);
    await select.selectOption(value);
  }
  async function combat(kind, values = {}) {
    await combatField('kind').selectOption(kind);
    for (const [name, value] of Object.entries(values)) {
      const control = combatField(name);
      if (name === 'path' || name === 'dragPath') await control.fill(value);
      else await control.selectOption(value);
    }
    await action('domain', () => button('domain-combat').click());
  }
  async function saveEndReopen() {
    const before = domain();
    await action('save', () => form('storage-save').click());
    const closed = await action('end', () => form('storage-end').click());
    owned = false;
    await form('storage-files').selectOption(closed.savedFile);
    await action('open', () => form('storage-open').locator('button[type="submit"]').click());
    owned = true;
    assert.deepEqual(domain(), before);
    await page.locator('[data-pane="combat"]').click();
  }
  async function turnFor(id) {
    for (let turn = 0; active() !== id && turn < battle().initiative.length; turn++) await combat('END_TURN');
    assert.equal(active(), id);
  }
  async function declare(kind, initiator, target, hand) {
    const previous = actor(initiator).sequence;
    const weaponAttacksBefore = battle().flows.flatMap(flow => flow.attacks).length;
    await combat(kind, {targetId: target, ...(hand ? {hand} : {})});
    assert.equal(contest().kind, kind);
    assert.equal(contest().actorId, initiator);
    assert.equal(contest().targetId, target);
    assert.equal(contest().sequenceKey, previous.sequenceKey);
    assert.equal(contest().attackOrdinal, previous.performed);
    assert.equal(contest().outcome, null);
    assert.equal(contest().initiator, null);
    assert.equal(contest().responder, null);
    assert.equal(lastEvent().resolution.rolls.length, 0);
    assert.equal(actor(initiator).sequence.performed, previous.performed);
    assert.equal(battle().flows.flatMap(flow => flow.attacks).length, weaponAttacksBefore);
  }
  function assertContest(skill) {
    const result = contest();
    assert.equal(result.selectedSkill, skill);
    for (const side of [result.initiator, result.responder]) {
      assert.ok(side.firstD20 >= 1 && side.firstD20 <= 20);
      assert.equal(side.total, side.selectedD20 + side.abilityModifier + side.proficiencyBonus);
    }
    const margin = result.initiator.total - result.responder.total;
    const threshold = result.minimumWinningMargin;
    assert.equal(result.outcome, margin >= threshold ? 'INITIATOR_WINS' : margin <= -threshold ? 'RESPONDER_WINS' : 'UNCHANGED');
    assert.deepEqual(lastEvent().resolution.rolls.map(roll => roll.value),
      [result.initiator.firstD20, result.initiator.secondD20, result.responder.firstD20, result.responder.secondD20].filter(die => die != null));
    return result;
  }
  async function defend(skill) {
    const before = contest(), attacks = battle().flows.flatMap(flow => flow.attacks).length;
    await pending.getByText(`${cardName(before.targetId)} 选择防御检定`, {exact: false}).waitFor();
    await action('domain', () => pending.getByRole('button', {name: skills[skill], exact: true}).click());
    const result = assertContest(skill);
    assert.equal(result.contestKey, before.contestKey);
    assert.equal(actor(result.actorId).sequence.performed, before.attackOrdinal + 1);
    assert.equal(actor(result.actorId).actionsRemaining, 0);
    assert.equal(battle().flows.flatMap(flow => flow.attacks).length, attacks);
    await summary.locator('.melee-contest-result').filter({hasText: `已用第 ${before.attackOrdinal + 1} 次攻击`}).waitFor();
    return result;
  }
  async function establishAttackPair(fighter, guard) {
    const trials = [];
    for (let attempt = 0; attempt < 10; attempt++) {
      await turnFor(fighter);
      const turnNumber = battle().turnNumber;
      assert.equal(actor(fighter).actionsRemaining, 1);
      assert.equal(actor(guard).prone, false);
      assert.equal(grapple(), undefined);
      await combat('BEGIN_ATTACK');
      const sequence = actor(fighter).sequence;
      assert.equal(sequence.limit, 2);
      await declare('GRAPPLE', fighter, guard, 'OFF_HAND');
      assert.equal(contest().attackOrdinal, 0);
      await summary.getByText('已攻击 0/2 · 剩余 2', {exact: true}).waitFor();
      await assertViewportFits();
      await saveEndReopen();
      await page.setViewportSize({width: 390, height: 844});
      await assertViewportFits();
      assert.equal(battle().turnNumber, turnNumber);
      assert.deepEqual(actor(fighter).sequence, sequence);
      const first = await defend('ACROBATICS');
      await summary.getByText('已攻击 1/2 · 剩余 1', {exact: true}).waitFor();
      if (first.outcome !== 'INITIATOR_WINS') {
        trials.push([first.outcome]);
        assert.equal(grapple(), undefined);
      } else {
        const relation = grapple();
        assert.equal(relation?.originatingContest, first.contestKey);
        await declare('SHOVE_PRONE', fighter, guard);
        assert.equal(contest().attackOrdinal, 1);
        const shove = await defend('ATHLETICS');
        trials.push([first.outcome, shove.outcome]);
        assert.equal(actor(guard).prone, shove.outcome === 'INITIATOR_WINS');
        if (shove.outcome === 'INITIATOR_WINS') return {turnNumber, sequence, first, shove, relation, trials};
        await action('domain', () => releaseButton.click());
        assert.equal(grapple(), undefined);
        assert.equal(lastEvent().resolution.rolls.length, 0);
      }
      await combat('END_TURN');
    }
    assert.fail(`No first-ordinal grapple and second-ordinal shove prone both succeeded in 10 turns: ${JSON.stringify(trials)}`);
  }
  function assertAttackPair(pair, fighter, guard) {
    assert.equal(active(), fighter);
    assert.equal(battle().turnNumber, pair.turnNumber);
    assert.deepEqual(actor(fighter).sequence, {...pair.sequence, performed: 2, closed: true});
    assert.equal(actor(fighter).actionsRemaining, 0);
    assert.equal(actor(guard).prone, true);
    assert.deepEqual(grapple(), pair.relation);
    const contests = battle().meleeControl.contests.filter(entry => entry.sequenceKey === pair.sequence.sequenceKey);
    assert.deepEqual(contests, [pair.first, pair.shove]);
    assert.deepEqual(contests.map(entry => [entry.turnNumber, entry.attackOrdinal]), [[pair.turnNumber, 0], [pair.turnNumber, 1]]);
  }
  async function establish(fighter, guard) {
    for (let attempt = 0; attempt < 16 && !grapple(); attempt++) {
      if (!actor(fighter).sequence || actor(fighter).sequence.closed) {
        if (active() === fighter && actor(fighter).actionsRemaining === 0) await combat('END_TURN');
        await turnFor(fighter);
        await combat('BEGIN_ATTACK');
      }
      await declare('GRAPPLE', fighter, guard, 'OFF_HAND');
      await defend('ACROBATICS');
    }
    assert.ok(grapple(), 'No grapple succeeded within the bounded number of real dice attempts');
    return grapple();
  }
  async function assertViewportFits() {
    assert.ok(await page.evaluate(() => {
      const center = document.getElementById('workspace-center-scroll');
      return document.documentElement.scrollWidth <= window.innerWidth && center.scrollWidth <= center.clientWidth;
    }), 'The workbench must fit the viewport without horizontal page scrolling');
  }

  try {
    await page.goto(origin + '/host/storage');
    await form('storage-message').filter({hasText: /^创建、打开或恢复一个战役。$/}).waitFor();
    const initial = await page.request.get(origin + '/api/host/storage/state');
    assert.equal(initial.status(), 200);
    const idle = await initial.json();
    assert.equal(idle.storage.state, 'IDLE');
    assert.equal(idle.campaign, null);
    assert.deepEqual(idle.savedCampaigns, []);
    assert.deepEqual(idle.storage.residualRunIds, []);

    await selectStandardRules(page);
    await field('storage-create', 'name').fill(campaignName);
    await action('create', () => button('storage-create').click());
    owned = true;
    assert.equal((await fs.readdir(path.join(root, 'runs'))).length, 1, 'The launcher must be using the selected fixture');
    for (const [name, kind] of [[fighterName, 'PC'], [guardName, 'NPC'], [watcherName, 'NPC']]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption(kind);
      await selectRule('domain-create', 'classKey', 'class.fighter');
      await action('domain', () => button('domain-create').click());
    }
    const fighter = card(fighterName).id, guard = card(guardName).id, watcher = card(watcherName).id;
    await page.locator('[data-pane="growth"]').click();
    for (const level of [2, 3, 4, 5]) {
      await field('domain-growth', 'characterId').selectOption(fighter);
      await selectRule('domain-growth', 'class', 'class.fighter');
      if (level === 3) await selectRule('domain-growth', 'subclass', 'subclass.champion');
      else await field('domain-growth', 'subclass').selectOption('');
      await field('domain-growth', 'ability').selectOption(level === 4 ? 'ability.strength' : '');
      await action('domain', () => button('domain-growth').click());
      assert.equal(card(fighterName).level, level);
    }
    await page.locator('[data-pane="actions"]').click();
    await field('domain-equipment', 'characterId').selectOption(watcher);
    await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
    await selectRule('domain-equipment', 'item', 'item.dagger');
    await action('domain', () => button('domain-equipment').click());
    const weapon = card(watcherName).state.equipment.find(item => item.item.ruleKey === 'item.dagger').itemId;
    await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED');
    await field('domain-equipment', 'itemId').selectOption(weapon);
    await field('domain-equipment', 'location').selectOption('MAIN_HAND');
    await action('domain', () => button('domain-equipment').click());

    const node = await createNode(page, action, '擒抱训练场');
    await focusSpace(page, action, 'WORLD', node);
    await placeAtFocus(page, action, [fighter, guard, watcher]);
    await beginBattle(page, action, [[fighter, 2, 2, 0], [guard, 3, 2, 1], [watcher, 2, 1, 1]]);
    const pair = await establishAttackPair(fighter, guard);
    const {relation} = pair;
    assertAttackPair(pair, fighter, guard);
    await summary.getByText('受擒（速度 0）', {exact: false}).waitFor();
    await assertViewportFits();

    const beforeDrag = actor(fighter).movementSpentHalfFeet;
    const carriedBudget = actor(guard).movementSpentHalfFeet;
    await combat('DRAG_GRAPPLE', {grappleKey: relation.grappleKey, path: '1,2\n1,3'});
    assert.equal(movement().cursor, 1);
    assert.equal(movement().drag.allowanceHalfFeet, 30);
    assert.deepEqual(actor(fighter).position, {x: 1, y: 2});
    assert.deepEqual(actor(guard).position, {x: 2, y: 2});
    const react = () => pending.getByRole('button', {name: '放弃反应', exact: true});
    const remaining = () => summary.locator('.drag-result').filter({hasText: '已走 1/2 格 · 已用移动力 5 尺 · 剩余移动力 10 尺'});
    await react().waitFor();
    await remaining().waitFor();
    await saveEndReopen();
    assertAttackPair(pair, fighter, guard);
    await remaining().waitFor();
    await action('domain', () => react().click());
    await action('domain', () => pending.getByRole('button', {name: '继续结算行动', exact: true}).click());
    assert.deepEqual(actor(fighter).position, {x: 1, y: 3});
    assert.deepEqual(actor(guard).position, {x: 1, y: 2});
    assert.equal(actor(fighter).movementSpentHalfFeet, beforeDrag + 20);
    assert.equal(actor(guard).movementSpentHalfFeet, carriedBudget);
    assert.equal(movement().status, 'COMPLETED');
    assert.equal(movement().drag.targetId, guard);
    assert.deepEqual(movement().drag.path, [{x: 2, y: 2}, {x: 1, y: 2}]);
    assertAttackPair(pair, fighter, guard);
    await summary.getByText('已走 2/2 格 · 已用移动力 10 尺', {exact: false}).waitFor();
    await summary.getByText('擒抱中 · 副手', {exact: false}).waitFor();
    const reopenedHistory = await readHistory(page);
    const dragEntries = reopenedHistory.split('\n').filter(line => line.includes('拖带移动'));
    assert.ok(dragEntries.some(entry => entry.includes('进阶战士 位置 (2,2) → (1,2)') && entry.includes('练习守卫 位置 (3,2) → (2,2)')));
    assert.ok(dragEntries.some(entry => entry.includes('进阶战士 位置 (1,2) → (1,3)') && entry.includes('练习守卫 位置 (2,2) → (1,2)')));
    assert.match(reopenedHistory, /力量（运动） d20=\d+.*敏捷（杂技） d20=\d+/);
    assert.match(reopenedHistory, /分差（发起方减应对方） -?\d+ 点/);

    await turnFor(guard);
    await combat('ESCAPE_GRAPPLE', {grappleKey: relation.grappleKey, escapeSkill: 'ACROBATICS'});
    assert.equal(contest().kind, 'ESCAPE_GRAPPLE');
    assert.equal(contest().grappleKey, relation.grappleKey);
    assert.equal(actor(guard).actionsRemaining, 0);
    const escape = assertContest('ACROBATICS');
    assert.equal(battle().meleeControl.grapples.find(value => value.grappleKey === relation.grappleKey).endReason,
      escape.outcome === 'INITIATOR_WINS' ? 'ESCAPED' : null);

    if (!grapple()) {
      await turnFor(fighter);
      await establish(fighter, guard);
    }
    const held = grapple();
    if (active() === guard) await combat('END_TURN');
    await turnFor(guard);
    await combat('BEGIN_ATTACK');
    await declare('SHOVE_PRONE', guard, fighter);
    const choice = contest();
    const budgets = battle().combatants;
    await action('domain', () => releaseButton.click());
    assert.equal(battle().meleeControl.grapples.find(value => value.grappleKey === held.grappleKey).endReason, 'RELEASED');
    assert.deepEqual(contest(), choice);
    assert.deepEqual(battle().combatants, budgets);
    assert.equal(lastEvent().resolution.rolls.length, 0);
    const prone = await defend('ATHLETICS');
    assert.equal(actor(fighter).prone, prone.outcome === 'INITIATOR_WINS');
    await turnFor(fighter);
    await combat('BEGIN_ATTACK');
    await declare('SHOVE_PUSH', fighter, guard);
    assert.deepEqual(contest().destination, {x: 1, y: 1});
    const push = await defend('ACROBATICS');
    assert.deepEqual(actor(guard).position, push.outcome === 'INITIATOR_WINS' ? {x: 1, y: 1} : {x: 1, y: 2});
    assert.equal(actor(guard).movementSpentHalfFeet, carriedBudget);
    const log = await readHistory(page);
    for (const entry of ['挣脱擒抱', '擒抱结束（主动释放）', '推倒', '推开 5 尺']) assert.ok(log.includes(entry), entry);
    await page.setViewportSize({width: 1440, height: 1050});
    await assertViewportFits();
    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), attackPairTrials: pair.trials,
      attacks: [pair.first, pair.shove].map(result => ({kind: result.kind, ordinal: result.attackOrdinal, outcome: result.outcome})),
      sameTurnDrag: true, escape: escape.outcome, prone: prone.outcome, push: push.outcome, pairedDragFeet: 10,
      reopenedChoice: true, reopenedPausedDrag: true, releaseDuringChoice: true, viewportWidths: [1440, 390]}));
  } finally {
    if (owned && (await form('storage-campaign-name').textContent().catch(() => '')).trim() === campaignName) {
      await action('end', () => form('storage-end').click()).catch(error => console.error(`Test fixture cleanup: ${error.message}`));
    }
    await browser.close();
  }
}

main().catch(error => {
  console.error(error.stack);
  process.exitCode = 1;
});
