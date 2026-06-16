# Step 35 (M1 재측정 2) — NATEPAN A-B 2차 (세션 16, 2026-06-16)

## 결과

| 항목 | 값 |
|---|---|
| n_contexts | 40 |
| mauve_rerank | 0.8437 |
| mauve_random_mean | 0.9529 |
| delta | −0.1092 |
| mauve_random_std | 0.0428 |
| seeds | [42, 137, 2026] → [0.9803, 0.8924, 0.9860] |
| cond4 | FAIL |

## 주목할 점

- **random_mean=0.9529 (매우 높음)** — NATEPAN AI 초안이 이미 인간 분포와 매우 유사
  - NATEPAN POST MAUVE baseline=0.8395와 비교 시 random 초안들이 오히려 더 높음
  - 씨드별 변동: 0.9803/0.8924/0.9860 (범위 0.094 — 소표본 MAUVE 불안정성)
- **rerank=0.8437 < random=0.9529** — 리랭커가 초안 중 "가장 AI다운 것"을 선택
  - 판별기가 여전히 "격식체=human"으로 오판 → 가장 격식적인 초안 선택 → MAUVE 저하

## P(human) 현황 (재학습 후)
- 슬랭 서사: P(human)=0.9999937 ✓ (올바른 방향)
- 격식 상담사: P(human)=0.9180889 ⚠️ (여전히 0.9+ — 목표는 <0.5)
- 역전 완전 해소 아님 (슬랭>격식 방향은 맞으나 격차 불충분)

## 함정

- THEQOO 재학습 실패: CUDA device mismatch (cpu ↔ cuda:0). 수정 중.
- random MAUVE 씨드 간 변동 큼(0.09) — MAUVE 자체 불안정성 반영. 소표본의 한계.

## 다음
- THEQOO CUDA 수정 → 재학습 → P(human) 재체크
- M6 댓글 길이 제약 배포 → COMMENT MAUVE 재측정
- M7 신선 출력 더 축적 (admin trigger 30건+) → 재학습 → P(human) 개선 기대
- cond4는 P(human) 역전 해소 후 재측정
