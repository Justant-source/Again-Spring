아래는 온라인 커뮤니티에 올라온 갈등 사연이다. 이 사연의 "뼈대"만 JSON으로 추출하라.
목적: 다른 사람이 이 뼈대만 보고 자기 인생 이야기로 완전히 새로 쓸 수 있어야 한다.

규칙
- 원문 문장·표현을 그대로 옮기지 마라. 모든 항목은 네 말로 다시 요약한다.
- 이름·회사명·지명·브랜드·정확한 금액·날짜·나이는 쓰지 마라. "몇백만 원대", "지난달", "30대 초반"처럼 뭉개라.
- 작성자가 누구와 무엇 때문에 부딪혔는지, 사건이 어떤 순서로 커졌는지, 무엇이 걸려 있는지, 양쪽 주장이 무엇인지에 집중하라.
- gray_zone에는 작성자 쪽에도 있을 법한 잘못이나 애매한 지점을 한 줄로 적어라(공감 투표가 갈려야 한다).
- b_side_viable: 상대방이 자기 입장에서 글을 올릴 만한 관계(연인·배우자·친구)면 true, 상사·부모·시댁처럼 상대가 글 올릴 리 없는 관계면 false.

출력: JSON 하나만. 키는 category, author_role, counterpart_role, relationship, incident, sequence(3~5개), stakes, author_claim, counterpart_claim, emotion, gray_zone, b_side_viable.
카테고리: {{CATEGORY}}
제목: {{TITLE}}
본문: {{CONTENT}}
