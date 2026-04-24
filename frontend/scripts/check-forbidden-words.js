#!/usr/bin/env node
/* Scans app/components/lib for forbidden words. docs/FORBIDDEN_WORDS.md authoritative. */

const fs = require('fs');
const path = require('path');

const FORBIDDEN = [
  '과실비율', '판결', '판사', '유죄', '무죄',
  '가해자', '피해자', '승자', '패자',
  '나르시시스트', '소시오패스', '가스라이팅',
  '손절', '절교', '절연',
];

const ROOTS = ['app', 'components', 'lib', 'mocks'];
const IGNORE_FILENAME = /(forbidden|system_prompt|check-forbidden-words)/i;

// Phrases where a forbidden word appears in intentional contrast ("X이 아니라 Y").
// These are reviewed and allowed; the scanner skips them before flagging.
const ALLOWED_CONTEXTS = [
  '판결이 아니라',
  '판결·승패',
];

function scan(dir) {
  let violations = 0;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name.startsWith('.') || entry.name === 'node_modules') continue;
      violations += scan(full);
      continue;
    }
    if (!/\.(tsx?|jsx?|md)$/.test(entry.name)) continue;
    if (IGNORE_FILENAME.test(entry.name)) continue;
    const content = fs.readFileSync(full, 'utf8');
    for (const word of FORBIDDEN) {
      let cursor = 0;
      while (true) {
        const idx = content.indexOf(word, cursor);
        if (idx < 0) break;
        const ctxStart = Math.max(0, idx - 20);
        const ctxEnd = Math.min(content.length, idx + word.length + 20);
        const context = content.slice(ctxStart, ctxEnd);
        const allowed = ALLOWED_CONTEXTS.some((p) => context.includes(p));
        if (!allowed) {
          const lineNo = content.slice(0, idx).split('\n').length;
          console.error(`✗ ${full}:${lineNo}  "${word}"  …${context.replace(/\n/g, ' ')}…`);
          violations++;
        }
        cursor = idx + word.length;
      }
    }
  }
  return violations;
}

let total = 0;
for (const r of ROOTS) {
  if (fs.existsSync(r)) total += scan(r);
}
if (total > 0) {
  console.error(`\n${total} forbidden word(s) found. See docs/FORBIDDEN_WORDS.md.`);
  process.exit(1);
} else {
  console.log('✓ no forbidden words found');
}
