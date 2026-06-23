# AI-User v2 / v2.1 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-23 (v2.1 Phase 8 **SHIPPED ✅** 유지 — 출하 4인 20%, 최신 9인 46.7% PASS)

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
> - **kill criterion 사전 등록·확정 완료**: ≤60% naive ≥3인, 통합 평균.

---

## 현재 위치

### Phase 0 — 창립 & 동결 ✅ (2026-06-21)

- [x] `roadmap.md` v2 CLOSED 표기 + v2.1 섹션 추가
- [x] `charter-v2.1.md` 창립 (재구성 3명제·규율·코드훅 동결)
- [x] `decisions.md` V2-D04(재구성) + V2-D05(kill criterion 사전 등록) 추가
- [x] `STATE.md` v2.1 라이브 포인터 초기화
- [x] `steps/v2.1-00-charter.md` 기록
- [x] `steps/v2.1-00-charter-reaudit.md` 기록 (2026-06-22, 연표 보존 재검증)
- [x] `npm run lint:docs` exit 0 통과
- [🔴] **Phase 0 당시 kill criterion 오너 명시 확정 대기** (제안 ≤60% naive ≥3인 사용 중. Phase 5 측정 전 필수.)
- [x] 후속 정합성 메모: 위 pending 항목은 Phase 0 당시 상태이며, 같은 날 `V2-D05`/`Kill Criterion 현황`에서 확정됨
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
| 6 | 결정론 다양화 1라운드 | Phase 5 | ✅ 완료 2026-06-21 (VARIETY_SEEDS·CATEGORY_GUIDE·prod 배포) |
| 7 | QLoRA 데이터게이트 | Phase 6 | ❌ 비발동 (조건3 미충족 FRIEND/WORK <수백) |
| 8 | 최종 판정·출하/피벗 | Phase 7 | ✅ **SHIPPED** — 출하 시점 4인 20%, E-008·E-009·E-010·E-011·E-012 포함 최신 9인 46.7% ≤ 60% PASS(2026-06-22). 생성코드는 Phase 6서 이미 prod 반영 → 출하=봉인+게이트검증. e2e/build/test green |

> **Phase 5 결과**: AI 식별률 80% (3인 평균) → FAIL. Phase 6 결정론 다양화 진행.

---

## Kill Criterion 현황

```
✅ 오너 명시 확정 (2026-06-21)
```
신선 캐주얼 독자(≥3인) 통합 평균 봇 식별률 ≤ **60%** = PASS  
"kill criterion 제안값 그대로 확정" — 임계 ≤60%, 평가자수 ≥3인, 통합 평균.  
**Phase 5 측정 완료 — FAIL 80% (2026-06-21). Phase 6 완료(VARIETY_SEEDS·CATEGORY_GUIDE·prod), Phase 7 비발동(조건3). Phase 8 측정 — ✅ SHIPPED: naive 4인 평균 AI 식별률 20% ≤ 60% PASS 최종 확정·출하 (2026-06-22, 출하 결정 시점). 독립 재채점 일치(40/0/0/40). 추가 평가자 E-008(쎄오일시)·E-009(이한별)·E-010(곽평안)·E-011(이태훈)·E-012(박진우) 포함 최신 9인 평균 46.7% — PASS 유지(≤60%). 출하 = 절대규칙 #4 게이트 검증(dev health UP·e2e-realbe·build·BE test green) + 결정 봉인. 생성 코드는 Phase 6 시점에 이미 prod 반영(orchestrator/backend Up since Phase 6) → 측정==출하, 재배포 불필요. `AI_USER_ML_ENABLED=false` 유지. 상세 분석: `eval/v2.1/phase8/v2.1-phase8-01-analysis.md`. ⚠️ 인과 주의: Phase5(80%)→Phase8 최신 9인(46.7%)도 PASS지만, 변화량 해석은 5개 변수 동시변화로 "Phase 6 기여" 시사·미증명(L-P8-02). 게이트 PASS는 절대 임계라 유효.**

### Phase 8 준비 자산 (eval/v2.1/phase8/, steps/)
- `v2.1-phase8-01-evaluator.html` — 평가자용(자동저장·복사폴백·공유, node --check PASS·이모지/담당자 정리 완료)
- `scoring-calculator.html` — 채점 자동화(ANSWER_KEY=A-A-H-A-H-H-A-H-H-A·AI_QUESTIONS 1·2·4·7·10 검증)
- `v2.1-phase8-01-results.md` — 응답 수집·채점·게이트 판정 스캐폴드(answer-key 사전 기입, PASS→08b/FAIL→08d 분기)
- `owner-runbook.md` — 모집→판정 6단계 런북 / `evaluator-guide.md` — 모집 가이드
- `kit-integrity-check.md` — 정합성+금지어 PASS / `spec-compliance-check.md` — spec 준수 PASS
- `steps/v2.1-08c-...md` — Phase 6 tell 빈도 관찰(위험계정 057·035) / `08d-...md` — FAIL 3옵션 / `08b-...md` — 출하 체크리스트

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
| **V2-D05** | v2.1 kill criterion ≤60% naive ≥3인 (2026-06-21 확정) |
| **V2-D06** | Phase 5 FAIL 80% → Phase 6 결정론 다양화 T1·T3 1순위 진행 |
| **V2-D07** | Phase 7 QLoRA 비발동 (조건3 미충족) → Phase 8 직행 |
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

### v2.1 (SHIPPED + post-ship tuning)

| Phase | 상태 |
|---|---|
| Phase 0 | ✅ 2026-06-21 |
| Phase 1 | ✅ 2026-06-21 |
| Phase 2 | ✅ 2026-06-21 |
| Phase 3 | ✅ 2026-06-21 (commit de108ae5) |
| Phase 4 | ✅ 2026-06-21 (commit de108ae5) |
| Phase 5 | ❌ FAIL 2026-06-21 (80%>60%, →Phase 6) |
| Phase 6 | ✅ 완료 2026-06-21 (4bc7c0cf) |
| Phase 7 | ❌ QLoRA 비발동 2026-06-21 |
| Phase 8 | ✅ SHIPPED 2026-06-22 (출하 4인 20% PASS, 최신 9인 46.7% PASS 유지) |

---

## v2.1 post-ship 정교화 — T5·T6·T7 레버 튜닝 ✅ (2026-06-22, commit 8c84b58f)

> Phase 8 출하(20% PASS) 이후 **잔존 tell 감소** 작업. 설문 없이 스타일 분포 매칭 신호.

### A. 오타·슬랭 레버 튜닝 (T6·T7) ✅

| 변경 | 내용 | 대상 |
|---|---|---|
| THEQOO typoProb | 0.30 → 0.55 (실효율 0.18→0.385) | T6 과교정문법 |
| BLIND typoProb | 0.45 → 0.55 | T6 과교정문법 |
| NATEPAN chosungInject | false → true + `{ㅠㅠ,ㅋㅋ,ㄹㅇ,헐}` (fleet 16개) | T7 슬랭부재 |
| GENERAL chosungInject | false → true + `{ㅋㅋ,ㅠㅠ,ㅇㅇ,ㄹㅇ}` | T7 슬랭부재 |

### B. 어휘이질 필터 — B2 캘리브레이션 실패 + 폴백 (T5) ✅

- **B2 캘리브레이션 실패**: human rare-ratio 0.441 > AI 0.282 — 인간이 AI보다 희귀어 사용 多
- **θ=0.30 기준 human FP = 77.9%** → 빈도기반 탐지 폐기
- **폴백**: 문어체 denylist 13종 → `SELF_CRITIQUE_EXTRA_CLICHES`: `방증,여실히,함의,귀결,개탄,단언컨대,요컨대,결론적으로,시사하는,기인하,고찰,도출,엄연한`
- `SelfCritiqueService` rare-vocab detector #12 다크 출시 (`ENABLED=false`)

### 분포 측정 기준선 (A0, measure_style_distribution.py)

| 지표 | 인간 | AI | 비고 |
|---|---|---|---|
| 슬랭토큰 포함 비율 | 19.9% | 44.3% | DCINSIDE 등 고슬랭 voice 지배 |
| 초성 포함 비율 | 20.2% | 33.7% | |
| 종결어미 다양도 | 0.64 | 1.66 | AI가 과다 다양화 → 후속 모니터링 |

### 현재 상태

- **dev/prod 배포 완료** (commit 8c84b58f, e2e-realbe 148 PASS)
- T6·T7은 코드 레벨에서 직접 해소됨. T5 폴백(denylist)은 recall 낮으나 즉각 효과.
- **잔존 과제(v3)**: T8 비응집(QLoRA 영역), T5 심층 수정(re-calibration), thin plaza 보강(FRIEND/WORK).
