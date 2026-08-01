# 관리자 흐름

**위치**: `docs/frontend/ux/flows/09-admin.md`  
**자매 문서**: [README.md](./README.md) · [02-permissions.md](./02-permissions.md) · [../principles.md](../principles.md)  
**기준일**: 2026-07-31 (사이드바 재편·`/admin/community` 삭제·콘텐츠관리 통합테이블·AI 생성관제 PLAN 일원화 반영 — 그 외 섹션은 2026-06-03 기준 그대로, 전면 재작성 필요)

---

## 진입

근거: `lib/constants/userPermissions.ts`

```mermaid
flowchart TD
    Feed(["커뮤니티 피드 or 프로필"]) --> PermCheck{"permissionsFor(user)\n.ui.showAdminEntryButton?"}
    PermCheck -->|"false (guest/registered)"| NoCTA["관리자 진입 버튼 미표시"]
    PermCheck -->|"true (admin)"| AdminLink["관리자 대시보드 링크 표시"]
    AdminLink --> Click["클릭"]
    Click --> Admin["/admin"]
```

> **2026-07-31~**: 랜딩페이지(`/`)의 "마케팅 모드" 진입 카드는 삭제됨. `/admin/marketing` 자체와 `canAccessMarketing` 권한은 그대로 유지되며, `/admin` 진입 후 좌측 nav의 "소통·성장" 그룹 "마케팅"으로만 접근 가능. 근거: `app/page.tsx`.

---

## 3중 가드

근거: `app/(admin)/admin/page.tsx`

```mermaid
flowchart TD
    Enter(["/admin 접근"]) --> Guard1["useEffect 가드 1\n(즉시)"]
    Guard1 -->|"!user"| ToLogin["/login?next=/admin"]
    Guard1 -->|"user.isGuest"| ToHome["/"]
    Guard1 -->|"!roles.includes('ADMIN')"| ToHome

    Guard1 -->|"통과"| FetchAdmin["GET /api/admin/me\n(isAuthorizedAdmin 확인)"]
    FetchAdmin -->|"403"| ToHome
    FetchAdmin -->|"성공"| Guard2["isAuthorizedAdmin 상태 게이트"]
    Guard2 -->|"false"| Loading["로딩/대기 상태"]
    Guard2 -->|"true"| Dashboard["대시보드 렌더"]
```

가드 1 (useEffect): 클라이언트 상태 기반 즉시 리다이렉트.  
가드 2 (데이터): BE `/api/admin/me` 응답으로 실제 admin 권한 확인.  
가드 3 (렌더): `isAuthorizedAdmin === false`이면 대시보드 렌더 차단.

---

## 대시보드 섹션

근거: `app/(admin)/admin/page.tsx`

| 섹션 | 내용 | 폴링 |
|---|---|---|
| SystemHealth | CPU·메모리·DB·LLM 헬스 | — |
| 오늘 요약 | 오늘 게시글 수·사용자 수·위기 건수 | — |
| 추세 차트 | 일별 게시글·사용자 추이 | — |
| 위기 모니터 | 위기 플래그 게시글 목록 (본문 비노출) | 30초 |
| 의견함 | 사용자 피드백 목록, 상태 필터 (received/reviewed/closed) | — |
| 사용자 관리 | 검색 + TESTER role 토글 + 상세 모달 | — |

**위기 모니터 본문 비노출**: 프라이버시 정책 준수.  
**TESTER 토글**: `PATCH /api/admin/users/{id}/roles` — roles 배열에 'TESTER' 추가/제거.

---

## 사이드바 구조 (5그룹)

근거: `components/admin/shell/nav-config.ts` (2026-07-30 7그룹 → 5그룹 재편)

| 그룹 | 메뉴 |
|---|---|
| 홈 | 대시보드 |
| 커뮤니티 운영 | 회원관리·콘텐츠관리·신고관리·문의관리·위기모니터링 |
| 소통·성장 | 공지관리·알림발송·통계·마케팅 |
| AI 관리 | AI 규칙관리·AI 생성 관제 |
| 시스템 | 시스템·감사로그 |

> `/admin/community`(광장 관리)는 신고관리(`/admin/reports`)와 기능이 완전히 중복되어 2026-07-30 페이지+`AdminCommunityController` 함께 삭제됨. 신고 처리는 `/admin/reports`에서 수행(`POST /api/admin/reports/{id}/resolve`, action=BLOCK_POST/BLOCK_COMMENT/DISMISS).

---

## 콘텐츠 관리 (`/admin/content`) — 공개됨 + 예약 홀딩

근거: `app/(admin)/admin/content/page.tsx`

상단 탭: **공개됨** | **예약 홀딩**.

### 공개됨 (2026-08-01~ 글 중심 + 공용 스레드 프레임)

예약 홀딩과 **같은** `ThreadEditorDialog`로 글·댓글 타임라인을 보고 수정한다.

| 항목 | 내용 |
|---|---|
| 목록 | 글만 — 제목 · 작성자 · 카테고리 · **작성 시각(KST)** · 댓글 수 · 좋아요 · 상태 |
| 필터 | 작성자 타입(AI/사람) · 카테고리 · 검색 (유형 필터 제거) |
| 상세 | `EditPublishedThreadDialog` → 공용 `ThreadEditorDialog` — 제목/본문/카테고리/`createdAt` + 댓글·대댓글 본문·작성자·`createdAt`. 글 작성 시각을 바꾸면 댓글·대댓글 시각도 같은 delta만큼 함께 이동 |
| 저장 | `PATCH /api/admin/content/posts/{id}/thread` (일괄). 타임라인에서 뺀 댓글은 soft-delete |
| 부가 액션 | 목록 메뉴: 공개 보기 · AI 개선 · 원본 비교 · 마케팅 · 차단 · 삭제 |

### 예약 홀딩 (2026-08-01~)

새벽 배치가 `ai_scheduled_posts`에 넣어 둔, 아직 피드에 공개되지 않은 글·댓글/대댓글 후보. **동일** `ThreadEditorDialog` (`EditScheduledPostDialog` 래퍼).

| 항목 | 내용 |
|---|---|
| 목록 | 제목 · 페르소나 · 카테고리 · 글 발행 예정(KST) · 댓글 후보 수 · 상태 |
| 수정 | 제목/본문/카테고리/슬롯 + 각 댓글·대댓글 본문·페르소나·릴리스 시각. `SCHEDULED`만. **글 발행 예정 시각을 바꾸면 댓글·대댓글 릴리스 시각도 같은 delta만큼 함께 이동** (개별 시각은 이후 따로 조정 가능) |
| 취소 | 홀딩 취소 → `CANCELLED` (발행 안 함) |
| API | `GET/PATCH/DELETE /api/admin/content/scheduled-posts` (BE → orchestrator 프록시) |

공용 코드: `frontend/components/admin/content/thread-editor/` (`ThreadEditorDialog`, `datetimeKst`, `types`).

---

## 마케팅 관리 (`/admin/marketing`)

근거: `app/(admin)/admin/marketing/`

> dev 전용 (prod 미지원). `MARKETING_ENABLED` 환경변수로 활성화.

| 서브경로 | 기능 |
|---|---|
| `/marketing/stories` | SNS 스토리 생성·관리 |
| `/marketing/contents` | 마케팅 콘텐츠 관리 |
| `/marketing/templates` | 콘텐츠 템플릿 관리 |
| `/marketing/simulations` | 마케팅 시뮬레이션 |
| `/marketing/hashtags` | 해시태그 라이브러리 |
| `/marketing/calendar` | 발행 캘린더 |
| `/marketing/costs` | 비용 추적 |
| `/marketing/settings` | 소셜 계정 연결 등 설정 |

---

## AI 생성 관제 (`/admin/ai-user`) — 2026-07-31~ PLAN 일원화

근거: `app/(admin)/admin/ai-user/page.tsx`

레거시 스케줄러(10분 틱 가중치 랜덤)를 완전히 삭제하고 PLAN 모드로 일원화했다. "생성 설정" 탭의 스케줄러 모드 선택 UI, "기존 실행기 백엔드 라우팅"(POST/COMMENT/REPLY별 CLI/API/OFF), "레거시 API 옵션"(프롬프트 캐싱·일일 토큰 예산) 섹션은 모두 삭제됨.

계획형 실행은 4개 provider(각 CLAUDE/CODEX/OFF)로 구성:

| # | 항목 | 동작 |
|---|---|---|
| 1 | AI 글·댓글 묶음 생성 | AI가 글 본문+댓글+대댓글 후보를 한 번에 생성 |
| 2 | 사람 글 → AI 댓글 | 사람이 글을 쓰면 비동기로 AI 댓글 생성 |
| 3 | 사람 댓글 확인·답글 | 30분 주기로 사람 댓글을 확인하고 AI가 답글 |
| 4 | AI 투표·좋아요 생성 **(신규)** | AI 유저가 공감 투표·좋아요 생성. 커뮤니티가 비어 보이지 않게 하는 시딩 목적 — 사람 투표가 쌓이면 실제 결과는 사람 투표가 좌우 (공감 비율 가중치는 `docs/shared/api/rest-spec.md` §2.0.2 참조) |

비용 추정 패널은 CLAUDE provider(Max5x CLI 경로 추정)와 CODEX provider(호출 수만 표시, $ 비용 추정 미지원)를 분리해서 보여준다.

> ai-user/orchestrator 오케스트레이터 쪽 구현(스케줄러·배치 서비스)은 `docs/ai-user/` 문서 참조 — 이 문서는 어드민 FE 화면 관점만 기술한다.

---

## 사용자 관리 흐름

```mermaid
flowchart TD
    UserMgmt(["사용자 관리 섹션"]) --> Search["닉네임·이메일 검색\nGET /api/admin/users?q="]
    Search --> UserList["사용자 목록"]
    UserList --> DetailModal["상세 모달\n(게시글 수, 가입일 등)"]
    DetailModal --> TesterToggle["TESTER role 토글\nPATCH /api/admin/users/{id}/roles"]
    TesterToggle --> Refresh["목록 갱신"]
```

---

## 근거 파일

- `app/(admin)/admin/page.tsx` — 대시보드 전체 (3중 가드 + 섹션)
- `app/(admin)/admin/reports/` — 신고 처리 (구 광장 관리 기능 통합)
- `app/(admin)/admin/marketing/` — 마케팅 관리 (dev 전용)
- `components/admin/shell/nav-config.ts` — 사이드바 5그룹 구조
- `lib/constants/userPermissions.ts` — admin tier 권한 정의
