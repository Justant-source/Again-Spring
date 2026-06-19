# NEXT — Immediate Actions

## 자동으로 이미 끝난 것

- THEQOO fresh cond5 owner 반영
- feedback 기반 THEQOO cleanup 보강
- blind fingerprint dedupe
- automatic pre-blind gates 3종 추가 및 실측

## 다음 순서

### 1. runtime host 복구

가장 먼저 필요하다. 이게 안 열리면 runtime 공식 재측정은 전부 대기다.

```bash
python3 .result/ai-user/scripts/recover_runtime_host.py
```

직접 명령으로 보면 아래와 같다.

```bash
cd /home/justant/Data/Again-Spring
docker compose -f env/docker-compose.dev.yml --env-file env/.env.dev up -d llm-ai-user
```

### 2. runtime probe

```bash
python3 .result/ai-user/scripts/probe_runtime_pipeline.py \
  --community THEQOO --strict-runtime
```

### 3. NATEPAN cond5 수동 응답

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

### 4. THEQOO runtime h2h

runtime이 살아난 뒤에만 공식값으로 인정한다.

```bash
python3 .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO --generator runtime --strict-runtime \
  --n-contexts 20 --drafts 4 --workers 8
```

## 주의

- 현재 셸에서는 `docker`와 `curl`이 없고 `ssh`도 권한 거부다.
- 그래서 1번은 반드시 dev host 접근 권한이 있는 주체가 실행해야 한다.
- `AI_USER_ML_ENABLED=true` 전환은 여전히 사람이 수동으로만 한다.
- 자동 proxy/judge 결과는 참고용이다. 최종 cond5 대체물로 쓰지 않는다.
