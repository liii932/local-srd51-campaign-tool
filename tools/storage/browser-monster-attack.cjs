#!/usr/bin/env node
'use strict';

// Run against a fresh disposable run-local.sh instance on the protected loopback entry.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, readHistory} = require('./browser-action.cjs');
const {selectStandardRules} = require('./browser-rule-library.cjs');
const {createNode, focusSpace, placeAtFocus, beginBattle} = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '地狱犬攻击浏览器验收';
const moverName = '抓者';
const carriedName = '受擒队友';
const houndName = '地狱犬';
const route = '2,3\n3,4\n4,5';

async function main() {
  const [flag, storageRoot] = process.argv.slice(2);
  assert.ok(process.argv.length === 4 && flag === '--fresh-root' && path.isAbsolute(storageRoot),
    'Usage: node tools/storage/browser-monster-attack.cjs --fresh-root /absolute/private/test-root');
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
  const summary = form('domain-combat-summary');
  const pending = form('domain-pending');
  const biteChoice = pending.getByRole('button', {name: '借机攻击 · Bite', exact: true});
  const passChoice = pending.getByRole('button', {name: '放弃反应', exact: true});
  const next = pending.getByRole('button', {name: '继续结算行动', exact: true});
  let current;
  let owned = false;
  const domain = () => current.campaign.domain;
  const card = name => domain().characters.find(character => character.name === name);
  const history = () => domain().combats[0];
  const battle = () => history().current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const actor = id => battle().combatants.find(value => value.characterId === id);
  const position = id => domain().positions.find(value => value.characterId === id);
  const grapple = () => battle().meleeControl.grapples.find(relation => !relation.endReason);
  const flow = () => battle().flows.find(candidate => candidate.status === 'ACTIVE');
  const movement = () => battle().flows.flatMap(flow => flow.movements).findLast(move => move.drag);
  const attack = () => battle().flows.flatMap(flow => flow.attacks).findLast(value => value.monsterAttack);
  const dice = () => history().events.at(-1).resolution.rolls.map(roll => roll.value);

  async function action(name, trigger) {
    current = (await observeStorageAction(page, name, trigger, {})).body;
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
    await field('domain-combat', 'kind').selectOption(kind);
    for (const [name, value] of Object.entries(values)) {
      const control = field('domain-combat', name);
      if (name === 'path' || name === 'dragPath') await control.fill(value);
      else await control.selectOption(value);
    }
    await action('domain', () => button('domain-combat').click());
  }
  async function turnFor(id, fresh = false) {
    if (fresh && active() === id) await combat('END_TURN');
    for (let steps = 0; active() !== id && steps < battle().initiative.length; steps++) await combat('END_TURN');
    assert.equal(active(), id);
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
  async function fitViewport() {
    assert.ok(await page.evaluate(() => {
      const main = document.getElementById('workspace-center-scroll');
      return document.documentElement.scrollWidth <= window.innerWidth && main.scrollWidth <= main.clientWidth;
    }));
  }
  function assertPrefix(mover, carried, hound, grappleKey) {
    const move = movement(), window = flow().windows.at(-1);
    assert.equal(move.cursor, 1);
    assert.equal(move.spentHalfFeet, 10);
    assert.equal(move.drag.grappleKey, grappleKey);
    assert.equal(move.drag.allowanceHalfFeet, 30);
    assert.deepEqual(position(mover).grid, {x: 2, y: 3});
    assert.deepEqual(position(carried).grid, {x: 2, y: 2});
    assert.equal(window.trigger.subjectId, mover);
    assert.deepEqual(window.opportunities.map(option => option.actorId), [hound]);
    assert.equal(window.opportunities[0].options[0].monsterAttack.entryKey, 'bite');
    assert.equal(window.opportunities[0].options[0].itemId, null);
    assert.equal(actor(hound).reactionAvailable, true);
  }
  async function assertBite(hound, target, opportunity) {
    const result = attack();
    assert.equal(result.actorId, hound);
    assert.equal(result.targetId, target);
    assert.equal(result.opportunityAttack, opportunity);
    assert.equal(result.itemId, null);
    assert.equal(result.sequenceKey, null);
    assert.equal(result.status, 'RESOLVED');
    assert.equal(result.monsterAttack.action.entryKey, 'bite');
    assert.equal(result.monsterAttack.hitBonus, 5);
    assert.equal(result.monsterAttack.reachFeet, 5);
    assert.ok(result.firstD20 >= 1 && result.firstD20 <= 20);
    assert.equal(result.secondD20, null);
    const components = result.monsterAttack.damageComponents;
    assert.deepEqual(dice(), [result.firstD20, ...components.flatMap(component => component.dice)]);
    const line = summary.locator('.monster-attack-result').filter({hasText: `d20=${result.firstD20}`});
    await line.waitFor();
    if (result.hit) {
      assert.deepEqual(components.map(component => [component.damageType, component.diceSides, component.modifier]),
        [['PIERCING', 8, 3], ['FIRE', 6, 0]]);
      assert.deepEqual(components.map(component => component.dice.length), result.critical ? [2, 4] : [1, 2]);
      for (const {dice, modifier, rawDamage, effectiveDamage} of components) {
        assert.equal(rawDamage, dice.reduce((total, die) => total + die, modifier));
        assert.equal(effectiveDamage, rawDamage);
      }
      assert.equal(result.damageAmount, components.reduce((total, component) => total + component.effectiveDamage, 0));
      const shown = await line.textContent();
      assert.ok(shown.includes(`穿刺 d8=${components[0].dice.join(' / ')}`), shown);
      assert.ok(shown.includes(`火焰 d6=${components[1].dice.join(' / ')}`), shown);
      assert.ok(shown.includes(`有效伤害合计 ${result.damageAmount}`), shown);
    } else {
      assert.equal(result.damageAmount, 0);
      await line.filter({hasText: '未命中'}).waitFor();
    }
    return result;
  }
  async function movePair(grappleKey, path = route, dragPath = '') {
    await combat('DRAG_GRAPPLE', {grappleKey, path, dragPath});
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
    assert.equal((await fs.readdir(path.join(root, 'runs'))).length, 1);
    for (const name of [moverName, carriedName]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption('PC');
      await selectRule('domain-create', 'classKey', 'class.fighter');
      await selectRule('domain-create', 'fightingStyleKey', 'feature.fighter_defense');
      await action('domain', () => button('domain-create').click());
    }
    const mover = card(moverName).id, carried = card(carriedName).id;
    const node = await createNode(page, action, '咬击训练场');
    await focusSpace(page, action, 'WORLD', node);
    await placeAtFocus(page, action, [mover, carried]);
    await field('domain-monster-create', 'name').fill(houndName);
    await selectRule('domain-monster-create', 'monster', 'monster.hell_hound');
    await action('domain', () => button('domain-monster-create').click());
    const hound = domain().space.monsters.find(monster => monster.name === houndName).id;

    await page.locator('[data-pane="magic"]').click();
    const damage = 'domain-health';
    await field(damage, 'characterId').selectOption(mover);
    await selectRule(damage, 'rule', 'health.srd2014');
    await selectRule(damage, 'effect', 'vital_effect.dm_damage');
    for (let hit = 0; card(moverName).hitPoints > 6 && hit < 3; hit++)
      await action('domain', () => button(damage).click());
    assert.ok(card(moverName).hitPoints > 0 && card(moverName).hitPoints <= 6);

    await beginBattle(page, action, [[mover, 2, 2, 0], [carried, 2, 1, 0], [hound, 1, 2, 1]]);
    for (let attempt = 0; !grapple() && attempt < 12; attempt++) {
      await turnFor(mover, attempt > 0);
      await combat('BEGIN_ATTACK');
      await combat('GRAPPLE', {targetId: carried, hand: 'MAIN_HAND'});
      await action('domain', () => pending.getByRole('button', {name: '敏捷（杂技）', exact: true}).click());
    }
    assert.ok(grapple(), 'No grapple succeeded within twelve fresh turns');
    const grappleKey = grapple().grappleKey;

    await movePair(grappleKey);
    assertPrefix(mover, carried, hound, grappleKey);
    assert.deepEqual(dice(), []);
    await biteChoice.waitFor();
    await passChoice.waitFor();
    await fitViewport();
    await saveEndReopen();
    await page.setViewportSize({width: 390, height: 844});
    await fitViewport();
    assertPrefix(mover, carried, hound, grappleKey);
    await action('domain', () => passChoice.click());
    assert.equal(actor(hound).reactionAvailable, true);
    assert.deepEqual(dice(), []);
    await action('domain', () => next.click());
    assert.equal(movement().status, 'COMPLETED');
    assert.deepEqual(position(mover).grid, {x: 4, y: 5});
    assert.deepEqual(position(carried).grid, {x: 3, y: 4});
    assert.equal(actor(mover).movementSpentHalfFeet, 30);
    assert.equal(actor(carried).movementSpentHalfFeet, 0);
    assert.equal(attack(), undefined);

    let opportunity;
    const biteAttempts = [];
    for (let attempt = 0; attempt < 6; attempt++) {
      await turnFor(mover, true);
      await movePair(grappleKey, '4,4\n3,3\n2,2', '3,3\n2,2\n3,2');
      assert.equal(movement().status, 'COMPLETED');
      await turnFor(mover, true);
      await movePair(grappleKey);
      assertPrefix(mover, carried, hound, grappleKey);
      await saveEndReopen();
      assertPrefix(mover, carried, hound, grappleKey);
      const moverPlace = position(mover), carriedPlace = position(carried);
      const retainedAction = actor(hound).actionsRemaining;
      await action('domain', () => biteChoice.click());
      opportunity = await assertBite(hound, mover, true);
      biteAttempts.push({d20: opportunity.firstD20, hit: opportunity.hit});
      assert.equal(actor(hound).reactionAvailable, false);
      assert.equal(actor(hound).actionsRemaining, retainedAction);
      await action('domain', () => next.click());
      if (opportunity.hit) {
        assert.equal(card(moverName).hitPoints, 0);
        assert.equal(grapple(), undefined);
        assert.equal(movement().cursor, 1);
        assert.equal(movement().status, 'STOPPED');
        assert.deepEqual(position(mover), moverPlace);
        assert.deepEqual(position(carried), carriedPlace);
        assert.equal(actor(mover).movementSpentHalfFeet, 10);
        assert.equal(actor(carried).movementSpentHalfFeet, 0);
        break;
      }
      assert.equal(movement().status, 'COMPLETED');
    }
    assert.ok(opportunity?.hit, 'No opportunity Bite hit the weakened mover in six attempts');
    const log = await readHistory(page);
    assert.match(log, /借机攻击 · Bite.*穿刺 d8=.*火焰 d6=/);
    assert.match(log, /拖带移动.*停止.*已走 1\/3 格/);

    await turnFor(hound);
    const oldReaction = actor(hound).reactionAvailable;
    await field('domain-monster', 'actorId').selectOption(hound);
    await field('domain-monster', 'kind').selectOption('MONSTER_ATTACK');
    await field('domain-monster', 'attackAction').selectOption({label: 'Bite'});
    await field('domain-monster', 'targetId').selectOption(carried);
    await action('domain', () => button('domain-monster').click());
    const normal = await assertBite(hound, carried, false);
    assert.equal(actor(hound).actionsRemaining, 0);
    assert.equal(actor(hound).reactionAvailable, oldReaction);
    await saveEndReopen();
    assert.deepEqual(attack(), normal);
    assert.equal(movement().status, 'STOPPED');
    await page.setViewportSize({width: 1440, height: 1050});
    await fitViewport();

    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), passedReaction: true,
      reopenedBeforePassAndBite: true, biteAttempts, opportunityDamage: opportunity.damageAmount,
      normalHit: normal.hit, normalDamage: normal.damageAmount, stoppedPrefixFeet: 5, viewportWidths: [1440, 390]}));
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
