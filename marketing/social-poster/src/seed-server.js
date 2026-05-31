#!/usr/bin/env node
/**
 * 서버 환경용 세션 시딩 CLI (headless, 자동 로그인)
 *
 * 사용법:
 *   node src/seed-server.js --platform instagram
 *   node src/seed-server.js --platform x
 *
 * Instagram 2FA 챌린지가 뜨면 이메일로 받은 코드를 터미널에 입력하세요.
 * 완료 후 storageState JSON을 admin UI에 붙여넣으세요.
 */

const { chromium } = require('playwright');
const readline = require('readline');
const X = require('./lib/x-selectors');
const IG = require('./lib/ig-selectors');

// ── 인자 파싱 ──────────────────────────────────────────────────
const args = process.argv.slice(2);
const getArg = (name) => {
  const idx = args.indexOf(name);
  if (idx !== -1 && args[idx + 1]) return args[idx + 1];
  const prefix = args.find(a => a.startsWith(name + '='));
  return prefix ? prefix.split('=').slice(1).join('=') : null;
};

const platform = (getArg('--platform') || '').toLowerCase();
if (!['x', 'instagram'].includes(platform)) {
  console.error('❌ --platform must be "x" or "instagram"');
  console.error('Usage: node src/seed-server.js --platform x');
  process.exit(1);
}

// ── 유틸 ───────────────────────────────────────────────────────
function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => rl.question(question, ans => { rl.close(); resolve(ans.trim()); }));
}

function askSecret(question) {
  return new Promise(resolve => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    process.stdout.write(question);
    // Hide input
    if (process.stdin.isTTY) process.stdin.setRawMode(true);
    let input = '';
    process.stdin.resume();
    process.stdin.on('data', function handler(ch) {
      ch = ch.toString();
      if (ch === '\n' || ch === '\r' || ch === '') {
        if (process.stdin.isTTY) process.stdin.setRawMode(false);
        process.stdin.removeListener('data', handler);
        process.stdout.write('\n');
        rl.close();
        resolve(input);
      } else if (ch === '') {
        process.exit();
      } else if (ch === '') {
        if (input.length > 0) { input = input.slice(0, -1); process.stdout.write('\b \b'); }
      } else {
        input += ch;
        process.stdout.write('*');
      }
    });
  });
}

async function humanDelay(page, min = 800, max = 1800) {
  await page.waitForTimeout(min + Math.random() * (max - min));
}

// ── X 로그인 ───────────────────────────────────────────────────
async function seedX(browser, email, password) {
  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  });
  const page = await ctx.newPage();

  console.log('📍 x.com 로그인 페이지 이동...');
  await page.goto('https://x.com/i/flow/login', { waitUntil: 'networkidle', timeout: 30000 });
  await humanDelay(page, 1500, 2500);

  await page.waitForSelector(X.LOGIN_USERNAME_INPUT, { timeout: 10000 });
  await page.fill(X.LOGIN_USERNAME_INPUT, email);
  await humanDelay(page);

  // 비밀번호 필드가 같은 페이지에 있는지 확인 (new X UI)
  const pwOnSamePage = await page.$(X.LOGIN_PASSWORD_INPUT);
  if (!pwOnSamePage) {
    let clicked = false;
    for (const sel of X.LOGIN_NEXT_BUTTON.split(',').map(s => s.trim())) {
      const btn = await page.$(sel);
      if (btn) { await btn.click(); clicked = true; break; }
    }
    if (!clicked) await page.press(X.LOGIN_USERNAME_INPUT, 'Enter');
    await humanDelay(page, 1500, 2500);
  }

  await page.waitForSelector(X.LOGIN_PASSWORD_INPUT, { timeout: 8000 });
  await page.fill(X.LOGIN_PASSWORD_INPUT, password);
  await humanDelay(page);

  let clicked = false;
  for (const sel of X.LOGIN_SUBMIT_BUTTON.split(',').map(s => s.trim())) {
    const btn = await page.$(sel);
    if (btn) { await btn.click(); clicked = true; break; }
  }
  if (!clicked) await page.press(X.LOGIN_PASSWORD_INPUT, 'Enter');

  await page.waitForTimeout(4000);
  console.log('🔄 URL:', page.url());

  if (page.url().includes('/home')) {
    console.log('✅ X 로그인 성공!');
    return await ctx.storageState();
  }

  // TOTP 또는 이메일 챌린지 처리
  const totpInput = await page.$(X.TOTP_INPUT);
  if (totpInput) {
    const code = await ask('🔐 인증 코드를 입력하세요: ');
    await page.fill(X.TOTP_INPUT, code);
    await humanDelay(page);
    for (const sel of X.TOTP_SUBMIT.split(',').map(s => s.trim())) {
      const btn = await page.$(sel);
      if (btn) { await btn.click(); break; }
    }
    await page.waitForTimeout(3000);
  }

  if (page.url().includes('/home')) {
    console.log('✅ X 로그인 성공 (인증 완료)!');
    return await ctx.storageState();
  }

  throw new Error(`X 로그인 실패. 현재 URL: ${page.url()}`);
}

// ── Instagram 로그인 ────────────────────────────────────────────
async function seedInstagram(browser, email, password) {
  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  });
  const page = await ctx.newPage();

  console.log('📍 Instagram 로그인 페이지 이동...');
  await page.goto('https://www.instagram.com/accounts/login/', { waitUntil: 'networkidle', timeout: 30000 });
  await humanDelay(page, 1000, 2000);

  await page.waitForSelector(IG.LOGIN_USERNAME_INPUT, { timeout: 10000 });
  await page.fill(IG.LOGIN_USERNAME_INPUT, email);
  await humanDelay(page, 500, 1000);
  await page.fill(IG.LOGIN_PASSWORD_INPUT, password);
  await humanDelay(page);

  await page.press(IG.LOGIN_PASSWORD_INPUT, 'Enter');
  await page.waitForTimeout(5000);
  console.log('🔄 URL:', page.url());

  // 챌린지 (이메일/SMS 인증) 처리
  const challengeSelectors = IG.CHALLENGE_INDICATOR.split(',').map(s => s.trim());
  let challenged = false;
  for (const sel of challengeSelectors) {
    if (await page.$(sel)) { challenged = true; break; }
  }

  if (challenged || page.url().includes('/challenge')) {
    console.log('\n⚠️  Instagram 보안 인증이 필요합니다.');
    console.log('📧 이메일 또는 SMS로 받은 인증 코드를 확인하세요.');

    const code = await ask('🔐 인증 코드 입력: ');

    // 코드 입력 필드 탐색
    const codeInput = await page.$('input[name="verificationCode"], input[autocomplete="one-time-code"], input[type="text"]');
    if (codeInput) {
      await codeInput.fill(code);
      await humanDelay(page);
      await page.press(codeInput, 'Enter');
    } else {
      // 코드가 숫자 버튼 방식일 수 있음
      await page.keyboard.type(code);
      await page.keyboard.press('Enter');
    }

    await page.waitForTimeout(4000);
    console.log('🔄 URL after code:', page.url());
  }

  // "나중에 하기" 팝업 처리
  const notNowSelectors = ['button:has-text("나중에"), button:has-text("Not Now"), button:has-text("Not now")'];
  for (const sel of notNowSelectors) {
    const btn = await page.$(sel).catch(() => null);
    if (btn) { await btn.click(); await page.waitForTimeout(2000); break; }
  }

  if (!page.url().includes('/accounts/login') && !page.url().includes('/challenge')) {
    console.log('✅ Instagram 로그인 성공!');
    return await ctx.storageState();
  }

  throw new Error(`Instagram 로그인 실패. 현재 URL: ${page.url()}`);
}

// ── 메인 ───────────────────────────────────────────────────────
(async () => {
  console.log(`\n🔐 ${platform === 'x' ? 'X (Twitter)' : 'Instagram'} 세션 시딩 시작\n`);

  const email = await ask('이메일: ');
  const password = await askSecret('비밀번호: ');

  console.log('\n🌐 headless 브라우저 실행 중...');
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });

  try {
    let storageState;
    if (platform === 'x') {
      storageState = await seedX(browser, email, password);
    } else {
      storageState = await seedInstagram(browser, email, password);
    }

    const json = JSON.stringify(storageState, null, 2);
    console.log('\n' + '='.repeat(60));
    console.log('✅ STORAGE STATE (아래 JSON을 admin UI에 붙여넣으세요)');
    console.log('='.repeat(60) + '\n');
    console.log(json);
    console.log('\n' + '='.repeat(60) + '\n');
  } catch (err) {
    console.error('\n❌ 오류:', err.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
