#!/usr/bin/env node
'use strict';

// Run against a fresh disposable run-local.sh instance on the protected loopback entry.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, readHistory} = require('./browser-action.cjs');
const {selectStandardRules} = require('./browser-rule-library.cjs');
const {createNode, focusSpace, placeAtFocus, createScene, enterScene} = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '狼啮咬浏览器验收';
const wolfName = '袭击的狼';
const observerName = '巡林战士';

async function main() {
  const [flag, storageRoot] = process.argv.slice(2);
  assert.ok(process.argv.length === 4 && flag === '--fresh-root' && path.isAbsolute(storageRoot),
    'Usage: node tools/storage/browser-wolf-bite.cjs --fresh-root /absolute/private/test-root');
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
    if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text())) pageErrors.push(event.text());
  });
  const form = id => page.locator('#' + id);
  const button = id => form(id).locator('button[type="submit"], button:not([type])');
  const field = (id, name) => form(id).locator(`[name="${name}"]`);
  const combatPane = () => page.locator('[data-pane="combat"]').click();
  const summary = form('domain-combat-summary');
  let current;
  let owned = false;
  const domain = () => current.campaign.domain;
  const combatHistory = () => domain().combats.find(history => history.current.combatId === domain().space.focus.space.id);
  const battle = () => combatHistory().current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const actor = id => battle().combatants.find(value => value.characterId === id);
  const monster = id => domain().space.monsters.find(value => value.id === id);
  const position = id => domain().positions.find(value => value.characterId === id);
  const clock = () => Number(domain().space.clocks.find(value => value.space.id === battle().combatId).second);
  const lastAttack = () => battle().flows.flatMap(flow => flow.attacks).findLast(attack => attack.monsterAttack);
  const lastRolls = () => combatHistory().events.at(-1).resolution.rolls.map(roll => roll.value);

  async function action(name, trigger) {
    current = (await observeStorageAction(page, name, trigger, {})).body;
    return current;
  }

  async function selectRule(formId, fieldName, ruleKey) {
    const select = field(formId, fieldName);
    const value = (await select.locator('option').evaluateAll(options => options.map(option => option.value)))
      .find(option => option.endsWith('/' + ruleKey));
    assert.ok(value, `Missing selectable rule ${ruleKey}`);
    await select.selectOption(value);
  }

  async function createMonster(name, ruleKey) {
    await page.locator('[data-pane="world"]').click();
    await field('domain-monster-create', 'name').fill(name);
    await selectRule('domain-monster-create', 'monster', ruleKey);
    await action('domain', () => button('domain-monster-create').click());
    return domain().space.monsters.find(value => value.name === name).id;
  }

  async function combat(kind, acting = undefined) {
    await field('domain-combat', 'kind').selectOption(kind);
    if (acting) await field('domain-combat', 'actorId').selectOption(acting);
    await action('domain', () => button('domain-combat').click());
  }

  async function turnFor(id) {
    for (let turns = 0; active() !== id && turns < battle().initiative.length; turns++) await combat('END_TURN');
    assert.equal(active(), id);
  }

  async function leaveScene(ids) {
    await page.locator('[data-pane="world"]').click();
    for (const id of ids) await form('domain-scene-leave-subjects').locator(`[data-character="${id}"] [name="join"]`).check();
    await action('domain', () => button('domain-scene-leave').click());
    assert.equal(domain().space.focus.space.kind, 'WORLD');
  }

  async function saveEndReopen() {
    const before = domain();
    await action('save', () => form('storage-save').click());
    const ended = await action('end', () => form('storage-end').click());
    owned = false;
    await form('storage-files').selectOption(ended.savedFile);
    await action('open', () => button('storage-open').click());
    owned = true;
    assert.deepEqual(domain(), before);
    await combatPane();
  }

  function posture(name, label) {
    const row = summary.getByRole('row').filter({has: page.getByRole('cell', {name, exact: true})});
    return row.getByRole('cell', {name: label, exact: true});
  }

  async function fitViewport() {
    assert.ok(await page.evaluate(() => {
      const center = document.getElementById('workspace-center-scroll');
      return document.documentElement.scrollWidth <= innerWidth && center.scrollWidth <= center.clientWidth;
    }));
  }

  async function strike(wolf, target) {
    const before = monster(target).hitPoints;
    await field('domain-monster', 'actorId').selectOption(wolf);
    await field('domain-monster', 'kind').selectOption('MONSTER_ATTACK');
    await field('domain-monster', 'attackAction').selectOption({label: 'Bite'});
    await field('domain-monster', 'targetId').selectOption(target);
    await action('domain', () => button('domain-monster').click());
    const attack = lastAttack();
    const evidence = attack.monsterAttack;
    assert.equal(attack.actorId, wolf);
    assert.equal(attack.targetId, target);
    assert.equal(attack.opportunityAttack, false);
    assert.equal(attack.status, 'RESOLVED');
    assert.equal(actor(wolf).actionsRemaining, 0);
    assert.equal(actor(wolf).reactionAvailable, true);
    assert.equal(evidence.action.rule.ruleKey, 'monster.wolf');
    assert.equal(evidence.action.entryKey, 'bite');
    assert.equal(evidence.hitBonus, 4);
    assert.equal(attack.secondD20, null);
    const line = summary.locator('.monster-attack-result').filter({hasText: monster(target).name});
    await line.filter({hasText: `d20=${attack.firstD20}`}).waitFor();
    if (!attack.hit) {
      assert.equal(monster(target).hitPoints, before);
      assert.equal(evidence.onHit, null);
      assert.deepEqual(lastRolls(), [attack.firstD20]);
      await line.filter({hasText: '未命中'}).waitFor();
      return attack;
    }
    const component = evidence.damageComponents[0], outcome = evidence.onHit, save = outcome.savingThrow;
    assert.equal(before - monster(target).hitPoints, attack.damageAmount);
    assert.equal(evidence.damageComponents.length, 1);
    assert.equal(component.damageType, 'PIERCING');
    assert.equal(component.modifier, 2);
    assert.equal(component.dice.length, attack.critical ? 4 : 2);
    assert.equal(attack.damageAmount, component.dice.reduce((total, die) => total + die, 2));
    assert.equal(save.abilityKey, 'ability.strength');
    assert.equal(save.difficultyClass, 11);
    assert.equal(save.total, save.firstD20 + 3);
    assert.equal(save.success, save.total >= 11);
    assert.deepEqual(lastRolls(), [attack.firstD20, ...component.dice, save.firstD20]);
    await line.filter({hasText: `穿刺 d4=${component.dice.join(' / ')}`})
      .filter({hasText: /力量豁免 DC 11/}).filter({hasText: `d20=${save.firstD20}`})
      .filter({hasText: `总值 ${save.total}`}).filter({hasText: save.success ? '豁免成功' : '目标倒地'}).waitFor();
    assert.equal(actor(target).prone, !save.success);
    await posture(monster(target).name, save.success ? '站立' : '倒地').waitFor();
    return attack;
  }

  try {
    await page.goto(origin + '/host/storage');
    await form('storage-message').filter({hasText: /^创建、打开或恢复一个战役。$/}).waitFor();
    const response = await page.request.get(origin + '/api/host/storage/state');
    assert.equal(response.status(), 200);
    const idle = await response.json();
    assert.equal(idle.storage.state, 'IDLE');
    assert.equal(idle.campaign, null);
    assert.deepEqual(idle.savedCampaigns, []);
    assert.deepEqual(idle.storage.residualRunIds, []);
    await selectStandardRules(page);
    await field('storage-create', 'name').fill(campaignName);
    await action('create', () => button('storage-create').click());
    owned = true;
    assert.equal((await fs.readdir(path.join(root, 'runs'))).length, 1);

    await page.locator('[data-pane="people"]').click();
    await field('domain-create', 'name').fill(observerName);
    await field('domain-create', 'kind').selectOption('PC');
    await selectRule('domain-create', 'classKey', 'class.fighter');
    await selectRule('domain-create', 'fightingStyleKey', 'feature.fighter_defense');
    await action('domain', () => button('domain-create').click());
    const observer = domain().characters.find(character => character.name === observerName).id;
    const world = await createNode(page, action, '林地');
    await focusSpace(page, action, 'WORLD', world);
    await placeAtFocus(page, action, [observer]);
    const wolf = await createMonster(wolfName, 'monster.wolf');
    assert.equal(monster(wolf).hitPoints, 11);

    let fallen, target;
    const attempts = [];
    for (let attempt = 0; attempt < 30; attempt++) {
      if (!target || monster(target).hitPoints <= 18) {
        if (target) {
          await combat('END_COMBAT');
          await leaveScene([wolf, target, observer]);
        }
        target = await createMonster(`守林地狱犬 ${attempt + 1}`, 'monster.hell_hound');
        const scene = await createScene(page, action, `林地交战 ${attempt + 1}`, 'RULES');
        await enterScene(page, action, scene, [[wolf, 1, 2, 0], [target, 2, 2, 1], [observer, 7, 7, 1]]);
        await combatPane();
        await combat('REENTER_COMBAT', observer);
      } else await combat('END_TURN');
      await turnFor(wolf);
      const attack = await strike(wolf, target);
      attempts.push({attack: attack.firstD20, hit: attack.hit, save: attack.monsterAttack.onHit?.savingThrow.total ?? null});
      if (attack.monsterAttack.onHit?.outcome === 'PRONE_APPLIED') { fallen = attack; break; }
    }
    assert.ok(fallen, `No Bite knocked its target prone in ${attempts.length} attempts`);
    const targetName = monster(target).name, struckScene = battle().combatId, retainedHp = monster(target).hitPoints;
    assert.equal(monster(target).prone, true);
    await fitViewport();
    await saveEndReopen();
    assert.deepEqual(lastAttack(), fallen);
    await posture(targetName, '倒地').waitFor();
    await summary.locator('.monster-attack-result').filter({hasText: targetName}).filter({hasText: '力量豁免 DC 11'}).waitFor();
    assert.match(await readHistory(page), /近战攻击 · Bite.*力量豁免 DC 11.*豁免失败.*目标倒地/);
    await page.setViewportSize({width: 390, height: 844});
    await fitViewport();

    await combat('END_COMBAT');
    await leaveScene([wolf, target, observer]);
    assert.deepEqual(position(target).space, {kind: 'WORLD', id: world});
    assert.equal(monster(target).prone, true);
    const secondScene = await createScene(page, action, '另一片林地', 'RULES');
    await enterScene(page, action, secondScene, [[target, 3, 3, 1], [observer, 7, 7, 0]]);
    await combatPane();
    assert.notEqual(battle().combatId, struckScene);
    assert.equal(battle().status, 'EXPLORATION');
    assert.equal(monster(target).hitPoints, retainedHp);
    assert.equal(actor(target).prone, true);
    await posture(targetName, '倒地').waitFor();
    const beforeStand = clock();
    await combat('STAND_UP', target);
    await posture(targetName, '站立').waitFor();
    assert.equal(actor(target).prone, false);
    assert.equal(monster(target).prone, false);
    assert.equal(clock(), beforeStand + 6);
    assert.deepEqual(lastRolls(), []);

    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), attempts, damage: fallen.damageAmount,
      dc: 11, targetHitPoints: retainedHp, reopenedProne: true, stoodInAnotherScene: true, viewportWidths: [1440, 390]}));
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
