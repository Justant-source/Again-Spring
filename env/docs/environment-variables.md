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
| `CLAUDE_BIN` | llm-worker CLI 실행 파일명 | `claude` |
| `CLAUDE_MODEL` | llm-worker `--model` 인자 (채팅) | `claude-haiku-4-5-20251001` |
| `REPORT_LLM_MODEL` | llm-worker 리포트 모델 | `claude-sonnet-4-6` |
| `CLAUDE_HOST_CONFIG_DIR` | bind mount 원본 (`→ /root/.claude`) — **llm-worker에 마운트** | dev: `/home/justant/.claude` / prod: `/root/.claude` |
| `LLM_POOL_SIZE` | ThreadPoolExecutor 상한 | `100` |
| `LLM_QUEUE_CAPACITY` | LinkedBlockingQueue 용량 | `500` |
| `LLM_QUEUE_WAIT_TIMEOUT_MS` | 큐 대기 최대 시간 (ms) | `30000` |
| `ANTHROPIC_API_KEY` | claude CLI 인증 fallback (정상 케이스에선 비워둠) | `""` |

API 키 없이 동작 — 호스트의 `~/.claude` 세션을 **llm-worker** 컨테이너가 공유. backend 컨테이너에는 마운트 불필요.

긴급 롤백: `LLM_PROVIDER=claude-code`로 변경 → backend에서 in-process 직접 호출 (backend Dockerfile revert 필요).

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
| `ASM_API_TOKEN` | ASM 인증 토큰 | 필요시 입력 | 비워둠 |
| `ASM_ENABLED` | 마케팅 기능 활성화 | `false` (기본) | `false` (변경 금지) |

마케팅 관련 환경변수는 ASM 프로젝트로 이동. ASM_BASE_URL/ASM_API_TOKEN/ASM_ENABLED 참조.

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
