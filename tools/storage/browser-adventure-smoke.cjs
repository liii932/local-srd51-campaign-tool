#!/usr/bin/env node
'use strict';

// Actual UI writes and production randomness against an explicitly disposable, fresh root.
// Start run-local.sh separately; this script neither starts services nor removes storage files.
// Usage: node tools/storage/browser-adventure-smoke.cjs /absolute/empty/output --fresh-root /absolute/temporary/storage-root
// NODE_PATH supplies external Playwright. DND_BROWSER_EXECUTABLE optionally selects native Chromium/Edge.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady} = require('./browser-action.cjs');
const {selectStandardRules} = require('./browser-rule-library.cjs');

const {focusSpace, placeAtFocus, beginBattle} = require('./browser-spaces.cjs');

const ORIGIN = 'http://127.0.0.1:8080', HTTP_TIMEOUT = 120000;
const NAME = '石桥巡查短冒险', PC = '巡桥者艾拉', NPC = '守桥人洛恩';
const [output, acknowledgement, storageRoot] = process.argv.slice(2);
assert.ok(process.argv.length === 5 && acknowledgement === '--fresh-root'
  && path.isAbsolute(output || '') && path.isAbsolute(storageRoot || ''),
'Usage: node browser-adventure-smoke.cjs /absolute/empty/output --fresh-root /absolute/temporary/storage-root');
const report = {schema: 1, origin: ORIGIN, result: 'FAILED', checks: [], stages: [], actions: [],
  screenshots: [], layouts: [], pageErrors: [], cspFailures: [], busyFailures: [], busyChecks: 0};
const json = value => JSON.stringify(value, null, 2) + '\n';
const within = (parent, child) => {
  const relative = path.relative(parent, child);
  return relative === '' || relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
};
const facts = ({epoch, revision, lastCommand, ...campaign}) => campaign;
const filePayload = ({fileKey, integrity, ...payload}) => payload;
let outputReady = false, browser, context, page, current, phase = 'fresh-root';

async function main() {
  const root = await fs.realpath(storageRoot), evidence = path.resolve(output);
  assert.equal((await fs.lstat(storageRoot)).isSymbolicLink(), false, 'The disposable storage root must be a real directory');
  assert.ok(!within(path.resolve(__dirname, '../..'), root), 'Storage must be outside the repository');
  assert.ok(!within(root, evidence) && !within(evidence, root), 'Evidence and managed storage must be separate');
  assert.deepEqual((await fs.readdir(root)).sort(), ['instance.lock', 'runs', 'saves'], 'Refusing an unrecognized storage root');
  for (const directory of ['runs', 'saves']) {
    assert.equal((await fs.lstat(path.join(root, directory))).isSymbolicLink(), false);
    assert.deepEqual(await fs.readdir(path.join(root, directory)), [], `Fresh ${directory} must be empty`);
  }
  await fs.mkdir(output, {recursive: true});
  assert.deepEqual(await fs.readdir(output), [], 'Evidence directory must be empty');
  outputReady = true;
  report.storageRoot = root;
  const launch = {headless: true}, executable = process.env.DND_BROWSER_EXECUTABLE;
  if (executable !== undefined) {
    assert.ok(path.isAbsolute(executable), 'DND_BROWSER_EXECUTABLE must be absolute');
    assert.ok((await fs.stat(executable)).isFile(), 'DND_BROWSER_EXECUTABLE must identify a file');
    launch.executablePath = executable;
  }
  browser = await chromium.launch(launch); report.browserVersion = browser.version();

  async function openBrowserContext() {
    context = await browser.newContext({viewport: {width: 1440, height: 1050}, locale: 'zh-CN'});
    page = await context.newPage(); page.setDefaultTimeout(30000); page.setDefaultNavigationTimeout(HTTP_TIMEOUT);
    page.on('pageerror', error => report.pageErrors.push(error.message));
    page.on('console', event => {
      if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text())) report.cspFailures.push(event.text());
    });
    page.on('requestfailed', request => {
      if (/CSP|BLOCKED_BY/i.test(request.failure()?.errorText || '')) report.cspFailures.push(request.url());
    });
    // Observe feedback before allowing the real request through, without changing its body or response.
    await page.route(`${ORIGIN}/api/host/storage/*`, async route => {
      const request = route.request();
      const ruleCommand = new URL(request.url()).pathname.startsWith('/api/host/storage/rule-');
      if (request.method() === 'POST' && !ruleCommand) {
        try {
          const busy = await page.evaluate(() => {
            const workspace = document.querySelector('#storage-workspace'), message = document.querySelector('#storage-message');
            return {disabled: workspace?.disabled, ariaBusy: workspace?.getAttribute('aria-busy'),
              feedback: message?.classList.contains('is-busy'), text: message?.textContent.trim(),
              live: message?.getAttribute('aria-live'), outside: !workspace?.contains(message)};
          });
          assert.equal(busy.disabled, true); assert.equal(busy.ariaBusy, 'true');
          assert.equal(busy.feedback, true); assert.equal(busy.outside, true);
          assert.equal(busy.live, 'polite'); assert.ok(busy.text?.length > 0);
          report.busyChecks++;
        } catch (failure) { report.busyFailures.push(failure.message); }
      }
      await route.continue();
    });
    const response = page.waitForResponse(response => response.request().method() === 'GET'
      && response.url() === `${ORIGIN}/api/host/storage/state`, {timeout: HTTP_TIMEOUT});
    await page.goto(ORIGIN + '/host/storage', {waitUntil: 'domcontentloaded'});
    const loaded = await response; assert.equal(loaded.status(), 200); current = await loaded.json();
    await waitForStorageReady(page);
  }
  const form = id => page.locator('#' + id);
  const card = name => current.campaign.domain.characters.find(character => character.name === name);
  const battle = () => current.campaign.domain.combats.at(-1).current;
  const pane = name => page.locator(`[data-pane="${name}"]`).click();
  const acceptedFiles = new Map();
  async function captureSavedFiles(expectedAdded, initialized = false) {
    const keys = [...current.storage.saveFiles].sort();
    assert.deepEqual(current.savedCampaigns.map(file => file.fileKey).sort(), keys);
    assert.deepEqual((await fs.readdir(path.join(root, 'saves'))).sort(), keys.map(key => key + '.json'),
      'Reported complete saves must match the exact files in the supplied root');
    for (const [key, bytes] of acceptedFiles) {
      assert.ok(keys.includes(key), `Previously accepted save ${key} disappeared`);
      assert.deepEqual(await fs.readFile(path.join(root, 'saves', key + '.json')), bytes,
        `Previously accepted save ${key} changed`);
    }
    const added = keys.filter(key => !acceptedFiles.has(key));
    if (initialized) {
      // initialize() publishes one INITIAL. Its identity comes from the actual response set difference.
      assert.equal(added.length, 1, 'A new run must publish its own distinct INITIAL save');
      const input = JSON.parse(await fs.readFile(path.join(root, 'runs', current.storage.runId, 'input.json')));
      for (const key of added) {
        const saved = JSON.parse(await fs.readFile(path.join(root, 'saves', key + '.json')));
        assert.deepEqual(filePayload(saved), filePayload(input), 'INITIAL must preserve the exact new run input');
      }
    } else assert.deepEqual(added, [...expectedAdded].sort(), 'Only the exact accepted response save may be added');
    for (const key of added) acceptedFiles.set(key, await fs.readFile(path.join(root, 'saves', key + '.json')));
    return added;
  }
  async function stage(name, task) {
    phase = name; const started = Date.now();
    try { await task(); } finally {
      const elapsedMs = Date.now() - started; report.stages.push({stage: name, elapsedMs});
      console.log(`Adventure: ${name} (${elapsedMs} ms)`);
    }
  }
  async function action(name, trigger) {
    const started = Date.now(), timing = {stage: phase, action: name, postResponseMs: null, stateResponseMs: null};
    report.actions.push(timing);
    try {
      const accepted = await observeStorageAction(page, name, trigger, timing, HTTP_TIMEOUT, value => { timing.phase = value; });
      assert.deepEqual(report.busyFailures, [], 'A command lacked visible, accessible busy feedback');
      current = accepted.body;
      if (name === 'end') {
        assert.ok(current.savedFile);
        assert.equal(await form('storage-files').inputValue(), current.savedFile, 'END must select its exact FINAL, even with earlier manual saves');
      }
      return accepted;
    } catch (failure) { timing.error = failure.message; throw failure; }
    finally { timing.elapsedMs = Date.now() - started; }
  }
  const submit = (id, label) => action('domain', () => form(id).getByRole('button', {name: label, exact: true}).click());
  const actionBody = async (name, trigger) => (await action(name, trigger)).body;
  async function rule(selector, key) {
    const value = await page.locator(selector).locator('option').evaluateAll((options, key) =>
      options.find(option => option.value.endsWith('/' + key))?.value, key);
    assert.ok(value, `Missing actual rule ${key}`); await page.locator(selector).selectOption(value);
  }
  async function screenshot(name, width, height) {
    await page.setViewportSize({width, height});
    const layout = await page.evaluate(() => ({width: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth}));
    report.layouts.push({...layout, screenshot: name});
    assert.ok(layout.document <= width && layout.body <= width, `Horizontal overflow at ${width}px: ${JSON.stringify(layout)}`);
    await page.screenshot({path: path.join(output, name), fullPage: true}); report.screenshots.push(name);
  }
  async function combatAction(kind, fields = {}) {
    await form('domain-combat').locator('[name="kind"]').selectOption(kind);
    for (const [name, value] of Object.entries(fields)) {
      const input = form('domain-combat').locator(`[name="${name}"]`);
      if (['targetId', 'itemId'].includes(name)) await input.selectOption(String(value)); else await input.fill(String(value));
    }
    return submit('domain-combat', '执行战斗行动');
  }
  async function settle() {
    for (let step = 0; step < 16; step++) {
      if (!battle().flows.some(flow => flow.status === 'ACTIVE')) return;
      const pass = form('domain-pending').getByRole('button', {name: '放弃反应', exact: true});
      const next = form('domain-pending').getByRole('button', {name: '继续结算行动', exact: true});
      if (await pass.count()) { report.reactionsPassed = (report.reactionsPassed || 0) + 1; await action('domain', () => pass.first().click()); }
      else if (await next.count()) { report.continuations = (report.continuations || 0) + 1; await action('domain', () => next.first().click()); }
      else throw new Error('An active action has no visible reaction/continuation');
    }
    throw new Error('Pending action exceeded the bounded UI continuation steps');
  }

  let pcId, npcId, campId, towerId, retained, finalFile, manualBytes, finalBytes, initialCards;
  try {
    await stage('open and create campaign', async () => {
      await openBrowserContext();
      assert.equal(current.status, 'OK'); assert.equal(current.storage.state, 'IDLE'); assert.equal(current.campaign, null);
      assert.equal(current.storage.runId, ''); assert.deepEqual(current.storage.residualRunIds, []);
      assert.deepEqual(current.storage.saveFiles, []); assert.deepEqual(current.savedCampaigns, []);
      assert.equal(await form('storage-files').locator('option').count(), 0);
      assert.equal(await form('storage-residuals').locator('option').count(), 0);
      await selectStandardRules(page);
      await form('storage-create').locator('[name="name"]').fill(NAME);
      await action('create', () => form('storage-create').getByRole('button', {name: '创建战役', exact: true}).click());
      assert.equal(current.storage.state, 'ACTIVE'); assert.equal(current.campaign.name, NAME);
      assert.deepEqual(await fs.readdir(path.join(root, 'runs')), [current.storage.runId], 'The browser must target the explicitly supplied disposable root');
      report.initialSaves = {create: await captureSavedFiles(null, true)};
      report.campaignId = current.campaign.campaignId;
      report.checks.push('Explicit fresh temporary root, IDLE state and exact created run verified before proceeding');
    });
    await stage('create PC and NPC and equip weapons', async () => {
      for (const [name, kind] of [[PC, 'PC'], [NPC, 'NPC']]) {
        await form('domain-create').locator('[name="name"]').fill(name);
        await form('domain-create').locator('[name="kind"]').selectOption(kind);
        await rule('#domain-create [name="classKey"]', 'class.fighter');
        await submit('domain-create', '创建完整角色');
      }
      pcId = card(PC).id; npcId = card(NPC).id;
      initialCards = current.campaign.domain.characters.map(character => ({id: character.id, initial: character.initial}));
      assert.equal(card(PC).kind, 'PC'); assert.equal(card(NPC).kind, 'NPC');
      await pane('actions');
      for (const name of [PC, NPC]) {
        await form('domain-equipment').locator('[name="characterId"]').selectOption(card(name).id);
        await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_ACQUIRED');
        await rule('#domain-equipment [name="item"]', 'item.dagger');
        await submit('domain-equipment', '应用装备操作');
        const weapon = card(name).state.equipment.find(item => item.item.ruleKey === 'item.dagger'); assert.ok(weapon);
        await form('domain-equipment').locator('[name="kind"]').selectOption('ITEM_EQUIPPED');
        await form('domain-equipment').locator('[name="itemId"]').selectOption(weapon.itemId);
        await form('domain-equipment').locator('[name="location"]').selectOption('MAIN_HAND');
        await submit('domain-equipment', '应用装备操作');
        assert.equal(card(name).state.equipment.find(item => item.itemId === weapon.itemId).location, 'MAIN_HAND');
      }
      report.checks.push('Real forms created distinct PC/NPC and acquired/equipped their actual catalog weapons');
    });
    await stage('explore connected locations and resolve a check', async () => {
      await pane('world');
      for (const name of ['石桥营地', '桥后哨所']) {
        await form('domain-node').locator('[name="name"]').fill(name); await submit('domain-node', '建立地点');
      }
      campId = current.campaign.domain.nodes.find(node => node.name === '石桥营地').id;
      towerId = current.campaign.domain.nodes.find(node => node.name === '桥后哨所').id;
      await focusSpace(page, actionBody, 'WORLD', campId);
      await form('domain-link').locator('[name="from"]').selectOption(campId);
      await form('domain-link').locator('[name="to"]').selectOption(towerId); await submit('domain-link', '建立有向连接');
      assert.ok(current.campaign.domain.nodes.find(node => node.id === campId).adjacent.includes(towerId));
      await placeAtFocus(page, actionBody, [pcId, npcId]);
      await form('domain-item').locator('[name="characterId"]').selectOption(pcId);
      await form('domain-item').locator('[name="name"]').fill('巡桥火把');
      await form('domain-item').locator('[name="quantity"]').fill('2'); await submit('domain-item', '保存物品记录');
      await pane('actions');
      await form('domain-check').locator('[name="characterId"]').selectOption(pcId);
      await form('domain-check').locator('[name="kind"]').selectOption('ABILITY');
      await form('domain-check').locator('[name="ability"]').selectOption('ability.wisdom');
      await form('domain-check').locator('[name="difficultyClass"]').fill('12');
      await form('check-with-effects').check();
      for (const branch of ['success', 'failure']) {
        await form('domain-check').locator(`[name="${branch}Target"]`).selectOption(pcId);
        await form('domain-check').locator(`[name="${branch}Message"]`).fill(branch === 'success' ? '发现桥后的脚印，准备前往哨所。' : '没有辨清脚印，留在营地警戒。');
      }
      await submit('domain-check', '服务器掷骰检定');
      const check = card(PC).history.events.at(-1).checkResult; assert.ok(check);
      assert.ok(check.firstD20 >= 1 && check.firstD20 <= 20); assert.equal(check.secondD20, null);
      assert.equal(check.total, check.selectedD20 + check.abilityModifier + check.proficiencyBonus);
      assert.equal(check.success, check.total >= 12);
      assert.equal(current.campaign.domain.positions.find(position => position.characterId === pcId).space.id, campId);
      report.checkResult = check;
      assert.match(await form('storage-history').innerText(), check.success ? /发现桥后的脚印/ : /没有辨清脚印/);
      await pane('world');
      await form('domain-travel').locator('[name="targetNodeId"]').selectOption(towerId);
      for (const id of [pcId, npcId])
        await form('domain-travel-subjects').locator(`[data-character="${id}"] [name="join"]`).check();
      await form('domain-travel').locator('[name="minutes"]').fill('2');
      await form('domain-travel').locator('[name="adjudication"]').fill('沿桥面巡查抵达哨所');
      await submit('domain-travel', '沿路线转移');
      const spaces = current.campaign.domain.space;
      assert.equal(spaces.focus.space.id, towerId);
      assert.equal(Number(spaces.clocks.find(clock => clock.space.id === campId).second), 120);
      assert.equal(Number(spaces.clocks.find(clock => clock.space.id === towerId).second), 0);
      assert.ok(current.campaign.domain.positions.every(position => position.space.id === towerId));
      await screenshot('01-exploration-wide.png', 1440, 1050); await screenshot('02-exploration-mobile.png', 390, 844);
      report.checks.push('Two actual connected locations, positions and inventory; real DC12 roll applied only its declared result branch');
    });
    await stage('fight, pass a reaction and resolve an attack', async () => {
      await page.setViewportSize({width: 1440, height: 1050}); await pane('combat');
      await beginBattle(page, actionBody, [[pcId, 1, 0, 0], [npcId, 2, 0, 1]]);
      assert.equal(battle().initiative.length, 2);
      assert.deepEqual(battle().initiative.map(turn => turn.characterId).sort(), [pcId, npcId].sort());
      const initialRolls = current.campaign.domain.combats.at(-1).events.at(-1).resolution.rolls;
      for (const [ordinal, turn] of battle().initiative.entries()) {
        assert.equal(turn.ordinal, ordinal); assert.ok(turn.d20 >= 1 && turn.d20 <= 20);
        assert.equal(turn.total, turn.d20 + turn.dexterityModifier);
        const die = initialRolls.find(roll => roll.purpose === 'INITIATIVE' && roll.subjectId === turn.characterId);
        assert.ok(die); assert.equal(die.sides, 20); assert.equal(die.value, turn.d20);
      }
      const actorId = battle().initiative[battle().activeOrdinal].characterId;
      const targetId = actorId === pcId ? npcId : pcId;
      const x = battle().combatants.find(actor => actor.characterId === actorId).position.x;
      const weapon = current.campaign.domain.characters.find(character => character.id === actorId).state.equipment.find(item => item.location === 'MAIN_HAND');
      await combatAction('MOVE', {x: x === 1 ? 0 : 3, y: 0}); await settle();
      assert.ok(report.reactionsPassed > 0); assert.ok(report.continuations > 0);
      await combatAction('MOVE', {x, y: 0}); await settle();
      await combatAction('BEGIN_ATTACK');
      const targetHp = current.campaign.domain.characters.find(character => character.id === targetId).hitPoints;
      await combatAction('ATTACK', {targetId, itemId: weapon.itemId}); await settle();
      const attack = battle().flows.flatMap(flow => flow.attacks).find(attack => attack.actorId === actorId && attack.targetId === targetId);
      assert.ok(attack); assert.equal(attack.status, 'RESOLVED'); assert.equal(attack.itemId, weapon.itemId);
      assert.ok(attack.firstD20 >= 1 && attack.firstD20 <= 20);
      assert.equal(attack.selectedD20, attack.secondD20 === null ? attack.firstD20
        : attack.advantage ? Math.max(attack.firstD20, attack.secondD20) : Math.min(attack.firstD20, attack.secondD20));
      assert.equal(attack.hit, attack.selectedD20 === 20 || attack.selectedD20 !== 1
        && attack.selectedD20 + attack.abilityModifier + attack.proficiencyBonus + (attack.blessingD4 || 0) >= attack.finalArmorClass);
      if (!attack.hit) { assert.deepEqual(attack.damageDice, []); assert.equal(attack.damageAmount, 0); }
      else {
        assert.equal(attack.damageDice.length, attack.critical ? 2 : 1);
        assert.ok(attack.damageDice.every(die => die >= 1 && die <= 4));
        assert.equal(attack.damageAmount, Math.max(0, attack.damageDice.reduce((sum, die) => sum + die, 0) + attack.abilityModifier));
      }
      const targetAfter = current.campaign.domain.characters.find(character => character.id === targetId);
      assert.equal(targetAfter.hitPoints, Math.max(0, targetHp - attack.damageAmount));
      assert.ok(targetAfter.hitPoints > 0, 'The short dagger exchange must leave both actors able to continue');
      report.attack = attack;
      await screenshot('03-combat-wide.png', 1440, 1050); await screenshot('04-combat-mobile.png', 390, 844);
      report.checks.push('Server initiative selected the actual first actor; leave-reach MOVE exposed PASS/continue, then a real weapon attack resolved');
    });
    await stage('save and end, then close the browser context', async () => {
      retained = facts(current.campaign);
      assert.deepEqual(current.campaign.domain.characters.map(character => ({id: character.id, initial: character.initial})), initialCards);
      const saved = await action('save', () => form('storage-save').click()); assert.ok(saved.body.savedFile);
      await captureSavedFiles([saved.body.savedFile]);
      manualBytes = await fs.readFile(path.join(root, 'saves', saved.body.savedFile + '.json'));
      report.manualSave = saved.body.savedFile;
      const ended = await action('end', () => form('storage-end').click()); finalFile = ended.body.savedFile;
      await captureSavedFiles([finalFile]);
      assert.notEqual(finalFile, report.manualSave); assert.equal(current.storage.state, 'IDLE'); assert.equal(current.campaign, null);
      assert.deepEqual(await fs.readdir(path.join(root, 'runs')), []);
      finalBytes = await fs.readFile(path.join(root, 'saves', finalFile + '.json'));
      assert.deepEqual(filePayload(JSON.parse(finalBytes)), filePayload(JSON.parse(manualBytes)), 'MANUAL and FINAL must contain the same complete facts');
      await fs.writeFile(path.join(output, 'adventure-final.json'), finalBytes, {flag: 'wx'});
      report.savedFilesBeforeClose = [...acceptedFiles.keys()].sort();
      await context.close(); report.contextClosedAfterEnd = true;
      report.checks.push('Manual save and END published distinct exact complete files without changing facts; END selected its FINAL and removed the run');
    });
    await stage('reopen exact FINAL with all original facts and dice', async () => {
      await openBrowserContext(); assert.equal(current.storage.state, 'IDLE');
      await captureSavedFiles([]);
      await form('storage-files').selectOption(finalFile);
      const opened = await action('open', () => form('storage-open').getByRole('button', {name: '打开战役', exact: true}).click());
      assert.equal(opened.request.get('fileKey'), finalFile); assert.equal(current.campaign.revision, 0);
      report.initialSaves.open = await captureSavedFiles(null, true);
      assert.deepEqual(facts(current.campaign), retained, 'Every initial/current character, rule, item, position, root, combat flow, original die and history must survive reopening');
      await pane('combat');
      assert.match(await form('storage-characters').innerText(), new RegExp(PC));
      assert.match(await form('storage-characters').innerText(), new RegExp(NPC));
      await screenshot('05-reopened-wide.png', 1440, 1050); await screenshot('06-reopened-mobile.png', 390, 844);
      report.reopenedFinal = finalFile;
      report.checks.push('A new browser context selected the exact END file and reopened all initial/current/domain/history facts without rerolling or replaying');
    });
    await stage('continue one real action and end the owned fixture', async () => {
      const oldHistory = current.campaign.history.length, oldRoots = current.campaign.domain.roots.length;
      const oldCombatEvents = current.campaign.domain.combats.at(-1).events.length;
      await combatAction('END_TURN'); await settle();
      assert.equal(current.campaign.revision, 1); assert.equal(current.campaign.history.length, oldHistory + 1);
      assert.equal(current.campaign.domain.roots.length, oldRoots + 1);
      assert.equal(current.campaign.domain.combats.at(-1).events.length, oldCombatEvents + 1);
      assert.deepEqual(current.campaign.domain.combats.at(-1).current.flows, retained.domain.combats.at(-1).current.flows,
        'Continuing a turn must retain the existing original action/dice');
      await action('end', () => form('storage-end').click());
      await captureSavedFiles([current.savedFile]);
      assert.equal(current.storage.state, 'IDLE'); assert.equal(current.campaign, null);
      assert.deepEqual(current.storage.residualRunIds, []); assert.deepEqual(await fs.readdir(path.join(root, 'runs')), []);
      report.finalSave = current.savedFile; report.savedFiles = current.storage.saveFiles;
      assert.deepEqual([...current.storage.saveFiles].sort(), [...acceptedFiles.keys()].sort());
      report.checks.push('A continued UI turn appended exactly one root/history event; final END left IDLE with no residual run and preserved earlier save bytes');
    });
    assert.equal(report.busyChecks, report.actions.length, 'Every campaign POST must expose busy feedback');
    assert.deepEqual(report.pageErrors, []); assert.deepEqual(report.cspFailures, []); assert.deepEqual(report.busyFailures, []);
    report.result = 'PASSED';
  } catch (failure) {
    report.error = failure.message; report.failingPhase = phase;
    if (page && !page.isClosed()) await page.screenshot({path: path.join(output, 'failure.png'), fullPage: true}).catch(() => {});
    throw failure;
  } finally {
    await browser.close();
    await fs.writeFile(path.join(output, 'adventure-browser-report.json'), json(report), {flag: 'wx'});
  }
  console.log(JSON.stringify({result: report.result, actions: report.actions.length, checks: report.checks.length,
    screenshots: report.screenshots.length, evidence: path.join(output, 'adventure-browser-report.json')}));
}
main().catch(async failure => {
  report.error ||= failure.message;
  if (outputReady) await fs.writeFile(path.join(output, 'adventure-browser-report.json'), json(report), {flag: 'wx'}).catch(() => {});
  if (browser?.isConnected()) await browser.close().catch(() => {});
  console.error(failure.message); process.exitCode = 1;
});
