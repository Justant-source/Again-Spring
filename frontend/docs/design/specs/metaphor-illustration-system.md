# 다시봄 메타포 일러스트 시스템

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

## 5. 현재 일러스트 목록 (12개)

| ID | 파일명 | 그룹 | 톤 | designPrompt |
|---|---|---|---|---|
| locked-mailbox | 01-locked-mailbox.svg | avoidance | neutral | 자물쇠가 달린 우체통. 수신함 슬롯·받침대 포함. 닫힌 수직 구조 강조. |
| boiling-kettle | 02-boiling-kettle.svg | tension | heavy | 여러 줄 수증기가 솟구치는 주전자. 동적인 곡선 수증기 획, 주둥이·몸체·손잡이. |
| locked-door | 03-locked-door.svg | avoidance | heavy | 자물쇠·문고리 있는 나무 문. 4분할 패널, 견고하고 닫힌 구조감. |
| too-big-umbrella | 04-too-big-umbrella.svg | protection | neutral | 캐노피가 큰 우산, 아래 작은 원(사람 실루엣). 비대칭 보호·거리감. |
| person-in-rain | 05-person-in-rain.svg | loneliness | heavy | 빗속 홀로 선 실루엣(원+사각형). 대각선 빗줄기 여러 개, 그림자 타원. |
| frozen-pond | 06-frozen-pond.svg | avoidance | heavy | 유기적 연못 위 균열 패턴. 물가 식물 2–3개, 균열선이 정적 분위기 강조. |
| cracked-window | 07-cracked-window.svg | tension | heavy | 창틀 안 방사형 균열. 십자형 프레임, 중심 균열점 원, 정밀한 갈라짐. |
| empty-chair | 08-empty-chair.svg | avoidance | heavy | 미니멀 의자 실루엣(등받이·좌석·다리). 그림자 타원, 빈 공간이 주인공. |
| overflowing-cup | 09-overflowing-cup.svg | tension | neutral | 컵에서 흘러넘치는 곡선 액체 2줄기. 잎 장식·물방울, 벅차면서 아름다운 균형. |
| rope-bridge | 10-rope-bridge.svg | hesitation | neutral | 좌우 절벽 판자 잇는 곡선 밧줄들. 나뭇가지 배경, 불안정하지만 연결된 느낌. |
| half-open-letter | 11-half-open-letter.svg | hesitation | warm | 반쯤 열린 봉투. 내용 암시선 2–3개, 열린 플랩 곡선, 잎사귀 장식 옵션. |
| two-trees-roots | 12-two-trees-roots.svg | recovery | warm | 두 나무(구름형 수관), 아래 얽히는 뿌리 곡선들. 희망적이고 연결된 구성. |

---

## 6. 갭 분석 (신규 일러스트가 필요한 자리)

현재 부족한 영역:

| 그룹 | 현재 수 | 부족한 감정/상황 |
|---|---|---|
| recovery | 1개 | 화해·대화 시작·첫 걸음 |
| protection | 1개 | 과잉 통제·감시·집착 |
| hesitation | 2개 | 용기·작은 시도·반쯤 열린 마음 (행동판) |
| loneliness | 1개 | 군중 속 외로움·존재감 없음 |

**우선 추천 신규 주제** (번호는 파일명 번호 기준):
- `13-first-step.svg` — recovery: 첫 발자국 하나 (용기 내어 내딛은 첫 걸음)
- `14-glass-wall.svg` — avoidance: 유리벽 사이 두 사람 (눈은 마주치지만 닿지 않는)
- `15-empty-table.svg` — loneliness: 두 자리 중 하나만 채워진 식탁
- `16-seed-in-hand.svg` — recovery: 손바닥 위 씨앗 (새로운 시작)
- `17-open-window.svg` — recovery: 활짝 열린 창문 (통함·환기)

---

## 7. Claude Design 요청 템플릿

아래 3-Block을 복사해 Claude Design 채팅에 붙여넣으세요.

---

### Block 1 — 공통 컨텍스트 (매번 필수 포함)

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

### Block 2 — 스타일 레퍼런스 (기존 SVG 코드 1개 붙여넣기)

```
기존 일러스트 코드를 참고해서 같은 스타일로 만들어줘:

[11-half-open-letter.svg 코드 전체 붙여넣기]
```

### Block 3 — 이번 요청 명세

```
새로 만들어줄 일러스트:
- 파일명: 13-first-step.svg
- 주제: 용기 내어 내딛은 첫 발자국
- group: recovery
- 표현하고 싶은 감정: 조심스러운 용기, 작은 희망
- 관련 욕구: 연결, 성장, 시작
- 톤: warm (포근하게, 무겁지 않게)
- 색: #FFF8F0 + #A08670 + #A8C8B4 (세이지 그린, 희망)
- 특이사항: 발자국은 하나만, 너무 귀엽지 않게
```

---

## 8. 검수 체크리스트 (Claude Design 결과물 받은 후)

- [ ] viewBox="0 0 240 240" 확인
- [ ] 색이 3색 이하이며 팔레트 값 정확한지 확인
- [ ] 그라데이션/텍스트/emoji 없는지 확인
- [ ] 중심 오브젝트가 너무 작지 않은지 (40% 이상)
- [ ] `frontend/public/illustrations/metaphors/` 에 저장
- [ ] `frontend/lib/constants/metaphors.ts` METAPHORS 배열에 등록 (모든 필드 필수)
- [ ] `frontend/docs/design/specs/metaphor-illustration-system.md` 현재 목록 테이블 업데이트
- [ ] `npm run build` 통과
- [ ] `npm run lint:emoji` 통과
