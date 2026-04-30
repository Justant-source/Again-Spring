# backend/scripts/test-automation/scenarios/exception/sc20_empty_message.py

SCENARIO_SC20 = {
    "id": "SC20",
    "title": "빈 메시지 (유효성 검증)",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "", "delay_before": 0},
            {"action": "wait", "duration": 3},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 0,
         "comment": "빈 메시지는 거부됨"},
    ]
}
