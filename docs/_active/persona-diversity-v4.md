# AI-user 페르소나 다양성 v4 (persona-diversity-v4)

> **상태**: Phase 1·2 완료 — WP1~WP4 병합 완료(commit `81ba5dc9`), 감사에서 발견된 결함 3건
> 수정 중(§6) · **마지막 갱신**: 2026-09-05
>
> 초기 작업 지시는 각 에이전트에게 gitignore 대상 경로로 직접 전달됐다. 이 파일은 그 계약의
> **git 추적 요약본**이다 — 전달용 원본이 사라져도 트랙 목적과 결정 사항은 이 파일에 남는다.
> 이 파일 자체가 이 트랙의 git 추적 권위본이다.

## 1. 왜 하는가

AI-user 150명의 문체·연령·상황이 좁게 수렴해 실제 사용자에게 "AI 티"가 났다.
V21까지의 `personas` 스키마는 `archetype`·`tier`·`voice_profile`(JSON)만 있고 연령·성별·
결혼·자녀 같은 정체성 축이 없어 서사가 20~50대를 가리지 않고 뒤섞였다(과거 글에 정년·손주·
환갑 서사가 섞여 있었던 게 그 증상). 이번 트랙은 페르소나 정체성 축을 스키마로 명시하고,
150명 쿼터를 축별로 균등 분배하고, 카테고리·시점 규칙을 강제하고, 문체 다양성을 게이트로
검증한다.

## 2. 트랙 구조 — WP1~WP4 병렬 worktree

| WP | 담당 | 소유 영역 |
|---|---|---|
| WP1 | 페르소나 스키마·쿼터·문체 축 | `V22__persona_identity_axes.sql`, `PersonaFactory`, `PersonaCard`, `AiUserSeedLoader`(java) |
| WP2 | 카테고리 비율·소스 골격 추출 | thread-plan 카테고리 믹서, `POST /v2/extract-skeleton`(llm 워커) |
| WP3 | 작성자·댓글자 선택 가중치 | 페르소나 선택/추첨 로직, `last_post_at`/`last_comment_at` 갱신 |
| WP4 (이 파일 작성자) | 정리 도구·게이트·설정·문서 | `ai-user/tools/purge_offtarget_posts.py`, `ai-user/tools/persona_gate_check.py`, 설정 정리, Doc-Sync |

각 WP는 별도 git worktree/브랜치(`wp1/persona-v4` 등)에서 작업하며, Fable이 Phase 2에서
병합·최종 문서화·게이트 재검증을, Phase 3~4에서 dev 검증 후 prod 반영을 담당한다.
에이전트는 dev DB만 읽고 쓴다 — prod DB 쓰기는 Fable 전용.

## 3. 결정 10개 요약

1. **정체성 축 신설**: `personas`에 `age_years`(23~49) · `gender`(M/F) · `marital`
   (SINGLE/DATING/ENGAGED/MARRIED) · `married_years` · `has_kids` · `job_type`(8종) ·
   `job_title` · `style_axes`(JSON) 컬럼 추가(`V22__persona_identity_axes.sql`, WP1).
   기존 `voice_profile.age`(밴드)·`gender`·`job`은 호환용으로 동시 갱신한다.
2. **150명 쿼터 그리드 고정**: 성별 75/75, 연령대 60/60/30(23-29/30-36/37-49), 결혼
   미혼 60·기혼 90(연령대별 15/45/30), 기혼 중 자녀 있음 45명, tier HEAVY20·REGULAR80·
   LIGHT50, voice_type NATEPAN75·BLIND75.
3. **`style_axes` 10축 균등 분포**: directness·affect·humor·stance·length·speech·
   emoticon·spelling·linebreak·profanity — 각 축 값이 코드로 강제된 균등 분포를 갖는다.
4. **`PersonaCard` 400자 요약으로 프롬프트 교체**: AI_POST·PAIRED·HUMAN_POST·human-reply
   전부 `personaCard`(문자열)를 쓰고, `voiceProfile` 전체 JSON은 더 이상 보내지 않는다
   (토큰 절감 겸 페르소나 일관성 강화).
5. **카테고리 비율 + 시점 제한 고정**: WORK 35%(전원, B시점 금지) · COUPLE 25%
   (`marital != MARRIED`만 작성자, B 허용) · FRIEND 15%(전원, B 허용) · FAMILY 15%
   (전원, B시점 금지, 시부모/처가는 MARRIED만) · MARRIED 10%(`marital == MARRIED`만).
   기존 `romanticShare`는 이 표로 대체.
6. **작성자·댓글자 선택 가중치 공식 고정**: `weight = tierW × (1 + hoursSinceLast/24)^1.5`
   (HEAVY 3.0 / REGULAR 1.5 / LIGHT 1.0). 결정론 정렬 금지, 매번 가중 비복원 추첨.
7. **소스 골격 JSON 계약**: `POST /v2/extract-skeleton`(Haiku)이 원문에서 category·역할·
   사건·claim 등을 뽑아 일반화한다. 고유명사·금액·날짜는 일반화, 원문 문장 그대로 담지 않음.
8. **기존 글 정리는 분류 후 승인제**: `purge_offtarget_posts.py --classify`로 50대 이상
   서사를 Haiku가 판정, `--apply`는 사람(Fable)이 JSONL을 검토한 뒤 dev에서 먼저 실행.
   prod는 `--i-mean-it` 없이 거부.
9. **게이트 a(분포)·b(다양성)·c(회전)를 배포 전 필수 검증으로 고정**: a·b는 실패 시
   종료 코드 1(배포 게이트), c(글쓰기 회전)는 참고용(배포 게이트 아님).
10. **죽은 설정 제거**: `AI_USER_PERSONA_TARGET`(참조 코드 0건, compose/코드 기본값 불일치)을
    compose·env·`OrchestratorProperties`에서 제거하고, 페르소나 목표치는 상수
    `PERSONA_COUNT = 150`으로 고정한다.

## 4. Phase 진행 상태

| Phase | 내용 | 상태 |
|---|---|---|
| 1 | WP1~WP4 병렬 worktree 구현 | **완료** — WP1(신원 축·쿼터), WP2(카테고리 믹스·소스 골격), WP3(`PersonaLottery` 추첨, matcher·캡슐 폐기), WP4(도구·게이트·설정·문서) 각각 구현 완료 |
| 2 | 병합 + 최종 문서화 + 게이트 재검증 | **완료** — WP1~WP4 병합(commit `81ba5dc9`, 중복 클래스 정리). 테스트: orchestrator 403건, llm 244건, learning 108건, e2e-realbe 125건 모두 통과. dev DB에 `V22__persona_identity_axes.sql` 적용 완료. 감사에서 결함 3건 발견 — §6 |
| 3 | dev 전수 검증 (`--classify` 전체, `--apply`, 게이트 a/b/c) | 대기 — §6 결함 수정 후 진행 |
| 4 | prod 반영 (Fable 전용) | 대기 |

## 5. WP4 실측 메모 (2026-09-05, 병합 전 — §4 Phase 2 완료로 아래 두 항목은 이후 해소됨)

- dev DB에 `V22__persona_identity_axes.sql` 아직 미적용 — `persona_gate_check.py --gate a`가
  "V22 컬럼이 없다" 메시지 + 종료 코드 2로 정확히 감지함을 확인. (Phase 2 완료 후 dev DB에 적용됨 — §4)
- `purge_offtarget_posts.py --classify --limit 100` 실측: dev synthetic 글 396건 중 100건
  표본 분류 → OFF_TARGET 2건(2%), ERROR 0건. 전수 실행은 비용 절약을 위해 보류.
- `docker exec <container> mariadb -B`(batch 모드)는 출력 중 백슬래시를 한 번 더
  이스케이프해서 `JSON_ARRAYAGG` 결과를 깨뜨린다 — `--raw` 플래그로 해결(두 도구 모두 반영).
- `AI_USER_PERSONA_TARGET` 제거는 compose 2곳 + `env/.env.dev` + `env/.env.ai-user` +
  `OrchestratorProperties.personaTarget` 완료. `AiUserSeedLoader.java`(WP1 소유 파일)의
  두 호출부(`ensureCount(props.getPersonaTarget())`, 47행대·165행대)는 WP4가 건드리지
  않았다 — WP1이 상수로 교체하기 전까지 이 파일은 컴파일되지 않는다(Phase 2 병합 시 조율 필요).
  (병합 commit `81ba5dc9`에서 `PersonaQuotaPlanner.PERSONA_COUNT`로 교체 완료 — 해소됨)

## 6. 감사 발견 결함 (2026-09-05, 수정 중)

Phase 2 병합 후 감사에서 발견돼 별도로 수정 중인 결함 3건:

1. **레거시 `/generate/post` 원문 누출** — legacy `assemblePostPrompt` 경로(`PromptAssembler`)가
   신규 `personaCard` 대신 원문 소스 텍스트를 그대로 흘려보내는 경로가 남아 있다.
2. **human-reply `PersonaCard` 미배선** — `ReplyGenRequest`(`ai-user/llm/.../dto/ReplyGenRequest.java`)에
   `personaCard` 필드가 없다. `voiceProfile` 전체 JSON만 실려 human-reply 경로는 계약4(400자 카드로
   교체)를 아직 못 받았다.
3. **`PersonaMaritalReader` 컬럼 미독해** — `personas.marital`(V22) 컬럼을 우선 읽고 없으면
   `voice_profile.marital`로 폴백하도록 설계됐으나(`PersonaMaritalReader.read()`), 실제 호출 경로에서
   컬럼값이 온전히 전달되지 않는 사례가 감사에서 확인됐다.

세 건 모두 다른 에이전트가 코드 레벨에서 수정 중이다. 이 문서는 감사 시점 기준 사실만 기록하며,
수정 완료 여부는 다음 갱신에서 반영한다.

> **참고(§7 작성 중 확인)**: 위 3번(`PersonaMaritalReader` 컬럼 미독해)은 commit `c1ac52f7`
> (`fix(ai-user): 감사에서 드러난 계약 위반 3건과 미배선 3건을 고친다`)에서 이미 해결됐다 —
> 현재 HEAD의 `PersonaMaritalReader.java`는 `personas.marital` 컬럼만 읽고 폴백이 없다
> (직접 코드 확인, 2026-09-05). §4·§6 상태 갱신은 이 문서의 다른 작업자 담당이라 여기서는
> 건드리지 않고, §7의 사실관계에만 반영한다.

## 7. Phase 4 prod 배포 절차 (2026-09-05 작성 — 계획 단계, prod 미실행)

> 이 절은 조사·계획만 담는다. 작성 시점에 prod에는 어떤 명령도 실행하지 않았고 prod DB도
> 읽지 않았다 — 전부 코드·설정·`docker inspect`/`docker ps`(조회만) 근거다. 실행하는
> 운영자(또는 에이전트)는 각 단계의 "성공 판정"을 실제로 확인하며 진행할 것.

### 7.0 범위 재확인 — 이 트랙은 backend·frontend를 건드리지 않는다

`git diff --stat a2962abe~1 556cba48 -- backend/ frontend/` 결과가 **빈 diff**다 — WP1~WP4
전체가 `ai-user/` + `docs/` + `env/` 설정에만 있다. 따라서 **`scripts/deploy.sh prod`
(base+prod 스택: backend·frontend·nginx·DB)는 이 트랙 때문에 실행할 필요가 없다.** Phase 4는
`env/docker-compose.ai-user.yml` 스택(그중에서도 아래 3개 컨테이너)만 대상으로 한다.

### 7.1 사전 확인 결과 (코드·설정 직접 확인 — prod DB는 읽지 않음)

| 확인 항목 | 결과 | 근거 |
|---|---|---|
| (a) prod orchestrator가 V22를 자동 적용하는가 | **예.** `flyway.enabled: true`, `baseline-on-migrate: true`, 전용 히스토리 테이블 `flyway_schema_history_aiuser` — 재기동 시 자동 적용됨 | `ai-user/orchestrator/src/main/resources/application.yml:24-29` |
| (b) 새벽 배치 크론 위치·시각 | `justant` 사용자 crontab 3번째 줄 `5 3 * * * .../env/scripts/nightly-ai-user-batch.sh` — 호스트 TZ는 `timedatectl` 확인 결과 `Asia/Seoul`이므로 **03:05 KST** | `crontab -l`(직접 조회), `env/scripts/nightly-ai-user-batch.sh` |
| (c) provider_*/kill switch 켜고 끄는 법 | 즉시 정지는 `POST /api/admin/ai-user/kill`(전 provider→`OFF` + `ai_user_kill_switch=true`, 원자적 1콜, ADMIN 권한 필요). 원복 전용 엔드포인트는 없다 — `PUT /api/admin/ai-user/generation-config`로 **전체 필드를 다시 보내야** 한다(부분 patch 아님). 상태 조회는 `GET /api/admin/ai-user/effective-gates`(`generationAllowed`/`publishingAllowed`/`reasons[]`) | `backend/src/main/java/com/againspring/api/admin/AdminAiUserController.java:41`(`@PreAuthorize("hasRole('ADMIN')")`)`,80-89,109-115,205-219` |
| (d) prod 배포 실패 시 롤백(이전 이미지 태그) | **자동 장치 없음.** compose가 태그 없는 bare 이름(`againspring-ai-user-orchestrator` 등, 암묵 `:latest`)으로 빌드해서, 재빌드하면 이전 이미지가 dangling(`<none>`)이 되고 GC 대상이 된다. 실측: 현재 실행 중인 orchestrator 이미지는 `aa19acc3cc67`(43시간 전 = 구버전), 남아있는 별도 태그 `againspring-ai-user-orchestrator-prod:latest`(`5169ba5110e6`)는 **2026-06-21 산물이라 2.5개월 구버전 — 롤백용으로 못 쓴다** | `docker ps`/`docker images`/`docker inspect` 실측(조회만, 2026-09-05) |

### 7.2 조사 중 추가로 발견한 위험 — 사용자가 준 제약 목록에는 없던 것

**orchestrator 말고 `ai-learning`·`prod-dev-sync`도 재빌드가 필요하다.** 이 트랙(commit
`a2962abe`~`556cba48`)의 diff를 뒤져보면 이 두 컨테이너의 소스도 바뀌었는데, `docker inspect`로
확인한 두 컨테이너의 이미지는 **둘 다 2026-09-03 09:10 UTC 빌드 — orchestrator와 똑같이
구버전**이다.

1. **`ai-learning`**(컨테이너 `againspring-ai-learning`, llm-ai-user처럼 dev/prod 공유 단일
   인스턴스): `ai-user/learning/app/services/persona_strengthener.py`의
   `update_persona_profiles()`가 이번 트랙에서 **no-op으로 교체됨**(코드 주석: "lexicon·
   writing_quirks·general_style·post_style·comment_style·reply_style는 이제
   PersonaProfileRegenerator가 유일한 쓰기 경로"). 구버전이 그대로 살아있으면
   `ai-user/learning/app/scheduler.py:104`(크롤 완료 후 자동 `run_strengthen()`, KST **02:00**)와
   115행(standalone, KST **05:00**)이 재생성 직후의 개별화된 페르소나 문체를 voice_type
   단위로 다시 뭉갠다 — **이 트랙이 고치려는 문제가 그대로 재발**한다. 02:00 KST가
   03:05 KST 새벽 배치보다 먼저 오므로 **사실상 이게 더 급한 데드라인**이다.
2. **`prod-dev-sync`**(컨테이너 `againspring-prod-dev-sync`): `ai-user/sync/sync.py`가 이번
   트랙에서 `personas` 테이블을 24h full sync 대상에서 **제외**하도록 바뀌었다(사유: prod→dev
   단방향 동기화가 dev에서 이미 검증된 재생성 페르소나를 prod 값으로 되돌리기 때문).
   구버전이 살아있으면 `ai-user/sync/sync.py:70`(`SYNC_CRON` 기본값 KST **05:30**) full
   sync 때 dev의 이미 검증된 페르소나 상태가 prod(재생성 전이면 구버전 SINGLE 일색) 값으로
   덮인다 — "dev는 이미 배포·검증했다"는 전제가 다음날 새벽에 무효화된다.

세 컨테이너 모두 같은 `docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user`
호출 한 번으로 같이 재빌드할 수 있다 — 7.4의 4단계 참고.

### 7.3 MARRIED 슬롯 구멍 — 대응 방안과 근거

**현상**: `CategoryMixPlanner.authorEligible()`(`ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/service/threadplan/CategoryMixPlanner.java:67-74`)은
`MARRIED` 카테고리에 `PersonaMaritalReader.isMarried(p)`(marital == `'MARRIED'`)만 작성자
자격을 준다. `PersonaMaritalReader`(같은 디렉터리 `PersonaMaritalReader.java:26-28`)는
**폴백 없이 컬럼값만** 읽는다(§6 참고 노트 — 이 부분은 이미 수정 완료). V22
(`ai-user/orchestrator/src/main/resources/db/migration/V22__persona_identity_axes.sql:6`)는
`marital`을 `NOT NULL DEFAULT 'SINGLE'`로 추가한다 — 즉 **재생성 전에는 prod 150명 전원이
SINGLE**이 된다. `PersonaLottery.drawAuthors()`(`ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/service/persona/PersonaLottery.java:36-42`)는
자격자가 없으면 **빈 리스트를 반환할 뿐 예외를 던지지 않는다**. 호출부
`NightlyScheduledFillService.tryFillOneSlot()`(`ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/service/threadplan/NightlyScheduledFillService.java:274-278`)도
`drawn.isEmpty()`면 `warn` 로그만 남기고 다음 source로 넘어간다 — **크래시가 아니라 "그
슬롯만 조용히 생성 실패"**다. 실질 피해는 계약5 비율(MARRIED 10%, `pairedAllowed`도
MARRIED 포함)만큼 그날 밤 예약글 목표치가 미달되는 것 — 데이터 오염이나 500 에러는 아니다.

**대응 후보**:
- **재생성을 먼저 하고 배포** — 불가능. `regenerate-persona-profiles` 엔드포인트와
  `marital` 컬럼 자체가 이번 트랙의 신규 코드/스키마라 구버전 orchestrator에는 없다.
- **배포 직후 생성을 잠시 끄고 재생성 완료 후 켜기 — 채택 (아래 이유).**
- **방치(위험 수용)** — 크래시는 없지만 MARRIED 슬롯이 재생성 완료 전까지 매 배치마다
  조용히 빠지고 아무 알림도 없다(로그만). 첫날부터 정상 분포로 시작한다는 트랙 목적에
  안 맞아 채택하지 않는다.

**추천 = 배포 직후 kill switch ON → 재생성 → 검증 → OFF, 이유**: 실제 생성 진입점들이
전부 `ai_user_kill_switch`를 **직접** 확인한다 — 확인한 곳만 나열하면
`AiPostBundleService.ownsPostGeneration()`(`ai-user/orchestrator/.../threadplan/AiPostBundleService.java:117`),
`ScheduledPostPublisher.java:89`, `ThreadPlanPublisher.java:44`, `PartnerAnswerPublisher.java:74`,
`PlanEngagementDispatcher.java:74`, `PairedPostScheduler.java:490`, `HumanReplyBatchService.java:68`,
`ThreadPlanGenerationService.java:726` — MARRIED뿐 아니라 전 생성·발행 경로를 한 스위치로
막을 수 있는, 코드로 검증된 유일한 전역 서킷브레이커다. 반면 재생성 경로
(`PersonaProfileRegenerator.java`, `PersonaProfileLlmClient.java`)는 이 게이트를 **전혀
참조하지 않는다**(grep 결과 0건) — kill switch가 켜져 있어도 재생성 자체는 그대로 동작한다.
즉 "생성은 멈추고 재생성만 켜놓는" 상태를 kill switch 하나로 만들 수 있다.

### 7.4 단계별 절차

시각은 전부 KST(호스트 TZ 확인됨: `timedatectl` → `Asia/Seoul`). 이날 밤 크론이 이 순서로
온다: **02:00**(ai-learning crawl+strengthen) → **03:05**(nightly-ai-user-batch) →
**05:00**(ai-learning standalone strengthen) → **05:30**(prod-dev-sync 24h full). 재빌드·
재생성·검증은 전부 **02:00 KST 전** 완료를 목표로 한다.

| # | 단계 | 명령/조작 | 성공 판정 | 실패 시 대응 |
|---|---|---|---|---|
| 1 | prod DB 백업 (V22 ALTER TABLE 전 안전망) | `docker exec againspring-mariadb-prod sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines "$MARIADB_DATABASE"' > /home/justant/backups/prod-aiuser-$(date +%Y%m%d-%H%M%S).sql` | 덤프 파일 크기 > 0, `grep -c "INSERT INTO \`personas\`"` > 0 | 1회 재시도. 재실패 시 중단·보고(백업 없이 진행 금지) |
| 2 | 재빌드 전 이미지 id 3개 기록 (수동 롤백용 메모) | `docker inspect --format '{{.Image}}' againspring-ai-user-orchestrator againspring-ai-learning againspring-prod-dev-sync` | sha 3개 확보해 별도로 적어둠 | — |
| 3 | kill switch ON — 재생성 끝날 때까지 전 생성 정지 | `GET /api/admin/ai-user/generation-config` 응답 전체를 저장(9단계 복원용) → `POST /api/admin/ai-user/kill` | `GET /api/admin/ai-user/effective-gates`에서 `ai_user_kill_switch=true`, `generationAllowed=false` | kill 호출이 5xx면 최대 3회 재시도(`.claude/rules/llm-safety.md` §4). 계속 실패하면 4단계 진행 보류하고 보고 |
| 4 | orchestrator + ai-learning + prod-dev-sync 재빌드 | `cd env && docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d --build ai-user-orchestrator ai-learning prod-dev-sync` | `docker ps`에서 3개 모두 `healthy`, `docker inspect --format '{{.Created}}'`가 방금 시각으로 갱신 | healthcheck 실패 시 `docker logs <컨테이너>` 확인. Flyway 체크섬/마이그레이션 오류면 §7.5 롤백 절차로 |
| 5 | V22 적용 확인 | `python3 ai-user/tools/persona_gate_check.py --env-file env/.env.prod --env-name prod --gate a` | 종료 코드가 2가 **아니면**(0 또는 1) 컬럼 존재 확인. 이 시점은 재생성 전이라 **1(분포 불일치)이 정상** | 종료 코드 2면 Flyway 미적용 — orchestrator 로그에서 flyway 에러 원인 확인 |
| 6 | 재생성 드라이런 (LLM 비용 0) | `docker exec againspring-ai-user-orchestrator wget -qO- -T 60 --post-data='' "http://localhost:8096/admin/trigger/regenerate-persona-profiles?seed=<임의 정수>&dryRun=true"` | 응답 `distribution`이 §3 결정 2의 쿼터(성별 75/75, 결혼 미혼60/기혼90 등)와 오차 ±3 이내 | 크게 어긋나면 `PersonaQuotaPlanner` 확인 필요 — 진행 중단, 보고 |
| 7 | 재생성 실행 (150명, 실 LLM 호출) | `docker exec againspring-ai-user-orchestrator wget -qO- -T 21600 --post-data='' "http://localhost:8096/admin/trigger/regenerate-persona-profiles?seed=<6과 동일 seed>&batch=10"` | 응답 처리 건수 = 150, 에러 0 | 일부 실패면 실패 id만 `only=<id1,id2,...>`로 재호출(최대 3회, `.claude/rules/llm-safety.md` §4). 3회 소진 후에도 남으면 §7.3 "방치" 리스크로 명시하고 보고 |
| 8 | 재생성 검증 (게이트 a·b) | `python3 ai-user/tools/persona_gate_check.py --env-file env/.env.prod --env-name prod --gate a` 및 `--gate b` | 둘 다 종료 코드 0(PASS) | FAIL이면 미달 축만 `only=`로 좁혀 7번 재실행 |
| 9 | kill switch OFF — 3단계 스냅샷으로 복원 | `PUT /api/admin/ai-user/generation-config`에 3단계에서 저장한 응답 본문을 그대로 보내되 `aiUserKillSwitch: false`만 바꿔 전송(부분 patch 불가 — 전체 필드 필요) | `GET /api/admin/ai-user/effective-gates`에서 `ai_user_kill_switch=false`, (다른 게이트가 별도로 꺼져있지 않다면) `generationAllowed=true` | 실패 시 최대 3회 재시도. 계속 실패하면 kill switch는 ON인 채로 두고 보고 — 생성 안 되는 쪽이 MARRIED 구멍보다 안전 |
| 10 | 02:00 KST 전 최종 확인 | `docker ps`로 ai-learning이 4단계 재빌드 이미지로 떠 있는지 재확인 | 02:00 KST 이전 완료 | 못 맞추면 02:00 크론 전에 ai-learning을 `AI_LEARNING_ENABLED=false`로 임시 재기동해 강화 잡을 건너뛰게 하고, 재빌드 후 원복 |
| 11 | 03:05 KST 새벽 배치 결과 확인 | `tail -50 env/logs/nightly-ai-user-batch.log` | 목표 글 수만큼 생성, `category=MARRIED` 슬롯도 성공 라인 포함 | 미달이면 7~9단계가 02:00 전에 못 끝난 것 — §7.3 "방치" 상태로 하룻밤 지나간 것이니 다음날 수동 보정 |
| 12 | 05:30 KST sync 이후 dev 상태 확인 | dev DB에서 `marital` 분포 조회(dev이므로 이 문서 범위 내에서 허용) | 4단계에서 prod-dev-sync가 이미 재빌드됐다면 personas는 sync 대상에서 빠져 dev 값 그대로 유지 | 4단계를 건너뛰고 구버전 sync가 돌았다면 dev personas가 prod 값으로 덮였을 것(재생성 후라면 무해, 재생성 전이라면 dev 검증 상태 훼손) — 그래서 4단계를 반드시 05:30 전에 끝내는 게 핵심 |

### 7.5 롤백

자동 롤백 장치가 없으므로(§7.1 (d)) 수동으로 진행한다.

1. 2단계에서 기록해 둔 이미지 sha로 되돌린다: `docker tag <기록해둔 sha> againspring-ai-user-orchestrator:latest`(ai-learning·prod-dev-sync도 각각 동일)
   → `docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d --no-build ai-user-orchestrator ai-learning prod-dev-sync`(`--no-build`로 방금 재태그한 이미지 사용).
2. V22가 이미 적용된 상태로 구버전 orchestrator를 되돌려도 **컬럼 롤백은 불필요**하다 —
   구버전 코드는 신규 컬럼을 참조하지 않고, 전부 `DEFAULT`가 있어 기존 INSERT/SELECT에
   영향이 없다(Flyway는 다운마이그레이션을 지원하지 않으므로 애초에 컬럼을 되돌리는
   경로도 없다).
3. DB 자체를 되돌려야 하는 상황(예: 재생성이 잘못된 값으로 150명을 덮어씀)이면 1단계
   백업으로 복원한다: `docker exec -i againspring-mariadb-prod mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" < /home/justant/backups/prod-aiuser-<timestamp>.sql` —
   **이건 prod 전체 데이터를 백업 시점으로 되돌리는 파괴적 작업이라 사용자의 명시 승인
   없이는 절대 실행하지 않는다.**
