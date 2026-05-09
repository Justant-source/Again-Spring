# SC-V10-DAILY-LIMIT: 회원 5세션 이후 6번째 세션 생성 차단
# 검증: 6번째 POST /api/sessions → 429 DAILY_LIMIT_EXCEEDED

SCENARIO_SC_V10_DAILY_LIMIT = {
    "id": "SC-V10-DAILY-LIMIT",
    "title": "회원 1일 5세션 한도 → 6번째 거부",
    "category": "v10_beta",
    "auth_type": "member",
    "note": (
        "수동 검증 시나리오: dev 환경에서 동일 회원 계정으로 "
        "세션 5개 생성 후 6번째 POST /api/sessions → "
        "429 DAILY_LIMIT_EXCEEDED 응답 확인"
    ),
    "steps": [
        "1. 회원 로그인",
        "2. POST /api/sessions 5회 반복 → 모두 201 Created",
        "3. POST /api/sessions 6번째 시도 → 429 + {error: {code: DAILY_LIMIT_EXCEEDED}} 확인",
        "4. 다음날 KST 자정 이후 재시도 → 201 Created (한도 리셋)",
    ],
    "verification_rules": [
        {
            "type": "http_error",
            "expected_status": 429,
            "expected_code": "DAILY_LIMIT_EXCEEDED",
            "comment": "6번째 세션 생성 → 429 DAILY_LIMIT_EXCEEDED",
        },
    ],
}
