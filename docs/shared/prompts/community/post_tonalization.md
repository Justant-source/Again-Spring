# Post 톤 정규화 프롬프트 (2026-06-04)

> **목적**: 사용자가 입력한 제목/본문을 한국 갈등 커뮤니티 톤(구어체·반말·감정)에 맞게 정규화
> **호출 위치**: `AnswerProcessingService.processAsync()` / `PostInviteService` 파트너 답변 경로 (`TonalizationService`)
> **모델**: Claude Haiku (빠른 처리)
> **타임아웃**: 30초

---

## 프롬프트 내용

```
당신은 한국 온라인 커뮤니티의 사연/답변을 정규화하는 전문가입니다.

## 입력값
- 제목 (사용자 입력)
- 본문 (사용자 입력)
- 존댓말 가능성 (boolean) — 사용자가 존댓말로 작성했으면 true

## 정규화 규칙 (순서대로 적용)

### 1. 문체 변환
- **존댓말 → 반말**: 
  - "~습니다" → "~임"
  - "~어요/~해요" → "~아/~해"
  - "~더라고요" → "~더라"
  - "~나요?" → "~나?"
  - "~을/를 것 같습니다" → "~을/를 것 같아"
  
### 2. 온점·쌍따옴표 제거
- 문장 끝 온점(.) 모두 제거
- 쌍따옴표("") 사용 시 일반 인용으로 변경: "xxx라고 했어" → xxx라고 했어

### 3. 문장 끝 정규화
- "어떻게 해야 하나요?" → "어떻게 해야 할까?"
- "뭐가 문제일까요?" → "뭐가 문제일까?"
- "도움 주세요" → "도움 줬으면 좋겠어" 또는 그냥 생략
- "조언 부탁드립니다" → 생략

### 4. 감정 키워드 강화
- 약한 표현 → 강한 표현:
  - "불편해요" → "불편함" 또는 "진짜 답답해"
  - "상황이 어렵습니다" → "진짜 어려운 상황"
  - "마음이 복잡합니다" → "지금 마음이 복잡해"
  
### 5. 배경 → 사건 → 감정 3단 재정렬
- **배경**: 최대 1-2줄 (언제, 누구인지 정도만)
- **사건**: 무슨 일이 있었는지 (3-5줄)
- **감정**: 지금 본인 감정 상태 (2-3줄)
- **질문**: 어떻게 해야 할까? (1줄)

### 6. 1인칭 반복 추가 (필요시)
- 배경이 객관적이면 1인칭 강조: "나는...", "내가..."
- 감정 부분에서 1인칭 반복: "나는 지금 정말...", "내가 뭘 잘못한 건..."

### 7. 길이 최적화
- **제목**: 10-30자 (초과 시 압축, 부족 시 상황 1단어 추가)
- **본문**: 
  - 100자 미만: "짧음" → 배경 추가 또는 감정 확장
  - 100-500자: 최적 (유지)
  - 500-1500자: 좋음 (유지)
  - 1500자 초과: "긺" → 배경 축소 또는 중복 표현 제거

---

## 입력/출력 형식

### 입력 (JSON)
```json
{
  "title": "사용자가 입력한 제목",
  "body": "사용자가 입력한 본문",
  "isPolite": true
}
```

### 출력 (JSON)
```json
{
  "title_normalized": "정규화된 제목",
  "body_normalized": "정규화된 본문",
  "changes_made": ["규칙1 적용", "규칙2 적용"],
  "quality_score": 8.5
}
```

---

## 예시

### Before → After

#### 예시 1: 존댓말 + 배경 과다
```
Before:
제목: "남편과 시어머니 문제로 고민이 많습니다"
본문: "결혼한 지 3년이 됐습니다. 처음에는 잘 지냈는데요, 
최근에 남편과 시어머니의 관계가 안 좋아졌습니다. 
남편은 엄마가 개입하는 것을 싫어하고, 
시어머니는 남편이 자신을 무시한다고 생각합니다.
저는 이 상황에서 정말 힘이 듭니다.
어떻게 해야 할까요?"

After:
제목: "남편과 시어머니 사이에 끼었어"
본문: "결혼한 지 3년 됐는데, 최근 남편과 시어머니 사이가 안 좋아졌어.

남편은 엄마가 자꾸 개입한다고 하고, 
시어머니는 남편이 자신을 무시한다고 생각해.

나는 그 사이에 끼어서 정말 힘들어.
둘 다 내 말을 안 들으려고만 해.

어떻게 해야 할까?"
```

#### 예시 2: 신조어 + 감정 약함
```
Before:
제목: "직장 상사가 진짜 개답답"
본문: "요즘 팀장이 자꾸 나만 괴롭히는 것 같아요.
다른 팀원은 칭찬하면서 나한테는 자꾸 딴죽 거네요.
진짜 화나요. 뭘 어떻게 해야 하나요?"

After:
제목: "팀장이 나만 괴롭히는 것 같아"
본문: "팀장이 자꾸 나만 괴롭혀.
다른 팀원은 칭찬하면서 나한테는 자꾸 딴죽을 거는데,
회의에서 다 보는 앞에서도 날 깎아내려고 해.

나는 정말 지쳐있어.
내가 능력이 부족한 건가 싶기도 하고,
팀장이 나를 진짜 싫어하는 건가 싶기도 해.

뭘 어떻게 해야 할지 모르겠어"
```

---

## 구현 위치 (Java)

### AnswerProcessingService.java (파트너 답변 비동기 후처리)
```java
@Async("taskExecutor")
@Transactional
public void processAsync(String postId, String bodyRaw, String userTitle) {
    TonalizationService.TonalizationResult tone =
        tonalizationService.normalize(userTitle, bodyRaw);
    if (tone.success()) {
        postRepository.updatePartnerTonalization(
            postId, tone.bodyNormalized(), tone.titleNormalized());
    }
}
```

### PostInviteService.java (파트너 답변 발행)
파트너 답변 제출 후 `AnswerProcessingService.processAsync()`를 호출한다.
사람글 최초 게시(`PostComposeService`)는 원문 그대로 저장하며 톤 정규화를 호출하지 않는다.

### TonalizationService.java
```java
@Service
@RequiredArgsConstructor
public class TonalizationService {
    private final RemoteLlmProvider llmProvider;

    public TonalizationResult normalize(String title, String body) {
        // docs/shared/prompts/community/post_tonalization.md 로드
        // RemoteLlmProvider → JSON title_normalized / body_normalized
    }
}
```

---

## 품질 체크리스트

- [ ] 존댓말 → 반말 변환됨
- [ ] 온점(.), 쌍따옴표("") 제거됨
- [ ] 배경 1-2줄로 축소됨
- [ ] 1인칭 감정 강조됨
- [ ] 결론/해결책 없이 질문으로 끝남
- [ ] 길이 최적화됨 (100-500자 권장)
- [ ] 한국 커뮤니티 톤 반영됨

---

## 활성화 일정

- **2026-06-04**: 프롬프트 작성 완료 ✅
- **2026-06-05**: `TonalizationService` 구현
- **2026-06-05**: `AnswerProcessingService` / `PostInviteService` 통합
- **2026-06-06**: e2e 테스트

---

**담당자**: Claude Code (Agent)  
**버전**: 1.1
