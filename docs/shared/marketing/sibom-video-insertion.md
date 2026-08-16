# 시봄이 숏폼 삽입 스펙 — Shorts / Reels

> **권위본**: 이 문서 (2026-08-12 그릴링 합의 · **2026-08-16 본문 레이아웃 SSOT**, 검증 job `#669`).  
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
| 23 | 발행 최소선 = 4장(전 채널 공통). 4장 미만은 재생성 1회 후 사망. 4장 이상이면 soft target 미달이어도 발행 (2026-08-15 그릴링) |
| 24 | soft-fill 자동 보충을 코드로 구현. LLM 추가 호출 없음(결정 #20 준수). 풀 고갈 시 채울 수 있는 만큼만 (2026-08-15 그릴링) |
| 25 | **본문 레이아웃 SSOT (2026-08-16, job 669)**: 사연 줄 = 마침표·절(`는데`/`지만`)만. 시봄이 없는 줄만 화면당 최대 3. 시봄이 비트 = **그 한 절 + 캐릭터 `image_text` 카드**(TTS 유지). 20자 wrap·조사 절단·무음 `image_only`·3줄 우하단 스티커 금지 |

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

**발행 최소선 = 4장(전 채널 공통). 4장 미만은 재생성 1회 후 사망.**
4장 이상이면 soft target 미달이어도 발행한다.
WaggleBot `min_sibom` 하드 게이트는 이 최소선(4)과 같아야 한다. Shorts soft target 5~7을 하드 실패로 쓰면
4장 플랜이 `SIBOM_SCENES_TOO_SHORT` → `RENDER_UNKNOWN`으로 죽는다 (job 665, 2026-08-16).
강한 매칭 부족 시 soft-fill 자동 보충 → 그래도 4장 미만이면 재생성 (추가 LLM 호출 없음).

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
- `beat_index` = Waggle이 사연을 문장/절 줄로 나눈 **뒤**(pack 전)의 본문 줄 인덱스. 3줄로 묶인 `text_only` 화면 인덱스가 아니다. intro는 본문 인덱스와 별개(`role=intro`).  
- 캡션: catalog `caption`/`alt_captions` 재사용 또는 **최대 10자** 신규(`maxChars=10`). 판정·평가 금지. **사연 문장을 캡션에 넣지 않는다.**

### 5.2 스키마·품질 가드 (코드, LLM 보정 1회)

개별 항목 위반은 다음처럼 정리한다.

1. 未知 `image_id` → 드롭  
2. 캡션 `maxChars` 초과 → sibling_bottom 교체 시도 → 캡션 비움  
3. 총 장수 > 상한 → punch/soft_fill부터 잘라냄  
4. soft_fill이 intro/peak → punch로 강등 또는 드롭  
5. 중복 id / 동일 `swap_group` → 후순위 드롭  
6. 피크 위치 가드: 1피크는 타임라인 과도한 초반 금지 · resolution성 2피크는 후반만  
7. 최종 플랜이 비었거나 최소 장수(4장)에 못 미치면 **렌더를 시작하지 않고 FAILED**로 끝낸다. 
   soft-fill 자동 보충이 먼저 시도되고, 그래도 4장 미만이면 폴백 없이 실패한다.
   크림+텍스트 전용 폴백과 메타포 폴백은 모두 금지한다.

AS는 `SIBOM_PLAN_EMPTY`·`SIBOM_PLAN_TOO_SHORT`·`SIBOM_SCENES_TOO_SHORT`를 품질 실패로 기록한다. 관리자는 실패 잡에서 재생성을 요청할 수 있고, 새 자식 잡은 원 잡을 덮어쓰지 않으며 성공 시 즉시 자동 게시한다.

AS는 채널별 초기 생성 결과가 비었거나 최소 장수(4장)에 못 미친 경우에만, 부족한 플랜을 명시한 **LLM 보정 호출 1회**를 허용한다. 
보정 뒤에도 가드를 통과하지 못하면 폴백 없이 실패하며 각 시도의 안전한 요약 진단만 남긴다.

대본 본문은 Reels 목표 30초(하드 상한 32초), Shorts 목표 45초(하드 상한 47초)다. WaggleBot은 문장 경계에서 한 번만 축약·재TTS한 뒤에도 **본문 TTS**가 상한을 넘으면 `DURATION_TTS_EXCEEDED`로 실패시킨다. 좋아요 순 댓글 2개와 아웃트로는 본문 이후에 붙고 본문 길이 판정 및 최종 MP4 하드 상한에는 포함하지 않는다.

---

## 6. 레이아웃·모션 (렌더러) — 본문 SSOT

> 런타임: WaggleBot `again_spring_text.split_story_lines` → 줄당 씬 → `apply_sibom_plan_to_body`(`image_text`) → `pack_undecorated_story_screens`.  
> 검증: job **#669** (2026-08-16). 이 절과 코드가 어긋나면 **코드를 문서에 맞춘다.**

본문은 두 종류의 화면만 쓴다.

| 화면 | 언제 | 보이는 것 | TTS |
|---|---|---|---|
| `text_only` | 시봄이가 **없는** 연속 절 | 문장/절 블록 **최대 3개**, 좌측 정렬 Tone L | 줄마다 낭독 |
| `image_text` | `sibom_plan` 본문 비트 (peak/punch/soft_fill) | **그 비트의 절 하나** + 시봄이 카드(PNG 상황 캡션) | 그 절 낭독 |

인트로(`role=intro`)는 훅 카드 + large 시봄이. 댓글·아웃트로에는 시봄이 없음.

### 6.1 사연 줄을 나누는 규칙

Waggle `split_story_lines`만 사용한다.

1. 공백 정규화 후 **문장 끝** `[.!?…]` + 공백에서 자른다.  
2. 한 문장 안에서는 절 끝만 자른다: `는데` / `지만` / `은데` / `ㄴ데` (쉼표 포함 `는데,` 우선).  
3. **하지 않는다**: `smart_split_korean(..., max_chars=20)` 또는 22자 창, `가`/`를`/`을` 조사로 명사구를 쪼개기, 시봄이 캡션 칸에 사연을 20자로 욱여넣기.

예 (job 668 대본 기준 올바른 줄):

```
아이를 낳자는 얘기를 꺼냈는데
조건이 나왔어요.
지금까지 생활비를 반반씩 내고 있었는데,
임신하는 동안 생활비를 제가 전부 내야 한다는 거였죠.
```

잘못된 줄 (금지): `지금까지 생활비를` / `생활비를 제가` / `함께` 다음 줄에 `낳는 건데`.

화면 표시에서 폰트가 길면 **렌더러가 시각적으로만 wrap**할 수 있다. 그건 새 비트·새 TTS 청크가 아니다.

### 6.2 시봄이가 없는 화면 (`text_only`)

`pack_undecorated_story_screens`: 시봄이 역할이 **없는** 인접 줄만 모아 화면당 **최대 3블록**. 4번째부터 다음 `text_only`.  
시봄이 `image_text` 앞뒤에서 버퍼를 비운다. 캐릭터 카드를 3줄 스택 안에 넣지 않는다.

### 6.3 시봄이 본문 비트 (`image_text`)

`apply_sibom_plan_to_body`가 해당 줄 씬을 `image_text`로 바꾸고, 사연 `text_lines`/`pre_split_lines`는 **그대로 둔다.**

- 한 화면에 사연 **한 절** + 캐릭터(캡션은 PNG에 이미 합성).  
- TTS는 그 절이다. 무음 컷이 아니다.  
- `size=large`(intro/peak): Tone L 카드 미디어 슬롯에 크게, hold·숨쉬기.  
- `size=small`(punch/soft_fill): **그 카드 슬롯 안에서** 작게. 3줄 `text_only`의 우하단에 스티커로 상주시키지 않는다.  
- 모션: 팝(약 1.2s, scale 92→100) + punch는 유지, hold는 숨쉬기 루프. shake id는 §9.

`beat_index`는 pack **전** 줄 인덱스에 붙인다. 화면을 3줄로 묶은 뒤에 붙이면 캐릭터가 3문장 내내 남거나 비트가 스킵된다 (`no free body scene`).

### 6.4 좌표·크롬

- 인트로 large 참고: 합성 후 `(90, 550)` @ 1:1, scale 1.0.  
- small 기본 스케일 0.40 of 820px (1080 캔버스) — **본문에서는 카드 슬롯 기준**.  
- Tone L 카드·불릿·타이포는 [`youtube-shorts-strategy.md`](youtube-shorts-strategy.md) §4. PNG 자체는 1:1.

### 6.5 명시적 금지 (2026-08-16에 품질을 떨어뜨린 패턴)

| 금지 | 증상 |
|---|---|
| 사연을 20/22자로 재분할 | 문장 중간에서 줄바꿈 |
| 조사 `가/를/을`로 분할 | `생활비를` / `제가` 같은 조각 |
| 본문 시봄이를 무음 `image_only`로 삽입 | 그 구간 글·TTS 없음 |
| 3줄 `text_only` 전체에 small 우하단 오버레이 | 캐릭터가 어색하게 계속 붙어 있음 |
| pack 후 화면에 `beat_index` 매핑 | 1절+캐릭터 씬 소멸, 비트 스킵 |

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
- 사연 20/22자 wrap · 조사 절단 · 본문 시봄이 무음 `image_only` · 3줄 화면 우하단 스티커 상주 (§6.5)  

---

## 11. Doc-Sync

- 본 문서 §6 = **본문 레이아웃 SSOT** (job 669). Waggle `again_spring_text` · `apply_sibom_plan_to_body` · `pack_undecorated_story_screens`  
- `youtube-shorts-strategy.md` §4.2·§7.1: 동일 계약  
- `sprout-character-system/README.md` §6.2: 본 스펙이 런타임 이관을 **재정의** (30장 즉시, AS 플랜 소유)  
- `platforms.md` / `api.md`: brief 필드 반영  

**마지막 합의**: 2026-08-16 본문 레이아웃 복구 (문장/절 줄 · 1절+시봄이 카드 · 시봄이 없는 줄만 3블록)
