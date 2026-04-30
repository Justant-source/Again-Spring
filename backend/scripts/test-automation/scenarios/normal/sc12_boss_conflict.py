# backend/scripts/test-automation/scenarios/normal/sc12_boss_conflict.py

SCENARIO_SC12 = {
    "id": "SC12",
    "title": "상사 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "colleague",
    "category_data": {"mainCategory": "직장·직업"},
    "messages_by_persona": {
        "test4@again.com": [
            {"action": "send", "content": "상사가 회의 때마다 제 의견을 묵살해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "처음엔 참았는데 이제 일하기 싫어졌어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "이직을 생각하고 있는데 맞는 판단인지 모르겠어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
