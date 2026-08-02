#!/usr/bin/env node
/**
 * E3: e2e-realbe는 dev(:8090)만. prod URL이면 즉시 실패.
 */
const url = process.env.E2E_BASE_URL || 'http://localhost:8090'
if (/:8091\b/i.test(url) || /againspring\.net/i.test(url)) {
  console.error(
    `[e2e] refused prod target: E2E_BASE_URL=${url}\n` +
      `Use: E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe`,
  )
  process.exit(1)
}
process.stdout.write(url)
