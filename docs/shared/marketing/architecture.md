# 마케팅 시스템 아키텍처

## 설계 원칙

1. **AS = 얇은 트리거** — Again-Spring은 잡 생성·콜백 수신·폴링·상태 표시만 담당. 콘텐츠 생성 로직 없음.
2. **ASM = 콘텐츠 공장** — 카피라이팅·음성·영상·이미지·게시 전담. GPU 서버(WSL RTX 3090).
3. **이중 동기화** — 푸시(콜백) + 풀(폴링). ASM이 종료 상태 도달 시 AS 콜백 엔드포인트로 즉시 전송, 폴링은 콜백 미수신 시 재조정용.
4. **멱등성** — AS가 각 잡 생성 시도마다 Idempotency-Key(UUID)를 발송. ASM이 중복 감지 후 같은 응답 반환.
5. **단방향 초기 요청** — 잡 생성은 AS → ASM. 이후 ASM → AS 콜백 + AS 폴링으로 동기화.

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
    AsmClient.getJob(remoteJobId)
    │
    ├── 성공 → job.applyRemote(status, phase, progress, artifacts, publications)
    │           poll_fail_count = 0
    │
    └── 실패 → job.markPollFailure()
                poll_fail_count >= 5 → status = STALE
                (STALE 후 24시간 재시도, 초과 시 FAILED)
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
    ├─ 예약된 미발행 잡 감지: scheduledPublishAt < NOW() - 5분
    │  (status ∉ PUBLISHED, FAILED, PARTIAL)
    │
    └─ rescheduleExpiredJob() 실행
        ├─ originalScheduledAt 기록 (첫 이월 시)
        ├─ 다음날 동일 시간대로 재예약
        │  예: 14:30 → 다음날 14:30
        │
        └─ 충돌 감지 (동일 시간대에 다른 미발행 잡 있음)
           └─ 충돌 시 다음 빈 슬롯으로 이동
              (±5분 범위에서 충돌 없을 때까지)
              └─ 슬롯 단위: 원본 예약 시각 기준
                 정각(00분) → 1시간 단위
                 30분(30분) → 30분 단위
```

**상태 업데이트**:
- `scheduledPublishAt`: 새 예약 시각
- `rescheduledCount`: +1
- `lastRescheduledAt`: 현재 시각
- `rescheduledReason`: "예약 시각 경과 (원 예약: {원래시간})"
- `originalScheduledAt`: 첫 이월 시에만 저장

**로깅**: INFO 레벨로 상세 기록. TODO: watchdog 텔레그램 알림 연동 (향후)

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

## DB 스키마 (V103)

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
  requested_by            VARCHAR(32),
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
| ASM 서버 다운 (폴링 시) | `markPollFailure()`, 로그 WARN | 잡 상태 유지 |
| 폴링 5회 연속 실패 | `status = STALE` | 잡 목록에 STALE 배지 |
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
