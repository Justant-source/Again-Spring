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

    // 패턴은 PromptSchema 상수에서 파생 — 태그명 변경 시 PromptSchema만 수정할 것
    private static final Pattern META_BLOCK = Pattern.compile(
        "<" + PromptSchema.TAG_TURN_META + ">\\s*(\\{.*?})\\s*</" + PromptSchema.TAG_TURN_META + ">",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern META_BLOCK_CODE_FENCE = Pattern.compile(
        "```\\w*[\\r\\n]*<" + PromptSchema.TAG_TURN_META + ">[\\r\\n]*(\\{.*?})[\\r\\n]*(?:</"
            + PromptSchema.TAG_TURN_META + ">[\\r\\n]*)?```",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern WRAPPER_BLOCK = Pattern.compile(
        "<" + PromptSchema.TAG_MEDIATOR_RESPONSE + ">\\s*(.*?)\\s*</" + PromptSchema.TAG_MEDIATOR_RESPONSE + ">",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern ISSUE_DELTA_BLOCK = Pattern.compile(
        "<" + PromptSchema.TAG_ISSUE_DELTA + ">\\s*(\\{.*?})\\s*</" + PromptSchema.TAG_ISSUE_DELTA + ">",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern QUEUE_DELTA_BLOCK = Pattern.compile(
        "<" + PromptSchema.TAG_QUEUE_DELTA + ">\\s*(\\{.*?})\\s*</" + PromptSchema.TAG_QUEUE_DELTA + ">",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // 방어적 마지막 패스: PromptSchema.STRIP_TAG_ALTERNATION 에서 태그 목록 파생
    private static final Pattern UNKNOWN_STRUCTURED_BLOCK = Pattern.compile(
        "<(" + PromptSchema.STRIP_TAG_ALTERNATION + ")[^>]*>.*?</\\1>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // 닫는 태그 없이 잘린 구조 블록 방어: MAX_TOKENS 초과로 </turn_meta> 등이 누락되면
    // 위 패턴들이 못 잡는다. 여는 태그만 매칭해 그 지점부터 끝까지 제거 → JSON/메타 노출 차단.
    // (본문은 turn_meta 앞에 위치하므로 보존된다)
    private static final Pattern DANGLING_OPEN_TAG = Pattern.compile(
        "<(" + PromptSchema.STRIP_TAG_ALTERNATION + ")[^>]*>",
        Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Result parse(String rawResponse, int turn, String senderTag) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new Result("", null, null, null, null, null, null, null, null);
        }

        String working = rawResponse;
        Session.HorsemenTurnEntry horsemen = null;
        Session.NvcTurnEntry nvc = null;

        Session.UserStateEntry userState = null;
        IssueContextDelta issueDelta = null;
        QuestionQueueDelta queueDelta = null;
        // V47 신규
        List<String> inferredKeywords = null;
        String inferredTitle = null;
        String inferredKoreanTag = null;

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
                horsemen = readHorsemen(root.get(PromptSchema.FIELD_HORSEMEN), turn, senderTag);
                nvc = readNvc(root.get(PromptSchema.FIELD_NVC_COMPLETION), turn, senderTag);
                userState = readUserState(root.get(PromptSchema.FIELD_USER_STATE), turn, senderTag);
                issueDelta = readIssueDelta(root.get(PromptSchema.FIELD_ISSUE_DELTA));
                queueDelta = readQueueDelta(root.get(PromptSchema.FIELD_QUEUE_DELTA));
                // V47 신규 필드
                inferredKeywords = readStringList(root.get(PromptSchema.FIELD_INFERRED_KEYWORDS), 2);
                inferredTitle = readStringField(root, PromptSchema.FIELD_INFERRED_TITLE, 30);
                inferredKoreanTag = readStringField(root, PromptSchema.FIELD_INFERRED_KOREAN_TAG, 20);
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

        // 닫는 태그 없이 잘린 구조 블록 — 여는 태그부터 끝까지 잘라낸다 (응답이 MAX_TOKENS에서 잘린 경우).
        Matcher dangling = DANGLING_OPEN_TAG.matcher(working);
        if (dangling.find()) {
            working = working.substring(0, dangling.start()).strip();
        }

        return new Result(working.strip(), horsemen, nvc, userState, issueDelta, queueDelta,
                inferredKeywords, inferredTitle, inferredKoreanTag);
    }

    private Session.HorsemenTurnEntry readHorsemen(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.HorsemenTurnEntry e = new Session.HorsemenTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.criticism    = readDouble(node, PromptSchema.H_CRITICISM);
        e.contempt     = readDouble(node, PromptSchema.H_CONTEMPT);
        e.defensiveness = readDouble(node, PromptSchema.H_DEFENSIVENESS);
        e.stonewalling = readDouble(node, PromptSchema.H_STONEWALLING);
        return e;
    }

    private Session.NvcTurnEntry readNvc(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.NvcTurnEntry e = new Session.NvcTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.observation = readBool(node, PromptSchema.NVC_OBSERVATION);
        e.feeling     = readBool(node, PromptSchema.NVC_FEELING);
        e.need        = readBool(node, PromptSchema.NVC_NEED);
        e.request     = readBool(node, PromptSchema.NVC_REQUEST);
        return e;
    }

    private IssueContextDelta readIssueDelta(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        IssueContextDelta d = new IssueContextDelta();
        JsonNode hl = node.get(PromptSchema.ID_HEADLINE);
        d.headline = (hl != null && !hl.isNull()) ? trimStr(hl.asText(), 50) : null;

        d.factsAdded = new ArrayList<>();
        JsonNode factsNode = node.get(PromptSchema.ID_FACTS_ADDED);
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
        JsonNode fcNode = node.get(PromptSchema.ID_FACTS_CONFIRMED);
        if (fcNode != null && fcNode.isArray()) {
            for (JsonNode fc : fcNode) {
                if (fc.isTextual()) d.factsConfirmed.add(fc.asText());
            }
        }

        d.needsAdded = new ArrayList<>();
        JsonNode needsNode = node.get(PromptSchema.ID_NEEDS_ADDED);
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
        JsonNode threadsNode = node.get(PromptSchema.ID_THREADS_ADDED);
        if (threadsNode != null && threadsNode.isArray()) {
            for (JsonNode tn : threadsNode) {
                Session.UnresolvedThread t = new Session.UnresolvedThread();
                t.text = trimStr(textOf(tn, "text"), 60);
                t.origin = textOf(tn, "origin");
                if (t.text != null && !t.text.isBlank()) d.threadsAdded.add(t);
            }
        }

        d.threadsResolved = new ArrayList<>();
        JsonNode trNode = node.get(PromptSchema.ID_THREADS_RESOLVED);
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
        JsonNode stateNode = node.get(PromptSchema.US_STATE);
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
        JsonNode evNode = node.get(PromptSchema.US_EVIDENCE);
        e.evidenceSnippet = (evNode != null && !evNode.isNull()) ? trimStr(evNode.asText(), 30) : null;
        JsonNode confNode = node.get(PromptSchema.US_CONFIDENCE);
        e.confidence = (confNode != null && !confNode.isNull() && confNode.isNumber())
            ? Math.min(1.0, Math.max(0.0, confNode.asDouble())) : null;
        JsonNode derNode = node.get(PromptSchema.US_DERIVED_FROM);
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
        JsonNode askedNode = node.get(PromptSchema.QD_ASKED);
        if (askedNode != null && askedNode.isArray()) {
            for (JsonNode a : askedNode) {
                if (a.isTextual()) d.asked.add(a.asText());
            }
        }

        d.newQuestions = new ArrayList<>();
        JsonNode newNode = node.get(PromptSchema.QD_NEW);
        if (newNode != null && newNode.isArray()) {
            for (JsonNode qn : newNode) {
                Session.PendingQuestion q = new Session.PendingQuestion();
                q.intent = intentOf(qn, PromptSchema.QD_INTENT);
                q.target = textOf(qn, PromptSchema.QD_TARGET);
                q.text = trimStr(textOf(qn, PromptSchema.QD_TEXT), 80);
                q.hookFromIssue = textOf(qn, PromptSchema.QD_HOOK_FROM_ISSUE);
                q.antidoteFor = ratioElementOf(qn, PromptSchema.QD_ANTIDOTE_FOR);
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

    // ── V47 신규 헬퍼 ────────────────────────────────────────────────────────

    private List<String> readStringList(JsonNode node, int maxItems) {
        if (node == null || !node.isArray()) return null;
        List<String> result = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(trimStr(item.asText(), 20));
                if (result.size() >= maxItems) break;
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String readStringField(JsonNode root, String field, int maxLen) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) return null;
        String val = node.asText().strip();
        return val.isBlank() ? null : trimStr(val, maxLen);
    }

    public record Result(
        String mediatorMessage,
        Session.HorsemenTurnEntry horsemen,
        Session.NvcTurnEntry nvc,
        Session.UserStateEntry userState,
        IssueContextDelta issueDelta,
        QuestionQueueDelta queueDelta,
        // V47 신규
        List<String> inferredKeywords,
        String inferredTitle,
        String inferredKoreanTag) {}
}
