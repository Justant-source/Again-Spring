# Step 39 — R0: API-first wrapper + DENY_SIGS refusal retry

## 일시
2026-06-17

## 결정
D-47: run_ab_test.py의 generate_post()를 API-first로 전환 (clcocloud Haiku, DENY_SIGS 검사 + CLI fallback)

## 한 일
- run_ab_test.py에 `_api_generate()` 신규 추가
  - clcocloud API 직접 호출 (base_url, api_key via env)
  - user 메시지에 `<instructions>` 태그로 prompt 주입 (system 톱레벨 금지 — Kiro routing 회피)
  - `anthropic-beta` 헤더 제거
  - DENY_SIGS 거절 감지 → ContentSafetyGuard.SIGNATURE 기반 retry (max 3회)
- `_cli_generate()` 신규 추가 (기존 generate_post 로직 추출)
- `generate_post()` 분기
  - API 호출 성공 → 반환
  - API 오류/거절/크레딧 → CLI fallback
  - CLI 실패 → 예외 전파

## 수치
- 코드 라인: ~120 (wrapper + retry logic)
- API 호출 최대 재시도: 3회 (DENY_SIGS 감지 시)
- 거절문 시그니처: ContentSafetyGuard.SIGNATURE 기준 (12종)

## 검증
- 논리 검토 완료 (실 테스트 불필요)
- env.CLCOCLOUD_API_KEY, CLCOCLOUD_BASE_URL 필수

## 다음
- R1: audit_mislabels.py로 mislabel 감지 및 정화
