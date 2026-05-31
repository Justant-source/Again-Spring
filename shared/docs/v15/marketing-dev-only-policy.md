# V15 마케팅 자동화 — dev 전용 정책

**작성일**: 2026-05-24  
**권위본**: 이 파일  
**관련 파일**: `env/docker-compose.dev.yml`, `backend/src/main/resources/application-dev.yml`, `env/.env.dev.example`

---

## 핵심 원칙

**마케팅 자동화 모듈(V15)은 dev 환경에서만 활성화한다. prod에는 포함하지 않는다.**

---

## 이유

| 항목 | 상태 |
|---|---|
| Q1. 저작권 — 50% 재작성 + "영감을 받은" 표기로 충분한가 | 미결 (법무 검토 필요) |
| Q2. 회사 사이드 프로젝트 정책 저촉 여부 | 미결 |
| Q3. 익명 운영 환경 (사업자등록 / WHOIS 등) | 미결 |

위 3건이 확인되기 전까지 prod에 마케팅 자동화를 노출하면 법적·운영상 리스크가 발생한다. V15.1~V15.6은 Q1~Q3 미답변 상태로 dev에서 개발·검증을 진행하고, **prod 배포 게이트는 3건 답변 완료 + 명시적 prod 배포 지시 시에만** 해제한다.

---

## 활성화 메커니즘

### 1. 환경변수 (Spring feature flag)

```
# dev — MARKETING_ENABLED=true  (.env.dev 또는 application-dev.yml)
# prod — 변수 미설정 또는 MARKETING_ENABLED=false  (절대 true 설정 금지)
```

`application.yml` 기본값:
```yaml
app:
  features:
    marketing:
      enabled: ${MARKETING_ENABLED:false}   # 기본 false
```

`application-dev.yml` 오버라이드:
```yaml
app:
  features:
    marketing:
      enabled: true   # dev 전용
```

`application-prod.yml`에는 `marketing` 키 자체를 포함하지 않는다.

### 2. Spring `@ConditionalOnProperty`

마케팅 관련 모든 `@RestController` / `@Service` / `@Configuration` 빈에 아래 애노테이션을 붙인다:

```java
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
```

이 애노테이션이 없으면 prod 프로파일에서도 빈이 등록되어 엔드포인트가 노출된다.  
**모든 마케팅 빈에 반드시 명시할 것.**

적용 대상:
- `MarketingModuleController` (V15.1 — 완료)
- `StoryController` (V15.2)
- `SimulationController` (V15.3)
- `ContentController` (V15.5)
- `CostController` (V15.7)
- `StoryService`, `SimulationOrchestrator`, `ContentService`, `CostMonitoringService`
- `MarketingAsyncConfig` (marketingExecutor 빈)

### 3. Docker Compose 분리

마케팅 렌더링 사이드카 (`marketing-renderer`)는 **`docker-compose.dev.yml`에만** 포함한다.

```yaml
# docker-compose.dev.yml (V15.4에서 추가)
services:
  marketing-renderer-dev:
    build:
      context: ../marketing/renderer
      dockerfile: Dockerfile
    container_name: againspring-marketing-renderer-dev
    environment:
      PORT: 9000
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1500m
    networks:
      - againspring-dev
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:9000/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s
```

`docker-compose.prod.yml`에는 `marketing-renderer` 서비스를 추가하지 않는다.

### 4. 볼륨 분리

마케팅 이미지 자산은 dev 전용 named volume에 저장한다:

```yaml
# docker-compose.dev.yml
volumes:
  marketing_assets_dev:

services:
  backend-dev:
    volumes:
      - marketing_assets_dev:/var/marketing-assets:rw
```

prod에는 이 볼륨 및 마운트 경로가 없다.

---

## prod 배포 게이트 (마케팅 모듈 한정)

아래 모든 조건이 충족될 때에만 마케팅 모듈을 prod에 포함할 수 있다:

- [ ] Q1: 저작권 법무 검토 완료 (서면 확인)
- [ ] Q2: 회사 사이드 프로젝트 정책 확인 완료
- [ ] Q3: 익명 운영 환경 설정 완료 (사업자등록 or WHOIS 프라이버시)
- [ ] dev에서 1주일 이상 안정 운영 확인
- [ ] 사용자가 명시적으로 "prod 마케팅 모듈 배포해줘" 지시

위 조건 미충족 상태에서 prod에 `MARKETING_ENABLED=true`를 추가하는 것은 **절대 금지**이며, CLAUDE.md 절대 규칙과 동급으로 취급한다.

---

## 개발 체크리스트 — 마케팅 빈 추가 시

새 마케팅 관련 빈(`@RestController`, `@Service`, `@Configuration`)을 추가할 때마다:

1. `@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")` 추가
2. `CLAUDE.md` 개발 체크리스트의 "백엔드 수정 시" 섹션 준수
3. 테스트에서 `@TestPropertySource(properties = "app.features.marketing.enabled=true")` 명시

---

## prod 미지원 명시

**마케팅 대시보드(`/admin/marketing`)는 prod(`againspring.net`)에서 지원하지 않는다.**

| 환경 | 마케팅 대시보드 | 비고 |
|---|---|---|
| dev (`dev.againspring.net`) | **지원** | `MARKETING_ENABLED=true` |
| prod (`againspring.net`) | **미지원** | `MARKETING_ENABLED` 미설정, 빈 미등록 |

prod 배포 시 마케팅 관련 엔드포인트(`/api/admin/marketing/**`)는 Spring 컨텍스트에 존재하지 않으므로 404가 아닌 빈 자체가 없음. `marketing-renderer` 컨테이너도 prod compose에 없음.

---

## 현재 적용 상태 (2026-05-25)

V15.1~V15.7 구현 완료. 모든 마케팅 빈에 `@ConditionalOnProperty` 적용됨.

| 클래스 | `@ConditionalOnProperty` |
|---|---|
| `MarketingModuleController` | 완료 |
| `StoryController` | 완료 |
| `StoryService`, `StoryAnonymizationService`, `RewriteRatioCalculator` | 완료 |
| `SimulationController`, `SimulationService`, `SimulationOrchestrator` | 완료 |
| `PersonaInferenceService`, `MarketingAsyncConfig` | 완료 |
| `ContentController`, `ContentService`, `PlatformContentRouter` | 완료 |
| `XContentGenerator`, `InstagramContentGenerator`, `NaverBlogContentGenerator` | 완료 |
| `MarketingCopyGuard`, `ImageRenderClient` | 완료 |
| `CostController`, `CostMonitoringService` | 완료 |
| `ApprovalWorkflowService`, `ContentExportService` | 완료 |
