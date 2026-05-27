# 네이버 블로그 마케팅 콘텐츠 가이드

> 권위본: 이 파일. 채널별 전략 구현은 `NaverBlogContentGenerator.java` + `NaverImageStrategy.java`.

---

## 1. 플랫폼 특성

| 항목 | 내용 |
|---|---|
| 글 길이 | 800~1,200자 마크다운 |
| SEO 핵심 | 제목에 키워드 1개 + 본문 3회 자연 반복 |
| 이미지 | 3개 인라인 삽입 (썸네일·중간 다이어그램·마무리 카드) |
| 소비 패턴 | 검색 → 클릭 → 스크롤. 글 가독성이 핵심 |
| 해시태그 | 5~8개, 포스트 하단 |

---

## 2. 콘텐츠 구성 (5-Box)

| Box | 제목 예시 | 내용 | 이미지 슬롯 |
|---|---|---|---|
| 1. 문제 제시 | "왜 이렇게 말이 안 통할까요?" | 공감 가는 갈등 상황 묘사 + SEO 키워드 삽입 | — |
| 2. 감정 단어 | "그 순간 어떤 감정이었을까요" | 감정 3~5개 + NVC 관찰 1줄 | — |
| 3. 사용 장면 | "다시봄 AI와 대화하다" | 채팅 장면 묘사 + 마커 `<!-- IMG:chat-preview -->` | `/render-chat` |
| 4. 리포트 시각화 | "두 사람의 이야기를 정리하면" | NeedsMap/기여도 설명 + 마커 `<!-- IMG:report-needs-map -->` | `/render-report-summary` |
| 5. CTA | "지금 시작하는 한 마디" | 인용 카드 + 마커 `<!-- IMG:quote-card -->` + 다시봄 링크 | `/render-quote` |

---

## 3. LLM 출력 JSON 스키마

```json
{
  "markdown": "...(마크다운 본문, 800~1200자, 슬롯 마커 포함)...",
  "imageSlots": [
    {"slot": "<!-- IMG:chat-preview -->",     "kind": "chat",          "quoteText": null},
    {"slot": "<!-- IMG:report-needs-map -->", "kind": "report-needs",  "quoteText": null},
    {"slot": "<!-- IMG:quote-card -->",       "kind": "quote",         "quoteText": "메타포 한 줄 (30자 이내)"}
  ],
  "hashtags": ["#다시봄", "#부부갈등", "#관계회복", "#AI갈등중재", "#감정정리"]
}
```

- `markdown`: 마커 `<!-- IMG:xxx -->` 가 포함된 마크다운. 마커는 빈 줄 사이에 위치.
- `imageSlots`: 마커와 1:1 대응. `NaverImageStrategy`가 이 목록을 순회하며 렌더러 호출 후 마커를 `![alt](filename)` 형태로 치환.
- `kind`: `chat` | `report-needs` | `report-ratio` | `report-combined` | `quote`.
- `quoteText`: `kind=quote`일 때만 사용 (인용 카드 line1).

---

## 4. 렌더링 흐름

```
NaverImageStrategy.compose()
  ↓
imageSlots[] 순회:
  kind=chat         → renderChatPreview(keyMoments)    → POST /render-chat
  kind=report-needs → renderReportSummary(mode=needs)  → POST /render-report-summary
  kind=report-ratio → renderReportSummary(mode=ratio)  → POST /render-report-summary
  kind=quote        → renderQuote(quoteText, ...)      → POST /render-quote
  ↓
각 PNG를 /tmp/marketing-images/ 저장
  → 파일명: naver_{contentId}_{slot_idx:02d}_{kind}.png
  ↓
markdown에서 마커를 ![alt](filename) 으로 치환
  ↓
image_paths JSON:
[
  {"filename":"naver_5_01_chat.png",   "role":"CHAT_PREVIEW",    "slot":"IMG:chat-preview",     "alt":"AI와의 대화 장면","order":1},
  {"filename":"naver_5_02_needs.png",  "role":"REPORT_NEEDS",    "slot":"IMG:report-needs-map", "alt":"NeedsMap 다이어그램","order":2},
  {"filename":"naver_5_03_quote.png",  "role":"QUOTE_CARD",      "slot":"IMG:quote-card",       "alt":"메타포 인용 카드","order":3}
]
```

---

## 5. SEO 가이드라인

- 제목(H1): `[키워드] + 다시봄 | 서비스명 불필요`
- 예: "부부 갈등 대화법, 다시봄 AI로 시작하는 방법"
- 본문 내 키워드 자연 반복 3회 (과도한 키워드 스터핑 금지)
- 소제목(H2) 2~3개로 섹션 분리
- 메타 description = 첫 문단 2문장 (LLM이 자동 생성)

---

## 6. 톤 앤 매너

- 정보성 + 감성. 읽는 사람이 "이게 내 이야기네"라고 느껴야 함
- 전문 용어 최소화, 일상 언어로 풀어쓰기
- 리포트 수치(기여도 %)는 절대 노출 금지
- 결론·처방 금지. "이런 경우가 있어요 → 다시봄에서 정리해볼 수 있어요" 패턴
- 금지어: `shared/docs/policies/forbidden-words.md` Level 1~3 + `shared/docs/policies/marketing-copy.md` Level B

---

## 7. 마커 치환 최종 출력 예시

LLM 마크다운 (치환 전):
```
## 오늘도 대화가 막혔나요?

"넌 너무 예민해." 이 말 한마디에 상처받으셨다면, 그 감정은 충분히 타당해요.

<!-- IMG:chat-preview -->

두 사람이 나눈 대화를 AI가 중립적으로 정리하면 이런 그림이 나와요.

<!-- IMG:report-needs-map -->

지금 이 한 마디로 시작해보세요.

<!-- IMG:quote-card -->
```

NaverImageStrategy 치환 후:
```
## 오늘도 대화가 막혔나요?

"넌 너무 예민해." 이 말 한마디에 상처받으셨다면, 그 감정은 충분히 타당해요.

![AI와의 대화 장면](naver_5_01_chat.png)

두 사람이 나눈 대화를 AI가 중립적으로 정리하면 이런 그림이 나와요.

![NeedsMap 다이어그램](naver_5_02_needs.png)

지금 이 한 마디로 시작해보세요.

![메타포 인용 카드](naver_5_03_quote.png)
```
