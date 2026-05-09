# SC-V10-FEEDBACK: 게스트/회원 의견 제출 → DB 저장 → Admin 조회
# 검증: POST /api/feedbacks → 201, GET /api/admin/feedbacks → 목록 포함

SCENARIO_SC_V10_FEEDBACK = {
    "id": "SC-V10-FEEDBACK",
    "title": "의견 폼 제출 → DB 저장 → Admin 표시",
    "category": "v10_beta",
    "steps": [
        "1. 게스트로 POST /api/feedbacks {category: 'praise', content: '10자 이상 내용 테스트입니다'} → 201 + {id: N}",
        "2. 회원으로 POST /api/feedbacks {category: 'feature', content: '기능 개선 요청 테스트입니다'} → 201",
        "3. ADMIN 계정으로 GET /api/admin/feedbacks → 위 항목 포함 확인",
        "4. PATCH /api/admin/feedbacks/{id} {status: 'reviewed'} → 200 + status=reviewed",
        "5. category='crisis' 제출 → 로그에 [CRISIS_FEEDBACK] 출력 확인",
    ],
    "verification_rules": [
        {
            "type": "http_status",
            "expected_status": 201,
            "comment": "피드백 제출 성공",
        },
        {
            "type": "admin_list_contains",
            "comment": "Admin 목록에 제출한 피드백 포함",
        },
    ],
}
