#!/usr/bin/env python3
# backend/scripts/test-automation/run.py
"""
Usage:
  python run.py --scenario SC13 --persona test1@again.com   # dry run
  python run.py --all --max-concurrent 5                     # 전체 실행
  python run.py --scenarios SC13,SC14,SC15                   # 취소 시나리오만
"""
import argparse
import asyncio
import sys
import os

# 경로 설정
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import DEV_URL, MAX_CONCURRENT
from personas import PERSONA_MAP, PERSONAS
from runner.orchestrator import Orchestrator

# 시나리오 임포트 — Normal
from scenarios.normal.sc01_in_law_kitchen import SCENARIO_SC01
from scenarios.normal.sc02_parenting_conflict import SCENARIO_SC02
from scenarios.normal.sc03_friend_breakup import SCENARIO_SC03
from scenarios.normal.sc04_workplace_conflict import SCENARIO_SC04
from scenarios.normal.sc05_money_conflict import SCENARIO_SC05
from scenarios.normal.sc06_career_difference import SCENARIO_SC06
from scenarios.normal.sc07_roommate_conflict import SCENARIO_SC07
from scenarios.normal.sc08_communication_issue import SCENARIO_SC08
from scenarios.normal.sc09_sibling_unfair import SCENARIO_SC09
from scenarios.normal.sc10_long_distance import SCENARIO_SC10
from scenarios.normal.sc11_friend_betrayal import SCENARIO_SC11
from scenarios.normal.sc12_boss_conflict import SCENARIO_SC12

# Cancellation
from scenarios.cancellation.sc13_two_messages_cancel import SCENARIO_SC13
from scenarios.cancellation.sc14_five_quick_messages import SCENARIO_SC14
from scenarios.cancellation.sc15_duo_cancel import SCENARIO_SC15
from scenarios.cancellation.sc23_context_integration import SCENARIO_SC23
from scenarios.cancellation.sc24_duo_isolation import SCENARIO_SC24

# Exception
from scenarios.exception.sc16_external_resource import SCENARIO_SC16
from scenarios.exception.sc17_forbidden_words import SCENARIO_SC17
from scenarios.exception.sc18_inactive_session import SCENARIO_SC18
from scenarios.exception.sc19_crisis_keyword import SCENARIO_SC19
from scenarios.exception.sc20_empty_message import SCENARIO_SC20
from scenarios.exception.sc21_very_long_message import SCENARIO_SC21
from scenarios.exception.sc22_decline_finalize import SCENARIO_SC22

ALL_SCENARIOS = {
    "SC01": SCENARIO_SC01, "SC02": SCENARIO_SC02, "SC03": SCENARIO_SC03,
    "SC04": SCENARIO_SC04, "SC05": SCENARIO_SC05, "SC06": SCENARIO_SC06,
    "SC07": SCENARIO_SC07, "SC08": SCENARIO_SC08, "SC09": SCENARIO_SC09,
    "SC10": SCENARIO_SC10, "SC11": SCENARIO_SC11, "SC12": SCENARIO_SC12,
    "SC13": SCENARIO_SC13, "SC14": SCENARIO_SC14, "SC15": SCENARIO_SC15,
    "SC16": SCENARIO_SC16, "SC17": SCENARIO_SC17, "SC18": SCENARIO_SC18,
    "SC19": SCENARIO_SC19, "SC20": SCENARIO_SC20, "SC21": SCENARIO_SC21,
    "SC22": SCENARIO_SC22, "SC23": SCENARIO_SC23, "SC24": SCENARIO_SC24,
}

# 시나리오별 실행 페르소나 매트릭스
SCENARIO_PERSONA_MAP = {
    # Normal
    "SC01": ["test1@again.com"],
    "SC02": ["test5@again.com"],
    "SC03": ["test3@again.com"],
    "SC04": ["test4@again.com"],
    "SC05": ["test6@again.com"],
    "SC06": ["test6@again.com"],
    "SC07": ["test7@again.com"],
    "SC08": ["test3@again.com"],
    "SC09": ["test8@again.com"],
    "SC10": ["test5@again.com"],
    "SC11": ["test3@again.com"],
    "SC12": ["test4@again.com"],
    # Cancellation
    "SC13": ["test1@again.com", "test2@again.com", "test10@again.com"],
    "SC14": ["test10@again.com", "test2@again.com"],
    "SC15": ["test1@again.com"],  # Duo: test1 + test2
    "SC23": ["test1@again.com"],
    "SC24": ["test1@again.com"],  # Duo: test1 + test2
    # Exception
    "SC16": ["test1@again.com"],
    "SC17": ["test1@again.com"],
    "SC18": ["test1@again.com"],
    "SC19": ["test9@again.com"],
    "SC20": ["test1@again.com"],
    "SC21": ["test1@again.com"],
    "SC22": ["test1@again.com"],
}

def build_runs(scenario_ids: list[str], persona_filter: str = None) -> list[dict]:
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
            # Duo: Orchestrator._run_duo_pair() 가 세션 조율
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
                # _prelogin_all 이 email 필드를 사용하므로 A 이메일을 기본으로 노출
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
    parser.add_argument("--scenario", help="단일 시나리오 ID (예: SC13)")
    parser.add_argument("--scenarios", help="쉼표로 구분한 시나리오 IDs")
    parser.add_argument("--all", action="store_true", help="모든 시나리오 실행")
    parser.add_argument("--persona", help="특정 페르소나만 실행")
    parser.add_argument("--max-concurrent", type=int, default=MAX_CONCURRENT)
    args = parser.parse_args()

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
