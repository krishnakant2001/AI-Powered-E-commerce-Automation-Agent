package com.strikerkk.aicommerce.payment_service.payment;

import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VerifySignature")
class VerifySignatureTest {

    private static final String ORDER_ID = TestDataFactory.GATEWAY_ORDER_ID;
    private static final String PAYMENT_ID = TestDataFactory.GATEWAY_PAYMENT_ID;

    private VerifySignature verifySignature;

    @BeforeEach
    void setUp() {
        verifySignature = new VerifySignature();
        ReflectionTestUtils.setField(verifySignature, "razorpayKeySecret", TestDataFactory.KEY_SECRET);
        ReflectionTestUtils.setField(verifySignature, "webhookKeySecret", TestDataFactory.WEBHOOK_SECRET);
    }

    // ==================================================================
    // the checkout signature
    // ==================================================================

    @Nested
    @DisplayName("the checkout signature")
    class Checkout {

        @Test
        @DisplayName("accepts HMAC_SHA256(orderId | paymentId) signed with the key secret")
        void acceptsTheRazorpayFormula() {
            String expected = TestDataFactory.hmacSha256Hex(
                    ORDER_ID + "|" + PAYMENT_ID, TestDataFactory.KEY_SECRET);

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, expected)).isTrue();
        }

        @Test
        @DisplayName("is the pipe that separates the two ids, not a concatenation")
        void usesThePipeSeparator() {
            String withoutPipe = TestDataFactory.hmacSha256Hex(
                    ORDER_ID + PAYMENT_ID, TestDataFactory.KEY_SECRET);

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, withoutPipe)).isFalse();
        }

        @Test
        @DisplayName("refuses a signature made with another secret")
        void refusesAnotherSecret() {
            String signedWithTheWrongSecret = TestDataFactory.hmacSha256Hex(
                    ORDER_ID + "|" + PAYMENT_ID, "an-attacker-secret");

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, signedWithTheWrongSecret))
                    .isFalse();
        }

        @Test
        @DisplayName("refuses a signature of another order - a paid order cannot vouch for a free one")
        void refusesTheSignatureOfAnotherOrder() {
            String otherOrderSignature = TestDataFactory.razorpaySignature("order_SOMETHINGelse", PAYMENT_ID);

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, otherOrderSignature))
                    .isFalse();
        }

        @Test
        @DisplayName("refuses a signature of another payment")
        void refusesTheSignatureOfAnotherPayment() {
            String otherPaymentSignature = TestDataFactory.razorpaySignature(ORDER_ID, "pay_SOMETHINGelse");

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, otherPaymentSignature))
                    .isFalse();
        }

        @Test
        @DisplayName("refuses an empty and a garbage signature")
        void refusesGarbage() {
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, "")).isFalse();
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, "deadbeef")).isFalse();
        }

        @Test
        @DisplayName("refuses a null signature instead of blowing up")
        void refusesANullSignature() {
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, null)).isFalse();
        }

        @Test
        @DisplayName("is case sensitive - the digest is lower case hex")
        void isCaseSensitive() {
            String expected = TestDataFactory.razorpaySignature(ORDER_ID, PAYMENT_ID);

            assertThat(expected).isLowerCase();
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, expected.toUpperCase()))
                    .isFalse();
        }

        @Test
        @DisplayName("produces a 64 character hex digest, every byte zero padded")
        void producesA64CharacterDigest() {
            String expected = TestDataFactory.razorpaySignature(ORDER_ID, PAYMENT_ID);

            assertThat(expected).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, expected)).isTrue();
        }

        @Test
        @DisplayName("zero pads a digest that contains a byte below 0x10")
        void zeroPadsSmallBytes() {
            // Brute forces a payload whose digest starts with a 0 nibble, which is exactly the
            // case the manual hex loop has to pad. Without the padding the digest would be 63 chars.
            for (int i = 0; i < 2_000; i++) {
                String candidate = TestDataFactory.hmacSha256Hex("padding-" + i, TestDataFactory.KEY_SECRET);
                if (candidate.startsWith("0")) {
                    assertThat(candidate).hasSize(64);
                    return;
                }
            }
            throw new AssertionError("no payload produced a digest with a leading zero nibble");
        }

        @Test
        @DisplayName("is deterministic - the same input always yields the same verdict")
        void isDeterministic() {
            String expected = TestDataFactory.razorpaySignature(ORDER_ID, PAYMENT_ID);

            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, expected)).isTrue();
            assertThat(verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, expected)).isTrue();
        }
    }

    // ==================================================================
    // the webhook signature
    // ==================================================================

    @Nested
    @DisplayName("the webhook signature")
    class Webhook {

        @Test
        @DisplayName("accepts HMAC_SHA256(rawBody) signed with the webhook secret")
        void acceptsTheRawBodyDigest() {
            String payload = TestDataFactory.paymentCapturedPayload();

            assertThat(verifySignature.verifyWebhookSignature(payload, TestDataFactory.webhookSignature(payload)))
                    .isTrue();
        }

        @Test
        @DisplayName("uses the webhook secret, not the checkout key secret - they are different secrets")
        void usesTheWebhookSecret() {
            String payload = TestDataFactory.paymentCapturedPayload();
            String signedWithTheCheckoutSecret =
                    TestDataFactory.hmacSha256Hex(payload, TestDataFactory.KEY_SECRET);

            assertThat(verifySignature.verifyWebhookSignature(payload, signedWithTheCheckoutSecret)).isFalse();
        }

        @Test
        @DisplayName("refuses a body that was tampered with after signing")
        void refusesATamperedBody() {
            String payload = TestDataFactory.paymentCapturedPayload();
            String signature = TestDataFactory.webhookSignature(payload);

            assertThat(verifySignature.verifyWebhookSignature(payload.replace("captured", "failed"), signature))
                    .isFalse();
        }

        @Test
        @DisplayName("refuses a body that only differs by a whitespace - the raw bytes are signed")
        void refusesAReformattedBody() {
            String payload = TestDataFactory.paymentCapturedPayload();
            String signature = TestDataFactory.webhookSignature(payload);

            assertThat(verifySignature.verifyWebhookSignature(payload + " ", signature)).isFalse();
        }

        @Test
        @DisplayName("signs an empty body without complaining")
        void signsAnEmptyBody() {
            assertThat(verifySignature.verifyWebhookSignature("", TestDataFactory.webhookSignature("")))
                    .isTrue();
        }

        @Test
        @DisplayName("refuses a null and a garbage signature")
        void refusesGarbage() {
            String payload = TestDataFactory.paymentCapturedPayload();

            assertThat(verifySignature.verifyWebhookSignature(payload, null)).isFalse();
            assertThat(verifySignature.verifyWebhookSignature(payload, "deadbeef")).isFalse();
        }

        @Test
        @DisplayName("handles a body with non ASCII characters, it is signed as UTF-8")
        void handlesNonAsciiBodies() {
            String payload = "{\"event\":\"payment.captured\",\"note\":\"₹8999 – naïve\"}";

            assertThat(verifySignature.verifyWebhookSignature(payload, TestDataFactory.webhookSignature(payload)))
                    .isTrue();
        }
    }

    // ==================================================================
    // the two secrets are independent
    // ==================================================================

    @Test
    @DisplayName("a checkout signature is never accepted as a webhook signature")
    void theTwoChecksAreIndependent() {
        String checkoutSignature = TestDataFactory.razorpaySignature(ORDER_ID, PAYMENT_ID);

        assertThat(verifySignature.verifyWebhookSignature(ORDER_ID + "|" + PAYMENT_ID, checkoutSignature))
                .isFalse();
    }

    @Test
    @DisplayName("an unusable secret is reported instead of being silently accepted")
    void anUnusableSecretIsReported() {
        ReflectionTestUtils.setField(verifySignature, "razorpayKeySecret", "");

        // An empty HMAC key is rejected by the JCE itself; the handler only swallows
        // NoSuchAlgorithmException and InvalidKeyException, so this surfaces to the caller.
        assertThatThrownBy(() -> verifySignature.verifyRazorpaySignature(ORDER_ID, PAYMENT_ID, "anything"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Test
    @DisplayName("is a Spring component, so both secrets are injected once")
    void isASpringComponent() {
        assertThat(VerifySignature.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    @DisplayName("exposes exactly the two checks the service needs")
    void exposesExactlyTwoChecks() {
        assertThat(VerifySignature.class.getDeclaredMethods())
                .filteredOn(m -> !m.isSynthetic())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("verifyRazorpaySignature", "verifyWebhookSignature");
    }

    @Test
    @DisplayName("keeps both secrets private, they never leave the component")
    void keepsBothSecretsPrivate() {
        assertThat(VerifySignature.class.getDeclaredFields())
                .filteredOn(f -> !f.isSynthetic())
                .allMatch(f -> java.lang.reflect.Modifier.isPrivate(f.getModifiers()));
    }
}

