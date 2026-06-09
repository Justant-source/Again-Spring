# 마케팅 API 명세

## 1. Again-Spring (AS) 어드민 API

### 1.1 잡 생성

```
POST /api/admin/marketing/jobs
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

**Request Body**
```json
{
  "postId": "abc123def456",       // posts.id (VARCHAR(32))
  "targets": ["naver_blog", "x"], // 지원 플랫폼 목록 참조
  "autoPublish": false            // true 시 READY 도달 즉시 자동 게시
}
```

**Response 201**
```json
{
  "id": 1,
  "remote_job_id": "01HX...",
  "post_id": "abc123def456",
  "status": "REQUESTED",
  "phase": null,
  "progress": 0,
  "targets": ["naver_blog", "x"],
  "auto_publish": false,
  "artifacts": null,
  "publications": null,
  "error_message": null,
  "requested_by": null,
  "poll_fail_count": 0,
  "last_polled_at": null,
  "created_at": "2026-06-09T05:00:00Z",
  "updated_at": "2026-06-09T05:00:00Z"
}
```

**오류**
| 코드 | 이유 |
|---|---|
| 400 | postId 누락 또는 targets 빈 배열 |
| 401/403 | 인증 없거나 ADMIN 권한 없음 |
| 503 | ASM 서버 연결 불가 (`AsmUnavailableException`) |

---

### 1.2 잡 목록 조회

```
GET /api/admin/marketing/jobs
Authorization: Bearer <admin-jwt>
```

**Response 200** — `JobResponse[]` 배열

---

### 1.3 잡 상세 조회

```
GET /api/admin/marketing/jobs/{id}
Authorization: Bearer <admin-jwt>
```

**Response 200** — `JobResponse`  
**오류**: 404 (존재하지 않는 id)

---

### 1.4 수동 게시 승인

```
POST /api/admin/marketing/jobs/{id}/publish
Authorization: Bearer <admin-jwt>
```

**Response 200** — 업데이트된 `JobResponse`  
**오류**: 404 (없는 id), 400 (`status != READY`)

---

## 2. ASM (Again-Spring-Marketing) API

> Base URL: `http://100.115.252.61:8200`  
> Auth: `Authorization: Bearer asm-dev-token-change-in-prod`

### 2.1 잡 생성

```
POST /api/v1/jobs
Authorization: Bearer <asm-token>
```

**Request Body (StoryBrief)**
```json
{
  "source_id": "abc123def456",
  "brief": {
    "title": "사연 제목",
    "neutral_summary": "중립 요약 (최대 500자)",
    "side_a": "작성자 관점",
    "side_b": "상대방 관점",
    "empathy_ratio": { "a": 50, "b": 50 },
    "jury_gist": "",
    "tags": [],
    "policy": {
      "no_emoji": true,
      "forbidden_terms": ["판결", "처방", "승패", "승자", "패자"]
    }
  },
  "targets": ["naver_blog", "x"],
  "options": {
    "voice_id": null,
    "tone": null,
    "auto_publish": false
  }
}
```

**Response 202**
```json
{
  "job_id": "01HX...",
  "status": "QUEUED"
}
```

---

### 2.2 잡 상태 조회 (폴링용)

```
GET /api/v1/jobs/{job_id}
Authorization: Bearer <asm-token>
```

**Response 200 (JobView)**
```json
{
  "job_id": "01HX...",
  "status": "RUNNING",
  "phase": "copy",
  "progress": 0.3,
  "artifacts": {
    "video_mp4": "/api/v1/jobs/{id}/artifacts/video.mp4",
    "thumbnail": "/api/v1/jobs/{id}/artifacts/thumb.png",
    "blog_md":   "/api/v1/jobs/{id}/artifacts/blog.md",
    "images":    []
  },
  "publications": [],
  "error": null
}
```

---

### 2.3 수동 게시 트리거

```
POST /api/v1/jobs/{job_id}/publish
Authorization: Bearer <asm-token>
```

**Precondition**: `status == READY`  
**Response 202** — `{ "job_id": "...", "status": "PUBLISHING" }`  
**오류**: 404, 409 (상태 불일치)

---

### 2.4 아티팩트 다운로드

```
GET /api/v1/jobs/{job_id}/artifacts/{filename}
Authorization: Bearer <asm-token>
```

**Response**: 파일 스트림 (FileResponse)

---

## 3. 폴링 흐름 (AS 내부)

`MarketingPollingScheduler`가 15초마다 비종료 잡 (`QUEUED`, `RUNNING`, `READY`, `PUBLISHING`) 을 폴링:

```
AS polling → GET /api/v1/jobs/{remote_job_id} → ASM
                  ↓
          job.applyRemote(status, phase, progress, artifacts, publications)
                  ↓
           marketingJobRepository.save(job)
                  ↓ (ASM 연결 실패 시)
           job.markPollFailure() → poll_fail_count++
           poll_fail_count >= 5 → status = STALE
```
