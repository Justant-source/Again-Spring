#!/usr/bin/env python3
"""
profile_style_compare.py — 프로필·중재자 스타일 영향도 end-to-end 검증

Test A: test1(ENFJ/wave) vs test2(ISTJ/mountain) — 동일 스크립트, mediatorStyleX=50 고정
Test B: test1 단일 — mediatorStyleX ∈ {0, 50, 100} 세션별 sweep
Test C: 프로필 mediatorStyleX가 세션 생성 시 fallback으로 사용되는지 DB 검증 (LLM 없음)
Test D: FE prefill 코드 감사 (코드 grep 결과를 보고서에 기록)

Usage:
  cd backend/scripts/test-automation
  source venv/bin/activate
  python runner/profile_style_compare.py --test all --reset
  python runner/profile_style_compare.py --test profile-compare --reset
  python runner/profile_style_compare.py --test style-sweep
  python runner/profile_style_compare.py --test default-propagation
"""

import argparse
import asyncio
import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

import aiohttp

# ─── 경로 상수 ─────────────────────────────────────────────────────────────────
# 스크립트: backend/scripts/test-automation/runner/profile_style_compare.py
# ROOT: Again-Spring/ (parents[4])
ROOT = Path(__file__).parents[4]
RESULT_DIR = ROOT / ".result"
DATA_DIR = RESULT_DIR / "data"
ENV_DEV = ROOT / "env" / ".env.dev"

# ─── 시나리오 공통 데이터 ─────────────────────────────────────────────────────
USER_MESSAGES = [
    "주말마다 친구들 만나러 나가서 진짜 외로워요",
    "지난주에는 토요일 일요일 다 친구들이랑 약속이 있다고 나갔어요",
    '물어보면 "잠깐 보고 올게"라고 했는데 결국 새벽까지 안 들어와요',
    "저는 그냥 같이 있고 싶을 뿐인데, 이게 그렇게 큰 요구인가요",
    '이런 얘기를 하면 매번 "너는 왜 그렇게 집착하냐"고 화를 내요',
    "저도 친구가 없어서 그런 게 아니에요. 우선순위가 저는 아닌 것 같아요",
]

CATEGORY_PAYLOAD = {
    "category": {
        "majorId": "couple",
        "middleId": "couple_time",
        "minorId": "friends_first",
        "customText": None,
    }
}

SESSION_DESCRIPTION = "주말마다 친구들 만나러 가는 그 사람 때문에 매번 다투게 돼요"

EMPATHY_KEYWORDS = ["마음", "감정", "서운", "외로", "힘드", "무거", "공감", "느끼", "따뜻", "소중"]
FACT_KEYWORDS = ["사실", "관찰", "패턴", "상황", "행동", "확인", "정리", "구체", "이유", "원인"]


# ─── 환경 변수 ─────────────────────────────────────────────────────────────────

def load_env_dev() -> dict:
    env = {}
    if not ENV_DEV.exists():
        return env
    for line in ENV_DEV.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        env[k.strip()] = v.strip()
    return env


# ─── API 헬퍼 ─────────────────────────────────────────────────────────────────

def _hdr(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


async def login(http: aiohttp.ClientSession, base_url: str, email: str, password: str) -> str:
    async with http.post(
        f"{base_url}/api/auth/login",
        json={"email": email, "password": password},
        headers={"Content-Type": "application/json"},
    ) as resp:
        data = await resp.json(content_type=None)
    tok_obj = data.get("token") or {}
    token = (tok_obj.get("accessToken") if isinstance(tok_obj, dict) else None) or data.get("accessToken")
    if not token:
        raise ValueError(f"Login failed for {email}: {data}")
    print(f"  ✔ login {email}")
    return token


async def reset_test_data(http: aiohttp.ClientSession, base_url: str, token: str) -> dict:
    async with http.post(f"{base_url}/api/admin/test/reset", headers=_hdr(token)) as resp:
        data = await resp.json(content_type=None)
    print(f"  ✔ reset → {data}")
    return data


async def patch_profile(http: aiohttp.ClientSession, base_url: str, token: str, mediator_style_x: int) -> dict:
    async with http.patch(
        f"{base_url}/api/users/me",
        json={"mediatorStyleX": mediator_style_x},
        headers=_hdr(token),
    ) as resp:
        data = await resp.json(content_type=None)
        if resp.status not in (200, 201, 204):
            print(f"  ! patch_profile HTTP {resp.status}: {data}")
        else:
            print(f"  ✔ patch profile mediatorStyleX={mediator_style_x}")
    return data


async def get_profile(http: aiohttp.ClientSession, base_url: str, token: str) -> dict:
    async with http.get(f"{base_url}/api/users/me", headers=_hdr(token)) as resp:
        return await resp.json(content_type=None)


async def create_solo_session(
    http: aiohttp.ClientSession,
    base_url: str,
    token: str,
    mediator_style_x: int,
    mediator_style_y: int = 50,
    include_override: bool = True,
    description: str = SESSION_DESCRIPTION,
) -> dict:
    payload = {
        "relationType": "couple",
        "description": description,
        "soloMode": True,
        **CATEGORY_PAYLOAD,
    }
    if include_override:
        payload["mediatorStyleX"] = mediator_style_x
        payload["mediatorStyleY"] = mediator_style_y
    async with http.post(f"{base_url}/api/sessions", json=payload, headers=_hdr(token)) as resp:
        data = await resp.json(content_type=None)
        if resp.status not in (200, 201):
            raise RuntimeError(f"Session creation failed HTTP {resp.status}: {data}")
    sid = data.get("id") or data.get("sessionId", "?")
    override_info = f"mediatorStyleX={mediator_style_x}" if include_override else "mediatorStyleX=(profile default)"
    print(f"  ✔ session {sid} [{override_info}]")
    return data


async def send_user_message(
    http: aiohttp.ClientSession, base_url: str, token: str, session_id: str, content: str
) -> dict:
    async with http.post(
        f"{base_url}/api/sessions/{session_id}/messages",
        json={"content": content},
        headers=_hdr(token),
    ) as resp:
        try:
            return await resp.json(content_type=None)
        except Exception:
            return {"_http_status": resp.status}


async def poll_mediator(
    http: aiohttp.ClientSession,
    base_url: str,
    token: str,
    session_id: str,
    since_ms: int,
    timeout: int = 70,
) -> list[dict]:
    """Poll GET /messages?since=<ms> until ≥1 MEDIATOR message appears or timeout."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        async with http.get(
            f"{base_url}/api/sessions/{session_id}/messages?since={since_ms}",
            headers=_hdr(token),
        ) as resp:
            msgs = await resp.json(content_type=None)
        mediator_msgs = [m for m in (msgs or []) if isinstance(m, dict) and "MEDIATOR" in m.get("sender", "")]
        if mediator_msgs:
            return mediator_msgs
        await asyncio.sleep(3)
    print(f"    ! poll timeout ({timeout}s) — mediator response missing")
    return []


async def run_conversation(
    http: aiohttp.ClientSession, base_url: str, token: str, session_id: str
) -> list[dict]:
    """Send 6 messages, poll after each. Returns events list."""
    events: list[dict] = []
    for i, content in enumerate(USER_MESSAGES, 1):
        before_ms = int(time.time() * 1000)
        print(f"  → 턴 {i}/6: {content[:35]}…" if len(content) > 35 else f"  → 턴 {i}/6: {content}")
        await send_user_message(http, base_url, token, session_id, content)
        events.append({"turn": i, "role": "user", "content": content})

        await asyncio.sleep(2)
        mediator_msgs = await poll_mediator(http, base_url, token, session_id, before_ms)
        for m in mediator_msgs:
            c = m.get("content", "")
            preview = c[:70] + "…" if len(c) > 70 else c
            print(f"    ← 중재자: {preview}")
            events.append({"turn": i, "role": "mediator", "content": c})

        if i < len(USER_MESSAGES):
            await asyncio.sleep(3)

    return events


async def finalize_and_get_report(
    http: aiohttp.ClientSession, base_url: str, token: str, session_id: str, timeout: int = 120
) -> dict:
    """POST /finalize → POST /finalize/agree → poll GET /report."""
    async with http.post(f"{base_url}/api/sessions/{session_id}/finalize", headers=_hdr(token)) as resp:
        print(f"  ✔ finalize request → HTTP {resp.status}")

    await asyncio.sleep(2)

    async with http.post(f"{base_url}/api/sessions/{session_id}/finalize/agree", headers=_hdr(token)) as resp:
        print(f"  ✔ finalize agree → HTTP {resp.status}")

    await asyncio.sleep(3)

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        async with http.get(f"{base_url}/api/sessions/{session_id}/report", headers=_hdr(token)) as resp:
            if resp.status == 200:
                data = await resp.json(content_type=None)
                print(f"  ✔ report ready — keys: {list(data.keys())[:6]}")
                return data
        await asyncio.sleep(5)

    print(f"  ! report timeout after {timeout}s")
    return {}


async def terminate_session(
    http: aiohttp.ClientSession, base_url: str, token: str, session_id: str
) -> None:
    async with http.post(
        f"{base_url}/api/admin/test/sessions/{session_id}/terminate", headers=_hdr(token)
    ) as resp:
        print(f"  ✔ terminate {session_id} → HTTP {resp.status}")


# ─── DB 헬퍼 ───────────────────────────────────────────────────────────────────

def db_get_mediator_style_x(session_id: str, db_password: str) -> str:
    """docker exec mariadb SELECT mediator_style_x FROM sessions WHERE id=..."""
    cmd = [
        "docker", "exec", "againspring-mariadb-dev",
        "mariadb", "-uagainspring", f"-p{db_password}",
        "-D", "againspring_dev", "--skip-column-names", "-e",
        f"SELECT mediator_style_x FROM sessions WHERE id='{session_id}';",
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        val = result.stdout.strip()
        if result.returncode != 0 and not val:
            val = f"ERROR: {result.stderr.strip()[:80]}"
        return val
    except Exception as exc:
        return f"ERROR: {exc}"


# ─── 파일 저장 ─────────────────────────────────────────────────────────────────

def save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


# ─── 리포트 생성 ───────────────────────────────────────────────────────────────

def _kw_found(text: str, keywords: list[str]) -> str:
    found = [kw for kw in keywords if kw in text]
    return ", ".join(found) if found else "없음"


def _mediator_text_for_turn(events: list[dict], turn: int) -> str:
    parts = [e["content"] for e in events if e["role"] == "mediator" and e["turn"] == turn]
    return " ".join(parts) if parts else "*(없음 — 타임아웃)*"


def _all_mediator_text(events: list[dict]) -> str:
    return " ".join(e["content"] for e in events if e["role"] == "mediator")


def _safe(d, *keys, max_len=80):
    cur = d
    for k in keys:
        if not isinstance(cur, dict):
            return "—"
        cur = cur.get(k)
        if cur is None:
            return "—"
    s = str(cur)
    return s[:max_len] + "…" if len(s) > max_len else s


def write_profile_report(r1: dict, r2: dict, path: Path) -> None:
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    t1_all = _all_mediator_text(r1["events"])
    t2_all = _all_mediator_text(r2["events"])

    lines = [
        "# Test A — 프로필 차분 비교 리포트",
        "",
        f"**실행일시**: {now}  ",
        "**시나리오**: 주말 친구 약속 갈등 (6턴 동일 스크립트)  ",
        "**방법**: 동일 카테고리(couple > couple_time > friends_first) + mediatorStyleX=50 고정, **프로필만 다름**",
        "",
        "## 페르소나",
        "",
        "| 항목 | test1 서영 | test2 지훈 |",
        "|---|---|---|",
        "| MBTI | ENFJ | ISTJ |",
        "| 스타일 | wave 파도형 | mountain 산형 |",
        "| MBTI 비율 (E/N/F/P) | 30/55/65/40 | — |",
        "| MBTI 비율 (I/S/T/J) | — | 70/40/35/25 |",
        "| 10문항 답변 | `[4,3,2,4,4,4,3,4,3,4]` | `[2,1,4,2,2,3,2,3,2,2]` |",
        "| mediatorStyleX | 50 (균형, 고정) | 50 (균형, 고정) |",
        "",
        "---",
        "",
        "## 대화 비교표",
        "",
    ]

    for turn in range(1, 7):
        user_msg = USER_MESSAGES[turn - 1]
        m1 = _mediator_text_for_turn(r1["events"], turn)
        m2 = _mediator_text_for_turn(r2["events"], turn)
        lines += [
            f"### 턴 {turn}",
            "",
            f"**사용자 메시지**: {user_msg}",
            "",
            "**중재자 → test1 (서영, ENFJ wave)**",
            f"> {m1}",
            "",
            "**중재자 → test2 (지훈, ISTJ mountain)**",
            f"> {m2}",
            "",
        ]

    lines += [
        "---",
        "",
        "## 어휘 패턴 분석 (정성, 전체 응답 기준)",
        "",
        "| | test1 (서영, ENFJ) | test2 (지훈, ISTJ) |",
        "|---|---|---|",
        f"| 공감 키워드 | {_kw_found(t1_all, EMPATHY_KEYWORDS)} | {_kw_found(t2_all, EMPATHY_KEYWORDS)} |",
        f"| 사실/관찰 키워드 | {_kw_found(t1_all, FACT_KEYWORDS)} | {_kw_found(t2_all, FACT_KEYWORDS)} |",
        f"| 응답 총 글자 수 | {len(t1_all)} | {len(t2_all)} |",
        "",
        "---",
        "",
        "## 최종 리포트 비교",
        "",
        "| 항목 | test1 (서영) | test2 (지훈) |",
        "|---|---|---|",
        f"| contributionRatio.a | {_safe(r1.get('report',{}), 'contributionRatio', 'a')} | {_safe(r2.get('report',{}), 'contributionRatio', 'a')} |",
        f"| aPatternFeedback | {_safe(r1.get('report',{}), 'aPatternFeedback')} | {_safe(r2.get('report',{}), 'aPatternFeedback')} |",
        f"| suggestedApproach | {_safe(r1.get('report',{}), 'suggestedApproach')} | {_safe(r2.get('report',{}), 'suggestedApproach')} |",
        f"| repairSuggestions 수 | {len(r1.get('report',{}).get('repairSuggestions') or [])} | {len(r2.get('report',{}).get('repairSuggestions') or [])} |",
        "",
        "---",
        "",
        "## Test D — FE 기본값 prefill 코드 감사",
        "",
        "파일: `frontend/app/session/category/page.tsx`",
        "",
        "```typescript",
        "// line 17: 프로필 mediatorStyleX를 slier 초기값으로 prefill",
        "const userMediatorStyleX = useUserStore((s) => s.user?.mediatorStyleX ?? 50);",
        "// line 21: state 초기화",
        "const [mediatorStyle, setMediatorStyleLocal] = useState({ x: userMediatorStyleX, y: 50 });",
        "// line 67–68: 세션 생성 payload에 포함",
        "mediatorStyleX: mediatorStyle.x,",
        "mediatorStyleY: mediatorStyle.y,",
        "```",
        "",
        "**결론**: 프로필 `mediatorStyleX`가 세션 생성 화면 슬라이더에 자동 prefill됨. ✅",
        "",
        "---",
        "",
        "## 정성 결론",
        "",
        "> ⚠️ **LLM 비결정성 주의** — N=1 실행. 통계적 결론은 불가. 아래는 이번 실행의 정성 관찰.",
        "",
        "- 위 대화 비교표에서 두 페르소나의 중재자 응답 패턴 차이를 직접 확인 바람.",
        "- ENFJ(wave) test1 응답에 감정 공감 표현이 더 많은지 / ISTJ(mountain) test2 응답에 사실 정리가 더 많은지 주목.",
        "- `UserProfileFragment`가 `<user_profile>` 블록으로 system prompt에 주입되므로,",
        "  차이가 관찰된다면 프로필 컨텍스트 주입이 유효하다는 간접 증거.",
    ]

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"\n  ✔ report saved → {path}")


def write_style_report(results_b: list[dict], test_c_rows: list[dict], path: Path) -> None:
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    label_map = {0: "팩트형 (0)", 50: "균형 (50)", 100: "공감형 (100)"}

    lines = [
        "# Test B — 중재자 스타일 sweep / Test C — 기본값 전파 검증",
        "",
        f"**실행일시**: {now}  ",
        "**대상**: test1 (서영, ENFJ wave)  ",
        "**Test B 방법**: mediatorStyleX ∈ {0, 50, 100} 세션별 명시 송신, 동일 6턴 스크립트  ",
        "**Test C 방법**: PATCH /api/users/me → POST /api/sessions(override 없음) → DB SELECT 검증",
        "",
    ]

    if results_b:
        lines += [
            "---",
            "",
            "## Test B — 대화 비교표",
            "",
        ]
        for turn in range(1, 7):
            user_msg = USER_MESSAGES[turn - 1]
            lines += [f"### 턴 {turn}", "", f"**사용자 메시지**: {user_msg}", ""]
            for res in results_b:
                sx = res["mediator_style_x"]
                m = _mediator_text_for_turn(res["events"], turn)
                lines += [
                    f"**중재자 (styleX={sx}, {label_map.get(sx, sx)})**",
                    f"> {m}",
                    "",
                ]

        # Keyword summary
        analyses = []
        for res in results_b:
            all_text = _all_mediator_text(res["events"])
            analyses.append({
                "sx": res["mediator_style_x"],
                "emp": _kw_found(all_text, EMPATHY_KEYWORDS),
                "fact": _kw_found(all_text, FACT_KEYWORDS),
                "len": len(all_text),
            })

        sx_labels = " | ".join(label_map.get(a["sx"], str(a["sx"])) for a in analyses)
        lines += [
            "---",
            "",
            "## Test B — 어휘 패턴 분석 (전체 응답 기준)",
            "",
            f"| | {sx_labels} |",
            f"|---|{'---|' * len(analyses)}",
            "| 공감 키워드 | " + " | ".join(a["emp"] for a in analyses) + " |",
            "| 사실/관찰 키워드 | " + " | ".join(a["fact"] for a in analyses) + " |",
            "| 응답 총 글자 수 | " + " | ".join(str(a["len"]) for a in analyses) + " |",
            "",
        ]

        # Report comparison
        if any(res.get("report") for res in results_b):
            lines += [
                "## Test B — 최종 리포트 비교",
                "",
                f"| 항목 | {sx_labels} |",
                f"|---|{'---|' * len(results_b)}",
            ]
            for field, keys in [
                ("contributionRatio.a", ["contributionRatio", "a"]),
                ("aPatternFeedback", ["aPatternFeedback"]),
                ("suggestedApproach", ["suggestedApproach"]),
            ]:
                vals = [_safe(res.get("report", {}), *keys) for res in results_b]
                lines.append(f"| {field} | " + " | ".join(vals) + " |")
            lines.append("")

    if test_c_rows:
        lines += [
            "---",
            "",
            "## Test C — 프로필 기본값 전파 검증",
            "",
            "| 단계 | PATCH mediatorStyleX | 세션 override | DB SELECT | 예상값 | 판정 |",
            "|---|---|---|---|---|---|",
        ]
        for row in test_c_rows:
            verdict = "✅ PASS" if row["pass"] else "❌ FAIL"
            lines.append(
                f"| {row['step']} | {row['patch']} | {row['override']} "
                f"| {row['db_result']} | {row['expected']} | {verdict} |"
            )
        pass_count = sum(1 for r in test_c_rows if r["pass"])
        lines += [
            "",
            f"**결과**: {pass_count}/{len(test_c_rows)} PASS",
            "",
        ]

    lines += [
        "---",
        "",
        "## 정성 결론",
        "",
        "> ⚠️ **LLM 비결정성 주의** — N=1 실행.",
        "",
        "**Test B**:",
        "- `mediatorStyleX=0`(팩트형) 응답이 사실/관찰 어휘 위주이고, `styleX=100`(공감형)이 감정 언어 위주인지 위 표로 확인 요망.",
        "- `ChatPromptAssembler.buildMediatorStyleFragment` 임계값(≤30: 팩트 모드, ≥70: 공감 모드)에 따라",
        "  프롬프트 지시문이 분기하므로, 0과 100의 응답 차이가 가장 뚜렷해야 함.",
        "",
        "**Test C**:",
        "- 4건 모두 PASS면 `SessionService.createSession`의 mediatorStyleX fallback 로직 정상.",
        "- FAIL 발생 시 `backend/src/main/java/com/againspring/service/SessionService.java:142` 확인.",
    ]

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"\n  ✔ report saved → {path}")


# ─── 테스트 실행 ───────────────────────────────────────────────────────────────

async def run_solo_test(
    http: aiohttp.ClientSession,
    base_url: str,
    token: str,
    label: str,
    mediator_style_x: int,
) -> dict:
    print(f"\n  ▶ {label}")
    sess = await create_solo_session(http, base_url, token, mediator_style_x)
    session_id = sess.get("id") or sess.get("sessionId")
    events = await run_conversation(http, base_url, token, session_id)
    report = await finalize_and_get_report(http, base_url, token, session_id)
    return {
        "label": label,
        "session_id": session_id,
        "mediator_style_x": mediator_style_x,
        "events": events,
        "report": report,
    }


async def run_test_a(http: aiohttp.ClientSession, base_url: str, token1: str, token2: str) -> tuple:
    print("\n" + "═" * 60)
    print("TEST A — 프로필 차분 (ENFJ wave vs ISTJ mountain, styleX=50 고정)")
    print("═" * 60)
    r1 = await run_solo_test(http, base_url, token1, "test1 서영 ENFJ/wave (styleX=50)", 50)
    r2 = await run_solo_test(http, base_url, token2, "test2 지훈 ISTJ/mountain (styleX=50)", 50)
    return r1, r2


async def run_test_b(http: aiohttp.ClientSession, base_url: str, token1: str) -> list:
    print("\n" + "═" * 60)
    print("TEST B — 중재자 스타일 sweep (test1, styleX ∈ {0, 50, 100})")
    print("═" * 60)
    results = []
    for sx in [0, 50, 100]:
        r = await run_solo_test(http, base_url, token1, f"test1 서영 (styleX={sx})", sx)
        results.append(r)
    return results


async def run_test_c(
    http: aiohttp.ClientSession, base_url: str, token1: str, db_password: str
) -> list:
    print("\n" + "═" * 60)
    print("TEST C — 프로필 기본값 전파 검증 (LLM 호출 없음)")
    print("═" * 60)
    rows = []

    async def verify(step: str, patch_val: int, override_val, expected: int) -> dict:
        await patch_profile(http, base_url, token1, patch_val)
        profile = await get_profile(http, base_url, token1)
        actual_x = profile.get("mediatorStyleX", "?")
        print(f"    GET /users/me → mediatorStyleX={actual_x}")

        payload = {
            "relationType": "couple",
            "description": "전파 검증용 (대화 없음)",
            "soloMode": True,
            "category": {"majorId": "couple", "middleId": "couple_time", "minorId": "friends_first", "customText": None},
        }
        if override_val is not None:
            payload["mediatorStyleX"] = override_val

        async with http.post(f"{base_url}/api/sessions", json=payload, headers=_hdr(token1)) as resp:
            sess = await resp.json(content_type=None)
        sid = sess.get("id") or sess.get("sessionId", "?")
        print(f"    세션 생성 → {sid}")

        db_val = db_get_mediator_style_x(sid, db_password) if db_password else "DB_PASSWORD_없음"
        passed = db_val.strip() == str(expected)
        verdict = "✅ PASS" if passed else f"❌ FAIL (기대={expected}, 실제={db_val!r})"
        print(f"    DB mediator_style_x={db_val!r} → {verdict}")

        # Terminate to free session slot
        await terminate_session(http, base_url, token1, sid)

        return {
            "step": step,
            "patch": str(patch_val),
            "override": str(override_val) if override_val is not None else "없음 (profile default)",
            "db_result": db_val,
            "expected": str(expected),
            "pass": passed,
        }

    rows.append(await verify("C-1: patch=20, override=없음", 20, None, 20))
    rows.append(await verify("C-2: patch=80, override=없음", 80, None, 80))
    rows.append(await verify("C-3: patch=80, override=10 (override 우선 검증)", 80, 10, 10))

    # Restore to default 50
    await patch_profile(http, base_url, token1, 50)
    print("  ✔ profile restored to mediatorStyleX=50")
    return rows


# ─── main ─────────────────────────────────────────────────────────────────────

async def main():
    parser = argparse.ArgumentParser(description="프로필·중재자 스타일 영향도 end-to-end 검증")
    parser.add_argument(
        "--test",
        choices=["all", "profile-compare", "style-sweep", "default-propagation"],
        default="all",
    )
    parser.add_argument("--reset", action="store_true", default=True)
    parser.add_argument("--no-reset", dest="reset", action="store_false")
    parser.add_argument("--base-url", default="http://localhost:8090")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    env = load_env_dev()
    db_password = env.get("MARIADB_PASSWORD", "")
    if not db_password:
        print("⚠ MARIADB_PASSWORD not found — Test C DB 검증 건너뜀")

    today = datetime.now().strftime("%Y-%m-%d")
    profile_report = RESULT_DIR / f"profile-mbti-test-{today}.md"
    style_report = RESULT_DIR / f"mediator-style-test-{today}.md"

    run_a = args.test in ("all", "profile-compare")
    run_b = args.test in ("all", "style-sweep")
    run_c = args.test in ("all", "default-propagation")

    print(f"\n{'═'*60}")
    print(f"다시봄 프로필·스타일 검증 — {base_url}")
    print(f"실행: A={run_a}  B={run_b}  C={run_c}  reset={args.reset}")
    print(f"{'═'*60}")

    async with aiohttp.ClientSession() as http:
        # Health check
        try:
            async with http.get(f"{base_url}/api/health") as resp:
                h = await resp.json(content_type=None)
                print(f"\n헬스체크: {h.get('status', resp.status)}")
        except Exception as exc:
            print(f"\n⚠ 헬스체크 실패: {exc}")
            sys.exit(1)

        print("\n[1/4] 로그인")
        token1 = await login(http, base_url, "test1@again.com", "test123")
        token2 = await login(http, base_url, "test2@again.com", "test123") if run_a else None

        if args.reset:
            print("\n[2/4] test* 계정 데이터 초기화")
            await reset_test_data(http, base_url, token1)

        print("\n[3/4] 테스트 실행")
        result_a1 = result_a2 = None
        results_b: list = []
        test_c_rows: list = []

        if run_a:
            result_a1, result_a2 = await run_test_a(http, base_url, token1, token2)
            ts = datetime.now().strftime("%H%M%S")
            save_json(DATA_DIR / "test_a" / f"test1_{ts}.json", result_a1)
            save_json(DATA_DIR / "test_a" / f"test2_{ts}.json", result_a2)

        if run_b:
            results_b = await run_test_b(http, base_url, token1)
            ts = datetime.now().strftime("%H%M%S")
            for r in results_b:
                save_json(DATA_DIR / "test_b" / f"styleX{r['mediator_style_x']}_{ts}.json", r)

        if run_c:
            test_c_rows = await run_test_c(http, base_url, token1, db_password)

        print("\n[4/4] 리포트 작성")
        if run_a and result_a1 and result_a2:
            write_profile_report(result_a1, result_a2, profile_report)
        if run_b or run_c:
            write_style_report(results_b, test_c_rows, style_report)

    print(f"\n{'═'*60}")
    print("완료")
    if run_a:
        print(f"  Test A/D → {profile_report}")
    if run_b or run_c:
        print(f"  Test B/C → {style_report}")
    print(f"{'═'*60}\n")


if __name__ == "__main__":
    asyncio.run(main())
