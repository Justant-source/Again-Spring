# AI-user 페르소나 다양성 v4 (persona-diversity-v4)

> **상태**: 진행 중 (Phase 1 — WP1~WP4 병렬 worktree 구현) · **마지막 갱신**: 2026-09-05
>
> 초기 작업 지시는 각 에이전트에게 gitignore 대상 경로로 직접 전달됐다. 이 파일은 그 계약의
> **git 추적 요약본**이다 — 전달용 원본이 사라져도 트랙 목적과 결정 사항은 이 파일에 남는다.
> 이 파일 자체가 이 트랙의 git 추적 권위본이다.

## 1. 왜 하는가

AI-user 150명의 문체·연령·상황이 좁게 수렴해 실제 사용자에게 "AI 티"가 났다.
V21까지의 `personas` 스키마는 `archetype`·`tier`·`voice_profile`(JSON)만 있고 연령·성별·
결혼·자녀 같은 정체성 축이 없어 서사가 20~50대를 가리지 않고 뒤섞였다(과거 글에 정년·손주·
환갑 서사가 섞여 있었던 게 그 증상). 이번 트랙은 페르소나 정체성 축을 스키마로 명시하고,
150명 쿼터를 축별로 균등 분배하고, 카테고리·시점 규칙을 강제하고, 문체 다양성을 게이트로
검증한다.

## 2. 트랙 구조 — WP1~WP4 병렬 worktree

| WP | 담당 | 소유 영역 |
|---|---|---|
| WP1 | 페르소나 스키마·쿼터·문체 축 | `V22__persona_identity_axes.sql`, `PersonaFactory`, `PersonaCard`, `AiUserSeedLoader`(java) |
| WP2 | 카테고리 비율·소스 골격 추출 | thread-plan 카테고리 믹서, `POST /v2/extract-skeleton`(llm 워커) |
| WP3 | 작성자·댓글자 선택 가중치 | 페르소나 선택/추첨 로직, `last_post_at`/`last_comment_at` 갱신 |
| WP4 (이 파일 작성자) | 정리 도구·게이트·설정·문서 | `ai-user/tools/purge_offtarget_posts.py`, `ai-user/tools/persona_gate_check.py`, 설정 정리, Doc-Sync |

각 WP는 별도 git worktree/브랜치(`wp1/persona-v4` 등)에서 작업하며, Fable이 Phase 2에서
병합·최종 문서화·게이트 재검증을, Phase 3~4에서 dev 검증 후 prod 반영을 담당한다.
에이전트는 dev DB만 읽고 쓴다 — prod DB 쓰기는 Fable 전용.

## 3. 결정 10개 요약

1. **정체성 축 신설**: `personas`에 `age_years`(23~49) · `gender`(M/F) · `marital`
   (SINGLE/DATING/ENGAGED/MARRIED) · `married_years` · `has_kids` · `job_type`(8종) ·
   `job_title` · `style_axes`(JSON) 컬럼 추가(`V22__persona_identity_axes.sql`, WP1).
   기존 `voice_profile.age`(밴드)·`gender`·`job`은 호환용으로 동시 갱신한다.
2. **150명 쿼터 그리드 고정**: 성별 75/75, 연령대 60/60/30(23-29/30-36/37-49), 결혼
   미혼 60·기혼 90(연령대별 15/45/30), 기혼 중 자녀 있음 45명, tier HEAVY20·REGULAR80·
   LIGHT50, voice_type NATEPAN75·BLIND75.
3. **`style_axes` 10축 균등 분포**: directness·affect·humor·stance·length·speech·
   emoticon·spelling·linebreak·profanity — 각 축 값이 코드로 강제된 균등 분포를 갖는다.
4. **`PersonaCard` 400자 요약으로 프롬프트 교체**: AI_POST·PAIRED·HUMAN_POST·human-reply
   전부 `personaCard`(문자열)를 쓰고, `voiceProfile` 전체 JSON은 더 이상 보내지 않는다
   (토큰 절감 겸 페르소나 일관성 강화).
5. **카테고리 비율 + 시점 제한 고정**: WORK 35%(전원, B시점 금지) · COUPLE 25%
   (`marital != MARRIED`만 작성자, B 허용) · FRIEND 15%(전원, B 허용) · FAMILY 15%
   (전원, B시점 금지, 시부모/처가는 MARRIED만) · MARRIED 10%(`marital == MARRIED`만).
   기존 `romanticShare`는 이 표로 대체.
6. **작성자·댓글자 선택 가중치 공식 고정**: `weight = tierW × (1 + hoursSinceLast/24)^1.5`
   (HEAVY 3.0 / REGULAR 1.5 / LIGHT 1.0). 결정론 정렬 금지, 매번 가중 비복원 추첨.
7. **소스 골격 JSON 계약**: `POST /v2/extract-skeleton`(Haiku)이 원문에서 category·역할·
   사건·claim 등을 뽑아 일반화한다. 고유명사·금액·날짜는 일반화, 원문 문장 그대로 담지 않음.
8. **기존 글 정리는 분류 후 승인제**: `purge_offtarget_posts.py --classify`로 50대 이상
   서사를 Haiku가 판정, `--apply`는 사람(Fable)이 JSONL을 검토한 뒤 dev에서 먼저 실행.
   prod는 `--i-mean-it` 없이 거부.
9. **게이트 a(분포)·b(다양성)·c(회전)를 배포 전 필수 검증으로 고정**: a·b는 실패 시
   종료 코드 1(배포 게이트), c(글쓰기 회전)는 참고용(배포 게이트 아님).
10. **죽은 설정 제거**: `AI_USER_PERSONA_TARGET`(참조 코드 0건, compose/코드 기본값 불일치)을
    compose·env·`OrchestratorProperties`에서 제거하고, 페르소나 목표치는 상수
    `PERSONA_COUNT = 150`으로 고정한다.

## 4. Phase 진행 상태

| Phase | 내용 | 상태 |
|---|---|---|
| 1 | WP1~WP4 병렬 worktree 구현 | 진행 중 — WP4(이 파일 작성자) 완료분: 도구 2종 + 설정 정리 + 문서 초안. WP1~WP3는 별도 worktree, 이 문서 작성 시점 기준 미확인 |
| 2 | Fable 병합 + 최종 문서화 + 게이트 재검증 | 대기 |
| 3 | dev 전수 검증 (`--classify` 전체, `--apply`, 게이트 a/b/c) | 대기 |
| 4 | prod 반영 (Fable 전용) | 대기 |

## 5. WP4 실측 메모 (2026-09-05)

- dev DB에 `V22__persona_identity_axes.sql` 아직 미적용 — `persona_gate_check.py --gate a`가
  "V22 컬럼이 없다" 메시지 + 종료 코드 2로 정확히 감지함을 확인.
- `purge_offtarget_posts.py --classify --limit 100` 실측: dev synthetic 글 396건 중 100건
  표본 분류 → OFF_TARGET 2건(2%), ERROR 0건. 전수 실행은 비용 절약을 위해 보류.
- `docker exec <container> mariadb -B`(batch 모드)는 출력 중 백슬래시를 한 번 더
  이스케이프해서 `JSON_ARRAYAGG` 결과를 깨뜨린다 — `--raw` 플래그로 해결(두 도구 모두 반영).
- `AI_USER_PERSONA_TARGET` 제거는 compose 2곳 + `env/.env.dev` + `env/.env.ai-user` +
  `OrchestratorProperties.personaTarget` 완료. `AiUserSeedLoader.java`(WP1 소유 파일)의
  두 호출부(`ensureCount(props.getPersonaTarget())`, 47행대·165행대)는 WP4가 건드리지
  않았다 — WP1이 상수로 교체하기 전까지 이 파일은 컴파일되지 않는다(Phase 2 병합 시 조율 필요).
