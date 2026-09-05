# AI Learning Service

`ai-user/learning`은 FastAPI 기반 보조 서비스다. 역할은 4개다.

1. `example_bank`에 인간 예시와 AI 예시를 저장
2. RAG 검색과 style sample 제공
3. 크롤링과 dedup
4. 말투 강화와 daily topic 합성

## 현재 startup 동작

learning container가 뜨면 아래가 항상 실행된다.

1. `EmbeddingService.load()`로 `nlpai-lab/KURE-v1`를 메모리에 올린다.
2. DB 테이블을 생성한다.
3. `init_scheduler()`가 APScheduler를 시작한다.

현재 코드에는 startup 자체를 `AI_LEARNING_ENABLED`나 `AI_LEARNING_CRAWL_ENABLED`로 막는 분기가 없다.

## API

| 메서드 | 경로 | 역할 |
|---|---|---|
| `GET` | `/health` | 헬스체크 |
| `POST` | `/embed` | 임베딩 디버그 |
| `POST` | `/examples/save` | example 저장 + register 분류 |
| `POST` | `/examples/search` | 3단계 폴백 유사 예시 검색 |
| `POST` | `/examples/style-sample` | 주제 무관 말투 샘플 랜덤 반환 |
| `GET` | `/examples/export` | ML/분석용 코퍼스 export |
| `GET` | `/examples/{id}` | 단일 원본 조회 |
| `GET` | `/examples/count` | source별 개수 |
| `POST` | `/crawl/{source}` | 수동 크롤 트리거 |
| `GET` | `/crawl/log` | 최근 crawl 로그 |
| `POST` | `/strengthen/batch` | 전체 voice 강화 |
| `POST` | `/strengthen/{voice_type}` | 특정 voice 강화 |
| `GET` | `/strengthen/status` | 예시/voice 소스 상태 |
| `GET` | `/topics/today` | 오늘의 seed 조회 |
| `POST` | `/topics/{id}/use` | used_count 증가 |
| `GET` | `/topics/stats` | 최근 topic 상태 |
| `POST` | `/topics/synthesize` | 수동 합성 |
| `POST` | `/examples/claim-popular-source` | 인기 crawl POST soft-claim (재구성 원본) |
| `POST` | `/examples/commit-source` | soft → COMMITTED (영구 사용) |
| `POST` | `/examples/release-source` | soft 예약 해제 |
| `POST` | `/examples/expire-source-reservations` | 만료 soft 행 정리 (optional) |

## example_bank 동작

### 저장

- `/examples/save`는 본문 512자까지만 임베딩한다.
- register classifier 결과를 `casual`, `polite`, `mixed` 축으로 저장한다.
  Wave1-D 이후 기준 문체는 BLIND(polite 편향)·NATEPAN(casual 편향) 두 곳이며,
  `mixed` 과다로 `/examples/similar` 필터가 무력화되지 않도록 다수결 임계는 0.55다.
- orchestrator 저장 훅은 `AI_LEARNING_ENABLED=true`일 때만 실행된다.
- (WO-CRAWL-01) 크롤 시점 관심도 스냅샷을 함께 저장한다: `view_count`·`like_count`·`comment_count`·
  `engagement_span_hours`(첫~마지막 댓글 시간폭). 매일 KST 04:00 배치잡이 같은 `source`+나이구간
  (0~3h/3~12h/12h+) 안에서 백분위를 계산해 `popularity_pct`(0~1)로 저장한다 — 표본 30건 미만 구간은
  NULL 유지. 재가공 원본 선별(`rebuild_public_feed_from_crawled_sources.py`)이 이 값으로 하위 30%를
  거르고 가중 샘플링한다.

### 검색

`/examples/search`는 아래 순서로 폴백한다.

1. `content_type + category + source != SELF_GENERATED + register + quality`
2. `quality` 제거
3. `category` 제거

현재 Java orchestrator는 `excludeSelfGenerated=true`로 요청해서 SELF_GENERATED 예시를 기본적으로 제외한다.

### style sample

`/examples/style-sample`은 임베딩을 쓰지 않고 `ORDER BY RAND()`로 뽑는다.

- stage 1: source + content_type + register + quality
- stage 2: source 완화
- stage 3: comment 샘플 부족 시 짧은 post로 대체

이 경로는 `ActionExecutor.styleExamplesFor()`가 RAG miss fallback으로 쓴다.

## 크롤링

### 수동 crawl

`POST /crawl/{source}?limit=...`는 background task로 `_do_crawl()`를 호출한다.

- 품질 필터 미통과 항목은 저장하지 않는다.
- 같은 `source_url`은 한 run 안과 기존 DB 모두에서 dedup한다.
- **동시성 가드 (2026-08-10)**: ingest 직전에 MariaDB `GET_LOCK(ai_learning_crawl_ingest:{source})`를
  잡고 URL 스냅샷을 **다시** 읽은 뒤 INSERT한다. embed 전 스냅샷만 쓰면 겹친 crawl 두 건이
  같은 Blind/Natepan URL을 `example_bank`에 이중 INSERT하던 레이스가 있었다.
- 결과는 `crawl_log`에 남는다. `GET /crawl/log`의 `at` 필드는 ISO-8601 UTC(`...Z`) 문자열이다
  (2026-07-30 이전에는 Python `str(datetime)` 그대로라 backend의 `Instant.parse()`가 못 읽는 버그가 있었음).
- 표절 방어: `services/ngram_guard.py`(연속 n-gram 겹침 검사, 임계값 0.20 — 소급 감사 실측 기반)와
  `services/popularity_scorer.py`(관심도 백분위)가 이 디렉토리에 있다.

### 현재 scheduler source budget

`ai-user/learning/app/scheduler.py` 기준 (Wave1-D, 2026-08-01 — BLIND·NATEPAN 단일화):

| source | limit |
|---|---:|
| `natepan` | `1500` |
| `blind` | `500` (WO-CRAWL-01 — 403 차단율은 admin 배지로 관찰) |

활성 크롤러 모듈은 `crawlers/natepan.py` · `crawlers/blind.py` 둘뿐이다.
`clien` · `theqoo` · `ruliweb` · `dcinside` · `fmkorea` · `mlbpark` · `ppomppu` · `bobaedream` ·
`naver_comments` · `daum_comments` 및 `*_backup`은 코드에서 삭제했다.

**2026-08-01**: prod `example_bank`에서 clien·ruliweb·theqoo·dcinside **4,346건 삭제** 완료
(백업: `prod-pre-corpus-unify-20260801-163820.sql` — **2026-09-05 백업 30일 보관 정책으로 삭제됨**.
이 시점으로의 롤백은 더 이상 불가하다).
`BLIND` → `blind` source 정규화. 잔여: natepan · blind · SELF_GENERATED.

### 현재 스케줄

| 작업 | UTC | KST |
|---|---|---|
| daily crawl | `17:00` | `02:00` |
| popularity 재계산 (`recompute_popularity_scores`) | `19:00` | `04:00` |
| standalone strengthen + topic synthesis | `20:00` | `05:00` |

크롤 자체는 `run_daily_crawl()` 안에서 강화·토픽합성까지 이어서 호출한다 (02:00 한 번의 잡 안에서 순차 실행).
사연 생성 배치(`nightly-ai-user-batch.sh`, **03:05 KST**)와 겹치지 않도록 크롤만 1시간 앞당긴다.

### 런타임 가드 (2026-08-11)

- **uvicorn `--workers 1` 고정** (`ai-user/learning/Dockerfile`). worker마다 `lifespan`→`init_scheduler()`가
  떠서 daily crawl이 이중 실행되던 문제를 막는다.
- **크롤 진행 마커** `/tmp/ai_learning_crawl_in_progress` (`app/crawl_guard.py`). `_do_crawl` /
  `run_daily_crawl`이 쓰는 동안 `ops-watchdog`는 `againspring-ai-learning`을 **재시작하지 않는다**
  (알림만). KST 02–03시도 동일 보호(스케줄 윈도우).
- 크롤 중 health 지연은 임베딩 CPU 포화 때문에 날 수 있다. compose healthcheck는 timeout 30s /
  retries 5로 여유를 둔다.
- 유실 분 수동 보충은 `POST /crawl/{natepan|blind}` (+ 필요 시 strengthen/topic)로 한다.

### Popularity 게이트 (2026-08-01)

크롤 결과는 **무차별 저장하지 않는다.** `popularity_gate`가 임베딩·INSERT 전에 걸러낸다.

| 규칙 | 내용 |
|---|---|
| POST 지표 | view/like/comment 중 ≥1개 필수 |
| 절대 하한 | source별 floor (natepan: view≥50 ∨ like≥3 ∨ comment≥5 · blind: view≥30 ∨ like≥1 ∨ comment≥3) |
| 상대 순위 | **광장(plaza) 코호트 안** relative percentile ≥ `CRAWL_MIN_POPULARITY_PCT`(기본 **0.50**). 소스 배치 전체가 아니라 COUPLE/MARRIED/…별로 순위 |
| COMMENT | 인기 POST(이번에 통과했거나 DB에 `popularity_pct≥threshold`)의 자식만 저장 |

`UNRANKED`(지표 없음) 글은 코퍼스에 넣지 않는다. 사연 선별도 인기 보장 코퍼스를 전제로 한다.

### 크롤 광장 분류 (로컬 분류기, LLM 없음)

저장 `category`는 보드명을 게이트로 쓰지 않는다. `plaza_classifier`가 제목+본문으로 점수를 매긴다.

| 규칙 | 내용 |
|---|---|
| 가중치 | 제목 히트 ×**3**, primary 키워드 ×**2**, supporting ×**1**. 남편/아내/시댁 등 spouse 보너스는 MARRIED에만 (이중 가산 없이) |
| 채널 힌트 | Natepan 테마 plaza · Blind 보드 → `channel_hint`로 해당 광장에 **작은 가산(+1, Phase3 약화)** |
| Natepan 랭킹 | 섹션이 연애가 아니어도 항상 분류. 섹션명에 「연애」가 있으면 `channel_hint=COUPLE`만 |
| Natepan 테마 | `20027` **임신/출산/육아** → `channel_hint=MARRIED` (다른 MARRIED 채널과 같은 페이지네이션) |
| Blind | 보드 3개만 (결혼생활 / 썸·연애 / 회사생활). 힌트는 MARRIED/COUPLE/WORK. **저장은 광장 enum** — 세 보드에서도 FAMILY/FRIEND/OTHER가 나올 수 있음. 레거시 `romance`/`marriage`/`workplace` 행은 claim 매핑이 계속 인식 |
| FAMILY | 본가·부모·형제·조부모. 시댁/시집/친정·육아 인접은 MARRIED 쪽. **2026-08-22 이후: AI 생성 비활성화** (아래 FAMILY 흡수 참조) |

기존 crawl 행 재라벨: `ai-user/tools/reclassify_example_bank_categories.py` (기본 **dry-run**, `--env prod|dev`, `--apply`로 `example_bank` UPDATE). **고신뢰만** 이동 — 1등 점수 ≥ 2×2등 **그리고** 1등 ≥ 6. 약함/동점은 그대로(OTHER 유지). LLM 없음. `posts` 재분류는 별도 `reclassify_post_categories.py`.

#### 광장 분류 개선 이력 (2026-08-22, 소스 재고 파이프라인 안정화)

다섯 건의 커밋이 example_bank 제목 추출 버그부터 AI 생성 광장 라우팅까지 근본 원인을 하나씩 복구했다.

**①** (`f2f8f8f3` 제목 추출 버그): Natepan ID 범위 크롤이 제목 선택자 오류로 모든 글의 제목을 None으로 저장했다. 실제 페이지는 `og:title` 메타태그 또는 클래스 없는 `<h1>`을 쓰는데 선택자(`h2.tit`, `dd.tit h2` 등)가 맞지 않았다. 재고 기준 OTHER 432/432(100%), COUPLE 119/287(41%), MARRIED 142/426(33%), WORK 52/234(22%)가 무제목이었으며, plaza_classifier가 제목에 ×3 가중치를 주므로 분류 정확도가 구조적으로 훼손됐다. 수정 후: og:title 우선, 클래스 없는 h1 폴백, 레거시 선택자 유지.

**②** (`ebd7d0d5`, `e8a8a76d` 소급 복구): 기존 4,180건 무제목 행을 원본 URL로 재조회해 제목만 채워 **3,590건 복구(86%)**했다. 실행 결과: POST 제목 있는 행 2,201 → 5,301 / 6,909. 2차 버그(SQL 주석의 퍼센트 기호 `9.8%가`가 파이썬 파라미터 바인딩 자리표시자로 해석돼 claim 전체가 400 오류) 수정 완료.

**③** (`b483f8d1`, `0a61c983` 재고 사전조회): nightly fill이 재고 0인 (source, plaza) 조합에도 claim을 시도해 CLAIM_EMPTY로 헛도는 현상 수정. blind는 결혼·연애·직장 게시판만 크롤해 FAMILY/FRIEND 재고가 0인데 계속 요청했다. 신규 `GET /examples/available-count` 엔드포인트가 같은 술어로 재고를 확인 후 0인 조합은 skip(`SKIP_NO_INVENTORY` 로그). 즉시 발견된 성능 버그: available-count가 `COUNT(*)`로 모든 행을 스캔해 blind+MARRIED 98.6초, natepan+MARRIED 40.5초 소요 → **EXISTS로 변경해 0.31초로 단축**.

**④** (`41857752` 잡담 필터): 발행 글의 9.8%(275건 중 27건)가 갈등 사연이 아닌 정보 전달 글(역사 기사 예: "덕혜옹주가…" 제목의 '친구'로 FRIEND로 claim돼 "덕혜옹주가 친구한테 한 고종 독살 얘기"로 각색). 넓은 필터들(제목 유무, 길이·품질 조건, 관계어/1인칭/동사 OR)은 진짜 사연의 절반까지 버렸다. 대신 **오탐 0%인 좁은 규칙 채택**: 전언 형식이면서 1인칭 경험 서술이 전혀 없는 글만 제외(`라고 함/다고 알려 AND NOT 했는데/내가`). 실측 확실한 사연 200건 0%, OTHER 300건 5%로 확실한 사연 손실 0.

**⑤** (`ac2dce59` 채널 힌트 왜곡): channel_hint(크롤 게시판 → MARRIED 등)의 보너스가 2점이라 약한 점수 구간에서 내용을 눌렀다. 결혼생활 게시판에 올라온 친구·가족 갈등 글이 MARRIED로 흡수되던 이유다. 수정: 힌트 보너스 2→1, MARRIED spouse 보너스는 제목만, 약한 동점은 COUPLE 대신 OTHER 선호, body spouse 키워드는 이미 primary 점수가 있을 때만 추가. 실측(1,500건): 힌트 MARRIED 115건 이동(COUPLE 37·WORK 29·FRIEND 25·FAMILY 24)이 모두 "내용이 가리키는 광장" 방향. FRIEND가 모든 힌트에서 상위 이동처인 것이 재고 5까지 마른 원인.

**⑥** (`f5e1ec7a` FAMILY 흡수): 세 접근(채널 추가·재분류·분류기 개선)을 모두 시도했으나 FAMILY 재고를 늘리지 못했다(FAMILY ±0). prod 재고: MARRIED 411·OTHER 430·COUPLE 271·WORK 224·FAMILY 21·FRIEND 5. 코퍼스에 가족 갈등 사연이 실제로 없어 나이틀리 fill이 채울 수 없는 광장을 계속 요청했다. 환경변수 `AI_USER_FAMILY_PLAZA_ENABLED`(기본 false)로 끄면 페르소나 관심사에서 FAMILY를 필터해 OTHER로 재배치하고, 재고 사전조회에서도 FAMILY 조합 건너뛴다. 사용자 대면은 일절 변화 없음 — 검색 필터 '가족' 칩, 글쓰기 카테고리, 상세·프로필 라벨, 관리자 선택지 모두 유지. 발행된 26건도 그대로. 사용자는 가족 글을 쓰고 검색하되 AI만 그 광장을 채우지 않는다.

## Popular source claim (2026-08-05)

AI 예약 글의 **primary reconstruct source**는 topic RAG(`findSimilar`)가 아니라
인기 crawl POST를 soft-claim한다. 구현: `ai-user/learning/app/services/source_claim.py`,
orchestrator 클라이언트 `AiLearningClient.claimPopularSource` /
`commitSource` / `releaseSource`.

### `POST /examples/claim-popular-source`

Body (camelCase): `{ source: "blind"|"natepan", reservationKey, reserveUntil, windowDays?: 14, expandDays?: 30, category?: "COUPLE"|"MARRIED"|"FRIEND"|"FAMILY"|"WORK"|"OTHER", excludeExampleIds?: number[] }`

| 규칙 | 내용 |
|---|---|
| 후보 | `content_type=POST`, `source_url IS NOT NULL`, `popularity_pct IS NOT NULL`, source ∈ {blind,natepan} |
| **카테고리 스코프** | `category`(광장 enum) 지정 시 해당 광장 매핑만. Blind 레거시=`romance`/`marriage`/`workplace` + 신규 광장 enum(FAMILY/FRIEND 포함). Natepan=광장 enum. **미지정 시 필터 없음(레거시)**. 잘못된 enum → 400 |
| 요청 제외 | `excludeExampleIds` — LLM/세이프가드 실패한 example id. 다음 인기 글을 claim |
| 순위 | `popularity_pct DESC` (NULL last) |
| 창 | `created_at` 기준 **14일**. 없으면 **한 번** 30일로 확장. 그래도 없으면 이 요청은 empty. **한 claim 호출이 source를 바꾸지는 않음** — 새벽 배치는 다른 source/plaza/persona로 **새 claim**을 재시도한다 ([operations.md](../60-runtime/operations.md) §8) |
| 영구 제외 | 같은 `source_url`을 가진 **형제** `example_bank` 행이 `posts.source_example_id`로 쓰였거나 `example_source_reservations.status='COMMITTED'` |
| soft 제외 | 형제 행 중 `status='SOFT'` AND `reserve_until > NOW(3)` |
| 동시성 | claim 시 동일 `source_url` 가족 전체를 `FOR UPDATE`로 잠그고 같은 `reservationKey`로 SOFT 예약. commit/release도 key 가족 단위 |
| 응답 | ExampleItem-like `{id, content, source, title, sourceUrl, score≈popularity_pct, category}` 또는 `{"status":"empty"}` / null |

### 예약 생명주기

| 단계 | 엔드포인트 | 효과 |
|---|---|---|
| claim | `/examples/claim-popular-source` | `example_source_reservations`에 SOFT 행 |
| commit | `/examples/commit-source` `{exampleId, reservationKey}` | SOFT→COMMITTED (영구) |
| release | `/examples/release-source` | SOFT 행 삭제 (COMMITTED/missing은 no-op) |

크롤 `SOURCES` budget(natepan 1500 · blind 500)은 **변경하지 않는다**.

## 말투 강화

`/strengthen/*`는 DB의 예시를 source별로 모아 voice profile을 갱신한다.

- `persona_strengthener.py`는 `LLM_AI_USER_URL`을 직접 호출한다.
- compose는 dev/prod 모두 이 값을 ai-user llm 컨테이너로 override한다.
- 운영상 이 경로는 `ai-user/docs/personas/profiles/*/voice.yml`를 수정할 수 있다.
- refusal/error 판정은 하드코딩 목록이 아니라 `ai-user/learning/app/services/llm_error_signatures.py`
  로더를 거쳐 `docs/shared/policies/llm-error-signatures.json` SSOT를 읽는다(2026-09-03 리팩터,
  기존 `_looks_like_llm_error`/`LLM_ERROR_SIGNATURES` 하드코딩 목록 폐기). 판정 결과가
  `voice_profile`에 섞이지 않게 막는 동작 자체는 그대로다 — 상세: `.claude/rules/llm-safety.md` §2.
- **(2026-09 persona-diversity-v4, 예정 — Phase 2 확인 필요)** `persona_strengthener.py`는
  신규 정체성 축(`age_years`·`gender`·`marital`·`style_axes`)을 **덮어쓰지 않는다** — 강화
  대상은 `voice_profile`의 문체 필드(`lexicon`·`reply_style`·`comment_style`·`general_style`)로
  한정하고, `personas` 테이블의 정체성 컬럼은 WP1 시더/팩토리만 쓴다. 표절 방어
  `services/ngram_guard.py`(위 §)와 동일한 n-gram 겹침 로직이 orchestrator 쪽에도 Java로
  포팅되어(`docs/_active/persona-diversity-v4.md` 계약 3, 게이트 b 8-gram Jaccard) 런타임
  생성 단계에서도 검사한다 — 이 문서의 Python 구현과 Java 포팅본은 별도 유지보수 대상이다.

### `lexicon`/`general_style`은 이제 오케스트레이터 전용 (persona-diversity-v4 / WP1, 2026-09-05)

`update_persona_profiles()`는 **no-op**(항상 0 반환, DB에 쓰지 않음)으로 바뀌었다. `lexicon`·
`writing_quirks`·`general_style`·`post_style`·`comment_style`·`reply_style`은 이제
오케스트레이터의 `PersonaProfileRegenerator`(V22 신원 축 재생성, [orchestrator.md](orchestrator.md)
§ Persona 신원 축)가 유일한 쓰기 경로다. 이 크롤 강화 루프가 같은 필드를 계속 덮어썼던 것이
voice_type당 소수 lexicon으로 수렴하던 문제(3종 수렴)의 직접 원인이었다.

- `strengthen_all()`은 이제 `expand_persona_example_pools()`(example_comments/example_replies
  풀 확장, 페르소나별 상한 12/8)만 호출한다. `analyze_style_with_llm`/`get_examples_by_source`는
  더 이상 `strengthen_all` 경로에서 쓰이지 않는다(불필요한 LLM 호출 제거).
- `POST /strengthen/{voice_type}`(admin 단건 트리거, `app/api/strengthen.py`)는 여전히
  `update_persona_profiles`를 직접 호출하지만 이제 no-op이라 안전하다 — 하위 호환을 위해 시그니처만 유지.

## daily topics

`/topics/today`는 오늘 날짜의 `daily_topic` 행을 `used_count ASC, RAND()`로 돌려준다.

- orchestrator는 category별 seed를 가져와 post topic 후보로 사용한다.
- 사용한 seed는 `/topics/{id}/use`로 카운트만 올린다.

## orchestrator와의 관계

| 설정 | 어디서 쓰나 | 실제 의미 |
|---|---|---|
| `AI_LEARNING_ENABLED` | orchestrator `AiLearningClient` | orchestrator가 search/save/topics 호출할지 결정 |
| `AI_LEARNING_CRAWL_ENABLED` | learning APScheduler 일일 crawl | `true`면 learning이 **02:00 KST**에 natepan/blind 크롤. orchestrator 컨테이너는 compose에서 `false` 고정(중복 트리거 방지) |
| learning scheduler | learning app startup | `AI_LEARNING_ENABLED=true` AND `AI_LEARNING_CRAWL_ENABLED=true` 둘 다여야 크론 job이 등록됨(`init_scheduler()`가 둘 다 확인 후 return None). 2026-06-24~07-30 `AI_LEARNING_CRAWL_ENABLED=false`로 방치되어 36일간 무크롤 사고 발생(WO-CRAWL-01) — env flag 하나로 완전히 꺼질 수 있으니 admin 크롤 신선도 배지로 감시한다. |

## 운영 메모

- host에서 직접 테스트 가능한 AI-user API는 현재 dev 기준 `localhost:8099` 하나다.
- learning을 완전히 멈추려면 container stop 또는 코드 수정이 필요하다. env flag만으로 scheduler는 꺼지지 않는다.
- 품질과 dedup은 현재 `example_bank`의 핵심 방어선이다. 광장 분포 실험은 이 DB 상태에 강하게 의존한다.
