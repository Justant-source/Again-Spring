#!/usr/bin/env node
'use strict';

const { execSync } = require('child_process');
const path = require('path');

const TARGETS = ['app', 'components', 'lib'];
const EXCLUDE_PATTERNS = [
  'node_modules',
  'mockServiceWorker.js',
  '.next',
];

const EMOJI_RANGES = [
  '[\\x{1F300}-\\x{1F9FF}]',
  '[\\x{2600}-\\x{27BF}]',
  '[\\x{2B50}]',
].join('|');

const root = path.resolve(__dirname, '..');
let violations = [];

for (const target of TARGETS) {
  let output;
  try {
    output = execSync(
      `grep -rohnP '${EMOJI_RANGES}' ${target}`,
      { cwd: root, encoding: 'utf-8' }
    );
  } catch (e) {
    output = e.stdout || '';
  }

  const lines = output.split('\n').filter(Boolean);
  for (const line of lines) {
    if (EXCLUDE_PATTERNS.some(p => line.includes(p))) continue;
    violations.push(line);
  }
}

if (violations.length > 0) {
  console.error('emoji 사용 발견 (V13.10 정책 위반):');
  violations.forEach(v => console.error('  ' + v));
  console.error('\ndocs/design/icons.md 의 SVG 컴포넌트 사용.');
  process.exit(1);
}

console.log('emoji 잔존 0개');
process.exit(0);
