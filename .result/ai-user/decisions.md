# 결정 로그 (append-only)

> 모든 설계 결정을 시간 순 기록. 삭제 금지.

---

## 2026-06-15 — 사용자 4문항 답변 (계획 수립)

| 질문 | 답 |
|---|---|
| 연구 범위 | **Phase 0–1만** — 판별기+평가+Best-of-N+분포매칭. 생성은 Claude 유지, GPU는 학습/추론만. QLoRA/DPO는 조건부 미래. |
| 추출 범위 | **신규 GPU ML 서비스만** — orchestrator/llm/learning은 AS 그대로. orchestrator가 REST로 ML 서비스 호출. |
| GPU 위치 | **WSL 3090** (`100.115.252.61`). |
| AS 이전 | **불필요** — AS는 이미 Ubuntu(`100.81.189.92`). WSL에 AI-User만 신설. |

## 2026-06-15 — VRAM 권한 (세션 1)

- 2026-06-15 ~ 약 2026-06-22 (1주) WaggleBot VRAM(ComfyUI/LTX + fish-speech) 전부 unload 가능.
- **3090 24GB 전체 사용 가능.** Step 4 판별기 학습 시 VRAM 가드 완화.
- 창 종료 후: 다시 ~11GB 여유 기준 + WaggleBot 유휴창 필요.

## 2026-06-15 — 신규 서비스 설계 결정

| 결정 | 내용 | 근거 |
|---|---|---|
| 포트 | **8201** | ASM=8200, learning=8099, AS 콜백=8090과 구분 |
| DB | **MariaDB:11 (자체 `aiuser-ml-db`)** | ASM 선례, 커뮤니티 DB 비공유, 동시성 |
| API 토큰 (dev) | `aiuser-ml-api-token-dev-2026` (AS→ML 방향) | Step 5 AS 배포 시 orchestrator env 추가 필요 |
| 콜백 토큰 (dev) | `aiuser-ml-callback-dev-token-2026` (ML→AS 방향) | Step 5 AS 수신 엔드포인트 구현 시 필요 |
| Dockerfile 베이스 | **`pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime`** | WSL 드라이버 610(CUDA 13.3 지원) → CUDA 12.4 컨테이너 호환 |
| torch 설치 | 베이스 이미지 사전탑재 (pyproject.toml 미포함) | CPU 버전 재설치 방지 |
| POS 태거 | Step 1에서 결정 (mecab-python3 vs kiwipiepy vs konlpy) | Step 0에서는 불필요 |
| Best-of-N 기본값 | N=4, POST 우선 적용, COMMENT는 베이스라인 AUC 확인 후 | 토큰 비용 vs 효과 균형 |
| `/score`·`/rerank` 초기 | Stub (degraded=true, 중립 0.5 반환) | Step 4 판별기 구현 전까지 graceful degradation |

## 2026-06-16 동료 검토 보정 결정

### D-08: 수집·리랭킹 분리
- **결정**: `AI_USER_ML_COLLECT`(수집)과 `AI_USER_ML_ENABLED`(리랭킹)를 독립 플래그로 분리
- **이유**: AUC 미달(0.2~0.43) 시 리랭킹을 켜면 AI스러운 초안이 winner 선택됨 → 출력 악화. 수집만 먼저 켜야 진짜 negative 코퍼스 축적 가능.
- **상태**: 적용 완료 (commit 9ee6e1d8)

### D-09: mauve-text 계기판 선행 설치
- **결정**: MAUVE 없이 생성 품질 최적화 착수 금지 (측정 먼저 원칙)
- **이유**: eval_harness._try_mauve() 이미 graceful fallback 있음, 의존성만 추가하면 자동 활성화
- **상태**: 적용 완료 (WSL commit 906ebd7)

### D-10: TSD 프롬프팅 > Style-RAG 우선순위
- **이유**: TSD는 "애초에 사람처럼 생성"(생성 사전 개입), Style-RAG는 사후 패치. 계기판(MAUVE/AUC) 없이 둘 다 블라인드 최적화 — 1·2순위 완료 후 착수.

### D-11: Korean Unsmile 사용 범위 제한
- **결정**: human 코퍼스 혼입 금지. 생성시 negative constraint/필터 전용.
- **이유**: 혼입 시 판별기가 "혐오 표현=인간"으로 오염됨.

### D-12: Phase 2/3 (QLoRA+DPO) 진입 조건 명시
- **조건**: 실제 AUC(n_ai≥30 후 재학습) 측정값 기준으로 AUC>0.75 OR MAUVE<0.80 정체 시만 진입.
- **현재**: 대기. CLIEN/DCINSIDE/THEQOO AUC 이미 0.98~1.0 = QLoRA 불필요. THEQOO MAUVE=0.345 → TSD 먼저(생성 개선), Phase 2/3는 TSD 후 재측정.

## 2026-06-16 Step 9 결정 (AI negative 백필)

### D-13: historical SELF_GENERATED = 정당한 AI negative
- **결정**: 봇이 이미 작성·게시한 글/댓글/대댓글을 `label=ai`로 corpus에 직접 백필.
- **이유**: 페르소나별 voice_type에 맞춰 생성된 진짜 AI 출력 = 가장 정확한 negative 샘플. 토큰 재생성 0.
- **안전 4중 필터**: ① `synthetic=1` ② voice_type 12종 화이트리스트 ③ `deleted_at IS NULL` ④ LlmErrorSignature 40+ 시그니처 denylist.
- **결과**: 5803행 추가, 3건 오류 텍스트 차단, 첫 실제 AUC 확보.

### D-14: community 라벨 = voice_type (≠ posts.category)
- **결정**: 판별기 community 키 = `personas.voice_profile.voice_type` (NATEPAN/DCINSIDE/...), **`posts.category`(COUPLE/MARRIED/...) 아님**.
- **이유**: `pushNegative(voiceProfileField(persona,"voice_type"), ...)` 라이브 경로와 동일 키를 사용해야 corpus가 오염 없이 라이브와 병합됨.

### D-15: 학습 파이프라인 POST 전용 + 주의사항
- **확인**: `train_pipeline`/`eval_harness` = `content_type=POST`만 사용. COMMENT 백필은 retrain trigger(n_ai≥30)엔 기여하나 AUC/MAUVE 수치에는 미반영.
- **함의**: NATEPAN처럼 봇 글(POST)이 없는 커뮤니티는 댓글 백필로는 AUC를 개선할 수 없음 → 자연 봇 활동 대기.

### D-16: 멱등 백필 dedup 키 = text SHA-256
- **확인**: `/corpus/ingest` dedup은 text SHA-256 UNIQUE 제약 (community/label 무관). 백필 재실행·live 재푸시 모두 자동 skip.
- **롤백**: `DELETE FROM corpus_item WHERE source='BACKFILL_SELF_GENERATED'`

## 2026-06-16 Base Hardening 결정 (Step 10~17)

### D-17: ENABLE 게이트 5조건 (커뮤니티별 모두 충족 시에만 enable-candidate)
- **결정**: `AI_USER_ML_ENABLED=true`로 전환 판단은 아래 5조건 **전부** 충족 시에만 사람이 수동으로.
- **조건 1**: POST 실제 n_ai≥100 AND n_human≥300 (synthetic 포함 위조 샘플 0)
- **조건 2**: CV-AUC mean≥0.75 AND std≤0.1 (stratified 5-fold, 단일 split 아님)
- **조건 3**: T1 클린 피처 확인 (분리기 정상화로 avg_sentence_length 신뢰 가능)
- **조건 4**: 오프라인 A-B `MAUVE(rerank) > MAUVE(random)` 且 지표 퇴행 없음 (판별기로 검증 금지 — 순환)
- **조건 5**: 사람 블라인드 baseline 정확도 확보 (목표 ~50%)
- **구현**: `GET /metrics/enable-candidates` 엔드포인트 (Step 14/T7).
- **코드에서 enable 플래그 변경 절대 금지** — 게이트 충족 후 ops가 수동으로 `.env.dev/.env.prod`에서 변경.

### D-18: AUC 두 가지 의미 혼동 금지 (관점 교정)
- **결정**: 코드·주석·응답 필드 어디서도 "AUC≥0.55=사람 같음"으로 해석하는 표현 금지.
- "AUC≥0.55=ready"는 **"리랭커 배포 가능"**만 의미. 사람 같음(MAUVE→1.0, 블라인드~50%) 과 별개.
- `ready` 필드 응답에 "reranker-deployable (NOT human-like)" 주석 추가.

### D-19: 합성 음성 위조 금지 (AUC 부풀림 근본 원인 차단)
- **발견**: `train_pipeline.py:120-132` — real n_ai<MIN_SAMPLES_PER_CLASS 시 human 텍스트를 복제해 label=0 음성으로 위조. 이것이 NATEPAN AUC=0.562, DCINSIDE AUC=1.000의 원인.
- **결정**: 위조 경로를 `INSUFFICIENT_DATA` 게이팅으로 대체. POST real n_ai<100 OR n_human<300 → 학습 스킵 + INSUFFICIENT_DATA 마킹. ready 제외.
- **이유**: 위조 샘플로 학습한 판별기는 신뢰 불가. 리랭커 배포 판단 근거로 사용 금지.

### D-20: CV AUC 저장 = ModelVersion.auc (스키마 변경 0)
- **결정**: `Base.metadata.create_all`은 컬럼 추가 불가(누락 테이블만 생성) → ModelVersion 스키마 미변경.
- `ModelVersion.auc`에 **CV mean** 저장 (기존 readiness 읽기 경로 무변경).
- CV std·ablation·선택된 C는 **`EvalRun(kind="cv").metrics_json`** 저장 (컬럼 추가 0).

### D-21: 문장 분리기 공유 함수로 통일
- **발견**: 분리기가 2곳에 중복, 서로 다른 regex — 공유 유틸 없음.
  - `features_katfish.py:93-99`: `re.split(r'[.!?]')` → `avg_sentence_length`
  - `eval_harness.py:49-53`: `_split_sentences()` `(?<=[다요여임나죠])\.\s*|\n+` → burstiness
  - 결과: DCINSIDE `avg_sentence_length=57.40` (단일 문장 취급) — 신뢰 불가.
- **결정**: `features_katfish.py`에 `split_sentences()` 공유 함수 신설. `eval_harness.py` import. 분리 경계: `\n`, `...`/`…`, `!`, `?`, 마침표(한글 종결 뒤), 2자 이상 연속 자모(ㅋㅋ/ㅎㅎ/ㅠㅠ/ㅜㅜ), 이모지.

## 2026-06-16 Base Hardening 2라운드 결정 (Step 18~26)

### D-22: cond3 정정 — avg_sl 데이터 임계 폐기, 테스트 기반 불리언으로 교체
- **배경**: Step 14 문서는 cond3 = `avg_sl ≥ 6.5` (THEQOO 3.99 → false-negative). 실배포 코드는 DCINSIDE `avg_sl<20`, 나머지 `bl_run is not None`. 둘 다 데이터값 의존 = 불안정.
- **결정**: cond3 = **분리기 단위테스트 통과 불리언** (`SPLITTER_VERIFIED` 상수, Step 19에서 true 확정). 회귀 방지용 sanity 체크만 유지.
- **이유**: split_sentences()가 D-21 경계를 구현했음을 단위테스트로 입증하면 cond3는 일회성 검증 통과로 충분. avg_sl은 언어·커뮤니티마다 달라 데이터 임계가 부적절.
- **위치**: `app/api/routes_metrics.py` cond3 분기 + `app/config.py SPLITTER_VERIFIED`.

### D-23: cond5 정정 — 역방향 임계 추가 (인식률 ≤ 0.60 성공)
- **배경**: Step 14/15 문서 모순: `≥0.80` 충족 vs `<0.75` 충족. 실배포 코드: `blind_run is not None`만 확인 (임계 없음). 모두 틀림.
- **결정**: `cond5.met = blind_run is not None AND blind_run.metrics_json["human_accuracy"] <= BLIND_ACCURACY_THRESHOLD (0.60)`.
- **방향 명시**: 정확도 높음 = AI 탐지됨 = 미달. 목표 = AI가 인간으로 착각될 정도 = 정확도 낮음(≤0.60).
- **코드 주석 의무**: "인식률 ≤ 0.60 = AI가 인간처럼 보임 = 성공 (높을수록 탐지됨 = 실패)".
- **위치**: `app/api/routes_metrics.py` cond5 분기 + `app/config.py BLIND_ACCURACY_THRESHOLD = 0.60`.

### D-24: THEQOO 인간 코퍼스 디오염 정책
- **배경**: THEQOO human POST 344개 중 272개(79%) URL 포함. 판별기가 "갈등 서사=AI, 링크공유/공지=인간" 역학습 → P(human) 방향 역전.
- **필터 조합** (길이 단독 금지):
  1. URL 제거 후 잔여 텍스트 < 25자 → 링크지배 → 삭제
  2. 보일러플레이트 마커 (`관리자`/`운영팀`/`공지`/안녕하세요+번호목록/`삭제할 예정`) → 삭제
  3. 다중 URL + 광고 패턴 → 삭제
  4. 서사 + URL = KEEPER (URL만 strip)
- **적용 2지점**: ① 일회성 corpus_item DELETE ② `/corpus/ingest` 경로 필터 내장 (향후 차단)
- **재-pull**: example_bank에서 클린 데이터 재적재 (theqoo 845개 중 클린분 pull)
- **완료 기준**: P(human) 방향 교정 확인 (슬랭 高, 격식체 低)

### D-25: PersonaFactory voice별 HEAVY≥1 보장
- **배경**: tier 배정이 voice와 독립 랜덤({REGULAR,REGULAR,LIGHT,HEAVY}) → NATEPAN/INVEN 전 페르소나가 HEAVY=0. POST는 HEAVY만 가능 → NATEPAN AI POST = 0.
- **결정**: `PersonaFactory.generateOne()`에 **voice_type별 HEAVY 쿼터** 추가 — 신규 배치 생성 시 voice당 HEAVY≥1 보장.
- **즉시 수정**: 기존 dev DB NATEPAN/INVEN 페르소나 중 1개씩 tier=HEAVY로 DB 직접 UPDATE.
- **위치**: `PersonaFactory.java` ensureCount/generateOne + dev DB SQL UPDATE.

### D-26: 댓글 분포매칭 범위 — 초성체 활성, Best-of-N 보류

- **배경**: COMMENT는 이미 comma정규화+길이컷 적용. 누락: (1) 초성체 주입(allowChosung=false), (2) Best-of-N(POST 전용 하드코딩).
- **결정**: 
  - 초성체 주입: COMMENT에도 VOICE_DIST 기준 allowChosung=true 경로 개방 (voice별 chosungInject 값 존중)
  - 댓글 Best-of-N: **N1(코퍼스 정화) 완료 후 결정** — 역전 판별기로 리랭크 시 품질 악화 위험 (순환검증 금지)
- **YAML 주의**: `voices.yml post_processing`은 죽은 설정. 실값은 `OutputSanitizer.VOICE_DIST` Java 하드코딩.

---

## 2026-06-16 — 3라운드 결정 (D-27~D-31)

### D-27: cond4 반복 측정 기준 — 단일런 노이즈 인정
- **배경**: N9 Round3 THEQOO Δ+0.4834가 단일런 노이즈임을 확인. random arm이 무시드 `random.randint()`로 3런에서 0.9111/None/0.4961로 출렁임. n_contexts=12로 소표본.
- **결정**: cond4 충족 기준 = Δ 평균(≥3 시드) > 0 **AND** std < 평균 **AND** 오케스트레이터 실제 출력 비퇴행. 단일런 Δ+값 = UNVERIFIED로 강등.
- **즉시 조치**: `routes_eval.py`에서 random arm K≥3 시드 반복 평균±std 구현. 참조 코퍼스 1회 스냅샷 고정. n_contexts ≥ 40.

### D-28: ctx_* 오염 정리 (A-B 테스트 누수)
- **배경**: `run_ab_test.py`가 A-B 컨텍스트 제출 시 `source='ctx_0..ctx_9'`로 저장 → label=human으로 혼입. THEQOO 11, CLIEN 9, NATEPAN 2 = 총 22행.
- **결정**: 해당 22행 DELETE. 이후 테스트 컨텍스트는 corpus에 기록 안 함 (source 필터 추가).
- **완료**: 2026-06-16, DB에서 22행 삭제 확인 (THEQOO n_train 544→534로 검증).

### D-29: 전 커뮤니티 N1 디오염 — ctx_* 우선, 장르 필터는 단계적
- **배경**: M2에서 P(human) 역전이 T8 이후 AI corpus 슬랭화 + human corpus 장르 다양성 혼재가 원인임을 확인. 단순 링크 제거만으론 부족.
- **결정**: 1단계 = ctx_* 삭제(완료). 2단계 = NATEPAN/CLIEN decontaminate 확장(URL+<25자 필터). 3단계 = corpus 장르 필터(갈등 서사 POST만)는 M7 후 신선 AI corpus 축적 후 판단.
- **이유**: 장르 필터 과적용 시 human corpus 급감 위험. M7로 AI corpus 스타일을 먼저 교정한 뒤 판별기 재학습이 더 효과적.

### D-30: M5 사용자 라벨링 — M7 신선 출력 축적 후
- **배경**: N5에서 에이전트 자가 라벨링 정확도=1.00 (cond5 FAIL). 사용자가 M5는 "M7 개선 후 직접 라벨링"으로 결정.
- **결정**: M7 dev 배포 후 자연 틱 or admin trigger로 THEQOO+NATEPAN 신선 출력 40쌍 이상 축적 → 사용자에게 라벨 숨긴 파일 제시 → 정확도 산출.
- **기준**: 정확도 ≤ 0.60 = cond5 met. 50% ≈ 랜덤 = 이상적.

### D-31: M7 파일럿 범위 — THEQOO+NATEPAN 한정, reply voiceType 전역 수정
- **배경**: 생성 문체가 "격식 상담사"로 수렴해 100% AI 탐지됨. 가장 강력한 레버는 문체 다양화.
- **결정**: features 백필은 THEQOO(기존) + NATEPAN(신규) 파일럿. reply voiceType 수정은 전 커뮤니티 적용 (버그 수정 성격). SelfCritiqueService voiceType 오버로드 추가 (post/comment 정제 경로 정합성).
- **구현**: GenDto.ReplyRequest + ReplyGenRequest.java voiceType 필드 추가. ActionExecutor reply builder voiceType 설정. GenerationController/SelfCritiqueService 수정. dev e2e 142 passed.
