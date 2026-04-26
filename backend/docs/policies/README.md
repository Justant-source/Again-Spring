# policies/ — BE 구현 정책

백엔드 구현 관련 정책 문서. 각 파일은 특정 도메인의 구현 세부 사항을 다룹니다.

## 문서 목록

- **[auth-jwt.md](./auth-jwt.md)** — JWT 토큰 생성/검증, 서명 알고리즘, 토큰 만료 정책
- **[oauth-google.md](./oauth-google.md)** — Google OAuth2 콜백 처리, 사용자 프로비저닝 흐름
- **[keyword-guard.md](./keyword-guard.md)** — 금지어 검사 구현, 4가지 레벨 분류, 위기 감지
- **[prompt-sanitizer.md](./prompt-sanitizer.md)** — 사용자 입력 정제, injection 패턴 차단, 길이 제한

## 서비스 전체 정책 (shared/)

cross-cutting 정책 (양쪽 영향):
- 금지어 목록, 위기 키워드 정의 → `shared/docs/policies/`
- 심리학 모델 → `shared/docs/v1/PSYCHOLOGY_MODEL_RATIONALE.md`
- 온보딩 매핑 → `shared/docs/v1/ONBOARDING_MAPPING.md`

**BE 구현만**: 이 디렉토리에 있는 4개 파일들
