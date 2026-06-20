# AI-User Project Closeout

> **상태**: CLOSED (2026-06-21)  
> **프로젝트**: 다시봄 AI 유저 시뮬레이션 — ML reranking 활성화 추진  
> **종료 결정**: D-107 (2026-06-21) — Best-of-N reranking 영구 폐기, 성공 재정의

---

## (a) 출하된 것 (Shipped)

| 레버 | 코드 위치 | 효과 | prod 반영일 |
|---|---|---|---|
| **결정론적 오타 주입 (Track A)** | `OutputSanitizer.injectTypos()` `:263–291` (T1~T8) | 절반의 글에 1~2개 자연스러운 오타 삽입 → AI 티 감소 | 2026-06-21 (이미지 재빌드) |
| **THEQOO 후처리 cleanup** | `OutputSanitizer.cleanupTheqoo()` `:210–224` | 12개 변환: 이모지 제거·`…`정규화·`헐`/`개공감` 제거·middot 교정 | 2026-06-21 |
| **CASUAL 25% 분기 (Track B)** | `ActionExecutor.java:346` `casual = RNG.nextDouble() < 0.25` | 글 4개 중 1개는 갈등 없는 일상 주제 → 주제 다양화 | 2026-06-21 |
| **SELF_CRITIQUE_EXTRA_CLICHES** | `.env.dev:86` | "이번달만 세 번째" 등 탐지 클리셰 7개 자기비판 필터 추가 | 2026-06-20 (.env 설정) |

**공통 특성**: 코드 변경 없음, 이미지 재빌드만으로 반영. `AI_USER_ML_ENABLED=false` 영구 유지.

---

## (b) 아카이브된 것 (Archived)

| 항목 | 이유 |
|---|---|
| **Best-of-N ML reranking** (KcELECTRA + KatFishNet stacking, `AiUserMlClient`) | r16: ML reranking proxy=0.283 > baseline=0.150 → 역효과 (D-105) |
| **Best-of-N rule-based reranking** (self-critique 점수 기반) | r17: rule reranking proxy=0.317 > baseline=0.150 → 역효과 (D-106) |
| **Best-of-N 선택 로직** (`ActionExecutor.java:427–456`) | gate 코드는 유지, `AI_USER_ML_ENABLED=false`로 비활성 |
| **THEQOO discriminator 갈등글 특화 재학습** (AUC=0.9976) | 갈등글 특화 후에도 proxy 개선 없음 (Step 91) |
| **cond4 MAUVE A-B Δ>0 게이트** | D-68: MAUVE 포화 (0.97~1.0) → Δ→0, 게이트 무력화 |
| **cond5 LLM proxy 활성화 게이트** | D-101: gap_hi=0.54 보수 상한 → proxy 단독 판정 불가 |
| **5조건 활성화 파이프라인 (D-17)** | Goal B out-of-scope → 파이프라인 중단 |
| **NATEPAN/CLIEN 활성화 추진** | cond4 FAIL (NATEPAN -0.1048, CLIEN -0.0436) → 재시도 없음 |

---

## (c) D-106 — Negative Result

**결론**: Best-of-N reranking은 생성 품질을 **저하**시킨다.

| 지표 | 값 | 의미 |
|---|---|---|
| r15 baseline (단일 draft, ML 없음) | proxy=**0.150** | 기준점 |
| r16 ML reranking (Best-of-4 + discriminator) | proxy=**0.283** | baseline 대비 +0.133 (탐지율 상승) |
| r17 rule-based reranking (Best-of-4 + 자기비판) | proxy=**0.317** | baseline 대비 +0.167 (탐지율 상승) |

**근본 원인**: Best-of-4 선택 편향. ML 모델은 가장 formal한 초안을 선택 (human-like로 잘못 분류), rule 점수는 가장 casual한 초안을 선택. 두 경우 모두 극단 초안 → Claude judge가 AI로 쉽게 탐지. 탐지 신호는 formality가 아닌 **내러티브 구조·어휘 일관성**에서 발생.

---

## (d) 교훈 (Lessons Learned)

### 1. MAUVE 포화 — cond4 무력화
- MAUVE는 분포 유사도를 측정. 충분한 학습 후 모든 커뮤니티에서 0.97~1.0으로 수렴.
- 포화 상태에서 Δ = A군 - B군 은 노이즈 범위 안. 더 이상 정보가 없는 지표.
- **교훈**: 포화 지표는 의사결정 기준에서 조기 제거해야 한다. 대안: 문체 Tell 탐지율로 직접 측정.

### 2. Proxy↔Human 갭 — cond5 불확실성
- r9 (Haiku): gap = -48pp (proxy < human 정확도). r14 (Codex): gap = +54pp (proxy > human).
- Era/모델 간 갭 방향이 반대 → 단순 오프셋 보정 불가.
- **교훈**: LLM-as-judge는 인간 평가의 대리 지표로 신뢰할 수 없다. proxy PASS ≠ human PASS. 수용 판단은 인간 평가로만 해야 한다.

### 3. Goal A·B 혼동이 활성화 추진을 장기화
- 초기 목표: Goal B (계정 누적 = 구별 불가). 측정 결과 r15 proxy=0.150으로 Goal A (글 1개 = 사람 같음)는 사실상 달성.
- "Goal A 달성했는데 Goal B를 향해 계속 최적화"가 수십 스텝의 낭비 원인.
- **교훈**: "이 글 혼자 읽으면 사람 같은가"와 "이 계정 전체를 보면 봇처럼 보이는가"는 **다른 문제**. 둘을 혼용하지 말 것. Goal B는 메시지 단위 생성 개선으로 해결되지 않는다 (계정 구조, 활동 패턴, 사회적 그래프 수준의 문제).

### 4. Best-of-N 선택 편향 — reranker 설계 anti-pattern
- Best-of-N은 점수가 높은 **극단** 후보를 선택. 극단 = 판별하기 쉬운 특성.
- ML discriminator: highest P(human) → 가장 formal. Rule: highest naturalness → 가장 슬랭.
- 단일 draft 무작위 선택이 Best-of-4 선택보다 자연스러운 아이러니.
- **교훈**: 생성 품질 개선은 단일 draft 품질 강화가 우선. reranker는 draft 품질이 균일해진 뒤 적용해야 효과가 있다.

---

## ML 서비스 장기 처리 — 결정 옵션 (D-108)

> 아래 두 옵션 중 선택은 **사용자 결정 후 D-108에 append**. 현재 closeout 문서는 옵션 제시까지만.

| | **옵션 A — COLLECT-only monitor 유지** | **옵션 B — decommission (정지)** |
|---|---|---|
| **비용** | WSL(100.115.252.61) 컨테이너 상시 가동 + 코퍼스 누적 | 재개 시 ML 서비스 재구축 필요 |
| **편익** | 향후 재개 시 데이터 연속성 (example_bank 누적 유지) | WSL GPU·메모리 해제 + 운영 표면 축소 |
| **현재 상태** | `AI_USER_ML_COLLECT=true` (dev) / `false` (prod default) | `AI_USER_ML_ENABLED=false` 이미 비활성 |
| **추천 시나리오** | Goal B를 향후 재개할 가능성이 있음 | Goal B를 완전히 포기하고 운영 단순화 |

**D-108 작성 방법**: `decisions.md`에 `## D-108 (날짜) — ML 서비스 decommission 결정` 으로 append.

---

## 최종 상태

- **`AI_USER_ENABLED`**: false (dev) / false (prod default) — AI 유저 시뮬레이션 비활성
- **`AI_USER_ML_ENABLED`**: false (영구) — ML reranking 비활성
- **`AI_USER_ML_COLLECT`**: true (dev, COLLECT-only) — 데이터 수집만
- **Phase 2 blind eval**: 자료 준비 완료 → 오너 + 친구 1~2인 평가 대기 (`.result/ai-user/phase2-blind-eval/`)
- **STATE.md**: CLOSED
