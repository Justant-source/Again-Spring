package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.Juror;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.JuryService;
import com.againspring.service.community.PostComposeService;
import com.againspring.service.community.PostService;
import com.againspring.service.community.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CommunityPostController - 커뮤니티 포스트 API
 */
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Community", description = "커뮤니티 포스트·투표·배심원")
public class CommunityPostController {

    private final PostComposeService composeService;
    private final PostService postService;
    private final VoteService voteService;
    private final JuryService juryService;
    private final VoteOptionRepository voteOptionRepository;
    private final JurorRepository jurorRepository;
    private final CommunityReportRepository communityReportRepository;
    private final CommentService commentService;

    /**
     * 포스트 생성 — AI 중립화 + VoteOption 저장 + 비공개 시 배심원 비동기 생성
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 생성")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        Post post = composeService.composeAndNeutralize(
                userId,
                request.getBodyRaw(),
                request.getCategory(),
                request.getVisibility(),
                request.getSessionId()
        );

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());

        // 비공개 포스트: 배심원 비동기 생성
        if (PostVisibility.PRIVATE.equals(post.getVisibility()) && !options.isEmpty()) {
            juryService.generateJuryAsync(post, options);
        }

        return ResponseEntity.ok(PostResponse.from(post, options));
    }

    /**
     * 공개 포스트 목록 조회
     */
    @GetMapping
    @Operation(summary = "공개 포스트 목록")
    public ResponseEntity<Page<PostResponse>> listPosts(
            @RequestParam(required = false) String category,
            Pageable pageable) {

        Page<Post> posts = postService.listPublicPosts(category, pageable);
        Page<PostResponse> responses = posts.map(post -> {
            List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());
            return PostResponse.from(post, options);
        });
        return ResponseEntity.ok(responses);
    }

    /**
     * 포스트 상세 조회
     */
    @GetMapping("/{id}")
    @Operation(summary = "포스트 상세 조회")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : null;
        Post post = postService.getPost(id, userId);

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);
        Map<Long, Long> voteResult = voteService.getVoteResult(id);
        Optional<Long> myVote = userId != null ? voteService.getMyVote(id, userId) : Optional.empty();

        return ResponseEntity.ok(PostDetailResponse.from(post, options, voteResult, myVote));
    }

    /**
     * 포스트 삭제 (작성자만)
     */
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 삭제")
    public ResponseEntity<Void> deletePost(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.deletePost(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * 공개 투표
     */
    @PostMapping("/{id}/vote")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "투표 수행")
    public ResponseEntity<VoteResultResponse> castVote(
            @PathVariable String id,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Map<Long, Long> result = voteService.castVoteAndGetResult(id, request.getOptionId(), userDetails.getUsername());
        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);
        long totalVotes = result.values().stream().mapToLong(Long::longValue).sum();

        List<VoteOptionResultDto> resultDtos = options.stream()
                .map(opt -> {
                    long count = result.getOrDefault(opt.getId(), 0L);
                    double percentage = totalVotes > 0 ? (count * 100.0) / totalVotes : 0.0;
                    return VoteOptionResultDto.builder()
                            .id(opt.getId())
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(VoteResultResponse.builder()
                .options(resultDtos)
                .totalVotes(totalVotes)
                .myVotedOptionId(request.getOptionId())
                .build());
    }

    /**
     * 배심원 결과 조회 (작성자만)
     */
    @GetMapping("/{id}/jury")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "배심원 투표 결과")
    public ResponseEntity<JuryResultResponse> getJuryResult(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        // 작성자 권한 확인
        Post post = postService.getPost(id, userId);
        if (!userId.equals(post.getAuthorId())) {
            throw new com.againspring.common.exception.BusinessException("ACCESS_DENIED", "작성자만 배심원 결과를 조회할 수 있습니다.", 403);
        }
        List<Juror> jurors = jurorRepository.findByPostId(id);
        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);

        // 옵션 label 조회용 map
        Map<Long, String> optionLabels = options.stream()
                .collect(Collectors.toMap(VoteOption::getId, VoteOption::getLabel));

        // 배심원 DTO
        List<JuryResultResponse.JurorDto> jurorDtos = jurors.stream()
                .map(j -> {
                    String label = j.getChosenOptionId() != null
                            ? optionLabels.getOrDefault(j.getChosenOptionId(), "")
                            : "";
                    String ageGroup = j.getPersona() != null ? j.getPersona().getAgeGroup() : "";
                    String gender = j.getPersona() != null ? j.getPersona().getGender() : "";
                    return JuryResultResponse.JurorDto.builder()
                            .ageGroup(ageGroup)
                            .gender(gender)
                            .chosenOptionLabel(label)
                            .empathyComment(j.getEmpathyComment())
                            .build();
                })
                .toList();

        // 분포 계산
        long total = jurors.size();
        Map<Long, Long> countByOption = jurors.stream()
                .filter(j -> j.getChosenOptionId() != null)
                .collect(Collectors.groupingBy(Juror::getChosenOptionId, Collectors.counting()));

        List<JuryResultResponse.DistributionDto> distribution = options.stream()
                .map(opt -> {
                    long count = countByOption.getOrDefault(opt.getId(), 0L);
                    double pct = total > 0 ? (count * 100.0) / total : 0.0;
                    return JuryResultResponse.DistributionDto.builder()
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(pct)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(JuryResultResponse.builder()
                .jurors(jurorDtos)
                .distribution(distribution)
                .legalNotice("이 결과는 공감 분포일 뿐 법적 책임이나 과실 비율과 무관합니다.")
                .build());
    }

    /**
     * 포스트 신고
     */
    @PostMapping("/{id}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 신고")
    public ResponseEntity<Void> reportPost(
            @PathVariable String id,
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        com.againspring.domain.community.CommunityReport report =
                com.againspring.domain.community.CommunityReport.builder()
                        .targetType("POST")
                        .targetId(id)
                        .reporterUserId(userDetails.getUsername())
                        .reason(request.getReason())
                        .status("PENDING")
                        .build();
        communityReportRepository.save(report);
        return ResponseEntity.accepted().build();
    }

    /**
     * 포스트 좋아요 토글
     */
    @PostMapping("/{id}/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 좋아요 토글")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        boolean liked = commentService.togglePostLike(id, userDetails.getUsername());
        long count = commentService.getPostLikeCount(id);
        return ResponseEntity.ok(LikeResponse.builder().liked(liked).count(count).build());
    }
}
