package com.againspring.repository;

import com.againspring.domain.Message;
import com.againspring.domain.enums.MessageSender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

/**
 * MessageRepository (V1.5 카톡식 채팅)
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId " +
           "AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<Message> findNewMessagesSince(@Param("sessionId") String sessionId,
                                        @Param("since") Instant since);

    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId " +
           "AND m.sender IN :senders ORDER BY m.createdAt ASC")
    List<Message> findBySessionIdAndSenderIn(@Param("sessionId") String sessionId,
                                              @Param("senders") List<MessageSender> senders);

    long countBySessionIdAndSender(String sessionId, MessageSender sender);

    boolean existsBySessionIdAndSenderAndIsFinalizeSuggestionTrue(String sessionId, MessageSender sender);

    List<Message> findBySessionIdAndSenderAndIsFinalizeSuggestionTrueAndDismissedAtIsNull(
            String sessionId, MessageSender sender);
}
