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
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), postGuide);
        String user = """
            카테고리: %s
            아키타입: %s
            %s

            위 카테고리와 말투로 한국 갈등 커뮤니티 사연을 완전 창작해주세요.
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - 실제 사건 원문 복제 금지 (완전 창작)
            - 판결·처방·승패 표현 금지
            - 300~600자 내외, 자연스러운 커뮤니티 말투
            """.formatted(
                req.getCategory() != null ? req.getCategory() : "OTHER",
                req.getArchetype() != null ? req.getArchetype() : "일반갈등",
                req.getTopicSeed() != null ? "주제 힌트: " + req.getTopicSeed() : "");
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleCommentPrompt(CommentGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), commentGuide);
        String user = """
            글 제목: %s
            글 내용 요약: %s
            내 입장: %s (AUTHOR=작성자 편, PARTNER=상대방 편, NEUTRAL=중립)

            이 글에 달 짧은 댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 50~150자 내외, 자연스러운 커뮤니티 반말 댓글
            """.formatted(
                req.getPostTitle() != null ? req.getPostTitle() : "",
                req.getPostBodyExcerpt() != null ? req.getPostBodyExcerpt() : "",
                req.getStance() != null ? req.getStance() : "NEUTRAL");
        return system + "\n" + SEP + "\n" + user;
    }

    public String assembleReplyPrompt(ReplyGenRequest req) {
        String system = buildSystem(req.getVoiceProfile(), req.getSlangLevel(), replyGuide);
        String user = """
            원댓글: %s
            맥락: %s
            반응: %s (AGREE=공감, DISAGREE=반박, CURIOUS=궁금)

            이 댓글에 대한 자연스러운 대댓글을 작성해주세요.
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 30~100자 내외, 커뮤니티 반말 대댓글
            """.formatted(
                req.getParentCommentExcerpt() != null ? req.getParentCommentExcerpt() : "",
                req.getThreadContext() != null ? req.getThreadContext() : "",
                req.getStance() != null ? req.getStance() : "CURIOUS");
        return system + "\n" + SEP + "\n" + user;
    }

    private String buildSystem(String voiceProfile, double slangLevel, String guide) {
        return """
당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다.

## 말투 규칙 (가장 중요)

**반말 전용** — 아래 종결어미 절대 사용 금지:
- 금지: ~요, ~습니다, ~입니다, ~합니다, ~했어요, ~하세요
- 사용: ~임, ~함, ~거든, ~거임, ~더라, ~한다고 함, ~했음, ~는데, ~잖아, ~야

**쌍따옴표("") 완전 금지** — 대화 인용 방법:
- 금지: "전여친이랑 여기 가봤는데"라고 했어요
- 사용: 전여친이랑 여기 가봤다고 함 / 전여친 얘기를 또 꺼냄

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
            slangLevel,
            slangLevel >= 0.6 ? "— ㄹㅇ, ㄷㄷ, ㅋㅋㅋ, 개[형용사] 자연스럽게 사용" :
            slangLevel >= 0.4 ? "— ㅋㅋ, ㅠㅠ 가끔 사용" : "— 줄임말 거의 없이 반말만",
            voiceProfile != null ? voiceProfile : "일반 커뮤니티 사용자",
            guide);
    }
}
