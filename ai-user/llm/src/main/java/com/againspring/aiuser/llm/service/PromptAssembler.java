package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class PromptAssembler {
    private static final String SEP = "<<<USER_PROMPT>>>";

    private volatile String postGuide = "";
    private volatile String commentGuide = "";
    private volatile String replyGuide = "";
    private volatile String partnerGuide = "";

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void loadGuides() {
        reload();
    }

    /** 관리자 요청 또는 스케줄에 의한 DB 기반 재로드. */
    public synchronized void reload() {
        // % 문자를 %% 로 이스케이프 — buildSystem()에서 String.formatted()에 넘기기 때문
        postGuide    = loadGuide("voice/post",    "voice/post.md").replace("%", "%%");
        commentGuide = loadGuide("voice/comment", "voice/comment.md").replace("%", "%%");
        replyGuide   = loadGuide("voice/reply",   "voice/reply.md").replace("%", "%%");
        partnerGuide = loadGuide("voice/partner", "voice/partner.md").replace("%", "%%");
        log.info("Voice guides loaded: post={}c comment={}c reply={}c partner={}c",
            postGuide.length(), commentGuide.length(), replyGuide.length(), partnerGuide.length());
    }

    private String loadGuide(String dbKey, String classpathPath) {
        if (jdbcTemplate != null) {
            try {
                List<String> rows = jdbcTemplate.queryForList(
                    "SELECT content FROM ai_prompt_template WHERE `key` = ? AND content != ''",
                    String.class, dbKey);
                if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                    log.debug("Voice guide '{}' loaded from DB ({}c)", dbKey, rows.get(0).length());
                    return rows.get(0);
                }
            } catch (Exception e) {
                log.warn("DB read failed for '{}', falling back to classpath: {}", dbKey, e.getMessage());
            }
        }
        String content = loadResource(classpathPath);
        // 첫 기동 시 classpath 내용을 DB에 시드 (빈 레코드만 업데이트)
        if (jdbcTemplate != null && !content.isBlank()) {
            try {
                jdbcTemplate.update(
                    "UPDATE ai_prompt_template SET content = ? WHERE `key` = ? AND (content IS NULL OR content = '')",
                    content, dbKey);
            } catch (Exception e) {
                log.warn("DB seed failed for '{}': {}", dbKey, e.getMessage());
            }
        }
        return content;
    }

    private String loadResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load voice guide '{}': {}", path, e.getMessage());
            return "";
        }
    }

    // 다양성 시드 — user 프롬프트 끝에 랜덤 1개 추가 (temperature 대안)
    // 2026-06-05 개정: 구체 사건 강제 시드 추가
    private static final String[] VARIETY_SEEDS = {
        "배경 설명은 1~2줄만. 구체적 사건(언제, 무슨 행동)으로 곧바로 진입.",
        "'내가', '나는' 1인칭을 계속 반복해서 쓸 것.",
        "마무리에서 해결책이나 결론을 내지 말고 물음표나 혼란 상태로 끝낼 것.",
        "중간에 '근데 생각해보니' 같은 사족 넣으면서 두서없게.",
        "마지막 문장을 강한 감정이나 의문으로 끝내기.",
        "반복 횟수·날짜 언급 필수: '벌써 세 번째', '지난 주에도', '어제 또'.",
        "상대방이 한 말·행동 1가지를 구체적으로 서술 (간접화법으로).",
        "구체적인 D-day나 기간 언급 (사귄 지 1년, 일한 지 3개월).",
        "갈등의 결정적 순간 1가지: 어떤 말을 들었을 때, 어떤 행동을 봤을 때.",
        "문장마다 종결어미를 다르게 — ~요 / ~더라고요 / ~거든요 / ~네요 중 매번 다른 어미 선택.",
        "같은 어미 2문장 연속 금지 — 특히 '~했어요'가 3회 이상 반복되면 실격.",
    };
    private static final java.util.Random PROMPT_RNG = new java.util.Random();

    private String lengthInstruction(String tier) {
        if (tier == null) return "자연스러운 길이로.";
        return switch (tier) {
            case "SHORT"    -> "아주 짧게 — 50~120자. 핵심 상황 하나만 툭 던지는 초단 글.";
            case "MEDIUM"   -> "짧게 — 150~350자. 상황과 감정 간략히.";
            case "LONG"     -> "보통 — 400~800자. 사건 흐름 상세히.";
            case "VERYLONG" -> "길게 — 900~1800자. 길게 쏟아내는 글. 사족·반복·감정 흐름 자연스럽게 포함.";
            default         -> "자연스러운 길이로.";
        };
    }

    public String assemblePostPrompt(PostGenRequest req) {
        // PARTNER stance이면 별도 파트너 프롬프트로 분기
        if ("PARTNER".equalsIgnoreCase(req.getStance()) && req.getCounterpartBody() != null && !req.getCounterpartBody().isBlank()) {
            return assemblePartnerPrompt(req);
        }
        // 기존 로직 유지
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), postGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules());
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        // 랜덤 다양성 시드 (50% 확률로 1개 추가)
        String varietySeed = PROMPT_RNG.nextBoolean()
            ? "\n[스타일 힌트] " + VARIETY_SEEDS[PROMPT_RNG.nextInt(VARIETY_SEEDS.length)] : "";
        String user = """
            %s카테고리: %s
            아키타입: %s
            %s
            글 길이: %s
            %s
            %s
            위 카테고리와 말투로 한국 갈등 커뮤니티 사연을 완전 창작해주세요.
            - 🚨 구체적 사건 필수: "어제/지난주에 X가 Y를 했다" 형태의 사건 1개 이상 포함. 감정만 나열하는 한탄 글 금지.
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - 실제 사건 원문 복제 금지 (완전 창작)
            - 판결·처방·승패 표현 금지
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            %s%s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getCategory() != null ? req.getCategory() : "OTHER",
                req.getArchetype() != null ? req.getArchetype() : "일반갈등",
                req.getTopicSeed() != null ? "상황: " + safe(req.getTopicSeed()) : "",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들과 같은 소재·사건 유형 반복 금지 — 완전히 다른 상황·디테일로"),
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    private String assemblePartnerPrompt(PostGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), partnerGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules());
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        String varietySeed = PROMPT_RNG.nextBoolean()
            ? "\n[스타일 힌트] " + VARIETY_SEEDS[PROMPT_RNG.nextInt(VARIETY_SEEDS.length)] : "";
        String user = """
            [작성자가 쓴 원글]
            %s

            카테고리: %s
            아키타입: %s
            글 길이: %s
            %s
            %s
            위 원글에서 상대방(파트너) 입장으로 같은 갈등을 1인칭으로 서술해주세요.
            - 원글에서 언급된 구체 사건을 반드시 참조하되 해석은 자기 시각으로
            - 방어적 해명보다 내가 느끼고 있는 것 중심
            - 판결·처방·사과 표현 금지
            - 실제 인물 실명·개인정보 절대 포함 금지
            ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지
            %s%s""".formatted(
                safe(req.getCounterpartBody()),
                req.getCategory() != null ? req.getCategory() : "OTHER",
                req.getArchetype() != null ? req.getArchetype() : "갈등",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들과 같은 표현·말버릇 반복 금지"),
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleCommentPrompt(CommentGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), commentGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules());
        String toneNote = isPolite(req.getFormality())
            ? "- 존댓말로 작성 (~요, ~어요, ~더라고요, ~것 같아요)"
            : "- 반말로 작성 (요/습니다 금지)";
        // 모드 힌트가 있으면 고정 길이 지시 대신 모드별 지시 사용 (문체 현실화 S3)
        String lengthLine = req.getModeHint() != null && !req.getModeHint().isBlank()
            ? "- " + safe(req.getModeHint())
            : "- 50~150자 내외";
        String user = """
            %s글 제목: %s
            글 내용 요약: %s
            내 입장: %s (AUTHOR=작성자 편, PARTNER=상대방 편, NEUTRAL=중립)
            %s
            %s
            %s
            %s
            %s
            %s
            %s
            이 글에 달 짧은 댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            %s
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                safe(req.getPostTitle() != null ? req.getPostTitle() : ""),
                safe(req.getPostBodyExcerpt() != null ? req.getPostBodyExcerpt() : ""),
                req.getStance() != null ? req.getStance() : "NEUTRAL",
                req.getArchetypeCommentSamples() != null && !req.getArchetypeCommentSamples().isBlank() ? "이 글에 자주 달리는 댓글 패턴 (참고용):\n" + safe(req.getArchetypeCommentSamples()) : "",
                req.getExistingComments() != null && !req.getExistingComments().isBlank() ? "이미 달린 댓글들 (중복 피하고 다른 관점으로):\n" + safe(req.getExistingComments()) : "",
                styleExamplesBlock(req.getStyleExamples()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                req.getDispositionNote() != null && !req.getDispositionNote().isBlank() ? "내 성향: " + safe(req.getDispositionNote()) : "",
                req.getReactableComments() != null && !req.getReactableComments().isBlank() ? "이미 달린 댓글들 (번호로 좋아요 표시 가능):\n" + safe(req.getReactableComments()) : "",
                recentOutputsBlock(req.getRecentOutputs(), "댓글", "위와 같은 전개(공감→경험담→조언)였다면 이번엔 다른 전개로"),
                lengthLine,
                toneNote);
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleReplyPrompt(ReplyGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), replyGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules());
        String toneNote = isPolite(req.getFormality())
            ? "- 존댓말로 작성 (~요, ~어요 등 자연스럽게)"
            : "- 반말로 작성 (요/습니다 금지)";
        // 모드 힌트가 있으면 고정 길이 지시 대신 사용 (문체 현실화 S3)
        String lengthLine = req.getModeHint() != null && !req.getModeHint().isBlank()
            ? "- " + safe(req.getModeHint())
            : "- 초단문 필수: 15~40자 (한 문장 반도 안 됨)";
        String user = """
            %s%s%s원댓글: %s
            맥락: %s
            반응: %s (AGREE=공감, DISAGREE=반박, CURIOUS=궁금)
            %s
            %s
            %s
            이 댓글에 대한 자연스러운 대댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            %s
            - 원댓글 단어 하나를 받아 감정 한 방으로 반응 — 분석·요약·조언 나열 금지
            - 정말/진짜 같은 강조어와 ㅠㅠ를 습관처럼 쓰지 말 것 — 매번 다른 반응어로
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getPostBodyExcerpt() != null && !req.getPostBodyExcerpt().isBlank() ? "원글 맥락: " + safe(req.getPostBodyExcerpt()) + "\n" : "",
                req.getSiblingComments() != null && !req.getSiblingComments().isBlank() ? "다른 댓글들:\n" + safe(req.getSiblingComments()) + "\n" : "",
                safe(req.getParentCommentExcerpt() != null ? req.getParentCommentExcerpt() : ""),
                safe(req.getThreadContext() != null ? req.getThreadContext() : ""),
                req.getStance() != null ? req.getStance() : "CURIOUS",
                req.getDispositionNote() != null && !req.getDispositionNote().isBlank() ? "내 성향: " + safe(req.getDispositionNote()) : "",
                styleExamplesBlock(req.getStyleExamples()),
                recentOutputsBlock(req.getRecentOutputs(), "댓글", null),
                lengthLine,
                toneNote);
        return system + "\n" + SEP + "\n" + user;
    }

    /**
     * 페르소나 voice 생성 프롬프트 조립.
     * PersonaFactory가 전달한 raw prompt를 그대로 사용(이미 완성된 프롬프트).
     */
    public String assemblePersonaPrompt(PersonaGenRequest req) {
        // 페르소나 생성은 단순 패스스루 — PersonaFactory가 완성 프롬프트를 전달
        return req.getPrompt() != null ? req.getPrompt() : "";
    }

    /**
     * 글 분석 프롬프트 조립 — 좋아요·투표 결정용 구조화 신호 추출.
     * 최소 프롬프트(생성 가이드 미주입) → 토큰 절약. formatted() 미사용 → 본문의 % 안전.
     */
    public String assemblePostAnalysisPrompt(PostAnalysisRequest req) {
        String title    = req.getTitle() != null ? req.getTitle() : "";
        String body     = req.getBodyPublished() != null ? req.getBodyPublished() : "";
        String category = req.getCategory() != null ? req.getCategory() : "OTHER";
        String hints    = req.getArchetypeHints() != null ? req.getArchetypeHints() : "";

        String system = """
당신은 한국 갈등 커뮤니티 글을 분석하는 도구입니다.
주어진 글을 읽고 아래 7개 항목을 판단해 JSON 객체 1개만 출력합니다. 설명·코드펜스(```) 절대 금지.

- author_sympathy (0~1): 글의 서술이 '작성자' 본인을 정당하다/피해자로 보이게 하는 정도. 0=작성자가 명백히 잘못, 0.5=반반, 1=작성자가 명백한 피해자.
- ambiguity (0~1): 상황이 애매하고 양쪽 주장이 팽팽한 정도. 한쪽 주장만 일방적으로 강하면 높음.
- severity (0~1): 갈등의 감정적 강도. 차분=0, 극심한 분노·절망=1.
- topics: 핵심 주제 키워드 한국어 3개 이하 (예: 가사분담, 연락, 금전).
- emotions: 드러난 감정 한국어 3개 이하 (예: 억울함, 분노, 불안).
- archetype_frame: 주어진 후보 id 중 가장 맞는 1개, 맞는 게 없으면 null.
- political_hint: 글이 진보/보수 프레임 중 어디에 가까운지 ("progressive"|"conservative"|"neutral").

반드시 아래 형식의 JSON 1개만 출력:
{"author_sympathy":0.0,"ambiguity":0.0,"severity":0.0,"topics":[],"emotions":[],"archetype_frame":null,"political_hint":"neutral"}""";

        StringBuilder user = new StringBuilder();
        user.append("카테고리: ").append(category).append("\n");
        if (!hints.isBlank()) {
            user.append("archetype_frame 후보: ").append(hints).append("\n");
        }
        user.append("제목: ").append(title).append("\n\n");
        user.append("본문:\n").append(body).append("\n\n");
        user.append("위 글을 분석해 JSON 1개만 출력하세요.");

        return system + "\n" + SEP + "\n" + user;
    }

    /** String.formatted()에 넘기기 전 % 이스케이프 */
    private String safe(String s) {
        return s != null ? s.replace("%", "%%") : "";
    }

    private String dynamicExamplesBlock(String examples) {
        if (examples == null || examples.isBlank()) return "";
        // RAG 동적 예시에서 온점·쌍따옴표 정규화 — 이 예시는 구조만 참고, 문체는 위 규칙만 따르기
        String normalized = examples.trim()
            .replaceAll("\\.$", "")  // 문장 끝 온점 제거
            .replaceAll("\"", "");    // 쌍따옴표 제거
        return "\n───────────────────────────────────────\n" +
               "[실제 커뮤니티 예시 — 당신의 말투(반말/해요체)와 같은 register로 선별됨]\n" +
               "이 예시들의 종결어미·문장 호흡·끊는 방식·어휘 톤을 모방하라\n" +
               "단, 내용·구체 표현·온점·쌍따옴표는 모방 금지 (온점·쌍따옴표는 항상 제거)\n" +
               "───────────────────────────────────────\n" +
               safe(normalized) + "\n" +
               "───────────────────────────────────────\n";
    }

    /**
     * 반복 방지 블록 — 이 페르소나의 최근 출력을 보여주고 같은 시작·말버릇·전개를 금지.
     * 호출마다 변하는 내용이므로 반드시 USER 섹션에만 주입 (캐시 prefix 보호).
     */
    private String recentOutputsBlock(String recentOutputs, String label, String extraRule) {
        if (recentOutputs == null || recentOutputs.isBlank()) return "";
        return "\n───────────────────────────────────────\n" +
               "[내가 최근에 쓴 " + label + " — 반복 방지]\n" +
               safe(recentOutputs) + "\n" +
               "🚨 위 " + label + "에서 쓴 시작 문구·말버릇·문장 구조를 이번에 또 쓰면 실격\n" +
               "- 위에서 쓴 첫 단어로 또 시작하지 말 것\n" +
               "- 위에 2번 이상 나온 단어·이모티콘(예: 진짜/공감/ㅠㅠ)은 이번엔 빼기\n" +
               (extraRule != null && !extraRule.isBlank() ? "- " + extraRule + "\n" : "") +
               "───────────────────────────────────────\n";
    }

    /** 문체 few-shot 블록 — voice 소스 크롤 코퍼스 랜덤 샘플. 말투만 모방, 내용 모방 금지. */
    private String styleExamplesBlock(String examples) {
        if (examples == null || examples.isBlank()) return "";
        String normalized = examples.trim()
            .replaceAll("(?m)(?<![.?!])\\.\\s*$", "")  // 줄 끝 온점 제거 (말줄임표 보존)
            .replaceAll("\"", "");
        return "\n───────────────────────────────────────\n" +
               "[실제 커뮤니티 문체 샘플 — 주제 무관, 말투만 참고]\n" +
               "종결어미·문장 호흡·끊는 방식·리듬만 모방하라\n" +
               "내용·소재·구체 표현은 절대 가져오지 말 것 (온점·쌍따옴표도 항상 제거)\n" +
               "───────────────────────────────────────\n" +
               safe(normalized) + "\n" +
               "───────────────────────────────────────\n";
    }

    private boolean isPolite(String formality) {
        return "polite".equalsIgnoreCase(formality);
    }

    private String buildSystem(String voiceProfile, double slangLevel, String guide, String formality,
                               String correctionCautions, String globalForbidRules) {
        boolean polite = isPolite(formality);
        // % 문자가 String.formatted()의 포맷 지시자로 오해받지 않도록 이스케이프
        String safeVoice    = voiceProfile != null ? voiceProfile.replace("%", "%%") : "일반 커뮤니티 사용자";
        String safeGuide    = guide != null ? guide.replace("%", "%%") : "";
        // 첨삭 학습 섹션: 빈 값이면 섹션 자체를 제거
        String cautionsSection = (correctionCautions != null && !correctionCautions.isBlank())
            ? "\n## 주의사항 (이 작성자 과거 첨삭 반영 — 반드시 준수)\n" + correctionCautions.replace("%", "%%")
            : "";
        String globalRulesSection = (globalForbidRules != null && !globalForbidRules.isBlank())
            ? "\n## 전역 금지 규칙 (모든 AI 작성자 공통 — 절대 위반 금지)\n" + globalForbidRules.replace("%", "%%")
            : "";

        String speechRules = polite ? """
            **구어 존댓말** — 단, 어미를 매 문장 다르게 섞을 것:
            - 혼용할 것: ~요, ~더라고요, ~거든요, ~네요, ~던데요, ~잖아요, 명사 종결(진짜 헐), 어미 생략(아 그건 좀...)
            - 🚨 같은 종결어미 2문장 연속 금지 — 특히 "~했어요/~어요"로만 이어지는 단조 반복 절대 금지
            - 예시 (다양한 어미 섞기):
              ❌ "남자친구가 그렇게 말했어요. 진짜 황당했어요. 어이가 없었어요."
              ✅ "남자친구가 그렇게 말했어요. 진짜 황당하더라고요. 어이가 없잖아요"
            - 금지: ~습니다/~입니다 격식체, 상담원·공지문 같은 정중한 설명조
            - 간접화법 인용 금지: 겹따옴표 절대 금지 — 대신: ~라고 하더라고요 / ~했다고 해요
            - 문장 끝 온점(.) 금지 — 점 생략, ㅠ/ㅋ/...로 끊기
            """ : """
            **반말 전용** — 아래 종결어미 절대 사용 금지:
            - 금지: ~요, ~습니다, ~입니다, ~합니다, ~했어요, ~하세요
            - 사용: ~임, ~함, ~거든, ~거임, ~더라, ~한다고 함, ~했음, ~는데, ~잖아, ~야
            - 간접화법 인용 금지: 겹따옴표 사용 절대 금지 — 대신: ~라고 함 / ~했다고 함
            - 문장 끝 온점(.) 금지 — 한국 반말에도 마찬가지로 점 생략, 그냥 끊거나 ㅠ/ㅋ/...로 끊기
            """;

        String slangGuide = polite
            ? (slangLevel >= 0.5 ? "ㅠㅠ, ㅋㅋ 가끔 자연스럽게 사용 가능" : "이모지·줄임말 거의 없이 정중하게")
            : (slangLevel >= 0.6 ? "— ㄹㅇ, ㄷㄷ, ㅋㅋㅋ, 개[형용사] 자연스럽게 사용"
               : slangLevel >= 0.4 ? "— ㅋㅋ, ㅠㅠ 가끔 사용" : "— 줄임말 거의 없이 반말만");

        // ⚠️ 캐싱 구조: <<<PERSONA_SECTION>>> 마커 앞은 ClaudeApiInvoker가 cache_control로 캐싱하는
        //    "정적 prefix"다. 마커 앞에는 호출/페르소나마다 변하지 않는 내용(코어 규칙 + 콘텐츠 타입별
        //    고정 가이드)만 둔다. 가변값(말투·슬랭·페르소나·첨삭·전역규칙)은 반드시 마커 뒤로.
        //    이렇게 해야 prefix가 Haiku 2048토큰 최소치를 넘고 페르소나 무관하게 동일 → 캐시 히트.
        return """
당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다.

## 핵심 4가지 (2026-06-05 개정 — 가장 중요)

### 0. 🚨 구체적 사건(trigger) 필수 — 감정만 있는 한탄 글 절대 금지
- ❌ 나쁜 예: "남친이 내 말을 안 들어. 나는 계속 답답함." (무슨 일인지, 언제인지 없음)
- ✅ 좋은 예: "어제 퇴근하고 힘들었다고 했는데 남친이 내 말 중간에 자기 게임 얘기로 바꿨어. 그게 이번 달만 세 번째야."
- **"X가 Y를 했다" 형태의 사건이 반드시 1개 이상** 있어야 함
- 언제(어제/지난주/추석 때), 어떤 행동(말을 끊었다/취소했다/무시했다), 몇 번(세 번째/올해만) 중 최소 2가지 포함

### 1. 배경 50%% 축소 → 본 이야기로 빠르게 진입
- ❌ "5년을 사귀고 있는데, 만난 지 첫 6개월에는 좋았지만, 지금은 점점..."
- ✅ "어제 남친이 내 말 중간에 자기 게임 얘기로 바꿨어. 벌써 세 번째야."
- 배경은 **최대 1~2줄**, 나머지는 갈등 상황 + 감정에 할애

### 2. 감정 토로 강화 → "저는", "제가", "나는", "내가" 연속 사용
- ❌ "남편이 내 의견을 무시합니다. 이것이 문제입니다."
- ✅ "내가 말해도 남편은 안 들어. 나는 계속 답답하고. 내가 뭐가 잘못한 건가."
- **1인칭 감정 폭발 우선** — 주인공성 강조

### 3. 미완성감 유지 → 결론 없이 질문/혼란으로 끝내기
- ❌ "결론적으로 우리는 상담을 받아야 할 것 같습니다."
- ✅ "그래서 지금 내가 뭘 해야 하는지 몰라. 우리 진짜 이대로는 안 될 것 같은데..."
- **해결책 제시 금지** — 막혀있는 상태를 그대로 노출

## 한국 온라인 커뮤니티 필수 문체 규칙 (절대 준수)

**온점(.) 사용 금지**
- 한국 커뮤니티에서는 문장 끝에 온점을 거의 붙이지 않음
- 금지: "남자친구가 전여친 얘기를 꺼냈어요." / "정말 황당했음."
- 허용: "남자친구가 전여친 얘기를 꺼냈어요" / "정말 황당했음"
- 온점 대신 줄바꿈, ㅠ, ㅋ, ... 으로 끊거나 그냥 끊기
- 예외: 말줄임표(...), 물음표(?), 느낌표(!)는 사용 가능

**쌍따옴표("") 사용 금지**
- 한국 커뮤니티에서 간접화법에 쌍따옴표를 쓰지 않음
- 금지: 남자친구가 "전여친이 더 예뻤다"고 했어 / "바빠서 못 봤다"며 연락이 없음
- 허용: 남자친구가 전여친이 더 예뻤다고 했어 / 바빠서 못 봤다며 연락이 없음
- 허용: 남자친구가 그냥 지나가는 말이라고 함 / 걔가 뭐라고 했냐면

## 창작 금지 (항상 준수)
- 실명, 연락처, 주소, 주민번호 등 개인정보 포함 금지
- 실제 사건 원문 복제 금지 — 완전 창작
- 판결·단정 표현 금지 ("네가 잘못", "저 사람이 나쁘다" 식)

## 커뮤니티 스타일 가이드
%s

<<<PERSONA_SECTION>>>
## 말투 규칙 (가장 중요)

%s

**자연스러운 구어체** — 페르소나 특성의 writing_quirks에 consistent_errors가 있으면 그 오류 패턴을 **일관되게** 재현. mobile_typos: true이면 모바일 오탈자(자모분리·인접키) 2~3개 자연스럽게 포함. 맞춤법이 완벽할 필요 없음.

슬랭 수준 %.1f/1.0 %s

## 페르소나 특성
%s
%s%s""".formatted(
            safeGuide,
            speechRules,
            slangLevel,
            slangGuide,
            safeVoice,
            cautionsSection,
            globalRulesSection);
    }
}
