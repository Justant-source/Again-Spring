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

2. **[crisis-modal.md](./crisis-modal.md)**
   - `CrisisResourceModal` 컴포넌트 동작
   - 위기 키워드 감지 로직
   - 핫라인 카드 구성 (1366, 1393, 132, 112, 1388, 1577-0199)
   - 접근성 (포커스 트랩, ESC 비활성화)
   - **권위본**: `../../shared/docs/policies/crisis-detection.md`

---

## 핵심 원칙

### 금지어 정책

- **Level 1** (법률): "과실비율" → "화해 기여도", "판결" → "결과", "가해자/피해자" → "화해 기여도 레이블"
- **Level 2** (진단명): "나르시시스트" → 구체적 행동 기술, "PTSD" → "깊은 상처"
- **Level 3** (판결): "이겼다/졌다" → 사용 금지, "헤어지세요" → 사용 금지

### 위기 감지

- **즉시 감지**: "때리", "폭행", "자살", "강간" 등 → 세션 중단 + 모달 표시
- **연락처**: 1366(여성긴급), 1393(생명의전화), 132(경찰), 112(신고), 1388(청소년), 1577-0199(학교폭력)
- **접근성**: 포커스 트랩, ESC 무시, 모달만으로 닫기 불가능

---

## 개발 체크리스트

### 새 페이지/컴포넌트 추가 시

- [ ] `npm run lint:words` 통과 (금지어 검사)
- [ ] 사용자 입력 필드에 `KeywordGuard` 컴포넌트 추가
- [ ] 위기 키워드 감지 → `CrisisResourceModal` 렌더 확인
- [ ] 테스트: "때리", "자살" 등 키워드 입력 시 모달 표시 확인

### 배포 전

- [ ] `npm run lint:words` 최종 확인
- [ ] 모든 금지어 대체 표현 사용 확인
- [ ] 위기 모달 팝업 테스트

---

## 정책 적용 흐름

```
사용자 입력 텍스트
    ↓
checkForbiddenWords(text) [클라이언트]
    ↓
  Level 1 감지?
    YES → 입력 차단 + 모달
    NO  → 계속
    ↓
  Level 2/3 감지?
    YES → 경고 배너 + 입력 허용
    NO  → 계속
    ↓
checkCrisisKeywords(text) [클라이언트]
    ↓
  위기 키워드 감지?
    YES → CrisisResourceModal 표시 + 세션 중단
    NO  → 입력 제출
    ↓
[BE로 전송]
    ↓
KeywordGuard [서버]
    ↓
  (동일한 규칙 재검사)
```

---

## 권위본 참조

이 디렉토리의 모든 문서는 shared 문서의 FE 구현 레이어입니다. 정책 변경은 항상 다음 순서로:

1. `../../shared/docs/policies/` 수정
2. `frontend/lib/constants/` 데이터 동기화
3. `frontend/docs/policies/` 구현 가이드 업데이트
4. (필요 시) BE 규칙도 함께 업데이트

