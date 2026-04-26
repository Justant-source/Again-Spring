# 다시봄 v2 개선 작업지시서 (Refinement)

**버전**: v2.0
**대상**: Claude Code
**프로젝트 경로**: `/home/justant/Data/Again-Spring`
**작성 배경**: 심리학적 신뢰성·UX·바이럴성에 대한 외부 분석 결과를 바탕으로 한 1차 개선

---

## 📌 개선 배경

본 프로젝트의 4가지 관점 종합 분석 결과:

1. **심리학적 신뢰성**: Gottman·NVC AI 적용의 한계 + AI 가스라이팅·시코펀시 리스크
2. **UX 위험**: 11게이트 온보딩 → 정신건강 앱 평균 D30 retention 3-4% 대비 더 낮을 가능성
3. **사업성**: Paired식 1인 결제·Solo-First 구조 미적용 시 페어 가입 강제로 매출 0 위험
4. **바이럴성**: 16Personalities·SBTI 사례에서 검증된 캐릭터·라벨링·카톡 공유 자산 부재

→ 본 v2 개선은 위 리스크를 코드/UI 레벨에서 즉시 해결하기 위한 작업이다.

---

## 🎯 v2 핵심 변경사항

### ✅ 채택 (이번 작업 범위)

| 분류 | 내용 |
|---|---|
| **UX** | Solo-First 재설계 (5분 안에 첫 결과 도달) |
| **UX** | 온보딩 11게이트 → 5게이트 이내 압축 |
| **온보딩** | 10문항 필수 + MBTI 수동(슬라이더 비율) + 60문항 정밀(선택) |
| **결과** | "관계 온도 35-38도" 게이지 **완전 제거** |
| **결과** | "Four Horsemen 탐지 결과" UI 표시 **제거** (내부 점수산정엔 유지) |
| **결과** | 화해 기여도 (70:30/100:0) **유지 + 법적 안내문구 강화** |
| **결과** | 결과 카드 = 은유 카드 3장 중심 |
| **Retention** | 결과 후 1주일 Repair Attempt drip 캠페인 |
| **바이럴** | 카카오톡 OG 카드 + 6유형 캐릭터 일러스트 |
| **반응형** | PC/태블릿 보조 뷰 (30대 이상 사용자용) |
| **법적** | 안내 문구·면책 강화 |

### ❌ 폐기

- 관계 온도 (35.0~38.0°C 게이지) — 화면·DB 컬럼·LLM 출력 모두 제거
- Four Horsemen 탐지 결과 사용자 노출 — UI에서만 제거 (내부 분석은 유지)

### ⏸ 보류 (이번 작업 범위 아님)

- 결제/구독 모델 구현
- B2B/EAP/상담사 제휴 채널
- Gottman Knowledge RAG (Phase 3로)

---

## 📂 관련 문서

이 작업지시서와 함께 작업할 6개 문서:

| 문서 | 역할 |
|---|---|
| **`REFINEMENT_WORK_ORDER.md`** | 이 파일. 메인 체크리스트 |
| **`SOLO_FIRST_REDESIGN.md`** | Solo-First 5게이트 압축 플로우 상세 설계 |
| **`RESULT_CARDS_REDESIGN.md`** | 은유 카드 + 화해 기여도 안내 + 폐기 항목 |
| **`ONBOARDING_V2.md`** | 10문항 + MBTI 슬라이더 + 60문항 정밀 테스트 |
| **`REPAIR_DRIP_CAMPAIGN.md`** | 결과 후 1주일 follow-up 시퀀스 |
| **`KAKAO_VIRAL_ASSETS.md`** | 6유형 캐릭터 + 카톡 OG 카드 + 공유 가이드 |

---

## 📋 Phase별 작업 체크리스트

각 Phase는 독립적으로 작업 가능하지만, **Phase 0 (폐기 작업)을 가장 먼저** 수행해야 다른 Phase의 코드 정합성이 유지됩니다.

### Phase 0: 폐기 작업 (반드시 먼저)

#### 0-1. 관계 온도 완전 제거

- [ ] **DB 스키마 수정**
  - `reports` 테이블에서 `temperature` 컬럼 제거
  - `users.temperatureHistory` 컬럼 제거
  - `user_relationships.average_temperature` 컬럼 제거
  - Flyway 마이그레이션 스크립트 작성: `V_XX__remove_temperature.sql`
  - 기존 데이터는 백업 후 컬럼 DROP

- [ ] **백엔드 도메인 수정**
  - `Report.java` 엔티티에서 `temperature` 필드 제거
  - `User.java`의 `temperatureHistory` 제거
  - `ReportService.java`의 온도 계산 로직 제거
  - `UserService.java`의 온도 이력 업데이트 로직 제거

- [ ] **API 응답 DTO 수정**
  - `ReportResponse`에서 `temperature` 필드 제거
  - `SessionListResponse`의 `temperature` 필드 제거
  - `RelationshipResponse`의 `averageTemperature` 제거
  - OpenAPI 스펙 자동 갱신 확인

- [ ] **LLM 프롬프트 수정**
  - `shared/prompts/` 내 모든 프롬프트에서 "관계 온도", "temperature" 언급 제거
  - 리포트 생성 프롬프트의 출력 JSON 스키마에서 temperature 제거
  - `MOCK_SCENARIOS.md`의 모든 샘플 응답에서 temperature 제거

- [ ] **프론트엔드 컴포넌트 제거**
  - `components/result/Temperature.tsx` 파일 삭제
  - `components/result/TemperatureGauge.tsx` 파일 삭제 (있는 경우)
  - 모든 Result 페이지에서 Temperature 컴포넌트 import 제거
  - 관계 온도 추이 그래프 (`history/page.tsx`) 제거
  - `lib/types/report.ts`에서 temperature 타입 제거

- [ ] **검색 검증**
  ```bash
  # 다음 명령어로 잔존 코드 확인
  grep -r "temperature" frontend/ backend/ shared/ --include="*.ts" --include="*.tsx" --include="*.java" --include="*.md"
  grep -r "관계 온도" frontend/ backend/ shared/
  grep -r "관계온도" frontend/ backend/ shared/
  # 모두 0건이 나와야 함 (단, 신체 체온/날씨 등 무관 컨텍스트 제외)
  ```

#### 0-2. Four Horsemen UI 표시 제거 (내부 분석 유지)

- [ ] **프론트엔드 컴포넌트 제거**
  - `components/result/FourHorsemen.tsx` 파일 삭제
  - 모든 Result 페이지에서 FourHorsemen 컴포넌트 import 제거
  - 결과 리포트 화면에서 Four Horsemen 섹션 완전 비표시

- [ ] **API 응답 분리**
  - 외부 응답 DTO에서 `fourHorsemen` 필드 제거
  - **내부 도메인 객체에는 유지** (점수 산정에 사용)
  - `ReportResponse` (외부용)와 `Report` (내부용) 분리
  - 관리자 API에서만 Four Horsemen 결과 조회 가능 (디버깅·QA용)

- [ ] **DB는 유지**
  - `reports.four_horsemen` JSON 컬럼 그대로 유지
  - 화해 기여도 계산 로직(`RatioEnforcer.java`)에서 계속 활용

- [ ] **LLM 프롬프트는 유지**
  - 시스템 프롬프트에서 Four Horsemen 탐지 지시는 그대로
  - 단, 출력 시 사용자에게 직접 보여줄 메시지에는 "비판/방어/경멸/담쌓기" 같은 임상 용어 사용 금지

- [ ] **검색 검증**
  ```bash
  # 프론트엔드에서만 0건이어야 함
  grep -r "FourHorsemen\|four_horsemen\|four-horsemen" frontend/
  grep -r "비판형\|방어형\|경멸\|담쌓기" frontend/
  ```

### Phase 1: Solo-First 재설계 (`SOLO_FIRST_REDESIGN.md` 참조)

- [ ] **Task 1.1** 진입 플로우 재설계
  - 신규 사용자: `/` → `/onboarding/quick` (3문항) → `/session/quick-describe` → 결과
  - 기존 5게이트 이내 도달
- [ ] **Task 1.2** Quick Describe 화면 신설
  - 카테고리 선택 → 상황 한 줄 설명 (300자 이내)
  - 페어 초대 옵션은 부수적으로 표시
- [ ] **Task 1.3** Solo 결과 화면 강화
  - 은유 카드 2-3장 (페어 결과 대비 축소판)
  - 하단 CTA: "더 정확한 분석을 받으려면 상대방 초대"
- [ ] **Task 1.4** 페어 업그레이드 동선
  - Solo 결과 페이지에서 "초대 링크 생성" 버튼 → 기존 페어 플로우 재활용
  - 상대방 참여 완료 시 Solo 결과 → 페어 결과로 자동 업그레이드
- [ ] **Task 1.5** 분석 이벤트 트래킹
  - 게이트별 이탈 측정 위해 `solo_started`, `solo_completed`, `solo_to_pair_invite`, `pair_completed` 이벤트 발생

### Phase 2: 결과 카드 재설계 (`RESULT_CARDS_REDESIGN.md` 참조)

- [ ] **Task 2.1** 결과 페이지 레이아웃 재구성
  - 기존: [욕구 차이 지도] → [관계 온도] → [화해 기여도] → [Four Horsemen] → [NVC] → [Repair]
  - 신규: [욕구 차이 지도] → [은유 카드 3장] → [화해 기여도 + 안내문구] → [NVC] → [Repair]

- [ ] **Task 2.2** 은유 카드 컴포넌트 신설
  - `components/result/MetaphorCards.tsx` 생성
  - 3장 카드: "두 분의 욕구", "함께 자라는 길", "다음 한 걸음"
  - 각 카드에 봄 모티프 일러스트
  - LLM이 카드별 텍스트 생성 (자세한 형식은 `RESULT_CARDS_REDESIGN.md`)

- [ ] **Task 2.3** 화해 기여도 컴포넌트 강화
  - 기존 `ContributionRatio.tsx` 유지
  - **법적 안내문구 박스 신설** (필수 표시)
    ```
    💡 이 수치는 두 분의 회복 시작점을 부드럽게 안내하기 위한 참고용이에요.
       법적 판단이나 책임 비율과는 무관합니다.
       AI의 분석에는 한계가 있으니, 깊은 갈등은 전문 상담을 권해드려요.
    ```
  - 폰트 크기는 본문보다 작게(12px) but 확실히 보이도록
  - 시각적으로 분리 (테두리 + 옅은 배경)

- [ ] **Task 2.4** 욕구 차이 지도 보존 + 라벨 강화
  - 좌표 점 옆에 5단계 거리 라벨 표시 ("매우 가까움/가까움/보통/떨어짐/매우 떨어짐")
  - LLM이 거리 자동 계산하여 라벨 부여

### Phase 3: 온보딩 v2 (`ONBOARDING_V2.md` 참조)

- [ ] **Task 3.1** 기존 10문항 유지 + 필수화
  - `app/(onboarding)/onboarding/page.tsx`에서 10문항 → 6스타일 매핑 그대로
  - 회원가입 직후 필수 통과
- [ ] **Task 3.2** MBTI 입력 화면 신설
  - `app/(onboarding)/mbti/page.tsx` 생성
  - 4축(E↔I, S↔N, T↔F, J↔P) 슬라이더 UI
  - 각 슬라이더는 0-100 비율 (0=완전 E, 100=완전 I)
  - "잘 모르겠으면 60문항 정밀 테스트로 가기" 버튼
- [ ] **Task 3.3** 60문항 정밀 테스트 화면 신설
  - `app/(onboarding)/mbti/full/page.tsx`
  - 60문항 (각 4축당 15문항)
  - 5점 리커트 척도
  - 자동 비율 산출 후 슬라이더에 결과 반영
  - 사용자가 결과 수정 가능
- [ ] **Task 3.4** MBTI 데이터를 LLM 컨텍스트에 주입
  - `users.mbti_profile` JSON 컬럼 신설 (`{e_i: 35, s_n: 70, t_f: 25, j_p: 60}`)
  - LLM 프롬프트에 "이 사용자는 약간 외향(E), 강한 직관(N), 약간 사고(T), 약간 인식(P)" 식으로 주입
  - 결과 카드 LLM이 MBTI 비율 반영해 더 개인화된 메시지 생성

- [ ] **Task 3.5** 온보딩 스킵·재시험 옵션
  - MBTI 입력은 선택, 10문항만 통과해도 다음 단계 진행 가능
  - 프로필 화면에서 언제든 MBTI 재입력·재시험 가능

### Phase 4: Repair Drip 캠페인 (`REPAIR_DRIP_CAMPAIGN.md` 참조)

- [ ] **Task 4.1** Drip 캠페인 백엔드 작성
  - `RepairDripScheduler.java` 생성
  - 결과 생성 후 1일/3일/7일 후 알림 트리거
  - 카톡/이메일 채널 (이번 단계는 이메일만, 카톡은 향후)
- [ ] **Task 4.2** Drip 메시지 템플릿
  - 1일 후: "어제의 대화, 어떻게 풀어가고 계세요?"
  - 3일 후: "오늘 저녁 7시, 다음 한 문장만 보내보세요: '...'"
  - 7일 후: "한 주가 지났어요. 다시 한 번 점검해볼까요?"
- [ ] **Task 4.3** Opt-out 기능
  - 사용자 프로필에서 알림 수신 여부 설정
  - 모든 알림 메일 하단에 수신 거부 링크
- [ ] **Task 4.4** Drip 효과 측정
  - 알림 발송 → 클릭 → 재방문 → 새 세션 시작 퍼널 측정

### Phase 5: 카카오 바이럴 자산 (`KAKAO_VIRAL_ASSETS.md` 참조)

- [ ] **Task 5.1** 6유형 캐릭터 일러스트 디자인 가이드 작성
  - 파도형/산형/불꽃형/이파리형/달빛형/별빛형
  - 각 유형 색상·모티프·표정 가이드
  - **실제 일러스트 제작은 디자인 단계** (Claude Code는 SVG 플레이스홀더만)
- [ ] **Task 5.2** 카카오톡 OG 메타 태그 설정
  - 800x400px 권장 사이즈
  - `app/layout.tsx`의 OG 메타 태그 설정
  - 동적 OG 이미지 생성 (`/api/og?type=share-card&style=wave`)
- [ ] **Task 5.3** 결과 공유 카드 생성기
  - `components/result/ShareCard.tsx` (기존 ShareImage.tsx 확장)
  - HTML2Canvas 또는 OG 이미지 API로 PNG 생성
  - 갈등 내용 비공개, 6유형 + 추상화된 욕구 지도만 표시
- [ ] **Task 5.4** 카카오톡 공유 SDK 통합
  - Kakao JavaScript SDK 설치
  - 공유 시 자동 카카오톡 채널로 전달
  - 카톡 인앱 브라우저에서 다시 열렸을 때 페이지 정상 표시

### Phase 6: 반응형 보조 뷰

- [ ] **Task 6.1** 태블릿 (768px ~ 1023px) 레이아웃
  - 결과 페이지: 욕구 지도 좌측, 카드 우측 2열
  - 온보딩: 한 화면에 2-3문항 동시 표시
- [ ] **Task 6.2** 데스크톱 (1024px+) 레이아웃
  - 최대 너비 1200px 컨테이너
  - 사이드바 네비게이션 추가 (역사·프로필·도움말)
- [ ] **Task 6.3** 카카오 인앱 브라우저 호환성
  - User-Agent 감지 → 카톡 인앱 시 외부 브라우저 열기 안내 배너
  - 또는 카톡 인앱에서도 정상 동작 검증
- [ ] **Task 6.4** 폰트 크기 접근성
  - 30대+ 사용자 위해 최소 폰트 14px (모바일) / 15px (데스크톱)
  - 본문 line-height 1.6 이상

### Phase 7: 법적 안내문구 강화

- [ ] **Task 7.1** 화해 기여도 옆 안내문구 (Phase 2-3과 연계)
- [ ] **Task 7.2** 결과 페이지 하단 면책 문구 강화
  ```
  본 서비스는 AI를 활용한 대화 정리 도구로,
  심리상담·법률자문·의료진단을 대체하지 않습니다.
  AI 분석에는 한계가 있으며, 깊은 갈등은 전문가 상담을 권해드려요.
  
  위급 상황: 1366 (여성긴급전화) | 1393 (자살예방) | 1577-0199 (정신건강위기)
  ```
- [ ] **Task 7.3** 회원가입 시 동의 항목 강화
  - "AI 분석 결과는 법적 판단·과실비율 산정과 무관합니다" 동의
  - "위기 상황에서는 본 서비스를 사용하지 않고 전문기관에 연락" 동의
- [ ] **Task 7.4** 화해 기여도 산정 시 LLM 프롬프트 강화
  - "수치는 어디까지나 회복 시작점 안내용이며, 법적 책임이나 도덕적 판단이 아님을 결과 텍스트에 반영" 지시
  - 차이형 70:30 클립 + 사실형 100:0 가능 정책 유지
  - Power Imbalance 시그널 감지 시 화해 기여도 자동 비활성화
    - 통제·고립·위협·신체 폭력·금전 통제 키워드 감지
    - 감지 시 화해 기여도 표시 안 하고 Crisis Resource로 분기

---

## 🔍 검증 시나리오

### 시나리오 1: 신규 Solo 사용자 첫 사용

```
1. / 진입 → "지금 갈등을 정리해보고 싶다면" CTA 클릭
2. 회원가입 (또는 게스트)
3. 10문항 경향성 테스트 (90초)
4. 갈등 카테고리 선택 → 한 줄 설명 입력 (1분)
5. 결과 화면 도달 (Solo 모드, 워터마크 표시)
   - 욕구 차이 지도 (B 위치 비어있음 + "초대하면 채워져요")
   - 은유 카드 3장
   - 화해 기여도 표시 안 함 + "상대방 참여 시 안내" 메시지
   - NVC 스크립트 (A 입장만)
   - Repair 제안 1-2개
6. CTA: "[B 이름] 초대하기" 또는 "결과 카드 카톡 공유"

→ 5게이트 이내, 5분 이내 첫 결과 도달
```

### 시나리오 2: 기존 Solo 사용자 → 페어 업그레이드

```
1. Solo 결과 페이지에서 "상대방 초대" 클릭
2. 초대 링크 생성 → 카톡 공유
3. 상대방 참여 완료 시
4. 기존 사용자에게 "결과가 업그레이드 됐어요" 알림
5. 페어 결과 페이지 표시
   - 욕구 차이 지도 (A, B 모두 표시)
   - 은유 카드 3장 (양쪽 정보 종합)
   - 화해 기여도 + 법적 안내문구
   - NVC 양방향 스크립트
   - Repair 제안 3개
```

### 시나리오 3: Power Imbalance 감지

```
1. 사용자 입력에 "맞았어요", "통제하려고", "협박" 등 포함
2. 즉시 화해 기여도 산정 비활성화
3. Crisis Resource 모달 표시
4. 사용자가 "계속 진행" 선택 시에도 화해 기여도 섹션 비표시
5. 결과 카드 하단에 전문기관 연락처 강조
```

### 시나리오 4: 카톡 공유 흐름

```
1. 결과 페이지에서 "카톡 공유" 버튼 클릭
2. ShareCard 생성 (욕구 지도 + 6유형 + 카피)
3. Kakao SDK로 공유
4. 카톡으로 전송 시 OG 카드 미리보기 표시
5. 친구가 링크 클릭 → 카톡 인앱 브라우저
6. "다시봄" 랜딩 페이지 정상 표시
7. 외부 브라우저 열기 배너 표시
```

---

## 📊 KPI 측정

### Phase 1 (Solo-First) 효과 측정
- 첫 결과 도달 시간 (Time to First Value, TTFV) — 목표 5분 이내
- 신규 사용자 D1 retention — 목표 30%+
- Solo → Pair 업그레이드율 — 목표 15%+

### Phase 4 (Drip) 효과 측정
- 1일 후 알림 클릭률 — 목표 25%+
- 7일 내 재방문율 — 목표 20%+
- 두 번째 세션 생성률 — 목표 10%+

### Phase 5 (바이럴) 효과 측정
- 카톡 공유 클릭률 — 목표 10%+
- 공유로 유입된 신규 가입률 (K-factor) — 목표 0.3+

---

## 🚨 Claude Code 작업 원칙

1. **Phase 0 폐기 작업을 가장 먼저** — 다른 Phase 코드 정합성 확보
2. **DB 마이그레이션은 Flyway 스크립트로 관리** — 롤백 가능하게
3. **API 변경 시 OpenAPI 스펙 갱신** — FE/BE 타입 동기화
4. **모든 신규 컴포넌트는 모바일/태블릿/데스크톱 3가지 뷰포트 테스트**
5. **법적 안내문구는 절대 누락 금지** — 화해 기여도 표시 시 필수
6. **Power Imbalance 시그널 감지 로직은 후처리 필터로 강제** — LLM 프롬프트만 믿지 말 것
7. **카톡 인앱 브라우저 호환성 매 화면 검증**

---

## ✅ 완료 조건 (Definition of Done)

### Phase 0
- [ ] "관계 온도", "Four Horsemen 탐지 결과" 단어가 사용자에게 노출되는 화면 0건
- [ ] DB 마이그레이션 완료, 기존 데이터 백업
- [ ] 검색 명령어로 잔존 코드 0건 확인

### Phase 1
- [ ] 신규 사용자가 5분 이내에 첫 결과 도달 가능
- [ ] Solo → Pair 업그레이드 시 데이터 누락 없음
- [ ] 5게이트 이내 첫 결과 도달

### Phase 2
- [ ] 은유 카드 3장 LLM 응답 정상 생성
- [ ] 화해 기여도 옆 법적 안내문구 모든 결과 페이지에 표시
- [ ] 욕구 차이 지도 5단계 거리 라벨 표시

### Phase 3
- [ ] MBTI 4축 슬라이더 정상 동작
- [ ] 60문항 정밀 테스트 결과가 슬라이더에 자동 반영
- [ ] LLM 프롬프트에 MBTI 컨텍스트 주입 확인

### Phase 4
- [ ] 1일/3일/7일 후 자동 이메일 발송 동작
- [ ] Opt-out 정상 동작

### Phase 5
- [ ] 카톡 공유 시 OG 카드 정상 미리보기
- [ ] 6유형별 SVG 플레이스홀더 표시 (실제 일러스트는 디자인 단계)
- [ ] 카톡 인앱 브라우저에서 페이지 정상 동작

### Phase 6
- [ ] 768px, 1024px, 1440px 뷰포트에서 모든 페이지 정상 표시
- [ ] 폰트 가독성 30대+ 사용자 기준 충족

### Phase 7
- [ ] 모든 결과 페이지 하단 면책 문구 표시
- [ ] 회원가입 시 강화된 동의 항목 표시
- [ ] Power Imbalance 시그널 감지 → 화해 기여도 자동 비활성화 동작

---

## 📞 Claude Code 명령 패턴

### 전체 v2 시작
```
/home/justant/Data/Again-Spring 에서
shared/docs/REFINEMENT_WORK_ORDER.md 읽고 Phase 0부터 시작해줘.
Phase 0 폐기 작업 완료 후 보고해줘.
```

### 특정 Phase 집중
```
shared/docs/SOLO_FIRST_REDESIGN.md 기반으로 
Phase 1 Solo-First 재설계를 완전히 구현해줘.
기존 페어 플로우는 유지하면서 진입 동선만 바꿔줘.
```

### 검증 시나리오 실행
```
시나리오 3 (Power Imbalance 감지) 수동 테스트해줘.
"맞았어요"라는 입력으로 세션 시작 후 동작 확인.
```

---

**끝. Phase 0부터 시작하세요.**
