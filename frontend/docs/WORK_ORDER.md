# 다시봄 (Again Spring) — 프론트엔드 프로토타입 작업지시서

**작성일**: 2026-04-24
**대상**: Claude Code
**버전**: v1.1 (프론트 프로토타입 with Mock API + 목업 디자인 통합)
**프로젝트 경로**: `/home/justant/Data/Again-Spring`

---

## 📌 프로젝트 개요

**프로젝트명**: 다시봄 (Again Spring)
**한 줄 설명**: 싸운 두 사람 사이에서 AI 중재자가 양쪽의 입력을 중립적으로 처리해 관계 회복을 돕는 웹앱
**이름의 의미**: 
- "다시봄"의 **봄(spring)** = 관계가 다시 꽃피는 계절
- "다시봄"의 **봄(see)** = 서로를 다시 바라본다는 의미
- 두 의미를 중의적으로 담은 한국어 서비스명

**이번 단계 목표**: Mock API 기반의 프론트엔드 프로토타입 완성 (실제 LLM 연동 없이 UI/UX 플로우 검증)

---

## ⚙️ 기술 스택 (확정)

### 프론트엔드
- **프레임워크**: Next.js 14+ (App Router)
- **언어**: TypeScript
- **스타일**: Tailwind CSS
- **상태 관리**: Zustand (세션 상태는 서버 저장, 클라이언트는 최소)
- **UI 컴포넌트**: shadcn/ui
- **차트/시각화**: Recharts (욕구 차이 지도 2D 좌표)
- **애니메이션**: Framer Motion (페이지 전환, 결과 공개 연출)
- **아이콘**: Lucide React
- **폼**: React Hook Form + Zod (유효성 검사)
- **HTTP 클라이언트**: Axios or Fetch (Mock API 호출)

### 모바일 확장 준비
- **Capacitor** 설치 준비 (이번 단계에서 실제 모바일 빌드는 안 함, 구조만 준비)
- 모든 네이티브 의존 기능은 추상화 레이어로 분리

### Mock API
- **MSW (Mock Service Worker)**: 네트워크 레벨에서 API Mock
- 실제 백엔드 개발 전까지 프론트 단독 개발 가능
- 백엔드 붙을 때 MSW만 제거하면 됨

### 향후 백엔드 (참고용, 이번 단계 구현 X)
- Spring Boot 3.x
- MongoDB: 세션 원문, 대화 이력 (30일 TTL)
- MariaDB: User, Subscription, Report 메타
- Neo4j: 관계 그래프 (A-B 관계, 히스토리 연결)
- Redis: 세션 실시간 상태
- Anthropic Claude API: LLM

---

## 🎯 프로토타입 구현 범위

### ✅ 포함 (MVP 프론트)
1. 회원가입 / 로그인 / 게스트 모드
2. 온보딩 10문항 경향성 테스트
3. 세션 시작 플로우 (관계 유형 → 대/중/소분류 → 상황 서술)
4. 초대 링크 생성 화면
5. B 참여 플로우 (초대 링크 랜딩)
6. 6턴 멀티턴 중재 UI (Mock으로 AI 응답 시뮬레이션)
7. 앵커링 방지 입력 격리
8. 결과 리포트 화면 (욕구 차이 지도 포함)
9. Solo 모드
10. 공유 이미지 생성 (프론트에서 Canvas/SVG로)
11. 법적 리스크 가드 (키워드 감지 프론트 레벨)
12. 세션 이력 페이지 (회원만)

### ❌ 제외 (다음 단계)
- 실제 LLM API 호출
- 결제/구독
- 실시간 진정 모드 (Cooldown Timer)
- 관계 점검 모드
- Repair Attempt Library
- 모바일 앱 빌드

---

## 📂 프로젝트 구조

**프로젝트 루트**: `/home/justant/Data/Again-Spring`

```
Again-Spring/
├── docs/                         # 이 작업지시서 문서들
│   ├── WORK_ORDER.md             # 이 파일
│   ├── README.md
│   ├── CATEGORIES.md
│   ├── SYSTEM_PROMPTS.md
│   ├── ONBOARDING_MAPPING.md
│   ├── RATIO_CALCULATION.md
│   ├── FORBIDDEN_WORDS.md
│   ├── MOCK_SCENARIOS.md
│   ├── TERMS_OF_SERVICE.md
│   └── MOCKUP_INTEGRATION.md     # 목업 디자인 통합 가이드 (신규)
│
├── design/                       # 🎨 Claude Design으로 제작된 목업 파일 (사용자가 추가 예정)
│   ├── mockups/                  # 최종 확정된 목업 (HTML/이미지/Figma 파일)
│   │   ├── 00-landing/
│   │   ├── 01-onboarding/
│   │   ├── 02-session-start/
│   │   ├── 03-category-select/
│   │   ├── 04-describe/
│   │   ├── 05-invite/
│   │   ├── 06-wait/
│   │   ├── 07-join-b/
│   │   ├── 08-mediation/
│   │   ├── 09-result/
│   │   ├── 10-solo-mode/
│   │   └── 11-history/
│   ├── tokens/                   # 디자인 토큰 (색상, 타이포, 간격)
│   │   └── design-tokens.json
│   └── README.md                 # 목업 작업 이력 및 버전 관리
│
├── app/                          # Next.js App Router
│   ├── (auth)/
│   ├── (onboarding)/
│   ├── (session)/
│   ├── (dashboard)/
│   ├── layout.tsx
│   └── page.tsx
│
├── components/
│   ├── ui/                       # shadcn/ui 컴포넌트
│   ├── mediation/
│   ├── result/
│   ├── onboarding/
│   └── shared/
│
├── lib/
│   ├── api/
│   ├── store/
│   ├── utils/
│   ├── constants/
│   └── types/
│
├── mocks/                        # MSW Mock API
│   ├── handlers/
│   ├── fixtures/
│   └── browser.ts
│
├── public/
│   ├── share-templates/
│   └── fonts/
│
├── styles/
│   └── globals.css
│
├── .env.local.example
├── next.config.js
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── README.md
```

### 페이지 상세 구조 (app 디렉토리)

```
app/
├── (auth)/
│   ├── signup/page.tsx
│   ├── login/page.tsx
│   └── guest/page.tsx
├── (onboarding)/
│   └── onboarding/page.tsx       # 10문항 테스트
├── (session)/
│   ├── new/page.tsx              # 세션 시작 (관계 유형 선택)
│   ├── category/page.tsx         # 대/중/소분류
│   ├── describe/page.tsx         # 상황 서술
│   ├── invite/page.tsx           # 초대 링크 생성
│   ├── wait/page.tsx             # B 참여 대기
│   ├── join/[token]/page.tsx     # B 참여 랜딩
│   ├── mediation/page.tsx        # 6턴 중재 진행
│   └── result/[id]/page.tsx      # 결과 리포트
├── (dashboard)/
│   ├── history/page.tsx          # 세션 이력
│   └── profile/page.tsx          # 프로필
├── layout.tsx
└── page.tsx                      # 랜딩 페이지
```

---

## 🎨 목업 디자인 통합 프로세스 (중요!)

### 디자인 작업 흐름

본 프로젝트의 목업 디자인은 **Claude Design**을 통해 별도로 제작됩니다.
목업이 완성되는 시점은 개발 Phase와 **비동기적**으로 진행되므로 Claude Code는 다음 규칙을 따릅니다:

### 규칙 1: 목업 존재 여부 확인

Claude Code는 각 화면을 구현하기 **직전**에 `design/mockups/` 폴더에서 해당 화면의 목업 존재 여부를 반드시 확인합니다.

```bash
# 예: 랜딩 페이지 구현 전
ls design/mockups/00-landing/
```

### 규칙 2: 목업 존재 시 동작

`design/mockups/XX-XXX/` 폴더에 파일이 존재하면:
1. 해당 폴더의 `SPEC.md` (있는 경우) 또는 이미지/HTML 파일을 먼저 확인
2. 목업의 레이아웃, 색상, 타이포그래피, 간격을 **최대한 충실히 재현**
3. 구현 후 목업과 비교하여 차이가 있으면 주석으로 명시

### 규칙 3: 목업 부재 시 동작

목업이 아직 제공되지 않은 경우:
1. 본 `WORK_ORDER.md`의 "디자인 가이드" 섹션 기준으로 **기본 디자인 구현**
2. 파일 상단에 다음 주석 추가:
   ```tsx
   // ⚠️ MOCKUP PENDING: design/mockups/XX-XXX/ 에 목업 추가 시 재작업 필요
   ```
3. 사용자가 목업 추가 후 재작업 요청 시 즉시 반영

### 규칙 4: 목업 업데이트 시 동작

사용자가 목업을 나중에 추가하거나 업데이트할 경우 다음과 같이 명령합니다:

```
"design/mockups/08-mediation/ 에 목업을 추가했어. 
해당 화면을 목업에 맞춰 재작업해줘."
```

Claude Code는 이 명령을 받으면:
1. 해당 디렉토리의 모든 파일 확인 (이미지, HTML, SPEC.md 등)
2. 기존 구현 코드와 목업의 차이점 분석
3. 레이아웃, 컴포넌트 구조, 스타일을 목업 기준으로 재구성
4. 기능 로직은 그대로 유지하되 UI만 교체
5. 재작업 후 주석 업데이트:
   ```tsx
   // ✅ MOCKUP APPLIED: design/mockups/08-mediation/ (updated: 2026-XX-XX)
   ```

### 목업 폴더 구조 (사용자가 제공할 형식)

각 화면별 `design/mockups/XX-XXX/` 폴더 권장 구성:

```
08-mediation/
├── SPEC.md              # 디자인 스펙 및 의도 설명 (선택)
├── desktop.png          # 데스크톱 뷰 (1440px)
├── mobile.png           # 모바일 뷰 (375px)
├── mockup.html          # HTML 목업 (있는 경우 우선 참조)
├── assets/              # 이미지, SVG 등
└── notes.md             # 추가 메모 (선택)
```

### SPEC.md 템플릿 (참고용)

목업 폴더의 `SPEC.md` 작성 시 포함 권장 항목:

```markdown
# [화면명] 디자인 스펙

## 의도
이 화면에서 사용자가 느껴야 할 감정과 행동 목표

## 주요 컴포넌트
- 중재자 말풍선: 좌측 정렬, 라벤더 계열 배경
- 턴 진행 바: 상단 고정, 6개 도트
- ...

## 인터랙션
- 입력 제출 시: fade-out → 중재자 답변 typing 애니메이션

## 색상 특이사항
- 중재자 메시지 배경: #F5F3FF
- A측 강조: #CECBF6
- B측 강조: #F4C0D1

## 주의사항
- 상대방 원문 미리보기 절대 금지
```

### 우선순위 높은 화면 (목업 우선 제작 권장)

다음 화면들은 **서비스의 시그니처**이므로 목업이 가장 중요:

| 우선순위 | 화면 | 이유 |
|---|---|---|
| 🔴 최우선 | `09-result/` | 욕구 차이 지도 = 바이럴 핵심 |
| 🔴 최우선 | `08-mediation/` | 6턴 중재 UI = 사용자 체류 시간 최대 |
| 🟡 높음 | `00-landing/` | 첫인상 |
| 🟡 높음 | `07-join-b/` | B 끌어오기 성공률 직결 |
| 🟢 중간 | `01-onboarding/` | 간단한 구조 |
| 🟢 중간 | `03-category-select/` | 리스트 UI 기본 |
| 🔵 낮음 | 나머지 | 기본 디자인으로도 충분 |

### Claude Code가 목업 부재 시 사용자에게 물어볼 질문

구현 중 목업 가이드만으로 판단 어려울 때:
```
⚠️ [화면명]의 [특정 UI 요소]에 대해 목업이 없어 판단이 필요합니다.
다음 중 어떤 방향으로 구현할까요?

(A) 옵션 1 - 설명
(B) 옵션 2 - 설명

또는 design/mockups/XX-XXX/에 목업을 추가해주시면 해당 디자인으로 구현하겠습니다.
```

---

## 🗂️ 데이터 모델 (TypeScript 타입)

### lib/types/session.ts

```typescript
export type RelationType = 'couple' | 'marriage' | 'friend' | 'family' | 'parent_child';

export type ConflictType = 'factual' | 'difference' | 'mixed';

export type ParticipantRole = 'A' | 'B';

export type SessionStatus =
  | 'waiting_b'
  | 'b_joined'
  | 'in_mediation'
  | 'completed'
  | 'solo_mode'
  | 'terminated';

export interface Session {
  id: string;
  createdBy: string; // User ID (A)
  inviteToken: string;
  inviteeId?: string; // B User ID (가입 시)
  inviteeGuestName?: string; // B 게스트 이름
  relationType: RelationType;
  category: {
    major: string; // 대분류
    middle: string; // 중분류
    minor: string; // 소분류
  };
  status: SessionStatus;
  currentTurn: number; // 1~6
  turns: Turn[];
  createdAt: Date;
  completedAt?: Date;
  reportId?: string;
}

export interface Turn {
  turnNumber: number; // 1~6
  role: ParticipantRole;
  content: string;
  mediatorMessage?: string; // 중재자가 생성한 질문/요약
  isPerspectiveTaking?: boolean; // Turn 5-6 여부
  skipped?: boolean;
  createdAt: Date;
}

export interface Report {
  id: string;
  sessionId: string;
  conflictType: ConflictType;
  contributionRatio: {
    a: number; // 0-100
    b: number; // 0-100
    label: {
      a: string;
      b: string;
    };
  };
  needsMap: {
    axisX: string;
    axisY: string;
    positionA: { x: number; y: number };
    positionB: { x: number; y: number };
    interpretation: string;
  };
  temperature: number; // 35.0 ~ 38.0
  fourHorsemen: {
    criticism: { detected: boolean; examples?: string[] };
    defensiveness: { detected: boolean; examples?: string[] };
    contempt: { detected: boolean; examples?: string[] };
    stonewalling: { detected: boolean; examples?: string[] };
  };
  nvcScripts: {
    aToB: NVCScript;
    bToA: NVCScript;
  };
  repairSuggestions: string[];
  isSoloMode: boolean;
  createdAt: Date;
}

export interface NVCScript {
  observation: string;
  feeling: string;
  need: string;
  request: string;
}
```

### lib/types/user.ts

```typescript
export type CommunicationStyle =
  | 'wave'      // 🌊 파도형
  | 'mountain'  // 🏔️ 산형
  | 'flame'     // 🔥 불꽃형
  | 'leaf'      // 🌿 이파리형
  | 'moon'      // 🌙 달빛형
  | 'star';     // ⭐ 별빛형

export interface User {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  communicationStyle?: CommunicationStyle;
  onboardingAnswers?: number[]; // 10개의 1-5 점수
  temperatureHistory: TemperatureEntry[];
  createdAt: Date;
}

export interface TemperatureEntry {
  sessionId: string;
  partnerId: string;
  temperature: number;
  recordedAt: Date;
}
```

---

## 📋 화면별 구현 태스크

각 태스크는 체크박스로 표시. Claude Code는 순서대로 진행.
**각 Phase 시작 전 `design/mockups/` 폴더 확인 필수.**

### Phase 1: 프로젝트 세팅

- [ ] **Task 1.1** 프로젝트 디렉토리 생성
  ```bash
  cd /home/justant/Data
  mkdir -p Again-Spring
  cd Again-Spring
  ```
- [ ] **Task 1.2** Next.js 14 프로젝트 생성 (기존 디렉토리에)
  ```bash
  npx create-next-app@latest . --typescript --tailwind --app --src-dir=false
  ```
- [ ] **Task 1.3** 의존성 설치
  ```bash
  npm install zustand axios framer-motion lucide-react recharts react-hook-form zod @hookform/resolvers
  npm install -D msw@latest
  npx shadcn@latest init
  ```
- [ ] **Task 1.4** shadcn 기본 컴포넌트 설치: Button, Input, Card, Dialog, RadioGroup, Progress, Toast
- [ ] **Task 1.5** MSW 초기 설정 (`mocks/browser.ts`, `app/layout.tsx`에 MSW 초기화)
- [ ] **Task 1.6** 폴더 구조 생성 (위 구조대로)
  - `docs/`, `design/mockups/`, `design/tokens/` 디렉토리 포함
- [ ] **Task 1.7** `docs/` 폴더에 모든 MD 문서 배치 확인
- [ ] **Task 1.8** `design/README.md` 생성 — 목업 추가 가이드 안내

### Phase 2: 상수 및 타입 정의

- [ ] **Task 2.1** `lib/types/*.ts` 전체 타입 정의 작성
- [ ] **Task 2.2** `lib/constants/categories.ts` 작성 → `docs/CATEGORIES.md` 참조
- [ ] **Task 2.3** `lib/constants/onboardingQuestions.ts` 작성 → 10문항 전체
- [ ] **Task 2.4** `lib/constants/forbiddenWords.ts` 작성 → 키워드 가드 리스트
- [ ] **Task 2.5** `lib/constants/crisisResources.ts` 작성 → 전문기관 연락처
- [ ] **Task 2.6** `lib/utils/keywordGuard.ts` 함수 구현 (Level 1/2/3 탐지)

### Phase 3: 랜딩 및 인증 화면

**🎨 구현 전 확인**: `design/mockups/00-landing/`

- [ ] **Task 3.1** 랜딩 페이지 (`app/page.tsx`)
  - 메인 카피: "다시 봄. 다시 바라봄."
  - 서브 카피: "싸운 우리, AI 중재자가 도와드릴게요"
  - CTA 2개: "지금 시작하기" (로그인) / "둘러보기" (게스트)
  - 서비스 소개 섹션 3개 (앵커링 방지 / Gottman 기반 / 프라이버시)
- [ ] **Task 3.2** 회원가입 페이지 — 닉네임, 이메일, 비밀번호
- [ ] **Task 3.3** 로그인 페이지
- [ ] **Task 3.4** 게스트 모드 진입 페이지

### Phase 4: 온보딩 10문항 테스트

**🎨 구현 전 확인**: `design/mockups/01-onboarding/`

- [ ] **Task 4.1** `components/onboarding/LikertQuestion.tsx` — 5점 척도 컴포넌트
- [ ] **Task 4.2** `app/(onboarding)/onboarding/page.tsx` — 한 화면에 한 문항씩, 진행률 표시
- [ ] **Task 4.3** 결과 계산 로직 → 6가지 스타일 중 1개로 매핑 (`docs/ONBOARDING_MAPPING.md` 참조)
- [ ] **Task 4.4** 결과 화면 — 스타일 카드 애니메이션 공개, 공유 버튼
- [ ] **Task 4.5** 게스트는 온보딩 스킵 가능, 회원은 필수

### Phase 5: 세션 시작 플로우 (A측)

**🎨 구현 전 확인**: 
- `design/mockups/02-session-start/`
- `design/mockups/03-category-select/`
- `design/mockups/04-describe/`
- `design/mockups/05-invite/`
- `design/mockups/06-wait/`

- [ ] **Task 5.1** `app/(session)/new/page.tsx` — 관계 유형 선택 (카드 UI 5개)
- [ ] **Task 5.2** `app/(session)/category/page.tsx` — 대/중/소분류 단계별 선택
- [ ] **Task 5.3** `app/(session)/describe/page.tsx` — 상황 서술
- [ ] **Task 5.4** `app/(session)/invite/page.tsx` — 초대 링크 생성
- [ ] **Task 5.5** `app/(session)/wait/page.tsx` — B 참여 대기 화면

### Phase 6: B 참여 플로우

**🎨 구현 전 확인**: `design/mockups/07-join-b/`

- [ ] **Task 6.1** `app/(session)/join/[token]/page.tsx` — 초대 링크 랜딩
- [ ] **Task 6.2** B의 간단 가입/게스트 선택
- [ ] **Task 6.3** B의 상황 서술

### Phase 7: 6턴 멀티턴 중재 UI

**🎨 구현 전 확인**: `design/mockups/08-mediation/` (최우선 목업!)

- [ ] **Task 7.1** `app/(session)/mediation/page.tsx` — 메인 중재 화면
- [ ] **Task 7.2** `components/mediation/MediatorMessage.tsx` — 중재자 말풍선
- [ ] **Task 7.3** `components/mediation/TurnInput.tsx` — 턴 입력 컴포넌트
- [ ] **Task 7.4** `components/mediation/ProgressBar.tsx` — 6턴 진행률 바
- [ ] **Task 7.5** 앵커링 방지 규칙 준수
- [ ] **Task 7.6** Turn 5-6 스킵 버튼 구현

### Phase 8: 결과 리포트 화면 (시그니처!)

**🎨 구현 전 확인**: `design/mockups/09-result/` (최우선 목업!)

- [ ] **Task 8.1** `app/(session)/result/[id]/page.tsx` — 리포트 메인 페이지
- [ ] **Task 8.2** `components/result/NeedsMap.tsx` — 🗺️ 욕구 차이 지도 (시그니처!)
- [ ] **Task 8.3** `components/result/Temperature.tsx` — 관계 온도
- [ ] **Task 8.4** `components/result/ContributionRatio.tsx` — 화해 기여도
- [ ] **Task 8.5** `components/result/FourHorsemen.tsx`
- [ ] **Task 8.6** `components/result/NVCScript.tsx` — NVC 재작성
- [ ] **Task 8.7** `components/result/RepairSuggestions.tsx`
- [ ] **Task 8.8** `components/result/ShareImage.tsx` — 공유 이미지 생성

### Phase 9: Solo 모드

**🎨 구현 전 확인**: `design/mockups/10-solo-mode/`

- [ ] **Task 9.1** Solo 모드 진입
- [ ] **Task 9.2** Solo 모드 중재 UI (3턴 축소)
- [ ] **Task 9.3** Solo 모드 결과 리포트 (워터마크)

### Phase 10: 세션 이력 및 프로필

**🎨 구현 전 확인**: `design/mockups/11-history/`

- [ ] **Task 10.1** `app/(dashboard)/history/page.tsx` — 세션 이력 리스트
- [ ] **Task 10.2** `app/(dashboard)/profile/page.tsx` — 프로필

### Phase 11: Mock API 구현

- [ ] **Task 11.1** `mocks/handlers/session.ts` — 세션 CRUD Mock
- [ ] **Task 11.2** `mocks/handlers/mediation.ts` — 6턴 중재 AI 응답 시뮬레이션
- [ ] **Task 11.3** `mocks/handlers/user.ts` — 사용자 CRUD Mock
- [ ] **Task 11.4** `mocks/fixtures/mockReports.ts` — 5가지 샘플 리포트
  (`docs/MOCK_SCENARIOS.md` 참조)

### Phase 12: 법적 리스크 가드

- [ ] **Task 12.1** `components/shared/KeywordGuard.tsx` — HOC 래퍼
- [ ] **Task 12.2** `components/shared/CrisisResource.tsx` — 전문기관 리소스 모달
- [ ] **Task 12.3** 이용약관 페이지 (`app/terms/page.tsx`) — `docs/TERMS_OF_SERVICE.md` 참조
- [ ] **Task 12.4** 개인정보 처리방침 페이지
- [ ] **Task 12.5** 모든 결과 화면 하단 고정 푸터

### Phase 13: 반응형 및 접근성

- [ ] **Task 13.1** 모든 페이지 모바일 반응형 (320px ~)
- [ ] **Task 13.2** 키보드 네비게이션 및 ARIA 레이블
- [ ] **Task 13.3** 다크모드 지원 (Tailwind `dark:` prefix)

### Phase 14: 테스트 및 빌드

- [ ] **Task 14.1** 주요 플로우 E2E 테스트 시나리오 실행
- [ ] **Task 14.2** Lighthouse 점수 확인 (Performance 80+, Accessibility 90+)
- [ ] **Task 14.3** 프로덕션 빌드 확인 (`npm run build`)
- [ ] **Task 14.4** Capacitor 초기 설정 준비
  ```bash
  npm install @capacitor/core @capacitor/cli
  npx cap init "다시봄" "com.againspring.app"
  ```

---

## 🎨 디자인 가이드 (목업 부재 시 기본값)

### 서비스 컨셉 키워드
- "다시 바라봄" → 따뜻하고 평온한 분위기
- "봄" → 부드러운 파스텔 톤, 꽃잎/새싹 모티프
- "중재자" → 중립적, 신뢰감 있는 UI

### 컬러 팔레트 (기본)
- **Primary (봄 연두)**: `#7FB77E` — 중재자 메시지, CTA
- **Secondary (봄 라벤더)**: `#B4A6E3` — 서브 포인트
- **A측 컬러**: `#CECBF6` (Purple 100 배경), `#26215C` (텍스트)
- **B측 컬러**: `#F4C0D1` (Pink 100 배경), `#4B1528` (텍스트)
- **경고 (경고)**: `#EF4444` (Red 500)
- **성공 (화해)**: `#10B981` (Emerald 500)
- **중립 배경**: `#FAF9F6` (부드러운 따뜻한 회색)

### 타이포그래피
- **폰트**: Pretendard (한글 가독성 최상)
- **크기**: h1 24px, h2 20px, h3 16px, body 14-16px

### 톤 & 보이스
- 존댓말 사용
- 따뜻하고 차분하게
- "~하셨을 수 있어요" 가정적 표현
- 명령형 금지 ("해보세요" O, "하세요" X)

### 금지 단어 UI 레벨
- "과실", "판결", "승자", "패자", "가해자", "피해자" — 절대 UI에 표시 안 함
- 대체어 사전은 `docs/FORBIDDEN_WORDS.md` 참조

**⚠️ 목업이 있는 경우 목업의 컬러/타이포를 최우선으로 따를 것.**

---

## 📎 참조 문서

프로젝트 `docs/` 폴더 내:

1. **`WORK_ORDER.md`** — 이 파일
2. **`SYSTEM_PROMPTS.md`** — Gottman + NVC LLM 프롬프트
3. **`CATEGORIES.md`** — 대/중/소분류 전체 데이터
4. **`ONBOARDING_MAPPING.md`** — 10문항 → 6스타일 매핑 로직
5. **`RATIO_CALCULATION.md`** — 화해 기여도 계산 알고리즘
6. **`FORBIDDEN_WORDS.md`** — 금지어 및 대체어 사전
7. **`MOCK_SCENARIOS.md`** — Mock API 시나리오 샘플
8. **`TERMS_OF_SERVICE.md`** — 이용약관 초안
9. **`MOCKUP_INTEGRATION.md`** — 목업 디자인 통합 상세 가이드

---

## ✅ 완료 조건 (Definition of Done)

1. 모든 Phase 태스크 체크박스 완료
2. `npm run dev`로 로컬 실행 성공 (경로: `/home/justant/Data/Again-Spring`)
3. Mock API로 전체 플로우 E2E 시뮬레이션 가능
4. 주요 3가지 시나리오 (사실형/차이형/혼합형) 더미 리포트 확인 가능
5. 반응형 확인 (모바일 320px, 태블릿 768px, 데스크톱 1024px+)
6. 프로덕션 빌드 성공 (`npm run build` 에러 없음)
7. README.md에 실행 방법 및 Mock 모드 안내 작성
8. 제공된 목업 모두 반영 완료 (목업 있는 화면 기준)

---

## 🚨 Claude Code 작업 원칙

1. **프로젝트 경로 고정** — 모든 작업은 `/home/justant/Data/Again-Spring` 기준
2. **한 Phase씩 완료 후 진행** — 이전 Phase 미완료 상태로 다음 Phase 넘어가지 않기
3. **목업 우선 확인** — 각 화면 구현 전 `design/mockups/` 해당 폴더 확인
4. **타입 안전성 우선** — `any` 사용 금지, 모든 props/state 타입 정의
5. **컴포넌트 재사용성** — 비슷한 UI가 2번 이상 나오면 컴포넌트화
6. **접근성 준수** — 모든 인터랙티브 요소에 ARIA 속성, 키보드 지원
7. **커밋 단위** — Phase별 또는 주요 기능별로 커밋
8. **금지 단어 검사** — 커밋 전 `FORBIDDEN_WORDS.md` 리스트로 UI 문구 검증
9. **키워드 가드 테스트** — 위험 키워드 입력 시 반드시 가드 작동 확인
10. **주석 규칙** — 목업 미반영 파일 상단에 `// ⚠️ MOCKUP PENDING`, 반영 후 `// ✅ MOCKUP APPLIED` 주석

---

## 📞 이슈 발생 시

- 스펙 모호한 경우: `docs/WORK_ORDER.md` 해당 섹션 재확인
- 디자인 판단 필요: `design/mockups/` 확인 후 없으면 사용자(달콩)에게 확인 요청
- 기술 선택: 이 문서 기본 스택 준수, 변경 필요 시 사용자 승인
- 목업 추가/변경 시: 사용자 명령 대기 후 재작업

---

## 🔔 사용자(달콩) 명령 패턴 예시

Claude Code가 인식해야 할 일반적 명령 패턴:

### 초기 개발 시작
```
"/home/justant/Data/Again-Spring에서 docs/WORK_ORDER.md를 읽고 
Phase 1부터 순서대로 진행해줘."
```

### 목업 반영 요청
```
"design/mockups/09-result/에 목업을 추가했어. 
해당 화면을 목업에 맞춰 재작업해줘."
```

### 특정 Phase 재검토
```
"Phase 7 중재 UI 다시 확인하고 목업 있는지 보고 재작업해줘."
```

### 부분 기능 추가
```
"docs/MOCK_SCENARIOS.md의 시나리오 5(Four Horsemen 모두 탐지)를 
Mock에 추가 반영해줘."
```

---

**끝.**
