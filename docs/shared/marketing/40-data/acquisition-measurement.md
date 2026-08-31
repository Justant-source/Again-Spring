# 유입 계측 — 발행 다음 칸을 채우는 지표

> **권위본**: 이 문서 (2026-08-29 신설).
> 관련: [`platforms.md`](../70-policy/platforms.md) (발행·점수) · [`api.md`](../50-api.md) §4.2.1 (플랫폼 통계) · [`seo.md`](../70-policy/seo.md) (검색 유입)
> 스키마: [`../../50-api/database-schema.md`](../../50-api/database-schema.md) `visit_events` · `users` 유입 컬럼

---

## 0. 왜 이 문서가 생겼나

런칭 후 30일간 4채널에 1,068건을 발행해 **YouTube 85,678조회 · X 19,027노출**을 얻었는데
**신규 가입은 1명**이었다. 원인을 조사하다 더 근본적인 문제를 발견했다 — **계측 자체가 깨져 있었다.**

| 결함 | 실태 |
|---|---|
| FE↔BE 필드명 불일치 | `visits.ts`가 snake_case(`utm_source`)로 보내는데 DTO는 camelCase(`utmSource`) 기대 → Jackson이 조용히 폐기. `session_key` **100% NULL**, UTM **전량 유실** |
| 가입 귀속 미배선 | `users.acquisition_source`/`acquisition_campaign` 컬럼은 있는데 **엔티티 매핑도 기록 코드도 없어** 전 행 NULL |
| 방문 기록 조건부 | UTM이나 외부 referrer가 있을 때만 기록 → **방문 분모 자체가 없음** |
| 봇 미분리 | nginx의 t.co 유입 114건이 전부 OVH 데이터센터 링크검사 봇이었다. 걸러내지 않으면 0을 성과로 오독 |
| 화면 부재 | 어드민이 "발행 성공"까지만 보여줌 → **"8만 뷰가 방문 0"을 한 달간 어떤 화면도 말해주지 않음** |

그래서 "마케팅이 효과가 있었나"에 **답할 수단이 없었다.** 이 문서는 그 수단을 정의한다.

---

## 1. 퍼널 정의

```
발행 (marketing_job)
  └→ 노출·조회 (marketing_publication_stats)      ← 플랫폼 API/스크레이핑
       └→ 클릭 → 방문 (visit_events)               ← 여기부터 이 문서
            └→ 고유 방문자 (visitor_key)
                 └→ 가입 (users.acquisition_source)  ← 종단 지표
```

**플랫폼 지표까지는 [`api.md`](../50-api.md) §4.2.1**, **방문부터는 이 문서**가 권위본이다.

---

## 2. 방문 기록 — `visit_events`

### 2.1 기록 시점

`frontend/components/VisitTracker.tsx`가 **모든 페이지뷰**에서 `POST /api/public/visits`.
`/admin` 경로는 제외. 같은 세션·같은 경로는 `sessionStorage`로 1회만.

> 🔴 2026-08-29 이전에는 UTM/외부 referrer가 있을 때만 기록했다. 개선 효과를 재려면
> 분모(전체 방문)가 필요하므로 전 페이지뷰 기록으로 바꿨다.

### 2.2 🚨 필드명은 camelCase — 절대 규칙

`lib/api/visits.ts` → `PublicVisitController.VisitRequest` 는 **Jackson 기본(camelCase)** 으로 역직렬화한다.
snake_case로 보내면 **예외 없이 조용히 버려진다**(`FAIL_ON_UNKNOWN_PROPERTIES` 기본 off).

| 보내야 함 | 보내면 안 됨 |
|---|---|
| `utmSource` `utmMedium` `utmCampaign` `utmContent` | `utm_source` … |
| `sessionKey` `visitorKey` | `session_key` `visitor_key` |

`path`·`referrer`는 이름이 같아 우연히 살아남았다 — 그래서 **한 달간 아무도 몰랐다.**
회귀 방지 테스트: `PublicVisitControllerTest`(계약) · e2e journey `18-C`(네트워크 본문 검사).

### 2.3 식별자 3종

| 키 | 수명 | 용도 |
|---|---|---|
| `visitorKey` (`as_vid` 쿠키) | 1년 rolling | **고유 방문자·재방문**. 집계의 기본 단위 |
| `sessionKey` (sessionStorage) | 브라우저 세션 | 한 세션 안의 이동 묶기 |
| `as_utm` 쿠키 | 30일 **first-touch** | 가입 시 채널 귀속용. 이미 있으면 덮어쓰지 않음 |

`crypto.randomUUID`는 비보안 컨텍스트(카카오톡 인앱 http)에서 undefined다 — 폴백 필수.

### 2.4 봇 분류

`VisitorClassifier`가 저장 시점에 User-Agent로 판정해 `is_bot`에 남긴다.

- **집계는 항상 `is_bot = 0`으로 필터**한다.
- 봇 행도 **버리지 않는다** — `user_agent`를 함께 보존해 규칙이 바뀌면 과거 행을 재분류할 수 있어야 한다.
- 오탐이 미탐보다 비싸다. 실제 방문자를 통계에서 지우는 쪽이 더 큰 손해다.
- UA가 없거나 `Mozilla/`로 시작하지 않으면 봇으로 본다.

### 2.5 rate limit

IP당 **60초 슬라이딩 윈도우 30건**.

> 🔴 이전 구현은 "윈도우 시작 시각" 하나만 저장하고 그 뒤 2초 안의 요청을 전부 거부했다.
> 전 페이지뷰를 기록하게 되면서 홈 → 사연으로 빠르게 이동하는 **정상 사용자의 두 번째
> 방문이 429로 유실**됐다(e2e 실측). 계측이 사용자 행동을 막아서는 안 된다.
> 회귀 테스트: `VisitRateLimitTest`.

---

## 3. 가입 귀속 — `AcquisitionAttribution`

`as_utm` 쿠키(first-touch)를 읽어 신규 `users` 행에 채운다. 귀속 지점 4곳:

| 지점 | 코드 |
|---|---|
| 이메일 가입 | `AuthService.signup` |
| OAuth 신규 | `AuthService.oauthSignIn` |
| 게스트 생성 | `AuthService.guest` |
| 게스트→회원 승계 | `AuthService.migrateGuestData` → `inherit()` |

**게스트에도 채널을 남기는 이유**: 마케팅 링크로 들어와 게스트로 둘러본 뒤 며칠 후 가입하는
흐름에서, 그때 쿠키가 만료됐어도 게스트 행에서 승계해 유입 경로를 잃지 않는다.

**first-touch 정책**: 마지막 클릭이 아니라 "처음 데려온 채널"을 남긴다. 발견 채널을 알고 싶기 때문.

요청 컨텍스트가 없는 경로(스케줄러·배치)에서는 안전하게 no-op이다 — **귀속 실패가 가입을 막아선 안 된다.**

---

## 4. 조회 — 퍼널 API·화면

```
GET /api/admin/marketing/stats/acquisition?days=30
→ { days, totalVisits, totalVisitors, totalSignups, botSplit{human,bot},
    byChannel[{source,visits,visitors,sessions,signups}],
    daily[{date,visits,visitors,signups}], topReferrers[], topPaths[] }
```

- 서비스: `AcquisitionFunnelService` · 화면: 어드민 마케팅 → 통계 탭 `AcquisitionFunnelPanel`
- **0을 회색으로 죽이지 않고 눈에 띄게 표시**한다. 이 화면의 존재 이유가 "0을 보이게 하는 것"이다.

### 집계 규칙

| 규칙 | 이유 |
|---|---|
| `is_bot = 0` 필터 | 링크검사 크롤러를 유입으로 세면 0을 성과로 오독 |
| 채널 미상 가입은 `(unknown)`으로 분리 | 합계를 부풀리지 않기 위해 |
| `synthetic`(AI 페르소나)·게스트 제외 | 사람 가입만 센다 |
| `id NOT LIKE 'e2e%'` 제외 | prod에 e2e 픽스처 계정 10개가 남아 있어 30일 가입이 11명으로 잡혔다(실제 1명). **근본 해결은 prod 계정 정리** |

---

## 5. 🕳 알려진 함정

| 함정 | 내용 |
|---|---|
| **V120 이전 행** | `user_agent`가 없어 `is_bot`이 기본값 0이다. 사람으로 잡히니 **장기 창(30·90일) 비교 시 주의**. 주 단위 비교는 영향 없음 |
| **nginx 로그 보존** | 이전엔 docker stdout뿐이라 18일 뒤 소실됐다. 지금은 `env/logs/nginx/`에 90일 보존([`../../../env/deployment.md`](../../../env/deployment.md)) |
| **누적 조회수 비교 금지** | 플랫폼 조회는 시간에 따라 쌓인다. 반드시 **동일 일령**(`DATEDIFF(collected_at, created_at)`)으로 코호트를 맞춰 비교할 것 |
| **동시 변경** | `utm_source`로 채널 간 구분은 되지만, 같은 채널 안의 두 변경(예: About 링크 vs 훅 보강)은 섞인다 |

---

## 6. 기준선 — 2026-08-29 스냅샷

개선 효과 판정의 유일한 근거. **이 수치와 비교한다.**

| 지표 | 값 | 비고 |
|---|---|---|
| 신규 가입 (사람, 7일) | **0** | 봇·게스트·페르소나·e2e 제외 |
| 신규 가입 (사람, 30일) | **1** | DB상 11이나 10건은 `e2epersona01~10` |
| UTM 방문 (30일) | **2** | 둘 다 8/1 테스트. **계측 버그로 실제 클릭도 기록 불가였음** |
| 한국 모바일 실방문자 | **2~9 / 일** | nginx 18일치 UA·IP 수기 분류 |
| 발행 성공 (7일) | 96 | 4채널 합계 |
| YouTube 누적 조회 / 좋아요 | 85,678 / 151 | 좋아요율 0.176% |
| YouTube v2 코호트 0일차 평균 조회 | 475 | v1 동일 일령 1,266 대비 37% |
| YouTube v2 유지율 | 53% | 17.5초 / 33초. v1은 36% |
| X 스레드 링크 단 조회 | **3** | main 97 · reply1 94 · reply2 3 |
| IG 릴스 / 피드 도달 | 2,778 / 36 | 좋아요 1 / 0 |

### 판정 순서

1. **UTM 방문 > 0** — 1차 관문. 계측이 살아 있고 클릭 경로가 열렸다는 최소 증거
2. **채널별 분해** — `utm_source`로 무엇이 작동했는지 구분
3. **v2 0일차 조회** — 기준 475. **동일 일령 비교로만** 읽는다
4. **채널 귀속 가입** — 기준 0. 1건이라도 나오면 퍼널 전체가 연결된 것

> 검색 유입은 색인에 며칠~2주 걸린다. **첫 주 판정에 포함하지 말 것** — [`seo.md`](../70-policy/seo.md) 참조.
