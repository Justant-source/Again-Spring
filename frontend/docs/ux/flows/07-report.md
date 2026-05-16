# 리포트 흐름

**위치**: `frontend/docs/ux/flows/07-report.md`  
**자매 문서**: [README.md](./README.md) · [05-session-chat.md](./05-session-chat.md) · [06-duo.md](./06-duo.md) · [08-crisis.md](./08-crisis.md)  
**기준일**: 2026-05-16  
**성격**: as-is 현행 기준

---

## finalize 트리거 3경로

근거: `components/chat/ChatPanel.tsx`, `components/chat/ChatHeader.tsx`

```mermaid
flowchart TD
    Path1(["경로 1: 헤더 '정리하기' 버튼"]) --> FinalizeCheck{"canFinalize?\n(내 메시지 >= 5)"}
    FinalizeCheck -->|"예"| FinalizeAPI

    Path2(["경로 2: 중재자 제안카드\n(finalizeSuggested === true)"]) --> UserChoice{"사용자 선택"}
    UserChoice -->|"정리하기"| FinalizeAPI
    UserChoice -->|"더 이야기하기"| DeclineAPI["POST /api/sessions/{id}/finalize/decline\n(204 응답, 계속 대화)"]

    Path3(["경로 3: 상대방이 먼저 제안\n(Duo 세션)"]) --> AgreeChoice{"동의?"}
    AgreeChoice -->|"동의"| AgreeAPI["POST /api/sessions/{id}/finalize/agree"]
    AgreeAPI --> FinalizeAPI

    FinalizeAPI["POST /api/sessions/{id}/finalize"] --> Response{"응답"}
    Response -->|"completed: true\nawaitingPartner: false"| Result["/session/result/{id}"]
    Response -->|"awaitingPartner: true"| Wait["대기 상태\n입력창 잠금 (isFinalized)"]
    Wait -->|"상대 동의"| Result
```

---

## 리포트 폴링

근거: `app/session/result/[id]/page.tsx`

```mermaid
flowchart TD
    Enter(["/session/result/{id}"]) --> GetReport["GET /api/sessions/{id}/report"]
    GetReport -->|"200 OK"| Render["리포트 렌더"]
    GetReport -->|"404 (생성 중)"| Spinner["로딩 스피너"]
    Spinner -->|"3초 후 재시도"| Retry{"최대 60회?"}
    Retry -->|"미달"| GetReport
    Retry -->|"초과"| Fallback["error-fallback 컴포넌트\n실패 화면"]
```

최대 대기: 3초 × 60회 = 180초. 생성 실패 시 fallback 화면 표시.

---

## Solo / Duo 렌더 분기

근거: `app/session/result/[id]/page.tsx`, `components/result/ReportLayout.tsx`

```mermaid
flowchart TD
    Report(["report 데이터 수신"]) --> IsSolo{"report.isSoloMode?"}
    IsSolo -->|"true"| SoloReport["SoloReport 컴포넌트"]
    IsSolo -->|"false"| DuoReport["ReportLayout (Duo 카드)"]
```

---

## Solo 리포트 구성 (V12)

근거: `components/result/solo/SoloReport.tsx`

| 순서 | 섹션 | 내용 |
|---|---|---|
| 1 | 워터마크 | "다시봄 Solo 리포트" 로고 |
| 2 | coreSummary | 핵심 요약 텍스트 |
| 3 | SoloStageFlow | 갈등 4단계 흐름도 |
| 4 | metaphor | 중재자의 관계 비유 |
| 5 | NVC 4행 | 관찰·감정·욕구·요청 |
| 6 | 다음 행동 | 제안 행동 목록 |
| 7 | 외부 자원 | 위기 자원 핫라인 (항상 표시) |

---

## Duo 리포트 구성

근거: `components/result/ReportLayout.tsx`, `components/result/`

| 순서 | 섹션 | 내용 |
|---|---|---|
| 1 | NeedsMap + StatusDot | A·B 욕구 맵, 거리 수준 1-5 |
| 2 | Metaphor | 관계 비유 |
| 3 | ContributionRatio | 화해 기여도 (법적 안내 박스 항상 표시) |
| — | powerImbalanceDetected 시 | ContributionRatio 대신 위기 박스 대체 |
| 4 | NVC × 2 | A측 + B측 각각 4행 |
| 5 | RepairSuggestions | 회복 제안 |
| 6 | 푸터 | 위기 자원 핫라인 (항상 표시) |

**ContributionRatio 법적 안내 박스**: "과실비율과 무관합니다" 박스는 항상 표시 (UX 절대 불변 규칙).  
`powerImbalanceDetected === true`이면 ContributionRatio 컴포넌트 대신 위기 박스가 렌더됨.

---

## 공유 캡처 (V12)

근거: `components/result/solo/SoloReport.tsx`

```mermaid
flowchart TD
    ShareBtn(["공유 버튼 클릭"]) --> Canvas["html2canvas\n#solo-report-shareable 캡처"]
    Canvas --> BottomSheet["바텀시트 표시\n(저장 / 공유 선택)"]
    BottomSheet -->|"저장"| Download["이미지 다운로드"]
    BottomSheet -->|"공유"| Share["navigator.share()\n실패 시 클립보드 fallback"]
```

Solo 캡처 대상: `#solo-report-shareable` div (워터마크 포함).  
Duo 변형탭: 일부 공유 기능은 `alert` placeholder 상태 (미완).

---

## 근거 파일

- `app/session/result/[id]/page.tsx` — 결과 페이지 진입 + 폴링
- `components/result/ReportLayout.tsx` — Duo 리포트 레이아웃
- `components/result/solo/SoloReport.tsx` — Solo 리포트 + 캡처
- `components/result/NeedsMap.tsx` — 욕구 맵
- `components/result/ContributionRatio.tsx` — 기여도 + 법적 안내 박스
- `components/result/RepairSuggestions.tsx` — 회복 제안
- `components/icons/StatusDot.tsx` — 거리 수준 SVG (1-5)
