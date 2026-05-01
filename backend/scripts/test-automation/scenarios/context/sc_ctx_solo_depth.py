# backend/scripts/test-automation/scenarios/context/sc_ctx_solo_depth.py
# 멀티턴 Solo 6턴 — 표면 불만 → 배경 → 감정 에스컬레이션 → 핵심 상처
# 중재자가 초반 컨텍스트를 후반 응답에서도 유지하는지 검증

SCENARIO_SC_CTX_SOLO_DEPTH = {
    "id": "SC-CTX-SOLO-DEPTH",
    "title": "Solo 6턴 심층 컨텍스트 관리",
    "category": "context",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            # 턴 1: 표면 불만
            {"action": "send",
             "content": "남편이 퇴근 후에 항상 핸드폰만 봐요. 저와 대화를 안 하려 해요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 2: 배경 추가
            {"action": "send",
             "content": "결혼한 지 3년 됐는데 처음 1년은 괜찮았어요. 그때는 저녁마다 같이 산책도 했거든요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 3: 패턴 발견 — 감정적 무효화
            {"action": "send",
             "content": "제가 뭔가 불편하다고 말하면 남편은 '당신이 너무 예민한 거야'라고 해요. 제 감정이 틀린 건지 의심이 돼요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 4: 내면 상태 공개
            {"action": "send",
             "content": "요즘은 대화를 시도하는 게 무서워요. 또 '예민하다'는 말 들을까봐요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 5: 핵심 상처
            {"action": "send",
             "content": "혼자인 것 같아서 너무 외로워요. 같이 있는데 왜 이렇게 외로운 건지 모르겠어요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 6: 해결 요청
            {"action": "send",
             "content": "남편이 제 감정을 인정해주길 원하는데, 어떻게 말하면 남편이 들어줄까요?",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
        ],
    },
    "verification_rules": [
        # 6턴 → 중재자 응답 ≥3개 (LLM 취소 메커니즘으로 일부 턴이 통합될 수 있음)
        {"type": "mediator_response_count", "expected_min": 3},
        # 전체 응답에서 회피 패턴 없음
        {"type": "all_mediator_no_avoidance"},
        # 중재자 응답들이 반복되지 않음
        {"type": "mediator_responses_distinct", "overlap_threshold": 0.65},
        # 후반(3번째 응답 이후) 응답이 초반 키워드를 참조하는지 확인
        {"type": "mediator_later_response_references",
         "keywords": ["예민", "핸드폰", "산책", "3년", "대화"],
         "from_turn": 3},
    ],
}
