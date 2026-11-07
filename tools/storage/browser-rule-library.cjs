'use strict';

const assert = require('node:assert/strict');

// Call only after the browser fixture has verified its disposable storage boundary.
async function selectStandardRules(page) {
  const standardKeys = ['srd51_runtime', 'srd51_characters', 'srd51_magic', 'dm_runtime_house_rules'];
  const library = page.locator('#rule-library');
  await page.locator('#rule-members-load').click();
  const memberRow = (list, key, version) => page.locator(`#${list} [data-rule-module="${key}"][data-rule-release="${version}"]`);
  const bundled = page.locator('#rule-bundled [data-rule-module]');
  await bundled.first().waitFor();
  // The bundled inventory lists the standard combination before the alternate house package.
  const members = await bundled.evaluateAll((rows, length) => rows.slice(0, length)
    .map(row => [row.dataset.ruleModule, row.dataset.ruleRelease]), standardKeys.length);
  assert.deepEqual(members.map(([key]) => key), standardKeys, 'Refusing an unexpected bundled order');
  const installed = await page.locator('#rule-inventory [data-rule-module]').evaluateAll(rows =>
    rows.map(row => [row.dataset.ruleModule, row.dataset.ruleRelease].join('@')));
  const expected = members.map(member => member.join('@'));
  assert.ok(installed.every(identity => expected.includes(identity)), 'Refusing non-fixture rule inventory');
  for (const [key, version] of members) {
    const shipped = memberRow('rule-bundled', key, version);
    const row = memberRow('rule-inventory', key, version);
    if (await row.count() === 0) {
      await shipped.locator('[data-rule-action="import"]').click();
      await row.waitFor();
    }
    assert.equal(await row.getAttribute('data-rule-tuple'), await shipped.getAttribute('data-rule-tuple'), 'Refusing a same-version material mismatch');
    if (await row.getAttribute('data-accepted') !== 'true') {
      await row.locator('[data-rule-action="review"]').click();
      await page.locator('#rule-review-title').and(page.getByText(`${key}@${version}`, {exact: true})).waitFor();
      await page.locator('#rule-accept').click();
      await page.locator(`#rule-inventory [data-rule-module="${key}"][data-rule-release="${version}"][data-accepted="true"]`).waitFor();
    }
  }
  const ordered = await page.locator('#rule-members [data-rule-module]').evaluateAll(rows =>
    rows.map(row => [row.dataset.ruleModule, row.dataset.ruleRelease].join('@')));
  if (ordered.length === 0) {
    for (const identity of expected) {
      await page.locator('#rule-member-choice').selectOption({label: identity});
      await page.locator('#rule-member-add').click();
    }
  } else assert.deepEqual(ordered, expected);
  await page.locator('#rule-preview').click();
  await page.locator('#storage-create button[type="submit"]:enabled').waitFor();
  await library.locator('summary').click();
}

module.exports = {selectStandardRules};
