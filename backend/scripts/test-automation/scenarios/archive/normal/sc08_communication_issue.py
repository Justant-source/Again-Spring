# backend/scripts/test-automation/scenarios/normal/sc08_communication_issue.py

SCENARIO_SC08 = {
    "id": "SC08",
    "title": "연인 소통 문제 (Solo 3턴)",
    "category": "normal",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "소통·이해"},
    "messages_by_persona": {
        "test3@again.com": [
            {"action": "send", "content": "남자친구가 제 말을 잘 안 들어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "얘기하다 보면 폰만 보고 있어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
            {"action": "send", "content": "무시당하는 기분이 들어서 상처받았어요", "delay_before": 0},
            {"action": "wait", "duration": 12},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 1},
    ]
}
