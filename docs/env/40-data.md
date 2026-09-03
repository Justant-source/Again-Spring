---
title: L4 Data — environment variables
last_updated: 2026-09-01
---

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

## 시크릿 보관 원칙 (범위 B)

- **env 부트스트랩만**: DB 접속 + vault 마스터키.
- **AS MariaDB `encrypted_secret`**: 마케팅과 무관한 앱 시크릿 (JWT/OAuth secret/메일/ASM 클라이언트 토큰 복사본/GitHub PAT 등). 마스터키=`AS_SECRET_MASTER_KEY`. 기동 시 `EncryptedSecretEnvironmentPostProcessor`가 Spring Environment에 주입.
- **ASM `credential` + `system_secret`**: 마케팅 플랫폼 계정·ASM 런타임 키. 마스터키=`ASM_CREDENTIAL_KEY`.
- 시딩: `scripts/seed_encrypted_secrets_from_env.py` (AS), ASM `scripts/seed_system_secrets_from_env.py`. 시딩 후 env에서 해당 키 **삭제**.
- GitHub PAT SSOT = AS `github.pat.<username>`. 평문 `.git-credentials` 금지 — `scripts/git-credential-as-vault` credential helper.

## 공통 DB / vault 부트스트랩

| 변수 | 사용처 | 예시 |
|---|---|---|
| `MARIADB_ROOT_PASSWORD` | MariaDB root 부트스트랩 | dev/prod 각자 설정 |
| `MARIADB_DATABASE` | DB 이름 | `againspring_dev`, `againspring` |
| `MARIADB_USER` | 앱 계정 | `againspring` |
| `MARIADB_PASSWORD` | prod 또는 현재 스택 DB 비밀번호 | 비밀값 (env에만) |
| `AS_SECRET_MASTER_KEY` | `encrypted_secret` AES-256-GCM 마스터키 | `openssl rand -base64 32` (32바이트) |
| `SEARCH_NGRAM_BACKFILL_ON_STARTUP` | 기동 시 `post_search_ngrams` 미적재 백필 (`againspring.search.ngram-backfill-on-startup`) | `true` |

### AS `encrypted_secret` vault 키 (env에 두지 않음)

| vault key | 주입 alias (예) |
|---|---|
| `jwt.secret` | `jwt.secret` / `JWT_SECRET` |
| `oauth.google.client_secret` | `oauth2.google.client-secret` |
| `oauth.kakao.client_secret` | `oauth2.kakao.client-secret` |
| `oauth.naver.client_secret` | `oauth2.naver.client-secret` |
| `mail.gmail_app_password` | `spring.mail.password` |
| `llm.anthropic_api_key` | `ANTHROPIC_API_KEY` (BE; LLM 컨테이너는 `.env.ai-user` 별도) |
| `ai_user.bot_password` | `AI_USER_BOT_PASSWORD` (dev) |
| `sync.dev_mariadb_password` | `DEV_MARIADB_PASSWORD` (prod sync) |
| `asm.api_token` / `asm.callback_token` | `ASM_API_TOKEN` / `ASM_CALLBACK_TOKEN` (AS→ASM 클라이언트 복사본; 권위본은 ASM `system_secret`) |
| `telegram.bot_token` / `telegram.chat_id` | `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` |
| `github.pat.<username>` | git credential helper only |

## Base LLM (`againspring-llm`)

| 변수 | 설명 | 기본값 |
|---|---|---|
| `LLM_PROVIDER` | backend LLM provider 선택 | `remote` |
| `LLM_WORKER_URL` | backend → `againspring-llm` URL | `http://againspring-llm:8090` |
| `LLM_ENABLED` | `false`면 RemoteLlmProvider가 501 거절 (server-dev L3) | `true` |
| `CLAUDE_BIN` | Claude CLI 바이너리 | `claude` |
| `CLAUDE_MODEL` | 기본 모델 | `claude-haiku-4-5-20251001` |
| `REPORT_LLM_MODEL` | 리포트 모델 | `claude-sonnet-5` |
| `CLAUDE_HOST_CONFIG_DIR` | `/root/.claude`로 마운트할 호스트 경로 | 환경별 실제 값 |
| `LLM_POOL_SIZE` | worker pool size | `100` |
| `LLM_QUEUE_CAPACITY` | queue size | `500` |
| `LLM_QUEUE_WAIT_TIMEOUT_MS` | queue wait timeout | `120000` (2026-08-15, 기존 `30000`) |
| `LLM_DEFAULT_TIMEOUT_MS` | Claude CLI 브리지의 요청 실행 timeout | `600000` |
| `LLM_REMOTE_DEFAULT_TIMEOUT_MS` | backend가 bridge에 전달하는 요청 timeout | `600000` |
| `LLM_REMOTE_READ_TIMEOUT_MS` | backend → bridge HTTP read timeout (실행 timeout보다 길어야 함) | `610000` |
| `LLM_PROCESS_TERMINATION_GRACE_MS` | 타임아웃·취소 뒤 CLI 프로세스 트리에 정상 종료를 허용하는 시간 | `2000` |
| `LLM_REMOTE_CONNECT_TIMEOUT_MS` | backend → bridge HTTP connect timeout | `10000` (2026-08-15, 기존 `5000`) |
| `ASM_REQUEST_TIMEOUT_MS` | backend → ASM 일반 요청 timeout | `30000` (2026-08-15, 기존 `10000`) |
| `ASM_STATS_REQUEST_TIMEOUT_MS` | backend → ASM 긴 읽기 (통계 collect · X inbox/outbound Playwright) | `300000` |

## 마케팅 (Again-Spring & ASM)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MARKETING_RENDER_PROFILE` | `marketing_fast` | WaggleBot 렌더 프로필 선택. `marketing_fast` = 현행 운영 (간편 레이아웃) / `marketing_v2` = 신규 v2 (BGM·SFX·전환·앱크롬제거·투표바). 기본값은 env, 잡 생성 시 `renderProfile` 필드로 개별 지정 가능 (Phase 3: 2026-08-23, 기준선 수집 중) |
| `MARKETING_X_TIMELINE_BASE_URL` | `https://api.fxtwitter.com` | `@againspring_net` 타임라인 읽기(페르소나 학습). 발행 아님. Spring `marketing.x.timeline-base-url` |
| `MARKETING_X_PERSONA_LEARN_MODEL` | `claude-sonnet-5` | Justant-Bot 페르소나 프로필 증류. Spring `marketing.x.persona-learn-model`. 선댓글 작문은 Haiku. 상세 `docs/shared/marketing/70-policy/justant-bot-x-ops.md` |

**2026-08-15 마케팅 파이프라인 안정화**: 시봄이 영상 생성 LLM 호출이 최대 600초까지 걸릴 수 있어
`LLM_QUEUE_WAIT_TIMEOUT_MS`를 4배 상향했다(30s는 호출 1건이 600초를 점유하는 상황에서 큐 포화 시
즉시 실패했다). `LLM_REMOTE_CONNECT_TIMEOUT_MS`·`ASM_REQUEST_TIMEOUT_MS`도 WSL 부하 상황을
감안해 상향했다. 위 값들은 `application.yml` **기본값**이며 `.env.prod`는 이미 별도로 올바른
런타임 값을 갖고 있었다(`b9a293c4`) — 이번 변경은 env 누락 시 안전망이다. **dev는 LLM 호출 자체가
`127.0.0.1:1`로 차단돼 있어(아래 prod 체크리스트 참조) 이 값들과 무관하다.**

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
| `AI_USER_STRUCTURED_GENERATION_FAILURE_ALERTS_ENABLED` | 구조화 생성 번들 실패 시 Telegram 알림 | `true` |
| `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_THRESHOLD` | PARSE_FAIL 이벤트 임계값 — N회 이상 초과 시 1회 알림 | `3` |
| `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_WINDOW_MINUTES` | PARSE_FAIL 계수 윈도우(분) | `30` |
| `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_COOLDOWN_MINUTES` | PARSE_FAIL 알림 쿨다운(분) | `360` |
| `AI_USER_BACKEND_URL` | orchestrator가 write할 backend. dev 전용 인스턴스는 `http://againspring-backend-dev:8080`, prod는 `http://againspring-backend-prod:8080` | `http://againspring-backend-prod:8080` |
| `AI_USER_ENABLED` | **하드 게이트**. false면 스케줄러와 tick이 바로 skip | `true` |
| `AI_USER_TICK_CRON` | 메인 tick cron | `0 */10 * * * *` |
| `AI_USER_DAILY_GLOBAL_CAP` | 일일 상한 fallback | `500` |
| `AI_USER_BOT_PASSWORD` | synthetic 계정 로그인용 | **vault** `ai_user.bot_password` (env 비권장) |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | 예약 글 게시 성공·최종 실패 알림 · Justant-Bot 선댓글/대댓글 게시 통보 | 기본은 host watchdog credential file(`~/.config/again-spring-watchdog/telegram.env`)에서 compose가 주입. Git에 커밋 금지. BE는 vault `telegram.bot_token` / `telegram.chat_id` |
| `TELEGRAM_ENV_FILE` | ai-user compose의 Telegram credential file 경로 override | 기본값=`/home/justant/.config/again-spring-watchdog/telegram.env`; 다른 host 경로일 때만 설정 |
| `AI_USER_SEED_ENABLED` | seed loader 활성화 | `true` |
| `AI_USER_REPETITION_THRESHOLD` | 반복 가드 임계값 | `0.45` |
| `AI_USER_PERSONA_TARGET` | admin 목표가 0일 때 fallback 총량 | `50` |
| `AI_USER_FORCE_ACTIVE` | 강제 활성 모드 | `false` |
| `AI_USER_SECONDARY_BACKEND_URL` | 보조 backend direct write | 기본 공란 |
| `AI_USER_FAMILY_PLAZA_ENABLED` | AI 생성에 FAMILY 광장 포함 여부 (false = OTHER로 흡수). 사용자 검색/글쓰기는 여전히 FAMILY 지원 | `false` |
| `PAIRED_POST_ENABLED` | 양면 사연(작성자+상대방) 생성. prod 기본 true | `true`(prod) / `false`(dev 휴면) |
| `PAIRED_POST_CRON` | paired posts cron (당일 부족분 보충) | `0 0 */2 * * *` |
| `PAIRED_POST_PAIRS` | 한 번의 스케줄 실행에서 생성할 최대 pair 수 | `3` |
| `PAIRED_POST_TARGET_SHARE` | 하루 AI 글 중 양면 사연 비율 | `0.20` |
| `PAIRED_POST_ROMANTIC_SHARE` | paired 글 내부에서 연인/부부 비율 (`FRIEND`는 나머지) | `0.80` |
| `PAIRED_POST_PARTNER_DELAY_MIN` | 작성자 공개 후 상대방 제출 최소 지연(분) | `10` |
| `PAIRED_POST_PARTNER_DELAY_MAX` | 상대방 제출 최대 지연(분) | `120` |
| `PAIRED_POST_PARTNER_DELAY_MEDIAN` | 상대방 지연 중앙값(분, 분포 편향) | `55` |
| `PAIRED_POST_PARTNER_PUBLISHER_ENABLED` | 지연 상대방 제출 워커 | `true` |
| `PAIRED_POST_PARTNER_PUBLISHER_CRON` | 상대방 제출 due tick | `0 */1 * * * *` |
| `PAIRED_POST_PARTNER_PUBLISH_BATCH_SIZE` | 상대방 제출 배치 크기 | `5` |
| `PAIRED_POST_AUTHOR_SLOT_FROM_HOUR` | 작성자 홀딩 슬롯 샘플 시작(KST hour) | `7` |
| `PAIRED_POST_AUTHOR_SLOT_TO_HOUR` | 작성자 홀딩 슬롯 샘플 끝(KST hour, exclusive-ish) | `23` |

중요:

- `AI_USER_ENABLED`는 더 이상 단순 로그 플래그가 아니다.
- DB `ai_user_generation_config.ai_user_kill_switch`는 2차 kill-switch로 계속 사용된다.
- 실제 운영 목표치는 `/api/admin/ai-user/generation-config`가 우선한다.

### AI-user LLM

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AI_USER_LLM_MODEL` | 댓글/대댓글 기본 모델 | `claude-haiku-4-5-20251001` |
| `AI_POST_CLAUDE_MODEL` | PLAN AI 글 묶음의 Claude(Sonnet) 모델 | `claude-sonnet-5` |
| `AI_POST_CODEX_MODEL` | PLAN AI 글 묶음의 Codex(Terra) 모델 | `gpt-5.6-terra` |
| `AI_INTERACTION_CLAUDE_MODEL` | 사람 글 계획·사람 반응 batch의 Claude(Haiku) 모델 | `claude-haiku-4-5-20251001` |
| `AI_INTERACTION_CODEX_MODEL` | 사람 글 계획·사람 반응 batch의 Codex(Luna) 모델 | `gpt-5.6-luna` |
| `CODEX_HOST_CONFIG_DIR` | Codex 로그인 세션을 컨테이너 `/root/.codex`로 마운트할 호스트 경로 | `/home/justant/.codex` |
| `ANTHROPIC_API_KEY` | direct API 경로용 키 (LLM 컨테이너 `.env.ai-user`) | 공란; BE는 vault `llm.anthropic_api_key` |
| `AI_USER_LLM_POOL_SIZE` | AI-user worker pool | `20` |
| `AI_USER_LLM_QUEUE_CAPACITY` | AI-user queue | `100` |
| `AI_USER_LLM_QUEUE_WAIT_TIMEOUT_MS` | queue wait timeout | `30000` |
| `AI_USER_LLM_DEFAULT_TIMEOUT_MS` | llm-ai-user worker 기본 timeout (`timeoutMs` 미전달 시). compose→`LLM_DEFAULT_TIMEOUT_MS`. 운영 SSOT는 `/admin/ai-user` `bundle_timeout_ms` | `600000` |
| `SELF_CRITIQUE_ENABLED` | 자기비평 루프 | `true` |
| `SELF_CRITIQUE_THRESHOLD` | pass 기준 | `5` |
| `SELF_CRITIQUE_EXTRA_CLICHES` | 추가 상투구 차단 | 공란 |
| `SELF_CRITIQUE_RARE_VOCAB_ENABLED` | 어휘이질(T5) detector — 다크 출시, 캘리브레이션 후 활성화 | `false` |
| `SELF_CRITIQUE_RARE_VOCAB_RATIO` | 희귀/문어체 어휘 비율 임계값 | `0.18` |
| `SELF_CRITIQUE_RARE_VOCAB_MIN_TOKENS` | 어휘이질 판정 최소 토큰 수 | `25` |
| `SELF_CRITIQUE_RARE_VOCAB_PENALTY` | 어휘이질 감점 폭 | `1` |
| `LLM_API_REFUSAL_RETRIES` | refusal 재시도 | `0` |
| `LLM_API_REFUSAL_FALLBACK_MODEL` | 재시도 소진 후 fallback | 공란 |
| `LLM_STRUCTURED_PROMPT_MODE` | 구조화 생성 시 `--json-schema` 플래그 대신 프롬프트에 스키마 주입 (기본 off) | `false` |
| `LLM_STRUCTURED_GENERATION_FAILURE_ALERTS_ENABLED` | 구조화 생성 번들 실패 시 Telegram 알림(워커) | `true` |
| `LLM_STRUCTURED_GENERATION_PARSE_FAIL_THRESHOLD` | PARSE_FAIL 이벤트 임계값(워커) — N회 이상 초과 시 1회 알림 | `3` |
| `LLM_STRUCTURED_GENERATION_PARSE_FAIL_WINDOW_MINUTES` | PARSE_FAIL 계수 윈도우(분)(워커) | `30` |
| `LLM_STRUCTURED_GENERATION_PARSE_FAIL_COOLDOWN_MINUTES` | PARSE_FAIL 알림 쿨다운(분)(워커) | `360` |

**구조화 프롬프트 모드** (`LLM_STRUCTURED_PROMPT_MODE`, 2026-08-21 신규):

- **OFF (기본값)**: 구조화 생성이 `--json-schema` 플래그를 사용. Claude Code CLI는 도구 정의를 로드하되 StructuredOutput만 활성화(`--disallowedTools BASH,READ,...` 명시 리스트). 토큰 오버헤드 약 18.8k.
- **ON**: `--json-schema` 플래그 제거, 대신 system prompt 끝에 Korean instruction + schema JSON을 텍스트 주입. CLI가 모든 도구를 차단(`--disallowedTools "*"`)하므로 토큰 절감: **~18.5k/call** (스키마 인라인 비용 ~350-400 토큰, net 절감 ~18.1k). 대신 **스키마 검증이 없으므로** 모델이 malformed JSON 반환 시 post+comments 번들 전체 손실 위험. 추출 전략은 lenient(`JsonExtractorUtil`: direct parse → strip ```json fences → substring first`{`/`[`to last`}`/`]`), 실패 시 예외.
- **A/B 테스트**: `scripts/llm-mode-ab.py`에서 두 모드 성능 비교 가능.

실측 (2026-08-21, AI-user PLAN 모드 포스트 생성):
- 입력 토큰: 22,739 → 4,242 (81.4% 절감)
- 출력 토큰: 7,714 → 307 (96% 절감)
- 소요 시간: 115s → 8.4s (92.7% 단축)
- 기본 작업자(`llm-worker`): 입력 기준 279 token (모든 도구 차단 방식)

PLAN 모드의 운영 설정 권위는 다음과 같이 분리한다.

- DB `ai_user_generation_config`: workload provider, pause/kill, 후보 풀·batch 상한·`hr_*`·**`bundle_timeout_ms`·`nightly_*`**. `/admin/ai-user` 저장 즉시 반영(캐시 없음).
- env/yml: CLI 경로, 위 모델 식별자, pool/queue, cron 및 배포 스위치. timeout env는 DB 부재 시 fallback.
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
| `AI_USER_THREAD_PLAN_BUNDLE_TIMEOUT_MS` | 번들형 구조화 생성 타임아웃 ms — **DB `bundle_timeout_ms` 부재/비정상 시만 fallback**. 운영 SSOT는 `/admin/ai-user` | `600000` |
| `AI_USER_THREAD_PLAN_MICRO_BATCH_ENABLED` | AI_POST 생성 시 4~6 persona micro-batch (false=레거시 mega-call) | `true` |
| `AI_USER_THREAD_PLAN_MICRO_BATCH_SIZE` | micro-batch당 댓글 persona 수 (런타임 4..6 clamp) | `5` |
| `AI_USER_THREAD_PLAN_PLAN_PERSONA_CAST_MAX` | 단일 LLM 요청에 넣는 persona cast 상한(셔플 후 cap). 넘기면 Claude 200K 토큰 한도 초과 위험 | `40` |
| `AI_USER_THREAD_PLAN_READY_MIN_TOP_LEVEL` | 품질 게이트 후 READY 최상위 하한 | `3` |
| `AI_USER_THREAD_PLAN_READY_MIN_ITEMS` | 품질 게이트 후 READY 전체 item 하한 | `6` |
| `AI_USER_THREAD_PLAN_STANCE_SHARE_MAX` | stance 단일 관점 최대 비율 | `0.80` |
| `AI_USER_HUMAN_REPLY_DELAY_MINUTES_MIN` | human reply 게시 지연 하한(분) | `1` |
| `AI_USER_HUMAN_REPLY_DELAY_MINUTES_MAX` | human reply 게시 지연 상한(분) | `30` |
| `AI_USER_HUMAN_REPLY_INBOX_TTL_DAYS` | inbox `observed_at` TTL(일). 초과 시 `CANCELLED`+`EXPIRED_TTL` | `7` |
| `AI_USER_HUMAN_REPLY_PLAN_TTL_DAYS` | `REQUESTED` plan `created_at` TTL(일). 초과 시 `EXPIRED`+`EXPIRED_TTL` | `7` |
| `AI_USER_HUMAN_REPLY_TTL_CLEANUP_ENABLED` | TTL 정리 cron 실행 gate (**기본 OFF**, admin force 가능) | `false` |
| `AI_USER_HUMAN_REPLY_CHUNK_SIZE` | human-reply LLM 호출당 interaction chunk (§16.7 / W6-B) | `20` |
| `AI_USER_HUMAN_REPLY_RESPONDERS_MIN` | interaction당 최소 AI 응답 수 (0=무응답 허용) | `0` |
| `AI_USER_HUMAN_REPLY_RESPONDERS_MAX` | interaction당 최대 AI 응답 수 | `3` |
| `AI_USER_HUMAN_REPLY_DISTINCT_PERSONAS_MAX` | post×human 대화에 참여 가능한 distinct persona 상한 | `3` |
| `AI_USER_HUMAN_REPLY_PER_PERSONA_MAX` | persona당 human-reply 상한 (동일 post×human) | `5` |
| `AI_USER_HUMAN_REPLY_PER_POST_MAX` | post×human human-reply 총상한 (3×5) | `15` |
| `AI_USER_HUMAN_REPLY_CANDIDATE_MAX` | LLM에 넘기는 candidateResponders 상한 (interested pool) | `8` |
| `AI_USER_MATCHER_AUTHOR_THRESHOLD` | WP3 작성자 매칭 점수 하한. 미만이면 신규 persona 자동 생성 경로 | `0.35` |
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

prod는 현재 `AI_USER_FORCE_ACTIVE=true`, `AI_USER_LLM_DEFAULT_TIMEOUT_MS=600000`, `AI_LEARNING_ENABLED=false`로
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
| `SYNC_CRON` | 24h full cron (5-field) | `30 5 * * *` |
| `SYNC_CONTENT_CRON` | 5분 콘텐츠 증분 cron | `*/5 * * * *` |
| `SYNC_TIMEZONE` | scheduler timezone | `Asia/Seoul` |
| `SYNC_BACKFILL_DAYS` | full 잡 증분 backfill window | `7` |
| `SYNC_CONTENT_LOOKBACK_MINUTES` | 콘텐츠 잡 lookback (겹침 허용) | `15` |
| `DEV_DB_NAME` | dev DB 이름 | `againspring_dev` |
| `AI_USER_DEV_ENABLED` | orchestrator-dev 하드 게이트 (기본 off, L3) | `false` |

- **5분 콘텐츠(T1+U1)**: `posts`, `vote_options`, `post_comments`, `votes`, `post_likes` + 참조 `users`(비식별)·`personas`
- **24h full**: 아래 전체 표
- D1: prod 우선 upsert (dev-only 행 삭제 안 함)

현재 sync(full) 테이블군:

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

- OAuth **client ID**는 env에 둘 수 있음. **client secret**은 AS `encrypted_secret`만.
- `APP_URL`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` — 비시크릿/식별자. `GMAIL_APP_PASSWORD` → vault `mail.gmail_app_password`.
- `ASM_BASE_URL`, `ASM_ENABLED`, `ASM_CALLBACK_*` 비시크릿 설정은 env. `ASM_API_TOKEN` / callback token → vault (ASM 권위본=`system_secret`).
- `ASM_CALLBACK_BASE_URL` — ASM이 종료 콜백·Telegram 재구동 버튼을 보낼 AS 주소.
  **prod는 `http://100.81.189.92:8091`**, dev는 `:8090`. `application.yml` 기본값은 `:8090`이라
  compose에 안 넣으면 prod 잡이 dev로 콜백되고 재구동이 `JOB_NOT_FOUND`가 된다
  (`marketing_job` #974, 2026-08-31). `docker-compose.prod.yml` `backend-prod`와
  `application-prod.yml`에 기본 `:8091`을 둔다.
- Instagram/Meta `app_id`·`app_secret`·`access_token`은 **env에 두지 않음** — ASM `instagram_reels` credential(AES-256-GCM)만. 상세 [`docs/shared/marketing/credentials.md`](../shared/marketing/40-data/credentials.md)
- ASM 런타임 (`ASM_BEARER_TOKEN`, `ASM_CALLBACK_TOKEN`, `WAGGLEBOT_API_KEY`, ASM `ANTHROPIC_API_KEY`) → ASM `system_secret`. ASM `.env`에는 `ASM_DATABASE_URL` + `ASM_CREDENTIAL_KEY`만.
- ASM 로컬 mp4 보존: `VIDEO_RETENTION_DAYS`(기본 30) · `VIDEO_RETENTION_POLL_INTERVAL_SECONDS`(기본 3600). AS env가 아님. 정책 [`docs/shared/marketing/youtube-shorts-strategy.md`](../shared/marketing/70-policy/youtube-shorts-strategy.md).
- `MARKETING_TELEGRAM_BUTTONS_ENABLED` — 마케팅 실패 알림 메시지에 인라인 버튼(재구동/무시) 표시 여부. 기본값 `false` (안전 장치).
  `true`일 때 Telegram 인라인 버튼 클릭 → ASM webhook → AS `/api/admin/marketing/jobs/redrive` API 호출. 
  상세: [`docs/shared/marketing/api.md`](../shared/marketing/50-api.md) §1.6 및 `TelegramNotifier`·`MarketingJobService.buildFailureAlertMarkup()`.
  사람이 버튼을 눌러야만 재구동되는 반자동 장치이므로 dev/prod 모두 활성화 가능(2026-08-20 dev 활성화).
  단, 재구동은 기존 admin regenerate와 동일하게 공유 ASM 인스턴스를 경유하므로 dev 잡 재구동도 실제 파이프라인을 태운다는 점은 동일.
- `ASM_X_THREAD_PUBLISH_TRIGGER_ENABLED` — `XThreadPublishTriggerScheduler` opt-in 게이트(기본 `false`).
  **X(`x_thread`)와 Instagram(`instagram_feed`) 24h 자동 발행을 함께 켠다.** prod만 `true`.
  `.env.prod`에만 `true`로 설정한다(dev는 절대 금지 — ASM이 dev/prod 공유 단일 인스턴스라 dev에서 켜면
  실계정에 자동 발행됨, 2026-07-31 사고). `docker-compose.prod.yml`의 `backend-prod` `environment:` 블록에
  반드시 wiring돼 있어야 한다 — 2026-08-01, `.env.prod`엔 `true`로 설정했지만 compose에 안 걸려 있어
  10시간+ 동안 조용히 `false`로 동작한 인시던트 발생(상세: `docs/shared/marketing/70-policy/x-thread-strategy.md` §6).
- `ASM_AUTO_PUBLISH_SINCE` — ISO-8601 Instant. 트리거 ON일 때 **이 시각 이후 생성된 글만** 24h 후
  자동 발행. 비어 있으면 fail-closed(스킵). 2026-08-02 백로그 폭주 이후 필수.
  예: `2026-08-02T08:43:52Z`. compose `backend-prod`에 wiring 필요.

- `NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION` · `NEXT_PUBLIC_NAVER_SITE_VERIFICATION` — 검색엔진 소유 확인
  메타태그 값(2026-08-29 신설). 비시크릿. 비어 있으면 태그를 아예 렌더하지 않는다(`app/layout.tsx`
  `metadata.verification`). Google Search Console / 네이버 서치어드바이저에서 발급받은 문자열을
  **prod에만** 넣는다 — dev는 `app/robots.ts`가 전체 색인을 차단하므로 등록 대상이 아니다.
  🔴 **빌드 인자로 넘겨야 한다.** `NEXT_PUBLIC_*`는 빌드 타임에 번들로 인라인되므로
  compose `environment:`에만 두면 항상 `undefined`가 된다. `docker-compose.{dev,prod}.yml`의
  frontend `build.args`와 `frontend/Dockerfile`의 `ARG`/`ENV` 양쪽에 wiring돼 있어야 하며,
  값 변경 시 프론트엔드 **재빌드**가 필요하다(재시작만으로는 반영 안 됨).
  `.env.prod`에 `GOOGLE_SITE_VERIFICATION` / `NAVER_SITE_VERIFICATION`로 넣는다.

  **2026-08-29 실제 구성** — 두 검색엔진이 서로 다른 방식으로 확인 중이다:
  - **구글 = DNS TXT**가 주 수단이다. Cloudflare에 `google-site-verification=0EMRUcCv…`와
    이전 토큰 `Lu1Xj8bo…` 두 개가 공존한다(구글은 일치하는 것 하나만 있으면 통과).
    `.env.prod`의 `GOOGLE_SITE_VERIFICATION`에도 DNS 토큰을 넣어 메타태그를 함께 내보내고
    있는데, 구글은 방식마다 토큰을 따로 발급하므로 **이 메타태그는 검증에 쓰이지 않을 수
    있다**. 무해하고 DNS가 사라질 경우의 예비책이라 남겨 둔다 — 메타태그만 보고
    "소유확인이 여기에 걸려 있다"고 판단하지 말 것. 권위는 DNS TXT다.
  - **네이버 = HTML 파일**이다(`frontend/public/naver79b8914327d3fa9c1bacd9df6b05b40b.html`).
    이 방식은 토큰을 발급하지 않으므로 `NAVER_SITE_VERIFICATION`은 비어 있는 것이 정상이다.
    🔴 파일을 지우면 소유확인이 풀린다(네이버가 주기적으로 재확인).

prod는 OAuth와 메일 관련 값을 모두 실제 값으로 채워야 한다.

## prod 필수 체크리스트

- `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`, `AS_SECRET_MASTER_KEY`
- `encrypted_secret` 시딩 완료 (jwt/oauth secrets/mail/asm tokens/github.pat.*)
- OAuth client ID env + secrets in vault
- `CLAUDE_HOST_CONFIG_DIR` 존재 + `claude` 로그인 완료
- `CODEX_HOST_CONFIG_DIR` 존재 + `codex` 로그인 완료
- `LLM_WORKER_URL=http://againspring-llm:8090`
- shared ai-user 사용 시 `.env.ai-user`의 prod/dev DB 자격 증명
- Git: `credential.helper` = `scripts/git-credential-as-vault` (평문 `.git-credentials` 없음)

## 변경 절차

1. `.env.*.example` 갱신
2. 호스트의 실제 `.env.*` 반영
3. 관련 compose 재기동
