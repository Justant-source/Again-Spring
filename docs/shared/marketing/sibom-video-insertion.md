# 시봄이 숏폼 삽입 스펙 — Shorts / Reels

> **권위본**: 이 문서 (2026-08-12 그릴링 합의).  
> 캐릭터 자산·카탈로그: [`docs/frontend/design/specs/sprout-character-system/`](../../frontend/design/specs/sprout-character-system/) · 런타임 패키지: `.temp/sprouts/` (승격 예정).  
> 채널 계약: [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md).  
> **메타포 일러스트 60종은 영상 경로에서 완전 사용 금지.**

---

## 1. 목표

사물 메타포 대신 **시봄이 캐릭터**로 감정·상황을 시각화한다.  
인트로/썸네일 + 본문 중간에 넣고, 사연 TTS 텍스트와 **독립된** 짧은 캡션으로 이해를 돕는다.

---

## 2. 결정 로그 (그릴링)

| # | 결정 |
|---|---|
| 1 | 인트로+본문 시봄이. **메타포 완전 금지·런타임 언플러그** (파일 보관만) |
| 2 | 장수 = 품질 우선 + soft-fill 하이브리드 (억지 매칭 금지) |
| 3 | **전체 예산 4~7 (인트로 포함)** — Reels ≈4~5 / Shorts ≈5~7 |
| 4 | 사연 TTS 텍스트 = 메인 · 시봄이 캡션 = 독립 보조 메시지 |
| 5 | 피크 1~2 = 크게+상주 · 나머지 = 작게+펀치 · 인트로 = 크게 |
| 6 | 고르기 = **원문** · 배치 = **대본 타임라인** |
| 7 | 인트로·피크 = `hold` · 나머지 = `punch` |
| 8 | 1피크 = `hook_emotion` 정렬 · 2피크 = 결말/반전일 때만 |
| 9 | 인트로 = 1인·표정 중심 권장 · 본문 피크와 다른 id 권장 (강제 아님) |
| 10 | soft-fill = 1인 범용만 · 인트로/피크 불가 · id·`swap_group` 중복 금지 |
| 11 | 캡션 = 카탈로그 재사용 or 단문 생성 + 폴백 체인 |
| 12 | LLM은 **기존 2회만** (사연 생성 / 영상 직전). 시봄이 전용 3rd 콜 없음 |
| 13 | 1초: 코드 keyword shortlist 저장 · 2초: ≤10 1줄 카드 + 스키마 가드 |
| 14 | YT/IG = 같은 캐릭터 패키지 · **채널별 분리 LLM** · 플랜 자유 각색 (중복 콘텐츠 회피) |
| 15 | 채널 간 id 공유 **강제 없음** |
| 16 | **30장으로 진행**. 추가 30장은 이후 상황 보고 |
| 17 | brief/렌더에서 `metaphor_id` 경로 제거. DB 컬럼은 보존·무시 |
| 18 | arc는 프롬프트 권장 · **피크 위치만 코드 가드** |
| 19 | 모션 = 팝+페이드 · 일부 id 약한 바운스/쉐이크 (수치·목록은 결과 보고 수정) |
| 20 | 가드 실패 = **추가 LLM 없이 코드 다운그레이드** |
| 21 | 댓글·아웃트로 **시봄이 없음** |
| 22 | **AS**가 후보·플랜·가드 · catalog SSOT · ASM/WaggleBot은 합성·모션만 |

---

## 3. 텍스트 계층

| 레이어 | 역할 | TTS |
|---|---|---|
| 사연/대본 화면 텍스트 | 메인 서사 | ✅ |
| 시봄이 캔버스 안 캡션 | 보조 이해 (명사구·상황, 판정 금지) | ❌ |

캡션은 캐릭터 PNG **위**에 먼저 합성한 뒤 프레임에 배치한다 (`.temp/sprouts/README.md` §5).
캐릭터 PNG 캔버스는 항상 **1:1**로 유지한다. 캡션이나 사연 텍스트가 줄바꿈되면, 그 PNG를 담는 카드·블록은 직사각형이 될 수 있다.

---

## 4. 예산·역할

### 4.1 총량 (인트로 포함)

| 채널 | soft target |
|---|---|
| Reels (≤30s) | 총 4~5 |
| Shorts (≤45s) | 총 5~7 |

하한 강제 없음. 강한 매칭 부족 시 soft-fill → 그래도 모자라면 그 수 그대로.

### 4.2 role

| role | size | dwell | 비고 |
|---|---|---|---|
| `intro` | large | hold | 썸네일/첫 프레임. 1인·표정 권장 |
| `peak` | large | hold | 1장 필수 권장(`hook_emotion`). 2장은 결말/반전만 |
| `punch` | small | punch | 본문 감정 펀치 |
| `soft_fill` | small | punch | 범용 1인만. 피크/인트로 승격 금지 |

### 4.3 soft-fill 풀 (초기)

`drained`, `curled-up`, `stunned`, `swallow-words`, `indignant`, `side-glance`, `relieved`  
(카탈로그에 있는 id만. 상황 특정 컷 금지.)

---

## 5. LLM·토큰 계약

```
[1] 사연 생성 LLM (기존)
    → hook, hook_emotion, body…
    → 코드: 원문 × catalog keywords 스코어 → posts.sibom_candidates (≤12 id)
    ※ 시봄이 전용 LLM 호출 없음. 메타포 선택 지시 제거.

[2] 영상 직전 LLM — 채널별 분리 (기존 VideoVariant / 동등 경로)
    → script_reels|script_shorts + sibom_plan (해당 채널)
    → 컨텍스트: shortlist ≤10 1줄 카드 (id|arc|people|meaning|maxChars)
               + soft_fill 풀 고정 목록
               + 원문 요약/핵심 문단 + 훅/emotion
    → 금지: 30장 풀 catalog dump
```

### 5.1 `sibom_plan` 항목 (채널별)

```json
{
  "role": "intro|peak|punch|soft_fill",
  "image_id": "waiting-reply",
  "caption": "읽씹 3일차",
  "beat_index": 0,
  "size": "large|small",
  "dwell": "hold|punch"
}
```

- 고르기 근거 = 원문 의미 / keywords·trigger  
- `beat_index` = 해당 채널 대본 비트 인덱스  
- 캡션: catalog `caption`/`alt_captions` 재사용 또는 **최대 10자** 신규(`maxChars=10`). 판정·평가 금지

### 5.2 스키마 가드 (코드, 재시도 LLM 없음)

위반 시 다운그레이드:

1. 未知 `image_id` → 드롭  
2. 캡션 `maxChars` 초과 → sibling_bottom 교체 시도 → 캡션 비움  
3. 총 장수 > 상한 → punch/soft_fill부터 잘라냄  
4. soft_fill이 intro/peak → punch로 강등 또는 드롭  
5. 중복 id / 동일 `swap_group` → 후순위 드롭  
6. 피크 위치 가드: 1피크는 타임라인 과도한 초반 금지 · resolution성 2피크는 후반만  
7. 최종 0장이면 크림+텍스트만 (메타포 폴백 **금지**)

---

## 6. 레이아웃·모션 (렌더러)

| 모드 | 배치 | 모션 |
|---|---|---|
| large (intro/peak) | 상단 사연 텍스트 + 중하단 시봄이 크게 | 페이드 인, hold 동안 유지 |
| small (punch/soft_fill) | 사연 텍스트 主役 + 시봄이 ≈화면폭 35–45% 코너 스티커 | 팝(scale 92→100)+짧은 페이드 · 0.8–1.5s |
| 특수 | `indignant`, `stunned` 등 | 약한 바운스/쉐이크 (목록·강도는 결과 보고 조정) |

- 본문 시봄이 컷에서도 **사연 텍스트는 유지** (메인).  
- 댓글 씬·아웃트로: 시봄이 없음.  
- 기본 좌표 참고: catalog 합성 후 프레임 `(90, 550)` @ 1:1 (large). small은 스케일·앵커만 변경.
- 카드·텍스트 블록의 배경·테두리·타이포 크롬은 **Tone L**을 유지한다. 줄바꿈으로 블록이 직사각형이 되어도 캐릭터 PNG 자체는 1:1이다.

---

## 7. 파이프라인 소유권

| 단계 | 소유 |
|---|---|
| catalog.json + PNG 30장 SSOT | 레포 자산 (design specs / sprouts 패키지 → WaggleBot `assets/sprouts/` 배포) |
| keyword shortlist | **AS** (사연 저장 시) |
| `script_*` + `sibom_plan` + 가드 | **AS** (영상 잡 생성 직전, 채널별) |
| brief 전달 | AS → ASM → WaggleBot |
| 캡션 PIL 합성 · 프레임 배치 · 모션 | **WaggleBot** (ASM은 패스스루) |
| 메타포 PNG/`metaphor_id` intro | **언플러그** |

---

## 8. brief 필드 변경

| 필드 | 상태 |
|---|---|
| `metaphor_id` / `metaphor_ids` | 영상 경로 **무시**. 신규 선택·주입 중지. DB 컬럼 유지 |
| `sibom_candidates` | string[] ≤12 (사연 생성 후 코드) |
| `sibom_plan` | 객체 배열 (채널별 brief / job options) |

인트로 썸네일: `sibom_plan` 중 `role=intro` 합성 PNG. 없으면 크림+훅.

---

## 9. 구현 기본값 (그릴링에서 열어둔 디테일)

결과 보고 수정 전제:

- punch duration: **1.2s** 기본  
- small scale: **0.40** of 820px on 1080 canvas  
- bounce ids 초기: `indignant`, `stunned`, `burst-crying`, `two-argue`  
- soft-fill 풀: §4.3  
- catalog 정식 경로: design specs SSOT + WaggleBot `assets/sprouts/` 미러

---

## 10. 비범위 / 명시적 금지

- 메타포 60종을 intro·본문·썸네일에 사용  
- 시봄이 전용 LLM 3rd 콜 · catalog 전체 dump  
- 틀린 상황 컷으로 예산 채우기  
- 댓글/아웃트로 시봄이  
- 캡션에 판정·승패·처방 표현  
- 1인 장면에 진영색 칠하기  

---

## 11. Doc-Sync

- 본 문서 = 영상 삽입 계약 SSOT  
- `youtube-shorts-strategy.md`: 메타포 intro → 시봄이 intro/`sibom_plan`으로 갱신  
- `sprout-character-system/README.md` §6.2: 본 스펙이 런타임 이관을 **재정의** (30장 즉시, AS 플랜 소유)  
- `platforms.md` / `api.md`: brief 필드 반영  

**마지막 합의**: 2026-08-12 그릴링
