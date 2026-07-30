# OpenAPI / Swagger

springdoc-openapi 2.6.0이 자동으로 OpenAPI 3.0 스펙을 생성하고 Swagger UI를 호스팅한다.

## 접근

| 환경 | URL |
|---|---|
| local (`./gradlew bootRun`) | `http://localhost:8080/swagger-ui.html` |
| dev 서버 | `https://dev.againspring.net/swagger-ui/` (nginx 라우팅) |
| prod | **비활성** (`application-prod.yml`에서 `springdoc.swagger-ui.enabled: false`) |

raw OpenAPI JSON: `/v3/api-docs` (모든 환경에서 동일 경로)

## 설정

`backend/src/main/resources/application.yml`:

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
```

prod 비활성:
```yaml
# application-prod.yml
springdoc:
  swagger-ui:
    enabled: false
```

## 빈 설정

`config/OpenApiConfig.java`가 다음을 정의:

- API 정보 (title, version, description)
- 보안 스키마: `bearer-jwt` — `SecurityScheme.Type.HTTP` + `scheme("bearer")` + `bearerFormat("JWT")`
  → Swagger UI 우상단 **Authorize** 버튼에 JWT 입력란 노출
- 전체 15개 컨트롤러 `@Tag` + `@SecurityRequirement(name = "bearer-jwt")` 적용
  (공개 엔드포인트는 컨트롤러에서 `@SecurityRequirement` 미적용)

`config/OpenApiExamples.java`는 응답 예시 모음 (Swagger UI에서 사용자가 바로 시도 가능).

## 컨트롤러 어노테이션 현황

| 컨트롤러 | `@Tag` | `@SecurityRequirement` |
|---|---|---|
| AuthController | Auth | JWT 필요 메서드만 |
| OAuth2Controller | Auth | — (공개) |
| HealthController | Health | — (공개) |
| CommunityPostController | Community — Posts | bearer-jwt |
| CommunityCommentController | Community — Comments | bearer-jwt |
| PostInviteController | Community — Invites | bearer-jwt |
| NotificationController | Notifications | bearer-jwt |
| UserController | User | bearer-jwt |
| FeedbackController | Feedback | — (공개) |
| AdminDashboardController | Admin — Dashboard | bearer-jwt |
| AdminUserController | Admin — Users | bearer-jwt |
| AdminHealthController | Admin — Health | bearer-jwt |
| AdminFeedbackController | Admin — Feedbacks | bearer-jwt |
| AdminPromptsController | Admin — Prompts | bearer-jwt |
| CalendarController | Marketing — Calendar | bearer-jwt |
| ContentController | Marketing — Content | bearer-jwt |
| CostController | Marketing — Cost | bearer-jwt |
| DashboardController | Marketing — Dashboard | bearer-jwt |
| HashtagController | Marketing — Hashtags | bearer-jwt |
| MarketingImageController | Marketing — Images | bearer-jwt |
| MarketingModuleController | Marketing — Modules | bearer-jwt |
| RepurposeController | Marketing — Repurpose | bearer-jwt |
| SimulationController | Marketing — Simulation | bearer-jwt |
| SocialPublishController | Marketing — Social | bearer-jwt |
| StoryController | Marketing — Stories | bearer-jwt |
| TemplateController | Marketing — Templates | bearer-jwt |
| **합계** | **27개** | — |

## DTO 어노테이션 컨벤션

```java
@Schema(description = "회원가입 요청")
public class SignupRequest {

    @Schema(description = "이메일", example = "user@example.com")
    @Email @NotBlank
    private String email;

    @Schema(description = "비밀번호 (8자 이상, 영문+숫자+특수문자)", example = "Pass123!")
    @Size(min = 8) @NotBlank
    private String password;

    @Schema(description = "이메일 인증 코드 (6자리)")
    @Size(min = 6, max = 6)
    private String code;

    @Schema(description = "닉네임", example = "달콩")
    @NotBlank
    private String nickname;
}
```

Controller 메서드:

```java
@Operation(summary = "회원가입", description = "이메일 인증 코드 검증 후 계정 생성")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "성공"),
    @ApiResponse(responseCode = "400", description = "검증 실패"),
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
})
@PostMapping("/signup")
public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) { ... }
```

## API 명세는 어디서 보나

- **Swagger UI** = 클릭 가능한 인터랙티브 명세 (개발/QA용)
- **`docs/shared/api/rest-spec.md`** = 엔드포인트 전체 표 + 정책 (사람용 빠른 참조)

두 문서가 어긋나면 Swagger가 우선 (코드에서 자동 생성).

## 정적 스냅샷 (선택)

`shared/schemas/openapi.json`에 정적 스냅샷이 있을 수 있음 (commit 시점 기준). FE 코드 생성 (orval, openapi-typescript)에 활용 가능하지만 현재 FE는 자체 타입 사용.

스냅샷 갱신:
```bash
cd backend && ./gradlew bootRun &
sleep 10
curl http://localhost:8080/v3/api-docs > ../shared/schemas/openapi.json
```
