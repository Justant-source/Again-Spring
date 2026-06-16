# Step 27 (M1 재실행) — cond4 A-B 재측정 (K=3 시드, 40 contexts)

**날짜**: 2026-06-16  
**목적**: 이전 단일런 THEQOO Δ=+0.4834를 K=3 고정시드 × 40 contexts로 재측정해 UNVERIFIED 해소.

---

## 사전 수정 사항

### routes_eval.py 수정 (K=3 고정시드)

이전 코드: `random.randint()` (무시드, 런마다 다름)  
수정 후:
```python
random_seeds = [42, 137, 2026]
random_mauve_scores = []
for seed in random_seeds:
    random.seed(seed)
    seed_winners = [drafts_ctx["drafts"][random.randint(...)]]
    seed_mauve = _try_mauve(seed_winners, human_texts)
    if seed_mauve is not None:
        random_mauve_scores.append(seed_mauve)
mauve_random_mean = sum(random_mauve_scores)/len(random_mauve_scores)  # ← 3시드 평균
mauve_random_std = sqrt(variance)
delta = mauve_rerank - mauve_random_mean
```

배포: docker cp + 컨테이너 restart (2026-06-16 세션).

### DB 스키마 수정

`jobs.params_json TEXT(65535)` → `MEDIUMTEXT(16MB)`: 40 contexts × 4 drafts × ~1500자 = ~240KB 초과로 500 에러.  
직접 SQL: `ALTER TABLE jobs MODIFY COLUMN params_json MEDIUMTEXT`  
models.py도 `Text(16777215)`로 업데이트 (WSL 파일 수정).

---

## 실행 및 결과

### THEQOO (2026-06-16 18:48~18:50)

```
커뮤니티    : THEQOO
실행방식    : python3 run_ab_test.py --community THEQOO --n-contexts 40 --drafts 4 --workers 12
실제 컨텍스트: 16/40 (24개 0 drafts — Claude CLI 중단 원인: 실행 중 binary 경로 symlink 갱신(18:49))
```

| 지표 | 값 |
|---|---|
| mauve_rerank | 0.9815 |
| mauve_random_mean (3시드) | 0.9908 |
| mauve_random_seeds | [0.9953, 0.9773, 1.0] |
| mauve_random_std | 0.0098 |
| **delta** | **−0.0094** ← NEGATIVE |
| n_contexts | 16 (≥10 MAUVE 최소 충족) |
| snapshot_size | 376 |

**cond4 THEQOO**: ❌ FAIL (delta < 0)

### NATEPAN (2026-06-16 18:52~18:55)

```
커뮤니티    : NATEPAN
실행방식    : python3 run_ab_test.py --community NATEPAN --n-contexts 40 --drafts 4 --workers 12
실제 컨텍스트: 40/40 (전체 성공 — Claude CLI 안정 후 재실행)
```

| 지표 | 값 |
|---|---|
| mauve_rerank | 0.8273 |
| mauve_random_mean (3시드) | 0.8441 |
| mauve_random_seeds | [0.8734, 0.7346, 0.9242] |
| mauve_random_std | 0.0801 |
| **delta** | **−0.0167** ← NEGATIVE (|Δ|/std = 0.21, 노이즈 수준) |
| n_contexts | 40 |
| snapshot_size | 388 |

**cond4 NATEPAN**: ❌ FAIL (delta < 0, 단 std >> |Δ| → 노이즈 수준)

---

## 해석

**왜 delta < 0인가?**

- 판별기 P(human) 역전 상태 (M2 확인: 슬랭→P=0.0000044, 격식AI→P=0.9976)
- 리랭커가 "P(human) 가장 높은 초안" = 실은 가장 격식적인(=AI스러운) 초안을 winner로 선택
- 결과: rerank winner가 random winner보다 더 AI처럼 들림 → MAUVE 저하
- **예상된 결과**: 판별기가 고장난 상태에서 리랭커 사용 = 역효과

**이전 Δ=+0.4834의 진실**

seed 42: 0.9953, seed 137: 0.9773, seed 2026: 1.0 → random arm 평균 0.9908  
rerank = 0.9815 < 0.9908 → 이전 단일런에서 random arm이 0.4961(= seed 137보다 낮음)이 나온 건 순수 운.

---

## 결론

| 커뮤니티 | Δ | std | |Δ|/std | n_ctx | cond4 |
|---|---|---|---|---|---|
| THEQOO | −0.0094 | 0.0098 | ~1σ | 16 | ❌ FAIL |
| NATEPAN | −0.0167 | 0.0801 | ~0.2σ | 40 | ❌ FAIL |

**추가 발견**: run_ab_test.py 단순 프롬프트 → NATEPAN MAUVE=0.83 (random arm). 오케스트레이터 baseline MAUVE(null 기록)보다 높음 시사 → 오케스트레이터 시스템 프롬프트/컨텍스트 주입이 자연스러움을 저하시키는 신호.

**cond4 달성 경로**: 판별기 역전 해소 → M7 신선 출력 축적 후 재학습 → P(human) 방향 교정 → 재측정

---

## 함정

- Claude CLI symlink 갱신 시 subprocess FileNotFoundError → 테스트 중단 가능성. 해법: run_ab_test.py에 재시도 로직 추가 or 다른 LLM API 사용.
- `params_json TEXT(64KB)` 제약으로 40ctx 이상 제출 시 500. 해법: MEDIUMTEXT (이번에 적용).
