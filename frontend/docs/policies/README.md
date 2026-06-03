# 정책 문서 (Policies)

프론트엔드에서 강제해야 할 서비스 정책들의 구현 가이드입니다. 정책의 권위본은 모두 `../../shared/docs/policies/` 에 있으며, 이 디렉토리는 FE 특화 구현 방법을 설명합니다.

---

## 목차

1. **[forbidden-words-lint.md](./forbidden-words-lint.md)**
   - `npm run lint:words` 스크립트 사용법
   - Level 1/2/3 금지어 분류
   - 위기 키워드 즉시 감지
   - CI/CD 통합
   - **권위본**: `../../shared/docs/policies/forbidden-words.md`


> 위기 처리 흐름: [`../ux/flows/08-crisis.md`](../ux/flows/08-crisis.md) 참조.

---

## 핵심 원칙

### 금지어 정책

- **Level 1** (법률): "과실비율" → "공감 비율", "판결" → "결과", "가해자/피해자" → "작성자/상대방"
- **Level 2** (진단명): "나르시시스트" → 구체적 행동 기술, "PTSD" → "깊은 상처"
- **Level 3** (판결): "이겼다/졌다" → 사용 금지, "헤어지세요" → 사용 금지

### 광장형 위기 처리

- **입력 필터 미적용**: 사용자가 게시글·댓글에 입력한 텍스트는 차단하지 않음
- **관리자 마크**: 관리자가 게시글에 `crisis flag` 설정 가능
- **상시 핫라인**: CrisisResourceModal 표시 (1366, 1393, 132 등)
- **접근성**: 포커스 트랩, ESC 무시, 명시적 버튼으로만 닫기 가능

---

## 개발 체크리스트

### 신규 AI 생성 컨텐츠 추가 시

- [ ] `npm run lint:words` 통과 (금지어 검사)
- [ ] 판결/처방/승패 표현 검토
- [ ] "AI 배심원", "AI 분석" 레이블 명시
- [ ] 색상 일관성 (초록=A, 붉은=B, 회색=중립)

### 배포 전

- [ ] `npm run lint:words` 최종 확인
- [ ] `npm run lint:emoji` 통과
- [ ] AI 출력에 금지 표현 없는지 최종 검증
- [ ] CrisisResourceModal이 필요 시 표시되는지 확인

---

## 정책 적용 흐름 (광장형)

```
[AI 생성 텍스트]
    ↓
checkForbiddenWords(text) [FE]
    ↓
  Level 1 금지어 감지?
    YES → 교체 또는 수정
    NO  → 계속
    ↓
[배심원 의견, 중립화 요약 표시]
    ├─ "AI 배심원" 레이블 필수
    ├─ 판결/처방 표현 제거됨
    └─ 공감 비율, 관점 용어 사용
    ↓
[사용자 입력 텍스트 (게시글/댓글)]
    ↓
[BE로 전송]
    ↓
KeywordGuard [서버]
    ↓
  위기 키워드 감지?
    YES → admin crisis flag 설정 가능
    NO  → 정상 저장
    ↓
[게시글 상세에 CrisisResourceModal 표시]
```

---

## 권위본 참조

이 디렉토리의 모든 문서는 shared 문서의 FE 구현 레이어입니다. 정책 변경은 항상 다음 순서로:

1. `../../shared/docs/policies/` 수정
2. `frontend/lib/constants/` 데이터 동기화
3. `frontend/docs/policies/` 구현 가이드 업데이트
4. (필요 시) BE 규칙도 함께 업데이트

