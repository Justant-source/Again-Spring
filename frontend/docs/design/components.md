# 컴포넌트 매핑 (`docs/design/components.md`)

> 화면↔React 컴포넌트 대응 + 각 컴포넌트의 UX 체크리스트 링크
> 톤 시스템: [system.md](./system.md) | UX 원칙: [ux/principles.md](../ux/principles.md)

---

## 라우트 페이지

| 화면 | 컴포넌트/경로 | 톤 | HAX 체크리스트 |
|---|---|---|---|
| 랜딩 | `app/page.tsx` | L | [B1](../ux/hax-checklist.md#b1-landing--appagetsx) |
| 온보딩 인트로 | `app/(onboarding)/onboarding/intro/page.tsx` | L | [B2](../ux/hax-checklist.md#b2-onboarding-intro--apponboardingonboardingintropagetsx) |
| 온보딩 질문 | `app/(onboarding)/onboarding/page.tsx` + `LikertQuestion`, `MbtiAxisSlider` | L | [B3](../ux/hax-checklist.md#b3-onboarding-questions--apponboardingonboardingpagetsx--likertquestion-mbtiaxisslider) |
| 인증 (로그인·회원가입·게스트·OAuth) | `app/(auth)/` | L | [B4](../ux/hax-checklist.md#b4-auth--appauthlogin-appauthsignup-appauthguest-appauthcallbackprovider-appauth) |
| 세션 시작 | `app/session/new/page.tsx` | L | [B5](../ux/hax-checklist.md#b5-newsession--appsessionnewpagetsx) |
| 카테고리 선택 | `app/session/category/page.tsx` | L | [B6](../ux/hax-checklist.md#b6-categoryselect--appsessioncategorypagetsx) |
| 채팅 | `app/session/chat/[id]/page.tsx` | L | [B7](../ux/hax-checklist.md#b7-chatpage-route--appsessionchatidpagetsx) |
| B 참여 | `app/session/join/[token]/page.tsx` | L | [B8](../ux/hax-checklist.md#b8-joinb--appsessionjointokenpagetsx) |
| 결과 리포트 | `app/session/result/[id]/page.tsx` + `solo/page.tsx` | P | [B9](../ux/hax-checklist.md#b9-result--appsessionresultidpagetsx--appsessionresultidsolo) |
| 세션 이력 | `app/(dashboard)/history/page.tsx` + `app/session/history/[id]/page.tsx` | L | [B10](../ux/hax-checklist.md#b10-sessionhistory-route--appdashboardhistorypagetsx--appsessionhistoryidpagetsx) |
| 프로필 | `app/(dashboard)/profile/page.tsx` | L | [B11](../ux/hax-checklist.md#b11-profile--appdashboardprofilepagetsx) |

---

## 재사용 컴포넌트

### ChatLayout — `components/chat/ChatLayout.tsx`

**톤**: L | **역할**: 채팅 세션 전체 레이아웃 (Solo/Duo 분기)
**UX 체크리스트**: [B12](../ux/hax-checklist.md#b12-chatlayout--componentschatchatlatLayouttsx)
**관련 UX 원칙**: AI 한계 안내(§1.1), 나가기 1탭(§2.3)

---

### ChatPanel — `components/chat/ChatPanel.tsx`

**톤**: L | **역할**: 한 사용자의 채팅 본체 (메시지 목록 + 입력)
**UX 체크리스트**: [B13](../ux/hax-checklist.md#b13-chatpanel--componentschatchatpaneltsx)
**관련 UX 원칙**: AI 메시지·사용자 메시지 시각 구분 필수(CLAUDE.md 절대불변 §3)

---

### ChatInput — `components/chat/ChatInput.tsx`

**톤**: L | **역할**: 메시지 입력 + FE 위기 키워드 감지 (이중 방어 중 FE측)
**UX 체크리스트**: [B14](../ux/hax-checklist.md#b14-chatinput--componentschatchatinputtsx)
**관련 UX 원칙**: 위기 이중 감지 유지 필수(CLAUDE.md 절대불변 §2)

---

### MessageBubble — `components/chat/MessageBubble.tsx`

**톤**: L | **역할**: 메시지 말풍선 (AI vs 사용자 시각 구분)
**UX 체크리스트**: [B15](../ux/hax-checklist.md#b15-messagebubble--componentschatmessagebubbletsx)

---

### CrisisModal + CrisisResourceModal — `components/chat/` + `components/shared/`

**톤**: L | **역할**: 위기 감지 시 전면 차단 모달
**UX 체크리스트**: [B16](../ux/hax-checklist.md#b16-crisismodal-chat--componentschatcrisismodaltsx--crisisresourcemodal--componentssharedcrisisresourcemodaltsx)
**절대 불변**: ESC·바깥 클릭으로 닫히지 않음 (CLAUDE.md §절대불변 §1)

---

### PartnerPanel + PartnerStatusBar + SwipeContainer — `components/chat/`

**톤**: L | **역할**: Duo 채팅 파트너 뷰·상태·스와이프 UI
**UX 체크리스트**: [B17](../ux/hax-checklist.md#b17-partnerpanel--partnerstatusbar--swipecontainer--componentschat)

---

### InviteModal — `components/chat/InviteModal.tsx`

**톤**: L | **역할**: 파트너 초대 링크 + 클립보드 공유
**UX 체크리스트**: [B18](../ux/hax-checklist.md#b18-invitemodal--componentschatinvitemodaltsx)
**Safety Check**: 초대 링크가 가해자 추적 수단이 되지 않는지 확인 (principles.md §2)

---

### FinalizeSuggestionCard — `components/chat/FinalizeSuggestionCard.tsx`

**톤**: L | **역할**: 5턴 후 정리 유도 카드 (5턴 이후 활성)
**UX 체크리스트**: [B19](../ux/hax-checklist.md#b19-finalizesuggestioncard--componentschatfinalizesuggestioncardtsx)

---

### ChatHeader — `components/chat/ChatHeader.tsx`

**톤**: L | **역할**: 채팅 상단바 (나가기·로고·상태)
**UX 체크리스트**: [B20](../ux/hax-checklist.md#b20-chatheader--componentschatchatheadertsx)
**관련 UX 원칙**: 나가기 1탭 필수(§2.3)

---

### 결과 컴포넌트 그룹 — `components/result/`

**톤**: P | **역할**: Solo/Duo 리포트 전체 (ReportLayout, ContributionRatio, NeedsMap, NVCScript, RepairSuggestions, StyleCombination, SoloResult 등)
**UX 체크리스트**: [B21](../ux/hax-checklist.md#b21-reportlayout--contributionratio--needsmap--metaphorcards--nvcscript--repairsuggestions--stylecombination--soloresult--componentsresult)
**절대 불변**: ContributionRatio 법적 안내 박스 항상 표시 (CLAUDE.md §절대불변 §5)

---

### KeywordGuard + Crisis utilities — `components/shared/` + `lib/utils/`

**역할**: FE 위기 키워드·금지어 감지 유틸리티 (BE KeywordGuard와 이중 방어)
**UX 체크리스트**: [B22](../ux/hax-checklist.md#b22-keywordguard--crisis-utilities--componentssharedkeywordguardtsx--libutilskeywordguardts)

---

## SVG 아이콘 컴포넌트 — `components/icons/` + `components/shared/Motif.tsx`

| 컴포넌트 | 역할 |
|---|---|
| `DasibomLogo` | 다시봄 새싹 로고 |
| `Conversation` | 대화·정리 아이콘 |
| `SafeHaven` | 보호·우산 아이콘 |
| `Phone` | 전화 자원 안내 |
| `CrisisResources` | 위기 자원 메뉴 진입 |
| `Motif` | 커뮤니케이션 스타일 (파도·산·불꽃·이파리·달빛·별빛) |

자세한 카탈로그·사용법: [icons.md](./icons.md)

---

*변경 이력: V14 (2026-05-16) — `docs/ui/design-handoff.md`의 컴포넌트 매핑 테이블 분리 + HAX 체크리스트 양방향 링크 추가.*
