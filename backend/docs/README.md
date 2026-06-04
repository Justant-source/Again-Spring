# backend/docs — BE 특화

다시봄 백엔드(Spring Boot 3.3 + Java 21 + MariaDB) 내부 문서. 이 디렉토리는 백엔드 구현 세부 사항을 다룹니다.

**서비스 전체 정책** (심리학 모델, 금지어 정의, 온보딩 매핑 등)은 `../../shared/docs/policies/`에 있습니다.

## 문서 인덱스

- [structure.md](./structure.md) — `com.againspring.*` 패키지 구조 + 각 패키지 책임
- [architecture.md](./architecture.md) — 레이어 흐름, JPA, Flyway, 커뮤니티 서비스, 도메인 이벤트
- [llm-bridge.md](./llm-bridge.md) — LLM 브릿지 구조, RemoteLlmProvider, PromptSanitizer
- [testing.md](./testing.md) — 테스트 전략, 커버리지 정책, `./gradlew test`
- [openapi.md](./openapi.md) — Swagger UI, DTO 컨벤션, API 문서화

## AI User 자기진화 시스템

- [ai-learning.md](./ai-learning.md) — 전체 시스템 개요, 아키텍처, Phase 1~3 (자기비평, RAG, 크롤링), API 명세
- [**ai-learning-continuous-system.md**](./ai-learning-continuous-system.md) — **✨ 지속적 학습 시스템 (권장)** — 단일 중앙 ai-learning 서비스에서 dev/prod가 공유하는 예시뱅크를 지속적으로 크롤링 & 누적하는 방식. 시간축별 성장 예측, 모니터링 가이드, FAQ 포함.

## 빠른 시작

```bash
cd backend
./gradlew bootRun        # localhost:8080 (dev 프로파일 자동 적용)
./gradlew test           # 전체 테스트
```

자세한 로컬 개발 환경/도커 절차는 `../../env/docs/local-dev.md` 참조.
