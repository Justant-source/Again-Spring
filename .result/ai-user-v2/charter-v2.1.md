# AI-User v2.1 — Charter (광장 정렬)

> **라운드**: v2.1 "Plaza-Alignment"  
> **시작일**: 2026-06-21  
> **전임**: AI-User v2 (CLOSED 2026-06-21, `roadmap.md` v2 섹션)  
> **창립 진단**: v2 `lessons.md` + `blind_kit_v3_key.md` 측정 착시 분석

---

## 재구성 3명제 (동결 — 재논의 금지)

### 명제 A. 오너 = 게이트 평가자 은퇴, 캘리브레이션 전용

v2 평가자는 오너 1인이었다. 오너는 캐주얼 독자의 정반대(봇헌터 수준 패턴 인식 + 라운드 간 기억 오염). v2 55.6% PASS는 **캐주얼 독자 타깃을 검증하지 못한 수치**다.

**결정**: 신선 캐주얼 독자 ≥3인 패널 = 게이트 평가자. 오너는 동일 키트를 **별도** 채점(캘리브레이션, 게이트 아님). 패널 회전 레지스트리(`eval/v2.1/evaluator-registry.md`)로 기억 방지.

### 명제 B. 카테고리 정렬 = 제품 적합성 (회피는 퇴행 금지 제약만)

v2 PASS한 4개(P3·P5·P13·P15)는 tell이 제거된 게 아니라 길이·구체성·슬랭으로 **묻혔을 뿐**. 회피 추격을 계속하면 whack-a-mole이다.

다시봄은 6 광장(PostCategory: COUPLE·MARRIED·FRIEND·FAMILY·WORK·OTHER) 피드 구조다. AI 사연을 광장에 정렬하면:
- 각 광장 피드가 주제 일관 (제품 가치)  
- 맥락 불일치 tell 소멸 = 부산물
- "퇴행 금지": 회피 수준이 캐주얼 bar 거의 충족 추정이므로 **더 추격하지 않는다**

**결정**: 카테고리 정렬은 "제품 적합성" 프레임으로. 회피 정당화 금지.

### 명제 C. 타깃 = 다시봄 6 광장 `PostCategory` (taxonomy 고정)

```
COUPLE (연인) · MARRIED (부부) · FRIEND (친구) · FAMILY (가족) · WORK (직장) · OTHER (기타)
```

`backend/.../domain/enums/PostCategory.java:12-18` 권위본. 분류·생성·RAG 모두 이 6개로만.

> ⚠️ 금지: `RelationType`(parent_child·korean_specific 포함 7개) / `CATEGORIES` 3-레벨 카탈로그 — 이쪽에 정렬 금지.

---

## 성공 기준

> **"광장별 계정 타임라인 전체를, 신선 캐주얼 독자 ≥3인이 블라인드로 봤을 때 봇으로 안 보는 수준"**

- **타깃 독자**: 봇헌터 아닌 **신선 캐주얼 일반 독자** (포렌식 ×), ≥3인 합의
- **평가 단위**: **광장별 계정 타임라인** (글 1개 ×)
- **공동체**: **NATEPAN 전용** (변수 고정)
- **오라클 단일**: 사람 블라인드 1개. proxy/MAUVE/LLM-judge = humanness 판정 금지

---

## Kill Criterion (v2.1)

```
✅ 등록: 2026-06-21 Phase 0 (사전 등록 — 측정 전)

신선 캐주얼 독자 ≥3인, 광장별 계정 타임라인 블라인드에서
평균 봇 식별률 ≤ 60% = PASS

→ PASS : 광장 정렬 레버 prod 출하
→ FAIL  : QLoRA 데이터게이트 평가 or 품질-피벗 (옵션 제시까지만, 오너 결정)
```

**오너 확정 대기 항목**: 임계값(제안 ≤60% v2 승계) · 평가자수(제안 ≥3인) · 광장별 vs 통합 평균. 오너 명시 확정 전 어떤 humanness PASS/FAIL 판정도 금지.

---

## v2.1 표준 규율 (R1~R8 승계 + v2.1 구체)

| # | 규율 | v2.1 구체 |
|---|---|---|
| R1 | **단위 = 광장별 계정 타임라인** | 6 광장 각각이 계정 단위 |
| R2 | **proxy 사다리 금지** | 분포 sanity(카테고리 적합성)는 허용, humanness 판정 금지 |
| R3 | **변수 고정** | NATEPAN 전용. 측정 1회당 변수 1개 |
| R4 | **저빈도 고정보 eval** | baseline(Ph5) 1회 + 최종(Ph8) 1회 = 2회. named-tell 라벨셋 |
| R5 | **kill criterion 사전 등록** | 위 등록 완료(오너 확정 대기) |
| R6 | **판별기 = QA만** | rerank OFF. `AiUserMlClient.java:174` 미변경 |
| R7 | **v1 제약 승계** | `AI_USER_ML_ENABLED=false` 영구. D-108 COLLECT-only. 레버 보존 |
| R8 | **main 단일·docs-as-code·prod 게이트** | 절대규칙 #4·#8·#9 |

**Anti-pattern (금지)**:
- 오너 게이트 평가 / whack-a-mole / 카테고리 정렬을 회피로 정당화
- 6 새 크롤러 신설(기존 NATEPAN 분류 먼저)
- QLoRA 조건 미충족 발동
- proxy/MAUVE/LLM-judge 부활

---

## 핵심 코드 훅 (v2 승계 + v2.1 추가)

| 레버 | 위치 | v2.1 역할 |
|---|---|---|
| 광장 enum | `backend/.../PostCategory.java:12-18` | 분류·생성·RAG taxonomy 고정 |
| 카테고리 고정 | `ActionExecutor.topCategory`(:1506-1513), `profile.yml interests` | 페르소나↔광장 = config 재배분 |
| RAG 검색 | `examples.py:87-185`(3단 폴백) · `AiLearningClient.findSimilar`(:154-167) | **category='talk'→6광장 덮어쓰기로 stage-1 활성화** |
| 분류기 | `EmbeddingService.embed_batch`(`embedding.py:31`) · `topic_synthesizer._synthesize_with_llm`(`:20`) | Phase 2 분류, GPU 바운드 |
| 크롤러 섹션 | `natepan.py:STATIC_SECTIONS`(:23-33), `_parse_post_detail`(:147) | Phase 3 섹션→광장 힌트 보존 |
| CASUAL 프레임 | `CASUAL_FRAMES`(`ActionExecutor.java:868-879`) · `computeCasualProb`(:1230-1235) | Phase 4: 프레임 내용 정렬(확률 25% 불변) |
| tell 후처리 | `OutputSanitizer.java` · `SelfCritiqueService.java:32-37` | Phase 6: 종결·감정 다양화 |
| 동결 | `ActionExecutor.java:427` · `AiUserMlClient.java:174` | R6/R7 — 절대 미변경 |

---

## Phase 요약

| Phase | 핵심 | GPU | 위치 |
|---|---|---|---|
| 0 | 창립·동결·kill criterion | 0 | 로컬 |
| 1 | Eval 오라클 재정립 (설계 only) | 0 | 로컬 |
| 2 | NATEPAN 7,106건 6광장 분류 | **집약** | WSL 16 |
| 3 | thin 광장 외과 보강 | 중 | WSL 16 |
| 4 | 페르소나↔광장 정렬 + RAG | 낮 | 로컬 8 |
| 5 | baseline 블라인드 (naive ≥3) | 0 | 로컬+인간 |
| 6 | 결정론 다양화 1라운드 | 0 | 로컬 8 |
| 7 | QLoRA 데이터게이트 판정 | **(조건부) 집약** | WSL 16 |
| 8 | 최종 판정·출하/피벗·봉인 | 0 | 로컬+인간 |

---

**마지막 갱신**: 2026-06-21 Phase 0 창립
