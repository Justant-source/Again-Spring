package com.againspring.service.parser;

import com.againspring.domain.Session;
import com.againspring.service.context.IssueContextDelta;
import com.againspring.service.context.QuestionQueueDelta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Splits an LLM chat response into the user-facing text and an optional <turn_meta>
 * JSON block carrying 4 Horsemen / NVC completion scores. Tolerates a missing or
 * malformed meta block — the user text always reflows.
 */
@Slf4j
@Component
public class ChatTurnMetaParser {

    private static final Pattern META_BLOCK = Pattern.compile(
        "<turn_meta>\\s*(\\{.*?})\\s*</turn_meta>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // LLM이 <turn_meta>를 코드 펜스로 감싸고 </turn_meta>를 생략하는 경우 처리
    private static final Pattern META_BLOCK_CODE_FENCE = Pattern.compile(
        "```\\w*[\\r\\n]*<turn_meta>[\\r\\n]*(\\{.*?})[\\r\\n]*(?:</turn_meta>[\\r\\n]*)?```",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern WRAPPER_BLOCK = Pattern.compile(
        "<mediator_response>\\s*(.*?)\\s*</mediator_response>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // AI가 <turn_meta> 대신 독립 최상위 태그로 출력할 때 스트립 + 파싱
    private static final Pattern ISSUE_DELTA_BLOCK = Pattern.compile(
        "<issue_delta>\\s*(\\{.*?})\\s*</issue_delta>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern QUEUE_DELTA_BLOCK = Pattern.compile(
        "<question_queue_delta>\\s*(\\{.*?})\\s*</question_queue_delta>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // 방어적 마지막 패스: 파싱 후 남은 알려진 구조 태그 잔재 제거
    // mediator_response는 WRAPPER_BLOCK이 별도 처리하므로 제외
    private static final Pattern UNKNOWN_STRUCTURED_BLOCK = Pattern.compile(
        "<(turn_meta|issue_delta|question_queue_delta|user_state|horsemen|nvc_completion)[^>]*>.*?</\\1>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Result parse(String rawResponse, int turn, String senderTag) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new Result("", null, null, null, null, null);
        }

        String working = rawResponse;
        Session.HorsemenTurnEntry horsemen = null;
        Session.NvcTurnEntry nvc = null;

        Session.UserStateEntry userState = null;
        IssueContextDelta issueDelta = null;
        QuestionQueueDelta queueDelta = null;

        Matcher codeFenceMeta = META_BLOCK_CODE_FENCE.matcher(working);
        Matcher meta = META_BLOCK.matcher(working);
        String metaJson = null;
        if (codeFenceMeta.find()) {
            metaJson = codeFenceMeta.group(1);
            working = META_BLOCK_CODE_FENCE.matcher(working).replaceAll("").trim();
        } else if (meta.find()) {
            metaJson = meta.group(1);
            working = META_BLOCK.matcher(working).replaceAll("").trim();
        }
        if (metaJson != null) {
            try {
                JsonNode root = objectMapper.readTree(metaJson);
                horsemen = readHorsemen(root.get("horsemen"), turn, senderTag);
                nvc = readNvc(root.get("nvc_completion"), turn, senderTag);
                userState = readUserState(root.get("user_state"), turn, senderTag);
                issueDelta = readIssueDelta(root.get("issue_delta"));
                queueDelta = readQueueDelta(root.get("question_queue_delta"));
            } catch (Exception e) {
                log.warn("turn_meta JSON parse failed (turn={}): {}", turn, e.getMessage());
            }
        }

        // AI가 <turn_meta> 대신 독립 최상위 태그로 출력한 경우 스트립 + 파싱
        // (turn_meta 에서 이미 파싱된 값은 덮어쓰지 않음)
        Matcher issueMatcher = ISSUE_DELTA_BLOCK.matcher(working);
        if (issueMatcher.find()) {
            if (issueDelta == null) {
                try {
                    issueDelta = readIssueDelta(objectMapper.readTree(issueMatcher.group(1)));
                } catch (Exception e) {
                    log.warn("standalone issue_delta parse failed (turn={}): {}", turn, e.getMessage());
                }
            }
            working = ISSUE_DELTA_BLOCK.matcher(working).replaceAll("").trim();
        }

        Matcher queueMatcher = QUEUE_DELTA_BLOCK.matcher(working);
        if (queueMatcher.find()) {
            if (queueDelta == null) {
                try {
                    queueDelta = readQueueDelta(objectMapper.readTree(queueMatcher.group(1)));
                } catch (Exception e) {
                    log.warn("standalone question_queue_delta parse failed (turn={}): {}", turn, e.getMessage());
                }
            }
            working = QUEUE_DELTA_BLOCK.matcher(working).replaceAll("").trim();
        }

        // mediator_response 래퍼가 있으면 내부 텍스트 추출 (UNKNOWN_STRUCTURED_BLOCK 실행 전)
        Matcher wrapper = WRAPPER_BLOCK.matcher(working);
        if (wrapper.find()) {
            working = wrapper.group(1).trim();
        }

        // 방어적 마지막 패스: 파싱 후 남은 알려진 구조 태그 잔재 제거
        working = UNKNOWN_STRUCTURED_BLOCK.matcher(working).replaceAll("").trim();

        return new Result(working.strip(), horsemen, nvc, userState, issueDelta, queueDelta);
    }

    private Session.HorsemenTurnEntry readHorsemen(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.HorsemenTurnEntry e = new Session.HorsemenTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.criticism = readDouble(node, "criticism");
        e.contempt = readDouble(node, "contempt");
        e.defensiveness = readDouble(node, "defensiveness");
        e.stonewalling = readDouble(node, "stonewalling");
        return e;
    }

    private Session.NvcTurnEntry readNvc(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.NvcTurnEntry e = new Session.NvcTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.observation = readBool(node, "observation");
        e.feeling = readBool(node, "feeling");
        e.need = readBool(node, "need");
        e.request = readBool(node, "request");
        return e;
    }

    private IssueContextDelta readIssueDelta(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        IssueContextDelta d = new IssueContextDelta();
        JsonNode hl = node.get("headline");
        d.headline = (hl != null && !hl.isNull()) ? trimStr(hl.asText(), 50) : null;

        d.factsAdded = new ArrayList<>();
        JsonNode factsNode = node.get("facts_added");
        if (factsNode != null && factsNode.isArray()) {
            for (JsonNode fn : factsNode) {
                Session.IssueFact f = new Session.IssueFact();
                f.text = trimStr(textOf(fn, "text"), 80);
                f.source = textOf(fn, "source");
                f.contributesTo = ratioElementOf(fn, "contributesTo");
                if (f.text != null && !f.text.isBlank()) d.factsAdded.add(f);
            }
        }

        d.factsConfirmed = new ArrayList<>();
        JsonNode fcNode = node.get("facts_confirmed");
        if (fcNode != null && fcNode.isArray()) {
            for (JsonNode fc : fcNode) {
                if (fc.isTextual()) d.factsConfirmed.add(fc.asText());
            }
        }

        d.needsAdded = new ArrayList<>();
        JsonNode needsNode = node.get("needs_added");
        if (needsNode != null && needsNode.isArray()) {
            for (JsonNode nn : needsNode) {
                Session.NeedSlot n = new Session.NeedSlot();
                n.text = trimStr(textOf(nn, "text"), 60);
                n.owner = textOf(nn, "owner");
                n.contributesTo = ratioElementOf(nn, "contributesTo");
                if (n.text != null && !n.text.isBlank()) d.needsAdded.add(n);
            }
        }

        d.threadsAdded = new ArrayList<>();
        JsonNode threadsNode = node.get("threads_added");
        if (threadsNode != null && threadsNode.isArray()) {
            for (JsonNode tn : threadsNode) {
                Session.UnresolvedThread t = new Session.UnresolvedThread();
                t.text = trimStr(textOf(tn, "text"), 60);
                t.origin = textOf(tn, "origin");
                if (t.text != null && !t.text.isBlank()) d.threadsAdded.add(t);
            }
        }

        d.threadsResolved = new ArrayList<>();
        JsonNode trNode = node.get("threads_resolved");
        if (trNode != null && trNode.isArray()) {
            for (JsonNode tr : trNode) {
                if (tr.isTextual()) d.threadsResolved.add(tr.asText());
            }
        }

        return d;
    }

    private String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull() && v.isTextual()) ? v.asText() : null;
    }

    private Session.RatioElement ratioElementOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) return null;
        try {
            return Session.RatioElement.valueOf(v.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Session.UserStateEntry readUserState(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        JsonNode stateNode = node.get("state");
        if (stateNode == null || stateNode.isNull()) return null;
        Session.UserStateEntry e = new Session.UserStateEntry();
        e.turn = turn;
        e.sender = sender;
        try {
            e.state = Session.UserState.valueOf(stateNode.asText());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown UserState '{}' in turn_meta (turn={})", stateNode.asText(), turn);
            return null;
        }
        JsonNode evNode = node.get("evidence");
        e.evidenceSnippet = (evNode != null && !evNode.isNull()) ? trimStr(evNode.asText(), 30) : null;
        JsonNode confNode = node.get("confidence");
        e.confidence = (confNode != null && !confNode.isNull() && confNode.isNumber())
            ? Math.min(1.0, Math.max(0.0, confNode.asDouble())) : null;
        JsonNode derNode = node.get("derived_from");
        e.derivedFrom = (derNode != null && !derNode.isNull()) ? derNode.asText() : null;
        return e;
    }

    private String trimStr(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return 0.0;
        if (v.isNumber()) {
            double d = v.asDouble();
            if (d < 0) d = 0;
            if (d > 1) d = 1;
            return d;
        }
        return 0.0;
    }

    private Boolean readBool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return false;
        if (v.isBoolean()) return v.asBoolean();
        return false;
    }

    private QuestionQueueDelta readQueueDelta(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        QuestionQueueDelta d = new QuestionQueueDelta();

        d.asked = new ArrayList<>();
        JsonNode askedNode = node.get("asked");
        if (askedNode != null && askedNode.isArray()) {
            for (JsonNode a : askedNode) {
                if (a.isTextual()) d.asked.add(a.asText());
            }
        }

        d.newQuestions = new ArrayList<>();
        JsonNode newNode = node.get("new");
        if (newNode != null && newNode.isArray()) {
            for (JsonNode qn : newNode) {
                Session.PendingQuestion q = new Session.PendingQuestion();
                q.intent = intentOf(qn, "intent");
                q.target = textOf(qn, "target");
                q.text = trimStr(textOf(qn, "text"), 80);
                q.hookFromIssue = textOf(qn, "hookFromIssue");
                q.antidoteFor = ratioElementOf(qn, "antidoteFor");
                if (q.intent != null && q.target != null) d.newQuestions.add(q);
            }
        }

        return d;
    }

    private Session.Intent intentOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) return null;
        try {
            return Session.Intent.valueOf(v.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record Result(
        String mediatorMessage,
        Session.HorsemenTurnEntry horsemen,
        Session.NvcTurnEntry nvc,
        Session.UserStateEntry userState,
        IssueContextDelta issueDelta,
        QuestionQueueDelta queueDelta) {}
}
