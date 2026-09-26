package com.strikerkk.aicommerce.order_service;

import com.strikerkk.aicommerce.order_service.auth.FeignClientInterceptor;
import com.strikerkk.aicommerce.order_service.auth.UserInterceptor;
import com.strikerkk.aicommerce.order_service.auth.WebConfig;
import com.strikerkk.aicommerce.order_service.clients.CartClient;
import com.strikerkk.aicommerce.order_service.clients.ProductClient;
import com.strikerkk.aicommerce.order_service.clients.UserClient;
import com.strikerkk.aicommerce.order_service.consumers.PaymentSuccessConsumer;
import com.strikerkk.aicommerce.order_service.controller.OrderController;
import com.strikerkk.aicommerce.order_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.order_service.repository.OrderItemRepository;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.order_service.service.OrderResilience4j;
import com.strikerkk.aicommerce.order_service.service.OrderService;
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
@DisplayName("OrderServiceApplication")
class OrderServiceApplicationTests {

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
        assertThat(OrderServiceApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
        assertThat(OrderServiceApplication.class.getAnnotation(EnableFeignClients.class)).isNotNull();
    }

    @Test
    @DisplayName("the controller is registered")
    void controllerIsRegistered() {
        assertThat(applicationContext.getBean(OrderController.class)).isNotNull();
    }

    @Test
    @DisplayName("both services are registered")
    void servicesAreRegistered() {
        assertThat(applicationContext.getBean(OrderService.class)).isNotNull();
        assertThat(applicationContext.getBean(OrderResilience4j.class)).isNotNull();
    }

    @Test
    @DisplayName("both repositories are registered")
    void repositoriesAreRegistered() {
        assertThat(applicationContext.getBean(OrderRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(OrderItemRepository.class)).isNotNull();
    }

    @Test
    @DisplayName("the Feign clients to cart-, user- and product-service are created")
    void everyFeignClientIsCreated() {
        assertThat(applicationContext.getBean(CartClient.class)).isNotNull();
        assertThat(applicationContext.getBean(UserClient.class)).isNotNull();
        assertThat(applicationContext.getBean(ProductClient.class)).isNotNull();
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
    @DisplayName("the messaging side is wired: consumer, template and topic")
    void theMessagingSideIsWired() {
        assertThat(applicationContext.getBean(PaymentSuccessConsumer.class)).isNotNull();
        assertThat(applicationContext.getBeansOfType(KafkaTemplate.class)).isNotEmpty();
        assertThat(applicationContext.getBean(NewTopic.class).name()).isEqualTo("order-confirmed-topic");
    }

    @Test
    @DisplayName("the resilience guards of the product-service call are configured")
    void theResilienceGuardsAreConfigured() {
        assertThat(applicationContext.getBean(CircuitBreakerRegistry.class)
                .circuitBreaker("product-service-call")).isNotNull();
        assertThat(applicationContext.getBean(RetryRegistry.class)
                .retry("product-service-call")).isNotNull();
    }

    @Test
    @DisplayName("every endpoint of the order API is mapped")
    void everyEndpointIsMapped() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet())
                .extracting(Object::toString)
                .anyMatch(info -> info.contains("POST") && info.contains("/orders]"))
                .anyMatch(info -> info.contains("POST") && info.contains("/orders/buy-now"))
                .anyMatch(info -> info.contains("GET") && info.contains("/orders/{orderId}]"))
                .anyMatch(info -> info.contains("GET") && info.contains("/orders/my-orders"))
                .anyMatch(info -> info.contains("PATCH") && info.contains("/orders/{orderId}/cancel"))
                .anyMatch(info -> info.contains("GET") && info.contains("/orders/{orderId}/items"));
    }
}
