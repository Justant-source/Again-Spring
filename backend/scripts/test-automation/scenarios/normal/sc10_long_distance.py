# backend/scripts/test-automation/scenarios/normal/sc10_long_distance.py

SCENARIO_SC10 = {
    "id": "SC10",
    "title": "장거리 연애 문제 (Solo 3턴)",
    "category": "normal",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "연애·결혼"},
    "messages_by_persona": {
        "test5@again.com": [
            {"action": "send", "content": "장거리 연애를 1년 했는데 연락이 줄었어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "바쁘다고는 하는데 예전과 달라진 것 같아요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "이대로 헤어지는 건지 불안해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
