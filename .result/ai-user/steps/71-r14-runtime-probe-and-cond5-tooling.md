# Step 71 (R14) — runtime probe + cond5 tooling

## 배경

- host blocker 때문에 runtime 실측은 여전히 대기 상태다.
- 그렇다고 host가 열린 뒤에 도구를 만들기 시작하면, R14 Phase 1과 Phase 3가 또 지연된다.

## 이번 세션 추가물

### 1. runtime probe

- 파일: `.result/ai-user/scripts/probe_runtime_pipeline.py`
- 목적:
  1. `:8092` health 확인
  2. `/generate/post` 4 draft 생성 확인
  3. ML `/rerank` winner 확인
  4. THEQOO 알려진 tell 스캔 + 수동 육안 검토용 report 저장
- 현재 실행 결과:
  - `runtime_down`
  - `connection refused`

### 2. cond5 blind builder

- 파일: `.result/ai-user/scripts/build_cond5_blind.py`
- 입력:
  - `/corpus/export/blind` raw export json
  - 또는 endpoint 직접 fetch
- 출력:
  - survey markdown
  - answers template json
- 특징:
  - owner/friend 응답 구조를 기본 제공
  - optional `used-corpus-ids.json` 필터 지원
  - smoke:
    - CLIEN `n_per_class=4`, `n_pairs=2` fetch 성공
    - 생성 파일:
      - `.result/ai-user/blind/r14-cond5-clien-smoke-survey.md`
      - `.result/ai-user/blind/r14-cond5-clien-smoke-answers-template.json`

### 3. cond5 summarizer

- 파일: `.result/ai-user/scripts/summarize_cond5_results.py`
- 출력:
  - owner / friend / combined
  - 유효/무효 응답 수
  - AI 탐지 정확도
  - cond5 PASS/FAIL
  - smoke:
    - empty-response 기준 `PENDING` markdown 생성 성공
    - 파일: `.result/ai-user/blind/r14-cond5-clien-smoke-results.md`

## 권장 실행 순서

1. dev host에서 `:8092` 복구
2. `probe_runtime_pipeline.py --strict-runtime`
3. THEQOO/CLIEN/NATEPAN runtime h2h
4. NATEPAN/THEQOO `build_cond5_blind.py`
5. owner+friend 응답 수집
6. `summarize_cond5_results.py`

## 예시 명령

```bash
python3 .result/ai-user/scripts/probe_runtime_pipeline.py \
  --community THEQOO --strict-runtime

python3 .result/ai-user/scripts/build_cond5_blind.py \
  --community THEQOO --fetch-export --n-per-class 20 --n-pairs 20 \
  --used-ids .result/ai-user/blind/used-corpus-ids.json

python3 .result/ai-user/scripts/summarize_cond5_results.py \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json
```

## 상태

- ✅ 코드 준비 완료
- ⛔ host/runtime 복구 전 공식 실측은 아직 시작 불가
