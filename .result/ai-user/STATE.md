# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-19 세션 43 (Step 73 완료 — survey answer importer)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: Step 73 완료 — survey answer importer
- **핵심 성과**:
  - `source_filter="theqoo"` latest measured snapshot **330**
  - live `/corpus/stats` 기준 THEQOO human **562**, ai **116**
  - THEQOO 재학습 완료: version `01KVDQJSKTY93279KQYZ91PHNS`, CV-AUC **0.9958**
  - Codex-only `source_filter="theqoo"` A-B 재측정: **Δ_real=+0.1326**, snapshot **311**
  - THEQOO h2h survey **20쌍** 생성 완료:
    - survey: `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
    - answers template: `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`
  - h2h 집계 스크립트 추가:
    - `.result/ai-user/scripts/summarize_h2h_results.py`
    - 결과: `.result/ai-user/blind/r13-h2h-theqoo-results.md`
  - THEQOO owner v2 h2h 결과:
    - 유효 응답 **12/20**
    - rerank 탐지 **25.0% (3/12)**
    - random 탐지 **75.0% (9/12)**
    - **cond4-B PASS**
  - THEQOO A-B v2:
    - `Δ_real=+0.0686`
    - `mauve_rerank=0.9907`
    - `mauve_random_mean=0.9221`
  - THEQOO A-B v3 (CLI hardening 확인):
    - `Δ_real=+0.0087`
    - `mauve_rerank=0.9907`
    - `mauve_random_mean=0.9820`
  - R14 selective rerank gate 구현 완료:
    - 신규 env `AI_USER_ML_ENABLED_COMMUNITIES`
    - `AI_USER_ML_ENABLED=true`여도 community 목록으로 rerank 대상을 제한 가능
    - 비어 있으면 기존 전역 동작 유지
- **판정 보정**:
  - `:8092` 복구는 여전히 **host 접근 블로커**다. 현재 셸에서는 `ssh` 권한 거부 + `docker` 부재라 직접 해결 불가
  - R14 공식 runtime 측정은 `--generator runtime --strict-runtime` + `cli_fallbacks=0` 조건으로만 인정
  - `CLIEN blind② 40%`는 CLIEN 전용 cond5 근거다. NATEPAN/THEQOO까지 확장 해석하지 않음
  - 현재 활성화 준비 상태는 **GO candidate가 아니라 HOLD**
  - host가 열리면 즉시 실행할 준비물 추가:
    - `probe_runtime_pipeline.py` — health / 4 drafts / `/rerank` / known tell scan
    - `build_cond5_blind.py` — community별 fresh cond5 설문 생성
    - `summarize_cond5_results.py` — owner/friend cond5 집계
  - cond5 smoke 검증 완료:
    - `build_cond5_blind.py --fetch-export`로 CLIEN 2쌍 survey/template 생성 성공
    - `summarize_cond5_results.py`로 empty-response `PENDING` results 생성 성공
  - fresh cond5 설문 준비 완료:
    - [r14-cond5-natepan-survey.md](/home/justant/Data/Again-Spring/.result/ai-user/blind/r14-cond5-natepan-survey.md)
    - [r14-cond5-theqoo-survey.md](/home/justant/Data/Again-Spring/.result/ai-user/blind/r14-cond5-theqoo-survey.md)
    - 대응 answers/results 템플릿까지 생성 완료
  - 응답 처리 자동화 완료:
    - `import_survey_answers.py`로 survey markdown의 `정답/이유`를 answers json으로 직접 반영 가능
    - cond5 current survey 헤더에 import 명령 추가 완료
  - blind export 제약 확인:
    - `/corpus/export/blind`는 source id 메타를 비워서 반환
    - `used-corpus-ids.json` 중복 필터는 현재 fresh cond5 세트에 완전 적용 불가
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지
- **상태**: **HOLD** — `:8092` host 접근 블로커 + runtime 공식 재측정 부재 + NATEPAN/THEQOO fresh cond5 공백
- **남은 즉시 작업**:
  - `:8092`를 올릴 수 있는 dev host에 먼저 접근
  - runtime 배관 검증: `probe_runtime_pipeline.py`로 health, 4-draft 생성, `/rerank`, known tell scan 확인
  - host 로그에서 실제 backend/model 확인
  - THEQOO runtime h2h를 owner+friend로 다시 수집
  - `build_cond5_blind.py`로 NATEPAN/THEQOO fresh cond5 블라인드 재생성
  - `benefit_pp >= 5%p`인 community만 selective gate(B) 후보로 평가
- **이번 추가 하드닝**:
  - THEQOO `유니코드 말줄임표(…)`를 ASCII `...`로 정규화
  - runtime `OutputSanitizer`와 CLI fallback 하네스(`build_h2h_survey.py`, `run_ab_test.py`) 동시 반영
  - `쓰레기 차도` → `쓰레기통이 차도`
  - `집에서는 딸이 더 조심해야` → `집에서는 여자가 더 조심해야`
  - regenerated survey 기준 `쓰레기 차도` / `집에서는 딸이 더 조심해야` / `…` / `헐` / `개공감` / `😥` / `🥲` 모두 **0건**
  - local Phase 0 probe:
    - `/corpus/stats` 최신 수치 확인 완료
    - `localhost:8092` health still down
    - `/usr/bin/ssh` 실행 권한 거부, local `docker` 부재 → host handoff 필요

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

### 즉시 가능 (R9 배포 완료, 축적 대기)

| 작업 | 내용 | 위치 | 선결 |
|---|---|---|---|
| **R7 M-after** | COMMENT MAUVE 재측정 (신선 CLIEN COMMENT ai ≥50건) | WSL python3 mauve | ❌ CLIEN COMMENT 7건/50 미달 (POST 94건은 별개, 결정P2 대기) |
| **blind ① 기존코퍼스** | ✅ 완료 — 100% FAIL (베이스라인) | .result/ai-user/blind/ | — |
| **blind ① Track A 신선분** | ✅ 파일 생성 — 갈등 매칭 20쌍 (injectTypos 적용분) | .result/ai-user/blind/r9-blind1-fresh-survey.md | ⏳ 사용자 응답 대기 |
| **blind ②** | ✅ 파일 생성 — 혼합주제 20쌍 (CONFLICT+CASUAL AI vs human) | .result/ai-user/blind/r9-blind2-mixed-survey.md | ⏳ 사용자 응답 대기 |
| **MAUVE 재측정** | CLIEN/NATEPAN POST+COMMENT 전후 비교 | WSL python3 mauve | ✅ 신선분 축적 가능 |
| **Step 58** | THEQOO corpus 수집 전략 결정 (A/B/C) + ≥300건 확보 | **✅ 완료** | real snapshot=**311/300** |
| **Step 59** | THEQOO h2h survey 재생성 | **✅ 완료** | 응답 수집만 남음 |
| **Step 60** | THEQOO h2h 집계 자동화 | **✅ 완료** | JSON 응답만 채우면 결과 갱신 가능 |
| **Step 61** | THEQOO owner h2h 집계 + go/no-go 확정 | **✅ 완료** | v1 owner 기준 FAIL → 전역 NO GO |
| **Step 62** | THEQOO post-processing 축소 패치 | **✅ 완료** | 재생성·재측정만 남음 |
| **Step 63** | h2h/ab 하네스 런타임 정합화 | **✅ 완료** | `:8092` 복구 후 재측정 실행 |
| **Step 64** | THEQOO survey v2 재생성 + A-B 재측정 | **✅ 완료** | owner 응답만 남음 |
| **Step 65** | THEQOO owner v2 h2h 집계 + 전역 재판정 | **✅ 완료** | 연구 게이트 기준 GO candidate |
| **Step 66** | THEQOO ellipsis hardening | **✅ 완료** | `…` → `...` 정규화, 재측정은 runtime 복구 후 |
| **Step 67** | THEQOO awkward phrase hardening | **✅ 완료** | 2개 잔여 표현 정규화, 오프라인 재생성 완료 |
| **Step 68** | R14 runtime gate + host handoff | **✅ 완료(HALT 기록)** | local env에서 `ssh`/`docker` 불가, dev host 복구 절차로 전환 |
| **Step 69** | R14 selective rerank gate prep | **✅ 완료** | `AI_USER_ML_ENABLED_COMMUNITIES` 구현, 기본 동작 불변 |
| **Step 70** | R14 activation gate correction | **✅ 완료** | host blocker / strict runtime / per-community cond5 / selective gate 임계 정정 |
| **Step 71** | R14 runtime probe + cond5 tooling | **✅ 완료** | host 복구 직후 쓸 probe/blind/summarizer 추가 |
| **Step 72** | R14 fresh cond5 surveys prepared | **✅ 완료** | NATEPAN/THEQOO cond5 survey+template+pending results 생성, metadata gap 확인 |
| **Step 73** | R14 survey answer importer | **✅ 완료** | md 설문 답변을 answers json으로 자동 반영 |

### 중기

| 작업 | 내용 | 위치 | 비고 |
|---|---|---|---|
| **THEQOO corpus 교정** | human corpus 소스 변경 (격식→슬랭 역방향 해소) | corpus 소스 변경 | R10 예정 (D-52) |
| **COMMENT M-after** | NATEPAN 측정 후 R7 완료 | WSL | 신선분 축적 후 |
| **에스컬레이션 평가** | blind①② 후 D-12 Phase 2/3 진입조건 보고 | — | blind 결과 후 |

### 활성화 게이트 (R14 보정본, 2026-06-19)

```
cond1: ✅ n_ai≥100 AND n_human≥300 — CLIEN(247/1066), NATEPAN(226/469), THEQOO(116/562)
cond2: ✅ AUC 학습됨 — CLIEN 0.9965, NATEPAN 0.9989, THEQOO 0.9958
cond3: ✅ SPLITTER_VERIFIED=True
cond4: ⚠️ 공식값은 runtime strict 재측정 필요 (현재 수치는 CLI proxy/fallback 비중 존재)
cond5: ✅ CLIEN only — blind② 합산 40% (친구 25% / 오너 55%)
       ⚠️ NATEPAN/THEQOO fresh community-specific PASS 없음
```

**AI_USER_ML_ENABLED 상태**: false 유지. 현재는 **수동 활성화 판단 단계 아님**.

---

## 🔴 결정 필요 사항 (사용자 결정 대기)

| 우선순위 | 항목 | 배경 | 선택지 |
|---|---|---|---|
| **P2** | ~~COMMENT 생성 배치 (R7 M-after)~~ | ✅ 완료 — WSL 배치 B 경로로 해결 (2026-06-18) | — |
| **P3** | **AI_USER_ML_ENABLED 활성화 시기** | host blocker + runtime 공식 cond4-B 부재 + NATEPAN/THEQOO cond5 공백 때문에 아직 판정 단계가 아님 | 자동: host 복구 후 runtime/cond5 재측정 / 선택: 계속 false 유지 |
| **P5** | **THEQOO corpus 수집 방법** | ✅ **C) 크롤링 완료**. real-only corpus **311/300** 확보. | closed |
| **P6** | **THEQOO 개선 방향** | owner v2 PASS는 CLI fallback 기반이고 유효 12/20으로 얇다. runtime strict 첫 측정 + friend 추가가 핵심이다 | A) host 접근 확보 / B) runtime h2h owner+friend / C) fresh cond5 |
| **P7** | **Selective gate 채택 기준** | 전역 ON은 기본값이 아니다. `benefit_pp >= 5%p`인 community만 B안 후보로 본다 | A) B selective gate / B) C 유지 / C) A는 예외적 |

---

## 핵심 수치 현황

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
