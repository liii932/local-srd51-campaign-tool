#!/usr/bin/env node
'use strict';

// Run against the isolated launcher or native Windows external Tomcat, using a disposable, newly created private root.
// Fresh mode refuses existing saves/runs; explicit resume mode verifies only its retained Arcane fixture.
// NODE_PATH supplies an external Playwright installation; no browser packages enter the WAR.
// DND_BROWSER_EXECUTABLE optionally selects an existing absolute Chromium/Edge executable.
// Usage: node tools/storage/browser-smoke.cjs /absolute/empty/screenshot-directory
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');
const { createHash } = require('node:crypto');
const { isDeepStrictEqual } = require('node:util');
const HTTP_TIMEOUT = 120000;
const { observeStorageAction, waitForStorageReady, waitForExactText, waitForHistoryCount } = require('./browser-action.cjs');
const { selectStandardRules } = require('./browser-rule-library.cjs');

const {createNode, focusSpace, placeAtFocus, createScene, enterScene, beginBattle} = require('./browser-spaces.cjs');

const ORIGIN = 'http://127.0.0.1:8080';
const CAMPAIGN = '浏览器验收营地';
const FULL_CAMPAIGN = '完整领域浏览器验收';
const ARCANE_CAMPAIGN = '奥术回想浏览器验收';
const output = process.argv[2];
const resume = process.argv[3] === '--resume-arcane';
const resumeRun = process.argv[4], resumeInput = process.argv[5];
if ((!resume && process.argv.length !== 3) || (resume && (process.argv.length !== 6 || !/^[0-9a-f-]{36}$/.test(resumeRun || '') || !path.isAbsolute(resumeInput || ''))) || !path.isAbsolute(output || '')) {
  console.error('Usage: node tools/storage/browser-smoke.cjs /absolute/empty/screenshot-directory');
  console.error('Resume: node tools/storage/browser-smoke.cjs /absolute/empty/output --resume-arcane exact-run-id /absolute/storage/runs/exact-run-id/input.json');
  console.error('Target must be an isolated launcher or native Windows external Tomcat using a disposable, newly created root.');
  process.exit(2);
}

const report = { origin: ORIGIN, mode: resume ? 'RESUME_ARCANE_AND_BASE_TAIL' : 'FRESH_FULL', result: 'FAILED', checks: [], screenshots: [], pageErrors: [], cspFailures: [], actions: [] };

async function main() {
  console.log('Browser phase: opening the isolated campaign workbench');
  await fs.mkdir(output, { recursive: true });
  assert.equal((await fs.readdir(output)).length, 0, 'Screenshot output directory must be empty');
  const launch = { headless: true }, executable = process.env.DND_BROWSER_EXECUTABLE;
  if (executable !== undefined) {
    assert.ok(path.isAbsolute(executable), 'DND_BROWSER_EXECUTABLE must be an absolute file path');
    assert.ok((await fs.stat(executable)).isFile(), 'DND_BROWSER_EXECUTABLE must identify an existing file');
    launch.executablePath = executable;
  }
  const browser = await chromium.launch(launch);
  report.browserVersion = browser.version();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, locale: 'zh-CN' });
  const page = await context.newPage();
  page.setDefaultTimeout(30000);
  page.setDefaultNavigationTimeout(HTTP_TIMEOUT);
  let disposableConfirmed = false;
  let ownsActiveFixture = false;
  let originalSaves = null, saveDirectory = null;

  page.on('pageerror', error => report.pageErrors.push(error.message));
  page.on('console', event => {
    if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text()))
      report.cspFailures.push(event.text());
  });
  page.on('requestfailed', request => {
    const failure = request.failure()?.errorText || '';
    if (/CSP|BLOCKED_BY/i.test(failure)) report.cspFailures.push(`${request.url()}: ${failure}`);
  });

  const form = id => page.locator(`#${id}`);
  const visible = async selector => { await page.locator(selector).waitFor({ state: 'visible' }); };
  const equalText = (selector, expected) => waitForExactText(page, selector, expected);
  const historyCount = expected => waitForHistoryCount(page, expected);

  async function action(name, trigger) {
    const started = Date.now(), timing = {action: name, postResponseMs: null, stateResponseMs: null};
    report.actions.push(timing);
    try {
      const {body} = await observeStorageAction(page, name, trigger, timing, HTTP_TIMEOUT);
      return body;
    } catch (error) {
      timing.failedPhase = timing.postResponseMs === null ? `POST /${name}` : timing.stateRefreshRequired && timing.stateResponseMs === null ? 'GET /state' : 'response body or UI render';
      timing.error = error.message;
      throw new Error(`${name} (${timing.failedPhase}): ${error.message}`);
    } finally { timing.elapsedMs = Date.now() - started; }
  }

  async function screenshot(filename, width, height) {
    await page.setViewportSize({ width, height });
    const overflow = await page.evaluate(() => ({ viewport: window.innerWidth,
      document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
    assert.ok(overflow.document <= overflow.viewport && overflow.body <= overflow.viewport,
      `${width}px layout has horizontal overflow: ${JSON.stringify(overflow)}`);
    await page.screenshot({ path: path.join(output, filename), fullPage: true });
    report.screenshots.push(filename);
    report.checks.push(`No horizontal overflow at ${width}px`);
  }

  async function createFreeCard(name, maximum, current) {
    await page.locator('[data-pane="manual"]').click();
    await form('manual-create').locator('[name="name"]').fill(name);
    await form('manual-create').locator('[name="template"]').selectOption('blank');
    await action('domain', () => form('manual-create').getByRole('button', {name: '创建自由卡', exact: true}).click());
    for (const [field, value] of [['hp.maximum', maximum], ['hp.current', current]]) {
      const row = page.locator(`#manual-card-editor [data-manual-target="${field}"]`);
      await row.locator('input').fill(String(value));
      await action('domain', () => row.getByRole('button', {name: '保存', exact: true}).click());
    }
    await equalText('#manual-cards .hp', `${current} / ${maximum}`);
  }

  async function endFixture() {
    const ended = await action('end', () => form('storage-end').click());
    await visible('#storage-idle');
    assert.equal(await form('storage-active').isVisible(), false);
    ownsActiveFixture = false;
    assert.ok(ended.savedFile, 'Ending must return the exact accepted final save');
    return ended.savedFile;
  }

  async function currentCampaign() {
    const response = await page.request.get(`${ORIGIN}/api/host/storage/state`, {timeout: HTTP_TIMEOUT});
    assert.equal(response.status(), 200);
    return (await response.json()).campaign;
  }

  async function rejectRecovery(expectedChoices) {
    const before = await currentCampaign();
    const responses = Promise.all([
      page.waitForResponse(response => response.request().method() === 'POST' && response.url() === `${ORIGIN}/api/host/storage/domain`, {timeout: HTTP_TIMEOUT}),
      page.waitForResponse(response => response.request().method() === 'GET' && response.url() === `${ORIGIN}/api/host/storage/state`, {timeout: HTTP_TIMEOUT})
    ]);
    await form('domain-rest').getByRole('button', { name: '应用休息操作', exact: true }).click();
    const [response, refreshed] = await responses;
    assert.equal(response.status(), 409, 'An invalid recovery must be rejected by the server');
    assert.equal((await response.json()).code, 'DOMAIN_REJECTED');
    const payload = JSON.parse(new URLSearchParams(response.request().postData()).get('payload'));
    assert.deepEqual(payload.command.restInput.recoveredSpellSlots, expectedChoices, 'The real form must submit selected ranks/counts');
    assert.equal(refreshed.status(), 200); await refreshed.finished();
    await waitForStorageReady(page);
    await page.locator('#storage-message').filter({hasText: /^\s*此操作不满足当前规则或状态，请检查角色、装备、轮次与待处理行动。/}).waitFor();
    const after = await currentCampaign();
    assert.deepEqual(after.domain, before.domain, 'Rejected recovery must preserve slots, day, roots and character history');
    assert.deepEqual(after.history, before.history); assert.equal(after.revision, before.revision);
  }


  // The retained file is a typed campaign document; Host views use ordinary JSON records.
  // Convert only integer fields and maps declared by these exact fixture record types.
  const integerFields = {
    'character.State': ['level', 'proficiencyBonus', 'hitPoints', 'maximumHitPoints', 'version'],
    'character.ClassLevel': ['level'], 'character.FeatureState': ['acquiredAtClassLevel'],
    'character.ProficiencyState': ['acquiredAtTotalLevel'], 'character.ResourceState': ['current', 'maximum'],
    'character.HitDicePool': ['sides', 'current', 'maximum'],
    'character.HealthState': ['temporaryHitPoints', 'deathSuccesses', 'deathFailures', 'stableRecoveryMinute', 'stableRecoverySecond'],
    'character.RestState': ['minute', 'second', 'lastLongRestEndMinute', 'lastLongRestEndSecond'],
    'character.RestAttempt': ['startMinute', 'hitPointsAtStart', 'sleepMinutes', 'lightMinutes', 'strenuousMinutes', 'consecutiveStrenuousMinutes'],
    'character.SpellbookState': ['learnedThroughLevel', 'preparedAtRestMinute', 'preparedAtRestSecond', 'lastArcaneRecoveryDay'],
    'character.LearnedSpell': ['acquiredAtClassLevel'], 'campaign.Root': ['sequence'],
    'rule.RuleIdentity': [], 'rule.EntryIdentity': [], 'preparation-identity': []
  };
  const integer = value => {
    if (value === null) return null;
    assert.equal(typeof value, 'string'); assert.match(value, /^(0|-?[1-9][0-9]*)$/);
    const number = Number(value); assert.ok(Number.isSafeInteger(number)); return number;
  };
  function typed(value) {
    if (value === null || typeof value !== 'object') return value;
    if (Array.isArray(value)) return value.map(typed);
    assert.ok(Object.hasOwn(integerFields, value.type), `Unexpected retained record ${value.type}`);
    if (value.type === 'rule.RuleIdentity') assert.deepEqual(Object.keys(value).sort(), ['moduleKey', 'ruleKey', 'ruleType', 'type']);
    const result = {};
    for (const [key, field] of Object.entries(value)) {
      if (key === 'type') continue;
      if (integerFields[value.type].includes(key)) result[key] = integer(field);
      else if (value.type === 'character.State' && key === 'abilities') {
        assert.ok(Array.isArray(field)); assert.equal(field.length, 6);
        const keys = field.map(pair => { assert.equal(pair.length, 2); return pair[0]; });
        assert.deepEqual([...keys].sort(), ['charisma', 'constitution', 'dexterity', 'intelligence', 'strength', 'wisdom'].map(key => 'ability.' + key));
        result[key] = Object.fromEntries(field.map(([ability, score]) => [ability, integer(score)]));
      } else result[key] = typed(field);
    }
    return result;
  }
  function eventEvidence(event, retained) {
    if (retained) {
      assert.equal(event.type, 'character.Event'); assert.equal(event.command.type, 'character.Command');
      assert.equal(event.after.type, 'character.State'); if (event.before !== null) assert.equal(event.before.type, 'character.State');
      if (event.command.restInput) assert.equal(event.command.restInput.type, 'character.RestInput');
    }
    const choices = event.command.restInput?.recoveredSpellSlots || (retained ? [] : {});
    const slots = retained ? Object.fromEntries(choices.map(pair => {
      assert.equal(pair.length, 2); const rank = integer(pair[0]), count = integer(pair[1]);
      assert.ok(rank >= 1 && rank <= 5 && count >= 1 && count <= 4); return [String(rank), count];
    })) : choices;
    return {operationId: event.operationId, requestDigest: event.requestDigest, kind: event.command.kind,
      hitDieRoll: event.hitDieRoll, checkResult: event.checkResult, healthResult: event.healthResult,
      before: retained ? typed(event.before) : event.before, after: retained ? typed(event.after) : event.after, recoveredSpellSlots: slots};
  }
  const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
  const completePayload = ({fileKey, integrity, ...payload}) => payload;
  async function saveManifest(directory) {
    const rows = [];
    for (const name of (await fs.readdir(directory)).filter(name => name.endsWith('.json')).sort()) {
      const bytes = await fs.readFile(path.join(directory, name)), file = JSON.parse(bytes);
      assert.equal(name, file.fileKey + '.json');
      rows.push({fileKey: file.fileKey, name: file.campaign.name, bytes: bytes.length, sha256: sha256(bytes)});
    }
    return rows;
  }
  async function verifyOriginalSaves() {
    if (!originalSaves) return;
    const after = await saveManifest(saveDirectory), byKey = new Map(after.map(file => [file.fileKey, file]));
    for (const file of originalSaves) assert.deepEqual(byKey.get(file.fileKey), file, `Original save ${file.fileKey} changed`);
    report.originalSavesAfter = originalSaves.map(file => byKey.get(file.fileKey));
  }
  async function resumeArcane() {
    assert.equal(path.basename(resumeInput), 'input.json');
    assert.equal(path.basename(path.dirname(resumeInput)), resumeRun);
    const runDirectory = path.dirname(resumeInput);
    assert.equal(path.basename(path.dirname(runDirectory)), 'runs');
    saveDirectory = path.join(path.dirname(path.dirname(runDirectory)), 'saves');
    const bytes = await fs.readFile(resumeInput), baseline = JSON.parse(bytes);
    const allocation = JSON.parse(await fs.readFile(path.join(runDirectory, 'allocation.json'), 'utf8'));
    assert.equal(allocation.runId, resumeRun); assert.equal(baseline.fileKey, resumeRun);
    assert.equal(sha256(bytes), allocation.inputDigest); assert.equal(baseline.campaign.name, ARCANE_CAMPAIGN);
    assert.equal(baseline.applicationModel, 'campaign-spaces-3'); assert.equal(baseline.fileKind, 'RULE_BOUND_CAMPAIGN');
    assert.equal(baseline.formatVersion, 3); assert.equal(baseline.campaign.eventTail, '14');
    assert.equal(baseline.domains.type, 'campaign.State'); assert.equal(baseline.domains.characters.length, 1);
    const character = baseline.domains.characters[0];
    assert.equal(character.type, 'campaign.Character'); assert.equal(character.history.type, 'character.History');
    assert.equal(character.history.current.type, 'character.State');
    const expectedState = typed(character.history.current), expectedRoots = baseline.domains.roots.map(root => {
      assert.equal(root.type, 'campaign.Root'); return typed(root);
    });
    const expectedEvents = character.history.events.map(event => eventEvidence(event, true));
    assert.equal(expectedRoots.length, 14); assert.equal(expectedEvents.length, 11);
    assert.deepEqual(expectedRoots.flatMap(root => root.characterOperations), expectedEvents.map(event => event.operationId));
    assert.deepEqual(expectedEvents.map(event => event.kind), ['INITIALIZED', 'SPELLBOOK_INITIALIZED', 'RESOURCE_SPENT',
      'REST_STARTED', 'TIME_ADVANCED', 'TIME_ADVANCED', 'REST_COMPLETED', 'REST_ENDED', 'REST_STARTED', 'TIME_ADVANCED', 'REST_COMPLETED']);
    assert.deepEqual(expectedState.characterClass, {moduleKey: 'srd51_characters', ruleType: 'character.class', ruleKey: 'class.wizard'});
    assert.equal(expectedState.campaignId, baseline.campaign.campaignKey);
    const slot = expectedState.resources.find(resource => resource.identity.ruleKey === 'resource.wizard.slot1');
    assert.deepEqual(slot.identity, {moduleKey: 'srd51_characters', ruleType: 'character.resource', ruleKey: 'resource.wizard.slot1'});
    assert.equal(slot.current, 1); assert.equal(slot.maximum, 2); assert.equal(expectedState.spellbook.lastArcaneRecoveryDay, 0);
    assert.deepEqual(expectedEvents[6].recoveredSpellSlots, {'1': 1}); assert.deepEqual(expectedEvents[10].recoveredSpellSlots, {});
    originalSaves = await saveManifest(saveDirectory); report.originalSavesBefore = originalSaves;
    const matchingSavedFiles = [];
    for (const file of originalSaves) {
      assert.ok([CAMPAIGN, FULL_CAMPAIGN, ARCANE_CAMPAIGN].includes(file.name), 'Refusing unrelated saved data');
      const saved = JSON.parse(await fs.readFile(path.join(saveDirectory, file.fileKey + '.json'), 'utf8'));
      if (isDeepStrictEqual(completePayload(saved), completePayload(baseline))) matchingSavedFiles.push(file.fileKey);
    }
    assert.ok(matchingSavedFiles.length > 0, 'Retained input must match an original complete save payload');
    report.baseline = {runId: resumeRun, campaignId: baseline.campaign.campaignKey, inputBytes: bytes.length,
      inputSha256: sha256(bytes), matchingSavedFiles};
    await fs.writeFile(path.join(output, 'baseline-input.json'), bytes);
    const response = await page.request.get(`${ORIGIN}/api/host/storage/state`, {timeout: HTTP_TIMEOUT});
    assert.equal(response.status(), 200); const initial = await response.json();
    assert.equal(initial.storage.state, 'RESIDUAL'); assert.equal(initial.campaign, null);
    assert.deepEqual(initial.storage.residualRunIds, [resumeRun]);
    assert.deepEqual(initial.storage.saveFiles, [], 'Residual policy must hide save choices until recovery');
    assert.deepEqual(initial.savedCampaigns, [], 'Residual policy must hide save metadata until recovery');
    disposableConfirmed = true;
    await form('storage-residuals').selectOption(resumeRun);
    await action('recover', () => form('storage-recover').getByRole('button', {name: '恢复战役', exact: true}).click());
    ownsActiveFixture = true;
    const recoveredResponse = await page.request.get(`${ORIGIN}/api/host/storage/state`, {timeout: HTTP_TIMEOUT});
    assert.equal(recoveredResponse.status(), 200); const recoveredView = await recoveredResponse.json();
    assert.equal(recoveredView.storage.state, 'ACTIVE'); assert.equal(recoveredView.storage.runId, resumeRun);
    assert.deepEqual([...recoveredView.storage.saveFiles].sort(), originalSaves.map(file => file.fileKey).sort());
    const recovered = recoveredView.campaign, current = recovered.domain.characters[0];
    assert.equal(recovered.campaignId, baseline.campaign.campaignKey); assert.equal(recovered.name, ARCANE_CAMPAIGN);
    assert.equal(recovered.history.length, baseline.domains.roots.length);
    assert.equal(current.name, character.name); assert.equal(current.kind, character.kind); assert.equal(current.active, character.active);
    assert.deepEqual(current.state, expectedState); assert.deepEqual(current.history.current, expectedState);
    assert.deepEqual(recovered.domain.roots, expectedRoots);
    assert.deepEqual(recovered.domain.space, typed(baseline.domains.space));
    assert.deepEqual(current.history.events.map(event => eventEvidence(event, false)), expectedEvents);
    report.checks.push('Explicit recovery matched the retained input, saved payloads, space facts, Wizard slots and use day, all roots and character events');
    await page.locator('[data-pane="growth"]').click();
    await screenshot('01-recovered-arcane-wide.png', 1440, 1050);
    await screenshot('02-recovered-arcane-mobile.png', 390, 844);
    const freshSave = await action('save', () => form('storage-save').click());
    assert.ok(freshSave.savedFile, 'Save must return its exact accepted file identity');
    const freshBytes = await fs.readFile(path.join(saveDirectory, freshSave.savedFile + '.json'));
    const freshDocument = JSON.parse(freshBytes);
    assert.equal(freshDocument.fileKey, freshSave.savedFile);
    assert.deepEqual(completePayload(freshDocument), completePayload(baseline), 'Fresh complete save must preserve every retained payload field');
    report.freshSave = {fileKey: freshSave.savedFile, bytes: freshBytes.length, sha256: sha256(freshBytes), retainedPayloadEqual: true};
    const saved = await endFixture(); report.roundTripFileKey = saved;
    await form('storage-files').selectOption(saved);
    await action('open', () => form('storage-open').getByRole('button', {name: '打开战役', exact: true}).click());
    ownsActiveFixture = true;
    const reopened = await currentCampaign();
    assert.deepEqual(reopened.domain, recovered.domain); assert.deepEqual(reopened.history, recovered.history);
    assert.match(await form('storage-characters').innerText(), /奥术回想使用日：第 0 日/);
    assert.match(await form('storage-history').innerText(), /奥术回想：.*0 → 1（恢复 1）/);
    await screenshot('03-reopened-arcane-wide.png', 1440, 1050);
    await endFixture();
    report.checks.push('Fresh save + END + exact-file open preserved the entire recovered Host domain/history; reopened fixture ended successfully');
    await verifyOriginalSaves();
  }

  try {
    const initial = await page.goto(`${ORIGIN}/host/storage`, { waitUntil: 'networkidle' });
    assert.equal(initial.status(), 200, 'The isolated storage page must be available');
    await visible('#storage-idle');
    await equalText('#storage-message', '创建、打开或恢复一个战役。');
    if (resume) { await resumeArcane(); } else {
    assert.equal(await form('storage-active').isVisible(), false, 'Refusing an existing active run');
    assert.equal(await form('storage-files').locator('option').count(), 0, 'Refusing a root with saved campaigns');
    assert.equal(await form('storage-residuals').locator('option').count(), 0, 'Refusing a root with residual runs');
    assert.equal(await form('storage-create').getByRole('button', {name: '创建战役', exact: true}).isDisabled(), true);
    disposableConfirmed = true;
    report.checks.push('Fresh disposable root verified before the first business write');
    console.log('Browser phase: verifying local rule acceptance and free-card forms');
    await selectStandardRules(page);
    await form('storage-create').locator('[name="name"]').fill(CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true;
    await visible('#storage-active');
    await equalText('#storage-campaign-name', CAMPAIGN);
    await createFreeCard('艾拉', 12, 4);
    await historyCount(3);
    const firstNode = await createNode(page, action, '自由卡营地');
    await focusSpace(page, action, 'WORLD', firstNode);
    await placeAtFocus(page, action, [{label: '艾拉'}]);
    await page.locator('[data-pane="manual"]').click();
    const checkForm = form('manual-check');
    await checkForm.locator('[name="characterId"]').selectOption({label: '艾拉'});
    await checkForm.locator('[name="checkType"]').selectOption('MANUAL');
    await checkForm.locator('[name="difficultyClass"]').fill('0');
    await page.locator('[data-add-manual-effect="success"]').click();
    const recovery = form('manual-success-effects').locator('fieldset').last();
    await recovery.locator('[name="kind"]').selectOption('HP');
    await recovery.locator('[name="targetId"]').selectOption({label: '艾拉'});
    await recovery.locator('[name="amount"]').fill('5');
    await action('domain', () => checkForm.getByRole('button', {name: '服务器掷骰并应用后果', exact: true}).click());
    await equalText('#manual-cards .hp', '9 / 12');
    await historyCount(7);
    assert.match(await form('storage-history').innerText(), /自由卡检定.*应用顺序：生命值 5/);
    report.checks.push('Explicit accepted rule selection allowed a free-card check to apply a +5 HP consequence to 4/12');
    await screenshot('01-healed-wide.png', 1440, 1050);
    await screenshot('02-healed-mobile.png', 390, 844);

    await action('save', () => form('storage-save').click());
    const finalSave = await endFixture();
    await form('storage-files').selectOption(finalSave);
    await action('open', () => form('storage-open').getByRole('button', { name: '打开战役', exact: true }).click());
    ownsActiveFixture = true;
    await visible('#storage-active');
    await equalText('#storage-campaign-name', CAMPAIGN);
    await equalText('#manual-cards .hp', '9 / 12');
    await historyCount(7);
    report.checks.push('Selected exact final save through UI; reopened free-card HP and all seven history entries match');
    await screenshot('03-reopened-wide.png', 1440, 1050);
    await endFixture();
    await form('storage-create').locator('[name="name"]').fill(FULL_CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true; await visible('#storage-domain');
    await page.locator('[data-pane="people"]').click();
    const chooseRule = async (selector, fragment) => {
      const values = await page.locator(selector).locator('option').evaluateAll(options => options.map(option => option.value));
      const value = values.find(value => value.includes(fragment));
      assert.ok(value, `No supported rule for ${fragment}`); await page.locator(selector).selectOption(value);
    };
    for (const [name, kind] of [['战士艾拉', 'PC'], ['守桥人', 'NPC']]) {
      await form('domain-create').locator('[name="name"]').fill(name);
      await form('domain-create').locator('[name="kind"]').selectOption(kind);
      await chooseRule('#domain-create [name="classKey"]', 'fighter');
      await action('domain', () => form('domain-create').getByRole('button', { name: '创建完整角色', exact: true }).click());
    }
    assert.equal(await page.locator('#storage-characters .character-card').count(), 2);
    console.log('Browser phase: rich PC and NPC created; testing world and conditional checks');
    const camp = await createNode(page, action, '石桥营地');
    await focusSpace(page, action, 'WORLD', camp);
    const ruleCards = (await currentCampaign()).domain.characters;
    const pc = ruleCards.find(card => card.kind === 'PC'), npc = ruleCards.find(card => card.kind === 'NPC');
    await placeAtFocus(page, action, [pc.id, npc.id]);
    await form('domain-item').locator('[name="characterId"]').selectOption(pc.id);
    await form('domain-item').locator('[name="name"]').fill('火把');
    await form('domain-item').locator('[name="quantity"]').fill('3');
    await action('domain', () => form('domain-item').getByRole('button', { name: '保存物品记录', exact: true }).click());
    await form('domain-node').locator('[name="name"]').fill('桥后哨所');
    await action('domain', () => form('domain-node').getByRole('button', { name: '建立地点', exact: true }).click());
    await form('domain-note').locator('[name="text"]').fill('守桥人与队伍在石桥会合。');
    await action('domain', () => form('domain-note').getByRole('button', { name: '记入战役历史', exact: true }).click());
    await page.locator('[data-pane="actions"]').click();
    await form('domain-check').locator('[name="characterId"]').selectOption(pc.id);
    await action('domain', () => form('domain-check').getByRole('button', { name: '服务器掷骰检定', exact: true }).click());
    assert.match(await form('storage-characters').innerText(), /最近检定/);
    await form('check-with-effects').check();
    await chooseRule('#domain-check [name="successEffect"]', 'vital_effect.dm_damage');
    await chooseRule('#domain-check [name="failureEffect"]', 'vital_effect.dm_healing');
    for (const branch of ['success', 'failure']) {
      const select = form('domain-check').locator(`[name="${branch}Item"]`);
      const item = await select.locator('option').evaluateAll(options => options.find(option => option.value)?.value);
      assert.ok(item, 'Conditional item must come from the selected target inventory'); await select.selectOption(item);
    }
    await form('domain-check').locator('[name="successDelta"]').fill('2');
    await form('domain-check').locator('[name="failureDelta"]').fill('-1');
    await form('domain-check').locator('[name="successMessage"]').fill('成功分支：备好火把。');
    await form('domain-check').locator('[name="failureMessage"]').fill('失败分支：在营地消耗火把。');
    await form('domain-check').locator('[name="difficultyClass"]').fill('0');
    await action('domain', () => form('domain-check').getByRole('button', { name: '服务器掷骰检定', exact: true }).click());
    assert.match(await form('storage-history').innerText(), /成功分支：备好火把/);
    assert.match(await form('storage-history').innerText(), /火把 数量 3 → 5/);
    await form('domain-check').locator('[name="difficultyClass"]').fill('30');
    await action('domain', () => form('domain-check').getByRole('button', { name: '服务器掷骰检定', exact: true }).click());
    assert.match(await form('storage-history').innerText(), /失败分支：在营地消耗火把/);
    assert.match(await form('storage-history').innerText(), /火把 数量 5 → 4/);
    report.checks.push('The same check form declared both branches before server dice, then applied HP, inventory and message for forced success and forced failure');
    console.log('Browser phase: both rich conditional outcomes verified; testing free cards');
    await page.locator('[data-pane="manual"]').click();
    await form('manual-create').locator('[name="name"]').fill('自由记录员');
    await form('manual-create').locator('[name="template"]').selectOption('blank');
    const manualCreated = await action('domain', () => form('manual-create').getByRole('button', { name: '创建自由卡', exact: true }).click());
    const saveRule = manualCreated.campaign.domain.manual.catalog.saves.find(save => save.abilityKey === 'ability.dexterity');
    assert.ok(saveRule, 'The free card catalog must publish its saving throw identity');
    const editManual = async (key, value) => {
      const row = page.locator(`#manual-card-editor [data-manual-target="${key}"]`);
      await row.locator('input').fill(value); await action('domain', () => row.getByRole('button', { name: '保存', exact: true }).click());
    };
    await editManual('hp.maximum', '12'); await editManual('hp.current', '4'); await editManual('ability.dexterity', '14');
    await form('manual-card-editor').locator('summary', {hasText: '职业等级'}).click();
    await editManual('class.fighter', '3');
    await form('manual-card-editor').locator('summary', {hasText: '豁免熟练'}).click();
    const saveRow = page.locator(`#manual-card-editor [data-manual-target="${saveRule.key}"]`);
    await saveRow.locator('select').selectOption('FULL');
    await action('domain', () => saveRow.getByRole('button', { name: '保存', exact: true }).click());
    await form('manual-create').locator('[name="name"]').fill('自由守卫');
    await form('manual-create').locator('[name="kind"]').selectOption('NPC');
    await form('manual-create').locator('[name="template"]').selectOption('guard');
    await action('domain', () => form('manual-create').getByRole('button', { name: '创建自由卡', exact: true }).click());
    const manualIds = (await currentCampaign()).domain.manual.characters.map(history => history.current.id);
    await placeAtFocus(page, action, manualIds);
    const adjudicationScene = await createScene(page, action, '混合裁定场景', 'DM');
    await enterScene(page, action, adjudicationScene, [[manualIds[0], 0, 0, 0], [manualIds[1], 1, 0, 1], [pc.id, 2, 0, 0]]);
    await action('domain', () => form('domain-scene-mode').locator('button').click());
    await page.locator('[data-pane="manual"]').click();
    await form('manual-check').locator('[name="characterId"]').selectOption({label: '自由记录员'});
    await form('manual-check').locator('[name="checkType"]').selectOption('MANUAL');
    await form('manual-check').locator('[name="manualModifier"]').fill('0');
    await form('manual-check').locator('[name="difficultyClass"]').fill('0');
    const previousEffects = form('manual-check').getByRole('button', {name: '删除后果', exact: true});
    while (await previousEffects.count()) await previousEffects.first().click();
    async function manualEffect(branch, kind, values) {
      await page.locator(`[data-add-manual-effect="${branch}"]`).click();
      const row = page.locator(`#manual-${branch}-effects > fieldset`).last(); await row.locator('[name="kind"]').selectOption(kind);
      if (kind !== 'MESSAGE') await row.locator('[name="targetId"]').selectOption({label: '自由记录员'});
      for (const [name, value] of Object.entries(values)) {
        const input = row.locator(`[name="${name}"]`);
        await input.fill(value);
      }
    }
    await manualEffect('success', 'HP', {amount: '3'}); await manualEffect('success', 'HP', {amount: '-1'});
    await manualEffect('success', 'MESSAGE', {text: '自由行动成功，先恢复后消耗。'});
    await manualEffect('failure', 'HP', {amount: '-1'});
    await action('domain', () => form('manual-check').getByRole('button', { name: '服务器掷骰并应用后果', exact: true }).click());
    const freeCard = () => page.locator('#manual-cards .card').filter({hasText: '自由记录员'});
    assert.equal((await freeCard().locator('.hp').textContent()).trim(), '6 / 12');
    await form('manual-check').locator('[name="checkType"]').selectOption('SAVING_THROW');
    await form('manual-check-source').selectOption(saveRule.key);
    await form('manual-check').locator('[name="rollMode"]').selectOption('ADVANTAGE');
    await form('manual-check').locator('[name="difficultyClass"]').fill('60');
    await manualEffect('failure', 'GRANT_TEMPORARY_ITEM', {name: '应急绷带', description: '自由卡豁免记录', quantity: '2'});
    await manualEffect('failure', 'MESSAGE', {text: '豁免失败，使用应急绷带。'});
    await action('domain', () => form('manual-check').getByRole('button', { name: '服务器掷骰并应用后果', exact: true }).click());
    assert.equal((await freeCard().locator('.hp').textContent()).trim(), '5 / 12');
    assert.match(await form('storage-history').innerText(), /豁免失败，使用应急绷带/);
    assert.match(await freeCard().innerText(), /应急绷带 × 2/);
    await form('manual-inventory').locator('[name="characterId"]').selectOption({label: '自由记录员'});
    await form('manual-item-source').selectOption('item.torch');
    await form('manual-inventory').locator('[name="quantity"]').fill('3');
    const inventoryAdded = await action('domain', () => form('manual-inventory').getByRole('button', { name: '保存自由卡物品', exact: true }).click());
    const owner = inventoryAdded.campaign.domain.manual.characters.map(history => history.current).find(card => card.name === '自由记录员');
    const torch = inventoryAdded.campaign.domain.items.find(item => item.characterId === owner.id && item.sourceKey === 'item.torch');
    assert.ok(torch); await form('manual-inventory-item').selectOption(torch.id);
    await form('manual-inventory').locator('[name="active"]').uncheck();
    await action('domain', () => form('manual-inventory').getByRole('button', { name: '保存自由卡物品', exact: true }).click());
    assert.match(await freeCard().innerText(), /归档/);
    await form('manual-inventory').locator('[name="active"]').check();
    await action('domain', () => form('manual-inventory').getByRole('button', { name: '保存自由卡物品', exact: true }).click());
    await page.locator('[data-pane="world"]').click();
    await action('domain', () => form('domain-scene-mode').locator('button').click());
    for (const id of [...manualIds, pc.id])
      await form('domain-scene-leave-subjects').locator(`[data-character="${id}"] [name="join"]`).check();
    await action('domain', () => form('domain-scene-leave').locator('button').click());
    await page.locator('[data-pane="manual"]').click();
    report.checks.push('Free cards edited HP, Dexterity, class level and saving proficiency; MANUAL and saving checks applied ordered outcomes, inventory archive/restore and one shared DM combat grid with rule and free cards');
    console.log('Browser phase: free cards, ordered checks and mixed DM combat verified; testing rich battle and files');
    await screenshot('07-free-card-wide.png', 1440, 1050);
    await screenshot('08-free-card-mobile.png', 390, 844);
    await beginBattle(page, action, [[pc.id, 1, 0, 0], [npc.id, 2, 0, 1]]);
    assert.match(await form('domain-combat-summary').innerText(), /当前/);
    await form('domain-combat').locator('[name="kind"]').selectOption('END_TURN');
    await action('domain', () => form('domain-combat').getByRole('button', { name: '执行战斗行动', exact: true }).click());
    const fullHistoryCount = await page.locator('#storage-history > li').count();
    await screenshot('05-full-campaign-wide.png', 1440, 1050);
    await screenshot('06-full-campaign-mobile.png', 390, 844);
    const fullSave = await endFixture();
    await form('storage-files').selectOption(fullSave);
    await action('open', () => form('storage-open').getByRole('button', { name: '打开战役', exact: true }).click());
    ownsActiveFixture = true; await visible('#storage-domain');
    assert.equal(await page.locator('#storage-characters .character-card').count(), 2);
    assert.equal(await page.locator('#manual-cards .card').count(), 2);
    assert.match(await form('manual-cards').innerText(), /应急绷带/);
    await historyCount(fullHistoryCount);
    assert.match(await form('domain-world-summary').innerText(), /石桥营地/);
    assert.match(await form('domain-world-summary').innerText(), /火把/);
    assert.match(await form('domain-combat-summary').innerText(), /当前/);
    const downloadable = await endFixture();
    await form('storage-files').selectOption(downloadable);
    const downloadEvent = page.waitForEvent('download'); await form('storage-download').click();
    const download = await downloadEvent, downloadedFile = path.join(output, 'campaign-fixture.json');
    await download.saveAs(downloadedFile);
    page.once('dialog', dialog => dialog.accept());
    await action('delete', () => form('storage-delete').click());
    await form('storage-import').locator('[name="file"]').setInputFiles(downloadedFile);
    await action('import', () => form('storage-import').getByRole('button', { name: '导入文件', exact: true }).click());
    await form('storage-files').selectOption(downloadable);
    assert.equal(await form('storage-download').isVisible(), true);
    report.checks.push('Runtime UI created PC and NPC, locations, positions, items, notes, a server check and battle turn; full save reopened unchanged');
    report.checks.push('Downloaded, deleted and reimported one exact full file through protected UI');
    console.log('Browser phase: full file reopened, downloaded, deleted and reimported; testing Arcane Recovery');

    await form('storage-create').locator('[name="name"]').fill(ARCANE_CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true;
    await page.locator('[data-pane="people"]').click();
    await form('domain-create').locator('[name="name"]').fill('研读法师');
    await chooseRule('#domain-create [name="classKey"]', 'class.wizard');
    await action('domain', () => form('domain-create').getByRole('button', { name: '创建完整角色', exact: true }).click());
    await page.locator('[data-pane="magic"]').click();
    const book = form('domain-spellbook');
    const spellChoices = async names => book.locator('[name="learned"] option').evaluateAll((options, names) =>
      names.map(name => options.find(option => option.value.endsWith('/spell.' + name))?.value), names);
    const learned = await spellChoices(['shield', 'burning_hands', 'alarm', 'charm_person', 'color_spray', 'comprehend_languages']);
    assert.ok(learned.every(Boolean), 'Wizard book must use the actual finite rule catalog');
    await book.locator('[name="learned"]').selectOption(learned);
    await book.locator('[name="prepared"]').selectOption(await spellChoices(['shield', 'burning_hands']));
    await action('domain', () => book.getByRole('button', { name: '确认法术选择', exact: true }).click());
    const wizardId = (await currentCampaign()).domain.characters[0].id;
    const wizardNode = await createNode(page, action, '研读营地');
    await focusSpace(page, action, 'WORLD', wizardNode);
    await placeAtFocus(page, action, [wizardId]);
    await page.locator('[data-pane="growth"]').click();
    await chooseRule('#domain-resource [name="resource"]', 'resource.wizard.slot1');
    await form('domain-resource').locator('[name="amount"]').fill('2');
    await action('domain', () => form('domain-resource').getByRole('button', { name: '应用资源操作', exact: true }).click());
    const rest = form('domain-rest');
    const restAction = async kind => {
      await rest.locator('[name="kind"]').selectOption(kind);
      await action('domain', () => rest.getByRole('button', { name: '应用休息操作', exact: true }).click());
    };
    await restAction('REST_STARTED');
    await rest.locator('[name="minutes"]').fill('59'); await restAction('TIME_ADVANCED');
    await rest.locator('[name="kind"]').selectOption('REST_COMPLETED');
    await rest.locator('[name="recoverSlot1"]').fill('1');
    await rejectRecovery([['1', '1']]);
    await rest.locator('[name="minutes"]').fill('1'); await restAction('TIME_ADVANCED');
    await rest.locator('[name="kind"]').selectOption('REST_COMPLETED');
    await rest.locator('[name="recoverSlot1"]').fill('2');
    await rejectRecovery([['1', '2']]);
    assert.equal(await rest.locator('[name="recoverSlot1"]').getAttribute('max'), '2');
    assert.equal(await rest.locator('[name="recoverSlot4"]').isVisible(), false);
    assert.equal(await rest.locator('[name="recoverSlot5"]').isVisible(), false);
    await rest.locator('[name="recoverSlot1"]').fill('1');
    await restAction('REST_COMPLETED');
    let wizard = (await currentCampaign()).domain.characters[0];
    const wizardSlots = character => character.state.resources.find(resource => resource.identity.ruleKey === 'resource.wizard.slot1');
    assert.equal(Number(wizardSlots(wizard).current), 1); assert.equal(Number(wizard.state.spellbook.lastArcaneRecoveryDay), 0);
    assert.deepEqual(wizard.history.events.at(-1).command.restInput.recoveredSpellSlots, {'1': 1});
    assert.match(await form('storage-characters').innerText(), /最近奥术回想：.*0 → 1（恢复 1）/);
    assert.match(await form('storage-history').innerText(), /奥术回想：.*0 → 1（恢复 1）/);
    await restAction('REST_ENDED'); await restAction('REST_STARTED');
    await rest.locator('[name="minutes"]').fill('60'); await restAction('TIME_ADVANCED');
    await rest.locator('[name="kind"]').selectOption('REST_COMPLETED'); await rest.locator('[name="recoverSlot1"]').fill('1');
    assert.match(await form('arcane-recovery-status').innerText(), /本日已使用/);
    await rejectRecovery([['1', '1']]);
    await screenshot('09-arcane-recovery-wide.png', 1440, 1050);
    await screenshot('10-arcane-recovery-mobile.png', 390, 844);
    await rest.locator('[name="recoverSlot1"]').fill('0'); await restAction('REST_COMPLETED');
    wizard = (await currentCampaign()).domain.characters[0];
    assert.equal(Number(wizardSlots(wizard).current), 1); assert.equal(Number(wizard.state.spellbook.lastArcaneRecoveryDay), 0);
    assert.deepEqual(wizard.history.events.at(-1).command.restInput.recoveredSpellSlots, {}, 'All zeros must decline without consuming another use');
    const arcaneBefore = await currentCampaign();
    await action('save', () => form('storage-save').click());
    const arcaneSave = await endFixture();
    await form('storage-files').selectOption(arcaneSave);
    await action('open', () => form('storage-open').getByRole('button', { name: '打开战役', exact: true }).click());
    ownsActiveFixture = true;
    const arcaneAfter = await currentCampaign();
    assert.deepEqual(arcaneAfter.domain, arcaneBefore.domain, 'Save, END and reopen must retain slots, use day, exact roots and child history');
    assert.deepEqual(arcaneAfter.history, arcaneBefore.history);
    assert.match(await form('storage-characters').innerText(), /奥术回想使用日：第 0 日/);
    assert.match(await form('storage-history').innerText(), /奥术回想：.*0 → 1（恢复 1）/);
    await endFixture();
    report.checks.push('Real Wizard rest form offered only spent supported slots and submitted selected ranks/counts; incomplete rest, over-budget and daily reuse were rejected without state/history writes; empty selection declined');
    report.checks.push('Arcane Recovery restored exactly one spent slot, displayed actual recovery/day, and Save + END + reopen preserved slots, use day and complete root/child history');
    console.log('Browser phase: Arcane Recovery and full-file round trip verified');

    }
    assert.equal(await form('storage-residuals').locator('option').count(), 0, 'Fixture runs must end without residuals');
    assert.deepEqual(report.pageErrors, [], 'Browser JavaScript errors were observed');
    assert.deepEqual(report.cspFailures, [], 'CSP failures were observed');
    report.checks.push('All fixture runs ended through Save and End; no pageerror or CSP failures');
    await verifyOriginalSaves();
    report.result = 'PASSED';
  } catch (error) {
    report.error = error.message;
    if (resume) report.cleanup = 'Retained fixture and accepted outcomes preserved; no automatic retry or END on failure';
    if (disposableConfirmed) {
      await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
      if (!resume && ownsActiveFixture && await form('storage-active').isVisible().catch(() => false)) {
        const name = await form('storage-campaign-name').textContent().catch(() => '');
        if ([CAMPAIGN, FULL_CAMPAIGN, ARCANE_CAMPAIGN].includes(name.trim())) {
          try { await endFixture(); report.cleanup = 'Fixture saved and ended through UI'; }
          catch (cleanup) { report.cleanup = `UI cleanup remains pending: ${cleanup.message}`; }
        }
      }
    }
    throw error;
  } finally {
    await browser.close();
    await fs.writeFile(path.join(output, 'browser-report.json'), JSON.stringify(report, null, 2) + '\n');
  }
}

main().then(() => {
  console.log(`Browser smoke passed. Screenshots and report: ${output}`);
}).catch(error => {
  console.error(`Browser smoke failed: ${error.message}`);
  console.error(`Report directory: ${output}`);
  process.exitCode = 1;
});
