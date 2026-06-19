# Ensemble Blind Judge — NATEPAN
> 생성: 2026-06-19 16:51:23
> survey: `.result/ai-user/blind/r14-cond5-natepan-survey.md`
> answers: `.result/ai-user/blind/r14-cond5-natepan-answers-template.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **45.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | A | human | ai | human | X | community_fit=A5, narrative_flow=A5, style_tells=A4 |
| 2 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 3 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 4 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B4 |
| 5 | B | human | ai | ai | O | community_fit=B3, narrative_flow=B4, style_tells=A3 |
| 6 | A | ai | human | ai | O | community_fit=A4, narrative_flow=A4, style_tells=A3 |
| 7 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B5, style_tells=B5 |
| 8 | A | human | ai | human | X | community_fit=A4, narrative_flow=B4, style_tells=A4 |
| 9 | A | ai | human | ai | O | community_fit=A4, narrative_flow=A4, style_tells=A4 |
| 10 | A | human | ai | human | X | community_fit=A4, narrative_flow=A4, style_tells=A4 |
| 11 | A | human | ai | human | X | community_fit=A4, narrative_flow=A4, style_tells=A4 |
| 12 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B5 |
| 13 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 14 | B | human | ai | ai | O | community_fit=A4, narrative_flow=B4, style_tells=B4 |
| 15 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 16 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=A4 |
| 17 | A | human | ai | human | X | community_fit=A5, narrative_flow=B4, style_tells=A4 |
| 18 | A | human | ai | human | X | community_fit=A4, narrative_flow=A4, style_tells=A5 |
| 19 | A | ai | human | ai | O | community_fit=A5, narrative_flow=A4, style_tells=A4 |
| 20 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B3 |

## Judge Reasons

### Pair 1

- `community_fit` A5: 문장 너무 정돈돼 있고 상담글처럼 길고 매끈해서 판 글톤이랑 덜 맞음
- `narrative_flow` A5: 흐름이 너무 정돈돼 있고 연결어·심리분석 표현이 상담문처럼 매끈해서 AI 느낌이 강함
- `style_tells` A4: 문단마다 지나치게 정돈돼 있고 연결어·설명 패턴이 반복돼 AI식 서술 느낌이 큼

### Pair 2

- `community_fit` B4: 판 글치고 너무 추상적이고 가사처럼 흘러서 말투가 덜 자연스러움
- `narrative_flow` B4: 판글치고 흐름이 약하고 문장 연결이 인위적임
- `style_tells` B4: 반복 어구가 두드러지고 문장이 감정시처럼 부자연스럽습니다

### Pair 3

- `community_fit` B4: 판 글치곤 너무 정갈하고 감정선이 무난하게 정리됨
- `narrative_flow` B4: 전개가 너무 매끈하고 주제문식 첫문장+정리된 씁쓸 결말이 AI스럽다
- `style_tells` B4: 감정선이 너무 정돈돼 있고 문장 패턴이 비슷하게 반복됨

### Pair 4

- `community_fit` B5: 문장이라기보다 키워드 나열 같아서 판 글체로 덜 자연스러움
- `narrative_flow` B5: 문장 연결이 없고 키워드만 나열돼 글 흐름이 너무 비자연스러움
- `style_tells` B4: 문장형이 아니라 키워드만 나열돼서 글맛이 없고 부자연스러움

### Pair 5

- `community_fit` B3: 상황 전개가 너무 정돈돼 있고 문장 흐름이 실제 판 글치고 좀 매끈한 편임
- `narrative_flow` B4: 상황 설명이 너무 순서정연하고 문장 연결이 매끈해서 AI식 사연글 느낌이 강함
- `style_tells` A3: 표현이 다소 딱딱하고 문장 흐름이 부자연스러움

### Pair 6

- `community_fit` A4: 문장 흐름이 너무 정돈돼 있고 고민글 템플릿처럼 보여서 판 말투치고 덜 날것 같음
- `narrative_flow` A4: 사연 전개가 너무 정리돼 있고 문장 연결이 과하게 매끈해 AI 느낌이 남
- `style_tells` A3: 서술이 너무 정돈돼 있고 '둘이서' 같은 강조 패턴이 반복돼 AI식 고민글 톤이 납니다

### Pair 7

- `community_fit` B4: 문장이 너무 정돈돼 있고 반복 설명이 많아 커뮤 특유의 날것 같은 느낌이 덜함
- `narrative_flow` B5: 주제선언형 도입이고 문장 연결이 너무 매끈해서 결말도 템플릿처럼 보임
- `style_tells` B5: 서술이 너무 정돈돼 있고 '근데/거든요' 같은 패턴 반복이 많아 AI식 고민글 톤에 가깝다

### Pair 8

- `community_fit` A4: 감정선이 과하게 정리돼 있고 가사 인용까지 붙어서 판 글치곤 좀 작위적임
- `narrative_flow` B4: 짧은 글인데 흐름이 너무 정리돼 있고 마지막 자문형 결말이 작위적임
- `style_tells` A4: 감정문장 패턴이 반복되고 말투가 과하게 정돈돼 보여서 AI 티가 더 남

### Pair 9

- `community_fit` A4: A가 문장 흐름이 너무 정돈돼 있고 고민글 톤이 템플릿처럼 보임
- `narrative_flow` A4: 사연형 흐름과 문장 연결이 너무 정돈됐고 결말도 상담글처럼 뻣뻣함
- `style_tells` A4: 서술이 너무 매끈하고 감정 전개가 정리된 느낌이라 AI 티가 더 남

### Pair 10

- `community_fit` A4: 판 글치곤 말투가 너무 작위적이고 과장 반복이 많아 부자연스러움
- `narrative_flow` A4: 주장부터 박는 topic-first고 문장 전개가 단조로우며 결말도 뻣뻣함
- `style_tells` A4: 과장어와 반복이 과하고 문장 리듬이 기계적으로 몰아치는 느낌임

### Pair 11

- `community_fit` A4: 문장 연결이 어색하고 요약문처럼 말투가 덜 자연스러움
- `narrative_flow` A4: 주제부터 요약식으로 바로 들어가고 문장 연결이 해설문처럼 딱딱해서 AI 티가 남
- `style_tells` A4: '하는거' 반복이 많고 해명문처럼 문장 결이 조금 부자연스러움

### Pair 12

- `community_fit` B5: 행갈이 많고 시처럼 써서 판 글투랑 덜 맞음
- `narrative_flow` B5: 문장 연결이 반복적이고 시처럼 과하게 정돈돼서 네이트판 실감나는 사연글 톤이 아님
- `style_tells` B5: 반복 구조가 과하고 문장 흐름이 인위적임

### Pair 13

- `community_fit` B4: 너무 짧고 추상적이라 판 글투보다 생성문장 느낌이 남
- `narrative_flow` B4: 맥락 없이 문장만 나열돼서 흐름이 약하고 너무 뭉뚱그린 표현이라 AI 문장 느낌이 남
- `style_tells` B4: 짧고 추상적인데 같은 어휘 반복이 많아 기계적으로 보임

### Pair 14

- `community_fit` A4: 판 글치고 너무 짧고 맥락 없는 칭찬문이라 자연스러움이 덜함
- `narrative_flow` B4: 사연 전개가 너무 매끈하고 감정선이 정리돼 있어서 AI식 서술 티가 남
- `style_tells` B4: 문장 길이와 전개가 너무 고르고, '근데'식 패턴 반복이 많아 AI 티가 남

### Pair 15

- `community_fit` B4: 맥락 없이 기사 헤드라인처럼 끊겨서 판 글투로는 덜 자연스러움
- `narrative_flow` B4: 문장 연결이 끊기고 맥락 설명 없이 제목식으로만 이어져 AI 요약문처럼 보임
- `style_tells` B4: 반복 표현이 눈에 띄고 문장 연결이 어색함

### Pair 16

- `community_fit` B4: 서술이 너무 매끈하고 판 특유의 거친 줄임말·튀는 말투가 덜함
- `narrative_flow` B4: 도입-전개-감정-질문 결말 흐름이 너무 매끈하고 정돈돼 보여서
- `style_tells` A4: ㅋㅋ 반복, 같은 어투 재사용, 마지막 이름 나열이 과해서 부자연스러움

### Pair 17

- `community_fit` A5: 판 글치고 시처럼 너무 작위적임
- `narrative_flow` B4: 주제형 도입과 지나치게 반듯한 흐름, 정리된 결말이 AI톤에 가깝습니다
- `style_tells` A4: 시적 문장 반복과 말줄임표가 너무 정형적임

### Pair 18

- `community_fit` A4: 표현이 다소 작위적이고 문장 흐름이 덜 자연스러움
- `narrative_flow` A4: 서사 없이 결론만 툭 나오고 문장 연결이 어색해 AI 요약문처럼 보임
- `style_tells` A5: 표현이 과하게 꾸며졌고 문장 호흡이 부자연스러움

### Pair 19

- `community_fit` A5: 문장이 너무 정돈돼 있고 감정 표현도 무난해서 커뮤체 느낌이 약함
- `narrative_flow` A4: 사연 흐름이 너무 정돈돼 있고 문장 연결이 매끈해서 판글 특유의 날것 느낌이 덜함
- `style_tells` A4: 문장 흐름이 지나치게 정돈돼 있고 감정 표현도 무난해서 AI식 서술 느낌이 더 남

### Pair 20

- `community_fit` B4: 문장이 너무 매끈하고 감정 포인트가 고르게 들어가서 살짝 인위적임
- `narrative_flow` B4: 첫문장 요약형 도입이 강하고 전개가 너무 가지런해 AI식 사연문 톤에 가깝습니다
- `style_tells` B3: 서술이 너무 정돈돼 있고 '캡처' 반복이 인위적임

