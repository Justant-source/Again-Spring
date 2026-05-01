# backend/scripts/test-automation/scenarios/flow/sc_flow_solo_report.py
# Solo 6턴 대화 → finalize → 리포트 생성 검증 (Sonnet 모델)

SCENARIO_SC_FLOW_SOLO_REPORT = {
    "id": "SC-FLOW-SOLO-REPORT",
    "title": "Solo 6턴 후 리포트 생성 end-to-end 검증",
    "category": "flow",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "skip_cleanup": True,   # 앱에서 리포트 확인 가능하도록 TERMINATED 처리 생략
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send",
             "content": "시어머니가 첫날부터 다른 며느리와 비교해요. '저 집 며느리는 이것도 잘 하더라' 이런 말을 자주 해요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            {"action": "send",
             "content": "남편에게 말했더니 '엄마는 원래 그래, 신경 쓰지 마'라고 해요. 편을 들어주지 않아요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            {"action": "send",
             "content": "이번 추석에 또 가야 하는데 벌써부터 무서워요. 명절이 너무 싫어졌어요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            {"action": "send",
             "content": "제가 너무 예민한 건지, 며느리로서 당연히 감수해야 하는 건지 모르겠어요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            {"action": "send",
             "content": "남편이 시어머니와 저 사이에서 어떻게 행동하면 좋을지 같이 생각해볼 수 있을까요?",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            {"action": "send",
             "content": "남편에게 비교당하지 않도록 시어머니 앞에서 저를 지켜달라고 부탁하고 싶어요. 그게 가능할까요?",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 종료 요청
            {"action": "finalize"},
            # 세션 상태 확인 (COMPLETED)
            {"action": "assert_status", "expected": "COMPLETED"},
            # 리포트 폴링 (최대 150초 — Sonnet 모델 ~85초 소요)
            {"action": "poll_report", "max_wait": 150, "interval": 8},
        ],
    },
    "verification_rules": [
        # 1. mediator 응답 ≥5 (6턴, 각 40s 대기)
        {"type": "mediator_response_count", "expected_min": 5},
        # 2. turn_meta JSON 메시지 본문 노출 없음
        {"type": "no_turn_meta_leak"},
        # 3. 세션 COMPLETED
        {"type": "session_status", "expected": "COMPLETED"},
        # 4. 리포트 생성됨
        {"type": "report_generated"},
        # 5. isSoloMode = true
        {"type": "report_field", "field": "isSoloMode", "level": "FAIL"},
        # 6. needsMap 존재
        {"type": "report_field", "field": "needsMap", "level": "WARNING"},
        # 7. repairSuggestions 존재
        {"type": "report_field", "field": "repairSuggestions", "level": "WARNING"},
    ],
}
