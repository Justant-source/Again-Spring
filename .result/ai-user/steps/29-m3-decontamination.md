# Step 29 (M3) — 전 커뮤니티 ctx_* 오염 제거 및 재학습 (2026-06-16)

## 상태: ✅ 완료 (전 커뮤니티 재학습 완료, 신규 CV-AUC 확보)

---

## 목표

N1 THEQOO 디오염 이후, 전 커뮤니티를 대상으로 테스트 누수(ctx_* 마커) 제거 + 추가 오염 정제 → 신뢰할 수 있는 CV-AUC 재산출.

---

## 수정 내용

### 1. ctx_* 테스트 누수 제거

THEQOO/CLIEN/NATEPAN 코퍼스에서 테스트 잔존 마커(`ctx_*` = label=human으로 설정된 테스트 데이터) 22행 완전 삭제:

| 커뮤니티 | 삭제된 ctx_* 행 수 |
|---|---|
| THEQOO | 11행 |
| CLIEN | 9행 |
| NATEPAN | 2행 |
| **합계** | **22행** |

### 2. NATEPAN/CLIEN 추가 디오염

N1에서 THEQOO만 수행했던 링크·공지·광고 필터를 NATEPAN/CLIEN으로 확대:
- decontaminate.py 필터 적용
- 잔여 텍스트 <25자 삭제, 보일러플레이트 마커 제거

### 3. 전 커뮤니티 재학습 및 신규 CV-AUC

`/train` 완료 후 Stratified 5-fold CV로 신뢰할 수 있는 AUC 산출:

| 커뮤니티 | n_train (ctx_* 삭제 후) | AUC mean | AUC std | 모델 ID |
|---|---|---|---|---|
| THEQOO | 534 (544→534) | **0.9986** | **0.00275** | 01KV7V3A1AR9YA39P5JVH5KX0H |
| CLIEN | 1091 | **0.9947** | **0.0095** | 01KV7V3EJXV3RB1S4SANY05NBX |
| NATEPAN | 613 | **0.9994** | **0.00086** | 01KV7V33HB9BQJEZG5E7DWRJ99 |

---

## 완료 기준 달성

- [x] ctx_* 22행 DELETE (label=human 테스트 누수)
- [x] THEQOO/CLIEN/NATEPAN decontaminate 확장
- [x] 전 커뮤니티 `/train` 완료
- [x] 신규 CV-AUC 5-fold mean±std 산출

---

## 함정

- **AUC 여전히 높음 (0.998~0.999)**: "AI가 여전히 쉽게 구별됨" = 판별기가 과신뢰 상태. M5 블라인드 평가 후 재정의 필요.
- **P(human) 역전 여전히 유지**: M1 디오염 후에도 슬랭 서사→HIGH, 격식AI→LOW 상태 (M2에서 재확인 완료). 코드 버그 아님 — T8 AI corpus가 슬랭화되어 판별기가 역방향 학습. n_ai≥100 신선 출력으로 재학습 필요.

---

## 다음 단계

- **M4**: ablation + CV std 실측치 표 추출
- **M7**: 생성 스타일 다양화(NATEPAN features + reply voiceType)로 신선 출력 축적
- **M5**: M7 신선 출력 확보 후 사람 블라인드 평가로 cond5 측정
