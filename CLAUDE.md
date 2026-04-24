# CLAUDE.md — 다시봄 프로젝트 개발 가이드

**프로젝트**: 다시봄 · Again Spring  
**진행 상황**: 백엔드 전체 구현 완료, FE-BE 통합 및 배포 준비 중  
**기준일**: 2026-04-25

---

## 🎯 프로젝트 한 줄 요약

싸운 두 사람 사이에서 AI가 양쪽 이야기를 중립적으로 정리해 관계 회복을 돕는 웹앱.  
FE는 Next.js 14 MSW 프로토타입, BE는 Spring Boot 3.3 + **MariaDB 11** + Claude Code LLM 브릿지.

---

## 🏗️ 작업 범위 분리 원칙

### 작업 위치 규칙

| 작업 범위 | 디렉토리 | 주요 문서 |
|---|---|---|
| **FE 기능/UI** | `frontend/` | `shared/docs/API_SPEC.md` |
| **BE 기능/API** | `backend/` | `shared/docs/API_SPEC.md` |
| **LLM 브릿지** | `backend/src/main/java/.../llm/` | `shared/docs/LLM_BRIDGE_ARCHITECTURE.md` |
| **공유 타입/스키마** | `shared/` | `shared/docs/DATABASE_SCHEMA.md` |
| **인프라** | `infra/` | `infra/docker-compose.yml` |

### 절대 규칙

1. **FE는 Claude Code를 직접 호출하지 않음**  
   → 모든 LLM 요청은 BE 경유 (REST API)

2. **BE만 Claude Code를 수행**  
   → ClaudeCodeBridge: `backend/src/main/java/.../llm/bridge/ClaudeCodeBridge.java`

3. **금지어/위기 키워드 확인 필수**  
   → 코드/프롬프트 수정 시 `shared/docs/FORBIDDEN_WORDS.md` 참조

---

## 📋 문서 위치 맵

### API / 스키마
- `shared/docs/API_SPEC.md` — REST API 전체 명세 (엔드포인트, 요청/응답 스키마)
- `shared/docs/DATABASE_SCHEMA.md` — MariaDB 테이블 설명

### LLM / AI
- `shared/docs/LLM_BRIDGE_ARCHITECTURE.md` — Claude Code 프로세스 풀, 에러 처리, PromptSanitizer
- `shared/docs/SYSTEM_PROMPTS.md` — Gottman + NVC 프롬프트 원본
- `shared/docs/RATIO_CALCULATION.md` — 화해 기여도 계산 규칙

### 기획 / 정책
- `shared/docs/FORBIDDEN_WORDS.md` — 금지어 · 위기 키워드 (필수!)
- `shared/docs/CATEGORIES.md` — 갈등/관계 카테고리 정의
- `shared/docs/ONBOARDING_MAPPING.md` — 온보딩 Q&A → 소통 스타일 매핑
- `shared/docs/TERMS_OF_SERVICE.md` — 서비스 이용약관

### FE 개발
- `shared/docs/MOCK_SCENARIOS.md` — MSW 목업 시나리오
- `.request/design/` — UI 디자인 핸드오프 에셋 (HTML/JSX/CSS)

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

- **프로세스 풀**: `Semaphore(3)` — 동시 최대 3개 Claude 프로세스
- **타임아웃**: 30초
- **호출**: `claude -p "프롬프트"` (Headless 모드)
- **Fallback**: Claude 불가 시 `FallbackResponses` 기본 응답 반환

### 보안 규칙

```
❌ 위험: 사용자 입력을 프롬프트에 직접 삽입
  claude -p "사용자의 말: ${userInput}"

✅ 안전: PromptSanitizer → 구조화된 JSON 삽입
  sanitized = sanitizer.sanitize(userInput);
  prompt = template + JSON.stringify(sanitized);
```

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

### 개발 (localhost)

```bash
DB_URL=jdbc:mariadb://localhost:3306/againspring?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_USER=againspring
DB_PASSWORD=changeme
JWT_SECRET=dev_secret_key_change_in_prod
CLAUDE_BIN=/usr/local/bin/claude
NODE_ENV=development
```

### 프로덕션 (달콩님 홈서버)

```bash
DB_URL=jdbc:mariadb://mariadb:3306/againspring?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_USER=againspring
DB_PASSWORD=${SECURE_PASS}
JWT_SECRET=${RANDOM_32_CHAR_STRING}
CLAUDE_BIN=/home/user/claude        # 서버에서 로그인된 Claude CLI
ANTHROPIC_API_KEY=${API_KEY}        # (향후 API 전환 시)
CLOUDFLARE_TOKEN=${CF_TOKEN}        # Tunnel 설정
```

**배포 상세**: `infra/docker-compose.yml` 참조.

---

## 📊 현재 진행 상황

- ✅ 모노레포 구조 (frontend/, backend/, shared/, infra/)
- ✅ 프론트엔드 MSW 프로토타입 (Next.js 14)
- ✅ 백엔드 구현 완료
  - ✅ Spring Boot 3.3 + Java 21 + Gradle Kotlin DSL
  - ✅ MariaDB 11 (JPA + Flyway V1__init.sql)
  - ✅ JWT 인증 (회원가입 / 로그인 / 게스트)
  - ✅ 세션 관리 + 중재 State Machine
  - ✅ LLM 브릿지 (ClaudeCodeBridge, PromptSanitizer, Semaphore 풀)
  - ✅ 위기 감지 (CrisisDetector) + 금지어 가드 (KeywordGuard)
  - ✅ 리포트 생성 (기여도, NVC, 4Horsemen)
  - ✅ 관계 그래프 (MariaDB: user_relationships, conflict_history, temperature_history)
  - ✅ 데이터 보존 정책 (30일 만료, 스케줄러)
  - ✅ OpenAPI / Swagger UI (`/swagger-ui.html`)
- ⏳ FE-BE API 통합 테스트
- ⏳ 배포 (Cloudflare Tunnel + 홈서버 Docker Compose)

---

## 💡 개발 체크리스트

### 백엔드 수정 시

- [ ] `shared/docs/API_SPEC.md` 명세와 일치하는지 확인
- [ ] `shared/docs/FORBIDDEN_WORDS.md` 금지어 없는지 확인
- [ ] LLM 호출 시 PromptSanitizer 경유 여부 확인
- [ ] `shared/docs/DATABASE_SCHEMA.md` 스키마 준수
- [ ] 테스트 커버리지 80% 이상 유지

### 배포 전 (최종)

- [ ] Integration 테스트 모두 통과
- [ ] 금지어/위험 키워드 최종 검사
- [ ] 환경 변수 프로덕션 값 적용
- [ ] Cloudflare Tunnel 설정 완료
- [ ] MariaDB 볼륨 백업 확인
- [ ] 모니터링 대시보드 구성

---

**마지막 업데이트**: 2026-04-25  
**담당**: Claude Code (Agent)
