package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OutputSanitizer의 "---" 구분선 처리 테스트 (2026-06-11 절단 수정).
 *
 * 회귀 배경: Sonnet은 "제목\n\n---\n\n본문" 형태로 쓰는 습관이 있는데,
 * 기존 코드가 "---" 이후를 무조건 삭제해 본문 전체(1200자+)를 날렸음 (prod 글 ~50% 파괴).
 */
class OutputSanitizerHrTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer();

    @Test
    void preservesBodyAfterHorizontalRule() {
        // 실제 회귀 케이스: 제목 + --- + 긴 본문
        String raw = "간병비 얼마인지 계산해봤는데 손이 떨렸음\n\n---\n\n"
            + "지난 화요일에 요양보호사 선생님한테 연락이 왔음\n"
            + "엄마 상태가 요즘 갑자기 안 좋아졌다고\n"
            + "이제는 시간을 늘려야 할 것 같다고 함\n"
            + "근데 여기에 간병비 150을 더 얹으면 이게 어떻게 계산이 되냐고\n"
            + "나는 그 말을 듣는 순간 뭔가 끊기는 느낌이었음";
        String out = sanitizer.sanitizePost(raw);

        assertTrue(out.length() > 100, "본문이 보존돼야 함 (기존엔 22자로 파괴됨): " + out.length());
        assertTrue(out.contains("간병비 얼마인지 계산해봤는데"), "제목/훅 보존");
        assertTrue(out.contains("요양보호사 선생님한테 연락"), "본문 보존");
        assertTrue(out.contains("끊기는 느낌이었음"), "본문 끝까지 보존");
        assertFalse(out.contains("---"), "구분선 자체는 제거");
    }

    @Test
    void dropsShortTrailingMetaAfterRule() {
        // 본문 뒤에 AI가 덧붙인 짧은 메타는 제거 (뒤가 40자 미만)
        String raw = "어제 남편이 또 약속을 까먹었어 이번 달만 세 번째야 진짜 서운한데 어떻게 말해야 할지 모르겠음\n\n---\n\n반말 사용함";
        String out = sanitizer.sanitizePost(raw);
        assertTrue(out.contains("세 번째야"), "본문 보존");
        assertFalse(out.contains("반말 사용함"), "짧은 메타 꼬리 제거");
        assertFalse(out.contains("---"));
    }

    @Test
    void handlesBodyWithoutRuleUnchanged() {
        String raw = "어제 회사에서 팀장이 내 보고서를 자기가 만들었다고 발표했어\n내 이름은 한 번도 안 나왔음\n진짜 어이없어서 말도 안 나왔음";
        String out = sanitizer.sanitizePost(raw);
        assertTrue(out.contains("팀장이 내 보고서"));
        assertTrue(out.contains("말도 안 나왔음"));
    }

    @Test
    void multipleRulesCollapseToBlankLines() {
        String raw = "첫 사건이 있었음 지난주 월요일에 회사에서 처음 시작된 일이었어\n---\n"
            + "그 다음에 또 비슷한 일이 생겼고 이번엔 훨씬 더 심했어\n---\n"
            + "결국 이렇게까지 와버렸음 나 진짜 어떻게 해야 할지 모르겠음";
        String out = sanitizer.sanitizePost(raw);
        assertFalse(out.contains("---"), "모든 구분선 제거");
        assertTrue(out.contains("첫 사건"));
        assertTrue(out.contains("훨씬 더 심했어"), "중간 단락 보존");
        assertTrue(out.contains("모르겠음"), "마지막 단락까지 보존");
    }

    @Test
    void postLengthCapMatchesBackendLimit() {
        // backend PostCreateRequest @Size(max=1000) — sanitize 결과가 반드시 1000자 이하여야 게시됨
        String body = "어제 회사에서 팀장이 또 내 보고서를 가로챘는데 진짜 어이가 없었음 ".repeat(40); // 1000자 초과
        String out = sanitizer.sanitizePost(body);
        assertTrue(out.length() <= 1000, "MAX_POST=1000 컷 (backend 제한 일치): " + out.length());
    }
}
