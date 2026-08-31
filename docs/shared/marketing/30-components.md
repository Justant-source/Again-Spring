# social-poster 서비스 운영 가이드

> **위치**: `Again-Spring-Marketing/services/social-poster/`  
> **역할**: X·Instagram·네이버 블로그 Playwright 자동 게시 (ASM M6 단계)

---

## 개요

`social-poster`는 Node.js + Playwright 기반의 소셜 미디어 자동 게시 서비스입니다.  
ASM의 M6 파이프라인 단계에서 호출되어 생성된 콘텐츠를 각 플랫폼에 게시합니다.

**기술 스택**: Node.js · Express · Playwright (Chromium) · otplib (TOTP 2FA) · sharp

---

## 디렉터리 구조

```
services/social-poster/
├── Dockerfile
├── package.json
├── extract-session.js        # 세션 쿠키 추출 CLI 도구
├── src/
│   ├── server.js             # Express 서버 진입점
│   ├── seed-cli.js           # 세션 시딩 CLI
│   ├── seed-server.js        # 세션 시딩 서버 모드
│   ├── lib/
│   │   ├── session.js        # 세션 스토리지 (파일 기반)
│   │   ├── totp.js           # TOTP 2FA 처리
│   │   ├── anti-bot.js       # 봇 탐지 우회 (랜덤 딜레이, 사람처럼 입력)
│   │   ├── x-selectors.js    # X(트위터) CSS 셀렉터
│   │   ├── ig-selectors.js   # Instagram CSS 셀렉터
│   │   ├── ig-restriction.js # IG scraping_warning 닫기 (Graph 25/2207050 해제)
│   │   └── naver-selectors.js # 네이버 블로그 CSS 셀렉터
│   └── routes/
│       ├── capture-x-thread.js   # 광장 캡처 (X·IG 공유, IG만 commentsReadableBudget)
│       ├── publish-x.js          # X 게시 엔드포인트
│       ├── publish-instagram.js  # Instagram 피드 게시 엔드포인트
│       ├── dismiss-instagram-restriction.js  # scraping_warning 닫기
│       ├── publish-naver-blog.js # 네이버 블로그 게시 엔드포인트
│       ├── session-health.js     # 세션 유효성 확인
│       └── test-login.js         # 로그인 테스트
```

---

## API 엔드포인트

### POST /capture/x-thread

광장 사연·댓글·비율을 JPEG로 캡처한다. X·IG 파이프가 공유한다.

```json
{
  "postId": "post_…",
  "hasPartnerStory": false,
  "commentsReadableBudget": true
}
```

- 기본(X): 댓글 **최대 4장 고정**.
- `commentsReadableBudget: true`(IG만): 누적 crop 높이 ≤530 CSS가 되도록 상위 N(1~4)만 자름 — 상세 [`instagram-feed-strategy.md`](70-policy/instagram-feed-strategy.md) §2.1.2.
- **동시성**: author / partner / detail 캡처는 별도 browser context · 직렬. 동일 context 병렬은 파트너 본문 가로 타일 깨짐을 유발할 수 있음(2026-08-10 수정). 본문 JPEG 가로 self-similarity 가드(mid-band + full-frame MAE, 2026-08-14) + 1회 재시도.

### POST /publish/x

X(트위터)에 텍스트 + 이미지를 게시합니다.

```json
{
  "text": "게시할 텍스트 (최대 280자)",
  "imageBase64": "base64 이미지 (선택)",
  "imageMime": "image/png",
  "replyToTweetId": "답글 대상 트윗 ID (선택)"
}
```

`replyToTweetId`가 있으면 해당 트윗의 답글로 붙인다. 없으면 루트 트윗.

### POST /instagram/dismiss-restriction

Instagram `scraping_warning`(자동화된 행동 의심) 화면의 **닫기**를 눌러 Meta Graph `User access is restricted`(code 25 / 2207050)를 해제한다. 미디어는 올리지 않는다. 릴스 게시는 Graph API가 이 엔드포인트를 한 번 호출한 뒤 `/media`를 재시도한다.

```json
{ "storageState": "{...Playwright storageState...}" }
```

성공: `{ "ok": true }`. 닫기를 못 누르면 `{ "ok": false, "error": "SCRAPING_WARNING_UNCLEARED …" }`.

### POST /publish/instagram

Instagram 피드를 게시합니다. 릴스 영상 업로드는 Graph API만 사용한다(웹 업로드는 anti-bot에 막힘).

```json
{
  "caption": "캡션 텍스트",
  "imageBase64": "base64 이미지",
  "imageMime": "image/jpeg",
  "type": "feed"
}
```

### POST /publish/naver-blog

네이버 블로그에 마크다운 콘텐츠를 게시합니다.

```json
{
  "title": "글 제목",
  "content": "마크다운 본문",
  "tags": ["태그1", "태그2"]
}
```

### GET /session-health

세션 쿠키 유효성을 확인합니다.

```json
{
  "x": { "valid": true },
  "instagram": { "valid": false },
  "naver": { "valid": true }
}
```

---

## 세션 관리

social-poster는 Playwright 세션 쿠키를 파일로 저장하여 재로그인 없이 게시합니다.

### 최초 세션 설정

```bash
cd services/social-poster

# X 세션 추출
node extract-session.js x

# Instagram 세션 추출
node extract-session.js instagram

# 네이버 세션 추출
node extract-session.js naver
```

각 명령은 Chromium 창을 열어 수동 로그인 후 세션을 저장합니다.

### 세션 파일 위치

```
services/social-poster/
└── sessions/
    ├── x-session.json
    ├── instagram-session.json
    └── naver-session.json
```

> **⚠️ 보안**: `sessions/` 디렉터리는 절대 git 커밋하지 않습니다 (`.gitignore` 적용).

---

## TOTP 2FA 처리

X, Instagram의 2FA를 자동으로 처리합니다.  
`lib/totp.js`가 환경 변수의 TOTP 시크릿으로 6자리 OTP를 생성합니다.

환경 변수 (ASM `.env`에 추가 필요):
```env
X_TOTP_SECRET=JBSWY3DPEHPK3PXP
INSTAGRAM_TOTP_SECRET=...
```

---

## M6 구현 계획

현재 M6(소셜 게시)는 미구현 상태입니다. 구현 시:

1. ASM `app/worker/pipeline.py`의 `run_stub()` → `run_pipeline()` 교체
2. `app/publishers/` 모듈에서 social-poster 서비스를 HTTP 호출
3. 멱등 게시: `publication(job_id, platform)` UNIQUE 제약으로 중복 방지
4. 게시 결과 URL을 `publications` 테이블에 저장

---

## 트러블슈팅

### 세션 만료 오류

플랫폼이 세션을 만료시키면 `session-health` 엔드포인트가 `valid: false`를 반환합니다.  
해결: `extract-session.js [platform]`으로 재로그인.

### 봇 감지 차단

`lib/anti-bot.js`의 랜덤 딜레이와 사람처럼 입력하는 방식을 사용하지만,  
플랫폼 정책 변경으로 차단될 수 있습니다. 셀렉터 파일(`*-selectors.js`)을 업데이트하세요.
