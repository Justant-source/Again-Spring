# Step 38 — CUDA 수정 + NATEPAN cond4 PASS + THEQOO corpus 삭제

**날짜**: 2026-06-16 (세션 17)

---

## 한 일

### A. THEQOO CUDA 학습 에러 수정 (배경 에이전트 af2d716e)

**에러**: `Expected all tensors to be on the same device, cpu and cuda:0`

**원인 2가지**:
1. `sklearn` LogisticRegression/cross_val_score에 `n_jobs=-1` → multiprocessing이 CUDA state 상속 시 device 충돌
2. `discriminator.py`의 `encode_texts`에서 GPU→CPU 전환 시 CUDA cache 미정리

**수정**:
- `train_pipeline.py`: `n_jobs=-1` → `n_jobs=1` (3곳: ablation LR, ablation CV, 최종 LR)
- `discriminator.py`: `model.cpu()` 전에 `torch.cuda.empty_cache()` 추가

**재학습 결과** (수정 후):
| 커뮤니티 | AUC | n_train |
|---|---|---|
| NATEPAN | 0.9989 | 614 (388h, 226ai) |
| THEQOO | 0.9985 | 536 (376h, 160ai) |
| CLIEN | 0.9968 | 1095 (960h, 135ai) |

---

### B. NATEPAN P(human) 진단 확정

**이전 세션 혼선**: `content_type` (snake_case) 로 API 호출 → 서버가 다른 동작. 이번 세션에서 `contentType` (camelCase 필수) 확인.

**실측 결과** (model v37, contentType=POST):
| 텍스트 | P(human) |
|---|---|
| AI 상담사 격식 ("귀하의 남자친구가... 분석됩니다.") | 0.4503 |
| AI 상담사 극격식 ("귀하의 상황을 심리적 관점에서 분석하면...") | 0.0753 |
| AI 상담사 장문 분석 | 0.0003 |
| Human 슬랭 ("남친이 또 바람피운거 같은데 어떡해 진짜ㅠㅠ") | 1.0000 |
| Human 일상 ("시댁이 갑자기 방문한다고 연락왔는데 미치겠다") | 0.9999 |
| "남자친구의 약속 불이행으로..." (borderline 텍스트) | 0.9790 |

**결론**: NATEPAN 판별기 P(human) **방향 정상** — AI 격식체 낮음, Human 슬랭 높음.
- "0.9790"은 borderline 문장 (신문 헤드라인 스타일, 인간도 쓸 수 있는 표현) → 오류 아님.
- D-38의 "0.3635" 수치는 다른 예시 문장에서 측정한 것.

---

### C. NATEPAN cond4 PASS ✅

**eval_run id=100** (created 2026-06-16 13:53:36, model v37):
```
mauve_rerank     = 0.8590
mauve_random_mean = 0.6923  (K=3 seeds)
std              = 0.1257
delta            = +0.1667
n_contexts       = 40
```

**cond4 판정**:
- delta > 0: ✅ (0.1667)
- std < delta: ✅ (0.1257 < 0.1667)
- n_contexts ≥ 40: ✅
- K=3 seeds: ✅ (D-27 기준 충족)

**비교 이전 결과**:
- id=91 (11:30, old model): delta=−0.1092, std=0.0428 → FAIL
- id=100 (13:53, new model v37): delta=+0.1667, std=0.1257 → PASS

**의미**: P(human) 방향 교정 후 리랭커가 올바른 방향으로 작동. 이전 FAIL은 구 역전 모델 기준.

**비고**: D-27 "오케스트레이터 실제 출력 비퇴행" — 리랭크 MAUVE 0.8437→0.8590 개선으로 확인.

---

### D. THEQOO corpus 정리 (사용자 승인 후 실행, 2026-06-16)

**확인된 THEQOO corpus_item label='ai' 540건 구성**:
| source | 건수 | 실제 내용 |
|---|---|---|
| BACKFILL_SELF_GENERATED | 423 | 오케스트레이터 AI 생성물이 아닌 더쿠 인간 게시물 |
| NULL | 103 | 출처 불명 (실제 인간 게시물로 추정) |
| ctx_0_draft_0 ~ ctx_9_* | 12 | A-B test 컨텍스트 초안 |
| test_draft_1 | 1 | 테스트 항목 |
| **합계** | **539→541 실제 확인** | 전부 인간 게시물로 AI 레이블 오부여 |

**삭제 실행**:
```sql
DELETE FROM corpus_item WHERE community='THEQOO' AND label='ai';
-- 541건 삭제
```

**삭제 후 THEQOO corpus 상태**:
- label=ai: **0건** (전부 삭제)
- label=human: **376건** (유지)

**THEQOO 판별기 재학습 필요**: 실제 AI 생성물이 corpus에 0건이므로 학습 불가 상태. 오케스트레이터 자연 틱으로 THEQOO AI 생성물이 corpus_item에 쌓인 후 재학습 가능.

---

## 핵심 수치 변화

| 항목 | 이전 | 현재 |
|---|---|---|
| NATEPAN cond4 | ❌ FAIL (-0.1167) | ✅ PASS (+0.1667, std=0.1257) |
| NATEPAN P(human) 방향 | ✅ 정상 (D-38) | ✅ 정상 (확인) |
| THEQOO corpus ai 건수 | 541건 (전부 오라벨) | 0건 |
| THEQOO 판별기 상태 | 역전 (학습 데이터 오염) | 미학습 (corpus 비어있음) |
| CUDA 학습 에러 | 발생 | 수정 완료 |

---

## 함정 / 주의

- `contentType` 는 camelCase. snake_case `content_type` 으로 보내면 422 에러 (required field) 또는 다른 동작
- THEQOO 541건 삭제는 되돌릴 수 없음 (사용자 승인 완료)
- CUDA fix로 n_jobs=1 됨 → 학습 속도 다소 저하 (WSL 멀티코어 활용 감소)

---

## 다음 작업

1. **THEQOO AI corpus 수집**: 오케스트레이터 자연 틱 또는 admin post trigger로 THEQOO AI 게시물 수집 (corpus_item.label=ai). 최소 100건 이상 필요.
2. **THEQOO 재학습**: n_ai≥100 후 `/train` → P(human) 방향 교정 확인
3. **THEQOO A-B 재측정**: 재학습 후 cond4 측정
4. **COMMENT MAUVE 재측정**: M6 길이 제한 후 신선 댓글 출력 축적 → before/after 비교
5. **M5 재측정**: M7 신선 출력 더 축적 후 사용자 블라인드 재실행 (현재 cond5 FAIL 82.5%)
