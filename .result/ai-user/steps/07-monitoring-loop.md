# Step 7 완료 기록 — 주기 갱신 + 모니터링

**날짜**: 2026-06-16  
**세션**: 5  
**상태**: ✅ 완료 (65/65 pytest + WSL commit 5b53372)

---

## 한 일

### 신규 파일 (WSL `~/Data/Again-Spring-AI-User`)

| 파일 | 역할 |
|---|---|
| `app/worker/retrain_loop.py` | 6h 주기 자동 재학습 루프 |
| `app/worker/eval_loop.py` | 24h 주기 자동 eval 루프 |
| `app/api/routes_metrics.py` | `/metrics/readiness` + `/metrics/history` |
| `tests/test_metrics.py` | 메트릭 엔드포인트 5개 테스트 |
| `tests/test_loops.py` | 루프 설정·임계 로직 4개 테스트 |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `app/config.py` | 6종 신규 설정 (retrain/eval 스케줄) |
| `app/main.py` | lifespan에 retrain_loop + eval_loop 태스크 시작, metrics_router 등록 |

---

## 아키텍처 (Step 7 이후 전체 루프)

```
FastAPI lifespan startup:
  ├─ corpus_pull_loop  (10분 주기) ─────────────────────────────────────────────
  │    AS learning GET /examples/export → POST /corpus/ingest (human label)     │
  │    AS ActionExecutor → POST /corpus/ingest (ai label, Step 5)                │
  │                                                                               ▼
  ├─ retrain_check_loop (6시간 주기) ──────────── corpus_item (n_ai 카운트)     DB
  │    n_ai >= 30/커뮤니티 AND AUC < 0.55?                                       ▲
  │    YES → POST /train (auto-retrain-YYYY-MM-DD idempotency)                   │
  │    NO  → skip                                                                 │
  │    AUC >= 0.55 → logger.info("🎯 READINESS: ...") → 운영자 모니터            │
  │                                                                               │
  └─ eval_periodic_loop (24시간 주기) ──────────────────────────────────────────┘
       POST /eval/baseline (auto-eval-YYYY-MM-DD idempotency)
       → eval_run 테이블에 시계열 저장 → GET /metrics/history로 조회
```

---

## 신규 엔드포인트

### GET /metrics/readiness
```json
{
  "communities": {
    "NATEPAN": {
      "n_human": 1557, "n_ai": 0,
      "latest_auc": 0.319, "auc_target": 0.55,
      "min_ai_needed": 30, "ready": false
    }
  },
  "summary": { "ready_count": 0, "total_count": 4 }
}
```

### GET /metrics/history?community=NATEPAN&limit=100
```json
{
  "runs": [
    { "community": "NATEPAN", "kind": "baseline",
      "metrics": { "human_comma_rate": 0.0113, ... },
      "n_human": 396, "n_ai": 0, "created_at": "2026-06-15T15:04:24" }
  ],
  "total": 1
}
```

---

## 신규 설정 (config.py)

| 환경변수 | 기본값 | 의미 |
|---|---|---|
| `RETRAIN_ENABLED` | `true` | 자동 재학습 루프 활성화 |
| `RETRAIN_CHECK_INTERVAL_SEC` | `21600` | 체크 주기 (6시간) |
| `RETRAIN_MIN_AI_PER_COMMUNITY` | `30` | 재학습 트리거 n_ai 임계 |
| `RETRAIN_AUC_TARGET` | `0.55` | AI_USER_ML_ENABLED 활성화 목표 AUC |
| `EVAL_PERIODIC_ENABLED` | `true` | 주기 eval 루프 활성화 |
| `EVAL_PERIODIC_INTERVAL_SEC` | `86400` | eval 주기 (24시간) |

---

## 현재 상태 (2026-06-16)

```
/metrics/readiness 결과:
  CLIEN:   n_ai=0, AUC=0.304 → NOT READY
  DCINSIDE: n_ai=0, AUC=0.429 → NOT READY
  NATEPAN: n_ai=0, AUC=0.319 → NOT READY
  THEQOO:  n_ai=0, AUC=0.200 → NOT READY
```

**AUC 낮은 이유**: synthetic negative (human texts를 ai 라벨로) 사용 → 반사 학습.  
**n_ai=0 이유**: prod AI_USER_ML_ENABLED=false 상태 → ActionExecutor.pushNegative()가 /corpus/ingest로 실제 AI 텍스트를 아직 미전송.

**다음 조건 충족 시 자동 처리:**
1. n_ai >= 30/커뮤니티 → retrain_loop이 자동 /train 트리거
2. 재학습 후 AUC >= 0.55 → 🎯 READINESS 로그 출력
3. 운영자가 수동으로 `AI_USER_ML_ENABLED=true` 활성화

---

## 검증 기록

```bash
# pytest
docker exec again-spring-ai-user-aiuser-ml-1 python -m pytest tests/ -q
→ 65 passed (56 기존 + 9 신규: 5 test_metrics + 4 test_loops)

# 라이브 서비스 확인
curl -H 'Authorization: Bearer aiuser-ml-api-token-dev-2026' \
     http://localhost:8201/metrics/readiness
→ 200 OK (4개 커뮤니티 데이터)

# 컨테이너 로그
retrain_check_loop started (interval=21600s, min_ai=30, auc_target=0.55) ✅
eval_periodic_loop started (interval=86400s) ✅
corpus_pull_loop started ✅

# WSL commit
git commit 5b53372
```

---

## 전체 Step 0-7 완료 — 프로젝트 완성

| Step | 내용 | 상태 |
|---|---|---|
| 0 | 기록+스캐폴드 | ✅ |
| 1 | KatFishNet 피처+POS | ✅ |
| 2 | 코퍼스 파이프라인 | ✅ |
| 3 | 평가 하네스+베이스라인 | ✅ |
| 4 | 판별기 학습+스코어 | ✅ |
| 5 | AS Best-of-N 와이어링 | ✅ |
| 6 | 분포 매칭 개편 | ✅ |
| 7 | 주기 갱신+모니터링 | ✅ |

**최종 활성화 절차** (미래):
1. `GET /metrics/readiness` 모니터링
2. AUC >= 0.55 + n_ai >= 30 확인
3. prod `AI_USER_ML_ENABLED=true` 설정
4. `AI_USER_ML_BEST_OF_N=4` 확인 (기본값)
5. 배포 후 ActionExecutor 로그에서 "rerank winner" 메시지 확인
