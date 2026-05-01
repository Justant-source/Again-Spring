# backend/scripts/test-automation/scenarios/cancellation/sc15_duo_cancel.py

SCENARIO_SC15 = {
    "id": "SC15",
    "title": "Duo 양쪽 동시 — A 취소, B에게 응답",
    "category": "cancellation",
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
            {"action": "send", "content": "회사가 진짜 바쁜 시즌이라고요", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        # join 웰컴 메시지가 추가되므로 마지막 mediator가 B에게 가는지만 검증
        {"type": "response_to_user_b"},
    ]
}
