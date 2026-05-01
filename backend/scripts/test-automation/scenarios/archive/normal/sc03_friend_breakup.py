# backend/scripts/test-automation/scenarios/normal/sc03_friend_breakup.py

SCENARIO_SC03 = {
    "id": "SC03",
    "title": "친구 절교 후회 (Solo 4턴)",
    "category": "normal",
    "relation_type": "friend",
    "category_data": {"mainCategory": "친구·지인"},
    "messages_by_persona": {
        "test3@again.com": [
            {"action": "send", "content": "친한 친구랑 큰 싸움을 했고 연락이 끊겼어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "제가 너무 심한 말을 했던 것 같아서 후회돼요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
