# 아키텍처

## 기술 스택

| 영역 | 기술 |
|---|---|
| 프레임워크 | Next.js 14 (App Router) |
| 언어 | TypeScript 5+ (strict) |
| 런타임 | React 18+ |
| 상태 관리 | Zustand + persist |
| HTTP | axios + Bearer 인터셉터 |
| 스타일 | Tailwind CSS 3+ |
| 테스트 | Vitest, Playwright |
| Mock | MSW 2+ (개발 전용) |

---

## 데이터 흐름

### 광장형 사연 발행 흐름

```
[사용자] → /community/new (작성) → POST /api/community/posts
  ↓
[Backend] Post + VoteOption(작성자/상대방) 저장 (원문 그대로, 게시 시 LLM 미호출)
  ↓
[Frontend] 게시글 상세
  ├─ VoteBar: 작성자 vs 상대방 공감 투표
  └─ CommunityComment: 댓글
```

사람 글 게시 후 커뮤니티 공감 투표·댓글이 제품 핵심이다. AI 반응은 AI-user가 이어간다.

### HTTP 요청 흐름

```
[페이지/컴포넌트]
  ↓
[lib/api/client.ts] axios instance
  ├─ Request Interceptor: Authorization Bearer 헤더 추가
  └─ Response Interceptor: 401/403/402/429 에러 처리
  ↓
[브라우저]
  ├─ 개발 모드: MSW Worker (mocks/handlers/*)로 가로챔
  └─ 프로덕션: 실제 Backend (:8080)로 전송
  ↓
[응답]
  ↓
[React State / Zustand Store]
  ├─ uiStore: 화면 상태 (모달, 필터 등)
  └─ persist: localStorage 동기화 (단, 민감정보 제외)
```

---

## 상태 관리 (Zustand)

### uiStore

```typescript
// lib/store/uiStore.ts
{
  // 모달 상태
  isCrisisModalOpen: boolean
  showCommunityFeedFilter: boolean
  
  // 사용자 인증 상태는 userStore에서 (BE 동기화)
  
  // 임시 입력값
  draftPostTitle: string
  draftPostBody: string
  
  // persist: localStorage에 저장
  // → 새로고침 후 상태 복구
}
```

**세션 상태는 없음** (광장형) — 모든 상태는 서버 동기화 또는 일시적 UI 상태.

---

## API 클라이언트 구조

### lib/api/community.ts

```typescript
// 게시글 관련 API
export const communityApi = {
  getPosts(filters?: { category?: string }),
  getPost(postId: string),
  createPost(body: { ... }),
  voteCommunity(postId: string, vote: 'A' | 'B'),
}
```

### lib/api/user.ts

```typescript
// 사용자 관련 API
export const userApi = {
  getCurrentUser(),
  login(email, password),
  loginWithOAuth(provider, code),
  logout(),
  getPermissions(),
}
```

### 요청/응답 인터셉터

```typescript
// lib/api/client.ts
const client = axios.create({ baseURL: process.env.NEXT_PUBLIC_API_URL })

// Request: Bearer 토큰 추가
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('again-spring-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Response: 에러 처리
client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response.status === 401) redirect('/login')
    if (err.response.status === 403) redirect('/unauthorized')
    if (err.response.status === 429) showRateLimitModal()
    // ...
  }
)
```

---

## 권한 시스템 (3-tier)

```typescript
// lib/constants/userPermissions.ts

enum UserRole {
  GUEST = 'GUEST',           // 비로그인
  USER = 'USER',             // 일반 사용자
  TESTER = 'TESTER',         // QA/테스터
  ADMIN = 'ADMIN',           // 관리자
}

function permissionsFor(role: UserRole) {
  return {
    canReadFeed: true,                  // 모두
    canCreatePost: role !== 'GUEST',    // 로그인 필수
    canVote: role !== 'GUEST',          // 로그인 필수
    canComment: role !== 'GUEST',       // 로그인 필수
    canManageCommunity: role === 'ADMIN',
    canViewAdminDashboard: role === 'ADMIN' || role === 'TESTER',
  }
}
```

---

## MSW (Mock Service Worker)

### 개발 모드에서의 목업

```typescript
// mocks/browser.ts
export const worker = setupWorker(...handlers)

// pages/layout.tsx에서
<MSWProvider>
  <App />
</MSWProvider>
```

### 핸들러 구조

```typescript
// mocks/handlers/community.ts
export const communityHandlers = [
  http.get('/api/posts', () => json([...])),
  http.get('/api/posts/:id', () => json({...})),
  http.post('/api/posts', () => json({...})),
]

// mocks/handlers/notifications.ts
// mocks/handlers/user.ts
```

**중요**: MSW는 dev 전용. prod에서는 무시되고 실제 Backend로 요청.

---

## 파일 조직 원칙

### 페이지 컴포넌트 (`app/**`)
- Server Component 기본 (데이터 페칭)
- 필요시 `'use client'` 선언
- 레이아웃 리소스 활용 (인증 게이트, 헤더 등)

### 재사용 컴포넌트 (`components/**`)
- 도메인별 폴더 (`community/c3/`, `admin/`, `auth/` 등)
- 큰 컴포넌트: 폴더 + index.ts
- 작은 컴포넌트: 단일 파일

### 호출 패턴

```
app/community/page.tsx (Server RSC)
  ↓
  ├─ components/community/c3/FeedCard.tsx (Client)
  │   ├─ hooks: useFeed(), useLike()
  │   └─ lib/api/community.ts
  │
  ├─ components/community/c3/VoteBar.tsx (Client)
  │   └─ lib/api/community.ts
```

---

## 주요 기능별 데이터 흐름

### 1. 피드 열람 (`/community`)

```
Page → api.getPosts()
  ↓
[무한스크롤] Intersection Observer
  ↓
FeedCard 렌더링
```

### 2. 게시글 작성 (`/community/new`)

```
Form → [제목, 본문, 카테고리]
  ↓
POST /api/community/posts { userTitle, bodyRaw, category }
  ↓
[Backend] Post + VoteOption(작성자/상대방) 저장 (LLM 미호출)
  ↓
[Frontend] 상세 페이지 자동 진입
```

### 3. 투표 (`/community/[id]`)

```
VoteBar [작성자 / 상대방]
  ↓
POST /api/community/posts/{id}/vote { optionId }
  ↓
[응답] percentage (작성자 vs 상대방)
  ↓
VoteBar UI 업데이트
```

### 4. 댓글 (`CommentBar`)

```
CommentComposeSheet [로그인 필수]
  ↓
POST /api/community/posts/{id}/comments { content: "..." }
  ↓
[응답] { commentId, author, createdAt, ... }
  ↓
CommentBar 무한스크롤 목록 prepend
```

---

## 부재 기능

> 광장형 피벗으로 인해 제거됨

- **온보딩 (MBTI 테스트)** — 더 이상 필요 없음
- **세션 기반 상태** — 광장형에서 중앙화된 피드로 변경
- **1:1 중재 채팅** — 공개 사연 + 커뮤니티 공감 투표로 대체
- **클라이언트 사이드 키워드 필터** — 서버에서만 검사 (위기 감지)

---

## 마이그레이션 참고

| 구 모델 (V1.5) | 신 모델 (광장형) | 상태 |
|---|---|---|
| 세션 기반 채팅 | 광장 피드 기반 | ✅ 완료 |
| 6턴 중재 | 커뮤니티 공감 투표(작성자 vs 상대방) | ✅ 완료 |
| Solo/Duo 모드 | 단일 광장 (비공개/공개) | ✅ 완료 |
| ContributionRatio | EmpathyRatio (작성자/상대방 %) | ✅ 완료 |

---

