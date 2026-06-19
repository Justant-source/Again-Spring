# NEXT — Immediate Actions

## 자동으로 이미 끝난 것

- THEQOO fresh cond5 owner 반영
- feedback 기반 THEQOO cleanup 보강
- blind fingerprint dedupe
- automatic pre-blind gates 3종 추가 및 실측

## 다음 순서

### 1. dev probe 재확인

이제 `localhost:8092`는 1순위 체크가 아니다. 먼저 read-only live dev admin 경로를 확인한다.

```bash
python3 .result/ai-user/scripts/probe_dev_ai_user_stack.py
```

### 2. dev host one-shot runner

```bash
bash .result/ai-user/scripts/run_r14_runtime_phase1.sh
```

### 3. 수동 응답

응답 파일:

- `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
- `.result/ai-user/blind/r13-h2h-clien-survey.md`
- `.result/ai-user/blind/r13-h2h-natepan-survey.md`
- `.result/ai-user/blind/r14-cond5-natepan-survey.md`

응답 후 import:

```bash
python3 .result/ai-user/scripts/import_survey_answers.py \
  --survey <survey.md> \
  --answers <answers.json> \
  --respondent owner
```

### 4. 집계

```bash
python3 .result/ai-user/scripts/summarize_h2h_results.py \
  --answers <h2h-answers.json>

python3 .result/ai-user/scripts/summarize_cond5_results.py \
  --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json
```

## 주의

- direct `/admin/trigger/*`는 외부에서 403/500이라 공식 진입점으로 쓰지 않는다.
- runtime h2h 공식값은 one-shot runner 성공 뒤에만 인정한다.
- `generate-posts`류 프록시는 h2h raw draft 경로를 대체하지 못하므로 현재 크리티컬 패스가 아니다.
- `AI_USER_ML_ENABLED=true` 전환은 여전히 사람이 수동으로만 한다.
- 자동 proxy/judge 결과는 참고용이다. 최종 cond5 대체물로 쓰지 않는다.
