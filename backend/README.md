# Backend — 다시봄 AI 중재 서비스

**Stack**: Java 21, Spring Boot 3.3, Gradle, MariaDB 11, Claude Code CLI

## Quick Start

```bash
# 1. DB 시작 (호스트에서)
cd ../infra && docker compose up -d

# 2. 백엔드 실행 (dev 프로파일 자동 적용)
./gradlew bootRun          # localhost:8080

# 3. 테스트
./gradlew test

# 4. 확인
curl http://localhost:8080/api/health
```

## 전체 문서

자세한 아키텍처, 구조, 정책은 [`docs/README.md`](./docs/README.md) 참조.

## BE 특화 정책

- 구현 정책 (JWT, OAuth, 금지어 검사, 입력 정제): [`docs/policies/`](./docs/policies/)
- 서비스 전체 정책 (심리학 모델, 온보딩, 위기 감지): `../../shared/docs/policies/`

## 환경 설정

MariaDB 필수. `application.yml`에서 프로파일별 자동 설정:

```bash
# dev: localhost MariaDB (docker-compose)
# prod: 환경변수 기반 (DB_URL, JWT_SECRET 등)
# test: H2 in-memory
```

자세한 환경 변수: `docs/README.md`

---

**마지막 업데이트**: 2026-04-26
