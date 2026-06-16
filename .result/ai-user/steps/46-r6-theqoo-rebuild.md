# Step 46 — R6: THEQOO corpus 재구축 (ai=0 → ≥100)

## 일시
2026-06-17 (세션 19)

## 결정
D-53: P0 완료 후 THEQOO 봇 POST 자연 틱 + 수동 트리거로 ai corpus 재구축 시작.

## 배경
- 세션 17에서 THEQOO ai corpus 541건 전량 삭제 (사용자 승인) — R3 소스 가드 미안착
- R3 가드 안착 (P0 완료) 후 재수집 시작
- 현재 THEQOO: human=376, **ai=0** → R6 목표: ai≥100

## 한 일

### Trigger (R6 수집 시작)
1. `generate-posts?voice=THEQOO&count=10` → attempted=7 (16:16)
2. `generate-posts?voice=THEQOO&count=20` → attempted=7 (16:26)

### 현재 corpus 상태 (트리거 후 확인)
```
THEQOO ai POST: 0 → 7 (첫 트리거 결과 확인)
THEQOO ai POST: 7 → (두 번째 트리거 후 확인 필요)
```
2026-06-16 16:25~로 +1 inserted 로그 관찰 (THEQOO 트리거에서)

## 다음 (Wait → Measure)
1. **Wait**: `/corpus/stats` 확인 — THEQOO n_ai ≥ 100 달성 시
2. **추가 트리거**: 자연 틱으로 부족하면 generate-posts?voice=THEQOO 추가 실행
3. **Measure (n_ai 충족 후)**:
   ```bash
   TOKEN="aiuser-ml-api-token-dev-2026"
   # 재학습
   curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"communities": ["THEQOO"], "idempotencyKey": "r6-theqoo-train-v1"}' \
     http://100.115.252.61:8201/train
   ```
4. 학습 완료 후 P(human) 스팟체크
   - 슬랭 서사 `"어제 남친이...ㅠㅠ"` → P(human) 高 기대
   - 격식 AI 텍스트 → P(human) 低 기대

## 현재 부족분
- 현재 n_ai ≈ 7 (트리거 1~2회)
- 목표 100건까지 93건 이상 추가 필요
- THEQOO 페르소나 7명 → 매 틱 최대 7건씩 → 14회 틱 이상 필요
- 자연 틱 + 추가 트리거 병행

## 상태
- **Trigger**: ✅ 2회 실행 (n_ai 7건 확인)
- **Wait**: ⏳ n_ai ≥ 100 목표
- **Train**: 🔜 n_ai 충족 후
- **Measure (P(human))**: 🔜 학습 후
