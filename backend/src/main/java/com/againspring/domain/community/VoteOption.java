package com.againspring.domain.community;

import jakarta.persistence.*;
import lombok.*;

/**
 * 투표 선택지 (V17 커뮤니티)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vote_options")
public class VoteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, length = 32)
    private String postId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private Integer orderIdx = 0;
}
