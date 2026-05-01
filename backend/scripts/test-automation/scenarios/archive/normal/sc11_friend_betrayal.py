# backend/scripts/test-automation/scenarios/normal/sc11_friend_betrayal.py

SCENARIO_SC11 = {
    "id": "SC11",
    "title": "친구의 배신 (Solo 3턴)",
    "category": "normal",
    "relation_type": "friend",
    "category_data": {"mainCategory": "친구·지인"},
    "messages_by_persona": {
        "test3@again.com": [
            {"action": "send", "content": "친한 친구가 제 비밀을 다른 사람한테 얘기했어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "믿었던 친구라서 더 상처가 심해요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "어떻게 대처해야 할지 모르겠어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
