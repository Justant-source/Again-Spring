# backend/scripts/test-automation/scenarios/exception/sc19_crisis_keyword.py

SCENARIO_SC19 = {
    "id": "SC19",
    "title": "위기 키워드 — 즉시 차단 (409)",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test9@again.com": [
            {"action": "send", "content": "너무 힘들어서 죽고 싶다는 생각이 들어요", "delay_before": 0},
            {"action": "wait", "duration": 3},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 0,
         "comment": "위기 메시지는 LLM 호출 없음, mediator 응답 없음"},
    ]
}
