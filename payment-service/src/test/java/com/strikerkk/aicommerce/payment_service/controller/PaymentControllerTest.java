package com.strikerkk.aicommerce.payment_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.response.InitiatePaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.RefundResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import com.strikerkk.aicommerce.payment_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.payment_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.payment_service.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    private static final Long PAYMENT_ID = TestDataFactory.PAYMENT_ID;
    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @Captor
    private ArgumentCaptor<InitiatePaymentRequest> initiateCaptor;

    @Captor
    private ArgumentCaptor<VerifyPaymentRequest> verifyCaptor;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private InitiatePaymentResponse initiateResponse() {
        InitiatePaymentResponse response = new InitiatePaymentResponse();
        response.setPaymentId(PAYMENT_ID);
        response.setGatewayOrderId(TestDataFactory.GATEWAY_ORDER_ID);
        response.setAmount(TestDataFactory.AMOUNT);
        response.setCurrency("INR");
        response.setGateway(PaymentGateway.RAZORPAY);
        response.setKeyId(TestDataFactory.KEY_ID);
        return response;
    }

    private VerifyPaymentResponse verifyResponse(PaymentStatus status, String message) {
        VerifyPaymentResponse response = new VerifyPaymentResponse();
        response.setPaymentId(PAYMENT_ID);
        response.setOrderId(ORDER_ID);
        response.setStatus(status);
        response.setMessage(message);
        response.setPaidAt(LocalDateTime.now());
        return response;
    }

    private RefundResponse refundResponse(Long id, String amount, RefundStatus status) {
        RefundResponse response = new RefundResponse();
        response.setId(id);
        response.setPaymentId(PAYMENT_ID);
        response.setRefundAmount(new BigDecimal(amount));
        response.setRefundStatus(status);
        response.setGatewayRefundId(TestDataFactory.GATEWAY_REFUND_ID);
        response.setReason("Customer changed their mind");
        response.setRefundedAt(LocalDateTime.now());
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    // ==================================================================
    // POST /payments/initiate
    // ==================================================================

    @Nested
    @DisplayName("POST /payments/initiate")
    class Initiate {

        @Test
        @DisplayName("answers 201 - a payment attempt has been created at the gateway")
        void answersWith201() throws Exception {
            when(paymentService.initiatePayment(any())).thenReturn(initiateResponse());

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Payment created successfully"));
        }

        @Test
        @DisplayName("hands the browser everything the Razorpay checkout needs")
        void handsTheBrowserTheCheckoutData() throws Exception {
            when(paymentService.initiatePayment(any())).thenReturn(initiateResponse());

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                    .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                    .andExpect(jsonPath("$.data.gatewayOrderId").value(TestDataFactory.GATEWAY_ORDER_ID))
                    .andExpect(jsonPath("$.data.amount").value(8999.00))
                    .andExpect(jsonPath("$.data.currency").value("INR"))
                    .andExpect(jsonPath("$.data.gateway").value("RAZORPAY"))
                    .andExpect(jsonPath("$.data.keyId").value(TestDataFactory.KEY_ID));
        }

        @Test
        @DisplayName("binds the order id of the body and passes it to the service")
        void bindsTheOrderId() throws Exception {
            when(paymentService.initiatePayment(any())).thenReturn(initiateResponse());

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":777,\"gateway\":\"RAZORPAY\"}"))
                    .andExpect(status().isCreated());

            verify(paymentService).initiatePayment(initiateCaptor.capture());
            assertThat(initiateCaptor.getValue().getOrderId()).isEqualTo(777L);
            assertThat(initiateCaptor.getValue().getGateway()).isEqualTo(PaymentGateway.RAZORPAY);
        }

        @Test
        @DisplayName("stamps every answer with a timestamp")
        void stampsEveryAnswer() throws Exception {
            when(paymentService.initiatePayment(any())).thenReturn(initiateResponse());

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @DisplayName("turns 'not your order' into a 403")
        void turnsAForeignOrderIntoA403() throws Exception {
            when(paymentService.initiatePayment(any()))
                    .thenThrow(new UnauthorizedException("Order does not belong to this user"));

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order does not belong to this user"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("turns a double payment into a 500 carrying the reason")
        void turnsADoublePaymentIntoA500() throws Exception {
            when(paymentService.initiatePayment(any()))
                    .thenThrow(new RuntimeException("Payment already exists for this order"));

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment already exists for this order"));
        }

        @Test
        @DisplayName("never reaches the service when the body is not JSON")
        void neverReachesTheServiceOnGarbage() throws Exception {
            // HttpMessageNotReadableException is a RuntimeException, so the catch all of the advice
            // grabs it before Spring can answer 400 - the caller is told 500 for their own mistake.
            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("refuses a request without a body")
        void refusesARequestWithoutABody() throws Exception {
            mockMvc.perform(post("/payments/initiate").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("a malformed body is answered 500 instead of 400 (documents the bug)")
        void aMalformedBodyIsAnsweredWithA500() throws Exception {
            // The advice handles RuntimeException, and every Spring MVC binding failure derives
            // from it, so the whole 4xx family of the framework is rewritten into a 500.
            assertThat(RuntimeException.class).isAssignableFrom(
                    org.springframework.http.converter.HttpMessageNotReadableException.class);

            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\": }"))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================================================================
    // POST /payments/verify
    // ==================================================================

    @Nested
    @DisplayName("POST /payments/verify")
    class Verify {

        @Test
        @DisplayName("answers 200 with the verified payment")
        void answersWith200() throws Exception {
            when(paymentService.verifyPayment(any()))
                    .thenReturn(verifyResponse(PaymentStatus.SUCCESS, "Payment verified successfully"));

            mockMvc.perform(post("/payments/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Payment verified"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                    .andExpect(jsonPath("$.data.orderId").value(ORDER_ID));
        }

        @Test
        @DisplayName("binds the three Razorpay fields of the checkout callback")
        void bindsTheThreeRazorpayFields() throws Exception {
            when(paymentService.verifyPayment(any()))
                    .thenReturn(verifyResponse(PaymentStatus.SUCCESS, "Payment verified successfully"));

            mockMvc.perform(post("/payments/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"razorpayOrderId":"order_A","razorpayPaymentId":"pay_B","razorpaySignature":"sig_C"}
                                    """))
                    .andExpect(status().isOk());

            verify(paymentService).verifyPayment(verifyCaptor.capture());
            VerifyPaymentRequest request = verifyCaptor.getValue();
            assertThat(request.getRazorpayOrderId()).isEqualTo("order_A");
            assertThat(request.getRazorpayPaymentId()).isEqualTo("pay_B");
            assertThat(request.getRazorpaySignature()).isEqualTo("sig_C");
        }

        @Test
        @DisplayName("a failed verification is still a 200 - the answer carries the verdict")
        void aFailedVerificationIsStillA200() throws Exception {
            when(paymentService.verifyPayment(any()))
                    .thenReturn(verifyResponse(PaymentStatus.FAILED, "Payment failed due to some reason"));

            mockMvc.perform(post("/payments/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.message").value("Payment failed due to some reason"));
        }

        @Test
        @DisplayName("turns an unknown Razorpay order into a 500")
        void turnsAnUnknownOrderIntoA500() throws Exception {
            when(paymentService.verifyPayment(any()))
                    .thenThrow(new RuntimeException("Payment not found for this Razorpay order"));

            mockMvc.perform(post("/payments/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment not found for this Razorpay order"));
        }
    }

    // ==================================================================
    // GET /payments/{paymentId}/refunds
    // ==================================================================

    @Nested
    @DisplayName("GET /payments/{paymentId}/refunds")
    class Refunds {

        @Test
        @DisplayName("answers 200 with every refund of the payment")
        void answersWithEveryRefund() throws Exception {
            when(paymentService.getRefundsByPaymentId(PAYMENT_ID)).thenReturn(List.of(
                    refundResponse(1L, "1000.00", RefundStatus.SUCCESS),
                    refundResponse(2L, "7999.00", RefundStatus.PENDING)));

            mockMvc.perform(get("/payments/{paymentId}/refunds", PAYMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Getting refund list"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].refundAmount").value(1000.00))
                    .andExpect(jsonPath("$.data[0].refundStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[1].refundStatus").value("PENDING"));
        }

        @Test
        @DisplayName("answers 200 with an empty list when nothing was refunded")
        void answersWithAnEmptyList() throws Exception {
            when(paymentService.getRefundsByPaymentId(PAYMENT_ID)).thenReturn(List.of());

            mockMvc.perform(get("/payments/{paymentId}/refunds", PAYMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("reads the payment id from the path")
        void readsThePaymentIdFromThePath() throws Exception {
            when(paymentService.getRefundsByPaymentId(321L)).thenReturn(List.of());

            mockMvc.perform(get("/payments/{paymentId}/refunds", 321L)).andExpect(status().isOk());

            verify(paymentService).getRefundsByPaymentId(321L);
        }

        @Test
        @DisplayName("turns 'not your payment' into a 500 - the service raises a plain RuntimeException")
        void turnsAForeignPaymentIntoA500() throws Exception {
            when(paymentService.getRefundsByPaymentId(PAYMENT_ID))
                    .thenThrow(new RuntimeException("Payment does not belong to this user"));

            mockMvc.perform(get("/payments/{paymentId}/refunds", PAYMENT_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment does not belong to this user"));
        }

        @Test
        @DisplayName("refuses a payment id that is not a number")
        void refusesANonNumericId() throws Exception {
            mockMvc.perform(get("/payments/{paymentId}/refunds", "abc"))
                    .andExpect(status().is5xxServerError());

            verifyNoInteractions(paymentService);
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("is a REST controller rooted at /payments")
        void isARestControllerRootedAtPayments() {
            assertThat(PaymentController.class.getAnnotation(RestController.class)).isNotNull();
            assertThat(PaymentController.class.getAnnotation(RequestMapping.class).value())
                    .containsExactly("/payments");
        }

        @Test
        @DisplayName("exposes exactly the three endpoints of the payment API")
        void exposesExactlyThreeEndpoints() {
            assertThat(PaymentController.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .extracting(java.lang.reflect.Method::getName)
                    .containsExactlyInAnyOrder("initiatePayment", "verifyPayment", "getRefunds");
        }

        @Test
        @DisplayName("initiating and verifying are POSTs, listing the refunds is a GET")
        void usesTheRightVerbs() throws Exception {
            assertThat(PaymentController.class
                    .getMethod("initiatePayment", InitiatePaymentRequest.class)
                    .getAnnotation(PostMapping.class).value()).containsExactly("/initiate");

            assertThat(PaymentController.class
                    .getMethod("verifyPayment", VerifyPaymentRequest.class)
                    .getAnnotation(PostMapping.class).value()).containsExactly("/verify");

            assertThat(PaymentController.class
                    .getMethod("getRefunds", Long.class)
                    .getAnnotation(GetMapping.class).value()).containsExactly("/{paymentId}/refunds");
        }

        @Test
        @DisplayName("no endpoint takes a user id - the gateway header carries the caller")
        void noEndpointTakesAUserId() {
            assertThat(PaymentController.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .allSatisfy(method -> assertThat(method.getParameterCount()).isEqualTo(1));
        }
    }
}


