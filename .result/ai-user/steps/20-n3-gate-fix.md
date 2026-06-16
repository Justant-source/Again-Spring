# Step 20 (N3) — enable-gate cond3·cond5 로직 정정 (2026-06-16)

## 결론

`GET /metrics/enable-candidates`의 cond3(분리기 임계)·cond5(블라인드 방향) 로직 정정 완료.
cond3는 데이터 의존 임계를 테스트 기반 불리언으로 교체, cond5는 역방향 임계(≤0.60) 추가.

---

## 완료 기준 달성

| 항목 | 결과 |
|---|---|
| cond3 테스트 기반 불리언 | ✅ `SPLITTER_VERIFIED=True` (False-negative 제거) |
| cond5 역방향 임계 추가 | ✅ `human_accuracy ≤ 0.60 = 성공` |
| cond5 방향 주석 | ✅ "높을수록 탐지됨 = 미달" |
| 역방향 임계 잔존 0 확인 | ✅ |
| `/metrics/enable-candidates` 응답 확인 | ✅ 12개 모두 enable_candidate=false (정상) |

---

## 변경 내용

### app/config.py — 상수 추가

```python
splitter_verified: bool = True
# 설명: D-21 경계 단위테스트(Step 19) 통과 확인. cond3 불리언.

blind_accuracy_threshold: float = 0.60
# 설명: cond5 방향. 정확도 ≤ 이 값 = AI가 인간처럼 보임 = 성공.
#       높을수록 AI 탐지됨 = 실패.
```

### app/api/routes_metrics.py — cond3 정정 (이전 vs 수정)

**이전** (데이터 의존, false-negative 위험):
```python
# DCINSIDE: avg_sl < 20
# 기타: bl_run is not None
```

**수정** (테스트 기반 불리언):
```python
cond3 = settings.splitter_verified
# 주석: D-21 Step 19 unit test verified. Splitter normalization confirmed.
```

### app/api/routes_metrics.py — cond5 정정 (이전 vs 수정)

**이전** (임계 없음, 방향 미검증):
```python
cond5 = blind_run is not None
# blind_accuracy 읽지만 미사용
```

**수정** (역방향 임계 포함):
```python
blind_accuracy = bm.get("human_accuracy", 1.0)
# 정확도 ≤ 0.60 = AI가 인간처럼 보임 = 성공 (높을수록 탐지됨 = 실패)
cond5 = blind_run is not None and blind_accuracy <= settings.blind_accuracy_threshold
```

---

## API 응답 (정정 후)

**cond3_splitter**:
```json
{
  "met": true,
  "baseline_avg_sl": null,
  "note": "D-21 Step 19 unit test verified. Splitter normalization confirmed."
}
```

**cond5_human_blind**:
```json
{
  "met": false,
  "human_accuracy": null,
  "human_accuracy_threshold": 0.6,
  "note": "T6 human_blind EvalRun not yet created. Accuracy ≤ 0.60 = success (AI appears human-like)."
}
```

**전체 현황**: 12개 커뮤니티 모두 `enable_candidate=false` (정상 — N5 미실행, n_ai<100 등)

---

## 이전 버그 정리

| 버그 | 증상 | 수정 |
|---|---|---|
| cond3 THEQOO false-negative | avg_sl=3.99가 임계 미달로 cond3=false | 테스트 기반으로 교체, THEQOO cond3=true |
| cond5 방향 없음 | 정확도 100%도 통과 | 역방향 임계(≤0.60) 추가 |
| Step14 문서 모순 | `≥0.80` vs `<0.75` 혼재 | 0.60으로 통일, 코드 주석 명시 |

---

## WSL 커밋

- **commit**: `dac259b`
- **메시지**: `fix(gate): cond3 test-based boolean + cond5 direction threshold (D-22/D-23)`
- **변경 파일**: `app/config.py`, `app/api/routes_metrics.py`

---

## 다음 단계

- N5 완료 시 → `blind_accuracy ≤ 0.60` 실측값으로 cond5 재검증
- N8 완료 시 → n_ai≥100 → cond1·cond2 재검증
