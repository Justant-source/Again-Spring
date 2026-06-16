package com.againspring.aiuser.llm.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputSanitizer {
    // backend 사연 본문 @Size(max=1000)와 일치 — 초과분은 문장 경계로 컷해 게시 거부(400) 방지
    private static final int MAX_POST = 1000;
    private static final int MAX_COMMENT = 300;

    // LLM이 여러 옵션을 제시할 때 첫 번째 옵션만 추출
    private static final Pattern MULTI_OPTION = Pattern.compile(
        "(?:옵션\\s*[1-9]|선택지\\s*[1-9]|버전\\s*[1-9])[^:：]*[：:]?\\s*[`\"]*(.*?)[`\"]*\\s*(?=(?:옵션|선택지|버전)\\s*[2-9]|$)",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    // 첫 코드블록(```) 안의 내용 추출
    private static final Pattern CODE_BLOCK = Pattern.compile("```[^\n]*\n?(.*?)```", Pattern.DOTALL);
    // 수평 구분선 (--- 단독 줄) — Sonnet의 "제목\n---\n본문" 분리에 쓰임
    private static final Pattern HR_LINE = Pattern.compile("\n[ \\t]*-{3,}[ \\t]*\n");
    // AI 메타 응답 패턴 (앞부분에서만 제거)
    private static final Pattern LEADING_META = Pattern.compile(
        "^(?:안녕하세요[,!. ]*|물론이죠[,. ]*|물론입니다[,. ]*|네,? 저는 [^\n]*\n?|제가 도와드릴게요[,. ]*" +
        "|앞 댓글[의에]? [^\\n]{0,30}없어서[,\\s]*[^\n]*\n?" +
        "|[^\n]{0,20}몇 가지[를를]? 제안[해드릴게알려][^\n]*\n?" +
        "|[^\n]{0,20}제안[해드릴게알려][^\n]*\n?)",
        Pattern.CASE_INSENSITIVE
    );

    // ── 커뮤니티별 분포 매칭 설정 (Step 6) ──────────────────────────────────────
    private record VoiceDistribution(double targetCommaRate, boolean chosungInject,
                                     String[] chosungPhrases, double sampleProb) {}

    private static final java.util.Map<String, VoiceDistribution> VOICE_DIST;
    static {
        VOICE_DIST = new java.util.HashMap<>();
        VOICE_DIST.put("NATEPAN",  new VoiceDistribution(0.011, false, null, 0.70));
        VOICE_DIST.put("DCINSIDE", new VoiceDistribution(0.030, true,
            new String[]{"ㄹㅇ","ㅇㅈ","ㄷㄷ","ㅋㅋ"}, 0.80));
        VOICE_DIST.put("BLIND",    new VoiceDistribution(0.015, false, null, 0.60));
        VOICE_DIST.put("GENERAL",  new VoiceDistribution(0.015, false, null, 0.50));
        VOICE_DIST.put("FMKOREA",  new VoiceDistribution(0.015, true,
            new String[]{"ㄹㅇㅋㅋ","ㄷㄷ","ㅇㅈ","후추"}, 0.80));
        VOICE_DIST.put("RULIWEB",  new VoiceDistribution(0.018, false, null, 0.60));
        VOICE_DIST.put("THEQOO",   new VoiceDistribution(0.011, true,
            new String[]{"헐","ㅠㅠ","ㄷㄷ","개공감"}, 0.75));
        VOICE_DIST.put("ARCALIVE", new VoiceDistribution(0.015, true,
            new String[]{"ㄹㅇ","ㄱㄱ","ㅇㅇ","어쩔"}, 0.80));
        VOICE_DIST.put("INVEN",    new VoiceDistribution(0.015, false, null, 0.60));
        VOICE_DIST.put("MLBPARK",  new VoiceDistribution(0.020, false, null, 0.50));
        VOICE_DIST.put("PPOMPPU",  new VoiceDistribution(0.015, false, null, 0.55));
        VOICE_DIST.put("CLIEN",    new VoiceDistribution(0.022, false, null, 0.60));
    }
    private static final java.util.Random DIST_RNG = new java.util.Random();

    public String sanitizePost(String raw) {
        return sanitize(raw, MAX_POST);
    }

    public String sanitizeComment(String raw) {
        return sanitize(raw, MAX_COMMENT);
    }

    public String sanitizePost(String raw, String voiceType) {
        String base = sanitize(raw, MAX_POST);
        return applyDist(base, voiceType, true);
    }

    public String sanitizeComment(String raw, String voiceType) {
        String base = sanitize(raw, MAX_COMMENT);
        // N6: allowChosung=true — VOICE_DIST.chosungInject 값이 voice별 주입 여부를 결정
        // (이전: false 하드코딩 → DCINSIDE/THEQOO/FMKOREA/ARCALIVE 댓글 초성체 완전 차단)
        return applyDist(base, voiceType, true);
    }

    private String applyDist(String text, String voiceType, boolean allowChosung) {
        if (voiceType == null || text.isBlank()) return text;
        VoiceDistribution dist = VOICE_DIST.get(voiceType.toUpperCase());
        if (dist == null) return text;
        if (DIST_RNG.nextDouble() > dist.sampleProb()) return text;
        String s = normalizeCommaRate(text, dist.targetCommaRate());
        if (allowChosung && dist.chosungInject() && dist.chosungPhrases() != null) {
            s = injectChosung(s, dist.chosungPhrases());
        }
        return s;
    }

    private String normalizeCommaRate(String text, double targetRate) {
        long commaCount = text.chars().filter(c -> c == ',').count();
        if (text.isEmpty() || (double) commaCount / text.length() <= targetRate * 1.5) return text;
        int targetCommas = (int) (text.length() * targetRate);
        int toRemove = (int) commaCount - targetCommas;
        if (toRemove <= 0) return text;
        StringBuilder sb = new StringBuilder(text);
        int removed = 0;
        for (int i = sb.length() - 1; i >= 0 && removed < toRemove; i--) {
            if (sb.charAt(i) == ',' && DIST_RNG.nextBoolean()) {
                sb.deleteCharAt(i);
                removed++;
            }
        }
        return sb.toString();
    }

    private String injectChosung(String text, String[] phrases) {
        String[] lines = text.split("[\\n\\r]+");
        if (lines.length < 2) return text;
        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().length() > 10) candidates.add(i);
        }
        if (candidates.isEmpty()) return text;
        int lineIdx = candidates.get(DIST_RNG.nextInt(candidates.size()));
        String phrase = phrases[DIST_RNG.nextInt(phrases.length)];
        lines[lineIdx] = lines[lineIdx] + " " + phrase;
        return String.join("\n", lines);
    }

    // LLM이 콘텐츠 대신 질문하거나 생성 거부하는 패턴 — 빈 문자열 반환 → ActionExecutor FAILED 처리
    private static final Pattern META_RESPONSE = Pattern.compile(
        "(?:원댓글|댓글|글|내용)[의을를]?\\s*(?:구체적|원문|실제).*?알려|" +
        "알려줄\\s*수\\s*있[음어]|" +
        "자연스러운\\s*대댓글을\\s*쓸\\s*수\\s*없|" +
        "어떤\\s*상황인지\\s*모르|" +
        "좀\\s*더\\s*알아야|" +
        "포맷\\s*오류|" +
        "입력.*?다시|" +
        "아직\\s*취업도\\s*못\\s*했는데.*?노후.*?책임.*?대댓글",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private String sanitize(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw;

        // 0. AI 메타 응답 감지 → 즉시 빈 문자열 (ActionExecutor FAILED → 스킵)
        if (META_RESPONSE.matcher(s).find()) return "";

        // 1. 멀티 옵션 패턴 감지: "옵션 1 ... 옵션 2 ..." → 첫 번째 옵션만 추출
        if (s.contains("옵션 1") || s.contains("옵션1") || s.contains("선택지 1") || s.contains("버전 1")) {
            s = extractFirstOption(s);
        }

        // 2. 코드블록(```) 처리: 블록 안 내용 꺼내기
        Matcher codeMatcher = CODE_BLOCK.matcher(s);
        if (codeMatcher.find()) {
            String inside = codeMatcher.group(1).trim();
            if (!inside.isBlank()) {
                s = inside;
            } else {
                s = CODE_BLOCK.matcher(s).replaceAll("");
            }
        }
        // 단일 백틱 인라인 코드 제거
        s = s.replaceAll("`([^`]+)`", "$1");

        // 3. "---" 구분선 처리 (2026-06-11 수정)
        // Sonnet은 "제목\n\n---\n\n본문" 형태로 쓰는 습관 → 무조건 이후 삭제하면 본문 전체가 날아감.
        // 첫 구분선 뒤가 충분히 길면(본문) 구분선만 제거하고 보존, 짧으면(AI가 덧붙인 메타) 뒤를 삭제.
        Matcher hr = HR_LINE.matcher(s);
        if (hr.find()) {
            String before = s.substring(0, hr.start()).stripTrailing();
            String after  = s.substring(hr.end()).stripLeading();
            s = after.length() >= 40 ? (before + "\n\n" + after) : before;
        }
        // 본문 중간에 남은 구분선은 빈 줄로 정리
        s = s.replaceAll("(?m)^[ \\t]*-{3,}[ \\t]*$", "").replaceAll("\n{3,}", "\n\n");

        // 마크다운 제거 + 분석 줄 제거
        s = s.replaceAll("(?m)^#{1,6}\\s+", "")
             .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
             .replaceAll("(?<![\\w가-힣])\\*([^*\\n]+)\\*(?![\\w가-힣])", "$1")
             .replaceAll("(?m)^>\\s*", "")
             .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
             // 분석/평가 줄 제거 (✅, ❌, -, •로 시작하는 분석 항목)
             .replaceAll("(?m)^\\s*[✅❌📌🔍\\-•]\\s+(?:반말|존댓말|공감|분석|평가|자연스|어색|길이|[0-9]+점)[^\n]*\n?", "")
             .trim();

        // 4. AI 메타 응답 선두 제거
        s = LEADING_META.matcher(s).replaceFirst("").stripLeading();
        s = s.trim();

        // 4.5 한국 커뮤니티 문체 후처리 — 프롬프트 우회 방어
        // 줄 끝 온점 제거: "했음.\n" → "했음\n", 단 "..." "!." "?." 말줄임/복합부호는 유지
        s = s.replaceAll("(?<![.!?])\\. *(\n|$)", "$1");
        // 문자열 끝 온점 제거
        s = s.replaceAll("(?<![.!?])\\. *$", "");
        // 쌍따옴표 → 제거 (안의 내용은 유지, 인용 그대로)
        s = s.replaceAll("\"([^\"\\n]{1,60})\"", "$1");

        // 5. 후행 AI 말투 제거 ("어떤 톤으로 반응하고 싶은지 알려주면..." 류)
        s = s.replaceAll("(?s)\n+(?:어[떤떻]\\s*[^\n]*알려|더\\s*정확하게|앞\\s*댓글\\s*내용을\\s*보여)[^\n]*$", "").trim();

        // 6. 길이 컷 (문장 경계)
        if (s.length() > maxLen) {
            int cutAt = maxLen;
            String endings = ".!?\nㅋㅠ";
            for (int i = maxLen - 1; i >= Math.max(0, maxLen - 60); i--) {
                char c = s.charAt(i);
                if (endings.indexOf(c) >= 0) { cutAt = i + 1; break; }
            }
            s = s.substring(0, cutAt).stripTrailing();
        }

        // 7. 불완전 종결 감지 및 정리
        // 한국어 연결어미/관형어미로 끝나면 마지막 완결된 문장 단위까지 자름
        s = trimIncompleteEnding(s);

        return s;
    }

    // 한국어 자연 종결어미 — 평서/의문/반말/존댓말 (단어 경계 (?![가-힣]) 로 "화요일"의 "요" 등 오매칭 방지).
    // ㅆ음 계열(었음/왔음/이었음 등)·다고/라고·는데/은데·거야/건가 등을 포함해 정상 종결을 불완전으로 오판하지 않게 함.
    private static final String ENDING_ALT =
        "거든|거임|거야|건가|건지|더라|" +
        "했음|있음|없음|겠음|었음|았음|였음|왔음|갔음|봤음|났음|졌음|줬음|썼음|" +
        "했어|았어|었어|였어|왔어|갔어|봤어|났어|졌어|줬어|썼어|" +
        "임|함|됨|맞음|좋음|나쁨|싶음|싶어|" +
        "다고|라고|냐고|는데|은데|" +
        "잖아|이야|아니야|는가|을까|ㄹ까|구나|" +
        "해요|이에요|예요|아요|어요|네요|세요|데요|까요|거예요|죠|요";
    /** 마침표·물음표·느낌표·ㅋ·ㅠ 또는 한국어 종결어미로 끝나는지 (단어 경계 적용). */
    private static final Pattern COMPLETE_ENDING = Pattern.compile(
        "(?s).*(?:[.?!]|ㅋ+|ㅠ+|(?:" + ENDING_ALT + ")(?![가-힣]))$");
    /** 텍스트 중간/끝의 종결 위치 탐색용 (trim 시 마지막 완결점 찾기). */
    private static final Pattern ENDING_FINDER = Pattern.compile(
        "[.?!]|ㅋ+|ㅠ+|(?:" + ENDING_ALT + ")(?![가-힣])");

    /**
     * 텍스트가 불완전한 어미로 끝날 때 마지막 완결된 종결까지 잘라냄.
     * 예: "이게 맞은" → 불완전 → 앞의 자연스러운 종결까지 유지.
     * 정상 종결로 끝나면(COMPLETE_ENDING) 그대로 보존 — 긴 본문 오절단 방지(2026-06-11).
     */
    private String trimIncompleteEnding(String s) {
        if (s == null || s.isBlank()) return s;
        if (endsWithComplete(s)) return s;
        // 마지막 완전한 종결 위치 탐색 (뒤에서 최대 80자)
        int searchFrom = Math.max(0, s.length() - 80);
        Matcher m = ENDING_FINDER.matcher(s.substring(searchFrom));
        int lastComplete = -1;
        while (m.find()) {
            lastComplete = searchFrom + m.end();
        }
        if (lastComplete > searchFrom) {
            return s.substring(0, lastComplete).stripTrailing();
        }
        // 탐색 범위 내 종결 없으면 줄바꿈 기준 (본문 절반 이상 보존될 때만)
        int lastNewline = s.lastIndexOf('\n', s.length() - 2);
        if (lastNewline > s.length() / 2) {
            return s.substring(0, lastNewline).stripTrailing();
        }
        return s;
    }

    private boolean endsWithComplete(String s) {
        String trimmed = s.stripTrailing();
        if (trimmed.isEmpty()) return false;
        return COMPLETE_ENDING.matcher(trimmed).matches();
    }

    /** 멀티 옵션 텍스트에서 첫 번째 실제 내용만 추출 */
    private String extractFirstOption(String s) {
        // "옵션 1 (설명)\n`...\n`" 또는 "옵션 1:\n내용" 패턴
        Matcher m = Pattern.compile(
            "(?:옵션|선택지|버전)\\s*1[^:\n（(]*[:\\s（(][^\n]*\n[`\"]*(.*?)[`\"]*\\n",
            Pattern.DOTALL).matcher(s);
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (!candidate.isBlank()) return candidate;
        }
        // 백틱 코드블록으로 감싸진 첫 번째 옵션
        Matcher blockM = Pattern.compile("```[^\n]*\n(.*?)```", Pattern.DOTALL).matcher(s);
        if (blockM.find()) {
            String candidate = blockM.group(1).trim();
            if (!candidate.isBlank()) return candidate;
        }
        // 앞의 메타 설명만 제거하고 나머지 반환
        return s.replaceAll("(?s)^.*?(?=맞아|ㄹㅇ|진짜|아니|그냥|근데|저도|나도|어휴|헐|ㅋ|ㅠ|음|글쎄)", "").trim();
    }
}
