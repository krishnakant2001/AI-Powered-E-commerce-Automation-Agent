package com.strikerkk.aicommerce.payment_service.controller;

import com.strikerkk.aicommerce.payment_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.payment_service.service.WebhookService;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController")
class WebhookControllerTest {

    private static final String PATH = "/payments/webhook/razorpay";
    private static final String SIGNATURE_HEADER = TestDataFactory.RAZORPAY_SIGNATURE_HEADER;

    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private WebhookController webhookController;

    @Captor
    private ArgumentCaptor<String> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> signatureCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================================================================
    // the happy path
    // ==================================================================

    @Nested
    @DisplayName("a well formed delivery")
    class WellFormedDelivery {

        @Test
        @DisplayName("is acknowledged with an empty 200, so Razorpay stops retrying")
        void isAcknowledgedWithAnEmpty200() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(TestDataFactory.paymentCapturedPayload()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("hands the service the raw body, byte for byte - the signature covers it")
        void handsTheServiceTheRawBody() throws Exception {
            String payload = TestDataFactory.paymentCapturedPayload();

            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(payload))
                    .andExpect(status().isOk());

            verify(webhookService).handleRazorpayWebhook(payloadCaptor.capture(), signatureCaptor.capture());
            assertThat(payloadCaptor.getValue()).isEqualTo(payload);
            assertThat(signatureCaptor.getValue()).isEqualTo("a-signature");
        }

        @Test
        @DisplayName("never re-serialises the body, a reformatted payload would break the signature")
        void neverReSerialisesTheBody() throws Exception {
            String payload = "{\"event\" :   \"payment.captured\",  \"payload\" : {} }";

            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(payload))
                    .andExpect(status().isOk());

            verify(webhookService).handleRazorpayWebhook(payloadCaptor.capture(), anyString());
            assertThat(payloadCaptor.getValue()).isEqualTo(payload);
        }

        @Test
        @DisplayName("forwards a payload that is not JSON at all - the service decides")
        void forwardsANonJsonPayload() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content("not json"))
                    .andExpect(status().isOk());

            verify(webhookService).handleRazorpayWebhook("not json", "a-signature");
        }

        @Test
        @DisplayName("accepts every Razorpay event, the routing lives in the service")
        void acceptsEveryEvent() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(TestDataFactory.paymentFailedPayload()))
                    .andExpect(status().isOk());

            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(TestDataFactory.refundProcessedPayload()))
                    .andExpect(status().isOk());

            verify(webhookService, org.mockito.Mockito.times(2))
                    .handleRazorpayWebhook(anyString(), anyString());
        }
    }

    // ==================================================================
    // failures
    // ==================================================================

    @Nested
    @DisplayName("a suspicious delivery")
    class SuspiciousDelivery {

        @Test
        @DisplayName("without the signature header never reaches the service")
        void withoutTheSignatureHeaderIsRejected() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TestDataFactory.paymentCapturedPayload()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(webhookService);
        }

        @Test
        @DisplayName("with a forged signature becomes a 500, Razorpay will retry")
        void withAForgedSignatureBecomesA500() throws Exception {
            doThrow(new RuntimeException("Invalid webhook signature"))
                    .when(webhookService).handleRazorpayWebhook(anyString(), anyString());

            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "forged")
                            .content(TestDataFactory.paymentCapturedPayload()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid webhook signature"));
        }

        @Test
        @DisplayName("about an unknown payment becomes a 500, so the delivery is retried")
        void aboutAnUnknownPaymentBecomesA500() throws Exception {
            doThrow(new RuntimeException("Payment not found"))
                    .when(webhookService).handleRazorpayWebhook(anyString(), anyString());

            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "a-signature")
                            .content(TestDataFactory.paymentCapturedPayload()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment not found"));
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("is a REST controller rooted at /payments/webhook")
        void isARestControllerRootedAtTheWebhookPath() {
            assertThat(WebhookController.class.getAnnotation(RestController.class)).isNotNull();
            assertThat(WebhookController.class.getAnnotation(RequestMapping.class).value())
                    .containsExactly("/payments/webhook");
        }

        @Test
        @DisplayName("listens on POST /payments/webhook/razorpay")
        void listensOnTheRazorpayPath() throws Exception {
            PostMapping mapping = WebhookController.class
                    .getMethod("handleRazorpayWebhook", String.class, String.class)
                    .getAnnotation(PostMapping.class);

            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly("/razorpay");
        }

        @Test
        @DisplayName("takes the body as a raw String, never as a parsed DTO")
        void takesTheBodyAsARawString() throws Exception {
            assertThat(WebhookController.class
                    .getMethod("handleRazorpayWebhook", String.class, String.class)
                    .getParameterTypes())
                    .containsExactly(String.class, String.class);
        }

        @Test
        @DisplayName("reads the signature from the X-Razorpay-Signature header")
        void readsTheSignatureHeader() throws Exception {
            org.springframework.web.bind.annotation.RequestHeader header =
                    WebhookController.class
                            .getMethod("handleRazorpayWebhook", String.class, String.class)
                            .getParameters()[1]
                            .getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);

            assertThat(header).isNotNull();
            assertThat(header.value()).isEqualTo(SIGNATURE_HEADER);
        }

        @Test
        @DisplayName("answers with no body at all, Razorpay only reads the status code")
        void answersWithNoBody() throws Exception {
            assertThat(WebhookController.class
                    .getMethod("handleRazorpayWebhook", String.class, String.class)
                    .getGenericReturnType().getTypeName())
                    .isEqualTo("org.springframework.http.ResponseEntity<java.lang.Void>");
        }

        @Test
        @DisplayName("exposes exactly one endpoint")
        void exposesExactlyOneEndpoint() {
            assertThat(WebhookController.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .extracting(java.lang.reflect.Method::getName)
                    .containsExactly("handleRazorpayWebhook");
        }
    }
}

