# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 16:03:24
> survey: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey-fixed.md`
> answers: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **35.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 2 | A | human | ai | human | X | community_fit=A4, micro_tell=A4, narrative_flow=B3, style_tells=A4 |
| 3 | B | ai | human | human | X | community_fit=A3, micro_tell=B4, narrative_flow=B4, style_tells=B2 |
| 4 | B | human | ai | ai | O | community_fit=A3, micro_tell=B3, narrative_flow=B4, style_tells=B3 |
| 5 | B | ai | human | human | X | community_fit=B5, micro_tell=B4, narrative_flow=B3, style_tells=B4 |
| 6 | B | human | ai | ai | O | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B2 |
| 7 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 8 | A | human | ai | human | X | community_fit=A3, micro_tell=A4, narrative_flow=B4, style_tells=A2 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=?1, style_tells=B4 |
| 10 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 11 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 12 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 13 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 14 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 15 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 16 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 17 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 18 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 19 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 20 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |

## Judge Reasons

### Pair 1

- `community_fit` B4: 정보를 설명하듯 나열하고 문장 연결이 어색. A는 감정과 불만이 자연스럽게 흘러가는 THEQOO 특유의 글
- `micro_tell` B4: 정보 전달식(롯데 콜라보) 후 반응('무서운데요')이 뒤에 붙는 구조가 부자연스러움. A는 감정 토로 속에 '진짜 모르겠음' 같은 반응어가 자연스럽게 흐름과 함께함.
- `narrative_flow` B4: B는 주제를 먼저 던지는 topic-first opener('레딧 리미널 스페이스 팬덤 유저들이...'), 정보 나열식 구조('그래피티 아티스트 존 버거만 작가와 콜라보'), 뻣뻣한 결말('어린이날용 맞냐')이 AI 특징. A는 구체적 상황, 자연스러운 감정 흐름, 일관된 구어체로 사람 글.
- `style_tells` B3: 정보제공식 톤(뉴스 헤드라인처럼)에 감탄사 부자연스러움('ㅈㄴ 무서운데요' 섞임) + '어린이날용 맞냐' 문법 어색

### Pair 2

- `community_fit` A4: 공식 공지문처럼 과도하게 구조화됨. 번호·체계·예외조항. THEQOO 일상 글 톤과 불일치.
- `micro_tell` A4: 공식문 구조+오타('고나리하거나'), 커뮤니티 반응어(헐·개공감·진짜) 전무, 감정 배제된 형식적 톤이 자동생성 특성
- `narrative_flow` B3: 구체적 사건(trigger)으로 시작하는 전형적 사연 형식. 구어체와 심리 표현은 자연스럽지만 다소 정제되고 정렬된 느낌이 AI 생성을 시사함. A는 공지문으로 맥락 상 이상.
- `style_tells` A4: 공지사항 형식으로 체계화·형식화된 구조와 문장이 AI 티. B는 감정·상황을 자연스러운 일상문으로 표현한 사람 글

### Pair 3

- `community_fit` A3: A는 상황-배경-결론이 너무 명확하게 정렬되어 있고 논리 구조가 체계적이어서 AI티가 보임. B는 감정 흐름이 더 자연스럽고 '꾸덕파' 같은 커뮤니티 용어 사용이 실제 사용자처럼 느껴짐
- `micro_tell` B4: '저렇게 시뻘겓고 잔뜩 졸여져서 고춧가루 보이는 떡볶이' 표현이 너무 세련되고 인위적. A는 직장 부당함을 구체적 상황으로 표현한 반면, B는 떡볶이 취향을 마치 글쓰듯 정리한 느낌
- `narrative_flow` B4: 1번/2번 떡볶이 구분이 모호하고 '고춧가루 보이는' 표현이 어색. 구체적인데 실제 의미와 맞지 않는 AI 특성. A는 직장 불만이 인간답고 감정 표현이 자연스러움.
- `style_tells` B2: 투표 글의 전형적 구조(1번/2번 대조) + '좋은데/아닌데' 반복 구조가 AI의 이진 대조 패턴과 일치. [A]는 개인적 상황 묘사가 구체적이고 '걍 멍함' 같은 자연스러운 종결로 인간미 있음

### Pair 4

- `community_fit` A3: 너무 짧고 구체적 디테일 부족. B는 취직·가족반응·심리적 깊이가 있어 실제 경험이 묻어남
- `micro_tell` B3: B는 문제 상황의 맥락, 갈등, 감정, 질문으로 이어지는 구조가 체계적이고 개인 경험이 구체적으로 설정되어 있어 AI의 스토리 구성 패턴으로 보인다. A는 극도로 짧고 단순하며 비문적 요소와 이모지만 있어서 사람이 쓴 글처럼 보인다.
- `narrative_flow` B4: 명확한 3단계 구조(상황 제시→부모 반응→자기 감정), topic-first opener(취직 회사), 심리적 갈등을 체계적으로 전개하고 성찰적 결말이 다소 뻣뻣함. A는 짧은 사진 설명일 뿐.
- `style_tells` B3: 구체적 사건을 정제된 구조로 설명한 뒤 '나는 항상 이렇게 결국...'에서 패턴화된 자기분석으로 넘어가는 흐름이 AI 생성 텍스트 특유의 상황→감정 프레임과 맞음. A는 이모지와 부자연스러운 음절('읍..')로 인간미 있게 느껴짐.

### Pair 5

- `community_fit` B5: [B]는 구조가 과도하게 조직화되어 있고 동일 의미 반복('움짤 올리는 방법/업로드 방법/올리는 법'), 부자연스러운 용어('무묭이', '원덬이'), 문법 어색('으로 데려온') 등이 AI 생성 특징. [A]는 자연스러운 구어체와 감정 표현이 THEQOO 실제 문체와 정확히 일치.
- `micro_tell` B4: 문법 붕괴(괄호 미종료, 문장 단절), 특정 용어 비자연스러운 삽입(파워메인·시목여진·오쏀 등 맥락 불명확), 구조 비체계적 정보 더미식. A는 구어체·감정 자연스러움·일관된 사연 구조로 사람 티 명백
- `narrative_flow` B3: [B]는 구조화된 리스트와 이모지 반복으로 자동생성 느낌. [A]는 실시간 감정 흐름·자기반성·구체적 불안감이 자연스러움.
- `style_tells` B4: 문장 구조가 파편화되고 'A 방법 / A 업로드 / A 올리는 법' 같은 동일 의미 반복, '👇👇👇' 과도한 이모지·화살표 남발, 드라마 설명이 비자연스럽고 끊겨있음. A는 구어체 일관성 있고 감정표현 자연스러움.

### Pair 6

- `community_fit` B4: 논리적 구조(상황-구체사례-감정-자기성찰)가 정연하고, 감정표현이 통제되어있으며, 자기반성 표현이 명시적. A는 감정폭발·문장단편화·욕설로 THEQOO 실제 문체. B는 AI가 조언글에서 쓰는 전형적 구성.
- `micro_tell` B4: [B]는 상황-구체예-문제분석-자기의심의 구조가 너무 정돈됨. [A]는 '염병들 하고있음','2찍들','제발 현실좀 살길' 같은 실제 분노한 사용자의 비문·욕설·절박함이 자연스러움
- `narrative_flow` B4: 명확한 topic-first opener + 구체적 사례(10분 vs 1시간) + 논리적 대비 + 체계적 문장 연결이 AI의 글쓰기 특징. A는 산발적·감정적으로 매우 자연스러운 유저 글
- `style_tells` B2: B는 문제-사례-패턴분석-고민이 너무 논리적으로 정렬됨. A는 감정적·자조적·띄어쓰기 어색한 THEQOO다운 스타일

### Pair 7

- `community_fit` B4: 구체성 부족, ㅠ 과도 반복, 괄호 설명 어색, '합격함' 맥락 불명확 → 인위적. A는 구체적 상황과 자연스러운 감정 표현으로 실제 경험처럼 보임
- `micro_tell` B4: 눈물이모티콘 30개 반복은 실제 감정보다 AI의 '감정 과장 연출', 이야기 구조가 너무 정갈함 (오리 등장→감동→드라마 예고), 자신 경험이 아닌 미디어 리뷰 스타일
- `narrative_flow` B4: 스토리 연결 파편화(바게트→오리→합격), 감정 반복과다(ㅠㅠ×30+), 결말 뻣뻣함(드라마 응원구). A는 일상적 불만 흐름이 자연스러움
- `style_tells` B4: 감탄사 수십 개 극반복(ㅠㅠㅠ..., ㅠㅠㅠ...) 과한 패턴. A는 자연스러운 일상 표현.

### Pair 8

- `community_fit` A3: 영화 제목 나열과 단순한 질문 구조. B는 직장 상황의 구체적 감정 표현과 '1도', '슬쩍 와서' 등 자연스러운 한국어가 훨씬 풍부함
- `micro_tell` A4: [A]는 영화 제목 나열→한 줄 평가→질문이라는 매우 형식화된 구조가 기계적. [B]는 '칼퇴', '1도 모르겠', 'ㅋㅋ·ㅠㅠ' 등 실제 반응어가 자연스럽고 감정 표현이 풍부함.
- `narrative_flow` B4: [B]는 구체적 사건(팀장의 반복 지시, 네 번째), 감정 흐름(답답함→자조→고민), 선택지 노출이 일관되고, 다시봄이 처리할 갈등글의 전형적 구조. [A]는 영화 제목 나열로 사연이 아니며 AI 생성 가능성이 낮음.
- `style_tells` A2: 영화 4개 나열 후 한 문장 감정과 질문으로 마무리하는 구조가 과하게 규칙적. B는 구체적인 상황·감정·고민이 자연스럽게 흘러감

### Pair 9

- `community_fit` B4: 핸드볼·전입신고·은박지 텐트가 서로 연결 안 되고, 무엇을 웃고 있는지 맥락이 불명확해 보임. [A]는 룸메 갈등을 일관되고 구체적으로 묘사해 실제 경험담처럼 자연스러움
- `micro_tell` B4: 맥락 없이 단편적인 문장들이 연결되지 않으며, '핸드볼 전입신고'와 '은박지 텐트'가 논리적으로 연결되지 않음. A는 룸메 설거지 분쟁이라는 일관된 상황을 자연스러운 구어체로 풀어냄.
- `narrative_flow` ?1: [no reason]
- `style_tells` B4: 문맥 단절(핸드볼·전입신고→실시간→은박지 텐트), 감탄사 14개 과도 반복, 각 문장이 독립적이고 연결 부재

### Pair 10

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 11

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 12

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 13

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 14

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 15

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 16

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 17

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 18

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 19

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

### Pair 20

- `community_fit` ?1: [no reason]
- `micro_tell` ?1: [no reason]
- `narrative_flow` ?1: [no reason]
- `style_tells` ?1: [no reason]

