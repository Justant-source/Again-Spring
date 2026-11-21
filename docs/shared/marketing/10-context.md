# 마케팅 자동화 시스템 — 개요

> **권위본**: 이 디렉터리 (`docs/shared/marketing/`) 가 마케팅 관련 모든 정책·API·아키텍처의 권위본입니다.

**Justant-Bot**: 운영자(Justant) 말투의 X 선댓글·대댓글 AI. 계정 `@againspring_net`. 광장 AI-user와 다름. 권위본 [`justant-bot-x-ops.md`](70-policy/justant-bot-x-ops.md).

---

## Phase 2 = 타깃 SSOT (2026-08-11~)

코드가 **병렬 착수** 중. 아래 Phase 2 계약이 **새 타깃 SSOT**다. 런타임이 아직 공유 풀·동일 mp4여도 문서·구현은 이 계약을 향해 맞춘다.

**북극성** = 유입·계정 성장. 제품 = 광장 + 공감 투표 + AI-user 시딩 (**AI 배심원 없음**). AI/합성 고지는 **2027-01** 「AI가 일부 각색」.

### Phase 2 계약 (타깃)

| 계약 | 요지 | 상세 |
|---|---|---|
| 플랫폼별 점수·cap | X / IG feed / Reels / Shorts **독립 popularity** + **일일 cap 기본 각 3** | [`platforms.md`](70-policy/platforms.md) §점수·cap |
| 멀티 플랫폼 | 같은 사연·같은 날 **중복 허용**. **IG feed ⊥ Reels만** 배타 (`score_feed` vs `score_reels`, 동점→Reels) | [`platforms.md`](70-policy/platforms.md) |
| 영상 | Reels ≤30s / Shorts ≤45s · **유니크 mp4** · 전문 낭독 폐기 · 비트 = 자극 훅→요약→비율/클리프행어 | [`youtube-shorts-strategy.md`](70-policy/youtube-shorts-strategy.md) |
| 시봄이 삽입 | 메타포 **금지** · 시봄이 30장 · 인트로+본문 4~7 · 본문 = 1절+캐릭터 카드 / 시봄이 없는 줄만 ≤3블록 | [`sibom-video-insertion.md`](70-policy/sibom-video-insertion.md) §6 |
| 2단 훅 | 사연 생성 = 마스터 훅+감정 / **영상 슬롯 확정 시** = `hook_reels`·`hook_shorts` + 스크립트 | [`api.md`](50-api.md) · [`youtube-shorts-strategy.md`](70-policy/youtube-shorts-strategy.md) |
| TTS 감정 | `hook_emotion` → WaggleBot S2 Pro (`shock`\|`anger`\|`tension`\|`sad`\|`hype`) | [`api.md`](50-api.md) |
| 통계·학습 | 플랫폼 통계 수집 + **주간 리포트** + 가중치 `auto_adjust` on/off (프롬프트 자동 패치 없음) | [`platforms.md`](70-policy/platforms.md) · [`api.md`](50-api.md) · [`architecture.md`](20-containers.md) |

### Phase 1 유지 (폐기하지 않음)

| 계약 | 요지 | 상세 |
|---|---|---|
| 마스터 훅 | 광장 `title` ≠ SNS 훅. `promoTitle`/`hook*` + `hook_emotion` | 전략 문서·[`api.md`](50-api.md) |
| 태그 | 브랜드 항상 `#다시봄`+`#againspring`. X=2개만 / IG≤5 / YT=`#Shorts`+브랜드+니치 | [`platforms.md`](70-policy/platforms.md) |
| IG 캡션 | raw URL 제거 · 프로필 링크 | [`instagram-feed-strategy.md`](70-policy/instagram-feed-strategy.md) |
| 발행 | T+24h 커밋 후 **채널 렌더 READY 즉시 실발행** (저녁 슬롯 없음) | [`platforms.md`](70-policy/platforms.md) |
| UTM | X·YT 등 링크 → 사연 상세 | [`api.md`](50-api.md) |
| 텔레그램 | 발행 후 N시간(기본 24h) 신규 댓글 → WaggleBot 텔레그램 · 수동 답글 | [`architecture.md`](20-containers.md) |
| **유입 계측** | 전 페이지뷰 기록 · 봇 분리 · `as_utm` first-touch → 가입 채널 귀속 · 어드민 퍼널 화면 | [`acquisition-measurement.md`](40-data/acquisition-measurement.md) |
| **검색 기반** | robots·sitemap(348 URL)·홈/광장 SSR·구글(DNS)/네이버(HTML 파일) 소유확인 | [`seo.md`](70-policy/seo.md) |
| **클릭 경로** | YT = **채널 프로필 링크가 유일**(설명란·고정댓글 불가) · X = 링크를 **첫 답글**에 · IG = 프로필 | [`youtube-shorts-strategy.md`](70-policy/youtube-shorts-strategy.md) §5.2 · [`x-thread-strategy.md`](70-policy/x-thread-strategy.md) |
| 고지 | **2027-01**까지 AI/합성 고지 없음 · 배심원 카피 금지 | 본 절 |

---

## 아키텍처 개요

```
[다시봄 어드민 /admin/marketing]
        │  대기 보드 · 핀 · 초안 · 플랫폼별 cap/가중치 · 통계 탭(KPI·테마·타임라인)
        │  T+24h 스케줄러 / 완료탭 강제
        ▼
[Again-Spring BE]  MarketingHoldingCommitService → MarketingJobService
        │
        │  AsmClient (REST)
        ▼
POST /api/v1/jobs → [Again-Spring-Marketing (ASM)]
                     WSL GPU 서버 100.115.252.61:8200
                     M0~M6 파이프라인 → 소셜 게시
```

### 컴포넌트 역할

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| **Again-Spring (AS)** | Ubuntu 서버 | 홀딩 보드·24h 확정·잡 폴링·Admin UI. 얇은 ASM 클라이언트. 렌더러/SFX/BGM 설정은 **WaggleBot 권위본** 참조 |
| **Again-Spring-Marketing (ASM)** | WSL GPU 서버 | 콘텐츠 생성·렌더링·소셜 게시 전담. 게시된 로컬 mp4는 **30일** 보존. WaggleBot 프록시 게이트웨이 |
| **WaggleBot** | WSL GPU 서버 (ASM과 동일) | 비디오 렌더링 엔진. **단일 공유 인스턴스** (dev/prod 구분 없음) — 렌더러 설정(`worker/ai_worker/renderer/settings.yaml`) 변경은 즉시 운영 발행에 반영 |
| **ASM social-poster** | ASM `services/social-poster/` | Playwright 자동 게시. **타깃 분배 = 플랫폼별 score·cap** (런타임 전환 중일 수 있음 — SSOT는 Phase 2) |

**접속**: AS 호스트 Tailscale `100.81.189.92`에서 `ssh justant@100.115.252.61` (암호 없음) → `~/Data/Again-Spring-Marketing`

## 반응 루프 · 포맷 실험 (2026-11-21, 구현 중 · 기본 전부 꺼짐)

게시 성공이 아니라 **사람의 반응**으로 영상 포맷과 소재를 고르는 구조. 진단 배경: 게시물 약 1,250건 중 시험 노출을 넘어 확산된 것이 0건이었고, 영상 포맷이 한 가지(정지 텍스트 카드)뿐이었다. 진행 트랙: `docs/_active/marketing-breakout-loop.md`.

흐름: AS가 영상 잡마다 **포맷 arm**을 배정 → 게시 48시간 뒤 같은 일령의 반응으로 **성과 점수** 계산 → 점수가 다음 arm 배정(UCB1)과 소재 선택 **주제 편향**에 되돌아간다. arm이 정한 영상 매개변수는 AS → ASM → WaggleBot으로 전달된다. 코드: `backend/.../marketing/experiment/`.

### 포맷 arm

`marketing_format_arm` 테이블(V132). 영상 플랫폼(`instagram_reels`, `youtube_shorts`)의 **자동 게시 잡**에만 적용한다. 어드민 `실험` 패널(통계 탭)에서 켜고 끈다.

| arm | 프로필 | 대본 | 댓글 수 | 아웃트로 | 길이 상한 | 시드 |
|---|---|---|---:|---|---:|---|
| `baseline` | `marketing_hook_a` | narrative | 2 | `partner_cta` | 플랫폼 기본 | 켜짐 |
| `short_reveal` | `marketing_hook_a` | narrative | 0 | `vote_reveal` | 25초 | 꺼짐 |
| `dilemma` | `marketing_hook_a` | dilemma | 0 | `vote_reveal` | 30초 | 꺼짐 |
| `kinetic_reveal` | `marketing_hook_b` | narrative | 1 | `vote_reveal` | 30초 | 꺼짐 |
| `reaction_reveal` | `marketing_hook_c` | dilemma | 0 | `vote_reveal` | 30초 | 꺼짐 |

- `dilemma` 대본: 사건 → 작성자 입장 한 문장 → 상대방 입장 한 문장 → 시청자에게 어느 쪽에 공감하는지 묻는 문장으로 끝낸다. 결말·교훈을 서술하지 않는다(`VideoVariantService`의 `scriptStyle`). `narrative`/미지정은 기존 프롬프트와 같다.
- `vote_reveal`: 엔드카드가 공감 비율 막대와 "당신은 어느 쪽인가요?"를 보여 준다. 비율이 없으면 문구만.
- `comment_count`: 영상에서 낭독하는 댓글 수(0이면 댓글 장면 없음).
- 길이 통제는 이 값(`comment_count`, `max_duration_*`)으로 한다. 현행 30초 상한이 실제로는 적용되지 않았다 — [`youtube-shorts-strategy.md`](70-policy/youtube-shorts-strategy.md) §3.
- 카카오톡 대화형 포맷, X 스레드·IG 피드의 arm은 범위 밖.

### arm 배정 (`pickForAutoJob`)

1. `marketing.experiment.enabled=false`면 배정하지 않는다(`format_arm` NULL, 현행 동작 그대로). 켜진 arm이 없거나 영상 플랫폼이 아니어도 배정 없음. 켜진 arm이 하나뿐이면 그 arm.
2. 켜진 arm 중 최근 30일 배정 수가 `marketing.experiment.min_samples`(기본 8) 미만이거나 채점 건수가 0인 arm이 있으면, 배정 수가 가장 적은 arm(동률은 key 사전순).
3. 모두 채웠으면 UCB1: `meanReward + sqrt(2*ln(N)/n)`이 가장 큰 arm. `n` = 해당 arm의 최근 60일 채점 건수, `N` = 그 합.

실험 프로필(`marketing_hook_b/c`)은 arm이 정한 경우에만 자동 게시를 허용한다. arm 없이 들어온 요청은 지금처럼 400으로 거부한다. arm 적용 동작의 세부는 `MarketingJobService`가 권위본이다(배선 진행 중).

### 성과 점수

잡 × 플랫폼마다 1행(`marketing_publication_outcome`). 매일 06:30 KST 통계 수집 뒤 `scorePending()`이 채점하고, 어드민 "지금 채점"으로도 돌릴 수 있다. 게시 시각 = `scheduledPublishAt`(없으면 `createdAt`).

```
primary    = YouTube views | X impressions | IG reach
engagement = likes + 2*comments(X는 replies) + 3*(shares+saves)(IG) | 3*reposts(X)
engRate    = engagement / max(primary, 1)
viewRatio  = primary / 같은 플랫폼 직전 채점 30건 primary 중앙값       (표본 10건 미만이면 1.0)
engRatio   = engRate / 같은 30건 engRate 중앙값                      (중앙값 0이면 engRate>0 ? 2.0 : 1.0, 표본 10건 미만이면 1.0)
reward     = 0.6*min(viewRatio, 10) + 0.4*min(engRatio, 5)
breakout   = viewRatio >= 3.0
```

- 스냅샷은 게시 +36h ~ +96h 구간에서 **지표가 있는 가장 이른 것**을 쓴다(지표가 전부 null인 빈 행은 건너뛴다).
- 구간 안에 스냅샷이 없고 게시 +96h가 지나면 `MISSING`으로 남기고 통계에서 뺀다.
- 조회가 시험 노출 구간에 묶여 있는 동안은 `engRatio`가 arm을 가른다. 확산이 일어나면 `viewRatio`가 지배한다.

### 주제 편향

`marketing.experiment.topic_bias_enabled=true`일 때만, 소재 선택 점수에 배수를 곱한다(꺼져 있으면 1.0, 기존 동작). 셀 = (platform, category, hook_emotion), 최근 60일 채점 건수 n.

```
m = ((n*cellMean + 5*platformMean) / (n + 5)) / platformMean     (platformMean <= 0이면 1.0)
multiplier = clamp(m, 0.5, 2.0)
```

사람 확정 없이 커밋 틱마다 최신 값으로 계산한다. 어드민 표의 배수는 스위치와 무관하게 현재 계산값을 보여 준다.

### 계정 지표

ASM `GET /api/v1/stats/accounts`(X 팔로워, YouTube 구독자·누적 조회·영상 수, Instagram 팔로워)를 AS가 하루 1회 `marketing_account_stats`에 적재한다(같은 날 재실행은 갱신, 값이 전부 없는 플랫폼은 건너뜀). ASM이 아직 엔드포인트가 없거나 실패하면 경고 로그만 남긴다.

### 기본값과 켜는 절차

**기본은 전부 꺼짐**: `marketing.experiment.enabled=false` · `topic_bias_enabled=false` · `baseline` 외 arm 꺼짐. 꺼져 있으면 잡 페이로드·산출물이 실험 도입 전과 같아야 한다(ASM·WaggleBot도 새 필드가 없으면 기존과 같음). ASM은 dev·prod가 공유하는 단일 인스턴스이고 실계정에 게시한다.

1. dev 배포 → e2e → (명시 지시 시) prod 배포. ASM 재빌드, WaggleBot 백엔드 재빌드·워커 재시작.
2. 테스트 탭에서 새 arm 영상을 눈으로 확인한 뒤 어드민 `실험` 패널에서 arm을 켠다.
3. `marketing.experiment.enabled`를 켜고, cap을 권고값으로 내린다: `marketing.cap.youtube_shorts=2` · `instagram_reels=1` · `x_thread=2` · `instagram_feed=0`(후보가 하루 6~7편이라 cap이 크면 주제 편향이 고를 여지가 없다). cap은 코드로 바꾸지 않는다.
4. 채점이 arm당 8건쯤 쌓이면 주제 편향을 켠다.

API: 어드민 [`50-api.md` §4.6](50-api.md) · ASM/WaggleBot 전달 필드 [`50-api.md` §2.1](50-api.md).

---

## 파일 구조

```
docs/shared/marketing/
├── README.md           ← 이 파일: 전체 개요 · Phase 2 타깃 SSOT
├── api.md              ← AS ↔ ASM REST API · brief·쿼터·통계
├── architecture.md     ← 시스템 설계 · 분배·슬롯·텔레그램·통계 루프
├── asm-setup.md        ← ASM 서버 설치·운영 가이드
├── platforms.md        ← 지원 플랫폼 · Phase2 점수/cap · 태그 · 슬롯
├── credentials.md      ← 플랫폼 계정 자격증명 저장·암호화 정책
├── social-poster.md            ← social-poster 서비스 운영 가이드
├── x-thread-strategy.md        ← X 사연 스레드 (솔로 3~4 / 양면 최대 6단)
├── instagram-feed-strategy.md  ← IG 하이브리드 캐러셀 (24h · 글 슬롯)
├── youtube-shorts-strategy.md  ← Shorts/Reels (유니크·길이·TTS 감정 · §5.2 클릭 경로 제약)
├── sibom-video-insertion.md    ← 시봄이 숏폼 삽입 (메타포 금지 · 본문 §6 레이아웃 SSOT)
├── acquisition-measurement.md  ← 🆕 유입 계측 (방문→고유방문자→가입 · 기준선 · 판정 기준)
└── seo.md                      ← 🆕 검색 유입 기반 (robots·sitemap·SSR·소유확인)
```

> **발행 이후를 보려면** [`acquisition-measurement.md`](40-data/acquisition-measurement.md)를 읽는다.
> 플랫폼 지표(조회·도달)까지는 [`api.md`](50-api.md) §4.2.1이 권위본이고, **클릭·방문·가입은
> 그 문서**가 권위본이다. 2026-08-29 이전에는 이 구간의 계측이 통째로 깨져 있었다.

---

## 빠른 시작

> **24h 자동 분배 (Phase 2 타깃)**: 대기 보드 → T+24h **커밋**. 채널별 독립 점수·cap(기본 3) · 같은 사연 멀티 플랫폼 허용 · IG feed⊥Reels만 배타. **실발행**은 렌더 READY 즉시. 상세 [`platforms.md`](70-policy/platforms.md).

### 어드민 `/admin/marketing` 탭

| 탭 | 역할 |
|---|---|
| **대기** | 24h N-top 홀딩 보드 · 카드 라벨 = 포맷(VIDEO/TEXT) + 상태(후보/후보 외) · 핀 = 인라인 포맷 select(`VIDEO\|TEXT`, soft-reserve) · 초안 다이얼로그 = 게시글 제목 + 작성자/상대방 본문 read-only 표시, `promoTitle` 숨김, `tags`·`topComments`만 편집 가능 · 일일 상한·점수 가중치 |
| **완료** | 사연(story) 단위 리스트 · 상단 **게시 이력**(COMMITTED) — 클릭 시 플랫폼별 상태+URL · **Job {id}** 는 `/admin/marketing/jobs/{id}` 로 이동(다이얼로그 내 승인/재시도 없음) · 하단 **탈락**(DROPPED) — 강제 배포(인라인 모드 선택 + 확인) · 플랫폼 성과 카드·잡 보드 박스·구 타임라인 UI는 이 탭에서 제거됨 |
| **설정** | 플랫폼 자동 on/off · **X 운영**(아침/밤·대댓글/선댓글 한도·킬스위치 기본 꺼짐 · 댓글·의식 발행은 연결됨 · **페르소나 학습** 기본 켜짐 04:30) · 플랫폼 계정 자격증명 · (Phase 2) 채널별 cap·가중치·`auto_adjust` · **배경음악(BGM)** on/off + 감정별 곡 선택 · **효과음 매핑** (삽입 지점 17개 × 음원 282개) |
| **통계** | Phase 3: 채널 KPI·UTM·수집 건강 · 감정×카테고리 테마 배수(제안→확정) · 이벤트 타임라인 · 주간 리포트 |
| **테스트** (2026-08-22) | 최근 사연 목록 또는 postId 직접 입력 → 릴스/쇼츠 렌더 테스트. `POST /jobs`를 `autoPublish:false`로 호출 — LLM 대본·시봄이 매핑을 실제로 생성하고 WaggleBot이 렌더링하지만 **실제 플랫폼에는 절대 게시되지 않는다**. 완료된 영상은 탭 안에서 `ArtifactSection`으로 바로 미리보기(같은 사연을 반복 실행해 LLM 결과 편차 비교 가능). 게시 버튼 없음 — 대기/완료 탭과 완전히 분리된 QA 전용 화면 |

> **신규 홀딩 기본 태그 시드**: `#다시봄` `#againspring` `#공감비율` `#[카테고리]` (≤5) — 신규 홀딩 생성 시에만 적용, 기존 홀딩 백필 없음 (`MarketingHoldingBriefSeeder`). X 텍스트는 브랜드 2개만.
> **긴급 재게시**: 완료 탭 다이얼로그에는 승인/재시도가 없다 — 잡 상세 페이지(`/admin/marketing/jobs/[id]`)의 게시/재게시 버튼으로 처리.

1. `https://againspring.net/admin/marketing` (기본 탭 = **대기**)
2. 상한·가중치 조정 → 순위/컷라인 즉시 반영
3. 필요 시 핀으로 soft-reserve · 초안 PATCH (tags·topComments)
4. T+24h에 스케줄러가 채널별 점수·cap으로 COMMITTED(잡 생성). 선정 실패는 대기 탭에 잔류·재시도, 미선정만 DROPPED
5. **완료** 탭에서 게시 이력(플랫폼별 상태·URL) 확인 · 탈락 건 강제 배포 · 긴급 시 잡 상세에서 직접 게시/재게시
6. **설정**에서 채널 auto on/off · 계정 자격증명 · Phase 2 cap/가중치 · **통계** 탭에서 KPI·테마 배수·주간 리포트

> 수동 `POST /api/admin/marketing/jobs`는 BE에 남아 있으나(스케줄러·force·e2e·**테스트 탭**), Admin UI의 실게시 주 경로는 여전히 **대기 보드 → 자동/강제 확정**이다. 테스트 탭은 같은 엔드포인트를 `autoPublish:false`로만 호출하므로 실게시 경로와 절대 섞이지 않는다.

### IG 단건 검증 (요청 시 1사연)

1. 설정에서 대상 채널만 on · 또는 강제/`TEXT` 핀으로 피드 슬롯 확보
2. 빌드 완료 후 아티팩트: `card_01` 훅(4:5) · 중간 장 · 비율카드 · `upload.json` 캡션(**URL 없음**)
3. 완료 탭에서 잡 상세 → 게시 승인 → 인스타 앱에서 확인

> 중간 장·댓글 글자 크기 규칙: [`instagram-feed-strategy.md`](70-policy/instagram-feed-strategy.md) §2.1.1–2.1.2 (`commentsReadableBudget`은 IG만, X는 최대 4장 고정).

### 잡 상태 흐름

```
REQUESTED → QUEUED → RUNNING → READY → PUBLISHING → PUBLISHED
                                  ↓
                               (수동 승인 시 PUBLISHING)
RUNNING → SLA_BREACHED (15분 생성 SLA 경고, 계속 폴링)
FAILED(WaggleBot poll timeout) → WAITING_EXTERNAL → READY
                ↓
             STALE (폴링 5회 연속 실패) → FAILED (24시간 ASM 무응답)
```

`SLA_BREACHED`와 `WAITING_EXTERNAL`은 게시 실패가 아닌 원격 처리 대기 상태다. AS는 같은
remote job ID를 계속 조회하며, 나중에 `READY`가 되면 그 폴링 주기 안에
즉시 게시한다. 이후 `READY`/`PUBLISHED` 응답은 이전의 처리 지연 상세와 오류 표시를 지운다.

`scheduled_publish_at`은 **DB NOT NULL**이다(2026-08-15, `V117`). 값은 잡 생성 시각이며
**자동 발행을 게이팅하지 않는다** — `auto_publish=true` 잡은 READY 도달 즉시 게시한다.
저녁 고정 슬롯·다음날 이월은 폐기했다(2026-08-16).

---

## 실패 처리 정책 (2026-08-15 안정화)

### 재시도 원칙

**콘텐츠 안전만 fail-closed. 운영 문제는 재시도한다.**

| 실패 종류 | 예 | 처리 |
|---|---|---|
| 콘텐츠 안전 | 스키마 누출·LLM 오류 문자열 | 재시도 금지, 즉시 사망 |
| 운영 문제 | 시봄이 분량 미달·LLM 타임아웃·렌더 실패 | **총 2회**(초기 1 + 5분 후 재큐잉 1) |
| 인증/세션 오류 | Claude 세션 만료 | 재시도 없음 — 재시도해도 100% 실패하므로 즉시 긴급 알림 + 회로 차단(위 원칙의 유일한 예외) |

### 실패 계약

`MarketingJobService.failJob()`이 모든 실패 처리의 단일 진입점이다. `job.setStatus("FAILED")`를
직접 호출하는 코드는 없어야 한다. 각 실패는 4개 필드를 반드시 채운다:

| 필드 | 예 |
|---|---|
| `failureStage` | `AS:QUALITY_GATE`, `AS:ASM_POLL` (AS 자체 7단계). ASM은 `ASM:WAGGLE_POLL` 등 10단계, WaggleBot은 `WAGGLE:SCENE_COMPOSE` 등 phaseName 기반 — 저장소별 독립 어휘 + 접두사(공통 enum 없음, ASM 단방향 계약 보호) |
| `failureCode` | 예: `SIBOM_PLAN_TOO_SHORT`, `ASM_24H_TIMEOUT` |
| `retryable` | boolean |
| `errorSummary`/`errorMessage` | 사람이 읽을 원인 |

stage·code가 비면 "⚠️ 원인 미기록(코드 결함)"으로 텔레그램에 표시된다(침묵 방지) — 조용히
UNKNOWN으로 덮지 않는다.

### 텔레그램 진단 메시지

실패 알림은 잡 ID뿐 아니라 **환경·재시도 상태·3중 식별자(AS/ASM/WaggleBot)·단계·시도 이력
(KST 시각·소요시간·오류 원문)·컨텍스트(시봄이 장수·TTS voice 등)·실패 단계별 확인 명령**까지
담는다 — 붙여넣으면 추가 조사 없이 진단 가능하게 설계됐다. 3800자 초과 시 시도 이력부터 축약.

### 일일 발행 리포트

`MarketingDailyReportScheduler`가 매일 22:00 KST에 채널별 생성/발행/실패/대기 건수와 전환율을
텔레그램으로 보고한다. **전 채널 발행 0건이어도 반드시 보고**한다("⚠️ 오늘 발행 0건") — 그 전까지는
잡이 아예 안 만들어지거나 조용히 0건 발행돼도 알림이 없었다.

---

## 환경 변수 (Again-Spring 측)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `ASM_BASE_URL` | `http://100.115.252.61:8200` | ASM 서버 주소 |
| `ASM_API_TOKEN` | `asm-dev-token-change-in-prod` | Bearer 인증 토큰 |
| `ASM_ENABLED` | `true` | false 시 잡 생성 API 비활성화 |
| `ASM_POLL_INTERVAL_MS` | `15000` | 폴링 주기 (밀리초) |
| `ASM_PROCESSING_SLA_MS` | `900000` | 원격 생성 지연 경고 기준; 초과해도 실패 처리하지 않음 |
| `ASM_REQUEST_TIMEOUT_MS` | `30000` (2026-08-15, 기존 `10000`) | ASM HTTP 타임아웃 |

> **ASM 측 추가 env**: `ASM_CREDENTIAL_KEY` (base64 32바이트) — 플랫폼 계정 자격증명 AES-256-GCM 마스터키.
> ASM `.env`에만 두고 git 커밋 금지. 생성: `openssl rand -base64 32`. 상세: [`credentials.md`](40-data/credentials.md)

---

## 관련 코드 위치

| 영역 | 경로 |
|---|---|
| BE 얇은 클라이언트 | `backend/.../marketing/AsmClient.java` |
| BE 홀딩·확정 | `backend/.../marketing/holding/MarketingHoldingService.java` · `MarketingHoldingCommitService.java` |
| BE 플랫폼 auto / 점수 / 상한 / 자동 즉시발행 | `MarketingPlatformAutoService` · `MarketingScoreWeightService` · `MarketingQuotaService` · `MarketingJobService` |
| BE 잡·폴링 | `MarketingJobService.java` · `MarketingPollingScheduler.java` |
| BE Admin API | `AdminMarketingController` · `AdminMarketingHoldingController` · `AdminMarketingCompletedController` · `AdminMarketingPlatformController` |
| BE Admin API 경로 (BGM/SFX) | `GET /api/admin/marketing/bgm/tracks` · `GET /api/admin/marketing/bgm/sample` · `PUT /api/admin/marketing/bgm/settings` · `GET /api/admin/marketing/sfx/mapping` · `PUT /api/admin/marketing/sfx/mapping` · `GET /api/admin/marketing/sfx/sample` |
| FE 마케팅 허브 | `frontend/app/(admin)/admin/marketing/page.tsx` (탭: 대기/완료/통계/테스트/설정) |
| FE 홀딩 UI | `frontend/components/admin/marketing/HoldingBoard.tsx` · `HoldingControlsBar.tsx` · `HoldingDraftDialog.tsx` |
| FE 테스트 탭 (렌더 QA) | `frontend/components/admin/marketing/RenderTestSection.tsx` — `createMarketingTestJob`(항상 `autoPublish:false`, 게시 버튼 없음). 프로필 5종 선택 · **포맷 arm 선택(꺼진 arm도 렌더 가능, 플랫폼별로 따로 렌더, 배지 표시)** · 프로필/훅 규칙(v1·v2)·arm 배지·필터 · 같은 사연(postId) 변형 나란히 비교. A/B 성과 분석 = `scripts/marketing_ab_report.py`(같은 일령 비교, 코호트 n<10 이면 결론 보류) |
| FE 잡 상세 | `frontend/app/(admin)/admin/marketing/jobs/[id]/page.tsx` |
| FE API 클라이언트 | `frontend/lib/api/admin/marketing.ts` |
| FE 플랫폼 계정 UI | `frontend/components/admin/marketing/PlatformCredentialsSection.tsx` · `PlatformAutoSection.tsx` |
| FE 설정 탭 (BGM/SFX) | BGM 선택·on/off = `PlatformCredentialsSection.tsx`의 `BgmTrackPicker` (배치는 `ShortformVideoSection.tsx`) · 효과음 매핑 = `SfxMappingSection.tsx` |
| ASM 자격증명 (crypto/스키마/API) | `app/core/crypto.py` · `app/domain/credentials.py` · `app/api/routes_credentials.py` |
| ASM WaggleBot 프록시 | `app/api/routes_waggle_voices.py` (TTS voices · `/bgm/tracks`·`/bgm/sample`·`/bgm/settings` · `/sfx/mapping`) |
| ASM 프로젝트 | `/home/justant/Data/Again-Spring-Marketing/` (WSL) |
| ASM social-poster | `services/social-poster/` (WSL) |
| WaggleBot 렌더러 설정 권위본 | `worker/ai_worker/renderer/settings.yaml` (WSL) — `sfx.active` · `bgm.enabled` · 트랙 할당 |
