# X (Twitter) 마케팅 콘텐츠 가이드

> 권위본: 이 파일. 채널별 전략 구현은 `XContentGenerator.java` + `XImageStrategy.java`.

---

## 1. 플랫폼 특성

| 항목 | 내용 |
|---|---|
| 1회 발행 단위 | 3~5 트윗 스레드 |
| 트윗당 글자 수 | 270자 이내 (안전 마진 10자) |
| 이미지 슬롯 | 트윗당 최대 4장, 보통 1장만 사용 |
| 소비 패턴 | 스크롤 중 1~2초 안에 멈추게 해야 함 → 첫 트윗이 훅 |
| 해시태그 | 2~3개, 마지막 트윗에만 |

---

## 2. 콘텐츠 구성

### 스레드 흐름

```
트윗 1 (훅) — 관계 언어로 시작, 인용 카드 PNG 첨부
트윗 2 (공감) — 갈등 상황 메타포 + 감정 인정
트윗 3 (통찰) — NVC 관찰 또는 욕구 한 줄 요약
트윗 4 (희망) — 화해 가능성 제시 (optional)
트윗 5 (CTA) — 다시봄 사용 안내 + 해시태그 + (옵션) 채팅 스크린샷 PNG
```

### 스레드 흐름 (이미지 포함)

```
트윗 1 (훅) — 메타포 훅 카드 PNG 첨부 (METAPHOR_COVER, 1080×1080)
트윗 2 (공감) — 갈등 상황 메타포 + 감정 인정 + 인용 카드 PNG (QUOTE_CARD)
트윗 3 (통찰) — NVC 관찰 또는 욕구 한 줄 요약
트윗 4 (희망) — 화해 가능성 제시 (optional)
트윗 5 (CTA) — 다시봄 사용 안내 + 해시태그 + 채팅 스크린샷 PNG (optional)
```

### 이미지 첨부 규칙

| 슬롯 | Role | 위치 | 렌더러 엔드포인트 | 입력 |
|---|---|---|---|---|
| `TWEET_1` | `METAPHOR_COVER` | 트윗 1 (훅 이미지) | `POST /render-metaphor-card` | `svgFilename`, `hookText`=첫 트윗 첫 문장 |
| `TWEET_2` | `QUOTE_CARD` | 트윗 2 | `POST /render-quote` | `line1`=메타포, `line2`=감정 한 줄 |
| `TWEET_5` | `CHAT_PREVIEW` | 트윗 5 (optional) | `POST /render-chat` | 키 모먼트 3개 메시지 (`KeyMomentSelector`) |

---

## 3. LLM 출력 JSON 스키마

```json
{
  "tweets": [
    "트윗 본문 (270자 이내, 번호 없이 독립된 문장)",
    "...최대 5개"
  ],
  "quoteCard": {
    "line1": "메타포 한 줄 (30자 이내)",
    "line2": "감정 또는 핵심 인사이트 (40자 이내)",
    "attribution": "다시봄"
  }
}
```

- `tweets`: 3~5개. 각 항목이 독립된 트윗.
- `quoteCard`: 인용 카드용 텍스트. 이미지 렌더러가 소비.
- 마크다운 불허 (** _ # 등 금지).

### 파싱 실패 처리

LLM이 code fence 안에 JSON 반환 시 `extractFirstJsonBlock()` 유틸로 추출.  
파싱 실패 시 `status=REJECTED`, `safetyCheckJson`에 원인 기록.

---

## 4. 이미지 파일 명명 규칙

```
metaphor_cover_{contentId}.png  → 메타포 훅 카드 (1080×1080, 항상 order=1)
quote_{contentId}.png           → 인용 카드 (order=2)
chat_{contentId}.png            → 채팅 스크린샷 (optional, order=3)
```

image_paths JSON 메타:
```json
[
  {"filename":"metaphor_cover_5.png","role":"METAPHOR_COVER","slot":"TWEET_1","alt":"훅 텍스트","order":1},
  {"filename":"quote_5.png",         "role":"QUOTE_CARD",    "slot":"TWEET_2","alt":"메타포 인용 카드","order":2},
  {"filename":"chat_5.png",          "role":"CHAT_PREVIEW",  "slot":"TWEET_5","alt":"갈등 대화 미리보기","order":3}
]
```

---

## 5. 톤 앤 매너

- 판단하지 않고 감정을 있는 그대로 반영
- 1인칭 경험 언어 ("~하셨을 거예요", "~하셨겠죠")
- 메타포는 시뮬레이션 Report의 `metaphorDisplayName`에서 가져옴
- 결론·처방 금지 → 가능성 제시만
- 금지어: `shared/docs/policies/forbidden-words.md` Level 1~3 + `shared/docs/policies/marketing-copy.md` Level B

---

## 6. few-shot 예시

**입력 (메타포: "비를 홀로 맞고 서 있는 기분", NVC 욕구: "연결")**

```json
{
  "tweets": [
    "\"넌 너무 예민해.\" 그 말을 들을 때마다 내 감정이 틀린 것 같아졌을 거예요. 감정에는 옳고 그름이 없어요. 아직 서로에게 닿지 못한 것뿐이에요. 💙",
    "비를 홀로 맞고 서 있는 기분 — 그 외로움, 진심으로 공감해요. 오늘 차 안에서의 침묵도 사실은 \"내 마음을 봐줘\"라는 신호였을 거예요.",
    "두 사람 모두 연결을 원하고 있어요. 다만 그 언어가 아직 달랐을 뿐이에요.",
    "말이 막히고 감정이 엉켜있을 때, 다시봄 AI 갈등 중재 도구와 함께 내 감정의 언어를 찾아보세요. #다시봄 #부부관계"
  ],
  "quoteCard": {
    "line1": "비를 홀로 맞고 서 있는 기분",
    "line2": "그 외로움을 이제 혼자 안고 있지 않아도 돼요",
    "attribution": "다시봄"
  }
}
```
