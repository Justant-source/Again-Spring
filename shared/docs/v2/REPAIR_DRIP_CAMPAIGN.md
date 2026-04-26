# Repair Drip 캠페인 — 결과 후 1주일 follow-up 시퀀스

**버전**: v2.0
**대상**: Claude Code
**연관 작업**: `REFINEMENT_WORK_ORDER.md` Phase 4

---

## 🎯 목표

**"단발적 사용(episodic) → 일상 사용(habitual)으로 전환"**

다시봄은 본질적으로 "싸웠을 때만 쓰는" 앱이라 정신건강 앱 평균 D30 retention(3-4%)보다 더 낮을 가능성이 큼. 결과 도출 후 **1주일 follow-up drip**으로:
1. 갈등 해결 진행 상황 자연스럽게 챙김
2. 재방문 유도 (두 번째 세션 또는 회복 점검)
3. 카톡 대신 이메일 우선 (이번 단계는 카톡 보류, 향후 도입)

---

## 📅 Drip 시퀀스 설계

### 결과 생성 후 타임라인

```
T+0 (결과 도출)
    │
    │ 사용자가 결과 페이지에서 "1주일 회복 가이드 메일 받기" 옵트인
    │
    ▼
T+1일 후 (오전 10시)
    📧 메일 1: "어제의 대화, 어떻게 풀어가고 계세요?"
    
    │
    ▼
T+3일 후 (오후 7시)
    📧 메일 2: "오늘 저녁, 다음 한 문장만 보내보세요"
    
    │
    ▼
T+7일 후 (오전 10시)
    📧 메일 3: "한 주가 지났어요. 다시 한 번 점검해볼까요?"
    
    │
    ▼
T+30일 후 (오전 10시) [선택]
    📧 메일 4: "한 달이 지났어요. 그 후 어떠셨나요?"
```

### 메일 내용 템플릿

#### 메일 1: T+1일 후

**제목**: `[다시봄] 어제의 대화, 어떻게 풀어가고 계세요? 🌱`

**본문**:
```
[닉네임]님, 안녕하세요.

어제 다시봄에서 [상대방]님과의 [관계 유형] 갈등을 정리하셨어요.

오늘은 어떠셨나요?

작은 변화도 큰 시작이에요.
어제 받으신 결과 카드 중에 마음에 남은 한 문장이 있다면,
그 한 문장을 오늘 한 번 곱씹어보시는 것도 좋아요.

📌 어제의 결과 다시 보기:
[다시봄 결과 보기 버튼]

📌 다음 한 걸음 카드:
"오늘 저녁 7시, 다음 문장 한 줄을 보내보세요:
 '어제 일에 대해 잠깐 얘기 나눌 시간 있어?'"

천천히, 부담 없이.
다시봄이 응원해요. 🌸

---
[수신 거부] | [모든 알림 끄기]
```

#### 메일 2: T+3일 후

**제목**: `[다시봄] 오늘 저녁, 다음 한 문장만 보내보세요 💌`

**본문**:
```
[닉네임]님, 안녕하세요.

3일 전 정리하신 [관계 유형] 갈등, 잘 풀려가고 계신가요?

오늘은 작은 행동을 제안드릴게요.

📌 오늘 저녁 7시, 다음 한 문장 보내보기:
"잠깐 시간 되면 얘기하자"

거창한 시작이 아니어도 괜찮아요.
짧은 한 마디가 종종 큰 문을 열어요.

만약 이미 화해하셨다면, 정말 잘하셨어요. 🌸
다시봄에 다시 들어와서 "회복 완료" 체크하시면
관계 회복 기록이 남아요.

📌 다시봄 들어가기:
[다시봄 바로가기 버튼]

---
[수신 거부] | [모든 알림 끄기]
```

#### 메일 3: T+7일 후

**제목**: `[다시봄] 한 주가 지났어요. 다시 한 번 점검해볼까요? 🌷`

**본문**:
```
[닉네임]님, 안녕하세요.

한 주 전 정리하신 [관계 유형] 갈등으로부터 7일이 지났어요.

이번 한 주, 어떠셨나요?

✅ 잘 풀렸어요 → 회복 완료 체크하기
🔄 아직 진행 중이에요 → 다시 한 번 정리하기
😔 더 어려워졌어요 → 다른 시각에서 보기

지난번 결과를 바탕으로,
혹시 새로운 갈등이 생겼다면 다시 한 번
다시봄에서 정리해보시는 것도 좋아요.

📌 새로운 세션 시작:
[다시봄에서 시작하기 버튼]

📌 지난 결과 다시 보기:
[지난 결과 보기 버튼]

깊은 갈등이 계속된다면 전문 상담을 권해드려요.

---
[수신 거부] | [모든 알림 끄기]
```

#### 메일 4 (선택): T+30일 후

**제목**: `[다시봄] 한 달이 지났어요. 그 후 어떠셨나요? 🌸`

**본문**: 위와 유사한 구조로 한 달 후 점검.

---

## 🛠️ 백엔드 구현

### Spring Boot Scheduler

```java
// backend/src/main/java/com/againspring/repair/RepairDripScheduler.java

@Component
@RequiredArgsConstructor
@Slf4j
public class RepairDripScheduler {
    
    private final ReportRepository reportRepository;
    private final RepairDripService dripService;
    
    /**
     * 매일 오전 10시: 1일 후 / 7일 후 / 30일 후 메일 발송
     */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void sendMorningDrips() {
        sendDripsForOffset(1, "morning_d1");
        sendDripsForOffset(7, "morning_d7");
        sendDripsForOffset(30, "morning_d30");
    }
    
    /**
     * 매일 오후 7시: 3일 후 메일 발송
     */
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void sendEveningDrips() {
        sendDripsForOffset(3, "evening_d3");
    }
    
    private void sendDripsForOffset(int daysAfter, String dripType) {
        LocalDate targetDate = LocalDate.now().minusDays(daysAfter);
        
        List<Report> targetReports = reportRepository.findByCreatedAtBetween(
            targetDate.atStartOfDay(),
            targetDate.atTime(23, 59, 59)
        );
        
        log.info("Drip campaign [{}]: {} reports to process", dripType, targetReports.size());
        
        for (Report report : targetReports) {
            try {
                dripService.sendDrip(report, dripType);
            } catch (Exception e) {
                log.error("Drip send failed for report {}: {}", report.getId(), e.getMessage());
            }
        }
    }
}
```

### Drip Service

```java
// backend/src/main/java/com/againspring/repair/RepairDripService.java

@Service
@RequiredArgsConstructor
public class RepairDripService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final DripLogRepository dripLogRepository;
    
    public void sendDrip(Report report, String dripType) {
        // 사용자 가져오기
        String userId = report.getOwnerUserId();
        User user = userRepository.findById(userId).orElse(null);
        
        if (user == null || user.isGuest()) {
            // 게스트는 메일 없음
            return;
        }
        
        // Opt-out 체크
        if (!user.isDripOptedIn()) {
            return;
        }
        
        // 이미 발송한 경우 스킵
        boolean alreadySent = dripLogRepository.existsByReportIdAndDripType(
            report.getId(), dripType);
        if (alreadySent) {
            return;
        }
        
        // 메일 템플릿 선택
        DripTemplate template = selectTemplate(dripType, report);
        
        // 메일 발송
        EmailMessage email = EmailMessage.builder()
            .to(user.getEmail())
            .subject(template.getSubject())
            .htmlBody(template.renderHtml(user, report))
            .build();
        
        emailService.send(email);
        
        // 로그 기록
        dripLogRepository.save(DripLog.builder()
            .reportId(report.getId())
            .userId(userId)
            .dripType(dripType)
            .sentAt(Instant.now())
            .build());
    }
    
    private DripTemplate selectTemplate(String dripType, Report report) {
        return switch (dripType) {
            case "morning_d1"  -> DripTemplate.D1_MORNING;
            case "evening_d3"  -> DripTemplate.D3_EVENING;
            case "morning_d7"  -> DripTemplate.D7_MORNING;
            case "morning_d30" -> DripTemplate.D30_MORNING;
            default -> throw new IllegalArgumentException("Unknown drip type: " + dripType);
        };
    }
}
```

### 이메일 템플릿 관리

```java
// backend/src/main/java/com/againspring/repair/DripTemplate.java

public enum DripTemplate {
    
    D1_MORNING(
        "[다시봄] 어제의 대화, 어떻게 풀어가고 계세요? 🌱",
        "templates/drip/d1_morning.html"
    ),
    D3_EVENING(
        "[다시봄] 오늘 저녁, 다음 한 문장만 보내보세요 💌",
        "templates/drip/d3_evening.html"
    ),
    D7_MORNING(
        "[다시봄] 한 주가 지났어요. 다시 한 번 점검해볼까요? 🌷",
        "templates/drip/d7_morning.html"
    ),
    D30_MORNING(
        "[다시봄] 한 달이 지났어요. 그 후 어떠셨나요? 🌸",
        "templates/drip/d30_morning.html"
    );
    
    private final String subject;
    private final String templatePath;
    
    public String renderHtml(User user, Report report) {
        // Thymeleaf 또는 Handlebars로 템플릿 렌더링
        // 변수: nickname, partnerName, relationType, resultUrl, unsubscribeUrl
        return TemplateRenderer.render(templatePath, Map.of(
            "nickname", user.getNickname(),
            "partnerName", report.getPartnerName(),
            "relationType", report.getRelationTypeKorean(),
            "resultUrl", buildResultUrl(report.getId()),
            "newSessionUrl", buildNewSessionUrl(),
            "unsubscribeUrl", buildUnsubscribeUrl(user.getId())
        ));
    }
}
```

---

## 🗄️ DB 스키마 변경

### `users` 테이블

```sql
ALTER TABLE users
ADD COLUMN drip_opted_in BOOLEAN DEFAULT FALSE,
ADD COLUMN drip_opted_in_at TIMESTAMP NULL;
```

### `drip_logs` 테이블 (신규)

```sql
CREATE TABLE drip_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    drip_type VARCHAR(50) NOT NULL,  -- morning_d1, evening_d3, ...
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMP NULL,        -- 메일 열람 추적 (선택)
    clicked_at TIMESTAMP NULL,       -- 링크 클릭 추적
    UNIQUE KEY uniq_report_drip (report_id, drip_type),
    INDEX idx_user (user_id),
    INDEX idx_sent (sent_at)
);
```

### `email_unsubscribes` 테이블 (신규)

```sql
CREATE TABLE email_unsubscribes (
    user_id VARCHAR(32) PRIMARY KEY,
    unsubscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(200) NULL
);
```

---

## 🌐 API 엔드포인트

### `PATCH /api/users/me/drip-preferences`

**Request**
```json
{
  "optedIn": true
}
```

**Response 200**
```json
{
  "dripOptedIn": true,
  "dripOptedInAt": "2026-04-24T10:30:00Z"
}
```

### `GET /api/unsubscribe?token={user_id_token}`

이메일 하단 "수신 거부" 링크.

토큰 검증 후 자동으로 `drip_opted_in = false`.

응답: 간단한 HTML 페이지 ("구독이 해지됐어요")

---

## 🎨 옵트인 UI

결과 페이지 푸터에 옵트인 체크박스:

```
┌────────────────────────────────────────────┐
│ 🔔 1주일 follow-up 안내                    │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ ☑ 1일/3일/7일 후 회복 가이드 메일      │ │
│ │   받기 (선택, 언제든 해지 가능)         │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ 메일 내용:                                  │
│ • 1일 후: "어제의 대화, 어떻게 풀어가세요?" │
│ • 3일 후: "오늘 저녁, 다음 한 문장 보내기" │
│ • 7일 후: "한 주가 지났어요. 점검해볼까요?"│
│                                            │
│ 게스트는 받을 수 없어요. [회원가입하기]    │
└────────────────────────────────────────────┘
```

---

## 📊 효과 측정

### KPI

| 지표 | 측정 방법 | 목표 |
|---|---|---|
| 옵트인율 | 결과 도달 사용자 중 옵트인 % | 30%+ |
| 메일 발송 성공률 | 발송 성공 / 발송 시도 | 95%+ |
| 메일 열람률 (D1) | 열람 / 발송 | 25%+ |
| 메일 클릭률 (D3) | 클릭 / 발송 | 10%+ |
| 7일 내 재방문율 | 메일 통해 다시봄 재방문 | 20%+ |
| 두 번째 세션 생성률 | 첫 세션 후 30일 내 재세션 | 10%+ |
| 옵트아웃율 | 옵트인 후 해지 | <10% |

### 이벤트 트래킹

```typescript
// drip 관련 이벤트
| 이벤트 | 시점 |
|---|---|
| `drip_opted_in` | 결과 페이지 옵트인 체크 |
| `drip_email_sent` | 메일 발송 성공 |
| `drip_email_failed` | 메일 발송 실패 |
| `drip_email_opened` | 메일 열람 (이미지 픽셀 추적) |
| `drip_email_clicked` | 메일 내 링크 클릭 |
| `drip_unsubscribed` | 수신 거부 |
```

---

## 🔧 EmailService 추상화

기존 BE에 EmailService가 있다면 활용. 없으면 신규 작성.

### Spring Mail 설정

```yaml
# application.yml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}  # Gmail App Password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### EmailService

```java
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${mail.from:noreply@againspring.app}")
    private String fromAddress;
    
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            
            helper.setFrom(fromAddress, "다시봄");
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject());
            helper.setText(message.getHtmlBody(), true);  // HTML
            
            mailSender.send(mime);
            
            log.info("Email sent: to={}, subject={}", message.getTo(), message.getSubject());
            
        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
            throw new EmailSendException(e);
        }
    }
}
```

---

## 🧪 검증 시나리오

### 시나리오 1: 옵트인 후 1일 후 메일 수신
```
1. 사용자 A가 결과 도달
2. 옵트인 체크 후 저장
3. 24시간 후 오전 10시
4. 스케줄러 실행
5. 메일 발송 확인
6. 메일 클릭 → 다시봄 결과 페이지로 이동
7. drip_logs에 로그 기록 확인
```

### 시나리오 2: 옵트아웃
```
1. 메일 하단 "수신 거부" 클릭
2. /unsubscribe 페이지 표시
3. drip_opted_in = false 자동 업데이트
4. 이후 모든 drip 발송 중단
```

### 시나리오 3: 게스트 처리
```
1. 게스트 사용자가 결과 도달
2. 옵트인 옵션 표시되지 않음 (또는 회원가입 유도)
3. 회원가입 시 옵트인 가능
```

### 시나리오 4: 중복 발송 방지
```
1. 같은 report_id, drip_type 조합으로 발송 시도
2. drip_logs UNIQUE 제약으로 차단
3. 로그에 "이미 발송됨" 기록
```

---

## ✅ Phase 4 완료 조건

- [ ] RepairDripScheduler 정상 동작
- [ ] 4가지 drip 메일 템플릿 작성 완료
- [ ] 옵트인 UI 결과 페이지에 표시
- [ ] 메일 발송 성공 확인 (개발 환경)
- [ ] drip_logs 테이블 정상 기록
- [ ] 옵트아웃 동작 확인
- [ ] 게스트는 메일 발송 안 됨
- [ ] 중복 발송 방지 동작
- [ ] KPI 측정 이벤트 트래킹

---

**끝.**
