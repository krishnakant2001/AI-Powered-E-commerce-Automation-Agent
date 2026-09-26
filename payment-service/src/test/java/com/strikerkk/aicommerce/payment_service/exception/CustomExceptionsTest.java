package com.strikerkk.aicommerce.payment_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("The custom exceptions")
class CustomExceptionsTest {

    private static Stream<Class<? extends RuntimeException>> all() {
        return Stream.of(
                AccessDeniedException.class,
                BadRequestException.class,
                ResourceNotFoundException.class,
                UnauthorizedException.class,
                IllegalStateException.class);
    }

    @Test
    @DisplayName("every one of them is unchecked, so no signature has to declare it")
    void everyOneIsUnchecked() {
        all().forEach(type -> assertThat(RuntimeException.class).isAssignableFrom(type));
    }

    @Test
    @DisplayName("every one of them carries the message the advice puts in the body")
    void everyOneCarriesItsMessage() throws Exception {
        for (Class<? extends RuntimeException> type : all().toList()) {
            RuntimeException exception = type.getConstructor(String.class).newInstance("boom");

            assertThat(exception.getMessage()).as(type.getSimpleName()).isEqualTo("boom");
        }
    }

    @Test
    @DisplayName("every one of them offers exactly the message constructor")
    void everyOneOffersTheMessageConstructor() {
        all().forEach(type -> assertThat(type.getDeclaredConstructors())
                .as(type.getSimpleName())
                .hasSize(1)
                .allMatch(c -> c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class));
    }

    @Test
    @DisplayName("an unauthorized payment names the order it refused")
    void anUnauthorizedPaymentNamesTheOrder() {
        assertThatThrownBy(() -> {
            throw new UnauthorizedException("Order does not belong to this user");
        })
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Order does not belong to this user")
                .hasNoCause();
    }

    @Test
    @DisplayName("the custom IllegalStateException is not the JDK one, so the advice treats it as a 500")
    void theCustomIllegalStateIsNotTheJdkOne() {
        assertThat(java.lang.IllegalStateException.class.isAssignableFrom(IllegalStateException.class))
                .isFalse();

        assertThat(IllegalStateException.class.getSuperclass()).isEqualTo(RuntimeException.class);
    }

    @Test
    @DisplayName("they all live in the exception package, the advice scans nothing else")
    void theyAllLiveInTheExceptionPackage() {
        all().forEach(type -> assertThat(type.getPackageName())
                .isEqualTo("com.strikerkk.aicommerce.payment_service.exception"));
    }

    @Test
    @DisplayName("a null message is allowed, the advice is what has to cope with it")
    void aNullMessageIsAllowed() throws Exception {
        for (Class<? extends RuntimeException> type : all().toList()) {
            assertThat(type.getConstructor(String.class).newInstance((Object) null).getMessage())
                    .as(type.getSimpleName())
                    .isNull();
        }
    }
}


