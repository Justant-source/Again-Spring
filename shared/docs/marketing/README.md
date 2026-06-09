# 마케팅 자동화 시스템 — 개요

> **권위본**: 이 디렉터리 (`shared/docs/marketing/`) 가 마케팅 관련 모든 정책·API·아키텍처의 권위본입니다.

---

## 아키텍처 개요

```
[다시봄 어드민]  →  POST /api/admin/marketing/jobs  →  [Again-Spring BE]
                                                               │
                                                       AsmClient (REST)
                                                               │
                                                    POST /api/v1/jobs ↓
                                          [Again-Spring-Marketing (ASM)]
                                           WSL GPU 서버 100.115.252.61:8200
                                                               │
                                                    ┌──────────┴──────────┐
                                                    │    M0: 스텁 파이프라인 │
                                                    │    M1: Claude 카피  │
                                                    │    M2: TTS (Fish)  │
                                                    │    M3: 영상 (LTX-2)  │
                                                    │    M4: 렌더링 (FFmpeg)│
                                                    │    M5: 이미지 카드    │
                                                    │    M6: 소셜 게시     │
                                                    └─────────────────────┘
```

### 컴포넌트 역할

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| **Again-Spring (AS)** | Ubuntu 서버 | 얇은 트리거/클라이언트. 잡 생성·폴링·UI 표시만 담당 |
| **Again-Spring-Marketing (ASM)** | WSL GPU 서버 | 콘텐츠 생성·렌더링·소셜 게시 전담 |
| **ASM social-poster** | ASM `services/social-poster/` | X·Instagram·네이버 블로그 Playwright 자동 게시 |

---

## 파일 구조

```
shared/docs/marketing/
├── README.md           ← 이 파일: 전체 개요
├── api.md              ← AS ↔ ASM REST API 명세
├── architecture.md     ← 시스템 설계 결정 및 데이터 흐름
├── asm-setup.md        ← ASM 서버 설치·운영 가이드
├── platforms.md        ← 지원 플랫폼 및 콘텐츠 형식
└── social-poster.md    ← social-poster 서비스 운영 가이드
```

---

## 빠른 시작

### 어드민 사용법

1. `https://againspring.net/admin/content` → 사연 행 우측 메뉴 → **마케팅 제작 요청**
2. 타겟 플랫폼 선택 (네이버 블로그, X, 인스타그램 등)
3. 자동 게시 여부 토글
4. **마케팅 제작 요청** 클릭 → ASM에 잡 생성
5. `https://againspring.net/admin/marketing` → 잡 목록에서 진행 상황 모니터링
6. 상태가 `READY`이고 자동 게시 OFF 시 → 잡 상세 → **게시 승인** 클릭

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

---

## 관련 코드 위치

| 영역 | 경로 |
|---|---|
| BE 얇은 클라이언트 | `backend/.../marketing/AsmClient.java` |
| BE 서비스 | `backend/.../marketing/MarketingJobService.java` |
| BE 폴링 스케줄러 | `backend/.../marketing/MarketingPollingScheduler.java` |
| BE 어드민 API | `backend/.../api/admin/AdminMarketingController.java` |
| FE 잡 목록 | `frontend/app/(admin)/admin/marketing/page.tsx` |
| FE 잡 상세 | `frontend/app/(admin)/admin/marketing/jobs/[id]/page.tsx` |
| FE 생성 다이얼로그 | `frontend/components/admin/content/CreateMarketingJobDialog.tsx` |
| FE API 클라이언트 | `frontend/lib/api/admin/marketing.ts` |
| ASM 프로젝트 | `/home/justant/Data/Again-Spring-Marketing/` (WSL) |
| ASM social-poster | `ASM/services/social-poster/` (WSL) |
