# 지원 플랫폼 및 콘텐츠 형식

## 미공개 초점

| 상태 | 플랫폼 |
|---|---|
| **활성** | `x_thread`, `instagram_feed`, `instagram_reels`, `youtube_shorts` (24h 분배 · **Phase 2** 플랫폼별 점수·cap · Reels/Shorts **별도 잡**) |
| **보류 (deferred)** | `naver_blog`, `naver_clip`, `threads` |

에이전트·신규 작업은 활성 채널만. 보류 채널은 사용자 명시 요청 전 구현·디버그·배포 금지.

> **제품 프레임**: 광장 사연 + 공감 투표·댓글 + AI-user 시딩. **AI 배심원 없음**. SNS는 사람 사연 큐레이션 톤. AI/합성 고지는 **2027-01**까지 없음.
> **Phase 2.1–2.2 (현행 분배)**: 플랫폼별 popularity + 일일 cap(기본 각 3). 같은 날 멀티 플랫폼 중복 허용. IG feed ⊥ Reels만 배타. Reels/Shorts는 **별도 잡**(유니크 렌더는 2.4).

### 플랫폼 자동 on/off (관리자)

- API: `GET /api/admin/marketing/platforms` · `PUT /api/admin/marketing/platforms/{platform}/auto` (`AdminMarketingPlatformController`)
- 저장: `system_setting` 키 `marketing.platform.{id}.auto_enabled`
- **전체 플랫폼 표시** (준비중 배지 없음). 관리자 자유 on/off — 미구현도 on 가능
- 런타임 지원 상수 (`MarketingPlatformAutoService.RUNTIME_SUPPORTED`): `x_thread`, `instagram_feed`, `instagram_reels`, `youtube_shorts`
- 기본값: 지원 채널 ON / 미지원 OFF. 미지원+on 저장은 200 + `warning`; 발행 시 `resolveTargets(format, enabled)` = enabled ∩ supported (미지원 스킵·로그). VIDEO force일 때 릴스 포함 시 `instagram_feed` 제외

### 24h 대기 보드 (`AdminMarketingHoldingController` · `marketing_holding`)

T+24h 전 사연을 seed·순위 스냅샷하는 **대기 보드** (`GET /api/admin/marketing/holding`, 최대 20행 + meta).

| API | 동작 |
|---|---|
| `GET …/holding` | 보드 + meta. 컷라인 N = `remainingPool - softReservedPool`(핀 soft-reserve; remainingPool은 플랫폼 잔여 **합**) |
| `PATCH …/holding/{postId}/draft` | `draft_json` 교체. `locked_at != null` → 400 |
| `POST …/holding/{postId}/pin` | Body `{format: VIDEO\|TEXT}`. 핀 + soft-reserve |
| `DELETE …/holding/{postId}/pin` | 핀 해제·예약 반환 → `IN_POOL` 또는 `OUT_OF_CUT` |

상태: `IN_POOL` \| `PINNED` \| `OUT_OF_CUT` \| `COMMITTED` \| `DROPPED`.

### 인기 점수 가중치 (Phase 2)

커밋 선정 점수(플랫폼별):

```
score = wHook*hook + wVoteSkew*vote_skew + wComments*comments
      + wVotes*votes + wViews*views + wHasPartner*has_partner
```

| 신호 | 의미 |
|---|---|
| `views` | 조회 |
| `comments` | 탑레벨 댓글 수 |
| `votes` | 투표 수 |
| `vote_skew` | \|author% − 50\| / 50 |
| `has_partner` | paired면 1 |
| `hook` | 훅 강도 0–1 (필드 없으면 0.5 · `promo_title` 길이로 소폭 보정) |

기본 가중치(plan §3): Reels hook/skew 강조 · Shorts skew/votes · X comments/partner · IG feed hook/paired.  
키: `marketing.score.weights.{platform}.{hook\|vote_skew\|comments\|votes\|views\|has_partner}`  
API: `GET`/`PUT /api/admin/marketing/score-weights` — 응답 `platforms` 맵.  
**Deprecated**: flat `marketing.score.weight_views|comments|votes` — 대기 보드 미리보기 정렬용만.

### 24h 자동 분배 (`XThreadPublishTriggerScheduler` → `MarketingHoldingCommitService`)

사연 `created_at` 기준 **+24h** 후 (`createdAt >= ASM_AUTO_PUBLISH_SINCE`), **홀딩 확정 파이프라인 (Phase 2)**:

| 단계 | 동작 |
|---|---|
| 핀(PINNED) | soft-reserve 우선 COMMITTED. 잔여 부족 시 **가능한 플랫폼만** 커밋 · 전부 없으면 PINNED 유지 |
| 자동 | 플랫폼별 점수 DESC → 각 플랫폼 잔여 cap까지 독립 선정. **같은 사연 멀티 플랫폼 허용** |
| IG 배타 | 같은 날 `instagram_feed` ∩ `instagram_reels` 금지. `score_feed` vs `score_reels`, 동점 → Reels. 탈락 슬롯은 다음 순위 backfill |
| 잡 | **플랫폼당 1잡** (Reels ≠ Shorts — 듀얼 mp4 폐기 준비) |
| 그 외 | T+24h 도달·미선정 → DROPPED. COMMITTED 시 초안 `locked_at` |

### 커밋 ≠ 실발행 (Phase 1 계약 유지)

T+24h 커밋 = **선정·잡 생성**만. SNS 노출은 KST **저녁 슬롯** (`MarketingPublishSlotService`).  
잡 생성 시 `scheduledPublishAt` = 해당 플랫폼 슬롯의 **다음 발생** (Asia/Seoul; 오늘 슬롯이 지났으면 내일). ASM에는 `auto_publish=false`로 보내 READY 대기 → `MarketingPollingScheduler`가 슬롯 시각에 `triggerPublish`.

| 단계 | 의미 |
|---|---|
| **커밋 (T+24h)** | 홀딩 선정 → `COMMITTED` · 잡 enqueue · 아티팩트 빌드. **이 시각 ≠ SNS 노출** |
| **실발행** | KST 저녁 슬롯에 ASM publish |

| 채널 | 기본 슬롯 (KST) |
|---|---|
| `instagram_feed` | 20:00 |
| `instagram_reels` · `youtube_shorts` | 20:30 |
| `x_thread` | 21:30 |

설정: `system_setting` `marketing.publish_slot.{platform}=HH:mm` · API `GET`/`PUT /api/admin/marketing/publish-slots`. 댓글 노티 창 기본 24h: `marketing.comment_notify_hours`.

| 포맷 | 타겟 (`resolveTargets` — force/수동) |
|---|---|
| VIDEO | 영상 채널(on∩supported) + 글 채널. **릴스 포함 시 `instagram_feed` 제외** |
| TEXT | 글 채널만 |

**강제(완료 탭)**: `POST /api/admin/marketing/completed/{postId}/force` — 상한 무시 (`VIDEO_AND_TEXT` \| `TEXT_ONLY`). 이미 잡이 있는 플랫폼은 건너뜀.  
목록: `GET /api/admin/marketing/completed?status=&limit=50`.

### 일일 cap (Phase 2)

| 키 | 기본 | 비고 |
|---|---|---|
| `marketing.cap.x_thread` | 3 | |
| `marketing.cap.instagram_feed` | 3 | |
| `marketing.cap.instagram_reels` | 3 | |
| `marketing.cap.youtube_shorts` | 3 | |

API: `GET`/`PUT /api/admin/marketing/quota` — Body 플랫폼 필드(`xThread` 등) 또는 legacy `dailyTextCap`+`dailyVideoCap`(분배 저장).  
응답 `platforms.{id}.{cap,usedToday,remaining}` + 파생 `dailyTextCap`/`dailyVideoCap`(합).

**Legacy fallback**: 플랫폼 키가 없으면 `marketing.daily_text_cap` / `marketing.daily_video_cap`으로 보정(텍스트=⌊text/2⌋씩, 영상=video cap 각각). 시드·신규는 V109 플랫폼 키 사용. 커밋 선정은 **플랫폼 cap만** 소비.

### Phase 2 잔여 (2.3+)

| 계약 | 내용 |
|---|---|
| 영상 변형 | Reels≤30s / Shorts≤45s · **유니크 mp4** · 전문 낭독 폐기 · 변형 훅(2.3–2.4) |
| 학습 | 통계 수집 · 주간 리포트 · 가중치 auto |

## 플랫폼 목록

다음 플랫폼 식별자(value)를 `targets` 배열에 사용합니다:

| 플랫폼 | value | 콘텐츠 형식 | M6 게시 방법 | 미공개 |
|---|---|---|---|---|
| X 4단 스레드 | `x_thread` | 텍스트 스레드 | Playwright (`x-thread-strategy.md`) | **활성 (24h · 독립 cap)** |
| 네이버 블로그 | `naver_blog` | 마크다운 → HTML | Playwright 자동 로그인 | 보류 |
| 인스타그램 피드 | `instagram_feed` | 하이브리드 캐러셀 (훅+캡처+비율) | Playwright (`instagram-feed-strategy.md`) | **활성 (24h · 독립 cap)** — **릴스와만 배타** |
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Meta Graph / 세션 | **활성 (24h · 독립 cap · 별도 잡)** |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | WaggleBot 렌더 → API 업로드 | **활성 (24h · 독립 cap · 별도 잡)** — [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) |
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
| `instagram_reels` | `video_mp4`, `thumbnail`, `customcover`(선택, 관리자 업로드) |
| `youtube_shorts` | `video_mp4`, `thumbnail`, `customcover`(선택, 관리자 업로드) |

---

## 영상 스펙 (Shorts / Reels)

| 항목 | YouTube Shorts | Instagram Reels |
|---|---|---|
| 해상도 | 1080×1920 (9:16) | 1080×1920 (9:16) |
| 분류(공식) | 정방 **또는** 세로 ≤3분 | 권장 9:16 (허용 1.91:1~9:16) |
| 코덱 | H.264 | H.264 |
| 프레임률 | ≥30fps | ≥30fps |
| 오디오 | TTS (Fish Speech) | 동일 |
| 잡/렌더 | **별도 잡** (Phase 2.1) · 유니크 mp4는 2.4 | **별도 잡** · ≤30s 계약은 2.4 |
| Phase 2.4 | ≤45s · 유니크 렌더 · 전문 낭독 폐기 | ≤30s · 유니크 렌더 · 전문 낭독 폐기 |
| 상세 | [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) | 同 |

---

## 텍스트 정책

AI가 생성하는 모든 텍스트에 적용:

- **금지어**: `판결`, `처방`, `승패`, `승자`, `패자` (공감·관점 표현 사용)
- **이모지 금지**: `policy.no_emoji = true`
- **중립성**: 작성자=A, 상대방=B 균형 유지
- 권위본: `docs/shared/policies/forbidden-words.md`

---

## 해시태그 전략 (Phase 1 계약)

**브랜드 태그 항상**: `#다시봄` **그리고** `#againspring` (전 활성 채널 공통).

| 플랫폼 | 태그 규칙 |
|---|---|
| `x_thread` | 브랜드 2개만 (`#다시봄` `#againspring`). **카테고리·니치 태그 없음** |
| `instagram_feed` | 브랜드 2 + `#공감비율` + `#[카테고리]` 등 **≤5** (상세: `instagram-feed-strategy.md`) |
| `instagram_reels` / `youtube_shorts` | `#Shorts`(YT) + 브랜드 2 + 니치(상한 준수). IG Reels ≤5, YT ≤15 |
| 네이버 블로그 (보류) | `#다시봄`, `#갈등사연`, `#[카테고리]`, `#[주요키워드]` |

홀딩 시드(`MarketingHoldingBriefSeeder`, IG feed용): `#다시봄` `#againspring` `#공감비율` `#[카테고리]` (≤5, 신규만·백필 없음).  
`platform_specs` clamp·ASM 빌더는 위 표와 통일. 구 X용 `#갈등` 단독·카테고리 세트는 **폐기**.
