# 패키지 구조

루트 패키지: `com.againspring`

## 한 줄 책임

| 패키지 | 책임 |
|---|---|
| `api/` | REST 컨트롤러 (8개: Auth/Health/Message/OAuth2/Report/Session/User/AdminPrompts) + DTO (request, response) |
| `service/` | 비즈니스 로직, 트랜잭션 경계, State Machine |
| `domain/` | JPA 엔티티 + Enum (도메인 모델) |
| `repository/` | Spring Data JPA 인터페이스 |
| `llm/` | ClaudeCodeBridge + Prompt 어셈블 + 모니터링 |
| `safety/` | KeywordGuard, CrisisDetector, RatioEnforcer (모든 보호 로직) |
| `security/` | JwtFilter, SecurityConfig, RateLimitFilter, UserDetailsService |
| `config/` | 빈 설정 (CORS, Async, Scheduling, OpenAPI, AccessLog) |
| `common/` | 공통 예외 (BusinessException, GlobalExceptionHandler) |
| `util/` | 잡유틸 (GuestNicknameGenerator) |

## 트리

```
com.againspring/
├── AgainSpringApplication              # @SpringBootApplication 진입점
│
├── api/
│   ├── AdminPromptsController          # POST /api/admin/prompts/reload
│   ├── AuthController                  # /api/auth/{signup,login,guest,logout,...}
│   ├── HealthController                # GET /api/health
│   ├── MessageController               # /api/sessions/{id}/messages, /invite
│   ├── OAuth2Controller                # /api/auth/oauth2/{provider}
│   ├── ReportController                # /api/sessions/{id}/report, /api/reports/{id}
│   ├── SessionController               # /api/sessions, /me, /{id}, /join/{token}
│   ├── UserController                  # /api/users/me, /onboarding
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateSessionRequest
│   │   │   ├── ForgotPasswordRequest
│   │   │   ├── GuestRequest
│   │   │   ├── JoinSessionRequest
│   │   │   ├── LoginRequest
│   │   │   ├── OAuthCallbackRequest
│   │   │   ├── OnboardingRequest
│   │   │   ├── SendVerificationRequest
│   │   │   ├── SignupRequest
│   │   │   └── UpdateUserRequest
│   │   └── response/
│   │       ├── AuthResponse
│   │       ├── ChatTurnResponse
│   │       ├── CreateSessionResponse
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
│   │       ├── TurnResponse
│   │       └── UserResponse
│
├── service/
│   ├── AuthService                     # 회원가입/로그인/게스트, JWT 발급
│   ├── ChatService                     # V1.5 카톡식 메시지 송수신, AI 응답, 종료 권유
│   ├── EmailVerificationService        # 6자리 코드 발송/검증
│   ├── LogoutService                   # 토큰 폐기 → revoked_tokens
│   ├── PasswordResetService            # 재설정 토큰 발급/검증
│   ├── SessionRoleResolver             # 사용자가 USER_A인지 USER_B인지 판정
│   ├── SessionService                  # 세션 CRUD + 초대 토큰
│   ├── SessionStateMachine             # V1.5 카톡식 상태 전이 단일 진실
│   ├── StyleCalculator                 # 온보딩 응답 → 6스타일 enum (label/emoji/strengths/caution 메타 포함)
│   ├── UserService                     # User 조회/수정
│   ├── crisis/
│   │   └── CrisisDetector              # 위기 키워드 분석
│   ├── event/
│   │   ├── SessionCompletedEvent
│   │   └── TurnCompletedEvent
│   ├── oauth/
│   │   ├── OAuthProviderService
│   │   └── OAuthUserInfo
│   ├── parser/
│   │   ├── ChatTurnMetaParser          # V1.5 응답 본문/<turn_meta> JSON 분리 (Phase B)
│   │   └── TurnResponseParser          # 레거시 6턴 모델 JSON 파서
│   ├── prompt/
│   │   ├── ChatPromptAssembler         # V1.5 카톡식 프롬프트 조립 (Solo/Duo)
│   │   ├── DuoBalanceFormatter         # <duo_balance> 관심 분배 지시 (Phase C)
│   │   ├── PsychologyFeedbackFormatter # <psychology_feedback> 누적 점수 지시 (Phase B)
│   │   └── UserProfileFragment         # <user_profile> 자연어 블록 (Phase A)
│   ├── report/
│   │   ├── MetaphorSelector
│   │   ├── NeedsMapValidator
│   │   ├── NVCValidator
│   │   ├── RatioEnforcer               # 화해 기여도 클리핑
│   │   ├── ReportGenerationService     # V1.5 리포트 (Sonnet, A/B 병렬)
│   │   └── ReportResponseParser
│   └── retention/
│       ├── AccessLogService
│       ├── RetentionScheduler          # @Scheduled cron 0 0 3 * * *
│       └── UserDeletionService         # DELETE /api/users/me
│
├── domain/                             # JPA 엔티티 (Lombok @Entity)
│   ├── EmailVerification
│   ├── GuestSession
│   ├── Message                         # V1.5 카톡식 메시지 (solo/duo)
│   ├── PasswordResetToken
│   ├── Report
│   ├── RevokedToken
│   ├── Session
│   ├── User
│   ├── enums/
│   │   ├── ConflictType                # FACTUAL, DIFFERENCE, MIXED
│   │   ├── RelationType                # COUPLE, MARRIAGE, FRIEND, FAMILY, PARENT_CHILD, KOREAN_SPECIFIC
│   │   ├── SessionStatus               # CHATTING_SOLO, CHATTING_DUO, AWAITING_FINALIZATION, COMPLETED, TERMINATED
│   │   └── TurnRole                    # A, B, MEDIATOR
│   └── relationship/
│       ├── LlmCallLog                  # llm_call_logs
│       └── UserRelationship            # A-B 집계
│
├── repository/                         # 모두 JpaRepository<Entity, ID>
│   ├── EmailVerificationRepository
│   ├── GuestSessionRepository
│   ├── LlmCallLogRepository
│   ├── MessageRepository                # V1.5 메시지 저장소
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
│   ├── bridge/
│   │   ├── ClaudeCodeBridge            # Claude CLI 호출 (@ConditionalOnProperty)
│   │   ├── ClaudeCodeWorkerPool        # Semaphore(3) + ExecutorService
│   │   ├── MockLLMProvider             # 테스트 프로파일
│   │   ├── PromptSanitizer             # injection 방지
│   │   └── exception/
│   │       ├── ClaudeCodeException
│   │       ├── LLMCapacityException
│   │       ├── LLMSanitizationException
│   │       └── LLMTimeoutException
│   ├── fallback/
│   │   └── FallbackResponses           # 실패 시 안전 기본값
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
│   ├── Level                           # WARNING/CRITICAL
│   ├── RatioEnforcer                   # 화해 기여도 클리핑
│   ├── SafetyAuditLogger               # 모든 safety 이벤트 → DB
│   ├── SafetyTriggerEvent
│   └── ScanResult
│
├── security/
│   ├── JwtAuthFilter                   # OncePerRequestFilter, addFilterBefore
│   ├── JwtService                      # 토큰 생성/검증
│   ├── RateLimitFilter                 # bucket4j 기반
│   ├── SecurityConfig                  # SecurityFilterChain
│   └── UserDetailsServiceImpl
│
├── config/
│   ├── AccessLogInterceptor            # 모든 요청 로깅
│   ├── AsyncConfig                     # @EnableAsync ThreadPoolTaskExecutor
│   ├── ClockConfig                     # @Bean Clock (테스트 가능)
│   ├── CorsConfig                      # CORS 허용 도메인
│   ├── JpaAuditingConfig               # @EnableJpaAuditing
│   ├── OpenApiConfig                   # springdoc 설정
│   ├── OpenApiExamples                 # Swagger 예시 응답
│   ├── SchedulingConfig                # @EnableScheduling
│   └── WebMvcConfig                    # 인터셉터 등록
│
├── common/
│   ├── exception/
│   │   ├── BusinessException           # 도메인 예외 베이스
│   │   └── GlobalExceptionHandler      # @RestControllerAdvice
│   ├── dto/
│   └── util/
│
├── RevokedTokenCleanupScheduler        # 매일 04:00 UTC
│
└── util/
    └── GuestNicknameGenerator          # "Guest-XXXXXX" 생성
```

## resources

```
backend/src/main/resources/
├── application.yml                     # 베이스 설정
├── application-dev.yml                 # dev 프로파일
├── application-prod.yml                # prod 프로파일 (모든 env 강제)
├── application-test.yml                # test 프로파일 (H2 + MockLLMProvider)
├── db/migration/V1~V5.sql              # Flyway
├── safety/forbidden-words.yml          # KeywordGuard 단어 목록
├── prompts/                            # (사용 안 함 — shared/docs/prompts/ 사용)
└── logback-spring.xml
```

## 코드 위치 → 문서 매핑

| 작업 | 파일 위치 | 참고 docs |
|---|---|---|
| 새 API 추가 | `api/*Controller.java` + `api/dto/` | `shared/docs/api/rest-spec.md` |
| 새 DB 컬럼 | `domain/*.java` + `db/migration/V{n+1}__*.sql` | `shared/docs/api/database-schema.md` |
| 새 OAuth provider | `service/oauth/OAuthProviderService.java` | `shared/docs/policies/auth.md` + `policies/oauth-google.md` |
| 보안 정책 | `safety/*.java` + `security/*.java` | `shared/docs/policies/{forbidden-words, crisis-detection}.md` + `policies/{prompt-sanitizer, keyword-guard, auth-jwt}.md` |
| 프롬프트 변경 | `shared/docs/prompts/*.md` (런타임 자산) | `shared/docs/llm/system-prompts.md` |
| LLM 브릿지 | `llm/bridge/*.java` | `shared/docs/llm/bridge-architecture.md` |
