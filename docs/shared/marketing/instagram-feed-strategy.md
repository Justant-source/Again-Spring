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

| IG | 짧은 사연 (X 3단) | 긴 사연 (X 4단) |
|---|---|---|
| 1 | 훅 카드 (4:5) | 훅 카드 (4:5) |
| 2 | `storyPart1` → 4:5 패딩 캔버스 | `storyPart1` → 4:5 패딩 캔버스 |
| 3 | `comments` → 4:5 패딩 캔버스 | `storyPart2` → 4:5 패딩 캔버스 |
| 4 | 비율 디자인 카드 (4:5) | `comments` → 4:5 패딩 캔버스 |
| 5 | — | 비율 디자인 카드 (4:5) |

실측 장수 = **4 또는 5**. 6장은 댓글 캡처가 늘어날 때 예약.

중간 장(X 캡처)은 원본을 그대로 올리지 않고 `#FAF7F4` 4:5 캔버스 중앙에 두고 **상하좌우 여백**을 둔다.
(인스타 그리드 1:1·피드 4:5 크롭에서 글자가 잘리지 않게.)

### 2.2 훅 카드

- 크기: `1080×1350` (4:5)
- 내용: **홍보 제목(`promoTitle`)만**. 로고·배지·부가문구 없음
- 여백: 가로·세로 넉넉히 (1:1 썸네일 세이프존 안쪽에 제목)
- 배경: 카테고리 → 5색 매핑

| `PostCategory` | 색 | Hex | 글자색 |
|---|---|---|---|
| COUPLE | 피치 | `#C9785A` | `#1A1A1A` |
| MARRIED | 세이지 | `#5F8F76` | `#1A1A1A` |
| FAMILY | 슬레이트 블루 | `#2C4A6E` | `#FFFFFF` |
| WORK | 차콜 | `#222222` | `#FFFFFF` |
| FRIEND | 머스타드 | `#C9A227` | `#1A1A1A` |
| OTHER | 머스타드 | `#C9A227` | `#1A1A1A` |

### 2.3 공감비율 피니시 카드

- 크기: `1080×1350` (4:5)
- 내용:
  - 상단 라벨: 왼쪽 `작성자` / 오른쪽 `상대방`
  - 막대 + `A% : B%` 숫자
  - 하단 가운데: `어느쪽에 더 공감하세요?`
- 진영색: 작성자 `#C9785A` / 상대방 `#5F8F76`
- 판결·승패·처방·로고 금지 (유도 문구는 위 CTA만 허용; 링크 CTA는 캡션 URL)

### 2.4 캡션 (발행 시 LLM 없음)

```
{promoTitle}
https://againspring.net/community/{postId}

당신은 어느 쪽에 공감하나요?

#다시봄 #공감비율 #[카테고리한글]
```

- 1행 = `promoTitle` (없으면 원제 20자 폴백)
- 2행 = 사연 URL
- 3행 공백 후 고정 유도 문구
- 해시태그 3~5개, `#다시봄` 필수. `platform_specs` hashtag_cap으로 clamp

### 2.5 `promoTitle` (AS SSOT)

- 컬럼: `posts.promo_title` `VARCHAR(20)` nullable
- **모든 사연** 생성 시 1회 LLM 생성·저장 (사람·AI 不分). 발행 파이프에서 추가 LLM 호출 없음
- 규칙: ≤20자, 질문·긴장형, 판결/처방/승패 금지
- 폴백: 비어 있으면 `title`/`userTitle`를 20자로 자름
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
