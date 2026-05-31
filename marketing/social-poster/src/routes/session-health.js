const express = require('express');
const router = express.Router();
const { applyStorageState, dumpStorageState } = require('../lib/session');

router.post('/health', async (req, res) => {
  const { platform, storageState } = req.body;

  if (!platform || !storageState) {
    return res.status(400).json({ ok: false, loggedIn: false, platform, error: 'platform and storageState required' });
  }

  try {
    const getBrowser = req.app.get('getBrowser');
    const browser = await getBrowser();
    const { context, page } = await applyStorageState(browser, storageState);

    try {
      let loggedIn = false;

      if (platform === 'X') {
        await page.goto('https://x.com/home', { waitUntil: 'domcontentloaded', timeout: 15000 });
        loggedIn = !page.url().includes('/login');
      } else if (platform === 'INSTAGRAM') {
        await page.goto('https://www.instagram.com/', { waitUntil: 'domcontentloaded', timeout: 15000 });
        loggedIn = !page.url().includes('/accounts/login');
      } else {
        return res.status(400).json({ ok: false, loggedIn: false, platform, error: 'unknown platform' });
      }

      if (loggedIn) {
        // 피드 잠깐 스크롤 → 쿠키 활동 갱신 (세션 만료 방지)
        await page.evaluate(() => { window.scrollBy(0, 300); });
        await page.waitForTimeout(800 + Math.random() * 600);
        await page.evaluate(() => { window.scrollBy(0, 200); });
        await page.waitForTimeout(500 + Math.random() * 400);

        const updatedStorageState = await dumpStorageState(context);
        return res.json({ ok: true, loggedIn: true, platform, updatedStorageState });
      }

      return res.json({ ok: true, loggedIn: false, platform, updatedStorageState: null });
    } finally {
      await context.close();
    }
  } catch (err) {
    console.error(`[SOCIAL_POSTER] session health check failed for ${platform}:`, err);
    return res.json({ ok: false, loggedIn: false, platform, error: err.message });
  }
});

module.exports = router;
