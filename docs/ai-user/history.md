# AI-User v2 / v2.1 프로젝트 히스토리

다시봄 AI 페르소나 시스템의 계정 단위 현실성(realism) 개선 프로젝트. 글의 "AI다움" 신호(tells)를 제거하고, 계정 타임라인을 캐주얼 독자 관점에서 구별 불가능하게 만들기 위한 2단계 노력.

---

## v2: 계정 메모리 + Cadence 도입 (CLOSED, 2026-06-21)

**목표**: 메시지 단위에서 계정 단위로 평가 기준 전환. 캐주얼 독자가 글 타임라인을 봤을 때 봇으로 의심하지 않는 수준.

**결과**:
- 블라인드 식별률: 88.9% → 55.6% (−33.3pp)
- 평가: 5/9 PASS (오너 기준)
- 산출: `SELF_CRITIQUE_EXTRA_CLICHES` 35종 tell 시그니처

**주요 개선**:
1. **Phase 3 — 계정 히스토리 메모리** (`life_state.json`, 진행 중인 사건 추적)
2. **Phase 4 — 응답 Cadence** (딜레이, 대댓글 깊이, 활동 패턴)
3. **Phase 5 — Named-tell 필터** (결정론적 후처리, 생성기 개선 없음)

**측정 착시 2개**:
- (a) 오너 1인 평가 = 캐주얼 독자 대표성 미검증 + 라운드 학습 오염
- (b) PASS한 글도 tell이 완전 제거된 게 아니라 길이·구체성에 묻힘

**상세**: `.result/ai-user-v2/lessons.md`

---

## v2.1: 광장 정렬 + 신선 평가자 오라클 (SHIPPED, 2026-06-22)

v2의 착시를 수정하기 위해 평가 방법론과 최적화 목표를 완전히 재구성.

### 핵심 개선

| 영역 | v2 | v2.1 |
|---|---|---|
| **평가자** | 오너 1인 (라운드 간 학습 오염) | 신선 캐주얼 독자 ≥3인 (블라인드) |
| **평가 단위** | 계정 타임라인 (v1 메시지→계정 전환) | 계정 타임라인 (광장별) |
| **최적화 목표** | 가장 사람 같은 텍스트 선택 (tell 제거) | 광장별 문맥(COUPLE·MARRIED·FRIEND·FAMILY·WORK·OTHER) 정렬 = 제품 적합성 |
| **오라클** | 사람 블라인드 (proxy 사다리는 v1에서 폐기) | 사람 블라인드 (동일, 신선 패널) |
| **Kill Criterion** | ≤60% 봇 식별률 (오너 1인) | ≤60% 봇 식별률 (신선 ≥3인 평균) |

### Phase 0~8 완료 체인

| Phase | 날짜 | 핵심 | 결과 |
|---|---|---|---|
| 0 | 2026-06-21 | 창립·방법론 동결·kill criterion 등록 | ✅ |
| 1 | 2026-06-21 | 신선 평가자 블라인드 키트 설계 | ✅ |
| 2 | 2026-06-21 | NATEPAN 7,106건 → 6광장 분류 | ✅ |
| 3 | 2026-06-21 | 빈약 광장(FRIEND·WORK) 외과적 보강 | ✅ |
| 4 | 2026-06-21 | 페르소나↔광장 정렬 + CASUAL 프레임 재구성 | ✅ |
| 5 | 2026-06-21 | Baseline 블라인드 (3인 평가) | ❌ FAIL 80% |
| 6 | 2026-06-21 | VARIETY_SEEDS 다양화 + CATEGORY_GUIDE (prod 배포) | ✅ |
| 7 | 2026-06-21 | QLoRA 데이터게이트 | ❌ 비발동 (조건3 미충족) |
| **8** | **2026-06-22** | **최종 판정·검증** | **✅ PASSED** |

### Phase 8 최종 결과 (2026-06-22)

**신선 평가자 4인** (최일찬·김태준·김윤태·윤도현) **통합 평균**:
- **AI 식별률 20% ≤ 60% → PASS (절대 임계 충족)**
- 독립 재채점 일치: 40/0/0/40 (100% 무오류)
- Human 오탐률: 55% (역전 신호 = 구별 불가)

**생성 코드 반영 시점**: Phase 6 (orchestrator, backend 이미 prod 배포)
- 출하 = 측정 + 봉인 (재배포 불필요)
- 절대규칙 #4 게이트 검증: `dev:8090` e2e-realbe 전체 PASS · build · BE test green

**ML 비활성화 유지**: `AI_USER_ML_ENABLED=false` (영구, v1 D-108 COLLECT-only)

---

## v2.1+ 보강 (post-SHIP, 2026-06-22)

### 코퍼스 정제

**thin 광장 보강 시도**:
- dcinside·Blind robots.txt ClaudeBot 차단 → 제외
- OTHER/freeboard 키워드 마이닝 → 노이즈 (dry-run 입증) → ABORT
- NATEPAN 테마 채널 발견 (`/talk/c20019 회사생활`) → WORK 채널 크롤 + plaza_classifier 정밀 게이트

**Cross-run 중복 제거**:
- `example_bank` unique index 부재 → 일일 크롤 재삽입 유도
- 코퍼스 44~51% 중복 (정확 url+type+content)
- Cross-run dedup guard 도입 → 차단

**정제 결과**:
- dev: −5,583건 (중복 제거)
- prod: −6,697건 (44%), 0 잔존
- prod ai-learning 배포 완료 (절대규칙 #4)

---

## 핵심 교훈

(`lessons.md` 전문 참조)

1. **게이트 판정(절대 임계)과 인과 주장(상대 비교)을 분리하라.**
   - Phase 5 FAIL(80%) → Phase 8 PASS(20%) −60pp는 5개 변수 동시변화로, Phase 6 기여는 시사적·미증명.
   - PASS 판정(≤60%)은 절대 임계라 유효. 인과 설명은 보조 분석.

2. **AI 식별률만 보지 말고 Human 오탐률을 대조하라.**
   - v2.1 Phase 8: AI 20% vs Human 55% 역전
   - 역전 = 평가자 간 구별 불가 = 목표 달성(다만 AI는 더 '캐주얼'로 오인)

3. **코퍼스 두께가 광장별 위장 성공과 상관.**
   - thin FRIEND(165)·WORK(156) vs 충실 FAMILY(488)·MARRIED(539)
   - 고도 캐주얼 전략은 데이터 풍부도(depth) 필수

4. **측정 동결로 "측정한 것 == 출하한 것"을 보장.**
   - Phase 5 블라인드 후 코드 고정
   - Phase 8 재측정 = 게이트 검증 (변수 동결)

---

## 상세 산출물

프로젝트 전체 상세는 `.result/ai-user-v2/` 참조:

| 자료 | 경로 | 용도 |
|---|---|---|
| 방법론·규율 | `charter-v2.1.md` | v2.1 규율 8조, 핵심 코드훅 |
| 진행 상황 | `STATE.md` | 라이브 포인터 (매 세션 갱신) |
| 의사결정 | `decisions.md` | V2-D01~D07 결정 기록 |
| 교훈 분석 | `lessons.md` | 레버별 효과·탐지 tell 분석·재확인 |
| 로드맵 | `roadmap.md` | v1~v2~v2.1 전 이력 |
| Phase별 기록 | `steps/v2.1-*.md` | 각 Phase 스냅샷 (00~09) |
| 평가 오라클 | `eval/v2.1/*.md` | 신선 평가자 프로토콜·키트·가이드 |

### Phase 5 Baseline & Phase 8 최종 평가

- `eval/v2.1/oracle-protocol.md` — 신선 독자 정의·지시문·채점법
- `eval/v2.1/blind-kit-spec.md` — 광장별 블라인드 키트 템플릿
- `eval/v2.1/phase8/v2.1-phase8-01-analysis.md` — Phase 8 최종 분석
- `eval/v2.1/phase8/scoring-calculator.html` — 자동 채점기
- `eval/v2.1/phase8/v2.1-phase8-01-evaluator.html` — 평가자용 인터페이스

---

## 전략적 영향

- **단위 = 계정 타임라인** (메시지→계정 전환) 및 **평가자 = 신선 블라인드** 방법론은 다른 AI 페르소나 시스템에 직접 전이 가능.
- **후처리 필터 + cadence + 메모리** = 생성기 파인튜닝 없이 humanness 개선 (토큰·학습 비용 0)
- **광장 정렬** = 제품 적합성(plaza 문맥) 레버로, 탐지 회피와는 별개 (시스템 안전)
- v1 D-108 COLLECT-only·ML 비활성화 유지 = 측정·운영 안정성
