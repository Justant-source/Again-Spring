# backend/scripts/test-automation/runner/persona_bot.py
import asyncio
import time
from datetime import datetime
from aiohttp import ClientSession
from runner.auth import AuthClient
from runner.api_client import DasibomClient
from runner.verifier import ScenarioVerifier
from config import DEV_URL, DEFAULT_WAIT_AFTER_SEND

def current_iso():
    return datetime.now().isoformat(timespec='seconds')

class PersonaBot:
    def __init__(self, email: str, password: str, http_session: ClientSession,
                 cached_token: str = None):
        self.email = email
        self.password = password
        self.http = http_session
        self.auth = AuthClient(http_session)
        self.cached_token = cached_token
        self.client = None
        self.partner_session_id = None  # Duo 시나리오용

    async def login(self):
        if self.cached_token:
            self.auth.tokens[self.email] = self.cached_token
        else:
            await self.auth.login(self.email, self.password)
        self.client = DasibomClient(self.http, self.auth.auth_headers(self.email))

    async def run_scenario(self, scenario: dict, session_id: str = None,
                            invite_token: str = None) -> dict:
        result = {
            "scenario_id": scenario["id"],
            "persona": self.email,
            "events": [],
            "started_at": current_iso(),
        }

        try:
            await self.login()

            if session_id:
                result["session_id"] = session_id
            elif invite_token:
                join_result = await self.client.join_via_token(invite_token)
                result["session_id"] = join_result.get("id", "") or join_result.get("sessionId", "")
                result["events"].append({"type": "join", "at": current_iso()})
            else:
                relation_type = scenario.get("relation_type", "couple")
                category_data = scenario.get("category_data", {})
                sess = await self.client.create_session(relation_type, category_data)
                result["session_id"] = sess.get("id") or sess.get("sessionId", "")

            actions = scenario.get("messages_by_persona", {}).get(self.email, [])

            for action in actions:
                event = await self._execute_action(result["session_id"], action, result)
                if event:
                    result["events"].append(event)

            # 폴링: 마지막 send 이후 충분히 기다림
            await asyncio.sleep(DEFAULT_WAIT_AFTER_SEND)

            all_messages = await self.client.get_all_messages(result["session_id"])
            result["all_messages"] = all_messages
            result["completed_at"] = current_iso()

            # 시나리오 검증
            rules = scenario.get("verification_rules", [])
            if rules:
                verifier = ScenarioVerifier(rules)
                result["verification"] = verifier.verify(all_messages, result, scenario)

            # 세션 cleanup: 다음 시나리오가 세션 한도에 걸리지 않도록 TERMINATED 처리
            if result.get("session_id") and not scenario.get("is_duo") and not scenario.get("skip_cleanup"):
                try:
                    await self.client.admin_terminate_session(result["session_id"])
                except Exception:
                    pass

        except Exception as e:
            result["error"] = str(e)
            result["completed_at"] = current_iso()

        return result

    async def _execute_action(self, session_id: str, action: dict, result: dict):
        action_type = action.get("action")
        delay = action.get("delay_before", 0)
        if delay > 0:
            await asyncio.sleep(delay)

        at = current_iso()

        if action_type == "send":
            resp = await self.client.send_message(session_id, action["content"])
            return {"type": "send", "content": action["content"], "at": at, "resp": resp}

        elif action_type == "wait":
            await asyncio.sleep(action.get("duration", 5))
            return {"type": "wait", "duration": action.get("duration"), "at": at}

        elif action_type == "invite_partner":
            token = await self.client.create_invite_token(session_id)
            result["invite_token"] = token
            return {"type": "invite_token_created", "token": token, "at": at}

        elif action_type == "join_via_invite_token":
            token = action.get("token") or result.get("invite_token", "")
            join = await self.client.join_via_token(token)
            return {"type": "joined", "at": at, "data": join}

        elif action_type == "finalize":
            resp = await self.client.request_finalize(session_id)
            return {"type": "finalize_requested", "at": at, "resp": resp}

        elif action_type == "agree_finalize":
            resp = await self.client.agree_finalize(session_id)
            return {"type": "finalize_agreed", "at": at, "resp": resp}

        elif action_type == "poll_report":
            max_wait = action.get("max_wait", 90)
            interval = action.get("interval", 8)
            waited = 0
            while waited < max_wait:
                await asyncio.sleep(interval)
                waited += interval
                report_resp = await self.client.get_report(session_id)
                if report_resp.get("_http_status") == 200:
                    result["report"] = report_resp
                    return {"type": "report_received", "at": current_iso(), "waited_s": waited}
            result["report"] = {"_http_status": 404}
            return {"type": "report_timeout", "at": at, "waited_s": max_wait}

        elif action_type == "assert_status":
            status_resp = await self.client.get_session_status(session_id)
            actual = status_resp.get("status", "")
            expected = action.get("expected")
            result["session_status"] = actual
            passed = actual == expected
            return {"type": "assert_status", "at": at,
                    "expected": expected, "actual": actual, "passed": passed}

        elif action_type == "create_session":
            rel = action.get("relation_type", "couple")
            cat = action.get("category_data", {})
            try:
                status, body = await self.client.create_session_raw(rel, cat)
                return {"type": "create_session", "at": at,
                        "http_status": status, "body": body}
            except Exception as e:
                return {"type": "create_session", "at": at,
                        "http_status": 500, "error": str(e)}

        return None
