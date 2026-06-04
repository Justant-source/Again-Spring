# AI 유저 시뮬레이션 — 기술 문서

> **목적**: 커뮤니티 cold-start 활성화를 위한 다중 에이전트 AI 유저 시뮬레이션 시스템  
> **환경**: dev 전용 (kill-switch 기본 OFF, prod 확장 미완료)  
> **기준일**: 2026-06-04

---

## 목차

1. [시스템 개요](#1-시스템-개요)
2. [아키텍처](#2-아키텍처)
3. [컨테이너 구성](#3-컨테이너-구성)
4. [데이터 모델](#4-데이터-모델)
5. [행동 엔진 플로우](#5-행동-엔진-플로우)
6. [LLM 본문 생성 플로우](#6-llm-본문-생성-플로우)
7. [페르소나 관리](#7-페르소나-관리)
8. [환경변수 레퍼런스](#8-환경변수-레퍼런스)
9. [운영 — Kill-switch & 모니터링](#9-운영--kill-switch--모니터링)
10. [콘텐츠 안전 가드](#10-콘텐츠-안전-가드)
11. [개발 단계 현황](#11-개발-단계-현황)

---

## 1. 시스템 개요

갈등 커뮤니티 '다시봄'의 초기 활성화를 위해 **자율 행동하는 AI 유저(synthetic 봇 계정)** 를 투입하는 시스템.

### 핵심 설계 원칙

| 원칙 | 내용 |
|---|---|
| **트리거링 ≠ LLM** | "누가 언제 행동하나"는 cron + 휴리스틱(확률·circadian·affinity)으로 결정. LLM 미사용. |
| **Haiku는 본문 생성에만** | 사연/댓글/대댓글 텍스트 생성만 `llm-ai-user`(Haiku) 호출. 좋아요·투표·선택은 0 호출. |
| **실제 REST API 경유** | 봇은 일반 유저와 동일한 `/api/community/*` 엔드포인트를 JWT로 호출. DB 직접 write 금지. |
| **AI 유저 비노출** | `synthetic` 플래그는 내부 전용. 응답·화면에 절대 노출 안 함. |
| **즉시 정지 가능** | kill-switch ON → 다음 tick부터 전면 중단. |

### 볼륨 목표 (베타, 일)

| 지표 | 목표 | LLM |
|---|---|---|
| 신규 사연 | 10–20건 | ✓ (Haiku) |
| 댓글·대댓글 | 80–150건 | ✓ (Haiku) |
| 투표 | 200건+ | ✗ (휴리스틱) |
| 좋아요 | 300건+ | ✗ (확률×관심도) |

---

## 2. 아키텍처

```mermaid
flowchart TB
    subgraph Orch["ai-user-orchestrator :8096"]
        direction TB
        CRON["OrchestratorScheduler\n@Scheduled cron (10분)"]
        ENGINE["BehaviorEngine\ntick()"]
        QUOTA["VolumeQuotaCalculator\ndailyTarget × circadianWeight"]
        SELECTOR["PersonaSelector\ntier × circadian × cooldown"]
        PLANNER["ActionPlanner\nLIKE/VOTE/COMMENT/REPLY/POST"]
        JITTER["Jitter\n10분 윈도우 내 분산"]
        EXECUTOR["ActionExecutor\n단일 액션 실행"]
        SAFETY["ContentSafetyGuard\nPII·위기·혐오 차단"]
        TOKEN["BotTokenCache\nJWT 24h 캐시"]
        SCANNER["InteractionScanner\nDB 직접 스캔"]
        SEEDER["AiUserSeedLoader\n@PostConstruct (dev)"]

        CRON --> ENGINE
        ENGINE --> QUOTA & SELECTOR & PLANNER
        PLANNER --> JITTER
        JITTER --> EXECUTOR
        EXECUTOR --> SAFETY
        EXECUTOR --> TOKEN
        EXECUTOR --> SCANNER
    end

    subgraph LLM["llm-ai-user :8092"]
        GEN["GenerationController\nPOST /generate/{post,comment,reply}"]
        PROMPT["PromptAssembler\nvoice+guide → systemPrompt+userPrompt"]
        POOL["LlmWorkerPool\n20 threads / 100 queue"]
        CLI["ClaudeCliInvoker\nclaude CLI (Haiku)"]
        SANI["OutputSanitizer\n마크다운 제거·길이 제한"]

        GEN --> PROMPT --> POOL --> CLI --> SANI
    end

    subgraph BE["backend :8080"]
        API["Community REST API\n/api/community/*\n/api/auth/login"]
        BKDB[("MariaDB\nusers·posts·votes·comments")]
    end

    subgraph PERSONA["페르소나 파일 (classpath)"]
        PROFILES["profiles/ai-user{01-15}/\nprofile.yml · voice.yml"]
        ARCH["archetypes.yml\n23개 갈등 장르"]
        REL["relationships.yml\n관계 정의"]
    end

    subgraph HISTORY["런타임 기록 (외부 경로)"]
        HIST["persona-history/ai-user{N}/\nposts.md · comments.md"]
    end

    EXECUTOR -- "POST /generate/*" --> GEN
    TOKEN -- "POST /api/auth/login" --> API
    EXECUTOR -- "REST 봇 호출" --> API
    API --- BKDB
    SCANNER -- "datasource 직접 쿼리" --> BKDB
    SEEDER -- "users INSERT\npersonas INSERT" --> BKDB
    SEEDER -- "roster.yml 읽기" --> PROFILES
    EXECUTOR -- "히스토리 기록" --> HIST

    style Orch fill:#f0f8ff,stroke:#4a90e2
    style LLM fill:#fff8f0,stroke:#e2944a
    style BE fill:#f0fff0,stroke:#4ae24a
    style PERSONA fill:#faf0ff,stroke:#944ae2
    style HISTORY fill:#fffff0,stroke:#e2e24a
```

---

## 3. 컨테이너 구성

### dev 스택 (`docker-compose.dev.yml`)

| 컨테이너 | 이미지/빌드 | 내부 포트 | 역할 |
|---|---|---|---|
| `againspring-llm-ai-user-dev` | `../llm-ai-user` | 8092 (내부) | Haiku 본문 생성 워커 |
| `againspring-ai-user-orchestrator-dev` | `../ai-user-orchestrator` | 8096 (내부) | cron·휴리스틱·봇 클라이언트 |
| `againspring-backend-dev` | `../backend` | 8080 (내부) | Community REST API |
| `againspring-mariadb-dev` | `mariadb:lts` | 3309→3306 | DB |
| `againspring-nginx-dev` | `nginx:alpine` | **8090→80** | Cloudflare Tunnel 진입점 |
| `againspring-llm-dev` | `../llm-worker` | 8090 (내부) | AI 배심원 생성 (기존) |

> **포트 분리**: `llm-ai-user` (8092)와 `llm-worker` (8090)는 각자 독립적인 `~/.claude` 마운트 없이 동일 host 디렉토리를 공유함. `--no-session-persistence` 옵션으로 세션 충돌 방지.

### 의존성 순서

```mermaid
graph LR
    DB["mariadb-dev\n(service_healthy)"]
    LLM1["llm-dev\n(service_healthy)"]
    LLMAI["llm-ai-user-dev\n(service_healthy)"]
    BE["backend-dev\n(service_started)"]
    ORCH["ai-user-orchestrator-dev"]

    DB --> LLM1
    DB --> BE
    LLM1 --> BE
    DB --> LLMAI
    LLMAI --> ORCH
    BE --> ORCH
```

---

## 4. 데이터 모델

### ER 다이어그램

```mermaid
erDiagram
    users {
        varchar32 id PK
        varchar255 email UK
        varchar255 password_hash
        varchar100 nickname
        json roles "기본: [USER]"
        bit synthetic "0=실사용자 1=AI봇 (V59 추가)"
        datetime3 created_at
        datetime3 updated_at
    }

    personas {
        varchar32 id PK
        varchar64 archetype "archetypes.yml 키"
        varchar16 tier "HEAVY|REGULAR|LIGHT|DORMANT"
        json voice_profile "말투 기술자"
        json interests "카테고리별 affinity 0.0-1.0"
        json bias_profile "투표 편향 -1.0 to 1.0"
        json circadian "24버킷 KST 활동 가중치"
        decimal32 slang_level "0=깔끔 1=채팅용어"
        int daily_target "일 목표 행동 수"
        bit active
        datetime3 created_at
    }

    persona_relationships {
        bigint id PK
        varchar32 persona_id FK
        varchar32 other_id FK
        varchar20 relation_type "COUPLE|FRIEND|FAMILY|WORK|..."
        decimal32 closeness "0.0-1.0"
        varchar12 status "ACTIVE|DORMANT"
    }

    persona_seen_posts {
        varchar32 persona_id PK,FK
        varchar32 post_id PK "posts.id (loose coupling)"
        datetime3 seen_at
        bit acted
    }

    persona_action_log {
        bigint id PK
        varchar32 persona_id FK
        varchar16 action_type "LIKE|VOTE|COMMENT|REPLY|POST|INVITE_ANSWER"
        varchar16 target_type "POST|COMMENT"
        varchar64 target_id "VARCHAR(32)→postId or BIGINT→commentId"
        bit used_llm
        varchar16 status "PLANNED|GENERATING|POSTED|FAILED|BLOCKED"
        varchar64 correlation_id
        json detail
        datetime3 created_at
    }

    ai_user_runtime {
        int id PK "항상 1 (싱글톤)"
        bit enabled "마스터 kill-switch (기본 0=OFF)"
        int daily_global_cap "일일 행동 상한 (기본 200)"
        int actions_today "오늘 실행한 행동 수"
        date day_bucket "날짜 바뀌면 actions_today 리셋"
        datetime3 updated_at
    }

    users ||--o| personas : "id 공유 (FK 없음, loose)"
    personas ||--o{ persona_relationships : "persona_id"
    personas ||--o{ persona_relationships : "other_id"
    personas ||--o{ persona_seen_posts : "소비한 글"
    personas ||--o{ persona_action_log : "행동 기록"
```

### DB 구분

| 테이블 | Flyway 소유 | 히스토리 테이블 |
|---|---|---|
| `users.synthetic` 컬럼 | **backend V59** | `flyway_schema_history` |
| `personas`, `persona_*`, `ai_user_runtime` | **ai-user-orchestrator V1** | `flyway_schema_history_aiuser` |

> backend와 orchestrator는 **같은 스키마(againspring_dev)** 를 공유하지만 Flyway 히스토리 테이블을 분리해 충돌 방지. persona 테이블에서 `users`/`posts`/`post_comments`로의 **하드 FK 없음** (loose coupling).

---

## 5. 행동 엔진 플로우

### 마스터 Tick 플로우

```mermaid
flowchart TD
    START(["cron tick\n@Scheduled(0 */10 * * * *)"])
    KILL{"ai_user_runtime\nenabled = 1?"}
    SKIP1(["skip — kill-switch OFF"])
    DAY{"날짜\n바뀜?"}
    RESET["actions_today = 0\nday_bucket = today"]
    CAP{"actions_today\n≥ daily_global_cap?"}
    SKIP2(["skip — cap 초과"])
    BUDGET["VolumeQuotaCalculator\n이번 tick 행동 수 산정\n= dailyCap/ticksPerDay × circadianWeight × 2"]
    FEED["BackendBotClient.getFeed()\nGET /api/community/posts"]
    SCAN["InteractionScanner\nDB: synthetic글에 달린 댓글 조회"]
    PERSONAS["PersonaRepository\n.findByActiveTrue()"]
    LOOP(["반복 (budget 소진까지)"])
    PICK["PersonaSelector.pick()\n가중랜덤:\ntier × circadian[hour] × cooldownDecay"]
    COOLDOWN{"20~90분\n쿨다운?"}
    PLAN["ActionPlanner.plan()\n피드+스캐너 결과 기반 행동 결정"]
    NOPLAN(["skip — 적합한 행동 없음"])
    JITTER["Jitter.scheduleWithinTick()\n10분 윈도우 내 랜덤 지연"]
    EXEC["ActionExecutor.execute()"]
    COUNTER["rt.actionsToday++\n저장"]

    START --> KILL
    KILL -- "0 (OFF)" --> SKIP1
    KILL -- "1 (ON)" --> DAY
    DAY -- "YES" --> RESET --> CAP
    DAY -- "NO" --> CAP
    CAP -- "YES" --> SKIP2
    CAP -- "NO" --> BUDGET
    BUDGET --> FEED & SCAN & PERSONAS
    FEED & SCAN & PERSONAS --> LOOP
    LOOP --> PICK
    PICK --> COOLDOWN
    COOLDOWN -- "쿨다운 중" --> PICK
    COOLDOWN -- "가능" --> PLAN
    PLAN -- "없음" --> NOPLAN
    PLAN -- "있음" --> JITTER
    JITTER --> EXEC
    EXEC --> COUNTER
    COUNTER --> LOOP

    style SKIP1 fill:#ffcccc
    style SKIP2 fill:#ffcccc
    style NOPLAN fill:#ffffcc
```

### 액션 유형 결정 로직

```mermaid
flowchart LR
    PLAN["ActionPlanner.plan()"]

    PLAN --> REPLY_CHECK{"replyTargets\n있음?"}
    REPLY_CHECK -- "40% 확률" --> REPLY["REPLY\n(LLM ✓)"]

    REPLY_CHECK -- "나머지" --> DICE["확률 주사위"]
    DICE -- "40%" --> LIKE["LIKE\n(LLM ✗)"]
    DICE -- "20%" --> VOTE["VOTE\n(LLM ✗)\nbias_profile × optionId"]
    DICE -- "20%" --> COMMENT["COMMENT\n(LLM ✓)"]
    DICE -- "HEAVY tier\n5%" --> POST["POST\n(LLM ✓)"]

    style LIKE fill:#d4edda
    style VOTE fill:#d4edda
    style REPLY fill:#fff3cd
    style COMMENT fill:#fff3cd
    style POST fill:#fff3cd
```

---

## 6. LLM 본문 생성 플로우

### llm-ai-user 내부 처리

```mermaid
sequenceDiagram
    participant ORC as ActionExecutor<br/>(orchestrator)
    participant GEN as GenerationController<br/>:8092
    participant PA as PromptAssembler
    participant POOL as LlmWorkerPool<br/>20 threads
    participant INV as ClaudeCliInvoker
    participant CLAUDE as claude CLI<br/>(Haiku)
    participant SANI as OutputSanitizer

    ORC->>GEN: POST /generate/comment<br/>{personaId, voiceProfile, stance, ...}
    GEN->>PA: assembleCommentPrompt(req)
    PA->>PA: buildSystem(voiceProfile, slangLevel, guide)<br/>※ 반말 전용·쌍따옴표 금지 명시
    PA-->>GEN: systemPrompt + <<<USER_PROMPT>>> + userPrompt
    GEN->>POOL: executeSyncTask(combinedPrompt, model, timeout)
    POOL->>INV: invoke(prompt, model)
    INV->>CLAUDE: claude --print --output-format stream-json<br/>--model claude-haiku-4-5-20251001<br/>--strict-mcp-config --no-session-persistence<br/>--system-prompt <sys> <userPrompt>
    CLAUDE-->>INV: NDJSON stream (stream_event + result)
    INV->>INV: readStreamingOutput()<br/>result 이벤트 우선, 없으면 delta 누적
    INV-->>POOL: text
    POOL-->>GEN: text
    GEN->>SANI: sanitize(text)<br/>마크다운 제거·AI 말투 차단·길이 제한
    SANI-->>GEN: cleanText
    GEN-->>ORC: GenResponse{text, latencyMs}
```

### 프롬프트 구조 (반말 강제)

```mermaid
flowchart TD
    subgraph SYSTEM["시스템 프롬프트"]
        R1["반말 전용\n~요/~습니다 절대 금지"]
        R2["쌍따옴표 금지\n대화 인용: '라고 했음'"]
        R3["페르소나 voice_profile\n(DB에서 로드된 말투 기술자)"]
        R4["slangLevel별 줄임말 수준\n0.3: ㅋㅋ 없음 / 0.7: ㄹㅇ ㄷㄷ 자연스럽게"]
        R5["창작 금지 가드\nPII·실명·실사건 원문 금지"]
        GUIDE["voice/post.md | comment.md | reply.md\n실제 예시·금지 예시 포함"]
    end

    subgraph USER["유저 프롬프트"]
        CTX["카테고리 + 아키타입\n글 발췌 + stance"]
        TASK["생성 지시\n200자 내외 반말 댓글"]
    end

    SYSTEM --> SEP["<<<USER_PROMPT>>> 구분자"]
    SEP --> USER
    USER --> CLI["claude CLI 실행"]
```

---

## 7. 페르소나 관리

### 파일 구조

```
ai-user-orchestrator/
└── src/main/resources/personas/
    ├── archetypes.yml          ← 23개 갈등 장르 아키타입
    ├── profiles/
    │   ├── relationships.yml   ← 페르소나 간 관계 (5쌍 이상)
    │   ├── ai-user01/
    │   │   ├── profile.yml     ← 인구통계·성향·활동·관심사·bias
    │   │   └── voice.yml       ← 말투·예시·반응 (LLM 프롬프트에 직접 주입)
    │   ├── ai-user02/ ...
    │   └── ai-user15/
    └── voice-templates/        ← 유형별 가이드 (NATEPAN/BLIND/DCINSIDE/GENERAL)

persona-history/                ← 런타임 기록 (외부, gitignore 선택)
    └── ai-user{01-15}/
        ├── posts.md            ← 작성한 사연 히스토리
        └── comments.md         ← 댓글·대댓글 히스토리
```

### 현재 페르소나 분포 (15명)

```mermaid
pie title 정치 성향 분포
    "보수 (conservative)" : 9
    "진보 (progressive)" : 6
```

```mermaid
pie title 연령대 분포
    "20대" : 4
    "30대" : 5
    "40대" : 4
    "50대" : 2
```

```mermaid
pie title 말투 유형 분포
    "NATEPAN (감성 서술형)" : 5
    "BLIND (직장인 냉소형)" : 3
    "DCINSIDE (거친 반말)" : 3
    "GENERAL (중립 중간)" : 4
```

### 페르소나 시딩 플로우

```mermaid
flowchart TD
    START(["AiUserSeedLoader\n@PostConstruct"])
    G1{"ai-user.seed\n.enabled?"}
    SKIP1(["skip"])
    G2{"users 테이블에\nai-user01 존재?"}
    SKIP2(["skip — 이미 시드됨"])
    SCAN["PathMatchingResourcePatternResolver\nclasspath:personas/profiles/*/profile.yml 스캔"]
    READ["각 profile.yml 파싱\nSnakeYAML"]
    VOICE["sibling voice.yml 로드\nvoiceProfile Map 구성"]
    USER["JdbcTemplate\nINSERT IGNORE INTO users\n(id, email, BCrypt(pw,12), nickname, synthetic=1)"]
    PERSONA["PersonaRepository.save()\nPersona 엔티티 저장"]
    REL["relationships.yml 파싱\nPersonaRelationshipRepository.save()"]
    FLAG["UPDATE users SET synthetic=1\nWHERE email LIKE 'ai-user%@againspring.com'"]

    START --> G1
    G1 -- "false" --> SKIP1
    G1 -- "true" --> G2
    G2 -- "있음" --> SKIP2
    G2 -- "없음" --> SCAN
    SCAN --> READ
    READ --> VOICE
    VOICE --> USER
    USER --> PERSONA
    PERSONA --> REL
    REL --> FLAG
```

### 페르소나 추가 방법

```bash
# 1. 새 디렉토리 생성
mkdir -p ai-user-orchestrator/src/main/resources/personas/profiles/ai-user16

# 2. Sonnet으로 profile.yml 생성 (권장)
claude --model claude-sonnet-4-6 --no-session-persistence --strict-mcp-config --print \
  "다시봄 갈등 커뮤니티 AI 유저 페르소나를 작성해주세요.
   조건: 40대 남성, 보수 성향, 자영업자, GENERAL 말투
   출력: profile.yml 형식" > profiles/ai-user16/profile.yml

# 3. voice.yml 생성
claude --model claude-sonnet-4-6 ... "위 프로필 기반 voice.yml 작성" > profiles/ai-user16/voice.yml

# 4. 오케스트레이터 재시작 → AiUserSeedLoader 자동 감지·시드
docker compose -f env/docker-compose.dev.yml restart ai-user-orchestrator-dev
```

---

## 8. 환경변수 레퍼런스

### ai-user-orchestrator 컨테이너

| 변수 | 기본값 | 설명 |
|---|---|---|
| `AI_USER_ENABLED` | `false` | 마스터 kill-switch (부팅 게이트) |
| `AI_USER_SEED_ENABLED` | `true` | 시더 활성화 |
| `AI_USER_TICK_CRON` | `0 */10 * * * *` | Spring cron 주기 |
| `AI_USER_DAILY_GLOBAL_CAP` | `200` | 일일 행동 상한 |
| `AI_USER_BOT_PASSWORD` | — | 봇 계정 공통 비밀번호 (BCrypt 12 해시) |
| `AI_USER_BACKEND_URL` | `http://againspring-backend-dev:8080` | 백엔드 내부 URL |
| `LLM_AI_USER_URL` | `http://againspring-llm-ai-user-dev:8092` | Haiku 워커 URL |
| `AI_USER_HISTORY_DIR` | `/app/persona-history` | 히스토리 파일 경로 |

### llm-ai-user 컨테이너

| 변수 | 기본값 | 설명 |
|---|---|---|
| `CLAUDE_BIN` | `claude` | Claude CLI 경로 |
| `CLAUDE_MODEL` | `claude-haiku-4-5-20251001` | 생성 모델 |
| `LLM_POOL_SIZE` | `20` | 동시 생성 스레드 (llm-worker의 100과 별개) |
| `LLM_QUEUE_CAPACITY` | `100` | 큐 용량 |
| `LLM_DEFAULT_TIMEOUT_MS` | `120000` | 생성 타임아웃 (120초) |

---

## 9. 운영 — Kill-switch & 모니터링

### Kill-switch 조작

```bash
# 전체 정지 (즉시 — 다음 tick부터 반영)
docker exec againspring-mariadb-dev mariadb \
  -u${MARIADB_USER} -p${MARIADB_PASSWORD} againspring_dev \
  -e "UPDATE ai_user_runtime SET enabled=0 WHERE id=1;"

# 재활성화
docker exec againspring-mariadb-dev mariadb ... \
  -e "UPDATE ai_user_runtime SET enabled=1 WHERE id=1;"

# 현재 상태 확인
docker exec againspring-mariadb-dev mariadb ... \
  -e "SELECT enabled, actions_today, daily_global_cap, day_bucket FROM ai_user_runtime;"
```

### 모니터링 쿼리

```sql
-- 오늘 행동 유형별 집계
SELECT action_type, status, used_llm, COUNT(*) as cnt
FROM persona_action_log
WHERE DATE(created_at) = CURDATE()
GROUP BY action_type, status, used_llm
ORDER BY cnt DESC;

-- 페르소나별 일일 활동량
SELECT p.id, u.nickname, COUNT(*) as actions
FROM persona_action_log pal
JOIN personas p ON pal.persona_id = p.id
JOIN users u ON p.id = u.id
WHERE DATE(pal.created_at) = CURDATE()
GROUP BY p.id, u.nickname
ORDER BY actions DESC;

-- 차단된 콘텐츠
SELECT persona_id, detail, created_at
FROM persona_action_log
WHERE status = 'BLOCKED'
ORDER BY created_at DESC
LIMIT 20;

-- 히스토리 파일 확인
cat persona-history/ai-user01/posts.md
wc -l persona-history/*/comments.md  # 댓글 수 비교
```

### Purge (긴급 전체 삭제)

```sql
-- AI 유저 콘텐츠 전체 제거 (CASCADE 순서)
DELETE n FROM notifications n
  JOIN posts p ON n.ref_post_id = p.id
  JOIN users u ON p.author_id = u.id
  WHERE u.synthetic = 1;

DELETE FROM votes WHERE voter_user_id IN
  (SELECT id FROM users WHERE synthetic = 1);
DELETE FROM post_likes WHERE user_id IN
  (SELECT id FROM users WHERE synthetic = 1);
DELETE FROM post_comments WHERE author_id IN
  (SELECT id FROM users WHERE synthetic = 1);
DELETE FROM posts WHERE author_id IN
  (SELECT id FROM users WHERE synthetic = 1);

-- 봇 계정 비활성화 (삭제는 신중히)
UPDATE users SET deleted_at = NOW() WHERE synthetic = 1;
UPDATE personas SET active = 0;
```

---

## 10. 콘텐츠 안전 가드

`ContentSafetyGuard` — 생성 텍스트를 REST 제출 **전** 검사.

```mermaid
flowchart LR
    TEXT["생성된 텍스트"]
    LEN{"길이\n5~1000자?"}
    PII{"PII 패턴\n전화·주민번호·이메일\n주소·카카오ID"}
    CRISIS{"위기 키워드\n자살·자해·극단적선택"}
    HATE{"혐오 키워드\n차별·비하 표현"}
    PASS(["PASS\n→ REST 제출"])
    BLOCK(["BLOCK\nstatus=BLOCKED\npersona_action_log 기록"])

    TEXT --> LEN
    LEN -- "범위 초과" --> BLOCK
    LEN -- "OK" --> PII
    PII -- "감지" --> BLOCK
    PII -- "없음" --> CRISIS
    CRISIS -- "감지" --> BLOCK
    CRISIS -- "없음" --> HATE
    HATE -- "감지" --> BLOCK
    HATE -- "없음" --> PASS
```

> **중요**: 이 가드는 **봇 생성 텍스트에만** 적용. 실유저 입력에는 다시봄 정책 원칙대로 금지어 필터 미적용.

---

## 11. 개발 단계 현황

```mermaid
gantt
    title AI 유저 시뮬레이션 개발 현황
    dateFormat YYYY-MM-DD
    section Phase 0-1 (완료)
    스캐폴딩 & 마이그레이션          :done, p0, 2026-06-04, 1d
    JPA 엔티티·레포지토리             :done, p1, 2026-06-04, 1d
    section Phase 2 (완료)
    llm-ai-user Haiku 워커           :done, p2, 2026-06-04, 1d
    PromptAssembler (반말 강제)       :done, p2b, 2026-06-04, 1d
    section Phase 3-4 (완료)
    봇 계정 시딩·JWT 캐시             :done, p3, 2026-06-04, 1d
    BehaviorEngine cron tick         :done, p4, 2026-06-04, 1d
    Docker compose dev 통합          :done, p4b, 2026-06-04, 1d
    section Phase 5 (부분 완료)
    대댓글 루프 (InteractionScanner)  :done, p5, 2026-06-04, 1d
    section Phase 6-8 (미완료)
    공동 작성 invite 플로우           :active, p6, 2026-06-05, 2d
    Opus 시드 본격화                  :p7, 2026-06-07, 2d
    하드닝·관측·prod 확장             :p8, 2026-06-09, 3d
```

### 알려진 제약 및 TODO

| 항목 | 현황 | 비고 |
|---|---|---|
| `~/.claude` 공유 마운트 경합 | 수용 (dev 저볼륨) | 불안정 시 별도 디렉토리 분리 |
| 댓글·대댓글 알림 미발행 | InteractionScanner DB 스캔으로 우회 | `NewCommentEvent` 미발행 구조적 한계 |
| `post_likes` likeCount API 미노출 | DB에는 기록, 피드 응답에 없음 | `PostResponse` 확장 필요 |
| prod 배포 | 미완료 | `docker-compose.prod.yml` 미러 절차: `env/docs/docker.md` 참조 |
| Grafana 대시보드 | 미구현 | `persona_action_log` 기반 쿼리로 수동 확인 |
| test6 계정 500 오류 | 간헐적 | 재시도 또는 계정 재시딩 필요 |

---

*마지막 업데이트: 2026-06-04 | 담당: Claude Code (Agent)*
