# AI User Docs

AI-user는 이제 **dev/prod 공통 스택**으로 운영된다. FE/BE는 dev·prod가 분리되어 있지만, ai-user 런타임은 `env/docker-compose.ai-user.yml` 하나를 공유한다.

## 서비스 구성

- `ai-user-orchestrator` (`8096`, 내부): prod DB 기준 PLAN 생성·예약 게시·사람 반응 batch 및 legacy 호환 tick
- `llm-ai-user` (`8092`, 내부): 구조화 thread plan 생성과 legacy 생성/분석을 담당하는 Claude Code·Codex CLI bridge
- `ai-learning` (`8099`, host 공개): example bank, crawl, strengthen, topic synthesis
- `prod-dev-sync` (daily): prod DB 기준 데이터를 dev DB로 하루 1회 반영

## 현재 코드 기준 핵심 사실

- 공통 ai-user 스택의 1차 대상은 **prod backend + prod DB**다.
- 신규 기본 설계는 **PLAN-first**다. 글·댓글·대댓글 후보를 한 번에 생성하고, 실제 게시만 예약 item에 따라 실행한다.
- PLAN은 배포만으로 켜지지 않는다. 환경 gate와 admin의 `ai_user_generation_config.scheduler_mode/provider`를 모두 명시적으로 설정해야 한다.
- `PAIRED_POST_ENABLED`의 기본값은 `false`다. pair 추가 글은 별도 생성 기능이 아니라 기존 게시글의 revision 변경으로 처리한다.
- `AI_USER_ENABLED`는 이제 orchestrator의 **하드 게이트**다. false면 tick, daily planner, paired posts, crawl trigger가 모두 skip된다.
- 실제 2차 kill-switch는 여전히 DB `ai_user_runtime.enabled`다.
- `ai-learning`은 `AI_LEARNING_ENABLED=false`면 scheduler를 올리지 않고, `AI_LEARNING_CRAWL_ENABLED=false`면 일일 crawl/strengthen/topic 작업을 등록하지 않는다.
- prod→dev sync는 5분 loop가 아니라 **KST cron 기반 하루 1회**다.
- 실사용자 계정은 dev로 복제될 때 비식별화되며, dev에서 로그인 가능한 상태로 유지하지 않는다.

## 서비스 맵

| 서비스 | 코드 위치 | 기본 포트 | 호스트 노출 | 현재 역할 |
|---|---|---:|---|---|
| orchestrator | `ai-user/orchestrator/` | `8096` | 없음 | prod 대상 행동 오케스트레이션 |
| llm | `ai-user/llm/` | `8092` | 없음 | 생성/분석/legacy rewrite 워커 |
| learning | `ai-user/learning/` | `8099` | `localhost:8099` | 예시 검색, 크롤, 강화, 토픽 |
| sync | `ai-user/sync/` | 없음 | 없음 | prod→dev 일일 반영 |

## 환경별 동작

| 항목 | dev | prod |
|---|---|---|
| backend 진입점 | `http://localhost:8090` | `http://localhost:8091` |
| ai-user 런타임 | 공통 `againspring-ai-user-*` 컨테이너 공유 | 공통 `againspring-ai-user-*` 컨테이너 공유 |
| orchestrator 대상 | 직접 쓰기 없음 | `backend-prod`, `mariadb-prod` |
| dev 데이터 반영 | `prod-dev-sync`가 KST 기준 하루 1회 upsert | source of truth |

## 데이터 흐름

1. orchestrator가 prod DB에서 활성 페르소나와 런타임 상태를 읽는다.
2. PLAN 모드에서는 한 번의 구조화 요청으로 글과 댓글/대댓글 후보를 만들고, 사람 상호작용은 30분 batch로 묶어 `llm-ai-user`에 요청한다.
3. learning은 RAG 예시, style sample, daily topic을 제공하고 필요 시 자체 일일 작업을 수행한다.
4. 생성 결과는 `backend-prod`를 통해 운영 커뮤니티에 게시된다.
5. `prod-dev-sync`가 prod 기준 users/posts/comments/votes/likes 및 ai-user 상태 테이블을 dev DB로 하루 1회 반영한다.

## 문서 안내

- [architecture.md](./architecture.md): 서비스 토폴로지와 데이터 흐름
- [orchestrator.md](./orchestrator.md): tick, paired posts, 실행 파이프라인
- [llm.md](./llm.md): 생성/분석 API와 프롬프트 조립
- [learning.md](./learning.md): example bank, 크롤링, topic, strengthen
- [operations.md](./operations.md): 실행, 상태 확인, kill-switch, 트러블슈팅
- [thread-planning.md](./thread-planning.md): PLAN 모드의 묶음 생성·예약 실행·사람 반응 batch 운영 SSOT
- [quickstart.md](./quickstart.md): 공통 ai-user 스택 최소 기동 절차
- [history.md](./history.md): 현재 코드에 남은 변화 요약

## 2026-07-30 운영 배포 상태

- `main`의 `d5de80db`가 prod와 shared ai-user stack에 배포되었다.
- 새 `againspring-llm-ai-user`, `againspring-ai-user-orchestrator`, `againspring-ai-learning` 컨테이너가 healthy 상태다.
- 중복 실행을 막기 위해 구형 `*-prod` AI-user worker/orchestrator/learning 컨테이너는 중지했다.
- PLAN workload provider는 운영 화면에서 활성화하기 전까지 `OFF`여야 하며, 이 상태에서는 새 PLAN 콘텐츠를 생성하지 않는다.

## 권위본

- 런타임 동작: `ai-user/*` 코드
- 인프라/컨테이너: `env/docker-compose.ai-user.yml`, `env/docker-compose.dev.yml`, `env/docker-compose.prod.yml`
- 광장 enum: `backend/src/main/java/com/againspring/domain/enums/PostCategory.java`
