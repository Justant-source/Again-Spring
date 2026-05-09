# SC-V10-USER-DELETION: 회원 탈퇴 → 24h 이내 익명화
# 검증: DELETE /api/users/me → 이메일·닉네임 익명화 확인 (단위 테스트 시간 조작)

SCENARIO_SC_V10_USER_DELETION = {
    "id": "SC-V10-USER-DELETION",
    "title": "탈퇴 → 24h 익명화 (시간 조작 단위 테스트)",
    "category": "v10_beta",
    "auth_type": "member",
    "steps": [
        "1. 회원 로그인 → DELETE /api/users/me → 200 + 세션 만료 확인",
        "2. 탈퇴 직후: 동일 이메일로 로그인 시도 → 401 (계정 비활성화) 확인",
        "3. 단위 테스트(UserDeletionServiceTest): scheduleAnonymization() 호출 후 "
           "email=deleted_{id}@anonymized.invalid, nickname='탈퇴한 사용자', deletedAt≠null 확인",
        "4. Admin: GET /api/admin/users/search?q=탈퇴한 사용자 → 익명화 계정 조회 확인",
        "5. PIPA 준수: 원래 이메일·닉네임이 DB에 평문 잔존하지 않는 것 확인",
    ],
    "verification_rules": [
        {
            "type": "http_status",
            "expected_status": 200,
            "comment": "탈퇴 API 정상 응답",
        },
        {
            "type": "db_field_anonymized",
            "table": "users",
            "fields": ["email", "nickname"],
            "comment": "탈퇴 후 email=deleted_*@anonymized.invalid, nickname=탈퇴한 사용자",
        },
    ],
    "note": (
        "24h 경과 시뮬레이션은 UserDeletionServiceTest에서 "
        "scheduleAnonymization()을 직접 호출하여 검증 (실시간 대기 불필요)."
    ),
}
