package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.VoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MarketingHoldingBriefSeederTest {

    @Mock VoteOptionRepository voteOptionRepository;
    @Mock VoteService voteService;
    @Mock CommentService commentService;
    @Mock UserRepository userRepository;

    @InjectMocks
    MarketingHoldingBriefSeeder seeder;

    private void stubEmptyCollaborators() {
        lenient().when(voteOptionRepository.findByPostIdOrderByOrderIdx(anyString()))
            .thenReturn(Collections.emptyList());
        lenient().when(voteService.getVoteResult(anyString())).thenReturn(Collections.emptyMap());
        lenient().when(commentService.getTopLevelComments(anyString())).thenReturn(Collections.emptyList());
    }

    @Test
    void seedFromPost_withCategory_seedsFourTagsInOrder() {
        stubEmptyCollaborators();
        Post post = Post.builder()
            .id("post1")
            .authorId("author1")
            .title("제목")
            .bodyRaw("본문")
            .category(PostCategory.COUPLE)
            .build();

        BriefDto brief = seeder.seedFromPost(post);

        assertThat(brief.getTags()).containsExactly(
            "#다시봄", "#againspring", "#공감비율", "#연인");
        assertThat(brief.getHookEmotion()).isNull();
    }

    @Test
    void seedFromPost_withoutCategory_seedsBrandPairAndEmpathyTag() {
        stubEmptyCollaborators();
        Post post = Post.builder()
            .id("post2")
            .authorId("author1")
            .title("제목")
            .bodyRaw("본문")
            .category(null)
            .build();

        BriefDto brief = seeder.seedFromPost(post);

        assertThat(brief.getTags()).containsExactly("#다시봄", "#againspring", "#공감비율");
    }

    @Test
    void seedFromPost_withEachCategory_appendsHashPrefixedDisplayName() {
        stubEmptyCollaborators();
        for (PostCategory category : PostCategory.values()) {
            Post post = Post.builder()
                .id("post-" + category.name())
                .authorId("author1")
                .title("제목")
                .bodyRaw("본문")
                .category(category)
                .build();

            BriefDto brief = seeder.seedFromPost(post);

            assertThat(brief.getTags()).containsExactly(
                "#다시봄", "#againspring", "#공감비율", "#" + category.getDisplayName());
            assertThat(brief.getTags()).hasSizeLessThanOrEqualTo(5);
        }
    }

    @Test
    void seedFromPost_unrelatedFields_unaffectedByTagChange() {
        stubEmptyCollaborators();
        Post post = Post.builder()
            .id("post3")
            .authorId("author1")
            .title("제목")
            .bodyRaw("본문")
            .category(PostCategory.WORK)
            .build();

        List<String> otherFieldsSnapshot = List.of(
            String.valueOf(seeder.seedFromPost(post).getTitle()),
            String.valueOf(seeder.seedFromPost(post).getPostUrl()));

        assertThat(otherFieldsSnapshot).containsExactly("제목", "https://againspring.net/community/post3");
    }

    @Test
    void seedFromPost_withHookEmotion_passesThrough() {
        stubEmptyCollaborators();
        Post post = Post.builder()
            .id("post-hook")
            .authorId("author1")
            .title("제목")
            .bodyRaw("본문")
            .category(PostCategory.COUPLE)
            .hookEmotion("tension")
            .build();

        BriefDto brief = seeder.seedFromPost(post);

        assertThat(brief.getHookEmotion()).isEqualTo("tension");
        assertThat(brief.getPromoTitle()).isNotBlank();
    }

    @Test
    void seedFromPost_blankHookEmotion_becomesNull() {
        stubEmptyCollaborators();
        Post post = Post.builder()
            .id("post-blank")
            .authorId("author1")
            .title("제목")
            .bodyRaw("본문")
            .hookEmotion("   ")
            .build();

        BriefDto brief = seeder.seedFromPost(post);

        assertThat(brief.getHookEmotion()).isNull();
    }
}
