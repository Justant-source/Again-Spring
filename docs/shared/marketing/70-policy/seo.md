# 검색 유입 기반 — robots · sitemap · SSR · 소유확인

> **권위본**: 이 문서 (2026-08-29 신설).
> 관련: [`acquisition-measurement.md`](../40-data/acquisition-measurement.md) (유입 계측) · [`../../../env/environment-variables.md`](../../../env/environment-variables.md) (인증 변수)
> 코드: `frontend/app/robots.ts` · `frontend/app/sitemap.ts` · `frontend/app/layout.tsx` · `frontend/public/naver*.html`

---

## 0. 왜 생겼나

런칭 후 30일간 검색 유입이 사실상 0이었다. 조사 결과 **검색엔진이 사이트를 읽을 기반 자체가 없었다.**

| 항목 | 2026-08-29 이전 | 이후 |
|---|---|---|
| `robots.txt` | Cloudflare 기본 주석만 (규칙 없음) | `Allow: /` + Sitemap 선언 |
| `sitemap.xml` | **404** | **348 URL** (사연 329 + 고정 4) |
| 홈·광장 HTML | CSR — 크롤러가 빈 페이지를 봄 (한글 462·500자) | SSR — 11,799자 · 1,232자 |
| 서치콘솔·서치어드바이저 | 미등록 | 양쪽 등록·수집 확인 |
| 홈 메타데이터 | 피벗 이전 문구("AI 중재자") | 실제 서비스로 교체 |

> ℹ️ `noindex`는 원래 없었다. 조사 초기에 "noindex가 있다"는 보고가 있었으나 **404 페이지에만**
> 붙은 것이었다. 정상 페이지는 처음부터 색인 가능했다.

---

## 1. robots.txt — `app/robots.ts`

도메인으로 색인 대상을 가른다. **dev가 색인되면 prod와 중복 콘텐츠가 되어 양쪽 순위가 함께 내려간다.**

| 도메인 | 정책 |
|---|---|
| `againspring.net` | `Allow: /` · `Disallow: /admin /api /auth /s` · Sitemap 선언 |
| `dev.againspring.net` (그 외 전부) | `Disallow: /` — 전면 차단 |

판별 기준은 `NEXT_PUBLIC_APP_URL`이다. 정적 라우트(`○`)라 데이터 의존이 없다.

---

## 2. sitemap.xml — `app/sitemap.ts`

`/api/community/posts`를 페이지네이션으로 훑어 사연 상세 URL을 만든다(최대 10페이지 × 100건).

**🔴 `export const dynamic = 'force-dynamic'` 필수.** 이유는 §4 참조.

고정 경로 4개(`/` `/community` `/terms` `/privacy`) + 사연 URL.
BE 장애 시 빈 배열로 폴백해 **사이트맵 생성 실패가 배포를 막지 않는다.**

---

## 3. SSR — 홈·광장

사연 상세(`/community/[id]`)는 원래부터 `generateMetadata` + `opengraph-image.tsx`로 잘 만들어져 있었다.
문제는 **홈과 광장**이었다.

| 페이지 | 원인 | 조치 |
|---|---|---|
| 홈 | `if (!mounted) return null` 가드가 SSR에서 항상 렌더를 차단 | 서버 컴포넌트 + `LandingPageClient` 분리 |
| 광장 | `posts` 초기값 `[]`, fetch가 `useEffect`에만 → SSR 결과가 "아직 사연이 없습니다" | 서버 컴포넌트 + `CommunityFeedClient` 분리 |

**초기 목록만 서버에서 주입**하고 필터·정렬·무한스크롤·투표는 클라이언트 그대로다.

🚨 **서버 fetch에 인증 토큰을 싣지 않는다.** 사용자별 데이터(`myVoteSide` 등)가 캐시에 섞이면
모든 방문자가 한 사람의 투표 상태를 보게 된다.

---

## 4. 🚨 정적 프리렌더 금지 — 반복하기 쉬운 함정

`/`, `/community`, `/sitemap.xml` 은 모두 **`export const dynamic = 'force-dynamic'`** 이다.

ISR(`revalidate`)로 두면 **정적 프리렌더가 `docker build` 중에 실행**되는데, 그 시점에는
backend 컨테이너가 존재하지 않아 **fetch가 반드시 실패**한다. try/catch가 빈 배열로 폴백하면서
**"아직 사연이 없습니다"가 이미지에 그대로 구워지고**, ISR 재검증도 이를 되돌리지 못했다
(`x-nextjs-cache: HIT` 상태로 462자 고정).

크롤러가 배포 직후 한 번 들르고 마는 상황에서 "요청이 쌓이면 채워진다"는 자기치유는
SEO 목적에 맞지 않는다. 백엔드 응답이 0.1초대라 요청마다 렌더해도 비용이 무시할 수준이다.

> `robots.txt`는 데이터 의존이 없어 정적(`○`) 유지.

### 서버사이드 API base

서버 컴포넌트·sitemap·OG 이미지는 `lib/serverApiBase.ts`의 `SERVER_API_BASE`를 쓴다.

```
BACKEND_INTERNAL_URL  →  API_BASE_URL  →  http://localhost:8080
```

🔴 **순서가 중요하다.** compose가 실제로 주입하는 값은 `BACKEND_INTERNAL_URL`이고
`API_BASE_URL`은 컨테이너에 없다(`next.config.mjs` rewrites가 로컬 개발용으로만 참조).
`API_BASE_URL`만 쓰면 컨테이너에서 `localhost:8080`으로 폴백해 **조용히 빈 응답**을 받는다.

---

## 5. 메타데이터 — `app/layout.tsx`

| 항목 | 값 |
|---|---|
| title | `다시봄 · 갈등 사연 공감 투표 커뮤니티` (template: `%s · 다시봄`) |
| description | 연인·부부·친구·가족·직장 갈등 사연 → 작성자/상대방 공감 투표 |
| keywords | 사연 · 갈등 · 공감 · 커뮤니티 · 연애 고민 · 부부 갈등 · 직장 갈등 · 다시봄 |
| og / twitter | `이 갈등, 당신은 어느 쪽에 공감하나요?` |

이전 문구는 광장형 피벗 이전의 "싸운 두 사람 사이에 조용히 앉는 AI 중재자"였다 —
실제 서비스와 달랐고 검색 이용자가 쓰는 말도 아니었다.

---

## 6. 소유확인 — 방식이 서로 다르다

| 엔진 | 방식 | 위치 |
|---|---|---|
| **구글** | **DNS TXT** (권위) | Cloudflare. 토큰 2개 공존 — 구글은 일치하는 하나만 있으면 통과 |
| 구글 (보조) | 메타태그 | `.env.prod` `GOOGLE_SITE_VERIFICATION` → `metadata.verification.google` |
| **네이버** | **HTML 파일** | `frontend/public/naver79b8914327d3fa9c1bacd9df6b05b40b.html` |

### 🔴 주의 3가지

1. **네이버 파일을 지우면 소유확인이 풀린다.** 일회성이 아니라 주기적으로 재확인한다.
   `frontend/public/README-verification.md`에도 적어 뒀다.
2. **네이버는 HTML 파일 방식이라 토큰이 없다.** `NAVER_SITE_VERIFICATION`이 비어 있는 것이 정상이다.
3. **구글 메타태그는 DNS 토큰을 담고 있어 검증에 안 쓰일 수 있다.** 구글은 방식마다 토큰을
   따로 발급한다. 무해하고 DNS 유실 시 예비책이라 남겨 뒀지만, **권위는 DNS TXT다** —
   메타태그만 보고 소유확인 근거를 판단하지 말 것.

`NEXT_PUBLIC_*`은 **빌드 타임 인라인**이라 값 변경 시 프론트엔드 **재빌드**가 필요하다
(재시작으로는 반영 안 됨). Dockerfile `ARG`/`ENV` + compose `build.args` 양쪽에 wiring돼 있다.

---

## 7. 등록 절차 (재등록 시 참고)

### 구글
1. Search Console → 속성 추가 → **도메인** → `againspring.net`
2. 제시된 TXT를 Cloudflare DNS에 `@`로 추가 → 확인
3. **Sitemaps** → 입력란에 **`sitemap.xml`만** 입력
   (입력란 앞에 도메인이 이미 붙어 있다. 전체 주소를 넣으면 홈페이지를 제출하게 되어 "가져올 수 없음")

### 네이버
1. searchadvisor.naver.com → 웹마스터 도구 → **`https://againspring.net/`** 추가
   (🔴 `http://`로 등록하면 301만 타고 색인이 안 붙는다. 입력창 기본값이 `http://`라 실수하기 쉽다)
2. 소유확인 → **HTML 파일 업로드** → 이미 배포돼 있으므로 바로 확인
3. **사이트를 클릭해 안으로 들어가야** 좌측에 「요청」 메뉴가 보인다(사이트 목록 화면엔 없음)
4. 요청 → **사이트맵 제출**: `sitemap.xml`
5. 요청 → **웹 페이지 수집**: 홈·광장·인기 사연 몇 개

> 네이버는 사이트맵만으로 크롤이 잘 안 붙는다. 개선 전 18일간 Yeti 방문이 **2회**였다(Googlebot 400회).
> 「설정 → 수집 주기」를 "빠르게"로 올려두면 초기 색인에 도움이 된다.

---

## 8. 확인 방법

배포 후 **실물로 확인해야 한다.** 이 영역의 결함은 테스트·빌드·타입체크를 전부 통과한다.

```bash
curl -s https://againspring.net/community | grep -oP '[가-힣]' | wc -l   # 1만 이상
curl -s https://againspring.net/sitemap.xml | grep -c "<loc>"            # 300 이상
curl -s https://againspring.net/robots.txt                               # Allow: / 확인
curl -s https://againspring.net/naver*.html                              # 200 + 파일 내용

# 크롤러가 실제로 오는지 (로그 90일 보존)
grep -c Googlebot env/logs/nginx/prod/access.log
grep -c Yeti      env/logs/nginx/prod/access.log
```
