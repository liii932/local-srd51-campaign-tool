#!/usr/bin/env node
'use strict';

// Run against a fresh disposable StorageLoopLauncher on the protected loopback entry.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { observeStorageAction, waitForStorageReady } = require('./browser-action.cjs');
const { selectStandardRules } = require('./browser-rule-library.cjs');
const { createNode, focusSpace, placeAtFocus, beginBattle } = require('./browser-spaces.cjs');

const origin = 'http://127.0.0.1:8080';
const campaignName = '卧倒浏览器验收';
const fighterName = '试炼战士';
const guardName = '试炼守卫';

async function main() {
  const browser = await chromium.launch({headless: true});
  const page = await browser.newPage({locale: 'zh-CN', viewport: {width: 1440, height: 1050}});
  page.setDefaultTimeout(30000);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', event => {
    if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text())) {
      pageErrors.push(event.text());
    }
  });
  const form = id => page.locator('#' + id);
  const button = id => form(id).locator('button[type="submit"], button:not([type])');
  const field = (id, name) => form(id).locator(`[name="${name}"]`);
  const kind = field('domain-combat', 'kind');
  const note = form('domain-combat-note');
  const fighterRow = form('domain-combat-summary').getByRole('row')
    .filter({has: page.getByRole('cell', {name: fighterName, exact: true})});
  const posture = name => fighterRow.getByRole('cell', {name, exact: true});
  let current;
  let owned = false;
  const domain = () => current.campaign.domain;
  const card = name => domain().characters.find(character => character.name === name);
  const history = () => domain().combats[0];
  const battle = () => history().current;
  const active = () => battle().initiative[battle().activeOrdinal].characterId;
  const combatant = id => battle().combatants.find(row => row.characterId === id);
  const latestMovement = () => battle().flows.at(-1).movements[0];
  const sceneSecond = () => Number(domain().space.clocks.find(clock => clock.space.id === battle().combatId).second);

  async function action(name, trigger) {
    current = (await observeStorageAction(page, name, trigger, {})).body;
    return current;
  }

  async function submitCombat() {
    const observed = await observeStorageAction(page, 'domain', () => button('domain-combat').click(), {});
    current = observed.body;
    return JSON.parse(observed.request.get('payload')).command;
  }

  async function rejectDomain(trigger) {
    const before = current.campaign;
    const [rejected, refreshed] = await Promise.all([
      page.waitForResponse(res => res.url() === origin + '/api/host/storage/domain' && res.request().method() === 'POST'),
      page.waitForResponse(res => res.url() === origin + '/api/host/storage/state' && res.request().method() === 'GET'),
      trigger()
    ]);
    assert.equal(rejected.status(), 409);
    assert.equal((await rejected.json()).code, 'DOMAIN_REJECTED');
    assert.equal(refreshed.status(), 200);
    current = await refreshed.json();
    assert.deepEqual(current.campaign, before);
    await waitForStorageReady(page);
    await form('storage-message').filter({hasText: '已重新读取当前战役'}).waitFor();
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
    await page.locator('[data-pane="combat"]').click();
    await posture('倒地').waitFor();
  }

  async function assertViewportFits() {
    const widths = await page.evaluate(() => [innerWidth, document.documentElement.scrollWidth, document.body.scrollWidth]);
    assert.ok(widths.slice(1).every(width => width <= widths[0]), `Page overflows viewport: ${widths}`);
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
    const preview = JSON.parse(await form('rule-preview-result').textContent());
    await field('storage-create', 'name').fill(campaignName);
    await action('create', () => button('storage-create').click());
    owned = true;
    assert.deepEqual(domain().rules.binding, preview.binding);

    await page.locator('[data-pane="people"]').click();
    for (const [name, type, classKey] of [[fighterName, 'PC', 'class.fighter'], [guardName, 'NPC', 'class.wizard']]) {
      await field('domain-create', 'name').fill(name);
      await field('domain-create', 'kind').selectOption(type);
      const classes = field('domain-create', 'classKey');
      const value = (await classes.locator('option').evaluateAll(options => options.map(option => option.value)))
        .find(option => option.endsWith('/' + classKey));
      assert.ok(value, `Missing selectable rule ${classKey}`);
      await classes.selectOption(value);
      await action('domain', () => button('domain-create').click());
    }
    const fighter = card(fighterName).id;
    const guard = card(guardName).id;
    const world = await createNode(page, action, '姿态训练营');
    await focusSpace(page, action, 'WORLD', world);
    await placeAtFocus(page, action, [fighter, guard]);
    await beginBattle(page, action, [[fighter, 1, 1, 0], [guard, 5, 5, 1]]);
    if (active() !== fighter) {
      await kind.selectOption('END_TURN');
      await submitCombat();
    }
    assert.equal(active(), fighter);
    await posture('站立').waitFor();
    await assertViewportFits();

    const standing = combatant(fighter);
    await kind.selectOption({label: '主动卧倒'});
    await note.filter({hasText: '保留动作、反应与移动力'}).waitFor();
    assert.equal(await field('domain-combat', 'actorId').inputValue(), fighter);
    assert.equal(await field('domain-combat', 'path').isEnabled(), false);
    const drop = await submitCombat();
    assert.equal(drop.kind, 'DROP_PRONE');
    assert.equal(drop.actorId, fighter);
    assert.equal(drop.targetId, null);
    assert.deepEqual(drop.path, []);
    assert.deepEqual(combatant(fighter), {...standing, prone: true});
    assert.equal(history().events.at(-1).command.kind, 'DROP_PRONE');
    await posture('倒地').waitFor();

    await page.setViewportSize({width: 390, height: 844});
    await assertViewportFits();
    await rejectDomain(() => button('domain-combat').click());
    await posture('倒地').waitFor();

    await kind.selectOption('MOVE');
    await note.filter({hasText: '匍匐，每格消耗 10 尺移动力'}).waitFor();
    await field('domain-combat', 'x').fill('2');
    await field('domain-combat', 'y').fill('1');
    const crawl = await submitCombat();
    assert.equal(crawl.kind, 'MOVE');
    assert.deepEqual(crawl.path, [{type: 'scene.Position', x: '2', y: '1'}]);
    for (const key of ['movementCost', 'locomotion', 'prone']) assert.equal(Object.hasOwn(crawl, key), false);
    assert.equal(combatant(fighter).prone, true);
    const crawlFeet = (combatant(fighter).movementSpentHalfFeet - standing.movementSpentHalfFeet) / 2;
    assert.equal(crawlFeet, 10);
    assert.equal(latestMovement().status, 'COMPLETED');
    assert.equal(latestMovement().spent, 5);

    await saveEndReopen();
    await kind.selectOption('STAND_UP');
    await note.filter({hasText: '当前速度一半'}).waitFor();
    await submitCombat();
    await posture('站立').waitFor();
    const stood = combatant(fighter);
    assert.equal(stood.prone, false);
    assert.equal(stood.movementSpentHalfFeet, 50);
    assert.equal(stood.actionAvailable, standing.actionAvailable);
    assert.equal(stood.reactionAvailable, standing.reactionAvailable);

    await kind.selectOption('MOVE');
    assert.doesNotMatch(await note.textContent(), /匍匐/);
    await field('domain-combat', 'x').fill('3');
    await field('domain-combat', 'y').fill('1');
    await submitCombat();
    assert.equal(latestMovement().status, 'COMPLETED');
    assert.equal(latestMovement().spent, 5);
    const walkFeet = (combatant(fighter).movementSpentHalfFeet - stood.movementSpentHalfFeet) / 2;
    assert.equal(walkFeet, 5);

    await kind.selectOption('DROP_PRONE');
    await submitCombat();
    await kind.selectOption('END_COMBAT');
    await submitCombat();
    assert.equal(battle().status, 'EXPLORATION');
    assert.equal(await kind.locator('option[value="DROP_PRONE"]').count(), 1);
    await field('domain-combat', 'actorId').selectOption(fighter);
    await posture('倒地').waitFor();
    const beforeStandSecond = sceneSecond();
    const explorationBudget = combatant(fighter);
    await kind.selectOption('STAND_UP');
    await note.filter({hasText: '推进场景时钟 6 秒'}).waitFor();
    await submitCombat();
    await posture('站立').waitFor();
    assert.equal(sceneSecond(), beforeStandSecond + 6);
    assert.equal(combatant(fighter).movementSpentHalfFeet, explorationBudget.movementSpentHalfFeet);

    const explorationStanding = combatant(fighter);
    const beforeDropSecond = sceneSecond();
    await kind.selectOption('DROP_PRONE');
    await note.filter({hasText: '探索中主动卧倒，不推进场景时钟'}).waitFor();
    await submitCombat();
    await posture('倒地').waitFor();
    assert.deepEqual(combatant(fighter), {...explorationStanding, prone: true});
    assert.equal(sceneSecond(), beforeDropSecond);

    await kind.selectOption('EXPLORATION_MOVE');
    await note.filter({hasText: '匍匐，每格按 10 尺计入当前速度上限'}).waitFor();
    await page.setViewportSize({width: 1440, height: 1050});
    await assertViewportFits();
    await page.setViewportSize({width: 390, height: 844});
    await assertViewportFits();
    await field('domain-combat', 'path').fill('4,1\n5,1\n6,1\n7,1');
    await rejectDomain(() => button('domain-combat').click());
    await field('domain-combat', 'path').fill('4,1\n5,1\n6,1');
    const explorationCrawl = await submitCombat();
    assert.equal(explorationCrawl.kind, 'EXPLORATION_MOVE');
    assert.equal(explorationCrawl.path.length, 3);
    for (const key of ['movementCost', 'locomotion', 'prone']) assert.equal(Object.hasOwn(explorationCrawl, key), false);
    assert.equal(sceneSecond(), beforeDropSecond + 6);
    assert.deepEqual(combatant(fighter).position, {x: 6, y: 1});
    assert.equal(combatant(fighter).prone, true);
    assert.equal(combatant(fighter).movementSpentHalfFeet, explorationBudget.movementSpentHalfFeet);

    await saveEndReopen();
    assert.equal(battle().status, 'EXPLORATION');
    await field('domain-combat', 'actorId').selectOption(fighter);
    await kind.selectOption('EXPLORATION_MOVE');
    await field('domain-combat', 'path').fill('7,1\n8,1\n9,1');
    await submitCombat();
    assert.deepEqual(combatant(fighter).position, {x: 9, y: 1});
    assert.equal(combatant(fighter).prone, true);
    assert.equal(combatant(fighter).movementSpentHalfFeet, explorationBudget.movementSpentHalfFeet);
    assert.equal(sceneSecond(), beforeDropSecond + 12);

    await page.locator('[data-pane="world"]').click();
    await form('domain-scene-leave-subjects').locator(`[data-character="${fighter}"] [name="join"]`).check();
    await rejectDomain(() => button('domain-scene-leave').click());
    await page.locator('[data-pane="combat"]').click();
    await field('domain-combat', 'actorId').selectOption(fighter);
    await kind.selectOption('STAND_UP');
    await submitCombat();
    assert.equal(sceneSecond(), beforeDropSecond + 18);
    assert.equal(combatant(fighter).prone, false);
    assert.equal(combatant(fighter).movementSpentHalfFeet, explorationBudget.movementSpentHalfFeet);
    assert.equal(combatant(fighter).actionAvailable, explorationBudget.actionAvailable);
    assert.equal(combatant(fighter).reactionAvailable, explorationBudget.reactionAvailable);

    await kind.selectOption('EXPLORATION_MOVE');
    await note.filter({hasText: '每格按 5 尺计入当前速度上限'}).waitFor();
    await field('domain-combat', 'path').fill('8,1\n7,1\n6,1\n5,1\n4,1\n3,1');
    await submitCombat();
    assert.deepEqual(combatant(fighter).position, {x: 3, y: 1});
    assert.equal(sceneSecond(), beforeDropSecond + 24);
    assert.equal(combatant(fighter).movementSpentHalfFeet, explorationBudget.movementSpentHalfFeet);
    await page.locator('[data-pane="world"]').click();
    await form('domain-scene-leave-subjects').locator(`[data-character="${fighter}"] [name="join"]`).check();
    await action('domain', () => button('domain-scene-leave').click());
    assert.equal(domain().space.focus.space.kind, 'WORLD');
    assert.equal(domain().positions.find(position => position.characterId === fighter).space.id, world);
    await action('end', () => form('storage-end').click());
    owned = false;
    assert.deepEqual(pageErrors, []);
    console.log(JSON.stringify({result: 'PASSED', browser: browser.version(), crawlFeet, walkFeet,
      standFeet: 15, explorationDropSeconds: 0, explorationStepSeconds: 6, explorationCrawlMaxCells: 3,
      explorationWalkMaxCells: 6, duplicateRejected: true, excessCrawlRejected: true, proneLeaveRejected: true,
      reopenedAndContinued: true, explorationReopenedAndContinued: true, stoodAndLeft: true, viewportWidths: [1440, 390]}));
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
