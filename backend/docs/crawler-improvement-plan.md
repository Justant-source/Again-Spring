# 크롤러 개선 계획: 뉴스 댓글 → 실제 커뮤니티 글/댓글/대댓글

> **목표**: 6개 커뮤니티에서 뉴스 기사 댓글이 아니라, 사람이 직접 쓴 **갈등글, 감정글, 질문글** 과 그 **댓글, 대댓글**을 집중 수집

---

## 1. 현재 상태 분석

### 1.1 문제점

| 크롤러 | 현재 타겟 | 문제 | 수집량 |
|---|---|---|---|
| **naver_comments.py** | 뉴스 기사 댓글 (공개 API) | "기사" 맥락 부족, 한두 줄 댓글 | ✅ 500/일 (빠름) |
| **daum_comments.py** | 뉴스 기사 댓글 (공개 API) | 동일 | ✅ 500/일 (빠름) |
| **dcinside.py** | 갈등/인생 갤러리 (원글만) | ❌ 댓글·대댓글 미수집 | ❌ 0 (미작동) |
| **natepan.py** | 남녀탐구생활 (원글만) | ❌ 댓글·대댓글 미수집 | ❌ 0 (미작동) |
| **bobaedream.py** | 보배드림 (원글만) | ❌ 댓글·대댓글 미수집 | ❌ 0 (미작동) |
| **blind.py** | 블라인드 (원글만) | ❌ 댓글 미수집 | ❌ 0 (미작동) |

### 1.2 필요한 변경

```
현재 수집 구조:
  원글만 → POST로 저장

목표 수집 구조:
  ├─ 원글 → POST로 저장 (제목+본문)
  ├─ 댓글 → COMMENT로 저장
  └─ 대댓글 → COMMENT로 저장 (depth 표시)

예시:
  원글: "남자친구가 전여친 얘기를 자꾸 꺼냄" (POST)
  댓글1: "전여친 얘기 꺼내는 남친 정말 싫음" (COMMENT)
  대댓글1: "공감해요. 넘 답답했어요" (COMMENT, depth=2)
  댓글2: "얘기를 자꾸 안 하게 하려면..." (COMMENT)
```

---

## 2. 6개 크롤러별 개선 방안

### 2.1 디시인사이드 (dcinside.py) — 갈등 글판 + 댓글

**타겟 갤러리**:
- `life_incident` (인생/갈등)
- `love` (연애)
- `marriage` (결혼)

**수집 전략**:
```
1️⃣  갤러리 인기글 목록 → top-30 추출
2️⃣  각 글의 상세 페이지 접근
3️⃣  원글 정보 (제목, 본문, 글쓴이 닉네임 마스킹) → POST 저장
4️⃣  해당 글의 댓글 목록 (5~20개) → COMMENT 저장
5️⃣  대댓글도 있으면 → COMMENT 저장 (depth=2)
6️⃣  좋아요/조회수로 품질 점수 계산
```

**코드 구조**:
```python
# dcinside.py
async def crawl():
    for gallery in ['life_incident', 'love', 'marriage']:
        posts = await fetch_popular_posts(gallery, limit=10)  # 각 갤러리 10개
        
        for post in posts:
            # Step 1: 원글 저장
            post_data = {
                'content': post['title'] + '\n' + post['body'],
                'content_type': 'POST',
                'category': 'COUPLE',  # 갈등 분류
                'source': 'DCINSIDE'
            }
            await save_example(post_data)
            
            # Step 2: 댓글 + 대댓글 수집
            comments = await fetch_comments(post['id'], limit=15)
            
            for comment in comments:
                comment_data = {
                    'content': comment['text'],  # 댓글 본문만
                    'content_type': 'COMMENT',
                    'category': 'COUPLE',
                    'source': 'DCINSIDE'
                }
                await save_example(comment_data)
                
                # 대댓글도 수집
                for reply in comment.get('replies', []):
                    reply_data = {
                        'content': reply['text'],
                        'content_type': 'COMMENT',
                        'source': 'DCINSIDE'
                    }
                    await save_example(reply_data)
```

**일일 예상 수집량**: 30개 원글 × 15개 댓글 = **450건** (limit 100 기준)

---

### 2.2 네이트판 (natepan.py) — 남녀탐구생활 + 댓글

**타겟**:
- `남녀탐구생활` (베스트 글)
- 감성적, 개인적 갈등글 위주

**수집 전략**:
```
1️⃣  남녀탐구생활 베스트글 (좋아요순) → top-20 추출
2️⃣  각 글 상세 페이지 접근
3️⃣  원글 (제목+본문, 좋아요 수를 품질점수로 변환) → POST
4️⃣  댓글 (상위 20개, 좋아요순) → COMMENT
5️⃣  대댓글 → COMMENT
```

**일일 예상 수집량**: 20개 원글 × 20개 댓글 = **400건**

---

### 2.3 블라인드 (blind.py) — 직장 갈등글 + 댓글

**타겟**:
- 직장, 상사, 동료 갈등
- 비로그인 공개 글 (limit 확인 필수)

**수집 전략**:
```
1️⃣  직장/팀별 갈등글 → top-20
2️⃣  원글 (직장 분류) → POST (category=WORK)
3️⃣  댓글 (평균 10~15개) → COMMENT
```

**일일 예상 수집량**: 20개 원글 × 12개 댓글 = **240건**

---

### 2.4 보배드림 (bobaedream.py) — 감정글 + 댓글

**타겟**:
- 자유게시판 추천순 글
- 남성 감정글 (연애, 가족, 진로 등)

**수집 전략**:
```
1️⃣  추천순 글 → top-20
2️⃣  원글 (제목+본문) → POST
3️⃣  댓글 (최상위 15개) → COMMENT
```

**일일 예상 수집량**: 20개 원글 × 15개 댓글 = **300건**

---

### 2.5 네이버 뉴스 댓글 (naver_comments.py) — 유지 (속도 이점)

**현황**: ✅ 이미 500건/일 수집 중

**역할**: 보조 데이터 (메인은 커뮤니티 글)

**유지 이유**:
- 공개 API 사용 (안정적)
- 빠른 속도
- 뉴스 기사의 다양한 주제 커버

---

### 2.6 다음 뉴스 댓글 (daum_comments.py) — 유지 또는 제거

**현황**: 401 인증 에러

**선택**:
- ❌ 제거 (에러 해결 필요)
- ✅ 유지 (API 재조사 후 수정)

**추천**: 우선 **네이버 500 + 커뮤니티 1,000 = 1,500건/일**로 시작

---

## 3. 구현 체크리스트

### Phase 1: 핵심 3개 (dcinside, natepan, bobaedream)

```
[ ] dcinside.py 수정
    [ ] 인기글 목록 페이징 (top-30)
    [ ] 각 글의 상세 페이지 접근 (Playwright navigation)
    [ ] 댓글 + 대댓글 파싱 로직
    [ ] 저장 로직 (원글 → POST, 댓글/대댓글 → COMMENT)
    [ ] 테스트 (3개 갤러리 × 10글 = 30건 확인)

[ ] natepan.py 수정
    [ ] 남녀탐구생활 베스트글 크롤링
    [ ] 댓글 수집 로직
    [ ] 저장 로직
    [ ] 테스트 (20건 확인)

[ ] bobaedream.py 수정
    [ ] 추천순 글 크롤링
    [ ] 댓글 수집 로직
    [ ] 저장 로직
    [ ] 테스트 (20건 확인)
```

### Phase 2: 추가 개선

```
[ ] blind.py 수정
    [ ] 직장 갈등글 크롤링
    [ ] 댓글 수집
    [ ] 테스트

[ ] naver_comments.py 유지
    [ ] 필요 시 키워드 추가 ("연애", "가족", "직장")

[ ] daum_comments.py 재평가
    [ ] API 에러 원인 분석
    [ ] 복구 또는 제거 결정
```

---

## 4. 예상 결과

### 4.1 수집량 증대

| 원본 | 현재 | 목표 | 증가율 |
|---|---|---|---|
| 뉴스 댓글 | 1,000건/일 | 500건/일 | ↓ 50% (의도적) |
| **커뮤니티 글** | **0건** | **1,000건/일** | ↑ ∞ |
| **커뮤니티 댓글** | **0건** | **500건/일** | ↑ ∞ |
| **합계** | 1,000건/일 | **2,000건/일** | ↑ 2배 |

### 4.2 데이터 질 향상

**현재** (뉴스 댓글 위주):
```
예시:
"좋은 기사네요." (10자)
"동의합니다." (5자)
"완전 공감 ^^" (7자)
```

**목표** (커뮤니티 글 + 댓글):
```
예시:
원글: "남자친구가 3년 사귀면서 전여친 얘기 자꾸 해. 처음엔 괜찮았는데 이제 진짜 답답함. 어떻게 하지?" (70자)
댓글: "그건 정말 답답하겠네요. 한 번 진심으로 얘기해봤어요? 상대가 무심코 하는 건지 의식적인 건지 확인하고..." (50자)
대댓글: "맞아요. 솔직하게 감정을 표현하는 게 제일 중요한 것 같아요. 화내지 말고 '내가 이렇게 느껴'라고" (40자)
```

**향상도**:
- 텍스트 길이: 평균 7자 → 50자 (7배)
- 맥락 풍부도: "기사 댓글" → "개인 갈등 스토리" (무한)
- 자연스러움: 뉴스 댓글 특화 어투 제거

---

## 5. 타이밍 & 우선순위

### 즉시 (Week 1)

```
✅ dcinside.py 수정 (가장 중요, 안정적)
✅ natepan.py 수정 (감성글, 자연스러움)
✅ bobaedream.py 수정 (남성 감정글)
```

**기대 효과**: 1,000~1,200건/일 새로운 커뮤니티 글/댓글 수집

### Week 2

```
✅ blind.py 수정 (직장 갈등, 카테고리 확장)
🔍 daum_comments.py 재검토 (API 에러 해결)
```

### Week 3+

```
📊 수집 데이터 통계 분석
🎯 품질 개선 (여전히 뉴스 댓글이 섞여 있다면 비율 조정)
```

---

## 6. 로컬 테스트 (개발자용)

```bash
# 단일 크롤러 테스트
python3 -c "
import asyncio
from ai_learning.crawlers.dcinside import crawl

result = asyncio.run(crawl())
print(f'수집: {result[\"collected\"]}건')
print(f'저장: {result[\"saved\"]}건')
"

# 통합 테스트 (모든 크롤러 병렬)
python3 -c "
import asyncio
from ai_learning.crawlers import *

async def test_all():
    results = await asyncio.gather(
        dcinside.crawl(),
        natepan.crawl(),
        bobaedream.crawl(),
        blind.crawl(),
        naver.crawl(),
    )
    total = sum(r.get('saved', 0) for r in results)
    print(f'전체 수집: {total}건')

asyncio.run(test_all())
"
```

---

## 7. 모니터링 지표

### 매일 아침 체크

```bash
# 어제 수집량
curl http://localhost:8099/crawl/log | jq '.logs[-6:] | map(.source, .saved)'

# 예시뱅크에서 각 타입별 비율
SELECT 
  content_type,
  source,
  COUNT(*) as cnt,
  AVG(CHAR_LENGTH(content)) as avg_len
FROM example_bank
WHERE created_at > NOW() - INTERVAL 1 DAY
GROUP BY content_type, source
ORDER BY cnt DESC;
```

### 기대 결과 (1주일 후)

```
source       saved  avg_len  content_type
DCINSIDE      350   80char   POST
DCINSIDE      450   45char   COMMENT
NATEPAN       280   75char   POST
NATEPAN       400   40char   COMMENT
BOBAEDREAM    250   85char   POST
BOBAEDREAM    350   42char   COMMENT
NAVER        5000   15char   COMMENT  (기존)
─────────────────────────────────
합계        ~6,680  평균 35   

vs 현재 1,000건 → **6배 증가**
```

---

**다음 단계**: 이 계획을 바탕으로 각 크롤러를 수정하고, 1주일 후 결과를 검증합니다.

