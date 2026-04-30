# backend/scripts/test-automation/scenarios/exception/sc22_decline_finalize.py

SCENARIO_SC22 = {
    "id": "SC22",
    "title": "종료 권유 거부 후 계속 채팅",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "남편이랑 많이 이야기가 됐어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "아직 더 이야기하고 싶어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
