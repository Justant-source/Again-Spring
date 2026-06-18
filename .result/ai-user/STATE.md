# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-18 세션 27 (R13 — Phase 1·2·3 병렬 실행 중, D-68 선등록 완료)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: R13 진행 중 — cond4 재정의 + h2h 검증 (Phase 1 측정 중)
- **5조건 차단 현황**: **cond4 재정의 진행 중** — 신 cond4(D-68): do-no-harm MAUVE AND h2h 비퇴행
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지
- **차단 사유**: MAUVE 포화 전역 발생 → 신 임계치 도입 (D-68)
- **직전 측정**: R12 cond4 재측정 완료 (2026-06-18) / R13 Phase 1 실행 중

---

## ✅ 지금까지 완료한 것 (6라운드 R0~R8)

| 단계 | 내용 | 결과 |
|---|---|---|
| **P0** | R3 오케스트레이터 재배포 (pushNegative SELF_GENERATED) | e2e 142P, ML ACCEPTED 정상화 |
| **R0** | clcocloud API 우선 래퍼 (run_ab_test.py) | DENY_SIGS 재시도 + CLI 폴백 |
| **R1** | corpus ctx_* 오라벨 34건 삭제 (CLIEN−32, NATEPAN−2) | 재학습 CLIEN=0.9965, NATEPAN=0.9989 |
| **R2** | 인코딩 방향 회귀 테스트 | D-45: 인코딩 정상, 5/6 PASS + 1 xfailed |
| **R3** | AS+ML 양면 소스 가드 | pushNegative source=SELF_GENERATED 보장 |
| **R4** | CLIEN de-counselor + writing_quirks 7개 features | voice.yml + DB JSON_SET 완료 |
| **R5** | CLIEN MAUVE M-before=0.6277, M-after=0.3527(n=22) + 블라인드 | **블라인드 100%(20/20) → cond5 FAIL** |
| **R6** | THEQOO corpus n_ai=100 + 재학습 | AUC=1.000이지만 **P(human) 방향 역전 HALT** |
| **R7** | COMMENT MAUVE M-before·M-after 측정 + 언어 가드 3계층 구현 | M-before CLIEN=0.0677/NATEPAN=0.0598. M-after CLIEN=**0.4661** Δ=+0.3984 ✅. NATEPAN=**0.9107** Δ=+0.8509 ✅ (배치생성 B경로, 2026-06-18) |
| **R8** | 6라운드 최종 현황 결산 | cond5 FAIL 확정, R9 계획 수립 |
| **R9 Track A** | OutputSanitizer.injectTypos T1~T8 결정론적 오타 주입 (CLIEN prob=0.55) | 구현·35테스트 통과·dev배포 ✅ · 런타임검증(오타확인) ✅ |
| **R9 Track B** | CASUAL 25% 분기 + assembleCasualPostPrompt + voice/post_casual.md | 구현·e2e 통과·dev배포 ✅ · 런타임검증(27% CASUAL) ✅ |

### 시스템 픽스 이력 (세션 21)
- `f7c477a8`: Haiku 역할극 거절 방지 — 시스템 프롬프트 persona framing 제거 (`당신은 X입니다` 삭제)
- `32b562e7`: Claude API 우선순위 + 재시도 3회 규칙 (llm-safety.md)

---

## 🔜 앞으로 해야 할 것

### 즉시 가능 (R9 배포 완료, 축적 대기)

| 작업 | 내용 | 위치 | 선결 |
|---|---|---|---|
| **R7 M-after** | COMMENT MAUVE 재측정 (신선 CLIEN COMMENT ai ≥50건) | WSL python3 mauve | ❌ CLIEN COMMENT 7건/50 미달 (POST 94건은 별개, 결정P2 대기) |
| **blind ① 기존코퍼스** | ✅ 완료 — 100% FAIL (베이스라인) | .result/ai-user/blind/ | — |
| **blind ① Track A 신선분** | ✅ 파일 생성 — 갈등 매칭 20쌍 (injectTypos 적용분) | .result/ai-user/blind/r9-blind1-fresh-survey.md | ⏳ 사용자 응답 대기 |
| **blind ②** | ✅ 파일 생성 — 혼합주제 20쌍 (CONFLICT+CASUAL AI vs human) | .result/ai-user/blind/r9-blind2-mixed-survey.md | ⏳ 사용자 응답 대기 |
| **MAUVE 재측정** | CLIEN/NATEPAN POST+COMMENT 전후 비교 | WSL python3 mauve | ✅ 신선분 축적 가능 |

### 중기

| 작업 | 내용 | 위치 | 비고 |
|---|---|---|---|
| **THEQOO corpus 교정** | human corpus 소스 변경 (격식→슬랭 역방향 해소) | corpus 소스 변경 | R10 예정 (D-52) |
| **COMMENT M-after** | NATEPAN 측정 후 R7 완료 | WSL | 신선분 축적 후 |
| **에스컬레이션 평가** | blind①② 후 D-12 Phase 2/3 진입조건 보고 | — | blind 결과 후 |

### prod 배포 게이트 (5조건 — **전부 충족** 2026-06-18)

```
cond1: ✅ n_ai≥100 AND n_human≥300 — CLIEN(247/1066), NATEPAN(226/469), THEQOO(100/311)
cond2: ✅ AUC 학습됨 — CLIEN 0.9965, NATEPAN 0.9989, THEQOO 0.9973
cond3: ✅ SPLITTER_VERIFIED=True
cond4: ❌ NATEPAN Δ=-0.0001 FAIL (R12 재측정, 판별기 재학습 후, MAUVE 포화로 마진 소멸)
       ⚠️ CLIEN Δ=+0.0134 (R12, MAUVE 포화로 R11 +0.3371에서 급락)
       ⚠️ THEQOO Δ=+0.0186 (R12, 소폭 하락)
cond5: ✅ blind② 합산 40% (친구 25% / 오너 55%) — R9 PASS (2026-06-18)
```

**AI_USER_ML_ENABLED 상태**: false → **활성화 차단** (5조건 미충족, NATEPAN cond4 FAIL, R12 재학습 필요)

---

## 🔴 결정 필요 사항 (사용자 결정 대기)

| 우선순위 | 항목 | 배경 | 선택지 |
|---|---|---|---|
| **P1** | **THEQOO human corpus 소스** (R10 핵심) | dev DB 7건·AS export = 다시봄 갈등체 → 실제 더쿠 슬랭 아님. 현재 human 410건이 격식체라 P(human) 역전 | A) THEQOO 직접 크롤(법적·인프라 검토 필요) / B) 외부 공개 데이터셋 / C) AS 플랫폼 내 THEQOO 스타일 글 직접 주석 수집 |
| **P2** | ~~COMMENT 생성 배치 (R7 M-after)~~ | ✅ 완료 — WSL 배치 B 경로로 해결 (2026-06-18) | — |
| **P3** | **AI_USER_ML_ENABLED 활성화 시기** | THEQOO cond4 미충족 → ML 리랭커 활성화 불가 | 자동: THEQOO cond4 해소 후 수동 활성화 / 선택: CLIEN+NATEPAN만 부분 활성화 가능 여부 검토 |
| **P4** | ✅ **NATEPAN cond4 재측정 완료** | R11 결과: Δ=-0.2901 FAIL (기존 M1 Δ=+0.1667과 대조) — 리랭커 성능 저하, P(human) 역전 | R12: 판별기 재학습 필요 |

---

## 핵심 수치 현황

### AUC (CV 5-fold)
| 커뮤니티 | AUC | std | n_human | n_ai | 상태 |
|---|---|---|---|---|---|
| CLIEN | 0.9968 | 0.0053 | 960 | 157 | ✅ (재학습 2026-06-16) |
| NATEPAN | 0.9989 | 0.00125 | 427 | 226 | ✅ (재학습 2026-06-16) |
| THEQOO | 1.000 | 0.001 | 393 | 100 | ❌ P(human) 방향 역전 HALT |

### MAUVE
| 커뮤니티 | POST | COMMENT | 비고 |
|---|---|---|---|
| CLIEN | 0.644(baseline) → **0.9811**(ab-test n=50) Δ=+0.3371 ✅ | 0.0677(M-before) → **0.4661**(M-after) Δ=+0.3984 ✅ | cond4 PASS (2026-06-18) |
| NATEPAN | 0.8395 | 0.0598(M-before) → **0.9107**(M-after) Δ=+0.8509 / **M-after(R11) Δ=-0.2901** ❌ | R7 배치=+0.8509, R11 재측정=Δ=-0.2901 FAIL |
| THEQOO | — | **R10 Δ=+0.4458 vs R11 Δ=+0.0417** ⚠️ | R10=Sonnet, R11=Haiku Phase1b (포화, 한계선) |

### 블라인드 cond5
| 라운드 | 커뮤니티 | 정확도 | 목표 |
|---|---|---|---|
| M5 (세션 16) | NATEPAN+THEQOO | 82.5% (33/40) | ≤60% ❌ |
| R5 (세션 21) | CLIEN | **100% (20/20)** | ≤60% ❌ |
| R9 blind① 기존 (세션 22) | CLIEN | **100% (20/20)** | ≤60% ❌ (베이스라인 확인) |
| R9 blind① Track A 신선분 (세션 23) | CLIEN fresh | 25% (5/20) ✅ PASS | ≤60% 목표 |
| R9 blind② 혼합주제 (세션 24) | CLIEN mixed | **25% (5/20) / 55% (11/20) 오너** | ≤60% 목표 |
| **R9 합산** (세션 25) | 친구+오너 | **40% (16/40) ✅ PASS** | ≤60% 목표 |

---

## R9 진행 현황 (cond5 전용 스타일 강화)

| Track | 레버 | 상태 | 결과 |
|---|---|---|---|
| **A** | OutputSanitizer.injectTypos T1~T8 결정론적 오타 주입 (CLIEN prob=0.55) | ✅ 배포 완료 | ⚠️ AI_USER_ENABLED=false로 신선 POST 미생성 |
| **B** | executePost CASUAL 25% 분기 + assembleCasualPostPrompt | ✅ 배포 완료 | ⚠️ 동일 차단 |
| **C-R7** | COMMENT MAUVE M-after | ✅ CLIEN 62건(0.4661 Δ+0.3984) ✅ NATEPAN 55건(0.9107 Δ+0.8509) | **R7 완료** (2026-06-18) |
| **C-THEQOO** | human corpus 소스 교정 | ⏸ R10 이연 | D-52 |

**R9 측정 (배포 후 신선 축적 필요)**:
- blind ① 갈등 매칭 20쌍 → Track A 순수 문체 cond5
- blind ② 혼합주제 20쌍 → Track B + 현실 cond5 (목표 ≤60%)
- MAUVE 재측정: CLIEN/NATEPAN POST+COMMENT 전후 비교

---

## [S25] 세션 25 blind② 오너 결과 (2026-06-18)

### blind② 최종 집계
- **친구 평가**: 25% 정확도 (5/20 AI 탐지)
- **오너 평가**: 55% 정확도 (11/20 AI 탐지) ✅ PASS (≤60% 목표)
- **합산**: 40% (16/40) ✅ PASS (≤60% 목표)

### PromptAssembler 개선 필요 항목 (D-62)
- **부인→아내/와이프**: "그 남자"→지칭어 구체화
- **나레이터 투**: ~하기 시작했어요 패턴 검사·제거
- **느껴버렸어요**: 감정 과장 표현 제거

---

## [S24] 세션 24 실측 (2026-06-17)

### CLI-Haiku POST 배치
- 전환: LLM_POST_MODEL=haiku + backend_post=CLI (일시적)
- 생성: 6병렬 에이전트 × 3콜 × 5건 = 94 corpus 기록 (~17분)
- 원복: sonnet+API 복원 완료
- **이유**: API+Sonnet에서 Kiro 혼입 차단 목표 (신선 분석용)

### Track A+B 런타임 확인
- **Track A (injectTypos T1~T8)**: 질레야·들어왓어요·있엇는데 등 오타 발견 ✅
- **Track B (CASUAL 25%)**: 카페·드라마·육아·날씨 글 ~27% 확인 ✅
- **POST 모델 정정**: 기존분 = Sonnet via API (not Haiku), 신선분 = Haiku+CLI
  - 교란 변수 존재: blind① 결과 해석 시 모델 변화(Sonnet→Haiku) 명시 필요

### blind 설문 파일 생성
- **blind①**: `.result/ai-user/blind/r9-blind1-fresh-survey.md`
  - 갈등 매칭 20쌍 (Haiku+CLI 신선분)
  - 주의: human 일부 비갈등 포함 가능 → 정밀도 제한 명시
  
- **blind②**: `.result/ai-user/blind/r9-blind2-mixed-survey.md`
  - 혼합주제 20쌍 (10 CONFLICT + 10 CASUAL AI vs 20 human 다양주제)
  - cond5 핵심 측정 (목표 ≤60%)
  - 각 쌍마다 탐지 이유 수집 (D-55 정책)

### 신선 축적 현황
- **CLIEN POST ai**: +94 신선 (→ 총 256+)
- **CLIEN COMMENT ai**: 미배치 (별도 필요, R7 M-after 용)
- **THEQOO**: HALT 유지 (P(human) 역전 미해결, D-52)

---

---

## [R13] 라운드 13 진행 중 — cond4 재정의 + h2h 검증 (2026-06-18~)

### Phase 진행 현황

| Phase | 내용 | 상태 |
|---|---|---|
| **P3 선등록** | D-68 cond4 재정의 선등록 (decisions.md + roadmap.md) | ✅ 완료 (7bb048f3) |
| **P1 구현** | source_filter 구현 (WSL routes_eval.py + schemas.py) | ✅ 완료 |
| **P1 측정** | THEQOO Δ_real (source_filter="theqoo", 진짜 111건) | ⏳ 실행 중 |
| **P2 구현** | build_h2h_survey.py | ⏳ 실행 중 |
| **P2 설문** | 커뮤니티별 h2h survey.md 생성 | ⏳ P2 구현 완료 후 |

### D-68 선등록 임계 (측정 전 확정)
- THEQOO Δ_real > 0 → cond4 A 충족 ✅ (Phase 2 h2h 진행)
- THEQOO Δ_real ≤ 0 → 진짜코퍼스 없이 미검증 ❌ (Step 52-53 재개)
- h2h 합격: 리랭커 탐지율 ≤ random 탐지율 (per-person)

---

## [R12] 라운드 12 — NATEPAN 재학습 + cond4 재측정 (2026-06-18)

### 재학습 결과
- NATEPAN 판별기: AUC=**0.9989**, n_train=695 (n_human=469, n_ai=226) ✅
- THEQOO: AUC=0.9972 (함께 갱신)
- CLIEN: AUC=0.9975 (함께 갱신)

### cond4 재측정 결과

| 커뮤니티 | R11 delta | R12 delta | 판정 |
|---|---|---|---|
| NATEPAN | -0.2901 | **-0.0001** | ❌ FAIL (사실상 0, 음수) |
| CLIEN | +0.3371 | **+0.0134** | ⚠️ provisional (급락) |
| THEQOO | +0.0417 | **+0.0186** | ⚠️ provisional (소폭 하락) |

### MAUVE 포화 분석

모든 커뮤니티 MAUVE가 0.97~0.9998 영역으로 수렴:
- NATEPAN: rerank=0.9997, random_mean=0.9998
- CLIEN: rerank=0.9969, random_mean=0.9835
- THEQOO: rerank=0.9974, random_mean=0.9788

**근본 원인**: AI 출력 품질이 전반적으로 향상되어 MAUVE 포화 → rerank vs random 마진 소멸
- NATEPAN: 재학습으로 -0.2901 → -0.0001 개선 (방향은 올바름, 완전 해소는 못 됨)
- CLIEN: R11 +0.3371 → +0.0134로 급락 (포화 효과)
- THEQOO: 소폭 하락 (안정적)

### go/no-go 판정

**NO GO** ❌ — NATEPAN delta=-0.0001 (음수)로 전역 게이트 차단 지속

### R13 옵션

**A) 리랭커 임계값 조정**: MAUVE 포화 상태에서 delta≈0은 "리랭커가 최소한 랜덤과 동등"을 의미 — cond4 기준을 Δ≥-0.01(허용 오차)로 완화하거나 다른 metric(P(human) 직접) 사용

**B) 더 많은 시드로 재실행**: 단일 런 노이즈 확인 — NATEPAN 3회 이상 독립 런 평균

**C) MAUVE 보완 metric**: P(human) 분포나 리랭커 랭킹 정확도(top-1 선택 정확률) 등 포화되지 않은 지표 도입

---

## [R11] 라운드 11 cond4 재측정 (2026-06-18)

### 측정 결과

| 커뮤니티 | MAUVE (M-after) | DELTA | 상태 | 비고 |
|---|---|---|---|---|
| **NATEPAN** | — | **-0.2901** | ❌ FAIL | 리랭커가 랜덤보다 나쁜 초안 선택, P(human) 역전 |
| **CLIEN** | 0.9811 | +0.3371 | ✅ PASS | 변함없음 (R9/R10 안정) |
| **THEQOO** | — | +0.0417 | ⚠️ 한계선 | Haiku Phase1b, Sonnet 판별기 포화 명확 |

### go/no-go 판정

- **NO GO** ❌ — NATEPAN cond4 FAIL로 인한 전역 게이트 차단
- 원인: 리랭커 모델 성능 저하. R7 M-after Δ=+0.8509와 R11 Δ=-0.2901의 극심한 괴리
- 전역 게이트: `ActionExecutor.java:425` 단일 boolean — 분리 불가능

### R12 계획

- **NATEPAN 판별기 재학습** 필요 (기존 모델 포화 명확)
  - 훈련 데이터 정제: 엣지 케이스, 불균형 클래스 재검토
  - 모델 아키텍처: 현 classifier 포화 → 하이퍼파라미터/구조 조정 검토
  - 신선 코퍼스: NATEPAN n_ai, n_human 증강 필요
- **THEQOO P(human) 포화**: Sonnet 판별기가 모든 draft에 P(human)≈1.0 → 다음 라운드 이연
- **전역 활성화**: NATEPAN 해소 후에만 가능

---

## 운영 메모

- **Auto 모드**: 막히지 않으면 계속 진행 (사용자 명시, 2026-06-02)
- **로컬**: 최대 6개 에이전트 병렬
- **WSL CPU**: 20코어, 최대 16개 에이전트 병렬
- **API 우선순위**: clcocloud API → CLI 폴백 / **재시도 최대 3회**
- **prod 배포**: 명시 지시 + 절대규칙 #4

---

## 특이사항 / 함정 (세션 간 공유 필수)

### [S22] R9 피벗 — 프롬프트 레벨 오타 주입은 이미 죽었다
- CLIEN 5/5 페르소나 `mobile_typos=true`이 이미 DB에 있는데도 AI POST 오타 0 = Haiku가 무시
- **Track A 전략 전환**: LLM 지시 대신 `OutputSanitizer.injectTypos()` 결정론적 후처리 (injectChosung 선례)
- **Track B**: 갈등 서사 하드코딩 탈출 — CASUAL 25% 분기 (voice/post_casual.md, 사건 의무 해제)
- appendWritingQuirks `Math.min(1→2)` — 사소한 보강, 본질은 injectTypos

### [S22] injectTypos 핵심 불변식
- T1~T8 transforms, budget=1~2, fireProb 게이트(≈45% 클린 유지)
- 첫 줄(hook) 보호, len<40 skip, UNKNOWN voice 무변
- applyDist에서 normalizeCommaRate·injectChosung **다음(마지막)** 호출 → 하류 정규화 불침범

### [S21] cond5 100% 원인 — 주제+문체 복합
- CLIEN ai corpus = 갈등 서사만 / human = 다양 주제 → 주제로 구별 가능
- 문체 신호도 기여: "저도 비슷한 상황이었는데요..." 패턴, 균일 길이, 오타 0
- 순수 문체 cond5는 갈등 매칭 쌍으로 재측정 필요

### [S22] AI_USER_ENABLED=false — 신선 축적 차단 (D-56)

- `.env.dev`에 `AI_USER_ENABLED=false` 설정됨 (`cda5bb2d fix(dev-cost)` 때 의도적 비활성화)
- 자동 스케줄 틱: 매 10분 fire되지만 `enabled=false`로 전부 스킵 → **신선 POST 자동 생성 없음**
- 수동 admin trigger는 작동 (`docker exec againspring-ai-user-orchestrator wget ... /admin/trigger/tick`)
- **선택지**: A) `AI_USER_ENABLED=true` 임시 전환 → 빠른 축적 (비용↑) / B) 수동 트리거 유지 (저비용)
- **사용자 결정 대기 중** (2026-06-17)

### [S22] LLM 토큰 소모 패턴 (D-57)

- **경로**: clcocloud API (`https://api.clcocloud.com/claude`) — CLI 아님
- **패턴**: Haiku 호출 → 매번 PROVIDER_ERROR (Kiro 혼입) → Sonnet 폴백 → **사실상 Sonnet 토큰만 소모**
- **이중 과금**: Haiku 실패분(소량) + Sonnet 성공분 (매 액션)
- **Sonnet 캐시 히트**: 70~72% (캐싱 정상 작동)
- **해소 조건**: clcocloud Haiku 풀에서 Kiro 노드 제거 (서비스 측 이슈, 당장 수동 해결 불가)
- ContentSafetyGuard 'credit balance' 차단 지속 — SEED/PAIRED 기능에서 Kiro 응답 일부 필터 중

### [S22] R7 M-after 선결 조건
- llm-ai-user 재빌드: 2026-06-17 09:13 KST ✅
- 신선 COMMENT ai 축적 현황 (2026-06-17 기준): CLIEN 3건, NATEPAN 5건, THEQOO 6건 (목표: CLIEN ≥50)
- corpus 확인: `SELECT ... WHERE content_type='COMMENT' AND label='ai' AND ingested_at > '2026-06-17 00:13:00'`
- **AI_USER_ENABLED=false 중 — 자동 축적 차단됨**

### [S20] Haiku 역할극 거절 픽스 (f7c477a8)
- 원인: `당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다` → clcocloud Haiku 거절
- 수정: PromptAssembler.java 2곳 + ClaudeCliInvoker.java 1곳에서 persona framing 제거

### [S18] CLIEN personas 세대 불일치
- DB 활성 CLIEN 5개 = PersonaFactory 자동 생성
- voice.yml 변경은 DB에 직접 JSON_SET 필요 (R4에서 5건 적용 완료)

### [S17] THEQOO P(human) 역전 근본 원인
- AS-platform human corpus = 격식 갈등 서사
- AI THEQOO corpus = 슬랭 더쿠 스타일
- → 방향 역전. human corpus 소스 변경 필요 (큰 작업)

### [이전] Python 테스트 모듈 캐싱
- `patch("app.storage.db.get_session")` 실패 → 사용 지점 패치 필요

---

## 전체 Step 인덱스

| Step | 세션 | 내용 | 상태 |
|---|---|---|---|
| Step 0~17 | 1~10 | 스캐폴드~T8 THEQOO TSD | ✅ |
| Step 18~26 | 11~13 | 2라운드 N1~N9 | ✅ |
| Step 27~34 | 14~16 | 3라운드 M1~M8 + CUDA 수정 | ✅ |
| Step 35~38 | 16~17 | M5 블라인드, NATEPAN 교정, THEQOO corpus 삭제 | ✅ |
| Step 39~43 | 18 | 6라운드 R0~R4 (API래퍼·소스가드·CLIEN de-counselor) | ✅ |
| **Step 44** | 19 | P0: R3 오케스트레이터 재배포 + e2e + corpus 축적 확인 | ✅ |
| **Step 45** | 19~21 | R5: CLIEN MAUVE 0.6277→0.3527 + 블라인드 100%(20/20) FAIL | ✅ |
| **Step 46** | 19~20 | R6: THEQOO n_ai=100 + AUC=1.000, P(human) 역전 HALT | ❌ HALT |
| **Step 47** | 19·26 | R7: M-before(CLIEN 0.0677, NATEPAN 0.0598) + 언어 가드 3계층 + M-after CLIEN 0.4661 | 🔄 NATEPAN 미달(25건) |
| **Step 48** | 21 | R8: 6라운드 결산 + cond5 FAIL 확정 + R9 계획 | ✅ |
| **Step 49** | 22 | R9 Track A: OutputSanitizer.injectTypos T1~T8 결정론적 오타 주입 | ✅ 배포완료 |
| **Step 50** | 22 | R9 Track B: CASUAL 25% 분기 + PromptAssembler.assembleCasualPostPrompt | ✅ 배포완료 |
| **Step 51** | 22~ | R9 blind①②+MAUVE 재측정 + 에스컬레이션 평가 | 🔄 축적 대기 |
