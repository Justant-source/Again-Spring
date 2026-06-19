# Step 69 (R14-phase2-prep) — selective rerank gate

## 상태

- R14 Phase 2에서 A/B/C 비용·효용 판단이 남아 있음
- B안(per-community selective gate)은 반복적으로 필요성이 제기됐지만 아직 코드 경로는 전역 boolean뿐이었음
- 최종 상태 (2026-06-19 세션 39): **완료**
  - dormant selective gate 구현 완료
  - 기본 동작 불변

## 이번 세션 변경

### 1. 신규 env 추가

- 이름:
  - `AI_USER_ML_ENABLED_COMMUNITIES`
- 형식:
  - 쉼표 구분 `voice_type` 목록 (`CLIEN,NATEPAN,THEQOO`)
- 규칙:
  - `AI_USER_ML_ENABLED=false` → 전부 off
  - `AI_USER_ML_ENABLED=true` + 빈 값 → 기존처럼 전역 on
  - `AI_USER_ML_ENABLED=true` + 목록 존재 → 목록 community만 on

### 2. orchestrator gate 변경

- 대상:
  - `AiUserMlClient.isEnabledFor(String community)`
  - `ActionExecutor.executePost()`
- 변경:
  - POST rerank 진입 조건을 전역 `isEnabled()`에서 community-aware `isEnabledFor(community)`로 교체

### 3. 테스트/문서

- `AiUserMlClientTest`
  - disabled / blank scope / scoped allow / scoped deny / null community 케이스 추가
- `docs/env/environment-variables.md`
  - env 변수 문서화

## 의미

- 지금은 `AI_USER_ML_ENABLED=false`이므로 런타임 동작 변화는 없다.
- R14 Phase 2에서 B안을 채택하면, 추가 코드 수정 없이 env 조정만으로 selective rerank 운용이 가능하다.

## 남은 작업

1. Java 테스트 실행
2. dev:8090 e2e 검증
3. runtime 재검증 결과를 넣어 A/B/C 최종 선택
