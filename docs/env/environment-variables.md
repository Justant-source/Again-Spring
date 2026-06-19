# 환경 변수

## Source of truth

- `env/.env.example` (로컬용 — 최소 항목)
- `env/.env.dev.example` (dev 서버)
- `env/.env.prod.example` (prod 서버)
- `backend/src/main/resources/application*.yml` (런타임 키)

`.env.dev` / `.env.prod` 파일은 gitignored — 호스트에서 생성.

## 분류별 항목

### MariaDB

| 변수 | 사용처 | dev 기본 | prod |
|---|---|---|---|
| `MARIADB_ROOT_PASSWORD` | mariadb 컨테이너 부트스트랩 | `changeme_dev` | **필수** |
| `MARIADB_DATABASE` | DB 이름 | `againspring_dev` | `againspring` |
| `MARIADB_USER` | 앱 접속 계정 | `againspring` | `againspring` |
| `MARIADB_PASSWORD` | 앱 접속 비밀번호 | `changeme_dev` | **필수** |

### JWT

| 변수 | 사용처 | dev 기본 | prod |
|---|---|---|---|
| `JWT_SECRET` | `JwtService` 서명 키 (≥256bit) | placeholder | **필수** |

생성 예: `openssl rand -base64 32`

### LLM 워커 (`againspring-llm` 컨테이너)

| 변수 | 사용처 | 기본값 |
|---|---|---|
| `LLM_PROVIDER` | `LLMProvider` 빈 선택 (backend) | `remote` |
| `LLM_JURY_PROVIDER` | 배심원 생성 provider (`remote` \| `mock`) | `remote` (dev) |
| `LLM_WORKER_URL` | backend → llm-worker 접속 URL | `http://againspring-llm:8090` |
| `LLM_DEFAULT_TIMEOUT_MS` | LLM 호출 타임아웃 (ms) | `120000` |
| `CODEX_BIN` | llm-worker Codex CLI 실행 파일명 | `codex` |
| `CODEX_MODEL` | llm-worker `codex exec --model` 인자 | `gpt-5.4` |
| `CLAUDE_BIN` | 레거시 CLI 경로 호환용 | `claude` |
| `CLAUDE_MODEL` | 레거시 모델명 호환용 | `claude-haiku-4-5-20251001` |
| `REPORT_LLM_MODEL` | llm-worker 리포트 모델 | `claude-sonnet-4-6` |
| `CLAUDE_HOST_CONFIG_DIR` | bind mount 원본 (`→ /root/.claude`) — **llm-worker에 마운트** | dev: `/home/justant/.claude` / prod: `/root/.claude` |
| `LLM_POOL_SIZE` | ThreadPoolExecutor 상한 | `100` |
| `LLM_QUEUE_CAPACITY` | LinkedBlockingQueue 용량 | `500` |
| `LLM_QUEUE_WAIT_TIMEOUT_MS` | 큐 대기 최대 시간 (ms) | `30000` |
| `ANTHROPIC_API_KEY` | 레거시 clcocloud API 키 (현재 런타임 미사용) | `""` |

API 키 없이 동작 — 호스트의 `~/.claude` 세션을 **llm-worker** 컨테이너가 공유. backend 컨테이너에는 마운트 불필요.

긴급 롤백: `LLM_PROVIDER=claude-code`로 변경 → backend에서 in-process 직접 호출 (backend Dockerfile revert 필요).

### AI 유저 오케스트레이션

| 변수 | 사용처 | dev 기본 | prod |
|---|---|---|---|
| `AI_USER_ENABLED` | AI 유저 행동 활성화 (오케스트레이터) | `false` | **필수** |
| `AI_USER_PERSONA_TARGET` | 🚨 **2026-06-10 변경**: 일일 총량 fallback (admin UI 목표 > 0일 때는 무시됨) | `100` | 변경 필요시 admin UI |

> ⚠️ **2026-06-10 변경**: 일일 5개 타입(posts/comments/replies/votes/likes) 목표는 `admin UI(/admin/ai-user)`에서 설정합니다.
> 총량은 **UI 목표 합 × 1.1**로 자동 계산됩니다.
> 이 env var는 **admin UI 목표가 모두 0일 때만 fallback**으로 동작합니다.
> 운영 중 목표 조정은 **admin UI를 사용**하세요 (재배포 불필요).

#### 문체·반복 가드 (orchestrator)

| 변수 | 사용처 | 기본 |
|---|---|---|
| `AI_USER_REPETITION_THRESHOLD` | 생성문 vs 최근 출력 2-gram Jaccard 임계 — 초과 시 1회 재생성 (llm.md §15) | `0.45` |
| `AI_USER_MIN_POST_CHARS` | 글 최소 길이 — 미달 시 1회 재생성 (제목만 남는 절단 방어, llm.md §6.3) | `50` |

#### AI-User ML 서비스 연동 (Best-of-N 리랭킹, WSL 100.115.252.61:8201)

| 변수 | 사용처 | 기본 |
|---|---|---|
| `AI_USER_ML_BASE_URL` | ML 서비스 base URL (WSL Tailscale) | `http://100.115.252.61:8201` |
| `AI_USER_ML_API_TOKEN` | Bearer 인증 토큰 (ML → AS 단방향) | `aiuser-ml-api-token-dev-2026` |
| `AI_USER_ML_ENABLED` | Best-of-N 리랭킹 활성화 (false=단일초안 기존 경로) — 수집은 `AI_USER_ML_COLLECT`로 별도 제어 | `false` |
| `AI_USER_ML_ENABLED_COMMUNITIES` | 선택적 리랭킹 대상 community 목록 (쉼표 구분 `voice_type`, 비어 있으면 전역 적용). `AI_USER_ML_ENABLED=true`일 때만 의미 있음 | `""` |
| `AI_USER_ML_COLLECT` | AI negative 코퍼스 수집 단독 활성화. `AI_USER_ML_ENABLED`(리랭킹)와 독립. 판별기 AUC가 낮을 때(0.55 미만) 수집만 켜고 리랭킹은 OFF 유지해야 출력 악화 방지 | `false` |
| `AI_USER_ML_BEST_OF_N` | 초안 생성 수 (활성화 시) | `4` |
| `AI_USER_ML_TIMEOUT_MS` | ML 서비스 응답 타임아웃 (ms) | `500` |

### AI 유저 LLM 생성 (`againspring-llm-ai-user` 컨테이너, 8092)

backend의 `againspring-llm`(8090, 채팅·배심원)과 **별개 서비스**. 글/댓글/대댓글 생성 전용.
`backend=API` 경로는 `ANTHROPIC_API_KEY`/`ANTHROPIC_BASE_URL`(DB `system_setting` 우선)로 clcocloud 프록시 호출.

| 변수 | 사용처 | dev/기본 | 비고 |
|---|---|---|---|
| `AI_USER_LLM_MODEL` | `CLAUDE_MODEL` — 댓글/대댓글 기본 모델 | `claude-haiku-4-5-20251001` | |
| `LLM_POST_MODEL` | 글(POST)+partner 전용 모델 오버라이드 | `claude-sonnet-4-6` | 빈 값=`CLAUDE_MODEL` 폴백 (llm.md §6.3) |
| `LLM_API_PROMPT_CACHING` | user-block `cache_control` 캐싱 | `true` | clcocloud 간헐 무시 — "되면 보너스" (llm.md §16) |
| `LLM_API_CACHE_TTL` | 캐시 TTL `5m`(GA) \| `1h`(beta) | `5m` | ⚠️ `1h`은 clcocloud Kiro 오라우팅 유발 — 직접 API 전용 |
| `LLM_API_REFUSAL_RETRIES` | clcocloud 거절(PROVIDER_ERROR) 재시도 횟수 | `2` | llm.md §18 |
| `LLM_API_REFUSAL_FALLBACK_MODEL` | 재시도 소진 시 폴백 모델 (거절 0% 실측) | `claude-sonnet-4-6` | 빈 값=폴백 비활성 |
| `SELF_CRITIQUE_ENABLED` | 생성 후 자기비평 루프 | `true` | |
| `SELF_CRITIQUE_THRESHOLD` | 비평 통과 점수 (7점 만점) | `5` | |
| `SELF_CRITIQUE_EXTRA_CLICHES` | 추가 AI 상투구 (쉼표 구분 리터럴) — 무배포 등록 | `""` | llm.md §15 |

### OAuth2

| 변수 | 비고 |
|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | dev: 선택 / prod: 필수 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | dev: 선택 / prod: 필수 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | dev: 선택 / prod: 필수 |

frontend도 build-time ARG로 `NEXT_PUBLIC_{GOOGLE,KAKAO,NAVER}_CLIENT_ID`를 받아 정적 인라인.

### App URL

| 변수 | 사용처 | dev | prod |
|---|---|---|---|
| `APP_URL` | OAuth `redirect_uri` 베이스 + frontend `NEXT_PUBLIC_APP_URL` build ARG | `https://dev.againspring.net` | `https://againspring.net` |

### Email (Spring Mail) — 발신자 `againspring2026@gmail.com` 단일화

| 변수 | 사용처 | dev | prod |
|---|---|---|---|
| `MAIL_HOST` | SMTP 호스트 | `smtp.gmail.com` | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP 포트 | `587` | `587` |
| `MAIL_USERNAME` | 발신 계정 | `againspring2026@gmail.com` | `againspring2026@gmail.com` |
| `GMAIL_APP_PASSWORD` | Gmail 앱 비밀번호 16자 | 선택 (없으면 이메일 발송 비활성) | 필수 |

`GMAIL_APP_PASSWORD`: againspring2026@gmail.com → Google 계정 → 2단계 인증 → 앱 비밀번호 발급.
이메일 인증·비밀번호 재설정 모두 단일 발신자. SMTP 미설정 시 dev에서는 로그로 코드 출력.

### ASM (Again-Spring-Marketing 서비스)

| 변수 | 사용처 | dev | prod |
|---|---|---|---|
| `ASM_BASE_URL` | Again-Spring-Marketing 게이트웨이 (HTTP) | `http://100.115.252.61:8200` | 비워둠 |
| `ASM_API_TOKEN` | ASM 인증 토큰 (AS → ASM) | 필요시 입력 | 비워둠 |
| `ASM_CALLBACK_TOKEN` | ASM이 콜백 인증에 사용 (ASM → AS) | `asm-callback-token-dev` | **필수 (비밀값)** |
| `ASM_CALLBACK_BASE_URL` | AS가 jobCreate 요청 시 포함, ASM이 콜백 URL 생성용 | `http://100.81.189.92:8090` | `http://100.81.189.92:8091` |
| `ASM_ENABLED` | 마케팅 기능 활성화 | `false` (기본) | `false` (변경 금지) |

마케팅 관련 환경변수는 ASM 프로젝트로 이동. ASM_BASE_URL/ASM_API_TOKEN/ASM_ENABLED 참조.
- `ASM_CALLBACK_TOKEN`: Bearer 토큰. `POST /api/internal/marketing/callback` 인증용. dev 기본값은 `asm-callback-token-dev`.
- `ASM_CALLBACK_BASE_URL`: AS의 외부 접근 URL. ASM이 콜백을 보낼 대상 도메인. 통상 LB/nginx 외부 IP:port.

### 위기 알림 (선택사항)

| 변수 | 사용처 | dev | prod |
|---|---|---|---|
| `CRISIS_WEBHOOK_URL` | 위기 신호 감지 시 webhook URL | 선택 | 선택 |
| `CRISIS_EMAIL` | 위기 알림 이메일 | 선택 | 선택 |

## prod 필수 항목 체크리스트

prod는 `application-prod.yml`이 모든 키에 기본값 없이 환경변수를 강제합니다. 누락 시 부팅 실패.

- [ ] `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`
- [ ] `JWT_SECRET`
- [ ] `GOOGLE_*`, `KAKAO_*`, `NAVER_*` (전체 OAuth)
- [ ] `MAIL_USERNAME` (againspring2026@gmail.com), `GMAIL_APP_PASSWORD`
- [ ] `CLAUDE_HOST_CONFIG_DIR` 디렉토리가 호스트에 존재 + `claude` 1회 로그인 완료 (llm-worker 컨테이너가 사용)
- [ ] `LLM_WORKER_URL` (`http://againspring-llm:8090`)
- [ ] `LLM_POOL_SIZE`, `LLM_QUEUE_CAPACITY`, `LLM_QUEUE_WAIT_TIMEOUT_MS` (기본값 사용 가능)

## 변경 시 절차

1. `.env.dev.example` / `.env.prod.example` 갱신 (committed)
2. 호스트의 `.env.dev` / `.env.prod`에 실제 값 반영
3. `docker compose ... up -d --build`로 재기동 (env는 build/run 양쪽에 영향)
