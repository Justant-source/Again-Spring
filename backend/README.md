# 다시봄 (Again Spring) — Backend

다시봄 AI 중재 갈등 해소 백엔드 서비스.

**Stack**: Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), MariaDB 11, Claude Code CLI

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose (MariaDB)
- Claude Code CLI (호스트에서 1회 로그인 완료 — `~/.claude/` 디렉토리 필요)

### Development

```bash
# DB 시작 (infra/docker-compose.yml)
cd ../infra && docker compose up -d

# 빌드
./gradlew build

# dev 프로파일로 실행 (기본 env 자동 적용)
./gradlew bootRun

# 테스트
./gradlew test

# Swagger UI
# http://localhost:8080/swagger-ui.html

# 헬스 체크
curl http://localhost:8080/api/health
```

### Profiles

- `dev`: localhost MariaDB, debug logging, Swagger enabled, LLM fallback 응답 지원
- `prod`: env-driven config, minimal logging, Swagger disabled

### Environment Variables

```bash
# DB
DB_URL=jdbc:mariadb://localhost:3306/againspring?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_USER=againspring
DB_PASSWORD=changeme

# JWT
JWT_SECRET=dev_secret_key_change_in_prod

# Claude Code CLI (API 키 불필요 — 호스트 ~/.claude 마운트)
LLM_PROVIDER=claude-code
CLAUDE_BIN=claude
CLAUDE_MODEL=claude-haiku-4-5-20251001

# OAuth2
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# Email (Gmail App Password)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...@gmail.com
MAIL_PASSWORD=...
```

## Project Structure

```
src/main/java/com/againspring/
├── api/             # REST Controllers + DTOs
│   ├── dto/         # Request / Response 객체
│   └── graph/       # 관계 그래프 컨트롤러
├── domain/          # JPA 엔티티 (User, Session, Turn, Report, relationship/*)
├── repository/      # JpaRepository 인터페이스
├── service/         # 비즈니스 로직 (Mediation, Report, Session, User, Graph)
├── llm/             # LLM 브릿지 (ClaudeCodeBridge, PromptSanitizer, FallbackResponses)
├── safety/          # 위기 감지 + 금지어 가드 (CrisisDetector, KeywordGuard)
├── security/        # JWT 인증 (JwtService, JwtAuthFilter, SecurityConfig)
└── config/          # 설정 (CORS, JPA Auditing, Scheduling, OpenAPI)

src/main/resources/
├── application.yml                    # Base config
├── application-{dev,prod,test}.yml    # Profile-specific
└── db/migration/
    ├── V1__init.sql                   # 초기 스키마
    ├── V2__add_oauth_guest.sql        # OAuth + Guest 세션 지속성
    ├── V3__add_email_verification.sql # 이메일 인증
    ├── V4__add_security_tables.sql    # 보안 테이블
    └── V5__remove_temperature.sql     # 관계 온도 컬럼 제거
```

## DB Schema (MariaDB 11)

| 테이블 | 설명 |
|---|---|
| `users` | 회원 정보, 온보딩 답변(JSON), 소통 스타일 |
| `sessions` | 중재 세션, 초대 토큰, 현재 턴 상태 |
| `turns` | 세션별 대화 턴 (사용자 입력 + AI 응답) |
| `reports` | 세션 완료 후 분석 리포트 (기여도, NVC, 4Horsemen 내부 점수) |
| `user_relationships` | 두 사용자 간 관계 유형 + 상태 |
| `conflict_history` | 세션별 갈등 이력 |
| `guest_sessions` | 초대 토큰별 Guest ID 일관성 |
| `email_verifications` | 이메일 인증코드 (10분 만료) |
| `llm_call_logs` | LLM 호출 감사 로그 |

자세한 스키마: `shared/docs/DATABASE_SCHEMA.md`

## LLM Bridge

Claude Code CLI를 서브프로세스로 실행. API 키 없이 호스트 `~/.claude` 세션을 컨테이너에 볼륨 마운트해 공유.

- **모델**: `claude-haiku-4-5-20251001` (기본, `CLAUDE_MODEL` 변경 가능)
- **동시성**: `Semaphore(3)` — 최대 3개 병렬 프로세스
- **타임아웃**: 60초
- **Fallback**: Claude 불가 시 `FallbackResponses` 기본 응답 반환

자세한 설계: `shared/docs/LLM_BRIDGE_ARCHITECTURE.md`

## Testing

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests com.againspring.api.HealthControllerTest

# 커버리지 리포트 (JaCoCo)
./gradlew test jacocoTestReport
```

## Build Artifacts

```bash
./gradlew bootJar
# Output: build/libs/againspring-*.jar
```

---

**버전**: Spring Boot 3.3, Java 21, MariaDB 11
