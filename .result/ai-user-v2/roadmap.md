# AI-User v2 — Roadmap

> **프로젝트**: 계정 단위 현실성 — NATEPAN 전용  
> **시작일**: 2026-06-21 · **규율**: `README.md` R1~R8

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

## Phase 2 — 계정 단위 Eval 하니스 (오라클 수정) 🔜

**목표**: 고정된 계정-단위 오라클을 먼저 구축. **생성 변경보다 먼저.** 이후 모든 개선을 이 타깃에 측정(귀속 가능).

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

## Phase 3 — 계정 생성: 메모리 & Topic Trajectory 🔜

**목표**: 페르소나가 자기 과거를 참조하고, 한 사건이 며칠~몇 주 발전하다 드리프트하는 **주제 궤적** 부여. i.i.d. 패턴 제거.

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

## Phase 4 — Cadence & 상호작용 현실화 🔜

**목표**: 글 품질과 무관한 계정 단위 거대 신호(게시 리듬·대댓글) 현실화.

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

## Phase 5 — Named-Tell 결정론 제거 루프 🔜

**목표**: Phase 2 라벨셋의 named-tell을 **결정론적으로** 제거. 리랭킹 아님.

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

## Phase 6 — 결정 게이트 & 클로즈아웃 🔜

**목표**: 사전등록 kill criterion 적용. 정직한 종결 + 전이 교훈 봉인.

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
