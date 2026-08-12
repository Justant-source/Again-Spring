#!/usr/bin/env python3
"""시봄이 10장 리뷰 페이지(.dc.html) 빌더."""
import json, re
from pathlib import Path
import gen  # 캐릭터 부품 재사용

HERE = Path(__file__).parent
SVG = HERE / "svg"
cat = json.loads((SVG / "catalog.json").read_text(encoding="utf-8"))

CAPTIONS = {
    "caught-lying": "거짓말이 들켰다",
    "talked-behind-back": "뒷말을 들었다",
    "stood-up": "혼자 기다렸다",
    "talked-over": "말이 잘렸다",
    "pressured-decision": "대신 정해버렸다",
    "breakup": "이별을 통보받았다",
    "solo-parenting": "혼자 다 떠맡았다",
    "in-law-conflict": "양가 눈치만 봤다",
    "late-regret": "계속 생각났다",
    "reconciled": "마침내 웃었다",
    "money-trouble": "돈 얘기가 남았다",
    "left-out": "나만 몰랐다",
    "nagging": "또 시작이다",
    "overloaded": "다 내 몫이었다",
    "compared": "누구는 잘한다며",
    "no-apology": "사과는 없었다",
    "burst-crying": "결국 울었다",
    "curled-up": "밤새 곱씹었다",
    "comforted": "네 편이라고",
    "cut-off": "이제 그만하자",
    "two-cold-backs": "말 안 한 지 3일",
    "two-argue": "결국 터졌다",
    "two-hand-hesitate": "먼저 사과했는데",
    "waiting-reply": "읽씹 3일차",
    "swallow-words": "말을 삼켰다",
    "stunned": "말문이 막혔다",
    "side-glance": "눈치만 봤다",
    "drained": "이제 지쳤다",
    "indignant": "왜 나만",
    "relieved": "조금은 풀렸다",
}
ORDER = [m["id"] for m in cat["images"]]
META = {m["id"]: m for m in cat["images"]}
PRESETS = cat["presets"]

THREE_PERSON = {"left-out"}
TWO_PERSON = {"two-cold-backs", "two-argue", "two-hand-hesitate", "nagging", "compared",
              "no-apology", "comforted", "caught-lying", "talked-over", "in-law-conflict",
              "reconciled"}


def _people(iid):
    if iid in THREE_PERSON:
        return 3
    if iid in TWO_PERSON:
        return 2
    return 1


def _counter_summary(counter):
    return " · ".join(f"{v}{k}" for k, v in counter.items())


def inner_svg(path):
    s = path.read_text(encoding="utf-8")
    return re.sub(r'^<svg[^>]*>|</svg>$', '', s.strip())


def pct(v, total=820):
    return f"{v / total * 100:.3f}%"


def card(iid):
    m = META[iid]
    p = PRESETS[m["slot"]]
    x, y, w, h = p["rect"]
    cap = CAPTIONS[iid]
    over = " over" if len(cap) > p["maxChars"] else ""
    return f'''      <figure class="card" data-screen-label="{iid}">
        <div class="stage">
          <svg viewBox="0 0 820 820" class="art">{inner_svg(SVG / (iid + ".svg"))}</svg>
          <div class="slot" style="left:{pct(x)};top:{pct(y)};width:{pct(w)};height:{pct(h)}"></div>
          <div class="cap" style="left:{pct(x)};top:{pct(y)};width:{pct(w)};height:{pct(h)}">{cap}</div>
        </div>
        <figcaption>
          <div class="row"><b>{iid}</b><span class="tag t-{m['slot']}">{m['slot']} · {p['maxChars']}자</span></div>
          <p class="mean">{m['meaning']}</p>
          <p class="trig">발동조건 — {m['trigger']}</p>
          <p class="cc{over}">캡션 “{cap}” · {len(cap)}자 / {p['maxChars']}자</p>
        </figcaption>
      </figure>'''


FACES = [("dot", "smile", "", "기본"), ("happy", "smile", "", "미소"),
         ("down", "flat", "sad", "낙담"), ("wide", "small_open", "up", "놀람"),
         ("squint", "open", "angry", "분함"), ("side", "flat", "", "곁눈"),
         ("flat", "flat", "", "어이없음"), ("teary", "wavy", "sad", "울먹"),
         ("dot", "flat", "angry", "화남"), ("side", "flat", "angry", "째려봄"),
         ("sleepy", "flat", "", "체념"), ("cry", "open", "sad", "울음"),
         ("sparkle", "big_smile", "up", "부러움"), ("dot", "pout", "sad", "삐죽"),
         ("down", "grit", "angry", "참는 중"), ("dot", "big_smile", "", "활짝"),
         ("flat", "tight", "up", "결심"), ("dot", "flat", "", "무표정"),
         ("wide", "grit", "sad", "움찔"), ("happy", "big_smile", "up", "뿌듯")]


def face_strip():
    out = []
    for e, mo, br, label in FACES:
        body = gen.sibom(eye=e, mo=mo, br=br, arm="rest", bl=(e in ("dot", "happy", "sparkle")))
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 620" '
               f'width="820" height="620"><g fill="none" stroke="{gen.INK}" '
               f'stroke-width="7" stroke-linecap="round" stroke-linejoin="round">'
               f'<g transform="translate(410 40)">{body}</g></g></svg>')
        (SVG / f"face-{label}.svg").write_text(svg, encoding="utf-8")
        out.append(f'<div class="face"><svg viewBox="0 0 820 620" class="art">'
                   f'{inner_svg(SVG / f"face-{label}.svg")}</svg><span>{label}</span></div>')
    return "\n        ".join(out)


CARDS = "\n".join(card(i) for i in ORDER)

import collections
_pc = collections.Counter(_people(i) for i in ORDER)
PEOPLE_SUMMARY = " · ".join(f"{v}인 {c}" for v, c in sorted(_pc.items()))
_sc = collections.Counter(META[i]["slot"] for i in ORDER)
SLOT_SUMMARY = " · ".join(f"{k} {v}" for k, v in sorted(_sc.items()))

HTML = f'''<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <script src="./support.js"></script>
  </head>
  <body>
    <x-dc>
      <helmet data-dc-atomics>
        <style>
          body {{ margin:0; background:#FBF3EC; color:#5C4030;
                 font-family:"Helvetica Neue", Helvetica, "Apple SD Gothic Neo", sans-serif; }}
          a {{ color:#C9785A; }}
          a:hover {{ color:#5C4030; }}
          .wrap {{ max-width:1360px; margin:0 auto; padding:56px 32px 96px; }}
          .kicker {{ font-size:13px; letter-spacing:.18em; text-transform:uppercase; color:#A08670; }}
          h1 {{ font-size:44px; margin:10px 0 6px; font-weight:700; }}
          .lede {{ font-size:17px; line-height:1.7; color:#8A7A6A; max-width:64ch; margin:0 0 12px; }}
          h2 {{ font-size:15px; letter-spacing:.14em; text-transform:uppercase;
                color:#A08670; margin:56px 0 18px; font-weight:700; }}
          .specs {{ display:flex; flex-wrap:wrap; gap:10px; margin-top:22px; }}
          .chip {{ border:1.5px solid #E3D5C6; border-radius:999px; padding:7px 15px;
                   font-size:13px; background:#FFF8F0; }}
          .faces {{ display:grid; grid-template-columns:repeat(4,1fr); gap:14px; }}
          .face {{ background:#FFF8F0; border:1.5px solid #EADFD2; border-radius:14px;
                   padding:10px 6px 12px; text-align:center; }}
          .face span {{ display:block; font-size:13px; color:#A08670; margin-top:2px; }}
          .grid {{ display:grid; grid-template-columns:repeat(3,1fr); gap:26px; }}
          .card {{ margin:0; background:#FFF8F0; border:1.5px solid #EADFD2;
                   border-radius:16px; overflow:hidden; }}
          .stage {{ position:relative; aspect-ratio:1/1; background:#FBF3EC; }}
          .art {{ display:block; width:100%; height:100%; }}
          .slot {{ position:absolute; border:2px dashed #C9785A; border-radius:6px;
                   opacity:.5; pointer-events:none; }}
          .cap {{ position:absolute; display:flex; align-items:center; justify-content:center;
                  text-align:center; font-weight:700; color:#5C4030;
                  font-size:9.76cqw; line-height:1.18; letter-spacing:-.01em; }}
          .stage {{ container-type:inline-size; }}
          figcaption {{ padding:16px 18px 20px; border-top:1.5px solid #EADFD2; }}
          .row {{ display:flex; align-items:center; justify-content:space-between; gap:10px; }}
          .row b {{ font-size:15px; font-family:ui-monospace, SFMono-Regular, Menlo, monospace; }}
          .tag {{ font-size:12px; padding:4px 10px; border-radius:999px; white-space:nowrap; }}
          .t-bottom {{ background:#EFE3D6; color:#8A6B52; }}
          .t-top {{ background:#DCE9E0; color:#4A7460; }}
          .t-bubble {{ background:#F6DED6; color:#B0654A; }}
          .mean {{ margin:10px 0 4px; font-size:15px; }}
          .trig {{ margin:0 0 8px; font-size:13px; color:#8A7A6A; line-height:1.6; }}
          .cc {{ margin:0; font-size:12px; color:#A08670;
                 font-family:ui-monospace, SFMono-Regular, Menlo, monospace; }}
          .cc.over {{ color:#B0654A; font-weight:700; }}
        </style>
      </helmet>
      <div class="wrap">
        <p class="kicker">다시봄 · Again Spring</p>
        <h1>시봄이 — 1차 {len(ORDER)}장</h1>
        <p class="lede">서툴러서 갈등하고, 그래서 다시 봄이 필요한 새싹. 점선은 런타임에 캡션이 얹히는 슬롯이고,
          안의 글자는 실제 렌더가 아니라 배치 확인용 샘플입니다. PNG에는 글자를 굽지 않습니다.</p>
        <div class="specs">
          <span class="chip">820×820 · 배경 투명</span>
          <span class="chip">외곽선 #5C4030 / stroke 7·22</span>
          <span class="chip">몸통 #FFF8F0 · 떡잎 #A8C8B4</span>
          <span class="chip">진영 피치 #C9785A / 세이지 #5F8F76</span>
          <span class="chip">캡션 80px bold #5C4030</span>
          <span class="chip">{PEOPLE_SUMMARY}</span>
          <span class="chip">{SLOT_SUMMARY}</span>
        </div>

        <h2>표정 세트 {len(FACES)}종</h2>
        <div class="faces">
        {face_strip()}
        </div>

        <h2>{len(ORDER)}장</h2>
        <div class="grid">
{CARDS}
        </div>
      </div>
    </x-dc>
    <script type="text/x-dc" data-dc-script data-props='{{ "$preview": {{ "width": 1400, "height": 1000 }} }}'>
      class Component extends DCLogic {{
        renderVals() {{ return {{}}; }}
      }}
    </script>
  </body>
</html>
'''

(HERE / "시봄이 10장.dc.html").write_text(HTML, encoding="utf-8")
print("bytes:", len(HTML))
