package com.strikerkk.aicommerce.agent_service.entity;

import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JPA entities")
class AgentEntitiesTest {

    private Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(name + " is missing from " + type.getSimpleName(), ex);
        }
    }

    @Nested
    @DisplayName("AgentSession")
    class AgentSessionTest {

        @Test
        @DisplayName("maps onto the agent_session table with a generated UUID id")
        void isMappedCorrectly() {
            assertThat(AgentSession.class.getAnnotation(Entity.class)).isNotNull();
            assertThat(AgentSession.class.getAnnotation(Table.class).name()).isEqualTo("agent_session");

            Field id = field(AgentSession.class, "sessionId");
            assertThat(id.getAnnotation(Id.class)).isNotNull();
            assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.UUID);
            assertThat(id.getAnnotation(Column.class).name()).isEqualTo("session_id");
            assertThat(id.getAnnotation(Column.class).updatable()).isFalse();
        }

        @Test
        @DisplayName("the status is stored as a readable string")
        void statusIsStoredAsAString() {
            Field status = field(AgentSession.class, "status");

            assertThat(status.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
            assertThat(status.getAnnotation(Column.class).nullable()).isFalse();
        }

        @Test
        @DisplayName("the timestamps are filled in by Hibernate")
        void timestampsAreManagedByHibernate() {
            assertThat(field(AgentSession.class, "createdAt").getAnnotation(CreationTimestamp.class)).isNotNull();
            assertThat(field(AgentSession.class, "updatedAt").getAnnotation(UpdateTimestamp.class)).isNotNull();
        }

        @Test
        @DisplayName("messages and actions are lazy, cascading children")
        void childrenAreLazyAndCascading() {
            for (String name : List.of("messages", "actions")) {
                OneToMany mapping = field(AgentSession.class, name).getAnnotation(OneToMany.class);

                assertThat(mapping).as(name).isNotNull();
                assertThat(mapping.mappedBy()).isEqualTo("session");
                assertThat(mapping.fetch()).isEqualTo(FetchType.LAZY);
                assertThat(mapping.cascade()).contains(jakarta.persistence.CascadeType.ALL);
            }
        }

        @Test
        @DisplayName("a fresh session starts ACTIVE with empty children")
        void aFreshSessionStartsActive() {
            AgentSession session = AgentSession.builder().userId(42L).build();

            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(session.getMessages()).isEmpty();
            assertThat(session.getActions()).isEmpty();
            assertThat(session.getSessionId()).isNull();
        }

        @Test
        @DisplayName("an explicit status overrides the default")
        void anExplicitStatusWins() {
            AgentSession session = AgentSession.builder().userId(42L).status(SessionStatus.CLOSED).build();

            assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
        }

        @Test
        @DisplayName("has a no-arg constructor for Hibernate and working setters")
        void hasANoArgConstructorAndSetters() {
            AgentSession session = new AgentSession();
            UUID id = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            session.setSessionId(id);
            session.setUserId(42L);
            session.setStatus(SessionStatus.CLARIFYING);
            session.setInitialIntent("buy a speaker");
            session.setCreatedAt(now);
            session.setUpdatedAt(now);

            assertThat(session.getSessionId()).isEqualTo(id);
            assertThat(session.getUserId()).isEqualTo(42L);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CLARIFYING);
            assertThat(session.getInitialIntent()).isEqualTo("buy a speaker");
            assertThat(session.getCreatedAt()).isEqualTo(now);
            assertThat(session.getUpdatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("AgentMessage")
    class AgentMessageTest {

        @Test
        @DisplayName("maps onto the agent_message table with a generated UUID id")
        void isMappedCorrectly() {
            assertThat(AgentMessage.class.getAnnotation(Entity.class)).isNotNull();
            assertThat(AgentMessage.class.getAnnotation(Table.class).name()).isEqualTo("agent_message");

            Field id = field(AgentMessage.class, "messageId");
            assertThat(id.getAnnotation(Id.class)).isNotNull();
            assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.UUID);
        }

        @Test
        @DisplayName("belongs to exactly one session, fetched lazily")
        void belongsToOneSession() {
            Field session = field(AgentMessage.class, "session");

            assertThat(session.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
            assertThat(session.getAnnotation(JoinColumn.class).name()).isEqualTo("session_id");
            assertThat(session.getAnnotation(JoinColumn.class).nullable()).isFalse();
        }

        @Test
        @DisplayName("the role is stored as a readable string and is mandatory")
        void theRoleIsMandatory() {
            Field role = field(AgentMessage.class, "role");

            assertThat(role.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
            assertThat(role.getAnnotation(Column.class).nullable()).isFalse();
        }

        @Test
        @DisplayName("free text columns are TEXT, not VARCHAR(255)")
        void freeTextColumnsAreText() {
            assertThat(field(AgentMessage.class, "content").getAnnotation(Column.class).columnDefinition())
                    .isEqualTo("TEXT");
            assertThat(field(AgentMessage.class, "toolInput").getAnnotation(Column.class).columnDefinition())
                    .isEqualTo("TEXT");
            assertThat(field(AgentMessage.class, "toolOutput").getAnnotation(Column.class).columnDefinition())
                    .isEqualTo("TEXT");
        }

        @Test
        @DisplayName("the ordering column is mandatory")
        void theOrderingColumnIsMandatory() {
            Column column = field(AgentMessage.class, "sequenceNumber").getAnnotation(Column.class);

            assertThat(column.name()).isEqualTo("sequence_number");
            assertThat(column.nullable()).isFalse();
        }

        @Test
        @DisplayName("builds a tool-call message with every field populated")
        void buildsAToolCallMessage() {
            AgentSession session = AgentSession.builder().userId(42L).build();

            AgentMessage message = AgentMessage.builder()
                    .session(session)
                    .role(MessageRole.TOOL_RESULT)
                    .toolName("addToCart")
                    .toolInput("{\"productId\":1}")
                    .toolOutput("{\"cartItemId\":9}")
                    .sequenceNumber(3)
                    .build();

            assertThat(message.getSession()).isSameAs(session);
            assertThat(message.getRole()).isEqualTo(MessageRole.TOOL_RESULT);
            assertThat(message.getToolName()).isEqualTo("addToCart");
            assertThat(message.getToolInput()).isEqualTo("{\"productId\":1}");
            assertThat(message.getToolOutput()).isEqualTo("{\"cartItemId\":9}");
            assertThat(message.getSequenceNumber()).isEqualTo(3);
            assertThat(message.getContent()).isNull();
        }

        @Test
        @DisplayName("has a no-arg constructor for Hibernate")
        void hasANoArgConstructor() {
            AgentMessage message = new AgentMessage();
            message.setContent("hello");

            assertThat(message.getContent()).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("AgentAction")
    class AgentActionTest {

        @Test
        @DisplayName("maps onto the agent_actions table with a generated UUID id")
        void isMappedCorrectly() {
            assertThat(AgentAction.class.getAnnotation(Entity.class)).isNotNull();
            assertThat(AgentAction.class.getAnnotation(Table.class).name()).isEqualTo("agent_actions");

            Field id = field(AgentAction.class, "actionId");
            assertThat(id.getAnnotation(Id.class)).isNotNull();
            assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.UUID);
        }

        @Test
        @DisplayName("records who did what, and both enums are strings")
        void recordsWhoDidWhat() {
            assertThat(field(AgentAction.class, "userId").getAnnotation(Column.class).nullable()).isFalse();
            assertThat(field(AgentAction.class, "actionType").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
            assertThat(field(AgentAction.class, "status").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
        }

        @Test
        @DisplayName("the payloads are TEXT so a whole JSON body fits")
        void thePayloadsAreText() {
            assertThat(field(AgentAction.class, "requestPayload").getAnnotation(Column.class).columnDefinition())
                    .isEqualTo("TEXT");
            assertThat(field(AgentAction.class, "responsePayload").getAnnotation(Column.class).columnDefinition())
                    .isEqualTo("TEXT");
        }

        @Test
        @DisplayName("a fresh action starts PENDING")
        void aFreshActionStartsPending() {
            AgentAction action = AgentAction.builder()
                    .userId(42L)
                    .actionType(ActionType.PLACE_ORDER)
                    .build();

            assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING);
            assertThat(action.getResourceId()).isNull();
            assertThat(action.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("an explicit status overrides the default")
        void anExplicitStatusWins() {
            AgentAction action = AgentAction.builder()
                    .userId(42L)
                    .actionType(ActionType.PLACE_ORDER)
                    .status(ActionStatus.SUCCESS)
                    .build();

            assertThat(action.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        }

        @Test
        @DisplayName("the outcome can be written back after the tool answered")
        void theOutcomeCanBeWrittenBack() {
            AgentAction action = new AgentAction();

            action.setStatus(ActionStatus.FAILURE);
            action.setResponsePayload("{\"error\": true}");
            action.setResourceId("1001");
            action.setFailureReason("cart-service is down");

            assertThat(action.getStatus()).isEqualTo(ActionStatus.FAILURE);
            assertThat(action.getResponsePayload()).isEqualTo("{\"error\": true}");
            assertThat(action.getResourceId()).isEqualTo("1001");
            assertThat(action.getFailureReason()).isEqualTo("cart-service is down");
        }

        @Test
        @DisplayName("the timestamps are filled in by Hibernate and created_at never changes")
        void timestampsAreManagedByHibernate() {
            assertThat(field(AgentAction.class, "createdAt").getAnnotation(CreationTimestamp.class)).isNotNull();
            assertThat(field(AgentAction.class, "createdAt").getAnnotation(Column.class).updatable()).isFalse();
            assertThat(field(AgentAction.class, "updatedAt").getAnnotation(UpdateTimestamp.class)).isNotNull();
        }
    }
}

