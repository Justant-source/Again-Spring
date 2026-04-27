package com.againspring.service.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import org.junit.jupiter.api.Test;

class ChatTurnMetaParserUserStateTest {

    private final ChatTurnMetaParser parser = new ChatTurnMetaParser();

    @Test
    void parse_extractsUserState_withAllFields() {
        String response = "응답 본문\n\n<turn_meta>{\n"
            + "\"horsemen\":{\"criticism\":0.0,\"contempt\":0.0,\"defensiveness\":0.0,\"stonewalling\":0.0},\n"
            + "\"nvc_completion\":{\"observation\":false,\"feeling\":true,\"need\":false,\"request\":false},\n"
            + "\"user_state\":{\"state\":\"VENTING\",\"evidence\":\"무거운 분위기\",\"confidence\":0.7,\"derived_from\":\"nvc.feeling=true\"}\n"
            + "}</turn_meta>";

        ChatTurnMetaParser.Result result = parser.parse(response, 3, "USER_A");

        assertNotNull(result.userState());
        assertEquals(Session.UserState.VENTING, result.userState().state);
        assertEquals("무거운 분위기", result.userState().evidenceSnippet);
        assertEquals(0.7, result.userState().confidence);
        assertEquals("nvc.feeling=true", result.userState().derivedFrom);
        assertEquals(3, result.userState().turn);
        assertEquals("USER_A", result.userState().sender);
    }

    @Test
    void parse_handlesUnknownState_returnsNullUserState() {
        String response = "본문\n\n<turn_meta>{\"user_state\":{\"state\":\"UNKNOWN_STATE\"}}</turn_meta>";
        ChatTurnMetaParser.Result result = parser.parse(response, 1, "USER_A");
        assertNull(result.userState());
    }

    @Test
    void parse_handlesAbsentUserStateField_returnsNullUserState() {
        String response = "본문\n\n<turn_meta>{\n"
            + "\"horsemen\":{\"criticism\":0.1,\"contempt\":0.0,\"defensiveness\":0.0,\"stonewalling\":0.0}\n"
            + "}</turn_meta>";
        ChatTurnMetaParser.Result result = parser.parse(response, 2, "USER_B");
        assertNull(result.userState());
    }

    @Test
    void parse_clampsConfidence_toValidRange() {
        String response = "본문\n\n<turn_meta>{\"user_state\":{\"state\":\"DEFENSIVE\",\"confidence\":1.5}}</turn_meta>";
        ChatTurnMetaParser.Result result = parser.parse(response, 1, "USER_A");
        assertNotNull(result.userState());
        assertEquals(Session.UserState.DEFENSIVE, result.userState().state);
        assertEquals(1.0, result.userState().confidence);
    }

    @Test
    void parse_trimsEvidenceTo30Chars() {
        String longEvidence = "a".repeat(50);
        String response = "본문\n\n<turn_meta>{\"user_state\":{\"state\":\"BLAMING\",\"evidence\":\"" + longEvidence + "\"}}</turn_meta>";
        ChatTurnMetaParser.Result result = parser.parse(response, 1, "USER_A");
        assertNotNull(result.userState());
        assertEquals(30, result.userState().evidenceSnippet.length());
    }

    @Test
    void parse_preservesExistingHorsemenAndNvc_whenUserStateAdded() {
        String response = "본문\n\n<turn_meta>{\n"
            + "\"horsemen\":{\"criticism\":0.5,\"contempt\":0.0,\"defensiveness\":0.3,\"stonewalling\":0.0},\n"
            + "\"nvc_completion\":{\"observation\":true,\"feeling\":false,\"need\":false,\"request\":false},\n"
            + "\"user_state\":{\"state\":\"BLAMING\"}\n"
            + "}</turn_meta>";

        ChatTurnMetaParser.Result result = parser.parse(response, 4, "USER_A");

        assertNotNull(result.horsemen());
        assertEquals(0.5, result.horsemen().criticism);
        assertNotNull(result.nvc());
        assertTrue(result.nvc().observation);
        assertNotNull(result.userState());
        assertEquals(Session.UserState.BLAMING, result.userState().state);
    }
}
