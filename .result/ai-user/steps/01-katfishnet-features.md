# Step 1 완료 기록 — KatFishNet 피처 추출기 + Korean POS in Docker

**날짜**: 2026-06-15  
**세션**: 1  
**상태**: ✅ 완료 (24/24 pytest 통과)

## 완료 기준 검증

- [x] `pytest tests/test_features.py` 통과 (17개 샘플 테스트 전부)
- [x] GPU 미사용 확인 (kiwipiepy/scipy 모두 CPU)
- [x] 이미지 의존성 문서화: `Dockerfile`에 kiwipiepy 레이어 명시

## 구현 내용

### `app/ml/pos_tagger.py`

- `kiwipiepy 0.23.2` 싱글톤 (`Kiwi()`) — 첫 호출 시 lazy load
- `tag(text)` → `[(form, tag_str), ...]`
- `check_spacing(text)` → `(corrected, error_rate)` — `kiwi.space()` 사용
- 폴백: kiwipiepy 로드 실패 시 `_naive_tag()` (정규식 기반, 정확도 저하)

### `app/ml/features_katfish.py`

9개 피처 (KatFishNet-inspired, AS 커뮤니티 게시글용):

| 피처 | 설명 | AI vs 인간 방향 |
|---|---|---|
| `comma_rate` | 쉼표 수 / 어절 수 | AI가 높음 |
| `comma_back_rate` | 문장 뒤 1/3 위치 쉼표 비율 | AI가 높음 |
| `spacing_error_rate` | 띄어쓰기 오류율 | 인간이 높음 |
| `pos_ngram_diversity` | POS bigram unique/total | 인간이 높음 |
| `pos_trigram_diversity` | POS trigram unique/total | 인간이 높음 |
| `connector_rate` | 접속부사(그러나/따라서 등) 비율 | AI가 높음 |
| `ending_variety` | 문말 어미(EF) 종류 수 | 인간이 높음 |
| `avg_sentence_length` | 문장당 평균 어절 수 | AI가 긺 |
| `kiwi_available` | kiwipiepy 사용 여부 | —(메타) |

## 함정 (다음 세션이 알아야 할 것)

### kiwipiepy 0.23.x API 변경사항
```python
# 잘못된 (구버전 예시):
result[0].tokens  # AttributeError: 'tuple' has no 'tokens'
t.tag.name        # AttributeError: 'str' has no 'name'

# 올바른 (0.23.x):
result[0][0]      # tokens list (result는 [(tokens, score)] 튜플)
str(t.tag)        # 이미 'MAG', 'NNG' 등 문자열 (no .name needed)
```

### pydantic-settings mock 불가
```python
# 실패: Settings 클래스에 class-level attribute 없음
patch("app.config.Settings.api_token", TOKEN)  # AttributeError

# 올바른 방법:
mock_settings = MagicMock()
mock_settings.api_token = TOKEN
patch("app.config.get_settings", return_value=mock_settings)
patch("app.auth.get_settings", return_value=mock_settings)
```

### Dockerfile 패치
Python에서 `str.replace()` 사용 시 공백/개행 불일치로 실패 가능.
→ **통 덮어쓰기** (`open(..., 'w').write(전체내용)`) 가 안전.

### DC 스타일 POS 결과 예시
```python
tag("ㄹㅇ 진짜 어이없어 이 상황이")
# → [('ㄹㅇ', 'SW'), ('진짜', 'MAG'), ('어이없', 'VA'), ('어', 'EC'), ...]

tag("그러나 이 상황은 매우 복잡하며")
# → [('그러나', 'MAJ'), ('이', 'MM'), ('상황', 'NNG'), ('은', 'JX'), ...]
```

## Step 2가 알아야 할 것

### `example_bank` 테이블 구조 확인 필요
- `ai-user/learning/app/db/models.py`에서 컬럼 목록 확인
- 특히 `voice_id` 또는 커뮤니티 컬럼 존재 여부
- 없으면 → AI negative push 시 community를 ActionExecutor가 직접 전달 (Step 5 처리)

### 코퍼스 목표
- human positives: AS learning `GET /examples/export?sourceClass=human` pull
- AI negatives: ActionExecutor 작성 시점 push (Step 5와 함께)
- Step 3 베이스라인은 human 데이터만으로 시작 가능

### export 엔드포인트 위치
- AS 파일: `ai-user/learning/app/api/examples.py`
- 기존 `CamelCompatModel` 패턴 재사용
- `run_query` 함수 패턴 재사용 (같은 파일 내 기존 코드 참조)
