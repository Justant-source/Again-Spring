# Step 12 완료 기록 — readiness 게이트 버그 수정 (POST 전용 카운트)

**날짜**: 2026-06-15  
**세션**: T3 Base Hardening  
**상태**: ✅ SUCCESS: T3 Base Hardening - Readiness Gate Bug Fixed  
**Pytest**: PASS (모든 테스트 통과)

---

## 요약

Step 11에서 발견한 readiness 게이트 버그를 수정했습니다.

### 버그의 원인

n_ai 카운트가 **POST + COMMENT 합산**되어 있었습니다.
- NATEPAN의 경우: 댓글 295개(AI)가 포함되어 n_ai=295 → `ready=true`
- 실제로는 POST만 사용하는 훈련 데이터(n_ai=0)와 불일치

### 수정 내용

**파일**: `ai-user/routes_metrics.py` + `ai-user/retrain_loop.py`

```python
# 수정 전: 모든 콘텐츠 유형 포함
n_ai = count_ai_content_by_community(community_name)

# 수정 후: POST만 필터링
n_ai = count_ai_content_by_community(community_name, content_type='POST')
```

동일하게 `retrain_loop.py`에도 `content_type='POST'` 필터를 추가하여 일관성 확보.

---

## 의미 명시: ready = "리랭커 배포 가능 (NOT 사람 같음)"

readiness 게이트에 명확한 의미를 부여했습니다:

| 항목 | 값 |
|---|---|
| **ready의 정의** | 리랭커(reranker) 배포 가능 상태 |
| **의미 NOT** | AI 생성 콘텐츠가 사람 같음(human-like) |
| **용도** | 판별 모델 배포, 마케팅 자동화 활성화 |

이를 `ready_meaning` 필드에 추가하여 API 응답에 명시.

---

## 수정 전/후 NATEPAN 준비 상태

### 수정 전 (POST + COMMENT 합산)
```json
{
  "n_human": 443,
  "n_ai": 295,
  "latest_auc": 0.561798,
  "ready": true  // 버그: 댓글로 인해 n_ai 과대계산
}
```

### 수정 후 (POST만)
```json
{
  "n_human": 443,
  "n_ai": 0,
  "n_ai_note": "POST only (training uses POST only)",
  "latest_auc": 0.561798,
  "auc_note": "CV 5-fold mean (NOT single-split)",
  "auc_target": 0.75,
  "min_ai_needed": 100,
  "ready": false,  // 수정됨: n_ai < min_ai_needed
  "ready_meaning": "reranker-deployable (NOT human-like)"
}
```

---

## 전체 커뮤니티 Readiness 현황

| 커뮤니티 | n_human | n_ai | latest_auc | auc_target | min_ai_needed | ready | 비고 |
|---|---|---|---|---|---|---|---|
| **NATEPAN** | 443 | 0 | 0.561798 | 0.75 | 100 | ❌ | AUC 미달 |
| **DCINSIDE** | 39 | 20 | 1.0 | 0.75 | 100 | ❌ | n_ai 미달 |
| **THEQOO** | 332 | 65 | 0.980482 | 0.75 | 100 | ❌ | n_ai 미달 |
| **CLIEN** | 286 | 40 | 0.989035 | 0.75 | 100 | ❌ | n_ai 미달 |

### 요약
- **ready_count**: 0/4 (모두 준비 미완료)
- **임계점**:
  - n_ai ≥ 100 (모든 커뮤니티 미달)
  - AUC ≥ 0.75 (NATEPAN 미달, 나머지는 우수)

---

## 다음 단계 (Step 13+)

1. **AI 콘텐츠 생성 확대**: n_ai < 100 → 1,000+ 목표
2. **모델 성능 개선**: NATEPAN AUC 0.561 → 0.75+ (특성 엔지니어링/하이퍼파라미터)
3. **배포 준비**: 모든 커뮤니티 ready=true 달성 시 prod 리랭커 배포

---

## 검증 명령

```bash
# Pytest 실행 (POST 필터 검증)
cd ai-user && python -m pytest tests/ -v

# Readiness 게이트 확인
curl http://localhost:8200/api/readiness
```

**결과**: ✅ Pytest PASS, readiness 응답 정상 (POST 카운트 반영)

---

**마지막 갱신**: 2026-06-15 | Step 11 발견 사항 → Step 12 수정 완료
