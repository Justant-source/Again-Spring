/**
 * X (Twitter) publishing route
 *
 * POST /publish/x
 * Body: {
 *   storageState: string,           // JSON string of Playwright storageState
 *   credentials: { email, password },  // For re-login if needed
 *   content: {
 *     tweets: string[],             // 1-5 tweet texts
 *     linkUrl: string,              // Optional URL to append/reply
 *     linkMode: "last_tweet" | "first_reply"  // Where to put linkUrl
 *   }
 * }
 *
 * Response: {
 *   ok: boolean,
 *   url: string | null,             // Posted tweet URL if successful
 *   error: string | null,
 *   needsReseed: boolean,           // true if storageState invalid/expired
 *   updatedStorageState: string | null  // Updated session state
 * }
 */

const express = require('express');
const fs = require('fs');
const router = express.Router();
const { applyStorageState, dumpStorageState } = require('../lib/session');
const { generateTotp } = require('../lib/totp');
const X = require('../lib/x-selectors');
const { jitter, warmup } = require('../lib/anti-bot');
const { captureFailure, shortError } = require('../lib/debug');

/**
 * jitter 기반 지연 — 동일 타이밍 패턴 방지
 */
async function humanDelay(page, minMs = 1200, maxMs = 3000) {
  await page.waitForTimeout(jitter((minMs + maxMs) / 2, 0.35));
}

/**
 * Check if currently logged in.
 * URL 만으로는 신뢰할 수 없음(로그아웃 시 x.com 루트로 리다이렉트되어 /login 미포함).
 * → 로그인 상태에서만 존재하는 DOM 요소를 직접 확인한다.
 */
async function isLoggedIn(page) {
  try {
    await page.goto('https://x.com/home', {
      waitUntil: 'domcontentloaded',
      timeout: 15000,
    });
    await page.waitForTimeout(2000); // SPA 렌더링 대기

    const url = page.url();
    if (url.includes('/login') || url.includes('/i/flow') || url.includes('/account/access')) {
      return false;
    }

    // 긍정 신호: 로그인 상태에서만 보이는 요소 (최대 5초 대기)
    const loggedInSelector =
      '[data-testid="SideNav_NewTweet_Button"], [data-testid="AppTabBar_Home_Link"], ' +
      '[data-testid="SideNav_AccountSwitcher_Button"], [aria-label="Home timeline"], ' +
      '[data-testid="primaryColumn"]';
    try {
      await page.waitForSelector(loggedInSelector, { timeout: 5000 });
      return true;
    } catch (e) { /* 아래 부정 신호 확인 */ }

    // 부정 신호: 로그인 폼/버튼이 보이면 로그아웃 상태
    const loggedOutEl = await page.$(
      'input[autocomplete="username"], a[href="/login"], a[data-testid="loginButton"], ' +
      '[data-testid="login"]'
    );
    if (loggedOutEl) return false;

    // 둘 다 불명확 — 안전하게 로그아웃으로 간주(재로그인/재시드 유도)
    return false;
  } catch (e) {
    return false;
  }
}

/**
 * Attempt to re-login with username/password/TOTP
 * Returns: 'success' | 'challenge' | 'failed'
 */
async function attemptRelogin(page, credentials) {
  const { email, password, totpSecret } = credentials || {};

  if (!email || !password) {
    return 'failed';
  }

  try {
    // Navigate to login
    await page.goto('https://x.com/i/flow/login', {
      waitUntil: 'domcontentloaded',
      timeout: 15000,
    });
    await humanDelay(page, 1000, 2000);

    // Fill email/username
    await page.waitForSelector(X.LOGIN_USERNAME_INPUT, { timeout: 10000 }).catch(() => {});
    const emailInput = await page.$(X.LOGIN_USERNAME_INPUT);
    if (!emailInput) return 'failed';
    await page.fill(X.LOGIN_USERNAME_INPUT, email);
    await humanDelay(page, 800, 1500);

    // Check if password field is already visible (new X single-page UI)
    const pwOnSamePage = await page.$(X.LOGIN_PASSWORD_INPUT);
    if (!pwOnSamePage) {
      // Old X UI: click Next
      const nextBtnSelectors = X.LOGIN_NEXT_BUTTON.split(',').map(s => s.trim());
      let clicked = false;
      for (const sel of nextBtnSelectors) {
        const btn = await page.$(sel);
        if (btn) { await btn.click(); clicked = true; break; }
      }
      if (!clicked) await page.press(X.LOGIN_USERNAME_INPUT, 'Enter');
      await humanDelay(page, 1000, 2000);
    }

    // Fill password
    await page.waitForSelector(X.LOGIN_PASSWORD_INPUT, { timeout: 8000 }).catch(() => {});
    const passwordInput = await page.$(X.LOGIN_PASSWORD_INPUT);
    if (!passwordInput) return 'failed';
    await page.fill(X.LOGIN_PASSWORD_INPUT, password);
    await humanDelay(page, 800, 1500);

    // Submit
    const submitBtnSelectors = X.LOGIN_SUBMIT_BUTTON.split(',').map(s => s.trim());
    let clicked = false;
    for (const sel of submitBtnSelectors) {
      const btn = await page.$(sel);
      if (btn) { await btn.click(); clicked = true; break; }
    }
    if (!clicked) await page.press(X.LOGIN_PASSWORD_INPUT, 'Enter');
    await page.waitForTimeout(3000);

    // Check for TOTP field
    const totpField = await page.$(X.TOTP_INPUT);
    if (totpField && totpSecret) {
      const code = generateTotp(totpSecret);
      await page.fill(X.TOTP_INPUT, code);
      await humanDelay(page, 800, 1500);

      // Submit TOTP
      const totpSubmitSelectors = X.TOTP_SUBMIT.split(',').map(s => s.trim());
      clicked = false;
      for (const sel of totpSubmitSelectors) {
        const btn = await page.$(sel);
        if (btn) {
          await btn.click();
          clicked = true;
          break;
        }
      }
      await page.waitForTimeout(3000);
    }

    // Check for challenge (unknown device, email verification, etc.)
    const challengeSelectors = X.CHALLENGE_HEADING.split(',').map(s => s.trim());
    for (const sel of challengeSelectors) {
      const el = await page.$(sel);
      if (el) return 'challenge';
    }

    // Check if still on login page
    if (page.url().includes('/login') || page.url().includes('/i/flow')) {
      return 'failed';
    }

    return 'success';
  } catch (e) {
    console.error('[X] Re-login error:', e.message);
    return 'failed';
  }
}

/**
 * Attempt to extract posted tweet URL from the home feed
 */
async function extractPostedTweetUrl(page) {
  try {
    // Try to get the user's handle from the profile link or navigation
    // This is a simple heuristic; X structure may vary
    let handle = null;

    // Try from profile link in sidebar
    const profileLink = await page.$('a[href^="/"][href$="/home"], a[href^="/"][role="link"]');
    if (profileLink) {
      const href = await profileLink.getAttribute('href');
      if (href && href.startsWith('/') && href !== '/home') {
        handle = href.replace(/^\//, '');
      }
    }

    if (!handle) {
      // Fallback: try to find from recent tweet in feed
      // (Less reliable, but worth trying)
      return null;
    }

    // Look for the most recent tweet from this user
    const tweetLinkSelector = X.FIRST_TWEET_LINK(handle);
    const tweetLink = await page.$(tweetLinkSelector);
    if (tweetLink) {
      const href = await tweetLink.getAttribute('href');
      if (href) {
        return 'https://x.com' + href;
      }
    }

    return null;
  } catch (e) {
    // Non-fatal; we'll return null
    console.warn('[X] Could not extract tweet URL:', e.message);
    return null;
  }
}

router.post('/', async (req, res) => {
  const { storageState, credentials = {}, content = {} } = req.body;
  const { tweets = [], linkUrl = '', linkMode = 'last_tweet', imageBase64 = null, imageFilename = 'cover.png' } = content;

  // Validate inputs
  if (!storageState) {
    return res.status(400).json({
      ok: false,
      error: 'storageState required',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  if (!tweets || !Array.isArray(tweets) || tweets.length === 0) {
    return res.status(400).json({
      ok: false,
      error: 'tweets array required (1-5 tweets)',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  if (tweets.length > 5) {
    return res.status(400).json({
      ok: false,
      error: 'maximum 5 tweets per thread',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  const getBrowser = req.app.get('getBrowser');
  if (!getBrowser) {
    return res.status(500).json({
      ok: false,
      error: 'browser not available',
      needsReseed: false,
      url: null,
      updatedStorageState: null,
    });
  }

  let browser, context, page;
  let tmpImgPath = null; // try/catch 양쪽에서 접근하려면 블록 바깥에 선언

  try {
    browser = await getBrowser();

    // Apply stored session
    try {
      ({ context, page } = await applyStorageState(browser, storageState));
    } catch (parseErr) {
      console.error('[X] Failed to parse storageState:', parseErr.message);
      return res.json({
        ok: false,
        error: 'Invalid storageState JSON',
        needsReseed: true,
        url: null,
        updatedStorageState: null,
      });
    }

    // Check if logged in; if not, try to re-login
    let loggedIn = await isLoggedIn(page);
    if (!loggedIn) {
      await captureFailure(page, 'x-not-logged-in'); // 세션 상태 진단
    }
    if (!loggedIn && credentials.email && credentials.password) {
      const loginResult = await attemptRelogin(page, credentials);
      if (loginResult === 'challenge') {
        const updatedState = await dumpStorageState(context);
        await context.close();
        return res.json({
          ok: false,
          error: 'CHALLENGE_DETECTED',
          needsReseed: true,
          url: null,
          updatedStorageState: updatedState,
        });
      }
      if (loginResult === 'failed') {
        const updatedState = await dumpStorageState(context);
        await context.close();
        return res.json({
          ok: false,
          error: 'LOGIN_FAILED',
          needsReseed: true,
          url: null,
          updatedStorageState: updatedState,
        });
      }
      loggedIn = true;
    }

    if (!loggedIn) {
      const updatedState = await dumpStorageState(context);
      await context.close();
      return res.json({
        ok: false,
        error: 'NOT_LOGGED_IN',
        needsReseed: true,
        url: null,
        updatedStorageState: updatedState,
      });
    }

    // Navigate to home and open compose
    await page.goto('https://x.com/home', {
      waitUntil: 'domcontentloaded',
      timeout: 15000,
    });
    await humanDelay(page, 1000, 2000);

    // 피드 워밍업 — 즉시 compose 클릭 패턴 방지
    await warmup(page, 'X');

    // Compose 버튼 대기 후 클릭 (UI 변경 대응)
    const composeBtnSelectors = X.COMPOSE_TWEET_BUTTON.split(',').map(s => s.trim());
    let clicked = false;
    try {
      await page.waitForSelector(composeBtnSelectors.join(', '), { timeout: 8000 });
      for (const sel of composeBtnSelectors) {
        const btn = await page.$(sel);
        if (btn) { await btn.click(); clicked = true; break; }
      }
    } catch (e) {
      // 셀렉터 실패 → URL 직접 이동으로 fallback
    }
    if (!clicked) {
      // X compose 직접 URL fallback
      await page.goto('https://x.com/compose/tweet', {
        waitUntil: 'domcontentloaded',
        timeout: 15000,
      });
    }

    // Wait for tweet textarea to appear
    try {
      await page.waitForSelector(X.TWEET_TEXT_AREA_0, { timeout: 12000 });
    } catch (waitErr) {
      // 진단 캡처 — 로그인월/UI변경 구분
      await captureFailure(page, 'x-no-textarea');
      const loginWall = await page.$(
        'input[autocomplete="username"], input[name="text"], a[href="/login"], [data-testid="login"]'
      );
      if (loginWall) {
        const e = new Error('SESSION_EXPIRED: X compose에 트윗 입력창이 없고 로그인 화면이 감지됨. X 세션 재시드 필요.');
        e.needsReseed = true;
        throw e;
      }
      throw new Error('Tweet textarea not found — X compose가 열리지 않았거나 UI가 변경됨 (debug 스크린샷 확인)');
    }
    await humanDelay(page, 800, 1500);

    // 이미지 첨부 (imageBase64 전달된 경우)
    if (imageBase64) {
      tmpImgPath = `/tmp/x-img-${Date.now()}.png`;
      try {
        fs.writeFileSync(tmpImgPath, Buffer.from(imageBase64, 'base64'));
        const mediaInputSels = X.MEDIA_INPUT.split(',').map(s => s.trim());
        let mediaInputFound = false;
        for (const sel of mediaInputSels) {
          const el = await page.$(sel);
          if (el) {
            await page.setInputFiles(sel, tmpImgPath);
            mediaInputFound = true;
            break;
          }
        }
        if (mediaInputFound) {
          await humanDelay(page, 2000, 3500); // 업로드 대기
        }
      } catch (imgErr) {
        console.warn('[X] Image upload failed (non-fatal):', imgErr.message);
      }
    }

    // Type each tweet
    // ⚠️ X 입력창은 Draft.js contenteditable — page.fill()은 React 상태를 갱신하지 못해
    //    Post 버튼이 비활성 유지됨. 반드시 click + keyboard.type 로 실제 키 입력 이벤트 발생.
    for (let i = 0; i < tweets.length; i++) {
      let text = tweets[i];

      // Append link to last tweet if linkMode is last_tweet
      if (i === tweets.length - 1 && linkMode === 'last_tweet' && linkUrl) {
        text = text + '\n' + linkUrl;
      }

      // Get textarea selector for this tweet
      const textareaSelector = typeof X.TWEET_TEXT_AREA_N === 'function'
        ? X.TWEET_TEXT_AREA_N(i)
        : X.TWEET_TEXT_AREA_0;

      const ta = await page.$(textareaSelector);
      if (!ta) throw new Error(`tweet 입력창(${i})을 찾지 못함`);
      await ta.click();
      await page.waitForTimeout(300);
      await page.keyboard.type(text, { delay: 12 });
      await humanDelay(page, 800, 1500);

      // If not the last tweet, click "Add another Tweet" button
      if (i < tweets.length - 1) {
        const addBtn = await page.$(X.ADD_TWEET_BUTTON);
        if (!addBtn) throw new Error(`Could not find add tweet button for tweet ${i + 1}`);
        await addBtn.click();
        await page.waitForTimeout(1000);
      }
    }

    // Submit tweet(s) — 여러 Post 버튼 셀렉터 중 "활성화된" 것을 찾아 클릭
    // (full compose 모달은 tweetButton, 인라인은 tweetButtonInline — 상황에 따라 활성 버튼이 다름)
    await page.waitForTimeout(800);
    clicked = false;
    const submitBtnSelectors = X.TWEET_SUBMIT_BUTTON.split(',').map(s => s.trim());
    for (let t = 0; t < 16 && !clicked; t++) { // 최대 8초, 활성 버튼 폴링
      for (const sel of submitBtnSelectors) {
        const btn = await page.$(sel);
        if (btn && await btn.isEnabled().catch(() => false)) {
          await btn.click({ timeout: 5000 }).catch(() => {});
          clicked = true;
          break;
        }
      }
      if (!clicked) await page.waitForTimeout(500);
    }
    if (!clicked) {
      // fallback: 키보드 단축키(Ctrl+Enter)로 게시 시도
      console.warn('[X] Post 버튼 비활성/미발견 — Ctrl+Enter fallback');
      await page.keyboard.press('Control+Enter');
      await page.waitForTimeout(1000);
      // 여전히 compose 가 열려 있으면 텍스트 미입력 등으로 실패
      const stillComposing = await page.$(X.TWEET_TEXT_AREA_0);
      if (stillComposing) {
        await captureFailure(page, 'x-submit-disabled');
        throw new Error('게시 실패: Post 버튼이 활성화되지 않음 (트윗 본문 입력 실패 가능 — debug 스크린샷 확인)');
      }
    }

    // Wait for submission to complete
    await page.waitForTimeout(4000);

    // Try to capture the posted tweet URL
    let postUrl = await extractPostedTweetUrl(page);

    // Handle first_reply link mode: reply to the first (only) tweet with linkUrl
    if (linkMode === 'first_reply' && linkUrl && postUrl) {
      try {
        await humanDelay(page, 2000, 3000);

        // Navigate to the posted tweet
        await page.goto(postUrl, {
          waitUntil: 'domcontentloaded',
          timeout: 10000,
        });
        await humanDelay(page);

        // Find reply textarea and fill with linkUrl
        const replyArea = await page.$(X.TWEET_TEXT_AREA_0);
        if (replyArea) {
          await replyArea.fill(linkUrl);
          await humanDelay(page, 800, 1500);

          // Submit reply
          const replySubmitBtn = await page.$('[data-testid="tweetButtonInline"]');
          if (replySubmitBtn) {
            await replySubmitBtn.click();
            await page.waitForTimeout(3000);
          }
        }
      } catch (e) {
        // Non-fatal: post succeeded even if reply failed
        console.warn('[X] First-reply link failed (non-fatal):', e.message);
      }
    }

    // Capture updated storageState and close
    const updatedStorageState = await dumpStorageState(context);
    await context.close();

    // 임시 이미지 파일 정리
    if (tmpImgPath) {
      try { fs.unlinkSync(tmpImgPath); } catch (e) { /* ignore */ }
    }

    return res.json({
      ok: true,
      url: postUrl,
      error: null,
      needsReseed: false,
      updatedStorageState,
    });
  } catch (err) {
    console.error('[X] Posting error:', err.message, err.stack);
    if (context) {
      try {
        await context.close();
      } catch (e) {
        // ignore
      }
    }
    if (tmpImgPath) {
      try { fs.unlinkSync(tmpImgPath); } catch (e) { /* ignore */ }
    }
    return res.json({
      ok: false,
      url: null,
      error: shortError(err),
      needsReseed: err.needsReseed === true,
      updatedStorageState: null,
    });
  }
});

module.exports = router;
