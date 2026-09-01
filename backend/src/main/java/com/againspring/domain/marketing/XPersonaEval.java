package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One held-out reproduction score for a gold {@link XPersonaExample}.
 * Scores are 0–100 resemblance on 말투/길이/결/내용 plus overall.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_persona_eval", indexes = {
    @Index(name = "idx_xpeval_example", columnList = "example_id"),
    @Index(name = "idx_xpeval_created", columnList = "created_at")
})
public class XPersonaEval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "example_id", nullable = false)
    private Long exampleId;

    @Column(name = "tweet_id", length = 64)
    private String tweetId;

    @Column(name = "bot_body", columnDefinition = "TEXT")
    private String botBody;

    @Column(name = "score_overall")
    private Integer scoreOverall;

    @Column(name = "score_tone")
    private Integer scoreTone;

    @Column(name = "score_length")
    private Integer scoreLength;

    @Column(name = "score_texture")
    private Integer scoreTexture;

    @Column(name = "score_content")
    private Integer scoreContent;

    @Column(name = "judge_note", columnDefinition = "TEXT")
    private String judgeNote;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
