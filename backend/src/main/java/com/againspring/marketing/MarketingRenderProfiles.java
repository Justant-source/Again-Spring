package com.againspring.marketing;

import java.util.List;
import java.util.Set;

/**
 * 허용 렌더 프로필의 단일 출처(SSOT). 컨트롤러 검증·서비스 기본값 모두 여기를 참조한다.
 * 새 프로필 추가 시 이 목록과 프런트 {@code frontend/lib/api/admin/marketing.ts} 의
 * RENDER_PROFILE_OPTIONS 만 갱신한다.
 */
public final class MarketingRenderProfiles {

    public static final String FAST = "marketing_fast";
    public static final String V2 = "marketing_v2";
    /** 훅 구간 디자인 A안: 숫자 강조·대형 글자. 2026-10-18 운영 채택(자동 게시 허용). */
    public static final String HOOK_A = "marketing_hook_a";
    /** 훅 구간 디자인 실험: 키네틱 타이포. */
    public static final String HOOK_B = "marketing_hook_b";
    /** 훅 구간 디자인 실험: 캐릭터 리액션 먼저. */
    public static final String HOOK_C = "marketing_hook_c";

    /** 허용 전체 (표시 순서 유지). */
    public static final List<String> ALLOWED = List.of(FAST, V2, HOOK_A, HOOK_B, HOOK_C);

    /** 테스트 탭(autoPublish=false) 전용 실험 프로필. 자동 게시 잡에는 쓸 수 없다. A안은 채택되어 제외. */
    public static final Set<String> EXPERIMENTAL = Set.of(HOOK_B, HOOK_C);

    private MarketingRenderProfiles() {}

    public static boolean isAllowed(String profile) {
        return profile != null && ALLOWED.contains(profile);
    }

    public static boolean isExperimental(String profile) {
        return profile != null && EXPERIMENTAL.contains(profile);
    }
}
