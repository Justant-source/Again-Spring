# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 11:04:52
> survey: `../blind/r14-cond5-theqoo-survey.md`
> answers: `../blind/r14-cond5-theqoo-answers-template.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **25.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | human | ai | ai | O | community_fit=A3, micro_tell=B3, narrative_flow=B4, style_tells=B4 |
| 2 | B | ai | human | human | X | community_fit=A3, micro_tell=B3, narrative_flow=B3, style_tells=B4 |
| 3 | A | human | ai | human | X | community_fit=A4, micro_tell=A3, narrative_flow=B4, style_tells=B2 |
| 4 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=?1, style_tells=B4 |
| 5 | B | human | ai | ai | O | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B2 |
| 6 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 7 | A | human | ai | human | X | community_fit=A5, micro_tell=A4, narrative_flow=A4, style_tells=A4 |
| 8 | A | human | ai | human | X | community_fit=A3, micro_tell=B2, narrative_flow=B3, style_tells=A3 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B5, narrative_flow=B3, style_tells=A2 |
| 10 | A | human | ai | human | X | community_fit=A5, micro_tell=A3, narrative_flow=A4, style_tells=A3 |
| 11 | B | human | ai | ai | O | community_fit=B5, micro_tell=A3, narrative_flow=B4, style_tells=A4 |
| 12 | B | ai | human | human | X | community_fit=A3, micro_tell=B4, narrative_flow=?1, style_tells=B3 |
| 13 | B | ai | human | human | X | community_fit=B3, micro_tell=B4, narrative_flow=A3, style_tells=B5 |
| 14 | B | human | ai | ai | O | community_fit=B4, micro_tell=A3, narrative_flow=B4, style_tells=B3 |
| 15 | B | ai | human | human | X | community_fit=B4, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 16 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 17 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 18 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 19 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 20 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |

## Judge Reasons

### Pair 1

- `community_fit` A3: 문법 어색함(띄어쓰기 부족, 문장 연결 어색) + 감정 표현이 강제적. [B]는 구체적 상황과 자연스러운 어조가 일관됨
- `micro_tell` B3: B는 상황을 체계적으로 구성(배경→문제→대화→감정)하고 문장이 다듬어져 있음. A는 분노와 단편적 사례가 자연스럽게 터져나오고 비문 같은 구조가 실제 커뮤니티 글처럼 보임.
- `narrative_flow` B4: 질문으로 주제를 먼저 제시(topic-first opener)하고 상황을 논리적·순차적으로 전개하는 구조가 뚜렷. A는 파편화되고 맥락 불분명해 인간의 급한 호소처럼 보임.
- `style_tells` B4: 문장구조 완벽,감정흐름 순차적,띄어쓰기 정확. A는 타이핑실수,2찍들·염병들같은자연스러운온라인표현이 너무 인간다움

### Pair 2

- `community_fit` A3: 불평의 흐름이 너무 체계적으로 정리되어 있고, 배경-현재-의문의 3단 구조가 명확함. B는 개인적 취향이 구체적이고 즉흥적이라 더 자연스러움
- `micro_tell` B3: THEQOO 특화 반응어('개공감', '1도 모르겠') 밀도 차이. A는 감정 기복과 비문투, 자연스러운 반응어 분포. B는 반응어 거의 없고 '꾸덕파·1번·2번' 같은 표현과 '시뻘겋고 잔뜩 졸여져서' 구체 묘사가 정제된 느낌.
- `narrative_flow` B3: '지역은 대부분이 2번 비주얼이라 슬픔'의 비표준적 문법과 '저렇게 시뻘겣고 잔뜩 졸여져서' 같은 과하게 상세한 묘사가 AI의 어색한 문체 패턴으로 보임. A는 THEQOO의 자연스러운 연애 상담 형식.
- `style_tells` B4: B는 '1번·2번' 번호 체계로 구분하고 '저렇게 시뻘겓고 잔뜩 졸여져서 고춧가루 보이는'처럼 객관적으로 설명하려는 톤이 인위적. A는 구체적 사건과 감정 호소가 자연스러움.

### Pair 3

- `community_fit` A4: A는 팬덤정보를 너무 정확하게 배치하고 문체가 깔끔하게 정렬됨. B는 감정 반복, '1도 모르겠음' 같은 자연스러운 신조어로 실제 THEQOO 고민글처럼 보임
- `micro_tell` A3: '어린이날용 맞냐'의 문법이 어색하고, 전체 구조가 정보 전달에 집중된 느낌. [B]는 '1도 모르겠음', '근데'의 반복, 감정 누적이 자연스러운 커뮤니티 톤.
- `narrative_flow` B4: 심리 갈등을 체계적으로 정렬한 구조(이분법적 질문 제시→결말), topic-first opener 명확함, 문장 연결이 지나치게 매끄러움. A는 구어체 감정 표현('ㅈㄴ 무서운데요')이 인간다운 반응을 보임
- `style_tells` B2: 감정 호소가 구조화되어 있고 '내가 이상한 건지/걔가 무감각한 건지' 대조적 자문이 AI 패턴처럼 느껴짐. 하지만 둘 다 자연스러움

### Pair 4

- `community_fit` B4: 공지사항 형식이 너무 체계적이고 경어체('~부탁드립니다', '~참고하시면 됩니다')가 일관되게 반복되며, 구조화된 느낌이 자동 생성된 공고문처럼 보임. A는 구어체 감정 표현과 구체적 상황 설명이 실제 사용자 글처럼 자연스러움.
- `micro_tell` B4: 공지/정책 문서 형식, 반응어 전무, 과도하게 구조화된 번호·예외 조항. THEQOO 개인 글치고 관리자 톤이 너무 강하고 형식적. [A]는 감정·비문·'ㅠㅠ'·'1도 몰랐음' 등 사람 티가 뚜렷함.
- `narrative_flow` ?1: [no reason]
- `style_tells` B4: 공식 공지 형식의 정형화된 구조(번호 매기기, 예외 항목 분류)와 중립적인 설명체가 AI 생성 특성. A는 구체적 상황과 자연스러운 감정 표현으로 인간의 고민글.

### Pair 5

- `community_fit` B4: 상황 설명과 감정 전개가 체계적이고 논리적. 감정의 변화 과정('화/어이없음' 구분, '자기 의심')을 너무 섬세하게 표현해 구성 같음. THEQOO는 보통 더 단편적이고 직설적.
- `micro_tell` B4: B는 감정 호 전개·논리적 서술·자기 성찰이 너무 정갈하고 선형적. A는 중구난방 불평과 '지들', 'ㅅㅂ' 같은 실제 반응어 밀도가 자연스럽다.
- `narrative_flow` B4: 명확한 topic-first opener('걔 통장이 없음')로 시작해 시간순·논리순으로 정리. 감정 전개가 단계적이고 문장 연결이 체계적. A는 구어적·자유로운 나열식으로 휴먼 톤.
- `style_tells` B2: 감정을 과도하게 명제식으로 분석하고, 시간순 구성이 너무 체계적이며, 자신을 제3자처럼 관찰하는 톤('내가 되게 예민한 사람처럼')이 AI스러움. A는 구체적 물건 투덜거림과 비속어로 인간미 있음

### Pair 6

- `community_fit` B4: 형식적 리스트·정보 나열식 구조·문법 어색('으로 데려온')·이모지 과다. A는 감정 표현 자연스럽고 THEQOO 감성글 전형.
- `micro_tell` B4: [B]는 과도하게 구조화된 템플릿, 부자연스러운 문장('파워메인 색/빛연출 맛집'), 오류('시목여진'), 지시사항처럼 보이는 괄호 주석이 섞여있음. [A]는 '1도 이해가 안 됨', '걍 기분이 별로임' 같은 실제 반응어와 자연스러운 감정 흐름으로 사람다움.
- `narrative_flow` B4: [B]는 정보 나열식 구조와 화질 설명→드라마 소개로의 갑작스러운 주제 전환이 부자연스럽고, '으로 데려온 원덬이가' 같은 문법적 어색함이 있음. [A]는 감정의 흐름과 일관성 있는 사연 구조로 사람의 실제 경험담처럼 읽힘.
- `style_tells` B4: 같은 의미 3회 반복(움짤 방법), 이모지 과다·패턴화, 약자 남용('시목여진' 등 의미 불명확)으로 자동생성 느낌

### Pair 7

- `community_fit` A5: A는 공식 공지처럼 구조화되고 형식적이며, B는 구어체 감정 표현(헐, 어딨어)과 불규칙한 문장이 실제 THEQOO 사용자의 자연스러운 고민글
- `micro_tell` A4: A는 형식적 공지사항 스타일로 감정이 없고, B는 '헐'과 구어체('어딨어', '모르겠고')로 자연스러운 고민글
- `narrative_flow` A4: 공지사항 톤, topic-first 구조(타 사이트→더쿠→대응→권장), 뻣뻣한 결말('강화된 비밀번호로 변경하시는 것을 권장'+지시문), 정보 전달 중심. B는 감정과 자연스러운 흐름이 사람다움.
- `style_tells` A4: 공식 안내 톤, '비밀번호·변경·강화' 단어 반복, 비는 문체로 딱딱함. B는 감정적 구어체, 자연스러운 상황 서술

### Pair 8

- `community_fit` A3: [A]는 영화 제목 나열이라 패턴이 단순하고 AI가 생성하기 쉬운 구조. [B]는 구체적 상황·감정 기복·'개공감' 같은 일상 표현이 자연스러워 실제 글처럼 보임
- `micro_tell` B2: 통장 합치기는 커뮤니티 상담글의 과사용 주제이고, 시간순 구조화(본가 방문→대사→차 안에서)가 AI의 스토리 생성 패턴처럼 보임. 글 A는 단순 감상이라 특징이 없음.
- `narrative_flow` B3: 구체적이고 체계적인 상황 설명(배경→갈등→자기 의문)이 AI의 사연 생성 템플릿과 일치. 하지만 '개공감', '슬쩍 꺼냐고' 등 입말이 자연스러워 확신도는 낮음
- `style_tells` A3: 영화 제목만 4개 나열하고 매우 단순한 구조. 실제 글이라면 더 구체적 감정 표현이 있을 법한데, 템플릿처럼 딱딱함. [B]는 구체적 갈등 상황과 자연스러운 감정 흐름이 인간다움

### Pair 9

- `community_fit` B4: 정보 전달만 있고 감정이 거의 없으며 '한줄요약:' 형식이 너무 정형화됨. THEQOO 특성상 A처럼 감정적으로 늘어지는 구어체가 자연스러운데 B는 객관적으로 건조함.
- `micro_tell` B5: 감정표현 전무, 정보 요약식 구조 인위적. A는 '헐', 'ㄷㄷ', '1도 모르겠어', '걍' 등 실제 커뮤니티 반응어가 자연스럽게 분포, 감정 흐름 진정성
- `narrative_flow` B3: 정보 중심의 요약 형식, 감정 표현 부재, 구조화된 형식이 AI 생성 콘텐츠의 특징
- `style_tells` A2: 나는 X, 형은 Y 대조 구조가 반복적으로 나타나며, 감정 호소의 패턴이 약간 과도함

### Pair 10

- `community_fit` A5: 감정 표현(ㅠㅠ 26회 반복)이 과도하고 구체적 내용이 극도로 부족. 실제 THEQOO 드라마 감상글은 장면 설명이나 캐릭터 평가가 있는데, A는 내용 없이 감정만 반복되는 AI 패턴.
- `micro_tell` A3: 반복적인 ㅠ 사용이 패턴화되어 있고, 맥락이 불충분하며 감정만 반복되는 구조가 AI 생성물 같음. [B]는 구체적 디테일(7살 차이, 25만원, 마통)과 심리적 갈등이 현실적이고 반응어가 자연스럽게 배치됨.
- `narrative_flow` A4: 마지막 '다음주가 마지막화임! 마지막까지 소라와 진경 많관부!!!'가 드라마 후기처럼 뻣뻣함. 감정 반복(ㅠㅠ 과다)과 구조의 단순함이 AI 특징. B는 구체적 경험담의 자연스러운 감정 변화와 미결정 결말이 인간적.
- `style_tells` A3: 감정 표현이 과하고 패턴적. 눈물 이모지의 반복(ㅠㅠㅠ...)가 기계적이고, 드라마 감상글의 짧은 분량에 비해 감정 폭주가 부자연스러움

### Pair 11

- `community_fit` B5: B는 장문으로 정리되고 논리적 구조(상황→사건→대화→자기반성)가 일관되며, THEQOO 실제 문체의 충동성과 단문 구조와 맞지 않음. A는 비논리적이고 자조적인 THEQOO 실제 말투.
- `micro_tell` A3: 비문과 반응어 부족으로 자연스럽지 않음. B는 '1도 모르겠', '걍' 같은 반응어가 자연스럽게 배치되고 구체적 상황 설명이 진정해 보임.
- `narrative_flow` B4: B가 구체적인 시간·숫자들로 과도하게 구조화되어 있고, 감정 흐름이 명확하게 배열되며, 반복적인 논리('빠르게 하면 더 받고 / 잘하면 더 쌓이고')와 뻣뻣한 결말('솔직히 아직도 모르겠음')이 AI의 특징을 보임. A는 맥락 없이 이상해서 오히려 사람의 엉뚱한 댓글처럼 보임
- `style_tells` A4: 첫 문장이 의미 불명확하고 부자연스러움. '은박지로 텐트치기' 표현도 어색. B는 일관된 감정과 구체적 상황으로 자연스러운 실제 글처럼 보임

### Pair 12

- `community_fit` A3: 길이가 길고 문제·상황·반박·감정이 너무 체계적으로 구조화됨. 감정 표현(ㅠㅠ, 걍 등)은 자연스럽지만 전체 논리 흐름이 명확하고 일관됨. B는 극도로 짧고 팬덤 커뮤니티 특성상 훨씬 자연스러움
- `micro_tell` B4: 뮤비/앨범 정보만 전달하고 감정 근거가 부족함. 팬 커뮤니티 특유의 구체적 자조나 자연스러운 비문 없이 기계적으로 정보 나열하는 느낌. A는 직장 불만의 논리적 흐름과 '분위기가 뭔데 그게' 같은 자연스러운 중얼거림이 사람다움.
- `narrative_flow` ?1: [no reason]
- `style_tells` B3: 감탄사 11개 연속 반복(!!!!!!!!), 단순 정보만 있고 실제 감정·상황 묘사 없음. A는 구체성·감정 일관성 있어 사람 글에 가까움.

### Pair 13

- `community_fit` B3: 제품명·블랙리스트·후기 등 반복되는 마케팅 문구, 과도하게 구조화된 형식(EVENT 1/2 구분, 조건 나열), 딱딱한 톤이 AI 생성 특징. 글A는 감정적 반복과 자연스러운 구어체(헐, 멘탈 터짐)로 실제 사용자 글로 보임.
- `micro_tell` B4: 과도하게 구조화된 마케팅 카피, 공식 톤 일관성, 법적 공정위 문구 삽입이 사람의 자연스러운 커뮤니티 톤보다 자동 생성 마케팅 텍스트처럼 보임. A는 비문법적 감정 표현('멘탈 같이 터졌음', 반복된 '모르겠음')과 구체적 상황 회상이 인간다움.
- `narrative_flow` A3: 구체적 사건을 시간 순서대로 체계적 나열, 감정표현 일관성, 마지막 질문 형식이 AI 특징. 다만 '헐', '멘탈 터졌음' 등 자연스러운 구어체가 섞여 확신도 낮음
- `style_tells` B5: 마케팅 템플릿 구조, 제품명 반복, 딱딱한 정형 표현. A는 친구 끊김 상황의 자연스러운 감정 표현.

### Pair 14

- `community_fit` B4: 구조가 명확하고 감정 표현이 체계적이며 시간 순서가 일관되어 있어 AI 생성 콘텐츠의 특징. A는 문법이 어색하고 의미 불명확해서 오히려 인간의 산발적 댓글처럼 보임
- `micro_tell` A3: 맥락이 단편적이고 문장들이 연결되지 않으며, 커뮤니티 표현('ㅇㅈㄹ', 'ㅅㅂㅋㅋㅋ')이 자연스럽지 않게 삽입된 것처럼 보임. B는 '1도 모르겠음', '뭔가가 확 식는 느낌' 등 실제 감정과 구어체가 자연스럽게 배치됨
- `narrative_flow` B4: 구조화된 흐름, 명확한 topic-first opener('통장 공개하자고 한 게 걔 쪽이었거든'), 시간순 논리적 전개, 일반화된 질문으로 끝나는 뻣뻣한 결말. A는 단편적·감정적 분노로 보다 인간다움
- `style_tells` B3: 완벽한 시간순서 + 감정 변화의 논리적 단계성 + 상담글 정형화 패턴

### Pair 15

- `community_fit` B4: 문법이 불자연스러움: '안 갇혀 있음' '집에 갔다와도 됨' '원래 집없고' 등에서 오류. 반복적인 수사 구조('무엇보다')와 논리 흐름이 어색. A는 입말체로 자연스럽고 THEQOO 실제 문체와 일치.
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

