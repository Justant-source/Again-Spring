# 패키지 구조

루트 패키지: `com.againspring`

## Source of truth

| 항목 | 위치 |
|---|---|
| 컨트롤러 | `backend/src/main/java/com/againspring/api/**/*Controller.java` |
| 서비스 | `backend/src/main/java/com/againspring/service/**/*.java` |
| 도메인 | `backend/src/main/java/com/againspring/domain/**/*.java` |
| 마이그레이션 | `backend/src/main/resources/db/migration/` (최신 V126) |

## 패키지 계층 개요

```flowchart
flowchart TD
    subgraph API["api/ — REST 컨트롤러"]
        direction LR
        C1["AuthController\nOAuth2Controller\nHealthController"]
        C2["CommunityPostController\nCommunityCommentController\nPostInviteController"]
        C3["NotificationController\nUserController\nFeedbackController"]
        C4["admin/: AdminDashboardController\nAdminUserController\nAdminHealthController\nAdminFeedbackController\nAdminPromptsController"]
    end

    subgraph SVC["service/ — 비즈니스 로직"]
        direction LR
        S1["community/:\nPostComposeService\nCommunityCommentService\nVoteService"]
        S2["admin/, category/,\ncrisis/, marketing/,\nnotification/, notify/,\noauth/, retention/, util/"]
    end

    subgraph DOM["domain/ — JPA 엔티티"]
        direction LR
        D1["User · Post · PostComment\nVote · PostLike\nCommunityReport"]
        D2["Notification · Marketing\nFeedback · RevokedToken"]
    end

    subgraph INF["인프라"]
        direction LR
        I1["llm/: RemoteLlmProvider\nPromptSanitizer · PromptLoader"]
        I2["safety/: CrisisKeywordGuard\nsecurity/: JWT · SecurityConfig\nconfig/: OpenAPI · CORS · Async"]
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
| `service/community/` | 광장 서비스 (Post · Comment · Vote · Invite · Tonalization) |
| `service/marketing/` | 마케팅 자동화 (dev 전용) |
| `service/notification/` | 알림 서비스 |
| `service/notify/` | 위기 알림 이메일 · 피드백 이메일 발신 |
| `service/retention/` | 30일 보존 스케줄러 · 일일 통계 집계 |
| `domain/` | JPA 엔티티 + Enum |
| `domain/community/` | Post · PostComment · Vote · PostLike |
| `domain/marketing/` | Marketing 엔티티 |
| `domain/notification/` | Notification 엔티티 |
| `repository/` | Spring Data JPA 인터페이스 |
| `repository/community/` | PostRepository · PostCommentRepository · VoteRepository |
| `llm/` | `LLMProvider` 인터페이스 + RemoteLlmProvider (기본) |
| `llm/remote/` | HTTP 클라이언트 → againspring-llm 워커 |
| `llm/` | `RemoteLlmProvider` · `PromptSanitizer` · `prompt/PromptLoader` |
| `safety/` | CrisisKeywordGuard · CrisisScanResult · SafetyAuditLogger |
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
│   ├── AdminFeedbackController         # GET/PATCH /api/admin/feedbacks/**
│   ├── AdminPromptsController          # POST /api/admin/prompts/reload
│   ├── AuthController                  # /api/auth/{signup,login,guest,logout,agree,forgot-password,reset-password}
│   ├── NotificationController          # /api/notifications
│   ├── FeedbackController              # POST /api/feedbacks
│   ├── HealthController                # GET /api/health
│   ├── OAuth2Controller                # POST /api/auth/oauth2/{provider}
│   ├── UserController                  # /api/users/me, /password (onboarding 엔드포인트 없음)
│   ├── AnnouncementPublicController
│   ├── InquiryController
│   ├── community/                      # CommunityPostController · CommunityCommentController · PostInviteController · CommunityStatsController
│   ├── admin/                          # 대시보드·사용자·콘텐츠·마케팅·크롤·신고·문의·공지·시크릿·AI-user
│   ├── internal/                       # AiUserInternalController · PersonaExportController · MarketingCallbackController
│   ├── visits/PublicVisitController
│   └── dto/
│   │   ├── request/
│   │   │   ├── SignupRequest
│   │   │   ├── LoginRequest
│   │   │   ├── CreatePostRequest
│   │   │   └── ... (다른 request DTOs)
│   │   └── response/
│   │       ├── PostResponse
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
│   ├── marketing/
│   │   └── (마케팅 자동화 서비스들)
│   ├── community/
│   │   ├── CommunityPostService
│   │   ├── CommunityCommentService
│   │   ├── AnswerProcessingService
│   │   ├── TonalizationService
│   │   ├── PostComposeService
│   │   ├── PostInviteService
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
│   │   └── AccessLogService
│   ├── DailyStatsAggregatorService     # 자정(Asia/Seoul) 일별 통계
│   ├── RevokedTokenCleanupScheduler    # 매일 04:00 UTC, 만료 revoked_tokens
│   ├── AuthService
│   ├── EmailVerificationService
│   ├── FeedbackService
│   ├── LogoutService
│   ├── PasswordResetService
│   └── UserService                     # 프로필·비밀번호·탈퇴. onboarding/tutorial 메서드 없음
│
├── domain/                             # JPA 엔티티 (Lombok @Entity)
│   ├── DailyStats
│   ├── EmailVerification
│   ├── Feedback
│   ├── PasswordResetToken
│   ├── RevokedToken
│   ├── User
│   ├── VisitEvent
│   ├── community/
│   │   ├── Post
│   │   ├── PostComment
│   │   ├── PostLike
│   │   ├── Vote
│   │   ├── VoteOption
│   │   └── CommunityReport
│   ├── marketing/
│   │   └── (마케팅 엔티티들)
│   ├── notification/
│   │   └── (알림 엔티티들)
│   └── enums/
│       ├── PostStatus · PublishMode · PostVisibility · PostCategory
│       ├── CommentStatus · ReportStatus · NotificationType
│
├── repository/                         # 모두 JpaRepository<Entity, ID>
│   ├── DailyStatsRepository
│   ├── EmailVerificationRepository
│   ├── FeedbackRepository
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
│       └── CommunityReportRepository
│
├── llm/
│   ├── PromptSanitizer.java            # 사용자 입력 검증 + <user_input> 태그
│   ├── remote/                          # ← 유일한 provider
│   │   ├── RemoteLlmProvider            # HTTP POST /v1/invoke
│   │   └── dto/
│   │       ├── WorkerInvokeRequest
│   │       └── WorkerInvokeResponse
│   ├── PromptSanitizer
│   ├── LlmImage
│   └── prompt/
│       └── PromptLoader                 # 구조화 프롬프트 조립(StructuredPrompt)은 없음
│
├── safety/
│   ├── CrisisDetectedEvent
│   ├── CrisisKeywordGuard
│   ├── CrisisScanResult
│   └── SafetyAuditLogger
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
│   ├── V49~V55.sql                     # 광장형 확장 (voting, notifications, etc)
│   ├── V56__drop_legacy_mediation_tables.sql  # 레거시 테이블 제거
│   ├── V106__drop_ai_jury.sql          # jurors 테이블·juror_count 제거
│   └── (other migrations)
├── safety/crisis-keywords.yml          # CrisisKeywordGuard 단어 목록
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
| V51 | (역사) jurors 테이블 생성 — **V106에서 DROP** | 광장형 NEW |
| V52 | community_reports 생성 | 광장형 NEW |
| V53 | notifications 테이블 | 광장형 NEW |
| V54 | marketing 테이블 확장 | 광장형 NEW |
| V55 | community3 추가 컬럼 | 광장형 NEW |
| V56 | drop_legacy_mediation_tables | 정리 (세션, 메시지 등 삭제) |
| V106 | drop_ai_jury | jurors 테이블·posts.juror_count 삭제 |

## 코드 위치 → 문서 매핑

| 작업 | 파일 위치 | 참고 docs |
|---|---|---|
| 새 API 추가 | `api/*Controller.java` + `api/dto/` | `docs/shared/50-api/rest-spec.md` |
| 새 DB 컬럼 | `domain/*.java` + `db/migration/V{n+1}__*.sql` | `docs/backend/40-data.md` |
| Admin API | `api/admin/*Controller.java` | `docs/shared/50-api/admin.md` |
| 광장 게시글 | `service/community/CommunityPostService.java` | `docs/shared/50-api/rest-spec.md` · `docs/shared/50-api/flows.md` |
| 보안 정책 | `safety/*.java` + `security/*.java` | `docs/shared/policies/` |
| 프롬프트 변경 | `docs/shared/prompts/*.md` | `docs/shared/prompts/` |
| LLM 브릿지 | `llm/remote/*.java` | `docs/backend/30-components/llm-bridge.md` |
| 역할/권한 | `config/UserPermissionsConfig.java` | `docs/shared/policies/user-permissions.md` |

---
