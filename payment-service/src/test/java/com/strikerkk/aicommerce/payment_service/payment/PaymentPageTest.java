package com.strikerkk.aicommerce.payment_service.payment;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPage")
class PaymentPageTest {

    private static final String GATEWAY_ORDER_ID = TestDataFactory.GATEWAY_ORDER_ID;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentPage paymentPage;

    @BeforeEach
    void setUp() {
        paymentPage = new PaymentPage(paymentRepository);
        ReflectionTestUtils.setField(paymentPage, "razorpayKeyId", TestDataFactory.KEY_ID);
    }

    private String body() {
        return paymentPage.getPaymentPage(GATEWAY_ORDER_ID).getBody();
    }

    // ==================================================================
    // the page
    // ==================================================================

    @Nested
    @DisplayName("the rendered page")
    class RenderedPage {

        @BeforeEach
        void storedPayment() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment()));
        }

        @Test
        @DisplayName("answers with 200 and an HTML document")
        void answersWithHtml() {
            ResponseEntity<String> response = paymentPage.getPaymentPage(GATEWAY_ORDER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).startsWith("<!DOCTYPE html>").contains("</html>");
        }

        @Test
        @DisplayName("declares UTF-8, the rupee sign must survive")
        void declaresUtf8() {
            ResponseEntity<String> response = paymentPage.getPaymentPage(GATEWAY_ORDER_ID);

            assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("text/html; charset=UTF-8");
            assertThat(response.getBody()).contains("₹");
        }

        @Test
        @DisplayName("shows the order the customer is about to pay for")
        void showsTheOrder() {
            assertThat(body()).contains("Order ID: " + TestDataFactory.ORDER_ID);
        }

        @Test
        @DisplayName("shows the amount in rupees for the human and in paise for the gateway")
        void showsBothAmounts() {
            String html = body();

            assertThat(html).contains("₹" + TestDataFactory.AMOUNT);
            assertThat(html).contains("amount: " + TestDataFactory.AMOUNT_IN_PAISE);
        }

        @Test
        @DisplayName("hands the checkout the publishable key and the gateway order id")
        void handsTheCheckoutItsParameters() {
            String html = body();

            assertThat(html).contains("key: \"" + TestDataFactory.KEY_ID + "\"");
            assertThat(html).contains("order_id: \"" + GATEWAY_ORDER_ID + "\"");
            assertThat(html).contains("currency: \"INR\"");
        }

        @Test
        @DisplayName("never prints the key secret into the browser")
        void neverPrintsTheKeySecret() {
            assertThat(body()).doesNotContain(TestDataFactory.KEY_SECRET);
        }

        @Test
        @DisplayName("loads the Razorpay checkout script")
        void loadsTheCheckoutScript() {
            assertThat(body()).contains("https://checkout.razorpay.com/v1/checkout.js");
        }
    }

    // ==================================================================
    // amounts
    // ==================================================================

    @Nested
    @DisplayName("the amount conversion")
    class AmountConversion {

        private String pageFor(String amount) {
            Payment payment = TestDataFactory.payment();
            payment.setAmount(new BigDecimal(amount));
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));
            return body();
        }

        @Test
        @DisplayName("turns whole rupees into paise")
        void turnsRupeesIntoPaise() {
            assertThat(pageFor("1.00")).contains("amount: 100");
        }

        @Test
        @DisplayName("keeps the paise of a fractional amount")
        void keepsThePaise() {
            assertThat(pageFor("499.99")).contains("amount: 49999");
        }

        @Test
        @DisplayName("truncates below a paisa, the gateway only counts whole paise")
        void truncatesBelowAPaisa() {
            assertThat(pageFor("10.999")).contains("amount: 1099");
        }

        @Test
        @DisplayName("renders a zero amount without breaking the page")
        void rendersAZeroAmount() {
            assertThat(pageFor("0.00")).contains("amount: 0");
        }
    }

    // ==================================================================
    // failures
    // ==================================================================

    @Test
    @DisplayName("refuses to render a page for a payment that was never initiated")
    void refusesAnUnknownGatewayOrder() {
        when(paymentRepository.findByGatewayOrderId("order_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPage.getPaymentPage("order_UNKNOWN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Payment is not initiated yet");
    }

    @Test
    @DisplayName("renders the page of an already paid payment as well, it is only a helper screen")
    void rendersAnAlreadyPaidPayment() {
        when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                .thenReturn(Optional.of(TestDataFactory.capturedPayment()));

        assertThat(body()).contains("Order ID: " + TestDataFactory.ORDER_ID);
    }

    @Test
    @DisplayName("blows up on a payment without an amount")
    void blowsUpWithoutAnAmount() {
        Payment payment = TestDataFactory.payment(1L, TestDataFactory.USER_ID, PaymentStatus.INITIATED);
        payment.setAmount(null);
        when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentPage.getPaymentPage(GATEWAY_ORDER_ID))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================================================================
    // HTTP
    // ==================================================================

    @Nested
    @DisplayName("over HTTP")
    class OverHttp {

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.standaloneSetup(paymentPage).build();
        }

        @Test
        @DisplayName("is served at GET /payments/page/{gatewayOrderId}")
        void isServedAtThePagePath() throws Exception {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            mockMvc.perform(get("/payments/page/{gatewayOrderId}", GATEWAY_ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Payment Page")));
        }

        @Test
        @DisplayName("takes the gateway order id straight from the path")
        void takesTheIdFromThePath() throws Exception {
            when(paymentRepository.findByGatewayOrderId("order_FROMpath"))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            mockMvc.perform(get("/payments/page/{gatewayOrderId}", "order_FROMpath"))
                    .andExpect(status().isOk());
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("is a REST controller under /payments")
        void isARestControllerUnderPayments() {
            assertThat(PaymentPage.class.getAnnotation(RestController.class)).isNotNull();
            assertThat(PaymentPage.class.getAnnotation(RequestMapping.class).value())
                    .containsExactly("/payments");
        }

        @Test
        @DisplayName("exposes a single read only endpoint")
        void exposesASingleReadOnlyEndpoint() throws Exception {
            GetMapping mapping = PaymentPage.class
                    .getMethod("getPaymentPage", String.class)
                    .getAnnotation(GetMapping.class);

            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly("/page/{gatewayOrderId}");
        }

        @Test
        @DisplayName("keeps the page builder private, it is not part of the API")
        void keepsTheBuilderPrivate() throws Exception {
            assertThat(java.lang.reflect.Modifier.isPrivate(PaymentPage.class
                    .getDeclaredMethod("createPaymentPage", String.class)
                    .getModifiers())).isTrue();
        }
    }
}

