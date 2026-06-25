package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.GenDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.service.DailyPostQuotaService;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * AI 유저 양면 갈등 시나리오 스케줄러.
 *
 * 흐름:
 *  1. persona_relationships에서 COUPLE/MARRIAGE/FRIEND 페어 선택
 *  2. 작성자 A → 갈등 사연 생성 + PRIVATE 게시 + WAIT_FOR_PARTNER 설정 + 초대 토큰 발급
 *  3. 파트너 B → 작성자 본문을 컨텍스트로 상대방 입장 생성 + /api/s/{token}/answer 제출
 *  4. WAIT_FOR_PARTNER → 자동 PUBLIC 전환 → 기존 BehaviorEngine tick이 댓글·투표 참여
 *
 * 스케줄 기본값: 매일 UTC 05:00 (KST 14:00)
 * 환경변수:
 *  - PAIRED_POST_ENABLED / PAIRED_POST_CRON / PAIRED_POST_PAIRS
 *  - PAIRED_POST_TARGET_SHARE (default 0.15)
 *  - PAIRED_POST_ROMANTIC_SHARE (default 0.80)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PairedPostScheduler {

    private final PersonaRelationshipRepository relationshipRepo;
    private final PersonaRepository personaRepo;
    private final BackendBotClient backendBot;
    private final LlmAiUserClient llmClient;
    private final BotTokenCache tokenCache;
    private final OrchestratorProperties props;
    private final JdbcTemplate jdbcTemplate;
    private final ContentSafetyGuard safetyGuard;
    private final AiUserGenerationConfigRepository generationConfigRepository;
    private final DailyPostQuotaService dailyPostQuotaService;

    private static final Random RNG = new Random();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> PAIR_TYPES = List.of("COUPLE", "MARRIAGE", "FRIEND");

    /** 매일 KST 14:00 (UTC 05:00) 실행 — 환경변수로 재정의 가능 */
    @Scheduled(cron = "${ai-user.paired-post.cron:0 0 5 * * *}")
    public void runPairedPosts() {
        if (!props.isEnabled()) {
            log.debug("[PairedPost] AI_USER_ENABLED=false — skip");
            return;
        }
        OrchestratorProperties.PairedPost config = props.getPairedPost();
        if (config == null || !config.isEnabled()) {
            log.debug("[PairedPost] disabled — skip");
            return;
        }
        List<PersonaRelationship> all =
            relationshipRepo.findByRelationTypeInAndStatus(PAIR_TYPES, "ACTIVE");
        if (all.isEmpty()) {
            log.warn("[PairedPost] No COUPLE/MARRIAGE/FRIEND relationships found. " +
                     "Seed ai-user/docs/personas/profiles/relationships.yml first.");
            return;
        }

        List<PersonaRelationship> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, RNG);

        int totalSyntheticToday = dailyPostQuotaService.postsCreatedToday();
        int pairedToday = countPairedPostsToday();
        int desiredToday = desiredPairedPostsToday(config, totalSyntheticToday);
        int remainingTarget = Math.max(0, desiredToday - pairedToday);
        int runCap = Math.max(0, config.getPairsPerRun());
        int toRun = Math.min(Math.min(runCap, remainingTarget), shuffled.size());

        if (toRun <= 0) {
            log.debug("[PairedPost] target satisfied — totalToday={} pairedToday={} desiredToday={} runCap={}",
                totalSyntheticToday, pairedToday, desiredToday, runCap);
            return;
        }

        EnumMap<PairBucket, Integer> mixCounts = countPairedMixToday();
        log.info("[PairedPost] Running {} pair(s) (pool={}, totalToday={}, pairedToday={}, desiredToday={}, romanticToday={}, friendToday={})",
            toRun,
            all.size(),
            totalSyntheticToday,
            pairedToday,
            desiredToday,
            mixCounts.get(PairBucket.ROMANTIC),
            mixCounts.get(PairBucket.FRIEND));

        for (int i = 0; i < toRun; i++) {
            PairBucket desiredBucket = chooseNextBucket(config, mixCounts);
            Optional<PersonaRelationship> relOpt = takeCandidateForBucket(shuffled, desiredBucket);
            if (relOpt.isEmpty()) {
                relOpt = takeAnyCandidate(shuffled);
            }
            if (relOpt.isEmpty()) {
                break;
            }

            try {
                PersonaRelationship rel = relOpt.get();
                executePair(rel);
                mixCounts.merge(bucketForRelationType(rel.getRelationType()), 1, Integer::sum);
            } catch (Exception e) {
                log.error("[PairedPost] Pair {} failed: {}", i, e.getMessage(), e);
            }
        }
    }

    /** 수동 즉시 실행 (테스트·어드민용) */
    public void triggerNow() {
        runPairedPosts();
    }

    /** 관리자 설정 기반 backend 조회 — AiUserGenerationConfig.backend_post 우선. */
    private String backendForPost() {
        return generationConfigRepository.findById(1)
            .map(c -> c.effectiveBackend("POST"))
            .orElse("CLI");
    }

    // ── 핵심 실행 흐름 ─────────────────────────────────────────────────────────

    private void executePair(PersonaRelationship rel) {
        Optional<Persona> authorOpt = personaRepo.findById(rel.getPersonaId());
        Optional<Persona> partnerOpt = personaRepo.findById(rel.getOtherId());
        if (authorOpt.isEmpty() || partnerOpt.isEmpty()) {
            log.warn("[PairedPost] Persona not found — personaId={} otherId={}",
                rel.getPersonaId(), rel.getOtherId());
            return;
        }
        Persona author  = authorOpt.get();
        Persona partner = partnerOpt.get();

        String category = categoryForRelationType(rel.getRelationType());
        String corrId   = UUID.randomUUID().toString().substring(0, 8);

        // ── Step 1: 작성자 JWT ────────────────────────────────────────────────
        String authorEmail = lookupEmail(author.getId());
        Optional<String> jwtOpt = tokenCache.getToken(author.getId(), authorEmail, props.getBotPassword());
        if (jwtOpt.isEmpty()) {
            log.warn("[PairedPost] No JWT for author {}", author.getId());
            return;
        }
        String jwt = jwtOpt.get();

        // ── Step 2: 작성자 본문 생성 (AUTHOR stance) ──────────────────────────
        String lengthTier = RNG.nextDouble() < 0.55 ? "MEDIUM" : "LONG";
        String backend = backendForPost();
        log.info("[PairedPost] backend={} corrId={}", backend, corrId);
        Optional<String> bodyOpt = llmClient.generatePost(GenDto.PostRequest.builder()
            .personaId(author.getId())
            .voiceProfile(buildVoiceBlock(author))
            .slangLevel(author.getSlangLevel().doubleValue())
            .category(category)
            .archetype(author.getArchetype())
            .formality(getFormality(author))
            .demographic(buildDemographic(author))
            .lengthTier(lengthTier)
            .stance("AUTHOR")
            .correlationId(corrId + "-A")
            .backend(backend)
            .build());
        if (bodyOpt.isEmpty()) {
            log.warn("[PairedPost] Author body gen failed corrId={}", corrId);
            return;
        }
        String authorBody = bodyOpt.get();
        ContentSafetyGuard.GuardResult authorGuard = safetyGuard.check(authorBody, ContentSafetyGuard.ContentType.POST);
        if (!authorGuard.passed()) {
            log.warn("[PairedPost] Author body blocked: {}", authorGuard.reason());
            return;
        }

        // ── Step 3: 글 작성 (PRIVATE + jurorCount=3) ──────────────────────────
        String title = extractTitle(authorBody);
        Optional<PostDto> postOpt = backendBot.createPost(jwt, CreatePostDto.builder()
            .userTitle(title)
            .bodyRaw(authorBody)
            .category(category)
            .visibility("PRIVATE")
            .jurorCount(3)
            .build());
        if (postOpt.isEmpty()) {
            log.warn("[PairedPost] Post creation failed corrId={}", corrId);
            return;
        }
        String postId = postOpt.get().getId();

        // ── Step 4: WAIT_FOR_PARTNER 모드 설정 ────────────────────────────────
        backendBot.setPublishMode(jwt, postId, "WAIT_FOR_PARTNER", 72);

        // ── Step 5: 초대 토큰 발급 ────────────────────────────────────────────
        Optional<String> inviteTokenOpt = backendBot.createInviteToken(jwt, postId);
        if (inviteTokenOpt.isEmpty()) {
            log.warn("[PairedPost] Invite token failed for post={}", postId);
            return;
        }
        String inviteToken = inviteTokenOpt.get();

        // ── Step 6: 작성자 발행 본문 조회 (상대방 프롬프트 컨텍스트) ──────────
        Optional<Map<String, Object>> postDetailOpt = backendBot.getPost(postId);
        String authorBodyPublished = postDetailOpt
            .map(d -> (String) d.get("bodyPublished"))
            .filter(b -> b != null && !b.isBlank())
            .orElse(authorBody);

        // ── Step 7: 파트너 본문 생성 (PARTNER stance + 원글 컨텍스트) ──────────
        Optional<String> partnerBodyOpt = llmClient.generatePost(GenDto.PostRequest.builder()
            .personaId(partner.getId())
            .voiceProfile(buildVoiceBlock(partner))
            .slangLevel(partner.getSlangLevel().doubleValue())
            .category(category)
            .archetype(partner.getArchetype())
            .formality(getFormality(partner))
            .demographic(buildDemographic(partner))
            .lengthTier("MEDIUM")
            .stance("PARTNER")
            .counterpartBody(authorBodyPublished)
            .correlationId(corrId + "-P")
            .backend(backend)
            .build());
        if (partnerBodyOpt.isEmpty()) {
            log.warn("[PairedPost] Partner body gen failed for post={}", postId);
            return;
        }
        String partnerBody = partnerBodyOpt.get();
        ContentSafetyGuard.GuardResult partnerGuard = safetyGuard.check(partnerBody, ContentSafetyGuard.ContentType.POST);
        if (!partnerGuard.passed()) {
            log.warn("[PairedPost] Partner body blocked: {}", partnerGuard.reason());
            return;
        }

        // ── Step 8: 파트너 답변 제출 → 자동 PUBLIC 전환 ─────────────────────
        boolean ok = backendBot.submitPartnerAnswer(inviteToken, null, partnerBody);
        if (ok) {
            log.info("[PairedPost] ✅ corrId={} post={} author={} partner={} cat={}",
                corrId, postId,
                author.getId().substring(0, 8),
                partner.getId().substring(0, 8),
                category);
        } else {
            log.warn("[PairedPost] Partner answer submission failed for post={}", postId);
        }
    }

    // ── 유틸리티 ──────────────────────────────────────────────────────────────

    /** voice_profile에서 LLM 주입용 voice 블록 조립 */
    @SuppressWarnings("unchecked")
    private String buildVoiceBlock(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();

        Object style = vp.get("general_style");
        if (style != null && !style.toString().isBlank()) sb.append(style.toString().trim());

        // 자주 쓰는 표현 (lexicon.signature_phrases)
        Object lexicon = vp.get("lexicon");
        if (lexicon instanceof Map<?, ?> lex) {
            Object phrases = lex.get("signature_phrases");
            if (phrases instanceof List<?> list && !list.isEmpty()) {
                sb.append("\n[자주 쓰는 표현] ");
                sb.append(list.subList(0, Math.min(3, list.size())).stream()
                    .map(Object::toString).reduce((a, b) -> a + " / " + b).orElse(""));
            }
        }

        // 맞춤법·오타 패턴 (writing_quirks.consistent_errors)
        Object quirks = vp.get("writing_quirks");
        if (quirks instanceof Map<?, ?> q) {
            Object errs = q.get("consistent_errors");
            if (errs instanceof List<?> elist && !elist.isEmpty()) {
                sb.append("\n[맞춤법·오타] ").append(elist.get(0));
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "일반 커뮤니티 사용자" : result;
    }

    /** voice_profile.formality 조회 (없으면 "casual") */
    private String getFormality(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "casual";
        Object f = vp.get("formality");
        return (f != null && !f.toString().isBlank()) ? f.toString() : "casual";
    }

    /** voice_profile.age/gender → 한국어 demographic 문자열 */
    private String buildDemographic(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return null;
        List<String> parts = new ArrayList<>();
        Object age = vp.get("age");
        if (age != null && !age.toString().isBlank()) {
            String kr = switch (age.toString()) {
                case "10s"       -> "10대";
                case "20s_early" -> "20대 초반";
                case "20s_late"  -> "20대 후반";
                case "30s_early" -> "30대 초반";
                case "30s_late"  -> "30대 후반";
                case "30s"       -> "30대";
                case "40s"       -> "40대";
                case "50s"       -> "50대";
                case "60s"       -> "60대";
                default          -> age.toString();
            };
            parts.add(kr);
        }
        Object gender = vp.get("gender");
        if (gender != null && !gender.toString().isBlank()) {
            parts.add("M".equalsIgnoreCase(gender.toString()) ? "남성" : "여성");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** users 테이블에서 이메일 조회 (JdbcTemplate — 오케스트레이터 DB와 동일 스키마) */
    private String lookupEmail(String personaId) {
        try {
            String email = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, personaId);
            return email != null ? email : "unknown@againspring.com";
        } catch (Exception e) {
            return "unknown@againspring.com";
        }
    }

    /** 본문 첫 줄에서 제목 추출 (메타 텍스트 제거) */
    private String extractTitle(String body) {
        if (body == null || body.isBlank()) return "갈등 사연";
        for (String line : body.strip().split("[\\n\\r]+")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            // 선두 메타 라인([원문 수정본] 등) 스킵
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) continue;
            if (trimmed.matches("^\\[.{1,20}].*")) continue;
            String title = trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
            return title;
        }
        return "갈등 사연";
    }

    private int desiredPairedPostsToday(OrchestratorProperties.PairedPost config, int totalSyntheticToday) {
        int targetPosts = generationConfigRepository.findById(1)
            .map(AiUserGenerationConfig::getTargetPosts)
            .orElse(0);

        int basePostCount = Math.max(targetPosts, totalSyntheticToday);
        if (basePostCount <= 0) {
            return 0;
        }

        double share = clampShare(config.getTargetShare());
        return Math.max(1, (int) Math.ceil(basePostCount * share));
    }

    private int countPairedPostsToday() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts p " +
                "JOIN users u ON p.author_id = u.id " +
                "WHERE u.synthetic = 1 " +
                "  AND p.deleted_at IS NULL " +
                "  AND p.created_at >= ? " +
                "  AND p.partner_answered_at IS NOT NULL " +
                "  AND p.partner_body_published IS NOT NULL",
                Integer.class,
                todayStartTimestamp());
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[PairedPost] countPairedPostsToday failed: {}", e.getMessage());
            return 0;
        }
    }

    private EnumMap<PairBucket, Integer> countPairedMixToday() {
        EnumMap<PairBucket, Integer> counts = new EnumMap<>(PairBucket.class);
        counts.put(PairBucket.ROMANTIC, 0);
        counts.put(PairBucket.FRIEND, 0);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.category AS category, COUNT(*) AS cnt " +
                "FROM posts p " +
                "JOIN users u ON p.author_id = u.id " +
                "WHERE u.synthetic = 1 " +
                "  AND p.deleted_at IS NULL " +
                "  AND p.created_at >= ? " +
                "  AND p.partner_answered_at IS NOT NULL " +
                "  AND p.partner_body_published IS NOT NULL " +
                "  AND p.category IN ('COUPLE', 'MARRIED', 'FRIEND') " +
                "GROUP BY p.category",
                todayStartTimestamp());

            for (Map<String, Object> row : rows) {
                PairBucket bucket = bucketForCategory(Objects.toString(row.get("category"), ""));
                int count = ((Number) row.getOrDefault("cnt", 0)).intValue();
                counts.merge(bucket, count, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("[PairedPost] countPairedMixToday failed: {}", e.getMessage());
        }

        return counts;
    }

    private PairBucket chooseNextBucket(OrchestratorProperties.PairedPost config, EnumMap<PairBucket, Integer> counts) {
        int romanticCount = counts.getOrDefault(PairBucket.ROMANTIC, 0);
        int friendCount = counts.getOrDefault(PairBucket.FRIEND, 0);
        int nextTotal = romanticCount + friendCount + 1;

        double romanticShare = clampShare(config.getRomanticShare());
        double friendShare = 1.0 - romanticShare;

        double romanticDeficit = (nextTotal * romanticShare) - romanticCount;
        double friendDeficit = (nextTotal * friendShare) - friendCount;
        return friendDeficit > romanticDeficit ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private Optional<PersonaRelationship> takeCandidateForBucket(
        List<PersonaRelationship> candidates,
        PairBucket bucket
    ) {
        Iterator<PersonaRelationship> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            PersonaRelationship rel = iterator.next();
            if (bucketForRelationType(rel.getRelationType()) == bucket) {
                iterator.remove();
                return Optional.of(rel);
            }
        }
        return Optional.empty();
    }

    private Optional<PersonaRelationship> takeAnyCandidate(List<PersonaRelationship> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.remove(0));
    }

    private PairBucket bucketForRelationType(String relationType) {
        return "FRIEND".equalsIgnoreCase(relationType) ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private PairBucket bucketForCategory(String category) {
        return "FRIEND".equalsIgnoreCase(category) ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private String categoryForRelationType(String relationType) {
        return switch (relationType != null ? relationType.toUpperCase(Locale.ROOT) : "") {
            case "COUPLE" -> "COUPLE";
            case "FRIEND" -> "FRIEND";
            case "MARRIAGE" -> "MARRIED";
            default -> "MARRIED";
        };
    }

    private Timestamp todayStartTimestamp() {
        ZonedDateTime todayStart = LocalDate.now(KST).atStartOfDay(KST);
        return Timestamp.from(todayStart.toInstant());
    }

    private double clampShare(double share) {
        if (Double.isNaN(share)) return 0.0;
        return Math.max(0.0, Math.min(1.0, share));
    }

    private enum PairBucket {
        ROMANTIC,
        FRIEND
    }
}
