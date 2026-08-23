# YouTube Shorts 전략 — Again Spring × WaggleBot

> **권위본**: 이 문서. `youtube_shorts` / `instagram_reels` 영상 채널의 생성·검수·게시 계약.
> 플랫폼 표·분배는 [`platforms.md`](platforms.md), 캡처/피드는 [`instagram-feed-strategy.md`](instagram-feed-strategy.md) · [`x-thread-strategy.md`](x-thread-strategy.md).
> **시봄이 삽입(인트로·본문·brief)**: [`sibom-video-insertion.md`](sibom-video-insertion.md) — **메타포 일러스트는 영상 경로에서 사용 금지**. 본문 레이아웃 SSOT는 그 문서 §6 (job `#669`).
> **작성**: 2026-08-04 · **Phase 2 타깃 SSOT**: 2026-08-11 · **시봄이 삽입**: 2026-08-12 · **본문 레이아웃 복구**: 2026-08-16

---

## 1. 채널 상태

| 항목 | 값 |
|---|---|
| 자동 생성 | **활성** — 사연 `+24h` 후 **채널별 popularity·cap**으로 독립 선정 (`MarketingHoldingCommitService`) |
| 자동 게시 | **활성** — READY 즉시 YT API / Reels Graph 게시 (저녁 슬롯 없음) |
| 렌더 | WaggleBot (`POST /api/external/jobs`) — LLM은 **Claude CLI 브릿지** (`llm_backend=cli`). **채널별 유니크 렌더** (동일 mp4 공유 금지) |
| 게시 계정 | 다시봄 전용 YouTube (ASM `youtube_shorts` OAuth) · IG Reels Graph (`instagram_reels` 토큰). Graph 25/2207050이면 scraping_warning 닫기 후 Graph 재시도 |

`instagram_reels`와 `youtube_shorts`는 **독립 선정**. 같은 사연이 양쪽에 가도 **레이아웃·대사·mp4가 다름**.  
피드(`instagram_feed`)와 Reels는 **상호배타** (`score_feed` vs `score_reels`, 동점→Reels) — [`platforms.md`](platforms.md).  
구(舊) “X/피드 PUBLISHED 후 Shorts만 렌더” 트리거·**1회 렌더 공유 듀얼**은 Phase 2에서 **폐기**.

> 런타임 전환 중일 수 있음. **문서·신규 코드는 본 Phase 2 계약이 SSOT**.

---

## 2. 경계

```
AS 24h 분배 (채널별 score·cap)
  → Reels 확정 시: 변형 훅·스크립트 → ASM 잡(targets=[instagram_reels]) → WaggleBot 유니크 렌더 ≤30s
  → Shorts 확정 시: 변형 훅·스크립트 → ASM 잡(targets=[youtube_shorts]) → WaggleBot 유니크 렌더 ≤45s
  → 아티팩트: 플랫폼별 upload.json + **각자** video/thumbnail → READY
  → READY 즉시 publish: YouTube API / Instagram Reels
```

- **로컬 mp4 보존**: 게시 성공 직후 ASM 디스크의 `*__video.mp4`를 지우지 않는다. **게시 시각(`publication.updated_at`)부터 30일** 지난 뒤에만 바이트를 삭제한다(아티팩트 DB row·게시 URL은 유지). 미게시(`NEEDS_AUTH`/`FAILED`) 영상은 재게시용으로 남긴다. 썸네일·`upload.json`은 이 정책 밖. 구현: ASM `app/worker/video_retention.py` (시간당 스윕). `VIDEO_RETENTION_DAYS`(기본 30).
- ASM `app/media` 로컬 GPU 파이프는 Shorts에 사용하지 않는다.
- WaggleBot 크롤 채널·자체 YT 업로드는 사용하지 않는다.
- alone / 양 채널 동시 선정 모두 **렌더는 분리**.

---

## 3. 콘텐츠 스펙 — Phase 2 SSOT

| 항목 | 값 |
|---|---|
| 해상도 | **1080×1920 (9:16)** |
| 길이 | **Reels ≤30s** · **Shorts ≤45s** |
| LTX / ComfyUI | **off** (`videoGen: false`) |
| 프레임 | Waggle `text_only`(시봄이 없는 줄, 화면당 ≤3) · `image_text`(시봄이 비트 = 1절+캐릭터) · `comments` · `outro` |
| 본문 비트 | **자극 훅 → 요약 → 사연의 여운**. 공감비율 확인·댓글 작성·의견 요청 CTA 금지. **전문 낭독 금지** |
| 훅 | 사연 생성 = 마스터 훅+`hook_emotion` / **영상 슬롯 확정 시** = `hook_reels` 또는 `hook_shorts` + `script_*` |
| 감정 → TTS | `hook_emotion` → ASM→WaggleBot `options.mood`/`ttsEmotion` (Fish Speech markers; plan S2 Pro path) |
| 댓글 | 광장 **좋아요 순 상위 2**(§4.3/§4.5) — 화자별 TTS |
| 양면(paired) | 영상에는 작성자 중심. 클로징·첫 댓글만 상대 처리 |
| 클로징 TTS | 참여 유도 문구 없이 종료. 사연 본문에 없는 공감비율·댓글 확인 문구를 추가하지 않음. |
| 시봄이 | 인트로+본문. 예산 Reels 4~5 / Shorts 5~7 (인트로 포함). **메타포 금지**. 상세 [`sibom-video-insertion.md`](sibom-video-insertion.md) |
| 썸네일 | **`sibom_plan` `role=intro` 합성 PNG**(없으면 크림+훅) → 발행 시 `thumbnails.set` 필수. 폴백: mp4 frame0 추출. Shorts 선반은 oar 자동프레임(≠frame0)이라 API 등록 없으면 본문 씬이 노출됨 |

### paired 첫 댓글 (게시 승인 시)

- 대상 = 신규 `youtube_shorts` 양면 사연 중 공개된 `partner_body`가 있는 경우만. 본문은
  `partner_body_published`를 라벨·요약 없이 그대로 사용한다.
- 한도 초과 시 문장/줄바꿈 경계에서 절단하고 `전체 사연: {광장 URL}`을 덧붙인다.
- `commentThreads.insert` 성공 결과는 `publications[].partner_comment`
  (`state`, `comment_id`, `url`, `truncated`, `error`)에 기록한다. 댓글 실패는 영상 게시를
  되돌리지 않으며 운영 알림에서 수동 조치를 안내한다.
- YouTube Data API에는 pin 메서드가 없으므로, 성공 알림의 댓글 링크를 열어 YouTube Studio에서
  운영자가 수동으로 고정한다.

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

권위본은 [`sibom-video-insertion.md`](sibom-video-insertion.md) **§6**. 요약:

- **좌측 정렬** Tone L. NotoSerifKR-Medium 52px. 가용 폭 900px (좌표 x=90 기준, 1080px 캔버스).
- 시봄이가 **없는** 연속 절만 화면당 **최대 3블록** (`text_only`). 4번째부터 새 화면.
- 줄 나누기: 마침표·절(`는데`/`지만` 등)만. **구어체 종결어미도 문장 경계로 인정** (마침표 무용지물 방지). 글자 수 상한 40자로 안전장치. `smart_split_korean(..., max_chars=20)` · 22자 창 · 조사 `가/를/을` 절단 **금지**.
- 짧은 꼬리(24자 미만) 자동 흡수 — "전부였는데"(5자) 같은 줄이 독립 화면을 차지하지 않도록 함.
- 줄바꿈 계산은 **배치될 씬 폰트·폭으로** (계산상·실제 렌더 폰트 불일치 방지).
- 시봄이 비트는 별도 `image_text` 카드: **그 한 절** + 캐릭터(PNG 상황 캡션). TTS는 그 절. 무음 컷 아님. 3줄 화면 우하단에 스티커로 상주시키지 않음.
- `beat_index`는 3줄로 묶기 **전** 줄 인덱스.
- 폰트는 세리프(`--font-serif`), 색은 `--L-ink`.

### 4.3 댓글 프레임

- 노출 **최대 3개** (기존 2개에서 확대).
- **fade-in 스택** — 댓글이 순서대로 하나씩 페이드인하며 쌓인다 (한 번에 다 뿌리지 않음).
- **실제 닉네임** 노출 (익명 처리 아님) + **medium blur** 배경 처리.
- **좋아요 수 + 상대 시간**(예: `3일 전`)을 닉네임과 함께 표시.
- 진영색 적용 — 작성자 댓글은 `--faction-author`, 상대방 댓글은 `--faction-partner`, 그 외 일반 유저는 중립(잉크색).

### 4.4 아웃트로

- **필수(mandatory)**: tail 순서 `comments` → `outro`. 본문 duration trim·`marketing_fast` 경로에서도 생략 불가.
- 고정 문구(예): `여러분은 어떻게 생각하세요? 댓글로 알려주세요` — WaggleBot `variant_config.outro_text` + Tone L 레이아웃.
- **마스코트 없음**.
- CTA는 **Tone L** 팔레트로 통일 (배경 `--L-bg`/`--L-card`, 텍스트 `--L-ink`, 포인트만 `--L-point`).
- 본문 TTS 32/47초 상한·MP4 trim 대상에서 **제외**. AS는 `generation_diagnostics.outro_duration_ms <= 0`이면 `LAYOUT_OUTRO_MISSING`으로 READY 승격을 거부한다.

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

## 5. AS brief 필드 (Shorts / Reels)

| 필드 | 설명 |
|---|---|
| `title` | 광장 제목 (훅과 불일치 허용) |
| `promo_title` / `hook_text` | **마스터 훅** (사연 생성 시) |
| `hook_emotion` | `shock`\|`anger`\|`tension`\|`sad`\|`hype` → **TTS S2 Pro** |
| `hook_reels` / `hook_shorts` | **영상 슬롯 확정 시** 변형 훅 (`VideoVariantService`). 채널당 LLM 1회(+보정 최대 1). 회로 open이면 호출 없음. `/`·`／` 구분자는 공백으로 정규화. [llm-call-budget.md](../../ai-user/llm-call-budget.md) §3 |
| `script_reels` / `script_shorts` | 요약 낭독 대본. 끝에 공감비율·댓글·의견 요청 CTA를 추가하지 않음. |
| `max_duration_reels_sec` / `max_duration_shorts_sec` | 본문 목표 30 / 45초 (듀얼·분리 잡). 본문 TTS는 각각 32 / 47초를 넘으면 실패한다. 댓글 2개·아웃트로는 본문 길이 판정에서 제외한다. |
| `max_duration_sec` | alone 잡의 활성 캡 (30 또는 45) |
| `sibom_candidates` | string[] ≤12. 사연 생성 후 코드 keyword shortlist (`posts.sibom_candidates`) |
| `sibom_plan` | 채널별 삽입 플랜 배열 (`role`/`image_id`/`caption`/`beat_index`/`size`/`dwell`). 인트로·피크·펀치. 상세 [`sibom-video-insertion.md`](sibom-video-insertion.md) |
| `metaphor_id` / `metaphor_ids` | **영상 렌더에서 무시**. DB 컬럼 보존만. 신규 선택·주입 중지 |
| `side_a` / `author_body` | 폴백 본문. 렌더 body는 `script_*` 우선 |
| `partner_body` | paired일 때 상대 — 클로징/첫 댓글용 |
| `top_comments` | `{ author, author_id?, body, like_count, created_at, side }[]` 최대 2 (§4.5) |
| `paired` | boolean |
| `post_url` | 광장 URL + UTM (`utm_source=youtube`\|`instagram` 등) — Phase 1 유지 |

### 5.1 게시 카피

`upload.json` + 게시 본문 계약 (`ASM app/publishers/video_copy.py`):

| 항목 | 규칙 |
|---|---|
| 제목 | 변형 훅(`hook_reels`/`hook_shorts`) → 마스터 훅 → `title` 폴백. `/`·`／` 구분자는 금지이며 `VideoVariantService`가 공백으로 정규화한다(TTS가 "슬래시"로 읽는 것 방지). |
| 링크 | 광장 URL + **UTM** — 랜딩 = 사연 상세. IG Reels 캡션 URL 정책은 피드와 동일 계열(프로필 중심) 가능 |
| CTA | `당신은 어느 쪽에 공감하나요?` |
| 해시태그 | `#Shorts`(YT) + 브랜드 2(`#다시봄` `#againspring`) + 니치. IG ≤5, YT ≤15 |
| YT description | 위 블록 + `#Shorts` |

---

## 6. 어드민 UX

- 잡 상세: **인라인 mp4 재생** + 썸네일 + 사용 댓글 최대 3(§4.5) + (paired) 예정 첫 댓글
- `READY && !autoPublish` → **게시 승인** (미리보기). `autoPublish=true`면 READY 즉시 게시
- **설정 탭 → 숏폼영상**: 본문 `tts_voice` + 댓글 풀 `comment_tts_voices`(최대 5). Reels/Shorts가 **파일은 분리**해도 보이스 풀은 공유 가능
- **보이스 계약**: 본문·intro·클로징 = `tts_voice`. 댓글 = `comment_tts_voices` 풀에서 작성자별 랜덤. **감정** = `hook_emotion` → S2 Pro
- **클로징**: again_spring 고정 문구는 voice+text 키로 디스크 캐시·loudnorm 후 재사용
- **AV 동기**: 오디오 타임라인 고정. 화면만 `TTS_TEXT_LEAD_SEC=0.10`(100ms) 앞서 전환
- **Intro**: `sibom_plan` 중 `role=intro` 시봄이 합성 PNG가 있으면 표지, 없으면 크림+**훅**만. **메타포 PNG 사용 금지** (원제 낭독 intro 폐기)
- **TTS 음량**: 통합 낭독 분할 청크·장면별 TTS(본문/intro/댓글/클로징)·alignment 폴백 본문 모두 개별 2-pass loudnorm(`I=-16`) 후, 타깃 밴드(`-19…-14`) 밖이면 **양방향** volume gain+peak 리미터. 전역 loudnorm은 이중 적용을 피하기 위해 skip. (본문만 건너뛰던 경로에서 mid-video 볼륨 붕괴가 났음 — WaggleBot `10026251` / YT `_R0dV019OiI`)

---

## 7. 구현 순서

1. WaggleBot `POST/GET /api/external/jobs` — **완료**
2. ASM Waggle 파이프 — **유니크 듀얼 렌더**(플랫폼별 Waggle 호출) — **완료** (2.4)
3. AS brief + 24h 스케줄러 + 어드민 미리보기 — **완료** → Phase 2 **채널별 score·cap**(2.1)
4. `auto_publish` → READY 즉시 YouTube + Reels — **활성**
5. **Phase 1 유지**: UTM · 태그 · 마스터 훅 · `hook_emotion` 필드 · 텔레그램 댓글 노티
6. **Phase 2.3–2.5 완료**: `VideoVariantService` 변형 훅·`script_*` · ≤30/≤45s · 유니크 렌더 · `hook_emotion`→WaggleBot TTS mood
7. **잔여 Phase 2**: 통계·주간리포트·`auto_adjust`(2.6+)

### 7.1 레이아웃 리디자인 (§4 — 반영 완료)

1. **본문 프레임**: [`sibom-video-insertion.md`](sibom-video-insertion.md) §6. `text_only` ≤3(시봄이 없는 줄만) · 시봄이 = 1절 `image_text` 카드. 검증 job `#669` (2026-08-16 초기, 2026-08-23 정밀 조정 완료).
   - 구어체 종결어미 추가, 40자 글자 수 상한, 실제 폰트(52px)·가용폭(900px) 기준 계산.
   - 짧은 꼬리 흡수(24자), 줄바꿈 단계에서 의미 분리 재감싸기.
   - 20자/조사 wrap 금지, 마침표에만 의존 금지.
   - 실측: 본문 프레임 6장→17장, 한 장당 18초→2~3초, 오른쪽 여백 0px→151px, 길이 71.2초→56.4초.
2. **댓글 프레임**: 최대 3개 + fade-in + 닉네임/medium blur/좋아요+상대시간/진영색 + AS brief `top_comments`(§4.5). 본문 줄 수 4→9줄로 확대, 말줄임표 보완.
3. **인트로 프레임**: v2 전용 렌더 (앱 크롬 제거, 크림 배경). 시봄이 contain 배치(cover 아님) — 정사각 캐릭터 좌우 잘림 방지.
4. **아웃트로**: 마스코트 제거 + Tone L CTA
5. **AV lead**: `TTS_TEXT_LEAD_SEC=0.10` (job `#462` 확정)
