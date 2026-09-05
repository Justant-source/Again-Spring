# docs/_active/ — 진행 중인 작업 트랙

## 용도

이 디렉토리는 **진행 중인 다단계 작업 트랙의 ROADMAP + 상태**를 담는다.
`docs/`는 git 추적 + pre-commit 린트로 보호되는 SSOT이지만, `.result/` `.temp/` `.request/`는
**.gitignore 대상**이다. 그런데도 계획·경위·진행 상황 문서가 그 안에만 존재하는 일이 반복됐고,
그 결과 `.result/ai-user-v2/`처럼 **디렉토리 자체가 로컬 디스크에서 소실**된 전례가 있다
(2026-09-02 발견, `docs/ai-user/history.md` 참고).

**여기 있는 문서는 git으로 추적된다.** `.temp/` `.result/`에 계획·상태 문서를 두는 것은
금지다 — 유실 전례가 있다.

## 규칙

- **파일 하나 = 트랙 하나.** 파일명은 트랙 이름(kebab-case), 예: `sibom-character.md`.
- 각 파일은 상단에 다음 헤더를 둔다:
  - `상태`: 진행 중 / 보류 / 완료 대기
  - `마지막 갱신`: `YYYY-MM-DD`
- 트랙이 **완료되면**: 해당 모듈의 history/README로 핵심을 요약 승격한 뒤, 이 디렉토리에서
  해당 파일을 **삭제**한다. `docs/_active/`는 항상 "지금 진행 중인 것"만 담아야 한다.

## 현재 트랙

| 파일 | 트랙 | 상태 |
|---|---|---|
| [`sibom-character.md`](./sibom-character.md) | 시봄이 캐릭터 리파인 + 모션 + 31~60장 확장 | 진행 중 |
| [`persona-diversity-v4.md`](./persona-diversity-v4.md) | AI-user 페르소나 정체성 축·쿼터·게이트 재구성 | 진행 중 |
