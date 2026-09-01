# 시봄이(Sibom) — 영상 제작용 캐릭터 패키지

> **원본**: gitignore 대상이던 로컬 미러(`.temp/sprouts/`) 안의 문서를 git 추적 경로인 여기로 승격했다(2026-09-02, 원본 삭제됨).
> **⚠️ 권위본 안내**: 이 문서는 시봄이를 실제 영상에 합성하는 방법(카탈로그 스키마·슬롯·2단계 합성·이미지 선택 알고리즘)을 설명하는 참고 문서다. **장수·완성도·배포 상태의 최신 권위본은 같은 폴더 [`README.md`](./README.md)** 다 — 이 문서는 1차 배치(30장) 시절에 작성됐고, 아래 §2 폴더 구성과 §7 표는 그 시점(로컬 미러가 30장만 담고 있던 상태) 그대로 보존한 것이다. 실제로는 **60장 전체가 완성·배포됐다**(2026-08-21, `README.md` §10). 최신 60장 전체 카탈로그는 이 폴더의 `catalog.json`을 따른다.

이 폴더 하나만으로 "다시봄" 캐릭터 시봄이를 영상에 배치할 수 있다. 이 패키지를 읽는 모델은
이 프로젝트의 다른 어떤 컨텍스트도 갖고 있지 않다고 가정하고 작성했다 — 여기 없는 정보는 필요 없다.

---

## 1. 이게 뭔가

**다시봄(Again Spring)**은 갈등 사연 커뮤니티다. 사용자가 갈등 사연을 올리면 커뮤니티가 공감 투표를 하고,
이 사연을 짧은 세로형 영상(Shorts)으로도 만든다. **시봄이**는 그 영상에 들어가는 자체 캐릭터 —
동그란 몸통 + 머리 위 떡잎 2장을 가진 새싹으로, "서툴러서 갈등하고, 그래서 다시 봄이 필요하다"는
컨셉이다. 카카오 이모티콘처럼 단일 캐릭터가 과장된 리액션을 짓고, 그 아래(또는 옆) 짧은 한글 문구가
붙는 형식이다.

이 패키지는 **사연 본문의 한 문단**을 받아서 → **어떤 시봄이 그림 + 어떤 짧은 캡션**을 그 문단 옆에
넣을지 결정하고 → **실제 영상 프레임에 합성**하는 데 필요한 모든 것을 담고 있다.

이 문서 작성 시점 기준 30장(1차 배치)이 완성돼 있었다 — **현재는 60장 전체가 완성·배포됐다**
(최신 상태는 같은 폴더 `README.md` 참고). 표정·상황이 겹치지 않게 설계했으므로, 사연 어디에
어떤 감정이 나오든 대부분 매칭된다(아래 예시는 1차 배치 30장 기준 서술이며, 60장 기준 코퍼스
매칭률은 `README.md` §10을 따른다).

---

## 2. 폴더 구성

```
.temp/sprouts/                          # 로컬 전용 미러 (gitignore) — 문서는 여기서 승격돼 빠졌다
├── catalog.json                        # 30장 메타데이터 — 매칭 로직의 핵심 (§4)
├── svg/{id}.svg                        # 원본 벡터 30장 (820×820, 배경 투명, 자체완결형)
├── png/{id}.png                        # 렌더된 래스터 30장 (820×820, RGBA 투명) — 영상 합성엔 보통 이걸 씀
└── example/
    ├── step1-caption-on-character.png  # 1단계 예시: 캐릭터 PNG 위에 캡션을 직접 합성한 결과
    └── step2-composited-video-frame.png # 2단계 예시: 그 결과를 1080×1920 영상 프레임에 배치한 최종 결과
```

`svg/`와 `png/`는 **같은 30장**이다. SVG는 확대·재렌더가 필요할 때, PNG는 바로 합성할 때 쓴다.
파일명(`{id}`)은 `catalog.json`의 `id` 필드와 정확히 일치한다 (예: `waiting-reply.svg` ↔ `waiting-reply.png` ↔ `{"id": "waiting-reply", ...}`).

---

## 3. 캐릭터 사양

| 항목 | 값 |
|---|---|
| 이름 | 시봄이 (Sibom) |
| 캔버스 | 820×820px, 배경 투명 |
| 외곽선 색 | `#5C4030` (딥브라운) |
| 몸통 — 1인 장면(중립) | `#FFF8F0` (크림) |
| 몸통 — 2인 장면, 사연 작성자 쪽 | `#C9785A` (피치) |
| 몸통 — 2인 장면, 상대방 쪽 | `#5F8F76` (세이지) |
| 떡잎(머리 위 잎) | `#A8C8B4` |
| 볼터치 | `#F4A896` |

**규칙**: 1인 장면(대부분)은 절대 진영색(피치/세이지)을 쓰지 않는다 — 화자가 작성자인지 상대방인지 이미지가
미리 판단해버리면 안 되기 때문이다. 2인 장면(둘이 같이 나오는 장면)만 진영색을 쓴다.

---

## 4. `catalog.json` 구조 — 이걸로 이미지를 고른다

각 이미지 항목의 필드:

```jsonc
{
  "id": "waiting-reply",              // 파일명과 매칭되는 고유 ID
  "slot": "bottom",                    // 캡션이 들어가는 위치 프리셋 — bottom | top | bubble (§5)
  "maxChars": 16,                       // 이 슬롯에 들어갈 수 있는 최대 글자 수(한글 기준)
  "people": 1,                          // 등장인물 수: 1 | 2 | 3
  "arc": "reaction",                    // 서사 단계 힌트 — trigger(사건 발생) | reaction(감정 상태) | resolution(결말·전환)
  "categories": ["ANY"],                // 어떤 관계에 어울리는지 힌트(강제 아님) — COUPLE|MARRIED|FRIEND|FAMILY|WORK|OTHER|ANY
  "meaning": "읽씹·답장 없음",            // 사람이 읽는 한 줄 설명
  "trigger": "연락을 했는데 답이 없거나 읽고 씹혔다고 말하는 대목",
                                          // 이 문장을 그대로 "이 이미지를 언제 써야 하는가"의 판단 기준으로 써라
  "keywords": ["읽씹", "답장이 없다", "카톡을 읽고도", "연락이 끊겼다", "답이 없었다"],
                                          // 문단에 이 표현이 있으면 이 이미지가 강한 후보
  "caption": "읽씹 3일차",               // 검증된 샘플 캡션 (그대로 써도 됨)
  "alt_captions": ["답장이 없다", "읽고 씹혔다"],
                                          // 다른 표현이 필요할 때 참고할 대체 캡션 예시
  "swap_group": "no_response",          // 같은 의미 계열 그룹 이름
  "sibling_bottom": "waiting-reply"     // 캡션이 너무 길 때 대신 쓸 수 있는 bottom 슬롯 형제 이미지 id (없으면 null)
}
```

### 4.1 슬롯 프리셋 3종 (`catalog.json`의 `presets` 필드)

캡션은 **캐릭터 PNG/SVG 안의 지정된 사각형(rect) 안에** 직접 그려 넣는다 (영상 프레임이 아니라
캐릭터 이미지 자체 위에!). rect 좌표는 820×820 캔버스 기준이다.

| slot | rect (x, y, w, h) | maxChars | 특징 |
|---|---|---|---|
| `bottom` | 40, 596, 740, 200 | 16 (2줄×8자) | 기본값, 가장 여유 있음 |
| `top` | 40, 24, 740, 200 | 16 | 캐릭터가 아래를 보는 구도용 |
| `bubble` | 420, 40, 376, 300 | 12 (3줄×4자) | 캐릭터 옆에 그려진 말풍선 안 — 가장 빡빡함 |

캡션 폰트: **80px, bold, 색 `#5C4030`**, 중앙 정렬.

### 4.2 폴백 체인 — 캡션이 너무 길 때

1. `maxChars` 초과 → 같은 `swap_group`의 `sibling_bottom` 이미지로 교체 (그 이미지는 `bottom` 슬롯이라 여유가 더 많다)
2. `sibling_bottom`이 없거나(=null) 그것도 넘음 → 캡션 없이 캐릭터 이미지만 사용
3. 애초에 어울리는 이미지가 없음 → 이미지 없이 자막(텍스트)만 사용

**알려진 갭**: 아래 10개 이미지는 `sibling_bottom`이 없다(=폴백 1단계 불가, 바로 2단계로 감).
캡션을 짧게 못 줄이면 그 이미지는 캡션 없이 그림만 나간다.
`swallow-words · side-glance · indignant · nagging · cut-off · caught-lying · talked-behind-back · talked-over · pressured-decision · in-law-conflict`

---

## 5. 두 단계 합성 — 실제로 그림을 어떻게 넣는가

### 1단계 — 캡션을 캐릭터 이미지 위에 그린다

`png/{id}.png`를 열고, `catalog.json`에서 그 이미지의 `slot`에 해당하는 `rect` 안에
글자를 **가운데 정렬**로 그려 넣는다. 결과: 캡션이 이미 포함된 820×820 PNG.

### 2단계 — 그 결과를 영상 프레임에 배치한다

1단계 결과 이미지를 실제 영상 프레임(세로형, 보통 1080×1920) 위에 원하는 위치에 얹는다.
참고 구현(다시봄 자체 파이프라인, WaggleBot)에서는 `x=90, y=550`에 1:1 배율로 놓는다 —
하지만 이건 그 파이프라인 고유의 좌표고, **다른 영상 시스템이면 원하는 위치에 자유롭게 배치해도 된다.**
중요한 건 순서: 캡션은 반드시 1단계에서 캐릭터 캔버스 안에 먼저 그려 넣어야 하고,
영상 프레임에 얹은 뒤에 따로 자막을 겹쳐 쓰지 않는다(이미 캐릭터 그림 안에 있으므로).

### 예시 (Python + Pillow)

```python
import json
from PIL import Image, ImageDraw, ImageFont

cat = json.load(open("catalog.json", encoding="utf-8"))
presets = cat["presets"]
FONT = "아무 한글 지원 폰트 경로.ttf"  # 볼드체 권장

def add_caption(image_id, caption):
    meta = next(m for m in cat["images"] if m["id"] == image_id)
    img = Image.open(f"png/{image_id}.png").convert("RGBA")
    x, y, w, h = presets[meta["slot"]]["rect"]
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, presets[meta["slot"]]["font_size"])
    bbox = draw.textbbox((0, 0), caption, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((x + (w - tw) / 2, y + (h - th) / 2 - bbox[1]),
               caption, font=font, fill="#5C4030")
    return img  # 820x820 RGBA, 프레임에 얹을 준비 완료

captioned = add_caption("waiting-reply", "읽씹 3일차")
frame = Image.new("RGBA", (1080, 1920), (251, 243, 236, 255))
frame.alpha_composite(captioned, (90, 550))
frame.convert("RGB").save("out.png")
```

이 코드로 만든 실제 결과물이 `example/step1-caption-on-character.png`(1단계)와
`example/step2-composited-video-frame.png`(2단계, 최종)이다. 열어서 눈으로 확인하고 시작할 것.

---

## 6. 이미지 선택 알고리즘 (사연 문단 → 이미지+캡션)

사연은 여러 문단으로 나뉘고, **문단마다** 이미지 하나(있으면)를 고른다. 순서:

1. 문단 텍스트에서 각 이미지의 `keywords` 배열과 겹치는 표현이 있는지 확인 → 겹치는 이미지들을 후보로 모은다.
2. 후보가 여러 개면, 각 이미지의 `trigger` 문장과 문단의 실제 의미를 비교해 가장 가까운 것 하나를 고른다
   (LLM 판단 권장 — keyword는 후보를 좁히는 용도지 최종 결정 기준이 아님).
3. 후보가 하나도 없으면 그 문단엔 이미지를 붙이지 않는다(자막만).
4. 이미지를 골랐으면, 문단 의미를 그 이미지의 `maxChars` 안에 담는 캡션을 새로 쓴다.
   `alt_captions`를 few-shot 예시로 참고할 것 — 명사구·상황 서술 위주로, **평가나 판정 표현은 쓰지 않는다**
   (예: "그건 상대방 잘못" ❌, "읽씹 3일차" ✅). 사연은 누구 편도 들지 않는 커뮤니티 콘텐츠다.
5. 캡션이 `maxChars`를 넘으면 §4.2 폴백 체인을 그대로 따른다.
6. 영상 전체에서 같은 이미지를 반복해서 쓰지 않는 게 좋다(30장으로 다양성을 주는 게 목적).
7. `arc` 필드로 문단의 위치를 가늠할 수 있다 — `trigger`는 사건이 벌어지는 초중반, `reaction`은 그 여파를
   곱씹는 중반, `resolution`은 이야기가 정리되는 후반에 어울린다. 강제는 아니고 배치 감각을 잡는 용도.

---

## 7. 30장 빠른 참조

> ⚠️ 아래는 **1차 배치(30장) 기준**이다. 60장 전체 최신 카탈로그는 같은 폴더의 `catalog.json`
> (권위본은 `README.md`)을 따른다.

`slot` = bottom(여유) / top(여유) / bubble(빡빡). `people` = 등장인물 수. `arc` = 서사 단계.
전체 필드는 `catalog.json`을 봐라 — 이 표는 훑어보기용.

| id | slot | 인원 | 단계 | 의미 | 샘플 캡션(글자수/상한) |
|---|---|---|---|---|---|
| two-cold-backs | bottom | 2 | reaction | 말없이 등 돌린 냉전 | "말 안 한 지 3일" (10/16) |
| two-argue | top | 2 | trigger | 정면충돌·언성이 높아진 순간 | "결국 터졌다" (6/16) |
| two-hand-hesitate | bottom | 2 | resolution | 화해를 시도했지만 받아들여지지 않음 | "먼저 사과했는데" (8/16) |
| waiting-reply | bottom | 1 | reaction | 읽씹·답장 없음 | "읽씹 3일차" (6/16) |
| swallow-words | bubble | 1 | reaction | 하고 싶은 말을 삼킴 | "말을 삼켰다" (6/12) |
| stunned | bottom | 1 | trigger | 어이없어 말문이 막힘 | "말문이 막혔다" (7/16) |
| side-glance | top | 1 | reaction | 눈치를 보며 살핌 | "눈치만 봤다" (6/16) |
| drained | bottom | 1 | reaction | 지치고 소진됨 | "이제 지쳤다" (6/16) |
| indignant | bubble | 1 | reaction | 억울하고 분함 | "왜 나만" (4/12) |
| relieved | bottom | 1 | resolution | 한숨 돌린 안도 | "조금은 풀렸다" (7/16) |
| money-trouble | bottom | 1 | trigger | 돈·계산 문제로 상한 마음 | "돈 얘기가 남았다" (9/16) |
| left-out | bottom | 3 | trigger | 나만 빠진 자리·배제 | "나만 몰랐다" (6/16) |
| nagging | top | 2 | trigger | 일방적인 잔소리·훈계 | "또 시작이다" (6/16) |
| overloaded | bottom | 1 | reaction | 감당 못 할 만큼 떠안음 | "다 내 몫이었다" (8/16) |
| compared | bottom | 2 | trigger | 남과 비교당함 | "누구는 잘한다며" (8/16) |
| no-apology | bottom | 2 | resolution | 끝내 사과하지 않음 | "사과는 없었다" (7/16) |
| burst-crying | bubble | 1 | reaction | 참다가 결국 터진 울음 | "결국 울었다" (6/12) |
| curled-up | bottom | 1 | reaction | 혼자 곱씹으며 웅크림 | "밤새 곱씹었다" (7/16) |
| comforted | bottom | 2 | resolution | 곁에서 편들어주고 다독임 | "네 편이라고" (6/16) |
| cut-off | top | 1 | resolution | 마음을 정하고 거리를 둠 | "이제 그만하자" (7/16) |
| caught-lying | top | 2 | trigger | 거짓말·숨긴 일이 들통남 | "거짓말이 들켰다" (8/16) |
| talked-behind-back | top | 1 | trigger | 뒤에서 이야기됨을 알게 됨 | "뒷말을 들었다" (7/16) |
| stood-up | bottom | 1 | trigger | 약속 장소에서 혼자 기다림 | "혼자 기다렸다" (7/16) |
| talked-over | top | 2 | trigger | 말이 끊기거나 무시당함 | "말이 잘렸다" (6/16) |
| pressured-decision | bubble | 1 | trigger | 내 의사와 상관없이 결정됨 | "대신 정해버렸다" (8/12) |
| breakup | bottom | 1 | trigger | 이별을 통보받음 | "이별을 통보받았다" (9/16) |
| solo-parenting | bottom | 1 | reaction | 육아·집안일을 혼자 떠맡음 | "혼자 다 떠맡았다" (9/16) |
| in-law-conflict | top | 2 | trigger | 양가 사이에서 눈치를 봄 | "양가 눈치만 봤다" (9/16) |
| late-regret | bottom | 1 | resolution | 그때 일이 계속 떠오름 | "계속 생각났다" (7/16) |
| reconciled | bottom | 2 | resolution | 마침내 화해하고 웃음 | "마침내 웃었다" (7/16) |

---

## 8. 지켜야 할 것

- **PNG/SVG 원본에 글자를 직접 굽지 말 것** — 이미 굽혀 있으면 안 되고(실제로 안 굽혀 있음), 캡션은 항상 §5의
  런타임 합성으로 넣는다. 같은 이미지를 다른 캡션으로 재사용할 수 있어야 하기 때문.
- **1인 장면 캐릭터에 진영색(피치/세이지)을 칠하지 말 것** — 이미 중립색(크림)으로 그려져 있다. 색을 바꾸면
  안 됨.
- **캡션에 판정·평가 표현을 쓰지 말 것** — "그건 잘못했네", "나쁘다" 같은 문구 금지. 상황을 담백하게 서술하는
  명사구만 (§6.4 참고).
- 이 30장은 1차 배치다. 부족하면(예: 필요한 상황에 맞는 이미지가 없으면) 억지로 끼워 맞추지 말고 이미지 없이
  진행하는 게 낫다 — 틀린 이미지보다 이미지가 없는 게 낫다.
