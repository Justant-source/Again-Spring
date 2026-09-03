package com.againspring.aiuser.orchestrator.client;

import com.againspring.aiuser.orchestrator.client.dto.CommentThreadDto;
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

    /**
     * Plan execution retry entrypoint. The plan item's key is passed unchanged
     * to the backend so an ambiguous network timeout can be replayed safely.
     */
    public Optional<PostDto> createPost(String jwt, CreatePostDto req, String idempotencyKey) {
        try {
            var request = restClient.post()
                    .uri("/api/community/posts")
                    .header("Authorization", "Bearer " + jwt);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            return Optional.ofNullable(request.body(req).retrieve().body(PostDto.class));
        } catch (Exception e) {
            log.error("Create post failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Create a post (auth required). */
    public Optional<PostDto> createPost(String jwt, CreatePostDto req) {
        return createPost(jwt, req, (String) null);
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
            String body = restClient.post().uri("/api/community/posts/{postId}/like", postId)
                .header("Authorization", "Bearer " + jwt).retrieve().body(String.class);
            // toggle 감지: liked=false면 의도치 않게 좋아요 취소됨 → 재호출로 복구
            if (body != null && body.contains("\"liked\":false")) {
                restClient.post().uri("/api/community/posts/{postId}/like", postId)
                    .header("Authorization", "Bearer " + jwt).retrieve().toBodilessEntity();
                log.debug("Like re-applied on post {} (toggle recovery)", postId);
            }
            return true;
        } catch (Exception e) {
            log.warn("Like failed on post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Like a comment or reply (toggle — 이미 좋아요면 취소됨 → 복구 재호출). 대댓글도 동일 엔드포인트. */
    public boolean likeComment(String jwt, String postId, Long commentId) {
        try {
            String body = restClient.post()
                .uri("/api/community/posts/{postId}/comments/{commentId}/like", postId, commentId)
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .body(String.class);
            // toggle 감지: liked=false면 의도치 않게 좋아요 취소됨 → 재호출로 복구
            if (body != null && body.contains("\"liked\":false")) {
                restClient.post()
                    .uri("/api/community/posts/{postId}/comments/{commentId}/like", postId, commentId)
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .toBodilessEntity();
                log.debug("Like re-applied on comment {} post {} (toggle recovery)", commentId, postId);
            }
            return true;
        } catch (Exception e) {
            log.warn("Like comment failed on post {} comment {}: {}", postId, commentId, e.getMessage());
            return false;
        }
    }

    /**
     * Unlike a comment (toggle once). Opposite of {@link #likeComment}: if the toggle
     * accidentally <em>adds</em> a like (persona had not liked), immediately toggles back
     * and returns false. Used by engagement surplus convergence.
     */
    public boolean unlikeComment(String jwt, String postId, Long commentId) {
        try {
            String body = restClient.post()
                .uri("/api/community/posts/{postId}/comments/{commentId}/like", postId, commentId)
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .body(String.class);
            if (body != null && body.contains("\"liked\":true")) {
                restClient.post()
                    .uri("/api/community/posts/{postId}/comments/{commentId}/like", postId, commentId)
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .toBodilessEntity();
                log.debug("Unlike aborted on comment {} post {} (was not liked)", commentId, postId);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Unlike comment failed on post {} comment {}: {}", postId, commentId, e.getMessage());
            return false;
        }
    }

    /** Record a view (POST /view with deviceId — auth 불필요, deviceId로 중복 방지) */
    public boolean viewPost(String postId, String deviceId) {
        try {
            restClient.post().uri("/api/community/posts/{postId}/view", postId)
                .body(java.util.Map.of("deviceId", deviceId))
                .retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("View post failed on {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Add comment or reply (parentCommentId=null for top-level) */
    public boolean addComment(String jwt, String postId, String commentBody, Long parentCommentId) {
        var dto = CreateCommentDto.builder().body(commentBody).parentCommentId(parentCommentId).build();
        try {
            restClient.post().uri("/api/community/posts/{postId}/comments", postId)
                .header("Authorization", "Bearer " + jwt).body(dto)
                .retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Comment failed on post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Plan publisher needs the returned id so a reserved reply can target its planned parent. */
    public Optional<String> addCommentReturningId(String jwt, String postId, String commentBody, Long parentCommentId) {
        return addCommentReturningId(jwt, postId, commentBody, parentCommentId, null);
    }

    /** Same-key replay support for reserved thread-plan comment items. */
    public Optional<String> addCommentReturningId(String jwt, String postId, String commentBody,
                                                   Long parentCommentId, String idempotencyKey) {
        var dto = CreateCommentDto.builder().body(commentBody).parentCommentId(parentCommentId).build();
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = addCommentRequest(jwt, postId, dto, idempotencyKey)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});
            Object id = response == null ? null : response.get("id");
            return id == null ? Optional.empty() : Optional.of(String.valueOf(id));
        } catch (Exception e) {
            log.warn("Planned comment failed on post {}: {}", postId, e.getMessage());
            return Optional.empty();
        }
    }

    private RestClient.RequestHeadersSpec<?> addCommentRequest(String jwt, String postId,
                                                                CreateCommentDto dto, String idempotencyKey) {
        var request = restClient.post().uri("/api/community/posts/{postId}/comments", postId)
                .header("Authorization", "Bearer " + jwt);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return request.body(dto);
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
        return submitPartnerAnswer(token, userTitle, bodyRaw, null);
    }

    public boolean submitPartnerAnswer(String token, String userTitle, String bodyRaw,
                                       java.util.List<Integer> captureSplitAfterLines) {
        try {
            restClient.post()
                .uri("/api/s/{token}/answer", token)
                .body(InviteDto.AnswerRequest.builder()
                        .userTitle(userTitle)
                        .bodyRaw(bodyRaw)
                        .captureSplitAfterLines(captureSplitAfterLines)
                        .build())
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Partner answer submission failed for token {}: {}", token, e.getMessage());
            return false;
        }
    }

    /** Set publish mode to WAIT_FOR_PARTNER (or PUBLISH_NOW) */
    public boolean setPublishMode(String jwt, String postId, String mode, Integer voteDurationHours) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("mode", mode);
            if (voteDurationHours != null) body.put("voteDurationHours", voteDurationHours);
            restClient.patch()
                .uri("/api/community/posts/{postId}/publish-mode", postId)
                .header("Authorization", "Bearer " + jwt)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("setPublishMode failed for post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /** Fetch post detail (no auth) */
    public java.util.Optional<java.util.Map<String, Object>> getPost(String postId) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = restClient.get()
                .uri("/api/community/posts/{postId}", postId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>(){});
            return java.util.Optional.ofNullable(resp);
        } catch (Exception e) {
            log.warn("getPost failed for {}: {}", postId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Fetch comments for a post (no auth required) */
    public java.util.List<CommentThreadDto> getComments(String postId, int page, int size) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> raw = restClient.get()
                .uri("/api/community/posts/{postId}/comments?page={page}&size={size}", postId, page, size)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>(){});
            if (raw == null) return java.util.Collections.emptyList();
            java.util.List<CommentThreadDto> result = new java.util.ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            for (java.util.Map<String, Object> item : raw) {
                try {
                    CommentThreadDto dto = mapper.convertValue(item, CommentThreadDto.class);
                    result.add(dto);
                } catch (Exception ignored) {}
            }
            return result;
        } catch (Exception e) {
            log.warn("getComments failed for post {}: {}", postId, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
