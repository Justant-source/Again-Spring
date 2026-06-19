# Step 64 (R13-next7) — THEQOO survey v2 재생성 + A-B 재측정

## 상태

- Step 62에서 THEQOO 후처리 1차 축소 패치 적용
- Step 63에서 측정 하네스 runtime 우선 구조로 정합화
- 하지만 `:8092`가 내려가 있어 runtime 경로를 실제로 사용하지 못함
- 최종 상태 (2026-06-19 세션 35): **완료**
  - CLI fallback에도 THEQOO cleanup을 동기화
  - survey v2와 A-B 재측정까지 완료

## 이번 세션 변경

### 1. CLI fallback에도 THEQOO cleanup 적용

- 파일:
  - `.result/ai-user/scripts/build_h2h_survey.py`
  - `.result/ai-user/scripts/run_ab_test.py`
- 추가:
  - trailing standalone `헐` 제거
  - 문장 부호 뒤 `헐` 제거
  - standalone `헐 제가/내가/...` 제거
  - 유니코드 이모지 제거
  - `개공감` trailing 제거

### 2. THEQOO survey v2 재생성

- 명령:
  - `python3 .result/ai-user/scripts/build_h2h_survey.py --community THEQOO --n-contexts 20 --drafts 4 --workers 8 --generator cli`
- 결과:
  - `20 contexts × 4 drafts`
  - 최종 `20쌍`
  - 파일:
    - `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
    - `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`

### 3. survey 텍스트 즉시 점검

- 카운트:
  - `헐`: **0**
  - `개공감`: **0**
  - `😥`: **0**
  - `🥲`: **0**

### 4. A-B 재측정

- 명령:
  - `python3 .result/ai-user/scripts/run_ab_test.py --community THEQOO --n-contexts 20 --drafts 4 --workers 8 --source-filter theqoo --generator cli`
- job:
  - `01KVENTAXQA711C2J4S1HM230V`
- 결과:
  - `mauve_rerank = 0.9907`
  - `mauve_random_mean = 0.9221`
  - `mauve_random_std = 0.0367`
  - `mauve_random_seeds = [0.8961, 0.8961, 0.9741]`
  - `delta = +0.0686`
  - `snapshot_size = 330`
  - `degraded = False`

## 해석

- 오프라인 cond4-A 신호는 계속 **양수 유지**다.
- 이전 owner가 반복적으로 잡아낸 `헐/이모지` 신호는 survey v2 텍스트에서 사라졌다.
- 따라서 지금 남은 핵심 질문은:
  - 이 교정이 실제 사람 h2h에서 rerank 탐지율을 낮추는가

## 다음 스텝

1. `r13-h2h-theqoo-survey.md`에 owner 응답 입력
2. `summarize_h2h_results.py`로 cond4-B 재집계
3. 여전히 FAIL이면 rerank/prompt까지 확대
