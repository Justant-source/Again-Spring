package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaDailyQuota;
import com.againspring.aiuser.orchestrator.repository.PersonaDailyQuotaRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewDispatcher {

    private final PersonaDailyQuotaRepository quotaRepository;
    private final PersonaRepository personaRepository;
    private final BackendBotClient backendBotClient;
    private final Jitter jitter;

    @Autowired(required = false)
    private ActionExecutor actionExecutor;

    private static final Random RNG = new Random();

    public int dispatchViews() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<PersonaDailyQuota> todayQuotas = quotaRepository.findByDayBucket(today);

        int totalDispatched = 0;

        for (PersonaDailyQuota quota : todayQuotas) {
            int remaining = quota.getTargetViews() - quota.getDoneViews();
            if (remaining <= 0) continue;

            Optional<Persona> personaOpt = personaRepository.findById(quota.getPersonaId());
            if (personaOpt.isEmpty()) continue;

            Persona persona = personaOpt.get();

            // Fetch feed
            List<PostDto> feedPosts = backendBotClient.getFeed(0, 30)
                .map(PostFeedPage::getContent)
                .orElse(Collections.emptyList());

            if (feedPosts.isEmpty()) continue;

            // Generate up to 'remaining' VIEW actions
            for (int i = 0; i < remaining; i++) {
                PostDto post = feedPosts.get(RNG.nextInt(feedPosts.size()));
                PlannedAction viewAction = PlannedAction.view(post);

                if (actionExecutor != null) {
                    jitter.scheduleWithinTick(() -> {
                        actionExecutor.execute(persona, viewAction);
                        // Update quota
                        quota.setDoneViews(quota.getDoneViews() + 1);
                        quotaRepository.save(quota);
                    });
                }
                totalDispatched++;
            }
        }

        if (totalDispatched > 0) {
            log.info("ViewDispatcher: dispatched {} VIEW actions for {}", totalDispatched, today);
        }

        return totalDispatched;
    }
}
