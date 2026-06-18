# Step 56 — R11: 활성화 go/no-go 판정 + 모니터링/롤백 런북

## 일시
2026-06-18 (세션 27)

## 결정
D-67: R11 전 커뮤니티 cond4 재측정 완료. 전역 ON 권고 여부 확정.

## go/no-go 표

| 커뮤니티 | cond4 delta | cond4 판정 | cond5 blind | cond5 판정 | 전체 |
|---|---|---|---|---|---|
| CLIEN | +0.3371 (신선, 최신 모델) | ✅ PASS | 40% (blind②) | ✅ PASS | READY |
| NATEPAN | **-0.2901** (Phase3 재측정) | ❌ **FAIL** | M5 82.5% (2026-06-16) | ❌ FAIL | BLOCK |
| THEQOO | +0.0417 (Phase1b, marginal) | ⚠️ provisional | blind 미실시 | — | PENDING |

## 결론: NO GO ❌

**블로킹 사유**: NATEPAN cond4 **역전** (Δ=-0.2901)
- 리랭커 활성화 시 NATEPAN에서 인간다움이 악화(랜덤 선택보다 나쁜 초안 선택)
- 전역 게이트(`ActionExecutor.java:425`)이므로 NATEPAN을 분리 불가 — 전역 ON = NO GO

**THEQOO 부가 발견**:
- Phase 1b: delta=+0.0417 (기술적 PASS, 그러나 D-66 +0.4458 대비 10배 퇴행)
- 원인: AI 모델 출력 품질 향상으로 랜덤 선택도 인간다워짐 (mauve_random: 0.5316→0.9357)
- THEQOO 단독으로는 provisional PASS — NATEPAN이 블로킹

**STATE.md 5조건 재판정**:
- cond4 NATEPAN: ❌ FAIL (STATE.md의 "✅ PASS(동결M1)" 철회)
- cond4 THEQOO: ⚠️ provisional PASS (D-66 Δ=+0.4458 → R11 +0.0417로 퇴행 확인)
- 전체: 5조건 미충족 — AI_USER_ML_ENABLED 활성화 불가

## cond4 역전 원인 분석

**NATEPAN (-0.2901)**:
- rerank=0.3442, random_mean=0.6343 → 리랭커가 MAUVE 측면에서 더 나쁜 초안 선택
- 가설: M1(세션 17, model v37) 이후 AI 출력 스타일 변화(N6 writing_quirks 등) → 판별기 미적응
- 판별기 학습 당시 "격식체/오류 없는 = AI"가 현재는 AI도 비격식체 생성 → 역전

**THEQOO (퇴행 +0.4458→+0.0417)**:
- AI 출력 품질 향상의 역설: Haiku가 더 인간다운 텍스트 생성 → random 선택도 고품질 → 마진↓
- 좋은 신호 (AI가 더 인간다워짐) + 나쁜 신호 (리랭커 차별화 능력 감소)

## R12 권고사항

1. **NATEPAN 판별기 재학습 (최우선)**: 최신 AI 출력 corpus(N6+R7 결과)로 재학습
   - 목표: Δ>0 AND std<Δ 재달성
   - 방법: ai-user corpus(NATEPAN ai, 최신 `SELF_GENERATED`) 추가 + /train 재실행

2. **THEQOO cond4 모니터링**: Δ=+0.0417 → 재학습 후 개선 여부 확인

3. **per-community 게이트 도입 검토 (선택)**: CLIEN은 즉시 준비됨. `ActionExecutor.java:425` + `AiUserMlClient.java:139` 수정으로 per-community enable-list 지원 가능 → CLIEN만 먼저 활성화 가능. 별도 사용자 승인 필요.

4. **cond5 blind (Phase 2) 상태**: NATEPAN cond4 FAIL이 blocking이므로 cond5는 R12로 이월. 판별기 재학습 후 실시.

## 모니터링/롤백 런북 (사람이 향후 켤 때용)

```
전제 조건: 이 런북은 R12 판별기 재학습 후 cond4가 재확인된 시점에만 사용.
```

켜기 전 체크리스트:
- [ ] NATEPAN cond4 재측정: Δ>0 AND std<Δ
- [ ] THEQOO cond4 재측정: Δ>0 (개선 확인)
- [ ] 각 커뮤니티 blind② ≤60% (cond5)
- [ ] .env.dev에서 `AI_USER_ML_ENABLED=true` 수동 입력

켠 직후 모니터링 (N틱, 최소 24h):
1. **MAUVE 드리프트**: `eval/baseline` 주기 실행 → MAUVE → 1.0 방향이면 정상, ↓이면 즉시 복귀
2. **AUC 드리프트**: discriminator AUC → 0.5 방향이면 정상 (AI = human 구분 불가 = 성공), ↑이면 이상
3. **본문 육안 점검**: N틱 커뮤니티별 5개 샘플 → 판결/처방/승패 표현 0 확인 (관점교정 가드)
4. **역전 감지**: `eval/ab-test` delta < 0이면 즉시 복귀

롤백:
```bash
# .env.dev 수정
AI_USER_ML_ENABLED=false  # (또는 주석 처리)
# orchestrator 재시작
cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --force-recreate ai-user-orchestrator
```

## 불변 확인
- `AI_USER_ML_ENABLED=false` ✅ (env/`.env.dev` 확인)
- `AI_USER_ENABLED=false` ✅ (Phase 1/3 측정 후 복원 확인)
- `ActionExecutor.java:425` 게이트 미변경 ✅
- `.env.prod` git 커밋 금지 ✅

## 상태
- ✅ 완료 (2026-06-18)
- **전역 활성화: NO GO** — NATEPAN cond4 FAIL 해소 전까지
