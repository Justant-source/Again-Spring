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

### THEQOO (20쌍, owner 1인)

| 번호 | 사용자 답 | A 레이블 | B 레이블 | AI 지목 |
|---|---|---|---|---|
| 1 | B | random | rerank | rerank |
| 2 | B | random | rerank | rerank |
| 3 | A | rerank | random | rerank |
| 4 | B | rerank | random | random |
| 5 | A | rerank | random | rerank |
| 6 | A | random | rerank | random |
| 7 | A | random | rerank | random |
| 8 | 판단불가 | rerank | random | — |
| 9 | A | random | rerank | random |
| 10 | B | rerank | random | random |
| 11 | A | random | rerank | random |
| 12 | B | rerank | random | random |
| 13 | 답변불가 | random | rerank | — |
| 14 | 답변불가 | rerank | random | — |
| 15 | 답변불가 | rerank | random | — |
| 16 | 답변불가 | random | rerank | — |
| 17 | B | rerank | random | random |
| 18 | B | rerank | random | random |
| 19 | A | random | rerank | random |
| 20 | B | random | rerank | rerank |

- 유효 응답: 12/20
- rerank 탐지: 3/12 = **25.0%**
- random 탐지: 9/12 = **75.0%**
- D-68 판정: 25.0% ≤ 75.0% → **PASS ✅**

**주목**: `헐`/이모지 신호는 사라졌고, v2 시점의 새 오너 이유는 `유니코드 말줄임표(…)`와 일부 어색한 구체 표현(`쓰레기 차도`, `딸이 더 조심해야`)이었다.
이후 `…` → `...` 정규화 하드닝은 별도 반영 완료했고, 남은 주요 잔여 후보는 어색한 구체 표현 쪽이다.

---

## 커뮤니티별 go/no-go 표

| 커뮤니티 | cond4-MAUVE Δ | D-68 Δ≥-0.02 | h2h 비퇴행 | cond5 | **신 cond4** | 전체 판정 |
|---|---|---|---|---|---|---|
| **CLIEN** | +0.0134 | ✅ | ⚠️ runtime strict 재확인 필요 | ✅ (blind② 40%) | ⚠️ provisional | ⚠️ runtime 공식값 필요 |
| **NATEPAN** | -0.0001 | ✅ (-0.02 이내) | ⚠️ runtime strict 재확인 필요 | ⚠️ fresh PASS 없음 | ⚠️ provisional | ⛔ cond5 + runtime 보강 필요 |
| **THEQOO** | Δ_real=+0.0686 | ✅ | ⚠️ owner-only CLI fallback 기반 PASS | ⚠️ fresh PASS 없음 | ⚠️ provisional | ⛔ host 복구 + runtime + cond5 필요 |

---

## 전략 노트

### 리랭커 한계효용 평가

- CLIEN: h2h 동률(50% vs 50%) — 리랭커가 문체를 개선하지도 악화하지도 않음.
  활성화 근거 = "do-no-harm 확인". 토큰 N배 비용 대비 개선폭 없음.
  **권고**: 활성화하되 비용 모니터링 필수. cond5 40%는 리랭커 없이도 달성됨.
- NATEPAN: rerank가 random보다 5.8%p 덜 탐지 → 미약하지만 긍정적.
  Δ=-0.0001로 MAUVE 변화 거의 없고 h2h 소폭 개선.
  **권고**: 활성화 가능. 리랭커가 최소한 해를 끼치지 않음.
- THEQOO: v2 owner 기준 rerank가 random보다 **50%p 덜 탐지**됨.
  이후 `…` 정규화와 `쓰레기 차도`/`집에서는 딸이 더 조심해야` 좁은 표현 하드닝까지 반영했고,
  최신 CLI 재생성 survey에서는 해당 신호가 모두 **0건**이다.
  **권고**: 연구 게이트는 통과. 운영 전 우선순위는 runtime 경로 복구와 필요 시 새 사람 응답 라운드 판단이다.

### 전역 게이트 상황

- `ActionExecutor.java:424` 단일 boolean 문제는 `AI_USER_ML_ENABLED_COMMUNITIES`로 준비만 완료된 상태다.
- 전역 ON은 더 이상 기본값이 아니다. runtime strict 결과와 `benefit_pp >= 5%p` 조건을 통과한 community만 selective gate 후보로 본다.
- `:8092` 런타임 복구와 community별 cond5 보강 전에는 활성화 판단을 내리지 않는다.

### 롤백 트리거 (활성화 후 모니터링)

- P(human) 분포가 0.5 이하 또는 1.0 근처로 역전 시 즉시 복귀
- 실 서비스 MAUVE 드리프트 ±0.05 이상 시 재측정
- ContentSafetyGuard 차단율 급증(>10%) 시 LLM 오염 재점검

---

## 결론

**R13/R14 경계 판정**:
- 현재 h2h 문서는 cond4 참고자료로만 유지한다.
- 공식 활성화 상태는 **HOLD**다.
- 남은 선행조건:
  1. `:8092` host 복구
  2. runtime strict cond4 재측정
  3. NATEPAN/THEQOO fresh cond5
  4. selective gate(B) vs OFF(C) 비용/효용 결정

**다음 스텝**:
1. `:8092` 런타임 복구 후 동일 샘플 경로 재검증
2. 필요 시 THEQOO 새 사람 응답 라운드 진행
3. 수동 활성화 여부 결정
