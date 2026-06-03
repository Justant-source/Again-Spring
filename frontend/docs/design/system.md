# 다시봄 디자인 시스템 — 3-Tone Hybrid

> **이 문서**: *어떻게 보이는가* (토큰·톤·금지사항·시그니처)
> **왜 그렇게 작동해야 하는가**: [docs/ux/principles.md](../ux/principles.md) (4원칙군) 참조
> **토큰 실제 값**: `frontend/tailwind.config.ts` > `theme.extend.colors` (단일 SSOT)

---

## 3-Tone 시스템

다시봄의 모든 화면은 목적에 따라 세 가지 톤 중 하나를 씁니다. **절대 섞지 마세요.**

### Tone L — 편지지 (Letter)

**용도**: 인증 플로우, 사연 작성, 커뮤니티 피드, 프로필

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

**관련 UX 원칙**: [principles.md](../ux/principles.md)

---

### Tone P — 파스텔 (Pastel)

**용도**: 배심원 카드, 공감 비율 공유 이미지, 마케팅 카드

**토큰** (`tailwind.config.ts > colors.tone-p`):

| 역할 | 토큰 | CSS 변수 | 비고 |
|---|---|---|---|
| 배경 | `tone-p.bg` | `var(--P-bg)` | |
| 카드 | `tone-p.card` | `var(--P-card)` | |
| A 포인트 (작성자) | `tone-p.a` | `var(--P-a)` | 초록 계열 (A측 공감) |
| B 포인트 (상대방) | `tone-p.b` | `var(--P-b)` | 붉은 계열 (B측 공감) |
| 텍스트 | `tone-p.ink` | `var(--P-ink)` | |
| 보조 | `tone-p.sub` | `var(--P-sub)` | |
| 보더 | `tone-p.border` | `var(--P-border)` | |

**특징**:
- 라운드: `rounded-pastel` (14px) / `rounded-card-p` (18px) — 크게
- 타이포: Pretendard 전용, 제목만 font-weight 500
- 느낌: "배심원 카드·공감 비율 카드"

**관련 UX 원칙**: [principles.md](../ux/principles.md)

---

### Tone Q — 조용한 고급감 (Quiet)

**용도**: 어드민 대시보드, 설정 화면, 향후 프리미엄 기능

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
- "과실비율", "유죄/무죄", "가해자/피해자", "이겼다/졌다" 등 법적 결론·낙인 표현 ([forbidden-words.md](../../shared/docs/policies/forbidden-words.md) 참조)
  - **참고**: "배심원"은 제품 메타포로 허용. 금지 대상은 배심원이 내리는 법적 *결론* 표현임
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

### 공감 비율 바
- A측(작성자)=초록 / B측(상대방)=붉은 일관 유지
- `X% : Y%` 형식으로 표시 (실제 집계값)
- AI 분석 레이블 (`"AI 배심원 분석"`) 항상 동반

### 배심원 카드
- 페르소나명 + 편향(A측/B측/중립) 표시
- "AI 배심원" 레이블 필수
- 배경색: A편향=연초록, B편향=연붉은, 중립=연회색

### 메타포 일러스트 — `design/specs/metaphor-illustration-system.md`
- 갈등·공감 테마 SVG 일러스트 (emoji 금지)
- 공유 이미지 카드 생성 시 사용

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
