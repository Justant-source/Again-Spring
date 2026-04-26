# 디자인 핸드오프 및 컴포넌트 매핑

본 문서는 Claude Design으로 제작된 목업을 프로젝트의 React 컴포넌트에 통합하는 방법을 설명합니다. 디자인 파일은 `frontend/design/handoff/`에 위치하며, 화면별 확정 목업은 `frontend/design/mockups/XX-XXX/` 폴더에 조직돼 있습니다.

---

## 목업 파일 구성

### 기본 경로
```
frontend/design/
├── handoff/                    # Claude Design 원본 (HTML/JSX/CSS)
│   ├── Again Spring Mockup.html     # 전체 디자인 캔버스
│   ├── styles.css                   # 3-Tone 공유 토큰
│   ├── primitives.jsx, icons.jsx    # 기본 컴포넌트
│   └── tone-L/P/Q-screens.jsx       # 톤별 화면 프로토타입
├── mockups/XX-XXX/             # 화면별 확정 목업
│   ├── SPEC.md                 # 디자인 의도·인터랙션
│   ├── desktop.png / mobile.png  # 레이아웃 레퍼런스
│   ├── mockup.html            # 구현 가능한 HTML (있으면)
│   └── assets/                # 이미지·SVG 에셋
└── tokens/
    └── design-tokens.json     # 색상·타이포·간격 토큰
```

### 목업 폴더 명명 규칙
```
00-landing/      # 랜딩 페이지
01-onboarding/   # 10문항 온보딩 테스트
02-session-start/ # 관계 유형 선택
03-category-select/ # 대/중/소분류
04-describe/     # 상황 서술
05-invite/       # 초대 링크
06-wait/         # B 참여 대기
07-join-b/       # B 참여
08-chat/         # 카톡식 채팅 세션
09-result/       # 결과 리포트
10-solo-mode/    # Solo 모드 결과
11-history/      # 세션 이력
```

---

## 디자인 시스템 — 3-Tone Hybrid

모든 UI는 화면 성격에 따라 세 가지 톤 중 하나를 사용합니다. 절대 섞지 마세요.

### Tone L — 편지지 (Letter)
**용도**: 온보딩, 입력 플로우, 카톡식 채팅 세션

팔레트:
- 배경: `#F5EFE6` (크림 베이지)
- 텍스트: `#2B2B2B` (잉크 블랙)
- 보조 텍스트: `#8A7F6B` (먹먹한 갈색)
- 보더: `#D9CFBD`
- 포인트: `#8A3A1F` (테라코타, 아주 드물게)

특징:
- 제목: `var(--font-serif)` (Nanum Myeongjo / Noto Serif KR)
- 본문: Pretendard
- 라운드: 2~4px (거의 각짐)
- 여백: 40px padding, 넉넉하게
- 느낌: "잘 정돈된 편지지"

### Tone P — 파스텔 (Pastel)
**용도**: 결과 리포트, 카톡 공유 이미지, 관계 온도, 욕구 차이 지도

팔레트:
- 배경: `#FBF3EC` (크림 피치)
- 카드: `#FFF8F0` (오프화이트)
- A 포인트: `#F4A896` (저채도 피치)
- B 포인트: `#A8C8B4` (저채도 세이지)
- 텍스트: `#5C4030` (따뜻한 다크브라운)
- 보조: `#A08670`

특징:
- 타이포: Pretendard 전용. 제목만 `font-weight: 500`
- 라운드: **14~20px (크게)**
- 아이콘: 단순한 원·점·선. 이모지는 최소한만
- 느낌: "따뜻한 MBTI 결과지"

### Tone Q — 조용한 고급감 (Quiet)
**용도**: PDF 리포트, Premium 결제 화면, 상담사 연결 화면

팔레트:
- 배경: `#FAFAF7` (오프화이트)
- 카드: `#FFFFFF`
- 텍스트: `#1A1A1A`
- 보조: `#9B9890`
- 보더: `#E8E6E0` (얇게)
- 포인트: `#6B7A8F` (블루그레이)

특징:
- 타이포: Pretendard + Inter. 영문 소문자 레이블 활용
- 라운드: 8~12px
- 느낌: "Linear / Arc Browser"

---

## 절대 금지 사항

- ❌ 하트, 손잡는 일러스트, 무지개 그라데이션
- ❌ Duolingo식 마스코트 캐릭터
- ❌ "과실비율", "판결", "가해자/피해자" 등 판결/병리 용어
- ❌ 다크모드 기본값
- ❌ 3D 글래스모피즘, 네온, 그라데이션 효과
- ❌ `bold` (font-weight 700) 혼용 → 500만 사용
- ❌ Title Case / ALL CAPS 한국어

---

## 컴포넌트 매핑 테이블

| 목업 | React 컴포넌트 | 경로 | 톤 |
|---|---|---|---|
| 00-landing | Landing | `app/page.tsx` | L |
| 01-onboarding | Onboarding, LikertQuestion | `app/(onboarding)/onboarding/page.tsx` | L |
| 02-session-start | SessionStart, RelationshipSelector | `app/session/new/page.tsx` | L |
| 03-category-select | CategorySelect | `app/session/category/page.tsx` | L |
| 04-describe | DescribeFlow, KeywordGuard | `app/session/describe/page.tsx` | L |
| 05-invite | InviteFlow | `app/session/invite/page.tsx` | L |
| 06-wait | WaitingB | `app/session/wait/page.tsx` | L |
| 07-join-b | JoinB, PartnerSummary | `app/session/join/[token]/page.tsx` | L |
| 08-chat | ChatLayout, ChatPanel, MessageBubble | `app/session/[sessionId]/page.tsx` | L |
| 09-result | ReportLayout, NeedsMap, ContributionRatio | `app/session/result/[id]/page.tsx` | P |
| 10-solo-mode | SoloResult | `app/session/result/[id]/solo/page.tsx` | P |
| 11-history | SessionHistory | `app/(dashboard)/history/page.tsx` | L |

---

## 캐피 톤

모든 텍스트는 다음 원칙을 따릅니다:

- 존댓말 기본. 따뜻하고 차분하게.
- "~하셨을 수 있어요", "~처럼 느끼셨을 것 같아요" 가정형
- 양쪽 호명 순서 균형 (A 먼저 말했으면 다음엔 B 먼저)
- 헤드라인은 짧고 시적. 예: "지금, 누구와의 이야기인가요?", "당신의 마음을 들려주세요."

---

## 시그니처 요소 (일관되게 유지)

### 욕구 차이 지도
- 2D 차트, A는 피치 원, B는 세이지 원
- 축 레이블은 세리프 이탤릭

### 관계 온도
- `36.2°C` 형식 (소수점 1자리 고정)

### 화해 기여도
- `55 : 45` 또는 `55 · 45` 형식 (5 단위 반올림)

### 커뮤니케이션 스타일
- 자연물 은유 (파도형 🌊, 산형 🏔️, 불꽃형 🔥 등)

---

## 프로그레스 표시

입력 플로우 4단계는 상단에 작은 대시로 표시. 숫자 아닌 시각 위주:
```
■ ■ — —   (2/4 진행 중)
```

---

## 공유 이미지 규격

- 비율: 9:16 (인스타 스토리 / 카톡 공유 세로형)
- 해상도: 1080×1920
- 톤: **Tone P (파스텔) 강제**
- 하단 워터마크: `다시봄 · again-spring.com`
- 금지: 갈등 원문 절대 노출 금지. 축·수치·스타일명만.

---

## 디자인 토큰 JSON

`frontend/design/tokens/design-tokens.json`에는 다음 정보가 포함돼 있습니다:

```json
{
  "colors": {
    "primary": {
      "spring": "#7FB77E",
      "lavender": "#B4A6E3"
    },
    "background": {
      "base": "#FAF9F6",
      "card": "#FFFFFF"
    },
    "participant": {
      "a": { "bg": "#CECBF6", "text": "#26215C" },
      "b": { "bg": "#F4C0D1", "text": "#4B1528" }
    }
  },
  "typography": {
    "fontFamily": "Pretendard",
    "heading": { "size": 24, "weight": 700 },
    "body": { "size": 15, "weight": 400 }
  },
  "spacing": {
    "section": 32,
    "component": 20
  },
  "radius": {
    "card": 16,
    "button": 12
  }
}
```

Claude Code는 이 JSON을 `tailwind.config.ts`에 매핑합니다.

---

## 목업 반영 상태

각 파일 상단에 다음 주석 중 하나를 반드시 추가하세요:

**목업 반영 완료**:
```tsx
// ✅ MOCKUP APPLIED
// Source: design/mockups/09-result/
// Applied: 2026-04-26
```

**목업 부재, 기본 디자인**:
```tsx
// ⚠️ MOCKUP PENDING
// Target: design/mockups/09-result/
// 목업 추가 시 재작업 필요
```

**목업 일부 반영 (불완전)**:
```tsx
// 🟡 MOCKUP PARTIAL
// Source: design/mockups/09-result/
// Note: 욕구 차이 지도만 반영, 상단 헤더는 기본 디자인
```

---

## 목업 업데이트 절차

사용자가 목업을 추가하거나 수정한 후 Claude Code에 재작업을 명령할 때:

1. 해당 목업 폴더 재스캔 (`design/mockups/XX-XXX/`)
2. 기존 구현 코드의 상단 주석 확인
3. 목업과의 차이점 파악 (레이아웃, 색상, 타이포, 간격, 애니메이션)
4. **UI 레이어만 교체** — 기능 로직은 건드리지 않음
5. 주석 업데이트

**UI 교체 시 유지 사항**:
- 타입 정의, 상태 관리 변경 금지
- API 호출 로직 유지
- 기존 이벤트 핸들러 유지

---

## 반응형 설계

각 톤의 공통 반응형 원칙:

- 모바일 (~640px): 단일 컬럼
- 태블릿 (640~1024px): 지도 전체폭, 메트릭 2열
- 데스크톱 (1024px+): 지도 좌측, 메트릭 우측 2열

`PhoneFrame` 컴포넌트가 모바일 우선 레이아웃을 제공합니다.

---

## 핸드오프 원본 활용

`frontend/design/handoff/`의 파일들:
- `Again Spring Mockup.html` — 브라우저에서 전체 디자인 캔버스 확인 가능
- `styles.css` — 3-Tone 공유 토큰 reference
- `tone-L/P/Q-screens.jsx` — 화면별 React JSX 구조 참고

**주의**: 핸드오프 파일은 배포 미포함. 참조만 하고, 최종 구현은 `tailwind.config.ts` + 각 페이지 컴포넌트에서 수행.

