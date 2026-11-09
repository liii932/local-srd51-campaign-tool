#!/usr/bin/env node
'use strict';

// Run against a fresh disposable StorageLoopLauncher instance on the protected loopback entry.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { observeStorageAction } = require('./browser-action.cjs');
const { selectStandardRules } = require('./browser-rule-library.cjs');
const { createNode, focusSpace, placeAtFocus, beginBattle } = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '冠军重击浏览器验收';
const fighterName = '试炼冠军';
const targetName = '试炼守卫';
const featureKey = 'feature.champion_improved_critical';

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
  const attacks = () => battle().flows.flatMap(flow => flow.attacks);
  const features = () => card(fighterName).state.features.filter(feature => feature.feature.ruleKey === featureKey);

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

  async function strike(fighter, target, weapon) {
    const hp = card(targetName).hitPoints;
    assert.ok(hp > 0, 'The target must be standing before the attack');
    await combat('BEGIN_ATTACK');
    await combat('ATTACK', target, weapon);
    const attempt = attacks().at(-1);
    assert.equal(attempt.status, 'RESOLVED');
    assert.equal(attempt.actorId, fighter);
    assert.equal(attempt.targetId, target);
    assert.ok(attempt.firstD20 >= 1 && attempt.firstD20 <= 20);
    assert.equal(attempt.firstD20, attempt.selectedD20);
    const critical = attempt.selectedD20 >= 19;
    const hit = critical || attempt.selectedD20 !== 1
      && attempt.selectedD20 + attempt.abilityModifier + attempt.proficiencyBonus >= attempt.finalArmorClass;
    assert.equal(attempt.hit, hit);
    assert.equal(attempt.critical, critical);
    assert.equal(attempt.damageDice.length, hit ? (critical ? 2 : 1) : 0);
    const rolled = attempt.damageDice.reduce((sum, die) => sum + die, 0);
    assert.equal(attempt.damageAmount, hit ? rolled + attempt.abilityModifier : 0);
    assert.equal(card(targetName).hitPoints, Math.max(0, hp - attempt.damageAmount));
    await form('domain-combat-summary').getByText('已攻击 1/1 · 剩余 0', {exact: true}).waitFor();
    return attempt;
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

    for (const [name, kind] of [[fighterName, 'PC'], [targetName, 'NPC']]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption(kind);
      await selectRule('domain-create', 'classKey', 'class.fighter');
      await action('domain', () => button('domain-create').click());
    }
    const fighter = card(fighterName).id;
    const target = card(targetName).id;
    await page.locator('[data-pane="growth"]').click();
    for (const level of [2, 3]) {
      await field('domain-growth', 'characterId').selectOption(fighter);
      await selectRule('domain-growth', 'class', 'class.fighter');
      if (level === 3) await selectRule('domain-growth', 'subclass', 'subclass.champion');
      await action('domain', () => button('domain-growth').click());
      assert.equal(card(fighterName).level, level);
      assert.deepEqual(features().map(feature => feature.acquiredAtClassLevel), level === 3 ? [3] : []);
    }
    assert.equal(features()[0].owner.ruleKey, 'subclass.champion');
    await page.locator('[data-pane="people"]').click();
    await form('storage-characters').locator('article').filter({hasText: fighterName})
      .getByText(/特性：.*Improved Critical/).waitFor();

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
    await placeAtFocus(page, action, [fighter, target]);
    await beginBattle(page, action, [[fighter, 1, 1, 0], [target, 2, 1, 1]]);
    if (active() !== fighter) await combat('END_TURN');
    assert.equal(active(), fighter);
    const first = await strike(fighter, target, weapon);

    const before = domain();
    await action('save', () => form('storage-save').click());
    const ended = await action('end', () => form('storage-end').click());
    owned = false;
    await form('storage-files').selectOption(ended.savedFile);
    await action('open', () => form('storage-open').locator('button[type="submit"]').click());
    owned = true;
    assert.deepEqual(domain(), before);
    assert.deepEqual(attacks(), [first]);

    await page.locator('[data-pane="combat"]').click();
    await combat('END_TURN');
    assert.equal(active(), target);
    await combat('END_TURN');
    assert.equal(active(), fighter);
    const second = await strike(fighter, target, weapon);
    assert.deepEqual(attacks(), [first, second]);
    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), featureKey, acquiredAt: 3,
      attacks: [first, second].map(strike => ({d20: strike.selectedD20, hit: strike.hit, critical: strike.critical})),
      reopenedAfterFirstStrike: true}));
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
