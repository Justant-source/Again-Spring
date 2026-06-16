# Step 43 — R4: CLIEN de-counselor + features 신규

## 일시
2026-06-17

## 결정
D-48: CLIEN general_style에서 상담사 레퍼토리 제거 + features 추가 (7개 신규 프로필)

## 한 일
- voice.yml 7개 프로필 신규 추가 (ai-user-036, 081~086)
  - `writing_quirks.features` 신규 정의:
    * "CLIEN 40~60대 경험담 기반. 저도 비슷한 상황이었는데요 패턴. 상담조(구조적/전문가/부부상담/정부지원/법적조치 권유) 어휘 절대 금지. 번호목록·단계별 안내 없음. 존댓말 유지, 자연스러운 2~3문단."
- general_style 지시문 추가: 【R4 de-counselor】 마커
- example_comments 7개 프로필 예제 교체
  - "부부 상담 받아보세요" → "저도 비슷한 경험이 있어서요" 패턴
  - "노동법 위반" → 감정 공감 우선
  - "정부 지원 프로그램" → 형제 경험담
- dev DB `personas` 테이블 5개 CLIEN 레코드 JSON_SET 적용
  - `$.writing_quirks.features` 추가
  - `$.general_style` de-counselor 지시 append
  - 완료: 5 rows updated

## 수치
- voice.yml 신규 프로필: 7개
- DB 직접 갱신: 5개 (active CLIEN personas)
- PersonaFactory target 포화로 신규 7개 비활성 (차후 DB 재시드 시 활성화)

## 함정
- PersonaFactory target=100 이미 포화
  - SeedLoader는 loadAndInsert() 호출 시 existsById 검사 스킵
  - 기존 5개 CLIEN 페르소나에만 JSON_SET 적용 가능

## 검증
- ActionExecutor.appendWritingQuirks가 이미 features 처리 (Java 변경 불필요)

## 다음
- R5: 신선 CLIEN 출력 생성 후 MAUVE 측정 (전/후 비교)
