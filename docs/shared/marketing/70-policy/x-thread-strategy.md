# X 스레드 마케팅 전략 — 4단 체인

> **권위본**: 이 문서. X(트위터) 채널의 콘텐츠 포맷·발행 트리거·구현 위치 결정을 담는다.
> 플랫폼 일반 사양은 [`platforms.md`](platforms.md), 발행기 운영은 [`social-poster.md`](../30-components.md) 참조.
> **작성**: 2026-07-31 · 레퍼런스 실측 30건 기반

---

## 1. 근거 — 레퍼런스 채널 실측

X는 비로그인 접근이 402로 차단되어, nitter 미러(`nitter.privacyredirect.com`)의 안티봇 챌린지를
헤드리스 브라우저로 통과해 수집했다. X 로그인 세션은 쓰지 않았다.
표본 = 계정당 좋아요 상위 10개 스레드.

| 계정 | 팔로워 | 자답글 진입률(중앙값) | 노출 중앙값 | 판정 |
|---|---|---|---|---|
| `@bingbingblind` (빙글빙글 블라인드) | 19,194 | **15.3%** | 93,540 | 채택 |
| `@nowyeosi` (지금 여성시대는…) | 48,734 | **15.5%** | 611,596 | 채택 |
| `@ceolmh3` (캣츠파파) | 15,155 | 1.9% | 1,152 | **제외** |

`@ceolmh3`는 커뮤니티 사연 채널이 아니라 테슬라·고양이·일상 개인 계정이다. 자답글도 전략이 아닌 잡담이며,
히트 1건(64만)을 빼면 도달이 사실상 없다.

### 1.1 포맷 — 본문 텍스트가 없다

- `@bingbingblind`: 자기 트윗 32건 중 **본문 텍스트가 있는 것 0건**, 이미지 30건
- `@nowyeosi`: 39건 중 텍스트 2건, 이미지 36건

이미지는 디자인된 카드가 아니라 **커뮤니티 앱 원본 스크린샷 그대로**다. 블라인드 앱 상단바,
여시 카페 조회수·댓글수까지 노출된다. 제작비가 0이라 5개월에 3,334트윗(일 22건)이 가능하다.

### 1.2 첫 댓글의 정체 — 두 종류

| 유형 | 내용 |
|---|---|
| 긴 사연 | 같은 스크린샷의 **스크롤 연속분**. 메인이 문장 중간에서 끊기고 첫 댓글이 그 다음 줄부터 시작한다. |
| 짧은 사연 | **원본 커뮤니티 댓글창 스크린샷**("댓글 57" 창). 여론이 갈리는 장면 자체. |

### 1.3 체인 유지율 — 4칸까지 안 빠진다

메인 노출을 100으로 놓았을 때 (고유 스레드 10건 기준):

| 칸 | 도달률 | 표본 |
|---|---|---|
| 첫 자답글 | 13.2% (범위 6.3–20.2%) | n=10 |
| 둘째 자답글 | 13.1% (범위 10.7–16.4%) | n=4 |
| 셋째 자답글 | 11.3% (범위 11.2–11.3%) | n=2 |
| 넷째 자답글 | 10.7% | n=1 |
| **다섯째 자답글** | **8.5% ← 여기서 처음 꺾인다** | n=1 |

관문은 첫 댓글 하나뿐이고, 그 뒤로는 거의 안 빠진다. 자답글 3칸은 안전 구간 안이다.
단 셋째·넷째 칸 근거는 표본이 얇다(자답글을 4칸 이상 다는 계정이 `@bingbingblind` 하나뿐).

---

## 2. 확정 사양 — 가변 체인 (솔로 3~4단 / 양면 최대 6단, 2026-08-04 갱신 · Phase 1 훅 계약)

메인 트윗 텍스트 = **마스터 훅** (`hook_text` / 재정의 `promo_title`). 광장 `title` 낭독·복제 아님 (Phase 1).
사연 링크는 **첫 답글**(`steps[1]`)에 붙인다(2026-08-29 갱신 — §2.3). 그 외 트윗(reply)은 텍스트 없이
이미지 단독. 이미지는 다시봄 실제 화면의 무보정 캡처.
해시태그(브랜드 2만): 메인 또는 체인 말미에 `#다시봄` `#againspring` — 카테고리 태그 없음 ([`platforms.md`](platforms.md)).
링크에는 UTM 부착 → 사연 상세 랜딩 (Phase 1 계측).

본문이 **8줄 이하**(`SHORT_POST_MAX_LINES`)면 나누지 않고, 그보다 길면 LLM/BE의 `capture_split_after_lines`로 **N장**(진영당 최대 4, 장당 ≤8 개행 블록)으로 나눈다.
**양면 사연**이면 작성자 본문 뒤에 상대방 본문도 같은 규칙으로 이어 붙이되 **작성자+상대 본문 합 ≤6장**. 예산 초과분은 마케팅 캡처에서만 뒤를 자른다(앱 본문 유지).
어느 쪽이든 각 잡의 `x_thread__upload.json`에 실제 스텝 목록(`steps`)이 기록되고, 발행측(`dispatcher.py`)은
이 목록을 그대로 따른다 — 문서의 표는 설명용이고 실제 동작의 SSOT는 코드다.

| 순서 | 솔로 짧은(≤8줄) | 솔로 긴(예: 3장) | 양면 (작성자+상대) | 소스 |
|---|---|---|---|---|
| 메인 | 작성자 본문(part1) | 작성자 part1 | 작성자 part1 | `/read?side=g` then `r` |
| **첫 답글** | **링크**(+ 이어지는 이미지) | **링크**(+ part2) | **링크**(+ part2) | — |
| … | 광장 댓글(≤4) | … | 광장 댓글(≤4) | `/community/{id}` |
| 마지막 | 공감 비율 | 공감 비율 | 공감 비율 | `/community/{id}` |

양면 최악 예: 작성자3 + 상대3 + 댓글 + 비율 (훅은 IG만). X는 비율이 마지막 답글, 링크는 첫 답글.

비율 막대가 들어가는 마지막 댓글에는 좋아요·댓글·공유 줄을 **넣지 않는다** — 시선이 숫자에서 분산된다.

### 2.3 링크 위치 = 첫 답글 (2026-08-29 실측 갱신)

**변경 전**: 링크(`link_text`)는 체인 마지막 스텝(`steps[-1]`, 보통 공감 비율 카드)에 있었다.

**실측 근거** — `@againspring_net`(팔로워 157) 스레드를 fxtwitter API로 조회수 측정:

| 스텝 | 조회수 | main 대비 |
|---|---|---|
| main | 97 | 100% |
| reply1(첫 답글) | 94 | 97% |
| reply2 | 3 | **3%** |

이탈이 reply2에서 일어나는데, 링크는 그보다 뒤(마지막 스텝)에 있어 사실상 아무도 보지 못했다.
최근 30일 95개 스레드 노출 합계 19,027회 대비 **사이트 유입 0**이었던 것과 정합적이다.

**변경**: `_publish_x_thread`(`app/publishers/dispatcher.py`)가 `link_text`를 `steps[1]`(첫 답글)에
넣도록 수정. 마지막 스텝(공감 비율 카드)은 이제 다른 중간 스텝과 동일하게 텍스트 없이 이미지만
올라간다 — 중간 스텝이 원래도 무캡션 이미지였던 것과 같은, 이미 검증된 동작이라 신규 리스크가
없다. 기대 효과: 링크 도달 3뷰 → 94뷰(최대 약 31배).

**경계 조건**:
- `len(steps) == 1`(답글이 아예 없는 이상 케이스): 링크를 잃지 않기 위해 메인 본문에 직접 붙인다.
- `len(steps) == 2`: `steps[1] == steps[-1]`이라 한 곳에서만 대입 — 중복 삽입 없음.

**참고**: 레거시 단일 트윗 플랫폼(`"x"`, `content_builders.py:140`)은 이미 `linkMode: "first_reply"`로
`services/social-poster/src/routes/publish-x.js`(`:563`)에서 같은 방식을 써왔다. `x_thread`만 여태
`steps[-1]`(last_tweet과 유사한 배치)에 남아 있었던 것 — 이번 변경으로 두 경로의 링크 배치 전략이
일치한다.

코드: `app/publishers/dispatcher.py` (`_publish_x_thread`) · 테스트: `tests/test_x_thread_publish.py`,
`tests/test_x_thread_link_placement.py` (ASM 리포).

### 2.1 캡처 사양

모바일 뷰포트 `430×932`, `deviceScaleFactor: 3`, 로케일 `ko-KR`.
캡처 전 상단 베타 배너(`베타 서비스 —`로 시작하고 높이 80px 미만인 요소)를 `display:none` 처리한다.

**동시성 (2026-08-10)**: 작성자 본문 · 상대방 본문 · 상세(댓글/비율) 캡처는 **각각 별도 Playwright browser context**에서 **직렬** 실행한다. 동일 context에서 `Promise.all` 병렬 + `deviceScaleFactor:3`이면 파트너 본문 JPEG가 가로로 같은 띠가 반복되는(세로 3등분) 깨짐이 간헐 재현됐다. 본문 JPEG는 가로 self-similarity 가드로 한 번 재시도한다. 가드는 **mid-band MAE + 같은 period의 full-frame MAE**가 둘 다 낮을 때만 타일로 판정한다(2026-08-14) — 짧은 중간 장(피치 여백)의 mid-only 오탐을 막기 위함.

**컷 지점 (2026-08-04~)** — 의미 단락(개행 블록) · 장당 최대 8:

1. 해당 본문의 **비어 있지 않은 개행 블록** 수가 `SHORT_POST_MAX_LINES`(8) 이하면 미분할(1장).
2. 그보다 길면 AS brief의 `capture_split_after_lines` / `partner_capture_split_after_lines`
   (1-based, 각 장 마지막 블록; 마지막 장 제외)에서 DOM으로 자른다.
3. PLAN/Call1·Call2 LLM이 배열을 고르고 `posts.capture_split_after_lines` /
   `partner_capture_split_after_lines`(JSON)에 저장. 없거나 범위 밖이면 BE 휴리스틱(8블록 청크).
4. 진영당 최대 4장, 양면 본문 합 ≤6. 초과 블록은 **마케팅 캡처만** 뒤 절단(`capture_block_count`).
5. BE `CaptureHeightCalculator.partHeightsCss`가 컷별 후보 Y를 brief에 실음.
6. 댓글 캡처는 **최대 4개** 고정.

양면 여부: brief `has_partner_story=true`이면 social-poster가 `/read?side=r`도 캡처해
`partnerPart1..N`을 반환하고, 파이프라인이 작성자 본문 뒤에 삽입한다.

공감 비율 막대는 좌우에 `RATIO_SIDE_PADDING_CSS`(32px, deviceScaleFactor 반영) 여백을 추가한 뒤
상하 패딩으로 `RATIO_SAFE_ASPECT`(1.91:1)를 맞춘다 — 여백이 없으면 "작성자"/"상대방" 라벨이
X 미리보기(축소 썸네일)에서 카드 가장자리에 붙어 잘려 보인다(2026-08-02 수정).

구현 위치: `services/social-poster/src/routes/capture-x-thread.js` (ASM 리포) ·
AS `CaptureHeightCalculator` / `MarketingJobService` brief 필드.

### 2.2 계정 포지션

**광장 큐레이션 톤.** 레퍼런스 3계정이 전부 3인칭 큐레이터인 것은 우연이 아니다
(`@bingbingblind` bio: "블라여론 퍼오는거고 내의견아님"). 타임라인에서 기업 계정으로 분류되면
리트윗이 확실히 덜 돈다. 다만 **bio에 다시봄 운영 계정임을 명시**한다 — 순수 큐레이터 위장은
링크가 전부 자사 도메인이라 금방 들통나고, 들통난 뒤 받는 타격이 크다.

### 2.4 X 운영 설정 (2026-08-30)

#### Justant-Bot

**Justant-Bot**은 운영자(Justant)의 말투를 쓰는 X 댓글 AI다. 선댓글·대댓글·아침/밤 인사 작문이 이 봇이다. 게시 계정은 `@againspring_net`. 트윗/댓글 **본문에는** Justant-Bot·AI·봇이라고 쓰지 않는다. 목소리 SSOT는 `marketing.x.persona_profile_json`. 프롬프트: `docs/shared/prompts/marketing/x-outbound-reply.md`. 이후 대화에서 「Justant-Bot」은 이 봇을 가리킨다. 광장 **AI-user**와는 별개다.

어드민 `/admin/marketing` **설정 → X 운영**. API `GET`/`PUT /api/admin/marketing/x-ops`.
저장 키 `marketing.x.*` (`system_setting`).

| 항목 | 기본 | 키 |
|---|---|---|
| 아침 시각 | 07:30 KST | `marketing.x.morning_time` |
| 밤 시각 | 22:00 KST | `marketing.x.night_time` |
| 사연 퍼오기 /일 | 2 | `marketing.x.story_scoops_per_day` |
| 선댓글 /일 | 20 | `marketing.x.outbound_daily_cap` |
| 우리 글 대댓글 /일 | 40 | `marketing.x.inbound_daily_cap` |
| 우리 글당 대댓글 | 12 | `marketing.x.inbound_per_post_cap` |
| 불 난 글 최소 댓글 | 3 | `marketing.x.hot_min_replies` |
| 불 난 글 최대 나이 | 6시간 | `marketing.x.hot_max_age_hours` |
| 아침/밤 글 · 대댓글 · 선댓글 | **off** | `marketing.x.{ritual,inbound,outbound}_enabled` |
| 페르소나 학습 | **on** · 04:30 KST | `marketing.x.persona_learning_enabled` · `persona_learn_at` |

매일 새벽 `personaLearnAt`에 `@againspring_net` 타임라인을 읽어 **운영자가 직접 남에게 단 댓글·인용 평**만 gold 코퍼스(`source=TIMELINE`)에 넣는다. Justant-Bot이 게시한 선댓글·대댓글(`x_ops_action.posted_tweet_id`, 최근 14일)은 타임라인에 남아 있어도 gold가 아니다. 최근 3일 POSTED 댓글이 타임라인에 없으면 운영자가 지운 것으로 보고 `DELETED_AUTO`(avoid)로 넣는다. 자동 `x_thread`(자기 체인·링크만·브랜드 해시태그 훅)도 제외. 프로필 JSON은 `marketing.x.persona_profile_json`. **dev는 예시만 적재**(L3, LLM 없음). prod는 **Sonnet**(`claude-sonnet-5`, `MARKETING_X_PERSONA_LEARN_MODEL`)으로 증류. 수동 실행 `POST /api/admin/marketing/x-ops/learn`. Telegram으로 페르소나를 학습시키지 않는다. 게시 알림용 Telegram은 그대로 둔다.

성장 루프 발행기는 위 플래그를 읽는다. 스위치 기본값은 꺼짐이라, 어드민에서 켜기 전에는 X에 글·댓글이 나가지 않는다. **prod가 자동 게시 중이라고 보지 않는다.**

- **inbound** (우리 글 대댓글): 하루 40, 글당 12(설정 기본값). 수신 후 **30분 창**, 지터 **3–25분**은 코드 고정(어드민 UI 없음). 게시 성공 시 outbound와 같은 Telegram 통보.
- **outbound** (팔로우 선댓글): 하루 20, **팔로우 중 타임라인 원글**(맞팔 필수 아님). 최소 댓글 수·최대 나이는 설정의 `hotMinReplies` / `hotMaxAgeHours` (`hotMinReplies=0`이면 댓글 없는 최신글도 후보). 그 글에 우리 댓글이 없으면 **원글(root)**, 있으면 스레드 대댓글. **후보 조회는 1분이 아니다** — `XGrowthLoopScheduler.outboundTick`이 **08:00–22:00 KST 30분 간격**(08:00, 08:30, … 22:30). **틱당 댓글 최대 1개**(게시 성공하면 그 틱은 끝). **네이티브 영상 글은 스킵**(`hasVideo`)하고 다음 후보로 넘어간다. GIF는 영상이 아니라 사진으로 취급. 사진이 있으면 Haiku가 **첫 JPEG만**(ASM `photoJpegBase64`) + **타인 댓글 최대 10**(`peerReplies`)을 본다. 자신 없으면 게시하지 않는다(`UNSURE`) — ㅋㅋ로 채우지 않음. 영어 원글에 한글 댓글(또는 반대)은 `LANG_MISMATCH`로 스킵. 가드 이유: `TOO_LONG` / `LAUGH_SPAM` / `ECHO` / `LANG_MISMATCH` / `VIDEO` / `VISION_FAIL`. 작문 피드백은 세 층만: 프롬프트 `docs/shared/prompts/marketing/x-outbound-reply.md` · 금지 `x-outbound-donts.md` · `system_setting` `marketing.x.outbound_guards` + `OutboundDraftGuard`. 밤·심야에는 X 세션 API를 치지 않는다. 의식/대댓글 분 단위 틱과는 분리. **게시 성공 시** `@WaggleBot_bot` Telegram으로 댓글 URL·대상 글 URL·본문을 보낸다 (`XOpsTelegramAlerts`).
- **ritual** (아침/밤 글): `morningTime` / `nightTime`에 사진 한 장 + 짧은 격려. 사진은 ASM `assets/x-ritual`.
- BE는 `RemoteLlmProvider`(Haiku) + **Justant-Bot** `persona_profile_json` + 운영자 TIMELINE few-shot + 지운 자동댓글 avoid로 작문하고, ASM이 게시한다.
- **dev는 LLM 꺼짐(L3)** — 작문·발행은 no-op. 페르소나 학습은 새벽에 그대로 돈다.
- 나중에 켤 때: inbound → outbound → ritual.

**Phase B (이후)**: 사연 메인 텍스트를 마스터 훅 대신 페르소나 1~3줄 평 + 스크린샷 1장으로 바꾼다. `storyScoopsPerDay`는 아직 소비하지 않는다. 링크·유입은 후순위.

---

## 3. 발행 트리거

### 3.1 조건 (2026-08-02~)

```
post.createdAt >= ASM_AUTO_PUBLISH_SINCE   (컷오프 — 이 시각 이후 생성분만)
AND  post.createdAt + 24시간 경과
AND  해당 post에 x_thread 타깃 marketing_job이 한 번도 없음 (status 무관)
```

사람·PLAN 구분 없이 **컷오프 이후 생성된 글만** 24시간 후, **공유 일일 풀의 글 슬롯**에
선정되면 X 스레드를 1회 자동 생성·발행한다(영상 슬롯 사연에는 X 없음).
같은 틱에서 `instagram_feed`도 함께 별도 잡을 만든다(ASM alone 제약).
상한·배분: [`platforms.md`](platforms.md) · Admin `/admin/marketing → 일일 상한`.

**컷오프 (`ASM_AUTO_PUBLISH_SINCE`, 2026-08-02)**: 24h 게이트만 켜면 기존 사연 백로그가
실계정에 연속 발행된다. ISO-8601 Instant(예: `2026-08-02T08:43:52Z`) 이후 생성분만
eligible. 트리거 ON인데 값이 비어 있으면 **fail-closed**(스킵).

**24시간의 근거**: `ThreadPlanService`가 PLAN 수명을 `publishedAt + 24h`로 잡는다.
"24시간 경과 = PLAN 슬롯 소진"이 성립하고, `createdAt`만으로 계산한다.

**댓글 수 게이트 폐지 (2026-08-02)**: 이전에는 캡처 품질을 위해 `comments >= 6`을 요구했으나,
제품 정책이 "게시 후 24시간이면 무조건 X·IG 발행"으로 확정되어 제거했다.
이후 일일 상한(공유 풀)이 추가되어 **무제한 전체 발행은 하지 않는다**.
댓글이 적어도 빈 댓글창/짧은 캡처로 올린다.

**one-shot (2026-08-01)**: X 스레드는 사연당 **1회**다. 글 슬롯 선정의
`NOT EXISTS`는 status를 보지 않는다.

### 3.2 상태값(`ThreadPlanStatus`)을 트리거로 쓰지 않는 이유

- `COMPLETED`는 **코드 어디에서도 세팅되지 않는 죽은 값**이다.
- 유일한 종료 전이는 `EXPIRED`인데, prod에 `AI_USER_THREAD_PLAN_MAINTENANCE_ENABLED`가 없어
  기본값 `false`(`application.yml:69`)다. 그 결과 **만료 시각을 6시간 넘긴 플랜도 `ACTIVE`로 남고
  `EXPIRED` 행이 0건**이다. 상태값으로는 영원히 안 걸린다.

### 3.3 댓글 게이트 (폐지)

2026-08-02 이전에는 PLAN 실패율·빈 댓글창 캡처를 이유로 `comments >= 6`을 요구했다.
제품 정책이 **게시 후 24시간 무조건 X·IG 발행**으로 바뀌어 게이트를 제거했다.
이력이 필요하면 git history의 본 섹션을 본다.

---

## 4. 구현

### 4.1 경로 — 기존 마케팅 잡 재사용

```
BE (조건 판정 → 잡 생성)  →  AsmClient  →  ASM (캡처 → 슬라이스 → X 발행)
```

`/admin/marketing`에서 잡 상태·실패를 그대로 볼 수 있고 `publication` 멱등성도 재사용된다.
BE 코드가 들어가므로 **절대 규칙 #4의 dev → e2e-realbe → main push → prod 순서를 거쳐야 한다**.

### 4.2 발행 방식 — 순차 답글

현재 `publish-x.js`는 **트윗별로 다른 이미지를 붙일 수 없다**. 이미지 업로드(`:368`)가
스레드 타이핑 루프(`:400`) 이전에 한 번만 실행되고, `images[]`를 `setInputFiles`로
전부 첫 트윗에 첨부한다. 즉 "이미지 4장 달린 트윗 1개"만 가능하다.

따라서 스레드 컴포저를 쓰지 않고 **한 칸씩 발행하고 답글로 잇는다**:

```
메인 발행 → 트윗 URL 획득 → 답글1 → 답글2 → 답글3
```

이미 `linkMode: first_reply` 경로(`:469`)가 이 방식으로 검증돼 있고, 스레드 컴포저의
트윗별 미디어 셀렉터를 새로 잡는 것보다 X UI 변경에 훨씬 덜 취약하다.

**중간 실패 시 반쪽 스레드는 짝이 맞지 않으므로 전부 삭제한다** (ASM
`app/publishers/x_thread_retry.py`). 이미 올라간 칸은 `/publish/x/delete`로 지우고
publication을 `PENDING`으로 되돌린 뒤 **5분 후** 처음부터 재시도한다
(`claimed_by=retry-hold` + `lease_expires_at`). **3회** 연속 실패하면 게시한 칸을
다시 삭제하고 잡을 `FAILED`로 종료한다. URL 추출 실패(`postedButUrlMissing`)는
중복 게시 위험이 있어 자동 재시도하지 않는다(알려진 URL만 삭제 후 수동 확인).

### 4.3 선행 조건

- **X 세션 재시딩.** ASM `credential` 테이블에 `x` 자격증명은 있으나 `has_session = 0`,
  `status = UNKNOWN`이다. 마지막 성공 발행이 2026-06-14(6건)이고 이후 6주 이상 중단 상태다.
- **트윗 URL 기록.** 과거 발행 6건 모두 `publication.url`이 `NULL` 또는
  `(posted - url extraction failed)`다. 순차 답글 방식은 **메인 트윗 URL이 있어야
  답글을 달 수 있으므로**, URL 추출 실패는 더 이상 non-fatal이 아니다. 반드시 고쳐야 한다.

---

## 4.4 구현 상태

BE(Again-Spring)의 24시간 트리거 + 마케팅 잡 생성(플랫폼 `"x_thread"` / `"instagram_feed"` 각각 alone),
ASM(Again-Spring-Marketing, WSL)의 Playwright 캡처+슬라이스 및 순차 답글 발행 — 둘 다 구현 완료,
prod에서 자동 발행 중(2026-08-02 기준). 짧은 사연 3단/긴 사연 4단 분기, 컷 지점 로직,
메인 트윗 텍스트는 §2(Phase 1 = **마스터 훅**)·§6·§7 참고.
**실발행은 렌더 READY 즉시**(커밋≠publish 시계 슬롯 없음) — [`platforms.md`](platforms.md).
**구현 세부사항은 이 문서가 아닌 실제 코드(BE 잡 생성 로직, ASM 발행 서비스)에서 확인하십시오.**

---

## 5. 보류·미해결

| 항목 | 상태 |
|---|---|
| 공감 비율 퍼센트 표기 | **추가하지 않음**(2026-07-31 결정). 마지막 칸에 남는 숫자는 표수뿐이다. |
| 공감 비율 막대가 항상 50:50 | **수정 완료**(2026-08-11). 원인: 목록 `PostResponse`에 `authorPct` 미노출 + FE `authorPct ?? 50`/`percentage ?? 50`. 조치: BE 목록에 raw `authorPct`/`partnerPct` 추가; FE `resolveAuthorPct`가 `voteResult.options[0].percentage` → `authorPct` → count 비율 순으로 해석(실표 있으면 50 강제 금지); 상세 로드 시 `post.voteResult`를 로컬 상태에 시드. SEO/OG(`fetchPostForOg`)는 기존 `voteResult` 경로 유지. |
| 콘텐츠가 사실상 전부 봇(synthetic) | **그대로 진행** — AI/합성 고지는 **2027-01** 「AI가 일부 각색」까지 없음. 고지·수익화 재평가는 예약(F1~F3). |
| `paired` 사연이 141건 중 1건 | 상대방(세이지) 카드가 거의 항상 빈 칸으로 캡처된다. |
| PLAN 아이템 실패율 19.4% | 원인 미진단. 댓글 게이트로 증상만 가린 상태. |
| 2026-08-02 이전 발행분(job148~152 등) | **미정리**. 같은 배치로 구버전 캡처 코드(비율 여백 없음, 픽셀 기반 컷)로 발행된 트윗들이 라이브에 남아있다. job146/147/153은 삭제 후 재발행 완료, 나머지는 아직 손대지 않음. |

---

## 6. 인시던트 — wiring 누락으로 트리거가 10시간+ 조용히 꺼져 있었음 (2026-08-01)

**증상**: 2026-07-31 다음날 오전, 24시간+댓글6 조건을 만족하는 사연이 9건 쌓였는데도 x_thread 잡이 하나도 생성되지 않음.

**원인**: `.env.prod`에 `ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED=true`를 2026-08-01에 설정했으나,
`env/docker-compose.prod.yml`의 `backend-prod` `environment:` 블록에 이 변수가 wiring돼 있지 않았다.
`ASM_BASE_URL`/`ASM_API_TOKEN`/`ASM_ENABLED`는 전달되는데 이 변수만 누락되어, 컨테이너 안에서는
Spring의 기본값(`${ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED:false}`)으로 조용히 `false`가 됐다.
스케줄러의 스킵 로그가 `log.debug`라 운영 로그에도 흔적이 없었다.

**수정**: `docker-compose.prod.yml`에 wiring 한 줄 추가 (`ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED: ${ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED:-false}`).

**재개 절차** (2026-07-31 실계정 오발행 사고 재발 방지):
1. wiring 수정만 우선 배포, `.env.prod`는 일시적으로 `false` 유지 — 스케줄러 자동 트리거는 계속 꺼둔다.
2. `/admin/marketing`에서 사연 1건을 `autoPublish=false`로 수동 생성 → ASM 캡처/슬라이스가 `READY`까지
   도달하는지, 캡처된 이미지가 실제로 맞는지 사람이 직접 확인한다.
3. 확인되면 `.env.prod`를 `true`로 되돌리고 재배포 — 이때부터 나머지 8건을 포함해 이후 사연이 자동으로 처리된다.

관련: `docs/env/environment-variables.md`의 `ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED` 항목,
`XThreadPublishTriggerScheduler.java`의 opt-in 플래그 주석(2026-07-31 실계정 오발행 사고 배경 설명).

---

## 6.1 인시던트 — 발행 완료 사연 무한 재발행 방지 (2026-08-01)

**증상(잠재)**: `findPostsEligibleForXThreadPublish`가 "활성 잡만" 제외하면, 성공적으로
`PUBLISHED`된 사연도 다음 폴링에서 다시 eligible이 되어 10분마다 새 x_thread 잡이 생긴다.

**수정**: status 조건 제거 — `JSON_CONTAINS(targets, '"x_thread"')`인 잡이 **한 건이라도**
있으면 영구 제외. 코드 주석에 terminal-status 배경을 남김 (`MarketingJobRepository`).

**관련 흐름도**: `docs/shared/api/flows.md` §6.

---

## 7. 인시던트 — 캡처 크롭·짧은글 분할 버그, 메인 트윗 제목 텍스트 추가 (2026-08-02)

**증상**: §6 wiring 수정으로 트리거가 켜진 뒤 자동 발행된 스레드에서 두 가지 문제가 발견됨.
1. 긴 사연의 컷 지점이 문장 중간을 자르거나(픽셀 빈 줄 탐지가 실패하면 이미지 정중앙으로
   폴백하던 구조), 공감 비율 막대에 좌우 여백이 없어 X 미리보기에서 "작성자"/"상대방" 라벨이
   잘려 보임 — job146(`post_2b97a638711244f2a889`)·job147(`post_41af02a163d04b569d8d`)의
   원래 발행분에서 확인.
2. 12줄 이하 짧은 사연도 무조건 4단으로 강제 분할됨 — job153(`post_d21666606f7747528fed`,
   174자)에서 확인. 애초 요구사항은 "짧은 사연은 나누지 말고 본문 전체를 한 장에 담고,
   첫 댓글에 댓글 캡처, 둘째 댓글에 URL+비율 막대"였다.

**원인**: 2026-07-31 최초 구현이 모든 사연을 4단(본문 앞/본문 뒤/댓글/비율)으로 고정 처리했고,
컷 지점 계산이 픽셀 기반 빈 줄 탐지(실패 시 정중앙 폴백)였다.

**수정**(ASM 리포 `services/social-poster/src/routes/capture-x-thread.js`,
`app/worker/pipeline.py`, `app/publishers/dispatcher.py`):
- 컷 지점을 `Range.getClientRects()` 기반 실제 줄 경계로 교체 — 글자 중간 절단 구조적으로 불가능
- 본문 12줄 이하(`SHORT_POST_MAX_LINES`)면 3단(본문 전체/댓글/URL+비율), 초과면 기존 4단 —
  `x_thread__upload.json`의 `steps` 필드로 발행측에 전달, 잡마다 가변
- 공감 비율 막대 좌우 여백(`RATIO_SIDE_PADDING_CSS`) 추가
- **메인 트윗에 사연 제목을 텍스트로 추가**(2026-08-02 결정, 그 전까지 전 구간 이미지 단독) —
  `upload.json`의 `title` 필드(BE의 `MarketingJob` brief에서 옴)를 메인 스텝 텍스트로 사용.
  **Phase 1 계약**: 메인 텍스트는 **마스터 훅**으로 교체(광장 `title` 아님). brief `hook`/`promo_title` 경로.

**정리**: job146·147·153의 기존 오발행 트윗(각 3~4개, 총 12개)을 삭제 후 새 코드로 재발행.
삭제 라우트(`publish/x/delete`)·존재 확인 라우트(`publish/x/check`)를 이 작업을 위해 새로 추가함 —
기존 코드베이스에 트윗 삭제 기능 자체가 없었음. 같은 배치의 나머지(job148~152)는 미정리 상태로
남음(§5 참고).

**주의**: 삭제 시 "..." 메뉴는 반드시 대상 트윗의 `<article>` 안에서만 찾아야 한다 — 답글
permalink 페이지는 부모 트윗 체인을 한 화면에 같이 보여주므로, 페이지 전역에서 첫 번째로
매칭되는 메뉴를 누르면 의도한 트윗이 아니라 그 조상(주로 상위 답글)이 삭제된다.
실제로 이 사고가 나서(엉뚱한 트윗 삭제) `publish-x-delete.js`가 `tweetUrl`의 status id로
대상 `<article>`을 먼저 특정하도록 수정됐다.
