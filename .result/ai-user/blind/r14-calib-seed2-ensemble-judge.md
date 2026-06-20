# Ensemble Blind Judge — THEQOO
> 생성: 2026-06-20 11:05:22
> survey: `../blind/r14-cond5-theqoo-survey.md`
> answers: `../blind/r14-cond5-theqoo-answers-template.json`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **20**
- proxy accuracy: **30.0%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
| 1 | B | human | ai | ai | O | community_fit=B4, micro_tell=?1, narrative_flow=B4, style_tells=B3 |
| 2 | A | ai | human | ai | O | community_fit=?1, micro_tell=B3, narrative_flow=A4, style_tells=A3 |
| 3 | A | human | ai | human | X | community_fit=A4, micro_tell=B4, narrative_flow=A3, style_tells=A3 |
| 4 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B3 |
| 5 | B | human | ai | ai | O | community_fit=B4, micro_tell=B3, narrative_flow=B3, style_tells=A3 |
| 6 | B | ai | human | human | X | community_fit=B4, micro_tell=B4, narrative_flow=B4, style_tells=B2 |
| 7 | A | human | ai | human | X | community_fit=A4, micro_tell=A4, narrative_flow=A4, style_tells=A4 |
| 8 | B | human | ai | ai | O | community_fit=B4, micro_tell=?1, narrative_flow=B4, style_tells=B2 |
| 9 | B | ai | human | human | X | community_fit=B4, micro_tell=B2, narrative_flow=?1, style_tells=B2 |
| 10 | A | human | ai | human | X | community_fit=B4, micro_tell=A4, narrative_flow=B4, style_tells=A4 |
| 11 | A | human | ai | human | X | community_fit=A3, micro_tell=A3, narrative_flow=B4, style_tells=A4 |
| 12 | B | ai | human | human | X | community_fit=?1, micro_tell=B3, narrative_flow=A3, style_tells=B3 |
| 13 | B | ai | human | human | X | community_fit=B4, micro_tell=?1, narrative_flow=B3, style_tells=B3 |
| 14 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 15 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 16 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 17 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 18 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 19 | A | ai | human | ai | O | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |
| 20 | A | human | ai | human | X | community_fit=?1, micro_tell=?1, narrative_flow=?1, style_tells=?1 |

## Judge Reasons

### Pair 1

- `community_fit` B4: 상황-수치-감정이 체계적으로 배열되고 문장이 완성도 있음. THEQOO는 감정 중심으로 빠르게 끝내는데, B는 설명적 구조가 AI 생성처럼 보임
- `micro_tell` ?1: [no reason]
- `narrative_flow` B4: B는 topic-first 오프너('간병비 얼마나 드는지...')로 시작해 구체적 상황(월 150)→가족갈등→개인의 한탄으로 자연스럽게 흘러감. A는 단편적이고 문법이 끊겨있어('살길 2찍들도') 오히려 감정적 실제 글로 보임.
- `style_tells` B3: B가 상황을 단계적으로 전개하고 구체적 수치를 제시하는 체계적 형태. A는 더 감정적이고 비문법적인 인간의 화난 글로 보임.

### Pair 2

- `community_fit` ?1: [no reason]
- `micro_tell` B3: 커뮤니티 반응어와 자연스러운 비문이 거의 없고, 음식 설명이 너무 정제되고 정확함. A는 '1도 모르겠음', '개공감', 반어적 질문 등 자연스러운 사람 특징이 더 많음.
- `narrative_flow` A4: topic-first opener(5:5 비용 제안) + 배경 명확 + 구체적 숫자 나열(8만원, 8개월) + 논리적 흐름(상황→반응→사례→상대주장→의문) → AI 문장 구조. B는 개인 취향부터 시작해 감정적·자연스러운 불평
- `style_tells` A3: '나 냈고' 반복과 의문형('내가 이상한 건지 걔가 이상한 건지') 반복이 패턴성 띔. B는 '꾸덕파', '미치겟음ㅠㅠ' 등 자연스러운 입말과 구체적 선호도 표현

### Pair 3

- `community_fit` A4: 정보 덤프식 구조가 AI스럽고 THEQOO의 자조적·감정 중심 톤이 부족. B는 감정 흐름이 자연스럽고 사람의 혼란을 생생하게 표현함
- `micro_tell` B4: 관계 고민의 전형적 구조, 감정 표현 정형화, '내가 이상한 건지/걔가 무감각한 건지' 같은 자조적 의문이 단계적이고 인공적. A는 구체적 사건과 'ㅈㄴ' 같은 자연스러운 반응어 포함.
- `narrative_flow` A3: 주제 우선 오프닝(리미널스페이스), 정보 나열식 구조, 감정 표현 제한적. B는 감정 흐름과 자연스러운 자기회의가 더 인간다움.
- `style_tells` A3: '무서운데요'와 '맞냐'를 섞는 존댓글/반말 부조화 + '롯데에서 어린이날을 맞아 그래피티 아티스트 존 버거만 작가와 콜라보한 것' 같은 구조적 정보 정렬이 다소 인위적. [B]는 '1도 모르겠음', '버티는 건지' 등 심리적 불안감이 매우 자연스러움.

### Pair 4

- `community_fit` B4: [B]는 공지문처럼 형식적이고 구조화되어 있어 AI 티가 난다. [A]는 구어체·감정·비논리적 전개가 살아있어 THEQOO 실제 사용자 글처럼 자연스럽다.
- `micro_tell` B4: 공지사항의 과도한 기계성. 번호 체계의 완벽함, '참고하시면 됩니다'·'참고 부탁드립니다' 반복, 예외1)·예외2)의 딱딱함, 모든 항목이 균등하게 정렬된 형식미. 반면 A는 불규칙한 구어체('1도 모르겠', 'ㅠㅠ'), 구체적 상황 기억(블랙박스·네비·카페), 자연스러운 의심의 흐름으로 휴먼.
- `narrative_flow` B4: B는 topic-first ('정치 카테고리 추가'), 체계적 항목 분류(1,1),2), 뻣뻣한 결말('스루해주시기 바랍니다'). A는 자연스러운 사건 전개, 감정('ㅠㅠ'), 구어체('말할 게 뭐 있냐는 거야')로 사람 글이 명확.
- `style_tells` B3: B는 공식 공지문 형식과 계층적 구조가 AI 생성처럼 보이고, A는 개인 감정 흐름과 구체적 배경이 자연스러움

### Pair 5

- `community_fit` B4: 체계적 논리 전개, 감정 분석이 정제된 표현, 문장 완성도 높음. A는 산발적·비문·속어 자연스러움으로 실제 THEQOO 문체
- `micro_tell` B3: B는 감정 변화를 시간순으로 너무 논리적으로 설명하고, 마지막에 자문하는 형태로 끝나는 것이 상담글 템플릿 같음. A는 여러 불만을 산만하게 비난하는 톤이 실제 화풀이에 더 가까움.
- `narrative_flow` B3: B는 상황-발견-반응-추가불안-결말질문이 깔끔하게 정렬되어 있고, 감정을 표현하면서도 논리적으로 상황을 분석하는 구조가 체계적. A는 감정적이고 거칠며 문장이 불규칙해 실제 사람의 자연스러운 글처럼 보임.
- `style_tells` A3: "왜 필요함?" "왜 필요하냐니깐?" 같은 의문문 패턴 반복과 각 항목을 괄호로 정리하는 구조화된 나열식이 AI 티를 냄. B는 감정 표현과 혼란스러움이 더 자연스러움

### Pair 6

- `community_fit` B4: 과도한 이모지, 과도하게 체계화된 목록 형식, '못 본 무묭이들' 같은 어색한 표현이 AI 특징
- `micro_tell` B4: 과도한 구조화와 이모지 남발, 일부 문법 어색('싸패 아니예요', '파워메인 잔잔마라'). [A]는 자연스러운 구어와 진정한 감정 표현
- `narrative_flow` B4: B는 구조적 opener('못 본 무묭이들'), 부자연스러운 문법('시목여진 평생 공조해', '오쏀 힐링드'), 뻣뻣한 종결('건강하고 즐거운 추석 보내!')로 AI의 커뮤니티 스타일 모방 흔적이 명백. A는 감정 흐름과 심리 진행이 일관성 있고 자연스러움.
- `style_tells` B2: [B]는 👇👇👇, 이모지 과다 반복(💙💛💚, 🎹🎻 등), '으로 데려온' 같은 어색한 표현이 있음. [A]는 감정 표현이 자연스러운 구어체로 AI 티나 패턴 반복이 없음.

### Pair 7

- `community_fit` A4: [A]는 형식적 공지문 스타일로 체계화·구조화·설명문 톤이 강함. [B]는 새벽 카톡, 감정 기술, '헐', '뭐라고 해야 할지도' 같은 자연스러운 구어체로 실제 THEQOO 연애고민글 문체
- `micro_tell` A4: 공식 공지문처럼 체계적이고 감정 표현 전무. '헐' 같은 자연스러운 커뮤니티 반응어 부재. B는 '헐', '새벽 1시에', '폰 뒤집는 거' 등 구체적 디테일과 감정이 인간다운 고민글.
- `narrative_flow` A4: 공지문 같은 체계적 구조, 기계적 권장사항, 뻣뻣한 마무리(강화된 비밀번호 변경·최소 8자리 조건). [B]는 새벽 1시 카톡 구체적 디테일, 감정적 흐름, 자연스러운 질문 결말.
- `style_tells` A4: 공지문 형식의 구조화된 문체, 괄호로 예시/설명을 계층화한 패턴, 기술적 톤이 인공적. B는 새벽 1시 상황을 구체적으로 묘사하고 감정이 자연스럽게 흐름.

### Pair 8

- `community_fit` B4: 길이가 길고 갈등이 체계적으로 정렬되어 있으며, 감정 표현이 반복적으로 구조화된 점이 AI 특징. A는 너무 짧아서 판별 어려움.
- `micro_tell` ?1: [no reason]
- `narrative_flow` B4: 상황 설명이 체계적으로 구성되고 감정 표현이 균형잡혀 있음. 통장·숨기는·이상한 키워드 반복, 마지막 클로징이 인위적으로 느껴짐. A는 자연스러운 구어체 질문 형식.
- `style_tells` B2: 상황 설명이 체계적이고 질문 반복('왜','뭐가','이게') 패턴이 약간 과함. A는 영화 나열 후 자연스러운 한국식 감각('덬들은')으로 끝나 오히려 인간다움.

### Pair 9

- `community_fit` B4: B는 '한줄요약:', '출처:' 같은 인위적 포맷과 극도로 간결한 요약체 구조로 AI 처리처럼 보임. A는 감정 기복과 구체적 디테일, 자연스러운 구어체('헐', 'ㄷㄷ')가 THEQOO 실제 글처럼 일관됨.
- `micro_tell` B2: 너무 형식적이고 불완전하며 실제 커뮤니티 표현이 거의 없어 AI 생성 요약처럼 보임. A는 비문('미치겠는 게'), '헐'·'ㄷㄷ'·'1도 모르겠' 같은 표현이 자연스럽게 섞여있어 사람 글로 보임.
- `narrative_flow` ?1: [no reason]
- `style_tells` B2: 형식적 메타정보('한줄요약:', '출처:')와 개인감정 부재로 AI 요약처럼 보임. A는 '헐', 'ㄷㄷ' 등 자연스러운 감탄사와 구체적 사건들로 인간 글로 보임

### Pair 10

- `community_fit` B4: [B]의 문장 구조가 너무 정제되어 있고 시간 순서와 논리가 완벽하게 배치됨. 정보 전개가 효율적이고, 감정 표현도 일관성 있게 배치된 형태로 AI가 만든 긴 고민글 같음. [A]는 드라마 실시간 댓글의 감정 표현 반복으로 더 자연스러움.
- `micro_tell` A4: 이모티콘 30개 연속 반복은 부자연스럽고, 내용이 드라마 감상평처럼 지나치게 단순함. B는 구체적 사건, 불확실성, 자책이 섞여 있어 실제 사람의 글임
- `narrative_flow` B4: 배경→사건→의심→확인→결정→반응의 체계적 단계 진행. 불필요한 배경 설명(7살 차이, 어릴 때부터), 논리적 인과관계, 마지막 갈등 표현 등이 AI의 전형적 사연 작성 패턴
- `style_tells` A4: 과도한 'ㅠㅠ' 반복(30회 이상) + 내용 극단적 단순화(상황→감정 1줄) = 패턴 생성. B는 구체적 상황 전개, 자연스러운 감정 흐름

### Pair 11

- `community_fit` A3: 문맥 연결이 부자연스럽고(핸드볼→전입신고→은박지 텐트), 문장들이 서로 관계 없이 배열된 느낌. B는 감정의 흐름과 구체적 상황 설명이 자연스러움.
- `micro_tell` A3: 의미가 불명확하고 부자연스러움. '핸드볼 전입신고', '아침 은박지 텐트치기' 같은 구절이 실제 커뮤니티 반응을 모방하려다 실패한 AI처럼 보임. B는 '1도 못 했음'·'더 열받는 건' 같은 자연스러운 반응어와 일관된 심리적 서사로 사람의 글처럼 느껴짐.
- `narrative_flow` B4: 명확한 topic-first opener(구체적 시간·상황)로 시작, 일관된 서사 흐름(상황→문제→맥락→심리갈등), 감정 전개의 체계성, '솔직히 아직도 모르겠음'처럼 약간 뻣뻣한 결말. A는 문맥 부재로 평가 불가.
- `style_tells` A4: 문맥 불명확하고 '아침 은박지로 텐트치기' 같은 부자연스러운 표현, ㅋㅋ 과다반복. B는 논리적 흐름과 자연스러운 감정표현으로 인간적.

### Pair 12

- `community_fit` ?1: [no reason]
- `micro_tell` B3: '앨범 7월10일이래' 표현이 문법적으로 어색. 정보 제공(뮤비 주의)과 팬 감정이 섞여있는데 톤 일관성 약함. 배경설명 부재. AI가 자연스럽지 않게 생성했을 법한 구조.
- `narrative_flow` A3: 사연의 흐름이 상황-구체설명-반박-성찰 순으로 깔끔히 구조화되어 있고, 연차 숫자(15→11→4→3)가 논리적으로 정렬되어 있으며, 결말의 '취소해야/버텨야' 선택지 제시가 약간 뻣뻣한 AI 구조적 특성을 보임.
- `style_tells` B3: 과한 느낌표 반복과 짧은 구조. [A]는 구체적 상황과 개인의 고민이 자연스럽게 섞여있어 인간다운 반면, [B]의 '!!!!!!! 많관부!!!!!'는 과한 패턴 반복으로 보임

### Pair 13

- `community_fit` B4: 마케팅 글로 너무 구조화되고 정제됨. A는 감정적 혼란과 자연스러운 여성 톤이 진짜 같음
- `micro_tell` ?1: [no reason]
- `narrative_flow` B3: 과도하게 구조화된 광고 포맷, 반복되는 경직된 문장, 자연스럽지 않은 형식적 흐름. A는 감정이 생생하고 구체적 사건·자연스러운 자기반성으로 인간의 혼란이 명확함
- `style_tells` B3: 이모지 과다, 브랜드명·상품명 기계적 반복, 형식화된 구조(EVENT 1/2, 유의사항)로 인해 마케팅 자동화 텍스트로 보임. A는 자연스러운 감정 흐름과 구어체 표현으로 인간 작성 가능성 높음

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

