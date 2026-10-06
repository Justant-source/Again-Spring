# SVG 아이콘 카탈로그

> **정책 (V13.10 + V14, 2026-05-16 영구 적용)**:
> 다시봄 앱 전체에서 **emoji 사용 금지** (장식·기능성 글리프 전부 포함).
> 모든 시각 아이콘은 본 카탈로그의 SVG 컴포넌트 또는 텍스트로 처리.

자동 검증: `npm run lint:emoji` — app/components/lib 전체 스캔, 위반 시 exit 1.

---

## 디자인 가이드

신규 아이콘 추가 시 준수:

- **단색 또는 2색**: 잉크 차콜 + 보조 색
- **stroke**: 1.5~2px, `strokeLinecap="round"`, `strokeLinejoin="round"`
- **색**: `currentColor` (부모 색 상속)
- **기본 viewBox**: `0 0 24 24` (확장 가능)
- **차분한 톤**: 강한 SOS·블링블링 효과 금지

---

## 아이콘 목록

`frontend/components/icons/` 의 React SVG(`DasibomLogo`, `Phone`, `SafeHaven`, `CrisisResources`, `IconCheck`, `StatusDot`, `Conversation`)와 `components/shared/Motif.tsx` 는 삭제됐다. 화면 아이콘은 `lucide-react` 를 쓴다. 새싹 캐릭터 자산은 `docs/frontend/assets/sprout-character-system/` 이다.

```tsx
import { CheckCircle2 } from 'lucide-react';
```

---

## 신규 아이콘 추가 절차

1. `frontend/components/icons/`에 신규 컴포넌트 작성 (디자인 가이드 준수)
2. 본 카탈로그(`icons.md`) 테이블에 등재
3. `npm run lint:emoji` 통과 확인 (교체 대상 emoji 0)
4. PR 리뷰 시 시각 검증

---

## emoji 교체 이력

| emoji | 교체 방법 | V14 완료 |
|---|---|---|
| ✅ (MOCKUP 주석) | 주석 라인 제거 | ✓ |
| ⚠️ (MOCKUP 주석) | 주석 라인 제거 | ✓ |
| ✓ (UI 텍스트) | 텍스트 단순화 | ✓ |
| 💚🌱🟡🟠🔴 (거리 표시) | 컴포넌트 삭제. 거리 점은 현재 UI에 없음 | ✓ |
| 🌊🏔🔥🌿🌙⭐ (스타일 data) | `Motif` 삭제. emoji 필드 없음 | ✓ |

---

*변경 이력: V14 (2026-05-16) — Phase 4 emoji 전체 제거 + SVG 정책 통합.*
