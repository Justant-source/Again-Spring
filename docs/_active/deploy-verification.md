# 배포 실물 검증 (E단계)

> **상태**: 완료 (2026-09-02, commit `1de5117a`) · **마지막 갱신**: 2026-09-02
>
> §3 작업 1~5 전부 완료. 실측: `gradlew test` 712 통과 · dev 배포 후
> `verify-deploy.sh` PASS=4/FAIL=0 (배포 **전** FAIL → **후** PASS 로 뒤집힘) ·
> e2e-realbe 전체 125 통과. 결과는 `docs/env/60-runtime/deployment.md` 에 반영했다.
> 이 파일은 아래 잔여 항목이 정리되면 삭제한다.
>
> **잔여**: e2e `14-B` 가 운영자 토글 값을 단언하다 막혔던 건 렌더 확인으로 바꿔
> 해소했다(값은 코드가 아니라 `system_setting` 런타임 상태다 — dev·prod 동일 확인).
> 같은 유형의 단언이 다른 spec 에 더 있는지는 미점검이다.

배포 후 "기능이 실제로 동작하는가"를 실물 데이터로 확인하는 절차를 만든다.
결정 경위는 2026-09-02 그릴링 세션. 스킬 운용 규칙은 `.claude/rules/skill-ops.md`.

---

## 1. 왜 필요한가

이 프로젝트의 반복 사고는 전부 **테스트와 헬스체크를 통과하면서 기능만 죽는** 유형이었다.

- 유입·UTM·세션 계측이 snake_case ↔ camelCase 불일치로 전량 유실
- X 통계가 빈 스냅샷으로 기존 데이터를 덮어씀
- `NEXT_PUBLIC_*` 빌드 인자 누락으로 계측 사망

### 근본 원인 — 헬스체크가 속이 비어 있다

`backend/src/main/java/com/againspring/api/HealthController.java` 는 상수 `status=UP` 만 반환한다.
**DB 조회가 없다.** 그런데 `docs/env/60-runtime/deployment.md` 는 배포 후 검증으로 이 엔드포인트
curl 하나만 안내한다 → DB가 죽어도, 계측이 죽어도 통과한다.

실제 컴포넌트 점검(DB `SELECT 1`, SMTP 등 4종)은
`backend/src/main/java/com/againspring/service/admin/SystemHealthService.java` 에 **이미 구현되어 있으나**
배포 절차 문서에 언급이 없다.

---

## 2. 확정 결정

**배포 래퍼 + e2e 양쪽.** 배포와 검증을 한 명령으로 물리적으로 묶어 분리 불가능하게 만든다.
dev에서 잡을 수 있는 것은 e2e spec으로 내려 기존 게이트(절대 규칙 #4)에 자동 편입하고,
prod 전용 확인은 래퍼가 맡는다.

---

## 3. 작업 범위

<!-- lint-docs: allow-missing-start -->

| # | 작업 | 내용 |
|---|---|---|
| 1 | 헬스 엔드포인트 보강 | `/api/health` 가 DB까지 보게 하거나, 배포 검증 경로를 `/api/admin/health/system` 으로 교체. **어느 쪽이든 배포 문서에 반영** |
| 2 | `scripts/verify-deploy.sh` 신규 | §4 검증 항목 실행. 실패 시 exit 1 |
| 3 | `scripts/deploy.sh` 래퍼 신규 | compose 기동 → 헬스 대기 → verify-deploy 자동 실행 (분리 불가) |
| 4 | e2e-realbe 신규 spec | 계측 필드가 빈 값으로 저장되지 않는지 · 번들에 빌드 주입값이 실제 박혔는지 |
| 5 | 문서 교체 | AGENTS.md 절대 규칙 #4 와 `docs/env/60-runtime/deployment.md` 의 배포 명령을 래퍼로 교체 |

<!-- lint-docs: allow-missing-end -->

---

## 4. 검증 항목 (실측 근거 확보 완료)

| # | 항목 | 확인 방법 | 실패 판정 |
|---|---|---|---|
| 1 | 방문 계측 필드 매핑 | `frontend/lib/api/visits.ts` 페이로드(camelCase) ↔ `backend/src/main/java/com/againspring/api/visits/PublicVisitController.java` DTO 대조 | 하나라도 불일치 시 Jackson이 조용히 null 처리 — 예외 없음 |
| 2 | 방문 이벤트 적재 | 최근 10분 `visit_events` 카운트 | 0건이면 파이프라인 사망 |
| 3 | **세션 키 채움** | 최근 10분 `visit_events` 중 `session_key` / `visitor_key` NULL 비율 | 전량 NULL이면 2026-08-29 snake/camel 사고 재발 |
| 4 | UTM 귀속 | UTM 파라미터로 접속 → 가입 → `users.acquisition_source` 확인 (`backend/src/main/java/com/againspring/service/acquisition/AcquisitionAttribution.java`) | NULL이면 귀속 배선 끊김 |
| 5 | 유입 퍼널 API | `GET /api/admin/marketing/stats/acquisition?days=1` | 방문했는데 `totalVisits=0` 이면 계측 실패 |
| 6 | 빌드 주입값 | 페이지 소스에서 `NEXT_PUBLIC_APP_URL` 값 문자열 검색 (Next.js가 번들에 리터럴 인라인) | dev 이미지에 prod 도메인이 박혀 있으면 빌드 인자 오주입 |
| 7 | 헬스 실질 점검 | `/api/admin/health/system` (DB `SELECT 1` 포함) | DB 컴포넌트 DOWN |
| 8 | 백그라운드 파이프라인 | `GET /api/admin/dashboard/pulse` · `GET /api/admin/ai-user/action-feed` | 배포 전후 값이 전혀 안 변하면 AI-user 틱·마케팅 잡 사망 |

---

## 5. 미확정 항목 — 2026-09-02 판정 완료

1. **카카오·네이버 FE 빌드 인자 = 죽은 인자로 확정. 제거함.**
   `frontend/lib/auth/oauth.ts` 가 `type Provider = 'google'` 이고 로그인 페이지에 두 버튼이
   아예 없다. authorize 리다이렉트를 만드는 코드 자체가 없어 도달 불가능한 경로였다.
   `frontend/Dockerfile` 과 dev/prod compose 의 `NEXT_PUBLIC_KAKAO_CLIENT_ID` ·
   `NEXT_PUBLIC_NAVER_CLIENT_ID` 를 제거했다.
   **BE 쪽 `KAKAO_CLIENT_ID` · `NAVER_CLIENT_ID` 는 유지한다** — `application.yml` 이 읽고
   `OAuthProviderService` 가 code 교환에 쓴다. FE UI 가 생기면 바로 살아나는 서버측 설정이다.

2. **`.env` 의 소셜 CLIENT_ID 빈 값 = 사고 아님.** dev·prod 둘 다 같은 상태이고, 1번대로
   카카오·네이버가 FE 에 미구현이라 시도되는 경로 자체가 없다. 조치 불필요.

3. **e2e 픽스처 우회 필터는 한 곳에만 있다 — 검증 쿼리 선택에 직접 영향.**
   `id NOT LIKE 'e2e%'` 는 `AcquisitionFunnelService` 에만 존재한다.
   `DailyStatsAggregatorService` · `PmfStatsService` · `DashboardOpsService` 는 e2e 도
   synthetic 도 거르지 않는다.
   → **신규가입 판정에는 `/api/admin/marketing/stats/acquisition` 만 쓴다.**
   `/api/admin/dashboard/pulse` 의 `totalUsers` · `todayNewUsers` 는 오염된 값이므로
   신규가입 근거로 쓰지 않는다(§4 의 8번 항목은 파이프라인 생존 확인 용도로만 쓴다).

---

## 6. 완료 조건

1~5번 작업이 끝나고 dev 에서 래퍼 실행이 실측으로 통과하면, 결과를
`docs/env/60-runtime/deployment.md` 에 반영한 뒤 **이 파일을 삭제한다.**
