# 마케팅 반응 루프 + 포맷 실험 구현 계획

> **For agentic workers:** 이 문서가 설계와 계획을 겸한다. 자기 작업(Task) 절과 "전역 제약"만 지키면 된다. 다른 Task의 파일은 건드리지 않는다.

- `상태`: 진행 중
- `마지막 갱신`: 2026-11-21
- 작성: 2026-11-21 · 트랙 종료 시 `docs/shared/marketing/`로 승격 후 삭제

**Goal:** 게시 성공이 아니라 사람의 반응을 기준으로 영상 포맷과 소재를 자동으로 고르는 구조를 만든다. 목표는 플랫폼의 시험 노출을 통과하는 게시물이 나올 때까지 포맷을 바꿔 가며 시험하는 것이다.

**Architecture:** AS 백엔드가 게시물마다 "포맷 arm"을 배정하고, 게시 48시간 뒤 같은 일령의 반응으로 성과 점수를 매긴다. 그 점수가 다음 arm 배정(UCB)과 소재 선택 배수(주제 편향)에 자동으로 되돌아간다. arm이 정한 영상 매개변수는 AS → ASM → WaggleBot으로 전달된다.

**Tech Stack:** AS = Spring Boot 3.3 + MariaDB(Flyway) + Next.js 14 / ASM = Python 3.12 FastAPI / WaggleBot = Java 백엔드 + Python 렌더 워커

## 1. 왜 (2026-11-21 진단)

- 게시물 약 1,250건 중 시험 노출을 넘어 확산된 것이 0건이다. YouTube 쇼츠 242건 중 52%가 조회 900~1,300에 몰리고 최고가 2,532다.
- 영상 포맷이 한 가지(정지 텍스트 카드, 43~45초)뿐이고, 운영 게시물에서 포맷을 번갈아 시험하는 구조가 없다.
- 성과를 소재 선택에 되돌리는 장치(`auto_adjust`, 테마 배수)는 꺼져 있다.
- 30초 상한은 어디서도 적용되지 않는다. 댓글 낭독 약 21초가 길이를 넘긴다.
- 목표 지표(팔로워·구독자 수)를 저장하지 않는다.

## 2. 설계 결정

### 2.1 포맷 arm

arm은 영상 매개변수 묶음이다. DB 테이블 `marketing_format_arm`에 두고 어드민에서 켜고 끈다. 영상 플랫폼(`instagram_reels`, `youtube_shorts`)에만 적용한다.

| arm key | render_profile | script_style | comment_count | outro_mode | max_duration_sec | 시드 enabled |
|---|---|---|---:|---|---:|---|
| `baseline` | `marketing_hook_a` | `narrative` | 2 | `partner_cta` | (플랫폼 기본) | true |
| `short_reveal` | `marketing_hook_a` | `narrative` | 0 | `vote_reveal` | 25 | false |
| `dilemma` | `marketing_hook_a` | `dilemma` | 0 | `vote_reveal` | 30 | false |
| `kinetic_reveal` | `marketing_hook_b` | `narrative` | 1 | `vote_reveal` | 30 | false |
| `reaction_reveal` | `marketing_hook_c` | `dilemma` | 0 | `vote_reveal` | 30 | false |

- `script_style=dilemma`: 대본이 사건 → 작성자 입장 한 문장 → 상대방 입장 한 문장 → "누구에게 공감하나요"로 끝난다. 결말을 서술하지 않는다.
- `outro_mode=vote_reveal`: 엔드카드가 공감 비율 막대(작성자 n% : 상대방 m%)와 "당신은 어느 쪽인가요?"를 보여 준다. 영상 안에서 보상을 준다.
- `comment_count`: 영상에서 낭독하는 댓글 수. 0이면 댓글 장면이 없다.
- 카카오톡 대화형 포맷은 이번 범위에서 뺀다(대본 스키마가 달라 별도 트랙).

### 2.2 성과 점수 (reward)

게시 후 48시간 기준. 스냅샷은 게시 시각 +36h ~ +96h 구간에서 지표가 있는 가장 이른 것을 쓴다. 게시 시각은 `marketing_job.scheduledPublishAt`, 없으면 `createdAt`.

```
primary     = YouTube views | X impressions | IG reach
engagement  = likes + 2*comments(X는 replies) + 3*(shares + saves + reposts)
engRate     = engagement / max(primary, 1)
viewRatio   = primary / 플랫폼 직전 30건 outcome의 primary 중앙값   (표본 10건 미만이면 1.0)
engRatio    = engRate / 같은 30건의 engRate 중앙값               (중앙값 0이면 engRate>0 ? 2.0 : 1.0, 표본 10건 미만이면 1.0)
reward      = 0.6*min(viewRatio, 10) + 0.4*min(engRatio, 5)
breakout    = viewRatio >= 3.0
```

- 조회가 시험 노출 구간에 묶여 있는 동안에는 `engRatio`가 arm을 가른다. 확산이 일어나면 `viewRatio`가 지배한다.
- 구간 안에 스냅샷이 없고 게시 후 96h가 지나면 outcome을 `MISSING`으로 남기고 통계에서 뺀다.

### 2.3 arm 배정

자동 게시 영상 잡을 만들 때 플랫폼별로 고른다.

1. `marketing.experiment.enabled=false`면 arm을 배정하지 않는다(현재 동작 그대로, `format_arm` = NULL).
2. 켜진 arm 중 최근 30일 배정 수가 `marketing.experiment.min_samples`(기본 8) 미만인 arm이 있으면 그중 배정 수가 가장 적은 것(동률은 key 사전순).
3. 모두 채웠으면 UCB1: `meanReward + sqrt(2*ln(N)/n)`이 가장 큰 arm. `n`은 최근 60일 outcome 수(MISSING 제외), `N`은 그 합. outcome이 0건인 arm은 2단계 규칙으로 취급한다.

### 2.4 주제 편향

`marketing.experiment.topic_bias_enabled=true`면 소재 선택 점수에 배수를 곱한다. 기존 테마 배수(shadow) 자리에 들어가고, 꺼져 있으면 기존 동작 그대로다.

```
cell = (platform, category, hook_emotion)       최근 60일 outcome(MISSING 제외)
m    = ((n*cellMean + 5*platformMean) / (n + 5)) / platformMean      (platformMean <= 0이면 1.0)
multiplier = clamp(m, 0.5, 2.0)
```

사람 확정 없이 매 커밋 틱에 최신 값으로 계산한다(주제 편향은 X·IG 피드에도 적용).

### 2.5 계정 지표

ASM이 X 팔로워, YouTube 구독자·누적 조회·영상 수, Instagram 팔로워를 돌려주고, AS가 하루 1회 `marketing_account_stats`에 적재한다.

### 2.6 게시량

코드로 바꾸지 않는다. 운영 권고값만 남긴다: `marketing.cap.youtube_shorts=2`, `instagram_reels=1`, `x_thread=2`, `instagram_feed=0`. 후보가 하루 6~7편이라 cap을 줄여야 주제 편향이 실제로 고를 여지가 생긴다.

## 전역 제약

- **기본값은 전부 꺼짐.** 새 플래그·새 arm이 꺼져 있거나 새 필드가 없으면 동작·산출물이 지금과 바이트 단위로 같아야 한다. ASM은 dev·prod가 공유하는 단일 인스턴스이고 실계정에 게시한다.
- **배포·재시작·커밋·푸시 금지.** 작업 트리만 바꾼다. 컨테이너 재빌드·재기동, `git commit`, prod·dev 배포는 하지 않는다.
- **LLM 호출 금지, 게시·X/IG 접속 금지.** 테스트는 목(mock)으로만 한다.
- **기존 미커밋 변경을 보존한다.** ASM 5개 파일, WaggleBot 9개 파일(`_frames.py` 투표 막대 포함)에 운영자의 작업이 있다. 되돌리거나 정리하지 않는다.
- AS: 코드에 LLM 오류 시그니처 하드코딩 금지, 새 ENUM은 대문자, DB 시간은 UTC, 필드명은 AS→ASM JSON이 snake_case, ASM→WaggleBot 옵션이 camelCase.
- 다른 Task 소유 파일은 읽기만 한다.

## 계약 (Task 간 인터페이스)

### DB — V132 (`V132__marketing_experiment.sql`)

```sql
ALTER TABLE marketing_job ADD COLUMN format_arm VARCHAR(32) NULL, ADD INDEX idx_marketing_job_format_arm (format_arm);

CREATE TABLE marketing_format_arm (
  arm_key VARCHAR(32) PRIMARY KEY, render_profile VARCHAR(32) NOT NULL, script_style VARCHAR(16) NOT NULL,
  comment_count INT NOT NULL, outro_mode VARCHAR(16) NOT NULL, max_duration_sec INT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0, description VARCHAR(200) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3));
-- §2.1 표의 5행 시드

CREATE TABLE marketing_publication_outcome (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, job_id BIGINT NOT NULL, post_id VARCHAR(32) NOT NULL, platform VARCHAR(40) NOT NULL,
  format_arm VARCHAR(32) NULL, category VARCHAR(20) NULL, hook_emotion VARCHAR(16) NULL,
  status VARCHAR(16) NOT NULL,           -- SCORED | MISSING
  published_at TIMESTAMP(3) NOT NULL, snapshot_at TIMESTAMP(3) NULL,
  primary_metric BIGINT NULL, engagement BIGINT NULL, view_ratio DOUBLE NULL, eng_ratio DOUBLE NULL,
  reward DOUBLE NULL, breakout TINYINT(1) NOT NULL DEFAULT 0, computed_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_outcome_job_platform (job_id, platform), KEY idx_outcome_platform_time (platform, published_at));

CREATE TABLE marketing_account_stats (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, platform VARCHAR(40) NOT NULL, collected_on DATE NOT NULL,
  followers BIGINT NULL, extra_json LONGTEXT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_account_stats (platform, collected_on));
```

`system_setting` 시드: `marketing.experiment.enabled=false`, `marketing.experiment.topic_bias_enabled=false`, `marketing.experiment.min_samples=8`.

### AS Java (패키지 `com.againspring.marketing.experiment`, Task 1이 만든다)

```java
public record FormatArm(String key, String renderProfile, String scriptStyle, int commentCount,
                        String outroMode, Integer maxDurationSec, boolean enabled, String description) {}

public class MarketingExperimentSettings {      // system_setting 읽기·쓰기
    boolean isEnabled(); boolean isTopicBiasEnabled(); int minSamples();
    void setEnabled(boolean v); void setTopicBiasEnabled(boolean v);
}
public class MarketingFormatArmService {
    List<FormatArm> listAll(); Optional<FormatArm> find(String key); FormatArm setEnabled(String key, boolean enabled);
    /** 실험이 꺼져 있거나 켜진 arm이 없으면 empty. §2.3 규칙. */
    Optional<FormatArm> pickForAutoJob(String platform);
}
public class MarketingOutcomeService {
    /** §2.2. 아직 outcome이 없는 게시 완료 잡을 채점한다. 반환 = 새로 만든 행 수. */
    int scorePending();
}
public class MarketingTopicBiasService {
    /** §2.4. 플래그가 꺼져 있으면 1.0. */
    double multiplier(String platform, String category, String hookEmotion);
}
```

- `MarketingJob`에 `formatArm` 필드(컬럼 `format_arm`), `JobResponse`에 `formatArm` 노출.
- `VideoVariantService`(Task 2): 기존 7-인자 `generate`는 그대로 두고, 맨 끝에 `String scriptStyle`을 받는 8-인자 오버로드를 추가한다. `null`·`"narrative"`면 기존 프롬프트와 글자 단위로 같다.

### AS → ASM (`options`, snake_case, 전부 선택 필드)

`format_arm: string`, `comment_count: int(0~2)`, `outro_mode: "partner_cta" | "vote_reveal"`. `max_duration_sec`는 기존 brief 필드(`max_duration_reels_sec`, `max_duration_shorts_sec`)에 arm 값을 넣어 보낸다.

### ASM → WaggleBot (`options`, camelCase, 전부 선택 필드)

`formatArm`, `commentCount`, `outroMode`. WaggleBot `variant_config` 키는 `format_arm`, `comment_count`, `outro_mode`. `format_arm`이 있고 `baseline`이 아니면 ASM은 `externalId`에 `__fa_<arm>`을 붙인다(WaggleBot의 멱등 재사용 방지).

### ASM 계정 지표 API

`GET /api/v1/stats/accounts` (기존 Bearer 인증) →
`{"x": {"followers": int|null, "following": int|null}, "youtube": {"subscribers": int|null, "views": int|null, "videos": int|null}, "instagram": {"followers": int|null, "media_count": int|null}, "errors": {"<platform>": "<사유>"}}`. 플랫폼 하나가 실패해도 200으로 나머지를 돌려준다.

### 어드민 API (Task 1) — `/api/admin/marketing/experiment`

- `GET /` → `{settings:{enabled, topicBiasEnabled, minSamples}, arms:[{key, renderProfile, scriptStyle, commentCount, outroMode, maxDurationSec, enabled, description, assigned30d, outcomes60d, meanReward, breakouts}], topics:[{platform, category, hookEmotion, n, meanReward, multiplier}], accounts:[{platform, collectedOn, followers}]}` (accounts는 최근 60일)
- `PUT /settings` `{enabled?, topicBiasEnabled?}` · `PATCH /arms/{key}` `{enabled}` · `POST /score/run` → `{created:int}`

## Review Focus

1. 실험이 켜졌는데 켜진 arm이 `baseline` 하나뿐일 때: 항상 `baseline`을 고르고 산출물이 지금과 같아야 한다.
2. 같은 사연이 릴스와 쇼츠에 서로 다른 arm으로 나갈 때: 두 잡의 대본·렌더가 섞이지 않아야 한다(WaggleBot 멱등 재사용).
3. 통계 스냅샷이 전부 null인 빈 행일 때: 채점이 그 행을 건너뛰고 지표 있는 행을 써야 한다.
4. 공감 비율이 없거나 0:0인 사연에 `vote_reveal`이 걸릴 때: 막대 없이 질문 문구만 보여야 하고 렌더가 실패하면 안 된다.
5. 실험 프로필(`marketing_hook_b`/`_c`)이 arm 없이 자동 게시로 요청될 때: 지금처럼 거부해야 한다.

## Tasks

### Task 1 — AS 기반 (신규 파일 + 엔티티·DTO·클라이언트)

**소유 파일:** `backend/.../db/migration/V132__marketing_experiment.sql`, `backend/.../marketing/experiment/**`(신규), `domain/marketing/MarketingJob.java`, `domain/marketing/` 신규 엔티티 3개, 신규 리포지토리, `api/admin/AdminMarketingExperimentController.java`(신규), `api/admin/dto/JobResponse.java`, `marketing/AsmClient.java`(`fetchAccountStats()` 추가), `marketing/MarketingPlatformStatsScheduler.java`(일일 수집 뒤 `scorePending()`과 계정 지표 적재 호출), `integration/FlywayMigrationIT`의 개수, 대응 테스트.

- [ ] V132 + 엔티티·리포지토리. `FlywayMigrationIT` 개수 132.
- [ ] `MarketingExperimentSettings`, `MarketingFormatArmService`(§2.3). 테스트: 실험 꺼짐 → empty / arm 하나 → 그 arm / 표본 미달 arm 우선 / UCB가 평균 높은 arm 선택 / outcome 0건 arm 처리.
- [ ] `MarketingOutcomeService`(§2.2). 테스트: 구간 안 가장 이른 지표 스냅샷 선택 / 빈 행 건너뜀 / 96h 경과 MISSING / 표본 10건 미만 ratio 1.0 / breakout 경계 3.0 / 중복 채점 없음.
- [ ] `MarketingTopicBiasService`(§2.4). 테스트: 꺼짐 1.0 / 수축 계산 / clamp 0.5·2.0 / platformMean 0.
- [ ] 계정 지표: `AsmClient.fetchAccountStats()` + 적재(같은 날 재실행은 갱신). ASM 실패 시 경고 로그만.
- [ ] `AdminMarketingExperimentController` + 테스트. 스케줄러 연결.
- [ ] `cd backend && ./gradlew test --tests '*experiment*' --tests '*FlywayMigrationIT*'` 통과.

### Task 2 — 대본 스타일 (`VideoVariantService`)

**소유 파일:** `service/community/VideoVariantService.java`, `VideoVariantServiceTest.java`

- [ ] 8-인자 `generate(..., String scriptStyle)` 추가. `dilemma`면 프롬프트에 §2.1의 대본 구조 지시 블록을 넣는다. 훅 규칙 v2는 유지.
- [ ] 테스트: `null`/`narrative` 프롬프트가 기존과 같음 / `dilemma` 프롬프트에 지시 블록 포함 / 알 수 없는 스타일은 `narrative`로 처리.
- [ ] `./gradlew test --tests '*VideoVariantServiceTest*'` 통과.

### Task 3 — ASM 전달 + 계정 지표 (WSL `~/Data/Again-Spring-Marketing`)

**소유 파일:** `app/domain/brief.py`, `app/media/waggle_client.py`, `app/worker/stats_collect.py` 또는 신규 `app/worker/account_stats.py`, `app/api/` 통계 라우트, 대응 `tests/`.

- [ ] `JobOptions`에 `format_arm`, `comment_count`, `outro_mode` 추가(기본 None).
- [ ] `_brief_to_payload`: 값이 있을 때만 `formatArm`/`commentCount`/`outroMode`를 싣고, `comment_count`가 있으면 보내는 댓글을 그 수로 자른다. `externalId` 접미사 `__fa_<arm>`.
- [ ] 테스트: 새 필드가 없으면 페이로드·`externalId`가 기존과 같음(기존 테스트 그대로 통과) / 필드별 전달 / `baseline`은 접미사 없음.
- [ ] `GET /api/v1/stats/accounts`: YouTube `channels.list(part=statistics, mine=true)`, Instagram Graph `followers_count,media_count`, X는 기존 profile-counts 경로 재사용. 테스트는 HTTP 목.
- [ ] `pytest tests/` 통과(기존 실패가 있으면 그대로 보고).

### Task 4 — WaggleBot 렌더 매개변수 (WSL `~/Data/WaggleBot`)

**소유 파일:** 백엔드 `ExternalIngestService.java`(옵션 → `variant_config` 전달), 워커 `ai_worker/renderer/layout.py`·`_frames.py`·`scene/director.py` 중 필요한 곳, 대응 `worker/test/`.

- [ ] Java: `commentCount`, `outroMode`, `formatArm`을 `variant_config`의 `comment_count`, `outro_mode`, `format_arm`으로 전달. 없으면 키를 넣지 않는다.
- [ ] 워커: `comment_count`가 있으면 낭독 댓글 수 상한으로 쓴다(없으면 기존 2).
- [ ] 워커: `outro_mode="vote_reveal"`이면 엔드카드 문구를 "당신은 어느 쪽인가요?"로 하고 공감 비율 막대를 그린다(미커밋 투표 막대 구현을 재사용). 비율이 없으면 문구만.
- [ ] 워커: `max_duration_sec`가 있고 `comment_count`가 명시된 잡에 한해, 총 길이가 상한+2초를 넘으면 댓글 장면을 뒤에서부터 빼고 다시 계산한다. 키가 없는 기존 잡은 길이 통제를 바꾸지 않는다.
- [ ] 테스트: 키가 없을 때 장면 계획이 기존과 같음 / 각 키 동작 / 비율 없음. LLM·TTS·GPU가 필요한 테스트는 돌리지 않는다.

### Task 5 — AS 배선 (Task 1·2 완료 후)

**소유 파일:** `marketing/MarketingJobService.java`, `marketing/dto/CreateJobRequest.java`, `marketing/holding/MarketingHoldingCommitService.java`, 대응 테스트.

- [ ] `createJob`: 자동 게시 영상 잡이고 호출자가 프로필을 지정하지 않았으면 플랫폼의 `pickForAutoJob`으로 arm을 고른다. arm이 있으면 `renderProfile`·`scriptStyle`·`max_duration_*`·`options.format_arm/comment_count/outro_mode`·`job.formatArm`에 반영한다.
- [ ] arm이 정한 실험 프로필은 자동 게시를 허용한다. arm 없이 들어온 실험 프로필 + 자동 게시는 지금처럼 거부한다.
- [ ] `MarketingHoldingCommitService.toScored`: 주제 편향이 켜져 있으면 테마 배수 대신 `MarketingTopicBiasService.multiplier`를 곱한다. 꺼져 있으면 기존 코드 경로.
- [ ] 테스트: 실험 꺼짐 → 요청 페이로드가 기존과 같음 / arm 반영 / 실험 프로필 거부 유지 / 주제 편향 켜짐·꺼짐.
- [ ] `./gradlew test` 전체 통과.

### Task 6 — 어드민 "실험" 화면 (AS 프런트)

**소유 파일:** `frontend/lib/api/admin/marketingExperiment.ts`(신규), `frontend/components/admin/marketing/ExperimentPanel.tsx`(신규), `frontend/app/(admin)/admin/marketing/page.tsx`(통계 탭에 패널 추가), `frontend/lib/api/admin/marketing.ts`(잡 타입에 `formatArm`), 대응 테스트.

- [ ] 패널 구성: 실험·주제 편향 스위치 / arm 표(켜기, 배정 수, 채점 수, 평균 점수, 확산 건수) / 주제 표(배수 내림차순) / 계정 팔로워 추이 표 / "지금 채점" 버튼.
- [ ] 컴포넌트 테스트(vitest): 목 응답 렌더, 스위치·토글이 올바른 API를 부름.
- [ ] `npm run lint:docs && npm run build && npm run test` 통과.

### Task 7 — 문서 (Task 5와 병렬)

**소유 파일:** `docs/**`만.

- [ ] `docs/_index.md` 트리거맵에 따라 `docs/backend/40-data.md`(V132), `docs/shared/50-api/rest-spec.md`·`docs/shared/marketing/50-api.md`(어드민·ASM API, options 필드), `docs/shared/marketing/10-context.md`·`30-components.md`(실험 구조), `docs/env/40-data.md`(새 설정 키), `docs/frontend/30-components/`를 코드에 맞춘다.
- [ ] `python3 scripts/lint_docs.py` 통과.

## 구현 뒤 남는 운영 절차 (사람이 결정)

1. dev 배포 → e2e → (명시 지시 시) prod 배포. ASM 재빌드와 WaggleBot 백엔드 재빌드·워커 재시작.
2. 테스트 탭에서 새 arm 영상을 눈으로 확인한 뒤 arm을 켠다.
3. `marketing.experiment.enabled`를 켜고 cap을 §2.6 권고값으로 내린다.
4. 채점이 arm당 8건 쌓이면 주제 편향을 켠다.

## 이번 범위 밖

- AI-user 사연 생성 주제에 성과를 되돌리는 것(소재 풀 자체의 편향). 선택 단계 편향의 효과를 본 뒤 별도 트랙.
- 카카오톡 대화형 등 대본 스키마가 다른 포맷, X 스레드·IG 피드의 포맷 arm.
