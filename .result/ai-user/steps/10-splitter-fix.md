# Step 10 완료 기록 — DCINSIDE 문장 분리기 통일 (Base Hardening T1)

**날짜**: 2026-06-15
**세션**: Base Hardening Phase
**상태**: ✅ 완료 (PASS)

---

## 문제 정의 (Before)

### 원본 상태
- **분리기 중복 존재**: `features_katfish.py`의 `split_sentences()`와 `eval_harness.py`의 정규식이 서로 다른 로직 사용
- **정규식 불일치**: 
  - `features_katfish.py`: 마침표·느낌표·물음표 단순 분리
  - `eval_harness.py`: 복잡한 정규식 (문맥·약자 처리 시도)
- **평가 왜곡**: 두 경로의 차이로 인해 학습 데이터와 평가 데이터의 문장 분리가 불일치
- **DCINSIDE 지표**: avg_sentence_length = **57.40** (과도히 김 → 분리기 미작동 신호)

---

## 해결 방법

### 통일된 분리 함수 신설
공유 함수 `split_sentences()`를 단일 권위본으로 확립:

```python
def split_sentences(text: str) -> list[str]:
    """
    문장을 명확한 경계로 분리.
    경계: 마침표, 느낌표, 물음표 (각각 뒤따르는 공백 포함)
    """
    import re
    # 문장 끝 마커: . ! ? 다음 공백(들)
    sentences = re.split(r'[\.\!\?]+\s+', text)
    # 빈 문장 제거 및 정리
    return [s.strip() for s in sentences if s.strip()]
```

### 분리 경계 명시
| 경계 | 예 | 처리 |
|---|---|---|
| 마침표 | `좋습니다.` → `좋습니다` | 기본 문장 끝 |
| 느낌표 | `정말이에요!` → `정말이에요` | 감정 강조 |
| 물음표 | `왜인가요?` → `왜인가요` | 의문 문장 |
| 연속 마침표 | `아...` → `아` | 여러 마침표 일괄 처리 |
| 줄바꿈 후 분리 | `끝.\n새로` | 공백 기준 정규식 |

### 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `ai-user/features_katfish.py` | `split_sentences()` 통일된 구현 신설 |
| `ai-user/eval_harness.py` | 기존 정규식 제거 → `split_sentences()` 호출로 전환 |
| `tests/test_splitter.py` (신규) | 경계 케이스 12건 추가 테스트 |
| `tests/test_eval_integration.py` (수정) | 분리기 통일 후 평가 경로 동기화 |

---

## Before / After 비교

### DCINSIDE 핵심 지표

| 지표 | Before | After | 개선 |
|---|---|---|---|
| avg_sentence_length | 57.40 | **7.02** | ✅ 87.8% 감소 (정규화) |
| n_human_samples | 39 | 39 | — |
| n_ai_samples | 20 | 20 | — |
| MAUVE 점수 | — | 0.9999 | ✅ 극도로 높음 (스타일 유사) |

---

## 4개 커뮤니티 평가 결과 (eval_result_summary)

### 종합 지표

| 커뮤니티 | n_human | n_ai | MAUVE | avg_sentence_length | human_burstiness | 상태 |
|---|---|---|---|---|---|---|
| **DCINSIDE** | 39 | 20 | 0.9999 | 7.02 | 0.729 | ✅ 우수 |
| **THEQOO** | 332 | 65 | 0.3454 | 3.99 | 1.032 | ⚠️ MAUVE 낮음 |
| **CLIEN** | 286 | 40 | 0.9877 | 6.97 | 0.847 | ✅ 우수 |
| **NATEPAN** | 443 | 0 | null | 6.65 | 0.909 | — (샘플 없음) |

### 세부 해석

- **DCINSIDE**: MAUVE 0.9999 + avg_sentence_length 7.02 → **최고 품질 달성**
  - 분리기 통일 전 57.40 → 통일 후 7.02로 정규화
  - 인간과 AI의 스타일 유사도 극대화

- **THEQOO**: MAUVE 0.3454 (낮음) → 문체 특성상 분리기 외 요인 (감정·은어)
  - avg_sentence_length 3.99 (최단) = 매우 짧은 문장 선호
  - 분리기 개선 후에도 MAUVE 개선 필요 (별도 작업)

- **CLIEN**: MAUVE 0.9877 + avg_sentence_length 6.97 → **우수 성과**
  - DCINSIDE와 유사한 문체 특성 (교양·중립)

- **NATEPAN**: AI 샘플 0 → 평가 불가 (다음 단계에서 샘플 수집 후 재평가)

---

## 완료 기준 충족 여부

### T1: Sentence Splitter Fix

| 기준 | 충족 | 근거 |
|---|---|---|
| ✅ pytest 모두 통과 | YES | `pytest_passed: true` |
| ✅ DC avg_sentence_length 개선 | YES | 57.40 → 7.02 (87.8% 개선) |
| ✅ 분리기 통일 | YES | `split_sentences()` 단일 함수 신설 |
| ✅ DC MAUVE >= 0.99 | YES | 0.9999 (우수) |
| ✅ 4개 커뮤니티 평가 | YES | 전수 평가 완료 |

### 결론: **PASS** ✅

분리기 통일로 DCINSIDE의 문장 길이를 정규화하여 평가 신뢰도 극대화.
다음 단계(Base Hardening T2)로 진행 가능.

---

## 참고: eval_result_summary 상세 구조

```json
{
  "DCINSIDE": {
    "n_human": 39,
    "n_ai": 20,
    "mauve": 0.9999469309892473,
    "avg_sentence_length": 7.023971888837834,
    "human_burstiness": 0.7291176857602629
  },
  "THEQOO": {
    "n_human": 332,
    "n_ai": 65,
    "mauve": 0.3454370308081824,
    "avg_sentence_length": 3.9958482331631786,
    "human_burstiness": 1.0319954423422417
  },
  "CLIEN": {
    "n_human": 286,
    "n_ai": 40,
    "mauve": 0.987673428805067,
    "avg_sentence_length": 6.9742108614599605,
    "human_burstiness": 0.8466136619063707
  },
  "NATEPAN": {
    "n_human": 443,
    "n_ai": 0,
    "mauve": null,
    "avg_sentence_length": 6.652918698846831,
    "human_burstiness": 0.90886477370921
  }
}
```

---

**담당**: Base Hardening Phase  
**다음 단계**: T2 (Ending Sentence Diversity) 진행  
**평가 Job ID**: 01KV71ZT17ZFYV877XCJBQX2SA
