# Step 67 (R13-next10) — THEQOO awkward phrase hardening

## 상태

- Step 66에서 `유니코드 말줄임표` 정규화까지 반영
- owner v2 이유에 남은 구체 표현 2개를 별도 저비용 후보로 관리하던 상태
- 최종 상태 (2026-06-19 세션 38): **완료**
  - THEQOO 어색한 표현 2개 정규화
  - CLI 경로 재생성으로 샘플 반영 확인 완료

## 이번 세션 변경

### 1. runtime cleanup 보강

- 대상:
  - `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/OutputSanitizer.java`
- 변경:
  - `쓰레기 차도` → `쓰레기통이 차도`
  - `집에서는 딸이 더 조심해야` → `집에서는 여자가 더 조심해야`

### 2. fallback 하네스 동기화

- 대상:
  - `.result/ai-user/scripts/build_h2h_survey.py`
  - `.result/ai-user/scripts/run_ab_test.py`
- 변경:
  - runtime down 시 direct `codex exec` 결과에도 동일한 표현 정규화 적용

### 3. 회귀 테스트 추가

- 대상:
  - `ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/OutputSanitizerHrTest.java`
- 추가:
  - 두 표현 정규화 테스트 1건

## 해석

- 이번 변경은 표현 2개에 대한 exact phrase 수준의 좁은 하드닝이다.
- 의미를 크게 바꾸지 않으면서 owner가 즉시 탐지했던 표면 신호를 줄이는 목적이다.
- 여전히 운영상 핵심 blocker는 `:8092` runtime down이다.

## 재생성 결과

- survey 재생성:
  - `python3 .result/ai-user/scripts/build_h2h_survey.py --community THEQOO --n-contexts 20 --drafts 4 --workers 8 --generator cli`
  - 결과 파일: `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
  - 확인:
    - `쓰레기 차도` **0**
    - `집에서는 딸이 더 조심해야` **0**
    - `…` **0**
    - `헐` / `개공감` / `😥` / `🥲` **0**

- A-B 재측정:
  - `python3 .result/ai-user/scripts/run_ab_test.py --community THEQOO --n-contexts 20 --drafts 4 --workers 8 --source-filter theqoo --generator cli`
  - 결과:
    - `mauve_rerank=0.9907`
    - `mauve_random_mean=0.9820`
    - `Δ=+0.0087`
  - 해석:
    - CLI fallback 경로 기준 cond4-A 양수 유지

## 다음 스텝

1. `:8092` runtime 복구
2. 필요 시 runtime 경로로 동일 survey/AB 재검증
3. 새 사람 응답 라운드 필요 여부 판단
