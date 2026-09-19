package com.strikerkk.aicommerce.api_gateway.config;

import com.strikerkk.aicommerce.api_gateway.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        TestTokens.JWT_SECRET_PROPERTY,
        TestTokens.EUREKA_DISABLED_PROPERTY,
        TestTokens.EUREKA_NO_REGISTER_PROPERTY,
        TestTokens.EUREKA_NO_FETCH_PROPERTY
})
@DisplayName("Gateway routing configuration")
class GatewayRouteConfigurationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private RouteLocator routeLocator;

    private List<RouteDefinition> definitions;

    @BeforeEach
    void loadRouteDefinitions() {
        definitions = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(definitions).isNotNull();
    }

    // helpers ------------------------------------------------------------------

    private RouteDefinition definition(String id) {
        return definitions.stream()
                .filter(definition -> id.equals(definition.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no route definition with id '" + id + "'"));
    }

    private static List<String> filterNames(RouteDefinition definition) {
        return definition.getFilters().stream().map(FilterDefinition::getName).toList();
    }

    private static List<String> pathPatterns(RouteDefinition definition) {
        return definition.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .toList();
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway.local" + path).build());
    }

    private Optional<Route> firstMatchingRoute(String path) {
        return routeLocator.getRoutes()
                .concatMap(route -> Mono.from(route.getPredicate().apply(exchangeFor(path)))
                        .filter(Boolean::booleanValue)
                        .map(matched -> route))
                .next()
                .blockOptional();
    }

    // the routing table ------------------------------------------------------------------

    @Test
    @DisplayName("declares exactly the seven routes of the platform, in the documented order")
    void shouldDeclareTheSevenRoutes() {
        assertThat(definitions).extracting(RouteDefinition::getId)
                .containsExactly(
                        "agent-service",
                        "user-service",
                        "user-service-oauth2",
                        "product-service",
                        "cart-service",
                        "order-service",
                        "payment-service");
    }

    @Test
    @DisplayName("every route targets its service through the load balancer, never a hard coded host")
    void shouldAlwaysTargetTheServiceThroughTheLoadBalancer() {
        assertThat(definitions).allSatisfy(definition ->
                assertThat(definition.getUri().getScheme())
                        .as("route '%s' must use the lb:// scheme", definition.getId())
                        .isEqualTo("lb"));

        assertThat(definition("agent-service").getUri()).hasToString("lb://AGENT-SERVICE");
        assertThat(definition("user-service").getUri()).hasToString("lb://USER-SERVICE");
        assertThat(definition("user-service-oauth2").getUri()).hasToString("lb://USER-SERVICE");
        assertThat(definition("product-service").getUri()).hasToString("lb://PRODUCT-SERVICE");
        assertThat(definition("cart-service").getUri()).hasToString("lb://CART-SERVICE");
        assertThat(definition("order-service").getUri()).hasToString("lb://ORDER-SERVICE");
        assertThat(definition("payment-service").getUri()).hasToString("lb://PAYMENT-SERVICE");
    }

    @Test
    @DisplayName("every route declares its Path predicates")
    void shouldDeclareThePathPredicates() {
        assertThat(pathPatterns(definition("agent-service")))
                .containsExactly("/api/v1/agent/**");
        assertThat(pathPatterns(definition("user-service")))
                .containsExactly("/api/v1/users/**");
        assertThat(pathPatterns(definition("user-service-oauth2")))
                .containsExactly("/users/oauth2/**", "/users/login/oauth2/**");
        assertThat(pathPatterns(definition("product-service")))
                .containsExactly("/api/v1/products/**", "/api/v1/admin/products/**");
        assertThat(pathPatterns(definition("cart-service")))
                .containsExactly("/api/v1/cart/**");
        assertThat(pathPatterns(definition("order-service")))
                .containsExactly("/api/v1/orders/**");
        assertThat(pathPatterns(definition("payment-service")))
                .containsExactly("/api/v1/payments/**");
    }

    // the filters ------------------------------------------------------------------

    @Test
    @DisplayName("no route is reachable without going through the AuthenticationFilter")
    void everyRouteIsGuardedByTheAuthenticationFilter() {
        assertThat(definitions).allSatisfy(definition ->
                assertThat(filterNames(definition))
                        .as("route '%s' must declare the AuthenticationFilter", definition.getId())
                        .contains("AuthenticationFilter"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"agent-service", "user-service", "product-service", "cart-service",
            "order-service", "payment-service"})
    @DisplayName("strips the /api/v1 prefix before authenticating, so the filter sees the real path")
    void shouldStripTheApiV1PrefixBeforeAuthenticating(String routeId) {
        RouteDefinition definition = definition(routeId);

        assertThat(filterNames(definition)).containsExactly("StripPrefix", "AuthenticationFilter");

        FilterDefinition stripPrefix = definition.getFilters().get(0);
        assertThat(stripPrefix.getArgs().values()).containsExactly("2");
    }

    @Test
    @DisplayName("the OAuth2 route keeps its prefix and preserves the Host header for the redirect")
    void oauth2RouteKeepsThePrefixAndTheHostHeader() {
        RouteDefinition definition = definition("user-service-oauth2");

        assertThat(filterNames(definition))
                .containsExactly("AuthenticationFilter", "PreserveHostHeader")
                .doesNotContain("StripPrefix");
    }

    @Test
    @DisplayName("the compiled routes keep the declared filter order")
    void compiledRoutesKeepTheFilterOrder() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).isNotNull().hasSize(7);
        assertThat(routes).extracting(Route::getId)
                .containsExactlyInAnyOrder("agent-service", "user-service", "user-service-oauth2",
                        "product-service", "cart-service", "order-service", "payment-service");

        routes.forEach(route -> assertThat(route.getFilters())
                .as("route '%s' must have at least one filter", route.getId())
                .isNotEmpty());
    }

    // predicate evaluation ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {1} -> {0}")
    @CsvSource({
            "agent-service,       /api/v1/agent/chat",
            "agent-service,       /api/v1/agent/sessions/abc/messages",
            "user-service,        /api/v1/users/auth/login",
            "user-service,        /api/v1/users/profile",
            "user-service-oauth2, /users/oauth2/authorization/google",
            "user-service-oauth2, /users/login/oauth2/code/google",
            "product-service,     /api/v1/products/1",
            "product-service,     /api/v1/admin/products",
            "cart-service,        /api/v1/cart",
            "cart-service,        /api/v1/cart/items/3",
            "order-service,       /api/v1/orders",
            "order-service,       /api/v1/orders/42",
            "payment-service,     /api/v1/payments/webhook/razorpay",
            "payment-service,     /api/v1/payments/page/checkout"
    })
    @DisplayName("routes a request to the expected service")
    void shouldRouteRequestToTheExpectedService(String expectedRouteId, String path) {
        assertThat(firstMatchingRoute(path))
                .as("request to '%s'", path)
                .isPresent()
                .get()
                .extracting(Route::getId)
                .isEqualTo(expectedRouteId);
    }

    @ParameterizedTest(name = "[{index}] {0} is not routed")
    @ValueSource(strings = {
            "/",
            "/api/v1",
            "/api/v2/products/1",
            "/api/v1/product/1",
            "/products/1",
            "/users/profile",
            "/actuator/health",
            "/unknown"
    })
    @DisplayName("does not route anything that is not declared in application.yml")
    void shouldNotRouteUndeclaredPaths(String path) {
        assertThat(firstMatchingRoute(path)).as("request to '%s'", path).isEmpty();
    }

    @Test
    @DisplayName("the /api/v1/agent/** route comes first, so it never shadows another service")
    void agentRouteDoesNotShadowTheOthers() {
        assertThat(firstMatchingRoute("/api/v1/agent/chat")).get()
                .extracting(Route::getId).isEqualTo("agent-service");
        assertThat(firstMatchingRoute("/api/v1/users/profile")).get()
                .extracting(Route::getId).isEqualTo("user-service");
    }
}

