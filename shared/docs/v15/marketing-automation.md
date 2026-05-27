# V15 마케팅 자동화 — 전체 사양

**버전**: V15.9  
**대상**: dev 환경 전용 (prod 배포는 Q1/Q2/Q3 완료 후)  
**기준일**: 2026-05-25  
**관련 정책**: `marketing-dev-only-policy.md`, `../policies/marketing-copy.md`

---

## 워크플로우 요약

외부 사연(수동 수집) → 익명화(50%+ 재작성) → 7~10턴 Haiku 시뮬레이션 → V12 리포트 → 3채널 콘텐츠 자동 생성 → 플랫폼별 시각 자산 렌더링 → 3중 안전 검사 → 어드민 승인 → zip 다운로드 → 수동 발행

---

## 플랫폼 추상화 (V15.9)

### ContentGenerator 인터페이스

모든 플랫폼 생성기는 `ContentGenerator` 인터페이스를 구현합니다.

```java
// service/marketing/content/ContentGenerator.java
public interface ContentGenerator {
    String generate(GenerationContext ctx) throws Exception;
    MarketingContent.Platform supports();
}
```

Spring이 `List<ContentGenerator>`를 자동 주입하면 `ContentGeneratorRegistry`가 `Map<Platform, ContentGenerator>`로 변환합니다. 새 플랫폼을 추가하려면 `ContentGenerator` 구현체 1개 + `platform-descriptors.yml` 항목 추가만 하면 됩니다.

### GenerationContext

```java
// service/marketing/content/GenerationContext.java
public record GenerationContext(
    String simulationSummary,
    String relationType,
    PlatformDescriptor descriptor,
    String templateBody,               // PR3 템플릿 지원 (nullable)
    Map<String, String> templateVariables
) {
    public static GenerationContext of(...) { ... }
    public boolean hasTemplate() { return templateBody != null && !templateBody.isBlank(); }
}
```

### PlatformDescriptor YAML

플랫폼 메타데이터 권위본: `backend/src/main/resources/marketing/platform-descriptors.yml`

```yaml
platforms:
  - code: x
    displayName: "X (구 Twitter)"
    maxCharsPerUnit: 270
    maxUnits: 5
    hashtagCount: 3
    renderType: TEXT
    enabled: true
  - code: instagram
    displayName: Instagram
    maxCharsPerUnit: 300
    maxUnits: 1
    hashtagCount: 5
    renderType: CARD
    enabled: true
  - code: naver_blog
    displayName: 네이버블로그
    maxCharsPerUnit: 1200
    maxUnits: 1
    hashtagCount: 5
    renderType: MARKDOWN
    enabled: true
  - code: threads
    displayName: Threads
    maxCharsPerUnit: 300
    maxUnits: 1
    hashtagCount: 3
    renderType: TEXT
    enabled: false   # PR2에서 Generator 구현 후 true로 변경
  - code: facebook
    displayName: Facebook
    maxCharsPerUnit: 600
    maxUnits: 1
    hashtagCount: 2
    renderType: TEXT
    enabled: false   # PR2에서 Generator 구현 후 true로 변경
```

`PlatformDescriptorLoader`가 앱 시작 시 `@PostConstruct`에서 YAML을 로드하고, 5개 미만이면 `BeanInitializationException`으로 실패합니다.

### 주요 서비스 클래스

| 클래스 | 역할 |
|---|---|
| `ContentGenerator` (interface) | 플랫폼별 콘텐츠 생성 계약 (`GenerationOutput` 반환) |
| `GenerationOutput` (record) | bodyText, hashtags, structuredPayload Map |
| `GenerationContext` (record) | 생성에 필요한 모든 컨텍스트 |
| `PlatformDescriptor` (record) | YAML에서 로드한 플랫폼 메타데이터 |
| `PlatformDescriptorLoader` | YAML 로드 + 검증 |
| `ContentGeneratorRegistry` | Platform → Generator 매핑 레지스트리 |
| `XContentGenerator` | X 3~5 트윗 스레드 + `quoteCard` JSON 출력 |
| `InstagramContentGenerator` | 캡션 + 슬라이드 6~7장 JSON 출력 |
| `NaverBlogContentGenerator` | 마크다운 + 이미지 슬롯 마커 JSON 출력 |
| `PlatformContentRouter` | 진입점 (registry.resolve → generate) |
| `ContentGenerationExecutor` | 비동기 생성 + 이미지 composition |
| `ImageCompositionStrategy` (interface) | 플랫폼별 이미지 렌더링 전략 |
| `XImageStrategy` | 인용 카드 + 옵션 채팅 스크린샷 |
| `InstagramImageStrategy` | 카드뉴스 6~7장 일괄 렌더 |
| `NaverImageStrategy` | 마커별 렌더러 분기 + 마크다운 치환 |
| `ImageCompositionStrategyRegistry` | Platform → Strategy 매핑 레지스트리 |
| `KeyMomentSelector` | 채팅 키 모먼트 3개 추출 (휴리스틱) |
| `RenderedImage` (record) | filename, role, slot, alt, order |

---

## 채팅 UI 스크린샷 (V15.8)

마케팅 콘텐츠 생성 후 시뮬레이션 세션의 실제 대화를 다시봄 채팅 UI 스타일로 렌더링한 PNG를 자동으로 생성합니다.

### 흐름

```
ContentGenerationExecutor
  └─ simulation.getSessionId() != null
       └─ messageRepository.findBySessionId() (최대 5개)
       └─ ImageRenderClient.renderChatPreview()
            └─ POST http://marketing-renderer:9000/render-chat
            └─ Playwright → HTML → PNG (390×720)
            └─ /tmp/marketing-images/chat_{id}.png 저장
  └─ content.setImagePaths("[\"chat_{id}.png\"]")
```

### 디자인 토큰

| 항목 | 값 |
|---|---|
| 배경 | `#FBF3EC` |
| 카드 | `#FFF8F0` |
| 잉크 | `#5C4030` |
| 서브텍스트 | `#A08670` |
| 보더 | `#EADFD0` |
| 사용자 A 버블 | `#F4A896` (오른쪽 정렬) |
| 중재자 버블 | `#FFF8F0` + 보더 (왼쪽 정렬) |
| 워터마크 | `again-spring.net` |

### 이미지 서빙

`GET /api/admin/marketing/images/{filename}` — `MarketingImageController`  
경로 순회(path traversal) 방어: `..` / `/` 포함 파일명 즉시 400  
어드민 전용: `@PreAuthorize("hasRole('ADMIN')")`

---

## 채널별 콘텐츠 사양

> 채널별 상세 가이드: [`marketing-platform-x.md`](marketing-platform-x.md) · [`marketing-platform-instagram.md`](marketing-platform-instagram.md) · [`marketing-platform-naver.md`](marketing-platform-naver.md)

| 채널 | 형식 | 글자 수/해시태그 | 시각 자산 | 활성 |
|---|---|---|---|---|
| X (트위터) | 3~5 트윗 스레드 | 270자/트윗, 태그 3개 | 인용 카드 1장 + 채팅 1장(optional) | 활성 |
| 인스타그램 | 카드뉴스 + 짧은 캡션 | 150자 캡션, 태그 5개 | 카드뉴스 6~7장 PNG | 활성 |
| 네이버 블로그 | 마크다운 | 800~1,200자, 태그 5개 | 채팅+NeedsMap+인용 카드 3장 | 활성 |
| Threads | 단일 글 | 300자 이하, 태그 3개 | — | PR2 예정 |
| Facebook | 본문 | 400~600자, 태그 2개 | — | PR2 예정 |

---

## 안전 검사 3중 레이어

1. **Level 1·2 금지어** — KeywordGuard (기존)
2. **Level B 마케팅 금지어** — MarketingCopyGuard (V15.5)
3. **포지셔닝 가드** — disclaimer 자동 추가

---

## marketing-renderer 사이드카 (dev 전용)

컨테이너: `againspring-marketing-renderer-dev` (포트 9000 내부)  
스택: Node.js + Playwright + Sharp  
엔드포인트:

| Path | 입력 | 출력 | 용도 |
|---|---|---|---|
| `POST /render` | `{html, viewport, strip_exif}` | PNG bytes | HTML → PNG 범용 렌더링 |
| `POST /render-chat` | `{messages, title, subtitle, maxMessages, viewport}` | PNG bytes | 다시봄 채팅 UI 스크린샷 (키 모먼트 선별 후 전달) |
| `POST /render-quote` | `{line1, line2, attribution, variant, contentId}` | PNG bytes | 인용 카드 (1080×1350, X·네이버·인스타 공용) |
| `POST /render-card-news` | `{slides[], theme, contentId}` | `{slides:[{filename,base64}]}` | 인스타 카드뉴스 다중 PNG (6~7장) |
| `POST /render-report-summary` | `{report:{needsMap,contributionRatio,metaphor}, mode}` | PNG bytes | NeedsMap·기여도 다이어그램 (1080×1350) |

---

## 비용 한도

- 일일 시뮬레이션: 10건
- 월 예산: $20 USD (80% 도달 시 WARN 로그)

---

## 테스트 격리

마케팅 시뮬레이션 세션(`is_test_run=true`)은 일반 통계에서 자동 제외.

---

## prod 배포 게이트

`shared/docs/v15/marketing-dev-only-policy.md` 참조.
