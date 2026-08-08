package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.VoteOption;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.marketing.dto.CreateJobRequest.EmpathyRatioDto;
import com.againspring.marketing.dto.CreateJobRequest.PolicyDto;
import com.againspring.marketing.dto.CreateJobRequest.TopCommentDto;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.PromoTitleService;
import com.againspring.service.community.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seeds holding {@code draft_json} from a post — same BriefDto shape as
 * {@link com.againspring.marketing.MarketingJobService} job creation (minimal safe duplicate;
 * capture-split / height fields omitted until commit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingHoldingBriefSeeder {

    private final VoteOptionRepository voteOptionRepository;
    private final VoteService voteService;
    private final JurorRepository jurorRepository;
    private final CommentService commentService;
    private final UserRepository userRepository;

    public BriefDto seedFromPost(Post post) {
        String postId = post.getId();

        String summary = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }

        String sideAText = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        if (sideAText != null && sideAText.length() > 300) sideAText = sideAText.substring(0, 300);
        if (sideAText == null) sideAText = "작성자 관점";

        String sideBText = post.getPartnerBodyPublished() != null
            ? post.getPartnerBodyPublished() : post.getPartnerBodyRaw();
        if (sideBText != null && sideBText.length() > 300) sideBText = sideBText.substring(0, 300);
        if (sideBText == null || sideBText.isBlank()) sideBText = "상대방 입장은 아직 등록되지 않았어요";

        String authorBodyFull = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();

        boolean paired = post.getPartnerAnsweredAt() != null
            && post.getPartnerBodyPublished() != null
            && !post.getPartnerBodyPublished().isBlank();
        String partnerBodyFull = paired ? post.getPartnerBodyPublished() : null;

        int empathyA = 50, empathyB = 50;
        Map<String, Integer> voteLabels = new LinkedHashMap<>();
        try {
            List<VoteOption> voteOptions = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);
            Map<Long, Long> voteResults = voteService.getVoteResult(postId);
            long totalVotes = voteResults.values().stream().mapToLong(Long::longValue).sum();
            if (!voteOptions.isEmpty() && totalVotes > 0) {
                long optionACount = voteResults.getOrDefault(voteOptions.get(0).getId(), 0L);
                empathyA = (int) Math.round((double) optionACount / totalVotes * 100);
                empathyB = 100 - empathyA;
            }
            for (VoteOption opt : voteOptions) {
                long count = voteResults.getOrDefault(opt.getId(), 0L);
                String label = opt.getLabel() != null ? opt.getLabel() : "선택지";
                voteLabels.put(label, (int) count);
            }
        } catch (Exception e) {
            log.warn("Holding brief: vote load failed for {}: {}", postId, e.getMessage());
        }

        String juryGist = "";
        List<String> juryOpinions = new ArrayList<>();
        try {
            List<com.againspring.domain.community.Juror> jurors = jurorRepository.findByPostId(postId);
            List<String> comments = jurors.stream()
                .map(j -> j.getEmpathyComment())
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toList());
            juryOpinions = comments.stream()
                .limit(3)
                .map(c -> c.length() > 100 ? c.substring(0, 100) : c)
                .collect(Collectors.toList());
            if (!comments.isEmpty()) {
                String combined = String.join(" / ", comments);
                juryGist = combined.length() > 200 ? combined.substring(0, 200) : combined;
            }
        } catch (Exception e) {
            log.warn("Holding brief: juror load failed for {}: {}", postId, e.getMessage());
        }

        List<TopCommentDto> topComments = new ArrayList<>();
        try {
            List<PostComment> comments = commentService.getTopLevelComments(postId);
            topComments = comments.stream()
                .filter(c -> c.getBody() != null && !c.getBody().isBlank())
                .sorted((a, b) -> {
                    int la = a.getLikeCount() != null ? a.getLikeCount() : 0;
                    int lb = b.getLikeCount() != null ? b.getLikeCount() : 0;
                    return Integer.compare(lb, la);
                })
                .limit(3)
                .map(c -> TopCommentDto.builder()
                    .author(resolveNickname(c.getAuthorId()))
                    .authorId(c.getAuthorId())
                    .body(c.getBody())
                    .likeCount(c.getLikeCount() != null ? c.getLikeCount() : 0)
                    .createdAt(c.getCreatedAt())
                    .side(resolveSide(c.getAuthorId(), post.getAuthorId(), post.getPartnerUserId()))
                    .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Holding brief: comments load failed for {}: {}", postId, e.getMessage());
        }

        List<String> tags = new ArrayList<>();
        if (post.getCategory() != null) {
            tags.add(post.getCategory().getDisplayName());
        }

        String storyTitle = post.getTitle();
        if (storyTitle == null || storyTitle.isBlank()) {
            storyTitle = post.getUserTitle();
        }

        return BriefDto.builder()
            .title(storyTitle)
            .promoTitle(PromoTitleService.resolveOrFallback(post))
            .metaphorId(post.getMetaphorId())
            .neutralSummary(summary)
            .sideA(sideAText)
            .sideB(sideBText)
            .authorBody(authorBodyFull)
            .partnerBody(partnerBodyFull)
            .empathyRatio(EmpathyRatioDto.builder().a(empathyA).b(empathyB).build())
            .juryGist(juryGist)
            .juryOpinions(juryOpinions)
            .topComments(topComments)
            .voteLabels(voteLabels)
            .postUrl("https://againspring.net/community/" + postId)
            .tags(tags)
            .hasPartnerStory(paired)
            .policy(PolicyDto.builder()
                .noEmoji(true)
                .forbiddenTerms(Arrays.asList("판결", "처방", "승패", "승자", "패자", "가해자", "피해자"))
                .build())
            .build();
    }

    private String resolveNickname(String userId) {
        if (userId == null || userId.startsWith("anon_")) return "익명";
        return userRepository.findById(userId)
            .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
            .orElse("익명");
    }

    private String resolveSide(String commentAuthorId, String postAuthorId, String postPartnerUserId) {
        if (commentAuthorId == null) return "neutral";
        if (postAuthorId != null && postAuthorId.equals(commentAuthorId)) return "author";
        if (postPartnerUserId != null && postPartnerUserId.equals(commentAuthorId)) return "partner";
        return "neutral";
    }
}
