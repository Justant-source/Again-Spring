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
