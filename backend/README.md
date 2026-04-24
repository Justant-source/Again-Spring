# 다시봄 (Again Spring) — Backend

다시봄 AI-mediated relationship conflict resolution backend service.

**Stack**: Java 21, Spring Boot 3.3+, Gradle, MongoDB, Neo4j, Claude Code CLI

## Quick Start

### Prerequisites

- Java 21+
- MongoDB 7+ (or Docker)
- Neo4j 5+ (or Docker)
- Claude Code CLI (logged in)

### Development

```bash
# Build
./gradlew build

# Run with dev profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests
./gradlew test

# View Swagger UI
# http://localhost:8080/swagger-ui.html

# Health check
curl http://localhost:8080/api/health
```

### Profiles

- `dev`: localhost MongoDB/Neo4j, debug logging, Swagger enabled
- `prod`: env-driven config, minimal logging, Swagger disabled
- `test`: testcontainers, mock LLM provider

### Environment Variables

For production deployment:

```bash
export MONGO_URI="mongodb://user:pass@host:27017/againspring?authSource=admin"
export NEO4J_URI="bolt://host:7687"
export NEO4J_USER="neo4j"
export NEO4J_PASSWORD="changeme"
export JWT_SECRET="<min-256-bit-random>"
export LLM_PROVIDER="claude-code"  # or "claude-api" in future
export CLAUDE_BIN="/usr/local/bin/claude"
export CLAUDE_POOL_SIZE="3"
export PROMPTS_PATH="/opt/againspring/shared/prompts"
```

## Project Structure

```
src/main/java/com/againspring/
├── api/             # REST Controllers + DTOs
├── service/         # Business logic
├── llm/             # LLM Bridge (Phase 6+)
├── domain/          # Entities (Phase 2+)
├── repository/      # Data access
├── security/        # Auth/Authz
├── safety/          # Safety guards (Phase 9+)
├── config/          # Spring configs
└── common/          # Shared utilities & exceptions

src/main/resources/
├── application.yml                 # Base config
├── application-{dev,prod,test}.yml # Profile-specific
└── logback-spring.xml              # Logging
```

## Phases

See `/home/justant/Data/Again-Spring/.request/command_BE/BACKEND_WORK_ORDER.md` for full 16-phase roadmap.

**Phase 1 (current)**: Project setup, build files, basic health endpoint
**Phase 2+**: Domain models, auth, APIs, LLM bridge, reports, safety guards, deployment

## Related Docs

- `BACKEND_WORK_ORDER.md` — Full 16-phase checklist
- `DATABASE_SCHEMA.md` — MongoDB & Neo4j schema design
- `LLM_BRIDGE_ARCHITECTURE.md` — Claude Code CLI integration
- `DEPLOYMENT.md` — Docker, Kubernetes, Cloudflare Tunnel setup

## Build Artifacts

Gradle wrapper version: **8.10**

```bash
./gradlew bootJar
# Output: build/libs/againspring-0.1.0.jar
```

## Testing

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests com.againspring.api.HealthControllerTest

# With coverage (requires JaCoCo plugin)
./gradlew test jacocoTestReport
```

## Contributing

- All Java files: package `com.againspring.*`
- Use Lombok annotations for DTOs
- Configuration classes: `@Configuration` + `@EnableXxx` as needed
- Exceptions: extend `BusinessException` with code + message

---

**작성**: Claude Code Agent (Phase 1)  
**버전**: 0.1.0-alpha
