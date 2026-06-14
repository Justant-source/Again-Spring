# 디자인 (`docs/design/`)

> 다시봄 디자인 시스템 컨텍스트 — 2026-06-03 광장형 SSOT 기준

---

## 무엇을 어디서 보는가

| 궁금한 것 | 파일 |
|---|---|
| 3-Tone 시스템 (L/P/Q) 의도·금지사항 | [system.md](./system.md) |
| 화면↔컴포넌트 매핑, 각 컴포넌트 UX 체크리스트 링크 | [components.md](./components.md) |
| SVG 아이콘 카탈로그, emoji 금지 정책 | [icons.md](./icons.md) |
| 시각 정본 HTML (28화면) | [`frontend/design/다시봄 광장형 UX (standalone).html`](../../design/다시봄%20광장형%20UX%20(standalone).html) |
| **토큰 구현** | `frontend/tailwind.config.ts` + `frontend/app/globals.css` |
| 스펙 문서 (화면별 UX 결정) | [specs/](./specs/) |

---

## 왜 그렇게 작동해야 하는가

디자인이 *어떻게 보이는가*의 이유는 UX 문서에 있습니다.

- [docs/ux/principles.md](../ux/principles.md) — 4원칙군 (AI 신뢰성 / 위기 보호 / 인지 부하 / 결과 안전성)
- [docs/ux/hax-checklist.md](../ux/hax-checklist.md) — 컴포넌트별 PR 체크리스트
- [docs/ux/collaboration.md](../ux/collaboration.md) — Claude Design + Claude Code 협업 흐름

---

## Claude Design 호출 시 제공할 컨텍스트

새 화면/컴포넌트를 Claude Design에 요청할 때:

1. **system.md** — 톤 시스템 + 절대 금지사항
2. **시각 정본 HTML** — `frontend/design/다시봄 광장형 UX (standalone).html` (28화면, 톤 레퍼런스)
3. **specs/{화면}.md** — 이번 화면의 UX 의도·구조 (Claude Code가 사전 작성)
4. **icons.md** — 사용 가능한 SVG 아이콘

협업 흐름 상세: [docs/ux/collaboration.md](../ux/collaboration.md)

---

## 정책 (절대 금지)

- AI emoji 사용 금지 (V13.10, 영구): [icons.md](./icons.md) 참조, `npm run lint:emoji`로 검증
- 금지어: [docs/policies/forbidden-words-lint.md](../policies/forbidden-words-lint.md)
- 토큰 하드코딩 금지: `tailwind.config.ts` 키 사용 또는 `var(--L-*)` 참조
