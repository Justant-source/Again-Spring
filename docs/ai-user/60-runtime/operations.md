# AI User Operations

## 1. 기동

공통 ai-user 스택은 base/dev/prod 위에 따로 올린다.

```bash
cd env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
bash ./rebuild-stacks.sh ai-user
```

## 2. 현재 상태 확인

host에서 바로 가능한 체크:

```bash
curl http://localhost:8090/api/health
curl http://localhost:8091/api/health
curl http://localhost:8099/health
docker compose -f env/docker-compose.ai-user.yml --env-file env/.env.ai-user ps
```

container 내부 체크:

```bash
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
docker exec againspring-ai-learning python -c "import urllib.request; urllib.request.urlopen('http://localhost:8099/health')"
```

## 3. 실제 kill-switch

멈추는 범위가 다른 다섯 단계가 있다. 위에서부터 순서대로 확인한다 — 상위가 걸려 있으면 하위는 볼 필요 없다.

1. **`/admin/ai-user` 킬 스위치** (= `ai_user_generation_config.ai_user_kill_switch`) — 생성 + 모든 발행기(스레드/예약글/human-reply) + engagement(좋아요·투표) 전부 정지.
2. **`schedule_execution_paused`** — 발행만 정지(due item은 유지, 재개 후 만료 전 재분배). 생성은 계속된다.
3. **provider `OFF`** — 워크로드별(AI_POST/HUMAN_POST/HUMAN_INTERACTION/VOTE_LIKE) 생성만 중지. 기존 예약 item은 유지. **yml 기본값으로 되살아나지 않는다**(관리자가 명시로 켜야 복귀).
4. **`llm_generation_gate` = `HELD`** — 생성만 중지, 기존 콘텐츠 발행(PUBLISHING)은 계속된다. `POST /admin/trigger/llm-generation-hold`/`-resume`로 조작.
5. **env `AI_USER_ENABLED=false`** — 전부 정지(scheduler 자체가 skip). 재기동 필요.

**상태 확인은 한 곳**: `GET /api/admin/ai-user/effective-gates`(backend가 orchestrator `/admin/trigger/effective-gates`를 프록시, orchestrator 미응답 시 502) — 위 5단계 전부와 `nightly_snapshot_unrestored`(§8)를 포함한 게이트 17개 + `generationAllowed`/`publishingAllowed`/`reasons[]`를 한 번에 반환한다. 어드민 `/admin/ai-user` 화면 `EffectiveGatesPanel`이 이 값을 그대로 보여준다. 이 응답은 **전체 요약**이지 서비스별 정밀 시뮬레이션이 아니다 — 예를 들어 `PairedPostScheduler`는 `ai_user_kill_switch`/`schedule_execution_paused`를 직접 읽지 않는다(1·5단계로만 실제 정지).

신고가 `PENDING`인 경우에는 위 제어를 자동으로 변경하지 않는다. 관리자 `BLOCKED`, post private/delete, parent comment delete/block만 관련 미게시 item을 취소한다. 실제 사람/AI 작성 여부에 관계없이 notification은 backend의 정상 게시 경로로 보낸다.

### PLAN 상태 확인

운영 DB에서 plan/item/inbox/job 상태와 outbox 적체를 함께 확인한다. 아래 조회는 콘텐츠를 출력하지 않는다.

```sql
SELECT status, COUNT(*) FROM ai_thread_plans GROUP BY status;
SELECT status, COUNT(*) FROM ai_thread_plan_items GROUP BY status;
SELECT status, COUNT(*) FROM ai_human_interaction_inbox GROUP BY status;
SELECT state, provider, COUNT(*) FROM ai_llm_jobs GROUP BY state, provider;
SELECT status, COUNT(*) FROM ai_user_outbox GROUP BY status;
```

`FAILED` job은 provider 자동 전환으로 복구하지 않는다. 세션 인증, model mapping, schema/safety failure code를 해결한 뒤 관리자 action으로 같은 provider에 명시 재시도한다. LLM을 수동으로 호출해 콘텐츠를 만들어 DB에 넣지 않는다.

### 단일 shared runtime 보장

공통 스택이 healthy인 상태에서 예전 `againspring-*-prod` AI-user 컨테이너가 동시에 실행되면 같은 prod DB의 outbox와 예약 item을 두 consumer가 처리할 수 있다. 다음 이름의 구형 컨테이너만 중지하고, base LLM인 `againspring-llm-prod`는 중지하지 않는다.

```bash
docker stop againspring-ai-user-orchestrator-prod \
  againspring-llm-ai-user-prod \
  againspring-ai-learning-prod
```

새 stack의 health를 먼저 확인한다.

```bash
docker compose -f env/docker-compose.ai-user.yml --env-file env/.env.ai-user ps
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
curl --fail http://localhost:8099/health
```

prod DB에서 확인한다. `ai_user_runtime.enabled`는 V20에서 제거됐다(읽는 코드가 없던 dead kill-switch) — 실제 kill switch는 `ai_user_generation_config.ai_user_kill_switch`다.

```bash
docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring \
  -e "SELECT id, ai_user_kill_switch FROM ai_user_generation_config; SELECT id, daily_global_cap, actions_today, day_bucket FROM ai_user_runtime;"
```

## 4. 일일 cap

현재 코드는 `ai_user_generation_config`가 있으면 아래 합계로 cap을 자동 재계산한다.

- `target_posts + target_comments + target_replies + target_votes + target_likes`
- 위 합계 × `1.1` → `ai_user_runtime.daily_global_cap`
- 목표가 모두 0이면 `AI_USER_DAILY_GLOBAL_CAP`(코드 기본 `200` —
  `OrchestratorProperties.dailyGlobalCap`/`AiUserRuntime.dailyGlobalCap`) fallback — 이전에는
  이미 죽어있던 `AI_USER_PERSONA_TARGET * 20`으로 잘못 기술돼 있었다(2026-09 persona-diversity-v4
  WP4 정정, 병합 후 코드로 재확인 완료)

## 5. 로그 포인트

```bash
docker logs -f againspring-ai-user-orchestrator
docker logs -f againspring-llm-ai-user
docker logs -f againspring-ai-learning
docker logs -f againspring-prod-dev-sync
```

보통 확인할 메시지:

- orchestrator: `Tick complete`, `Daily cap 갱신`, `Content analysis`
- llm: generation timeout, sanitize, self critique
- learning: `Scheduler initialized`, `Daily crawl started`, `Topic synthesis completed`
- sync: `Daily sync start`, `Daily sync complete`

## 6. learning 운영 주의점

- `AI_LEARNING_ENABLED=false`면 scheduler가 시작되지 않는다.
- `AI_LEARNING_CRAWL_ENABLED=false`면 자체 일일 crawl/strengthen/topic 작업이 등록되지 않는다.
- API 엔드포인트(`/crawl/*`, `/strengthen/*`, `/topics/*`)는 컨테이너가 떠 있는 한 계속 응답한다.
- **크롤 소스 (Wave1-D)**: 일일 예산·수동 트리거 모두 `natepan`(1500) · `blind`(500) 둘뿐.
  `POST /crawl/{source}`에 그 외 이름을 넣으면 unknown source로 스킵된다.
  비활성 커뮤니티 크롤러 모듈은 코드베이스에서 제거됨.
  prod `example_bank` 비-natepan/blind 4,346건 삭제 완료 (2026-08-01, 백업 선행).

## 7. sync 운영 주의점

`prod-dev-sync`는 **1시간 콘텐츠 증분**(2026-09-03 5분→1시간, `f4867a68`) + **KST 일 1회 full**을 실행하며, 컨테이너 기동 시에도 full→content 순으로 1회 동기화한다.

- full cron: `SYNC_CRON` 기본 `30 5 * * *` / timezone `Asia/Seoul`
- content cron: `SYNC_CONTENT_CRON` 기본 `0 * * * *` / lookback `SYNC_CONTENT_LOOKBACK_MINUTES` 기본 75
- 실사용자 계정은 dev에서 비식별화되고 로그인 불가 상태로 반영된다.
- 실사용자 `posts`/`post_comments`는 제목·본문이 마스킹되고, `PRIVATE`/`DRAFT`/삭제된 글은 제외되며,
  `invite_token`은 (작성자 불문) 복사하지 않는다.
- 1시간 잡은 posts/comments/votes/likes(+vote_options)와 참조 users만 (T1+U1). **`personas`는
  content·full 모두 sync 대상이 아니다**(2026-09, persona-diversity-v4 WP1 — dev·prod 오케스트레이터가
  동일 시드로 각자 독립적으로 페르소나를 진화시키므로, prod→dev 단방향 동기화가 있으면 dev의 최신
  페르소나 갱신(voice_profile 등)이 다음 주기에 prod 예전 값으로 되돌아간다. posts/comments의
  author_id FK는 dev 자체 시드 personas 행으로 충족되어 personas를 안 옮겨도 깨지지 않는다.
  근거: `ai-user/sync/sync.py` 모듈 docstring·`SYNC_TABLES`/`CONTENT_TABLES`).
- full 잡은 아래 전체 표.
- D1: prod 우선 upsert. e2e 잔여는 cleanup.
- L3: `ai-user-orchestrator-dev`는 compose profile `ai-user-dev` + `AI_USER_DEV_ENABLED=false` (기본 미기동). dev backend는 LLM 네트워크 미연결.

현재 반영 범위:

- `users`, `posts`, `vote_options`, `post_comments`, `votes`, `post_likes`
- `persona_relationships`, `persona_seen_posts`, `persona_action_log`
- `persona_history_entries`, `persona_life_state`, `persona_daily_quota`
- `ai_content_corrections`, `ai_global_rules`

설정 테이블(`ai_user_runtime`·`ai_user_generation_config`·`system_setting`·`ai_prompt_template`)은
dev 튜닝을 prod 값으로 덮어써버리므로 sync 대상에서 제외한다(2026-09). `personas`도 위와 같은
이유로 별도 제외된다.

### 기존 dev 원문 정리

이 마스킹 규칙(§7 상단)은 2026-09 이후 sync부터 적용된다. 그 이전에 이미 dev DB로 복사된
실사용자 `posts`/`post_comments` 원문은 1회성 스크립트로 정리한다: `ai-user/tools/scrub_dev_real_user_content.py`

```bash
cd ai-user/sync && DEV_DB_HOST=127.0.0.1 DEV_DB_PORT=3309 DEV_DB_USER=againspring \
  DEV_DB_PASSWORD="$(grep ^DEV_MARIADB_PASSWORD= ../../env/.env.ai-user | cut -d= -f2-)" DEV_DB_NAME=againspring_dev \
  python3 ../tools/scrub_dev_real_user_content.py            # dry-run — 카운트만 출력, DB 미변경
# 카운트가 타당하면 --apply로 실제 반영 (명시 승인 후에만 실행)
```

`--apply` 없이 실행하면 dry-run이며 DB는 변경되지 않는다. 호스트가 dev가 아니면(호스트명에
`mariadb-dev`가 없고 `127.0.0.1`/`localhost`도 아니면) 스크립트가 거부한다.

## 8. 새벽 배치 — 예약글 파이프라인 (2026-07-31~)

낮 시간 토큰 소모를 막으면서 "새벽엔 준비만, 낮엔 사람처럼 하나씩 올라오는" 요구를
만족하려면 **생성 시점과 발행 시점을 분리**해야 한다. PLAN 모드의
`AiPostBundleService.generateAndPublish()`는 생성 즉시 글을 발행하므로 이것만으로는
부족하다 — 새벽 배치가 이걸 그대로 쓰면 그 순간 전부 발행돼 버린다.

그래서 별도 홀딩 테이블 `ai_scheduled_posts`를 뒀다. 새벽 배치는
`generateAndPublish` 대신 `AiPostBundleService.generateAndHold()`를 쓴다 — 같은
구조화 LLM 호출(글+댓글/대댓글 후보 한 번에) 뒤 backend에 보내지 않고
`ai_scheduled_posts`에 저장만 한다. `generateAndHold` 시점에 각 댓글/대댓글
후보 item에 `scheduledAt`(ISO Instant)을 `CandidateScheduleSupport.schedule`로
심어 두어, 관리자가 발행 전 릴리스 시각을 보고 수정할 수 있다.
`ScheduledPostPublisher`(매 분 cron)가 슬롯 도래 시 실제 글을 만들고, 저장해둔
후보를 `ThreadPlanGenerationService.persistResponse()`로 재생(replay)한다 —
`persistResponse`는 item에 `scheduledAt`이 있으면 그걸 쓰고, 없으면 기존
`schedule()` fallback. 발행 시점엔 LLM을 다시 부르지 않는다.

관리자 UI: `/admin/content` → **대기** 탭. BE
`/api/admin/content/scheduled-posts`가 orchestrator `/admin/scheduled-posts`를
프록시한다. `SCHEDULED`만 PATCH(제목·본문·슬롯·후보)/DELETE(`CANCELLED`).
슬롯만 바꾸고 items를 안 보내면 후보 시각은 delta-shift.
어드민 `ThreadEditorDialog`는 저장 시점에 로드 기준 글 발행 예정 시각 대비
delta를 계산해 댓글·대댓글 `atLocal`에 일괄 적용한다 (키보드·피커 공통).

공개된 글의 미게시 COMMENT/REPLY는 orchestrator `ai_thread_plan_items`에 남는다.
`GET/PATCH /api/admin/content/posts/{id}/thread`가 게시 댓글과 예약 후보를 병합해
보여 주고, `pendingItems`로 본문·페르소나·`scheduledAt` 수정·취소를 프록시한다
(`/admin/thread-plan-items`).

```
env/scripts/nightly-ai-user-batch.sh (호스트 crontab 05 3 * * *, KST)
  ├─ provider_* 스냅샷 (/admin/ai-user SSOT)
  ├─ provider(ai_post_bundle/human_post_plan/human_interaction) = CLAUDE (임시)
  ├─ SSOT: ai_user_generation_config ( /admin/ai-user 저장값 )
  │    N=target_posts, paired=ceil(N×nightly_paired_share), solo=N−paired
  │    slots=nightly_slot_from_hour~to_hour, spacing=nightly_slot_min_spacing_minutes
  │    (DB 조회 실패 시에만 NIGHTLY_BATCH_* env fallback)
  ├─ 목표 N=target_posts **저장**(시도 횟수가 아님). 상세 재시도·텔레그램은 아래 「새벽 fill」
  ├─ POST /admin/trigger/fill-nightly-scheduled-posts (있으면) 또는
  │    generate-scheduled-posts(solo) + paired-posts — 스크립트가 호출하는 경로
  │    ├─ ActivityCurve.sampleFutureInstants 로 발행 슬롯 샘플링
  │    ├─ generateAndHold() → ai_scheduled_posts SCHEDULED
  │    └─ PairedPostScheduler: Call1 hold(AUTHOR+phase1)→PUBLIC(T0, KST 02–06 밴)
  │       → Δ(10m–2h, median~50–60m) 후 Call2(PARTNER+phase2)→invite answer
  │       → partner 도착 시 미게시 cancel + phase2 both-context (phase1 게시분 보존)
  │       양면 부족분은 솔로로 채워 N을 맞춤
  ├─ 낮 동안 밀린 REQUESTED 스레드플랜(실사람 글 반응 등)도 이 창에서 같이 소진
  └─ provider = 스냅샷 복원 (강제 OFF 금지 — 관리자가 CLAUDE로 둔 값 유지)

ScheduledPostPublisher (cron AI_USER_SCHEDULED_POST_PUBLISHER_CRON, 기본 매 분)
  ├─ ScheduledPostLeaseService.claimDue()로 due 행 lease
  ├─ BackendBotClient.createPost — 진짜 글 생성
  ├─ candidates_json 재생 직전 `CandidateScheduleSupport.rescheduleFromPublishAt`
  │    (실제 발행 시각 기준 조밀한 early-window으로 재계산 — 홀드 슬롯만
  │     옮기고 `scheduledAt`을 안 옮긴 ops/SQL 드리프트 방어)
  ├─ solo: persistAndFinalize(full mins) / paired phase1: PHASE1_READY_MIN_* (2/1)
  └─ PUBLISHED로 갱신

PairedPostScheduler cron (PAIRED_POST_CRON, 기본 2시간) — 당일 양면 사연 부족분 보충
```

어드민 PATCH로 `scheduled_publish_at`을 옮기면 `shiftScheduledAts`로
`candidates_json`도 같이 밀린다. raw SQL로 슬롯만 바꾸면 댓글이 수 시간 뒤
슬롯에 남는 버그가 난다 — 발행 시 `rescheduleFromPublishAt`이 안전망이다.

`schedule_execution_paused`는 항상 `false`로 둬서 이미 만들어진 item의 게시는
낮 동안 계속된다.

스크립트 로그: `env/logs/nightly-ai-user-batch.log`(자체 타임스탬프) /
`env/logs/nightly-ai-user-batch.cron.log`(cron stdout/stderr).

### provider 스냅샷 영속화 및 stale 복원 (2026-09-03)

`nightly-ai-user-batch.sh`가 `provider_*`를 임시로 `CLAUDE`로 켜기 전 원래 값을 DB
`ai_provider_snapshot`(V21, singleton, `restored_at`) 테이블에 먼저 저장한다(기존엔 스크립트
로컬 변수에만 있어 스크립트가 죽으면 원래 값이 유실됐다). 정상 종료 시 스크립트 자신이
`updated_by='nightly-batch-restore'`로 복원하고 `restored_at`을 마크한다.

스크립트 시작 시점에도 `restored_at IS NULL`(전날 실행이 SIGKILL 등으로 죽어 복원 못 한 경우)을
먼저 확인해 원복 후 진행한다(`updated_by='nightly-batch-stale-restore'`). 이 자체 방어와 별개로
orchestrator `NightlyProviderStaleReconciler`가 **매시 7분** cron으로 같은 조건(`restored_at
IS NULL AND updated_by='nightly-batch'`)에 **3시간 유예**를 더해 재확인한다 — 스크립트가 다음 날
03:05까지 아예 재기동되지 않는 최악의 경우에도 최대 3시간 안에 provider가 `CLAUDE`에 고정된 채
방치되지 않는다. 복원 시 마찬가지로 `updated_by='nightly-batch-stale-restore'`를 남겨 감사 로그로
구분한다(정상 복원=`nightly-batch-restore`, 지연 복원=`nightly-batch-stale-restore`).

3시간 유예 창 안에서는 `GET /api/admin/ai-user/effective-gates`의 `nightly_snapshot_unrestored`
게이트(§3)가 `true`로 즉시(유예 없이) 노출되므로, 리컨실러가 돌기 전에도 수동 개입할 수 있다.

### 새벽 fill — claim 재시도 · LLM 상한 · 부족분 텔레그램

권위 스크립트: `env/scripts/nightly-ai-user-batch.sh`. 새 env 변수 없음 — 알림은 기존 `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`.

| 규칙 | 내용 |
|---|---|
| empty claim | 그 슬롯만 skip하고 끝내지 **않음**. 다른 **페르소나 / 광장 / 소스(blind↔natepan)** 로 재claim. 선호 소스·광장을 먼저 쓰지만 필수는 아님. Blind empty여도 Natepan 재시도 허용 |
| 같은 원본 | claim된 같은 example에 LLM/세이프가드 재시도 금지. release 후 **다른** 예시 |
| 양면 부족 | paired가 목표에 못 미치면 나머지를 **솔로**로 채워 N 도달을 시도 |
| LLM 상한 | claim 성공 후 생성이 들어간 호출만. 전체 새벽 fill(솔로+양면) **3 × target_posts**. empty claim은 카운트하지 않음. 수동 `generate-scheduled-posts`는 그 요청의 `count` 기준 3× |
| 로그 | attempted vs saved. 슬롯별 실패 이유(source, plaza, persona id, claim empty vs LLM/safety/serialize/persist) |
| Telegram | **saved < N일 때만**. N·saved·실패 원인 상세(메시지 길이 상한 ~3500자). 가득 채우면 보내지 않음 |

광장 재시도 순서: 페르소나 최상위 interest → COUPLE/MARRIED/FRIEND/[FAMILY/]WORK. OTHER로 그라운딩하지 않는 편. claim empty여도 프리스타일 폴백은 없다.

**FAMILY 광장 라우팅 (2026-08-22~)**: prod corpus에 가족 갈등 사연이 실제로 부족해(재고 21 vs MARRIED 411) 환경변수 `AI_USER_FAMILY_PLAZA_ENABLED`(기본 false)이 꺼져 있다. 꺼지면 페르소나의 최상위 interest가 FAMILY여도 OTHER로 재배치되고, 나이틀리 fill의 광장 재시도 순서에서도 FAMILY가 제외된다. 환경변수 하나로 뒤돌리기 가능 — 사용자 대면(검색 필터, 글쓰기 카테고리, 라벨, 관리자 선택지)은 변화 없음. 코퍼스가 늘어나면 true로 복구.

### ActivityCurve — KST 시간대 활동 가중치

`OrchestratorProperties.ThreadPlan.kstHourlyHumanWeights`(기존 필드, 이전엔
`EffectiveExposureCalculator`의 노출시간 가중에만 쓰이고 평평한 기본값이라 사실상
미사용)를 재사용한다. 22:00 KST를 1.0으로, 02:00~05:00 KST를 0.05~0.08로 둔
시간별 가중치 — 출퇴근(07~09시)·점심(12~13시) 소피크, 22시 본피크, 새벽 저활동
구조만 반영한다.

**주의: 이 곡선은 손으로 작성한 근사값이며 실측 한국 휴대폰 사용 데이터에 기반하지
않았다.** 이 프로젝트 어디에도 그런 데이터/분석은 존재하지 않는다(2026-07-31 확인).
나중에 실제 데이터를 확보하기 전까지는 추측치로 취급할 것.

`ActivityCurve`(`ai-user/orchestrator/.../service/threadplan/ActivityCurve.java`) 제공 함수:

- `sampleFutureInstants(from, to, count, weights, minSpacing, rng)` — **stratified inverse-CDF**
  (질량 버킷당 1개 + 버킷 내 jitter) + 최소 간격 보장(양방향 보정). iid 가중 샘플은
  저녁 피크 몰림 후 spacing이 `to`로 팩킹해 오전 슬롯이 비는 실패 모드가 있어
  2026-08-11에 교체. 새벽 배치의 글 슬롯 배정에 사용.
- `nextActiveHour(from, minWeight, weights)` — dead hour(가중치 < 임계값)에 걸리면
  다음 활성 시간대로 스냅. `ThreadPlanGenerationService.schedule()`의 댓글/대댓글
  경과-분 배열(기존 decay 구조 유지)이 새벽 트로프에 떨어지는 것 방지, 그리고
  `ThreadPlanPublisher`의 stampede 재분배에도 사용.
- `advanceByWeightedSeconds(from, targetSeconds, weights)` —
  `EffectiveExposureCalculator.weightedSeconds`의 역함수. 노출시간 예산 재계산용,
  dead-hour 회피와는 다른 목적(대기하며 누적 vs 즉시 건너뛰기)이라 혼동 금지.

### 2026-07-30~31 LEGACY → PLAN → 예약 파이프라인 전환 경위

- **2026-07-30**: PLAN 모드가 postId(VARCHAR) `Long.valueOf()` 파싱 버그로 깨져
  있어, LEGACY tick을 새벽에 압축 실행하는 임시방편을 썼다. 생성=발행이 분리되지
  않아 새 글 7개가 한꺼번에 몰리고 댓글이 하나도 안 붙는 문제로 이어졌다.
- **2026-07-31 오전**: postId 버그 수정(커밋 `1e9475cd`). **주의**: comment/reply
  ID(`post_comments.id`, `parent_comment_id`)는 실제 BIGINT라서 그쪽
  `Long.valueOf()`는 원래도 옳다 — postId만 String으로 고쳐야 하며 comment ID까지
  바꾸면 새 버그가 생긴다.
- **2026-07-31 낮**: PLAN 전환 후에도 `generateAndPublish()`는 즉시 발행이라 여전히
  "새벽에 만들면 새벽에 올라옴"이었다(사용자가 다시 발견). `generateAndHold` +
  `ai_scheduled_posts` + `ScheduledPostPublisher`로 생성/발행을 실제로 분리.
- **구현 중 발견한 추가 버그 2건**:
  1. `ScheduledPostPublisher`에 `claimDue`/`completePosted`/`releaseFailed`를
     `@Transactional`로 선언하고 같은 클래스 안에서 self-invocation으로 호출 —
     Spring 프록시 기반 AOP는 self-invocation을 가로채지 못해 트랜잭션이 조용히
     적용 안 됨(`lockDueItems`의 PESSIMISTIC_WRITE가 "no transaction in progress"로
     매 분 실패). `ThreadPlanPublisher`가 이미 lease 메서드를 별도 빈
     (`ThreadPlanItemLeaseService`)으로 분리해둔 것과 동일한 이유 — 새 코드도
     `ScheduledPostLeaseService`로 분리해 해결.
  2. 새벽 배치 트리거가 `category`를 빈 문자열/null로 넘겨 backend
     `createPost`가 `VALIDATION_ERROR`로 거부 — `ActionExecutor.topCategory()`와
     동일한 로직(persona 관심사 최고값, 없으면 "OTHER")으로 채우도록 수정.
- 실제 스모크 테스트로 생성→홀드→(강제 due 처리)→발행→댓글 재생까지 전 구간 확인
  완료(2026-07-31).

### 기존 글 재배치 (WO-RETIME-01, 2026-07-31)

전환 전 LEGACY 압축배치로 몰려 올라온 글 8개를 `env/scripts/retime-nightly-batch-posts.py`로
재배치했다(dry-run 기본, `--apply`로 실행, SQL은 `/tmp/retime-posts.sql`에 저장 —
`backdate-timeline.py`와 동일한 관례). 이미 지난 슬롯 배정 글은 `created_at`
delta-shift(글 삭제 없음), 아직 안 지난 슬롯 글은 `ai_scheduled_posts`로 이관 후
원본 삭제(발행은 `ScheduledPostPublisher`가 나중에 담당).

**실사람 데이터 제약**: 8개 중 2개 글에 실사람의 좋아요 1건·투표 1건이 섞여 있었다
(전부 봇으로 착각하면 안 됨). 이 2개는 반드시 "이미 지난 슬롯"으로만 배정해
글을 삭제하지 않고, 좋아요/투표 행의 시각도 글과 같은 delta만큼 같이 옮겼다
(`backdate-timeline.py`가 이미 쓰는 기법과 동일 — 콘텐츠·행위자는 그대로, 시각만
내부 일관성을 위해 이동).

**주의(재발 방지)**: 재배치 스크립트가 delta-shift한 댓글 결과를 검증할 때, 사전
조사 리포트가 알려준 "원래 시각"을 곧이곧대로 믿지 말 것 — 한 번은 조사 에이전트가
KST라고 보고한 시각이 실제로는 raw UTC 저장값을 그대로 옮긴 것이라(라벨링 오류)
delta 적용 결과가 이상해 보인 적이 있다. delta-shift 자체의 산술은 항상
검증 가능하다(같은 delta를 post/comment에 동일 적용하면 상대 간격은 반드시
보존된다) — 결과가 이상하면 델타 계산이 아니라 "이전 값이 무엇이었는지"에 대한
가정을 먼저 의심할 것. 또한 `mariadb -B`(batch 모드) 출력은 백슬래시/탭/개행을
자체적으로 이스케이프하므로, JSON 컬럼을 다시 읽어 검증할 땐 `--raw` 없이 조회한
결과를 그대로 `json.loads`하면 가짜 파싱 실패가 난다 — 반드시 `--raw`를 붙일 것.

### 번들 생성 지연 및 구성 최적화

실측 결과 글+최대 24개 후보를 한 번에 LLM 요청하는 구조화 생성이 5~10분 이상 걸릴
수 있음이 확인됐다. 대응책:

1. 타임아웃 SSOT: `/admin/ai-user` → `ai_user_generation_config.bundle_timeout_ms`
   (기본 600000ms=600초, 60~900초). **설정 저장 즉시** orchestrator `GenerationConfigSupport`가
   DB를 재조회해 solo/paired/human-reply 구조화 호출의 `timeoutMs`에 넣는다.
   env `AI_USER_THREAD_PLAN_BUNDLE_TIMEOUT_MS` / `AI_USER_LLM_DEFAULT_TIMEOUT_MS`는 DB 부재·비정상 시 fallback.
2. 후보 풀 크기: `ai_user_generation_config.candidate_pool_size`를 24보다 작게
   (16 권장: 최상위 14 + 대댓글 2)으로 설정하면 생성 속도 개선. 허용 범위 8~30.
3. 맞춤법 LLM은 오탈자 휴리스틱(`됬` 등)이 있을 때만 돈다. 교정 실패·줄바꿈 변경은
   hold를 버리지 않고 생성 원문을 유지한다(2026-08-18). SelfCritique 재시도는 원본
   thread-plan 프롬프트를 다시 붙이지 않는다.
4. micro-batch는 활성 페르소나 전체를 5명씩 돌리지 않는다. matcher 상위
   `max(batchSize, ready-min-items)+batchSize`명만 넣고(기본 11), 댓글이 READY 하한(기본 6)에 닿으면
   후속 HUMAN_POST를 생략한다(2026-08-18). 호출 횟수 SSOT: `docs/ai-user/70-policy/llm-call-budget.md`.

## 9. 트러블슈팅

### 글이 하나도 안 올라올 때

- `.env.ai-user`의 `AI_USER_ENABLED=true`인지 먼저 확인
- PLAN 모드라면 `ai_user_generation_config.provider_*`가 낮에는 `OFF`가 정상이다(§8) —
  버그가 아니라 새벽 배치 설계다. 새 글이 전혀 없어야 정상인 건 아니고, 새 LLM
  job만 안 만들 뿐 이미 예약된 item 게시는 계속된다.
- `AI_USER_SCHEDULED_POST_PUBLISHER_ENABLED=true`인지 확인 — false면 `ai_scheduled_posts`에
  생성은 계속되지만 아무것도 발행되지 않는다
- `ai_scheduled_posts` 상태 분포 확인: `SELECT status, COUNT(*) FROM ai_scheduled_posts GROUP BY status;`
  — `PUBLISHING` 상태가 `lease_until`을 훨씬 지나서도 남아 있으면 발행 중 예외로
  lease가 안 풀린 것, orchestrator 로그에서 `Scheduled post publish failed` 검색
- orchestrator 로그에 매분 `Create post failed: ... CRISIS_DETECTED`가 찍히면
  **구버전 BE**가 사연 본문 LEVEL1(피해자·소송 등)을 차단하던 회귀다.
  광장형 정책상 차단하면 안 된다 — `PostComposeService`는 감지·관제만 하고 게시한다.
  (BACKEND_WRITE_FAILED 재시도는 3회로 캡; 그 이상이면 `FAILED`로 내려 큐를 막지 않는다)
- `ai_user_generation_config.ai_user_kill_switch = 0`인지 확인
- orchestrator 로그에 `Daily global cap reached`가 있는지 확인
- `GET /admin/trigger/llm-generation-status`의 `reason`이 `auto:llm-auth-down: ...`으로 시작하면 `LlmAvailabilityGate`가 워커 provider `AUTH_DOWN`을 감지해 자동 hold한 것이다 — 워커 CLI 세션(`claude auth login`) 복구 후 최대 5분(cron 주기) 이내 자동 resume되며, `reason`이 `auto:`로 시작하지 않으면 사람이 건 수동 hold이므로 `llm-generation-resume`을 직접 호출해야 한다.

### learning이 예상치 않게 crawl할 때

- `AI_LEARNING_CRAWL_ENABLED=true`인지 확인
- 수동 실행이 아니라면 scheduler 로그에 등록 시각이 찍혔는지 확인

### source claim / 풀 고갈 (2026-08-05, 파이프라인 안정화 2026-08-22)

일일 crawl **budget은 그대로**(natepan 1500 · blind 500). claim API가 budget을 바꾸지 않는다.

**한 번의** `claim-popular-source`는 요청한 source만 본다. 새벽 배치는 empty면 **Blind↔Natepan·다른 광장·다른 페르소나**로 다시 claim한다. Blind empty를 Natepan으로 바꾸는 대체를 **금지하지 않는다**.

원인 후보: 14→30일 창에 미사용 `popularity_pct` POST 부족 · 같은 `source_url`이
이미 posts/예약에 소진 · soft/COMMITTED 예약 과다 · 광장 스코프가 좁은데 해당 plaza 코호트가 비어 있음.

saved < N이면 Telegram(기존 TELEGRAM_*). 로그의 attempted/saved·슬롯 이유를 본다.

**2026-08-22 소스 재고 파이프라인 안정화**: 제목 추출 버그부터 광장 흡수까지 여섯 건의 수정이 nightly fill의 근본 원인을 순차 제거했다. 상세: [learning.md 광장 분류 개선 이력](../30-components/learning.md#광장-분류-개선-이력-2026-08-22-소스-재고-파이프라인-안정화).

2026-08-10: claim/crawl은 **source_url 동시성 가드**를 쓴다. 과거 이중 INSERT된
형제 row가 남아 있어도 claim 가족이 한 번만 잡힌다. 신규 이중 INSERT는
`GET_LOCK(ai_learning_crawl_ingest:*)`로 막는다.

twin 거절(`StoryTwinGuard`) 로그: `AI post rejected as story twin` — soft-reserve는 lifecycle release 경로가 회수해야 한다.

### 크롤이 조용히 멈췄을 때 (2026-06-24~07-30, 36일 무크롤 인시던트)

`AI_LEARNING_CRAWL_ENABLED=false`가 실수로 켜진 채 36일간 방치된 사고가 있었다.
`GET /crawl/log`는 능동 조회해야만 상태를 알 수 있어 아무도 눈치채지 못했다 —
**"조용히 멈출 수 있다"는 것 자체가 이 시스템의 구조적 위험**이다.

- 1차 확인: `.env.ai-user`의 `AI_LEARNING_CRAWL_ENABLED=true`인지
- 2차 확인: admin 대시보드의 크롤 신선도 배지(`GET /api/admin/crawl-status`,
  WO-CRAWL-01) — 24시간 내 성공 크롤이 0건이면 `stale` 경고가 뜬다. 이 배지가
  재발 방지 장치이므로, 배지 자체가 비정상(조회 오류)이면 그것부터 조사할 것
- natepan이 SUCCESS/FAILED를 같은 시각에 동시 기록하면 스케줄러 중복
  초기화(`init_scheduler()` 2회 호출 → 동시 크롤 → DB lock timeout 1205)
  의심 — `scheduler.py`의 `_scheduler` 싱글턴 가드가 이미 이를 막고 있으니,
  재발하면 그 가드가 우회됐는지부터 본다
- **2026-08-11**: uvicorn `--workers 2`면 worker마다 lifespan이 떠 스케줄러가 이중
  등록되고, 크롤 중 health 지연 → `ops-watchdog` restart → 그날 SUCCESS 유실.
  대응: `--workers 1` + 크롤 마커/`KST 02–03` 동안 ai-learning 재시작 생략
  (`docs/env/watchdog.md`, `app/crawl_guard.py`). 유실 시 수동 보충은
  `POST /crawl/natepan?limit=1500` · `POST /crawl/blind?limit=500`
- 후속 과제(WO-CRAWL-01 미착수분): 텔레그램 하트비트 알림 — 배지는 "들어가서
  봐야" 아는 수단이라 재발 가능성이 완전히 닫히지 않았다

### host에서 `localhost:8096`이 안 열릴 때

- compose 설계상 정상이다. orchestrator는 외부 공개 포트가 없다.

### dev에서 실사용자 로그인이 안 될 때

- 의도된 동작이다. prod mirrored user는 비식별화 + 비활성 상태로 저장된다.

### dev에서 ai_user_outbox "Unknown column" 에러

dev backend는 `ddl-auto: update` 모드이므로 JPA 엔티티에 선언되지 않은 컬럼은
자동 생성되지 않는다. `ai_user_outbox` 테이블이 backend 쓰기 전용 컬럼만 가지고
있으면 orchestrator(오케스트레이터)가 소비 전용 컬럼(published_at, lease_owner,
lease_until, attempt_count, last_error_code)을 찾지 못해 오류 난다.

**해결책**: prod의 `SHOW CREATE TABLE ai_user_outbox`와 비교해서 누락된 컬럼을
dev에 수동으로 `ALTER TABLE` 추가. 또한 `ai_user_generation_config` 테이블에서
`provider_ai_post_bundle`, `provider_human_post_plan`, `provider_human_interaction`
값이 유효한지 확인 — 유효값은 `CLAUDE`/`CODEX`/`OFF`뿐이다(`CLI`/`API` 아님).

### prod에서 `ai_user_generation_config` "Unknown column" 에러 (2026-07-31 실제 발생)

위 dev 케이스와 같은 종류의 문제가 prod에서도 실제로 터졌다: JPA 엔티티
(`AiUserGenerationConfig.java`)에 `provider_vote_like` 필드가 추가됐는데 대응
마이그레이션(`V90__ai_user_config_plan_only.sql`)이 아직 적용되지 않은 상태로
배포되면서, `ThreadPlanGenerationScheduler`를 포함해 이 테이블을 읽는 **모든**
스케줄러가 15초마다 예외를 던지며 멈췄다(orchestrator 전체 컨텐츠 파이프라인
정지). `V90`을 수동 적용(`docker exec ... mariadb < V90__*.sql`, idempotent라
안전)해서 해결.

**교훈**: 이 테이블은 backend가 소유하고 orchestrator는 읽기 전용으로 매핑한다
(§3 참조). 엔티티에 컬럼을 추가하는 커밋과 그 컬럼을 만드는 마이그레이션은
분리될 수 있지만, **마이그레이션이 실제로 적용(=backend 재시작으로 Flyway 실행,
또는 수동 적용)되기 전까지는 새 필드를 참조하는 어떤 쿼리도 prod에서 실행하면
안 된다.** 여러 세션이 동시에 이 저장소를 건드릴 때 특히 위험 — 한쪽이 엔티티만
먼저 커밋하면 다른 쪽 코드(이미 배포돼 있던 스케줄러 포함)가 즉시 깨진다.

### 댓글/대댓글 좋아요·조회수가 안 붙을 때

`PlanEngagementDispatcher`(§8, 5분 cron) 관련. `AI_USER_ENGAGEMENT_ENABLED=true`인지,
`ai_user_kill_switch`/`schedule_execution_paused`가 꺼져 있는지 확인.
`POST /admin/trigger/reconcile-engagement?dryRun=true`로 부족분이 실제로
계산되는지 먼저 확인하고, `dryRun=false`로 반영. 일부 좋아요만 반영되고 나머지가
`persona_action_log`에 `COMMENT_LIKE FAILED`(target_id가 댓글 id가 아니라
postId로 찍힘)로 남으면 봇 계정 JWT 발급 실패(`BotTokenCache`)가 원인인 경우가
많다 — `Bot login failed ... 429 Too Many Requests` 로그 확인. 리콘실러는
수렴형이라 다음 5분 주기에 저절로 재시도된다(자동 복구, 별도 조치 불필요).

## 9. persona-diversity-v4 — 기존 글 정리 · 게이트 스크립트

트랙 상세: `docs/_active/persona-diversity-v4.md`. 페르소나 연령대를 23~49세로 재구성하면서
기존 AI-user 글 중 50대 이상 화자 서사를 정리하고, 150명 쿼터·문체 다양성·글쓰기 회전을
검증하는 두 도구가 `ai-user/tools/`에 추가됐다.

### `purge_offtarget_posts.py` — 연령 이탈 글 분류·soft delete

```bash
# 분류만 (DB 쓰기 0). --limit로 비용 절약 표본만 돌릴 수 있다.
python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --classify --limit 100 --out out.jsonl

# OFF_TARGET만 soft delete (posts.deleted_at + 그 글의 post_comments.deleted_at)
python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --apply out.jsonl
```

- 분류기: AS 호스트 `claude -p --model claude-haiku-4-5-20251001 --output-format json --disallowedTools '*'`
  (이 도구에 한해 CLI 직접 호출 예외 허용). 20건씩 묶어 호출, 실패 시 최대 3회 재시도 후
  해당 배치는 `verdict=ERROR`로 표시(무음 실패 금지).
- `--apply`가 `env-file`을 prod로 추론하면 `--i-mean-it` 없이는 거부한다. prod DB 쓰기는
  Fable(Phase 3~4) 전용.
- **DB 접속은 `docker exec <container> mariadb --raw ...`로 한다.** prod mariadb는 호스트
  포트가 노출돼 있지 않아 이 방식만 동작한다. `--raw`가 없으면 `-B`(batch) 모드 클라이언트가
  출력 중 백슬래시를 한 번 더 이스케이프해서(`\n` → `\\n`) 본문에 실제 줄바꿈·따옴표가 있는
  글의 `JSON_ARRAYAGG` 결과가 깨진다(2026-09-05 실측, 100건 표본에서 발견).
- 2026-09-05 dev 100건 표본 결과: OFF_TARGET 2건(2%) — 부모 세대 시점 가족여행 정산 글,
  "내 딸"·"우리 엄마"라 자칭하는 40대 이상 모친 화자 글. 전수 실행(`--apply` 없이 `--classify`
  전체)은 아직 하지 않았다(비용 절약 지시).

### `persona_gate_check.py` — 게이트 a(분포)·b(다양성)·c(회전)·d(관계)

```bash
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate a
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate b
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate c --days 7
python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate d
```

- 게이트 a·b는 `personas` 테이블에 `V22__persona_identity_axes.sql`(WP1)이 적용돼
  `age_years`·`gender`·`marital`·`married_years`·`has_kids`·`style_axes` 컬럼이 있어야
  동작한다. 없으면 "V22 컬럼이 없다(미적용)" 메시지와 함께 **종료 코드 2**로 즉시 중단한다
  (2026-09-05 dev에서 이 실패 경로를 실측 확인한 뒤 dev에 V22를 적용했다 — orchestrator가
  기동 시 Flyway로 자동 적용하므로 prod도 재기동 시 같은 경로로 적용된다).
- 게이트 c(글쓰기 회전, 최근 N일)는 참고용이며 배포 게이트가 아니다 — 항상 종료 코드 0. 글
  집계 쿼리는 `deleted_at IS NULL`을 반드시 걸어야 한다(2026-09-05 감사에서 이 필터 누락으로
  soft-delete된 글이 회전율에 섞이는 버그를 발견·수정 — `docs/_active/persona-diversity-v4.md`
  §6 게이트 집계 결함).
- 게이트 d(관계)는 `PersonaRelationshipFiller` 실행 결과를 검증한다. 기준은 관계를 하나도
  갖지 못한 활성 페르소나 0명, 성별·나이 제약 위반 0건, `marital`과 관계 유형의 정합성 위반
  0건이다(SINGLE인데 MARRIAGE 관계 등). 2026-09-05 dev 실측에서 위반 20건이 나왔는데 전부
  이번 트랙이 만든 것이 아니라 **기존 시드 60건에 원래 섞여 있던 것**이었다(동성 COUPLE·
  나이차 초과). 관계 부여는 재생성으로 `marital`이 확정된 뒤에 실행해야 의미가 있다 —
  전원 기본값 `SINGLE` 상태에서 돌리면 MARRIAGE·COUPLE 관계가 하나도 생기지 않는다.
- has_kids(`BIT(1)`) 등 정수 캐스팅이 필요한 컬럼을 캐스팅 없이 `JSON_ARRAYAGG` 등 JSON 집계에
  넣으면 raw 바이트가 그대로 박혀 JSON 자체가 깨지고 게이트 a·b가 예외로 죽는다(같은 감사에서
  발견·수정) — 새 컬럼을 게이트 집계에 추가할 때 동일하게 정수 캐스팅할 것.
- 종료 코드: `0`=PASS, `1`=게이트 a/b FAIL, `2`=V22 미적용.
- **재생성 배치**(`POST /admin/trigger/regenerate-persona-profiles`)는 세션 한도·인증 오류
  시그니처 또는 연속 5회 실패를 만나면 자동으로 멈춘다(`processed`/`remaining`/`haltedReason`
  응답 필드로 진척 확인). 완료 판정은 `voice_profile.profile_rev="v4"` 마커다 — 상세:
  [llm-call-budget.md](../70-policy/llm-call-budget.md) §1.5.

### `resume-persona-profile-regen.sh` — 세션 한도 리셋 자동 대기·재개

2026-09-05 dev/prod 재생성 배치가 하루에 두 번 세션 한도로 중단됐다(dev 6am UTC 리셋,
prod 49/150에서 11am UTC 리셋). `PersonaProfileRegenerator`의 한도 감지·halt 자체는 정확히
동작했지만, 리셋 후 재실행은 사람이 트리거를 다시 쳐야 했다. `env/scripts/resume-persona-profile-regen.sh`가
그 재시도만 자동화한다 — LLM 호출·판정 로직은 전혀 새로 만들지 않고 기존 트리거를 반복 호출할 뿐이다.

```bash
env/scripts/resume-persona-profile-regen.sh --env dev --seed 20260905
env/scripts/resume-persona-profile-regen.sh --env prod --seed 20260905 --i-mean-it   # prod는 --i-mean-it 필수
env/scripts/resume-persona-profile-regen.sh --dry-run --env dev --seed 1             # 트리거 호출 없이 로직만 시연
```

- 재개 원리: only 없이(=force=false) 같은 seed로 재호출하면 `isProfileCurrent`가 이미
  완료된(`voice_profile.profile_rev="v4"`) 페르소나를 건너뛴다 — 그래서 이 스크립트는 매
  재시도마다 동일한 요청을 반복하기만 하면 된다. `--seed`는 QuotaPlanner 분포 계산에
  쓰이므로 전체 재시도 동안 반드시 같은 값을 유지해야 한다.
- `remaining==0`이면 성공 종료. `haltedReason`이 `LLM_ERROR_SIGNATURE: ...`(세션 한도/인증/거절)이면
  오류 문구에서 리셋 시각을 파싱해(`env/scripts/lib/session-reset-time.sh`의
  `parse_session_reset_epoch`) 그때까지 대기 후 재시도한다. 처리하는 실제 문구 두 종류:
  `You've hit your session limit · resets 11am (UTC)`, `resets 8pm (Asia/Seoul)`
  (시:분 및 `(TZ)`가 IANA 이름이든 `UTC`든 모두 GNU `date -d "today HH:MM" TZ=<tz>`로 계산). 파싱
  실패 시 고정 30분(`--fallback-wait-minutes`)으로 폴백한다.
- `haltedReason`이 `CONSECUTIVE_FAILURES(...)`이면 한도가 아니라 실제 결함이므로 **재시도하지
  않고 실패 종료**한다 — 사람이 봐야 한다.
- 전체 재시도 상한은 `--max-retries`(기본 12)로 무한 루프를 막는다. 컨테이너 이름은 환경별로
  다르다 — **prod는 `-prod` 접미사가 붙지 않는다**(`againspring-ai-user-orchestrator`), dev만
  `-dev`(`againspring-ai-user-orchestrator-dev`).
- 진행·완료·실패는 텔레그램으로 알린다. 자격 파일 경로(`~/.config/again-spring-watchdog/telegram.env`)와
  `send_telegram` 함수는 `ops-watchdog.sh`와 동일하게 재사용한다(새 경로/함수를 만들지 않았다).
- 리셋 시각 파서는 `env/scripts/lib/session-reset-time.sh`에 독립 함수로 분리돼 있고,
  `env/scripts/lib/test-session-reset-time.sh`로 두 실제 문구 + 분 포함 변형 + 자정/정오 경계값 +
  파싱 실패 케이스를 검증한다(`bash env/scripts/lib/test-session-reset-time.sh`).

### `finalize-persona-profile-regen.sh` — 재생성 완료 후 마무리 러너

150명 재생성(`resume-persona-profile-regen.sh`로 완료)이 끝난 뒤에는 순서가 중요한 여러
검증·쓰기 단계를 사람이 하나씩 쳐야 했다. `env/scripts/finalize-persona-profile-regen.sh`가
그 순서를 하나로 묶는다 — 새 판정 로직을 만들지 않고 기존 `persona_gate_check.py`와
`fill-persona-relationships` 트리거를 순서대로 호출할 뿐이다.

```bash
env/scripts/finalize-persona-profile-regen.sh --env dev --relationship-seed 20260905
env/scripts/finalize-persona-profile-regen.sh --env prod --relationship-seed 20260905 --i-mean-it
env/scripts/finalize-persona-profile-regen.sh --dry-run --env prod --relationship-seed 1 --i-mean-it
```

순서(뒤 단계는 앞 단계 완료를 전제):

1. **재생성 완료 확인** — `personas.style_axes IS NOT NULL AND voice_profile.profile_rev='v5'`인
   활성 페르소나 수가 `PersonaQuotaPlanner.PERSONA_COUNT`(150)와 같은가를 직접 COUNT 쿼리로
   확인한다. 미달이면 **여기서 전체 중단**(게이트도 관계 부여도 실행하지 않음) — 실패 시 볼 곳:
   `env/scripts/resume-persona-profile-regen.sh`로 재개하거나
   `env/logs/resume-persona-profile-regen.log`의 최근 `haltedReason`.
2. **게이트 a(분포)·b(다양성)** — `persona_gate_check.py --gate a`/`--gate b --json`을 호출해
   `passed`와 실패 체크 목록을 파싱한다. 둘 중 하나라도 FAIL(exit 1) 또는 V22 미적용(exit 2)이면
   **3)~5) 전부 중단**한다 — 관계 부여는 dev/prod DB 쓰기라, 분포가 틀린 채로 진행하지 않는다.
3. **관계 부여** — `POST /admin/trigger/fill-persona-relationships?seed=<--relationship-seed>`
   (`PersonaRelationshipFiller`, 기존 관계 유지, coverage만 채움). 이 단계의 성공 판정은
   "응답에 `status:error`가 없는가"뿐이다 — 관계 내용의 정합성은 4)가 판정한다.
4. **게이트 d(관계)** — `persona_gate_check.py --gate d`. FAIL이어도 5)는 계속 진행하지만
   (5)는 순수 조회라 막을 이유가 없다) 스크립트 전체 종료 코드에는 반영한다. 기존 시드에
   섞여 있던 위반(동성 COUPLE·나이차 초과 등, 위 문단 참고)은 관계 재부여로 저절로 고쳐지지
   않을 수 있다는 점을 실패 시 안내 문구로 출력한다.
5. **게이트 c(회전, 참고용)** — `persona_gate_check.py --gate c --days <--gate-c-days, 기본 7>`.
   항상 실행하고 항상 진행·종료 코드에 영향을 주지 않는다 — 오늘 값은 7일 운영 후 재실행해
   비교할 기준선일 뿐이다.
6. **요약 표 출력** — 5단계 각각의 상태(PASS/FAIL/OK/INFO)와 상세를 표로 정리해 로그와
   stdout에 남긴다.

- `--dry-run`은 DB 조회·트리거 호출을 전혀 하지 않고(컨테이너 실행 여부 확인조차 생략)
  내장 픽스처(150/150 정상 분포·무위반 관계·게이트 c 정상치)로 전체 흐름만 시연한다 —
  `resume-persona-profile-regen.sh --dry-run`과 동일한 안전 설계.
- 컨테이너 이름은 재개 스크립트와 동일한 규칙이다 — **prod는 `-prod` 접미사가 붙지 않는다**
  (`againspring-ai-user-orchestrator`), dev만 `-dev`(`againspring-ai-user-orchestrator-dev`).
  DB 컨테이너는 `persona_gate_check.py`의 `CONTAINERS` 매핑과 동일(`againspring-mariadb-dev`/
  `-prod`).
- 텔레그램 알림은 `ops-watchdog.sh`/`resume-persona-profile-regen.sh`와 동일한 자격 파일
  (`~/.config/again-spring-watchdog/telegram.env`)과 `send_telegram` 함수를 재사용한다.
- 종료 코드: `0`=전 단계 성공(게이트 c 제외), `1`=1)/2)에서 중단됐거나 3)/4)가 실패, `2`=인자
  오류(`--env prod`에 `--i-mean-it` 누락 등).
- `PROFILE_REV`(현재 `v5`)는 `PersonaProfileRegenerator.CURRENT_PROFILE_REV` /
  `persona_gate_check.py`의 `CURRENT_PROFILE_REV`와 동기화해야 한다 — 축 배정 알고리즘이
  바뀌어 리비전이 오르면 세 곳 모두 같이 올릴 것.
