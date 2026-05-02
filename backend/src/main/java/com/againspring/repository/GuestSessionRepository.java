package com.againspring.repository;

import com.againspring.domain.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestSessionRepository extends JpaRepository<GuestSession, Long> {

    Optional<GuestSession> findByInviteToken(String inviteToken);

    void deleteByGuestId(String guestId);
}
