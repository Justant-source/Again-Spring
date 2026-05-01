#!/usr/bin/env python3
# backend/scripts/test-automation/run.py
"""
Usage:
  python run.py --scenario SC-CANCEL-FAST                # 단일 시나리오
  python run.py --scenarios SC-CANCEL-FAST,SC-FLOW-SOLO  # 복수 시나리오
  python run.py --all                                     # 10개 전체 실행
  python run.py --all --reset                             # 전체 실행 전 test 계정 데이터 삭제
  python run.py --scenario SC-CANCEL-FAST --reset         # 단일 + 리셋

카테고리:
  mechanism/  — V1.5 취소 메커니즘 핵심 (3개, test1·test2)
  flow/       — 정상 흐름 (4개, test1·test2)
  validation/ — 검증/예외 (3개, test1·test3)
"""
import argparse
import asyncio
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import DEV_URL, MAX_CONCURRENT
from personas import PERSONA_MAP, PERSONAS
from runner.orchestrator import Orchestrator

# 시나리오 임포트 — mechanism
from scenarios.mechanism.sc_cancel_fast import SCENARIO_SC_CANCEL_FAST
from scenarios.mechanism.sc_cancel_burst import SCENARIO_SC_CANCEL_BURST
from scenarios.mechanism.sc_cancel_duo import SCENARIO_SC_CANCEL_DUO

# 시나리오 임포트 — flow
from scenarios.flow.sc_flow_solo import SCENARIO_SC_FLOW_SOLO
from scenarios.flow.sc_flow_duo_welcome import SCENARIO_SC_FLOW_DUO_WELCOME
from scenarios.flow.sc_flow_duo_chat import SCENARIO_SC_FLOW_DUO_CHAT
from scenarios.flow.sc_flow_finalize import SCENARIO_SC_FLOW_FINALIZE

# 시나리오 임포트 — validation
from scenarios.validation.sc_valid_empty import SCENARIO_SC_VALID_EMPTY
from scenarios.validation.sc_valid_crisis import SCENARIO_SC_VALID_CRISIS
from scenarios.validation.sc_valid_limit import SCENARIO_SC_VALID_LIMIT

# 시나리오 임포트 — context (멀티턴 컨텍스트 관리)
from scenarios.context.sc_ctx_solo_depth import SCENARIO_SC_CTX_SOLO_DEPTH
from scenarios.context.sc_ctx_duo_turns import SCENARIO_SC_CTX_DUO_TURNS
from scenarios.context.sc_ctx_recall import SCENARIO_SC_CTX_RECALL

# 시나리오 임포트 — flow (리포트 생성)
from scenarios.flow.sc_flow_solo_report import SCENARIO_SC_FLOW_SOLO_REPORT

ALL_SCENARIOS = {
    # mechanism — 취소 메커니즘 핵심 (페르소나 무관, test1 고정)
    "SC-CANCEL-FAST":    SCENARIO_SC_CANCEL_FAST,
    "SC-CANCEL-BURST":   SCENARIO_SC_CANCEL_BURST,
    "SC-CANCEL-DUO":     SCENARIO_SC_CANCEL_DUO,
    # flow — 정상 흐름
    "SC-FLOW-SOLO":      SCENARIO_SC_FLOW_SOLO,
    "SC-FLOW-DUO-WELCOME": SCENARIO_SC_FLOW_DUO_WELCOME,
    "SC-FLOW-DUO-CHAT":  SCENARIO_SC_FLOW_DUO_CHAT,
    "SC-FLOW-FINALIZE":  SCENARIO_SC_FLOW_FINALIZE,
    # validation — 검증/예외
    "SC-VALID-EMPTY":    SCENARIO_SC_VALID_EMPTY,
    "SC-VALID-CRISIS":   SCENARIO_SC_VALID_CRISIS,
    "SC-VALID-LIMIT":    SCENARIO_SC_VALID_LIMIT,
    # context — 멀티턴 컨텍스트 관리
    "SC-CTX-SOLO-DEPTH": SCENARIO_SC_CTX_SOLO_DEPTH,
    "SC-CTX-DUO-TURNS":  SCENARIO_SC_CTX_DUO_TURNS,
    "SC-CTX-RECALL":     SCENARIO_SC_CTX_RECALL,
    # flow — 리포트 생성
    "SC-FLOW-SOLO-REPORT": SCENARIO_SC_FLOW_SOLO_REPORT,
}

# 시나리오별 실행 페르소나 매트릭스 (최소화)
# Duo 시나리오: 첫 번째 이메일 = A, test2@again.com = B (고정)
SCENARIO_PERSONA_MAP = {
    # mechanism (페르소나 무관 — test1만)
    "SC-CANCEL-FAST":    ["test1@again.com"],
    "SC-CANCEL-BURST":   ["test1@again.com"],
    "SC-CANCEL-DUO":     ["test1@again.com"],   # Duo: test1(A) + test2(B)
    # flow
    "SC-FLOW-SOLO":      ["test1@again.com"],
    "SC-FLOW-DUO-WELCOME": ["test1@again.com"], # Duo: test1(A) + test2(B)
    "SC-FLOW-DUO-CHAT":  ["test1@again.com"],   # Duo: test1(A) + test2(B)
    "SC-FLOW-FINALIZE":  ["test1@again.com"],   # Duo: test1(A) + test2(B)
    # validation
    "SC-VALID-EMPTY":    ["test1@again.com"],
    "SC-VALID-CRISIS":   ["test3@again.com"],
    "SC-VALID-LIMIT":    ["test1@again.com"],
    # context
    "SC-CTX-SOLO-DEPTH": ["test1@again.com"],
    "SC-CTX-DUO-TURNS":  ["test1@again.com"],   # Duo: test1(A) + test2(B)
    "SC-CTX-RECALL":     ["test1@again.com"],
    # flow — 리포트 생성
    "SC-FLOW-SOLO-REPORT": ["test1@again.com"],
}


def build_runs(scenario_ids: list, persona_filter: str = None) -> list:
    runs = []
    for sc_id in scenario_ids:
        scenario = ALL_SCENARIOS.get(sc_id)
        if not scenario:
            print(f"Warning: Scenario {sc_id} not found")
            continue

        is_duo = scenario.get("is_duo", False)
        emails = SCENARIO_PERSONA_MAP.get(sc_id, [])

        if persona_filter:
            emails = [e for e in emails if e == persona_filter]

        if is_duo and len(emails) >= 1:
            a_email = emails[0]
            b_email = "test2@again.com"
            a_persona = PERSONA_MAP.get(a_email, {})
            b_persona = PERSONA_MAP.get(b_email, {})
            runs.append({
                "scenario": scenario,
                "is_duo": True,
                "duo_email_a": a_email,
                "duo_password_a": a_persona.get("password", "test123"),
                "duo_email_b": b_email,
                "duo_password_b": b_persona.get("password", "test123"),
                "email": a_email,
                "password": a_persona.get("password", "test123"),
            })
        else:
            for email in emails:
                persona = PERSONA_MAP.get(email, {})
                runs.append({
                    "scenario": scenario,
                    "email": email,
                    "password": persona.get("password", "test123"),
                })

    return runs


async def main():
    parser = argparse.ArgumentParser(description="다시봄 자동화 테스트")
    parser.add_argument("--scenario", help="단일 시나리오 ID (예: SC-CANCEL-FAST)")
    parser.add_argument("--scenarios", help="쉼표로 구분한 시나리오 IDs")
    parser.add_argument("--all", action="store_true", help="10개 시나리오 전체 실행")
    parser.add_argument("--persona", help="특정 페르소나만 실행")
    parser.add_argument("--reset", action="store_true",
                        help="실행 전 test 계정의 sessions/messages/reports 삭제")
    parser.add_argument("--max-concurrent", type=int, default=MAX_CONCURRENT)
    args = parser.parse_args()

    if args.reset:
        from runner.reset import reset_dev_test_data
        await reset_dev_test_data()

    if args.all:
        scenario_ids = list(ALL_SCENARIOS.keys())
    elif args.scenarios:
        scenario_ids = [s.strip() for s in args.scenarios.split(",")]
    elif args.scenario:
        scenario_ids = [args.scenario]
    else:
        print("Error: --scenario, --scenarios, or --all 중 하나를 지정하세요")
        sys.exit(1)

    runs = build_runs(scenario_ids, args.persona)
    print(f"실행 예정: {len(runs)}건 (max_concurrent={args.max_concurrent})")

    orch = Orchestrator(DEV_URL, args.max_concurrent)
    results = await orch.run_all(runs)
    orch.save_results(results)


if __name__ == "__main__":
    asyncio.run(main())
