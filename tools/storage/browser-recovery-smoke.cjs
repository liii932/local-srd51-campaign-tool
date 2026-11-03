#!/usr/bin/env node
'use strict';

// Real forms against a disposable external Tomcat WAR at the fixed loopback origin.
// The caller controls the exact Tomcat process between stages; this script never does.
// Usage: node browser-recovery-smoke.cjs prepare|recover|prepare-discard|discard /absolute/evidence-directory
// Use a new empty directory for each prepare, then its same directory for its matching transition.
// NODE_PATH supplies Playwright. DND_BROWSER_EXECUTABLE optionally selects native Chromium/Edge.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash, randomUUID } = require('node:crypto');
const { chromium } = require('playwright');

const ORIGIN = 'http://127.0.0.1:8080';
const HTTP_TIMEOUT = 120000;
const RECOVERY_NAME = '浏览器恢复验收营地';
const DISCARD_NAME = '浏览器精确放弃验收营地';
const KNOWN_NAMES = new Set(['浏览器验收营地', '基础生命值验收', '完整领域浏览器验收',
  '奥术回想浏览器验收', '装备与生命法术验收', RECOVERY_NAME, DISCARD_NAME]);
const [mode, output] = process.argv.slice(2);
assert.ok(process.argv.length === 4 && ['prepare', 'recover', 'prepare-discard', 'discard'].includes(mode)
  && path.isAbsolute(output || ''), 'Expected a mode and an absolute evidence directory');
const preparing = mode.startsWith('prepare');
const kind = mode.includes('discard') ? 'discard' : 'recover';
const fixtureName = kind === 'discard' ? DISCARD_NAME : RECOVERY_NAME;
const manifestPath = path.join(output, 'fixture.json');
const reportPath = path.join(output, `${mode}-report.json`);
const report = { schema: 1, origin: ORIGIN, mode, result: 'FAILED', checks: [], screenshots: [],
  pageErrors: [], cspFailures: [], actions: [] };
let outputReady = false;

const json = value => JSON.stringify(value, null, 2) + '\n';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
function durableCampaign(campaign) {
  // Epoch belongs to the current open run, and lastCommand is response-only metadata.
  const { epoch, lastCommand, ...facts } = campaign;
  return facts;
}

function requestDigest(action, epoch, revision, fields) {
  const parts = [];
  const integer = value => { const bytes = Buffer.alloc(4); bytes.writeInt32BE(value); parts.push(bytes); };
  const string = value => { const bytes = Buffer.from(String(value), 'utf8'); integer(bytes.length); parts.push(bytes); };
  for (const value of ['DND_STORAGE_HTTP_V1', action, epoch, revision]) string(value);
  const keys = Object.keys(fields).sort(); integer(keys.length);
  for (const key of keys) { string(key); integer(fields[key].length); for (const value of fields[key]) string(value); }
  return sha256(Buffer.concat(parts));
}

async function main() {
  if (preparing) {
    await fs.mkdir(output, { recursive: true });
    assert.equal((await fs.readdir(output)).length, 0, 'Prepare requires an empty evidence directory');
  } else {
    assert.ok((await fs.stat(output)).isDirectory(), 'The retained evidence directory must exist');
    await assert.rejects(fs.access(reportPath), { code: 'ENOENT' }, 'Refusing to overwrite transition evidence');
  }
  assert.equal((await fs.lstat(output)).isSymbolicLink(), false, 'Evidence directory must not be a symbolic link');
  outputReady = true;
  let fixture = preparing ? null : JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  if (fixture) {
    assert.equal(fixture.schema, 1);
    assert.equal(fixture.origin, ORIGIN);
    assert.equal(fixture.kind, kind, 'The retained prepare stage must match the requested transition');
    assert.equal(fixture.name, fixtureName);
    assert.match(fixture.runId, /^[0-9a-f-]{36}$/);
    assert.equal(fixture.expectedCampaign.name, fixtureName);
    assert.equal(fixture.expectedCampaign.campaignId, fixture.campaignId);
    assert.ok(fixture.savedFiles.some(file => file.fileKey === fixture.manualSaveFile), 'Retained manual save must be exact');
  }
  const launch = { headless: true }, executable = process.env.DND_BROWSER_EXECUTABLE;
  if (executable !== undefined) {
    assert.ok(path.isAbsolute(executable), 'DND_BROWSER_EXECUTABLE must be absolute');
    assert.ok((await fs.stat(executable)).isFile(), 'DND_BROWSER_EXECUTABLE must identify an existing file');
    launch.executablePath = executable;
  }
  const browser = await chromium.launch(launch);
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, locale: 'zh-CN' });
  const page = await context.newPage();
  page.setDefaultTimeout(30000); // UI selectors and dialogs retain their existing bound.
  let currentAction = null;
  let currentPhase = 'open-workbench';
  page.on('request', request => {
    if (!currentAction) return;
    if (request.method() === 'POST' && request.url() === `${ORIGIN}/api/host/storage/${currentAction.action}`) {
      currentAction.postRequestMs = Date.now() - currentAction.startedAt;
      currentAction.requestId = request.headers()['x-request-id'] || null;
    } else if (request.method() === 'GET' && request.url() === `${ORIGIN}/api/host/storage/state`) {
      currentAction.stateRequestMs = Date.now() - currentAction.startedAt;
    }
  });
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
  async function equalText(selector, expected) {
    await page.waitForFunction(({ selector, expected }) =>
      document.querySelector(selector)?.textContent.trim() === expected, { selector, expected });
  }
  async function state() {
    currentPhase = 'http-state';
    const response = await context.request.get(`${ORIGIN}/api/host/storage/state`, { timeout: HTTP_TIMEOUT });
    assert.equal(response.status(), 200, 'The real WAR storage state must be readable');
    const body = await response.json(); assert.equal(body.status, 'OK'); return body;
  }
  async function readiness(expectedState) {
    currentPhase = `http-readiness-${expectedState}`;
    const response = await context.request.get(`${ORIGIN}/api/host/storage/readiness`, { timeout: HTTP_TIMEOUT });
    assert.equal(response.status(), expectedState === 'RESIDUAL' ? 503 : 200);
    const body = await response.json();
    assert.equal(body.mode, 'SQLITE'); assert.equal(body.state, expectedState);
    assert.equal(body.ready, expectedState !== 'RESIDUAL');
    if (expectedState === 'RESIDUAL') assert.equal(body.code, 'STORAGE_RECOVERY_REQUIRED');
  }
  async function action(name, trigger) {
    const timing = { action: name, startedAt: Date.now(), requestId: null,
      postRequestMs: null, stateRequestMs: null, postMs: null, stateMs: null, totalMs: null };
    report.actions.push(timing); currentAction = timing;
    const phase = value => { currentPhase = `${name}:${value}`; };
    const observed = Promise.all([
      page.waitForResponse(response => response.request().method() === 'POST'
        && response.url() === `${ORIGIN}/api/host/storage/${name}`, { timeout: HTTP_TIMEOUT }).then(async response => {
          await response.finished();
          timing.postMs = Date.now() - timing.startedAt - (timing.postRequestMs || 0);
          return response;
        }),
      page.waitForResponse(response => response.request().method() === 'GET'
        && response.url() === `${ORIGIN}/api/host/storage/state`, { timeout: HTTP_TIMEOUT }).then(async response => {
          await response.finished();
          timing.stateMs = Date.now() - timing.startedAt - (timing.stateRequestMs || 0);
          return response;
        })
    ]).catch(observationFailure => ({ observationFailure }));
    try {
      phase('trigger'); await trigger();
      phase('http-responses'); const responses = await observed;
      if (responses.observationFailure) {
        phase(timing.postMs === null ? 'post-response' : 'paired-state-response');
        throw responses.observationFailure;
      }
      const [accepted, refreshed] = responses;
      phase('post-body'); const body = await accepted.json();
      assert.equal(accepted.status(), 200, `${name} failed with ${body.code || body.status}`);
      phase('paired-state'); assert.equal(refreshed.status(), 200); await refreshed.finished();
      phase('render');
      await equalText('#storage-message', name === 'end' ? '战役已保存并结束，可以再次打开。'
        : name === 'save' ? '战役已保存。' : name === 'discard' ? '所选运行已放弃，已存战役文件保留。' : '战役已更新。');
      return { body, request: new URLSearchParams(accepted.request().postData()) };
    } catch (failure) {
      report.failingPhase = currentPhase; report.failedRequestId = timing.requestId;
      throw failure;
    } finally {
      timing.totalMs = Date.now() - timing.startedAt;
      currentAction = null;
    }
  }
  async function savedFiles(current) {
    assert.deepEqual(current.savedCampaigns.map(file => file.fileKey).sort(), [...current.storage.saveFiles].sort());
    const files = [];
    for (const metadata of [...current.savedCampaigns].sort((a, b) => a.fileKey.localeCompare(b.fileKey))) {
      assert.ok(KNOWN_NAMES.has(metadata.name), `Refusing an unrecognized saved campaign: ${metadata.name}`);
      currentPhase = `http-download-${metadata.fileKey}`;
      const response = await context.request.get(`${ORIGIN}/api/host/storage/download?fileKey=${metadata.fileKey}`, { timeout: HTTP_TIMEOUT });
      assert.equal(response.status(), 200, `The exact saved fixture ${metadata.fileKey} must remain downloadable`);
      const bytes = await response.body();
      assert.ok(bytes.length > 0 && bytes.length <= 16 * 1024 * 1024);
      files.push({ ...metadata, bytes: bytes.length, sha256: sha256(bytes) });
    }
    return files;
  }
  async function screenshot(name) {
    await page.screenshot({ path: path.join(output, name), fullPage: true }); report.screenshots.push(name);
  }
  async function heal(amount) {
    await form('storage-heal').locator('[name="amount"]').fill(String(amount));
    await action('heal', () => form('storage-heal').getByRole('button', { name: '应用治疗', exact: true }).click());
  }
  async function verifyFacts(current, expected) {
    assert.equal(current.storage.state, 'ACTIVE');
    assert.equal(current.storage.runId, fixture.runId, 'Recovery must keep the exact retained run');
    assert.deepEqual(durableCampaign(current.campaign), expected, 'Facts, identities, revision and history must survive unchanged');
    await equalText('#storage-campaign-name', fixtureName);
    await equalText('#storage-characters .hp', `${expected.characters[0].currentHp} / 12`);
    await page.waitForFunction(count => document.querySelectorAll('#storage-history > li').length === count, expected.history.length);
  }

  try {
    const response = await page.goto(`${ORIGIN}/host/storage`, { waitUntil: 'networkidle', timeout: HTTP_TIMEOUT });
    assert.equal(response.status(), 200);
    const initial = await state();
    assert.equal(initial.campaign, null, 'No implicit activation is permitted');
    assert.equal(await form('storage-active').isVisible(), false, 'Refusing an already active run');
    await form('storage-idle').waitFor({ state: 'visible' });
    if (preparing) {
      assert.equal(initial.storage.state, 'IDLE', 'Prepare requires idle disposable storage');
      assert.deepEqual(initial.storage.residualRunIds, [], 'Refusing unknown residual runs');
      assert.equal(initial.storage.runId, '');
      await readiness('IDLE');
      const baselineFiles = await savedFiles(initial);
      report.checks.push('IDLE root contains only exact known browser fixture names; baseline file identities and bytes retained');
      await page.locator('#storage-modules input[value="srd51-healing@1"]').check();
      await page.locator('#storage-modules input[value="srd51-runtime@1"]').uncheck();
      await form('storage-create').locator('[name="name"]').fill(fixtureName);
      await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
      const created = await state();
      assert.equal(created.campaign.name, fixtureName);
      fixture = { schema: 1, origin: ORIGIN, kind, name: fixtureName, runId: created.storage.runId,
        campaignId: created.campaign.campaignId, epoch: created.storage.epoch, baselineFiles };
      report.ownedFixture = { name: fixtureName, runId: fixture.runId, campaignId: fixture.campaignId };
      await fs.writeFile(path.join(output, 'fixture-progress.json'), json(fixture), { flag: 'wx' });
      await form('storage-character').locator('[name="name"]').fill('恢复验收艾拉');
      await form('storage-character').locator('[name="maximumHp"]').fill('12');
      await form('storage-character').locator('[name="currentHp"]').fill('4');
      await action('characters', () => form('storage-character').getByRole('button', { name: '创建角色', exact: true }).click());
      await heal(5);
      await equalText('#storage-characters .hp', '9 / 12');
      const save = await action('save', () => form('storage-save').click());
      assert.ok(save.body.savedFile);
      fixture.manualSaveFile = save.body.savedFile;
      fixture.savedCampaign = durableCampaign((await state()).campaign);
      assert.equal(fixture.savedCampaign.history.length, 2);
      assert.deepEqual(fixture.savedCampaign.rules, ['srd51-vitality@1', 'srd51-healing@1']);
      assert.equal(fixture.savedCampaign.characters[0].currentHp, 9);
      assert.equal(fixture.savedCampaign.history[1].beforeHp, 4);
      assert.equal(fixture.savedCampaign.history[1].afterHp, 9);
      assert.equal(fixture.savedCampaign.history[1].amount, 5);
      assert.equal(fixture.savedCampaign.history[1].applied, 5);
      // The discard case deliberately leaves progress beyond its accepted manual save.
      if (kind === 'discard') await heal(1);
      const prepared = await state();
      fixture.expectedCampaign = durableCampaign(prepared.campaign);
      fixture.savedFiles = await savedFiles(prepared);
      report.epochs = { beforeRestart: prepared.storage.epoch };
      report.savedFilesBeforeRestart = fixture.savedFiles;
      assert.equal(prepared.storage.runId, fixture.runId);
      assert.equal(prepared.storage.epoch, fixture.epoch);
      await verifyFacts(prepared, fixture.expectedCampaign);
      assert.equal(fixture.expectedCampaign.characters[0].currentHp, kind === 'discard' ? 10 : 9);
      assert.equal(fixture.expectedCampaign.history.length, kind === 'discard' ? 3 : 2);
      for (const original of baselineFiles) assert.deepEqual(fixture.savedFiles.find(file => file.fileKey === original.fileKey), original);
      await fs.writeFile(manifestPath, json(fixture), { flag: 'wx' });
      await screenshot(`${mode}-active.png`);
      report.checks.push('Real forms created 4/12, healed 5, saved 9/12; exact run, epoch, campaign facts/history and all saved bytes retained');
      if (kind === 'discard') report.checks.push('One additional unsaved heal leaves 10/12 for the discard boundary');
      report.next = `Stop or terminate only the exact external candidate process, restart the same base/root, then run ${kind} with this directory`;
    } else {
      assert.equal(initial.storage.state, 'RESIDUAL');
      assert.equal(initial.storage.runId, '');
      assert.deepEqual(initial.storage.residualRunIds, [fixture.runId], 'Only the exact retained fixture may be recovered/discarded');
      await readiness('RESIDUAL');
      assert.deepEqual(await form('storage-residuals').locator('option').evaluateAll(options => options.map(option => option.value)), [fixture.runId]);
      await form('storage-residuals').selectOption(fixture.runId);
      report.ownedFixture = { name: fixtureName, runId: fixture.runId, campaignId: fixture.campaignId };
      await screenshot(`${mode}-residual.png`);
      if (kind === 'recover') {
        const accepted = await action('recover', () => form('storage-recover').getByRole('button', { name: '恢复战役', exact: true }).click());
        assert.equal(accepted.request.get('runId'), fixture.runId);
        const recovered = await state();
        report.epochs = { beforeRestart: fixture.epoch, afterRecovery: recovered.storage.epoch };
        await verifyFacts(recovered, fixture.expectedCampaign);
        assert.notEqual(recovered.storage.epoch, fixture.epoch, 'Explicit recovery must publish a new epoch');
        const recoveredFiles = await savedFiles(recovered);
        report.savedFilesAfterRecovery = recoveredFiles;
        assert.deepEqual(recoveredFiles, fixture.savedFiles, 'Recovery may not replace, create or remove accepted save files');
        await readiness('ACTIVE');
        const revision = String(recovered.campaign.revision);
        currentPhase = 'http-reject-old-epoch';
        const staleRequestId = randomUUID();
        const stale = await context.request.post(`${ORIGIN}/api/host/storage/save`, { timeout: HTTP_TIMEOUT,
          headers: { Origin: ORIGIN, 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
            'X-CSRF-Token': recovered.csrf, 'X-Host-State-Epoch': fixture.epoch,
            'X-Object-Row-Version': revision, 'X-Request-Id': staleRequestId,
            'X-Request-Digest': requestDigest('save', fixture.epoch, revision, {}) }, data: '' });
        report.staleEpochProbe = { requestId: staleRequestId, status: stale.status() };
        assert.equal(stale.status(), 409, 'The retained old epoch must be rejected after recovery');
        const unchanged = await state();
        await verifyFacts(unchanged, fixture.expectedCampaign);
        assert.deepEqual(await savedFiles(unchanged), fixture.savedFiles, 'Old epoch rejection must not publish a save');
        report.checks.push('Explicit UI recovery kept exact facts/history/saved bytes, changed epoch, and rejected the old epoch without writing');
        await screenshot('recover-active.png');
        const ended = await action('end', () => form('storage-end').click());
        assert.ok(ended.body.savedFile);
        const final = await state();
        assert.equal(final.storage.state, 'IDLE'); assert.equal(final.campaign, null);
        assert.deepEqual(final.storage.residualRunIds, []);
        const files = await savedFiles(final);
        report.savedFilesAfterTransition = files;
        report.finalStorage = final.storage;
        for (const original of fixture.savedFiles) assert.deepEqual(files.find(file => file.fileKey === original.fileKey), original);
        assert.deepEqual(files.map(file => file.fileKey).sort(), [...fixture.savedFiles.map(file => file.fileKey), ended.body.savedFile].sort());
        report.finalFile = ended.body.savedFile;
        report.checks.push('Real Save and End form returned IDLE, removed only the recovered run, and preserved every earlier save');
      } else {
        const accepted = await action('discard', async () => {
          const confirmation = page.waitForEvent('dialog').then(async dialog => {
            try {
              assert.equal(dialog.type(), 'confirm');
              assert.equal(dialog.message(), `永久放弃所选运行 ${fixture.runId} 的未保存进度？已存战役文件会保留。`);
              await dialog.accept();
            } catch (failure) { await dialog.dismiss().catch(() => {}); throw failure; }
          });
          await Promise.all([form('storage-discard').click(), confirmation]);
        });
        assert.equal(accepted.request.get('runId'), fixture.runId);
        const discarded = await state();
        assert.equal(discarded.storage.state, 'IDLE'); assert.equal(discarded.campaign, null);
        assert.deepEqual(discarded.storage.residualRunIds, []);
        const remainingFiles = await savedFiles(discarded);
        report.epochs = { beforeRestart: fixture.epoch, afterDiscard: discarded.storage.epoch };
        report.savedFilesAfterTransition = remainingFiles;
        report.finalStorage = discarded.storage;
        assert.deepEqual(remainingFiles, fixture.savedFiles, 'Discard must preserve every exact baseline and fixture saved file byte');
        report.checks.push('Exact UI selection and confirmation discarded only the retained residual; every saved identity, name, history tail and byte remained unchanged');
      }
      await readiness('IDLE');
      await screenshot(`${mode}-idle.png`);
    }
    assert.deepEqual(report.pageErrors, [], 'Browser JavaScript errors were observed');
    assert.deepEqual(report.cspFailures, [], 'CSP failures were observed');
    report.result = 'PASSED';
  } catch (failure) {
    report.error = failure.message;
    report.failingPhase ||= currentPhase; report.failedRequestId ??= currentAction?.requestId || null;
    await screenshot(`${mode}-failure.png`).catch(() => {});
    throw failure;
  } finally {
    await browser.close();
    await fs.writeFile(reportPath, json(report), { flag: 'wx' });
  }
  console.log(JSON.stringify({ result: report.result, mode, checks: report.checks.length,
    runId: fixture.runId, evidence: reportPath }));
}

main().catch(async failure => {
  report.error ||= failure.message;
  if (outputReady) await fs.writeFile(reportPath, json(report), { flag: 'wx' }).catch(() => {});
  console.error(failure.message); process.exitCode = 1;
});
