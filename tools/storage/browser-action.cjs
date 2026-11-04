'use strict';

// Observe only browser traffic: read-only API probes must not satisfy the UI's session refresh.
const assert = require('node:assert/strict');
const ORIGIN = 'http://127.0.0.1:8080';

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
    await page.waitForFunction(() => {
      const workspace = document.querySelector('#storage-workspace');
      return workspace && workspace.getAttribute('aria-busy') === 'false' && !workspace.disabled;
    });
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

module.exports = {observeStorageAction};
