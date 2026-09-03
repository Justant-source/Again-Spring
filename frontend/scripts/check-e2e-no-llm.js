#!/usr/bin/env node
// journeys가 APIRequestContext(request)로 LLM 경로를 직접 치는지 정적으로 잡는다 — page.route는 request 픽스처를 못 막는다.
const fs = require('fs'); const path = require('path')
const dir = path.join(__dirname, '..', 'tests', 'e2e-realbe', 'journeys')
const BAD = [/corrections\/analyze/, /ai-rules\/history\/[^'"`]*analyze/, /analyze-batch/, /marketing\/[^'"`]+\/(generate|simulation|story)/, /admin\/trigger\//]
let hits = 0
for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.spec.ts'))) {
  const src = fs.readFileSync(path.join(dir, f), 'utf8')
  src.split('\n').forEach((line, i) => {
    if (/page\.route\(/.test(line)) return // 라우트 차단 선언은 허용
    if (/^\s*(\*|\/\/)/.test(line)) return // 주석 줄(JSDoc/line comment)은 코드가 아니므로 제외
    for (const re of BAD) if (re.test(line)) { hits++; console.error(`${f}:${i + 1}: LLM 경로 리터럴: ${line.trim()}`) }
  })
}
if (hits) { console.error(`\n[check-e2e-no-llm] ${hits}건 — 해당 spec은 no-llm-fixture로도 못 막는다(request 픽스처). 제거하라.`); process.exit(1) }
console.log('[check-e2e-no-llm] OK')
