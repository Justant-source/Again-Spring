#!/usr/bin/env node
/**
 * lint:docs — 문서 integrity 검사
 *
 * 검사 항목:
 *   1. 깨진 내부 링크 (.md 파일 대상)
 *   2. 수동 날짜 스탬프 (**마지막 업데이트**) — git log가 권위본
 *   3. 삭제된 컴포넌트 참조 (keywordGuard.ts — 광장형 전환 시 삭제됨)
 *   4. 루트에 허용 외 .md 파일 없음 (CLAUDE.md·README.md·AGENTS.md만 허용)
 *   5. C4Context / C4Container 다이어그램 타입 금지 (GitHub 렌더 깨짐)
 *   6. docs/_index.md 트리거맵의 대상 파일 실재 여부
 *
 * 실행: node scripts/check-docs.js
 * 또는: npm run lint:docs
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
// 단일 docs/ 트리 스캔 (런타임 자산 shared/docs·ai-user/docs/personas 는 docs/ 밖 → 자동 제외)
const DOCS_DIRS = ['docs'];
const ROOT_FILES = ['README.md', 'CLAUDE.md'];

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

// ── 4. 루트 .md 허용 목록 (CLAUDE.md·README.md·AGENTS.md만) ─────────────
const ALLOWED_ROOT_MD = new Set(['CLAUDE.md', 'README.md', 'AGENTS.md']);

function checkRootMdFiles() {
  if (!fs.existsSync(ROOT)) return;
  for (const entry of fs.readdirSync(ROOT, { withFileTypes: true })) {
    if (entry.isFile() && entry.name.endsWith('.md') && !ALLOWED_ROOT_MD.has(entry.name)) {
      error(path.join(ROOT, entry.name), '루트에 허용되지 않는 .md 파일 — docs/ 하위로 이동');
    }
  }
}

// ── 5. C4Context / C4Container 금지 (GitHub 렌더 불가) ─────────────────
function checkC4Diagrams(filePath, content) {
  if (/^```mermaid[\s\S]*?^C4Context|^```mermaid[\s\S]*?^C4Container/m.test(content)) {
    error(filePath, 'C4Context/C4Container 다이어그램 발견 — flowchart+subgraph 로 변환 (P1 원칙)');
  }
}

// ── 6. _index.md 트리거맵 대상 파일 실재 ─────────────────────────────────
function checkIndexTriggerTargets() {
  const indexPath = path.join(ROOT, 'docs/_index.md');
  if (!fs.existsSync(indexPath)) {
    warn(indexPath, 'docs/_index.md 없음 — 트리거맵 검사 건너뜀');
    return;
  }
  const content = fs.readFileSync(indexPath, 'utf-8');
  // 트리거맵 표에서 대상 경로 추출: | glob | `docs/shared/api/foo.md` |
  const re = /`(docs\/[^`]+\.md)`/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    const rel = m[1];
    const abs = path.join(ROOT, rel);
    if (!fs.existsSync(abs)) {
      error(indexPath, `트리거맵 대상 파일 없음: ${rel}`);
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
      checkC4Diagrams(full, content);
    }
  }
}

// ── 실행 ───────────────────────────────────────────────────────────────
checkRootMdFiles();
checkIndexTriggerTargets();

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
    checkC4Diagrams(full, content);
  }
}

console.log('');
if (errorCount === 0 && warnCount === 0) {
  console.log('✅  lint:docs PASSED — 깨진 링크·날짜 스탬프·삭제된 참조·루트 md·C4·트리거맵 0건');
} else {
  if (warnCount > 0) console.warn(`   경고 ${warnCount}건 (0 exit)`);
  if (errorCount > 0) console.error(`   오류 ${errorCount}건 (1 exit)`);
}

process.exit(errorCount > 0 ? 1 : 0);
