# 디바이스 ID — 공통 유니크 식별자

## 개요

게스트(비로그인) 포함 모든 접속자를 브라우저/기기 단위로 식별하기 위한 공통 유틸리티.
현재는 **조회수 중복 방지**에 사용하며, 투표·좋아요 등 다른 기능의 중복 방지에도 재사용 가능.

---

## 동작 방식

### FE — `frontend/lib/utils/deviceId.ts`

| 함수 | 설명 |
|---|---|
| `getOrCreateDeviceId()` | localStorage에서 ID 조회, 없으면 UUID v4 생성 후 저장 |
| `deviceToGuestNickname(id)` | ID → "게스트 NNNN" 닉네임 변환 (기존 기능) |

**저장 키**: `again-spring-device-id` (localStorage)

**생명주기**:
- 브라우저 데이터 초기화 전까지 유지
- 시크릿/시크릿 창 → localStorage 격리로 별개 ID 생성 (정상 동작)
- SSR 환경 → `window === undefined`로 빈 문자열 반환 (서버 측 조회 미발생)

### BE — 중복 방지 흐름

```
FE: POST /api/community/posts/{id}/view  { deviceId: "..." }
  └─ ViewService.recordViewAndGetCount()
        ├─ PostView 테이블 INSERT (UK: post_id + device_id)
        │    ├─ 신규 → 성공 → posts.view_count += 1
        │    └─ 중복 → DataIntegrityViolationException → 무시
        └─ 현재 view_count 반환
```

**DB 테이블**: `post_views`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | PK |
| post_id | VARCHAR(32) | FK posts.id |
| device_id | VARCHAR(64) | 브라우저 UUID |
| viewed_at | TIMESTAMP(3) | 기록 시각 |
| — | UNIQUE (post_id, device_id) | 중복 방지 제약 |

---

## 조회수 흐름

1. 사용자가 사연 상세 페이지(`/community/{id}`) 진입
2. FE가 `GET /api/community/posts/{id}` 로 포스트 로드 (응답에 현재 `viewCount` 포함)
3. FE가 `POST /api/community/posts/{id}/view { deviceId }` fire-and-forget
4. BE가 `post_views` unique constraint 기반으로 중복 여부 판단 후 증가
5. 피드 목록(`GET /api/community/posts`)은 `posts.view_count` 컬럼 값 그대로 반환

---

## 다른 기능에 재사용 시

1. FE에서 `getOrCreateDeviceId()` 호출 후 요청 body에 포함
2. BE에서 해당 기능의 `{feature}_device_records` 테이블에 `(target_id, device_id)` unique constraint 적용
3. `DataIntegrityViolationException` catch로 중복 무시
4. `ViewService` 패턴 그대로 복사해서 서비스 작성

---

## 보안 고려사항

- deviceId는 UUIDv4로 사용자 개인정보를 포함하지 않음
- 의도적인 ID 위조 시도는 막지 않음 — 조회수는 신뢰 임계값이 낮은 지표
- 높은 신뢰가 필요한 기능(결제, 이벤트 당첨 등)에는 JWT 인증 결합 필요
