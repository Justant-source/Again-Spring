# API 흐름 — 시퀀스 다이어그램

> last-verified: 2026-08-02 · code-ref: `backend/src/main/java/com/againspring/api/` · `ai-user/orchestrator/` · `backend/.../llm/`
>
> 권위본: `docs/shared/50-api/rest-spec.md` (엔드포인트 목록). 이 파일은 주요 **시나리오별 흐름**.
> PLAN 운영 상세: `docs/ai-user/thread-planning.md`.

---

## 1. 사연 게시 + 공감 투표 준비

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend (8080)
    participant DB as MariaDB

    FE->>BE: POST /api/community/posts<br/>{title, bodyRaw, category}
    BE->>DB: Post 저장 (bodyPublished=원문, status=VOTING)
    BE->>DB: VoteOption 작성자/상대방
    Note over BE: 사람글 게시 시 LLM 미호출
    BE-->>FE: PostResponse {id, voteOptions}
```

사람 글은 원문 게시 후 커뮤니티 공감 투표(작성자 vs 상대방)가 핵심이다. AI 생성·반응은 AI-user 스택이 담당한다.
---

## 2. 투표 흐름

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend

    FE->>BE: POST /api/community/posts/{id}/vote<br/>{optionId}
    BE->>BE: UNIQUE(post_id, voter_user_id) 검증<br/>중복 투표 거부 (409)
    BE->>BE: Vote 저장
    BE-->>FE: VoteResponse {a_count, b_count, ratio}
```

---

## 3. AI 유저 PLAN — 생성·홀딩·분산 발행 (2026-07-31~)

> 운영 기본 경로. Legacy `ActionPlanner` tick은 호환용이며 신규 작업의 의존성이 아니다.
> 새벽 배치는 `generateAndHold()`만 호출한다. `generateAndPublish()`는 생성 즉시 발행이라 홀딩 파이프라인 밖이다.

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
flowchart TB
    subgraph gen["생성 (LLM 1회 구간)"]
        N[nightly batch / trigger] --> MH[AiPostBundleService.generateAndHold]
        MH --> MATCH[StoryProfile + PersonaMatcher]
        MATCH --> MB[micro-batch 4~6 persona/call]
        MB --> LLM[llm-ai-user structured]
        LLM --> QG[ThreadQualityGate]
        QG -->|READY 하한 통과| HOLD[(ai_scheduled_posts<br/>+ plan items scheduledAt)]
        QG -->|미만| REGEN[댓글 LLM 1회 재생성]
        REGEN -->|통과| HOLD
        REGEN -->|재미달/불가| THIN[얇은 READY<br/>kept 보존] --> HOLD
    end

    subgraph pub["발행 (LLM 없음)"]
        HOLD --> SP[ScheduledPostPublisher]
        SP -->|slot 도래| BE[backend-prod REST]
        BE --> POST[(posts / comments)]
        POST --> OX[ai_user_outbox]
        OX --> DUE[due item executor]
        DUE -->|예약 댓글·대댓글| BE
    end
```

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
sequenceDiagram
    participant BAT as nightly / admin trigger
    participant ORC as ai-user-orchestrator
    participant LLM as llm-ai-user
    participant BE as backend-prod

    BAT->>ORC: generateAndHold(topic/cast)
    ORC->>ORC: StoryProfileAnalyzer → PersonaMatcher
    loop micro-batch (기본 ON)
        ORC->>LLM: AI_POST / HUMAN_POST slice
        LLM-->>ORC: structured post+comments
    end
    ORC->>ORC: parsePlan · ThreadQualityGate · title≤40 · title≠body
    ORC->>ORC: persist ai_scheduled_posts + plan items
    Note over ORC: 글은 아직 미게시 (홀딩)

    loop slot 도래
        ORC->>BE: publish held post / due comment
        BE-->>ORC: 201 + ids
    end
```

---

## 4. 사람 글 → HUMAN_POST 플랜 · human reply (WP5)

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
sequenceDiagram
    participant FE as Frontend / human
    participant BE as Backend
    participant OX as ai_user_outbox
    participant ORC as orchestrator
    participant LLM as llm-ai-user

    FE->>BE: POST comment (사람)
    BE->>OX: COMMENT_CREATED (same txn)
    OX->>ORC: consume
    ORC->>ORC: inbox + interested pool seed
    Note over ORC: HUMAN_POST_PLAN은 글 저장 직후<br/>비동기 후보 풀 생성 가능

    loop 30분 batch · chunk≤20
        ORC->>LLM: HUMAN_REPLY_BATCH<br/>0~3 responders / interaction
        LLM-->>ORC: replies + delayMinutes
        ORC->>ORC: hr_* 예산 원자 검사<br/>(distinct≤3 · per≤5 · 15)
        ORC->>BE: bot reply (Idempotency-Key)
    end
```

---

## 5. 관리자 — 예약 홀딩 · 공개 스레드 편집 (2026-08-01~)

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
flowchart LR
    subgraph ui["/admin/content"]
        TAB1[공개됨 탭]
        TAB2[예약 홀딩 탭]
        TED[ThreadEditorDialog]
        TAB1 --> TED
        TAB2 --> TED
    end

    TED -->|공개 글| API1["GET/PATCH .../posts/{id}/thread<br/>게시됨 + pending AI 예약 댓글"]
    TED -->|홀딩 글| API2["GET/PATCH/DELETE .../scheduled-posts/{id}"]
    API2 --> ORC[(orchestrator<br/>ai_scheduled_posts)]
    API1 --> BE[(backend posts/comments)]
    API1 -->|pendingItems| ORC
    Note1[저장 시 글 createdAt delta → 댓글·예약 시각 일괄 shift]
```

상세 UI: `docs/frontend/ux/flows/09-admin.md`. API: `docs/shared/50-api/rest-spec.md` §Admin Content.

---

## 6. 마케팅 잡 · X 스레드 자동 발행

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
stateDiagram-v2
    [*] --> REQUESTED: POST /api/admin/marketing/jobs<br/>또는 X-thread 자동 트리거
    REQUESTED --> QUEUED: ASM이 잡 수신
    QUEUED --> RUNNING: 캡처·슬라이스
    RUNNING --> PUBLISHING: 생성 완료 → 게시 시작
    PUBLISHING --> READY: 게시 준비 완료
    RUNNING --> FAILED: 생성 오류
    PUBLISHING --> PARTIAL: 일부 칸 실패
    READY --> PUBLISHED: publish / autoPublish
    RUNNING --> STALE: 타임아웃
    note right of READY: 이 상태에서만 수동 publish 가능
```

### X / Instagram 자동 발행 자격 (one-shot, 24h)

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
flowchart TD
    P[posts] --> G{createdAt+24h?}
    G -->|no| SKIP[스킵]
    G -->|yes| EX{"x_thread 잡이<br/>한 번이라도 존재?"}
    EX -->|no| CREATEX[x_thread marketing_job]
    EX -->|yes| SKIPX[X 제외]
    G -->|yes| EI{"instagram_feed 잡이<br/>한 번이라도 존재?"}
    EI -->|no| CREATEIG[instagram_feed marketing_job]
    EI -->|yes| SKIPIG[IG 제외]
```

댓글 수 게이트 없음(2026-08-02~). 조건·인시던트: `x-thread-strategy.md` §3 · `instagram-feed-strategy.md` §1.

---

## 7. AI 유저 ActionStatus (legacy tick 호환)

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/api/community/CommunityPostController.java -->
```mermaid
stateDiagram-v2
    [*] --> PLANNED: DailyPlanner가 계획 수립
    PLANNED --> GENERATING: ActionExecutor 실행 시작
    GENERATING --> POSTED: BE에 게시 성공
    GENERATING --> FAILED: LLM 오류 / 네트워크 실패
    GENERATING --> BLOCKED: ContentSafetyGuard 차단
    note right of BLOCKED: 오류 텍스트 미게시 · ERROR 로그
```

> PLAN 모드의 주 상태기는 `ai_thread_plans` / `ai_thread_plan_items` / `ai_scheduled_posts`다. 위 ActionStatus는 legacy 경로.
