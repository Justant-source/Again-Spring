# Session & Chat API — 세션 생성 · 채팅 · 초대 · 정리

> 상담 세션의 전체 생명주기(생성 → 대화 → 초대 → 정리)를 담당하는 API.
> Solo 모드와 Duo 모드(app.features.duo-mode=true 또는 TESTER 역할)를 지원합니다.

## Source of truth

| 항목 | 위치 |
|---|---|
| 세션 컨트롤러 | `backend/src/main/java/com/againspring/api/SessionController.java` |
| 메시지 컨트롤러 | `backend/src/main/java/com/againspring/api/MessageController.java` |
| 세션 서비스 | `backend/src/main/java/com/againspring/service/SessionService.java` |
| 채팅 서비스 | `backend/src/main/java/com/againspring/service/CancelableChatService.java` |
| 상태 머신 | `backend/src/main/java/com/againspring/service/SessionStateMachine.java` |
| DB 테이블 | `sessions`, `messages`, `turns` |

## 세션 API 엔드포인트

| Method | Path | Auth | 요청 | 응답 | 상태코드 |
|---|---|---|---|---|---|
| `POST` | `/api/sessions` | JWT 필수 | `CreateSessionRequest` | `CreateSessionResponse` | 201 / 422 (위기) |
| `GET` | `/api/sessions/me` | JWT 필수 | — | `List<SessionListItemResponse>` | 200 |
| `GET` | `/api/sessions/{id}` | JWT 필수 | — | `SessionResponse` | 200 / 403 / 404 |
| `POST` | `/api/sessions/join/{token}` | 공개 (principal 선택) | `JoinSessionRequest` | `SessionResponse` | 200 / 403 / 409 / 410 |
| `GET` | `/api/sessions/{id}/status` | 공개 | — | `SessionStatusResponse` | 200 / 404 |
| `DELETE` | `/api/sessions/{id}` | JWT 필수 | — | — | 204 / 403 / 404 |

## 메시지(채팅) API 엔드포인트

| Method | Path | Auth | 요청 | 응답 | 상태코드 |
|---|---|---|---|---|---|
| `POST` | `/api/sessions/{id}/messages` | JWT 필수 | `SendMessageRequest` | `ChatTurnResponse` | 200 / 409 (위기) |
| `GET` | `/api/sessions/{id}/messages` | JWT 필수 | `?since=epochMs` | `List<MessageResponse>` | 200 |
| `GET` | `/api/sessions/{id}/partner-messages` | JWT 필수 | — | `List<MessageMetadataResponse>` | 200 |
| `GET` | `/api/sessions/{id}/partner-status` | JWT 필수 | — | `PartnerStatusResponse` | 200 |
| `GET` | `/api/sessions/{id}/invocation-status` | JWT 필수 | — | `{ inProgress, sender, lastUserMessageAt }` | 200 |
| `POST` | `/api/sessions/{id}/finalize` | JWT 필수 | — | `FinalizationResponse` | 200 |
| `POST` | `/api/sessions/{id}/finalize/agree` | JWT 필수 | — | `FinalizationResponse` | 200 |
| `POST` | `/api/sessions/{id}/finalize/decline` | JWT 필수 | — | — | 200 |
| `GET` | `/api/sessions/{id}/invite` | JWT 필수 (Duo 게이팅) | — | `InviteTokenResponse` | 200 / 403 |
| `POST` | `/api/sessions/{id}/invite` | JWT 필수 (Duo 게이팅) | — | `InviteTokenResponse` | 200 / 403 |

## 세션 상태 머신

```mermaid
stateDiagram-v2
    direction LR
    [*] --> CHATTING_SOLO : POST /api/sessions (세션 생성)
    CHATTING_SOLO --> CHATTING_DUO : POST /api/sessions/join/{token}<br/>(상대방 참여 + duoMode/TESTER)
    CHATTING_SOLO --> FINALIZING : POST /messages/{id}/finalize<br/>(5턴 이상)
    CHATTING_DUO --> FINALIZING : 한쪽이 finalize 요청<br/>(5턴 이상)
    FINALIZING --> CHATTING_SOLO : POST finalize/decline
    FINALIZING --> CHATTING_DUO : POST finalize/decline (Duo)
    FINALIZING --> COMPLETED : finalize/agree (양쪽 동의 or Solo)
    CHATTING_SOLO --> CANCELLED : DELETE /api/sessions/{id}
    CHATTING_DUO --> CANCELLED : DELETE /api/sessions/{id}
    COMPLETED --> [*]
    CANCELLED --> [*]
```

## 메시지 전송 · 취소(Cancelable) 흐름

```mermaid
sequenceDiagram
    participant FE
    participant MC as MessageController
    participant CCS as CancelableChatService
    participant LLM as Claude CLI

    FE->>MC: POST /messages {content}
    MC->>CCS: acceptUserMessage(sessionId, sender, content)
    Note over CCS: 1) 사용자 메시지 즉시 저장 (<100ms)
    Note over CCS: 2) 진행 중인 LLM invocation 취소

    alt 위기 키워드 감지
        CCS-->>MC: crisisLevel=1
        MC-->>FE: 409 Conflict {crisis: true}
    else 정상
        CCS-->>MC: ChatAcceptResult
        MC-->>FE: 200 OK {userMsg, mediatorMessages:null}
        MC->>CCS: beginInvocation(sessionId, sender) [비동기]
        CCS->>LLM: claude --print "..."
        LLM-->>CCS: AI 응답
        CCS->>CCS: 응답 저장 (MEDIATOR_TO_A/B)

        loop FE 폴링
            FE->>MC: GET /messages?since={lastTs}
            MC-->>FE: List<MessageResponse>
        end
    end
```

## 새로고침 후 타이핑 상태 복원

```mermaid
sequenceDiagram
    participant FE
    participant MC as MessageController
    participant CCS as CancelableChatService

    Note over FE: 새로고침 발생
    FE->>MC: GET /invocation-status
    MC->>CCS: isInvocationActive(sessionId, sender)
    MC->>MC: findLastMessageAtBySender(...)
    MC-->>FE: { inProgress: true, lastUserMessageAt: "..." }
    Note over FE: inProgress=true → TypingBubble 표시
    Note over FE: lastUserMessageAt 기준으로<br/>새 mediator 응답 판별
```

## Duo 초대 · 참여 흐름

> 초대/참여 엔드포인트는 `app.features.duo-mode=true` 또는 `ROLE_TESTER` 가 있어야 접근 가능.
> 그 외 403 DUO_MODE_DISABLED 반환.

```mermaid
sequenceDiagram
    participant A as 사용자 A (세션 생성자)
    participant B as 사용자 B (초대받는 사람)
    participant SC as SessionController
    participant MC as MessageController

    A->>MC: POST /api/sessions/{id}/invite
    MC-->>A: { token, inviteUrl }

    A->>B: 초대 URL 공유

    B->>SC: POST /api/sessions/join/{token}
    SC->>SC: 토큰 검증 (만료/이미 사용 여부)
    alt 토큰 만료
        SC-->>B: 410 Gone
    else 이미 참여자 있음
        SC-->>B: 409 Conflict
    else 정상
        SC->>SC: session.inviteeUserId = B.userId
        SC->>SC: 상태 CHATTING_SOLO → CHATTING_DUO
        SC-->>B: 200 OK SessionResponse
    end
```

## 정리(Finalize) 흐름

```mermaid
sequenceDiagram
    participant A as 사용자 A
    participant B as 사용자 B
    participant MC as MessageController

    A->>MC: POST /finalize (5턴 이상 필요)
    MC-->>A: { status: "PENDING_B_AGREE" }
    MC-->>B: (폴링으로 정리 요청 감지)

    alt B가 동의
        B->>MC: POST /finalize/agree
        MC-->>B: { status: "COMPLETED" }
        Note over A,B: 세션 COMPLETED 전이
    else B가 거절
        B->>MC: POST /finalize/decline
        MC-->>B: { status: "DECLINED" }
        Note over A,B: 대화 계속
    end
```

## 특수 상태코드

| 코드 | 경로 | 의미 |
|---|---|---|
| 409 | `POST /messages` | 위기 키워드 감지 — FE는 CrisisModal 즉시 표시 |
| 410 | `POST /sessions/join/{token}` | 초대 토큰 만료 |
| 409 | `POST /sessions/join/{token}` | 세션에 이미 참여자 존재 |
| 422 | `POST /sessions` | 세션 설명에 위기 키워드 포함 |
| 403 | `GET/POST /invite`, `POST /join` | duo-mode 비활성 (DUO_MODE_DISABLED) |

## 변경 시 절차

- 세션 상태 추가: `SessionStatus` enum + `SessionStateMachine` + 이 문서 stateDiagram 동시 수정
- Duo 게이팅 해제: `app.features.duo-mode=true` 설정 or TESTER 역할 부여 (`PATCH /api/admin/users/{id}/roles`)
