# 데이터 보존 정책

다시봄은 사용자가 입력한 갈등 원문을 **30일 후 자동 삭제**한다. 리포트(요약)는 영구 보관. 약관 제6조 명시.

## Source of truth

- BE 스케줄러: `backend/.../service/retention/RetentionScheduler.java` (cron `0 0 3 * * *`)
- 사용자 삭제: `backend/.../service/retention/UserDeletionService.java`
- 접근 로그: `backend/.../service/retention/AccessLogService.java`
- 인터셉터: `backend/.../config/AccessLogInterceptor.java`
- DB: `sessions.content_expires_at`, `turns.{content, mediator_message, mediator_summary_for_opponent}` (Flyway V1)

## 30일 원문 만료

### 대상 컬럼

| 테이블 | 컬럼 | 만료 동작 |
|---|---|---|
| `turns` | `content` (사용자 원문) | NULL 처리 |
| `turns` | `mediator_message` (LLM 응답) | NULL 처리 |
| `turns` | `mediator_summary_for_opponent` (중립 요약) | NULL 처리 |

만료 후에도 다음은 보존:
- 메타: `turns.id`, `turn_number`, `role`, `user_id`, `created_at`, `tokens_used`, `llm_latency_ms`
- 리포트: `reports` 전체 (요약·기여도·NVC·needs map만 있고 원문은 없음)
- 관계 그래프: `user_relationships`, `conflict_history`

### 스케줄러 동작

```java
@Scheduled(cron = "0 0 3 * * *")  // 매일 03:00 UTC
public void purgeExpiredContent() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(30);
    // sessions.status IN (COMPLETED, TERMINATED) 인 세션의 turns.content 등을 NULL
}
```

대상은 `COMPLETED` / `TERMINATED` 상태 세션만 — 진행 중 세션은 절대 만료되지 않음.

### `content_expires_at` 컬럼

`sessions.content_expires_at` (Flyway V1)은 세션 생성 시 `now() + 30 days`로 세팅. 스케줄러가 이 시점 기준으로 만료 대상 선별.

## 사용자 요청 즉시 삭제

```
DELETE /api/users/me
  ↓
UserDeletionService:
  1. users.deleted_at = now() (소프트 삭제)
  2. 해당 user의 sessions/turns 원문 즉시 NULL
  3. reports는 유지 (상대방도 접근 가능하므로)
  4. user_relationships의 user 측 nullify
```

상대방 입장에서 본인이 참여한 세션·리포트는 계속 조회 가능하되, 탈퇴한 사용자의 닉네임은 "탈퇴한 사용자"로 마스킹.

## 세션 이력 화면 노출

`GET /api/sessions/me` 응답에 만료된 세션이 포함되면 다음만 노출:
- `id`, `relationType`, `status`, `createdAt`, `completedAt`, `partnerName`
- 원문/중재자 메시지는 응답에 포함되지 않음

`GET /api/sessions/{id}` 만료 후:
- `turns[].content` = `null`
- `turns[].mediatorMessage` = `null`
- 클라이언트는 "30일이 지나 원문은 자동 삭제되었어요" 표시

리포트(`GET /api/reports/{id}`)는 영향 없음 — 영구 조회 가능.

## 위기 감지 데이터

위기 키워드로 종료된 세션도 동일하게 30일 후 원문 만료. 단, `safety_audit_log`는 **개인정보 마스킹** 후 더 오래 보관 (감사 목적). 자세한 마스킹 정책은 `safety/SafetyAuditLogger`.

## 접근 로그

`AccessLogInterceptor` → `AccessLogService`가 모든 인증 API 호출 시 다음을 기록:
- `user_id`, `endpoint`, `method`, `status_code`, `latency`, `ip`, `created_at`

이 로그는 **개인정보가 아니므로** 90일 보관 (별도 정책). 사용자 탈퇴 시 user_id 부분은 NULL 처리.

## LLM 호출 로그

`llm_call_logs` 테이블은 30일 이후 자동 정리는 안 됨 (별도 정책 미정). 단, **프롬프트/응답 본문은 저장하지 않음** — `tokens_used`, `latency_ms`, `outcome`만 기록.

## 만료 시점 사용자 알림

- 세션 완료 직후: "30일 후 원문은 자동 삭제됩니다" 안내 표시
- 만료 1일 전: (현재 미구현 — 향후 검토)
- 만료 후 조회: "30일이 지나 원문은 자동 삭제되었어요" 화면

## 약관 명시 (제6조)

> 1. 서비스는 이용자의 입력 원문을 **최대 30일** 보관한 후 자동 삭제합니다.
> 2. 이용자가 삭제를 요청하는 경우 즉시 해당 데이터를 삭제합니다.
> 3. 세션 이력 화면에서 보이는 내용은 AI가 생성한 요약본과 결과 리포트로 한정되며, 원문 대화는 포함되지 않습니다.
> 4. 서비스는 이용자의 데이터를 AI 학습용으로 사용하지 않습니다.

상세는 [terms-of-service.md](./terms-of-service.md).

## 변경 시 절차

1. 보존 기간 변경: `RetentionScheduler` 상수 + `sessions.content_expires_at` 계산 로직 + 본 문서 + 약관 동시 갱신
2. 새 만료 대상 컬럼 추가: 스케줄러 update 쿼리 보강 + Flyway 마이그레이션 (필요 시)
3. 사용자 알림 UX 변경: FE의 세션 이력/리포트 페이지 카피 갱신
