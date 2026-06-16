# Step 41 — R2: Encoding verification + label direction confirmation

## 일시
2026-06-17

## 결정
D-45: test_label_direction.py (WSL + Docker)로 human P(human) 방향 확정 및 오라벨 근본원인 파악

## 한 일
- test_label_direction.py 작성
  - 형식 텍스트 vs. 구어 텍스트 human 확률 비교
  - predict_proba[:,1] = P(human) 검증
- Docker 모델 테스트 (5 PASS + 1 xfail)
  - test_training_direction: PASS (human=1, AI=0 인코딩 정상)
  - test_slang_higher_than_formal: PASS (구어가 형식보다 P(human) 높음)
  - test_formal_texts_have_low_human_prob: XFAIL
    * text0="공손한 체계적 설명" P(human)=0.68 (정상)
    * text2="매우 형식적 경험담" P(human)=0.9967 (역전)
    * 원인: R1 정화 전 오라벨 코퍼스 (인젝션 가능성)

## 인코딩 확정
- train_pipeline.py:108: human=1
- discriminator.py:90-91: predict_proba[:,1]=P(human)
- 방향 일치 ✓
- 역전 = 오라벨 데이터 문제, 인코더 문제 아님

## 다음
- R1 정화 후 xfail 재테스트
- R3: 재오염 차단 가드 추가
