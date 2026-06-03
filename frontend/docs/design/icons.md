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

### 다시봄 정체성 — `components/icons/`

| 컴포넌트 | 역할 | 주요 사용처 |
|---|---|---|
| `DasibomLogo` | 다시봄 새싹 로고 | 헤더, 랜딩 |
| `Conversation` | 대화·정리 아이콘 | 온보딩, 안내 |
| `SafeHaven` | 보호·우산 | 위기 자원 섹션 |
| `Phone` | 전화 수화기 | 핫라인 번호 옆 |
| `CrisisResources` | 위기 자원 진입 | 위기 메뉴 |
| `IconCheck` | 완료·성공 체크 | 성공 메시지 |
| `StatusDot` | 상태 컬러 점 | 배심원 분석 상태 표시 (pending/done/error) |

### 커뮤니케이션 스타일 — `components/shared/Motif.tsx`

6종 자연물 은유 SVG:

| variant | 스타일 | 색 참고 |
|---|---|---|
| `wave` | 파도형 | `#60A5FA` |
| `mountain` | 산형 | `#78716C` |
| `flame` | 불꽃형 | `#F87171` |
| `leaf` | 이파리형 | `#4ADE80` |
| `moon` | 달빛형 | `#A78BFA` |
| `star` | 별빛형 | `#FBBF24` |

---

## 사용 패턴

```tsx
import { IconCheck } from '@/components/icons/IconCheck';
import { StatusDot } from '@/components/icons/StatusDot';
import { DasibomLogo } from '@/components/icons/DasibomLogo';

// 성공 메시지
<p>비밀번호가 변경되었어요 <IconCheck width={14} height={14} /></p>

// 거리 표시
<StatusDot level={distanceInfo.level} size={12} />

// 로고
<DasibomLogo width={28} height={28} />
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
| 💚🌱🟡🟠🔴 (거리 표시) | `StatusDot` SVG | ✓ |
| 🌊🏔🔥🌿🌙⭐ (스타일 data) | `emoji` 필드 제거 (미사용) | ✓ |

---

*변경 이력: V14 (2026-05-16) — Phase 4 emoji 전체 제거 + SVG 정책 통합.*
