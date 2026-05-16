# 다시봄 디자인 시스템 — 3-Tone Hybrid

> **이 문서**: *어떻게 보이는가* (토큰·톤·금지사항·시그니처)
> **왜 그렇게 작동해야 하는가**: [docs/ux/principles.md](../ux/principles.md) (4원칙군) 참조
> **토큰 실제 값**: `frontend/tailwind.config.ts` > `theme.extend.colors` (단일 SSOT)

---

## 3-Tone 시스템

다시봄의 모든 화면은 목적에 따라 세 가지 톤 중 하나를 씁니다. **절대 섞지 마세요.**

### Tone L — 편지지 (Letter)

**용도**: 온보딩, 입력 플로우, 카톡식 채팅 세션, 이력/프로필

**토큰** (`tailwind.config.ts > colors.tone-l`):

| 역할 | 토큰 | CSS 변수 |
|---|---|---|
| 배경 | `tone-l.bg` | `var(--L-bg)` |
| 카드 | `tone-l.card` | `var(--L-card)` |
| 텍스트 | `tone-l.ink` | `var(--L-ink)` |
| 보조 텍스트 | `tone-l.sub` | `var(--L-sub)` |
| 보더 | `tone-l.border` | `var(--L-border)` |
| 포인트 (드물게) | `tone-l.point` | `var(--L-point)` |

**특징**:
- 라운드: `rounded-letter` (3px) — 거의 각짐
- 제목: `font-serif` (Noto Serif KR / Nanum Myeongjo)
- 본문: `font-sans` (Pretendard)
- 여백: 40px padding, 넉넉하게
- 느낌: "잘 정돈된 편지지"

**관련 UX 원칙**: [principles.md §3 — 인지 부하 최소화](../ux/principles.md), [principles.md §1 — AI 중재자 신뢰성](../ux/principles.md)

---

### Tone P — 파스텔 (Pastel)

**용도**: 결과 리포트, 카톡 공유 이미지, 관계 온도, 욕구 차이 지도

**토큰** (`tailwind.config.ts > colors.tone-p`):

| 역할 | 토큰 | CSS 변수 | 비고 |
|---|---|---|---|
| 배경 | `tone-p.bg` | `var(--P-bg)` | |
| 카드 | `tone-p.card` | `var(--P-card)` | |
| A 포인트 | `tone-p.a` | `var(--P-a)` | RelationshipColorSync 런타임 덮어씀 |
| B 포인트 | `tone-p.b` | `var(--P-b)` | RelationshipColorSync 런타임 덮어씀 |
| 텍스트 | `tone-p.ink` | `var(--P-ink)` | |
| 보조 | `tone-p.sub` | `var(--P-sub)` | |
| 보더 | `tone-p.border` | `var(--P-border)` | |

**특징**:
- 라운드: `rounded-pastel` (14px) / `rounded-card-p` (18px) — 크게
- 타이포: Pretendard 전용, 제목만 font-weight 500
- 느낌: "따뜻한 MBTI 결과지"

**관련 UX 원칙**: [principles.md §4 — 결과 해석 안전성](../ux/principles.md)

---

### Tone Q — 조용한 고급감 (Quiet)

**용도**: PDF 리포트, Premium 결제 화면, 상담사 연결 화면 (향후)

**토큰** (`tailwind.config.ts > colors.tone-q`):

| 역할 | 토큰 | CSS 변수 |
|---|---|---|
| 배경 | `tone-q.bg` | `var(--Q-bg)` |
| 카드 | `tone-q.card` | `var(--Q-card)` |
| 텍스트 | `tone-q.ink` | `var(--Q-ink)` |
| 보조 | `tone-q.sub` | `var(--Q-sub)` |
| 보더 | `tone-q.border` | `var(--Q-border)` |
| 포인트 | `tone-q.point` | `var(--Q-point)` |

**특징**:
- 라운드: 8~12px
- 느낌: "Linear / Arc Browser"

---

## 절대 금지 사항

- 하트, 손잡는 일러스트, 무지개 그라데이션
- Duolingo식 마스코트 캐릭터
- "과실비율", "판결", "가해자/피해자" 등 판결/병리 용어 ([forbidden-words-lint.md](../policies/forbidden-words-lint.md) 참조)
- 다크모드 기본값
- 3D 글래스모피즘, 네온, 그라데이션 효과
- `font-weight: 700` (bold) 혼용 → 500만 사용
- Title Case / ALL CAPS 한국어
- **AI emoji** (V13.10, 2026-05-16 영구 적용) — 🌱🌊🔥✅⚠ 포함 모든 emoji 금지
  - 모든 시각 아이콘은 SVG 컴포넌트 사용
  - 카탈로그: [icons.md](./icons.md)
  - 자동 검증: `npm run lint:emoji`

---

## 카피 톤

- 존댓말 기본. 따뜻하고 차분하게.
- "~하셨을 수 있어요", "~처럼 느끼셨을 것 같아요" 가정형
- 양쪽 호명 순서 균형 (A 먼저 말했으면 다음엔 B 먼저)
- 헤드라인은 짧고 시적: "지금, 누구와의 이야기인가요?", "당신의 마음을 들려주세요."

**관련 UX 원칙**: [principles.md §3 — 카피·인터랙션](../ux/principles.md)

---

## 시그니처 요소

이 요소들은 다시봄 전체에서 일관되게 유지합니다.

### 욕구 차이 지도
- 2D 차트, A는 피치 원, B는 세이지 원
- 축 레이블은 세리프 이탤릭

### 관계 온도
- `36.2°C` 형식 (소수점 1자리 고정)

### 화해 기여도
- `55 : 45` 또는 `55 · 45` 형식 (5 단위 반올림)
- 법적 안내 박스 항상 표시 ("과실비율과 무관합니다")

### 커뮤니케이션 스타일
- 자연물 은유 — `Motif.tsx` SVG variant 사용 (emoji 금지)
- 파도형 · 산형 · 불꽃형 · 이파리형 · 달빛형 · 별빛형

---

## 공유 이미지 규격

- 비율: 9:16 (인스타 스토리 / 카톡 공유 세로형)
- 해상도: 1080×1920
- 톤: **Tone P 강제**
- 하단 워터마크: `다시봄 · again-spring.com`
- 금지: 갈등 원문 절대 노출 금지. 축·수치·스타일명만.

---

## 프로그레스 표시

입력 플로우 단계는 상단에 작은 대시로 표시. 숫자 아닌 시각 위주:
```
■ ■ — —   (2/4 진행 중)
```

---

## 토큰 변경 절차

값을 변경할 때 **한 곳만 수정**:

1. `frontend/tailwind.config.ts` > `theme.extend.colors` 수정
2. `app/globals.css`의 `theme()` 참조가 자동 반영
3. 시각 회귀 검증 (핵심 5개 화면)
4. [docs/ux/collaboration.md](../ux/collaboration.md) §시나리오 C 흐름 참조

---

*변경 이력: V14 (2026-05-16) — `docs/ui/design-handoff.md` 분리 재구성. SSOT = tailwind.config.ts.*
