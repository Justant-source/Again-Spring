# 시봄이(Sibom) 캐릭터 일러스트 시스템

**작성일**: 2026-08-12
**작성자**: Claude Code (Claude Design 세션 기반)
**상태**: **2/2 배치 완료(60/60장), 배포됨**(2026-08-21, §10). 매칭 실측 기반 키워드 전면 재설계 포함 — 코퍼스 커버리지 5%→99.2%. 경위: `.temp/sibom/emoticon/upgrade-plan.md`
**목적**: 컨텍스트가 완전히 초기화돼도 이 폴더 하나만으로 작업을 그대로 이어갈 수 있게 한다.

> ⚠️ 이 문서는 **`docs/frontend/design/specs/metaphor-illustration-system.md`(기존 60종 사물 메타포)를 대체하지 않는다.**
> 시봄이는 그 옆에 새로 추가되는 **영상 전용 캐릭터 자산**이다. 기존 사물 메타포는 그대로 두고, 실패하면 되돌아갈 자리로 남겨둔다.

---

## 0. 5분 요약 (컨텍스트 초기화 후 여기부터 읽기)

- **무엇을 만드는가**: 다시봄 Shorts 영상(WaggleBot 파이프라인)에 쓸 자체 캐릭터 "시봄이" 일러스트 60장. 카카오 이모티콘 문법(단일 캐릭터 + 과장된 리액션 + 짧은 상황 라벨)을 차용하되 그림은 100% 자체 제작.
- **왜 새로 만드는가**: 기존 사물 은유 60종(`metaphor-illustration-system.md`)은 prod 실사용률 17%(332건 중 57건)로 사실상 실패했다. 원인은 ① 은유가 너무 추상적이고 ② 카테고리(연인/친구/직장/가족)로 쪼개져 같은 감정을 5번씩 그려 커버리지가 얇았고 ③ AI-user가 사연 작성 시점에 메타포를 고르는데 대본 LLM은 그 선택을 참고하지 않아 미스매치가 났기 때문. 이 세 문제를 전부 설계로 풀었다(§2 결정 로그 참고).
- **지금 어디까지 됐는가**: 60/60장 완성. 그림체 리파인(§9) → 모션 배선(WaggleBot `layout.py`, 완료) → **2배치 31~60장 + 매칭 실측 재설계 완료·5곳 배포**(2026-08-21, §10). 코퍼스 실측 커버리지 5%→99.2%.
- **다음에 할 일**: 없음(60/60 완료). Claude Design 게시(§6)와 커밋만 남음 — 진행 상황은 `.temp/sibom/emoticon/upgrade-plan.md` 참고. §7 체크리스트는 1배치 시절 기준이라 낡았다.

---

## 1. 이 폴더의 파일

```
docs/frontend/design/specs/sprout-character-system/
├── README.md          # 이 문서
├── catalog.json        # 30장 메타데이터 SSOT (사연→영상 매칭용, §4 참고)
├── gen.py               # SVG 생성기 — 부품 조립식 (몸통·팔·눈·입·소품을 함수로 분리)
├── build_page.py        # Claude Design 리뷰 페이지(.dc.html) 빌더 — gen.py 산출물을 소비
└── svg/
    ├── {60개 씬 ID}.svg       # 60장 원본 (820×820, 배경 투명, 자체 완결형 — <use> 참조 없음)
    └── face-{표정라벨}.svg     # 표정 세트 20종 (리뷰용 별도 크롭, 씬에는 안 쓰임)
```

**중요**: `svg/*.svg`는 각 파일이 독립적으로 완결된 SVG다(패스 데이터를 전부 인라인으로 가짐).
Claude Design에 올린 리뷰 페이지(`.dc.html`)만 파일 크기 때문에 `<defs>/<use>`로 패스를 압축했을 뿐, **런타임에 PNG로 구울 원본은 이 `svg/` 폴더의 개별 파일들**이다.

---

## 2. 결정 로그 (사용자와 합의한 순서 그대로)

번호는 대화에서 실제로 오간 질문 순서. 나중에 사용자가 "왜 이렇게 했지?"라고 물으면 이 표를 먼저 본다.

| # | 결정 | 왜 |
|---|---|---|
| 1 | 소재 = 100% 자체 제작 일러스트. "짤"에서 가져오는 건 그림이 아니라 *포맷*(단독 캐릭터·과장 리액션·상황 라벨) | 인기 짤을 그대로 재해석하면 저작권·퍼블리시티권 문제. Claude Design은 사진도 못 만듦 |
| 2 | 캐릭터 = 새싹, 이름 **"시봄이"** (다시봄에서 파생) | 사용자 지정. (참고: Claude가 "떡잎이"를 추천했으나 채택되지 않음 — 혼동 방지용으로 기록) |
| 3 | 등장 구성 = 1인 다수 · 2인 소수(최종 30장 기준 1인 18·2인 11·3인 1) | 사연 문단 대부분은 화자 1인의 감정 서술. 관계 전환점·intro 표지만 2인 |
| 4 | 이미지 선택 주체 = **대본을 쓰는 LLM이 문단마다 직접 `{image_id, caption}` 결정**. 균등 분배(`distribute_images`) 폐기 | 기존 실패 원인 ③을 근본적으로 제거. 의미를 먼저 정하고 글자를 줄이는 게, 글자에 맞춰 그림을 고르는 것보다 항상 낫다 |
| 5 | 이미지 안 텍스트 = PNG에 글자를 굽지 않고 **런타임 PIL 합성**. 슬롯은 미리 그려둔 빈 그릇(말풍선 외곽선 등)만 | 60장 고정 자산 + 무한한 캡션 조합. 카카오 이모티콘 실제 문법과도 일치 |
| 6 | 슬롯 프리셋 3종 고정 좌표(`bottom`/`top`/`bubble`), 이미지당 프리셋 하나만 배정 | 이미지마다 좌표를 재는 대신 프리셋 3개만 유지 — 그림 수정해도 좌표 재계산 불필요 |
| 7 | 순서 = **이미지 먼저 고르고 캡션을 그 예산에 맞춰 작성**. 초과 시 폴백 체인(§4 `fallback_chain`) | 이미지는 60장 고정된 희소자원, 캡션은 무한히 탄력적 — 의미를 먼저 맞추는 쪽이 항상 이득 |
| 8 | AS(`posts.metaphor_id`)의 메타포 선택 경로는 **폐기**(컬럼은 보존, 하위호환). SSOT는 WaggleBot 쪽으로 이전 예정(§6) | 결정 4의 결과로 이 배관이 무의미해짐. DB 삭제는 안 함 — 기존 57건 데이터 보존 |
| 9 | 표정은 자유(눈썹 허용, 강도 제한 없음) — "사연은 작성자 시점 감정 기록이므로 누가 잘못했는지 드러나도 됨"(사용자 명시) | 초기엔 "판정 금지 눈썹 금지"로 시작했으나 사용자가 명확히 뒤집음. **재적용 금지** |
| 10 | 선화 규격 = 이모티콘형: 외곽선 stroke **9·28**(리파인 전 7·22), 5색 이내, 그라데이션·그림자 금지, 820×820 배경 투명 | 기존 사물 메타포(stroke 1.5–2)는 축소 시 형태가 사라짐. 7·22도 쇼츠 `small`(40%)에서 뭉개져 2026-08-20 상향 |
| 11 | **판정 기준 = 쇼츠 축소 렌더**(165px 상당). 원본 820px에서 괜찮아 보여도 축소하면 표정이 소멸한다 | 시봄이의 실사용처는 쇼츠/릴스이고 `small` 역할은 40%로 축소된다 |

---

## 3. 캐릭터 바이블

### 3.1 정체성
- **이름**: 시봄이 (한글 고정, 영문 슬러그 `sibom`)
- **컨셉**: "서툰 존재" — 아직 다 자라지 못했다. 관계에 서툴러서 상처받고 상처 주기도 하고, 매번 후회한다. 서사: *"서툴러서 갈등하고, 그래서 다시 봄이 필요하다."*
- **해부학**: 씨앗형 몸통(위가 좁고 볼 높이가 가장 넓음) + 머리 위 떡잎 2장 + 짧고 통통한 팔다리(stroke 28). 몸이 곧 브랜드가 아니라 **머리 위 떡잎 하나**가 브랜드 — 몸은 연기를 위한 자유 도구.
- **떡잎 시그니처 규칙(2026-08-20)**: 떡잎은 장식이 아니라 감정 증폭기다. `droop`=시듦(소진·낙담·슬픔) · `perky`=쫑긋(놀람·기대·안도) · `bristle`=곤두섬(분노·격앙) · `normal`=평상. catalog의 `leaf_rule`에 기록.

### 3.2 색 규칙
| 용도 | 값 | 조건 |
|---|---|---|
| 외곽선 | `#5C4030` (딥브라운) | 항상 |
| 몸통(중립, 1인 장면) | `#FFF8F0` (크림) | 1인 장면 기본값 — 화자가 작성자든 상대방이든 중립 유지 |
| 몸통(작성자 진영, 2인 장면만) | `#E89A72` (밝은 피치) | **캐릭터 전용 변주.** FE 진영색 `#C9785A`와 같은 계열이되 밝다 |
| 몸통(상대방 진영, 2인 장면만) | `#6FB08A` (밝은 세이지) | **캐릭터 전용 변주.** FE 진영색 `#5F8F76`와 같은 계열이되 밝다 |
| 떡잎 | `#A8C8B4` (일러스트 전용 연세이지) | **진영 세이지(`#5F8F76`)와 다른 값** — 2인 장면에서 혼동 방지 |
| 볼터치 | `#F4A896` (살몬) | 선택 |

> ⚠️ 진영색을 **캐릭터용으로만** 밝게 변주한 이유: 원래 값(`#C9785A`/`#5F8F76`)은 어두워서 잉크색 이목구비와 대비가 죽고, 쇼츠 축소 시 표정이 사라졌다. **FE 앱의 진영색 토큰은 바꾸지 않는다** — 여기 값은 캐릭터 몸통 전용이다.

**규칙**: 1인 장면은 절대 진영색을 쓰지 않는다. 1인까지 피치로 칠하면 "화자=작성자=우리 편"이 무의식적으로 형성돼 공감 투표가 기울어진다.

### 3.3 캔버스·선화 규격
- 캔버스 820×820, 배경 투명(PNG 렌더 시 `-b` 옵션으로 배경색 넣지 말 것 — 런타임 합성기가 배경을 깐다)
- 외곽선 stroke: 몸통·얼굴선 `9`, 팔다리 `28`(굵게 — 이모티콘 가독성용), 줄기 `11`
- 이목구비는 `FACE_SCALE=1.22` / `FACE_DY=-18` 로 **그룹 transform 한 곳에서** 일괄 확대·배치한다(부품 좌표를 개별 수정하지 않는다). 선 굵기도 함께 커져 축소 가독성이 오른다.
- **팔·소품은 입 아래(y≳360)** 에 둔다 — 확대된 얼굴을 가리면 표정이 죽는다.
- `stroke-linecap="round" stroke-linejoin="round"` 고정
- 색 최대 5개 이내(외곽선 포함)

### 3.4 부품 어휘 (gen.py 함수 목록 — 새 포즈 만들 때 이 안에서 조합)

| 부품 | 함수 | 옵션 |
|---|---|---|
| 눈 | `eyes(mode)` | `dot`(기본) `wide`(놀람) `flat`(어이없음/째려봄용) `happy`(⌒⌒ 미소) `squint`(><분함) `teary`(눈물 맺힘) `side`(곁눈질) `down`(내리깐 눈/낙담) `sleepy`(반쯤 감김/체념) `cry`(눈물 폭발) `sparkle`(반짝반짝/부러움) `blink`(감은 눈 — **idle 깜빡임 전용, 씬에 직접 쓰지 말 것**) |
| 동공 하이라이트 | `_hl(cx, cy, r)` | 큰 점 + 작은 점 2개. 채워진 눈(`dot`·`wide`·`teary`·`cry`·`side`·`sparkle`)에 자동 적용 — **눈에 생기를 넣는 핵심 요소** |
| 눈썹 | `brow(mode)` | `angry`(찌푸림) `sad`(팔자) `up`(치켜올림/당황) `""`(없음) — **2026-08-12부터 표정 제약 해제로 도입** |
| 입 | `mouth(mode)` | `smile` `flat` `wavy`(당황) `open`(크게 벌림) `small_open` `tight`(꾹 다뭄) `grit`(이 악뭄) `pout`(삐죽) `big_smile`(활짝) `none` |
| 팔 | `arms(mode)` | `rest`(기본, 뒤) `phone`(짧은 팔로 휴대폰 받침, 앞) `cross`(팔짱 X자 교차, 앞) `clasp`(손 모아쥠, 앞) `limp`(축 늘어짐) `up`(두 팔 위/부들거림) `reach_r`(오른팔 뻗음) `hug`(무릎 감싸안기, 앞) `hold`(무거운 것 받쳐 듦, 앞) `shrug`(어깨 으쓱/손바닥 위로, 앞) |
| 다리 | `legs(mode)` | `stand`(기본) `sit`(앉음) `curl`(웅크려 접음) `none` |
| 떡잎 | `leaves(mode)` | `normal` `droop`(시듦/낙담) `perky`(쫑긋/놀람·기대) `bristle`(곤두섬/분노 — perky를 밑동 기준 40° 회전+길이 75%로 변환한 것. 맨손으로 새 path 그리면 "토끼 귀"가 된다, §9 참고) |
| 소품 | `prop(mode)` | `phone` `bundle`(포대기) `receipt`(영수증) `papers`(서류 더미) — 얼굴을 가리지 않는 y좌표(**≥366**, 2026-08-20 얼굴 확대로 하향 조정)에 배치 |
| 효과기호 | `marks(mode)` | `shock`(충격선) `sweat`(땀방울) `steam`(김/분노) `shake`(부들 떨림선) `quiet`(말줄임 점 3개) `sparkle`(반짝 마름모) `chatter`(뒷담화 말풍선 3개) `clock`(혼자 기다림 시계) `flinch`(움찔선) |
| 몸통 fill | `body(fill)` | §3.2 색 규칙 참고 |

`sibom(**kwargs)`가 이 부품들을 조립해 캐릭터 1인분을 만든다. `ARM_FRONT` 세트(`phone`,`cross`,`clasp`,`hug`,`hold`)는 몸통 **앞**에 그려지고, 나머지는 몸통 **뒤**에 그려진다 — 앞뒤 순서를 틀리면 팔이 얼굴을 가로지른다(1차 렌더에서 실제로 겪은 버그, §5 참고).

### 3.5 배치 헬퍼
- `place(body_svg, preset, scale=None)` — 1인을 슬롯 안전영역에 배치
- `duo(left, right, preset, gap, scale, tilt=0)` — 2인 대칭 배치. **중립 규칙**: 두 캐릭터의 표정 요소 개수·크기·선 굵기는 동일해야 함(감정 종류는 달라도 강도는 같게)
- `flip(svg)` — 좌우 반전 (2인 장면에서 오른쪽 캐릭터가 왼쪽을 보게 할 때)
- `row(items, preset, scale)` — 3인 이상 배치(현재 `left-out` 1장만 사용)

---

## 4. 슬롯 프리셋 & catalog.json 스키마

### 4.1 슬롯 3종 (좌표는 820×820 기준, 캔버스가 프레임에 1:1 배율로 놓인다는 전제 — §6.2 참고)

| preset | 캐릭터 안전영역 | 캡션 rect | maxChars | 용도 |
|---|---|---|---|---|
| `bottom` | y 24–560 전폭 | x40 y596 w740 h200 | **10**(2026-08 이후 조정, 코드가 이 값을 검증함) | 기본값, 가장 넉넉 |
| `top` | y 260–796 전폭 | x40 y24 w740 h200 | **10** | 캐릭터가 아래를 보는 구도 |
| `bubble` | x24–400 y180–796(좌하단) | x420 y40 w376 h300(말풍선) | **10** | 가장 빡빡 — 폴백이 가장 자주 필요 |

> ⚠️ 예전엔 16/16/12로 슬롯마다 달랐으나 현재 catalog는 **세 슬롯 모두 10으로 통일**돼 있다(`test_sibom_composite.py`가 이 값을 단언). 캡션은 항상 10자 이내로 써야 한다.

캡션 폰트: **80px bold `#5C4030`**, 위쪽 자막(기존 `image_text.caption_above`, 20자 상한)보다 크고 굵게 — 자막이 아니라 짤 글씨로 읽히게.

### 4.2 `catalog.json` 필드 설명 (이번에 강화한 부분)

```jsonc
{
  "id": "waiting-reply",       // WaggleBot assets/sprouts/{id}.png 파일명과 1:1
  "slot": "bottom",             // 프리셋 3종 중 하나
  "maxChars": 16,                // 프리셋에서 파생, 검증용으로 이미지에도 복사
  "people": 1,                   // 1|2|3 — 등장인물 수
  "arc": "reaction",             // "trigger"(사건) | "reaction"(감정상태) | "resolution"(결말)
                                  //   → 대본에서 이 이미지를 문단 어디쯤 배치할지 힌트
  "categories": ["ANY"],         // 적용 관계 카테고리 힌트(하드 필터 아님, 가중치용)
                                  //   COUPLE|MARRIED|FRIEND|FAMILY|WORK|OTHER|ANY
  "meaning": "읽씹·답장 없음",     // 사람이 읽는 설명
  "trigger": "연락을 했는데 답이 없거나 읽고 씹혔다고 말하는 대목",
                                  // LLM 프롬프트에 그대로 주입할 발동조건 문장
  "keywords": ["읽씹", "답장이 없다", "카톡을 읽고도", ...],
                                  // 🆕 규칙기반/폴백 매칭용 — LLM 실패 시 단순 포함검사로 후보 축소
  "caption": "읽씹 3일차",         // 검증된 샘플 캡션(리뷰 페이지에 쓴 것과 동일)
  "alt_captions": ["답장이 없다", "읽고 씹혔다"],
                                  // 🆕 대본 LLM에게 few-shot으로 줄 대체 캡션 2개
  "swap_group": "no_response",   // 🆕 같은 의미 그룹 — 캡션 초과 시 대체 후보 찾을 때 사용
  "sibling_bottom": "waiting-reply"
                                  // 🆕 그 swap_group 안에서 bottom 프리셋인(=글자수 여유 있는) 형제.
                                  //    null이면 대체 불가 → §4.3 폴백 3단계로 직행
}
```

### 4.3 폴백 체인 (질문 6·7에서 합의, `catalog.json`의 `fallback_chain`에도 동일 텍스트)

1. 캡션이 `maxChars` 초과 → 같은 `swap_group`의 `sibling_bottom` 이미지로 교체
2. `sibling_bottom`이 없거나 그것도 초과 → 캡션 비우고 캐릭터 이미지만 노출
3. 적합한 이미지가 전혀 없음 → `text_only` 씬으로 강등(이미지 없이 자막만)

**알려진 갭**: 아래 10장은 `sibling_bottom`이 없다(폴백 1단계 불가, 바로 2단계로 감). 2배치(31–60번) 설계 시 이 갭을 메우는 `bottom` 프리셋 형제를 우선 배정할 것.
`swallow-words · side-glance · indignant · nagging · cut-off · caught-lying · talked-behind-back · talked-over · pressured-decision · in-law-conflict`

---

## 5. 렌더링 중 실제로 겪은 문제 (재발 방지용)

새 포즈를 추가할 때 아래 실수를 반복하기 쉽다. 각 항목은 실제로 1차 렌더에서 나왔던 버그.

| 증상 | 원인 | 수정 |
|---|---|---|
| 팔이 얼굴을 가로지르는 큰 곡선 | 앞팔(`cross`,`phone` 등)을 몸통 **뒤**에 그림 | `ARM_FRONT` 세트로 분리, 몸통 그린 뒤에 그리기 |
| 팔짱이 몸통을 가로지르는 두꺼운 띠로 뭉침 | 팔짱 두 선이 거의 같은 궤적 | X자로 확실히 교차시키고 좌우 시작점을 크게 벌림(-158/158) |
| 반짝이는 눈이 `+` 기호로 보임 | 단순 십자선으로 구현 | 큰 눈동자 + 작은 하이라이트 원 2개 조합으로 교체 |
| 소품(서류·영수증)이 얼굴을 덮음 | y좌표가 너무 위(≤300) | y≥326로 내리고 폭도 축소 |
| "으쓱"(shrug) 팔이 일반 팔과 구분 안 됨 | 궤적이 `rest`와 거의 동일 | 손바닥이 위로 향하도록 위쪽으로 크게 꺾음 |
| `hold`(받쳐 듦) 팔이 소품과 안 붙어 보임 | 팔 끝 좌표가 소품 가장자리와 거리가 있음 | 팔 끝을 소품 경계에 거의 닿게(x=±46) 조정 |
| 말풍선(`bubble` preset) 꼬리가 캐릭터를 안 향함 | 꼬리 좌표를 대충 잡음 | 캐릭터 머리 위치(x=384 부근)를 향하도록 재계산 |
| 2인 등 돌림 장면이 뒷모습이라 앞모습과 구분 안 됨 | 뒤태를 별도로 그리려 함 | 뒤태 대신 **서로 반대쪽을 보는 얼굴**(`face_dx` 오프셋)로 표현 — 훨씬 명확 |
| Claude Design 업로드 시 60KB+ 파일을 손으로 옮기다 플레이스홀더를 잘못 씀 | 대용량 텍스트를 tool call에 직접 타이핑하려다 실수 | **교훈**: 페이지가 커지면 Bash로 10줄 단위 검증 후, 반드시 실제 콘텐츠 전체를 조립해서 올릴 것. 절대 "나중에 채우기" 식 placeholder를 실제 파일에 쓰지 말 것 |

---

## 6. Claude Design 프로젝트 (외부 리소스 — 여기 파일과 별개로 존재)

- **프로젝트**: "다시봄 — 시봄이 캐릭터 시스템"
- **project_id**: `0a210407-8afc-46bd-8708-7bf106aba19d`
- **리뷰 페이지 URL**
  - `시봄이 10장.dc.html` — **리파인 전 원본 기록**(파일명과 달리 30장). 비교 기준이므로 **덮어쓰지 말 것**
  - `시봄이 리파인 1차 대비 1-10.dc.html` / `11-20` / `21-30` — 2026-08-20 신구 대비 3부작 (사용자 승인 완료)
- ⚠️ **업로드 제약(실측)**: 한 파일 **40KB 부근에서 잘린다**. 그리고 `encoding:"base64"` 로 올리면 1,824 bytes로 잘리므로 **반드시 평문(`data`)으로** 올릴 것. 큰 페이지는 처음부터 여러 파일로 나눠 생성하고(사후 분할 금지 — 내용이 유실됨), 페이지 전체 범위 `<defs>`+`<use>` 중복 제거로 용량을 줄인다(30장 기준 130KB→27~31KB).
- ⚠️ **업로드 후 반드시 `list_files`로 실제 size를 로컬 바이트와 대조**할 것. 도구가 성공을 보고해도 파일이 없거나 잘려 있을 수 있다(2026-08-20 실제 발생).
- **페이지 구성**: 표정 세트 20종 + 30장 카드(각 카드 = 그림 + 점선 슬롯 표시 + 샘플 캡션 오버레이 + 발동조건 + 글자수 검증)
- 이 페이지는 라이브 프리뷰(`?embed=1` 추가)로 열면 다음 세션에서 `write_files` 호출 시 자동 갱신됨

### 6.1 다음 세션에서 이 프로젝트를 이어가는 법
1. `mcp__claude-design__get_claude_design_prompt` 먼저 호출(모든 Claude Design 작업 전 필수)
2. `mcp__claude-design__finalize_plan(project_id, scope:"project")`로 plan_token 발급
3. 이 폴더의 `gen.py`를 로컬에 복사해 실행 → 새 SVG 생성 → `build_page.py`로 리뷰 페이지 재생성
4. 페이지가 커서(현재도 60KB 근접) 한 번에 못 옮기면: Bash `sed -n 'A,Bp'`로 8–10KB 단위로 잘라 손실 없이 확인한 뒤 전체를 조립해서 `write_files` — **plain text로, placeholder 금지**
5. `if_match`에 직전 `write_files`/`read_file`이 반환한 etag를 반드시 넣을 것(동시편집 충돌 방지)

### 6.2 런타임 이관 계획 — **재정의 (2026-08-12 그릴링)**

> ⚠️ 아래 구(舊) “60장 확정 후 / WaggleBot이 매칭 SSOT” 계획은 **폐기**.  
> **현행 SSOT**: [`docs/shared/marketing/sibom-video-insertion.md`](../../../../shared/marketing/70-policy/sibom-video-insertion.md)  
> 요약: **30장으로 즉시 진행** · 메타포 영상 경로 **완전 금지** · **AS**가 shortlist·`sibom_plan`·가드 · WaggleBot은 합성·모션만 · YT/IG 채널별 분리 LLM 각색.

| 단계 | 작업 |
|---|---|
| 1 | `svg/*.svg` → PNG 820×820 (투명). `.temp/sprouts/png/` 또는 동등 미러 |
| 2 | PNG + `catalog.json` → `WaggleBot/assets/sprouts/` (렌더 소비용 미러). **플랜 SSOT는 AS brief** |
| 3 | AS: 사연 저장 시 코드 keyword → `sibom_candidates` · 영상 직전 LLM이 채널별 `sibom_plan` |
| 4 | AS→ASM→WaggleBot brief: `sibom_plan` 패스스루 · `metaphor_id` 언플러그 |
| 5 | WaggleBot: 캡션 PIL 합성 + large/small·hold/punch·모션. `distribute_images()` 메타포 경로 폐기 |
| 6 | 추가 30장은 이후 상황 보고 — 현재 설계는 30장만 가정 |

---

## 7. 다음 세션 시작 체크리스트

컨텍스트가 없는 상태에서 이 작업을 재개하라는 요청을 받으면:

> ⚠️ 2026-08-20 갱신: 아래 2·3번(30장 리뷰 확인 → 31–60장 설계)은 **이미 지나간 단계다.** 30장 리뷰는 완료됐고, 사용자 판단은 "장수를 늘리기 전에 그림체·모션부터"였다. **31–60장은 보류 상태**이며 현행 우선순위는 `.temp/sibom/emoticon/upgrade-plan.md` 를 따른다.

1. 이 README를 처음부터 읽는다 (§0 요약 → §2 결정 로그 순).
2. ~~사용자에게 "30장 리뷰를 마쳤는지" 확인~~ → 완료(2026-08-20 승인). 대신 **§9 리파인 로그**와 `.temp/sibom/emoticon/upgrade-plan.md` 를 읽는다.
3. 안 읽히는 이미지 피드백이 없으면: §4.3 "알려진 갭"부터 메운다(각 `sibling_bottom: null` 항목에 대응하는 `bottom` 프리셋 형제 이미지 10장 정도를 우선 설계 후보로 제안).
4. `gen.py`를 이 폴더에서 로컬 스크래치패드로 복사해 이어서 실행(원본은 이 폴더에 남기고, 작업은 스크래치패드에서).
5. 31–60번 완성되면 §6.2 런타임 이관 계획을 순서대로 실행 — **사용자의 명시적 승인 없이 WSL/WaggleBot 리포에 파일을 쓰거나 AS의 `metaphor_id` 관련 코드를 수정하지 말 것.** 이 문서는 계획이지 실행 허가가 아니다.
6. `docs/frontend/structure.md`의 `design/specs/` 트리 목록에 이 폴더가 이미 추가돼 있는지 확인(§8 참고, 이번 세션에서 이미 반영함).

---

## 8. Doc-Sync 메모

- `docs/frontend/structure.md`의 design/specs 트리에 이 폴더를 1줄 추가함(이번 세션에서 완료).
- `docs/frontend/design/specs/metaphor-illustration-system.md`는 **건드리지 않음** — 두 시스템은 별개로 공존(레거시 vs 신규). 60장 확정·런타임 이관이 끝나면 그 문서 상단에 "영상용은 sprout-character-system.md 참고" 안내를 추가할 것(아직 안 함).
- CLAUDE.md 라우팅 표는 수정하지 않음 — 기존 "FE 디자인" 행이 `docs/frontend/design/` 트리 전체를 이미 가리키고 있어 이 폴더도 그 아래 자동 포함됨.

---

## 9. 그림체 리파인 1차 (2026-08-20 · 배포 완료)

계획·경위 권위본: `.temp/sibom/emoticon/upgrade-plan.md`

### 왜 했나
사용자 피드백 "귀엽긴 한데 확 끌리는 매력은 없다". 원인을 눈 12종 전수 렌더로 특정했다:

**🔴 가장 많이 쓰이던 `down` 눈이 단순 아래꺾임 곡선이라 `blink`(감은 눈)와 렌더 결과가 사실상 같았다 → 30장 중 12장이 눈을 감고 있었다.** 이게 매력 부재의 최대 원인이었다.
부차 원인: 눈 반지름 17(몸통 rx=150)·하이라이트 없음 / 외곽선 7이라 축소 시 형태 소멸 / 다리가 몸통에 가려 18px만 노출 / 어두운 진영색 위에서 잉크 이목구비 대비 실종.

### 무엇을 바꿨나 (`gen.py` 부품 단위 → 30장 자동 반영)
- `down` 눈을 **윗꺼풀에 덮인 큰 동공**으로 재설계. `blink`는 idle 깜빡임 전용으로 분리
- `wide`·`side`가 크림 흰자+굵은 외곽선 탓에 **물안경**처럼 보이던 것 → 큰 동공 방식으로 교체
- `teary`·`cry` 눈물이 **구레나룻**처럼 옆에 붙던 것 → 물방울(`_drop`)·눈 아래 흐름으로 교체
- 눈 반지름 17→27 + 하이라이트 2점(`_hl`), 얼굴 전체 `FACE_SCALE=1.22`/`FACE_DY=-18`
- 진영색을 **캐릭터 전용 밝은 변주**로(§3.2), 외곽선 7→9, 팔다리 22→28, 몸통 정타원→씨앗형
- 확대된 얼굴을 팔짱·소품이 가려서 **앞팔 4종과 소품 4종을 입 아래로 재배치**
- 떡잎 `bristle`(곤두섬) 신설 + 감정→떡잎 규칙을 catalog `leaf_rule`에 기록
  - ⚠️ 초판은 좁고 곧게 세운 형태였는데 **"토끼 귀"로 보여서 폐기**(2026-08-21 수정). 대안으로 폭을 줄여봤더니 선처럼 뭉개졌다 — **잎의 폭(실루엣)은 유지하고 각도만 세워야** 떡잎으로 읽힌다. 최종안 = 검증된 `perky`를 밑동 기준 **40° 회전 + 길이 75%**. 새 떡잎 모드를 만들 때도 `perky`/`normal`을 변환하는 방식을 쓸 것(맨손으로 path를 그리면 폭이 죽는다).
- Phase 2 준비: catalog 각 이미지에 `motion` 필드(sway/shake/sob/sink/pop) + `motion_kinds` 정의

### 🚨 catalog는 절대 통째로 쓰지 말 것
`gen.py`가 만드는 catalog는 항목당 5필드 축약본인데, 런타임 catalog는 **13필드**다
(`categories`·`arc`·`keywords`·`caption`·`alt_captions`·`swap_group`·`sibling_bottom`·`people`·`maxChars` + 최상위 `fallback_chain`).
게다가 `presets.maxChars`는 런타임이 **10**인데 생성기 기본값은 16/12였다 — 덮어썼다면 **장면 매칭과 자막 길이 제한이 동시에 파괴**된다(`test_sibom_composite.py`가 `maxChars==10`을 검증 중).

→ `gen.py`를 **병합 방식**으로 고쳤다. 기존 catalog를 읽어 `slot`·`motion`만 갱신하고 나머지 키는 전부 보존한다.
병합 기준을 `svg/catalog.json` → `../catalog.json` 순으로 찾고, **못 찾으면 얇은 catalog를 쓰는 대신 `SystemExit`으로 중단**한다(조용한 메타 유실이 최악이라서). 실행 후 매칭 메타 유실 여부를 `assert`로 검사한다.

### 배포 위치 (3곳 동기화, md5 일치 확인)
| 위치 | 내용 |
|---|---|
| WSL 런타임 `WaggleBot/assets/sprouts/` | svg 30 · png 30(820×820 RGBA) · catalog |
| WSL 생성기 `WaggleBot/assets/sprouts_design/` | `gen.py` · `svg/`(병합 기준 catalog 포함) |
| 여기(AS SSOT) | scene svg 30 · face svg 20 · catalog · gen.py · build_page.py |
| 배포 전 백업 | WSL `assets/sprouts.bak/` |

- **PNG는 로컬에서 렌더해 rsync한다** — WSL에는 `rsvg-convert`·`inkscape`·`cairosvg`가 **하나도 없다**.
- 🚨 **WaggleBot의 `assets/`는 gitignore 대상**(`.gitignore:61`)이다. 거기 있는 시봄이 SVG·PNG·catalog는 **버전관리되지 않는 배포 산출물**이고, **버전 권위본은 이 폴더(AS)** 다. 아트를 고치면 ① 여기에 커밋하고 ② WSL로 rsync 배포한다. WaggleBot 쪽만 고치면 다음 배포나 컨테이너 재생성 때 소리 없이 사라질 수 있다.
- 검증: WSL `pytest worker/test/test_sibom_composite.py` → 9 passed

### 판정 기준
**쇼츠 축소(165px 상당) 렌더로 판정한다.** 원본 820px에서 괜찮아 보여도 축소하면 표정이 소멸한다 — 이 테스트가 리파인 성패를 가른 기준이었다.

---

## 10. 2배치(31~60) — 매칭 실측 기반 재설계 (2026-08-21 · 배포 완료)

계획·실측 근거 권위본: `.temp/sibom/emoticon/upgrade-plan.md`

### 착수 전 실측이 뒤집은 것

31~60장을 그리기 전, "그림 커버리지가 아니라 **매칭이 실제로 동작하는지**"부터 실측했다. 코드(`SibomCandidateService.java`)를 읽어보니 이미지 선택은 **순수 부분문자열 검사**(`text.contains(keyword)`, 형태소 분석 전혀 없음)였다. prod 사연 265건에 당시(1배치) keywords를 그대로 돌리자 **252건(95%)에서 후보 0개**였다 — 조사·어미가 붙은 문장형 키워드(`"말을 안 한 지"`)가 실제 사연 문장과 거의 일치하지 않았기 때문. soft_fill 풀 7개가 항상 최소조건을 채워줘서 발행은 안 막혔지만, **265건 중 252건이 조용히 범용 7장으로만 영상이 만들어지는 상태**였다(에러 없음).

→ 순서를 바꿨다: **① 기존 30장 keywords를 2~6자 어간으로 전면 교체(그림 불변) → ② 31~60장을 같은 규칙으로 설계.**

### 결과 (실측)

| 단계 | 코퍼스 커버리지(265건 기준) | 평균 후보수 |
|---|---|---|
| 1배치 keywords(문장형) | 5% | 0.2 |
| 1배치 keywords 어간 교체 후 | 94.3% | 3.36 |
| 2배치(60장) 완료 후 | **99.2%** | **6.74** |

매칭 0건 이미지 없음. swap_group dedup 이후에도 4장 미만으로 떨어지는 사연은 17.4%뿐(전량 soft_fill이 보완, 발행 차단 없음 — 1배치 시절 95%에서 대폭 개선).

### 31~60장 구성

- **9장** = 기존 30장 중 캡션 폴백(`sibling_bottom`)이 없던 그룹에 **bottom 슬롯 형제**를 새로 그려 채움(`holding-in`·`glancing-around`·`quiet-anger`·`scolded-silent`·`walking-away`·`evidence-found`·`overheard`·`voice-drowned-out`·`decision-announced`). 구·신 상호 링크까지 완료 — `sibling_bottom` 미보유가 11개→21개(전부 신규 top/bubble, 알려진 스코프)로 감소.
- **21장** = prod 사연 코퍼스 실측 상위 주제(잠정 추정과 실측이 갈린 항목 다수 — 예: "외도"는 잠정 2위였으나 실측 25위, "생활습관 충돌"이 실측 2위로 상향). `COUPLE×reaction`(`forgotten-anniversary`) · `WORK×resolution`(`quit-decided`) 구조적 갭도 메움. 3인 장면 1→2장(`drunk-conflict` 신설), 감정 스펙트럼에 없던 질투(`jealous-envy`)·죄책감(`guilt-heavy`) 신설.
- 기존 30장과 의미가 겹치는 주제(뒷담화·시댁·독박육아·잔소리·사과거부 등)는 신규 이미지 없이 1배치 키워드 보강으로 흡수.

### gen.py 구조 변경 — `emit()` 확장 + 완결성 가드 강화

기존 `emit(name, preset, inner, meaning, trigger)`는 **1배치 이미지 갱신만** 전제로 설계돼 있었다(catalog 병합 시 기존 id는 slot/motion만 갱신, 나머지는 병합 기준 catalog 값을 그대로 씀). **완전히 새 id는 병합해줄 기존 항목이 없다** — 그래서 `emit()`에 `people`·`arc`·`categories`·`keywords`·`caption`·`alt_captions`·`swap_group`·`sibling_bottom`·`max_chars` kwargs를 추가했고, 병합 로직에 **신규 id의 필수 필드 누락 시 `SystemExit`으로 중단**하는 가드를 넣었다(구 가드는 "이미지 중 하나라도 있으면 통과"였던 약한 `any()` 검사였다 — 전부 있어야 통과하는 `all()`로 강화).

**🚨 구조적 함정**: catalog 병합 블록(`import json` 이하)은 **파일 끝이 아니라 30번째 이미지 직후**에 최상위 코드로 실행된다. 새 `emit()` 호출을 파일 끝에 추가하면 **에러 없이 조용히 무시된다**(병합이 이미 끝난 뒤에 실행되므로). 31~60번 씬은 반드시 30번(`reconciled`) 직후 · `import json` 직전에 삽입해야 한다.

**병렬 제작 방식**: 6개 sonnet 에이전트가 5장씩 담당하되, **각자 `emit()` 호출 코드만 별도 파일에 작성**하고 gen.py를 직접 실행하지 않게 했다(위 삽입 지점이 하나뿐이라 여러 에이전트가 동시에 같은 파일을 건드리면 충돌한다). 메인 세션이 6개 결과를 한 파일로 통합해 1회 실행.

### 🚨 배포 시 발견한 다섯 번째 사본 — AS 백엔드 classpath 리소스

지금까지 "3곳 동기화"(AS SSOT·WSL 런타임·WSL 생성기)로 충분한 줄 알았는데, **AS 백엔드가 실제 매칭에 쓰는 catalog는 이 중 어디도 아니었다.** `SibomCatalog.java`가 읽는 경로는 `ClassPathResource("sibom/catalog.json")` — 즉 **`backend/src/main/resources/sibom/catalog.json`** (별도 빌드 리소스 사본). 이 사본이 1배치 키워드 교체 이전 상태로 굳어 있었다 — **동기화가 안 됐으면 이번 매칭 개선 전체가 실제 운영에 반영되지 않을 뻔했다.**

→ **동기화 대상은 5곳**: AS SSOT(`docs/frontend/design/specs/sprout-character-system/catalog.json`) · **AS 백엔드 리소스**(`backend/src/main/resources/sibom/catalog.json`) · WSL 런타임(`assets/sprouts/catalog.json`) · WSL 생성기 2곳(`assets/sprouts_design/catalog.json`, `assets/sprouts_design/svg/catalog.json`). 백엔드 리소스는 `JsonNode` 트리로 느슨하게 파싱해서 WaggleBot 전용 필드(`motion`·`leaf_rule`·`motion_kinds`)가 섞여 있어도 무해하다 — 5곳 전부 **동일 파일**을 그대로 복사하면 된다.

### 검증

- 코퍼스 재측정(위 표) — `/tmp/posts.txt` 265건 기준
- WSL `pytest test_sibom_motion.py test_sibom_plan_director.py test_sibom_composite.py` → 33 passed(`test_catalog_loads_30_images`가 `_60_images`로, 카운트 단언도 60으로 갱신)
- AS 백엔드 `./gradlew test --tests "*Sibom*" --tests "*VideoVariantService*"` → 49 passed (SibomCandidateService 8·SibomPlanGuard 17·VideoVariantService 24)
- 60장 렌더 후 165px 축소 콘택트시트로 육안 검수. 🚨 **1차 검수에서 자체 QA 스크립트 버그**(PNG를 `.resize()` 없이 그대로 붙여넣어 인접 이미지가 겹쳐 보임)로 5장을 오탐 — 개별 파일로 재확인해 전부 정상임을 확인. 그리드 스크립트를 쓸 때는 반드시 `paste()` 전에 `resize()`를 확인할 것.
- `caption`/`alt_captions` 10자 초과 2건(`habit-clash`·`wedding-stress`) 발견 후 수정.

### 10.1 키워드 노이즈 정리 (2026-08-22)

prod 실사연 렌더 테스트 도중 `credit-stolen`이 `내가`(코퍼스 265건 중 129건=49%!)로,
`parents-control`이 `얘기`(106건=40%)로 걸리는 걸 실측으로 발견했다. 부분문자열 매칭
특성상 대명사·접속사급 단어를 키워드로 두면 사실상 랜덤 노이즈가 된다.

전체 60장을 코퍼스 재감사(빈도 8% 이상 또는 정지어 사전 매칭) → 16건 의심 항목 중
**의미상 진짜 핵심어인 것(`헐`·`분위기`·`힘들`·`나만` 등, 빈도는 높지만 주제와 실제로
맞음)은 유지하고, 대명사/접속사성 노이즈만 제거**했다(7장: credit-stolen·
parents-control·late-regret·turned-blame·quit-decided·drunk-conflict·overheard).
제거로 놓친 5건(코퍼스 기준 커버리지 99.2%→98.1%)을 직접 확인한 결과 전부 그 노이즈
키워드 덕분에 우연히 걸렸던 무관한 사연이었다 — 정밀도 개선이 맞았다.

**교훈**: 키워드 개수·길이 규칙(2~6자, 6~10개)만으로는 부족하다. 배포 후 반드시
실제 코퍼스로 빈도 감사를 해서 "흔하지만 무관한 단어"가 섞이지 않았는지 확인할 것.

### 10.2 soft_fill 풀 7→14 확장 (2026-08-22)

`SibomPlanGuard.SOFT_FILL_POOL`(AS 백엔드, `backend/.../service/community/SibomPlanGuard.java`)은
1배치(30장) 시절 고른 7개로 고정돼 있었다 — 60장 확장 후에도 자동으로 늘지 않는
하드코딩 상수라 §10 스코프에서 명시적으로 제외했던 항목이다. 매칭 실패 사연이
99.2%→17.4%(swap_group dedup 이후 4장 미만)로 줄면서 soft_fill 의존도는 낮아졌지만,
여전히 걸리는 사연은 항상 같은 7장(`drained`·`curled-up`·`stunned`·`swallow-words`·
`indignant`·`side-glance`·`relieved`)만 봤다.

60장 카탈로그에서 `people()==1`이면서 기존 7개와 겹치지 않는 새 `swap_group`을 가진
7개(`guilt-heavy`·`walking-away`·`overloaded`·`money-trouble`·`health-ignored`·
`jealous-envy`·`burst-crying`)를 추가해 풀을 14개로 확장했다 — 전부 캡션 10자 이내
(`presets.maxChars` 검증 완료). `SibomPlanGuardTest.softFillNonPool_demotesToPunch`가
"풀에 없는 이미지" 예시로 쓰던 `money-trouble`이 이번에 풀에 편입되면서 테스트 전제가
깨져 `late-regret`으로 교체했다.

검증: `./gradlew test --tests "*Sibom*" --tests "*VideoVariantService*"` 49 passed →
dev(:8090) 배포·jar 문자열로 신규 7개 확인 → e2e-realbe 116 passed → prod DB 백업 →
prod(:8091) 배포·jar 문자열 재확인 → `https://againspring.net/api/health` 200(nginx
stale-IP 재발 없음).
