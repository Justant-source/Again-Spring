# 다시봄 · 목업 디자인 관리

본 폴더는 Claude Design으로 제작된 목업과 코드 구현을 연결하는 허브입니다.

## 구조

```
design/
├── handoff/      # Claude Design 핸드오프 원본 (HTML/JSX/CSS)
├── mockups/      # 화면별 최종 목업 (XX-XXX/SPEC.md + 이미지 등)
├── tokens/       # 디자인 토큰 JSON (Tone L / P / Q)
└── README.md     # 이 파일
```

## 원본 핸드오프 파일 (`handoff/`)

Claude Design에서 내려받은 프로토타입 원본입니다. 배포에 포함되지 않으며, 스타일/레이아웃 레퍼런스 용도로만 참조합니다.

- `Again Spring Mockup.html` — 전체 디자인 캔버스 엔트리
- `styles.css` — 3-Tone (L/P/Q) 공유 토큰
- `primitives.jsx`, `icons.jsx`, `mediation-screens.jsx`, `tone-L-screens.jsx`, `tone-P-screens.jsx` — 화면별 React 프로토타입
- `design-canvas.jsx`, `tweaks-panel.jsx` — 캔버스 툴 (프로덕션 미포함)

## 화면별 폴더 (`mockups/XX-XXX/`)

각 Phase 구현 전 해당 폴더를 확인합니다. 파일이 있으면 목업 기반 구현, 없으면 기본 디자인 + `⚠️ MOCKUP PENDING` 주석.

자세한 통합 절차는 `docs/MOCKUP_INTEGRATION.md` 참조.

## 목업 반영 현황

| ID | 화면 | 출처 | 상태 |
|---|---|---|---|
| 00-landing | 랜딩 | handoff `LandingScreen` (tone-L) | ✅ APPLIED |
| 01-onboarding | 10문항 온보딩 | handoff `OnboardingSlider/Emoji/Sentence` | ✅ APPLIED |
| 02-session-start | 관계 유형 선택 | handoff `TreeBig` | ✅ APPLIED |
| 03-category-select | 대/중/소분류 | handoff `TreeMid/TreeSmall` | ✅ APPLIED |
| 04-describe | 상황 서술 | handoff `InputDescribe` | ✅ APPLIED |
| 05-invite | 초대 메시지 | handoff `InvitePick` | ✅ APPLIED |
| 06-wait | B 대기 · Solo 전환 | handoff `WaitingB` | ✅ APPLIED |
| 07-join-b | B 참여 + 요약 | handoff `BSummary` | ✅ APPLIED |
| 08-mediation | 6턴 중재 | handoff `MediationLetter` | ✅ APPLIED |
| 09-result | 결과 리포트 | handoff `SignatureMap`, `ReportCards`, `ReportStory` | ✅ APPLIED |
| 10-solo-mode | Solo 모드 결과 | handoff `SoloResult` | ✅ APPLIED |
| 11-history | 세션 이력 | — | ⚠️ PENDING |
