# 인스타그램 마케팅 콘텐츠 가이드

> 권위본: 이 파일. 채널별 전략 구현은 `InstagramContentGenerator.java` + `InstagramImageStrategy.java`.

---

## 1. 플랫폼 특성

| 항목 | 내용 |
|---|---|
| 소비 패턴 | 비주얼 우선. 텍스트는 카드 안에 녹여야 함 |
| 캡션 | 150자 이내 후크 + CTA. 본문 정보는 카드에 |
| 해시태그 | 5개, 캡션 하단 |
| 슬라이드 수 | 6~7장 |
| 이미지 규격 | 1080×1350px (4:5 세로형) |

---

## 2. 카드뉴스 슬라이드 구성

### Role 매핑표

| 순서 | Role | 제목 예시 | 본문 | 시각 힌트 |
|---|---|---|---|---|
| 1 | `COVER` | 메타포 한 줄 (30자) | 없음 또는 짧은 공감 문장 | 그라데이션 배경 + 큰 타이포 |
| 2 | `SCENE` | 갈등 장면 인용 | 실제 대화에서 추출한 말 한마디 (따옴표) | 채팅 버블 형태 |
| 3 | `FEELING` | 그 때의 감정 | 감정 단어 3개 + 설명 1문장 | 색상 팔레트 + 감정 단어 크게 |
| 4 | `NVC` | 관찰과 욕구 | NVC 관찰 1줄 + 욕구 1줄 | 두 줄 카드, 구분선 |
| 5 | `RATIO` | 화해 기여도 | 두 사람의 기여 설명 (수치 노출 금지) | 원형 또는 양방향 화살표 |
| 6 | `CTA` | 다시봄으로 | "지금 대화를 시작해보세요" | 다시봄 로고 + QR 또는 URL |
| 7 (optional) | `BONUS` | 추가 인사이트 | 리포트에서 추출한 핵심 문장 | 단색 배경 + 인용 형식 |

---

## 3. LLM 출력 JSON 스키마

```json
{
  "caption": "캡션 본문 (150자 이내, 후크+CTA만)",
  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "...", "..."],
  "slides": [
    {
      "role": "COVER",
      "title": "슬라이드 제목 (30자 이내)",
      "body": "본문 (60자 이내, 없으면 빈 문자열)",
      "visualHint": "렌더러에 전달할 시각 힌트 (gradient-warm | chat-bubble | emotion-palette | two-line | ratio-circle | cta-logo | quote-card)"
    }
  ]
}
```

- `slides` 배열은 6개 이상 7개 이하. 미충족 시 재시도 1회 후 `status=REVIEW`.
- `role` 값: `COVER | SCENE | FEELING | NVC | RATIO | CTA | BONUS` 중 하나.
- `visualHint`는 렌더러 레이아웃 결정에 사용.

---

## 4. 렌더링 흐름

```
InstagramImageStrategy.compose()
  ↓
ImageRenderClient.renderCardNews(slides, theme="warm", contentId)
  POST /render-card-news
  body: { slides: [{role, title, body, visualHint}], theme, contentId }
  response: { slides: [{filename:"card_{contentId}_{idx:02d}.png", base64:"..."}] }
  ↓
각 PNG를 /tmp/marketing-images/ 저장
  ↓
image_paths JSON:
[
  {"filename":"card_5_01.png","role":"COVER","slot":"SLIDE_1","alt":"커버","order":1},
  {"filename":"card_5_02.png","role":"SCENE","slot":"SLIDE_2","alt":"갈등 장면","order":2},
  ...
]
```

---

## 5. 디자인 토큰 (슬라이드 공통)

`marketing-renderer/src/styles/tokens.js` 단일 출처 참조.

| 토큰 | 값 |
|---|---|
| 배경(warm) | `#FBF3EC` |
| USER_A 버블 | `#F4A896` |
| AI 버블 | `#FFF8F0` |
| 텍스트 | `#5C4030` |
| 서브텍스트 | `#A08670` |
| 경계선 | `#EADFD0` |
| 헤더 그라데이션 | `#F4A896 → #A8C8B4` |
| 워터마크 | `again-spring.net` |

---

## 6. 캡션 작성 규칙

- **훅**: 메타포 또는 감정 공감 한 줄
- **CTA**: "다시봄에서 지금 대화를 시작해보세요"
- 본문 정보(NVC, 기여도 등)는 캡션에 쓰지 않음
- 결론·처방 금지 → 초대 형식만
- 금지어: `shared/docs/policies/forbidden-words.md` Level 1~3

---

## 7. few-shot 예시

**슬라이드 출력 예**

```json
{
  "caption": "비를 홀로 맞고 서 있는 기분 아시나요? 다시봄에서 지금 대화를 시작해보세요 🌸",
  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "#부부관계", "#AI중재"],
  "slides": [
    {"role":"COVER",   "title":"비를 홀로 맞고 서 있는 기분", "body":"", "visualHint":"gradient-warm"},
    {"role":"SCENE",   "title":"그 때 그 말이 상처였어요", "body":"\"넌 너무 예민해.\"", "visualHint":"chat-bubble"},
    {"role":"FEELING", "title":"그 순간의 감정", "body":"외로움 · 억울함 · 그리움", "visualHint":"emotion-palette"},
    {"role":"NVC",     "title":"관찰과 욕구", "body":"관찰: 대화가 끊겼어요\n욕구: 연결되고 싶었어요", "visualHint":"two-line"},
    {"role":"RATIO",   "title":"두 사람의 이야기", "body":"갈등은 한 사람만의 것이 아니에요", "visualHint":"ratio-circle"},
    {"role":"CTA",     "title":"다시봄과 함께", "body":"지금 대화를 시작해보세요", "visualHint":"cta-logo"}
  ]
}
```
