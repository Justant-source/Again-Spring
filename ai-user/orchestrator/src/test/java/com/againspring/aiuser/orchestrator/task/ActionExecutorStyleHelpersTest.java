package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 문체 현실화(S1·S3) 헬퍼 테스트.
 * - 히스토리 역파싱: writeHistory 포맷과 동기 — 포맷이 바뀌면 여기서 깨져야 함
 * - 반복 가드: 2-gram Jaccard
 * - 댓글 모드 샘플링: 분포·stance 가중
 */
class ActionExecutorStyleHelpersTest {

    // ── 히스토리 역파싱 (writeHistory 포맷 동기) ────────────────────────────

    @Test
    void extractsCommentBodyFromHistoryBlock() {
        // writeHistory: "\n| ts | 댓글 | postId | preview |\n\n> 본문\n"
        String block = "\n| 2026-06-11 08:30 | 댓글 | post_a089 | 진짜 열받네 ㅠ 근데... |\n\n> 진짜 열받네 ㅠ 근데 이런 경우 기록 남겨놔\n";
        assertEquals("진짜 열받네 ㅠ 근데 이런 경우 기록 남겨놔",
            ActionExecutor.extractHistoryBody(block, "comments"));
    }

    @Test
    void extractsPostBodyFromHistoryBlock() {
        // writeHistory: "\n| ts | cat | postId | titlePreview | POSTED |\n\n### ts — cat\n본문\n"
        String block = "\n| 2026-06-09 00:35 | WORK | post_95cc | 지난주 금요일에... | POSTED |\n\n### 2026-06-09 00:35 — WORK\n지난주 금요일에 팀장이 내게 왔는데\n월요일부터 새 프로젝트 떠넘김\n";
        assertEquals("지난주 금요일에 팀장이 내게 왔는데\n월요일부터 새 프로젝트 떠넘김",
            ActionExecutor.extractHistoryBody(block, "posts"));
    }

    @Test
    void returnsNullForGarbageBlocks() {
        assertNull(ActionExecutor.extractHistoryBody("", "comments"));
        assertNull(ActionExecutor.extractHistoryBody(null, "comments"));
        assertNull(ActionExecutor.extractHistoryBody("| 테이블 행만 있음 |", "comments"));
        assertNull(ActionExecutor.extractHistoryBody("헤더 없는 텍스트", "posts"));
    }

    @Test
    void formatsRecentOutputsLatestFirstWithTruncation() {
        String formatted = ActionExecutor.formatRecentOutputs(
            List.of("첫 번째 댓글", "두 번째 댓글", "세 번째 아주 길게 쓴 댓글인데 잘려야 함"), 10);
        assertNotNull(formatted);
        String[] lines = formatted.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("- 세 번째"), "최신이 첫 줄");
        assertTrue(lines[0].endsWith("…"), "초과분은 말줄임");
        assertEquals("- 첫 번째 댓글", lines[2]);
        assertNull(ActionExecutor.formatRecentOutputs(List.of(), 10));
        assertNull(ActionExecutor.formatRecentOutputs(null, 10));
    }

    // ── 반복 가드 (2-gram Jaccard) ──────────────────────────────────────────

    @Test
    void detectsNearDuplicateOutputs() {
        String prev = "근데 증거 챙기는 것도 결국 본인이 직접 움직여야 하는 거라 피곤하긴 함 ㅠ";
        String almostSame = "근데 증거 챙기는 것도 결국 본인이 직접 움직여야 하는 거라 피곤함 ㅠ";
        assertTrue(ActionExecutor.maxBigramJaccard(almostSame, List.of(prev)) > 0.45,
            "거의 같은 문장은 임계 초과");

        String different = "어휴 그건 팀장이 선 넘었네 일단 메일로 기록부터 남겨";
        assertTrue(ActionExecutor.maxBigramJaccard(different, List.of(prev)) < 0.45,
            "다른 문장은 임계 미만");
    }

    @Test
    void shortTextsAreExemptFromRepetitionGuard() {
        // 12자 미만은 bigram 노이즈가 커서 가드 제외 (빈 셋)
        assertEquals(0.0, ActionExecutor.maxBigramJaccard("ㄹㅇ 공감", List.of("ㄹㅇ 공감")));
        assertTrue(ActionExecutor.charBigrams("짧은 글").isEmpty());
    }

    // ── 댓글 모드 샘플링 (S3) ───────────────────────────────────────────────

    private ActionExecutor bareExecutor() {
        // 순수 함수 계열만 호출 — 의존성 전부 null로 인스턴스화
        return new ActionExecutor(null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }

    private Persona casualPersona() {
        return Persona.builder()
            .id("p1")
            .voiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"))
            .build();
    }

    @Test
    void commentModeSamplingCoversAllModesWithoutDominance() {
        ActionExecutor exec = bareExecutor();
        Persona persona = casualPersona();
        EnumMap<ActionExecutor.CommentMode, Integer> counts = new EnumMap<>(ActionExecutor.CommentMode.class);
        int n = 4000;
        for (int i = 0; i < n; i++) {
            counts.merge(exec.pickCommentMode(persona, "NEUTRAL"), 1, Integer::sum);
        }
        for (ActionExecutor.CommentMode mode : ActionExecutor.CommentMode.values()) {
            assertTrue(counts.getOrDefault(mode, 0) > 0, mode + " 모드가 한 번도 안 나옴");
            assertTrue(counts.getOrDefault(mode, 0) < n * 0.5, mode + " 모드가 과반 점유");
        }
    }

    @Test
    void partnerStanceBoostsDisagreeMode() {
        ActionExecutor exec = bareExecutor();
        Persona persona = casualPersona();
        int n = 4000, partnerDisagree = 0, authorDisagree = 0;
        for (int i = 0; i < n; i++) {
            if (exec.pickCommentMode(persona, "PARTNER") == ActionExecutor.CommentMode.DISAGREE) partnerDisagree++;
            if (exec.pickCommentMode(persona, "AUTHOR") == ActionExecutor.CommentMode.DISAGREE) authorDisagree++;
        }
        assertTrue(partnerDisagree > authorDisagree, "PARTNER stance에서 DISAGREE 가중 상승");
    }

    @Test
    void modeHintsAreRenderable() {
        ActionExecutor exec = bareExecutor();
        for (ActionExecutor.CommentMode mode : ActionExecutor.CommentMode.values()) {
            String hint = exec.commentModeHint(mode);
            assertNotNull(hint);
            assertFalse(hint.isBlank());
            assertTrue(hint.contains("자"), "길이 지시 포함: " + hint);
        }
        assertTrue(exec.commentModeHint(ActionExecutor.CommentMode.REACTION_ONLY).contains("조언"));
        assertFalse(exec.replyLengthHint().isBlank());
    }
}
