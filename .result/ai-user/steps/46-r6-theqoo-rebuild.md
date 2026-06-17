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

---

## R6 학습 결과 (세션 20)

### 완료 수치
| 항목 | 값 |
|---|---|
| n_ai | 100 (목표 달성) |
| n_human | 393 |
| CV-AUC | **1.000 ± 0.001** (bestC=1.0) |
| 학습 완료 | job 01KV9DMSNC971TQXRMEEM0M44Q |

### P(human) 방향 스팟체크 ❌ HALT
| 텍스트 | P(human) | 기대 방향 | 실제 |
|---|---|---|---|
| 슬랭 "어제 남친이 제 친구한테 연락했다는 거 알고ㅠㅠ 진짜 너무하는 거 아님?" | **0.0009** | HIGH | ❌ LOW |
| 격식 "해당 상황에 대한 다양한 관점을 종합적으로 분석하면..." | **0.9801** | LOW | ❌ HIGH |
| AI 내러티브 "저도 예전에 비슷한 상황이 있었는데요. 파트너가 저의 친한..." | **0.9281** | LOW | ❌ HIGH |

### 판정: HALT (계획 §R6 halt 조건 충족)
**방향 역전 지속 — 슬랭=AI, 격식=Human으로 학습됨**

**근본원인 가설**: 
- Human corpus(AS 학습 API 출처): Again Spring 플랫폼 사용자 글 = 구조적 갈등 내러티브 (formal)
- AI corpus: THEQOO voice(T8 features — 더쿠 슬랭, ㅠㅠ, ㅋㅋ) → informal/slang 생성
- 결과: classifier가 "슬랭=AI, 격식=Human" 역방향 학습

**THEQOO cond4 측정 보류** (역전 상태에서 MAUVE delta 무의미)
