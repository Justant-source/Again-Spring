# AI-User v3 — Decisions (의사결정 로그)

> **라운드**: v3 "Style Convergence & Generation Quality Leap"  
> **생성일**: 2026-06-22  
> **장소**: GitHub Issues / Slack (예상)

---

## V3-D01: v3 Scope 확정

**날짜**: 2026-06-22 (Phase 0)  
**결정**: v3 = T8(비응집) + Thin plaza + Comment 품질 3-트랙  
**승인**: 예상 (오너 회신 대기)

### 논의 배경

v2.1 SHIPPED (2026-06-22, 5인 평가 28% PASS) 이후,  
post-ship 튜닝(T6/T7/T5)이 단문 필터 범위 내에서 최적화되었음.  
추가 진전은 다음 3개 축이 필요:

1. **T8 비응집** (감정·사건 응집성) — 구조적·생성 모델 레벨
2. **Thin plaza 부족** (FRIEND 165 → 400, WORK 156 → 400) — 코퍼스 3-4배
3. **Comment 미측정** (POST만 평가) — 혼합 평가 first time

### 결정 내용

| 항목 | v2.1 | v3 | 비고 |
|---|---|---|---|
| **주요 레버** | 카테고리 정렬 | T8 + Thin plaza + Comment | 모두 구현 |
| **모델 개입** | 프롬프트·필터 | QLoRA fine-tuning | Haiku 4.5 |
| **코퍼스** | NATEPAN 5K+ | NATEPAN + 4개 신규 | Phase 1 |
| **평가 키트** | POST only | **POST + COMMENT mixed** | 혼합 지시문 |
| **평가자** | ≥3인 | **≥4인** | 강화 |
| **Kill criterion** | ≤60% | **≤25%** | 강화 제안 |

### 승인 조건

- ✅ charter-v3.md 작성 완료
- ✅ roadmap.md 상세 작성 완료
- 🔴 오너 kill criterion ≤25% **수치 확정** 필수
- 🔴 오너 평가자 ≥4인 **최종 확정** 필수

### 위험도

**Low**: 기술적 타당성 확보(QLoRA Haiku 사례 있음, WSL GPU 충분)  
**Medium**: 평가자 모집 기한(4주 소요 가능)  
**High**: Prod 배포 gate (e2e-realbe 완전 통과 필수, Phase 6~7 직렬)

---

## V3-D02: QLoRA 데이터게이트 3-AND 조건 수치화

**날짜**: 2026-06-22 (Phase 0 최종)  
**결정**: Phase 3→4 전환 시 3개 조건 **모두** 만족해야만 진행  
**승인**: 예상 (charter-v3.md에 기술됨)

### 조건 명세

#### 조건 1: Thin plaza 보강 완료 (Phase 1 Gate)
```
FRIEND clean count ≥ 400건
WORK clean count ≥ 400건
기타 광장 회귀 < ±20% (이전 대비)
```
**시간**: Phase 1 완료 = 약 일주일  
**책임**: 개발자  
**증거**: crawl/phase1-report.md

#### 조건 2: Comment eval 분석 → T8이 주요 원인 확인 (Phase 2 Gate)
```
Post-only 식별률 vs. Comment-mixed 식별률
차이 < 10pp → T8(또는 다른 구조) 주요 원인
차이 ≥ 10pp → Comment 품질이 추가 killer → Phase 3 데이터 필요 없음, Phase 6 진행
```
**시간**: Phase 2 분석 = 약 2주  
**책임**: 평가자(채점) + 개발자(분석)  
**증거**: eval/phase2/comment-eval-v1.md

#### 조건 3: SFT 고품질 데이터 확보 ≥500건 (Phase 3 Gate)
```
응집성 점수 ≥0.7인 데이터만 선별 (자동 + 수동)
구체적 사건(trigger) ≥95% 포함
감정 명시 ≥90% 포함
광장별 분포: FRIEND/WORK ≥20%, 기타 ≤30%
```
**시간**: Phase 3 준비 = 약 2주  
**책임**: 개발자  
**증거**: ai-user/learning/data/sft_dataset_v1.jsonl + metadata.json

### 3-AND Logic (모두 충족 필수)
```
Phase 4 진행 = 
  (Phase 1 PASS: FRIEND≥400 AND WORK≥400) AND
  (Phase 2 PASS: diff<10pp) AND
  (Phase 3 PASS: data≥500건)

하나라도 FAIL → Phase 4 연기 or 오너 결정
```

### 의도

v2.1 "quick-win" 3개 레버(T5/T6/T7)에서  
phase-gate 조건이 느슨했고(S1~S5 선택적),  
결국 재학습·재측정 반복을 낳았다.  
v3는 **조건을 사전 수치화**하여 amber/green 상태를 객관화.

### 변경 이력

없음 (초기)

---

## V3-D03: Kill Criterion ≤25% 오너 사전 등록 (필수)

**날짜**: 2026-06-22 (Phase 0)  
**결정**: 📋 **오너 회신 대기**  
**제안**:
```
신선 캐주얼 독자 ≥4인, 광장별 계정 타임라인 블라인드에서
(FRIEND·WORK 광장 필수 포함, POST 3 + COMMENT 3 per 계정)

평균 봇 식별률 ≤ 25% = PASS

→ PASS: QLoRA 어댑터 + thin plaza + comment 가이드 prod 배포
→ FAIL: QLoRA 재학습 or 추가 필터 옵션 제시 (오너 결정)
```

### 근거

| 기준 | v2.1 | v3 |
|---|---|---|
| **식별률 기준** | ≤60% | **≤25%** |
| **이유** | 카테고리 정렬만 | 카테고리+T8+thin plaza+comment |
| **신뢰도** | 3인 합의 | **4인 합의** |
| **키트** | 27 unit (POST only) | **30 unit (POST+COMMENT)** |

### v2.1과의 차이

**v2.1**: 광장 정렬이 30% 자동 해결 → ≤60% PASS는 합리적  
**v3**: T8(QLoRA) + thin plaza(3배) + comment 필수 포함 → ≤25%는 보수적(realistic) 기준

### 위험 평가

- **낙관**: ≤20% 달성 가능 (v2.1 post-ship이 이미 작은 개선이라도 효과 있었으므로)
- **현실**: ≤25% 달성 가능 (QLoRA T8 개선 ≥15pp 가정)
- **비관**: >30% 가능성 (T8 개선 미흡 시) → 재학습 필요

**제안: 오너 확정 필수 — 측정 전에** (감정 편향 차단)

### 변경 이력

없음 (초기, 오너 승인 대기)

---

## V3-D04: v2.1 Lessons → v3 적용 체크리스트

**날짜**: 2026-06-22 (Phase 0)  
**결정**: v2.1 lessons.md의 14개 교훈을 v3에 적용 (선별식 기록)  
**출처**: `ai-user-v2/lessons.md` 섹션 L-P1~L-P8

### 적용 매트릭스

| 교훈 | 제목 | v2.1 내용 | v3 적용 | 상태 |
|---|---|---|---|---|
| **L-P1-01** | Eval 오라클 오염 | 오너 1인이 봇헌터 | ≥4인 신선 평가자 강제 | ✅ charter R1 |
| **L-P2-02** | 카테고리 정렬 weak spot | FRIEND/WORK 미포함 | Thin plaza 필수 포함 (Phase 1) | ✅ charter R9 |
| **L-P3-01** | QLoRA 데이터게이트 | 비활성 (AI_USER_ML_ENABLED=false) | 3-AND 수치화 (V3-D02) | ✅ roadmap Phase 3 |
| **L-P4-01** | 분포 이해 부족 | MAUVE proxy 사용 | Named-tell 라벨셋 재정의 (Phase 4) | ✅ roadmap Phase 4 |
| **L-P5-01** | Kill criterion 늦은 등록 | Phase 5 직전에 확정 | Phase 0에서 사전 등록 (V3-D03) | ✅ 진행 중 |
| **L-P6-01** | Comment 미측정 | POST 단위만 평가 | POST+COMMENT 혼합 키트 (Phase 2/5) | ✅ charter R10 |
| **L-P7-01** | 데이터게이트 조건 모호 | 정성적 "충분함" | 정량적 조건 (≥500건, 응집성≥0.7) | ✅ roadmap Phase 3 |
| **L-P8-04** | Blind.co robots.txt | 무시하고 크롤 시도 | 명시적 제외 (phase 1 신설 크롤에서) | ✅ charter R9 |

### 미적용 항목 (의도적)

| 교훈 | 이유 |
|---|---|
| L-P5-02 (평가 단위 post vs. timeline) | v3도 timeline 유지 (기존 성공 요소) |
| L-P6-02 (공동체 제약) | v3도 NATEPAN 전용 유지 (변수 고정) |

### 변경 이력

없음 (초기)

---

## V3-D05: Phase 2 Comment Eval 결과 기반 T8 귀속 검증 (미정)

**날짜**: 2026-06-22 (계획)  
**Phase**: 2 (약 2주 후)  
**결정**: Phase 2 comment eval 후 분석 → T8이 정말 주요 원인인가?  
**기준**:
```
Post-only 식별률 - Comment-mixed 식별률 < 10pp
→ T8(또는 다른 구조 요소)이 tell의 주요 부분

그 차이 ≥ 10pp
→ Comment 품질 자체가 큰 tell 이슈
→ Phase 3 QLoRA는 필요하지만, comment 필터 강화가 추가로 필요
```

**변경 이력**: 생성 예정 (Phase 2 후 재기록)

---

## V3-D06: Phase 6 Gate — E2E & Prod 배포 준비 (미정)

**날짜**: 2026-06-22 (계획)  
**Phase**: 6 (약 6주 후)  
**결정**: Phase 5 PASS/FAIL 여부에 따라 분기

### PASS 경로 (AI 식별률 ≤25%)
- ✅ E2E-realbe 전체 통과 (dev:8090, prod 대상 금지)
- ✅ Comment 가이드 통합 (ContentSafetyGuard or OutputSanitizer)
- ✅ LlmWorkerPool 어댑터 로드 경로 구현
- ✅ Prod 환경 어댑터 배포 준비
- ✅ Prod DB 백업 + dry-run

### FAIL 경로 (AI 식별률 >25%)
- 🔄 Phase 4 T8 개선 정말 ≥15pp였나? (로그 재확인)
- 🔄 Phase 1 Thin plaza 정화 수준이 충분했나? (코퍼스 재평가)
- 🔄 Phase 2 Comment eval 결과가 정말 포함 필요한가?
- 📋 옵션 A: QLoRA 재학습 (데이터 확대 or 학습 조건)
- 📋 옵션 B: 추가 필터 (T8-specific post-processing)
- 📋 옵션 C: 하이브리드
- 📋 **오너 선택**

**변경 이력**: 생성 예정 (Phase 6 후 재기록)

---

## V3-D07: Prod 배포 Go/No-go (미정)

**날짜**: 2026-06-22 (계획)  
**Phase**: 7 (약 8주 후)  
**결정**: 오너 최종 판단

### Go 조건
```
✅ Phase 5 PASS (≤25% AI 식별률)
✅ Phase 6 E2E 전체 통과
✅ Main commit & push
✅ Prod DB 백업 완료
✅ Prod 배포 dry-run 완료
```

### No-go 조건 (자동)
```
❌ Phase 5 FAIL (>25%)
❌ Phase 6 E2E 실패 > 3건
❌ Prod 배포 dry-run 오류
```

**변경 이력**: 생성 예정 (Phase 7 후 재기록)

---

## 결정 권위도

| 결정 | 오너 권한 | 개발자 권한 | 평가자 권한 | 상태 |
|---|---|---|---|---|
| **V3-D01** (Scope) | 최종 승인 | 제안 | 정보만 | 제안 중 |
| **V3-D02** (데이터게이트) | 이해+동의 | 정의 | 평가 | ✅ 정의됨 |
| **V3-D03** (Kill criterion) | **필수 확정** | 제안 | 정보만 | 🔴 대기 |
| **V3-D04** (v2 Lessons) | 정보만 | 적용 | 정보만 | ✅ 적용함 |
| **V3-D05** (Comment 귀속) | 정보만 | 분석 | 제공 | 계획 |
| **V3-D06** (배포 준비) | 최종 승인 | 준비 | 정보만 | 계획 |
| **V3-D07** (Go/No-go) | **최종 결정** | 보고 | 정보만 | 계획 |

---

## 결정 이력 (Change Log)

### 2026-06-22 초안 작성
- V3-D01~V3-D07 작성
- V3-D03 오너 회신 대기 중
- 변경 이력: 없음

---

## 차기 갱신

**예상**: 2026-06-24 (Phase 0 완료 후)
- V3-D03 오너 확정 기록
- V3-D02 조건 재확인

---

**마지막 갱신**: 2026-06-22 Phase 0 준비

