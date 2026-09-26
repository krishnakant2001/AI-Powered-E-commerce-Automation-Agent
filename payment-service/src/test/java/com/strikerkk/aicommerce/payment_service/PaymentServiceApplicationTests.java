package com.strikerkk.aicommerce.payment_service;

import com.razorpay.RazorpayClient;
import com.strikerkk.aicommerce.payment_service.auth.FeignClientInterceptor;
import com.strikerkk.aicommerce.payment_service.auth.UserInterceptor;
import com.strikerkk.aicommerce.payment_service.auth.WebConfig;
import com.strikerkk.aicommerce.payment_service.clients.OrderClient;
import com.strikerkk.aicommerce.payment_service.controller.PaymentController;
import com.strikerkk.aicommerce.payment_service.controller.WebhookController;
import com.strikerkk.aicommerce.payment_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.payment_service.payment.PaymentPage;
import com.strikerkk.aicommerce.payment_service.payment.VerifySignature;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import com.strikerkk.aicommerce.payment_service.service.PaymentResilience4j;
import com.strikerkk.aicommerce.payment_service.service.PaymentService;
import com.strikerkk.aicommerce.payment_service.service.WebhookService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("PaymentServiceApplication")
class PaymentServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("the application context starts")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("is a Spring Boot application with the Feign clients enabled")
    void isAnnotatedCorrectly() {
        assertThat(PaymentServiceApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
        assertThat(PaymentServiceApplication.class.getAnnotation(EnableFeignClients.class)).isNotNull();
    }

    @Test
    @DisplayName("every controller is registered")
    void everyControllerIsRegistered() {
        assertThat(applicationContext.getBean(PaymentController.class)).isNotNull();
        assertThat(applicationContext.getBean(WebhookController.class)).isNotNull();
        assertThat(applicationContext.getBean(PaymentPage.class)).isNotNull();
    }

    @Test
    @DisplayName("every service is registered")
    void everyServiceIsRegistered() {
        assertThat(applicationContext.getBean(PaymentService.class)).isNotNull();
        assertThat(applicationContext.getBean(WebhookService.class)).isNotNull();
        assertThat(applicationContext.getBean(PaymentResilience4j.class)).isNotNull();
    }

    @Test
    @DisplayName("both repositories are registered")
    void bothRepositoriesAreRegistered() {
        assertThat(applicationContext.getBean(PaymentRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(RefundRepository.class)).isNotNull();
    }

    @Test
    @DisplayName("the Feign client to order-service is created")
    void theFeignClientIsCreated() {
        assertThat(applicationContext.getBean(OrderClient.class)).isNotNull();
    }

    @Test
    @DisplayName("the gateway-header infrastructure is registered")
    void theHeaderInfrastructureIsRegistered() {
        assertThat(applicationContext.getBean(UserInterceptor.class)).isNotNull();
        assertThat(applicationContext.getBean(WebConfig.class)).isNotNull();
    }

    @Test
    @DisplayName("the UserInterceptor is really plugged into the MVC pipeline")
    void theInterceptorIsPluggedIn() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        // getAdaptedInterceptors() is protected on AbstractHandlerMapping.
        HandlerInterceptor[] interceptors =
                (HandlerInterceptor[]) ReflectionTestUtils.invokeMethod(mapping, "getAdaptedInterceptors");

        assertThat(interceptors).hasAtLeastOneElementOfType(UserInterceptor.class);
    }

    @Test
    @DisplayName("the caller id is propagated to every downstream call")
    void theCallerIdIsPropagated() {
        assertThat(applicationContext.getBean(FeignClientInterceptor.class)).isNotNull();
        assertThat(applicationContext.getBeansOfType(feign.RequestInterceptor.class).values())
                .anyMatch(FeignClientInterceptor.class::isInstance);
    }

    @Test
    @DisplayName("the exception advice is registered")
    void theAdviceIsRegistered() {
        assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    @DisplayName("a single ModelMapper is shared by the whole service")
    void aSingleModelMapperIsShared() {
        assertThat(applicationContext.getBeansOfType(ModelMapper.class)).hasSize(1);
    }

    @Test
    @DisplayName("a single Razorpay client is shared, and it carries the order sub client")
    void aSingleRazorpayClientIsShared() {
        assertThat(applicationContext.getBeansOfType(RazorpayClient.class)).hasSize(1);
        assertThat(applicationContext.getBean(RazorpayClient.class).orders).isNotNull();
    }

    @Test
    @DisplayName("the signature checker is registered with both secrets injected")
    void theSignatureCheckerIsRegistered() {
        VerifySignature verifySignature = applicationContext.getBean(VerifySignature.class);

        assertThat(verifySignature).isNotNull();
        assertThat(ReflectionTestUtils.getField(verifySignature, "razorpayKeySecret"))
                .isEqualTo("dummysecret");
        assertThat(ReflectionTestUtils.getField(verifySignature, "webhookKeySecret"))
                .isEqualTo("dummywebhooksecret");
    }

    @Test
    @DisplayName("the messaging side is wired: template and topic")
    void theMessagingSideIsWired() {
        assertThat(applicationContext.getBeansOfType(KafkaTemplate.class)).isNotEmpty();
        assertThat(applicationContext.getBean(NewTopic.class).name()).isEqualTo("payment-success-topic");
    }

    @Test
    @DisplayName("the resilience guards of the order-service call are configured")
    void theResilienceGuardsAreConfigured() {
        assertThat(applicationContext.getBean(CircuitBreakerRegistry.class)
                .circuitBreaker("order-service-call")).isNotNull();
        assertThat(applicationContext.getBean(RetryRegistry.class)
                .retry("order-service-call")).isNotNull();
    }

    @Test
    @DisplayName("the circuit breaker opens at a 50 percent failure rate over ten calls")
    void theCircuitBreakerIsTuned() {
        var config = applicationContext.getBean(CircuitBreakerRegistry.class)
                .circuitBreaker("order-service-call")
                .getCircuitBreakerConfig();

        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    @DisplayName("the order-service call is retried three times")
    void theRetryIsTuned() {
        assertThat(applicationContext.getBean(RetryRegistry.class)
                .retry("order-service-call")
                .getRetryConfig()
                .getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("every endpoint of the payment API is mapped")
    void everyEndpointIsMapped() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet())
                .extracting(Object::toString)
                .anyMatch(info -> info.contains("POST") && info.contains("/payments/initiate"))
                .anyMatch(info -> info.contains("POST") && info.contains("/payments/verify"))
                .anyMatch(info -> info.contains("GET") && info.contains("/payments/{paymentId}/refunds"))
                .anyMatch(info -> info.contains("POST") && info.contains("/payments/webhook/razorpay"))
                .anyMatch(info -> info.contains("GET") && info.contains("/payments/page/{gatewayOrderId}"));
    }
}
