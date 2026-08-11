# 인증 흐름

**위치**: `docs/frontend/ux/flows/01-auth.md`  
**자매 문서**: [README.md](./README.md) · [02-permissions.md](./02-permissions.md) · [09-partner-invite-ownership.md](./09-partner-invite-ownership.md) · [../principles.md](../principles.md)  
**기준일**: 2026-08-11  
**성격**: as-is 현행 기준

---

## 진입점

| 경로 | 설명 |
|---|---|
| `/` | 랜딩 — 로그인 상태에 따라 분기 |
| `/login` | 이메일 로그인 (`?next=` 지원) |
| `/signup` | 이메일 회원가입 (`?next=` 지원) |
| `/guest` | 게스트 닉네임 입력 |
| `/auth/callback/[provider]` | OAuth 콜백 처리 |
| `/forgot-password` | 비밀번호 재설정 요청 |
| `/reset-password/[token]` | 비밀번호 재설정 실행 |
| `/s/[token]` | 상대 초대 — 미로그인 시 로그인/가입으로 보내고 **`next=/s/{token}`으로 복귀** |

---

## 초대 URL `next` 보존 (2026-08-11)

상대 초대 `/s/{token}`에서 가입·로그인·OAuth·게스트 승격(GuestUpgrade)을 탈 때 **항상 같은 초대 URL로 복귀**한다. 홈(`/`)·광장(`/community`)으로 보내면 안 된다.

| 경로 | 계약 |
|---|---|
| `/login?next=/s/{token}` | 성공 후 `safeRedirect(next)` → `/s/{token}` |
| `/signup?next=/s/{token}` | 성공 후 동일 (게스트 업그레이드 포함) |
| OAuth `state` | `next=/s/{token}` 인코딩 → 콜백에서 복원 |
| GuestUpgradeModal | `redirect`/`next`에 `/s/{token}` 유지 |

근거: `safeRedirect()` · [09-partner-invite-ownership.md](./09-partner-invite-ownership.md).

---

## 가입 흐름

근거: `app/(auth)/signup/page.tsx`

```mermaid
flowchart TD
    Start(["가입 시작"]) --> Nick["닉네임 입력\n(2~12자)"]
    Nick --> Email["이메일 입력"]
    Email --> SendCode["POST /api/auth/send-verification\n인증코드 발송"]
    SendCode --> CodeInput["6자리 인증코드 입력"]
    CodeInput -->|"유효"| PW["비밀번호 입력\n(8자 이상)"]
    CodeInput -->|"오류"| CodeInput
    PW --> Terms["약관 동의\n(필수: 이용약관·개인정보)"]
    Terms --> Submit["POST /api/auth/signup"]
    Submit -->|"fromGuestSession 쿼리"| Upgraded["/?upgraded=true\n게스트 데이터 인계"]
    Submit -->|"next=/s/{token}"| InviteBack["/s/{token}\n초대 URL 복귀"]
    Submit -->|"일반 (next 없음)"| Landing["/\n랜딩"]
    Submit -->|"에러"| ErrBox["에러 표시"]
```

**검증 에러 6종**:
- `NICKNAME_TAKEN` — 닉네임 중복
- `EMAIL_TAKEN` — 이메일 중복
- `EMAIL_VERIFICATION_FAILED` — 인증코드 불일치
- `EMAIL_VERIFICATION_EXPIRED` — 인증코드 만료
- `WEAK_PASSWORD` — 비밀번호 강도 미달
- `TERMS_NOT_AGREED` — 필수 약관 미동의

---

## 로그인 흐름

근거: `app/(auth)/login/page.tsx`, `lib/api/client.ts`

```mermaid
flowchart TD
    Start(["로그인 시작"]) --> Form["이메일 + 비밀번호 입력"]
    Form --> Submit["POST /api/auth/login"]
    Submit -->|"성공"| Token["accessToken → localStorage\nsetUser()"]
    Token --> Redirect["safeRedirect(next)\n오픈리다이렉트 방지"]
    Submit -->|"EMAIL_NOT_REGISTERED"| CTA1["가입하기 버튼 노출"]
    Submit -->|"WRONG_PASSWORD"| CTA2["비밀번호 오류 안내"]
    Submit -->|"OAUTH_LOGIN_REQUIRED"| CTA3["소셜 로그인 버튼 안내"]
    Submit -->|"ACCOUNT_SUSPENDED"| ErrBox["계정 정지 안내"]
```

`safeRedirect()`: `next` 파라미터가 동일 도메인(상대 경로)인지 검증 후 push. 외부 도메인이면 `/`로 fallback.  
초대 플로우에서는 `next=/s/{token}`이 반드시 살아 있어야 한다(홈·광장 fallback 금지).

---

## 게스트 진입

근거: `app/(auth)/guest/page.tsx`

```mermaid
flowchart TD
    Start(["게스트로 둘러보기 클릭"]) --> GuestPage["/guest"]
    GuestPage --> NickAuto["generateGuestNickname()\n랜덤 닉네임 자동 생성"]
    NickAuto --> NickEdit["닉네임 수정 가능 (선택)"]
    NickEdit --> GuestAuth["POST /api/auth/guest\n{nickname}"]
    GuestAuth --> Token["mock-guest-token 저장\nsetUser({isGuest: true})"]
    Token --> Force["/onboarding/intro?next=/session/new\n강제 이동"]
```

`generateGuestNickname()`: `lib/utils/guestNickname.ts` — 형용사+명사 랜덤 조합.  
게스트 토큰은 `again-spring-token` key로 localStorage 저장. 만료 2시간.

---

## OAuth 콜백

근거: `app/auth/callback/[provider]/page.tsx`, `lib/auth/oauth.ts`

```mermaid
flowchart TD
    Start(["소셜 로그인 버튼 클릭"]) --> OAuthRedirect["/api/auth/oauth2/{provider}\n(Google 등)\nstate 파라미터에 next 인코딩"]
    OAuthRedirect --> Provider["소셜 인증 제공자 화면"]
    Provider -->|"성공"| Callback["/auth/callback/{provider}\n?code=...&state=..."]
    Callback --> Decode["decodeState(state)\nnext 파라미터 복원"]
    Callback -->|"code 없음"| ErrLogin["/login?error=oauth_failed"]
    Decode --> OAuthPost["POST /api/auth/oauth2/{provider}\n{code}"]
    OAuthPost -->|"신규 사용자"| Onboard["/onboarding/intro\n(onboardingCompletedAt 없음)"]
    OAuthPost -->|"기존 사용자"| Redirect["safeRedirect(next)"]
    OAuthPost -->|"실패"| ErrLogin
```

지원 provider: Google. Kakao·Naver는 클라이언트 ID 환경변수 있으나 BE 구현 여부 별도 확인 필요.

---

## 비밀번호 재설정

근거: `app/(auth)/forgot-password/page.tsx`, `app/(auth)/reset-password/[token]/page.tsx`

```mermaid
flowchart TD
    Start(["비밀번호를 잊으셨나요?"]) --> ForgotPage["/forgot-password\n이메일 입력"]
    ForgotPage --> SendMail["POST /api/auth/forgot-password\n임시 비밀번호 메일 발송"]
    SendMail --> ForceModal["로그인 후\nForcePasswordChangeModal 표시"]
    ForceModal --> ChangeSubmit["POST /api/users/me/password\n신규 비밀번호"]

    Start2(["리셋 링크 클릭"]) --> ResetPage["/reset-password/{token}"]
    ResetPage --> ResetSubmit["POST /api/auth/reset-password\n{token, newPassword}"]
    ResetSubmit -->|"성공"| LoginPage["/login"]
    ResetSubmit -->|"토큰 만료"| ErrBox["만료 안내"]
```

`ForcePasswordChangeModal`: 임시 비밀번호로 로그인한 사용자에게 강제 표시. 닫기·ESC 불가 (UX 안전 정책).

---

## 근거 파일

- `app/(auth)/signup/page.tsx`
- `app/(auth)/login/page.tsx`
- `app/(auth)/guest/page.tsx`
- `app/(auth)/forgot-password/page.tsx`
- `app/(auth)/reset-password/[token]/page.tsx`
- `app/auth/callback/[provider]/page.tsx`
- `lib/auth/oauth.ts`
- `lib/utils/guestNickname.ts`
