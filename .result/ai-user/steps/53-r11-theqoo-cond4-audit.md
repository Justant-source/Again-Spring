# Step 53 — R11: THEQOO cond4 타당성 감사

## 일시
2026-06-18 (세션 27)

## 결정
D-67: THEQOO cond4 provisional 여부 확인 — delta_real(진짜 더쿠 111) vs delta_synth(합성 200)

## 방법
- 초안: THEQOO 12 contexts × 4 drafts (Claude CLI, 8 workers)
- 스코어: /score 엔드포인트 (ML:8201)
- MAUVE: gpt2, device_id=0, K=3 seeds [42,137,2026], ML container
- 참조 분리: source='theqoo'(111) vs source='SYNTHETIC_THEQOO_STYLE'(200)

## 결과

### Phase 1a — Sonnet CLI (실측, 포화 사례)

| 지표 | 진짜 더쿠 111 기준 | 합성 200 기준 |
|---|---|---|
| n_contexts_valid | 12 | 12 |
| mauve_rerank | 0.9773 | 0.9968 |
| mauve_random_mean | 0.9773 | 0.9968 |
| **delta** | **+0.0000** | **+0.0000** |

**근본 원인 — 판별기 포화**: 모든 48개 초안(12 ctx × 4)이 P(human) ≈ 0.998~1.000으로 포화. argmax가 사실상 무작위 → rerank_winner = random_winner (동일 분포). delta=0은 "역전"이 아니라 **discriminator saturation** (식별 불가).

**모델 혼동**: 이 테스트는 Sonnet CLI(WSL)로 생성 → 프로덕션 경로(Haiku API)보다 훨씬 인간다운 텍스트 생성 → 포화. D-66 Δ=+0.4458은 run_ab_test.py (Haiku API)로 측정 → 신뢰 가능한 비교는 Phase 1b 참조.

### Phase 1b — Haiku API (프로덕션 경로, run_ab_test.py)

| 지표 | 값 | D-66 비교 | 변화 |
|---|---|---|---|
| n_contexts | 12 | 12 | — |
| mauve_rerank | 0.9774 | 0.9774 | → 동일 |
| mauve_random_mean | 0.9357 | 0.5316 | ↑↑ 급등 |
| mauve_random_std | 0.0295 | — | — |
| mauve_random_seeds | [0.9148, 0.9774, 0.9148] | — | — |
| **delta** | **+0.0417** | **+0.4458** | ↓↓ 퇴행 |
| snapshot_size (n_human) | 311 | 311 | — |
| degraded | false | — | — |
| delta>0 | ✅ | ✅ | — |
| std<delta | ✅ (0.0295<0.042) | — | 기술적 PASS |

**핵심 발견**: rerank MAUVE는 D-66(0.9774)과 동일하지만, **random_mean이 0.5316→0.9357로 급등**. AI 모델 출력 품질이 M1 이후 크게 향상 → 랜덤 선택도 인간다워짐 → 리랭커 마진이 사라짐.

**판정**: 기술적 PASS(delta=+0.0417 > std=0.0295) — 그러나 매우 미미. D-66 Δ=+0.4458 대비 10배 퇴행. **THEQOO cond4 provisional 유지** — 재학습 시 개선 예상.

## 판정

**THEQOO cond4 Phase 1b 결과**: delta=+0.0417 (기술적 PASS, delta>std)
- Phase 1a(Sonnet): 판별기 포화(모델 혼동), 결론 불가
- Phase 1b(Haiku): 기술적 PASS이지만 D-66 대비 10배 퇴행 → provisional 유지
- 랜덤 기준선 급등(0.5316→0.9357) = AI 출력 품질 향상의 역설 (좋은 신호지만 리랭커 마진↓)

**전역 게이트 기여**: NATEPAN cond4 FAIL(-0.2901)이 전역 NO GO의 blocking factor — THEQOO 단독으로는 provisional PASS.

## 상태
- **Phase 1a**: ✅ 완료 (Sonnet CLI 포화 — 모델 혼동, 결론 불가)
- **Phase 1b**: ✅ 완료 (2026-06-18) — delta=+0.0417, provisional PASS
