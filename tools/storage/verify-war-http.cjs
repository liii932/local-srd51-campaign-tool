#!/usr/bin/env node
'use strict';

// Reads and deliberately rejected commands only. Run browser smoke tests separately
// against a disposable root for accepted business writes and file lifecycle checks.
const assert = require('node:assert/strict');
const http = require('node:http');
const { randomUUID } = require('node:crypto');
const ORIGIN = 'http://127.0.0.1:8080';
const expectedState = process.argv[2] || 'IDLE';
assert.ok(['IDLE', 'ACTIVE', 'RESIDUAL'].includes(expectedState), 'Expected IDLE, ACTIVE or RESIDUAL');
let cookie = '';
let checks = 0;

async function request(path, method = 'GET', headers = {}, body = '') {
  const result = await new Promise((resolve, reject) => {
    const outgoing = http.request(ORIGIN + path, { method, headers: {
      ...(cookie ? { Cookie: cookie } : {}),
      ...(method === 'POST' ? { 'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(body) } : {}), ...headers
    }, timeout: 5000 }, incoming => {
      let text = '';
      incoming.setEncoding('utf8');
      incoming.on('data', chunk => { text += chunk; });
      incoming.on('end', () => resolve({ status: incoming.statusCode, headers: incoming.headers, text }));
    });
    outgoing.on('timeout', () => outgoing.destroy(new Error('HTTP timeout')));
    outgoing.on('error', reject);
    outgoing.end(body);
  });
  for (const value of result.headers['set-cookie'] || []) {
    if (value.startsWith('DNDHOSTSESSION=')) cookie = value.split(';', 1)[0];
  }
  checks++;
  return result;
}

async function expect(path, status, method, headers, body) {
  const response = await request(path, method, headers, body);
  assert.equal(response.status, status, `${method || 'GET'} ${path}`);
  return response;
}

(async () => {
  const health = await expect('/health', 200);
  assert.equal(health.text, 'OK');
  assert.match(health.headers['content-type'], /^text\/plain;.*charset=UTF-8/i);
  const root = await expect('/', 302);
  assert.equal(root.headers.location, '/host', 'The WAR welcome JSP must execute');
  const host = await expect('/host', 303);
  assert.equal(host.headers.location, '/host/storage');
  assert.equal(host.headers['cache-control'], 'no-store');
  assert.equal(host.headers['x-content-type-options'], 'nosniff');
  assert.ok(host.headers['content-security-policy']);
  assert.match((host.headers['set-cookie'] || []).join(';'), /DNDHOSTSESSION=.*Path=\/; HttpOnly; SameSite=Strict/);
  const page = await expect('/host/storage', 200);
  assert.match(page.text, /host-storage\.js/);
  const script = await expect('/host/assets/host-storage.js', 200);
  assert.match(script.headers['content-type'], /javascript/);
  assert.match(script.text, /DND_STORAGE_HTTP_V1/);
  const style = await expect('/host/assets/host-storage.css', 200);
  assert.match(style.headers['content-type'], /text\/css/);
  await expect('/WEB-INF/web.xml', 404);

  const ready = await expect('/api/host/storage/readiness', expectedState === 'RESIDUAL' ? 503 : 200);
  const readiness = JSON.parse(ready.text);
  assert.equal(readiness.mode, 'SQLITE');
  assert.equal(readiness.state, expectedState);
  assert.equal(readiness.ready, expectedState !== 'RESIDUAL');
  if (expectedState === 'RESIDUAL') assert.equal(readiness.code, 'STORAGE_RECOVERY_REQUIRED');
  const before = JSON.parse((await expect('/api/host/storage/state', 200)).text);
  assert.equal(before.status, 'OK');
  assert.equal(before.storage.state, expectedState);
  assert.match(before.csrf, /^[A-Za-z0-9_-]+$/);
  if (expectedState === 'IDLE') {
    assert.equal(before.campaign, null);
    assert.equal(before.storage.epoch, '0');
  }
  const commandHeaders = {
    Origin: ORIGIN, 'X-CSRF-Token': before.csrf,
    'X-Host-State-Epoch': before.storage.epoch,
    'X-Object-Row-Version': before.campaign ? before.campaign.revision : '0',
    'X-Request-Id': randomUUID(), 'X-Request-Digest': '0'.repeat(64)
  };
  await expect('/api/host/storage/create', 403, 'POST', { ...commandHeaders, Origin: 'https://invalid.example' });
  await expect('/api/host/storage/create', 403, 'POST', { ...commandHeaders, 'X-CSRF-Token': 'invalid' });
  await expect('/api/host/storage/create', 409, 'POST', { ...commandHeaders,
    'X-Host-State-Epoch': before.storage.epoch === '0' ? '1' : '0' });
  const digest = await expect('/api/host/storage/create', 400, 'POST', commandHeaders,
    'name=Rejected&modules=srd51-vitality%401');
  assert.equal(JSON.parse(digest.text).code, 'INVALID_REQUEST_DIGEST');
  for (const value of ['localhost:8080', '[::1]:8080', 'invalid.example']) {
    await expect('/host', 404, 'GET', { Host: value });
  }
  await expect('/host', 405, 'PUT');
  await expect('/api/host/rules', 404);
  const legacy = await expect('/api/host/campaigns', 409, 'POST');
  assert.equal(JSON.parse(legacy.text).code, 'STORAGE_MODE_REQUIRED');
  await expect('/display', 404);
  await expect('/api/public/state', 404);
  const after = JSON.parse((await expect('/api/host/storage/state', 200)).text);
  assert.deepEqual(after, before, 'Readiness and rejected commands must not change business state');
  console.log(JSON.stringify({ status: 'PASS', origin: ORIGIN, checks, state: expectedState,
    verified: ['WAR welcome JSP', 'annotated listeners/servlets', 'web.xml filters/session',
      'static resources', 'SQLite readiness', 'CSRF/epoch/digest rejection', 'legacy routes closed'] }));
})().catch(failure => { console.error(failure.message); process.exitCode = 1; });
