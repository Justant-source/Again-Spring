# 마케팅 시스템 아키텍처

> **Phase 2 = 타깃 SSOT** (코드 병렬 착수). 분배·영상·통계는 [`platforms.md`](platforms.md) · [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) · [`api.md`](api.md).  
> Phase 1 유지: UTM · 저녁 슬롯 · 텔레그램 댓글 노티 · 태그 · 배심원 없음 · 2027-01 고지.

## 설계 원칙

1. **AS = 얇은 트리거** — Again-Spring은 홀딩·24h 확정·잡 생성·콜백 수신·폴링·상태 표시·통계/리포트. 콘텐츠 생성 로직 없음.
2. **ASM = 콘텐츠 공장** — 카피라이팅·음성·영상·이미지·게시 전담. GPU 서버(WSL RTX 3090).
3. **이중 동기화** — 푸시(콜백) + 풀(폴링). ASM이 종료 상태 도달 시 AS 콜백 엔드포인트로 즉시 전송, 폴링은 콜백 미수신 시 재조정용.
4. **멱등성** — AS가 각 잡 생성 시도마다 Idempotency-Key(UUID)를 발송. ASM이 중복 감지 후 같은 응답 반환.
5. **단방향 초기 요청** — 잡 생성은 AS → ASM. 이후 ASM → AS 콜백 + AS 폴링으로 동기화.
6. **Phase 2 분배** — 플랫폼별 popularity·일일 cap(기본 3). 같은 사연 멀티 플랫폼 허용. IG feed⊥Reels만 점수 배타(동점→Reels).

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

### 5. 이월 정책 (P3-10) — 예약 시각 경과 자동 재예약

```
MarketingPollingScheduler (15초마다)
    │
    ├─ 저녁 슬롯 도래: READY && autoPublish && scheduledPublishAt <= NOW()
    │  → triggerPublish() (ASM에 생성 시 auto_publish=false로 보냈던 잡)
    │
    ├─ ASM poll / applyPoll (상태·아티팩트 동기화)
    │
    └─ 생성 중 슬롯 경과 이월 (poll **이후**에 실행 — 순서 고정)
       조건: autoPublish=true AND scheduledPublishAt < NOW()-5분
             AND status ∈ QUEUED, RUNNING, STALE (READY 제외)
       └─ rescheduleExpiredJob()
           ├─ originalScheduledAt 기록 (첫 이월 시)
           ├─ 다음날 동일 시간대로 재예약 (예: 20:30 → 다음날 20:30)
           ├─ 충돌 시 다음 빈 슬롯 (±5분, 정각=1h / 30분=30m)
           └─ Telegram 이월 알림 1회/이월
       ⚠️ poll보다 먼저 이월하면 applyPoll이 옛 엔티티로 슬롯을 덮어
          15초마다 동일 "1회째 이월" 알림이 반복된다. preview(autoPublish=false)는 이월 대상 아님.
```

**저녁 슬롯 (Phase 1 유지)**: 커밋/잡 생성 시 `MarketingPublishSlotService`가 KST 다음 발생을 `scheduledPublishAt`에 기록. 기본 `instagram_feed` 20:00 · reels/shorts 20:30 · `x_thread` 21:30. 설정 키 `marketing.publish_slot.*`.

**상태 업데이트**:
- `scheduledPublishAt`: 새 예약 시각
- `rescheduledCount`: +1
- `lastRescheduledAt`: 현재 시각
- `rescheduledReason`: "예약 시각 경과 (원 예약: {원래시간})"
- `originalScheduledAt`: 첫 이월 시에만 저장

**로깅**: INFO 레벨로 상세 기록 + `TelegramNotifier`로 @WaggleBot_bot 채팅방에 이월 발생 시마다 알림 (잡 ID·원 예약/새 예약 시각·이월 횟수 포함). 원격 **FAILED/PARTIAL** 알림은 publication별 오류를 가진 **ASM pipeline/dispatcher만** 상태 전환당 1회 보낸다. AS는 callback/poll의 최상위 `error` 또는 publication `error`를 `errorMessage`에 저장하지만 같은 실패를 다시 알리지 않는다. 단, ASM 잡을 만들기 전 AS에서 실패한 경우에는 AS가 직접 1회 알린다. 봇 토큰/chat id는 `encrypted_secret` vault(`telegram.bot_token`/`telegram.chat_id`)에서 주입. 워치독과 동일 계열(`TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`).

### 6. Phase 2 분배 · 영상 · 통계 루프

```
T+24h MarketingHoldingCommitService
    │
    ├─ 채널별 score (platforms.md §식) DESC
    ├─ 채널별 cap (기본 3) 잔여까지 COMMIT
    ├─ 같은 사연 → 멀티 플랫폼 허용
    ├─ IG: score_feed vs score_reels (동점→Reels) 만 배타
    │
    ├─ Reels/Shorts 확정 시
    │     ├─ 변형 훅·스크립트 생성 (2단)
    │     ├─ hook_emotion → brief → WaggleBot S2 Pro TTS
    │     └─ 유니크 렌더 (Reels≤30s / Shorts≤45s · 전문 낭독 금지)
    │
    └─ 저녁 슬롯 publish (Phase 1)
            │
            ▼
        [발행 성공] → 댓글 감시 창 (published_at + N h, 기본 24)
            → 신규 댓글 → WaggleBot 텔레그램 → 운영자 수동 답글
            → 플랫폼 통계 수집 (X/IG/YT)
            → 주간 리포트 + (auto_adjust on 시) 가중치 소폭 보정
```

자동 답글·페르소나·프롬프트 자동 패치는 **후속/금지**. AI 고지는 **2027-01**.

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

## DB 스키마 (V104)

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
  requested_by            VARCHAR(128),                      -- V104: force=`admin:force:`+JWT subject(UUID)
  poll_fail_count         INT DEFAULT 0,
  last_polled_at          TIMESTAMP NULL,
  scheduled_publish_at    DATETIME(6) NULL,                  -- 예약된 발행 시각 (이월 정책)
  rescheduled_count       INT DEFAULT 0,                     -- 이월된 횟수
  rescheduled_reason      VARCHAR(255) NULL,                 -- 이월 사유
  original_scheduled_at   DATETIME(6) NULL,                  -- 원래 예약 시각 (첫 이월 시 기록)
  last_rescheduled_at     DATETIME(6) NULL,                  -- 마지막 이월 시각
  created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mj_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

**V104**: `requested_by` VARCHAR(32)→128 — `admin:force:`(12)+JWT UUID(36)=48이 32를 넘겨 강제 배포 500 발생.

**이월 정책 필드 (V103 추가)**:
- `scheduled_publish_at`: 현재 예약된 발행 시각 (이월 시 갱신됨)
- `rescheduled_count`: 총 이월 횟수 (0 = 원래 예약시각, 1+ = 이월됨)
- `rescheduled_reason`: 이월 사유 예: "예약 시각 경과 (원 예약: 2026-08-08T14:00:00Z)"
- `original_scheduled_at`: 첫 이월 시 저장, 변경되지 않음 (감사 추적용)
- `last_rescheduled_at`: 마지막 이월 시각

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
