# docs/backend — BE 특화

다시봄 백엔드(Spring Boot 3.3 + Java 21 + MariaDB) 내부 문서. 이 디렉토리는 백엔드 구현 세부 사항을 다룹니다.

**서비스 전체 정책** (심리학 모델, 금지어 정의, 온보딩 매핑 등)은 `../shared/policies/`에 있습니다.

## 문서 인덱스

- [structure.md](./structure.md) — `com.againspring.*` 패키지 구조 + 각 패키지 책임
- [architecture.md](./architecture.md) — 레이어 흐름, JPA, Flyway, 커뮤니티 서비스, 도메인 이벤트
- [llm-bridge.md](./llm-bridge.md) — LLM 브릿지 구조, RemoteLlmProvider, PromptSanitizer
- [testing.md](./testing.md) — 테스트 전략, 커버리지 정책, `./gradlew test`
- [openapi.md](./openapi.md) — Swagger UI, DTO 컨벤션, API 문서화

## AI User 연계

- [ai-user.md](./ai-user.md) — backend가 shared ai-user 런타임과 연결되는 방식
- [ai-learning.md](./ai-learning.md) — backend 관점의 shared learning 서비스 정리
- [ai-learning-continuous-system.md](./ai-learning-continuous-system.md) — 과거 구상 문서. historical context 전용

## 빠른 시작

```bash
cd backend
./gradlew bootRun        # localhost:8080 (dev 프로파일 자동 적용)
./gradlew test           # 전체 테스트
```

자세한 로컬 개발 환경/도커 절차는 [`../env/local-dev.md`](../env/local-dev.md)를 참조.
