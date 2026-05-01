# backend/scripts/test-automation/scenarios/normal/sc02_parenting_conflict.py

SCENARIO_SC02 = {
    "id": "SC02",
    "title": "양육관 충돌 (Solo 5턴)",
    "category": "normal",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "자녀·양육"},
    "messages_by_persona": {
        "test5@again.com": [
            {"action": "send",
             "content": "아이 교육 방식에 대해 아내랑 계속 부딪혀요. 저는 자율성을 중요하게 생각하는데",
             "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "아내는 사교육을 더 시키고 싶어해요. 이미 학원이 3개인데도요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
