#!/usr/bin/env node
'use strict';

// Run against a fresh disposable StorageLoopLauncher instance on the protected loopback entry.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { observeStorageAction } = require('./browser-action.cjs');
const { selectStandardRules } = require('./browser-rule-library.cjs');
const { createNode, focusSpace, placeAtFocus, beginBattle } = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '回气浏览器验收';
const fighterName = '试炼战士';
const guardName = '试炼守卫';
const featureKey = 'feature.fighter_second_wind';
const resourceKey = 'resource.fighter.second_wind';

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
  const resource = () => card(fighterName).state.resources.find(row => row.identity.ruleKey === resourceKey);
  const history = () => domain().combats[0];
  const battle = () => history().current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const combatant = id => battle().combatants.find(row => row.characterId === id);

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

  async function combat(kind) {
    await field('domain-combat', 'kind').selectOption(kind);
    return action('domain', () => button('domain-combat').click());
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

    for (const [name, kind, classKey] of [[fighterName, 'PC', 'class.fighter'], [guardName, 'NPC', 'class.wizard']]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption(kind);
      await selectRule('domain-create', 'classKey', classKey);
      await action('domain', () => button('domain-create').click());
    }
    const fighter = card(fighterName).id;
    const guard = card(guardName).id;
    assert.equal(card(fighterName).level, 1);
    const grant = card(fighterName).state.features.find(feature => feature.feature.ruleKey === featureKey);
    assert.equal(grant.acquiredAtClassLevel, 1);
    assert.equal(grant.owner.ruleKey, 'class.fighter');
    assert.equal(resource().current, 1);
    assert.equal(resource().maximum, 1);
    await page.locator('[data-pane="people"]').click();
    await form('storage-characters').locator('article').filter({hasText: fighterName})
      .getByText(/特性：.*Second Wind/).waitFor();

    const world = await createNode(page, action, '回气训练营');
    await focusSpace(page, action, 'WORLD', world);
    await placeAtFocus(page, action, [fighter, guard]);
    await page.locator('[data-pane="magic"]').click();
    await field('domain-health', 'characterId').selectOption(fighter);
    await field('domain-health', 'kind').selectOption('HEALTH_EFFECT_RESOLVED');
    await selectRule('domain-health', 'effect', 'vital_effect.dm_damage');
    await action('domain', () => button('domain-health').click());
    const {hitPoints: wounded, maximumHitPoints: maximum} = card(fighterName);
    assert.equal(wounded, maximum - 6);
    assert.ok(wounded > 0);

    await beginBattle(page, action, [[fighter, 1, 1, 0], [guard, 2, 1, 1]]);
    if (active() !== fighter) await combat('END_TURN');
    assert.equal(active(), fighter);
    await combat('DASH');
    const actorBefore = combatant(fighter);
    assert.equal(actorBefore.actionAvailable, false);
    assert.ok(actorBefore.movementBonus > 0);

    await field('domain-combat', 'kind').selectOption({label: '回气（附赠动作）'});
    assert.equal(await field('domain-combat', 'actorId').inputValue(), fighter);
    assert.equal(await field('domain-combat', 'targetId').isEnabled(), false);
    const observed = await observeStorageAction(page, 'domain', () => button('domain-combat').click(), {});
    current = observed.body;
    const submitted = JSON.parse(observed.request.get('payload')).command;
    assert.equal(submitted.kind, 'SECOND_WIND');
    assert.equal(submitted.actorId, fighter);
    assert.equal(submitted.targetId, null);
    for (const field of ['amount', 'die', 'source']) assert.equal(Object.hasOwn(submitted, field), false);

    const event = history().events.at(-1);
    assert.equal(event.command.kind, 'SECOND_WIND');
    const [roll] = event.resolution.rolls;
    assert.ok(Number.isInteger(roll.value) && roll.value >= 1 && roll.value <= 10);
    assert.deepEqual(event.resolution.rolls, [{purpose: 'SECOND_WIND_HEALING', subjectId: fighter, sides: 10, value: roll.value}]);
    const [payment, healing] = event.resolution.healthChanges;
    assert.equal(event.resolution.healthChanges.length, 2);
    assert.equal(payment.resource.ruleKey, resourceKey);
    assert.equal(payment.resourceAmount, 1);
    assert.equal(payment.characterId, fighter);
    assert.equal(healing.characterId, fighter);
    const applied = Math.min(maximum - wounded, roll.value + 1);
    assert.deepEqual(healing.healthResult.dice, [roll.value]);
    assert.equal(healing.healthResult.rolledAmount, roll.value + 1);
    assert.equal(healing.healthResult.appliedAmount, applied);
    assert.equal(card(fighterName).hitPoints, wounded + applied);
    assert.equal(resource().current, 0);
    assert.deepEqual(combatant(fighter), actorBefore);
    const summary = form('domain-combat-summary').locator('.second-wind-result');
    await summary.filter({hasText: `d10=${roll.value} + 战士等级 1`}).filter({hasText: `实际恢复 ${applied}`})
      .filter({hasText: '使用次数 1 → 0/1'}).waitFor();

    const before = domain();
    await action('save', () => form('storage-save').click());
    const ended = await action('end', () => form('storage-end').click());
    owned = false;
    await form('storage-files').selectOption(ended.savedFile);
    await action('open', () => form('storage-open').locator('button[type="submit"]').click());
    owned = true;
    assert.deepEqual(domain(), before);
    await page.locator('[data-pane="combat"]').click();
    await summary.filter({hasText: `d10=${roll.value} + 战士等级 1`}).waitFor();
    await combat('END_TURN');
    assert.equal(active(), guard);
    await combat('END_COMBAT');
    assert.equal(battle().status, 'EXPLORATION');
    assert.equal(resource().current, 0);
    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), featureKey, fighterLevel: 1,
      die: roll.value, appliedHealing: applied, hitPoints: wounded + applied, remainingUses: 0, reopenedAndContinued: true}));
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
