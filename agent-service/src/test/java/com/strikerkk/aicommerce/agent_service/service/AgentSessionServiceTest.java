package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.ConversationHistoryResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.MySessionResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.SessionActionResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.SessionStatusResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.StartSessionResponse;
import com.strikerkk.aicommerce.agent_service.dto.summary.SessionSummary;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentMessage;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.exception.SessionNotFoundException;
import com.strikerkk.aicommerce.agent_service.exception.UnauthorizedSessionAccessException;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentActionRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentMessageRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentSessionService")
class AgentSessionServiceTest {

    private static final UUID SESSION_ID = TestDataFactory.SESSION_ID;
    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final Long OTHER_USER_ID = TestDataFactory.OTHER_USER_ID;

    @Mock
    private AgentSessionRepository agentSessionRepository;

    @Mock
    private AgentActionRepository agentActionRepository;

    @Mock
    private AgentMessageRepository agentMessageRepository;

    @Mock
    private SessionContextService sessionContextService;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private AgentSessionService agentSessionService;

    @Captor
    private ArgumentCaptor<AgentSession> sessionCaptor;

    @Captor
    private ArgumentCaptor<SessionContext> contextCaptor;

    @Captor
    private ArgumentCaptor<AgentMessage> messageCaptor;

    @Captor
    private ArgumentCaptor<AgentAction> actionCaptor;

    @BeforeEach
    void signIn() {
        UserContext.setUserId(String.valueOf(USER_ID));
        UserContext.setUserEmail(TestDataFactory.USER_EMAIL);
        UserContext.setUserRole(TestDataFactory.USER_ROLE);
    }

    @AfterEach
    void signOut() {
        UserContext.clear();
        UserContext.setUserEmail(null);
        UserContext.setUserRole(null);
    }

    private AgentSession ownedSession() {
        return TestDataFactory.session(SESSION_ID, USER_ID);
    }

    private void sessionExists(AgentSession session) {
        when(agentSessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
    }

    // session start ---------------------------------------------------------------------------

    @Nested
    @DisplayName("startSession")
    class StartSession {

        @Test
        @DisplayName("creates a row in Postgres and a context in Redis")
        void createsEverything() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.empty());
            when(agentSessionRepository.save(any(AgentSession.class))).thenAnswer(invocation -> {
                AgentSession toSave = invocation.getArgument(0);
                toSave.setSessionId(SESSION_ID);
                toSave.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
                return toSave;
            });

            StartSessionResponse response =
                    agentSessionService.startSession(TestDataFactory.startSessionRequest("buy a speaker"));

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(sessionCaptor.getValue().getInitialIntent()).isEqualTo("buy a speaker");

            verify(sessionContextService).save(contextCaptor.capture());
            SessionContext context = contextCaptor.getValue();
            assertThat(context.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(context.getUserId()).isEqualTo(USER_ID);
            assertThat(context.getUserEmail()).isEqualTo(TestDataFactory.USER_EMAIL);
            assertThat(context.getCurrentIntent()).isEqualTo("buy a speaker");
            assertThat(context.getConversationMessages()).isEmpty();

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(response.getMessage()).isEqualTo("Session started. How can I help you today?");
            assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        }

        @Test
        @DisplayName("a null body is allowed - there is simply no initial intent")
        void aNullBodyIsAllowed() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.empty());
            when(agentSessionRepository.save(any(AgentSession.class))).thenAnswer(invocation -> {
                AgentSession toSave = invocation.getArgument(0);
                toSave.setSessionId(SESSION_ID);
                return toSave;
            });

            agentSessionService.startSession(null);

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getInitialIntent()).isNull();
        }

        @Test
        @DisplayName("an empty request body is allowed too")
        void anEmptyBodyIsAllowed() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.empty());
            when(agentSessionRepository.save(any(AgentSession.class))).thenAnswer(invocation -> {
                AgentSession toSave = invocation.getArgument(0);
                toSave.setSessionId(SESSION_ID);
                return toSave;
            });

            agentSessionService.startSession(new StartSessionRequest());

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getInitialIntent()).isNull();
        }

        @Test
        @DisplayName("reuses the live session instead of opening a second one")
        void reusesTheLiveSession() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.context()));

            StartSessionResponse response =
                    agentSessionService.startSession(TestDataFactory.startSessionRequest("hi"));

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(response.getMessage())
                    .isEqualTo("You already have an active session. Continuing from where you left off.");
            assertThat(response.getCreatedAt()).isNotNull();

            verify(agentSessionRepository, never()).save(any());
            verify(sessionContextService, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getSessionStatus")
    class GetSessionStatus {

        @Test
        @DisplayName("merges the Postgres row with the live Redis context")
        void mergesPostgresAndRedis() {
            AgentSession session = TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.CLARIFYING);
            sessionExists(session);

            when(agentMessageRepository.countBySession_SessionId(SESSION_ID)).thenReturn(6);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of(TestDataFactory.action(session, ActionType.ADD_TO_CART, ActionStatus.SUCCESS)));

            SessionContext context = TestDataFactory.contextWith(
                    TestDataFactory.userTurn("I want a speaker"),
                    TestDataFactory.assistantTurn("Sure - which colour?"),
                    TestDataFactory.userTurn("black"));
            context.setPendingClarificationFor("COLOR");
            context.setLastActivityAt(LocalDateTime.of(2026, 1, 1, 11, 0));
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.of(context));

            SessionStatusResponse response = agentSessionService.getSessionStatus(SESSION_ID);

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getStatus()).isEqualTo(SessionStatus.CLARIFYING);
            assertThat(response.getInitialIntent()).isEqualTo(TestDataFactory.INITIAL_INTENT);
            assertThat(response.getTotalMessages()).isEqualTo(6);
            assertThat(response.getTotalActions()).isEqualTo(1);
            assertThat(response.getPendingClarificationFor()).isEqualTo("COLOR");
            assertThat(response.getLastAgentMessage()).isEqualTo("Sure - which colour?");
            assertThat(response.getLastActivityAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 11, 0));
            assertThat(response.getCreatedAt()).isEqualTo(session.getCreatedAt());
        }

        @Test
        @DisplayName("picks the newest assistant turn, not the first one")
        void picksTheNewestAssistantTurn() {
            AgentSession session = ownedSession();
            sessionExists(session);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of());
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.of(
                    TestDataFactory.contextWith(
                            TestDataFactory.assistantTurn("first"),
                            TestDataFactory.userTurn("ok"),
                            TestDataFactory.assistantTurn("latest"))));

            assertThat(agentSessionService.getSessionStatus(SESSION_ID).getLastAgentMessage())
                    .isEqualTo("latest");
        }

        @Test
        @DisplayName("falls back to the Postgres timestamp when Redis has expired")
        void fallsBackWhenRedisExpired() {
            AgentSession session = ownedSession();
            sessionExists(session);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of());
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            SessionStatusResponse response = agentSessionService.getSessionStatus(SESSION_ID);

            assertThat(response.getPendingClarificationFor()).isNull();
            assertThat(response.getLastAgentMessage()).isNull();
            assertThat(response.getLastActivityAt()).isEqualTo(session.getUpdatedAt());
        }

        @Test
        @DisplayName("a conversation with no assistant turn yet leaves the last message empty")
        void noAssistantTurnYet() {
            AgentSession session = ownedSession();
            sessionExists(session);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of());
            when(sessionContextService.findBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(TestDataFactory.contextWith(TestDataFactory.userTurn("hi"))));

            assertThat(agentSessionService.getSessionStatus(SESSION_ID).getLastAgentMessage()).isNull();
        }

        @Test
        @DisplayName("an unknown session is a 404")
        void anUnknownSessionIsA404() {
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentSessionService.getSessionStatus(SESSION_ID))
                    .isInstanceOf(SessionNotFoundException.class)
                    .hasMessageContaining(SESSION_ID.toString());
        }

        @Test
        @DisplayName("somebody else's session is a 403 and nothing is read")
        void somebodyElsesSessionIsA403() {
            sessionExists(TestDataFactory.session(SESSION_ID, OTHER_USER_ID));

            assertThatThrownBy(() -> agentSessionService.getSessionStatus(SESSION_ID))
                    .isInstanceOf(UnauthorizedSessionAccessException.class);

            verifyNoInteractions(agentMessageRepository, agentActionRepository, sessionContextService);
        }
    }

    @Nested
    @DisplayName("endSession")
    class EndSession {

        @Test
        @DisplayName("closes an active session and drops the Redis keys")
        void closesAnActiveSession() {
            AgentSession session = TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.ACTIVE);
            sessionExists(session);

            agentSessionService.endSession(SESSION_ID);

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.CLOSED);
            verify(sessionContextService).delete(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("closes a session that was waiting for clarification")
        void closesAClarifyingSession() {
            sessionExists(TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.CLARIFYING));

            agentSessionService.endSession(SESSION_ID);

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.CLOSED);
        }

        @Test
        @DisplayName("is idempotent - an already closed session is not written again")
        void isIdempotent() {
            sessionExists(TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.CLOSED));

            agentSessionService.endSession(SESSION_ID);

            verify(agentSessionRepository, never()).save(any());
            verify(sessionContextService).delete(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("a completed session is also left alone but still evicted from Redis")
        void aCompletedSessionIsLeftAlone() {
            sessionExists(TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.COMPLETED));

            agentSessionService.endSession(SESSION_ID);

            verify(agentSessionRepository, never()).save(any());
            verify(sessionContextService).delete(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("nobody can end somebody else's session")
        void nobodyCanEndSomebodyElsesSession() {
            sessionExists(TestDataFactory.session(SESSION_ID, OTHER_USER_ID));

            assertThatThrownBy(() -> agentSessionService.endSession(SESSION_ID))
                    .isInstanceOf(UnauthorizedSessionAccessException.class);

            verify(agentSessionRepository, never()).save(any());
            verifyNoInteractions(sessionContextService);
        }

        @Test
        @DisplayName("an unknown session is a 404")
        void anUnknownSessionIsA404() {
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentSessionService.endSession(SESSION_ID))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getConversationHistory")
    class GetConversationHistory {

        @Test
        @DisplayName("returns every stored turn in sequence order")
        void returnsEveryTurn() {
            AgentSession session = ownedSession();
            sessionExists(session);

            when(agentMessageRepository.findBySession_SessionIdOrderBySequenceNumberAsc(SESSION_ID))
                    .thenReturn(List.of(
                            TestDataFactory.message(session, MessageRole.USER, "I want a speaker", 0),
                            TestDataFactory.toolMessage(session, MessageRole.TOOL_RESULT, "searchProducts",
                                    null, "[{\"id\":1}]", 1),
                            TestDataFactory.message(session, MessageRole.ASSISTANT, "I found three", 2)));

            ConversationHistoryResponse response = agentSessionService.getConversationHistory(SESSION_ID);

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getTotalMessages()).isEqualTo(3);
            assertThat(response.getMessages()).extracting("role")
                    .containsExactly(MessageRole.USER, MessageRole.TOOL_RESULT, MessageRole.ASSISTANT);
            assertThat(response.getMessages().get(1).getToolName()).isEqualTo("searchProducts");
            assertThat(response.getMessages().get(1).getToolOutput()).isEqualTo("[{\"id\":1}]");
            assertThat(response.getMessages().get(2).getSequenceNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("a session that never said anything blows up loudly")
        void anEmptySessionBlowsUp() {
            sessionExists(ownedSession());
            when(agentMessageRepository.findBySession_SessionIdOrderBySequenceNumberAsc(SESSION_ID))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> agentSessionService.getConversationHistory(SESSION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No conversation history found");
        }

        @Test
        @DisplayName("ownership is checked before anything is read")
        void ownershipIsCheckedFirst() {
            sessionExists(TestDataFactory.session(SESSION_ID, OTHER_USER_ID));

            assertThatThrownBy(() -> agentSessionService.getConversationHistory(SESSION_ID))
                    .isInstanceOf(UnauthorizedSessionAccessException.class);

            verifyNoInteractions(agentMessageRepository);
        }
    }

    @Nested
    @DisplayName("getMySessions")
    class GetMySessions {

        private AgentSession sessionWith(SessionStatus status) {
            return TestDataFactory.session(UUID.randomUUID(), USER_ID, status);
        }

        private void noMessagesOrActions() {
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(0);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        }

        @Test
        @DisplayName("returns an empty list for a brand-new user")
        void returnsAnEmptyListForANewUser() {
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

            MySessionResponse response = agentSessionService.getMySessions();

            assertThat(response.getTotalSessions()).isZero();
            assertThat(response.getSessions()).isEmpty();
        }

        @Test
        @DisplayName("counts the messages and the actions of every session")
        void countsMessagesAndActions() {
            AgentSession session = sessionWith(SessionStatus.ACTIVE);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(session.getSessionId())).thenReturn(7);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(session.getSessionId()))
                    .thenReturn(List.of(
                            TestDataFactory.action(session, ActionType.SEARCH_PRODUCT, ActionStatus.SUCCESS),
                            TestDataFactory.action(session, ActionType.ADD_TO_CART, ActionStatus.SUCCESS)));

            SessionSummary summary = agentSessionService.getMySessions().getSessions().get(0);

            assertThat(summary.getSessionId()).isEqualTo(session.getSessionId());
            assertThat(summary.getTotalMessages()).isEqualTo(7);
            assertThat(summary.getTotalActions()).isEqualTo(2);
        }

        @Test
        @DisplayName("a placed order becomes the headline outcome, with its id")
        void aPlacedOrderIsTheHeadline() {
            AgentSession session = sessionWith(SessionStatus.COMPLETED);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(4);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(
                            TestDataFactory.action(session, ActionType.INITIATE_PAYMENT, ActionStatus.SUCCESS),
                            TestDataFactory.action(session, ActionType.PLACE_ORDER, ActionStatus.SUCCESS, "1001")));

            assertThat(agentSessionService.getMySessions().getSessions().get(0).getOutcome())
                    .isEqualTo("Order #1001 placed successfully");
        }

        @Test
        @DisplayName("buyNow counts as a placed order too")
        void buyNowCountsAsAPlacedOrder() {
            AgentSession session = sessionWith(SessionStatus.COMPLETED);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(4);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(TestDataFactory.action(
                            session, ActionType.BUY_NOW, ActionStatus.SUCCESS, "2002")));

            assertThat(agentSessionService.getMySessions().getSessions().get(0).getOutcome())
                    .isEqualTo("Order #2002 placed successfully");
        }

        @Test
        @DisplayName("an order without a resource id still reads sensibly")
        void anOrderWithoutAResourceId() {
            AgentSession session = sessionWith(SessionStatus.COMPLETED);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(4);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(TestDataFactory.action(
                            session, ActionType.PLACE_ORDER, ActionStatus.SUCCESS, null)));

            assertThat(agentSessionService.getMySessions().getSessions().get(0).getOutcome())
                    .isEqualTo("Order placed successfully");
        }

        @Test
        @DisplayName("a failed order does not count as an outcome")
        void aFailedOrderDoesNotCount() {
            AgentSession session = sessionWith(SessionStatus.FAILED);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(4);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(TestDataFactory.action(
                            session, ActionType.PLACE_ORDER, ActionStatus.FAILURE, "1001")));

            assertThat(agentSessionService.getMySessions().getSessions().get(0).getOutcome())
                    .isEqualTo("Session failed - please try again");
        }

        @Test
        @DisplayName("a payment with no order is reported as awaiting completion")
        void aPaymentWithNoOrder() {
            AgentSession session = sessionWith(SessionStatus.ACTIVE);
            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(session));
            when(agentMessageRepository.countBySession_SessionId(any())).thenReturn(4);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(TestDataFactory.action(
                            session, ActionType.INITIATE_PAYMENT, ActionStatus.SUCCESS)));

            assertThat(agentSessionService.getMySessions().getSessions().get(0).getOutcome())
                    .isEqualTo("Payment initiated - awaiting completion");
        }

        @Test
        @DisplayName("without any action the session status decides the wording")
        void theStatusDecidesTheWording() {
            AgentSession active = sessionWith(SessionStatus.ACTIVE);
            AgentSession completed = sessionWith(SessionStatus.COMPLETED);
            AgentSession failed = sessionWith(SessionStatus.FAILED);
            AgentSession closed = sessionWith(SessionStatus.CLOSED);
            AgentSession clarifying = sessionWith(SessionStatus.CLARIFYING);

            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .thenReturn(List.of(active, completed, failed, closed, clarifying));
            noMessagesOrActions();

            assertThat(agentSessionService.getMySessions().getSessions())
                    .extracting(SessionSummary::getOutcome)
                    .containsExactly("In progress",
                            "Completed Successfully",
                            "Session failed - please try again",
                            "Session closed without completing",
                            "Waiting for your response");
        }

        @Test
        @DisplayName("keeps the newest-first order the repository returns")
        void keepsTheRepositoryOrder() {
            AgentSession newest = sessionWith(SessionStatus.ACTIVE);
            AgentSession oldest = sessionWith(SessionStatus.CLOSED);

            when(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .thenReturn(List.of(newest, oldest));
            noMessagesOrActions();

            assertThat(agentSessionService.getMySessions().getSessions())
                    .extracting(SessionSummary::getSessionId)
                    .containsExactly(newest.getSessionId(), oldest.getSessionId());
        }
    }

    @Nested
    @DisplayName("getSessionActions")
    class GetSessionActions {

        @Test
        @DisplayName("returns the audit trail of the session")
        void returnsTheAuditTrail() {
            AgentSession session = ownedSession();
            sessionExists(session);
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of(
                            TestDataFactory.action(session, ActionType.SEARCH_PRODUCT, ActionStatus.SUCCESS),
                            TestDataFactory.action(session, ActionType.PLACE_ORDER, ActionStatus.SUCCESS, "1001")));

            SessionActionResponse response = agentSessionService.getSessionActions(SESSION_ID);

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getTotalActions()).isEqualTo(2);
            assertThat(response.getActions()).extracting("actionType")
                    .containsExactly(ActionType.SEARCH_PRODUCT, ActionType.PLACE_ORDER);
            assertThat(response.getActions().get(1).getResourceId()).isEqualTo("1001");
        }

        @Test
        @DisplayName("a session that ran no tools returns an empty list, not an error")
        void anEmptyAuditTrail() {
            sessionExists(ownedSession());
            when(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .thenReturn(List.of());

            SessionActionResponse response = agentSessionService.getSessionActions(SESSION_ID);

            assertThat(response.getTotalActions()).isZero();
            assertThat(response.getActions()).isEmpty();
        }

        @Test
        @DisplayName("ownership is checked before anything is read")
        void ownershipIsCheckedFirst() {
            sessionExists(TestDataFactory.session(SESSION_ID, OTHER_USER_ID));

            assertThatThrownBy(() -> agentSessionService.getSessionActions(SESSION_ID))
                    .isInstanceOf(UnauthorizedSessionAccessException.class);

            verifyNoInteractions(agentActionRepository);
        }
    }

    @Nested
    @DisplayName("markAsClarifying")
    class MarkAsClarifying {

        @Test
        @DisplayName("flips the status in Postgres and records the topic in Redis")
        void flipsBothStores() {
            AgentSession session = ownedSession();
            sessionExists(session);
            SessionContext context = TestDataFactory.context();
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.of(context));

            agentSessionService.markAsClarifying(SESSION_ID, "COLOR");

            verify(agentSessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.CLARIFYING);

            assertThat(context.getPendingClarificationFor()).isEqualTo("COLOR");
            verify(sessionContextService).save(context);
        }

        @Test
        @DisplayName("still flips Postgres when Redis has already expired")
        void stillFlipsPostgresWhenRedisExpired() {
            sessionExists(ownedSession());
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            agentSessionService.markAsClarifying(SESSION_ID, "SIZE");

            verify(agentSessionRepository).save(any(AgentSession.class));
            verify(sessionContextService, never()).save(any());
        }

        @Test
        @DisplayName("an unknown session is silently ignored")
        void anUnknownSessionIsIgnored() {
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            agentSessionService.markAsClarifying(SESSION_ID, "COLOR");

            verify(agentSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("saveMessage")
    class SaveMessage {

        @Test
        @DisplayName("numbers the turn from the count already stored")
        void numbersTheTurn() {
            AgentSession session = ownedSession();
            when(agentMessageRepository.countBySession_SessionId(SESSION_ID)).thenReturn(4);
            when(agentMessageRepository.save(any(AgentMessage.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            agentSessionService.saveMessage(session, MessageRole.USER, "hello", null, null, null);

            verify(agentMessageRepository).save(messageCaptor.capture());
            AgentMessage saved = messageCaptor.getValue();

            assertThat(saved.getSequenceNumber()).isEqualTo(4);
            assertThat(saved.getSession()).isSameAs(session);
            assertThat(saved.getRole()).isEqualTo(MessageRole.USER);
            assertThat(saved.getContent()).isEqualTo("hello");
            assertThat(saved.getToolName()).isNull();
        }

        @Test
        @DisplayName("the first turn of a session is number zero")
        void theFirstTurnIsZero() {
            when(agentMessageRepository.countBySession_SessionId(SESSION_ID)).thenReturn(0);
            when(agentMessageRepository.save(any(AgentMessage.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            agentSessionService.saveMessage(ownedSession(), MessageRole.USER, "hi", null, null, null);

            verify(agentMessageRepository).save(messageCaptor.capture());
            assertThat(messageCaptor.getValue().getSequenceNumber()).isZero();
        }

        @Test
        @DisplayName("stores a tool call with its payloads")
        void storesAToolCall() {
            when(agentMessageRepository.countBySession_SessionId(SESSION_ID)).thenReturn(2);
            when(agentMessageRepository.save(any(AgentMessage.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            agentSessionService.saveMessage(ownedSession(), MessageRole.TOOL_RESULT, null,
                    "addToCart", null, "{\"cartItemId\":9}");

            verify(agentMessageRepository).save(messageCaptor.capture());
            AgentMessage saved = messageCaptor.getValue();

            assertThat(saved.getRole()).isEqualTo(MessageRole.TOOL_RESULT);
            assertThat(saved.getToolName()).isEqualTo("addToCart");
            assertThat(saved.getToolOutput()).isEqualTo("{\"cartItemId\":9}");
            assertThat(saved.getContent()).isNull();
        }

        @Test
        @DisplayName("hands back whatever the repository persisted")
        void handsBackThePersistedRow() {
            AgentMessage persisted = TestDataFactory.message(ownedSession(), MessageRole.USER, "hi", 0);
            when(agentMessageRepository.countBySession_SessionId(SESSION_ID)).thenReturn(0);
            when(agentMessageRepository.save(any(AgentMessage.class))).thenReturn(persisted);

            assertThat(agentSessionService.saveMessage(ownedSession(), MessageRole.USER, "hi", null, null, null))
                    .isSameAs(persisted);
        }
    }

    @Nested
    @DisplayName("saveAction")
    class SaveAction {

        @Test
        @DisplayName("logs the intent as PENDING before the tool is even called")
        void logsThePendingIntent() {
            AgentSession session = ownedSession();
            when(agentActionRepository.save(any(AgentAction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            agentSessionService.saveAction(session, USER_ID, ActionType.ADD_TO_CART, "{\"productId\":7}");

            verify(agentActionRepository).save(actionCaptor.capture());
            AgentAction saved = actionCaptor.getValue();

            assertThat(saved.getSession()).isSameAs(session);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getActionType()).isEqualTo(ActionType.ADD_TO_CART);
            assertThat(saved.getRequestPayload()).isEqualTo("{\"productId\":7}");
            assertThat(saved.getStatus()).isEqualTo(ActionStatus.PENDING);
            assertThat(saved.getResponsePayload()).isNull();
        }

        @Test
        @DisplayName("hands back the persisted row so the caller knows the id")
        void handsBackThePersistedRow() {
            AgentAction persisted = TestDataFactory.pendingAction();
            when(agentActionRepository.save(any(AgentAction.class))).thenReturn(persisted);

            assertThat(agentSessionService.saveAction(
                    ownedSession(), USER_ID, ActionType.ADD_TO_CART, "{}")).isSameAs(persisted);
        }
    }

    @Nested
    @DisplayName("updateActionResult")
    class UpdateActionResult {

        @Test
        @DisplayName("writes the success outcome back onto the action")
        void writesTheSuccessOutcome() {
            AgentAction action = TestDataFactory.pendingAction();
            when(agentActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));

            agentSessionService.updateActionResult(action.getActionId(), ActionStatus.SUCCESS,
                    "{\"orderId\":1001}", "1001", null);

            verify(agentActionRepository).save(action);
            assertThat(action.getStatus()).isEqualTo(ActionStatus.SUCCESS);
            assertThat(action.getResponsePayload()).isEqualTo("{\"orderId\":1001}");
            assertThat(action.getResourceId()).isEqualTo("1001");
            assertThat(action.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("writes the failure outcome back onto the action")
        void writesTheFailureOutcome() {
            AgentAction action = TestDataFactory.pendingAction();
            when(agentActionRepository.findById(action.getActionId())).thenReturn(Optional.of(action));

            agentSessionService.updateActionResult(action.getActionId(), ActionStatus.FAILURE,
                    "{\"error\": true}", null, "cart-service is down");

            assertThat(action.getStatus()).isEqualTo(ActionStatus.FAILURE);
            assertThat(action.getFailureReason()).isEqualTo("cart-service is down");
            assertThat(action.getResourceId()).isNull();
        }

        @Test
        @DisplayName("an unknown action id is a no-op, not a crash")
        void anUnknownActionIdIsANoOp() {
            UUID unknown = UUID.randomUUID();
            when(agentActionRepository.findById(unknown)).thenReturn(Optional.empty());

            agentSessionService.updateActionResult(unknown, ActionStatus.SUCCESS, "{}", null, null);

            verify(agentActionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateSessionStatus")
    class UpdateSessionStatus {

        @Test
        @DisplayName("writes the new status back")
        void writesTheNewStatus() {
            AgentSession session = ownedSession();
            sessionExists(session);

            agentSessionService.updateSessionStatus(SESSION_ID, SessionStatus.COMPLETED);

            verify(agentSessionRepository).save(session);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("an unknown session id is a no-op, not a crash")
        void anUnknownSessionIdIsANoOp() {
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            agentSessionService.updateSessionStatus(SESSION_ID, SessionStatus.ACTIVE);

            verify(agentSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not check ownership - it is an internal call")
        void doesNotCheckOwnership() {
            AgentSession session = TestDataFactory.session(SESSION_ID, OTHER_USER_ID);
            sessionExists(session);

            agentSessionService.updateSessionStatus(SESSION_ID, SessionStatus.FAILED);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.FAILED);
        }
    }
}

