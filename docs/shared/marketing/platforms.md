# 지원 플랫폼 및 콘텐츠 형식

## 미공개 초점

| 상태 | 플랫폼 |
|---|---|
| **활성** | `x_thread`, `instagram_feed`, `instagram_reels` + `youtube_shorts` (24h 분배 · 동일 영상 듀얼) |
| **보류 (deferred)** | `naver_blog`, `naver_clip`, `threads` |

에이전트·신규 작업은 활성 채널만. 보류 채널은 사용자 명시 요청 전 구현·디버그·배포 금지.

### 플랫폼 자동 on/off (관리자)

- API: `GET /api/admin/marketing/platforms` · `PUT /api/admin/marketing/platforms/{platform}/auto` (`AdminMarketingPlatformController`)
- 저장: `system_setting` 키 `marketing.platform.{id}.auto_enabled`
- **전체 플랫폼 표시** (준비중 배지 없음). 관리자 자유 on/off — 미구현도 on 가능
- 런타임 지원 상수 (`MarketingPlatformAutoService.RUNTIME_SUPPORTED`): `x_thread`, `instagram_feed`, `instagram_reels`, `youtube_shorts`
- 기본값: 지원 채널 ON / 미지원 OFF. 미지원+on 저장은 200 + `warning`; 발행 시 `resolveTargets(format, enabled)` = enabled ∩ supported (미지원 스킵·로그). VIDEO일 때 릴스 포함 시 `instagram_feed` 제외

### 24h 대기 보드 (`AdminMarketingHoldingController` · `marketing_holding`)

T+24h 전 사연을 seed·순위 스냅샷하는 **대기 보드** (`GET /api/admin/marketing/holding`, 최대 20행 + meta).

| API | 동작 |
|---|---|
| `GET …/holding` | 보드 + meta. 컷라인 N = `remainingPool - softReservedPool`(핀 soft-reserve) |
| `PATCH …/holding/{postId}/draft` | `draft_json` 교체. `locked_at != null` → 400 |
| `POST …/holding/{postId}/pin` | Body `{format: VIDEO\|TEXT}`. 핀 + soft-reserve. 풀/영상 슬롯 부족·전부 핀 점유 시 400. 컷라인 축소 시 최하위 비핀 → `OUT_OF_CUT` |
| `DELETE …/holding/{postId}/pin` | 핀 해제·예약 반환 → `IN_POOL` 또는 `OUT_OF_CUT` |

상태: `IN_POOL` \| `PINNED` \| `OUT_OF_CUT` \| `COMMITTED` \| `DROPPED`.

### 인기 점수 가중치

`score = wViews*views + wComments*top_level_comments + wVotes*votes` DESC, tie-break `created_at` DESC.  
기본 `0.1` / `1.0` / `0.5`.  
API: `GET`/`PUT /api/admin/marketing/score-weights` · 키 `marketing.score.weight_views` / `weight_comments` / `weight_votes` (각 0–100).

### 24h 자동 분배 (`XThreadPublishTriggerScheduler` → `MarketingHoldingCommitService`)

사연 `created_at` 기준 **+24h** 후 (`createdAt >= ASM_AUTO_PUBLISH_SINCE`), **홀딩 확정 파이프라인(배분 C)**:

| 단계 | 동작 |
|---|---|
| 핀(PINNED) | soft-reserve 우선 COMMITTED. 잔여 부족 시 PINNED 유지(다음 틱) |
| 자동 | 점수 DESC → 잔여 **영상** 슬롯 → **글** 슬롯. **1사연 = 공유 풀 1칸** |
| 그 외 | T+24h 도달·미선정 → DROPPED. COMMITTED 시 초안 `locked_at` |

| 포맷 | 타겟 (`resolveTargets`) |
|---|---|
| VIDEO | 영상 채널(on∩supported) + 글 채널(**video+text companion**). **릴스 포함 시 `instagram_feed` 제외**(IG feed⊥reels). X 등 동반 가능 |
| TEXT | 글 채널만 |
| 영상 채널 전원 off | `effectiveVideoCap=0`, 잔여 풀 전부 글 |

잡 그룹: Reels+Shorts는 **듀얼 1잡**, `x_thread`/`instagram_feed` 등은 **alone**.  
**강제(완료 탭)**: `POST /api/admin/marketing/completed/{postId}/force` — 상한 무시 (`VIDEO_AND_TEXT` \| `TEXT_ONLY`). 주로 `DROPPED` 재진입.  
목록: `GET /api/admin/marketing/completed?status=&limit=50`.  
**공유 풀**: `dailyTextCap`(기본 6) = KST 하루 **마케팅 사연 수**. 영상 상한 `dailyVideoCap`. 멀티 플랫폼 잡은 추가 칸 아님.  
**상한 저장**: `system_setting` 키 `marketing.daily_text_cap` / `marketing.daily_video_cap` · API `GET`/`PUT /api/admin/marketing/quota`.

## 플랫폼 목록

다음 플랫폼 식별자(value)를 `targets` 배열에 사용합니다:

| 플랫폼 | value | 콘텐츠 형식 | M6 게시 방법 | 미공개 |
|---|---|---|---|---|
| X 4단 스레드 | `x_thread` | 텍스트 스레드 | Playwright (`x-thread-strategy.md`) | **활성 (24h · 글 슬롯)** |
| 네이버 블로그 | `naver_blog` | 마크다운 → HTML | Playwright 자동 로그인 | 보류 |
| 인스타그램 피드 | `instagram_feed` | 하이브리드 캐러셀 (훅+캡처+비율) | Playwright (`instagram-feed-strategy.md`) | **활성 (24h · 글 슬롯)** |
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Meta Graph / 세션 · 캡션=제목+사연URL+해시태그 | **활성 (24h · 영상)** — **피드와만 배타**(릴스 포함 시 feed 스킵). X 동반 가능 · Graph 자격 권장 |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | WaggleBot 렌더 → API 업로드 | **활성 (24h · 영상 슬롯 · Reels 듀얼)** — [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) |
| 네이버 클립 | `naver_clip` | 세로형 영상 (9:16) | Playwright (미구현) | 보류 |
| Threads | `threads` | 텍스트 + 이미지 | Playwright 자동 로그인 (인스타 계정 상속) | 보류 |

> **게시 계정 자격증명**: 어드민 `/admin/marketing` → **설정** 탭(플랫폼 auto + 계정). ASM이 암호화 저장.
> 필드 스키마·암호화: [`credentials.md`](credentials.md). X 로그인 세션은 ASM credential PK `x`(UI 라벨 「X 4단 스레드」)로 유지.

---

## 플랫폼별 아티팩트

| 플랫폼 | 필요 아티팩트 |
|---|---|
| `x_thread` | (스레드 스텝 이미지·upload.json) · Playwright |
| `naver_blog` | `blog_md` (마크다운), `images[]` (인용 이미지) |
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
