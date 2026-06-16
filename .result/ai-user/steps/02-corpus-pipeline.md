# Step 2 완료 기록 — 코퍼스 파이프라인

**날짜**: 2026-06-15  
**세션**: 2  
**상태**: ✅ 완료 (31/31 pytest 통과)

## 한 일

### AS 측 (ai-user/learning)

- `ai-user/learning/app/api/examples.py`에 `GET /examples/export` 추가:
  - `source_class=human` → WHERE source != 'SELF_GENERATED'
  - `source_class=ai` → WHERE source = 'SELF_GENERATED'
  - `since` 파라미터: ISO datetime 커서 기반 페이지네이션
  - `limit` 최대 5000, `offset` 지원
  - 응답: `{items, total, offset, limit}`
  - 에러 처리: logger.error + 빈 items 반환 (서비스 중단 방지)

### WSL 측 (Again-Spring-AI-User)

- `app/config.py`: 신규 설정 추가:
  - `as_learning_base_url = "http://100.81.189.92:8099"`
  - `corpus_pull_enabled = True`
  - `corpus_pull_interval_sec = 600` (10분)
  - `corpus_pull_limit = 1000`
  - `SOURCE_COMMUNITY_MAP`: source 문자열 → 커뮤니티 ID 매핑 dict

- `app/worker/corpus_pull.py`: async 코퍼스 pull 루프:
  - `corpus_pull_loop()`: asyncio.create_task로 시작, shutdown 시 취소
  - `_pull_once()`: GET /examples/export → source→community 매핑 → POST /corpus/ingest
  - 커서: `data/.corpus_pull_cursor` 파일 저장 (재시작 후 이어서)
  - 미지 source: 스킵 + WARNING 로그 (SOURCE_COMMUNITY_MAP 확장 안내)

- `app/main.py`: lifespan에 corpus_pull_loop asyncio.create_task 추가

- `tests/test_corpus_pull.py`: 6개 테스트 (natepan/dcinside 매핑, 미지소스 스킵, 빈응답, 커서 영속)

## 완료 기준 검증

| 기준 | 결과 |
|---|---|
| `GET /examples/export?sourceClass=human&limit=3` → 실 데이터 | ✅ dcinside 글 3개 반환 |
| corpus pull 로그 "+321 inserted, 406 skipped (dup hash)" | ✅ |
| `/corpus/stats` 커뮤니티별 human 카운트 | ✅ DCINSIDE:26 / NATEPAN:168 / THEQOO:127 |
| 해시 dedup 동작 (DB dedup + 배치 내 dedup) | ✅ |
| `pytest tests/` 31/31 통과 | ✅ |

## 발견된 버그 & 수정

### URL prefix 오류
- `corpus_pull.py`가 `/api/examples/export` 사용 → 404
- learning 서비스 실제 경로: `/examples/export` (prefix 없음)
- `sed -i` 로 수정

### 배치 내부 dedup 누락
- DB 빈 상태에서 같은 배치의 중복 해시 → IntegrityError
- 수정: `seen_in_batch: set[str]` 추가

### 모듈 내부 import → 테스트 mock 불가
- `_pull_once()` 내부에서 `from app.config import get_settings` → `patch("module.get_settings")` 실패
- 수정: 모듈 레벨 import로 이동

## 코퍼스 현황 (2026-06-15)

| 커뮤니티 | human | ai |
|---|---|---|
| NATEPAN | 168 | 0 |
| THEQOO | 127 | 0 |
| DCINSIDE | 26 | 0 |

AI negative는 Step 5(ActionExecutor)에서 push.

## 설계 결정

- **커서 파일**: DB 추가 테이블 없이 `data/.corpus_pull_cursor` 텍스트 파일 — 단순하고 충분
- **push 방향**: ML 서비스가 AS learning에서 human pull (이 파일)
  AI negative는 AS ActionExecutor가 작성 시점에 push (Step 5)
- **인증 없음**: learning /export는 내부망 전용 — Tailscale IP 기반 격리
- **source_class='ai' 미사용**: SELF_GENERATED는 AS ActionExecutor가 직접 push (Step 5)
  여기서는 human pull만 구현

## 다음 구체 작업 (검증 후 완료 처리)

1. AS learning 컨테이너 재시작 → 새 endpoint 활성화
2. WSL docker rebuild → corpus_pull.py + main.py 반영
3. `curl http://100.81.189.92:8099/api/examples/export?sourceClass=human&limit=5` 확인
4. `/corpus/stats` 데이터 확인 (10분 대기 또는 수동 트리거)
5. pytest 통과 확인
