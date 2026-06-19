# Step 79 (R14) — dev host network harness

## 상태

- strict runtime h2h는 외부에서 새 프록시를 만들수록 복잡해진다.
- `generate-posts`는 최종 글 1건이지, h2h가 필요한 raw `/generate/post` 4-draft 엔드포인트가 아니다.
- 따라서 크리티컬 패스는 dev host의 docker network 안에서 existing runtime harness를 직접 실행하는 것이다.

## 이번 정리

### 1. read-only live probe만 유지

- 파일: `.result/ai-user/scripts/probe_dev_ai_user_stack.py`
- 확인:
  - `/api/health`
  - admin login
  - `/api/admin/health/system`
  - `/api/admin/ai-user/generation-config`
  - `/api/admin/ai-user/generation-status`
  - `/api/admin/ai-rules/prompts/voice/post`
- 하지 않는 것:
  - no-op prompt PUT
  - backfill trigger
  - direct `/admin/trigger/*`

### 2. dev network runner 추가

- 파일: `.result/ai-user/scripts/run_python_in_dev_network.sh`
- 역할:
  - dev host에서 `againspring-dev` network 안에 일회성 Python 컨테이너를 띄운다
  - existing runtime harness를 그대로 실행한다

## dev host에서 실행할 실제 명령

### strict runtime probe

```bash
bash .result/ai-user/scripts/run_python_in_dev_network.sh \
  .result/ai-user/scripts/probe_runtime_pipeline.py \
  --community THEQOO --strict-runtime
```

### THEQOO runtime h2h survey

```bash
bash .result/ai-user/scripts/run_python_in_dev_network.sh \
  .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO --generator runtime --strict-runtime \
  --n-contexts 20 --drafts 4 --workers 8
```

### THEQOO runtime A-B

```bash
bash .result/ai-user/scripts/run_python_in_dev_network.sh \
  .result/ai-user/scripts/run_ab_test.py \
  --community THEQOO --generator runtime \
  --n-contexts 20 --drafts 4 --workers 8 --source-filter theqoo
```

## 결론

- 프록시는 나중 일이다.
- 지금 필요한 건 dev host execution이다.
- THEQOO runtime h2h는 여전히 codex→Sonnet 전환 후 첫 공식 측정이므로, 재실패 가능성을 열어 둔다.
