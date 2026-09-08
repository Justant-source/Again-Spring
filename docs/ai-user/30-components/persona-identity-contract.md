---
title: Persona identity contract (persona-diversity-v4)
last_updated: 2026-09-05
---

# 페르소나 신원 축 설계 계약 — persona-diversity-v4

> 이 문서는 `persona-diversity-v4` 트랙(WP1~WP4)의 원 작업 지시 문서(gitignore 대상 경로에만 있어
> 유실 가능했던 WP 공용 계약 파일)에 담겼던 계약 1~7의 **git 추적 권위본**이다. 트랙 진행 상태·Phase 4
> 트랙 경위와 배포 이력은 [history.md](../history.md)를 보고, 이 문서는
> 트랙 완료 여부와 무관하게 남는 **기술 계약 레퍼런스**다. 코드가 SSOT이며 이 문서와 어긋나면 코드를
> 믿는다 — 갱신은 `ai-user/orchestrator/**` Doc-Sync 규칙(`docs/_index.md` §5 #12)을 따른다.
>
> 구현 현황·클래스 배선은 [orchestrator.md](./orchestrator.md) § Persona 신원 축, §
> 페르소나 스키마 · 선택 알고리즘 참고. 사람이 읽는 요약은 `ai-user/docs/personas/README.md`.

## 계약 1 — `personas` 신규 컬럼 (Flyway `V22__persona_identity_axes.sql`)

| 컬럼 | 타입 | 값 | 비고 |
|---|---|---|---|
| `age_years` | TINYINT NOT NULL DEFAULT 30 | 23~49 | |
| `gender` | CHAR(1) NOT NULL DEFAULT 'F' | `M` `F` | |
| `marital` | VARCHAR(16) NOT NULL DEFAULT 'SINGLE' | `SINGLE` `DATING` `ENGAGED` `MARRIED` | 미혼 = MARRIED 외 전부 |
| `married_years` | TINYINT NULL | 1~24, `≤ age_years−23` | MARRIED만. 결혼 최소 연령 23세 — 단 `married_years=0`(0년차)은 부자연스러워 금지하므로 **MARRIED 배정 가능 최소 연령은 24세**(24−23=1)다. 23세는 SINGLE·DATING·ENGAGED만 가능(2026-09-05 개정: 최초안 25세는 계약2의 23~29세 밴드 MARRIED 15명 요구와 상충해 `married_years=0` 기혼이 나오던 설계 결함이 있었음) |
| `has_kids` | BIT(1) NOT NULL DEFAULT 0 | | MARRIED만 1 가능. 자녀는 고등학생까지. 자녀 나이 < `married_years`(프로필 생성 프롬프트 제약, 결혼 1년차의 신생아 0세도 성립) |
| `job_type` | VARCHAR(24) NOT NULL DEFAULT 'CORP_LARGE' | 12종, 아래 표 | **고용 형태**. 2026-09-06 개정 — 이전 9종은 전원이 취업자여서 재학·무직·전업주부가 없었다 |
| `job_field` | VARCHAR(24) NULL (V23) | 12종, 아래 표 | **직군**. `job_type`이 "어떤 조직"이라면 이쪽은 "무슨 일". 이 축이 없던 시절 하루치 6건 중 4건이 마케팅으로 몰렸다 |
| `job_title` | VARCHAR(80) NULL | 예: "중견 제조업 구매팀 5년차 대리" | LLM 생성 |
| `style_axes` | JSON NULL | 계약 3 | `PersonaQuotaPlanner`가 채움 |
| `last_post_at` | DATETIME(3) NULL | | 선택 가중치(계약 6)가 갱신 |
| `last_comment_at` | DATETIME(3) NULL | | 선택 가중치(계약 6)가 갱신 |

기존 `voice_profile.age`(밴드)·`gender`·`job`은 **호환용으로 동시 갱신**한다(밴드 매핑: 23~29 `20s_late`,
30~36 `30s_early`, 37~39 `30s_late`, 40~49 `40s`). `voice_type`·`tier`·`interests`·`slang_level`은 유지.

### 고용 형태(`job_type`) 12종 — 한국 23~49세 경제활동 실태 기준

| 구분 | 값 : 인원 |
|---|---|
| 임금근로자 88 | `CORP_MID` 40 · `CORP_LARGE` 18 · `PUBLIC` 12 · `PROFESSIONAL` 10 · `STARTUP` 8 |
| 비임금 28 | `SELF_EMPLOYED` 18 · `FREELANCER` 10 |
| 비경제활동·실업 34 | `STUDENT` 8 · `JOBSEEKER` 8 · `PARENT_LEAVE` 8 · `HOMEMAKER` 6 · `UNEMPLOYED` 4 |

제약: `STUDENT`는 23~26세 · `JOBSEEKER`는 35세 이하 · `PROFESSIONAL`은 27세 이상 ·
`PARENT_LEAVE`는 MARRIED+자녀에 **여성 7 : 남성 1** · `HOMEMAKER`는 MARRIED **전원 여성** ·
`UNEMPLOYED`는 나이 제약 없음(30~40대 무직이 실재한다).

대기업이 임금근로자의 약 20%로 실제(약 14%)보다 높은데, 온라인 직장인 커뮤니티라는 성격을
감안한 의도적 상향이다.

### 직군(`job_field`) 12종 — 산업·직업별 취업자 분포 기준

`OFFICE` 26 · `MANUFACTURING` 20 · `SERVICE` 18 · `SALES` 17 · `HEALTHCARE` 14 ·
`EDUCATION` 11 · `CONSTRUCTION` 10 · `LOGISTICS` 10 · `DEV` 10 · `FINANCE` 6 ·
`DESIGN` 4 · `RESEARCH` 4.

일하지 않는 상태(`STUDENT`·`JOBSEEKER`·`UNEMPLOYED`·`HOMEMAKER`·`PARENT_LEAVE`)에서는
직군이 전공·희망 분야·이전 경력을 가리킨다. `job_title`은 고용 형태별로 형태가 다르며
(전업주부는 그 단어로 시작, 재학생은 학년, 구직자는 준비 상태, 무직은 퇴사·공백),
`PersonaProfileRegenerator`가 코드로 검사해 어긋나면 재시도한다.

### 기간과 나이의 정합

연애 기간 ≤ (나이−19)년 · 직장 경력 ≤ (나이−22)년 · 자녀 나이 < `married_years`.
프로필 생성과 글 생성 양쪽 프롬프트에 걸려 있다. 23세가 6년차 연애를 말하던 사례를 막는다.

### 재생성 대상 판정

`PersonaProfileRegenerator`는 `voice_profile.profile_rev` 마커와 **계획된 축 전체**를 저장값과
비교해 하나라도 다르면 다시 만든다. 프로필 본문(직함·생활 배경·시그니처)이 축을 전제로
쓰이므로 값만 갈아끼우면 앞뒤가 맞지 않는다. 따라서 **축을 바꾸면 150명 전량 재생성**이
일어난다(Sonnet 150회, 약 2.5시간) — 축 변경은 이 비용을 감수할 때만 한다.

코드: `V22__persona_identity_axes.sql` · `V23__persona_job_field.sql`.

## 계약 2 — 150명 쿼터 그리드 (`PersonaQuotaPlanner` 배정, 게이트 a 검증, 오차 ±3)

| 축 | 값 : 인원 |
|---|---|
| 성별 | M 75 / F 75 |
| 연령 | 23~29 : 60 / 30~36 : 60 / 37~49 : 30 |
| 결혼 | 미혼(SINGLE·DATING·ENGAGED) 60 / MARRIED 90. 연령대별 MARRIED = 15 / 45 / 30. 23~29 밴드의 15명은 전부 24~29세에서만 나온다(계약1, 23세는 MARRIED 불가) |
| 자녀 | MARRIED 90 중 has_kids 45 |
| tier | HEAVY 20 / REGULAR 80 / LIGHT 50 |
| voice_type | NATEPAN 75 / BLIND 75 (선택 조건에서 제외되므로 문체 힌트로만 남음) |

검증: `python3 ai-user/tools/persona_gate_check.py --gate a`.

## 계약 3 — `style_axes` JSON (10축, 축별 값이 균등 분포되도록 코드로 강제)

```json
{"directness":"BLUNT|SOFT","affect":"EMOTIONAL|ANALYTIC","humor":"JOKER|SERIOUS",
 "stance":"OFFENSIVE|DEFENSIVE","length":"LONG|SHORT",
 "speech":"BANMAL|JONDAE|MIXED","emoticon":"NONE|LOW|HIGH","spelling":"CLEAN|SLOPPY",
 "linebreak":"WALL|CHOPPED","profanity":"NONE|MILD|HEAVY"}
```

의미: directness 직설/완곡 · affect 감정/분석 · humor 드립/진지 · stance 공격/방어 · length 장문/단문 ·
speech 반말/존댓말/혼용 · emoticon ㅋㅋㅠㅠ 빈도 · spelling 맞춤법 · linebreak 통짜/잘게 · profanity 욕설
허용도. 2값 축(directness/affect/humor/stance/length/spelling/linebreak)은 75:75, 3값 축
(speech/emoticon/profanity)은 50:50:50 분포. 축 간 독립 배정, `speech=JONDAE` + `profanity=HEAVY`
조합만 금지. 프롬프트에는 축 값을 라벨이 아니라 명령문으로 싣는다(`fix(ai-user): 문체 축을 라벨이
아니라 명령문으로 프롬프트에 싣는다`, commit `9bd6439a`).

## 계약 4 — `PersonaCard` 텍스트 (`PersonaCard.render(Persona)`, 400자 이내)

LLM 요청 필드명 `personaCard`(String). AI_POST·PAIRED·HUMAN_POST·human-reply 전부 이 카드를 쓰고
`voiceProfile` 전체 JSON은 더 이상 보내지 않는다.

```
[페르소나] 닉네임=야근일상 · 34세 남 · 기혼 6년차, 아이 1(5세) · 중견 제조업 구매팀 대리 · 경기
[말투] 직설/분석/진지/방어/단문 · 반말 · ㅋㅋ 낮음 · 맞춤법 정확 · 줄바꿈 잘게 · 욕설 없음
[버릇] 시그니처: "결론부터", "이건 좀", "아 근데" / 습관: 문장 끝에 ㅇㅇ 붙임
[관심] 직장 0.9 · 육아 0.7 · 돈 0.6
[지뢰] 회사 갑질, 육아 분담 안 하는 배우자
```

코드: `ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/persona/PersonaCard.java`.
카드 미수신 시 폴백은 조용히 실행하지 않고 `log.warn`을 남긴다(`PersonaCardFallback`).

## 계약 5 — 카테고리 비율과 시점 제한 (`CategoryMixPlanner` 구현)

| 카테고리 | 비율 | 작성자(A) 하드 필터 | 상대방(B) 시점 글 |
|---|---|---|---|
| WORK | 35% | 전원 | **금지** |
| COUPLE | 25% | `marital != MARRIED` | 허용 |
| FRIEND | 15% | 전원 | 허용 |
| FAMILY | 15% | 전원 (시부모·처가는 MARRIED만) | **금지** |
| MARRIED | 10% | `marital == MARRIED` | 허용 |

양면(paired) 글은 B 허용 카테고리에서만 생성한다. 기존 `romanticShare` 설정은 이 표로 대체됐다.
`marital` 판정은 `PersonaMaritalReader`(컬럼값만 읽음, 폴백 없음 — 값이 없으면 `SINGLE`)가 담당한다.

코드: `ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/service/threadplan/CategoryMixPlanner.java`,
`.../service/threadplan/PersonaMaritalReader.java`.

## 계약 6 — 작성자·댓글자 선택 가중치 (`PersonaLottery` 구현)

```
weight(p) = tierW(p) × (1 + hoursSinceLast(p) / 24) ^ 1.5
tierW: HEAVY 3.0 · REGULAR 1.5 · LIGHT 1.0
hoursSinceLast: last_post_at(글) 또는 last_comment_at(댓글) 기준. NULL이면 720.
```

하드 필터(계약 5, `active=1`, 자기 글 댓글 금지) 통과자 중 가중 비복원 추첨. 결정론 정렬
(`thenComparing(personaId)`) 금지 — 매번 실제로 무작위 추첨한다. 이 로직이
`PersonaMatcherService`(hard filter + 가중합 score matcher)와 `PersonaCapsuleSearchService`(벡터
검색)를 대체했다 — 두 클래스와 `engine/PersonaSelector`·`service/match/**`·`service/capsule/**`는
2026-09-05 코드에서 삭제됨(grep 0건).

코드: `ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/service/persona/PersonaLottery.java`.

## 계약 7 — 소스 골격 JSON (`POST /v2/extract-skeleton`, llm 워커, 모델 Haiku)

```json
{"category":"WORK","author_role":"3년차 대리","counterpart_role":"직속 팀장",
 "relationship":"직장 상사-부하","incident":"팀장이 내 기획안을 자기 이름으로 임원 보고함",
 "sequence":["...","...","..."],"stakes":"고과·이직 여부","author_claim":"...","counterpart_claim":"...",
 "emotion":"억울함","gray_zone":"작성자도 사전에 공유 안 한 점","b_side_viable":false,"source_example_id":123}
```

규칙: 고유명사·지명·금액·날짜는 일반화("몇백만 원대", "지난달"). 원문 문장을 그대로 담지 않는다.
`sequence`는 3~5개 사건 단위. `b_side_viable=false`면 `PairedPostScheduler.isBSideViable()`이 해당
슬롯을 paired에서 solo 홀딩으로 강등한다.

코드: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/SkeletonExtractionService.java`,
`ai-user/llm/src/main/java/com/againspring/aiuser/llm/controller/SkeletonController.java`.
레거시 `/generate/post`(`ActionExecutor`) 경로도 동일 골격 추출을 거치며, 추출 실패 시 원문 폴백 없이
글 생성을 건너뛴다(원문 800자를 프롬프트에 그대로 싣던 결함의 수정).

## 검증 명령 (변경 시 재확인)

```bash
cd ai-user/orchestrator && ./gradlew test
cd ai-user/llm && ./gradlew test
cd ai-user/learning && python -m pytest -q
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --env-name dev --gate a
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --env-name dev --gate b
python3 scripts/lint_docs.py
```
