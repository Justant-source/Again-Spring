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
import java.util.regex.Pattern;

/**
 * IG 훅 제목 생성 — 원제 복제 + 의미단위 줄바꿈(\\n).
 * 사연 생성 시 1회만 호출 — 발행 파이프에서 추가 LLM 없음.
 * PLAN이 promo_title을 이미 넣으면 skip.
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

    private static final Pattern WS = Pattern.compile("\\s+");

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
            return clampStore(promo.trim());
        }
        String base = post.getTitle() != null && !post.getTitle().isBlank()
                ? post.getTitle()
                : post.getUserTitle();
        return wrapSemantic(base != null ? base.trim() : "");
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
        String generated = generate(title);
        if (generated == null || generated.isBlank()) {
            generated = wrapSemantic(title);
        }
        String promo = normalizeAgainstTitle(generated, title);

        int updated = postRepository.updatePromoTitleIfAbsent(postId, promo);
        if (updated == 0) {
            log.info("PromoTitle skip (post gone or already set): {}", postId);
            return;
        }
        log.info("PromoTitle saved for {}: '{}'", postId, promo.replace("\n", "\\n"));
    }

    /**
     * LLM으로 원제 복제 + 의미 줄바꿈. 실패 시 null.
     */
    public String generate(String title) {
        if (!enabled) return null;
        try {
            String prompt = buildPrompt(title);
            String result = llmProvider.invoke(prompt, model);
            return parseResult(result, title);
        } catch (Exception e) {
            log.warn("PromoTitle LLM failed: {}", e.getMessage());
            return null;
        }
    }

    /** @deprecated body unused — kept for any leftover callers */
    @Deprecated
    public String generate(String title, String body) {
        return generate(title);
    }

    private String buildPrompt(String title) {
        String safeTitle = promptSanitizer.sanitize(title != null ? title : "");

        return """
            당신은 인스타그램 피드 1장(훅 카드)용 줄바꿈 편집자입니다.
            사연 **원제 글자를 그대로** 두고, 의미 있는 구 단위로만 줄바꿈(\\n)을 넣으세요.

            ## 규칙
            - 원제와 **글자 내용 동일** (개행·공백 정규화 후 비교). 글자 생략·추가·재작성 금지.
            - 각 줄은 공백 포함 **4~10자**가 이상적. **최대 10자**. 1음절(한 글자)만 단독 줄로 두지 말 것.
            - 공백마다 끊지 말 것. 어절 1~3개를 모아 **보기 좋은 짧은 구**로 한 줄을 만들 것.
              예) "왜 / 말 / 안" (X) → "왜 만나자는 말" / "안 하냐고" (O)
            - 줄바꿈은 의미 단위(구·절·조사 묶음). 단어를 어중간히 쪼개지 말 것.
            - 줄 수 제한 없음. 원제 전부 포함.
            - 이모지·해시태그·따옴표 추가 금지. 판결/처방/승패 표현 추가 금지.

            <user_input>
            원제: %s
            </user_input>

            ## 출력 (JSON only)
            {"promo_title": "의미구\\\\n줄바꿈\\\\n원제"}
            """.formatted(safeTitle);
    }

    private String parseResult(String jsonResult, String originalTitle) {
        try {
            String json = TonalizationService.extractJsonObject(jsonResult);
            JsonNode root = objectMapper.readTree(json);
            String promo = root.path("promo_title").asText(null);
            if (promo == null || promo.isBlank()) return null;
            // JSON may contain literal \n sequences
            promo = promo.trim()
                    .replace("\\n", "\n")
                    .replaceAll("^[\"']+|[\"']+$", "");
            return normalizeAgainstTitle(promo, originalTitle);
        } catch (Exception e) {
            log.debug("PromoTitle parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 개행 제거 후 원제와 같으면 줄별 ≤10 검증. 1음절 단독 줄이 많으면 휴리스틱으로 재포장.
     */
    static String normalizeAgainstTitle(String promo, String title) {
        String base = title != null ? title.trim() : "";
        if (base.isEmpty()) return "";
        if (promo == null || promo.isBlank()) return wrapSemantic(base);

        String collapsedPromo = collapseWs(promo.replace("\n", ""));
        String collapsedTitle = collapseWs(base);
        if (!collapsedPromo.equals(collapsedTitle)) {
            return wrapSemantic(base);
        }

        String[] rawLines = promo.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
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
        if (lines.isEmpty()) return wrapSemantic(base);
        if (hasTooManyOrphans(lines)) {
            return wrapSemantic(base);
        }
        String joined = String.join("\n", lines);
        if (!collapseWs(joined.replace("\n", "")).equals(collapsedTitle)) {
            return wrapSemantic(base);
        }
        return clampStore(joined);
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
        String t = title.trim();
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
