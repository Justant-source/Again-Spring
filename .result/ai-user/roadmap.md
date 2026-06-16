# 로드맵 — Step 0–7 마스터 체크리스트

> **하드 순서(연구문서 강제):** 평가 하네스·베이스라인(Step 3)은 모든 최적화보다 먼저.
> Best-of-N(Step 5)은 판별기(Step 4) 이후. SelfCritique 개편(Step 6)은 베이스라인 이후.

---

## Step 0 — 기록 시스템 + WSL 서비스 스캐폴드 ← **현재**

**목표:** `.result/ai-user/` 기록 생성. WSL에 `~/Data/Again-Spring-AI-User` 골격(ASM 미러).  
**입력:** ASM 구조(`~/Data/Again-Spring-Marketing/`), 탐사 결과.  
**산출:**
- `.result/ai-user/` 기록 파일들 (`README`, `STATE`, `roadmap`, `decisions`, `context/`)
- WSL: `~/Data/Again-Spring-AI-User/` — compose, CUDA Dockerfile, FastAPI `/health`, `aiuser-ml-db`, `worker/callback.py`, `/score`·`/rerank`·`/corpus`·`/eval`·`/train` 스텁.
- git repo 초기화

**완료 기준:**
- [ ] AS 호스트에서 `curl -H "Authorization: Bearer aiuser-ml-api-token-dev-2026" http://100.115.252.61:8201/health` → 200
- [ ] 응답에 `"gpu_available": true`
- [ ] WSL: `docker exec aiuser-ml python -c "import torch; print(torch.cuda.is_available())"` → `True`
- [ ] `/corpus/stats` 정상 응답 (빈 dict)

---

## Step 1 — KatFishNet 피처 추출기 + Korean POS in Docker

**목표:** `ml/pos_tagger.py` + `ml/features_katfish.py` + 단위 테스트.  
**입력:** KatFishNet 리포 참고(피처 추출기만, 벤치 데이터 미사용), Step 0 서비스.  
**산출:** DC/네이트판 샘플로 쉼표/띄어쓰기/품사 n-gram 피처 벡터 산출.  
**완료 기준:**
- [ ] `pytest tests/test_features.py` 통과 (샘플 한국어 문자열 → 합리적 피처 값)
- [ ] GPU 미사용 확인

---

## Step 2 — 코퍼스 파이프라인

**목표:** learning에 `GET /examples/export` 추가. ML 서비스 `/corpus/ingest` + 풀 스케줄. AS가 작성 시점에 AI negative push.  
**입력:** `ai-user/learning/app/api/examples.py`(`example_bank` 스키마), `AiLearningClient` 패턴.  
**산출:** `/corpus/stats`에 커뮤니티별 human 다수·AI(초기 소수) 라벨 적재.  
**완료 기준:**
- [ ] learning `GET /examples/export?sourceClass=human&limit=5` → 실제 크롤 데이터 반환
- [ ] `/corpus/stats` 커뮤니티별 human/ai 카운트 표시
- [ ] 해시 dedup 동작

---

## Step 3 — 평가 하네스 + 베이스라인 ⛔ 최적화 게이트

**목표:** `ml/eval_harness.py`(MAUVE·종결어미 JS발산·버스티니스·쉼표/띄어쓰기율·품사다양성) + async 잡 + 콜백/폴링. 현재 AI vs human 커뮤니티별 베이스라인.  
**입력:** Step 1–2.  
**산출:** 주요 커뮤니티 베이스라인 메트릭 `eval_run` + `data/eval/*.json` + `.result/ai-user/` 리포트.  
**완료 기준:**
- [ ] `POST /eval/baseline` 잡 완료 → 메트릭 JSON 저장
- [ ] MAUVE, 쉼표율, 종결어미 JS-div 값 존재 (≥3개 커뮤니티)
- [ ] `.result/ai-user/steps/03-baseline-report.md` 작성
- **이 스텝 완료 전 Step 4~6 착수 금지.**

---

## Step 4 — 판별기 학습 + 스코어 엔드포인트

**목표:** `ml/discriminator.py`(KcELECTRA-base + KatFishNet 피처 LR 스태킹) + `ml/train_pipeline.py`(GPU, VRAM 가드) + `ml/registry.py`. 커뮤니티별 AUC. `/score`·`/rerank` CPU 추론.  
**입력:** 코퍼스(Step 2), 피처(Step 1).  
**산출:** 커뮤니티별 체크포인트 + AUC 리포트. `/rerank` 실제 랭킹 동작.  
**완료 기준:**
- [ ] `/rerank` 커뮤니티별 실제 humanProb 점수 반환 (CPU)
- [ ] AUC 리포트 `.result/ai-user/steps/04-auc-report.md`
- [ ] 모델 부재 시 `degraded=true`, neutral 0.5 반환
- [ ] 학습 후 `nvidia-smi` VRAM 해제 확인

---

## Step 5 — AS Best-of-N 와이어링

**목표:** `AiUserMlClient.java` 신규. `ai-user-ml:` 프로퍼티. `ActionExecutor.executePost/Comment`에 N생성+`/rerank`+winner. 기본 `enabled=false`. WSL 다운 시 기존 경로 폴백.  
**입력:** Step 4. `ActionExecutor.java`(L336-477 executePost, L179-263 executeComment), `AiLearningClient.java` 패턴.  
**산출:** AS orchestrator가 ML 서비스로 리랭킹 요청.  
**완료 기준:**
- [ ] `enabled=false` → 현행 동일 (바이트 동일 동작)
- [ ] `enabled=true` + WSL up → POST 작성 시 winner 선택 확인 (로그)
- [ ] WSL down → 무에러 폴백 (orchestrator 로그)
- [ ] orchestrator 단위테스트 갱신
- [ ] AI negative push(작성 시점) 동작

---

## Step 6 — 분포매칭 개편 (AS 측 llm)

**목표:** `SelfCritiqueService`(전역 하드패널티 → 커뮤니티 실측 분포 대조 + 쉼표율 신규). `OutputSanitizer`(결정론적 → `voices.yml` `post_processing` 기반 확률적).  
**입력:** Step 3 베이스라인 메트릭 (커뮤니티별 comma_rate 등). `SelfCritiqueService.java`, `OutputSanitizer.java`, `voices.yml`.  
**완료 기준:**
- [ ] `SelfCritiqueServiceTest` 갱신 + 통과
- [ ] `OutputSanitizerHrTest` 갱신 + 통과
- [ ] `voices.yml`에 12개 커뮤니티 `post_processing` 블록 추가
- [ ] `cd frontend && npm run lint:words && npm run lint:docs` 통과

---

## Step 7 — 주기 갱신 + 모니터링 (상시)

**목표:** 스케줄 코퍼스 재pull + `/train refresh` + 주기 `/eval` AUC/MAUVE 드리프트 추적. 다양성·에코체임버 메트릭.  
**완료 기준:**
- [ ] 시계열 메트릭 추적 가능
- [ ] 커뮤니티별 "완료" 기준(AUC≤0.55, MAUVE≥0.90) 측정 가능

---

## ⚠️ Base Hardening — Step 10~17 (2026-06-16 추가)

> **관점 교정**: Step 9 ready_count=4/4 / AUC 0.98~1.0은 성공이 아니라 **측정의 시작점**이다.
> 프로젝트 성공 = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%.
> "AUC≥0.55=ready"는 **"리랭커 배포 가능"**만 의미 — 절대 "사람 같다"가 아님.
> 현재 AUC 수치 자체가 신뢰 불가 (단일split+소표본누수·합성음성위조·readiness카운팅버그).
> **Phase A(Step10~14): WSL, 토큰0 / Phase B(Step15): 토큰소량 / Phase C(Step16~17): AS-side, 배포게이트**
> **전 작업 `AI_USER_ML_ENABLED=false` 유지. enable은 게이트 충족 후 사람이 수동으로 켠다.**
> **하드 순서**: 10→11→12 반드시 먼저. 13·14는 11 이후. 15는 12 이후. 16·17은 15 이후.

---

## Step 10 (T1) — DCINSIDE 문장 분리기 수정 ✦Phase A

**목표:** 문장 분리기를 2곳에서 1개 공유 함수로 통일. 다양한 경계(ㅋㅋ/개행/이모지/!/?/...) 인식.
**입력:** `features_katfish.py:93-99` (re.split `[.!?]`) + `eval_harness.py:49-53` (`_split_sentences` `(?<=[다요여임나죠])\.|\n+`) — 두 곳에 중복, 서로 다른 정규식.
**산출:** `features_katfish.py`에 `split_sentences()` 공유 함수. `eval_harness.py`가 import. 테스트 DC 스타일 케이스 추가.
**완료 기준:**
- [ ] DCINSIDE `avg_sentence_length` **<~20** (수정 전 57.40)
- [ ] 4개 커뮤니티 `/eval/baseline` 재실행 → before/after 표를 `steps/10-splitter-fix.md`에 기록
- [ ] `pytest tests/test_features.py tests/test_eval_harness.py` 전체 통과

---

## Step 11 (T2) — 신뢰 가능한 AUC ✦Phase A (Step 10 이후)

**목표:** 단일 split AUC + 합성 음성 위조를 제거. stratified 5-fold CV mean±std + 피처 ablation + C 선택 CV.
**입력:** `train_pipeline.py:120-143` (위조로직 + 단일split). sklearn 1.9.0 확인됨 (cross_val_score, StratifiedKFold 사용 가능).
**산출:** `ModelVersion.auc`=CV mean. CV std·ablation·C는 `EvalRun(kind="cv").metrics_json` 저장. 합성위조 경로 → INSUFFICIENT_DATA 게이팅 대체.
**완료 기준:**
- [ ] 모든 커뮤니티: CV-AUC mean±std **또는** `INSUFFICIENT_DATA` (단일 1.000 소멸)
- [ ] POST 실제 n_ai<100 OR n_human<300 → 학습 스킵 + INSUFFICIENT_DATA 마킹
- [ ] ablation 표(KatFishNet-9 / KcELECTRA-768 / 777) `steps/11-cv-auc.md`에 기록

---

## Step 12 (T3) — readiness 게이트 버그 수정 ✦Phase A (Step 11 이후)

**목표:** n_ai 카운트를 POST-only로 수정. ready 의미 = "리랭커 배포 가능 (NOT 사람 같음)" 명시. 임계 상향.
**입력:** `routes_metrics.py:30-41` + `retrain_loop.py:57-62` — 두 곳 모두 content_type 무필터. `config.py:40-41` (retrain_min=30, auc_target=0.55).
**산출:** 두 곳 모두 `.filter(content_type=="POST")` 추가. ready = POST n≥100 AND CV-AUC≥0.75. 코드 주석 갱신.
**완료 기준:**
- [ ] **NATEPAN `ready=false`** (`/metrics/readiness` 응답)
- [ ] n_ai가 POST 전용 카운트로 변경됨 확인
- [ ] `ready` 응답에 "reranker-deployable (NOT human-like)" 주석/필드 존재

---

## Step 13 (T4) — COMMENT 측정 추가 ✦Phase A (Step 11 이후)

**목표:** eval harness를 POST·COMMENT별로 분리 측정. 학습은 POST 전용 유지, 측정만 확장.
**입력:** `routes_eval.py:22-98` (`_run_eval_baseline`, content_type 단일 스칼라). `EvalRun.content_type` 컬럼 이미 존재(models.py:46).
**산출:** `_run_eval_baseline`이 POST·COMMENT 루프. `data/eval/{job_id}.json` = `{"POST":{...},"COMMENT":{...}}`. EvalRun 행 (community, POST/COMMENT)당 1행.
**완료 기준:**
- [ ] `/eval/baseline` 실행 → `/metrics/history`에 커뮤니티별 **COMMENT MAUVE/지표** 존재
- [ ] POST 측정 결과 이전과 동일 (회귀 없음)

---

## Step 14 (T7) — ENABLE 게이트 정의·구현 ✦Phase A (Step 12·13 이후) **ENABLE 변경 금지**

**목표:** 커뮤니티별 5조건 enable-candidate 게이트 코드화. 현재 상태 보고 (대부분 미충족=정상).
**5조건:**
1. POST 실제 n_ai≥100 AND n_human≥300 (synthetic 0)
2. CV-AUC mean≥0.75 AND std≤0.1
3. T1 클린 피처 확인
4. 오프라인 A-B `MAUVE(rerank) > MAUVE(random)` + 지표 퇴행 없음
5. 사람 블라인드 baseline 확보
**입력:** `routes_metrics.py` 패턴. `EvalRun(kind="cv"|"ab_test"|"human_blind")` 읽기.
**산출:** `GET /metrics/enable-candidates` 신규 엔드포인트.
**완료 기준:**
- [ ] 엔드포인트 응답 = 커뮤니티별 5조건 상태 + `enable_candidate` 불리언
- [ ] 현재 상태 보고를 `steps/14-enable-gate.md`에 기록
- [ ] **`AI_USER_ML_ENABLED` 변경 없음**

---

## Step 15 (T6) — 독립 검증 harness ✦Phase B (Step 14 이후, 토큰 소량)

**목표:** 순환 검증 차단. Best-of-N 효과를 MAUVE + 사람 블라인드로만 검증.
**순환 위험:** rerank = 판별기 argmax → rerank를 판별기 점수로 평가하면 순환 → **반드시 MAUVE/사람 블라인드**.
**산출:**
- (a) 오프라인 A-B: AS LLM에서 고정 컨텍스트×N=4 초안 생성 → ML `/rerank` vs random → 두 AI 집합을 각각 human 대비 MAUVE → `EvalRun(kind="ab_test")`.
- (b) 사람 블라인드 export 스크립트(CorpusItem → JSONL, 라벨 숨김) + 정확도 집계기 → `EvalRun(kind="human_blind")`.
**완료 기준:**
- [ ] A-B `MAUVE(rerank)−MAUVE(random)` delta 기록
- [ ] 사람 블라인드 baseline 정확도 기록 (목표~50%)
- [ ] `steps/15-independent-validation.md` 작성

---

## Step 16 (T5) — POST 샘플 보강 + 신선 재수집 ✦Phase C (AS-side, 배포 게이트)

**목표:** POST 희소 voice(NATEPAN 등)에 실제 AI POST 축적. Step 6 이후 신선 출력 우선.
**입력:** AS 오케스트레이터 시딩/스케줄. `AI_USER_ML_COLLECT=true` 유지.
**완료 기준:**
- [ ] 대상 커뮤니티 POST 실제 n≥100 도달
- [ ] 신선 출력으로 CV-AUC/MAUVE 재측정
- [ ] 절대 규칙 #4 (dev→e2e→push→prod) 준수

---

## Step 17 (T8) — THEQOO TSD 프롬프팅 ✦Phase C (Step 14 이후, AS-side)

**목표:** THEQOO (최저 MAUVE 0.345) 생성 프롬프트에 CAT-LLM TSD 주입. 첫 측정 기반 생성 개선.
**입력:** THEQOO human 분포 (단어수준 POS 비율, 문장수준 길이/리듬/쉼표). AS LLM 생성 프롬프트 / `voices.yml`.
**완료 기준:**
- [ ] THEQOO 신선 출력 재수집 → MAUVE before/after delta 기록 (개선 없어도 정직히)
- [ ] 롤백 가능 유지
- [ ] `steps/17-tsd-theqoo.md` 작성
