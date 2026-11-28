#!/usr/bin/env node
'use strict';

// Start run-player-local.sh separately with a new private root. This script
// writes only through real Host forms; it never starts, deploys or removes a server.
// Usage: node tools/storage/browser-workspace.cjs /absolute/empty/evidence --fresh-root /absolute/private-root
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('playwright');
const {observeStorageAction, waitForStorageReady, showCharacter, readHistory} = require('./browser-action.cjs');
const {selectStandardRules} = require('./browser-rule-library.cjs');
const {createNode, focusSpace, placeAtFocus, beginBattle} = require('./browser-spaces.cjs');

const ORIGIN = 'http://127.0.0.1:8080';
const NAME = '三栏工作区浏览器验收';
const [output, acknowledgement, storageRoot] = process.argv.slice(2);
assert.ok(process.argv.length === 5 && acknowledgement === '--fresh-root'
  && path.isAbsolute(output || '') && path.isAbsolute(storageRoot || ''),
'Usage: node browser-workspace.cjs /absolute/empty/evidence --fresh-root /tmp/private-root');
const report = {schema: 1, result: 'FAILED', checks: [], actions: [], screenshots: [], layouts: [],
  pageErrors: [], cspFailures: [], busyChecks: [], storageReads: 0, storagePosts: 0};
const json = value => JSON.stringify(value, null, 2) + '\n';
let browser, page, current, owned = false, outputReady = false;

async function main() {
  const root = await fs.realpath(storageRoot);
  const repository = path.resolve(__dirname, '../..');
  const relativeRoot = path.relative(repository, root);
  assert.ok(relativeRoot.startsWith('..' + path.sep) || path.isAbsolute(relativeRoot), 'Managed storage must be outside the repository');
  assert.equal((await fs.lstat(storageRoot)).isSymbolicLink(), false);
  assert.equal((await fs.stat(root)).mode & 0o777, 0o700, 'The disposable root must be owner-only');
  assert.deepEqual((await fs.readdir(root)).sort(), ['instance.lock', 'runs', 'saves']);
  for (const directory of ['runs', 'saves']) assert.deepEqual(await fs.readdir(path.join(root, directory)), [], `Fresh ${directory} must be empty`);
  assert.ok(!path.resolve(output).startsWith(root + path.sep), 'Evidence must be outside managed storage');
  await fs.mkdir(output, {recursive: true});
  assert.deepEqual(await fs.readdir(output), [], 'Evidence directory must be empty');
  outputReady = true; report.storageRoot = root;
  const launch = {headless: true};
  if (process.env.DND_BROWSER_EXECUTABLE) {
    const executable = process.env.DND_BROWSER_EXECUTABLE;
    assert.ok(path.isAbsolute(executable) && (await fs.stat(executable)).isFile());
    launch.executablePath = executable;
  }
  browser = await chromium.launch(launch); report.browser = browser.version();
  page = await browser.newPage({viewport: {width: 1440, height: 1050}, locale: 'zh-CN'});
  page.setDefaultTimeout(30000); page.setDefaultNavigationTimeout(120000);
  page.on('pageerror', error => report.pageErrors.push(error.message));
  page.on('console', event => {
    if (/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(event.text())) report.cspFailures.push(event.text());
  });
  page.on('request', request => {
    if (request.url() === ORIGIN + '/api/host/storage/state') report.storageReads++;
    if (request.method() === 'POST' && request.url().startsWith(ORIGIN + '/api/host/storage/')) report.storagePosts++;
  });
  await page.route(ORIGIN + '/api/host/storage/*', async route => {
    const request = route.request();
    if (request.method() === 'POST' && !new URL(request.url()).pathname.includes('/rule-')) {
      report.busyChecks.push(await page.evaluate(() => ({
        disabled: document.querySelector('#storage-workspace').disabled,
        ariaBusy: document.querySelector('#storage-workspace').getAttribute('aria-busy'),
        feedback: document.querySelector('#storage-message').classList.contains('is-busy'),
        outside: !document.querySelector('#storage-workspace').contains(document.querySelector('#storage-message')),
        pagerOutside: !document.querySelector('#storage-workspace').contains(document.querySelector('#character-browser-next')),
        playersOutside: !document.querySelector('#storage-workspace').contains(document.querySelector('#host-players')),
        playersRefreshEnabled: !document.querySelector('#host-players-refresh').disabled
      })));
    }
    await route.continue();
  });
  const form = id => page.locator('#' + id);
  const field = (id, name) => form(id).locator(`[name="${name}"]`);
  const pane = name => page.locator(`[data-pane="${name}"]`).click();
  const submit = id => action('domain', () => form(id).locator('button[type="submit"], button:not([type])').click());
  const domain = () => current.campaign.domain;
  const card = name => domain().characters.find(character => character.name === name);
  const visibleCard = () => page.locator('#character-browser-scroll [data-character-source]');
  const identity = () => visibleCard().evaluateAll(rows => rows.map(row => [row.dataset.characterSource, row.dataset.characterId]));
  const first = form('character-browser-previous'), next = form('character-browser-next');

  async function action(name, trigger) {
    const timing = {name}; report.actions.push(timing);
    current = (await observeStorageAction(page, name, trigger, timing)).body;
    return current;
  }
  async function rule(id, name, key) {
    const select = field(id, name);
    const value = await select.locator('option').evaluateAll((options, key) => options.find(option => option.value.endsWith('/' + key))?.value, key);
    assert.ok(value, `Missing rule ${key}`); await select.selectOption(value);
  }
  async function verifyActivePlayers() {
    await page.locator('#host-players-invite:enabled').waitFor();
    assert.doesNotMatch(await form('host-players-message').innerText(), /请先创建|暂不可用|尚未配置/,
      'A configured active campaign must not retain an IDLE or unavailable player message');
  }
  async function screenshot(name, width = 1440, height = 1050) {
    await page.setViewportSize({width, height});
    const layout = await page.evaluate(() => {
      const box = id => {
        const rect = document.getElementById(id).getBoundingClientRect();
        return {x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom};
      };
      return {width: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth,
        center: box('workspace-center'), left: box('workspace-left'), right: box('workspace-right')};
    });
    assert.ok(layout.document <= width && layout.body <= width, `Page overflow: ${JSON.stringify(layout)}`);
    if (width > 900) {
      assert.ok(layout.left.right <= layout.center.x + 1 && layout.center.right <= layout.right.x + 1, 'Wide workspace must have three columns');
    } else {
      assert.ok(layout.center.bottom <= layout.left.y + 1 && layout.left.bottom <= layout.right.y + 1, 'Narrow workspace order must be center, left, right');
    }
    report.layouts.push({name, ...layout});
    await page.screenshot({path: path.join(output, name), fullPage: true}); report.screenshots.push(name);
  }
  async function formSnapshot() {
    return form('workspace-center-scroll').evaluate(root => ({
      pane: Array.from(root.querySelectorAll('[data-panel]')).filter(panel => !panel.hidden).map(panel => panel.dataset.panel),
      fields: Array.from(root.querySelectorAll('input, select, textarea')).map(input => ({id: input.id, name: input.name,
        value: input.value, checked: input.checked, selected: input.multiple ? Array.from(input.selectedOptions, option => option.value) : null}))
    }));
  }
  async function verifyLog(closeWithEscape, mobile = false) {
    const scroll = form('workspace-center-scroll');
    await scroll.evaluate(element => { element.scrollTop = (element.scrollHeight - element.clientHeight) * 0.55; });
    const dimensions = () => scroll.evaluate(element => ({top: element.scrollTop, height: element.clientHeight, content: element.scrollHeight}));
    const original = await dimensions(), position = original.top;
    assert.ok(position > 0, 'A long working form is required for scroll restoration');
    const tabPosition = await page.evaluate(() => ({
      tabs: document.querySelector('#storage-workspace .tabs').getBoundingClientRect().top,
      scroll: document.querySelector('#workspace-center-scroll').getBoundingClientRect().top
    }));
    assert.ok(Math.abs(tabPosition.tabs - tabPosition.scroll) <= 1, 'Sticky tabs must meet the scrollport without exposing content above them');
    await form('campaign-log-toggle').click();
    await form('campaign-log-overlay').waitFor({state: 'visible'});
    assert.equal(await form('campaign-log-toggle').getAttribute('aria-expanded'), 'true');
    assert.equal(await scroll.evaluate(element => element.inert), true);
    assert.deepEqual(await dimensions(), original, 'Opening the log must preserve the underlying scroll geometry');
    assert.ok((await form('storage-history').innerText()).length > 0);
    const history = form('storage-history');
    assert.equal(await history.getAttribute('role'), 'list');
    assert.equal(await history.evaluate(element => getComputedStyle(element).listStyleType), 'none', 'Sequence labels must not have duplicate ordered-list numbering');
    const historySize = await history.evaluate(element => ({height: element.clientHeight, content: element.scrollHeight}));
    assert.ok(historySize.content > historySize.height, 'The record fixture must exercise a long independent list');
    await history.evaluate(element => { element.scrollTop = element.scrollHeight; });
    assert.ok(await history.evaluate(element => element.scrollTop) > 0, 'The record list must scroll independently');
    assert.deepEqual(await dimensions(), original, 'Scrolling the record must not move or resize the working form');
    const bounds = await page.evaluate(() => {
      const center = document.getElementById('workspace-center').getBoundingClientRect();
      const overlay = document.getElementById('campaign-log-overlay').getBoundingClientRect();
      return {inside: overlay.left >= center.left - 1 && overlay.right <= center.right + 1 && overlay.top >= center.top - 1 && overlay.bottom <= center.bottom + 1};
    });
    assert.equal(bounds.inside, true, 'The record must stay inside the center workspace');
    if (mobile) await screenshot('07-history-mobile.png', 390, 844);
    else if (!closeWithEscape) await screenshot('05-history-wide.png');
    if (closeWithEscape) await page.keyboard.press('Escape'); else await form('campaign-log-close').click();
    await form('campaign-log-overlay').waitFor({state: 'hidden'});
    assert.equal(await scroll.evaluate(element => element.inert), false);
    assert.equal(await form('campaign-log-toggle').evaluate(element => element === document.activeElement), true, 'Closing must restore the opener focus');
    assert.ok(Math.abs(await scroll.evaluate(element => element.scrollTop) - position) <= 1, 'Closing must restore the exact working position');
  }

  try {
    const loaded = page.waitForResponse(response => response.url() === ORIGIN + '/api/host/storage/state');
    await page.goto(ORIGIN + '/host/storage'); current = await (await loaded).json(); await waitForStorageReady(page);
    assert.equal(current.storage.state, 'IDLE'); assert.equal(current.campaign, null);
    assert.deepEqual(current.savedCampaigns, []); assert.deepEqual(current.storage.residualRunIds, []);
    assert.equal(await form('rule-library').getAttribute('open'), null, 'Rule library starts collapsed');
    assert.equal(await form('host-players').isVisible(), true);
    await form('host-players-message').filter({hasText: /请先创建、打开或恢复一个战役/}).waitFor();
    assert.equal(await form('character-browser-empty').isVisible(), true);
    assert.equal(await first.isEnabled(), false); assert.equal(await next.isEnabled(), false);
    await screenshot('00-idle-wide.png');
    await selectStandardRules(page);
    await field('storage-create', 'name').fill(NAME);
    await action('create', () => form('storage-create').locator('button[type="submit"]').click()); owned = true;
    await verifyActivePlayers();
    assert.deepEqual(await fs.readdir(path.join(root, 'runs')), [current.storage.runId], 'The real HTTP server must use the supplied disposable root');
    const directActive = page.waitForResponse(response => response.url() === ORIGIN + '/api/host/storage/state');
    await page.reload(); current = await (await directActive).json(); await waitForStorageReady(page);
    await verifyActivePlayers();
    await form('host-players-message').filter({hasText: /逐人核对加入、分配角色/}).waitFor();
    assert.equal(await form('character-browser-empty').isVisible(), true);
    assert.equal(await visibleCard().count(), 0);
    await screenshot('01-empty-wide.png');
    report.checks.push('Fresh private root, true IDLE, empty active roster and collapsed rule library');

    await field('domain-create', 'name').fill('工作区战士'); await rule('domain-create', 'classKey', 'class.fighter');
    await rule('domain-create', 'fightingStyleKey', 'feature.fighter_defense'); await submit('domain-create');
    const fighter = card('工作区战士').id;
    assert.deepEqual(await identity(), [['RULE', fighter]]);
    assert.equal(await first.isEnabled(), false); assert.equal(await next.isEnabled(), false);
    await screenshot('02-single-wide.png');
    await pane('manual'); await field('manual-create', 'name').fill('工作区自由守卫');
    await field('manual-create', 'kind').selectOption('NPC');
    await field('manual-create', 'template').selectOption('guard'); await submit('manual-create');
    const manual = domain().manual.characters[0].current.id;
    for (const [source, id] of [['RULE', fighter], ['MANUAL', manual], ['RULE', fighter], ['MANUAL', manual]]) {
      await showCharacter(page, source, id); assert.deepEqual(await identity(), [[source, id]]);
    }
    assert.equal((await form('character-browser-position').textContent()).trim(), '2 / 2');
    await pane('people'); await field('domain-create', 'name').fill('工作区法师'); await field('domain-create', 'kind').selectOption('NPC');
    await rule('domain-create', 'classKey', 'class.wizard'); await submit('domain-create');
    const wizard = card('工作区法师').id;
    assert.deepEqual(await identity(), [['MANUAL', manual]], 'Insertion before the selected free card must preserve its identity');
    assert.equal((await form('character-browser-position').textContent()).trim(), '3 / 3', 'The selected identity must follow its changed ordinal');
    const ordered = [...domain().characters.map(character => ['RULE', character.id]),
      ...domain().manual.characters.map(history => ['MANUAL', history.current.id])];
    for (const [source, id] of ordered) {
      await showCharacter(page, source, id); assert.deepEqual(await identity(), [[source, id]]);
      assert.equal(await visibleCard().count(), 1);
    }
    assert.equal(await first.isEnabled(), true); assert.equal(await next.isEnabled(), false);
    await screenshot('04-manual-wide.png');
    await showCharacter(page, ...ordered[0]);
    assert.equal(await first.isEnabled(), false); assert.equal(await next.isEnabled(), true);
    report.checks.push('Single/first/last boundaries, mixed-source paging and selected free-card identity retained when a new rule card changed its ordinal from 2/2 to 3/3');

    const node = await createNode(page, action, '工作区训练场'); await focusSpace(page, action, 'WORLD', node);
    await placeAtFocus(page, action, [fighter, wizard]);
    await pane('actions');
    for (const id of [fighter, wizard]) {
      await field('domain-equipment', 'characterId').selectOption(id); await field('domain-equipment', 'kind').selectOption('ITEM_ACQUIRED');
      await rule('domain-equipment', 'item', 'item.dagger'); await submit('domain-equipment');
      const item = domain().characters.find(character => character.id === id).state.equipment.find(item => item.item.ruleKey === 'item.dagger').itemId;
      await field('domain-equipment', 'kind').selectOption('ITEM_EQUIPPED'); await field('domain-equipment', 'itemId').selectOption(item);
      await field('domain-equipment', 'location').selectOption('MAIN_HAND'); await submit('domain-equipment');
    }
    await beginBattle(page, action, [[fighter, 1, 1, 0], [wizard, 2, 1, 1]]);
    const battle = domain().combats.at(-1).current;
    const actor = battle.initiative[battle.activeOrdinal].characterId;
    const target = actor === fighter ? wizard : fighter;
    await field('domain-combat', 'actorId').selectOption(actor);
    await field('domain-combat', 'kind').selectOption('BEGIN_ATTACK'); await submit('domain-combat');
    await field('domain-combat', 'kind').selectOption('ATTACK'); await field('domain-combat', 'targetId').selectOption(target);
    const weapon = domain().characters.find(character => character.id === actor).state.equipment.find(item => item.location === 'MAIN_HAND').itemId;
    await field('domain-combat', 'itemId').selectOption(weapon);
    const selected = await formSnapshot(), focusText = await form('domain-focus-current').textContent();
    const beforePaging = {reads: report.storageReads, posts: report.storagePosts};
    for (const index of [1, 2, 1, 0, 1, 2, 1]) {
      await showCharacter(page, ...ordered[index]);
      assert.deepEqual(await formSnapshot(), selected, 'Card browsing must preserve every working form field and active pane');
      assert.equal(await form('domain-focus-current').textContent(), focusText, 'Card browsing must preserve the execution focus');
    }
    assert.equal(report.storageReads, beforePaging.reads, 'Card browsing must not request whole campaign state');
    assert.equal(report.storagePosts, beforePaging.posts, 'Card browsing must not write campaign state');
    assert.equal(await field('domain-combat', 'actorId').inputValue(), actor);
    assert.equal(await field('domain-combat', 'targetId').inputValue(), target);
    assert.equal(await field('domain-combat', 'itemId').inputValue(), weapon);
    await pane('people'); await form('workspace-center-scroll').evaluate(element => { element.scrollTop = 0; });
    await showCharacter(page, 'RULE', fighter); await verifyActivePlayers(); await screenshot('03-rule-wide.png');
    await pane('combat'); await showCharacter(page, 'RULE', wizard);
    const selectedCard = await identity();
    await action('save', () => form('storage-save').click());
    assert.deepEqual(await identity(), selectedCard, 'A successful state render must retain card identity');
    const refresh = page.waitForResponse(response => response.url() === ORIGIN + '/api/host/storage/state');
    await page.evaluate(() => document.dispatchEvent(new Event('dnd:storage-refresh'))); await (await refresh).finished(); await waitForStorageReady(page);
    assert.deepEqual(await identity(), selectedCard, 'A real GET refresh must retain card identity');
    assert.equal(await field('domain-combat', 'actorId').inputValue(), actor);
    assert.equal(await field('domain-combat', 'targetId').inputValue(), target);
    report.checks.push('Repeated independent paging preserved all fields, active attack actor/target/weapon and caused no state read or write; save and real GET retained card identity');

    await pane('actions'); await field('domain-check', 'characterId').selectOption(wizard);
    await form('check-with-effects').check(); await field('domain-check', 'successTarget').selectOption(fighter);
    await field('domain-check', 'successMessage').fill('记录打开后仍保留的检定草稿');
    const draft = await formSnapshot();
    await verifyLog(false); assert.deepEqual(await formSnapshot(), draft);
    await verifyLog(true); assert.deepEqual(await formSnapshot(), draft);
    assert.ok((await readHistory(page)).length > 0);
    await pane('people'); await form('workspace-center-scroll').evaluate(element => { element.scrollTop = 0; });
    await showCharacter(page, 'RULE', fighter); await verifyActivePlayers();
    await screenshot('06-workspace-mobile.png', 390, 844);
    await pane('actions'); await showCharacter(page, 'RULE', wizard);
    await verifyLog(true, true); assert.deepEqual(await formSnapshot(), draft);
    report.checks.push('Center-only record overlay, explicit close and Esc preserved working draft, exact inner scroll and opener focus at desktop and 390px');
    assert.deepEqual(report.pageErrors, []); assert.deepEqual(report.cspFailures, []);
    for (const busy of report.busyChecks) assert.deepEqual(busy, {disabled: true, ariaBusy: 'true', feedback: true, outside: true, pagerOutside: true, playersOutside: true, playersRefreshEnabled: true});
    assert.ok(report.busyChecks.length > 0);
    await action('end', () => form('storage-end').click()); owned = false;
    assert.equal(current.storage.state, 'IDLE'); assert.deepEqual(await fs.readdir(path.join(root, 'runs')), []);
    await form('host-players-message').filter({hasText: /请先创建、打开或恢复一个战役/}).waitFor();
    assert.equal(await form('host-players-invite').isVisible(), false);
    assert.equal(await visibleCard().count(), 0); assert.equal(await form('character-browser-empty').isVisible(), true);
    await screenshot('08-idle-mobile.png', 390, 844);
    report.checks.push('Busy feedback retained independent sidebars; end returned to empty IDLE with no residual run; no JavaScript or CSP errors');
    report.result = 'PASSED';
  } finally {
    if (owned && current?.campaign?.name === NAME) {
      try { await action('end', () => form('storage-end').click()); report.cleanup = 'Owned disposable run ended'; }
      catch (failure) { report.cleanup = failure.message; }
    }
  }
}

main().catch(error => { report.error = error.stack; process.exitCode = 1; }).finally(async () => {
  if (outputReady) {
    if (report.result !== 'PASSED' && page) await page.screenshot({path: path.join(output, 'failure.png'), fullPage: true}).catch(() => {});
    await fs.writeFile(path.join(output, 'workspace-browser-report.json'), json(report));
  }
  await browser?.close();
  console.log(json({result: report.result, checks: report.checks, commands: report.actions.length,
    busyChecks: report.busyChecks.length, screenshots: report.screenshots,
    report: path.join(output, 'workspace-browser-report.json'), error: report.error}));
});
