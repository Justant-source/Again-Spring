# AI-User v2 / v2.1 — Roadmap

> **⛔ v2 — CLOSED 2026-06-21** — Phase 0~6 완료, PASS (5/9=55.6%, 오너 1인). 측정 착시 2개(오라클 오염·tell 생존) 확인 → v2.1로 계속.
> v2.1 섹션은 이 파일 하단 참조.

---

## v2 — CLOSED (2026-06-21)

> **프로젝트**: 계정 단위 현실성 — NATEPAN 전용  
> **기간**: 2026-06-21 (1일 집중) · **규율**: `README.md` R1~R8  
> **결과**: v1 88.9% → v2 66.7% → v3 55.6% PASS (-33.3pp) · **prod 출하 완료**

---

## Phase 0 — 창립 & 방법론 동결 🔄

**목표**: v2를 v1과 구분된 프로젝트로 창립. 진단·bar·kill criterion·고정 baseline 정의를 문서에 동결.

**완료 체크리스트**:
- [x] `DIAGNOSIS.md` — 3-미스매치 진단 창립 문서
- [x] `README.md` — charter (bar·R1~R8·NATEPAN 전용)
- [x] `decisions.md` — V2-D01 스코프 확정
- [x] `STATE.md` — 라이브 포인터 초기화
- [x] `roadmap.md` — 이 문서
- [ ] **🔴 kill criterion 오너 사전 등록** (Phase 0 완료 블로커)
- [ ] `npm run lint:docs` 통과
- [ ] git commit + push

**Gate**: 5개 문서 + kill criterion 사전등록 타임스탬프 + `lint:docs`

---

## Phase 1 — NATEPAN 갈등 사연 최대 크롤 & 코퍼스 최대화 🔜

**목표**: NATEPAN 갈등 사연을 계획보다 공격적으로 최대 수집. 작성자-그룹핑 + 정화.

> **사용자 강조**: "네이트판에서 갈등 사연들을 최대한 많이 가져와"

**핵심 워크스트림**:

### 1-A: NATEPAN 전용 공격 크롤

- `ai-user/learning/app/crawlers/natepan*` 재가동
- 나머지 11종 크롤러 비활성(NATEPAN 예산 전량 집중)
- 일일 cap 상향 + **과거 아카이브 backfill**(사연 게시판 깊은 페이지네이션)
- 목표: clean 갈등 사연 수천~수만 건
- 실행: **WSL Claude Code** (최대 16 에이전트, RTX 3090 임베딩)

### 1-B: 작성자·타임라인 메타 캡처 (v1 최대 약점 해소)

- 기존 크롤: 글 단위 (author 그룹핑 없음) → **작성자 ID·게시시각 보존** 추가
- 단일 작성자 연속글 = 실제 계정 타임라인 → Phase 2 eval 인간 베이스라인으로 활용
- `example_bank` 스키마 또는 별도 테이블에 `author_id`, `posted_at` 보존

### 1-C: 공격적 정화

- 언어 가드: 한글 비율 < 10% → drop
- URL-heavy → drop (THEQOO식 오염 방지)
- 광고·중복: `content_hash` 전역 유니크 자동 dedup
- 비-사연(연예/뉴스): 필터 (키워드·도메인 기반)
- NATEPAN은 진성 슬랭 사연 → THEQOO식 formal 오염 적음

### 1-D: 적재

- `example_bank` (8099, KURE-v1 임베딩) — RAG few-shot 앵커
- ML 판별기 코퍼스 (8201, COLLECT-only, D-108)
- 정화 리포트: drop 사유별 카운트 → `crawl/phase1-report.md`

**Gate**:
- NATEPAN clean human count 대폭 증가 (before/after 스냅샷)
- 작성자-그룹 타임라인 ≥ M개 확보 (eval용, M = Phase 2에서 결정)
- 정화 drop 리포트 기록

**Halt**: 크롤 차단/rate-limit → 정중 백오프·보고. 정화 후 잔존 < 목표 → 보고 후 게이트 조정.

**주의**: 실 사이트 스크래핑 = rate limit·politeness·ToS 준수.

---

## Phase 2 — 계정 단위 Eval 하니스 (오라클 수정) ✅

**목표**: 고정된 계정-단위 오라클을 먼저 구축. **생성 변경보다 먼저.** 이후 모든 개선을 이 타깃에 측정(귀속 가능).

**완료**: 2026-06-21 — 블라인드 키트 v1/v2/v3 작성 · v3 평가 완료 (5/9=55.6% PASS)

**핵심 워크스트림**:

### 2-A: 실제 NATEPAN 계정 타임라인 베이스라인

- Phase 1의 작성자-그룹 사연으로 **장르 일치** 실계정 타임라인 M개 구성
- v1 약점: 주제묶음(다른 저자) → v2: 단일 작성자 연속글 → 해소
- 파일: `eval/human-timelines/`

### 2-B: 블라인드 키트

- AI 계정 타임라인 N개 + 실 NATEPAN 계정 M개
- 무작위 배치 (정답 봉인)
- **캐주얼 독자 지시문**: "이 계정 쭉 훑어봐 — 사람? 봇?" (포렌식 × )
- 평가자 **≥3인** (오너 + 친구 2)
- 파일: `eval/blind-kit-v2-01.md`

### 2-C: Named-Tell 라벨셋 산출 (R4)

- 봇 판정마다 **이유 명명** 강제 ("X한건지 모르겠음 말미 반복", "주제 다양성 없음" 등)
- 라벨 → Phase 5의 결정론 제거 타깃
- 파일: `eval/named-tell-labelset.md`

### 2-D: Pass 기준 사전등록 + Baseline 1회 측정

- **Pass 기준**: kill criterion 오너 확정값 (Phase 0 등록)
- **현 시스템(v1 출하 레버, NATEPAN) 계정 블라인드 1회** → 계정-단위 인간 baseline 숫자 확정
- proxy r15=0.150을 대체하는 **계정-단위 baseline**

**Gate**:
- 블라인드 키트 + 사전등록 기준(측정 **전** 기록 타임스탬프)
- baseline 1회 숫자 + named-tell 라벨셋 v0

**Halt**: 실계정 타임라인 확보 불가 → Phase 1로 되돌아가 데이터 보강 (폴백 명시 금지).

---

## Phase 3 — 계정 생성: 메모리 & Topic Trajectory ✅

**목표**: 페르소나가 자기 과거를 참조하고, 한 사건이 며칠~몇 주 발전하다 드리프트하는 **주제 궤적** 부여. i.i.d. 패턴 제거.

**완료**: 2026-06-21 commit 491e4515

> 감사상 **가장 큰 레버** — trajectory 현재 전무. 계정 humanness가 사는 곳.

### 3-A: Life-State / Ongoing-Situation 저장소

- personaId 키 DB 테이블 또는 구조화 history 메타
- 훅: `ActionExecutor.loadRecentBodies`(L1298) · `writeHistory`(L1161)에 사건·주제 메타 캡처
- Flyway 마이그레이션 (ENUM 대문자 규칙 준수, 마케팅 enum 버그 선례)

### 3-B: Continuity Block

- `PromptAssembler.assemblePostPrompt`(L119) — 반복회피 블록과 **별개** 연속성 블록 추가
- "지난주 X 썼음 — 이번엔 발전/드리프트"
- NATEPAN 갈등 saga (한 갈등이 여러 글로 전개)에 최적

### 3-C: Stateful CASUAL

- `ActionExecutor.java:346` i.i.d. Bernoulli(0.25) → 계정별 stateful 리듬
- Markov · streak 회피 (예: 3연속 CASUAL 방지)

**Gate**: 계정 타임라인이 단일 사건 전개를 보임(샘플 검수) + R3 준수 + 빌드/테스트 통과

**Halt**: 메모리 주입이 ContentSafetyGuard 오염루프 유발 → 즉시 보고 (절대규칙 #7)

---

## Phase 4 — Cadence & 상호작용 현실화 ✅

**목표**: 글 품질과 무관한 계정 단위 거대 신호(게시 리듬·대댓글) 현실화.

**완료**: 2026-06-21 commit a42fba61

### 4-A: 현실 지연 Reply

- 휴면 `Jitter.scheduleReplyWithDelay`(L55-59, 5–60분) 배선
- `BehaviorEngine.java:222` 훅 포인트
- 현재: 0–10분 균일 burst → 변경: 5–60분 realistic

### 4-B: 계정별 리듬

- `PersonaSelector`(L28-29) flat 20–90분 쿨다운 → 계정별 circadian/burst
- 예: 저녁형(18~23h 집중) · 점심버스트(12~13h)

### 4-C: 대댓글 깊이

- `InteractionScanner`(L29,52): MAX_REPLIES_PER_COMMENT=2 · top-level만 → 완화
- 다회전 대댓글 체인 · 봇간 왕복 허용

### 4-D: 자기글 응답

- 페르소나가 자기 글 댓글에 응답 (back-and-forth)

**Gate**: 비균일 cadence + ≥2단계 대댓글 관찰 + `scheduleReplyWithDelay` 호출됨 확인 + 빌드/테스트

**Halt**: 봇간 무한 응답루프 → cap 강화·보고

---

## Phase 5 — Named-Tell 결정론 제거 루프 ✅

**목표**: Phase 2 라벨셋의 named-tell을 **결정론적으로** 제거. 리랭킹 아님.

**완료**: 2026-06-21 — v3 eval 결과 PASS (5/9=55.6% ≤60%)

**결과 요약**: v2 기반 tell 35종 필터 + 어미·감정 다양화로 식별률 66.7%→55.6%(-11.1pp) 달성. 남은 탐지 5건은 신규 tell 4종(T1 동일사건반복 100% 탐지, T3 광장 불일치, T2 미완종결, T4 감정평탄화) + 사실 오류 1건. Phase 5b 어미 변형 3종(모르겠어/어요/네요) 미등록 → v2.1에서 추진.

**1st 타깃 (v1에서 100% 탐지 확인, 미필터)**:
> `"X한건지 모르겠음"` 말미 구조 (예: "이게 맞는 건지 모르겠음", "뭘 기준으로 봐야 하는 건지 모르겠음")

### 5-A: Tell 결정론 제거

- `SELF_CRITIQUE_EXTRA_CLICHES` (.env.dev:86) 확장
- `OutputSanitizer` 구조 필터 추가
- 대상: Phase 2 라벨셋 우선순위 tell부터

### 5-B: 판별기 = 진단 전용 (R6)

- feature attribution으로 어떤 tell 쓰는지 진단만
- rerank 절대 OFF

### 5-C: 계정 블라인드 1회/사이클 (저빈도)

- 각 제거 사이클 후 Phase 2 eval 1회
- 사이클당 변수 1개 (R3)
- 게이트 통과 시에만 다음 사이클

**Gate**: 사이클별 named-tell 제거 + 계정 블라인드 식별률 baseline 대비 개선 기록

**Halt**: 2사이클 후 개선 정체 → kill criterion 적용(Phase 6)

---

## Phase 6 — 결정 게이트 & 클로즈아웃 🔄

**목표**: 사전등록 kill criterion 적용. 정직한 종결 + 전이 교훈 봉인.

**상태**: 2026-06-21 PASS 판정 이후 **현재 진행 중** (prod ship 준비)

### 판정

- **PASS** (봇 식별률 ≤ kill criterion 임계): NATEPAN 계정 레버 prod 출하
  - 절대규칙 #4: dev → e2e dev:8090 전체 PASS → main push → DB 백업 → prod
- **FAIL**: QLoRA 데이터게이트 평가
  - NATEPAN clean ≥ 5000(예) → QLoRA 발동 옵션 제시 (결정: 사용자)
  - NATEPAN clean < 5000 → 품질-피벗 옵션 제시

### 전이 교훈 → ASM/WaggleBot

- 5 lesson을 페르소나 시스템 가이드로 기록
- `LESSONS-FOR-WAGGLEBOT.md` 작성

### 봉인

- `STATE.md` 최종 → CLOSED 또는 SHIPPED
- `lint:docs` + commit + push

**Gate**: kill criterion 판정 기록 + 데이터게이트/피벗 옵션 제시 + 교훈 문서 + lint:docs

**Halt**: 옵션 임의 결정 금지 — 제시까지만

---

## QLoRA 데이터게이트 (연기된 레버)

```
발동 조건:
  NATEPAN clean verified-real ≥ [오너 확정 임계, 예 5000]
  AND Phase 3~5 계정 레버가 kill criterion bar 미달로 plateau

미충족 → 100% 프롬프트 유지
충족 → 크롤정화 → SFT 데이터빌더 → 3090 학습 → 어댑터 레지스트리 (별도 work-order)
```

---

## 실행 분배

| 작업 유형 | 위치 | 최대 에이전트 |
|---|---|---|
| Phase 0·2·3·4·6 (문서·Java) | 로컬 | 8 |
| Phase 1 크롤·정화·임베딩 | **WSL Claude Code** | 16 |
| Phase 5 판별기 진단 | **WSL Claude Code** | 16 |
| (발동시) QLoRA | **WSL Claude Code** | 16 |

---

## 프로젝트 최종 결과 (2026-06-21 CLOSED)

- **총 소요**: 1일 집중 작업 (Phase 0~6)
- **결과**: v1 88.9% → v2 66.7% → v3 55.6% PASS (-33.3pp)
- **prod 출하**: llm-ai-user-prod SELF_CRITIQUE_EXTRA_CLICHES 35종 + bug fix 2건
- **교훈 문서**: `.result/ai-user-v2/lessons.md`
- **QLoRA 게이트**: 미발동 (PASS 달성으로 plateau 조건 불충족)
- **측정 착시**: (a) 오너 1인 오염 (b) tell 생존(P3·P5·P13·P15 묻힘) → v2.1 계속

---

---

# v2.1 — 광장 정렬(Plaza-Alignment)

> **프로젝트**: 제품 적합성 정렬 — 다시봄 6 광장  
> **시작일**: 2026-06-21 · **전임**: v2 CLOSED · **규율**: `charter-v2.1.md` R1~R8  
> **단일 오라클**: 신선 캐주얼 독자 ≥3인 계정 블라인드. proxy/MAUVE/LLM-judge 금지.

---

## Phase 0 — 창립 & 동결 (v2.1 Charter) 🔄

**목표**: v2.1 창립. 진단 승계 + 제품적합성 재구성 + eval 재정립 설계 동결. 새 kill criterion 오너 사전 등록.

**Gate**: 4개 문서 + **kill criterion 오너 등록 타임스탬프(측정 전)** + `lint:docs`

**Halt**: 오너 kill criterion 미확정 → Phase 1 진행 보류·보고

**토큰·GPU**: 낮음/0 | **위치**: 로컬 8 | **세션**: 독립

| 작업 | 파일 |
|---|---|
| roadmap.md v2 CLOSED + v2.1 섹션 | `.result/ai-user-v2/roadmap.md` |
| 재구성 3명제 동결 | `charter-v2.1.md` |
| V2-D04·D05 | `decisions.md` |
| 🔴 **kill criterion 오너 확정** (제안: ≤60% naive ≥3인) | `STATE.md` |
| v2.1 라이브 포인터 초기화 | `STATE.md` |

---

## Phase 1 — Eval 재정립 (오라클 수정, 설계 only) 🌱

**목표**: 새 오라클 설계 동결. naive ≥3 모집·지시 프로토콜, 광장별 계정 타임라인 블라인드 키트 SPEC, 회전 레지스트리(기억 방지), 오너=캘리브레이션. (키트 채움·측정은 Phase 5.)

**Gate**: 3개 eval 설계 문서 + 회전 레지스트리 초기화 + kill criterion 정합 + `lint:docs`. **측정 0회.**

**Halt**: naive ≥3 확보 불가 → 완화 옵션(2인+오너 캘리브레이션 혼합) 제시까지만·보고

**토큰·GPU**: 낮음/0 | **위치**: 로컬 8 | **세션**: 독립 (Phase 2와 병렬)

| 작업 | 파일 |
|---|---|
| naive 평가자 정의·지시문·채점법·집계 규칙 | `eval/v2.1/oracle-protocol.md` |
| 광장별 키트 템플릿 (계정당 ≥3 포스트, 정답키 분리) | `eval/v2.1/blind-kit-spec.md` |
| 패널 회전 레지스트리 (오너=캘리브레이션 전용 표기) | `eval/v2.1/evaluator-registry.md` |
| 라운드 예산 (baseline Ph5 + 최종 Ph8 = 2회) | oracle-protocol 내 |
| 캘리브레이션 절차 (오너 별도 채점·차이 측정·게이트 아님) | oracle-protocol 내 |

---

## Phase 2 — 토픽 분류기 + NATEPAN 6광장 분류 + 인벤토리 🖥️ GPU

**목표**: 기존 `example_bank` NATEPAN **7,106건**을 6 광장으로 1회성 분류. 광장별 clean count + thin 광장 식별. 신규 크롤러 금지.

**Gate**: 7,106건 100% 라벨 + 광장별 count 스냅샷 + thin 광장 명시 + 스팟체크 정확도. (분포 = **sanity 게이트**, humanness 아님)

**Halt**: 스팟체크 정확도 < 임계 → 시드/키워드 보강 후 재실행(3회 한도)

**토큰·GPU**: Claude ≈0 / **GPU 집약** | **위치**: WSL 16 (RTX 3090) | **세션**: 독립 (Phase 0 후)

| 작업 | 상세 |
|---|---|
| 시드 라벨 | 키워드 앵커(남친·여친→COUPLE·남편·아내·시댁→MARRIED·친구→FRIEND·엄마·아빠·동생→FAMILY·회사·팀장→WORK·else→OTHER) + 저신뢰 ~300건 LLM-classify(`topic_synthesizer` 템플릿) |
| KURE-v1 임베딩 | `EmbeddingService.embed_batch`(`embedding.py:31`), 3090 |
| centroid kNN 할당 | 코사인 + 저신뢰→OTHER. lovetalk 섹션(`natepan.py:29-32`) 존재 행 COUPLE 가중 |
| **적재** | **`example_bank.category` 덮어쓰기**(현 'talk' → 6광장) → stage-1 RAG 즉시 활성화(검색 코드 0변경) |
| 인벤토리 리포트 | `crawl/v2.1-plaza-inventory.md` (광장별 count + thin 광장) |

---

## Phase 3 — 빈약 광장 외과적 보강 + 작성자 타임라인 메타 🖥️

**목표**: Phase 2 thin 광장만 보강 크롤. 기존 `natepan.py` 섹션 활용, 6 새 크롤러 금지. author_id·posted_at 보존.

**Gate**: thin 광장 RAG 앵커 최소치(광장당 clean ≥ M, Phase 1/2 확정) + 정화 리포트 + author 메타 보존

**Halt**: 크롤 차단/rate-limit → 정중 백오프·보고. 잔존 < 목표 → 보고 후 게이트 조정(합성 금지)

**토큰·GPU**: 낮/중(임베딩) | **위치**: WSL 16 | **세션**: 독립 (Phase 2 후)

| 작업 | 상세 |
|---|---|
| thin 광장 타깃 크롤 | `STATIC_SECTIONS` lovetalk→COUPLE 등 광장 친화 섹션 |
| 섹션→광장 힌트 보존 | `_parse_post_detail`(`:147`)에 `sec["name"]` 전달, 'talk' 하드코딩 제거(`natepan.py:166`) |
| ToS·politeness | rate-limit 백오프, open-web NATEPAN 우선, auth-wall(Blind 등) 제외 |
| 정화 | 한글<10% drop, URL-heavy drop, `content_hash` dedup |
| 신규 ingest | Phase 2 분류기 자동 적용 + author_id/posted_at 적재 |

---

## Phase 4 — 페르소나↔광장 정렬 + 카테고리 조건부 RAG 생성 🔧

**목표**: 페르소나를 6 광장에 균등 매핑(WORK 편중 해소), few-shot 앵커를 해당 광장 코퍼스에서만 추출. 목표 tell = 맥락 불일치·주제 편중 소멸.

**Gate**: 페르소나 6광장 균등 분포 + 샘플 생성에서 광장-매칭 앵커 확인 + 맥락 불일치/주제 편중 **육안 소멸** + 빌드/`lint:docs`. (육안 sanity, humanness 판정 아님)

**Halt**: `ContentSafetyGuard` 오염루프 → 즉시 보고(절대규칙 #7). R7 훼손 감지 → 롤백·보고

**토큰·GPU**: 중/낮(재임베딩 WSL) | **위치**: 로컬 8 (+ WSL) | **세션**: 독립 (Phase 2·3 후)

| 작업 | 코드 |
|---|---|
| 페르소나↔광장 정렬 | `profile.yml interests` argmax 감사·재배분(코드 변경 0: `topCategory`:1506-1513) |
| 카테고리 조건부 RAG | Phase 2 라벨로 stage-1 필터 광장-매칭 앵커 반환. thin 광장 stage-3 누수캡(`examples.py`, 선택) |
| CASUAL 프레임 정렬 | `CASUAL_FRAMES`(:868-879) 광장-관계 친화·갈등 인접으로 정렬(화목/날씨 무관글 제거). **확률 25%·메커니즘 불변(R7)** |
| 검증 | 광장별 샘플 생성 → 앵커 동일 광장 확인 + 맥락 일치. 빌드/테스트 |

---

## Phase 5 — 광장별 baseline 블라인드 1회 (naive ≥3) 👥

**목표**: Phase 1 새 오라클로 첫 측정. 광장별 계정 타임라인 블라인드. named-tell 라벨셋 v0 산출.

**Gate**: 광장별 식별률 + named-tell 라벨셋 v0 + 회전 레지스트리 갱신 + kill criterion 대비 판정

**Halt**: naive <3 → Phase 1 완화 옵션 발동·보고. 인간 계정 타임라인 부족 → Phase 2/3 회귀

**토큰·GPU**: 낮음/0 — **인간 바운드** | **위치**: 로컬 8 + 인간 | **세션**: 독립 (Phase 1·4 후)

| 작업 | 상세 |
|---|---|
| 키트 채움 | 광장당 AI 계정(Phase 4) + 인간 계정(NATEPAN 작성자 타임라인, 쓰니 661 등), 계정당 ≥3 포스트 |
| 평가 실시 | naive ≥3 (회전 레지스트리 기록). 오너 = 별도 캘리브레이션 채점(게이트 아님) |
| named-tell 라벨셋 산출 | 봇 판정마다 이유 명명 → Phase 6 다양화 타깃 |
| 기록 | kill criterion 대비 baseline 식별률(측정 1회, R3) |

---

## Phase 6 — 잔존 생성 tell 결정론적 다양화 1라운드 ✅ (2026-06-21 완료)

**목표**: Phase 5 라벨셋의 생성 스타일 tell(수렴 종결·감정 평탄화·이중질문 종결) 다양화. **1라운드 한정.** whack-a-mole 금지.

**Gate**: 종결·감정 분포가 baseline 대비 다양화(분포 sanity) + 빌드/`lint:docs`. (humanness 판정은 Phase 8)

**Halt**: 1라운드 후 육안 정체 → QLoRA 게이트(Phase 7) 분기. 표면형 추가 추격 금지.

**토큰·GPU**: 중/0 (판별기 진단 WSL) | **위치**: 로컬 8 (+ WSL) | **세션**: 독립 (Phase 5 후)

| 작업 | 상세 |
|---|---|
| 종결 다양화 | 어미 추격(whack-a-mole) 금지. 종결 **유형** 분산(질문·감정폭발·미완 혼합) — `PromptAssembler` 또는 `OutputSanitizer` |
| 감정 다양화 | 느낌표·감정 수식어·감정 선행 패턴 다양화(단조 사건 나열 분산) |
| 단일 변수 | 이 1라운드 = 종결·감정 다양화 1개 변수(R3). SelfCritique 임계(5/7) 불변 |
| 판별기 진단 | feature attribution으로 tell 진단만(R6). rerank OFF |

---

## Phase 7 — QLoRA 데이터게이트 판정 + (충족시) 광장별 fine-tune ❌ QLoRA 비발동 (2026-06-21)

**목표**: 3조건 AND 충족 시에만 3090 QLoRA. 미충족 = 100% 프롬프트 유지. 이미 이긴 적 재타격 금지.

**Gate**: 3조건 판정 기록(발동/비발동 근거) + (발동시) 어댑터 산출 + 광장별 검증

**Halt**: 조건 미충족 발동 시도 = **금지**. 데이터 부족 → 비발동·보고

**토큰·GPU**: Claude ≈0 / **(발동시) GPU 집약**, 미발동 0 | **위치**: WSL 16 | **세션**: 독립 (Phase 6 후)

**3조건 AND 판정**:
1. 재정립 eval(naive ≥3) 식별률 여전히 kill 임계 **초과**
2. 잔존 탐지가 **생성 스타일 tell에 귀속**(맥락·편중 아님 — Phase 4 처리)
3. 광장별 clean ≥ 임계(오너 확정, 예 광장당 수백~수천)

미충족 → Phase 8. 충족 → SFT 데이터빌더 → 3090 QLoRA 어댑터 → 어댑터 레지스트리 (별도 work-order).

---

## Phase 8 — kill criterion 최종 판정 + 출하/피벗 + 교훈 봉인 🔄 진행 중

**목표**: 새 오라클로 최종 측정 → kill criterion 판정 → 출하 or 피벗 **옵션 제시(임의 결정 금지)**. 교훈 봉인.

**Gate**: 최종 판정 기록 + 출하 완료(PASS) 또는 옵션 제시(FAIL) + 교훈 문서 + `lint:docs` + 절대규칙 #4·#8

**Halt**: 옵션 임의 결정 금지. prod 배포 = 명시적 지시 + 절대규칙 #4 순서

**토큰·GPU**: 낮음/0 — **인간 바운드** + 배포 | **위치**: 로컬 8 + 인간 | **세션**: 독립 (Phase 7 후)

| 판정 | 결과 |
|---|---|
| **PASS** | 절대규칙 #4: dev → e2e-realbe dev:8090 PASS → main push → DB 백업 → prod. 레버 보존, ML false 유지. |
| **FAIL** | 옵션 제시까지만: (a) QLoRA 데이터게이트 (b) 품질-피벗 (c) 광장 보강. **오너 결정.** |

| 작업 | 파일 |
|---|---|
| 최종 블라인드 (신선 패널) | `eval/v2.1/` 키트 |
| 교훈 봉인 | `lessons.md` v2.1 추가 + `LESSONS-FOR-WAGGLEBOT.md` 갱신 |
| 최종 폐쇄 | `STATE.md` SHIPPED 또는 옵션 대기 + `lint:docs` + commit/push |

---

## 실행 분배 (v2.1)

| 작업 유형 | 위치 | 최대 에이전트 |
|---|---|---|
| Phase 0·1·5·8 (문서·eval·배포) | 로컬 | 8 |
| Phase 2 (분류·임베딩) | **WSL Claude Code** | 16 |
| Phase 3 (크롤·정화·임베딩) | **WSL Claude Code** | 16 |
| Phase 4·6 (Java·Python 코드) | 로컬 | 8 |
| Phase 7 QLoRA (발동시) | **WSL Claude Code** | 16 |

---

**kill criterion 재등록(오너 확정) 전까지 어떤 humanness PASS/FAIL 판정도 금지.**
