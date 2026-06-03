package com.againspring.repository.community;

import com.againspring.domain.community.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostViewRepository extends JpaRepository<PostView, Long> {

    boolean existsByPostIdAndDeviceId(String postId, String deviceId);
}
