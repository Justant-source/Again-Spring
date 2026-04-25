# Solo-First 재설계 가이드

**버전**: v2.0
**대상**: Claude Code
**연관 작업**: `REFINEMENT_WORK_ORDER.md` Phase 1

---

## 🎯 목표

**"신규 사용자가 5분 안에 첫 결과를 받아볼 수 있게 한다."**

기존 11게이트 → 5게이트 이내로 압축. Solo 모드를 **메인 진입로**로, 페어는 **업그레이드 옵션**으로 재정의.

---

## 📊 Before/After 비교

### Before (11게이트)

```
1. 회원가입
2. 온보딩 10문항
3. 세션 생성 → 관계 유형 선택
4. 대분류 선택
5. 중분류 선택
6. 소분류 선택
7. 상황 서술
8. 초대 링크 생성
9. 상대방 가입·테스트 ⏸ 24시간 대기
10. A/B 입력 6턴
11. 결과 도달

→ 평균 도달 시간: 24시간+ (상대방 의존)
→ Solo 전환은 24시간 후에만 가능
```

### After (5게이트)

```
1. 회원가입 (또는 게스트 즉시 시작)
2. 온보딩 10문항 (90초)
3. Quick Describe (관계 + 카테고리 + 한 줄 상황)
4. AI 분석 (10-20초)
5. Solo 결과 도달 ✨

→ 평균 도달 시간: 5분 이내
→ 페어는 결과 페이지에서 옵션으로 제공
```

---

## 🛣️ 새로운 진입 동선

### 신규 사용자

```
┌─────────────────────────────────────────────┐
│ [LANDING]                                   │
│ "다시 봄. 다시 바라봄."                      │
│ ┌─────────────────────────────────┐         │
│ │ 🌱 지금 갈등을 정리하고 싶어요    │ → /onboarding
│ └─────────────────────────────────┘         │
│ ┌─────────────────────────────────┐         │
│ │ 🤝 둘이 함께 해보고 싶어요        │ → /onboarding (pair flag)
│ └─────────────────────────────────┘         │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [회원가입 OR 게스트]                         │
│ • 회원가입: 이메일 + 비밀번호 + 닉네임      │
│ • 게스트: 닉네임만                           │
│   (게스트는 결과 저장 X, 1시간 토큰)        │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [온보딩 10문항] (90초)                       │
│ • 5점 리커트 척도                            │
│ • 결과 → 6유형 + 캐릭터 카드                 │
│ • 다음 단계 안내                             │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [QUICK DESCRIBE] (1-2분)                    │
│ Step 1: 관계 유형 (5개 카드)                 │
│ Step 2: 무슨 일로? (대분류만)                │
│ Step 3: 상황 한 줄 (300자 이내)              │
│                                             │
│ ⓘ 더 자세히 안내받고 싶다면? [상세 모드]    │
│   → 기존 대/중/소분류 + 1500자 입력         │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [AI 분석 중...] (10-20초)                   │
│ • 봄 모티프 로딩 애니메이션                  │
│ • "꽃이 피어나고 있어요..."                  │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [SOLO 결과 페이지]                          │
│ • 욕구 차이 지도 (B 위치 흐릿하게)           │
│ • 은유 카드 3장 (Solo 버전)                  │
│ • 화해 기여도: 비활성화 + 안내                │
│ • NVC: A 입장만                             │
│ • Repair: 1-2개                             │
│                                             │
│ ┌─────────────────────────────────┐         │
│ │ 🤝 [상대방] 초대해서 더 정확하게  │         │
│ └─────────────────────────────────┘         │
│ ┌─────────────────────────────────┐         │
│ │ 📤 결과 카톡 공유                │         │
│ └─────────────────────────────────┘         │
└─────────────────────────────────────────────┘
```

### 페어 업그레이드 동선

```
┌─────────────────────────────────────────────┐
│ [Solo 결과 페이지]                           │
│ "🤝 상대방 초대" 클릭                        │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [초대 링크 생성]                             │
│ • 톤 선택 (부드럽게/가볍게/진지하게)         │
│ • 카톡 공유 버튼                             │
│ • URL 복사                                  │
│ ⏰ 24시간 후 만료                           │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [상대방 참여 대기]                           │
│ • 폴링 또는 SSE                              │
│ • Solo 결과는 계속 볼 수 있음                │
│ • 알림: "상대방이 참여하면 알려드릴게요"     │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [상대방 (B) 진입]                            │
│ B1. 초대 링크 클릭                           │
│ B2. B 가입/게스트                            │
│ B3. B의 온보딩 10문항                        │
│ B4. B의 상황 서술 (Quick Describe 동일 UI)  │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│ [페어 결과 자동 업그레이드]                  │
│ • A에게 알림: "결과가 업그레이드 됐어요"     │
│ • 페어 결과 페이지 표시                      │
│   - 욕구 차이 지도 (A, B 모두 표시)         │
│   - 은유 카드 3장 (양쪽 정보 종합)           │
│   - 화해 기여도 + 법적 안내문구              │
│   - NVC 양방향                              │
│   - Repair 3개                              │
└─────────────────────────────────────────────┘
```

---

## 🆕 신규 화면 사양

### 1. Quick Describe (`/session/quick-describe`)

**목적**: 기존 4단계(대/중/소/서술) → 3단계(관계/대분류/한 줄)로 압축

#### 화면 구조

```tsx
// app/(session)/quick-describe/page.tsx
'use client';

export default function QuickDescribePage() {
  const [step, setStep] = useState(1);
  const [relationType, setRelationType] = useState<RelationType | null>(null);
  const [majorCategory, setMajorCategory] = useState<string | null>(null);
  const [description, setDescription] = useState('');
  const [showDetailedMode, setShowDetailedMode] = useState(false);
  
  // ... 단계별 UI
}
```

#### Step 1: 관계 유형 (1화면)
- 5개 카드 (연인/부부/친구/가족/부모자식)
- 카드 클릭 즉시 다음 단계
- 상단 진행률 바 (1/3)

#### Step 2: 무슨 일로? (1화면)
- 선택한 관계의 **대분류만** 표시 (중/소분류는 생략)
- 예: 연인 → "연락·관심" / "시간·우선순위" / "돈·경제" / "신뢰" / "표현 방식" / "직접 입력"
- 6-8개 카드 형식
- "직접 입력" 선택 시 텍스트 입력 표시

#### Step 3: 한 줄 설명 (1화면)
- 텍스트 입력 (최소 30자, 최대 300자)
- 글자 수 카운터 표시
- 키워드 가드 작동 (위험 키워드 감지)
- 하단 푸터: 
  - "더 자세히 안내받고 싶다면?" → [상세 모드] 토글
  - 상세 모드 토글 시 기존 중/소분류 + 1500자 입력 화면으로 전환

#### 제출 버튼
- "분석 시작" 클릭 → POST `/api/sessions/quick`
- AI 분석 중 페이지로 자동 이동

### 2. AI 분석 중 화면 (`/session/[id]/analyzing`)

**목적**: 10-20초 LLM 응답 대기 동안 사용자 이탈 방지

#### 디자인
- 봄 모티프 로딩 애니메이션 (꽃잎이 피어나는 SVG)
- 단계별 메시지 순환:
  ```
  "두 분의 이야기를 듣고 있어요..." (0-5초)
  "마음의 결을 살펴보는 중이에요..." (5-10초)
  "어떻게 풀어가면 좋을지 정리하고 있어요..." (10-15초)
  "거의 다 됐어요! 결과를 준비하고 있어요..." (15-20초)
  ```
- 너무 오래 걸리면 (30초+) "조금만 더 기다려주세요" 추가 메시지

#### 기술 구현
- SSE로 진행 상태 수신 (`mediator_thinking`, `mediator_response` 이벤트)
- 응답 수신 즉시 결과 페이지로 자동 이동

### 3. Solo 결과 페이지 (`/session/[id]/result?mode=solo`)

#### 컴포넌트 구성

```tsx
<SoloResultPage>
  <ResultHeader>
    <Watermark>Solo 분석</Watermark>
    <Title>{userName}님의 갈등 정리</Title>
  </ResultHeader>
  
  <NeedsMap mode="solo" hideOpponent />
  {/* B 위치는 점선 원 + "상대방을 초대하면 채워져요" */}
  
  <MetaphorCards mode="solo" cards={3} />
  {/* Solo 버전: A 관점에서의 카드 */}
  
  <ContributionRatioPlaceholder>
    <Icon>🤝</Icon>
    <Message>
      "화해 기여도는 양쪽이 모두 참여했을 때 안내드릴 수 있어요.
       상대방을 초대하시면 더 정확한 분석이 가능해요."
    </Message>
    <Button onClick={openInviteModal}>상대방 초대하기</Button>
  </ContributionRatioPlaceholder>
  
  <NVCScript direction="aToB" />
  {/* A가 B에게 할 말만 */}
  
  <RepairSuggestions count={2} />
  {/* Solo는 2개만 */}
  
  <ActionFooter>
    <PrimaryAction onClick={openInviteModal}>
      🤝 [상대방] 초대해서 더 정확하게
    </PrimaryAction>
    <SecondaryAction onClick={openShareModal}>
      📤 결과 카톡 공유
    </SecondaryAction>
  </ActionFooter>
  
  <LegalDisclaimer />
</SoloResultPage>
```

---

## 🔄 백엔드 API 변경

### 신규 엔드포인트

#### `POST /api/sessions/quick`

Quick Describe로부터 즉시 Solo 세션 생성 + 분석 트리거.

**Request**
```json
{
  "relationType": "couple",
  "majorCategory": "connection",
  "description": "최근 연락이 너무 뜸해서 서운함을 느낍니다.",
  "customMajor": null
}
```

**Response 201**
```json
{
  "sessionId": "ses_abc123",
  "status": "analyzing",
  "estimatedSeconds": 15,
  "streamUrl": "/api/sessions/ses_abc123/stream"
}
```

**동작**:
1. 세션 생성 (status: `solo_analyzing`)
2. Solo 모드 단일 턴 입력 저장
3. LLM 호출 비동기 시작
4. SSE 스트림 URL 반환

#### `POST /api/sessions/{id}/upgrade-to-pair`

Solo 세션을 페어 세션으로 업그레이드.

**Request**
```json
{
  "inviteMessage": {
    "tone": "soft",
    "customText": null
  }
}
```

**Response 200**
```json
{
  "inviteToken": "inv_xyz789",
  "inviteUrl": "https://againspring.app/join/inv_xyz789",
  "inviteMessage": "우리 얘기 좀 정리해보고 싶어서...",
  "status": "waiting_b",
  "expiresAt": "2026-04-25T10:30:00Z"
}
```

**동작**:
1. Solo 세션의 status를 `solo_completed` → `waiting_b`로 변경
2. 초대 토큰 발급
3. 기존 페어 플로우 동선으로 이어짐

### 기존 엔드포인트 동작 변경

#### `POST /api/sessions` (기존 페어 시작)
- 기존 동작 유지 (상세 모드, 양쪽 처음부터 페어로 시작)
- "둘이 함께 해보고 싶어요" CTA에서만 호출

---

## 🗄️ DB 스키마 변경

### `sessions` 테이블

새로운 status 값 추가:
```sql
ALTER TABLE sessions
MODIFY COLUMN status ENUM(
  'waiting_b',
  'b_joined',
  'in_mediation',
  'completed',
  'solo_analyzing',     -- 신규: Solo 분석 중
  'solo_completed',     -- 신규: Solo 결과 완료
  'solo_to_pair',       -- 신규: Solo → Pair 업그레이드 진행 중
  'terminated'
);
```

새로운 컬럼 추가:
```sql
ALTER TABLE sessions
ADD COLUMN is_quick_mode BOOLEAN DEFAULT FALSE,
ADD COLUMN solo_completed_at TIMESTAMP NULL,
ADD COLUMN upgraded_to_pair_at TIMESTAMP NULL;
```

### `reports` 테이블

```sql
ALTER TABLE reports
ADD COLUMN is_solo_report BOOLEAN DEFAULT FALSE,
ADD COLUMN solo_report_id VARCHAR(32) NULL,  -- 페어 업그레이드 시 원본 Solo 리포트 참조
ADD COLUMN upgraded_at TIMESTAMP NULL;
```

---

## 📊 분석 이벤트 트래킹

다음 이벤트를 백엔드 로그·DB에 기록:

| 이벤트 | 발생 시점 | 속성 |
|---|---|---|
| `landing_view` | 랜딩 페이지 진입 | source(referrer), is_mobile |
| `signup_started` | 회원가입 화면 진입 | mode (solo/pair) |
| `signup_completed` | 회원가입 완료 | user_id, is_guest |
| `onboarding_started` | 온보딩 시작 | user_id |
| `onboarding_completed` | 온보딩 완료 | user_id, communication_style, time_taken_sec |
| `quick_describe_started` | Quick Describe 진입 | user_id |
| `quick_describe_step_completed` | 각 단계 완료 | step (1/2/3), time_taken_sec |
| `quick_describe_submitted` | 최종 제출 | session_id, char_count |
| `solo_analysis_started` | LLM 분석 시작 | session_id |
| `solo_analysis_completed` | LLM 분석 완료 | session_id, duration_sec |
| `solo_result_viewed` | Solo 결과 페이지 진입 | session_id |
| `solo_to_pair_invite_clicked` | 페어 초대 버튼 클릭 | session_id |
| `solo_to_pair_invite_sent` | 초대 링크 공유 | session_id, channel (kakao/url) |
| `pair_completed` | 페어 결과 도달 | session_id, total_time_sec |
| `share_card_generated` | 공유 카드 생성 | session_id |
| `share_card_downloaded` | 공유 카드 다운로드 | session_id, format |
| `share_to_kakao_clicked` | 카톡 공유 클릭 | session_id |

---

## 🎨 디자인 가이드

### Quick Describe 화면

- **배경**: 그라디언트 (#FAF9F6 → #E8F5E9)
- **카드 색상**: 흰색 + 부드러운 그림자
- **선택 시**: 봄 연두(#7FB77E) 테두리 + 체크 마크
- **버튼**: Primary 색상 (#7FB77E)
- **간격**: 카드 간 12px, 섹션 간 24px

### AI 분석 중 화면

- **중앙 정렬**: 봄 모티프 일러스트
- **로딩 애니메이션**: 꽃잎 회전 또는 점진적 피어남
- **메시지**: 18px / line-height 1.6 / 봄 라벤더(#B4A6E3)
- **하단**: 작은 진행률 표시 (선택)

### Solo 결과 페이지

- **워터마크**: 우상단 "Solo 분석" 배지 (반투명 봄 라벤더)
- **B 위치**: 점선 원 + 흐릿한 회색 + "✨ 초대하면 채워져요" 캡션
- **CTA 버튼**: 화면 하단 고정 (sticky bottom)
  - 1순위: "[상대방] 초대하기" (Primary)
  - 2순위: "결과 카톡 공유" (Secondary)

---

## 🧪 검증 시나리오

### 시나리오 1: 신규 사용자 5분 도달
```
1. 새 브라우저 시크릿 모드
2. / 진입
3. 시간 측정 시작
4. "지금 갈등을 정리하고 싶어요" 클릭
5. 게스트 가입 (닉네임만)
6. 온보딩 10문항 완료
7. Quick Describe 3단계 완료
8. AI 분석 대기
9. Solo 결과 페이지 도달
10. 시간 측정 종료

→ 목표: 5분 이내
→ 측정: TTFV (Time to First Value)
```

### 시나리오 2: Solo → Pair 업그레이드
```
1. Solo 결과 페이지에서 "[상대방] 초대" 클릭
2. 톤 선택 (부드럽게)
3. URL 복사
4. 새 브라우저로 URL 접속 (B 시뮬레이션)
5. B 가입 + 온보딩 + Quick Describe 입력
6. A 페이지 자동 새로고침 또는 SSE 알림
7. 페어 결과 페이지 표시 확인

→ 목표: 데이터 누락 없이 업그레이드
→ 측정: 페어 결과의 욕구 지도에 A, B 모두 표시
```

### 시나리오 3: 게스트 → 회원 전환
```
1. 게스트로 시작
2. Solo 결과 도달
3. "결과 저장하려면 가입하세요" CTA 클릭
4. 회원가입 폼 (이메일 + 비밀번호)
5. 가입 완료 시 게스트 데이터 → 회원 계정으로 마이그레이션
6. 결과 페이지 동일하게 표시

→ 목표: 게스트→회원 시 데이터 이전 정상
→ 측정: 게스트 토큰의 세션 → 회원 user_id로 owner 변경 확인
```

---

## ✅ Phase 1 완료 조건

- [ ] 신규 사용자 5분 이내 첫 결과 도달 가능
- [ ] Quick Describe 3단계 정상 동작
- [ ] AI 분석 중 화면 SSE 연동 정상
- [ ] Solo 결과 페이지 모든 컴포넌트 정상 표시
- [ ] Solo → Pair 업그레이드 정상 동작
- [ ] 분석 이벤트 트래킹 누락 없음
- [ ] 모바일/태블릿/데스크톱 반응형 동작
- [ ] 카톡 인앱 브라우저에서 정상 동작

---

**끝.**
