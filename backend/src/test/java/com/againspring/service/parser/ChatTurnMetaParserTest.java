package com.againspring.service.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import org.junit.jupiter.api.Test;

class ChatTurnMetaParserTest {

    private final ChatTurnMetaParser parser = new ChatTurnMetaParser();

    @Test
    void parse_returnsBlank_whenInputNull() {
        var r = parser.parse(null, 1, "USER_A");
        assertEquals("", r.mediatorMessage());
        assertNull(r.horsemen());
        assertNull(r.nvc());
    }

    @Test
    void parse_extractsBothTextAndMeta() {
        String raw = "그러셨군요. 마음이 무거우셨겠어요.\n"
            + "<turn_meta>{\"horsemen\":{\"criticism\":0.4,\"contempt\":0,\"defensiveness\":0.1,\"stonewalling\":0},"
            + "\"nvc_completion\":{\"observation\":true,\"feeling\":true,\"need\":false,\"request\":false}}"
            + "</turn_meta>";
        var r = parser.parse(raw, 3, "USER_A");

        assertTrue(r.mediatorMessage().contains("그러셨군요"));
        assertFalse(r.mediatorMessage().contains("turn_meta"));
        assertNotNull(r.horsemen());
        assertEquals(3, r.horsemen().turn);
        assertEquals("USER_A", r.horsemen().sender);
        assertEquals(0.4, r.horsemen().criticism);
        assertEquals(0.0, r.horsemen().contempt);

        assertNotNull(r.nvc());
        assertTrue(r.nvc().observation);
        assertFalse(r.nvc().need);
    }

    @Test
    void parse_clampsOutOfRangeIntensities() {
        String raw = "응답.\n<turn_meta>{\"horsemen\":{\"criticism\":1.7,\"contempt\":-0.2}}</turn_meta>";
        Session.HorsemenTurnEntry h = parser.parse(raw, 1, "USER_A").horsemen();
        assertEquals(1.0, h.criticism);
        assertEquals(0.0, h.contempt);
    }

    @Test
    void parse_returnsNullScores_whenMetaBlockMissing() {
        String raw = "잘 들었어요. 어떤 마음이셨을까요?";
        var r = parser.parse(raw, 1, "USER_A");
        assertEquals(raw, r.mediatorMessage());
        assertNull(r.horsemen());
        assertNull(r.nvc());
    }

    @Test
    void parse_returnsNullScores_whenJsonMalformed() {
        String raw = "응답입니다.\n<turn_meta>{not valid json}</turn_meta>";
        var r = parser.parse(raw, 1, "USER_A");
        assertEquals("응답입니다.", r.mediatorMessage());
        assertNull(r.horsemen());
        assertNull(r.nvc());
    }

    @Test
    void parse_stripsCodeFenceWrappedMeta_missingClosingTag() {
        String raw = "남편분한테 꼭 전하고 싶은 게 뭘까요?\n\n"
            + "```\n<turn_meta>\n"
            + "{\"horsemen\":{\"criticism\":0.0,\"contempt\":0.0,\"defensiveness\":0.0,\"stonewalling\":0.0},"
            + "\"nvc_completion\":{\"observation\":false,\"feeling\":false,\"need\":false,\"request\":false},"
            + "\"user_state\":{\"state\":\"NEGOTIATING\",\"evidence\":\"함께 생각해볼 수 있을까요\",\"confidence\":0.85}}"
            + "\n```";
        var r = parser.parse(raw, 5, "USER_A");

        assertTrue(r.mediatorMessage().contains("남편분한테"));
        assertFalse(r.mediatorMessage().contains("turn_meta"));
        assertFalse(r.mediatorMessage().contains("```"));
        assertNotNull(r.horsemen());
        assertNotNull(r.userState());
        assertEquals("NEGOTIATING", r.userState().state.name());
    }

    @Test
    void parse_stripsCodeFenceWrappedMeta_withClosingTag() {
        String raw = "응답 텍스트입니다.\n"
            + "```json\n<turn_meta>\n"
            + "{\"horsemen\":{\"criticism\":0.3,\"contempt\":0.0,\"defensiveness\":0.0,\"stonewalling\":0.0}}\n"
            + "</turn_meta>\n```";
        var r = parser.parse(raw, 2, "USER_A");

        assertTrue(r.mediatorMessage().contains("응답 텍스트"));
        assertFalse(r.mediatorMessage().contains("turn_meta"));
        assertFalse(r.mediatorMessage().contains("```"));
        assertNotNull(r.horsemen());
        assertEquals(0.3, r.horsemen().criticism);
    }

    @Test
    void parse_unwrapsMediatorResponseWrapperIfPresent() {
        String raw = "<mediator_response>그러셨겠어요.</mediator_response>"
            + "<turn_meta>{\"horsemen\":{\"criticism\":0,\"contempt\":0,\"defensiveness\":0,\"stonewalling\":0}}</turn_meta>";
        var r = parser.parse(raw, 1, "USER_A");
        assertEquals("그러셨겠어요.", r.mediatorMessage());
        assertNotNull(r.horsemen());
    }

    // ── 실제 버그 재현: AI가 독립 최상위 태그로 출력하는 케이스 ──

    @Test
    void parse_stripsStandaloneIssueDeltaTag_andParsesIt() {
        String raw = "아, 그렇군요. 그 시간이 특히 불안했을 것 같아요.\n"
            + "<issue_delta>\n"
            + "{\"headline\":\"어제 취침 통보 후 오전 11시까지 연락 공백\","
            + "\"facts_added\":[{\"text\":\"어제 잔다고 카톡함\",\"contributesTo\":\"PERSPECTIVE\"}]}\n"
            + "</issue_delta>";
        var r = parser.parse(raw, 2, "USER_A");

        assertTrue(r.mediatorMessage().contains("아, 그렇군요"));
        assertFalse(r.mediatorMessage().contains("issue_delta"), "issue_delta 태그가 노출되면 안 됨");
        assertFalse(r.mediatorMessage().contains("facts_added"), "JSON 내용이 노출되면 안 됨");
        assertNotNull(r.issueDelta(), "issueDelta 파싱 결과가 있어야 함");
        assertEquals("어제 취침 통보 후 오전 11시까지 연락 공백", r.issueDelta().headline);
        assertEquals(1, r.issueDelta().factsAdded.size());
    }

    @Test
    void parse_stripsStandaloneQueueDeltaTag_andParsesIt() {
        String raw = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?\n"
            + "<question_queue_delta>\n"
            + "{\"asked\":[\"8b547c9f-67eb-4ff0-8d1a-f823e1e6cb72\"],"
            + "\"new\":[{\"intent\":\"SEEK_FEELING\",\"target\":\"USER_A\",\"text\":\"그때 어떤 감정이었나요\"}]}\n"
            + "</question_queue_delta>";
        var r = parser.parse(raw, 3, "USER_A");

        assertTrue(r.mediatorMessage().contains("잠깐 정리할 시간이 필요해요"));
        assertFalse(r.mediatorMessage().contains("question_queue_delta"), "question_queue_delta 태그가 노출되면 안 됨");
        assertFalse(r.mediatorMessage().contains("SEEK_FEELING"), "JSON 내용이 노출되면 안 됨");
        assertNotNull(r.queueDelta(), "queueDelta 파싱 결과가 있어야 함");
        assertEquals(1, r.queueDelta().asked.size());
        assertEquals(1, r.queueDelta().newQuestions.size());
    }

    @Test
    void parse_handlesBothStandaloneTags_togethterWithText() {
        String raw = "그러셨군요. 마음이 많이 무거우셨겠어요.\n"
            + "<issue_delta>{\"headline\":\"반복된 무시 패턴\"}</issue_delta>\n"
            + "<question_queue_delta>{\"asked\":[],\"new\":[]}</question_queue_delta>";
        var r = parser.parse(raw, 4, "USER_A");

        assertEquals("그러셨군요. 마음이 많이 무거우셨겠어요.", r.mediatorMessage());
        assertFalse(r.mediatorMessage().contains("<"), "어떤 XML 태그도 노출되면 안 됨");
        assertNotNull(r.issueDelta());
        assertEquals("반복된 무시 패턴", r.issueDelta().headline);
    }

    @Test
    void parse_standaloneIssueDoesNotOverrideTurnMetaIssueDelta() {
        // turn_meta 내 issue_delta가 있으면 standalone 태그는 무시
        String raw = "응답입니다.\n"
            + "<turn_meta>{\"horsemen\":{\"criticism\":0.2,\"contempt\":0,\"defensiveness\":0,\"stonewalling\":0},"
            + "\"issue_delta\":{\"headline\":\"turn_meta 헤드라인\"}}</turn_meta>\n"
            + "<issue_delta>{\"headline\":\"standalone 헤드라인\"}</issue_delta>";
        var r = parser.parse(raw, 5, "USER_A");

        assertEquals("turn_meta 헤드라인", r.issueDelta().headline, "turn_meta 값이 우선돼야 함");
        assertFalse(r.mediatorMessage().contains("issue_delta"));
    }
}
