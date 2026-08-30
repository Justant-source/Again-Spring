# 마케팅 시스템 아키텍처

> **Phase 2 = 타깃 SSOT** (코드 병렬 착수). 분배·영상·통계는 [`platforms.md`](platforms.md) · [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) · [`api.md`](api.md).  
> Phase 1 유지: UTM · 텔레그램 댓글 노티 · 태그 · 배심원 없음 · 2027-01 고지. **저녁 슬롯은 폐기** — READY 즉시 발행.

## 설계 원칙

1. **AS = 얇은 트리거** — Again-Spring은 홀딩·24h 확정·잡 생성·콜백 수신·폴링·상태 표시·통계/리포트. 콘텐츠 생성 로직 없음.
2. **ASM = 콘텐츠 공장** — 카피라이팅·음성·영상·이미지·게시 전담. GPU 서버(WSL RTX 3090).
3. **이중 동기화** — 푸시(콜백) + 풀(폴링). ASM이 종료 상태 도달 시 AS 콜백 엔드포인트로 즉시 전송, 폴링은 콜백 미수신 시 재조정용.
4. **멱등성** — AS가 각 잡 생성 시도마다 Idempotency-Key(UUID)를 발송. ASM이 중복 감지 후 같은 응답 반환.
5. **단방향 초기 요청** — 잡 생성은 AS → ASM. 이후 ASM → AS 콜백 + AS 폴링으로 동기화.
6. **Phase 2 분배** — 플랫폼별 popularity·일일 cap(기본 3). 같은 사연 멀티 플랫폼 허용. IG feed⊥Reels만 점수 배타(동점→Reels).

---

## 렌더 프로필 (Phase 3: 2026-08-23)

**렌더 프로필**은 WaggleBot이 영상을 렌더링할 때 사용할 기능 세트를 지정합니다. 기본값은 env `MARKETING_RENDER_PROFILE` (기본 `marketing_fast`), 잡 생성 시 `POST /api/v1/jobs`의 `renderProfile` 필드로 개별 지정 가능.

| 프로필 | 설명 | 상태 |
|---|---|---|
| `marketing_fast` | 현행 운영 중인 기본 프로필. 간편 레이아웃, BGM/SFX/전환 없음 | 활성 |
| `marketing_v2` | 신규 v2 렌더. BGM(감정별 2곡, `assets/media/bgm/<emotion>/`) + SFX(7종 팔레트, `assets/media/sfx/<event>.wav`) + ffmpeg 전환(xfade) + 앱 크롬 제거(인트로 포함) + 투표 비율 바(실제 `empathy_ratio` 없으면 미표시) | Phase 3 기준선 수집 중, 사용자 승인 대기 |

**SFX 팔레트 — 삽입 지점 17개.** 음원·음량·오프셋은 **어드민 「설정 → 효과음 매핑」에서 직접 고른다**
(`GET`/`PUT /api/admin/marketing/sfx/mapping` → ASM → WaggleBot `/api/sfx/mapping` → `settings.yaml`의 `sfx.active`).

| 구분 | 이벤트 | 붙는 자리 | 영상당 |
|---|---|---|---|
| 씬 전환 | `hook_in` | 인트로 시작 | 1 |
| | `intro_out` | 인트로 → 본문 첫 항목 | 1 |
| | `page` | 본문 3줄마다 (화면이 비워지는 순간) | 2~3 |
| | `card_in` | 본문 → 시봄이 카드(`image_text`) | 2~3 |
| | `card_out` | 시봄이 카드 → 본문 | 2~3 |
| | `section_whoosh` | 본문 → 댓글 | 1 |
| | `bubble` | 댓글 카드마다 | 1~3 |
| | `outro_in` | 댓글 → 아웃트로 | 1 |
| 시봄이 | `sibom_punch` | 캐릭터 등장(24프레임 페이드인) | 2~3 |
| | `motion_sway`·`sink`·`shake`·`pop`·`sob` | 카탈로그 `motion` 값으로 갈라짐 | 2~3 |
| 화면 요소 | `text_line` | 본문 줄이 하나 나타날 때 — 가장 잦아 가장 낮은 음량 | 5~8 |
| | `best_badge` | 추천 1위 댓글 배지 | 1 |
| | `vote_fill` | ⚠️ 투표 바 — **렌더러에 그리는 코드가 없다.** 소리만 있고 그림이 없는 상태 | 1 |

**같은 순간에 겹치는 소리는 오프셋으로 시간차를 준다.** 시봄이 카드 지점은
`card_in`(착지) → `sibom_punch`(+0.25초, 캐릭터 등장) → `motion_*`(+0.95초, punch 24프레임이 끝난 뒤)
순으로 벌려 하나의 연출처럼 들리게 했다. 댓글 구간도 `section_whoosh` → `bubble` → `best_badge` 로 벌린다.

간격 규칙은 실제 재생 시각(오프셋 반영) 기준이다. `sfx.short_gap_events`에 적힌 이벤트는
`short_gap_sec`(0.34초), 나머지는 `min_gap_sec`(2.0초). **오프셋이 실제 간격을 정하므로 규칙은 최소만 건다** —
2.5초를 일괄 적용했을 때 일부러 겹쳐 배치한 소리가 통째로 버려졌다.
영상당 상한은 `sfx.max_per_video`(현재 40)가 권위본이다.

음원은 `assets/media/sfx/_library/<카테고리>/`에 Mixkit 262개(상업 사용 가능·표기 불필요).
`file` 값은 `assets/media/sfx/` 기준 상대경로이고 하위 경로도 그대로 해석된다(`_resolve_sfx_path`).
**`assets/`는 gitignore라 음원 파일은 git에 없다** — 출처 URL은 `assets/media/sfx/LICENSES.md`에 기록.
**BGM 전역 스위치 — `settings.yaml`의 `bgm.enabled` (현재 `false`, 모든 렌더에서 제외)**

`false`면 프로필과 무관하게 어떤 렌더에도 BGM이 들어가지 않는다. 고르는 기능
(카탈로그 API · 어드민 곡 선택 · 잡별 `bgmTrack` · `hook_emotion` 자동 선택)은 그대로 살아 있어
`true`로 되돌리면 고른 곡 그대로 복귀한다. 어드민 「설정 → 배경음악 (BGM)」 박스의 체크박스로 켜고 끄며,
`PUT /api/admin/marketing/bgm/settings` → ASM → WaggleBot `PUT /api/bgm/settings`로 전달된다.
차단은 소비 지점(`_bgm_allowed_for_profile`) 한 곳에서만 한다 — director는 여전히 곡을 고른다.

켜져 있을 때: `volume=0.40` + 사이드체인 더킹(`threshold=0.10 ratio=4`)으로 목소리보다 약 14dB 아래.
**이 값은 실측으로 잡았다** — 이전 `volume=0.15`·`threshold=0.03 ratio=9`는 원본 −18.5dB를 최종 −44.2dB까지
끌어내려 목소리(−16.0dB)보다 28dB 아래였고, 사실상 들리지 않았다.
검증은 말이 멈추는 구간 비교가 유일하게 결정적이다(−14dB 신호는 전체 볼륨에 0.2dB만 더한다):
TTS 원본 −70.4dB(무음) vs 최종본 −22.5dB.

BGM 곡은 어드민 설정에서 직접 고를 수 있고(`shortform_video` 자격증명 `bgm_track`), 비우면 `hook_emotion`으로 자동 선택된다.

**⚠️ 에셋 위치 중요**: BGM/SFX는 반드시 `assets/media/` 하위에 있어야 합니다. WaggleBot 컨테이너가 `MEDIA_DIR=/app/media`로만 마운트하므로, `assets/voices/`, `assets/images/` 등 다른 디렉터리는 보이지 않습니다.

---

## 데이터 흐름

### 1. 잡 생성 (Idempotency 포함)

```
어드민 클릭 "마케팅 제작 요청"
    │
    ▼
POST /api/admin/marketing/jobs { postId, targets, autoPublish }
    │
    ▼
AdminMarketingController
    │
    ▼
MarketingJobService.createJob()
    ├── Post 조회 (PostRepository)
    ├── StoryBrief 생성 (제목·요약·관점·empathy_ratio)
    ├── Idempotency-Key 생성 (UUID)
    ├── AsmClient.createJob(brief, targets, options, idempotencyKey, callbackBaseUrl)
    │       └── POST http://100.115.252.61:8200/api/v1/jobs + Idempotency-Key
    │               └── ASM이 ULID job_id 반환, idempotency 캐시에 저장
    └── MarketingJob 저장 { remoteJobId, postId, status=REQUESTED, ... }
```

### 2. 콜백 모델 (즉시 동기화)

```
ASM이 READY/PUBLISHED/PARTIAL/FAILED 도달
    │
    ▼
ASM: POST /api/internal/marketing/callback
    ├── Authorization: Bearer {ASM_CALLBACK_TOKEN}
    ├── body: { job_id, status, phase, progress, artifacts, publications, error }
    │
    ▼
AS CallbackController
    ├── 토큰 검증
    ├── job.applyRemote(status, phase, progress, ...)
    ├── poll_fail_count = 0 (재설정)
    └── 응답 204 No Content
```

### 3. 폴링 루프 (재조정용)

```
MarketingPollingScheduler (15초마다)
    │
    ▼
findByStatusIn([QUEUED, RUNNING, READY, PUBLISHING, STALE])
    │
    ▼
for each job:
    READY + artifacts 있음 → ASM GET 스킵 (미리보기 완료; 게시는 due-slot/수동)
    STALE + artifacts 있음 → READY 복구 후 스킵
    ASM circuit open(연결 실패 후 5분) → 사이클 전체 GET 스킵
    │
    AsmClient.getJob(remoteJobId)
    │
    ├── 성공 → job.applyRemote(...) ; poll_fail_count = 0
    │
    └── 실패 → markPollFailure() ; circuit 5분 open ; 남은 잡 break
                poll_fail_count >= 5 → STALE (artifacts 있으면 READY 유지)
                (STALE 후 지수 백오프, 24h 초과 시 FAILED)
```

### 4. 수동 게시 승인

```
어드민 "게시 승인" 클릭 (status==READY && autoPublish==false)
    │
    ▼
POST /api/admin/marketing/jobs/{id}/publish
    │
    ▼
MarketingJobService.triggerPublish(id)
    ├── status == READY 검증
    └── AsmClient.publish(remoteJobId)
            └── POST /api/v1/jobs/{remote_job_id}/publish
                    └── ASM: status = PUBLISHING → 소셜 게시 → PUBLISHED (콜백)
```

### 5. 자동 발행 — READY 즉시 (저녁 슬롯 폐기)

T+24h 자동 선정 잡과 강제 배포 잡은 `auto_publish=true`로 ASM에 생성한다.
X 캡처·IG 피드·Reels·Shorts 모두 채널 렌더가 READY가 되면 **즉시 게시**한다.
`scheduled_publish_at`은 V117 NOT NULL용 생성 시각일 뿐 게이팅하지 않으며, 다음날 저녁 이월도 하지 않는다.

ASM Waggle 영상 경로도 X와 같이 `auto_publish`면 READY 직후 `PUBLISHING`으로 올린다.
Again-Spring 폴러는 READY auto-publish 잡을 슬롯 대기 없이 `triggerPublish`한다.

### 6. Phase 2 분배 · 영상 · 통계 루프

```
T+24h MarketingHoldingCommitService
    │  틱은 오케스트레이션만. 사연별 commit/drop 은 REQUIRES_NEW.
    │  선정 후 잡 insert 실패 → DROPPED 금지, due 유지, 텔레그램, 다음 틱 재시도.
    │
    ├─ 채널별 score (platforms.md §식) DESC · 선택 채널 실제 1-based rank를 holding JSON에 기록
    ├─ 채널별 cap (기본 3) 잔여까지 COMMIT
    ├─ 같은 사연 → 멀티 플랫폼 허용
    ├─ IG: score_feed vs score_reels (동점→Reels) 만 배타
    │
    ├─ Reels/Shorts 확정 시
    │     ├─ 변형 훅·스크립트 생성 (2단)
    │     ├─ hook_emotion → brief → WaggleBot S2 Pro TTS
    │     └─ 유니크 렌더 (Reels≤30s / Shorts≤45s · 전문 낭독 금지)
    │
    └─ READY 즉시 publish
            │
            ▼
        [발행 성공] → 댓글 감시 창 (published_at + N h, 기본 24)
            → 신규 댓글 → WaggleBot 텔레그램 → 운영자 수동 답글
            → 플랫폼 통계 수집 (X/IG/YT)
            → 주간 리포트 + (auto_adjust on 시) 가중치 소폭 보정
```

X 운영 설정(아침/밤·대댓글/선댓글 한도·킬스위치·페르소나 학습)은 어드민 `/admin/marketing` 설정 탭과
`GET`/`PUT /api/admin/marketing/x-ops`(`marketing.x.*`)에 있다. **발행 스위치 기본 꺼짐.** 페르소나 학습은 기본 켜짐(매일 04:30 KST, FxTwitter 읽기). `POST /x-ops/learn`으로 즉시 실행. 실제 X 발행·댓글 파이프는 아직 미연결이라 스위치를 켜도 글이 나가지 않는다.
프롬프트 자동 패치는 **금지**. AI 고지는 **2027-01**.

### 댓글 알림 정확성

Instagram Reels/Feed의 숫자 media ID는 Graph API 댓글 조회를 우선한다. Graph API가
성공적으로 빈 목록을 반환하면 댓글이 없는 것으로 확정하며 Playwright DOM 스크래핑으로
폴백하지 않는다. 페이지의 `계속`·`다른 프로필 사용하기` 같은 UI 텍스트를 댓글로 오인해
텔레그램을 보내는 것을 막기 위해서다.

Graph API 자체가 응답하지 못했을 때만 Playwright fallback을 시도하며, 플랫폼이 발급한
안정적인 댓글 ID가 없는 DOM 항목은 알림 대상에서 제외한다. 알림에는 댓글 본문·가능하면
작성자·**원래 발행 permalink**를 담는다. 브라우저가 로그인/홈으로 리다이렉트된 현재 URL은
링크로 사용하지 않는다.

### 데이터 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│ Again-Spring (AS) 어드민                                     │
└────────┬──────────────────────────────────────────────────┘
         │
         │ 1. POST /api/admin/marketing/jobs
         ▼
┌────────────────────────────────────────────────────────────┐
│ Again-Spring Backend                                        │
│ ├─ POST /api/admin/marketing/jobs                          │
│ ├─ GenerateIdempotency-Key (UUID)                          │
│ └─ CreateJob + Callback URL 포함                            │
└────┬──────────────────────────────────────────┬──────────┘
     │                                          │
     │ 2. POST /api/v1/jobs                    │ 3. [Callback] POST /api/internal/marketing/callback
     │ + Idempotency-Key                       │    + Status/Artifacts (READY/PUBLISHED/PARTIAL/FAILED)
     │                                          │
     ▼                                          │
┌──────────────────────────────────────┐       │
│ ASM (Again-Spring-Marketing)         │       │
│ ├─ QUEUED → RUNNING → READY/FAILED   │──────┘
│ └─ Content Generation Pipeline       │
└──────────────────────────────────────┘
     │
     ▼
AS 어드민: 폴링 재조정 (15초, STALE 상태 지수 백오프)
```

---

## 생성 기록 수집 (V118)

마케팅 영상 변형 단계에서 LLM 호출의 프롬프트·응답·시봄이 가드 로직을 감사 및 디버깅 목적으로 저장합니다.

| Phase | 스테이지 | 채널 | 기록 내용 |
|---|---|---|---|
| **Phase 1** | `VIDEO_VARIANT` | instagram_reels, youtube_shorts | LLM 훅/스크립트 생성 호출, 최종 시봄이 플랜, guard_log |
| Phase 2 (향후) | `PROMO_TITLE` | x_thread 등 | 프로모 훅 생성 호출 |
| Phase 3 (향후) | 효과음/배치 확장 | WaggleBot 채널 | 음향 설정·시봄이 애니메이션 선택 이력 |

**테이블**: `marketing_generation_trace` (V118, `backend/src/main/resources/db/migration/V118__add_generation_trace.sql`)

각 trace는:
- `llm_prompt` (LONGTEXT): 전송된 완전 프롬프트 — **사연 본문 포함, 영구 보관** (posts.content는 30일 후 NULL)
- `llm_response` (LONGTEXT): LLM 원응답 (파싱 전)
- `llm_model`, `llm_attempt`, `llm_result`, `llm_duration_ms`: 시도 메타데이터
- `sibom_plan_llm`, `sibom_plan_final`: 시봄이 가드 전/후 플랜
- `sibom_guard_log` (JSON): 가드 실행 로그 (`[{action, imageId, reason}, ...]`)
- `final_hook`, `final_script`: TTS 입력 직전 최종 버전

**가드 로직**은 코드 전용 (외부 3rd-party LLM 미사용) — WaggleBot이 guard와 동시에 렌더를 시작하므로, 생성 완료 후 저장은 비블로킹 시도(실패 시 로그만 남김).

---

## DB 스키마 (V115)

```sql
CREATE TABLE marketing_job (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  remote_job_id           VARCHAR(64) UNIQUE,               -- ASM ULID
  post_id                 VARCHAR(32) NOT NULL,              -- posts.id FK
  status                  VARCHAR(20) NOT NULL,              -- 잡 상태
  phase                   VARCHAR(20),                       -- 현재 파이프라인 단계
  progress                DOUBLE DEFAULT 0,                  -- 0.0~1.0
  targets                 JSON,                              -- ["naver_blog", "x"]
  auto_publish            BOOLEAN DEFAULT FALSE,
  artifacts               JSON,                              -- ASM 생성 결과물 경로
  publications            JSON,                              -- 게시 기록 [{platform, state, url}]
  error_message           TEXT,
  failure_code            VARCHAR(80) NULL,                  -- 품질 실패 분류 (V115)
  generation_diagnostics  JSON NULL,                          -- 최종 플랜·실제 길이·폴백 사유 (V115, LLM 원출력 금지)
  actual_duration_ms      BIGINT NULL,                        -- 최종 MP4 실제 길이 (V115)
  retry_of_job_id         BIGINT NULL,                        -- 재생성 원 잡 (V115)
  generation_attempt      INT NOT NULL DEFAULT 1,             -- 재생성 시도 횟수 (V115)
  requested_by            VARCHAR(128),                      -- V104: force=`admin:force:`+JWT subject(UUID)
  poll_fail_count         INT DEFAULT 0,
  last_polled_at          TIMESTAMP NULL,
  scheduled_publish_at    DATETIME(6) NOT NULL,              -- 생성 시각 (V117). 자동 발행 게이트 아님
  rescheduled_count       INT DEFAULT 0,                     -- 레거시 이월 횟수 (신규 자동 잡 미사용)
  rescheduled_reason      VARCHAR(255) NULL,
  original_scheduled_at   DATETIME(6) NULL,
  last_rescheduled_at     DATETIME(6) NULL,
  created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mj_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

**V104**: `requested_by` VARCHAR(32)→128 — `admin:force:`(12)+JWT UUID(36)=48이 32를 넘겨 강제 배포 500 발생.

**V115 품질 게이트**: 비어 있거나 기준 미달인 시봄이 플랜, 또는 본문 TTS가 Reels 32초/Shorts 47초를 넘으면 `FAILED`다. 댓글 2개·아웃트로는 본문 이후에 붙으며 최종 MP4에는 별도 하드 상한을 적용하지 않는다. 품질 실패 잡은 READY·자동 게시로 전환할 수 없고, 재생성은 추적 가능한 새 자식 잡으로만 수행한다.

**V116 실패 계약**: ASM/WaggleBot이 `failure_code`, `failure_stage`, `retryable`, `error_summary`와 안전한 생성 진단을 전달하면 AS가 폴링·콜백 모두에서 보존한다. 예를 들어 MariaDB 낙관적 동시성 충돌은 `INFRA_DB_CONFLICT` 및 재시도 가능으로, 품질 게이트 실패는 재시도 불가로 표시한다.

**2026-08-15 실측 및 강제**: V116 컬럼은 존재했으나 실측 결과 AS 실패의 70%, ASM·WaggleBot 실패의
100%가 이 필드들을 채우지 않고 있었다(스키마는 있었으나 강제가 없었다). 2026-08-15부터 3개
저장소 모두 **단일 진입점**을 통해서만 실패 상태를 기록하도록 강제한다:

| 저장소 | 진입점 | 단계 어휘 접두사 |
|---|---|---|
| AS | `MarketingJobService.failJob()` | `AS:` (BRIEF_BUILD·VARIANT_LLM·SIBOM_GUARD·QUALITY_GATE·ASM_CREATE·ASM_POLL·PUBLISH_TRIGGER) |
| ASM | `app/worker/failure.py::fail_job()` | `ASM:` (CLAIM·BRIEF_PARSE·SCRIPT_GEN·TTS·WAGGLE_SUBMIT·WAGGLE_POLL·CAPTURE·UPLOAD·PUBLISH·CALLBACK) |
| WaggleBot | 실패 응답 페이로드 (`failureCode`/`failureStage`/`retryable`/`error`) | `WAGGLE:` (기존 `phaseName`을 영문 상수로 승격, 예: "씬 구성"→`SCENE_COMPOSE`) |

공통 enum을 두지 않고 저장소별로 독립 정의한 이유: ASM `CLAUDE.md`의 단방향 계약("ASM은 AS를
모른다")을 지키기 위해서다. `failure_stage`가 비면(코드 결함) 텔레그램에 "⚠️ 원인 미기록"으로
눈에 띄게 표시된다 — 조용히 UNKNOWN으로 덮지 않는다. FAILED인데 `failure_stage`가 NULL이면
실패하는 회귀 테스트를 AS·ASM 양쪽에 추가해 재발을 막는다.

ASM 렌더 실패는 `retryable=true`이고 `ingest_attempts < 2`면 1회 자동 재시도한다 —
쿼터가 "실제 발행 성공" 기준(2026-08-12, `83e14ba7`)이라 재시도가 그날 발행 편수를 깎지 않는다.
WaggleBot poll timeout은 1800초→2700초로 상향(2026-08-14 실측 타임아웃 2건).

**예약 시각 필드 (V103/V117)**:
- `scheduled_publish_at`: **V117부터 DB NOT NULL**. 신규 잡은 생성 시각을 넣는다. **READY 즉시 발행**이며 저녁 슬롯·이월 게이팅에 쓰지 않는다(2026-08-16).
- `rescheduled_count` / `rescheduled_reason` / `original_scheduled_at` / `last_rescheduled_at`: 과거 저녁 슬롯 이월용. 신규 자동 발행 경로는 갱신하지 않는다.

---

## ASM 파이프라인 내부 (M0 스텁)

M0에서는 실제 GPU·API 없이 잡이 QUEUED → PUBLISHED까지 자동 진행됩니다:

```python
# app/worker/pipeline.py (M0 스텁)
async def run_stub(job_id):
    phases = ["copy", "tts", "video", "render", "image", "publish"]
    for i, phase in enumerate(phases):
        update_job(job_id, status=RUNNING, phase=phase, progress=(i+1)/6)
        await asyncio.sleep(2)  # 가짜 처리 시간
    
    # 가짜 아티팩트 생성
    create_stub_artifact(job_id, "video.mp4", kind="video")
    create_stub_artifact(job_id, "thumb.png", kind="image")
    create_stub_artifact(job_id, "blog.md", kind="text")
    
    update_job(job_id, status=READY)
    
    if job.auto_publish:
        trigger_publish(job_id)
```

---

## 오류 처리

### AsmClient 재시도 (2026-08-08 추가)

`AsmClient`의 모든 메서드는 **3회 지수 백오프 재시도**를 지원합니다. (M3 P1-6)

**재시도 정책**:
- 대상: 네트워크 오류 (`ConnectException`, `SocketTimeoutException`), 타임아웃 (`ResourceAccessException`), 5xx 서버 오류
- 백오프: 1초 → 2초 → 4초
- 비재시도: 4xx 오류 (401 인증 실패, 400 유효성 오류 등) — 즉시 실패

**적용 메서드**: `createJob()`, `getJob()`, `publish()`, `republish()`

**로컬 영상 보존**: 게시 성공 직후 ASM `data/jobs/` mp4를 삭제하지 않는다. `PUBLISHED` 후 30일 지난 영상만 시간당 스윕이 바이트를 지운다. 정책 SSOT: [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md).

---

| 오류 상황 | AS 동작 | 사용자 표시 |
|---|---|---|
| ASM 서버 일시 불안정 (잡 생성 시) | AsmClient 3회 재시도 후 실패 시 `AsmUnavailableException` → 503 반환 | "마케팅 잡 생성에 실패했어요" |
| ASM 인증 실패 (401) | 즉시 실패 (재시도 없음) | "ASM 인증 오류" |
| ASM 서버 다운 (폴링 시) | `markPollFailure()` 후 남은 잡 GET을 중단하고 ASM circuit을 5분 연다 | 기존 상태 유지(아티팩트가 있으면 READY 미리보기 보존) |
| 폴링 5회 연속 실패 | 아티팩트 없으면 `status = STALE`; 있으면 `READY` 유지 | STALE 배지 또는 READY 미리보기 |
| STALE 24시간 초과 | 아티팩트 없는 잡만 `FAILED` 처리 | 최종 실패 표시 |
| ASM `FAILED`/`PARTIAL` 전환 | callback/poll의 최상위 `error` 또는 publication별 `error`를 `errorMessage`에 저장(최대 1,000자). 텔레그램은 ASM만 상태 전환당 1회 알림 | 채널·원인을 포함한 최종 실패 표시 |
| 잘못된 postId | BE 400 반환 | 다이얼로그 오류 메시지 |
| READY가 아닌 잡에 publish | BE 400 반환 | 버튼 비활성화로 방지 |

---

## 보안

- AS → ASM: `Authorization: Bearer ${ASM_API_TOKEN}` (환경 변수로 관리)
- ASM 어드민 엔드포인트: Spring Security `@PreAuthorize("hasRole('ADMIN')")`
- ASM API: `verify_bearer()` constant-time 비교 (`hmac.compare_digest`)
- ASM이 생성한 아티팩트 URL은 동일한 Bearer 토큰으로만 다운로드 가능

---

## 피벗 히스토리

| 날짜 | 이벤트 |
|---|---|
| 2026-06-02 | 커뮤니티 광장 피벗, 마케팅 prod 비활성화 |
| 2026-06-09 | V15 마케팅 전면 제거(Phase R), ASM 분리(Phase I) |
| 2026-06-09 | V79 FK 오류 수정, V80 marketing_job 테이블 추가 |
| 2026-06-09 | ASM social-poster 복원·이관, ASM 서버 기동 |
| 2026-08-11 | Doc-Sync Phase 2 타깃 SSOT: 채널별 score·cap · 유니크 영상 · 통계/`auto_adjust` · `hook_emotion`→TTS |
