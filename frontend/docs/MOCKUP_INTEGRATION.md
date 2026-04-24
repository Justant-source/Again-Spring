# 목업 디자인 통합 가이드

**대상**: Claude Code
**목적**: Claude Design으로 제작된 목업을 프로젝트에 통합하는 표준 절차

---

## 🎨 전제 조건

본 프로젝트 "다시봄"의 UI 디자인은 **Claude Design**을 통해 별도로 제작됩니다. 
개발과 디자인은 **비동기적**으로 진행되며, 다음과 같은 상황이 발생할 수 있습니다:

1. 목업 없이 개발 먼저 진행
2. 목업 완성 후 기존 코드 재작업
3. 일부 화면만 목업 있고 나머지는 미완성
4. 목업 수정 후 재적용 요청

Claude Code는 이 모든 상황에 **일관된 절차**로 대응해야 합니다.

---

## 📁 목업 파일 위치

프로젝트 경로: `/home/justant/Data/Again-Spring/design/mockups/`

각 화면마다 별도 폴더로 구성되며, 폴더명 규칙은 **번호-화면명(kebab-case)**:

```
design/mockups/
├── 00-landing/              # 랜딩 페이지
├── 01-onboarding/           # 10문항 온보딩 테스트
├── 02-session-start/        # 세션 시작 (관계 유형 선택)
├── 03-category-select/      # 대/중/소분류 선택
├── 04-describe/             # 상황 서술
├── 05-invite/               # 초대 링크 생성
├── 06-wait/                 # B 참여 대기
├── 07-join-b/               # B 참여 랜딩
├── 08-mediation/            # 6턴 중재 (⭐ 최우선)
├── 09-result/               # 결과 리포트 (⭐ 최우선)
├── 10-solo-mode/            # Solo 모드
└── 11-history/              # 세션 이력
```

---

## 📂 각 목업 폴더의 표준 구성

```
09-result/
├── SPEC.md                  # 디자인 스펙 및 의도 (중요!)
├── desktop.png              # 데스크톱 뷰 (권장 1440px)
├── mobile.png               # 모바일 뷰 (권장 375px)
├── mockup.html              # HTML 목업 (있으면 최우선 참조)
├── assets/                  # 이미지, SVG, 아이콘 등
│   ├── icon-map.svg
│   └── bg-pattern.png
└── notes.md                 # 추가 메모
```

### 우선순위 (Claude Code 판독 순서)

1. **`mockup.html`** — 있으면 가장 정확한 구현 기준
2. **`SPEC.md`** — 디자인 의도와 인터랙션 명세
3. **`desktop.png`, `mobile.png`** — 시각적 레이아웃 레퍼런스
4. **`notes.md`** — 보조 정보

---

## 🔍 Claude Code의 목업 체크 절차

### 절차 1: Phase 시작 전 확인

각 Phase 작업을 시작하기 **전에 반드시** 해당하는 목업 폴더를 확인합니다.

```bash
# 예: Phase 8 (결과 리포트) 시작 전
ls -la design/mockups/09-result/
cat design/mockups/09-result/SPEC.md 2>/dev/null
```

### 절차 2: 파일 존재 여부별 분기

```
┌─────────────────────────────────────────┐
│ design/mockups/XX-XXX/ 파일 확인        │
└─────────────────────────────────────────┘
            │
    ┌───────┴────────┐
    │                │
  파일 O            파일 X
    │                │
    ↓                ↓
  목업 기반 구현    기본 디자인 구현
  + ✅ 주석         + ⚠️ PENDING 주석
```

### 절차 3: 목업 분석

파일이 있는 경우 다음 순서로 분석:

1. **`mockup.html` 존재 여부** → 있으면 브라우저에서 확인 가능하므로 구조/CSS를 그대로 참고
2. **`SPEC.md` 내용 읽기** → 인터랙션, 애니메이션, 특이사항 파악
3. **이미지 파일 확인** → 레이아웃, 간격, 폰트 크기 추정
4. **`assets/` 폴더 에셋** → `public/` 또는 적절한 위치로 복사

### 절차 4: 구현

목업 기반으로 구현 시 다음 원칙 준수:

- **레이아웃**: 목업의 그리드/플렉스 구조를 최대한 재현
- **색상**: 목업의 정확한 HEX 값 사용 (추정 금지, 보이지 않으면 SPEC.md 확인 또는 질문)
- **타이포그래피**: 폰트 크기, 굵기, 행간 일치
- **간격**: 패딩/마진 목업 기준
- **애니메이션**: SPEC.md에 명시된 인터랙션 구현
- **에셋**: 목업 폴더의 이미지/SVG 그대로 사용

### 절차 5: 주석 표시

구현한 파일 상단에 다음 주석 중 하나 반드시 추가:

**목업 반영 완료**:
```tsx
// ✅ MOCKUP APPLIED
// Source: design/mockups/09-result/
// Applied: 2026-XX-XX
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

## 🔄 목업 업데이트 시 재작업 절차

사용자가 목업을 추가하거나 수정한 후 Claude Code에게 재작업을 명령할 때:

### 사용자 명령 패턴

```
"design/mockups/08-mediation/ 에 목업을 추가했어. 
해당 화면을 목업에 맞춰 재작업해줘."
```

```
"design/mockups/09-result/ 목업을 업데이트했어. 반영해줘."
```

```
"design/mockups/ 전체 확인하고 MOCKUP PENDING 표시된 것들 다 재작업해줘."
```

### Claude Code 재작업 순서

1. **목업 폴더 전체 재스캔**
   ```bash
   ls -la design/mockups/XX-XXX/
   ```

2. **기존 구현 코드 식별**
   - 해당 화면에 매핑되는 파일들 찾기
   - 상단 주석 확인 (`MOCKUP PENDING` / `MOCKUP APPLIED` / `MOCKUP PARTIAL`)

3. **Diff 분석**
   - 기존 코드와 목업의 차이점 파악
   - 레이아웃 구조 변경 필요 여부
   - 색상/타이포/간격 변경 필요 여부
   - 새 에셋 추가 필요 여부

4. **재작업 범위 결정**
   - UI 레이어만 교체 (권장)
   - 기능 로직은 그대로 유지
   - 타입, 상태 관리, API 호출 부분 건드리지 않음

5. **재구현**
   - 컴포넌트 구조 조정
   - 스타일 업데이트
   - 에셋 `public/`에 복사
   - 애니메이션 반영

6. **주석 업데이트**
   ```tsx
   // ✅ MOCKUP APPLIED
   // Source: design/mockups/09-result/
   // Initial: 2026-XX-XX
   // Updated: 2026-YY-YY (레이아웃 전면 개편)
   ```

7. **사용자에게 보고**
   ```
   design/mockups/09-result/ 목업 반영 완료:
   
   변경사항:
   - components/result/NeedsMap.tsx: 좌표계 디자인 변경
   - components/result/ContributionRatio.tsx: 바 차트 → 도넛 차트로 교체
   - public/images/map-bg.svg 추가
   
   미반영 사항 (필요 시 알려주세요):
   - 공유 이미지 템플릿은 SPEC.md에 명시되지 않아 기존 유지
   ```

---

## 📝 SPEC.md 템플릿 (사용자 작성용)

목업을 추가할 때 Claude Design이나 사용자가 `SPEC.md`에 다음 정보를 담으면 구현 품질이 크게 좋아집니다:

```markdown
# [화면명] 디자인 스펙

## 화면 ID
09-result

## 라우트
/session/result/[id]

## 디자인 의도
(이 화면에서 사용자가 느껴야 할 감정과 행동 목표를 간단히 기술)
예: "갈등 후 불편했던 감정이 '이해'로 전환되는 순간. 과열된 감정을 식히고 
    상대를 다시 바라볼 수 있도록 차분하고 따뜻한 톤을 유지."

## 주요 컴포넌트

### 1. 헤더
- 상단 중앙 정렬, 세션 제목
- 배경: 그라디언트 (봄 파스텔)

### 2. 욕구 차이 지도
- 정사각형 차트 (500x500)
- 2개 축 (X: 연결성-자율성, Y: 안정-변화)
- A, B 위치는 원형 마커 (반경 24px)
- 두 점 사이 점선 연결

### 3. 관계 온도 게이지
- 가로 바 형태
- 색상: 35°C(파랑) → 36.5°C(흰색) → 38°C(주황)

## 인터랙션

- 페이지 로드 시: 각 섹션이 아래에서 위로 순차 페이드인 (stagger 200ms)
- 욕구 차이 지도: A/B 마커가 중앙에서 제 위치로 이동하는 애니메이션 (1.2초)
- 공유 버튼 클릭: 하단에서 슬라이드업 모달

## 색상 팔레트

| 요소 | 색상 |
|---|---|
| 배경 | #FAF9F6 |
| 헤더 그라디언트 | #E8F5E9 → #F3E5F5 |
| A측 마커 | #CECBF6 |
| B측 마커 | #F4C0D1 |
| 중립 텍스트 | #374151 |
| 경고 텍스트 | #EF4444 |

## 타이포그래피

- 제목: Pretendard 24px / 700
- 섹션 제목: Pretendard 18px / 600
- 본문: Pretendard 15px / 400 / line-height 1.6

## 간격

- 섹션 간: 32px
- 컴포넌트 내부 패딩: 20px
- 카드 border-radius: 16px

## 반응형

- 모바일 (~640px): 단일 컬럼
- 태블릿 (640~1024px): 지도는 전체폭, 메트릭 2열
- 데스크톱 (1024px+): 지도 좌측, 메트릭 우측 2열

## 주의사항

- 갈등 원문 내용 **절대 노출 금지**
- 공유 이미지에는 추상화된 지도 + 온도만
- 화해 기여도는 "과실비율" 대신 "먼저 다가가면 좋은 쪽" 표현

## 참고 에셋

- assets/icon-temperature.svg
- assets/bg-gradient.png
```

---

## ⚙️ 디자인 토큰 관리

`design/tokens/design-tokens.json`에 색상, 타이포, 간격 등의 디자인 토큰 관리:

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

**Claude Code는 이 JSON을 Tailwind config에 매핑하여 `tailwind.config.ts`에 반영**할 수 있습니다.

---

## 🚦 목업 상태 대시보드

Claude Code는 작업 시작 전 또는 사용자 요청 시 다음 형식의 상태를 보고할 수 있습니다:

```
다시봄 목업 반영 현황:

✅ APPLIED (5/12)
  - 00-landing
  - 01-onboarding
  - 08-mediation
  - 09-result
  - 11-history

🟡 PARTIAL (2/12)
  - 02-session-start (레이아웃만 반영, 색상 미확정)
  - 05-invite (본문 반영, 공유 모달 미반영)

⚠️ PENDING (5/12)
  - 03-category-select
  - 04-describe
  - 06-wait
  - 07-join-b
  - 10-solo-mode
```

사용자 명령:
```
"다시봄 목업 반영 현황 보여줘"
```

---

## 🎯 품질 체크리스트

목업 반영 완료 기준:

- [ ] 레이아웃이 목업과 일치 (±10% 허용)
- [ ] 색상이 목업과 일치 (HEX 기준)
- [ ] 폰트 크기·굵기 일치
- [ ] 간격(마진·패딩) 목업 기준
- [ ] 명시된 애니메이션 구현
- [ ] 반응형 동작 (모바일/태블릿/데스크톱)
- [ ] 에셋 파일 `public/`에 적절히 배치
- [ ] 주석에 출처 명시

---

## ❓ 목업 불명확 시 사용자에게 질문

목업만으로 판단 어려운 경우 Claude Code는 다음 형식으로 질문합니다:

```
⚠️ design/mockups/09-result/ 목업 관련 질문이 있습니다.

1. SPEC.md에 명시되지 않은 '공유 모달'의 배경 처리:
   (A) 반투명 오버레이 (rgba(0,0,0,0.5))
   (B) 전체 불투명 흰 배경
   (C) 블러 효과

2. 욕구 차이 지도의 빈 상태 (Solo 모드 시):
   (A) B 위치에 점선 원 + "아직 비어있어요" 텍스트
   (B) B 위치 생략 + A 중심 강조
   (C) 전체 흐림 처리 + CTA 오버레이

어떻게 진행할까요?
```

---

## 📌 요약

1. Claude Code는 각 Phase 시작 전 `design/mockups/XX-XXX/` 확인
2. 파일 있으면 목업 기반 구현, 없으면 기본 디자인 + PENDING 주석
3. 사용자가 "목업 추가했어" 명령 시 즉시 재작업
4. 모든 파일 상단에 상태 주석 (`APPLIED` / `PENDING` / `PARTIAL`)
5. 불명확 시 질문으로 대응, 추측 금지
6. 재작업은 UI 레이어만 교체, 기능 로직 건드리지 않기

**끝.**
