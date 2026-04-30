# backend/scripts/test-automation/scenarios/normal/sc04_workplace_conflict.py

SCENARIO_SC04 = {
    "id": "SC04",
    "title": "직장동료 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "colleague",
    "category_data": {"mainCategory": "직장·직업"},
    "messages_by_persona": {
        "test4@again.com": [
            {"action": "send", "content": "팀장이 제 아이디어를 자기 것처럼 발표했어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "동료들이 다 봤는데 너무 억울해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "항의하고 싶은데 어떻게 해야 할지 모르겠어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 2},
        {"type": "no_avoidance_pattern"},
    ]
}
