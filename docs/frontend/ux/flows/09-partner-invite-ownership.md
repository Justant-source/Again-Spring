# 09 — 상대 초대 소유권 · 삭제 · 인증 복귀

> 그릴링 합의 (2026-08-11). 구현 SSOT. 코드와 충돌 시 이 문서 + runtime을 맞출 것.

## 목표 UX

1. 초대 `/s/{token}` → 가입/로그인 후 **항상 같은 URL로 복귀** (홈·광장 금지)
2. 게스트/익명으로 쓴 상대 글 = **토큰 capability**로 수정·삭제·재작성
3. 「내 계정으로 연결」명시적 CTA 후에만 회원 소유 → 이후 그 계정만
4. 작성자는 자기 초대 링크로 상대 슬롯 사용 불가
5. 초대 발송 = **회원만**, 미답변 시 **동일 토큰 재복사**
6. 한쪽 삭제 = tombstone 문구 + 재작성 가능; 양쪽 삭제 또는 상대 미작성 시 작성자 삭제 = **완전 삭제**
7. 완전 삭제 URL(`/community/{id}`, `/s/{token}`) = 「삭제된 게시글」+ 광장 버튼
8. **시한부 투표 제거** (`voteCloseAt` / duration / `CLOSED` 잠금). **공감 투표는 유지**

## 미연결(unowned) 판정

상대 본문 또는 partner tombstone이 있고, 다음 중 하나:

- `partnerUserId` null
- `partnerUserId` starts with `partner_`
- 유저가 guest
- 유저 soft-deleted

그 외 살아 있는 회원 `partnerUserId` = **연결됨**.

## API 계약 (추가·변경)

### GET `/api/s/{token}`

확장 응답:

```json
{
  "postId": "...",
  "userTitle": "...",
  "authorBodyPublished": "...",
  "category": "...",
  "deleted": false,
  "partnerState": "NONE|ACTIVE|TOMBSTONE",
  "ownership": "UNOWNED|OWNED|OWNED_BY_OTHER|AUTHOR",
  "partnerBodyPublished": null,
  "canWrite": true,
  "canEdit": false,
  "canDelete": false,
  "canClaim": false
}
```

- 포스트 `deletedAt != null` → `deleted: true` (본문 필드 생략 가능). FE는 삭제 페이지.
- `ownership=AUTHOR` (요청자가 작성자) → 작성·claim 불가, 사연 상세로 안내 플래그.

인증 optional. 있으면 ownership/can* 계산에 사용.

### POST `/api/s/{token}/answer` (기존)

- 작성자 본인 → 403 `AUTHOR_CANNOT_BE_PARTNER`
- 이미 ACTIVE 상대 + unowned가 아님 → 409
- ACTIVE tombstone 후 재작성 / NONE에서 신규 작성 허용
- 로그인 회원 제출 → 즉시 OWNED (`partnerUserId` = 회원)
- 게스트 JWT → partnerUserId = guest id (UNOWNED)
- 무인증 → `partner_{nano}` 지양, 가능하면 게스트 발급 유도. 남기더라도 UNOWNED

### POST `/api/s/{token}/claim` (신규, JWT 회원)

- unowned + not author → `partnerUserId` = 회원
- 그 외 403/409

### PATCH `/api/s/{token}/answer` (신규)

- unowned: 토큰만으로(또는 게스트) 본문 수정
- owned: 소유 JWT만
- tombstone에서는 재작성은 POST answer 사용

### DELETE `/api/s/{token}/answer` (신규)

- 권한: unowned=토큰, owned=소유자
- 상대 본문 clear + `partner_body_deleted_at` set (tombstone)
- 작성자 본문이 이미 tombstone이면 → **완전 삭제** 트리거
- 토큰 유지 (재작성용)

### DELETE `/api/community/posts/{id}` (변경, JWT 작성자)

| 조건 | 결과 |
|---|---|
| 상대 ACTIVE 본문 있음 | 작성자 본문만 tombstone (`author_body_deleted_at`), 제목·상대 유지 |
| 상대 NONE 또는 초대만 있고 미작성 | 포스트 soft-delete + 댓글 삭제(hard 또는 soft 일괄) |
| 상대 TOMBSTONE 이고 작성자 본문 삭제 | 완전 삭제 |
| 작성자 본문 이미 tombstone | idempotent / 완전 삭제 조건 재평가 |

### GET `/api/community/posts/{id}`

- `deletedAt != null` → 410 또는 200 `{ deleted: true }` (FE 합의: **200 + deleted flag** 권장, 기존 404면 FE 분기)
- 응답에 `authorBodyDeleted`, `partnerBodyDeleted` boolean
- tombstone이면 해당 body null + 플래그 true

### 시한부 투표 제거

- 신규 로직에서 `voteCloseAt` / `voteDurationHours` 설정 중지
- `CLOSED`로 투표 잠그지 않음 (공감 투표 상시)
- `publish-mode`의 duration 의미 제거 또는 API deprecated
- DB 컬럼은 즉시 drop 또는 nullable 방치 후 후속 마이그레이션 (1차는 쓰기 중지 + CLOSED 미사용)

## DB (**V107**)

```sql
-- V107__post_side_tombstones.sql
ALTER TABLE posts
  ADD COLUMN author_body_deleted_at TIMESTAMP(6) NULL,
  ADD COLUMN partner_body_deleted_at TIMESTAMP(6) NULL;
```

`vote_close_at` / `vote_duration_hours` 컬럼은 유지하되 **쓰기·제품 동작에서 미사용**(legacy).

## FE 화면

### `/s/[token]`

상태 머신: loading → deleted | author_self | write | manage_unowned | manage_owned | blocked_owned_by_other

- 로그인 링크·가입·OAuth·GuestUpgrade: `next=/s/{token}` 유지
- 제출 전 이탈 초안: `sessionStorage['invite-draft:'+token]`
- CTA: 연결 / 수정 / 삭제 / 다시 작성

### `/community/[id]`

- SideStory 작성자/상대: tombstone 시 「작성자가 글을 삭제했습니다」/「상대방이 글을 삭제했습니다」
- 소유자에게 수정·다시 작성 진입
- `deleted` → 삭제된 게시글 + 광장 버튼
- 초대 버튼: 게스트 차단(기존), 상대 ACTIVE면 숨김·미답변·tombstone 정책은 “미답변일 때만 재복사” → ACTIVE면 재발송 숨김, NONE이면 InviteSheet

### 인증

`login` / `signup` / OAuth state / GuestUpgradeModal 전부 `next`·`redirect` 보존.

## 비범위

- 공감 투표(VoteBar) 제거 금지
- prod 배포
- 네이버/YouTube 등 마케팅 채널
