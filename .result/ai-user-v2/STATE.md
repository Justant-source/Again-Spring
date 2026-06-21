# AI-User v2 / v2.1 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 (v2.1 Phase 5 완료 — FAIL 80%·Phase 6 진행 중)

---

## ⛔ v2 — CLOSED (2026-06-21)

Phase 0~6 완료. 결과: 88.9%→55.6% PASS (-33.3pp). 측정 착시 2개 확인 → v2.1로 계속.

상세: `roadmap.md` v2 섹션 / `lessons.md` / `decisions.md` V2-D01~D03.

---

## 🔄 v2.1 — 광장 정렬(Plaza-Alignment)

> **불변식**: proxy/MAUVE/LLM-judge를 humanness 게이트로 절대 금지.
> **kill criterion 오너 확정 전 어떤 humanness PASS/FAIL 판정도 금지.**

### v2.1 핵심 규율 (잊지 말 것)

> - **단위 = 광장별 계정 타임라인.** 6 광장(`COUPLE·MARRIED·FRIEND·FAMILY·WORK·OTHER`) 각각.
> - **오라클 = 신선 캐주얼 독자 ≥3인.** 오너 = 캘리브레이션 전용.
> - **NATEPAN 전용.** 분류는 6 광장 taxonomy로만. 신규 크롤러 금지.
> - **변수 1개/측정.** kill criterion 측정 전 오너 등록 필수.
> - **판별기 = QA 전용.** rerank OFF. `AI_USER_ML_ENABLED=false` 영구.
> - **D-108 COLLECT-only 유지.** 출하 레버 보존.
> - **kill criterion 사전 등록 (오너 확정 대기)**: ≤60% naive ≥3인.

---

## 현재 위치

### Phase 0 — 창립 & 동결 ✅ (2026-06-21)

- [x] `roadmap.md` v2 CLOSED 표기 + v2.1 섹션 추가
- [x] `charter-v2.1.md` 창립 (재구성 3명제·규율·코드훅 동결)
- [x] `decisions.md` V2-D04(재구성) + V2-D05(kill criterion 사전 등록) 추가
- [x] `STATE.md` v2.1 라이브 포인터 초기화
- [x] `steps/v2.1-00-charter.md` 기록
- [x] `npm run lint:docs` exit 0 통과
- [🔴] **kill criterion 오너 명시 확정 대기** (제안 ≤60% naive ≥3인 사용 중. Phase 5 측정 전 필수.)
- [x] git commit (v2.1 Phase 0·1·2)

---

### Phase 1 — Eval 재정립 (설계 only) ✅ (2026-06-21)

- [x] `eval/v2.1/oracle-protocol.md` (naive 정의·지시문·채점법·kill criterion·캘리브레이션)
- [x] `eval/v2.1/blind-kit-spec.md` (광장별 키트 템플릿·배치·정답키 분리)
- [x] `eval/v2.1/evaluator-registry.md` (회전 레지스트리 초기화)
- [x] kill criterion 방향 수정: `≤60%=PASS` 확정
- [x] `steps/v2.1-01-eval-design.md` 기록

---

### Phase 2 — 분류 ✅ (2026-06-21, Phase 1과 병렬)

- [x] NATEPAN 7,106건 → 6광장 라벨 (키워드 기반 SQL CASE + REGEXP)
- [x] `example_bank.category` 덮어쓰기 (`againspring_dev.example_bank`, 코드 변경 0)
- [x] corpus_item 원상복구 (plaza 태그 472건 제거, remaining_dirty=0)
- [x] `crawl/v2.1-plaza-inventory.md` 인벤토리 (실측 7,106건)
- [x] `steps/v2.1-02-classification.md` 기록
- **thin 광장**: FRIEND(202) · WORK(227) · COUPLE(382) → Phase 3 보강 대상

---

### Phase 3 — 빈약 광장 외과적 보강 ✅ (2026-06-21, commit de108ae5)

- [x] `natepan.py`: `section_name` 파라미터 추가, 연애 섹션→COUPLE, 나머지→OTHER
- [x] 신규 크롤 데이터가 즉시 광장 조건부 RAG에 반영
- [x] 빌드·배포 완료
- **Phase 3 보강 결과**:
  - 크롤 데이터: Phase 1(1,481건) + Phase 3(295건) = **1,776건 누적**
  - 신규 AI 포스트: WORK 2건 + COUPLE 1건 = **3건 생성**
  - `example_bank` 최종 분포: FRIEND 161·WORK 139·COUPLE 323·MARRIED 507·FAMILY 457·OTHER 1,709

---

### Phase 4 — 페르소나↔광장 정렬 + CASUAL_FRAMES ✅ (2026-06-21, commit de108ae5)

- [x] 10개 profile.yml interests 재배분 (WORK 편중 해소)
  - 분포: COUPLE 33·FAMILY 21·FRIEND 17·MARRIED 17·WORK 6·OTHER 6
- [x] CASUAL_FRAMES 전량 갈등-인접 프레임으로 교체 (P17·P11 맥락 불일치 tell 해소)
- [x] 빌드·`lint:docs`·배포 완료

---

### Phase 5~8 — 이후 순차 (각 Phase 완료 후 STATE 갱신)

| Phase | 핵심 | 선행 | 상태 |
|---|---|---|---|
| 5 | baseline 블라인드 (naive ≥3) | Phase 1·4 ✅ · kill criterion ✅ | ❌ FAIL 80% (→Phase 6) |
| 6 | 결정론 다양화 1라운드 | Phase 5 | 🔄 진행 중 |
| 7 | QLoRA 데이터게이트 | Phase 6 | ⏳ 대기 |
| 8 | 최종 판정·출하/피벗 | Phase 7 | ⏳ 대기 |

> **Phase 5 결과**: AI 식별률 80% (3인 평균) → FAIL. Phase 6 결정론 다양화 진행.

---

## Kill Criterion 현황

```
✅ 오너 명시 확정 (2026-06-21)
```
신선 캐주얼 독자(≥3인) 통합 평균 봇 식별률 ≤ **60%** = PASS  
"kill criterion 제안값 그대로 확정" — 임계 ≤60%, 평가자수 ≥3인, 통합 평균.  
**Phase 5 측정 완료 — FAIL 80% (2026-06-21). Phase 6 진행 중.**

---

## NATEPAN 코퍼스 현황 (Phase 3 보강 + thin plaza 재분류 후, 2026-06-21)

| 구분 | 현황 |
|---|---|
| COUPLE POST | **358** |
| MARRIED POST | **539** |
| FRIEND POST | **165** |
| FAMILY POST | **488** |
| WORK POST | **156** |
| OTHER POST | **1,590** |
| **합계** | **3,296** |
| 크롤 누적 | Phase 1(1,481) + Phase 3 보강(295+α) |
| `example_bank` natepan 전체 | **7,106** (POST + COMMENT) |

> thin 광장(FRIEND·WORK) NATEPAN 랭킹 섹션 특성상 300건 목표 미달(165·156). 랭킹 베스트 섹션 = 연애·가족 편중 구조적 한계. Phase 5 진행은 현 RAG 건수로 가능.

---

## 활성 결정

| 결정 | 내용 |
|---|---|
| V2-D01 | NATEPAN·계정·캐주얼bar·QLoRA연기 |
| V2-D02 | v2 kill criterion ≤60% (오너 1인) — CLOSED |
| V2-D03 | v2 Phase 5b PASS (55.6%) — CLOSED |
| **V2-D04** | v2.1 재구성·eval 재정립 (오너 은퇴·제품적합성·6광장) |
| **V2-D05** | v2.1 kill criterion ≤60% naive ≥3인 (오너 확정 대기) |
| **V2-D06** | Phase 5 FAIL 80% → Phase 6 결정론 다양화 T1·T3 1순위 진행 |
| (v1) D-108 | ML COLLECT-only (영구) |

---

## Phase 완료 체인

### v2 (CLOSED)

| Phase | 상태 |
|---|---|
| Phase 0 | ✅ 2026-06-21 |
| Phase 1 | ✅ 2026-06-21 (+1481, 쓰니 661건) |
| Phase 2 | ✅ 2026-06-21 (kit v3·평가 완료 55.6% PASS) |
| Phase 3 | ✅ 2026-06-21 commit 491e4515 |
| Phase 4 | ✅ 2026-06-21 commit a42fba61 |
| Phase 5 | ✅ 2026-06-21 (PASS 5/9=55.6%) |
| Phase 6 | ✅ 2026-06-21 SHIPPED |

### v2.1 (진행 중)

| Phase | 상태 |
|---|---|
| Phase 0 | ✅ 2026-06-21 |
| Phase 1 | ✅ 2026-06-21 |
| Phase 2 | ✅ 2026-06-21 |
| Phase 3 | ✅ 2026-06-21 (commit de108ae5) |
| Phase 4 | ✅ 2026-06-21 (commit de108ae5) |
| Phase 5 | ❌ FAIL 2026-06-21 (80%>60%, →Phase 6) |
| Phase 6~8 | 🔄 Phase 6 진행 중 |
