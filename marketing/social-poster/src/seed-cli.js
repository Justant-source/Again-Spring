#!/usr/bin/env node

/**
 * Session seeding CLI for social-poster
 *
 * 이 CLI는 Docker 컨테이너가 아닌 운영자 로컬 머신에서만 실행하세요.
 *
 * Usage:
 *   node src/seed-cli.js --platform x
 *   node src/seed-cli.js --platform instagram
 *
 * Outputs storageState JSON to stdout. Paste into admin UI.
 */

const { chromium } = require('playwright');
const readline = require('readline');

async function main() {
  // Parse --platform argument
  const platformArg = process.argv.find(arg => arg.startsWith('--platform=')) ||
                      (process.argv.includes('--platform') && process.argv[process.argv.indexOf('--platform') + 1]);

  let platform = platformArg;
  if (platformArg?.startsWith('--platform=')) {
    platform = platformArg.split('=')[1];
  }

  if (!platform || !['x', 'instagram'].includes(platform)) {
    console.error('❌ Error: --platform must be "x" or "instagram"');
    console.error('Usage: node src/seed-cli.js --platform x');
    console.error('       node src/seed-cli.js --platform instagram');
    process.exit(1);
  }

  const loginUrl = platform === 'x'
    ? 'https://x.com/i/flow/login'
    : 'https://www.instagram.com/accounts/login/';

  let browser, context, page;

  try {
    // Launch headless: false so operator can interact
    console.log(`🌐 Launching ${platform} login in headed browser...`);
    browser = await chromium.launch({ headless: false });
    context = await browser.newContext();
    page = await context.newPage();

    // Navigate to login
    console.log(`📍 Navigating to ${loginUrl}`);
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });

    // Wait for user input
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });

    console.log('\n✅ 브라우저가 열렸습니다.');
    console.log('📋 다음 단계를 완료하세요:');
    console.log('   1. 로그인 (username/password)');
    console.log('   2. 2FA/TOTP 인증 (필요한 경우)');
    console.log('   3. 모든 확인 완료');
    console.log('\n⏳ 완료 후 Enter를 눌러주세요...\n');

    await new Promise(resolve => rl.once('line', resolve));
    rl.close();

    // Capture storageState
    console.log('\n📸 Capturing storageState...');
    const storageState = await context.storageState();
    const json = JSON.stringify(storageState, null, 2);

    console.log('\n' + '='.repeat(60));
    console.log('✅ STORAGE STATE (아래 JSON을 admin UI에 붙여넣으세요)');
    console.log('='.repeat(60) + '\n');
    console.log(json);
    console.log('\n' + '='.repeat(60) + '\n');

    await browser.close();
    process.exit(0);
  } catch (err) {
    console.error('\n❌ Error:', err.message);
    if (browser) await browser.close();
    process.exit(1);
  }
}

main();
