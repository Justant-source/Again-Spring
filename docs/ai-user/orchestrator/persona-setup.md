# Persona Setup

현재 AI-user 페르소나 코퍼스는 `ai-user/docs/personas/` 아래에 있다. compose는 이 트리를 `:rw`로 mount하므로 실행 중에도 파일이 바뀔 수 있다.

## 현재 저장소 스냅샷

- persona profile 디렉토리 수: `115`
- 마지막 번호: `ai-user-115`
- 관계 파일: `ai-user/docs/personas/profiles/relationships.yml`

target 값과 실제 디렉토리 수는 다를 수 있다.

- 코드 기본 `AI_USER_PERSONA_TARGET = 10`
- dev/prod compose override `AI_USER_PERSONA_TARGET = 50`
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
    │   ├── profile.yml
    │   ├── voice.yml
    │   ├── life_state.json          # runtime 생성 가능
    │   └── history/
    │       ├── posts.md
    │       └── comments.md
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

## runtime writeback

compose가 `AI_USER_HISTORY_DIR=/app/personas/profiles`로 override하므로, 실행 중 생성되는 파일이 persona tree 안에 쌓인다.

- `history/posts.md`
- `history/comments.md`
- `life_state.json`

이 파일들은 단순 기록이 아니라 current code에서 재주입에 사용된다.

- recent output repetition guard
- CASUAL streak
- ongoing situation continuity

## relationships 파일

`profiles/relationships.yml`는 paired posts와 일부 관계형 설정의 입력이다.

현재 파일 특징:

- 실제 prod persona UUID 기준으로 작성돼 있다
- `COUPLE`, `MARRIAGE`, `FRIEND`, `FAMILY`, `COLLEAGUE` 등이 섞여 있다
- `PairedPostScheduler`는 여기서 `COUPLE`과 `MARRIAGE`만 읽는다

## 편집할 때 주의할 점

- exact count를 target에 맞춘다고 기존 프로필을 지우는 코드는 없다.
- history와 life_state는 runtime 산출물이라 단순 포맷 변경이 곧 동작 변경으로 이어진다.
- `voice_type`와 `interests`는 RAG source 선택, prompt guide, reaction heuristics에 모두 연결된다.
