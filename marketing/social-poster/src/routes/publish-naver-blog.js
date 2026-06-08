/**
 * Naver Blog 발행 라우트
 *
 * POST /publish/naver-blog
 * Body: {
 *   storageState: string,             // Playwright storageState JSON (NID_AUT 쿠키 포함)
 *   credentials: { email, password }, // Naver ID(=blogId)와 비밀번호
 *   content: {
 *     title: string,                  // 블로그 포스트 제목
 *     body: string,                   // 포스트 본문 (plain text)
 *   }
 * }
 *
 * Response: {
 *   ok: boolean,
 *   url: string | null,               // 발행된 포스트 URL
 *   error: string | null,
 *   needsReseed: boolean,
 *   updatedStorageState: string | null
 * }
 */

const express = require('express');
const router = express.Router();
const { applyStorageState, dumpStorageState } = require('../lib/session');
const NV = require('../lib/naver-selectors');
const { jitter, warmup } = require('../lib/anti-bot');
const { captureFailure, shortError } = require('../lib/debug');

async function humanDelay(page, minMs = 1200, maxMs = 3000) {
  await page.waitForTimeout(jitter((minMs + maxMs) / 2, 0.35));
}

async function isLoggedIn(page) {
  try {
    await page.goto('https://www.naver.com/', {
      waitUntil: 'domcontentloaded',
      timeout: 15000,
    });
    await page.waitForTimeout(2000);

    // 로그인 상태 긍정 신호
    const loggedInEl = await page.$(NV.LOGGED_IN_INDICATOR);
    if (loggedInEl) return true;

    // 로그아웃 신호
    const loggedOutEl = await page.$(NV.LOGGED_OUT_INDICATOR);
    if (loggedOutEl) return false;

    return false;
  } catch (e) {
    return false;
  }
}

async function attemptRelogin(page, credentials) {
  const { email: naverId, password } = credentials;
  try {
    await page.goto('https://nid.naver.com/nidlogin.login', {
      waitUntil: 'domcontentloaded',
      timeout: 20000,
    });
    await humanDelay(page, 1000, 2000);

    await page.fill(NV.LOGIN_ID_INPUT, naverId);
    await humanDelay(page, 500, 1000);
    await page.fill(NV.LOGIN_PW_INPUT, password);
    await humanDelay(page, 800, 1500);

    const submitBtn = await page.$(NV.LOGIN_SUBMIT_BUTTON.split(',').map(s => s.trim()).join(', '));
    if (submitBtn) {
      await submitBtn.click();
    } else {
      await page.press(NV.LOGIN_PW_INPUT, 'Enter');
    }

    await page.waitForTimeout(4000);
    const url = page.url();

    if (url.includes('nidlogin.login') || url.includes('captcha') || url.includes('saftycheck')) {
      return 'challenge';
    }
    return 'success';
  } catch (e) {
    console.error('[NAVER_BLOG] Relogin failed:', e.message);
    return 'failed';
  }
}

async function postToBlog(page, blogId, content) {
  const { title, body } = content;

  // 블로그 글쓰기 페이지 접근
  const writeUrl = `https://blog.naver.com/${blogId}/edit`;
  await page.goto(writeUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
  await page.waitForTimeout(3000);

  // 리다이렉트 감지 (로그인 페이지로 이동하면 세션 만료)
  const currentUrl = page.url();
  if (currentUrl.includes('nidlogin.login') || currentUrl.includes('/login')) {
    throw Object.assign(new Error('NAVER_SESSION_EXPIRED'), { needsReseed: true });
  }

  // 에디터 로딩 대기 (iframe 기반 SE3)
  let editorFrame = null;
  try {
    await page.waitForSelector(NV.EDITOR_IFRAME, { timeout: 15000 });
    editorFrame = page.frameLocator(NV.EDITOR_IFRAME).first();
  } catch (e) {
    // iframe 없으면 직접 접근 시도
    console.log('[NAVER_BLOG] No editor iframe found, trying direct access');
  }

  // 제목 입력
  const titleInput = await page.$(NV.POST_TITLE_INPUT);
  if (titleInput) {
    await titleInput.click();
    await page.keyboard.selectAll();
    await page.keyboard.type(title, { delay: 30 });
    await humanDelay(page, 500, 1000);
  } else {
    console.warn('[NAVER_BLOG] Title input not found, skipping');
  }

  // 본문 입력 — iframe 내부 또는 직접
  if (editorFrame) {
    try {
      const bodyEl = editorFrame.locator(NV.EDITOR_CONTENT_AREA).first();
      await bodyEl.click();
      await humanDelay(page, 500, 1000);
      await page.keyboard.type(body, { delay: 20 });
    } catch (e) {
      console.warn('[NAVER_BLOG] iframe body fill failed, trying fallback:', e.message);
      // 폴백: 직접 page 레벨에서 입력
      const bodyDirect = await page.$(NV.EDITOR_CONTENT_AREA);
      if (bodyDirect) {
        await bodyDirect.click();
        await page.keyboard.type(body, { delay: 20 });
      }
    }
  } else {
    const bodyDirect = await page.$(NV.EDITOR_CONTENT_AREA);
    if (bodyDirect) {
      await bodyDirect.click();
      await humanDelay(page, 500, 1000);
      await page.keyboard.type(body, { delay: 20 });
    }
  }

  await humanDelay(page, 1000, 2000);

  // 발행 버튼 클릭
  let publishClicked = false;
  for (const sel of NV.PUBLISH_BTN.split(',').map(s => s.trim())) {
    const btn = await page.$(sel);
    if (btn) {
      await btn.click();
      publishClicked = true;
      break;
    }
  }
  if (!publishClicked) {
    throw new Error('PUBLISH_BTN_NOT_FOUND');
  }

  await humanDelay(page, 1500, 3000);

  // 발행 확인 팝업이 있으면 클릭
  const confirmBtn = await page.$(NV.PUBLISH_CONFIRM_BTN);
  if (confirmBtn) {
    await confirmBtn.click();
    await page.waitForTimeout(3000);
  }

  // 발행 후 URL 추출
  await page.waitForTimeout(3000);
  const postedUrl = page.url();
  if (NV.POST_URL_PATTERN.test(postedUrl)) {
    return postedUrl;
  }

  // URL이 바뀌지 않았으면 페이지에서 링크 추출 시도
  const postLink = await page.$(`a[href*="blog.naver.com/${blogId}/"]`);
  if (postLink) {
    const href = await postLink.getAttribute('href');
    if (href && NV.POST_URL_PATTERN.test(href)) {
      return href.startsWith('http') ? href : `https:${href}`;
    }
  }

  return null;
}

router.post('/', async (req, res) => {
  const { storageState, credentials = {}, content = {} } = req.body;
  const { email: blogId, password } = credentials;
  const { title, body } = content;

  if (!storageState || !blogId || !body) {
    return res.status(400).json({
      ok: false,
      url: null,
      error: 'storageState, credentials.email(blogId), content.body are required',
      needsReseed: false,
      updatedStorageState: null,
    });
  }

  const getBrowser = req.app.get('getBrowser');
  const browser = await getBrowser();
  const { context, page } = await applyStorageState(browser, storageState);

  try {
    await warmup(page);

    const loggedIn = await isLoggedIn(page);
    let reloginNeeded = !loggedIn;

    if (reloginNeeded) {
      if (!password) {
        return res.json({
          ok: false,
          url: null,
          error: 'NAVER_SESSION_EXPIRED — relogin required but no password provided',
          needsReseed: true,
          updatedStorageState: null,
        });
      }
      const reloginResult = await attemptRelogin(page, { email: blogId, password });
      if (reloginResult !== 'success') {
        return res.json({
          ok: false,
          url: null,
          error: reloginResult === 'challenge' ? 'NAVER_CHALLENGE_REQUIRED' : 'NAVER_RELOGIN_FAILED',
          needsReseed: true,
          updatedStorageState: null,
        });
      }
    }

    // 게시 시도
    let publishedUrl = null;
    try {
      publishedUrl = await postToBlog(page, blogId, { title: title || '다시봄 사연', body });
    } catch (e) {
      const needsReseed = e.needsReseed === true;
      await captureFailure(page, 'naver-blog').catch(() => {});
      return res.json({
        ok: false,
        url: null,
        error: shortError(e),
        needsReseed,
        updatedStorageState: null,
      });
    }

    const updatedStorageState = await dumpStorageState(context);
    return res.json({
      ok: true,
      url: publishedUrl,
      error: null,
      needsReseed: false,
      updatedStorageState,
    });
  } catch (e) {
    console.error('[NAVER_BLOG] Unhandled error:', e);
    await captureFailure(page, 'naver-blog').catch(() => {});
    return res.json({
      ok: false,
      url: null,
      error: shortError(e),
      needsReseed: false,
      updatedStorageState: null,
    });
  } finally {
    await context.close().catch(() => {});
  }
});

module.exports = router;
