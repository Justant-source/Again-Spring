#!/usr/bin/env node
/**
 * lint:docs — 문서 integrity 검사
 *
 * 검사 항목:
 *   1. 깨진 내부 링크 (.md 파일 대상)
 *   2. 수동 날짜 스탬프 (**마지막 업데이트**) — git log가 권위본
 *   3. 삭제된 컴포넌트 참조 (keywordGuard.ts — 광장형 전환 시 삭제됨)
 *
 * 실행: node scripts/check-docs.js
 * 또는: npm run lint:docs
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
const DOCS_DIRS = ['frontend/docs', 'backend/docs', 'env/docs', 'shared/docs'];
const ROOT_FILES = ['README.md'];

let errorCount = 0;
let warnCount = 0;

function error(file, msg) {
  console.error(`❌  ${path.relative(ROOT, file)}: ${msg}`);
  errorCount++;
}

function warn(file, msg) {
  console.warn(`⚠️   ${path.relative(ROOT, file)}: ${msg}`);
  warnCount++;
}

// ── 1. 깨진 내부 링크 ──────────────────────────────────────────────────
function checkLinks(filePath, content) {
  const dir = path.dirname(filePath);
  // [text](path) 형태 추출 (http, #-only 제외)
  const re = /\[[^\]]*\]\(([^)#][^)]*?\.md(?:#[^)]*)?)(?:[^)]*)\)/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    const raw = m[1].split('#')[0].trim();
    if (raw.startsWith('http')) continue;
    const abs = path.resolve(dir, raw);
    if (!fs.existsSync(abs)) {
      error(filePath, `깨진 링크 → ${raw}`);
    }
  }
}

// ── 2. 수동 날짜 스탬프 ────────────────────────────────────────────────
function checkDateStamp(filePath, content) {
  // CLAUDE.md는 의도적으로 날짜를 관리하므로 제외
  if (filePath.endsWith('CLAUDE.md')) return;
  if (/\*\*마지막 업데이트\*\*/.test(content)) {
    warn(filePath, '수동 날짜 스탬프 발견 — 삭제 권장 (git log -1 --format=%cs 사용)');
  }
}

// ── 3. 삭제된 컴포넌트 참조 ────────────────────────────────────────────
// "부재하는 것 (삭제됨)" 섹션의 의도적 목록은 허용 — 줄에 "삭제됨" 또는 "부재" 포함 시 skip
// 삭제된 컴포넌트를 "사용하도록 안내"하는 문서 표현만 잡는다.
// "삭제됨·부재·서버만 사용" 맥락은 의도적 문서이므로 허용.
const BANNED_DOC_REFS = [
  {
    pattern: /입력\s*필드.*keywordGuard|keywordGuard.*체크리스트|→\s*`KeywordGuard`\s*컴포넌트/i,
    reason: 'KeywordGuard 컴포넌트 사용 안내 — lib/utils/keywordGuard.ts는 삭제됨 (광장형 전환)',
  },
];

function checkBannedRefs(filePath, content) {
  for (const { pattern, reason } of BANNED_DOC_REFS) {
    if (pattern.test(content)) {
      error(filePath, `삭제된 참조: ${reason}`);
    }
  }
}

// ── 파일 워크 ──────────────────────────────────────────────────────────
function walkDir(dir) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.') || entry.name === 'node_modules') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkDir(full);
    } else if (entry.name.endsWith('.md')) {
      const content = fs.readFileSync(full, 'utf-8');
      checkLinks(full, content);
      checkDateStamp(full, content);
      checkBannedRefs(full, content);
    }
  }
}

for (const dir of DOCS_DIRS) {
  walkDir(path.join(ROOT, dir));
}
for (const file of ROOT_FILES) {
  const full = path.join(ROOT, file);
  if (fs.existsSync(full)) {
    const content = fs.readFileSync(full, 'utf-8');
    checkLinks(full, content);
    checkDateStamp(full, content);
    checkBannedRefs(full, content);
  }
}

console.log('');
if (errorCount === 0 && warnCount === 0) {
  console.log('✅  lint:docs PASSED — 깨진 링크·날짜 스탬프·삭제된 참조 없음');
} else {
  if (warnCount > 0) console.warn(`   경고 ${warnCount}건 (0 exit)`);
  if (errorCount > 0) console.error(`   오류 ${errorCount}건 (1 exit)`);
}

process.exit(errorCount > 0 ? 1 : 0);
