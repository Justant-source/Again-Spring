# Step 75 (R14) — THEQOO fresh cond5 owner

## 목적

- THEQOO fresh cond5 owner 응답을 answers/results에 반영하고, 탐지 이유를 다음 runtime 라운드 전 cleanup 입력으로 기록한다.

## 실행

```bash
python3 .result/ai-user/scripts/import_survey_answers.py \
  --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json \
  --respondent owner

python3 .result/ai-user/scripts/summarize_cond5_results.py \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json
```

## 결과

- owner import:
  - `pairs_detected=20`
  - `pairs_parsed=20`
  - `imported=20`
- cond5 집계:
  - owner 유효 응답: `19/20`
  - owner AI 탐지 정확도: `84.2% (16/19)`
  - owner 판정: **FAIL**
  - friend: 미응답 (`PENDING`)
  - current combined: **FAIL**

## owner 탐지 신호

1. inline 반응어
   - `개공감`, `헐`이 문장 안에서 뜬금없이 삽입됨

2. 문체 패턴
   - `1도 모르겠음`, `1도 이해가 안 됨`처럼 `1도 ~`를 넓게 남발
   - `월·화·수` 같은 middle dot 표기

3. 내용 구조
   - 같은 갈등 주제가 여러 라운드에서 반복됨
   - THEQOO 대비 장문/장황한 전개
   - `"통장 합치기 얘기가 또 나왔음"` 같은 topic-first opener

## 즉시 반영

- `OutputSanitizer.cleanupTheqoo`
  - inline `개공감/헐` 제거
  - `1도 모르겠음/고` → `진짜 모르겠음/고`
  - `1도 이해가 안 됨` → `도무지 이해가 안 됨`
  - `월·화·수` → `월, 화, 수`
- fallback sync:
  - `.result/ai-user/scripts/build_h2h_survey.py`
  - `.result/ai-user/scripts/run_ab_test.py`

## 의미

- THEQOO는 fresh cond5 owner 기준으로 아직 사람에게 쉽게 탐지된다.
- 따라서 runtime cond4-B가 나중에 다시 PASS하더라도, cond5 없이 활성화 근거로 사용할 수 없다.
- 다음 수동 병목은 `r14-cond5-natepan-survey.md`와 runtime host 복구다.
