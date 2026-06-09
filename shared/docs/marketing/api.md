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

### 1.5 플랫폼 자격증명 관리

> ASM `/api/v1/credentials`로 투명 프록시. 시크릿은 ASM이 암호화·마스킹. 상세: [`credentials.md`](credentials.md)

```
GET    /api/admin/marketing/credentials             # 7개 플랫폼 상태 (시크릿 마스킹)
PUT    /api/admin/marketing/credentials/{platform}  # 저장/수정  body: {"values": {...}}
DELETE /api/admin/marketing/credentials/{platform}  # 삭제 (204, 멱등)
Authorization: Bearer <admin-jwt>
```

**GET Response 200** — `CredentialStatus[]` (아래 ASM 2.5 형식과 동일)
**PUT**: 성공 시 단일 `CredentialStatus`. **오류**: 400 (미지원 platform / 필수 누락)

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
  "phase": "VIDEO",
  "progress": 0.5,
  "artifacts": {
    "x":          { "card": "/api/v1/jobs/01HX.../artifacts/x/card.png", "caption": "..." },
    "naver_blog": { "blog_md": "/api/v1/jobs/01HX.../artifacts/naver_blog/post.md" }
  },
  "publications": [],
  "error": null
}
```

> **`artifacts`는 플랫폼별 패키지 맵**(`dict[str, Any]`)이다 — `targets`에 포함된 플랫폼 value를 키로, 해당 플랫폼이 필요로 하는 아티팩트 묶음을 값으로 갖는다. (과거 문서의 평면 `video_mp4`/`thumbnail`/`blog_md`/`images` 형태는 폐기됨 — ASM commit `9aaa03d` "per-platform artifact packages".)
> **`phase`** 는 ASM `JobPhase` enum: `SCRIPT` → `TTS` → `VIDEO` → `RENDER` → `IMAGE` → `PUBLISH` (대문자). 폴링 파서(`MarketingJob.applyRemote`)는 맵 형태 `artifacts`를 그대로 JSON 컬럼에 저장한다.

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

### 2.5 자격증명 (credentials)

> AES-256-GCM 암호화 저장. 시크릿은 평문 미반환. 필드 스키마·병합 규칙: [`credentials.md`](credentials.md)

```
GET    /api/v1/credentials             # 7개 플랫폼 전체 상태
PUT    /api/v1/credentials/{platform}  # 저장/수정 (병합)  body: {"values": {...}}
DELETE /api/v1/credentials/{platform}  # 삭제 (204)
Authorization: Bearer <asm-token>
```

**GET Response 200 (CredentialStatus[])**
```json
[
  {
    "platform": "x",
    "fields": [
      { "key": "email", "secret": false, "required": true },
      { "key": "password", "secret": true, "required": true },
      { "key": "totp_secret", "secret": true, "required": false },
      { "key": "storage_state", "secret": true, "required": false }
    ],
    "configured": true,
    "values": { "email": "again_spring@example.com" },
    "secret_set": { "password": true, "totp_secret": false, "storage_state": false },
    "updated_at": "2026-06-09T05:00:00"
  }
]
```

**오류**: 400 (미지원 platform / 필수 필드 누락), 401 (Bearer 누락)

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
