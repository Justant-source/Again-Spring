# 갈등 카테고리

세션 생성 시 사용자가 선택하는 대/중/소 분류. LLM 프롬프트 컨텍스트로 주입되어 카테고리 특화 질문 생성.

## Source of truth

- FE: `frontend/lib/constants/categories.ts` (실제 사용 데이터)
- BE: `backend/.../domain/enums/RelationType.java`, `api/dto/.../SessionCategory.*`

## 데이터 구조

```typescript
interface CategoryTree { major: MajorCategory[] }
interface MajorCategory  { id; label; relationType; middles: MiddleCategory[] }
interface MiddleCategory { id; label; minors: MinorCategory[] }
interface MinorCategory  { id; label; allowCustomInput: boolean }
```

각 분류 끝에 항상 "직접 입력" 옵션 (`allowCustomInput: true`).

## 5개 + 1 메이저

| ID | 표시명 | RelationType |
|---|---|---|
| `couple` | 연인/썸 | `COUPLE` |
| `marriage` | 부부 | `MARRIAGE` |
| `friend` | 친구 | `FRIEND` |
| `family` | 가족 (형제·친척) | `FAMILY` |
| `parent_child` | 부모-자식 | `PARENT_CHILD` |
| `korean_specific` | 한국 고유 갈등 | `KOREAN_SPECIFIC` |

## 1. 연인/썸 (`couple`)

- 1-1. 연락·관심 문제
- 1-2. 시간·우선순위 문제
- 1-3. 돈·경제 문제
- 1-4. 미래·약속 문제
- 1-5. 가족·주변 관계 문제
- 1-6. 신뢰·거짓말 문제
- 1-7. 애정 표현 방식 차이
- 1-8. 생활 습관 차이
- 1-9. 기념일·이벤트 문제
- 1-10. 직접 입력

## 2. 부부 (`marriage`)

- 2-1. 가사·육아 분담
- 2-2. 돈·재정 문제
- 2-3. 시가·처가 문제
- 2-4. 자녀 관련 문제
- 2-5. 부부 관계·애정
- 2-6. 생활 습관·일상
- 2-7. 신뢰·외도 의심
- 2-8. 직업·커리어
- 2-9. 직접 입력

## 3. 친구 (`friend`)

- 3-1. 연락 소홀·거리감
- 3-2. 약속·신뢰 문제
- 3-3. 말실수·험담 문제
- 3-4. 금전 문제
- 3-5. 인간관계 문제
- 3-6. 가치관·정치·종교 차이
- 3-7. 경사·상사 문제
- 3-8. 직접 입력

## 4. 가족 (`family`)

- 4-1. 재산·상속 문제
- 4-2. 명절·가족모임 문제
- 4-3. 자녀 양육·교육 (형제 간)
- 4-4. 부모님 돌봄·간병
- 4-5. 생활 간섭 문제
- 4-6. 오래된 서운함·감정
- 4-7. 직접 입력

## 5. 부모-자식 (`parent_child`)

- 5-1. 진로·직업 문제
- 5-2. 결혼·연애 문제
- 5-3. 독립·자율성 문제
- 5-4. 돈·경제 문제
- 5-5. 생활 방식 간섭
- 5-6. 표현 방식·말투 문제
- 5-7. 형제자매와의 차별
- 5-8. 직접 입력

## 6. 한국 고유 (`korean_specific`)

Gottman 모델로 잡히지 않는 한국 관계 패턴. 학술 근거는 [psychology-model.md](./psychology-model.md) 참조.

| 코드 | 표시명 | 핵심 맥락 | AI 주의사항 |
|---|---|---|---|
| `in_law` | 시댁/처가 관련 | 시어머니/장모 갈등, 명절, 방문 빈도 | "시어머니가 잘못했네요" 같은 제3자 판단 절대 금지. 부부 사이 대처에만 집중 |
| `face` | 다른 사람 앞에서의 무시 | 친지/직장 동료 앞에서의 망신, SNS 공개 깎아내림 | 일반 모욕(경멸)과 구별 — 제3자 존재 맥락이 핵심 |
| `lingered` | 오래 묵힌 서운함 | 단일 사건 아닌 누적 감정 | 별도 입력 트랙으로 처리 — 단일 사건 인터뷰 적용 금지 |
| `generation` | 세대차/원가족 영향 | 양육관·경제관·결혼 의례 가치관 차이 | 어느 한쪽 가치관이 우월하다는 뉘앙스 절대 금지 |

## LLM 프롬프트 주입 형식

```
[CONTEXT]
관계 유형: {major.label}
갈등 카테고리: {middle.label} > {minor.label}
{customMinor 입력 시: 추가 설명: {customText}}
```

이 컨텍스트가 있어야 LLM이 카테고리 특화 후속 질문 생성.

## 변경 시 절차

1. `frontend/lib/constants/categories.ts` 갱신
2. 신규 `relationType` 추가 시 `backend/.../domain/enums/RelationType.java`도 추가
3. 본 문서 갱신
4. 관련 프롬프트 (`shared/prompts/relations/*.md`)에 카테고리 인지 안내 보강 검토
