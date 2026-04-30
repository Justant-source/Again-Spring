# backend/scripts/test-automation/runner/orchestrator.py
import asyncio
import json
import os
from datetime import datetime
from pathlib import Path
import aiohttp
from runner.persona_bot import PersonaBot
from runner.verifier import ScenarioVerifier
from config import MAX_CONCURRENT, DEV_URL, DEFAULT_WAIT_AFTER_SEND

def current_iso():
    return datetime.now().isoformat(timespec='seconds')

class Orchestrator:
    def __init__(self, dev_url: str = DEV_URL, max_concurrent: int = MAX_CONCURRENT):
        self.dev_url = dev_url
        self.semaphore = asyncio.Semaphore(max_concurrent)
        self.llm_call_count = 0
        self.results = []

    async def run_all(self, runs: list[dict]) -> list[dict]:
        """
        runs: [{"scenario": {...}, "email": "...", "password": "...", ...}, ...]
        Duo runs have is_duo=True, duo_email_a, duo_email_b, duo_password_a, duo_password_b.
        """
        async with aiohttp.ClientSession() as http:
            token_cache = await self._prelogin_all(runs, http)

            tasks = []
            for run in runs:
                if run.get("is_duo"):
                    tasks.append(self._run_duo_pair(run, http, token_cache))
                else:
                    tasks.append(self._run_one_wrapped(run, http, token_cache))

            nested = await asyncio.gather(*tasks, return_exceptions=True)

            results = []
            for item in nested:
                if isinstance(item, Exception):
                    results.append({"error": str(item)})
                elif isinstance(item, list):
                    results.extend(item)
                else:
                    results.append(item)
            return results

    async def _prelogin_all(self, runs: list[dict],
                             http: aiohttp.ClientSession) -> dict:
        """Login each unique persona once. Rate limit: 5/min per IP."""
        from runner.auth import AuthClient
        token_cache: dict[str, str] = {}
        seen_order = []
        seen = set()
        for run in runs:
            for email_key in ("email", "duo_email_a", "duo_email_b"):
                email = run.get(email_key)
                if email and email not in seen:
                    seen.add(email)
                    password = run.get("password") or run.get(
                        "duo_password_a" if email_key == "duo_email_a" else "duo_password_b", "test123")
                    seen_order.append({"email": email, "password": password})

        auth = AuthClient(http)
        BATCH = 4
        for i, entry in enumerate(seen_order):
            email, password = entry["email"], entry["password"]
            for attempt in range(3):
                try:
                    token = await auth.login(email, password)
                    token_cache[email] = token
                    print(f"  [login] {email} ✓")
                    break
                except Exception as e:
                    if "RATE_LIMITED" in str(e) and attempt < 2:
                        print(f"  [login] {email} rate-limited, waiting 65s...")
                        await asyncio.sleep(65)
                    else:
                        print(f"  [login] {email} ✗ {e}")
                        break
            if (i + 1) % BATCH == 0 and (i + 1) < len(seen_order):
                print(f"  [login] batch pause 65s...")
                await asyncio.sleep(65)
        return token_cache

    async def _run_one_wrapped(self, run: dict, http: aiohttp.ClientSession,
                                token_cache: dict) -> dict:
        async with self.semaphore:
            bot = PersonaBot(run["email"], run["password"], http,
                             cached_token=token_cache.get(run["email"]))
            scenario = run["scenario"]
            session_id = run.get("session_id")
            invite_token = run.get("invite_token")
            try:
                return await bot.run_scenario(scenario, session_id=session_id,
                                              invite_token=invite_token)
            except Exception as e:
                return {"error": str(e), "scenario_id": scenario.get("id", "?"),
                        "persona": run["email"]}

    async def _run_duo_pair(self, run: dict, http: aiohttp.ClientSession,
                             token_cache: dict) -> list[dict]:
        """
        Duo 시나리오 조율:
        1. A가 세션 생성 + invite_token 발급 (순차)
        2. A 메시지 액션 + B join 및 메시지 액션 (병렬)
        3. 공유 세션의 messages 수집 + 검증
        """
        async with self.semaphore:
            scenario = run["scenario"]
            email_a = run["duo_email_a"]
            email_b = run["duo_email_b"]
            pw_a = run["duo_password_a"]
            pw_b = run["duo_password_b"]

            bot_a = PersonaBot(email_a, pw_a, http, cached_token=token_cache.get(email_a))
            bot_b = PersonaBot(email_b, pw_b, http, cached_token=token_cache.get(email_b))

            result_a = {
                "scenario_id": scenario["id"],
                "persona": email_a,
                "events": [],
                "started_at": current_iso(),
            }
            result_b = {
                "scenario_id": scenario["id"],
                "persona": email_b,
                "events": [],
                "started_at": current_iso(),
            }

            try:
                await bot_a.login()
                await bot_b.login()

                # A: 세션 생성
                relation_type = scenario.get("relation_type", "couple")
                category_data = scenario.get("category_data", {})
                sess = await bot_a.client.create_session(relation_type, category_data)
                session_id = sess.get("id") or sess.get("sessionId", "")
                result_a["session_id"] = session_id
                result_b["session_id"] = session_id

                # A: invite_token 발급
                invite_token = await bot_a.client.create_invite_token(session_id)
                result_a["invite_token"] = invite_token
                result_a["events"].append({"type": "invite_token_created",
                                           "token": invite_token, "at": current_iso()})

                # B: join (먼저 토큰으로 세션 참여)
                join_result = await bot_b.client.join_via_token(invite_token)
                joined_session_id = join_result.get("id") or join_result.get("sessionId") or session_id
                result_b["session_id"] = joined_session_id
                result_b["events"].append({"type": "join", "at": current_iso()})
                # join 후 BE가 웰컴 메시지 처리를 마칠 때까지 대기
                await asyncio.sleep(3)

                # A와 B의 나머지 액션을 병렬 실행
                # invite_partner / join_via_invite_token 액션은 이미 처리했으므로 건너뜀
                actions_a = [a for a in scenario.get("messages_by_persona", {}).get(email_a, [])
                              if a["action"] not in ("invite_partner",)]
                actions_b = [a for a in scenario.get("messages_by_persona", {}).get(email_b, [])
                              if a["action"] not in ("join_via_invite_token",)]

                async def run_a():
                    for action in actions_a:
                        event = await bot_a._execute_action(session_id, action, result_a)
                        if event:
                            result_a["events"].append(event)

                async def run_b():
                    for action in actions_b:
                        event = await bot_b._execute_action(joined_session_id, action, result_b)
                        if event:
                            result_b["events"].append(event)

                await asyncio.gather(run_a(), run_b())
                await asyncio.sleep(DEFAULT_WAIT_AFTER_SEND)

                # A와 B 각자의 시점에서 메시지 수집 후 ID 기준 병합
                msgs_a = await bot_a.client.get_all_messages(session_id)
                msgs_b = await bot_b.client.get_all_messages(joined_session_id)
                seen_ids: set = set()
                all_messages = []
                for m in (msgs_a if isinstance(msgs_a, list) else []) + \
                          (msgs_b if isinstance(msgs_b, list) else []):
                    mid = m.get("id") or m.get("content", "")[:20]
                    if mid not in seen_ids:
                        seen_ids.add(mid)
                        all_messages.append(m)
                all_messages.sort(key=lambda m: m.get("createdAt", ""))
                result_a["all_messages"] = all_messages
                result_b["all_messages"] = all_messages
                result_a["completed_at"] = current_iso()
                result_b["completed_at"] = current_iso()

                rules = scenario.get("verification_rules", [])
                if rules:
                    verifier = ScenarioVerifier(rules)
                    verification = verifier.verify(all_messages, result_a, scenario)
                    result_a["verification"] = verification
                    result_b["verification"] = verification

            except Exception as e:
                result_a["error"] = str(e)
                result_b["error"] = str(e)
                result_a["completed_at"] = current_iso()
                result_b["completed_at"] = current_iso()

            return [result_a, result_b]

    async def _run_one(self, run: dict, http: aiohttp.ClientSession,
                        token_cache: dict) -> dict:
        async with self.semaphore:
            bot = PersonaBot(run["email"], run["password"], http,
                             cached_token=token_cache.get(run["email"]))
            scenario = run["scenario"]
            session_id = run.get("session_id")
            invite_token = run.get("invite_token")
            return await bot.run_scenario(scenario, session_id=session_id,
                                            invite_token=invite_token)

    def save_results(self, results: list, output_dir: str = None):
        if not output_dir:
            ts = datetime.now().strftime("%Y-%m-%dT%H-%M-%S")
            output_dir = f"results/{ts}"

        Path(output_dir).mkdir(parents=True, exist_ok=True)

        for r in results:
            sc_id = r.get("scenario_id", "unknown")
            persona = r.get("persona", "unknown").replace("@", "_").replace(".", "_")
            fname = f"{output_dir}/{sc_id}_{persona}.json"
            with open(fname, "w", encoding="utf-8") as f:
                json.dump(r, f, ensure_ascii=False, indent=2)

        summary = self._build_summary(results)
        with open(f"{output_dir}/summary.json", "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)

        print(f"\n결과 저장: {output_dir}/summary.json")
        print(f"총 {summary['total_executions']}건 | PASS: {summary['passed']} | FAIL: {summary['failed']}")
        return output_dir

    def _build_summary(self, results: list) -> dict:
        total = len(results)
        passed = sum(1 for r in results
                      if r.get("verification", {}).get("overall") == "PASS")
        failed = sum(1 for r in results
                      if r.get("verification", {}).get("overall") == "FAIL")
        errors = sum(1 for r in results if "error" in r and "verification" not in r)

        by_category = {}
        for r in results:
            sc_id = r.get("scenario_id", "SC00")
            num = int(sc_id[2:]) if sc_id[2:].isdigit() else 0
            if 1 <= num <= 12:
                cat = "normal"
            elif 13 <= num <= 15:
                cat = "cancellation"
            else:
                cat = "exception"

            if cat not in by_category:
                by_category[cat] = {"executions": 0, "passed": 0, "failed": 0}
            by_category[cat]["executions"] += 1
            v = r.get("verification", {}).get("overall")
            if v == "PASS":
                by_category[cat]["passed"] += 1
            elif v == "FAIL":
                by_category[cat]["failed"] += 1

        return {
            "run_id": datetime.now().isoformat(timespec='seconds'),
            "total_executions": total,
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "by_scenario_category": by_category,
        }
