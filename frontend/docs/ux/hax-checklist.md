# HAX 18 가이드라인 — 다시봄 컴포넌트별 체크리스트

> **이 문서의 위치**: 본 문서는 [`principles.md`](./principles.md)의 *원칙 1군 — AI 중재자 신뢰성*을 **컴포넌트 단위 체크리스트**로 풀어낸 것입니다. PR 리뷰, 새 컴포넌트 작성, 기존 화면 audit 시 이 문서를 *기계적으로* 따라가며 점검합니다.
>
> **사용 방법**: 작업 중인 컴포넌트의 섹션을 찾아 해당 항목들을 하나씩 확인합니다. 18개 모든 가이드라인을 모든 컴포넌트에 적용할 필요는 없습니다 — 각 컴포넌트 섹션에는 *해당 컴포넌트에 진짜로 적용되는* 가이드라인만 포함되어 있습니다.
>
> **현재 기준**: V1.5 카톡식 채팅 + Phase A/B/C/D 중재 컨텍스트 강화 완료 (2026-04-30).
>
> **출처**: Microsoft Research, [HAX Toolkit — Guidelines for Human-AI Interaction](https://www.microsoft.com/en-us/haxtoolkit/ai-guidelines/) (Amershi et al., CHI 2019). 본 문서는 18개 가이드라인을 다시봄 도메인(갈등 중재 AI, 양방향 비대칭 사용자, 위기 사용자 가능성, V1.5 카톡식 자유 채팅 구조)에 맞게 재해석합니다.

---

## Part A — HAX 18 가이드라인 빠른 참조

다시봄에 적용 가능한 정도를 **A/B/C** 3단계로 분류합니다.

- **A (필수)**: 이 가이드라인이 누락되면 다시봄의 핵심 가치가 손상됩니다. 모든 관련 컴포넌트에 반드시 반영.
- **B (조건부)**: 특정 화면에서 의무, 다른 화면에서는 선택.
- **C (의도적 비적용)**: 다시봄의 도메인 제약(데이터 보존 30일, 양방향 비대칭, 학습 회피)으로 적용하지 않음. *이유*는 명시.

### 시점 1: Initially (사용자가 처음 만날 때)

| # | 가이드라인 | 등급 | 다시봄 적용 요지 |
|---|---|---|---|
| **G1** | Make clear what the system can do | **A** | AI는 판결자가 아니라 번역기. 첫 채팅 진입 시 1회 안내. |
| **G2** | Make clear how well the system can do what it can do | **A** | "AI도 틀릴 수 있고, 자유롭게 다시 말씀해 주세요" — 결과 리포트의 법적 안내 박스가 모범 구현. |

### 시점 2: During interaction (사용자와 상호작용 중)

| # | 가이드라인 | 등급 | 다시봄 적용 요지 |
|---|---|---|---|
| **G3** | Time services based on context | **B** | 입력 중 침묵·머뭇거림에 카운트다운 부재. `TypingBubble`이 G3의 양방향 적용. |
| **G4** | Show contextually relevant information | **B** | 채팅 중엔 채팅에만 집중. 결과 리포트는 결과 화면에서. |
| **G5** | Match relevant social norms | **A** | 한국어 존댓말, 가정형, 양쪽 호명 균형. 디자인 시스템과 일치. |
| **G6** | Mitigate social biases | **A** | A 피치, B 세이지 동등 면적. `myRole` 기반 시점별 카피. BE 프롬프트의 호명 순서 균형. |

### 시점 3: When wrong (AI가 틀렸을 때)

| # | 가이드라인 | 등급 | 다시봄 적용 요지 |
|---|---|---|---|
| **G7** | Support efficient invocation | **B** | V1.5에선 자유 채팅이라 명시 invoke 없음 — 다음 메시지로 자연 invoke. |
| **G8** | Support efficient dismissal | **A** | "잠시 멈추기" / "이 세션 그만두기"가 1탭 안에. *위기 모달은 의도적 비적용 (§B16)*. |
| **G9** | Support efficient correction | **A** | V1.5에선 자유 채팅이 G9의 자연 적용. `FinalizeSuggestionCard`의 "더 이야기할래요" 명시 적용. |
| **G10** | Scope services when in doubt | **A** | 의미 모호 시 추측 답변 금지. BE 프롬프트의 책임. |
| **G11** | Make clear why the system did what it did | **A** | `ContributionRatio` 법적 안내 박스 + `conflictType` 보조 카피가 모범 구현. |

### 시점 4: Over time (시간이 지남에 따라)

| # | 가이드라인 | 등급 | 다시봄 적용 요지 |
|---|---|---|---|
| **G12** | Remember recent interactions | **C** | 의도적 비적용. 30일 자동 만료, AI가 먼저 과거를 꺼내지 않음. *세션 내* Phase A/B/C 컨텍스트는 학습이 아닌 추론이므로 비충돌. |
| **G13** | Learn from user behavior | **C** | 의도적 비적용. 한쪽 패턴 학습이 비대칭 편향을 만듦. |
| **G14** | Update and adapt cautiously | **B** | 프롬프트·정책 업데이트 시 진행 중 세션 영향 금지. BE 책임. |
| **G15** | Encourage granular feedback | **C** | 채팅 *중* 평가 금지. 결과 화면 이후만. |
| **G16** | Convey the consequences of user actions | **A** | "이 글은 AI가 정리해서 전달됩니다" + InviteModal 카피가 모범 구현. |
| **G17** | Provide global controls | **A** | 세션 삭제·중단·이력 통제가 항상 동등 위계. `/(dashboard)/history` 멀티셀렉트 삭제. |
| **G18** | Notify users about changes | **B** | 정책·프롬프트 변경 시 다음 세션 시작 화면에서 고지. |

**적용 분포**: A 11개, B 5개, C 3개 (의도적 비적용 + 근거 명시). 누군가 "왜 다시봄에는 학습 기능이 없냐"라고 질문할 때 답할 수 있어야 합니다 — 그래서 C 등급도 빠지지 않고 명시했습니다.

---

## Part B — 컴포넌트별 체크리스트

각 컴포넌트 섹션은 다음 형식을 따릅니다.

```
### <컴포넌트명> (<경로>)
**역할**: 한 줄 요약
**적용 가이드라인**: G#, G#, G# (등급 표시)

#### 체크 항목
- [ ] G# — 구체적 확인 사항
```

PR 리뷰 시 변경된 컴포넌트의 체크 항목을 모두 검증합니다.

V1.5 구조에 맞춰 컴포넌트는 **라우트 그룹**(B1~B11)과 **재사용 컴포넌트 그룹**(B12~B22)으로 나눕니다.

---

## B-route. 라우트 페이지

### B1. Landing — `app/page.tsx`

**역할**: 신규 방문자가 처음 만나는 화면. 다시봄이 무엇이고 무엇이 *아닌지* 이해시키는 곳.

**적용 가이드라인**: G1 (A), G2 (A), G5 (A)

#### 체크 항목

- [ ] **G1** 메인 카피에 "AI가 양쪽의 말을 정리해 드려요" 같이 *기능*이 명시되어 있다.
- [ ] **G1** "AI가 누가 옳은지 판결합니다", "AI가 갈등을 해결해 드립니다" 같은 *과대 약속*이 없다.
- [ ] **G2** "결과는 참고용입니다", "모든 갈등을 해결할 수 있는 도구는 아닙니다" 같은 메시지가 어딘가에 (FAQ 또는 본문에) 있다.
- [ ] **G5** 한국어 존댓말 일관성, 호명 익명 기본, 친밀 관계만 정상화하는 시각 요소(하트, 손잡는 일러스트) 부재.

---

### B2. Onboarding Intro — `app/(onboarding)/onboarding/intro/page.tsx`

**역할**: 온보딩 검사 시작 안내 — "10문항 검사는 필수 / MBTI는 선택".

**적용 가이드라인**: G1 (A), G16 (A), G17 (B)

#### 체크 항목

- [ ] **G1** 결과가 *진단*이 아니라 *경향성 관찰*이라는 점이 표현되어 있다.
- [ ] **G16** "이 답변은 당신의 스타일을 분류하는 데만 쓰이며, 상대에게 공유되지 않습니다" 명시.
- [ ] **G17** "지금은 건너뛰기" 옵션 검토 (현재 필수). 사용자가 게스트로도 진행 가능한 경로 명확히.

---

### B3. Onboarding Questions — `app/(onboarding)/onboarding/page.tsx` + `LikertQuestion`, `MbtiAxisSlider`

**역할**: 10문항 Likert + (선택) MBTI 4축. 답변 후 자동 진행 + 마지막 자동 제출.

**적용 가이드라인**: G6 (A), G16 (A), G17 (A)

#### 체크 항목

- [ ] **G6** Likert 5점 척도에서 한쪽 끝이 사회적으로 "더 바람직하게" 보이지 않는다.
- [ ] **G6** 성별·연령·관계 형태에 따라 다르게 해석될 표현 회피.
- [ ] **G16** 답변이 어디에 쓰이는지 첫 문항 진입 시 1회 노출.
- [ ] **G17** "이전" 버튼 또는 brower back으로 답변 수정 가능 (현재 `currentIdx` decrement으로 가능).
- [ ] **G17** 이미 답한 항목은 store에 저장되어 다시 진입 시 복원됨 (현재 `useEffect`로 hydrate).

#### 안티패턴 (피해야 할 것)
- ❌ "정확한 결과를 위해 모든 문항에 답해 주세요"라는 *강제 카피*.
- ❌ 진행률을 100% 채우라고 압박하는 시각 효과.

---

### B4. Auth — `app/auth/login`, `app/auth/signup`, `app/auth/guest`, `app/auth/callback/[provider]`, `app/(auth)/forgot-password`, `app/(auth)/reset-password/[token]`

**역할**: 가입·로그인·게스트 진입·OAuth callback·비밀번호 재설정.

**적용 가이드라인**: G1 (B), G16 (A)

#### 체크 항목

- [ ] **G1** 가입 완료 후 첫 채팅 진입 시 G1·G2 카드가 노출되는 경로 보장.
- [ ] **G16** 가입 약관 외에 *UI에서* "30일 후 자동 삭제됩니다" 같은 핵심 사실 표시.
- [ ] **G16** "OAuth 계정 정보는 로그인에만 쓰이고, AI 학습에 사용되지 않습니다" 명시.
- [ ] **G16** 게스트 모드에서도 데이터 정책 명시 (게스트는 더 짧은 보존 기간 등).

---

### B5. NewSession — `app/session/new/page.tsx`

**역할**: 관계 유형 선택. 첫 세션이면 G1·G2 카드 진입 직전 노출 트리거 지점.

**적용 가이드라인**: G1 (A), G5 (A), G17 (A)

#### 체크 항목

- [ ] **G1** 사용자가 *첫 세션*이라면 G1·G2 안내가 이 화면 또는 다음 화면(category) 또는 chat 진입 시 *반드시* 노출.
- [ ] **G5** 관계 옵션이 다양 (현재 5개 + 한국 특화 — 양호).
- [ ] **G5** 한쪽이 "기본값"으로 두드러지지 않음.
- [ ] **G17** `PhoneHeader`의 `onBack`이 `/`로 안전 복귀 (현재 양호).

---

### B6. CategorySelect — `app/session/category/page.tsx`

**역할**: 갈등 카테고리 2단계 선택 (middle → minor) + 직접 입력 옵션. **마지막 단계에서 즉시 세션 생성 → 채팅으로 이동**.

**적용 가이드라인**: G1 (A), G5 (A), G6 (A), G10 (B), G16 (A)

#### 체크 항목

- [ ] **G1** 세션 생성이 *되돌릴 수 없는* 단계는 아님 (BE에서 삭제 가능). 단, "다음 화면에서 채팅이 시작돼요" 같은 *결과 예고*가 마지막 minor 선택 버튼 위에 있는지 확인.
- [ ] **G5·G6** 카테고리명이 진단명·법률 용어가 아닌 *상황 묘사* (디자인 시스템 금지어 정책과 일치).
- [ ] **G6** 모든 카테고리가 *가해자 판단*이 아닌 *상황 묘사* ("폭언" → "큰소리로 다툼", "외도" → "신뢰가 깨진 사건").
- [ ] **G10** "기타·잘 모르겠음" 또는 "직접 입력" 옵션 존재 (현재 `allowCustomInput` 분기 — 양호).
- [ ] **G6** 위험 카테고리 ("데이트 폭력", "가정 폭력") 선택 시 위기 모달 직행이 아닌 *부드러운 자원 카드* 분기 검토.
- [ ] **G16** 세션 생성 직전 "지금 시작하면 자동 저장돼요. 언제든 멈출 수 있어요" 같은 안내 검토.

---

### B7. ChatPage (Route) — `app/session/chat/[id]/page.tsx`

**역할**: 채팅 라우트 entry. 세션 fetch → `ChatLayout` 위임. `completed` 상태면 결과로 리다이렉트.

**적용 가이드라인**: G1 (A), G14 (B)

#### 체크 항목

- [ ] **G1** **첫 진입**이면 (예: 세션의 메시지 0개) AI 능력·한계 안내를 1회 노출. `EmptyChatPlaceholder`에 한 줄 추가가 자연스러움.
- [ ] **G14** 세션 진행 중 정책·프롬프트 swap 영향 차단 — BE 책임이지만 FE는 세션 시작 시점의 프롬프트 버전을 잠그는 것이 이상적.
- [ ] **G16** 로딩 중 사용자에게 "세션을 불러오고 있어요" 안내 (현재 `loading || !session`이면 `null` 반환 — 빈 화면이라 사용자에게 *무엇이 일어나는지* 표시 필요).

---

### B8. JoinB — `app/session/join/[token]/page.tsx`

**역할**: B가 초대받아 들어오는 화면. *비대칭 비동기 협업*의 핵심 진입점.

**적용 가이드라인**: G1 (A), G5 (A), G6 (A), G16 (A)

#### 체크 항목

- [ ] **G1** B에게도 G1·G2 안내가 *반드시* 보인다 (A가 본 동일한 내용).
- [ ] **G5·G6** 시작 카피가 "당신을 초대한 분이 다음과 같이 말했습니다" ✗ → "두 분이 함께 이야기를 정리해 보려 합니다. 먼저 ㅇㅇ님의 마음부터 들어볼게요" ✓
- [ ] **G16** A의 메시지 원문이 절대 노출되지 않음. B는 자기 시점에서 처음부터 적음.
- [ ] **G16** 게스트로 참여할 수 있는 옵션 명시.
- [ ] **G6** "이미 A가 진행 중이니 빨리 답하세요" 같은 압박 카피 부재.

---

### B9. Result — `app/session/result/[id]/page.tsx` + `app/session/result/[id]/solo/page.tsx`

**역할**: 결과 리포트 라우트 entry. `ReportLayout`을 variant 'card' 또는 'story'로 렌더.

**적용 가이드라인**: G2 (A), G11 (A), G16 (A)

#### 체크 항목

- [ ] **G2·G11** `ContributionRatio`의 법적 안내 박스가 항상 표시 (현재 양호 — 유지).
- [ ] **G16** 결과 공유 (`ShareCardRatio`, `ShareCardBlurredLetter`, `ShareCardMetaphor`) 버튼 옆에 *어떤 정보가 포함되는지* 명시.
- [ ] **G16** 갈등 원문이 공유 카드에 절대 미포함됨을 unit test로 검증.
- [ ] **G2** "당신은 ~형 사람입니다" 카피 부재 — `StyleCombination`이 *영구 진단* 표현을 쓰지 않는지 확인.

#### 안티패턴
- ❌ "지난번보다 0.3°C 올랐어요" — 데이터 정밀도 과장.
- ❌ "55:45이니 A님이 더 노력하셔야 합니다" — 처방 금지.

---

### B10. SessionHistory (Route) — `app/(dashboard)/history/page.tsx` + `app/session/history/[id]/page.tsx`

**역할**: 과거 세션 목록 (`(dashboard)`) + 개별 세션 메시지 보기 (`session/history/[id]`).

**적용 가이드라인**: G12 (C), G17 (A)

#### 체크 항목

- [ ] **G12** AI가 *먼저* 과거를 꺼내지 않음 (의도적 비적용). 새 세션의 AI 응답이 자동으로 과거를 인용하지 않음.
- [ ] **G12** 사용자가 명시적으로 `/session/history/[id]`를 열 때만 과거 노출.
- [ ] **G17** 멀티셀렉트 + 일괄 삭제 (현재 양호).
- [ ] **G17** 삭제 확인 모달이 *되돌리기* 옵션을 짧게 제공할 수 있는지 검토 (현재 `showDeleteConfirm` + `deleting`).
- [ ] **G17** 진행 중 세션(`ACTIVE_STATUSES`)에 라벨로 표시되어 사용자가 자기 진행 상태를 파악 가능 (현재 양호).

---

### B11. Profile — `app/(dashboard)/profile/page.tsx`

**역할**: 사용자 프로필. 온보딩 결과 수정·계정 관리·로그아웃·탈퇴.

**적용 가이드라인**: G17 (A), G18 (B)

#### 체크 항목

- [ ] **G17** 탈퇴 옵션이 *숨겨지지* 않고 동등 위계 표시.
- [ ] **G17** 탈퇴 시 "전체 이력 삭제" 정책 명시.
- [ ] **G18** 정책·프롬프트 변경 사항이 있다면 프로필 진입 시 1회 고지 검토.

---

## B-component. 재사용 컴포넌트

### B12. ChatLayout — `components/chat/ChatLayout.tsx`

**역할**: 채팅 컨테이너. Solo/Duo 분기, polling으로 세션 상태 갱신, Solo→Duo 전이 감지하여 `PartnerJoinedToast` 표시. 종료 감지 시 결과 화면으로 push.

**적용 가이드라인**: G3 (A), G18 (B)

#### 체크 항목

- [ ] **G3** Polling 주기(5초)가 사용자에게 부담을 주는 깜빡임을 만들지 않음 (현재 silent refresh — 양호).
- [ ] **G18** Solo→Duo 전이 시 `PartnerJoinedToast` 알림 — 사용자가 세션 *상태 변화*를 알 수 있음. 양호.
- [ ] **G18** `completed` 상태로의 전이 시 결과 화면 자동 push — 사용자가 *왜* 화면이 바뀌는지 짧게 안내 가치 있음.

---

### B13. ChatPanel — `components/chat/ChatPanel.tsx`

**역할**: 한 사용자의 채팅 본체. 메시지 fetch (polling), 전송 (optimistic), 위기 거절 처리, finalize 로직.

**적용 가이드라인**: G1 (A), G3 (A), G6 (A), G16 (A)

#### 체크 항목

- [ ] **G1** `EmptyChatPlaceholder`에 AI 능력·한계 한 줄 추가 (현재: "무슨 일이 있으셨어요? / 편한 말로, 카톡처럼 한 줄씩 적어주세요. / 제가 차분히 들을게요" — *AI라는 사실*이 명시 안 됨).
- [ ] **G3** `TypingBubble`이 AI 응답 중 표시 + `aria-live="polite"` (현재 양호).
- [ ] **G3** Polling 실패 시 silent (현재 `console.debug`만 — 양호).
- [ ] **G6** AI 메시지(`MEDIATOR_TO_A` / `MEDIATOR_TO_B`)와 사용자 메시지가 시각적으로 명확히 구분 (현재 `isMine` 좌우 분기 — 더 강한 시각 신호 검토).
- [ ] **G16** 위기 거절 시 (`crisisLevel === 1` 응답) optimistic 메시지 즉시 제거 + `CrisisModal` 표시 (현재 양호).

---

### B14. ChatInput — `components/chat/ChatInput.tsx`

**역할**: 채팅 입력 + 클라이언트 사이드 위기 키워드 사전 체크.

**적용 가이드라인**: G3 (A), G16 (A)

#### 체크 항목

- [ ] **G3** 카운트다운, 시간 제한 부재 (현재 양호).
- [ ] **G3** 30초 이상 입력 정지 시 *비강제* 격려 토스트 검토 (현재 미구현 — 보강 후보).
- [ ] **G16** 입력창 placeholder 또는 하단에 "이 글은 AI가 정리해서 ㅇㅇ님께만 전달돼요" 같은 안내 추가 검토.
- [ ] **G16** 위기 키워드 감지 시 `alert`로 핫라인 표시 (현재 구현). **`CrisisModal` 같은 다이얼로그로 격상 + 일관 거동** 검토.
- [ ] **G6** placeholder가 한쪽 시점을 강요하지 않음 ("편한 말로 적어주세요" — 현재 양호).

---

### B15. MessageBubble — `components/chat/MessageBubble.tsx`

**역할**: 채팅 메시지 1개 렌더 (사용자 또는 AI 중재자).

**적용 가이드라인**: G1 (A), G6 (A)

#### 체크 항목

- [ ] **G1** AI 메시지(`!isMine` + sender가 `MEDIATOR_TO_*`)임을 사용자가 *명확히* 인식 가능. 현재 좌우 분기 + 색상만으로는 부족 — **작은 라벨/아이콘 검토**.
- [ ] **G6** 색상 면적이 한쪽으로 치우치지 않음 (`var(--P-a)` 와 `var(--P-card)`).
- [ ] **G5** 시간 표시가 한국어 24시간 포맷 (현재 양호).

---

### B16. CrisisModal (chat) — `components/chat/CrisisModal.tsx` + CrisisResourceModal — `components/shared/CrisisResourceModal.tsx`

**역할**: 위기 키워드 감지 시 모달. **HAX보다 `principles.md` §2.3 (Designing for Safety)이 우선합니다.**

**적용 가이드라인**: G3 (A), G8 (C), G16 (A)

#### 체크 항목

- [ ] **G3** 즉시 표시, 다른 어떤 모달보다 우선.
- [ ] **G8** **현재 갭**: `chat/CrisisModal`은 `onClick={onClose}`로 바깥 클릭 닫기 허용 + `shared/CrisisResourceModal`은 ESC로 닫힘 → **권장: 두 모달 모두 명시적 액션 버튼으로만 닫히게 변경**.
- [ ] **G8** 닫기 버튼 라벨이 "닫기" / "알겠어요"가 아니라 "지금은 괜찮아요" 같이 사용자가 *현재 안전*을 표현할 수 있는 카피 검토.
- [ ] **G16** `tel:` 링크 결과 명시 — 누르면 즉시 전화 연결됨이 사용자에게 명확.
- [ ] **G16** 두 모달의 거동을 통일 — 사용자가 어느 진입점에서 만나든 같은 경험.
- [ ] **G3** 위기 거절 후 입력창은 비활성화되지 않음 (사용자가 다른 표현으로 다시 적을 수 있게). 단 위기 키워드 다시 포함 시 즉시 재거절.

#### 의도적 비적용 근거
- HAX는 일반적으로 G8 "쉬운 dismiss"를 권장하지만, 다시봄 위기 모달은 *의도적으로* dismiss를 어렵게 만듭니다. 이는 PenzeyMoog *Design for Safety*의 "사용자가 위기 상황에서 실수로 안전망을 떨쳐내지 못하게" 원칙에 의한 것입니다. 코드 주석에 명시 권장.

---

### B17. PartnerPanel + PartnerStatusBar + SwipeContainer — `components/chat/`

**역할**: Duo 모드에서 상대방 진행 상태를 *블러된 메타데이터로만* 보여주는 패널. 데이터 격리의 핵심.

**적용 가이드라인**: G1 (A), G16 (A)

#### 체크 항목

- [ ] **G1** "내용은 두 분의 사생활 보호를 위해 가려져 있어요" 카피 항상 표시 (현재 양호).
- [ ] **G16** `BlurredBubble`은 글자 수와 시간만 노출 — 본문 미노출 (현재 양호).
- [ ] **G16** API `partner-messages`가 BE에서 메타데이터만 응답하는지 코드 레벨 검증 (BE 책임).
- [ ] **G16** 스와이프 안내 ("← 스와이프하면 본인 채팅으로 돌아갈 수 있어요") 명확 (현재 양호).
- [ ] **G6** 상대 메시지 색상이 자기 메시지와 동등한 비중 (현재 `var(--P-card)` / `var(--P-a)` 동등 면적).

#### 모범 구현 사례
- 이 컴포넌트군은 `principles.md` §2.2 "양쪽 데이터 격리"의 모범 구현입니다. 향후 새 협업 기능 설계 시 이 패턴을 참고합니다.

---

### B18. InviteModal — `components/chat/InviteModal.tsx`

**역할**: B를 초대하는 링크 + 톤별 메시지 + native share. 다시봄에서 가장 *악용 가능성*이 높은 기능.

**적용 가이드라인**: G1 (A), G16 (A), G17 (A)

#### 체크 항목

- [ ] **G1** "상대분이 합류해도 두 분의 대화는 서로 보이지 않아요. 제가 양쪽 마음을 따로 듣고, 균형있게 정리해드려요" — 데이터 격리 명시 (현재 양호 — 유지).
- [ ] **G16** "링크 만들기"가 *즉시 발송*이 아님 — `navigator.share` 또는 클립보드 복사 (현재 양호 — 유지).
- [ ] **G16** 만료 정보 ("X시간 안에 한 번만 사용") 표시 검토 — 현재 미명시.
- [ ] **G16** 링크 안에 갈등 내용 미포함 (토큰만) — BE 책임이지만 FE 표시도 토큰만 (현재 양호).
- [ ] **G17** "나중에 할게요" 옵션 (현재 양호).
- [ ] **G17** 초대 *취소* 또는 *링크 무효화* 옵션 검토 — 현재 발송 후 취소 흐름 부재.
- [ ] **G16** 카톡 미리보기(Open Graph)에 갈등 내용 미노출 — 메타 태그 audit 필요.

---

### B19. FinalizeSuggestionCard — `components/chat/FinalizeSuggestionCard.tsx`

**역할**: AI가 정리 시점이라 판단했을 때 채팅 안에 등장하는 카드. "정리하기" / "더 이야기할래요" 양 옵션.

**적용 가이드라인**: G9 (A), G16 (A)

#### 체크 항목

- [ ] **G9** "정리하기"와 "더 이야기할래요"가 동등 시각 비중 (현재 `flex: 1` × 2 — 양호).
- [ ] **G9** 한쪽이 *기본/추천*으로 강조되지 않음 — 단, "정리하기"가 채워진 버튼, "더 이야기할래요"가 윤곽선 버튼이라 *기본 유도*가 약하게 있음. 이는 의도된 design system primary/ghost인지 확인.
- [ ] **G16** 사용자가 "정리하기" 선택 후 상대방 동의 대기 ("상대방의 동의를 기다리고 있어요…") 메시지 표시 (현재 양호).
- [ ] **G9** Pending 상태에서 사용자가 *취소*하고 더 이야기할 수 있는 경로 검토.

---

### B20. ChatHeader — `components/chat/ChatHeader.tsx`

**역할**: 채팅 상단 헤더. 초대·정리 액션 + 세션 정보.

**적용 가이드라인**: G8 (A), G17 (A)

#### 체크 항목

- [ ] **G8·G17** **현재 갭**: 헤더에 "잠시 멈추기" / "이 세션 그만두기" 명시 액션 부재 — 추가 검토.
- [ ] **G8** "초대하기" 버튼은 Solo 모드에서만 활성화 (`canInvite={!isDuo}` — 양호).
- [ ] **G17** 도움말·핫라인 진입점 (위기 자원에 *상시* 접근 가능) — 현재 미구현.

---

### B21. ReportLayout + ContributionRatio + NeedsMap + MetaphorCards + NVCScript + RepairSuggestions + StyleCombination + SoloResult — `components/result/`

**역할**: 결과 리포트의 모든 카드. 다시봄의 가장 강력한 화면이자 *가장 위험한* 화면.

**적용 가이드라인**: G2 (A), G6 (A), G11 (A), G16 (A)

#### 체크 항목

- [ ] **G2·G11** `ContributionRatio` 법적 안내 박스 — *모범 구현*이므로 유지 + 다른 카드(NVCScript, RepairSuggestions)에도 유사한 *AI 한계* 한 줄 검토.
- [ ] **G6** A 피치 / B 세이지 동등 채도 (현재 양호).
- [ ] **G6** `myRole` 기반 시점별 카피 (현재 ReportLayout이 `myName`/`partnerName` 추출 — 양호).
- [ ] **G6** `NeedsMap` 점 위치 — 현재 절대 좌표. *시점별 좌표 회전* 검토 (단, 두 사용자가 같은 리포트 비교 가능성 고려).
- [ ] **G11** 모든 수치 옆에 `?` 근거 제공 검토 — 현재 `ContributionRatio`만 *법적 안내*가 있음. `NeedsMap`도 보강 후보.
- [ ] **G16** 외부 공유 (`ShareCard*`)에 갈등 원문 미포함 — unit test 검증.
- [ ] **G2** `StyleCombination` 카피가 *영구 진단*이 아닌 *현재 시점 관찰*임을 표현.
- [ ] **G16** `powerImbalanceDetected === true` 시 ContributionRatio 숨김 + 위기 자원 박스 표시 (현재 모범 구현 — 유지).
- [ ] **G2** `SoloResult`에서 "이건 한쪽 시점만 반영한 결과"임을 명시 (현재 양호).

#### 안티패턴
- ❌ "지난번보다 0.3°C 올랐어요" — 데이터 정밀도 과장.
- ❌ "55:45이니 A님이 더 노력하셔야 합니다" — 처방 금지.
- ❌ 한쪽 색상만 진하게 칠한 차트.

---

### B22. KeywordGuard + Crisis utilities — `components/shared/KeywordGuard.tsx` + `lib/utils/keywordGuard.ts`

**역할**: 입력 필드 옆 인라인 경고 (Level 1/2). Level 1은 모달, Level 2는 배너.

**적용 가이드라인**: G6 (A), G9 (A), G10 (B), G16 (A)

#### 체크 항목

- [ ] **G6** 메시지가 비난조가 아니다 ("이 단어는 사용 금지" ✗ → "이 표현은 ㅇㅇ님 마음을 거칠게 만들 수 있어요. 다른 말로 바꿔보시겠어요?" ✓).
- [ ] **G9** 권위본의 권장 대체어를 함께 제안 (예: "과실비율" → "화해 기여도").
- [ ] **G10** Level 2는 차단하지 않음, Level 1만 차단.
- [ ] **G16** 차단 시 *왜* 막는지 명시 ("이 표현은 결과를 왜곡시킬 수 있어 다른 표현을 부탁드려요").

---

### B23. PhoneFrame + Logo + Footer + LegalFooter + Motif + Dashes + RelationshipColorSync — `components/shared/`

**역할**: 모바일 우선 레이아웃 + 시각 요소. AI 자체와 직접 관련 없으나 *전체 신뢰 인상*에 영향.

**적용 가이드라인**: G5 (B), G18 (B)

#### 체크 항목

- [ ] **G5** 데스크톱에서 폰 프레임 안 렌더링이 *장난스럽지* 않음 (갈등 사용자가 진지한 도구로 인식).
- [ ] **G18** `LegalFooter`가 결과 화면에 일관 위치 표시 — 약관·법적 안내가 어디에서나 같은 자리에.
- [ ] **G5** `RelationshipColorSync`가 관계 유형에 따라 색상을 바꾸지만, *어느 관계에서도* 시각적 무게가 동등.

---

## Part C — 공통 PR 체크리스트

새 컴포넌트 또는 새 화면 PR 시 다음 5개 질문에 답해야 합니다.

```markdown
## HAX 체크리스트

- [ ] 이 변경이 영향을 주는 컴포넌트의 hax-checklist.md 섹션을 모두 읽었습니다.
- [ ] **G1·G2**: 이 화면에서 사용자가 AI를 처음 만난다면, AI 능력·한계 안내가 노출되거나 이미 노출된 적이 있습니다.
- [ ] **G6**: 양쪽 호명 순서, 색상 면적, 시각 위계가 균형을 이룹니다.
- [ ] **G9**: AI 출력이 어색할 때 사용자가 즉시 다른 메시지로 정정할 수 있습니다.
- [ ] **G16**: 사용자 입력의 결과(어디로 가고, 누가 보고, 언제 삭제되는지)가 입력 *전에* 명시되어 있습니다.
- [ ] **G17**: "잠시 멈추기" 또는 "이 화면 빠져나가기"가 1탭 안에 가능합니다.
```

이 5개는 *최소* 항목입니다. 영향을 받는 가이드라인이 더 있다면 그 항목도 확인합니다.

또한 새 외부 공유·새 입력 필드·새 알림 PR은 [`principles.md`](./principles.md) §2.1의 4개 abuser/survivor archetype 질문도 추가로 답해야 합니다.

---

## Part D — 시즌별 audit (반기 1회)

다음 항목은 신규 PR 단위로는 잡기 어렵고, 반기 단위 전체 audit으로 점검합니다.

### D1. G6 — 누적 편향 점검
- 최근 100개 세션의 결과 리포트를 샘플링해 다음을 검증:
  - A·B 호명 순서가 50:50에 근접하는가?
  - 화해 기여도가 한쪽으로 치우친 분포(예: 60:40 평균)를 보이지 않는가?
  - 만약 치우쳤다면 프롬프트(`shared/docs/prompts/`)·UI 어디에 편향 소스가 있는지 추적.

### D2. G2 — 신뢰 calibration 점검
- 사용자가 finalize 후 `decline` 비율 측정.
- 너무 높으면 (예: >40%): AI 정리 품질이 신뢰 임계점 아래.

### D3. G16 — 데이터 흐름 명시 누락 점검
- 모든 입력 컴포넌트에 "이 입력이 어디로 가는지" 카피가 있는지 자동 검사 (lint 규칙 추가 검토).

### D4. G14 — 정책 변경 영향 검토
- 직전 6개월 내 권위본 정책(`shared/docs/policies/`) 변경 사항이 진행 중 세션에 영향을 줬는지 확인.
- 영향 있었다면 사용자에게 사후 고지가 됐는지 확인.

### D5. G8 — 위기 모달 거동 일관성
- `chat/CrisisModal`과 `shared/CrisisResourceModal`의 dismiss 거동이 일치하는지 확인.
- 두 모달이 *동일한* 위기 정책에 따라 작동하는지 확인.

---

## Part E — 의도적 비적용(C)의 주기적 재검토

다시봄이 의도적으로 적용하지 않는 G12·G13·G15는 *환경 변화*에 따라 재검토 가치가 있습니다.

### E1. G12 (Remember recent interactions) — 재검토 트리거
- 사용자가 "같은 갈등이 반복돼서 매번 처음부터 적기 힘들다"는 피드백을 다수 보낼 때
- 재검토 시 검토 사항: 가해자 추적·통제 도구로 변질될 가능성, 30일 만료 정책과의 충돌
- 단, *세션 내* Phase A/B/C 컨텍스트는 학습이 아닌 추론이므로 비충돌 — 이 차이를 명확히 유지

### E2. G13 (Learn from user behavior) — 재검토 트리거
- AI 응답 품질이 정체되어 단순 사용자 행동 학습이 도움이 될 명확한 근거가 있을 때
- 재검토 시 검토 사항: 양방 비대칭 학습으로 인한 편향, 데이터 보존 정책

### E3. G15 (Encourage granular feedback) — 재검토 트리거
- 결과 리포트 *후* 가벼운 피드백 외에, 채팅 *중* 피드백이 품질에 결정적이라는 데이터가 모일 때
- 재검토 시 검토 사항: 갈등 한복판의 평가가 신뢰할 수 있는가, 평가 부담이 사용자 경험을 해치는가

---

## 관련 문서

- 본 문서의 모(母) 문서: [`./principles.md`](./principles.md) — 원칙 1군 §1.1~1.8 참조
- 디자인 시스템: [`../ui/design-handoff.md`](../ui/design-handoff.md) — 카피 톤, 양쪽 호명 균형, 색상 동등성
- 위기 모달 정책: [`shared/docs/policies/crisis-detection.md`](../../../shared/docs/policies/crisis-detection.md) "FE 구현 가이드" — G8 비적용의 근거
- 금지어 정책: [`../policies/forbidden-words-lint.md`](../policies/forbidden-words-lint.md) — G6·G9·G10 구현
- 권위본 (다른 디렉토리): `shared/docs/policies/`

## 외부 참고

- [HAX Toolkit — Guidelines for Human-AI Interaction](https://www.microsoft.com/en-us/haxtoolkit/ai-guidelines/)
- [HAX Design Library](https://www.microsoft.com/en-us/haxtoolkit/library/) — 18개 가이드라인 각각의 디자인 패턴·예시
- [HAX Workbook](https://www.microsoft.com/en-us/haxtoolkit/workbook/) — 팀 워크숍 진행 시 활용
- 원전: Amershi, S. et al. (2019). "Guidelines for Human-AI Interaction." *CHI 2019*. https://www.microsoft.com/en-us/research/publication/guidelines-for-human-ai-interaction/

## 변경 이력

- 2026-04-27 (v1.1) — V1.5 카톡식 채팅 + Phase A/B/C 반영. 컴포넌트 매핑 재구성: 라우트(B1~B11) + 재사용(B12~B23). `MediationSession`, `MediatorMessage`, `TurnInput`, `WaitingB`, `DescribeFlow` 폐기. `ChatLayout`, `ChatPanel`, `ChatInput`, `MessageBubble`, `PartnerPanel`, `InviteModal`, `FinalizeSuggestionCard` 추가. `MetaphorCards`, `NVCScript`, `RepairSuggestions`, `StyleCombination`, `ShareCard*` 추가. 위기 모달 dismiss 갭 명시 (B16).
- 2026-04-27 (v1.0) — 초안.
