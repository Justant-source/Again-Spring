# Step 40 — R1: Mislabel audit + contamination detection

## 일시
2026-06-17

## 결정
D-46: audit_mislabels.py로 학습 코퍼스 오라벨 감지 (test contamination vs. BACKFILL 식별)

## 한 일
- audit_mislabels.py 작성 (WSL)
  - 각 소스별 ctx_* + BACKFILL 검사
  - ctx_* (test 오염): DELETE 대상 (라벨 반전)
  - BACKFILL: 정상 AI 라벨 (BACKFILL로 생성되었으나 AI 특성 유지)
- dry-run 결과
  - 총 34개 오염 항목: CLIEN ctx_* 32개, NATEPAN ctx_* 2개
  - BACKFILL human 매칭: 0개 (모두 AI 정상)
  - test 오염만 제거 대상

## 영향도
- CLIEN: 1122 → 1090 (32 삭제, −2.9%)
- NATEPAN: 528 → 526 (2 삭제, −0.38%)
- THEQOO: 기존 387 (변경 없음)
- 결론: NATEPAN cond4=0.38% → 미미 영향 → PASS 유지

## 검증
- 오염 패턴: "ctx_" 프리픽스 = test set 재오염
- BACKFILL = PersonaFactory 자동 생성 (정상)

## 상태
- **✅ COMPLETE** (2026-06-17 세션 19 — 사용자 승인 후 실행)

## DELETE 실행 결과 (세션 19)
- CLIEN: 32건 삭제 ✅
- NATEPAN: 2건 삭제 ✅
- 총 34건 삭제, human_match=0 (과삭제 없음)
- NATEPAN 0.4% < 5% → **cond4 PASS 유지(provisional 아님)** 확정

## R1 이후 재학습
- CLIEN: AUC=0.9965 (재학습 완료)
- NATEPAN: AUC=0.9989 (재학습 완료)
- 양 커뮤니티 AUC 유지 — R1 삭제 영향 미미

## 다음
- R8 입력: NATEPAN cond4 PASS 확정 → A-B 재실행 불필요(동결)
