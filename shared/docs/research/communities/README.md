# 한국 커뮤니티 성향 분석 인덱스

AI 유저 시뮬레이션 페르소나의 말투·성향 근거 자료.
각 커뮤니티는 개별 MD 파일로 관리되며 **확장 가능한 구조**로 유지한다.

## 파일 목록

| 파일 | 커뮤니티 | 정치 성향 | 주 이용층 | 말투 유형 |
|---|---|---|---|---|
| [natepan.md](natepan.md) | 나이트판 | 중립→진보 | 여 10-30대 | NATEPAN |
| [blind.md](blind.md) | 블라인드 | 중립 | 직장인 20-40대 | BLIND |
| [dcinside.md](dcinside.md) | 디시인사이드 | 보수~극보수 | 남 20-30대 | DCINSIDE |
| [fmkorea.md](fmkorea.md) | FM코리아 | 보수 | 남 20-30대 | DCINSIDE |
| [bobaedream.md](bobaedream.md) | 보배드림 | 진보 (중년 남성) | 남 40-60대 | GENERAL |
| [ohmyhumor.md](ohmyhumor.md) | 오늘의유머 | 진보 (민주당계) | 남 30-40대 | GENERAL |
| [naver-news.md](naver-news.md) | 네이버 뉴스 댓글 | 보수 58.9% | 전 연령 | MIXED |
| [clien.md](clien.md) | 클리앙 | 진보+반페미 | IT 30-40대 | BLIND |

## 커뮤니티 추가 방법

1. `template.md`를 복사해서 `{커뮤니티명}.md` 생성
2. 각 섹션을 채운 후 위 테이블에 행 추가
3. 말투가 새 유형이면 `voice-templates/` 디렉토리에 `{TYPE}.md` 추가
4. 관련 페르소나의 `voice.yml`에 `community_references:` 항목 추가

## 관련 파일

- `../korean-community-patterns.md` — 통합 요약 보고서
- `voice-templates/` — 말투 유형별 LLM 프롬프트 가이드
- `../../personas/profiles/*/voice.yml` — 적용된 페르소나 파일

## 업데이트 이력

| 날짜 | 내용 |
|---|---|
| 2026-06-04 | 초기 8개 커뮤니티 분석 완료 |
