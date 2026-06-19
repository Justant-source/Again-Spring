# Step 66 (R13-next9) — THEQOO ellipsis hardening

## 상태

- Step 65에서 THEQOO는 owner v2 기준 cond4-B PASS까지 확보
- 다만 owner 이유에 반복적으로 남은 신호는 `유니코드 말줄임표(…)`
- 최종 상태 (2026-06-19 세션 37): **완료**
  - THEQOO ellipsis hardening 반영
  - runtime/CLI fallback cleanup 정합화 유지

## 이번 세션 변경

### 1. runtime cleanup 보강

- 대상:
  - `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/OutputSanitizer.java`
- 변경:
  - THEQOO cleanup에서 `…`와 `⋯`를 ASCII `...`로 정규화
- 이유:
  - 오너 피드백상 문제는 "말줄임표를 쓴다"보다 "유니코드 특수문자를 쓴다" 쪽이었음

### 2. fallback 하네스 동기화

- 대상:
  - `.result/ai-user/scripts/build_h2h_survey.py`
  - `.result/ai-user/scripts/run_ab_test.py`
- 변경:
  - runtime이 죽어 있을 때 direct `codex exec` 결과에도 동일하게 `…` → `...` cleanup 적용
- 이유:
  - D-77/D-78에서 정리했듯, 측정 경로와 runtime 후처리가 최대한 같은 방향이어야 함

### 3. 회귀 테스트 추가

- 대상:
  - `ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/OutputSanitizerHrTest.java`
- 추가:
  - THEQOO `…` 정규화 테스트 1건

## 해석

- 이번 변경은 연구 게이트 판정을 바꾸는 재측정이 아니라, owner 피드백에 근거한 저비용 후속 하드닝이다.
- 현재 운영상 핵심 blocker는 여전히 `:8092` runtime down이다.
- 따라서 다음 의미 있는 측정은 runtime 복구 후 같은 경로로 survey/AB를 다시 생성할 수 있을 때다.

## 결론

- THEQOO `유니코드 말줄임표` 신호는 코드 레벨에서 줄여뒀다.
- 연구 게이트 상태는 그대로 **GO candidate**다.
- `AI_USER_ML_ENABLED=true`는 여전히 자동 전환하지 않는다.

## 다음 스텝

1. `:8092` runtime 복구
2. 필요 시 THEQOO survey/AB 동일 경로 재생성
3. 잔여 어색한 표현 신호 별도 축소 여부 판단
