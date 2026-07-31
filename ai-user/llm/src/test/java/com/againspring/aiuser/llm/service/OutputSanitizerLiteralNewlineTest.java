package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OutputSanitizer의 리터럴 "\n" 정규화 테스트 (2026-07-31 수정).
 *
 * 회귀 배경: post_2b97a638711244f2a889 — LLM이 실제 개행(0x0A) 대신
 * 문자 그대로의 백슬래시+n을 출력해, 본문이 "...헐\n2년 전에..." 처럼
 * 리터럴 텍스트로 그대로 게시됨. FE는 whiteSpace: pre-wrap을 쓰므로
 * 실제 개행문자였다면 정상 렌더링됐을 것 — 데이터 자체의 문제였음.
 */
class OutputSanitizerLiteralNewlineTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer();

    @Test
    void convertsLiteralBackslashNToRealNewline() {
        String raw = "전남친한테 연락 왔음 헐\\n2년 전에 서로 바빠지면서 흐지부지 끝난 사람인데 크게 싸운 건 아니어서 그냥 각자 살았거든\\n"
            + "헤어지고 나서 나한테 집중해보자 싶어서 식단이랑 운동 시작했고 피부과도 꾸준히 다녔어";
        String out = sanitizer.sanitizePost(raw);

        assertFalse(out.contains("\\n"), "리터럴 백슬래시+n이 남아있으면 안 됨: " + out);
        assertTrue(out.contains("\n"), "실제 개행으로 변환돼야 함");
    }

    @Test
    void leavesRealNewlinesUnaffected() {
        String raw = "전남친한테 연락 왔음 헐\n2년 전에 서로 바빠지면서 흐지부지 끝난 사람인데 크게 싸운 건 아니어서 그냥 각자 살았거든\n"
            + "헤어지고 나서 나한테 집중해보자 싶어서 식단이랑 운동 시작했고 피부과도 꾸준히 다녔어";
        String out = sanitizer.sanitizePost(raw);

        assertTrue(out.contains("헐\n2년"), "이미 실제 개행이면 그대로 유지");
    }
}
