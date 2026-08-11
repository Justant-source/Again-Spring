# 다시봄 — Backend

> Spring Boot 3.3 기반 갈등 사연 커뮤니티 백엔드.  
> 사연 게시·작성자/상대방 공감 투표·댓글 API와 AI-user 연동을 담당합니다.

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 3.3 |
| 빌드 | Gradle (Kotlin DSL) |
| DB | MariaDB 11 LTS + Flyway |
| ORM | Spring Data JPA |
| 인증 | Spring Security + JWT + Google OAuth2 |
| 이메일 | Spring Mail (Gmail SMTP) |
| 테스트 | JUnit 5 + Testcontainers 1.20.4 |

---

## 빠른 시작 (로컬 개발)

```bash
# 1. DB 시작
cd /home/justant/Data/Again-Spring/env && docker compose up -d   # MariaDB localhost:3306

# 2. 백엔드 실행
cd /home/justant/Data/Again-Spring/backend && ./gradlew bootRun  # localhost:8080

# 3. 헬스 체크
curl http://localhost:8080/api/health
```

> 자세한 환경 설정: [`env/docs/local-dev.md`](../env/docs/local-dev.md)

---

## 디렉토리 구조

```
backend/
├── src/main/java/com/againspring/
│   ├── api/                    # 컨트롤러 (community, admin, marketing 등)
│   ├── domain/                 # 도메인 엔티티 (community, marketing, notification 등)
│   ├── service/                # 비즈니스 로직
│   ├── llm/                    # LLM 브릿지 (RemoteLlmProvider, PromptSanitizer)
│   ├── safety/                 # 위기 감지, 콘텐츠 안전
│   ├── seed/                   # 시드 데이터
│   └── config/, security/, repository/, common/
├── src/main/resources/
│   └── db/migration/           # Flyway V1~V56
├── docs/                       # 개발 문서
└── build.gradle.kts
```

---

## 문서 진입점

| 영역 | 문서 |
|---|---|
| 패키지 구조 | [`docs/structure.md`](docs/structure.md) |
| 아키텍처·흐름 | [`docs/architecture.md`](docs/architecture.md) |
| LLM 브릿지 | [`docs/llm-bridge.md`](docs/llm-bridge.md) |
| OpenAPI·컨트롤러 | [`docs/openapi.md`](docs/openapi.md) |
| 테스트 정책 | [`docs/testing.md`](docs/testing.md) |
| API 명세 | [`../shared/docs/api/`](../shared/docs/api/) |
| 서비스 정책 | [`../shared/docs/policies/`](../shared/docs/policies/) |

---

**마지막 업데이트**: 2026-06-03
