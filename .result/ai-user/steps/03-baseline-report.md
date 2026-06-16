# Step 3 완료 기록 — 평가 하네스 + 베이스라인

**날짜**: 2026-06-16  
**세션**: 3  
**상태**: ✅ 완료 (44/44 pytest 통과 + 4개 커뮤니티 베이스라인 확보)

---

## 한 일

### 신규 파일 (WSL `~/Data/Again-Spring-AI-User/`)

| 파일 | 역할 |
|---|---|
| `app/ml/eval_harness.py` | Level 1 메트릭 구현 (KatFishNet 평균·burstiness·종결어미 JS-div·MAUVE) |
| `tests/test_eval_harness.py` | 13개 단위 테스트 (헬퍼 함수 + compute_community_baseline 통합) |
| `app/api/routes_eval.py` | `_run_eval_baseline()` 실제 실행 와이어링 (이전 stub → 실 동작) |

### eval_harness.py 구현 내용

- `compute_community_baseline(human_texts, ai_texts, community) → CommunityMetrics`
- AI 샘플 없을 시 `ai_*` 필드 → None (Step 5 전까지 정상)
- 종결어미 JS-div: `scipy.spatial.distance.jensenshannon` (sqrt(JSD) 반환, 최대 ~0.83)
- MAUVE: `mauve-text` 설치 시 자동 사용, 미설치 시 None (현재 None)
- Burstiness: CV(std/mean) of sentence lengths
- KatFishNet 9개 피처 평균 (기존 `features_katfish.py` 재사용)

### routes_eval.py 와이어링

- `POST /eval/baseline` → Job DB 저장 → `submit_job(fn=_run_eval_baseline)` 즉시 dispatch
- `_run_eval_baseline`: CorpusItem 조회 → 커뮤니티별 `compute_community_baseline` → `eval_run` 테이블 저장 + `data/eval/{job_id}.json` 기록
- `GET /eval/{job_id}` → 폴링 가능

---

## 베이스라인 결과 (2026-06-16, POST 타입, AI 샘플 없음)

**잡 ID**: `01KV5WZHQVRTM3SZ5WD673YPQ3`  
**파일**: `data/eval/01KV5WZHQVRTM3SZ5WD673YPQ3.json`

| 커뮤니티 | n_human | comma_rate | spacing_error_rate | pos_ngram_diversity | connector_rate | avg_sentence_len | burstiness |
|---|---|---|---|---|---|---|---|
| **DCINSIDE** | 35 | 0.030 | **0.919** | **0.212** | 0.003 | **49.4** | 0.703 |
| **NATEPAN** | 396 | 0.011 | 0.694 | **0.627** | 0.002 | 18.0 | **0.936** |
| **THEQOO** | 300 | 0.011 | 0.400 | 0.541 | 0.001 | **7.8** | 0.930 |
| **CLIEN** | 228 | 0.022 | 0.746 | 0.570 | 0.003 | 13.1 | 0.809 |

**ending_js_div**: null (AI 샘플 없음 — Step 5 후 재측정)  
**mauve**: null (mauve-text 미설치 + AI 샘플 없음)

---

## 주요 발견

### 커뮤니티별 특성

1. **DCINSIDE** — 특이값:
   - `spacing_error_rate` 91.9% → 매우 거칠고 비형식적 문체
   - `pos_ngram_diversity` 21.2% (다른 커뮤니티의 1/3 수준) → 반복적 어휘/구조
   - `avg_sentence_length` 49.4 어절 → **의심값**: DC 게시글은 "다." 종결 패턴 사용이 적어 문장 분할기가 전체를 1문장으로 처리하는 경우 多. Step 4에서 문장 분할 개선 필요.
   - `burstiness` 0.70 (상대적으로 낮음) → 단일 장문 경향과 연관

2. **NATEPAN** — 가장 "표준적":
   - 396개 샘플 최다 코퍼스
   - `pos_ngram_diversity` 62.7% (최고) → 다양한 어휘 사용
   - `burstiness` 0.94 → 인간 텍스트 전형적 고변동

3. **THEQOO** — 초단문 스타일:
   - `avg_sentence_length` 7.8 어절 (최저) → 짧고 감탄사적 문체
   - `spacing_error_rate` 40.0% (가장 낮음) → 상대적으로 맞춤법 정확
   - `burstiness` 0.93 → 길이 변동이 있음 (때때로 장문 포함)

4. **CLIEN** — 중간 특성 (신규 발견):
   - 예상 외 228개 샘플 이미 확보 (Step 2 당시 미집계)
   - `spacing_error_rate` 74.6% (중간), `pos_ngram_diversity` 57.0% (중간)

### AI 문체와의 예측 차이

Step 5에서 AI negative 수집 후 비교 예상:
- **comma_rate**: AI가 높을 것 (현재 human 1-3%, AI 예상 5-15%)
- **spacing_error_rate**: AI가 낮을 것 (AI는 맞춤법 정확 경향)
- **pos_ngram_diversity**: AI가 낮을 것 (반복적 구조)
- **connector_rate**: AI가 높을 것 (접속부사 남용)
- **burstiness**: AI가 낮을 것 (균일한 문장 길이)

---

## 완료 기준 검증

| 기준 | 결과 |
|---|---|
| `app/ml/eval_harness.py` 신규 + 3개 이상 메트릭 | ✅ burstiness + comma_rate + spacing_error + POS diversity + connector_rate |
| `POST /eval/baseline` 잡 완료 → JSON 저장 | ✅ `data/eval/01KV5WZHQVRTM3SZ5WD673YPQ3.json` |
| 3개 이상 커뮤니티 결과 | ✅ 4개 (DCINSIDE/NATEPAN/THEQOO/CLIEN) |
| `pytest tests/` 44/44 통과 | ✅ |
| ending_js_div · MAUVE 존재 | ⚠️ AI 샘플 없어 null — Step 5 후 `POST /eval/baseline` 재실행으로 갱신 |

> **Note**: 로드맵 원문 "MAUVE, 쉼표율, 종결어미 JS-div 값 존재" 기준에서 MAUVE와 JS-div가 null.  
> AI 샘플은 Step 5(ActionExecutor)에서 push 예정. Step 3의 핵심 목적(하네스 구현 + 베이스라인 수치 확보)은 완료.  
> 쉼표율(comma_rate) 및 기타 KatFishNet 메트릭은 실측값 존재. Step 5 후 재실행 시 JS-div/MAUVE 추가됨.

---

## 함정 기록

- **scipy jensenshannon**: sqrt(JSD) 반환 (최대 ~0.832 for 완전 이산 분포). `> 0.99` 테스트 → `> 0.80`으로 수정.
- **docker cp vs 볼륨 마운트**: 소스 코드는 빌드 시 COPY, `/app/data`만 볼륨. 소스 수정 후 `docker cp` 또는 rebuild 필요.
- **curl -sf**: 에러 응답(401/422)을 빈 문자열로 반환 → 디버깅 시 `-v` 사용.
- **DCINSIDE avg_sentence_length 49**: 문장 분할기가 "다." 외 종결 패턴("ㅋㅋ", "...", "!") 처리 미비. Step 4에서 분할기 개선 가능.

---

## 다음 구체 작업 (Step 4 — 판별기 학습)

- `app/ml/discriminator.py`: KcELECTRA-base + KatFishNet LR 스태킹
- `app/ml/train_pipeline.py`: GPU 학습 (VRAM 가드: `mem_get_info`, fp16, `empty_cache`)
- `app/ml/registry.py`: 커뮤니티별 체크포인트 관리
- `POST /train` → GPU 잡 → AUC 리포트
- `POST /score`, `POST /rerank` → **CPU 추론** (GPU 미사용)
- 선결조건: AI negative (Step 5) 없이 Step 4 착수 가능 (human only로 AUC 측정 가능하나, 클래스 불균형)
  → 선택: dummy AI negative (균일 랜덤 피처) 로 먼저 학습 또는 Step 5 먼저 진행 여부 결정 필요
