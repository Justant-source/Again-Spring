# 지원 플랫폼 및 콘텐츠 형식

## 미공개 초점

| 상태 | 플랫폼 |
|---|---|
| **활성** | `x`, `x_thread`, `instagram_feed`, `instagram_reels` + `youtube_shorts` (24h 분배 · 동일 영상 듀얼) |
| **보류 (deferred)** | `naver_blog`, `naver_clip`, `threads` |

에이전트·신규 작업은 활성 채널만. 보류 채널은 사용자 명시 요청 전 구현·디버그·배포 금지.

### 24h 자동 분배 (`XThreadPublishTriggerScheduler`)

사연 `created_at` 기준 **+24h** 후 (`createdAt >= ASM_AUTO_PUBLISH_SINCE`), **공유 일일 풀** 안에서만 자동 게시:

| 채널 | 대상 | 동작 |
|---|---|---|
| `instagram_reels` + `youtube_shorts` | 인기 상위 · **영상 상한** 내 (기본 3) | **한 잡·한 번 렌더** → 동일 mp4를 릴스·쇼츠에 `autoPublish=true` · **X 없음** |
| `x_thread` + `instagram_feed` | 잔여 풀 · 인기 순 (글 슬롯) | 각 alone 잡 · `autoPublish=true` |

**공유 풀**: `dailyTextCap`(기본 6) = KST 하루 마케팅 사연 총 상한. 영상이 먼저 소비 → 글 슬롯 = `dailyTextCap − videosToday`. 예: 영상 2 → 글 4.  
**상한 저장**: `system_setting` 키 `marketing.daily_text_cap` / `marketing.daily_video_cap`. 관리자 `/admin/marketing → 일일 상한` 또는 `GET|PUT /api/admin/marketing/quota`. 수동 잡도 같은 날 카운트 포함.  
**인기 점수**: `view_count` DESC → 최상위 댓글 수 → 투표 수 → `created_at` 최신.  
**상호배타**: 같은 사연에 릴스/쇼츠 ↔ (X+피드) 동시 금지. ASM은 `instagram_reels`+`youtube_shorts` 듀얼만 허용(다른 타겟과 혼합 금지).

## 플랫폼 목록

다음 플랫폼 식별자(value)를 `targets` 배열에 사용합니다:

| 플랫폼 | value | 콘텐츠 형식 | M6 게시 방법 | 미공개 |
|---|---|---|---|---|
| X (트위터) | `x` | 텍스트 + 이미지 | Playwright 자동 로그인 | **활성** |
| X 4단 스레드 | `x_thread` | 텍스트 스레드 | Playwright (`x-thread-strategy.md`) | **활성 (24h · 글 슬롯)** |
| 네이버 블로그 | `naver_blog` | 마크다운 → HTML | Playwright 자동 로그인 | 보류 |
| 인스타그램 피드 | `instagram_feed` | 하이브리드 캐러셀 (훅+캡처+비율) | Playwright (`instagram-feed-strategy.md`) | **활성 (24h · 글 슬롯)** |
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Meta Graph / 세션 · 캡션=제목+사연URL+해시태그 | **활성 (24h · 영상 슬롯 · Shorts 듀얼)** — 글 슬롯과 상호배타 · Graph 자격 권장 |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | WaggleBot 렌더 → API 업로드 | **활성 (24h · 영상 슬롯 · Reels 듀얼)** — [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) |
| 네이버 클립 | `naver_clip` | 세로형 영상 (9:16) | Playwright (미구현) | 보류 |
| Threads | `threads` | 텍스트 + 이미지 | Playwright 자동 로그인 (인스타 계정 상속) | 보류 |

> **게시 계정 자격증명**: 각 플랫폼의 로그인/API 계정 정보는 어드민 `/admin/marketing → 플랫폼 계정`
> 탭에서 입력하며 ASM이 암호화 저장한다. 플랫폼별 필드 스키마·암호화 정책은 [`credentials.md`](credentials.md) 참조.
> 미공개 기간에는 **X 계정만** 시딩·유지한다.

---

## 플랫폼별 아티팩트

| 플랫폼 | 필요 아티팩트 |
|---|---|
| `naver_blog` | `blog_md` (마크다운), `images[]` (인용 이미지) |
| `x` | `images[0]` (카드 이미지), 텍스트 |
| `instagram_feed` | `images[]` (훅 4:5 + X캡처 원본비율 + 비율카드 4:5, 4~5장) · caption |
| `instagram_reels` | `video_mp4`, `thumbnail` |
| `youtube_shorts` | `video_mp4`, `thumbnail` |

---

## 영상 스펙 (Shorts / Reels)

| 항목 | YouTube Shorts | Instagram Reels |
|---|---|---|
| 해상도 | 1080×1920 (9:16) | 1080×1920 (9:16) |
| 분류(공식) | 정방 **또는** 세로 ≤3분 | 권장 9:16 (허용 1.91:1~9:16) |
| 코덱 | H.264 | H.264 |
| 프레임률 | ≥30fps | ≥30fps |
| 오디오 | TTS (Fish Speech) | 동일 |
| 렌더 | WaggleBot (LTX off) | **동일 mp4** (듀얼 타겟 잡 1회 렌더) |
| 상세 | [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) | 同 |

---

## 텍스트 정책

AI가 생성하는 모든 텍스트에 적용:

- **금지어**: `판결`, `처방`, `승패`, `승자`, `패자` (공감·관점 표현 사용)
- **이모지 금지**: `policy.no_emoji = true`
- **중립성**: 작성자=A, 상대방=B 균형 유지
- 권위본: `docs/shared/policies/forbidden-words.md`

---

## 해시태그 전략

플랫폼별 기본 해시태그 (M1 카피라이팅 단계에서 추가):

- 네이버 블로그: `#다시봄`, `#AI배심원`, `#[카테고리]`, `#[주요키워드]`
- X: `#againspring`, `#AI배심원`, `#갈등`, `#[카테고리]`
- 인스타그램 피드: `#다시봄`, `#공감비율`, `#[카테고리]` (상세: `instagram-feed-strategy.md`)
