# 패키지 구조

루트 패키지: `com.againspring`

## Source of truth

| 항목 | 위치 |
|---|---|
| 컨트롤러 | `backend/src/main/java/com/againspring/api/**/*Controller.java` |
| 서비스 | `backend/src/main/java/com/againspring/service/**/*.java` |
| 도메인 | `backend/src/main/java/com/againspring/domain/*.java` |
| 마이그레이션 | `backend/src/main/resources/db/migration/V1~V24*.sql` |

## 패키지 계층 개요

```flowchart
flowchart TD
    subgraph API["api/ — REST 컨트롤러"]
        direction LR
        C1[AuthController\nOAuth2Controller\nHealthController]
        C2[CommunityPostController\nCommunityCommentController\nUserController\nFeedbackController]
        C3[admin/\nAdminDashboardController\nAdminUserController\nAdminHealthController]
        C4[AdminFeedbackController\nAdminPromptsController\nAdminTestController]
    end

    subgraph SVC["service/ — 비즈니스 로직"]
        direction LR
        S1[CommunityPostService\nJuryService\nPostCommentService]
        S2[AuthService\nUserService\nFeedbackService]
        S3[admin/\nsafety/\nretention/]
        S4[notify/\noauth/\ncategory/]
    end

    subgraph DOM["domain/ — JPA 엔티티"]
        direction LR
        D1[User · Post · PostComment\nVote · Juror · Feedback]
        D2[GuestSession · RevokedToken\nEmailVerification · PasswordResetToken]
    end

    subgraph INF["인프라"]
        direction LR
        I1[llm/remote/ — RemoteLlmProvider<br/>llm/bridge/ — ClaudeCodeBridge fallback]
        I2[safety/ — KeywordGuard·Crisis]
        I3[security/ — JWT·SecurityConfig]
        I4[config/ — OpenAPI·CORS·Async]
    end

    API --> SVC
    SVC --> DOM
    SVC --> INF
```

## 한 줄 책임

| 패키지 | 책임 |
|---|---|
| `api/` | REST 컨트롤러 + DTO (community, auth, user, feedback) |
| `api/admin/` | 관리자 컨트롤러 (Dashboard · User · Health) |
| `service/` | 비즈니스 로직, 트랜잭션 경계 |
| `service/admin/` | 관리자 기능 (통계 · 사용자 · 모니터링) |
| `service/community/` | 광장 서비스 (Post · Comment · Vote · Jury) |
| `service/notify/` | 위기 알림 이메일 · 피드백 이메일 발신 |
| `service/retention/` | 30일 보존 스케줄러 · 일일 통계 집계 |
| `domain/` | JPA 엔티티 + Enum |
| `domain/community/` | Post · PostComment · Vote · Juror |
| `repository/` | Spring Data JPA 인터페이스 |
| `repository/community/` | PostRepository · PostCommentRepository · VoteRepository · JurorRepository |
| `llm/remote/` | `RemoteLlmProvider` (기본) + HTTP 클라이언트 (againspring-llm 워커) |
| `llm/` | `LLMProvider` 인터페이스 + Prompt 어셈블 + 모니터링 |
| `safety/` | KeywordGuard · CrisisDetector · SafetyAuditLogger |
| `security/` | JwtFilter · SecurityConfig · RateLimitFilter · UserDetailsService |
| `config/` | 빈 설정 (CORS · Async · Scheduling · OpenAPI) |
| `common/` | 공통 예외 (BusinessException · GlobalExceptionHandler) |

## 트리

```
com.againspring/
├── AgainSpringApplication              # @SpringBootApplication 진입점
│
├── api/
│   ├── admin/
│   │   ├── AdminDashboardController    # GET /api/admin/dashboard/{summary,daily-stats,retention,crisis-recent,llm-failure-rate}
│   │   ├── AdminHealthController       # GET /api/admin/health/system
│   │   └── AdminUserController         # GET/DELETE/PATCH /api/admin/users/**
│   ├── AdminFeedbackController         # GET/PATCH /api/admin/feedbacks/**
│   ├── AdminPromptsController          # POST /api/admin/prompts/reload (app.admin.enabled)
│   ├── AdminTestController             # POST /api/admin/test/reset,/terminate (@Profile dev)
│   ├── AuthController                  # /api/auth/{signup,login,guest,logout,agree,forgot-password,reset-password,send-verification,check-nickname}
│   ├── FeedbackController              # POST /api/feedbacks
│   ├── HealthController                # GET /api/health
│   ├── MessageController               # /api/sessions/{id}/messages, /invite, /finalize/**
│   ├── OAuth2Controller                # POST /api/auth/oauth2/{provider}
│   ├── ReportController                # POST/GET /api/sessions/{id}/report, GET /api/reports/{id}
│   ├── SessionContextDebugController   # GET /api/admin/sessions/{id}/context (app.admin.enabled)
│   ├── SessionController               # /api/sessions, /me, /{id}, /join/{token}, /{id}/status
│   ├── UserController                  # /api/users/me, /password, /onboarding, /tutorial/complete, /history
│   ├── dto/
│   │   ├── request/
│   │   │   ├── AgreeReconfirmRequest
│   │   │   ├── ChangePasswordRequest
│   │   │   ├── CreateSessionRequest
│   │   │   ├── DeleteAccountRequest
│   │   │   ├── ForgotPasswordRequest
│   │   │   ├── GuestRequest
│   │   │   ├── JoinSessionRequest
│   │   │   ├── LoginRequest
│   │   │   ├── OAuthCallbackRequest
│   │   │   ├── OnboardingRequest
│   │   │   ├── ProgressTurnRequest
│   │   │   ├── ResetPasswordRequest
│   │   │   ├── SendMessageRequest
│   │   │   ├── SendVerificationRequest
│   │   │   ├── SignupRequest
│   │   │   ├── SubmitFeedbackRequest
│   │   │   ├── UpdateFeedbackStatusRequest
│   │   │   └── UpdateUserRequest
│   │   └── response/
│   │       ├── AdminUserDetailResponse
│   │       ├── AuthResponse
│   │       ├── ChatTurnResponse
│   │       ├── CreateSessionResponse
│   │       ├── CrisisMessageResponse
│   │       ├── CurrentTurnResponse
│   │       ├── FinalizationResponse
│   │       ├── InviteTokenResponse
│   │       ├── MessageMetadataResponse
│   │       ├── MessageResponse
│   │       ├── OnboardingResponse
│   │       ├── PartnerStatusResponse
│   │       ├── ReportResponse
│   │       ├── SessionHistoryResponse
│   │       ├── SessionListItemResponse
│   │       ├── SessionResponse
│   │       ├── SessionStatusResponse
│   │       ├── SystemHealthResponse
│   │       ├── TurnResponse
│   │       └── UserResponse
│
├── service/
│   ├── admin/
│   │   ├── AdminUserDetailService      # 사용자 상세 조회 + 역할 변경 위임
│   │   ├── CrisisMonitoringService     # 최근 위기 메시지 조회
│   │   ├── PmfStatsService             # DAU·세션 수·완료율·평균 턴
│   │   ├── RetentionCohortService      # 14일 코호트 리텐션 계산
│   │   └── SystemHealthService         # DB·LLM·디스크 상태 점검
│   ├── category/
│   │   └── CategoryCatalog             # categories.yml 로드 + 카테고리 조회
│   ├── context/
│   │   ├── CategoryRuleEnforcer        # 카테고리별 대화 룰 적용
│   │   ├── FirstMessageService         # 세션 생성 직후 mediator 첫마디 자동 저장
│   │   ├── FirstMessageTemplateLoader  # 248개 첫마디 템플릿 JSON 로드
│   │   ├── IssueContextDelta           # Phase D IssueContext 변경분
│   │   ├── IssueContextMerger          # 4슬롯 병합 로직
│   │   ├── PhaseDMetrics               # Phase D 지표 집계
│   │   ├── QuestionPrioritizer         # A·B PQ 우선순위 계산
│   │   ├── QuestionQueueDelta          # PQ 변경분
│   │   ├── QuestionQueueUpdater        # PQ 갱신
│   │   ├── RatioElementTagger          # 화해 기여도 요소 태깅
│   │   ├── UserStateAppender           # UserState 7종 누적
│   │   ├── WelcomeMessageGenerator     # B 진입 환영 메시지 조립
│   │   └── WelcomeQuestionResolver     # B 진입 시 PQ top1 질문 해결
│   ├── crisis/
│   │   └── CrisisDetector              # 위기 키워드 분석 (safety/ 위임)
│   ├── event/
│   │   ├── PartnerJoinedEvent
│   │   ├── SessionCompletedEvent
│   │   └── TurnCompletedEvent
│   ├── notify/
│   │   ├── CrisisFeedbackNotifier      # 위기 감지 시 관리자 이메일 알림
│   │   └── FeedbackEmailNotifier       # 피드백 제출 시 관리자 이메일 알림
│   ├── oauth/
│   │   ├── OAuthProviderService
│   │   └── OAuthUserInfo
│   ├── parser/
│   │   ├── ChatTurnMetaParser          # 응답 본문/<turn_meta> JSON 분리 (Phase B)
│   │   └── TurnResponseParser          # 레거시 6턴 모델 JSON 파서
│   ├── prompt/
│   │   ├── CategoryContextFragment     # <category_context> 블록
│   │   ├── ChatPromptAssembler         # 전체 프롬프트 조립 (Solo/Duo)
│   │   ├── DuoBalanceFormatter         # <duo_balance> 관심 분배 지시 (Phase C)
│   │   ├── IssueContextFragment        # Phase D IssueContext 주입
│   │   ├── PsychologyFeedbackFormatter # <psychology_feedback> 누적 점수 (Phase B)
│   │   ├── QuestionQueueFragment       # Phase D PQ 주입
│   │   ├── UserProfileFragment         # <user_profile> + MBTI 자연어 블록 (Phase A)
│   │   └── UserStateFragment           # Phase D UserState 7종 주입
│   ├── report/
│   │   ├── MetaphorSelector
│   │   ├── NeedsMapValidator
│   │   ├── NVCValidator
│   │   ├── RatioEnforcer               # 화해 기여도 클리핑 (40~60% 안전 범위)
│   │   ├── ReportGenerationService     # 리포트 생성 (Solo/Duo, async)
│   │   └── ReportResponseParser
│   ├── retention/
│   │   ├── AccessLogService            # 요청 접근 로그 저장
│   │   ├── DailyStatsAggregator        # @Scheduled 자정 집계 → daily_stats
│   │   ├── GuestSessionCleanupScheduler # 만료 게스트 세션 정리
│   │   ├── RetentionScheduler          # cron 0 0 3 * * * (30일 이후 데이터 삭제)
│   │   └── UserDeletionService         # PII 익명화 처리
│   ├── AdminRoleAssigner               # ADMIN 역할 전용 부여 (API 우회 불가)
│   ├── AdminTestService                # dev 테스트 데이터 초기화
│   ├── AuthService                     # 회원가입/로그인/게스트, JWT 발급
│   ├── CancelableChatService           # 메시지 즉시 저장 + 진행 중 LLM 취소 + async 재호출
│   ├── ChatService                     # 레거시 (CancelableChatService로 교체됨)
│   ├── EmailVerificationService        # 6자리 코드 발송/검증
│   ├── FeedbackService                 # 피드백 저장 + 알림
│   ├── GuestSessionRateLimiter         # 게스트 1일 세션 제한 (UserPermissionsConfig 주입)
│   ├── LogoutService                   # 토큰 폐기 → revoked_tokens
│   ├── PasswordResetService            # 재설정 토큰 발급/검증
│   ├── RevokedTokenCleanupScheduler    # 매일 04:00 UTC 폐기 토큰 정리
│   ├── SessionRoleResolver             # 사용자 → USER_A / USER_B 판정
│   ├── SessionService                  # 세션 CRUD + 초대 토큰 발급/검증
│   ├── SessionStateMachine             # 세션 상태 전이 단일 진실원천
│   ├── StyleCalculator                 # 온보딩 응답 → 6스타일 enum
│   └── UserService                     # User 조회/수정/탈퇴
│
├── domain/                             # JPA 엔티티 (Lombok @Entity)
│   ├── DailyStats                      # V19 일일 세션·사용자 집계
│   ├── EmailVerification               # V3
│   ├── Feedback                        # V16 피드백 수집
│   ├── GuestSession                    # V2 게스트 세션 추적
│   ├── Message                         # V7 카톡식 메시지 (solo/duo)
│   ├── PasswordResetToken              # V4
│   ├── Report                          # V1 + V23 Solo 리포트 확장
│   ├── RevokedToken                    # V4 폐기 JWT
│   ├── Session                         # V1 + V14(mediator_style)
│   ├── User                            # V1 + V11(mbti) + V17(consent) + V20(mustChangePassword) + V24(tutorialCompletedAt)
│   ├── enums/
│   │   ├── ConflictType                # FACTUAL · DIFFERENCE · MIXED
│   │   ├── MessageSender               # USER_A · USER_B · MEDIATOR_TO_A · MEDIATOR_TO_B · SYSTEM
│   │   ├── RelationType                # COUPLE · MARRIAGE · FRIEND · FAMILY · PARENT_CHILD · KOREAN_SPECIFIC
│   │   ├── ReportStatus                # PENDING · COMPLETED · FAILED
│   │   ├── SessionStatus               # CHATTING_SOLO · CHATTING_DUO · FINALIZING · COMPLETED · CANCELLED
│   │   └── TurnRole                    # A · B · MEDIATOR
│   └── relationship/
│       ├── LlmCallLog                  # llm_call_logs (모니터링)
│       └── UserRelationship            # A-B 집계 관계
│
├── repository/                         # 모두 JpaRepository<Entity, ID>
│   ├── DailyStatsRepository
│   ├── EmailVerificationRepository
│   ├── FeedbackRepository
│   ├── GuestSessionRepository
│   ├── LlmCallLogRepository
│   ├── MessageRepository
│   ├── PasswordResetTokenRepository
│   ├── ReportRepository
│   ├── RevokedTokenRepository
│   ├── SessionRepository
│   ├── UserRelationshipRepository
│   └── UserRepository
│
├── llm/
│   ├── LLMException                    # 추상 예외
│   ├── LLMProvider                     # 인터페이스
│   ├── LLMRequest, LLMResponse, PromptLayer
│   ├── remote/                         # ← 기본 provider (llm.provider=remote)
│   │   ├── RemoteLlmProvider           # @ConditionalOnProperty(remote) HTTP 클라이언트
│   │   ├── RemoteCancelableInvocation  # long-poll + 원격 취소 (extends CancelableInvocation)
│   │   └── dto/
│   │       ├── WorkerInvokeRequest/Response
│   │       ├── WorkerCreateInvocationRequest/Response
│   │       └── WorkerInvocationResultResponse
│   ├── bridge/                         # 긴급 fallback (llm.provider=claude-code)
│   │   ├── CancelableInvocation        # in-process 취소 핸들 (base class)
│   │   ├── ClaudeCodeBridge            # Claude CLI 직접 호출 (@ConditionalOnProperty)
│   │   ├── ClaudeCodeWorkerPool        # Semaphore(3) — fallback 전용
│   │   ├── MockLLMProvider             # 테스트 프로파일 (llm.provider=mock)
│   │   ├── PromptSanitizer             # injection 방지 (remote/bridge 공통)
│   │   └── exception/
│   │       ├── ClaudeCodeException
│   │       ├── LLMCapacityException
│   │       ├── LLMSanitizationException
│   │       └── LLMTimeoutException
│   ├── fallback/
│   │   └── FallbackResponses           # Claude 불가 시 안전 기본값
│   ├── monitoring/
│   │   └── LLMCallLogger               # llm_call_logs 기록
│   └── prompt/
│       ├── PromptAssembler             # 레이어 합성
│       └── PromptLoader                # shared/docs/prompts/**.md 캐시
│
├── safety/
│   ├── CrisisDetectedEvent             # 도메인 이벤트
│   ├── CrisisDetector                  # 위기 키워드 분석
│   ├── CrisisResponse                  # 핫라인 응답 페이로드
│   ├── EnforcedRatio                   # RatioEnforcer 출력
│   ├── KeywordGuard                    # 금지어 검사
│   ├── Level                           # WARNING / CRITICAL
│   ├── RatioEnforcer                   # 화해 기여도 클리핑
│   ├── SafetyAuditLogger               # 모든 safety 이벤트 → DB
│   ├── SafetyTriggerEvent
│   └── ScanResult
│
├── security/
│   ├── JwtAuthFilter                   # OncePerRequestFilter
│   ├── JwtService                      # 토큰 생성/검증 (UserPermissionsConfig 주입)
│   ├── RateLimitFilter                 # bucket4j 기반
│   ├── SecurityConfig                  # SecurityFilterChain (경로별 인증 규칙)
│   └── UserDetailsServiceImpl
│
├── config/
│   ├── AccessLogInterceptor            # 모든 요청 로깅
│   ├── AsyncConfig                     # @EnableAsync ThreadPoolTaskExecutor
│   ├── ClockConfig                     # @Bean Clock (테스트 교체 가능)
│   ├── CorsConfig                      # 허용 도메인 (dev/prod 분기)
│   ├── JpaAuditingConfig               # @EnableJpaAuditing
│   ├── OpenApiConfig                   # springdoc 설정 + bearerAuth SecurityScheme
│   ├── OpenApiExamples                 # Swagger 예시 응답
│   ├── SchedulingConfig                # @EnableScheduling
│   ├── UserPermissionsConfig           # @Component: permissions.yml 로드 (게스트 제한 등)
│   └── WebMvcConfig                    # 인터셉터 등록
│
└── common/
    ├── exception/
    │   ├── BusinessException           # 도메인 예외 베이스
    │   └── GlobalExceptionHandler      # @RestControllerAdvice
    ├── dto/
    └── util/
        └── GuestNicknameGenerator      # "Guest-XXXXXX" 생성
```

## resources

```
backend/src/main/resources/
├── application.yml                     # 베이스 설정
├── application-dev.yml                 # dev 프로파일
├── application-prod.yml                # prod 프로파일 (모든 env 강제)
├── application-test.yml                # test 프로파일 (H2 + MockLLMProvider)
├── db/migration/
│   ├── V1__init.sql                    # 기본 테이블 (users, sessions, messages, reports 등)
│   ├── V2__add_oauth_and_guest.sql
│   ├── V3__add_email_verification.sql
│   ├── V4__add_security_tables.sql     # revoked_tokens, password_reset_tokens
│   ├── V5__remove_temperature.sql
│   ├── V6__solo_mode_default_true.sql
│   ├── V7__chat_messages.sql           # V1.5 카톡식 messages 테이블
│   ├── V8__add_session_psychology_tracking.sql
│   ├── V9__add_duo_balance_tracking.sql
│   ├── V10__phase_d_context_algorithm.sql
│   ├── V11__add_user_mbti_type.sql
│   ├── V12__finalize_dismiss_and_invite_index.sql
│   ├── V13__add_user_mbti_profile.sql
│   ├── V14__add_mediator_style_to_sessions.sql
│   ├── V15__fix_crisis_level_column_type.sql
│   ├── V16__add_feedbacks.sql          # feedbacks 테이블
│   ├── V17__add_user_consent.sql       # users: consent 컬럼들
│   ├── V18__seed_admin_role.sql
│   ├── V19__add_daily_stats.sql        # daily_stats 테이블
│   ├── V20__add_must_change_password.sql
│   ├── V21__ensure_user_columns.sql
│   ├── V22__add_user_mediator_default_x.sql
│   ├── V23__add_report_v12_fields.sql  # Solo 리포트 확장 11개 컬럼
│   └── V24__add_tutorial_completed_at.sql
├── safety/forbidden-words.yml          # KeywordGuard 단어 목록
├── permissions.yml                     # UserPermissionsConfig 로드 (게스트/일반/tester 권한)
├── prompts/                            # (미사용 — shared/docs/prompts/ 사용)
└── logback-spring.xml
```

## 코드 위치 → 문서 매핑

| 작업 | 파일 위치 | 참고 docs |
|---|---|---|
| 새 API 추가 | `api/*Controller.java` + `api/dto/` | `shared/docs/api/rest-spec.md` → 해당 도메인 `.md` |
| 새 DB 컬럼 | `domain/*.java` + `db/migration/V{n+1}__*.sql` | `shared/docs/api/database-schema.md` |
| Admin API | `api/admin/*Controller.java` + `api/AdminXxxController.java` | `shared/docs/api/admin.md` + `shared/docs/admin-dashboard.md` |
| 새 OAuth provider | `service/oauth/OAuthProviderService.java` | `shared/docs/policies/auth.md` + `backend/docs/policies/oauth-google.md` |
| 보안 정책 | `safety/*.java` + `security/*.java` | `shared/docs/policies/{forbidden-words,crisis-detection}.md` |
| 프롬프트 변경 | `shared/docs/prompts/*.md` (런타임 자산) | `shared/docs/prompts/README.md` |
| LLM 브릿지 (remote) | `llm/remote/*.java` | `backend/docs/llm-bridge.md` |
| LLM 브릿지 (fallback) | `llm/bridge/*.java` | `backend/docs/llm-bridge.md` |
| Phase D 컨텍스트 | `service/context/` + `service/prompt/` | `shared/docs/policies/context-algorithm.md` |
| 피드백 시스템 | `service/FeedbackService.java` + `domain/Feedback.java` | `shared/docs/api/feedback.md` |
| 역할/권한 | `config/UserPermissionsConfig.java` + `service/AdminRoleAssigner.java` | `shared/docs/policies/user-permissions.md` |
