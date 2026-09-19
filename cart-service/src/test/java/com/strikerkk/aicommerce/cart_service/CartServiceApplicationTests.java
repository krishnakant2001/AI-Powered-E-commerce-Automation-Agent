package com.strikerkk.aicommerce.cart_service;

import com.strikerkk.aicommerce.cart_service.auth.FeignClientInterceptor;
import com.strikerkk.aicommerce.cart_service.auth.UserInterceptor;
import com.strikerkk.aicommerce.cart_service.auth.WebConfig;
import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.controller.CartController;
import com.strikerkk.aicommerce.cart_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.cart_service.repository.CartItemRepository;
import com.strikerkk.aicommerce.cart_service.repository.CartRepository;
import com.strikerkk.aicommerce.cart_service.service.CartResilience4j;
import com.strikerkk.aicommerce.cart_service.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("CartServiceApplication")
class CartServiceApplicationTests {

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
        assertThat(CartServiceApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
        assertThat(CartServiceApplication.class.getAnnotation(EnableFeignClients.class)).isNotNull();
    }

    @Test
    @DisplayName("the controller is registered")
    void controllerIsRegistered() {
        assertThat(applicationContext.getBean(CartController.class)).isNotNull();
    }

    @Test
    @DisplayName("both services are registered")
    void servicesAreRegistered() {
        assertThat(applicationContext.getBean(CartService.class)).isNotNull();
        assertThat(applicationContext.getBean(CartResilience4j.class)).isNotNull();
    }

    @Test
    @DisplayName("both repositories are registered")
    void repositoriesAreRegistered() {
        assertThat(applicationContext.getBean(CartRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(CartItemRepository.class)).isNotNull();
    }

    @Test
    @DisplayName("the Feign client to product-service is created")
    void theFeignClientIsCreated() {
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
        HandlerInterceptor[] interceptors = (HandlerInterceptor[]) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(mapping, "getAdaptedInterceptors");

        assertThat(interceptors)
                .hasAtLeastOneElementOfType(UserInterceptor.class);
    }

    @Test
    @DisplayName("FeignClientInterceptor is NOT a bean, so the caller id is not propagated")
    void feignClientInterceptorIsNotRegistered() {
        assertThatThrownBy(() -> applicationContext.getBean(FeignClientInterceptor.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        assertThat(applicationContext.getBeansOfType(feign.RequestInterceptor.class).values())
                .noneMatch(FeignClientInterceptor.class::isInstance);
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
    @DisplayName("every endpoint of the cart is mapped")
    void everyEndpointIsMapped() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet())
                .extracting(Object::toString)
                .anyMatch(info -> info.contains("GET") && info.contains("/cart]"))
                .anyMatch(info -> info.contains("GET") && info.contains("/cart/items"))
                .anyMatch(info -> info.contains("POST") && info.contains("/cart/items"))
                .anyMatch(info -> info.contains("PATCH") && info.contains("/cart/items/{cartItemId}"))
                .anyMatch(info -> info.contains("DELETE") && info.contains("/cart/items/{cartItemId}"))
                .anyMatch(info -> info.contains("DELETE") && info.contains("/cart/clear"));
    }
}
