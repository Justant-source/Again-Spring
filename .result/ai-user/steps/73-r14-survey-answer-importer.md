# Step 73 (R14) — survey answer importer

## 목적

- 사용자가 survey markdown에 직접 적은 `정답/이유`를 answers template json으로 자동 반영한다.

## 추가 파일

- `.result/ai-user/scripts/import_survey_answers.py`

## 사용 예시

```bash
python3 .result/ai-user/scripts/import_survey_answers.py \
  --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json \
  --respondent owner
```

## 검증

1. blank cond5 survey
   - `pairs_detected=2`
   - `pairs_parsed=0`
   - 기존 answers json 무변경 확인

2. filled temp cond5 survey
   - owner 2건 import 성공
   - `choice/reason` 구조 정상 반영

## 반영

- `build_cond5_blind.py`
  - current survey header에 import 명령 안내 추가
- `build_h2h_survey.py`
  - future h2h survey header에도 동일 안내 추가

## 의미

- 다음 owner/friend 응답 라운드부터는 markdown 답변 후 importer 실행만 하면 된다.
- 사람이 답한 뒤의 반복 수작업이 사라진다.
