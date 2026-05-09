# SC-V10-ADMIN-ACCESS: USER role → 403 / ADMIN role → 200
# 검증: GET /api/admin/dashboard/summary 권한 분기

SCENARIO_SC_V10_ADMIN_ACCESS = {
    "id": "SC-V10-ADMIN-ACCESS",
    "title": "/admin USER 403 + ADMIN 200",
    "category": "v10_beta",
    "steps": [
        "1. 일반 회원(USER role)으로 로그인 → GET /api/admin/dashboard/summary → 403 응답 확인",
        "2. ADMIN 계정(suhday@naver.com)으로 로그인 → GET /api/admin/dashboard/summary → 200 응답 확인",
        "3. ADMIN 계정으로 GET /api/admin/feedbacks → 200 + 피드백 목록 확인",
        "4. ADMIN 계정으로 GET /api/admin/users/search?q=test → 200 확인",
        "5. 비로그인 상태에서 GET /api/admin/dashboard/summary → 401 확인",
        "6. FE: 일반 회원으로 /admin 경로 직접 접근 → '접근 권한이 없습니다' 표시 확인",
    ],
    "verification_rules": [
        {
            "type": "http_status",
            "role": "USER",
            "expected_status": 403,
            "comment": "일반 회원 → /api/admin/** 403",
        },
        {
            "type": "http_status",
            "role": "ADMIN",
            "expected_status": 200,
            "comment": "ADMIN → /api/admin/** 200",
        },
    ],
}
