# 데이터베이스 스키마 (MariaDB 11)

## Source of truth

- Flyway 마이그레이션: `backend/src/main/resources/db/migration/V*.sql`
- JPA 엔티티: `backend/src/main/java/com/againspring/domain/**`
- Repository: `backend/src/main/java/com/againspring/repository/**`

## 환경

| 환경 | 호스트 | DB 이름 | 컨테이너 |
|---|---|---|---|
| local | localhost:3306 | `againspring` | `againspring-mariadb` |
| dev | dev 서버 internal + 호스트:3309 | `againspring_dev` | `againspring-mariadb-dev` |
| prod | prod 서버 internal only | `againspring` | `againspring-mariadb-prod` |

설정: `backend/src/main/resources/application*.yml` + `env/.env.{dev,prod}.example`

CHARSET: `utf8mb4` / COLLATION: `utf8mb4_unicode_ci` / TIMEZONE: `UTC`

## Flyway 마이그레이션 흐름

| 버전 | 파일 | 핵심 변경 |
|---|---|---|
| V1 | `V1__init.sql` | 초기 스키마: users, sessions, turns, reports, user_relationships, conflict_history, temperature_history, llm_call_logs |
| V2 | `V2__add_oauth_and_guest.sql` | users에 `provider`, `provider_id` 추가 + email/password nullable + guest_sessions 신규 |
| V3 | `V3__add_email_verification.sql` | email_verifications 신규 |
| V4 | `V4__add_security_tables.sql` | password_reset_tokens, revoked_tokens 신규 |
| V5 | `V5__remove_temperature.sql` | reports/user_relationships/conflict_history의 temperature 제거 + temperature_history 삭제 |
| V7 | `V7__add_messages_table_and_session_columns.sql` | **V1.5 카톡식 전환**: messages 테이블 신규 + sessions 컬럼 6개 추가 + turns 표기 deprecated |
| V8 | `V8__add_session_psychology_tracking.sql` | **턴 간 심리 점수 피드백**: sessions에 `horsemen_history` JSON, `nvc_completion_history` JSON, `current_focus` VARCHAR(50) 추가 |
| V9 | `V9__add_duo_balance_tracking.sql` | **Duo 균형 추적**: sessions에 `user_a_emotion_intensity` DECIMAL(3,2), `user_b_emotion_intensity` DECIMAL(3,2) 추가 |

**dev 프로파일은 Flyway disabled** (Hibernate ddl-auto=update 사용). prod 프로파일은 Flyway 적용 + ddl-auto=validate.

## 테이블 일람

| 테이블 | 역할 | PK | 주요 인덱스 |
|---|---|---|---|
| `users` | 회원/게스트 계정 | `id` (VARCHAR 32) | `idx_users_email`, `uk_users_provider (provider, provider_id)` |
| `sessions` | 중재 세션 메타 | `id` (VARCHAR 32) | `idx_sessions_invite_token`, `idx_sessions_status`, `idx_sessions_content_expires_at` |
| `messages` | 카톡 메시지 (V7) | `id` (BIGINT auto) | `(session_id, created_at)`, `(session_id, sender)` |
| `turns` | **[DEPRECATED V1.5 이후]** 세션의 턴 (1~6) | `id` (BIGINT auto) | `uk_turns_session_number (session_id, turn_number)` |
| `reports` | 분석 리포트 | `id` (VARCHAR 32) | `idx_reports_session_id` (UNIQUE), `idx_reports_created_at` |
| `user_relationships` | A-B 관계 집계 | `id` (BIGINT auto) | `uk_user_relationships_a_b_type` |
| `conflict_history` | 세션 이력 행 | `id` (BIGINT auto) | `idx_conflict_history_user_pair` |
| `llm_call_logs` | LLM 감사 로그 | `id` (BIGINT auto) | `idx_llm_call_logs_session_id`, `idx_llm_call_logs_correlation_id` |
| `guest_sessions` | 초대 토큰별 게스트 ID 지속성 | `id` (BIGINT auto) | `idx_guest_sessions_invite_token` |
| `email_verifications` | 회원가입 6자리 코드 | `id` (BIGINT auto) | `idx_ev_email`, `idx_ev_expires` |
| `password_reset_tokens` | 비밀번호 재설정 토큰 | `id` (BIGINT auto) | `idx_prt_token` (UNIQUE), `idx_prt_expires` |
| `revoked_tokens` | JWT 블랙리스트 | `id` (BIGINT auto) | `idx_rt_jti` (UNIQUE), `idx_rt_expires` |

## 핵심 테이블 상세

### `users` (V1 + V2)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | VARCHAR(32) PK | ULID-style |
| `email` | VARCHAR(255) | nullable (소셜 가입 시) |
| `password_hash` | VARCHAR(255) | nullable (소셜 / 게스트) |
| `nickname` | VARCHAR(100) | 필수 |
| `provider` | VARCHAR(50) | google/kakao/naver/null |
| `provider_id` | VARCHAR(255) | OAuth provider의 user id |
| `communication_style` | VARCHAR(50) | wave/mountain/flame/leaf/moon/star |
| `onboarding_answers` | JSON | List<Integer> |
| `roles` | JSON | List<String>, default `["USER"]` |
| `deleted_at` | TIMESTAMP(3) | 소프트 삭제 |
| `created_at`, `updated_at` | TIMESTAMP(3) | 필수 |

UNIQUE: `(provider, provider_id)` — OAuth 중복 가입 방지.

### `sessions` (V1 + V7)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | VARCHAR(32) PK | |
| `created_by_user_id` | VARCHAR(32) | A |
| `invitee_user_id` | VARCHAR(32) | B (회원이면) |
| `invitee_guest_name` | VARCHAR(100) | B가 게스트면 표시명 |
| `invite_token` | VARCHAR(64) UNIQUE | nullable |
| `invite_expires_at` | TIMESTAMP(3) | 24h |
| `relationship_type` | VARCHAR(32) | RelationType enum |
| `conflict_type` | VARCHAR(32) | factual/difference/mixed |
| `category` | JSON | `{major, middle, minor, customMinor?}` |
| `status` | VARCHAR(32) | **V7**: 'chatting_solo' \| 'chatting_duo' \| 'awaiting_finalization' \| 'completed' \| 'terminated' (ENUM이 아닌 VARCHAR 32로 확장 가능) |
| `description` | LONGTEXT | **V7**: NULL 허용 (V1.5는 첫 메시지로 대체) |
| `solo_mode` | BOOLEAN | **V7**: DEFAULT TRUE |
| `user_a_message_count` | INT | **V7**: DEFAULT 0 |
| `user_b_message_count` | INT | **V7**: DEFAULT 0 |
| `partner_joined_at` | TIMESTAMP(3) | **V7**: Solo→Duo 전이 시각 |
| `finalize_suggested_at` | TIMESTAMP(3) | **V7**: 종료 권유 시각 |
| `finalize_agreed_by_a` | BOOLEAN | **V7**: DEFAULT FALSE |
| `finalize_agreed_by_b` | BOOLEAN | **V7**: DEFAULT FALSE |
| `horsemen_history` | JSON | **V8**: 턴별 4 Horsemen 강도 누적 `[{turn, sender, criticism, contempt, defensiveness, stonewalling}, ...]` |
| `nvc_completion_history` | JSON | **V8**: 턴별 NVC 4단계 완성 여부 `[{turn, sender, observation, feeling, need, request}, ...]` |
| `current_focus` | VARCHAR(50) | **V8**: `early_grounding \| deepen \| perspective \| solution` |
| `user_a_emotion_intensity` | DECIMAL(3,2) | **V9**: A 누적 감정 강도 0.00~1.00 (Duo 균형 보정용) |
| `user_b_emotion_intensity` | DECIMAL(3,2) | **V9**: B 누적 감정 강도 0.00~1.00 |
| `report_id` | VARCHAR(32) | |
| `content_expires_at` | TIMESTAMP(3) | now+30일 (RetentionScheduler 기준) |
| `crisis_flags` | JSON | List<String> |
| `completed_at` | TIMESTAMP(3) | |
| `created_at`, `updated_at` | TIMESTAMP(3) | 필수 |

### `turns` (V1)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto PK | |
| `session_id` | VARCHAR(32) | FK → sessions, ON DELETE CASCADE |
| `turn_number` | INT | 1~6 |
| `role` | VARCHAR(32) | A/B/MEDIATOR |
| `user_id` | VARCHAR(32) | nullable (mediator turn) |
| `content` | LONGTEXT | **30일 후 NULL** (RetentionScheduler) |
| `mediator_message` | LONGTEXT | **30일 후 NULL** |
| `mediator_summary_for_opponent` | LONGTEXT | **30일 후 NULL** — 앵커링 방지 중립 요약 |
| `is_perspective_taking` | BOOLEAN | Turn 5,6 표시 |
| `skipped` | BOOLEAN | |
| `tokens_used` | INT | LLM 토큰 추정 |
| `llm_latency_ms` | BIGINT | |
| `created_at` | TIMESTAMP(3) | 필수 |

UNIQUE: `(session_id, turn_number)` — 같은 턴 중복 작성 방지.

**주의**: V1.5 이후 신규 데이터는 `messages` 테이블에 저장됨. `turns` 테이블은 히스토리 보존만.

### `messages` (V7, 카톡식 대화)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto PK | |
| `session_id` | VARCHAR(32) | FK → sessions, ON DELETE CASCADE |
| `sender` | VARCHAR(32) | USER_A, USER_B, MEDIATOR_TO_A, MEDIATOR_TO_B |
| `content` | LONGTEXT | 메시지 본문 (**30일 후 NULL**) |
| `char_count` | INT | 문자 수 (블러링 용도) |
| `is_finalize_suggestion` | BOOLEAN | DEFAULT FALSE — 종료 권유 메시지 표시 |
| `is_partner_join_notice` | BOOLEAN | DEFAULT FALSE — Solo→Duo 전이 알림 |
| `crisis_level` | INT | NULL / 1(경고) / 2(위험) / 3(긴급) |
| `llm_model` | VARCHAR(50) | claude-haiku-4-5-20251001 등 |
| `tokens_used` | INT | LLM 소비 토큰 |
| `llm_latency_ms` | BIGINT | LLM 응답 시간 |
| `created_at` | TIMESTAMP(3) | 필수 |

**인덱스**:
- `(session_id, created_at)` — 메시지 조회
- `(session_id, sender)` — 발신자별 필터링

FK: `session_id` → `sessions(id)` ON DELETE CASCADE

### `turns` (V1 — **DEPRECATED V1.5 이후**)

**V1.5 카톡식 전환 이후 신규 데이터는 저장되지 않습니다.** 기존 운영 데이터는 보존되며, 마이그레이션이 필요한 경우 BE 팀에 문의하세요.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto PK | |
| `session_id` | VARCHAR(32) | FK → sessions, ON DELETE CASCADE |
| `turn_number` | INT | 1~6 |
| `role` | VARCHAR(32) | A/B/MEDIATOR |
| `user_id` | VARCHAR(32) | nullable (mediator turn) |
| `content` | LONGTEXT | **30일 후 NULL** (RetentionScheduler) |
| `mediator_message` | LONGTEXT | **30일 후 NULL** |
| `mediator_summary_for_opponent` | LONGTEXT | **30일 후 NULL** — 앵커링 방지 중립 요약 |
| `is_perspective_taking` | BOOLEAN | Turn 5,6 표시 |
| `skipped` | BOOLEAN | |
| `tokens_used` | INT | LLM 토큰 추정 |
| `llm_latency_ms` | BIGINT | |
| `created_at` | TIMESTAMP(3) | 필수 |

### `reports` (V1, V5에서 temperature 제거)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | VARCHAR(32) PK | |
| `session_id` | VARCHAR(32) UNIQUE | 1:1 |
| `participant_a`, `participant_b` | JSON | 닉네임 스냅샷 |
| `conflict_type` | VARCHAR(32) | |
| `solo_mode` | BOOLEAN | |
| `contribution_ratio` | JSON | `{a, b, label: {a, b}}` |
| `needs_map` | JSON | 욕구 차이 지도 |
| `four_horsemen` | JSON | 내부 점수 (UI 노출 정책 별도) |
| `nvc_scripts` | JSON | aToB / bToA |
| `repair_suggestions` | JSON | List<String> |
| `llm_provider`, `llm_call_count`, `generation_duration_ms` | 메타 |
| `a_pattern_feedback`, `suggested_approach`, `invite_again_cta` | LONGTEXT | Solo 모드 전용 |
| `created_at` | TIMESTAMP(3) | |

### `user_relationships` (V1, V5)

A-B 관계의 집계. Neo4j 대체용.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `user_a_id` | VARCHAR(32) | canonical: smaller |
| `user_b_id` | VARCHAR(32) | nullable (게스트 시 user_b_guest_name) |
| `user_b_guest_name` | VARCHAR(100) | |
| `relationship_type` | VARCHAR(32) | |
| `first_session_at`, `last_session_at` | TIMESTAMP(3) | |
| `session_count` | INT | |

UNIQUE: `(user_a_id, user_b_id, relationship_type)` — 같은 페어 + 동일 관계는 1행.

### `conflict_history` (V1, V5)

세션 단위 행. 분석/이력 화면용.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `session_id` | VARCHAR(32) | |
| `user_a_id`, `user_b_id` | VARCHAR(32) | |
| `relationship_type`, `conflict_type` | VARCHAR(32) | |
| `created_at` | TIMESTAMP(3) | |

### `llm_call_logs` (V1)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `correlation_id` | VARCHAR(64) | 호출 추적 |
| `provider` | VARCHAR(50) | claude-code |
| `session_id` | VARCHAR(32) | |
| `turn_number` | INT | |
| `tokens_used`, `latency_ms`, `input_length`, `output_length` | 메타 |
| `outcome` | VARCHAR(32) | success/fallback/timeout/error |
| `error_code` | VARCHAR(64) | |
| `created_at` | TIMESTAMP(3) | |

**프롬프트/응답 본문은 저장하지 않음** — 길이와 결과만.

### `guest_sessions` (V2)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `invite_token` | VARCHAR(64) | |
| `guest_id` | VARCHAR(32) | "Guest-XXXXXX" 형식 |
| `guest_nickname` | VARCHAR(100) | |
| `created_at`, `expires_at` | TIMESTAMP(3) | |

같은 초대 링크로 재방문하는 게스트가 동일 ID를 유지하기 위함.

### `email_verifications` (V3)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `email` | VARCHAR(255) | |
| `code` | VARCHAR(6) | |
| `created_at`, `expires_at` | TIMESTAMP(3) | 만료 10분 |
| `used` | BOOLEAN | default false |

### `password_reset_tokens` (V4)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `email` | VARCHAR(255) | |
| `token` | VARCHAR(64) UNIQUE | |
| `created_at`, `expires_at` | TIMESTAMP(3) | 만료 30분 |
| `used` | BOOLEAN | |

### `revoked_tokens` (V4)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT auto | |
| `jti` | VARCHAR(64) UNIQUE | JWT ID |
| `user_id` | VARCHAR(64) | |
| `revoked_at`, `expires_at` | TIMESTAMP(3) | |

`RevokedTokenCleanupScheduler`(매일 04:00 UTC)가 `expires_at < now()` 행 삭제.

## 마이그레이션 추가 절차

```bash
# 다음 버전 번호 확인
ls backend/src/main/resources/db/migration/

# V6__<descriptive_name>.sql 작성
$EDITOR backend/src/main/resources/db/migration/V6__add_xxx.sql

# dev에서 검증
cd env
docker compose -f docker-compose.dev.yml up -d --build
docker logs againspring-backend-dev | grep -i flyway
```

규칙:
- 파일명: `V<n>__<snake_case>.sql` (밑줄 두 개)
- ROLLBACK은 별도 마이그레이션으로
- ALTER TABLE 시 `IF EXISTS` / `IF NOT EXISTS` 사용 (재실행 안전)

## 데이터 보존

자세한 정책은 [policies/data-retention.md](../policies/data-retention.md). 핵심:

- `turns.{content, mediator_message, mediator_summary_for_opponent}` → 30일 후 NULL
- `reports`는 영구 보관
- `users.deleted_at` → 소프트 삭제 (즉시 원문 삭제)
