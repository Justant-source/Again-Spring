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
  "autoPublish": false,                  // true 시 READY 도달 즉시 자동 게시
  "renderProfile": "marketing_v2"        // [선택사항] 렌더 프로필 ('marketing_v2'|'marketing_fast') — 테스트 탭 전용
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
  "remote_status": null,
  "remote_phase": null,
  "progress": 0,
  "targets": ["naver_blog", "x_thread"],
  "auto_publish": false,
  "render_profile": "marketing_v2",
  "artifacts": null,
  "publications": null,
  "error_message": null,
  "processing_detail": null,
  "generation_diagnostics": null,
  "waiting_external_since": null,
  "sla_breached_at": null,
  "requested_by": null,
  "poll_fail_count": 0,
  "last_polled_at": null,
  "scheduled_publish_at": "2026-08-16T11:30:00Z",
  "rescheduled_count": 0,
  "rescheduled_reason": null,
  "original_scheduled_at": "2026-08-16T11:30:00Z",
  "last_rescheduled_at": null,
  "created_at": "2026-06-09T05:00:00Z",
  "updated_at": "2026-06-09T05:00:00Z"
}
```

`scheduled_publish_at`는 모든 잡에서 NOT NULL이다 (V117) — 생성 시각을 넣는다.
`autoPublish=true` 잡은 **READY 도달 즉시** 게시되며 이 컬럼으로 시각을 기다리지 않는다.
`autoPublish=false`(render-only) 잡은 `findDueAutoPublishJobs`에 안 걸린다.

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

> ASM `/api/v1/waggle/*` → WaggleBot. 어드민 JWT로 미리듣기·선택. `tts_voice`/`comment_tts_voices`는 `shortform_video` pseudo-platform(설정 전용) 자격증명 public 필드 — Reels/Shorts **유니크 mp4**여도 보이스 풀은 공유 가능. Phase 2: `hook_emotion` → S2 Pro. 상세: `credentials.md`.

```
GET /api/admin/marketing/tts/voices
GET /api/admin/marketing/tts/voice-sample?path=/api/tts/voices/{key}/sample
Authorization: Bearer <admin-jwt>
```

**voices 200** — `{ defaultVoice, voices:[{ key, label, gender?, sampleUrl, hasSample, ... }] }`  
`sampleUrl` = WaggleBot 키 기반 경로(`/api/tts/voices/{key}/sample`). 미리듣기는 `voice-sample`로 스트리밍 (path는 `/api/tts/voices/*/sample` 또는 `/api/media/voices/` 접두만 허용).

### 1.5.2 WaggleBot 배경음악 (숏폼영상 설정)

> TTS 음성과 같은 경로. `bgm_track`도 `shortform_video` pseudo-platform 자격증명 public 필드다.
> **비워두면 사연의 `hook_emotion`에 맞춰 WaggleBot이 자동 선택한다** — 값이 있을 때만 그 곡으로 고정된다.

```
GET /api/admin/marketing/bgm/tracks
GET /api/admin/marketing/bgm/sample?path=/api/media/bgm/{emotion}/{file}.mp3
Authorization: Bearer <admin-jwt>
```

**tracks 200** — `{ tracks:[{ emotion, emotionLabel, file, path }] }`
감정 5종(`shock`·`anger`·`tension`·`sad`·`hype`) × 2곡. `path`가 곧 선택값이자 미리듣기 경로이며,
그대로 `variant_config.bgm_track`으로 저장된다. `sample`은 `/api/media/bgm/` 접두만 허용.

---

### 1.6 실패 잡 일괄 재구동

```
POST /api/admin/marketing/jobs/redrive
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

실패한 마케팅 잡(또는 필터 쿼리)을 재구동한다. 요청 직후 반환하며(즉시 응답), 실제 완료는 기존 폴링 스케줄러가 관찰한다.

**Request Body**
```json
{
  "jobIds": [1, 2, 3],
  "filter": null,
  "skipExisting": false
}
```

| 필드 | 설명 |
|---|---|
| `jobIds` | 재구동할 잡 ID 배열. `filter`가 없을 때 필수. |
| `filter` | 대체 필터 쿼리: `{"status": "FAILED", "since": "2026-08-16T00:00:00Z"}`. `jobIds`가 있으면 무시됨. |
| `skipExisting` | `true`일 때, 이미 `PUBLISHED` 상태인 플랫폼은 재생성 스킵 (child job 재사용, idempotent). 기본값 `false`. |

**Response 200**
```json
{
  "requested": 3,
  "results": [
    {
      "sourceId": 1,
      "targetId": 2,
      "action": "REGENERATED",
      "reason": null,
      "platformStates": {
        "youtube_shorts": "RUNNING",
        "instagram_reels": "PUBLISHED"
      }
    },
    {
      "sourceId": 2,
      "targetId": 3,
      "action": "SKIPPED",
      "reason": "모든 플랫폼이 이미 PUBLISHED",
      "platformStates": null
    },
    {
      "sourceId": 3,
      "targetId": null,
      "action": "ERROR",
      "reason": "원본 잡이 존재하지 않음",
      "platformStates": null
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `sourceId` | 원본 잡(실패한 잡) ID |
| `targetId` | 새로 생성된 자식 잡 ID. SKIPPED/ERROR면 `null`. |
| `action` | 취한 조치: `REGENERATED`(ASM regenerate 성공) / `RECREATED`(409 fallback, 새로 create) / `SKIPPED`(모든 플랫폼 published) / `ERROR`(예외 발생) |
| `reason` | 스킵/에러 메시지 (성공 시 `null`) |
| `platformStates` | `targetId`가 있을 때만 포함. 자식 잡의 플랫폼별 현재 상태(`PUBLISHED`, `RUNNING`, `FAILED` 등). |

**오류**
| 코드 | 이유 |
|---|---|
| 400 | `jobIds`·`filter` 모두 빈 경우 |
| 401/403 | 인증 없음 또는 권한 없음 |

**Telegram 인라인 버튼 통합** (선택사항):
- `MARKETING_TELEGRAM_BUTTONS_ENABLED=true`일 때, 마케팅 잡이 `FAILED` 상태로 알림 메시지가 발송되면
  Telegram 인라인 버튼 2개가 붙는다: "재구동" / "무시"
- "재구동" 버튼 클릭 → ASM webhook 수신 → 이 API 호출 (`POST /api/admin/marketing/jobs/redrive` with `jobIds=[<jobId>]`)
- "무시" 버튼 클릭 → alert 추적만 갱신, API 호출 없음
- 멱등성: 이미 비터미널 child job이 있으면 버튼 미표시 (redundant redrive 방지)
- callback_data 포맷: `redrive:{env}:{jobId}:{epochSecond}` / `ignore:{env}:{jobId}`

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

> `brief.tags`: 브랜드 `#다시봄` + `#againspring` 필수. 24h 홀딩 신규 시드(IG feed용) = `#다시봄` `#againspring` `#공감비율` `#[카테고리]`(≤5, 어드민 대기 탭에서 편집 가능, 기존 홀딩 백필 없음). X = 브랜드 2개만(카테고리 없음). 수동 잡 생성(`/api/admin/marketing/jobs`)은 카테고리명만 채움. 상세 [`platforms.md`](platforms.md).
>
> `brief.promo_title`: **마스터 훅** (광장 `title`과 분리). AS `PromoTitleService`/훅 필드.
> `brief.hook_emotion`: `shock|anger|tension|sad|hype` → **WaggleBot S2 Pro TTS** (Phase 2 SSOT).
> 영상 슬롯 확정 시: `hook_reels`/`hook_shorts` · `script_reels`/`script_shorts` ·
> `max_duration_reels_sec`(30) / `max_duration_shorts_sec`(45) 목표 · alone 시 `max_duration_sec`. Reels 32초·Shorts 47초 상한은 본문 TTS에만 적용한다. 댓글 2개와 아웃트로는 이후에 붙고 최종 MP4에는 별도 하드 상한을 적용하지 않는다.
> 시봄이: `sibom_candidates`(≤12) · `sibom_plan`(채널별). **`metaphor_id`/`metaphor_ids`는 영상 렌더에서 무시**.
> 삽입 계약 SSOT: [`sibom-video-insertion.md`](sibom-video-insertion.md).
>
> UTM (Phase 1 유지): AS가 잡 생성 시 `brief.post_url`(+ `options.post_urls`/`utm_campaign`)에 부착.
> `utm_source`=`x`|`instagram`|`youtube`, `utm_medium=organic`, `utm_campaign=story_{localJobId}`,
> `utm_content={postId}_{hookType}` (`master`|`reels`|`shorts`|`feed` …). 랜딩 = 사연 상세. **IG 캡션에는 URL/UTM 없음**.

**Request Body (StoryBrief)** — Phase 1 유지 + Phase 2 영상 필드
```json
{
  "source_id": "abc123def456",
  "callback_base_url": "http://100.81.189.92:8090",
  "brief": {
    "title": "광장 사연 제목",
    "promo_title": "자극\n마스터\n훅",
    "hook_emotion": "tension",
    "hook_reels": "릴스용 변형 훅",
    "hook_shorts": "쇼츠용 변형 훅",
    "script_reels": "훅 → 요약 → 클리프행어 대본",
    "script_shorts": "훅 → 요약 → 클리프행어 대본",
    "max_duration_sec": 45,
    "sibom_candidates": ["waiting-reply", "drained", "side-glance"],
    "sibom_plan": [
      {
        "role": "intro",
        "image_id": "waiting-reply",
        "caption": "읽씹 3일차",
        "beat_index": 0,
        "size": "large",
        "dwell": "hold"
      }
    ],
    "metaphor_id": "empty-chair",
    "neutral_summary": "중립 요약 (최대 500자)",
    "side_a": "작성자 관점(또는 요약 스크립트)",
    "side_b": "상대방 관점",
    "empathy_ratio": { "a": 62, "b": 38 },
    "tags": ["#다시봄", "#againspring", "#공감비율", "#이별"],
    "post_url": "https://againspring.net/community/abc123def456?utm_source=youtube&utm_medium=organic&utm_campaign=story_42&utm_content=abc123def456_shorts",
    "policy": {
      "no_emoji": true,
      "forbidden_terms": ["판결", "처방", "승패", "승자", "패자"]
    }
  },
  "targets": ["youtube_shorts"],
  "options": {
    "voice_id": null,
    "tone": null,
    "auto_publish": false,
    "hook_emotion": "tension",
    "utm_campaign": "story_42",
    "post_urls": {
      "youtube_shorts": "https://againspring.net/community/abc123def456?utm_source=youtube&utm_medium=organic&utm_campaign=story_42&utm_content=abc123def456_shorts"
    }
  }
}
```

| brief 필드 | 페이즈 | 설명 |
|---|---|---|
| `title` | 1 유지 | 광장용. SNS 훅과 분리 |
| `promo_title` / `hook_text` | 1 유지 | 마스터 훅 |
| `hook_emotion` | 1+2 | enum 5종. **Phase 2: TTS에 전달** |
| `hook_reels` / `hook_shorts` | **2 SSOT** | 영상 슬롯 확정 시 변형 훅 (`VideoVariantService`). `/`·`／`는 공백으로 정규화 |
| `script_reels` / `script_shorts` | **2 SSOT** | 요약 대본 (전문 낭독 금지) |
| `max_duration_reels_sec` / `max_duration_shorts_sec` | **2 SSOT** | 30 / 45 |
| `max_duration_sec` | **2 SSOT** | alone 잡 활성 캡 |
| `sibom_candidates` | **시봄이** | string[] ≤12. 사연 생성 후 코드 shortlist |
| `sibom_plan` | **시봄이** | 채널별 삽입 플랜 배열. 인트로=`role=intro`. 상세 [`sibom-video-insertion.md`](sibom-video-insertion.md) |
| `metaphor_id` / `metaphor_ids` | **무시(영상)** | DB 보존만. 영상 렌더·썸네일 경로에서 **사용하지 않음** |
| `tags` | 1 유지 | 플랫폼 clamp. 브랜드 2 항상 |
| `post_url` + UTM | 1 유지 | X·YT 출구. `utm_content`의 hookType 구분 |
| `empathy_ratio` | 1 유지 | **실투표 %** |

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

파일이 없으면 404. **영상 mp4**는 게시 직후 삭제하지 않고, 해당 플랫폼이 `PUBLISHED`가 된 시각부터 **30일** 지난 뒤에 ASM 워커가 로컬 바이트만 지운다(메타데이터 row는 유지 → 그 시점부터 이 GET은 404). 권위 정책: [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) 파이프라인 주석.

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

업로드된 커스텀 커버·자동 썸네일은 다음 발행 시 반영된다:
- **YouTube Shorts**: 영상 업로드 성공 후 항상 `thumbnails.set` API 호출(실패해도 게시 자체는 성공 처리 — non-fatal). **우선순위** = 운영자 `customcover` → WaggleBot 인트로 썸네일(`sibom_plan` intro 합성 PNG, `thumbnailUrl`) → mp4 frame0 추출. **메타포 PNG 금지**. 1×1 플레이스홀더(≤1KB)는 업로드하지 않는다. Shorts 선반은 `oar` 자동프레임(본문 씬)을 고를 수 있어 **API 썸네일 등록이 필수**이며, 영상 첫 프레임(인트로)은 API 실패 시 백업일 뿐이다.
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
GET    /api/v1/waggle/bgm/tracks                     # WaggleBot BGM 카탈로그
GET    /api/v1/waggle/bgm/sample?path=/api/media/bgm/… # BGM 샘플 프록시
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

**내부 재구동 채널 (2026-08-20)** — 텔레그램 "재구동" 버튼 승인 시 ASM이 같은 토큰으로 호출하는 내부 엔드포인트. 어드민 JWT 없이 이 채널로만 접근하며, 단일 잡만 지원(필터 미지원). **ASM은 호출자 신원으로 `remoteJobId`(ULID)를 함께 제공해야 하며**, AS는 이를 DB의 저장된 `remoteJobId`와 검증한다. 이는 dev 환경에서 job id 재사용 시 잘못된 잡을 재구동할 위험을 차단한다(조회 테스트 참조: 2026-08-20 진행 중 발견). 응답은 §1.6 redrive와 동일한 `{requested, results[]}` 구조.

```
POST /api/internal/marketing/redrive
Authorization: Bearer {ASM_CALLBACK_TOKEN}
{"jobId": 689, "remoteJobId": "01KZZ30T74V3SB8WQW7VA5AEC1", "skipExisting": true}
```

| 필드 | 설명 |
|---|---|
| `jobId` | 재구동할 로컬 잡 ID (long). 단일 값만 허용. |
| `remoteJobId` | ASM 원격 잡의 ULID (예: `01KZZ30T74V3SB8WQW7VA5AEC1`). 신원 검증 필수. 빈 문자열이면 400 거부. |
| `skipExisting` | `true`일 때, 이미 `PUBLISHED` 상태인 플랫폼은 재생성 스킵 (child job 재사용, idempotent). 기본값 `false`. |

**Response 200** — `{requested: 1, results: [{...}]}` (§1.6과 동일)

**오류**
| 코드 | 이유 | 로그 |
|---|---|---|
| 401 | 유효하지 않거나 누락된 Authorization 헤더 | (기존 debug 로그) |
| 400 | `remoteJobId` 누락 또는 공백 | `[REDRIVE_REJECT] reason=NO_IDENTITY jobId=...` |
| 404 | `jobId`가 존재하지 않음 | `[REDRIVE_REJECT] reason=JOB_NOT_FOUND jobId=...` |
| 409 | `remoteJobId` 저장값과 불일치 (신원 검증 실패) | `[REDRIVE_REJECT] reason=IDENTITY_MISMATCH jobId=... expected=<stored> got=<given>` |

**배경**
Telegram 버튼의 callback_data는 `{env}:{jobId}`를 포함한다. env와 jobId가 다른 소스에서 나오면 불일치할 수 있다. 실제 버그: 버튼이 `dev:698`을 지정했으나 job 698은 prod에만 존재 → dev로 라우팅 시 not found (무해). 위험: dev는 job id를 리셋한 적이 있어(현재 max=65), 재사용되는 id는 스테일 버튼으로 다른 잡을 재구동할 수 있다. 해결책: remoteJobId(ULID)로 신원 증명 → 재사용 불가능.

**성공 로그**
```
[REDRIVE_INTERNAL] jobId=689 remoteJobId=01KZZ... skipExisting=true via=asm-callback-token
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
    { "platform": "x", "state": "published", "url": "https://x.com/..." },
    {
      "platform": "youtube_shorts",
      "state": "PUBLISHED",
      "url": "https://www.youtube.com/shorts/...",
      "partner_comment": {
        "state": "PUBLISHED",
        "comment_id": "Ug...",
        "url": "https://www.youtube.com/watch?v=...&lc=Ug...",
        "truncated": false,
        "error": null
      }
    }
  ],
  "event": "terminal_state_transition",
  "error": null
}
```

**Response 204 No Content** — 성공 시 본문 없음  
**오류**: 
- 401 — 잘못된 또는 누락된 `Authorization` 헤더
- 400 — 필수 필드 누락

**품질 진단·실패 계약 (additive)**: 콜백/잡 조회는 `failure_code`, `failure_stage`, `retryable`, `error_summary`, `actual_duration_ms`, `diagnostics`(ASM은 호환을 위해 `generation_diagnostics`도 허용)를 추가로 보낼 수 있다. 진단에는 최종 시봄이 플랜·적용 장수·본문/댓글/아웃트로/MP4 실제 길이·폴백/실패 사유만 넣고 원문 프롬프트나 LLM 원출력은 넣지 않는다. `SIBOM_*`, `VARIANT_*`, `DURATION_*`, `LAYOUT_*`(예: `LAYOUT_OUTRO_MISSING` — `outro_duration_ms<=0`)는 READY/자동 게시로 승격할 수 없는 터미널 품질 실패다. 인프라 동시성 충돌은 `INFRA_DB_CONFLICT`·`retryable=true`로 구분한다.

**양면 Shorts 댓글 계약 (additive)**: 신규 `youtube_shorts` 양면 사연은 영상 게시 후
`brief.partner_body`를 첫 댓글로 작성한다. 결과는 해당 publication의 `partner_comment`에
기록한다. `partner_comment.state=FAILED`여도 영상 publication은 `PUBLISHED`로 유지하며, AS는
운영 알림에서 수동 댓글 작성·고정을 안내한다.

**목적**: AS는 콜백 수신 후 원격 상태(`status`, `phase`, `progress`, `artifacts`, `publications`)와 `error`를 DB에 반영하고 폴링 오류 카운트를 초기화합니다. `error`가 있으면 `marketing_job.error_message`에 최대 1,000자로 저장하며, 최상위 `error`가 비어도 `publications[].error`의 실패 상세를 사용합니다. 원격 `FAILED`/`PARTIAL` 텔레그램은 ASM만 전송해 중복을 막습니다.

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
             (단 artifacts 있으면 READY 유지 — 미리보기 보존)
           연결 실패 시 ASM circuit 5분 open (나머지 잡 GET 중단)
           
           READY + artifacts → GET 스킵 (게시는 due-slot/수동 publish)
           STALE + artifacts → READY 복구
           STALE (artifacts 없음) → 지수 백오프 / 24h 초과 시 FAILED
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

---

## 4. Phase 2 어드민 — cap · 점수 · 통계 (타깃 SSOT)

> 경로·DTO 이름은 구현 PR에서 Flyway/컨트롤러와 맞출 수 있다. **의미·기본값**이 계약이다. 상세 식·키: [`platforms.md`](platforms.md).

### 4.1 채널별 일일 cap

```
GET  /api/admin/marketing/quota
PUT  /api/admin/marketing/quota
Authorization: Bearer <admin-jwt>
```

**타깃 body (PUT)**
```json
{
  "x_thread": 3,
  "instagram_feed": 3,
  "instagram_reels": 3,
  "youtube_shorts": 3
}
```

저장 키: `marketing.cap.{platform}` (기본 **각 3**).  
레거시 `marketing.daily_text_cap` / `daily_video_cap`는 마이그레이션 후 폐기.

### 4.2 플랫폼별 점수 가중치 · auto_adjust

```
GET  /api/admin/marketing/score-weights
PUT  /api/admin/marketing/score-weights          // platforms map and/or legacy + optional autoAdjust
POST /api/admin/marketing/score-weights/auto-adjust/run   // 1회 실행 (cron도 Mon 09:00 KST)
```

가중치 심볼: `hook`, `vote_skew`, `comments`, `votes`, `views`, `has_partner` — 플랫폼마다 독립. 기본 계수는 [`platforms.md`](platforms.md) §popularity 식.  
`marketing.score.auto_adjust` 기본 **`false`**. on이면 주간 통계로 **소폭**(상대 ±5% · 절대 ±0.05) 보정만. **프롬프트 자동 패치 없음(M4)**.

### 4.2.1 플랫폼 통계 수집 · 주간 리포트 (Phase 2.6–2.7)

**저장 선택**: 수집기는 **ASM**(자격증명·`publication.remote_id`). 캐논 스냅샷은 **AS** `marketing_publication_stats` (V110) — 주간 리포트·`auto_adjust`가 AS에 있음. ASM에도 `publication_stat` 로컬 캐시.

```
POST /api/admin/marketing/stats/collect?lookbackDays=14&limit=40  → 202 {runId,status}
GET  /api/admin/marketing/stats/collect/{runId}                   → {status,summary?}
GET  /api/admin/marketing/weekly-report?weeksAgo=0
```

수동 수집은 **비동기**(CF/nginx 60s 타임아웃 회피). FE가 `runId`를 폴링. AS→ASM body에 `skip_slow=true`(X Playwright 생략, IG/YT만).

ASM: `POST /api/v1/stats/collect` (`skip_slow`) · social-poster `POST /stats/x`.

| 플랫폼 | 지금 수집 가능 | 대표 지표(primaryMetric) | 비고 |
|---|---|---|---|
| X | impressions≈views, likes, replies, reposts | `impressions` | 세션 Playwright scrape (`/stats/x`). API 키 없음. **스케줄 수집(06:30)에서만 채워짐** — 수동 버튼은 항상 건너뜀 |
| IG Feed | reach, saves, shares, likes, comments | `reach` | Graph insights. media_id는 발행 URL이 없어 **발행 시각 대조**로 역추적(아래 참조) |
| IG Reels | reach, saves, shares, likes, comments | `reach` (2026-08-22부터, 과거 `plays`) | Graph insights + numeric media id. **v21에서 `plays`/`views` 제거** — 요청하면 전건 HTTP 400 |
| YT Shorts | views, likes, comments, avg_view_duration_sec | `views` | Data API `videos.list` + Analytics API |

일 06:30 KST 스케줄 수집(`skip_slow=false`, X 포함). 실패는 배치 전체를 막지 않음.

#### 2026-08-22~23 장애 대응 — 4채널 전부 미수집이었던 근본원인과 조치

**증상**: admin `/marketing` 통계 탭 "데이터 건강"이 4채널 전부 오류/미확인, 실제로 X를 제외한 IG·YT는 며칠째 지표가 전혀 안 쌓이고 있었다. 표면 오류를 하나 고치면 그 뒤에 또 다른 원인이 있는 패턴이 채널마다 반복됐다 — **오류 메시지가 지목하는 원인을 그대로 믿지 말고, 실제 값이 들어오는 것까지 확인**해야 한다는 게 이번 사고의 핵심 교훈이다.

| 채널 | 겹쳐 있던 원인 | 조치 |
|---|---|---|
| **IG Reels** | ① 토큰에 `instagram_manage_insights` scope 없음 ② 권한을 고쳐도 v21에서 제거된 `plays`/`views`를 요청해 전건 HTTP 400 | 토큰 재발급(아래) + 요청 지표를 `reach,saved,shares,total_interactions,likes,comments`로 변경 |
| **IG Feed** | ① 위와 동일한 토큰 문제 ② 발행 URL이 애초에 저장되지 않아 media_id 확보 불가 ③ media_id를 찾아도 `instagram_feed` 자격에 Graph 토큰이 없어(브라우저 로그인 정보만 존재) `instagram_reels` 자격으로 폴백하지 못함(자격 행 존재 자체가 truthy 체크를 통과해버림) | 발행 시각 대조로 media_id 역추적(아래) + 토큰 유무 기준 폴백으로 수정 |
| **YT Shorts** | ① Google Cloud 프로젝트에서 YouTube Analytics API 자체가 비활성 — **토큰/scope 문제가 아니었다**(scope는 이미 정상, `tokeninfo`로 확인) ② API를 켜자 드러난 파싱 버그: `dimensions=video`로 요청하면 응답의 첫 컬럼은 영상 ID인데 `rows[0][0]`을 지표로 읽어 조용히 `None`이 됨 | 콘솔에서 API 활성화 + `columnHeaders`에서 지표 이름으로 컬럼 위치를 찾도록 수정 |
| **대시보드 배지** | 채널 상태를 "선택된 주 전체 누적 오류 유무"로 판정 — 며칠 전 오류 행 하나가 그 주 내내 채널을 계속 "오류"로 보이게 함(최신 수집이 성공해도 무관) | **플랫폼별 가장 최근 수집 1건**의 성공 여부로 판정하도록 변경. 상단 `partial N / errors N`은 "이번 주 누적 문제 건수"로 계속 별개 지표 |
| **수동 수집 UI** | `skip_slow=true`(타임아웃 회피)로 건너뛴 X를 오류 행으로 저장 → 수동 버튼 누를 때마다 X가 degraded로 떨어짐 | 건너뛴 항목은 결과에서 제외(오류 행으로 저장하지 않음) |

**IG 토큰 재발급 필요 이유**: Meta 앱 설정에서 권한(scope)을 추가해도 **이미 발급된 access_token에는 반영되지 않는다** — OAuth 토큰은 발급 시점의 scope가 고정된다. ASM `scripts/refresh_ig_token.py`가 단기 Explorer 토큰을 stdin으로만 받아(인자/환경변수 미사용, 화면에 값 미표시) scope 검증 → 60일 장기 토큰 교환 → 가능하면 **만료 없는 페이지 토큰**으로 승격 → 암호화 저장까지 처리한다.

**IG Feed 발행 시각 대조**: 사이드카(`publish-instagram.js`)가 공유 직후 `page.url()`에서 `/p/`를 찾아 URL을 얻으려 하는데, 인스타 웹이 공유 완료 후 게시물 페이지로 이동하지 않아 이 검사가 항상 실패해 URL이 NULL로 저장된다. 대신 `publication.updated_at`(발행 시각)과 Graph `/{ig_user_id}/media`가 반환하는 `timestamp`를 대조한다 — 실측 오차 3~5초. **30초 창에 후보가 정확히 하나일 때만** 채택(2건 이상이면 오매칭 위험이 크니 스킵). 창을 넓히면 오히려 나빠진다: 120초 창은 17건 매칭·7건 모호, 30초 창은 24건 매칭·0건 모호 — 연달아 발행된 게시물이 넓은 창에서 서로의 후보가 되기 때문. Graph API가 반환하는 미디어는 최근 100건까지라 그 이전 발행분은 소급 수집 불가.

**채널 배지(healthy/degraded/unknown) 판정 기준**: 플랫폼별 **가장 최근 수집 1건**의 성공 여부로 정한다. 이 기준이라도 **직전에 저장된 낡은 오류 행**이 그날의 "최신"으로 남아 있으면 다음 스케줄 수집(06:30)까지는 배지가 오류로 보인다 — 실제 수집 실패가 아니라 표시 지연이므로, 다음 06:30 이후에도 오류면 그때 실제 장애로 취급할 것.

**결과 검증(2026-08-23 06:31 KST 스케줄 수집)**: X 16건 전건 성공(예: impressions 171~252), IG Feed/Reels 정상, YT Shorts 11/13건 성공(2건은 `youtube video not found` — 삭제된 영상 참조로 지표 수집 로직과 무관). 4채널 모두 정상 배지로 전환 확인.

### 4.3 저녁 슬롯 API (레거시, 자동 발행에 미사용)

```
GET /api/admin/marketing/publish-slots
PUT /api/admin/marketing/publish-slots
```

키 `marketing.publish_slot.{platform}` 은 관리자 UI에 남아 있으나 **T+24h 자동 선정·영상 게시를 게이팅하지 않는다**.
댓글 노티 창: `marketing.comment_notify_hours`(기본 24).

### 4.4 플랫폼 통계 수집 · 주간 리포트

상세는 §4.2.1. 엔드포인트:

- `POST /api/admin/marketing/stats/collect` (202 async) · `GET .../stats/collect/{runId}`
- `GET /api/admin/marketing/weekly-report?weeksAgo=0`
- `POST /api/admin/marketing/score-weights/auto-adjust/run`

채널×훅유형→가입 대시보드는 **후속** (UTM 축적 후).

### 4.5 통계 탭 · 테마 배수 (Phase 3)

어드민 **「통계」** 탭용. 컨트롤러: `AdminMarketingStatsController` (`/api/admin/marketing/stats`).  
수집(`collect`)은 기존 `AdminMarketingController` 경로 유지.

```
GET  /api/admin/marketing/stats/dashboard
     ?platform=&weeksAgo=0&rangeDays=7&primaryMetric=
     → { weekStart, weekEnd, prevWeek*, platforms[{platform,primaryMetric,value,prevValue,deltaPct,series}],
         utm, health, unknownCounts, todoHints }

GET  /api/admin/marketing/stats/theme-matrix?platform=&weeksAgo=0
     → { platform, emotions[], categories[], cells[{emotion,category,n,score,delta,boost,locked}],
         proposals[], rolledProposals[], unknownHints }

POST /api/admin/marketing/stats/theme-matrix/propose?platform=&weeksAgo=0
     → Proposal[] (저장 안 함 · PROPOSE 이벤트)

POST /api/admin/marketing/stats/theme-matrix/apply
     Body: { platform, changes:[{emotion,category,boost}], confirm:true }
     → { applied, before, after, cooldownUntil } | 400

GET  /api/admin/marketing/stats/theme-boosts?platform=
     → { platform, matrix, shadow, cooldownUntil, canApplyNow }

GET  /api/admin/marketing/stats/events?limit=50
     → [{ id, eventType, platform, payloadJson, createdAt }]
```

**설정 키**: `marketing.theme.boost.{platform}.{emotion}.{category}` · `marketing.theme.shadow`(기본 true) · `last_apply_at` · `min_n=3` · `boost_min/max=0.7/1.3` · `delta_cap=0.05`.  
**이벤트 테이블**: `marketing_stats_event` (V111) — `COLLECT_*` · `PROPOSE` · `APPLY` · `SHADOW_TOGGLE`.
