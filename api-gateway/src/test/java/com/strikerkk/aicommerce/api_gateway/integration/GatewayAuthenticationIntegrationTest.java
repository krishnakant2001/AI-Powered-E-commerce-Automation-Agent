package com.strikerkk.aicommerce.api_gateway.integration;

import com.strikerkk.aicommerce.api_gateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                TestTokens.JWT_SECRET_PROPERTY,
                TestTokens.EUREKA_DISABLED_PROPERTY,
                TestTokens.EUREKA_NO_REGISTER_PROPERTY,
                TestTokens.EUREKA_NO_FETCH_PROPERTY
        })
@DisplayName("Gateway over HTTP")
class GatewayAuthenticationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Nested
    @DisplayName("protected routes")
    class ProtectedRoutes {

        @ParameterizedTest(name = "[{index}] GET {0}")
        @ValueSource(strings = {
                "/api/v1/agent/chat",
                "/api/v1/users/profile",
                "/api/v1/users/addresses",
                "/api/v1/products/1",
                "/api/v1/admin/products",
                "/api/v1/cart",
                "/api/v1/cart/items",
                "/api/v1/orders",
                "/api/v1/orders/42",
                "/api/v1/payments/123"
        })
        @DisplayName("answers 401 when no token is supplied")
        void shouldRejectAnonymousRequests(String path) {
            webTestClient.get().uri(path)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("answers 401 for a non Bearer Authorization header")
        void shouldRejectNonBearerHeader() {
            webTestClient.get().uri("/api/v1/products/1")
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("answers 401 for a token signed with another secret")
        void shouldRejectTokenSignedWithAnotherKey() {
            webTestClient.get().uri("/api/v1/products/1")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.tokenSignedWithAnotherKey()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("answers 401 for an expired token")
        void shouldRejectExpiredToken() {
            webTestClient.get().uri("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.expiredToken()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("answers 401 for a token whose payload was tampered with")
        void shouldRejectTamperedToken() {
            webTestClient.get().uri("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.tokenWithTamperedPayload()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("answers 401 for garbage instead of a token")
        void shouldRejectGarbageToken() {
            webTestClient.get().uri("/api/v1/cart")
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("not-a-jwt"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @ParameterizedTest(name = "[{index}] GET {0}")
        @ValueSource(strings = {
                "/api/v1/agent/chat",
                "/api/v1/users/profile",
                "/api/v1/products/1",
                "/api/v1/cart",
                "/api/v1/orders",
                "/api/v1/payments/123"
        })
        @DisplayName("lets a valid token through: the request now fails downstream, not at the gate")
        void shouldForwardRequestsCarryingAValidToken(String path) {
            webTestClient.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.validToken()))
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Nested
    @DisplayName("public routes")
    class PublicRoutes {

        @ParameterizedTest(name = "[{index}] {0} needs no token")
        @ValueSource(strings = {
                // StripPrefix=2 turns these into /users/auth/... before the filter sees them
                "/api/v1/users/auth/login",
                "/api/v1/users/auth/signup",
                // and these into /payments/...
                "/api/v1/payments/webhook/razorpay",
                "/api/v1/payments/page/checkout",
                "/api/v1/payments/page/order/ord_123",
                // the OAuth2 route has no StripPrefix, the path reaches the filter unchanged
                "/users/oauth2/authorization/google",
                "/users/login/oauth2/code/google"
        })
        @DisplayName("are never answered with 401")
        void shouldNotRequireATokenOnPublicRoutes(String path) {
            webTestClient.get().uri(path)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }

        @Test
        @DisplayName("the Razorpay webhook is reachable with a POST and no credentials")
        void razorpayWebhookIsReachableWithoutCredentials() {
            webTestClient.post().uri("/api/v1/payments/webhook/razorpay")
                    .bodyValue("{\"event\":\"payment.captured\"}")
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Nested
    @DisplayName("routing")
    class Routing {

        @ParameterizedTest(name = "[{index}] GET {0} is not routed")
        @ValueSource(strings = {
                "/",
                "/api/v2/products/1",
                "/api/v1/product/1",
                "/products/1",
                "/users/profile",
                "/unknown"
        })
        @DisplayName("answers 404 for a path that matches no route")
        void shouldReturn404ForUnknownPaths(String path) {
            webTestClient.get().uri(path)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("exposes the actuator health endpoint, which is handled locally and unprotected")
        void shouldExposeTheActuatorHealthEndpoint() {
            // AuthenticationFilter is a per-route filter and no route matches /actuator/**, so the
            // actuator is NOT behind it. See src/test/README.md, "Notes / findings".
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }
    }
}



