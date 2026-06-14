# API 흐름 — 시퀀스 다이어그램

> last-verified: 2026-06-14 · code-ref: `backend/src/main/java/com/againspring/api/` · `backend/.../llm/`
>
> 권위본: `docs/shared/api/rest-spec.md` (엔드포인트 목록). 이 파일은 주요 **시나리오별 흐름**.

---

## 1. 사연 게시 + 배심원 생성 흐름

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend (8080)
    participant PS as PromptSanitizer
    participant LW as llm-worker (8090)
    participant Claude as Claude API

    FE->>BE: POST /api/community/posts<br/>{title, bodyRaw, category, ...}
    BE->>PS: sanitize(userInput)
    Note over PS: 제어문자 제거 · <> 전각 변환 · 5000자 캡
    PS-->>BE: 정제된 입력
    BE->>BE: Post 저장 (status=DRAFT)
    BE->>LW: POST /v1/invoke<br/>{prompt: jury_persona.md + <user_input>, model: haiku}
    LW->>Claude: Claude CLI/API 호출
    Claude-->>LW: 배심원 코멘트 (공감·관점)
    LW-->>BE: WorkerInvokeResponse
    Note over BE: forbidden-words 검사<br/>판결·처방·승패 표현 금지
    BE->>BE: Juror 저장 · Post status=VOTING
    BE-->>FE: PostResponse {id, jurors, voteOptions}
```

---

## 2. 투표 흐름

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

## 3. AI 유저 봇 게시 흐름 (오케스트레이터 → BE)

```mermaid
sequenceDiagram
    participant ORC as ai-user-orchestrator (8096)
    participant LLM as llm-ai-user (8092)
    participant CSG as ContentSafetyGuard
    participant BE as Backend (8080)

    ORC->>ORC: ActionPlanner: pickPersona() · pickAction()
    ORC->>LLM: POST /generate/post<br/>{voiceProfile, topicSeed, ...}
    LLM->>LLM: PromptAssembler + LlmErrorSignature 검사 (L1)
    LLM-->>ORC: GenResponse {text}
    ORC->>CSG: check(text)
    Note over CSG: PII · crisis · hate · LLM 오류 시그니처 (L2)<br/>BLOCKED → 미게시 · ERROR 로그
    CSG-->>ORC: OK
    ORC->>BE: POST /api/community/posts<br/>(Bot 전용 엔드포인트, X-Bot-Token 인증)
    BE-->>ORC: 201 Created
    ORC->>ORC: ActionStatus = POSTED<br/>loadRecentBodies에 저장 (가드 통과분만)
```

---

## 4. 마케팅 잡 상태 흐름

```mermaid
stateDiagram-v2
    [*] --> REQUESTED: POST /api/admin/marketing/jobs
    REQUESTED --> QUEUED: ASM이 잡 수신
    QUEUED --> RUNNING: 콘텐츠 생성 시작
    RUNNING --> PUBLISHING: 생성 완료 → 게시 시작
    PUBLISHING --> READY: 게시 준비 완료
    RUNNING --> FAILED: 생성 오류
    PUBLISHING --> PARTIAL: 일부 플랫폼 실패
    READY --> PUBLISHED: POST /api/admin/marketing/jobs/{id}/publish
    RUNNING --> STALE: 타임아웃 (active job 중단)
    note right of READY: 이 상태에서만 publish 가능
```

---

## 5. AI 유저 ActionStatus 상태 흐름

```mermaid
stateDiagram-v2
    [*] --> PLANNED: DailyPlanner가 계획 수립
    PLANNED --> GENERATING: ActionExecutor 실행 시작
    GENERATING --> POSTED: BE에 게시 성공
    GENERATING --> FAILED: LLM 오류 / 네트워크 실패
    GENERATING --> BLOCKED: ContentSafetyGuard 차단
    note right of BLOCKED: 오류 텍스트 미게시 · ERROR 로그
```
