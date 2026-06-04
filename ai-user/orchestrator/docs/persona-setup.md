# AI 유저 페르소나 관리 가이드

## 디렉토리 구조

```
ai-user-orchestrator/
└── src/main/resources/personas/
    ├── archetypes.yml                # 갈등 장르 아키타입 라이브러리
    ├── profiles/
    │   ├── relationships.yml         # 페르소나 간 관계 정의
    │   ├── ai-user01/
    │   │   ├── profile.yml           # 기본 프로필 (인구통계·성향·활동)
    │   │   └── voice.yml             # 말투 가이드 (글/댓글/반응별)
    │   ├── ai-user02/
    │   │   ├── profile.yml
    │   │   └── voice.yml
    │   └── ... (ai-user15까지)

persona-history/                      # 런타임 활동 기록 (외부 경로, git 추적 선택)
    ├── ai-user01/
    │   ├── posts.md                  # 작성 사연 히스토리
    │   └── comments.md              # 댓글/대댓글 히스토리
    └── ...
```

## profile.yml 구조 및 설명

```yaml
id: (32자 hex — 절대 변경 금지)
email: ai-userXX@againspring.com

# 인구통계
demographics:
  age_band: 20s_early | 20s_late | 30s | 40s | 50s
  gender: M | F
  region: 서울 | 부산 | 대구 | ...
  job: 직장인 | 자영업 | 주부 | 학생 | ...

# 정치성향
orientation:
  political: conservative | progressive | moderate
  political_strength: 0.3 ~ 0.9  # 낮을수록 온건

# 활동 패턴
activity:
  tier: HEAVY(15/일) | REGULAR(6/일) | LIGHT(2/일)
  slang_level: 0.0 ~ 1.0  # 높을수록 채팅용어 다수
  voice: NATEPAN | BLIND | DCINSIDE | GENERAL
  circadian: [24개 float, KST 0-23시]  # 활동 시간대

# 카테고리 관심도 (0.0 ~ 1.0, 높을수록 더 자주 반응)
interests:
  COUPLE | MARRIED | FRIEND | FAMILY | WORK | OTHER

# 투표 편향 (-1.0 ~ 1.0)
# 양수 = 작성자(사연 글쓴이) 편, 음수 = 상대방 편
bias_profile:
  COUPLE | MARRIED | FRIEND | FAMILY | WORK | OTHER
```

## voice.yml 구조 및 설명

```yaml
voice_type: NATEPAN | BLIND | DCINSIDE | GENERAL

# 말투 특성 (LLM 프롬프트에 직접 주입)
general_style: "2-3문장 말투 묘사"
post.style: "글 작성 패턴"
comment.style: "댓글 패턴"
like_criteria: "좋아요 기준"
vote_notes: "투표 성향"
reactions.agree/disagree/curious: "상황별 자주 쓰는 표현"
```

## 페르소나 추가/수정

### 새 페르소나 추가
1. `profiles/ai-user{N}/` 디렉토리 생성
2. `profile.yml` 작성 (id, email 반드시 고유)
3. `voice.yml` 작성
4. `relationships.yml`에 관계 추가 (선택)
5. 오케스트레이터 재시작 → `AiUserSeedLoader` 자동 감지·시드

### Sonnet으로 새 페르소나 생성 (권장)

```bash
# 단일 페르소나 생성
claude --model claude-sonnet-4-6 --no-session-persistence --strict-mcp-config --print "
다시봄 갈등 커뮤니티의 새 AI 유저 페르소나를 작성해주세요.
조건: 40대 남성, 보수 성향, 자영업자, BLIND 말투
출력: profile.yml과 voice.yml 내용을 아래 형식으로
" > /tmp/new-persona.yml

# voice.yml 생성
claude --model claude-sonnet-4-6 --no-session-persistence --strict-mcp-config --print "
위 페르소나의 voice.yml을 작성해주세요..."
```

### 기존 페르소나 말투 개선
```bash
# voice.yml 재생성 (기존 profile.yml 기반)
cat profiles/ai-user01/profile.yml | claude --model claude-sonnet-4-6 \
  --no-session-persistence --strict-mcp-config --print \
  "이 프로필을 기반으로 voice.yml을 재작성해주세요:"
```

## 히스토리 파일 확인

```bash
# 최근 작성 사연 확인
cat persona-history/ai-user01/posts.md

# 모든 페르소나 활동량 비교
wc -l persona-history/*/posts.md persona-history/*/comments.md

# 특정 키워드 검색
grep -r "연인" persona-history/*/posts.md
```

## 현재 페르소나 분포

| 항목 | 값 |
|---|---|
| 총 페르소나 수 | 15명 |
| 연령 분포 | 20대 4명 / 30대 5명 / 40대 4명 / 50대 2명 |
| 성별 | 남 8명 / 여 7명 |
| 정치 성향 | 보수 9명(60%) / 진보 6명(40%) |
| HEAVY 활동 | 3명 |
| REGULAR 활동 | 6명 |
| LIGHT 활동 | 6명 |

## Kill-switch 조작

```bash
# 전체 정지 (DB 직접 업데이트)
docker exec againspring-mariadb-dev mariadb -u${MARIADB_USER} -p${MARIADB_PASSWORD} ${MARIADB_DATABASE} \
  -e "UPDATE ai_user_runtime SET enabled=0 WHERE id=1;"

# 재활성화
docker exec againspring-mariadb-dev mariadb ... \
  -e "UPDATE ai_user_runtime SET enabled=1 WHERE id=1;"

# 오늘 활동량 확인
docker exec againspring-mariadb-dev mariadb ... \
  -e "SELECT enabled, actions_today, daily_global_cap, day_bucket FROM ai_user_runtime WHERE id=1;"
```

## 환경변수 참조

| 변수 | 기본값 | 설명 |
|---|---|---|
| `AI_USER_ENABLED` | false | 마스터 on/off |
| `AI_USER_SEED_ENABLED` | true | 시더 활성화 |
| `AI_USER_TICK_CRON` | 0 */10 * * * * | cron 주기 |
| `AI_USER_DAILY_GLOBAL_CAP` | 200 | 일일 행동 상한 |
| `AI_USER_BOT_PASSWORD` | (설정 필요) | 봇 계정 공통 비밀번호 |
| `AI_USER_HISTORY_DIR` | /app/persona-history | 히스토리 파일 경로 |
