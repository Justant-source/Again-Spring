# Step 14 완료 기록 — ENABLE 게이트 5조건 구현

**상태**: ✅ 완료 (2026-06-15)  
**담당 태스크**: T7 (Step 14)  
**결과 요약**: GET `/metrics/enable-candidates` 엔드포인트 구현 완료 · 5조건 게이트 로직 적용 · 모든 커뮤니티 enable_candidate=false (조건 미충족 예상)

---

## 1. 5조건 정의 (결정 D-17 기반)

다시봄 AI 유저 학습 모드 활성화(`AI_USER_ML_ENABLED=true`)는 다음 5가지 조건을 **모두** 만족해야 한다.

| 조건 | 명칭 | 검사 대상 | 합격 기준 |
|---|---|---|---|
| **Cond1** | Data Sufficiency | 커뮤니티별 포스트 | AI ≥100개 AND 사람 ≥300개 |
| **Cond2** | CV AUC | Cross-Validation | cv_auc_mean ≥ 0.75 AND cv_auc_std ≤ 0.05 |
| **Cond3** | Splitter | 글 길이 예측 | baseline_avg_sl ≥ 6.5 |
| **Cond4** | A/B Test (MAUVE) | T6 ab_test EvalRun | mauve_delta ≥ 0.1 |
| **Cond5** | Human-Blind | T6 human_blind EvalRun | human_blind score ≥ 0.80 |

---

## 2. 구현: GET `/metrics/enable-candidates`

**엔드포인트 명세**

```
GET /metrics/enable-candidates
응답 형식: JSON
```

**응답 본문**

```json
{
  "summary": {
    "enable_candidate_count": <정수>,
    "total_count": <정수>,
    "note": "<설명>",
    "enable_flag": "AI_USER_ML_ENABLED=<현재값>"
  },
  "communities": {
    "<COMMUNITY_NAME>": {
      "enable_candidate": <boolean>,
      "conditions": {
        "cond1_data": {
          "met": <boolean>,
          "n_ai_post": <정수>,
          "n_human_post": <정수>,
          "required_n_ai": 100,
          "required_n_human": 300
        },
        "cond2_cv_auc": {
          "met": <boolean>,
          "cv_auc_mean": <소수|null>,
          "cv_auc_std": <소수|null>
        },
        "cond3_splitter": {
          "met": <boolean>,
          "baseline_avg_sl": <소수|null>
        },
        "cond4_ab_mauve": {
          "met": <boolean>,
          "mauve_delta": <소수|null>,
          "note": "<설명>"
        },
        "cond5_human_blind": {
          "met": <boolean>,
          "note": "<설명>"
        }
      }
    }
  },
  "errors": []
}
```

---

## 3. 현재 상태 (2026-06-15)

### 요약

- **enable_candidate_count**: 0 / 12
- **AI_USER_ML_ENABLED**: false (변경 없음)
- **비고**: 대부분 조건 미충족 (Base Hardening 단계에서 예상) · Enable은 수동 옵스 액션

### 커뮤니티별 상세

| Community | Cond1 | Cond2 | Cond3 | Cond4 | Cond5 | Enable? | 주요 사유 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| ARCALIVE | ❌ | ❌ | ❌ | ❌ | ❌ | **NO** | Cond1: AI 51 < 100 · Cond4/5: T6 미생성 |
| CLIEN | ❌ | ❌ | ✅ | ❌ | ❌ | **NO** | Cond1: AI 40 < 100 · Cond4/5: T6 미생성 |
| DCINSIDE | ❌ | ❌ | ✅ | ❌ | ❌ | **NO** | Cond1: AI 20 < 100 · Cond4/5: T6 미생성 |
| NATEPAN | ❌ | ❌ | ✅ | ❌ | ❌ | **NO** | Cond1: AI 0 < 100 · Cond4/5: T6 미생성 |
| THEQOO | ❌ | ❌ | ✅ | ❌ | ❌ | **NO** | Cond1: AI 65 < 100 · Cond4/5: T6 미생성 |
| 8개 커뮤니티 | ❌ | ❌ | ❌ | ❌ | ❌ | **NO** | — |

---

## 4. 미충족 조건 분석

### Cond1 (Data Sufficiency)

대부분 커뮤니티가 AI 포스트 목표(100개)에 미달:
- ARCALIVE: 51/100 (51%)
- CLIEN: 40/100 (40%)
- DCINSIDE: 20/100 (20%)
- NATEPAN: 0/100 (0%)
- THEQOO: 65/100 (65%)

**대책**: Base Hardening 단계에서 AI 유저 정상 작동 시 자동 해결. 추가 시딩 필요 시 `generate-posts` 트리거 실행.

### Cond2 (CV AUC)

모든 커뮤니티에서 `cv_auc_mean=null` · `cv_auc_std=null`

**사유**: 분류 모델 학습 미완료. Base Hardening(T8~T9)에서 스플리터 기초 학습 후 결과값 산출 예정.

### Cond3 (Splitter)

**합격**: CLIEN, DCINSIDE, NATEPAN, THEQOO (4개)  
**불합격**: ARCALIVE, 8개 커뮤니티

**분석**:
- CLIEN: baseline_avg_sl = 6.97 ✅
- DCINSIDE: baseline_avg_sl = 7.02 ✅
- NATEPAN: baseline_avg_sl = 6.65 ✅
- THEQOO: baseline_avg_sl = 3.99 ❌ (경계값 6.5 미달)

**주의**: THEQOO는 Cond3 도 미충족 (데이터 질 부족 가능성).

### Cond4 (A/B Test MAUVE) & Cond5 (Human-Blind)

**상태**: T6 (Step 15) 미완료  
**예상**: T6에서 ab_test · human_blind EvalRun 객체 생성 시 결과값 자동 입력

---

## 5. 명시: AI_USER_ML_ENABLED 정책

| 항목 | 값 | 비고 |
|---|---|---|
| **현재 상태** | `false` | prod · dev 동일 |
| **변경 권한** | 수동 옵스만 | `.env.prod` · `.env.dev` 직접 편집 |
| **코드 주입 금지** | ✅ 엄격 적용 | get/enable-candidates 조회용일 뿐 |
| **변경 시점** | Base Hardening 완료 후 | 5조건 모두 충족 + ops 확인 시 |

**CRITICAL**: 이 엔드포인트는 **조회(read) 전용**. 모든 조건을 만족하더라도 AI_USER_ML_ENABLED을 코드로 변경하면 안 된다.

---

## 6. 테스트 결과

| 항목 | 결과 |
|---|---|
| 엔드포인트 추가 | ✅ 성공 |
| 테스트 추가 | ✅ 성공 (5조건 로직 커버) |
| 빌드 | ✅ 성공 |
| pytest 통과 | ✅ 통과 |

---

## 7. 다음 단계 (로드맵)

| Step | 태스크 | 상태 | 예상 영향 |
|---|---|---|---|
| 15 | T8 (Splitter 학습) | 📅 대기 | Cond2, Cond3 갱신 |
| 16 | T9 (A/B Test) | 📅 대기 | Cond4 갱신 |
| 17 | T10 (Human-Blind) | 📅 대기 | Cond5 갱신 |
| 18 | T11 (Enable 최종 판정) | 📅 대기 | AI_USER_ML_ENABLED 변경 → prod 배포 |

---

**파일 생성**: 2026-06-15  
**Reference**: Decision D-17 · T7 결과 JSON
