# Crisis Resource Modal — 위기 감지 및 리소스 연결

위기 키워드 분류와 사회적 자원 정보의 권위본은 `../../../shared/docs/policies/crisis-detection.md` 입니다. 이 문서는 FE에서 모달을 어떻게 띄우는지를 다룹니다.

---

## 개요

사용자가 입력한 텍스트에서 위기 관련 키워드가 감지되면, `CrisisResourceModal` 컴포넌트가 즉시 표시되어 전문 기관의 연락처와 지원 자원을 제공합니다.

---

## 위기 키워드 분류

### 즉시 감지 키워드 (세션 중단)

```
폭력: "때리", "폭행", "폭력", "구타"
성폭력: "강간", "성폭행"
자해: "죽고 싶", "자살", "자해", "목 매"
아동학대: "아이를 때", "아동학대"
```

감지 시:
- 세션 즉시 중단
- 풀스크린 `CrisisResourceModal` 표시
- 입력 필드 비활성화
- 핫라인 카드만 노출

---

## 핫라인 정보

### 주요 전국 핫라인

| 기관 | 번호 | 대상 | 운영 |
|---|---|---|---|
| 여성긴급전화 | 1366 | 가정폭력, 성폭력 | 24/7 |
| 생명의전화 | 1393 | 자살 위기, 정신 건강 | 24/7 |
| 경찰청 | 112 | 긴급 신고 | 24/7 |
| 119 구급차 | 119 | 의료 응급 | 24/7 |
| 청소년전화 | 1388 | 청소년 상담 | 24/7 |
| 학교폭력신고 | 1577-0199 | 따돌림, 폭행 | 24/7 |

### 카드 구성

각 핫라인은 다음 정보를 포함:

```tsx
{
  id: 'women_hotline',
  name: '여성긴급전화',
  number: '1366',
  category: ['family_violence', 'sexual_violence'],
  description: '가정폭력 및 성폭력 피해자를 위한 24시간 상담·보호 서비스',
  action: {
    label: '전화 연결',
    href: 'tel:1366'
  }
}
```

---

## 컴포넌트 동작

### 모달 렌더링

`components/shared/CrisisResourceModal.tsx`:

```tsx
export interface CrisisResourceModalProps {
  isOpen: boolean;
  onClose: () => void;
  detectedKeywords?: string[];
}

export function CrisisResourceModal({ isOpen, onClose, detectedKeywords }: CrisisResourceModalProps) {
  if (!isOpen) return null;
  
  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center">
      <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
        <h2 className="text-xl font-bold mb-4">지금 도움이 필요하신가요?</h2>
        <p className="text-gray-600 mb-6">
          다음 기관들이 24시간 무료로 상담해드립니다.
        </p>
        
        {/* 감지된 카테고리에 맞는 핫라인만 표시 */}
        {getRelevantHotlines(detectedKeywords).map((hotline) => (
          <HotlineCard key={hotline.id} hotline={hotline} />
        ))}
      </div>
    </div>
  );
}
```

### 호출 위치

1. **입력 필드** (`components/mediation/TurnInput.tsx`)
   ```tsx
   const [crisisKeywordsDetected, setCrisisKeywordsDetected] = useState(false);
   
   const handleInput = (text: string) => {
     const hasCrisis = checkCrisisKeywords(text);
     setCrisisKeywordsDetected(hasCrisis);
   };
   
   return (
     <>
       <textarea onChange={(e) => handleInput(e.target.value)} />
       <CrisisResourceModal 
         isOpen={crisisKeywordsDetected} 
         onClose={() => setCrisisKeywordsDetected(false)} 
       />
     </>
   );
   ```

2. **설명 입력** (`app/session/describe/page.tsx`)
   ```tsx
   const [crisisDetected, setCrisisDetected] = useState(false);
   
   const handleDescriptionChange = (text: string) => {
     const keywords = checkCrisisKeywords(text);
     setCrisisDetected(!!keywords);
   };
   ```

3. **Solo 모드** (`app/session/mediation/solo/page.tsx`)
   - 동일한 로직 적용

---

## 접근성 (Accessibility)

### 포커스 관리

모달이 열리면:
- 포커스를 첫 번째 행동 버튼(핫라인)으로 이동
- `role="dialog"` 속성 추가
- `aria-labelledby` / `aria-describedby` 지정

```tsx
<div
  role="dialog"
  aria-labelledby="crisis-title"
  aria-describedby="crisis-description"
  className="fixed inset-0 z-50"
>
  <h2 id="crisis-title">지금 도움이 필요하신가요?</h2>
  <p id="crisis-description">다음 기관들이 24시간 무료로 상담해드립니다.</p>
</div>
```

### ESC 키 동작

사용자가 ESC를 누르면 모달이 **닫혀서는 안 됩니다** (위기 상황에서 실수로 닫을 수 있음).

대신:
- "나중에" 버튼으로만 닫기 가능
- 또는 핫라인 클릭 후 자동 닫기

```tsx
useEffect(() => {
  const handleEscape = (e: KeyboardEvent) => {
    // ESC 무시 — 사용자가 의도적으로만 닫을 수 있음
    if (e.key === 'Escape') {
      e.preventDefault();
    }
  };
  
  if (isOpen) {
    document.addEventListener('keydown', handleEscape);
  }
  
  return () => {
    document.removeEventListener('keydown', handleEscape);
  };
}, [isOpen]);
```

---

## 감지 로직

### 클라이언트 검사 (`lib/utils/keywordGuard.ts`)

```typescript
export function checkCrisisKeywords(text: string): string[] {
  const crisisKeywords = [
    '때리', '폭행', '폭력', '구타',  // 폭력
    '강간', '성폭행',                 // 성폭력
    '죽고 싶', '자살', '자해', '목 매', // 자해
    '아이를 때', '아동학대'           // 아동학해
  ];
  
  const detected: string[] = [];
  crisisKeywords.forEach((keyword) => {
    if (text.includes(keyword)) {
      detected.push(keyword);
    }
  });
  
  return detected;
}
```

### 서버 검사 (BE)

BE의 `KeywordGuard.java` (Spring)도 동일한 규칙으로 검사하여, 클라이언트 우회 시에도 보호.

---

## 사용자 이동 경로

```
사용자 입력 (위기 키워드 포함)
    ↓
클라이언트: checkCrisisKeywords() 감지
    ↓
CrisisResourceModal 표시 (배경 블러, 포커스 트랩)
    ↓
┌─────────────────────────┐
│ 핫라인 클릭              │ (tel: 링크)
│  또는 "나중에" 버튼      │
└─────────────────────────┘
    ↓
모달 닫기
    ↓
입력 필드 계속 (선택적) 또는 세션 종료
```

---

## 핸드오프 데이터 구조

`lib/constants/crisisResources.ts`:

```typescript
export const CRISIS_RESOURCES = [
  {
    id: 'women_hotline',
    name: '여성긴급전화',
    number: '1366',
    description: '가정폭력 및 성폭력 피해자를 위한 24시간 상담·보호',
    categories: ['family_violence', 'sexual_violence'],
    tel: 'tel:1366'
  },
  // ... 다른 핫라인들
];
```

---

## 정책 업데이트

위기 키워드 또는 핫라인 정보 변경 시:

1. `../../../shared/docs/policies/crisis-detection.md` 업데이트
2. `lib/utils/keywordGuard.ts` 동기화
3. `lib/constants/crisisResources.ts` 동기화
4. BE의 `KeywordGuard.java` 동기화

