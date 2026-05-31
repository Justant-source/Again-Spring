/**
 * Playwright storageState helpers.
 * storageState = JSON string of browser context cookies + localStorage.
 */

const { buildContext, maskWebdriver } = require('./anti-bot');

async function applyStorageState(browser, storageStateJson) {
  const storageState = JSON.parse(storageStateJson);
  const context = await buildContext(browser, storageState);
  const page = await context.newPage();
  await maskWebdriver(page);
  return { context, page };
}

async function dumpStorageState(context) {
  const state = await context.storageState();
  return JSON.stringify(state);
}

module.exports = { applyStorageState, dumpStorageState };
