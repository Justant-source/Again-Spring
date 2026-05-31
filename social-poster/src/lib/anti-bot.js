/**
 * anti-bot.js — 봇 탐지 우회 유틸리티
 *
 * X·Instagram은 서버 IP headless 브라우저를 강하게 탐지한다.
 * 실제 Windows 11 Chrome처럼 보이도록 컨텍스트 옵션·스크립트를 설정한다.
 */

// Windows 11 + Chrome 120 User-Agent (실제 브라우저와 동일)
const REALISTIC_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
  'AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/120.0.0.0 Safari/537.36';

/**
 * 핑거프린트가 강화된 브라우저 컨텍스트 생성
 * session.js의 applyStorageState에서 사용
 */
async function buildContext(browser, storageState) {
  const context = await browser.newContext({
    storageState,
    userAgent: REALISTIC_UA,
    viewport: { width: 1920, height: 1080 },
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    deviceScaleFactor: 1,
    hasTouch: false,
    // Accept-Language 헤더
    extraHTTPHeaders: {
      'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
    },
  });
  return context;
}

/**
 * navigator.webdriver 프로퍼티 숨김 (headless 탐지 차단)
 * 새 페이지를 열기 직전에 호출
 */
async function maskWebdriver(page) {
  await page.addInitScript(() => {
    // navigator.webdriver 제거
    Object.defineProperty(navigator, 'webdriver', {
      get: () => undefined,
      configurable: true,
    });
    // Chrome automation 제거
    if (window.chrome) {
      window.chrome.runtime = {};
    }
    // plugins 배열 비어있지 않게
    Object.defineProperty(navigator, 'plugins', {
      get: () => [1, 2, 3, 4, 5],
      configurable: true,
    });
    // languages
    Object.defineProperty(navigator, 'languages', {
      get: () => ['ko-KR', 'ko', 'en-US', 'en'],
      configurable: true,
    });
  });
}

/**
 * 지연 시간에 ±pct 비율의 무작위 분산 적용
 * 동일한 타이밍 패턴이 반복되지 않도록 방지
 * @param {number} base 기준 ms
 * @param {number} pct 분산 비율 (0~1), 기본 0.35
 */
function jitter(base, pct = 0.35) {
  const delta = base * pct;
  return base + (Math.random() * 2 - 1) * delta;
}

/**
 * 포스팅 전 피드 워밍업 — 사람처럼 피드를 잠깐 훑고 시작
 * 봇 탐지 패턴(즉시 compose 클릭)을 피한다
 * @param {import('playwright').Page} page
 * @param {'X'|'INSTAGRAM'} platform
 */
async function warmup(page, platform) {
  try {
    if (platform === 'X') {
      // 이미 x.com/home에 있다고 가정
      // 페이지를 2~4번 서서히 스크롤
      const scrollCount = 2 + Math.floor(Math.random() * 3);
      for (let i = 0; i < scrollCount; i++) {
        await page.evaluate(() => {
          window.scrollBy(0, 300 + Math.random() * 400);
        });
        await page.waitForTimeout(jitter(800, 0.4));
      }
      // 스크롤 상단으로 복귀 (compose 버튼은 상단 사이드바에 있음)
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.waitForTimeout(jitter(600, 0.3));
    } else if (platform === 'INSTAGRAM') {
      // 피드 잠깐 스크롤
      const scrollCount = 1 + Math.floor(Math.random() * 2);
      for (let i = 0; i < scrollCount; i++) {
        await page.evaluate(() => {
          window.scrollBy(0, 250 + Math.random() * 350);
        });
        await page.waitForTimeout(jitter(1000, 0.4));
      }
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.waitForTimeout(jitter(700, 0.3));
    }
  } catch (e) {
    // warmup 실패는 치명적이지 않음 — 포스팅 계속 진행
    console.warn(`[ANTI_BOT] warmup failed (non-fatal): ${e.message}`);
  }
}

module.exports = { buildContext, maskWebdriver, jitter, warmup, REALISTIC_UA };
