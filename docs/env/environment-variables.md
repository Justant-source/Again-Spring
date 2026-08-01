# 환경 변수

## Source of truth

- `env/.env.example`
- `env/.env.dev.example`
- `env/.env.prod.example`
- `env/.env.ai-user.example`
- `backend/src/main/resources/application*.yml`
- `ai-user/orchestrator/src/main/resources/application.yml`

`.env.dev`, `.env.prod`, `.env.ai-user`는 git에 커밋하지 않는다.

## 파일별 역할

| 파일 | 대상 |
|---|---|
| `.env.example` | 로컬 base 스택 |
| `.env.dev.example` | dev 웹/DB 스택 |
| `.env.prod.example` | prod 웹/DB 스택 |
| `.env.ai-user.example` | shared ai-user 스택 |

## 공통 DB / JWT

| 변수 | 사용처 | 예시 |
|---|---|---|
| `MARIADB_ROOT_PASSWORD` | MariaDB root 부트스트랩 | dev/prod 각자 설정 |
| `MARIADB_DATABASE` | DB 이름 | `againspring_dev`, `againspring` |
| `MARIADB_USER` | 앱 계정 | `againspring` |
| `MARIADB_PASSWORD` | prod 또는 현재 스택 DB 비밀번호 | 비밀값 |
| `DEV_MARIADB_PASSWORD` | shared sync가 쓰는 dev DB 비밀번호 | 비밀값 |
| `JWT_SECRET` | backend JWT 서명 키 | `openssl rand -base64 32` 생성 권장 |

## Base LLM (`againspring-llm`)

| 변수 | 설명 | 기본값 |
|---|---|---|
| `LLM_PROVIDER` | backend LLM provider 선택 | `remote` |
| `LLM_JURY_PROVIDER` | 배심원 provider | `remote` |
| `LLM_WORKER_URL` | backend → `againspring-llm` URL | `http://againspring-llm:8090` |
| `CLAUDE_BIN` | Claude CLI 바이너리 | `claude` |
| `CLAUDE_MODEL` | 기본 모델 | `claude-haiku-4-5-20251001` |
| `REPORT_LLM_MODEL` | 리포트 모델 | `claude-sonnet-4-6` |
| `CLAUDE_HOST_CONFIG_DIR` | `/root/.claude`로 마운트할 호스트 경로 | 환경별 실제 값 |
| `LLM_POOL_SIZE` | worker pool size | `100` |
| `LLM_QUEUE_CAPACITY` | queue size | `500` |
| `LLM_QUEUE_WAIT_TIMEOUT_MS` | queue wait timeout | `30000` |
| `LLM_DEFAULT_TIMEOUT_MS` | 요청 timeout | `120000` |

## dev/prod backend가 shared ai-user를 바라보는 변수

아래 값은 `backend-dev`, `backend-prod`에 모두 들어간다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `AI_LEARNING_URL` | `http://againspring-ai-learning:8099` | learning API |
| `AI_USER_LLM_URL` | `http://againspring-llm-ai-user:8092` | AI-user 생성 워커 |
| `AI_USER_ORCHESTRATOR_URL` | `http://againspring-ai-user-orchestrator:8096` | orchestrator admin trigger |

## Shared ai-user 런타임 (`.env.ai-user`)

### 오케스트레이터

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_USER_BACKEND_URL` | orchestrator가 write할 backend. dev 전용 인스턴스는 `http://againspring-backend-dev:8080`, prod는 `http://againspring-backend-prod:8080` | `http://againspring-backend-prod:8080` |
| `AI_USER_ENABLED` | **하드 게이트**. false면 스케줄러와 tick이 바로 skip | `true` |
| `AI_USER_TICK_CRON` | 메인 tick cron | `0 */10 * * * *` |
| `AI_USER_DAILY_GLOBAL_CAP` | 일일 상한 fallback | `500` |
| `AI_USER_BOT_PASSWORD` | synthetic 계정 로그인용 | 비밀값 |
| `AI_USER_SEED_ENABLED` | seed loader 활성화 | `true` |
| `AI_USER_REPETITION_THRESHOLD` | 반복 가드 임계값 | `0.45` |
| `AI_USER_PERSONA_TARGET` | admin 목표가 0일 때 fallback 총량 | `50` |
| `AI_USER_FORCE_ACTIVE` | 강제 활성 모드 | `false` |
| `AI_USER_SECONDARY_BACKEND_URL` | 보조 backend direct write | 기본 공란 |
| `PAIRED_POST_ENABLED` | legacy paired posts 활성화. 신규 PLAN 경로에서는 사용하지 않음 | `false` |
| `PAIRED_POST_CRON` | paired posts cron | `0 0 */2 * * *` |
| `PAIRED_POST_PAIRS` | 한 번의 스케줄 실행에서 생성할 최대 pair 수 | `3` |
| `PAIRED_POST_TARGET_SHARE` | 하루 synthetic 글 중 paired 글 최소 비율 | `0.15` |
| `PAIRED_POST_ROMANTIC_SHARE` | paired 글 내부에서 연인/부부 비율 (`FRIEND`는 나머지) | `0.80` |

중요:

- `AI_USER_ENABLED`는 더 이상 단순 로그 플래그가 아니다.
- DB `ai_user_runtime.enabled`는 2차 kill-switch로 계속 사용된다.
- 실제 운영 목표치는 `/api/admin/ai-user/generation-config`가 우선한다.

### AI-user LLM

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_USER_LLM_MODEL` | 댓글/대댓글 기본 모델 | `claude-haiku-4-5-20251001` |
| `AI_POST_CLAUDE_MODEL` | PLAN AI 글 묶음의 Claude(Sonnet) 모델 | `claude-sonnet-4-6` |
| `AI_POST_CODEX_MODEL` | PLAN AI 글 묶음의 Codex(Terra) 모델 | `gpt-5.6-terra` |
| `AI_INTERACTION_CLAUDE_MODEL` | 사람 글 계획·사람 반응 batch의 Claude(Haiku) 모델 | `claude-haiku-4-5-20251001` |
| `AI_INTERACTION_CODEX_MODEL` | 사람 글 계획·사람 반응 batch의 Codex(Luna) 모델 | `gpt-5.6-luna` |
| `CODEX_HOST_CONFIG_DIR` | Codex 로그인 세션을 컨테이너 `/root/.codex`로 마운트할 호스트 경로 | `/home/justant/.codex` |
| `ANTHROPIC_API_KEY` | direct API 경로용 키 | 공란 |
| `AI_USER_LLM_POOL_SIZE` | AI-user worker pool | `20` |
| `AI_USER_LLM_QUEUE_CAPACITY` | AI-user queue | `100` |
| `AI_USER_LLM_QUEUE_WAIT_TIMEOUT_MS` | queue wait timeout | `30000` |
| `AI_USER_LLM_DEFAULT_TIMEOUT_MS` | 생성 timeout | `120000` |
| `SELF_CRITIQUE_ENABLED` | 자기비평 루프 | `true` |
| `SELF_CRITIQUE_THRESHOLD` | pass 기준 | `5` |
| `SELF_CRITIQUE_EXTRA_CLICHES` | 추가 상투구 차단 | 공란 |
| `LLM_API_REFUSAL_RETRIES` | refusal 재시도 | `0` |
| `LLM_API_REFUSAL_FALLBACK_MODEL` | 재시도 소진 후 fallback | 공란 |

PLAN 모드의 운영 설정 권위는 다음과 같이 분리한다.

- DB `ai_user_generation_config`: `scheduler_mode`, workload provider, pause/kill switch, 후보 풀과 batch 상한. 관리자 API만 변경한다.
- env/yml: CLI 경로, 위 모델 식별자, pool/queue/timeout, cron 및 배포 게이트.
- yml provider 값은 DB 설정 행이 없을 때의 호환 fallback일 뿐이며, DB 값이 항상 우선한다.

### PLAN rollout gate

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_USER_THREAD_PLAN_ENABLED` | PLAN 생성 서비스 gate | `false` |
| `AI_USER_THREAD_PLAN_PUBLISHER_ENABLED` | due item 게시 gate | `false` |
| `AI_USER_HUMAN_REPLY_BATCH_ENABLED` | 30분 사람 interaction batch gate | `false` |
| `AI_USER_THREAD_PLAN_MAINTENANCE_ENABLED` | 만료/재분배 maintenance gate | `false` |
| `AI_USER_THREAD_PLAN_AI_POST_PROVIDER` | DB config 부재 시 AI 글 bundle provider | `CODEX` |
| `AI_USER_THREAD_PLAN_HUMAN_PROVIDER` | DB config 부재 시 사람 글/반응 provider | `CODEX` |
| `AI_USER_THREAD_PLAN_BUNDLE_TIMEOUT_MS` | 번들형 구조화 생성(글+최대 24후보) 타임아웃 ms | `240000` |
| `AI_USER_THREAD_PLAN_MICRO_BATCH_ENABLED` | AI_POST 생성 시 4~6 persona micro-batch (false=레거시 mega-call) | `true` |
| `AI_USER_THREAD_PLAN_MICRO_BATCH_SIZE` | micro-batch당 댓글 persona 수 (런타임 4..6 clamp) | `5` |
| `AI_USER_THREAD_PLAN_READY_MIN_TOP_LEVEL` | 품질 게이트 후 READY 최상위 하한 | `3` |
| `AI_USER_THREAD_PLAN_READY_MIN_ITEMS` | 품질 게이트 후 READY 전체 item 하한 | `6` |
| `AI_USER_THREAD_PLAN_STANCE_SHARE_MAX` | stance 단일 관점 최대 비율 | `0.80` |
| `AI_USER_HUMAN_REPLY_DELAY_MINUTES_MIN` | human reply 게시 지연 하한(분) | `1` |
| `AI_USER_HUMAN_REPLY_DELAY_MINUTES_MAX` | human reply 게시 지연 상한(분) | `30` |
| `AI_USER_HUMAN_REPLY_INBOX_TTL_DAYS` | inbox `observed_at` TTL(일). 초과 시 `CANCELLED`+`EXPIRED_TTL` | `7` |
| `AI_USER_HUMAN_REPLY_PLAN_TTL_DAYS` | `REQUESTED` plan `created_at` TTL(일). 초과 시 `EXPIRED`+`EXPIRED_TTL` | `7` |
| `AI_USER_HUMAN_REPLY_TTL_CLEANUP_ENABLED` | TTL 정리 cron 실행 gate (**기본 OFF**, admin force 가능) | `false` |
| `AI_USER_SCHEDULED_POST_PUBLISHER_ENABLED` | `ai_scheduled_posts` 예약글 발행 gate | `false` |
| `AI_USER_SCHEDULED_POST_PUBLISHER_CRON` | 예약글 발행 스케줄러 cron (5-field) | `0 * * * * *` |
| `AI_USER_SCHEDULED_POST_PUBLISH_BATCH_SIZE` | 발행 tick당 최대 처리 행 수 | `5` |
| `AI_USER_POST_SLOT_MIN_SPACING_MINUTES` | 예약글 발행 슬롯 간 최소 간격(분) | `45` |

이 gate들은 배포만으로 콘텐츠를 만들지 않도록 모두 기본 `false`다. 실제 provider 선택·pause·kill switch·후보 수·batch 상한은 관리자 API의 DB 설정이 권위다.

**2026-07-30 발견 / 2026-07-31 수정**: PLAN 모드의 postId(VARCHAR) 파싱 버그로 PLAN을 `scheduler_mode=LEGACY`로 되돌렸으나, 2026-07-31 postId만 String으로 바꿔 수정 완료(comment/reply ID는 실제 BIGINT라 그대로 둠). **prod는 2026-07-31부터 `scheduler_mode='PLAN'`으로 운영 중이다.**

**2026-07-31 추가**: PLAN 전환 후에도 `AiPostBundleService.generateAndPublish()`는 생성 즉시 발행이라 "새벽 배치=새벽 일괄 발행" 문제가 남아 있었다. `generateAndHold()` + `ai_scheduled_posts` + `ScheduledPostPublisher`(위 4개 env var)로 생성/발행을 분리했다 — 상세: `docs/ai-user/operations.md` §8.

또한 위 3개 gate(`AI_USER_THREAD_PLAN_ENABLED`/`PUBLISHER_ENABLED`/
`AI_USER_HUMAN_REPLY_BATCH_ENABLED`)는 `env/docker-compose.ai-user.yml`의
orchestrator `environment:` 블록에 반드시 배선되어야 한다 — compose 파일에 passthrough 항목 필수. 새 env var를 `application.yml`에 추가할 때는 compose 배선도 함께 확인할 것.

prod는 현재 `AI_USER_FORCE_ACTIVE=true`, `AI_USER_LLM_DEFAULT_TIMEOUT_MS=240000`, `AI_LEARNING_ENABLED=false`로
운영 중이다(새벽 압축배치용, 위 표의 기본값과 다름) — 새벽 압축배치 절차는
`docs/ai-user/operations.md` §8 참조.

### Learning

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_LEARNING_ENABLED` | scheduler 전체 enable | `true` |
| `AI_LEARNING_CRAWL_ENABLED` | crawl/strengthen/topic job 등록 여부 | `false` |
| `CRAWL_MIN_POPULARITY_PCT` | 크롤 ingest 시 배치 내 상대 popularity 하한 (0~1). 미만 POST·그 자식 COMMENT 폐기 | `0.50` |
| `AI_LEARNING_URL` | orchestrator가 learning을 호출할 주소 | `http://againspring-ai-learning:8099` |
| `LLM_AI_USER_URL` | learning이 AI-user LLM을 호출할 주소 | `http://againspring-llm-ai-user:8092` |

중요:

- `AI_LEARNING_ENABLED=false`면 scheduler 자체가 올라오지 않는다.
- `AI_LEARNING_CRAWL_ENABLED=false`면 일일 crawl/strengthen/topic job이 등록되지 않는다.
- 크롤 저장 전 `popularity_gate`가 지표·절대하한·상대 percentile을 검사한다. UNRANKED는 넣지 않는다.

### prod → dev sync

| 변수 | 설명 | 기본값 |
|---|---|---|
| `SYNC_CRON` | 5-field cron | `30 5 * * *` |
| `SYNC_TIMEZONE` | scheduler timezone | `Asia/Seoul` |
| `SYNC_BACKFILL_DAYS` | 증분 backfill window | `7` |
| `DEV_DB_NAME` | dev DB 이름 | `againspring_dev` |

현재 sync는 다음 테이블군을 upsert한다.

- `users`
- `posts`, `vote_options`, `post_comments`, `votes`, `post_likes`
- `personas`, `persona_relationships`, `persona_seen_posts`, `persona_action_log`
- `persona_history_entries`, `persona_life_state`, `persona_daily_quota`
- `ai_user_runtime`, `ai_user_generation_config`
- `ai_content_corrections`, `ai_global_rules`, `ai_prompt_template`, `system_setting`

`users`는 dev 반영 시 비식별화되고 로그인 가능한 상태를 유지하지 않는다.

### AI-user ML

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_USER_ML_BASE_URL` | ML 서비스 URL | `http://100.115.252.61:8201` |
| `AI_USER_ML_API_TOKEN` | ML bearer token | 예시값 |
| `AI_USER_ML_ENABLED` | Best-of-N reranking enable | `false` |
| `AI_USER_ML_ENABLED_COMMUNITIES` | 커뮤니티 제한 | 공란 |
| `AI_USER_ML_COLLECT` | negative corpus 수집만 enable | `false` |
| `AI_USER_ML_BEST_OF_N` | 초안 수 | `4` |
| `AI_USER_ML_TIMEOUT_MS` | timeout | `500` |

## OAuth / App URL / Mail / ASM

기존 규칙은 유지된다.

- `GOOGLE_*`, `KAKAO_*`, `NAVER_*`
- `APP_URL`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `GMAIL_APP_PASSWORD`
- `ASM_BASE_URL`, `ASM_API_TOKEN`, `ASM_ENABLED`, `ASM_CALLBACK_*`

prod는 OAuth와 메일 관련 값을 모두 실제 값으로 채워야 한다.

## prod 필수 체크리스트

- `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_*`, `KAKAO_*`, `NAVER_*`
- `GMAIL_APP_PASSWORD`
- `CLAUDE_HOST_CONFIG_DIR` 존재 + `claude` 로그인 완료
- `CODEX_HOST_CONFIG_DIR` 존재 + `codex` 로그인 완료
- `LLM_WORKER_URL=http://againspring-llm:8090`
- shared ai-user 사용 시 `.env.ai-user`의 prod/dev DB 자격 증명

## 변경 절차

1. `.env.*.example` 갱신
2. 호스트의 실제 `.env.*` 반영
3. 관련 compose 재기동
