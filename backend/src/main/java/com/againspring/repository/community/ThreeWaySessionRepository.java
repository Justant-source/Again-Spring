package com.againspring.repository.community;

import com.againspring.domain.community.ThreeWaySession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 3자 중재 세션 저장소 (V17 커뮤니티)
 */
@Repository
public interface ThreeWaySessionRepository extends JpaRepository<ThreeWaySession, String> {

    /**
     * 초대 토큰으로 세션 조회
     */
    Optional<ThreeWaySession> findByInviteToken(String inviteToken);

    /**
     * 당사자 중 하나로 참여 중인 세션 조회
     */
    List<ThreeWaySession> findByPartyAUserIdOrPartyBUserId(String partyAUserId, String partyBUserId);
}
