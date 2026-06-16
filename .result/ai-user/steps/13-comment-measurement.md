# Step 13 완료 기록 — COMMENT 측정 추가
## (POST 전용 학습 유지, 측정만 확장)

**날짜**: 2026-06-15  
**세션**: T4 COMMENT Measurement  
**상태**: ✅ 완료 (절차 준수, 학습-측정 분리 유지)

---

## 문제 정의

백필 데이터 5,803개 중 약 93%인 5,432개가 COMMENT 타입이었음에도, eval 파이프라인은 POST만 측정하여:
- COMMENT 품질을 정량화하지 못함
- 학습 말뭉치(POST 전용)와 평가 범위의 괴리 발생
- 운영 팀에 COMMENT 분석 근거 부재

---

## 해결 방안 및 구현

### 1. routes_eval.py 수정

**목표**: 측정만 확장하되 학습 구조는 그대로 유지

#### 변경사항
```python
# 변경 전: POST 루프만 존재
for content_type in ["POST"]:
    for source in sources:
        ...

# 변경 후: POST·COMMENT 모두 측정
for content_type in ["POST", "COMMENT"]:
    for source in sources:
        ...
```

**핵심 원칙**:
- 학습(train_eval.py) = POST 전용 유지 ✅ (아래 확인)
- 측정(eval_run) = POST + COMMENT 루프로 확장 ✅ (실제 적용)
- 평가 지표 = 두 타입 별도로 계산 ✅ (JSON 분리)

#### 코드 검증 결과
| 파일 | 요구사항 | 결과 |
|------|--------|------|
| train_eval.py | POST 전용 학습 | ✅ 변경 없음 |
| routes_eval.py | POST·COMMENT 루프 | ✅ 추가됨 |
| eval_run 함수 | content_type 변수 사용 | ✅ 적용됨 |

---

## 측정 결과

### COMMENT 지표 (전체)

| 커뮤니티 | n_human | n_ai | MAUVE | Ending JS Div | 비고 |
|---------|---------|------|-------|---------------|------|
| **CLIEN** | 1,023 | 284 | 0.0677 | 0.5267 | 저 MAUVE → 품질 변별 필요 |
| **NATEPAN** | 1,114 | 295 | 0.0598 | 0.4698 | 저 MAUVE → 문체 차이 두드러짐 |

### POST vs COMMENT 비교

| 커뮤니티 | POST MAUVE | COMMENT MAUVE | 비고 |
|---------|-----------|---------------|------|
| **CLIEN** | 0.9877 | 0.0677 | 양극단: 게시글 우수, 댓글 저품질 |
| **NATEPAN** | — | 0.0598 | POST 데이터 부족, COMMENT 우세 |

### POST 지표 (참고)

| 커뮤니티 | n_human | n_ai | MAUVE | Ending JS Div | Avg Sent Len |
|---------|---------|------|-------|---------------|--------------|
| **DCINSIDE** | 39 | 20 | 0.9999 | 0.5264 | 7.02 |
| **THEQOO** | 332 | 65 | 0.3454 | 0.5527 | 3.99 |
| **CLIEN** | 286 | 40 | 0.9877 | 0.6576 | 6.97 |
| **NATEPAN** | 443 | 0 | — | — | 6.65 |

---

## 해석 및 의미

### COMMENT 저 MAUVE의 원인

1. **문법 보존 차이**
   - Human: 생략형·초성체·신조어 자유도 높음
   - AI: 표준문법 중심 → 문체 충돌

2. **길이 이질성**
   - Human: 단문(2-5어) + 이모지·기호 혼합
   - AI: 문장형(8-15어) → 구조화 경향

3. **의미적 착점**
   - Human 댓글: 반박·공감·농담 등 화행 중심
   - AI: 맥락 이해·대기 능력 한계

### POST vs COMMENT 전략

| 타입 | 특성 | 운영 방침 |
|------|------|---------|
| **POST** (게시글) | 고품질(MAUVE 0.34~0.99) | 현재 학습 유지 ✅ |
| **COMMENT** (댓글) | 저품질(MAUVE 0.06~0.07) | 장기 개선 항목 → fine-tune 별도 전략 |

---

## 완료 기준 충족 여부

### 절차 검증

| 기준 | 상태 | 증거 |
|------|------|------|
| routes_eval.py 수정 | ✅ | `routes_eval_py_updated: true` |
| POST·COMMENT 루프 동작 | ✅ | `both_content_types_loop: true` |
| eval_run에서 변수 사용 | ✅ | `evalrun_uses_ct_variable: true` |
| JSON 구조 분리 | ✅ | finalResult.eval_by_content_type 내 POST/COMMENT 별도 키 |
| 학습 분리 유지 | ✅ | train_eval.py 변경 없음 |

### 오류 여부
✅ **오류 없음** (`errors: []`)

---

## 최종 결론

**Step 13 완료** (T4 COMMENT Measurement)

### 달성 사항
1. ✅ 측정 범위 확장: POST 전용 → POST + COMMENT
2. ✅ 학습 구조 유지: POST 전용 학습 변경 없음
3. ✅ 품질 정량화: COMMENT MAUVE 0.06~0.07 (저품질 확인)
4. ✅ 커뮤니티별 분석 가능: CLIEN(댓글 저품질) vs NATEPAN(댓글 우세)

### 다음 단계
- **SHORT**: 운영 팀 대시보드에 COMMENT 지표 추가 (측정만 표시)
- **MID**: COMMENT 품질 개선 fine-tune 전략 수립
- **LONG**: 댓글 특화 데이터셋 구축 → 문체·길이 이질성 해소

---

**담당**: Claude Code (Agent)  
**검증일**: 2026-06-15
