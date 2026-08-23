# 플랫폼 계정 자격증명 관리

> **권위본**. 마케팅 플랫폼별 게시 계정 정보(자격증명)의 저장·암호화·관리 정책.

---

## 개요

다시봄 어드민(`/admin/marketing` → **플랫폼 계정** 탭)에서 플랫폼별 게시 계정 정보를 입력하면,
ASM(Again-Spring-Marketing) 서버가 **AES-256-GCM으로 암호화**하여 `credential` 테이블에 저장한다.

```
[어드민 UI]  PUT /api/admin/marketing/credentials/{platform}
     │
[AS BE]  AsmClient (JsonNode 투명 프록시, Bearer)
     │
[ASM]  PUT /api/v1/credentials/{platform}
     │  app.core.crypto.encrypt(json) → (enc_blob, nonce)
     ▼
  credential(platform PK, enc_blob, nonce, updated_at)
```

- **권위 저장소 = ASM** (게시 주체가 ASM이므로). AS BE는 암호화 키를 보지 않는 단순 프록시.
- secret 필드(비밀번호·토큰·TOTP)는 **저장 후 평문으로 다시 반환되지 않는다.** 조회 시 설정 여부(`secret_set`)만 노출.
- 실제 게시(M6 publisher)에서 이 자격증명을 복호화해 사용한다(게시 구현은 별도 작업).

---

## 플랫폼별 필드 스키마 (권위본 = ASM `app/domain/credentials.py`)

`secret`=암호화·마스킹 대상, `required`=upsert 시 필수.

| platform (value)  | key             | secret | required |
|-------------------|-----------------|:------:|:--------:|
| `x`               | `email`         |        | ✓ |
|                   | `password`      | 🔒     | ✓ |
|                   | `totp_secret`   | 🔒     |   |
|                   | `storage_state` | 🔒     |   |
| `instagram_feed`  | `email`         |        | ✓ |
|                   | `password`      | 🔒     | ✓ |
|                   | `totp_secret`   | 🔒     |   |
|                   | `storage_state` | 🔒     |   |
| `instagram_reels` | `app_id`        |        |   |
|                   | `app_secret`    | 🔒     |   |
|                   | `ig_user_id`    |        |   |
|                   | `access_token`  | 🔒     |   |
|                   | `graph_host`    |        |   |
|                   | `email`         |        |   |
|                   | `password`      | 🔒     |   |
|                   | `totp_secret`   | 🔒     |   |
|                   | `storage_state` | 🔒     |   |
| `naver_blog`      | `naver_id`      |        | ✓ |
|                   | `password`      | 🔒     | ✓ |
|                   | `storage_state` | 🔒     |   |
| `naver_clip`      | `naver_id`      |        | ✓ |
|                   | `password`      | 🔒     | ✓ |
|                   | `storage_state` | 🔒     |   |
| `youtube_shorts`  | `client_id`     |        | ✓ |
|                   | `client_secret` | 🔒     | ✓ |
|                   | `refresh_token` | 🔒     |   |
|                   | `channel_id`    |        |   |
| `shortform_video` | `tts_voice`     |        |   |
|                   | `comment_tts_voices` |   |   |
|                   | `bgm_track`     |        |   |
| `threads`         | `storage_state` | 🔒     |   |

> FE는 이 스키마를 **GET 응답의 `fields` 배열**로 받아 폼을 동적 렌더한다(드리프트 없음).
> 한국어 라벨만 FE(`PlatformCredentialsSection.tsx`)의 라벨 사전에서 보강.
>
> **`storage_state`** = social-poster(Playwright)가 사용하는 로그인 세션(쿠키/스토리지) 직렬화 값. secret으로 암호화 저장하며, 보통 어드민 폼이 아니라 세션 시딩 경로(ASM `/api/v1/sessions/{platform}`)로 주입된다. API 기반인 `youtube_shorts`에는 없음.
> **로그인 식별자**: X 게시는 타겟 `x_thread`이며, ASM 자격증명 PK는 로그인 세션용으로 여전히 `x`다(어드민 라벨은 「X 4단 스레드」). `instagram_*`는 `email`, `naver_*`는 `naver_id`. 권위본은 항상 ASM `app/domain/credentials.py`의 `PLATFORM_CREDENTIALS`.
> **`youtube_shorts.refresh_token`**: OAuth로 자동 획득(폼 숨김).
> **`shortform_video`**: 로그인·게시 기능이 없는 **설정 전용 pseudo-platform**(2026-08-10 도입). `instagram_reels`/`youtube_shorts`는 WaggleBot에서 같은 영상을 한 번만 렌더링해 재사용(paired render)하므로, 나레이션(`tts_voice`=본문·클로징 낭독 voice key, `comment_tts_voices`=댓글 낭독 풀 콤마구분 최대5·렌더 시 댓글마다 랜덤 배정)은 플랫폼별이 아니라 여기 한 곳에서만 설정한다. 어드민 UI는 「설정」탭의 「숏폼영상」 박스(`ShortformVideoSection.tsx`). 소비처는 ASM `app/worker/youtube_shorts_pipeline.py`의 `_apply_tts_credentials()`.
> `bgm_track`(2026-08-23 추가)도 같은 박스에서 고른다 — 값은 WaggleBot 카탈로그의 `path`(`/api/media/bgm/{emotion}/{file}.mp3`)를 그대로 저장한다.
> **비워두면 사연의 `hook_emotion`에 맞춰 WaggleBot이 매번 자동 선택**하며, 값이 있을 때만 그 곡으로 고정된다(어드민 UI의 「자동 선택」 항목이 빈 값).
> **`instagram_reels` Graph API**: `app_id`/`app_secret`(Meta 앱) + `ig_user_id` + `access_token`(Instagram User/Page 토큰)은 **ASM credential AES-256-GCM만** SSOT. `.env`에 토큰·App Secret을 두지 않는다(YouTube `client_secret`/`refresh_token`과 동일). `graph_host` 기본=`graph.facebook.com`(Instagram Login이면 `graph.instagram.com`). Instagram Login은 공개 `video_url` 게시. Graph가 `User access is restricted`(25/2207050)를 주면 Playwright로 영상을 올리지 않고, `storage_state`로 `scraping_warning` **닫기**만 누른 뒤 Graph를 한 번 재시도한다.
> **`threads`**: 로그인 자격은 `instagram_feed`에서 런타임 상속 — 어드민 입력 불필요.

---

## 병합(merge) 시맨틱 — 부분 수정

`PUT /api/v1/credentials/{platform}`는 기존 문서에 입력값을 **병합**한다:

| 입력 | 동작 |
|---|---|
| secret 필드가 비었거나 누락 | 기존 값 **유지** (재입력 불필요) |
| secret 필드에 새 값 | **교체** |
| public 필드에 값 | 교체 |
| public 필드가 빈 문자열 | **삭제(clear)** |
| 스키마에 없는 key | 무시(drop) |

병합 후 `required` 필드가 비면 **400**. 미지원 platform도 **400**.
TOTP 등 secret 값을 완전히 비우려면 해당 플랫폼을 **삭제 후 재등록**한다.

---

## 암호화 설계

- 알고리즘: **AES-256-GCM** (`app/core/crypto.py`, `cryptography` 라이브러리 `AESGCM`).
- 마스터키: `ASM_CREDENTIAL_KEY` (base64 32바이트). 생성: `openssl rand -base64 32`.
- 매 암호화마다 랜덤 **12바이트 nonce** 생성 → `credential.nonce`에 분리 저장. 인증 태그는 `enc_blob`에 포함.
- 저장 포맷: `enc_blob = AESGCM(key).encrypt(nonce, json_utf8, None)`, `nonce = 12 bytes`.

### 키 관리

- `ASM_CREDENTIAL_KEY`는 **ASM `.env`에만** 두고 git 커밋 금지(`docker-compose.yml`의 `asm` 서비스가 `env_file: .env`로 주입).
- **키 분실 시 기존 암호문은 복호화 불가** → 모든 플랫폼 계정 재입력 필요.
- 키 로테이션: 새 키로 교체하면 기존 행 복호화가 깨지므로, 교체 전 각 플랫폼을 재저장하거나 일괄 재입력해야 함.

---

## 시스템 시크릿 (`system_secret`)

플랫폼 계정 UI와 분리된 **마케팅 런타임 키** 테이블. 동일 `ASM_CREDENTIAL_KEY`로 AES-256-GCM.

| vault key | env (기동 주입) | 비고 |
|---|---|---|
| `asm.bearer_token` | `ASM_BEARER_TOKEN` | ASM API Bearer 권위본 |
| `asm.callback_token` | `ASM_CALLBACK_TOKEN` | AS↔ASM 콜백 |
| `wagglebot.api_key` | `WAGGLEBOT_API_KEY` | WaggleBot `EXTERNAL_API_KEY`와 맞춤 |
| `llm.anthropic_api_key` | `ANTHROPIC_API_KEY` | ASM 쪽 LLM |

- 스키마: `secret_key` PK, `enc_blob`, `nonce`(12B), `updated_at`.
- 기동: `app.main` lifespan → `load_into_environ()` → `get_settings.cache_clear()`.
- 시딩(1회): `scripts/seed_system_secrets_from_env.py` 후 ASM `.env`에서 해당 키 삭제.
- ASM `.env` 부트스트랩만: `ASM_DATABASE_URL`, `ASM_CREDENTIAL_KEY` (+ 비시크릿 URL/포트).
- AS가 호출할 때 쓰는 `ASM_API_TOKEN` 복사본은 AS `encrypted_secret` (`asm.api_token`) — 권위본은 위 Bearer.

---

## 보안 주의

- secret 값은 GET 응답·로그·**감사로그** 어디에도 평문 노출 금지. `@Auditable`은 action·targetType·`targetId(=platform)`만 기록(요청 본문 미기록).
- AS BE→ASM 구간은 내부망(Tailscale IP `100.115.252.61`)+Bearer 인증, 평문 JSON. 브라우저→AS BE 구간은 HTTPS.
- ASM `credential` 테이블은 dev·prod 마케팅이 공유하는 단일 인스턴스에 저장된다.

---

## 관련 코드

| 영역 | 경로 |
|---|---|
| ASM 암호화 | `app/core/crypto.py` |
| ASM system_secret | `app/core/system_secrets.py` · `system_secret` 테이블 |
| ASM 스키마·병합·마스킹 | `app/domain/credentials.py` |
| ASM API | `app/api/routes_credentials.py` · `app/api/routes_waggle_voices.py` |
| AS BE 프록시 | `backend/.../marketing/AsmClient.java` (`listCredentials`/`upsertCredential`/`deleteCredential`/`listWaggleVoices`/`getWaggleVoiceSample`) |
| AS BE 엔드포인트 | `backend/.../api/admin/AdminMarketingController.java` |
| FE API | `frontend/lib/api/admin/marketing.ts` |
| FE UI | `frontend/components/admin/marketing/PlatformCredentialsSection.tsx` |
