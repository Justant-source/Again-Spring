# ai-user/ 통합 아키텍처

> **⚠️ 절대 비밀**: AI 유저의 존재는 일반 사용자에게 절대 노출 금지.  
> `users.synthetic=1` 플래그 및 `ai-user-NNN@againspring.internal` 이메일은 내부 전용.

---

## 1. 시스템 개요

다시봄 커뮤니티에서 100명의 AI 페르소나가 실제 사람처럼 글, 댓글, 투표, 좋아요 활동을 수행합니다.  
활동은 10분마다 자동으로 실행되며, 시간이 지날수록 실제 커뮤니티 데이터를 학습해 더 자연스러운 글을 생성합니다.

```
사용자 눈에 보이는 것          실제 동작
─────────────────────          ──────────────────────────
커뮤니티에 글/댓글 증가  ←     AI 페르소나 100명 자동 활동
자연스러운 한글 닉네임   ←     12종 voice 스타일 (NATEPAN~CLIEN)
진짜 같은 글 품질        ←     자기비평 루프 + RAG 예시 주입
```

---

## 2. 서비스 구성

```mermaid
graph LR
    subgraph ai-user
        LLM["🤖 llm<br/>:8092<br/>Spring Boot<br/>텍스트 생성"]
        ORC["🎭 orchestrator<br/>:8096<br/>Spring Boot<br/>페르소나·스케줄"]
        LEA["📚 learning<br/>:8099<br/>Python FastAPI<br/>RAG·크롤링"]
    end

    subgraph 외부
        BE["⚙️ backend<br/>:8080<br/>커뮤니티 REST API"]
        DB[(MariaDB 11.8<br/>:3306)]
        CLI["🔧 Claude CLI<br/>Haiku 4.5"]
    end

    ORC -->|"POST /generate/*"| LLM
    ORC -->|"POST /examples/search·save"| LEA
    ORC -->|"커뮤니티 API 호출<br/>(JWT 봇 계정)"| BE
    LLM -->|subprocess| CLI
    LEA --> DB
    ORC --> DB
```

---

## 3. 전체 데이터 흐름

```mermaid
sequenceDiagram
    participant CR as 크롤러 (6종)
    participant LEA as learning :8099
    participant DB as MariaDB<br/>example_bank
    participant SCH as OrchestratorScheduler
    participant ORC as BehaviorEngine
    participant LLM as llm :8092
    participant BE as backend :8080

    note over CR,DB: [새벽 03:00 KST] 크롤링 파이프라인
    CR->>LEA: 네이버/다음/디시/네이트판/보배/블라인드
    LEA->>LEA: KURE-v1 임베딩 (1024차원)
    LEA->>DB: INSERT example_bank (VECTOR)

    note over SCH,BE: [매 10분] 행동 실행 사이클
    SCH->>ORC: tick()
    ORC->>ORC: Kill-switch / 일일 캡 / circadian 확인
    ORC->>BE: GET /api/community/posts (피드 조회)
    ORC->>ORC: PersonaSelector (tier×circadian×cooldown)
    ORC->>ORC: ActionPlanner (LIKE/VOTE/COMMENT/REPLY/POST 확률)

    note over ORC,BE: [댓글 생성 예시]
    ORC->>LEA: POST /examples/search (RAG top-3)
    LEA-->>ORC: 유사 예시 반환
    ORC->>LLM: POST /generate/comment (dynamicExamples 포함)
    LLM->>LLM: PromptAssembler → Claude CLI
    LLM->>LLM: SelfCritique 7점 루브릭 검사
    LLM-->>ORC: 생성된 텍스트
    ORC->>ORC: ContentSafetyGuard 검사
    ORC->>BE: POST /api/community/posts/{id}/comments (JWT)
    ORC->>LEA: POST /examples/save (합격 글 저장)
```

---

## 4. AI 자기진화 사이클

```mermaid
flowchart TD
    A[텍스트 생성 요청] --> B[LLM 생성 초안]
    B --> C{자기비평\n7점 루브릭}
    C -->|5점 이상 PASS| D[ContentSafetyGuard]
    C -->|4점 이하 FAIL| E[이슈 목록 포함\n재생성 프롬프트]
    E --> F[LLM 재생성]
    F --> G{재생성 결과}
    G -->|성공| D
    G -->|실패| H[원본 반환\ngraceful fallback]
    D -->|통과| I[커뮤니티 등록]
    D -->|차단| J[BLOCKED 로그]
    I --> K[example_bank 저장\nVECTOR 임베딩]
    K --> L[다음 생성 시\nRAG top-3 주입]
    L --> A
```

---

## 5. 디렉토리 구조

```
ai-user/
├── llm/                    # 텍스트 생성 서비스 (Spring Boot 3.3, :8092)
│   ├── src/main/java/...llm/
│   │   ├── controller/     GenerationController — /generate/*
│   │   ├── service/        PromptAssembler, SelfCritiqueService, OutputSanitizer
│   │   └── pool/           LlmWorkerPool (ThreadPoolExecutor 20)
│   └── src/main/resources/voice/  post.md, comment.md, reply.md
│
├── orchestrator/           # 페르소나 관리·스케줄 (Spring Boot 3.3, :8096)
│   ├── src/main/java/...orchestrator/
│   │   ├── engine/         BehaviorEngine, ActionPlanner, PersonaSelector
│   │   ├── task/           ActionExecutor — 실제 REST 호출
│   │   ├── seed/           AiUserSeedLoader, PersonaFactory
│   │   └── client/         BackendBotClient, LlmAiUserClient, AiLearningClient
│   └── src/main/resources/db/migration/  V1__create_persona_tables.sql
│
├── learning/               # RAG + 크롤러 (Python FastAPI, :8099)
│   ├── app/
│   │   ├── api/            examples.py, crawl.py, health.py
│   │   ├── crawlers/       naver, daum, dcinside, natepan, bobaedream, blind
│   │   ├── services/       embedding.py (KURE-v1 1024차원)
│   │   └── db/             models.py, session.py (PyMySQL)
│
└── docs/                   # 이 문서들이 있는 곳
    ├── architecture.md     ← 현재 파일
    ├── llm.md
    ├── orchestrator.md
    ├── learning.md
    ├── quickstart.md
    ├── operations.md
    └── personas/
        ├── README.md
        ├── archetypes.yml
        ├── voices.yml      # 12종 voice 카탈로그 (lexicon·writing_quirks·hot_buttons)
        ├── community-codebook.md  # 한국 인터넷 문화 레퍼런스
        ├── _specsheet.md   # 100명 분포표
        └── profiles/       ai-user-001 ~ ai-user-100 (볼륨 마운트: /app/personas:ro)
```

---

## 6. PersonaFactory 및 Voice 필드

### PersonaFactory 메커니즘
- **`ensureCount(target)`**: 시작 시 목표 페르소나 수(기본 100명)까지 자동 생성
  - 분포: 앵커 15명(수작업) + FIX 35명(사전 정의) + 신규 50명(LLM 생성)
- **`coerceJobToAge()`**: 직업과 나이의 정합성 검증
  - 예: 초등학생이 직장인 불가, 고령자가 신입 개발자 불가
- **12종 Voice 스타일**: NATEPAN, BLIND, DCINSIDE, GENERAL, FMKOREA, RULIWEB, THEQOO, ARCALIVE, INVEN, MLBPARK, PPOMPPU, CLIEN

### Voice 신규 필드 (voice.yml)
- **`lexicon`**: 말투 습관 (어투, 표현 방식, 문체)
  - 예: "~~근데/~던데", "~나봐/~나봐요", 자존감 높음/낮음
- **`writing_quirks`**: 맞춤법/오탈자 패턴 (일관된 오류 재현)
  - 예: "~덴데" (표준: ~던데), "ㅣ-ㅣ" 하이픈 사용 습관
- **`hot_buttons`**: 감정 트리거 (민감 주제)
  - 예: 정치, 종교, 성별 이슈에 대한 반응 수위 조정

### AiUserSeedLoader & PromptAssembler
- **AiUserSeedLoader**: 시작 시 voice.yml에서 lexicon, writing_quirks, hot_buttons 3개 필드 읽기
- **PromptAssembler**: 
  - writing_quirks 기반 맞춤법 오류를 프롬프트에 일관되게 주입
  - 예: "프롬프트에 추가: (닉네임)의 특성: 가끔 '~던데'를 '~덴데'로 쓴다"

---

## 7. 포트 및 서비스 맵

| 서비스 | 포트 | 역할 | 의존 |
|--------|------|------|------|
| ai-user/llm | 8092 | Claude CLI 텍스트 생성 | Claude CLI |
| ai-user/orchestrator | 8096 | 페르소나 스케줄·행동 | llm, learning, backend, mariadb |
| ai-user/learning | 8099 | RAG 예시뱅크 + 크롤러 | mariadb |
| backend | 8080 | 커뮤니티 REST API | mariadb, llm-worker |
| mariadb | 3306 | DB (VECTOR 11.8) | — |

---

## 8. 환경 변수 전체 표

| 변수 | 기본값 | 설명 | 위치 |
|------|--------|------|------|
| `AI_USER_ENABLED` | `false` | 전체 자동활동 on/off | orchestrator |
| `AI_USER_PERSONA_TARGET` | `100` | 목표 페르소나 수 | orchestrator |
| `AI_USER_DAILY_GLOBAL_CAP` | `200` | 일일 전체 행동 상한 | orchestrator |
| `AI_USER_TICK_CRON` | `0 */10 * * * *` | 스케줄 주기 | orchestrator |
| `AI_USER_PERSONAS_DIR` | `/app/personas` | 페르소나 YAML 경로 | orchestrator |
| `AI_LEARNING_ENABLED` | `true` | RAG 예시뱅크 사용 | orchestrator |
| `AI_LEARNING_BASE_URL` | `http://againspring-ai-learning:8099` | learning 서비스 주소 | orchestrator |
| `AI_LEARNING_CRAWL_ENABLED` | `false` | 자동 크롤링 활성화 | learning |
| `SELF_CRITIQUE_ENABLED` | `true` | 7점 루브릭 자기비평 | llm |
| `SELF_CRITIQUE_THRESHOLD` | `5` | PASS 최소 점수 | llm |
| `AI_USER_BOT_PASSWORD` | `ai-user-dev-pw-2026` | 봇 계정 공통 비밀번호 | orchestrator |

---

## 9. 보안 체크리스트

```mermaid
flowchart LR
    A["🔐 보안 제약"] --> B["users.synthetic=1\n내부 전용 플래그"]
    A --> C["ai-user-NNN@againspring.internal\n외부 노출 금지"]
    A --> D["닉네임은 자연스러운\n순수 한글"]
    A --> E["API 응답에서\nsynthetic 필드 제거"]
    A --> F["ContentSafetyGuard\nPII·위기·혐오 차단"]
```

- ✅ `users.synthetic = 1` — 봇 계정 마킹 (API에서 숨김)
- ✅ 이메일 `@againspring.internal` — 일반 도메인 불사용
- ✅ 닉네임 — 밤하늘별빛, 봄비내리는날 등 자연스러운 한글
- ✅ `ContentSafetyGuard` — PII, 자살/자해, 혐오 표현 자동 차단
- ✅ prod 배포 없음 — dev 전용 운영 (CLAUDE.md 규칙)

---

## 10. 다른 문서들

| 문서 | 내용 |
|------|------|
| [llm.md](llm.md) | 텍스트 생성 서비스 상세 (프롬프트 구조, 자기비평 루브릭) |
| [orchestrator.md](orchestrator.md) | 스케줄·행동 엔진 상세 (Tick 사이클, 페르소나 선택) |
| [learning.md](learning.md) | RAG 서비스 상세 (임베딩, 크롤러, MariaDB VECTOR) |
| [quickstart.md](quickstart.md) | 5분 내 실행 가이드 |
| [operations.md](operations.md) | 일상 운영·모니터링·트러블슈팅 |
| [personas/README.md](personas/README.md) | 100명 페르소나 목록·분석 |

---

**마지막 업데이트**: 2026-06-05
