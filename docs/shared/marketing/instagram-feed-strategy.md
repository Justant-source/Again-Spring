# 인스타그램 피드 마케팅 전략 — 하이브리드 캐러셀

> **권위본**: 이 문서. `instagram_feed` 채널의 콘텐츠 포맷·캡션·구현 위치 결정을 담는다.
> 플랫폼 일반 사양은 [`platforms.md`](platforms.md), X 캡처 소스는 [`x-thread-strategy.md`](x-thread-strategy.md),
> 발행기 운영은 [`social-poster.md`](social-poster.md) 참조.
> **작성**: 2026-08-02 · 레퍼런스 `@issue_archive` / `@salonbleuciel` / `@knowing_sister` + 그릴링 확정

---

## 1. 채널 상태

| 항목 | 값 |
|---|---|
| 자동/스케줄 활성 | **활성** — `createdAt >= ASM_AUTO_PUBLISH_SINCE` 이고 `+24h` 후 one-shot (`XThreadPublishTriggerScheduler`) |
| 파이프 구현 | **허용** (하이브리드 캐러셀) |
| 실발행 | 자동(24h) + 관리자 단건 수동 |

컷오프 이후 생성된 사연만 공개 24시간 뒤 `instagram_feed` 잡을 1회 생성·`autoPublish`한다.
X(`x_thread`)와 동일 스케줄러·동일 게이트(댓글 수 조건 없음, `ASM_AUTO_PUBLISH_SINCE` 공유). ASM alone 제약으로 **별도 잡**.

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
| … | `comments`(≤4) | `comments` | `comments` |
| 끝 | 비율 디자인 카드 | 비율 카드 | 비율 카드 |

실측 장수 = 훅 + 본문(솔로≤4 / 양면 합≤6) + 댓글 + 비율. IG 캐러셀 상한(10) 안.
댓글 슬라이드만 IG 캔버스 패딩을 본문(40)보다 작게(≈20) 적용해 글자 체감을 키운다.

중간 장(X 캡처)은 원본을 `#FAF7F4` 4:5 캔버스에 두고 **여백 + `object-fit: contain`** 으로 올린다.
**사연 본문 UI는 절대 크롭하지 않는다** — 본문 카드 전체가 보여야 한다.

### 2.2 훅 카드

- 크기: `1080×1350` (4:5)
- 내용: **`promo_title` (원제 복제 + 의미단위 `\n`)**. 로고·배지·부가문구 없음. 없으면 `title` 폴백
- 타이포: 줄 수에 따라 큰 글씨 (`Noto Sans KR`). `white-space: pre-line`
- 한 줄 **목표 4~10자**(최대 10). **1음절 단독 줄 금지** — 어절을 모아 의미 구로 패킹
- 중간 장(사연/댓글 캡처): **전체 레이아웃 보존** (`object-fit: contain`). 분홍 본문 UI 크롭 금지. 여백 ~40px

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
  - 막대 + `A% : B%` 숫자
  - 하단 가운데: `어느쪽에 더 공감하세요?` (**CTA만 ~1.5배**, 60px)
- 진영색: 작성자 `#C9785A` / 상대방 `#5F8F76`
- 판결·승패·처방·로고 금지 (유도 문구는 위 CTA만 허용; 링크 CTA는 캡션 URL)

### 2.4 캡션 (발행 시 LLM 없음)

```
{promo_title flattened}
https://againspring.net/community/{postId}

당신은 어느 쪽에 공감하나요?

#다시봄 #공감비율 #[카테고리한글]
```

- 1행 = `promo_title`에서 **개행→공백** 한 줄 (없으면 `title`)
- 2행 = 사연 URL
- 3행 공백 후 고정 유도 문구
- 해시태그 3~5개, `#다시봄` 필수. `platform_specs` hashtag_cap으로 clamp

### 2.5 `promoTitle` (AS SSOT)

- 컬럼: `posts.promo_title` `VARCHAR(500)` nullable (**V96**, 개행 허용)
- **모든 사연** 생성 시 1회: AI PLAN `promo_title` 전달 또는 `PromoTitleService` LLM. 발행 파이프 추가 LLM 없음
- 규칙: **원제 글자 복제** + 의미 구 `\n`. 각 줄 목표 4~10자(최대 10). 1음절 단독 줄 금지. 재작성·생략 금지
- 폴백: 어절 패킹 휴리스틱(고아 1자 줄 병합)
- 기존 글 배치 백필 LLM 없음

---

## 3. 구현 위치

| 계층 | 위치 |
|---|---|
| 전략 문서 | 이 파일 |
| AS 필드·생성 | `Post.promoTitle`, `PromoTitleService`, compose/답변 파이프 |
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

## 5. 비범위

- `instagram_reels` / Threads
- 대량 자동 발행·스케줄러
- 기존 사연 홍보 제목 배치 백필
- X 캡처 파이프 재발명
- prod 배포 (별도 명시 전)

---
