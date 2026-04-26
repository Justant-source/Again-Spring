# 카카오 바이럴 자산 — 6유형 캐릭터 + 카톡 OG 카드 + 공유 가이드

**버전**: v2.0
**대상**: Claude Code (개발 작업) + 디자이너 (일러스트 제작)
**연관 작업**: `REFINEMENT_WORK_ORDER.md` Phase 5

---

## 🎯 목표

**한국 시장에서 검증된 바이럴 패턴**(MBTI, SBTI, 16Personalities)을 활용해 다시봄을 카톡·인스타 공유 친화 서비스로 만들기.

핵심 전략:
1. **6유형 캐릭터화**: 파도형/산형/불꽃형/이파리형/달빛형/별빛형
2. **카카오톡 OG 카드 최적화**: 800x400px, 미리보기 노출
3. **공유 카드 생성기**: 추상화된 욕구 지도 + 6유형 캐릭터 + 한 줄 카피
4. **카카오톡 SDK 통합**: 한 클릭 공유

---

## 🎨 6유형 캐릭터 디자인 가이드

### 캐릭터 컨셉

각 유형을 **자연 모티프 + 동물·식물 캐릭터**로 형상화. MBTI 16유형이 동물 캐릭터로 인기를 얻은 패턴 차용.

### 6유형 캐릭터 사양

#### 🌊 파도형 (Wave Type)

- **컨셉**: 감정 표현이 풍부하고 즉각적
- **캐릭터**: 푸른 파도 위에서 춤추는 작은 돌고래 또는 해마
- **메인 컬러**: 봄 바다 블루 (#60A5FA)
- **표정**: 웃고 있는, 활기찬
- **소품**: 물방울, 파도 무늬
- **한 줄 카피**: "마음을 숨기지 않고, 있는 그대로 흘러가는 분"

#### 🏔️ 산형 (Mountain Type)

- **컨셉**: 차분하고 거리를 두고 생각
- **캐릭터**: 산 정상에 앉아있는 작은 곰 또는 부엉이
- **메인 컬러**: 산 갈색 (#78716C)
- **표정**: 평온한, 깊이 생각하는
- **소품**: 산봉우리, 안개
- **한 줄 카피**: "조용히 마음의 깊이를 다지는 분"

#### 🔥 불꽃형 (Flame Type)

- **컨셉**: 직설적이고 명확함을 선호
- **캐릭터**: 작은 모닥불 옆의 여우 또는 호랑이
- **메인 컬러**: 봄 노을 빨강 (#F87171)
- **표정**: 결연한, 명확한
- **소품**: 불꽃, 불씨
- **한 줄 카피**: "있는 그대로 솔직하게 말해주는 분"

#### 🌿 이파리형 (Leaf Type)

- **컨셉**: 조화와 공감을 중시
- **캐릭터**: 봄 잎사귀 위의 작은 나비 또는 토끼
- **메인 컬러**: 봄 새싹 초록 (#4ADE80)
- **표정**: 부드러운, 공감하는
- **소품**: 새싹, 잎사귀
- **한 줄 카피**: "다른 사람의 마음을 먼저 알아주는 분"

#### 🌙 달빛형 (Moon Type)

- **컨셉**: 말보다 분위기·행동으로 표현
- **캐릭터**: 달빛 아래 고양이 또는 사슴
- **메인 컬러**: 봄 라벤더 (#A78BFA)
- **표정**: 신비로운, 조용한
- **소품**: 초승달, 별
- **한 줄 카피**: "말 없이도 마음이 전해지는 분"

#### ⭐ 별빛형 (Star Type)

- **컨셉**: 논리와 이유를 중시
- **캐릭터**: 별을 보고 있는 작은 펭귄 또는 강아지
- **메인 컬러**: 봄 별빛 노랑 (#FBBF24)
- **표정**: 호기심 어린, 분석적
- **소품**: 별, 작은 책
- **한 줄 카피**: "이유를 찾고, 답을 정리하는 분"

### 디자인 사양

- **사이즈**: 메인 1024x1024px, 썸네일 256x256px
- **포맷**: SVG (벡터, 확대 시 깨짐 없음) + PNG (호환성)
- **스타일**: 미니멀, 부드러운 라인, 따뜻한 색감
- **배경**: 투명 또는 봄 그라디언트
- **표현**: 일러스트 스타일 (실사 X)

### 캐릭터 페어 매트릭스 (6×6 = 36조합)

각 유형 조합별 "궁합 카드" 만들기:

```
파도형 × 산형 → "물결과 바위처럼 다른 두 마음"
파도형 × 불꽃형 → "표현이 풍부한 두 마음"
파도형 × 이파리형 → "감정과 공감이 만나는 자리"
... (총 36조합)
```

---

## 📤 카카오톡 OG 카드

### OG 메타 태그 사양

```html
<!-- 카카오톡이 인식하는 OG 태그 -->
<meta property="og:title" content="다시봄 — 우리의 욕구 차이 지도" />
<meta property="og:description" content="🌊 파도형 × 🏔️ 산형 — 두 분 모두 봄을 다시" />
<meta property="og:image" content="https://againspring.app/og/share?type=result&style=wave-mountain" />
<meta property="og:image:width" content="800" />
<meta property="og:image:height" content="400" />
<meta property="og:url" content="https://againspring.app/result/{public_id}" />
<meta property="og:site_name" content="다시봄" />
<meta property="og:type" content="website" />

<!-- 카카오톡 전용 -->
<meta property="al:web:url" content="https://againspring.app/result/{public_id}" />
```

### OG 이미지 크기 권장

- **카카오톡 권장**: 800x400px (2:1 비율)
- **인스타그램 정사각**: 1080x1080px (백업)
- **트위터 카드**: 1200x630px (백업)

### 동적 OG 이미지 생성 API

#### `GET /api/og?type={type}&style={style}&user={user}`

쿼리 파라미터에 따라 동적 PNG 생성.

**Parameters**:
- `type`: `share-card` | `result-card` | `personality-card`
- `style`: 6유형 중 하나 (`wave`, `mountain`, ...)
- `user`: 사용자 닉네임 (선택, 표시용)

**Response**:
- Content-Type: `image/png`
- 800x400px PNG

### Next.js OG 이미지 생성

```typescript
// app/api/og/route.ts
import { ImageResponse } from 'next/og';

export const runtime = 'edge';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const type = searchParams.get('type') ?? 'share-card';
  const style = searchParams.get('style') ?? 'wave';
  const user = searchParams.get('user') ?? '나';
  
  const styleInfo = COMMUNICATION_STYLES[style];
  
  return new ImageResponse(
    (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          width: '100%',
          height: '100%',
          background: 'linear-gradient(135deg, #FAF9F6 0%, #E8F5E9 100%)',
          padding: '40px',
        }}
      >
        <div style={{ fontSize: 72, marginBottom: 20 }}>
          {styleInfo.emoji}
        </div>
        <div
          style={{
            fontSize: 36,
            fontWeight: 700,
            color: styleInfo.color,
            marginBottom: 10,
          }}
        >
          {user}님은 {styleInfo.label}이시군요
        </div>
        <div style={{ fontSize: 20, color: '#5F5E5A', marginBottom: 30 }}>
          {styleInfo.copy}
        </div>
        <div
          style={{
            fontSize: 16,
            color: '#7FB77E',
            fontWeight: 500,
          }}
        >
          🌸 다시봄 — 다시 봄, 다시 바라봄
        </div>
      </div>
    ),
    {
      width: 800,
      height: 400,
    }
  );
}
```

---

## 📱 공유 카드 생성기

### 컴포넌트 구조

```typescript
// components/result/ShareCard.tsx

interface ShareCardProps {
  reportId: string;
  userStyle: CommunicationStyle;
  partnerStyle?: CommunicationStyle;  // Solo는 undefined
  needsMapPosition: { a: { x, y }, b?: { x, y } };
  mode: 'solo' | 'pair';
}

export function ShareCard({
  reportId,
  userStyle,
  partnerStyle,
  needsMapPosition,
  mode
}: ShareCardProps) {
  const [generating, setGenerating] = useState(false);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  
  const handleGenerate = async () => {
    setGenerating(true);
    
    if (mode === 'pair') {
      // 페어용: 양쪽 캐릭터 + 욕구 지도
      const url = `/api/og?type=share-card&style=${userStyle}-${partnerStyle}&user=${user}`;
      setImageUrl(url);
    } else {
      // Solo용: 본인 캐릭터만
      const url = `/api/og?type=personality-card&style=${userStyle}&user=${user}`;
      setImageUrl(url);
    }
    
    setGenerating(false);
  };
  
  const handleKakaoShare = () => {
    if (typeof window === 'undefined' || !window.Kakao) return;
    
    window.Kakao.Share.sendDefault({
      objectType: 'feed',
      content: {
        title: '다시봄 — 우리의 욕구 차이 지도',
        description: getShareDescription(),
        imageUrl: imageUrl,
        link: {
          mobileWebUrl: `https://againspring.app/result/${reportId}`,
          webUrl: `https://againspring.app/result/${reportId}`,
        },
      },
      buttons: [
        {
          title: '나도 해보기',
          link: {
            mobileWebUrl: 'https://againspring.app',
            webUrl: 'https://againspring.app',
          },
        },
      ],
    });
  };
  
  // ... 다운로드 / URL 복사 등
}
```

### 공유 시 표시되는 카드 내용

#### Solo 카드

```
┌────────────────────────────────────┐
│                                    │
│           🌊                        │
│                                    │
│      [닉네임]님은 파도형이시군요     │
│                                    │
│   "마음을 숨기지 않고, 있는        │
│    그대로 흘러가는 분"              │
│                                    │
│   🌸 다시봄 — 나도 해보기 →        │
│                                    │
└────────────────────────────────────┘

(800x400px, 봄 그라디언트 배경)
```

#### Pair 카드

```
┌────────────────────────────────────┐
│                                    │
│      🌊         ×         🏔️         │
│   파도형               산형         │
│                                    │
│   "물결과 바위처럼 다른 두 마음"     │
│                                    │
│   거리감: 적당히 떨어짐 (3/5)        │
│                                    │
│   🌸 다시봄 — 우리도 해보기 →       │
│                                    │
└────────────────────────────────────┘

(800x400px, 두 색상 그라디언트)
```

**중요**: 갈등 내용은 **절대 표시 안 함**. 추상화된 캐릭터·유형·거리감만.

---

## 🔌 카카오톡 SDK 통합

### Kakao SDK 설치

```html
<!-- app/layout.tsx -->
<Script
  src="https://t1.kakaocdn.net/kakao_js_sdk/2.7.4/kakao.min.js"
  integrity="sha384-DKYJZ8NLiK8MN4/C5P2dtSmLQ4KwPaoqAfyA/DfmEc1VDxu4yyC7wy6K1Hs90nka"
  crossOrigin="anonymous"
  strategy="beforeInteractive"
/>
```

### 초기화

```typescript
// lib/kakao.ts
export function initKakao() {
  if (typeof window === 'undefined') return;
  if (!window.Kakao) return;
  
  if (!window.Kakao.isInitialized()) {
    window.Kakao.init(process.env.NEXT_PUBLIC_KAKAO_JS_KEY);
  }
}

// app/layout.tsx 또는 _app.tsx
useEffect(() => {
  initKakao();
}, []);
```

### 환경변수

```bash
# .env.local
NEXT_PUBLIC_KAKAO_JS_KEY=your_kakao_js_key
```

### Kakao Developers 설정

1. **앱 생성**: https://developers.kakao.com → 내 애플리케이션
2. **플랫폼 설정**: Web 플랫폼에 다시봄 도메인 추가
3. **JavaScript 키 복사** → 환경변수 입력
4. **카카오 로그인** 활성화 (선택, 향후 OAuth용)

---

## 📊 카톡 인앱 브라우저 호환성

### 카카오톡 인앱 브라우저 특성

- WebKit/Chromium 기반이지만 일부 기능 제한
- iOS Safari 7.0+ 수준 지원
- localStorage 작동, 일부 cookie 제한
- 외부 브라우저 자동 열기 가능

### 호환성 처리

```typescript
// lib/utils/userAgent.ts
export function isKakaoInApp(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /KAKAOTALK/i.test(navigator.userAgent);
}

export function openInExternalBrowser(url: string) {
  // 카톡 인앱이면 외부 브라우저 열기 시도
  if (isKakaoInApp()) {
    window.location.href = `kakaotalk://web/openExternal?url=${encodeURIComponent(url)}`;
  } else {
    window.open(url, '_blank');
  }
}
```

### 카톡 인앱 안내 배너

```typescript
// components/shared/KakaoInAppBanner.tsx
export function KakaoInAppBanner() {
  const [show, setShow] = useState(false);
  
  useEffect(() => {
    if (isKakaoInApp()) {
      setShow(true);
    }
  }, []);
  
  if (!show) return null;
  
  return (
    <div className="bg-yellow-100 border-b border-yellow-300 px-4 py-2 text-sm flex items-center justify-between">
      <span>📱 카톡 안에서 열렸어요. 더 편하게 이용하려면 외부 브라우저로 여세요.</span>
      <button
        onClick={() => openInExternalBrowser(window.location.href)}
        className="text-yellow-700 underline"
      >
        브라우저로 열기
      </button>
    </div>
  );
}
```

---

## 🎨 캐릭터 일러스트 작업 흐름

### Claude Code 단계 (이번 작업)

- [ ] **SVG 플레이스홀더 생성** (실제 일러스트 전 임시)
  - 6개 유형마다 단순 도형 + 이모지로 SVG 작성
  - 컬러 팔레트는 위 사양 따르기
  - 디자인 가이드의 메인 컬러 사용
- [ ] **컴포넌트 시스템**
  - `components/character/CharacterIcon.tsx` (유형 받아서 SVG 표시)
  - `components/character/CharacterCard.tsx` (전신 + 텍스트)
- [ ] **OG 이미지 동적 생성 API**
  - `/api/og` 라우트 구현
  - 6유형 전부 지원

### 디자이너 단계 (별도 진행)

- [ ] **6유형 일러스트 제작**
  - SVG/AI 파일 + PNG 백업
  - 스타일 가이드 준수
- [ ] **공유 카드 템플릿 디자인**
  - Solo / Pair 두 가지
  - 카카오톡 OG 사이즈 (800x400)
- [ ] **인스타 정사각 카드** (선택)
  - 1080x1080
- [ ] **캐릭터 페어 매트릭스** (장기 작업)
  - 36조합 카드 (선택, MVP 이후)

### 일러스트 도착 시 교체

- [ ] SVG 플레이스홀더 → 실제 일러스트 SVG로 교체
- [ ] `public/characters/` 디렉토리에 배치
- [ ] OG 이미지 생성 API에서 실제 일러스트 로드

---

## 📋 컴포넌트 명세

### 신규 컴포넌트

#### `components/character/CharacterIcon.tsx`
```typescript
interface CharacterIconProps {
  type: CommunicationStyle;
  size?: 'sm' | 'md' | 'lg' | 'xl';
}
```
- 유형별 SVG 표시
- 사이즈 옵션 (24/48/96/192px)

#### `components/character/CharacterCard.tsx`
```typescript
interface CharacterCardProps {
  type: CommunicationStyle;
  showCopy?: boolean;
  showStrengths?: boolean;
}
```
- 전신 일러스트 + 라벨 + 카피 + 강점/주의점

#### `components/result/ShareCard.tsx`
```typescript
interface ShareCardProps {
  reportId: string;
  userStyle: CommunicationStyle;
  partnerStyle?: CommunicationStyle;
  needsMap: NeedsMapData;
  mode: 'solo' | 'pair';
}
```
- 공유 이미지 생성 + 카카오 공유 + 다운로드 + URL 복사

#### `components/shared/KakaoInAppBanner.tsx`
- 카톡 인앱 브라우저 감지 + 외부 브라우저 열기 안내

#### `app/api/og/route.ts`
- 동적 OG 이미지 생성 (Next.js Edge Runtime)

---

## 📊 KPI 측정

### 이벤트 트래킹

| 이벤트 | 시점 | 속성 |
|---|---|---|
| `share_card_generated` | 카드 생성 클릭 | report_id, mode (solo/pair) |
| `share_to_kakao_clicked` | 카톡 공유 버튼 클릭 | report_id |
| `share_image_downloaded` | 이미지 다운로드 | report_id, format |
| `share_url_copied` | URL 복사 | report_id |
| `share_url_visited_via_kakao` | 카톡 링크 통해 유입 | original_report_id, source |
| `share_url_signup_converted` | 공유 링크로 신규 가입 | source |

### 목표 KPI

| 지표 | 목표 |
|---|---|
| 카톡 공유 클릭률 (결과 페이지 도달자 중) | 10%+ |
| 공유 링크 클릭률 (받은 사람 중) | 30%+ |
| 공유 링크 → 신규 가입 전환률 | 5%+ |
| K-factor (1명이 새로 데려오는 사용자 수) | 0.3+ |

---

## 🧪 검증 시나리오

### 시나리오 1: Pair 카드 카톡 공유
```
1. 페어 결과 페이지 → "카톡 공유" 클릭
2. ShareCard 생성 (파도형 × 산형 + 욕구 지도)
3. Kakao SDK 호출
4. 카톡 친구 선택 화면 표시
5. 메시지 전송
6. 받은 사람이 카톡에서 미리보기 카드 확인
7. 클릭 → 카톡 인앱 브라우저로 열림
8. 인앱 브라우저 호환성 배너 표시
9. 외부 브라우저 열기 또는 그대로 사용
10. 신규 가입 시 K-factor 측정
```

### 시나리오 2: Solo 카드 다운로드 후 인스타 공유
```
1. Solo 결과 페이지 → "이미지로 저장"
2. 1080x1080 PNG 다운로드
3. 사용자가 인스타그램 스토리에 직접 업로드
4. 인스타에서 다시봄 링크 표시
```

### 시나리오 3: OG 카드 미리보기 검증
```
1. 카카오톡 채팅창에 다시봄 결과 URL 붙여넣기
2. 자동 미리보기 생성 확인
3. 800x400px 이미지 + 제목 + 설명 표시
4. 클릭 시 정상 페이지 이동
```

### 시나리오 4: 카카오톡 자체 디버깅 도구
```
- https://developers.kakao.com/tool/debugger/sharing
- 다시봄 URL 입력 → 미리보기 시뮬레이션
- 캐시 삭제 가능
```

---

## ✅ Phase 5 완료 조건

- [ ] 6유형 캐릭터 SVG 플레이스홀더 작성
- [ ] CharacterIcon, CharacterCard 컴포넌트 정상 동작
- [ ] ShareCard 컴포넌트 정상 동작
- [ ] /api/og 동적 이미지 생성 API 정상 동작
- [ ] 카카오 SDK 통합 및 초기화 정상
- [ ] 카톡 공유 시 OG 카드 정상 미리보기 (실제 카톡에서 검증)
- [ ] 카톡 인앱 브라우저 호환성 배너 동작
- [ ] 6유형 모두에 대해 OG 이미지 생성 가능
- [ ] 공유 이미지에 갈등 내용 노출 0건
- [ ] KPI 측정 이벤트 트래킹

---

## 🚀 향후 확장

이번 단계 이후 추가 가능한 작업:

1. **36조합 페어 매트릭스**: 6×6 페어 카드 캐릭터 일러스트
2. **인스타 스토리 템플릿**: 1080x1920 세로형
3. **틱톡/유튜브 쇼츠 자동 생성**: 결과 영상화
4. **WhatsApp/LINE 공유**: 글로벌 확장 시
5. **카카오 채널 연동**: "다시봄 채널 추가하면 회복 가이드 메시지 받기"

---

**끝.**
