package com.againspring.aiuser.llm.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputSanitizer {
    private static final int MAX_POST = 2000;
    private static final int MAX_COMMENT = 300;

    // LLM이 여러 옵션을 제시할 때 첫 번째 옵션만 추출
    private static final Pattern MULTI_OPTION = Pattern.compile(
        "(?:옵션\\s*[1-9]|선택지\\s*[1-9]|버전\\s*[1-9])[^:：]*[：:]?\\s*[`\"]*(.*?)[`\"]*\\s*(?=(?:옵션|선택지|버전)\\s*[2-9]|$)",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    // 첫 코드블록(```) 안의 내용 추출
    private static final Pattern CODE_BLOCK = Pattern.compile("```[^\n]*\n?(.*?)```", Pattern.DOTALL);
    // AI 메타 응답 패턴 (앞부분에서만 제거)
    private static final Pattern LEADING_META = Pattern.compile(
        "^(?:안녕하세요[,!. ]*|물론이죠[,. ]*|물론입니다[,. ]*|네,? 저는 [^\n]*\n?|제가 도와드릴게요[,. ]*" +
        "|앞 댓글[의에]? [^\\n]{0,30}없어서[,\\s]*[^\n]*\n?" +
        "|[^\n]{0,20}몇 가지[를를]? 제안[해드릴게알려][^\n]*\n?" +
        "|[^\n]{0,20}제안[해드릴게알려][^\n]*\n?)",
        Pattern.CASE_INSENSITIVE
    );

    public String sanitizePost(String raw) {
        return sanitize(raw, MAX_POST);
    }

    public String sanitizeComment(String raw) {
        return sanitize(raw, MAX_COMMENT);
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

        // 3. 마크다운 제거 + 구분선 이후 AI 분석 제거
        // "---" 구분선은 커뮤니티 댓글에 절대 없으므로 이후 전체 삭제
        s = s.replaceAll("(?s)\\n---+\\n.*$", "")
             .replaceAll("(?m)^#{1,6}\\s+", "")
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

    /**
     * 텍스트가 불완전한 어미로 끝날 때 마지막 완결된 종결까지 잘라냄.
     * 예: "이게 맞은" → "이게 맞은" 제거, 앞의 자연스러운 종결까지 유지
     */
    private String trimIncompleteEnding(String s) {
        if (s == null || s.isBlank()) return s;
        // 완전한 종결로 끝나면 그대로 반환
        if (endsWithComplete(s)) return s;
        // 마지막 완전한 종결 위치 탐색 (뒤에서 최대 80자)
        int searchFrom = Math.max(0, s.length() - 80);
        // 완결 종결 패턴: 문장 끝 or 자연스러운 한국어 종결어미
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            // 마침표/물음표/느낌표, ㅋ/ㅠ 종결, 한국어 종결어미 목록
            "[.?!]|ㅋ+|ㅠ+|" +
            "(?:거든|거임|더라|했음|있음|없음|겠음|모르겠음|ㄴ거임|건지|는지|하는건지|뭐하는건지|" +
            "잖아|이잖아|아니잖아|이야|야(?![^\\n])|봐(?![\\w])|봐야지|해야지|싶음|싶어|" +
            "했거든|갔거든|겠거든|없거든|있거든|했잖아|없잖아|있잖아|" +
            "임(?![\\w])|함(?![\\w])|됨(?![\\w])|맞음(?![\\w])|좋음(?![\\w])|나쁨(?![\\w])|" +
            "요(?![\\w])|해요|이에요|아요|어요|네요|세요|데요|거예요)"
        ).matcher(s.substring(searchFrom));
        int lastComplete = -1;
        while (m.find()) {
            lastComplete = searchFrom + m.end();
        }
        if (lastComplete > searchFrom) {
            return s.substring(0, lastComplete).stripTrailing();
        }
        // 탐색 범위를 넓혀서 줄바꿈 기준으로 자름
        int lastNewline = s.lastIndexOf('\n', s.length() - 2);
        if (lastNewline > s.length() / 2) {
            return s.substring(0, lastNewline).stripTrailing();
        }
        return s;
    }

    private boolean endsWithComplete(String s) {
        String trimmed = s.stripTrailing();
        if (trimmed.isEmpty()) return false;
        char last = trimmed.charAt(trimmed.length() - 1);
        // 명백한 완결 종결
        if (".?!ㅋㅠ".indexOf(last) >= 0) return true;
        // 한국어 종결어미로 끝나는지 (5자 범위)
        String tail = trimmed.length() >= 5 ? trimmed.substring(trimmed.length() - 5) : trimmed;
        return tail.matches(".*(?:거든|거임|더라|했음|있음|없음|겠음|잖아|이야|봐야지|해야지|싶음|싶어|임|함|됨|맞음|요|해요|이에요|아요|어요|네요|데요)$");
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
