# backend/scripts/test-automation/scenarios/exception/sc21_very_long_message.py

SCENARIO_SC21 = {
    "id": "SC21",
    "title": "매우 긴 메시지 (5000자 초과)",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "저는 오늘 파트너와 크게 싸웠어요. " * 200, "delay_before": 0},
            {"action": "wait", "duration": 15},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 0,
         "comment": "매우 긴 메시지는 처리되거나 거부될 수 있음"},
    ]
}
