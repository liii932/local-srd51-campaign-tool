#!/usr/bin/env node
'use strict';

// Run against a fresh disposable run-local.sh instance on the protected loopback entry.
// Usage: node tools/storage/browser-manual-check-draft.cjs --fresh-root /absolute/private/test-root
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady, readHistory} = require('./browser-action.cjs');
const {createNode, focusSpace, placeAtFocus} = require('./browser-spaces.cjs');

const ORIGIN = 'http://127.0.0.1:8080';
const ENDPOINT = `${ORIGIN}/api/host/storage/`;
const TIMEOUT = 120000;
const CHECK_FIELDS = ['characterId', 'checkType', 'modifierSource', 'manualName', 'manualModifier', 'rollMode', 'difficultyClass'];
const [flag, storageRoot] = process.argv.slice(2);
assert.ok(process.argv.length === 4 && flag === '--fresh-root' && path.isAbsolute(storageRoot),
  'Usage: node browser-manual-check-draft.cjs --fresh-root /absolute/private/test-root');

async function main() {
  const root = await fs.realpath(storageRoot);
  const repo = await fs.realpath(path.join(__dirname, '../..'));
  assert.ok(root !== repo && !root.startsWith(repo + path.sep), 'The disposable storage root must be outside the repository');
  assert.equal((await fs.lstat(storageRoot)).isSymbolicLink(), false);
  assert.deepEqual((await fs.readdir(root)).sort(), ['instance.lock', 'runs', 'saves'], 'Refusing an unrecognized storage root');
  for (const name of ['runs', 'saves']) {
    const directory = path.join(root, name);
    assert.ok((await fs.lstat(directory)).isDirectory(), `Expected a real ${name} directory`);
    assert.deepEqual(await fs.readdir(directory), [], `Refusing a nonempty ${name} directory`);
  }

  const browser = await chromium.launch({headless: true});
  try {
    const page = await browser.newPage({viewport: {width: 1440, height: 1050}, locale: 'zh-CN'});
    page.setDefaultTimeout(30000);
    page.setDefaultNavigationTimeout(TIMEOUT);
    const pageErrors = [];
    let checkPosts = 0;
    page.on('pageerror', error => pageErrors.push(error.message));
    page.on('request', request => {
      if (request.method() === 'POST' && request.url() === ENDPOINT + 'domain'
          && new URLSearchParams(request.postData()).get('action') === 'manual-check') checkPosts++;
    });

    const form = id => page.locator('#' + id);
    const field = (id, name) => form(id).locator(`[name="${name}"]`);
    const check = form('manual-check');
    const pane = name => page.locator(`[data-pane="${name}"]`).click();
    const submitCheck = check.getByRole('button', {name: '服务器掷骰并应用后果', exact: true});
    const rows = branch => form(`manual-${branch}-effects`).locator(':scope > fieldset');
    const choices = select => select.locator('option').evaluateAll(options => options.map(option => option.value).filter(Boolean));
    let current;
    const card = id => current.campaign.domain.manual.characters.find(history => history.current.id === id).current;

    async function action(name, trigger) {
      current = (await observeStorageAction(page, name, trigger, {})).body;
      return current;
    }

    async function readState(trigger) {
      const [response] = await Promise.all([
        page.waitForResponse(response => response.url() === ENDPOINT + 'state' && response.request().method() === 'GET', {timeout: TIMEOUT}),
        trigger()
      ]);
      assert.equal(response.status(), 200);
      current = await response.json();
      await waitForStorageReady(page);
    }

    const refresh = () => readState(() => page.evaluate(() => document.dispatchEvent(new Event('dnd:storage-refresh'))));
    const reload = () => readState(() => page.reload({waitUntil: 'domcontentloaded'}));

    async function createCampaign(name) {
      assert.equal(await form('storage-create-rules').inputValue(), 'standard');
      await field('storage-create', 'name').fill(name);
      await action('create', () => form('storage-create').getByRole('button', {name: '创建战役', exact: true}).click());
      assert.equal(current.campaign.name, name);
      assert.deepEqual(await fs.readdir(path.join(root, 'runs')), [current.storage.runId], 'Browser run must be in the supplied root');
      const identity = {campaignId: current.campaign.campaignId, runId: current.storage.runId};
      await pane('manual');
      await field('manual-create', 'kind').selectOption('NPC');
      for (const role of ['巡逻队长', '守卫']) {
        await field('manual-create', 'name').fill(`${name} · ${role}`);
        await field('manual-create', 'template').selectOption('guard');
        await action('domain', () => form('manual-create').getByRole('button', {name: '创建自由卡', exact: true}).click());
      }
      const node = await createNode(page, action, `${name} · 营地`);
      await focusSpace(page, action, 'WORLD', node);
      await placeAtFocus(page, action, current.campaign.domain.manual.characters.map(history => history.current.id));
      await pane('manual');
      const candidates = await choices(field('manual-check', 'characterId'));
      assert.equal(candidates.length, 2, 'The fixture must have two eligible free cards');
      return {...identity, candidates};
    }

    async function draft() {
      return check.evaluate((form, fieldNames) => {
        const branch = name => Array.from(form.querySelector(`#manual-${name}-effects`).children, row =>
          Object.fromEntries(Array.from(row.querySelectorAll('input, select'), control => [control.name, control.value])));
        const fields = Object.fromEntries(fieldNames.map(name => [name, form.elements[name].value]));
        return {fields, success: branch('success'), failure: branch('failure')};
      }, CHECK_FIELDS);
    }

    async function addEffect(branch, {kind, targetId, ...values}) {
      await check.locator(`[data-add-manual-effect="${branch}"]`).click();
      const row = rows(branch).last();
      await row.locator('[name="kind"]').selectOption(kind);
      if (targetId) await row.locator('[name="targetId"]').selectOption(targetId);
      for (const [name, value] of Object.entries(values)) await row.locator(`[name="${name}"]`).fill(value);
    }

    async function executeCheck(expected) {
      const {characterId, success, failure} = expected;
      const targetIds = [...new Set([...success, ...failure].map(effect => effect.targetId).filter(Boolean))].sort();
      const targets = targetIds.map(id => ({characterId: id, version: String(card(id).version)}));
      const characterVersion = String(card(characterId).version);
      const {space, generation} = current.campaign.domain.space.focus;
      const focus = {space, generation: String(generation)};
      const {body, request} = await observeStorageAction(page, 'domain', () => submitCheck.click(), {});
      assert.equal(request.get('action'), 'manual-check');
      assert.deepEqual(JSON.parse(request.get('payload')), {...expected, characterVersion, targets, focus},
        'POST must carry the current draft, latest versions and current focus');
      current = body;
      const event = current.campaign.domain.roots.at(-1);
      assert.equal(event.kind, 'manual-check');
      assert.equal(event.subjectId, characterId);
      assert.equal(JSON.parse(event.after).result.success, true);
    }

    async function assertFreshDraft(defaultSource) {
      const {fields, success, failure} = await draft();
      assert.deepEqual(success, [], 'A new run must have an empty success branch');
      assert.deepEqual(failure, [], 'A new run must have an empty failure branch');
      assert.deepEqual(fields, {characterId: '', checkType: 'ABILITY', modifierSource: defaultSource,
        manualName: 'DM 检定', manualModifier: '0', rollMode: 'NORMAL', difficultyClass: '10'},
      'A new run must have default check fields and require an explicit executor');
    }

    await readState(() => page.goto(ORIGIN + '/host/storage', {waitUntil: 'domcontentloaded'}));
    assert.equal(current.storage.state, 'IDLE', 'Refusing an existing active or blocked run');
    assert.equal(current.campaign, null);
    assert.deepEqual(current.savedCampaigns, [], 'Refusing existing saves');
    assert.deepEqual(current.storage.residualRunIds, [], 'Refusing existing residual runs');

    const a = await createCampaign('草稿甲');
    const [defaultSource] = await choices(field('manual-check', 'modifierSource'));
    assert.ok(defaultSource, 'The initial form must have an ability source');
    const aTarget = a.candidates[1];
    const aCheck = {characterId: aTarget, checkKind: 'MANUAL', key: null, manualName: '甲的试探', manualModifier: '7',
      rollMode: 'ADVANTAGE', dc: '4', success: [{kind: 'HP', targetId: aTarget, amount: '-2'}, {kind: 'MESSAGE', text: '甲的检定成功'}],
      failure: [{kind: 'GRANT_TEMPORARY_ITEM', targetId: a.candidates[0], name: '甲的信号旗', description: '甲的检定失败', quantity: '2'}]};
    await field('manual-check', 'characterId').selectOption(aCheck.characterId);
    await field('manual-check', 'checkType').selectOption(aCheck.checkKind);
    await field('manual-check', 'manualName').fill(aCheck.manualName);
    await field('manual-check', 'manualModifier').fill(aCheck.manualModifier);
    await field('manual-check', 'rollMode').selectOption(aCheck.rollMode);
    await field('manual-check', 'difficultyClass').fill(aCheck.dc);
    for (const effect of aCheck.success) await addEffect('success', effect);
    for (const effect of aCheck.failure) await addEffect('failure', effect);
    assert.equal((await choices(rows('success').first().locator('[name="targetId"]')))[1], aTarget, 'The selected HP target must be a non-first candidate');
    const aDraft = await draft();

    const initialHp = Number(card(aTarget).fields['hp.current']);
    await executeCheck(aCheck);
    assert.deepEqual(await draft(), aDraft, 'Executing A must preserve both branches and the check fields');
    await refresh();
    assert.deepEqual(await draft(), aDraft, 'GET refresh must preserve the current run draft');
    await action('save', () => form('storage-save').click());
    assert.deepEqual(await draft(), aDraft, 'Saving must preserve the current run draft');
    await executeCheck(aCheck);
    assert.deepEqual(await draft(), aDraft, 'Executing the same draft again must preserve it');
    assert.equal(Number(card(aTarget).fields['hp.current']), initialHp - 4, 'Both real A checks must apply to the selected target');
    await action('end', () => form('storage-end').click());

    const b = await createCampaign('草稿乙');
    assert.notEqual(b.campaignId, a.campaignId);
    assert.notEqual(b.runId, a.runId);
    await assertFreshDraft(defaultSource);
    const [bActor, bTarget] = b.candidates;
    const bKey = current.campaign.domain.manual.catalog.saves.find(save => save.abilityKey === 'ability.dexterity').key;
    const bCheck = {characterId: bActor, checkKind: 'SAVING_THROW', key: bKey, manualName: null, manualModifier: null,
      rollMode: 'DISADVANTAGE', dc: '0', success: [{kind: 'HP', targetId: bTarget, amount: '-1'}],
      failure: [{kind: 'MESSAGE', text: '乙的豁免失败'}]};
    await field('manual-check', 'characterId').selectOption(bActor);
    await field('manual-check', 'checkType').selectOption(bCheck.checkKind);
    await field('manual-check', 'modifierSource').selectOption(bCheck.key);
    await field('manual-check', 'rollMode').selectOption(bCheck.rollMode);
    await field('manual-check', 'difficultyClass').fill(bCheck.dc);
    await addEffect('success', bCheck.success[0]);
    await addEffect('failure', bCheck.failure[0]);

    const bDraft = await draft();
    const bTargetHp = Number(card(bTarget).fields['hp.current']);
    await executeCheck(bCheck);
    assert.equal(Number(card(bTarget).fields['hp.current']), bTargetHp - 1, 'B must apply its own HP adjustment');
    assert.doesNotMatch(await readHistory(page), /甲的/);
    assert.deepEqual(await draft(), bDraft, 'Executing B must preserve both branches and the derived source');
    await reload();
    await pane('manual');
    assert.equal(current.storage.runId, b.runId);
    assert.deepEqual(await draft(), bDraft, 'A full reload must restore both branches and the derived source');

    await pane('world');
    await field('domain-space-unplace', 'characterId').selectOption(bTarget);
    await field('domain-space-unplace', 'adjudication').fill('守卫退出本次执行范围');
    await action('domain', () => form('domain-space-unplace').getByRole('button', {name: '撤除安置', exact: true}).click());
    await pane('manual');
    const invalidDraft = {...bDraft, success: [{...bDraft.success[0], targetId: ''}]};
    const target = rows('success').first().locator('[name="targetId"]');
    assert.deepEqual(await choices(target), [bActor], 'Another eligible target must remain after unplacing the selected target');
    assert.deepEqual(await draft(), invalidDraft, 'Losing a target must preserve the row and clear its selection');
    assert.match(await target.locator('option:checked').textContent(), /目标已失效.*重新选择/);
    assert.equal(await target.getAttribute('aria-invalid'), 'true');
    assert.equal(await check.evaluate(form => form.checkValidity()), false, 'An unresolved target must block submission');
    const postsBefore = checkPosts, revisionBefore = current.campaign.revision;
    await submitCheck.click();
    await waitForStorageReady(page);
    for (let attempt = 0; attempt < 2; attempt++) {
      await refresh();
      assert.deepEqual(await draft(), invalidDraft, 'Repeated state refreshes must keep an unresolved target empty');
    }
    assert.equal(checkPosts, postsBefore, 'Submitting with an unresolved target must send no POST');
    assert.equal(current.campaign.revision, revisionBefore);

    await reload();
    await pane('manual');
    assert.deepEqual(await draft(), invalidDraft, 'A reload must retain the unresolved row without choosing a target');
    assert.equal(await target.getAttribute('aria-invalid'), 'true');
    await placeAtFocus(page, action, [bTarget]);
    await pane('manual');
    assert.deepEqual(await choices(target), b.candidates, 'Both targets must be eligible again');
    assert.deepEqual(await draft(), invalidDraft, 'An eligible former target must still require explicit reselection');
    assert.equal(await check.evaluate(form => form.checkValidity()), false);

    await target.selectOption(bActor);
    assert.equal(await target.getAttribute('aria-invalid'), null);
    assert.equal(await check.evaluate(form => form.checkValidity()), true);
    const bActorHp = Number(card(bActor).fields['hp.current']);
    await executeCheck({...bCheck, success: [{...bCheck.success[0], targetId: bActor}]});
    assert.equal(Number(card(bActor).fields['hp.current']), bActorHp - 1, 'Explicit reselection must apply to the new target');
    assert.equal(Number(card(bTarget).fields['hp.current']), bTargetHp - 1, 'The removed target must receive no further HP adjustment');

    const finalB = await action('end', () => form('storage-end').click());
    await form('storage-files').selectOption(finalB.savedFile);
    await action('open', () => form('storage-open').getByRole('button', {name: '打开战役', exact: true}).click());
    await pane('manual');
    assert.equal(current.campaign.campaignId, b.campaignId);
    assert.notEqual(current.storage.runId, b.runId);
    await assertFreshDraft(defaultSource);
    assert.equal(Number(card(bActor).fields['hp.current']), bActorHp - 1, 'Opening a save must retain the applied campaign state');
    await action('end', () => form('storage-end').click());
    assert.equal(current.storage.state, 'IDLE');
    assert.deepEqual(await fs.readdir(path.join(root, 'runs')), []);
    assert.deepEqual(pageErrors, []);
    console.log('Manual check draft regression passed');
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
