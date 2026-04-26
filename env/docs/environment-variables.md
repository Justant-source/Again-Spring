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

### Claude Code CLI (LLM 브릿지)

| 변수 | 사용처 | 기본값 |
|---|---|---|
| `LLM_PROVIDER` | `LLMProvider` 빈 선택 | `claude-code` |
| `CLAUDE_BIN` | CLI 실행 파일명 | `claude` |
| `CLAUDE_MODEL` | `--model` 인자 | `claude-haiku-4-5-20251001` |
| `CLAUDE_HOST_CONFIG_DIR` | bind mount 원본 (`→ /root/.claude`) | dev: `/home/justant/.claude` / prod: `/root/.claude` |
| `ANTHROPIC_API_KEY` | claude CLI 인증 fallback (정상 케이스에선 비워둠) | `""` |

API 키 없이 동작 — 호스트의 `~/.claude` 세션을 컨테이너가 공유.

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

### Email (Spring Mail)

| 변수 | 사용처 | dev | prod |
|---|---|---|---|
| `MAIL_HOST` | SMTP 호스트 | `smtp.gmail.com` | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP 포트 | `587` | `587` |
| `MAIL_USERNAME` | 발신 계정 | 선택 (없으면 이메일 인증 비활성) | 필수 |
| `MAIL_PASSWORD` | 16자리 Gmail App Password | 선택 | 필수 |

이메일 인증 흐름은 `EmailVerificationService`가 처리. SMTP 미설정 시 회원가입 인증 코드 발송 비활성.

## prod 필수 항목 체크리스트

prod는 `application-prod.yml`이 모든 키에 기본값 없이 환경변수를 강제합니다. 누락 시 부팅 실패.

- [ ] `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`
- [ ] `JWT_SECRET`
- [ ] `GOOGLE_*`, `KAKAO_*`, `NAVER_*` (전체 OAuth)
- [ ] `MAIL_USERNAME`, `MAIL_PASSWORD`
- [ ] `CLAUDE_HOST_CONFIG_DIR` 디렉토리가 호스트에 존재 + `claude` 1회 로그인 완료

## 변경 시 절차

1. `.env.dev.example` / `.env.prod.example` 갱신 (committed)
2. 호스트의 `.env.dev` / `.env.prod`에 실제 값 반영
3. `docker compose ... up -d --build`로 재기동 (env는 build/run 양쪽에 영향)
