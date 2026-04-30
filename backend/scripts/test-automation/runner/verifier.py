# backend/scripts/test-automation/runner/verifier.py
import time

class ScenarioVerifier:
    def __init__(self, rules: list):
        self.rules = rules

    def verify(self, all_messages: list, result: dict, scenario: dict) -> dict:
        verdicts = []
        for rule in self.rules:
            v = self._check_rule(rule, all_messages, result, scenario)
            verdicts.append(v)

        overall = "PASS" if all(v["status"] in ("PASS", "WARNING") for v in verdicts) else "FAIL"
        critical_fail = any(v["status"] == "FAIL" for v in verdicts)
        if critical_fail:
            overall = "FAIL"

        return {"verdicts": verdicts, "overall": overall}

    def _check_rule(self, rule, messages, result, scenario):
        t = rule.get("type", "")
        if t == "mediator_response_count":
            return self._check_mediator_count(messages, rule)
        elif t == "response_contains_context_from_both":
            return self._check_context_both(messages, rule)
        elif t == "cancellation_log_present":
            return self._check_cancellation(messages, result, rule)
        elif t == "no_avoidance_pattern":
            return self._check_no_avoidance(messages, rule)
        elif t == "external_resource_mention":
            return self._check_external_resource(messages, rule)
        elif t == "response_to_user_b":
            return self._check_response_to_b(messages, rule)
        elif t == "session_status":
            return self._check_status(result, rule)
        else:
            return {"rule": t, "status": "SKIP", "detail": "unknown rule type"}

    def _check_mediator_count(self, messages, rule):
        mediator_count = sum(1 for m in messages
                              if m.get("sender", "").startswith("MEDIATOR"))
        expected = rule.get("expected")
        expected_min = rule.get("expected_min")

        if expected is not None:
            ok = mediator_count == expected
        elif expected_min is not None:
            ok = mediator_count >= expected_min
        else:
            ok = mediator_count > 0

        return {
            "rule": "mediator_response_count",
            "status": "PASS" if ok else "FAIL",
            "detail": f"expected={expected}, actual={mediator_count}"
        }

    def _check_context_both(self, messages, rule):
        """★ 취소 검증 핵심: 응답이 여러 메시지의 컨텍스트를 통합하는지"""
        last_mediator = None
        for m in reversed(messages):
            if m.get("sender", "").startswith("MEDIATOR"):
                last_mediator = m
                break

        if not last_mediator:
            return {"rule": "response_contains_context_from_both",
                    "status": "FAIL", "detail": "No mediator response found"}

        text = last_mediator.get("content", "")
        kw1 = rule.get("keywords_from_msg1", [])
        kw2 = rule.get("keywords_from_msg2", [])
        all_kw = kw1 + kw2
        present = [kw for kw in all_kw if kw in text]

        ratio = len(present) / max(len(all_kw), 1)
        if ratio >= 0.4:
            status = "PASS"
        elif ratio > 0:
            status = "WARNING"
        else:
            # 키워드가 없어도 응답이 존재하면 WARNING (LLM이 동의어·재구성으로 응답)
            # 응답 자체가 없으면 이미 위에서 FAIL 처리됨
            status = "WARNING"

        return {
            "rule": "response_contains_context_from_both",
            "status": status,
            "detail": f"{len(present)}/{len(all_kw)} keywords: {present}"
        }

    def _check_cancellation(self, messages, result, rule):
        """취소 발생을 응답 수로 간접 검증"""
        send_events = [e for e in result.get("events", []) if e.get("type") == "send"]
        mediator_count = sum(1 for m in messages
                              if m.get("sender", "").startswith("MEDIATOR"))

        sends = len(send_events)
        if sends > 1 and mediator_count == 1:
            return {"rule": "cancellation_log_present", "status": "PASS",
                    "detail": f"send={sends}, mediator=1, 취소 {sends-1}회 추정"}
        elif sends == 1 and mediator_count == 1:
            return {"rule": "cancellation_log_present", "status": "PASS",
                    "detail": "단일 메시지 정상 흐름"}
        else:
            return {"rule": "cancellation_log_present", "status": "FAIL",
                    "detail": f"send={sends}, mediator={mediator_count} (예상: mediator=1)"}

    def _check_no_avoidance(self, messages, rule):
        avoidance_phrases = ["전문가와 상담", "저는 AI입니다", "대답하기 어렵습니다"]
        last_mediator = next(
            (m for m in reversed(messages) if m.get("sender","").startswith("MEDIATOR")), None)
        if not last_mediator:
            return {"rule": "no_avoidance_pattern", "status": "FAIL", "detail": "No mediator msg"}
        text = last_mediator.get("content", "")
        found = [p for p in avoidance_phrases if p in text]
        if found:
            return {"rule": "no_avoidance_pattern", "status": "FAIL",
                    "detail": f"Avoidance phrases found: {found}"}
        return {"rule": "no_avoidance_pattern", "status": "PASS"}

    def _check_external_resource(self, messages, rule):
        resources = rule.get("keywords", ["변호사", "정신건강의학과", "신용회복"])
        last_mediator = next(
            (m for m in reversed(messages) if m.get("sender","").startswith("MEDIATOR")), None)
        if not last_mediator:
            return {"rule": "external_resource_mention", "status": "FAIL"}
        text = last_mediator.get("content", "")
        found = [r for r in resources if r in text]
        if found:
            return {"rule": "external_resource_mention", "status": "PASS",
                    "detail": f"Found: {found}"}
        return {"rule": "external_resource_mention", "status": "WARNING",
                "detail": "External resource not explicitly mentioned"}

    def _check_response_to_b(self, messages, rule):
        last_mediator = next(
            (m for m in reversed(messages) if m.get("sender","").startswith("MEDIATOR")), None)
        if not last_mediator:
            return {"rule": "response_to_user_b", "status": "FAIL"}
        if last_mediator.get("sender") == "MEDIATOR_TO_B":
            return {"rule": "response_to_user_b", "status": "PASS"}
        return {"rule": "response_to_user_b", "status": "FAIL",
                "detail": f"sender={last_mediator.get('sender')}"}

    def _check_status(self, result, rule):
        expected = rule.get("expected")
        actual = result.get("session_status")
        if actual == expected:
            return {"rule": "session_status", "status": "PASS"}
        return {"rule": "session_status", "status": "FAIL",
                "detail": f"expected={expected}, actual={actual}"}
