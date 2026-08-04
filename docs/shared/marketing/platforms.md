# 지원 플랫폼 및 콘텐츠 형식

## 미공개 초점

| 상태 | 플랫폼 |
|---|---|
| **활성** | `x`, `x_thread`, `instagram_feed`, `youtube_shorts` (렌더+검수; **자동 게시 없음**) |
| **보류 (deferred)** | `naver_blog`, `instagram_reels`, `naver_clip`, `threads` |

에이전트·신규 작업은 활성 채널만. 보류 채널은 사용자 명시 요청 전 구현·디버그·배포 금지.
`instagram_feed`는 [`instagram-feed-strategy.md`](instagram-feed-strategy.md) — **게시 후 24h 자동 one-shot**
(+ 관리자 단건 수동). X와 동일 스케줄러(`XThreadPublishTriggerScheduler`).

## 플랫폼 목록

다음 플랫폼 식별자(value)를 `targets` 배열에 사용합니다:

| 플랫폼 | value | 콘텐츠 형식 | M6 게시 방법 | 미공개 |
|---|---|---|---|---|
| X (트위터) | `x` | 텍스트 + 이미지 | Playwright 자동 로그인 | **활성** |
| X 4단 스레드 | `x_thread` | 텍스트 스레드 | Playwright (`x-thread-strategy.md`) | **활성** |
| 네이버 블로그 | `naver_blog` | 마크다운 → HTML | Playwright 자동 로그인 | 보류 |
| 인스타그램 피드 | `instagram_feed` | 하이브리드 캐러셀 (훅+캡처+비율) | Playwright (`instagram-feed-strategy.md`) | **활성 (24h 자동)** |
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Playwright 자동 로그인 | 보류 |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | WaggleBot 렌더 → API 업로드(수동 승인) | **활성 (렌더)** — [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) |
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

| 항목 | YouTube Shorts | Instagram Reels (예정) |
|---|---|---|
| 해상도 | 1080×1920 (9:16) | 1080×1920 (9:16) |
| 분류(공식) | 정방 **또는** 세로 ≤3분 | 권장 9:16 (허용 1.91:1~9:16) |
| 코덱 | H.264 | H.264 |
| 프레임률 | ≥30fps | ≥30fps |
| 오디오 | TTS (Fish Speech) | 동일 |
| 렌더 | WaggleBot (LTX off) | 동일 복제 예정 |
| 상세 | [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) | — |

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
