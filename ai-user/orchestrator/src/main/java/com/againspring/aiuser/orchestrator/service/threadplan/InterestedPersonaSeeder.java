package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiPostInterestedPersona;
import com.againspring.aiuser.orchestrator.repository.AiPostInterestedPersonaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Best-effort seed of {@code ai_post_interested_personas} from a plan cast (W6-A).
 * Failures are logged and never fail plan READY/ACTIVE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestedPersonaSeeder {
    private final AiPostInterestedPersonaRepository repository;

    @Transactional
    public void seedFromPlanCast(String postId, Collection<String> personaIds) {
        if (postId == null || postId.isBlank() || personaIds == null || personaIds.isEmpty()) return;
        Set<String> unique = new LinkedHashSet<>();
        for (String id : personaIds) {
            if (id != null && !id.isBlank()) unique.add(id.trim());
        }
        int inserted = 0;
        for (String personaId : unique) {
            if (repository.existsByPostIdAndPersonaId(postId, personaId)) continue;
            try {
                repository.saveAndFlush(AiPostInterestedPersona.builder()
                        .postId(postId)
                        .personaId(personaId)
                        .source(AiPostInterestedPersona.SOURCE_PLAN_CAST)
                        .build());
                inserted++;
            } catch (DataIntegrityViolationException duplicate) {
                log.debug("interested persona already present post={} persona={}", postId, personaId);
            }
        }
        if (inserted > 0) {
            log.info("Seeded {} PLAN_CAST interested personas for post={}", inserted, postId);
        }
    }
}
