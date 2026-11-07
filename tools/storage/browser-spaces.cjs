'use strict';

const form = (page, id) => page.locator('#' + id);
const submit = (page, action, id) => action('domain', () => form(page, id).locator('button[type="submit"], button:not([type])').click());

async function focusSpace(page, action, kind, id) {
  await page.locator('[data-pane="world"]').click();
  await form(page, 'domain-space-choice').selectOption(kind + ':' + id);
  return action('domain', () => form(page, 'domain-focus-set').click());
}

async function createNode(page, action, name) {
  await page.locator('[data-pane="world"]').click();
  await form(page, 'domain-node').locator('[name="name"]').fill(name);
  const created = await submit(page, action, 'domain-node');
  return created.campaign.domain.nodes.find(node => node.name === name).id;
}

async function placeAtFocus(page, action, characters) {
  await page.locator('[data-pane="world"]').click();
  for (const character of characters) {
    await form(page, 'domain-position').locator('[name="characterId"]').selectOption(character);
    await submit(page, action, 'domain-position');
  }
}

async function createScene(page, action, name, resolution) {
  await page.locator('[data-pane="world"]').click();
  await form(page, 'domain-scene-create').locator('[name="name"]').fill(name);
  await form(page, 'domain-scene-create').locator('[name="resolution"]').selectOption(resolution);
  const result = await submit(page, action, 'domain-scene-create');
  return result.campaign.domain.space.scenes.find(scene => scene.name === name).id;
}

async function enterScene(page, action, sceneId, placements) {
  await page.locator('[data-pane="world"]').click();
  await form(page, 'domain-scene-enter').locator('[name="sceneId"]').selectOption(sceneId);
  for (const [id, x, y, side] of placements) {
    const row = form(page, 'domain-scene-enter-subjects').locator(`[data-character="${id}"]`);
    await row.locator('[name="join"]').check();
    for (const [field, value] of [['x', x], ['y', y], ['side', side]]) await row.locator(`[name="${field}"]`).fill(String(value));
  }
  return submit(page, action, 'domain-scene-enter');
}

async function beginBattle(page, action, placements) {
  const scene = await createScene(page, action, '交战场景', 'RULES');
  await enterScene(page, action, scene, placements);
  await page.locator('[data-pane="combat"]').click();
  await form(page, 'domain-combat').locator('[name="kind"]').selectOption('REENTER_COMBAT');
  return submit(page, action, 'domain-combat');
}

module.exports = {createNode, focusSpace, placeAtFocus, createScene, enterScene, beginBattle};
