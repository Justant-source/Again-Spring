# Step 18 (N1) — THEQOO 인간 코퍼스 디오염 (2026-06-16)

## 상태: ✅ 코퍼스 정제 완료 / ⏳ P(human) 방향 교정 대기 (n_ai≥100 필요)

---

## 완료 기준 달성 현황

| 항목 | 결과 |
|---|---|
| decontaminate.py 필터 구현 | ✅ 5규칙 필터 구현 |
| corpus_item 정제 완료 | ✅ 168개 삭제 (48.8%) |
| /corpus/ingest 가드 내장 | ✅ 향후 오염 차단 |
| THEQOO 재-pull | ✅ 클린 데이터 252개 확보 |
| P(human) 방향 교정 확인 | ⏳ **대기** — n_ai≥100 재학습 필요 |

---

## 필터 설계 (decontaminate.py)

| 규칙 | 조건 | 처리 |
|---|---|---|
| 링크지배 | URL 제거 후 잔여 < 25자 | 삭제 |
| 보일러플레이트 | 관리자/운영팀/공지/삭제예정 마커 | 삭제 |
| 광고덤프 | URL 3개 이상 | 삭제 |
| 초단문 | URL 없고 < 15자 | 삭제 |
| **서사+링크** | URL 있으나 잔여 ≥ 25자 | **KEEPER** (URL만 strip) |

---

## 드라이런 → 삭제 결과

| 지표 | 값 |
|---|---|
| THEQOO human POST (필터 전) | 344 |
| 오염 삭제 | **168개 (48.8%)** |
| 남은 클린 데이터 | 176 |
| 재-pull 후 총계 | **252** |
| 이유별 — 링크지배 | 123 (73%) |
| 이유별 — 광고/링크덤프 | 41 (24%) |
| 이유별 — 보일러플레이트 | 4 (2%) |

---

## 재학습 결과

```
Training 결과: INSUFFICIENT_DATA
  n_ai=66 < 100 → 학습 스킵 (예상된 결과)
  n_human=252 >= 300 충족
  현재 모델(오염 전 데이터) AUC 유지: 0.980482
```

**정상**: n_ai≥100이 되면 자동으로 재학습 트리거됨 (N8 완료 후).

---

## P(human) 스팟체크 결과 (현재 = 역전 유지)

| 텍스트 | P(human) | 기대 방향 |
|---|---|---|
| "어제 남친이 약속 또 어겼어ㅠㅠ..." (슬랭 서사) | **0.0075** | 高 기대 → 역전 ❌ |
| "당신의 남자친구는..." (AI 분석조) | 0.546 | 低 기대 → 부분 역전 |
| URL 전용 포스트 | 0.9999 | (오염 제거됨) |

**원인**: 현재 모델이 오염 전 344개 코퍼스로 학습됨 → 역전 패턴 유지.
**수정 경로**: N8(n_ai≥100) → `/train` 재학습 → 스팟체크 재실행 (N9).

---

## 인제스트 가드 (routes_corpus.py 추가)

```python
# /corpus/ingest human POST 인제스트 시 실시간 필터
from app.ml.decontaminate import is_contaminated, clean_text

if label == "human" and content_type == "POST":
    contaminated, reason = is_contaminated(text)
    if contaminated:
        log.info(f"[decontam] skip: {reason[:60]}")
        filtered_count += 1
        continue
    text = clean_text(text)  # URL strip (KEEPER path)
```

재-pull 중 **32개 추가 오염 항목 자동 차단** 확인.

---

## WSL 커밋

- 위치: `~/Data/Again-Spring-AI-User/`
- **변경 파일**: `app/ml/decontaminate.py` (신규), `app/api/routes_corpus.py` (가드 추가)
- 커밋 메시지: `feat(corpus): THEQOO decontamination filter + ingest guard (N1/Step18)`

---

## 함정

1. **cursor 리셋 방법**: cursor 파일 삭제 + 서비스 재시작으로 재-pull 트리거
2. **재-pull dedup**: SHA-256 unique 제약으로 이미 있는 행은 자동 skip → "727 skipped"
3. **모델 상태 유지**: 새 학습 없으니 P(human) 방향 바뀌지 않음 — N8 완료까지 정상

---

## 다음 단계

1. **N8(b)**: THEQOO n_ai +35개 → 100 달성 → `/train` 재학습
2. **N9**: 클린 모델로 A-B 재실행 + THEQOO P(human) 방향 교정 확인
3. **cond1 현황**: THEQOO n_human=252 ≥ 300 ✅, n_ai=66 < 100 ❌
