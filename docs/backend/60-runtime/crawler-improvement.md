# 크롤러 개선 계획: 뉴스 댓글 → 실제 커뮤니티 글/댓글/대댓글

> **목표**: 여러 커뮤니티에서 뉴스 기사 댓글이 아니라, 사람이 직접 쓴 **갈등글, 감정글, 질문글** 과 그 **댓글, 대댓글**을 집중 수집

---

## 0. 현재 상태 (WO-CRAWL-01, 2026-07-30)

이 문서의 원래 계획(§1~§2)은 dcinside/natepan/bobaedream 3개를 Phase 1로 잡았으나,
실제로는 **natepan·blind 2개 소스를 집중 강화**하는 방향으로 갔다. dcinside·bobaedream·
theqoo·clien 등은 코드는 있지만 `scheduler.py`의 `SOURCES` 예산이 `0`이라 여전히
비활성이다 (§5 "미착수" 참조) — 아래 표가 실제 코드 기준 최신 상태다.

| 크롤러 | 상태 | 일일 예산 | 수집 내용 |
|---|---|---:|---|
| **natepan.py** | ✅ 활성 | 1,500 | 베스트글 섹션 + 9개 테마채널(회사생활·취업/면접·알바경험담·남편 VS 아내 등), 원글+댓글, 조회수·추천수·댓글수·댓글 작성시각 |
| **blind.py** | ✅ 활성 | 500 (2026-07-30 240→500, "갈등 소재 대세" 판단) | 결혼생활·썸·연애·회사생활 3채널, 갈등 키워드 필터, 조회수·좋아요·댓글수·게시시각(상대시각→절대시각 변환) |
| dcinside.py / bobaedream.py / theqoo.py / clien.py / fmkorea.py / ppomppu.py / ruliweb.py / mlbpark.py | ⏸️ 비활성 (limit=0) | 0 | 코드는 존재, §5 Phase 2 후보 |
| naver_comments.py / daum_comments.py | ⏸️ 비활성 (limit=0) | 0 | 뉴스 댓글 — 커뮤니티 글로 방향 전환하며 후순위 |

**natepan·blind는 이미 댓글/대댓글을 수집한다** — 원래 §1.1의 "원글만 수집" 문제는
이 두 소스에 한해 해소됐다. 관심도 지표(조회수·좋아요·댓글수·참여 시간폭)도
`example_bank`에 저장되고, 매일 KST 04:00 배치잡이 소스+나이구간 내 백분위로
점수화한다 (`popularity_pct`). 재가공 원본 선별은 이 점수를 가중치로 쓴다.
상세: `docs/ai-user/learning.md`.

---

## 1. 원래 문제 분석 (작성 당시 기준 — 현재는 §0 참조)

### 1.1 문제점 (2026-06 시점, 지금은 대부분 해소)

| 크롤러 | 당시 타겟 | 당시 문제 | 당시 수집량 |
|---|---|---|---|
| **naver_comments.py** | 뉴스 기사 댓글 (공개 API) | "기사" 맥락 부족, 한두 줄 댓글 | 500/일 (빠름) |
| **daum_comments.py** | 뉴스 기사 댓글 (공개 API) | 동일 | 500/일 (빠름) |
| **dcinside.py** | 갈등/인생 갤러리 (원글만) | 댓글·대댓글 미수집 | 0 (미작동, 지금도 미작동) |
| **natepan.py** | 남녀탐구생활 (원글만) | 댓글·대댓글 미수집 | **WO-CRAWL-01로 해소** — 댓글+관심도까지 수집 |
| **bobaedream.py** | 보배드림 (원글만) | 댓글·대댓글 미수집 | 0 (미작동, 지금도 미작동) |
| **blind.py** | 블라인드 (원글만) | 댓글 미수집 | **WO-CRAWL-01로 해소** — 댓글수+관심도까지 수집 |

### 1.2 목표했던 변경 (natepan·blind는 달성)

```
원래 수집 구조:
  원글만 → POST로 저장

목표 수집 구조:
  ├─ 원글 → POST로 저장 (제목+본문)
  ├─ 댓글 → COMMENT로 저장
  └─ 대댓글 → COMMENT로 저장 (depth 표시)
```

---

## 2. 아직 비활성인 크롤러 (Phase 2 후보 — §0 표 참조)

아래 §2.1·§2.4는 dcinside·bobaedream을 활성화할 때의 설계 스케치다 (미구현, 코드
샘플은 참고용 의사코드). 활성화하려면 `scheduler.py`의 `SOURCES` 예산을 0에서
올리고, natepan·blind에 이미 붙은 관심도 지표 파싱(view_count/like_count/
comment_count/comment_timestamps → `example_bank`)을 같은 방식으로 붙여야
`popularity_pct` 채점 대상에 들어간다.

### 2.1 디시인사이드 (dcinside.py) — 갈등 글판 + 댓글 (미구현)

**타겟 갤러리**:
- `life_incident` (인생/갈등)
- `love` (연애)
- `marriage` (결혼)

**수집 전략 스케치**:
```
1️⃣  갤러리 인기글 목록 → top-30 추출
2️⃣  각 글의 상세 페이지 접근
3️⃣  원글 정보 (제목, 본문, 글쓴이 닉네임 마스킹) → POST 저장
4️⃣  해당 글의 댓글 목록 (5~20개) → COMMENT 저장
5️⃣  대댓글도 있으면 → COMMENT 저장 (depth=2)
6️⃣  좋아요/조회수로 관심도 지표 계산 (natepan.py/blind.py 패턴 참고)
```

**일일 예상 수집량**: 30개 원글 × 15개 댓글 = 450건 (limit 100 기준, 미검증 추정치)

### 2.4 보배드림 (bobaedream.py) — 감정글 + 댓글 (미구현)

**타겟**:
- 자유게시판 추천순 글
- 남성 감정글 (연애, 가족, 진로 등)

**수집 전략 스케치**:
```
1️⃣  추천순 글 → top-20
2️⃣  원글 (제목+본문) → POST
3️⃣  댓글 (최상위 15개) → COMMENT
```

**일일 예상 수집량**: 20개 원글 × 15개 댓글 = 300건 (미검증 추정치)

### 2.5 네이버 뉴스 댓글 (naver_comments.py) / 다음 뉴스 댓글 (daum_comments.py)

- naver_comments.py: 공개 API, 안정적, 500건/일 가능 — 현재 예산 0 (커뮤니티 글 위주로 방향 전환하며 후순위)
- daum_comments.py: 401 인증 에러 (미해결) — 제거 또는 API 재조사 중 택1, 현재 예산 0

---

## 3. 구현 체크리스트

```
[x] natepan.py 댓글+관심도 수집 (WO-CRAWL-01, 2026-07-30)
[x] blind.py 댓글수+관심도 수집 (WO-CRAWL-01, 2026-07-30)
[ ] dcinside.py 수정 (미착수 — §2.1)
[ ] bobaedream.py 수정 (미착수 — §2.4)
[ ] blind.py 채널 확장 또는 예산 추가 증액 (403 차단율을 admin 배지로 관찰하며 판단)
[ ] daum_comments.py 재평가 (API 에러 원인 분석)
```

---

## 4. 모니터링

```bash
# 최근 크롤 로그 (at 필드는 ISO-8601 UTC, WO-CRAWL-01에서 포맷 수정됨)
curl http://localhost:8099/crawl/log | jq '.[0:10]'

# admin 대시보드 크롤 신선도 배지 (WO-CRAWL-01) — 24h 무크롤 시 stale 경고
# GET /api/admin/crawl-status (ADMIN JWT 필요)

# 예시뱅크에서 소스·타입별 최근 수집 현황 + 관심도 지표 채움률
SELECT
  source, content_type,
  COUNT(*) as cnt,
  AVG(CHAR_LENGTH(content)) as avg_len,
  SUM(view_count IS NOT NULL) as has_view_count,
  SUM(popularity_pct IS NOT NULL) as scored
FROM example_bank
WHERE created_at > NOW() - INTERVAL 1 DAY
GROUP BY source, content_type
ORDER BY cnt DESC;
```

---

## 5. 다음 단계

1. dcinside·bobaedream 등 §2 후보를 활성화할지 결정 — 활성화 시 관심도 지표
   파싱을 natepan/blind와 동일 계약(`view_count`/`like_count`/`comment_count`/
   `comment_timestamps`)으로 맞출 것 (`docs/ai-user/learning.md` 참조)
2. blind 예산 500 이후 403 차단율 관찰 → 추가 증액 여부 판단
3. 텔레그램 크롤 하트비트 알림 연동 (admin 배지의 후속 — 사용자 예약)
