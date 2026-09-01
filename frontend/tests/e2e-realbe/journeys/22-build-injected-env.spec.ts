/**
 * Journey 22: 빌드 주입값 회귀 방지 (NEXT_PUBLIC_APP_URL)
 *
 * 배경: `docs/_active/deploy-verification.md` §4-6. dev/prod는 같은 Docker 이미지를
 * 쓰고 `NEXT_PUBLIC_APP_URL`만 docker-compose build arg로 갈린다
 * (`env/docker-compose.dev.yml` → `https://dev.againspring.net`,
 *  `env/docker-compose.prod.yml` → `https://againspring.net`).
 * Next.js가 이 값을 빌드 시점에 번들에 **리터럴로 인라인**하므로, 빌드 인자가
 * 누락되면 코드의 fallback(`frontend/app/layout.tsx` 등의
 * `|| 'https://againspring.net'`)이 대신 박혀 dev 이미지에 prod 도메인이 새거나,
 * 값이 아예 빈 문자열로 굳어버릴 수 있다 — 런타임 에러 없이 조용히.
 *
 * 커버 범위:
 *   A. sitemap.xml의 모든 <loc>이 dev 도메인과 정확히 일치 (prod 아님·빈 값 아님)
 *   B. 홈 페이지 canonical 링크(metadataBase 경유)도 동일 도메인
 *   C. robots.txt가 dev를 dev로 인식해 전체 disallow — 빌드 인자가 비어/prod로
 *      새면 `robots.ts`의 isProd 판정이 뒤집혀 dev가 색인 허용으로 노출된다
 *      (중복 콘텐츠로 prod 검색 순위 훼손 — 조용히 죽는 회귀라 e2e 없이는 못 잡는다)
 *
 * no-llm 가드레일 호환: 정적/공개 라우트만 호출, LLM 트리거 엔드포인트 미접촉.
 */
import { test, expect } from '../support/no-llm-fixture'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// e2e는 dev(:8090)만 대상으로 한다(E3) — 기대 도메인도 dev 고정.
const EXPECTED_HOSTNAME = 'dev.againspring.net'
const FORBIDDEN_BARE_PROD_HOSTNAME = 'againspring.net'

test.describe('Journey 22-A: sitemap.xml — NEXT_PUBLIC_APP_URL 인라인 값', () => {
  test('sitemap.xml의 모든 loc이 dev 도메인과 정확히 일치한다 (prod 아님·빈 값 아님)', async ({
    request,
  }) => {
    const res = await request.get(`${BASE}/sitemap.xml`)
    expect(res.ok()).toBeTruthy()
    const xml = await res.text()

    const locMatches = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1])
    // 정적 엔트리 4개(홈·community·terms·privacy) 이상은 항상 있어야 한다.
    expect(locMatches.length).toBeGreaterThanOrEqual(4)

    for (const loc of locMatches) {
      expect(loc.length).toBeGreaterThan(0)
      const url = new URL(loc)
      // 빌드 인자가 빈 값이면 code fallback이 prod 도메인을 박는다 — bare 도메인이면 실패.
      expect(url.hostname).toBe(EXPECTED_HOSTNAME)
      expect(url.hostname).not.toBe(FORBIDDEN_BARE_PROD_HOSTNAME)
      expect(url.protocol).toBe('https:')
    }
  })
})

test.describe('Journey 22-B: 홈 페이지 canonical — metadataBase 인라인 값', () => {
  test('canonical 링크가 dev 도메인 절대 URL로 렌더된다 (metadataBase 경유)', async ({
    request,
  }) => {
    const res = await request.get(`${BASE}/`)
    expect(res.ok()).toBeTruthy()
    const html = await res.text()

    const canonicalMatch = html.match(/rel="canonical"\s+href="([^"]+)"/)
    expect(canonicalMatch).not.toBeNull()
    const href = canonicalMatch![1]
    expect(href.length).toBeGreaterThan(0)

    const url = new URL(href)
    expect(url.hostname).toBe(EXPECTED_HOSTNAME)
    expect(url.hostname).not.toBe(FORBIDDEN_BARE_PROD_HOSTNAME)
  })
})

test.describe('Journey 22-C: robots.txt — dev 도메인 판정이 뒤집히지 않는다', () => {
  test('dev 빌드는 robots.txt가 전체 disallow다 (isProd 오판정 회귀 방지)', async ({
    request,
  }) => {
    // robots.ts: isProd = SITE_URL.includes('againspring.net') && !SITE_URL.includes('dev.')
    // 빌드 인자가 비거나 prod 값으로 새면 SITE_URL이 'https://againspring.net'(fallback)이
    // 되어 isProd가 true로 뒤집히고, dev가 색인 허용 + prod sitemap URL을 노출한다.
    const res = await request.get(`${BASE}/robots.txt`)
    expect(res.ok()).toBeTruthy()
    const body = await res.text()

    expect(body).toMatch(/User-Agent:\s*\*/i)
    expect(body).toMatch(/Disallow:\s*\/\s*$/im)
    // isProd 분기였다면 나왔을 allow 규칙·Sitemap 라인이 없어야 한다.
    // 주의: "Disallow:"도 부분 문자열로 "allow:"를 포함하므로 반드시 줄 시작(^)에 고정한다.
    expect(body).not.toMatch(/^Allow:\s*\//im)
    expect(body).not.toMatch(/^Sitemap:/im)
  })
})
