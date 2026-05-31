# Social Poster 장애 대응 Runbook

> 마지막 업데이트: 2026-05-31  
> 관련 컨테이너: `againspring-social-poster-dev`  
> 포트: 9100 (내부), 외부 미노출

---

## 핵심 파일 구조 (치트시트)

```
marketing/social-poster/src/
├── server.js                  ← Express 앱 진입점, 라우터 등록
├── lib/
│   ├── anti-bot.js            ← 핑거프린트, warmup, jitter (봇탐지 우회)
│   ├── session.js             ← storageState 로드/저장
│   ├── x-selectors.js         ← X CSS 셀렉터 모음 ← UI 변경 시 여기만 수정
│   └── ig-selectors.js        ← Instagram CSS 셀렉터 모음 ← UI 변경 시 여기만 수정
└── routes/
    ├── publish-x.js           ← X 트윗 발행 로직
    ├── publish-instagram.js   ← Instagram 이미지+캡션 발행 로직
    ├── session-health.js      ← 세션 유효성 확인 + 쿠키 갱신
    └── test-login.js          ← admin UI 로그인 테스트 버튼
```

---

## 시나리오별 대응

### 1. 셀렉터 깨짐 (X·Instagram UI 변경)

**증상:** 발행 실패, 로그에 `selector not found` 또는 timeout 오류

**원인:** X·Instagram이 DOM 구조를 변경함

**대응 (재빌드 불필요):**

```bash
# 1. 셀렉터 파일 수정 (로컬 에디터에서)
#    X:         marketing/social-poster/src/lib/x-selectors.js
#    Instagram: marketing/social-poster/src/lib/ig-selectors.js

# 2. 컨테이너 재시작 (3~5초, 재빌드 없음)
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml restart againspring-social-poster-dev

# 3. 헬스체크
docker exec againspring-social-poster-dev curl -s http://localhost:9100/health
```

> **왜 재빌드 없이 되나?**  
> `src/` 디렉토리가 호스트에서 컨테이너로 마운트됨 (`docker-compose.dev.yml` volume).  
> 파일 변경 → restart → nodemon이 새 파일로 서버 재시작.

---

### 2. 로그인 플로우 변경 (X·Instagram 로그인 UI 개편)

**증상:** `CHALLENGE_REQUIRED`, `LOGIN_FAILED`, `NEXT_BUTTON_NOT_FOUND` 등

**대응:**

```bash
# 1. 플랫폼 로그인 페이지의 실제 input/button 확인
docker exec againspring-social-poster-dev node -e "
const {chromium} = require('playwright');
(async () => {
  const b = await chromium.launch({args:['--no-sandbox']});
  const p = await (await b.newContext()).newPage();
  await p.goto('https://x.com/i/flow/login', {waitUntil:'networkidle',timeout:30000});
  const inputs = await p.evaluate(() =>
    Array.from(document.querySelectorAll('input')).map(i => ({
      name:i.name, type:i.type, autocomplete:i.autocomplete, id:i.id
    }))
  );
  console.log(JSON.stringify(inputs, null, 2));
  await b.close();
})().catch(console.error);
"

# 2. x-selectors.js 또는 publish-x.js의 attemptRelogin 수정

# 3. 재시작
docker compose -f docker-compose.dev.yml restart againspring-social-poster-dev
```

---

### 3. 세션 만료 (EXPIRED 상태)

**증상:** admin UI에서 "세션 만료" 표시, `notifyHealthCheckFailed` 알림 수신

**대응:**

1. Windows PC에서 해당 플랫폼 브라우저로 로그인
2. F12 → Console → `allow pasting` → `extract-session.js` 코드 실행
3. JSON 복사
4. `dev.againspring.net/admin/marketing/settings` → "브라우저 세션 시드" → 붙여넣기 → "세션 등록"

> **세션 자동 갱신 주기:** 매일 03:00 `SessionHealthCheckJob`이 피드를 방문해 쿠키를 갱신함.  
> 갱신 성공 시 DB에 새 storageState가 저장되어 세션 수명이 연장됨.

---

### 4. 봇 탐지 / IP 차단

**증상:** 로그인 시 `We've temporarily limited your login`, Instagram challenge 루프

**원인:** 서버 IP(데이터센터)가 탐지됨

**대응:**
- 단기: 몇 시간 후 재시도 (임시 제한은 보통 1~6시간)
- 세션 시드 재등록: Windows PC 브라우저에서 새로 추출
- 중기: `anti-bot.js` `REALISTIC_UA` 및 `buildContext` 옵션 업데이트 고려

---

### 5. 발행 코드 수정이 필요한 경우 (포스팅 로직 변경)

**X 발행 흐름 (`publish-x.js`):**
```
applyStorageState → isLoggedIn → (실패시 attemptRelogin)
→ goto /home → warmup → click compose
→ fill tweets → submit → extractPostedTweetUrl
→ dumpStorageState → return
```

**Instagram 발행 흐름 (`publish-instagram.js`):**
```
applyStorageState → isLoggedIn → (실패시 attemptRelogin)
→ goto / → warmup → click New Post
→ setInputFiles (image) → Next × 2 → fill caption → Share
→ waitForConfirm → dumpStorageState → return
```

**수정 후:**
```bash
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml restart againspring-social-poster-dev
# 로그 확인
docker logs againspring-social-poster-dev -f --tail=20
```

---

## 빠른 명령 모음

```bash
# social-poster 재시작 (코드 수정 후)
docker compose -f docker-compose.dev.yml restart againspring-social-poster-dev

# 실시간 로그
docker logs againspring-social-poster-dev -f --tail=50

# 헬스체크
docker exec againspring-social-poster-dev curl -s http://localhost:9100/health

# 세션 상태 확인 (DB)
docker exec againspring-mariadb-dev mariadb -uagainspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT platform, status, last_used_at FROM social_sessions;"

# SessionHealthCheckJob 수동 트리거 (개발 중 테스트)
# → BE 로그에서 [SESSION_HEALTH] 확인
```

---

## 봇 차단 우회 아키텍처 요약

| 레이어 | 구현 위치 | 내용 |
|--------|-----------|------|
| 핑거프린트 | `anti-bot.js` | Windows 11 Chrome UA, 1920×1080, ko-KR locale |
| webdriver 마스킹 | `anti-bot.js maskWebdriver()` | `navigator.webdriver = undefined` |
| 행동 패턴 | `anti-bot.js jitter()` | 모든 지연시간에 ±35% 분산 |
| 워밍업 | `anti-bot.js warmup()` | 포스팅 전 피드 2~4회 스크롤 |
| 세션 갱신 | `session-health.js` + `SessionHealthCheckJob` | 매일 03:00 피드 방문 → 쿠키 갱신 → DB 저장 |
