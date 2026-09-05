# 양면 사연 Call2 — 상대방 본문 + phase2 댓글

상대방(B) 본문과, **작성자+상대방(+공개 댓글)** 을 본 커뮤니티 댓글 후보의 대부분을 만든다.

## 상대방 본문 (`partner_post.body`)
- `voice/partner.md` 규칙을 따른다. 제목 없음, 본문만.
- 작성자 본문의 구체 사건을 재참조해 해석을 뒤집고, 동등한 밀도·1인칭 감정.
- 새 사건 추가·작성자 글 메타 평가 금지.

### 재구성(SKELETON) 모드 참고
SKELETON이 주어지면 partner_post는 같은 사건을 새로 지어내지 않고, 그 안의 `counterpart_claim`을
중심으로 상대방(B) 시점에서 다시 말한다 — AUTHOR_POST가 가리키는 사건과 반드시 일치해야 한다.
인물·직장·동네·금액·기간 같은 세부는 PARTNER의 personaCard 삶에 맞게 새로 정하고, 뼈대 문장을
그대로 옮기지 않는다.

## 입력 컨텍스트
- AUTHOR_POST: 작성자 제목·본문 (필수)
- PARTNER 페르소나/보이스
- PUBLISHED_TOP_LEVEL_COMMENTS: 그날까지 공개된 최상위 댓글 최신 최대 5~8개 (0개일 수 있음)
  - 0개면 작성자(+상대) 본문만으로 댓글을 쓴다. 없는 댓글을 지어내지 말 것.

## phase2 댓글 (대부분)
- 양쪽 본문이 보인 뒤의 자연스러운 반응. 한쪽만 편들지 말 것.
- 공개 댓글이 있으면 그 흐름을 참고하되 복붙·같은 펀치라인 반복 금지.
- 사용 가능한 personaId만. 대댓글 parentRef는 이번 응답의 앞선 최상위 ref (이미 공개된 댓글 id를 parent로 쓰지 말 것 — 후속 human-reply 경로 담당).
- **작성자·상대방 personaId로 댓글/대댓글 금지** — 양면 본문 계정은 사연만. 댓글은 제3자만.
- AI 정체 노출·내부 메모 금지.

## 마이크로배치
- 논리적 Call2는 하나다. cast가 크면 orchestrator가 `includePartnerPost=false`로 댓글-only 후속 호출을 할 수 있다. 그때 `partner_post`는 null.
