# Step 11 (T2) — 신뢰 가능한 AUC (CV 5-fold + INSUFFICIENT_DATA 게이트)

**완료일**: 2026-06-16
**담당**: Base Hardening Phase A

## 문제

train_pipeline.py:120-132 — 실제 AI POST 부족 시 human 텍스트 복제하여 label=0 음성 위조.
단일 split + 소표본 누수. 결과: DCINSIDE AUC=1.000(과적합), NATEPAN AUC=0.562(위조 기반).

## 수정

(a) INSUFFICIENT_DATA 게이트: POST 실제 n_ai<100 OR n_human<300 → 학습 스킵.
(b) Stratified 5-fold CV: CV mean±std. ModelVersion.auc = CV mean. EvalRun(kind="cv").metrics_json = std/ablation/C.
(c) 피처 ablation + C 선택: KatFishNet-9 / KcELECTRA-768 / 777결합, C in {0.01,0.1,1.0}.
(d) config.py: retrain_min_ai 30→100, retrain_auc_target 0.55→0.75.

## 검증 수치

모든 커뮤니티 INSUFFICIENT_DATA (예상 정상):

| 커뮤니티 | n_ai(POST) | n_human(POST) | skip_reason |
|---|---|---|---|
| DCINSIDE | 20 | 39 | n_ai=20<100 |
| NATEPAN | 0 | 443 | n_ai=0<100 |
| THEQOO | 65 | 332 | n_ai=65<100 |
| CLIEN | 40 | 286 | n_ai=40<100 |

합성 위조 경로 완전 제거. 단일 1.000 소멸.
CV AUC는 n_ai>=100 달성 후 첫 학습 시 자동 산출 예정.

## 완료 기준

- [x] 합성 음성 위조 경로 제거
- [x] INSUFFICIENT_DATA 게이트 작동
- [x] 단일 split 1.000 소멸
- [x] 70/70 pytest 통과
