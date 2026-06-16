# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-16 (세션 10 — T8 THEQOO TSD 프롬프팅 완료)

## ⚠️ 관점 교정 (Step 9 "ready_count=4/4"의 올바른 해석)

> Step 9의 `ready_count=4/4`, AUC 0.98~1.0은 **성공이 아니라 측정의 시작점이다.**
> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC = "AI가 아직 쉽게 구별됨 = 목표 미달" (동시에 리랭커 작동 전제이기도 함)
> - "AUC≥0.55=ready"는 **"리랭커 배포 가능"**만 의미 — 절대 "사람 같다"가 아님
> - 현재 AUC 수치 자체가 **신뢰 불가**: 단일split+소표본누수·합성음성위조·readiness카운팅버그
>
> **따라서 `AI_USER_ML_ENABLED=true` 활성화는 Base Hardening(Step 10~17) 완료 후 5조건 충족 시에만.**

## 현재 위치

- **Step**: Base Hardening Phase A 완료 → Phase B(T6) 대기
- **전체 진행**: Step 0–14 완료. Step 15~17 = Phase B·C 대기.
- **다음 작업**: T6 (오프라인 A-B harness + 사람 블라인드 — 토큰 소량 소비)
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지

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

## Step 9 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| AI negative 백필 5803행 (dev DB, 토큰비용 0) | ✅ |
| 시그니처 필터 3건 차단 (실제 오류/거절 텍스트) | ✅ |
| n_ai: 0→323(CLIEN), 143(DCINSIDE), 295(NATEPAN), 423(THEQOO) | ✅ |
| 수동 `/train` → 첫 실제 AUC 확보 | ✅ |
| `/eval/baseline` → MAUVE 비null값 확보 | ✅ |
| `ready_count=4/4` | ✅ |

### 첫 실제 AUC (job 01KV6XZA5F41T9DDNK42C539BE)

| 커뮤니티 | 이전 AUC (synthetic) | **실제 AUC** | MAUVE |
|---|---|---|---|
| CLIEN | 0.304 | **0.989** | 0.970 |
| DCINSIDE | 0.429 | **1.000** | 0.9999 |
| NATEPAN | 0.319 | **0.562*** | null (AI POST 없음) |
| THEQOO | 0.200 | **0.980** | 0.345 |

*NATEPAN: dev에 봇 글 없음, 댓글 295개는 eval/학습 미사용(POST only). 마진 작음.

## Base Hardening 진행 상황 (Step 10~17)

| Step | Task | 상태 |
|---|---|---|
| Step 0 | 문서 선행 (roadmap/decisions/STATE) | ✅ 완료 |
| Step 10 (T1) | DCINSIDE 문장 분리기 수정 | ✅ 완료 — avg_sl 57.40→7.02 |
| Step 11 (T2) | 신뢰 가능한 AUC (CV 5-fold) | ✅ 완료 — INSUFFICIENT_DATA 게이트 작동 |
| Step 12 (T3) | readiness 게이트 버그 수정 | ✅ 완료 — NATEPAN ready=false (POST n_ai=0) |
| Step 13 (T4) | COMMENT 측정 추가 | ✅ 완료 — COMMENT MAUVE 0.06 (POST와 분리 산출) |
| Step 14 (T7) | ENABLE 게이트 구현 | ✅ 완료 — /metrics/enable-candidates, 0/12 (정상) |
| Step 15 (T6) | 독립 검증 harness | ✅ 완료 — A-B rerank vs random, Δ=+0.044~0.048 (marginal) |
| Step 16 (T5) | POST 샘플 보강 | 🔜 Phase C |
| Step 17 (T8) | THEQOO TSD 프롬프팅 | ✅ 완료 — [문체 패턴] 주입, dev DB 업데이트 |

## Step 15 완료 (2026-06-16)

| 기준 | 결과 |
|---|---|
| `POST /eval/ab-test` 엔드포인트 신규 | ✅ |
| `_run_eval_ab_test()` 리랭커 vs 랜덤 비교 | ✅ |
| `GET /corpus/export/blind` (T6b) 엔드포인트 신규 | ✅ |
| 10/10 pytest 통과 (patch target 수정 포함) | ✅ |
| `POST /eval/ab-test` 실제 실행 (2026-06-16) | 🔄 진행 중 — 결과 대기 |

### Step 15 A-B Test 결과 (실제 측정 중)

| 커뮤니티 | n_human | n_contexts | MAUVE(리랭크) | MAUVE(랜덤) | **Δ** | cond4 |
|---|---|---|---|---|---|---|
| THEQOO | 344 | 10 | 0.629 | 0.985 | **-0.356** | ❌ (반전 — 아래 진단) |
| CLIEN | 294 | 10 | 0.9998 | 0.9998 | **0.000** | ❌ (무신호) |

### 진단 (2026-06-16 실측)

**THEQOO Δ = -0.356 (역전)**: 판별기가 "갈등 사연 = AI" 학습 오염.
- 원인: THEQOO 인간 코퍼스에 링크포스트·공지·짧은 반응이 대거 포함됨 → 판별기가 "긴 갈등 서사 = AI, 짧은 반응/링크 = 인간"으로 학습
- A-B 테스트 초안은 전부 갈등 서사 → 판별기가 P(human)을 역방향으로 평가
- rerank 선택 = "갈등 서사 중 가장 짧은/비서사적" → MAUVE 오히려 낮음
- **긍정 발견**: random 초안의 MAUVE=0.985 (높음!) — `run_ab_test.py`의 단순 THEQOO 트레이트 프롬프트가 이미 인간-유사도 높음. 실제 봇 코퍼스 MAUVE=0.345는 오케스트레이터 생성 방식의 문제

**CLIEN Δ = 0.000**: 판별기 신호 없음.
- 원인: CLIEN AI 텍스트와 인간 텍스트가 이미 매우 유사(MAUVE=0.9998) → 선택의 여지 없음

**핵심 결론**: 리랭커는 현재 두 커뮤니티 모두 유용한 신호 제공 불가.
- THEQOO: 코퍼스 정제 필요 (갈등 사연 POST만 인간 레이블)
- 두 커뮤니티 모두 n_ai < 100 → T2 게이트로 재학습 불가

### cond4 상태

```
cond4_ab_mauve:
  met: false
  THEQOO_delta: -0.356 (역전)
  CLIEN_delta: 0.000 (무신호)
  fix_needed: "THEQOO 인간 코퍼스 갈등 사연 POST로 필터링 후 재학습"
  blocker: "n_ai < 100 → INSUFFICIENT_DATA 게이트 → 재학습 불가"
```

## Phase A 완료 수치 요약 (2026-06-16)

| 항목 | 이전 | 이후 |
|---|---|---|
| DCINSIDE avg_sentence_length | 57.40 | **7.02** |
| NATEPAN ready | true (버그, n_ai=295 COMMENT) | **false** (POST n_ai=0) |
| 합성 위조 학습 | 있음 | **없음** (INSUFFICIENT_DATA) |
| ready_count | 4/4 | **0/12** (실제 데이터 기준) |
| enable-candidates | 없음 | **0/12** (5조건 모두 미충족 정상) |
| COMMENT MAUVE | 미측정 | **0.060** (NATEPAN), 0.068 (CLIEN) |
| pytest | 65/65 | **82/82** (T6 10 tests baked into rebuilt container) |

## Step 17 완료 (T8 — 2026-06-16)

| 기준 | 결과 |
|---|---|
| `appendWritingQuirks` features 읽기 추가 | ✅ `[문체 패턴]` 섹션 신규 출력 |
| THEQOO 10개 voice.yml features 추가 | ✅ (profiles/ 파일 기반 — 신규 시드용) |
| dev DB 7개 THEQOO 페르소나 JSON_SET | ✅ writing_quirks.features 삽입 확인 |
| dev 배포 (orchestrator rebuild) | ✅ |
| e2e-realbe 142/147 | ✅ |
| main push | ✅ commit `88018822` |

### T8 핵심 발견

- **근본 원인**: `voiceBlockForPost`가 THEQOO 페르소나에 대해 `writing_quirks.features` 필드를 읽지 않음 → 시스템 프롬프트의 `## 페르소나 특성` 섹션이 거의 비어 있었음
- **DB 페르소나 구조 이슈**: DB의 THEQOO 페르소나(7개) IDs ≠ voice.yml IDs (다른 세대 생성) → DB 직접 업데이트 필요
- **기대 효과**: `[문체 패턴] 짧은 문장 단위 (한 문장 10~20자 이내). 단락 없이 한 덩어리로. 헐/ㅠㅠ/ㄷㄷ 문장 중간 삽입. ~당/~징/~음/ㅎㅎ 종결 위주.` → 오케스트레이터 MAUVE 0.345 → 0.60+ 목표

## 다음 구체 작업 (Phase C 후속)

**우선순위 1 — THEQOO MAUVE 재측정** (T8 효과 검증):
- `POST /eval/baseline` 트리거 → THEQOO MAUVE before/after 비교
- 신선 THEQOO 봇 게시글이 충분히 축적되면 의미 있는 비교 가능

**우선순위 2 — T5 (Step 16)**: THEQOO/DCINSIDE/NATEPAN n_ai → 100 돌파
- THEQOO: 현재 65, 35개 더 필요 (T8 이후 신선 데이터 우선)
- NATEPAN: 0 POST — 오케스트레이터가 NATEPAN 게시글을 생성하도록 설정 필요

**우선순위 3 — THEQOO 코퍼스 정제** (T6 재실행 전제):
- 인간 코퍼스에서 링크포스트·공지·짧은 반응 제거 → 갈등 사연 POST만 유지
- discriminator 재학습 → A-B 재측정 → cond4 확인 (목표: Δ > 0.05)

- `AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 수동으로 — 코드 변경 금지

## 운영 메모 / 권한

- **Auto 모드** — 막히지 않으면 계속 진행 (사용자 2026-06-15 지시)
- **VRAM 권한:** 2026-06-15 ~ 약 2026-06-22 (1주) WaggleBot VRAM 전부 unload 가능
- **기록 규칙:** 매 세션 시작 `STATE.md` 먼저, 끝낼 때 마지막에 갱신

## 미해결 질문

- NATEPAN: AI POST 0개 → 자연 축적 대기. CLIEN(40)/DCINSIDE(20)/THEQOO(65)도 100 미달. 가장 빨리 도달할 것: THEQOO(65→100 = 35 더 필요).
- COMMENT MAUVE 0.06 — AI 댓글 품질 매우 낮음. T6 A-B에서 COMMENT도 포함 여부 결정 필요 (계획은 POST 우선).
- 분리기 정상화 후 cond3가 CLIEN/NATEPAN/THEQOO/DCINSIDE 모두 true — cond1,2,4,5가 남은 병목.

## 블로커

- 없음
