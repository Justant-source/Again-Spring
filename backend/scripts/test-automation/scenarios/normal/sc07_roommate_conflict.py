# backend/scripts/test-automation/scenarios/normal/sc07_roommate_conflict.py

SCENARIO_SC07 = {
    "id": "SC07",
    "title": "룸메이트 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "friend",
    "category_data": {"mainCategory": "친구·지인"},
    "messages_by_persona": {
        "test7@again.com": [
            {"action": "send", "content": "룸메이트가 집안일을 전혀 안 해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "설거지가 쌓여있어도 모른 척해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "말하기가 어색해서 참고 있는데 스트레스가 너무 심해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
