#!/usr/bin/env node
'use strict';

// Run against a fresh disposable StorageLoopLauncher instance on the protected loopback entry.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { observeStorageAction } = require('./browser-action.cjs');
const { selectStandardRules } = require('./browser-rule-library.cjs');
const { createNode, focusSpace, placeAtFocus, beginBattle } = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '额外攻击浏览器验收';
const featureKey = 'feature.fighter_extra_attack';
const fighterName = '试炼战士';

async function main() {
  const browser = await chromium.launch({headless: true});
  const page = await browser.newPage({locale: 'zh-CN'});
  page.setDefaultTimeout(30000);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', event => {
    if (/Content Security Policy|violates.*directive/i.test(event.text())) pageErrors.push(event.text());
  });
  const form = id => page.locator('#' + id);
  const button = id => form(id).locator('button[type="submit"], button:not([type])');
  const field = (id, name) => form(id).locator(`[name="${name}"]`);
  let current;
  let owned = false;
  const domain = () => current.campaign.domain;
  const card = name => domain().characters.find(character => character.name === name);
  const battle = () => domain().combats[0].current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const actor = id => battle().combatants.find(value => value.characterId === id);
  const attacks = key => battle().flows.flatMap(flow => flow.attacks).filter(attack => attack.sequenceKey === key);
  const extraAttacks = () => card(fighterName).state.features.filter(feature => feature.feature.ruleKey === featureKey);

  async function action(name, trigger) {
    current = (await observeStorageAction(page, name, trigger, {})).body;
    return current;
  }
  async function selectRule(formId, name, key) {
    const select = field(formId, name);
    const value = (await select.locator('option').evaluateAll(options => options.map(option => option.value)))
      .find(option => option.endsWith('/' + key));
    assert.ok(value, `Missing selectable rule ${key}`);
    await select.selectOption(value);
  }
  async function combat(kind, target, weapon) {
    await field('domain-combat', 'kind').selectOption(kind);
    if (target) await field('domain-combat', 'targetId').selectOption(target);
    if (weapon) await field('domain-combat', 'itemId').selectOption(weapon);
    await action('domain', () => button('domain-combat').click());
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
    const preview = JSON.parse(await form('rule-preview-result').textContent());
    await field('storage-create', 'name').fill(campaignName);
    await action('create', () => form('storage-create').locator('button[type="submit"]').click());
    owned = true;
    assert.deepEqual(domain().rules.binding, preview.binding);
    assert.equal(domain().rules.catalog.find(rule => rule.key === featureKey).executable, true);

    for (const [name, kind] of [[fighterName, 'PC'], ['第一守卫', 'NPC'], ['第二守卫', 'NPC']]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption(kind);
      await selectRule('domain-create', 'classKey', 'class.fighter');
      await action('domain', () => button('domain-create').click());
    }
    const fighter = card(fighterName).id;
    const targets = ['第一守卫', '第二守卫'].map(name => card(name).id);
    await page.locator('[data-pane="growth"]').click();
    for (const level of [2, 3, 4, 5, 6]) {
      await field('domain-growth', 'characterId').selectOption(fighter);
      await selectRule('domain-growth', 'class', 'class.fighter');
      if (level === 3) await selectRule('domain-growth', 'subclass', 'subclass.champion');
      else await field('domain-growth', 'subclass').selectOption('');
      await field('domain-growth', 'ability').selectOption(level === 4 || level === 6 ? 'ability.strength' : '');
      await action('domain', () => button('domain-growth').click());
      assert.equal(card(fighterName).level, level);
      assert.deepEqual(extraAttacks().map(feature => feature.acquiredAtClassLevel), level < 5 ? [] : [5]);
    }
    await page.locator('[data-pane="people"]').click();
    assert.match(await form('storage-characters').locator('article').filter({hasText: fighterName}).innerText(), /Extra Attack/);

    await page.locator('[data-pane="actions"]').click();
    await field('domain-equipment', 'characterId').selectOption(fighter);
    await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
    await selectRule('domain-equipment', 'item', 'item.dagger');
    await action('domain', () => button('domain-equipment').click());
    const weapon = card(fighterName).state.equipment.find(item => item.item.ruleKey === 'item.dagger').itemId;
    await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED');
    await field('domain-equipment', 'itemId').selectOption(weapon);
    await field('domain-equipment', 'location').selectOption('MAIN_HAND');
    await action('domain', () => button('domain-equipment').click());
    assert.equal(card(fighterName).state.equipment.find(item => item.itemId === weapon).location, 'MAIN_HAND');

    const world = await createNode(page, action, '武器练习场');
    await focusSpace(page, action, 'WORLD', world);
    await placeAtFocus(page, action, [fighter, ...targets]);
    await beginBattle(page, action, [[fighter, 1, 1, 0], [targets[0], 2, 1, 1], [targets[1], 1, 2, 1]]);
    for (let turns = 0; active() !== fighter && turns < targets.length; turns++) await combat('END_TURN');
    assert.equal(active(), fighter);

    await combat('BEGIN_ATTACK');
    const sequence = actor(fighter).sequence;
    assert.equal(sequence.limit, 2);
    assert.equal(sequence.performed, 0);
    assert.equal(actor(fighter).actionAvailable, false);
    await combat('ATTACK', targets[0], weapon);
    const first = attacks(sequence.sequenceKey)[0];
    assert.equal(first.status, 'RESOLVED');
    assert.equal(first.attackOrdinal, 0);
    assert.equal(first.targetId, targets[0]);
    assert.ok(first.firstD20 >= 1 && first.firstD20 <= 20);
    assert.deepEqual(actor(fighter).sequence, {...sequence, performed: 1});
    await form('domain-combat-summary').getByText('已攻击 1/2 · 剩余 1', {exact: true}).waitFor();

    const before = domain();
    await action('save', () => form('storage-save').click());
    const ended = await action('end', () => form('storage-end').click());
    owned = false;
    await form('storage-files').selectOption(ended.savedFile);
    await action('open', () => form('storage-open').locator('button[type="submit"]').click());
    owned = true;
    assert.deepEqual(domain(), before);
    assert.deepEqual(attacks(sequence.sequenceKey), [first]);
    assert.equal(actor(fighter).actionAvailable, false);

    await page.locator('[data-pane="combat"]').click();
    await form('domain-combat-summary').getByText('已攻击 1/2 · 剩余 1', {exact: true}).waitFor();
    await combat('ATTACK', targets[1], weapon);
    const strikes = attacks(sequence.sequenceKey);
    assert.deepEqual(strikes.map(strike => strike.attackOrdinal), [0, 1]);
    assert.deepEqual(strikes.map(strike => strike.targetId), targets);
    assert.deepEqual(strikes[0], first);
    assert.equal(strikes[1].status, 'RESOLVED');
    assert.deepEqual(actor(fighter).sequence, {...sequence, performed: 2, closed: true});
    assert.equal(actor(fighter).actionAvailable, false);
    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), growthLevelsVerified: [4, 5, 6], attackLevel: 6,
      attacks: strikes.map(strike => ({ordinal: strike.attackOrdinal, hit: strike.hit})), reopenedAfterFirstStrike: true}));
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
