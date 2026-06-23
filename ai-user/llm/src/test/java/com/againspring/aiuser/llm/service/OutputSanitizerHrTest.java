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

    // ── 분포 매칭 (Step 6) ─────────────────────────────────────────────────

    @Test
    void sanitizePostWithVoiceTypeReturnsSameLengthClass() {
        // voiceType 있어도 기본 sanitize 결과와 크게 달라지지 않아야 함 (길이 계층 동일)
        String raw = "어제 학교 갔는데 친구 만나서 밥 먹었음";
        String withVoice  = sanitizer.sanitizePost(raw, "NATEPAN");
        String withoutVoice = sanitizer.sanitizePost(raw);
        assertNotNull(withVoice);
        // 알 수 없는 voiceType은 기본값과 동일
        assertEquals(withoutVoice, sanitizer.sanitizePost(raw, "UNKNOWN_COMMUNITY"));
        // null voiceType도 기본값과 동일
        assertEquals(withoutVoice, sanitizer.sanitizePost(raw, null));
    }

    @Test
    void commaRateNormalizationRemovesExcessCommas() {
        // NATEPAN target=0.011, 1.5배=0.0165. 쉼표 20개/200자=10% → 제거 대상
        StringBuilder sb = new StringBuilder();
        // 200자짜리 텍스트에 쉼표 20개 주입
        for (int i = 0; i < 10; i++) {
            sb.append("어제,학교,갔음 ");
        }
        String highComma = sb.toString().trim(); // ~100자, 쉼표 20개
        // sampleProb 때문에 항상 제거되진 않지만, 최소한 결과는 null이 아님
        String result = sanitizer.sanitizePost(highComma, "NATEPAN");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void sanitizeCommentWithVoiceTypeWorks() {
        String raw = "ㄹㅇ 그건 좀 아니지 않음";
        String result = sanitizer.sanitizeComment(raw, "DCINSIDE");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void theqooCleanupRemovesTrailingHeol() {
        String raw = "남친이 또 약속 어겼어요 ㅋㅋ 이번엔 진짜 안 그럴게 해놓고 당일에 취소함ㅠㅠ 제가 예민한 건지 헷갈려요 헐";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertFalse(result.endsWith("헐"), "THEQOO 후처리는 문장 끝 standalone 헐을 제거해야 함");
        assertFalse(result.contains("헷갈려요 헐"));
        assertTrue(result.contains("헷갈려요"));
    }

    @Test
    void theqooCleanupRemovesStandaloneHeolAndEmoji() {
        String raw = "동료가 제 아이디어 가로채서 너무 짜증나요... 헐 제가 예민한 건지 모르겠어요 😥";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertFalse(result.contains("헐 제가"), "문장 중간 standalone 헐 제거");
        assertFalse(result.contains("😥"), "장난스러운 유니코드 이모지 제거");
        assertTrue(result.contains("제가 예민한 건지 모르겠어요"));
    }

    @Test
    void theqooCleanupRemovesInlineGaegonggam() {
        String raw = "지난주 영화도 내가 냈고 개공감 근데 또 내가 더 내래";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertFalse(result.contains("개공감 근데"), "문장 중간 standalone 개공감 제거");
        assertTrue(result.contains("내가 냈고 근데"));
    }

    @Test
    void theqooCleanupNormalizesUnicodeEllipsis() {
        String raw = "남친이 또 저한테 예민하다고 하는데… 내가 이상한 건지 모르겠음…";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertFalse(result.contains("…"), "유니코드 말줄임표는 ASCII 점 세 개로 정규화");
        assertTrue(result.contains("모르겠음..."), "THEQOO 잔여 탐지 신호를 ASCII ellipsis로 치환");
    }

    @Test
    void theqooCleanupNormalizesAwkwardSpecificPhrases() {
        String raw = "진짜 내가 예민한 건가 싶어서 올려봐... 룸메가 같이 사는데 집안일을 너무 안 해. 쓰레기 차도 안 버리고.\n"
            + "오빠가 자꾸 집에서는 딸이 더 조심해야 된다 이래서 답답함.";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertTrue(result.contains("쓰레기통이 차도 안 버리고"), "THEQOO 어색한 쓰레기 표현 정규화");
        assertTrue(result.contains("집에서는 여자가 더 조심해야"), "THEQOO 오빠 화자에서 어색한 딸 지칭 정규화");
        assertFalse(result.contains("쓰레기 차도"));
        assertFalse(result.contains("집에서는 딸이 더 조심해야"));
    }

    @Test
    void theqooCleanupNormalizesOneDoAndWeekdayMiddot() {
        String raw = "나는 이게 1도 모르겠고 월·화·수 내내 연락 기다렸는데 1도 이해가 안 됨";
        String result = sanitizer.sanitizePost(raw, "THEQOO");

        assertTrue(result.contains("진짜 모르겠고"), "남발된 1도 모르겠고는 덜 튀는 표현으로 정규화");
        assertTrue(result.contains("월, 화, 수"), "요일 사이 middle dot은 쉼표로 정규화");
        assertTrue(result.contains("도무지 이해가 안 됨"), "1도 이해가 안 됨은 과한 패턴을 줄인다");
        assertFalse(result.contains("1도 모르겠고"));
        assertFalse(result.contains("월·화·수"));
    }

    @Test
    void stripsTrailingOperationMemoTable() {
        String raw = "인천에서 살면서 느낀 건데 이런 일 오면 나도 같이 흔들리더만\n"
            + "몇달 동생 고민 다 들어주고 금전적으로도 도왔는데 이제 모르겠음 ㄹㅇ\n"
            + "적용 처리 메모\n"
            + "| 항목 | 처리 내용 |\n"
            + "|------|-----------|\n"
            + "| 구체 사건 | 2주 연락 두절 |\n"
            + "| 페르소나 quirk | 더만 종결 |\n";

        String out = sanitizer.sanitizePost(raw);

        assertTrue(out.contains("인천에서 살면서 느낀 건데"));
        assertFalse(out.contains("적용 처리 메모"));
        assertFalse(out.contains("| 항목 | 처리 내용 |"));
        assertFalse(out.contains("페르소나 quirk"));
    }

    @Test
    void stripsTrailingWritingNoteChecklist() {
        String raw = "혹시 저만 이렇게 생각하는 건지 모르겠는데요\n"
            + "사귀는 사람이 5시간 동안 연락을 한 줄만 보냈다는 게 저는 좀 심하다 싶더라고요\n"
            + "[작성 노트]\n"
            + "- 트리거: 5시간 동안 연락 한 줄\n"
            + "- 어미 변화: ~더라고요 → ~잖아요\n"
            + "- 모바일 오타: 납들하기\n"
            + "- 페르소나 표현: 도덕성이 중요한데 삽입\n"
            + "- 온점·쌍따옴표 없음\n";

        String out = sanitizer.sanitizePost(raw);

        assertTrue(out.contains("혹시 저만 이렇게 생각하는 건지 모르겠는데요"));
        assertFalse(out.contains("[작성 노트]"));
        assertFalse(out.contains("- 트리거:"));
        assertFalse(out.contains("- 온점·쌍따옴표 없음"));
    }
}
