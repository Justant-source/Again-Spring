# 다시봄 데이터베이스 스키마

**DB 스택**: MongoDB + Neo4j
**버전**: v1.0

---

## 🗄️ DB 역할 분리

| DB | 역할 | 데이터 |
|---|---|---|
| **MongoDB** | 주 저장소 | User, Session, Turn, Report, 인증 정보 |
| **Neo4j** | 관계 그래프 | Person 노드, 관계 엣지, 세션 엣지 |

---

## 📦 MongoDB 스키마

### Database: `againspring`

### Collection: `users`

```javascript
{
  _id: ObjectId("..."),              // MongoDB 자동 생성
  userId: "usr_abc123",              // 앱 레벨 ID (외부 노출용)
  email: "user@example.com",         // 게스트는 null
  passwordHash: "$2a$10$...",        // BCrypt 해시
  nickname: "달콩",
  isGuest: false,
  
  communicationStyle: "wave",        // 온보딩 결과: wave | mountain | flame | leaf | moon | star
  onboardingAnswers: [4, 2, 3, 5, 2, 4, 3, 5, 4, 3],  // 10개 리커트
  onboardingCompletedAt: ISODate("2026-04-24T10:00:00Z"),
  
  roles: ["USER"],                   // USER, ADMIN
  
  createdAt: ISODate("2026-04-24T10:00:00Z"),
  updatedAt: ISODate("2026-04-24T10:00:00Z"),
  deletedAt: null                    // 소프트 삭제
}
```

**인덱스**:
```javascript
db.users.createIndex({ userId: 1 }, { unique: true })
db.users.createIndex({ email: 1 }, { unique: true, sparse: true })  // 게스트는 email 없으므로 sparse
db.users.createIndex({ deletedAt: 1 })
```

---

### Collection: `sessions`

```javascript
{
  _id: ObjectId("..."),
  sessionId: "ses_abc123",
  
  // 참여자
  createdByUserId: "usr_abc123",     // A
  inviteeUserId: "usr_456",          // B (가입자인 경우)
  inviteeGuestName: null,            // B가 게스트면 이름만
  
  // 초대 토큰
  inviteToken: "inv_xyz789",
  inviteTokenExpiresAt: ISODate("2026-04-25T10:30:00Z"),
  
  // 메타
  relationType: "couple",            // couple | marriage | friend | family | parent_child
  category: {
    major: "couple",
    middle: "connection",
    minor: "infrequent_contact",
    customMinor: null                // "직접 입력" 선택 시 사용
  },
  
  // 상태
  status: "in_mediation",            // waiting_b | b_joined | in_mediation | completed | solo_mode | terminated
  currentTurn: 3,                    // 1-6
  currentRole: "A",                  // A | B
  isSoloMode: false,
  
  // 턴 내용 (임베디드)
  turns: [
    {
      turnNumber: 1,
      role: "A",
      userId: "usr_abc123",
      content: "3주 동안 연락이 너무 적어서...",  // 30일 후 삭제 대상
      mediatorMessage: "이야기 시작해주셔서 감사해요...",
      mediatorSummaryForOpponent: "A님은 연락 빈도에 대한 어려움을 공유하셨어요.",  // 앵커링 방지용 중립 요약
      isPerspectiveTaking: false,
      skipped: false,
      createdAt: ISODate("2026-04-24T10:31:00Z")
    }
  ],
  
  // 크라이시스 감지 로그
  crisisDetections: [
    {
      turnNumber: 2,
      detectedAt: ISODate("2026-04-24T10:35:00Z"),
      level: 1,
      category: "domestic_violence"
    }
  ],
  
  // 리포트 링크
  reportId: "rep_abc123",
  
  // 타임스탬프
  createdAt: ISODate("2026-04-24T10:30:00Z"),
  updatedAt: ISODate("2026-04-24T10:45:00Z"),
  completedAt: null,
  
  // TTL — 30일 후 원문 삭제 (turns.content, turns.mediatorMessage는 ID만 남기고 원문 삭제)
  contentExpiresAt: ISODate("2026-05-24T10:30:00Z")
}
```

**인덱스**:
```javascript
db.sessions.createIndex({ sessionId: 1 }, { unique: true })
db.sessions.createIndex({ inviteToken: 1 }, { unique: true, sparse: true })
db.sessions.createIndex({ createdByUserId: 1, status: 1 })
db.sessions.createIndex({ inviteeUserId: 1, status: 1 })
db.sessions.createIndex({ inviteTokenExpiresAt: 1 }, { expireAfterSeconds: 0 })  // TTL
db.sessions.createIndex({ createdAt: -1 })
```

### Session 원문 30일 TTL 정책

**요구사항**: 갈등 원문(`turns.content`)은 30일 후 자동 삭제. 리포트는 영구 보관.

**구현 방안 A: 별도 컬렉션 + TTL**
```javascript
// sessions: 메타정보만 (영구)
// session_contents: 원문 + TTL
db.session_contents.createIndex(
  { expiresAt: 1 }, 
  { expireAfterSeconds: 0 }
)
```

**구현 방안 B: 배치 Job으로 주기적 원문 삭제**
```java
@Scheduled(cron = "0 0 3 * * *")  // 매일 03:00
public void purgeOldContents() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(30);
    mongoTemplate.updateMulti(
        Query.query(Criteria.where("createdAt").lt(threshold)),
        Update.update("turns.$[].content", null)
              .unset("turns.$[].mediatorMessage"),
        Session.class
    );
}
```

**권장**: 방안 B (단순하고 통제 가능)

---

### Collection: `reports`

```javascript
{
  _id: ObjectId("..."),
  reportId: "rep_abc123",
  sessionId: "ses_abc123",
  
  // 참여자 복제 (세션 삭제돼도 리포트는 남도록)
  participantA: {
    userId: "usr_abc123",
    nicknameSnapshot: "달콩"
  },
  participantB: {
    userId: "usr_456",
    guestName: null,
    nicknameSnapshot: "민수"
  },
  
  // 분석 결과
  conflictType: "difference",        // factual | difference | mixed
  isSoloMode: false,
  
  contributionRatio: {
    a: 55,
    b: 45,
    label: {
      a: "먼저 다가가면 좋은 쪽",
      b: "마음 열고 기다려주면 좋은 쪽"
    }
  },
  
  needsMap: {
    axisX: "connection_autonomy",
    axisXLabel: "연결성-자율성",
    axisY: "stability_change",
    axisYLabel: "안정-변화",
    positionA: { x: -70, y: 0 },
    positionB: { x: 60, y: 0 },
    interpretation: "두 분은 '연결성-자율성' 축에서 거리가 있어요"
  },
  
  fourHorsemen: {
    criticism: { detected: false, intensity: null },
    defensiveness: { detected: true, intensity: "mild" },
    contempt: { detected: false, intensity: null },
    stonewalling: { detected: true, intensity: "moderate" }
  },
  
  nvcScripts: {
    aToB: {
      observation: "하루에 연락이 1-2번 정도 오고 있어",
      feeling: "가끔 혼자 남겨진 것 같고 불안해",
      need: "나한테는 '함께 있다는 느낌'이 중요해",
      request: "짧게라도 하루 몇 번 안부 나눌 수 있을까?"
    },
    bToA: { /* ... */ }
  },
  
  repairSuggestions: [
    "우리 서로 다른 게 문제가 아니라는 걸 인정하자",
    "아침과 저녁, 하루 두 번 '안부 시간'을 정해볼까?",
    "서로의 리듬을 존중하는 방법을 찾아보자"
  ],
  
  // 메타
  llmProvider: "claude-code",        // 감사 추적용
  llmCallCount: 8,
  generationDuration: 18500,         // ms
  
  createdAt: ISODate("2026-04-24T11:15:00Z")
}
```

**인덱스**:
```javascript
db.reports.createIndex({ reportId: 1 }, { unique: true })
db.reports.createIndex({ sessionId: 1 }, { unique: true })
db.reports.createIndex({ "participantA.userId": 1 })
db.reports.createIndex({ "participantB.userId": 1 })
db.reports.createIndex({ createdAt: -1 })
```

**주의**: 리포트는 **참여자 양쪽 모두 접근 가능**. 서비스 레이어에서 권한 검증 필수.

---

### Collection: `llm_call_logs`

감사·모니터링·장애 분석용 로그.

```javascript
{
  _id: ObjectId("..."),
  sessionId: "ses_abc123",
  taskType: "turn_3_a",              // turn_1 | turn_2 | ... | final_report
  provider: "claude-code",
  
  // 요청/응답 (민감 정보 마스킹)
  promptLength: 2400,
  responseLength: 850,
  
  success: true,
  errorCode: null,
  
  elapsedMs: 4200,
  
  createdAt: ISODate("2026-04-24T10:45:00Z")
}
```

**인덱스**:
```javascript
db.llm_call_logs.createIndex({ sessionId: 1, createdAt: -1 })
db.llm_call_logs.createIndex({ success: 1, createdAt: -1 })
db.llm_call_logs.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 })  // 30일 TTL
```

---

### Collection: `invite_tokens` (선택)

초대 토큰을 `sessions` 컬렉션에 임베드 대신 별도 관리하고 싶은 경우.

```javascript
{
  _id: ObjectId("..."),
  token: "inv_xyz789",
  sessionId: "ses_abc123",
  expiresAt: ISODate("2026-04-25T10:30:00Z"),
  used: false,
  usedAt: null,
  createdAt: ISODate("2026-04-24T10:30:00Z")
}
```

**인덱스**:
```javascript
db.invite_tokens.createIndex({ token: 1 }, { unique: true })
db.invite_tokens.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 })
```

**결정**: MVP는 sessions 임베드로 시작, 트래픽 많아지면 분리.

---

## 🕸️ Neo4j 스키마

### 노드 (Node)

#### `:Person`

```cypher
(:Person {
  userId: "usr_abc123",
  nickname: "달콩",
  isGuest: false,
  createdAt: datetime("2026-04-24T10:00:00Z")
})
```

게스트도 Person 노드로 생성하되 `isGuest: true`. 나중에 회원가입 시 merge.

### 관계 (Relationship)

#### `:HAS_RELATIONSHIP`

Person 간의 관계 유형 표시. 세션이 생성될 때 없으면 자동 생성.

```cypher
(a:Person)-[:HAS_RELATIONSHIP {
  type: "couple",                    // couple | marriage | friend | family | parent_child
  firstSessionAt: datetime("2026-04-24T10:30:00Z"),
  lastSessionAt: datetime("2026-04-24T11:15:00Z"),
  sessionCount: 5,
  averageTemperature: 36.4
}]->(b:Person)
```

**양방향 처리**: 한 쌍에 양방향 관계 2개 또는 무방향 단일 관계. 
**권장**: 단방향 1개만 저장하고 쿼리 시 방향 무시.

#### `:HAD_CONFLICT`

각 세션을 Person 간 엣지로 기록 (상세 히스토리).

```cypher
(a:Person)-[:HAD_CONFLICT {
  sessionId: "ses_abc123",
  relationType: "couple",
  conflictType: "difference",
  createdAt: datetime("2026-04-24T10:30:00Z")
}]->(b:Person)
```

### 쿼리 예시

**내 모든 관계 조회**
```cypher
MATCH (me:Person {userId: $userId})-[r:HAS_RELATIONSHIP]-(other:Person)
RETURN other, r
ORDER BY r.lastSessionAt DESC
```

**특정 상대와의 갈등 이력**
```cypher
MATCH (me:Person {userId: $userId})-[c:HAD_CONFLICT]-(other:Person {userId: $partnerId})
RETURN c
ORDER BY c.createdAt DESC
```

**관계 세션 횟수 갱신 (세션 완료 시)**
```cypher
MATCH (a:Person {userId: $userIdA})
MATCH (b:Person {userId: $userIdB})
MERGE (a)-[r:HAS_RELATIONSHIP {type: $relationType}]->(b)
ON CREATE SET 
  r.firstSessionAt = $now,
  r.sessionCount = 1
ON MATCH SET
  r.lastSessionAt = $now,
  r.sessionCount = r.sessionCount + 1
CREATE (a)-[:HAD_CONFLICT {
  sessionId: $sessionId,
  relationType: $relationType,
  conflictType: $conflictType,
  createdAt: $now
}]->(b)
```

---

## 🔐 데이터 암호화

### 민감 필드 암호화

다음 필드는 **애플리케이션 레이어 암호화** (AES-256):
- `users.email`
- `sessions.turns.content` (원문)
- `sessions.turns.mediatorMessage`

Spring Data MongoDB의 `@Encrypted` 또는 수동 Converter 구현.

### 키 관리
- 프로덕션: AWS KMS, Google Cloud KMS, 또는 HashiCorp Vault
- 개발: 환경변수 (`ENCRYPTION_KEY`)

---

## 📊 데이터 볼륨 예상

| 컬렉션 | 1K DAU 기준 | 10K DAU 기준 |
|---|---|---|
| users | 10K | 100K |
| sessions | 30K (3/DAU) | 300K |
| reports | 20K | 200K |
| llm_call_logs | 200K | 2M |

**MVP는 단일 MongoDB 인스턴스로 충분. 10K DAU 도달 시 샤딩 검토.**

---

## 🔄 마이그레이션 전략

### Spring Data Migration 도구
- **Mongock**: MongoDB 마이그레이션 관리 (추천)
- 버전 관리된 변경 스크립트

```java
@ChangeUnit(id = "001-create-initial-indexes", order = "001", author = "claude-code")
public class InitialIndexesChangeUnit {
    
    @Execution
    public void execute(MongoTemplate template) {
        template.indexOps("users").ensureIndex(
            new Index().on("userId", Sort.Direction.ASC).unique()
        );
        // ...
    }
}
```

---

## ✅ Claude Code 작업 체크리스트

### MongoDB
- [ ] Spring Data MongoDB 의존성 추가
- [ ] 연결 설정 (`application.yml`)
- [ ] 엔티티 클래스 작성 (`domain/mongo/`)
- [ ] Repository 인터페이스 작성
- [ ] 인덱스 Mongock 마이그레이션 작성
- [ ] TTL 정책 구현 (30일 원문 삭제 스케줄러)
- [ ] 민감 필드 암호화 Converter

### Neo4j
- [ ] Spring Data Neo4j 의존성 추가
- [ ] 연결 설정
- [ ] 노드/관계 엔티티 작성 (`domain/neo4j/`)
- [ ] Repository 인터페이스 작성 (Cypher 쿼리 포함)
- [ ] 세션 완료 시 그래프 업데이트 로직
- [ ] MVP 범위 엄수 (복잡한 그래프 쿼리 금지)

### 공통
- [ ] `@Transactional` 처리 (MongoDB는 버전별 트랜잭션 지원 다름)
- [ ] 테스트용 Testcontainers 설정
- [ ] 개발 환경 더미 데이터 seeding 스크립트

---

**끝.**
