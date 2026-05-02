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
}
