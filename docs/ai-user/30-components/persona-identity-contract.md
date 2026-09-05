---
title: Persona identity contract (persona-diversity-v4)
last_updated: 2026-09-05
---

# 페르소나 신원 축 설계 계약 — persona-diversity-v4

> 이 문서는 `persona-diversity-v4` 트랙(WP1~WP4)의 원 작업 지시 문서(gitignore 대상 경로에만 있어
> 유실 가능했던 WP 공용 계약 파일)에 담겼던 계약 1~7의 **git 추적 권위본**이다. 트랙 진행 상태·Phase 4
> prod 배포 절차는 `docs/_active/persona-diversity-v4.md`(트랙 완료 시 삭제 예정)를 보고, 이 문서는
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
| `job_type` | VARCHAR(24) NOT NULL DEFAULT 'CORP_LARGE' | 9종, 아래 표 | V22 SQL 주석의 "8종"은 오기 — `PersonaQuotaPlanner.assignJobTypes` 실측 기준 9종 |
| `job_title` | VARCHAR(80) NULL | 예: "중견 제조업 구매팀 5년차 대리" | LLM 생성 |
| `style_axes` | JSON NULL | 계약 3 | `PersonaQuotaPlanner`가 채움 |
| `last_post_at` | DATETIME(3) NULL | | 선택 가중치(계약 6)가 갱신 |
| `last_comment_at` | DATETIME(3) NULL | | 선택 가중치(계약 6)가 갱신 |

기존 `voice_profile.age`(밴드)·`gender`·`job`은 **호환용으로 동시 갱신**한다(밴드 매핑: 23~29 `20s_late`,
30~36 `30s_early`, 37~39 `30s_late`, 40~49 `40s`). `voice_type`·`tier`·`interests`·`slang_level`은 유지.

`job_type` 9종과 150명 쿼터: `CORP_LARGE` 30 · `CORP_MID` 25 · `STARTUP` 20 · `PUBLIC` 15 ·
`PROFESSIONAL` 15 · `SELF_EMPLOYED` 15 · `FREELANCER` 10 · `JOBSEEKER` 10 · `PARENT_LEAVE` 10.

코드: `ai-user/orchestrator/src/main/resources/db/migration/V22__persona_identity_axes.sql`.

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
