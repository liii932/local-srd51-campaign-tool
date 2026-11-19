'use strict';

// Observe only browser traffic: read-only API probes must not satisfy the UI's session refresh.
const assert = require('node:assert/strict');
const ORIGIN = 'http://127.0.0.1:8080';

const waitForStorageReady = page => page.locator('#storage-workspace[aria-busy="false"]:enabled').waitFor({state: 'attached'});

function waitForExactText(page, selector, expected) {
  const escaped = expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return page.locator(selector).filter({hasText: new RegExp('^\\s*' + escaped + '\\s*$')}).waitFor({state: 'attached'});
}

function waitForHistoryCount(page, expected) {
  const selector = expected === 0 ? '#storage-history:not(:has(> li))'
    : '#storage-history > li:nth-of-type(' + expected + '):last-of-type';
  return page.locator(selector).waitFor({state: 'attached'});
}

// Card browsing is independent of the working form. Never refresh state to select a card.
async function showCharacter(page, source, id) {
  assert.ok(['RULE', 'MANUAL'].includes(source), `Unknown character source ${source}`);
  const cards = page.locator('#character-browser-scroll [data-character-source]');
  const matches = () => cards.evaluateAll((rows, expected) => rows.some(row =>
    row.dataset.characterSource === expected.source && row.dataset.characterId === expected.id), {source, id});
  if (await matches()) return;
  const previous = page.locator('#character-browser-previous'), next = page.locator('#character-browser-next');
  while (await previous.isEnabled()) await previous.click();
  for (;;) {
    if (await matches()) return;
    assert.ok(await next.isEnabled(), `Character ${source}:${id} is not in the browser`);
    const position = await page.locator('#character-browser-position').textContent();
    await next.click();
    assert.notEqual(await page.locator('#character-browser-position').textContent(), position, 'Next must advance the browser');
  }
}

async function readHistory(page) {
  const overlay = page.locator('#campaign-log-overlay');
  const wasOpen = await overlay.isVisible();
  if (!wasOpen) await page.locator('#campaign-log-toggle').click();
  const text = await page.locator('#storage-history').innerText();
  if (!wasOpen) await page.locator('#campaign-log-close').click();
  return text;
}

async function observeStorageAction(page, name, trigger, timing, timeout = 120000, phase = () => {}) {
  const started = Date.now(), posts = [], states = [];
  let resolvePost, resolveState;
  const post = new Promise(resolve => { resolvePost = resolve; });
  const state = new Promise(resolve => { resolveState = resolve; });
  async function within(promise, label) {
    let timer;
    try {
      return await Promise.race([promise, new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`Timed out waiting for ${label}`)), timeout);
      })]);
    } finally { clearTimeout(timer); }
  }
  const observe = response => {
    const request = response.request();
    if (request.method() === 'POST' && response.url() === `${ORIGIN}/api/host/storage/${name}`) {
      posts.push(response); timing.postResponseMs = Date.now() - started; timing.postStatus = response.status();
      timing.requestId = request.headers()['x-request-id'] || null;
      const fields = new URLSearchParams(request.postData());
      if (fields.has('fileKey')) timing.fileKey = fields.get('fileKey');
      if (fields.has('action')) timing.domainAction = fields.get('action');
      resolvePost(response);
    } else if (request.method() === 'GET' && response.url() === `${ORIGIN}/api/host/storage/state`) {
      states.push(response); timing.stateResponseMs = Date.now() - started; timing.stateStatus = response.status();
      resolveState(response);
    }
  };
  page.on('response', observe);
  try {
    phase('trigger'); await trigger();
    phase('post-response'); const response = await within(post, `POST /${name}`);
    phase('post-body'); const body = await response.json();
    timing.postMs = Date.now() - started - (timing.postRequestMs || 0);
    const epoch = response.request().headers()['x-host-state-epoch'];
    timing.epochChanged = Boolean(body.storage) && String(body.storage.epoch) !== epoch;
    timing.stateRefreshRequired = !response.ok() || timing.epochChanged;
    if (timing.stateRefreshRequired) {
      phase('session-refresh'); const refreshed = await within(state, 'GET /state');
      assert.equal(refreshed.status(), 200, `${name} did not refresh its session`);
      await refreshed.finished();
      timing.stateMs = Date.now() - started - (timing.stateRequestMs || 0);
    }
    phase('render');
    await waitForStorageReady(page);
    assert.equal(posts.length, 1, `${name} must submit once without retrying`);
    assert.equal(states.length, timing.stateRefreshRequired ? 1 : 0,
      `${name} must refresh the session only after an epoch change or a failed command`);
    assert.equal(response.status(), 200, `${name} failed: ${JSON.stringify(body)}`);
    assert.equal(body.status, 'OK', `${name} returned an invalid state response`);
    return {body, request: new URLSearchParams(response.request().postData())};
  } finally {
    page.off('response', observe);
  }
}

module.exports = {observeStorageAction, waitForStorageReady, waitForExactText, waitForHistoryCount, showCharacter, readHistory};
