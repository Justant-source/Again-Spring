// NOTE: Instagram 웹 UI는 안티봇 탐지가 가장 강합니다.
// 셀렉터가 깨지면 ig-selectors.js만 수정하세요.
// 발행 실패는 PARTIAL 상태로 처리되며 운영자에게 알림이 전송됩니다.

const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const { applyStorageState, dumpStorageState } = require('../lib/session');
const { generateTotp } = require('../lib/totp');
const IG = require('../lib/ig-selectors');
const { jitter, warmup } = require('../lib/anti-bot');

async function humanDelay(page, minMs = 1200, maxMs = 3000) {
  await page.waitForTimeout(jitter((minMs + maxMs) / 2, 0.35));
}

async function isLoggedIn(page) {
  try {
    await page.goto('https://www.instagram.com/', { waitUntil: 'domcontentloaded', timeout: 15000 });
    return !page.url().includes('/accounts/login') && !page.url().includes('/challenge');
  } catch (e) {
    return false;
  }
}

async function attemptRelogin(page, credentials) {
  // Returns 'success' | 'challenge' | 'failed'
  const { email, password, totpSecret } = credentials;
  try {
    await page.goto('https://www.instagram.com/accounts/login/', { waitUntil: 'domcontentloaded', timeout: 15000 });
    await humanDelay(page, 1000, 2000);

    await page.fill(IG.LOGIN_USERNAME_INPUT, email);
    await humanDelay(page, 500, 1000);

    await page.fill(IG.LOGIN_PASSWORD_INPUT, password);
    await humanDelay(page);

    // Submit via Enter (Instagram submit button is hidden)
    await page.press(IG.LOGIN_PASSWORD_INPUT, 'Enter');
    await page.waitForTimeout(4000);

    // Check for TOTP
    const totpField = await page.$(IG.TOTP_INPUT.split(',')[0].trim());
    if (totpField && totpSecret) {
      const code = generateTotp(totpSecret);
      await page.fill(IG.TOTP_INPUT.split(',')[0].trim(), code);
      await humanDelay(page);
      const totpBtn = await page.$(IG.TOTP_SUBMIT.split(',')[0].trim());
      if (totpBtn) await totpBtn.click();
      await page.waitForTimeout(4000);
    }

    // Check for challenge
    const challengeEl = await page.$(IG.CHALLENGE_INDICATOR.split(',')[0].trim());
    if (challengeEl) return 'challenge';

    if (page.url().includes('/accounts/login') || page.url().includes('/challenge')) {
      return 'failed';
    }

    return 'success';
  } catch (e) {
    return 'failed';
  }
}

router.post('/', async (req, res) => {
  const { storageState, credentials = {}, content = {} } = req.body;
  const { caption = '', imageBase64, imageFilename = 'post.png' } = content;

  // Validate required image
  if (!imageBase64) {
    return res.status(400).json({
      ok: false,
      error: 'IMAGE_REQUIRED',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  if (!storageState) {
    return res.status(400).json({
      ok: false,
      error: 'storageState required',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  // Write image to temp file
  const tmpPath = path.join('/tmp', 'ig-upload-' + Date.now() + '-' + imageFilename);
  try {
    fs.writeFileSync(tmpPath, Buffer.from(imageBase64, 'base64'));
  } catch (e) {
    return res.json({
      ok: false,
      error: 'Failed to write temp image: ' + e.message,
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  const getBrowser = req.app.get('getBrowser');
  const browser = await getBrowser();
  let context, page;

  try {
    ({ context, page } = await applyStorageState(browser, storageState));

    let loggedIn = await isLoggedIn(page);
    if (!loggedIn) {
      const loginResult = await attemptRelogin(page, credentials);

      if (loginResult === 'challenge') {
        const updatedState = await dumpStorageState(context);
        await context.close();
        fs.unlinkSync(tmpPath);
        return res.json({
          ok: false,
          error: 'CHALLENGE',
          needsReseed: true,
          url: null,
          updatedStorageState: updatedState,
        });
      }

      if (loginResult === 'failed') {
        const updatedState = await dumpStorageState(context);
        await context.close();
        fs.unlinkSync(tmpPath);
        return res.json({
          ok: false,
          error: 'LOGIN_FAILED',
          needsReseed: true,
          url: null,
          updatedStorageState: updatedState,
        });
      }
    }

    // Navigate to instagram home
    await page.goto('https://www.instagram.com/', { waitUntil: 'domcontentloaded', timeout: 15000 });
    await humanDelay(page, 1500, 2500);

    // 피드 워밍업 — 즉시 업로드 패턴 방지
    await warmup(page, 'INSTAGRAM');

    // Try to click "New Post" button (try multiple selectors)
    let newPostClicked = false;
    for (const sel of IG.NEW_POST_NAV_BUTTON.split(',')) {
      const el = await page.$(sel.trim());
      if (el) {
        await el.click();
        newPostClicked = true;
        break;
      }
    }

    if (!newPostClicked) {
      // Try navigating directly to create page
      await page.goto('https://www.instagram.com/create/select/', { waitUntil: 'domcontentloaded', timeout: 10000 });
    }
    await humanDelay(page, 1000, 2000);

    // Upload file — try setInputFiles approach
    const fileInput = await page.$(IG.FILE_INPUT);
    if (!fileInput) {
      throw new Error('FILE_INPUT not found — Instagram UI may have changed');
    }

    await page.setInputFiles(IG.FILE_INPUT, tmpPath);
    await humanDelay(page, 2000, 3000);

    // Click Next (crop step)
    const nextBtn1 = await page.$(IG.NEXT_BUTTON.split(',')[0].trim());
    if (nextBtn1) {
      await nextBtn1.click();
      await humanDelay(page);
    }

    // Click Next again (filter/edit step)
    const nextBtn2 = await page.$(IG.NEXT_BUTTON.split(',')[0].trim());
    if (nextBtn2) {
      await nextBtn2.click();
      await humanDelay(page);
    }

    // Fill caption
    const captionEl =
      (await page.$(IG.CAPTION_INPUT.split(',')[0].trim())) ||
      (await page.$(IG.CAPTION_INPUT.split(',')[1]?.trim())) ||
      (await page.$(IG.CAPTION_INPUT.split(',')[2]?.trim()));

    if (captionEl && caption) {
      await captionEl.click();
      await humanDelay(page, 500, 1000);
      await page.keyboard.type(caption, { delay: 30 });
      await humanDelay(page);
    }

    // Share
    const shareBtn =
      (await page.$(IG.SHARE_BUTTON.split(',')[0].trim())) ||
      (await page.$(IG.SHARE_BUTTON.split(',')[1]?.trim()));

    if (!shareBtn) throw new Error('SHARE_BUTTON not found');

    await shareBtn.click();

    // Wait for confirmation (up to 30 seconds)
    let postUrl = null;
    try {
      await page.waitForSelector(IG.POST_SHARED_CONFIRM.split(',')[0].trim(), { timeout: 30000 });
      // Try to get the post URL from current page or redirect
      postUrl = page.url().includes('/p/') ? page.url() : null;
    } catch (e) {
      // Non-fatal — post may have succeeded even without the confirmation selector
    }

    const updatedStorageState = await dumpStorageState(context);
    await context.close();
    fs.unlinkSync(tmpPath);

    return res.json({
      ok: true,
      url: postUrl,
      error: null,
      needsReseed: false,
      updatedStorageState,
    });
  } catch (err) {
    if (context) {
      try {
        await context.close();
      } catch (e) {
        // Ignore cleanup errors
      }
    }
    try {
      fs.unlinkSync(tmpPath);
    } catch (e) {
      // Ignore cleanup errors
    }

    return res.json({
      ok: false,
      url: null,
      error: err.message,
      needsReseed: false,
      updatedStorageState: null,
    });
  }
});

module.exports = router;
