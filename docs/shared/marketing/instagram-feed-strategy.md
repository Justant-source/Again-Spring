# 인스타그램 피드 마케팅 전략 — 하이브리드 캐러셀

> **권위본**: 이 문서. `instagram_feed` 채널의 콘텐츠 포맷·캡션·구현 위치 결정을 담는다.
> 플랫폼 일반 사양은 [`platforms.md`](platforms.md), X 캡처 소스는 [`x-thread-strategy.md`](x-thread-strategy.md),
> 발행기 운영은 [`social-poster.md`](social-poster.md) 참조.
> **작성**: 2026-08-02 · 레퍼런스 `@issue_archive` / `@salonbleuciel` / `@knowing_sister` + 그릴링 확정

---

## 1. 채널 상태

| 항목 | 값 |
|---|---|
| 자동/스케줄 활성 | **활성** — `createdAt >= ASM_AUTO_PUBLISH_SINCE` 이고 `+24h` 후, **공유 풀 글 슬롯**에 선정된 사연만 (`XThreadPublishTriggerScheduler`) |
| 파이프 구현 | **허용** (하이브리드 캐러셀) |
| 실발행 | 자동(24h · 글 슬롯) + 관리자 단건 수동 |

컷오프 이후 생성된 사연은 공개 24시간 뒤 **공유 일일 풀** 안에서 분배된다: **영상 우선(릴스+쇼츠, X 없음)** · **잔여 글 슬롯 → X+피드**.  
피드는 영상 잡이 없는 글 슬롯 사연만. X와 동일 틱에서 함께 enqueue. ASM alone 제약으로 **별도 잡**. 상한·배분 상세는 [`platforms.md`](platforms.md).

---

## 2. 확정 사양 — 하이브리드 캐러셀

레퍼런스 패턴: **1장 자극 제목(색 배경)** → **사연 캡처** → **댓글 캡처** → (피니시).
다시봄은 피니시를 **공감비율 디자인 카드**로 둔다. 중간 장은 X 스레드 캡처를 재사용한다.

### 2.1 장수·순서

X `/capture/x-thread` 산출물 기준 (X의 `ratio` 스크린샷은 **폐기**).

| IG | 솔로 짧은 | 솔로 긴(N장) | 양면 |
|---|---|---|---|
| 1 | 훅 카드 (4:5) | 훅 카드 | 훅 카드 |
| 2+ | `storyPart1..N` | `storyPart1..N` | `storyPart*` then `partnerPart*` |
| … | `comments`(높이 버짓, ≤4) | `comments` | `comments` |
| 끝 | 비율 디자인 카드 | 비율 카드 | 비율 카드 |

실측 장수 = 훅 + 본문(솔로≤4 / 양면 합≤6) + 댓글 + 비율. IG 캐러셀 상한(10) 안.

### 2.1.1 중간 장 프레이밍 (사연 텍스트 · 정방 안전영역)

- 훅·중간·비율 **모두 4:5 (1080×1350)** — IG 캐러셀이 첫 장 비율을 따르므로 중간만 1:1이면 레터박스가 다시 생김.
- 사연 분홍 박스는 캔버스 **중앙 1:1 (y=135..1215)** 안에 `contain` 배치: **가로·세로 중 먼저 닿는 쪽까지** 확대.
- **`cover` 금지** — 분홍 박스 안 글자 절대 절단 없음. 박스 바깥 초록 마진 비율 0.
- 정방 밖(상·하 135px)만 tone-L `#EDF1E8` 띠. 프로필/탐색 1:1 크롭 시 분홍이 프레임에 맞닿음.

### 2.1.2 댓글 장 — 최소 글자 크기 (높이·라인 버짓, **IG 전용**)

- 댓글도 사연과 같이 4:5 중앙 1:1에 `contain` (cover 금지). 캡처가 길수록 글자가 작아진다.
- **허용 하한** = 짧은 댓글 4장이 들어간 기준 피드(실측 crop ≈526 CSS @430 → scale ≈1.42). 그보다 작아지면 안 됨.
- `instagram_feed` 캡처만 `commentsReadableBudget=true` — 광장 상위부터 누적 높이 ≤ `MAX_COMMENTS_CROP_HEIGHT_CSS`(530)인 최대 N(1~4). 긴 댓글이면 3·2·1장.
- **X(`x_thread`)는 미적용** — 기존처럼 최대 4장 고정·길게 유지.
- N은 줄 수(soft wrap) 반영 높이로 결정 — “무조건 상위 3개”가 아니라 버짓에 맞는 만큼.

### 2.2 훅 카드

- 크기: `1080×1350` (4:5)
- 내용: **마스터 훅** (`hook_text` 또는 재정의된 `promo_title`). 광장 `title`과 **완전 분리** — 원제 낭독·복제 금지. 로고·배지·부가문구 없음. 훅 없으면 `title` 폴백(레거시)
- 타이포: 줄 수에 따라 큰 글씨 (`Noto Sans KR`). `white-space: pre-line`. IG 카드용 줄바꿈 패킹(`hook_packed` 또는 발행 시 패킹)
- 한 줄 **목표 4~10자**(최대 10). **1음절 단독 줄 금지** — 어절을 모아 의미 구로 패킹
- 중간 장(사연): 4:5 출력 + 중앙 1:1에 분홍 박스 contain(가로·세로 중 먼저 닿을 때까지). 글자 크롭 금지

| `PostCategory` | 톤 | Hex | 글자색 |
|---|---|---|---|
| COUPLE | 핑크 파스텔 (plaza `#E0879A`) | `#F7D0DB` | `#2E3A2E` (`--L-ink`) |
| MARRIED | 코랄 피치 (plaza `#D67E5E`) | `#F4D0BC` | `#2E3A2E` |
| FRIEND | 머스타드 옐로 (plaza `#D6A646`) | `#F8E08C` | `#2E3A2E` |
| FAMILY | 잎 그린 (plaza `#B39A56` → 녹색 쪽) | `#C9DDB8` | `#2E3A2E` |
| WORK | 파우더 블루 (plaza `#6E90B8`) | `#C8D6EC` | `#2E3A2E` |
| OTHER | 세이지 민트 (plaza `#7BA68E`) | `#C9DDD4` | `#2E3A2E` |

> 대분류는 제품 기준 **6개**(연인·부부·친구·가족·직장·기타). 크림끼리 겹치지 않게 hue를 벌린다.

### 2.3 공감비율 피니시 카드

- 크기: `1080×1350` (4:5)
- 내용:
  - 상단 라벨: 왼쪽 `작성자` / 오른쪽 `상대방`
  - 막대 + `A% : B%` 숫자 (**실투표 %** — Phase 1에서 50:50 폴백 버그 수정)
  - 하단 가운데: `어느쪽에 더 공감하세요?` (**CTA만 ~1.5배**, 60px)
- 진영색: 작성자 `#C9785A` / 상대방 `#5F8F76`
- 판결·승패·처방·로고 금지 (유도 문구는 위 CTA만 허용)
- **링크 CTA는 캡션에 raw URL을 넣지 않음** — 유입은 프로필(±스토리). 사연 단위 UTM은 X/YT 등

### 2.4 캡션 (발행 시 LLM 없음 · Phase 1 계약)

```
{hook flattened}

당신은 어느 쪽에 공감하나요?

#다시봄 #againspring #공감비율 #[카테고리한글]
```

- 1행 = 마스터 훅에서 **개행→공백** 한 줄 (없으면 `title` 폴백)
- **raw URL 금지** — `https://againspring.net/community/{postId}` 를 캡션에 넣지 않음. 링크 = 프로필(±스토리)
- 공백 후 고정 유도 문구
- 해시태그: 브랜드 2(`#다시봄` `#againspring`) + `#공감비율` + `#[카테고리]` 등 **≤5**. `platform_specs` hashtag_cap으로 clamp

### 2.5 마스터 훅 (`promoTitle` / `hook*` · AS SSOT)

- 컬럼: `posts.promo_title` 또는 `hook_text` (+ `hook_emotion`, optional `hook_packed`). 스키마는 구현 PR에서 Flyway와 맞춤
- **광장 `title` ≠ SNS 마스터 훅** (완전 분리)
- **모든 사연** 생성 시 1회: PLAN/LLM이 **자극 훅** 생성. 구 「원제 복제·재작성 금지」 규칙 **폐기**
- 패킹: 의미 구 `\n`, 각 줄 목표 4~10자(최대 10). 1음절 단독 줄 금지
- `/`·`／` 구분자는 금지하며, 저장·발행 단계에서 공백으로 정규화한다. 기존 훅에도 발행 시 적용한다.
- `hook_emotion` enum (Phase 1 필드): `shock` \| `anger` \| `tension` \| `sad` \| `hype` — brief에 전달(영상 TTS 연결은 Phase 2)
- 폴백: 훅 없을 때만 `title` / 어절 패킹 휴리스틱
- 기존 글 배치 백필 LLM 없음
- 발행 파이프 추가 LLM 없음
---

## 3. 구현 위치

| 계층 | 위치 |
|---|---|
| 전략 문서 | 이 파일 |
| AS 필드·생성 | `Post` 훅 필드(`promoTitle`/`hook*`) · `PromoTitleService` 또는 동등 · compose/PLAN |
| ASM 빌더 | `app/worker/pipeline.py` → `_run_instagram_feed_pipeline` |
| 캡처 소스 | ASM `services/social-poster` `POST /capture/x-thread` (X와 공유) |
| 발행 | ASM social-poster `POST /publish/instagram` (단건 수동) |

구 7장 카드뉴스(`COVER→SCENE→SIDE_A…`)는 `instagram_feed`에서 **사용하지 않는다**.

---

## 4. 단건 검증 루프

자동 스케줄·대량 타겟 활성화는 하지 않는다. 사용자 요청 시:

1. ASM job `targets=["instagram_feed"]` 1건 생성·빌드
2. 아티팩트(훅·캡처·비율·캡션) 확인
3. 단건 publish
4. 인스타 앱에서 확인 → 피드백 반영

---

## 5. 비범위 / 관련

- Threads
- 대량 자동 발행·스케줄러 변경 (피드 24h 자동은 유지)
- 기존 사연 홍보 제목 배치 백필
- X 캡처 파이프 재발명
- prod 배포 (별도 명시 전)

### 영상(릴스/쇼츠)과의 상호배타 (2026-08-06)

같은 사연에 `instagram_reels` 또는 `youtube_shorts` 잡이 한 번이라도 있으면 `instagram_feed` 자동 발행 대상에서 제외한다.
24h 분배에서 **영상 슬롯**으로 선정되면 피드는 만들지 않는다 (`NOT EXISTS` reels/shorts). 글 슬롯만 X+피드를 만든다.

---
