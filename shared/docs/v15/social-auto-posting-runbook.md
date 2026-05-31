# 소셜 자동 포스팅 런북 (dev 전용)

## 1. 개요

다시봄 마케팅 자동화 플랫폼의 소셜 미디어 자동 포스팅 기능에 대한 운영 및 보안 가이드입니다.

**기능 설명**:
- Admin UI에서 1클릭으로 승인된 콘텐츠를 X(Twitter)·Instagram에 자동 발행
- 자격증명(email, password) 저장 및 세션 관리
- 실시간 발행 상태 모니터링 및 플랫폼별 결과 추적

**중요 제약**:
- **dev 전용 기능**: prod 환경에 절대 배포 금지
- **marketing-renderer와 동일 정책 적용**: dev만 지원, prod compose 파일에 컨테이너 없음
- **비밀번호 쓰기 전용**: 저장 후 UI에서 복호화하지 않음 (보안상 단방향)

**소셜 플랫폼 자동화 위험**:
- X·Instagram은 자동화/봇 계정에 엄격한 정책 적용
- 계정 정지 위험 존재 → 개발·테스트 계정으로만 사용 권장
- **실제 비즈니스 계정 사용 시 자체 책임 원칙** (이용약관 참조)

**장애 격리**:
- social-poster 컨테이너 크래시 → 본 서비스(backend) 무영향
- RemoteLlmProvider와 동일한 fail-fast 구조 (HTTP 타임아웃 시 재시도 없음)

---

## 2. 위협 모델 및 보안 설계

### 자격증명 저장 (social_credentials 테이블)

```
암호화 방식: AES-256-GCM
마스터 키: SOCIAL_MASTER_KEY (32바이트, .env.dev에서 관리)
저장 위치: MariaDB social_credentials 테이블

구조:
  platform (X / INSTAGRAM)
  email_enc (암호화 저장)
  password_enc (암호화 저장)
  createdAt
  updatedAt
```

SocialCryptoService가 암호화/복호화 담당. **평문 자격증명은 메모리 내에서만 조작**.

### 세션 blob (social_sessions 테이블)

```
Playwright로 시드한 브라우저 sessionStorage/localStorage JSON
암호화: AES-256-GCM (SOCIAL_MASTER_KEY)
저장 필드: storage_state_enc
상태: SEEDED / EXPIRED / NOT_SEEDED
시드 일시: seeded_at
마지막 사용: last_used_at
```

세션이 만료되면 daily job (SessionHealthCheckJob, 오전 3시)이 status → EXPIRED로 변경하고 운영자 알림.

### 전송 보안 (Backend ↔ social-poster)

- **네트워크**: Docker 내부망 전용 (외부 포트 미개방)
- **전송 형식**: JSON over HTTP (plain text, 내부망이므로 무관)
- **비밀 로깅**: SocialCryptoService는 TRACE 레벨에서만 로그, 평문 패스워드는 절대 기록 금지
- **stateless**: social-poster는 마스터 키 미보유, backend가 암호화/복호화 담당

### 마스터 키 분리 전략

```
SOCIAL_MASTER_KEY 보유:
  ✓ Backend Spring 인스턴스
  ✓ Local 개발 환경 (.env.dev)

SOCIAL_MASTER_KEY 미보유:
  ✗ social-poster 컨테이너 (무상태)
  ✗ llm-worker 컨테이너
  ✗ frontend (오직 HTTP 클라이언트)
```

이 구조로 social-poster 컨테이너가 침해받아도 저장된 자격증명 복호화 불가.

---

## 3. 초기 설정 절차

### Step 1: SOCIAL_MASTER_KEY 생성

```bash
openssl rand -base64 32
# 출력 예: abc123+/def456==xyz789
```

### Step 2: .env.dev에 추가

```bash
# .env.dev 맨 아래 추가
SOCIAL_MASTER_KEY=<Step 1의 결과값>
SOCIAL_PUBLISHING_ENABLED=true
MARKETING_ENABLED=true

# 선택사항 (Slack 알림용)
APP_SOCIAL_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
APP_SOCIAL_EMAIL=admin@againspring.net
```

### Step 3: 인프라 기동

```bash
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
```

`againspring-backend-dev` 로그 확인:
```bash
docker logs againspring-backend-dev | grep "SOCIAL_MASTER_KEY initialized"
```

### Step 4: 자격증명 등록 (Admin UI)

브라우저에서:
```
https://dev.againspring.net/admin/marketing/settings
```

각 플랫폼(X, Instagram) 섹션에서:
1. **이메일**: 플랫폼 계정 이메일 (예: account@example.com)
2. **비밀번호**: 계정 비밀번호
3. **저장** → "저장되었습니다" 토스트 확인

**중요**: 저장 후 비밀번호는 UI에 표시되지 않음 (설계상 의도).

### Step 5: 세션 시딩

세션 시딩은 3가지 방법 중 1가지를 선택하여 수행합니다.

#### 5-1. Windows PC 브라우저 방식 (권장)

**환경**: Windows 11 + Chrome, Linux + Chrome 등 일반 사용자 환경

```bash
# marketing/social-poster/ 디렉토리에서 extract-session.js 파일 확인
# (파일명: extract-session.js)

# 방법: x.com 또는 instagram.com에 로그인 후 브라우저 콘솔에서 실행
```

1. 브라우저에서 https://x.com (또는 instagram.com) 접속
2. 로그인 (이메일 + 비밀번호, 2FA 필요 시 진행)
3. F12 → Console 탭 열기
4. 아래 스크립트 전체를 콘솔에 복사-붙여넣기:

```javascript
// extract-session.js 콘솔 버전
const extractSession = async () => {
  // 자세한 구현은 src/extract-session.js 참조
  // 이 함수는 현재 탭의 쿠키와 localStorage를 추출
  const cookies = await (async () => {
    const cs = await navigator.cookieStore.getAll();
    return cs || [];
  })();
  const sessionStorage = JSON.parse(JSON.stringify(window.sessionStorage));
  const localStorage = JSON.parse(JSON.stringify(window.localStorage));
  return { cookies, sessionStorage, localStorage };
};
extractSession().then(s => console.log(JSON.stringify(s)));
```

5. 콘솔 출력 → **전체 복사**

#### 5-2. 서버 헤드리스 방식 (자동화)

**환경**: Linux 서버, CI/CD, 운영 자동화

```bash
cd /path/to/marketing/social-poster
npm install

# 대화형 프롬프트로 실행 (이메일/비밀번호/2FA 입력)
node src/seed-server.js --platform x
```

흐름:
1. `--platform x` 또는 `--platform instagram` 지정
2. "이메일 입력:" → 계정 이메일 입력
3. "비밀번호 입력:" → 계정 비밀번호 입력
4. 2FA 필요 시 "TOTP 입력:" 또는 "이메일 코드 입력:" → 해당 값 입력
5. 자동 로그인 완료 → 세션 JSON 출력

**주의**: Windows 11 Chrome 120 핑거프린트로 위장하여 로그인 성공률 향상.

#### 5-3. 로컬 머신 브라우저 방식 (GUI 필요)

**환경**: 로컬 개발 머신 (X 서버 필요, WSL2는 불가)

```bash
# docker 컨테이너 X, 호스트 머신에서 실행
cd /path/to/marketing/social-poster
npm install

node src/seed-cli.js --platform x
```

브라우저 창(headed mode)에서:
1. X 로그인 페이지 자동 열림
2. 계정 로그인 (이메일 + 비밀번호)
3. 2FA 필요 시 진행
4. 로그인 완료 후 **터미널에서 Enter 입력**
5. 세션 JSON 출력 → **전체 복사**

#### 5-4. Admin UI에 세션 등록

```
https://dev.againspring.net/admin/marketing/settings
```

X 섹션 → "브라우저 세션 시드" → 텍스트박스에 위 3가지 방법 중 추출한 JSON 붙여넣기 → "세션 등록"

**상태 확인**: "세션 시드됨" 배지 녹색 표시

#### 5-5. Instagram 세션 시드 (동일 절차)

위 5-1, 5-2, 5-3 방법 중 1가지 선택 후 `--platform instagram` (또는 instagram.com) 사용

### Step 6: 발행 테스트

Admin → Marketing → Contents → 콘텐츠 선택:
1. 상태가 "APPROVED" 이상인 콘텐츠 선택
2. "소셜 자동 발행" 섹션 확인
3. X, Instagram 체크박스 선택 (또는 둘 다)
4. "마지막 트윗에 링크" / "첫 댓글에 링크" 선택
5. **발행** 버튼 클릭
6. 실시간 진행 상태 확인
   - 각 플랫폼 옆에 상태 뱃지 표시
   - "게시물 보기" 링크 클릭 → 플랫폼 확인

---

## 4. 세션 시딩 심화 가이드

### 왜 시딩이 필요한가?

X·Instagram은 비밀번호 기반 로그인 후 많은 경우 추가 인증을 요구합니다:
- 이메일 확인 링크 클릭
- 휴대폰 인증
- 의심 로그인 확인 (Suspicious login challenge)
- CAPTCHA

### 왜 Windows PC 브라우저 방식으로도 서버 계정 세션을 추출할 수 있는가?

일반적으로 서버 IP는 X·Instagram에서 봇으로 감지되어 로그인이 차단됩니다. 그러나 **Windows PC의 일반 사용자 브라우저에서 추출한 세션은 특정 계정의 쿠키·토큰 정보**이므로, 이를 서버(social-poster 컨테이너)로 전송해도 "이미 인증된 세션"으로 인식되어 정상 동작합니다.

즉:
- **세션 생성 단계**: Windows PC에서 일반 사용자로 로그인 (2FA 우회, 일반적인 PC 환경)
- **세션 사용 단계**: 서버 컨테이너가 그 쿠키를 사용해 요청 (IP 차단 무관, 이미 인증됨)

### 3가지 시딩 방식의 선택 기준

| 방식 | 추천 환경 | 특징 |
|------|---------|------|
| **Windows 브라우저** | 일반 운영자, 개발자 로컬머신 | 가장 간단함, 콘솔 script만 실행, 최고 성공률 |
| **서버 헤드리스** | Linux 서버, 자동화, CI/CD | 대화형 입력, 봇 탐지 회피 능력 우수, 무인 운영 |
| **로컬 CLI** | GUI 있는 개발 머신 | 전체 자동화, 하지만 X 서버 필요, WSL2 불가 |

### 시드 결과 검증

출력된 JSON 구조:
```json
{
  "cookies": [
    { "name": "...", "value": "..." }
  ],
  "origins": [
    {
      "origin": "https://x.com",
      "localStorage": [ ... ],
      "sessionStorage": [ ... ]
    }
  ]
}
```

**검증**:
- 크기: 최소 50KB 이상 (로그인 세션 충분함)
- 쿠키 개수: 최소 5개 이상
- JWT 토큰 포함 여부 확인 (세션 유효성)

### 세션 유효 기간

- **X**: 약 2주 (플랫폼 정책)
- **Instagram**: 약 1개월 (플랫폼 정책)

daily job(오전 3시)이 만료 감지 → status EXPIRED → 운영자 알림 (Slack 등)

---

## 5. SOCIAL_MASTER_KEY 회전 절차

언제 수행:
- 의심 상황: 마스터 키 노출, 서버 침해 징후
- 정기 보안: 3개월마다 회전 (권장)
- 운영자 변경: 새 담당자 인수인계 후

### 회전 단계

#### Step 1: 새 마스터 키 생성

```bash
NEW_KEY=$(openssl rand -base64 32)
echo "새 키: $NEW_KEY"
```

#### Step 2: 기존 자격증명 재암호화 (MariaDB)

```sql
-- 현재 암호화된 데이터 추출
SELECT id, platform, email_enc, password_enc
FROM social_credentials;

-- 위 결과를 로컬 스크립트에서:
-- 1. SOCIAL_MASTER_KEY (기존)로 각 값 복호화
-- 2. NEW_KEY로 재암호화
-- 3. UPDATE social_credentials SET email_enc=?, password_enc=? WHERE id=?

-- 또는 Java 유틸리티 클래스에서 일괄 처리:
-- SocialCryptoService.rotateAllCredentials(currentKey, newKey)
```

#### Step 3: 기존 세션 무효화 (권장)

```sql
-- 회전 후 모든 세션을 재시딩하려면
DELETE FROM social_sessions;

-- 또는 상태만 EXPIRED로 변경
UPDATE social_sessions SET status = 'EXPIRED';
```

이유: 기존 세션이 깨져도 발행 흐름이 중단되지 않도록.

#### Step 4: .env.dev 업데이트

```bash
vi .env.dev
# SOCIAL_MASTER_KEY=<NEW_KEY로 변경>
```

#### Step 5: Backend 재기동

```bash
cd env
docker compose -f docker-compose.dev.yml restart againspring-backend-dev

# 로그 확인
docker logs againspring-backend-dev | grep "SOCIAL_MASTER_KEY"
```

#### Step 6: 세션 재시딩 (필요 시)

Step 3에서 DELETE한 경우, [3단계 세션 시딩](#step-5-세션-시딩) 절차 반복.

---

## 6. 계정 침해 의심 시 대응

### 즉시 대응 (15분 이내)

#### 6-1. 발행 차단

```bash
# .env.dev 수정
SOCIAL_PUBLISHING_ENABLED=false

# Backend 재기동
cd env && docker compose -f docker-compose.dev.yml restart againspring-backend-dev

# 확인: Admin UI → settings에서 모든 버튼 disabled 상태
```

#### 6-2. 플랫폼 자체 조치

**X (Twitter)**:
- https://twitter.com/settings/security_and_account
- "Sessions" → 모든 활성 세션 강제 로그아웃
- 비밀번호 변경

**Instagram**:
- Settings → Security → "Where you're logged in"
- 의심 세션 강제 로그아웃
- 비밀번호 변경

### 복구 절차

#### 6-3. SOCIAL_MASTER_KEY 회전 (필수)

[5단계](#5-social_master_key-회전-절차) 참조.

#### 6-4. 자격증명 재등록

Admin UI → Marketing → Settings:
1. 플랫폼별로 이메일 입력
2. 새 비밀번호(Step 6-2에서 변경한 것) 입력
3. 저장

#### 6-5. 세션 재시딩

[3단계 Step 5](#step-5-세션-시딩) 절차 반복.

#### 6-6. 로그 검토

```bash
# Backend 로그 (최근 24시간)
docker logs --tail 1000 againspring-backend-dev | grep SOCIAL

# social-poster 로그 (있으면)
docker logs --tail 1000 againspring-social-poster-dev | grep ERROR

# DB 감사: 어떤 콘텐츠가 발행되었는지
SELECT id, platform, published_at, published_url 
FROM marketing_contents 
WHERE status IN ('PUBLISHING', 'PUBLISHED', 'FAILED') 
ORDER BY published_at DESC LIMIT 20;
```

#### 6-7. 정상화

```bash
# .env.dev에서 발행 활성화
SOCIAL_PUBLISHING_ENABLED=true

# Backend 재기동
cd env && docker compose -f docker-compose.dev.yml restart againspring-backend-dev

# 테스트 발행 (테스트 계정 또는 드래프트 콘텐츠)
```

---

## 7. 킬스위치 (응급 차단)

두 가지 feature flag:

### MARKETING_ENABLED=false

```bash
# .env.dev
MARKETING_ENABLED=false

# 효과:
# - /api/admin/marketing/* 모든 엔드포인트 → 404 Not Found
# - Admin UI 마케팅 섹션 접근 불가
# - 소셜 포스팅, 컨텐츠 생성, 성과 입력 모두 차단
```

**사용 시나리오**: 마케팅 기능 전체 긴급 중지

### SOCIAL_PUBLISHING_ENABLED=false

```bash
# .env.dev
SOCIAL_PUBLISHING_ENABLED=false

# 효과:
# - POST /api/admin/marketing/social/publish/{contentId} → 403 Forbidden
# - 자격증명 조회, 세션 상태 조회는 정상 동작
# - Admin UI의 "발행" 버튼 disabled
```

**사용 시나리오**: 소셜 발행만 임시 중단 (관리 가능)

### 적용 방법

```bash
# 1. .env.dev 편집
vi env/.env.dev

# 2. Backend 재기동
cd env && docker compose -f docker-compose.dev.yml restart againspring-backend-dev

# 3. 확인
curl http://localhost:8090/api/admin/marketing/contents
# MARKETING_ENABLED=false면 404
```

---

## 8. 봇 차단 우회 아키텍처

X·Instagram은 자동화된 계정에 대해 엄격한 봇 탐지 규칙을 적용합니다. social-poster는 여러 계층에서 봇으로 감지되지 않도록 설계됐습니다.

| 레이어 | 구현 위치 | 내용 |
|--------|-----------|------|
| 핑거프린트 | `src/lib/anti-bot.js` | Windows 11 Chrome 120 UA, 1920×1080, ko-KR locale |
| webdriver 마스킹 | `src/lib/anti-bot.js maskWebdriver()` | `navigator.webdriver = undefined` |
| 행동 패턴 | `src/lib/anti-bot.js jitter()` | 모든 지연시간에 ±35% 분산 |
| 워밍업 | `src/lib/anti-bot.js warmup()` | 포스팅 전 피드 2~4회 스크롤 |
| 세션 갱신 | `src/session-health.js` + `SessionHealthCheckJob` (BE) | 매일 03:00 피드 방문 → 쿠키 갱신 → DB 저장 |

### 세션 갱신 (Session Health Check Job)

매일 오전 3:00에 자동 실행:
1. 저장된 세션으로 각 플랫폼 피드 접속
2. 피드 스크롤 수행 (warmup과 동일)
3. 새로운 쿠키 상태 추출
4. `updatedStorageState` DB 저장
5. 세션 상태가 EXPIRED면 운영자 알림

**로그 확인**:
```bash
docker logs --since 3h againspring-backend-dev | grep SESSION_HEALTH

# 예상 출력:
# [SESSION_HEALTH] Starting daily session health check
# [SESSION_HEALTH] platform=X session healthy, cookies updated
# [SESSION_HEALTH] platform=INSTAGRAM session healthy, cookies updated
```

---

## 9. UI 셀렉터 유지보수

X·Instagram의 UI는 빈번하게 변경됩니다. 자동 발행이 실패하면 셀렉터 업데이트 필요.

### 셀렉터 위치

```
marketing/social-poster/src/lib/x-selectors.js   (X 로그인, 트윗 발행)
marketing/social-poster/src/lib/ig-selectors.js  (Instagram 로그인, 게시물 발행)
```

### 수정 후 배포

```bash
cd env
docker compose -f docker-compose.dev.yml restart againspring-social-poster-dev

# 로그 확인
docker logs againspring-social-poster-dev | tail -50
```

---

## 10. prod 배포 금지 정책 (절대 규칙)

### prod에서 소셜 포스팅을 절대 활성화하지 않는 이유

1. **social-poster 컨테이너가 docker-compose.prod.yml에 없음**
   - prod는 backend, frontend, MariaDB, nginx만 포함
   - social-poster 추가 금지 (영구적)

2. **자격증명 관리 복잡성**
   - prod 계정 침해 시 실제 비즈니스 크레딧/팔로워 손실
   - UI 검증 테스트 부족 → 버그 가능성 높음

3. **이용약관 리스크**
   - 자동 포스팅은 X·Instagram TOS 위반 가능성
   - 계정 정지 시 회복 불가능

### 위반 검사 (배포 전)

```bash
# 1. .env.prod에 SOCIAL_MASTER_KEY 없는지 확인
grep SOCIAL_MASTER_KEY env/.env.prod
# (출력 없어야 함)

# 2. docker-compose.prod.yml에 social-poster 없는지 확인
grep -i "social-poster" env/docker-compose.prod.yml
# (출력 없어야 함)

# 3. MARKETING_ENABLED 상태 확인
grep MARKETING_ENABLED env/.env.prod
# 또는 기본값(false) 사용

# 4. Backend config에서 social 관련 bean 비활성 확인
grep -r "@ConditionalOnProperty.*marketing.enabled" backend/src
```

위반 시: **rollback 필수, incident report 작성**

---

## 11. 운영자 체크리스트

### 주간 점검 (매주 월요일)

- [ ] 세션 상태 확인 (Admin Settings)
  - X: "세션 시드됨" (녹색)
  - Instagram: "세션 시드됨" (녹색)
- [ ] 최근 발행 콘텐츠 샘플 확인
  - 링크 유효성 (404 아닌지)
  - 플랫폼 표시 확인

### 월간 점검 (매월 1일)

- [ ] 로그 검토
  ```bash
  docker logs --since 30d againspring-backend-dev | grep SOCIAL | tail -50
  ```
- [ ] 자격증명 유효성 (비밀번호 변경 없었는지 플랫폼 확인)
- [ ] 마스터 키 회전 계획 (3개월마다)

### 장애 발생 시

1. **발행 실패**:
   - 콘텐츠 로그 확인 (status FAILED)
   - social-poster 로그 확인
   - 셀렉터 업데이트 필요 여부 판단

2. **세션 만료**:
   - Admin 알림 수신 (Slack 등)
   - 새 세션 시드 ([4단계](#4-세션-시딩-심화-가이드))
   - 운영자에게 재알림

3. **의심 활동**:
   - SOCIAL_PUBLISHING_ENABLED=false로 즉시 차단
   - 인시던트 로그 작성
   - 보안팀과 협업 ([6단계](#6-계정-침해-의심-시-대응))

---

## 12. 참고 자료

| 파일 | 설명 |
|------|------|
| `shared/docs/v15/` | V15 마케팅 자동화 전체 문서 |
| `env/.env.dev.example` | 환경변수 템플릿 |
| `env/docker-compose.dev.yml` | dev 컨테이너 구성 |
| `backend/docs/llm-bridge.md` | LLM 브릿지 (유사 구조) |
| `frontend/docs/testing.md` | FE e2e 테스트 |

---

**작성일**: 2026-05-31  
**최종 업데이트**: 2026-05-31  
**담당**: 다시봄 개발팀 (Claude Code Agent)  
**최종 검토**: 보안팀 필수

