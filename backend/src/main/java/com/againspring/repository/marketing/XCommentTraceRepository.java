package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XCommentTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface XCommentTraceRepository extends JpaRepository<XCommentTrace, Long> {
}
