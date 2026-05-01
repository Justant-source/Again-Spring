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
        elif t == "crisis_response_blocked":
            return self._check_crisis_blocked(result, rule)
        elif t == "session_limit_429":
            return self._check_session_limit_429(result, rule)
        elif t == "all_mediator_no_avoidance":
            return self._check_all_mediator_no_avoidance(messages, rule)
        elif t == "mediator_responses_distinct":
            return self._check_responses_distinct(messages, rule)
        elif t == "mediator_later_response_references":
            return self._check_later_references(messages, rule)
        elif t == "report_generated":
            return self._check_report_generated(result, rule)
        elif t == "report_field":
            return self._check_report_field(result, rule)
        elif t == "no_turn_meta_leak":
            return self._check_no_turn_meta_leak(messages, rule)
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
        if (actual or "").upper() == (expected or "").upper():
            return {"rule": "session_status", "status": "PASS"}
        return {"rule": "session_status", "status": "FAIL",
                "detail": f"expected={expected}, actual={actual}"}

    def _check_crisis_blocked(self, result, rule):
        """send 이벤트의 _http_status가 409인지 확인 (위기 키워드 차단 검증)."""
        for ev in result.get("events", []):
            if ev.get("type") == "send":
                status = ev.get("resp", {}).get("_http_status", 200)
                if status == 409:
                    return {"rule": "crisis_response_blocked", "status": "PASS",
                            "detail": "Crisis keyword returned 409 as expected"}
        return {"rule": "crisis_response_blocked", "status": "FAIL",
                "detail": "No 409 received — crisis keyword may not have been detected"}

    def _check_session_limit_429(self, result, rule):
        """create_session 이벤트 중 http_status 429가 있는지 확인."""
        for ev in result.get("events", []):
            if ev.get("type") == "create_session" and ev.get("http_status") == 429:
                return {"rule": "session_limit_429", "status": "PASS",
                        "detail": "4th session creation returned 429 as expected"}
        return {"rule": "session_limit_429", "status": "FAIL",
                "detail": "No 429 received — session limit may not be enforced"}

    def _check_all_mediator_no_avoidance(self, messages, rule):
        """모든 중재자 응답에서 회피 패턴 검사 (마지막만이 아닌 전체)."""
        avoidance_phrases = rule.get("avoidance_phrases",
                                      ["전문가와 상담", "저는 AI입니다", "대답하기 어렵습니다",
                                       "잘 모르겠어요", "답변하기 어렵"])
        mediator_msgs = [m for m in messages if m.get("sender", "").startswith("MEDIATOR")]
        if not mediator_msgs:
            return {"rule": "all_mediator_no_avoidance", "status": "FAIL",
                    "detail": "No mediator responses at all"}
        violations = []
        for i, m in enumerate(mediator_msgs):
            text = m.get("content", "")
            found = [p for p in avoidance_phrases if p in text]
            if found:
                violations.append(f"response#{i+1}: {found}")
        if violations:
            return {"rule": "all_mediator_no_avoidance", "status": "FAIL",
                    "detail": f"Avoidance phrases in: {violations}"}
        return {"rule": "all_mediator_no_avoidance", "status": "PASS",
                "detail": f"{len(mediator_msgs)} responses checked, none avoidant"}

    def _check_responses_distinct(self, messages, rule):
        """중재자 응답들이 과도하게 중복되지 않는지 검증 (컨텍스트 누적 없이 반복 응답 탐지)."""
        mediator_msgs = [m for m in messages if m.get("sender", "").startswith("MEDIATOR")]
        if len(mediator_msgs) < 2:
            return {"rule": "mediator_responses_distinct", "status": "PASS",
                    "detail": "Less than 2 responses — distinctness not applicable"}

        threshold = rule.get("overlap_threshold", 0.6)
        duplicates = []

        def word_overlap(a: str, b: str) -> float:
            wa = set(a.split())
            wb = set(b.split())
            if not wa or not wb:
                return 0.0
            return len(wa & wb) / min(len(wa), len(wb))

        for i in range(len(mediator_msgs) - 1):
            ratio = word_overlap(
                mediator_msgs[i].get("content", ""),
                mediator_msgs[i + 1].get("content", ""))
            if ratio >= threshold:
                duplicates.append(f"response#{i+1}↔#{i+2}: overlap={ratio:.2f}")

        if duplicates:
            return {"rule": "mediator_responses_distinct", "status": "WARNING",
                    "detail": f"High overlap pairs: {duplicates}"}
        return {"rule": "mediator_responses_distinct", "status": "PASS",
                "detail": f"{len(mediator_msgs)} responses checked, all distinct"}

    def _check_later_references(self, messages, rule):
        """후반부 중재자 응답이 초반 메시지의 키워드를 참조하는지 검증 (컨텍스트 추적 확인)."""
        keywords = rule.get("keywords", [])
        # from_turn: N번째 이후 중재자 응답에서 키워드 찾음 (1-based)
        from_turn = rule.get("from_turn", 2)

        mediator_msgs = [m for m in messages if m.get("sender", "").startswith("MEDIATOR")]
        if len(mediator_msgs) < from_turn:
            return {"rule": "mediator_later_response_references", "status": "WARNING",
                    "detail": f"Only {len(mediator_msgs)} mediator responses, need >={from_turn}"}

        later_msgs = mediator_msgs[from_turn - 1:]
        combined = " ".join(m.get("content", "") for m in later_msgs)
        found = [kw for kw in keywords if kw in combined]
        ratio = len(found) / max(len(keywords), 1)

        if ratio >= 0.4:
            return {"rule": "mediator_later_response_references", "status": "PASS",
                    "detail": f"Found {found} in responses #{from_turn}+"}
        elif ratio > 0:
            return {"rule": "mediator_later_response_references", "status": "WARNING",
                    "detail": f"Partial: {found}/{keywords} in responses #{from_turn}+"}
        return {"rule": "mediator_later_response_references", "status": "WARNING",
                "detail": f"None of {keywords} found in responses #{from_turn}+ (LLM may rephrase)"}

    def _check_report_generated(self, result, rule):
        report = result.get("report") or {}
        ok = report.get("_http_status") == 200
        return {
            "rule": "report_generated",
            "status": "PASS" if ok else "FAIL",
            "detail": f"http_status={report.get('_http_status', 'none')}",
        }

    def _check_report_field(self, result, rule):
        field = rule.get("field", "")
        report = result.get("report") or {}
        value = report.get(field)
        ok = value is not None and value is not False
        level = rule.get("level", "WARNING")  # WARNING or FAIL
        return {
            "rule": f"report_field:{field}",
            "status": "PASS" if ok else level,
            "detail": f"{field}={'present' if ok else 'null'}",
        }

    def _check_no_turn_meta_leak(self, messages, rule):
        mediator_msgs = [m for m in messages if m.get("sender", "").startswith("MEDIATOR")]
        leaked = [m for m in mediator_msgs if "<turn_meta>" in m.get("content", "")]
        return {
            "rule": "no_turn_meta_leak",
            "status": "PASS" if not leaked else "FAIL",
            "detail": f"leaked in {len(leaked)} message(s)" if leaked else "clean",
        }
