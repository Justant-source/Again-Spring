# 시봄이 캐릭터 업그레이드 계획 (v1)

> **상태**: 진행 중 · **마지막 갱신**: 2026-08-21
> (`docs/_active/README.md` 규약 — 완료 시 `docs/frontend/assets/sprout-character-system/README.md`로 요약 승격 후 이 파일 삭제)

> 작성: 2026-08-20 · /grilling 세션 결과 확정본
> 작업장: **Claude Design 기존 프로젝트** — [다시봄 — 시봄이 캐릭터 시스템](https://claude.ai/design/p/0a210407-8afc-46bd-8708-7bf106aba19d) (`시봄이 10장.dc.html` 이어서 사용)

---

## 0. 그릴링 확정 결정 요약

| 질문 | 확정 답 |
|---|---|
| 1순위 목표 | **쇼츠 시청 지표** (유지율·조회수·팔로우 전환) |
| 아쉬운 지점 | ① 정적이라 뻣뻣함 ② 그림체/디자인 자체 ③ 존재감/역할 애매 |
| 작업 순서 | **① 그림체 리파인 → ② 애니메이션 강화 → ③ 31~60장 추가 보류** |
| 리파인 범위 | 눈·표정 확대 + 진영색 조정 + 팔다리 개선 + 시그니처 요소 — **전부** |
| 애니메이션 수준 | **idle 루프 + 감정별 모션 매핑** |
| 검증 방식 | **3단 게이트** (정지 리뷰 → render-only → 발행+지표 관찰) |
| 실행 모델 규칙 | **Fable=계획만. 구현은 sonnet/opus** (탐색·기계적 작업=sonnet, 어려운 디자인 판단=opus). 멀티에이전트 최대 4 병렬, 토큰 절약 |

### 왜 "30장 추가"가 아니라 "리파인+애니메이션"인가

- 쇼츠 시청자는 **한 편만** 본다. 한 편에 들어가는 시봄이는 4~7장 → 풀이 30이든 60이든 시청자 체감 없음.
- 그림체가 안 끌리는 상태로 30장을 더 만들면 **안 끌리는 스타일이 60장으로 굳는다.**
- 시봄이는 `gen.py` 부품 조립식 → **부품만 고치면 30장 자동 재생성.** 리파인 비용이 압도적으로 싸다.
- 풀 확장(31~60)은 리파인·애니메이션 완료 후 **새 스타일로** 진행 (Phase 3, 보류).

---

## 1. 현재 상태 (탐색 결과)

- **에셋**: 30/60장 완성. procedural SVG → PNG 820×820.
  - 디자인 SSOT: `docs/frontend/design/specs/sprout-character-system/` (AS 저장소) — `catalog.json` + `svg/`
  - 런타임 사본: `WaggleBot/assets/sprouts/` (png·svg·catalog.json) + 생성기 `WaggleBot/assets/sprouts_design/gen.py` (554줄)
  - 로컬 미러: AS `.temp/sprouts/png/`
- **부품 어휘**: 눈 11종 · 입 9종 · 팔 11종 · 다리 4종 · 떡잎 3종(normal/droop/perky) · 마크 9종
- **영상 사용**: 쇼츠 5~7장 / 릴스 4~5장. 역할 = intro(large·hold) / peak(large·hold) / punch(small) / softfill
- **애니메이션 현황**: punch-pop 8프레임(스케일 92→100% + 페이드인, 1.2초)뿐. **이후 dwell 내내 완전 정지** ← 뻣뻣함의 근본 원인. shake는 TODO 상태 (`sibom_composite.py`, `_write_sibom_punch_frames`)
- **🚨 WaggleBot 이중 저장소**: 로컬 `/home/justant/Data/WaggleBot` + WSL `~/Data/WaggleBot` 양쪽 존재. 작업 전 git 동기화 상태 확인 필수.

---

## 2. Phase 1 — 그림체 리파인 (매력의 근원)

**원칙**: `gen.py` 부품 함수 수정 → 30장 전체 자동 재생성 → Claude Design 리뷰 페이지 갱신 → 승인 후 배포.

### 2.1 눈·표정 확대 (효과 최대)
- 눈 크기 상향 + **하이라이트(반짝임 점) 추가** — dot/wide/teary 등 11종 전부.
- 이목구비의 얼굴 점유율 확대 (현재 중앙에 조그맣게 몰림 → 감정 전달 면적 확보).
- 목표: 쇼츠 `small`(40% 축소) 사이즈에서도 표정이 읽힐 것.

### 2.2 진영색 밝기/채도 조정
- 현재 2인 장면 몸통(벽돌 피치 `#C9785A` / 짙은 세이지 `#5F8F76`)이 탁하고, 어두운 몸 + 어두운 갈색 이목구비로 대비 실종.
- **캐릭터용 변주 팔레트** 신설: FE 진영색과 같은 계열이되 밝고 맑은 톤 (예: 피치 파스텔↑, 세이지 명도↑). FE 앱 진영색 자체는 불변.
- 어두운 몸에서도 이목구비 대비 확보 (외곽선/이목구비 색 재검토).

### 2.3 팔다리 형태 개선
- 막대(선) 팔다리 → 몸통에 자연스럽게 붙은 통통한 형태. 포즈 11종 재설계 필요 — 리파인 중 작업량 최대 항목.
- 끊겨 보이는 페그형 다리 → 둥근 연결.

### 2.4 시그니처 요소 — "떡잎 감정 연동" 규칙
- 떡잎(브랜드 아이덴티티)이 감정에 반응하는 **캐릭터 고유 규칙** 수립:
  - 분노 → 곤두섬(perky+떨림) · 슬픔 → 시듦(droop) · 기쁨 → 살랑임 · 충격 → 쭈뼛
- 정지 이미지에서도 적용, Phase 2 애니메이션의 핵심 재료가 됨 (떡잎 = idle 루프의 주역).
- `catalog.json`에 감정→떡잎 상태 매핑 명시.

### 2.5 산출물
- `gen.py` 부품 개정 + 30장 SVG/PNG 재생성
- Claude Design 리뷰 페이지 갱신 (기존 프로젝트, 신구 대비 비교 뷰 권장)
- `catalog.json` 갱신 (떡잎 규칙 필드)
- AS 스펙 디렉토리 ↔ WaggleBot assets 동기화

---

## 2.9 Phase 2 착수 전 조사 결과 (2026-08-21) — 판정: **중단된 전환 상태**

Phase 2에 들어가기 전, `layout.py`의 미커밋 삭제 251줄이 다른 세션의 **의도적 완성 설계**인지 확인했다(사용자 지시로 보류 후 조사). 사용자 확인: 다른 세션은 모두 종료됨.

**판정: C) 작업 중단(미완성)** — 의도적 아키텍처 전환에 착수했으나 대체 구현이 없다.

| 항목 | 상태 |
|---|---|
| 모션 메타데이터 저장 | ✅ `_attach_sibom_plan_fields()`가 entry에 `sibom_role`·`sibom_dwell`·`sibom_image_id`·`sibom_shake` 기록 |
| 렌더 루프에서 사용 | ❌ `_render_intro_frame`/`_render_image_text_frame`이 **전부 무시** |
| punch 프레임 생성 | ❌ `_write_sibom_punch_frames()` 정의만, 호출 없음 (dead code) |
| 합성 함수 | ❌ `_compose_sibom_onto_base()` 정의만, 호출 없음 (dead code) |
| breathe 루프 | ❌ 삭제됨 |
| 모션 테스트 | ❌ `test_sibom_motion.py`가 삭제된 심볼을 import → 깨진 상태 |

**아키텍처 전환의 방향**: `66ef39b`("Sibomi as silent image_only beats")부터 시봄이가 **슬롯 오버레이 → 자체 카드 프레임**으로 바뀌었다. 따라서 삭제된 슬롯 전제 합성(`_compose_sibom_into_slot`·`_tonel_text_only_sibom_rect`)은 **되살리면 안 된다.**

**재사용할 수식 (HEAD에서)**: pop ease-out `1-(1-t)²` · breathe 사인 `1+A·sin(2πi/n)`(16프레임/2.0초/±3%) · shake 감쇠 `(1-t)^1.5` × 2.1Hz 타원.

**현재 시봄이 경로**: `director.apply_sibom_plan_to_body` → `sibom_plan.materialize_sibom_image`(캡션 PIL 합성) → `layout._attach_sibom_plan_fields`(메타만) → `_render_*_frame`이 시봄이 PNG를 **정적 이미지로 합성**. 모션 없음.

> 백업: WSL `~/backup/wagglebot-20260821/{layout,_frames,director,sibom_plan}.py.bak`

---

## 3. Phase 2 — 애니메이션 강화 (뻣뻣함 해결)

**원칙**: 별도 애니메이션 툴 없음. `gen.py`로 프레임 변형 생성 + WaggleBot PIL 합성 확장.

### 3.1 idle 루프 (핵심)
- 등장(punch-pop) 이후 **dwell 내내** 미세 모션 반복:
  - 숨쉬기 바운스 (스케일 ±1~2%)
  - 눈 깜빡임 (2~4초 주기, 프레임 변형)
  - 떡잎 살랑임 (Phase 1 시그니처 규칙과 연동)
- `hold` dwell(인트로·피크)에서 효과 최대.

### 3.2 감정별 모션 매핑
- `catalog.json`에 `motion` 필드 추가, 씬별 매핑:
  - 분노·충격(two-argue, indignant, stunned, burst-crying) → **shake** (기존 TODO 구현)
  - 울음 → 들썩임 · 지침(drained) → 처짐 · 화해(reconciled) → 튀어오름 등
- 구현 위치: `WaggleBot/worker/ai_worker/renderer/sibom_composite.py` + `layout.py` 프레임 라이터 확장.

### 3.4 ✅ Phase 2 구현 완료 (2026-08-21) — `WaggleBot/worker/ai_worker/renderer/layout.py`

**배선 방법 (핵심)**: 슬롯 rect를 밖에서 재계산하려던 접근은 **실패한다** — 미디어 박스 위치가 캡션 줄수에 따라 달라지기 때문. 대신 **캐릭터를 자기 캔버스 안에서 변형**(`_sibom_variant`)하고 기존 프레임 렌더러를 그대로 다시 호출한다. 렌더러(`_frames.py`)를 **한 줄도 건드리지 않고** 모션이 붙는다.

| 추가 API | 역할 |
|---|---|
| `_sibom_variant(pil, scale, dx, dy, alpha)` | 캔버스 크기 유지한 채 내용만 확대·이동·페이드 |
| `_sibom_motion_sequences(render_frame, ...)` | punch 12프레임 + motion별 루프 프레임 생성 |
| `_wire_sibom_motion(entry, render_frame, ...)` | 시봄이 씬이면 경로를 entry에 심음. 실패 시 정지 프레임으로 **graceful degrade** |

- **배선 지점**: `intro`(start_alpha=0.60 — 첫 프레임이 썸네일 후보) · `image_text`(0.35). `text_only`는 미디어 슬롯 자체가 없어 모션 불가 → 오해 소지 있던 TODO 제거하고 사유를 주석에 명시.
- **루프 이음매**: 모든 루프를 사인 기반으로 만들어 `i=0`과 `i=n`이 이어지게 했다(타일링 시 튐 방지). `sink`는 선형이면 되돌아올 때 튀어서 `(1-cos)/2` 로 교체.
- `sibom_dwell="punch"` 는 루프 없이 등장만.

**검증 (실측)**

- 유닛: `test_sibom_motion.py` **18 passed** — 배선 계약 테스트 4개를 새로 추가(`_wire_sibom_motion`이 entry를 채우고, `_build_visual_timeline`이 그 경로를 소비하며 **정지 프레임을 쓰지 않음**을 고정)
- 회귀: sibom 3개 파일 **27 passed / 1 failed** — 실패 1건은 **기존 회귀**(`test_again_spring_plan_maps_intro_and_peak`, `punch` 역할 미배정). `layout.py`만 수정했고 실패 지점은 `sibom_plan`/`director`라 무관. ⚠️ 별도 처리 필요
- **실렌더 스모크 신설**: `worker/test/smoke_sibom_motion.py` — 실제 `_render_image_text_frame`으로 프레임을 굽고 픽셀 차이를 확인. 4종(shake/sway/sink/sob) 전부 통과, 대비 시트 육안 확인 완료
  - ⚠️ 사인 모션은 `i=n/2`에서 0으로 돌아온다 — **중간 프레임끼리 비교하면 "모션 없음"으로 오판한다.** 최대 편차 지점을 찾아 비교할 것(실제로 이 함정에 한 번 걸림)

**미구현**: 눈 깜빡임 — 감은 눈(`blink`) PNG 자산이 없어 scale/offset 모션만 구현. `catalog.motion_kinds`의 `sway` 설명과 이 점이 어긋나므로 코드에 TODO로 명시함.

### 3.3 제외 (이번 범위 아님)
- 장면별 등장/퇴장 연출 다양화(슬라이드인·회전 팝) — "전면" 수준은 보류. idle+감정 모션 지표 확인 후 재논의.

---

## 4. Phase 3 — 31~60장 추가 (보류)

- **재개 조건**: Phase 1·2 발행 후 1~2주 지표 관찰 완료 + 새 그림체 확정.
- 재개 시 우선순위: `sibling_bottom: null` 갭 10장(swallow-words, side-glance, indignant, nagging, cut-off, caught-lying, talked-behind-back, talked-over, pressured-decision, in-law-conflict)의 bottom 형제부터.

---

## 5. 검증 — 3단 게이트

| 게이트 | 내용 | 통과 기준 |
|---|---|---|
| **G1 정지 리뷰** | Claude Design 리뷰 페이지에서 신구 30장 대비 | 사용자 승인 |
| **G2 render-only** | 테스트 영상 2~3편 mp4만 렌더 (**발행 절대 금지**) | 모션·가독성 사용자 승인 |
| **G3 발행+관찰** | 승인 후 발행 재개, 1~2주 쇼츠/릴스 지표 관찰 | 유지율·조회수 추이 |

**🚨 공유 인스턴스 주의**: ASM/WaggleBot은 dev/prod 구분 없는 단일 인스턴스 — 재배포·테스트가 실계정 발행으로 이어진 사고 전례(2026-07-31) 있음. G2는 반드시 render-only 경로 사용, 새 자동 동작은 기본 false 플래그.

---

## 6. 실행 규칙 (모델·에이전트)

- **Fable = 계획·판단만.** 구현 에이전트에 Fable 사용 금지.
- 구현 배분: gen.py 부품 수정·재생성·동기화 등 기계적 작업 = **sonnet** / 그림체 시안 판단·모션 커브 튜닝 등 미적 판단 = **opus**
- 멀티에이전트 **최대 4개 병렬**, 독립 작업만 병렬화.
- WaggleBot 이중 저장소: 작업 전 로컬↔WSL git 상태 대조 → 한쪽을 기준으로 커밋·push → 반대쪽 pull.
- Claude Design 리뷰 갱신은 기존 프로젝트/파일에 이어서 (새 프로젝트 생성 금지).

---

## 6.5 Phase 1 실행 기록 (2026-08-20)

### 🚨 착수 시 발견한 저장소 사실 (계획 수정)

| 발견 | 내용 |
|---|---|
| WaggleBot 이중 저장소가 **갈라져 있음** | WSL이 정본(GitHub 원격 보유), 로컬 `/home/justant/Data/WaggleBot`은 WSL을 origin으로 삼는 **뒤처진 클론**. `sibom_composite.py`도 md5가 서로 다름. **모든 작업은 WSL 기준** |
| `gen.py`는 **WSL에만 존재** | `~/Data/WaggleBot/assets/sprouts_design/gen.py`. 로컬 클론엔 `sprouts_design/` 자체가 없음 |
| **punch-pop조차 동작하지 않음** | `_write_sibom_punch_frames()`가 정의만 있고 **호출 지점 없음**(layout.py:382). 시봄이는 현재 100% 완전 정지 — "뻣뻣함"의 실제 원인 |
| 모션 코드가 **삭제된 상태** | WSL 미커밋 작업트리에서 `_write_sibom_breathe_frames()`·`_sibom_shake_offset()`·`_sibom_hold_segments()`가 제거됨. 커밋본엔 존재 → **Phase 2는 신규 구축이 아니라 복원+개선** |

### 진단 — "확 끌리지 않는" 근본 원인 4가지

1. **눈이 죽어 있었다**: 눈 반지름 17인데 몸통 rx는 150. 하이라이트 없음.
2. **🔴 30장 중 12장이 눈을 감고 있었다**: 가장 많이 쓰는 `down` 눈이 단순 아래꺾임 곡선이라 `blink`(감은 눈)와 사실상 동일하게 렌더됨. 이게 매력 부재의 최대 원인.
3. **축소 시 형태가 소멸**: 전역 외곽선 7 · 얼굴 점유율 낮음 → 쇼츠 `small`(40%)에서 표정이 안 읽힘.
4. **다리가 사실상 안 보였다**: 다리 y=392~458인데 몸통 바닥이 y=440 → 보이는 건 18px뿐.

### 적용한 변경 (`gen.py` 부품 단위 → 30장 자동 반영)

| 항목 | 변경 |
|---|---|
| 눈 | 반지름 17→27, **하이라이트 2점 추가**(`_hl`), 좌우 간격 ±50→±56 |
| **`down` 눈 재설계** | 아래꺾임 곡선 → **윗꺼풀에 덮인 큰 동공**(시무룩하되 살아있는 눈). `blink`는 idle 깜빡임 전용으로 분리 |
| `wide`·`side` | 크림 흰자+굵은 외곽선이 **물안경**처럼 보이던 것 → 큰 동공 방식으로 교체 |
| `teary`·`cry` | 눈물이 **구레나룻**처럼 옆에 붙던 것 → 물방울(`_drop`)/눈 아래 흐름으로 교체 |
| 얼굴 전체 | `FACE_SCALE=1.22` 확대 + `FACE_DY=-18` (transform 1곳으로 일괄 적용, 선 굵기도 함께 커져 축소 가독성↑) |
| 진영색 | 피치 `#C9785A`→`#E89A72`, 세이지 `#5F8F76`→`#6FB08A` (FE 토큰은 불변, **캐릭터 몸통 전용 밝은 변주**) |
| 외곽선 | 전역 7→9 |
| 팔다리 | stroke 22→28, 다리는 짧고 통통하게 바깥으로 벌림 |
| 몸통 | 정타원 → **씨앗형**(위 좁고 볼 높이가 가장 넓음) |
| 볼터치 | ±94→±106, 크기↑, 위치 아래로 |
| **팔·소품 재배치** | 팔짱·clasp·hold·phone과 소품 4종(papers/receipt/phone/bundle)을 **입 아래로 이동** — 확대된 얼굴을 가리던 문제 해결 |
| 떡잎 시그니처 | `bristle`(곤두섬) 모드 신설 + 감정→떡잎 규칙 `LEAF_RULE`을 catalog에 기록. two-argue·indignant·nagging에 적용 |
| Phase 2 준비 | catalog에 씬별 `motion` 필드(sway/shake/sob/sink/pop) + `motion_kinds` 정의 추가 |

### 검증

- 눈 12종 스와치 전수 확인 → 모든 모드가 서로 구별되고 살아 있음
- **쇼츠 축소(165px) 대비 렌더**: 기존은 표정이 뭉개져 식별 불가, 리파인본은 눈·볼터치·눈물까지 판독 가능
- 30장 전수 렌더 QA → 팔/소품이 입을 가리는 문제 발견 후 수정 완료

### 작업물 위치 (아직 배포 전)

- 리파인 생성기·SVG: 스크래치패드 `sibom/sprouts_design/` (`svg/`=리파인본, `svg_before/`=원본, `svg_a/`=중간안)
- 렌더 도구: `sibom/render.py`(콘택트시트), `cmp.py`/`ab.py`/`small.py`/`faces.py`
- 리뷰 페이지 빌더: `sibom/sprouts_design/build_compare_page.py` → `sibom/compare.html`
- **G1 리뷰**: Claude Design 기존 프로젝트에 `시봄이 리파인 1차 대비.dc.html` 신규 게시 (원본 `시봄이 10장.dc.html`은 보존)

### 배포 절차 (G1 승인 후) — ⚠️ WSL엔 SVG 렌더 도구가 없다

WSL에는 `rsvg-convert`·`inkscape`·`cairosvg`가 **모두 없다**. 기존 820×820 PNG는 다른 곳에서 렌더해 옮긴 것.
→ **SVG 생성과 PNG 렌더를 로컬에서 끝낸 뒤 결과물만 WSL로 rsync** 한다.

```
① 로컬: gen.py 실행 → svg/ + catalog.json 생성
② 로컬: rsvg-convert -w 820 -h 820 으로 png/ 렌더
③ rsync → WSL ~/Data/WaggleBot/assets/sprouts/{svg,png,catalog.json}
④ rsync → WSL ~/Data/WaggleBot/assets/sprouts_design/gen.py
⑤ AS 디자인 SSOT 동기화: docs/frontend/design/specs/sprout-character-system/{svg,catalog.json}
⑥ WSL에서 커밋 (로컬 클론은 stale이므로 커밋 금지)
```

### ✅ G1 승인 + 배포 완료 (2026-08-20)

사용자 승인: "30장 모두 확인했습니다. 시봄이 캐릭터가 훨씬 생생해졌습니다."

**배포 중 발견한 치명적 함정 — catalog 덮어쓰기 금지**

`gen.py`가 출력하던 catalog는 항목당 5개 필드짜리 **축약본**인데, 런타임 catalog는 13개 필드를 갖는다:
`categories · arc · keywords · caption · alt_captions · swap_group · sibling_bottom · people · maxChars` + 최상위 `fallback_chain`.
게다가 `presets.maxChars`가 런타임은 **10**(최근 "one-clause Sibomi cards" 커밋), 생성기는 16/12였다.
그대로 배포했으면 **장면 매칭 로직과 자막 길이 제한이 동시에 파괴**될 뻔했다 (`test_sibom_composite.py`가 `maxChars==10`을 검증 중).

→ `gen.py`를 **병합 방식으로 수정**했다. 기존 catalog를 읽어 `slot`·`motion`만 갱신하고 나머지 키는 전부 보존하며, 유실 여부를 실행 시 출력한다. `palette`는 코드에서 읽지 않는 문서용이라 새 진영색으로 갱신.

**배포 결과 (검증됨)**

| 위치 | 내용 |
|---|---|
| WSL 런타임 `~/Data/WaggleBot/assets/sprouts/` | svg 30 · png 30 (820×820 RGBA) · catalog(14필드, maxChars=10 보존) |
| WSL 생성기 `assets/sprouts_design/` | `gen.py` + `svg/`(병합 기준 catalog 포함) |
| AS SSOT `docs/frontend/design/specs/sprout-character-system/` | scene svg 30 + catalog + gen.py (face-*.svg 20장은 구스타일 리뷰용으로 잔존) |
| 백업 | WSL `assets/sprouts.bak/` (배포 전 상태) |

- 3곳 catalog **md5 동일** 확인
- WSL `pytest worker/test/test_sibom_composite.py` → **9 passed**
- PNG는 WSL에 렌더 도구가 없어 **로컬 rsvg-convert로 렌더 후 rsync**

**Phase 1 마무리 (2026-08-21)**

- `face-*.svg` 20종을 새 스타일로 재생성(`build_page.py`의 stroke 7→9도 함께 수정) — 리파인 전 스타일 잔존 문제 해소
- **catalog 병합 가드 강화**: AS에서 `gen.py`를 돌렸더니 병합 기준(`svg/catalog.json`)이 없어 **얇은 catalog가 다시 생성되는 사고**가 실제로 났다(AS 루트 권위본은 무사). → 기준을 `svg/catalog.json` → `../catalog.json` 순으로 찾고, **못 찾으면 `SystemExit`으로 중단**하도록 수정. 실행 후 매칭 메타 유실을 `assert`로 검사. 기준이 여러 곳에 있으면 전부 동일하게 갱신. 가드 동작 검증 완료.
- `gen.py` 3곳 md5 일치 확인
- AS `README.md` 갱신: 상태·결정로그(#10 stroke, #11 축소 판정 기준 신설)·색 규칙·선화 규격·부품 어휘(`blink`·`_hl`·`papers`)·Claude Design 업로드 제약·§9 리파인 로그 추가 → `npm run lint:docs` 통과

### 남은 작업

- [x] ~~G1 사용자 승인~~ → 완료, WSL·AS 반영 완료
- [ ] 승인 시: WSL `~/Data/WaggleBot/assets/sprouts_design/gen.py` 갱신 → SVG/PNG 재생성 → AS `docs/frontend/design/specs/sprout-character-system/` 동기화
- [ ] Phase 2: layout.py:207-209에 idle 시퀀스 삽입 + 삭제된 breathe/shake 복원, `_write_sibom_punch_frames` **호출 연결**(현재 미호출)
- [ ] G2 render-only 테스트 영상 (발행 금지)

---

## 7. 참조 경로

| 항목 | 경로 |
|---|---|
| 디자인 SSOT | `docs/frontend/design/specs/sprout-character-system/` (catalog.json, svg/, README.md) |
| 생성기 | `WaggleBot/assets/sprouts_design/gen.py` |
| 런타임 에셋 | `WaggleBot/assets/sprouts/` (png/svg/catalog.json) |
| 렌더 합성 | `WaggleBot/worker/ai_worker/renderer/sibom_composite.py`, `layout.py` |
| 영상 삽입 SSOT | `docs/shared/marketing/sibom-video-insertion.md` |
| 쇼츠 전략 | `docs/shared/marketing/youtube-shorts-strategy.md` |
| Claude Design | https://claude.ai/design/p/0a210407-8afc-46bd-8708-7bf106aba19d (`시봄이 10장.dc.html`) |
