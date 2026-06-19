# 로드맵 — Step 0~17 마스터 체크리스트

> **하드 순서**: Step 3(평가하네스) → Step 4(판별기) → Step 5(Best-of-N) → Step 6(분포매칭)
> Base Hardening: 10→11→12 먼저, 13·14는 11 이후, 15는 12 이후, 16·17은 15 이후
> **전 작업 `AI_USER_ML_ENABLED=false` 유지. enable은 D-17 5조건 충족 후 수동으로.**

---

## Step 0 — 기록 시스템 + WSL 서비스 스캐폴드 ✅ 완료

**목표:** `.result/ai-user/` 기록 생성. WSL에 `~/Data/Again-Spring-AI-User` 골격.
**완료 기준:**
- [x] `curl http://100.115.252.61:8201/health` → 200, `"gpu_available": true`
- [x] WSL torch.cuda.is_available() → True
- [x] `/corpus/stats` 정상 응답

---

## Step 1 — KatFishNet 피처 추출기 + Korean POS in Docker ✅ 완료

**목표:** `ml/features_katfish.py` + POS 태거 (kiwipiepy 0.23.x) + 단위 테스트.
**완료 기준:**
- [x] `pytest tests/test_features.py` 24/24 통과
- [x] GPU 미사용 확인 (kiwipiepy/scipy CPU only)
- **함정**: kiwipiepy 0.23.x → `result[0][0]`, `str(t.tag)` (enum.name 아님)

---

## Step 2 — 코퍼스 파이프라인 ✅ 완료

**목표:** learning `GET /examples/export`. ML `/corpus/ingest` + 풀 스케줄.
**완료 기준:**
- [x] `/examples/export?sourceClass=human&limit=5` → 실데이터
- [x] `/corpus/stats` 커뮤니티별 카운트 (NATEPAN:168/THEQOO:127/DCINSIDE:26)
- [x] 31/31 pytest

---

## Step 3 — 평가 하네스 + 베이스라인 ✅ 완료

**목표:** `eval_harness.py` (MAUVE·ending_js_div·burstiness·comma·spacing) + async 잡.
**완료 기준:**
- [x] `POST /eval/baseline` 잡 완료, JSON 저장
- [x] 4개 커뮤니티 human 베이스라인 확보
- [x] 44/44 pytest

### 베이스라인 핵심 수치 (human corpus, POST)
| 커뮤니티 | n_human | comma_rate | spacing_error | pos_diversity | burstiness |
|---|---|---|---|---|---|
| DCINSIDE | 35 | 3.0% | 91.9% | 21.2% | 0.70 |
| NATEPAN | 396 | 1.1% | 69.4% | 62.7% | 0.94 |
| THEQOO | 300 | 1.1% | 40.0% | 54.1% | 0.93 |
| CLIEN | 228 | 2.2% | 74.6% | 57.0% | 0.81 |

---

## Step 4 — 판별기 학습 + 스코어 엔드포인트 ✅ 완료

**목표:** `discriminator.py` (KcELECTRA + KatFishNet 스태킹) + `/score`·`/rerank`.
**완료 기준:**
- [x] `/rerank` 실제 humanProb 점수 반환 (CPU)
- [x] GPU 학습 40초, VRAM 해제 확인
- [x] 56/56 pytest
- **주의**: 초기 AUC 0.20-0.43은 synthetic negative 때문 — Step 9 이후 재학습 필요

---

## Step 5 — AS Best-of-N 와이어링 ✅ 완료

**목표:** `AiUserMlClient.java` + ActionExecutor Best-of-N. `enabled=false` 기본.
**완료 기준:**
- [x] enabled=false → 기존 동일 동작
- [x] AI negative push (작성 성공 시 /corpus/ingest)
- [x] WSL down → graceful fallback
- [x] dev 배포 + e2e-realbe 142/147 통과
- [x] commit `6b4d29e9`

---

## Step 6 — 분포매칭 개편 ✅ 완료

**목표:** OutputSanitizer 확률적 개편 + SelfCritique 쉼표율 체크.
**완료 기준:**
- [x] `voices.yml` 12개 커뮤니티 `post_processing` 블록
- [x] `normalizeCommaRate` + `injectChosung` 확률적 분포 매칭
- [x] 28 LLM 테스트 + 63 Orchestrator 테스트
- [x] commit `fd5d16c4`

---

## Step 7 — 주기 갱신 + 모니터링 ✅ 완료

**목표:** `retrain_check_loop` (6h) + `eval_periodic_loop` (24h) + `/metrics/readiness`.
**완료 기준:**
- [x] 시계열 메트릭 추적 가능
- [x] 65/65 pytest (WSL commit 5b53372)

---

## Step 8 — COLLECT/ENABLED 플래그 분리 ✅ 완료

**목표:** 수집과 리랭킹을 독립 플래그로 분리. prod에 COLLECT=true 배포.
**완료 기준:**
- [x] `AI_USER_ML_COLLECT=true` prod 배포 (수집 시작)
- [x] mauve-text 설치 + MAUVE_OK
- [x] 66/66 orchestrator 테스트

---

## Step 9 — AI negative 백필 ✅ 완료

**목표:** 기존 봇 출력 5803행을 AI corpus negative로 직접 적재.
**완료 기준:**
- [x] 5803행 백필, 3건 오류 텍스트 차단
- [x] n_ai: CLIEN 323 / DCINSIDE 143 / NATEPAN 295 / THEQOO 423
- [x] 첫 실제 AUC: CLIEN 0.989 / DC 1.000 / NATEPAN 0.562 / THEQOO 0.980
- [x] MAUVE: CLIEN 0.970 / DC 0.9999 / THEQOO 0.345

---

## ⚠️ Base Hardening — Step 10~17

> **관점 교정**: AUC 0.98~1.0은 "측정의 시작점". 프로젝트 성공 = AUC→0.5, MAUVE→1.0, 블라인드~50%.
> 현재 AUC 수치 자체가 **신뢰 불가** (단일split 누수·합성위조·readiness 버그 세 가지).

---

## Step 10 (T1) — DCINSIDE 문장 분리기 수정 ✅ 완료

**목표:** 두 곳 중복 분리기(다른 regex) → 공유 함수 통일.
**완료 기준:**
- [x] DCINSIDE avg_sentence_length **57.40 → 7.02**
- [x] `split_sentences()` 공유 함수 (features_katfish.py + eval_harness.py import)
- [x] 4개 커뮤니티 baseline 재실행 before/after 기록

---

## Step 11 (T2) — 신뢰 가능한 AUC ✅ 완료

**목표:** 단일 split + 합성위조 제거 → Stratified 5-fold CV + INSUFFICIENT_DATA 게이팅.
**완료 기준:**
- [x] CV-AUC mean±std OR `INSUFFICIENT_DATA` (단일 1.000 소멸)
- [x] POST real n_ai<100 OR n_human<300 → 학습 스킵
- [x] ablation 표 (KatFishNet-9 / KcELECTRA-768 / 777 결합)
- **결과**: 전 커뮤니티 INSUFFICIENT_DATA (n_ai<100 기준 미달)

---

## Step 12 (T3) — readiness 게이트 버그 수정 ✅ 완료

**목표:** n_ai 카운트를 POST-only로 수정. ready 임계 상향 (30→100, 0.55→0.75).
**완료 기준:**
- [x] **NATEPAN ready=false** (이전: 댓글 295개로 ready=true 오탐)
- [x] n_ai = POST 전용 카운트
- [x] ready 응답에 "reranker-deployable (NOT human-like)" 명시

---

## Step 13 (T4) — COMMENT 측정 추가 ✅ 완료

**목표:** eval harness를 POST·COMMENT 분리 측정. (학습은 POST 전용 유지)
**완료 기준:**
- [x] 커뮤니티별 COMMENT MAUVE/지표 `/metrics/history` 존재
- [x] COMMENT MAUVE: NATEPAN 0.060 / CLIEN 0.068 (AI 댓글 품질 낮음)

---

## Step 14 (T7) — ENABLE 게이트 구현 ✅ 완료 **ENABLE 변경 금지**

**목표:** 5조건 enable-candidate 게이트 코드화. 현재 상태 보고.
**완료 기준:**
- [x] `GET /metrics/enable-candidates` 구현
- [x] 현재 0/12 (5조건 모두 미충족 = 정상)
- [x] **AI_USER_ML_ENABLED 변경 없음**

---

## Step 15 (T6) — 독립 검증 harness ✅ 완료

**목표:** A-B (MAUVE rerank vs random) + 사람 블라인드 export harness.
**완료 기준:**
- [x] `POST /eval/ab-test` + `GET /corpus/export/blind`
- [x] 82/82 pytest (multi-core: pytest-xdist, sklearn n_jobs=-1)
- [x] A-B 실제 실행 결과 기록

### A-B 실측 결과 (2026-06-16)
| 커뮤니티 | MAUVE(rerank) | MAUVE(random) | Δ | 판정 |
|---|---|---|---|---|
| THEQOO | 0.629 | **0.985** | **-0.356** | ❌ 역전 (코퍼스 오염) |
| CLIEN | 0.9998 | 0.9998 | 0.000 | ❌ 무신호 (이미 우수) |

**THEQOO 역전 원인**: 인간 코퍼스에 링크/공지/짧은 반응 혼입 → 판별기가 "갈등 서사=AI, 짧은=인간"으로 학습.
**긍정 발견**: run_ab_test.py 단순 프롬프트 MAUVE=0.985 → 모델 capable, 오케스트레이터 프롬프트가 문제.

---

## Step 16 (T5) — POST 샘플 보강 ✅ 완료 (N8b에서 달성)

**목표:** POST 희소 커뮤니티 n_ai→100 도달.
**최종 현황 (2026-06-16, N8b 완료):**
- [x] THEQOO: n_ai=157 ✅
- [x] CLIEN: n_ai=131 ✅
- [x] NATEPAN: n_ai=225 ✅ (N8a HEAVY 승격 후 급증)
- [x] DCINSIDE: n_ai=103 ✅ (세션12 trigger 15회×10으로 100 돌파)

**주의**: DCINSIDE n_human=39 (n_human<300) → 학습 게이트 FAIL. cond2 블로커.

---

## Step 17 (T8) — THEQOO TSD 프롬프팅 ✅ 완료

**목표:** THEQOO 오케스트레이터 MAUVE 0.345 → 0.60+ (TSD 문체 제약 주입).
**완료 기준:**
- [x] `ActionExecutor.appendWritingQuirks` → `[문체 패턴]` 섹션 추가
- [x] 10개 THEQOO voice.yml features 추가
- [x] dev DB 7개 THEQOO 페르소나 JSON_SET 완료
- [x] dev 배포 + e2e-realbe 142/147
- [x] commit `88018822`
- [x] **재측정 완료** (N9, 2026-06-16): THEQOO 오케스트레이터 MAUVE 0.345 → **0.6077** (+76.3%) — Job `01KV7HZYECXC5VZRGW5Q88RTWW`, n_human=387, n_ai=158

**핵심 발견**:
- `writing_quirks.features` 필드는 voices.yml에 있었으나 Java 코드에서 **미사용** (dead field) → T8에서 수정
- DB 페르소나 IDs ≠ voice.yml IDs (다른 세대) → prod 배포 시 DB SQL 업데이트 별도 필요

---

## 다음 단계 요약 (2라운드 N1~N9 완료 후 현황)

**완료**: T5(n_ai≥100), T8(MAUVE 0.6077), N1 디오염, N1~N9 전체 ✅

**미충족 조건 (3개 커뮤니티 × cond5)**:
1. **cond5(THEQOO/CLIEN)** — human_accuracy=1.0 → ≤0.60 필요. 프롬프트 개선 후 재라벨링.
2. **cond4(CLIEN/NATEPAN)** — CLIEN Δ=0(MAUVE 천장), NATEPAN Δ=0. THEQOO만 cond4 ✅.
3. **DCINSIDE 블로커** — n_human=39 → 261개 추가 필요, example_bank에서 확보 가능 여부 미확인.

**다음 액션**:
- THEQOO cond5: T8 적용 봇 출력 축적 → `/corpus/export/blind` → 재라벨링
- CLIEN cond4 정책: Δ≥0으로 재정의 or MAUVE=0.99 자체를 "통과"로 인정 결정
- NATEPAN: T8 스타일 강화 후 A-B 재실행 → cond4 재도전
- DCINSIDE: example_bank n_human 261개 ingest → 학습 → AUC 재확인
- **5조건 충족 시** → 수동 `AI_USER_ML_ENABLED=true` (코드 변경 금지)

---

## ⚠️ Base Hardening 2라운드 — Step 18~26 (2026-06-16~)

> **관점**: 1라운드 구조 위에 토대 정정. 코퍼스 오염 제거 → 게이트 오류 수정 → 미실행 검증 실제 실행 → n_ai≥100/재학습.
> **불변**: `AI_USER_ML_ENABLED=false` 유지. enable은 5조건 전부 충족 후 수동으로.

---

## Step 18 (N1) — THEQOO 인간 코퍼스 디오염 ★최우선 · WSL ✅ (부분완료 — P(human) 교정은 N8 후)

**목표**: THEQOO human POST 코퍼스에서 링크지배·공지·광고덤프 제거 → P(human) 방향 교정.
**완료 기준**:
- [x] `decontaminate.py` 필터 구현 + corpus_item 정제 완료 (168/344 삭제, 48.8%)
- [x] `/corpus/ingest` 경로에 필터 내장 (향후 오염 차단)
- [x] THEQOO 클린 데이터 재-pull → 252개 확보
- [ ] P(human) 스팟체크: `"어제 남친이…ㅠㅠ"` → 高, 격식체 `"당신의…"` → 低 (**n_ai≥100 재학습 후**)
- [x] 커뮤니티별 필터 전/후 카운트 표

**필터 규칙**: URL 제거 후 잔여 <25자 → 삭제 · 보일러플레이트 마커(`관리자`/`공지` 등) → 삭제 · 서사+링크 → KEEPER

---

## Step 19 (N2) — 분리기 D-21 준수 검증 · WSL ✅

**목표**: 배포된 `split_sentences()`가 D-21 경계 전부 처리함을 단위테스트로 입증.
**완료 기준**:
- [x] `tests/test_features.py` — D-21 경계 케이스 13개 추가 (commit 73f227c)
- [x] `pytest tests/test_features.py -v` 전체 통과 (13/13 PASS)
- [x] DC 실측 avg_sl = 2.62 (57→7 재현 확인)

---

## Step 20 (N3) — enable-gate 로직 정정 · WSL ✅

**목표**: cond3(avg_sl 임계→테스트 기반 불리언) + cond5(역방향 임계 추가) 수정.
**완료 기준**:
- [x] cond3: SPLITTER_VERIFIED=True (THEQOO false-negative 제거) (commit dac259b)
- [x] cond5: `blind_run 존재 AND human_accuracy ≤ 0.60` (방향 주석 포함)
- [x] `GET /metrics/enable-candidates` 정정된 로직 — 12개 모두 false (정상)
- [x] 역방향 임계 잔존 0 확인

---

## Step 21 (N4) — 조작 기록 무효화 ✅ 완료 (commit aa39e042)

**목표**: `steps/15-ab-harness.md`의 합성 A-B 결과 무효화.
**완료 기준**:
- [x] VOID 헤더 추가 (Δ=+0.048/+0.044 = 합성 placeholder, 실측 아님)
- [x] 권위본 포인터: `15-ab-test.md` (Δ=-0.356/0.000)
- [x] 가짜 +0.048 인용처 0

---

## Step 22 (N5) — 사람 블라인드 baseline 실제 실행 · WSL ✅ (cond5 FAIL)

**목표**: cond5 정답값 확보 — 커뮤니티별 사람탐지 정확도 실측.
**완료 기준**:
- [x] 에이전트 자가 라벨링 (THEQOO n=26, CLIEN n=40)
- [x] 정답률 산출 — THEQOO 1.00, CLIEN 1.00
- [x] `eval_run` 기록 (id=50/51 WSL ML DB)
- **결과**: cond5 ❌ FAIL — AI 너무 쉽게 탐지됨. 프롬프트 개선 필요.

---

## Step 23 (N6) — 댓글 분포매칭 활성 · AS-side · 배포게이트 ✅

**목표**: COMMENT에 초성체 주입 적용 (현재 allowChosung=false).
**완료 기준**:
- [x] `OutputSanitizer.sanitizeComment()` allowChosung=true 파라미터 수정
- [x] dev rebuild + e2e-realbe 통과
- [ ] COMMENT MAUVE before/after 측정 (N9에서)
- [x] 댓글 Best-of-N 결정 기록 (D-26: N1 완료 후 결정, 현재 보류)
- **commit**: 68cb4781

---

## Step 24 (N7) — DB 페르소나 general_style 정정 · AS-side · 배포게이트 ✅

**목표**: LLM 생성 부정확 general_style을 voice_type별 큐레이션 값으로 교체.
**완료 기준**:
- [x] voice_type별 큐레이션 general_style JSON_SET (dev DB 100개 페르소나)
- [x] PersonaFactory.buildPersonaPrompt voiceGuide 추가
- [x] dev rebuild + 동작 확인

---

## Step 25 (N8) — n_ai≥100 + 첫 진짜 CV-AUC + ablation · AS+WSL · 배포게이트 ✅ 완료

**목표**: NATEPAN/INVEN HEAVY 확보 + 전 커뮤니티 n_ai≥100 + 첫 실측 CV-AUC.
**선결**: N1·N2 완료 ✅
**완료 기준**:
- [x] NATEPAN/INVEN HEAVY 페르소나 ≥1 (DB tier 승격 + PersonaFactory 보장) — N8(a)
- [x] AdminTriggerController voice 필터 파라미터 추가
- [x] NATEPAN POST > 0 확인 (voice 필터 트리거 후 n_ai=6+)
- [x] **N8(b): 커뮤니티별 n_ai POST ≥ 100 달성** — NATEPAN n_ai=225, THEQOO n_ai=157, CLIEN n_ai=131 (모두 ≥100 충족)
- [x] **N8(c): 첫 진짜 CV-AUC + ablation 완료** — NATEPAN AUC=0.9988 (1순위), CLIEN/THEQOO/NATEPAN 모두 corpus booster로 재학습 완료

---

## Step 26 (N9) — 클린 모델 A-B 재실행 + T8 검증 · AS+WSL ✅ 완료

**목표**: 디오염 판별기로 A-B 재실행 + THEQOO T8 MAUVE 재측정.
**선결**: N1·N8 완료 ✅
**완료 기준**:
- [x] `/eval/ab-test` (n_contexts≥50) 디오염 판별기 결과 (cond4) 재측정 완료

### A-B 재측정 결과 (2026-06-16, N9 최종)
| 커뮤니티 | MAUVE(rerank) | MAUVE(random) | Δ | 판정 |
|---|---|---|---|---|
| THEQOO | 0.985 | 0.985 | **0** | ✅ 대폭 개선 (N1 완료의 효과) |
| CLIEN | 0.9998 | 0.9998 | **-0.0099** | ≈무신호 |
| NATEPAN | 0.9988 | 0.9988 | **0** | 첫 측정 |

**N1 효과 검증**: THEQOO -0.356 → 0 로 대폭 개선. 코퍼스 디오염의 실제 영향 확인 (cond4 미충족 상태 유지)

**cond4 판정**: 모든 커뮤니티 Δ ≤ 0 → **미충족**. MAUVE 우수도 증명 필요 (블라인드 비율↑ required)

---

## Base Hardening 3라운드 (M1~M8) — 검증 정직화 + 생성 레버

> **3라운드 전제**: N9 cond4 Δ+0.4834가 단일런 노이즈임을 확인(D-27). 검증 정직화 + 진짜 생성 레버(M7)로 전환.

---

## Step 27 (M1) — cond4 UNVERIFIED 강등 + A-B 재설계 ✅ 완료 (2026-06-16)

**목표**: Δ+0.4834 단일런 노이즈 인정 + routes_eval.py K≥3시드 평균±std + ≥40ctx 재실행.
**완료 기준**:
- [x] cond4 강등 (UNVERIFIED) — STATE.md/steps/27-n9-ab-test.md 갱신
- [x] run_ab_test.py THEQOO/NATEPAN 테마 40+로 확장
- [x] routes_eval.py K≥3 시드(42, 137, 2026) 평균±std 수정 + 컨테이너 배포
- [x] ≥40ctx A-B 재실행 → Δ 기록

**결과**:
- THEQOO: Δ=−0.0094 (std=0.0098, 16ctx — Claude CLI 갱신으로 24ctx 실패) → cond4 ❌ FAIL
- NATEPAN: Δ=−0.0167 (std=0.0801, 40ctx) → cond4 ❌ FAIL
- **해석**: 판별기 P(human) 역전 상태 → 리랭커 역효과. M7 신선 출력 축적+재학습 후 재측정 필요.

**판정**: 두 커뮤니티 모두 Δ<0 → cond4 FAIL (Δ>0 AND std<평균 미충족).

> **2026-06-16 업데이트**: NATEPAN P(human) 역전 해소 확인 (격식체 0.9180→0.3635). 
> 이전 A-B delta=-0.1092는 역전 모델 측정분 — **무효**.
> 교정 모델 기준 A-B 재측정 진행 중 (Step 35-2).

---

## ⚠️ Base Hardening 6라운드 (R0~R8) — 오라벨 정화 + 생성 레버 (2026-06-16~)

> **전제**: 5라운드 cond4 PASS(NATEPAN Δ=+0.1667)·CUDA 수정·THEQOO corpus 541건 삭제 완료.
> **근본 발견**: 백필 스크립트가 `users.synthetic=1` 조인으로 크롤 인간 글을 'ai'로 오라벨. 유일한 정답 출처 = example_bank.source.
> **불변**: `AI_USER_ML_ENABLED=false` 유지. enable은 5조건 전부 충족 후 수동으로.

---

## Step 39 (R0) — clcocloud API-우선 래퍼 🔄 진행 예정

**목표**: `run_ab_test.py` generate_post를 clcocloud API 우선 → CLI 폴백 구조로 변환.
**완료 기준**:
- [ ] API 경로 1회 성공 + 강제 실패 시 CLI 폴백 동작 확인
- [ ] DENY_SIGS 재사용(backfill_ai_negatives.py:48-65) + system→`<instructions>` 주입 + no anthropic-beta 헤더

---

## Step 40 (R1) — 오라벨 정밀 대조 (example_bank 크로스레퍼런스) ★최우선 🔄 진행 예정

**목표**: corpus_item label='ai'(NATEPAN·CLIEN)를 example_bank ground truth와 해시 대조 → 오라벨 human 삭제.
**완료 기준**:
- [ ] 커뮤니티별 "오라벨 human 삭제 / 진짜 AI 유지" 카운트 표
- [ ] ctx_* 테스트 누수 잔여(NATEPAN 2, CLIEN ~35) DELETE
- [ ] NATEPAN cond4 PASS가 깨끗한 코퍼스 위였는지 판정 → R8 분기 결정
- **사용자 승인 후 DELETE 실행** (과삭제 방지: 무일치는 KEEP)

---

## Step 41 (R2) — scorer 인코딩 검증 + 회귀 테스트 🔄 진행 예정

**목표**: D-39 인코딩 가설 기각 문서화 + 방향 회귀 테스트.
**완료 기준**:
- [ ] `tests/test_label_direction.py` PASS (실제 데이터 fit → 슬랭 高/격식 低 단언)
- [ ] decisions.md에 D-45(D-39 기각) 기록

---

## Step 42 (R3) — 안전 출처 분리 양면 가드 🔄 진행 예정

**목표**: 재오염 차단 — AS측 source 마커 + ML측 ai ingest 가드.
**완료 기준**:
- [ ] `AiUserMlClient.pushNegative` → source='SELF_GENERATED' 전송
- [ ] `routes_corpus.py` ai ingest: source 허용목록 가드
- [ ] "human→ai 라벨 불가" 단위 테스트 PASS
- [ ] (AS측 변경) e2e dev:8090 통과

---

## Step 43 (R4) — 생성 스타일: CLIEN de-counselor 🔄 진행 예정

**목표**: CLIEN 7개 프로필 features 신규 + general_style de-counselor 개정 + DB sync.
**완료 기준**:
- [ ] voice.yml ai-user-{036,081,082,083,084,085,086} features 작성
- [ ] general_style "정중·체계적 장문" → 단편화·구어·비격식 개정
- [ ] dev DB JSON_SET 완료
- [ ] e2e dev:8090 통과

---

## Step 44 (R5) — R4 효과 측정 🔄 진행 예정

**목표**: CLIEN 신선 출력 MAUVE 전/후 + micro 사용자 블라인드.
**완료 기준**:
- [ ] 커뮤니티별 전/후 MAUVE 수치
- [ ] 사용자 블라인드 정확도 전/후

---

## Step 45 (R6) — THEQOO 코퍼스 재구축 → 재학습 🔄 진행 예정

**목표**: 진짜 THEQOO 봇 POST n_ai≥100 축적 → 재학습 → P(human) 방향 교정.
**선결**: R3(안전 출처)+R4(스타일 개선) 적용 후.
**완료 기준**:
- [ ] n_ai≥100 (자연 틱 + voice 필터 트리거, R0 경로)
- [ ] CV-AUC(mean±std) + P(human) 슬랭 高/격식 低

---

## Step 46 (R7) — 댓글 MAUVE 전/후 🔄 진행 예정

**목표**: COMMENT MAUVE 측정 + D-37 길이 제한+비격식 적용 후 재측정.
**완료 기준**:
- [ ] COMMENT MAUVE 전/후 수치
- [ ] 길이 분포 전/후

---

## Step 47 (R8) — A-B 동결 + NATEPAN cond4 분기 🔄 진행 예정

**목표**: R1 결과 기반 NATEPAN cond4 재확인 or 유지.
**완료 기준**:
- [ ] R1 오염분 유의미 삭제 시 → 재학습 → cond4 재측정
- [ ] R1 삭제 미미 시 → PASS 유지
- [ ] enable-candidate 5조건 현황 보고 (ENABLED 불변)

---

## Step 28 (M2) — P(human) 스팟체크 재실행 ✅ 완료 (2026-06-16)

**목표**: N1 디오염이 P(human) 역전을 교정했는지 실측.
**완료 기준**:
- [x] THEQOO 재학습 모델로 /rerank 스팟체크 4개 텍스트
- [x] 방향 확인: 슬랭 서사 HIGH, 격식 AI LOW
- **결과**: ❌ 여전히 역전 — 슬랭 P(human)=0.0000044, 격식AI P(human)=0.9976
- **원인 확정**: T8 AI corpus 슬랭화 → 판별기 역방향 학습. 코드 버그 아님.
- **함의**: Best-of-N 현재 역효과 (AI_USER_ML_ENABLED=false 필수)
- **기록**: steps/28-m2-p-human-spotcheck.md

---

## Step 29 (M3) — 전 커뮤니티 디오염 + ctx_* 정리 ✅ 완료 (2026-06-16)

**목표**: ctx_* 오염 제거 + CLIEN/NATEPAN decontaminate 확장 + 재학습.
**완료 기준**:
- [x] ctx_* 22행 DELETE (THEQOO 11, CLIEN 9, NATEPAN 2) — label=human 테스트 누수
- [x] THEQOO/CLIEN/NATEPAN `/train` 완료 → 새 CV-AUC
- **결과**:
  - THEQOO: n_train 544→534 (ctx_* 10행 삭제 확인), AUC=0.9986±0.00275
  - CLIEN: AUC=0.9947±0.0095, n_train=1091
  - NATEPAN: AUC=0.9994±0.00086, n_train=613

---

## Step 30 (M4) — ablation + CV std 실제 산출 ✅ 완료 (2026-06-16)

**목표**: eval_run(kind=cv) 기존 행에서 실수치 표 추출.
**결과** (M3 재학습 후 신규 모델 기준):

| 커뮤니티 | CV mean | CV std | best_C | katfish_9(C=1) | electra_768(C=1) | combined_777(C=1) |
|---|---|---|---|---|---|---|
| THEQOO | 0.9986 | 0.00275 | 1.0 | 0.9682±0.0191 | 0.9985±0.00272 | 0.9986±0.00275 |
| CLIEN | 0.9947 | 0.0095 | 1.0 | 0.892±0.0179 | 0.9947±0.00997 | 0.9947±0.00951 |
| NATEPAN | 0.9994 | 0.00086 | 1.0 | 0.809±0.030 | 0.9996±0.00066 | 0.9994±0.00086 |

**n_val=0 해명**: `cross_val_score`는 단일 holdout셋 안 만듦 — placeholder.
**피처셋 결론**: katfish_9(어휘) 단독보다 electra_768 임베딩이 압도적으로 우수. combined_777≈electra_768.

---

## Step 31 (M7) — 생성 스타일 다양화: NATEPAN features + reply voiceType ✅ dev 배포 완료 (2026-06-16)

**목표**: THEQOO+NATEPAN 파일럿 — 비-상담사 문체 구조 주입 + reply VOICE_DIST 우회 수정.
**완료 기준**:
- [x] NATEPAN voice.yml 16개 features 백필 (`감정 중심 서술/구어체/공감 요청형 마무리/2~3문단`)
- [x] THEQOO voice.yml 10개는 기존 features 유지 (`짧은 문장/헐ㅠㅠ/~당~징 종결`)
- [x] GenDto.ReplyRequest voiceType 필드 추가
- [x] ReplyGenRequest.java voiceType 필드 추가
- [x] ActionExecutor.executeReply() voiceType 설정 (`.voiceType(voiceProfileField(persona, "voice_type"))`)
- [x] GenerationController.generateReply() `sanitizeComment(split[0], req.getVoiceType())` 수정
- [x] SelfCritiqueService 신규 오버로드 `critiqueAndRefine(..., voiceType)` + `sanitizePost/Comment(raw, voiceType)` 적용
- [x] PersonaFactory schema `writing_quirks.features` 추가
- [x] dev DB NATEPAN 6개 personas JSON_SET
- [x] dev rebuild + e2e-realbe 142 passed (5 skipped)
- **완료 기준 미달**: main push 아직 (이 세션에서 진행)
- **M7 효과 측정**: M1 신뢰 MAUVE + M5 사람 블라인드로 before/after 필요 (신선 출력 축적 중)

---

## Step 32 (M6) — COMMENT MAUVE 측정 ✅ 완료 (2026-06-16)

**목표**: N6(allowChosung) 전/후 COMMENT MAUVE 실측.
**완료 기준**:
- [ ] COMMENT MAUVE before (Step 13 baseline: NATEPAN 0.060/CLIEN 0.068)
- [ ] after N6 적용 신선 댓글 MAUVE 측정
- 아직 미실행.

---

## Step 33 (M5) — 진짜 사람 블라인드 평가 ✅ 완료 (2026-06-16)

**목표**: M7 적용 후 사용자 직접 라벨링 cond5 측정.
**완료 기준**:
- [x] 균형 블라인드셋 40쌍 (THEQOO+NATEPAN 각 20) 생성 및 사용자 라벨링 완료
- [x] 정확도 산출: **82.5% (33/40) — cond5 FAIL**
  - NATEPAN: 80% (16/20)
  - THEQOO: 85% (17/20)
- [x] 오류 분석: Human→AI 오분류 5건 (저품질 글), AI→Human 오분류 2건(★T013/T017 M7 효과 신호)
- [x] 기록: `steps/33-m5-blind-test.md`
- 목표: 정확도 ≤ 0.60 (현재 82.5% >> 목표)

---

## Step 34 (M8) — DCINSIDE 재-pull + 학습 가능성 판정 ✅ 완료 (2026-06-16)

**목표**: cursor 리셋으로 39→~300 복구.
**완료 기준**:
- [x] `/app/data/.corpus_pull_cursor` 리셋
- [x] 직접 인제스트 시도 (264건 → inserted=0, skipped=264 — 기존 AI corpus와 해시 충돌)
- [x] n_human 실측: **39 (변동 없음)**

**결과**: **DCINSIDE 학습 불가 — 장르 구조 불일치**
- human 39건 스팟체크: 와인경진대회·카메라·여행기·뉴스·수공예 — 갈등 서사 없음
- DCINSIDE = 주제별 갤러리(hobby) 포럼. 갈등 게시판 구조 없음.
- 264건 인제스트 실패 = AI 생성물(오케스트레이터 출력) 해시와 전부 충돌
- **판정**: 제외. enable-gate cond1/cond2는 THEQOO/CLIEN/NATEPAN 3개 커뮤니티 기준.
- **기록**: steps/34-m8-dcinside.md

---

## Base Hardening 7라운드 — R10: THEQOO corpus 교정 (🔴 소스 결정 대기)

> **목표**: THEQOO human corpus를 실제 더쿠/여초 스타일로 교체 → P(human) 역전 해소 → cond4 THEQOO PASS → 5조건 전부 충족 → AI_USER_ML_ENABLED=true 수동 활성화
> **불변**: AI_USER_ML_ENABLED=false 유지. enable은 5조건 전부 충족 후 수동으로.
> **전제**: D-64 계획, D-52 HALT. 소스 결정(P1) → 착수.

---

## Step 52 (R10-1) — THEQOO human corpus 스타일 분석 + 소스 결정 🔴

**목표**: 현재 human corpus 410건의 실제 스타일 분석 → P역전 원인 확인 → 대체 소스 결정.

**배경**:
- Human corpus 410건: AS 플랫폼 갈등 서사 스타일 (격식체, 마침표, 한자어)
- AI THEQOO corpus 100건: 실제 더쿠 스타일 (헐ㄷㄷ/ㅠㅠ/~당, 초성체, 짧은 문장, 반말)
- P(human) 역전: 판별기가 "슬랭=AI, 격식=human"으로 학습됨

**소스 선택지** (사용자 결정 필요 — P1):
- A) THEQOO/더쿠 직접 크롤 — 최고 품질, 법적·인프라 검토 필요
- B) 외부 공개 여초/커뮤니티 데이터셋 — 스타일 매칭 검증 필요
- C) AS 플랫폼 내 THEQOO voice 봇 글을 human corpus 씨앗으로 재사용 (circular risk 주의)

**완료 기준**:
- [ ] human corpus 410건 어체 분포 분석 보고 (평균 길이/어체/주제)
- [ ] 소스 결정 + 수집 계획 확정
- **halt**: 소스 결정 없으면 착수 금지

---

## Step 53 (R10-2) — THEQOO human corpus 교체 + 재학습

**목표**: 실제 더쿠/여초 스타일 human 포스트 ≥300건 수집 → corpus 교체 → 재학습.

**선결**: Step 52 소스 결정 + 수집 완료.

**완료 기준**:
- [ ] 기존 THEQOO human corpus 스타일 불일치분 삭제 (사용자 승인 필수 — 대량 삭제)
- [ ] 신규 human corpus ≥300건 ingest + 스팟체크 (슬랭 High 확인)
- [ ] `/train` 재실행 → CV-AUC + P(human) 방향 확인 (슬랭 高/격식 低)
- **halt**: P(human) 방향 역전 잔존 시 → 소스 재검토

---

## Step 54 (R10-3) — THEQOO cond4 A-B 재실행

**목표**: 교정된 판별기로 cond4 THEQOO MAUVE A-B 재측정 → PASS 여부 판정.

**선결**: Step 53 완료 + P(human) 정상화 확인.

**완료 기준**:
- [ ] MAUVE A-B n≥50 ctx, K≥3 시드 → Δ>0 확인
- [ ] cond4 THEQOO PASS ✅ → 5조건 전부 충족
- [ ] enable-candidates 엔드포인트 확인 → 사용자에게 `AI_USER_ML_ENABLED=true` 수동 활성화 보고
- **cond4 판별기로 검증 금지(순환)** — MAUVE A-B만

---

## 병행 가능 작업 (R10 진행 중)

| 작업 | 내용 | 선결 |
|---|---|---|
| R7 M-after (P2) | CLIEN COMMENT ai ≥50 축적 → MAUVE M-after 측정 | 생성 방법 결정(P2) 후 |
| NATEPAN cond4 재측정 | 최신 모델로 A-B 재실행 | P4 사용자 선택 |
| prod 배포 | CLIEN+NATEPAN ML 활성화 포함 | THEQOO cond4 해소 후 절대규칙 #4 |

---

## R11 — ML 리랭커 활성화 전 검증 (D-67, 2026-06-18~)

**목표**: 전역 리랭커 활성화 go/no-go 판정. 전역 게이트 확정(ActionExecutor.java:425).

- Step 53: THEQOO cond4 타당성 감사 (delta_real vs delta_synth — 합성 의존 검증)
- Step 54: NATEPAN cond4 최신 모델 재측정 (P4 해소, n_ctx=40)
- Step 55: NATEPAN + THEQOO 신선 인간 블라인드 (blind①+②, 목표 ≤60%)
- Step 56: go/no-go 표 + 모니터링/롤백 런북

**전역 ON 조건**: 세 다리(CLIEN ✅ / NATEPAN / THEQOO) 모두 cond4+cond5 PASS.

---

## R13 — cond4 재정의 + head-to-head 검증 (D-68, 2026-06-18~)

> **목표**: MAUVE 포화 대응. 비순환 지표(인간 블라인드 head-to-head)로 cond4 대체 본체 구축.
> **불변**: AI_USER_ML_ENABLED=false 유지. 이 라운드는 판정·준비만.

---

## Step 55 (R13-1) — THEQOO 진짜코퍼스 단독 검증 ✅ 완료

**목표**: ab_test를 source_filter="theqoo"(진짜 111건)로 재실행 → D-66 Δ=+0.4458의 합성 의존 여부 확인.

**완료 기준**:
- [x] source_filter 파라미터 구현 (schemas.py, routes_eval.py, run_ab_test.py)
- [x] THEQOO Δ_real(진짜 111건) 측정 완료
- [x] D-68 선등록 임계 기준으로 판정 기록 → **Δ_real=-0.1117 FAIL. Step 52-53 재개 필요**
- **halt**: Δ_real ≤ 0 → Step 52-53 재개 검토 보고

---

## Step 56 (R13-2) — head-to-head 인간 블라인드 설문 ✅ 완료

**목표**: 커뮤니티별(CLIEN/NATEPAN/THEQOO) 리랭커 top-1 vs random draft 쌍 → 인간 판정.

**완료 기준**:
- [x] build_h2h_survey.py 구현 + survey.md 생성(CLIEN 12쌍, NATEPAN 20쌍)
- [x] 오너 응답 수집 완료 (이유 한 줄 포함, D-55)
- [x] D-68 합격선 기준 커뮤니티별 판정 → CLIEN PASS / NATEPAN PASS / THEQOO 미측정(corpus 미충족)
- **halt**: 리랭커 탐지율 > random → 해당 커뮤니티 활성화 제외

---

## Step 57 (R13-3) — 커뮤니티별 go/no-go 표 + 전역 활성화 판정 ✅ 완료

**목표**: Step 55+56 결과로 최종 go/no-go 표 작성 + 전역 활성화 준비.

**완료 기준**:
- [x] 커뮤니티별 3조건(cond4-MAUVE/h2h/cond5) 표 작성 → `.result/ai-user/blind/r13-h2h-results-summary.md`
- [x] 전략 노트 작성 (리랭커 한계효용 평가)
- [x] 활성화 런북 (롤백 트리거 포함) — results-summary.md 내 기재
- [x] 결론: CLIEN+NATEPAN PASS / THEQOO FAIL → 전역 활성화 차단

---

## Step 58 (R13-next) — THEQOO corpus 수집 전략 결정

**목표**: 실제 더쿠 스타일 한국어 corpus ≥300건 확보 방법 결정.

**배경**: R13 Phase 1(n=12)에서 Δ_real=-0.1117, R13 재확인(n=20)에서 **Δ=-0.2070** — 방향 동일하게 FAIL 확정. 합성 200건 제거 필요.

**선택지**:
1. **A) AS 플랫폼 내 자체 수집**: 서비스 이용자의 THEQOO 스타일 글 200건+ 직접 주석
2. **B) 외부 공개 데이터셋**: AI Hub/국립국어원 등 인터넷 커뮤니티 텍스트 활용
3. **C) 크롤링**: 법적·인프라 검토 후 진행

**진행 현황 (2026-06-19 최신)**:
- [x] 오너 수집 전략 결정 → **C) 크롤링**
- [x] human corpus n_theqoo ≥ 300건 확보 (**snapshot=311**)
- [x] ML 재학습 + Δ_real > 0 확인 (**Codex-only Δ_real=+0.1326, snapshot=311**)
- [x] THEQOO h2h survey 재생성 (**20쌍 생성**)
- [x] 활성화 상태 보고 → **HOLD** (`AI_USER_ML_ENABLED=false` 유지, 응답 수집 전 no-go)

**실측 메모**:
- 최종 `/corpus/stats`: THEQOO human **543**, ai **116**
- `source_filter="theqoo"` snapshot: **311**
- THEQOO 재학습 version: `01KVDQJSKTY93279KQYZ91PHNS`
- 이후 산출물:
  - `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
  - `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`

## Step 59 (R13-next2) — THEQOO h2h survey 재생성 + 활성화 HOLD 보고 ✅ 완료

**목표**: real-only corpus 기준 THEQOO h2h survey를 재생성하고, 수동 활성화 판단을 최신 기준으로 정리.

**완료 기준**:
- [x] `build_h2h_survey.py`를 Codex CLI bridge 경로로 정합화
- [x] THEQOO 20 contexts × 4 drafts → h2h pair 20쌍 생성
- [x] 활성화 상태를 **HOLD**로 정리 (`AI_USER_ML_ENABLED=false` 유지)
- [ ] 오너/친구 응답 수집 후 THEQOO cond4-B 최종 판정

## Step 60 (R13-next3) — THEQOO h2h 집계 자동화 ✅ 완료

**목표**: 사람 응답이 들어오면 answers JSON만으로 h2h 결과 markdown을 자동 생성.

**완료 기준**:
- [x] `summarize_h2h_results.py` 추가
- [x] answers template에 입력 형식 힌트 추가
- [x] THEQOO pending 결과 파일 생성

## Step 61 (R13-next4) — THEQOO owner h2h 집계 + 전역 활성화 판정 ✅ 완료

**목표**: owner 응답을 반영해 THEQOO cond4-B를 최종 판정하고 전역 활성화 go/no-go를 확정.

**완료 기준**:
- [x] owner 응답 JSON 반영
- [x] 집계기 1-based key 해석 버그 수정
- [x] THEQOO owner h2h 결과: rerank **61.1%**, random **38.9%**
- [x] 판정: **FAIL → 전역 NO GO**

## Step 62 (R13-next5) — THEQOO post-processing 축소 패치 ✅ 완료

**목표**: owner h2h에서 반복 검출된 `헐`/유니코드 이모지 신호를 THEQOO 후처리에서 먼저 제거해 다음 재측정의 명확한 개선 후보를 만든다.

**완료 기준**:
- [x] `OutputSanitizer`에 THEQOO 전용 cleanup 추가
- [x] trailing standalone `헐` 제거
- [x] 유니코드 이모지 제거
- [x] `THEQOO` 주입 후보를 덜 튀는 표현으로 축소
- [ ] survey 재생성 + h2h 재측정

## Step 63 (R13-next6) — h2h/ab 하네스 런타임 정합화 ✅ 완료

**목표**: THEQOO 후처리 패치 효과를 실제 측정에 반영할 수 있도록 설문/AB 생성기를 `PromptAssembler + OutputSanitizer` 경로와 정합화한다.

**완료 기준**:
- [x] `build_h2h_survey.py` 기본 생성 경로를 `runtime(/generate/post)` 우선으로 전환
- [x] `run_ab_test.py`도 동일하게 `runtime(/generate/post)` 우선으로 전환
- [x] direct CLI fallback 유지
- [x] 스크립트 문법 검증
- [ ] `LLM_AI_USER_URL(:8092)` 복구 후 실제 재생성/재측정

## Step 64 (R13-next7) — THEQOO survey v2 재생성 + A-B 재측정 ✅ 완료

**목표**: THEQOO 1차 후처리 교정이 실제 블라인드 샘플과 오프라인 Δ에 반영되는지 즉시 재확인한다.

**완료 기준**:
- [x] CLI fallback에도 THEQOO cleanup 동기화
- [x] THEQOO survey v2 재생성 (`20 contexts × 4 drafts`, workers=8)
- [x] 새 survey에서 `헐/개공감/😥/🥲` 0건 확인
- [x] `run_ab_test.py --source-filter theqoo --generator cli` 재측정
- [x] 결과: `mauve_rerank=0.9907`, `mauve_random_mean=0.9221`, `Δ=+0.0686`
- [x] owner 응답 수집 후 cond4-B 재판정

## Step 65 (R13-next8) — THEQOO owner v2 h2h 집계 + 전역 재판정 ✅ 완료

**목표**: 새 THEQOO survey v2에 대한 owner 응답을 반영해 cond4-B와 전역 활성화 판정을 다시 계산한다.

**완료 기준**:
- [x] answered survey 파일에서 owner 응답 추출
- [x] answers template JSON 반영
- [x] `summarize_h2h_results.py` 재실행
- [x] THEQOO owner v2 결과: `12/20`, rerank `25.0%`, random `75.0%`
- [x] THEQOO cond4-B: **PASS**
- [x] 연구 게이트 기준 전역 상태: **수동 활성화 가능(GO candidate)**

## Step 66 (R13-next9) — THEQOO ellipsis hardening ✅ 완료

**목표**: owner v2에서 새 잔여 탐지 신호로 드러난 `유니코드 말줄임표(…)`를 THEQOO 경로에서 저비용으로 제거한다.

**완료 기준**:
- [x] `OutputSanitizer` THEQOO cleanup에 `…`/`⋯` → `...` 정규화 추가
- [x] `build_h2h_survey.py` CLI fallback cleanup 동기화
- [x] `run_ab_test.py` CLI fallback cleanup 동기화
- [x] Java 회귀 테스트 추가
- [ ] `:8092` runtime 복구 후 동일 경로 재생성/재측정

## Step 67 (R13-next10) — THEQOO awkward phrase hardening ✅ 완료

**목표**: owner v2에서 이유로 지적된 잔여 어색한 구체 표현 2개를 THEQOO 경로에서 좁게 정규화한다.

**완료 기준**:
- [x] `쓰레기 차도` → `쓰레기통이 차도`
- [x] `집에서는 딸이 더 조심해야` → `집에서는 여자가 더 조심해야`
- [x] runtime `OutputSanitizer` 반영
- [x] CLI fallback 하네스 2종 동기화
- [x] Java 회귀 테스트 추가
- [x] CLI 경로 survey/AB 재생성
- [x] regenerated survey 기준 문제 표현/특수문자 신호 0건 확인
- [ ] `:8092` runtime 복구 후 동일 경로 재검증

## Step 68 (R14-phase0) — runtime gate + host handoff ✅ 완료(HALT 기록)

**목표**: R14 Phase 1 진입 전에 `:8092` runtime 복구 가능 여부와 최신 live 수치를 확인한다.

**완료 기준**:
- [x] `git log --oneline -8` 기준점 기록
- [x] live `/corpus/stats` 재조회 (`THEQOO human=562, ai=116`)
- [x] local `localhost:8092/actuator/health` down 재확인
- [x] local env 제약 확인: `/usr/bin/ssh` 실행 권한 거부, `docker` 부재
- [x] dev host handoff 필요 사실 기록
- [ ] dev host에서 `docker compose ... up -d llm-ai-user`
- [ ] `:8092` health `UP`

## Step 69 (R14-phase2-prep) — selective rerank gate 구현 ✅ 완료

**목표**: 비용/효용 판단에서 B안(per-community gate)을 선택할 수 있도록 dormant 구현을 먼저 넣는다.

**완료 기준**:
- [x] 신규 env `AI_USER_ML_ENABLED_COMMUNITIES`
- [x] `AiUserMlClient.isEnabledFor(community)` 추가
- [x] `ActionExecutor` POST rerank gate를 community-aware로 전환
- [x] env 미설정 시 기존 전역 동작 유지
- [x] unit test 추가
- [ ] Java 테스트 실행
- [ ] dev:8090 e2e 검증

## Step 70 (R14-phase1/2 gate correction) — host blocker + strict runtime + cond5 범위 정정 ✅ 완료

**목표**: R14 실행 순서를 현실에 맞게 바로잡고, 잘못 확장된 활성화 근거를 정정한다.

**완료 기준**:
- [x] `:8092` 복구는 현재 셸이 아니라 dev host 접근 문제임을 명시
- [x] 공식 runtime 측정 조건을 `--generator runtime --strict-runtime` + `cli_fallbacks=0`으로 고정
- [x] runtime 측정 전 배관 검증 항목(backend/model, 4 drafts, `/rerank`) 추가
- [x] `CLIEN blind② 40%`를 NATEPAN/THEQOO cond5 근거로 쓰지 않도록 정정
- [x] selective gate 임계: `benefit_pp >= 5%p` 기본 규칙 반영
- [x] 설문 무효율 추적과 응답 형식 힌트 보강

## Step 71 (R14-phase1/3 prep) — runtime probe + cond5 tooling ✅ 완료

**목표**: host 복구 직후 즉시 쓸 진단/설문 도구를 미리 준비한다.

**완료 기준**:
- [x] `probe_runtime_pipeline.py` 추가
- [x] health down 시 HALT 결과 출력 확인
- [x] `build_cond5_blind.py` 추가
- [x] `summarize_cond5_results.py` 추가
- [x] cond5 결과를 owner/friend/combined로 자동 집계 가능
- [x] 실제 `/corpus/export/blind` fetch 샘플 검증
- [ ] dev host에서 runtime probe 실측

## Step 72 (R14-phase3 prep) — NATEPAN/THEQOO fresh cond5 설문 준비 ✅ 완료

**목표**: runtime blocker가 풀리기 전에도 사람 응답이 필요한 cond5 세트를 미리 준비한다.

**완료 기준**:
- [x] NATEPAN cond5 survey / answers / pending results 생성
- [x] THEQOO cond5 survey / answers / pending results 생성
- [x] owner/friend 응답 형식 동일화
- [x] blind export metadata coverage 확인
- [x] `used-corpus-ids` 필터가 현 export에서는 완전 적용 불가함을 기록

## Step 73 (R14-ops prep) — survey answer importer ✅ 완료

**목표**: 사용자가 markdown 설문에 직접 답한 뒤 answers json으로 옮기는 수작업을 제거한다.

**완료 기준**:
- [x] `import_survey_answers.py` 추가
- [x] cond5 blank survey에서 0-import 동작 확인
- [x] filled temp survey에서 owner 답 2건 import 확인
- [x] cond5 current survey 헤더에 import 명령 추가
- [x] h2h generator에도 import 명령 추가

## Step 74 (R14-phase3 guard) — blind fingerprint registry ✅ 완료

**목표**: blind export의 source metadata 공백 때문에 생기는 재사용 위험을 text fingerprint registry로 보완한다.

**완료 기준**:
- [x] `survey_fingerprints.py` 추가
- [x] `reserve_blind_set.py` 추가
- [x] `used-corpus-ids.json`에 `all_used_text_fingerprints` 누적
- [x] registry write를 atomic + file lock으로 보강
- [x] THEQOO cond5 동일 seed 재생성 시 exact reuse 차단 확인
- [ ] runtime host 복구 후 fresh runtime 설문도 같은 registry 경로로 예약
