# 위기 처리 흐름

**위치**: `docs/frontend/ux/flows/08-crisis.md`  
**자매 문서**: [README.md](./README.md) · [09-admin.md](./09-admin.md)  
**기준일**: 2026-08-10

---

## 광장형 위기 정책 개요

다시봄 광장형 모델에서 **사용자 입력(게시글·댓글)에는 어떤 표현 필터도 적용하지 않습니다.**
사용자가 쓴 텍스트의 표현 책임은 사용자에게 있으며, 플랫폼은 입력을 차단하지 않습니다.

`PostComposeService`·`CommentService`는 `CrisisKeywordGuard.scan`으로 위기 키워드(자살·자해·폭력)를
감지하면 `CrisisDetectedEvent`만 남기고 **게시는 계속**합니다. AI-user 본문에는 이 관제도 적용하지 않습니다.

대신 다음 두 가지 수단으로 위기 상황에 대응합니다:

1. **자동 위기 감지 + 관제** — `CrisisDetector`(키워드 매칭)가 위기 신호를 감지하면 `/admin/crisis`(30초 폴링)에 노출
2. **상시 핫라인 리소스** — `CrisisResourceModal` 언제든 접근 가능

---

## 절대 불변 규칙

> `CrisisResourceModal`은 **ESC·바깥 클릭으로 닫히지 않는다.**  
> backdrop onClick 핸들러·ESC keydown handler 추가 금지. 닫기 버튼 단일 경로.

---

## (A) 자동 위기 감지 흐름

근거: `safety/CrisisKeywordGuard.java`, `app/(admin)/admin/crisis/`

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: frontend/app/community/[id]/page.tsx -->
```mermaid
flowchart TD
    Post(["게시글/댓글 작성"]) --> Detect["CrisisKeywordGuard.scan()\n키워드 매칭"]
    Detect -->|"감지"| Log["감지 이벤트 기록"]
    Log --> AdminCrisis["감사 로그\n(com.againspring.safety.audit, 본문 내용 비노출)"]
```

- 위기 모니터 본문 비노출: 프라이버시 정책 준수
- 수동 "위기 마크" 설정 UI는 존재하지 않는다 — 감지는 전적으로 자동(키워드 매칭)이며 감사 로그 `com.againspring.safety.audit`로만 기록된다(2026-09-03 grep 확인: `/admin/crisis` 백엔드 엔드포인트는 존재하지 않는다. 과거 이 문서가 서술하던 `AdminCommunityController` 기반 수동 마크 PATCH 엔드포인트는 존재한 적이 없거나 이미 삭제된 죽은 참조였음 — 2026-07-30 확인)

---

## (B) 상시 핫라인 접근

근거: `components/shared/CrisisResourceModal.tsx`, `lib/constants/crisisResources.ts`

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: frontend/app/community/[id]/page.tsx -->
```mermaid
flowchart TD
    Trigger(["SOS 버튼 클릭\n또는 crisisFlag 게시글 진입"]) --> Modal["CrisisResourceModal 표시\n(body 스크롤 잠금)"]
    Modal --> Hotlines["핫라인 카드 목록"]
    Hotlines -->|"전화 클릭"| Call["tel: 링크 즉시 연결"]
    Modal -->|"닫기 버튼 (유일한 경로)"| Close["모달 닫힘"]
```

---

## 핫라인 목록

출처: `lib/constants/crisisResources.ts`

| 번호 | 기관 | 운영 |
|---|---|---|
| 1393 | 자살예방상담전화 | 24시간 |
| 1366 | 여성긴급전화 | 24시간 |
| 132 | 경찰 여성·청소년 상담 | — |
| 112 | 경찰 신고 | 24시간 |
| 1388 | 청소년 상담 | — |
| 1577-0199 | 학교폭력 신고 | — |

`tel:` 링크로 즉시 전화 연결. `sms:` 링크도 제공.

---

## 근거 파일

- `components/shared/CrisisResourceModal.tsx` — 핫라인 모달 (ESC/바깥클릭 없음)
- `lib/constants/crisisResources.ts` — 핫라인 데이터
- `lib/constants/forbiddenWords.ts` — CRISIS_KEYWORDS, WARNING_KEYWORDS (관리자 판단 참고용, 사용자 입력 차단 아님)
- `app/(admin)/admin/crisis/` — 위기 감지 관제 UI
