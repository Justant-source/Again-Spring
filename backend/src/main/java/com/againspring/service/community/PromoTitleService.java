package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.community.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SNS 마스터 훅(+감정) 생성 — 제목·본문에서 도발적 훅을 새로 쓴다.
 * 사연 생성 시 1회만 호출 — 발행 파이프에서 추가 LLM 없음.
 * PLAN이 promo_title을 이미 넣으면 skip.
 * IG 훅 카드용 의미줄바꿈(\\n) 패킹 헬퍼는 유지(원제 글자 동일성 강제 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoTitleService {

    /** 저장 상한 (개행 포함). */
    public static final int MAX_STORE_LEN = 500;
    /** IG 훅 한 줄 최대 글자 수 (공백 포함). */
    public static final int MAX_LINE_LEN = 10;
    /** 한 줄 목표 하한 — 1음절 단독 줄 방지. */
    public static final int MIN_LINE_LEN = 2;
    /** 패킹 시 줄바꿈을 고려하는 목표 길이. */
    public static final int TARGET_LINE_LEN = 7;

    /** 허용 hook_emotion 값. */
    public static final Set<String> HOOK_EMOTIONS = Set.of(
            "shock", "anger", "tension", "sad", "hype");

    private static final Pattern WS = Pattern.compile("\\s+");
    /** SNS 훅에는 구분자 슬래시를 쓰지 않는다. 전각 슬래시도 함께 정리한다. */
    private static final Pattern SLASH_SEPARATORS = Pattern.compile("[/／]");
    private static final int BODY_PROMPT_MAX = 800;

    /** LLM/정규화 결과. emotion은 검증 실패 시 null. */
    public record HookResult(String promoTitle, String hookEmotion) {}

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Value("${llm.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${promo-title.enabled:true}")
    private boolean enabled;

    /**
     * 저장된 promo_title(개행 유지) 우선. 없으면 원제 휴리스틱 줄바꿈.
     * 글자 수 강제 컷 없음(저장 상한만).
     */
    public static String resolveOrFallback(Post post) {
        if (post == null) return "";
        String promo = post.getPromoTitle();
        if (promo != null && !promo.isBlank()) {
            return normalizeHook(promo);
        }
        String base = post.getTitle() != null && !post.getTitle().isBlank()
                ? post.getTitle()
                : post.getUserTitle();
        return wrapSemantic(base != null ? base.trim() : "");
    }

    /**
     * emotion 정규화. 허용 집합 밖·blank → null.
     */
    public static String validateEmotion(String emotion) {
        if (emotion == null || emotion.isBlank()) return null;
        String e = emotion.trim().toLowerCase(Locale.ROOT);
        return HOOK_EMOTIONS.contains(e) ? e : null;
    }

    @Async("taskExecutor")
    public void generateAsync(String postId) {
        if (!enabled || postId == null || postId.isBlank()) return;
        try {
            generateAndSave(postId);
        } catch (Exception e) {
            log.warn("PromoTitle generation failed for {}: {}", postId, e.getMessage());
        }
    }

    @Transactional
    public void generateAndSave(String postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;
        if (post.getPromoTitle() != null && !post.getPromoTitle().isBlank()) {
            return; // already set — one-shot (PLAN may have provided it)
        }

        String title = post.getTitle() != null && !post.getTitle().isBlank()
                ? post.getTitle()
                : post.getUserTitle();
        if (title == null) title = "";
        String body = post.getBodyPublished() != null && !post.getBodyPublished().isBlank()
                ? post.getBodyPublished()
                : post.getBodyRaw();
        if (body == null) body = "";

        HookResult generated = generate(title, body);
        String promo;
        String emotion;
        if (generated == null || generated.promoTitle() == null || generated.promoTitle().isBlank()) {
            promo = wrapSemantic(title);
            emotion = null;
        } else {
            promo = normalizeHook(generated.promoTitle());
            if (promo == null || promo.isBlank()) {
                promo = wrapSemantic(title);
            }
            emotion = validateEmotion(generated.hookEmotion());
        }

        int updated = postRepository.updatePromoTitleIfAbsent(postId, promo, emotion);
        if (updated == 0) {
            log.info("PromoTitle skip (post gone or already set): {}", postId);
            return;
        }
        log.info("PromoTitle saved for {}: '{}' emotion={}",
                postId, promo.replace("\n", "\\n"), emotion);
    }

    /**
     * LLM으로 마스터 훅+감정 생성. 실패 시 null.
     */
    public HookResult generate(String title, String body) {
        if (!enabled) return null;
        try {
            String prompt = buildPrompt(title, body);
            String result = llmProvider.invoke(prompt, model);
            return parseResult(result);
        } catch (Exception e) {
            log.warn("PromoTitle LLM failed: {}", e.getMessage());
            return null;
        }
    }

    /** @deprecated prefer {@link #generate(String, String)} */
    @Deprecated
    public String generate(String title) {
        HookResult r = generate(title, "");
        return r != null ? r.promoTitle() : null;
    }

    private String buildPrompt(String title, String body) {
        String safeTitle = promptSanitizer.sanitize(title != null ? title : "");
        String rawBody = body != null ? body : "";
        if (rawBody.length() > BODY_PROMPT_MAX) {
            rawBody = rawBody.substring(0, BODY_PROMPT_MAX);
        }
        String safeBody = promptSanitizer.sanitize(rawBody);

        return """
            당신은 SNS(인스타·X)용 **마스터 훅** 카피라이터입니다.
            사연 제목·본문을 보고, 클릭을 유도하는 **도발적·호기심 자극** 훅을 새로 작성하세요.
            원제 글자를 복제하지 마세요. 재작성·압축·비틀기가 핵심입니다.

            ## 규칙
            - 훅은 한국어. 짧고 강렬하게. 필요 시 의미 단위 줄바꿈(\\n) — IG 훅 카드용.
            - **본문 속 구체적 사실(기간·나이·금액·횟수 등 숫자)을 첫머리에 놓고, 그 직후에 모순·반전을 붙이세요.**
              "진짜"·"완전"·"너무" 같은 감정 형용사로 긴장을 만들지 말고 사실 자체로 만드세요.
              예(형식 참고용, 실제 사연 아님): "9년 사귄 사람이 결혼 얘기 나오자 혼자 여행부터 갑니다."
            - 각 줄은 공백 포함 **4~10자**가 이상적. **최대 10자**. 1음절만 단독 줄로 두지 말 것.
            - 이모지·해시태그·따옴표 장식·슬래시(/, ／) 금지.
            - 판결/처방/승패/유무죄 표현 금지. 가해자·피해자 단정 금지.
            - 「배심원」 단어 사용 금지.
            - hook_emotion은 다음 중 **정확히 하나**: shock | anger | tension | sad | hype

            <user_input>
            제목: %s
            본문: %s
            </user_input>

            ## 출력 (JSON only)
            {"promo_title": "도발적\\\\n마스터\\\\n훅", "hook_emotion": "shock"}
            """.formatted(safeTitle, safeBody);
    }

    private HookResult parseResult(String jsonResult) {
        try {
            String json = TonalizationService.extractJsonObject(jsonResult);
            JsonNode root = objectMapper.readTree(json);
            String promo = root.path("promo_title").asText(null);
            if (promo == null || promo.isBlank()) {
                promo = root.path("promoTitle").asText(null);
            }
            if (promo == null || promo.isBlank()) return null;
            promo = promo.trim()
                    .replace("\\n", "\n")
                    .replaceAll("^[\"']+|[\"']+$", "");
            String emotion = root.path("hook_emotion").asText(null);
            if (emotion == null || emotion.isBlank()) {
                emotion = root.path("hookEmotion").asText(null);
            }
            return new HookResult(normalizeHook(promo), validateEmotion(emotion));
        } catch (Exception e) {
            log.debug("PromoTitle parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 훅 텍스트 IG 패킹 — 줄별 ≤10, orphan 병합. **원제 글자 동일성 강제 없음.**
     */
    static String normalizeHook(String promo) {
        if (promo == null || promo.isBlank()) return "";

        String cleaned = SLASH_SEPARATORS.matcher(promo).replaceAll(" ");
        String[] rawLines = cleaned.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.length() <= MAX_LINE_LEN) {
                lines.add(t);
            } else {
                lines.addAll(hardWrap(t, MAX_LINE_LEN));
            }
        }
        if (lines.isEmpty()) return "";
        if (hasTooManyOrphans(lines)) {
            // re-pack from collapsed text (no title equality)
            return wrapSemantic(String.join(" ", lines));
        }
        lines = mergeOrphans(lines);
        return clampStore(String.join("\n", lines));
    }

    /**
     * @deprecated use {@link #normalizeHook(String)}; title equality no longer enforced.
     * blank promo → wrapSemantic(title) fallback for compose callers.
     */
    @Deprecated
    static String normalizeAgainstTitle(String promo, String title) {
        if (promo == null || promo.isBlank()) {
            return wrapSemantic(title != null ? title : "");
        }
        return normalizeHook(promo);
    }

    /** 1자 줄이 전체의 25% 이상이면 나쁜 줄바꿈으로 본다. */
    static boolean hasTooManyOrphans(List<String> lines) {
        if (lines == null || lines.isEmpty()) return false;
        int orphans = 0;
        for (String line : lines) {
            if (line != null && line.length() < MIN_LINE_LEN) orphans++;
        }
        return orphans > 0 && orphans * 4 >= lines.size();
    }

    /**
     * 어절을 모아 4~10자 줄로 패킹. 1음절 단독 줄·공백마다 끊기 방지.
     */
    static String wrapSemantic(String title) {
        if (title == null || title.isBlank()) return "";
        String t = SLASH_SEPARATORS.matcher(title).replaceAll(" ").trim();
        if (t.length() <= MAX_LINE_LEN) return clampStore(t);

        List<String> tokens = tokenize(t);
        if (tokens.isEmpty()) return clampStore(String.join("\n", hardWrap(t, MAX_LINE_LEN)));

        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (cur.length() == 0) {
                if (token.length() <= MAX_LINE_LEN) {
                    cur.append(token);
                } else {
                    lines.addAll(hardWrap(token, MAX_LINE_LEN));
                }
                continue;
            }
            String candidate = cur + token;
            // collapse double spaces from leading token spaces
            candidate = candidate.replaceAll(" {2,}", " ");
            if (candidate.length() <= MAX_LINE_LEN) {
                // Prefer packing until near target; still allow shorter if next would overflow later
                cur.setLength(0);
                cur.append(candidate.trim());
            } else if (cur.length() >= MIN_LINE_LEN) {
                lines.add(cur.toString().trim());
                cur.setLength(0);
                if (token.length() <= MAX_LINE_LEN) {
                    cur.append(token.trim());
                } else {
                    lines.addAll(hardWrap(token.trim(), MAX_LINE_LEN));
                }
            } else {
                // current too short — force attach even if slightly over, then hard-wrap
                String merged = (cur + token).trim().replaceAll(" {2,}", " ");
                lines.addAll(packOverflow(merged));
                cur.setLength(0);
            }
            // If current line already reached target and next tokens exist, flush at soft boundary
            if (cur.length() >= TARGET_LINE_LEN && cur.length() <= MAX_LINE_LEN) {
                // keep packing more if room remains — only flush when next token can't fit
            }
        }
        if (!cur.isEmpty()) {
            String last = cur.toString().trim();
            if (!last.isEmpty()) {
                if (last.length() < MIN_LINE_LEN && !lines.isEmpty()) {
                    String prev = lines.remove(lines.size() - 1);
                    String merged = (prev + " " + last).trim().replaceAll(" {2,}", " ");
                    if (merged.length() <= MAX_LINE_LEN) {
                        lines.add(merged);
                    } else {
                        lines.add(prev);
                        lines.addAll(hardWrap(last, MAX_LINE_LEN));
                    }
                } else {
                    lines.addAll(packOverflow(last));
                }
            }
        }
        // merge remaining 1-char orphans into neighbors
        lines = mergeOrphans(lines);
        if (lines.isEmpty()) return clampStore(String.join("\n", hardWrap(t, MAX_LINE_LEN)));
        return clampStore(String.join("\n", lines));
    }

    static List<String> tokenize(String title) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!buf.isEmpty()) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                // keep a single leading space marker on next non-space via separate space token
                out.add(" ");
            } else if (c == ',' || c == '，' || c == '、' || c == '…' || c == '.' || c == '·'
                    || c == '?' || c == '？' || c == '!' || c == '！') {
                buf.append(c);
                out.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) out.add(buf.toString());
        // drop leading/trailing pure-space tokens; collapse consecutive spaces
        List<String> cleaned = new ArrayList<>();
        for (String tok : out) {
            if (" ".equals(tok)) {
                if (!cleaned.isEmpty() && !" ".equals(cleaned.get(cleaned.size() - 1))) {
                    cleaned.add(" ");
                }
            } else if (!tok.isBlank()) {
                cleaned.add(tok);
            }
        }
        while (!cleaned.isEmpty() && " ".equals(cleaned.get(0))) cleaned.remove(0);
        while (!cleaned.isEmpty() && " ".equals(cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }
        return cleaned;
    }

    static List<String> packOverflow(String s) {
        if (s == null || s.isBlank()) return List.of();
        if (s.length() <= MAX_LINE_LEN) return List.of(s);
        return hardWrap(s, MAX_LINE_LEN);
    }

    static List<String> mergeOrphans(List<String> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (t.isEmpty()) continue;
            if (t.length() < MIN_LINE_LEN && !out.isEmpty()) {
                String prev = out.get(out.size() - 1);
                String merged = (prev + " " + t).trim().replaceAll(" {2,}", " ");
                if (merged.length() <= MAX_LINE_LEN) {
                    out.set(out.size() - 1, merged);
                } else {
                    out.add(t);
                }
            } else {
                out.add(t);
            }
        }
        // forward-merge leading orphan into next if still orphan at start
        if (out.size() >= 2 && out.get(0).length() < MIN_LINE_LEN) {
            String merged = (out.get(0) + " " + out.get(1)).trim().replaceAll(" {2,}", " ");
            if (merged.length() <= MAX_LINE_LEN) {
                out.set(1, merged);
                out.remove(0);
            }
        }
        return out;
    }

    static List<String> hardWrap(String s, int max) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(i + max, s.length());
            out.add(s.substring(i, end));
            i = end;
        }
        return out;
    }

    static String collapseWs(String s) {
        if (s == null) return "";
        return WS.matcher(s.trim()).replaceAll("");
    }

    static String clampStore(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= MAX_STORE_LEN) return t;
        return t.substring(0, MAX_STORE_LEN);
    }

    /** @deprecated use clampStore / wrapSemantic */
    @Deprecated
    static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}
