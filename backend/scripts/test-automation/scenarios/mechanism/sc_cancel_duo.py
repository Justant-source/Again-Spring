# backend/scripts/test-automation/scenarios/mechanism/sc_cancel_duo.py
# 취소 메커니즘 Duo 검증: A 전송 → 0.5초 후 B 전송 → 마지막 발신자(B)에게 응답

SCENARIO_SC_CANCEL_DUO = {
    "id": "SC-CANCEL-DUO",
    "title": "Duo 취소 — A 전송 후 B 전송, B에게 최종 응답",
    "category": "mechanism",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            {"action": "send", "content": "남편이 자꾸 회사 핑계만 대요", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 1.0},
            {"action": "send", "content": "회사가 진짜 바쁜 시즌이에요", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        {"type": "response_to_user_b",
         "comment": "A 전송 후 B 전송 → LLM 취소 후 B에게 응답"},
    ],
}
