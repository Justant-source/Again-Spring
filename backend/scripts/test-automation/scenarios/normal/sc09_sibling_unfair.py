# backend/scripts/test-automation/scenarios/normal/sc09_sibling_unfair.py

SCENARIO_SC09 = {
    "id": "SC09",
    "title": "형제자매 공평함 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "family",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "형제자매"},
    "messages_by_persona": {
        "test8@again.com": [
            {"action": "send", "content": "언니가 부모님 용돈을 혼자 더 많이 받는 것 같아요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "제가 취업을 못 해서 그런 것 같긴 한데", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "공평하지 않다고 느껴져요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
