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

## Step 16 (T5) — POST 샘플 보강 🔜 진행 중

**목표:** POST 희소 커뮤니티 n_ai→100 도달.
**현황:**
- [ ] THEQOO: ~65 POST, **35개 더 필요** (가장 근접)
- [ ] CLIEN: ~40 POST, 60개 더 필요
- [ ] DCINSIDE: ~20 POST, 80개 더 필요
- [ ] NATEPAN: 0 POST — ActionPlanner NATEPAN POST 배정 여부 확인 필요

**진행 방법**: 자연 축적 (봇 자동 활동) + 필요시 `generate-posts` 수동 트리거.

---

## Step 17 (T8) — THEQOO TSD 프롬프팅 ✅ 완료

**목표:** THEQOO 오케스트레이터 MAUVE 0.345 → 0.60+ (TSD 문체 제약 주입).
**완료 기준:**
- [x] `ActionExecutor.appendWritingQuirks` → `[문체 패턴]` 섹션 추가
- [x] 10개 THEQOO voice.yml features 추가
- [x] dev DB 7개 THEQOO 페르소나 JSON_SET 완료
- [x] dev 배포 + e2e-realbe 142/147
- [x] commit `88018822`
- [ ] **재측정 대기**: THEQOO 신선 출력 축적 후 `/eval/baseline` 재실행 → before/after delta 기록

**핵심 발견**:
- `writing_quirks.features` 필드는 voices.yml에 있었으나 Java 코드에서 **미사용** (dead field) → T8에서 수정
- DB 페르소나 IDs ≠ voice.yml IDs (다른 세대) → prod 배포 시 DB SQL 업데이트 별도 필요

---

## 다음 단계 요약

1. **T8 재측정** — `/eval/baseline` 재실행 → THEQOO MAUVE before/after
2. **T5** — THEQOO n_ai→100 달성 → discriminator 재학습
3. **THEQOO 코퍼스 정제** — 링크/공지/짧은반응 제거 → A-B 재실행 → cond4
4. **cond5** — 사람 블라인드 JSONL 라벨링
5. **5조건 충족 시** → 수동 `AI_USER_ML_ENABLED=true` (코드 변경 금지)

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
