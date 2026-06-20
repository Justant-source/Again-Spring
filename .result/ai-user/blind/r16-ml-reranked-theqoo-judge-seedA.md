# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 19:30:15
> survey: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey-fixed.md`
> answers: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **30.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | ai | human | human | X | community_fit=B3, micro_tell=B4, narrative_flow=B3, style_tells=B3 |
| 2 | A | human | ai | human | X | community_fit=A3, micro_tell=A4, narrative_flow=?1, style_tells=A3 |
| 3 | B | ai | human | human | X | community_fit=B3, micro_tell=B3, narrative_flow=A3, style_tells=B3 |
| 4 | B | human | ai | ai | O | community_fit=B3, micro_tell=A3, narrative_flow=B3, style_tells=A2 |
| 5 | B | ai | human | human | X | community_fit=B3, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 6 | B | human | ai | ai | O | community_fit=B3, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 7 | B | ai | human | human | X | community_fit=B4, micro_tell=B5, narrative_flow=B4, style_tells=B4 |
| 8 | A | human | ai | human | X | community_fit=A3, micro_tell=A3, narrative_flow=A3, style_tells=B3 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B3, narrative_flow=A2, style_tells=B4 |
| 10 | A | human | ai | human | X | community_fit=A4, micro_tell=A3, narrative_flow=A4, style_tells=?1 |
| 11 | B | ai | human | human | X | community_fit=B5, micro_tell=B4, narrative_flow=B5, style_tells=B5 |
| 12 | A | human | ai | human | X | community_fit=A5, micro_tell=A5, narrative_flow=A4, style_tells=A4 |
| 13 | A | ai | human | ai | O | community_fit=A4, micro_tell=A4, narrative_flow=?1, style_tells=B3 |
| 14 | B | human | ai | ai | O | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 15 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 16 | B | human | ai | ai | O | community_fit=B4, micro_tell=?1, narrative_flow=B4, style_tells=B3 |
| 17 | B | ai | human | human | X | community_fit=B5, micro_tell=B2, narrative_flow=B3, style_tells=B4 |
| 18 | A | human | ai | human | X | community_fit=A3, micro_tell=A3, narrative_flow=A4, style_tells=A3 |
| 19 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 20 | B | human | ai | ai | O | community_fit=B4, micro_tell=A3, narrative_flow=B4, style_tells=A4 |

## Judge Reasons

### Pair 1

- `community_fit` B3: 정보 구조가 다듬어졌고, '존 버거만' 같은 정확한 고유명사와 상황 설명이 순차적으로 배열된 형태. [A]는 실제 하소연의 즉흥성과 감정의 흐름이 자연스러움
- `micro_tell` B4: B는 전개가 너무 정연함(전제→반전→평가). A는 감정 토로형으로 '맨날', '진짜 모르겠음' 같은 자연스러운 티가 많고 울분의 흐름이 비정형적인 게 사람답다.
- `narrative_flow` B3: topic-first opener("레딧 리미널... 사진 한장")로 정보 구조화 후 단계적 전개. twist 활용한 설명형 서사가 AI 스타일. [A]는 감정 중심, 비정형적 흐름으로 인간적.
- `style_tells` B3: 정보전달 형식(뉴스식 설명구조)과 '네요'의 인위적 감탄이 있고, A는 자연스러운 감정 토로+일상 구어체가 명확함

### Pair 2

- `community_fit` A3: 공지문치고 과도하게 체계적·구조화, 규칙 나열이 기계적. B는 비문(얘기하다 나왔다)·감정 표현·자연스러운 일상문체가 인간다움
- `micro_tell` A4: 공지문 같은 형식(번호매김·예외1·예외2)과 '카테고리 기준은 아래의 공지내용을 참고하시면 됩니다' 같은 관리자톤이 THEQOO 사용자글로는 부자연스러움. B는 '진짜 모르겠음'·'얘기하다 나왔다고' 같은 일상적 감정표현으로 자연스러운 고민글.
- `narrative_flow` ?1: [no reason]
- `style_tells` A3: 공식문서 형식·체계적 분류·'기준은 아래 참고하시면 됩니다' 같은 딱딱한 표현. B는 입말체·감정 표현으로 자연스러움

### Pair 3

- `community_fit` B3: '2번 비주얼'이 어색하고, 전체 톤이 정제되어 있으며 감정 표현이 제너릭함. A는 '걍 멍함' 같은 자연스러운 THEQOO 톤과 구체성이 두드러짐
- `micro_tell` B3: '2번 비주얼이라'는 표현이 어색. 자연스러운 THEQOO 톤은 '2번이라'(떡볶이 생략)인데, 비주얼이라 명시한 부분이 인위적으로 느껴짐. A는 상황이 구체적(3주, 혼자, 메일)하고 '걍 멍함'처럼 투박한 사람 티가 명확.
- `narrative_flow` A3: 사건-배경-결과의 논리 구조가 과도하게 정렬됨. '팀장이 몰랐던 건지... 걍 멍함'은 뻣뻣한 결말. [B]는 떡볶이 취향 같은 일상적 관찰이 더 자연스러움.
- `style_tells` B3: 꾸덕파라 1번 떡볶이 표현이 다소 어색하고, '시뻘겣고 잔뜩 졸여져서 고춧가루 보이는' 식의 형용사 나열이 인위적. A는 직장 갈등 구체 사건으로 감정이 더 진솔함.

### Pair 4

- `community_fit` B3: [B]는 상황-반응-패턴 인식을 과도하게 구조화된 방식으로 표현. [A]는 팬의 자연스러운 짧은 반응 스타일
- `micro_tell` A3: A는 '여전히' 반복과 '짧...읍..지만'이 지나치게 자연스러워 의도적으로 연기하는 것처럼 보임. B는 '1도 안 물어봄' 같은 자연스러운 반응어와 THEQOO 특유의 하소연 구조(상황→반응→감정)가 일관되어 더 사람다움.
- `narrative_flow` B3: [B]는 사건→부모반응→자신감정→회의적질문의 구조화된 흐름을 따르고, '이게 맞는 건지 모르겠음'이라는 도덕·인식론적 결말이 AI 생성문의 전형. [A]는 매우 짧고 직관적이며 '짧...읍..지만'이라는 자연스러운 인터넷 표현과 감정 중심의 이모지 마무리가 인간적.
- `style_tells` A2: "여전히" 패턴 반복(2회)이 과하고, 짧은 길이에 불필요한 반복이 눈에 띔

### Pair 5

- `community_fit` B3: 과도한 구조화와 일부 약어 표현('파워메인 색/빛연출 맛집', '시목여진 평생 공조해', '오쏀 힐링드')이 의미 불명확하고, AI가 더쿠 특유 신조어를 부분적으로 이해하지 못한 것처럼 보임. [A]는 감정이 살아있는 자연스러운 상담글
- `micro_tell` B4: B는 이모지 과다, 의미 불명확한 오타들(무묭이→무뭐?, 싸패→?, 오쏀→?), 드라마 제목/용어 나열이 체계적이지 않음. A는 '진짜 모르겠거든', '나만 따로 두는 거임?' 같은 자연스러운 감정사 표현과 일관된 고민 구조로 사람의 진정한 토로로 읽힘.
- `narrative_flow` B4: 구조화된 포맷, '으로 데려온'의 어색한 문장 연결, 반복적인 드라마 나열 패턴, 과도한 이모지 사용이 AI 생성 특징
- `style_tells` B4: B는 드라마 축약형·이모지 패턴·체계적 리스트 구조가 과도하고, '(P;ㅠ 감사' 같은 부자연스러운 표현이 있음. A는 감정의 자연스러운 흐름과 진정한 구어체.

### Pair 6

- `community_fit` B3: A는 맥락 불명확하고 단편적이어서 오히려 실제 커뮤니티글 같음. B는 상황→예시→고민이 논리적으로 체계화되어 있고 톤이 일관성있게 정제됨
- `micro_tell` B4: 논리 구조가 너무 명확하고 체계적. 반응어 부재, 감정 절제, 심리상담 톤. A는 '2찍들도 손절' 같은 실제 은어와 '염병들 ㅠ' 같은 자연스러운 감정표현이 있음.
- `narrative_flow` B4: 명확한 topic-first opener(남친이 기준이 다름), 구체적 상황 비교(10분 vs 1시간 늦음), 논리적 흐름, 자연스러운 결말. AI가 구조화하는 방식이 두드러짐. A는 감정만 흩어지고 불완전해서 오히려 실제 사람의 단편적 호소로 보임
- `style_tells` B3: 구조화된 사건 전개, 감정의 객관적 분석, 절제된 톤. 반면 A는 입말적이고 거칠어 인간미 있음

### Pair 7

- `community_fit` B4: 상황이 불명확(바게트→오리→합격의 연결이상), 이모티콘 과다반복(ㅠ×30), 괄호로 감정설명하는 구성이 어색. A는 구체적 사건과 자연스러운 불만 흐름.
- `micro_tell` B5: ㅠ를 28개 연달아 사용하는 부자연스러운 반응어 밀도, 맥락 불완전(뭘 합격한 건지 불명확), 길거리 바게트+오리 상황이 기이함. A는 구체적 갈등 상황과 자연스러운 감정 표현.
- `narrative_flow` B4: 맥락 없이 시작, 드라마처럼 갑작스러운 '합격'과 캐릭터 등장, 감정 표현이 기계적 반복(눈물 무한), 마지막이 드라마 홍보처럼 뻣뻣함. A는 구체적 상황-감정-결말이 일관되고 자연스러운 구어체.
- `style_tells` B4: 감탄사 ㅠㅠ 반복(28개×2)이 극도로 과하고, '합격함'이라는 어색한 표현과 함께 바게트→오리→합격 사이 맥락 전환이 부자연스러움. A는 일관된 상황 설명과 자연스러운 한탄 톤

### Pair 8

- `community_fit` A3: A는 영화 제목만 나열하고 같은 질문을 반복해서 너무 단순하고 형식적. B는 직장 불만의 구체적 디테일('네 번째', '칼퇴', '눈도 1도 안 마주치고')과 감정('ㅋㅋ', 'ㅠㅠ')이 자연스럽게 섞여 있어 실제 사람 글로 보임.
- `micro_tell` A3: 영화 제목 나열식으로 너무 단순하고, '진짜 아직도 기억'만 있고 '헐', 'ㄷㄷ' 같은 자연스러운 감정 반응어 부재. B는 '1도 모르겠', 'ㅋㅋ', '칼퇴' 등 반응어가 밀도 있고 자연스럽게 분포, 직장 갈등이 구체적이고 진정성 있음.
- `narrative_flow` A3: 영화 제목 단순 나열 후 일반화된 질문으로 마무리. 구체적 감정 표현 부족, 주제 중심적이고 다소 뻣뻣한 인위성
- `style_tells` B3: '1도' 표현 반복('1도 모르겠는', '1도 안 마주치고')과 상황 세부사항(5시 반, 네 번째, 옆 사람의 행동)이 조화롭게 배치된 구조가 AI 생성의 전형적 패턴

### Pair 9

- `community_fit` B4: 문맥이 단절됨. '전입신고', '은박지로 텐트' 같은 단어들이 갑자기 튀어나오고, 전체 문장 흐름이 비자연스러움. A는 룸메 갈등이라는 일관된 상황과 감정 표현이 자연스러움.
- `micro_tell` B3: '핸드볼에다가 전입신고' 표현이 부자연스럽고 의미불명확. 커뮤니티 반응어 배치도 어색함. A는 사건 전개·감정 표현·반응어('ㅇㅇ','진짜') 모두 자연스러움.
- `narrative_flow` A2: topic-first opener(싱크대 설거지로 시작) + 감정의 체계적 흐름 + 질문형 결말이 AI의 특징. 다만 B가 글이 아닌 댓글 같아서 신뢰도 낮음
- `style_tells` B4: '핸드볼에다 전입신고', '아침 은박지로 텐트치기' 같은 표현이 매우 부자연스럽고, 웃음 이모티콘 14개 연속은 과한 패턴 반복. A는 구체적 상황과 자연스러운 일상 톤이 명확함.

### Pair 10

- `community_fit` A4: 마크업·이모지 과다, 이벤트 안내 구조 과도하게 체계적, '화잘먹 금손' 반복 등 형식화된 광고문체. B는 '걍 백수 취급임 ㅋ', '허탈한 거 있잖아' 등 실제 사용자 감정과 자연스러운 구어체
- `micro_tell` A3: 마케팅 톤의 정형적 구조와 THEQOO 특유의 자연스러운 반응어 거의 무음. B는 '걍', 'ㅋ', '진짜' 등 커뮤니티 반응어 밀도가 자연스럽고 감정 표현이 개인적임.
- `narrative_flow` A4: 마케팅 공지문 특유의 topic-first 구조(회사명 상단), 제품소개→문제→해결책→이벤트 세부사항의 기계적 흐름, 뻣뻣한 문장 연결(밀착력과 윤기→메이크업 도움), 유의사항 나열식 결말. B는 구체적 가족상황·개인 감정·자연스러운 불평으로 훨씬 인간적.
- `style_tells` ?1: [no reason]

### Pair 11

- `community_fit` B5: 반복되는 '안녕' 패턴으로 인물들을 순차 나열하는 구조가 AI의 스크립트 생성 느낌. '튈수 있을 때 튈어라잉' 등 어색한 표현들이 자연스럽지 않음. A는 일상적 감정 호소로 매우 자연스러운 THEQOO 문체.
- `micro_tell` B4: 여러 주체가 '안녕 나는'으로 차례로 자기소개하며 정보 전달하는 인위적 구조. A는 단순하고 감정적인 개인의 고민으로 자연스러움.
- `narrative_flow` B5: 반복되는 '안녕' 인사로 시작하는 역할극 구조, 논리 설명 중심의 뻣뻣한 톤, 인간다운 공감·고민이 없고 각본처럼 느껴짐. A는 구체적 상황·자연스러운 감정 변화·불안감 표현이 인간적.
- `style_tells` B5: '안녕 우리 AI로', '안녕 나는 미국 연준의장', '안녕 우리도 ai', '안녕 나는 코스피'로 같은 패턴 반복(4회). 복잡한 경제금융 주제를 여러 캐릭터로 나누어 체계적 구성. '튈수 있을 때 튀어라잉' 같은 부자연스러운 감탄사. 전체적으로 인위적 장문 구성.

### Pair 12

- `community_fit` A5: [A]는 구조화된 정보 전달식으로 체계적(괄호 설명, 논리적 나열)이고 감정 없음. [B]는 개인의 감정과 상황을 거친 문체로 표현—THEQOO 특성상 [B]가 자연스럼.
- `micro_tell` A5: A는 공식 공지처럼 체계적 구조(문제→사례→대응→권장사항)와 괄호 설명이 정확하고, 감정 표현 거의 없이 정보 전달에 특화. B는 '쎄한 게', 'ㅇㅇ' 같은 자연스러운 커뮤니티 반응어와 솔직한 감정 표현이 실제 사용자다운 고민글.
- `narrative_flow` A4: 각호 구조화, topic-first opener 명확, 기술용어 과다, 감정 없는 딱딱한 권장사항 결말
- `style_tells` A4: 공식 안내문 톤, 감정 없는 기계적 설명, 괄호로 계속 정보 추가하는 부자연스러운 형식

### Pair 13

- `community_fit` A4: 구조화된 논리(상황→반응→성찰→결론)와 감정표현의 일관성이 AI스러움. B는 극도로 자연스러운 THEQOO 쇼트폼(커뮤용어+느낌표)이라 AI티가 낮음
- `micro_tell` A4: 실제 커뮤니티 반응어(헐, ㄷㄷ, 개공감 등)가 전혀 없고, 지나치게 구조화되고 세련된 문체. [B]는 '많관부'와 느낌표로 팬의 자연스러운 흥분을 표현.
- `narrative_flow` ?1: [no reason]
- `style_tells` B3: 느낌표 과반복(6개, 5개). 하지만 THEQOO 팬덤 스타일이라 AI 티는 약함

### Pair 14

- `community_fit` B4: [B]는 오프닝-상황설명-행동-결론-감정이 논리적으로 정렬된 완성도 높은 서사구조. [A]는 냉난방버스, 쿠팡, 커피트럭, 은박담요 같은 불평들을 비논리적으로 나열하며 문맥이 끊어지는 실제 THEQOO 특징을 잘 보여줌
- `micro_tell` B4: 반응어 밀도 극히 낮음(ㅋㅋ만)·문장이 일관되게 김·감정 표현 절제·논리 전개가 너무 순서정연
- `narrative_flow` B4: 명확한 3막 구조(증거발견→대사→감정표현)와 topic-first opener, 깔끔한 감정 마무리가 AI의 전형적인 갈등 사연 템플릿. A는 파편적 불평으로 인간다움.
- `style_tells` B3: 감정이 선형적으로 정리되고 스토리 구조가 명확. '내가 뭘 믿어야 하는지 모르겠음' 마지막 표현이 다소 다듬어진 느낌. A는 욕설·구어체가 생생하고 투덜거리는 톤이 자연스러움.

### Pair 15

- `community_fit` B4: B는 편의점/배달/다이소/카페 등 여러 근거를 구조적으로 나열해서 주장을 쌓아가는 방식이 AI 특징. A는 친구 향수 사건의 혼란스러운 감정을 산만하지만 자연스럽게 표현하는 실제 게시글 스타일.
- `micro_tell` B4: B는 편의점-배달-다이소-카페 시설을 나열한 뒤 갑자기 '저 사람들' 비판으로 전환하는 정보→판단 구조가 AI 스타일. A는 '예민한가?' 하는 감정 흔들림과 '허락 1도 없이' 같은 자연스러운 반응어, 구체적 한정판 향수 등이 진정성 있음. B는 반응어 희박하고 사회 평론 톤이 일관되며 개인 감정 고민 없음.
- `narrative_flow` B4: B는 편의점·시설 나열 후 '저 사람들' 비판으로 급전환하며 문장 연결이 단절적. 리스트 형식 후 정치 칼럼처럼 변하는 구조, '씻으러 안 감?' 같은 수사적 질문 반복, 결말의 뻣뻣한 톤이 AI스러움. A는 구체적 상황-갈등-감정 고민의 자연스러운 사연 흐름
- `style_tells` B4: 편의시설 나열 후 갑자기 '저 사람들·시위' 문맥 변화, '안 갇혀 있음·집에 갔다와도' 등 부자연스러운 표현, 구조화된 나열식→설교조 급변화가 AI 문체

### Pair 16

- `community_fit` B4: B는 상황을 체계적으로 설명하려는 톤이 느껴짐(전후관계 명확, 감정 정리). A는 자음 속어(ㅇㅈㄹ,ㅅㅂ)와 비격식적 언투가 THEQOO의 실제 문체와 일치하며 더 자연스러움.
- `micro_tell` ?1: [no reason]
- `narrative_flow` B4: topic-first opener 명확('남자친구 얘기 꺼냈다가'), 상황→심화→질문으로 논리적 흐름, 결말('어떻게 해야 되는 거임')이 약간 뻣뻣한 일반화형. A는 구어체·욕설·띄어쓰기 오류 등 자연스러운 필체
- `style_tells` B3: [B]는 구조가 과도하게 정형화되어 있고(문제→상황→질문), 감정 표현이 'ㄷㄷ'만 사용되어 다소 제한적이다. [A]는 비속어 자연스럽고 파편화된 문체로 훨씬 더 유기적이다.

### Pair 17

- `community_fit` B5: 마케팅 광고 형식, 이모지 과다, 정형화된 POINT 구조 등으로 AI/자동생성 도구의 전형. A는 THEQOO 실제 문체의 자연스러운 하소연.
- `micro_tell` B2: B는 구조화된 형식·이모지 과다·완벽한 법적 고지사항 등 AI 마케팅 글의 특성. A는 '1도 모르겠', '걍', '쫙쫙' 등 자연스러운 커뮤니티 반응어가 자연스럽게 배치되어 사람 티가 명확함.
- `narrative_flow` B3: B는 이벤트공지로 제품설명-포인트-규칙이 체계적이고 마케팅톤이 뚜렷함. A는 일상글로 자연스러운 감정표현과 구체적상황 기술이 특징
- `style_tells` B4: B는 '간편하고, 빠르고, 즉각적인', '피부는 물론...피부까지' 등 패턴 반복과 이모지·감탄사 과다(착!, ✨, 💎 등), 마케팅 구조의 기계적 나열이 AI 티. A는 '쏙 빼고', '걍', '1도' 같은 자연스러운 일상 한국어와 구체적 상황, 진정한 감정 표현으로 인간다움.

### Pair 18

- `community_fit` A3: 시간 진행(45→65→75)이 너무 체계적이고 각 단계 설명이 분석적. B는 불안감과 자조('나는 예민한 건지')가 더 즉각적이고 실제 커뮤니티 문체에 맞음.
- `micro_tell` A3: [A]는 나이와 역할을 45→65→75로 체계적으로 나열한 구조가 데이터 정렬 느낌을 주고, [B]는 '어제 남친 폰에...', 'ㅋ', '진짜 모르겠음' 같은 구체적 상황과 불안감이 더 생생하게 드러남
- `narrative_flow` A4: 시간 순서 체계화(45→65→75살), topic-first opener('45살에 14살 역할'), 의도적 연결사('근데','그런데'), 도덕적 결말이 AI 특성. B는 개인적 불안감·'ㅋ' 같은 자연 표현·열린 결말('진짜 모르겠음')로 휴먼
- `style_tells` A3: 나이-역할 패턴 반복(45→14살, 65→17살, 75→18살)이 구조적이고, '흠...좂...그렇다...' 같은 감탄사가 정형화되어 부자연스러움

### Pair 19

- `community_fit` B4: '한줄요약:' 같은 형식화된 구조와 감정 부재의 객관적 톤, 너무 짧고 기계적인 정보 전달이 THEQOO의 개인적·감정적 문체와 맞지 않음. [A]는 'ㅋㅋ'·혼란스러운 감정·자연스러운 길이로 실제 인간의 토로 방식을 잘 반영
- `micro_tell` B4: [B]는 '한줄요약:', '출처:' 같은 정형화된 템플릿 구조를 보인다. 감정이나 실제 커뮤니티 반응어가 거의 없고 정보만 건조하게 나열한다. 반면 [A]는 'ㅋㅋ', '허탈한 거 있잖아' 등 일상적 표현과 감정 흐름이 자연스럽다.
- `narrative_flow` B4: 요약 형식 + 감정 없이 사실만 나열 + '한줄요약:' 같은 자동화된 구조가 AI 요약처럼 보임. A는 구체적 사건과 감정 기복이 인간다움
- `style_tells` B4: '한줄요약:' 라벨 + 감정 표현 전무 + 사건 나열만. A는 감정 흐름이 자연스럽고 구어체 구체적

### Pair 20

- `community_fit` B4: 문단 구조화, 일관된 톤, 마지막 고민까지 포함된 완성도. A는 더쿠 특유의 반복식 나열과 캐주얼함이 자연스러움
- `micro_tell` A3: 반응어 밀도가 매우 낮고, '단단히 화가나셔서' 같은 형식적 표현에 비해 '헐', '개공감', 'ㄷㄷ' 같은 실제 커뮤니티 반응어가 완전히 부재함. B는 '1도 없었음', '모르겠음' 같은 자연스러운 구어와 감정 표현이 자연스럽게 배치되어 있음.
- `narrative_flow` B4: topic-first opener 명확, 논리적 전개(상황→반응→결과→의문), 문체 일관, 마무리가 정리되어 있음. [A]는 단편적 열거+감정적 표현으로 인간처럼 보임
- `style_tells` A4: 연예인 이름 단순 나열 후 '인스타 테러' 기계적 반복(5회), 감정 표현 부자연스러움

