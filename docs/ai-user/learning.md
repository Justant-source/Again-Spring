# Learning 서비스 학습 문서 (ai-user-learning)

## 1. 개요

**Learning 서비스**는 한국어 커뮤니티 글/댓글 크롤링 → KURE-v1 임베딩 → MariaDB VECTOR 저장 → RAG 검색을 통해 LLM 생성 시 컨텍스트 예시를 제공하는 백엔드 마이크로서비스입니다.

| 항목 | 값 |
|------|-----|
| **포트** | 8099 (Python FastAPI) |
| **임베딩 모델** | `nlpai-lab/KURE-v1` (1024차원, BGE-M3 기반) |
| **데이터베이스** | MariaDB 11.8, VECTOR(1024) 지원 |
| **스케줄** | APScheduler, 매일 03:00 KST 크롤링 시작 |
| **주요 역할** | 예시뱅크 구축 & 코사인 유사도 RAG 검색 |

---

## 2. API 엔드포인트

| Method | 경로 | 설명 | 요청 바디 | 응답 |
|--------|------|------|---------|------|
| **POST** | `/examples/save` | 예시 저장 (자동 임베딩) | `SaveRequest` | `{"id": int, "status": "saved"}` |
| **POST** | `/examples/search` | 코사인 유사도 검색 | `SearchRequest` | `List[ExampleItem]` |
| **GET** | `/examples/{id}` | 단일 예시 조회 (원본 포함) | (없음) | `ExampleItem` |
| **GET** | `/examples/count` | 소스별 통계 | (없음) | `{"source": count, ...}` |
| **GET** | `/examples/export` | 인간/AI 코퍼스 export (내부 전용, Step 2) | (없음) | 코퍼스 데이터 |
| **POST** | `/crawl/{source}` | 크롤링 수동 트리거 | (없음) | `{"status": "queued"}` |
| **POST** | `/embed` | 텍스트 임베딩 (디버그용) | `{"text": str}` | `{"embedding": [float]}` |
| **GET** | `/health` | 헬스체크 | (없음) | `{"status": "ok"}` |

---

## 3. RAG 저장 및 검색 파이프라인

### 3.1 저장 시퀀스 (POST /examples/save)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant API as FastAPI<br/>/examples/save
    participant QF as QualityFilter
    participant EmbedSvc as EmbeddingService
    participant Model as KURE-v1<br/>Model
    participant DB as MariaDB<br/>example_bank
    
    Client->>API: SaveRequest 전송
    API->>QF: 품질 필터링 (잡음 차단)
    QF-->>API: passes=true/false
    alt 잡음이면
        API-->>Client: ❌ 저장 거부
    end
    API->>API: 품질 점수 계산
    API->>EmbedSvc: embed(content[:512])
    EmbedSvc->>Model: encode(text, normalize=true)
    Model-->>EmbedSvc: [float; 1024] 정규화 벡터
    EmbedSvc-->>API: [f₀, f₁, ..., f₁₀₂₃]
    API->>API: "[f₀,f₁,...]" 포맷팅
    API->>DB: INSERT example_bank<br/>(content, type, category,<br/>source, quality_score, embedding)
    DB-->>API: new_id, embedding 저장 완료
    API-->>Client: {"id": new_id, "status": "saved"}
```

**주요 포인트**:
- **QualityFilter 통과**: UI 토큰, 레시피(3개+ 신호), 갤러리 잡음(2개+ 신호 + <200자) 차단
- **정규화**: `normalize_embeddings=true` → 코사인 거리 계산 최적화
- **콘텐츠 트림**: 512자 이상 잘라냄 (메모리 효율)

### 3.2 검색 시퀀스 (POST /examples/search) — 3단계 폴백

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant API as FastAPI<br/>/examples/search
    participant EmbedSvc as EmbeddingService
    participant Model as KURE-v1<br/>Model
    participant DB as MariaDB<br/>example_bank
    
    Client->>API: SearchRequest (query, filters)
    API->>EmbedSvc: embed(query)
    EmbedSvc->>Model: encode(text, normalize=true)
    Model-->>EmbedSvc: [float; 1024]
    EmbedSvc-->>API: query_vec
    
    API->>DB: Stage 1: WHERE quality_score >= MIN_QUALITY<br/>AND filters (type+category)
    DB-->>API: results
    
    alt No results
        API->>DB: Stage 2: WHERE filters (type+category)<br/>[quality 제거]
        DB-->>API: results
        
        alt Still no results
            API->>DB: Stage 3: WHERE type만<br/>[category 완화]
            DB-->>API: results (크롤링 데이터 접근)
            API->>API: ⚠️ WARNING: "Stage 3 fallback"
        else Stage 2 hit
            API->>API: ⚠️ WARNING: "No quality >= MIN_QUALITY"
        end
    end
    
    API->>API: TOP-K 정렬 (similarity DESC, quality DESC)
    API-->>Client: List[ExampleItem]
```

**3단계 폴백의 배경**:
- **Stage 1**: 최고 품질 필터 (quality_score ≥ 0.5) + 앱 카테고리 (COUPLE/MARRIED/WORK/OTHER 등)
- **Stage 2**: 품질 필터 제거, 앱 카테고리 유지 → 불완전한 데이터 허용
- **Stage 3**: 카테고리 완화 (type만) → **크롤러 board-name 카테고리에 도달**
  - **배경**: 크롤러는 board-name (talk/hot/freeboard/workplace 등)으로 저장
  - 오케스트레이터는 앱 enum (COUPLE/MARRIED 등)으로 검색
  - → Stage 1/2에서 크롤링 데이터 미매칭, Stage 3에서 접근 가능

**품질 점수 해석**:
- `1.0`: 완전한 갈등 사연 (마침표 완전, 반말 특성 명확)
- `0.5`: 경계선 (불완전한 표현 또는 약한 갈등 신호)
- `0.0~0.5`: 순수 잡음 (UI 토큰, 레시피, 갤러리 메타 등)

### 3.3 검색 필터링 로직

```python
# SearchRequest 구조
{
    "query": "남친이 내 돈을 빌려갔어",
    "content_type": "post",     # optional
    "category": "COUPLE",        # optional: 앱 enum (COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER)
    "top_k": 5
}

# 동적 WHERE 조건 구성 (Stage별)
MIN_QUALITY_SCORE = float(os.getenv("RAG_MIN_QUALITY", "0.5"))

# Stage 1: 완전 필터
conditions = ["content_type = %s"] if req.content_type else []
if req.category:
    conditions.append("(category = %s OR category IS NULL)")
conditions.append(f"quality_score >= {MIN_QUALITY_SCORE}")

# Stage 2: 품질 제거 (index로 조건 -1)
# Stage 3: 카테고리 제거 (index로 조건 -2)

# SQL (Stage 1)
SELECT id, content, source, 
       1 - VEC_DISTANCE_COSINE(embedding, query_vec) AS similarity
FROM example_bank
WHERE content_type = %s 
  AND (category = %s OR category IS NULL)
  AND quality_score >= 0.5
ORDER BY similarity DESC, quality_score DESC
LIMIT 5;
```

---

## 4. example_bank 스키마 (현재)

### 4.1 DDL (CREATE TABLE)

```sql
CREATE TABLE IF NOT EXISTS example_bank (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content LONGTEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    category VARCHAR(32),
    topic VARCHAR(16) DEFAULT NULL,        -- 앱 토픽 (COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER)
    source VARCHAR(32) NOT NULL,
    quality_score DECIMAL(4,2),
    embedding VECTOR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT NOW(3),
    KEY idx_type_cat (content_type, category),
    KEY idx_topic_type (topic, content_type),
    KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
```

### 4.2 컬럼 상세

| 컬럼 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `id` | BIGINT | 자동 증분 PK | 12345 |
| `content` | LONGTEXT | 글/댓글 본문 | "남편이 내 일기장을..." |
| `content_type` | VARCHAR(16) | 콘텐츠 종류 | "post", "comment", "reply" |
| `category` | VARCHAR(32) | 카테고리 (크롤러 기준) | "talk", "hot", "freeboard", "workplace", ... |
| `topic` | VARCHAR(16) | 앱 토픽 분류 (Phase 4+) | "COUPLE", "MARRIED", "WORK", ... |
| `source` | VARCHAR(32) | 크롤러 소스 | "naver_news", "dcinside", "blind", ... |
| `title` | VARCHAR(512) | 원문 제목 (optional, 글만 해당) | "남편의 일기장 침해 사건" |
| `source_url` | VARCHAR(1024) | 원문 출처 URL (optional) | "https://www.naver.com/..." |
| `quality_score` | DECIMAL(4,2) | 품질 점수 | 0.50, 0.85, 1.00 |
| `embedding` | VECTOR(1024) | 1024차원 벡터 | `[0.123, 0.456, ...]` |
| `created_at` | DATETIME(3) | 저장 시각 (마이크로초) | 2026-06-06 14:23:45.123 |

### 4.3 인덱스

| 인덱스명 | 컬럼 | 용도 |
|---------|------|------|
| `PRIMARY KEY` | `id` | PK |
| `idx_type_cat` | `(content_type, category)` | 검색 필터 복합 인덱스 |
| `idx_topic_type` | `(topic, content_type)` | 토픽 기반 검색 |
| `idx_source` | `source` | 소스별 통계 |

**VECTOR 인덱스 없음**: 현재 VEC_DISTANCE_COSINE 계산이 sequential scan 방식. Phase 3에서 VEC_DISTANCE_COSINE(...) USING COSINE 추가 검토.

---

## 5. EmbeddingService

### 5.1 클래스 구현

```python
class EmbeddingService:
    MODEL_NAME = "nlpai-lab/KURE-v1"  # BGE-M3 기반, 1024차원
    
    def __init__(self):
        self.model = None
    
    def load(self):
        """앱 시작(startup 이벤트)시 모델 로드 (1회만)"""
        self.model = SentenceTransformer(self.MODEL_NAME)
        dim = self.model.get_sentence_embedding_dimension()
        if dim != 1024:
            raise RuntimeError(
                f"KURE-v1 embedding dimension mismatch: expected 1024, got {dim}"
            )
        logger.info(f"Embedding model loaded ({dim} dim)")  
        # 출력: "Embedding model loaded (1024 dim)"
    
    def embed(self, text: str) -> List[float]:
        """단일 텍스트 임베딩"""
        if not self.model:
            raise RuntimeError("Model not loaded. Call load() first.")
        vec = self.model.encode(text, normalize_embeddings=True)
        return vec.tolist()
    
    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """배치 임베딩 (메모리 효율 최적화)"""
        if not self.model:
            raise RuntimeError("Model not loaded. Call load() first.")
        vecs = self.model.encode(
            texts,
            normalize_embeddings=True,
            batch_size=32,
            show_progress_bar=False
        )
        return vecs.tolist()
```

### 5.2 임베딩 특성

| 항목 | 값 | 설명 |
|------|-----|------|
| **모델** | KURE-v1 | BGE-M3 기반 한국어 임베딩 |
| **차원** | 1024 | MariaDB VECTOR(1024)와 정확히 일치 |
| **정규화** | `normalize_embeddings=true` | L2 정규화, 코사인 거리 최적화 |
| **배치 크기** | 32 | 메모리 효율성 |
| **진행율 표시** | False | 프로덕션 로그 간결성 |

### 5.3 Startup 검증

```python
@app.on_event("startup")
async def startup():
    embed_service = EmbeddingService()
    embed_service.load()  # ← 1024차원 단언
    app.state.embed_service = embed_service
    logger.info("Learning service ready")
```

---

## 6. 크롤링 소스 (12종)

### 6.1 크롤러 목록 및 카테고리

| 크롤러 파일 | source 값 | category 값 | content_type | 일일 한도 | 상태 |
|-----------|---------|----------|-------------|----------|------|
| `naver_comments.py` | `naver_news` | `relationship_conflict` | comment | 500 | ✅ |
| `daum_comments.py` | `daum_news` | `relationship_conflict` | comment | 500 | ✅ |
| `dcinside.py` | `dcinside` | post.gall_id (동적) | post | 100 | ✅ |
| `natepan.py` | `natepan` | `talk` | post | 400 | ✅ |
| `bobaedream.py` | `bobaedream` | `freeb` | post | 100+ | ✅ |
| `blind.py` | `blind` | `workplace` | post | 50+ | ✅ |
| `fmkorea.py` | `fmkorea` | `best` | post | 100+ | ✅ |
| `theqoo.py` | `theqoo` | `hot` | post | 100+ | ✅ |
| `clien.py` | `clien` | `freeboard` | post | 100+ | ✅ |
| `ppomppu.py` | `ppomppu` | `freeboard` | post | 100+ | ✅ |
| `ruliweb.py` | `ruliweb` | `freeboard` | post | 100+ | ✅ |
| `mlbpark.py` | `mlbpark` | `bullpen` | post | 100+ | ✅ |

**미참조 파일**: `dcinside_backup.py`, `natepan_backup.py` (무시)

### 6.2 일일 크롤링 산출량

```
Naver News (500) + Daum News (500) + DCInside (100) + NatePan (400)
+ BobaeDream (100+) + Blind (50+) + FMKorea (100+) + Theqoo (100+)
+ Clien (100+) + Ppomppu (100+) + Ruliweb (100+) + MLBPark (100+)
= 약 1,600~1,800개/일 추가
```

### 6.3 Naver News 크롤러 구조

```python
# 호출 흐름
search_keywords = [
    "남자친구 갈등", "시어머니 갈등", "직장 상사",
    "부부 싸움", "가족 관계", ...
]

for keyword in search_keywords:
    # 1. AJAX 뉴스 검색 → OID/AID 추출
    articles = naver_search(keyword)
    
    for article in articles:
        oid, aid = extract_oid_aid(article.url)
        
        # 2. JSONP 댓글 API 호출
        comments = fetch_comments_jsonp(oid, aid)
        
        for comment in comments:
            # 3. VEC_FromText로 DB 저장
            embedding = embed_service.embed(comment.text)
            db.insert(
                content=comment.text,
                content_type="comment",
                category="relationship_conflict",
                source="naver_news",
                embedding=embedding
            )

# User-Agent 로테이션 (4개)
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...",
    ...
]
```

---

## 7. Voice 강화 파이프라인 (persona_strengthener.py)

### 7.1 VOICE_SOURCE_MAP (현재)

```python
VOICE_SOURCE_MAP = {
    "NATEPAN": "natepan",
    "DCINSIDE": "dcinside",
    "BLIND": "blind",
    "FMKOREA": "fmkorea",
    "THEQOO": "theqoo",
    "CLIEN": "clien",
    "PPOMPPU": "ppomppu",
    "RULIWEB": "ruliweb",
    "MLBPARK": "mlbpark",
    "GENERAL": None,  # 말투 강화 미적용 (ARCALIVE/INVEN은 현재 미매핑)
}
```

**말투 특성**:
- **NatePan**: 감성적 반말, 1인칭 강조 ("나는", "내가")
- **DCInside**: 거친 반말, 줄임말 (ㄹㅇ, ㄷㄷ, ㅇㄱ)
- **Blind**: 냉소적 분석, 직설적
- **FM Korea**: 유머 감수성, 가벼운 톤
- **TheQoo**: 감정 표현, "아 이거", "헐"
- **Clien**: 객관적 정보성, 기술 톤
- **Ppomppu/Ruliweb/MLBPark**: 커뮤니티 특화 말투

### 7.2 강화 프로세스

```
AI 배심원 생성 시:
1. search_examples(query, category) → Top-5 예시 반환
2. voice_source = VOICE_SOURCE_MAP[orchestrator_persona]
3. voice_examples = [ex for ex in top_5 if ex.source == voice_source]
4. 말투_프롬프트 = persona_strengthener.strengthen(voice_examples)
5. 최종_프롬프트 += 말투_프롬프트
```

---

## 8. 품질 필터 (QualityFilter)

### 8.1 필터 규칙 (passes)

```python
class QualityFilter:
    UI_NOISE_TOKENS = re.compile(r'\[\s*(?:원본\s*보기|더보기|본문\s*바로가기)\s*\]')
    RECIPE_SIGNALS = re.compile(r'(?:레시피|재료|굽기|한\s*스푼|양념)')
    GALLERY_NOISE = re.compile(r'(?:흑백|컬러|사진|촬영|카메라)')
    
    @staticmethod
    def passes(text: str) -> bool:
        """
        조건:
        1. UI 토큰 포함 → False
        2. 레시피 신호 3개+ → False
        3. 갤러리 신호 2개+ AND 길이 < 200자 → False
        """
        if QualityFilter.UI_NOISE_TOKENS.search(text):
            return False
        if len(QualityFilter.RECIPE_SIGNALS.findall(text)) >= 3:
            return False
        if (len(QualityFilter.GALLERY_NOISE.findall(text)) >= 2 
            and len(text) < 200):
            return False
        return True
```

### 8.2 점수 계산 (score)

```python
@staticmethod
def score(text: str) -> float:
    """
    기본값: 1.0
    감점:
    - 마침표 없음: -0.3
    - 이상한 따옴표 (""｜「」): -0.2
    - 레시피 신호 각 1개: -0.1
    - 갤러리 신호 각 1개: -0.05
    
    가산:
    - 반말 특성 (임, 함, 됨, 거든): +0.1
    
    범위: 0.0 ~ 1.0
    """
    score = 1.0
    
    # 감점
    score -= 0.3 if re.search(r'[^.].{0,50}$', text) else 0  # 마침표 없음
    score -= 0.2 if re.search(r'[""｜「」【】『』]', text) else 0
    score -= len(QualityFilter.RECIPE_SIGNALS.findall(text)) * 0.1
    score -= len(QualityFilter.GALLERY_NOISE.findall(text)) * 0.05
    
    # 가산
    score += 0.1 if any(ending in text for ending in ['임', '함', '됨', '거든']) else 0
    
    return round(max(0.0, min(1.0, score)), 2)
```

### 8.3 MIN_QUALITY_SCORE

```bash
# .env
RAG_MIN_QUALITY=0.5
```

| 임계값 | 의미 |
|--------|------|
| `0.5` (기본값) | 명백한 잡음 차단, 불완전한 갈등 사연 허용 |
| `0.7` | 높은 품질만 선택 (Stage 1 까다로움) |
| `0.0` | 품질 필터 무시 (테스트용) |

---

## 9. MariaDB VECTOR 활용

### 9.1 벡터 저장 (VEC_FromText)

```python
# Python (임베딩 완료)
vec = [0.123, 0.456, ..., 0.789]  # 1024차원
vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
# → "[0.12300000,0.45600000,...,0.78900000]"

# SQL INSERT
INSERT INTO example_bank 
  (content, content_type, category, source, quality_score, embedding)
VALUES (
  'text',
  'post',
  'talk',
  'natepan',
  0.85,
  VEC_FromText('[0.12300000,0.45600000,...,0.78900000]')
);
```

### 9.2 벡터 검색 (VEC_DISTANCE_COSINE)

```sql
-- 1단계: 쿼리 벡터화
SET @query_vec = VEC_FromText('[0.111,...,0.222]');

-- 2단계: 거리 계산 및 정렬
SELECT 
  id, 
  content, 
  source,
  VEC_DISTANCE_COSINE(embedding, @query_vec) AS distance,
  1 - VEC_DISTANCE_COSINE(embedding, @query_vec) AS similarity
FROM example_bank
WHERE content_type = 'post'
  AND (category = 'talk' OR category IS NULL)
  AND quality_score >= 0.5
ORDER BY distance ASC  -- ← 거리 작을수록 유사
LIMIT 5;
```

### 9.3 거리 ↔ 유사도 관계

```
VEC_DISTANCE_COSINE(v1, v2) 출력 범위: [0.0, 2.0]

distance = 0.0  → 완전히 같음  → 유사도 = 1.0
distance = 0.5  → 중간 유사    → 유사도 = 0.5
distance = 1.0  → 직각        → 유사도 = 0.0
distance = 2.0  → 완전히 반대  → 유사도 = -1.0 (드물음)

유사도 = 1 - 거리 (또는 >= 0일 때만)

마킹:
- 거리 < 0.3  → 높은 유사도 (매우 관련)
- 거리 0.3~0.7 → 중간 유사도 (관련)
- 거리 > 0.7  → 낮은 유사도 (비관련)
```

---

## 10. 크롤링 일정 (APScheduler)

### 10.1 스케줄 설정

```python
# scheduler.py
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

scheduler = BackgroundScheduler()
scheduler.add_job(
    func=scheduled_crawl_all,
    trigger=CronTrigger(
        hour=3,
        minute=0,
        timezone="Asia/Seoul"
    ),
    id="crawl_all_daily",
    name="Daily crawl at 03:00 KST"
)
scheduler.start()
```

### 10.2 크롤링 순서 (순차 실행)

```python
async def scheduled_crawl_all():
    """매일 03:00 KST 실행"""
    sources = [
        "naver_news",       # 500
        "daum_news",        # 500
        "dcinside",         # 100
        "natepan",          # 400
        "bobaedream",       # 100+
        "blind",            # 50+
        "fmkorea",          # 100+
        "theqoo",           # 100+
        "clien",            # 100+
        "ppomppu",          # 100+
        "ruliweb",          # 100+
        "mlbpark"           # 100+
    ]
    
    for source in sources:
        try:
            await crawl_source(source)
            logger.info(f"✅ {source} crawled")
        except Exception as e:
            logger.error(f"❌ {source} failed: {e}")
```

### 10.3 활성화 조건

```bash
# .env 또는 docker-compose
AI_LEARNING_CRAWL_ENABLED=true  # 기본값: false
```

---

## 11. SaveRequest & SaveExample

### 11.1 SaveRequest 모델

```python
from pydantic import BaseModel
from typing import Optional

class SaveRequest(BaseModel):
    content: str                          # 필수
    content_type: str                     # 필수: "post", "comment", "reply"
    category: Optional[str] = None        # optional: "talk", "freeboard", ...
    source: str = "SELF_GENERATED"        # 기본값
    quality_score: Optional[float] = None # 수동 지정 가능
```

### 11.2 save_example 함수

```python
async def save_example(req: SaveRequest, db_conn):
    """
    1. QualityFilter.passes() → 잡음 차단
    2. QualityFilter.score() → 자동 점수 계산
    3. EmbeddingService.embed() → 1024차원 벡터화
    4. INSERT INTO example_bank
    """
    
    # Step 1: 품질 필터링
    if not QualityFilter.passes(req.content):
        raise ValueError("Content fails quality filter (noise detected)")
    
    # Step 2: 품질 점수 (수동 지정 또는 자동 계산)
    quality_score = req.quality_score or QualityFilter.score(req.content)
    
    # Step 3: 임베딩
    embedding = embed_service.embed(req.content[:512])
    
    # Step 4: DB 저장 (topic은 현재 미포함, Phase 4에서 classifier 추가)
    query = """
    INSERT INTO example_bank 
      (content, content_type, category, source, quality_score, embedding, created_at)
    VALUES 
      (%s, %s, %s, %s, %s, VEC_FromText(%s), NOW(3))
    """
    
    cursor = db_conn.cursor()
    cursor.execute(query, (
        req.content,
        req.content_type,
        req.category,
        req.source,
        quality_score,
        f"[{','.join(f'{v:.8f}' for v in embedding)}]"
    ))
    db_conn.commit()
    
    return {"id": cursor.lastrowid, "status": "saved"}
```

**주의**: `topic` 컬럼은 현재 INSERT에 포함되지 않음. Phase 4에서 분류기(classifier) 추가 예정.

---

## 12. 통합 흐름: LLM 생성 ↔ Learning RAG

```mermaid
flowchart TD
    User["사용자 글 제출<br/>(FE)"] -->|POST /post| BE["Backend<br/>Spring Boot"]
    BE -->|/v1/invoke| LLMBridge["LLM Bridge<br/>ai-user-llm"]
    LLMBridge -->|orchestrate| Orch["Orchestrator<br/>(orchestration)"]
    
    Orch -->|search_examples| Learning["Learning 서비스<br/>POST /examples/search"]
    Learning -->|query: topicSeed| Embed["EmbeddingService<br/>embed(topicSeed)"]
    Embed -->|1024차원 벡터| MariaDB["MariaDB<br/>VEC_DISTANCE_COSINE"]
    MariaDB -->|Top-5 유사| Learning
    
    Learning -->|ExampleItem[]| Orch
    Orch -->|dynamicExamples 주입| PromptAsm["PromptAssembler<br/>(shared/prompts)"]
    PromptAsm -->|완성 프롬프트| Claude["Claude API<br/>(claude-haiku)"]
    Claude -->|AI 배심원 생성| LLMBridge
    LLMBridge -->|JSON 응답| BE
    BE -->|응답 저장| DB["PostJudgment<br/>(DB)"]
    DB -->|FE 표시| FE["Frontend<br/>배심원 표시"]
```

---

## 13. 환경 설정

### 13.1 .env 파일

```bash
# 크롤링
AI_LEARNING_CRAWL_ENABLED=true

# DB
DB_HOST=mariadb
DB_PORT=3306
DB_NAME=againspring
DB_USER=root
DB_PASSWORD=your_password

# FastAPI
UVICORN_HOST=0.0.0.0
UVICORN_PORT=8099

# RAG 품질 필터
RAG_MIN_QUALITY=0.5

# 크롤링 스케줄
CRAWLER_RUN_HOUR=3
CRAWLER_TIMEZONE=Asia/Seoul
```

### 13.2 requirements.txt

```
fastapi==0.115.0
uvicorn[standard]==0.32.0
sentence-transformers==3.3.1
torch==2.5.1+cpu
PyMySQL==1.1.1
playwright==1.49.0
python-dotenv==1.0.1
httpx==0.28.0
apscheduler==3.10.4
pydantic==2.10.0
beautifulsoup4==4.12.3
requests==2.32.3
```

---

## 14. API 사용 예시

### 14.1 예시 저장

```bash
curl -X POST http://localhost:8099/examples/save \
  -H "Content-Type: application/json" \
  -d '{
    "content": "남편이 내 개인정보를 봤어. 정말 화난다",
    "content_type": "post",
    "category": "freeboard",
    "source": "naver_news",
    "quality_score": 0.9
  }'

응답:
{
  "id": 5678,
  "status": "saved"
}
```

### 14.2 유사 예시 검색

```bash
curl -X POST http://localhost:8099/examples/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "배우자가 내 사생활을 침해했어",
    "content_type": "post",
    "category": "freeboard",
    "top_k": 5
  }'

응답:
[
  {
    "id": 5678,
    "content": "남편이 내 개인정보를 봤어...",
    "source": "naver_news",
    "title": "남편의 일기장 침해",
    "source_url": "https://www.naver.com/...",
    "score": 0.89
  },
  {
    "id": 5679,
    "content": "엄마가 내 폰을 봤대...",
    "source": "blind",
    "score": 0.84
  }
]
```

### 14.3 단일 예시 조회 (원본 비교용)

```bash
curl http://localhost:8099/examples/12345

응답:
{
  "id": 12345,
  "content": "남편이 내 개인정보를 봤어. 정말 화난다",
  "source": "naver_news",
  "title": "남편의 일기장 침해",
  "source_url": "https://www.naver.com/...",
  "score": null
}
```

**목적**: "원본 비교" 기능에서 크롤링된 원문(title + source_url)을 AI 재구성 생성 시에 참고 자료로 활용

### 14.4 소스별 통계

```bash
curl http://localhost:8099/examples/count

응답:
{
  "naver_news": 2850,
  "daum_news": 2400,
  "dcinside": 950,
  "natepan": 1850,
  "bobaedream": 650,
  "blind": 480,
  "fmkorea": 520,
  "theqoo": 550,
  "clien": 620,
  "ppomppu": 580,
  "ruliweb": 540,
  "mlbpark": 480,
  "SELF_GENERATED": 280
}
```

### 14.5 헬스체크

```bash
curl http://localhost:8099/health

응답:
{
  "status": "ok",
  "embedding_model": "KURE-v1",
  "embedding_dim": 1024,
  "db_connected": true
}
```

---

## 15. 문제 해결 & 트러블슈팅

### 15.1 모델 로드 실패

```
에러: RuntimeError: KURE-v1 embedding dimension mismatch: expected 1024, got 768
원인: 모델 버전 불일치 또는 캐시 손상
해결:
1. HuggingFace 캐시 삭제: rm -rf ~/.cache/huggingface
2. 모델 재다운로드: python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('nlpai-lab/KURE-v1')"
3. 로그 확인: grep "Embedding model loaded" logs/learning.log
```

### 15.2 3단계 폴백 과도 발동

```
증상: 로그에 "Stage 3 fallback" 빈번
원인: 크롤러 category ≠ 앱 카테고리, 또는 MIN_QUALITY_SCORE 너무 높음
해결:
1. RAG_MIN_QUALITY를 0.5 → 0.3으로 낮춤
2. Stage 1/2 카테고리 매핑 확인
3. 크롤러 category 값 확인: SELECT DISTINCT category FROM example_bank
```

### 15.3 VECTOR 쿼리 느림

```
증상: /examples/search가 5초 이상 소요
원인: VEC_DISTANCE_COSINE이 sequential scan
해결:
1. 벡터 인덱스 추가 (Phase 3):
   ALTER TABLE example_bank 
   ADD VECTOR INDEX idx_embedding (embedding) USING COSINE;
2. 쿼리 EXPLAIN 확인: EXPLAIN SELECT ... ORDER BY VEC_DISTANCE_COSINE(...)
3. 데이터 통계 갱신: ANALYZE TABLE example_bank;
```

### 15.4 스케줄 미실행

```
증상: 03:00 KST에 크롤링 미시작
원인: AI_LEARNING_CRAWL_ENABLED=false 또는 스케줄러 미시작
해결:
1. .env 확인: grep AI_LEARNING_CRAWL_ENABLED .env
2. 재시작: docker restart ai-user-learning
3. 로그: tail -f logs/learning.log | grep "scheduled_crawl"
4. 수동 트리거 (테스트): curl -X POST http://localhost:8099/crawl/naver_news
```

### 15.5 DB 연결 실패

```
에러: pymysql.err.OperationalError: (2003, "Can't connect to MySQL server")
원인: DB_HOST/DB_PORT/DB_USER/DB_PASSWORD 오류
해결:
1. .env 확인: mysql -h DB_HOST -u DB_USER -p
2. 마리아DB 상태: docker ps | grep mariadb
3. example_bank 테이블 확인: SHOW TABLES IN againspring;
4. 벡터 지원 확인: SELECT VERSION();  (11.8+ 필요)
```

---

## 16. 성능 최적화

### 16.1 배치 임베딩

```python
# 크롤러가 여러 글을 한 번에 임베딩
texts = [글1, 글2, ..., 글100]
embeddings = embed_service.embed_batch(texts)
# → 배치 처리로 30% 속도 향상
```

### 16.2 캐싱 전략

```python
# Redis 캐시 (선택, Phase 2+)
@cache.cached(timeout=3600)
def search_examples_cached(query: str, category: str, top_k: int):
    return search_examples(query, category, top_k)
```

### 16.3 DB 연결 풀링

```python
# SQLAlchemy 풀 (선택, Phase 2+)
from sqlalchemy import create_engine

engine = create_engine(
    'mysql+pymysql://user:pass@host/db',
    pool_size=20,
    max_overflow=40,
    pool_recycle=3600
)
```

---

## 17. 모니터링 & 로깅

### 17.1 로그 설정

```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/app/logs/learning.log'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)
logger.info("Learning service started")
logger.warning("Stage 3 fallback triggered")
logger.error("KURE-v1 model load failed")
```

### 17.2 주요 메트릭

```sql
-- 소스별 데이터 통계
SELECT source, COUNT(*) AS cnt, AVG(quality_score) AS avg_quality
FROM example_bank
GROUP BY source
ORDER BY cnt DESC;

-- 일별 추가량
SELECT DATE(created_at) AS date, COUNT(*) AS daily_cnt
FROM example_bank
GROUP BY DATE(created_at)
ORDER BY date DESC
LIMIT 30;

-- 카테고리별 품질 분포
SELECT category, 
       COUNT(*) AS cnt,
       MIN(quality_score) AS min_q,
       AVG(quality_score) AS avg_q,
       MAX(quality_score) AS max_q
FROM example_bank
GROUP BY category;
```

---

## 18. 아키텍처 요약

```mermaid
graph TB
    FE["Frontend<br/>(Next.js)"]
    BE["Backend<br/>(Spring Boot)"]
    Orch["Orchestrator<br/>(orchestration)"]
    Learn["Learning 서비스<br/>(FastAPI:8099)"]
    MDB["MariaDB<br/>example_bank"]
    KURE["KURE-v1<br/>Model<br/>(1024-dim)"]
    Crawlers["12개 크롤러<br/>(APScheduler)"]
    
    FE -->|REST| BE
    BE -->|/v1/invoke| Orch
    Orch -->|POST /examples/search| Learn
    Learn -->|embed()| KURE
    Learn -->|VEC_DISTANCE_COSINE| MDB
    Crawlers -->|save_example()| Learn
    Learn -->|VEC_FromText()| MDB
    
    style Learn fill:#ff9999
    style MDB fill:#99ccff
    style KURE fill:#99ff99
    style Crawlers fill:#ffcc99
```


## 19. 문체 현실화 (2026-06-11)

**`POST /examples/style-sample` — 문체 앵커 샘플링**
- 주제 무관, `source`(voice)·`register`·`content_type`만 일치하는 랜덤 예시 (`ORDER BY RAND()`, 임베딩 미사용).
- `SELF_GENERATED` 제외 필수 (자기 출력 재학습 → AI투 증폭 루프 방지). 품질 하한 max(0.6, RAG_MIN_QUALITY).
- 폴백: 소스+타입 → 소스 완화 → COMMENT 부족 시 같은 소스의 짧은 POST(≤200자).
- 오케스트레이터 `AiLearningClient.styleSample()`이 댓글(3개)·대댓글(2개) 생성마다 호출.

**커뮤니티 댓글 코퍼스 (크롤러 확장)**
- `natepan.py`: 글 상세의 `.cmt_list dd.usertxt` → COMMENT 수집 (글당 10, 일 200 한도).
- `clien.py`: `.comment_row .comment_view` → COMMENT 수집 (존댓말 레지스터 커버). 목록 셀렉터도 `a.list_subject`로 수정 (기존 셀렉터 방향 반대라 0건이었음).
- theqoo는 댓글이 AJAX 로딩이라 제외.

**페르소나 예시 풀 확장 (persona_strengthener)**
- `expand_persona_example_pools()`: voice_profile의 `example_comments` 12개 / `example_replies` 8개로 확장.
- 수제 예시는 `pool_meta.curated_*`로 보존, 크롤 추가분만 매일 새벽 회전. 페르소나마다 다른 랜덤 서브셋 (동일 voice 간 획일화 방지).
- 전용 크롤러 없는 voice(FMKOREA/PPOMPPU/BLIND/MLBPARK/ARCALIVE/INVEN 등)는 혼합 소스 폴백.

**camelCase 정합 수정 (중요 버그픽스)**
- orchestrator(Java)·backend는 `contentType`/`topK` 등 camelCase JSON을 보내는데 pydantic 모델이 snake_case뿐이라
  ① `/examples/search`의 content_type·register·top_k 필터가 조용히 무시되고 ② `/examples/save`는 필수 필드 누락 422로 전부 실패했었음 (backend 첨삭본 환류 포함).
- `CamelCompatModel`(populate_by_name + to_camel alias)로 snake/camel 양쪽 수용.

---

---

## 20. ML 판별기 서비스 (WSL GPU · 포트 8201)

> 8099 learning 서비스와 별개. WSL RTX 3090 전용 Python FastAPI.
> **현재 프로덕션 미연결** — `AI_USER_ML_ENABLED=false` (D-17: 5조건 충족 후 수동 활성화만 허용)

### 서비스 개요

| 항목 | 값 |
|---|---|
| 호스트 | `100.115.252.61:8201` |
| 인증 | `Bearer aiuser-ml-api-token-dev-2026` |
| 특징 | sklearn Pipeline (StandardScaler + LogisticRegression) |
| 특징 벡터 | KcELECTRA 768-dim + KatFishNet 9-dim = **777-dim** |
| GPU | RTX 3090 (CUDA 12.4) |

### KatFishNet 특징 (9차원)

`comma_rate`, `connector_rate`, `spacing_error_rate`, `pos_ngram_diversity`, `ending_variety`, `avg_sentence_length` 외 스타일 메타 지표. 한국어 커뮤니티 특화 hand-crafted 피처.

### 역할 (Best-of-N 리랭킹 — 현재 비활성)

AI 초안 N개를 생성 → 판별기 `P(human)` 점수로 가장 인간적인 초안을 선택 → BE로 제출.
`ActionExecutor.java`가 `AI_USER_ML_ENABLED=true`일 때 ML 서비스를 호출.

### 주요 API

| 경로 | 설명 |
|---|---|
| `GET /health` | 판별기 로드 상태 |
| `POST /corpus/ingest` | human/ai 코퍼스 적재 (content_hash SHA-256 globally unique dedup) |
| `GET /corpus/stats` | 커뮤니티별 n_human/n_ai 카운트 |
| `POST /train` | GPU 학습 트리거 (idempotencyKey 필수) |
| `GET /train/{job_id}` | 학습 상태 폴링 |
| `POST /rerank` | Best-of-N 리랭킹 (현재 미사용) |
| `POST /corpus/export/blind` | tell-scan용 AI 코퍼스 export |

### cond5 프록시 게이트 (tell-scan 방법론)

AI 생성 글 20쌍을 Claude ensemble judge(4명) 3-seed로 판정 → `proxy_accuracy` 측정.
보정 게이트: `human_est_upper = min(1.0, proxy + 0.54)`. 상한 ≤ 0.60이어야 PROXY-PASS.

### 연구 결과 이력 (2026-06-21 기준)

| 라운드 | 방식 | proxy mean | 판정 |
|---|---|---|---|
| r15 (Step 85) | 리랭킹 없음 (baseline) | 0.150 | PROXY-FAIL (upper=0.69), 현재 최선 |
| r16 (Step 90) | ML Best-of-4 | 0.283 | **PROXY-FAIL** (D-105) |
| r17 (Step 91) | Rule-based Best-of-4 | 0.317 | **PROXY-FAIL** (D-106) |

**D-106 결정 (2026-06-21)**: Best-of-4 리랭킹 전면 폐기.
- 근본 원인: 4개 후보 중 극단 초안 선택 편향 → ML=과격식, rule=과캐주얼, 둘 다 탐지 용이
- 탐지 신호 = 내러티브 구조/일관성/어휘, formality 레벨 아님

### 다음 단계: Step 93 — 단일 초안 생성 품질 강화

리랭킹 없이 1-shot 생성 자체를 개선하는 방향으로 전환.

| 항목 | 내용 |
|---|---|
| SELF_CRITIQUE_EXTRA_CLICHES 확장 | r15 관측 AI 티 패턴 목록 추가 (주기 반복·과구조화 서사·감정 추상명사 직서술) |
| 페르소나 보이스 다양화 | casual/tense/fragmented 스타일 변형 확대 |
| 프롬프트 자기비판 강화 | "AI 티 제거" 기준 명시 — 클리셰·내러티브 과구조화 경계 문구 추가 |
| 목표 | r18 3-seed proxy mean ≤ 0.150 (r15 baseline 유지 or 개선) |

### 판별기 활성화 조건 (D-17 불변)

아래 5조건 전부 충족 후 **사람이 수동으로** `AI_USER_ML_ENABLED=true` 설정. 코드 변경 금지.

| 조건 | 설명 | 현황 |
|---|---|---|
| cond1 | n_ai≥100 AND n_human≥300 (커뮤니티별) | THEQOO ✅ NATEPAN ✅ CLIEN ✅ |
| cond2 | CV-AUC ≥ 0.95 | THEQOO 0.9976 ✅ |
| cond3 | SPLITTER_VERIFIED | ✅ |
| cond4 | MAUVE Δ > 0 (리랭커 > random) | THEQOO +0.1380 ✅ NATEPAN ❌ CLIEN ❌ |
| cond5 | proxy ≤ 0.15 AND human_est_upper ≤ 0.60 | 전 커뮤니티 PROXY-FAIL |

---

**마지막 업데이트**: 2026-06-21 · **담당**: Claude Code Agent
**이력/변경사항 없음** — 현재 구현 기준 전면 재작성
