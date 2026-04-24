package com.againspring.domain.relationship;

import com.againspring.domain.enums.RelationType;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자 관계 엔티티 (MariaDB JPA)
 * Neo4j 그래프 대체: 두 사용자 간 관계 기록
 *
 * NOTE: 서비스 레이어에서 항상 userAId < userBId 규칙을 유지해야 함
 * (게스트의 경우 userBId가 null이고 userBGuestName 사용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "user_relationships",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"user_a_id", "user_b_id", "relationship_type"},
            name = "uk_user_relationships_a_b_type"
        )
    },
    indexes = {
        @Index(columnList = "user_a_id", name = "idx_user_a_id"),
        @Index(columnList = "user_b_id", name = "idx_user_b_id")
    }
)
public class UserRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false, name = "user_a_id")
    private String userAId;

    @Column(length = 32, name = "user_b_id")
    private String userBId;

    @Column(length = 100, name = "user_b_guest_name")
    private String userBGuestName;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false, name = "relationship_type")
    private RelationType relationshipType;

    @Column(name = "first_session_at")
    private Instant firstSessionAt;

    @Column(name = "last_session_at")
    private Instant lastSessionAt;

    @Column(name = "session_count")
    @Builder.Default
    private Integer sessionCount = 0;

    @Column(name = "average_temperature", columnDefinition = "DECIMAL(3,1)")
    private Double averageTemperature;
}
