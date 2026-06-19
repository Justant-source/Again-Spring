# NEXT — Immediate Actions

## 자동으로 이미 끝난 것

- THEQOO fresh cond5 owner 반영
- feedback 기반 THEQOO cleanup 보강
- blind fingerprint dedupe
- automatic pre-blind gates 3종 추가 및 실측

## 다음 순서

### 1. dev probe 재확인

이제 `localhost:8092`는 1순위 체크가 아니다. 먼저 live dev admin 경로에서 internal stack이 정상인지 다시 확인한다.

```bash
python3 .result/ai-user/scripts/probe_dev_ai_user_stack.py --probe-orchestrator
```

### 2. backend proxy 배포

```bash
# 새 external proxy 엔드포인트
# POST /api/admin/ai-user/generate-posts
# POST /api/admin/ai-user/reset-counter
```

### 3. generate-posts proxy live 검증

```bash
curl -X POST \
  'http://100.81.189.92:8090/api/admin/ai-user/generate-posts?voice=THEQOO&count=1' \
  -H 'Authorization: Bearer <ADMIN_JWT>'
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

proxy live 검증 뒤에만 공식값으로 인정한다.

```bash
python3 .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO --generator runtime --strict-runtime \
  --n-contexts 20 --drafts 4 --workers 8
```

## 주의

- direct `/admin/trigger/*`는 외부에서 403/500이라 공식 진입점으로 쓰지 않는다.
- 새 `generate-posts/reset-counter` backend proxy는 코드 준비만 된 상태고, live 사용 전 dev 배포가 필요하다.
- `AI_USER_ML_ENABLED=true` 전환은 여전히 사람이 수동으로만 한다.
- 자동 proxy/judge 결과는 참고용이다. 최종 cond5 대체물로 쓰지 않는다.
