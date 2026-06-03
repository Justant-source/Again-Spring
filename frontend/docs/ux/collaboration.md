# Claude Design + Claude Code 협업 흐름

> V14 (2026-05-16) 신규 작성. 이후 모든 신규 화면·수정 시 본 흐름을 따릅니다.
>
> **자매 문서**: [design/README.md](../design/README.md) — Claude Design 호출 시 제공할 컨텍스트
> **UX 원칙**: [principles.md](./principles.md) | **디자인 시스템**: [design/system.md](../design/system.md)

---

## 0. 역할 분담

| 영역 | 담당 | 산출물 |
|---|---|---|
| UX 기획 | Claude Code | `docs/design/specs/{화면}.md` |
| UI 시각화 | Claude Design | `docs/design/visual-reference/{화면}/` 또는 `design/mockups/` |
| 구현 | Claude Code | React 컴포넌트 |
| 검증 | 사용자 | 각 단계 승인 |

**핵심**: 사용자는 각 단계에서 *예/아니오* 또는 *어느 후보* 수준의 결정만.
디자인·기획·구현 자체는 Claude에 위임.

---

## 1. 시나리오 A — 새 화면 추가

```
[사용자 요구 1~2문장]
        ↓
[Claude Code: UX spec 작성]
        ↓ docs/design/specs/{화면}.md
[사용자 승인 ①]
        ↓
[Claude Design: UI 시각화]
        ↓ docs/design/visual-reference/{화면}/ (PNG)
[사용자 승인 ②]
        ↓
[Claude Code: 구현]
        ↓ React 컴포넌트
[사용자 검증 ③]
        ↓
[PR 생성 → Merge]
```

### Step 1 — 사용자가 요구 정의

"사연 상세 페이지에서 배심원 카드를 탭별로 펼쳐보는 UI" 같은 1~2문장.

### Step 2 — Claude Code: UX spec 작성

**입력**:
- 사용자 요구
- `docs/ux/principles.md` (4원칙군)
- `docs/ux/hax-checklist.md` (관련 컴포넌트 체크)
- `docs/design/system.md` (톤 결정)

**산출**: `docs/design/specs/{화면명}.md` (템플릿: `docs/design/specs/_TEMPLATE.md`)

### Step 3 — 사용자 승인 ①

"이런 UX 흐름이 맞는가?" — 예/아니오 또는 방향 조정.

### Step 4 — Claude Design: UI 시각화

**입력**:
- Step 2 spec
- `docs/design/system.md` (톤 시스템)
- `docs/design/visual-reference/` (현재 다시봄 톤 캡처)
- `docs/design/icons.md` (SVG 카탈로그)

**산출**: PNG 캡처 → `docs/design/visual-reference/{화면명}/`

### Step 5 — 사용자 승인 ②

"이렇게 보이는 게 맞는가?" — 예/아니오 또는 시각 조정.

### Step 6 — Claude Code: 구현

**입력**:
- Step 2 spec
- Step 4 캡처
- `tailwind.config.ts` (토큰)
- `docs/design/icons.md` (SVG)

**산출**: React 컴포넌트 + 단위 테스트

### Step 7 — 사용자 검증 ③

dev에서 시각 확인 + UX 원칙 통과 확인.

---

## 2. 시나리오 B — 기존 화면 수정

위 흐름과 동일하되 Step 2에서:
- 현재 코드 + 캡처 분석 추가
- 변경 사유·회귀 위험 명시
- UX 체크리스트 해당 항목 확인

---

## 3. 시나리오 C — 디자인 토큰 변경

### 빠른 흐름

```
[사용자 요구] "Tone P 카드 배경이 너무 노란 느낌"
        ↓
[Claude Code: 현재 값 분석 + 후보 3개 제안]
        ↓ (tailwind.config.ts 현재 값 + 의도 비교)
[사용자 결정]
        ↓
[Claude Code: tailwind.config.ts 한 곳만 수정]
        ↓ + visual-reference/ 캡처 업데이트 (필요시)
```

**핵심**: 토큰 변경은 `tailwind.config.ts` **한 곳만**. `globals.css`는 `theme()` 참조이므로 자동 반영.

---

## 4. 사용자 부담 최소화

### 결정 위임 가능한 영역 (Claude가 사전 결정)

- 시각 디테일 (색·간격·라운드 세부 조정)
- 컴포넌트 단위 결정 (버튼 위치, 모달 흐름 세부)
- 카피 톤 (한국어 가정형, 양쪽 균형)
- 기술 구현 방법 (React 패턴, Tailwind 클래스)

### 위임 불가 영역 (사용자 결정 필수)

- 다시봄의 핵심 메타포 ("공감이지 판결이 아니다" — 배심원은 공감을 표현하지 유죄를 선고하지 않음)
- 갈등 도메인 톤 균형 (얼마나 따뜻하게 vs 차분하게)
- 위기 사용자 책임 범위 (법적 판단이 필요한 영역)
- prod 배포 여부

---

## 5. 스펙 문서 (화면 추가 시 필수)

**위치**: `docs/design/specs/{화면명}.md`
**템플릿**: [`docs/design/specs/_TEMPLATE.md`](../design/specs/_TEMPLATE.md)

스펙 문서는 Claude Code가 Step 2에서 작성. 구현 완료 후 `status: Implemented`로 갱신.

---

## 6. V12·V13 회고

V14 협업 흐름의 첫 적용 사례:
- **V12 Solo 리포트 재설계** = 시나리오 B (기존 화면 수정)
- **V13 30초 온보딩 모달** = 시나리오 A (새 화면 추가)

V15+ 작업부터 본 흐름을 명시적으로 따릅니다.

---

*변경 이력: V14 (2026-05-16) 신규 작성.*
