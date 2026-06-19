# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-19 16:51:16
> survey: `.result/ai-user/blind/r14-cond5-theqoo-survey.md`
> answers: `.result/ai-user/blind/r14-cond5-theqoo-answers-template.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **50.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | human | ai | ai | O | community_fit=B3, narrative_flow=B4, style_tells=B4 |
| 2 | A | ai | human | ai | O | community_fit=A4, narrative_flow=A4, style_tells=A4 |
| 3 | B | human | ai | ai | O | community_fit=B4, narrative_flow=A4, style_tells=B3 |
| 4 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B5 |
| 5 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B3 |
| 6 | B | ai | human | human | X | community_fit=A4, narrative_flow=B4, style_tells=B4 |
| 7 | A | human | ai | human | X | community_fit=A5, narrative_flow=A5, style_tells=B4 |
| 8 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 9 | B | ai | human | human | X | community_fit=B4, narrative_flow=B5, style_tells=B3 |
| 10 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=A5 |
| 11 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 12 | A | ai | human | ai | O | community_fit=A3, narrative_flow=A4, style_tells=B4 |
| 13 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B5 |
| 14 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B5, style_tells=B4 |
| 15 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 16 | A | human | ai | human | X | community_fit=B4, narrative_flow=A4, style_tells=A4 |
| 17 | A | human | ai | human | X | community_fit=A4, narrative_flow=A4, style_tells=A4 |
| 18 | A | human | ai | human | X | community_fit=A5, narrative_flow=A5, style_tells=A5 |
| 19 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B5 |
| 20 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B2 |

## Judge Reasons

### Pair 1

- `community_fit` B3: A가 더 막쓴 커뮤 말투 같고, B는 사연 전개가 조금 더 정돈돼 보여서
- `narrative_flow` B4: 문장 흐름이 너무 정돈돼 있고 하소연 전개가 교과서처럼 이어져서 더 AI스럽다
- `style_tells` B4: 사연 전개와 감정 표현이 너무 정돈돼 있어 커뮤체 즉흥성이 덜함

### Pair 2

- `community_fit` A4: A는 하소연 톤은 맞는데 문장 연결이 좀 기계적이고 '개공감' 들어간 위치가 특히 어색함
- `narrative_flow` A4: 사연 전개가 단계별로 너무 정리돼 있고 끝맺음도 판정 요청형이라 살짝 AI티남
- `style_tells` A4: 질문형 반복과 감정 전개가 너무 단계적으로 정리돼 보여서 AI 티가 남

### Pair 3

- `community_fit` B4: 감정선이 너무 고르게 이어져서 실제 커뮤글보다 약간 작문처럼 보임
- `narrative_flow` A4: 제목형 첫문장에 정보 연결이 매끈해서 사람 후기보다 정리문 톤이 강함
- `style_tells` B3: 감정선이 너무 교과서처럼 단계적으로 반복돼서 AI 문장 느낌이 남

### Pair 4

- `community_fit` B5: 공지문처럼 너무 정돈돼 있고 말투가 커뮤 글치고 딱딱함
- `narrative_flow` B5: 주제부터 항목형으로 정리된 공지체고 결말도 지나치게 정돈돼 있어 AI 문안처럼 보임
- `style_tells` B5: 공지체로 너무 정돈돼 있고 항목 반복 패턴이 강해서 AI 문장처럼 보임

### Pair 5

- `community_fit` B4: 서술이 너무 정돈돼 있고 '했다고 함'처럼 시점이 흔들려 작문 느낌이 남
- `narrative_flow` B4: 주제문으로 시작하고 전개가 너무 매끈하며 끝도 교과서식 하소연이라 AI 느낌
- `style_tells` B3: 전개가 너무 매끈하고 반응형 문장이 반복돼서 AI식 정리글 느낌이 남

### Pair 6

- `community_fit` A4: 더쿠 특유의 말투보다 일반 연애상담체에 가깝고 문장 흐름이 너무 반듯함
- `narrative_flow` B4: 제목식 도입과 문장 연결이 너무 뜬금없고, 마지막 인사도 뻣뻣해서 AI 느낌이 남
- `style_tells` B4: 반복 패턴과 이모지·감탄 표현이 과해서 더 인공적으로 보임

### Pair 7

- `community_fit` A5: 공지문처럼 과하게 정리돼 있고 말투가 딱딱해서 커뮤 글 느낌이 덜함
- `narrative_flow` A5: 주제부터 딱 꺼내고 문장 연결이 공지문처럼 기계적이며 결말도 지나치게 뻣뻣함
- `style_tells` B4: 짧은 문장 패턴이 반복되고 '헐' 같은 감탄사 삽입이 좀 인위적임

### Pair 8

- `community_fit` B4: 문장 흐름이 너무 정돈돼 있고 커뮤체 흉내낸 표현이 조금 작위적임
- `narrative_flow` B4: 서사가 너무 매끈하게 순서대로 이어지고 첫줄 주제문도 AI식으로 정리된 느낌임
- `style_tells` B4: 상황 설명이 너무 정돈돼 있고 짧은 문장 패턴 반복이 강함

### Pair 9

- `community_fit` B4: A는 더쿠식 하소연 말투가 자연스럽고, B는 본문이라기보다 요약 템플릿처럼 너무 건조함
- `narrative_flow` B5: 주제부터 바로 던지는 요약형 문장이라 더쿠식 잡담 흐름이 없고, 문장 연결 없이 공지문처럼 딱딱함
- `style_tells` B3: 너무 요약문체고 말투 결이 과하게 정리돼 있음

### Pair 10

- `community_fit` B4: 사연 전개가 너무 정돈돼 있고 말투가 커뮤글치곤 덜 날것 같음
- `narrative_flow` B4: 도입이 주제선언형이고 사건 전개가 너무 매끈하며 결말도 상담글 템플릿처럼 정리돼 보여서
- `style_tells` A5: ㅠ 반복과 감탄 패턴이 과해서 인위적으로 보임

### Pair 11

- `community_fit` B4: 서술이 너무 정돈돼 있고 감정선이 단계적으로 깔끔해서 커뮤 글치곤 생성형 느낌이 남
- `narrative_flow` B4: 사연 흐름이 지나치게 매끈하고 문장 연결이 정리돼 있어서 AI체 같음
- `style_tells` B4: 문장 흐름이 너무 정돈돼 있고 끝부분 반복 패턴이 AI식으로 보임

### Pair 12

- `community_fit` A3: 서술이 너무 정돈돼 있고 하소연 흐름이 살짝 인위적임
- `narrative_flow` A4: 주제 선공개 뒤에 상황을 차례로 정리하는 흐름이 너무 매끈하고, 문장 연결도 균일해서 AI식 사연문처럼 보임
- `style_tells` B4: 과한 느낌표 반복이랑 짧은 홍보형 문장 패턴이 더 부자연스러움

### Pair 13

- `community_fit` B5: 광고 공지문 톤이 너무 강하고 문장 흐름이 커뮤니티 글보다 홍보문구에 가깝다
- `narrative_flow` B5: 도입부터 주제선언형이고 문장 연결이 지나치게 매끈하며 결말도 홍보문처럼 뻣뻣함
- `style_tells` B5: 홍보문체 반복이 많고 감탄·이모지·문장 톤이 부자연스러움

### Pair 14

- `community_fit` B4: 전개가 너무 매끈하고 갈등-정리-질문 흐름이 템플릿처럼 정돈돼 보임
- `narrative_flow` B5: 흐름이 너무 정돈돼 있고 문장 연결이 매끈해서 커뮤식 날것보다 생성문 느낌이 강함
- `style_tells` B4: 서술 흐름이 너무 매끈하고 반복 패턴이 많아 AI 문체에 가깝다

### Pair 15

- `community_fit` B4: 나열식 문장 흐름이 너무 정리돼 있고 더쿠식 자연스러운 감정선이 약함
- `narrative_flow` B4: 문장이 나열식으로 끊기고 결말이 설명조라 흐름이 부자연스러움
- `style_tells` B4: 나열식 문장 반복이 많고 표현이 좀 기계적으로 끊겨 보여서

### Pair 16

- `community_fit` B4: 문장 흐름이 너무 고르게 정리돼 있고 사연 전개도 전형적이라 커뮤 글치곤 덜 날것 같음
- `narrative_flow` A4: 키워드 나열식이라 흐름이 끊기고 마지막 설명도 억지로 붙인 느낌임
- `style_tells` A4: 이름+인스타 테러 반복이 너무 패턴적이라 AI 요약문처럼 보임

### Pair 17

- `community_fit` A4: 줄나눔이 너무 정돈돼 있고 요약문 톤이 인위적임
- `narrative_flow` A4: 사건 흐름을 단계적으로 너무 정리했고 연결어랑 결말 톤이 뻣뻣함
- `style_tells` A4: 반복적 전개랑 과한 말줄임표가 더 작위적임

### Pair 18

- `community_fit` A5: 광고문구 같고 말투가 커뮤체랑 너무 동떨어짐
- `narrative_flow` A5: 홍보문 톤이 너무 정형적이고 도입부터 주제선언형이라 AI/복붙 느낌이 강함
- `style_tells` A5: 과한 이모지·감탄사와 홍보 문구 반복이 부자연스러움

### Pair 19

- `community_fit` B5: 문체가 너무 인위적으로 끊기고 소재를 여러 개 억지로 붙인 느낌이라 커뮤글 톤이 덜 자연스러움
- `narrative_flow` B5: 화자와 주제가 계속 튀고 문장 연결이 인위적이라 AI 요약문처럼 보임
- `style_tells` B5: 반복 패턴이 과하고 말투 전환이 부자연스러움

### Pair 20

- `community_fit` B4: 문장 흐름이 좀 어색하고 '개공감'이 맥락상 뜬금없음
- `narrative_flow` B4: 문장 연결이 단조롭고 끝맺음이 좀 뻣뻣함
- `style_tells` B2: ‘개공감’ 쓰임이 어색하고 ‘함’ 반복이 좀 기계적임

