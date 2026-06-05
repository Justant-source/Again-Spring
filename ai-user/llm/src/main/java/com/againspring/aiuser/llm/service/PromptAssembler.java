package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class PromptAssembler {
    private static final String SEP = "<<<USER_PROMPT>>>";

    private String postGuide = "";
    private String commentGuide = "";
    private String replyGuide = "";
    private String partnerGuide = "";

    @PostConstruct
    public void loadGuides() {
        // % 문자를 %% 로 이스케이프 — buildSystem()에서 String.formatted()에 넘기기 때문
        postGuide = loadResource("voice/post.md").replace("%", "%%");
        commentGuide = loadResource("voice/comment.md").replace("%", "%%");
        replyGuide = loadResource("voice/reply.md").replace("%", "%%");
        partnerGuide = loadResource("voice/partner.md").replace("%", "%%");
        log.info("Voice guides loaded: post={}c comment={}c reply={}c partner={}c",
            postGuide.length(), commentGuide.length(), replyGuide.length(), partnerGuide.length());
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
    // 2026-06-04 개정: 배경 축소, 감정 강화, 미완성감 우선
    private static final String[] VARIETY_SEEDS = {
        "배경 설명은 1~2줄만. 감정과 상황으로 곧바로 진입.",
        "'내가', '나는' 1인칭을 계속 반복해서 쓸 것.",
        "마무리에서 해결책이나 결론을 내지 말고 물음표나 혼란 상태로 끝낼 것.",
        "중간에 '근데 생각해보니' 같은 사족 넣으면서 두서없게.",
        "마지막 문장을 강한 감정이나 의문으로 끝내기.",
        "배경 최소화 + 갈등 상황만 압축적으로 표현.",
        "반복적인 감정 표현: '내가 ~인데', '나는 ~이고'.",
        "구체적인 D-day나 기간 언급 (사귄 지 1년, 일한 지 3개월).",
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
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), postGuide, req.getFormality());
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
            위 카테고리와 말투로 한국 갈등 커뮤니티 사연을 완전 창작해주세요.
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
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    private String assemblePartnerPrompt(PostGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), partnerGuide, req.getFormality());
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
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleCommentPrompt(CommentGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), commentGuide, req.getFormality());
        String toneNote = isPolite(req.getFormality())
            ? "- 존댓말로 작성 (~요, ~어요, ~더라고요, ~것 같아요)"
            : "- 반말로 작성 (요/습니다 금지)";
        String user = """
            %s글 제목: %s
            글 내용 요약: %s
            내 입장: %s (AUTHOR=작성자 편, PARTNER=상대방 편, NEUTRAL=중립)
            %s
            %s
            %s
            이 글에 달 짧은 댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 50~150자 내외
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                safe(req.getPostTitle() != null ? req.getPostTitle() : ""),
                safe(req.getPostBodyExcerpt() != null ? req.getPostBodyExcerpt() : ""),
                req.getStance() != null ? req.getStance() : "NEUTRAL",
                req.getArchetypeCommentSamples() != null && !req.getArchetypeCommentSamples().isBlank() ? "이 글에 자주 달리는 댓글 패턴 (참고용):\n" + safe(req.getArchetypeCommentSamples()) : "",
                req.getExistingComments() != null && !req.getExistingComments().isBlank() ? "이미 달린 댓글들 (중복 피하고 다른 관점으로):\n" + safe(req.getExistingComments()) : "",
                dynamicExamplesBlock(req.getDynamicExamples()),
                toneNote);
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleReplyPrompt(ReplyGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), replyGuide, req.getFormality());
        String toneNote = isPolite(req.getFormality())
            ? "- 존댓말로 작성 (~요, ~어요 등 자연스럽게)"
            : "- 반말로 작성 (요/습니다 금지)";
        String user = """
            %s%s%s원댓글: %s
            맥락: %s
            반응: %s (AGREE=공감, DISAGREE=반박, CURIOUS=궁금)

            이 댓글에 대한 자연스러운 대댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 초단문 필수: 15~40자만 (한 문장 반도 안 됨)
            - 감정만 표현: 공감, 응원, 이해 중심
            - 정말, 진짜, 진심으로 등 감정 강조 자연스러움
            - ㅠㅠ, ㅋ, 💚 같은 이모지 자연스럽게 포함
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getPostBodyExcerpt() != null && !req.getPostBodyExcerpt().isBlank() ? "원글 맥락: " + safe(req.getPostBodyExcerpt()) + "\n" : "",
                req.getSiblingComments() != null && !req.getSiblingComments().isBlank() ? "다른 댓글들:\n" + safe(req.getSiblingComments()) + "\n" : "",
                safe(req.getParentCommentExcerpt() != null ? req.getParentCommentExcerpt() : ""),
                safe(req.getThreadContext() != null ? req.getThreadContext() : ""),
                req.getStance() != null ? req.getStance() : "CURIOUS",
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
               "[참고용 예시 — 길이·구조만 모방, 문장부호·존댓말·반말은 위 규칙만 따를 것]\n" +
               "아래 예시의 온점, 존댓말, 표현을 절대 모방하지 말 것. 페르소나와 한국 문체 규칙 우선.\n" +
               "───────────────────────────────────────\n" +
               safe(normalized) + "\n" +
               "───────────────────────────────────────\n";
    }

    private boolean isPolite(String formality) {
        return "polite".equalsIgnoreCase(formality);
    }

    private String buildSystem(String voiceProfile, double slangLevel, String guide, String formality) {
        boolean polite = isPolite(formality);
        // % 문자가 String.formatted()의 포맷 지시자로 오해받지 않도록 이스케이프
        String safeVoice = voiceProfile != null ? voiceProfile.replace("%", "%%") : "일반 커뮤니티 사용자";
        String safeGuide = guide != null ? guide.replace("%", "%%") : "";

        String speechRules = polite ? """
            **존댓말 사용** — 자연스러운 구어 존댓말:
            - 사용: ~요, ~어요, ~아요, ~더라고요, ~것 같아요, ~했어요, ~해요
            - 허용 예: 진짜 공감해요 / 저도 그랬어요 / 어휴 힘드셨겠어요 ㅠㅠ
            - 금지: 지나친 격식어 (~습니다, ~입니다, 공문서 투) / 완전 반말 (~임, ~거든)
            - 간접화법 인용 금지: 겹따옴표 사용 절대 금지 — 대신: ~라고 하더라고요 / ~했다고 해요
            - 문장 끝 온점(.) 금지 — 한국 커뮤니티는 점 생략, ㅠ/ㅋ/...로 끊기
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

        return """
당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다.

## 핵심 3가지 (2026-06-04 개정 — 가장 중요)

### 1. 배경 50%% 축소 → 본 이야기로 빠르게 진입
- ❌ "5년을 사귀고 있는데, 만난 지 첫 6개월에는 좋았지만, 지금은 점점..."
- ✅ "남친이 전여친 얘기를 자꾸 꺼냄. 나는 진짜 못 듣겠음."
- 배경은 **최대 1~2줄**, 나머지는 갈등 상황 + 감정에 할애

### 2. 감정 토로 강화 → "저는", "제가", "나는", "내가" 연속 사용
- ❌ "남편이 내 의견을 무시합니다. 이것이 문제입니다."
- ✅ "내가 말해도 남편은 안 들어. 나는 계속 답답하고. 내가 뭐가 잘못한 건가."
- **1인칭 감정 폭발 우선** — 주인공성 강조

### 3. 미완성감 유지 → 결론 없이 질문/혼란으로 끝내기
- ❌ "결론적으로 우리는 상담을 받아야 할 것 같습니다."
- ✅ "그래서 지금 내가 뭘 해야 하는지 몰라. 우리 진짜 이대로는 안 될 것 같은데..."
- **해결책 제시 금지** — 막혀있는 상태를 그대로 노출

## 말투 규칙 (가장 중요)

%s

**자연스러운 구어체** — 페르소나 특성의 writing_quirks에 consistent_errors가 있으면 그 오류 패턴을 **일관되게** 재현. mobile_typos: true이면 모바일 오탈자(자모분리·인접키) 2~3개 자연스럽게 포함. 맞춤법이 완벽할 필요 없음.

슬랭 수준 %.1f/1.0 %s

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

## 페르소나 특성
%s

## 커뮤니티 스타일 가이드
%s

## 창작 금지 (항상 준수)
- 실명, 연락처, 주소, 주민번호 등 개인정보 포함 금지
- 실제 사건 원문 복제 금지 — 완전 창작
- 판결·단정 표현 금지 ("네가 잘못", "저 사람이 나쁘다" 식)""".formatted(
            speechRules,
            slangLevel,
            slangGuide,
            safeVoice,
            safeGuide);
    }
}
