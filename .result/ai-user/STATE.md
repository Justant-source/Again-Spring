# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-16 (세션 6 — Step 8 ✅ 완료 — 수집/리랭킹 분리 + MAUVE 계기판)

## 현재 위치

- **Step**: 8 완료 (수집/리랭킹 분리 + MAUVE 계기판)
- **전체 진행**: Step 0–8 완료. prod AI_USER_ML_COLLECT=true, ENABLED=false 유지

## Step 1 완료 확인 (2026-06-15)

| 기준 | 결과 |
|---|---|
| `pytest tests/test_features.py` 24/24 통과 | ✅ |
| DC/네이트판 샘플 → 합리적 피처 값 | ✅ `ㄹㅇ=SW`, `그러나=MAJ` |
| GPU 미사용 (CPU only) | ✅ kiwipiepy/scipy 모두 CPU |
| kiwipiepy 0.23.x API 적용 | ✅ `result[0][0]`, `str(t.tag)` |

## 함정 기록 (Step 1에서 발견)

- **kiwipiepy 0.23.x API**: `result[0].tokens` 없음 → `result[0][0]` 사용
- **`t.tag`**: str ("MAG", "NNG" 등), NOT enum `.name`
- **pydantic-settings 패치**: `patch("Settings.api_token")` 실패 → `patch("get_settings")` 사용
- **Dockerfile 패치**: 파이썬 스크립트로 문자열 교체 시 공백 불일치 → Python으로 통 덮어쓰기 사용

## Step 2 완료 (2026-06-15)

| 기준 | 결과 |
|---|---|
| `/examples/export` 실 데이터 반환 | ✅ dcinside 글 정상 |
| `/corpus/stats` 커뮤니티 카운트 | ✅ NATEPAN:168/THEQOO:127/DCINSIDE:26 |
| 31/31 pytest 통과 | ✅ |

## Step 3 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `eval_harness.py` 신규 | ✅ |
| `POST /eval/baseline` 잡 완료 + JSON 저장 | ✅ `data/eval/01KV5WZHQVRTM3SZ5WD673YPQ3.json` |
| 4개 커뮤니티 베이스라인 확보 | ✅ DCINSIDE/NATEPAN/THEQOO/CLIEN |
| 44/44 pytest 통과 | ✅ |
| ending_js_div / MAUVE | ⚠️ AI 샘플 없어 null — Step 5 후 재실행 가능 |

### Step 3 베이스라인 핵심 수치 (human corpus, POST 타입)

| 커뮤니티 | n_human | comma_rate | spacing_error | pos_diversity | burstiness |
|---|---|---|---|---|---|
| DCINSIDE | 35 | 3.0% | **91.9%** | **21.2%** | 0.70 |
| NATEPAN | 396 | 1.1% | 69.4% | **62.7%** | **0.94** |
| THEQOO | 300 | 1.1% | 40.0% | 54.1% | 0.93 |
| CLIEN | 228 | 2.2% | 74.6% | 57.0% | 0.81 |

## Step 4 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `discriminator.py` + `registry.py` + `train_pipeline.py` | ✅ |
| `POST /train` GPU 잡 완료 (40초) | ✅ 4개 커뮤니티 |
| `POST /score` + `POST /rerank` CPU 추론 `degraded=False` | ✅ |
| 56/56 pytest | ✅ |
| AUC | ⚠️ synthetic negative → AUC 0.20-0.43 (반사 학습). Step5 AI negative 후 재학습 필요. |

## Step 5 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `AiUserMlClient.java` 신규 (Bearer, /rerank, /corpus/ingest, graceful skip) | ✅ |
| `ActionExecutor.executePost` Best-of-N 와이어링 | ✅ enabled=false 시 기존 단일초안 경로 그대로 |
| `ActionExecutor.executePost/Comment` AI negative push | ✅ 게시 성공 시 /corpus/ingest |
| `application.yml` AI_USER_ML_* 5종 추가 | ✅ |
| `docs/env/environment-variables.md` 문서화 | ✅ |
| 13 신규 pytest (`AiUserMlClientTest`) + 기존 63 유지 | ✅ (63/63 all pass) |
| dev 배포 + e2e-realbe 142/147 통과 | ✅ (5 skipped = 정상) |
| `main` push | ✅ commit `6b4d29e9` |

### Step 5 후 즉시 조치

- `/train` 재실행 트리거 완료 (job `01KV5YSEYFGKTKJVDBPAN6RDSY`) — AI negative 축적 후 AUC 개선 기대
- AI_USER_ML_ENABLED=false (기본) — 실제 AI negative 충분 축적(n≥30/커뮤니티) + AUC≥0.55 확인 후 점진 롤아웃

## Step 6 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `voices.yml` 12개 커뮤니티 `post_processing` 신규 | ✅ |
| `SelfCritiqueService` 쉼표 과다 체크 #11 | ✅ (>5% → score -1) |
| `OutputSanitizer.sanitizePost/Comment(raw, voiceType)` 오버로드 | ✅ |
| `normalizeCommaRate` + `injectChosung` | ✅ 확률적 분포 매칭 |
| voiceType 관통 배선 ActionExecutor → GenerationController | ✅ |
| 28 LLM 테스트 + 63 Orchestrator 테스트 | ✅ |
| dev 배포 + e2e-realbe 142/147 | ✅ |
| `main` push | ✅ commit `fd5d16c4` |

## Step 7 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `retrain_check_loop` (6h) 신규 | ✅ |
| `eval_periodic_loop` (24h) 신규 | ✅ |
| `GET /metrics/readiness` + `/metrics/history` | ✅ |
| config.py 6종 신규 설정 | ✅ |
| 65/65 pytest (56 기존 + 9 신규) | ✅ |
| WSL commit 5b53372 | ✅ (로컬 only — 원격 없음, ASM 패턴) |

## Step 8 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `AI_USER_ML_COLLECT` 플래그 분리 | ✅ |
| `pushNegative()` 수집 독립 게이트 | ✅ |
| `docker-compose.dev/prod` ML env 주입 | ✅ |
| 66/66 orchestrator 테스트 | ✅ |
| mauve-text 설치 + MAUVE_OK | ✅ |
| dev + prod 배포 (AI_USER_ML_COLLECT=true) | ✅ |

## 다음 구체 작업

1. `GET /metrics/readiness` 모니터링 → n_ai 상승 확인
2. n_ai≥30/커뮤니티 → `retrain_loop` 자동 발동 대기
3. 첫 실제 AUC 확인 후 TSD 프롬프팅 착수 (3순위)
4. AUC≥0.55 → `AI_USER_ML_ENABLED=true` 수동 활성화

## 운영 메모 / 권한

- **Auto 모드** — 막히지 않으면 계속 진행 (사용자 2026-06-15 지시)
- **VRAM 권한:** 2026-06-15 ~ 약 2026-06-22 (1주) WaggleBot VRAM 전부 unload 가능
- **기록 규칙:** 매 세션 시작 `STATE.md` 먼저, 끝낼 때 마지막에 갱신

## 미해결 질문

- AI negative 실제 축적까지: `AI_USER_ML_ENABLED=false` 유지 권장. 커뮤니티별 n_ai≥30 확인 후 롤아웃.
- `/corpus/ingest` push가 `voice_type=null` 페르소나에서 community=null 전달될 수 있음 — ML 서비스 측 null community 처리 확인 필요

## 블로커

- 없음
