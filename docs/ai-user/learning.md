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

## example_bank 동작

### 저장

- `/examples/save`는 본문 512자까지만 임베딩한다.
- register classifier 결과를 `casual`, `polite`, `mixed` 축으로 저장한다.
- orchestrator 저장 훅은 `AI_LEARNING_ENABLED=true`일 때만 실행된다.

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
- 결과는 `crawl_log`에 남는다.

### 현재 scheduler source budget

`ai-user/learning/app/scheduler.py` 기준:

| source | limit |
|---|---:|
| `natepan` | `1500` |
| `blind` | `240` |
| 나머지 (`naver`, `daum`, `dcinside`, `bobaedream`, `fmkorea`, `theqoo`, `clien`, `ppomppu`, `ruliweb`, `mlbpark`) | `0` |

### 현재 스케줄

| 작업 | UTC | KST |
|---|---|---|
| daily crawl + strengthen + topic synthesis | `18:00` | `03:00` |
| standalone strengthen + topic synthesis | `20:00` | `05:00` |

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
| `AI_LEARNING_CRAWL_ENABLED` | orchestrator `CrawlerTriggerScheduler` | orchestrator가 별도 crawl trigger를 날릴지 결정 |
| learning scheduler | learning app startup | 위 두 flag와 무관하게 현재 코드에서는 항상 등록 |

## 운영 메모

- host에서 직접 테스트 가능한 AI-user API는 현재 dev 기준 `localhost:8099` 하나다.
- learning을 완전히 멈추려면 container stop 또는 코드 수정이 필요하다. env flag만으로 scheduler는 꺼지지 않는다.
- 품질과 dedup은 현재 `example_bank`의 핵심 방어선이다. 광장 분포 실험은 이 DB 상태에 강하게 의존한다.
