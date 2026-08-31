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

T+24h 전 사연을 seed·순위 스냅샷하는 **대기 보드** (`GET /api/admin/marketing/holding`, 24h 미만 최대 20행 + T+24h 경과 미확정 `overdue` 행 + meta).

| API | 동작 |
|---|---|
| `GET …/holding` | 보드 + meta. 컷라인 N = `remainingPool - softReservedPool`. T+24h 경과·미확정은 `overdue=true`로 앞에 붙음 |
| `PATCH …/holding/{postId}/draft` | `draft_json` 교체. `locked_at != null` → 400 |
| `POST …/holding/{postId}/pin` | Body `{format: VIDEO\|TEXT}`. 핀 + soft-reserve |
| `DELETE …/holding/{postId}/pin` | 핀 해제·예약 반환 → `IN_POOL` 또는 `OUT_OF_CUT` |

상태: `IN_POOL` \| `PINNED` \| `OUT_OF_CUT` \| `COMMITTED` \| `DROPPED`.

### 콘텐츠 가드 — 갈등 사연이 아닌 글 제외 (`MarketingHoldingContentGuard`, 2026-08-29)

**배경**: X 노출 상위 5건 중 2건("덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기", "여초회사 1년
근무자가 쓰는 장단점")이 "A vs B 공감 투표"라는 서비스 핵심과 무관한 글이었다. 둘 다 AI-user가
원본 커뮤니티 글(`source_community`)의 문체를 흉내내 생성했는데, 원본 자체가 갈등 서사가
아니어서 결과물도 갈등 서사가 아니었다.

**DB 실측 결과 (2026-08-29)**: `posts.category`, 투표 옵션 구조("작성자 | 상대방"은 모든 글에
동일), `partner_body_published` 유무는 문제 글과 정상 글을 전혀 가르지 못했다 — 정상 사연도
파트너 답변 전에는 다수가 파트너 본문이 비어 있고, 문제 글도 category가 FRIEND/WORK로 정상
분류돼 있었다("기타" 온상 가설은 기각). 1인칭 대명사 존재 여부도 시도했으나 오탐이 너무 많았다.

실제로 정상 글과 문제 글을 가르는 것은 **본문 텍스트 자체의 장르**뿐이었다. `MarketingHoldingContentGuard`가
`findActiveCandidates()`가 반환한 후보(title + body_published)에 대해 두 시그널을 적용한다 —
prod DB 전수 조사에서 각각 정확히 그 문제 글 1건씩만 매칭하고 다른 글은 단 한 건도 걸리지 않음
(오탐 0건 확인):

| 사유 코드 | 판정 | 매칭 예 |
|---|---|---|
| `YEAR_TRIVIA_PATTERN` | 4자리 연도 표기(`\d{4}년`) — 역사/시사 트리비아 특징. 개인 갈등 사연은 "3년째"·"작년에" 같은 상대적 시점을 쓰지 사학적 연도를 인용하지 않음 | 덕혜옹주 글("1919년") |
| `PROS_CONS_LISTICLE` | "장점"과 "단점"이 함께 등장 — 항목별 리뷰/정리 글의 특징. 갈등 서사는 사건을 서술하지 장단점을 정리하지 않음 | 여초회사 글 |

**오탐 방지 설계**: 오탐(정상 사연을 잘못 거르는 것)이 미탐보다 비싸다(홀딩 풀이 마르면 발행이
멈춘다) — 그래서 의도적으로 좁게 설계했다. 걸린 후보는 홀딩 풀에 아예 올라가지 않고(랭킹에서
제외), `marketing_holding_exclusion`(**V121**, `post_id` PK) 테이블에 사유와 최초 감지 시각을
남긴다 — 조용히 사라지지 않고 나중에 SELECT로 오탐 검증 가능. 같은 사연이 24h 후보 창 안에서
매 스케줄러 tick마다 재평가되지만, `post_id`당 최초 1회만 기록·로그(`[holding-guard]` WARN)한다.
새 오염 패턴 발견 시 규칙을 추가하되 반드시 전체 DB에 대해 오탐 0건을 먼저 확인할 것.

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
| 자동 | 플랫폼별 점수 DESC → 각 플랫폼 잔여 cap까지 독립 선정. **같은 사연 멀티 플랫폼 허용**. 확정된 채널의 실제 1-based 순위는 `platform_rank_snapshot`에 JSON으로 잠금 |
| IG 배타 | 같은 날 `instagram_feed` ∩ `instagram_reels` 금지. `score_feed` vs `score_reels`, 동점 → Reels. 탈락 슬롯은 다음 순위 backfill |
| 잡 | **플랫폼당 1잡** (Reels ≠ Shorts — 듀얼 mp4 폐기 준비) |
| 그 외 | T+24h 도달·미선정 → DROPPED. **선정됐는데 잡 생성 실패 → due 유지(탈락 없음)·다음 틱 재시도**. COMMITTED 시 초안 `locked_at` |

### 커밋 후 즉시 발행

T+24h 커밋은 채널별 상위 사연을 선정하고 잡을 생성한다. 잡은 ASM에 `auto_publish=true`로 전달되며, 각 채널의 렌더가 READY가 되는 즉시 발행된다. Again-Spring에는 저녁 고정 슬롯이나 `scheduledPublishAt` 대기가 없다.

| 단계 | 의미 |
|---|---|
| **커밋 (T+24h)** | 홀딩 선정 → 사연 단위 TX로 잡 enqueue → `COMMITTED`. 잡 생성 실패 시 홀딩 유지·재시도 |
| **실발행** | 채널 렌더 READY 즉시 ASM이 발행 |

`scheduledPublishAt`은 관리자 수동 예약 등 명시적 예약에만 사용한다. 자동 선정에는 적용하지 않는다. 댓글 노티 창 기본 24h: `marketing.comment_notify_hours`.

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

**Legacy fallback**: 플랫폼 키가 없으면 `marketing.daily_text_cap` / `marketing.daily_video_cap`으로 보정(텍스트=⌊text/2⌋씩, 영상=video cap 각각). 시드·신규는 V109 플랫폼 키 사용.

**`usedToday` = 실제 발행 성공 건수 (2026-08-12~)**: `MarketingQuotaService.countPublishedByPlatformSince`가 오늘(KST) `marketing_job.publications` JSON을 잡별로 훑어 플랫폼별 `state=PUBLISHED` 항목만 센다(PARTIAL 잡은 성공한 타겟만 카운트). **커밋(홀딩 COMMITTED)·READY 대기·강제 배포·FAILED는 카운팅에서 제외** — READY 상태로 방치되거나 발행이 실패한 잡이 슬롯을 영구히 점유해 당일 나머지 업로드를 막던 버그 수정. 수동 "게시 승인" 성공 시에도 동일 로직으로 그 순간 카운팅된다.
> 이전 계약(v1, ~2026-08-11): 커밋 선정(홀딩 COMMITTED) 시점에 플랫폼 cap 소비 — deprecated, `MarketingHoldingRepository.countCommittedForPlatformSince`는 레거시로 남아있으나 quota 계산에 더 이상 쓰이지 않음.

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
| 인스타그램 릴스 | `instagram_reels` | 세로형 영상 (9:16) | Meta Graph만 (Playwright 업로드 금지) | **활성 (24h · 독립 cap · 별도 잡)** |
| YouTube Shorts | `youtube_shorts` | 세로형 영상 (9:16) | WaggleBot 렌더 → API 업로드 | **활성 (24h · 독립 cap · 별도 잡)** — [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) |
| 네이버 클립 | `naver_clip` | 세로형 영상 (9:16) | Playwright (미구현) | 보류 |
| Threads | `threads` | 텍스트 + 이미지 | Playwright 자동 로그인 (인스타 계정 상속) | 보류 |

> **게시 계정 자격증명**: 어드민 `/admin/marketing` → **설정** 탭(플랫폼 auto + 계정). ASM이 암호화 저장.
> 필드 스키마·암호화: [`credentials.md`](../40-data/credentials.md). X 로그인 세션은 ASM credential PK `x`(UI 라벨 「X 4단 스레드」)로 유지.

---

## 플랫폼별 아티팩트

| 플랫폼 | 필요 아티팩트 |
|---|---|
| `x_thread` | (스레드 스텝 이미지·upload.json) · Playwright |
| `naver_blog` | `blog_md` (마크다운), `images[]` (인용 이미지) |
| `instagram_feed` | `images[]` (훅 4:5 + X캡처 원본비율 + 비율카드 4:5, 4~5장) · caption |
| `instagram_reels` | `video_mp4`, `thumbnail`(인트로), `customcover`(선택, 관리자 업로드) |
| `youtube_shorts` | `video_mp4`, `thumbnail`(인트로·`thumbnails.set` 필수), `customcover`(선택, 관리자 업로드·우선) |

---

## 영상 스펙 (Shorts / Reels)

| 항목 | YouTube Shorts | Instagram Reels |
|---|---|---|
| 해상도 | 1080×1920 (9:16) | 1080×1920 (9:16) |
| 분류(공식) | 정방 **또는** 세로 ≤3분 | 권장 9:16 (허용 1.91:1~9:16) |
| 코덱 | H.264 | H.264 |
| 프레임률 | ≥30fps | ≥30fps |
| 오디오 — 나레이션 | TTS (Fish Speech S2-pro) · 어드민「숏폼영상」 설정의 `tts_voice` | 동일 |
| 오디오 — 배경음악 | 감정 5종(shock·anger·tension·sad·hype) × 2곡, Mixkit · 어드민 UI에서 곡 직접 선택 또는「자동 선택」(빈 값) · 나레이션보다 약 14 dB 하향 · **현재 off** (`settings.yaml` `bgm.enabled=false`) | 동일 |
| 오디오 — 효과음 | 17개 지점 매핑 가능, 262개 음원 라이브러리(Mixkit 12개 카테고리) · 어드민「설정 → 효과음 매핑」에서 지점별 음원·음량·오프셋 지정 · `assets/media/sfx/LICENSES.md` 출처 기록 | 동일 |
| 잡/렌더 | **별도 잡** (Phase 2.1) · 유니크 mp4는 2.4 | **별도 잡** · ≤30s 계약은 2.4 |
| Phase 2.4 | ≤45s · 유니크 렌더 · 전문 낭독 폐기 | ≤30s · 유니크 렌더 · 전문 낭독 폐기 |
| 시봄이 | `sibom_plan` intro+본문 · 메타포 **금지** · [`sibom-video-insertion.md`](sibom-video-insertion.md) | 同 |
| 상세 | [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) | 同 |

---

## 텍스트 정책

AI가 생성하는 모든 텍스트에 적용:

- **금지어**: `판결`, `처방`, `승패`, `승자`, `패자` (공감·관점 표현 사용)
- **이모지 금지**: `policy.no_emoji = true`
- **중립성**: 작성자=A, 상대방=B 균형 유지
- 권위본: `docs/shared/70-policy/forbidden-words.md`

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
