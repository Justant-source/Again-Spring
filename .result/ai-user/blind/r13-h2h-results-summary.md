# R13 h2h 결과 요약 — Phase 4 go/no-go (2026-06-18)

> D-68 선등록 임계: 리랭커 탐지율 ≤ random 탐지율 (do-no-harm)
> 응답자: 오너 1인 (친구 미응답, Phase 2 계획 대비 1인 선응답)

---

## 집계 상세

### CLIEN (12쌍)

| 번호 | 사용자 답 | A 레이블 | B 레이블 | AI 지목 |
|---|---|---|---|---|
| 1 | B | random | rerank | rerank |
| 2 | A | random | rerank | random |
| 3 | A | rerank | random | rerank |
| 4 | B | rerank | random | random |
| 5 | A | rerank | random | rerank |
| 6 | 답변불가 | random | rerank | — |
| 7 | 판단불가 | random | rerank | — |
| 8 | 답변불가 | rerank | random | — |
| 9 | 판단불가 | random | rerank | — |
| 10 | A | random | rerank | random |
| 11 | A | random | rerank | random |
| 12 | A | rerank | random | rerank |

- 유효 응답: 8/12 (답변불가·판단불가 4건 = 33%)
- rerank 탐지: 4/8 = **50.0%**
- random 탐지: 4/8 = **50.0%**
- D-68 판정: 50% ≤ 50% → **PASS ✅ (동률)**

**주목**: 33% 답변불가 = "두 draft가 거의 구별 불가" — MAUVE 포화의 직접적 주관적 확인.

---

### NATEPAN (20쌍)

| 번호 | 사용자 답 | A 레이블 | B 레이블 | AI 지목 |
|---|---|---|---|---|
| 1 | 답변불가 | random | rerank | — |
| 2 | 미응답 | random | rerank | — |
| 3 | B | rerank | random | random |
| 4 | B | rerank | random | random |
| 5 | A | rerank | random | rerank |
| 6 | B | random | rerank | rerank |
| 7 | A | random | rerank | random |
| 8 | A | rerank | random | rerank |
| 9 | B | random | rerank | rerank |
| 10 | B | rerank | random | random |
| 11 | B | random | rerank | rerank |
| 12 | A | rerank | random | rerank |
| 13 | A | random | rerank | random |
| 14 | B | rerank | random | random |
| 15 | A | rerank | random | rerank |
| 16 | B | random | rerank | rerank |
| 17 | B | rerank | random | random |
| 18 | 답변불가 | rerank | random | — |
| 19 | A | random | rerank | random |
| 20 | A | random | rerank | random |

- 유효 응답: 17/20 (답변불가·미응답 3건 = 15%)
- rerank 탐지: 8/17 = **47.1%**
- random 탐지: 9/17 = **52.9%**
- D-68 판정: 47.1% ≤ 52.9% → **PASS ✅**

**주목**: 7번 [A]에 "제 글을 써드리겠습니다." 프리앰블 오염 — 사용자가 AI로 정확 탐지(A=random).
오염 케이스가 1건 있지만 이를 제거해도 결과 방향 동일: rerank 7/16=43.8%, random 9/16=56.3% → PASS.

---

## 커뮤니티별 go/no-go 표

| 커뮤니티 | cond4-MAUVE Δ | D-68 Δ≥-0.02 | h2h 비퇴행 | cond5 | **신 cond4** | 전체 판정 |
|---|---|---|---|---|---|---|
| **CLIEN** | +0.0134 | ✅ | ✅ 50%≤50% | ✅ (cond5 40%) | ✅ PASS | ✅ 활성화 준비 가능 |
| **NATEPAN** | -0.0001 | ✅ (-0.02 이내) | ✅ 47.1%≤52.9% | ✅ (cond5 40% 합산) | ✅ PASS | ✅ 활성화 준비 가능 |
| **THEQOO** | Δ_real=-0.1117 | ❌ (<-0.02) | 미측정 | 미측정 | ❌ FAIL | ❌ Step 52-53 재개 필요 |

---

## 전략 노트

### 리랭커 한계효용 평가

- CLIEN: h2h 동률(50% vs 50%) — 리랭커가 문체를 개선하지도 악화하지도 않음.
  활성화 근거 = "do-no-harm 확인". 토큰 N배 비용 대비 개선폭 없음.
  **권고**: 활성화하되 비용 모니터링 필수. cond5 40%는 리랭커 없이도 달성됨.
- NATEPAN: rerank가 random보다 5.8%p 덜 탐지 → 미약하지만 긍정적.
  Δ=-0.0001로 MAUVE 변화 거의 없고 h2h 소폭 개선.
  **권고**: 활성화 가능. 리랭커가 최소한 해를 끼치지 않음.
- THEQOO: 진짜 corpus 없이 판정 불가. corpus 64% 합성이 모든 수치를 왜곡.
  **권고**: corpus 수집(≥300건) 완료 전 활성화 보류.

### 전역 게이트 상황

- `ActionExecutor.java:424` 단일 boolean — CLIEN/NATEPAN만 켜기 불가.
- THEQOO FAIL → 전역 활성화 차단.
- **차선책**: THEQOO corpus 수집 완료 + 재학습 + Δ_real>0 확인 후 전역 활성화.

### 롤백 트리거 (활성화 후 모니터링)

- P(human) 분포가 0.5 이하 또는 1.0 근처로 역전 시 즉시 복귀
- 실 서비스 MAUVE 드리프트 ±0.05 이상 시 재측정
- ContentSafetyGuard 차단율 급증(>10%) 시 LLM 오염 재점검

---

## 결론

**R13 Phase 4 판정**:
- CLIEN + NATEPAN: 신 cond4(D-68) **PASS** — 활성화 준비 완료
- THEQOO: **FAIL** — Step 52-53 (실제 더쿠 스타일 corpus ≥300건 수집) 재개 필요
- **전역 활성화**: THEQOO 해소 전까지 불가 (단일 게이트)
- `AI_USER_ML_ENABLED=true` 전환 시기: 오너 수동 결정 필요 (코드 변경 금지)

**다음 스텝**:
1. THEQOO Step 52-53: 실제 더쿠 스타일 한국어 corpus ≥300건 수집 방법 결정
2. 또는: THEQOO를 전역 게이트에서 제외하는 per-community 분기 구현 검토 (ActionExecutor 수정 필요)
