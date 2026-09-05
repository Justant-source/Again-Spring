---
title: Orchestrator schema · persona corpus
last_updated: 2026-08-31
---

# Persona Setup

현재 AI-user 페르소나 코퍼스는 `ai-user/docs/personas/` 아래에 있다. compose는 이 트리를 `:rw`로 mount하므로 실행 중에도 파일이 바뀔 수 있다.

## 현재 저장소 스냅샷

- persona profile 디렉토리 수: `115`
- 마지막 번호: `ai-user-115`
- 관계 파일: `ai-user/docs/personas/profiles/relationships.yml`

target 값과 실제 디렉토리 수는 다를 수 있다.

- `AI_USER_PERSONA_TARGET`는 2026-09 persona-diversity-v4에서 제거됐다(참조 코드 0건인 죽은
  설정이었다 — compose 기본 50, `OrchestratorProperties.personaTarget` 기본 10으로 서로
  달랐고 `application.yml` 바인딩도 없었다). `AiUserSeedLoader`의 `PersonaFactory.ensureCount(...)`
  호출은 상수 `PERSONA_COUNT = 150`을 직접 참조한다(계약: `docs/_active/persona-diversity-v4.md`).
- 기존 디렉토리는 자동 삭제되지 않는다

## 디렉토리 구조

```text
ai-user/docs/personas/
├── README.md
├── archetypes.yml
├── community-codebook.md
├── voices.yml
├── _specsheet.md
└── profiles/
    ├── relationships.yml
    ├── ai-user-001/
    │   ├── README.md                 # 사람이 읽는 요약
    │   ├── profile.yml
    │   ├── voice.yml
    │   └── ...
    └── ai-user-115/
        └── ...
```

## `profile.yml` 실제 구조

샘플 기준 핵심 필드:

```yaml
id: <uuid>
email: ai-user-001@againspring.internal
nickname: 꿀강아지98
demographics:
  age_band: 40s
  gender: F
  region: 서울
  job: 주부
orientation:
  political: conservative
  political_strength: 0.7
  values: [가족중심, 안정성, 전통]
personality: ...
activity:
  tier: REGULAR
  daily_target: 6
  slang_level: 0.2
  voice: NATEPAN
  circadian: [...]
interests:
  COUPLE: 0.7
  MARRIED: 0.8
  FRIEND: 0.5
  FAMILY: 0.9
  WORK: 0.3
  OTHER: 0.2
bias_profile:
  COUPLE: -0.1
  ...
archetype_preferences:
  - couple_communication
```

현재 orchestrator가 실질적으로 많이 쓰는 값은 아래다.

- `activity.tier`
- `activity.daily_target`
- `activity.slang_level`
- `activity.voice`
- `interests`
- `archetype_preferences`

## `voice.yml` 실제 구조

샘플 기준:

```yaml
persona_id: <uuid>
nickname: 꿀강아지98
formality: casual
like_score: 0.65
vote_score: 0.50
voice_type: NATEPAN
age: 40s
political_orientation: conservative
political_strength: 0.7
general_style: |
  ...
post:
  style: ...
comment:
  style: ...
reply:
  style: ...
reactions:
  agree: [...]
  disagree: [...]
lexicon:
  signature_phrases: [...]
writing_quirks:
  features: ...
hot_buttons:
  triggers: [...]
```

현재 llm/orchestrator가 주로 기대하는 필드는 아래다.

- `voice_type`
- `formality`
- `like_score`
- `vote_score`
- `general_style`
- `post`, `comment`, `reply`
- `lexicon`, `writing_quirks`, `hot_buttons`

## runtime persistence

현재 runtime writeback은 persona tree가 아니라 DB 테이블을 쓴다.

- `persona_history_entries`: 최근 글/댓글 재주입, 반복 억제
- `persona_life_state`: `casual_streak`, `ongoing_situation`

`profiles/*/README.md`는 사람이 빠르게 성향을 확인하는 문서이고, growing history는 더 이상 이 트리에 쌓지 않는다.
남아 있는 `history/`·`life_state.json`은 legacy import 잔여물일 수 있으며 수정 대상이 아니다.

## relationships 파일

`profiles/relationships.yml`는 paired posts와 일부 관계형 설정의 입력이다.

현재 파일 특징:

- 실제 prod persona UUID 기준으로 작성돼 있다
- `COUPLE`, `MARRIAGE`, `FRIEND`, `FAMILY`, `COLLEAGUE` 등이 섞여 있다
- `PairedPostScheduler`는 여기서 `COUPLE`과 `MARRIAGE`만 읽는다

## 편집할 때 주의할 점

- exact count를 target에 맞춘다고 기존 프로필을 지우는 코드는 없다.
- runtime history/life state의 권위본은 DB다. 남아 있는 legacy 파일은 migration 잔여물일 수 있으므로 운영 근거로 쓰지 않는다.
- `voice_type`와 `interests`는 RAG source 선택, prompt guide, reaction heuristics에 모두 연결된다.


## Orchestrator Flyway

코드: `ai-user/orchestrator/src/main/resources/db/migration/` (18 files).

| 파일 | 경로 |
|---|---|
| `V10__persona_fact_assertions.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V10__persona_fact_assertions.sql` |
| `V11__persona_semantic_capsules.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V11__persona_semantic_capsules.sql` |
| `V12__persona_match_audits.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V12__persona_match_audits.sql` |
| `V13__ai_post_interested_personas.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V13__ai_post_interested_personas.sql` |
| `V14__human_inbox_attempt_ledger.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V14__human_inbox_attempt_ledger.sql` |
| `V15__thread_plan_item_human_author.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V15__thread_plan_item_human_author.sql` |
| `V16__create_scheduled_partner_answers.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V16__create_scheduled_partner_answers.sql` |
| `V17__create_llm_generation_gate.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V17__create_llm_generation_gate.sql` |
| `V18__create_daily_planner_retry_log.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V18__create_daily_planner_retry_log.sql` |
| `V19__widen_thread_plan_item_stance.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V19__widen_thread_plan_item_stance.sql` |
| `V1__create_persona_tables.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V1__create_persona_tables.sql` |
| `V3__create_persona_daily_quota.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V3__create_persona_daily_quota.sql` |
| `V4__create_post_analysis.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V4__create_post_analysis.sql` |
| `V5__move_persona_history_to_db.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V5__move_persona_history_to_db.sql` |
| `V6__create_thread_plan_tables.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V6__create_thread_plan_tables.sql` |
| `V7__create_scheduled_posts.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V7__create_scheduled_posts.sql` |
| `V8__add_stance_and_source_example_to_plan_items.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V8__add_stance_and_source_example_to_plan_items.sql` |
| `V9__persona_history_provenance.sql` | `ai-user/orchestrator/src/main/resources/db/migration/V9__persona_history_provenance.sql` |
