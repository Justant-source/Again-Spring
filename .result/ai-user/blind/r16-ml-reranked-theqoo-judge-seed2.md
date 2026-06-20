# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 16:03:22
> survey: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey-fixed.md`
> answers: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **35.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=A3, style_tells=B3 |
| 2 | A | human | ai | human | X | community_fit=A5, micro_tell=A4, narrative_flow=A4, style_tells=A3 |
| 3 | B | ai | human | human | X | community_fit=B4, micro_tell=B3, narrative_flow=A3, style_tells=B4 |
| 4 | B | human | ai | ai | O | community_fit=B3, micro_tell=A3, narrative_flow=B4, style_tells=B4 |
| 5 | B | ai | human | human | X | community_fit=B4, micro_tell=B5, narrative_flow=B4, style_tells=B5 |
| 6 | B | human | ai | ai | O | community_fit=B4, micro_tell=A3, narrative_flow=B4, style_tells=B4 |
| 7 | B | ai | human | human | X | community_fit=B4, micro_tell=B3, narrative_flow=B4, style_tells=B4 |
| 8 | A | human | ai | human | X | community_fit=A4, micro_tell=?1, narrative_flow=B3, style_tells=A3 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B3, narrative_flow=A3, style_tells=B4 |
| 10 | A | human | ai | human | X | community_fit=A4, micro_tell=?1, narrative_flow=A4, style_tells=A4 |
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

- `community_fit` B4: 정보 전달이 너무 단계적(제목→설명→반전→출처)이고 '그래피티 아티스트 존 버거만 작가와 콜라보한 것' 같은 구체 정보 삽입이 인위적. A는 감정 흐름만 자연스러움
- `micro_tell` B4: 정보성(아티스트 이름·콜라보 사실)이 너무 정확하고 정돈되며, '무서운데요'라는 경어 형식이 THEQOO 자연 반응으로 부자연스러움. [A]는 개인 하소연으로 감정과 구어체가 일관됨.
- `narrative_flow` A3: 감정 표현이 구조화됨 ('왜 나만 이러고 있나 싶고'). B는 반전과 은어('ㅊㄴ')가 자연스러움
- `style_tells` B3: B는 뉴스식 정보 나열('콜라보한 것')과 구어체가 어색하게 섞임. A는 감정의 흐름이 일관되고 자연스러운 말투

### Pair 2

- `community_fit` A5: [A]는 운영진 공지 수준의 형식적·구조화된 톤으로 AI 생성 냄새. [B]는 '어제 스크린샷으로 받았음', '진짜 모르겠음' 등 THEQOO 실제 사용자의 자연스러운 구어체 고민글
- `micro_tell` A4: 공식 공지처럼 완벽하게 구조화되고 번호·마크다운 형식으로 정렬됨. THEQOO 사용자가 쓸 리 없는 관리자 톤. B는 문법이 불완전하지만 실제 혼란과 감정이 자연스럽게 흘러감
- `narrative_flow` A4: [A]는 topic-first opener(정치카테고리 추가), 항목화 구조(1)2)예외1)2)), 뻣뻣한 공문서 어조(스루해주시기바랍니다)로 AI 생성 특성 뚜렷. [B]는 개인감정, 자연스러운 입말체, 부자연스러운 결말이 사람의 사연글
- `style_tells` A3: 과도하게 체계화된 공식 포맷. 번호 매김, 예외 조항, 완벽한 구조화가 AI 특징. B는 자연스러운 구어체 사연.

### Pair 3

- `community_fit` B4: B는 '저렇게 시뻘겋고 잔뜩 졸여져서 고춧가루 보이는 떡볶이' 부분이 설명적이고 구조화되어 있음. A는 자신의 감정과 상황을 더 자연스럽고 산발적으로 풀어냄.
- `micro_tell` B3: B의 '저렇게 시뻘겣고 잔뜩 졸여져서 고춧가루 보이는 떡볶이'가 시각적으로 지나치게 길고 설명적. A는 '팀장이 몰랐던 건지 알면서 그런 건지 걍 멍함' 같은 자연스러운 커뮤니티 표현이 살아있음.
- `narrative_flow` A3: 구조적 정보 설명(3주 기획안→동료 검토→크레딧 누락)과 '걍 멍함' 결말이 약간 뻣뻣함. B는 '꾸덕파', '미치겟음' 등 자연스러운 한국 커뮤니티 톤
- `style_tells` B4: [B]는 구체적 사건 없이 개인 선호도만 표현. '1번 떡볶이'라는 인위적 분류, '비주얼'이라는 어색한 단어 선택, 명확한 갈등 트리거 부재. [A]는 팀장이 다른 사람 이름을 댄 구체적 직장 일화로 더 인간다움.

### Pair 4

- `community_fit` B3: B가 상황→배경→감정 순서로 구조화되어 있고, 문법과 완성도가 높으며 자연스러운 오류가 거의 없음. A는 팬 글의 짧은 형식과 '다리는 짧...읍..지만' 같은 자연스러운 웃음 표현이 실제 커뮤니티 사용자처럼 보임.
- `micro_tell` A3: [A]는 실제 커뮤니티 반응어가 없고 너무 단순함. [B]는 '1도 안 물어봐' 같은 자연스러운 표현과 일관된 감정 서사로 인간다워 보임.
- `narrative_flow` B4: 구체적 사건(취직→엄마/아빠 반응)→감정(준비 노력)→뻣뻣한 결말('이게 맞는 건지 모르겠음') 구조. A는 너무 자연스러운 캐주얼 톤과 비문법적 표현으로 인간미 넘침.
- `style_tells` B4: 상황→부모반응→자신노력→패턴인식→의문 순으로 일관된 감정 전개 구조. '항상 이렇게 결국..혼자...' 부분에서 심리분석적 문학성이 AI스러움. A는 즉흥적이고 일상적

### Pair 5

- `community_fit` B4: [B]는 목록 구조화, 과다 이모지, 비정상 신조어('무묭이', '싸패', '대유잼', '오쏀'), 어색한 문맥 비약이 AI의 특징. [A]는 자연스러운 구어 일상 표현과 진정성 있는 감정 고민으로 휴먼.
- `micro_tell` B5: [B]는 '파워메인 잔잔마라', '시목여진 평생 공조해', '오쏀 힐링드' 같은 표현이 문법적으로 부자연스럽고, '우리 현수 싸패 아니예요'처럼 이해 불가능한 문장이 다수 포함됨. [A]는 '진짜 모르겠거든', '완전 잘 지내던데', '걍' 같은 자연스러운 입말체와 일관된 감정 표현으로 사람의 글임.
- `narrative_flow` B4: 글B는 나열식·체계적 구조, 문맥 전환 급격, '움짤 올리는 방법/업로드 방법/올리는 법' 반복이 AI 티. 글A는 감정 흐름·구체적 사건 기술이 자연스러운 휴먼 톤.
- `style_tells` B5: 부자연스러운 약자 반복('움짤 올리는 방법/방법/법'), 과한 이모지(👇👇👇), 문법 오류('시목여진 평생 공조해', '오쏀 힐링드'), 의미 불명 표현('무묭이', 'P;ㅠ 감사')이 AI 생성 특징

### Pair 6

- `community_fit` B4: 구조가 너무 체계적이고 논리적. 상황 설정→대조→문제 제시→성찰이 정돈되어 있음. A는 감정 분출이 파편적이고 자연스러운 THEQOO 문체
- `micro_tell` A3: '이런 글들 많음', '두개만 들고옴'처럼 마치 설정을 구성하듯 메타 언급이 있어 예시를 만드는 느낌. [B]는 구체적 개인 경험(10분 늦음, 한 시간 늦음)에 기반한 자연스러운 고민이 묻어남.
- `narrative_flow` B4: B는 구체적 사건(약속 10분 늦음/상대 1시간 늦음)을 명시하고 논리적 대조 구조(나는 기준 엄격함/상대는 느슨함)로 전개됨. topic-first opener와 자기 성찰형 결말이 AI의 잘 구성된 서사 패턴 특징. A는 감정 단편 나열로 생생한 인간성이 두드러짐.
- `style_tells` B4: B는 구체적 사건(10분/1시간 늦음)과 불공평한 패턴을 차례로 제시한 뒤 자기 성찰로 마무리하는 구조가 정연하고, 감정 표현이 균형잡혀 있음. A는 감정이 터져나오고 구어체가 원어민의 투정처럼 자연스러움.

### Pair 7

- `community_fit` B4: 눈물 이모지 30개 반복는 기계적이고, 상황 설명('저러고 나서 바게트 사서/길거리에서/오리가 찾아옴')이 과하게 구조화됨. A는 불평 톤이 자연스럽고 '연락 1도 없었음', 'ㅇㅇ' 같은 THEQOO 실제 문체
- `micro_tell` B3: 맥락 끊김(저러고 나서 바게트→오리→드라마 후기 갑전환)+ㅠ 30개 과다로 감정표현 부자연스러움. A는 친구 약속 미루기 상황 명확하고 비문(1도, ㅋㅋㅋ)이 자연스럽게 섞임
- `narrative_flow` B4: B는 '저러고 나서'로 갑자기 시작해 이전 문맥 부재 + 오리/합격/마지막화가 무관련하게 이어져 흐름이 뻣뻣함. A는 친구 지각 상황→불만→감정으로 자연스럽게 연결되고 한국인 실제 말투임
- `style_tells` B4: 감탄사 50회 이상 과한 반복 + 바게트→오리→합격 문맥 부자연스러움 + 드라마 시청 후기치고 감정 패턴이 인위적

### Pair 8

- `community_fit` A4: 너무 짧고 구조가 딱딱함. 영화 제목만 나열하고 질문으로 끝나는 방식이 AI가 템플릿화한 느낌. B는 구체적 상황·감정·말투가 자연스럽고 길이도 THEQOO 실제 글과 맞음.
- `micro_tell` ?1: [no reason]
- `narrative_flow` B3: 상황을 논리적으로 전개하는 구조가 AI 같음. '내가 연차가 제일 낮은 것도 아니고 그 업무 담당자도 아닌데'는 AI의 체계적인 조건 나열 패턴.
- `style_tells` A3: "아직도" 반복('기억에 남음' vs '잊혀지지 않는') — AI 감정표현의 상투적 패턴

### Pair 9

- `community_fit` B4: 매우 짧고 문맥이 단절됨. '핸드볼 전입신고', '은박지 텐트치기' 같은 표현들이 THEQOO 구체적 일상 기술 패턴과 맞지 않음. A는 설거지 상황을 자연스럽게 전개하며 문체가 자연스러움.
- `micro_tell` B3: 맥락이 끊어져 여러 댓글이 섞인 듯 보이고, '아예 핸드볼에다가 전입신고', '은박지로 텐트치기' 등 의미가 불명확하게 배치됨
- `narrative_flow` A3: topic-first opener(설거지 상황)가 명확하고 문장 연결이 논리적이며 결말이 '이게 맞는건지'로 약간 뻣뻣함. B는 문맥이 붕괴되어 있어 AI로 보기 어려움
- `style_tells` B4: 웃음소리 15개 과다, 문맥 단절(핸드볼→전입신고→은박지 텐트 비약), 자연스럽지 않은 문장 연결

### Pair 10

- `community_fit` A4: [A]는 광고 문구와 이벤트 규칙이 기계적으로 정형화되어 있고, [B]는 '걍', '진짜', '모르겠음' 등 더쿠의 실제 감정 토로 문체를 자연스럽게 따르고 있음
- `micro_tell` ?1: [no reason]
- `narrative_flow` A4: 체계적 구조·topic-first 상품소개·마케팅톤·이모지과다·형식화된 결말. B는 구어적표현('ㅋ','진짜')과 자연스러운 개인 감정이 살아있음.
- `style_tells` A4: 마케팅 체계적 구조 + '화잘먹 금손' 반복 + '밀착력과 피부 윤기를 높여 속수분은' 부자연스러운 문장 연결 + 💙✨ 이모지 규칙적 사용

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

