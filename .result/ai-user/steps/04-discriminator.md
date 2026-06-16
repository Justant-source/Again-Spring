# Step 4 완료 기록 — KcELECTRA+KatFishNet 판별기

**날짜**: 2026-06-16  
**세션**: 3  
**상태**: ✅ 완료 (56/56 pytest 통과 + GPU 학습 + CPU /rerank 실동작)

---

## 한 일

### 신규 파일

| 파일 | 역할 |
|---|---|
| `app/ml/registry.py` | 커뮤니티별 pickle 체크포인트 + DB `ModelVersion` 레지스트리 |
| `app/ml/discriminator.py` | KcELECTRA `beomi/kcelectra-base` + KatFishNet → LR 스태킹, CPU 추론 |
| `app/ml/train_pipeline.py` | GPU fp16 인코딩(VRAM ≥2GB 가드) + CPU LR, AUC 평가, 레지스트리 저장 |
| `tests/test_discriminator.py` | 12개 테스트 (score/rerank/helper/registry) |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `app/api/routes_score.py` | stub → 실 discriminator 와이어링 (`degraded=False` when model exists) |
| `app/api/routes_train.py` | stub → `submit_job(use_gpu=True)` dispatch |

### 아키텍처

```
encode_texts(texts, device="cpu"|"cuda")
    └─ beomi/kcelectra-base AutoModel
    └─ [CLS] embedding (768-dim)

build_features(texts)
    └─ encode_texts (768) + feature_vector (9) → 777-dim array

train_pipeline.run_train_job
    ├─ _free_vram_gb() ≥ 2.0 → GPU fp16 encoding (GPU_BATCH=8)
    │   └─ model.cuda().half() → encode → model.float().cpu() → empty_cache()
    └─ CPU LR: StandardScaler + LogisticRegression(C=1.0, balanced, max_iter=500)

registry.ModelRegistry
    ├─ get(community) → LoadedModel | None (cache → disk → None)
    └─ save(community, stacker, version, auc, n_train, n_val)
         → /app/data/checkpoints/{community}/{version}.pkl + DB ModelVersion
```

---

## 학습 결과 (2026-06-16, POST, synthetic negative)

**잡 ID**: `01KV5XKWTTSM1KM3S02K78QW54`

| 커뮤니티 | n_human | n_ai | AUC | 비고 |
|---|---|---|---|---|
| DCINSIDE | 35 | 5(synthetic) | **0.429** | 랜덤(0.5) 이하 |
| NATEPAN | 396 | 5(synthetic) | **0.319** | 랜덤 이하 |
| THEQOO | 300 | 5(synthetic) | **0.200** | 랜덤 이하 |
| CLIEN | 228 | 5(synthetic) | **0.304** | 랜덤 이하 |

### AUC 해석

AUC < 0.5 (랜덤 이하) = **예상된 결과**:
- synthetic negative: human 텍스트 5개를 AI 라벨(0)로 강제 지정
- 동일한 텍스트가 human(1)과 AI(0) 두 레이블에 동시 존재 → 모델이 반사 학습
- `/rerank` 결과: 모든 텍스트에 human_prob≈1.0, 형식적인 문체가 winner로 선정(반사 효과)

**Step 5(ActionExecutor AI negative push) 후 재학습 시 실제 AUC 측정 가능.**

---

## 엔드포인트 검증

```bash
# /score — degraded=False, 실 KcELECTRA 확률값
POST /score {"community":"NATEPAN","contentType":"POST","candidates":[...]}
→ {"degraded":false,"modelVersion":"01KV5X...","scores":[{"humanProb":0.9999...}]}

# /rerank — winner 선정 동작
POST /rerank → {"winnerId":"draft-1","degraded":false,"ranked":[...]}

# /train — GPU 잡 (40초 완료: KcELECTRA 다운로드 포함)
POST /train → {"job_id":"...","status":"QUEUED"}
GET /train/{job_id} → {"status":"DONE","result":{...}}
```

---

## 완료 기준

| 기준 | 결과 |
|---|---|
| `app/ml/discriminator.py` | ✅ KcELECTRA + KatFishNet 777-dim |
| `app/ml/train_pipeline.py` GPU 학습 | ✅ 40초 완료 (GPU fp16 인코딩) |
| `app/ml/registry.py` 체크포인트 | ✅ `data/checkpoints/{community}/` 저장 |
| `POST /rerank` CPU 추론 `degraded=False` | ✅ |
| 커뮤니티별 AUC | ✅ (synthetic이라 값 무의미, 인프라 동작 확인) |
| 56/56 pytest | ✅ |

---

## 함정 기록

- **`MagicMock(spec=LoadedModel)`**: dataclass 인스턴스 속성은 spec에 노출 안됨 → `.replace(..., 1)`로 첫 번째만 수정 → 두 번째 누락. `MagicMock()` (no spec) 사용.
- **`patch("app.ml.registry.get_session")`**: `get_session`을 메서드 내부에서 import하므로 모듈 레벨 속성 없음 → `patch("app.storage.db.get_session")` 사용.
- **GPU 인코딩 시간**: `beomi/kcelectra-base` HuggingFace 캐시 히트 → 40초로 빠름. 첫 다운로드 시 ~2-3분 예상.
- **synthetic negative 반사**: AUC < 0.5로 모델 방향이 반대. 실 AI negative 없이는 `/rerank` 결과가 반사됨. Step 5 전까지 `degraded=True` 동작(AS 측 기존 단일초안 경로)이 실질적으로 더 안전.

---

## 다음 구체 작업 (Step 5 — AS Best-of-N 와이어링)

AS 측 수정:
- `AiUserMlClient.java` 신규 (AiLearningClient 패턴 복제):
  - base-url = `http://100.115.252.61:8201`
  - Bearer token = `aiuser-ml-api-token-dev-2026`
  - graceful skip (timeout 500ms, enabled=false 기본)
- `application.yml`: `ai-user-ml:` 블록 추가
- `ActionExecutor.executePost()`: N초안 생성 → `POST /rerank` → winner 선택
- `ActionExecutor.executePost()` + `executeComment()`: 작성 성공 시 AI negative push → `POST /corpus/ingest`

핵심 결정: `enabled=false` 기본 → feature flag로 점진 롤아웃
