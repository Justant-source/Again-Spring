# 다시봄 · Again Spring — 프론트엔드

> **"다시 봄. 다시 바라봄."**
>
> 싸운 두 사람 사이에서 AI 중재자가 양쪽 이야기를 중립적으로 정리해, 관계 회복을 돕는 웹앱의 프론트엔드.

---

## 빠른 시작

```bash
npm install
npm run dev          # localhost:3000 (MSW 자동 활성)
npm run lint:words   # 금지어 검사 (배포 전 필수)
npm run build        # 프로덕션 빌드
```

---

## 🎨 UX 정책 — 4원칙군 (모든 FE 작업의 기준)

다시봄의 사용자는 갈등 한복판에 있습니다. 일반 SaaS 디자인 원칙으로는 부족하며, **악용 시나리오를 명시적으로 차단하는 설계**가 필요합니다.

| 순위 | 원칙군 | 권위 문헌 | 언제 확인 |
|---|---|---|---|
| **1순위** | AI 중재자 신뢰성 | Microsoft HAX 18 가이드라인 | 모든 컴포넌트 수정 시 |
| **1순위** | 위기 사용자 보호 | Designing for Safety (PenzeyMoog) | 모든 컴포넌트 수정 시 |
| **2순위** | 인지 부하 최소화 | GOV.UK Service Manual | 화면 신규 추가 시 |
| **3순위** | 카피·인터랙션 | NN/g wizard, SAMHSA 6원칙 | 카피·인터랙션 수정 시 |
| **4순위** | 결과 해석 안전성 | WCAG 2.2 cognitive, Do No Harm Guide | 결과 리포트 수정 시 |

**권위본**: [`docs/ux/principles.md`](./docs/ux/principles.md)  
**컴포넌트 체크리스트**: [`docs/ux/hax-checklist.md`](./docs/ux/hax-checklist.md)

### 절대 불변 규칙

- **위기 모달은 ESC·바깥 클릭으로 닫히지 않는다** — 명시적 버튼으로만 닫힘
- **위기 감지는 FE+BE 이중 구현** — 어느 한쪽 제거 금지
- **결과 리포트에 처방(prescription) 금지** — "이니 더 노력하셔야 합니다" 패턴 금지
- **`ContributionRatio` 법적 안내 박스는 항상 표시** — 조건부로 만들지 않음

### 신규 화면·입력·공유 기능 PR 시 — Safety Check 4문

PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)에 포함:

1. **Abuser**: 가해자가 이 기능을 무기로 쓸 수 있는가?
2. **Survivor**: 학대 상황의 사용자에게 새로운 위험이 생기는가?
3. **Roadblock**: 악용을 막거나 마찰을 어디에 두는가?
4. **Exit**: 이 화면에서 1탭으로 빠져나갈 수 있는가?

---

## 핵심 정책

- **금지어 차단**: `npm run lint:words` — Level 1(법률), Level 2(진단명), Level 3(승패) 자동 스캔
- **위기 감지**: 클라이언트 사이드 `checkKeywords()` + 서버 사이드 `KeywordGuard` 이중화
- **디자인 톤**: Tone L (입력/채팅), Tone P (결과), Tone Q (고급) — 절대 섞지 않기
- **데이터 격리**: Duo 모드에서 상대방 메시지 원문 절대 노출 금지 (`BlurredBubble`)

---

## 배포 전 체크리스트

- [ ] `npm run lint:words` 통과
- [ ] 수정한 컴포넌트의 `docs/ux/hax-checklist.md` 해당 섹션 확인
- [ ] 위기 모달 dismiss 마찰 여전히 유지되는지 확인
- [ ] `npm run build` 성공
- [ ] 전체 플로우 수동 테스트 (온보딩 → 세션 → 채팅 → 결과)
- [ ] 모바일 반응형 확인

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/ux/principles.md`](./docs/ux/principles.md) | **FE UX 권위본** — 4원칙군, 우선순위, 로드맵 |
| [`docs/ux/hax-checklist.md`](./docs/ux/hax-checklist.md) | 컴포넌트별 PR 체크리스트 (HAX 18) |
| [`docs/ui/design-handoff.md`](./docs/ui/design-handoff.md) | 3-Tone 디자인 시스템, 목업 |
| [`docs/architecture.md`](./docs/architecture.md) | 기술 스택, 상태 관리, API 클라이언트 |
| [`docs/structure.md`](./docs/structure.md) | 폴더 구조 |
| [`docs/policies/crisis-modal.md`](./docs/policies/crisis-modal.md) | 위기 모달 정책 (위기 모달 수정 시 필독) |
| [`docs/policies/forbidden-words-lint.md`](./docs/policies/forbidden-words-lint.md) | 금지어 린트 구현 |
