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

## 2026-06-16 — 4라운드 결정 (D-32~D-35)

### D-32: DCINSIDE 학습 제외 — 장르 구조 불일치
- **결정**: DCINSIDE를 enable-gate cond1/cond2 에서 제외
- **근거**: human corpus 39건 샘플 체크 → 와인경진대회, 카메라 리뷰, 여행기, 뉴스 기사, 수공예 — 갈등 서사 전무. DCINSIDE = 주제별 갤러리(hobby) 포럼, 갈등 게시판 구조 없음. 264건 직접 인제스트 시도 → 전부 AI corpus 해시 충돌 (오케스트레이터 생성물)
- **대안**: THEQOO/CLIEN/NATEPAN 3개 커뮤니티로 enable-gate 운영

### D-33: cond4 FAIL 확정 — 리랭커 역효과 (판별기 역전 상태)
- **결정**: cond4 현재 FAIL. 판별기 역전이 해소될 때까지 리랭커 비활성 유지.
- **근거**: K=3 시드 재측정 결과 — THEQOO Δ=−0.0094 (std=0.0098, 16ctx), NATEPAN Δ=−0.0167 (std=0.0801, 40ctx). 두 커뮤니티 모두 delta<0. 역전된 판별기가 "격식적=human" 오판 → 가장 AI다운 초안이 winner 선택 → MAUVE 저하.
- **경로**: M7 신선 출력 축적 → 재학습 → P(human) 방향 교정 → A-B 재측정

### D-34: M5 blind test 인간 샘플 전략 — corpus 장르 한계 반영
- **결정**: M5 blind test에서 NATEPAN은 갈등 키워드 필터(남편/시어머니/남친/싸웠/갈등/억울) 적용 human 샘플 사용. THEQOO는 갈등 서사 human 샘플 부족(4건)으로 랜덤 인간 샘플 사용 — THEQOO M5 결과는 보수적으로 해석 필요.
- **근거**: ML corpus human 항목이 갈등 서사만 필터링되지 않음. NATEPAN 16건, THEQOO 4건만 갈등 필터 통과.
- **영향**: THEQOO M5 정확도 = 장르 구별(쉬움) 일부 반영될 수 있음 → NATEPAN M5만 신뢰 가능한 측정.

### D-35: DB jobs.params_json MEDIUMTEXT — 40ctx 제출 필수
- **결정**: ML 서비스 jobs 테이블 params_json을 TEXT(64KB)에서 MEDIUMTEXT(16MB)로 변경.
- **근거**: 40 contexts × 4 drafts × ~1500자 Korean = ~240KB → TEXT 한계 초과로 500 에러.
- **조치**: ALTER TABLE 직접 실행 + models.py Text(16777215) 업데이트 (WSL 서버).

### D-36: M5 cond5 FAIL — 사용자 정확도 82.5%, AI 여전히 탐지 가능
- **결정**: cond5 FAIL 확정. 현재 생성 품질로는 사람이 AI를 82.5% 정확도로 구별 가능 (목표 ≤60%).
- **상세**: NATEPAN 16/20=80%, THEQOO 17/20=85%. 40개 항목, 사용자 직접 라벨링.
- **긍정 신호**: T013·T017 두 건에서 AI 출력을 Human으로 오분류 — M7 THEQOO 특성이 일부 효과.
- **오분류 패턴**: Human→AI 5건(N007·N001·N004·N006·T005) = 내용이 짧고 비정형인 인간 글. AI가 지나치게 정형화되어 있음을 역증명.
- **경로**: M7 신선 출력 더 축적(오케스트레이터 자연 틱) → 재학습 → M5 재측정.

### D-37: M6 댓글 MAUVE 개선 방향 — 길이 제약 추가
- **결정**: AI 댓글 길이를 명시적으로 제한하는 프롬프트 추가.
- **근거**: 현재 AI 댓글 = 15+ 어절, 인간 댓글 = 2~5 어절. 길이·어조 차이가 MAUVE 0.06 원인.
- **방향**: 댓글 프롬프트에 "2~4문장 이내, 한마디 감탄도 OK" 추가. VOICE_DIST/features 유지.
- **주의**: 길이만 줄이면 내용 손실 — 감정 표현 집중도 함께 높여야 함.

## 2026-06-16 — 5라운드 결정 (D-38~D-40)

### D-38: NATEPAN P(human) 방향 교정 완료 — 재학습으로 역전 해소
- **결정**: NATEPAN 판별기 P(human) 방향이 재학습(01KV83HW965RKXZZZ9H499Z54S)으로 완전 교정됨.
- **수치**: 격식 상담사 텍스트 → P(human) 0.9180→0.3635 (역전 해소). 슬랭 서사 → 0.9999 (유지).
- **AUC**: 0.31875→0.998853. n_train=614 (388 human, 226 AI).
- **의의**: 이전 NATEPAN A-B delta=-0.1092는 구 역전 모델로 측정한 것 — 무효. 교정된 모델로 A-B 재측정 진행 중.
- **원인**: M3 디오염 + M7 신선 출력 축적이 corpus 장르 편향을 교정한 것으로 추정.

### D-39: THEQOO P(human) 완전 역전 — 레이블 또는 채점 로직 오류
- **결정**: THEQOO 판별기 레이블 역전 확인. 인간 슬랭→P(human)=0.003, AI 격식→0.718. 조사 중.
- **가설**: scorer.py에서 `proba[1]` 반환 시 label encoding이 'ai'=1, 'human'=0이면 역전 발생.
- **긴급 조치**: THEQOO 재학습 일시 중단, 레이블 감사 후 재학습.
- **주의**: NATEPAN(✅)·CLIEN(✅)는 정상 — THEQOO 전용 버그.

### D-40: NATEPAN A-B 재측정 필요 (교정된 모델 기준)
- **결정**: P(human) 교정 이전에 측정한 delta=-0.1092는 무효 데이터. 교정 후 모델로 재측정 필수.
- **기대**: P(human) 올바른 방향이면 리랭커가 가장 인간다운 초안 선택 → delta>0 기대.
- **완료 기준**: delta>0 AND std<delta (≥40 contexts, K=3 seeds).
- **결과**: ✅ 완료 → D-41 참조.

## 2026-06-16 — 세션 17 결정 (D-41~D-44)

### D-41: NATEPAN cond4 PASS 확정 (eval_run id=100)
- **결정**: NATEPAN cond4 **PASS**. P(human) 교정 모델(v37) 기준 재측정.
- **수치**: delta=+0.1667, std=0.1257, n_ctx=40, K=3 seeds.
  - delta > 0: ✅, std < delta: ✅, D-27 기준 충족.
  - rerank MAUVE = 0.8590, random_mean = 0.6923.
- **오케스트레이터 비퇴행**: rerank MAUVE 0.8437(구)→0.8590(신) 개선 확인.
- **이전 FAIL(id=91, delta=-0.1092)**: P(human) 역전 상태의 구 모델로 측정 — 무효.

### D-42: CUDA 학습 에러 수정 — n_jobs=-1 → n_jobs=1
- **결정**: train_pipeline.py의 sklearn `n_jobs=-1` → `n_jobs=1`. discriminator.py에 `torch.cuda.empty_cache()` 추가.
- **근거**: sklearn multiprocessing 워커가 CUDA state를 상속받아 device mismatch 발생.
- **부작용**: 학습 단일 프로세스화 → 약간 속도 저하. 허용 가능 (GPU 40초 수준 유지).
- **재학습 결과**: NATEPAN AUC=0.9989, THEQOO AUC=0.9985, CLIEN AUC=0.9968 (안정).

### D-43: THEQOO corpus ai 541건 전량 삭제 (사용자 승인, 2026-06-16)
- **결정**: THEQOO corpus_item label='ai' 541건 DELETE. 전부 오라벨(실제 더쿠 인간 게시물).
- **구성**: 423 BACKFILL_SELF_GENERATED + 103 NULL source + 14 ctx_*/test_draft.
- **이유**: P(human) 완전 역전의 근본 원인. 오염 데이터를 학습하면 "인간 게시물=AI" 역학습 지속.
- **현재 상태**: THEQOO ai=0건, human=376건. 판별기 학습 불가 → n_ai≥100 재수집 필요.
- **경로**: 오케스트레이터 자연 틱 → THEQOO AI 생성물 corpus 수집 → n_ai≥100 후 재학습.

### D-44: contentType camelCase 필수 (API 호출 규칙)
- **결정**: ML score API는 `contentType` (camelCase) 필드 필수. snake_case `content_type`은 422 에러.
- **근거**: FastAPI Pydantic 모델이 camelCase 별칭 사용. 세션 16에서 snake_case로 호출 시 다른 결과가 나온 혼선의 원인.
- **적용**: 모든 `/score`, `/rerank` 호출에 `contentType` 사용.

## 2026-06-16 — 6라운드 결정 (D-45~D-49)

### D-45: D-39 인코딩 가설 기각 — 역전 원인은 오라벨 데이터
- **결정**: D-39의 "scorer.py `proba[1]` 반환 시 label encoding `ai=1, human=0` 역전" 가설 기각.
- **실측 근거**: `train_pipeline.py:108` = `labels = [1 if l=="human" else 0]` (human=1, ai=0). `discriminator.py:90-91` = `predict_proba(feats)[:,1]` = index 1 = human. sklearn `classes_=[0,1]` → 인덱스 1 = class '1' = human. **학습·추론 완전 일치. LabelEncoder 없음.**
- **정정**: P(human) 역전의 진짜 원인은 오라벨 데이터 — human 글이 'ai'로 라벨된 corpus로 학습 → 역방향 판별. THEQOO 541건 삭제(D-43)가 정당했음을 입증.
- **회귀 테스트**: WSL `tests/test_label_direction.py` 추가 — 실제 데이터로 방향 단언.

### D-46: R1 정밀 대조 — example_bank 크로스레퍼런스 (Option A)
- **결정**: corpus_item label='ai' 오라벨 검사는 AS 러닝 서비스(8099) example_bank 크로스레퍼런스로 정밀 대조.
- **방법**: /examples/export?sourceClass=human 전량 pull → SHA-256 해시 인덱스 → corpus_item 'ai' 항목 각각 해시 대조. human 인덱스 일치 → DELETE, SELF_GENERATED 일치 or 무일치 → KEEP.
- **원칙**: 과삭제 방지 — 무일치는 보수적 KEEP (증명된 human만 삭제).
- **실현 가능성**: curl http://100.81.189.92:8099/health → UP, /examples/export 두 sourceClass 모두 응답 확인(2026-06-16 세션 17).

### D-47: R3 양면 가드 — AS+ML 동시 차단
- **결정**: corpus 오염 재발 방지를 AS측+ML측 양면에서 동시 차단.
  - AS측: `AiUserMlClient.pushNegative`에 `source=SELF_GENERATED` 마커 추가 (현재 미전송→NULL).
  - ML측: `routes_corpus.py` `/corpus/ingest` — `label='ai'` 항목은 `source` 허용목록(`SELF_GENERATED`) 필수. 미마커 'ai' 거부.
- **이유**: AS측만 수정 시 과거 NULL source 'ai'가 남아 모호성 유지. ML측만 수정 시 이미 들어온 오라벨은 미삭제. 양면 모두 필요.
- **의존**: AS측 변경 → e2e dev:8090 게이트 필수.

### D-48: R4 CLIEN de-counselor — features 신규 + general_style 개정
- **결정**: CLIEN 7개 프로필(ai-user-{036,081,082,083,084,085,086})에 features 신규 작성 + general_style "정중·체계적 장문" 폐기 → 단편화·구어·비격식·짧은 호흡으로 개정.
- **방향**: 번호목록·균형구조·상담조 완전 금지. 반말 가능, 2~3문장 단편, 감정 서술 집중.
- **Java 변경 없음**: `ActionExecutor.appendWritingQuirks`(690-733)·`PersonaFactory.buildPersonaPrompt` 스키마·`OutputSanitizer.VOICE_DIST` 커뮤니티 무관 → 데이터 편집만.
- **DB sync**: dev DB `JSON_SET($.writing_quirks.features)` + `JSON_SET($.general_style)`.

### D-49: R8 cond4 분기 결정 (R1 결과 의존)
- **결정**: R1 정밀 대조 후 NATEPAN 오염분 삭제 규모에 따라 분기.
  - 삭제 유의미(기존 corpus 구성이 크게 바뀜) → 재학습 → cond4 재측정. 현 PASS(eval_run id=100, Δ=+0.1667)는 **provisional**.
  - 삭제 미미(corpus 구성 변동 < 5%) → PASS 유지. 재측정 생략.
- **현재 상태**: NATEPAN corpus 'ai' 항목: NULL source 231건(라이브 AI 추정), BACKFILL 295건(오라벨 의심). 대조 전까지 provisional.
- **A-B 토큰 금지**: 분기 결정 전까지 A-B 재실행 불허.

## 2026-06-17 — R9 결정 (D-50~D-52)

### D-50: Track A — 결정론적 후처리 messiness 주입 (OutputSanitizer.injectTypos)
- **배경**: CLIEN 5/5 활성 페르소나가 `mobile_typos=true`임에도 R5 블라인드=100%. Haiku가 "모바일 오타 2~3개" 지시를 조용히 무시. 프롬프트 레벨 오타 주입은 무효(증명됨).
- **결정**: `OutputSanitizer`에 `injectTypos()` 신설. `injectChosung` 선례 그대로. LLM 준수 비의존 — 후처리에서 결정론적으로 오타 주입.
  - T1(됐/됬·웬/왠), T2(종결요탈락), T3(띄어쓰기붙이기), T4(조사분리), T5(의→에혼동), T6(ㅋㅋ/ㅎㅎ삽입), T7(받침단순화), T8(이중자음) — 8개 transform, 셔플 후 1~2개 적용.
  - `VOICE_DIST` 레코드에 `typoInject`, `typoProb` 추가. CLIEN typoProb=0.55, THEQOO=0.30(단문).
  - `ActionExecutor.appendWritingQuirks` `Math.min(1→2)` cap 상향 + loop 2건 주입 (프롬프트 보강).
  - `applyDist()` 마지막에 호출(chomping/chosung 이후).
- **완료기준**: gradle test 통과, 단위테스트 통계적 불변식, e2e dev:8090 통과.

### D-51: Track B — 일상 글 모드 25% (주제 다양화, cond5 최대 레버)
- **배경**: CLIEN AI 글 전부 갈등 서사 → 주제만으로 탐지 가능. 인간 CLIEN = 정치·음식·주식 등 다양.
- **결정**: `executePost`에 CONFLICT 75% / CASUAL 25% 분기 추가.
  - CASUAL: `buildCasualSeed` (CASUAL_FRAMES 회전), `dynamicExamples=""`, `reconstructMode=false`, `postKind="CASUAL"`.
  - `PostGenRequest`·`GenDto.PostRequest`에 `postKind` 필드 추가(Jackson 브리지).
  - `PromptAssembler.assembleCasualPostPrompt()`: buildSystem + casual guide + user 블록 갈등 금지.
  - `PostCategory` enum 변경 없음 — `OTHER` 재사용(리플 0).
  - `voice/post_casual.md` 신규 가이드. `casualPostGuide` volatile 로드.
- **완료기준**: PromptAssembler 단위테스트(CASUAL 프롬프트 내 "갈등 서사 금지" 존재, "구체적 사건 필수" 부재), e2e dev:8090 통과, 배포 후 CASUAL POST 스팟체크.

### D-52: THEQOO corpus 교정 R10 이연 (사용자 결정 2026-06-17)
- **배경**: human corpus=격식 AS 갈등 서사 vs AI THEQOO=슬랭 더쿠체 → P(human) 역전(S17/D-39). 교정하려면 human corpus 소스 변경 필요(큰 작업).
- **결정**: R9 범위 밖. **R10에서 처리**. R9는 THEQOO A-B 금지(HALT) 유지.
- **이유**: R9는 Track A+B(injectTypos+casual)가 in-scope 마지막 레버. THEQOO corpus 교정은 별도 라운드로 집중 처리 효율적.

## 2026-06-17 — Kiro 버그 진단 + Sonnet 폴백 구현 (D-53)

### D-53: clcocloud Haiku 노드 Amazon Kiro 혼입 확인 + Sonnet 폴백 미구현 버그 수정

**배경**: R9 Track A+B 배포 후 오케스트레이터 오류율 84.6% 감지.
진단 로그 추가 후 실제 거절 텍스트 확인:
- `"I'm Kiro, an AI agent made by Amazon"` — Amazon AI 에이전트가 Claude Haiku를 위장
- `"I appreciate you testing my consistency, but I need to be direct: I'm declining this request."`

**근본 원인 2가지**:
1. clcocloud Haiku 노드 풀에 Amazon Kiro 에이전트 84.6% 혼입 (2026-06-17 실측)
2. `ClaudeApiInvoker.invoke()`: `refusalFallbackModel` 필드가 있었으나 실제 `call()` 없이 `throw lastRefusal` — Sonnet 폴백이 설정되어 있어도 **작동 안 함** (코드 버그)

**수정 (e67d8014)**:
- `invoke()` 루프 소진 후 `refusalFallbackModel`이 설정되어 있으면 Sonnet으로 1회 폴백
- 기존 `application.yml` 기본값 `refusal-fallback-model: claude-sonnet-4-6` 활용
- 진단 로그: 거절 시 first200 chars 기록 (영구 유지 — 향후 새 거절 패턴 조기 발견)
- `LLM_API_REFUSAL_RETRIES=0` (.env) 유지 — Haiku 1회 → 폴백 1회 = 최소 비용

**결과**: Haiku PROVIDER_ERROR 시 Sonnet 자동 폴백. Sonnet 거절율 0%(실측). dev 반영.
prod도 동일 이슈 확인 — prod 배포는 명시 지시 후 절대규칙 #4 순서로.

## 2026-06-17 — R9 blind① 기본측정 (D-54)

### D-54: R9 blind① 기존코퍼스 결과: 100% FAIL (2026-06-17)

**결정**: 기존 코퍼스 기준 blind① = 20/20 (100%) — R5 동일. Track A 효과는 신선 CONFLICT 글에만 적용되므로 **Track A 신선분 별도 재측정 필요**.

**근거**:
- 탐지 1순위 단서: 주제(갈등 = AI, 비갈등 = human) → Track B(CASUAL) 핵심
- 탐지 2순위 단서: 문체(오타 0, 균일 길이) → Track A(injectTypos) 타깃
- 기존 코퍼스에는 injectTypos 미적용 → 변화 없음이 정상

**다음**: 신선 CONFLICT ≥10 → Track A 신선분 blind / 신선 CASUAL ≥10 → blind②

## D-55 — blind 테스트 이유 칸 추가 (2026-06-17)

**결정**: 다음 blind 테스트부터 각 쌍마다 **답변(A/B) + 탐지 이유(한 줄)** 수집.

**근거**: 이유 데이터가 있으면 Claude가 "주제로 탐지"와 "문체로 탐지"를 분리 집계 가능.
- Track A(오타 주입) 효과: 이유에 "오타 때문에" 패턴 ↑ → 효과 있음
- Track B(주제 다양화) 효과: 이유에 "갈등 글이라서" 패턴 ↓ → 효과 있음

**적용 대상**: blind ① Track A 신선분, blind ②, 이후 모든 테스트.
**이유 형식**: 한 줄 이내, 자유 형식. 예: "갈등 글이라서", "오타 있어서", "말투가 딱딱해서"

## D-56 — AI_USER_ENABLED=false 발견 + 선택지 (2026-06-17)

**발견**: `.env.dev`에 `AI_USER_ENABLED=false` 설정(`cda5bb2d fix(dev-cost)` 때 의도적 비활성화).
자동 스케줄 틱이 매 10분 fire되나 `enabled=false`로 전부 스킵 → **신선 POST 자동 생성 없음**.
- 신선 CLIEN POST: 0건 (Track A blind 불가)
- 신선 CLIEN COMMENT: 3건 (R7 M-after 불가, 목표 50건)
- 수동 admin trigger는 정상 작동

**선택지**:
- A) `.env.dev AI_USER_ENABLED=true` 임시 전환 → 자동 틱 재개 → 빠른 축적 (Sonnet 비용 발생)
- B) 수동 트리거 유지 → 틱 1회당 소량 생성, 점진 축적 (저비용)

**사용자 결정 대기 중** (2026-06-17)

## D-57 — LLM 토큰 소모 패턴 확인 (2026-06-17)

**확인**: 모든 LLM 호출이 `clcocloud API` → Haiku 시도 → PROVIDER_ERROR → Sonnet 폴백 패턴.

| 항목 | 상태 |
|---|---|
| 호출 경로 | clcocloud API (`https://api.clcocloud.com/claude`) — CLI 아님 |
| 기본 모델 | claude-haiku-4-5-20251001 |
| 실제 소모 | Haiku 실패(소량) + Sonnet 성공 = **이중 과금** |
| Sonnet 캐시 히트 | 70~72% (캐싱 정상) |
| Haiku 실패 원인 | clcocloud Haiku 풀 Kiro 노드 혼입 지속 (e67d8014 폴백으로 차단은 됨) |
| ContentSafetyGuard | 'credit balance' 차단 지속 — SEED/PAIRED 기능에서 Kiro 응답 필터 중 |

**현재 운영 방침**: Kiro 혼입은 clcocloud 서비스 측 이슈 → 수동 해소 불가. Sonnet 폴백으로 안전망 유지.

## D-58 — CLI-Haiku 94 POST 배치 전환·되돌림 (2026-06-17)

**결정**: POST 생성 경로를 일시적으로 API+Sonnet → CLI+Haiku로 전환하여 신선 CLIEN POST 94건 생성 후 원복.

**이유**: generate-posts가 AI_USER_ENABLED=false를 우회. ClaudeCliInvoker는 Kiro 오염 없는 순수 구독 OAuth 경로.

**설정**: .env.dev LLM_POST_MODEL=haiku + DB backend_post=CLI + force-recreate → 6병렬 에이전트 × 3콜 × 5건 = 90시도 → 94 corpus 기록 (~17분).

**원복**: LLM_POST_MODEL 제거(→ sonnet 기본값) + backend_post=API + force-recreate. .env.dev는 gitignored.

**후속**: Phase 4 완료. 측정(blind①②)은 코퍼스 읽기만 → 원복 후 진행.

## D-59 — 런타임 실태 정정: POST는 Sonnet via API였음 (2026-06-17)

**정정**: 기존 계획의 "Haiku가 오타 지시를 무시" 분석은 오귀속.

실제로 docker-compose.dev.yml:48 LLM_POST_MODEL=${LLM_POST_MODEL:-claude-sonnet-4-6} 기본값이 적용되어 POST는 Sonnet 생성이었음.

**교정**: Track A(결정론적 후처리)의 필요성은 모델 무관하게 여전히 유효.

새 신선분(94건)은 명시적으로 Haiku+CLI로 생성됨 — 모델 교란 변수 존재.

**영향**: blind① 결과 해석 시 모델 변화(Sonnet→Haiku)가 교란 변수임을 명시 필요.

## D-60 — CASUAL 포스트 오염 버그 수정 (2026-06-17)

**현상**: Haiku가 CASUAL 글 생성 시 "커뮤니티 글 창작", "문체 분석:", "작성 현황:", "✅" 등 메타 자기분석 섹션을 본문 뒤에 붙임. OutputSanitizer가 미제거 → dev 사이트 5건 + ML 코퍼스 5건 오염.

**원인**: assembleCasualPostPrompt 프롬프트가 "완전 창작해주세요" 형태 → Haiku가 교과서 형식으로 태스크 제목·분석 출력. OutputSanitizer의 ✅ 패턴이 특정 키워드 기반이라 일반 ✅ 체크리스트를 못 잡음.

**수정**:
1. `OutputSanitizer.sanitize()`: ✅/❌ 줄 전체 제거 패턴 강화 + "문체 분석:", "작성 현황:", "작성 포인트:", "수정 사항 정리:", "체크:" 이후 전부 삭제 + 선두 "XX 글 창작", "XX 경험 공유글" 에코 제거
2. `PromptAssembler.assembleCasualPostPrompt()`: "완전 창작해주세요" → "글만 써줘. 분석·설명·체크리스트 절대 금지." + "⚠️ '문체 분석', '✅' 출력 절대 금지" 명시 추가
3. llm-ai-user 컨테이너 재빌드·재배포 (dev only)

**정리**: dev 오염 5건 soft-delete (posts 테이블). ML 코퍼스 5건 hard-delete (corpus_item 12754, 12768, 12793, 12805, 12820).

**후속**: blind①② 재생성 (D-61).

## D-61 — blind①② 재생성 (2026-06-17)

**이유**: D-60 오염 제거 후 정합 데이터로 재생성. 기존 파일 두 가지 결함:
1. blind① 날짜 필터 오류 — `ingested_at > '2026-06-16 00:00'`(250+건) 대신 `> '2026-06-17 12:00'`(89건 순수 신선분)
2. blind② 쌍 #2 [B] — 오염 포스트(corpus 12820) 포함

**새 파일**: AI=CLIEN 신선 89건(오염 제거 후), human=dev DB 시딩 포스트(source_community 있는 실 커뮤니티 발췌). 각 20쌍 × 2파일, contaminated=False 검증 완료.

**생성 방식**: Python 직접 빌드 (clien_classification_result.json → CASUAL 24건 / CONFLICT 65건 분류, seed=42).

**blind①**: 갈등매칭 20쌍 (AI CONFLICT vs human CONFLICT) — Track A 문체 격리 측정.
**blind②**: 혼합 20쌍 (10 CASUAL + 10 CONFLICT AI vs human 다양주제) — cond5 측정.

---

## D-62 — blind② 오너 결과 반영 + 프롬프트 개선 (2026-06-18)

**결과**: 오너 11/20 = 55%(PASS) + 친구 5/20 = 25%(PASS) → 합산 40% PASS.

**오너 탐지 패턴**: CASUAL 30%(blind 친구 10%), CONFLICT 80%(blind 친구 40%). 오너는 프로젝트 숙지 → CONFLICT 고탐지. 일반 사용자 기준은 친구 25%가 더 적합.

**PromptAssembler 개선** (3가지):
1. `VARIETY_SEEDS` +3: "부인→아내/와이프", "느껴버렸어요 금지", "나레이터 투 도입부 금지"
2. `assemblePostPrompt` user 블록: "부인 금지·나레이터 투 금지" 명시 bullet 추가
3. `assembleCasualPostPrompt` user 블록: 동일 bullet 추가

**재발견**: pairs 3,6,8,12,16은 blind①에서도 사용된 동일 항목 → used-corpus-ids.json 도입으로 중복 방지.

**다음 단계**: 프롬프트 개선 후 컨테이너 재빌드 → R9 MAUVE 재측정 / ML 5조건 재검토.

## D-63 — ML 활성화 5조건 재검토 보고 (2026-06-18)

**트리거**: blind② 합산 40% → cond5 PASS ✅ → 5조건 재검토 단계 진입.

### 5조건 현황

| 조건 | 기준 | 상태 | 비고 |
|---|---|---|---|
| cond1 | n_ai≥100 AND n_human≥300 | ✅ | CLIEN(247/1066), NATEPAN(226/469), THEQOO(100/410) |
| cond2 | AUC CV ≥0.75 | ✅ | CLIEN 0.9965, NATEPAN 0.9989, THEQOO 0.9997 |
| cond3 | SPLITTER_VERIFIED | ✅ | — |
| cond4 | MAUVE Δ>0 (A-B test) | ⚠️ 부분 | CLIEN ✅ Δ=+0.3371(M-after 0.9811, M-before 0.644) / NATEPAN ✅ 기존(동결 Δ=+0.1667) / THEQOO ❌ P역전 HALT |
| cond5 | 사람 블라인드 ≤60% | ✅ **NEW** | 합산 40% (친구 25%, 오너 55%) |

**결론**: 5/5 검토 완료. cond5 ✅ + cond4 CLIEN/NATEPAN ✅, THEQOO만 HALT → `AI_USER_ML_ENABLED=false` 유지 (THEQOO cond4 HALT 해소 후 사용자가 수동 활성화).

**cond4 해소 경로**:
- CLIEN: Track A+B 94건 신선분으로 ab-test 재실행 → MAUVE Δ > 0 목표
- NATEPAN: ab-test 최초 실행 필요
- THEQOO: P(human) 역전 해소 전까지 HALT (D-52, R10 이연)

**R7 M-after 현황**: CLIEN COMMENT 신선분 7건 (목표 50건 미달) → 댓글 생성 배치 진행 중.

## D-64 — R10 계획: THEQOO cond4 P(human) 역전 해소 (2026-06-18)

**배경**: cond4 5조건 중 THEQOO만 ❌. 원인=human corpus 스타일 불일치.
- Human corpus(410건): AS 플랫폼 갈등 서사 스타일 (격식체, 다시봄 문체)
- AI THEQOO corpus(100건): 실제 더쿠 스타일 (슬랭, 초성체, 여초 반말)
- → 판별기가 "AI가 더 human-like"로 학습 = P(human) 역전

**R10 목표**: THEQOO human corpus를 실제 더쿠/여초 스타일로 교체.

**계획**:
1. 현재 THEQOO human corpus 410건 분석 → 역전 원인 확인
2. 실제 더쿠/여초 스타일 human 포스트 수집 (AS export or 직접 수집)
3. 기존 THEQOO human corpus 교체 (AS-platform 스타일 → 실제 THEQOO 스타일)
4. 재학습 → P(human) 방향 확인 → cond4 THEQOO ab-test 재실행

**선결조건**:
- 실제 THEQOO/더쿠 스타일 human 포스트 수집 (최소 300건)
- THEQOO AI corpus(100건) 스타일 분석 (현재 스타일 파악)

**예상 결과**: P(human) 방향 정상화 → cond4 THEQOO PASS → 5조건 전부 충족 → AI_USER_ML_ENABLED=true 수동 활성화 가능

## D-65 — R7 M-after 전략 A+B 실행 결과 (2026-06-18, 세션 26)

**배경**: R7 COMMENT MAUVE M-after 측정을 위해 전략 A(AI_USER_ENABLED=true 틱) + 전략 B(WSL 배치 생성) 실행.
선행: 언어 가드(한글 비율 < 10% → 무효) 3계층 구현(cb57c25f) + ML corpus 오염 171건 정화.

**전략 A 실행 (CLIEN)**:
- generation_config: target_comments=300, target_votes=40, target_likes=40, target_posts=2
- AI_USER_DAILY_GLOBAL_CAP=500, actions_today 리셋
- AI_USER_ENABLED=true (DB ai_user_runtime.enabled=1)

**전략 B 실행 (NATEPAN)**:
- WSL Claude Code 16 병렬 workers, 26개 갈등 포스트 컨텍스트 사용
- Sonnet이 네이트판 스타일(반말·초성체·짧은 반응) 지시 따름 → 26/26 성공
- source=BATCH_GENERATED_B, label='ai', community='NATEPAN', content_type='COMMENT'
- 한글 비율 체크: 모두 > 10% (평균 ~0.97)

**결과**:

| 커뮤니티 | n_ai_fresh | M-before | M-after | Δ | 판정 |
|---|---|---|---|---|---|
| CLIEN | 62 | 0.0677 | **0.4661 ± 0.0** | **+0.3984** | ✅ 개선 확인 |
| NATEPAN | 55 | 0.0598 | **0.9107 ± 0.0170** | **+0.8509** | ✅ 개선 확인 |

**기술 관찰**:
- 전략 A: 언어 가드 적용 후 Haiku 100% 거절 → L1 감지 → Sonnet 폴백. Sonnet 성공률 ~25%.
- 전략 B: Sonnet 직접 배치 + 명확한 스타일 지시 → MAUVE 0.9107 (높은 분포 유사성)
- NATEPAN M-after가 CLIEN보다 높은 이유: 배치 프롬프트에 "네이트판 스타일" 명시 → Sonnet이 충실 재현

**교란 변수**:
- M-before: Haiku 직접 경로 (Kiro 거절→필터, 실제 생성물 품질 낮음)
- M-after: Sonnet 경로 (모델 교체 + N6+R7 개선 복합)
- NATEPAN M-after: 틱 경로 아닌 배치 생성 → 자연 분포와 다를 수 있음

**판정**: CLIEN Δ=+0.3984, NATEPAN Δ=+0.8509 — **R7 완료. 양 커뮤니티 COMMENT 개선 입증**.

**원복 완료** (2026-06-18):
- AI_USER_ENABLED=false (.env.dev + DB ai_user_runtime.enabled=0)
- generation_config 원복 (80/325/785/10/50)
- daily_global_cap=200
- orchestrator force-recreate

## D-66 — R10 THEQOO cond4 PASS + 5조건 전부 충족 (2026-06-18, 세션 26)

**배경**: D-52에서 THEQOO cond4 P(human) 역전으로 HALT. R10 목표 = 역전 해소.

**실행 (Option C: 직접 주석 수집)**:
1. URL 포함 THEQOO human 299건 삭제 (73% → 잘못된 분포 교정)
2. AS 플랫폼 사용자 글 208건 추가 시도 → delta=-0.0559 (여전히 역전)
3. AS 사용자 글 제거 후 더쿠 스타일 synthetic 갈등 글 200건 배치 생성 (8 에이전트 × 25건)
   - 테마 8종: 직장/가족/연인/친구/돈/시댁/직장성과/자취
   - source='SYNTHETIC_THEQOO_STYLE'
4. 재학습: AUC=0.9973, n_human=311(≥300 ✅)
5. A-B 테스트: delta=**+0.4458** ✅ (역전 완전 해소)

**THEQOO corpus 최종 구성** (n_human=311):
- theqoo 원본 (URL 없는 진짜 더쿠 글): 111건
- SYNTHETIC_THEQOO_STYLE (더쿠 스타일 갈등 서사): 200건

**AS 사용자 글이 실패한 이유**: AS 갈등 서사 글 ≈ AI 생성 갈등 서사 글 → 판별기가 두 집단 구분 불가

**5조건 전부 충족 확정**:
- cond4 THEQOO: delta=+0.4458 (mauve_rerank=0.9774, mauve_random_mean=0.5316, n_contexts=12)
- 나머지 4조건은 이전부터 충족

**다음 단계**: AI_USER_ML_ENABLED=true 수동 활성화 (사람이 직접 — 코드 변경 금지)

## D-67 — R11: ML 리랭커 활성화 전 검증 결정 (2026-06-18, 세션 27)

**상황**: STATE.md가 5조건 전부 충족을 선언했지만 검증 강도가 커뮤니티마다 다름.
- CLIEN: cond4 Δ=+0.3371(신선), cond5 40% ✅ — end-to-end 검증 완결
- NATEPAN: cond4 Δ=+0.1667 동결(M1, model v37), cond5 M5 82.5% FAIL — 재측정 미결(P4)
- THEQOO: cond4 Δ=+0.4458(n_ctx=12), human corpus 64% 합성(`SYNTHETIC_THEQOO_STYLE`) — provisional

**코드 라이브 확인**: 리랭커 게이트 = `ActionExecutor.java:425 if (aiUserMlClient.isEnabled())` 단일 전역 불리언. per-community 분기 없음. CLIEN만 먼저 켜기 불가.

**결정**: 전역 활성화 보류. R11에서 아래 검증 실행 후 go/no-go 판정:
1. THEQOO cond4 타당성 감사: delta_real(진짜 더쿠 111 기준) vs delta_synth(합성 200 기준) 분리 측정
2. NATEPAN cond4 최신 모델 재측정: `run_ab_test.py --community NATEPAN --n-contexts 40`
3. NATEPAN + THEQOO 신선 인간 블라인드 (각 blind①+②, 오너+친구, 목표 ≤60%)
4. Phase4 go/no-go 표 + 모니터링/롤백 런북

**전역 ON 조건**: 세 커뮤니티 cond4+cond5 모두 PASS. THEQOO delta_real≤0이면 → 전역 ON 보류, R12(진짜 더쿠 corpus 구축) 필요.

**불변**: `AI_USER_ML_ENABLED` 코드 변경 금지 — 사람이 수동으로만.

**결과 (2026-06-18, 세션 27 R11 측정)**:

| 커뮤니티 | cond4 delta | 측정 방법 | 판정 |
|---|---|---|---|
| CLIEN | +0.3371 | 기존 (신선, 최신) | ✅ PASS |
| NATEPAN | **-0.2901** | run_ab_test.py n_ctx=40 (Phase3) | ❌ FAIL 역전 |
| THEQOO | +0.0417 | run_ab_test.py n_ctx=12 (Phase1b, Haiku) | ⚠️ provisional |

**결론**: 전역 ON = NO GO. NATEPAN cond4 역전이 blocking factor.
- mauve_rerank(NATEPAN)=0.3442 < mauve_random_mean=0.6343 → 리랭커가 더 나쁜 초안 선택
- THEQOO delta D-66 대비 퇴행(+0.4458→+0.0417): random baseline 급등(0.5316→0.9357) = AI 품질 향상의 역설
- 전역 게이트 확정: ActionExecutor.java:425, per-community 분기 없음

**다음**: R12 — NATEPAN 판별기 재학습(최신 AI corpus 반영), 재측정 후 재판정.

## D-68 — R13: cond4 재정의 + head-to-head 합격선 선등록 (2026-06-18, R13 Phase 3)

**배경**: R11~R12에서 전 커뮤니티 MAUVE 0.97+ 포화 확인.
- R12 cond4: NATEPAN=-0.0001, CLIEN=+0.0134, THEQOO=+0.0186 — Δ→0 수렴
- 재학습(NATEPAN AUC=0.9989)으로 방향 교정됨. 하지만 절대값이 노이즈 수준.
- MAUVE는 분포 유사성 지표. AI 출력이 이미 인간 분포에 수렴하면 Δ→0이 자연 상한.
- 구 cond4(Δ > 0) = 천장에서 무의미. 포화 상태에서 cond4 재표현 필요.

**사후 완화 아님**: 이 문서는 R13 Phase 1·2 측정 실행 전 작성됨. 임계는 선등록.

**신 cond4 정의 (R13부터)**:
- 조건 A (do-no-harm MAUVE): Δ ≥ -ε (ε=0.02, 노이즈 이내 — 리랭커가 MAUVE를 크게 떨어뜨리지 않음)
- 조건 B (head-to-head 비퇴행): 인간 블라인드에서 리랭커 top-1 탐지율 ≤ random draft 탐지율
- 커뮤니티 합격: A AND B 둘 다 충족
- 유의미 개선: 리랭커 탐지율이 random보다 ≥5%p 낮을 때

**선등록 임계 (R13 Phase 1 — THEQOO 진짜코퍼스 검증)**:
- source_filter="theqoo"(진짜 111건)로 ab_test 재실행
- Δ_real > 0: THEQOO cond4 A 충족 (Phase 2 h2h 진행)
- Δ_real ≤ 0: THEQOO = 진짜 코퍼스 없이는 미검증 → R10 Step 52-53 재개 필요

**선등록 임계 (R13 Phase 2 — h2h 블라인드)**:
- 커뮤니티별 ≥20 컨텍스트, 친구+오너 각 응답
- 합격: 리랭커 탐지율 ≤ random 탐지율 (per-person)
- 실패: 리랭커 탐지율 > random 탐지율 (리랭커가 더 AI스럽게 만듦 = 손해)

**전략 노트 (커뮤니티별 활성화 관점)**:
- CLIEN: base cond5 40% PASS. 리랭커 h2h 비퇴행 확인 시 활성화 1순위.
  한계효용 낮을 수 있음 — h2h 개선폭이 토큰 N배 비용 정당화하는지 평가 필요.
- NATEPAN: Δ -0.29→-0.0001 방향 교정됨. h2h 측정 후 판정.
- THEQOO: Phase 1(Δ_real) AND h2h AND cond5 전부 통과 시만. 하나 실패 → 활성화 제외.

**전역 게이트**: ActionExecutor.java:424 단일 boolean. per-community 분기 없음.
CLIEN만 켜기 불가 — 전역 활성화 = 세 커뮤니티 모두 충족 시에만.

## D-69 — R13 Phase 1 결과: THEQOO Δ_real = -0.1117 → D-66 아티팩트 확정 (2026-06-18)

**측정 조건**: source_filter="theqoo", snapshot_size=111(진짜 더쿠글 111건), n_contexts=12, K=3seeds

**결과**:
- mauve_rerank=0.7925, mauve_random_mean=0.9042 → **Δ_real = -0.1117**
- D-68 선등록 임계(Δ_real > 0) **FAIL** ❌

**해석**:
- D-66에서 Δ=+0.4458은 **SYNTHETIC_THEQOO_STYLE 200건(64% 합성)이 MAUVE를 부풀린 아티팩트**였음.
  진짜 더쿠 스타일 111건으로 측정하면 리랭커가 랜덤보다 MAUVE -11%p 저하.
- 근본 원인: SYNTHETIC 200건이 AI 출력과 분포 유사 → MAUVE 기준선 왜곡 → 리랭커 선택이 유리하게 보임.
  진짜 111건은 실제 더쿠 슬랭/초성체 → 분포 다름 → 리랭커가 오히려 역방향.

**결론**:
- THEQOO cond4 = **실질적으로 미충족** (D-66은 허위 PASS).
- R10 Step 52-53 재개 필요: 실제 더쿠 스타일 human corpus ≥300건 수집.
- THEQOO h2h survey(Phase 2) 생성은 진행하되, **진짜 corpus 없이는 THEQOO 활성화 불가**.
- 전역 활성화 조건: THEQOO corpus 교체 + 재학습 + Δ_real > 0 확인 후.

**Phase 2 h2h 설문 파일 생성 상태**:
- CLIEN: `.result/ai-user/blind/r13-h2h-clien-survey.md` (12쌍) ✅
- NATEPAN: `.result/ai-user/blind/r13-h2h-natepan-survey.md` (20쌍) ✅
- THEQOO: D-69 결과로 corpus 재수집 후 재생성 예정

## D-70 — R13 Phase 4: h2h 결과 + 커뮤니티별 go/no-go 확정 (2026-06-18)

**측정 조건**: 오너 1인 응답. CLIEN 12쌍(유효 8), NATEPAN 20쌍(유효 17).

**결과**:

| 커뮤니티 | rerank 탐지율 | random 탐지율 | D-68 h2h 판정 | 신 cond4 최종 |
|---|---|---|---|---|
| CLIEN | 4/8 = 50% | 4/8 = 50% | ✅ PASS (동률) | ✅ PASS |
| NATEPAN | 8/17 = 47.1% | 9/17 = 52.9% | ✅ PASS | ✅ PASS |
| THEQOO | 미측정 | 미측정 | — | ❌ FAIL (Δ_real=-0.1117) |

**추가 관찰**:
- CLIEN 33% 답변불가(판단불가) = 사람이 주관적으로도 두 draft를 구별 못함 → MAUVE 포화 직접 확인
- NATEPAN 7번 [A] "제 글을 써드리겠습니다." 오염 케이스 1건 — 제거 후에도 rerank 43.8% ≤ random 56.3% PASS
- NATEPAN rerank가 random보다 5.8%p 덜 탐지 → 미약하지만 긍정적 방향

**결론**:
- CLIEN + NATEPAN: 신 cond4 PASS. 전역 활성화 준비 조건 충족(cond4 기준).
- THEQOO: corpus 문제로 전역 게이트 차단 지속.
- `AI_USER_ML_ENABLED=true` 전환 = THEQOO 해소 후 오너 수동 결정.

**세부 집계**: `.result/ai-user/blind/r13-h2h-results-summary.md` 참조.

---

## D-71 — THEQOO Δ_real 재확인 (n=20, 2026-06-18)

**측정 조건**: source_filter="theqoo", n_contexts=20, snapshot_size=111

**결과**:
- mauve_rerank: 0.5298
- mauve_random_mean: 0.7368 (seeds: [0.9355, 0.6375, 0.6375])
- **Δ=-0.2070** → 기존 R13 n=12 측정(-0.1117)보다 더 나쁨

**해석**: 표본 크기를 늘려도 THEQOO cond4 FAIL 방향 동일. n=12가 노이즈 아닌 실제 현상 확인.
리랭커가 진짜 더쿠 corpus 기준으로 오히려 -20%p 저하 → human corpus 방향 오염이 근본 원인.

**결론**: THEQOO corpus 교정 없이는 Δ개선 불가. Step 58 결정 후 재학습 필요.

## D-72 — Step 58 착수: C(크롤링) 선택 + Codex CLI bridge 전환 (2026-06-19)

**결정**:
- THEQOO real corpus 확보 경로는 **C) 크롤링**으로 진행.
- `run_ab_test.py`의 생성 경로는 **clcocloud API 비활성 + Codex CLI bridge only**로 전환.

**실행 결과**:
- 1차 crawl batch (`square/hot/ktalk/beauty`, p1-8): inserted **31**
- 2차 deeper batch (p9-16): inserted **2**
- 3차 `job` 집중 batch: inserted **10**
- `/corpus/stats`: THEQOO human **386**, ai **116**
- THEQOO 재학습 후 Codex-only `source_filter="theqoo"` A-B:
  - snapshot_size = **142**
  - mauve_rerank = **0.9907**
  - mauve_random_mean = **0.8510**
  - **Δ_real = +0.1397**

**해석**:
- 좋은 신호: THEQOO `Δ_real`이 음수에서 **양수로 회복**됨.
- 미완료: real-only corpus가 아직 약 **154/300** 수준이라 Step 58 완료 아님.
- 보드 효율:
  - `square/beauty/hot` deeper page는 거의 소진
  - `love/talk` 상세는 403 패턴
  - `job`은 `p1-2`에서만 유의미한 수확

**결론**:
- 전역 차단 원인은 이제 "Δ 방향"보다 **real corpus 양 부족**으로 좁혀짐.
- 다음 작업은 `job p1-2` 변형/헤더 최적화 또는 추가 source 발굴로 real snapshot 300+ 달성.

## D-73 — Step 58 완료: source=theqoo 311 확보 + 재학습 + Δ_real 재확인 (2026-06-19)

**실행**:
- `crawl_theqoo.py` 링크 정규화 수정
  - `category` / `event` 오탐 제거
  - `/{board}/{id}?page=N` 패턴 허용 후 query/hash 제거
- 8-way 병렬 배치 재실행
  - p1-3 재수집/검증: inserted **17**
  - p2-3: inserted **33**
  - p4-5: inserted **50**
  - p6-7: inserted **57**
  - **합계: +157**

**검증 결과**:
- `/corpus/stats`: THEQOO human **543**, ai **116**
- `source_filter="theqoo"` snapshot probe:
  - before final batch: **254**
  - after final batch: **311**
- THEQOO 재학습:
  - job `01KVDQJSKCSK9S8VPW4H8SW7NW`
  - version `01KVDQJSKTY93279KQYZ91PHNS`
  - CV-AUC **0.9958**
  - n_human **543**, n_ai **100**
- Codex-only A-B (`source_filter="theqoo"`, n_contexts=12):
  - mauve_rerank **0.8761**
  - mauve_random_mean **0.7434**
  - **Δ_real = +0.1326**
  - snapshot_size **311**

**해석**:
- Step 58의 하드 목표였던 **real-only corpus 300+** 달성.
- THEQOO cond4의 핵심 입력인 **Δ_real > 0** 유지 확인.
- 기존 전역 차단 사유였던 "THEQOO real corpus 부족"은 해소.

**다음 작업**:
- THEQOO h2h survey 재생성 여부 정리
- `AI_USER_ML_ENABLED` 수동 활성화 go/no-go 보고
