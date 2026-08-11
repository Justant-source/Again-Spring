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
(백업: `/home/justant/backups/prod-pre-corpus-unify-20260801-163820.sql`).
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
| 절대 하한 | source별 floor (natepan: view≥50 ∨ like≥3 ∨ comment≥5 등) |
| 상대 순위 | 배치 내 per-source relative percentile ≥ `CRAWL_MIN_POPULARITY_PCT`(기본 **0.50**) |
| COMMENT | 인기 POST(이번에 통과했거나 DB에 `popularity_pct≥threshold`)의 자식만 저장 |

`UNRANKED`(지표 없음) 글은 코퍼스에 넣지 않는다. 사연 선별도 인기 보장 코퍼스를 전제로 한다.

## Popular source claim (2026-08-05)

AI 예약 글의 **primary reconstruct source**는 topic RAG(`findSimilar`)가 아니라
인기 crawl POST를 soft-claim한다. 구현: `ai-user/learning/app/services/source_claim.py`,
orchestrator 클라이언트 `AiLearningClient.claimPopularSource` /
`commitSource` / `releaseSource`.

### `POST /examples/claim-popular-source`

Body (camelCase): `{ source: "blind"|"natepan", reservationKey, reserveUntil, windowDays?: 14, expandDays?: 30 }`

| 규칙 | 내용 |
|---|---|
| 후보 | `content_type=POST`, `source_url IS NOT NULL`, `popularity_pct IS NOT NULL`, source ∈ {blind,natepan} |
| 순위 | `popularity_pct DESC` (NULL last) |
| 창 | `created_at` 기준 **14일**. 없으면 **한 번** 30일로 확장. 그래도 없으면 empty (다른 source로 폴백 금지) |
| 영구 제외 | 같은 `source_url`을 가진 **형제** `example_bank` 행이 `posts.source_example_id`로 쓰였거나 `example_source_reservations.status='COMMITTED'` |
| soft 제외 | 형제 행 중 `status='SOFT'` AND `reserve_until > NOW(3)` |
| 동시성 | claim 시 동일 `source_url` 가족 전체를 `FOR UPDATE`로 잠그고 같은 `reservationKey`로 SOFT 예약. commit/release도 key 가족 단위 |
| 응답 | ExampleItem-like `{id, content, source, title, sourceUrl, score≈popularity_pct}` 또는 `{"status":"empty"}` / null |

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
