# 패키지 구조

루트 패키지: `com.againspring`

## Source of truth

| 항목 | 위치 |
|---|---|
| 컨트롤러 | `backend/src/main/java/com/againspring/api/**/*Controller.java` |
| 서비스 | `backend/src/main/java/com/againspring/service/**/*.java` |
| 도메인 | `backend/src/main/java/com/againspring/domain/**/*.java` |
| 마이그레이션 | `backend/src/main/resources/db/migration/V1~V56.sql` |

## 패키지 계층 개요

```flowchart
flowchart TD
    subgraph API["api/ — REST 컨트롤러"]
        direction LR
        C1["AuthController\nOAuth2Controller\nHealthController"]
        C2["CommunityPostController\nCommunityCommentController\nPostInviteController"]
        C3["NotificationController\nUserController\nFeedbackController"]
        C4["admin/: AdminDashboardController\nAdminUserController\nAdminHealthController\nAdminCommunityController\nAdminFeedbackController\nAdminPromptsController"]
    end

    subgraph SVC["service/ — 비즈니스 로직"]
        direction LR
        S1["community/:\nCommunityPostService\nCommunityCommentService\nJuryService"]
        S2["admin/, category/,\ncrisis/, marketing/,\nnotification/, notify/,\noauth/, retention/, util/"]
    end

    subgraph DOM["domain/ — JPA 엔티티"]
        direction LR
        D1["User · Post · PostComment\nVote · Juror · PostLike\nCommunityReport"]
        D2["Notification · Marketing\nFeedback · GuestSession\nRevokedToken"]
    end

    subgraph INF["인프라"]
        direction LR
        I1["llm/: RemoteLlmProvider\nPromptSanitizer\nconfig/ · fallback/ · monitoring/"]
        I2["safety/: KeywordGuard · CrisisDetector\nsecurity/: JWT · SecurityConfig\nconfig/: OpenAPI · CORS · Async"]
    end

    API --> SVC
    SVC --> DOM
    SVC --> INF
```

## 한 줄 책임

| 패키지 | 책임 |
|---|---|
| `api/` | REST 컨트롤러 + DTO (community, auth, user, feedback) |
| `api/admin/` | 관리자 컨트롤러 (Dashboard · User · Health · Community) |
| `service/` | 비즈니스 로직, 트랜잭션 경계 |
| `service/admin/` | 관리자 기능 (통계 · 사용자 · 모니터링) |
| `service/community/` | 광장 서비스 (Post · Comment · Vote · Jury) |
| `service/marketing/` | 마케팅 자동화 (dev 전용) |
| `service/notification/` | 알림 서비스 |
| `service/notify/` | 위기 알림 이메일 · 피드백 이메일 발신 |
| `service/crisis/` | 위기 감지 (CrisisDetector 위임) |
| `service/retention/` | 30일 보존 스케줄러 · 일일 통계 집계 |
| `domain/` | JPA 엔티티 + Enum |
| `domain/community/` | Post · PostComment · Vote · Juror · PostLike |
| `domain/marketing/` | Marketing 엔티티 |
| `domain/notification/` | Notification 엔티티 |
| `repository/` | Spring Data JPA 인터페이스 |
| `repository/community/` | PostRepository · PostCommentRepository · VoteRepository · JurorRepository |
| `llm/` | `LLMProvider` 인터페이스 + RemoteLlmProvider (기본) |
| `llm/remote/` | HTTP 클라이언트 → againspring-llm 워커 |
| `llm/config/` | LLM 설정 |
| `llm/prompt/` | 프롬프트 어셈블 + 로더 |
| `llm/fallback/` | 로컬 fallback (개발 전용) |
| `llm/monitoring/` | LLM 호출 지표 수집 |
| `safety/` | KeywordGuard · CrisisDetector · SafetyAuditLogger |
| `security/` | JwtFilter · SecurityConfig · RateLimitFilter · UserDetailsService |
| `config/` | 빈 설정 (CORS · Async · Scheduling · OpenAPI) |
| `common/` | 공통 예외 (BusinessException · GlobalExceptionHandler) |
| `seed/` | 시드 데이터 |

## 트리

```
com.againspring/
├── AgainSpringApplication              # @SpringBootApplication 진입점
│
├── api/
│   ├── admin/
│   │   ├── AdminDashboardController    # GET /api/admin/dashboard/{summary,daily-stats,retention}
│   │   ├── AdminHealthController       # GET /api/admin/health/system
│   │   ├── AdminUserController         # GET/DELETE/PATCH /api/admin/users/**
│   │   └── AdminCommunityController    # /api/admin/posts, /api/admin/comments
│   ├── AdminFeedbackController         # GET/PATCH /api/admin/feedbacks/**
│   ├── AdminPromptsController          # POST /api/admin/prompts/reload (app.admin.enabled)
│   ├── AuthController                  # /api/auth/{signup,login,guest,logout,agree,forgot-password,reset-password}
│   ├── CommunityPostController         # /api/posts (CRUD, jury, voting)
│   ├── CommunityCommentController      # /api/posts/{id}/comments
│   ├── PostInviteController            # /api/posts/{id}/invite
│   ├── NotificationController          # /api/notifications
│   ├── FeedbackController              # POST /api/feedbacks
│   ├── HealthController                # GET /api/health
│   ├── OAuth2Controller                # POST /api/auth/oauth2/{provider}
│   ├── UserController                  # /api/users/me, /password, /onboarding
│   ├── CalendarController              # /api/calendar/* (marketing)
│   ├── ContentController               # /api/content/* (marketing)
│   ├── CostController                  # /api/cost/* (marketing)
│   ├── DashboardController             # /api/dashboard/* (marketing)
│   ├── HashtagController               # /api/hashtags/* (marketing)
│   ├── MarketingImageController        # /api/marketing-images/* (marketing)
│   ├── MarketingModuleController       # /api/marketing-modules/* (marketing)
│   ├── RepurposeController             # /api/repurpose/* (marketing)
│   ├── SimulationController            # /api/simulation/* (marketing)
│   ├── SocialPublishController         # /api/social-publish/* (marketing)
│   ├── StoryController                 # /api/stories/* (marketing)
│   ├── TemplateController              # /api/templates/* (marketing)
│   ├── community/
│   │   └── dto/                        # Community-specific request/response DTOs
│   ├── dto/
│   │   ├── request/
│   │   │   ├── SignupRequest
│   │   │   ├── LoginRequest
│   │   │   ├── CreatePostRequest
│   │   │   └── ... (다른 request DTOs)
│   │   └── response/
│   │       ├── PostResponse
│   │       ├── JurorResponse
│   │       ├── AuthResponse
│   │       └── ... (다른 response DTOs)
│
├── service/
│   ├── admin/
│   │   ├── AdminUserDetailService
│   │   ├── CrisisMonitoringService
│   │   ├── PmfStatsService
│   │   ├── RetentionCohortService
│   │   └── SystemHealthService
│   ├── category/
│   │   └── CategoryCatalog
│   ├── crisis/
│   │   └── CrisisDetector
│   ├── marketing/
│   │   └── (마케팅 자동화 서비스들)
│   ├── community/
│   │   ├── CommunityPostService
│   │   ├── CommunityCommentService
│   │   ├── JuryService
│   │   ├── PostComposeService
│   │   └── VoteService
│   ├── notification/
│   │   └── (알림 관련 서비스들)
│   ├── notify/
│   │   ├── CrisisFeedbackNotifier
│   │   └── FeedbackEmailNotifier
│   ├── oauth/
│   │   ├── OAuthProviderService
│   │   └── OAuthUserInfo
│   ├── retention/
│   │   ├── AccessLogService
│   │   ├── DailyStatsAggregator
│   │   ├── GuestSessionCleanupScheduler
│   │   ├── RetentionScheduler
│   │   └── UserDeletionService
│   ├── util/
│   │   └── (유틸리티 서비스들)
│   ├── AdminRoleAssigner
│   ├── AuthService
│   ├── EmailVerificationService
│   ├── FeedbackService
│   ├── GuestSessionRateLimiter
│   ├── LogoutService
│   ├── PasswordResetService
│   ├── RevokedTokenCleanupScheduler
│   ├── SessionRoleResolver
│   ├── SessionService
│   ├── SessionStateMachine
│   ├── StyleCalculator
│   └── UserService
│
├── domain/                             # JPA 엔티티 (Lombok @Entity)
│   ├── DailyStats
│   ├── EmailVerification
│   ├── Feedback
│   ├── GuestSession
│   ├── PasswordResetToken
│   ├── RevokedToken
│   ├── User
│   ├── community/
│   │   ├── Post
│   │   ├── PostComment
│   │   ├── PostLike
│   │   ├── Vote
│   │   ├── VoteOption
│   │   ├── Juror
│   │   └── CommunityReport
│   ├── marketing/
│   │   └── (마케팅 엔티티들)
│   ├── notification/
│   │   └── (알림 엔티티들)
│   ├── enums/
│   │   ├── ConflictType
│   │   ├── RelationType
│   │   └── ReportStatus
│   └── relationship/
│       ├── LlmCallLog
│       └── UserRelationship
│
├── repository/                         # 모두 JpaRepository<Entity, ID>
│   ├── DailyStatsRepository
│   ├── EmailVerificationRepository
│   ├── FeedbackRepository
│   ├── GuestSessionRepository
│   ├── LlmCallLogRepository
│   ├── PasswordResetTokenRepository
│   ├── RevokedTokenRepository
│   ├── UserRelationshipRepository
│   ├── UserRepository
│   └── community/
│       ├── PostRepository
│       ├── PostCommentRepository
│       ├── PostLikeRepository
│       ├── VoteRepository
│       ├── VoteOptionRepository
│       ├── JurorRepository
│       └── CommunityReportRepository
│
├── llm/
│   ├── PromptSanitizer.java            # 사용자 입력 검증 + <user_input> 태그
│   ├── config/
│   │   └── LlmProperties                # application.yml 설정 매핑
│   ├── remote/                          # ← 기본 provider
│   │   ├── RemoteLlmProvider            # HTTP POST /v1/invoke
│   │   └── dto/
│   │       ├── InvocationRequest
│   │       └── InvocationResponse
│   ├── fallback/
│   │   └── FallbackResponses            # Claude 불가 시 안전 기본값
│   ├── monitoring/
│   │   └── LLMCallLogger                # llm_call_logs 기록
│   └── prompt/
│       ├── PromptAssembler
│       └── PromptLoader
│
├── safety/
│   ├── CrisisDetectedEvent
│   ├── CrisisDetector
│   ├── CrisisResponse
│   ├── KeywordGuard
│   ├── Level
│   ├── RatioEnforcer
│   ├── SafetyAuditLogger
│   ├── SafetyTriggerEvent
│   └── ScanResult
│
├── security/
│   ├── JwtAuthFilter                   # OncePerRequestFilter
│   ├── JwtService                      # 토큰 생성/검증
│   ├── RateLimitFilter                 # bucket4j 기반
│   ├── SecurityConfig                  # SecurityFilterChain
│   └── UserDetailsServiceImpl
│
├── config/
│   ├── AccessLogInterceptor
│   ├── AsyncConfig                     # @EnableAsync ThreadPoolTaskExecutor
│   ├── ClockConfig                     # @Bean Clock
│   ├── CorsConfig
│   ├── JpaAuditingConfig
│   ├── OpenApiConfig
│   ├── OpenApiExamples
│   ├── SchedulingConfig
│   ├── UserPermissionsConfig
│   └── WebMvcConfig
│
├── common/
│   ├── exception/
│   │   ├── BusinessException
│   │   └── GlobalExceptionHandler
│   ├── dto/
│   └── util/
│       └── GuestNicknameGenerator
│
└── seed/
    └── (시드 데이터 클래스들)
```

## resources

```
backend/src/main/resources/
├── application.yml                     # 베이스 설정
├── application-dev.yml                 # dev 프로파일
├── application-prod.yml                # prod 프로파일
├── application-test.yml                # test 프로파일 (H2 + MockLLMProvider)
├── db/migration/
│   ├── V1__init.sql                    # 기본 테이블 (users, posts, comments 등)
│   ├── V2~V47.sql                      # 레거시 마이그레이션
│   ├── V48__community_posts.sql        # 광장형 posts 테이블 (new)
│   ├── V49~V55.sql                     # 광장형 확장 (voting, jurors, etc)
│   ├── V56__drop_legacy_mediation_tables.sql  # 레거시 테이블 제거
│   └── (other migrations)
├── safety/forbidden-words.yml          # KeywordGuard 단어 목록
├── permissions.yml                     # UserPermissionsConfig 로드
└── logback-spring.xml
```

## Flyway 마이그레이션 현황

| 버전 | 설명 | 타입 |
|---|---|---|
| V1~V47 | 레거시 마이그레이션 | 기존 시스템 |
| V48 | posts 테이블 생성 | 광장형 NEW |
| V49 | post_comments, post_likes 생성 | 광장형 NEW |
| V50 | votes, vote_options 생성 | 광장형 NEW |
| V51 | jurors 테이블 생성 | 광장형 NEW |
| V52 | community_reports 생성 | 광장형 NEW |
| V53 | notifications 테이블 | 광장형 NEW |
| V54 | marketing 테이블 확장 | 광장형 NEW |
| V55 | community3 추가 컬럼 | 광장형 NEW |
| V56 | drop_legacy_mediation_tables | 정리 (세션, 메시지 등 삭제) |

## 코드 위치 → 문서 매핑

| 작업 | 파일 위치 | 참고 docs |
|---|---|---|
| 새 API 추가 | `api/*Controller.java` + `api/dto/` | `shared/docs/api/rest-spec.md` |
| 새 DB 컬럼 | `domain/*.java` + `db/migration/V{n+1}__*.sql` | `shared/docs/api/database-schema.md` |
| Admin API | `api/admin/*Controller.java` | `shared/docs/api/admin.md` |
| 광장 게시글 | `service/community/CommunityPostService.java` | `shared/docs/api/community.md` |
| 보안 정책 | `safety/*.java` + `security/*.java` | `shared/docs/policies/` |
| 프롬프트 변경 | `shared/docs/prompts/*.md` | `shared/docs/prompts/README.md` |
| LLM 브릿지 | `llm/remote/*.java` | `backend/docs/llm-bridge.md` |
| 역할/권한 | `config/UserPermissionsConfig.java` | `shared/docs/policies/user-permissions.md` |

---

**마지막 업데이트**: 2026-06-03
