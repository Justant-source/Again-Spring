# AI-User v3 — STATE (라이브 포인터)

> **최종 갱신**: 2026-06-22  
> **현재 Phase**: 0 (준비 중)  
> **상태**: 문서 작성 완료, 오너 회신 대기

---

## 프로젝트 상태

| 항목 | 상태 | 설명 |
|---|---|---|
| **프로젝트명** | ✅ v3 스타일 수렴 & 생성 품질 도약 | charter-v3.md 정의 |
| **전임** | ✅ v2.1 SHIPPED (2026-06-22) | 5인 평가 28% PASS |
| **현재 위치** | ✅ Phase 0 준비 | 문서 작성 완료 |
| **기간** | 예상 7~10주 | Phase 0~7 |
| **kill criterion** | 🔴 **오너 확정 대기** | 제안: ≤25% AI 식별률 |

---

## 핵심 지표

### v2.1 최종 결과
- **AI 식별률**: 5/18 = 27.8% (**≤28% PASS**)
- **Human 오탐률**: 55% (역전, 구분 불가 수준)
- **평가자**: 신선 캐주얼 독자 5인
- **키트**: 계정 5개 (AI) + 4개 (Human), 각 POST 3건, 총 27 unit
- **배포**: 2026-06-22 prod 완료

### v3 목표치
- **AI 식별률**: ≤25% (강화 기준, v2.1 대비 -3pp)
- **평가자**: 신선 캐주얼 독자 4인 (v2.1 5인에서 후보 4인, 보수적)
- **키트**: 계정 5개 + 5개, 각 POST 3 + **COMMENT 3**, 총 30 unit
- **특화**: FRIEND/WORK 광장 필수 포함 (v2.1 weak spot)

---

## Phase 진행 현황

### Phase 0 — 창립 & 동결 🔄

**목표**: v3를 독립 프로젝트로 창립. 문서·kill criterion·고정 baseline 동결.

**완료 항목**:
- ✅ `charter-v3.md` — 프로젝트 charter 작성
- ✅ `roadmap.md` — 상세 워크스트림 (Phase 0~7)
- ✅ `README.md` — 요약 & 문서 가이드
- ✅ `STATE.md` — 이 파일

**진행 중 항목**:
- 📝 `decisions.md` — V3-D01~D03 작성 (이어서)
- 📋 `steps/` 디렉토리 (Phase별 상세 지시, 이후 생성)

**대기 중 항목**:
- 🔴 **오너 회신** (kill criterion ≤25% 확정)
- 🔴 **`npm run lint:docs` 실행** (문서 검증)
- 🔴 **Git commit & push** (Phase 0 완료 gate)

**Gate 조건**:
```
Phase 0 PASS = 
  ✅ 5개 문서 완성 +
  ✅ Kill criterion 타임스탬프 등록 +
  ✅ lint:docs 통과 +
  ✅ git push 완료
```

**예상 완료**: 1~2일 (오너 응답 시간 포함)

---

### Phase 1 — Thin Plaza 코퍼스 보강 🔜

**목표**: FRIEND(165→400+), WORK(156→400+) 코퍼스 3-4배 확대

**상태**: 미시작 (Phase 0 완료 후)

**주요 마일스톤**:
- [ ] 크롤러 신설 (NATEPAN-FRIEND, CLIEN-WORK, DCINSIDE-MARRIED/DATING, 인스티즈-FRIEND)
- [ ] 크롤 & 정화 (한글<10%, URL-heavy, content_hash dedup)
- [ ] 임베딩 & 적재 (KURE-v1, example_bank + ML 판별기)
- [ ] 정화 리포트 & 검증

**Gate**: FRIEND/WORK ≥400건 clean 데이터 확보

**예상 기간**: 3~5일

---

### Phase 2 — Comment 평가 하니스 🔜

**목표**: POST + COMMENT 혼합 키트 설계 & 첫 평가

**상태**: 미시작

**주요 마일스톤**:
- [ ] 평가자 4인 모집 (신선, v2.1 비참여)
- [ ] 블라인드 키트 구성 (30 unit: AI 15 + Human 15)
- [ ] 평가 지시문 작성 (comment 포함)
- [ ] 평가 실행 & 분석 (comment 식별률 측정)

**Gate**: Inter-rater reliability 확인 + comment-only 식별률 분석

**예상 기간**: 1~2주 (평가자 모집 포함)

---

### Phase 3 — QLoRA SFT 데이터 준비 🔜

**목표**: Haiku fine-tune용 갈등 고품질 POST ≥500건 SFT 포맷 준비

**상태**: 미시작

**3-AND 게이트**:
1. Phase 1 완료 (Thin plaza ≥400)
2. Phase 2 분석 (T8이 주요 원인 확인)
3. SFT 데이터 ≥500건 확보

**주요 마일스톤**:
- [ ] 고품질 인간 글 후보 필터링 (~2000건)
- [ ] 응집성 점수화 (자동 NLP + 수동 검증)
- [ ] SFT 포맷 변환 (instruction + input + output)
- [ ] Train/Val 분할 (80/20)

**Gate**: 응집성 ≥0.7 데이터 500건 확보

**예상 기간**: 1~2주

---

### Phase 4 — QLoRA 학습 & 어댑터 검증 ⚡

**목표**: WSL RTX 3090에서 Haiku QLoRA 어댑터 학습 + T8 감소 검증

**상태**: 미시작

**환경**: WSL 16 에이전트, RTX 3090 25.8GB VRAM

**주요 마일스톤**:
- [ ] QLoRA 학습 스크립트 작성 (`qora_trainer.py`)
- [ ] 학습 실행 (epoch 3~5, batch 8)
- [ ] 어댑터 저장 (`ai-user/learning/adapters/haiku-v3-01/`)
- [ ] Named-tell 분포 기반 검증 (T8 ≥15pp 감소)

**Gate**: T8 개선 ≥15pp + 다른 tell 회귀 <±5pp

**예상 기간**: 1주 (학습 4~8h + 검증)

---

### Phase 5 — Baseline 블라인드 (HARDER KIT) 🔜

**목표**: 강화된 v3 시스템(thin plaza+QLoRA+comment) 첫 블라인드 평가

**상태**: 미시작

**HARDER KIT**:
- **AI 계정**: 5개 (FRIEND 1 + WORK 1 + 기타 3, 각 POST 3 + COMMENT 3)
- **Human 계정**: 5개 (동일 구성)
- **총 30 unit** (v2.1 27 대비 확대)

**평가자**: 신선 4인 (E-017~E-020, v2.1 비참여)

**Kill criterion 적용**:
```
AI 식별률 = (AI로 정답 판정된 계정 수) / 5개
≤25% = PASS (1개 이하 봇 정답)
>25% = FAIL (2개 이상 봇 정답)
```

**예상 기간**: 1~2주 (평가 + 분석)

---

### Phase 6 — Comment 통합 & 최종 검증 🔜

**목표**: Comment 가이드 통합 + E2E 테스트 + Prod 배포 준비 (or 재학습 옵션)

**상태**: 미시작

**분기**:
- **PASS 경로** (≤25%): Comment 가이드 + E2E + Prod 배포 준비
- **FAIL 경로** (>25%): 재학습 / 추가 필터 / 하이브리드 옵션 제시

**예상 기간**: 1주 (PASS) ~ 2~4주 (FAIL, 재학습)

---

### Phase 7 — 클로즈아웃 & Prod 배포/피벗 🎯

**목표**: v3 최종 마무리 + Prod 배포 또는 오너 결정

**상태**: 미시작

**프로세스**:
- Phase 5 PASS ✅
- E2E-realbe 전체 PASS ✅
- Main commit & push ✅
- Prod DB 백업 ✅
- Prod 배포 ✅
- Prod 스모크 테스트 ✅

**교훈 문서**: `lessons.md` (v2.1 패턴 따름)

**예상 기간**: 1주 (배포) ~ 오너 결정 대기

---

## 주요 의사결정 (Open Items)

| ID | 주제 | 현재 | 오너 | Phase |
|---|---|---|---|---|
| **T0** | Kill criterion ≤25% 수치 확정 | 제안 | 🔴 대기 | 0 |
| **T1** | 평가자 ≥4인 최종 명단 | 제안 | 대기 | 0~2 |
| **T2** | FRIEND/WORK 광장 필수 포함 | 확정 | — | 1~5 |
| **T3** | Comment 필수 포함 | 확정 | — | 2~6 |
| **T4** | Thin plaza 크롤 소스 5개 정확성 | 제안 | 대기 | 1 |
| **T5** | QLoRA 데이터게이트 3-AND 기준 | 정의됨 | — | 3 |
| **T6** | Phase 6 FAIL 시 옵션 선택 | 미정 | 대기 | 6 |
| **T7** | Prod 배포 go/no-go | 미정 | 결정 | 7 |

---

## 차기 Checkpoint

### 즉시 (This Week)

1. 📋 오너 회신 → kill criterion ≤25% 확정
2. 📋 `decisions.md` 작성 완료
3. 🔨 `npm run lint:docs` 실행 + 통과
4. 📌 Git commit & push (Phase 0 완료)

### 다음 주 (Phase 1)

1. 크롤러 신설 시작 (WSL 16 에이전트)
2. 정화 & 정화 리포트
3. 임베딩 재구성 (KURE-v1, example_bank)

### 3주차 (Phase 2 병렬)

1. 평가자 4인 최종 모집
2. 블라인드 키트 구성 (30 unit)
3. 평가 지시문 최종화
4. **Phase 3 데이터 준비 병렬 진행** (로컬 8 에이전트)

---

## 외부 의존성

| 항목 | 상태 | 영향도 | 대체안 |
|---|---|---|---|
| 평가자 4인 모집 | 🔴 미정 | High | 기한 연장 or 3인 결정 |
| CLIEN/DCINSIDE 크롤 ToS | ✅ 검증됨 | Medium | 속도 조절 (politeness) |
| Blind.co robots.txt | ✅ 존중 | Low | 다른 소스로 보강 |
| WSL RTX 3090 사용 가능 | ✅ 확인됨 | High | 로컬 CPU 폴백 (지연) |
| Prod DB 용량 (백업) | ✅ 확인됨 | Low | AWS snapshot |

---

## 리소스 할당

| 리소스 | 할당 | 활용 |
|---|---|---|
| **로컬 Claude Code** | 8 에이전트 | Phase 0/3/5/6/7 |
| **WSL Claude Code** | 16 에이전트 | Phase 1/4 (GPU/크롤) |
| **평가자** | 4인 | Phase 2/5 (블라인드) |
| **오너** | 1인 | T0/T6/T7 결정 + 캘리브레이션 |

---

## Known Issues & Risks

| Issue | Severity | Mitigation |
|---|---|---|
| T8 개선 <15pp 가능성 | Medium | Phase 3 데이터 검증 강화 |
| Phase 1 Thin plaza 코퍼스 부족 | Low | 추가 소스 확보 (인스타, 유튜브 댓글 등) |
| Comment eval 평가자 피로 | Medium | 평가 기간 연장 or 평가 부하 분산 |
| QLoRA 학습 실패 (VRAM) | Low | Batch 축소 or LoRA rank 축소 |
| Prod 배포 긴급 롤백 | High | Prod 배포 전 e2e-realbe 완전 통과 필수 |

---

## 메모리 & 참조

- **전임**: v2.1 최종 결과 → `ai-user-v2/STATE.md`, `lessons.md`
- **v3 charter**: `charter-v3.md`
- **v3 roadmap**: `roadmap.md`
- **의사결정**: `decisions.md` (생성 예정)
- **진행 기록**: Phase별 `steps/` 디렉토리 (생성 예정)
- **평가 결과**: `eval/` (Phase 2/5 이후)

---

## 마지막 갱신

**일시**: 2026-06-22  
**작성자**: Claude Code (Agent)  
**배포**: .result/ai-user-v3/STATE.md

다음 갱신 예상: 2026-06-24 (Phase 0 완료 후)

