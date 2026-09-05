package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PromptAssembler {
    private static final String SEP = "<<<USER_PROMPT>>>";

    private final Map<String, String> classpathGuides = new ConcurrentHashMap<>();
    private static final List<String> GUIDE_KEYS = List.of(
        "voice/post", "voice/comment", "voice/reply", "voice/partner", "voice/post_paired_author",
        "voice/post_casual", "voice/reconstruct", "voice/paired_phase1", "voice/paired_phase2");

    @PostConstruct
    public void loadGuides() { reload(); }

    /** classpath 가이드를 다시 읽는다. DB는 보지 않는다(무상태 워커, 2026-09). */
    public synchronized void reload() {
        for (String key : GUIDE_KEYS) {
            classpathGuides.put(key, loadResourceOrEmpty(key + ".md"));
        }
        log.info("Voice guides loaded from classpath: {}", GUIDE_KEYS);
    }

    /** 요청 오버라이드 > classpath > "". 반환값은 String.formatted 안전(% 이스케이프). */
    public String guide(String key, Map<String, String> overrides) {
        String v = overrides == null ? null : overrides.get(key);
        if (v == null || v.isBlank()) v = classpathGuides.getOrDefault(key, "");
        return v.replace("%", "%%");
    }

    /**
     * persona-diversity-v4 계약4 — legacy {@code /generate/post}(assemblePostPrompt 및 그 분기)도
     * personaCard가 있으면 그것을("## 페르소나 특성" 섹션), 없으면 기존 voiceProfile 문자열을 쓴다.
     */
    private static String personaVoice(PostGenRequest req) {
        return (req.getPersonaCard() != null && !req.getPersonaCard().isBlank())
                ? req.getPersonaCard()
                : req.getVoiceProfile();
    }

    /** persona-diversity-v4 계약4 — {@link #personaVoice(PostGenRequest)}와 동일 규칙을 댓글에 적용. */
    private static String personaVoice(CommentGenRequest req) {
        return (req.getPersonaCard() != null && !req.getPersonaCard().isBlank())
                ? req.getPersonaCard()
                : req.getVoiceProfile();
    }

    /** persona-diversity-v4 계약4 — {@link #personaVoice(PostGenRequest)}와 동일 규칙을 대댓글에 적용. */
    private static String personaVoice(ReplyGenRequest req) {
        return (req.getPersonaCard() != null && !req.getPersonaCard().isBlank())
                ? req.getPersonaCard()
                : req.getVoiceProfile();
    }

    /** persona-diversity-v4 계약4 — {@link #personaVoice(PostGenRequest)}와 동일 규칙을 rewrite 경로에 적용. */
    private static String personaVoice(PostRewriteRequest req) {
        return (req.getPersonaCard() != null && !req.getPersonaCard().isBlank())
                ? req.getPersonaCard()
                : req.getVoiceProfile();
    }

    private String loadResourceOrEmpty(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
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
        "배우자 호칭: '부인' 금지 → '아내' 또는 '와이프'로. '부인'은 공문서 투라 커뮤니티에서 어색.",
        "감지 표현: '느껴버렸어요' 조합 금지 → '쎄하다', '낌새를 느끼다', '불현듯 생각났다'로.",
        "서두 나레이터 투 금지: '힘든 경험을 했는데요', '이런 일이 있었는데요' 식 시작 절대 금지.",
        // Phase 6 — T4·T2 종결 다양화 시드 (2026-06-21)
        "[종결 다양화] 이번 글은 강한 감정·분노로 끝내기 — 마지막 문장에서 폭발하는 감정 표현 (느낌표 포함).",
        "[종결 다양화] 이번 글은 혼란·막막함으로 끝내기 — 하소연하다 막혀버리는 톤 (이중질문 구조는 피할 것).",
        "[종결 다양화] 이번 글은 체념·포기로 끝내기 — '그냥', '됐어', '아 모르겠다' 류로 자연스럽게 끝내기.",
        "[종결 다양화] 이번 글은 상황을 완결된 장면으로 끝내기 — 글이 끊기지 않고 구체적 행동이나 결말로 마무리.",
    };
    // Phase 6 — T3 광장-내용 불일치 방지 가이드 (2026-06-21)
    private static final java.util.Map<String, String> CATEGORY_GUIDE = java.util.Map.of(
        "COUPLE",  "연인 관계(남친·여친·전남친·전여친) 갈등만 작성. 가족·직장·친구 갈등은 이 광장에 맞지 않음.",
        "MARRIED", "부부·배우자·시댁·처가 갈등만. 미혼 연인 이야기는 제외.",
        "FRIEND",  "친구 관계 갈등만. 연인·가족·직장 갈등은 이 광장에 맞지 않음.",
        "FAMILY",  "가족(부모·형제자매·친척) 갈등만. 연인·직장·친구 이야기는 제외.",
        "WORK",    "직장·업무·상사·동료 갈등만. 연인·가족·친구 갈등은 이 광장에 맞지 않음.",
        "OTHER",   ""
    );
    private static final java.util.Random PROMPT_RNG = new java.util.Random();

    private String lengthInstruction(String tier) {
        if (tier == null) return "자연스러운 길이로.";
        // ⚠️ backend 사연 본문 제한 1000자(PostCreateRequest @Size) — 모든 티어가 이 이하여야 게시됨
        return switch (tier) {
            case "SHORT"    -> "아주 짧게 — 50~120자. 핵심 상황 하나만 툭 던지는 초단 글.";
            case "MEDIUM"   -> "짧게 — 150~350자. 상황과 감정 간략히.";
            case "LONG"     -> "보통 — 400~650자. 사건 흐름 상세히.";
            case "VERYLONG" -> "길게 — 650~950자. 길게 쏟아내는 글. 사족·반복·감정 흐름 자연스럽게 포함 (절대 950자 넘기지 말 것).";
            default         -> "자연스러운 길이로.";
        };
    }

    public String assemblePostPrompt(PostGenRequest req) {
        // PARTNER stance이면 별도 파트너 프롬프트로 분기
        if ("PARTNER".equalsIgnoreCase(req.getStance()) && req.getCounterpartBody() != null && !req.getCounterpartBody().isBlank()) {
            return assemblePartnerPrompt(req);
        }
        // AUTHOR stance = 양면 사연의 작성자(A) — 상대방(B)이 곧 답할 전제
        if ("AUTHOR".equalsIgnoreCase(req.getStance())) {
            return assembleAuthorPairedPrompt(req);
        }
        // 재구성 모드: 단일 크롤 원본 골격(skeleton)을 페르소나 보이스로 사연화.
        // persona-diversity-v4 계약7 — 원문(sourceBody)이 아니라 sourceContext(골격 JSON)로 게이팅한다.
        if (req.isReconstructMode() && req.getSourceContext() != null && !req.getSourceContext().isEmpty()) {
            return assembleReconstructPrompt(req);
        }
        // R9 Track B: 일상 글 모드 — 갈등 서사 금지, 사건 의무 없음 (D-51)
        if ("CASUAL".equalsIgnoreCase(req.getPostKind())) {
            return assembleCasualPostPrompt(req);
        }
        // 기존 로직 유지 (단독 사연 — stance 미지정)
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), guide("voice/post", req.getPromptOverrides()), req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), req.getReconstructionRules());
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        // Phase 6 T3: 광장 가이드 추출 (catGuide)
        String catGuide = CATEGORY_GUIDE.getOrDefault(req.getCategory() != null ? req.getCategory() : "OTHER", "");
        // 랜덤 다양성 시드 (50% 확률로 1개 추가)
        String varietySeed = PROMPT_RNG.nextBoolean()
            ? "\n[스타일 힌트] " + VARIETY_SEEDS[PROMPT_RNG.nextInt(VARIETY_SEEDS.length)] : "";
        String user = """
            %s카테고리: %s%s
            아키타입: %s
            %s
            글 길이: %s
            %s
            %s
            위 카테고리와 말투로 내부 synthetic 페르소나용 한국 갈등 커뮤니티 사연을 완전 창작해주세요.
            - 출력 형식: 첫 줄=제목(공백 포함 12~40자), 빈 줄, 그다음 본문. 제목과 본문은 서로 다른 텍스트여야 함(동일 문자열 금지)
            - 🚨 구체적 사건 필수: "어제/지난주에 X가 Y를 했다" 형태의 사건 1개 이상 포함. 감정만 나열하는 한탄 글 금지.
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - 실제 사건 원문 복제 금지 (완전 창작)
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            - ⚠️ 단어: '부인' 금지(→ 아내/와이프) · 나레이터 투 도입부 금지('힘든 경험을 했는데요' 류)
            %s%s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getCategory() != null ? req.getCategory() : "OTHER",
                catGuide.isBlank() ? "" : " (⚠️ " + catGuide + ")",
                req.getArchetype() != null ? req.getArchetype() : "일반갈등",
                req.getTopicSeed() != null ? "상황: " + safe(req.getTopicSeed()) : "",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                situationContinuityBlock(req.getOngoingSituation()),
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들에서 다룬 갈등 유형을 먼저 파악하고, 이번엔 완전히 다른 유형의 갈등 상황으로 쓸 것 — 같은 사건을 각도만 바꾸거나 요약하는 것도 실격"),
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    public String assemblePostRewritePrompt(PostRewriteRequest req) {
        String guideText = guide("voice/post", req.getPromptOverrides());
        if (guideText.isBlank()) guideText = "기존 갈등 사연을 자연스럽게 교정한다";
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), guideText, req.getFormality(),
            req.getCorrectionCautions(), req.getGlobalForbidRules(), null);
        String sourceCategory = req.getCategory() != null ? req.getCategory() : "OTHER";
        String targetCategory = req.getTargetCategory() != null ? req.getTargetCategory() : sourceCategory;
        String sourceGuide = CATEGORY_GUIDE.getOrDefault(sourceCategory, "");
        String targetGuide = CATEGORY_GUIDE.getOrDefault(targetCategory, "");
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말 유지 (~요, ~더라고요, ~거든요)\n"
            : "- 반말 유지 (~임, ~함, ~거든, ~하더라)\n";
        String user = """
            %s[현재 제목]
            %s

            [현재 본문]
            %s

            현재 광장: %s%s
            최종 광장: %s%s
            %s
            위 legacy synthetic 사연을 새 글로 갈아엎지 말고, 어색한 부분만 자연스럽게 교정해주세요.
            - 사건·사실관계·감정 방향은 유지
            - 중복 표현, 부자연스러운 AI 말투, placeholder/debug 흔적만 정리
            - 최종 광장과 어긋나면 그 광장 맥락에 맞게 최소한만 재프레이밍
            - 제목은 12~40자(공백 포함, 40자 초과 금지), 본문은 제목과 다른 텍스트로 180~520자 목표·최대 900자
            - 제목=본문 동일 문자열 금지 — 제목은 훅, 본문은 사건 전개
            - 체크리스트, 설명문, 분석문, 코드펜스 절대 금지
            - 결과는 JSON 1개만 출력: {"title":"...","body":"..."}
            %s%s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                safe(req.getOriginalTitle() != null ? req.getOriginalTitle() : ""),
                safe(req.getOriginalBody() != null ? req.getOriginalBody() : ""),
                sourceCategory,
                sourceGuide.isBlank() ? "" : " (참고: " + sourceGuide + ")",
                targetCategory,
                targetGuide.isBlank() ? "" : " (참고: " + targetGuide + ")",
                req.getRewriteInstruction() != null && !req.getRewriteInstruction().isBlank()
                    ? "추가 지시: " + safe(req.getRewriteInstruction()) + "\n"
                    : "",
                politeSuffix,
                targetCategory.equals(sourceCategory) ? "" : "- 카테고리 이동은 최소 표현 조정만 허용, 핵심 사건 변경 금지\n");
        return system + "\n" + SEP + "\n" + user;
    }

    /**
     * 재구성 프롬프트 — 크롤 원문이 아니라 골격(SKELETON) JSON을 페르소나 보이스로 재서사.
     * post.md 가이드의 "실제 사건 원문 복제 금지(완전 창작)" 규칙과 충돌하므로
     * 별도 reconstruct 가이드를 사용하고 voice/post 가이드를 쓰지 않음.
     * 요청 오버라이드 또는 classpath에 voice/reconstruct 키가 있으면 그것을, 없으면 인라인 가이드를 사용.
     *
     * <p>persona-diversity-v4 계약7/레거시 배선 — 크롤 원문은 이 경로에 절대 실리지 않는다.
     * {@code req.getSourceContext()}는 llm 워커 {@code /v2/extract-skeleton}이 만든 뼈대 JSON뿐이고,
     * 고유명사·금액·날짜가 이미 일반화돼 있다({@code StructuredGenerationService.RECONSTRUCT_RULE}과
     * 동일한 원칙). 골격 추출이 실패하면 호출자(레거시 {@code ActionExecutor})가 이 메서드 자체를
     * 호출하지 않고 그 글 생성을 건너뛴다 — 원문 폴백 없음.</p>
     */
    private String assembleReconstructPrompt(PostGenRequest req) {
        String reconstructGuide = guide("voice/reconstruct", req.getPromptOverrides());
        if (reconstructGuide == null || reconstructGuide.isBlank()) {
            reconstructGuide = """
아래 지시에 따라 소스 골격(SKELETON)을 한국 갈등 커뮤니티 스타일 사연으로 재구성합니다.
SKELETON은 원문이 아니라 사건·역할·시퀀스만 남긴 뼈대다 — 이 자체를 그대로 옮기지 마라.

## 재구성 규칙
- SKELETON은 뼈대다. 등장인물·직장·동네·금액·기간·순서의 세부는 페르소나 삶에 맞게 전부 새로 정한다
- 반드시 "누가 무엇을 했다" 형태의 구체 사건 1개를 중심에 둔다. 감정 나열만 하는 글은 실격
- 실명·연락처·주소 등 개인정보를 **완전히 제거·변환**
- A(작성자)/B(상대방) 이분법 유지
- 다시봄 커뮤니티 문체: 온점(.) 금지, 쌍따옴표 금지, 한국 구어체
""";
        }
        String system = buildSystem(personaVoice(req), req.getSlangLevel(),
                reconstructGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), req.getReconstructionRules());
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        String user = """
            [SKELETON — 재구성 대상 뼈대, 원문 아님]
            %s

            카테고리: %s
            글 길이: %s
            %s
            위 SKELETON을 바탕으로 '다시봄' 내부 synthetic 사연 예시로 재구성해주세요.
            - 출력 형식: 첫 줄=제목(공백 포함 12~40자), 빈 줄, 그다음 본문. 제목≠본문(동일 문자열 금지)
            - SKELETON 뼈대는 유지하되 등장인물·직장·동네·금액·기간·세부 표현은 전부 새로 창작
            - 뼈대 필드를 문장 그대로 옮기지 말 것 — "누가 무엇을 했다" 형태로 새로 서술
            - 개인정보(실명·연락처·주소) 완전 제거 또는 일반화
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지
            %s%s""".formatted(
                safe(skeletonJson(req.getSourceContext())),
                req.getCategory() != null ? req.getCategory() : "OTHER",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                politeSuffix,
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들과 같은 소재·사건 유형 반복 금지"));
        return system + "\n" + SEP + "\n" + user;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper SKELETON_JSON =
        new com.fasterxml.jackson.databind.ObjectMapper();

    /** SKELETON 맵을 JSON 문자열로 직렬화. 실패·빈 값이면 빈 객체 문자열. */
    private static String skeletonJson(Map<String, Object> sourceContext) {
        if (sourceContext == null || sourceContext.isEmpty()) return "{}";
        try {
            return SKELETON_JSON.writeValueAsString(sourceContext);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * R9 Track B: 일상 글 모드 프롬프트 (D-51).
     * 갈등 서사 금지, 사건(trigger) 의무 없음. user 블록이 system의 "핵심 4가지" 보다 구체적으로 지시.
     * guide = guide("voice/post_casual", overrides) → buildSystem의 "커뮤니티 스타일 가이드" 섹션에 주입.
     */
    private String assembleCasualPostPrompt(PostGenRequest req) {
        // 일상 가이드: 없으면 인라인 최소 기본값 사용
        String casualGuide = guide("voice/post_casual", req.getPromptOverrides());
        if (casualGuide.isBlank()) {
            casualGuide = "일상 글 모드 — 갈등/분쟁 서사 금지. 일상 관찰·취향·수다·경험 공유. 큰 결론 없이 끝내도 됨.";
        }
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), casualGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), null);
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        String varietySeed = PROMPT_RNG.nextBoolean()
            ? "\n[스타일 힌트] " + VARIETY_SEEDS[PROMPT_RNG.nextInt(VARIETY_SEEDS.length)] : "";
        String user = """
            %s카테고리: %s
            %s
            글 길이: %s
            %s
            %s
            위 카테고리와 말투로 내부 synthetic 페르소나용 일상 한국 커뮤니티 글만 써줘. 분석·설명·체크리스트 절대 금지.
            - 출력 형식: 첫 줄=제목(공백 포함 12~40자), 빈 줄, 그다음 본문. 제목≠본문(동일 문자열 금지)
            - 🚨 갈등 서사 금지 — 연애·가족·직장 분쟁 이야기 절대 금지. 일상 관찰·취향·수다·경험 공유로.
            - 사건(trigger) 의무 없음 — "X가 Y를 했다" 형태 불필요. 큰 결론·해결책 없이 끝내도 됨.
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지 — 한국 커뮤니티 문체만 따를 것
            - ⚠️ "문체 분석", "✅", 체크리스트, 설명문 출력 절대 금지 — 커뮤니티 글 본문만
            - ⚠️ 단어: '부인' 금지(→ 아내/와이프) · 나레이터 투 도입부 금지('힘든 경험을 했는데요' 류)
            %s%s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getCategory() != null ? req.getCategory() : "OTHER",
                req.getTopicSeed() != null ? "주제: " + safe(req.getTopicSeed()) : "",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                situationContinuityBlock(req.getOngoingSituation()),
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들과 다른 주제·내용으로"),
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    /**
     * 양면 사연 작성자(A) — stance=AUTHOR.
     * 상대방(B)이 같은 사건을 다른 시각으로 받아칠 앵커를 남긴다.
     */
    private String assembleAuthorPairedPrompt(PostGenRequest req) {
        String pairedGuide = guide("voice/post_paired_author", req.getPromptOverrides());
        if (pairedGuide.isBlank()) pairedGuide = guide("voice/post", req.getPromptOverrides());
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), pairedGuide, req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), null);
        String politeSuffix = isPolite(req.getFormality())
            ? "- 자연스러운 구어 존댓말로 작성 (~요, ~어요, ~더라고요)\n"
            : "- 반말로 작성 (~임, ~함, ~거든, ~거임)\n";
        String catGuide = CATEGORY_GUIDE.getOrDefault(req.getCategory() != null ? req.getCategory() : "OTHER", "");
        String varietySeed = PROMPT_RNG.nextBoolean()
            ? "\n[스타일 힌트] " + VARIETY_SEEDS[PROMPT_RNG.nextInt(VARIETY_SEEDS.length)] : "";
        String user = """
            %s카테고리: %s%s
            아키타입: %s
            %s
            글 길이: %s
            %s
            %s
            위 카테고리와 말투로 **양면 사연의 작성자(A)** 입장을 1인칭으로 완전 창작해주세요.
            곧 상대방(B)도 같은 사건에 대해 자기 입장을 따로 씁니다.
            - 출력 형식: 첫 줄=제목(공백 포함 12~40자), 빈 줄, 그다음 본문. 제목≠본문
            - 🚨 구체 사건 필수: 상대가 재해석할 수 있는 행동·말·날짜 앵커 포함
            - 🚨 상대 속마음·의도를 단정하지 말 것 — 보이는 사실 + 내 감정만
            - 상대를 대신해 변명하거나 상대 입장을 요약하지 말 것
            - 실제 인물 실명·연락처·주소·개인정보 절대 포함 금지
            - ⚠️ 문장 끝 온점(.) 금지·쌍따옴표 금지
            - ⚠️ 단어: '부인' 금지(→ 아내/와이프) · 나레이터 투 도입부 금지
            %s%s""".formatted(
                req.getDemographic() != null && !req.getDemographic().isBlank() ? "사용자 프로필: " + safe(req.getDemographic()) + "\n" : "",
                req.getCategory() != null ? req.getCategory() : "OTHER",
                catGuide.isBlank() ? "" : " (⚠️ " + catGuide + ")",
                req.getArchetype() != null ? req.getArchetype() : "일반갈등",
                req.getTopicSeed() != null ? "상황: " + safe(req.getTopicSeed()) : "",
                lengthInstruction(req.getLengthTier()),
                dynamicExamplesBlock(req.getDynamicExamples()),
                situationContinuityBlock(req.getOngoingSituation()),
                recentOutputsBlock(req.getRecentOutputs(), "글", "위 글들과 같은 갈등 유형·사건 반복 금지"),
                politeSuffix,
                varietySeed);
        return system + "\n" + SEP + "\n" + user;
    }

    private String assemblePartnerPrompt(PostGenRequest req) {
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), guide("voice/partner", req.getPromptOverrides()), req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), null);
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
            위 원글의 **상대방(B)** 으로서, 작성자와 같은 무게의 사연 본문을 1인칭으로 작성해주세요.
            - 제목 줄 없이 본문만 출력
            - 원글에 나온 구체 사건을 반드시 재참조하되, 해석·감정은 내 시각으로
            - 작성자 본문과 비슷한 밀도 — 한 줄 반박·해명으로 끝내지 말 것
            - 방어적 해명보다 내가 느끼고 있는 것(피로·억울함·혼란·서운함) 중심
            - 원글에 없는 새 사건 추가 금지
            - 실제 인물 실명·개인정보 절대 포함 금지
            - 작성자 글을 요약·평가하는 메타 발화 금지
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
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), guide("voice/comment", req.getPromptOverrides()), req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), null, true);
        String toneNote = isPolite(req.getFormality())
            ? "- 존댓말로 작성 (~요, ~어요, ~더라고요, ~것 같아요)"
            : "- 반말로 작성 (요/습니다 금지)";
        // 모드 힌트가 있으면 고정 길이 지시 대신 모드별 지시 사용 (문체 현실화 S3)
        // 2026-06-16: 모드 미제공 시 fallback도 초단문화 (인간 댓글 MAUVE 맞춤)
        String lengthLine = req.getModeHint() != null && !req.getModeHint().isBlank()
            ? "- " + safe(req.getModeHint())
            : "- 초단문 필수: 10~35자 (한 줄, 최대 두 마디까지만)";
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
            이 글에 달 내부 synthetic 페르소나용 짧은 댓글을 작성해주세요.
            🚨 **반드시 초단문**. 실제 댓글은 한 줄, 평균 2~5어절, 최대 30~50자다.
            - 설명·분석·조언 문단으로 늘리지 말 것
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
        String system = buildSystem(personaVoice(req), req.getSlangLevel(), guide("voice/reply", req.getPromptOverrides()), req.getFormality(),
                req.getCorrectionCautions(), req.getGlobalForbidRules(), null, true);
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
            이 댓글에 대한 내부 synthetic 페르소나용 자연스러운 대댓글을 작성해주세요.
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

    /**
     * 게시 직전 맞춤법 교정 프롬프트 — 의미·사건·인물관계·문단구조 변경 없이 오탈자만 수정.
     * persona/voice/category 컨텍스트를 의도적으로 주입하지 않는다 (좁은 목적의 호출).
     * formatted() 미사용 → 본문의 % 안전 (assemblePostAnalysisPrompt와 동일 이유).
     * 2026-08-16 shortform-content-quality fix.
     */
    public String assembleProofreadPrompt(ProofreadRequest req) {
        String body = req.getBody() != null ? req.getBody() : "";

        String system = """
당신은 한국어 맞춤법 교정 전문가입니다. 사용자가 준 글의 맞춤법·오탈자·자모 결합 오류만 고칩니다.
- 의미, 사건 사실, 인물 관계, 문단 구조를 절대 바꾸지 않습니다.
- 문장을 추가·삭제·재배열하지 않습니다. 줄바꿈 위치를 그대로 유지합니다.
- 구어체·슬랭·말투·문장 길이는 그대로 유지합니다 — 표준어나 문어체로 다듬지 않습니다.
- 설명, 분석, 체크리스트, 따옴표 장식을 절대 출력하지 않습니다.
- 고칠 부분이 없으면 원문을 그대로 반환합니다.
- 결과는 JSON 1개만 출력합니다: {"corrected_body":"..."}""";

        StringBuilder user = new StringBuilder();
        user.append("[원문]\n").append(body).append("\n\n위 글을 교정해 JSON 1개만 출력하세요.");

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
               "[참고 코퍼스 예시 — 당신의 말투(반말/해요체)와 같은 register로 선별됨]\n" +
               "이 예시들의 종결어미·문장 호흡·끊는 방식·어휘 톤을 모방하라\n" +
               "단, 내용·구체 표현·온점·쌍따옴표는 모방 금지 (온점·쌍따옴표는 항상 제거)\n" +
               "───────────────────────────────────────\n" +
               safe(normalized) + "\n" +
               "───────────────────────────────────────\n";
    }

    /**
     * Phase 3: 상황 연속성 블록 — 진행 중인 상황(ongoing_situation)을 제공해 자연스러운 saga 이어가기 유도.
     * 호출마다 변하는 내용이므로 반드시 USER 섹션에만 주입 (캐시 prefix 보호).
     */
    private String situationContinuityBlock(String situation) {
        if (situation == null || situation.isBlank()) return "";
        return "\n[이전 사연 흐름] 최근 이런 상황을 썼음: " + safe(situation) + "\n" +
               "→ 이번 글에서 이 상황을 자연스럽게 발전·드리프트시키거나, 새로운 각도로 이어가도 좋음\n";
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
               "[참고 문체 샘플 — 주제 무관, 말투만 참고]\n" +
               "종결어미·문장 호흡·끊는 방식·리듬만 모방하라\n" +
               "내용·소재·구체 표현은 절대 가져오지 말 것 (온점·쌍따옴표도 항상 제거)\n" +
               "───────────────────────────────────────\n" +
               safe(normalized) + "\n" +
               "───────────────────────────────────────\n";
    }

    private boolean isPolite(String formality) {
        return "polite".equalsIgnoreCase(formality);
    }

    /** 게시글류(post/rewrite/reconstruct/casual/paired-author/partner) 전용 — 오타 재현 지시 제외. */
    private String buildSystem(String voiceProfile, double slangLevel, String guide, String formality,
                               String correctionCautions, String globalForbidRules, String reconstructionRules) {
        return buildSystem(voiceProfile, slangLevel, guide, formality,
            correctionCautions, globalForbidRules, reconstructionRules, false);
    }

    /**
     * @param includeTypoInstruction consistent_errors/mobile_typos 오타 재현 지시 포함 여부.
     *        댓글/대댓글은 true, 공개 사연(글) 계열은 false — 오타는 게시 전 별도 교정 단계로만
     *        걸러진다 (2026-08-16 shortform-content-quality fix). <<<PERSONA_SECTION>>> 마커
     *        뒤(가변 영역)에서만 분기하므로 캐싱 prefix 불변식은 깨지지 않는다.
     */
    private String buildSystem(String voiceProfile, double slangLevel, String guide, String formality,
                               String correctionCautions, String globalForbidRules, String reconstructionRules,
                               boolean includeTypoInstruction) {
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
        String reconstructionRulesSection = (reconstructionRules != null && !reconstructionRules.isBlank())
            ? "\n## 재구성 규칙 (원본 → 다시봄 사연 변환 시 준수)\n" + reconstructionRules.replace("%", "%%")
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
        // 2026-06-12 토큰 다이어트: clcocloud 캐싱 신뢰불가 확정 → 정적 prefix 자체를 축소.
        // 규칙은 무손실, ❌/✅ 예시만 각 1개로 압축. 상세 예시는 가이드·동적 주입이 담당.
        return """
아래 지시에 따라 자연스러운 한국 갈등 커뮤니티 텍스트를 창작합니다.

## 핵심 4가지 (가장 중요)

### 0. 🚨 구체적 사건(trigger) 필수 — 감정만 있는 한탄 글 절대 금지
- ✅ "어제 퇴근하고 힘들었다고 했는데 남친이 내 말 중간에 자기 게임 얘기로 바꿨어. 이번 달만 세 번째야"
- **"X가 Y를 했다" 사건 1개 이상** + 언제(어제/지난주)·어떤 행동(끊었다/취소했다)·몇 번(세 번째) 중 최소 2가지

### 1. 배경 50%% 축소 — 본 이야기로 빠르게 진입. 배경은 최대 1~2줄, 나머지는 갈등 상황+감정

### 2. 감정 토로 강화 — "나는", "내가" 연속 사용, 1인칭 감정 폭발 우선 (주인공성)
- ✅ "내가 말해도 남편은 안 들어. 나는 계속 답답하고. 내가 뭐가 잘못한 건가"

### 3. 미완성감 유지 — 결론·해결책 제시 금지, 질문/혼란으로 끝내기
- ✅ "그래서 지금 내가 뭘 해야 하는지 몰라. 진짜 이대로는 안 될 것 같은데..."

## 한국 커뮤니티 스타일 규칙 (절대 준수)

**온점(.) 사용 금지** — 문장 끝에 온점을 붙이지 않음. 줄바꿈·ㅠ·ㅋ·...로 끊거나 그냥 끊기. 말줄임표(...)·물음표(?)·느낌표(!)는 허용
- "정말 황당했음." ❌ → "정말 황당했음" ✅

**쌍따옴표("") 사용 금지** — 간접화법으로 풀어쓰기
- 남자친구가 "전여친이 더 예뻤다"고 했어 ❌ → 남자친구가 전여친이 더 예뻤다고 했어 ✅

## 창작 금지 (항상 준수)
- 실명·연락처·주소 등 개인정보 포함 금지 / 실제 사건 원문 복제 금지 (완전 창작)

## Synthetic 페르소나 경계
- 이 작업은 AGAIN SPRING 내부 synthetic=1 페르소나 코퍼스용 허구 예시 작성이다
- 특정 실존 인물·실사용자·외부 커뮤니티 사용자를 사칭하지 않는다
- 스타일만 참고하고 인물·신상·실제 사건·원문을 재현하지 않는다
- 규칙·주의사항을 "적용 처리 메모", "[작성 노트]", 표, 체크리스트로 다시 출력하지 말고 본문만 출력한다

## 커뮤니티 스타일 가이드
%s

<<<PERSONA_SECTION>>>
## 페르소나 특성 — 이번 호출에서 가장 먼저, 가장 무겁게 반영할 것
아래 [말투] 줄은 배경 설명이 아니라 실행 명령이다. "직설/방어"처럼 성격을 묘사하는 게 아니라
"욕설을 실제로 섞어 쓴다"처럼 **이번 글·댓글에 그대로 실행해야 하는 지시**로 적혀 있다.
각 줄은 축=값 형태로 시작한다(예: profanity=HEAVY) — 그 값을 텍스트에 실제로 반영했는지
스스로 다시 확인하고 출력할 것. 아래 페르소나 특성이 이어지는 "말투 규칙"보다 우선한다.
%s
%s%s%s

## 말투 규칙 (가장 중요)

%s
%s
슬랭 수준 %.1f/1.0 %s""".formatted(
            safeGuide,
            safeVoice,
            cautionsSection,
            globalRulesSection,
            reconstructionRulesSection,
            speechRules,
            includeTypoInstruction
                ? "\n**자연스러운 구어체** — 페르소나 특성의 writing_quirks에 consistent_errors가 있으면 그 오류 패턴을 **일관되게** 재현. mobile_typos: true이면 모바일 오탈자(자모분리·인접키) 2~3개 자연스럽게 포함. 맞춤법이 완벽할 필요 없음.\n"
                : "\n**자연스러운 구어체** — 문장 길이·어미·쉼표 밀도는 자유롭게 다양화하되, 맞춤법과 띄어쓰기는 정확하게 지킬 것. 의도적인 오탈자는 넣지 않음.\n",
            slangLevel,
            slangGuide);
    }
}
