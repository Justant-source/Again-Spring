# Step 30 (M4) — ablation 테이블 추출 및 CV 표준편차 실측 (2026-06-16)

## 상태: ✅ 완료 (M3 재학습 모델 기준 CV ablation 테이블 산출)

---

## 목표

M3 재학습 후 eval_run(kind=cv) 기록에서 5-fold cross-validation 상세 수치를 추출 → 피처셋 ablation 결론 도출.

---

## 결과: Ablation 테이블 (M3 재학습 모델)

| 커뮤니티 | CV mean | CV std | best_C | katfish_9(C=1) | electra_768(C=1) | combined_777(C=1) |
|---|---|---|---|---|---|
| THEQOO | 0.9986 | 0.00275 | 1.0 | 0.9682±0.0191 | 0.9985±0.00272 | 0.9986±0.00275 |
| CLIEN | 0.9947 | 0.0095 | 1.0 | 0.892±0.0179 | 0.9947±0.00997 | 0.9947±0.00951 |
| NATEPAN | 0.9994 | 0.00086 | 1.0 | 0.809±0.030 | 0.9996±0.00066 | 0.9994±0.00086 |

---

## 피처셋 분석

### 결론

1. **electra_768 압도적 우수**: KatFishNet-9(어휘 기반) 단독 대비 THEQOO 0.9682→0.9985, CLIEN 0.892→0.9947 상승
2. **combined_777 ≈ electra_768**: 스태킹(combined) 했으나 electra_768 단독과 거의 동일 성능 → 어휘 피처의 추가 기여도 미미
3. **결정**: 운영상 복잡도(dual model 로딩) vs 성능(무시미한 상승) 고려 시, **electra_768 단독 배포 고려 가능**

---

## 해설: n_val=0 이유

`cross_val_score`는 학습 과정에서 holdout validation set을 별도로 생성하지 않음 — sklearn 5-fold split는 train/test만 분리. placeholder 행.

---

## 함정

- **높은 AUC 자체가 신뢰도를 의미하지 않음**: AUC 0.998~0.999 = "판별기가 과신뢰, 매우 쉽게 구별 가능" → 프로젝트 목표(AUC→0.5)와 거리 멀음
- **ablation 결론이 최종이 아님**: M5 블라인드 평가에서 사람 정확도가 0.60 이하여야 "피처 정규화 필요" 판정 가능
- **katfish_9 완전 제거 검토 예정**: electra_768 단독 재학습으로 성능 동등 확인 후 배포 결정 필요

---

## 다음 단계

- **M5**: M7 신선 출력 축적 후 사람 블라인드 평가 실행 (정확도 목표: ≤0.60)
- **M6**: COMMENT MAUVE before/after N6(allowChosung) 측정
- **cond4 경로**: M7 신선 출력 재학습 → P(human) 방향 교정 → A-B 재측정
