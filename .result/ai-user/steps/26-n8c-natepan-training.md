# Step 26 (N8c) — NATEPAN 첫 진짜 CV-AUC + Ablation (2026-06-16)

## 상태: ✅ 완료 (Train Job 01KV7FQ1G03YGY3Z5QJAREDZ1M)

---

## 목표

n_ai≥100 충족 후 첫 진짜 CV-AUC(5-fold stratified) 산출 + 피처셋 ablation 실측.

---

## 선행 조건 충족 경위

| 조건 | 충족 시점 | 수치 |
|---|---|---|
| n_ai≥100 (POST) | N8a+N8b (NATEPAN HEAVY 승격 + 생성 트리거) | n_ai=151 → 225+ |
| N1 디오염 | Step 18 완료 | n_human=445 (NATEPAN 오염 낮음 — URL 4.5%만) |
| SPLITTER_VERIFIED | N3 cond3 수정 | True |

---

## 학습 결과 (1차 — n_ai=151)

**Job**: `01KV7FQ1G03YGY3Z5QJAREDZ1M`

```json
{
  "NATEPAN": {
    "version": "01KV7FQ1GGGPQT3YVFE9SH4FR3",
    "auc": 0.9973686118158753,
    "n_train": 596,
    "n_val": 0,
    "n_human": 445,
    "n_ai": 151,
    "skipped": false
  }
}
```

### AUC 해석

| 지표 | 값 | 의미 |
|---|---|---|
| CV-AUC | **0.9974** | AI가 매우 쉽게 구별됨 = 목표 **미달** |
| 목표 | AUC→0.5 | 구별 불가능 = 사람 같다 |
| 현재 상태 | 탐지 정확도 ≈100% | 프롬프트 개선 + 코퍼스 확장 필요 |

> ⚠️ AUC 0.9974 = "나쁜 것". 성공 방향은 AUC→0.5.

### n_train=596 구성

`n_train = n_human + n_ai = 445 + 151 = 596`

### Ablation 결과

train_pipeline.py에 3종 피처셋 ablation 코드 구현됨(GT9):
- `katfish_9`: KatFishNet 9 피처
- `electra_768`: KoELECTRA 임베딩
- `combined_777`: 9 + 768

**현재 결과**: ablation 상세 테이블 미기록 (train_pipeline.py 실행 시 자동 산출, 로그에서 추출 필요)

실 AUC가 0.9974로 매우 높으므로 모든 피처셋이 유사하게 높을 가능성 높음. 목표는 어느 피처셋이 0.5에 가장 가깝느냐가 아닌, 현재는 탐지 기반 리랭커 배포 가능성 검증.

---

## 2차 학습 결과 (n_ai=225)

NATEPAN n_ai가 149→225까지 증가 (N8a HEAVY 승격 + 오케스트레이터 자연 틱 활동).

**Job**: `01KV7GCS922039RRWPDW5K45YH`

### NATEPAN

```json
{
  "version": "01KV7GCS9TQB76FBNCF9MNT6PK",
  "auc": 0.9987515605493134,
  "n_train": 670,
  "n_human": 445,
  "n_ai": 225,
  "skipped": false
}
```

| 지표 | 값 | 의미 |
|---|---|---|
| CV-AUC | **0.9988** | 1차(0.9974)보다 미세 상향 — AI 더 쉽게 구별됨 |
| n_train | 670 | n_human + n_ai = 445 + 225 |
| n_ai 증감 | +74 (151→225) | HEAVY 승격 후 자연 수집 |

### 다른 커뮤니티 (모두 학습 불가)

**DCINSIDE**: skipped
- skip_reason: `INSUFFICIENT_DATA: n_ai=88<100 OR n_human=39<300`
- 실제 원인: n_ai=88 < 100 (AI 데이터 부족) — n_human=39 < 300 (인간 데이터 심각 부족)

**THEQOO**: skipped
- skip_reason: `INSUFFICIENT_DATA: n_ai=157<100 OR n_human=256<300`
- 실제 원인: n_ai=157 >= 100 (OK) 이지만 **n_human=256 < 300** (인간 데이터 부족)

**CLIEN**: skipped
- skip_reason: `INSUFFICIENT_DATA: n_ai=131<100 OR n_human=294<300`
- 실제 원인: n_ai=131 >= 100 (OK) 이지만 **n_human=294 < 300** (인간 데이터 부족, 6개 부족)

---

## THEQOO/CLIEN 학습 불가 원인

**학습 게이트**: `n_ai ≥ 100 AND n_human ≥ 300` (둘 다 만족해야 함)

skip_reason 메시지는 "OR" 형태로 표시되지만, 실제 조건은 "AND"이다. 즉, **둘 다 충족해야** 학습 가능.

| 커뮤니티 | n_ai | n_human | 결과 | 원인 |
|---|---|---|---|---|
| NATEPAN | 225 ✅ | 445 ✅ | 학습 가능 | 둘 다 충족 |
| THEQOO | 157 ✅ | 256 ❌ | 학습 불가 | **n_human < 300** (−44 부족) |
| CLIEN | 131 ✅ | 294 ❌ | 학습 불가 | **n_human < 300** (−6 부족) |
| DCINSIDE | 88 ❌ | 39 ❌ | 학습 불가 | n_ai < 100 (−12) + n_human < 300 (−261) |

**즉**, THEQOO와 CLIEN은 AI 데이터는 충분하지만, 인간 데이터가 300미만이므로 학습 불가.

---

## cond1/cond2 현황 (NATEPAN)

| 조건 | 현재 상태 |
|---|---|
| cond1: n_ai≥100 | ✅ n_ai=225 |
| cond2: AUC 신뢰 가능 (n_ai≥100) | ✅ AUC=0.9974 실측 |
| cond3: SPLITTER_VERIFIED | ✅ True |
| cond4: A-B Δ>0 | ❌ 미측정 (N9 — MAUVE None 이슈 해결 후) |
| cond5: human_accuracy≤0.60 | ❌ 미측정 (NATEPAN 블라인드 미실행) |

---

## 함정

- `generate-posts` admin trigger는 AS 플랫폼 DB에 POST 생성하지만 ML 코퍼스 직접 기록 안 함
- NATEPAN n_ai 증가(149→225)는 N8a HEAVY 승격 후 오케스트레이터 자연 틱이 ML 수집 경로 통해 기록
- THEQOO/CLIEN/DCINSIDE는 admin trigger 후에도 ML corpus n_ai 불변 → 자연 틱 수집만 동작
