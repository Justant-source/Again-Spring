# 패키지 구조

루트 패키지: `com.againspring`

## 한 줄 책임

| 패키지 | 책임 |
|---|---|
| `api/` | REST 컨트롤러 + DTO (request, response) |
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
│   ├── MediationController             # /api/sessions/{id}/turns, /stream
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
│   │   │   ├── ProgressTurnRequest
│   │   │   ├── ResetPasswordRequest
│   │   │   ├── SendVerificationRequest
│   │   │   ├── SignupRequest
│   │   │   └── UpdateUserRequest
│   │   └── response/
│   │       ├── AuthResponse
│   │       ├── CreateSessionResponse
│   │       ├── CurrentTurnResponse
│   │       ├── OnboardingResponse
│   │       ├── ReportResponse
│   │       ├── SessionListItemResponse
│   │       ├── SessionResponse
│   │       ├── SessionStatusResponse
│   │       ├── TurnResponse
│   │       ├── UserResponse
│   │       └── graph/
│   │           ├── PersonRelationshipSummary
│   │           └── SessionHistoryItem
│   └── graph/
│       └── RelationshipController      # /api/users/me/relationships
│
├── service/
│   ├── AuthService                     # 회원가입/로그인/게스트, JWT 발급
│   ├── EmailVerificationService        # 6자리 코드 발송/검증
│   ├── LogoutService                   # 토큰 폐기 → revoked_tokens
│   ├── MediationService                # 턴 진행, LLM 호출, 응답 저장
│   ├── PasswordResetService            # 재설정 토큰 발급/검증
│   ├── ReportService                   # 리포트 생성 (LLM 호출 + 파싱)
│   ├── SessionService                  # 세션 CRUD + 초대 토큰
│   ├── SessionStateMachine             # 상태 전이 단일 진실
│   ├── StyleCalculator                 # 온보딩 응답 → 스타일
│   ├── UserService                     # User 조회/수정
│   ├── event/
│   │   ├── SessionCompletedEvent       # turn_6 완료 시 발행
│   │   └── TurnCompletedEvent
│   ├── graph/
│   │   ├── RelationshipGraphService    # user_relationships 집계 갱신
│   │   └── SessionCompletedGraphListener  # SessionCompletedEvent 리스너
│   ├── oauth/
│   │   ├── OAuthProviderService        # google/kakao/naver 통합
│   │   └── OAuthUserInfo               # provider 응답 정규화
│   ├── parser/
│   │   └── TurnResponseParser          # LLM 응답 JSON 추출
│   ├── report/
│   │   ├── NeedsMapValidator
│   │   ├── NVCValidator                # NVC 4단계 구조 검증
│   │   └── ReportResponseParser        # 리포트 LLM 응답 → ParsedReport
│   └── retention/
│       ├── AccessLogService
│       ├── RetentionScheduler          # @Scheduled cron 0 0 3 * * *
│       └── UserDeletionService         # DELETE /api/users/me
│
├── domain/                             # JPA 엔티티 (Lombok @Entity)
│   ├── EmailVerification
│   ├── GuestSession
│   ├── PasswordResetToken
│   ├── Report
│   ├── RevokedToken
│   ├── Session
│   ├── Turn
│   ├── User
│   ├── enums/
│   │   ├── ConflictType                # FACTUAL, DIFFERENCE, MIXED
│   │   ├── RelationType                # COUPLE, MARRIAGE, FRIEND, FAMILY, PARENT_CHILD, KOREAN_SPECIFIC
│   │   ├── SessionStatus               # WAITING_B, B_JOINED, IN_MEDIATION, COMPLETED, SOLO_MODE, TERMINATED
│   │   └── TurnRole                    # A, B, MEDIATOR
│   └── relationship/
│       ├── ConflictHistory             # 세션 단위 행
│       ├── LlmCallLog                  # llm_call_logs
│       └── UserRelationship            # A-B 집계
│
├── repository/                         # 모두 JpaRepository<Entity, ID>
│   ├── ConflictHistoryRepository
│   ├── EmailVerificationRepository
│   ├── GuestSessionRepository
│   ├── LlmCallLogRepository
│   ├── PasswordResetTokenRepository
│   ├── ReportRepository
│   ├── RevokedTokenRepository
│   ├── SessionRepository
│   ├── TurnRepository
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
