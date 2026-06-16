# Step 0 완료 기록 — 기록 시스템 + WSL 서비스 스캐폴드

**날짜**: 2026-06-15  
**세션**: 1  
**상태**: ✅ 완료 (모든 완료 기준 통과)

## 완료 기준 검증 결과

- [x] `curl -H "Authorization: Bearer aiuser-ml-api-token-dev-2026" http://100.115.252.61:8201/health` → **200**
- [x] 응답: `"gpu_available": true`, `"cuda_device": "NVIDIA GeForce RTX 3090"`, `"vram_total_gb": 25.8`
- [x] CUDA: 시작 로그 `"CUDA available: True | device: NVIDIA GeForce RTX 3090 | VRAM: 25.8 GB"` ✅
- [x] `/corpus/stats` → `{}` (빈 dict, 정상)

## 한 일

- `.result/ai-user/` 기록 시스템 생성 (README, STATE, roadmap, decisions, context/*)
- WSL `~/Data/Again-Spring-AI-User/` 골격 생성:
  - `docker-compose.yml` — aiuser-ml-db(mariadb:11) + aiuser-ml 서비스, 포트 8201, GPU 선언
  - `Dockerfile` — `pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime` 베이스
  - `pyproject.toml` — FastAPI + SQLAlchemy + transformers + scikit-learn (torch는 베이스 이미지)
  - `app/{config,auth,main,schemas}.py` — pydantic-settings, bearer auth, lifespan DB init
  - `app/api/routes_{health,score,eval,train,corpus}.py` — /health (no auth), 나머지 stub (degraded=True)
  - `app/worker/{callback,jobs}.py` — ASM callback 패턴 복제, thread pool job runner
  - `app/storage/{db,models}.py` — SQLAlchemy ORM (corpus_item, model_version, eval_run, jobs)
  - `app/ml/__init__.py` — 빈 스텁 (Step 1-4에서 채움)
  - `tests/` — conftest, test_health, test_score_stub, test_corpus (DB mock)
  - `CLAUDE.md` + `.claude/rules/` — llm-safety 적용 + ml-service 규칙
  - `AGENTS.md`

## 설계 결정

- **DB**: MariaDB:11 (ASM 동일 패턴, 커뮤니티 DB 비공유)
- **포트**: 8201 (ASM=8200, AS콜백=8090과 구분)
- **Dockerfile 베이스**: pytorch 2.5.1 CUDA 12.4 runtime (WSL 드라이버 610 지원)
- **torch 설치**: pyproject.toml 미포함 (베이스 이미지 선탑재)
- **초기 /score·/rerank**: degraded=True, 중립 0.5 반환 (Step 4 이후 실제 판별기)
- **GPU**: /score는 CPU, /train만 GPU (간헐)

## 함정

- WSL에서 `nvidia-smi`는 PATH에 없음 → `/usr/lib/wsl/lib/nvidia-smi`
- swap 75% 사용 중 → RAM 집약 연산 주의
- pytorch 베이스 이미지 빌드 첫 번에 수 GB 다운로드 (시간 소요)

## 다음 스텝이 알아야 할 것 (Step 1용)

- Korean POS 태거 결정 필요: `kiwipiepy` (pip 간단, 외부 deps 없음) vs `python-mecab-ko` (mecab 번들)
  - kiwipiepy 권장: Docker에서 system deps 없이 pip install 만으로 동작
  - `app/ml/pos_tagger.py` + `app/ml/features_katfish.py` 구현
- KatFishNet 피처 3개: 쉼표 빈도/위치, 띄어쓰기 오류율, 품사 n-gram 다양성
  - 참조 코드: github.com/Shinwoo-Park/katfishnet (피처 추출기만, 벤치 데이터 미사용)
  - ⚠️ AS 자체 크롤 코퍼스로만 학습 (KatFish 벤치는 에세이/시 — 커뮤니티 아님)
- Dockerfile에 kiwipiepy 추가 필요: `RUN pip install --no-cache-dir kiwipiepy`
