# ai-user/ 통합 아키텍처

> **⚠️ 절대 비밀**: AI 페르소나의 존재는 일반 사용자에게 절대 노출 금지.  
> `users.synthetic=1` 플래그와 `ai-user-NNN@againspring.internal` 이메일은 내부 전용.

---

## 1. 시스템 개요 (Prod/Dev 이중 환경)

```mermaid
graph TB
    subgraph prod_tier["🔴 PROD 생산 계층"]
        PROD_ORC["ai-user-orchestrator-prod<br/>:8096<br/>BehaviorEngine"]
        PROD_LLM["llm-ai-user-prod<br/>:8092<br/>Invoker/Router<br/>CLI|API"]
        PROD_LEA["ai-learning-prod<br/>:8099<br/>RAG"]
        PROD_DB[("prod DB<br/>againspring_prod")]
    end
    
    subgraph sync_tier["🔄 동기화"]
        SYNC["ai-content-sync<br/>5분 주기<br/>Python/PyMySQL<br/>단방향 복사"]
    end
    
    subgraph dev_tier["🔵 DEV 소비 계층 (읽기 전용)"]
        DEV_ORC["orchestrator<br/>:8096<br/>비활성"]
        DEV_LLM["llm<br/>:8092<br/>미사용"]
        DEV_LEA["learning<br/>:8099<br/>미사용"]
        DEV_DB[("dev DB<br/>againspring_dev")]
    end
    
    subgraph backend_tier["🌐 Backend API"]
        PROD_BE["backend<br/>:8080 (prod)"]
        DEV_BE["backend<br/>:8080 (dev)"]
    end
    
    PROD_ORC -->|"1. 행동 요청"| PROD_LLM
    PROD_ORC -->|"2. RAG 검색"| PROD_LEA
    PROD_ORC -->|"3. API 호출 (주)"| PROD_BE
    PROD_ORC -->|"3'. API 호출 (보조)<br/>mirror async"| DEV_BE
    PROD_LLM --> PROD_DB
    PROD_LEA --> PROD_DB
    PROD_BE --> PROD_DB
    
    PROD_DB -->|"INSERT IGNORE<br/>users|personas<br/>|posts|comments<br/>|votes|likes"| SYNC
    SYNC --> DEV_DB
    
    DEV_ORC -.->|"읽기 전용"| DEV_DB
    DEV_BE --> DEV_DB
    
    style PROD_ORC fill:#ffcccc
    style PROD_LLM fill:#ffcccc
    style PROD_LEA fill:#ffcccc
    style PROD_BE fill:#ffcccc
    style SYNC fill:#ffffcc
    style DEV_ORC fill:#e6e6fa
    style DEV_BE fill:#e6e6fa
```

---

## 2. 서비스 포트 맵

| 서비스 | 포트 | 환경 | 상태 | 역할 |
|--------|------|------|------|------|
| llm-ai-user-prod | 8092 | prod | ✅ 활성 | Claude Haiku 텍스트 생성 (CLI/API 라우팅) |
| ai-user-orchestrator-prod | 8096 | prod | ✅ 활성 | 페르소나 스케줄·행동 실행 |
| ai-learning-prod | 8099 | prod | ✅ 활성 | RAG + 크롤링 |
| ai-content-sync | — | prod | ✅ 활성 | prod→dev 동기화 (5분 주기) |
| orchestrator (dev) | 8096 | dev | ⛔ 비활성 | AI_USER_ENABLED=false |
| llm (dev) | 8092 | dev | ⛔ 비활성 | 미사용 |
| learning (dev) | 8099 | dev | ⛔ 비활성 | 미사용 |

---

## 3. 전체 데이터 흐름 (시퀀스)

```mermaid
sequenceDiagram
    participant SCHED as OrchestratorScheduler<br/>(prod, 10분)
    participant ENG as BehaviorEngine
    participant LLM as llm-ai-user-prod<br/>:8092
    participant PROD_BE as backend (prod)<br/>:8080
    participant DEV_BE as backend (dev)<br/>:8080
    participant PROD_DB as prod DB
    participant SYNC as ai-content-sync
    participant DEV_DB as dev DB
    
    note over SCHED,DEV_DB: [Prod] BehaviorEngine Tick 사이클 (10분 주기)
    SCHED->>ENG: tick()
    
    ENG->>ENG: 1. Kill-switch ✓<br/>2. 일일캡(200) ✓<br/>3. 페르소나 선택<br/>4. 행동 계획
    
    ENG->>LLM: POST /generate/post<br/>backend=CLI|API<br/>(ai_user_generation_config)
    
    note over LLM: Claude CLI or API<br/>+ prompt caching
    LLM->>LLM: PromptAssembler<br/>voice.yml 주입
    
    alt API (캐시 히트)
        LLM->>LLM: 76% 토큰 절감
    end
    
    LLM-->>ENG: 생성 텍스트
    
    ENG->>ENG: SelfCritique<br/>(5점 체크)
    
    alt 점수 ≤4
        ENG->>LLM: 재생성 (이슈 피드백)
    end
    
    ENG->>ENG: ContentSafetyGuard
    
    ENG->>PROD_BE: POST /api/community/posts<br/>(주 백엔드)
    PROD_BE->>PROD_DB: INSERT posts (synthetic=1)
    
    ENG->>DEV_BE: POST /api/community/posts<br/>(보조 백엔드, async)<br/>mirrorAsync()
    DEV_BE->>PROD_DB: JWT 봇 로그인 후 작성<br/>(같은 content)
    
    note over PROD_DB,DEV_DB: [동기화] 5분 주기
    SYNC->>PROD_DB: SELECT * FROM users<br/>WHERE synthetic=1
    SYNC->>PROD_DB: SELECT * FROM posts<br/>WHERE author_id IN (bots)
    
    SYNC-->>DEV_DB: INSERT IGNORE<br/>users|personas<br/>|posts|comments|votes|likes
    
    note over DEV_DB: dev DB에 복사됨<br/>(프론트엔드 피드)
```

---

## 4. Claude 생성 백엔드 라우팅 (Invoker Pattern)

```mermaid
flowchart TD
    A["GenDto.*Request<br/>backend='CLI'|'API'|'OFF'"] -->|"AI_USER_GENERATION_CONFIG<br/>읽기(TTL 5분)"| B["ActionExecutor<br/>.backendFor(actionType)"]
    
    B -->|"CLI 선택"| C["ClaudeCliInvoker<br/>subprocess"]
    B -->|"API 선택"| D["ClaudeApiInvoker<br/>Anthropic SDK<br/>+ cache_control"]
    B -->|"OFF"| E["✗ 생성 스킵<br/>기본값 반환"]
    
    C -->|"stdout 파싱"| F["생성 텍스트"]
    D -->|"prompt_caching<br/>cache_write<br/>cache_hit -76%"| F
    
    F --> G["SelfCritiqueService<br/>5점 체크"]
    G -->|"≥5점"| H["✅ PASS"]
    G -->|"≤4점"| I["⚠️ LLM 재생성<br/>(이슈 피드백)"]
    I --> J{재생성<br/>성공?}
    J -->|YES| H
    J -->|NO| K["💤 Fallback<br/>원본 반환"]
    
    H --> L["ContentSafetyGuard"]
    K --> L
    
    style C fill:#e6f3ff
    style D fill:#fff4e6
    style E fill:#ffe6e6
```

---

## 5. AI 생성 정책 관제 (Admin Control Plane)

```mermaid
flowchart LR
    A["ai_user_generation_config<br/>singleton id=1<br/>(backend 마이그레이션)"] -->|"읽기 전용"| B["ActionExecutor<br/>.backendFor(actionType)<br/>5분 TTL 캐시"]
    
    B -->|"action_type 분기"| C["backend_post<br/>→ POST 생성시<br/>CLI/API/OFF"]
    B -->|"action_type 분기"| D["backend_comment<br/>→ COMMENT 생성시<br/>CLI/API/OFF"]
    B -->|"action_type 분기"| E["backend_reply<br/>→ REPLY 생성시<br/>CLI/API/OFF"]
    
    F["Admin API<br/>GET /api/admin/ai-user/<br/>generation-config<br/>PUT /api/admin/ai-user/<br/>generation-config"] -->|"읽기·수정"| A
    
    G["Admin Page<br/>/admin/ai-user"] -->|"UI 제어"| F
    
    G --> H["슬라이더<br/>target_posts<br/>target_comments<br/>target_replies"]
    G --> I["라우팅 매트릭스<br/>backend_post: CLI/API/OFF<br/>backend_comment<br/>backend_reply"]
    G --> J["실시간 토큰 추정<br/>캐시 히트율<br/>cost_usd"]
    
    K["POST /api/admin/ai-user/kill"] -->|"즉시 실행"| L["ai_user_runtime.enabled=0"]
    
    style A fill:#fff9e6
    style F fill:#ffffcc
    style G fill:#ffffcc
    style L fill:#ffe6e6
```

---

## 6. Prod→Dev 동기화 메커니즘

```mermaid
flowchart TD
    A["ai-content-sync<br/>5분 주기<br/>Python/PyMySQL"] --> B["FK 체크 해제<br/>SET FOREIGN_KEY_CHECKS=0"]
    
    B --> C["SELECT FROM prod DB"]
    C --> C1["users<br/>WHERE synthetic=1"]
    C --> C2["personas<br/>WHERE user_id IN(bots)"]
    C --> C3["posts<br/>WHERE author_id IN(bots)"]
    C --> C4["vote_options<br/>WHERE post_id IN(bot_posts)"]
    C --> C5["post_comments<br/>WHERE author_id IN(bots)"]
    C --> C6["votes<br/>WHERE author_id IN(bots)"]
    C --> C7["post_likes<br/>WHERE user_id IN(bots)"]
    
    C1 --> D["INSERT IGNORE INTO dev DB<br/>(중복 무시)"]
    C2 --> D
    C3 --> D
    C4 --> D
    C5 --> D
    C6 --> D
    C7 --> D
    
    D --> E["FK 체크 재활성화<br/>SET FOREIGN_KEY_CHECKS=1"]
    
    E --> F["로그: sync_summary<br/>(rows_copied, errors)"]
    
    F --> G["다음 사이클<br/>+5분"]
    
    style A fill:#ffffcc
    style B fill:#ffe6e6
    style D fill:#e6f3ff
    style E fill:#ffe6e6
    style F fill:#f0f0f0
```

---

## 7. 디렉터리 구조 (Sync 추가)

```
ai-user/
├── llm/                        # 텍스트 생성 (Spring Boot :8092)
│   ├── src/main/java/.../llm/
│   │   ├── invoker/
│   │   │   ├── Invoker.java ————— interface
│   │   │   ├── ClaudeCliInvoker.java
│   │   │   ├── ClaudeApiInvoker.java
│   │   │   └── InvokerRouter.java
│   │   ├── controller/         GenerationController
│   │   ├── service/
│   │   │   ├── PromptAssembler
│   │   │   ├── SelfCritiqueService
│   │   │   └── OutputSanitizer
│   │   └── pool/               LlmWorkerPool
│   └── src/main/resources/voice/
│
├── orchestrator/               # 페르소나 관리·스케줄 (Spring Boot :8096)
│   ├── src/main/java/.../orchestrator/
│   │   ├── engine/             BehaviorEngine
│   │   ├── scheduler/          PairedPostScheduler
│   │   ├── task/
│   │   │   ├── ActionExecutor.backendFor(actionType)
│   │   │   └── ...
│   │   ├── admin/
│   │   │   └── AdminAiUserController
│   │   │       GET/PUT /api/admin/ai-user/generation-config
│   │   │       POST /api/admin/ai-user/kill
│   │   ├── client/
│   │   │   ├── BackendBotClient ——— secondaryBackendRestClient
│   │   │   ├── LlmAiUserClient
│   │   │   └── AiLearningClient
│   │   ├── seed/               PersonaFactory, AiUserIdentity
│   │   └── config/
│   │       ├── OrchestratorProperties
│   │       │   └── secondaryBackendBaseUrl
│   │       └── RestClientConfig
│   │           └── Optional<RestClient> secondaryBackendRestClient
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__create_persona_tables.sql
│           └── V70__create_ai_user_generation_config.sql
│
├── learning/                   # RAG + 크롤러 (Python FastAPI :8099)
│   ├── app/
│   │   ├── api/
│   │   │   ├── examples.py
│   │   │   ├── crawl.py
│   │   │   └── health.py
│   │   ├── crawlers/
│   │   │   ├── naver.py, daum.py, dcinside.py, ...
│   │   └── services/           embedding.py
│
├── sync/                       # Prod→Dev 동기화 (신규)
│   ├── sync.py ————————————— 메인 스크립트 (PyMySQL)
│   ├── requirements.txt
│   └── Dockerfile
│
└── docs/                       # 이 문서들
    ├── README.md
    ├── architecture.md ←────────── 현재 파일
    ├── llm.md
    ├── orchestrator.md
    ├── learning.md
    ├── quickstart.md
    ├── operations.md
    └── personas/
```

---

## 8. 환경 변수 전체 (Prod/Dev/Sync)

### Prod Orchestrator & LLM
| 변수 | 기본값 | 설명 |
|------|--------|------|
| `AI_USER_ENABLED` | `true` | **prod은 true** |
| `AI_USER_PERSONA_TARGET` | **10** | 목표 페르소나 수 |
| `AI_USER_DAILY_GLOBAL_CAP` | `200` | 일일 상한 |
| `AI_USER_TICK_CRON` | `0 */10 * * * *` | 10분 주기 |
| `ANTHROPIC_API_KEY` | — | Claude API 키 (선택) |
| `AI_USER_SECONDARY_BACKEND_URL` | `http://againspring-backend-dev:8080` | 보조 백엔드 (dev) |
| `AI_LEARNING_BASE_URL` | `http://againspring-ai-learning-prod:8099` | prod learning |
| `SELF_CRITIQUE_ENABLED` | `false` | 자기비평 활성화 여부 |
| `SELF_CRITIQUE_THRESHOLD` | `5` | PASS 임계값 (0-7) |
| `PAIRED_POST_ENABLED` | `true` | 페어 글 생성 |
| `PAIRED_POST_CRON` | `0 0 5 * * *` | 매일 KST 14:00 |
| `PAIRED_POST_PAIRS` | `2` | 1회당 페어 수 |

### Sync (ai-content-sync)
| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SYNC_ENABLED` | `true` | 동기화 활성화 |
| `SYNC_INTERVAL_SECONDS` | `300` | 5분 주기 |
| `PROD_DB_HOST` | `mariadb` | prod DB (docker) |
| `PROD_DB_PORT` | `3306` | — |
| `PROD_DB_USER` | `againspring` | — |
| `PROD_DB_PASSWORD` | — | 필수 |
| `PROD_DB_NAME` | `againspring_prod` | — |
| `DEV_DB_HOST` | `mariadb-dev` | dev DB (docker) |
| `DEV_DB_PORT` | `3306` | — |
| `DEV_DB_USER` | `againspring` | — |
| `DEV_DB_PASSWORD` | — | 필수 |
| `DEV_DB_NAME` | `againspring_dev` | — |
| `SYNC_LOG_LEVEL` | `INFO` | DEBUG/INFO/WARN |

### Dev Orchestrator (비활성)
| 변수 | 값 |
|------|------|
| `AI_USER_ENABLED` | `false` ⛔ |
| `DB_NAME` | `againspring_dev` |
| `AI_LEARNING_BASE_URL` | `http://againspring-ai-learning:8099` |

---

## 9. Flyway 마이그레이션

### 마이그레이션 책임
- **Backend**: `flyway_schema_history` — 일반 비즈니스 스키마
- **Orchestrator (Prod)**: `flyway_schema_history_aiuser` — AI 유저 스키마 (분리)

### Orchestrator Migrations
```sql
-- V1__create_persona_tables.sql
personas
persona_relationships
persona_seen_posts
persona_action_log
ai_user_runtime

-- V70__create_ai_user_generation_config.sql (Backend 마이그레이션)
ai_user_generation_config
  ├─ id (PK)
  ├─ target_posts, target_comments, ...
  ├─ backend_post, backend_comment, backend_reply (CLI/API/OFF)
  ├─ prompt_caching (true/false)
  └─ updated_at
```

**참고**: `ai_user_generation_config`는 backend이 소유 마이그레이션하나, orchestrator는 읽기 전용 JPA entity로 매핑.

---

## 10. 보안 체크리스트

```mermaid
flowchart LR
    A["🔐 절대 규칙"] --> B["users.synthetic=1<br/>유일 식별자<br/>API 응답 숨김"]
    A --> C["이메일<br/>ai-user-NNN@<br/>againspring.internal<br/>내부 전용"]
    A --> D["닉네임<br/>순수 한글<br/>자연스러운 이름"]
    A --> E["ContentSafetyGuard<br/>PII 검사<br/>자살/자해 차단<br/>혐오 키워드"]
    A --> F["InteractionScanner<br/>synthetic=0<br/>실제 사용자만<br/>스캔"]
    A --> G["prod→dev sync<br/>INSERT IGNORE<br/>FK 체크 해제<br/>일방향만"]
    A --> H["보조 백엔드<br/>mirrorAsync()<br/>실패해도 무시<br/>주 백엔드만<br/>필수"]
    A --> I["ANTHROPIC_API_KEY<br/>env 변수<br/>.env 커밋 금지<br/>프로덕션 시크릿"]
```

---

## 11. Invoker 인터페이스 상세

### ClaudeCliInvoker
```java
public class ClaudeCliInvoker implements Invoker {
    @Override
    public String invoke(PromptContext ctx) {
        ProcessBuilder pb = new ProcessBuilder(
            "claude", "invoke",
            "--context", ctx.getSystemPrompt(),
            "--input", ctx.getUserInput(),
            "--model", "claude-haiku-4-5-20251001"
        );
        // subprocess stdout 파싱
        return result;
    }
}
```

### ClaudeApiInvoker
```java
public class ClaudeApiInvoker implements Invoker {
    @Override
    public String invoke(PromptContext ctx) {
        // Anthropic SDK (ANTHROPIC_API_KEY 사용)
        Message msg = client.messages().create(
            model("claude-haiku-4-5-20251001"),
            systemPrompt(ctx.getSystemPrompt()),
            cacheControl(ttl: 5*60*1000),  // prompt caching
            maxTokens(2048),
            messages(userMessage(ctx.getUserInput()))
        );
        
        // cache_write or cache_hit 확인
        return msg.content().get(0).text();
    }
}
```

### InvokerRouter
```java
public class InvokerRouter {
    public Invoker route(String backend) {
        switch(backend) {
            case "CLI": return cliInvoker;
            case "API": 
                return (ANTHROPIC_API_KEY != null) 
                    ? apiInvoker 
                    : cliInvoker;  // fallback
            case "OFF": return noOpInvoker;
            default: return cliInvoker;
        }
    }
}
```

---

## 12. BotTokenCache (보조 백엔드)

### 주 백엔드 (Prod)
```
AuthToken cache (String → String)
  key: "ai-user-001@againspring.internal"
  value: "eyJhbGc..." (JWT)
```

### 보조 백엔드 (Dev)
```
SecondaryAuthToken cache (ConcurrentHashMap)
  key: "ai-user-001@againspring.internal"
  value: "eyJhbGc..." (Dev JWT)
```

**분리 이유**: 두 환경의 토큰이 서로 다름

---

## 13. BackendBotClient 오버로드

### 기존 (주 백엔드만)
```java
PostGenDto createPost(PostGenDto.PostGenRequest req) 
  → POST :8080/api/community/posts
  → 토큰은 BotTokenCache에서 자동 관리
```

### 신규 (주+보조 백엔드)
```java
PostGenDto createPost(
    PostGenDto.PostGenRequest req,
    String email,          // "ai-user-001@againspring.internal"
    String password        // AI_USER_BOT_PASSWORD
)
  → POST :8080/api/community/posts (주)
  → mirrorAsync(req, email, password)
    → POST :8080/api/community/posts (보조, fire-and-forget)
```

---

## 14. 캐시 전략

### ActionExecutor.backendFor() 캐시
```
key: "actionType:POST" or "actionType:COMMENT"
value: "CLI" or "API" or "OFF"
ttl: 5분
source: ai_user_generation_config
```

**갱신 트리거**: 
- TTL 만료 → 자동 갱신
- Admin PUT `/api/admin/ai-user/generation-config` → 즉시 인벨리데이트

### ClaudeApiInvoker 프롬프트 캐싱
```
cache_control: {"type": "ephemeral", "ttl": 5*60}  // 5분
cache_write: 첫 호출 → 캐시 저장
cache_hit: 동일 prompt → -76% 토큰
```

---

## 15. 다른 문서들

| 문서 | 내용 |
|------|------|
| [README.md](README.md) | 🎯 시작점: 서비스 개요, 페르소나, 환경변수 |
| [llm.md](llm.md) | LLM 서비스 상세 (Invoker/Router, 프롬프트, Claude CLI/API) |
| [orchestrator.md](orchestrator.md) | 오케스트레이션 엔진 (BehaviorEngine, Admin API, 이중 백엔드) |
| [learning.md](learning.md) | RAG 서비스 (임베딩, 크롤러, VECTOR) |
| [quickstart.md](quickstart.md) | Prod 배포 빠른 시작 |
| [operations.md](operations.md) | Prod 운영·모니터링·트러블슈팅 |
| [personas/README.md](personas/README.md) | 페르소나 목록·분석 |

---

**마지막 업데이트**: 2026-06-06 (prod 아키텍처 완성, 현재 구현 기준)
