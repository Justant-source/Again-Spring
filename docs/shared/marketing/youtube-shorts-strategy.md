# YouTube Shorts 전략 — Again Spring × WaggleBot

> **권위본**: 이 문서. `youtube_shorts` 채널의 생성·검수·게시 계약을 담는다.
> 플랫폼 표는 [`platforms.md`](platforms.md), 캡처/피드는 [`instagram-feed-strategy.md`](instagram-feed-strategy.md) · [`x-thread-strategy.md`](x-thread-strategy.md).
> **작성**: 2026-08-04 · 그릴링 확정

---

## 1. 채널 상태

| 항목 | 값 |
|---|---|
| 자동 생성 | **활성 (조건부)** — `x_thread` 또는 `instagram_feed`가 사연당 최초 `PUBLISHED`될 때 Shorts 렌더 잡 1회 |
| 자동 게시 | **비활성** — 관리자 마케팅 탭에서 mp4 검수 후 수동 승인 시에만 업로드 |
| 렌더 | WaggleBot 블랙박스 (`POST /api/external/jobs`) — LLM은 **Claude CLI 브릿지** (`llm_backend=cli`, 호스트 `~/.claude` = Again Spring과 동일 세션). Anthropic API 직접 호출 금지. |
| 게시 계정 | 다시봄 전용 YouTube (ASM `youtube_shorts` OAuth) |

`instagram_reels`는 동일 9:16·복제 예정. 1차는 Shorts만.

---

## 2. 경계

```
AS 트리거/brief → ASM 잡(youtube_shorts alone) → WaggleBot ingest+render
  → ASM 아티팩트(mp4/thumbnail/upload.json) → READY
  → (수동 승인) ASM YouTube API 업로드 + paired 시 첫 댓글(상대 사연)
```

- ASM `app/media` 로컬 GPU 파이프는 Shorts에 사용하지 않는다.
- WaggleBot 크롤 채널·자체 YT 업로드는 사용하지 않는다.

---

## 3. 콘텐츠 스펙

| 항목 | 값 |
|---|---|
| 해상도 | **1080×1920 (9:16)** |
| LTX / ComfyUI | **off** (`videoGen: false`) |
| 프레임 | Waggle 기존 `text_only` / `comments` / `outro` 재활용 |
| 본문 | 작성자 사연 **거의 전문** 낭독 (원문 유지 + 청킹·금지어만) |
| 댓글 | 광장 **좋아요 순 상위 2** — 화자별 TTS. **&lt; 2개면 잡 미생성** |
| 양면(paired) | 영상에는 작성자만. 클로징·첫 댓글만 상대 처리 |
| 클로징 TTS | 솔로: `여러분의 의견을 댓글로 남겨주세요` / paired: `상대방의 사연이 궁금하면 댓글을 확인해주세요` |
| 썸네일 | Waggle 산출 + YT 권장 16:9 커버는 후속 가능 |

### paired 첫 댓글 (게시 승인 시)

- 본문 = 상대방 사연 전문 (한도 초과 시 절단 + 광장 URL)
- **1차: 채널 첫 댓글만** (YouTube Data API에 pin 없음 → pin UI는 후속)

---

## 4. AS brief 필드 (Shorts)

| 필드 | 설명 |
|---|---|
| `title` / `promo_title` | 훅·제목 |
| `metaphor_id` | 사연 생성 시 매칭된 메타포 일러스트 ID (60종). Shorts intro에 사용. 없으면 크림 빈화면 |
| `side_a` 또는 `author_body` | 작성자 본문 **전문** |
| `partner_body` | paired일 때만 상대 본문 **전문** |
| `top_comments` | `{ author?, body, likeCount }[]` **최대 2, body 전문** |
| `paired` | boolean |
| `post_url` | 광장 URL |

---

## 5. 어드민 UX

- 잡 상세: **인라인 mp4 재생** + 썸네일 + 사용 댓글 2 + (paired) 예정 첫 댓글
- `READY && !autoPublish` → **게시 승인** 시에만 업로드
- **플랫폼 계정 → YouTube Shorts / Instagram 릴스**: WaggleBot TTS 음성 목록을 미리듣고 `tts_voice`로 저장 (빈값=파이프 기본). Shorts 렌더 시 brief·WaggleBot `options.ttsVoice` → `contents.variant_config.tts_voice`에 주입.
- **단일 보이스 계약 (Again Spring)**: 사연·댓글·outro 전 구간이 어드민이 고른 음성 하나만 사용한다. WaggleBot은 `variant_config.tts_voice`를 SSOT로 읽고(컬럼이 pipeline 기본값으로 덮여도 복구), `again_spring` 잡에서는 `comment_voices` 다중 배정을 끈다. 참조 샘플이 없는 키(예: `yohan`)로 떨어지면 Fish Speech가 청크마다 불안정한 기본 음색을 써서 “여러 사람 목소리”처럼 들릴 수 있다.
- **Intro**: again_spring은 mood 스톡/회색 플레이스홀더를 쓰지 않는다. `metaphor_id` PNG가 있으면 표지로, 없으면 크림 빈화면+제목만.
- **TTS 음량**: 청크별 loudnorm 후 **병합 파일에 한 번 더** global loudnorm (`I=-16`) 적용해 씬 간 볼륨 점프를 줄인다.

---

## 6. 구현 순서

1. WaggleBot `POST/GET /api/external/jobs` (ingest→APPROVED, outro/videoGen per-job)
2. ASM `youtube_shorts` alone 파이프 + Waggle 클라이언트
3. AS brief 보강 + PUBLISHED 트리거(멱등) + 어드민 미리보기
4. 수동 승인 → YouTube 업로드 + paired 첫 댓글
