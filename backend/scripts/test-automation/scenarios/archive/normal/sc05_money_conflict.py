# backend/scripts/test-automation/scenarios/normal/sc05_money_conflict.py

SCENARIO_SC05 = {
    "id": "SC05",
    "title": "금전 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "금전·경제"},
    "messages_by_persona": {
        "test6@again.com": [
            {"action": "send", "content": "남자친구가 제 돈을 빌려갔는데 안 갚아요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "50만원인데 한 달이 지났어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "직접 얘기하면 싸울 것 같아서 망설여져요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
        {"type": "no_avoidance_pattern"},
    ]
}
