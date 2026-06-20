# Step 81 — Phase 0: Closeout 시작 / D-107 의사결정 동결

**날짜**: 2026-06-21  
**단계**: Wind-Down Phase 0 — Decision Freeze  
**결정**: D-107

---

## 요약

D-106(Best-of-N reranking 전면 폐기) 이후 프로젝트를 **closeout(종료)** 모드로 전환.  
더 이상 `AI_USER_ML_ENABLED` 활성화를 향해 일하지 않는다.

## D-107 핵심 (2026-06-21)

| 항목 | 결정 |
|---|---|
| Best-of-N reranking (ML·rule) | **영구 폐기** |
| `AI_USER_ML_ENABLED` | `false` 영구 (코드 변경 금지) |
| 성공 정의 | Goal A (글 1개 = 사람 같음) = **달성**. Goal B = out-of-scope. |
| cond4 / cond5 proxy | 의사결정 근거에서 **제외** |
| 5조건 활성화 게이트 | Goal B 재개 시에만 유효 — 현재 closeout 상태에서 적용 안 함 |

## Phase 0 작업 (이 세션)

- [x] `decisions.md` — D-107 append
- [x] `STATE.md` — WIND-DOWN 상태 전환
- [x] `roadmap.md` — Step 92·93 CANCELLED / SUPERSEDED-by-closeout
- [x] `steps/81-closeout-phase0-d107.md` — 이 파일

## 출하 대상 (Phase 1에서 prod 이미지 신선도 확인)

| 레버 | 코드 위치 | 커밋 |
|---|---|---|
| Track A: `injectTypos()` T1~T8 | `OutputSanitizer.java:263–291` | `74e2b283` |
| THEQOO cleanup: `cleanupTheqoo()` | `OutputSanitizer.java:210–224` | `b783168d` (최신) |
| CASUAL 25% 분기 | `ActionExecutor.java:346` | `74e2b283` |

## 다음 Phase

- **Phase 1**: prod 이미지 신선도 감사 → (미반영 시) dev 재빌드 + e2e-realbe PASS → prod 재빌드 (명시 지시 후)
- **Phase 2**: 계정 단위 1회 휴먼 수용 검사 (dev only, pass 기준 사전 등록 필수)
- **Phase 3**: `PROJECT-CLOSEOUT.md` 작성 + ML 서비스 decommission 옵션 제시 + STATE.md = CLOSED
