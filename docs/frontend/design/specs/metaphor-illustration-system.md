# 다시봄 메타포 일러스트 시스템

> ⚠️ **영상 경로 폐기 안내 (2026-08-12)**  
> YouTube Shorts / Instagram Reels의 인트로·본문·썸네일은 더 이상 메타포 일러스트를 쓰지 않는다.  
> **시봄이** 캐릭터 삽입이 SSOT다 → [`docs/shared/marketing/sibom-video-insertion.md`](../../../shared/marketing/70-policy/sibom-video-insertion.md).  
> 메타포 자산(SVG/PNG·`metaphor_id`)은 **영상 파이프라인에 넣지 말 것** (파일·카탈로그는 보관용으로만 유지).  
> 본 문서는 메타포 디자인/자산 스펙으로 보존한다. 삭제하지 않는다.

> Claude Design에 새 일러스트를 요청할 때 이 파일 전체를 컨텍스트로 붙여넣으세요.

---

## 1. 시스템 개요

다시봄의 메타포 일러스트는 **갈등 상황의 감정을 사물로 상징**합니다.
사람을 직접 그리지 않고, 우체통·문·의자 같은 사물에 감정을 담아 비유합니다.
따뜻하고 차분하며, 무겁지 않습니다.

---

## 2. SVG 규격 (절대 준수)

```
viewBox="0 0 240 240"
fill="none"
xmlns="http://www.w3.org/2000/svg"
```

| 속성 | 값 |
|---|---|
| stroke-width | 1.5 또는 2 (굵기 통일) |
| strokeLinecap | round |
| strokeLinejoin | round |
| 전체 크기 | 240×240 |
| 사용 색 수 | 일러스트당 최대 3색 |

---

## 3. 팔레트 (6색 — 일러스트당 최대 3색 조합)

| 역할 | 값 | 사용 조건 |
|---|---|---|
| 크림 웜 (배경 fill) | `#FFF8F0` | 항상 포함 |
| 웜 브라운 (주선·획) | `#A08670` | 항상 포함 |
| 딥 브라운 (강조선) | `#5C4030` | 구조감 필요할 때 |
| 살몬 핑크 (감정 포인트) | `#F4A896` | 감정·따뜻함 강조 |
| 세이지 그린 (자연·희망) | `#A8C8B4` | 자연물·회복 |
| 연크림 (섬세한 fill 변형) | `#FBF3EC` | 안쪽 fill 구분 필요 시 |

> **주의**: 위 살몬(`#F4A896`)·세이지(`#A8C8B4`) 색은 **일러스트 전용 액센트**입니다. 진영색(작성자 피치 `#C9785A` / 상대방 세이지 `#5F8F76`)과 다른 색이며, 혼동하지 마세요. ([system.md §3.4](../system.md))

**조합 예시**:
- tension/heavy: `#FFF8F0` + `#A08670` + `#5C4030`
- loneliness/warm: `#FFF8F0` + `#A08670` + `#F4A896`
- recovery: `#FFF8F0` + `#A08670` + `#A8C8B4`

---

## 4. 디자인 원칙 (절대 금지 포함)

**해야 할 것**
- 사물 하나가 감정 하나를 상징 (단순명료)
- 선 10개 내외 (최소주의)
- 중심 오브젝트가 뷰박스의 40–70% 차지
- 여백을 활용해 고요하고 정적인 느낌

**절대 금지**
- emoji 및 장식 글리프
- 그라데이션 (linear-gradient, radial-gradient)
- 글자·숫자 텍스트
- 사람 얼굴·표정 (실루엣 몸체 최소한은 가능)
- 4색 이상 조합
- 복잡한 무늬·배경 패턴
- 그림자 효과 (box-shadow, filter: drop-shadow)

---

## 5. 현재 일러스트 목록 (60개, 2026-05-27 완성)

### 보편 (01–12)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| locked-mailbox | 01-locked-mailbox.svg | avoidance | neutral | 마음을 받았는데 열어보지 않은 채 쌓여있는 상태 |
| boiling-kettle | 02-boiling-kettle.svg | tension | heavy | 작은 일에도 곧 터질 것 같이 끓고 있는 상태 |
| locked-door | 03-locked-door.svg | avoidance | heavy | 더 이상 들어올 수 없게 마음의 빗장을 채운 상태 |
| too-big-umbrella | 04-too-big-umbrella.svg | protection | neutral | 상대를 지키려다 오히려 거리감을 만든 상태 |
| person-in-rain | 05-person-in-rain.svg | loneliness | heavy | 누군가 알아봐주길 기다리며 그대로 서있는 상태 |
| frozen-pond | 06-frozen-pond.svg | avoidance | heavy | 흐르지 못하고 멈춰버린 감정 |
| cracked-window | 07-cracked-window.svg | tension | heavy | 깨지지는 않았지만 작은 충격에도 흔들리는 상태 |
| empty-chair | 08-empty-chair.svg | avoidance | heavy | 함께 있어도 마음은 없는 자리 |
| overflowing-cup | 09-overflowing-cup.svg | tension | neutral | 더 이상 받아들일 수 없을 만큼 가득 찬 상태 |
| rope-bridge | 10-rope-bridge.svg | hesitation | neutral | 건너고 싶지만 무서워서 머뭇거리는 관계 |
| half-open-letter | 11-half-open-letter.svg | hesitation | warm | 말하고 싶은데 끝까지 못 한 마음 |
| two-trees-roots | 12-two-trees-roots.svg | recovery | warm | 떨어져 보여도 깊은 곳은 연결되어 있음 |

### 연인·부부 (13–24)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| two-compasses-apart | 13-two-compasses-apart.svg | avoidance | neutral | 원하는 미래의 방향이 달라진 상태 |
| melting-candle | 14-melting-candle.svg | tension | heavy | 열정·온기가 소진되어가는 상태 |
| parallel-rails | 15-parallel-rails.svg | avoidance | neutral | 같은 방향이지만 영원히 만나지 않는 두 레일 |
| half-erased-note | 16-half-erased-note.svg | hesitation | neutral | 썼다가 지운 흔적이 남은 메모 — 말하다 멈춘 마음 |
| tangled-thread | 17-tangled-thread.svg | tension | neutral | 복잡하게 얽힌 관계 |
| dying-stove | 18-dying-stove.svg | avoidance | heavy | 오래된 관계의 온기가 식어가는 상태 |
| one-candle-out | 19-one-candle-out.svg | loneliness | heavy | 불균등한 노력 |
| empty-photo-frame | 20-empty-photo-frame.svg | loneliness | heavy | 함께였던 순간이 지워진 자리 |
| pendulum | 21-pendulum.svg | tension | neutral | 감정의 극과 극 |
| back-to-back-umbrellas | 22-back-to-back-umbrellas.svg | avoidance | neutral | 서로 등을 지고 각자 우산을 쓴 상태 |
| crumbling-sandcastle | 23-crumbling-sandcastle.svg | tension | heavy | 함께 쌓아온 것이 흔들리는 상태 |
| empty-nest | 24-empty-nest.svg | loneliness | warm | 역할이 끝난 후의 공허함 |

### 친구 (25–32)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| dried-bouquet | 25-dried-bouquet.svg | avoidance | neutral | 식어버린 우정 |
| emptying-hourglass | 26-emptying-hourglass.svg | avoidance | heavy | 서서히 멀어지는 관계 |
| one-lit-bulb | 27-one-lit-bulb.svg | loneliness | heavy | 일방적인 노력 |
| broken-thread | 28-broken-thread.svg | avoidance | heavy | 단절된 연결 |
| wrongly-folded-letter | 29-wrongly-folded-letter.svg | hesitation | neutral | 오해·전달 실패 |
| string-telephone | 30-string-telephone.svg | hesitation | warm | 거리가 있어도 닿으려는 시도 |
| inside-out-umbrella | 31-inside-out-umbrella.svg | tension | heavy | 믿었던 것이 배신당한 상태 |
| one-seedling | 32-one-seedling.svg | loneliness | neutral | 불균형한 투자 |

### 직장 (33–39)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| tilted-scale | 33-tilted-scale.svg | tension | heavy | 인정·보상의 불균형 |
| overflowing-papers | 34-overflowing-papers.svg | tension | heavy | 과도한 요구·소진 |
| empty-trophy | 35-empty-trophy.svg | loneliness | neutral | 인정받지 못한 노력 |
| light-under-door | 36-light-under-door.svg | loneliness | heavy | 배제·소외 |
| chained-anchor | 37-chained-anchor.svg | avoidance | heavy | 벗어나고 싶지만 묶인 상태 |
| too-many-keys | 38-too-many-keys.svg | tension | neutral | 과중한 책임·역할 |
| gears-not-meshing | 39-gears-not-meshing.svg | tension | neutral | 팀워크·협력 단절 |

### 가족 (40–47)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| small-birdcage | 40-small-birdcage.svg | tension | heavy | 통제·과잉보호로 숨막히는 상태 |
| tall-fence | 41-tall-fence.svg | protection | heavy | 보호가 감금으로 변한 상태 |
| trees-growing-apart | 42-trees-growing-apart.svg | avoidance | neutral | 같은 뿌리에서 서로 다른 방향으로 자란 |
| cracked-bowl | 43-cracked-bowl.svg | tension | heavy | 상처난 관계 |
| empty-dining-table | 44-empty-dining-table.svg | loneliness | heavy | 함께하지 못하는 시간 |
| wilting-plant | 45-wilting-plant.svg | avoidance | neutral | 방치된 관계 |
| closed-diary | 46-closed-diary.svg | hesitation | neutral | 세대 간 말하지 못하는 속마음 |
| long-shadow | 47-long-shadow.svg | protection | heavy | 부모의 영향력 아래 |

### 지인 (48–52)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| foggy-path | 48-foggy-path.svg | hesitation | neutral | 관계의 모호함 |
| half-open-window | 49-half-open-window.svg | hesitation | warm | 조심스럽게 열어두는 마음 |
| oil-on-water | 50-oil-on-water.svg | avoidance | neutral | 섞이지 않는 관계 |
| crossing-paths | 51-crossing-paths.svg | hesitation | neutral | 스쳐 지나가는 관계 |
| shallow-well | 52-shallow-well.svg | loneliness | neutral | 깊어지지 않는 관계 |

### 회복·전환 (53–60)

| ID | 파일명 | 그룹 | 톤 | 의미 |
|---|---|---|---|---|
| first-footstep | 53-first-footstep.svg | recovery | warm | 용기 내어 내딛은 시작 |
| seed-in-palm | 54-seed-in-palm.svg | recovery | warm | 새 관계의 가능성 |
| open-window | 55-open-window.svg | recovery | warm | 마음을 열어두는 상태 |
| cups-finally-touching | 56-cups-finally-touching.svg | recovery | warm | 화해의 첫 접촉 |
| melting-ice | 57-melting-ice.svg | recovery | neutral | 차가움이 녹는 과정 |
| crack-with-light | 58-crack-with-light.svg | recovery | warm | 상처가 빛의 통로 |
| two-compasses-aligned | 59-two-compasses-aligned.svg | recovery | warm | 같은 미래를 바라보는 마음 |
| raft-together | 60-raft-together.svg | recovery | neutral | 어려움 속 동행 |

---

## 6. 그룹·톤 분포 (60종)

| 그룹 | 수 | | 톤 | 수 |
|---|---|---|---|---|
| avoidance | 13 | | heavy | 23 |
| tension | 13 | | neutral | 23 |
| loneliness | 9 | | warm | 14 |
| hesitation | 8 | | | |
| recovery | 9 | | | |
| protection | 3 | | | |
| 합 | 60 | | 합 | 60 |

---

## 7. 추가 일러스트 요청 방법 (Claude Design)

기존 60종으로 충분하지 않은 경우 아래 3-Block 형식으로 Claude Design에 요청하세요.

### Block 1 — 공통 컨텍스트 (매번 필수)

```
다시봄 앱의 SVG 메타포 일러스트를 만들어줘.

기술 규격:
- viewBox="0 0 240 240", fill="none"
- stroke-width 1.5 또는 2, strokeLinecap="round", strokeLinejoin="round"
- 한 일러스트에서 최대 3색:
    #FFF8F0 (크림 웜, 배경 fill)
    #A08670 (웜 브라운, 주선)
    + 아래 중 1색:
       #5C4030 (딥 브라운, 구조감)
       #F4A896 (살몬 핑크, 감정·따뜻함)
       #A8C8B4 (세이지 그린, 자연·희망)

디자인 원칙:
- 사물 하나로 감정 하나를 상징
- 선 10개 내외 (최소주의)
- 중심 오브젝트가 뷰박스의 40–70% 차지
- 그라데이션, 글자, emoji, 표정, 4색 이상 — 절대 금지
- 분위기: 따뜻하고 차분하며, 무겁지 않게
```

### Block 2 — 스타일 레퍼런스

```
기존 일러스트 코드를 참고해서 같은 스타일로 만들어줘:
[유사한 톤의 기존 SVG 코드 1개 붙여넣기]
```

### Block 3 — 이번 요청 명세

```
새로 만들어줄 일러스트:
- 파일명: 61-xxx.svg
- 주제: ...
- group: recovery / avoidance / tension / hesitation / loneliness / protection
- 표현하고 싶은 감정: ...
- 관련 욕구: ...
- 톤: warm / neutral / heavy
- 색: #FFF8F0 + #A08670 + [포인트 1색]
- 특이사항: ...
```

---

## 8. 등록 체크리스트 (신규 SVG 수령 후)

- [ ] viewBox="0 0 240 240" 확인
- [ ] 색이 3색 이하이며 팔레트 값 정확한지 확인
- [ ] 그라데이션/텍스트/emoji 없는지 확인
- [ ] 중심 오브젝트가 너무 작지 않은지 (40% 이상)
- [ ] `frontend/public/illustrations/metaphors/` 에 저장
- [ ] `frontend/lib/constants/metaphors.ts` METAPHORS 배열에 등록 (모든 필드 필수)
- [ ] `ai-user/llm/src/main/resources/metaphors/catalog.json` (+ `catalog-compact.txt`) 동기화 — PLAN이 `metaphor_id`를 고를 때 사용
- [ ] 이 문서 §5 목록 테이블 업데이트
- [ ] `npm run build` 통과
- [ ] `npm run lint:emoji` 통과

### 런타임 매칭

AI 사연 생성(PLAN / PAIRED_PHASE1) 시 LLM이 `METAPHOR_CATALOG`에서 감정에 맞는 `metaphor_id` 1개를 고르고, `posts.metaphor_id`(V99)에 저장한다. Shorts intro는 이 ID의 PNG(`WaggleBot/assets/metaphors/{id}.png`)를 표지로 쓴다.
