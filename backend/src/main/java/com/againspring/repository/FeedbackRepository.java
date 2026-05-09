package com.againspring.repository;

import com.againspring.domain.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Feedback> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    Page<Feedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
