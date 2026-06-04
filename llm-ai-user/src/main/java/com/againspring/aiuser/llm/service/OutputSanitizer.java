package com.againspring.aiuser.llm.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputSanitizer {
    private static final int MAX_POST = 800;
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
        return s;
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
