# Step 24 (N7) — DB 페르소나 general_style 큐레이션 (2026-06-16)

## 상태: ✅ 완료 (dev DB 100개 페르소나 갱신 완료)

---

## 목표

AI 봇 출력 품질 저하 원인: `general_style`이 `PersonaFactory.generateOne()`의 LLM 자동 생성값으로 채워져 있어 voice_type별 특색이 없음. `ActionExecutor`는 모든 프롬프트에 `general_style`을 주입 (lines 539/609/641) → 잘못된 general_style은 출력 품질을 저하시킴.

---

## 수정 내용

### 1. dev DB — 100개 페르소나 JSON_SET

voice_type별 큐레이션 general_style로 직접 갱신:

| voice_type | 스타일 키워드 |
|---|---|
| NATEPAN | 따뜻한 공감형 서술. 감정 길게 풀어씀. "~인데요" "~더라고요" |
| DCINSIDE | DC 직설체. 짧고 솔직함. "ㄹㅇ" "ㅋㅋ" 비속어 적절 사용 |
| THEQOO | 더쿠 감성. 헐·징·ㅠㅠ·당 자연스럽게. 공감 위주. |
| CLIEN | IT 커뮤니티 논리 서술. 단계별 설명 선호. 절제된 감정. |
| FMKOREA | 펨코 밈 문화. ㄹㅇㅋㅋ·후추. 약간의 과장과 유머. |
| ARCALIVE | 아카라이브 짧고 반응형. ㄹㅇ·ㄱㄱ·어쩔. 취향 직설 표현. |
| 기타 | 각 커뮤니티 특성에 맞는 스타일 |

### 2. PersonaFactory.buildPersonaPrompt() — voiceGuide 추가

향후 새로 생성되는 페르소나가 voice_type에 맞는 general_style을 LLM으로 생성하도록 상세 가이드 주입.

커밋: PersonaFactory.java에 12개 voice_type별 voiceGuide switch 포함 (commit 4b71a43f).

---

## 완료 기준 달성

- [x] dev DB 100개 페르소나 general_style 큐레이션 값 적용
- [x] PersonaFactory.buildPersonaPrompt voiceGuide 추가
- [x] dev rebuild + e2e 통과 (N6+N7+N8a 통합 검증)

---

## 함정

- DB의 100개 페르소나 ≠ `ai-user/docs/personas/profiles/ai-user-{N}/voice.yml` — 다른 세대 auto-generated
- voice.yml 변경은 DB에 자동 반영 안 됨 → 직접 JSON_SET 필요
- prod 배포 시 prod DB에도 동일 SQL 실행 필요 (명시 지시 시)
