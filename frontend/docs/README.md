# 다시봄 (Again Spring) — 관계 회복 AI 중재자 프론트엔드 프로토타입

> **"다시 봄. 다시 바라봄."**  
> 싸운 두 사람 사이에서 AI 중재자가 양쪽의 입력을 중립적으로 처리해 관계 회복을 돕는 웹앱입니다.

---

## 🌸 서비스명에 대하여

**다시봄 (Again Spring)**의 이름에는 두 가지 의미가 담겨 있습니다:

1. **봄(Spring, 계절)** — 얼어붙은 관계가 다시 따뜻해지는 계절
2. **봄(See, 바라봄)** — 상대를 다시 바라보고, 나 자신을 다시 바라봄

이 중의적 의미를 유지하는 것이 브랜드의 핵심입니다.

---

## 📄 문서 구성

프로젝트 시작 전 다음 문서를 **순서대로** 읽으세요:

| 순서 | 문서 | 설명 |
|---|---|---|
| 1 | **`WORK_ORDER.md`** | 메인 작업지시서. Phase별 태스크 체크리스트 |
| 2 | **`MOCKUP_INTEGRATION.md`** | 목업 디자인 통합 가이드 (중요!) |
| 3 | **`CATEGORIES.md`** | 대/중/소분류 카테고리 전체 데이터 |
| 4 | **`ONBOARDING_MAPPING.md`** | 10문항 → 6스타일 매핑 로직 |
| 5 | **`SYSTEM_PROMPTS.md`** | Gottman + NVC LLM 프롬프트 (Mock에선 참고용) |
| 6 | **`RATIO_CALCULATION.md`** | 화해 기여도 계산 알고리즘 |
| 7 | **`FORBIDDEN_WORDS.md`** | 금지어 및 대체어 사전 |
| 8 | **`MOCK_SCENARIOS.md`** | Mock API 샘플 시나리오 |
| 9 | **`TERMS_OF_SERVICE.md`** | 이용약관 초안 |

---

## 🎯 이번 단계의 목표

**프론트엔드 프로토타입 완성** (실제 LLM 연동 없이 UI/UX 검증)

- ✅ Next.js 14 + TypeScript + Tailwind
- ✅ MSW 기반 Mock API
- ✅ 전체 플로우 구현 (가입 → 세션 → 6턴 중재 → 결과)
- ✅ Capacitor 모바일 확장 준비
- ✅ Claude Design 목업 통합 프로세스
- ❌ 실제 LLM 연동 (다음 단계)
- ❌ 실제 백엔드 (다음 단계)

---

## 🚀 시작하기

### 프로젝트 경로
```
/home/justant/Data/Again-Spring
```

### 1. 프로젝트 초기 설정

```bash
cd /home/justant/Data
mkdir -p Again-Spring
cd Again-Spring

# Next.js 프로젝트 생성
npx create-next-app@latest . --typescript --tailwind --app

# 이 작업지시서 문서들을 docs/ 폴더에 배치
mkdir -p docs design/mockups design/tokens
cp /path/to/*.md docs/
```

### 2. 의존성 설치

```bash
npm install zustand axios framer-motion lucide-react recharts react-hook-form zod @hookform/resolvers
npm install -D msw@latest
npx shadcn@latest init
```

### 3. 개발 서버 실행

```bash
npm run dev
```

---

## 📂 프로젝트 구조

```
Again-Spring/
├── docs/                   # 이 작업지시서 문서들
├── design/                 # 🎨 Claude Design 목업 파일
│   ├── mockups/            # 화면별 목업 폴더
│   └── tokens/             # 디자인 토큰
├── app/                    # Next.js App Router 페이지
├── components/             # 재사용 컴포넌트
├── lib/                    # 유틸, 타입, 상수, 스토어
├── mocks/                  # MSW Mock API
└── public/                 # 정적 에셋
```

자세한 구조는 `WORK_ORDER.md` 참조.

---

## 🎨 목업 디자인 통합

본 프로젝트의 UI는 **Claude Design**으로 별도 제작됩니다.  
목업은 비동기적으로 제공되며, Claude Code는 다음과 같이 동작합니다:

### 기본 플로우

1. 각 Phase 시작 전 `design/mockups/XX-XXX/` 폴더 확인
2. 목업 있으면 → 목업 기반 구현 + `// ✅ MOCKUP APPLIED` 주석
3. 목업 없으면 → 기본 디자인 구현 + `// ⚠️ MOCKUP PENDING` 주석

### 목업 추가 시 재작업 명령

```
"design/mockups/09-result/ 에 목업을 추가했어. 
해당 화면을 목업에 맞춰 재작업해줘."
```

자세한 내용은 **`MOCKUP_INTEGRATION.md`** 참조.

---

## ✅ 진행 체크리스트

Claude Code는 `WORK_ORDER.md`의 Phase별 체크박스를 순서대로 완료:

- [ ] Phase 1: 프로젝트 세팅
- [ ] Phase 2: 상수 및 타입 정의
- [ ] Phase 3: 랜딩 및 인증 화면
- [ ] Phase 4: 온보딩 10문항 테스트
- [ ] Phase 5: 세션 시작 플로우 (A측)
- [ ] Phase 6: B 참여 플로우
- [ ] Phase 7: 6턴 멀티턴 중재 UI
- [ ] Phase 8: 결과 리포트 화면 (시그니처)
- [ ] Phase 9: Solo 모드
- [ ] Phase 10: 세션 이력 및 프로필
- [ ] Phase 11: Mock API 구현
- [ ] Phase 12: 법적 리스크 가드
- [ ] Phase 13: 반응형 및 접근성
- [ ] Phase 14: 테스트 및 빌드

---

## 🎨 디자인 원칙

1. **앵커링 방지**: 상대방 원문은 양쪽 모두 입력 완료 전까지 공개 금지
2. **판결 금지**: "과실비율", "판사", "유죄" 등 법률 용어 절대 사용 금지 (→ FORBIDDEN_WORDS.md)
3. **차이 존중**: "다름"을 "잘못"으로 환원하지 않기
4. **긍정 프레이밍**: 모든 라벨은 긍정적 표현으로
5. **프라이버시**: 갈등 내용을 시각화에서 노출하지 않기
6. **안전 우선**: 위험 키워드 감지 시 즉시 전문 기관 연결

---

## 🧭 서비스 핵심 정체성

> **"싸운 후 누가 잘못인지 판결하는 AI"가 아니라,  
> "우리가 왜 자꾸 이 지점에서 부딪히는지 보여주는 AI"**

이 정체성을 지키면 프로덕트가 살고, 무너지면 경쟁 서비스의 아류가 됩니다.

---

## 💬 Claude Code 명령 패턴

### 초기 개발 시작
```
/home/justant/Data/Again-Spring 에서 docs/WORK_ORDER.md를 읽고
Phase 1부터 순서대로 진행해줘.
```

### 목업 반영 요청
```
design/mockups/09-result/ 에 목업을 추가했어.
해당 화면을 목업에 맞춰 재작업해줘.
```

### 특정 Phase 재검토
```
Phase 7 중재 UI 다시 확인하고 목업 있는지 보고 재작업해줘.
```

### 목업 현황 조회
```
다시봄 목업 반영 현황 보여줘.
```

---

## 🔜 다음 단계 (이 프로토타입 완료 후)

1. **백엔드 개발**: Spring Boot + MongoDB + MariaDB + Neo4j
2. **LLM 연동**: Anthropic Claude API 통합
3. **Gottman 지식 베이스 구축**: RAG 시스템
4. **모바일 앱 빌드**: Capacitor로 iOS/Android
5. **결제 시스템**: 구독 모델 구현
6. **상담사 제휴**: 전문가 연결 시스템

---

**끝. 이제 `WORK_ORDER.md`를 열어 Phase 1부터 시작하세요.**
