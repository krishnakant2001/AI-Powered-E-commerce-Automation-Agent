package com.strikerkk.aicommerce.order_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("The custom exceptions")
class CustomExceptionsTest {

    @Test
    @DisplayName("ResourceNotFoundException carries the message an order lookup failed with")
    void resourceNotFoundCarriesItsMessage() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Order not found");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("Order not found");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("BadRequestException carries its message")
    void badRequestCarriesItsMessage() {
        assertThat(new BadRequestException("bad input"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("bad input");
    }

    @Test
    @DisplayName("AccessDeniedException carries its message")
    void accessDeniedCarriesItsMessage() {
        assertThat(new AccessDeniedException("not your order"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("not your order");
    }

    @Test
    @DisplayName("UnauthorizedException carries its message")
    void unauthorizedCarriesItsMessage() {
        assertThat(new UnauthorizedException("no token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("no token");
    }

    @Test
    @DisplayName("the custom IllegalStateException shadows nothing - it is a plain RuntimeException")
    void theCustomIllegalStateIsARuntimeException() {
        com.strikerkk.aicommerce.order_service.exception.IllegalStateException exception =
                new com.strikerkk.aicommerce.order_service.exception.IllegalStateException("wrong state");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isNotInstanceOf(java.lang.IllegalStateException.class);
        assertThat(exception.getMessage()).isEqualTo("wrong state");
    }

    @Test
    @DisplayName("every one of them is unchecked, so no signature has to declare them")
    void everyOneIsUnchecked() {
        Stream.of(ResourceNotFoundException.class,
                        BadRequestException.class,
                        AccessDeniedException.class,
                        UnauthorizedException.class,
                        com.strikerkk.aicommerce.order_service.exception.IllegalStateException.class)
                .forEach(type -> assertThat(RuntimeException.class).isAssignableFrom(type));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" survives the throw")
    @ValueSource(strings = {"Order not found", "Order is not found", "Delivered order cannot be cancelled"})
    @DisplayName("the message survives being thrown and caught")
    void theMessageSurvivesTheThrow(String message) {
        assertThatThrownBy(() -> {
            throw new ResourceNotFoundException(message);
        })
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(message);
    }

    @Test
    @DisplayName("a null message is tolerated by all of them")
    void aNullMessageIsTolerated() {
        Stream.<Function<String, RuntimeException>>of(
                        ResourceNotFoundException::new,
                        BadRequestException::new,
                        AccessDeniedException::new,
                        UnauthorizedException::new,
                        com.strikerkk.aicommerce.order_service.exception.IllegalStateException::new)
                .forEach(factory -> assertThat(factory.apply(null).getMessage()).isNull());
    }

    @Test
    @DisplayName("each of them exposes a single message constructor")
    void eachExposesASingleConstructor() {
        Stream.of(ResourceNotFoundException.class,
                        BadRequestException.class,
                        AccessDeniedException.class,
                        UnauthorizedException.class,
                        com.strikerkk.aicommerce.order_service.exception.IllegalStateException.class)
                .forEach(type -> {
                    assertThat(type.getDeclaredConstructors()).hasSize(1);
                    assertThat(type.getDeclaredConstructors()[0].getParameterTypes())
                            .containsExactly(String.class);
                });
    }
}

