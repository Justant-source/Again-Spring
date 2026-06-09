# 지원 플랫폼 및 콘텐츠 형식

## 현재 지원 플랫폼 (M0 스텁)

다음 플랫폼 식별자(value)를 `targets` 배열에 사용합니다:

| 플랫폼 | value | 콘텐츠 형식 | M6 게시 방법 |
|---|---|---|---|
| 네이버 블로그 | `naver_blog` | 마크다운 → HTML | Playwright 자동 로그인 |
| X (트위터) | `x` | 텍스트 + 이미지 | Playwright 자동 로그인 |
| 인스타그램 피드 | `instagram_feed` | 이미지 + 캡션 | Playwright 자동 로그인 |
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Playwright 자동 로그인 |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | API (미구현) |
| 네이버 클립 | `naver_clip` | 세로형 영상 (9:16) | Playwright (미구현) |
| Threads | `threads` | 텍스트 + 이미지 | API (미구현) |

---

## 플랫폼별 아티팩트

| 플랫폼 | 필요 아티팩트 |
|---|---|
| `naver_blog` | `blog_md` (마크다운), `images[]` (인용 이미지) |
| `x` | `images[0]` (카드 이미지), 텍스트 |
| `instagram_feed` | `images[]` (카드뉴스 1~10장) |
| `instagram_reels` | `video_mp4`, `thumbnail` |
| `youtube_shorts` | `video_mp4`, `thumbnail` |

---

## 영상 스펙 (M3~M4)

| 항목 | 값 |
|---|---|
| 해상도 | 1080×1920 (9:16) |
| 코덱 | H.264 (FFmpeg NVENC) |
| 프레임률 | 30fps |
| 오디오 | TTS (Fish Speech) AAC 44.1kHz |
| 최대 길이 | 60초 (YouTube Shorts 기준) |

---

## 텍스트 정책

AI가 생성하는 모든 텍스트에 적용:

- **금지어**: `판결`, `처방`, `승패`, `승자`, `패자` (공감·관점 표현 사용)
- **이모지 금지**: `policy.no_emoji = true`
- **중립성**: 작성자=A, 상대방=B 균형 유지
- 권위본: `shared/docs/policies/forbidden-words.md`

---

## 해시태그 전략

플랫폼별 기본 해시태그 (M1 카피라이팅 단계에서 추가):

- 네이버 블로그: `#다시봄`, `#AI배심원`, `#[카테고리]`, `#[주요키워드]`
- X / 인스타그램: `#againspring`, `#AI배심원`, `#갈등`, `#[카테고리]`
