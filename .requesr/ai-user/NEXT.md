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

### 2. dev host docker network 안에서 strict runtime probe

```bash
bash .result/ai-user/scripts/run_python_in_dev_network.sh \
  .result/ai-user/scripts/probe_runtime_pipeline.py \
  --community THEQOO --strict-runtime
```

### 3. THEQOO runtime h2h 생성

```bash
bash .result/ai-user/scripts/run_python_in_dev_network.sh \
  .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO --generator runtime --strict-runtime \
  --n-contexts 20 --drafts 4 --workers 8
```

### 4. NATEPAN cond5 수동 응답

응답 파일:

- `.result/ai-user/blind/r14-cond5-natepan-survey.md`

응답 후 import:

```bash
python3 .result/ai-user/scripts/import_survey_answers.py \
  --survey .result/ai-user/blind/r14-cond5-natepan-survey.md \
  --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json \
  --respondent owner
```

집계:

```bash
python3 .result/ai-user/scripts/summarize_cond5_results.py \
  --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json
```

### 5. THEQOO runtime h2h

dev host network probe 통과 뒤에만 공식값으로 인정한다.

```bash
python3 .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO --generator runtime --strict-runtime \
  --n-contexts 20 --drafts 4 --workers 8
```

## 주의

- direct `/admin/trigger/*`는 외부에서 403/500이라 공식 진입점으로 쓰지 않는다.
- `generate-posts`류 프록시는 h2h raw draft 경로를 대체하지 못하므로 현재 크리티컬 패스가 아니다.
- `AI_USER_ML_ENABLED=true` 전환은 여전히 사람이 수동으로만 한다.
- 자동 proxy/judge 결과는 참고용이다. 최종 cond5 대체물로 쓰지 않는다.
