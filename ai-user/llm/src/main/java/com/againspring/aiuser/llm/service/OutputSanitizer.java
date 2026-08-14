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
    // 내부 운영/첨삭 메타가 본문 뒤에 누출된 패턴 — 발견 즉시 해당 지점부터 꼬리 제거
    private static final Pattern TRAILING_META_TABLE = Pattern.compile("(?m)^\\|\\s*항목\\s*\\|\\s*처리\\s*내용\\s*\\|");
    private static final Pattern TRAILING_NOTE_BULLET = Pattern.compile(
        "(?m)(?:^-\\s*(?:트리거|어미 변화|모바일 오타|페르소나 표현):|^-\\s*온점·쌍따옴표 없음\\s*$)");
    // AI 메타 응답 패턴 (앞부분에서만 제거)
    private static final Pattern LEADING_META = Pattern.compile(
        "^(?:안녕하세요[,!. ]*|물론이죠[,. ]*|물론입니다[,. ]*|네,? 저는 [^\n]*\n?|제가 도와드릴게요[,. ]*" +
        "|앞 댓글[의에]? [^\\n]{0,30}없어서[,\\s]*[^\n]*\n?" +
        "|[^\n]{0,20}몇 가지[를를]? 제안[해드릴게알려][^\n]*\n?" +
        "|[^\n]{0,20}제안[해드릴게알려][^\n]*\n?)",
        Pattern.CASE_INSENSITIVE
    );
    // THEQOO h2h에서 반복 검출된 신호 제거: 문장 끝/중간의 뜬금없는 감탄사와 장난스러운 유니코드 이모지.
    private static final Pattern THEQOO_TRAILING_REACTION = Pattern.compile("\\s+(?:헐|개공감)(?:[~….!?ㅋㅠ; ]*)$");
    private static final Pattern THEQOO_REACTION_AFTER_PUNCT = Pattern.compile("([.?!…~]+)\\s*(?:헐|개공감)\\s+");
    private static final Pattern THEQOO_STANDALONE_REACTION = Pattern.compile("\\s(?:헐|개공감)\\s+(?=(?:제가|내가|이게|그게|근데|그냥|뭔가|싶(?:음|은|은데|어|어서)|같(?:음|아)|느낌|기분|왜|아니|그리고))");
    private static final Pattern UNICODE_EMOJI = Pattern.compile("[\\x{2600}-\\x{27BF}\\x{1F300}-\\x{1FAFF}]");
    private static final Pattern UNICODE_ELLIPSIS = Pattern.compile("[…⋯]+");
    private static final Pattern THEQOO_TRASH_PHRASE = Pattern.compile("쓰레기 차도");
    private static final Pattern THEQOO_BROTHER_DAUGHTER_PHRASE = Pattern.compile("집에서는 딸이 더 조심해야");
    private static final Pattern THEQOO_ONE_DO_MORUGET = Pattern.compile("1도\\s+모르겠(음|고)");
    private static final Pattern THEQOO_ONE_DO_IDEAL = Pattern.compile("1도\\s+이해가\\s+안\\s*됨");
    private static final Pattern THEQOO_WEEKDAY_MIDDOT = Pattern.compile("([월화수목금토일])·(?=[월화수목금토일])");
    // 외부 크롤 원문에서 비롯된 특정 커뮤니티명은 다시봄 공개 글에 남기지 않는다.
    // 출처 식별자는 source/source_url 내부 메타데이터로만 보존한다.
    private static final Pattern NAMED_COMMUNITY_REFERENCE = Pattern.compile(
        "네이트\\s*판|nate\\s*pann?|블라인드|(?<![가-힣])블라(?=(?:랑|와|는|가|에|에서|도|의|에요|임|야|하고|같은|글|댓글|유저|사람|분들|반응|문화|$))|\\bblind\\b|"
            + "디시인사이드|디시|dcinside|dc\\s*inside|에펨코리아|펨코|fm\\s*korea|"
            + "더쿠|인스티즈|보배드림|클리앙|루리웹|웃긴대학|웃대|오늘의유머|오유|여시|개드립",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PANN_FEMALE_USER_REFERENCE = Pattern.compile("(?<![가-힣])판녀(?:들)?");
    // 일반 명사인 '판'은 커뮤니티 문맥에서만 치환한다 (판사 등 오탐 방지).
    private static final Pattern PANN_COMMUNITY_CONTEXT = Pattern.compile(
        "(?<![가-힣])판\\s*(?=(?:에|에서|으로|은|는|도|만|글|댓글|유저|사람|분들|반응|문화))");
    private static final Pattern PANN_COMMUNITY_SPACED_CONTEXT = Pattern.compile(
        "(?<![가-힣])판\\s+(?=(?:이럴|보면|에서는|문화|분위기|반응))");

    // ── 커뮤니티별 분포 매칭 설정 (Step 6) ──────────────────────────────────────
    // typoInject/typoProb: Track A R9 결정론적 오타 주입 (D-50). LLM 준수 비의존.
    private record VoiceDistribution(double targetCommaRate, boolean chosungInject,
                                     String[] chosungPhrases, double sampleProb,
                                     boolean typoInject, double typoProb) {}

    private static final java.util.Map<String, VoiceDistribution> VOICE_DIST;
    static {
        VOICE_DIST = new java.util.HashMap<>();
        // A1(typoProb/sampleProb 상향)·A2(chosungInject 확대) — T6 과교정문법·T7 슬랭부재 대응 (2026-06-22)
        // NATEPAN: typoProb 0.50→0.60(A1), chosungInject false→true + 감성 phrases(A2)
        VOICE_DIST.put("NATEPAN",  new VoiceDistribution(0.011, true,
            new String[]{"ㅠㅠ","ㅋㅋ","ㄹㅇ","헐"}, 0.70, true, 0.60));
        VOICE_DIST.put("DCINSIDE", new VoiceDistribution(0.030, true,
            new String[]{"ㄹㅇ","ㅇㅈ","ㄷㄷ","ㅋㅋ"}, 0.80, true, 0.50));
        // BLIND: typoProb 0.45→0.55(A1). polite 계정이므로 chosung은 유지(false).
        VOICE_DIST.put("BLIND",    new VoiceDistribution(0.015, false, null, 0.60, true, 0.55));
        // GENERAL: typoProb 0.45→0.55·sampleProb 0.50→0.60(A1), chosungInject false→true + 중립 phrases(A2)
        VOICE_DIST.put("GENERAL",  new VoiceDistribution(0.015, true,
            new String[]{"ㅋㅋ","ㅠㅠ","ㅇㅇ","ㄹㅇ"}, 0.60, true, 0.55));
        VOICE_DIST.put("FMKOREA",  new VoiceDistribution(0.015, true,
            new String[]{"ㄹㅇㅋㅋ","ㄷㄷ","ㅇㅈ","후추"}, 0.80, true, 0.50));
        VOICE_DIST.put("RULIWEB",  new VoiceDistribution(0.018, false, null, 0.60, true, 0.45));
        // THEQOO: typoProb 0.30→0.55·sampleProb 0.60→0.70(A1). 실효율 0.18→0.385(최저→중간). chosung 유지.
        VOICE_DIST.put("THEQOO",   new VoiceDistribution(0.011, true,
            new String[]{"ㅠㅠ","ㄷㄷ","그니까","ㅇㅇ"}, 0.70, true, 0.55));
        VOICE_DIST.put("ARCALIVE", new VoiceDistribution(0.015, true,
            new String[]{"ㄹㅇ","ㄱㄱ","ㅇㅇ","어쩔"}, 0.80, true, 0.40));
        VOICE_DIST.put("INVEN",    new VoiceDistribution(0.015, false, null, 0.60, true, 0.45));
        VOICE_DIST.put("MLBPARK",  new VoiceDistribution(0.020, false, null, 0.50, true, 0.45));
        VOICE_DIST.put("PPOMPPU",  new VoiceDistribution(0.015, false, null, 0.55, true, 0.50));
        VOICE_DIST.put("CLIEN",    new VoiceDistribution(0.022, false, null, 0.60, true, 0.55));
    }
    private static final java.util.Random DIST_RNG = new java.util.Random();

    // ── R9 Track A: 결정론적 오타 주입 변환 테이블 (T1~T8) ────────────────────────
    // null 반환 = 이 변환 미적용 (다음 변환으로 넘어감). replaceFirst — 1개만 변형.
    // 각 변환은 '첫 줄 이후' 텍스트에만 적용 (injectTypos가 첫 줄을 분리해서 전달).
    @SuppressWarnings("unchecked")
    private static final java.util.List<java.util.function.Function<String, String>> TYPO_TRANSFORMS;
    static {
        java.util.List<java.util.function.Function<String, String>> t = new java.util.ArrayList<>();
        // T1: 됐/됬, 웬/왠 혼동 (가장 흔한 한국어 맞춤법 오류)
        t.add(s -> {
            if (s.contains("됐")) return s.replaceFirst("됐", "됬");
            if (s.contains("됬")) return s.replaceFirst("됬", "됐");
            if (s.contains("웬")) return s.replaceFirst("웬", "왠");
            if (s.contains("왠")) return s.replaceFirst("왠", "웬");
            return null;
        });
        // T2: 종결 '요' 탈락 — "인데요"→"인데", 끝 15자 보호 (마지막 문장 유지)
        t.add(s -> {
            if (s.length() < 20) return null;
            String[] pats = {"인데요", "거든요", "는데요", "라서요"};
            for (String p : pats) {
                int idx = s.indexOf(p);
                if (idx >= 0 && idx < s.length() - 15) {
                    // p.length()=3 ("인데요"=인+데+요), 마지막 자('요') 제거
                    return s.substring(0, idx + p.length() - 1) + s.substring(idx + p.length());
                }
            }
            return null;
        });
        // T3: 띄어쓰기 붙이기 (조금 더→조금더)
        t.add(s -> {
            String[][] pairs = {{"조금 더", "조금더"}, {"너무 힘", "너무힘"},
                                {"진짜 너무", "진짜너무"}, {"많이 좋", "많이좋"}};
            for (String[] p : pairs) {
                if (s.contains(p[0])) return s.replaceFirst(java.util.regex.Pattern.quote(p[0]), p[1]);
            }
            return null;
        });
        // T4: 후치 조사 분리 (진짜로→진짜 로, 그래서→그래 서)
        t.add(s -> {
            String[][] pairs = {{"진짜로", "진짜 로"}, {"정말로", "정말 로"},
                                {"그래서", "그래 서"}, {"솔직히", "솔직 히"}};
            for (String[] p : pairs) {
                if (s.contains(p[0])) return s.replaceFirst(java.util.regex.Pattern.quote(p[0]), p[1]);
            }
            return null;
        });
        // T5: 조사 '의'→'에' 혼동 (나의→나에, 흔한 모바일 오타)
        t.add(s -> {
            int idx = s.indexOf("의 ");
            if (idx > 3) { // 글 맨 앞 3자 보호
                return s.substring(0, idx) + "에 " + s.substring(idx + 2);
            }
            return null;
        });
        // T6: ㅋㅋ/ㅎㅎ 줄 끝 삽입 (이미 초성체 있는 줄 스킵)
        t.add(s -> {
            String[] lines = s.split("\n");
            if (lines.length < 2) return null;
            java.util.List<Integer> cands = new java.util.ArrayList<>();
            for (int i = 0; i < lines.length - 1; i++) {
                String l = lines[i].trim();
                if (l.length() > 8 && !l.contains("ㅋ") && !l.contains("ㅎ")) cands.add(i);
            }
            if (cands.isEmpty()) return null;
            String[] ins = {"ㅋㅋ", "ㅎㅎ", "ㅋㅋㅋ"};
            int li = cands.get(DIST_RNG.nextInt(cands.size()));
            lines[li] = lines[li] + " " + ins[DIST_RNG.nextInt(ins.length)];
            return String.join("\n", lines);
        });
        // T7: 받침 단순화 (갔어→갓어, 왔어→왓어)
        t.add(s -> {
            String[][] pairs = {{"갔어", "갓어"}, {"왔어", "왓어"}, {"봤어", "봣어"}};
            for (String[] p : pairs) {
                if (s.contains(p[0])) return s.replaceFirst(java.util.regex.Pattern.quote(p[0]), p[1]);
            }
            return null;
        });
        // T8: 이중자음 오타 (있었→있엇, 없었→없엇)
        t.add(s -> {
            String[][] pairs = {{"있었", "있엇"}, {"없었", "없엇"}, {"했었", "했엇"}};
            for (String[] p : pairs) {
                if (s.contains(p[0])) return s.replaceFirst(java.util.regex.Pattern.quote(p[0]), p[1]);
            }
            return null;
        });
        TYPO_TRANSFORMS = java.util.Collections.unmodifiableList(t);
    }

    public String sanitizePost(String raw) {
        return sanitize(raw, MAX_POST);
    }

    public String sanitizeComment(String raw) {
        return sanitize(raw, MAX_COMMENT);
    }

    public String sanitizePost(String raw, String voiceType) {
        String base = sanitize(raw, MAX_POST);
        String result = applyDist(base, voiceType, true);
        // T6 등 injectTypos가 수 글자 추가할 수 있으므로 MAX_POST 재보장
        if (result.length() > MAX_POST) result = result.substring(0, MAX_POST).stripTrailing();
        return result;
    }

    public String sanitizeComment(String raw, String voiceType) {
        String base = sanitize(raw, MAX_COMMENT);
        // N6: allowChosung=true — VOICE_DIST.chosungInject 값이 voice별 주입 여부를 결정
        // (이전: false 하드코딩 → DCINSIDE/THEQOO/FMKOREA/ARCALIVE 댓글 초성체 완전 차단)
        String result = applyDist(base, voiceType, true);
        if (result.length() > MAX_COMMENT) result = result.substring(0, MAX_COMMENT).stripTrailing();
        return result;
    }

    private String applyDist(String text, String voiceType, boolean allowChosung) {
        if (voiceType == null || text.isBlank()) return text;
        String normalizedVoice = voiceType.toUpperCase();
        VoiceDistribution dist = VOICE_DIST.get(normalizedVoice);
        if (dist == null) return text;
        String s = text;
        if (DIST_RNG.nextDouble() <= dist.sampleProb()) {
            s = normalizeCommaRate(s, dist.targetCommaRate());
            if (allowChosung && dist.chosungInject() && dist.chosungPhrases() != null) {
                s = injectChosung(s, dist.chosungPhrases());
            }
            // R9 Track A: 결정론적 오타 주입 (LLM 무시 우회) — chosung 이후 마지막으로 실행
            if (dist.typoInject()) {
                s = injectTypos(s, dist.typoProb());
            }
        }
        return applyVoiceCleanup(s, normalizedVoice);
    }

    private String applyVoiceCleanup(String text, String voiceType) {
        if ("THEQOO".equals(voiceType)) {
            return cleanupTheqoo(text);
        }
        return text;
    }

    private String cleanupTheqoo(String text) {
        if (text == null || text.isBlank()) return text;
        String s = UNICODE_EMOJI.matcher(text).replaceAll("");
        s = UNICODE_ELLIPSIS.matcher(s).replaceAll("...");
        s = THEQOO_TRASH_PHRASE.matcher(s).replaceAll("쓰레기통이 차도");
        s = THEQOO_BROTHER_DAUGHTER_PHRASE.matcher(s).replaceAll("집에서는 여자가 더 조심해야");
        s = THEQOO_ONE_DO_MORUGET.matcher(s).replaceAll(match -> "진짜 모르겠" + match.group(1));
        s = THEQOO_ONE_DO_IDEAL.matcher(s).replaceAll("도무지 이해가 안 됨");
        s = THEQOO_WEEKDAY_MIDDOT.matcher(s).replaceAll("$1, ");
        s = THEQOO_REACTION_AFTER_PUNCT.matcher(s).replaceAll("$1 ");
        s = THEQOO_STANDALONE_REACTION.matcher(s).replaceAll(" ");
        s = THEQOO_TRAILING_REACTION.matcher(s).replaceFirst("");
        s = s.replaceAll(" {2,}", " ").replaceAll("\\n{3,}", "\n\n");
        return s.stripTrailing();
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

    /**
     * R9 Track A: 결정론적 한국어 오타 주입 (D-50).
     * - 첫 줄(hook) 보호, budget 1~2개, transform 순서 셔플 → 매 글마다 다른 오타 패턴
     * - fireProb 게이트: 약 절반은 오타 0 → 인간 corpus 이봉분포 모사
     * - len<40: 단문(초단문 댓글)은 건드리지 않음
     */
    private String injectTypos(String text, double fireProb) {
        if (text == null || text.length() < 40) return text;
        if (DIST_RNG.nextDouble() > fireProb) return text; // fireProb 게이트

        // 첫 줄 분리 (보호)
        int firstNl = text.indexOf('\n');
        String firstLine = firstNl >= 0 ? text.substring(0, firstNl + 1) : "";
        String workText = firstNl >= 0 ? text.substring(firstNl + 1) : text;
        if (workText.isBlank()) return text; // 단일 줄이면 건드리지 않음

        int budget = 1 + DIST_RNG.nextInt(2); // 1~2개

        // transform 순서 셔플 (글마다 다른 오타 종류 회전)
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < TYPO_TRANSFORMS.size(); i++) order.add(i);
        java.util.Collections.shuffle(order, DIST_RNG);

        String result = workText;
        int applied = 0;
        for (int idx : order) {
            if (applied >= budget) break;
            String transformed = TYPO_TRANSFORMS.get(idx).apply(result);
            if (transformed != null && !transformed.equals(result)) {
                result = transformed;
                applied++;
            }
        }
        return firstLine + result;
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

    /**
     * LLM이 실제 개행(0x0A) 대신 문자 그대로 "\n" / "\r\n"을 넣는 사례를 실개행으로 바꾼다.
     * legacy {@code /generate/*}와 PLAN {@code /v2/generate/*} 양쪽에서 공유한다.
     * (예: post_b0d71de4da9648608d52 — structured AI_POST가 sanitizer를 우회해 리터럴이 게시됨)
     */
    public static String normalizeLiteralNewlines(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        return raw.replace("\\r\\n", "\n").replace("\\n", "\n");
    }

    private String sanitize(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return "";
        String s = normalizeLiteralNewlines(raw);

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
             // ✅/❌ 분석 체크리스트 줄 전체 제거 (CASUAL 자기분석 방어)
             .replaceAll("(?m)^\\s*[✅❌]\\s+[^\n]*\n?", "")
             // 그 외 키워드 기반 분석 줄 제거
             .replaceAll("(?m)^\\s*[📌🔍\\-•]\\s+(?:반말|존댓말|공감|분석|평가|자연스|어색|길이|[0-9]+점)[^\n]*\n?", "")
             .trim();

        // 3.5 AI 메타 분석 섹션 제거 — 모델이 자기 분석/체크리스트를 본문 뒤에 붙이는 패턴 방어
        // "문체 분석:", "작성 현황:", "적용 처리 메모", "[작성 노트]" 이후 전부 삭제
        String[] META_SECTION_HEADERS = {
            "문체 분석:", "작성 현황:", "작성 포인트:", "수정 사항 정리:", "체크:",
            "적용 처리 메모", "[작성 노트]", "작성 노트:", "AI agent 체크리스트", "AI Agent Checklist"
        };
        for (String header : META_SECTION_HEADERS) {
            int idx = s.indexOf(header);
            if (idx >= 0) {
                s = s.substring(0, idx).stripTrailing();
                break;
            }
        }
        s = cutAtPattern(s, TRAILING_META_TABLE);
        s = cutAtPattern(s, TRAILING_NOTE_BULLET);
        // 선두 작업명 에코 제거: "커뮤니티 글 창작", "일상 글 창작", "카페 경험 공유글" 등
        s = s.replaceAll("(?m)^[^\n]{1,20}글 창작[^\n]*\n?", "").stripLeading();
        s = s.replaceAll("(?m)^[^\n]{1,20} 경험 공유글[^\n]*\n?", "").stripLeading();

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

        // 4.6 크롤 원문 출처 커뮤니티명은 범용 용어로 정규화한다.
        // 기존 오염 코퍼스가 LLM 출력에 재현되는 경우까지 막는 마지막 변환 계층이다.
        s = NAMED_COMMUNITY_REFERENCE.matcher(s).replaceAll("온라인 커뮤니티");
        s = PANN_FEMALE_USER_REFERENCE.matcher(s).replaceAll("커뮤니티 이용자들");
        s = PANN_COMMUNITY_CONTEXT.matcher(s).replaceAll("커뮤니티");
        s = PANN_COMMUNITY_SPACED_CONTEXT.matcher(s).replaceAll("커뮤니티 ");
        s = s.replace("커뮤니티을", "커뮤니티를")
             .replace("커뮤니티은", "커뮤니티는")
             .replace("커뮤니티이", "커뮤니티가");

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
    /** ASCII/Unicode 문장부호·ㅋ·ㅠ 또는 한국어 종결어미로 끝나는지 (단어 경계 적용). */
    private static final Pattern COMPLETE_ENDING = Pattern.compile(
        "(?s).*(?:[.?!…⋯]|ㅋ+|ㅠ+|(?:" + ENDING_ALT + ")(?![가-힣]))$");
    /** 텍스트 중간/끝의 종결 위치 탐색용 (trim 시 마지막 완결점 찾기). */
    private static final Pattern ENDING_FINDER = Pattern.compile(
        "[.?!…⋯]|ㅋ+|ㅠ+|(?:" + ENDING_ALT + ")(?![가-힣])");

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

    private String cutAtPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        return text.substring(0, matcher.start()).stripTrailing();
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
