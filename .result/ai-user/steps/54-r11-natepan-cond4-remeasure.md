# Step 54 — R11: NATEPAN cond4 최신 모델 재측정

## 일시
2026-06-18 (세션 27)

## 결정
D-67: P4(동결 M1 Δ=+0.1667, model v37) → 최신 모델로 재확인

## 방법
- `run_ab_test.py --community NATEPAN --n-contexts 40` (원 M1 n_ctx=40 동일)
- K=3 고정 시드 [42,137,2026] 서버 하드코딩
- NATEPAN human corpus: 100% source='natepan', 오염 0

## 결과

| 지표 | 현재 측정값 | M1 동결값 | 변화 |
|---|---|---|---|
| n_contexts | 40 | 40 | — |
| snapshot_size (n_human) | 469 | — | — |
| mauve_rerank | **0.3442** | 0.8590 | ↓↓ |
| mauve_random_mean | 0.6343 | 0.6923 | ↓소폭 |
| mauve_random_std | 0.0503 | 0.1257 | — |
| mauve_random_seeds | [0.661, 0.564, 0.678] | — | — |
| **delta** | **-0.2901** | **+0.1667** | 🔴 역전 |
| degraded | false | — | — |
| 판정 | ❌ FAIL | PASS | 퇴행 |

## 판정

**NATEPAN cond4 역전 (cond4 FAIL)**:
- 동결 M1 Δ=+0.1667 → 최신 모델 재측정 Δ=**-0.2901**
- rerank=0.3442 < random_mean=0.6343 → 리랭커가 인간 분포에서 더 멀리 떨어진 초안 선택 (역방향)
- P(human) 방향 역전: 판별기가 현재 AI 출력에서 인간다운 초안을 제대로 고르지 못함
- M1 이후 모델/프롬프트 변화로 판별기 적응 필요

**결론**: NATEPAN 리랭커 활성화 시 인간다움 악화 위험. 전역 ON 보류.

## 상태
- ✅ 완료 (2026-06-18)
- **cond4 FAIL** — 동결 M1 값이 최신 모델에서 재현되지 않음
