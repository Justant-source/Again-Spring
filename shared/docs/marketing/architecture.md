# 마케팅 시스템 아키텍처

## 설계 원칙

1. **AS = 얇은 트리거** — Again-Spring은 잡 생성·폴링·상태 표시만 담당. 콘텐츠 생성 로직 없음.
2. **ASM = 콘텐츠 공장** — 카피라이팅·음성·영상·이미지·게시 전담. GPU 서버(WSL RTX 3090).
3. **비동기 폴링** — 잡 생성 후 AS는 15초마다 ASM의 상태를 폴링해서 DB에 반영.
4. **단방향 계약** — ASM은 AS를 모른다. AS만 ASM을 호출한다.

---

## 데이터 흐름

### 잡 생성

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
    ├── AsmClient.createJob(brief, targets, options)
    │       └── POST http://100.115.252.61:8200/api/v1/jobs
    │               └── ASM이 ULID job_id 반환 + DB 저장
    └── MarketingJob 저장 { remoteJobId, postId, status=REQUESTED, ... }
```

### 폴링 루프

```
MarketingPollingScheduler (15초마다)
    │
    ▼
findByStatusIn([QUEUED, RUNNING, READY, PUBLISHING])
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
```

### 수동 게시 승인

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
                    └── ASM: status = PUBLISHING → 소셜 게시 → PUBLISHED
```

---

## DB 스키마 (V80)

```sql
CREATE TABLE marketing_job (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  remote_job_id   VARCHAR(64) UNIQUE,         -- ASM ULID
  post_id         VARCHAR(32) NOT NULL,        -- posts.id FK
  status          VARCHAR(20) NOT NULL,        -- 잡 상태
  phase           VARCHAR(20),                 -- 현재 파이프라인 단계
  progress        DOUBLE DEFAULT 0,            -- 0.0~1.0
  targets         JSON,                        -- ["naver_blog", "x"]
  auto_publish    BOOLEAN DEFAULT FALSE,
  artifacts       JSON,                        -- ASM 생성 결과물 경로
  publications    JSON,                        -- 게시 기록 [{platform, state, url}]
  error_message   TEXT,
  requested_by    VARCHAR(32),
  poll_fail_count INT DEFAULT 0,
  last_polled_at  TIMESTAMP NULL,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mj_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

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

| 오류 상황 | AS 동작 | 사용자 표시 |
|---|---|---|
| ASM 서버 다운 (잡 생성 시) | `AsmUnavailableException` → 503 반환 | "마케팅 잡 생성에 실패했어요" |
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
