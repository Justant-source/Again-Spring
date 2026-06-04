package com.againspring.aiuser.orchestrator.client;

import com.againspring.aiuser.orchestrator.client.dto.CreateCommentDto;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.InviteDto;
import com.againspring.aiuser.orchestrator.client.dto.LoginDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.client.dto.VoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class BackendBotClient {

    private final RestClient restClient;

    public BackendBotClient(@Qualifier("backendRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Login and get JWT token */
    public Optional<String> login(String email, String password) {
        try {
            LoginDto.Response resp = restClient.post()
                .uri("/api/auth/login")
                .body(LoginDto.Request.builder().email(email).password(password).build())
                .retrieve()
                .body(LoginDto.Response.class);
            if (resp != null && resp.getToken() != null) {
                return Optional.ofNullable(resp.getToken().getAccessToken());
            }
        } catch (Exception e) {
            log.error("Bot login failed for {}: {}", email, e.getMessage());
        }
        return Optional.empty();
    }

    /** Fetch post feed (permitAll — no auth needed) */
    public Optional<PostFeedPage> getFeed(int page, int size) {
        try {
            PostFeedPage result = restClient.get()
                .uri("/api/community/posts?page={page}&size={size}&sortBy=latest", page, size)
                .retrieve()
                .body(PostFeedPage.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Feed fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Create a post (auth required) */
    public Optional<PostDto> createPost(String jwt, CreatePostDto req) {
        try {
            PostDto result = restClient.post()
                .uri("/api/community/posts")
                .header("Authorization", "Bearer " + jwt)
                .body(req)
                .retrieve()
                .body(PostDto.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Create post failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Vote on a post. Returns false on 409 ALREADY_VOTED (normal dedup). */
    public boolean vote(String jwt, String postId, Long optionId) {
        try {
            restClient.post()
                .uri("/api/community/posts/{postId}/vote", postId)
                .header("Authorization", "Bearer " + jwt)
                .body(VoteDto.builder().optionId(optionId).build())
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.debug("Already voted on post {}", postId);
            } else {
                log.warn("Vote failed on post {}: {}", postId, e.getMessage());
            }
            return false;
        } catch (Exception e) {
            log.warn("Vote error on post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Like a post */
    public boolean likePost(String jwt, String postId) {
        try {
            restClient.post()
                .uri("/api/community/posts/{postId}/like", postId)
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Like failed on post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Add comment or reply (parentCommentId=null for top-level) */
    public boolean addComment(String jwt, String postId, String body, Long parentCommentId) {
        try {
            restClient.post()
                .uri("/api/community/posts/{postId}/comments", postId)
                .header("Authorization", "Bearer " + jwt)
                .body(CreateCommentDto.builder().body(body).parentCommentId(parentCommentId).build())
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Comment failed on post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Create invite token for co-authored post */
    public Optional<String> createInviteToken(String jwt, String postId) {
        try {
            InviteDto.Response resp = restClient.post()
                .uri("/api/community/posts/{postId}/invite", postId)
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .body(InviteDto.Response.class);
            return resp != null ? Optional.ofNullable(resp.getInviteToken()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Invite token creation failed for post {}: {}", postId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Submit partner answer via invite token (anonymous — no JWT needed per SecurityConfig) */
    public boolean submitPartnerAnswer(String token, String userTitle, String bodyRaw) {
        try {
            restClient.post()
                .uri("/api/s/{token}/answer", token)
                .body(InviteDto.AnswerRequest.builder().userTitle(userTitle).bodyRaw(bodyRaw).build())
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Partner answer submission failed for token {}: {}", token, e.getMessage());
            return false;
        }
    }
}
