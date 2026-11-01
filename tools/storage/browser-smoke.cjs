#!/usr/bin/env node
'use strict';

// Run only against tools/storage/run-local.sh with a disposable, newly created private root.
// This script creates and ends fixture campaigns through the UI. It refuses existing saves/runs.
// NODE_PATH supplies an external Playwright installation; no browser packages enter the WAR.
// Usage: node tools/storage/browser-smoke.cjs /absolute/empty/screenshot-directory
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

const ORIGIN = 'http://127.0.0.1:8080';
const CAMPAIGN = '浏览器验收营地';
const BASE_CAMPAIGN = '基础生命值验收';
const FULL_CAMPAIGN = '完整领域浏览器验收';
const output = process.argv[2];
if (process.argv.length !== 3 || !output || !path.isAbsolute(output)) {
  console.error('Usage: node tools/storage/browser-smoke.cjs /absolute/empty/screenshot-directory');
  console.error('Target must be tools/storage/run-local.sh using a disposable, newly created root.');
  process.exit(2);
}

const report = { origin: ORIGIN, result: 'FAILED', checks: [], screenshots: [], pageErrors: [], cspFailures: [] };

async function main() {
  console.log('Browser phase: opening the isolated campaign workbench');
  await fs.mkdir(output, { recursive: true });
  assert.equal((await fs.readdir(output)).length, 0, 'Screenshot output directory must be empty');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, locale: 'zh-CN' });
  const page = await context.newPage();
  page.setDefaultTimeout(30000);
  let disposableConfirmed = false;
  let ownsActiveFixture = false;

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
  const equalText = async (selector, expected) => {
    await page.waitForFunction(({ selector, expected }) =>
      document.querySelector(selector)?.textContent.trim() === expected, { selector, expected });
  };
  const historyCount = async expected => {
    await page.waitForFunction(expected => document.querySelectorAll('#storage-history > li').length === expected, expected);
  };

  async function action(name, trigger) {
    const observed = Promise.all([
      page.waitForResponse(response => response.request().method() === 'POST'
        && response.url() === `${ORIGIN}/api/host/storage/${name}`),
      page.waitForResponse(response => response.request().method() === 'GET'
        && response.url() === `${ORIGIN}/api/host/storage/state`)
    ]).catch(error => ({ observationFailure: error }));
    await trigger();
    const responses = await observed;
    if (responses.observationFailure) throw responses.observationFailure;
    const [response, stateResponse] = responses;
    const body = await response.json();
    assert.equal(response.status(), 200, `${name} failed: ${JSON.stringify(body)}`);
    assert.equal(stateResponse.status(), 200, `${name} did not refresh its session epoch`);
    await stateResponse.finished();
    // The UI sets its status only after parsing/rendering the refreshed GET and before clearing busy.
    const expectedMessage = name === 'end' ? '战役已保存并结束，可以再次打开。'
      : name === 'save' ? '战役已保存。' : name === 'import' ? '文件已核验并导入，请选择打开。'
      : name === 'delete' ? '所选文件已删除。' : '战役已更新。';
    await equalText('#storage-message', expectedMessage);
    return body;
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

  async function createCharacter(name, maximum, current) {
    await form('storage-character').locator('[name="name"]').fill(name);
    await form('storage-character').locator('[name="maximumHp"]').fill(String(maximum));
    await form('storage-character').locator('[name="currentHp"]').fill(String(current));
    await action('characters', () => form('storage-character').getByRole('button', { name: '创建角色', exact: true }).click());
    await equalText('#storage-characters .hp', `${current} / ${maximum}`);
  }

  async function endFixture() {
    const ended = await action('end', () => form('storage-end').click());
    await visible('#storage-idle');
    assert.equal(await form('storage-active').isVisible(), false);
    ownsActiveFixture = false;
    assert.ok(ended.savedFile, 'Ending must return the exact accepted final save');
    return ended.savedFile;
  }

  try {
    const initial = await page.goto(`${ORIGIN}/host/storage`, { waitUntil: 'networkidle' });
    assert.equal(initial.status(), 200, 'The isolated storage page must be available');
    await visible('#storage-idle');
    await equalText('#storage-message', '创建、打开或恢复一个战役。');
    assert.equal(await form('storage-active').isVisible(), false, 'Refusing an existing active run');
    assert.equal(await form('storage-files').locator('option').count(), 0, 'Refusing a root with saved campaigns');
    assert.equal(await form('storage-residuals').locator('option').count(), 0, 'Refusing a root with residual runs');
    assert.equal(await form('storage-create').getByRole('button', { name: '创建战役', exact: true }).isEnabled(), true,
      'Target must be idle, using a disposable new root from tools/storage/run-local.sh');
    disposableConfirmed = true;
    report.checks.push('Fresh disposable root verified before the first business write');
    console.log('Browser phase: fresh private root verified; testing vitality forms');

    const base = page.locator('#storage-modules input[value="srd51-vitality@1"]');
    const healing = page.locator('#storage-modules input[value="srd51-healing@1"]');
    const runtime = page.locator('#storage-modules input[value="srd51-runtime@1"]');
    assert.equal(await base.isChecked(), true);
    assert.equal(await base.isDisabled(), true);
    await healing.check();
    await runtime.uncheck();
    await form('storage-create').locator('[name="name"]').fill(CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true;
    await visible('#storage-active');
    await equalText('#storage-campaign-name', CAMPAIGN);
    await createCharacter('艾拉', 12, 4);
    await historyCount(1);
    await form('storage-heal').locator('[name="amount"]').fill('5');
    await action('heal', () => form('storage-heal').getByRole('button', { name: '应用治疗', exact: true }).click());
    await equalText('#storage-characters .hp', '9 / 12');
    await historyCount(2);
    assert.match(await form('storage-history').innerText(), /4 → 9/);
    report.checks.push('UI created 12/4 character and applied healing 5: 9/12 with exactly two history entries');
    await screenshot('01-healed-wide.png', 1440, 1050);
    await screenshot('02-healed-mobile.png', 390, 844);

    await action('save', () => form('storage-save').click());
    const finalSave = await endFixture();
    await form('storage-files').selectOption(finalSave);
    await action('open', () => form('storage-open').getByRole('button', { name: '打开战役', exact: true }).click());
    ownsActiveFixture = true;
    await visible('#storage-active');
    await equalText('#storage-campaign-name', CAMPAIGN);
    await equalText('#storage-characters .hp', '9 / 12');
    await historyCount(2);
    report.checks.push('Selected exact final save through UI; reopened HP and both history entries match');
    await screenshot('03-reopened-wide.png', 1440, 1050);
    await endFixture();
    await healing.check(); await runtime.check();
    await form('storage-create').locator('[name="name"]').fill(FULL_CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true; await visible('#storage-domain');
    assert.equal(await form('storage-vitality').isVisible(), false);
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
    await page.locator('[data-pane="world"]').click();
    await form('domain-node').locator('[name="name"]').fill('石桥营地');
    await action('domain', () => form('domain-node').getByRole('button', { name: '建立地点', exact: true }).click());
    await action('domain', () => form('domain-position').getByRole('button', { name: '移动到地点', exact: true }).click());
    await form('domain-item').locator('[name="name"]').fill('火把');
    await form('domain-item').locator('[name="quantity"]').fill('3');
    await action('domain', () => form('domain-item').getByRole('button', { name: '保存物品记录', exact: true }).click());
    await form('domain-node').locator('[name="name"]').fill('桥后哨所');
    await action('domain', () => form('domain-node').getByRole('button', { name: '建立地点', exact: true }).click());
    await form('domain-note').locator('[name="text"]').fill('守桥人与队伍在石桥会合。');
    await action('domain', () => form('domain-note').getByRole('button', { name: '记入战役历史', exact: true }).click());
    await page.locator('[data-pane="actions"]').click();
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
    await form('domain-check').locator('[name="successNode"]').selectOption({label: '桥后哨所'});
    await form('domain-check').locator('[name="failureNode"]').selectOption({label: '石桥营地'});
    await form('domain-check').locator('[name="successMessage"]').fill('成功分支：携火把抵达哨所。');
    await form('domain-check').locator('[name="failureMessage"]').fill('失败分支：消耗火把退回营地。');
    await form('domain-check').locator('[name="difficultyClass"]').fill('0');
    await action('domain', () => form('domain-check').getByRole('button', { name: '服务器掷骰检定', exact: true }).click());
    assert.match(await form('storage-history').innerText(), /成功分支：携火把抵达哨所/);
    assert.match(await form('storage-history').innerText(), /火把 数量 3 → 5/);
    await form('domain-check').locator('[name="difficultyClass"]').fill('30');
    await action('domain', () => form('domain-check').getByRole('button', { name: '服务器掷骰检定', exact: true }).click());
    assert.match(await form('storage-history').innerText(), /失败分支：消耗火把退回营地/);
    assert.match(await form('storage-history').innerText(), /火把 数量 5 → 4/);
    report.checks.push('The same check form declared both branches before server dice, then applied HP, inventory, location and message for forced success and forced failure');
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
    await form('manual-party').locator('[name="nodeId"]').selectOption({label: '石桥营地'});
    await action('domain', () => form('manual-party').getByRole('button', { name: '移动队伍', exact: true }).click());
    await form('manual-encounter').locator('[name="nodeId"]').selectOption({label: '石桥营地'});
    for (const row of await form('manual-encounter-roster').locator(':scope > div').all()) await row.locator('[name="participantNodeId"]').selectOption({label: '石桥营地'});
    await action('domain', () => form('manual-encounter').getByRole('button', { name: '开始简易遭遇', exact: true }).click());
    await form('manual-check').locator('[name="characterId"]').selectOption({label: '自由记录员'});
    await form('manual-check').locator('[name="checkType"]').selectOption('MANUAL');
    await form('manual-check').locator('[name="manualModifier"]').fill('0');
    await form('manual-check').locator('[name="difficultyClass"]').fill('0');
    async function manualEffect(branch, kind, values) {
      await page.locator(`[data-add-manual-effect="${branch}"]`).click();
      const row = page.locator(`#manual-${branch}-effects > fieldset`).last(); await row.locator('[name="kind"]').selectOption(kind);
      if (kind !== 'MESSAGE') await row.locator('[name="targetId"]').selectOption({label: '自由记录员'});
      for (const [name, value] of Object.entries(values)) {
        const input = row.locator(`[name="${name}"]`);
        if (name === 'nodeId') await input.selectOption({label: value}); else await input.fill(value);
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
    await manualEffect('failure', 'POSITION', {nodeId: '桥后哨所'});
    await manualEffect('failure', 'MESSAGE', {text: '豁免失败，退至桥后哨所。'});
    await action('domain', () => form('manual-check').getByRole('button', { name: '服务器掷骰并应用后果', exact: true }).click());
    assert.equal((await freeCard().locator('.hp').textContent()).trim(), '5 / 12');
    assert.match(await form('storage-history').innerText(), /豁免失败，退至桥后哨所/);
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
    await action('domain', () => form('manual-encounter-end').click());
    report.checks.push('Free cards edited HP, Dexterity, class level and saving proficiency; MANUAL and saving checks applied ordered outcomes, inventory archive/restore and mixed simple encounter positions');
    console.log('Browser phase: free cards, ordered checks and simple encounter verified; testing rich battle and files');
    await screenshot('07-free-card-wide.png', 1440, 1050);
    await screenshot('08-free-card-mobile.png', 390, 844);
    await page.locator('[data-pane="combat"]').click();
    await action('domain', () => form('domain-combat-start').getByRole('button', { name: '开始战斗', exact: true }).click());
    assert.match(await form('domain-combat-summary').innerText(), /当前/);
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
    console.log('Browser phase: full file reopened, downloaded, deleted and reimported; testing base-only selection');

    await runtime.uncheck(); await healing.uncheck();
    await form('storage-create').locator('[name="name"]').fill(BASE_CAMPAIGN);
    await action('create', () => form('storage-create').getByRole('button', { name: '创建战役', exact: true }).click());
    ownsActiveFixture = true;
    await visible('#storage-active');
    await equalText('#storage-campaign-name', BASE_CAMPAIGN);
    await createCharacter('基础角色', 10, 3);
    assert.equal(await form('storage-heal').getByRole('button', { name: '应用治疗', exact: true }).isDisabled(), true);
    await equalText('#storage-healing-note', '此战役未启用治疗模块。');
    await historyCount(1);
    report.checks.push('Base-only rule selection leaves healing disabled after character creation');
    await screenshot('04-base-only-wide.png', 1440, 1050);
    await endFixture();
    assert.equal(await form('storage-residuals').locator('option').count(), 0, 'Fixture runs must end without residuals');
    assert.deepEqual(report.pageErrors, [], 'Browser JavaScript errors were observed');
    assert.deepEqual(report.cspFailures, [], 'CSP failures were observed');
    report.checks.push('All fixture runs ended through Save and End; no pageerror or CSP failures');
    report.result = 'PASSED';
  } catch (error) {
    report.error = error.message;
    if (disposableConfirmed) {
      await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
      if (ownsActiveFixture && await form('storage-active').isVisible().catch(() => false)) {
        const name = await form('storage-campaign-name').textContent().catch(() => '');
        if ([CAMPAIGN, BASE_CAMPAIGN, FULL_CAMPAIGN].includes(name.trim())) {
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
