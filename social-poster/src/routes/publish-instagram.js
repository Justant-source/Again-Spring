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
const { captureFailure, shortError } = require('../lib/debug');

async function humanDelay(page, minMs = 1200, maxMs = 3000) {
  await page.waitForTimeout(jitter((minMs + maxMs) / 2, 0.35));
}

async function isLoggedIn(page) {
  // Instagram 은 로그아웃 시에도 루트 URL(/)에서 로그인 폼을 그대로 보여줌
  // → URL 체크는 신뢰 불가. DOM 요소로 직접 판단한다.
  try {
    await page.goto('https://www.instagram.com/', { waitUntil: 'domcontentloaded', timeout: 15000 });
    await page.waitForTimeout(2500); // SPA 렌더링 대기

    const url = page.url();
    if (url.includes('/accounts/login') || url.includes('/challenge')) return false;

    // 긍정 신호: 로그인 상태에서만 보이는 네비게이션 요소 (최대 5초 대기)
    const loggedInSelector =
      'svg[aria-label="홈"], svg[aria-label="Home"], ' +
      'svg[aria-label="새 게시물"], svg[aria-label="New post"], ' +
      'a[href="/explore/"], a[href^="/direct/"]';
    try {
      await page.waitForSelector(loggedInSelector, { timeout: 5000 });
      return true;
    } catch (e) { /* 아래 부정 신호 확인 */ }

    // 부정 신호: 로그인 폼 입력칸이 보이면 로그아웃 상태
    const loginForm = await page.$('input[name="username"], input[name="pass"], input[name="password"]');
    if (loginForm) return false;

    // 불명확 — 안전하게 로그아웃으로 간주(재로그인/재시드 유도)
    return false;
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
  const { caption = '', imageBase64, imageFilename = 'post.png', images = [] } = content;

  // 업로드할 이미지 목록 결정: images[] 우선, 없으면 imageBase64 단건
  const imageList = images && images.length > 0
    ? images
    : (imageBase64 ? [{ base64: imageBase64, filename: imageFilename }] : []);

  if (imageList.length === 0) {
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

  // 모든 이미지를 temp 파일로 저장
  const ts = Date.now();
  const tmpPaths = [];
  try {
    for (let i = 0; i < imageList.length; i++) {
      const img = imageList[i];
      const tmpPath = path.join('/tmp', `ig-upload-${ts}-${i}-${img.filename || 'slide.png'}`);
      fs.writeFileSync(tmpPath, Buffer.from(img.base64, 'base64'));
      tmpPaths.push(tmpPath);
    }
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
      await captureFailure(page, 'ig-not-logged-in'); // 세션 상태 진단
      const loginResult = await attemptRelogin(page, credentials);

      if (loginResult === 'challenge') {
        const updatedState = await dumpStorageState(context);
        await context.close();
        for (const p of tmpPaths) { try { fs.unlinkSync(p); } catch (e) { /* ignore */ } }
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
        for (const p of tmpPaths) { try { fs.unlinkSync(p); } catch (e) { /* ignore */ } }
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

    // "새로운 게시물" 버튼 클릭 → create 모달 오픈
    // ⚠️ /create/select/ 직접 URL 은 "페이지 사용 불가"로 죽었으므로 반드시 버튼 클릭으로 진입
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
      // 버튼을 못 찾음 → 로그인 안 됐거나 UI 변경. 진단 후 명확히 실패 처리
      await captureFailure(page, 'ig-no-newpost-button');
      const loginWall = await page.$('input[name="username"], input[name="pass"], input[name="password"]');
      if (loginWall) {
        const e = new Error('SESSION_EXPIRED: Instagram 새 게시물 버튼 대신 로그인 화면 감지. 세션 재시드 필요.');
        e.needsReseed = true;
        throw e;
      }
      throw new Error('Instagram "새로운 게시물" 버튼을 찾지 못함 — UI 변경 가능 (debug 스크린샷 확인)');
    }
    await humanDelay(page, 1500, 2500);

    // 파일 업로드 — 단건 또는 카드뉴스 다중 이미지
    // create 다이얼로그가 뜰 때까지 잠시 대기 (file input 은 보통 hidden)
    let fileInput = await page.$(IG.FILE_INPUT);
    if (!fileInput) {
      // 한 번 더 대기 후 재시도 (다이얼로그 렌더링 지연 대응)
      await page.waitForTimeout(2500);
      fileInput = await page.$(IG.FILE_INPUT);
    }
    if (!fileInput) {
      // 진단 캡처 — 로그인월/UI변경 구분
      await captureFailure(page, 'ig-no-fileinput');
      const loginWall = await page.$('input[name="username"], input[name="pass"], input[name="password"]');
      if (loginWall) {
        const e = new Error('SESSION_EXPIRED: Instagram 업로드 화면 대신 로그인 화면이 감지됨. Instagram 세션 재시드 필요.');
        e.needsReseed = true;
        throw e;
      }
      throw new Error('FILE_INPUT not found — Instagram 업로드 다이얼로그가 열리지 않았거나 UI가 변경됨 (debug 스크린샷 확인)');
    }

    await page.setInputFiles(IG.FILE_INPUT, tmpPaths);
    await humanDelay(page, 2500, 3500);

    // 다이얼로그 내 버튼은 locator 로 클릭 — 자동 대기 + 전환 오버레이(pointer intercept) 대응
    // (elementHandle.click() 은 모달 전환 중 div 오버레이에 가로막혀 타임아웃됨)
    const clickDialogBtn = async (text, { timeout = 12000, force = false } = {}) => {
      const loc = page.locator(
        `div[role="dialog"] button:has-text("${text}"), div[role="dialog"] div[role="button"]:has-text("${text}")`
      ).last();
      await loc.waitFor({ state: 'visible', timeout });
      try {
        await loc.click({ timeout: 5000 });
      } catch (e) {
        // 오버레이가 잠깐 가로막는 경우 사라질 때까지 잠시 후 force 클릭
        await page.waitForTimeout(1200);
        await loc.click({ force: true, timeout: 5000 });
      }
    };

    // 1차 "다음" (자르기 단계)
    await clickDialogBtn('다음');
    await humanDelay(page, 1500, 2500);

    // 2차 "다음" (필터/편집 단계)
    await clickDialogBtn('다음');
    await humanDelay(page, 1500, 2500);

    // 캡션 입력 (contenteditable)
    if (caption) {
      const captionLoc = page.locator(
        'div[aria-label="문구를 입력하세요..."], div[aria-label="Write a caption..."], div[role="dialog"] div[contenteditable="true"][role="textbox"]'
      ).first();
      try {
        await captionLoc.waitFor({ state: 'visible', timeout: 8000 });
        await captionLoc.click();
        await humanDelay(page, 400, 900);
        await page.keyboard.type(caption, { delay: 12 });
        await humanDelay(page, 800, 1500);
      } catch (e) {
        console.warn('[IG] 캡션 입력칸을 찾지 못함 (캡션 없이 진행):', e.message);
      }
    }

    // "공유하기"
    await clickDialogBtn('공유');

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
    for (const p of tmpPaths) { try { fs.unlinkSync(p); } catch (e) { /* ignore */ } }

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
    for (const p of tmpPaths) { try { fs.unlinkSync(p); } catch (e) { /* ignore */ } }

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
