package com.strikerkk.aicommerce.api_gateway.filters;

import com.strikerkk.aicommerce.api_gateway.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationFilter – public path matching")
class AuthenticationFilterPathMatchingTest {

    private static final String BASE_URL = "http://gateway.local";

    @Mock
    private JwtService jwtService;

    @Mock
    private GatewayFilterChain chain;

    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationFilter(jwtService).apply(new AuthenticationFilter.Config());
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(BASE_URL + path).build());
    }

    @ParameterizedTest(name = "[{index}] {0} is public")
    @ValueSource(strings = {
            // "/users/"
            "/users/",
            // "/users/css/**"
            "/users/css/login.css",
            "/users/css/theme/dark.css",
            // "/users/login/oauth2/**"  (the OAuth2 redirect-uri Google calls back on)
            "/users/login/oauth2/code/google",
            "/users/login/oauth2",
            // "/users/oauth2/**"        (the authorization request entry point)
            "/users/oauth2/authorization/google",
            "/users/oauth2",
            // "/users/auth/signup" and "/users/auth/login"
            "/users/auth/signup",
            "/users/auth/login",
            // "/payments/webhook/razorpay"
            "/payments/webhook/razorpay",
            // "/payments/page/**"       (the hosted Razorpay checkout page)
            "/payments/page/checkout",
            "/payments/page/order/ord_123",
            "/payments/page"
    })
    @DisplayName("lets a public path through without any Authorization header")
    void shouldBypassAuthenticationForPublicPaths(String path) {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchangeFor(path);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(same(exchange));
        verifyNoInteractions(jwtService);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0} requires a token")
    @ValueSource(strings = {
            "/",
            // "/users/" is an exact match, it is NOT a "/users/**" prefix
            "/users",
            "/users/profile",
            "/users/addresses",
            "/users/auth",
            "/users/auth/logout",
            "/users/auth/login/extra",
            "/users/authx",
            "/users/auth/loginx",
            // AntPathMatcher is case-sensitive
            "/USERS/auth/login",
            "/users/CSS/login.css",
            // trailing slash does not match an exact, slash-less pattern
            "/users/auth/login/",
            // payments
            "/payments/webhook",
            "/payments/webhook/razorpay/extra",
            "/payments/webhooks/razorpay",
            "/payments/page2/checkout",
            "/payments/1/status",
            // everything else is protected by design
            "/agent/chat",
            "/products/1",
            "/admin/products",
            "/cart",
            "/cart/items",
            "/orders",
            "/orders/99"
    })
    @DisplayName("answers 401 for every non public path when no Authorization header is present")
    void shouldRequireAuthenticationForEverythingElse(String path) {
        MockServerWebExchange exchange = exchangeFor(path);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("matches on the path only, the query string is irrelevant")
    void shouldIgnoreTheQueryStringWhenMatching() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(BASE_URL + "/users/oauth2/authorization/google?redirect_uri=/home").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(same(exchange));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("a public path is never validated, even when it carries a broken token")
    void shouldNotValidateTokensOnPublicPaths() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(BASE_URL + "/users/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer totally-broken")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(same(exchange));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("a public path is forwarded untouched: no X-user-* header is injected")
    void shouldNotInjectIdentityHeadersOnPublicPaths() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchangeFor("/payments/webhook/razorpay");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders forwarded = exchange.getRequest().getHeaders();
        assertThat(forwarded.containsKey("X-user-id")).isFalse();
        assertThat(forwarded.containsKey("X-user-role")).isFalse();
        assertThat(forwarded.containsKey("X-user-email")).isFalse();
    }

    @Test
    @DisplayName("documents that identity headers sent by a client survive on a public path")
    void shouldDocumentThatSpoofedHeadersSurviveOnPublicPaths() {
        // The filter returns early for public paths, so it does not clear client supplied
        // X-user-* headers there. Downstream services must therefore never trust those headers
        // on a public endpoint. See src/test/README.md, "Notes / findings".
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(BASE_URL + "/users/auth/login")
                        .header("X-user-id", "1")
                        .header("X-user-role", "ADMIN")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(same(exchange));
        assertThat(exchange.getRequest().getHeaders().getFirst("X-user-role")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("the public path list is exactly the eight patterns documented in the filter")
    @SuppressWarnings("unchecked")
    void shouldExposeExactlyTheDocumentedPublicPatterns() {
        List<String> publicPaths = (List<String>) ReflectionTestUtils
                .getField(AuthenticationFilter.class, "PUBLIC_PATHS");

        assertThat(publicPaths).containsExactly(
                "/users/",
                "/users/css/**",
                "/users/login/oauth2/**",
                "/users/oauth2/**",
                "/users/auth/signup",
                "/users/auth/login",
                "/payments/webhook/razorpay",
                "/payments/page/**");
    }
}




