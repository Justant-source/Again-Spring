package com.againspring.repository;

import com.againspring.domain.User;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 사용자 저장소 (JPA/MariaDB)
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * ID로 사용자 조회 (소프트 삭제 제외)
     */
    Optional<User> findByIdAndDeletedAtIsNull(String id);

    /**
     * 이메일 존재 여부 확인
     */
    boolean existsByEmail(String email);

    /** 게스트 닉네임 중복 차단 (탈퇴 제외) */
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    /**
     * OAuth provider + providerId로 사용자 조회
     */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * 이메일 prefix로 사용자 목록 조회 (dev 테스트 데이터 정리용)
     */
    java.util.List<User> findByEmailStartingWith(String prefix);

    long countByIsGuestFalseAndCreatedAtBetween(java.time.Instant from, java.time.Instant to);

    java.util.List<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndDeletedAtIsNull(
            String nickname, String email);

    /** Admin 전체 회원 목록 (게스트 제외, 탈퇴 제외, 최신 가입순) */
    Page<User> findByIsGuestFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** Admin 전체 사용자 목록 (게스트 포함, 탈퇴 제외, 최신 가입순) */
    Page<User> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** 총 회원 수 (게스트 제외, 탈퇴 제외) */
    long countByIsGuestFalseAndDeletedAtIsNull();

    /** 총 사용자 수 (게스트 포함, 탈퇴 제외) */
    long countByDeletedAtIsNull();

    /**
     * 주어진 ID 목록 중 AI 봇(synthetic=true)인 ID 집합 반환.
     * admin/content에서 "AI 개선" 액션 노출 여부 판단용.
     * ADMIN 전용 — 공개 API에서는 호출 금지.
     */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.synthetic = true")
    Set<String> findSyntheticIds(@Param("ids") Collection<String> ids);

    @Query("SELECT u.id FROM User u WHERE u.synthetic = true")
    Set<String> findAllSyntheticIds();
}
