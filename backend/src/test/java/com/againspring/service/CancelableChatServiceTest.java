package com.againspring.service;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.ClaudeCodeBridge;
import com.againspring.llm.prompt.StructuredPrompt;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.config.UserPermissionsConfig;
import com.againspring.service.crisis.CrisisDetector;
import com.againspring.service.prompt.ChatPromptAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CancelableChatServiceTest {

    @Mock MessageRepository messageRepo;
    @Mock SessionRepository sessionRepo;
    @Mock UserRepository userRepo;
    @Mock ClaudeCodeBridge llmBridge;
    @Mock CrisisDetector crisisDetector;
    @Mock ChatPromptAssembler promptAssembler;
    @Mock SessionStateMachine stateMachine;
    @Mock ChatTurnProcessor chatTurnProcessor;
    @Mock PlatformTransactionManager txManager;
    @Mock UserPermissionsConfig permissions;

    CancelableChatService service;
    Session session;

    private static final String SESSION_ID = "session-test-1";

    @BeforeEach
    void setUp() {
        TransactionStatus txStatus = new SimpleTransactionStatus();
        lenient().when(txManager.getTransaction(any())).thenReturn(txStatus);

        service = new CancelableChatService(
                messageRepo, sessionRepo, userRepo, llmBridge,
                crisisDetector, promptAssembler, stateMachine,
                chatTurnProcessor, txManager, permissions);

        session = new Session();
        session.setId(SESSION_ID);
        session.setStatus(SessionStatus.CHATTING_SOLO);
        session.setUserAId("user-a-id");

        lenient().when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(session));
        lenient().when(stateMachine.isActive(any())).thenReturn(true);
        lenient().when(stateMachine.isDuo(any())).thenReturn(false);
        lenient().when(crisisDetector.detect(any())).thenReturn(new CrisisDetector.CrisisInfo(0, null));
        lenient().when(messageRepo.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sessionRepo.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepo.findBySessionIdAndSenderIn(eq(SESSION_ID), any()))
                .thenReturn(List.of());
    }

    @Test
    void testSingleMessageNormalFlow() {
        var result = service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "안녕하세요");

        assertThat(result.success()).isTrue();
        assertThat(result.userMsg()).isNotNull();
        assertThat(result.mediatorMessages()).isEmpty();
        assertThat(result.crisisLevel()).isNull();
        verify(messageRepo).save(argThat(m -> m.getSender() == MessageSender.USER_A));
    }

    @Test
    void testNewMessageCancelsActiveInvocation() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());
        CancelableInvocation inv1 = new CancelableInvocation("inv-1", SESSION_ID);
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenReturn(inv1);

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "첫 메시지");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);

        assertThat(inv1.isCanceled()).isFalse();

        // Second message arrives — should cancel inv1
        CancelableInvocation inv2 = new CancelableInvocation("inv-2", SESSION_ID);
        doReturn(inv2).when(llmBridge).invokeCancelable(anyString(), anyString(), anyString());

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "두 번째 메시지");

        assertThat(inv1.isCanceled()).isTrue();
        assertThat(inv1.getResultFuture().isCompletedExceptionally()).isTrue();
        assertThat(inv2.isCanceled()).isFalse();

        service.beginInvocation(SESSION_ID, MessageSender.USER_A);
        assertThat(inv2.isCanceled()).isFalse();
    }

    @Test
    void testTwoQuickMessagesOnlySecondInvocationSurvives() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());

        List<CancelableInvocation> created = new ArrayList<>();
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            CancelableInvocation ci = new CancelableInvocation("inv-" + created.size(), SESSION_ID);
            created.add(ci);
            return ci;
        });

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "msg1");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);
        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "msg2");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);

        assertThat(created).hasSize(2);
        assertThat(created.get(0).isCanceled()).isTrue();
        assertThat(created.get(1).isCanceled()).isFalse();
    }

    @Test
    void testFiveQuickMessagesOnlyLastInvocationSurvives() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());

        List<CancelableInvocation> created = new ArrayList<>();
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            CancelableInvocation ci = new CancelableInvocation("inv-" + created.size(), SESSION_ID);
            created.add(ci);
            return ci;
        });

        for (int i = 0; i < 5; i++) {
            service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "msg-" + i);
            service.beginInvocation(SESSION_ID, MessageSender.USER_A);
        }

        assertThat(created).hasSize(5);
        for (int i = 0; i < 4; i++) {
            assertThat(created.get(i).isCanceled())
                    .as("invocation %d should be canceled", i).isTrue();
        }
        assertThat(created.get(4).isCanceled()).isFalse();
    }

    @Test
    void testCanceledInvocationDoesNotSaveMediatorMessage() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());
        CancelableInvocation inv = new CancelableInvocation("inv-1", SESSION_ID);
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenReturn(inv);

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "msg");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);

        // Cancel before result arrives
        inv.cancel();

        // Only user message save should have happened (in acceptUserMessage)
        verify(messageRepo, times(1)).save(any(Message.class));
    }

    @Test
    void testCrisisLevel1DoesNotCancelActiveInvocation() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());
        CancelableInvocation activeInv = new CancelableInvocation("inv-active", SESSION_ID);
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenReturn(activeInv);

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "정상 메시지");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);

        // Crisis level 1 — should NOT cancel the active invocation
        when(crisisDetector.detect(eq("죽고 싶어"))).thenReturn(new CrisisDetector.CrisisInfo(1, "죽고"));
        var result = service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "죽고 싶어");

        assertThat(result.success()).isFalse();
        assertThat(result.crisisLevel()).isEqualTo(1);
        assertThat(activeInv.isCanceled()).isFalse();
    }

    @Test
    void testSessionCleanupCancelsActiveInvocation() throws Exception {
        doReturn(new StructuredPrompt()).when(promptAssembler).assembleSoloTurnStructured(any(), any(), anyString(), any());
        CancelableInvocation inv = new CancelableInvocation("inv-1", SESSION_ID);
        when(llmBridge.invokeCancelable(anyString(), anyString(), anyString())).thenReturn(inv);

        service.acceptUserMessage(SESSION_ID, MessageSender.USER_A, "msg");
        service.beginInvocation(SESSION_ID, MessageSender.USER_A);

        assertThat(inv.isCanceled()).isFalse();
        service.cleanupSession(SESSION_ID);
        assertThat(inv.isCanceled()).isTrue();
    }
}
