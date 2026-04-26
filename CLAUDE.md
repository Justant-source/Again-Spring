# CLAUDE.md — 다시봄 프로젝트 개발 가이드

**프로젝트**: 다시봄 · Again Spring  
**도메인**: `dev.againspring.net` (dev) / `againspring.net`, `www.againspring.net` (prod)  
**진행 상황**: 백엔드 전체 구현 완료, FE-BE 통합 및 배포 준비 중  
**기준일**: 2026-04-26

---

## 🎯 프로젝트 한 줄 요약

싸운 두 사람 사이에서 AI가 양쪽 이야기를 중립적으로 정리해 관계 회복을 돕는 웹앱.  
FE는 Next.js 14 MSW 프로토타입, BE는 Spring Boot 3.3 + **MariaDB 11** + Claude Code LLM 브릿지.

---

## 🏗️ 작업 범위 분리 원칙

### 작업 위치 규칙

| 작업 범위 | 디렉토리 | 주요 문서 |
|---|---|---|
| **FE 기능/UI** | `frontend/` | `shared/docs/v1/API_SPEC.md` |
| **BE 기능/API** | `backend/` | `shared/docs/v1/API_SPEC.md` |
| **LLM 브릿지** | `backend/src/main/java/.../llm/` | `shared/docs/v1/LLM_BRIDGE_ARCHITECTURE.md` |
| **공유 타입/스키마** | `shared/` | `shared/docs/v1/DATABASE_SCHEMA.md` |
| **인프라** | `infra/` | `infra/docker-compose.yml` |

### 절대 규칙

1. **FE는 Claude Code를 직접 호출하지 않음**  
   → 모든 LLM 요청은 BE 경유 (REST API)

2. **BE만 Claude Code를 수행**  
   → ClaudeCodeBridge: `backend/src/main/java/.../llm/bridge/ClaudeCodeBridge.java`

3. **금지어/위기 키워드 확인 필수**  
   → 코드/프롬프트 수정 시 `shared/docs/v1/FORBIDDEN_WORDS.md` 참조

4. **🚨 PROD 배포 절대 규칙 — 위반 금지**  
   → 명시적으로 "prod에 배포해줘" 지시가 없는 한 prod 환경에 절대 배포하지 않음  
   → 배포 순서: **dev 배포 → commit & push (main 브랜치) → prod 배포**  
   → prod에는 반드시 main 브랜치 기준으로만 배포

5. **환경별 격리**  
   → dev: `infra/docker-compose.dev.yml` + `infra/.env.dev`  
   → prod: `infra/docker-compose.prod.yml` + `infra/.env.prod`  
   → `.env.prod`는 절대 git에 커밋 금지

---

## 📋 문서 위치 맵

> **v1/** = 안정적 핵심 문서 · **v2/** = 리디자인/기획 문서

### API / 스키마
- `shared/docs/v1/API_SPEC.md` — REST API 전체 명세 (엔드포인트, 요청/응답 스키마)
- `shared/docs/v1/DATABASE_SCHEMA.md` — MariaDB 테이블 설명

### LLM / AI
- `shared/docs/v1/LLM_BRIDGE_ARCHITECTURE.md` — Claude Code 프로세스 풀, 에러 처리, PromptSanitizer
- `shared/docs/v1/SYSTEM_PROMPTS.md` — Gottman + NVC 프롬프트 원본
- `shared/docs/v1/RATIO_CALCULATION.md` — 화해 기여도 계산 규칙

### 기획 / 정책
- `shared/docs/v1/FORBIDDEN_WORDS.md` — 금지어 · 위기 키워드 (필수!)
- `shared/docs/v1/CATEGORIES.md` — 갈등/관계 카테고리 정의
- `shared/docs/v1/ONBOARDING_MAPPING.md` — 온보딩 Q&A → 소통 스타일 매핑
- `shared/docs/v1/TERMS_OF_SERVICE.md` — 서비스 이용약관
- `shared/docs/v1/PSYCHOLOGY_MODEL_RATIONALE.md` — 심리학 모델 채택 근거

### FE 개발
- `shared/docs/v1/MOCK_SCENARIOS.md` — MSW 목업 시나리오
- `.request/design/` — UI 디자인 핸드오프 에셋 (HTML/JSX/CSS)

### 리디자인 / 기획 문서 (v2)
- `shared/docs/v2/ONBOARDING_V2.md` — 온보딩 v2 기획
- `shared/docs/v2/RESULT_CARDS_REDESIGN.md` — 결과 카드 리디자인
- `shared/docs/v2/SOLO_FIRST_REDESIGN.md` — Solo-first 리디자인
- `shared/docs/v2/REPAIR_DRIP_CAMPAIGN.md` — 관계 회복 드립 캠페인
- `shared/docs/v2/REFINEMENT_WORK_ORDER.md` — 개선 작업 지시서
- `shared/docs/v2/KAKAO_VIRAL_ASSETS.md` — 카카오 바이럴 에셋

---

## ⚠️ 금지어 및 법적 리스크 (필수 숙지)

### 절대 금지어 (UI 전면 차단)

**Level 1 법률 용어** (변호사법 저촉):
- "과실비율" → 대체: "화해 기여도"
- "판결", "판사", "심판" → 대체: "결과", "중재자"
- "유죄", "무죄", "증거", "판단" → 사용 금지
- "가해자", "피해자" → 사용 금지 (낙인)
- "고소", "소송" → 사용 금지

**Level 2 진단명/임상 용어** (악용 가능):
- "나르시시스트", "소시오패스", "가스라이팅", "PTSD", "트라우마" → 사용 금지
- 대체: 구체적 행동 기술 ("대화 중 거리를 두고 싶어하시는 편" 등)

**Level 3 판결/승패** (관계 파국):
- "이겼다/졌다", "맞다/틀렸다", "승자/패자" → 사용 금지
- "헤어지세요", "절교", "손절" → 사용 금지

### 위험 키워드 (세션 즉시 중단)

- **폭력**: "때리", "폭행", "폭력", "구타"
- **성폭력**: "강간", "성폭행"
- **자해**: "죽고 싶", "자살", "자해", "목 매"
- **아동학대**: "아이를 때", "아동학대"

감지 시 → Crisis Resource 모달 표시 (1366, 1393, 132 등 핫라인)

### 검증 방법

```bash
cd frontend
npm run lint:words    # 금지어 자동 스캔

# BE: PromptSanitizer (safety/KeywordGuard.java) 자동 적용
```

---

## 🔌 로컬 개발 명령

### 인프라 (DB)

```bash
cd /home/justant/Data/Again-Spring/infra
docker compose up -d      # MariaDB 11 시작 (localhost:3306)
docker compose logs -f    # 로그 확인
docker compose down       # 종료
```

### BE 개발

```bash
cd /home/justant/Data/Again-Spring/backend
./gradlew bootRun         # localhost:8080
```

환경변수 (dev 프로파일 기본값 자동 적용):

```bash
DB_URL=jdbc:mariadb://localhost:3306/againspring?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_USER=againspring
DB_PASSWORD=changeme
JWT_SECRET=dev_secret_key_change_in_prod
CLAUDE_BIN=/path/to/claude
```

### FE 개발

```bash
cd /home/justant/Data/Again-Spring/frontend
npm install
npm run dev          # localhost:3000 (MSW 자동 활성화)
npm run lint:words   # 금지어 검사
npm run build        # 프로덕션 빌드
```

### 헬스 체크

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

---

## 🧠 LLM 브릿지 운영 주의사항

### ClaudeCodeBridge 설계 원칙

- **모델**: `claude-haiku-4-5-20251001` (기본). `CLAUDE_MODEL` 환경변수로 변경 가능.
- **호출**: `claude --print --model <model> "<prompt>"` (프롬프트는 인자, stdin 미사용)
- **프로세스 풀**: `Semaphore(3)` — 동시 최대 3개 Claude 프로세스
- **타임아웃**: 60초 (Haiku 평균 응답 ~3초, 안전 마진)
- **Fallback**: Claude 불가 시 `FallbackResponses` 기본 응답 반환

### 인증 방식 (API 키 없이)

Claude Code CLI 자체 인증을 활용. 호스트의 `~/.claude` 디렉토리를 컨테이너 `/root/.claude`로 볼륨 마운트하여 호스트 로그인 세션 공유.

```yaml
# docker-compose.dev.yml / docker-compose.prod.yml
backend-dev:
  volumes:
    - ${CLAUDE_HOST_CONFIG_DIR:-/home/justant/.claude}:/root/.claude
```

호스트에서 최초 1회 `claude` 명령으로 로그인 후, 컨테이너는 동일한 세션을 사용. ANTHROPIC_API_KEY 불필요.

### 컨테이너에 Claude CLI 설치

`backend/Dockerfile`의 runtime stage:
```dockerfile
RUN apk add --no-cache nodejs npm \
 && npm install -g @anthropic-ai/claude-code
```

### 보안 규칙

```
❌ 위험: 사용자 입력을 프롬프트에 직접 삽입
  claude --print "사용자의 말: ${userInput}"

✅ 안전: PromptSanitizer → 구조화된 형태로 삽입
  sanitized = sanitizer.sanitize(userInput);
  prompt = systemPrompt + "\n<user_input>\n" + sanitized + "\n</user_input>";
```

### 프롬프트 프레이밍 주의

Claude Code CLI는 SW 엔지니어링 작업에 최적화돼 있어 비기술 조언을 거부할 수 있음. 다시봄의 system prompt(`shared/prompts/system.md`)는 NVC 재구성/구조화 출력 형태로 프레임돼 있어 정상 동작.

자세한 설계는 `shared/docs/v1/LLM_BRIDGE_ARCHITECTURE.md` 참조.

---

## 🧪 테스트 정책

| 계층 | 대상 | 목표 커버리지 | 비고 |
|---|---|---|---|
| **Unit** | Service | 80% | 비즈니스 로직 |
| | Controller | 70% | 라우팅, 입력 검증 |
| | LLM Bridge | 90% | 에러 처리 중점 |
| **Integration** | API | 80% | FE/BE 연동 |
| **Security** | Sanitizer | 100% | 금지어, 프롬프트 주입 |
| | Crisis Guard | 100% | 위험 키워드 감지 |

```bash
# BE
cd backend
./gradlew test

# FE
cd frontend
npm run test
```

---

## 🌐 배포 / 환경 변수

### 환경 구분

| 환경 | 도메인 | compose 파일 | env 파일 | nginx 포트 |
|---|---|---|---|---|
| **로컬 개발** | localhost | `docker-compose.yml` | — | — |
| **서버 dev** | `dev.againspring.net` | `docker-compose.dev.yml` | `.env.dev` | 8090 |
| **서버 prod** | `againspring.net` | `docker-compose.prod.yml` | `.env.prod` | 8091 |

### 컨테이너 명명 규칙

| 컨테이너 | dev | prod |
|---|---|---|
| MariaDB | `againspring-mariadb-dev` | `againspring-mariadb-prod` |
| Backend | `againspring-backend-dev` | `againspring-backend-prod` |
| Frontend | `againspring-frontend-dev` | `againspring-frontend-prod` |
| Nginx | `againspring-nginx-dev` | `againspring-nginx-prod` |

### 🚀 배포 명령

#### 1단계: dev 배포 (항상 먼저)

```bash
cd /home/justant/Data/Again-Spring/infra

# env 파일 준비 (최초 1회)
cp .env.dev.example .env.dev
vi .env.dev  # 실제 값 입력

# 빌드 & 실행
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# 확인
docker compose -f docker-compose.dev.yml ps
curl http://localhost:8090/api/health
```

#### 2단계: commit & push to main

```bash
git add -A
git commit -m "feat: 변경 내용 요약"
git push origin main
```

#### 3단계: prod 배포 (명시적 지시 시에만)

```bash
cd /home/justant/Data/Again-Spring/infra

# env 파일 준비 (최초 1회)
cp .env.prod.example .env.prod
vi .env.prod  # 실제 값 입력 (기본값 없음, 전부 필수)

# 빌드 & 실행
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 확인
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8091/api/health
```

### Cloudflare Tunnel 라우팅

```
dev.againspring.net  →  localhost:8090
againspring.net      →  localhost:8091
www.againspring.net  →  localhost:8091
```

상세 설정: `infra/cloudflare/tunnel.md` 참조.

### 환경 변수 (`.env.dev` / `.env.prod`)

전체 항목은 `infra/.env.dev.example` / `infra/.env.prod.example` 참조. 주요 항목:

```bash
# DB
MARIADB_ROOT_PASSWORD=...
MARIADB_DATABASE=againspring_dev
MARIADB_USER=againspring
MARIADB_PASSWORD=...

# JWT
JWT_SECRET=...

# Claude Code CLI (API 키 불필요 — 호스트 ~/.claude 마운트)
LLM_PROVIDER=claude-code
CLAUDE_BIN=claude
CLAUDE_MODEL=claude-haiku-4-5-20251001
CLAUDE_HOST_CONFIG_DIR=/home/justant/.claude

# OAuth2 (Google만 사용 중)
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# Email (Gmail App Password)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...@gmail.com
MAIL_PASSWORD=...   # 16자리 App Password

# App URL (소셜 로그인 redirect_uri 기준)
APP_URL=https://dev.againspring.net
```

---

## 📊 현재 진행 상황

- ✅ 모노레포 구조 (frontend/, backend/, shared/, infra/)
- ✅ 프론트엔드 (Next.js 14 — MSW 프로토타입 + 실제 API 연동)
- ✅ 백엔드 구현 완료
  - ✅ Spring Boot 3.3 + Java 21 + Gradle Kotlin DSL
  - ✅ MariaDB 11 (JPA + Flyway V1~V5)
    - V1: 초기 스키마
    - V2: OAuth + Guest 세션 지속성 (`provider`, `provider_id`, `guest_sessions`)
    - V3: 이메일 인증 (`email_verifications`)
    - V4: 보안 테이블
    - V5: 관계 온도 컬럼 제거 (`temperature`, `average_temperature`, `temperature_history`)
  - ✅ JWT 인증 (회원가입 / 로그인 / 게스트 / Google OAuth)
  - ✅ 이메일 인증코드 (Spring Mail + Gmail SMTP)
  - ✅ 세션 관리 + 중재 State Machine
  - ✅ **LLM 브릿지 (Claude Haiku 4.5 + 호스트 ~/.claude 마운트, API 키 불필요)**
  - ✅ 위기 감지 (CrisisDetector) + 금지어 가드 (KeywordGuard)
  - ✅ 리포트 생성 (기여도, NVC — 4Horsemen 내부 점수만 보존, UI 노출 없음)
  - ✅ 관계 그래프 (MariaDB: user_relationships, conflict_history)
  - ✅ 데이터 보존 정책 (30일 만료, 스케줄러)
  - ✅ OpenAPI / Swagger UI (`/swagger-ui.html`)
  - ✅ CORS 도메인 허용 + GlobalExceptionHandler 표준화
- ✅ Docker 멀티 컨테이너 배포 (MariaDB / Backend / Frontend / Nginx)
- ✅ Cloudflare Tunnel — `dev.againspring.net`, `againspring.net`
- ⏳ prod 배포 (명시적 지시 시에만)

---

## 💡 개발 체크리스트

### 백엔드 수정 시

- [ ] `shared/docs/v1/API_SPEC.md` 명세와 일치하는지 확인
- [ ] `shared/docs/v1/FORBIDDEN_WORDS.md` 금지어 없는지 확인
- [ ] LLM 호출 시 PromptSanitizer 경유 여부 확인
- [ ] `shared/docs/v1/DATABASE_SCHEMA.md` 스키마 준수
- [ ] 테스트 커버리지 80% 이상 유지

### dev 배포 전

- [ ] `./gradlew test` 통과
- [ ] 금지어/위험 키워드 검사 (`npm run lint:words`)
- [ ] `infra/.env.dev` 값 확인
- [ ] `docker compose -f docker-compose.dev.yml up -d --build` 성공
- [ ] `curl http://localhost:8090/api/health` 응답 확인

### prod 배포 전 (명시적 지시 시에만 수행)

- [ ] dev에서 충분히 검증 완료
- [ ] main 브랜치에 commit & push 완료
- [ ] `infra/.env.prod` 모든 값 입력 (기본값 없음)
- [ ] MariaDB 볼륨 백업 (`docker exec againspring-mariadb-prod mariadb-dump ...`)
- [ ] `docker compose -f docker-compose.prod.yml up -d --build` 성공
- [ ] `curl http://localhost:8091/api/health` 응답 확인
- [ ] Cloudflare Tunnel 라우팅 정상 확인

---

**마지막 업데이트**: 2026-04-26  
**담당**: Claude Code (Agent)
