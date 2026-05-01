# backend/scripts/test-automation/scenarios/exception/sc17_forbidden_words.py

SCENARIO_SC17 = {
    "id": "SC17",
    "title": "금지어 포함 입력 (임상용어)",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "남자친구가 나르시시스트인 것 같아요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
