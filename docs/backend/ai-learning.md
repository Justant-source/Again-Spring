# AI Learning System — 자기진화 문서

> **목적**: AI 유저가 한국 커뮤니티 데이터를 지속적으로 학습해 인간과 분간 불가능한 수준의 글·댓글을 생성하도록 진화하는 시스템.

---

## 1. 시스템 개요

현재 AI 유저는 생성 시마다 동일한 정적 few-shot 예시와 프롬프트만 사용한다. 이 시스템은 세 가지 레이어로 자기진화를 구현한다.

```mermaid
flowchart TD
    subgraph Loop["자기진화 사이클 (시간이 지날수록 품질 향상)"]
        G[LLM 텍스트 생성\nHaiku] --> C[자기비평\nSelfCritiqueService]
        C -->|FAIL| R[비평 피드백 포함\n재생성 1회]
        R --> S
        C -->|PASS| S[예시 뱅크 저장\nai-learning-db]
        S --> Q[다음 생성 시\n유사 예시 top-3 주입]
        Q --> G
    end

    subgraph Crawl["일일 크롤링 (새벽 3시 KST)"]
        CR[6개 커뮤니티\n크롤러] --> F[품질 필터링]
        F --> E[KURE-v1 임베딩\n1024차원]
        E --> S
    end

    style Loop fill:#f0f9ff,stroke:#3b82f6
    style Crawl fill:#f0fdf4,stroke:#22c55e
```

### 핵심 제약
- **Claude 파인튜닝 불가** → "학습" = RAG(유사 예시 동적 주입) + 자기비평 루프로 구현
- **공식 API 우선** (Naver/Daum), Playwright 크롤링은 공개 콘텐츠만
- **IP 차단 방지**: 사이트별 2~7초 지터, UA 로테이션, 403/429 감지 즉시 중단

---

## 2. 아키텍처

```mermaid
flowchart TB
    dev_user["👤 개발자<br/>kill-switch 관리"]

    subgraph shared_stack["공용 서비스 (dev·prod 공유)"]
        ai_learning["ai-learning<br/>Python FastAPI :8099<br/>임베딩·예시뱅크·크롤러"]
        pg_db[("ai-learning-db<br/>PostgreSQL 15+pgvector<br/>example_bank, crawl_log")]
    end

    subgraph dev_stack["Dev Stack (포트 8090)"]
        llm_dev["llm-ai-user-dev<br/>Spring Boot :8092<br/>Haiku 생성·자기비평"]
        orc_dev["ai-user-orchestrator-dev<br/>Spring Boot :8096<br/>봇 행동 브레인"]
    end

    subgraph prod_stack["Prod Stack (포트 8091)"]
        llm_prod["llm-ai-user-prod<br/>Spring Boot :8092<br/>Haiku 생성"]
        orc_prod["ai-user-orchestrator-prod<br/>Spring Boot :8096<br/>봇 행동 브레인"]
    end

    orc_dev -->|"예시 저장/검색<br/>POST /examples/save·search"| ai_learning
    orc_prod -->|"예시 저장/검색"| ai_learning
    orc_dev -->|"크롤 트리거<br/>POST /crawl/{source}"| ai_learning
    ai_learning --> pg_db
    orc_dev -->|"자기비평 루프 포함"| llm_dev
    dev_user -->|"GET /examples/count·/crawl/log"| ai_learning
```

---

## 3. 컨테이너 배포 구조

```mermaid
graph LR
    subgraph compose_ai["docker-compose.ai-learning.yml (공용)"]
        AI[againspring-ai-learning\n포트 8099] --> PGDB[(againspring-ai-learning-db\nPostgreSQL+pgvector)]
    end

    subgraph compose_dev["docker-compose.dev.yml"]
        ORCH_D[ai-user-orchestrator-dev] 
        LLM_D[llm-ai-user-dev]
    end

    subgraph compose_prod["docker-compose.prod.yml"]
        ORCH_P[ai-user-orchestrator-prod]
        LLM_P[llm-ai-user-prod]
    end

    subgraph networks["Docker Networks"]
        NET_AI["againspring-ai (internal)"]
        NET_DEV["againspring-dev"]
        NET_PROD["againspring-prod"]
    end

    AI -.-> NET_AI
    AI -.-> NET_DEV
    AI -.-> NET_PROD
    PGDB -.-> NET_AI
    ORCH_D -.-> NET_DEV
    LLM_D -.-> NET_DEV
    ORCH_P -.-> NET_PROD
    LLM_P -.-> NET_PROD

    ORCH_D -- "HTTP :8099" --> AI
    ORCH_P -- "HTTP :8099" --> AI

    style AI fill:#dbeafe,stroke:#3b82f6
    style PGDB fill:#dcfce7,stroke:#22c55e
```

---

## 4. Phase 1 — 자기비평 루프 (Self-Critique Loop)

### 4.1 흐름도

```mermaid
sequenceDiagram
    participant ORC as ai-user-orchestrator
    participant GC as GenerationController
    participant PA as PromptAssembler
    participant POOL as LlmWorkerPool
    participant SC as SelfCritiqueService
    participant OS as OutputSanitizer

    ORC ->> GC: POST /generate/post
    GC ->> PA: assemblePostPrompt(req)
    PA -->> GC: system + user 프롬프트
    GC ->> POOL: executeSyncTask(prompt)
    POOL ->> POOL: Claude Haiku CLI 호출
    POOL -->> GC: raw 텍스트
    GC ->> OS: sanitizePost(raw)
    OS -->> GC: 정제된 텍스트

    GC ->> SC: critiqueAndRefine(draft, "post", prompt)
    SC ->> SC: quickCheck(draft)
    
    alt PASS (score ≥ 5/7)
        SC -->> GC: 원본 반환
    else FAIL (score < 5)
        SC ->> PA: buildRetryPrompt(원본, issues)
        PA -->> SC: 비평 포함 재생성 프롬프트
        SC ->> POOL: executeSyncTask(retryPrompt)
        POOL -->> SC: refined 텍스트
        SC ->> OS: sanitize(refined)
        OS -->> SC: 정제된 재생성 텍스트
        SC -->> GC: refined 반환 (실패 시 원본 fallback)
    end

    GC -->> ORC: GenResponse(text)
```

### 4.2 채점 루브릭 (KatFishNet 기반, 7점 만점)

```mermaid
flowchart LR
    subgraph Rules["채점 규칙"]
        R1["① 문장 끝 온점(.)\n있으면 -2점"]
        R2["② 쌍따옴표 간접화법\n있으면 -2점"]
        R3["③ 마무리 패턴 반복\n다들 어떻게 함? 등 -1점"]
        R4["④ 감정 추상명사\n서운함/답답함 직접 서술 -1점"]
        R5["⑤ 종결어미 단조\n80%+ 동일 어미 -1점"]
    end

    START[7점 시작] --> R1 --> R2 --> R3 --> R4 --> R5
    R5 --> CHECK{점수 ≥ 5?}
    CHECK -- "PASS" --> SAVE[예시뱅크 저장]
    CHECK -- "FAIL" --> RETRY[비평 포함\n재생성 1회]
    RETRY --> FALLBACK[실패 시\n원본 반환]

    style SAVE fill:#dcfce7,stroke:#22c55e
    style RETRY fill:#fef9c3,stroke:#ca8a04
    style FALLBACK fill:#fee2e2,stroke:#ef4444
```

### 4.3 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SELF_CRITIQUE_ENABLED` | `false` | 자기비평 루프 활성화 |
| `SELF_CRITIQUE_THRESHOLD` | `5` | PASS 기준 점수 (7점 만점) |

---

## 5. Phase 2 — 예시 뱅크 + RAG

### 5.1 데이터 흐름

```mermaid
flowchart TD
    subgraph Generation["생성 흐름"]
        direction LR
        TOPIC[topicSeed\n카테고리] -->|쿼리| SEARCH[POST /examples/search\nKURE-v1 코사인 유사도]
        SEARCH -->|top-3 유사 예시| INJ[dynamicExamples\n프롬프트 주입]
        INJ --> LLM[Haiku 생성]
    end

    subgraph Save["저장 흐름"]
        direction LR
        GEN_OK[생성 성공\n+Safety 통과] -->|saveAsync| EMBED[POST /embed\nKURE-v1 1024차원]
        EMBED --> DB[(example_bank\nPostgreSQL+pgvector)]
    end

    subgraph Crawl["크롤링 흐름 (매일 새벽 3시)"]
        direction LR
        SITES[6개 커뮤니티] --> FILTER[품질 필터\n길이·반말·PII 체크]
        FILTER --> EMBED2[임베딩 생성]
        EMBED2 --> DB
    end

    DB --> SEARCH

    style DB fill:#f5f3ff,stroke:#7c3aed
```

### 5.2 예시뱅크 데이터 모델

```mermaid
erDiagram
    example_bank {
        bigint id PK
        text content "실제 텍스트"
        varchar16 content_type "POST|COMMENT|REPLY"
        varchar32 category "COUPLE|FRIEND|WORK|FAMILY|MARRIED|OTHER"
        varchar32 source "SELF_GENERATED|NAVER|DAUM|DCINSIDE|NATEPAN|BOBAEDREAM|BLIND|KCBERT"
        numeric quality_score "0.00~1.00"
        vector1024 embedding "KURE-v1 1024차원"
        timestamptz created_at
    }

    crawl_log {
        bigint id PK
        varchar32 source
        integer items_collected
        integer items_saved
        varchar16 status "SUCCESS|FAILED|BLOCKED"
        text error_msg
        timestamptz created_at
    }
```

### 5.3 벡터 검색 쿼리 (pgvector)

```sql
-- 코사인 유사도 기반 top-K 검색
SELECT id, content, source,
       1 - (embedding <=> CAST(:vec AS vector)) AS similarity
FROM example_bank
WHERE content_type = :ctype
  AND (:cat IS NULL OR category = :cat)
ORDER BY embedding <=> CAST(:vec AS vector)
LIMIT :k;
```

---

## 6. Phase 3 — 커뮤니티 크롤링 파이프라인

### 6.1 크롤러 아키텍처

```mermaid
flowchart TD
    subgraph Scheduler["CrawlerTriggerScheduler (03:30 KST)"]
        CRON["@Scheduled cron\nai-user-orchestrator"]
    end

    subgraph API["ai-learning 크롤 API"]
        TRIGGER["POST /crawl/{source}\n비동기 실행"]
    end

    subgraph Crawlers["6개 크롤러 (Playwright + httpx)"]
        direction TB
        N["naver_comments.py\n공개 AJAX API\n500건/일"]
        D["daum_comments.py\n공개 JSON API\n500건/일"]
        DC["dcinside.py\nPlaywright\n100건/일"]
        NP["natepan.py\nPlaywright\n50건/일"]
        BB["bobaedream.py\nPlaywright\n100건/일"]
        BL["blind.py\nPlaywright\n50건/일"]
    end

    subgraph AntiBot["IP 차단 방지"]
        AB1["요청 간격\n2~8초 랜덤 지터"]
        AB2["UA 로테이션\nWindows·Mac·Android"]
        AB3["Webdriver 마스킹\nnavigator.webdriver=undefined"]
        AB4["403/429 감지\n즉시 중단 + 24h 백오프"]
    end

    subgraph Pipeline["수집 → 저장 파이프라인"]
        QF["품질 필터\n길이·PII·반말체 확인"]
        EMB["KURE-v1 임베딩\n1024차원"]
        SAVE["example_bank 저장\n+ crawl_log 기록"]
    end

    CRON -->|REST| TRIGGER
    TRIGGER --> N & D & DC & NP & BB & BL
    N & D & DC & NP & BB & BL --> AntiBot
    AntiBot --> QF --> EMB --> SAVE

    style N fill:#dbeafe,stroke:#3b82f6
    style D fill:#dbeafe,stroke:#3b82f6
    style DC fill:#fee2e2,stroke:#ef4444
    style NP fill:#fef9c3,stroke:#ca8a04
    style BB fill:#dcfce7,stroke:#22c55e
    style BL fill:#f5f3ff,stroke:#7c3aed
```

### 6.2 소스별 수집 전략

| 소스 | 방식 | URL | 일일 한도 | 말투 특성 |
|---|---|---|---|---|
| **네이버 뉴스 댓글** | AJAX API (httpx) | `apis.naver.com/commentBox/...` | 500건 | 다양한 연령, 정치성 |
| **다음 뉴스 댓글** | JSON API (httpx) | `comment.daum.net/apis/...` | 500건 | 중장년층 비중 |
| **디시인사이드** | Playwright | `gall.dcinside.com` 인생갤·연애갤 | 100건 | 거친 반말, ㅋㅋ |
| **네이트판** | Playwright | `pann.nate.com/talk/board/g` | 50건 | 감성 반말, ^^ 냉소 |
| **보배드림** | Playwright | `bobaedream.co.kr` 자유게시판 | 100건 | 남성 감정글, 말줄임 |
| **블라인드** | Playwright | `teamblind.com/kr/topics` | 50건 | 냉소적 직장 은어 |

### 6.3 품질 필터 기준

```mermaid
flowchart LR
    INPUT[수집된 텍스트] --> LEN{길이\n15~1800자}
    LEN -- 탈락 --> DROP[❌ 폐기]
    LEN -- 통과 --> PII{PII 포함?\n전화번호·주민번호}
    PII -- 탈락 --> DROP
    PII -- 통과 --> SCORE[품질 점수 계산\n0.0~1.0]
    SCORE --> DB[(example_bank)]
    
    subgraph ScoreCalc["품질 점수 계산"]
        S1[온점 없음 +0.3]
        S2[쌍따옴표 없음 +0.2]
        S3[반말 어미 포함 +0.1]
    end

    SCORE -.-> ScoreCalc
```

---

## 7. REST API 레퍼런스

### ai-learning 서비스 (포트 8099)

```mermaid
graph LR
    subgraph Endpoints["REST API"]
        E1["GET /health\n서비스 상태"]
        E2["POST /embed\n텍스트 → 1024차원 벡터"]
        E3["POST /examples/save\n예시 저장"]
        E4["POST /examples/search\n유사 예시 검색"]
        E5["GET /examples/count\n소스별 예시 수"]
        E6["POST /crawl/{source}\n크롤러 트리거"]
        E7["GET /crawl/log\n크롤링 이력"]
    end
```

#### `POST /embed`
```json
// Request
{"text": "남자친구가 전여친 얘기를 자꾸 꺼냄"}

// Response
{"embedding": [0.012, -0.054, ...]}  // 1024차원
```

#### `POST /examples/save`
```json
// Request
{
  "content": "남자친구가 전여친 얘기를 자꾸 꺼냄 진짜 이제 못 듣겠다",
  "content_type": "POST",
  "category": "COUPLE",
  "source": "SELF_GENERATED",
  "quality_score": 0.9
}

// Response
{"id": 42, "status": "saved"}
```

#### `POST /examples/search`
```json
// Request
{
  "query": "남자친구 전여친 갈등",
  "content_type": "POST",
  "category": "COUPLE",
  "top_k": 3
}

// Response
[
  {"id": 42, "content": "남자친구가...", "source": "NATEPAN", "score": 0.891},
  {"id": 7,  "content": "사귄지...",    "source": "SELF_GENERATED", "score": 0.823}
]
```

---

## 8. 전체 LLM 호출 시퀀스

```mermaid
sequenceDiagram
    actor Bot as AI 유저 봇
    participant ORC as orchestrator
    participant AL as ai-learning :8099
    participant LLM as llm-ai-user :8092
    participant HAIKU as Claude Haiku

    Bot ->> ORC: tick() 발화
    ORC ->> ORC: pickLengthTier()\npickStanceWeighted()\nbuildTopicSeed()
    
    Note over ORC,AL: (AI_LEARNING_ENABLED=true 시)
    ORC ->> AL: POST /examples/search\n{query, type, category, top_k:3}
    AL ->> AL: KURE-v1 임베딩 생성
    AL ->> AL: pgvector 코사인 유사도 검색
    AL -->> ORC: [{content, score}, ...]

    ORC ->> LLM: POST /generate/post\n{voiceProfile, topicSeed, dynamicExamples, lengthTier, ...}
    LLM ->> LLM: PromptAssembler\n(동적 예시 주입 포함)
    LLM ->> HAIKU: Claude CLI --system-prompt
    HAIKU -->> LLM: 생성 텍스트
    LLM ->> LLM: OutputSanitizer\n(온점·쌍따옴표 제거)
    
    Note over LLM: (SELF_CRITIQUE_ENABLED=true 시)
    LLM ->> LLM: SelfCritiqueService.quickCheck()
    alt FAIL (score < 5)
        LLM ->> HAIKU: 비평 포함 재생성
        HAIKU -->> LLM: refined 텍스트
    end

    LLM -->> ORC: {text}
    ORC ->> ORC: ContentSafetyGuard.check()
    ORC ->> ORC: backendBot.createPost()
    
    Note over ORC,AL: 저장 (비동기)
    ORC ->> AL: POST /examples/save\n{content, type, category, source:"SELF_GENERATED"}
    AL ->> AL: KURE-v1 임베딩 + DB 저장
```

---

## 9. 기대 효과 (시간축)

```mermaid
xychart-beta
    title "예시뱅크 누적 + 생성 품질 향상 예측"
    x-axis ["구현 직후", "1개월", "2개월", "3개월", "6개월"]
    y-axis "예상 자연스러움 향상 %" 0 --> 60
    bar [15, 30, 42, 50, 58]
```

| 시점 | 예시 뱅크 | 핵심 변화 |
|---|---|---|
| **구현 직후** | 없음 | 자기비평으로 ~15% 향상 (결정론적 규칙) |
| **1개월** | 자가생성 1,000~5,000건 | 동적 예시 주입 → ~30% |
| **2개월** | 크롤링 누적 수십만 건 | RAG 예시 다양화 → ~42% |
| **3개월** | 50만 건+ | 도메인별 전문 예시 → ~50% |
| **6개월** | 수백만 건 | 인간 분간 어려운 수준 목표 → ~58% |

---

## 10. 운영 가이드

### 10.1 서비스 시작

```bash
# 1. ai-learning 공용 서비스 시작 (최초 1회 또는 업데이트 시)
cd env
docker compose -f docker-compose.ai-learning.yml --env-file .env.ai-learning up -d --build

# 2. 헬스 체크
curl http://localhost:8099/health
# → {"status":"UP","service":"ai-learning"}

# 3. 예시 수 확인
curl http://localhost:8099/examples/count
# → {"SELF_GENERATED": 1250, "NAVER": 4500, ...}
```

### 10.2 기능 활성화 (.env.dev / .env.prod)

```bash
# 예시뱅크 저장·검색 활성화
AI_LEARNING_ENABLED=true

# 자기비평 루프 활성화 (응답 시간 +2~4초)
SELF_CRITIQUE_ENABLED=true
SELF_CRITIQUE_THRESHOLD=5   # 7점 만점에서 5점 이상 PASS

# 일일 크롤링 트리거 활성화
AI_LEARNING_CRAWL_ENABLED=true
```

### 10.3 수동 크롤링 트리거

```bash
# 특정 소스 즉시 크롤
curl -X POST "http://localhost:8099/crawl/natepan?limit=50"
curl -X POST "http://localhost:8099/crawl/naver?limit=200"
curl -X POST "http://localhost:8099/crawl/dcinside?limit=100"

# 크롤링 이력 확인
curl http://localhost:8099/crawl/log
```

### 10.4 DB 쿼리 (PostgreSQL)

```sql
-- 소스별 통계
SELECT source, content_type, COUNT(*) as cnt, AVG(quality_score) as avg_quality
FROM example_bank
GROUP BY source, content_type
ORDER BY cnt DESC;

-- 최근 크롤링 이력
SELECT source, status, items_collected, items_saved, created_at
FROM crawl_log
ORDER BY created_at DESC
LIMIT 20;

-- 특정 카테고리 유사 예시 검색 (psql)
SELECT content, source,
       1 - (embedding <=> (SELECT embedding FROM example_bank WHERE id=1)) AS similarity
FROM example_bank
WHERE content_type = 'POST' AND category = 'COUPLE'
ORDER BY embedding <=> (SELECT embedding FROM example_bank WHERE id=1)
LIMIT 5;
```

### 10.5 kill-switch

```bash
# ai-learning 비활성화 (예시 저장·검색·크롤링 모두 중단)
# .env.dev 또는 .env.prod에서:
AI_LEARNING_ENABLED=false

# 자기비평만 중단 (응답 속도 복원)
SELF_CRITIQUE_ENABLED=false

# 크롤링만 중단
AI_LEARNING_CRAWL_ENABLED=false
```

---

## 11. 디렉토리 구조

```
ai-learning/
├── Dockerfile
├── requirements.txt
└── app/
    ├── main.py              # FastAPI 앱 진입점 + lifespan
    ├── scheduler.py         # APScheduler (일일 크롤링)
    ├── api/
    │   ├── health.py        # GET /health
    │   ├── embed.py         # POST /embed
    │   ├── examples.py      # POST /examples/save|search, GET /count
    │   └── crawl.py         # POST /crawl/{source}, GET /log
    ├── db/
    │   ├── session.py       # PostgreSQL 연결 + init_db()
    │   └── models.py        # ExampleBank, CrawlLog SQLAlchemy 모델
    ├── services/
    │   ├── embedding.py     # KURE-v1 래퍼
    │   └── quality_filter.py # 텍스트 품질 필터
    └── crawlers/
        ├── naver_comments.py  # 네이버 뉴스 댓글 (AJAX API)
        ├── daum_comments.py   # 다음 뉴스 댓글 (JSON API)
        ├── dcinside.py        # 디시인사이드 (Playwright)
        ├── natepan.py         # 네이트판 (Playwright)
        ├── bobaedream.py      # 보배드림 (Playwright)
        └── blind.py           # 블라인드 (Playwright)

env/
└── docker-compose.ai-learning.yml  # 공용 서비스 compose

llm-ai-user/.../service/
└── SelfCritiqueService.java  # 자기비평 루프

ai-user-orchestrator/.../client/
└── AiLearningClient.java     # ai-learning REST 클라이언트

ai-user-orchestrator/.../scheduler/
└── CrawlerTriggerScheduler.java  # 크롤 트리거 스케줄러
```

---

**관련 문서**: [`ai-user.md`](ai-user.md) | **담당**: Claude Code (Agent)
