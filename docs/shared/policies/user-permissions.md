# 사용자 등급별 권한 정책 (User Permissions)

> **권위본 데이터**: [`user-permissions.json`](./user-permissions.json) — 본 문서는 그 사람이 읽기 위한 설명. 코드/UI는 JSON을 직접 참조한다.
>
> **변경 절차**: `user-permissions.json` 한 곳만 수정 → BE 컨테이너 재시작 + FE 재빌드 → 변경 반영.

다시봄은 두 등급의 사용자를 운영한다.

| 등급 | 정의 | 진입 경로 |
|---|---|---|
| **게스트** (`guest`) | 회원가입 없이 임시 토큰으로 체험 | `POST /api/auth/guest`, 초대 토큰으로 진입 |
| **회원** (`registered`) | 이메일 인증 또는 OAuth 로그인 | `POST /api/auth/signup`, `/login`, `/oauth2/...` |

판별: `User.isGuest` 컬럼 (boolean) + JWT 클레임 `type`(`guest`|`access`).

---

## 핵심 차이 한눈에

| 카테고리 | 항목 | 게스트 | 회원 |
|---|---|---|---|
| **인증** | JWT 만료 | 1시간 | 24시간 |
| | 이메일 인증 | 불필요 | 필수 |
| **게시글** | 게시글 작성 | ✅ | ✅ |
| | 게시글 수정/삭제 | ✅ (본인만) | ✅ (본인만) |
| | 댓글 작성 | ✅ | ✅ |
| | 댓글 수정/삭제 | ✅ (본인만) | ✅ (본인만) |
| | 좋아요 | ✅ | ✅ |
| | 투표 (배심원) | ✅ | ✅ |
| **프로필** | 닉네임 편집 | ❌ | ✅ |
| | 통신 스타일 편집 | ❌ | ✅ |
| | MBTI 등록 | ❌ | ✅ |
| | 프로필 이미지 | ❌ | ✅ |
| | 계정 삭제 | ✅ (비번 X) | ✅ (OAuth 외 비번 필수) |
| **데이터** | 게시글 원문 보존 | 30일 | 30일 |
| | 댓글 원문 보존 | 30일 | 30일 |
| | 배심원 보존 | 60일 | 60일 |
| **UI** | 프로필 편집 영역 | 숨김 | 표시 |
| | "게스트 모드" 배지 | 표시 | 표시 안 함 |

---

## 운영 의도

- **게스트는 체험·전환 목적**. 짧은 토큰·턴 제한으로 빠른 회원 전환을 유도하면서, IP 단위 한도로 봇/스팸을 차단.
- **회원은 안정적 장기 사용자**. 충분한 보존 기간·Duo 초대·프로필 기반 중재자로 깊이 있는 경험 제공.
- **정책 일관성**: BE 비즈니스 로직과 FE UI 분기가 같은 JSON을 참조하므로 한쪽만 변경되어 누락되는 위험을 차단.

## 런타임 사용 위치

### Backend (Java)

`backend/src/main/java/com/againspring/config/UserPermissionsConfig.java`가 시작 시 `shared/docs/policies/user-permissions.json`을 로드(파일 경로는 `app.user-permissions-path` 설정으로 오버라이드 가능).

주요 참조 지점:

| 파일 | 사용 필드 |
|---|---|
| `security/JwtService.java` | `tiers.guest.auth.tokenExpirationSeconds` |
| `service/retention/RetentionScheduler.java` | `tiers.*.data.contentRetentionDays` |

### Frontend (TypeScript)

`frontend/lib/constants/userPermissions.ts`가 동일 데이터를 미러로 보유 (Docker 빌드 격리상 shared/ 직접 import 불가).

주요 참조 지점:

| 파일 | 사용 필드 |
|---|---|
| `app/(dashboard)/profile/page.tsx` | `tiers.guest.ui.showProfileEditing`, `showGuestModeBadge` |
| `components/profile/DeleteAccountModal.tsx` | `tiers.guest.profile.deleteRequiresPassword` |
| `lib/constants/userPermissions.ts` | 전체 권한 매트릭스 (FE 미러) |

---

## 데이터 동기화 규칙

1. **권위본**: `shared/docs/policies/user-permissions.json` (모든 변경의 시작점)
2. **BE 미러**: 없음 — 컨테이너에 볼륨 마운트로 직접 사용 (`docker-compose.dev.yml`에서 `../shared/docs/policies` 마운트)
3. **FE 미러**: `frontend/lib/constants/userPermissions.ts` — 수동 동기화. JSON과 일치하지 않으면 빌드 시 ESLint 경고(향후 자동화 검토)

---

## 변경 시 체크리스트

JSON 한 줄을 바꾸면 다음을 함께 확인:

- [ ] BE 의존 파일에 영향 없는지 (예: `dailyPostLimit`을 늘리면 `CommunityPostService` 동작 확인)
- [ ] FE 미러본 `userPermissions.ts` 동일하게 수정
- [ ] 정책 문서 본 파일(MD)의 표 갱신
- [ ] Dev에서 게스트/회원 양쪽 시나리오 모두 검증 후 prod 배포
