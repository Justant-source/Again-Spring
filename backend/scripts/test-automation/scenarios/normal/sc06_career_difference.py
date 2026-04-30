# backend/scripts/test-automation/scenarios/normal/sc06_career_difference.py

SCENARIO_SC06 = {
    "id": "SC06",
    "title": "부모자녀 진로 갈등 (Solo 3턴)",
    "category": "normal",
    "relation_type": "parent_child",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부모·자녀"},
    "messages_by_persona": {
        "test6@again.com": [
            {"action": "send", "content": "부모님이 제 진로 선택을 반대하세요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "디자인을 하고 싶은데 안정적인 직장을 원하세요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "대화가 매번 싸움으로 끝나요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
