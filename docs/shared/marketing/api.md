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
  "postId": "abc123def456",              // posts.id (VARCHAR(32))
  "targets": ["naver_blog", "x_thread"], // 지원 플랫폼 목록 참조
  "autoPublish": false                   // true 시 READY 도달 즉시 자동 게시
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
  "targets": ["naver_blog", "x_thread"],
  "auto_publish": false,
  "artifacts": null,
  "publications": null,
  "error_message": null,
  "requested_by": null,
  "poll_fail_count": 0,
  "last_polled_at": null,
  "scheduled_publish_at": null,
  "rescheduled_count": 0,
  "rescheduled_reason": null,
  "original_scheduled_at": null,
  "last_rescheduled_at": null,
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

### 1.5.1 WaggleBot TTS 음성 (숏폼영상 설정)

> ASM `/api/v1/waggle/*` → WaggleBot. 어드민 JWT로 미리듣기·선택. `tts_voice`/`comment_tts_voices`는 `shortform_video` pseudo-platform(설정 전용, 로그인 없음) 자격증명 public 필드 — `instagram_reels`/`youtube_shorts`가 WaggleBot에서 같은 영상을 공유하므로 나레이션 설정도 공유(2026-08-10, 상세: `credentials.md`).

```
GET /api/admin/marketing/tts/voices
GET /api/admin/marketing/tts/voice-sample?path=/api/tts/voices/{key}/sample
Authorization: Bearer <admin-jwt>
```

**voices 200** — `{ defaultVoice, voices:[{ key, label, gender?, sampleUrl, hasSample, ... }] }`  
`sampleUrl` = WaggleBot 키 기반 경로(`/api/tts/voices/{key}/sample`). 미리듣기는 `voice-sample`로 스트리밍 (path는 `/api/tts/voices/*/sample` 또는 `/api/media/voices/` 접두만 허용).

---

## 2. ASM (Again-Spring-Marketing) API

> Base URL: `http://100.115.252.61:8200`  
> Auth: `Authorization: Bearer asm-dev-token-change-in-prod`

### 2.1 잡 생성

```
POST /api/v1/jobs
Authorization: Bearer <asm-token>
Idempotency-Key: <uuid>
```

**Request Header**
- `Idempotency-Key`: UUID 형식. AS가 생성 시도마다 새로운 UUID를 보냄. ASM은 동일 key로 오는 중복 요청을 감지해 같은 응답 반환 (멱등성).

> `brief.tags`: 24h 홀딩 커밋 경로는 신규 홀딩 시드 `#다시봄` `#공감비율` `#[카테고리]`(어드민 대기 탭에서 편집 가능, 기존 홀딩 백필 없음)를 그대로 사용. 수동 잡 생성(`/api/admin/marketing/jobs`)은 카테고리명만 채움.

**Request Body (StoryBrief)**
```json
{
  "source_id": "abc123def456",
  "callback_base_url": "http://100.81.189.92:8090",  // AS가 포함 — ASM이 콜백 URL 생성 시 사용
  "brief": {
    "title": "사연 제목",
    "metaphor_id": "empty-chair",
    "neutral_summary": "중립 요약 (최대 500자)",
    "side_a": "작성자 관점",
    "side_b": "상대방 관점",
    "empathy_ratio": { "a": 50, "b": 50 },
    "jury_gist": "",
    "tags": ["#다시봄", "#공감비율", "#이별"],
    "policy": {
      "no_emoji": true,
      "forbidden_terms": ["판결", "처방", "승패", "승자", "패자"]
    }
  },
  "targets": ["naver_blog", "x_thread"],
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
  "event": "progress",
  "error": null
}
```

> **`artifacts`는 플랫폼별 패키지 맵**(`dict[str, Any]`)이다 — `targets`에 포함된 플랫폼 value를 키로, 해당 플랫폼이 필요로 하는 아티팩트 묶음을 값으로 갖는다. (과거 문서의 평면 `video_mp4`/`thumbnail`/`blog_md`/`images` 형태는 폐기됨 — ASM commit `9aaa03d` "per-platform artifact packages".)
> **`phase`** 는 ASM `JobPhase` enum: `SCRIPT` → `TTS` → `VIDEO` → `RENDER` → `IMAGE` → `PUBLISH` (대문자). 폴링 파서(`MarketingJob.applyRemote`)는 맵 형태 `artifacts`를 그대로 JSON 컬럼에 저장한다.
> **`event`** 필드는 콜백 및 폴링에서 공통으로 사용 — `progress`, `terminal_state_transition` 등.

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

### 2.4.1 커스텀 썸네일 업로드 (쇼츠/릴스)

```
PUT /api/v1/jobs/{job_id}/artifacts/{name}
Authorization: Bearer <asm-token>
Content-Type: image/png | image/jpeg
<raw image bytes>
```

**`name` 화이트리스트**: `{platform}__customcover.{png,jpg,jpeg}` (`platform` = `youtube_shorts` | `instagram_reels`) 만 허용 — `video.mp4` 등 렌더 파이프라인 산출물은 이 경로로 덮어쓸 수 없음. **최대 2MB**(YouTube `thumbnails.set` 상한과 동일). 같은 `(job_id, name)`으로 재업로드하면 upsert(기존 row 삭제 후 insert) — 중복 row 없음.

**Response 200** — `{ "name": "...", "size_bytes": N }`
**오류**: 400(이름/타입/크기 불일치), 401, 404(job 없음)

업로드된 커스텀 커버는 다음 발행 시 자동 반영된다:
- **YouTube Shorts**: 영상 업로드 성공 후 `thumbnails.set` API 호출(실패해도 게시 자체는 성공 처리 — non-fatal). 커스텀 커버가 없으면 호출 자체를 생략하고 YouTube 자동 프레임(항상 인트로 씬=대표 메타포+제목 레이아웃) 사용.
- **Instagram Reels**: Graph API `cover_url`(공개 HTTPS 필요, ASM은 현재 Tailscale 내부망 전용이라 **미동작**)은 보류 상태. 대신 API 경로 실패 시 폴백되는 Playwright 자동화 경로의 로컬 파일 첨부(`coverPath`)에는 반영됨.

Again-Spring 측 관리자 UI 경로: `PUT /api/admin/marketing/jobs/{id}/artifacts/{platform}/thumbnail` (멀티파트, `AdminMarketingController` → `AsmClient.putArtifact` 프록시).

---

### 2.5 자격증명 (credentials)

> AES-256-GCM 암호화 저장. 시크릿은 평문 미반환. 필드 스키마·병합 규칙: [`credentials.md`](credentials.md)

```
GET    /api/v1/credentials             # 7개 플랫폼 전체 상태
PUT    /api/v1/credentials/{platform}  # 저장/수정 (병합)  body: {"values": {...}}
DELETE /api/v1/credentials/{platform}  # 삭제 (204)

GET    /api/v1/waggle/voices                         # WaggleBot TTS 카탈로그
GET    /api/v1/waggle/voice-sample?path=/api/tts/…/sample # 샘플 오디오 프록시
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

### 2.6 콜백 엔드포인트 (ASM → AS)

ASM은 잡이 종료 상태(`READY`, `PUBLISHED`, `PARTIAL`, `FAILED`)로 전환될 때 AS의 콜백 엔드포인트를 호출합니다.

```
POST /api/internal/marketing/callback
Authorization: Bearer {ASM_CALLBACK_TOKEN}
```

**Request Body**
```json
{
  "job_id": "01HX...",
  "status": "PUBLISHED",
  "phase": "PUBLISH",
  "progress": 1.0,
  "artifacts": {
    "x": { "card": "/api/v1/jobs/01HX.../artifacts/x/card.png" },
    "naver_blog": { "blog_md": "/api/v1/jobs/01HX.../artifacts/naver_blog/post.md" }
  },
  "publications": [
    { "platform": "x", "state": "published", "url": "https://x.com/..." }
  ],
  "event": "terminal_state_transition",
  "error": null
}
```

**Response 204 No Content** — 성공 시 본문 없음  
**오류**: 
- 401 — 잘못된 또는 누락된 `Authorization` 헤더
- 400 — 필수 필드 누락

**목적**: AS는 콜백 수신 후 원격 상태(`status`, `phase`, `progress`, `artifacts`, `publications`)를 DB에 반영하고 폴링 오류 카운트를 초기화합니다.

---

## 3. 폴링 흐름 (AS 내부)

`MarketingPollingScheduler`가 15초마다 비종료 잡 (`QUEUED`, `RUNNING`, `READY`, `PUBLISHING`, `STALE`) 을 폴링:

```
AS polling → GET /api/v1/jobs/{remote_job_id} → ASM
                  ↓
          job.applyRemote(status, phase, progress, artifacts, publications)
                  ↓
           marketingJobRepository.save(job)
                  ↓ (ASM 연결 실패 시)
           job.markPollFailure() → poll_fail_count++
           poll_fail_count >= 5 → status = STALE
           
           (STALE 상태 시)
           → 지수 백오프 재시도 (exponential backoff)
           → 24시간 초과 시 FAILED로 전환
```

**상태 정의**
| 상태 | 설명 | 종료 여부 |
|---|---|---|
| `REQUESTED` | AS가 생성 요청 완료 | X |
| `QUEUED` | ASM이 수신, 큐 대기 중 | X |
| `RUNNING` | 콘텐츠 생성 중 | X |
| `READY` | 생성 완료, 게시 대기 | X |
| `PUBLISHING` | 게시 중 | X |
| `STALE` | 폴링 5회 연속 실패 (복구 가능, 24h 재시도 후 FAILED) | X |
| `PUBLISHED` | 게시 성공 (모든 플랫폼) | O |
| `PARTIAL` | 혼합 결과 (일부 플랫폼 성공, 일부 실패) | O |
| `FAILED` | 최종 실패 또는 STALE 24h 초과 | O |
