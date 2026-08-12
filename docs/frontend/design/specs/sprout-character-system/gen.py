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
PEACH = "#C9785A"   # 작성자 진영
SAGE = "#5F8F76"    # 상대방 진영

OUT = Path(__file__).parent / "svg"

# ── 부품 (로컬 좌표) ────────────────────────────────────────────────

def stem():
    return '<path d="M 0 142 C -6 112, -6 90, -2 70" stroke-width="9"/>'


def leaves(mode="normal"):
    if mode == "droop":   # 시든 떡잎 — 소진·낙담
        l = "M -4 76 C -52 58, -120 92, -132 148 C -88 156, -32 118, -4 76 Z"
        r = "M -2 70 C 52 52, 126 88, 140 144 C 94 154, 34 112, -2 70 Z"
    elif mode == "perky":  # 쫑긋 — 놀람·기대
        l = "M -4 74 C -74 -4, -148 -18, -172 26 C -136 80, -50 96, -4 74 Z"
        r = "M -2 68 C 72 -14, 156 -26, 182 20 C 140 78, 44 92, -2 68 Z"
    else:
        l = "M -4 74 C -66 10, -142 8, -166 50 C -128 98, -48 100, -4 74 Z"
        r = "M -2 68 C 66 0, 150 -4, 176 40 C 132 92, 42 96, -2 68 Z"
    return f'<path d="{l}" fill="{LEAF}"/><path d="{r}" fill="{LEAF}"/>'


def body(fill=CREAM):
    return f'<ellipse cx="0" cy="282" rx="150" ry="158" fill="{fill}"/>'


ARM_FRONT = {"phone", "cross", "clasp", "hug", "hold"}   # 몸통 앞에 그리는 팔


def arms(mode="rest"):
    sw = ' stroke-width="22"'
    if mode == "phone":       # 짧은 두 팔이 휴대폰을 받침
        return (f'<path d="M -120 344 C -104 372, -84 388, -62 394"{sw}/>'
                f'<path d="M 120 344 C 104 372, 84 388, 62 394"{sw}/>')
    if mode == "cross":       # 팔짱 — 몸 앞에서 X로 교차
        return (f'<path d="M -158 330 L 72 386"{sw}/>'
                f'<path d="M 158 330 L -72 386"{sw}/>')
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
        return (f'<path d="M -114 358 C -90 386, -58 398, -26 400"{sw}/>'
                f'<path d="M 114 358 C 90 386, 58 398, 26 400"{sw}/>')
    if mode == "hug":         # 무릎·몸을 감싸 안음
        return (f'<path d="M -136 344 C -114 386, -72 406, -30 406"{sw}/>'
                f'<path d="M 136 344 C 114 386, 72 406, 30 406"{sw}/>')
    if mode == "hold":        # 두 팔로 무거운 것을 감싸 받쳐 듦
        return (f'<path d="M -128 336 C -116 372, -84 396, -46 402"{sw}/>'
                f'<path d="M 128 336 C 116 372, 84 396, 46 402"{sw}/>')
    if mode == "shrug":       # 어깨 으쓱 — 체념, 손바닥이 위로
        return (f'<path d="M -120 314 C -158 288, -172 254, -160 226"{sw}/>'
                f'<path d="M 120 314 C 158 288, 172 254, 160 226"{sw}/>')
    return (f'<path d="M -110 312 C -152 330, -186 344, -204 362"{sw}/>'
            f'<path d="M 110 312 C 152 330, 186 344, 204 362"{sw}/>')


def legs(mode="stand"):
    sw = ' stroke-width="22"'
    if mode == "sit":
        return (f'<path d="M -30 410 C -30 444, -46 456, -78 458"{sw}/>'
                f'<path d="M 30 410 C 30 444, 46 456, 78 458"{sw}/>')
    if mode == "curl":        # 웅크려 접은 다리
        return (f'<path d="M -54 426 C -82 442, -104 444, -120 438"{sw}/>'
                f'<path d="M 54 426 C 82 442, 104 444, 120 438"{sw}/>')
    if mode == "none":
        return ""
    return f'<path d="M -42 392 L -42 458"{sw}/><path d="M 42 392 L 42 458"{sw}/>'


def blush(on=True):
    if not on:
        return ""
    return (f'<ellipse cx="-94" cy="320" rx="27" ry="15" fill="{BLUSH}" stroke="none"/>'
            f'<ellipse cx="94" cy="320" rx="27" ry="15" fill="{BLUSH}" stroke="none"/>')


def brow(mode=""):
    if mode == "angry":                      # 찌푸림 — 분노·째려봄
        return '<path d="M -88 232 L -32 254"/><path d="M 88 232 L 32 254"/>'
    if mode == "sad":                        # 팔자 — 서운·미안
        return '<path d="M -88 254 L -32 232"/><path d="M 88 254 L 32 232"/>'
    if mode == "up":                         # 치켜올림 — 당황·놀람
        return ('<path d="M -84 228 C -70 216, -46 216, -32 226"/>'
                '<path d="M 84 228 C 70 216, 46 216, 32 226"/>')
    return ""


def eyes(mode="dot"):
    d = ""
    if mode == "dot":
        d = (f'<circle cx="-50" cy="282" r="17" fill="{INK}" stroke="none"/>'
             f'<circle cx="50" cy="282" r="17" fill="{INK}" stroke="none"/>')
    elif mode == "wide":       # 놀람·굳음
        d = (f'<circle cx="-52" cy="280" r="26" fill="{CREAM}"/>'
             f'<circle cx="52" cy="280" r="26" fill="{CREAM}"/>'
             f'<circle cx="-52" cy="284" r="13" fill="{INK}" stroke="none"/>'
             f'<circle cx="52" cy="284" r="13" fill="{INK}" stroke="none"/>')
    elif mode == "flat":       # ㅡㅡ 어이없음
        d = ('<path d="M -70 282 L -30 282"/><path d="M 30 282 L 70 282"/>')
    elif mode == "happy":      # ⌒⌒ 미소
        d = ('<path d="M -72 288 C -62 270, -38 270, -28 288"/>'
             '<path d="M 28 288 C 38 270, 62 270, 72 288"/>')
    elif mode == "squint":     # >< 억울·분함
        d = ('<path d="M -72 266 L -34 282 L -72 298"/>'
             '<path d="M 72 266 L 34 282 L 72 298"/>')
    elif mode == "teary":      # 그렁그렁
        d = (f'<circle cx="-50" cy="278" r="19" fill="{INK}" stroke="none"/>'
             f'<circle cx="50" cy="278" r="19" fill="{INK}" stroke="none"/>'
             f'<circle cx="-44" cy="272" r="6" fill="{CREAM}" stroke="none"/>'
             f'<circle cx="56" cy="272" r="6" fill="{CREAM}" stroke="none"/>'
             f'<path d="M -68 300 C -76 314, -74 326, -64 328" stroke-width="5"/>')
    elif mode == "side":       # 곁눈질
        d = (f'<ellipse cx="-52" cy="280" rx="24" ry="22" fill="{CREAM}"/>'
             f'<ellipse cx="52" cy="280" rx="24" ry="22" fill="{CREAM}"/>'
             f'<circle cx="-38" cy="282" r="12" fill="{INK}" stroke="none"/>'
             f'<circle cx="66" cy="282" r="12" fill="{INK}" stroke="none"/>')
    elif mode == "sleepy":     # 반쯤 감김 — 체념·무기력
        d = ('<path d="M -74 276 L -26 276"/>'
             '<path d="M -70 286 C -58 296, -42 296, -30 286"/>'
             '<path d="M 74 276 L 26 276"/>'
             '<path d="M 70 286 C 58 296, 42 296, 30 286"/>')
    elif mode == "cry":        # 눈물 터짐
        d = (f'<circle cx="-50" cy="276" r="18" fill="{INK}" stroke="none"/>'
             f'<circle cx="50" cy="276" r="18" fill="{INK}" stroke="none"/>'
             f'<path d="M -56 300 C -70 330, -66 360, -52 376" stroke-width="7"/>'
             f'<path d="M 56 300 C 70 330, 66 360, 52 376" stroke-width="7"/>')
    elif mode == "sparkle":    # 반짝 — 부러움·기대
        d = (f'<circle cx="-50" cy="280" r="23" fill="{INK}" stroke="none"/>'
             f'<circle cx="50" cy="280" r="23" fill="{INK}" stroke="none"/>'
             f'<circle cx="-42" cy="272" r="8" fill="{CREAM}" stroke="none"/>'
             f'<circle cx="58" cy="272" r="8" fill="{CREAM}" stroke="none"/>'
             f'<circle cx="-56" cy="290" r="4" fill="{CREAM}" stroke="none"/>'
             f'<circle cx="44" cy="290" r="4" fill="{CREAM}" stroke="none"/>')
    elif mode == "down":       # 내리깐 눈 — 낙담
        d = ('<path d="M -72 278 C -62 296, -38 296, -28 278"/>'
             '<path d="M 28 278 C 38 296, 62 296, 72 278"/>')
    return d


def mouth(mode="smile"):
    if mode == "smile":
        return '<path d="M -12 336 C -5 345, 5 345, 12 336"/>'
    if mode == "flat":
        return '<path d="M -18 338 L 18 338"/>'
    if mode == "wavy":
        return '<path d="M -26 338 C -16 328, -8 348, 0 338 C 8 328, 16 348, 26 338"/>'
    if mode == "open":
        return f'<ellipse cx="0" cy="342" rx="20" ry="26" fill="{INK}" stroke="none"/>'
    if mode == "small_open":
        return f'<ellipse cx="0" cy="340" rx="12" ry="15" fill="{INK}" stroke="none"/>'
    if mode == "tight":
        return '<path d="M -22 340 C -12 332, 12 332, 22 340"/>'
    if mode == "grit":      # 이 악뭄 — 참는 중
        return '<path d="M -32 340 L -16 330 L 0 340 L 16 330 L 32 340"/>'
    if mode == "pout":      # 삐죽
        return '<path d="M -20 340 C -6 350, 6 330, 20 338"/>'
    if mode == "big_smile":
        return f'<path d="M -36 328 C -30 360, 30 360, 36 328 Z" fill="{INK}" stroke="none"/>'
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
    if mode == "papers":      # 서류 더미
        return (f'<rect x="-84" y="352" width="168" height="28" rx="5" fill="{CREAM}"/>'
                f'<rect x="-74" y="380" width="148" height="28" rx="5" fill="{CREAM}"/>'
                f'<rect x="-88" y="408" width="176" height="28" rx="5" fill="{CREAM}"/>')
    if mode == "receipt":     # 길게 늘어진 영수증
        return (f'<path d="M -26 366 L 26 366 L 26 486 L 13 474 L 0 486 L -13 474 L -26 486 Z" '
                f'fill="{CREAM}"/><path d="M -13 392 L 13 392"/><path d="M -13 416 L 13 416"/>'
                f'<path d="M -13 440 L 4 440"/>')
    if mode == "phone":
        return (f'<rect x="-42" y="336" width="84" height="104" rx="14" fill="{CREAM}"/>'
                f'<path d="M -20 364 L 20 364"/><path d="M -20 392 L 2 392"/>')
    if mode == "bundle":
        return (f'<path d="M -52 344 C -52 306, 52 306, 52 344 L 52 400 C 52 424, -52 424, -52 400 Z" '
                f'fill="{CREAM}"/><path d="M -34 344 L 34 344"/>')
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
        parts.append(f'<g transform="translate({face_dx} 0)">{face}</g>' if face_dx else face)
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
            f'<g fill="none" stroke="{INK}" stroke-width="7" stroke-linecap="round" '
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
     duo(sibom(fill=PEACH, eye="squint", mo="open", arm="up", br="angry", mk="steam"),
         flip(sibom(fill=SAGE, eye="squint", mo="open", arm="up", br="angry", mk="steam")),
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
     place(sibom(eye="down", mo="flat", arm="phone", pr="phone", bl=False), "bottom"),
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
     place(sibom(eye="squint", mo="open", arm="up", br="angry", mk="shake"), "bubble"),
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
     duo(sibom(fill=SAGE, eye="squint", mo="open", arm="rest", br="angry", mk="chatter"),
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
(OUT / "catalog.json").write_text(
    json.dumps({"font_size": 80,
                "presets": {"bottom": {"rect": [40, 596, 740, 200], "maxChars": 16},
                            "top": {"rect": [40, 24, 740, 200], "maxChars": 16},
                            "bubble": {"rect": [420, 40, 376, 300], "maxChars": 12}},
                "images": SHEET}, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"wrote {len(SHEET)} svg + catalog.json")
