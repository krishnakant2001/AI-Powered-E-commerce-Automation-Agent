package com.strikerkk.aicommerce.agent_service.repository;

import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentMessage;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("Agent repositories")
class AgentRepositoriesTest {

    @Autowired
    private AgentSessionRepository agentSessionRepository;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private AgentActionRepository agentActionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private AgentSession persistSession(Long userId, SessionStatus status) {
        return agentSessionRepository.saveAndFlush(AgentSession.builder()
                .userId(userId)
                .status(status)
                .initialIntent("buy a speaker")
                .build());
    }

    private AgentMessage persistMessage(AgentSession session, MessageRole role, String content, int sequence) {
        return agentMessageRepository.saveAndFlush(AgentMessage.builder()
                .session(session)
                .role(role)
                .content(content)
                .sequenceNumber(sequence)
                .build());
    }

    private AgentAction persistAction(AgentSession session, ActionType type, ActionStatus status) {
        return agentActionRepository.saveAndFlush(AgentAction.builder()
                .session(session)
                .userId(session.getUserId())
                .actionType(type)
                .status(status)
                .requestPayload("{\"productId\":1}")
                .build());
    }

    @Nested
    @DisplayName("AgentSessionRepository")
    class AgentSessionRepositoryTest {

        @Test
        @DisplayName("persists a session and generates a UUID id")
        void persistsASession() {
            AgentSession saved = persistSession(42L, SessionStatus.ACTIVE);

            assertThat(saved.getSessionId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(42L);
            assertThat(saved.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("stamps created_at and updated_at through the Hibernate callbacks")
        void stampsTheTimestamps() {
            AgentSession saved = persistSession(42L, SessionStatus.ACTIVE);

            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("refuses a session without an owner")
        void refusesASessionWithoutAnOwner() {
            assertThatThrownBy(() -> agentSessionRepository.saveAndFlush(
                    AgentSession.builder().status(SessionStatus.ACTIVE).build()))
                    .isInstanceOfAny(PersistenceException.class, org.springframework.dao.DataAccessException.class);
        }

        @Test
        @DisplayName("finds a session by its id")
        void findsASessionById() {
            UUID id = persistSession(42L, SessionStatus.ACTIVE).getSessionId();

            assertThat(agentSessionRepository.findById(id)).isPresent();
        }

        @Test
        @DisplayName("returns empty for an id that was never issued")
        void returnsEmptyForAnUnknownId() {
            assertThat(agentSessionRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("lists a user's sessions newest first")
        void listsSessionsNewestFirst() {
            AgentSession oldest = persistSession(42L, SessionStatus.CLOSED);
            AgentSession middle = persistSession(42L, SessionStatus.COMPLETED);
            AgentSession newest = persistSession(42L, SessionStatus.ACTIVE);

            // make the ordering deterministic - the three rows can share a millisecond
            oldest.setCreatedAt(java.time.LocalDateTime.now().minusHours(3));
            middle.setCreatedAt(java.time.LocalDateTime.now().minusHours(2));
            newest.setCreatedAt(java.time.LocalDateTime.now().minusHours(1));
            entityManager.persistAndFlush(oldest);
            entityManager.persistAndFlush(middle);
            entityManager.persistAndFlush(newest);
            entityManager.clear();

            List<AgentSession> sessions = agentSessionRepository.findByUserIdOrderByCreatedAtDesc(42L);

            assertThat(sessions).extracting(AgentSession::getSessionId)
                    .containsExactly(newest.getSessionId(), middle.getSessionId(), oldest.getSessionId());
        }

        @Test
        @DisplayName("never leaks another user's sessions")
        void neverLeaksAnotherUsersSessions() {
            persistSession(42L, SessionStatus.ACTIVE);
            persistSession(99L, SessionStatus.ACTIVE);

            assertThat(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(42L))
                    .hasSize(1)
                    .allMatch(session -> session.getUserId().equals(42L));
        }

        @Test
        @DisplayName("returns an empty list for a user who never chatted")
        void returnsAnEmptyListForAnUnknownUser() {
            assertThat(agentSessionRepository.findByUserIdOrderByCreatedAtDesc(12345L)).isEmpty();
        }

        @Test
        @DisplayName("a status change is written back")
        void aStatusChangeIsWrittenBack() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            session.setStatus(SessionStatus.CLOSED);
            agentSessionRepository.saveAndFlush(session);
            entityManager.clear();

            assertThat(agentSessionRepository.findById(session.getSessionId()))
                    .get()
                    .extracting(AgentSession::getStatus)
                    .isEqualTo(SessionStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("AgentMessageRepository")
    class AgentMessageRepositoryTest {

        @Test
        @DisplayName("persists a message against its session")
        void persistsAMessage() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            AgentMessage saved = persistMessage(session, MessageRole.USER, "hello", 0);

            assertThat(saved.getMessageId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getSession().getSessionId()).isEqualTo(session.getSessionId());
        }

        @Test
        @DisplayName("counts only the messages of the session asked for")
        void countsOnlyTheRightSession() {
            AgentSession first = persistSession(42L, SessionStatus.ACTIVE);
            AgentSession second = persistSession(42L, SessionStatus.ACTIVE);

            persistMessage(first, MessageRole.USER, "hi", 0);
            persistMessage(first, MessageRole.ASSISTANT, "hello", 1);
            persistMessage(second, MessageRole.USER, "hey", 0);

            assertThat(agentMessageRepository.countBySession_SessionId(first.getSessionId())).isEqualTo(2);
            assertThat(agentMessageRepository.countBySession_SessionId(second.getSessionId())).isEqualTo(1);
        }

        @Test
        @DisplayName("counts zero for a session with no messages")
        void countsZeroForAnEmptySession() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            assertThat(agentMessageRepository.countBySession_SessionId(session.getSessionId())).isZero();
            assertThat(agentMessageRepository.countBySession_SessionId(UUID.randomUUID())).isZero();
        }

        @Test
        @DisplayName("replays the conversation in sequence order, not insertion order")
        void replaysInSequenceOrder() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            persistMessage(session, MessageRole.ASSISTANT, "third", 2);
            persistMessage(session, MessageRole.USER, "first", 0);
            persistMessage(session, MessageRole.ASSISTANT, "second", 1);
            entityManager.clear();

            assertThat(agentMessageRepository.findBySession_SessionIdOrderBySequenceNumberAsc(session.getSessionId()))
                    .extracting(AgentMessage::getContent)
                    .containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("returns an empty history for an unknown session")
        void returnsAnEmptyHistoryForAnUnknownSession() {
            assertThat(agentMessageRepository.findBySession_SessionIdOrderBySequenceNumberAsc(UUID.randomUUID()))
                    .isEmpty();
        }

        @Test
        @DisplayName("stores a tool call with its JSON input and output")
        void storesAToolCall() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            AgentMessage saved = agentMessageRepository.saveAndFlush(AgentMessage.builder()
                    .session(session)
                    .role(MessageRole.TOOL_RESULT)
                    .toolName("addToCart")
                    .toolInput("{\"productId\":1,\"variantId\":2,\"quantity\":1}")
                    .toolOutput("{\"cartItemId\":9}")
                    .sequenceNumber(1)
                    .build());
            entityManager.clear();

            Optional<AgentMessage> reloaded = agentMessageRepository.findById(saved.getMessageId());

            assertThat(reloaded).get().satisfies(message -> {
                assertThat(message.getRole()).isEqualTo(MessageRole.TOOL_RESULT);
                assertThat(message.getToolName()).isEqualTo("addToCart");
                assertThat(message.getToolInput()).contains("\"variantId\":2");
                assertThat(message.getToolOutput()).isEqualTo("{\"cartItemId\":9}");
                assertThat(message.getContent()).isNull();
            });
        }

        @Test
        @DisplayName("refuses a message that belongs to no session")
        void refusesAnOrphanMessage() {
            assertThatThrownBy(() -> agentMessageRepository.saveAndFlush(AgentMessage.builder()
                    .role(MessageRole.USER)
                    .content("hi")
                    .sequenceNumber(0)
                    .build()))
                    .isInstanceOfAny(PersistenceException.class, org.springframework.dao.DataAccessException.class);
        }

        @Test
        @DisplayName("a long message body is not truncated - the column is TEXT")
        void aLongBodyIsNotTruncated() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);
            String longBody = "x".repeat(5000);

            AgentMessage saved = persistMessage(session, MessageRole.ASSISTANT, longBody, 0);
            entityManager.clear();

            assertThat(agentMessageRepository.findById(saved.getMessageId()))
                    .get()
                    .extracting(AgentMessage::getContent)
                    .isEqualTo(longBody);
        }
    }

    @Nested
    @DisplayName("AgentActionRepository")
    class AgentActionRepositoryTest {

        @Test
        @DisplayName("persists an action and defaults it to PENDING")
        void persistsAnAction() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            AgentAction saved = agentActionRepository.saveAndFlush(AgentAction.builder()
                    .session(session)
                    .userId(42L)
                    .actionType(ActionType.ADD_TO_CART)
                    .requestPayload("{\"productId\":1}")
                    .build());

            assertThat(saved.getActionId()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(ActionStatus.PENDING);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("lists the actions of one session oldest first")
        void listsActionsOldestFirst() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);

            AgentAction first = persistAction(session, ActionType.SEARCH_PRODUCT, ActionStatus.SUCCESS);
            AgentAction second = persistAction(session, ActionType.ADD_TO_CART, ActionStatus.SUCCESS);
            AgentAction third = persistAction(session, ActionType.PLACE_ORDER, ActionStatus.PENDING);

            first.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(3));
            second.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(2));
            third.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(1));
            entityManager.persistAndFlush(first);
            entityManager.persistAndFlush(second);
            entityManager.persistAndFlush(third);
            entityManager.clear();

            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(session.getSessionId()))
                    .extracting(AgentAction::getActionType)
                    .containsExactly(ActionType.SEARCH_PRODUCT, ActionType.ADD_TO_CART, ActionType.PLACE_ORDER);
        }

        @Test
        @DisplayName("never mixes the actions of two sessions")
        void neverMixesTwoSessions() {
            AgentSession first = persistSession(42L, SessionStatus.ACTIVE);
            AgentSession second = persistSession(42L, SessionStatus.ACTIVE);

            persistAction(first, ActionType.ADD_TO_CART, ActionStatus.SUCCESS);
            persistAction(second, ActionType.PLACE_ORDER, ActionStatus.SUCCESS);

            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(first.getSessionId()))
                    .hasSize(1)
                    .extracting(AgentAction::getActionType)
                    .containsExactly(ActionType.ADD_TO_CART);
        }

        @Test
        @DisplayName("returns an empty list for an unknown session")
        void returnsAnEmptyListForAnUnknownSession() {
            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(UUID.randomUUID()))
                    .isEmpty();
        }

        @Test
        @DisplayName("the outcome of an action can be written back")
        void theOutcomeCanBeWrittenBack() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);
            AgentAction action = persistAction(session, ActionType.PLACE_ORDER, ActionStatus.PENDING);

            action.setStatus(ActionStatus.SUCCESS);
            action.setResponsePayload("{\"orderId\":1001}");
            action.setResourceId("1001");
            agentActionRepository.saveAndFlush(action);
            entityManager.clear();

            assertThat(agentActionRepository.findById(action.getActionId())).get().satisfies(reloaded -> {
                assertThat(reloaded.getStatus()).isEqualTo(ActionStatus.SUCCESS);
                assertThat(reloaded.getResourceId()).isEqualTo("1001");
                assertThat(reloaded.getResponsePayload()).isEqualTo("{\"orderId\":1001}");
            });
        }

        @Test
        @DisplayName("a failure reason is persisted")
        void aFailureReasonIsPersisted() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);
            AgentAction action = persistAction(session, ActionType.INITIATE_PAYMENT, ActionStatus.PENDING);

            action.setStatus(ActionStatus.FAILURE);
            action.setFailureReason("Payment service is temporarily unavailable.");
            agentActionRepository.saveAndFlush(action);
            entityManager.clear();

            assertThat(agentActionRepository.findById(action.getActionId())).get().satisfies(reloaded -> {
                assertThat(reloaded.getStatus()).isEqualTo(ActionStatus.FAILURE);
                assertThat(reloaded.getFailureReason()).isEqualTo("Payment service is temporarily unavailable.");
            });
        }

        @Test
        @DisplayName("refuses an action that belongs to no session")
        void refusesAnOrphanAction() {
            assertThatThrownBy(() -> agentActionRepository.saveAndFlush(AgentAction.builder()
                    .userId(42L)
                    .actionType(ActionType.ADD_TO_CART)
                    .build()))
                    .isInstanceOfAny(PersistenceException.class, org.springframework.dao.DataAccessException.class);
        }
    }

    @Nested
    @DisplayName("Cascade")
    class CascadeTest {

        @Test
        @DisplayName("deleting a session takes its messages and actions with it")
        void deletingASessionCascades() {
            AgentSession session = persistSession(42L, SessionStatus.ACTIVE);
            entityManager.clear();

            AgentSession managed = agentSessionRepository.findById(session.getSessionId()).orElseThrow();
            managed.getMessages().add(AgentMessage.builder()
                    .session(managed).role(MessageRole.USER).content("hi").sequenceNumber(0).build());
            managed.getActions().add(AgentAction.builder()
                    .session(managed).userId(42L).actionType(ActionType.SEARCH_PRODUCT).build());
            agentSessionRepository.saveAndFlush(managed);
            entityManager.clear();

            assertThat(agentMessageRepository.countBySession_SessionId(session.getSessionId())).isEqualTo(1);

            agentSessionRepository.delete(
                    agentSessionRepository.findById(session.getSessionId()).orElseThrow());
            agentSessionRepository.flush();
            entityManager.clear();

            assertThat(agentSessionRepository.findById(session.getSessionId())).isEmpty();
            assertThat(agentMessageRepository.countBySession_SessionId(session.getSessionId())).isZero();
            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(session.getSessionId()))
                    .isEmpty();
        }
    }
}

