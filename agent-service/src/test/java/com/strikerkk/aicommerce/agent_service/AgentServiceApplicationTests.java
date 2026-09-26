package com.strikerkk.aicommerce.agent_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.auth.FeignClientInterceptor;
import com.strikerkk.aicommerce.agent_service.auth.UserInterceptor;
import com.strikerkk.aicommerce.agent_service.auth.WebConfig;
import com.strikerkk.aicommerce.agent_service.clients.CartServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.OrderServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.PaymentServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.ProductServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.UserServiceClient;
import com.strikerkk.aicommerce.agent_service.controller.AgentController;
import com.strikerkk.aicommerce.agent_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.agent_service.llm.SystemPromptBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolDefinitionBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolExecutionService;
import com.strikerkk.aicommerce.agent_service.llm.ToolExecutor;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentActionRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentMessageRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import com.strikerkk.aicommerce.agent_service.service.AgentChatService;
import com.strikerkk.aicommerce.agent_service.service.AgentSessionService;
import com.strikerkk.aicommerce.agent_service.service.SessionContextService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("AgentServiceApplication")
class AgentServiceApplicationTests {

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
        assertThat(AgentServiceApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
        assertThat(AgentServiceApplication.class.getAnnotation(EnableFeignClients.class)).isNotNull();
    }

    @Test
    @DisplayName("the controller is registered")
    void theControllerIsRegistered() {
        assertThat(applicationContext.getBean(AgentController.class)).isNotNull();
    }

    @Test
    @DisplayName("all three services are registered")
    void theServicesAreRegistered() {
        assertThat(applicationContext.getBean(AgentChatService.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentSessionService.class)).isNotNull();
        assertThat(applicationContext.getBean(SessionContextService.class)).isNotNull();
    }

    @Test
    @DisplayName("all three repositories are registered")
    void theRepositoriesAreRegistered() {
        assertThat(applicationContext.getBean(AgentSessionRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentMessageRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentActionRepository.class)).isNotNull();
    }

    @Test
    @DisplayName("the LLM layer is registered")
    void theLlmLayerIsRegistered() {
        assertThat(applicationContext.getBean(SystemPromptBuilder.class)).isNotNull();
        assertThat(applicationContext.getBean(ToolDefinitionBuilder.class)).isNotNull();
        assertThat(applicationContext.getBean(ToolExecutionService.class)).isNotNull();
        assertThat(applicationContext.getBean(ToolExecutor.class)).isNotNull();
    }

    @Test
    @DisplayName("a Feign client exists for every downstream service the agent can reach")
    void everyFeignClientIsCreated() {
        assertThat(applicationContext.getBean(ProductServiceClient.class)).isNotNull();
        assertThat(applicationContext.getBean(CartServiceClient.class)).isNotNull();
        assertThat(applicationContext.getBean(OrderServiceClient.class)).isNotNull();
        assertThat(applicationContext.getBean(PaymentServiceClient.class)).isNotNull();
        assertThat(applicationContext.getBean(UserServiceClient.class)).isNotNull();
    }

    @Test
    @DisplayName("the gateway-header infrastructure is registered and plugged into the MVC pipeline")
    void theHeaderInfrastructureIsRegistered() {
        assertThat(applicationContext.getBean(UserInterceptor.class)).isNotNull();
        assertThat(applicationContext.getBean(WebConfig.class)).isNotNull();

        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        HandlerInterceptor[] interceptors =
                (HandlerInterceptor[]) ReflectionTestUtils.invokeMethod(mapping, "getAdaptedInterceptors");

        assertThat(interceptors).hasAtLeastOneElementOfType(UserInterceptor.class);
    }

    @Test
    @DisplayName("the caller identity is propagated to every Feign call")
    void theCallerIdentityIsPropagated() {
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
    @DisplayName("both Redis templates are registered, each with its own value type")
    void bothRedisTemplatesAreRegistered() {
        assertThat(applicationContext.containsBean("redisTemplate")).isTrue();
        assertThat(applicationContext.containsBean("sessionContextRedisTemplate")).isTrue();

        assertThat(applicationContext.getBeanNamesForType(ResolvableType.forClassWithGenerics(
                RedisTemplate.class, String.class, SessionContext.class)))
                .contains("sessionContextRedisTemplate");
    }

    @Test
    @DisplayName("the shared ObjectMapper is on the context")
    void theSharedObjectMapperIsOnTheContext() {
        assertThat(applicationContext.getBean(ObjectMapper.class)).isNotNull();
    }

    @Test
    @DisplayName("the resilience4j instances the tools rely on are configured")
    void theResilienceInstancesAreConfigured() {
        RetryRegistry retryRegistry = applicationContext.getBean(RetryRegistry.class);
        CircuitBreakerRegistry circuitBreakerRegistry = applicationContext.getBean(CircuitBreakerRegistry.class);

        assertThat(retryRegistry.retry("microservice-call").getRetryConfig().getMaxAttempts()).isEqualTo(3);
        assertThat(retryRegistry.retry("llm-call").getRetryConfig().getMaxAttempts()).isEqualTo(2);

        var config = circuitBreakerRegistry.circuitBreaker("microservice-call").getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
    }

    @Test
    @DisplayName("the session TTL is read from the configuration")
    void theSessionTtlIsRead() {
        assertThat(ReflectionTestUtils.getField(
                applicationContext.getBean(SessionContextService.class), "ttlMinutes"))
                .isEqualTo(60L);
    }

    @Test
    @DisplayName("every agent endpoint is mapped")
    void everyEndpointIsMapped() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        assertThat(mapping.getHandlerMethods().keySet())
                .extracting(Object::toString)
                .anyMatch(info -> info.contains("POST") && info.contains("/agent/chat"))
                .anyMatch(info -> info.contains("POST") && info.contains("/agent/session/start"))
                .anyMatch(info -> info.contains("GET") && info.contains("/agent/session/{sessionId}]"))
                .anyMatch(info -> info.contains("DELETE") && info.contains("/agent/session/{sessionId}/end"))
                .anyMatch(info -> info.contains("GET") && info.contains("/agent/session/{sessionId}/history"))
                .anyMatch(info -> info.contains("GET") && info.contains("/agent/session/my"))
                .anyMatch(info -> info.contains("GET") && info.contains("/agent/session/{sessionId}/actions"));
    }
}
