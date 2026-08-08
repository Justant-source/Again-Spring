# 마케팅 자동화 시스템 — 개요

> **권위본**: 이 디렉터리 (`docs/shared/marketing/`) 가 마케팅 관련 모든 정책·API·아키텍처의 권위본입니다.

---

## 아키텍처 개요

```
[다시봄 어드민 /admin/marketing]
        │  대기 보드 · 핀 · 초안 · 상한/가중치
        │  T+24h 스케줄러 / 완료탭 강제
        ▼
[Again-Spring BE]  MarketingHoldingCommitService → MarketingJobService
        │
        │  AsmClient (REST)
        ▼
POST /api/v1/jobs → [Again-Spring-Marketing (ASM)]
                     WSL GPU 서버 100.115.252.61:8200
                     M0~M6 파이프라인 → 소셜 게시
```

### 컴포넌트 역할

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| **Again-Spring (AS)** | Ubuntu 서버 | 홀딩 보드·24h 확정·잡 폴링·Admin UI. 얇은 ASM 클라이언트 |
| **Again-Spring-Marketing (ASM)** | WSL GPU 서버 | 콘텐츠 생성·렌더링·소셜 게시 전담 |
| **ASM social-poster** | ASM `services/social-poster/` | Playwright 자동 게시. **24h 분배 = 공유 풀 · 영상 우선 · 잔여 글 · 1사연=1칸** |

**접속**: AS 호스트 Tailscale `100.81.189.92`에서 `ssh justant@100.115.252.61` (암호 없음) → `~/Data/Again-Spring-Marketing`

## 파일 구조

```
docs/shared/marketing/
├── README.md           ← 이 파일: 전체 개요
├── api.md              ← AS ↔ ASM REST API 명세
├── architecture.md     ← 시스템 설계 결정 및 데이터 흐름
├── asm-setup.md        ← ASM 서버 설치·운영 가이드
├── platforms.md        ← 지원 플랫폼 · 24h 홀딩/배분 C · auto on/off
├── credentials.md      ← 플랫폼 계정 자격증명 저장·암호화 정책
├── social-poster.md            ← social-poster 서비스 운영 가이드
├── x-thread-strategy.md        ← X 스레드 전략 (솔로 3~4 / 양면 최대 6단)
├── instagram-feed-strategy.md  ← IG 하이브리드 캐러셀 (24h · 글 슬롯)
└── youtube-shorts-strategy.md  ← Shorts/Reels (WaggleBot · 24h 영상 슬롯 듀얼 자동 게시)
```

---

## 빠른 시작

> **24h 자동 분배 (배분 C)**: 대기 보드 → T+24h 확정. 공유 풀(`dailyTextCap` 기본 6) · 영상 우선 · VIDEO는 video+text companion(IG feed⊥reels) · **1사연=1칸**. 상세 [`platforms.md`](platforms.md).

### 어드민 `/admin/marketing` 탭

| 탭 | 역할 |
|---|---|
| **대기** | 24h N-top 홀딩 보드 · 카드 라벨 = 포맷(VIDEO/TEXT) + 상태(후보/후보 외) · 핀 = 인라인 포맷 select(`VIDEO\|TEXT`, soft-reserve) · 초안 다이얼로그 = 게시글 제목 + 작성자/상대방 본문 read-only 표시, `promoTitle` 숨김, `tags`·`topComments`만 편집 가능 · 일일 상한·점수 가중치 |
| **완료** | 사연(story) 단위 리스트 · 상단 **게시 이력**(COMMITTED) — 클릭 시 플랫폼별 상태+URL+잡 상세 링크(다이얼로그 내 승인/재시도 없음) · 하단 **탈락**(DROPPED) — 강제 배포(인라인 모드 선택 + 확인) · 플랫폼 성과 카드·잡 보드 박스·구 타임라인 UI는 이 탭에서 제거됨 |
| **설정** | 플랫폼 자동 on/off · 플랫폼 계정 자격증명 (변경 없음) |

> **신규 홀딩 기본 태그 시드**: `#다시봄` `#공감비율` `#[카테고리]` — 신규 홀딩 생성 시에만 적용, 기존 홀딩 백필 없음 (`MarketingHoldingBriefSeeder`).
> **긴급 재게시**: 완료 탭 다이얼로그에는 승인/재시도가 없다 — 잡 상세 페이지(`/admin/marketing/jobs/[id]`)의 게시/재게시 버튼으로 처리.

1. `https://againspring.net/admin/marketing` (기본 탭 = **대기**)
2. 상한·가중치 조정 → 순위/컷라인 즉시 반영
3. 필요 시 핀으로 soft-reserve · 초안 PATCH (tags·topComments)
4. T+24h에 스케줄러가 핀→자동 영상→자동 글 순으로 COMMITTED, 미선정 DROPPED
5. **완료** 탭에서 게시 이력(플랫폼별 상태·URL) 확인 · 탈락 건 강제 배포 · 긴급 시 잡 상세에서 직접 게시/재게시
6. **설정**에서 채널 auto on/off · 계정 자격증명

> 수동 `POST /api/admin/marketing/jobs`는 BE에 남아 있으나(스케줄러·force·e2e), Admin UI의 주 경로는 **대기 보드 → 자동/강제 확정**이다.

### IG 단건 검증 (요청 시 1사연)

1. 설정에서 대상 채널만 on · 또는 강제/`TEXT` 핀으로 피드 슬롯 확보
2. 빌드 완료 후 아티팩트: `card_01` 훅(4:5) · 중간 장 · 비율카드 · `upload.json` 캡션
3. 완료 탭에서 잡 상세 → 게시 승인 → 인스타 앱에서 확인

> 중간 장·댓글 글자 크기 규칙: [`instagram-feed-strategy.md`](instagram-feed-strategy.md) §2.1.1–2.1.2 (`commentsReadableBudget`은 IG만, X는 최대 4장 고정).

### 잡 상태 흐름

```
REQUESTED → QUEUED → RUNNING → READY → PUBLISHING → PUBLISHED
                                  ↓
                               (수동 승인 시 PUBLISHING)
                ↓
             FAILED / STALE (폴링 5회 연속 실패)
```

---

## 환경 변수 (Again-Spring 측)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `ASM_BASE_URL` | `http://100.115.252.61:8200` | ASM 서버 주소 |
| `ASM_API_TOKEN` | `asm-dev-token-change-in-prod` | Bearer 인증 토큰 |
| `ASM_ENABLED` | `true` | false 시 잡 생성 API 비활성화 |
| `ASM_POLL_INTERVAL_MS` | `15000` | 폴링 주기 (밀리초) |
| `ASM_REQUEST_TIMEOUT_MS` | `10000` | ASM HTTP 타임아웃 |

> **ASM 측 추가 env**: `ASM_CREDENTIAL_KEY` (base64 32바이트) — 플랫폼 계정 자격증명 AES-256-GCM 마스터키.
> ASM `.env`에만 두고 git 커밋 금지. 생성: `openssl rand -base64 32`. 상세: [`credentials.md`](credentials.md)

---

## 관련 코드 위치

| 영역 | 경로 |
|---|---|
| BE 얇은 클라이언트 | `backend/.../marketing/AsmClient.java` |
| BE 홀딩·확정 | `backend/.../marketing/holding/MarketingHoldingService.java` · `MarketingHoldingCommitService.java` |
| BE 플랫폼 auto / 점수 / 상한 | `MarketingPlatformAutoService` · `MarketingScoreWeightService` · `MarketingQuotaService` |
| BE 잡·폴링 | `MarketingJobService.java` · `MarketingPollingScheduler.java` |
| BE Admin API | `AdminMarketingController` · `AdminMarketingHoldingController` · `AdminMarketingCompletedController` · `AdminMarketingPlatformController` |
| FE 마케팅 허브 | `frontend/app/(admin)/admin/marketing/page.tsx` (탭: 대기/완료/설정) |
| FE 홀딩 UI | `frontend/components/admin/marketing/HoldingBoard.tsx` · `HoldingControlsBar.tsx` · `HoldingDraftDialog.tsx` |
| FE 잡 상세 | `frontend/app/(admin)/admin/marketing/jobs/[id]/page.tsx` |
| FE API 클라이언트 | `frontend/lib/api/admin/marketing.ts` |
| FE 플랫폼 계정 UI | `frontend/components/admin/marketing/PlatformCredentialsSection.tsx` · `PlatformAutoSection.tsx` |
| ASM 자격증명 (crypto/스키마/API) | `app/core/crypto.py` · `app/domain/credentials.py` · `app/api/routes_credentials.py` |
| ASM 프로젝트 | `/home/justant/Data/Again-Spring-Marketing/` (WSL) |
| ASM social-poster | `ASM/services/social-poster/` (WSL) |
