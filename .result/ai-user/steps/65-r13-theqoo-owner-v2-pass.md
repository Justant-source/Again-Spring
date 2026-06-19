# Step 65 (R13-next8) — THEQOO owner v2 h2h 집계 + 전역 재판정

## 상태

- Step 64에서 THEQOO survey v2와 A-B 재측정 완료
- 사용자가 응답한 파일은 표준 이름이 아니라
  `r13-h2h-theqoo-surveyr13-h2h-theqoo-surveyr13-h2h-theqoo-survey.md`
- 최종 상태 (2026-06-19 세션 36): **완료**
  - owner v2 h2h 집계 완료
  - THEQOO cond4-B PASS
  - 연구 게이트 기준 전역 GO candidate

## 이번 세션 변경

### 1. owner 응답 반영

- 소스:
  - `.result/ai-user/blind/r13-h2h-theqoo-surveyr13-h2h-theqoo-surveyr13-h2h-theqoo-survey.md`
- 반영 대상:
  - `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`
- 특징:
  - `답변 불가` → `답변불가`
  - `구별 불가` 계열 → `판단불가`
  - summarize 스크립트가 해석 가능한 값으로 정규화

### 2. 집계 결과

- 파일:
  - `.result/ai-user/blind/r13-h2h-theqoo-results.md`
- 결과:
  - owner 유효 응답: **12/20**
  - rerank 탐지: **3/12 = 25.0%**
  - random 탐지: **9/12 = 75.0%**
  - D-68 판정: **PASS**

### 3. 해석

- v1에서는 `헐`/이모지가 주된 탐지 신호였고 FAIL이었다.
- v2에서는 그 신호가 사라졌고, 오너가 새로 반복 지적한 건:
  - 유니코드 말줄임표 `…`
  - 몇몇 어색한 구체 표현
- 그럼에도 rerank가 random보다 훨씬 덜 탐지되어 cond4-B는 통과했다.

## 결론

- THEQOO cond4-A: **PASS** (`Δ_real=+0.0686`)
- THEQOO cond4-B: **PASS** (`25.0% ≤ 75.0%`)
- 따라서 CLIEN / NATEPAN / THEQOO 모두 신 cond4를 통과한다.
- 연구 게이트 cond1~cond5 기준 전역 상태는 **GO candidate**다.
- 다만 `AI_USER_ML_ENABLED=true`는 여전히 **수동 활성화만 가능**하다.

## 다음 스텝

1. `…` 말줄임표 정규화 여부 결정
2. `:8092` runtime 경로 복구
3. 수동 활성화 여부 결정
