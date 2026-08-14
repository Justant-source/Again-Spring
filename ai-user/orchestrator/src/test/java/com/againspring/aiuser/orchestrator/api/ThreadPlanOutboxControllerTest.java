package com.againspring.aiuser.orchestrator.api;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.threadplan.HumanInteractionInboxService;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ThreadPlanOutboxControllerTest {
    @Test
    void onlyExplicitHumanEventEntersInteractionInbox() {
        ThreadPlanService plans = mock(ThreadPlanService.class);
        HumanInteractionInboxService inbox = mock(HumanInteractionInboxService.class);
        BackendBotClient backend = mock(BackendBotClient.class);
        OrchestratorProperties properties = new OrchestratorProperties();
        ThreadPlanGenerationService generation = mock(ThreadPlanGenerationService.class);
        ThreadPlanOutboxController controller = new ThreadPlanOutboxController(plans, inbox, backend, properties, generation);

        ThreadPlanOutboxController.Event missingFlag = commentEvent(null);
        controller.accept(missingFlag);
        ThreadPlanOutboxController.Event aiAuthor = commentEvent(true);
        controller.accept(aiAuthor);
        verifyNoInteractions(inbox);

        controller.accept(commentEvent(false));
        verify(inbox).observe(eq("post-1"), eq("11"), isNull(), eq("human-user"), eq("COMMENT"), any(), any());
    }

    private static ThreadPlanOutboxController.Event commentEvent(Boolean syntheticAuthor) {
        ThreadPlanOutboxController.Event event = new ThreadPlanOutboxController.Event();
        event.setType("COMMENT_CREATED");
        event.setPostId("post-1");
        event.setCommentId("11");
        event.setAuthorId("human-user");
        event.setSyntheticAuthor(syntheticAuthor);
        return event;
    }
}
