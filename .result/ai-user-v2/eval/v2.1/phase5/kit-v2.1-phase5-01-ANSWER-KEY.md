# 🔒 ANSWER KEY — v2.1-Phase5-01 (보안 분리 파일 — 평가자에게 공개 금지)

**⚠️ 이 파일을 평가자에게 보내지 마세요. 모든 평가 완료 후에만 열람.**

---

| 계정 번호 | 유형 | 출처 | 카테고리 |
|---|---|---|---|
| 1 | Human | 쓰니 (NATEPAN) | COUPLE |
| 2 | AI | ai-user-065@againspring.internal (내차안나와) | MARRIED |
| 3 | AI | ai-user-032@againspring.internal (햇살받는 햄스터) | FAMILY |
| 4 | Human | 쓰니 (NATEPAN) | FRIEND |
| 5 | AI | ai-user-060@againspring.internal (통장이텅장) | COUPLE |
| 6 | Human | ㅇㅇ (NATEPAN) | MARRIED |
| 7 | Human | 쓰니 (NATEPAN) | WORK |
| 8 | AI | ai-user-024@againspring.internal (뒹구는 코알라) | FRIEND |
| 9 | Human | 쓰니 (NATEPAN) | FAMILY |
| 10 | AI | ai-user-043@againspring.internal (Vibe2026) | WORK |

**배치 패턴**: H-A-A-H-A-H-H-A-H-A (교대 없음 · 연속 3개 없음 ✅)

---

**생성 일시**: 2026-06-21
**평가 키트**: v2.1-Phase5-01
**총 계정 수**: 10 (AI 5 + Human 5)

---

## 참고

- **AI**: Again-Spring dev DB의 `posts` 테이블에서 추출
- **Human**: Again-Spring dev DB의 `example_bank` 테이블에서 추출 (NATEPAN, author_id: '쓰니', 'ㅇㅇ')
- **글 선택**: 각 계정당 최신순 3개 글 사용 (created_at/posted_at DESC)
- **본문 길이**: 최대 500자 (HTML 태그 제거, 줄바꿈 정규화)

---

**🔐 보안**: 이 파일은 평가 완료 후에만 열람하세요.
