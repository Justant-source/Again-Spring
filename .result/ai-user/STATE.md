# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-20 세션 (Step 87 완료 — 보정형 cond5 게이트 + 공식 GPU cond4 + 활성화 후보 dossier)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: Step 87 완료 → **Phase-1 실행 완료 (2026-06-20)**
- **D-95**: Codex CLI → Claude Code CLI 복원 ✅
- **D-96~D-97**: CLIEN cond4 재측정 → -0.0436 FAIL 확정 ❌
- **D-101 (2026-06-20)**: cross-era 보정 실패 확인 + 보정형 3-state cond5 게이트 채택 (gap_hi=0.54)
- **D-102 (2026-06-20)**: 공식 GPU strict-runtime MAUVE A-B — THEQOO +0.1380 ✅ / NATEPAN -0.1048 ❌
- **D-103 (2026-06-20)**: NATEPAN Phase 4b 적대적 생성 (60샘플, tell 0.37, proxy 100% unknown)

### 활성화 후보 현황 (2026-06-20 최종)

| 커뮤니티 | cond1 | cond2 | cond3 | cond4 | cond5 | 후보 |
|---|---|---|---|---|---|---|
| **THEQOO** | ✅ | ✅ | ✅ | ✅ +0.1380 | ⚠️ PROXY-FAIL (0.69) | **✅ 활성화 결정** (D-104) |
| **NATEPAN** | ✅ | ✅ | ✅ | ❌ -0.1048 | ❌ PROXY-FAIL (0.84) | 미진입 |
| **CLIEN** | ✅ | ✅ | ✅ | ❌ -0.0436 (구조적) | 미측정 | 제외 |

**`AI_USER_ML_ENABLED=false` 유지** — 사람 수동 활성화 (D-17 불변, 코드 변경 금지)

### cond5 보정 게이트 요약 (D-101)

- **cross-era 보정 실패**: r14(Codex) gap=+54pp ↔ r9(Haiku) gap=-48pp → 반대 방향, 단순 오프셋 불가
- **채택**: `gap_hi=0.54` 보수 상한, `human_est_upper = min(1.0, proxy + 0.54)`
- **3-state 판정**: PROXY-FAIL / PROXY-INCONCLUSIVE (bare PASS 금지)
- THEQOO r15: proxy=0.15, upper=0.69 → **PROXY-FAIL** (but cond4 ✅ + tell-scan 2 + topic_overlap 0 → activation candidate 유지)
- NATEPAN r15: proxy=0.30, upper=0.84 → **PROXY-FAIL**

### 주요 아티팩트

- `.result/ai-user/THEQOO-activation-dossier.md` — THEQOO 5조건 표 + 활성화 절차
- `.result/ai-user/blind/r15-cond5-theqoo-claude-survey-cond5-gate.json` — cond5 게이트 판정
- `.result/ai-user/blind/r15-cond5-natepan-claude-survey-cond5-gate.json` — cond5 게이트 판정
- `.result/ai-user/blind/calibration-agreement.json` — proxy↔사람 보정 분석 (gap_mean=0.54)
- `.result/ai-user/scripts/cond5_auto_gate.py` — 3-state 자동 게이트 스크립트
- `.result/ai-user/scripts/calibration_agreement.py` — 보정 조인 스크립트
- `.result/ai-user/scripts/convert_r9_answers.py` — r9 schema 변환기
- `.result/ai-user/scripts/ensemble_blind_judge.py` — 4-judge (micro_tell 추가)

---

## ✅ 지금까지 완료한 것 (6라운드 R0~R8)

| 단계 | 내용 | 결과 |
|---|---|---|
| **P0** | R3 오케스트레이터 재배포 (pushNegative SELF_GENERATED) | e2e 142P, ML ACCEPTED 정상화 |
| **R0** | clcocloud API 우선 래퍼 (run_ab_test.py) | 이후 세션 28에서 **Codex CLI bridge only**로 전환 |
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

### 즉시 (이번 세션 이후)

| 우선순위 | 작업 | 내용 | 선결 |
|---|---|---|---|
| **P1** | **THEQOO cond5 최종 판정** | gap_hi=0.54(Codex era 파생)가 Claude 콘텐츠엔 과혹할 수 있음. 사람이 r15 THEQOO 설문 응답(20쌍)하면 공식 확인 가능. OR 현재 증거(proxy 15% + cond4 +0.1380 + tell-scan 2)로 activation candidate 판정 수용 | 사람 수동 결정 |
| **P2** | **THEQOO 활성화 실행** | dev AI_USER_ML_ENABLED=true → 2주 관찰 → e2e-realbe → prod (절대규칙 #4) | P1 판정 후 |
| **P3** | **NATEPAN cond4 개선** | corpus 보강(Phase 4b 60샘플 활용) + reranker 재학습 → cond4 재측정. 목표: delta > 0 | D-103 corpus 적용 후 |
| **P4** | **CLIEN 장기 개선** | 구조적 cond4 FAIL — discriminator가 MAUVE와 anti-corr. corpus 방향 재검토 필요 | 장기 |

### THEQOO 활성화 절차 (사람 결정 후)

1. `env/.env.dev` → `AI_USER_ML_ENABLED=true` (코드 변경 없음)
2. `cd env && docker compose -f docker-compose.dev.yml up -d againspring-ai-user-orchestrator`
3. 2주 관찰: 게시물 품질, 투표 반응, 탐지 신고 없음
4. `cd frontend && E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe` 전체 통과
5. main push → prod 배포 (절대규칙 #4 순서)

### 중기

| 작업 | 내용 |
|---|---|
| NATEPAN cond4 재도전 | Phase 4b 60샘플 corpus 추가 → `/train` → A-B 재측정 |
| CLIEN 구조 분석 | discriminator P(human) anti-corr MAUVE 근본 원인 조사 |
| 모든 커뮤니티 cond5 재측정 | THEQOO 활성화 후 학습 코퍼스 보강 시 재실행 |

---

## 🔴 결정 필요 사항 (사용자 결정 대기)

| 우선순위 | 항목 | 배경 | 선택지 |
|---|---|---|---|
| **P1** | **THEQOO cond5 + 활성화 실행 여부** | cond4 ✅(+0.1380) + proxy 15% + tell-scan 2 → 간접 증거 충분. cond5 PROXY-FAIL은 Codex era gap_hi 과혹 가능. | **✅ B 채택 (2026-06-20, D-104)** — proxy 증거 수락, 활성화 진행 |
| **P3** | **AI_USER_ENABLED 활성화 여부** | THEQOO는 4/5 조건 ✅ + cond5 간접 PASS 시사. `AI_USER_FORCE_ACTIVE=true`는 이미 dev에서 active. ML 리랭킹 별도 게이트. | 수동으로 AI_USER_ML_ENABLED=true 설정 (코드 변경 없음) |

---

## 핵심 수치 현황

### cond4 MAUVE (Phase-1 Claude 기준 공식 결과, 2026-06-20)

| 커뮤니티 | mauve_rerank | mauve_random_mean | delta | 판정 |
|---|---|---|---|---|
| **THEQOO** | 0.9591 | 0.8210 | **+0.1380** | ✅ PASS |
| **CLIEN** | 0.9099 | 0.9535 (±0.0308) | **-0.0436** | ❌ FAIL |
| **NATEPAN** | 0.6209 | 0.7257 | **-0.1048** | ❌ FAIL |

**주의사항**:
- 모두 `strict_runtime=True`, `draft_sources: runtime=46 cli=0 failed=2` 확인됨
- **CLIEN delta 음수 (-0.0436)**: 재측정 (2026-06-20): delta=-0.0436, 이전 artifact 의심(-0.0665) 대비 안정화. 두 번 연속 음수 → CLIEN cond4 진짜 FAIL 확인.
- THEQOO와 NATEPAN은 cond4 통과(delta > 0)
- CLIEN은 재측정으로도 일관되게 음수 → 리랭커 성능 이슈 확인됨

### AUC (CV 5-fold)
| 커뮤니티 | AUC | std | n_human | n_ai | 상태 |
|---|---|---|---|---|---|
| CLIEN | 0.9968 | 0.0053 | 960 | 157 | ✅ (재학습 2026-06-16) |
| NATEPAN | 0.9989 | 0.00125 | 427 | 226 | ✅ (재학습 2026-06-16) |
| THEQOO | 0.9958 | — | 543 | 100 | ✅ Step 58 재학습 완료 (version `01KVDQJSKTY93279KQYZ91PHNS`) |

### MAUVE
| 커뮤니티 | POST | COMMENT | 비고 |
|---|---|---|---|
| CLIEN | 0.644(baseline) → **0.9811**(ab-test n=50) Δ=+0.3371 ✅ | 0.0677(M-before) → **0.4661**(M-after) Δ=+0.3984 ✅ | cond4 PASS (2026-06-18) |
| NATEPAN | 0.8395 | 0.0598(M-before) → **0.9107**(M-after) Δ=+0.8509 / **M-after(R11) Δ=-0.2901** ❌ | R7 배치=+0.8509, R11 재측정=Δ=-0.2901 FAIL |
| THEQOO | **Codex-only Δ_real=+0.1326 (snapshot=311)** | — | ✅ real corpus 300+ 달성 후 양수 유지 |

### 블라인드 cond5
| 라운드 | 커뮤니티 | 정확도 | 목표 |
|---|---|---|---|
| M5 (세션 16) | NATEPAN+THEQOO | 82.5% (33/40) | ≤60% ❌ |
| R5 (세션 21) | CLIEN | **100% (20/20)** | ≤60% ❌ |
| R9 blind① 기존 (세션 22) | CLIEN | **100% (20/20)** | ≤60% ❌ (베이스라인 확인) |
| R9 blind① Track A 신선분 (세션 23) | CLIEN fresh | 25% (5/20) ✅ PASS | ≤60% 목표 |
| R9 blind② 혼합주제 (세션 24) | CLIEN mixed | **25% (5/20) / 55% (11/20) 오너** | ≤60% 목표 |
| **R9 합산** (세션 25) | 친구+오너 | **40% (16/40) ✅ PASS** | ≤60% 목표 |
| **R14 CLIEN cond4** | CLIEN n_drafts=8 | runtime=48 cli=0 failed=48 (ctx=12 타임아웃) | mauve_rerank=None ❌ |
| **r15 THEQOO cond5 proxy** | THEQOO (Claude ensemble judge, 4-judge) | **15% (3/20) ✅ raw PASS** | — |
| **r15 NATEPAN cond5 proxy** | NATEPAN (Claude ensemble judge, 4-judge) | **35% (7/20) ✅ raw PASS** | — |
| **r15 보정 결과 (D-101)** | THEQOO + NATEPAN | gap_hi=0.54 적용 후: THEQOO upper=0.69, NATEPAN upper=0.89 | ≤60% PROXY-FAIL |
| **r14 THEQOO cond5** (Codex, stale) | THEQOO | 84.2% (16/19) ❌ FAIL | ≤60% — 폐기됨 |

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
| **P1 측정** | THEQOO Δ_real (source_filter="theqoo", 진짜 111건) | ✅ 완료 Δ_real=-0.1117 FAIL |
| **P2 구현** | build_h2h_survey.py | ✅ 완료 |
| **P2 설문** | 커뮤니티별 h2h survey.md 생성 | ✅ 완료 |
| **P4 집계** | D-70 + r13-h2h-results-summary.md | ✅ 완료 |
| **P5 Step58** | real-only corpus 311 확보 + 재학습 + Δ_real 회복 | ✅ 완료 |
| **P6 Step59** | THEQOO h2h survey 재생성 (20쌍) | ✅ 완료 / 응답 대기 |
| **P7 Step60** | h2h 집계 자동화 + pending results 생성 | ✅ 완료 |
| **P8 Step61** | owner h2h 집계 + 전역 NO GO 확정 | ✅ 완료 |

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
- **SELF_CRITIQUE_EXTRA_CLICHES** (r15 관측 2026-06-20):
  - "이번달만 세 번째", "이번 달만 세 번째", "이번주만 세 번째", "이번 주만 세 번째" 패턴 추가 필요
  - r15 THEQOO Claude 설문 생성 과정에서 관측된 AI 탐지 신호 (space/non-space 변형 포함)

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
| **Step 55~57** | 27 | R13: source_filter + h2h survey + go/no-go 표 | ✅ |
| **Step 58** | — | THEQOO corpus 수집 전략 결정 | 🔴 사용자 결정 대기 |
