package com.againspring.service.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.community.VoteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 커뮤니티 게시글을 마케팅 생성 소스로 변환하는 서비스.
 * Post 도메인에서 양쪽 입장·공감 비율·지표를 읽어 마케팅 요약 문자열을 생성한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CommunityPostMarketingReader {

    private final PostRepository postRepo;
    private final VoteOptionRepository voteOptionRepo;
    private final VoteRepository voteRepo;
    private final PostCommentRepository commentRepo;
    private final PostLikeRepository likeRepo;

    @Value("${app.marketing.public-base-url:https://againspring.net}")
    private String publicBaseUrl;

    /**
     * 마케팅 소스 데이터를 담는 레코드.
     */
    public record PostMarketingData(
            String postId,
            String title,
            String authorSide,
            String partnerSide,
            String category,
            String relationType,
            int authorPct,
            int partnerPct,
            long voteCount,
            long commentCount,
            int viewCount,
            String postUrl
    ) {}

    /**
     * 후보 사연 목록 항목 DTO.
     */
    public record CandidatePostData(
            String id,
            String title,
            String snippet,
            String category,
            String categoryDisplayName,
            int authorPct,
            int partnerPct,
            long voteCount,
            long commentCount,
            int viewCount,
            java.time.Instant createdAt,
            boolean synthetic
    ) {}

    /**
     * postId로 마케팅 소스 데이터를 로드한다.
     * 공개글(PUBLIC)이고 삭제되지 않은 게시글만 허용.
     */
    @Transactional(readOnly = true)
    public PostMarketingData load(String postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + postId));

        if (post.getDeletedAt() != null) {
            throw new IllegalStateException("삭제된 게시글입니다: " + postId);
        }

        return buildData(post);
    }

    /**
     * Post 엔티티를 마케팅 소스 데이터로 변환.
     */
    @Transactional(readOnly = true)
    public PostMarketingData buildData(Post post) {
        String postId = post.getId();

        // 투표 선택지 (orderIdx 0=작성자, 1=상대방)
        List<VoteOption> options = voteOptionRepo.findByPostIdOrderByOrderIdx(postId);
        long totalVotes = voteRepo.countByPostId(postId);

        int authorPct = 0;
        int partnerPct = 0;

        if (totalVotes > 0 && options.size() >= 2) {
            long authorCount  = voteRepo.countByPostIdAndOptionId(postId, options.get(0).getId());
            long partnerCount = voteRepo.countByPostIdAndOptionId(postId, options.get(1).getId());
            authorPct  = (int) Math.round(authorCount  * 100.0 / totalVotes);
            partnerPct = (int) Math.round(partnerCount * 100.0 / totalVotes);
        }

        long commentCount = commentRepo.countByPostIdAndStatusAndDeletedAtIsNull(
                postId, CommentStatus.ACTIVE);

        String relationType = post.getCategory() != null
                ? post.getCategory().name().toLowerCase()
                : "other";

        String postUrl = publicBaseUrl + "/community/" + postId;

        return new PostMarketingData(
                postId,
                post.getTitle() != null ? post.getTitle() : "(제목 없음)",
                post.getBodyPublished() != null ? post.getBodyPublished() : "",
                post.getPartnerBodyPublished() != null ? post.getPartnerBodyPublished() : "(상대방 입장 미등록)",
                post.getCategory() != null ? post.getCategory().getDisplayName() : "기타",
                relationType,
                authorPct,
                partnerPct,
                totalVotes,
                commentCount,
                post.getViewCount() != null ? post.getViewCount() : 0,
                postUrl
        );
    }

    /**
     * 마케팅 LLM 프롬프트에 주입할 소스 문자열 빌드.
     */
    public String buildSourceContent(PostMarketingData data) {
        String authorSide = truncate(data.authorSide(), 600);
        String partnerSide = truncate(data.partnerSide(), 400);

        StringBuilder sb = new StringBuilder();
        sb.append("[제목] ").append(data.title()).append("\n");
        sb.append("[관계] ").append(data.category()).append("\n");
        sb.append("[작성자 입장]\n").append(authorSide).append("\n\n");

        if (!data.partnerSide().equals("(상대방 입장 미등록)")) {
            sb.append("[상대방 입장]\n").append(partnerSide).append("\n\n");
        }

        if (data.voteCount() > 0) {
            sb.append("[현재 공감 비율] 작성자 ").append(data.authorPct())
              .append("% : 상대방 ").append(data.partnerPct())
              .append("% (총 ").append(data.voteCount()).append("표)\n");
        } else {
            sb.append("[현재 공감 비율] 아직 투표가 없습니다.\n");
        }

        sb.append("[사연 링크] ").append(data.postUrl()).append("\n");

        return sb.toString();
    }

    /**
     * 후보 사연 데이터 변환 (후보 목록 API용).
     */
    @Transactional(readOnly = true)
    public CandidatePostData toCandidateData(Post post, boolean synthetic) {
        String postId = post.getId();

        List<VoteOption> options = voteOptionRepo.findByPostIdOrderByOrderIdx(postId);
        long totalVotes = voteRepo.countByPostId(postId);

        int authorPct = 0;
        int partnerPct = 0;
        if (totalVotes > 0 && options.size() >= 2) {
            long a = voteRepo.countByPostIdAndOptionId(postId, options.get(0).getId());
            long p = voteRepo.countByPostIdAndOptionId(postId, options.get(1).getId());
            authorPct  = (int) Math.round(a * 100.0 / totalVotes);
            partnerPct = (int) Math.round(p * 100.0 / totalVotes);
        }

        long commentCount = commentRepo.countByPostIdAndStatusAndDeletedAtIsNull(
                postId, CommentStatus.ACTIVE);

        String snippet = post.getBodyPublished() != null
                ? truncate(post.getBodyPublished(), 80)
                : "";

        return new CandidatePostData(
                postId,
                post.getTitle() != null ? post.getTitle() : "(제목 없음)",
                snippet,
                post.getCategory() != null ? post.getCategory().name() : "OTHER",
                post.getCategory() != null ? post.getCategory().getDisplayName() : "기타",
                authorPct,
                partnerPct,
                totalVotes,
                commentCount,
                post.getViewCount() != null ? post.getViewCount() : 0,
                post.getCreatedAt(),
                synthetic
        );
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
