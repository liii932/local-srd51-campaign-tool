#!/usr/bin/env node
'use strict';

// Run after browser-smoke.cjs, against its same disposable private test application.
// DND_BROWSER_EXECUTABLE optionally selects an existing absolute Chromium/Edge executable.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const HTTP_TIMEOUT = 120000;
const { observeStorageAction } = require('./browser-action.cjs');
const ORIGIN = 'http://127.0.0.1:8080';
const NAME = '装备与生命法术验收';
const KNOWN = new Set(['浏览器验收营地', '基础生命值验收', '完整领域浏览器验收', '奥术回想浏览器验收']);
const output = process.argv[2];
if (process.argv.length !== 3 || !path.isAbsolute(output || '')) throw new Error('Expected one absolute empty output directory');
const report = {result: 'FAILED', checks: [], screenshots: [], pageErrors: [], cspFailures: [], actions: []};

async function main() {
  console.log('Combat browser phase: opening the isolated workbench');
  await fs.mkdir(output, {recursive: true}); assert.equal((await fs.readdir(output)).length, 0);
  const launch = {headless: true}, executable = process.env.DND_BROWSER_EXECUTABLE;
  if (executable !== undefined) {
    assert.ok(path.isAbsolute(executable), 'DND_BROWSER_EXECUTABLE must be an absolute file path');
    assert.ok((await fs.stat(executable)).isFile(), 'DND_BROWSER_EXECUTABLE must identify an existing file');
    launch.executablePath = executable;
  }
  const browser = await chromium.launch(launch);
  report.browserVersion = browser.version();
  const context = await browser.newContext({viewport: {width: 1440, height: 1050}, locale: 'zh-CN'});
  const page = await context.newPage(); page.setDefaultTimeout(30000); page.setDefaultNavigationTimeout(HTTP_TIMEOUT);
  page.on('pageerror', error => report.pageErrors.push(error.message));
  page.on('console', event => { if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text())) report.cspFailures.push(event.text()); });
  const form = id => page.locator('#' + id);
  let owned = false, current = null, continuations = 0, passes = 0, closedWindowContinuations = 0;
  const card = name => current.campaign.domain.characters.find(character => character.name === name);
  const battle = () => current.campaign.domain.combats.at(-1).current;
  async function action(name, trigger) {
    const started = Date.now(), timing = {action: name, postResponseMs: null, stateResponseMs: null};
    report.actions.push(timing);
    try {
      const {body} = await observeStorageAction(page, name, trigger, timing, HTTP_TIMEOUT);
      current = body; return body;
    } catch (error) {
      timing.failedPhase = timing.postResponseMs === null ? `POST /${name}` : timing.stateRefreshRequired && timing.stateResponseMs === null ? 'GET /state' : 'response body or UI render';
      timing.error = error.message;
      throw new Error(`${name} (${timing.failedPhase}): ${error.message}`);
    } finally { timing.elapsedMs = Date.now() - started; }
  }

  async function chooseRule(selector, key) {
    const options = await page.locator(selector).locator('option').evaluateAll(options => options.map(option => option.value));
    const selected = options.find(value => value.endsWith('/' + key)); assert.ok(selected, `Missing selectable rule ${key}`);
    await page.locator(selector).selectOption(selected);
  }
  async function settle() {
    for (let step = 0; step < 16; step++) {
      const active = battle().flows.find(flow => flow.status === 'ACTIVE'); if (!active) return;
      const pass = form('domain-pending').getByRole('button', {name: '放弃反应', exact: true});
      const next = form('domain-pending').getByRole('button', {name: '继续结算行动', exact: true});
      if (await pass.count()) { passes++; await action('domain', () => pass.first().click()); }
      else if (await next.count()) {
        const leaf = active.frames.find(frame => frame.frameKey === active.leafFrameKey);
        const closed = active.windows.find(window => window.windowKey === leaf.windowKey && window.ownerFrameKey === leaf.frameKey && window.status === 'CLOSED');
        if (leaf.status === 'WAITING_WINDOW' && closed) closedWindowContinuations++;
        continuations++; await action('domain', () => next.first().click());
      }
      else throw new Error('An active flow has no current reaction or continuation control');
    }
    throw new Error('Flow did not finish within its bounded UI steps');
  }
  async function endUntil(id) {
    for (let step = 0; step < 8; step++) {
      if (battle().initiative[battle().activeOrdinal].characterId === id) return;
      await form('domain-combat').locator('[name="kind"]').selectOption('END_TURN');
      await action('domain', () => form('domain-combat').getByRole('button', {name: '执行战斗行动', exact: true}).click());
      await settle();
    }
    throw new Error('Requested actor did not receive a turn');
  }
  async function screenshot(filename, width, height) {
    await page.setViewportSize({width, height});
    const dimensions = await page.evaluate(() => [innerWidth, document.documentElement.scrollWidth, document.body.scrollWidth]);
    assert.ok(dimensions.slice(1).every(value => value <= dimensions[0]), `Horizontal overflow: ${dimensions}`);
    await page.screenshot({path: path.join(output, filename), fullPage: true}); report.screenshots.push(filename);
  }
  try {
    await page.goto(ORIGIN + '/host/storage', {waitUntil: 'networkidle'});
    await page.waitForFunction(() => document.querySelector('#storage-message').textContent.trim() === '创建、打开或恢复一个战役。');
    const response = await context.request.get(ORIGIN + '/api/host/storage/state', {timeout: HTTP_TIMEOUT}); assert.equal(response.status(), 200);
    const initial = await response.json(); assert.equal(initial.storage.state, 'IDLE'); assert.equal(initial.campaign, null);
    assert.equal(initial.storage.residualRunIds.length, 0);
    assert.ok(initial.savedCampaigns.every(save => KNOWN.has(save.name)), 'Refusing saves outside the primary smoke fixture whitelist');
    await page.locator('#storage-modules input[value="srd51-healing@1"]').check();
    await page.locator('#storage-modules input[value="srd51-runtime@1"]').check();
    await form('storage-create').locator('[name="name"]').fill(NAME);
    await action('create', () => form('storage-create').getByRole('button', {name: '创建战役', exact: true}).click()); owned = true;
    for (const [name, kind, characterClass] of [['治疗牧师', 'PC', 'class.cleric'], ['持剑战士', 'PC', 'class.fighter']]) {
      await form('domain-create').locator('[name="name"]').fill(name);
      await form('domain-create').locator('[name="kind"]').selectOption(kind);
      await chooseRule('#domain-create [name="classKey"]', characterClass);
      await action('domain', () => form('domain-create').getByRole('button', {name: '创建完整角色', exact: true}).click());
    }
    const clericId = card('治疗牧师').id, fighterId = card('持剑战士').id, targetId = clericId;
    console.log('Combat browser phase: cleric and fighter created at time zero');
    await page.locator('[data-pane="actions"]').click();
    await form('domain-equipment').locator('[name="characterId"]').selectOption(fighterId);
    await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_ACQUIRED');
    await chooseRule('#domain-equipment [name="item"]', 'item.dagger');
    await action('domain', () => form('domain-equipment').getByRole('button', {name: '应用装备操作', exact: true}).click());
    const sword = card('持剑战士').state.equipment.find(item => item.item.ruleKey === 'item.dagger'); assert.ok(sword);
    await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_EQUIPPED');
    await form('domain-equipment').locator('[name="itemId"]').selectOption(sword.itemId);
    await form('domain-equipment').locator('[name="location"]').selectOption('MAIN_HAND');
    await action('domain', () => form('domain-equipment').getByRole('button', {name: '应用装备操作', exact: true}).click());
    assert.equal(card('持剑战士').state.equipment.find(item => item.itemId === sword.itemId).location, 'MAIN_HAND');
    await form('domain-equipment').locator('[name="characterId"]').selectOption(clericId);
    await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_ACQUIRED');
    await chooseRule('#domain-equipment [name="item"]', 'item.mace');
    await action('domain', () => form('domain-equipment').getByRole('button', {name: '应用装备操作', exact: true}).click());
    const mace = card('治疗牧师').state.equipment.find(item => item.item.ruleKey === 'item.mace'); assert.ok(mace);
    await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_EQUIPPED');
    await form('domain-equipment').locator('[name="itemId"]').selectOption(mace.itemId);
    await action('domain', () => form('domain-equipment').getByRole('button', {name: '应用装备操作', exact: true}).click());
    await page.locator('[data-pane="magic"]').click();
    await form('domain-health').locator('[name="characterId"]').selectOption(fighterId);
    await form('domain-health').locator('[name="kind"]').selectOption('HEALTH_EFFECT_RESOLVED');
    await chooseRule('#domain-health [name="effect"]', 'vital_effect.dm_damage');
    await action('domain', () => form('domain-health').getByRole('button', {name: '应用健康操作', exact: true}).click());
    const wounded = card('持剑战士').hitPoints; assert.ok(wounded > 0 && wounded < card('持剑战士').maximumHitPoints);
    await form('domain-health').locator('[name="characterId"]').selectOption(clericId);
    await chooseRule('#domain-health [name="effect"]', 'vital_effect.dm_temporary');
    await form('domain-health').locator('[name="temporaryChoice"]').selectOption('REPLACE');
    await action('domain', () => form('domain-health').getByRole('button', {name: '应用健康操作', exact: true}).click());
    assert.equal(card('治疗牧师').state.health.temporaryHitPoints, 5);
    console.log('Combat browser phase: dagger/mace equipped; fighter HP6 and cleric tempHP5 recorded');
    await page.locator('[data-pane="combat"]').click();
    for (const row of await form('domain-deployments').locator('[data-character]').all()) {
      const id = await row.getAttribute('data-character');
      await row.locator('[name="side"]').fill(id === clericId ? '1' : '0');
      await row.locator('[name="x"]').fill(id === clericId ? '1' : '0');
      await row.locator('[name="y"]').fill('0');
    }
    await action('domain', () => form('domain-combat-start').getByRole('button', {name: '开始战斗', exact: true}).click());
    await endUntil(fighterId);
    for (const x of ['-1', '0']) {
      await form('domain-combat').locator('[name="kind"]').selectOption('MOVE');
      await form('domain-combat').locator('[name="x"]').fill(x); await form('domain-combat').locator('[name="y"]').fill('0');
      await action('domain', () => form('domain-combat').getByRole('button', {name: '执行战斗行动', exact: true}).click()); await settle();
    }
    assert.ok(continuations > 0, 'Leaving mace reach must expose a real continuation after PASS');
    assert.ok(passes > 0, 'The current opportunity must be explicitly passed through the UI');
    assert.ok(closedWindowContinuations > 0, 'A WAITING_WINDOW owner with its exact CLOSED window must continue successfully');
    report.checks.push('Real leave-reach movement exposed an opportunity window; UI explicitly passed and continued, then returned into melee');
    await form('domain-combat').locator('[name="kind"]').selectOption('BEGIN_ATTACK');
    await action('domain', () => form('domain-combat').getByRole('button', {name: '执行战斗行动', exact: true}).click());
    await form('domain-combat').locator('[name="kind"]').selectOption('ATTACK');
    await form('domain-combat').locator('[name="targetId"]').selectOption(targetId);
    await form('domain-combat').locator('[name="itemId"]').selectOption(sword.itemId);
    await action('domain', () => form('domain-combat').getByRole('button', {name: '执行战斗行动', exact: true}).click()); await settle();
    const attempt = battle().flows.flatMap(flow => flow.attacks).find(attack => attack.actorId === fighterId && attack.targetId === targetId); assert.ok(attempt);
    assert.equal(attempt.itemId, sword.itemId); assert.equal(attempt.status, 'RESOLVED');
    assert.equal(battle().combatants.find(person => person.characterId === fighterId).sequence.performed, 1);
    assert.ok(card('治疗牧师').hitPoints > 0);
    report.checks.push(`Equipped dagger attack accepted and resolved (${attempt.hit ? 'hit' : 'miss'}), with server-owned dice and one performed attack`);
    console.log('Combat browser phase: attack and any required continuation verified');
    await endUntil(clericId);
    const slot = () => card('治疗牧师').state.resources.find(resource => resource.identity.ruleKey === 'resource.cleric.slot1');
    const beforeSlots = slot().current, beforeHp = card('持剑战士').hitPoints;
    await form('domain-cast').locator('[name="actorId"]').selectOption(clericId);
    await chooseRule('#domain-cast [name="castingRule"]', 'spellcasting.life_domain');
    await chooseRule('#domain-cast [name="spell"]', 'spell.cure_wounds');
    await form('domain-cast').locator('[name="targets"]').selectOption(fighterId);
    await form('domain-cast').locator('[name="slotLevel"]').fill('1');
    await action('domain', () => form('domain-cast').getByRole('button', {name: '宣告施法', exact: true}).click()); await settle();
    assert.equal(slot().current, beforeSlots - 1);
    assert.ok(card('持剑战士').hitPoints > beforeHp && card('持剑战士').hitPoints <= card('持剑战士').maximumHitPoints);
    const cast = battle().flows.flatMap(flow => flow.casts).find(cast => cast.actorId === clericId && cast.spell.ruleKey === 'spell.cure_wounds'); assert.ok(cast);
    assert.equal(cast.status, 'RESOLVED'); assert.equal(cast.healingDice.length, 1); assert.ok(cast.healingDice.every(value => value >= 1 && value <= 8));
    assert.equal(beforeHp, 6); assert.equal(card('持剑战士').hitPoints, 12); assert.equal(beforeSlots, 2); assert.equal(slot().current, 1);
    assert.equal(cast.healingModifier, 5); assert.equal(cast.requestedHealing, cast.healingDice[0] + 5); assert.equal(cast.appliedHealing, 6);
    report.checks.push('Life cleric Cure Wounds consumed slots 2→1 and restored HP 6→12; its actual d8 and capped healing remain in the accepted flow');
    report.continuations = continuations;
    report.passes = passes; report.closedWindowContinuations = closedWindowContinuations;
    const healedHp = card('持剑战士').hitPoints, remainingSlots = slot().current, retainedCast = JSON.stringify(cast), retainedFlows = JSON.stringify(battle().flows);
    await page.setViewportSize({width: 1440, height: 1050});
    await form('domain-combat-summary').locator('.cast-result').last().scrollIntoViewIfNeeded();
    await page.screenshot({path: path.join(output, '11-casting-result-viewport.png'), fullPage: false}); report.screenshots.push('11-casting-result-viewport.png');
    await screenshot('09-equipment-attack-casting-wide.png', 1440, 1050); await screenshot('10-equipment-attack-casting-mobile.png', 390, 844);
    console.log('Combat browser phase: HP and slot readback verified; saving and reopening retained dice');
    const ended = await action('end', () => form('storage-end').click()); owned = false;
    await form('storage-files').selectOption(ended.savedFile);
    await action('open', () => form('storage-open').getByRole('button', {name: '打开战役', exact: true}).click()); owned = true;
    assert.equal(card('持剑战士').hitPoints, healedHp); assert.equal(slot().current, remainingSlots);
    assert.equal(JSON.stringify(battle().flows.flatMap(flow => flow.casts).find(item => item.actorId === clericId && item.spell.ruleKey === 'spell.cure_wounds')), retainedCast);
    assert.equal(JSON.stringify(battle().flows), retainedFlows);
    const history = current.campaign.domain.combats.at(-1);
    assert.ok(history.events.some(event => event.command.kind === 'PASS_REACTION'));
    assert.ok(history.events.some(event => event.command.kind === 'CONTINUE_MOVEMENT'));
    await action('end', () => form('storage-end').click()); owned = false;
    report.checks.push('Exact FINAL reopened with identical healing dice, HP and remaining spell slots');
    assert.deepEqual(report.pageErrors, []); assert.deepEqual(report.cspFailures, []); report.result = 'PASSED';
  } catch (error) {
    report.error = error.message; await page.screenshot({path: path.join(output, 'failure.png'), fullPage: true}).catch(() => {});
    if (owned && (await form('storage-campaign-name').textContent().catch(() => '')).trim() === NAME) {
      try { await action('end', () => form('storage-end').click()); report.cleanup = 'Owned fixture saved and ended through UI'; }
      catch (cleanup) { report.cleanup = cleanup.message; }
    }
    throw error;
  } finally { await browser.close(); await fs.writeFile(path.join(output, 'combat-browser-report.json'), JSON.stringify(report, null, 2) + '\n'); }
}
main().then(() => console.log(`Combat browser smoke passed: ${output}`)).catch(error => { console.error(error.message); process.exitCode = 1; });
