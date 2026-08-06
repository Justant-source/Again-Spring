# YouTube Shorts 전략 — Again Spring × WaggleBot

> **권위본**: 이 문서. `youtube_shorts` 채널의 생성·검수·게시 계약을 담는다.
> 플랫폼 표는 [`platforms.md`](platforms.md), 캡처/피드는 [`instagram-feed-strategy.md`](instagram-feed-strategy.md) · [`x-thread-strategy.md`](x-thread-strategy.md).
> **작성**: 2026-08-04 · 그릴링 확정

---

## 1. 채널 상태

| 항목 | 값 |
|---|---|
| 자동 생성 | **활성** — 사연 `+24h` 후 인기 상위(일 3캡)에 선정되면 `instagram_reels`+`youtube_shorts` **듀얼 잡** 1회 (`XThreadPublishTriggerScheduler`) |
| 자동 게시 | **활성** — 듀얼 잡 `autoPublish=true` → READY 후 YT API + Reels Graph/세션 모두 게시 |
| 렌더 | WaggleBot 블랙박스 (`POST /api/external/jobs`) — LLM은 **Claude CLI 브릿지** (`llm_backend=cli`, 호스트 `~/.claude` = Again Spring과 동일 세션). Anthropic API 직접 호출 금지. **듀얼 타겟은 1회 렌더** |
| 게시 계정 | 다시봄 전용 YouTube (ASM `youtube_shorts` OAuth) |

`instagram_reels`는 동일 9:16·**같은 mp4**. 피드(`instagram_feed`)와 상호배타.  
구(舊) “X/피드 PUBLISHED 후 Shorts만 렌더·수동 승인” 트리거(`maybeTriggerYoutubeShorts`)는 **제거**됨.

---

## 2. 경계

```
AS 24h 분배 → ASM 잡(targets=[instagram_reels, youtube_shorts], auto_publish)
  → WaggleBot ingest+render (1회)
  → 아티팩트: 플랫폼별 upload.json + 동일 video/thumbnail 복제 → READY
  → auto_publish: YouTube API + Instagram Reels 게시
```

- ASM `app/media` 로컬 GPU 파이프는 Shorts에 사용하지 않는다.
- WaggleBot 크롤 채널·자체 YT 업로드는 사용하지 않는다.
- alone `youtube_shorts` / alone `instagram_reels` 관리자 단건도 동일 파이프 유지.

---

## 3. 콘텐츠 스펙

| 항목 | 값 |
|---|---|
| 해상도 | **1080×1920 (9:16)** |
| LTX / ComfyUI | **off** (`videoGen: false`) |
| 프레임 | Waggle 기존 `text_only` / `comments` / `outro` 재활용 |
| 본문 | 작성자 사연 **거의 전문** 낭독 (원문 유지 + 청킹·금지어만) |
| 댓글 | 광장 **좋아요 순 상위 3**(§4.3/§4.5) — 화자별 TTS. (24h 영상 선정은 조회수 인기·일 3캡 — 댓글 수 게이트 없음) |
| 양면(paired) | 영상에는 작성자만. 클로징·첫 댓글만 상대 처리 |
| 클로징 TTS | 솔로: `여러분의 의견을 댓글로 남겨주세요` / paired: `상대방의 사연이 궁금하면 댓글을 확인해주세요` |
| 썸네일 | Waggle 산출 + YT 권장 16:9 커버는 후속 가능 |

### paired 첫 댓글 (게시 승인 시)

- 본문 = 상대방 사연 전문 (한도 초과 시 절단 + 광장 URL)
- **1차: 채널 첫 댓글만** (YouTube Data API에 pin 없음 → pin UI는 후속)

---

## 4. 시각 레이아웃 계약 (락다운 — 구현 반영)

> **상태**: Tone L 레이아웃·댓글 카드·아웃트로가 AS brief → ASM → WaggleBot 경로에 **반영됨** (검증 job `#462`). 아래는 런타임 계약이다.

### 4.1 톤 — Tone L SSOT

Shorts 화면은 다시봄 앱의 **Tone L(편지지)** 팔레트·타이포를 그대로 이식한다. 권위본은 `docs/frontend/design/system.md` §3.1/§3.4/§4 — 아래 값이 어긋나면 그 문서를 따른다.

| 항목 | 토큰 | 값 |
|---|---|---|
| 배경 | `--L-bg` | `#EDF1E8` |
| 카드 | `--L-card` | `#F7F9F2` |
| 본문 잉크 | `--L-ink` | `#2E3A2E` |
| 보조 텍스트 | `--L-sub` | `#7C8A77` |
| 보더 | `--L-border` | `#D3DCC9` |
| 포인트 | `--L-point` | `#8A3A1F` |
| 작성자 진영색 | `--faction-author` | `#C9785A` (피치) |
| 상대방 진영색 | `--faction-partner` | `#5F8F76` (세이지) |
| 세리프 (제목·본문 낭독 텍스트) | `--font-serif` | `'Noto Serif KR'` |
| 산세리프 (UI·라벨·통계) | `--font-sans` | `'Noto Sans KR'` |

### 4.2 본문 프레임

- **좌측 정렬**, 최대 **3줄**. 가운데 정렬·중앙 배치였던 기존 프레임과 다르다.
- 폰트는 세리프(`--font-serif`), 색은 `--L-ink`.

### 4.3 댓글 프레임

- 노출 **최대 3개** (기존 2개에서 확대).
- **fade-in 스택** — 댓글이 순서대로 하나씩 페이드인하며 쌓인다 (한 번에 다 뿌리지 않음).
- **실제 닉네임** 노출 (익명 처리 아님) + **medium blur** 배경 처리.
- **좋아요 수 + 상대 시간**(예: `3일 전`)을 닉네임과 함께 표시.
- 진영색 적용 — 작성자 댓글은 `--faction-author`, 상대방 댓글은 `--faction-partner`, 그 외 일반 유저는 중립(잉크색).

### 4.4 아웃트로

- **마스코트 없음** (기존 AI 배심원류 마스코트 요소 배제).
- CTA는 **Tone L** 팔레트로 통일 (배경 `--L-bg`/`--L-card`, 텍스트 `--L-ink`, 포인트만 `--L-point`).

### 4.5 brief `top_comments` 계약

`MarketingJobService` brief의 `top_comments` (`CreateJobRequest.TopCommentDto`) → ASM → WB `comments` 프레임이 소비한다.

| 필드 | 설명 |
|---|---|
| `author` | **닉네임** (`UserRepository` 조회, 없으면 "익명"). hex-like authorId는 ASM/WB에서 `익명` 가드 |
| `author_id` | 원본 user id (선택) |
| `like_count` | 좋아요 수 |
| `created_at` | 상대 시간용 ISO-8601 Instant |
| `side` | `author` / `partner` / `neutral` |

- **limit 3**. 본문 TTS용 `body`는 별도 유지.

---

## 5. AS brief 필드 (Shorts)

| 필드 | 설명 |
|---|---|
| `title` / `promo_title` | 훅·제목 |
| `metaphor_id` | 사연 생성 시 매칭된 메타포 일러스트 ID (60종). Shorts intro에 사용. 없으면 크림 빈화면 |
| `side_a` 또는 `author_body` | 작성자 본문 **전문** |
| `partner_body` | paired일 때만 상대 본문 **전문** |
| `top_comments` | `{ author, author_id?, body, like_count, created_at, side }[]` 최대 3, body 전문. `author`=닉네임(익명 fallback), `side`=`author`/`partner`/`neutral` (§4.5) |
| `paired` | boolean |
| `post_url` | 광장 URL |

### 5.1 게시 카피 (Reels / Shorts 공통)

`upload.json` + 게시 본문 계약 (`ASM app/publishers/video_copy.py`):

| 항목 | 규칙 |
|---|---|
| 제목 | `promo_title` → `title` (YT `snippet.title` / 캡션 첫 줄) |
| 링크 | 실제 광장 URL `https://againspring.net/community/{postId}` — 본문에 필수 |
| CTA | `당신은 어느 쪽에 공감하나요?` |
| 해시태그 | 카테고리별 실검색 태그 (`#다시봄` 필수 + `#가족`/`#직장`/`#연애` + `#빡침` 등). IG ≤5, YT ≤15. **본문 텍스트에도 포함** (YT는 `snippet.tags`에도 중복) |
| YT description | 위 블록 + `#Shorts` |

---

## 6. 어드민 UX

- 잡 상세: **인라인 mp4 재생** + 썸네일 + 사용 댓글 최대 3(§4.5) + (paired) 예정 첫 댓글
- `READY && !autoPublish` → **게시 승인** 시에만 업로드
- **플랫폼 계정 → YouTube Shorts**: 본문 `tts_voice` + 댓글 풀 `comment_tts_voices`(최대 5, 콤마구분)를 미리듣고 저장. Shorts 렌더 시 `options.ttsVoice` / `options.commentVoices` → `variant_config`.
- **보이스 계약**: 본문·intro·클로징 = `tts_voice`. 댓글 = `comment_tts_voices` 풀에서 작성자별 랜덤(본문 보이스 제외 우선). 풀이 비면 pipeline `comment_voices` 또는 본문 보이스 폴백. 참조 샘플 없는 키는 쓰지 말 것.
- **클로징**: again_spring 고정 문구는 voice+text 키로 디스크 캐시·loudnorm 후 재사용 (전역 loudnorm이 끝을 눌러 작아지지 않게, 통합 낭독 경로에서는 전역 loudnorm 생략).
- **AV 동기**: 오디오 타임라인 고정. 화면만 `TTS_TEXT_LEAD_SEC=0.10`(100ms) 앞서 전환 — 텍스트가 아주 조금 먼저 보이고 바로 TTS.
- **Intro**: again_spring은 mood 스톡/회색 플레이스홀더를 쓰지 않는다. `metaphor_id` PNG가 있으면 표지로, 없으면 크림 빈화면+제목만.
- **TTS 음량**: 통합 낭독 wav를 장면 분할해 재사용. 댓글/클로징은 개별 loudnorm(`I=-16`) 후 concat.

---

## 7. 구현 순서

1. WaggleBot `POST/GET /api/external/jobs` (ingest→APPROVED, outro/videoGen per-job) — **완료**
2. ASM Waggle 파이프 (alone + **Reels+Shorts 듀얼·1회 렌더**) — **완료**
3. AS brief 보강 + **24h 인기 상위 3 / 일 3캡** 스케줄러 + 어드민 미리보기 — **완료**
4. `auto_publish` → YouTube 업로드(+ paired 첫 댓글) + Reels 게시 — **활성**

### 7.1 레이아웃 리디자인 (§4 — 반영 완료)

1. **본문 프레임**: 좌측 정렬·최대 3줄 + Tone L 팔레트/타이포 (WaggleBot `text_only`)
2. **댓글 프레임**: 최대 3개 + fade-in + 닉네임/medium blur/좋아요+상대시간/진영색 + AS brief `top_comments`(§4.5)
3. **아웃트로**: 마스코트 제거 + Tone L CTA
4. **AV lead**: `TTS_TEXT_LEAD_SEC=0.10` (job `#462` 확정)
