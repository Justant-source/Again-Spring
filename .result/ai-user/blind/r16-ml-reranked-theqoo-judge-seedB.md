# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 19:30:24
> survey: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey-fixed.md`
> answers: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r16-ml-reranked-theqoo-survey.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **25.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 2 | A | human | ai | human | X | community_fit=A4, micro_tell=A3, narrative_flow=A3, style_tells=B2 |
| 3 | B | ai | human | human | X | community_fit=B4, micro_tell=B3, narrative_flow=B4, style_tells=B3 |
| 4 | B | human | ai | ai | O | community_fit=B4, micro_tell=B3, narrative_flow=B4, style_tells=A3 |
| 5 | B | ai | human | human | X | community_fit=A2, micro_tell=B4, narrative_flow=B3, style_tells=B4 |
| 6 | B | human | ai | ai | O | community_fit=B4, micro_tell=B3, narrative_flow=B4, style_tells=B4 |
| 7 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B4 |
| 8 | A | human | ai | human | X | community_fit=A3, micro_tell=A3, narrative_flow=A4, style_tells=B2 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 10 | A | human | ai | human | X | community_fit=A4, micro_tell=?1, narrative_flow=A4, style_tells=A3 |
| 11 | B | ai | human | human | X | community_fit=B5, micro_tell=B4, narrative_flow=B4, style_tells=B5 |
| 12 | A | human | ai | human | X | community_fit=A4, micro_tell=A4, narrative_flow=A4, style_tells=A5 |
| 13 | B | ai | human | human | X | community_fit=A3, micro_tell=B3, narrative_flow=?1, style_tells=B2 |
| 14 | B | human | ai | ai | O | community_fit=B3, micro_tell=B3, narrative_flow=B4, style_tells=B2 |
| 15 | B | ai | human | human | X | community_fit=B3, micro_tell=B3, narrative_flow=B4, style_tells=B4 |
| 16 | B | human | ai | ai | O | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 17 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=A4, style_tells=B4 |
| 18 | A | human | ai | human | X | community_fit=A3, micro_tell=A4, narrative_flow=A4, style_tells=A3 |
| 19 | B | ai | human | human | X | community_fit=B4, micro_tell=?1, narrative_flow=B2, style_tells=B2 |
| 20 | B | human | ai | ai | O | community_fit=B4, micro_tell=B3, narrative_flow=B3, style_tells=A5 |

## Judge Reasons

### Pair 1

- `community_fit` B4: 정보전달식 설명이 과다하고, 팬덤→사실→롯데콜라보 순서가 약간 인위적. A는 감정 표현이 훨씬 자연스러운 THEQOO 스타일
- `micro_tell` B4: [B]는 정보 소개→반전→개인의견 구조가 형식적이고, 앞부분 세련된 정보표현(롯데·작가·콜라보)과 뒤 구어체(ㅈㄴ)의 톤 불일치가 큼. [A]는 피해감정 축적이 자연스럽고 구어체 일관성 높음
- `narrative_flow` B4: B는 정보 나열식 오프닝(리미널 팬덤 → 잠실역 → 롯데 콜라보)으로 시작해 감정이 뒤에 따라옴. A는 상황 → 감정 → 혼란이 자연스럽게 흘러감. B의 '존 버거만 작가와 콜라보한 것'은 설명조이고, 마지막 의문이 갑자기 튀어나와 부자연스러움.
- `style_tells` B3: 정보 나열식 구조(뉴스처럼) + '무서운데요' 감탄사 부자연스러움 + '콜라보한 것' 어색한 문체

### Pair 2

- `community_fit` A4: 공식 공지 형식으로 과도하게 구조화됨. 불릿포인트·예외조항·번호 매기기가 기계적. THEQOO 실제 글은 [B] 같은 구어체 감정 표현이 일반적. [A]는 AI 공식문서 생성 패턴
- `micro_tell` A3: 공식 공지 형식으로 체계화·구조화된 리스트와 예외사항 명시가 AI 생성 특징. B는 구체적 상황과 진정한 고민 톤이 실제 사람의 글로 보임.
- `narrative_flow` A3: 공지문으로 형식은 체계적이지만 '카테고리 고나리하거나', '시비걸듯 싸우지마시고', '스루해주시기 바랍니다' 같은 표현이 부자연스러우며, 문어체와 구어체가 어색하게 섞임
- `style_tells` B2: A는 공식 공지문으로 운영진이 작성했을 가능성 높음. B는 개인 고민글로 AI가 감정 표현과 일상 문체를 생성한 스타일로 보임.

### Pair 3

- `community_fit` B4: '비주얼', '꾸덕파라' 표현이 어색하고 형용사 나열이 부자연스러움. A는 직장 상황 불만이 즉흥적이고 감정표현이 진정성 있음
- `micro_tell` B3: 반응어가 끝에만 있고, 떡볶이 비주얼을 '시뻘겋고 잔뜩 졸여져서 고춧가루 보이는' 식으로 과하게 상세히 묘사한 부분이 자연스럽지 않음. A는 '1도', '멍함', '손도 안 댔거든 ㅋㅋㅋ' 같은 반응어가 자연스럽게 분포함.
- `narrative_flow` B4: topic-first opener('난 꾸덕파라')로 주제부터 던지고, 자신의 취향→지역상황→구체적 표현→감정이 너무 일관되게 정렬돼 있음. [A]는 사건 설명 중간에 배경을 끼워넣는 등 더 비선형적이고 자연스러움
- `style_tells` B3: '2번 비주얼'이라는 표현이 부자연스러움. 일상적으로 '2번 스타일'이라 하지 '비주얼'이라 하지 않음. '땡길때'도 띄어쓰기 오류. [A]는 '걍 멍함' 등 자연스러운 일상 톤.

### Pair 4

- `community_fit` B4: B는 감정을 논리적으로 구조화(엄마→아빠→패턴인식→자아의문)하고 길이가 정제된 반면, A는 짧고 캐주얼한 이모지 문체로 THEQOO 스타일과 맞음
- `micro_tell` B3: B가 사연의 표준형식(상황→감정→고민)을 체계적으로 따르고 '혼자 결정하고 혼자 납득하고'같은 감정 표현이 정교하며 모범적. A는 캐주얼한 이모지·입말체('짧...읍..지만')가 자연스러워 사람글로 보임.
- `narrative_flow` B4: topic-first opener로 상황을 명확히 설정한 후 엄마→아빠→자기성찰로 구조화됨. '항상 이렇게 결국'이라는 일반화와 '이게 맞는 건지 모르겠음'이라는 뻣뻣한 종결이 AI 특성.
- `style_tells` A3: 여전히 반복 + 짧...읍..지만 부자연스러운 표현

### Pair 5

- `community_fit` A2: 신조어 없이 순수 감정만 표현한 점과 감정의 일관성이 자연스럽지만 완벽함. AI가 팬덤 특화 신조어(싸패, 잔잔마라, 오쏀 등)를 섞기보다는 일반적인 감정 토로를 쓸 확률이 높음. B는 오히려 신조어와 팬덤 표현이 자연스러워 실제 팬의 글로 보임.
- `micro_tell` B4: B는 구조가 매우 정형화되고 템플릿화됨. 이모지 과다, '원덬이', '6각인데 파워메인 잔잔마라', '시목여진 평생 공조해', '오쏀 힐링드' 같은 문법 오류와 불명확한 용어들이 기계적 생성 특성을 보임. 반면 A는 구체적 상황(5년 친구, 읽씹 3회, 인스타 관찰)과 자연스러운 감정('진짜 모르겠거든', '완전 잘 지내던데', '걍 이대로')으로 인간적.
- `narrative_flow` B3: B는 주제 먼저 제시(topic-first), 드라마 목록 형식화·반복 패턴, 과도한 이모지, 어색한 마무리('추석 보내!') 등 AI 특징 다수. A는 자연스러운 구어체('진짜 모르겠거든', '걍')와 감정적 흐름.
- `style_tells` B4: 첫 문단의 '움짤 올리는 방법/업로드 방법/올리는 법' 같은 과한 반복, '무묭이' 같은 어색한 약자, '으로 데려온 원덬이가' 같은 부자연스러운 문장 구조, 과도한 이모지 연쇄(👇👇👇), '6각인데 파워메인 잔잔마라' 같은 이해 불가능한 표현들이 축적되어 있음. A는 일관되게 자연스러운 구어체 고민글.

### Pair 6

- `community_fit` B4: 문법이 정확하고 사건→근거→감정의 구조가 너무 깔끔함. 실제 THEQOO 글은 A처럼 더 비구조적이고 감정만 흩어짐
- `micro_tell` B3: [B]는 상황을 논리적으로 구조화하고 감정 표현이 성찰적이며 실제 커뮤니티 반응어(헐, 개공감, ㄷㄷ 등)가 없다. [A]는 '2찍들', 짧은 비문, 무질서한 나열로 실제 사용자 댓글의 특징을 보인다.
- `narrative_flow` B4: 구체적 사건 대비(10분 vs 1시간), 논리적 문제 정의, 자기 성찰이 단계적으로 전개되어 AI의 체계적 구조화가 드러남. A는 감정적 폭발로 인간답음.
- `style_tells` B4: B는 문장 구조가 과도하게 정제되고 상황→심리 분석이 완벽하게 체계화됨. A는 THEQOO 특성상 거친 감정 표현과 비정형 문법이 자연스러움. B의 '지난주에...근데...' 구조화와 심리 분석 깊이가 AI 티.

### Pair 7

- `community_fit` B4: 이모티콘 30개 연달아 반복, 문맥 단절(오리→드라마), 감정 과잉 표현이 부자연스러움
- `micro_tell` B4: B는 울음표(ㅠ) 27회 반복으로 감정을 과도하게 표현하고, 바게트-오리 장면과 '합격' 연결이 불명확하며, 마치 드라마 각본 같은 구조. A는 친구 늦음에 대한 자연스러운 일상 한탄으로 '1도 없었음', 'ㅇㅇ', 'ㅋㅋㅋ' 같은 실제 반응어 밀도가 적절함.
- `narrative_flow` B4: 문맥 비약 심함: '저러고 나서'로 시작하는데 앞 상황 없음. 오리·바게트·합격·감동이 비약적으로 연결되고, 마지막이 드라마 응원('다음주 마지막화·많관부')으로 끝나 사연과 무관함. 문장 연결이 뻣뻣하고 topic-first opener 구조 전혀 없음
- `style_tells` B4: 감탄사 ㅠ의 과도한 반복(30회+), 문맥 불명확(오리→합격 연결 부자연스러움), 드라마 예고체로 끝남

### Pair 8

- `community_fit` A3: 감정표현이 거의 없고 구조가 너무 깔끔함. THEQOO 글치고는 이모티콘과 감정 톤이 부자연스럽게 빠짐. B는 ㅋㅋ ㅠㅠ 같은 톤이 자연스럽고 직장 고민의 세부가 구체적임.
- `micro_tell` A3: A는 반응어가 거의 없고 영화 제목만 나열한 형태로 THEQOO의 전형적 감정표현 패턴이 부족함. B는 '1도 모르겠', 'ㅋㅋ', 'ㅠㅠ' 등 반응어와 감정이 자연스럽게 배치됨.
- `narrative_flow` A4: A는 제목만 나열하고 구체적 경험·감정 흐름 없이 질문으로 끝남. 반면 B는 상황-반복-고민의 자연스러운 흐름과 '1도 모르겠는', '눈도 1도' 같은 실제 감정표현이 풍부함. A의 나열식 구조와 뻣뻣한 마무리가 AI의 topic-first opener 패턴으로 보임.
- `style_tells` B2: 감정 표현이 너무 체계적으로 정돈되고 구조가 완성도 있어 보임. A는 커뮤니티 특유 용어('덬들은') 사용으로 사람다움이 더 두드러짐.

### Pair 9

- `community_fit` B4: 문맥이 거의 없고 문장들이 연결되지 않으며, '핸드볼에다가 전입신고'와 '은박지로 텐트치기' 같은 표현이 불명확해 AI의 부자연스러운 텍스트 생성처럼 보임. A는 구체적 상황과 감정 흐름이 자연스러운 인간 문체
- `micro_tell` B4: B는 핸드볼/전입신고/은박지텐트 간 맥락 연결이 완전히 끊어짐. 웃음표 과다(16개)해도 글 전체의 의도가 불명확하고 파편화됨. A는 룸메 갈등이라는 명확한 주제 아래 불만 표현이 일관되고 자연스러움.
- `narrative_flow` B4: B는 '핸드볼→전입신고→은박지 텐트'로 주제가 계속 튀어다니고 문장들이 맥락 없이 연결됨. A는 싱크대 설거지→룸메 반응→심리 상태로 사연의 흐름이 자연스럽고 감정 표현도 생생함
- `style_tells` B3: [B]는 문장 간 문맥 단절('핸드볼'과 '전입신고' 무관), 감탄사 과다 반복(ㅋ 15개), 논리적 연결 부재. [A]는 구체적 사건과 자연스러운 감정 표현 일관.

### Pair 10

- `community_fit` A4: 이벤트 구조가 완벽하게 정형화되어 있고 각 섹션이 균등하게 배치됨. 광고글이지만 THEQOO의 실제 사용자 글보다 더 '공식적'이고 자동화된 느낌. B는 감정의 미묘함과 자연스러운 구어체('걍', 'ㅋ', '진짜 허탈한')로 인간적
- `micro_tell` ?1: [no reason]
- `narrative_flow` A4: 과도한 구조화(이벤트 1·2·유의사항 카테고리화), 형식적 인사말('안녕하세요'), 뻣뻣한 마무리('많은 관심과 사랑 부탁드립니다✨')가 마케팅 AI의 특징을 보여줌. B는 개인 경험의 감정 흐름이 생생하고 자연스러운 절망감으로 끝남.
- `style_tells` A3: '화잘먹 금손'이 3회 반복되고 마케팅 톤으로 과하게 정형화된 구조. B는 구어체와 개인 감정이 진정성 있게 표현됨.

### Pair 11

- `community_fit` B5: B는 여러 캐릭터를 '안녕 나는' 형식으로 번갈아 소개하는 부자연스러운 구조, 논리적 흐름 부재, 의인화된 금융 상품이 THEQOO 문체와 맞지 않음. A는 일상 감정 표현으로 자연스러운 커뮤니티 글.
- `micro_tell` B4: 여러 기관을 의인화하는 체계적 구조, 정보의 논리적 배치, 극적 마무리가 일관됨. A는 감정의 모호함과 실제 고민의 산발성이 사람 티.
- `narrative_flow` B4: [B]는 '안녕 [주체]야' 패턴이 반복되고 여러 인물이 동일한 구조로 차례로 개입하는 인공적 구성. [A]는 일관된 화자, 자연스러운 사건 전개, 감정적 고민이 진정성 있게 흐름.
- `style_tells` B5: "안녕"으로 반복 시작하는 과한 패턴, 여러 페르소나를 나열하는 인위적 구조, "튈수 있을 때 튀어라잉" 같은 부자연스러운 어미

### Pair 12

- `community_fit` A4: [A]는 공식 공지처럼 구조화되고 정중하며 기술적으로 체계적. [B]는 감정적 구어체, 오타('쎄한'), 줄임말('ㅇㅇ')이 자연스럽고 THEQOO 일반 사용자 글 스타일
- `micro_tell` A4: 공식 공지문처럼 체계적·정중한 톤, 실제 THEQOO 사용자의 반응어('헐','ㄷㄷ' 등) 전무, 문장 구조가 너무 다듬어짐. [B]는 '쎄한 게', 'ㅇㅇ', 감정적 흐름이 자연스러운 사람 스타일.
- `narrative_flow` A4: 정보 전달 목적, 체계적 구조, 공식 톤, 뻣뻣한 가이드 결말이 AI 특징
- `style_tells` A5: A는 보안정보를 체계적으로 정리한 공식문체인데 THEQOO는 감정토로 커뮤니티. 구어체 '다 맞아맞아'와 감정적 질문이 있는 B가 인간의 자연스러운 글

### Pair 13

- `community_fit` A3: 개인 경험담은 THEQOO 주류 문체(연예인/드라마 정보)와 맞지 않고, 자조적 표현이 너무 논리적·완벽하게 구성됨. B는 팬 반응으로 자연스러움.
- `micro_tell` B3: A는 직장 내 경험의 심리적 흐름과 자조적 결말이 자연스럽고 진정성 있음. B는 팬 커뮤니티의 단순한 공지 패턴을 따르면서도 실제 감정 레이어나 상황 맥락이 거의 없고, THEQOO 글 특성상 어울리지 않는 구조와 느낌표 남발이 도드라짐
- `narrative_flow` ?1: [no reason]
- `style_tells` B2: 느낌표 7개 + '약간 무서움 주의' 문구가 부자연스러움. A는 자책과 패턴 인식이 자연스럽고 감정 흐름이 진정성 있음

### Pair 14

- `community_fit` B3: 상황 설명이 너무 논리적으로 정렬됨. THEQOO 글은 [A]처럼 감정을 거칠게 터뜨리는데 [B]는 구조화된 일화 형태로 약간 인위적.
- `micro_tell` B3: B는 상황 설명이 논리적으로 정연하고 실제 커뮤니티 비문·비속어가 적음. A는 비속어('ㅅㅂ', '쳐먹으러', '지들')와 짧은 비문이 자연스럽게 섞여 실제 사용자 분노가 드러남.
- `narrative_flow` B4: topic-first opener 명확(카톡 멍 → 사건 설명 → 증거 배치 → 뻣뻣한 결말), 논리구조가 일직선적이고 마지막 '뭘 믿어야 하는지 모르겠음'이 교과서적. A는 산만한 나열이면서도 현장감·즉흥성·자연스러운 결말로 휴먼티 강함
- `style_tells` B2: 구조가 너무 일관성 있고 정제됨. A는 감정 표현이 지저분하고 구체적인데, B는 상황-문제-반박-결론이 깔끔해서 AI가 만든 전형적 구조처럼 보임

### Pair 15

- `community_fit` B3: 나열식 구조가 과도하게 체계적이고 일부 문장 호흡('집에 갔다와도 됨 / 씻으러 안 감?')이 어색하게 끊김. A는 감정의 흐름이 자연스럽고 THEQOO 일상 고민글 특성과 일치
- `micro_tell` B3: 편의시설 나열 → 정치비판으로 이어지는 구조가 논리적으로 조립된 느낌. 거친 표현('염병 떠는', '안 갇혀 있음')이 의도적으로 배치되어 보임. 톤이 불안정하면서도 결론을 향해 단계적으로 구성된 설득 구조가 드러남. A는 실제 감정 기록처럼 자연스러움.
- `narrative_flow` B4: B는 편의점·배달·다이소 나열로 시작해 갑자기 시위 비판으로 주제 전환, 뻣뻣한 일반화 결말('실체가 보이는 것 같음'). A는 구체적 상황→자연스러운 감정 흐름(고민, 예민함)으로 인간다움
- `style_tells` B4: '무엇보다' 2회 반복, 체크리스트식 나열 구조('편의점 - 셀 수 없이 많음' 등), 문장 패턴의 일관성 부족, '그저 원래 집없고 돈 없는 사람들이'의 관찰적·분석적 톤이 AI의 논리 구성처럼 보임

### Pair 16

- `community_fit` B4: 문법 정확도가 높고 상황 설명이 너무 순차적·논리적. THEQOO 특유의 비정형 표현, 줄임말, 거친 톤이 부족해 AI스러움
- `micro_tell` B4: 문장이 지나치게 일관되고 체계적이며 반응어가 부족하다. A는 띄어쓰기 오류, 여러 반응어(ㅇㅈㄹ, ㅅㅂㅋㅋㅋ), 끊어진 문체로 더 자연스럽다.
- `narrative_flow` B4: 명확한 topic-first opener(남자친구 얘기)+시간순 논리전개+구체적 배경(2년)→뻣뻣한 결말 구조. A는 비체계적·감정적·거침으로 자연스러운 인간 글.
- `style_tells` B3: 체계적인 상황 설명과 논리적 서술 구조. 상황→맥락→조언구하기 순서의 완성도는 AI 생성문의 특징

### Pair 17

- `community_fit` B4: 구조화된 마케팅 톤, 이모지와 포인트 강조, 체계적 이벤트 안내 형식이 AI 생성 특징. A는 불규칙한 문단과 자연스러운 감정 표현(걍, 1도 모르겠는데)이 인간다움.
- `micro_tell` B4: 마케팅 톤 + 형식 과잉 + 이모지 과다 + 감정 인위적. A는 '걍', '1도 모르겠' 등 커뮤니티 반응어가 자연스럽고 비문도 살아있음.
- `narrative_flow` A4: 구체적 사건(선배가 제외, 보고서 차별)을 먼저 제시한 topic-first opener → 증거 쌓기 → "회사 가기 싫어지고 있음"의 뻣뻣한 결말이 AI 생성 사연의 전형 패턴. [B]는 기업 광고글로 비교 대상 아님.
- `style_tells` B4: B는 이모지 과다(💙🤍✨ 등), 'POINT 1·2·3' 기계적 열거, '간편하고 빠르고 즉각적' 반복식 표현이 AI 특징. A는 '쏙 빼고' '걍' '1도' 같은 자연스러운 구어체와 구체적 상황 묘사로 인간미 있음

### Pair 18

- `community_fit` A3: [A]는 배우 나이·역할 수를 시간순으로 체계적 나열하는 구조가 AI의 논리적 조직화 특징. [B]는 연애 불안감 감정표현이 매우 자연스러워 실제 사용자 글로 보임.
- `micro_tell` A4: 사건을 시간순으로 정렬하고 객관 설명하는 방식, 반응어('헐' 같은) 거의 없음, '그래서'가 인과관계 불명확하게 연결. B는 '진짜 모르겠음', 'ㅋ'처럼 일상적 표현·감정흐름이 자연스러움
- `narrative_flow` A4: 사건을 객관적으로 나열하고 외부 반응으로 끝내는 구조. [B]는 관계 불안감과 자기 의심이 자연스럽게 흐르며 '모르겠음'으로 진정성 있게 닫힘. [A]는 감정 결여, 화자의 내적 성찰 부재, 뻣뻣한 종결이 특징.
- `style_tells` A3: 나이대 반복(45→14→65→17→75→18) + '~해버림' 패턴 반복 + 논리적 구조 과도. B는 'ㅋ' 이모티콘과 감정 표현이 자연스러움

### Pair 19

- `community_fit` B4: [B]는 정보전달식 포맷, 초단문, 감정 표현 부재. [A]는 자연스러운 입말과 구체적 감정으로 실제 사용자 글 같음.
- `micro_tell` ?1: [no reason]
- `narrative_flow` B2: [B]는 한 줄 요약 형식으로 사건을 딱딱하게 설명하고 출처를 명시하는 방식이 형식적. [A]는 감정의 흐름, 자연스러운 구어체('막상', '허탈한 거 있잖아'), 구체적 상황 설명이 실제 사연답음. 다만 [B]는 사연이 아닌 요약본이라 비교 대상이 부정확.
- `style_tells` B2: 감정 표현 전무, 정보 나열만 있음. A는 자연스러운 구어체와 일관된 감정의 흐름이 특징

### Pair 20

- `community_fit` B4: 다시봄식 갈등 구조(구체 사건→감정변화→성찰적 질문). THEQOO는 직설·빠른 평가(A) 톤이 주인데, B는 상담형 사연으로 너무 정제됨.
- `micro_tell` B3: B는 상황→의문→질문이 정연하게 구조화되고 감정표현이 절제됨. A는 분노하는 실제 사람의 직관적 반응처럼 보임 (명언 나열 + 짧은 질문)
- `narrative_flow` B3: 상황→문제→질문의 기계적 구조, '거절하기도 뭐해서 걍'의 자동적 감정사, 결말의 뻣뻣한 이분법식 질문이 AI 톤
- `style_tells` A5: 패턴 반복(연예인+테러 나열)이 과하고 인위적. 문장 구조가 경직되어 있고 감정 표현이 부자연스러움. B는 실제 경험의 자연스러운 구어체

