# backend/docs — BE 특화

다시봄 백엔드(Spring Boot 3.3 + Java 21 + MariaDB) 내부 문서. 이 디렉토리는 백엔드 구현 세부 사항을 다룹니다.

**서비스 전체 정책** (심리학 모델, 금지어 정의, 온보딩 매핑 등)은 `../../shared/docs/policies/`에 있습니다.

## 문서 인덱스

- [structure.md](./structure.md) — `com.againspring.*` 패키지 구조 + 각 패키지 책임
- [architecture.md](./architecture.md) — 레이어 흐름, JPA, Flyway, State Machine, 도메인 이벤트
- [llm-bridge.md](./llm-bridge.md) — Claude Code CLI 호출, ProcessBuilder, Semaphore(3), 프롬프트 로더, 모니터링
- [policies/](./policies/) — BE 구현 정책
  - [auth-jwt.md](./policies/auth-jwt.md) — JWT 토큰 생성/검증
  - [oauth-google.md](./policies/oauth-google.md) — Google OAuth2 콜백
  - [keyword-guard.md](./policies/keyword-guard.md) — 금지어 검사 (4 레벨)
  - [prompt-sanitizer.md](./policies/prompt-sanitizer.md) — 입력 정제 & injection 차단
- [testing.md](./testing.md) — 테스트 전략, 커버리지 정책, `./gradlew test`
- [openapi.md](./openapi.md) — Swagger UI, DTO 컨벤션, API 문서화

## 빠른 시작

```bash
cd backend
./gradlew bootRun        # localhost:8080 (dev 프로파일 자동 적용)
./gradlew test           # 전체 테스트
```

자세한 로컬 개발 환경/도커 절차는 `../../env/docs/local-dev.md` 참조.
