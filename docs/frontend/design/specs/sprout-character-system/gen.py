#!/usr/bin/env python3
"""시봄이 (Sibom) — 다시봄 캐릭터 SVG 생성기.

로컬 좌표계: 원점 = 캐릭터 가로 중심 / 떡잎 최상단 y=0. 발끝 y=458.
캔버스 820x820. 슬롯 프리셋별 안전영역에 맞춰 배치한다.
"""
from pathlib import Path

INK = "#5C4030"
CREAM = "#FFF8F0"
LEAF = "#A8C8B4"
BLUSH = "#F4A896"
# 캐릭터 몸통용 진영색 — FE 토큰(피치 #C9785A / 세이지 #5F8F76)과 같은 계열의
# 밝은 변주. 어두운 원색 위에서는 잉크 이목구비 대비가 죽어 표정이 안 읽힌다.
PEACH = "#E89A72"   # 작성자 진영
SAGE = "#6FB08A"    # 상대방 진영

# ── 얼굴 기준 좌표 ──────────────────────────────────────────────────
# 몸통 = ellipse(cy=282, ry=158). 이목구비를 몸통 중심보다 살짝 아래로 내리고
# 크게 키워야 유아도식(baby schema)이 살아난다. 쇼츠 40% 축소에서도 읽혀야 함.
EX, EY = 56, 286    # 눈 중심 (좌우 ±EX)
PUP = 27            # 기본 동공 반지름
BRW = 232           # 눈썹 기준 y
MO = 348            # 입 기준 y
BLY = 334           # 볼터치 y

# 이목구비 전체를 한 번에 키우고 내리는 배율. 부품 좌표를 일일이 고치지 않고
# face 그룹에 transform으로 적용한다 (선 굵기도 함께 커져 축소 시 가독성↑).
FACE_SCALE = 1.22
# 몸통 하단(y≈360~440)은 팔짱·소품 자리다. 얼굴이 그 위로 올라가야 입이 안 가린다.
FACE_DY = -18

OUT = Path(__file__).parent / "svg"

# ── 부품 (로컬 좌표) ────────────────────────────────────────────────

def stem():
    return '<path d="M 0 142 C -6 112, -6 90, -2 70" stroke-width="11"/>'


def leaves(mode="normal"):
    """떡잎 = 브랜드 아이덴티티이자 감정 증폭기.

    시그니처 규칙 — 감정이 떡잎에 반드시 반영된다:
      droop=시듦(소진·낙담·슬픔) · perky=쫑긋(놀람·기대·안도)
      bristle=곤두섬(분노·격앙) · normal=평상
    """
    if mode == "droop":   # 시든 떡잎 — 소진·낙담
        l = "M -4 76 C -52 58, -120 92, -132 148 C -88 156, -32 118, -4 76 Z"
        r = "M -2 70 C 52 52, 126 88, 140 144 C 94 154, 34 112, -2 70 Z"
    elif mode == "perky":  # 쫑긋 — 놀람·기대
        l = "M -4 74 C -74 -4, -148 -18, -172 26 C -136 80, -50 96, -4 74 Z"
        r = "M -2 68 C 72 -14, 156 -26, 182 20 C 140 78, 44 92, -2 68 Z"
    elif mode == "bristle":  # 곤두섬 — 분노·격앙
        # perky를 밑동 기준 40° 세우고 길이를 75%로 줄인 것.
        # 처음엔 좁고 곧게 세운 형태로 그렸다가 "토끼 귀"로 보여서 폐기했다.
        # 잎의 폭(실루엣)은 유지하고 각도만 세워야 떡잎으로 읽힌다.
        l = "M -4 74 C -6 -5, -40 -48, -74 -35 C -79 14, -39 64, -4 74 Z"
        r = "M -2 68 C 1 -15, 41 -62, 76 -48 C 80 5, 34 60, -2 68 Z"
    else:
        l = "M -4 74 C -66 10, -142 8, -166 50 C -128 98, -48 100, -4 74 Z"
        r = "M -2 68 C 66 0, 150 -4, 176 40 C 132 92, 42 96, -2 68 Z"
    return f'<path d="{l}" fill="{LEAF}"/><path d="{r}" fill="{LEAF}"/>'


def body(fill=CREAM):
    """씨앗형 몸통 — 위가 좁고 볼 높이(y≈320)에서 가장 넓다.

    완전한 타원은 어떤 캐릭터에서나 보이는 밋밋한 덩어리라 식별이 안 된다.
    위를 좁히고 볼살을 부풀리면 '새싹 씨앗 + 통통한 볼'이라는 실루엣이 생긴다.
    """
    return (f'<path d="M 0 124 C 74 124, 138 174, 146 244 '
            f'C 156 330, 118 442, 0 442 '
            f'C -118 442, -156 330, -146 244 '
            f'C -138 174, -74 124, 0 124 Z" fill="{fill}"/>')


ARM_FRONT = {"phone", "cross", "clasp", "hug", "hold"}   # 몸통 앞에 그리는 팔


def arms(mode="rest"):
    sw = ' stroke-width="28"'
    if mode == "phone":       # 짧은 두 팔이 휴대폰을 받침
        return (f'<path d="M -120 368 C -104 396, -84 412, -62 418"{sw}/>'
                f'<path d="M 120 368 C 104 396, 84 412, 62 418"{sw}/>')
    if mode == "cross":       # 팔짱 — 몸 앞에서 X로 교차 (입 아래에서)
        return (f'<path d="M -156 362 L 70 416"{sw}/>'
                f'<path d="M 156 362 L -70 416"{sw}/>')
    if mode == "limp":        # 축 늘어짐
        return (f'<path d="M -140 296 C -176 344, -184 396, -178 434"{sw}/>'
                f'<path d="M 140 296 C 176 344, 184 396, 178 434"{sw}/>')
    if mode == "up":          # 두 팔 위로 (부들부들·항의)
        return (f'<path d="M -128 290 C -176 250, -200 208, -206 172"{sw}/>'
                f'<path d="M 128 290 C 176 250, 200 208, 206 172"{sw}/>')
    if mode == "reach_r":     # 오른팔 뻗음
        return (f'<path d="M -110 312 C -152 330, -186 344, -204 362"{sw}/>'
                f'<path d="M 112 300 C 180 290, 250 288, 306 292"{sw}/>')
    if mode == "clasp":       # 두 손을 앞에 모아 쥠 (말 삼킴)
        return (f'<path d="M -114 382 C -90 410, -58 422, -26 424"{sw}/>'
                f'<path d="M 114 382 C 90 410, 58 422, 26 424"{sw}/>')
    if mode == "hug":         # 무릎·몸을 감싸 안음
        return (f'<path d="M -136 344 C -114 386, -72 406, -30 406"{sw}/>'
                f'<path d="M 136 344 C 114 386, 72 406, 30 406"{sw}/>')
    if mode == "hold":        # 두 팔로 무거운 것을 감싸 받쳐 듦
        return (f'<path d="M -128 360 C -116 396, -84 418, -46 424"{sw}/>'
                f'<path d="M 128 360 C 116 396, 84 418, 46 424"{sw}/>')
    if mode == "shrug":       # 어깨 으쓱 — 체념, 손바닥이 위로
        return (f'<path d="M -120 314 C -158 288, -172 254, -160 226"{sw}/>'
                f'<path d="M 120 314 C 158 288, 172 254, 160 226"{sw}/>')
    return (f'<path d="M -110 312 C -152 330, -186 344, -204 362"{sw}/>'
            f'<path d="M 110 312 C 152 330, 186 344, 204 362"{sw}/>')


def legs(mode="stand"):
    sw = ' stroke-width="28"'
    if mode == "sit":
        return (f'<path d="M -30 410 C -30 444, -46 456, -78 458"{sw}/>'
                f'<path d="M 30 410 C 30 444, 46 456, 78 458"{sw}/>')
    if mode == "curl":        # 웅크려 접은 다리
        return (f'<path d="M -54 426 C -82 442, -104 444, -120 438"{sw}/>'
                f'<path d="M 54 426 C 82 442, 104 444, 120 438"{sw}/>')
    if mode == "none":
        return ""
    # 몸통 바닥이 y=440이라 다리는 그 아래만 보인다. 짧고 통통하게,
    # 살짝 바깥으로 벌려야 페그(말뚝)가 아니라 발로 읽힌다.
    return (f'<path d="M -46 404 C -48 430, -52 446, -52 456"{sw}/>'
            f'<path d="M 46 404 C 48 430, 52 446, 52 456"{sw}/>')


def blush(on=True):
    if not on:
        return ""
    return (f'<ellipse cx="-106" cy="{BLY}" rx="30" ry="17" fill="{BLUSH}" stroke="none"/>'
            f'<ellipse cx="106" cy="{BLY}" rx="30" ry="17" fill="{BLUSH}" stroke="none"/>')


def brow(mode=""):
    if mode == "angry":                      # 찌푸림 — 분노·째려봄
        return (f'<path d="M -96 {BRW - 6} L -32 {BRW + 20}"/>'
                f'<path d="M 96 {BRW - 6} L 32 {BRW + 20}"/>')
    if mode == "sad":                        # 팔자 — 서운·미안
        return (f'<path d="M -96 {BRW + 20} L -32 {BRW - 6}"/>'
                f'<path d="M 96 {BRW + 20} L 32 {BRW - 6}"/>')
    if mode == "up":                         # 치켜올림 — 당황·놀람
        return (f'<path d="M -92 {BRW + 2} C -76 {BRW - 14}, -48 {BRW - 14}, '
                f'-32 {BRW}"/>'
                f'<path d="M 92 {BRW + 2} C 76 {BRW - 14}, 48 {BRW - 14}, '
                f'32 {BRW}"/>')
    return ""


def _hl(cx, cy, r=PUP):
    """동공 하이라이트 — 큰 것 + 작은 것. 눈에 생기를 넣는 핵심 요소."""
    return (f'<circle cx="{cx - r * 0.34:.0f}" cy="{cy - r * 0.40:.0f}" '
            f'r="{r * 0.34:.0f}" fill="{CREAM}" stroke="none"/>'
            f'<circle cx="{cx + r * 0.32:.0f}" cy="{cy + r * 0.36:.0f}" '
            f'r="{r * 0.15:.0f}" fill="{CREAM}" stroke="none"/>')


def _drop(cx, cy, r=13):
    """눈물방울 — 위가 뾰족하고 아래가 둥근 물방울."""
    return (f'<path d="M {cx} {cy - r * 1.7:.0f} '
            f'C {cx + r * 0.9:.0f} {cy - r * 0.4:.0f}, {cx + r} {cy + r * 0.3:.0f}, '
            f'{cx} {cy + r} '
            f'C {cx - r} {cy + r * 0.3:.0f}, {cx - r * 0.9:.0f} {cy - r * 0.4:.0f}, '
            f'{cx} {cy - r * 1.7:.0f} Z" fill="{CREAM}" stroke-width="6"/>')


def eyes(mode="dot"):
    d = ""
    if mode == "dot":
        d = (f'<circle cx="{-EX}" cy="{EY}" r="{PUP}" fill="{INK}" stroke="none"/>'
             f'<circle cx="{EX}" cy="{EY}" r="{PUP}" fill="{INK}" stroke="none"/>'
             + _hl(-EX, EY) + _hl(EX, EY))
    elif mode == "wide":       # 놀람·굳음 — 동공을 키워 눈이 커진 것으로 읽힌다.
        d = (f'<ellipse cx="{-EX}" cy="{EY - 2}" rx="31" ry="35" '
             f'fill="{INK}" stroke="none"/>'
             f'<ellipse cx="{EX}" cy="{EY - 2}" rx="31" ry="35" '
             f'fill="{INK}" stroke="none"/>'
             + _hl(-EX, EY - 4, 33) + _hl(EX, EY - 4, 33))
    elif mode == "flat":       # ㅡㅡ 어이없음
        d = (f'<path d="M {-EX - 28} {EY} L {-EX + 28} {EY}"/>'
             f'<path d="M {EX - 28} {EY} L {EX + 28} {EY}"/>')
    elif mode == "happy":      # ⌒⌒ 미소
        d = (f'<path d="M {-EX - 30} {EY + 12} C {-EX - 16} {EY - 18}, '
             f'{-EX + 16} {EY - 18}, {-EX + 30} {EY + 12}"/>'
             f'<path d="M {EX - 30} {EY + 12} C {EX - 16} {EY - 18}, '
             f'{EX + 16} {EY - 18}, {EX + 30} {EY + 12}"/>')
    elif mode == "squint":     # >< 억울·분함
        d = (f'<path d="M {-EX - 30} {EY - 22} L {-EX + 22} {EY} '
             f'L {-EX - 30} {EY + 22}"/>'
             f'<path d="M {EX + 30} {EY - 22} L {EX - 22} {EY} '
             f'L {EX + 30} {EY + 22}"/>')
    elif mode == "teary":      # 그렁그렁 — 눈물이 고였지만 아직 안 흐름
        d = (f'<circle cx="{-EX}" cy="{EY - 3}" r="30" fill="{INK}" stroke="none"/>'
             f'<circle cx="{EX}" cy="{EY - 3}" r="30" fill="{INK}" stroke="none"/>'
             + _hl(-EX, EY - 3, 30) + _hl(EX, EY - 3, 30)
             + _drop(-EX - 34, EY + 22, 13) + _drop(EX + 34, EY + 22, 13))
    elif mode == "side":       # 곁눈질 — 동공만 한쪽으로 몰아 시선을 만든다
        d = (f'<circle cx="{-EX + 17}" cy="{EY}" r="25" fill="{INK}" stroke="none"/>'
             f'<circle cx="{EX + 17}" cy="{EY}" r="25" fill="{INK}" stroke="none"/>'
             + _hl(-EX + 17, EY, 25) + _hl(EX + 17, EY, 25)
             + f'<path d="M {-EX - 30} {EY - 26} C {-EX - 14} {EY - 36}, '
               f'{-EX + 16} {EY - 34}, {-EX + 30} {EY - 26}" stroke-width="7"/>'
               f'<path d="M {EX - 30} {EY - 26} C {EX - 14} {EY - 36}, '
               f'{EX + 16} {EY - 34}, {EX + 30} {EY - 26}" stroke-width="7"/>')
    elif mode == "sleepy":     # 반쯤 감김 — 체념·무기력
        d = (f'<path d="M {-EX - 30} {EY - 12} L {-EX + 28} {EY - 12}"/>'
             f'<path d="M {-EX - 26} {EY} C {-EX - 12} {EY + 16}, '
             f'{-EX + 12} {EY + 16}, {-EX + 26} {EY}"/>'
             f'<path d="M {EX + 30} {EY - 12} L {EX - 28} {EY - 12}"/>'
             f'<path d="M {EX - 26} {EY} C {EX - 12} {EY + 16}, '
             f'{EX + 12} {EY + 16}, {EX + 26} {EY}"/>')
    elif mode == "cry":        # 눈물 터짐 — 눈 바로 아래로 흘러내린다
        d = (f'<circle cx="{-EX}" cy="{EY - 6}" r="25" fill="{INK}" stroke="none"/>'
             f'<circle cx="{EX}" cy="{EY - 6}" r="25" fill="{INK}" stroke="none"/>'
             + _hl(-EX, EY - 6, 25) + _hl(EX, EY - 6, 25)
             + f'<path d="M {-EX} {EY + 22} C {-EX - 13} {EY + 52}, '
               f'{-EX - 13} {EY + 76}, {-EX} {EY + 90} '
               f'C {-EX + 13} {EY + 76}, {-EX + 13} {EY + 52}, {-EX} {EY + 22} Z" '
               f'fill="{CREAM}" stroke-width="6"/>'
               f'<path d="M {EX} {EY + 22} C {EX - 13} {EY + 52}, '
               f'{EX - 13} {EY + 76}, {EX} {EY + 90} '
               f'C {EX + 13} {EY + 76}, {EX + 13} {EY + 52}, {EX} {EY + 22} Z" '
               f'fill="{CREAM}" stroke-width="6"/>')
    elif mode == "sparkle":    # 반짝 — 부러움·기대
        d = (f'<circle cx="{-EX}" cy="{EY - 2}" r="30" fill="{INK}" stroke="none"/>'
             f'<circle cx="{EX}" cy="{EY - 2}" r="30" fill="{INK}" stroke="none"/>'
             + _hl(-EX, EY - 2, 30) + _hl(EX, EY - 2, 30)
             + f'<circle cx="{-EX + 12}" cy="{EY - 20}" r="5" '
               f'fill="{CREAM}" stroke="none"/>'
               f'<circle cx="{EX + 12}" cy="{EY - 20}" r="5" '
               f'fill="{CREAM}" stroke="none"/>')
    elif mode == "down":       # 내리깐 눈 — 시무룩하지만 눈은 살아 있다.
        # 30장 중 가장 많이 쓰이는 눈. 단순 아래꺾임 곡선은 '눈 감음'으로 읽혀
        # 캐릭터를 죽인다. 윗꺼풀로 덮인 큰 동공으로 바꾼다.
        d = (f'<path d="M {-EX - 28} {EY - 6} C {-EX - 28} {EY + 30}, '
             f'{-EX + 28} {EY + 30}, {-EX + 28} {EY - 6} Z" '
             f'fill="{INK}" stroke="none"/>'
             f'<path d="M {EX - 28} {EY - 6} C {EX - 28} {EY + 30}, '
             f'{EX + 28} {EY + 30}, {EX + 28} {EY - 6} Z" '
             f'fill="{INK}" stroke="none"/>'
             f'<circle cx="{-EX - 9}" cy="{EY + 8}" r="8" '
             f'fill="{CREAM}" stroke="none"/>'
             f'<circle cx="{EX - 9}" cy="{EY + 8}" r="8" '
             f'fill="{CREAM}" stroke="none"/>')
    elif mode == "blink":      # 감은 눈 — idle 루프 깜빡임 프레임용
        d = (f'<path d="M {-EX - 28} {EY - 4} C {-EX - 14} {EY + 16}, '
             f'{-EX + 14} {EY + 16}, {-EX + 28} {EY - 4}"/>'
             f'<path d="M {EX - 28} {EY - 4} C {EX - 14} {EY + 16}, '
             f'{EX + 14} {EY + 16}, {EX + 28} {EY - 4}"/>')
    return d


def mouth(mode="smile"):
    if mode == "smile":
        return f'<path d="M -18 {MO - 5} C -8 {MO + 9}, 8 {MO + 9}, 18 {MO - 5}"/>'
    if mode == "flat":
        return f'<path d="M -22 {MO} L 22 {MO}"/>'
    if mode == "wavy":
        return (f'<path d="M -30 {MO} C -19 {MO - 12}, -10 {MO + 11}, 0 {MO} '
                f'C 10 {MO - 12}, 19 {MO + 11}, 30 {MO}"/>')
    if mode == "open":
        return (f'<ellipse cx="0" cy="{MO + 5}" rx="22" ry="27" '
                f'fill="{INK}" stroke="none"/>')
    if mode == "small_open":
        return (f'<ellipse cx="0" cy="{MO + 2}" rx="14" ry="17" '
                f'fill="{INK}" stroke="none"/>')
    if mode == "tight":
        return f'<path d="M -26 {MO + 2} C -14 {MO - 8}, 14 {MO - 8}, 26 {MO + 2}"/>'
    if mode == "grit":      # 이 악뭄 — 참는 중
        return (f'<path d="M -36 {MO + 2} L -18 {MO - 10} L 0 {MO + 2} '
                f'L 18 {MO - 10} L 36 {MO + 2}"/>')
    if mode == "pout":      # 삐죽
        return f'<path d="M -24 {MO + 2} C -7 {MO + 13}, 7 {MO - 10}, 24 {MO}"/>'
    if mode == "big_smile":
        return (f'<path d="M -40 {MO - 12} C -34 {MO + 24}, 34 {MO + 24}, '
                f'40 {MO - 12} Z" fill="{INK}" stroke="none"/>')
    if mode == "none":
        return ""
    return ""


def marks(mode=""):
    """감정 효과 기호."""
    if mode == "shock":
        return ('<path d="M -184 168 L -206 140"/><path d="M -146 142 L -158 108"/>'
                '<path d="M 184 168 L 206 140"/><path d="M 146 142 L 158 108"/>')
    if mode == "sweat":
        return (f'<path d="M 150 196 C 136 216, 136 234, 152 234 C 168 234, 168 216, 150 196 Z" '
                f'fill="{CREAM}"/>')
    if mode == "steam":
        return ('<path d="M -186 208 C -206 186, -192 162, -208 140" stroke-width="6"/>'
                '<path d="M 186 208 C 206 186, 192 162, 208 140" stroke-width="6"/>')
    if mode == "shake":
        return ('<path d="M -196 264 L -222 252"/><path d="M -196 300 L -222 312"/>'
                '<path d="M 196 264 L 222 252"/><path d="M 196 300 L 222 312"/>')
    if mode == "sparkle":
        return (f'<path d="M -190 146 C -184 166, -170 172, -160 172 C -172 178, -184 190, '
                f'-190 206 C -196 190, -208 178, -220 172 C -210 172, -196 166, -190 146 Z" '
                f'fill="{LEAF}"/>'
                f'<path d="M 190 146 C 196 166, 210 172, 220 172 C 208 178, 196 190, '
                f'190 206 C 184 190, 172 178, 160 172 C 170 172, 184 166, 190 146 Z" '
                f'fill="{LEAF}"/>'
                f'<path d="M 168 96 C 172 108, 180 112, 186 112 C 178 116, 172 124, '
                f'168 134 C 164 124, 158 116, 150 112 C 156 112, 164 108, 168 96 Z" '
                f'fill="{LEAF}"/>')
    if mode == "chatter":
        return (f'<ellipse cx="196" cy="238" rx="30" ry="21" fill="{CREAM}"/>'
                f'<ellipse cx="252" cy="196" rx="24" ry="17" fill="{CREAM}"/>'
                f'<ellipse cx="296" cy="160" rx="17" ry="13" fill="{CREAM}"/>')
    if mode == "flinch":
        return ('<path d="M -200 240 L -228 232"/><path d="M -202 288 L -230 296"/>')
    if mode == "quiet":
        return ('<circle cx="196" cy="250" r="7" fill="#5C4030" stroke="none"/>'
                '<circle cx="224" cy="238" r="9" fill="#5C4030" stroke="none"/>'
                '<circle cx="256" cy="222" r="11" fill="#5C4030" stroke="none"/>')
    if mode == "clock":
        return (f'<circle cx="186" cy="380" r="44" fill="{CREAM}" stroke="{INK}" stroke-width="6"/>'
                f'<path d="M 186 380 L 186 352" stroke-width="6"/>'
                f'<path d="M 186 380 L 208 390" stroke-width="6"/>')
    return ""


def prop(mode=""):
    # 소품은 모두 몸통 하단(얼굴 아래)에 놓는다 — 입을 가리면 표정이 죽는다.
    if mode == "papers":      # 서류 더미
        return (f'<rect x="-84" y="378" width="168" height="26" rx="5" fill="{CREAM}"/>'
                f'<rect x="-74" y="404" width="148" height="26" rx="5" fill="{CREAM}"/>'
                f'<rect x="-88" y="430" width="176" height="26" rx="5" fill="{CREAM}"/>')
    if mode == "receipt":     # 길게 늘어진 영수증
        return (f'<path d="M -26 388 L 26 388 L 26 502 L 13 490 L 0 502 L -13 490 L -26 502 Z" '
                f'fill="{CREAM}"/><path d="M -13 412 L 13 412"/><path d="M -13 436 L 13 436"/>'
                f'<path d="M -13 460 L 4 460"/>')
    if mode == "phone":
        return (f'<rect x="-42" y="366" width="84" height="94" rx="14" fill="{CREAM}"/>'
                f'<path d="M -20 392 L 20 392"/><path d="M -20 418 L 2 418"/>')
    if mode == "bundle":
        return (f'<path d="M -52 374 C -52 336, 52 336, 52 374 L 52 428 C 52 452, -52 452, -52 428 Z" '
                f'fill="{CREAM}"/><path d="M -34 374 L 34 374"/>')
    return ""


def sibom(*, fill=CREAM, eye="dot", mo="smile", arm="rest", leg="stand",
          leaf="normal", bl=True, mk="", pr="", back=False, face_dx=0, br=""):
    """캐릭터 1인분 (로컬 좌표)."""
    front_arm = arm in ARM_FRONT
    parts = [legs(leg), stem(), leaves(leaf)]
    if not front_arm:
        parts.insert(0, arms(arm))
    parts.append(body(fill))
    if back:
        parts.append('<path d="M 0 132 C 8 220, 8 340, 0 434" stroke-width="5"/>')
    else:
        face = blush(bl) + brow(br) + eyes(eye) + mouth(mo)
        parts.append(
            f'<g transform="translate({face_dx} {FACE_DY}) translate(0 {EY}) '
            f'scale({FACE_SCALE}) translate(0 {-EY})">{face}</g>')
    parts.append(prop(pr))
    if front_arm:
        parts.append(arms(arm))
    parts.append(marks(mk))
    return "".join(parts)


# ── 캔버스 배치 ──────────────────────────────────────────────────────

CH_H = 458  # 로컬 캐릭터 높이

SAFE = {   # preset -> (x0, y0, x1, y1) 캐릭터 안전영역
    "bottom": (24, 24, 796, 560),
    "top":    (24, 260, 796, 796),
    "bubble": (24, 180, 400, 796),
}

BUBBLE = (
    f'<path d="M 420 40 L 796 40 L 796 300 L 478 300 L 384 372 L 434 300 L 420 300 Z" '
    f'fill="{CREAM}" stroke="{INK}" stroke-width="7" stroke-linejoin="round"/>'
)


def canvas(inner, preset):
    bub = BUBBLE if preset == "bubble" else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 820" width="820" height="820">'
            f'<g fill="none" stroke="{INK}" stroke-width="9" stroke-linecap="round" '
            f'stroke-linejoin="round">{bub}{inner}</g></svg>')


def place(body_svg, preset, scale=None, cx=None, y_top=None):
    x0, y0, x1, y1 = SAFE[preset]
    s = scale if scale else (0.86 if preset == "bubble" else 1.0)
    cx = cx if cx is not None else (x0 + x1) / 2
    if y_top is None:
        y_top = y0 + ((y1 - y0) - CH_H * s) / 2
        if preset == "bubble":
            y_top = 336          # 말풍선 꼬리 끝에 머리가 닿도록
    return f'<g transform="translate({cx:.0f} {y_top:.0f}) scale({s})">{body_svg}</g>'


def duo(left, right, preset, gap=350, scale=0.74, tilt=0):
    x0, y0, x1, y1 = SAFE[preset]
    mid = (x0 + x1) / 2
    y_top = y0 + ((y1 - y0) - CH_H * scale) / 2
    rl = f' rotate({-tilt} 0 {CH_H*0.6:.0f})' if tilt else ""
    rr = f' rotate({tilt} 0 {CH_H*0.6:.0f})' if tilt else ""
    return (f'<g transform="translate({mid - gap/2:.0f} {y_top:.0f}) scale({scale}){rl}">{left}</g>'
            f'<g transform="translate({mid + gap/2:.0f} {y_top:.0f}) scale({scale}){rr}">{right}</g>')


def flip(svg):
    return f'<g transform="scale(-1 1)">{svg}</g>'


# ── 다인 배치 헬퍼 ──────────────────────────────────────────────

def row(items, preset, scale=0.66):
    """items = [(svg, cx), ...] — 캔버스 x중심 지정 배치."""
    x0, y0, x1, y1 = SAFE[preset]
    y_top = y0 + ((y1 - y0) - CH_H * scale) / 2
    return "".join(
        f'<g transform="translate({cx:.0f} {y_top:.0f}) scale({scale})">{svg}</g>'
        for svg, cx in items)


# ── 20장 ────────────────────────────────────────────────────────────

SHEET = []


def emit(name, preset, inner, meaning, trigger):
    SHEET.append(dict(id=name, slot=preset, meaning=meaning, trigger=trigger))
    OUT.mkdir(exist_ok=True)
    (OUT / f"{name}.svg").write_text(canvas(inner, preset), encoding="utf-8")


# 1. 말없이 등 돌린 냉전
emit("two-cold-backs", "bottom",
     duo(sibom(fill=PEACH, eye="down", mo="flat", arm="cross", bl=False, face_dx=-64),
         sibom(fill=SAGE, eye="down", mo="flat", arm="cross", bl=False, face_dx=64),
         "bottom", gap=420, scale=0.76, tilt=8),
     "말없이 등 돌린 냉전",
     "서로 말을 안 한 지 며칠째라고 서술하는 대목")

# 2. 정면충돌
emit("two-argue", "top",
     duo(sibom(fill=PEACH, eye="squint", mo="open", arm="up", br="angry",
               mk="steam", leaf="bristle"),
         flip(sibom(fill=SAGE, eye="squint", mo="open", arm="up", br="angry",
                    mk="steam", leaf="bristle")),
         "top", gap=400, scale=0.74),
     "정면충돌·언성이 높아진 순간",
     "그 자리에서 다투거나 언성이 높아졌다고 서술하는 대목")

# 3. 손 내밀었지만 머뭇
emit("two-hand-hesitate", "bottom",
     duo(sibom(fill=PEACH, eye="down", mo="tight", arm="reach_r", br="sad"),
         flip(sibom(fill=SAGE, eye="side", mo="flat", arm="cross")),
         "bottom", gap=372, scale=0.74),
     "화해를 시도했지만 받아들여지지 않음",
     "먼저 사과하거나 손을 내밀었는데 반응이 없었다고 말하는 대목")

# 4. 읽씹
emit("waiting-reply", "bottom",
     place(sibom(eye="down", mo="flat", arm="phone", pr="phone", bl=False,
                 leaf="droop"), "bottom"),
     "읽씹·답장 없음",
     "연락을 했는데 답이 없거나 읽고 씹혔다고 말하는 대목")

# 5. 말 삼킴
emit("swallow-words", "bubble",
     place(sibom(eye="down", mo="tight", arm="clasp", bl=False, mk="quiet"), "bubble"),
     "하고 싶은 말을 삼킴",
     "말하고 싶었지만 결국 아무 말도 못 했다고 말하는 대목")

# 6. 말문 막힘
emit("stunned", "bottom",
     place(sibom(eye="wide", mo="small_open", arm="rest", leaf="perky", bl=False,
                 br="up", mk="shock"), "bottom"),
     "어이없어 말문이 막힘",
     "상대의 말·행동에 황당하거나 말문이 막혔다고 말하는 대목")

# 7. 눈치보기
emit("side-glance", "top",
     place(sibom(eye="side", mo="flat", arm="cross", bl=False, mk="sweat"), "top"),
     "눈치를 보며 살핌",
     "분위기를 살피거나 눈치가 보였다고 말하는 대목")

# 8. 소진
emit("drained", "bottom",
     place(sibom(eye="sleepy", mo="wavy", arm="limp", leaf="droop", bl=False), "bottom"),
     "지치고 소진됨",
     "반복되는 일에 지쳤다·더는 못 하겠다고 말하는 대목")

# 9. 억울
emit("indignant", "bubble",
     place(sibom(eye="squint", mo="open", arm="up", br="angry", mk="shake",
                 leaf="bristle"), "bubble"),
     "억울하고 분함",
     "억울하다·왜 나만 그러냐고 말하는 대목")

# 10. 안도
emit("relieved", "bottom",
     place(sibom(eye="happy", mo="smile", arm="rest", leaf="perky"), "bottom"),
     "한숨 돌린 안도",
     "이야기 끝에 마음이 조금 풀렸거나 정리됐다고 말하는 대목")

# 11. 돈 문제
emit("money-trouble", "bottom",
     place(sibom(eye="flat", mo="flat", arm="hold", pr="receipt", bl=False,
                 br="angry"), "bottom"),
     "돈·계산 문제로 상한 마음",
     "돈을 빌려주고 못 받았다·계산이 불공평했다고 말하는 대목")

# 12. 배제
emit("left-out", "bottom",
     row([(sibom(eye="down", mo="flat", arm="limp", bl=False, br="sad", face_dx=-56), 150),
          (sibom(fill=SAGE, eye="happy", mo="smile", arm="rest"), 490),
          (flip(sibom(fill=SAGE, eye="happy", mo="smile", arm="rest")), 706)],
         "bottom", scale=0.56),
     "나만 빠진 자리·배제",
     "나만 부르지 않았다·단톡에서 빠졌다고 말하는 대목")

# 13. 잔소리 폭격
emit("nagging", "top",
     duo(sibom(fill=SAGE, eye="squint", mo="open", arm="rest", br="angry",
               mk="chatter", leaf="bristle"),
         flip(sibom(fill=PEACH, eye="down", mo="grit", arm="hug", bl=False, br="sad")),
         "top", gap=400, scale=0.74),
     "일방적인 잔소리·훈계",
     "상대가 계속 지적하거나 잔소리를 쏟아냈다고 말하는 대목")

# 14. 과부하
emit("overloaded", "bottom",
     place(sibom(eye="sleepy", mo="wavy", arm="hold", pr="papers", bl=False,
                 leaf="droop", mk="sweat"), "bottom"),
     "감당 못 할 만큼 떠안음",
     "일·역할이 나에게만 몰렸다고 말하는 대목")

# 15. 비교당함
emit("compared", "bottom",
     duo(sibom(fill=PEACH, eye="down", mo="pout", arm="limp", bl=False, br="sad"),
         sibom(fill=SAGE, eye="sparkle", mo="big_smile", arm="rest", mk="sparkle"),
         "bottom", gap=400, scale=0.72),
     "남과 비교당함",
     "누구는 이런데 너는 왜 그러냐는 말을 들었다고 말하는 대목")

# 16. 사과 없음
emit("no-apology", "bottom",
     duo(sibom(fill=PEACH, eye="down", mo="flat", arm="rest", br="sad"),
         sibom(fill=SAGE, eye="flat", mo="flat", arm="cross", bl=False, face_dx=64),
         "bottom", gap=400, scale=0.74),
     "끝내 사과하지 않음",
     "상대가 잘못을 인정하거나 사과하지 않았다고 말하는 대목")

# 17. 참다 터짐
emit("burst-crying", "bubble",
     place(sibom(eye="cry", mo="open", arm="limp", br="sad", leaf="droop"), "bubble"),
     "참다가 결국 터진 울음",
     "참다가 울어버렸다·눈물이 났다고 말하는 대목")

# 18. 웅크림
emit("curled-up", "bottom",
     place(sibom(eye="down", mo="flat", arm="hug", leg="curl", bl=False,
                 leaf="droop", br="sad"), "bottom"),
     "혼자 곱씹으며 웅크림",
     "그날 일이 계속 생각났다·혼자 곱씹었다고 말하는 대목")

# 19. 편들어줌
emit("comforted", "bottom",
     duo(sibom(fill=PEACH, eye="teary", mo="wavy", arm="rest", br="sad"),
         flip(sibom(fill=SAGE, eye="happy", mo="smile", arm="reach_r")),
         "bottom", gap=372, scale=0.74),
     "곁에서 편들어주고 다독임",
     "누군가 내 편을 들어주거나 위로해줬다고 말하는 대목")

# 20. 끊어내기
emit("cut-off", "top",
     place(sibom(eye="flat", mo="flat", arm="cross", bl=False, br="angry",
                 face_dx=64, leaf="perky"), "top"),
     "마음을 정하고 거리를 둠",
     "이제 그만하기로 했다·거리를 두기로 했다고 말하는 대목")



# 21. 거짓말 들킴
emit("caught-lying", "top",
     duo(sibom(fill=PEACH, eye="wide", mo="tight", arm="clasp", bl=False, br="up"),
         flip(sibom(fill=SAGE, eye="flat", mo="flat", arm="cross", bl=False, br="angry")),
         "top", gap=400, scale=0.74),
     "거짓말·숨긴 일이 들통남",
     "거짓말이나 숨긴 일이 들통났다고 말하는 대목")

# 22. 뒷담화
emit("talked-behind-back", "top",
     place(sibom(eye="side", mo="flat", arm="rest", bl=False, mk="chatter"), "top"),
     "뒤에서 이야기됨을 알게 됨",
     "누군가 내 뒷담화를 했다는 걸 알게 됐다고 말하는 대목")

# 23. 바람맞음
emit("stood-up", "bottom",
     place(sibom(eye="down", mo="flat", arm="limp", bl=False, leaf="droop", mk="clock"), "bottom"),
     "약속 장소에서 혼자 기다림",
     "약속을 어기거나 바람맞았다고 말하는 대목")

# 24. 말이 잘림·무시당함
emit("talked-over", "top",
     duo(sibom(fill=PEACH, eye="wide", mo="small_open", arm="reach_r", br="up"),
         flip(sibom(fill=SAGE, eye="flat", mo="flat", arm="cross")),
         "top", gap=372, scale=0.74),
     "말이 끊기거나 무시당함",
     "말을 하려는데 끊기거나 무시당했다고 말하는 대목")

# 25. 강요된 결정
emit("pressured-decision", "bubble",
     place(sibom(eye="flat", mo="tight", arm="shrug", bl=False, mk="sweat"), "bubble"),
     "내 의사와 상관없이 결정됨",
     "상의 없이 결정을 대신 내려버렸다고 말하는 대목")

# 26. 이별 통보
emit("breakup", "bottom",
     place(sibom(eye="teary", mo="flat", arm="limp", leaf="droop", bl=False, br="sad"), "bottom"),
     "이별을 통보받음",
     "이별을 통보받았다·헤어지자는 말을 들었다고 말하는 대목")

# 27. 독박육아
emit("solo-parenting", "bottom",
     place(sibom(eye="sleepy", mo="wavy", arm="hold", pr="bundle", bl=False,
                 leaf="droop", mk="sweat"), "bottom"),
     "육아·집안일을 혼자 떠맡음",
     "혼자 다 떠맡았다·독박을 썼다고 말하는 대목")

# 28. 양가 갈등
emit("in-law-conflict", "top",
     duo(sibom(fill=PEACH, eye="down", mo="tight", arm="rest", bl=False, br="sad"),
         flip(sibom(fill=SAGE, eye="flat", mo="flat", arm="cross", br="angry")),
         "top", gap=400, scale=0.74),
     "양가 사이에서 눈치를 봄",
     "시댁·처가 등 양가 문제로 눈치를 봤다고 말하는 대목")

# 29. 뒤늦은 후회
emit("late-regret", "bottom",
     place(sibom(eye="down", mo="tight", arm="rest", bl=False, leaf="droop", mk="quiet"), "bottom"),
     "그때 일이 계속 떠오름",
     "그때 그러지 말 걸 하고 후회했다고 말하는 대목")

# 30. 화해
emit("reconciled", "bottom",
     duo(sibom(fill=PEACH, eye="happy", mo="smile", arm="rest", leaf="perky"),
         flip(sibom(fill=SAGE, eye="happy", mo="smile", arm="rest", leaf="perky")),
         "bottom", gap=340, scale=0.76),
     "마침내 화해하고 웃음",
     "결국 화해했다·마음이 풀려 웃었다고 말하는 대목")


import json

# ── 떡잎 시그니처 규칙 (Phase 1) ─────────────────────────────────────
# 떡잎은 장식이 아니라 감정 증폭기다. 모든 씬은 감정에 맞는 떡잎 상태를 갖는다.
LEAF_RULE = {
    "droop": "시듦 — 소진·낙담·슬픔·기다림",
    "perky": "쫑긋 — 놀람·기대·안도·결심",
    "bristle": "곤두섬 — 분노·격앙",
    "normal": "평상",
}

# ── 감정별 모션 매핑 (Phase 2 애니메이션용) ─────────────────────────
# WaggleBot 렌더러가 dwell 구간에서 재생할 모션. 미지정은 sway(기본 idle).
MOTION = {
    "two-argue": "shake", "indignant": "shake", "stunned": "shake",
    "burst-crying": "sob",
    "drained": "sink", "overloaded": "sink", "solo-parenting": "sink",
    "curled-up": "sink", "late-regret": "sink", "breakup": "sink",
    "stood-up": "sink",
    "reconciled": "pop", "relieved": "pop",
}
for _it in SHEET:
    _it["motion"] = MOTION.get(_it["id"], "sway")

MOTION_KINDS = {
    "sway": "기본 idle — 숨쉬기 바운스 + 눈 깜빡임 + 떡잎 살랑임",
    "shake": "분노·충격 — 좌우 떨림 (감쇠)",
    "sob": "울음 — 세로 들썩임",
    "sink": "지침·낙담 — 아래로 처지는 느린 드리프트",
    "pop": "안도·화해 — 살짝 튀어오름",
}

# ── catalog 병합 (덮어쓰기 금지) ─────────────────────────────────────
# 🚨 런타임 catalog.json에는 이 생성기가 만들지 않는 값들이 들어 있다:
#    · 매칭 메타: categories · arc · keywords · caption · alt_captions
#                 · swap_group · sibling_bottom · people
#    · 조정된 presets: maxChars=10 (테스트가 이 값을 검증한다)
#    · fallback_chain
# 통째로 쓰면 장면 매칭과 자막 길이 제한이 파괴되므로 반드시 병합한다.
# 병합 기준은 레포마다 위치가 다르다 (런타임=svg 옆 / AS SSOT=스펙 루트).
# 기준을 못 찾으면 얇은 catalog를 쓰는 대신 **중단**한다 — 조용한 메타 유실이 최악이다.
BASES = [OUT / "catalog.json", OUT.parent / "catalog.json"]
found = [p for p in BASES if p.exists()]
if not found:
    raise SystemExit(
        "[gen.py] 중단: 병합 기준 catalog.json을 찾지 못했다.\n"
        f"  탐색: {[str(p) for p in BASES]}\n"
        "  런타임 catalog에는 categories·keywords·sibling_bottom·presets.maxChars 등\n"
        "  이 생성기가 만들지 않는 매칭 메타가 있다. 덮어쓰면 장면 매칭이 깨진다.\n"
        "  → 위 경로 중 하나에 현행 catalog.json을 두고 다시 실행할 것.")

cat = json.loads(found[0].read_text(encoding="utf-8"))
gen_by_id = {it["id"]: it for it in SHEET}

if cat.get("images"):
    for it in cat["images"]:
        g = gen_by_id.get(it["id"])
        if g:
            it["slot"] = g["slot"]        # 슬롯은 생성기가 권위 (아트 레이아웃)
            it["motion"] = g["motion"]
    known = {it["id"] for it in cat["images"]}
    cat["images"].extend(it for it in SHEET if it["id"] not in known)
else:
    cat["images"] = SHEET

cat["leaf_rule"] = LEAF_RULE
cat["motion_kinds"] = MOTION_KINDS
cat.setdefault("palette", {}).update(
    {"peach_author": PEACH, "sage_partner": SAGE,
     "cream_body": CREAM, "leaf": LEAF, "blush": BLUSH, "ink": INK})

blob = json.dumps(cat, ensure_ascii=False, indent=2)
for p in found:                      # 기존에 있던 위치 전부 동일하게 갱신
    p.write_text(blob, encoding="utf-8")

missing = [k for k in ("categories", "sibling_bottom", "caption")
           if not any(k in i for i in cat["images"])]
assert not missing, f"매칭 메타 유실: {missing}"
print(f"wrote {len(SHEET)} svg + catalog ({len(found)}곳: "
      f"{', '.join(p.parent.name + '/' for p in found)}) "
      f"images={len(cat['images'])} keys={len(cat['images'][0])} "
      f"maxChars={ {k: v['maxChars'] for k, v in cat.get('presets', {}).items()} }")
