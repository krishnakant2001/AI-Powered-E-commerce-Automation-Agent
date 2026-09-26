package com.strikerkk.aicommerce.agent_service.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Enums")
class AgentEnumsTest {

    @Nested
    @DisplayName("SessionStatus")
    class SessionStatusTest {

        @Test
        @DisplayName("has exactly the five states the agent can be in")
        void hasTheExpectedConstants() {
            assertThat(SessionStatus.values())
                    .containsExactly(SessionStatus.ACTIVE, SessionStatus.COMPLETED, SessionStatus.CLARIFYING,
                            SessionStatus.FAILED, SessionStatus.CLOSED);
        }

        @Test
        @DisplayName("round-trips through its name, which is what the column stores")
        void roundTripsThroughItsName() {
            for (SessionStatus status : SessionStatus.values()) {
                assertThat(SessionStatus.valueOf(status.name())).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("rejects an unknown name")
        void rejectsAnUnknownName() {
            assertThatThrownBy(() -> SessionStatus.valueOf("PAUSED"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ActionStatus")
    class ActionStatusTest {

        @Test
        @DisplayName("has exactly the four outcomes an action can have")
        void hasTheExpectedConstants() {
            assertThat(ActionStatus.values())
                    .containsExactly(ActionStatus.PENDING, ActionStatus.SUCCESS,
                            ActionStatus.FAILURE, ActionStatus.SKIPPED);
        }

        @Test
        @DisplayName("PENDING is first, so it is the natural default")
        void pendingIsFirst() {
            assertThat(ActionStatus.values()[0]).isEqualTo(ActionStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("MessageRole")
    class MessageRoleTest {

        @Test
        @DisplayName("covers both conversation sides and both tool halves")
        void hasTheExpectedConstants() {
            assertThat(MessageRole.values())
                    .containsExactly(MessageRole.USER, MessageRole.ASSISTANT,
                            MessageRole.TOOL_CALL, MessageRole.TOOL_RESULT);
        }
    }

    @Nested
    @DisplayName("ActionType")
    class ActionTypeTest {

        @Test
        @DisplayName("covers every capability the agent exposes as a tool")
        void hasTheExpectedConstants() {
            assertThat(ActionType.values())
                    .containsExactly(ActionType.SEARCH_PRODUCT, ActionType.GET_PRODUCT_DETAILS,
                            ActionType.GET_VARIANT_INFO, ActionType.ADD_TO_CART, ActionType.UPDATE_CART_ITEM,
                            ActionType.CLEAR_CART, ActionType.PLACE_ORDER, ActionType.BUY_NOW,
                            ActionType.CANCEL_ORDER, ActionType.GET_ORDER_STATUS, ActionType.INITIATE_PAYMENT,
                            ActionType.GET_USER_ADDRESS, ActionType.ADD_USER_ADDRESS);
        }

        @Test
        @DisplayName("round-trips through its name")
        void roundTripsThroughItsName() {
            for (ActionType type : ActionType.values()) {
                assertThat(ActionType.valueOf(type.name())).isEqualTo(type);
            }
        }
    }
}

