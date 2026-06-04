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

    @PostConstruct
    public void loadGuides() {
        postGuide = loadResource("voice/post.md");
        commentGuide = loadResource("voice/comment.md");
        replyGuide = loadResource("voice/reply.md");
        log.info("Voice guides loaded: post={}c comment={}c reply={}c",
            postGuide.length(), commentGuide.length(), replyGuide.length());
    }

    private String loadResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load voice guide '{}': {}", path, e.getMessage());
            return "";
        }
    }

    public String assemblePostPrompt(PostGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), postGuide, req.getFormality());
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        String user = """
            %s카테고리: %s
            아키타입: %s
            %s

            위 카테고리와 말투로 한국 갈등 커뮤니티 사연을 완전 창작해주세요.
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - 실제 사건 원문 복제 금지 (완전 창작)
            - 판결·처방·승패 표현 금지
            - 300~600자 내외, 자연스러운 커뮤니티 말투
            %s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + req.getDemographic() + "\n" : "",
                req.getCategory() != null ? req.getCategory() : "OTHER",
                req.getArchetype() != null ? req.getArchetype() : "일반갈등",
                req.getTopicSeed() != null ? "주제 힌트: " + req.getTopicSeed() : "",
                politeSuffix);
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
            이 글에 달 짧은 댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 50~150자 내외
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + req.getDemographic() + "\n" : "",
                req.getPostTitle() != null ? req.getPostTitle() : "",
                req.getPostBodyExcerpt() != null ? req.getPostBodyExcerpt() : "",
                req.getStance() != null ? req.getStance() : "NEUTRAL",
                req.getArchetypeCommentSamples() != null && !req.getArchetypeCommentSamples().isBlank() ? "이 글에 자주 달리는 댓글 패턴 (참고용):\n" + req.getArchetypeCommentSamples() : "",
                req.getExistingComments() != null && !req.getExistingComments().isBlank() ? "이미 달린 댓글들 (중복 피하고 다른 관점으로):\n" + req.getExistingComments() : "",
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
            - 30~100자 내외
            %s
            """.formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + req.getDemographic() + "\n" : "",
                req.getPostBodyExcerpt() != null && !req.getPostBodyExcerpt().isBlank() ? "원글 맥락: " + req.getPostBodyExcerpt() + "\n" : "",
                req.getSiblingComments() != null && !req.getSiblingComments().isBlank() ? "다른 댓글들:\n" + req.getSiblingComments() + "\n" : "",
                req.getParentCommentExcerpt() != null ? req.getParentCommentExcerpt() : "",
                req.getThreadContext() != null ? req.getThreadContext() : "",
                req.getStance() != null ? req.getStance() : "CURIOUS",
                toneNote);
        return system + "\n" + SEP + "\n" + user;
    }

    private boolean isPolite(String formality) {
        return "polite".equalsIgnoreCase(formality);
    }

    private String buildSystem(String voiceProfile, double slangLevel, String guide, String formality) {
        boolean polite = isPolite(formality);

        String speechRules = polite ? """
            **존댓말 사용** — 자연스러운 구어 존댓말:
            - 사용: ~요, ~어요, ~아요, ~더라고요, ~것 같아요, ~했어요, ~해요
            - 허용: "진짜 공감해요", "저도 그랬어요", "어휴 힘드셨겠어요 ㅠㅠ"
            - 금지: 지나친 격식어 (~습니다, ~입니다, 공문서 투) / 완전 반말 (~임, ~거든)
            - 쌍따옴표("") 완전 금지 — 인용 시: ~라고 하더라고요 / ~했다고 해요
            """ : """
            **반말 전용** — 아래 종결어미 절대 사용 금지:
            - 금지: ~요, ~습니다, ~입니다, ~합니다, ~했어요, ~하세요
            - 사용: ~임, ~함, ~거든, ~거임, ~더라, ~한다고 함, ~했음, ~는데, ~잖아, ~야
            - 쌍따옴표("") 완전 금지 — 인용 시: ~라고 함 / ~했다고 함
            """;

        String slangGuide = polite
            ? (slangLevel >= 0.5 ? "ㅠㅠ, ㅋㅋ 가끔 자연스럽게 사용 가능" : "이모지·줄임말 거의 없이 정중하게")
            : (slangLevel >= 0.6 ? "— ㄹㅇ, ㄷㄷ, ㅋㅋㅋ, 개[형용사] 자연스럽게 사용"
               : slangLevel >= 0.4 ? "— ㅋㅋ, ㅠㅠ 가끔 사용" : "— 줄임말 거의 없이 반말만");

        return """
당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다.

## 말투 규칙 (가장 중요)

%s

**자연스러운 구어체** — 맞춤법이 완벽할 필요 없음. 짧은 문장들.

슬랭 수준 %.1f/1.0 %s

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
            voiceProfile != null ? voiceProfile : "일반 커뮤니티 사용자",
            guide);
    }
}
