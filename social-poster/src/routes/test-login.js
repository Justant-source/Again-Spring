/**
 * Login test route — credentials가 실제로 동작하는지 검증
 *
 * POST /test-login
 * Body: { platform: "X" | "INSTAGRAM", credentials: { email, password } }
 * Response: { ok: boolean, error: string | null }
 */

const express = require('express');
const router = express.Router();
const X = require('../lib/x-selectors');
const IG = require('../lib/ig-selectors');
const { REALISTIC_UA, jitter, maskWebdriver } = require('../lib/anti-bot');

async function humanDelay(page, minMs = 1000, maxMs = 2000) {
  await page.waitForTimeout(jitter((minMs + maxMs) / 2, 0.35));
}

async function testXLogin(browser, email, password) {
  let context;
  try {
    context = await browser.newContext({
      userAgent: REALISTIC_UA,
      viewport: { width: 1920, height: 1080 },
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
    });
    const page = await context.newPage();
    await maskWebdriver(page);

    await page.goto('https://x.com/i/flow/login', { waitUntil: 'networkidle', timeout: 30000 });
    await humanDelay(page, 1500, 2500);

    // Wait up to 10s for the input to appear
    await page.waitForSelector(X.LOGIN_USERNAME_INPUT, { timeout: 10000 }).catch(() => {});
    const emailInput = await page.$(X.LOGIN_USERNAME_INPUT);
    if (!emailInput) {
      const url = page.url();
      return { ok: false, error: `LOGIN_INPUT_NOT_FOUND (url=${url})` };
    }

    await page.fill(X.LOGIN_USERNAME_INPUT, email);
    await humanDelay(page);

    // Check if password is already on the same page (new X UI)
    const pwOnSamePage = await page.$(X.LOGIN_PASSWORD_INPUT);
    if (!pwOnSamePage) {
      // Old X UI: click Next to reveal password field
      let clicked = false;
      for (const sel of X.LOGIN_NEXT_BUTTON.split(',').map(s => s.trim())) {
        const btn = await page.$(sel);
        if (btn) { await btn.click(); clicked = true; break; }
      }
      if (!clicked) {
        // Try pressing Enter as fallback
        await page.press(X.LOGIN_USERNAME_INPUT, 'Enter');
      }
      await humanDelay(page, 1500, 2500);
    }

    // Wait for password field
    await page.waitForSelector(X.LOGIN_PASSWORD_INPUT, { timeout: 8000 }).catch(() => {});
    const pwInput = await page.$(X.LOGIN_PASSWORD_INPUT);
    if (!pwInput) {
      const url = page.url();
      return { ok: false, error: `PASSWORD_INPUT_NOT_FOUND (url=${url})` };
    }
    await page.fill(X.LOGIN_PASSWORD_INPUT, password);
    await humanDelay(page);

    // Try submit button, then Enter as fallback
    let clicked = false;
    for (const sel of X.LOGIN_SUBMIT_BUTTON.split(',').map(s => s.trim())) {
      const btn = await page.$(sel);
      if (btn) { await btn.click(); clicked = true; break; }
    }
    if (!clicked) {
      await page.press(X.LOGIN_PASSWORD_INPUT, 'Enter');
    }

    await page.waitForTimeout(5000);

    const url = page.url();
    for (const sel of X.CHALLENGE_HEADING.split(',').map(s => s.trim())) {
      if (await page.$(sel)) return { ok: false, error: 'CHALLENGE_REQUIRED' };
    }
    if (url.includes('/home')) return { ok: true, error: null };
    return { ok: false, error: `LOGIN_FAILED (url=${url})` };
  } catch (e) {
    return { ok: false, error: e.message };
  } finally {
    if (context) await context.close().catch(() => {});
  }
}

async function testIgLogin(browser, email, password) {
  let context;
  try {
    context = await browser.newContext({
      userAgent: REALISTIC_UA,
      viewport: { width: 1920, height: 1080 },
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
    });
    const page = await context.newPage();
    await maskWebdriver(page);

    await page.goto('https://www.instagram.com/accounts/login/', { waitUntil: 'networkidle', timeout: 30000 });
    await humanDelay(page, 1000, 2000);

    await page.waitForSelector(IG.LOGIN_USERNAME_INPUT, { timeout: 10000 }).catch(() => {});
    const emailInput = await page.$(IG.LOGIN_USERNAME_INPUT);
    if (!emailInput) {
      const url = page.url();
      return { ok: false, error: `LOGIN_INPUT_NOT_FOUND (url=${url})` };
    }

    await page.fill(IG.LOGIN_USERNAME_INPUT, email);
    await humanDelay(page, 500, 1000);
    await page.fill(IG.LOGIN_PASSWORD_INPUT, password);
    await humanDelay(page);
    // Submit via Enter (Instagram submit button is hidden)
    await page.press(IG.LOGIN_PASSWORD_INPUT, 'Enter');
    await page.waitForTimeout(6000);

    const url = page.url();
    for (const sel of IG.CHALLENGE_INDICATOR.split(',').map(s => s.trim())) {
      if (await page.$(sel)) return { ok: false, error: 'CHALLENGE_REQUIRED' };
    }
    if (!url.includes('/accounts/login') && !url.includes('/challenge')) {
      return { ok: true, error: null };
    }
    return { ok: false, error: `LOGIN_FAILED (url=${url})` };
  } catch (e) {
    return { ok: false, error: e.message };
  } finally {
    if (context) await context.close().catch(() => {});
  }
}

router.post('/', async (req, res) => {
  const { platform, credentials = {} } = req.body;
  const { email, password } = credentials;

  if (!email || !password) {
    return res.status(400).json({ ok: false, error: 'email and password required' });
  }

  const getBrowser = req.app.get('getBrowser');
  const browser = await getBrowser();

  let result;
  if (platform === 'X') {
    result = await testXLogin(browser, email, password);
  } else if (platform === 'INSTAGRAM') {
    result = await testIgLogin(browser, email, password);
  } else {
    return res.status(400).json({ ok: false, error: `Unknown platform: ${platform}` });
  }

  console.log(`[TEST_LOGIN] platform=${platform} ok=${result.ok} error=${result.error}`);
  return res.json(result);
});

module.exports = router;
