# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 10:13:06
> survey: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r15-cond5-theqoo-claude-survey.md`
> answers: `/home/justant/Data/Again-Spring/.result/ai-user/blind/r15-cond5-theqoo-claude-answers-template.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **15.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | ai | human | human | X | community_fit=B5, narrative_flow=B4, style_tells=B3 |
| 2 | A | human | ai | human | X | community_fit=A4, narrative_flow=?1, style_tells=A3 |
| 3 | B | ai | human | human | X | community_fit=B4, narrative_flow=A2, style_tells=B4 |
| 4 | A | human | ai | human | X | community_fit=A4, narrative_flow=A2, style_tells=A3 |
| 5 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B4 |
| 6 | A | human | ai | human | X | community_fit=A4, narrative_flow=A5, style_tells=A3 |
| 7 | A | human | ai | human | X | community_fit=A3, narrative_flow=A3, style_tells=A2 |
| 8 | B | ai | human | human | X | community_fit=B5, narrative_flow=B5, style_tells=B3 |
| 9 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B4, style_tells=B3 |
| 10 | A | human | ai | human | X | community_fit=A5, narrative_flow=A4, style_tells=A4 |
| 11 | B | ai | human | human | X | community_fit=B4, narrative_flow=B4, style_tells=B3 |
| 12 | B | ai | human | human | X | community_fit=B3, narrative_flow=B4, style_tells=B3 |
| 13 | B | human | ai | ai | O | community_fit=B4, narrative_flow=B3, style_tells=B4 |
| 14 | B | ai | human | human | X | community_fit=B4, narrative_flow=B3, style_tells=B2 |
| 15 | A | human | ai | human | X | community_fit=A3, narrative_flow=B3, style_tells=A3 |
| 16 | A | human | ai | human | X | community_fit=A4, narrative_flow=A4, style_tells=A5 |
| 17 | A | human | ai | human | X | community_fit=A4, narrative_flow=A3, style_tells=B4 |
| 18 | A | ai | human | ai | O | community_fit=A4, narrative_flow=A3, style_tells=B3 |
| 19 | A | human | ai | human | X | community_fit=A3, narrative_flow=A4, style_tells=A2 |
| 20 | A | human | ai | human | X | community_fit=A3, narrative_flow=B3, style_tells=A3 |

## Judge Reasons

### Pair 1

- `community_fit` B5: 리스트 형식으로 기계적 반복('인스타 테러' 복제), 끝 문장 연결 어색, THEQOO 자연스러운 감정 표현 부족
- `narrative_flow` B4: 리스트 나열식 구조에 '이분들 뭔 죄인가요'로 끝나는 뻣뻣한 종결. 감정의 흐름이 끊겼고 개인적 구체 사건 부족.
- `style_tells` B3: 유명인 이름 나열 + '인스타 테러' 반복 패턴이 AI의 리스트 생성 방식처럼 보임. A는 감정의 흐름이 자연스럽고 구어적 표현이 일관됨

### Pair 2

- `community_fit` A4: 공식 공지 형식으로 매우 구조화되고 정형화됨. 번호 매기기, 예외 조항 같은 체계적 배열은 AI가 생성하기 용이한 형태. B는 감정 표현과 구어체('ㅠㅠ', '원래 이런 사람인지')가 자연스러워 실제 유저글처럼 보임.
- `narrative_flow` ?1: [no reason]
- `style_tells` A3: 정형화된 정책 공지 형식과 완벽한 1),예외1) 같은 구조화된 나열이 AI의 특징. B는 감정 표현이 자연스러운 구어체.

### Pair 3

- `community_fit` B4: 맥락 없이 뜬금없는 두 문장이 붙어있고, 웃음표시가 과하며, THEQOO의 자연스러운 길이와 말투에서 벗어남
- `narrative_flow` A2: 자연스러운 구어체이지만 감정-상황-의문의 구조가 AI 스타일로 정리됨. B는 맥락이 완전히 없어 평가 불가.
- `style_tells` B4: '핸드볼에다가 전입신고'·'은박지로 텐트치기' 등 맥락 부족한 표현, 부자연스러운 조합. A는 일관된 감정 흐름·자연스러운 구어체

### Pair 4

- `community_fit` A4: 마케팅 톤의 과도한 일관성, 완벽하게 정렬된 구조(POINT 1~3, EVENT 섹션), 형식화된 문체가 AI 생성의 특징. B는 자연스러운 감정 흐름(완전 굳었는데→말해야 하나→나 예민한가)과 구체적 상황 묘사, THEQOO 실제 고민글 톤과 일치
- `narrative_flow` A2: A는 광고/이벤트 공지로 체계적 포인트 구분과 정형화된 마케팅 톤이 뻣뻣함. B는 자연스러운 구어체('완전 굳었는데', 'ㅋㅋㅋ'), topic-first 오프닝, 감정의 자연스러운 흐름과 고민 중인 마무리가 인간다움.
- `style_tells` A3: 마케팅 공지문으로 POINT 반복, 이모지 과다, '착!' 패턴 반복이 AI 톤. B는 실제 일상 고민글처럼 감정 흐름과 캐주얼 표현이 자연스러움

### Pair 5

- `community_fit` B4: B는 정보가 과도하게 체계화되고 정렬되어 있음. 드라마 추천 부분의 일관된 포맷(제목/설명/이모지)과 '화질 저하 없이 움짤 올리는 방법' 같은 나열식 구조는 AI의 특징적 패턴. A는 감정적이고 즉각적인 입말체로 실제 THEQOO 사용자의 자연스러운 톤을 보임.
- `narrative_flow` B4: 리스트형 과도 구조화, 부자연스러운 문맥('으로 데려온' 연결 끊김), 정보 우선 나열 + 이모지 남발이 AI 패턴
- `style_tells` B4: 같은 의미 3회 반복, 문법 어색(으로 데려온), 이모지 과다, "6각인데 파워메인 잔잔마라" 같은 부자연스러운 표현

### Pair 6

- `community_fit` A4: 구조화된 형식('한줄요약:', '출처:')과 감정 부재로 정보 요약처럼 보임. B는 구어체 혼합, 감정 기복, 축약어('ㅇㅇ', 'ㅋㅋ', '걍', '뭐라')가 자연스러워 THEQOO 실제 문체와 일치
- `narrative_flow` A5: [A]는 '한줄요약:' 메타 레이블 후 요약 문장만 있고 실제 글의 흐름, 구체 사건, 감정 전개가 전무. [B]는 시간순 전개(지난달→현재→고민), 구어 표현(ㅋㅋ, 읽씹, 1도), 딜레마가 자연스러우며 한국 커뮤니티 톤이 일관됨.
- `style_tells` A3: '한줄요약', '출처' 라벨링과 과도하게 단순한 구조가 AI 정리 스타일. B는 입말 자연스럽고 감정 흐름이 살아있는 커뮤니티 글.

### Pair 7

- `community_fit` A3: 영화 제목이 4개 정확히 나열되어 구조가 너무 명확함. [B]는 감정 흐름과 구체적 상황이 자연스럽고 진정성 있음
- `narrative_flow` A3: 영화 제목들을 먼저 나열한 뒤 질문하는 topic-first 구조. 단순하고 논리적인 흐름이 AI답다. B는 배신 상황을 느슨한 구조로 풀어내 자연스러운 사연처럼 보임.
- `style_tells` A2: 영화 제목 나열식으로 설명이 거의 없고 과도하게 단순함. B는 시간순으로 상황과 감정을 자연스럽게 풀어냄.

### Pair 8

- `community_fit` B5: 공식 마케팅 톤과 구조화된 이벤트 공지 형식이 THEQOO의 일상적 감정 표현과 어울리지 않음. [A]는 짧고 자연스러운 일상의 한숨.
- `narrative_flow` B5: B는 이벤트 안내로 완벽하게 구조화되어 있고, EVENT 1/2 구분, 법적 유의사항의 반복적 강조(블랙리스트 3회, 후기 필수 2회), 뻣뻣한 마케팅 톤이 특징. A는 구체적 상황(팀장 5분전, 3시간 초과, 이번달 4번)과 자연스러운 감정 표현('ㅠㅠ', 열린결말)이 일관됨.
- `style_tells` B3: 형식적 톤, 브랜드명 반복, 마케팅 카피 톤이 AI 생성 문장처럼 보임. A는 자연스러운 구어체 일상 불만글

### Pair 9

- `community_fit` B4: [B]는 상황→감정→대화→의문으로 너무 체계적이고 감정 표현이 균형 잡혀 있음. [A]는 THEQOO 실제 문체처럼 파편적이고 산만하게 느껴짐
- `narrative_flow` B4: 명확한 상황-심리-대화-결론의 체계적 흐름, topic-first opener('지난 주말에 처음 갔는데'), 감정 상태 설명이 자연스러우면서도 정리된 구조(혼자→무시→피드백), 마지막 자조적 수사로 마무리되는 패턴이 AI 생성문의 전형적 특징을 보임. A는 단편적이고 댓글 같은 즉흥성이 강함.
- `style_tells` B3: 상황 설명이 체계적으로 구조화되어 있고, '내가' 주어가 반복되며, 감정을 점진적으로 전개. A는 감정이 흩어져 있고 오류까지 섞여 더 자연스러움.

### Pair 10

- `community_fit` A5: A는 공지사항 같은 공식 톤과 구조화된 설명으로 THEQOO 사용자 문체와 맞지 않음. B는 감정적이고 캐주얼한 반말로 커뮤니티 정상 글
- `narrative_flow` A4: 구조화된 정보문 톤. topic-first 시작(문제→예시→권장), 뻣뻣한 보안 가이드 결말. B는 일화적 시작(어제 연락 왔는데)과 감정적 자연스러운 끝(나는 뭐야 싶은).
- `style_tells` A4: 체계적 구조·공식어투·감정 전무. '권장합니다' '안전'처럼 기계적 설명이 과다. THEQOO의 자연스러운 구어체와 거리가 멀고 공지문처럼 느껴짐. B는 일상적 감정과 'ㄷㄷ' '걍' 같은 커뮤니티 표현이 자연스러움.

### Pair 11

- `community_fit` B4: 나열식 구성, '영상 20도'(문맥 불명확), '성인 키자니아야 뭐야'(어색한 끝말)이 AI 생성의 특징. A는 감정과 말투가 자연스럽고 THEQOO 톤이 일관됨
- `narrative_flow` B4: B는 나열식 구조로 각 항목을 topic-first로 제시한 후 비판을 붙이는 패턴(왜필요함?→왜필요하냐니깐?)이 반복되고, 결말이 '한풀이하는듯'으로 뻣뻣함. A는 감정의 흐름이 자연스럽고 '나는 지금 뭐임'처럼 휴먼한 여운으로 끝남.
- `style_tells` B3: 명사 나열→괄호 의견→다시 나열의 패턴 반복, 상황을 체계적으로 열거한 후 비판하는 구조가 AI 티

### Pair 12

- `community_fit` B3: [B]는 배우 나이와 역할을 체계적으로 나열하는 느낌이 강하고 '흠... 좀... 그렇다... 상태였는데' 같은 표현이 어색함. [A]는 '발 동동 구르고 있음' 같은 자연스러운 구어체와 실제 고민이 잘 드러남.
- `narrative_flow` B4: 시간 흐름을 45→65→75살로 체계적으로 정렬하고, '흠... 좀... 그렇다...'는 어색한 감정표현, 결말이 평가적 정보성('심하다, 너무한 거 아니냐고 난리')으로 끝남. A는 개인 고민이 자연스럽게 흘러나옴.
- `style_tells` B3: 배우 이력을 나열형으로 정렬한 구조와 '그렇다... 상태였는데' 같은 부자연스러운 표현, 시간순 반복 패턴('X살에 Y살 역할')이 AI 생성의 특징

### Pair 13

- `community_fit` B4: 구조화된 서사(상황→반복→고민→질문), 감정 변화의 세밀한 표현, 마지막 질문으로 마무리하는 방식이 AI 생성 글의 전형. A는 극도로 단편적이고 거친 표현으로 실제 사용자 글처럼 보임.
- `narrative_flow` B3: 명확한 구조(opener→구체예시2개→심리상태→결론)와 매끄러운 문장 연결이 체계적. [A]는 더 산만하고 생생한 욕설·상황이 혼재되어 자연스러움
- `style_tells` B4: 감정 구조가 문제-예시-예시-자기성찰로 완벽하게 정렬돼 있고, 감정 표현이 자연스러우면서도 인위적인 완성도를 보임. A는 더 단편적이고 감정이 분산됨.

### Pair 14

- `community_fit` B4: B는 이모티콘 반복(35개+)이 과도하고 문장이 너무 단순함. A는 구체적 시간(낮12시/밤11시) 설정과 심리 표현이 자연스럽고 미완성 톤도 현실감 있음
- `narrative_flow` B3: 오리 등장으로 갑작스럽게 화제 전환, '합격함'의 어색한 표현, TV 광고 같은 뻣뻣한 결말
- `style_tells` B2: 감탄사 반복(ㅠ 27개)이 과하고 패턴화됨. 실제 글이지만 AI의 감정 표현 반복이 약간 도드라짐

### Pair 15

- `community_fit` A3: 연예 정보+관심 요청의 반복적 템플릿 패턴. [B]는 감정의 기복·주저함·무력감이 인간답게 흐름
- `narrative_flow` B3: 배경 설명에서 질문까지 매우 체계적으로 구조화됨. 감정의 흐름이 일관적이고, 마지막 질문이 상담요청 형태로 너무 정돈된 사연 패턴을 따름.
- `style_tells` A3: 과한 느낌표 반복(6개, 5개)과 '약간 무서움 주의' 같은 부자연스러운 문장 구조. 팬글에서도 흔하지만 AI의 단순한 패턴 생성으로 보임. B는 구체적인 상황과 자연스러운 감정 표현.

### Pair 16

- `community_fit` A4: A는 여러 인물('안녕 우리는...', '안녕 나는...')이 반복적으로 자신을 소개하며 경제상황을 체계적으로 설명하는 구조로 인위적. B는 직장 갈등 불평글로 'ㄷㄷ', 감정적 표현이 THEQOO 일상 호소 문체와 일치.
- `narrative_flow` A4: 멀티 캐릭터 독백 형식으로 과도하게 구조화됨. '안녕 나는/우리는' 형태의 topic-first opener가 반복적으로 사용되고, 경제 상황을 논리적이지만 부자연스럽게 설명하는 톤이 AI처럼 느껴짐. B는 개인 경험담의 자연스러운 감정 흐름.
- `style_tells` A5: '안녕 우린...' '안녕 나는...' 반복 패턴, 여러 페르소나가 체계적으로 입장을 나열하는 구조, '튈수 있을 때 튀어라잉' 같은 어색한 표현이 AI의 특성을 노출함

### Pair 17

- `community_fit` A4: A는 정보를 체계적으로 정렬하는 느낌. 반면 B는 감정의 자연스러운 흐름과 일상적 불만 표현이 THEQOO 여성커뮤 스타일과 정확히 맞음.
- `narrative_flow` A3: [A]는 주제부터 명확하게 세팅(리미널-팬덤)하고 사실-반전-설명으로 논리적 구조화. [B]는 구체적 가족 갈등 사건과 감정 호소가 자연스러운 인간 글 특징
- `style_tells` B4: 가족 이중잣대 구조가 너무 명확하고 체계적. 감정 전개(불이해→황당→소외감)와 마지막 문제 제시가 AI의 공감 유도 패턴

### Pair 18

- `community_fit` A4: A는 상담글처럼 문제-배경-결론이 정돈되고 자기 합리화 구조가 분석적. B는 '꾸덕파', '1번·2번', '시뻘겋고 잔뜩 졸여져서' 같은 THEQOO 특유의 빠르고 구체적인 표현이 훨씬 자연스러움
- `narrative_flow` A3: [A]는 상황→감정→반복→질문으로 구조화된 흐름. [B]는 단순 불만글로 감정 표현이 더 즉각적이고 자연스러움
- `style_tells` B3: 떡볶이 비주얼 설명이 과하게 자세하고('저렇게 시뻘겓고 잔뜩 졸여져서 고춧가루 보이는'), 감탄사 '슬픔...', '미치겟음ㅠㅠ'이 약간 부자연스럽게 연이음. A는 감정과 상황이 매우 자연스럽고 진정성 있음.

### Pair 19

- `community_fit` A3: [A]는 논리적 나열 구조(편의점→배달존→다이소→카페)가 체계적이고 단계적 근거 제시가 AI스럽다. [B]는 감정 표현('ㅋㅋ ㅠㅠ'), 중단된 말투('아 나는...'), 자연스러운 일상 대화가 THEQOO 실제 문체에 가깝다.
- `narrative_flow` A4: topic-first 구조(편의점→배달→다이소→카페→사람들)의 구조화된 나열, 구체적 감정/대사 부족, '실체가 보이는 것 같음'의 뻣뻣한 결말
- `style_tells` A2: 무엇보다 반복 + 리스트 구조화, 논리적 전개가 인공적

### Pair 20

- `community_fit` A3: '여전히'의 반복과 '짧...읍..지만' 표현이 의도적으로 만들어진 느낌. B는 구체적 상황·대화·감정이 자연스럽게 섞여 있음
- `narrative_flow` B3: 상황 설명이 너무 체계적(시간→패턴→반복 횟수→대사→감정→결말)이고 구조화됨. 결말이 '못하겠다고'로 다소 뻣뻣함. A는 짧지만 자연스러운 구어체와 이모지로 휴먼 느낌이 강함
- `style_tells` A3: 여전히 반복(여전히 건강히, 여전히 다리는)과 감탄사 배치가 부자연스러움

